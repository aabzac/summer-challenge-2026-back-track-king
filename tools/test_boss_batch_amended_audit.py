"""Focused synthetic authorization/binding tests; original auditor and live files untouched."""
from dataclasses import asdict, replace
from datetime import datetime, timedelta, timezone
import json
from pathlib import Path
import unittest

from diagnostics import boss_batch_audit as original
from diagnostics import boss_batch_amended_audit as amended
import test_boss_batch_audit as fixtures


class AmendedAuditTest(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.BossBatchAuditTest()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        f = self.fixture
        self.root, self.plan, self.path = f.root, f.plan, f.path
        prior_games = [original.audit_game(self.root, self.path.parent/f'{slot["slot"]:04d}', self.plan, slot)[0]
                       for slot in self.plan['slots'][:20]]
        old_sha = original.sha(Path(original.__file__).read_bytes())
        prior = {'complete': False, 'status': 'INTERRUPTED', 'completedGames': 20, 'games': prior_games,
                 'planId': self.plan['planId'], 'planSha256': f.pins.plan,
                 'protocolSha256': f.pins.protocol, 'auditorSha256': old_sha}
        f.write_json(amended.INTERRUPTED, prior)
        f.write(amended.AMENDMENT, 'Synthetic explicit amendment; original outcome remains unknown.\n')
        f.write(amended.AMENDMENT_2, 'Synthetic rate-limit cooldown amendment.\n')
        self.folder = self.path.parent/'0021'
        f.write_json(self.folder/'api-error.json', {'status': 422, 'uncertain': True, 'message': 'SENSITIVE-ERROR-NOT-FOR-EXPORT'})
        self.rate_folder = self.path.parent/'0051'
        f.write_json(self.rate_folder/'api-error.json', {'status': 422, 'uncertain': True, 'apiCode': 407,
                                                       'at': '2026-09-18T21:54:20.423603+00:00'})
        self.pins = amended.Pins(**asdict(f.pins),
                                 amendment=original.sha((self.root/amended.AMENDMENT).read_bytes()),
                                 amendment_2=original.sha((self.root/amended.AMENDMENT_2).read_bytes()),
                                 interruption=original.sha((self.root/amended.INTERRUPTED).read_bytes()),
                                 original_auditor=old_sha,
                                 original_intent=original.sha((self.folder/'intent.json').read_bytes()),
                                 original_error=original.sha((self.folder/'api-error.json').read_bytes()),
                                 rate_limit_intent=original.sha((self.rate_folder/'intent.json').read_bytes()),
                                 rate_limit_error=original.sha((self.rate_folder/'api-error.json').read_bytes()))
        self.bind_retry(21, amended.AMENDMENT, self.pins.original_intent, self.pins.original_error, self.pins.amendment)
        self.bind_retry(51, amended.AMENDMENT_2, self.pins.rate_limit_intent, self.pins.rate_limit_error, self.pins.amendment_2)
        rate_evidence = {'schemaVersion': 1, 'planId': self.plan['planId'], 'slot': 51, 'requestAttempt': 1,
                         'httpStatus': 422, 'errorSha256': self.pins.rate_limit_error,
                         'intentSha256': self.pins.rate_limit_intent,
                         'serverResponse': {'id': 407, 'message': 'You reached the limit of plays for a period of time.'},
                         'atUtc': '2026-09-18T21:54:20.423603+00:00', 'chosenCooldownSeconds': 600,
                         'nextInterGamePauseSeconds': 20, 'noNewPlayBeforeUtc': '2026-09-18T22:04:21+00:00'}
        f.write_json(amended.RATE_LIMIT, rate_evidence)
        beginning = datetime(2026, 9, 18, 22, 4, 22, tzinfo=timezone.utc)
        for number in range(51, 81):
            folder = self.path.parent/f'{number:04d}'
            intent_path = folder/'retry/intent.json' if number == 51 else folder/'intent.json'
            intent = original.read(intent_path)
            intent['startedAt'] = (beginning+timedelta(seconds=(number-51)*30)).isoformat()
            f.write_json(intent_path, intent)
            result = original.read(folder/'result.json')
            result['completedAt'] = (beginning+timedelta(seconds=(number-51)*30+10)).isoformat()
            f.write_json(folder/'result.json', result)
        prefix = {f'{number:04d}/result.json': original.sha((self.path.parent/f'{number:04d}'/'result.json').read_bytes())
                  for number in range(1, 51)}
        diagnostic = original.read(self.path.parent/'0080/canonical.json')
        diagnostic['gameId'] = 904843581
        diagnostic['frames'][0]['view'] = ' 0\n'+json.dumps({'global': {'graphics': 'excluded technical map'}})
        f.write_json(amended.DIAGNOSTIC, diagnostic)
        self.pins = replace(self.pins, diagnostic=original.sha((self.root/amended.DIAGNOSTIC).read_bytes()),
                            rate_limit=original.sha((self.root/amended.RATE_LIMIT).read_bytes()),
                            first_fifty_results=original.sha(original.encoded(prefix)))

    def bind_retry(self, number, amendment_path, intent_sha, error_sha, amendment_sha):
        f, folder = self.fixture, self.path.parent/f'{number:04d}'
        # Bind the already generated synthetic completed game to the second attempt.
        for name in ('intent.json', 'play-response.json', 'canonical.json'):
            f.write(folder/'retry'/name, (folder/name).read_text(encoding='utf-8'))
            if name != 'intent.json':
                (folder/name).unlink()
        auth = {'schemaVersion': 1, 'planId': self.plan['planId'], 'slot': self.plan['slots'][number-1],
                'requestAttempt': 2, 'authorizedAt': '2026-09-19T00:00:00+00:00', 'reasonCode': 'EXCLUDE_UNKNOWN_HTTP_422',
                'originalIntentSha256': intent_sha, 'originalErrorSha256': error_sha,
                'amendmentFile': 'retry-amendment.txt', 'amendmentSha256': amendment_sha}
        auth['authorizationId'] = original.sha(original.encoded(auth))
        f.write_json(folder/'retry-authorization.json', auth)
        f.write(folder/'retry-amendment.txt', (self.root/amendment_path).read_text(encoding='utf-8'))
        intent = original.read(folder/'retry/intent.json')
        intent.update(requestAttempt=2, retryAuthorizationSha256=original.sha((folder/'retry-authorization.json').read_bytes()))
        f.write_json(folder/'retry/intent.json', intent)
        result = original.read(folder/'result.json'); result['requestAttempt'] = 2
        f.write_json(folder/'result.json', result)

    def test_authorized_retry_uses_explicit_readonly_binding_and_separate_exploratory_evidence(self):
        evidence_before = (self.root/amended.INTERRUPTED).read_bytes()
        auditor_before = Path(original.__file__).read_bytes()
        result = amended.export(self.path, self.root/amended.OUTPUT, self.root, self.pins)
        self.assertEqual(80, result['analyzedGames'])
        self.assertEqual(82, result['samplePlayAttempts'])
        self.assertEqual(40, len(result['pairs']))
        self.assertEqual(2, result['games'][20]['requestAttempt'])
        self.assertEqual(2, result['games'][50]['requestAttempt'])
        self.assertEqual(2, sum(game['requestAttempt'] == 2 for game in result['games']))
        self.assertEqual(2, len(result['amendments']))
        self.assertEqual([21, 51], [recovery['slot'] for recovery in result['recoveries']])
        self.assertEqual('serverReportedRateLimit', result['rateLimitRejection51']['classification'])
        self.assertEqual(20, result['rateLimitRejection51']['minimumRecordedInterGamePauseSeconds'])
        self.assertTrue(result['originalSlot21CreationUnknown'])
        self.assertEqual(1/1024, result['exploratoryExactTest']['p'])
        self.assertFalse(result['promotion'])
        self.assertNotIn('exactTest', result)
        self.assertNotIn('decision', result)
        output = (self.root/amended.OUTPUT).read_text(encoding='utf-8')
        for secret in ('sessionBinding', 'SENSITIVE-ERROR', 'DO-NOT-EXPORT', str(self.root)):
            self.assertNotIn(secret, output)
        self.assertEqual(evidence_before, (self.root/amended.INTERRUPTED).read_bytes())
        self.assertEqual(auditor_before, Path(original.__file__).read_bytes())
        with self.assertRaises(ValueError):
            amended.export(self.path, self.root/amended.INTERRUPTED, self.root, self.pins)

    def test_authorization_snapshot_and_original_attempt_are_immutable(self):
        snapshot = self.folder/'retry-amendment.txt'
        saved = snapshot.read_bytes(); snapshot.write_bytes(saved+b'changed')
        with self.assertRaisesRegex(ValueError, 'amendment snapshot'):
            amended.audit(self.path, self.root, self.pins)
        snapshot.write_bytes(saved)
        error = self.folder/'api-error.json'; error.write_bytes(error.read_bytes()+b'\n')
        with self.assertRaisesRegex(ValueError, 'Original slot-21 HTTP error'):
            amended.audit(self.path, self.root, self.pins)

    def test_third_attempt_or_wrong_retry_binding_is_rejected(self):
        intent = self.folder/'retry/intent.json'
        saved = intent.read_bytes()
        wrong = original.read(intent); wrong['retryAuthorizationSha256'] = 'wrong'
        self.fixture.write_json(intent, wrong)
        with self.assertRaisesRegex(ValueError, 'bound to its authorization'):
            amended.audit(self.path, self.root, self.pins)
        intent.write_bytes(saved)
        self.fixture.write_json(self.folder/'retry-again/intent.json', original.read(intent))
        with self.assertRaisesRegex(ValueError, 'exactly 82'):
            amended.audit(self.path, self.root, self.pins)

    def test_original_fifty_results_remain_exactly_frozen(self):
        result = self.path.parent/'0050/result.json'
        result.write_bytes(result.read_bytes()+b'\n')
        with self.assertRaisesRegex(ValueError, 'fifty results changed'):
            amended.audit(self.path, self.root, self.pins)

    def test_rate_limit_retry_and_next_games_respect_recorded_cooldown(self):
        intent_path = self.rate_folder/'retry/intent.json'
        saved = intent_path.read_bytes()
        intent = original.read(intent_path); intent['startedAt'] = '2026-09-18T22:04:20+00:00'
        self.fixture.write_json(intent_path, intent)
        with self.assertRaisesRegex(ValueError, 'preceded the registered cooldown'):
            amended.audit(self.path, self.root, self.pins)
        intent_path.write_bytes(saved)
        next_path = self.path.parent/'0052/intent.json'
        intent = original.read(next_path); intent['startedAt'] = '2026-09-18T22:04:51+00:00'
        self.fixture.write_json(next_path, intent)
        with self.assertRaisesRegex(ValueError, 'twenty-second pause'):
            amended.audit(self.path, self.root, self.pins)

    def test_second_authorization_cannot_use_first_amendment(self):
        auth_path = self.rate_folder/'retry-authorization.json'
        auth = original.read(auth_path); auth['amendmentSha256'] = self.pins.amendment
        auth.pop('authorizationId'); auth['authorizationId'] = original.sha(original.encoded(auth))
        self.fixture.write_json(auth_path, auth)
        with self.assertRaisesRegex(ValueError, 'its registered amendment'):
            amended.audit(self.path, self.root, self.pins)

    def test_incomplete_amended_sample_produces_no_final_export(self):
        (self.path.parent/'0080/result.json').unlink()
        with self.assertRaises(original.IncompleteError):
            amended.export(self.path, self.root/amended.OUTPUT, self.root, self.pins)
        self.assertFalse((self.root/amended.OUTPUT).exists())


if __name__ == '__main__':
    unittest.main()
