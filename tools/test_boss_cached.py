"""Hermetic cached-control planning, readiness and resume checks; never HTTP or JVM games."""
import copy
from contextlib import redirect_stdout
import io
import json
from pathlib import Path
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import boss_batch as batch
import boss_cached as cached
from boss_api import ApiError, Session
from diagnostics import boss_batch_audit as original
import test_boss_batch_audit as fixtures
import test_boss_batch as batch_fixtures


class BossCachedTest(unittest.TestCase):
    def setUp(self):
        f = fixtures.BossBatchAuditTest()
        f.setUp()
        self.addCleanup(f.doCleanups)
        self.f, self.root = f, f.root
        self.stdout = redirect_stdout(io.StringIO())
        self.stdout.__enter__()
        self.addCleanup(self.stdout.__exit__, None, None, None)
        for module in (batch, cached):
            mock = patch.object(module, 'ROOT', self.root)
            mock.start(); self.addCleanup(mock.stop)
        # Extend this test's owned copies only; historical fixture modules stay unchanged.
        self.league = {'divisionCount': 6, 'divisionIndex': 3, 'divisionOffset': 0}
        for slot in f.plan['slots']:
            folder = f.path.parent/f'{slot["slot"]:04d}'
            record = batch.read_json(folder/'result.json')
            raw = batch.read_json(folder/'canonical.json')
            next(agent for agent in raw['agents'] if agent['agentId'] == 6740207)['arenaboss']['league'] = self.league
            content = batch.encoded(raw)
            f.write(folder/'canonical.json', content); f.write(record['capture']['path'], content)
            record['capture']['rawSha256'] = batch.digest(content)
            record['outcome'] = batch.outcome(raw, raw['gameId'], slot, 6740207)
            f.write_json(folder/'result.json', record)
        evidence = {'complete': True, 'status': 'AMENDED_EXPLORATORY_COMPLETE', 'planId': f.plan['planId'],
                    'planSha256': batch.digest(f.path.read_bytes()),
                    'games': [original.audit_game(self.root, f.path.parent/f'{slot["slot"]:04d}', f.plan, slot)[0]
                              for slot in f.plan['slots']]}
        evidence_path = f.write_json(cached.CONTROL_EVIDENCE, evidence)
        mock = patch.object(cached, 'CONTROL_EVIDENCE_SHA', batch.digest(evidence_path.read_bytes()))
        mock.start(); self.addCleanup(mock.stop)
        mock = patch.object(cached, 'INITIAL_CONTROL_PLAN_ID', f.plan['planId'])
        mock.start(); self.addCleanup(mock.stop)
        builder = batch_fixtures.BossBatchTest(); builder.project = self.root
        self.candidate, self.check, self.marker = builder.build('current')
        self.session = Session(['offline-session', {'programmingLanguageId': 'Java', 'code': '',
                               'multi': {'agentsIds': [-1, -2], 'playType': ['IDE_CODE', 'BOSS'],
                                         'isSoloLeague': False, 'gameOptions': None}}], 'offline=test')
        for name in ('load_session', 'call_service_text'):
            mock = patch.object(batch, name)
            active = mock.start(); self.addCleanup(mock.stop)
            if name == 'load_session':
                active.return_value = self.session
            else:
                self.transport = active
                active.side_effect = AssertionError('Unexpected HTTP in offline test')
        self.create = SimpleNamespace(control_plan=f.path, control_arm='control', candidate=self.candidate,
                                      candidate_check=self.check, session=self.root/'offline.json',
                                      out=self.root/'build/cg-boss/new-cached-panel')
        self.plan = cached.make_plan(self.create)
        self.path = self.create.out/'plan.json'
        self.args = SimpleNamespace(plan=self.path, session=self.create.session, timeout=5, delay=0)

    def replay(self, number, result='W', summary=None, graphics=None):
        slot = self.plan['slots'][number-1]
        source_pair = self.f.plan['slots'][self.plan['cache']['games'][number-1]['sourceSlot']-1]['pair']
        ranks = [0, 0] if result == 'D' else ([0, 1] if (result == 'W') == (slot['seat'] == 0) else [1, 0])
        raw = batch_fixtures.BossBatchTest.replay(slot, self.marker, 20000+number, graphics or f'map-{source_pair}', ranks)
        next(agent for agent in raw['agents'] if agent['agentId'] == 6740207)['arenaboss']['league'] = self.league
        if summary:
            raw['frames'][-1]['summary'] = summary
        return raw

    def record(self, number, result='W', summary=None, graphics=None):
        slot = self.plan['slots'][number-1]
        raw = self.replay(number, result, summary, graphics)
        folder = self.path.parent/f'{number:04d}'
        build = self.plan['builds']['candidate']
        self.f.write_json(folder/'intent.json', {'planId': self.plan['planId'], 'slot': slot,
                                               'sourceSha256': build['sourceSha256'], 'requestAttempt': 1})
        self.f.write_json(folder/'play-response.json', {'gameId': raw['gameId']})
        canonical = self.f.write_json(folder/'canonical.json', raw)
        capture = batch.save_capture({'kind': 'boss', 'content': canonical.read_text(encoding='utf-8'),
                                      'expectedBuildId': build['buildId']}, self.root)
        value = batch.outcome(raw, raw['gameId'], slot, self.plan['bossAgentId'])
        self.f.write_json(folder/'result.json', {'planId': self.plan['planId'], 'slot': slot,
                                               'capture': capture, 'outcome': value, 'requestAttempt': 1})

    def test_plan_has_only_forty_candidate_requests_and_all_cache_pins(self):
        self.transport.assert_not_called()
        self.assertEqual(40, len(self.plan['slots']))
        self.assertEqual({'candidate'}, {slot['arm'] for slot in self.plan['slots']})
        self.assertEqual(20, sum(slot['seat'] == 0 for slot in self.plan['slots']))
        self.assertEqual(40, len({game['mapHash'] for game in self.plan['cache']['games']}))
        self.assertTrue(all(len(game['files']) >= 5 for game in self.plan['cache']['games']))
        result = cached.assess_cached(self.path)
        self.assertEqual(cached.POLICY, result['policy'])
        self.assertEqual('INCOMPLETE', result['bossStage'])
        self.assertEqual([], result['issues'])
        self.assertEqual(40, result['control']['games'])
        self.assertEqual(0, result['candidate']['games'])
        self.assertNotIn('p', result)
        self.assertFalse(result['promotion'])

    def test_complete_half_win_tie_passes_without_statistical_claim(self):
        # The fixture control has20 wins.10 wins+20draws=20win-equivalent points.
        for number in range(1, 41):
            self.record(number, 'W' if number <= 10 else 'D' if number <= 30 else 'L')
        result = cached.assess_cached(self.path)
        self.assertTrue(result['complete'])
        self.assertEqual('PASS', result['bossStage'])
        self.assertEqual(40, result['candidate']['halfWins'])
        self.assertEqual(40, result['control']['halfWins'])
        self.assertEqual(40, len(result['pairs']))
        self.assertEqual(batch.read_json(self.candidate/'manifest.json')['params'], result['candidate']['params'])
        self.assertEqual('current', result['candidate']['spec'])
        self.assertNotIn('p', result)
        self.transport.assert_not_called()

    def test_completed_candidate_below_control_fails(self):
        for number in range(1, 41):
            self.record(number, 'W' if number <= 19 else 'L')
        result = cached.assess_cached(self.path)
        self.assertEqual('NOT_PASSED', result['bossStage'])
        self.assertTrue(result['complete'])
        self.assertEqual([], result['issues'])

    def test_cached_raw_result_or_candidate_artifact_changes_block_before_http(self):
        game = self.plan['cache']['games'][0]
        for pin in (game['files'][0], game['files'][-1], self.plan['candidateManifest']):
            path = self.root/pin['path']; before = path.read_bytes()
            path.write_bytes(before+b'\n')
            with self.subTest(path=path.name), self.assertRaises(ValueError):
                cached.run(self.args)
            path.write_bytes(before)
        self.transport.assert_not_called()

    def test_wrong_map_and_technical_failure_are_retained_and_block_resume(self):
        self.record(1, 'L', '$0 has not provided an input in time', 'unexpected map')
        result = cached.assess_cached(self.path)
        self.assertEqual('INCOMPLETE', result['bossStage'])
        self.assertEqual(1, result['candidate']['losses'])
        self.assertTrue(any('map/seed/seat' in issue for issue in result['issues']))
        self.assertTrue(any('technical' in issue for issue in result['issues']))
        with self.assertRaisesRegex(ValueError, 'blocks resume'):
            cached.run(self.args)
        self.assertTrue((self.path.parent/'0001/result.json').is_file())
        self.transport.assert_not_called()

    def test_unknown_play_is_never_repeated(self):
        self.transport.side_effect = ApiError('Offline unknown response', status=422, uncertain=True)
        with self.assertRaises(ValueError):
            cached.run(self.args)
        self.assertEqual(['TestSession/play'], [c.args[1] for c in self.transport.call_args_list])
        self.transport.reset_mock()
        with self.assertRaisesRegex(ValueError, 'Unresolved play intent'):
            cached.run(self.args)
        self.transport.assert_not_called()
        self.assertTrue((self.path.parent/'0001/intent.json').is_file())

    def test_canonical_lookup_retry_does_not_replay_and_stops_on_retained_failure(self):
        self.transport.side_effect = [json.dumps({'gameId': 20001}), 'null']
        with self.assertRaisesRegex(ValueError, 'lookup not ready'):
            cached.run(self.args)
        self.assertFalse((self.path.parent/'0001/canonical.json').exists())
        self.assertEqual(1, len(list((self.path.parent/'0001').glob('canonical-rejected-*'))))
        self.transport.reset_mock()
        self.transport.side_effect = [json.dumps(self.replay(1, 'L', '$0 has not provided an input in time'))]
        with self.assertRaisesRegex(ValueError, 'Candidate problem retained'):
            cached.run(self.args)
        self.assertEqual(['gameResult/findByGameId'], [c.args[1] for c in self.transport.call_args_list])
        self.assertTrue((self.path.parent/'0001/result.json').is_file())

    def test_completed_panel_resume_has_no_requests(self):
        for number in range(1, 41):
            self.record(number)
        result = cached.run(self.args)
        self.assertEqual('PASS', result['bossStage'])
        self.transport.assert_not_called()

    def test_wrong_boss_identity_is_not_accepted_as_same_cached_panel(self):
        self.record(1)
        folder = self.path.parent/'0001'
        record = batch.read_json(folder/'result.json')
        raw = batch.read_json(folder/'canonical.json')
        boss = next(agent for agent in raw['agents'] if agent['agentId'] == 6740207)
        boss['arenaboss']['nickname'] = 'Another league'
        content = batch.encoded(raw)
        self.f.write(folder/'canonical.json', content)
        self.f.write(record['capture']['path'], content)
        record['capture']['rawSha256'] = batch.digest(content)
        record['outcome'] = batch.outcome(raw, raw['gameId'], self.plan['slots'][0], 6740207)
        self.f.write_json(folder/'result.json', record)
        self.assertTrue(any('boss/league' in issue for issue in cached.assess_cached(self.path)['issues']))

    def test_explicit_runtime_flags_are_readiness_failures_without_summary(self):
        for flag in ('frame', 'agent'):
            raw = self.replay(1)
            if flag == 'frame':
                raw['frames'][-1]['error'] = 'Runtime failure'
            else:
                raw['agents'][0]['valid'] = False
            self.assertTrue(cached.failures(raw))

    def test_missing_or_malformed_boss_league_is_rejected(self):
        for league in (None, {}, {'divisionCount': True, 'divisionIndex': 3, 'divisionOffset': 0}):
            boss = {'agentId': 6740207, 'arenaboss': {'nickname': 'Boss 4', 'league': league}}
            with self.subTest(league=league), self.assertRaisesRegex(ValueError, 'league divisions'):
                cached.boss_identity({'boss': boss})

    def test_any_verified_forty_game_arm_can_be_the_next_control(self):
        with patch.object(cached, 'INITIAL_CONTROL_PLAN_ID', 'another registered plan'):
            snapshot = cached.control_snapshot(self.f.path, 'candidate')
        self.assertEqual('candidate', snapshot['arm'])
        self.assertEqual(self.f.plan['builds']['candidate']['strategy'], snapshot['build']['strategy'])
        self.assertIsNone(snapshot['evidence'])
        self.assertEqual(40, len(snapshot['games']))

    def test_complete_cached_candidate_can_become_control_with_ancestry_pinned(self):
        for number in range(1, 41):
            self.record(number)
        snapshot = cached.control_snapshot(self.path, 'candidate')
        self.assertEqual(self.plan['planId'], snapshot['sourcePlanId'])
        self.assertEqual(self.plan['builds']['candidate']['buildId'], snapshot['build']['buildId'])
        self.assertEqual(40, len(snapshot['games']))
        prior_pin = self.plan['cache']['games'][0]['files'][0]
        path = self.root/prior_pin['path']; path.write_bytes(path.read_bytes()+b'\n')
        with self.assertRaises(ValueError):
            cached.control_snapshot(self.path, 'candidate')


if __name__ == '__main__':
    unittest.main()
