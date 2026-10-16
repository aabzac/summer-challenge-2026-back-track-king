"""Offline-only checks for the single explicitly amended cached slot29."""
import copy
import json
from types import SimpleNamespace as NS
import unittest
from unittest.mock import patch

import boss_batch as batch
import boss_cached as cached
import boss_cached_resume as resume
from boss_api import ApiError
import test_boss_cached as fixtures


class CachedResumeTest(unittest.TestCase):
    def setUp(self):
        self.f = fixtures.BossCachedTest(); self.f.setUp(); self.addCleanup(self.f.doCleanups)
        self.root, self.plan, self.path = self.f.root, self.f.plan, self.f.path
        for number in range(1, 29):
            self.f.record(number, 'L' if number % 3 == 0 else 'W')
        self.stopped = self.path.parent / '0029'
        self.f.f.write_json(self.stopped / 'intent.json', {
            'planId': self.plan['planId'], 'slot': self.plan['slots'][28], 'requestAttempt': 1,
            'sourceSha256': self.plan['builds']['candidate']['sourceSha256'],
            'startedAt': '2026-10-15T22:11:43+00:00'})
        self.error = {'status': 422, 'apiCode': 407, 'uncertain': True, 'requestAttempt': 1,
                      'at': '2026-10-15T22:11:44+00:00', 'retryAfterSeconds': None}
        self.f.f.write_json(self.stopped / 'api-error.json', self.error)
        self.amendment = self.root / 'docs/followup.md'
        self.f.f.write(self.amendment, 'User explicitly authorizes one amended attempt29; retain unknown original.\n')
        self.binding = self.root / 'build/analysis/followup/resume-binding.json'
        self.prepare_args = NS(plan=self.path, amendment=self.amendment, out=self.binding)
        self.original_files = {p: p.read_bytes() for p in self.path.parent.rglob('*') if p.is_file()}
        session = self.f.session
        private = self.root / 'build/cg-boss/private/fresh'; private.mkdir(parents=True)
        session.source_backup = private / 'before-boss.java'
        session.source_backup.write_bytes(b'class PreservedSource {}\n')
        session.source_sha256 = batch.digest(session.source_backup.read_bytes())
        bootstrap = private / 'start-session.json'
        bootstrap.write_text(batch.encoded({'testType': 'PUZZLE', 'userId': 1172955,
            'testSessionHandle': session.play_template[0], 'currentQuestion': {'answer': {
                'code': session.source_backup.read_text(), 'programmingLanguageId': 'Java'}}}), encoding='utf-8')
        self.args = NS(binding=self.binding, session=private / 'session.json', bootstrap=bootstrap,
                       bootstrap_sha256=batch.digest(bootstrap.read_bytes()), user_id=1172955, timeout=120, delay=20)
        sleep_patch = patch.object(batch.time, 'sleep')
        self.sleep = sleep_patch.start(); self.addCleanup(sleep_patch.stop)

    def prepare(self):
        return resume.prepare(self.prepare_args)

    def authorize(self):
        self.prepare(); return resume.authorize(NS(binding=self.binding))

    def replies(self, start=29, end=40, *, changed=None):
        replies = []
        for number in range(start, end + 1):
            raw = self.f.replay(number)
            if changed and number == start:
                raw = changed(raw)
            replies += [json.dumps({'gameId': raw['gameId']}), json.dumps(raw)]
        self.f.transport.side_effect = replies

    def assert_originals(self):
        for path, content in self.original_files.items():
            self.assertEqual(content, path.read_bytes(), str(path))

    def test_prepare_is_offline_and_authorization_is_separate_hash_bound_once(self):
        binding = self.prepare()
        self.assertEqual(28, resume.assess_resume(self.binding)['candidate']['games'])
        self.assertFalse((self.stopped / 'retry-authorization.json').exists())
        self.assertFalse((self.stopped / 'retry').exists())
        self.assertEqual(self.amendment.read_bytes(), (self.binding.parent / 'resume-amendment.txt').read_bytes())
        self.assertEqual(29, binding['retrySlot'])
        resume.authorize(NS(binding=self.binding))
        with self.assertRaises(ValueError): resume.authorize(NS(binding=self.binding))
        self.assert_originals(); self.f.transport.assert_not_called()

    def test_only_original_422_407_unknown_with_exact28_prefix_is_eligible(self):
        for changes in ({'status': 503}, {'apiCode': 408}, {'uncertain': False}, {'gameId': 123}, {'requestAttempt': 2}):
            self.f.f.write_json(self.stopped / 'api-error.json', {**self.error, **changes})
            with self.subTest(changes=changes), self.assertRaises(ValueError): self.prepare()
        self.f.f.write_json(self.stopped / 'api-error.json', self.error)
        for name in ('play-response.json', 'recovered-game.json', 'canonical.json'):
            path = self.stopped / name; path.write_text('{"gameId": 123}', encoding='utf-8')
            with self.subTest(name=name), self.assertRaises(ValueError): self.prepare()
            path.unlink()
        self.f.transport.assert_not_called()

    def test_retained_raw_and_plan_changes_block_before_network(self):
        self.authorize()
        for path in (self.path, self.path.parent / '0001/result.json', self.path.parent / '0001/canonical.json',
                     self.root / self.plan['cache']['games'][0]['files'][0]['path']):
            content = path.read_bytes(); path.write_bytes(content + b'\n')
            with self.subTest(path=path.name), self.assertRaises(ValueError): resume.run(self.args)
            path.write_bytes(content)
        self.f.transport.assert_not_called()

    def test_exact12_requests_preserve_payloads_outcomes_and_owner_lookup_then_complete_resume_is_read_only(self):
        self.authorize(); self.replies()
        original_check, original_play, original_transport = batch.check_sample, batch.play_arguments, batch.call_service_text
        result = resume.run(self.args)
        calls = self.f.transport.call_args_list
        plays = [c for c in calls if c.args[1] == 'TestSession/play']
        self.assertEqual(12, len(plays))
        for number, call in zip(range(29, 41), plays):
            expected = original_play(self.f.session, self.plan['slots'][number - 1],
                                     self.plan['builds']['candidate'], self.plan['requestBossAgentId'])
            self.assertEqual(expected, call.args[2])
        self.assertTrue(all(c.args[2][1] == 1172955 for c in calls if c.args[1] == 'gameResult/findByGameId'))
        self.assertEqual([20] * 11, [c.args[0] for c in self.sleep.call_args_list])
        self.assertEqual(2, batch.read_json(self.stopped / 'result.json')['requestAttempt'])
        self.assertTrue(result['complete']); self.assertTrue(result['readinessPassed'])
        self.assertEqual(1, result['authorizedRetries']); self.assertEqual(41, result['attemptedRequests'])
        self.assertTrue(result['originalCreationRemainsUnknown']); self.assertFalse(result['promotion'])
        self.assert_originals()
        self.assertIs(batch.check_sample, original_check); self.assertIs(batch.play_arguments, original_play)
        self.assertIs(batch.call_service_text, original_transport)
        self.f.transport.reset_mock(); resume.run(self.args); self.f.transport.assert_not_called()

    def test_second_refusal_never_replays_and_cannot_authorize_third_attempt(self):
        self.authorize()
        self.f.transport.side_effect = ApiError('Offline quota refusal', status=422, uncertain=True,
                                                response_bytes=b'{"id":407,"message":"offline"}')
        original_check, original_play = batch.check_sample, batch.play_arguments
        with self.assertRaises(ValueError): resume.run(self.args)
        self.assertEqual(['TestSession/play'], [c.args[1] for c in self.f.transport.call_args_list])
        self.assertTrue((self.stopped / 'retry/intent.json').exists())
        self.assertFalse((self.path.parent / '0030/intent.json').exists())
        self.assertIs(batch.check_sample, original_check); self.assertIs(batch.play_arguments, original_play)
        self.f.transport.reset_mock()
        with self.assertRaisesRegex(ValueError, 'Unresolved play intent'): resume.run(self.args)
        with self.assertRaises(ValueError): resume.authorize(NS(binding=self.binding))
        self.f.transport.assert_not_called(); self.assert_originals()

    def test_second_attempt_lookup_resumes_only_lookup_before_remaining_slots(self):
        self.authorize()
        self.f.transport.side_effect = [json.dumps({'gameId': 20029}), 'null']
        with self.assertRaisesRegex(ValueError, 'lookup is not ready'): resume.run(self.args)
        self.f.transport.reset_mock()
        replies = [json.dumps(self.f.replay(29))]
        for number in range(30, 41): replies += [json.dumps({'gameId': 20000 + number}), json.dumps(self.f.replay(number))]
        self.f.transport.side_effect = replies
        result = resume.run(self.args)
        self.assertTrue(result['complete'])
        calls = self.f.transport.call_args_list
        self.assertEqual('gameResult/findByGameId', calls[0].args[1])
        self.assertEqual(11, sum(c.args[1] == 'TestSession/play' for c in calls))
        self.assert_originals()

    def test_recovery_binds_real_second_game_id_without_another_play(self):
        self.authorize()
        self.f.transport.side_effect = ApiError('Offline uncertain response', status=422, uncertain=True)
        with self.assertRaises(ValueError): resume.run(self.args)
        self.f.transport.reset_mock()
        batch.recover(NS(plan=self.path, slot=29, game_id=20029))
        self.f.transport.assert_not_called()
        replies = [json.dumps(self.f.replay(29))]
        for number in range(30, 41): replies += [json.dumps({'gameId': 20000 + number}), json.dumps(self.f.replay(number))]
        self.f.transport.side_effect = replies
        self.assertTrue(resume.run(self.args)['complete'])
        self.assertEqual(11, sum(c.args[1] == 'TestSession/play' for c in self.f.transport.call_args_list))

    def test_wrong_map_and_technical_failure_are_retained_then_stop_before_next_play(self):
        self.authorize()
        raw = self.f.replay(29, summary='Offline explicit technical failure', graphics='wrong-map')
        self.f.transport.side_effect = [json.dumps({'gameId': 20029}), json.dumps(raw)]
        with self.assertRaisesRegex(ValueError, 'map/seed/seat'): resume.run(self.args)
        self.assertTrue((self.stopped / 'result.json').exists())
        self.assertFalse((self.path.parent / '0030/intent.json').exists())
        self.assertEqual(1, sum(c.args[1] == 'TestSession/play' for c in self.f.transport.call_args_list))
        self.f.transport.reset_mock()
        with self.assertRaises(ValueError): resume.run(self.args)
        self.f.transport.assert_not_called()

    def test_extra_retry_and_future_slot_evidence_are_rejected(self):
        self.authorize()
        extra = self.path.parent / '0030/retry-authorization.json'
        self.f.f.write_json(extra, {})
        with self.assertRaisesRegex(ValueError, 'Only slot29'): resume.run(self.args)
        extra.unlink()
        extra = self.path.parent / '0031/intent.json'; self.f.f.write_json(extra, {})
        with self.assertRaisesRegex(ValueError, 'beyond the next'): resume.run(self.args)
        self.f.transport.assert_not_called()

    def test_payload_guard_rejects_changed_seed_seat_build_boss_and_completed_slot(self):
        self.authorize()
        slot, build, boss = self.plan['slots'][28], self.plan['builds']['candidate'], self.plan['requestBossAgentId']
        with resume.guarded_batch(self.binding):
            for changed in ({**slot, 'seed': '999999'}, {**slot, 'seat': 1 - slot['seat']}, self.plan['slots'][0]):
                with self.subTest(changed=changed), self.assertRaisesRegex(ValueError, 'fixed remaining schedule'):
                    batch.play_arguments(self.f.session, changed, build, boss)
            with self.assertRaises(ValueError): batch.play_arguments(self.f.session, slot, {**build, 'buildId': 'changed'}, boss)
            with self.assertRaises(ValueError): batch.play_arguments(self.f.session, slot, build, boss + 1)
        self.f.transport.assert_not_called()

    def test_incomplete_or_duplicate_prefix_cannot_be_pinned_as_the_original28(self):
        path = self.path.parent / '0028/result.json'; content = path.read_bytes(); path.unlink()
        with self.assertRaisesRegex(ValueError, 'exactly 28'): self.prepare()
        path.write_bytes(content)
        record = batch.read_json(path); record['slot'] = self.plan['slots'][26]
        self.f.f.write_json(path, record)
        with self.assertRaisesRegex(ValueError, 'fixed plan'): self.prepare()
        self.f.transport.assert_not_called()

    def test_missing_authorization_changed_session_and_delay_are_rejected_without_play(self):
        self.prepare()
        with self.assertRaisesRegex(ValueError, 'authorization required'): resume.run(self.args)
        resume.authorize(NS(binding=self.binding))
        bad = copy.copy(self.args); bad.delay = 0
        with self.assertRaisesRegex(ValueError, '20-second'): resume.run(bad)
        self.f.session.play_template[1]['multi']['isSoloLeague'] = True
        with self.assertRaisesRegex(ValueError, 'session differs'): resume.run(self.args)
        self.f.transport.assert_not_called()


if __name__ == '__main__':
    unittest.main()
