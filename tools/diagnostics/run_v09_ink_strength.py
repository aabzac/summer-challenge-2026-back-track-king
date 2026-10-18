"""Write-once local incremental-ink strength experiment; no HTTP or promotion.

Reuse the reviewed ink-refresh runtime and shared benchmark/v4 gate unchanged.
Only strength invokes bots; prepare and freeze only inspect/map/compile helpers.
"""
from __future__ import annotations
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
import practical_gate_v4 as gate
import run_practical_v4 as schedule

NAME = 'v09-ink-strength-v1'
RUN = ROOT / 'build/analysis' / NAME
BENCH = ROOT / 'build/bench' / NAME
OLD = ROOT / 'build/analysis/v09-ink-refresh-v1'
DOC = ROOT / 'docs' / (NAME + '.md')
BASE = 281018000
A = 'current?NOISE=0.1,USE_BATCHED_REFRESH=true,USE_INCREMENTAL_INK_REFRESH=true'
B = 'current?NOISE=0.1,USE_BATCHED_REFRESH=true'
FLAG = 'USE_INCREMENTAL_INK_REFRESH'
JAVA = Path('C:/Program Files/Java/jdk-17/bin/java.exe')


def need(ok, reason):
    if not ok:
        raise ValueError(reason)


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
        json.dump(value, stream, indent=2, allow_nan=False)
        stream.write('\n')


def now():
    return datetime.now(timezone.utc).isoformat()


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


def old_evidence():
    frozen = read(OLD / 'verified-measurement-freeze.json')
    for field in ('files', 'runtimeFiles'):
        for name, digest in frozen[field].items():
            need(sha(ROOT / name) == digest, 'Prior frozen input changed: ' + name)
    audit = read(OLD / 'independent-audit-v2.json')
    published = read(ROOT / 'docs/evidence/v09-ink-refresh-v1.json')
    need(audit['complete'] and audit['status'] == 'PASS' and audit['process']['pass'], 'Prior readiness incomplete')
    need(published['independentAudit']['sha256'] == sha(OLD / 'independent-audit-v2.json'), 'Published audit drift')
    for name, row in audit['inputPins'].items():
        need(sha(name) == row['sha256'], 'Prior audited evidence changed: ' + name)
    baseline = read(OLD / 'baseline.json')
    for name, digest in baseline['artifactFiles'].items():
        need(sha(ROOT / name) == digest, 'Selected/older artifact changed: ' + name)
    return frozen, audit


def classpath():
    prior = read(OLD / 'verified-measurement-freeze.json')['classpath'].split(';')
    return ';'.join(p for p in prior if '/runtime/' in p.replace('\\', '/') or p.endswith('.jar'))


def classpath_inventory(cp):
    return sorted(rel(p) for entry in cp.split(';') if Path(entry).is_dir()
                  for p in Path(entry).rglob('*') if p.is_file())


def prepare():
    need(DOC.is_file() and not RUN.exists() and not BENCH.exists(), 'Fresh experiment and protocol required')
    old_evidence()
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
    helper = ROOT / 'tools/diagnostics/V09InkStrengthPreflight.java'
    dest = RUN / 'helper-classes'
    dest.mkdir()
    logged('compile-preflight', [str(JAVA.with_name('javac.exe')), '-encoding', 'UTF-8',
                               '-cp', classpath(), '-d', str(dest), str(helper)])
    logged('pool-preflight', [str(JAVA), '-cp', str(dest) + ';' + classpath(),
           'btk.tools.V09InkStrengthPreflight', str(RUN / 'excluded-maps.json'), *map(str, pools),
           str(RUN / 'pool-preflight.json')])
    print('Prepared all500 maps against', len(union), 'exclusions; zero games', flush=True)


def freeze():
    need(not BENCH.exists(), 'Freeze must precede games')
    old, audit = old_evidence()
    plan, preview = read(RUN / 'input-plan.json'), read(RUN / 'pool-preflight.json')
    need(plan['protocol'] == pin(DOC), 'Protocol changed since preparation')
    need(preview['passed'] and preview['complete'] and preview['distinctProspectiveMaps'] == 500,
         'Complete map preflight required')
    artifacts = {side: audit['artifacts'][name] for side, name in [('A', 'artifact'), ('B', 'artifact-off')]}
    pa, pb = dict(artifacts['A']['params']), dict(artifacts['B']['params'])
    need(pa.pop(FLAG) == 'true' and FLAG not in pb and pa == pb, 'Recipe differs beyond incremental flag')
    need(pa.get('TURN_BUDGET_MS', '36') == '36' and pa['NOISE'] == '0.1'
         and pa['USE_BATCHED_REFRESH'] == 'true' and pa.get('USE_ROLLOUT_MISSED_DEADLINE', 'false') == 'false',
         'Wrong base policy')
    need(preview['candidate']['params'] == artifacts['A']['params'] and preview['control']['params'] == artifacts['B']['params'],
         'Native runtime differs from tested submission params')
    need([preview[k] for k in gate.ARM_BUDGET_FIELDS] == [486, 486, 36, 36], 'Actual budgets differ')
    files = dict(old['files'])
    files.update(read(RUN / 'excluded-maps.json')['sourcePins'])
    additions = [DOC, Path(__file__), ROOT / 'tools/diagnostics/V09InkStrengthPreflight.java',
                 ROOT / 'tools/diagnostics/test_v09_ink_strength.py',
                 OLD / 'verified-measurement-freeze.json', OLD / 'independent-audit-v2.json',
                 ROOT / 'docs/evidence/v09-ink-refresh-v1.json']
    additions += [ROOT / row['path'] for row in plan['pools']]
    additions += list((ROOT / 'tools').glob('practical_gate*.py')) + list((ROOT / 'tools').glob('boss*.py'))
    additions += [ROOT / 'tools/run_practical_v4.py']
    additions += [p for p in RUN.rglob('*') if p.is_file() and '__pycache__' not in p.parts]
    files.update({rel(p): sha(p) for p in additions})
    files.update({rel(Path(p)): row['sha256'] for p, row in audit['inputPins'].items()
                  if Path(p).is_relative_to(ROOT)})
    runtime = dict(old['runtimeFiles'])
    runtime[str(Path(sys.executable))] = sha(sys.executable)
    inventory = classpath_inventory(classpath())
    need(inventory and all(p in files for p in inventory), 'Unpinned classpath additions')
    need(read(RUN / 'driver-tests-workspace-exit.json')['exitCode'] == 0, 'Successful synthetic driver checks required')
    save(RUN / 'freeze.json', {'experiment': NAME, 'at': now(), 'sourceCommit': plan['sourceCommit'],
         'files': files, 'runtimeFiles': runtime, 'classpath': classpath(), 'java': str(JAVA),
         'classpathInventory': inventory,
         'candidate': A, 'control': B, 'artifacts': artifacts, 'baseSeed': BASE, 'threads': 2,
         'processReadinessReused': pin(OLD / 'independent-audit-v2.json'),
         'strengthOnly': True, 'promotion': False})
    print('Frozen', len(files), 'files and', len(runtime), 'runtime files', flush=True)


def verify():
    frozen = read(RUN / 'freeze.json')
    need(frozen['candidate'] == A and frozen['control'] == B and frozen['baseSeed'] == BASE, 'Frozen recipe mismatch')
    need(classpath_inventory(frozen['classpath']) == frozen['classpathInventory'], 'Classpath inventory changed')
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
    need([d[k] for k in gate.ARM_BUDGET_FIELDS] == [486, 486, 36, 36], 'Wrong actual budgets')
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
                 'scope': 'Incremental effect on identical batched36 base. Local pass alone is not the full promotion gate.'})
            return
    raise ValueError('Sequence ended without final local decision')


if __name__ == '__main__':
    actions = {'prepare': prepare, 'freeze': freeze, 'verify': verify, 'strength': strength}
    need(len(sys.argv) == 2 and sys.argv[1] in actions, 'Mode prepare|freeze|verify|strength required')
    actions[sys.argv[1]]()
