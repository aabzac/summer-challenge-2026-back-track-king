"""Once-only local 100/36-ms budget diagnostic; no process gate or live services.

Uses the separately compiled tools-only LocalBudgetBenchmark against a pinned
runtime. Both bot recipes are identical. Only the caller's later allowance differs.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
NAME = 'v09-budget100-local-v1'
RUN = ROOT / 'build/analysis' / NAME
BENCH = ROOT / 'build/bench' / NAME
SPEC = 'current?NOISE=0.1,USE_BATCHED_REFRESH=true'
BASE_SEED = 261616000
BLOCKS = [('block1', 300, 150, 0), ('block2', 200, 100, 100000), ('block3', 500, 250, 200000)]
BUDGETS = {'firstA': 486, 'firstB': 486, 'laterA': 100, 'laterB': 36}
HASH = set('0123456789abcdef')


def require(ok, message):
    if not ok:
        raise ValueError(message)


def read(path):
    return json.loads(Path(path).read_bytes())


def sha(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')


def pin(path):
    return {'path': Path(path).resolve().relative_to(ROOT.resolve()).as_posix(), 'sha256': sha(path)}


def verified_pin(row):
    path = (ROOT / row['path']).resolve()
    require(path.is_relative_to(ROOT.resolve()) and path.is_file(), 'Missing/outside workspace input')
    require(sha(path) == row['sha256'], 'Pinned input changed: ' + row['path'])
    return path


def valid_hash(value):
    return isinstance(value, str) and len(value) == 64 and set(value) <= HASH


def validate_freeze(frozen):
    require(frozen['experiment'] == NAME and frozen['preflightPassed'] is True
            and frozen['localOnly'] is True and frozen['promotion'] is False,
            'Completed local-only preflight required')
    require(frozen['candidate']['spec'] == frozen['control']['spec'] == SPEC
            and frozen['candidate']['params'] == frozen['control']['params'], 'Identical exact recipes required')
    params = frozen['candidate']['params']
    require(params.get('TURN_BUDGET_MS', '36') == '36' and params.get('NOISE') == '0.1'
            and params.get('USE_BATCHED_REFRESH') == 'true' and params.get('USE_ROLLOUT_POLICY') == 'true'
            and params.get('USE_HEURISTIC_POLICY', 'false') == 'false'
            and params.get('USE_NEURAL_TARGET_PROPOSAL', 'false') == 'false'
            and params.get('USE_ROLLOUT_MISSED_DEADLINE', 'false') == 'false', 'Wrong fixed native base policy')
    require(frozen['budgets'] == BUDGETS and frozen['threads'] == 2, 'Wrong local budgets/workers')
    require(frozen['files'] and frozen['runtimeFiles'], 'Missing frozen source/runtime inputs')
    for name, digest in frozen['files'].items():
        verified_pin({'path': name, 'sha256': digest})
    for name, digest in frozen['runtimeFiles'].items():
        require(sha(Path(name)) == digest, 'Pinned runtime changed')
    prefix = frozen['javaCommandPrefix']
    require(isinstance(prefix, list) and len(prefix) == 3 and prefix[1] == '-cp'
            and all(isinstance(x, str) and x for x in prefix), 'Expected pinned java -cp prefix')
    require(str(Path(prefix[0])) in frozen['runtimeFiles'], 'Java executable is not runtime-pinned')
    # Each classpath directory/file must be covered, and each current file must be pinned.
    for entry in prefix[2].split(';'):
        path = Path(entry).resolve()
        require(path.exists(), 'Missing classpath entry')
        if path.is_dir():
            members = [p for p in path.rglob('*') if p.is_file()]
            require(members, 'Empty classpath directory')
            for member in members:
                require(member.is_relative_to(ROOT.resolve()), 'Classpath directory outside isolated workspace')
                name = member.relative_to(ROOT.resolve()).as_posix()
                require(frozen['files'].get(name) == sha(member), 'Unpinned classpath member: ' + name)
        else:
            relative = path.relative_to(ROOT.resolve()).as_posix() if path.is_relative_to(ROOT.resolve()) else None
            require(frozen['runtimeFiles'].get(str(path), frozen['files'].get(relative)) == sha(path),
                    'Unpinned classpath file')
    schedule = frozen['strength']
    require(schedule['baseSeed'] == BASE_SEED and schedule['prospectiveMapCount'] == 500
            and len(schedule['blocks']) == 3, 'Fixed complete500-map schedule required')
    exclusions = read(verified_pin(schedule['excludedMaps']))['gamesPerMap']
    require(exclusions and all(valid_hash(k) and type(v) is int and v > 0 for k, v in exclusions.items()),
            'Invalid prior-map exclusions')
    preflight = read(verified_pin(schedule['mapPreflight']))
    require(preflight['passed'] is True and preflight['complete'] is True
            and preflight['newGames'] == preflight['botCalls'] == 0
            and preflight['distinctProspectiveMaps'] == 500
            and preflight['excludedMaps'] == len(exclusions) and len(preflight['pools']) == 3,
            'Complete shared-core500-map preflight required')
    for row in preflight['inputs']:
        verified_pin(row)
    require(any(row['sha256'] == schedule['excludedMaps']['sha256']
                and (ROOT / row['path']).resolve() == (ROOT / schedule['excludedMaps']['path']).resolve()
                for row in preflight['inputs']), 'Core preflight used other exclusions')
    seen = set()
    for index, (name, games, pairs, offset) in enumerate(BLOCKS):
        block = schedule['blocks'][index]
        start = BASE_SEED + offset
        require((block['name'], block['games'], block['pairs'], block['seedStart']) == (name, games, pairs, start)
                and block['seeds'] == list(range(start, start + pairs)), 'Registered schedule differs')
        pool = read(verified_pin(block['pool']))
        require(pool == {'schemaVersion': 1, 'id': NAME + '-' + name, 'purpose': 'development',
                         'generator': 'cg-sha1prng', 'seedStart': start, 'pairs': pairs}, 'Pool manifest differs')
        hashes = block['mapHashes']
        require(len(hashes) == pairs and all(valid_hash(h) for h in hashes)
                and len(set(hashes)) == pairs and not seen.intersection(hashes)
                and not set(exclusions).intersection(hashes), 'Prospective map overlap/incomplete hashes')
        audited = preflight['pools'][index]
        require([row['seed'] for row in audited['maps']] == block['seeds']
                and [row['coreMapHash'] for row in audited['maps']] == hashes
                and audited['manifest']['sha256'] == block['pool']['sha256'],
                'Frozen pool differs from prospective shared-core audit')
        seen.update(hashes)
    require(len(seen) == 500, 'All500 prospective maps must be pinned before play')
    return frozen


def load_freeze():
    return validate_freeze(read(RUN / 'preflight-freeze.json'))


def inspect_report(path, frozen, index):
    data = read(path)
    require(data['timingProfile'] == 'LOCAL_DIAGNOSTIC' and data['experimentalTimingOnly'] is True
            and data['localOnly'] is True and data['promotion'] is False and data['promotionEligible'] is False
            and data['promotionGatePassed'] is False and data['processReadinessAssessed'] is False
            and data['practicalDirectStage'] == 'NOT_APPLICABLE',
            'Local-only report semantics required')
    require(data['a'] == data['b'] == SPEC and data['paramsA'] == data['paramsB'] == frozen['candidate']['params']
            and data['threads'] == 2, 'Report recipes/workers differ')
    require(tuple(data[k] for k in ('firstSearchBudgetMsA', 'firstSearchBudgetMsB',
                                   'turnSearchBudgetMsA', 'turnSearchBudgetMsB')) == (486, 486, 100, 36),
            'Wrong actual per-arm budgets')
    block = frozen['strength']['blocks'][index]
    require(data['complete'] is True and data['status'] == 'COMPLETE_LOCAL_DIAGNOSTIC'
            and data['plannedGames'] == block['games'] and 'failure' not in data,
            'Incomplete/failed raw diagnostic report')
    require(type(data['games']) is int and data['games'] == block['games']
            and data['baseSeed'] == block['seedStart'], 'Incomplete/wrong-size block')
    require(data['errorsA'] == data['errorsB'] == 0, 'Bot errors invalidate the diagnostic')
    require(data['excludedReports'], 'Missing raw exclusion provenance')
    exclusion = frozen['strength']['excludedMaps']
    require(len(data['excludedReports']) == 1, 'Unexpected exclusion input')
    for name, digest in data['excludedReports'].items():
        require((ROOT / name).resolve() == (ROOT / exclusion['path']).resolve()
                and digest == exclusion['sha256'], 'Exclusion input differs')
    pairs = data['pairs']
    require(len(pairs) == block['pairs'] and [p['index'] for p in pairs] == list(range(block['pairs']))
            and [p['seed'] for p in pairs] == block['seeds']
            and [p['mapHash'] for p in pairs] == block['mapHashes'], 'Raw paired map/seed coverage differs')
    wins = losses = draws = 0
    for pair in pairs:
        for seat, key in enumerate(('aAsP0', 'aAsP1')):
            game = pair[key]
            require(game['error'] == [None, None], 'Hidden raw game error')
            scores = game['score']
            require(len(scores) == 2 and all(type(v) is int and v >= 0 for v in scores), 'Invalid raw scores')
            winner = -1 if scores[0] == scores[1] else int(scores[1] > scores[0])
            require(type(game['winner']) is int and game['winner'] == winner
                    and type(game['turns']) is int and game['turns'] > 0, 'Raw winner/turns inconsistent')
            wins += winner == seat
            losses += winner not in (-1, seat)
            draws += winner == -1
    require((data['winsA'], data['winsB'], data['draws']) == (wins, losses, draws), 'Raw outcome totals disagree')
    require(data['gamesPerMap'] == dict.fromkeys(block['mapHashes'], 2), 'Raw map multiplicities differ')
    inspect_observations(data)
    return {'path': pin(path)['path'], 'sha256': sha(path), 'games': data['games'], 'wins': wins,
            'losses': losses, 'draws': draws, 'halfWins': 2 * wins + draws, 'mapHashes': block['mapHashes']}


def inspect_observations(data):
    observations = data['pairObservations']
    require(len(observations) == len(data['pairs']), 'Incomplete per-pair work observations')
    totals = {(side, phase): {} for side in ('a', 'b') for phase in ('first', 'later')}
    required = {'calls', 'totalNs', 'maxNs', 'above36Ms', 'above50Ms', 'above100Ms', 'aboveAllowance',
                'rolloutObservedCalls', 'routeQueries', 'completedComparisons', 'simulatedTurns',
                'ordinarySearchComplete', 'networkPhaseComplete', 'selectedOpening', 'selectedNetwork',
                'networkSearchStarted', 'networkEligible', 'networkEvaluated', 'networkRoots', 'networkComparisons'}
    for pair, observed in zip(data['pairs'], observations):
        require((observed['index'], observed['seed']) == (pair['index'], pair['seed']), 'Work observation identity differs')
        for game in ('aAsP0', 'aAsP1'):
            for side in ('a', 'b'):
                for phase in ('first', 'later'):
                    row = observed[game][side][phase]
                    require(set(row) == required and all(type(v) is int and v >= 0 for v in row.values()),
                            'Invalid/incomplete raw native work counters')
                    calls = 1 if phase == 'first' else pair[game]['turns'] - 1
                    require(row['calls'] == row['rolloutObservedCalls'] == calls
                            and row['totalNs'] >= row['maxNs']
                            and row['maxNs'] * calls >= row['totalNs']
                            and row['above100Ms'] <= row['above50Ms'] <= row['above36Ms'] <= calls
                            and row['aboveAllowance'] <= calls, 'Native call coverage/timing counters differ')
                    if phase == 'later':
                        require(row['aboveAllowance'] == row['above100Ms' if side == 'a' else 'above36Ms'],
                                'Observed allowance threshold differs')
                    total = totals[side, phase]
                    for key, value in row.items():
                        total[key] = max(total.get(key, 0), value) if key == 'maxNs' else total.get(key, 0) + value
    for (side, phase), expected in totals.items():
        actual = data['observations'][side][phase]
        require(all(actual[key] == value for key, value in expected.items()), 'Aggregate work observations disagree')
        n = expected['calls']
        derived = {'meanNativePlayMs': expected['totalNs'] / (1000000 * n) if n else 0,
                   'maxNativePlayMs': expected['maxNs'] / 1000000,
                   'meanRouteQueries': expected['routeQueries'] / n if n else 0,
                   'meanCompletedComparisons': expected['completedComparisons'] / n if n else 0,
                   'meanSimulatedTurns': expected['simulatedTurns'] / n if n else 0}
        require(all(type(actual[key]) in (int, float) and math.isfinite(actual[key])
                    and math.isclose(actual[key], value, rel_tol=1e-12, abs_tol=1e-12)
                    for key, value in derived.items()), 'Derived work means disagree')


def assess(reports):
    require(1 <= len(reports) <= 3, 'One to three complete blocks required')
    used = set()
    for index, report in enumerate(reports):
        require(report['games'] == BLOCKS[index][1] and len(report['mapHashes']) == BLOCKS[index][2]
                and not used.intersection(report['mapHashes']), 'Incomplete/reused pooled block')
        used.update(report['mapHashes'])
        if index:
            earlier = reports[:index]
            require(sum(r['halfWins'] for r in earlier) >= sum(r['games'] for r in earlier),
                    'Evidence continues after a declared futility stop')
    games = sum(r['games'] for r in reports)
    half = sum(r['halfWins'] for r in reports)
    status = 'COMPLETE_DIAGNOSTIC' if len(reports) == 3 else (
        'STOP_FUTILITY_' + str(games) if half < games else 'CONTINUE')
    score = half / (2 * games)
    radius = math.sqrt(math.log(40) / (2 * len(used)))
    return {'status': status, 'games': games, 'maps': len(used), 'wins': sum(r['wins'] for r in reports),
            'losses': sum(r['losses'] for r in reports), 'draws': sum(r['draws'] for r in reports),
            'halfWins': half, 'points': half / 2, 'score': score,
            'mapGroupedHoeffding95': [max(0, score - radius), min(1, score + radius)],
            'confidenceRole': 'Descriptive; independent representative map groups assumed; no promotion gate',
            'localOnly': True, 'promotion': False, 'promotionEligible': False,
            'processStage': 'NOT_APPLICABLE_LOCAL_DIAGNOSTIC', 'bossStage': 'NOT_REQUESTED'}


def ensure_unstarted():
    require(not (RUN / 'strength-summary.json').exists(), 'Preserve completed/interrupted experiment')
    for name, *_ in BLOCKS:
        for suffix in ('.json', '.log', '-command.json', '-exit.json', '-decision.json'):
            require(not (BENCH / (name + suffix)).exists(), 'Measurement already attempted; no automatic retry')


def run_block(frozen, index):
    block = frozen['strength']['blocks'][index]
    name = block['name']
    out = BENCH / (name + '.json')
    command = frozen['javaCommandPrefix'] + ['btk.runner.LocalBudgetBenchmark', str(block['games']),
        str(block['seedStart']), str((ROOT / frozen['strength']['excludedMaps']['path']).resolve()), str(out.resolve())]
    for suffix in ('.json', '.log', '-command.json', '-exit.json', '-decision.json'):
        require(not (BENCH / (name + suffix)).exists(), 'Block already attempted; no automatic retry')
    save(BENCH / (name + '-command.json'), {'command': command, 'cwd': str(ROOT),
         'freeze': pin(RUN / 'preflight-freeze.json'), 'pool': block['pool'],
         'budgets': BUDGETS, 'localOnly': True, 'promotion': False, 'automaticRetry': False})
    with (BENCH / (name + '.log')).open('x', encoding='utf-8') as log:
        process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, encoding='utf-8', errors='replace')
        for line in process.stdout:
            log.write(line)
            log.flush()
            print(line.rstrip(), flush=True)
        process.wait()
    save(BENCH / (name + '-exit.json'), {'exitCode': process.returncode, 'automaticRetry': False})
    require(process.returncode == 0 and out.is_file(), 'Failed/incomplete measured block; retain all raw evidence')
    return inspect_report(out, frozen, index)


def strength_stage():
    frozen = load_freeze()
    freeze_pin = pin(RUN / 'preflight-freeze.json')
    ensure_unstarted()
    reports = []
    try:
        for index, (name, *_rest) in enumerate(BLOCKS):
            verified_pin(freeze_pin)
            load_freeze()
            reports.append(run_block(frozen, index))
            verified_pin(freeze_pin)
            load_freeze()
            verdict = assess(reports)
            save(BENCH / (name + '-decision.json'), verdict)
            if verdict['status'] != 'CONTINUE':
                summary = dict(verdict, experiment=NAME, complete=True, freeze=freeze_pin,
                               reports=[{k: v for k, v in row.items() if k != 'mapHashes'} for row in reports],
                               scope='Same native batched policy; later caller budget100 versus36. No external deadline or arena-readiness inference.')
                save(RUN / 'strength-summary.json', summary)
                print(json.dumps(summary, indent=2), flush=True)
                return summary
    except Exception as error:
        save(RUN / 'strength-summary.json', {'experiment': NAME, 'status': 'INCOMPLETE', 'complete': False,
             'localOnly': True, 'promotion': False, 'promotionEligible': False, 'automaticRetry': False,
             'errorType': type(error).__name__, 'error': str(error),
             'retainedCompleteReports': [{k: v for k, v in row.items() if k != 'mapHashes'} for row in reports]})
        raise
    raise RuntimeError('Fixed schedule ended without a terminal diagnostic result')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', choices=('validate', 'strength'))
    args = parser.parse_args()
    if args.stage == 'validate':
        load_freeze()
        print('PASS: pinned local-only500-map schedule; zero games created')
    else:
        strength_stage()
