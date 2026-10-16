"""Explicit one-attempt continuation of the interrupted 28/40 cached boss panel.

This separate adapter preserves the original plan, first 28 outcomes and rejected
slot-29 request. It permits exactly one separately journaled attempt after the
original HTTP 422/API 407, then slots 30..40 once. It never retries an unknown
second attempt. All play, recovery, artifact, owner and canonical operations use
the existing boss tools; cached map/seat/boss checks also run after each result.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import math
from pathlib import Path
from types import SimpleNamespace as NS

import boss_batch as batch
import boss_cached as cached
import boss_owner_lookup as owner

SCHEMA = 'explicit-cached-28-of-40-resume-v1'
PREFIX = 28
RETRY_SLOT = 29


def require(condition, message):
    if not condition:
        raise ValueError(message)


def pin(path):
    return cached.file_pin(path)


def _original(folder, plan):
    rejected = folder / f'{RETRY_SLOT:04d}'
    batch.unknown_422_original(rejected, plan, plan['slots'][RETRY_SLOT - 1])
    error = batch.read_json(rejected / 'api-error.json')
    require(type(error.get('apiCode')) is int and error['apiCode'] == 407
            and error.get('requestAttempt') == 1, 'Expected the original slot-29 quota refusal')


def _cached_integrity(folder, plan):
    """Same cached-control checks, with amendment policy handled separately below."""
    require(plan.get('mode') == cached.MODE and plan.get('policy') == cached.POLICY
            and plan.get('gamesPerBuild') == 40 and plan.get('mapHashScheme') == batch.MAP_SCHEME
            and set(plan['builds']) == {'candidate'}, 'Unsupported cached-control plan')
    cache = plan['cache']
    cached.verify_pin(cache['sourcePlan']); cached.verify_pin(cache['manifest'])
    if cache['evidence'] is not None:
        cached.verify_pin(cache['evidence'])
    for game in cache['games']:
        for value in game['files']:
            cached.verify_pin(value)
    require(cached.control_snapshot(cached.public_path(cache['sourcePlan']['path']), cache['arm']) == cache,
            'Cached control provenance changed')
    candidate = plan['builds']['candidate']
    cached.verify_pin(plan['candidateManifest'])
    require(batch.artifact(candidate['directory'], candidate['processCheck']) == candidate,
            'Candidate artifact/process check changed')
    expected = [{'slot': game['slot'], 'pair': game['slot'], 'arm': 'candidate',
                 'seed': game['seed'], 'seat': game['seat']} for game in cache['games']]
    require(plan['slots'] == expected and plan['bossAgentId'] == cache['boss']['agentId']
            and plan['design'] == 'independent' and plan['promotion'] is False,
            'Candidate schedule differs from the pinned control')
    require([slot['slot'] for slot in plan['slots']] == list(range(1, 41)), 'Unexpected slot numbering')


def _cached_records(folder, plan, records, check_sample=batch.check_sample):
    check_sample(plan, records)
    numbers = [r['slot']['slot'] for r in records]
    require(numbers == list(range(1, len(records) + 1)) and PREFIX <= len(records) <= 40,
            'Completed outcomes must be the retained prefix followed in exact order')
    old = plan['cache']['games']
    prior_ids = {game['gameId'] for game in old}
    for record in records:
        slot, value = record['slot'], record['outcome']
        reference = old[slot['slot'] - 1]
        require(all(value[key] == reference[key] for key in ('mapHash', 'seed', 'seat')),
                f"Slot {slot['slot']}: candidate map/seed/seat differs from cached control")
        require(cached.boss_identity(value) == plan['cache']['boss'], 'Cached boss/league identity changed')
        require(value['gameId'] not in prior_ids, 'A cached control game was reused as a candidate result')
        require(record['capture']['expectedBuildMatches'] is True, 'Candidate build attribution failed')
        attempt = record.get('requestAttempt', 1)
        require(attempt == (2 if slot['slot'] == RETRY_SLOT else 1), 'Unexpected completed request attempt')
        raw_path = cached.public_path(record['capture']['path'])
        canonical = batch.attempt_folder(folder / f"{slot['slot']:04d}", attempt) / 'canonical.json'
        require(canonical.read_bytes() == raw_path.read_bytes(), 'Candidate canonical differs from archived capture')
        require(not cached.failures(batch.read_json(raw_path)), 'Recorded technical game failure blocks continuation')
    # A stopped unresolved next request is recoverable by ID, never skippable.
    for number in range(len(records) + 2, 41):
        future = folder / f'{number:04d}'
        require(not future.exists() or not any(future.iterdir()), 'Evidence exists beyond the next unresolved slot')


def prepare(args):
    """Offline only: pin the unchanged prefix and a copy of the explicit amendment."""
    folder, plan = cached.load_cached(args.plan)
    _original(folder, plan)
    records = batch.result_records(folder, plan)
    require(len(records) == PREFIX, 'Preparation requires exactly 28 completed candidate games')
    _cached_records(folder, plan, records)
    out = cached.public_path(args.out)
    require(out.is_relative_to((batch.ROOT / 'build/analysis').resolve()), 'Binding must stay in public analysis evidence')
    amendment = cached.public_path(args.amendment).read_bytes()
    require(0 < len(amendment) <= 1024 * 1024 and amendment.strip(), 'A nonempty explicit amendment is required')
    amendment.decode('utf-8')
    copied = out.with_name('resume-amendment.txt')
    copied.parent.mkdir(parents=True, exist_ok=True)
    if copied.exists():
        require(copied.read_bytes() == amendment, 'Retained amendment differs')
    else:
        with copied.open('xb') as stream:
            stream.write(amendment)
    paths = {Path(args.plan).resolve(), folder / '0029/intent.json', folder / '0029/api-error.json'}
    for record in records:
        paths.update(p for p in (folder / f"{record['slot']['slot']:04d}").rglob('*') if p.is_file())
        paths.add(cached.public_path(record['capture']['path']))
    if (folder / 'owner-lookup.json').exists():
        paths.add(folder / 'owner-lookup.json')
    binding = {'schema': SCHEMA, 'plan': pin(args.plan), 'planId': plan['planId'],
               'retainedCompleted': PREFIX, 'retrySlot': RETRY_SLOT, 'retryAttempt': 2,
               'originalError': {'status': 422, 'apiCode': 407, 'uncertain': True},
               'amendment': pin(copied), 'retainedFiles': [pin(p) for p in sorted(paths)],
               'exploratoryOnly': True, 'promotion': False,
               'ordinaryDelaySeconds': 20,
               'pacing': 'Remaining global slots 69..80; the next stage enforces 300s before global 81 and 101.',
               'originalCreationRemainsUnknown': True, 'automaticRetry': False}
    binding['bindingId'] = batch.digest(batch.encoded(binding))
    batch.write_once(out, binding)
    print('Resume binding saved: 28 outcomes retained; no authorization or network operation performed.')
    return binding


def load_binding(path):
    binding = batch.read_json(cached.public_path(path))
    body = dict(binding); identity = body.pop('bindingId', None)
    require(binding.get('schema') == SCHEMA and batch.digest(batch.encoded(body)) == identity
            and binding.get('retainedCompleted') == PREFIX and binding.get('retrySlot') == RETRY_SLOT
            and binding.get('retryAttempt') == 2 and binding.get('ordinaryDelaySeconds') == 20
            and binding.get('originalError') == {'status': 422, 'apiCode': 407, 'uncertain': True}
            and binding.get('exploratoryOnly') is True and binding.get('promotion') is False
            and binding.get('originalCreationRemainsUnknown') is True and binding.get('automaticRetry') is False,
            'Resume binding is invalid or changed')
    cached.verify_pin(binding['plan']); cached.verify_pin(binding['amendment'])
    for value in binding['retainedFiles']:
        cached.verify_pin(value)
    folder, plan = batch.load_plan(cached.public_path(binding['plan']['path']))
    require(plan['planId'] == binding['planId'], 'Resume binding belongs to another plan')
    _cached_integrity(folder, plan)
    _original(folder, plan)
    target = folder / f'{RETRY_SLOT:04d}'
    authorizations = list(folder.rglob('retry-authorization.json'))
    require(authorizations in ([], [target / 'retry-authorization.json']), 'Only slot29 may have a second attempt')
    retry_dirs = [p for p in folder.rglob('retry') if p.is_dir()]
    require(not retry_dirs or retry_dirs == [target / 'retry'], 'Unexpected retry directory')
    if authorizations:
        authorization = batch.validate_retry_authorization(target, plan, plan['slots'][RETRY_SLOT - 1])
        require(authorization['amendmentSha256'] == binding['amendment']['sha256'], 'Retry uses another amendment')
    else:
        require(not retry_dirs and not (target / 'retry-amendment.txt').exists(), 'Incomplete retry authorization')
    for number in range(1, 41):
        if number != RETRY_SLOT:
            require(not (folder / f'{number:04d}/retry-amendment.txt').exists(), 'Unexpected extra retry amendment')
    return folder, plan, binding


def assess_resume(binding_path):
    folder, plan, binding = load_binding(binding_path)
    records = batch.result_records(folder, plan)
    _cached_records(folder, plan, records)
    candidate = cached.arm_summary(plan['builds']['candidate'], [r['outcome']['result'] for r in records])
    control = cached.arm_summary(plan['cache']['build'], [r['result'] for r in plan['cache']['games']])
    complete = len(records) == 40
    return {'schema': SCHEMA, 'bindingId': binding['bindingId'], 'planId': plan['planId'],
            'complete': complete, 'readinessPassed': complete, 'issues': [],
            'candidate': candidate, 'control': control,
            'pairs': [{'slot': r['slot']['slot'], 'mapHash': r['outcome']['mapHash'],
                       'seed': r['outcome']['seed'], 'seat': r['outcome']['seat'],
                       'candidateGameId': r['outcome']['gameId'],
                       'controlGameId': plan['cache']['games'][r['slot']['slot'] - 1]['gameId']}
                      for r in records],
            'bossStage': ('PASS' if candidate['halfWins'] >= control['halfWins'] else 'NOT_PASSED')
                         if complete else 'INCOMPLETE',
            'exploratoryOnly': True, 'promotion': False, 'originalCreationRemainsUnknown': True,
            'collectionDates': {'candidate': cached.collection_dates([r.get('completedAt') for r in records]),
                                'control': cached.collection_dates([r.get('completedAt') for r in plan['cache']['games']])},
            **batch.retry_metadata(folder, plan)}


def authorize(args):
    folder, plan, binding = load_binding(args.binding)
    require(assess_resume(args.binding)['candidate']['games'] == PREFIX, 'Only the original stopped prefix can authorize')
    batch.authorize_retry(NS(plan=cached.public_path(binding['plan']['path']), slot=RETRY_SLOT,
                             amendment=cached.public_path(binding['amendment']['path'])))
    return batch.validate_retry_authorization(folder / '0029', plan, plan['slots'][28])


@contextmanager
def guarded_batch(binding_path):
    """Add full cached checks to the original sequential attempt-aware batch runner."""
    folder, plan, binding = load_binding(binding_path)
    require((folder / '0029/retry-authorization.json').exists(), 'Explicit retry authorization required before run')
    original_check, original_play = batch.check_sample, batch.play_arguments
    require(not getattr(original_play, '_cached_resume_guard', False), 'Resume guard is already active')

    def check(actual, records):
        if actual.get('planId') == plan['planId']:
            require(actual == plan, 'Active plan changed')
            _cached_records(folder, plan, records, original_check)
        else:
            original_check(actual, records)

    def play(session, slot, build, boss):
        current_folder, current_plan, current_binding = load_binding(binding_path)
        require(current_folder == folder and current_plan == plan and current_binding == binding,
                'Resume binding changed during execution')
        number = slot.get('slot')
        require(type(number) is int and RETRY_SLOT <= number <= 40 and slot == plan['slots'][number - 1]
                and build == plan['builds']['candidate'] and boss == plan['requestBossAgentId'],
                'Play request differs from the fixed remaining schedule')
        records = batch.result_records(folder, plan)
        _cached_records(folder, plan, records, original_check)
        require(number == len(records) + 1, 'Cannot skip or repeat a completed slot')
        attempt = 2 if number == RETRY_SLOT else 1
        require(not (batch.attempt_folder(folder / f'{number:04d}', attempt) / 'intent.json').exists(),
                'An unresolved request must be recovered, never repeated')
        return original_play(session, slot, build, boss)

    play._cached_resume_guard = True
    batch.check_sample, batch.play_arguments = check, play
    try:
        yield
    finally:
        batch.check_sample, batch.play_arguments = original_check, original_play


def run(args):
    require(math.isfinite(args.timeout) and 1 <= args.timeout <= 600 and args.delay == 20,
            'Use the fixed 20-second ordinary delay and a bounded request timeout')
    folder, plan, binding = load_binding(args.binding)
    assess_resume(args.binding)
    session = batch.load_session(args.session)
    owner_binding = owner.validate_owner(session, args.bootstrap, args.user_id, args.bootstrap_sha256)
    require(owner_binding['sessionBindingSha256'] == plan['sessionBindingSha256'], 'Owner session differs from original plan')
    source_root = Path(__file__).resolve().parent
    owner_binding.update(planId=plan['planId'], resumeBindingId=binding['bindingId'],
                         sourceSha256={name: batch.digest((source_root / name).read_bytes()) for name in
                                       ('boss_cached_resume.py', 'boss_owner_lookup.py', 'boss_batch.py',
                                        'boss_cached.py', 'boss_api.py', 'replay_capture.py')})
    # Fresh live readback has a different bootstrap hash; preserve the old owner record.
    batch.write_once(cached.public_path(args.binding).with_name('resume-owner-lookup.json'), owner_binding)
    active = NS(plan=cached.public_path(binding['plan']['path']), session=args.session,
                timeout=args.timeout, delay=args.delay)
    with guarded_batch(args.binding), owner.owner_transport(session, args.user_id):
        batch.run(active)
    return assess_resume(args.binding)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command', required=True)
    create = commands.add_parser('prepare', help='Pin the stopped prefix and explicit amendment; no HTTP or authorization')
    create.add_argument('--plan', type=Path, required=True)
    create.add_argument('--amendment', type=Path, required=True)
    create.add_argument('--out', type=Path, required=True)
    create.set_defaults(function=prepare)
    for command, function in [('authorize', authorize), ('report', lambda a: assess_resume(a.binding)), ('run', run)]:
        sub = commands.add_parser(command)
        sub.add_argument('--binding', type=Path, required=True)
        sub.set_defaults(function=function)
        if command == 'run':
            sub.add_argument('--session', type=Path, required=True)
            sub.add_argument('--bootstrap', type=Path, required=True)
            sub.add_argument('--bootstrap-sha256', required=True)
            sub.add_argument('--user-id', type=int, required=True)
            sub.add_argument('--timeout', type=float, default=120)
            sub.add_argument('--delay', type=float, default=20)
    args = parser.parse_args()
    try:
        result = args.function(args)
        if args.command in ('report', 'run'):
            print(batch.encoded(result), end='')
    except (ValueError, OSError, KeyError, TypeError, IndexError, batch.ApiError) as error:
        parser.exit(1, str(error) + '\n' if isinstance(error, ValueError)
                    else 'Cached resume stopped; existing evidence retained, no automatic retry.\n')


if __name__ == '__main__':
    main()
