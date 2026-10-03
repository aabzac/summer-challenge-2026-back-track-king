"""Offline original-request adoption checks. No HTTP or executable bot runs."""
import copy
from pathlib import Path
import shutil
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import boss_batch as batch
import boss_cached as cached
import test_boss_batch as fixtures


class BossAdoptionTest(unittest.TestCase):
    def setUp(self):
        self.f = fixtures.BossBatchTest()
        self.f.setUp()
        self.addCleanup(self.f.doCleanups)
        self.old_args, self.old, self.marker = self.f.plan(games=40, purpose='boss-screen')
        source_directory = self.f.project / 'build/cg-boss/original'
        source_directory.mkdir(parents=True)
        self.old_args.plan = source_directory / 'plan.json'
        self.f.session.play_template[1]['multi']['agentsIds'] = [-1, -2]
        self.old.update(requestBossAgentId=-2, sessionBindingSha256=batch.session_binding(self.f.session))
        self.save_plan(self.old_args.plan, self.old)
        self.original = self.old_args.plan.parent / '0001'
        self.original.mkdir()
        slot = self.old['slots'][0]
        raw = self.replay(slot, 1234, loss=True)
        for name, value in {
            'intent.json': {'planId': self.old['planId'], 'slot': slot, 'requestAttempt': 1,
                            'sourceSha256': self.old['builds']['candidate']['sourceSha256'],
                            'startedAt': '2026-10-03T00:00:00Z'},
            'play-response.json': {'gameId': 1234}, 'canonical.json': raw,
        }.items():
            (self.original / name).write_text(batch.encoded(value), encoding='utf-8')
        self.directory = self.f.project / 'build/cg-boss/amended'
        self.folder = self.directory / '0001'
        self.folder.mkdir(parents=True)
        for path in self.original.iterdir():
            shutil.copyfile(path, self.folder / path.name)
        self.plan = copy.deepcopy(self.old)
        self.plan.update(createdAt='2026-10-03T00:00:01Z', bossAgentId=6767608,
                         adoptedRequests=[{'slot': 1, 'gameId': 1234,
                             'reason': 'CORRECT_CANONICAL_BOSS_AGENT_ID',
                             'sourcePlan': self.pin(self.old_args.plan), 'sourcePlanId': self.old['planId'],
                             'files': {p.name: self.pin(p) for p in self.original.iterdir()}}])
        self.path = self.directory / 'plan.json'
        self.save_plan(self.path, self.plan)
        self.args = SimpleNamespace(plan=self.path, session=self.old_args.session, timeout=5, delay=0)
        mock = patch.object(cached, 'ROOT', self.f.project)
        mock.start(); self.addCleanup(mock.stop)

    def pin(self, path):
        return {'path': path.relative_to(self.f.project).as_posix(), 'sha256': batch.digest(path.read_bytes())}

    @staticmethod
    def save_plan(path, plan):
        body = dict(plan); body.pop('planId', None)
        plan['planId'] = batch.digest(batch.encoded(body))
        path.write_text(batch.encoded(plan), encoding='utf-8')

    def replay(self, slot, game_id, loss=False):
        winner = 1 - slot['seat'] if loss else slot['seat']
        raw = self.f.replay(slot, self.marker, game_id, graphics=f'map-{game_id}',
                            ranks=[0, 1] if winner == 0 else [1, 0])
        boss = next(agent for agent in raw['agents'] if agent['agentId'] == 6740207)
        boss['agentId'] = 6767608
        boss['arenaboss']['league'] = {'divisionCount': 6, 'divisionIndex': 3, 'divisionOffset': 0}
        return raw

    def finish(self):
        responses = []
        for number, slot in enumerate(self.plan['slots'][1:], 1235):
            responses.extend([batch.encoded({'gameId': number}), batch.encoded(self.replay(slot, number))])
        self.f.transport.side_effect = responses
        batch.run(self.args)

    def test_first_loss_retained_remaining39_only_and_cached_control_accepts(self):
        before = {p.name: p.read_bytes() for p in self.original.iterdir()}
        self.finish()
        self.assertEqual(78, self.f.transport.call_count)
        plays = [call for call in self.f.transport.call_args_list if call.args[1] == 'TestSession/play']
        self.assertEqual(39, len(plays))
        self.assertEqual(f"seed={self.plan['slots'][1]['seed']}\n", plays[0].args[2][1]['multi']['gameOptions'])
        result = batch.report(self.args)
        self.assertTrue(result['complete'])
        self.assertEqual(1, result['arms']['candidate']['losses'])
        self.assertEqual(40, result['completedGames'])
        self.assertEqual(0, result['authorizedRetries'])
        self.assertEqual([], result['excludedAttempts'])
        self.assertTrue(result['protocolAmendments'][0]['outcomeRetained'])
        for name, content in before.items():
            self.assertEqual(content, (self.original / name).read_bytes())
            self.assertEqual(content, (self.folder / name).read_bytes())
        snapshot = cached.control_snapshot(self.path, 'candidate')
        self.assertEqual('L', snapshot['games'][0]['result'])
        self.assertEqual(40, len(snapshot['games']))
        candidate, check, _ = self.f.build('current')
        new = cached.make_plan(SimpleNamespace(control_plan=self.path, control_arm='candidate',
                    candidate=candidate, candidate_check=check, session=self.old_args.session,
                    out=self.f.project / 'build/cg-boss/new-candidate'))
        target = self.f.project / 'build/cg-boss/new-candidate/plan.json'
        self.assertEqual(1, cached.assess_cached(target)['control']['losses'])
        self.assertEqual(new['cache']['games'][0]['gameId'], 1234)
        (self.original / 'canonical.json').write_bytes(before['canonical.json'] + b' ')
        with self.assertRaises(ValueError): cached.assess_cached(target)

    def test_original_copy_and_pin_tampering_rejected(self):
        for source in (self.original, self.folder):
            for name in ('intent.json', 'play-response.json', 'canonical.json'):
                path = source / name; before = path.read_bytes()
                path.write_bytes(before + b' ')
                with self.subTest(source=source.name, name=name), self.assertRaises(ValueError):
                    batch.load_plan(self.path)
                path.write_bytes(before)
        plan = copy.deepcopy(self.plan)
        plan['adoptedRequests'][0]['sourcePlan']['sha256'] = '0' * 64
        self.save_plan(self.path, plan)
        with self.assertRaises(ValueError): batch.load_plan(self.path)
        self.f.transport.assert_not_called()

    def test_changed_experiment_or_entry_even_with_valid_new_plan_hash_rejected(self):
        for field, value in [('sessionBindingSha256', 'f' * 64), ('requestBossAgentId', 6767608),
                             ('excludedMapHashes', []), ('scheduleSeed', 5), ('bossAgentId', 999)]:
            plan = copy.deepcopy(self.plan)
            if field == 'excludedMapHashes': value = ['a' * 64]
            plan[field] = value; self.save_plan(self.path, plan)
            with self.subTest(field=field), self.assertRaises(ValueError): batch.load_plan(self.path)
        for field, value in [('slot', 2), ('gameId', 8888), ('sourcePlanId', 'f' * 64)]:
            plan = copy.deepcopy(self.plan); plan['adoptedRequests'][0][field] = value
            self.save_plan(self.path, plan)
            with self.subTest(field=field), self.assertRaises(ValueError): batch.load_plan(self.path)
        plan = copy.deepcopy(self.plan); plan['slots'][1]['seed'] = '999'
        self.save_plan(self.path, plan)
        with self.assertRaises(ValueError): batch.load_plan(self.path)
        self.f.transport.assert_not_called()

    def test_missing_copy_cannot_create_another_first_request(self):
        (self.folder / 'intent.json').unlink()
        with self.assertRaises(OSError): batch.run(self.args)
        self.f.transport.assert_not_called()

    def test_other_original_requests_forbid_partial_history_adoption(self):
        other = self.old_args.plan.parent / '0002'; other.mkdir()
        (other / 'intent.json').write_text('{}', encoding='utf-8')
        with self.assertRaises(ValueError): batch.load_plan(self.path)
        self.f.transport.assert_not_called()

    def test_resigned_wrong_original_source_or_slot_or_game_rejected(self):
        for name, key, value in [('intent.json', 'sourceSha256', 'bad-source'),
                                 ('intent.json', 'slot', {**self.old['slots'][0], 'seed': '999'}),
                                 ('play-response.json', 'gameId', 4321)]:
            path = self.original / name; before = path.read_bytes()
            payload = batch.read_json(path); payload[key] = value
            path.write_text(batch.encoded(payload), encoding='utf-8')
            (self.folder / name).write_bytes(path.read_bytes())
            plan = copy.deepcopy(self.plan); plan['adoptedRequests'][0]['files'][name] = self.pin(path)
            self.save_plan(self.path, plan)
            with self.subTest(name=name, key=key), self.assertRaises(ValueError): batch.load_plan(self.path)
            path.write_bytes(before); (self.folder / name).write_bytes(before)
        self.f.transport.assert_not_called()


if __name__ == '__main__':
    unittest.main()
