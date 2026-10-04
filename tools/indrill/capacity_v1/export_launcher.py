"""External immutable-runtime launcher for the standalone teacher exporter.

No game-rule or selection logic. All commands are explicit, write-once stages.
compile --gson EXISTING_JAR creates an isolated class directory and execution freeze.
preflight and export validate that freeze immediately before and after execution.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shutil
import subprocess
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
TOOLS = ROOT / 'tools/indrill/capacity_v1'
RUN = ROOT / 'build/analysis/indrill-capacity-v1'
SETUP = RUN / 'setup'
CLASSES = SETUP / 'export-classes'
EXECUTION = SETUP / 'export-execution-freeze.json'
DATASET = RUN / 'dataset'


def require(ok, message):
    if not ok:
        raise ValueError(message)


def digest(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        while block := stream.read(1 << 20):
            value.update(block)
    return value.hexdigest()


def pin(path):
    path = Path(path).resolve()
    return {'path': str(path), 'sha256': digest(path)}


def write_once(path, value):
    with Path(path).open('x', encoding='utf-8') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')


def files(directory):
    return sorted(str(p.resolve()) for p in Path(directory).rglob('*') if p.is_file())


def trees(roots):
    return [{'path': str(Path(p).resolve()), 'files': files(p)} for p in roots]


def verify(value):
    for tree in value['trees']:
        require(files(tree['path']) == tree['files'], 'Execution tree inventory changed: ' + tree['path'])
    for item in value['pins']:
        require(digest(item['path']) == item['sha256'], 'Execution input changed: ' + item['path'])


def verify_execution(path=EXECUTION):
    path = Path(path).resolve()
    require(path == EXECUTION.resolve(), 'Unexpected execution-freeze location')
    value = json.loads(path.read_text(encoding='utf-8'))
    require(value['schema'] == 'indrill-capacity-export-execution-v1' and value['complete'] is True,
            'Incomplete execution freeze')
    require(Path(value['classes']).resolve() == CLASSES.resolve(), 'Unisolated helper classes')
    jdk = Path(value['jdkDirectory']).resolve()
    require(Path(value['java']).resolve() == jdk / 'bin/java.exe'
            and Path(value['javac']).resolve() == jdk / 'bin/javac.exe', 'Unbound JDK executable')
    retained = {item['path']: item['sha256'] for item in value['pins']}
    require(value['java'] in retained and value['javac'] in retained
            and retained.get(value['gson']['path']) == value['gson']['sha256'], 'Executable/Gson pins are missing')
    expected = [str(CLASSES.resolve()), str((ROOT / 'build/classes/java/main').resolve()),
                str((ROOT / 'build/resources/main').resolve()), value['gson']['path']]
    require(value['classpathEntries'] == expected and value['classpath'] == ';'.join(expected), 'Classpath differs')
    verify(value)
    return value


def compile_helpers(gson):
    require(not CLASSES.exists() and not EXECUTION.exists(), 'Preserve an existing compilation/freeze attempt')
    require(not (SETUP / 'export-compile-intent.json').exists(), 'Compilation already attempted; no implicit retry')
    SETUP.mkdir(parents=True, exist_ok=True)
    found = shutil.which('java')
    require(found is not None, 'Java is not available')
    probe = subprocess.run([found, '-XshowSettings:properties', '-version'], capture_output=True, text=True, check=True)
    version = probe.stdout + probe.stderr
    match = re.search(r'^\s*java.home\s*=\s*(.+)$', version, re.MULTILINE)
    require(match is not None and re.search(r'^\s*java.specification.version\s*=\s*17\s*$', version, re.MULTILINE), 'Java17 JDK is required')
    jdk = Path(match.group(1).strip()).resolve()
    java, javac = jdk / 'bin/java.exe', jdk / 'bin/javac.exe'
    require(java.is_file() and javac.is_file(), 'A complete Windows Java17 JDK is required')
    gson = Path(gson).resolve()
    require(gson.is_file(), 'Pass an existing Gson jar')
    sources = [TOOLS / 'IndrillTeacherExport.java', TOOLS / 'IndrillCertificateSynthetic.java']
    roots = [ROOT / 'build/classes/java/main', ROOT / 'build/resources/main', ROOT / 'src/main/java/btk/core', jdk]
    require(all(p.is_dir() for p in roots), 'Build production classes/resources before isolated compilation')
    inventories = trees(roots)
    inputs = {path for tree in inventories for path in tree['files']}
    inputs |= {str(p.resolve()) for p in TOOLS.iterdir() if p.is_file()}
    inputs |= {str((ROOT / 'docs/indrill-capacity-v1.md').resolve()), str(gson),
               str((ROOT / 'src/main/java/btk/tools/CgReplay.java').resolve()),
               str((ROOT / 'src/main/java/btk/tools/CgValidate.java').resolve()),
               str((ROOT / 'src/main/java/btk/bot/current/IndrillFeatures.java').resolve()),
               str((ROOT / 'src/main/java/btk/bot/current/IndrillV2Net.java').resolve()),
               str((ROOT / 'tools/indrill/train_q.py').resolve()), str((ROOT / 'tools/indrill/train_q_v2.py').resolve())}
    initial = {'trees': inventories, 'pins': [pin(p) for p in sorted(inputs)]}
    classpath_entries = [str(CLASSES.resolve()), str(roots[0].resolve()), str(roots[1].resolve()), str(gson)]
    classpath = ';'.join(classpath_entries)
    command = [str(javac), '-encoding', 'UTF-8', '-cp', classpath, '-d', str(CLASSES), *map(str, sources)]
    write_once(SETUP / 'export-compile-intent.json', {'createdAtUtc': datetime.now(timezone.utc).isoformat(),
               'command': command, 'javaPropertiesAndVersion': version, **initial})
    CLASSES.mkdir()
    with (SETUP / 'export-compile.log').open('x', encoding='utf-8') as stream:
        completed = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT)
    require(completed.returncode == 0, 'Isolated compile failed; retain this attempt')
    verify(initial)
    generated = files(CLASSES)
    require(any(Path(p).name == 'IndrillTeacherExport.class' for p in generated)
            and any(Path(p).name == 'IndrillCertificateSynthetic.class' for p in generated), 'Missing compiled helper classes')
    freeze = {'schema': 'indrill-capacity-export-execution-v1', 'complete': True,
              'createdAtUtc': datetime.now(timezone.utc).isoformat(), 'java': str(java), 'javac': str(javac),
              'jdkDirectory': str(jdk), 'classes': str(CLASSES.resolve()), 'gson': pin(gson),
              'classpathEntries': classpath_entries, 'classpath': classpath,
              'trees': inventories + trees([CLASSES]),
              'pins': initial['pins'] + [pin(p) for p in generated] +
                      [pin(SETUP / name) for name in ('export-compile-intent.json', 'export-compile.log')]}
    write_once(EXECUTION, freeze)
    verify_execution()
    print(json.dumps({'executionFreeze': pin(EXECUTION), 'helperClasses': len(generated)}))


def run_export_stage(mode):
    freeze = verify_execution()
    command = [freeze['java'], '-cp', freeze['classpath'], 'IndrillTeacherExport', mode, str(ROOT), str(DATASET)]
    require(not (SETUP / (mode + '-intent.json')).exists(), 'Stage already attempted; no automatic repeat')
    write_once(SETUP / (mode + '-intent.json'), {'executionFreeze': pin(EXECUTION), 'command': command})
    verify_execution()
    with (SETUP / (mode + '.log')).open('x', encoding='utf-8') as stream:
        result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT)
    verify_execution()
    write_once(SETUP / (mode + '-result.json'), {'exitCode': result.returncode, 'executionFreeze': pin(EXECUTION),
                                             'log': pin(SETUP / (mode + '.log'))})
    require(result.returncode == 0, 'Stage failed; retain outputs and do not retry/tune implicitly')
    print(json.dumps({'stage': mode, 'exitCode': result.returncode, 'dataset': str(DATASET)}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('compile', 'preflight', 'export', 'verify'))
    parser.add_argument('--gson')
    args = parser.parse_args()
    if args.mode == 'compile':
        require(args.gson is not None, 'compile requires --gson')
        compile_helpers(args.gson)
    elif args.mode == 'verify':
        verify_execution()
        print('Execution freeze verified')
    else:
        run_export_stage(args.mode)


if __name__ == '__main__':
    main()
