"""Fixed, resumable CodinGame boss batches using an imported local API session.

Only TestSession/play creates games. Its intent is persisted BEFORE sending, and
an unknown result is never retried. Canonical lookup can safely resume by game ID.
No arena submissions. Playing saves the supplied code in the website's IDE.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import copy
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import random
import re
import sys
import time

from boss_api import ApiError, DEFAULT_SESSION_PATH, call_service_text, load_session
from replay_capture import identify_build, replay_identity, save_capture, validate_local_build
from diagnostics.boss_evaluation_protocol import binomial_upper, clopper_pearson, fisher_p

ROOT = Path(__file__).resolve().parents[1]
MAP_SCHEME = 'cg-global-graphics-utf8-sha256-v1'


def digest(value):
    return hashlib.sha256(value if isinstance(value, bytes) else value.encode('utf-8')).hexdigest()


def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + '\n'


def read_json(path):
    return json.loads(Path(path).read_bytes())


def write_once(path, value):
    payload = encoded(value).encode('utf-8')
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        if path.read_bytes() != payload:
            raise ValueError(f'Existing record differs: {path.name}')
        return
    with path.open('xb') as stream:
        stream.write(payload)
        stream.flush()
        import os
        os.fsync(stream.fileno())


def now():
    return datetime.now(timezone.utc).isoformat()


@contextmanager
def run_lock(directory):
    """OS lock releases on crash; an abandoned intent still forbids another play."""
    with (directory / '.run.lock').open('a+b') as stream:
        if stream.tell() == 0:
            stream.write(b'0')
            stream.flush()
        stream.seek(0)
        if sys.platform == 'win32':
            import msvcrt
            msvcrt.locking(stream.fileno(), msvcrt.LK_NBLCK, 1)
        else:
            import fcntl
            fcntl.flock(stream.fileno(), fcntl.LOCK_EX | fcntl.LOCK_NB)
        try:
            yield
        finally:
            stream.seek(0)
            if sys.platform == 'win32':
                msvcrt.locking(stream.fileno(), msvcrt.LK_UNLCK, 1)
            else:
                fcntl.flock(stream.fileno(), fcntl.LOCK_UN)


def classes_hash(directory):
    root = directory / 'classes'
    files = sorted(root.rglob('*.class'))
    if not files or not (root / 'Player.class').is_file():
        raise ValueError('Submission classes are missing; run submit and procCheck')
    value = hashlib.sha256()
    for file in files:
        value.update(file.relative_to(root).as_posix().encode('utf-8'))
        value.update(b'\0')
        value.update(file.read_bytes())
    return value.hexdigest()


def artifact(directory, process_check):
    directory, process_check = Path(directory).resolve(), Path(process_check).resolve()
    exported = read_json(directory / 'manifest.json')
    manifest = validate_local_build(ROOT, exported['buildId'])
    source = (directory / 'Player.java').read_bytes()
    if (digest(source) != manifest['sha256'] or exported['sha256'] != manifest['sha256']
            or any(exported.get(key) != manifest.get(key) for key in
                   ('schemaVersion', 'strategyId', 'configSha256', 'params', 'marker', 'identityScheme'))
            or not isinstance(exported.get('strategy'), str)
            or exported['strategy'].split('?', 1)[0] != manifest['strategyId']):
        raise ValueError('Exported source differs from registered build')
    executable = classes_hash(directory)
    check = read_json(process_check)
    games = check.get('games')
    if (check.get('schemaVersion') != 1
            or check.get('sourceSha256') != manifest['sha256'] or check.get('classesSha256') != executable
            or check.get('strict') is not True or check.get('graceMs') != 0
            or type(check.get('graceMs')) is not int or type(check.get('problems')) is not int
            or check.get('problems') != 0 or not isinstance(games, list) or not games
            or not all(successful_process_game(game) for game in games)):
        raise ValueError('An exact-source/executable strict successful procCheck is required')
    return {'directory': str(directory), 'buildId': manifest['buildId'],
            'strategy': exported['strategy'], 'sourceSha256': manifest['sha256'],
            'classesSha256': executable, 'configSha256': manifest['configSha256'],
            'processCheck': str(process_check), 'processCheckSha256': digest(process_check.read_bytes())}


def successful_process_game(game):
    if not isinstance(game, dict):
        return False
    result, timings = game.get('result'), game.get('timings')
    if not isinstance(result, dict) or not isinstance(timings, list) or not timings:
        return False
    return (type(game.get('timeouts')) is int and game['timeouts'] == 0
            and type(game.get('nominalOverruns')) is int and game['nominalOverruns'] == 0
            and type(game.get('player')) is int and game['player'] in (0, 1)
            and result.get('error') == [None, None]
            and type(result.get('turns')) is int and result['turns'] > 0
            and type(result.get('winner')) is int and result['winner'] in (-1, 0, 1)
            and isinstance(result.get('score'), list) and len(result['score']) == 2
            and all(type(score) is int for score in result['score'])
            and all(isinstance(timing, dict) and timing.get('status') == 'OK'
                    and type(timing.get('turn')) is int and timing['turn'] >= 0
                    and timing.get('graceMs') == 0
                    and type(timing.get('responseMs')) in (int, float)
                    and math.isfinite(timing['responseMs']) for timing in timings))


def view_json(frame):
    if not isinstance(frame, dict):
        raise ValueError('Canonical replay contains a malformed frame')
    view = frame.get('view', '')
    if not isinstance(view, str) or '\n' not in view:
        return {}
    body = view.partition('\n')[2].strip()
    result = json.loads(body) if body else {}
    if not isinstance(result, dict):
        raise ValueError('Canonical replay contains a malformed view')
    return result


def map_hash(raw):
    global_data = view_json(raw['frames'][0]).get('global')
    graphics = global_data.get('graphics') if isinstance(global_data, dict) else None
    if not isinstance(graphics, str) or not graphics:
        raise ValueError('Canonical replay has no initial map')
    return digest(graphics)


def known_maps():
    hashes = set()
    root = ROOT / 'replays/cg'
    paths = list(root.glob('*.json')) + list((root / 'boss').rglob('*.json'))
    for path in paths:
        if any(part in ('raw', '_metadata') for part in path.relative_to(root).parts):
            continue
        try:
            hashes.add(map_hash(read_json(path)))
        except (ValueError, KeyError, TypeError, IndexError):
            continue
    return sorted(hashes)


def session_binding(session):
    # Hash even the handle; no session material is copied to a public plan.
    return digest(encoded(session.play_template))


def make_schedule(n, design, seed, comparison=True):
    if not 1 <= n <= 3000:
        raise ValueError('Games per build must be between 1 and 3000')
    rng = random.Random(seed)
    seeds = iter(rng.sample(range(1, 2**31 - 1), n * (2 if comparison else 1)))
    seats = [i % 2 for i in range(n)]
    rng.shuffle(seats)
    orders = [i % 2 for i in range(n)]
    rng.shuffle(orders)
    slots = []
    for pair in range(n):
        shared_seed = next(seeds)
        arms = ['candidate', 'control'] if comparison else ['candidate']
        if orders[pair]:
            arms.reverse()
        for arm in arms:
            game_seed = shared_seed if design == 'matched' or arm == arms[0] else next(seeds)
            slots.append({'slot': len(slots) + 1, 'pair': pair + 1, 'arm': arm,
                          'seat': seats[pair], 'seed': str(game_seed)})
    return slots


def make_plan(args):
    session = load_session(args.session)
    multi = session.play_template[1]['multi']
    options = multi.get('gameOptions')
    if options and not re.fullmatch(r'\s*seed\s*=\s*-?\d+\s*', options):
        raise ValueError('Captured custom game options require review before planning')
    builds = {'candidate': artifact(args.candidate, args.candidate_check)}
    if args.control:
        if not args.control_check:
            raise ValueError('--control-check is required with --control')
        builds['control'] = artifact(args.control, args.control_check)
        if builds['candidate']['buildId'] == builds['control']['buildId']:
            raise ValueError('Candidate and control are the same build')
    elif args.design == 'matched':
        raise ValueError('Matched design requires both candidate and control')
    out = Path(args.out).resolve()
    if out.exists():
        raise ValueError('Choose a new batch directory; existing plans are preserved')
    request_boss = next(agent for agent in multi['agentsIds'] if agent != -1)
    expected_boss = getattr(args, 'boss_agent_id', None) or request_boss
    if expected_boss <= 0:
        raise ValueError('The browser uses boss sentinel -2; provide --boss-agent-id from its canonical replay')
    result = {'schemaVersion': 1, 'createdAt': now(), 'purpose': args.purpose,
              'design': args.design, 'gamesPerBuild': args.games,
              'scheduleSeed': args.seed, 'builds': builds,
              'sessionBindingSha256': session_binding(session),
              'bossAgentId': expected_boss, 'requestBossAgentId': request_boss,
              'mapHashScheme': MAP_SCHEME, 'excludedMapHashes': known_maps(),
              'outcome': 'outright win; draws are non-wins', 'alpha': 0.05,
              'test': ('exact paired discordances' if args.design == 'matched' else 'one-sided Fisher')
                      if 'control' in builds else 'descriptive only',
              'slots': make_schedule(args.games, args.design, args.seed, 'control' in builds),
              'promotion': False,
              'policy': 'Fixed complete sample; no tuning, outcome-based early stop, replacement or extension.'}
    result['planId'] = digest(encoded(result))
    write_once(out / 'plan.json', result)
    print(f"Plan saved: {out / 'plan.json'} ({len(result['slots'])} games; no API calls)")


def load_plan(path):
    path = Path(path).resolve()
    plan = read_json(path)
    identity = plan.pop('planId')
    if plan.get('schemaVersion') != 1 or digest(encoded(plan)) != identity:
        raise ValueError('Plan has changed; refuse to run')
    plan['planId'] = identity
    return path.parent, plan


def find_game_id(value):
    ids = set()

    def visit(item, depth=0):
        if depth > 20:
            raise ValueError('Unexpected play response nesting')
        if isinstance(item, dict):
            if 'gameId' in item:
                identifier = item['gameId']
                if isinstance(identifier, bool) or not re.fullmatch(r'[1-9][0-9]*', str(identifier)):
                    raise ValueError('Invalid game ID in play response')
                ids.add(int(identifier))
            for child in item.values():
                if isinstance(child, (dict, list)):
                    visit(child, depth + 1)
        elif isinstance(item, list):
            for child in item:
                visit(child, depth + 1)
    visit(value)
    if len(ids) != 1:
        raise ValueError('Play response has no unique game ID; do not retry play')
    return ids.pop()


def play_arguments(session, slot, build, boss):
    args = copy.deepcopy(session.play_template)
    source = (Path(build['directory']) / 'Player.java').read_bytes()
    if digest(source) != build['sourceSha256']:
        raise ValueError('Source changed after planning')
    args[1]['code'] = source.decode('utf-8')
    multi = args[1]['multi']
    multi['agentsIds'] = [-1, boss] if slot['seat'] == 0 else [boss, -1]
    multi['playType'] = ['IDE_CODE', 'BOSS'] if slot['seat'] == 0 else ['BOSS', 'IDE_CODE']
    multi['gameOptions'] = f"seed={slot['seed']}\n"
    return args


def completed_replay(raw):
    """Check readiness before committing immutable canonical bytes or capture metadata."""
    ranks = raw.get('ranks')
    if (not isinstance(ranks, list) or len(ranks) != 2
            or any(type(rank) is not int or not 0 <= rank <= 2 for rank in ranks)):
        raise ValueError('Canonical replay has no unambiguous final ranks')
    end_screens = []
    for frame in raw['frames']:
        view = view_json(frame)
        if isinstance(view, dict) and 'endScreen' in view:
            end_screens.append(view['endScreen'])
    if (len(end_screens) != 1 or not isinstance(end_screens[0], list) or not end_screens[0]
            or not isinstance(end_screens[0][0], list) or len(end_screens[0][0]) != 2
            or any(type(score) not in (int, float) or not math.isfinite(score) for score in end_screens[0][0])):
        raise ValueError('Canonical replay does not identify a completed game')
    map_hash(raw)
    referee_input = raw.get('refereeInput')
    seed = re.fullmatch(r'\s*seed\s*=\s*(-?\d+)\s*', referee_input) if isinstance(referee_input, str) else None
    if not seed:
        raise ValueError('Canonical replay has no unambiguous map seed')
    return ranks, end_screens[0][0], seed[1]


def outcome(raw, game_id, slot, boss):
    actual_id, player, boss_slot = replay_identity(raw)
    if actual_id != game_id or player != slot['seat']:
        raise ValueError('Replay game ID or player seat differs from planned request')
    boss_agent = next(agent for agent in raw['agents'] if agent['index'] == boss_slot)
    if boss_agent['agentId'] != boss:
        raise ValueError('Boss agent changed')
    ranks, scores, seed = completed_replay(raw)
    if int(seed) != int(slot['seed']):
        raise ValueError('API did not reproduce the requested seed')
    result = 'W' if ranks[player] < ranks[boss_slot] else 'L' if ranks[player] > ranks[boss_slot] else 'D'
    return {'gameId': game_id, 'seat': player, 'seed': seed, 'refereeInput': raw['refereeInput'], 'result': result,
            'scores': scores, 'ranks': ranks, 'mapHash': map_hash(raw),
            'boss': boss_agent, 'gameInformation': [f['gameInformation'] for f in raw['frames'] if f.get('gameInformation')]}


def result_records(directory, plan):
    records = []
    for slot in plan['slots']:
        path = directory / f"{slot['slot']:04d}" / 'result.json'
        if path.exists():
            value = read_json(path)
            if value.get('planId') != plan['planId'] or value.get('slot') != slot:
                raise ValueError('Result does not belong to this fixed plan')
            validate_intent(path.parent, plan, slot)
            if bound_game_id(path.parent) != value['outcome']['gameId']:
                raise ValueError('Saved result is not the game returned for its original play intent')
            raw_path = (ROOT / value['capture']['path']).resolve()
            if not raw_path.is_relative_to((ROOT / 'replays/cg/boss').resolve()):
                raise ValueError('Result capture path escapes the boss replay directory')
            raw_bytes = raw_path.read_bytes()
            if digest(raw_bytes) != value['capture']['rawSha256']:
                raise ValueError('Saved canonical replay changed')
            raw = json.loads(raw_bytes)
            recomputed = outcome(raw, value['outcome']['gameId'], slot, plan['bossAgentId'])
            if recomputed != value['outcome']:
                raise ValueError('Saved result differs from its canonical replay')
            identity = identify_build(raw, slot['seat'], ROOT)
            expected = identity['retainedArtifactVerified'] and identity.get('buildId') == plan['builds'][slot['arm']]['buildId']
            if value['capture']['expectedBuildMatches'] != expected:
                raise ValueError('Saved build attribution changed or disagrees with its canonical replay')
            records.append(value)
    return records


def check_sample(plan, records):
    ids, maps = set(), {}
    boss_identity = None
    for record in records:
        value, slot = record['outcome'], record['slot']
        boss = value.get('boss')
        if boss is not None:
            observed_boss = (boss.get('agentId'), boss.get('arenaboss'))
            if boss_identity is not None and boss_identity != observed_boss:
                raise ValueError('Boss or league identity changed during the sample')
            boss_identity = observed_boss
        if value['gameId'] in ids:
            raise ValueError('Game ID appears in multiple scheduled slots')
        ids.add(value['gameId'])
        if value['mapHash'] in plan['excludedMapHashes']:
            raise ValueError('A scheduled game reused a map known before this plan')
        previous = maps.get(value['mapHash'])
        if previous and (plan['design'] != 'matched' or previous['pair'] != slot['pair'] or previous['arm'] == slot['arm']):
            raise ValueError('Different sampling units unexpectedly share a map')
        maps[value['mapHash']] = slot
    if plan['design'] == 'matched':
        by_pair = {}
        for record in records:
            key = record['slot']['pair']
            current = (record['outcome']['mapHash'], record['outcome']['seat'])
            if key in by_pair and by_pair[key] != current:
                raise ValueError('Matched builds did not receive the same map and seat')
            by_pair[key] = current


def validate_intent(folder, plan, slot):
    previous = read_json(folder / 'intent.json')
    if (previous.get('planId') != plan['planId'] or previous.get('slot') != slot
            or previous.get('sourceSha256') != plan['builds'][slot['arm']]['sourceSha256']):
        raise ValueError('Saved intent does not match the fixed request')


def bound_game_id(folder):
    response, recovered = folder / 'play-response.json', folder / 'recovered-game.json'
    received_id = None
    if response.exists():
        try:
            received_id = find_game_id(read_json(response))
        except ValueError:
            if not recovered.exists():
                raise
    if recovered.exists():
        recovered_id = read_json(recovered).get('gameId')
        if type(recovered_id) is not int or recovered_id <= 0 or received_id not in (None, recovered_id):
            raise ValueError('Recovery ID is invalid or contradicts saved play response')
        return recovered_id
    if received_id is None:
        raise ValueError('Unresolved play intent. Use recover with its real game ID; never repeat play.')
    return received_id


def run(args):
    directory, plan = load_plan(args.plan)
    session = load_session(args.session)
    if session_binding(session) != plan['sessionBindingSha256']:
        raise ValueError('Session template differs from the planned boss/IDE configuration')
    for value in plan['builds'].values():
        if artifact(value['directory'], value['processCheck']) != value:
            raise ValueError('Planned artifact or process-check evidence changed')
    with run_lock(directory):
        records = result_records(directory, plan)
        check_sample(plan, records)
        if any(not value['capture']['expectedBuildMatches'] for value in records):
            raise ValueError('Recorded build mismatch interrupts this batch; it cannot be bypassed by resuming')
        for slot in plan['slots']:
            folder = directory / f"{slot['slot']:04d}"
            if (folder / 'result.json').exists():
                continue
            folder.mkdir(exist_ok=True)
            build = plan['builds'][slot['arm']]
            intent = folder / 'intent.json'
            received = folder / 'play-response.json'
            recovered = folder / 'recovered-game.json'
            if intent.exists():
                validate_intent(folder, plan, slot)
            if not intent.exists():
                if any(folder.iterdir()):
                    raise ValueError('Slot evidence exists without its intent; refusing another play')
                request = play_arguments(session, slot, build, plan['requestBossAgentId'])
                write_once(intent, {'planId': plan['planId'], 'slot': slot, 'startedAt': now(),
                                    'sourceSha256': build['sourceSha256']})
                print(f"Starting {slot['slot']}/{len(plan['slots'])}: {slot['arm']} P{slot['seat']}", flush=True)
                try:
                    response = call_service_text(session, 'TestSession/play', request, timeout=args.timeout)
                except ApiError as error:
                    private_body = getattr(error, 'response_bytes', None)
                    if private_body:
                        private_error = ROOT / 'build/cg-boss/private/api-errors' / plan['planId'] / f"{slot['slot']:04d}.bin"
                        private_error.parent.mkdir(parents=True, exist_ok=True)
                        with private_error.open('xb') as stream:
                            stream.write(private_body)
                    write_once(folder / 'api-error.json', {'status': error.status, 'uncertain': error.uncertain,
                                                         'message': str(error), 'apiCode': getattr(error, 'error_code', None),
                                                         'at': now()})
                    raise ValueError('Play failed or its outcome is unknown. Intent retained; no automatic replay.') from None
                with received.open('xb') as stream:
                    stream.write(response.encode('utf-8'))
            game_id = bound_game_id(folder)
            canonical = folder / 'canonical.json'
            if not canonical.exists():
                response = call_service_text(session, 'gameResult/findByGameId', [game_id, None], timeout=30)
                try:
                    raw = json.loads(response)
                    if replay_identity(raw)[0] != game_id:
                        raise ValueError('Canonical lookup returned a different game')
                    completed_replay(raw)
                except (ValueError, TypeError, KeyError):
                    rejected = folder / f'canonical-rejected-{time.time_ns()}.txt'
                    rejected.write_text(response, encoding='utf-8')
                    raise ValueError('Canonical lookup is not ready/valid. Response retained; resume retries lookup only.') from None
                with canonical.open('xb') as stream:
                    stream.write(response.encode('utf-8'))
            text = canonical.read_bytes().decode('utf-8')
            # Save all canonical outcomes before checking the planned attribution/map.
            capture = save_capture({'kind': 'boss', 'content': text, 'expectedBuildId': build['buildId']}, ROOT)
            value = outcome(json.loads(text), game_id, slot, plan['bossAgentId'])
            record = {'planId': plan['planId'], 'slot': slot, 'completedAt': now(),
                      'capture': capture, 'outcome': value}
            write_once(folder / 'result.json', record)
            records.append(record)
            if not capture['expectedBuildMatches']:
                raise ValueError('Replay marker does not match planned build. Outcome retained; batch interrupted.')
            check_sample(plan, records)
            print(f"Saved {game_id}: {capture['path']}", flush=True)
            if args.delay and slot != plan['slots'][-1]:
                time.sleep(args.delay)
    report(args)


def recover(args):
    directory, plan = load_plan(args.plan)
    if not 1 <= args.slot <= len(plan['slots']) or args.game_id <= 0:
        raise ValueError('Invalid slot or game ID')
    with run_lock(directory):
        folder = directory / f'{args.slot:04d}'
        if not (folder / 'intent.json').exists() or (folder / 'result.json').exists():
            raise ValueError('Only an unresolved recorded play intent can be recovered')
        validate_intent(folder, plan, plan['slots'][args.slot - 1])
        if (folder / 'play-response.json').exists():
            try:
                received_id = find_game_id(read_json(folder / 'play-response.json'))
            except ValueError:
                received_id = None
            if received_id is not None and received_id != args.game_id:
                raise ValueError('Recovery ID contradicts saved play response')
        write_once(folder / 'recovered-game.json', {'gameId': args.game_id, 'recordedAt': now()})
    print('Game ID recorded. Run resumes canonical lookup only; it will not repeat play.')


def report(args):
    directory, plan = load_plan(args.plan)
    records = result_records(directory, plan)
    issues = []
    try:
        check_sample(plan, records)
    except ValueError as error:
        issues.append(str(error))
    if any(not record['capture']['expectedBuildMatches'] for record in records):
        issues.append('At least one replay has no matching planned build marker')
    complete = len(records) == len(plan['slots']) and not issues
    arms = {}
    for arm in plan['builds']:
        outcomes = [r['outcome']['result'] for r in records if r['slot']['arm'] == arm]
        count, wins, draws = len(outcomes), outcomes.count('W'), outcomes.count('D')
        arms[arm] = {'games': count, 'wins': wins, 'draws': draws, 'losses': outcomes.count('L'),
                     'winRate': wins / count if count else None,
                     'halfWinScore': (wins + draws / 2) / count if count else None,
                     'winRateExact95': clopper_pearson(wins, count) if count else None}
    result = {'planId': plan['planId'], 'complete': complete, 'issues': issues, 'arms': arms,
              'scheduledGames': len(plan['slots']), 'completedGames': len(records), 'promotion': False}
    if complete and 'control' in arms and plan['purpose'] != 'smoke':
        if plan['design'] == 'matched':
            pairs = {}
            for record in records:
                pairs.setdefault(record['slot']['pair'], {})[record['slot']['arm']] = record['outcome']['result'] == 'W'
            b = sum(p['candidate'] and not p['control'] for p in pairs.values())
            c = sum(p['control'] and not p['candidate'] for p in pairs.values())
            p = binomial_upper(b, b + c, 0.5) if b + c else 1.0
            result['discordances'] = {'candidateOnlyWin': b, 'controlOnlyWin': c}
        else:
            p = fisher_p(arms['candidate']['wins'], arms['candidate']['games'], arms['control']['wins'], arms['control']['games'])
        result.update(oneSidedP=p, bossScreen='evidence of improvement' if p <= plan['alpha'] else 'inconclusive for improvement')
    print(encoded(result), end='')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='command', required=True)
    create = sub.add_parser('plan', help='Freeze artifacts and a full schedule; no network calls')
    create.add_argument('--session', type=Path, default=DEFAULT_SESSION_PATH)
    create.add_argument('--candidate', type=Path, required=True)
    create.add_argument('--candidate-check', type=Path, required=True)
    create.add_argument('--control', type=Path)
    create.add_argument('--control-check', type=Path)
    create.add_argument('--boss-agent-id', type=int, help='Canonical positive boss ID when the captured request uses -2')
    create.add_argument('--games', type=int, default=40, help='Games PER build')
    create.add_argument('--design', choices=('matched', 'independent'), default='matched')
    create.add_argument('--purpose', choices=('smoke', 'boss-screen'), default='boss-screen')
    create.add_argument('--seed', type=int, default=2026091801, help='Schedule randomization seed')
    create.add_argument('--out', type=Path, required=True)
    create.set_defaults(function=make_plan)
    execute = sub.add_parser('run', help='Run or resume the fixed plan sequentially')
    execute.add_argument('--plan', type=Path, required=True)
    execute.add_argument('--session', type=Path, default=DEFAULT_SESSION_PATH)
    execute.add_argument('--timeout', type=float, default=120)
    execute.add_argument('--delay', type=float, default=2)
    execute.set_defaults(function=run)
    resume = sub.add_parser('recover', help='Attach a known created game ID to an uncertain attempt; no play')
    resume.add_argument('--plan', type=Path, required=True)
    resume.add_argument('--slot', type=int, required=True)
    resume.add_argument('--game-id', type=int, required=True)
    resume.set_defaults(function=recover)
    summary = sub.add_parser('report', help='Read saved results; compute a test only for a complete valid sample')
    summary.add_argument('--plan', type=Path, required=True)
    summary.set_defaults(function=report)
    args = parser.parse_args()
    if args.command == 'run' and (not math.isfinite(args.timeout) or not 1 <= args.timeout <= 600
                                 or not math.isfinite(args.delay) or not 0 <= args.delay <= 60):
        parser.error('Timeout must be 1..600 seconds and delay 0..60 seconds')
    try:
        args.function(args)
    except (ValueError, OSError, KeyError, TypeError, ApiError) as error:
        # Raw HTTP response bodies and request arguments are never printed here.
        if isinstance(error, (KeyError, TypeError)):
            parser.exit(1, 'Unsupported artifact or replay structure; records retained.\n')
        parser.exit(1, f'{error}\n')


if __name__ == '__main__':
    main()
