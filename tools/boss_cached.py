"""Forty candidate games against an immutable, previously observed control.

This is a practical reusable panel, not fresh confirmation or a significance test.
Only an explicit run command creates games. Unknown plays are never repeated.
"""
from __future__ import annotations

import argparse
from collections import Counter
import json
import math
from pathlib import Path
import re
import time

import boss_batch as batch

ROOT = Path(__file__).resolve().parents[1]
POLICY = 'practical-300-1000-boss-cache-v3'
MODE = 'cached-control-v1'
CONTROL_EVIDENCE = Path('docs/evidence/chords-boss-v1.amended.json')
CONTROL_EVIDENCE_SHA = '68a76e3d0361037ac8b10f42dfd4acf671a355a08200bd221fcb235796abd118'
INITIAL_CONTROL_PLAN_ID = '3d951ec7e4ad1878e4e135d11b9f3a74b4611964074918c0d223cc06e90ee24b'


def require(condition, message):
    if not condition:
        raise ValueError(message)


def public_path(path):
    path = (ROOT / path).resolve()
    require(path.is_relative_to(ROOT.resolve()) and 'private' not in path.relative_to(ROOT.resolve()).parts,
            'Cached evidence must stay inside the workspace and outside private files')
    return path


def file_pin(path):
    path = public_path(path)
    return {'path': path.relative_to(ROOT.resolve()).as_posix(), 'sha256': batch.digest(path.read_bytes())}


def verify_pin(pin):
    path = public_path(pin['path'])
    require(batch.digest(path.read_bytes()) == pin['sha256'], 'Pinned cached evidence changed')
    return path


def failures(raw):
    """Explicit referee/runtime failure signals are retained, never inferred from stderr."""
    values = []
    for index, frame in enumerate(raw['frames']):
        if frame.get('summary'):
            require(isinstance(frame['summary'], str), 'Malformed referee summary')
            values.append({'frame': index, 'summarySha256': batch.digest(frame['summary'])})
        if frame.get('error'):
            values.append({'frame': index, 'explicitError': True})
    for agent in raw['agents']:
        if agent.get('valid') is False:
            values.append({'player': agent['index'], 'invalidAgent': True})
    return values


def boss_identity(outcome):
    boss = outcome['boss']
    require(isinstance(boss.get('arenaboss'), dict) and isinstance(boss['arenaboss'].get('nickname'), str),
            'Missing canonical boss/league identity')
    league = boss['arenaboss'].get('league')
    require(isinstance(league, dict) and all(type(league.get(key)) is int
            for key in ('divisionCount', 'divisionIndex', 'divisionOffset')),
            'Missing or unsupported canonical boss league divisions')
    return {'agentId': boss['agentId'], 'arenaBoss': boss['arenaboss']}


def control_snapshot(control_plan, control_arm):
    require(control_arm in ('candidate', 'control'), 'Select the candidate or control source arm')
    source_path = public_path(control_plan)
    directory, plan = batch.load_plan(source_path)
    require(control_arm in plan['builds'], 'Source plan does not contain the selected control arm')
    evidence_path, public_games = None, None
    if plan['planId'] == INITIAL_CONTROL_PLAN_ID:
        evidence_path = public_path(CONTROL_EVIDENCE)
        require(batch.digest(evidence_path.read_bytes()) == CONTROL_EVIDENCE_SHA,
                'The registered historical control evidence changed')
        evidence = batch.read_json(evidence_path)
        require(evidence.get('complete') is True and evidence.get('status') == 'AMENDED_EXPLORATORY_COMPLETE'
                and evidence.get('planId') == plan['planId']
                and evidence.get('planSha256') == batch.digest(source_path.read_bytes()),
                'Control plan is not the registered completed historical sample')
        public_games = {game['gameId']: game for game in evidence['games'] if game['arm'] == control_arm}
        require(len(public_games) == 40, 'Historical audit has no unique forty-game control')
    if plan.get('mode') == MODE:
        previous = assess_cached(source_path)
        require(previous['complete'] and previous['readinessPassed'], 'Source cached panel is incomplete or has technical issues')
    build = plan['builds'][control_arm]
    require(batch.artifact(build['directory'], build['processCheck']) == build,
            'Control must be its unchanged verified artifact')
    subset = dict(plan, slots=[slot for slot in plan['slots'] if slot['arm'] == control_arm])
    records = batch.result_records(directory, subset)
    batch.check_sample(subset, records)
    require(len(records) == 40 and len(subset['slots']) == 40, 'Control requires exactly forty retained games')
    historical = batch.retry_metadata(directory, plan)
    games, identities = [], set()
    for number, record in enumerate(records, 1):
        old_slot, value = record['slot'], record['outcome']
        folder = directory/f"{old_slot['slot']:04d}"
        attempt = record.get('requestAttempt', 1)
        request_folder = batch.attempt_folder(folder, attempt)
        raw_path = public_path(record['capture']['path'])
        raw = batch.read_json(raw_path)
        require(record['capture']['expectedBuildMatches'] is True, 'Control attribution does not match its artifact')
        require((request_folder/'canonical.json').read_bytes() == raw_path.read_bytes(),
                'Retained control canonical differs from its archived capture')
        published = public_games.get(value['gameId']) if public_games is not None else None
        if public_games is not None:
            require(published is not None and all(published.get(key) == value[key]
                    for key in ('gameId', 'seat', 'seed', 'result', 'scores', 'ranks', 'mapHash'))
                    and published['rawSha256'] == record['capture']['rawSha256']
                    and published['buildId'] == build['buildId'], 'Control outcome differs from its published audit')
        technical = failures(raw)
        require(not technical and not (published and published.get('terminalFailures')), 'Cached control contains a technical failure')
        identity = boss_identity(value)
        identities.add(batch.encoded(identity))
        paths = [folder/'result.json', request_folder/'intent.json', request_folder/'canonical.json', raw_path]
        paths += [request_folder/name for name in ('play-response.json', 'recovered-game.json')
                  if (request_folder/name).exists()]
        if attempt == 2:
            paths += [folder/name for name in ('intent.json', 'api-error.json', 'retry-authorization.json', 'retry-amendment.txt')]
        games.append({'slot': number, 'sourceSlot': old_slot['slot'], 'gameId': value['gameId'],
                      'seed': value['seed'], 'seat': value['seat'], 'mapHash': value['mapHash'],
                      'result': value['result'], 'rawSha256': record['capture']['rawSha256'],
                      'completedAt': record.get('completedAt'),
                      'requestAttempt': attempt, 'files': [file_pin(path) for path in paths]})
    require(len({game['mapHash'] for game in games}) == 40 and len({game['gameId'] for game in games}) == 40
            and Counter(game['seat'] for game in games) == {0: 20, 1: 20},
            'Control must contain forty unique maps/game IDs and twenty games per seat')
    require(len(identities) == 1, 'Cached boss/league identity changed')
    return {'sourcePlan': file_pin(source_path), 'sourcePlanId': plan['planId'], 'arm': control_arm,
            'evidence': file_pin(evidence_path) if evidence_path is not None else None,
            'build': build, 'manifest': file_pin(Path(build['directory'])/'manifest.json'),
            'boss': json.loads(next(iter(identities))), 'games': games,
            'historicalProtocolStatus': historical['protocolStatus'], 'historicalAmendments': historical['protocolAmendments'],
            'historicalExcludedAttempts': historical['excludedAttempts']}


def make_plan(args):
    cache = control_snapshot(args.control_plan, args.control_arm)
    candidate = batch.artifact(args.candidate, args.candidate_check)
    require(candidate['buildId'] != cache['build']['buildId'], 'Candidate and cached control are the same build')
    session = batch.load_session(args.session)
    multi = session.play_template[1]['multi']
    options = multi.get('gameOptions')
    require(not options or re.fullmatch(r'\s*seed\s*=\s*-?\d+\s*', options), 'Custom game options require review')
    request_boss = next(agent for agent in multi['agentsIds'] if agent != -1)
    source_plan = batch.read_json(public_path(cache['sourcePlan']['path']))
    require(request_boss == source_plan['requestBossAgentId'], 'Captured boss selector differs from cached control')
    out = public_path(args.out)
    require(out.is_relative_to((ROOT/'build/cg-boss').resolve()) and not out.exists(),
            'Choose a new batch directory under build/cg-boss')
    cached_maps = {game['mapHash'] for game in cache['games']}
    plan = {'schemaVersion': 1, 'mode': MODE, 'policy': POLICY, 'createdAt': batch.now(),
            'design': 'independent', 'purpose': 'boss-cache', 'gamesPerBuild': 40,
            'builds': {'candidate': candidate}, 'candidateManifest': file_pin(Path(candidate['directory'])/'manifest.json'),
            'sessionBindingSha256': batch.session_binding(session), 'bossAgentId': cache['boss']['agentId'],
            'requestBossAgentId': request_boss, 'mapHashScheme': batch.MAP_SCHEME,
            'excludedMapHashes': sorted(set(batch.known_maps())-cached_maps), 'cache': cache,
            'slots': [{'slot': game['slot'], 'pair': game['slot'], 'arm': 'candidate',
                       'seed': game['seed'], 'seat': game['seat']} for game in cache['games']],
            'outcome': 'win=1, draw=0.5, loss=0; pass when candidate total >= cached control total',
            'promotion': False, 'inference': 'practical reused panel; not fresh confirmation or a significance test'}
    plan['planId'] = batch.digest(batch.encoded(plan))
    batch.write_once(out/'plan.json', plan)
    print('Cached-control plan saved: forty candidate requests; zero control requests; no games started.')
    return plan


def load_cached(plan_path):
    directory, plan = batch.load_plan(public_path(plan_path))
    require(plan.get('mode') == MODE and plan.get('policy') == POLICY and plan.get('gamesPerBuild') == 40
            and plan.get('mapHashScheme') == batch.MAP_SCHEME and set(plan['builds']) == {'candidate'},
            'Unsupported cached-control plan')
    cache = plan['cache']
    verify_pin(cache['sourcePlan']); verify_pin(cache['manifest'])
    if cache['evidence'] is not None:
        verify_pin(cache['evidence'])
    for game in cache['games']:
        for pin in game['files']:
            verify_pin(pin)
    require(control_snapshot(public_path(cache['sourcePlan']['path']), cache['arm']) == cache,
            'Cached control provenance changed')
    candidate = plan['builds']['candidate']
    verify_pin(plan['candidateManifest'])
    require(batch.artifact(candidate['directory'], candidate['processCheck']) == candidate,
            'Candidate artifact/process check changed')
    expected_slots = [{'slot': game['slot'], 'pair': game['slot'], 'arm': 'candidate',
                       'seed': game['seed'], 'seat': game['seat']} for game in cache['games']]
    require(plan['slots'] == expected_slots and plan['bossAgentId'] == cache['boss']['agentId']
            and plan['design'] == 'independent' and plan['promotion'] is False,
            'Candidate schedule differs from the pinned control')
    require(not any(directory.rglob('retry-authorization.json')) and not any(directory.rglob('retry/intent.json')),
            'Cached-control mode does not authorize replacement play attempts')
    return directory, plan


def arm_summary(build, outcomes):
    manifest = batch.read_json(Path(build['directory'])/'manifest.json')
    counts = Counter(outcomes)
    return {'spec': build['strategy'], 'params': manifest['params'], 'buildId': build['buildId'],
            'sourceSha256': build['sourceSha256'], 'games': len(outcomes), 'wins': counts['W'],
            'losses': counts['L'], 'draws': counts['D'], 'halfWins': 2*counts['W']+counts['D']}


def collection_dates(values):
    dates = [value for value in values if isinstance(value, str)]
    return {'firstCompletedAt': min(dates) if dates else None, 'lastCompletedAt': max(dates) if dates else None,
            'recordedDates': len(dates)}


def assess_cached(plan_path):
    directory, plan = load_cached(plan_path)
    records = batch.result_records(directory, plan)
    issues, pairs = [], []
    try:
        batch.check_sample(plan, records)
    except ValueError as error:
        issues.append(str(error))
    old = plan['cache']['games']
    for record in records:
        slot, value = record['slot'], record['outcome']
        reference = old[slot['slot']-1]
        if any(value[key] != reference[key] for key in ('mapHash', 'seed', 'seat')):
            issues.append(f"Slot {slot['slot']}: candidate map/seed/seat differs from the cached control")
        if boss_identity(value) != plan['cache']['boss']:
            issues.append(f"Slot {slot['slot']}: boss/league identity changed")
        if value['gameId'] in {game['gameId'] for game in old}:
            issues.append(f"Slot {slot['slot']}: a cached game was reused as a candidate result")
        if not record['capture']['expectedBuildMatches']:
            issues.append(f"Slot {slot['slot']}: candidate build attribution failed")
        raw_path = public_path(record['capture']['path'])
        require((directory/f"{slot['slot']:04d}"/'canonical.json').read_bytes() == raw_path.read_bytes(),
                'Candidate canonical differs from its archived capture')
        if failures(batch.read_json(raw_path)):
            issues.append(f"Slot {slot['slot']}: recorded technical game failure")
        pairs.append({'mapHash': reference['mapHash'], 'seed': reference['seed'], 'seat': reference['seat'],
                      'candidateGameId': value['gameId'], 'controlGameId': reference['gameId']})
    candidate = arm_summary(plan['builds']['candidate'], [record['outcome']['result'] for record in records])
    control = arm_summary(plan['cache']['build'], [game['result'] for game in old])
    complete = len(records) == 40
    stage = ('PASS' if not issues and candidate['halfWins'] >= control['halfWins'] else 'NOT_PASSED') if complete else 'INCOMPLETE'
    return {'policy': POLICY, 'complete': complete, 'bossStage': stage, 'candidate': candidate, 'control': control,
            'pairs': pairs, 'planId': plan['planId'], 'issues': issues, 'mapHashScheme': batch.MAP_SCHEME,
            'readinessPassed': complete and not issues, 'promotion': False,
            'controlEvidenceSha256': plan['cache']['evidence']['sha256'] if plan['cache']['evidence'] else None,
            'collectionDates': {'control': collection_dates([game['completedAt'] for game in old]),
                                'candidate': collection_dates([record.get('completedAt') for record in records])},
            'historicalProtocolStatus': plan['cache']['historicalProtocolStatus'],
            'historicalAmendments': plan['cache']['historicalAmendments'],
            'historicalExcludedAttempts': plan['cache']['historicalExcludedAttempts'],
            'interpretation': 'Practical score filter on reused known maps; no claim of statistical improvement.',
            'limitations': ['Unchanged public boss/league IDs do not prove unchanged hidden boss implementation or execution conditions.',
                           'Known map/control outcomes can influence development; this panel is not fresh confirmation.']}


def run(args):
    directory, plan = load_cached(args.plan)
    session = batch.load_session(args.session)
    require(batch.session_binding(session) == plan['sessionBindingSha256'], 'Session differs from the planned configuration')
    with batch.run_lock(directory):
        assessment = assess_cached(args.plan)
        require(not assessment['issues'], 'Recorded candidate problem blocks resume; outcomes remain retained')
        build = plan['builds']['candidate']
        for slot in plan['slots']:
            folder = directory/f"{slot['slot']:04d}"
            if (folder/'result.json').exists():
                continue
            folder.mkdir(exist_ok=True)
            intent, received = folder/'intent.json', folder/'play-response.json'
            if intent.exists():
                batch.validate_intent(folder, plan, slot)
            else:
                require(not any(folder.iterdir()), 'Slot evidence without an intent forbids another play')
                request = batch.play_arguments(session, slot, build, plan['requestBossAgentId'])
                batch.write_once(intent, {'planId': plan['planId'], 'slot': slot, 'startedAt': batch.now(),
                                         'sourceSha256': build['sourceSha256'], 'requestAttempt': 1})
                try:
                    response = batch.call_service_text(session, 'TestSession/play', request, timeout=args.timeout)
                except batch.ApiError as error:
                    if error.response_bytes:
                        private = ROOT/'build/cg-boss/private/api-errors'/plan['planId']/f"{slot['slot']:04d}.bin"
                        private.parent.mkdir(parents=True, exist_ok=True)
                        with private.open('xb') as stream:
                            stream.write(error.response_bytes)
                    batch.write_once(folder/'api-error.json', {'status': error.status, 'uncertain': error.uncertain,
                                     'apiCode': error.error_code, 'retryAfterSeconds': error.retry_after_seconds,
                                     'at': batch.now(), 'requestAttempt': 1})
                    raise ValueError('Play outcome unknown or rejected; intent retained; no automatic replay') from None
                with received.open('xb') as stream:
                    stream.write(response.encode('utf-8'))
            game_id = batch.bound_game_id(folder)
            canonical = folder/'canonical.json'
            if not canonical.exists():
                response = batch.call_service_text(session, 'gameResult/findByGameId', [game_id, None], timeout=30)
                try:
                    raw = json.loads(response)
                    require(batch.replay_identity(raw)[0] == game_id, 'Canonical game identity differs')
                    batch.completed_replay(raw)
                except (ValueError, TypeError, KeyError):
                    (folder/f'canonical-rejected-{time.time_ns()}.txt').write_text(response, encoding='utf-8')
                    raise ValueError('Canonical lookup not ready/valid; resume retries only lookup') from None
                with canonical.open('xb') as stream:
                    stream.write(response.encode('utf-8'))
            text = canonical.read_bytes().decode('utf-8')
            capture = batch.save_capture({'kind': 'boss', 'content': text, 'expectedBuildId': build['buildId']}, ROOT)
            value = batch.outcome(json.loads(text), game_id, slot, plan['bossAgentId'])
            batch.write_once(folder/'result.json', {'planId': plan['planId'], 'slot': slot, 'completedAt': batch.now(),
                                                  'capture': capture, 'outcome': value, 'requestAttempt': 1})
            assessment = assess_cached(args.plan)
            require(not assessment['issues'], 'Candidate problem retained; batch stopped without replacement')
            print(f"Saved candidate {slot['slot']}/40: game {game_id}", flush=True)
            if args.delay and slot != plan['slots'][-1]:
                time.sleep(args.delay)
    return report(args)


def report(args):
    result = assess_cached(args.plan)
    print(batch.encoded(result), end='')
    return result


def recover(args):
    load_cached(args.plan)
    batch.recover(args)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    create = sub.add_parser('plan', help='Pin the existing control and forty candidate requests; no HTTP')
    create.add_argument('--control-plan', type=Path, required=True)
    create.add_argument('--control-arm', default='control', choices=['candidate', 'control'])
    create.add_argument('--candidate', type=Path, required=True)
    create.add_argument('--candidate-check', type=Path, required=True)
    create.add_argument('--session', type=Path, default=batch.DEFAULT_SESSION_PATH)
    create.add_argument('--out', type=Path, required=True)
    create.set_defaults(function=make_plan)
    execute = sub.add_parser('run', help='Run/resume only the forty candidate games')
    execute.add_argument('--plan', type=Path, required=True)
    execute.add_argument('--session', type=Path, default=batch.DEFAULT_SESSION_PATH)
    execute.add_argument('--timeout', type=float, default=120)
    execute.add_argument('--delay', type=float, default=20)
    execute.set_defaults(function=run)
    for name, function in [('report', report), ('recover', recover)]:
        command = sub.add_parser(name)
        command.add_argument('--plan', type=Path, required=True)
        if name == 'recover':
            command.add_argument('--slot', type=int, required=True)
            command.add_argument('--game-id', type=int, required=True)
        command.set_defaults(function=function)
    args = parser.parse_args()
    if args.command == 'run' and (not math.isfinite(args.timeout) or not 1 <= args.timeout <= 600
                                 or not math.isfinite(args.delay) or not 0 <= args.delay <= 60):
        parser.error('Timeout must be 1..600 seconds and delay 0..60 seconds')
    try:
        args.function(args)
    except (ValueError, OSError, KeyError, TypeError, IndexError, batch.ApiError) as error:
        message = str(error) if isinstance(error, ValueError) else 'Unsupported cached evidence; records retained'
        parser.exit(1, message+'\n')


if __name__ == '__main__':
    main()
