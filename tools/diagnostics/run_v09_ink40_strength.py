"""Conditional, write-once ink ON/OFF strength at 40 ms; no live calls or promotion.

Reuse pure file/journal primitives and the unchanged practical-v4 reader/schedule.
Use the new readiness evidence and retained pre-fast-path native runtime, never
the current build tree. Only strength invokes bots; prepare indexes maps only.
"""
from __future__ import annotations
import json
from pathlib import Path
import re
import subprocess
import sys
from run_v09_fast_path_strength import need, read, sha, save, now

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import practical_gate_v4 as gate
import run_practical_v4 as schedule

NAME = 'v09-ink40-strength-v1'
RUN = ROOT / 'build/analysis' / NAME
BENCH = ROOT / 'build/bench' / NAME
DIAG = ROOT / 'build/analysis/v09-ink40-v1'
HARNESS = ROOT / 'build/analysis/v09-ink40-strength-harness-v1'
DOC = ROOT / 'docs/v09-ink40-v1.md'
BASE = 301018000
A = 'current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40,USE_INCREMENTAL_INK_REFRESH=true'
B = 'current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40'
FLAG = 'USE_INCREMENTAL_INK_REFRESH'
JAVA = Path('C:/Program Files/Java/jdk-17/bin/java.exe')


def rel(p):
    return Path(p).resolve().relative_to(ROOT).as_posix()


def pin(p):
    return {'path': rel(p), 'sha256': sha(p), 'bytes': Path(p).stat().st_size}


def logged(label, command):
    save(RUN / (label + '-command.json'), {'at': now(), 'command': command, 'cwd': str(ROOT)})
    with (RUN / (label + '.log')).open('x', encoding='utf-8') as log:
        process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, encoding='utf-8', errors='replace')
        for line in process.stdout:
            log.write(line)
            log.flush()
            if any(s in line for s in ('/300', '/200', '/500', 'report:', 'PASS', 'Exception', 'FAIL')):
                print(line.rstrip(), flush=True)
        process.wait()
    save(RUN / (label + '-exit.json'), {'at': now(), 'exitCode': process.returncode,
                                      'log': pin(RUN / (label + '.log'))})
    need(process.returncode == 0, label + ' failed; preserve outputs, no automatic retry')


def readiness_guards(ready):
    """A pure gate, also exercised with synthetic rejection cases before play."""
    need(ready['complete'] is True and ready['status'] == 'PASS' and ready['strengthReady'] is True,
         'Diagnostic readiness does not authorize strength play')
    need(ready['engineExact'] == 292, 'New engine replay validation required')
    need(ready['parity']['positions'] == 524 and ready['parity']['pass'] is True,
         'Complete exact ample parity required')
    process = ready['process']
    need(process['games'] == 16 and process['pass'] is True
         and all(type(process[k]) is int and process[k] == 0
                 for k in ('errors', 'timeouts', 'overruns', 'missing')), 'Strict process gate did not pass')
    need({'artifact', 'artifact-off'}.issubset(ready['artifacts']), 'Both tested artifact identities required')
    need(all(type(row['charactersUtf16']) is int and 0 < row['charactersUtf16'] <= 100000
             for row in ready['artifacts'].values()), 'Source character cap exceeded')


def check_pins(pins, label):
    need(bool(pins), label + ' pins missing')
    for name, row in pins.items():
        path = ROOT / name
        need(sha(path) == row['sha256'] and path.stat().st_size == row['bytes'], label + ' changed: ' + name)


def managed_inventory(directories):
    return sorted(rel(p) for directory in directories for p in (ROOT / directory).rglob('*') if p.is_file())


def diagnostic_evidence():
    frozen = read(DIAG / 'measurement-freeze.json')
    for field in ('files', 'runtimeFiles'):
        for name, digest in frozen[field].items():
            need(sha(ROOT / name) == digest, 'New diagnostic frozen input changed: ' + name)
    need(managed_inventory(frozen['managedDirectories']) == frozen['managedInventory'],
         'Diagnostic executable inventory changed')
    ready = read(DIAG / 'readiness.json')
    readiness_guards(ready)
    need(ready['artifacts'] == frozen['artifacts'], 'Readiness artifacts differ from tested freeze')
    check_pins(ready['inputPins'], 'Diagnostic readiness input')
    audit = read(DIAG / 'independent-readiness-audit.json')
    need(audit['complete'] is True and audit['status'] == 'PASS', 'Independent diagnostic audit required')
    check_pins(audit['inputPins'], 'Independent diagnostic audit input')
    audit_readiness = {str((ROOT / name).resolve()): row for name, row in audit['inputPins'].items()}
    ready_pin = audit_readiness.get(str((DIAG / 'readiness.json').resolve()), {})
    need(ready_pin.get('sha256') == sha(DIAG / 'readiness.json')
         and ready_pin.get('bytes') == (DIAG / 'readiness.json').stat().st_size,
         'Independent audit must pin the passing readiness record')
    need(Path(frozen['java']).resolve() == JAVA.resolve(), 'Unexpected diagnostic Java executable')
    baseline = read(DIAG / 'baseline.json')
    need(bool(baseline['artifactFiles']), 'Prior artifact preservation pins required')
    for name, digest in baseline['artifactFiles'].items():
        need(sha(ROOT / name) == digest, 'Selected/older artifact changed: ' + name)
    cp = classpath()
    dirs = [Path(entry).resolve() for entry in cp.split(';') if Path(entry).is_dir()]
    expected = sorted(name for name in frozen['files']
                      if any((ROOT / name).resolve().is_relative_to(folder) for folder in dirs))
    need(classpath_inventory(cp) == expected and expected, 'Diagnostic classpath inventory changed')
    for label, row in ready['artifacts'].items():
        manifest = read(DIAG / label / 'manifest.json')
        for part, filename in (('source', 'Player.java'), ('manifest', 'manifest.json')):
            path = DIAG / label / filename
            need((ROOT / row[part]['path']).resolve() == path.resolve()
                 and sha(path) == row[part]['sha256'] and path.stat().st_size == row[part]['bytes'],
                 'Diagnostic artifact identity changed: ' + label)
        need(manifest['sha256'] == row['source']['sha256'] and manifest['params'] == row['params']
             and manifest['charactersUtf16'] == row['charactersUtf16'], 'Manifest/readiness mismatch: ' + label)
    return frozen, ready, audit


def classpath():
    return read(DIAG / 'measurement-freeze.json')['classpath']


def classpath_inventory(cp):
    return sorted(rel(p) for entry in cp.split(';') if Path(entry).is_dir()
                  for p in Path(entry).rglob('*') if p.is_file())


def prepare():
    need(DOC.is_file() and not RUN.exists() and not BENCH.exists(), 'Fresh experiment and protocol required')
    diagnostic_evidence()
    proposed = [(BASE + off, BASE + off + n - 1) for _, _, n, off in schedule.BLOCKS]
    sources, intervals, union = {}, [], set()

    def remember(p):
        sources[rel(p)] = sha(p)

    def absorb(p, hashes):
        values = set(hashes)
        need(values and all(re.fullmatch('[0-9a-f]{64}', h) for h in values), 'Bad core map hash index')
        union.update(values)
        remember(p)

    for p in sorted((ROOT / 'bench/pools').glob('*.json')):
        d = read(p)
        need(d.get('generator') == 'cg-sha1prng' and type(d.get('seedStart')) is int
             and type(d.get('pairs')) is int and d['pairs'] > 0, 'Malformed registered pool')
        lo, hi = d['seedStart'], d['seedStart'] + d['pairs'] - 1
        need(all(b < lo or a > hi for a, b in proposed), 'Reserved seed overlap: ' + p.name)
        intervals.append({'manifest': pin(p), 'start': lo, 'end': hi})
        remember(p)
    p = ROOT / 'build/analysis/v09-budget100-local-v1/excluded-maps.json'
    absorb(p, read(p)['gamesPerMap'])
    for p in sorted((ROOT / 'build/analysis').glob('*/pool-preflight.json')):
        d = read(p)
        need(d.get('passed') is True and d.get('complete') is True, 'Incomplete prior map preflight')
        absorb(p, [r['coreMapHash'] for pool in d['pools'] for r in pool['maps']])
    for p in sorted((ROOT / 'build/bench').rglob('*.json')):
        try:
            d = read(p)
        except (ValueError, UnicodeDecodeError):
            continue
        maps = d.get('gamesPerMap') if isinstance(d, dict) else None
        if isinstance(maps, dict) and maps:
            need(all(type(n) is int and n > 0 for n in maps.values()), 'Malformed prior map counts')
            absorb(p, maps)
    for label in ('resume-map-audit', 'budget40-map-audit'):
        p = ROOT / 'build/analysis/v09-boss-budget40-followup-v1' / (label + '.json')
        d = read(p)
        need(d['passed'] and d['complete'], 'Prior boss map audit incomplete')
        absorb(p, [row['coreMapHash'] for row in d['maps']])
    RUN.mkdir(parents=True, exist_ok=False)
    # Map indexer consumes the shared core and canonical replay parser; no game calls.
    files = sorted({p for tree in (ROOT / 'replays').glob('cg*') if tree.is_dir()
                    for p in tree.rglob('*.json') if p.stem.isdigit()
                    and not {'raw', '_metadata', 'rendered'}.intersection(x.lower() for x in p.parts)})
    need(files, 'No saved CG exclusion inputs')
    chunks, chunk = [], []
    for p in files:
        if sum(len(str(q)) + 1 for q in chunk) + len(str(p)) > 20000:
            chunks.append(chunk)
            chunk = []
        chunk.append(p)
    if chunk:
        chunks.append(chunk)
    for i, group in enumerate(chunks, 1):
        out = RUN / f'cg-maps-{i}.json'
        logged(f'cg-index-{i}', [str(JAVA), '-cp', classpath(), 'btk.tools.NeuralV2Verify',
                               'maps', ','.join(str(p) for p in group), 'unused', str(out)])
        absorb(out, read(out)['gamesPerMap'])
        for p in group:
            remember(p)
    pools = [schedule.ensure_pool(NAME, b, n, BASE + off) for b, _, n, off in schedule.BLOCKS]
    save(RUN / 'excluded-maps.json', {'gamesPerMap': dict.fromkeys(sorted(union), 1),
         'sourcePins': sources, 'includesReservedUnplayedMaps': True,
         'hashScheme': 'sha256-utf8-Protocol.initBlock(initialState,0)'})
    save(RUN / 'input-plan.json', {'experiment': NAME, 'createdAt': now(),
         'sourceCommit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
         'candidate': A, 'control': B, 'protocol': pin(DOC), 'pools': [pin(p) for p in pools],
         'priorSeedIntervals': intervals, 'excludedMaps': pin(RUN / 'excluded-maps.json'),
         'excludedMapCount': len(union), 'newGames': 0, 'promotion': False})
    helper = ROOT / 'tools/diagnostics/V09Ink40StrengthPreflight.java'
    dest = RUN / 'helper-classes'
    dest.mkdir()
    logged('compile-preflight', [str(JAVA.with_name('javac.exe')), '-encoding', 'UTF-8',
                               '-cp', classpath(), '-d', str(dest), str(helper)])
    logged('pool-preflight', [str(JAVA), '-cp', str(dest) + ';' + classpath(),
           'btk.tools.V09Ink40StrengthPreflight', str(RUN / 'excluded-maps.json'), *map(str, pools),
           str(RUN / 'pool-preflight.json')])
    print('Prepared all 500 maps against', len(union), 'exclusions; zero games', flush=True)


def freeze():
    need(not BENCH.exists(), 'Freeze must precede games')
    diagnostic, ready, audit = diagnostic_evidence()
    plan, preview = read(RUN / 'input-plan.json'), read(RUN / 'pool-preflight.json')
    need(plan['protocol'] == pin(DOC), 'Protocol changed since preparation')
    need(preview['passed'] and preview['complete'] and preview['distinctProspectiveMaps'] == 500,
         'Complete map preflight required')
    artifacts = {side: ready['artifacts'][name] for side, name in [('A', 'artifact'), ('B', 'artifact-off')]}
    pa, pb = dict(artifacts['A']['params']), dict(artifacts['B']['params'])
    need(pa.pop(FLAG) == 'true' and FLAG not in pb and pa == pb, 'Recipe differs beyond incremental-ink flag')
    need(pa.get('TURN_BUDGET_MS') == '40' and pa['NOISE'] == '0.1'
         and pa['USE_BATCHED_REFRESH'] == 'true'
         and pa.get('USE_FAST_CHEAPEST_PATH', 'false') == 'false'
         and pa.get('USE_ROLLOUT_MISSED_DEADLINE', 'false') == 'false',
         'Wrong base policy')
    need(preview['candidate']['params'] == artifacts['A']['params'] and preview['control']['params'] == artifacts['B']['params'],
         'Native runtime differs from tested submission params')
    need([preview[k] for k in gate.ARM_BUDGET_FIELDS] == [486, 486, 40, 40], 'Actual budgets differ')
    files = dict(diagnostic['files'])
    files.update(read(RUN / 'excluded-maps.json')['sourcePins'])
    additions = [DOC, Path(__file__), ROOT / 'tools/diagnostics/V09Ink40StrengthPreflight.java',
                 ROOT / 'tools/diagnostics/test_v09_ink40_strength.py',
                 ROOT / 'tools/diagnostics/run_v09_fast_path_strength.py',
                 DIAG / 'measurement-freeze.json', DIAG / 'readiness.json', DIAG / 'independent-readiness-audit.json',
                 DIAG / 'baseline.json']
    additions += [ROOT / row['path'] for row in plan['pools']]
    additions += list((ROOT / 'tools').glob('practical_gate*.py')) + list((ROOT / 'tools').glob('boss*.py'))
    additions += [ROOT / 'tools/run_practical_v4.py']
    additions += [p for folder in (RUN, HARNESS) for p in folder.rglob('*')
                  if p.is_file() and '__pycache__' not in p.parts]
    files.update({rel(p): sha(p) for p in additions})
    for evidence in (ready, audit):
        files.update({rel(ROOT / p): row['sha256'] for p, row in evidence['inputPins'].items()
                      if (ROOT / p).resolve().is_relative_to(ROOT)})
    files.update(read(DIAG / 'baseline.json')['artifactFiles'])
    runtime = dict(diagnostic['runtimeFiles'])
    runtime[str(Path(sys.executable))] = sha(sys.executable)
    inventory = classpath_inventory(classpath())
    need(inventory and all(p in files for p in inventory), 'Unpinned classpath additions')
    checked = read(HARNESS / 'driver-tests-exit.json')
    need(checked['exitCode'] == 0 and checked['tests'] == 28, 'Successful synthetic driver checks required')
    for name, digest in read(HARNESS / 'source-pins.json').items():
        need(sha(ROOT / name) == digest, 'Strength source changed since synthetic checks: ' + name)
    save(RUN / 'freeze.json', {'experiment': NAME, 'at': now(), 'sourceCommit': plan['sourceCommit'],
         'files': files, 'runtimeFiles': runtime, 'classpath': classpath(), 'java': str(JAVA),
         'classpathInventory': inventory,
         'managedDirectories': diagnostic['managedDirectories'], 'managedInventory': diagnostic['managedInventory'],
         'candidate': A, 'control': B, 'artifacts': artifacts, 'baseSeed': BASE, 'threads': 2,
         'diagnosticReadinessReused': pin(DIAG / 'readiness.json'),
         'independentDiagnosticAudit': pin(DIAG / 'independent-readiness-audit.json'),
         'strengthOnly': True, 'promotion': False})
    print('Frozen', len(files), 'files and', len(runtime), 'runtime files', flush=True)


def verify():
    frozen = read(RUN / 'freeze.json')
    need(frozen['candidate'] == A and frozen['control'] == B and frozen['baseSeed'] == BASE, 'Frozen recipe mismatch')
    need(classpath_inventory(frozen['classpath']) == frozen['classpathInventory'], 'Classpath inventory changed')
    need(managed_inventory(frozen['managedDirectories']) == frozen['managedInventory'],
         'Diagnostic executable inventory changed')
    for field in ('files', 'runtimeFiles'):
        for name, digest in frozen[field].items():
            need(sha(ROOT / name) == digest, 'Frozen input changed: ' + name)
    return frozen


def inspect(report, index, frozen):
    d = report['data']
    name, games, pairs, offset = schedule.BLOCKS[index]
    preview = read(RUN / 'pool-preflight.json')['pools'][index]
    need((d['a'], d['b'], d['threads'], d['games'], d['baseSeed']) == (A, B, 2, games, BASE + offset), 'Wrong block settings')
    need(d['paramsA'] == frozen['artifacts']['A']['params'] and d['paramsB'] == frozen['artifacts']['B']['params'], 'Parameters changed')
    need([d[k] for k in gate.ARM_BUDGET_FIELDS] == [486, 486, 40, 40], 'Wrong actual budgets')
    need([p['seed'] for p in d['pairs']] == [p['seed'] for p in preview['maps']]
         and [p['mapHash'] for p in d['pairs']] == [p['coreMapHash'] for p in preview['maps']], 'Unregistered map/seed')
    need(d['poolSha256'] == sha(schedule.pool_path(NAME, name)), 'Pool identity mismatch')
    need(d['poolId'] == NAME + '-' + name and d['poolPurpose'] == 'confirmation', 'Wrong pool declaration')
    need(d['javaVersion'] == read(RUN / 'pool-preflight.json')['javaVersion'], 'Java version changed')
    expected = {rel(RUN / 'excluded-maps.json'): sha(RUN / 'excluded-maps.json')}
    need({k.replace('\\', '/'): v for k, v in d['excludedReports'].items()} == expected, 'Exclusions mismatch')
    need(not set(report['maps']).intersection(read(RUN / 'excluded-maps.json')['gamesPerMap']), 'Prior map overlap')
    need(d['errorsA'] == d['errorsB'] == 0, 'Bot errors block clean pass')
    for p in d['pairs']:
        for key in ('aAsP0', 'aAsP1'):
            g = p[key]
            need(not any(g['error']), 'Game error retained')
            expected_winner = -1 if g['score'][0] == g['score'][1] else int(g['score'][1] > g['score'][0])
            need(g['winner'] == expected_winner, 'Score/winner mismatch')


def strength():
    frozen = verify()
    need(not BENCH.exists(), 'Existing strength attempt cannot be replaced')
    BENCH.mkdir(parents=True)
    save(RUN / 'strength-start.json', {'at': now(), 'freeze': pin(RUN / 'freeze.json')})
    reports = []
    for index, (name, games, pairs, offset) in enumerate(schedule.BLOCKS):
        verify()
        report = BENCH / (name + '.json')
        command = [frozen['java'], '-cp', frozen['classpath'], 'btk.runner.BenchmarkRunner', A, B,
                   '', '', '2', '', rel(report), 'cg', rel(schedule.pool_path(NAME, name)),
                   rel(RUN / 'excluded-maps.json')]
        logged(name, command)
        parsed = gate.read_report(report, games)
        inspect(parsed, index, frozen)
        verify()
        reports.append(parsed)
        verdict = gate.assess(*reports)
        save(BENCH / (name + '-decision.json'), verdict)
        print(json.dumps({k: v for k, v in verdict.items() if k not in ('reports', 'boss')}, indent=2), flush=True)
        stage = verdict['directStage']
        if stage.startswith('STOP_FUTILITY') or stage in ('PASS_1000', 'NOT_PASSED'):
            save(RUN / 'strength-summary.json', {'experiment': NAME, 'complete': True, 'at': now(),
                 'localDecision': stage, 'gate': verdict, 'reports': [pin(Path(r['path'])) for r in reports],
                 'freeze': pin(RUN / 'freeze.json'), 'promotion': False, 'newBossGames': 0,
                 'scope': 'Incremental ink effect on identical batched40 base. Fast path and deadline correction remain OFF. Local pass alone is not the full promotion gate.'})
            return
    raise ValueError('Sequence ended without final local decision')


if __name__ == '__main__':
    actions = {'prepare': prepare, 'freeze': freeze, 'verify': verify, 'strength': strength}
    need(len(sys.argv) == 2 and sys.argv[1] in actions, 'Mode prepare|freeze|verify|strength required')
    actions[sys.argv[1]]()
