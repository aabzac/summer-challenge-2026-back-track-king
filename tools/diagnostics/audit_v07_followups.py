"""Reassess both fixed v07 experiments and verify their recorded identities; no games."""
import hashlib
import json
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'tools'))
from practical_gate import POLICY, assess, read_report, require

EXPERIMENTS = [('route-dependencies-v1', 'USE_ROLLOUT_ROUTE_DEPENDENCIES'),
               ('network-chords-v1', 'USE_ROLLOUT_NETWORK_CHORDS')]
STAGES = [('screen', 'screen', 300, 'candidate', 'control'),
          ('direct', 'direct', 1000, 'candidate', 'control'),
          ('style-candidate', 'style', 300, 'candidate', 'commonOpponent'),
          ('style-control', 'style', 300, 'control', 'commonOpponent'),
          ('full-candidate', 'full', 1000, 'candidate', 'commonOpponent'),
          ('full-control', 'full', 1000, 'control', 'commonOpponent')]
PROBE_PATH = 'docs/evidence/v07-candidates-public-state-probe.json'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load(path):
    return json.loads(path.read_bytes())


def inventory(*folders):
    return {p.relative_to(ROOT).as_posix(): sha(p)
            for folder in folders for p in sorted((ROOT / folder).rglob('*')) if p.is_file()}


def check_launch(name, flag, launch):
    require(launch['name'] == name and launch['policy'] == POLICY, 'Wrong experiment or policy')
    require(launch['candidate'] == 'current?' + flag + '=1'
            and launch['control'] == 'v07' and launch['commonOpponent'] == 'v05', 'Wrong experiment bots')
    require(launch['threads'] == 2 and launch['timing'] == 'CG', 'Wrong declared conditions')
    require(set(launch['pools']) == set(launch['poolHashes']) == {'screen', 'direct', 'style', 'full'},
            'Missing or unexpected declared pools')
    params = launch['resolvedParameters']
    require(set(params) == {launch['candidate'], 'v07', 'v05'}, 'Missing or unexpected resolved bots')
    require(set(params[launch['candidate']]) == set(params['v07']), 'Parameter schema differs')
    require({k for k in params[launch['candidate']]
             if params[launch['candidate']][k] != params['v07'][k]} == {flag}, 'Mixed experiment flags')
    require(params[launch['candidate']][flag] == 'true' and params['v07'][flag] == 'false',
            'Candidate flag is not enabled against disabled control')
    for _, other_flag in EXPERIMENTS:
        if other_flag != flag:
            require(params[launch['candidate']][other_flag] == params['v07'][other_flag] == 'false',
                    'Other experiment is active')
    require(PROBE_PATH in launch['priorReports'], 'Public-state probe was not frozen before play')


def check_report(report, stage, pool_stage, launch, side_a, side_b):
    data = report['data']
    a, b = launch[side_a], launch[side_b]
    require(data['a'] == a and data['b'] == b, f'{stage}: wrong bots')
    require(data['paramsA'] == launch['resolvedParameters'][a]
            and data['paramsB'] == launch['resolvedParameters'][b], f'{stage}: changed effective parameters')
    require(data['threads'] == launch['threads'] == 2, f'{stage}: wrong worker count')
    require(data['timingProfile'] == launch['timing'] == 'CG', f'{stage}: wrong timing profile')
    pool_path = ROOT / f"bench/pools/{launch['name']}-{pool_stage}.json"
    pool = load(pool_path)
    declared = launch['pools'][pool_stage]
    require(pool == {'schemaVersion': 1, 'id': launch['name'] + '-' + pool_stage,
                     'generator': 'cg-sha1prng', **declared}, f'{stage}: wrong pool definition')
    require(data['poolId'] == pool['id'] and data['poolSha256'] == launch['poolHashes'][pool_stage]
            == sha(pool_path), f'{stage}: pool identity mismatch')
    seed, count = declared['seedStart'], declared['pairs']
    require(data['games'] == count * 2, f'{stage}: wrong declared stage size')
    require([p['seed'] for p in data['pairs']] == list(range(seed, seed + count)),
            f'{stage}: incomplete or wrong predeclared seed interval')


def check_probe(probe):
    sample = load(ROOT / 'docs/evidence/boss-v07-summary.json')
    expected = {(g['gameId'], t, g['me']) for g in sample['games']
                for t in (0, 5, 10, 20, 30, 40, 60, 80) if t < g['turns']}
    special = {(904554568, 26, 1), (904555424, 28, 1)}
    expected.update(special)
    states = probe['states']
    actual = [(s['gameId'], s['turn'], s['player']) for s in states]
    require(len(states) == probe['stateCount'] == len(expected) == 146
            and len(set(actual)) == len(actual) and set(actual) == expected, 'Public-state sample changed')
    specs = ['v07?NOISE=0', 'current?NOISE=0',
             'current?NOISE=0,USE_ROLLOUT_ROUTE_DEPENDENCIES=1',
             'current?NOISE=0,USE_ROLLOUT_NETWORK_CHORDS=1']
    dependency_changed = chord_changed = chord_selected = 0
    for state, key in zip(states, actual):
        require(bool(state['exactBossChordCase']) == (key in special), 'Wrong public-state player role')
        require(re.fullmatch(r'[0-9a-f]{64}', state['stateSha256']) is not None, 'Missing state identity')
        choices = state['choices']
        require([c['spec'] for c in choices] == specs, 'Wrong public-state policy comparison')
        require(choices[0]['action'] == choices[1]['action'], 'Disabled policy action mismatch')
        dependency_changed += choices[1]['action'] != choices[2]['action']
        chord_changed += choices[1]['action'] != choices[3]['action']
        chord_selected += choices[3]['telemetry']['selectedChord'] is True
    require(probe['disabledParity'] == len(states), 'Incorrect saved parity total')
    require(probe['dependencyActionChanges'] == dependency_changed
            and probe['chordActionChanges'] == chord_changed
            and probe['chordSelectedStates'] == chord_selected, 'Incorrect saved mechanism totals')


def main():
    output = ROOT / 'docs/evidence/v07-followups-final-audit.json'
    require(not output.exists(), 'Preserve existing final audit')
    summary = []
    all_maps = set()
    original_submission = None
    for name, flag in EXPERIMENTS:
        folder = ROOT / 'build/bench' / name
        launch, complete = load(folder / 'launch.json'), load(folder / 'complete.json')
        check_launch(name, flag, launch)
        require(not (folder / 'failed.json').exists(), 'Failed experiment cannot have a successful final audit')
        require(load(folder / 'started.json')['launchSha256'] == sha(folder / 'launch.json'),
                'Launch manifest changed after run started')
        require(inventory('src/main/java', 'src/main/resources', 'build/classes/java/main',
                          'build/resources/main') == launch['code'], 'Source/class/resource file set or identity changed')
        require(inventory('build/submit') == launch['submissionFiles'], 'Submission file set or identity changed')
        for group in ('code', 'infrastructure', 'priorReports', 'submissionFiles'):
            for relative, expected in launch[group].items():
                require(sha(ROOT / relative) == expected, f'Changed {group} identity: {relative}')
        require(complete['submissionFilesUnchanged'] == launch['submissionFiles'], 'Submission changed')
        if original_submission is None:
            original_submission = launch['submissionFiles']
        require(original_submission == launch['submissionFiles'], 'Original artifacts differ between experiments')
        reports = []
        for stage, pool_stage, games, side_a, side_b in STAGES:
            path = folder / (stage + '.json')
            report = read_report(path, games) if path.exists() else None
            if report:
                check_report(report, stage, pool_stage, launch, side_a, side_b)
            reports.append(report)
        require(reports[0] is not None, 'Missing mandatory screen report')
        decision = assess(*reports)
        require(decision['strengthStatus'] in ('PASS', 'NOT_PASSED'), 'Strength protocol is still incomplete')
        require(decision == load(folder / 'decision.json'), 'Saved gate decision differs from full reports')
        require(decision['strengthStatus'] == complete['strengthStatus'], 'Incomplete gate')
        durable = load(ROOT / f'docs/evidence/{name}.json')
        require(durable['launch'] == launch and durable['decision'] == decision,
                'Durable evidence does not match launch and reassessed complete reports')
        experiment_maps = set().union(*(set(r['maps']) for r in reports if r))
        require(not experiment_maps.intersection(all_maps), 'Independent experiments reused maps')
        require(not experiment_maps.intersection(launch['priorMapHashes']), 'Historical maps reused')
        all_maps.update(experiment_maps)
        power = load(folder / 'power-request.json')
        require(power['status'] == 'released' and power['runOutcome'] == 'completed'
                and power['requestSucceeded'] and power['releaseAttempted']
                and power['releaseReturnCode'] != 0 and not power['globalPowerSettingsChanged'],
                'Power request was not successfully acquired and released')
        summary.append({'experiment': name, 'candidate': launch['candidate'],
                        'decision': decision, 'distinctMaps': len(experiment_maps),
                        'games': sum(r['data']['games'] for r in reports if r),
                        'launchSha256': sha(folder / 'launch.json'), 'sourceClassConfigAndArtifactIdentity': 'PASS',
                        'powerRequestReleased': True})
    probe = load(ROOT / PROBE_PATH)
    check_probe(probe)
    defaults = (ROOT / 'src/main/java/btk/bot/Params.java').read_text(encoding='utf-8')
    for _, flag in EXPERIMENTS:
        require(re.search(r'public\s+boolean\s+' + flag + r'\s*=\s*false\s*;', defaults) is not None,
                f'Unexpected enabled default: {flag}')
    result = {'schemaVersion': 1, 'auditSourceSha256': sha(Path(__file__)),
              'experiments': summary, 'totalDistinctMaps': len(all_maps),
              'totalGames': sum(s['games'] for s in summary),
              'publicStates': probe['stateCount'], 'disabledParity': probe['disabledParity'],
              'dependencyActionChanges': probe['dependencyActionChanges'], 'chordActionChanges': probe['chordActionChanges'],
              'originalSubmissionFilesPreserved': original_submission,
              'processReadiness': 'See separate exact-artifact reports if a strength gate passed; none inferred here',
              'defaultFlags': 'Both new experiments OFF; selected v07 unchanged',
              'promotionOrUpload': False}
    with output.open('x', encoding='utf-8') as file:
        json.dump(result, file, indent=2)
    print(json.dumps({'games': result['totalGames'], 'maps': len(all_maps),
                      'verdicts': {s['experiment']: s['decision']['strengthStatus'] for s in summary},
                      'checks': 'PASS'}, indent=2))


if __name__ == '__main__':
    main()
