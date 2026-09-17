"""Audit the completed, still-frozen experiment. Never run games or modify inputs.

Run only after complete.json AND the successful power-release record exist, before
artifact preparation or appending result documentation. The sole output is a NEW
docs/evidence/network-after-base-v1-audit.json; existing audits cannot be replaced.
"""
import hashlib
import itertools
import json
from datetime import datetime, timezone
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / 'tools'))
from practical_gate import assess, compact, read_report
import run_network_after_base_experiment as runner

NAME = 'network-after-base-v1'
FOLDER = ROOT / 'build/bench' / NAME
OUTPUT = ROOT / f'docs/evidence/{NAME}-audit.json'
DIAGNOSTIC = ROOT / f'docs/evidence/{NAME}-diagnostic.json'
EXPECTED_DIAGNOSTIC = '27753c7e7498f244f6635f67ba683ee36ed2a36f46054d9db011af1c2f20ac37'
LABELS = ('screen', 'direct', 'style-candidate', 'style-control',
          'full-candidate', 'full-control', 'ablation')
PARAMETER = 'USE_ROLLOUT_NETWORK_AFTER_BASE'
checks = []


def load(path):
    return json.loads(path.read_bytes())


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def check(name, passed, details=None):
    item = {'name': name, 'passed': bool(passed)}
    if details is not None:
        item['details'] = details
    checks.append(item)


def stamp(value):
    return datetime.fromisoformat(value)


def relative(path):
    return path.relative_to(ROOT).as_posix()


def stage_of(label):
    return label.split('-')[0]


def main():
    if OUTPUT.exists():
        raise RuntimeError('Preserve the existing audit; this script does not overwrite')
    if not (FOLDER / 'complete.json').exists() or not (FOLDER / 'power-request.json').exists():
        raise RuntimeError('All stages and the power-release finally block must finish first')
    manifest = load(FOLDER / 'launch.json')
    started = load(FOLDER / 'started.json')
    complete = load(FOLDER / 'complete.json')
    power = load(FOLDER / 'power-request.json')
    saved_decision = load(FOLDER / 'decision.json')
    if power.get('status') != 'released' or power.get('runOutcome') != 'completed':
        raise RuntimeError('Expected completed run with successfully released Windows request')
    check('correct experiment and fixed practical policy',
          manifest['name'] == NAME and manifest['policy'] == 'practical-300-1000-v2')
    check('no failed-run marker', not (FOLDER / 'failed.json').exists())
    check('7824 unique frozen historical maps',
          len(manifest['priorMapHashes']) == len(set(manifest['priorMapHashes'])) == 7824)

    reports = {}
    for label in LABELS:
        path = FOLDER / (label + '.json')
        if path.exists():
            games = 1000 if stage_of(label) in ('direct', 'full') else 300
            reports[label] = read_report(path, games)
    screen = reports['screen']
    ablation = reports['ablation']
    decision = assess(screen, reports.get('direct'), reports.get('style-candidate'),
                      reports.get('style-control'), reports.get('full-candidate'),
                      reports.get('full-control'))
    check('practical gate recomputed exactly', decision == saved_decision)

    # Independent integer-threshold branch calculation, separate from practical_gate.
    expected = {'screen', 'ablation'}
    direct_pass = screen['halfWins'] >= 360
    if 312 < screen['halfWins'] < 360:
        expected.add('direct')
        direct_pass = reports['direct']['halfWins'] >= 1100
    independent_status = 'NOT_PASSED'
    if direct_pass:
        expected.update(('style-candidate', 'style-control'))
        delta = reports['style-candidate']['halfWins'] - reports['style-control']['halfWins']
        if delta >= 18:
            independent_status = 'PASS'
        else:
            expected.update(('full-candidate', 'full-control'))
            full_delta = reports['full-candidate']['halfWins'] - reports['full-control']['halfWins']
            independent_status = 'PASS' if full_delta >= 0 else 'NOT_PASSED'
    check('only independently prescribed complete stages exist', set(reports) == expected,
          {'expected': sorted(expected), 'present': sorted(reports)})
    check('independent threshold verdict matches gate', decision['strengthStatus'] == independent_status)

    summaries, report_files = {}, []
    for label, report in reports.items():
        data = report['data']
        stage = stage_of(label)
        seed, pair_count, _ = runner.POOLS[stage]
        expected_a = 'v06' if label.endswith('-control') else manifest['candidate']
        expected_b = ('v05' if stage in ('style', 'full') else
                      manifest['ablationControl'] if stage == 'ablation' else 'v06')
        maps, seeds, indices, dimensions = [], [], [], []
        wins = losses = draws = 0
        side_counts = [0, 0]
        errors = []
        for index, pair in enumerate(data['pairs']):
            indices.append(pair['index'])
            maps.append(pair['mapHash'])
            seeds.append(pair['seed'])
            dimensions.append([pair[k] for k in ('width', 'height', 'townCount', 'pairCount')])
            for side, key in enumerate(('aAsP0', 'aAsP1')):
                game = pair[key]
                side_counts[side] += 1
                if game['winner'] == -1:
                    draws += 1
                elif game['winner'] == side:
                    wins += 1
                else:
                    losses += 1
                if game['error'] != [None, None]:
                    errors.append({'pair': index, 'side': side, 'error': game['error']})
        units = 2 * wins + draws
        check(label + ': independently recounted outcomes',
              (wins, losses, draws) == (data['winsA'], data['winsB'], data['draws'])
              and units == report['halfWins'])
        check(label + ': complete distinct paired maps and sequential indices',
              len(maps) == len(set(maps)) == pair_count
              and side_counts == [pair_count, pair_count]
              and indices == list(range(pair_count))
              and data['gamesPerMap'] == dict.fromkeys(maps, 2))
        check(label + ': exact predeclared seed interval', seeds == list(range(seed, seed + pair_count)))
        check(label + ': zero bot errors', not errors and data['errorsA'] == data['errorsB'] == 0)
        check(label + ': disjoint from frozen history', not set(maps).intersection(manifest['priorMapHashes']))
        check(label + ': exact bot specifications', data['a'] == expected_a and data['b'] == expected_b)
        check(label + ': all 98 parameters exact',
              len(data['paramsA']) == len(data['paramsB']) == 98
              and data['paramsA'] == manifest['resolvedParameters'][expected_a]
              and data['paramsB'] == manifest['resolvedParameters'][expected_b])
        check(label + ': fixed timing and Java conditions',
              data['timingProfile'] == 'CG' and data['firstSearchBudgetMs'] == 486
              and data['turnSearchBudgetMs'] == 36 and data['threads'] == 2
              and data['javaVersion'] == screen['data']['javaVersion'])
        pool = ROOT / f'bench/pools/{NAME}-{stage}.json'
        check(label + ': exact pool identity',
              data['poolId'] == NAME + '-' + stage
              and data['poolSha256'] == manifest['poolHashes'][stage] == sha(pool))
        exclusions_path = FOLDER / (stage + '-excluded.json')
        excluded = load(exclusions_path)['gamesPerMap']
        check(label + ': exclusions include complete frozen history', set(manifest['priorMapHashes']).issubset(excluded))
        check(label + ': all exclusions honored and report hash binds bytes',
              not set(maps).intersection(excluded)
              and sha(exclusions_path) in data['excludedReports'].values())
        summaries[label] = {'games': data['games'], 'wins': wins, 'losses': losses, 'draws': draws,
                            'halfWins': units, 'score': units / (2 * data['games']),
                            'distinctMaps': pair_count, 'seedStart': seed, 'seedEnd': seed + pair_count - 1,
                            'reportSha256': sha(FOLDER / (label + '.json')),
                            'exclusionsSha256': sha(exclusions_path),
                            'maxSearchMs': [data['maxMsA'], data['maxMsB']],
                            'slowMoves': [data['slowA'], data['slowB']]}
        report_files.extend((FOLDER / (label + '.json'), exclusions_path))

    for left, right in itertools.combinations(reports, 2):
        same_stage = stage_of(left) == stage_of(right) and stage_of(left) in ('style', 'full')
        a, b = reports[left], reports[right]
        if same_stage:
            check(left + '/' + right + ': exact matched map metadata',
                  a['maps'].keys() == b['maps'].keys()
                  and all(a['maps'][h]['seed'] == b['maps'][h]['seed']
                          and a['maps'][h]['dimensions'] == b['maps'][h]['dimensions'] for h in a['maps']))
        else:
            check(left + '/' + right + ': independent map sets', not set(a['maps']).intersection(b['maps']))
    seen = set()
    for stage in ('screen', 'direct', 'style', 'full', 'ablation'):
        stage_reports = [r for label, r in reports.items() if stage_of(label) == stage]
        if stage_reports:
            excluded = load(FOLDER / (stage + '-excluded.json'))['gamesPerMap']
            check(stage + ': explicitly excluded all earlier stage maps', seen.issubset(excluded))
            for report in stage_reports:
                seen.update(report['maps'])
    pa, pb = ablation['data']['paramsA'], ablation['data']['paramsB']
    changed = sorted(k for k in pa if pa[k] != pb.get(k))
    check('ablation differs in AFTER_BASE only', pa.keys() == pb.keys() and changed == [PARAMETER], changed)
    off = ('USE_ROLLOUT_NETWORK', 'USE_ROLLOUT_QUICK_EARLY', 'USE_ROLLOUT_QUICK_VALUE',
           'USE_ROLLOUT_QUICK_SAFE', 'USE_ROLLOUT_DIVERSE_REPLIES', 'USE_ROLLOUT_REPLAN',
           'USE_ROLLOUT_SECOND_TARGET', 'USE_ROLLOUT_SPREAD')
    check('candidate experimental flags and exact bounds',
          all(pa[k] == 'false' for k in off) and pa[PARAMETER] == 'true'
          and pa['USE_ROLLOUT_QUICK_ROUTE'] == 'true'
          and all(pa[k] == v for k, v in {'ROLLOUT_NETWORK_MAX_CELLS': '24',
                    'ROLLOUT_NETWORK_ROOTS': '2', 'ROLLOUT_NETWORK_RESERVE_MS': '12',
                    'NOISE': '0.1', 'SEED': '1'}.items()))

    code_now = runner.code_identity()
    infra_now = runner.infrastructure_identity()
    submissions_now = runner.submissions()
    check('complete source class and resource inventory unchanged', code_now == manifest['code'],
          {'fileCount': len(code_now), 'changedOrAdded': sorted(k for k in code_now if code_now[k] != manifest['code'].get(k)),
           'removed': sorted(set(manifest['code']) - set(code_now))})
    check('all frozen infrastructure exact', infra_now == manifest['infrastructure'], {'fileCount': len(infra_now)})
    check('all 25 submission files exact', len(submissions_now) == 25 and submissions_now == manifest['submissionFiles'])
    changed_prior = [k for k, value in manifest['priorReports'].items() if sha(ROOT / k) != value]
    check('all prior evidence and reserved pools exact', not changed_prior,
          {'fileCount': len(manifest['priorReports']), 'changed': changed_prior})
    check('started marker binds unchanged launch bytes', started['launchSha256'] == sha(FOLDER / 'launch.json'))
    check('preflight inputs match launch before configuration resolution',
          {k: v for k, v in manifest.items() if k != 'resolvedParameters'} == load(FOLDER / 'preflight-inputs.json'))
    check('protocol text matches immutable launch snapshot',
          manifest['protocol'] == (ROOT / f'docs/{NAME}.md').read_text(encoding='utf-8'))
    strength_path = ROOT / f'docs/evidence/{NAME}.json'
    ablation_path = ROOT / f'docs/evidence/{NAME}-ablation.json'
    durable, durable_ablation = load(strength_path), load(ablation_path)
    check('durable strength exactly mirrors launch and decision', durable == {'launch': manifest, 'decision': saved_decision})
    check('durable ablation exactly mirrors report and launch',
          durable_ablation['launch'] == manifest and durable_ablation['report'] == compact(ablation)
          and durable_ablation['purpose'] == manifest['ablationRole']
          and durable_ablation['submissionFilesUnchanged'] == submissions_now)
    check('completion marker agrees with complete results',
          complete['strengthStatus'] == saved_decision['strengthStatus']
          and complete['ablationScore'] == ablation['score']
          and complete['submissionFilesUnchanged'] == submissions_now)
    check('power acquisition and release succeeded',
          power['platform'] == 'nt' and power['requestFlags'] == '0x80000003'
          and power['releaseFlags'] == '0x80000000' and power['requestSucceeded']
          and power['releaseAttempted'] and power['requestReturnCode'] != 0
          and power['releaseReturnCode'] != 0 and power['status'] == 'released'
          and power['runOutcome'] == 'completed' and power['globalPowerSettingsChanged'] is False)
    check('power lifetime covers the complete run',
          stamp(power['startedAt']) <= stamp(power['requestAt']) <= stamp(started['startedAt'])
          <= stamp(complete['completedAt']) <= stamp(power['releaseAt']) <= stamp(power['endedAt']))

    diagnostic = load(DIAGNOSTIC)
    check('diagnostic exact pre-strength SHA', sha(DIAGNOSTIC) == EXPECTED_DIAGNOSTIC)
    bot_class = 'build/classes/java/main/btk/bot/current/RolloutBot.class'
    check('diagnostic measured the launched bot class', diagnostic['classSha256'] == manifest['code'][bot_class])
    check('diagnostic configs match launch except NOISE=0',
          diagnostic['params'] == dict(pa, NOISE='0.0')
          and diagnostic['baselineParams'] == dict(pb, NOISE='0.0'))
    samples = diagnostic['samples']
    sample_keys = [(s['gameId'], s['turn'], s['me'], s['stateSha256']) for s in samples]
    check('complete 133-state 14-replay diagnostic',
          diagnostic['sampleCount'] == len(samples) == len(set(sample_keys)) == 133
          and diagnostic['validatedReplays'] == len(diagnostic['games']) == 14
          and len(set(s['gameId'] for s in samples)) == 14
          and all(g['validated'] for g in diagnostic['games'])
          and diagnostic['networkMode'] == 'after_base')
    pairs = (('ordinarySelectedAction', 'action'), ('ordinarySelectedValue', 'selectedValue'),
             ('ordinarySelectedRoute', 'selectedRoute'), ('ordinarySelectedTarget', 'selectedTarget'),
             ('ordinarySelectedCount', 'selectedCount'), ('ordinarySelectedCells', 'selectedCells'),
             ('ordinaryComparisons', 'completedComparisons'), ('routeQueries', 'routeQueries'))
    parity_bad = [i for i, sample in enumerate(samples)
                  if not all(sample['candidate']['ample'][a] == sample['baseline']['ample'][b] for a, b in pairs)
                  or not sample['candidate']['ample']['ordinarySearchComplete']
                  or not sample['baseline']['ample']['ordinarySearchComplete']]
    check('independent full ample ordinary snapshot parity in all 133 states', not parity_bad, {'badIndices': parity_bad})
    source_input = ROOT / diagnostic['input']
    original = load(source_input)
    original_by_key = {(s['gameId'], s['turn'], s['me'], s['stateSha256']): s for s in original['samples']}
    check('diagnostic input hash and all original baseline ample actions',
          sha(source_input) == diagnostic['inputSha256']
          and set(sample_keys) == set(original_by_key)
          and all(s['baseline']['ample']['action'] == original_by_key[k]['baseline']['ample']['action']
                  for k, s in zip(sample_keys, samples)))
    nominal_comparisons = sum(s['candidate']['nominal']['networkComparisons'] for s in samples)
    nominal_selected = sum(s['candidate']['nominal']['selectedNetwork'] for s in samples)
    nominal_states = sum(s['candidate']['nominal']['networkComparisons'] > 0 for s in samples)
    check('independent nominal mechanism counts 69 comparisons 22 states 12 selections',
          nominal_comparisons == diagnostic['summary']['nominal']['networkComparisons'] == 69
          and nominal_states == diagnostic['summary']['nominal']['statesWithNetworkComparisons'] == 22
          and nominal_selected == diagnostic['summary']['nominal']['statesSelectingNetwork'] == 12)
    ample_ordinary = sum(s['candidate']['ample']['ordinaryComparisons'] for s in samples)
    check('ample ordinary comparison total remains 1187',
          ample_ordinary == diagnostic['summary']['ample']['candidateOrdinaryComparisons'] == 1187)

    paths = set(report_files)
    paths.update(FOLDER / name for name in ('launch.json', 'preflight-inputs.json', 'started.json',
                                          'decision.json', 'complete.json', 'power-request.json'))
    paths.update((strength_path, ablation_path, DIAGNOSTIC, Path(__file__).resolve()))
    hashes = {relative(p): sha(p) for p in sorted(paths)}
    audit = {'schemaVersion': 1, 'experiment': NAME, 'auditedAt': datetime.now(timezone.utc).isoformat(),
             'passed': all(c['passed'] for c in checks), 'checks': checks, 'results': summaries,
             'method': 'Independent pair-by-pair recount and integer threshold/stage calculation, practical-v2 recomputation, matched-map checks, exact frozen identities before artifact preparation/result edits.',
             'totalGames': sum(r['data']['games'] for r in reports.values()), 'totalDistinctMaps': len(seen),
             'frozenHistoricalMapCount': len(manifest['priorMapHashes']),
             'decision': {k: v for k, v in saved_decision.items() if k not in ('reports', 'matchedScreenDeltas', 'matchedFullDeltas')},
             'filesSha256': hashes, 'verifiedCodeFiles': len(code_now),
             'verifiedInfrastructureFiles': len(infra_now), 'verifiedSubmissionFiles': submissions_now,
             'powerRecord': power, 'completionRecord': complete, 'startedRecord': started,
             'diagnosticAudit': {'expectedSha256': EXPECTED_DIAGNOSTIC, 'botClassSha256': diagnostic['classSha256'],
                                 'states': len(samples), 'ampleOrdinaryMismatches': parity_bad,
                                 'nominalNetworkComparisons': nominal_comparisons,
                                 'nominalNetworkStates': nominal_states, 'nominalNetworkSelections': nominal_selected},
             'protocolSnapshot': {'path': f'docs/{NAME}.md',
                                  'preStrengthSha256': manifest['infrastructure'][f'docs/{NAME}.md'],
                                  'matchedAtAudit': manifest['protocol'] == (ROOT / f'docs/{NAME}.md').read_text(encoding='utf-8'),
                                  'archivedTextLocation': f'docs/evidence/{NAME}.json:launch.protocol',
                                  'note': 'Result documentation may be appended AFTER this audit; its expected change does not alter the immutable launch snapshot. Source/class/resource/pool/submission identities remain frozen.'},
             'timingInterpretation': 'In-process maxima and slow counts are retained diagnostics, including possible first moves; no strict process-readiness assertion. An exact artifact check is separate if strength passes.'}
    with OUTPUT.open('x', encoding='utf-8') as output:
        json.dump(audit, output, indent=2)
    print(json.dumps({'passed': audit['passed'], 'checks': len(checks),
                      'failures': [c for c in checks if not c['passed']], 'results': summaries,
                      'totalGames': audit['totalGames'], 'totalDistinctMaps': audit['totalDistinctMaps'],
                      'codeFiles': len(code_now), 'infrastructureFiles': len(infra_now),
                      'submissionFiles': len(submissions_now), 'auditPath': str(OUTPUT),
                      'auditSha256': sha(OUTPUT)}, indent=2))
    return 0 if audit['passed'] else 1


if __name__ == '__main__':
    raise SystemExit(main())
