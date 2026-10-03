"""Run an unchanged fixed boss batch with the official authenticated-owner replay lookup.

The public CodinGame client calls gameResult/findByGameId with [gameId, loggedUserId].
This adapter changes only that read argument. It never shares a replay, changes a play
payload, retries play, or relaxes canonical/artifact checks. The owner comes from a
private, hash-pinned Puzzle startTestSession response bound to the same session handle.
"""
from __future__ import annotations

import argparse
from contextlib import contextmanager
import math
from pathlib import Path

import boss_batch as batch


def validate_owner(session, bootstrap, owner_id, bootstrap_sha256):
    path = Path(bootstrap).resolve()
    if (not path.is_relative_to((batch.ROOT / 'build/cg-boss/private').resolve())
            or type(owner_id) is not int or owner_id <= 0
            or batch.digest(path.read_bytes()) != bootstrap_sha256):
        raise ValueError('A pinned private bootstrap and positive owner user ID are required')
    value = batch.read_json(path)
    if (value.get('userId') != owner_id or type(value.get('userId')) is not int
            or value.get('testSessionHandle') != session.play_template[0]
            or str(value.get('testType', '')).lower() != 'puzzle'):
        raise ValueError('Private puzzle bootstrap does not bind this owner and session handle')
    answer = value.get('currentQuestion', {}).get('answer', {})
    if (answer.get('programmingLanguageId') != 'Java' or not isinstance(answer.get('code'), str)
            or batch.digest(answer['code']) != session.source_sha256
            or session.source_backup is None
            or batch.digest(session.source_backup.read_bytes()) != session.source_sha256):
        raise ValueError('Current puzzle IDE source was not preserved exactly before play')
    return {'schemaVersion': 1, 'ownerUserId': owner_id, 'bootstrapSha256': bootstrap_sha256,
            'sessionBindingSha256': batch.session_binding(session),
            'sourceBackupSha256': session.source_sha256,
            'service': 'gameResult/findByGameId', 'change': 'null second argument to authenticated owner user ID',
            'playRequestsUnchanged': True, 'canonicalChecksUnchanged': True}


@contextmanager
def owner_transport(session, owner_id):
    """Scope interception to this validated session; preserve every other argument exactly."""
    original = batch.call_service_text
    binding = batch.session_binding(session)

    def call(active, service, args, timeout=30):
        if (batch.session_binding(active) != binding or active.cookie != session.cookie
                or active.headers != session.headers):
            raise ValueError('Owner lookup adapter cannot switch authenticated sessions')
        if service == 'gameResult/findByGameId':
            if (not isinstance(args, list) or len(args) != 2 or type(args[0]) is not int
                    or args[0] <= 0 or args[1] is not None):
                raise ValueError('Owner lookup adapter expects the fixed runner canonical lookup')
            return original(active, service, [args[0], owner_id], timeout=timeout)
        return original(active, service, args, timeout=timeout)

    batch.call_service_text = call
    try:
        yield
    finally:
        batch.call_service_text = original


def run(args):
    directory, plan = batch.load_plan(args.plan)
    session = batch.load_session(args.session)
    binding = validate_owner(session, args.bootstrap, args.user_id, args.bootstrap_sha256)
    if binding['sessionBindingSha256'] != plan.get('sessionBindingSha256'):
        raise ValueError('Owner bootstrap differs from the frozen plan session')
    source_root = Path(__file__).resolve().parent
    source_names = ('boss_owner_lookup.py', 'boss_batch.py', 'boss_cached.py', 'boss_api.py', 'replay_capture.py')
    binding.update(planId=plan['planId'], sourceSha256={name: batch.digest((source_root / name).read_bytes())
                                                     for name in source_names})
    # This independent write-once binding works for both batch and cached plans. It does
    # not rewrite an existing plan, intent, bootstrap, source backup, or policy tool.
    batch.write_once(directory / 'owner-lookup.json', binding)
    if args.mode == 'batch':
        execute = batch.run
    else:
        import boss_cached
        execute = boss_cached.run
    with owner_transport(session, args.user_id):
        return execute(args)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=('batch', 'cached'))
    parser.add_argument('--plan', type=Path, required=True)
    parser.add_argument('--session', type=Path, required=True)
    parser.add_argument('--bootstrap', type=Path, required=True)
    parser.add_argument('--bootstrap-sha256', required=True)
    parser.add_argument('--user-id', type=int, required=True)
    parser.add_argument('--timeout', type=float, default=120)
    parser.add_argument('--delay', type=float, default=20)
    args = parser.parse_args(argv)
    if (not math.isfinite(args.timeout) or not 1 <= args.timeout <= 600
            or not math.isfinite(args.delay) or not 0 <= args.delay <= 60):
        parser.error('Timeout must be 1..600 seconds and delay 0..60 seconds')
    try:
        run(args)
    except (ValueError, OSError, KeyError, TypeError, batch.ApiError) as error:
        # Do not include responses, session handles, source, or credentials in errors.
        parser.exit(1, str(error) + '\n' if isinstance(error, ValueError) else 'Boss owner lookup stopped; evidence retained.\n')


if __name__ == '__main__':
    main()
