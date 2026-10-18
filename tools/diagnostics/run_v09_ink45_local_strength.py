"""Scoped native ink45/ink40 budget comparison; timing prerequisite explicitly waived."""
from __future__ import annotations
import json
import importlib.util
import hashlib
from pathlib import Path
import re
import subprocess
import sys
from run_v09_fast_path_strength import need, read, sha, save, now

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import run_practical_v4 as schedule
_spec = importlib.util.spec_from_file_location('ink45_private_gate', ROOT / 'tools/practical_gate_v4.py')
gate = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(gate)

NAME = 'v09-ink45-strength-v1'
RUN = ROOT / 'build/analysis' / NAME
BENCH = ROOT / 'build/bench' / NAME
DIAG = ROOT / 'build/analysis/v09-ink40-v1'
HARNESS = ROOT / 'build/analysis/v09-ink45-strength-harness-v2'
DOC = ROOT / 'docs/v09-ink45-strength-v1.md'
BASE = 401018000
A = 'current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=45,USE_INCREMENTAL_INK_REFRESH=true'
B = 'current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40,USE_INCREMENTAL_INK_REFRESH=true'
FLAG = 'TURN_BUDGET_MS'
JAVA = Path('C:/Program Files/Java/jdk-17/bin/java.exe')
ARM_BUDGETS = [486, 486, 45, 40]
ARTIFACTS = {'A': ROOT / 'build/analysis/v09-ink45-boss-v1/artifact', 'B': DIAG / 'artifact'}
AUTHORIZATION = {'timeoutGateWaived': True, 'localStrengthOnly': True, 'promotion': False,
    'noNewProcessGames': True, 'newBossGames': 0, 'newProcessGames': 0,
    'scope': 'Native incremental-ink45 vs identical incremental-ink40; prior timing failures retained. No promotion.'}


def scoped_turn_budget(data, side):
    need(side in ('A', 'B') and data[side.lower()] == {'A': A, 'B': B}[side], 'Exact registered arm recipe required')
    params = data['params' + side]
    expected = '45' if side == 'A' else '40'
    need(params.get('TURN_BUDGET_MS') == expected and params.get('USE_ROLLOUT_POLICY') == 'true'
         and params.get('USE_INCREMENTAL_INK_REFRESH') == params.get('USE_BATCHED_REFRESH') == 'true'
         and params.get('NOISE') == '0.1'
         and all(params.get(k, 'false') == 'false' for k in ('USE_HEURISTIC_POLICY', 'USE_NEURAL_TARGET_PROPOSAL',
             'USE_FAST_CHEAPEST_PATH', 'USE_ROLLOUT_MISSED_DEADLINE')), 'Registered budget/policy mismatch')
    return int(expected)


# Private module only: raw reports, shared files and v4 stopping arithmetic stay unchanged.
gate._turn_budget = scoped_turn_budget


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


def params_guard(pa, pb):
    aa, bb = dict(pa), dict(pb)
    need(aa.pop('TURN_BUDGET_MS') == '45' and bb.pop('TURN_BUDGET_MS') == '40' and aa == bb,
         'Only TURN_BUDGET_MS may differ')
    scoped_turn_budget({'a': A, 'paramsA': pa}, 'A')
    scoped_turn_budget({'b': B, 'paramsB': pb}, 'B')


def managed_inventory(directories):
    return sorted(rel(p) for directory in directories for p in (ROOT / directory).rglob('*') if p.is_file())


def diagnostic_evidence():
    baseline = read(RUN / 'runtime-baseline.json')
    need(baseline['complete'] is True and baseline['passed'] is True
         and baseline['allOtherNativeClassesAndResourcesIdentical'] is True
         and baseline['newGames'] == baseline['policyCalls'] == 0, 'Frozen native runtime qualification required')
    for field in ('files', 'runtimeFiles'):
        for name, digest in baseline[field].items():
            need(sha(ROOT / name) == digest, 'Runtime input changed: ' + name)
    need(Path(baseline['java']).resolve() == JAVA.resolve(), 'Wrong Java executable')
    need(classpath_inventory(baseline['classpath']) == baseline['classpathInventory']
         and managed_inventory(baseline['managedDirectories']) == baseline['managedInventory'], 'Native inventory changed')
    diagnostic = read(RUN / 'runtime-diagnostic.json')
    need(diagnostic['complete'] is True and diagnostic['passed'] is True
         and diagnostic['newGames'] == diagnostic['policyCalls'] == 0, 'Native configuration diagnostic failed')
    need(sha(RUN / 'runtime-diagnostic.json') == baseline['diagnostic']['sha256'], 'Runtime diagnostic changed')
    need([diagnostic[k] for k in gate.ARM_BUDGET_FIELDS] == ARM_BUDGETS
         and diagnostic['seatBudgetsMs'] == [[45, 40], [40, 45]], 'Actual native identity/seat budgets differ')
    need(diagnostic['candidate']['spec'] == A and diagnostic['control']['spec'] == B, 'Native arm spec changed')
    artifacts = {}
    for side, folder in ARTIFACTS.items():
        manifest = read(folder / 'manifest.json')
        source = folder / 'Player.java'
        need(manifest['strategy'] == {'A': A, 'B': B}[side] and sha(source) == manifest['sha256']
             and 0 < manifest['charactersUtf16'] <= 100000, 'Artifact identity/cap mismatch')
        expected_sha = {'A': '1118fc8ad63d8b58b7d1f2a5f0e50c4605314e63d370518a93d19ae1cb7953a4',
                        'B': 'aff2dc9bf48d2f9a141012c3d47fc079bb9af9354cdfa846ef40832804db75a5'}[side]
        need(manifest['sha256'] == expected_sha, 'Unexpected exact artifact')
        need(diagnostic['candidate' if side == 'A' else 'control']['params'] == manifest['params'], 'Native/artifact params differ')
        executable = hashlib.sha256()
        for path in sorted((folder / 'classes').rglob('*.class')):
            executable.update(path.relative_to(folder / 'classes').as_posix().encode('utf-8'))
            executable.update(b'\0'); executable.update(path.read_bytes())
        artifacts[side] = {'directory': rel(folder), 'source': pin(source), 'manifest': pin(folder / 'manifest.json'),
            'params': manifest['params'], 'charactersUtf16': manifest['charactersUtf16'], 'buildId': manifest['buildId'],
            'classesSha256': executable.hexdigest()}
    params_guard(artifacts['A']['params'], artifacts['B']['params'])
    ready = read(DIAG / 'readiness.json')
    old_audit = read(DIAG / 'independent-readiness-audit.json')
    process45 = read(ROOT / 'build/analysis/v09-ink45-boss-v1/process-summary.json')
    need(ready['complete'] is True and ready['status'] == 'NOT_READY' and ready['strengthReady'] is False
         and ready['engineExact'] == 292 and ready['parity'] == {'positions': 524, 'pass': True, 'reused': True}
         and ready['process']['games'] == 16 and ready['process']['timeouts'] == 2
         and old_audit['complete'] is True and old_audit['status'] == 'PASS', 'Retained engine/parity/timing evidence differs')
    need(process45['complete'] is True and process45['games'] == process45['problems'] == 2
         and process45['exitCode'] == 1 and process45['passingTimingClaim'] is False,
         'Original45 timing diagnostic must remain failed')
    return baseline, artifacts, diagnostic


def classpath():
    return read(RUN / 'runtime-baseline.json')['classpath']


def classpath_inventory(cp):
    return sorted(rel(p) for entry in cp.split(';') if Path(entry).is_dir()
                  for p in Path(entry).rglob('*') if p.is_file())


def prepare():
    need(DOC.is_file() and not (RUN / 'input-plan.json').exists() and not BENCH.exists()
         and not any(schedule.pool_path(NAME, b).exists() for b, *_ in schedule.BLOCKS), 'Fresh experiment and protocol required')
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
    RUN.mkdir(parents=True, exist_ok=True)
    helper = ROOT / 'tools/diagnostics/V09Ink45StrengthPreflight.java'
    dest = RUN / 'helper-classes'
    dest.mkdir()
    logged('compile-preflight', [str(JAVA.with_name('javac.exe')), '-encoding', 'UTF-8',
                               '-cp', classpath(), '-d', str(dest), str(helper)])
    save(RUN / 'prior-pools.json', {'pools': [item['manifest'] for item in intervals]})
    logged('reserved-index', [str(JAVA), '-cp', str(dest) + ';' + classpath(), 'btk.tools.V09Ink45StrengthPreflight',
        'index-pools', str(RUN / 'prior-pools.json'), str(RUN / 'reserved-maps.json')])
    absorb(RUN / 'reserved-maps.json', read(RUN / 'reserved-maps.json')['gamesPerMap'])
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
    logged('pool-preflight', [str(JAVA), '-cp', str(dest) + ';' + classpath(),
           'btk.tools.V09Ink45StrengthPreflight', str(RUN / 'excluded-maps.json'), *map(str, pools),
           str(RUN / 'pool-preflight.json')])
    print('Prepared all 500 maps against', len(union), 'exclusions; zero games', flush=True)


def freeze():
    need(not BENCH.exists(), 'Freeze must precede games')
    baseline, artifacts, diagnostic = diagnostic_evidence()
    plan, preview = read(RUN / 'input-plan.json'), read(RUN / 'pool-preflight.json')
    need(plan['protocol'] == pin(DOC), 'Protocol changed since preparation')
    need(preview['passed'] and preview['complete'] and preview['distinctProspectiveMaps'] == 500,
         'Complete map preflight required')
    params_guard(preview['candidate']['params'], preview['control']['params'])
    need(preview['candidate']['params'] == artifacts['A']['params'] and preview['control']['params'] == artifacts['B']['params'],
         'Native runtime differs from retained submission params')
    need([preview[k] for k in gate.ARM_BUDGET_FIELDS] == ARM_BUDGETS, 'Actual budgets differ')
    checked = read(HARNESS / 'driver-tests-exit.json')
    need(checked['exitCode'] == 0 and checked['tests'] >= 20 and checked['noGamesOrBotCalls'] is True,
         'Successful offline driver checks required')
    need(sha(HARNESS / 'driver-tests.log') == checked['logSha256'], 'Synthetic log changed')
    for name, digest in read(HARNESS / 'source-pins.json').items():
        need(sha(ROOT / name) == digest, 'Strength source changed since synthetic checks: ' + name)
    files = dict(baseline['files'])
    files.update(read(RUN / 'excluded-maps.json')['sourcePins'])
    additions = [DOC, Path(__file__), ROOT / 'tools/diagnostics/V09Ink45StrengthPreflight.java',
        ROOT / 'tools/diagnostics/test_v09_ink45_local_strength.py',
        ROOT / 'tools/diagnostics/run_v09_fast_path_strength.py',
        ROOT / 'tools/diagnostics/test_v09_ink40_strength.py',
        ROOT / 'tools/diagnostics/run_v09_ink40_strength.py',
        DIAG / 'readiness.json', DIAG / 'independent-readiness-audit.json',
        ROOT / 'build/analysis/v09-ink45-boss-v1/process.json', ROOT / 'build/analysis/v09-ink45-boss-v1/process-summary.json',
        ROOT / 'build/analysis/v09-ink45-boss-v1/export-verdict.json']
    additions += [DIAG / ('process-' + str(i) + '.json') for i in range(1, 9)]
    additions += [ROOT / row['path'] for row in plan['pools']]
    additions += list((ROOT / 'tools').glob('practical_gate*.py')) + [ROOT / 'tools/boss_practical_gate.py', ROOT / 'tools/run_practical_v4.py']
    additions += [p for folder in (RUN, HARNESS, *ARTIFACTS.values()) for p in folder.rglob('*')
                  if p.is_file() and not {'__pycache__', 'fake-fixtures'}.intersection(p.parts)
                  and p.name not in ('audit_results.py', 'archive.py')]
    additions += list((ROOT / 'build/analysis/v09-ink45-strength-harness-v1').glob('*'))
    prior = [ROOT / 'docs/evidence' / (name + suffix) for name in
             ('v09-ink40-v1', 'v09-ink40-strength-v1', 'v09-ink45-boss-v1')
             for suffix in ('.json', '-artifacts.json', '-artifacts.zip')]
    additions += prior
    files.update({rel(p): sha(p) for p in additions})
    runtime = dict(baseline['runtimeFiles']); runtime[str(Path(sys.executable))] = sha(sys.executable)
    inventory = classpath_inventory(classpath())
    need(inventory and all(p in files for p in inventory), 'Unpinned native classpath additions')
    save(RUN / 'freeze.json', {'experiment': NAME, 'at': now(), 'sourceCommit': plan['sourceCommit'],
        'files': files, 'runtimeFiles': runtime, 'classpath': classpath(), 'java': str(JAVA),
        'classpathInventory': inventory, 'managedDirectories': baseline['managedDirectories'],
        'managedInventory': baseline['managedInventory'], 'candidate': A, 'control': B, 'artifacts': artifacts,
        'baseSeed': BASE, 'threads': 2, 'strengthOnly': True, **AUTHORIZATION,
        'runtimeBaseline': pin(RUN / 'runtime-baseline.json'), 'priorPublishedEvidence': [pin(p) for p in prior],
        'driverTests': pin(HARNESS / 'driver-tests-exit.json'),
        'retainedTimingStatus': 'NOT_READY', 'effectiveBudgetsMs': dict(zip(gate.ARM_BUDGET_FIELDS, ARM_BUDGETS))})
    print('Frozen', len(files), 'workspace and', len(runtime), 'external inputs;45/40 local-only waiver', flush=True)


def verify():
    frozen = read(RUN / 'freeze.json')
    need(frozen['candidate'] == A and frozen['control'] == B and frozen['baseSeed'] == BASE, 'Frozen recipe mismatch')
    need(all(frozen[k] == v for k, v in AUTHORIZATION.items()), 'Local-only timing waiver changed')
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
    need([d[k] for k in gate.ARM_BUDGET_FIELDS] == ARM_BUDGETS, 'Wrong actual budgets')
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
    save(RUN / 'strength-start.json', {'at': now(), 'freeze': pin(RUN / 'freeze.json'), **AUTHORIZATION})
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
                 'freeze': pin(RUN / 'freeze.json'), **AUTHORIZATION})
            return
    raise ValueError('Sequence ended without final local decision')


if __name__ == '__main__':
    actions = {'prepare': prepare, 'freeze': freeze, 'verify': verify, 'strength': strength}
    need(len(sys.argv) == 2 and sys.argv[1] in actions, 'Mode prepare|freeze|verify|strength required')
    actions[sys.argv[1]]()
