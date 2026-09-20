"""Offline threshold, futility, freshness and boss-integration checks for the v4 assessor."""
import hashlib
import io
import json
from pathlib import Path
import shutil
import unittest
from unittest.mock import patch
import uuid

import practical_gate_v4 as gate
import practical_gate as v2_gate
import boss_practical_gate as v3_gate


def local_fixture(n, half_wins, start=1, a='candidate', b='v08'):
    wins, draws = divmod(half_wins, 2)
    units = [2] * wins + [1] * draws + [0] * (n - wins - draws)
    pairs = []
    for i in range(n // 2):
        seed = start + i
        pair = {'index': i, 'seed': seed, 'mapHash': hashlib.sha256(str(seed).encode()).hexdigest(),
                'width': 20, 'height': 10, 'townCount': 4, 'pairCount': 6}
        for side, field in enumerate(('aAsP0', 'aAsP1')):
            unit = units[2 * i + side]
            pair[field] = {'winner': -1 if unit == 1 else side if unit == 2 else 1 - side,
                           'error': [None, None]}
        pairs.append(pair)
    return {'a': a, 'b': b, 'paramsA': {'FEATURE': 'true', 'NOISE': '0.1'},
            'paramsB': {'FEATURE': 'false', 'NOISE': '0.1'},
            'games': n, 'winsA': wins, 'winsB': n - wins - draws, 'draws': draws,
            'gamesAasP0': n // 2, 'gamesAasP1': n // 2, 'errorsA': 0, 'errorsB': 0,
            'timingProfile': 'CG', 'firstSearchBudgetMs': 486, 'turnSearchBudgetMs': 36,
            'threads': 2, 'javaVersion': '17', 'pairs': pairs,
            'gamesPerMap': {pair['mapHash']: 2 for pair in pairs}}


def cached_fixture(candidate_units=25, control_units=25, candidate_games=40):
    def arm(spec, feature, units, games, marker):
        wins, draws = divmod(units, 2)
        return {'spec': spec, 'params': {'FEATURE': feature, 'NOISE': '0.1'},
                'buildId': marker * 64, 'sourceSha256': marker * 64,
                'games': games, 'wins': wins, 'losses': games - wins - draws,
                'draws': draws, 'halfWins': units}
    complete = candidate_games == 40
    return {'policy': v3_gate.POLICY, 'planId': 'e' * 64, 'mapHashScheme': v3_gate.BOSS_MAP_HASH_SCHEME,
            'complete': complete, 'issues': [],
            'bossStage': ('PASS' if candidate_units >= control_units else 'NOT_PASSED') if complete else 'INCOMPLETE',
            'candidate': arm('candidate', 'true', candidate_units, candidate_games, 'a'),
            'control': arm('v08', 'false', control_units, 40, 'b'),
            'pairs': [{'mapHash': hashlib.sha256(f'cg-map-{i}'.encode()).hexdigest(),
                       'seed': str(90000 + i), 'seat': i % 2,
                       'candidateGameId': 10000 + i, 'controlGameId': 20000 + i}
                      for i in range(candidate_games)]}


# Disjoint fresh seed ranges for the three blocks.
S1, S2, S3 = 1, 1001, 5001


class PracticalGateV4Test(unittest.TestCase):
    def setUp(self):
        self.test_root = Path(__file__).resolve().parents[1] / 'build/test-practical-gate-v4'
        self.test_root.mkdir(parents=True, exist_ok=True)
        self.directory = self.test_root / ('case-' + uuid.uuid4().hex)
        self.directory.mkdir()
        self.addCleanup(self.cleanup)
        self.index = 0

    def cleanup(self):
        if self.directory.resolve().parent != self.test_root.resolve():
            raise AssertionError('Test cleanup escaped workspace directory')
        shutil.rmtree(self.directory)

    def report(self, fixture):
        self.index += 1
        path = self.directory / f'{self.index}.json'
        path.write_bytes(json.dumps(fixture).encode('utf-8'))
        return gate.read_report(path, fixture['games'])

    def b1(self, half): return self.report(local_fixture(300, half, start=S1))
    def b2(self, half): return self.report(local_fixture(200, half, start=S2))
    def b3(self, half): return self.report(local_fixture(500, half, start=S3))

    def test_futility_at_300_is_strictly_below_50_percent(self):
        stop = gate.assess(self.b1(299))  # 149.5% ... halfWins 299 < 300 games
        self.assertEqual('STOP_FUTILITY_300', stop['directStage'])
        self.assertEqual('NOT_PASSED', stop['strengthStatus'])
        self.assertEqual('NOT_STARTED', stop['bossUsage'])
        with self.assertRaisesRegex(ValueError, '300-game futility stop'):
            gate.assess(self.b1(299), self.b2(160))
        with self.assertRaisesRegex(ValueError, '300-game futility stop'):
            gate.assess(self.b1(299), boss=cached_fixture())
        # Exactly 50% (halfWins == games) survives and asks for block 2.
        even = gate.assess(self.b1(300))
        self.assertEqual('NEEDS_BLOCK2_500', even['directStage'])
        self.assertEqual('NEEDS_LOCAL_1000', even['strengthStatus'])

    def test_futility_at_500_pools_both_blocks(self):
        # Block1 at exactly 50% clears, block2 drags the pooled 500 below 50% -> stop.
        stop = gate.assess(self.b1(300), self.b2(150))  # pooled 450 / 500 games
        self.assertEqual('STOP_FUTILITY_500', stop['directStage'])
        self.assertEqual('NOT_PASSED', stop['strengthStatus'])
        self.assertEqual(2, len(stop['reports']))
        with self.assertRaisesRegex(ValueError, '500-game futility stop'):
            gate.assess(self.b1(300), self.b2(150), self.b3(400))
        # Pooled exactly 500 / 500 games (50%) survives and asks for block 3.
        cont = gate.assess(self.b1(320), self.b2(180))  # 500 / 500
        self.assertEqual('NEEDS_BLOCK3_1000', cont['directStage'])
        self.assertEqual('NEEDS_LOCAL_1000', cont['strengthStatus'])

    def test_final_gate_is_55_percent_over_pooled_1000(self):
        # 1100 / 2000 half-win units = exactly 55%.
        fail = gate.assess(self.b1(330), self.b2(210), self.b3(559))  # 1099 pooled
        self.assertEqual('NOT_PASSED', fail['directStage'])
        self.assertEqual('NOT_PASSED', fail['strengthStatus'])
        self.assertEqual(0.5495, round(fail['pooledScore'], 4))
        passed = gate.assess(self.b1(330), self.b2(210), self.b3(560))  # 1100 pooled
        self.assertEqual('PASS_1000', passed['directStage'])
        self.assertEqual('NEEDS_BOSS_CACHED_40', passed['strengthStatus'])
        self.assertFalse(passed['promotion'])

    def test_interim_looks_never_promote_early(self):
        # A perfect block 1 still requires the full 1000 before any pass.
        self.assertEqual('NEEDS_BLOCK2_500', gate.assess(self.b1(600))['directStage'])
        self.assertEqual('NEEDS_BLOCK3_1000', gate.assess(self.b1(600), self.b2(400))['directStage'])

    def test_boss_pass_requires_local_1000_pass_and_control_relative_score(self):
        args = (self.b1(330), self.b2(210), self.b3(560))
        self.assertEqual('PASS', gate.assess(*args, boss=cached_fixture(25, 25))['strengthStatus'])
        self.assertTrue(gate.assess(*args, boss=cached_fixture(6, 5))['promotion'])
        below = gate.assess(*args, boss=cached_fixture(78, 79))
        self.assertEqual('NOT_PASSED', below['strengthStatus'])
        self.assertFalse(below['promotion'])
        incomplete = gate.assess(*args, boss=cached_fixture(38, 0, candidate_games=19))
        self.assertEqual('NEEDS_BOSS_CACHED_40', incomplete['strengthStatus'])

    def test_boss_cannot_rescue_a_local_failure_but_is_retained(self):
        boss = cached_fixture(80, 0)
        fail = gate.assess(self.b1(330), self.b2(210), self.b3(559), boss)
        self.assertEqual('NOT_PASSED', fail['strengthStatus'])
        self.assertEqual('PASS', fail['bossStage'])
        self.assertEqual('RETAINED_NONPROMOTING_LOCAL_FAILURE', fail['bossUsage'])
        self.assertFalse(fail['promotion'])
        # A concurrent boss is retained pending while block 3 is still outstanding.
        pending = gate.assess(self.b1(320), self.b2(180), boss=boss)
        self.assertEqual('NEEDS_LOCAL_1000', pending['strengthStatus'])
        self.assertEqual('RETAINED_PENDING_LOCAL_1000', pending['bossUsage'])

    def test_reused_maps_across_blocks_are_rejected(self):
        with self.assertRaisesRegex(ValueError, 'reuses'):
            gate.assess(self.b1(320), self.report(local_fixture(200, 120, start=S1)))
        with self.assertRaisesRegex(ValueError, 'reuses'):
            gate.assess(self.b1(320), self.b2(180), self.report(local_fixture(500, 300, start=S1)))

    def test_changed_conditions_params_or_bots_are_rejected(self):
        block1 = self.b1(320)
        for field, value in [('threads', 3), ('javaVersion', '21'), ('paramsA', {'X': 'y'}),
                             ('paramsB', {'X': 'y'}), ('a', 'other'), ('b', 'v07'),
                             ('timingProfile', 'legacy'), ('turnSearchBudgetMs', 50)]:
            data = local_fixture(200, 120, start=S2)
            data[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                gate.assess(block1, self.report(data))

    def test_block_sizes_are_enforced(self):
        with self.assertRaisesRegex(ValueError, '300 games in block 1'):
            gate.assess(self.report(local_fixture(200, 120, start=S1)))
        with self.assertRaisesRegex(ValueError, '200 games in block 2'):
            gate.assess(self.b1(320), self.b3(300))
        with self.assertRaisesRegex(ValueError, '500 games in block 3'):
            gate.assess(self.b1(320), self.b2(180), self.report(local_fixture(400, 240, start=S3)))

    def test_policy_labels_are_distinct_from_v2_and_v3(self):
        self.assertEqual('practical-1000-futility-boss-cache-v4', gate.POLICY)
        self.assertEqual('practical-300-1000-v2', v2_gate.POLICY)
        self.assertEqual('practical-300-1000-boss-cache-v3', v3_gate.POLICY)

    def test_cli_writes_once_and_reuses_cached_boss_assessor(self):
        block1, block2, block3 = self.b1(330), self.b2(210), self.b3(560)
        assessor = unittest.mock.Mock(return_value=cached_fixture(25, 25))
        module = type('FakeCached', (), {'assess_cached': assessor})
        output = self.directory / 'decision.json'
        argv = ['--block1', block1['path'], '--block2', block2['path'], '--block3', block3['path'],
                '--boss-plan', 'plan.json', '--out', str(output)]
        with patch.dict('sys.modules', {'boss_cached': module}), patch('sys.stdout', new_callable=io.StringIO):
            gate.main(argv)
        assessor.assert_called_once_with(Path('plan.json'))
        saved = output.read_bytes()
        self.assertEqual('PASS', json.loads(saved)['strengthStatus'])
        with patch.dict('sys.modules', {'boss_cached': module}), patch('sys.stdout', new_callable=io.StringIO), \
                patch('sys.stderr', new_callable=io.StringIO), self.assertRaises(SystemExit):
            gate.main(argv)
        self.assertEqual(saved, output.read_bytes())


if __name__ == '__main__':
    unittest.main()
