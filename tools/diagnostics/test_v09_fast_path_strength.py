"""Synthetic-only checks of the fast-path-strength driver; no Java, bots or game execution."""
from copy import deepcopy
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import run_v09_fast_path_strength as driver


PARAMS_B = {'NOISE': '0.1', 'USE_BATCHED_REFRESH': 'true', 'USE_ROLLOUT_POLICY': 'true'}
PARAMS_A = dict(PARAMS_B, USE_FAST_CHEAPEST_PATH='true')


def synthetic_readiness():
    return {'complete': True, 'status': 'PASS', 'strengthReady': True, 'engineExact': 292,
            'parity': {'positions': 524, 'pass': True},
            'guards': {'ampleExact': True, 'turn40Target33': True, 'sixWinningClosures': True},
            'process': {'games': 8, 'pass': True, 'errors': 0, 'timeouts': 0, 'overruns': 0, 'missing': 0},
            'performance': {'equalWorkTimeReduction': 0.1, 'comparisonGain36': 0.1},
            'artifacts': {name: {'charactersUtf16': 99000} for name in
                          ('artifact', 'artifact-off', 'artifact-v09', 'default-artifact', 'guard-on', 'guard-off')}}


class DiagnosticReadinessTest(unittest.TestCase):
    def test_passing_readiness(self):
        driver.readiness_guards(synthetic_readiness())

    def test_incomplete_failed_or_blocked_readiness(self):
        for key, value in [('complete', False), ('status', 'NOT_READY'), ('strengthReady', False)]:
            with self.subTest(key=key):
                ready = synthetic_readiness()
                ready[key] = value
                with self.assertRaisesRegex(ValueError, 'readiness'):
                    driver.readiness_guards(ready)

    def test_missing_engine_replay_rejected(self):
        ready = synthetic_readiness()
        ready['engineExact'] = 291
        with self.assertRaisesRegex(ValueError, 'engine'):
            driver.readiness_guards(ready)

    def test_parity_count_and_failure_rejected(self):
        for key, value in [('positions', 523), ('pass', False)]:
            with self.subTest(key=key):
                ready = synthetic_readiness()
                ready['parity'][key] = value
                with self.assertRaisesRegex(ValueError, 'parity'):
                    driver.readiness_guards(ready)

    def test_each_guard_required(self):
        for key in synthetic_readiness()['guards']:
            with self.subTest(key=key):
                ready = synthetic_readiness()
                ready['guards'][key] = False
                with self.assertRaisesRegex(ValueError, 'guards'):
                    driver.readiness_guards(ready)

    def test_incomplete_or_failed_process_sample_rejected(self):
        for key, value in [('games', 7), ('pass', False), ('errors', 1), ('timeouts', 1),
                           ('overruns', 1), ('missing', 1)]:
            with self.subTest(key=key):
                ready = synthetic_readiness()
                ready['process'][key] = value
                with self.assertRaisesRegex(ValueError, 'process'):
                    driver.readiness_guards(ready)

    def test_speed_and_comparison_gains_must_both_be_strictly_positive(self):
        for key in ('equalWorkTimeReduction', 'comparisonGain36'):
            for value in (0, -0.01, float('inf'), float('nan'), True):
                with self.subTest(key=key, value=value):
                    ready = synthetic_readiness()
                    ready['performance'][key] = value
                    with self.assertRaisesRegex(ValueError, 'gains'):
                        driver.readiness_guards(ready)

    def test_all_six_artifacts_required(self):
        ready = synthetic_readiness()
        del ready['artifacts']['guard-on']
        with self.assertRaisesRegex(ValueError, 'six'):
            driver.readiness_guards(ready)

    def test_character_cap_including_guard_artifacts(self):
        for label in synthetic_readiness()['artifacts']:
            with self.subTest(label=label):
                ready = synthetic_readiness()
                ready['artifacts'][label]['charactersUtf16'] = 100001
                with self.assertRaisesRegex(ValueError, 'cap'):
                    driver.readiness_guards(ready)


def synthetic_report(index, wins, draws=0):
    name, games, count, offset = driver.schedule.BLOCKS[index]
    start = driver.BASE + offset
    pairs = []
    for pair_index in range(count):
        pair = {'index': pair_index, 'seed': start + pair_index,
                'mapHash': format(start + pair_index, '064x'),
                'width': 10, 'height': 10, 'townCount': 4, 'pairCount': 4}
        for seat, key in enumerate(('aAsP0', 'aAsP1')):
            ordinal = 2 * pair_index + seat
            winner = seat if ordinal < wins else -1 if ordinal < wins + draws else 1 - seat
            score = [5, 5] if winner == -1 else [6, 5] if winner == 0 else [5, 6]
            pair[key] = {'error': [None, None], 'winner': winner, 'score': score, 'turns': 10}
        pairs.append(pair)
    return {'a': driver.A, 'b': driver.B, 'paramsA': dict(PARAMS_A), 'paramsB': dict(PARAMS_B),
            'threads': 2, 'games': games, 'baseSeed': start, 'timingProfile': 'CG',
            'firstSearchBudgetMs': 486, 'turnSearchBudgetMs': 36,
            'firstSearchBudgetMsA': 486, 'firstSearchBudgetMsB': 486,
            'turnSearchBudgetMsA': 36, 'turnSearchBudgetMsB': 36,
            'javaVersion': '17.synthetic', 'errorsA': 0, 'errorsB': 0,
            'winsA': wins, 'winsB': games - wins - draws, 'draws': draws,
            'gamesAasP0': count, 'gamesAasP1': count,
            'poolId': driver.NAME + '-' + name, 'poolPurpose': 'confirmation',
            'poolSha256': 'pool-pin', 'excludedReports': {}, 'pairs': pairs,
            'gamesPerMap': dict.fromkeys((p['mapHash'] for p in pairs), 2)}


class ReportGuardsTest(unittest.TestCase):
    def setUp(self):
        self.raw = synthetic_report(0, 150)
        self.raw['excludedReports'] = {driver.rel(driver.RUN / 'excluded-maps.json'): 'exclusion-pin'}
        self.report = {'data': self.raw, 'maps': dict.fromkeys(self.raw['gamesPerMap'])}
        self.frozen = {'artifacts': {'A': {'params': dict(PARAMS_A)}, 'B': {'params': dict(PARAMS_B)}}}
        self.preview = {'javaVersion': '17.synthetic', 'pools': [{'maps': [
            {'seed': p['seed'], 'coreMapHash': p['mapHash']} for p in self.raw['pairs']]}]}
        self.excluded = {'gamesPerMap': {'f' * 64: 1}}

    def read_input(self, path):
        if Path(path).name == 'pool-preflight.json':
            return self.preview
        if Path(path).name == 'excluded-maps.json':
            return self.excluded
        self.fail('Unexpected fixture read: ' + str(path))

    def run_inspect(self):
        with patch.object(driver, 'read', side_effect=self.read_input), \
                patch.object(driver, 'sha', side_effect=lambda p:
                             'exclusion-pin' if Path(p).name == 'excluded-maps.json' else 'pool-pin'):
            driver.inspect(self.report, 0, self.frozen)

    def test_valid_matched_report(self):
        self.run_inspect()

    def test_parameter_delta_rejected(self):
        self.raw['paramsA']['NOISE'] = '0'
        with self.assertRaisesRegex(ValueError, 'Parameters'):
            self.run_inspect()

    def test_wrong_candidate_spec_rejected(self):
        self.raw['a'] = driver.B
        with self.assertRaisesRegex(ValueError, 'block settings'):
            self.run_inspect()

    def test_wrong_worker_count_rejected(self):
        self.raw['threads'] = 4
        with self.assertRaisesRegex(ValueError, 'block settings'):
            self.run_inspect()

    def test_incomplete_block_rejected(self):
        self.raw['games'] = 298
        with self.assertRaisesRegex(ValueError, 'block settings'):
            self.run_inspect()

    def test_budget_guard_both_arms_and_phases(self):
        for field in driver.gate.ARM_BUDGET_FIELDS:
            with self.subTest(field=field):
                original = self.raw[field]
                self.raw[field] += 1
                with self.assertRaisesRegex(ValueError, 'actual budgets'):
                    self.run_inspect()
                self.raw[field] = original

    def test_unregistered_seed_rejected(self):
        self.raw['pairs'][0]['seed'] += 1
        with self.assertRaisesRegex(ValueError, 'Unregistered'):
            self.run_inspect()

    def test_unregistered_map_rejected(self):
        self.raw['pairs'][0]['mapHash'] = 'a' * 64
        with self.assertRaisesRegex(ValueError, 'Unregistered'):
            self.run_inspect()

    def test_pool_digest_rejected(self):
        self.raw['poolSha256'] = 'changed'
        with self.assertRaisesRegex(ValueError, 'Pool identity'):
            self.run_inspect()

    def test_pool_id_rejected(self):
        self.raw['poolId'] = 'different-pool'
        with self.assertRaisesRegex(ValueError, 'pool declaration'):
            self.run_inspect()

    def test_nonconfirmation_pool_rejected(self):
        self.raw['poolPurpose'] = 'development'
        with self.assertRaisesRegex(ValueError, 'pool declaration'):
            self.run_inspect()

    def test_java_version_rejected(self):
        self.raw['javaVersion'] = 'different-runtime'
        with self.assertRaisesRegex(ValueError, 'Java version'):
            self.run_inspect()

    def test_exclusion_digest_rejected(self):
        self.raw['excludedReports'][driver.rel(driver.RUN / 'excluded-maps.json')] = 'changed'
        with self.assertRaisesRegex(ValueError, 'Exclusions'):
            self.run_inspect()

    def test_prior_map_overlap_rejected(self):
        self.excluded['gamesPerMap'][self.raw['pairs'][0]['mapHash']] = 1
        with self.assertRaisesRegex(ValueError, 'Prior map overlap'):
            self.run_inspect()

    def test_summary_error_rejected(self):
        self.raw['errorsB'] = 1
        with self.assertRaisesRegex(ValueError, 'Bot errors'):
            self.run_inspect()

    def test_hidden_game_error_rejected(self):
        self.raw['pairs'][0]['aAsP0']['error'][1] = 'synthetic failure'
        with self.assertRaisesRegex(ValueError, 'Game error'):
            self.run_inspect()

    def test_score_winner_inconsistency_rejected(self):
        self.raw['pairs'][0]['aAsP1']['score'] = [8, 1]
        with self.assertRaisesRegex(ValueError, 'Score/winner'):
            self.run_inspect()


class FixedScheduleTest(unittest.TestCase):
    def run_sequence(self, wins_by_block, expected_stage):
        """Feed write-once fake reports through the REAL v4 reader and stopping gate."""
        with tempfile.TemporaryDirectory(prefix='fast-path-strength-synthetic-') as directory:
            root = Path(directory).resolve()
            run = root / 'analysis'
            bench = root / 'bench'
            run.mkdir()
            (run / 'freeze.json').write_text('{}', encoding='utf-8')
            frozen = {'java': 'SYNTHETIC-NOT-EXECUTED', 'classpath': 'SYNTHETIC-NOT-EXECUTED'}
            commands = []

            def fake_logged(label, command):
                index = [row[0] for row in driver.schedule.BLOCKS].index(label)
                self.assertLess(index, len(wins_by_block), 'Attempted an undeclared continuation')
                self.assertEqual(command[3:10], ['btk.runner.BenchmarkRunner', driver.A, driver.B,
                                               '', '', '2', ''])
                self.assertEqual(command[11], 'cg')
                self.assertEqual(command[12], 'bench/pools/' + driver.NAME + '-' + label + '.json')
                self.assertEqual(command[13], 'analysis/excluded-maps.json')
                self.assertEqual(root / command[10], bench / (label + '.json'))
                commands.append(deepcopy(command))
                with (bench / (label + '.json')).open('x', encoding='utf-8') as output:
                    json.dump(synthetic_report(index, wins_by_block[index]), output)

            with patch.object(driver, 'ROOT', root), patch.object(driver, 'RUN', run), \
                    patch.object(driver, 'BENCH', bench), patch.object(driver.schedule, 'ROOT', root), \
                    patch.object(driver, 'verify', return_value=frozen), \
                    patch.object(driver, 'logged', side_effect=fake_logged), \
                    patch.object(driver, 'inspect') as inspected, patch('builtins.print'):
                driver.strength()
                summary = driver.read(run / 'strength-summary.json')
                self.assertEqual(summary['localDecision'], expected_stage)
                self.assertEqual(len(commands), len(wins_by_block))
                self.assertEqual(inspected.call_count, len(wins_by_block))
                self.assertFalse(summary['promotion'])
                self.assertEqual(summary['newBossGames'], 0)
                self.assertEqual(len(summary['reports']), len(wins_by_block))
                for index, (name, *_rest) in enumerate(driver.schedule.BLOCKS):
                    self.assertEqual((bench / (name + '.json')).exists(), index < len(wins_by_block))
                    self.assertEqual((bench / (name + '-decision.json')).exists(), index < len(wins_by_block))
                # A completed sequence can never be replayed through this entry point.
                with self.assertRaisesRegex(ValueError, 'cannot be replaced'):
                    driver.strength()
                self.assertEqual(len(commands), len(wins_by_block))

    def test_stop_below_50_at_300(self):
        self.run_sequence([149], 'STOP_FUTILITY_300')

    def test_exact_50_continues_then_stop_below_50_at_500(self):
        self.run_sequence([150, 99], 'STOP_FUTILITY_500')

    def test_both_exact_50_continue_and_exact_55_final_pass(self):
        self.run_sequence([150, 100, 300], 'PASS_1000')

    def test_no_early_pass_and_final_below_55_rejected(self):
        self.run_sequence([240, 100, 209], 'NOT_PASSED')


if __name__ == '__main__':
    unittest.main()
