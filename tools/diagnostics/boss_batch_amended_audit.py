"""Independent final audit of the explicitly authorized slot-21 and slot-51 retries.

The original auditor/protocol/plan/interruption bytes stay frozen. Original audit
helpers are reused through an explicit read-only file binding for the retry;
there is no API access, global monkeypatch, or modification of recorded attempts.
"""
from __future__ import annotations

import argparse
from collections import Counter
from dataclasses import dataclass
from datetime import datetime, timezone
import json
import math
from pathlib import Path
import sys

TOOLS = Path(__file__).resolve().parents[1]
if str(TOOLS) not in sys.path:
    sys.path.insert(0, str(TOOLS))
from diagnostics import boss_batch_audit as original

ROOT = TOOLS.parent
AMENDMENT = Path('docs/chords-boss-v1-amendment-1.md')
AMENDMENT_2 = Path('docs/chords-boss-v1-amendment-2.md')
RATE_LIMIT = Path('docs/evidence/chords-boss-v1.rate-limit.json')
INTERRUPTED = Path('docs/evidence/chords-boss-v1.interrupted.json')
OUTPUT = Path('docs/evidence/chords-boss-v1.amended.json')
DIAGNOSTIC = Path('replays/cg/boss/v07/66c56c429c80a7861b199597f2fbeb5f1f1830b525977cacd8304f2d892ddb28/904843581.json')
require, sha, encoded, read = original.require, original.sha, original.encoded, original.read


@dataclass(frozen=True)
class Pins(original.Pins):
    amendment: str = 'd53091fb2366a6319e028c60943ebd669e96f6cf52eaf20f83b909cc14b55079'
    amendment_2: str = '3d5f399720336630850c47af63e836647b31cc0eb08fda74dcf0d121c567bfb3'
    rate_limit: str = '7ed0cb41033d1b14657ce37d33cfe9aa11607941dae8e6f2434f1fdf91bbb074'
    first_fifty_results: str = 'e3fa7a8d391e36f1729489264b6d7f1ac74484da3389447b7a3d7c7ace96f336'
    interruption: str = '9efc3345ef8fd9786582fa481ed88bc1f205af7fc20aece79b9e7a96b3224e7c'
    original_auditor: str = '51e02937f31acf16d10ee3c114cfe347f594c39a0f2fb0631475b8802a804136'
    original_intent: str = '1907138b397caabd2fc47a0aa788e306b4475634e29b477ff77c2f41bba291a5'
    original_error: str = '021d7db1a3607a7db17bbe0638e54812a7cbae95ee5fbe8156f9ee908b7644a0'
    rate_limit_intent: str = '265f94235e1860b32ef9eb903d7cd1f75802e74a42a8012d14e773704ecadae9'
    rate_limit_error: str = '0fe8a75b2aef4b9e74de0d8c2497d9c412678aacd3d2c943500d752c9256e6b5'
    diagnostic: str = 'a5cf4a9bfaf266a2e1aa932289f038a6d343cb34e1c9d6226898edd332b6ec05'


@dataclass(frozen=True)
class RetryFiles:
    """Old audit_game reads only these files; no writes or implicit global override."""
    slot_directory: Path

    def __truediv__(self, name):
        require(name in {'result.json', 'intent.json', 'canonical.json', 'play-response.json', 'recovered-game.json'},
                'Original audit requested an unsupported retry file')
        return self.slot_directory / name if name == 'result.json' else self.slot_directory / 'retry' / name


def retry_authorization(directory, plan, pins, amendment_bytes, number=21):
    require(number in (21, 51), 'Only slots 21 and 51 have registered retry exceptions')
    folder, slot = directory / f'{number:04d}', plan['slots'][number-1]
    intent_sha, error_sha, amendment_sha = ((pins.original_intent, pins.original_error, pins.amendment)
                                          if number == 21 else
                                          (pins.rate_limit_intent, pins.rate_limit_error, pins.amendment_2))
    original.checked_file(folder / 'intent.json', intent_sha, f'Original slot-{number} intent')
    error = json.loads(original.checked_file(folder / 'api-error.json', error_sha, f'Original slot-{number} HTTP error'))
    require(error.get('status') == 422 and error.get('uncertain') is True, 'Original attempt is not the retained unknown HTTP 422')
    if number == 51:
        require(type(error.get('apiCode')) is int and error['apiCode'] == 407,
                'Slot 51 is not the retained server-reported rate limit')
    first_intent = read(folder / 'intent.json')
    require(first_intent.get('planId') == plan['planId'] and first_intent.get('slot') == slot
            and first_intent.get('sourceSha256') == plan['builds'][slot['arm']]['sourceSha256'], 'Original request binding changed')
    for name in ('play-response.json', 'recovered-game.json', 'canonical.json'):
        require(not (folder / name).exists(), 'Original unknown attempt acquired a primary outcome; do not select between attempts')
    auth_bytes = (folder / 'retry-authorization.json').read_bytes()
    auth = json.loads(auth_bytes)
    require(auth_bytes == encoded(auth).encode('utf-8'), 'Retry authorization bytes changed')
    unsigned = {key: value for key, value in auth.items() if key != 'authorizationId'}
    require(auth.get('authorizationId') == sha(encoded(unsigned)), 'Retry authorization identity changed')
    require(auth.get('schemaVersion') == 1 and auth.get('planId') == plan['planId'] and auth.get('slot') == slot
            and type(auth.get('requestAttempt')) is int and auth['requestAttempt'] == 2
            and auth.get('reasonCode') == 'EXCLUDE_UNKNOWN_HTTP_422'
            and auth.get('originalIntentSha256') == intent_sha
            and auth.get('originalErrorSha256') == error_sha
            and auth.get('amendmentFile') == 'retry-amendment.txt'
            and auth.get('amendmentSha256') == amendment_sha, 'Retry does not match its registered amendment')
    require((folder / 'retry-amendment.txt').read_bytes() == amendment_bytes, 'Retained amendment snapshot changed')
    intent = read(folder / 'retry/intent.json')
    require(type(intent.get('requestAttempt')) is int and intent['requestAttempt'] == 2
            and intent.get('retryAuthorizationSha256') == sha(auth_bytes), 'Retry intent is not bound to its authorization')
    require(intent.get('planId') == plan['planId'] and intent.get('slot') == slot
            and intent.get('sourceSha256') == first_intent['sourceSha256'], 'Retry source/map/seat request differs')
    return {'slot': number, 'requestAttempt': 2, 'authorizationId': auth['authorizationId'],
            'authorizationSha256': sha(auth_bytes), 'amendmentSha256': amendment_sha,
            'originalIntentSha256': intent_sha, 'originalErrorSha256': error_sha, 'originalHttpStatus': 422,
            'classification': 'unknownHttp422' if number == 21 else 'serverReportedRateLimit',
            'originalGameCreationUnknown': True, 'originalOutcomeUnknown': True,
            'samePlannedSourceSeedSeat': True, 'secondAttemptUsedWithoutOutcomeSelection': True}


def timestamp(value):
    require(isinstance(value, str), 'Missing recorded timestamp')
    parsed = datetime.fromisoformat(value.replace('Z', '+00:00'))
    require(parsed.tzinfo is not None, 'Recorded timestamp has no timezone')
    return parsed


def rate_limit_evidence(root, directory, plan, pins):
    evidence = json.loads(original.checked_file(root/RATE_LIMIT, pins.rate_limit, 'Rate-limit evidence'))
    error = read(directory/'0051/api-error.json')
    require(evidence.get('schemaVersion') == 1 and evidence.get('planId') == plan['planId']
            and evidence.get('slot') == 51 and evidence.get('requestAttempt') == 1
            and evidence.get('httpStatus') == 422 and evidence.get('errorSha256') == pins.rate_limit_error
            and evidence.get('intentSha256') == pins.rate_limit_intent
            and evidence.get('serverResponse') == {'id': 407, 'message': 'You reached the limit of plays for a period of time.'}
            and error.get('apiCode') == 407 and evidence.get('atUtc') == error.get('at')
            and evidence.get('chosenCooldownSeconds') == 600 and evidence.get('nextInterGamePauseSeconds') == 20,
            'Rate-limit evidence does not bind the retained slot-51 refusal')
    earliest = timestamp(evidence.get('noNewPlayBeforeUtc'))
    require(earliest == timestamp('2026-09-18T22:04:21+00:00')
            and (earliest-timestamp(evidence['atUtc'])).total_seconds() >= 600,
            'Registered rate-limit cooldown changed')
    require(timestamp(read(directory/'0051/retry/intent.json').get('startedAt')) >= earliest,
            'Slot-51 retry preceded the registered cooldown')
    delays = []
    for number in range(52, 81):
        start = timestamp(read(directory/f'{number:04d}'/'intent.json').get('startedAt'))
        finished = timestamp(read(directory/f'{number-1:04d}'/'result.json').get('completedAt'))
        delays.append((start-finished).total_seconds())
    require(min(delays) >= 20, 'A slot after 51 preceded its registered twenty-second pause')
    # Public whitelist only; never follow a path to the private HTTP response.
    return {'slot': 51, 'classification': 'serverReportedRateLimit', 'apiCode': 407,
            'httpStatus': 422, 'evidenceSha256': pins.rate_limit, 'noGameIdOrOutcomeReturned': True,
            'noNewPlayBeforeUtc': evidence['noNewPlayBeforeUtc'], 'chosenCooldownSeconds': 600,
            'nextInterGamePauseSeconds': 20, 'minimumRecordedInterGamePauseSeconds': min(delays),
            'cooldownAndSubsequentPausesVerified': True}


def audit(plan_path, root=ROOT, pins=Pins()):
    root, plan_path = Path(root).resolve(), Path(plan_path).resolve()
    original.within(root, plan_path, 'build/cg-boss')
    original.checked_file(Path(original.__file__), pins.original_auditor, 'Original auditor')
    original.checked_file(root / original.PROTOCOL, pins.protocol, 'Original prospective protocol')
    amendment_bytes = original.checked_file(root / AMENDMENT, pins.amendment, 'Registered amendment')
    amendment_2_bytes = original.checked_file(root / AMENDMENT_2, pins.amendment_2, 'Registered second amendment')
    prior = json.loads(original.checked_file(root / INTERRUPTED, pins.interruption, 'Original interrupted audit'))
    plan_bytes = original.checked_file(plan_path, pins.plan, 'Original 80-slot plan')
    plan = json.loads(plan_bytes)
    unsigned = {key: value for key, value in plan.items() if key != 'planId'}
    require(plan['planId'] == pins.plan_id == sha(encoded(unsigned)), 'Original plan identity changed')
    require(prior['complete'] is False and prior['status'] == 'INTERRUPTED' and prior['completedGames'] == 20
            and prior['planId'] == pins.plan_id and prior['planSha256'] == pins.plan
            and prior['protocolSha256'] == pins.protocol and prior['auditorSha256'] == pins.original_auditor
            and len(prior['games']) == 20, 'Prior interruption no longer identifies the original twenty games')
    # The plan's exact bytes and the original audit that checked its fixed balanced
    # schedule are pinned; still assert the sample dimensions at this boundary.
    slots = plan['slots']
    require(plan['design'] == 'matched' and plan['gamesPerBuild'] == 40 and plan['purpose'] == 'boss-screen'
            and plan['mapHashScheme'] == original.MAP_SCHEME and plan['bossAgentId'] == 6740207
            and plan['alpha'] == 0.05 and plan['promotion'] is False
            and len(slots) == 80 and [slot['slot'] for slot in slots] == list(range(1, 81)), 'Wrong fixed sample')
    missing = [slot['slot'] for slot in slots if not (plan_path.parent/f"{slot['slot']:04d}"/'result.json').is_file()]
    if missing:
        raise original.IncompleteError(f'Incomplete amended sample: {80-len(missing)}/80 results; no final evidence written')
    builds = original.audit_builds(root, plan, pins)
    submission = original.audit_submission(root, pins)
    diagnostic = json.loads(original.checked_file(root / DIAGNOSTIC, pins.diagnostic, 'Excluded technical game'))
    require(original.replay_identity(diagnostic)[0] == 904843581, 'Wrong excluded technical game')
    diagnostic_map = sha(original.view(diagnostic['frames'][0])['global']['graphics'])
    prefix = {f'{number:04d}/result.json': sha((plan_path.parent/f'{number:04d}'/'result.json').read_bytes())
              for number in range(1, 51)}
    require(sha(encoded(prefix)) == pins.first_fifty_results, 'Previously recorded fifty results changed')
    retries = [retry_authorization(plan_path.parent, plan, pins, amendment_bytes, 21),
               retry_authorization(plan_path.parent, plan, pins, amendment_2_bytes, 51)]
    rate_limit = rate_limit_evidence(root, plan_path.parent, plan, pins)
    expected_intents = {plan_path.parent/f'{number:04d}'/'intent.json' for number in range(1, 81)}
    expected_intents.update(plan_path.parent/f'{number:04d}/retry/intent.json' for number in (21, 51))
    require(set(plan_path.parent.rglob('intent.json')) == expected_intents, 'Expected exactly 82 retained play intents')
    games, identities = [], set()
    for slot in slots:
        folder = plan_path.parent/f"{slot['slot']:04d}"
        result = read(folder/'result.json')
        attempt = result.get('requestAttempt', 1)
        if slot['slot'] in (21, 51):
            require(type(attempt) is int and attempt == 2, 'Slots 21 and 51 must analyze only their authorized second attempts')
            files = RetryFiles(folder)
        else:
            require(type(attempt) is int and attempt == 1 and not (folder/'retry').exists()
                    and not (folder/'retry-authorization.json').exists(), 'An unregistered slot was retried')
            files = folder
        game, identity = original.audit_game(root, files, plan, slot)
        games.append(game); identities.add(identity)
    require(games[:20] == prior['games'], 'Previously observed twenty canonical outcomes changed')
    require(len(identities) == 1 and len({game['gameId'] for game in games}) == 80, 'Boss identity changed or game IDs repeat')
    require(all(game['gameId'] != 904843581 and game['mapHash'] != diagnostic_map for game in games),
            'Analyzed sample overlaps the separate post-plan technical diagnostic')
    excluded = set(plan['excludedMapHashes'])
    require(not {game['mapHash'] for game in games} & excluded, 'Analyzed games reused maps excluded by the original plan')
    pairs, hashes = [], set()
    for pair_id in range(1, 41):
        pair = {game['arm']: game for game in games if game['pair'] == pair_id}
        require(set(pair) == {'candidate', 'control'}, 'Incomplete matched pair')
        candidate, control = pair['candidate'], pair['control']
        require(candidate['mapHash'] == control['mapHash'] and candidate['seat'] == control['seat']
                and candidate['seed'] == control['seed'], 'Matched map/seat/seed differs')
        require(candidate['mapHash'] not in hashes, 'Different pairs reused a map')
        hashes.add(candidate['mapHash'])
        pairs.append({'pair': pair_id, 'mapHash': candidate['mapHash'], 'seat': candidate['seat'], 'seed': candidate['seed'],
                      'candidateGameId': candidate['gameId'], 'controlGameId': control['gameId'],
                      'candidate': candidate['result'], 'control': control['result']})
    require(sum(pair['seat'] == 0 for pair in pairs) == 20, 'Seat balance changed')
    aggregate = {}
    for arm in ('candidate', 'control'):
        values = [game for game in games if game['arm'] == arm]
        require(len(values) == 40, 'Expected forty analyzed games per strategy')
        counts = Counter(game['result'] for game in values)
        aggregate[arm] = {'games': 40, 'wins': counts['W'], 'losses': counts['L'], 'draws': counts['D'],
                          'winRate': counts['W']/40, 'halfWinScore': (counts['W']+counts['D']/2)/40,
                          'winRateExact95': original.clopper_pearson(counts['W'], 40),
                          'gamesWithTerminalFailureSummary': sum(bool(game['terminalFailures']) for game in values)}
    b = sum(pair['candidate'] == 'W' and pair['control'] != 'W' for pair in pairs)
    c = sum(pair['control'] == 'W' and pair['candidate'] != 'W' for pair in pairs)
    numerator, denominator = sum(math.comb(b+c, value) for value in range(b, b+c+1)), 2**(b+c)
    p = numerator/denominator
    for game in games:
        game['requestAttempt'] = 2 if game['slot'] in (21, 51) else 1
    return {'schemaVersion': 1, 'status': 'AMENDED_EXPLORATORY_COMPLETE', 'complete': True,
            'auditedAtUtc': datetime.now(timezone.utc).isoformat(),
            'planId': pins.plan_id, 'planSha256': pins.plan, 'originalProtocolSha256': pins.protocol,
            'originalAuditorSha256': pins.original_auditor, 'originalInterruptedEvidenceSha256': pins.interruption,
            'amendments': [{'path': AMENDMENT.as_posix(), 'sha256': pins.amendment, 'registeredAfterObservedGames': 20},
                           {'path': AMENDMENT_2.as_posix(), 'sha256': pins.amendment_2, 'registeredAfterObservedGames': 50}],
            'firstFiftyResultsSha256': pins.first_fifty_results,
            'auditorSha256': sha(Path(__file__).read_bytes()), 'analyzedGames': 80, 'gamesPerStrategy': 40,
            'matchedMapPairs': 40, 'samplePlayAttempts': 82, 'excludedOriginalAttempts': 2,
            'originalSlot21CreationUnknown': True, 'recoveries': retries, 'rateLimitRejection51': rate_limit,
            'boss': {'agentId': 6740207, 'nickname': 'Boss 4'},
            'mapHashScheme': original.MAP_SCHEME, 'excludedMapCount': len(excluded),
            'excludedMapSetSha256': sha(encoded(plan['excludedMapHashes'])),
            'additionalExcludedTechnicalGame': {'gameId': 904843581, 'rawSha256': pins.diagnostic, 'mapHash': diagnostic_map},
            'allMapsAbsentFromOriginalExclusionSet': True, 'builds': builds, 'originalSubmission': submission,
            'aggregate': aggregate, 'discordances': {'candidateOnlyWin': b, 'controlOnlyWin': c},
            'exploratoryExactTest': {'name': 'one-sided exact paired discordances; draws are non-wins',
                                     'numerator': numerator, 'denominator': denominator, 'p': p, 'alpha': 0.05,
                                     'interpretation': 'exploratory after two amendments following partial results'},
            'exploratoryDecision': 'evidence of improvement against this boss' if p <= 0.05 else 'inconclusive for improvement',
            'promotion': False, 'localStrengthGate': 'NOT_PASSED; not overridden by this external comparison',
            'executionIdentityIndependentlyVerified': False,
            'limitations': ['Two original HTTP 422 attempts excluded under explicit post-interim amendments: unknown at slot 21 and server-reported rate limit at slot 51.',
                           'No evidence establishes that the original failed request created no game or was independent of outcome.',
                           'Twenty and fifty outcomes were available before the respective amendments; timing gaps also limit interpretation.',
                           'Self-reported markers match retained source/configuration; remote execution identity is not independently proven.'],
            'games': games, 'pairs': pairs}


def export(plan, output, root=ROOT, pins=Pins()):
    result = audit(plan, root, pins)
    output = original.within(Path(root).resolve(), output, 'docs/evidence')
    require(output not in {(Path(root)/original.OUTPUT).resolve(), (Path(root)/INTERRUPTED).resolve()},
            'Amended evidence cannot overwrite original final/interrupted evidence')
    require(not output.exists(), 'Amended evidence already exists; preserve it')
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open('x', encoding='utf-8', newline='\n') as stream:
        stream.write(encoded(result))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--plan', type=Path, required=True)
    parser.add_argument('--out', type=Path, default=ROOT/OUTPUT)
    args = parser.parse_args()
    try:
        result = export(args.plan, args.out)
    except (ValueError, OSError, KeyError, TypeError, IndexError) as error:
        message = str(error) if isinstance(error, ValueError) else 'Incomplete or unsupported amended evidence; no final export'
        parser.exit(2, message+'\n')
    print(f"Amended exploratory audit passed: 80 analyzed games / 82 attempts; p={result['exploratoryExactTest']['p']:.8g}")


if __name__ == '__main__':
    main()
