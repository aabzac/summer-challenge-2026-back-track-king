"""Hermetic owner lookup binding and exact transport preservation tests."""
import copy
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import boss_batch as batch
import boss_owner_lookup as owner
from boss_api import Session
import test_boss_batch as fixtures


class OwnerLookupTest(unittest.TestCase):
    def setUp(self):
        self.f = fixtures.BossBatchTest(); self.f.setUp(); self.addCleanup(self.f.doCleanups)
        self.session = self.f.session
        folder = self.f.project / 'build/cg-boss/private/bootstrap'; folder.mkdir(parents=True)
        self.session.source_backup = folder / 'ide-source-backup.java'
        self.session.source_backup.write_bytes(b'class Original {}\n')
        self.session.source_sha256 = batch.digest(self.session.source_backup.read_bytes())
        self.bootstrap = folder / 'start-session.json'
        self.value = {'testType': 'PUZZLE', 'userId': 1172955, 'testSessionHandle': self.session.play_template[0],
                      'currentQuestion': {'answer': {'code': 'class Original {}\n', 'programmingLanguageId': 'Java'}}}
        self.bootstrap.write_text(batch.encoded(self.value), encoding='utf-8')
        self.sha = batch.digest(self.bootstrap.read_bytes())

    def test_owner_binding_hash_handle_and_original_source_validated(self):
        binding = owner.validate_owner(self.session, self.bootstrap, 1172955, self.sha)
        self.assertEqual(1172955, binding['ownerUserId'])
        self.assertNotIn(self.session.play_template[0], batch.encoded(binding))
        for user, sha in [(4, self.sha), (True, self.sha), (1172955, '0' * 64)]:
            with self.subTest(user=user, sha=sha), self.assertRaises(ValueError):
                owner.validate_owner(self.session, self.bootstrap, user, sha)
        for key, value in [('userId', 4), ('testSessionHandle', 'foreign-handle'), ('testType', 'CHALLENGE')]:
            changed = copy.deepcopy(self.value); changed[key] = value
            self.bootstrap.write_text(batch.encoded(changed), encoding='utf-8')
            with self.subTest(key=key), self.assertRaises(ValueError):
                owner.validate_owner(self.session, self.bootstrap, 1172955, batch.digest(self.bootstrap.read_bytes()))
        self.bootstrap.write_text(batch.encoded(self.value), encoding='utf-8')
        self.session.source_backup.write_bytes(b'changed')
        with self.assertRaises(ValueError): owner.validate_owner(self.session, self.bootstrap, 1172955, self.sha)
        self.f.transport.assert_not_called()

    def test_only_read_owner_argument_changes_play_object_and_bytes_preserved(self):
        self.f.transport.side_effect = None; self.f.transport.return_value = 'response'
        request = [self.session.play_template[0], {'code': 'class Frozen {}\r\n', 'programmingLanguageId': 'Java',
                    'multi': {'agentsIds': [-2, -1], 'playType': ['BOSS', 'IDE_CODE'],
                              'isSoloLeague': False, 'gameOptions': 'seed=1132660295\n'}}]
        original = copy.deepcopy(request)
        with owner.owner_transport(self.session, 1172955):
            self.assertEqual('response', batch.call_service_text(self.session, 'TestSession/play', request, timeout=120))
            args = [907056523, None]
            batch.call_service_text(self.session, 'gameResult/findByGameId', args, timeout=30)
            self.assertEqual([907056523, None], args)
        self.assertEqual(original, request)
        self.assertIs(request, self.f.transport.call_args_list[0].args[2])
        self.assertEqual(120, self.f.transport.call_args_list[0].kwargs['timeout'])
        self.assertEqual([907056523, 1172955], self.f.transport.call_args_list[1].args[2])
        self.assertIs(batch.call_service_text, self.f.transport)

    def test_foreign_identity_invalid_lookup_and_failure_restore_transport(self):
        other = Session(copy.deepcopy(self.session.play_template), 'other=cookie')
        with owner.owner_transport(self.session, 1172955):
            for args in ([907056523, 999], [True, None], [0, None], [907056523]):
                with self.subTest(args=args), self.assertRaises(ValueError):
                    batch.call_service_text(self.session, 'gameResult/findByGameId', args)
            with self.assertRaises(ValueError): batch.call_service_text(other, 'TestSession/play', ['unchanged'])
        self.f.transport.assert_not_called()
        self.f.transport.side_effect = RuntimeError('offline simulated transport error')
        with self.assertRaises(RuntimeError), owner.owner_transport(self.session, 1172955):
            batch.call_service_text(self.session, 'gameResult/findByGameId', [907056523, None])
        self.assertIs(batch.call_service_text, self.f.transport)

    def test_batch_and_cached_run_record_immutable_binding_and_source_pins(self):
        args, plan, _ = self.f.plan()
        args.bootstrap = self.bootstrap; args.bootstrap_sha256 = self.sha; args.user_id = 1172955
        for mode, module in [('batch', batch), ('cached', __import__('boss_cached'))]:
            args.mode = mode
            def execute(received):
                self.assertIs(args, received)
                batch.call_service_text(self.session, 'gameResult/findByGameId', [907056523, None])
            self.f.transport.side_effect = None
            with patch.object(module, 'run', side_effect=execute): owner.run(args)
            self.assertEqual([907056523, 1172955], self.f.transport.call_args.args[2])
        binding = batch.read_json(args.plan.parent / 'owner-lookup.json')
        self.assertEqual(plan['planId'], binding['planId'])
        self.assertEqual({'boss_owner_lookup.py', 'boss_batch.py', 'boss_cached.py', 'boss_api.py', 'replay_capture.py'},
                         set(binding['sourceSha256']))
        binding['ownerUserId'] = 5
        (args.plan.parent / 'owner-lookup.json').write_text(batch.encoded(binding), encoding='utf-8')
        self.f.transport.reset_mock()
        with self.assertRaises(ValueError): owner.run(args)
        self.f.transport.assert_not_called()


if __name__ == '__main__':
    unittest.main()
