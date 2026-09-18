"""Offline threshold, provenance and concurrent-stage checks for the v3 assessor."""
import copy
import hashlib
import io
import json
from pathlib import Path
import shutil
import unittest
from unittest.mock import patch
import uuid

import boss_practical_gate as gate
import practical_gate as previous_gate


def local_fixture(n, half_wins, start=1):
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
    return {'a': 'candidate', 'b': 'v07', 'paramsA': {'FEATURE': 'true', 'NOISE': '0.1'},
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
    return {'policy': gate.POLICY, 'planId': 'e' * 64, 'mapHashScheme': gate.BOSS_MAP_HASH_SCHEME,
            'complete': complete, 'issues': [],
            'bossStage': ('PASS' if candidate_units >= control_units else 'NOT_PASSED') if complete else 'INCOMPLETE',
            'candidate': arm('candidate', 'true', candidate_units, candidate_games, 'a'),
            'control': arm('v07', 'false', control_units, 40, 'b'),
            'pairs': [{'mapHash': hashlib.sha256(f'cg-map-{i}'.encode()).hexdigest(),
                       'seed': str(90000 + i), 'seat': i % 2,
                       'candidateGameId': 10000 + i, 'controlGameId': 20000 + i}
                      for i in range(candidate_games)]}


class BossPracticalGateTest(unittest.TestCase):
    def setUp(self):
        self.test_root = Path(__file__).resolve().parents[1] / 'build/test-boss-practical-gate'
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

    def screen(self, units=360):
        return self.report(local_fixture(300, units))

    def direct(self, units=1100):
        return self.report(local_fixture(1000, units, start=1001))

    def test_exact_screen_boundaries_and_no_continuation_at_floor(self):
        for units in (311, 312):
            with self.subTest(units=units):
                screen = self.screen(units)
                result = gate.assess(screen)
                self.assertEqual('NOT_PASSED', result['strengthStatus'])
                self.assertEqual('STOP_SCREEN_300', result['directStage'])
                with self.assertRaisesRegex(ValueError, 'at or below 52%'):
                    gate.assess(screen, boss=cached_fixture())
                with self.assertRaisesRegex(ValueError, 'at or below 52%'):
                    gate.assess(screen, self.direct())
        for units in (313, 359):
            self.assertEqual('NEEDS_DIRECT_1000', gate.assess(self.screen(units))['strengthStatus'])
        result = gate.assess(self.screen(360))
        self.assertEqual('PASS_300', result['directStage'])
        self.assertEqual('NEEDS_BOSS_CACHED_40', result['strengthStatus'])
        with self.assertRaisesRegex(ValueError, 'fast pass'):
            gate.assess(self.screen(360), self.direct())

    def test_direct_draw_half_credit_and_separate_1000_threshold(self):
        screen = self.screen(359)
        failure = self.direct(1099)
        self.assertEqual(0.5495, failure['score'])
        self.assertEqual('NOT_PASSED', gate.assess(screen, failure)['strengthStatus'])
        self.assertEqual('NEEDS_BOSS_CACHED_40', gate.assess(screen, self.direct(1100))['strengthStatus'])
        # A high screen is never pooled with an insufficient direct continuation.
        self.assertEqual('NOT_PASSED', gate.assess(screen, failure, cached_fixture(80, 0))['strengthStatus'])

    def test_cached_score_is_relative_to_control_not_a_fixed_win_count(self):
        screen = self.screen()
        for units in (5, 25, 53, 79):
            with self.subTest(units=units):
                self.assertEqual('PASS', gate.assess(screen, boss=cached_fixture(units, units))['strengthStatus'])
        self.assertEqual('PASS', gate.assess(screen, boss=cached_fixture(6, 5))['strengthStatus'])
        self.assertEqual('NOT_PASSED', gate.assess(screen, boss=cached_fixture(78, 79))['strengthStatus'])
        boss = cached_fixture(5, 5)
        boss['candidate'].update(wins=1, draws=3, losses=36)
        boss['control'].update(wins=2, draws=1, losses=37)
        result = gate.assess(screen, boss=boss)
        self.assertEqual('PASS', result['strengthStatus'])
        self.assertEqual(0, result['bossScoreDifference'])

    def test_completed_boss_cannot_bypass_missing_direct_continuation(self):
        boss = cached_fixture(80, 0)
        result = gate.assess(self.screen(330), boss=boss)
        self.assertEqual('NEEDS_DIRECT_1000', result['strengthStatus'])
        self.assertEqual('PASS', result['bossStage'])
        self.assertEqual('RETAINED_PENDING_DIRECT', result['bossUsage'])
        self.assertIs(boss, result['boss'])
        self.assertFalse(result['promotion'])

    def test_concurrent_boss_is_retained_after_local_failure(self):
        boss = cached_fixture(70, 20)
        result = gate.assess(self.screen(330), self.direct(1099), boss)
        self.assertEqual('NOT_PASSED', result['strengthStatus'])
        self.assertEqual('PASS', result['bossStage'])
        self.assertEqual('RETAINED_NONPROMOTING_DIRECT_FAILURE', result['bossUsage'])
        self.assertEqual(boss, result['boss'])
        self.assertEqual(2, len(result['reports']))

    def test_incomplete_boss_needs_remaining_fixed_sample_even_when_ahead(self):
        partial = cached_fixture(38, 0, candidate_games=19)
        self.assertEqual('NEEDS_BOSS_CACHED_40', gate.assess(self.screen(), boss=partial)['strengthStatus'])
        self.assertEqual('NEEDS_DIRECT_1000', gate.assess(self.screen(330), boss=partial)['strengthStatus'])
        self.assertEqual('PASS', gate.assess(self.screen(330), self.direct(), cached_fixture())['strengthStatus'])

    def test_changed_local_settings_identity_or_reused_maps_are_rejected(self):
        screen = self.screen(330)
        for field, value in [('threads', 3), ('javaVersion', '21'), ('paramsA', {'FEATURE': 'false'}),
                             ('paramsB', {'FEATURE': 'true'}), ('a', 'another'), ('b', 'v06')]:
            fixture = local_fixture(1000, 1100, start=1001)
            fixture[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                gate.assess(screen, self.report(fixture))
        with self.assertRaisesRegex(ValueError, 'reuses'):
            gate.assess(screen, self.report(local_fixture(1000, 1100)))

    def test_changed_boss_specs_or_full_parameters_are_rejected(self):
        screen = self.screen()
        for arm in ('candidate', 'control'):
            for field, value in [('spec', 'other'), ('params', {'FEATURE': 'true'}),
                                 ('sourceSha256', 'unknown'), ('buildId', '')]:
                boss = cached_fixture()
                boss[arm][field] = value
                with self.subTest(arm=arm, field=field), self.assertRaises(ValueError):
                    gate.assess(screen, boss=boss)

    def test_boss_pair_outcome_verdict_and_issue_checks(self):
        screen = self.screen()
        for mutation in ('duplicate_map', 'duplicate_game', 'seat', 'seed', 'missing_pair',
                         'control_size', 'half_wins', 'boolean', 'verdict', 'complete', 'issues', 'policy'):
            boss = cached_fixture()
            if mutation == 'duplicate_map': boss['pairs'][1]['mapHash'] = boss['pairs'][0]['mapHash']
            elif mutation == 'duplicate_game': boss['pairs'][0]['controlGameId'] = boss['pairs'][0]['candidateGameId']
            elif mutation == 'seat': boss['pairs'][0]['seat'] = True
            elif mutation == 'seed': boss['pairs'][0]['seed'] = None
            elif mutation == 'missing_pair': boss['pairs'].pop()
            elif mutation == 'control_size': boss['control'].update(games=39, losses=25)
            elif mutation == 'half_wins': boss['candidate']['halfWins'] += 1
            elif mutation == 'boolean': boss['candidate']['draws'] = True
            elif mutation == 'verdict': boss['bossStage'] = 'NOT_PASSED'
            elif mutation == 'complete': boss['complete'] = False
            elif mutation == 'issues': boss['issues'] = ['invalid attribution']
            else: boss['policy'] = previous_gate.POLICY
            with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                gate.assess(screen, boss=boss)

    def test_different_hash_schemes_are_explicitly_not_a_cross_domain_freshness_check(self):
        screen = self.screen()
        boss = cached_fixture()
        # Equal-looking strings from incomparable schemes do not establish overlap.
        boss['pairs'][0]['mapHash'] = next(iter(screen['maps']))
        result = gate.assess(screen, boss=boss)
        self.assertEqual('PASS', result['strengthStatus'])
        self.assertIn('not assessed', result['crossDomainFreshness'])
        self.assertIn('different hash schemes', result['crossDomainFreshness'])
        self.assertIn('not assessed', result['sourceIdentity'])
        self.assertEqual('practical-300-1000-v2', previous_gate.POLICY)

    def test_cli_uses_cached_read_only_assessor_and_refuses_overwrite(self):
        screen = self.screen()
        boss = cached_fixture()
        assessor = unittest.mock.Mock(return_value=boss)
        module = type('FakeCached', (), {'assess_cached': assessor})
        output = self.directory / 'decision.json'
        argv = ['--screen', screen['path'], '--boss-plan', 'saved-plan.json', '--out', str(output)]
        with patch.dict('sys.modules', {'boss_cached': module}), patch('sys.stdout', new_callable=io.StringIO):
            gate.main(argv)
        assessor.assert_called_once_with(Path('saved-plan.json'))
        saved = output.read_bytes()
        self.assertEqual('PASS', json.loads(saved)['strengthStatus'])
        with patch.dict('sys.modules', {'boss_cached': module}), patch('sys.stdout', new_callable=io.StringIO), \
                patch('sys.stderr', new_callable=io.StringIO), self.assertRaises(SystemExit):
            gate.main(argv)
        self.assertEqual(saved, output.read_bytes())


if __name__ == '__main__':
    unittest.main()
