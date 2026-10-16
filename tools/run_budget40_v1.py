"""Fixed budget40 experiment: strict process first, conditional local strength second.

Requires the reviewed preflight-freeze.json. Never uploads, promotes, retries a
measurement, edits a frozen artifact, or starts strength without all16 process passes.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import math
import statistics
import subprocess
from pathlib import Path

import practical_gate_v4 as gate
import run_practical_v4 as runner

ROOT = Path(__file__).resolve().parents[1]
NAME = 'v09-budget40-v1'
RUN = ROOT / 'build/analysis' / NAME
BENCH = ROOT / 'build/bench' / NAME
CANDIDATE = 'current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40'
CONTROL = 'current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=36'
BASE_SEED = 261316000


def require(ok, message):
    if not ok:
        raise ValueError(message)


def read(path):
    return json.loads(Path(path).read_bytes())


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def save(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open('x', encoding='utf-8', newline='\n') as stream:
        json.dump(value, stream, indent=2)
        stream.write('\n')


def pin(path):
    return {'path': path.relative_to(ROOT).as_posix(), 'sha256': sha(path)}


def load_freeze():
    frozen = read(RUN / 'preflight-freeze.json')
    require(frozen['experiment'] == NAME and frozen['preflightPassed'], 'Completed preflight required')
    require(frozen['candidate']['spec'] == CANDIDATE and frozen['control']['spec'] == CONTROL,
            'Wrong fixed recipes')
    require(frozen['strength']['baseSeed'] == BASE_SEED and len(frozen['processMaps']) == 8,
            'Fixed schedule differs')
    for name, digest in frozen['files'].items():
        require(sha(ROOT / name) == digest, 'Frozen input changed: ' + name)
    for name, digest in frozen['runtimeFiles'].items():
        require(sha(name) == digest, 'Runtime changed: ' + name)
    params = frozen['candidate']['params'].copy()
    require(params.pop('TURN_BUDGET_MS') == '40', 'Candidate budget differs')
    require(params == frozen['control']['params'], 'Unexpected strategy difference')
    require(params['USE_BATCHED_REFRESH'] == 'true' and params['NOISE'] == '0.1'
            and params.get('USE_ROLLOUT_MISSED_DEADLINE', 'false') == 'false', 'Wrong base policy')
    return frozen


def distribution(values):
    if not values:
        return {'count': 0, 'p50': None, 'p95': None, 'p99': None, 'p999': None, 'max': None}
    ordered = sorted(values)
    result = {'count': len(values), 'p50': statistics.median(ordered), 'max': ordered[-1]}
    for label, q in (('p95', .95), ('p99', .99), ('p999', .999)):
        result[label] = ordered[math.ceil(q * len(ordered)) - 1]
    return result


def inspect_process(report, frozen, expected_map):
    candidate = frozen['candidate']
    require(report['sourceSha256'] == candidate['sourceSha256']
            and report['classesSha256'] == candidate['classesSha256'], 'Process artifact differs')
    require(report['strict'] is True and report['graceMs'] == 0 and report['ioDiagnostics'] is False,
            'Strict uninstrumented process sample required')
    require(report['opponent'] == CONTROL and report['opponentParams'] == frozen['control']['params'],
            'Process control differs')
    games = report['games']
    require(len(games) == 2, 'Incomplete process call')
    rows = []
    for index, game in enumerate(games):
        require(game['game'] == index and game['player'] == index and game['seed'] == 7000 + index,
                'Wrong seat/seed coverage')
        require(Path(game['map']).name == Path(expected_map).name, 'Wrong process map')
        timings = game['timings']
        require(timings and [t['turn'] for t in timings] == list(range(len(timings))),
                'Missing/out-of-order process timings')
        for timing in timings:
            require(timing['budgetMs'] == (1000 if timing['turn'] == 0 else 50)
                    and timing['graceMs'] == 0, 'External deadline changed')
            response = timing['responseMs']
            require(response is None or (type(response) in (int, float)
                    and math.isfinite(response) and response >= 0), 'Invalid observed process response time')
        errors = game['result']['error']
        failed = bool(any(x is not None for x in errors) or game['timeouts'] or game['nominalOverruns']
                      or any(t['status'] != 'OK' or t['responseMs'] is None
                             or t['responseMs'] > t['budgetMs'] for t in timings))
        # Failed sessions may stop before the last timing is captured. They remain failures;
        # only a clean, complete response sequence can establish a process pass.
        require(failed or len(timings) == game['result']['turns'], 'Incomplete clean process timing coverage')
        rows.append({'map': expected_map, 'seat': index, 'failed': failed,
                     'errors': errors, 'timeouts': game['timeouts'], 'nominalOverruns': game['nominalOverruns'],
                     'timings': timings})
    return rows


def run_logged(command, prefix):
    save(RUN / f'{prefix}-command.json', {'command': command, 'cwd': str(ROOT)})
    with (RUN / f'{prefix}.log').open('x', encoding='utf-8') as log:
        process = subprocess.Popen(command, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                   text=True, encoding='utf-8', errors='replace')
        for line in process.stdout:
            log.write(line)
            log.flush()
            if any(x in line for x in ('game ', 'deadlines:', 'PROBLEM', 'FAIL', 'Exception', 'timing report:')):
                print(line.rstrip(), flush=True)
        process.wait()
    save(RUN / f'{prefix}-exit.json', {'exitCode': process.returncode})
    return process.returncode


def process_stage():
    frozen = load_freeze()
    require(not (RUN / 'process-summary.json').exists(), 'Preserve previous process stage')
    for index in range(1, 9):
        require(not (RUN / f'process-{index}.json').exists()
                and not (RUN / f'process-{index}-command.json').exists(), 'Process stage already attempted')
    rows, reports = [], []
    failure = None
    try:
        for index, item in enumerate(frozen['processMaps'], 1):
            load_freeze()
            output = RUN / f'process-{index}.json'
            command = [str(ROOT / 'gradlew.bat'), 'procCheck', '--console=plain',
                '-Pbot=' + CONTROL, '-Pgames=2', '-Pmap=' + item['path'], '-PgraceMs=0',
                '-PprocessDiagnostics=false', '-PsubmitOut=' + frozen['candidate']['directory'],
                '-Pout=' + output.relative_to(ROOT).as_posix()]
            code = run_logged(command, f'process-{index}')
            require(output.exists(), 'Missing process report; infrastructure-incomplete sample')
            report = read(output)
            inspected = inspect_process(report, frozen, item['path'])
            require(code in (0, 1), 'Unexpected process runner exit')
            require(code == 0 or any(x['failed'] for x in inspected), 'Unexplained failed invocation')
            rows.extend(inspected)
            reports.append(pin(output))
        load_freeze()
    except Exception as error:
        failure = f'{type(error).__name__}: {error}'
    complete = len(rows) == 16 and failure is None
    first = [t['responseMs'] for r in rows for t in r['timings'] if t['turn'] == 0 and t['responseMs'] is not None]
    later = [t['responseMs'] for r in rows for t in r['timings'] if t['turn'] > 0 and t['responseMs'] is not None]
    failed = sum(r['failed'] for r in rows)
    result = {'experiment': NAME, 'stage': 'strict-process', 'complete': complete,
        'status': 'PASS' if complete and failed == 0 else 'FAILED' if complete else 'INCOMPLETE',
        'failure': failure, 'attemptedGamesWithReports': len(rows), 'problemGames': failed,
        'timeouts': sum(r['timeouts'] for r in rows), 'nominalOverruns': sum(r['nominalOverruns'] for r in rows),
        'firstResponseMs': distribution(first), 'laterResponseMs': distribution(later),
        'laterAbove45': sum(t > 45 for t in later), 'laterAbove48': sum(t > 48 for t in later),
        'laterAbove50': sum(t > 50 for t in later),
        'missingResponses': sum(t['responseMs'] is None for r in rows for t in r['timings']),
        'reports': reports, 'games': rows,
        'scope': 'Once-only exact40 artifact, 16 local games, strict1000/50, no grace; all observed response times include failures.'}
    save(RUN / 'process-summary.json', result)
    print(json.dumps({k: v for k, v in result.items() if k not in ('games', 'reports')}, indent=2), flush=True)
    if result['status'] != 'PASS':
        save(RUN / 'strength-skipped.json', {'status': 'SKIPPED_PROCESS_' + result['status'],
            'reason': 'User explicitly requires process PASS before strength.', 'processSummary': pin(RUN / 'process-summary.json'),
            'newStrengthGames': 0, 'promotion': False})


def require_process_pass(frozen):
    path = RUN / 'process-summary.json'
    result = read(path)
    require(result['complete'] and result['status'] == 'PASS'
            and result['attemptedGamesWithReports'] == 16 and result['problemGames'] == 0
            and result['timeouts'] == result['nominalOverruns'] == 0, 'Process stage did not pass')
    require(not (RUN / 'strength-skipped.json').exists(), 'Strength already skipped')
    require(len(result['reports']) == 8, 'Incomplete process provenance')
    for saved, expected in zip(result['reports'], frozen['processMaps']):
        path = ROOT / saved['path']
        require(sha(path) == saved['sha256'], 'Process evidence changed')
        require(not any(r['failed'] for r in inspect_process(read(path), frozen, expected['path'])),
                'Retained raw process failure')


def inspect_strength(report, frozen, index):
    data = report['data']
    require(data['a'] == CANDIDATE and data['b'] == CONTROL and data['threads'] == 2, 'Wrong strength arms/workers')
    require(data['paramsA'] == frozen['candidate']['params'] and data['paramsB'] == frozen['control']['params'],
            'Strength configuration differs from exact tested artifact')
    require(tuple(data[k] for k in ('firstSearchBudgetMsA', 'firstSearchBudgetMsB', 'turnSearchBudgetMsA', 'turnSearchBudgetMsB'))
            == (486, 486, 40, 36), 'Wrong actual per-arm budgets')
    planned = frozen['strength']['blocks'][index]
    require([p['seed'] for p in data['pairs']] == planned['seeds']
            and [p['mapHash'] for p in data['pairs']] == planned['mapHashes'], 'Unregistered strength map/seed')


def strength_stage():
    frozen = load_freeze()
    require_process_pass(frozen)
    require(not (RUN / 'strength-summary.json').exists(), 'Preserve previous strength stage')
    require(not any((BENCH / f'{block}.json').exists() or (BENCH / f'{block}.log').exists()
                    for block, *_ in runner.BLOCKS), 'Strength was already attempted')
    reports = []
    for index, (block, games, pairs, offset) in enumerate(runner.BLOCKS):
        load_freeze()
        report = runner.run_block(NAME, CANDIDATE, CONTROL, block, games, pairs, BASE_SEED + offset)
        inspect_strength(report, frozen, index)
        load_freeze()
        reports.append(report)
        verdict = gate.assess(*reports)
        save(BENCH / f'{block}-decision.json', verdict)
        print(json.dumps({k: v for k, v in verdict.items() if k not in ('reports', 'boss')}, indent=2), flush=True)
        if verdict['directStage'].startswith('STOP_FUTILITY') or verdict['directStage'] in ('PASS_1000', 'NOT_PASSED'):
            save(RUN / 'strength-summary.json', {'experiment': NAME, 'complete': True,
                'status': verdict['directStage'], 'localOnly': True, 'promotion': False,
                'gate': verdict, 'processSummary': pin(RUN / 'process-summary.json'),
                'reports': [{'path': r['path'], 'sha256': r['sha256']} for r in reports],
                'scope': 'Budget effect on batched candidate40vs36, not selected-v09 promotion; no boss authorized or run.'})
            return
    raise RuntimeError('Fixed sequence ended without a gate verdict')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('stage', choices=('process', 'strength'))
    args = parser.parse_args()
    if args.stage == 'process':
        process_stage()
    else:
        strength_stage()


if __name__ == '__main__':
    main()
