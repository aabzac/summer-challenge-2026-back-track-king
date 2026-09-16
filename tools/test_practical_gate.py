import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from practical_gate import assess, read_report


def fixture(n, half_wins, a='candidate', b='selected', start=1):
    wins, draws = divmod(half_wins, 2)
    units = [2] * wins + [1] * draws + [0] * (n - wins - draws)
    pairs = []
    for i in range(n // 2):
        seed = start + i
        p = {'index': i, 'seed': seed, 'mapHash': hashlib.sha256(str(seed).encode()).hexdigest(),
             'width': 20, 'height': 10, 'townCount': 4, 'pairCount': 6}
        for side, field in enumerate(('aAsP0', 'aAsP1')):
            unit = units[2*i + side]
            p[field] = {'winner': -1 if unit == 1 else side if unit == 2 else 1-side,
                        'error': [None, None]}
        pairs.append(p)
    return {'a': a, 'b': b, 'paramsA': {'identity': a}, 'paramsB': {'identity': b},
            'games': n, 'winsA': wins, 'winsB': n-wins-draws, 'draws': draws,
            'gamesAasP0': n//2, 'gamesAasP1': n//2, 'errorsA': 0, 'errorsB': 0,
            'timingProfile': 'CG', 'firstSearchBudgetMs': 486, 'turnSearchBudgetMs': 36,
            'threads': 2, 'javaVersion': '17', 'pairs': pairs,
            'gamesPerMap': {p['mapHash']: 2 for p in pairs}}


class PracticalGateTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.index = 0

    def report(self, data):
        self.index += 1
        path = Path(self.temp.name) / f'{self.index}.json'
        path.write_text(json.dumps(data), encoding='utf-8')
        return read_report(path, data['games'])

    def test_direct_thresholds_and_draw_half_credit(self):
        screen = self.report(fixture(300, 359))
        self.assertEqual(assess(screen)['strengthStatus'], 'NEEDS_DIRECT_1000')
        direct = self.report(fixture(1000, 1100, start=1001))
        self.assertEqual(assess(screen, direct)['strengthStatus'], 'NEEDS_MATCHED_300_EACH')
        fail = self.report(fixture(1000, 1099, start=1001))
        result = assess(screen, fail)
        self.assertEqual(result['strengthStatus'], 'NOT_PASSED')
        self.assertEqual(result['commonOpponentStage'], 'SKIPPED_DIRECT_FAILURE')
        self.assertEqual(fail['score'], 0.5495)

    def test_screen_floor_stops_at_52_and_continues_immediately_above_it(self):
        for units in (300, 311, 312):
            screen = self.report(fixture(300, units))
            result = assess(screen)
            self.assertEqual(result['directStage'], 'STOP_SCREEN_300')
            self.assertEqual(result['strengthStatus'], 'NOT_PASSED')
            self.assertEqual(result['policy'], 'practical-300-1000-v2')
            with self.assertRaisesRegex(ValueError, 'at or below 52%'):
                assess(screen, self.report(fixture(1000, 1200, start=1001)))
        for units in (313, 318, 359):
            self.assertEqual(assess(self.report(fixture(300, units)))['strengthStatus'], 'NEEDS_DIRECT_1000')
        self.assertEqual(assess(self.report(fixture(300, 360)))['directStage'], 'PASS_300')

    def test_matched_exact_three_points_then_tie_in_fresh_full_stage(self):
        screen = self.report(fixture(300, 360))
        control = self.report(fixture(300, 360, 'selected', 'older', 1001))
        candidate = self.report(fixture(300, 378, 'candidate', 'older', 1001))
        result = assess(screen, style_candidate=candidate, style_control=control)
        self.assertEqual(result['strengthStatus'], 'PASS')
        self.assertAlmostEqual(sum(d['candidateMinusControl'] for d in result['matchedScreenDeltas'])/150, .03)
        candidate = self.report(fixture(300, 377, 'candidate', 'older', 1001))
        self.assertEqual(assess(screen, style_candidate=candidate, style_control=control)['strengthStatus'],
                         'NEEDS_MATCHED_1000_EACH')
        full_a = self.report(fixture(1000, 1200, 'candidate', 'older', 2001))
        full_b = self.report(fixture(1000, 1200, 'selected', 'older', 2001))
        self.assertEqual(assess(screen, None, candidate, control, full_a, full_b)['strengthStatus'], 'PASS')
        full_a = self.report(fixture(1000, 1199, 'candidate', 'older', 2001))
        self.assertEqual(assess(screen, None, candidate, control, full_a, full_b)['strengthStatus'], 'NOT_PASSED')

    def test_rejects_changed_conditions_params_and_reused_direct_maps(self):
        screen = self.report(fixture(300, 350))
        for field, value in [('threads', 3), ('paramsA', {'changed': True}), ('a', 'other')]:
            data = fixture(1000, 1200, start=1001)
            data[field] = value
            with self.assertRaises(ValueError):
                assess(screen, self.report(data))
        with self.assertRaisesRegex(ValueError, 'reuses'):
            assess(screen, self.report(fixture(1000, 1200)))

    def test_rejects_unmatched_maps_seeds_opponents_and_partial_reports(self):
        screen = self.report(fixture(300, 360))
        candidate = self.report(fixture(300, 380, 'candidate', 'older', 1001))
        for changed in ('maps', 'seed', 'opponent'):
            data = fixture(300, 360, 'selected', 'older', 2001 if changed == 'maps' else 1001)
            if changed == 'seed':
                data['pairs'][0]['seed'] += 1
            if changed == 'opponent':
                data['paramsB']['different'] = True
            with self.assertRaises(ValueError):
                assess(screen, style_candidate=candidate, style_control=self.report(data))
        with self.assertRaises(ValueError):
            assess(screen, style_candidate=candidate)

    def test_rejects_inconsistent_outcomes_duplicates_and_game_errors(self):
        for changed in ('total', 'duplicate', 'error', 'missing'):
            data = fixture(300, 360)
            if changed == 'total':
                data['winsA'] += 1
            elif changed == 'duplicate':
                data['pairs'][1]['mapHash'] = data['pairs'][0]['mapHash']
            elif changed == 'error':
                data['pairs'][0]['aAsP0']['error'][0] = 'timeout'
            else:
                data['pairs'].pop()
            with self.assertRaises(ValueError):
                self.report(data)

    def test_rejects_extra_continuations_after_early_pass(self):
        screen = self.report(fixture(300, 360))
        with self.assertRaises(ValueError):
            assess(screen, self.report(fixture(1000, 1200, start=1001)))
        incomplete = self.report(fixture(300, 359))
        with self.assertRaises(ValueError):
            assess(incomplete, style_candidate=self.report(fixture(300, 380, 'candidate', 'older', 1001)))


if __name__ == '__main__':
    unittest.main()
