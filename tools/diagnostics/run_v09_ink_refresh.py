"""Write-once command journal for the saved-state ink-refresh experiment. No live API calls."""
import hashlib
import json
import os
import shutil
from pathlib import Path
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / 'build/analysis/v09-ink-refresh-v1'
RT = json.loads((ROOT / 'build/analysis/v09-budget100-local-v1/baseline.json').read_bytes())
JAVA, JAVAC, OLD_CP = RT['java'], RT['javac'], RT['classpath']
HELPERS = RUN / 'helper-classes'


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def save(path, data):
    with path.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(data, stream, indent=2)
        stream.write('\n')


def run(label, command, allowed=(0,)):
    targets = [RUN / (label + suffix) for suffix in ('-command.json', '.log', '-exit.json')]
    if any(path.exists() for path in targets):
        raise ValueError('Command already attempted; preserve it: ' + label)
    save(targets[0], {'command': command, 'cwd': str(ROOT), 'automaticRetry': False})
    start = time.monotonic()
    env = dict(os.environ, JAVA_HOME=str(Path(JAVA).parent.parent))
    with targets[1].open('xb') as log:
        result = subprocess.run(command, cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT)
    save(targets[2], {'exitCode': result.returncode, 'seconds': time.monotonic()-start,
                      'logSha256': sha(targets[1])})
    print(targets[1].read_text(encoding='utf-8', errors='replace')[-10000:], flush=True)
    if result.returncode not in allowed:
        raise SystemExit(result.returncode)


def prepare():
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    files = {}
    for folder in ['build/submit', 'build/analysis/v09-batched-refresh-v1/artifact',
                   'build/analysis/v09-batched-refresh-v1/artifact-off',
                   'build/analysis/v09-batched-refresh-v1/artifact-v09']:
        for path in (ROOT/folder).rglob('*'):
            if path.is_file():
                files[path.relative_to(ROOT).as_posix()] = sha(path)
    if len(files) < 30:
        raise ValueError('Missing preserved artifacts')
    source = RUN/'baseline-source'
    source.mkdir(exist_ok=False)
    tracked = subprocess.check_output(['git', 'ls-files', 'src/main', 'src/test', 'tools', 'build.gradle.kts'],
                                     cwd=ROOT, text=True).splitlines()
    hashes = {}
    for name in tracked:
        content = subprocess.check_output(['git', 'show', commit+':'+name], cwd=ROOT)
        dest = source/name
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(content)
        hashes[name] = hashlib.sha256(content).hexdigest()
    save(RUN/'baseline.json', {'commit': commit, 'artifactFiles': files, 'sourceGitBytes': hashes,
          'runtime': {key: RT[key] for key in ['java', 'javac', 'classpath']},
          'protocolSha256': sha(ROOT/'docs/v09-ink-refresh-v1.md')})
    print('Preserved', len(files), 'artifact files and', len(hashes), 'tracked source files')


def build_artifacts():
    suites=[ET.parse(p).getroot() for p in (ROOT/'build/test-results/test').glob('TEST-*.xml')]
    failed=[(s.attrib['name'],case.attrib['name'],''.join(case.find('failure').itertext()))
            for s in suites for case in s.findall('testcase') if case.find('failure') is not None]
    total=sum(int(s.attrib['tests']) for s in suites)
    errors=sum(int(s.attrib.get('errors','0')) for s in suites)
    # Existing historical-catalog identity rejection documented before this experiment.
    known=len(failed)==1 and failed[0][0]=='btk.submit.FileMergerTest' and failed[0][1]=='characterLimitIsCheckedBeforePublishing()' \
        and 'Experiment implementation changed: btk/core/Pathing.class' in failed[0][2]
    if total!=249 or errors or (failed and not known):
        raise ValueError('Unexpected preflight failure; preserve and stop')
    save(RUN/'preflight-test-verdict.json', {'tests':total,'passed':total-len(failed),'errors':errors,
         'failures':failed,'knownHistoricalCatalogRejection':known,'allNewCoreAndSubmissionTestsPass':True,
         'fullSuitePass':False,'noFailedTestRerun':True})
    shutil.copytree(ROOT/'build/test-results/test', RUN/'test-results')
    run('engine', [str(ROOT/'gradlew.bat'), 'cgValidate', '--offline', '--console=plain'])
    specs = {
        'artifact': 'current?NOISE=0.1,USE_BATCHED_REFRESH=true,USE_INCREMENTAL_INK_REFRESH=true',
        'artifact-off': 'current?NOISE=0.1,USE_BATCHED_REFRESH=true',
        'artifact-v09': 'v09',
        'default-artifact': 'current',
        'guard-off': 'current?NOISE=0,USE_BATCHED_REFRESH=true,USE_ROLLOUT_MISSED_DEADLINE=true',
        'guard-on': 'current?NOISE=0,USE_BATCHED_REFRESH=true,USE_ROLLOUT_MISSED_DEADLINE=true,USE_INCREMENTAL_INK_REFRESH=true',
    }
    for label, spec in specs.items():
        if (RUN/label).exists():
            raise ValueError('Preserve existing artifact')
        run('export-'+label, [str(ROOT/'gradlew.bat'), 'submit', '--offline', '--console=plain',
            '-PsubmitVersion='+spec, '-PsubmitOut='+str(RUN/label)])


def freeze(verified=False):
    # Isolated tool runtime and compiled helpers, never rebuilt during measurements.
    if not verified:
        shutil.copytree(ROOT/'build/classes/java/main', RUN/'runtime/classes',
                        ignore=shutil.ignore_patterns('pduhard', 'ppo'))
        shutil.copytree(ROOT/'build/resources/main', RUN/'runtime/resources')
    else:
        old=json.loads((RUN/'measurement-freeze.json').read_bytes())
        allowed={'tools/diagnostics/run_v09_ink_refresh.py','tools/diagnostics/V09ForecastGuardStudy.java'}
        for name,digest in old['files'].items():
            if name not in allowed and sha(ROOT/name)!=digest:
                raise ValueError('Unexpected premeasurement change: '+name)
        for name in ['artifact','artifact-off','artifact-v09','default-artifact','guard-check-off','guard-check-on']:
            manifest=json.loads((RUN/name/'manifest.json').read_bytes())
            if manifest['sha256']!=sha(RUN/name/'Player.java') or manifest['charactersUtf16']>100000:
                raise ValueError('Invalid artifact: '+name)
        if sha(RUN/'guard-check-off/Player.java')!='3bd070b3802744778473e93cebed8148ba07a7b243bd744c8ac486076d304a2c':
            raise ValueError('Historical guard reference changed')
    jars = [p for p in OLD_CP.split(';') if p.endswith('.jar')]
    guard_classes=RUN/'guard-helper-classes' if verified else HELPERS
    if verified: guard_classes.mkdir(exist_ok=False)
    cp = ';'.join([str(guard_classes),str(HELPERS),str(RUN/'runtime/classes'),str(RUN/'runtime/resources'),*jars])
    run('compile-guards-verified' if verified else 'compile-guards', [JAVAC, '-encoding', 'UTF-8', '-cp', cp, '-d', str(guard_classes),
                          'tools/diagnostics/V09ForecastGuardStudy.java'])
    baseline = json.loads((RUN/'baseline.json').read_bytes())
    for name, digest in baseline['artifactFiles'].items():
        if sha(ROOT/name) != digest:
            raise ValueError('Prior artifact changed: '+name)
    expected = {'artifact-off':'2cb3a6a254fd8f37e0c9f07e2aa36565f4d61027ee44f508a5f64a93d547d61a',
                'artifact-v09':'aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac',
                'default-artifact':'ab0954a4545f25222fce6bccf8fa11866d276266e96b4728b502c83996d847e5'}
    for label, digest in expected.items():
        if sha(RUN/label/'Player.java') != digest:
            raise ValueError('Disabled artifact changed: '+label)
    files = {path for path in RUN.rglob('*') if path.is_file() and path.name not in ('audit.py', 'archive.py')
             and '__pycache__' not in path.parts}
    files |= {ROOT/name for name in baseline['artifactFiles']}
    for folder in ['src/main/java/btk/core', 'src/main/java/btk/bot', 'src/main/java/btk/submit']:
        files |= {p for p in (ROOT/folder).rglob('*.java') if 'pduhard' not in p.parts}
    for name in ['tools/diagnostics/run_v09_ink_refresh.py','tools/diagnostics/V09ForecastCostStudy.java',
                 'tools/diagnostics/V09ForecastGuardStudy.java','src/test/java/btk/core/InkRefreshTest.java',
                 'src/test/java/btk/submit/IncrementalInkRefreshSubmissionTest.java',
                 'docs/v09-ink-refresh-v1.md','docs/v09-ink-refresh-v1-packaging-amendment.md',
                 'docs/evidence/neural-v7-control-boss.json',
                 'build/analysis/v09-full-action-v1/selection.json',
                 'replays/cg/903510176.json','replays/cg/boss/904146009.json',
                 'replays/cg/boss/904148181.json','replays/cg/boss/904147739.json']:
        path = ROOT/name
        if not path.is_file(): raise ValueError('Missing required frozen input: '+name)
        files.add(path)
    audit = json.loads((ROOT/'docs/evidence/neural-v7-control-boss.json').read_bytes())
    for row in audit['rows']:
        if row['gameId'] in [907134258,907134581,907134651,907134720]:
            files.add(ROOT/row['canonical']['path'])
    external = [JAVA,JAVAC,*jars,str(Path(JAVA).parent/'server/jvm.dll'),str(Path(JAVA).parent.parent/'lib/modules')]
    save(RUN/('verified-measurement-freeze.json' if verified else 'measurement-freeze.json'), {'files':{str(p.relative_to(ROOT)).replace('\\','/'):sha(p) for p in sorted(files)},
         'runtimeFiles':{str(p):sha(p) for p in external}, 'classpath':cp, 'newGamesBeforeProcess':0,
         'budgetsMs':{'first':486,'later':36,'ample':5000,'warmup':500},'promotion':False})
    print('Frozen',len(files),'inputs; exact disabled artifacts and baseline preserved')


def check_freeze():
    frozen = json.loads((RUN/'verified-measurement-freeze.json').read_bytes())
    for name,digest in frozen['files'].items():
        if sha(ROOT/name)!=digest: raise ValueError('Frozen input changed: '+name)
    for name,digest in frozen['runtimeFiles'].items():
        if sha(name)!=digest: raise ValueError('Runtime changed: '+name)
    return frozen


def measured(stage):
    frozen=check_freeze()
    prefix=[JAVA,'-cp',frozen['classpath']]
    if stage=='compare':
        run(stage,prefix+['btk.tools.V09ForecastCostStudy','compare','docs/evidence/neural-v7-control-boss.json',
             'build/analysis/v09-batched-refresh-v1/artifact',str(RUN/'artifact'),str(RUN/'compare'),
             'USE_INCREMENTAL_INK_REFRESH'])
    elif stage=='guards':
        if not json.loads((RUN/'compare/report.json').read_bytes())['amplePhaseComplete']:
            raise ValueError('Main parity required')
        run(stage,prefix+['btk.tools.V09ForecastGuardStudy',str(RUN/'guard-check-off'),str(RUN/'guard-check-on'),
                         str(RUN/'guards'),'USE_INCREMENTAL_INK_REFRESH'])
    elif stage=='process':
        if not json.loads((RUN/'guards/report.json').read_bytes())['amplePhaseComplete']:
            raise ValueError('Guard parity required')
        maps=['replays/cg/903510176.json','replays/cg/boss/904146009.json',
              'replays/cg/boss/904148181.json','replays/cg/boss/904147739.json']
        for i,path in enumerate(maps,1):
            # Shared frozen ProcessCheck, equivalent to gradlew procCheck; do not rebuild.
            run('process-'+str(i),prefix+['btk.runner.ProcessCheck', 'v09','2',path,
                '0',str(RUN/('process-'+str(i)+'.json')),'false',str(RUN/'artifact')], allowed=(0,1))
            report=json.loads((RUN/('process-'+str(i)+'.json')).read_bytes())
            if len(report['games'])!=2: raise ValueError('Incomplete process sample')
            check_freeze()
    check_freeze()


if __name__ == '__main__':
    stage = sys.argv[1]
    if stage == 'prepare':
        prepare()
    elif stage == 'compile-profile':
        HELPERS.mkdir(exist_ok=False)
        run(stage, [JAVAC, '-encoding', 'UTF-8', '-cp', OLD_CP, '-d', str(HELPERS),
                    'tools/diagnostics/V09ForecastCostStudy.java'])
    elif stage == 'profile':
        run(stage, [JAVA, '-XX:FlightRecorderOptions=stackdepth=128', '-cp', str(HELPERS)+';'+OLD_CP,
            'btk.tools.V09ForecastCostStudy', 'profile', 'docs/evidence/neural-v7-control-boss.json',
            'build/analysis/v09-batched-refresh-v1/artifact', str(RUN/'profile')])
    elif stage == 'build-artifacts':
        build_artifacts()
    elif stage == 'freeze':
        freeze()
    elif stage == 'freeze-verified':
        freeze(True)
    elif stage == 'guard-artifacts':
        for label,flag in [('guard-check-off',''),('guard-check-on',',USE_INCREMENTAL_INK_REFRESH=true')]:
            run('export-'+label,[str(ROOT/'gradlew.bat'),'submit','--offline','--console=plain',
                '-PsubmitVersion=current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true'+flag,
                '-PsubmitOut='+str(RUN/label)])
    elif stage in ('compare','guards','process'):
        measured(stage)
    elif stage == 'command':
        run(sys.argv[2], sys.argv[3:])
    else:
        raise ValueError('Unknown stage')
