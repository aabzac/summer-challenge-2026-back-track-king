"""Read-only independent audit of the fixed chords/v07 live-boss screen.

No API/session imports, game creation, source mutation, or partial final exports.
Canonical outcomes are recomputed here, independently of boss_batch's result code.
Only an explicit whitelist of public evidence is exported after all 80 games pass.
"""
from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
import json
import math
from pathlib import Path
import re
import sys

TOOLS = Path(__file__).resolve().parents[1]
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))
from replay_capture import identify_build, replay_identity, validate_local_build
from diagnostics.boss_evaluation_protocol import clopper_pearson

ROOT = TOOLS.parent
PROTOCOL = Path('docs/chords-boss-v1.md')
LAUNCH = Path('docs/evidence/network-chords-v1.json')
OUTPUT = Path('docs/evidence/chords-boss-v1.json')
INTERRUPTED_OUTPUT = Path('docs/evidence/chords-boss-v1.interrupted.json')
MAP_SCHEME = 'cg-global-graphics-utf8-sha256-v1'
CANDIDATE = 'current?USE_ROLLOUT_NETWORK_CHORDS=1,USE_ROLLOUT_ROUTE_DEPENDENCIES=0'


@dataclass(frozen=True)
class Pins:
    # Read when this auditor was assigned, before inspecting any screen outcomes.
    protocol: str = '08dbec1aae56a58b3d82581720e48ba59dbb9c06483be899f086bee541c02b82'
    launch: str = '1e3f167bcddda2244f0b84ff7c8294f6501487a196bf46a8fd971d90fb3d6384'
    plan: str = '76daa81abb87f2846674a2707a3bb07451a67aa6b00d3d236a1af8bdff897d66'
    plan_id: str = '3d951ec7e4ad1878e4e135d11b9f3a74b4611964074918c0d223cc06e90ee24b'
    candidate_build: str = 'faa7087da1c02859900cb43afb026be3e18abdbadd84ddb0c74a3830b2169783'
    candidate_source: str = 'b14cc066978e82c4b7e364ca4b9126351f7e18fad9bd3e41b86dd23eb1c66353'
    control_build: str = '66c56c429c80a7861b199597f2fbeb5f1f1830b525977cacd8304f2d892ddb28'
    control_source: str = 'f8cd5b81217537d1d4c98dcbea1be50d8f81ca9c9f93d4d742b0a19831021015'


class IncompleteError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(content):
    return hashlib.sha256(content if isinstance(content, bytes) else content.encode('utf-8')).hexdigest()


def encoded(value):
    return json.dumps(value, ensure_ascii=False, sort_keys=True, indent=2) + '\n'


def read(path):
    return json.loads(Path(path).read_bytes())


def within(root, path, allowed):
    path = (root / path).resolve()
    require(path.is_relative_to((root / allowed).resolve()), 'Evidence path is outside its permitted directory')
    require('private' not in path.relative_to(root).parts, 'Private session files are outside the audit scope')
    return path


def checked_file(path, expected, label):
    content = path.read_bytes()
    require(sha(content) == expected, f'{label} SHA-256 changed')
    return content


def classes_hash(directory):
    root = directory / 'classes'
    files = sorted(root.rglob('*.class'))
    require((root / 'Player.class') in files, 'Compiled Player.class is missing')
    digest = hashlib.sha256()
    for file in files:
        digest.update(file.relative_to(root).as_posix().encode('utf-8'))
        digest.update(b'\0')
        digest.update(file.read_bytes())
    return digest.hexdigest()


def audit_process(path, build):
    raw = checked_file(path, build['processCheckSha256'], 'Process report')
    report = json.loads(raw)
    require(report.get('schemaVersion') == 1 and report.get('strict') is True
            and type(report.get('graceMs')) is int and report['graceMs'] == 0
            and type(report.get('problems')) is int and report['problems'] == 0,
            'Exact artifact process check is not strict and successful')
    require(report.get('sourceSha256') == build['sourceSha256']
            and report.get('classesSha256') == build['classesSha256'], 'Process report identifies another artifact')
    games = report.get('games')
    require(isinstance(games, list) and len(games) == 4, 'Expected four exact-artifact process games')
    maximum = 0.0
    for game in games:
        result, timings = game.get('result'), game.get('timings')
        require(type(game.get('timeouts')) is int and game['timeouts'] == 0
                and type(game.get('nominalOverruns')) is int and game['nominalOverruns'] == 0
                and type(game.get('player')) is int and game['player'] in (0, 1), 'Process game has errors or overruns')
        require(isinstance(result, dict) and result.get('error') == [None, None]
                and type(result.get('turns')) is int and result['turns'] > 0
                and type(result.get('winner')) is int and result['winner'] in (-1, 0, 1)
                and isinstance(result.get('score'), list) and len(result['score']) == 2
                and all(type(score) is int for score in result['score']), 'Process game is incomplete')
        require(isinstance(timings, list) and timings, 'Process timings are missing')
        for timing in timings:
            response = timing.get('responseMs')
            require(timing.get('status') == 'OK' and type(timing.get('turn')) is int and timing['turn'] >= 0
                    and timing.get('graceMs') == 0 and type(response) in (int, float)
                    and math.isfinite(response) and response >= 0, 'Process timing is not successful')
            if timing['turn'] > 0:
                maximum = max(maximum, response)
    return {'reportSha256': sha(raw), 'games': 4, 'strict': True, 'timeouts': 0,
            'nominalOverruns': 0, 'problems': 0, 'maxLaterResponseMs': maximum}


def audit_builds(root, plan, pins):
    builds, params = {}, {}
    for arm, expected_build, expected_source, strategy in (
            ('candidate', pins.candidate_build, pins.candidate_source, CANDIDATE),
            ('control', pins.control_build, pins.control_source, 'v07')):
        build = plan['builds'][arm]
        require(build['buildId'] == expected_build and build['sourceSha256'] == expected_source
                and build['strategy'] == strategy, 'Plan changed its registered build')
        manifest = validate_local_build(root, expected_build)
        directory = within(root, build['directory'], 'build/cg-boss/artifacts')
        exported = read(directory / 'manifest.json')
        # The first retained registration may use a shorter equivalent bot spec. Its
        # resolved parameters and every actual artifact/marker identity must still agree.
        require({key: value for key, value in exported.items() if key != 'strategy'}
                == {key: value for key, value in manifest.items() if key != 'strategy'}
                and exported.get('strategy') == strategy, 'Exported and registered manifests differ')
        checked_file(directory / 'Player.java', expected_source, 'Exported source')
        require(classes_hash(directory) == build['classesSha256'], 'Executable classes changed')
        require(manifest['configSha256'] == build['configSha256'], 'Configuration identity changed')
        params[arm] = manifest['params']
        process = audit_process(within(root, build['processCheck'], 'build/cg-boss/artifacts'), build)
        builds[arm] = {key: build[key] for key in ('strategy', 'buildId', 'sourceSha256', 'classesSha256', 'configSha256')}
        builds[arm]['process'] = process
        builds[arm]['flags'] = {key: manifest['params'][key] for key in (
            'USE_ROLLOUT_NETWORK_CHORDS', 'USE_ROLLOUT_ROUTE_DEPENDENCIES', 'USE_ROLLOUT_NETWORK_AFTER_BASE')}
    require(params['candidate']['USE_ROLLOUT_NETWORK_CHORDS'] == 'true'
            and params['control']['USE_ROLLOUT_NETWORK_CHORDS'] == 'false', 'Chord ablation flag differs')
    for arm in params:
        require(params[arm]['USE_ROLLOUT_ROUTE_DEPENDENCIES'] == 'false'
                and params[arm]['USE_ROLLOUT_NETWORK_AFTER_BASE'] == 'true', 'Unregistered strategy flag combination')
    delta = {key for key in params['candidate'].keys() | params['control'].keys()
             if params['candidate'].get(key) != params['control'].get(key)}
    require(delta == {'USE_ROLLOUT_NETWORK_CHORDS'}, 'More than the chord parameter changed')
    return builds


def audit_submission(root, pins):
    historical = json.loads(checked_file(root / LAUNCH, pins.launch, 'Original local experiment evidence'))
    expected = historical['launch']['submissionFiles']
    require(len(expected) == 26, 'Expected the original 26 submission files')
    actual = {path.relative_to(root).as_posix(): sha(path.read_bytes())
              for path in (root / 'build/submit').rglob('*') if path.is_file()}
    require(actual == expected, 'Original build/submit file set or bytes changed')
    return {'verifiedFiles': 26, 'fileMapSha256': sha(encoded(expected)), 'referenceSha256': pins.launch}


def view(frame):
    text = frame.get('view', '')
    require(isinstance(text, str), 'Malformed canonical view')
    body = text.partition('\n')[2].strip()
    value = json.loads(body) if body else {}
    require(isinstance(value, dict), 'Malformed canonical view JSON')
    return value


def response_id(value):
    ids = set()
    def visit(item, depth=0):
        require(depth <= 20, 'Play response nesting is unsupported')
        if isinstance(item, dict):
            if 'gameId' in item:
                identifier = item['gameId']
                require(not isinstance(identifier, bool) and re.fullmatch(r'[1-9][0-9]*', str(identifier)),
                        'Malformed play response game ID')
                ids.add(int(identifier))
            for child in item.values():
                if isinstance(child, (dict, list)):
                    visit(child, depth + 1)
        elif isinstance(item, list):
            for child in item:
                visit(child, depth + 1)
    visit(value)
    require(len(ids) == 1, 'No unique recorded play response game ID')
    return ids.pop()


def bound_id(folder):
    recovered = read(folder / 'recovered-game.json')['gameId'] if (folder / 'recovered-game.json').exists() else None
    response = folder / 'play-response.json'
    returned = None
    if response.exists():
        try:
            returned = response_id(read(response))
        except ValueError:
            require(recovered is not None, 'Unresolved recorded play response')
    require(recovered is None or type(recovered) is int and recovered > 0, 'Invalid recovered game ID')
    require(returned is None or recovered is None or returned == recovered, 'Recovery contradicts original response')
    require(returned is not None or recovered is not None, 'No game is bound to the original play intent')
    return recovered if recovered is not None else returned


def audit_game(root, folder, plan, slot):
    record = read(folder / 'result.json')
    intent = read(folder / 'intent.json')
    build = plan['builds'][slot['arm']]
    require(intent.get('planId') == plan['planId'] and intent.get('slot') == slot
            and intent.get('sourceSha256') == build['sourceSha256'], 'Intent is not bound to the fixed request')
    require(record.get('planId') == plan['planId'] and record.get('slot') == slot, 'Result belongs to another slot or plan')
    raw_bytes = (folder / 'canonical.json').read_bytes()
    capture = record['capture']
    archived = within(root, capture['path'], 'replays/cg/boss')
    require(raw_bytes == archived.read_bytes() and sha(raw_bytes) == capture['rawSha256'], 'Canonical capture bytes differ')
    raw = json.loads(raw_bytes)
    game_id, player, boss_slot = replay_identity(raw)
    require(game_id == bound_id(folder) and player == slot['seat'], 'Canonical game/seat differs from original request')
    boss = next(agent for agent in raw['agents'] if agent['index'] == boss_slot)
    require(boss['agentId'] == 6740207 and isinstance(boss.get('arenaboss'), dict)
            and boss['arenaboss'].get('nickname') == 'Boss 4', 'Boss identity changed')
    identity = identify_build(raw, player, root)
    require(identity['retainedArtifactVerified'] is True and identity['buildId'] == build['buildId']
            and identity['artifactSha256'] == build['sourceSha256'] and identity['configSha256'] == build['configSha256'],
            'Canonical first-turn marker does not match its planned retained artifact')
    require(capture.get('expectedBuildMatches') is True, 'Recorded attribution is not successful')
    ranks = raw.get('ranks')
    require(isinstance(ranks, list) and len(ranks) == 2 and all(type(rank) is int and 0 <= rank <= 2 for rank in ranks),
            'Canonical ranks are invalid')
    require(all(isinstance(frame, dict) for frame in raw['frames']), 'Malformed canonical frames')
    views = [view(frame) for frame in raw['frames']]
    graphics = views[0].get('global', {}).get('graphics')
    require(isinstance(graphics, str) and graphics, 'Canonical map is missing')
    endings = [item['endScreen'] for item in views if 'endScreen' in item]
    require(len(endings) == 1 and isinstance(endings[0], list) and endings[0]
            and isinstance(endings[0][0], list) and len(endings[0][0]) == 2
            and all(type(score) in (int, float) and math.isfinite(score) for score in endings[0][0]),
            'Canonical game is not completed')
    seed = raw.get('refereeInput')
    match = re.fullmatch(r'\s*seed\s*=\s*(-?[0-9]+)\s*', seed) if isinstance(seed, str) else None
    require(match is not None and int(match[1]) == int(slot['seed']), 'Canonical seed differs from requested seed')
    result = 'W' if ranks[player] < ranks[boss_slot] else 'L' if ranks[player] > ranks[boss_slot] else 'D'
    recomputed = {'gameId': game_id, 'seat': player, 'seed': match[1], 'refereeInput': seed,
                  'result': result, 'scores': endings[0][0], 'ranks': ranks, 'mapHash': sha(graphics), 'boss': boss,
                  'gameInformation': [frame['gameInformation'] for frame in raw['frames'] if frame.get('gameInformation')]}
    require(record['outcome'] == recomputed, 'Saved outcome differs from independent canonical recomputation')
    failures = []
    for index, frame in enumerate(raw['frames']):
        summary = frame.get('summary')
        if summary:
            require(isinstance(summary, str), 'Malformed terminal summary')
            failures.append({'frame': index, 'summarySha256': sha(summary),
                             'timeoutPlayers': [int(p) for p in re.findall(r'\$([01]) has not provided', summary)]})
    # No API handles, request payloads, source, private paths, stdout or arbitrary server text.
    public = {key: recomputed[key] for key in ('gameId', 'seat', 'seed', 'result', 'scores', 'ranks', 'mapHash')}
    public.update(slot=slot['slot'], pair=slot['pair'], arm=slot['arm'], rawSha256=sha(raw_bytes),
                  buildId=build['buildId'], terminalFailures=failures)
    return public, encoded(boss['arenaboss'])


def incomplete_attempts(directory, plan, missing):
    attempts = []
    for number in missing:
        folder, slot = directory / f'{number:04d}', plan['slots'][number-1]
        if not folder.exists() or not any(folder.iterdir()):
            continue
        intent = read(folder / 'intent.json')
        require(intent.get('planId') == plan['planId'] and intent.get('slot') == slot
                and intent.get('sourceSha256') == plan['builds'][slot['arm']]['sourceSha256'],
                'Incomplete intent differs from the fixed request')
        attempt = {'slot': number, 'pair': slot['pair'], 'arm': slot['arm'], 'seat': slot['seat'],
                   'seed': slot['seed'], 'state': 'intent_without_completed_result',
                   'playResponseRetained': (folder/'play-response.json').is_file(),
                   'canonicalRetained': (folder/'canonical.json').is_file(),
                   'recoveryIdRetained': (folder/'recovered-game.json').is_file()}
        if attempt['playResponseRetained'] or attempt['recoveryIdRetained']:
            try:
                attempt['boundGameId'] = bound_id(folder)
            except ValueError:
                attempt['responseIdentityUnresolved'] = True
        if (folder/'api-error.json').is_file():
            payload = (folder/'api-error.json').read_bytes()
            error = json.loads(payload)
            require(error.get('status') is None or type(error['status']) is int, 'Invalid retained HTTP status')
            require(type(error.get('uncertain')) is bool, 'Invalid retained transport uncertainty')
            attempt.update(httpStatus=error.get('status'), transportReportedUncertain=error['uncertain'],
                           apiErrorRecordSha256=sha(payload))
        attempts.append(attempt)
    return attempts


def audit(plan_path, root=ROOT, pins=Pins(), allow_interrupted=False):
    root, plan_path = Path(root).resolve(), Path(plan_path).resolve()
    within(root, plan_path, 'build/cg-boss')
    checked_file(root / PROTOCOL, pins.protocol, 'Prospective protocol')
    plan_bytes = checked_file(plan_path, pins.plan, 'Fixed plan')
    plan = json.loads(plan_bytes)
    unsigned = {key: value for key, value in plan.items() if key != 'planId'}
    require(plan['planId'] == pins.plan_id == sha(encoded(unsigned)), 'Plan identity is invalid')
    require(plan['schemaVersion'] == 1 and plan['design'] == 'matched' and plan['gamesPerBuild'] == 40
            and plan['purpose'] == 'boss-screen' and plan['scheduleSeed'] == 2026091840
            and plan['bossAgentId'] == 6740207 and plan['requestBossAgentId'] == -2
            and plan['mapHashScheme'] == MAP_SCHEME and plan['alpha'] == 0.05 and plan['promotion'] is False,
            'Plan differs from the fixed external screen design')
    require(set(plan['builds']) == {'candidate', 'control'}, 'Wrong comparison arms')
    slots = plan['slots']
    require(len(slots) == 80 and [slot['slot'] for slot in slots] == list(range(1, 81)), 'Wrong scheduled slot set')
    pairs = {}
    for slot in slots:
        require(type(slot['pair']) is int and 1 <= slot['pair'] <= 40 and slot['arm'] in plan['builds']
                and type(slot['seat']) is int and slot['seat'] in (0, 1)
                and isinstance(slot['seed'], str) and re.fullmatch(r'[1-9][0-9]*', slot['seed']), 'Malformed fixed slot')
        pairs.setdefault(slot['pair'], []).append(slot)
    require(set(pairs) == set(range(1, 41)), 'Wrong scheduled pair set')
    for pair in pairs.values():
        require(len(pair) == 2 and {slot['arm'] for slot in pair} == {'candidate', 'control'}
                and pair[0]['seed'] == pair[1]['seed'] and pair[0]['seat'] == pair[1]['seat'], 'Pair seed/seat/arms differ')
    require(len({pair[0]['seed'] for pair in pairs.values()}) == 40
            and sum(pair[0]['seat'] == 0 for pair in pairs.values()) == 20
            and sum(pair[0]['arm'] == 'candidate' for pair in pairs.values()) == 20, 'Schedule is not balanced and fresh')
    excluded = plan['excludedMapHashes']
    require(isinstance(excluded, list) and excluded == sorted(set(excluded))
            and all(isinstance(value, str) and re.fullmatch(r'[0-9a-f]{64}', value) for value in excluded), 'Invalid exclusion set')
    missing = [slot['slot'] for slot in slots if not (plan_path.parent / f"{slot['slot']:04d}" / 'result.json').is_file()]
    if missing and not allow_interrupted:
        raise IncompleteError(f'Incomplete fixed sample: {80-len(missing)}/80 results; final evidence was not written')
    complete = not missing
    retained_slots = [slot for slot in slots if slot['slot'] not in missing]
    require([slot['slot'] for slot in retained_slots] == list(range(1, len(retained_slots)+1)),
            'Completed results are not a contiguous prefix of the fixed schedule')
    builds = audit_builds(root, plan, pins)
    submission = audit_submission(root, pins)
    games, boss_identities = [], set()
    for slot in retained_slots:
        game, boss_identity = audit_game(root, plan_path.parent / f"{slot['slot']:04d}", plan, slot)
        games.append(game); boss_identities.add(boss_identity)
    require(len(boss_identities) <= 1, 'Canonical boss/league identity changed across games')
    require(len({game['gameId'] for game in games}) == len(games), 'Duplicate game ID')
    require(not {game['mapHash'] for game in games} & set(excluded), 'Sample reused an excluded historical map')
    paired, partial_pairs, map_hashes = [], [], set()
    for pair_id in range(1, 41):
        pair = {game['arm']: game for game in games if game['pair'] == pair_id}
        if not pair:
            continue
        first = next(iter(pair.values()))
        require(first['mapHash'] not in map_hashes, 'Different pairs reused the same map')
        map_hashes.add(first['mapHash'])
        if len(pair) != 2:
            partial_pairs.append({'pair': pair_id, 'completedArm': first['arm'], 'gameId': first['gameId'],
                                  'mapHash': first['mapHash'], 'seat': first['seat'], 'seed': first['seed'],
                                  'result': first['result']})
            continue
        candidate, control = pair['candidate'], pair['control']
        require(candidate['mapHash'] == control['mapHash'] and candidate['seat'] == control['seat'], 'Canonical matched pair differs')
        paired.append({'pair': pair_id, 'mapHash': candidate['mapHash'], 'seat': candidate['seat'], 'seed': candidate['seed'],
                       'candidateGameId': candidate['gameId'], 'controlGameId': control['gameId'],
                       'candidate': candidate['result'], 'control': control['result']})
    aggregate = {}
    for arm in ('candidate', 'control'):
        values = [game for game in games if game['arm'] == arm]
        counts = Counter(game['result'] for game in values)
        n = len(values)
        aggregate[arm] = {'games': n, 'wins': counts['W'], 'losses': counts['L'], 'draws': counts['D'],
                          'winRate': counts['W']/n if n else None, 'halfWinScore': (counts['W']+counts['D']/2)/n if n else None,
                          'winRateExact95': clopper_pearson(counts['W'], n) if n else None,
                          'gamesWithTerminalFailureSummary': sum(bool(game['terminalFailures']) for game in values)}
    result = {'schemaVersion': 1, 'complete': complete, 'status': 'COMPLETE' if complete else 'INTERRUPTED',
            'auditedAtUtc': datetime.now(timezone.utc).isoformat(),
            'method': 'Independent canonical rank/map/seat/marker recomputation; fixed 40 matched pairs; draws are non-wins; no replacements or pooling.',
            'planId': plan['planId'], 'planSha256': sha(plan_bytes), 'protocol': PROTOCOL.as_posix(),
            'protocolSha256': pins.protocol, 'auditorSha256': sha(Path(__file__).read_bytes()),
            'scheduledGames': 80, 'completedGames': len(games), 'scheduledMapPairs': 40,
            'completedMapPairs': len(paired), 'observedUniqueMaps': len(map_hashes), 'mapHashScheme': MAP_SCHEME,
            'excludedMapCount': len(excluded), 'excludedMapSetSha256': sha(encoded(excluded)),
            'allMapsAbsentFromOriginalExclusionSet': True, 'boss': {'agentId': 6740207, 'nickname': 'Boss 4'},
            'builds': builds, 'originalSubmission': submission, 'aggregate': aggregate,
            'promotion': False, 'localStrengthGate': 'NOT_PASSED; unchanged by this exploratory external screen',
            'executionIdentityIndependentlyVerified': False,
            'attributionLimit': 'Self-reported first-turn markers match locally retained source/configuration; remote executed source is not independently proven.',
            'games': games, 'pairs': paired}
    if complete:
        b = sum(pair['candidate'] == 'W' and pair['control'] != 'W' for pair in paired)
        c = sum(pair['control'] == 'W' and pair['candidate'] != 'W' for pair in paired)
        numerator, denominator = sum(math.comb(b+c, value) for value in range(b, b+c+1)), 2**(b+c)
        p = numerator / denominator
        result.update(discordances={'candidateOnlyWin': b, 'controlOnlyWin': c},
                      exactTest={'name': 'one-sided exact paired discordances', 'numerator': numerator,
                                 'denominator': denominator, 'p': p, 'alpha': 0.05},
                      decision='evidence of improvement against this boss' if p <= 0.05 else 'inconclusive for improvement')
    else:
        attempts = incomplete_attempts(plan_path.parent, plan, missing)
        result.update(incompleteAttempts=attempts, partialPairs=partial_pairs,
                      notStartedSlots=len(missing)-len(attempts), remainingScheduledGames=len(missing),
                      limitation='Incomplete fixed sample. Counts are descriptive only; no significance test or improvement verdict. The original schedule and all unresolved intents remain preserved.')
    return result


def export(plan, output, root=ROOT, pins=Pins(), allow_interrupted=False):
    result = audit(plan, root, pins, allow_interrupted)
    output = within(Path(root).resolve(), output, 'docs/evidence')
    require(result['complete'] or output != (Path(root)/OUTPUT).resolve(), 'Partial evidence cannot use the final evidence path')
    require(not output.exists(), 'Final evidence already exists; it must not be overwritten')
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(encoded(result))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, required=True)
    parser.add_argument('--interrupted', action='store_true', help='Export a separate descriptive interruption audit; no test or verdict')
    parser.add_argument('--out', type=Path)
    args = parser.parse_args()
    try:
        output = args.out or ROOT / (INTERRUPTED_OUTPUT if args.interrupted else OUTPUT)
        result = export(args.plan, output, allow_interrupted=args.interrupted)
    except (ValueError, OSError, KeyError, TypeError, IndexError) as error:
        message = str(error) if isinstance(error, ValueError) else 'Incomplete or unsupported evidence structure; no final export'
        parser.exit(2, message + '\n')
    if result['complete']:
        print(f"Audit passed: 80 games / 40 pairs; exact p={result['exactTest']['p']:.8g}; public evidence written")
    else:
        print(f"Interrupted audit: {result['completedGames']}/80 games retained; no significance test or improvement verdict")


if __name__ == '__main__':
    main()
