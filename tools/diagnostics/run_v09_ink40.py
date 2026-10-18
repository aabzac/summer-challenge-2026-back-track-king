"""Write-once ink40 artifact and strict process experiment. No live calls or promotion."""
import hashlib
import json
import math
import os
from pathlib import Path
import shutil
import statistics
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
RUN = ROOT / 'build/analysis/v09-ink40-v1'
OLD = ROOT / 'build/analysis/v09-ink-refresh-v1'
LATEST = ROOT / 'build/analysis/v09-fast-path-v1'
DOC = ROOT / 'docs/v09-ink40-v1.md'
BASE = 'current?NOISE=0.1,USE_BATCHED_REFRESH=true'
B = BASE + ',TURN_BUDGET_MS=40'
A = B + ',USE_INCREMENTAL_INK_REFRESH=true'
SPECS = {'artifact': A, 'artifact-off': B, 'artifact36': BASE + ',USE_INCREMENTAL_INK_REFRESH=true'}
MAPS = ['replays/cg/903510176.json', 'replays/cg/boss/904146009.json',
        'replays/cg/boss/904148181.json', 'replays/cg/boss/904147739.json',
        'replays/cg/903184594.json', 'replays/cg/903182662.json',
        'replays/cg/903507919.json', 'replays/cg/903584040.json']
JAVA = 'C:/Program Files/Java/jdk-17/bin/java.exe'

def need(ok, why):
    if not ok:
        raise ValueError(why)

def read(p):
    return json.loads(Path(p).read_text(encoding='utf-8-sig'))

def sha(p):
    with Path(p).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()

def rel(p):
    return Path(p).resolve().relative_to(ROOT).as_posix()

def pin(p):
    return {'path': rel(p), 'sha256': sha(p), 'bytes': Path(p).stat().st_size}

def save(p, value):
    p.parent.mkdir(parents=True, exist_ok=True)
    with p.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, indent=2, allow_nan=False); stream.write('\n')

def now():
    return datetime.now(timezone.utc).isoformat()

def logged(label, command, allowed=(0,)):
    save(RUN / (label + '-command.json'), {'at': now(), 'command': list(map(str, command)), 'cwd': str(ROOT)})
    env = dict(os.environ, JAVA_HOME=str(Path(JAVA).parent.parent))
    with (RUN / (label + '.log')).open('x', encoding='utf-8') as log:
        child = subprocess.Popen(list(map(str, command)), cwd=ROOT, env=env, stdout=subprocess.PIPE,
                                 stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
        for line in child.stdout:
            log.write(line); log.flush()
            if any(x in line for x in ('FAIL', 'PASS', 'Exception', 'BUILD ', 'chars', ' -> ', 'errors=', 'max ')):
                print(line.rstrip(), flush=True)
        child.wait()
    save(RUN / (label + '-exit.json'), {'at': now(), 'exitCode': child.returncode, 'log': pin(RUN / (label + '.log'))})
    need(child.returncode in allowed, label + ' failed; retain evidence')
    return child.returncode

def inventory(dirs):
    return sorted(rel(p) for d in dirs for p in Path(d).rglob('*') if p.is_file())

def preserved():
    base = read(RUN / 'baseline.json')
    for section in ('artifactFiles', 'inheritedImmutableFiles', 'sourceFiles'):
        for name, digest in base[section].items():
            need(sha(ROOT / name) == digest, 'Changed preserved input: ' + name)
    need(sha(DOC) == base['protocol']['sha256'], 'Protocol changed')
    return base

def prepare():
    need(not RUN.exists(), 'Experiment directory exists; do not replace')
    old = read(OLD / 'verified-measurement-freeze.json')
    audit = read(OLD / 'independent-audit-v2.json')
    pub = read(ROOT / 'docs/evidence/v09-ink-refresh-v1.json')
    need(audit['complete'] and audit['status'] == 'PASS' and audit['preflight']['engineReplaysExact'] == 292,
         'Inherited evidence incomplete')
    need(pub['independentAudit']['sha256'] == sha(OLD / 'independent-audit-v2.json'), 'Published audit mismatch')
    immutable, omitted = {}, {}
    for name, digest in old['files'].items():
        if name.replace('\\', '/').startswith(('src/', 'tools/', 'docs/')):
            omitted[name] = digest
        else:
            need(sha(ROOT / name) == digest, 'Old immutable input changed: ' + name)
            immutable[name] = digest
    for name, row in audit['inputPins'].items():
        path = Path(name)
        if not path.resolve().is_relative_to(ROOT):
            need(sha(path) == row['sha256'], 'Old audited runtime changed: ' + name)
            continue
        relative = rel(path)
        if relative.startswith(('src/', 'tools/', 'docs/')):
            continue
        need(sha(path) == row['sha256'], 'Old audited input changed: ' + relative)
        immutable[relative] = row['sha256']
    for name, digest in old['runtimeFiles'].items():
        need(sha(name) == digest, 'Old runtime changed')
    native_roots = [OLD / 'runtime/classes', OLD / 'runtime/resources']
    expected_native = sorted(name for name in old['files']
                             if any((ROOT / name).resolve().is_relative_to(p.resolve()) for p in native_roots))
    need(inventory(native_roots) == expected_native, 'Old native inventory changed')
    artifacts = dict(read(LATEST / 'baseline.json')['artifactFiles'])
    for folder in ('artifact', 'artifact-off', 'artifact-v09', 'default-artifact', 'guard-off', 'guard-on'):
        for path in (LATEST / folder).rglob('*'):
            if path.is_file(): artifacts[rel(path)] = sha(path)
    for pattern in ('v09-ink-refresh-v1*', 'v09-ink-strength-v1*', 'v09-fast-path-v1*', 'v09-budget40-v1*'):
        for p in (ROOT / 'docs/evidence').glob(pattern): artifacts[rel(p)] = sha(p)
    for p in (OLD / 'verified-measurement-freeze.json', OLD / 'independent-audit-v2.json'):
        immutable[rel(p)] = sha(p)
    sources = {rel(p): sha(p) for tree in ('src/main/java/btk', 'src/test/java/btk')
               for p in (ROOT / tree).rglob('*.java') if not {'pduhard', 'ppo'}.intersection(p.parts)
               and p.name != 'PduhardBeamBotTest.java'}
    commit = subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip()
    save(RUN / 'baseline.json', {'at': now(), 'commit': commit, 'protocol': pin(DOC),
         'artifactFiles': artifacts, 'inheritedImmutableFiles': immutable, 'sourceFiles': sources,
         'oldMutableSourceHashReferences': omitted, 'oldRuntimeFiles': old['runtimeFiles'],
         'oldClasspath': old['classpath'], 'inheritedAudit': pin(OLD / 'independent-audit-v2.json'),
         'sourceProvenance': 'Native runtime from archived ink-refresh experiment; current workspace source pins describe export tooling only. No production edits.'})
    preserved()
    print('Prepared immutable old runtime/evidence and', len(artifacts), 'prior artifact files', flush=True)

def classes(directory):
    return {p.relative_to(directory).as_posix(): sha(p) for p in sorted(directory.rglob('*.class'))}

def class_digest(directory):
    result = hashlib.sha256()
    for p in sorted(directory.rglob('*.class')):
        result.update(p.relative_to(directory).as_posix().encode()); result.update(b'\0'); result.update(p.read_bytes())
    return result.hexdigest()

def artifact_checks():
    result = {}
    for name, spec in SPECS.items():
        folder = RUN / name; manifest = read(folder / 'manifest.json'); source = folder / 'Player.java'
        chars = len(source.read_bytes().decode('utf-8').encode('utf-16-le')) // 2
        need(manifest['strategy'] == spec and manifest['sha256'] == sha(source), 'Wrong artifact')
        need(chars == manifest['charactersUtf16'] and chars <= 100000, 'Source cap')
        need('pathNeighbors' not in source.read_text(encoding='utf-8'), 'Fast path must be pruned')
        result[name] = {'source': pin(source), 'manifest': pin(folder / 'manifest.json'),
                        'classesSha256': class_digest(folder / 'classes'), 'charactersUtf16': chars,
                        'params': manifest['params']}
    need(result['artifact36']['source']['sha256'] == '6217083e6319f69a29619f83c7de43d99876153b1dc9d043ca3376f5bccf8124', 'Historical ink36 source drift')
    need(result['artifact-off']['source']['sha256'] == '9510ff174e76297736144193a4c519f3728eac067d09614bb65e2f0ecd1b88d9', 'Historical B40 source drift')
    a, b = dict(result['artifact']['params']), dict(result['artifact-off']['params'])
    need(a.pop('USE_INCREMENTAL_INK_REFRESH') == 'true' and a == b, 'Not one-flag comparison')
    need(b['TURN_BUDGET_MS'] == '40' and b.get('USE_ROLLOUT_MISSED_DEADLINE', 'false') == 'false'
         and b.get('USE_FAST_CHEAPEST_PATH', 'false') == 'false', 'Wrong budget/experimental guards')
    old, new = classes(RUN / 'artifact36/classes'), classes(RUN / 'artifact/classes')
    need(old.keys() == new.keys(), 'Generated class inventory differs')
    changed = [n for n in old if old[n] != new[n]]
    need(set(changed) <= {'Player.class', 'Player$MainBot.class', 'Player$Params.class', 'Player$CGRunner.class'},
         'Non-budget generated class changed: ' + str(changed))
    return result, changed

def build():
    preserved()
    for name, spec in SPECS.items():
        need(not (RUN / name).exists(), 'Existing artifact')
        logged('export-' + name, [ROOT / 'gradlew.bat', 'submit', '--offline', '--console=plain',
                                  '-PsubmitVersion=' + spec, '-PsubmitOut=' + str(RUN / name)])
    result, changed = artifact_checks()
    save(RUN / 'export-verdict.json', {'complete': True, 'passed': True, 'artifacts': result,
                                      'changedGeneratedClasses40vs36': changed})
    preserved()

def freeze():
    base = preserved(); artifacts, changed = artifact_checks()
    need(not (RUN / 'measurement-freeze.json').exists(), 'Freeze exists')
    native = [RUN / 'runtime/classes', RUN / 'runtime/resources']
    for src, dst in zip((OLD / 'runtime/classes', OLD / 'runtime/resources'), native): shutil.copytree(src, dst)
    jars = [p for p in base['oldClasspath'].split(';') if p.endswith('.jar')]
    cp = ';'.join(map(str, [*native, *jars]))
    helper = RUN / 'helper-classes'; helper.mkdir()
    helper_source = ROOT / 'tools/diagnostics/V09Ink40ArtifactCheck.java'
    javac = str(Path(JAVA).with_name('javac.exe'))
    logged('compile-artifact-check', [javac, '-encoding', 'UTF-8', '-cp', cp, '-d', helper, helper_source])
    logged('artifact-check', [JAVA, '-cp', str(helper) + ';' + cp, 'btk.tools.V09Ink40ArtifactCheck', RUN])
    need(read(RUN / 'artifact-check.json')['passed'], 'Metadata check failed')
    files = {p for p in RUN.rglob('*') if p.is_file() and '__pycache__' not in p.parts}
    files.update(ROOT / p for field in ('artifactFiles', 'inheritedImmutableFiles', 'sourceFiles') for p in base[field])
    files.update([DOC, Path(__file__), helper_source, ROOT / 'build.gradle.kts', *[ROOT / p for p in MAPS]])
    runtime = dict(base['oldRuntimeFiles']); runtime[str(Path(sys.executable))] = sha(sys.executable)
    managed = [*native, helper, *[RUN / x / 'classes' for x in SPECS]]
    save(RUN / 'measurement-freeze.json', {'experiment': 'v09-ink40-v1', 'at': now(), 'sourceCommit': base['commit'],
        'files': {rel(p): sha(p) for p in sorted(files)}, 'runtimeFiles': runtime, 'java': JAVA, 'classpath': cp,
        'managedDirectories': [rel(p) for p in managed], 'managedInventory': inventory(managed),
        'classpathInventory': inventory(native), 'artifacts': artifacts, 'budgetsMs': {'first': 486, 'later': 40},
        'fullResponseLimitsMs': {'first': 1000, 'later': 50, 'grace': 0}, 'promotion': False, 'newBossGames': 0,
        'inheritedAudit': base['inheritedAudit'], 'changedGeneratedClasses40vs36': changed})
    verify(); print('Measurement freeze complete', flush=True)

def verify():
    frozen = read(RUN / 'measurement-freeze.json')
    for field in ('files', 'runtimeFiles'):
        for name, expected in frozen[field].items(): need(sha(ROOT / name) == expected, 'Frozen input drift: ' + name)
    need(inventory([ROOT / x for x in frozen['managedDirectories']]) == frozen['managedInventory'], 'Inventory drift')
    preserved()
    return frozen

def process():
    frozen = verify()
    need(not any((RUN / f'process-{i}-command.json').exists() for i in range(1, 9)), 'Process stage already attempted')
    for i, name in enumerate(MAPS, 1):
        verify()
        logged('process-' + str(i), [JAVA, '-cp', frozen['classpath'], 'btk.runner.ProcessCheck', B, '2', name,
               '0', RUN / f'process-{i}.json', 'false', RUN / 'artifact'], allowed=(0, 1))
        verify(); print('Completed fixed process panel', i, '/ 8', flush=True)
    readiness()

def distribution(values):
    values = sorted(values)
    def rank(p): return values[max(0, math.ceil(p * len(values)) - 1)] if values else None
    return {'count': len(values), 'medianMs': statistics.median(values) if values else None,
            'p95Ms': rank(.95), 'p99Ms': rank(.99), 'maxMs': max(values) if values else None,
            **{'above' + str(n) + 'Ms': sum(v > n for v in values) for n in (45, 48, 50)}}

def readiness():
    f = verify(); first, later, rows = [], [], []
    errors = timeouts = overruns = missing = 0
    for i, name in enumerate(MAPS, 1):
        p = RUN / f'process-{i}.json'; d = read(p); done = read(RUN / f'process-{i}-exit.json')
        need(d['sourceSha256'] == f['artifacts']['artifact']['source']['sha256']
             and d['classesSha256'] == f['artifacts']['artifact']['classesSha256'], 'Wrong process artifact')
        need(len(d['games']) == 2 and d['strict'] and d['graceMs'] == 0 and not d['ioDiagnostics'] and d['opponent'] == B, 'Wrong process settings')
        for j, g in enumerate(d['games']):
            need((g['game'], g['player'], g['seed'], g['map']) == (j, j, 7000+j, Path(name).name), 'Process identity')
            errors += sum(x is not None for x in g['result']['error'])
            timeouts += g['timeouts']; overruns += g['nominalOverruns']
            timings = g['timings']; missing += max(0, g['result']['turns'] - len(timings))
            need([t['turn'] for t in timings] == list(range(len(timings))), 'Unordered timing')
            for t in timings:
                need(t['budgetMs'] == (1000 if t['turn'] == 0 else 50) and t['graceMs'] == 0, 'Wrong IO budget')
                v = t['responseMs']
                if v is None: missing += 1
                else:
                    need(math.isfinite(v) and v >= 0, 'Bad response time')
                    (first if t['turn'] == 0 else later).append(v)
                if t['status'] != 'OK' and not g['timeouts'] and not g['nominalOverruns']: errors += 1
        rows.append({'report': pin(p), 'exitCode': done['exitCode'], 'problems': d['problems']})
    passed = errors == timeouts == overruns == missing == 0 and len(first) == 16 and all(r['exitCode'] == r['problems'] == 0 for r in rows) and max(first) <= 1000 and max(later) <= 50
    summary = {'complete': True, 'passed': passed, 'games': 16, 'reports': rows, 'errors': errors,
               'timeouts': timeouts, 'overruns': overruns, 'missing': missing, 'first': distribution(first),
               'later': distribution(later), 'noRetryOrReplacement': True}
    save(RUN / 'process-summary.json', summary)
    pins = [RUN / 'measurement-freeze.json', RUN / 'baseline.json', RUN / 'export-verdict.json',
            RUN / 'artifact-check.json', RUN / 'process-summary.json']
    pins += [p for i in range(1, 9) for p in (RUN / f'process-{i}.json', RUN / f'process-{i}-command.json', RUN / f'process-{i}-exit.json', RUN / f'process-{i}.log')]
    save(RUN / 'readiness.json', {'complete': True, 'status': 'PASS' if passed else 'NOT_READY', 'strengthReady': passed,
        'engineExact': 292, 'ampleParityReused': 524, 'parity': {'positions': 524, 'pass': True, 'reused': True},
        'inheritedAudit': f['inheritedAudit'],
        'process': {'games': 16, 'pass': passed, 'errors': errors, 'timeouts': timeouts, 'overruns': overruns, 'missing': missing},
        'artifacts': f['artifacts'], 'inputPins': {str(p.resolve()): {'sha256': sha(p), 'bytes': p.stat().st_size} for p in pins},
        'promotion': False, 'newBossGames': 0})
    print(json.dumps(summary), flush=True)

if __name__ == '__main__':
    {'prepare': prepare, 'build': build, 'freeze': freeze, 'verify': verify,
     'process': process, 'readiness': readiness}[sys.argv[1]]()
