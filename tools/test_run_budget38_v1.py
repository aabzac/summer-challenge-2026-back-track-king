"""Synthetic evidence checks only: no Java, bot, benchmark or process execution."""
import contextlib
import copy
import io
import json
from pathlib import Path
import shutil
import unittest
from unittest.mock import patch
import uuid

import run_budget38_v1 as study


class Budget38ProcessGateTest(unittest.TestCase):
    def setUp(self):
        self.test_root = Path(__file__).resolve().parents[1] / 'build/test-run-budget38-v1'
        self.test_root.mkdir(parents=True, exist_ok=True)
        self.root = self.test_root / ('case-' + uuid.uuid4().hex)
        self.run = self.root / 'analysis'
        self.run.mkdir(parents=True)
        self.bench = self.root / 'bench'
        self.addCleanup(self.cleanup)
        self.addCleanup(patch.stopall)
        patch.object(study, 'ROOT', self.root).start()
        patch.object(study, 'RUN', self.run).start()
        patch.object(study, 'BENCH', self.bench).start()
        # Any accidental call to the measurement subprocess in these tests is an error.
        patch.object(study.subprocess, 'Popen', side_effect=AssertionError('No process runs in unit tests')).start()
        params = {'NOISE': '0.1', 'USE_BATCHED_REFRESH': 'true', 'USE_ROLLOUT_POLICY': 'true'}
        self.frozen = {
            'candidate': {'spec': study.CANDIDATE, 'sourceSha256': 'a' * 64,
                          'classesSha256': 'b' * 64, 'params': {**params, 'TURN_BUDGET_MS': '38'}},
            'control': {'spec': study.CONTROL, 'params': params},
            'processMaps': [{'path': f'replays/cg/{100 + i}.json'} for i in range(8)],
        }

    def cleanup(self):
        if self.root.resolve().parent != self.test_root.resolve():
            raise AssertionError('Test cleanup escaped workspace directory')
        shutil.rmtree(self.root)

    def report(self, index=0):
        games = []
        for seat in range(2):
            games.append({'game': seat, 'player': seat, 'seed': 7000 + seat,
                          'map': self.frozen['processMaps'][index]['path'],
                          'result': {'turns': 2, 'error': [None, None]},
                          'timeouts': 0, 'nominalOverruns': 0,
                          'timings': [{'turn': t, 'budgetMs': 1000 if t == 0 else 50,
                                       'graceMs': 0, 'responseMs': 600.0 if t == 0 else 38.0,
                                       'status': 'OK'} for t in range(2)]})
        return {'sourceSha256': 'a' * 64, 'classesSha256': 'b' * 64,
                'strict': True, 'graceMs': 0, 'ioDiagnostics': False,
                'opponent': study.CONTROL, 'opponentParams': self.frozen['control']['params'].copy(),
                'problems': 0, 'games': games}

    def inspect(self, report):
        return study.inspect_process(report, self.frozen, self.frozen['processMaps'][0]['path'])

    def retained_pass(self):
        pins = []
        for index in range(8):
            path = self.run / f'process-{index + 1}.json'
            study.save(path, self.report(index))
            pins.append(study.pin(path))
        summary = {'complete': True, 'status': 'PASS', 'attemptedGamesWithReports': 16,
                   'problemGames': 0, 'timeouts': 0, 'nominalOverruns': 0, 'reports': pins}
        study.save(self.run / 'process-summary.json', summary)
        return summary

    def replace_summary(self, summary):
        # Mutate synthetic evidence to exercise the verifier; never touches run evidence.
        (self.run / 'process-summary.json').write_text(json.dumps(summary), encoding='utf-8')

    def test_exact_deadline_is_allowed_and_late_or_missing_response_fails(self):
        report = self.report()
        report['games'][0]['timings'][0]['responseMs'] = 1000.0
        report['games'][0]['timings'][1]['responseMs'] = 50.0
        self.assertFalse(any(row['failed'] for row in self.inspect(report)))
        for turn, response in ((0, 1000.000001), (1, 50.000001), (1, None)):
            report = self.report()
            report['games'][0]['timings'][turn]['responseMs'] = response
            with self.subTest(turn=turn, response=response):
                self.assertTrue(self.inspect(report)[0]['failed'])

    def test_nonfinite_negative_and_nonnumeric_observed_times_are_rejected(self):
        for response in (float('nan'), float('inf'), -0.001, True, '38'):
            for status in ('OK', 'TIMEOUT'):
                report = self.report()
                report['games'][0]['timings'][1].update(responseMs=response, status=status)
                with self.subTest(response=response, status=status), self.assertRaisesRegex(ValueError, 'response time'):
                    self.inspect(report)

    def test_artifact_or_outer_deadline_changes_are_rejected(self):
        for field in ('sourceSha256', 'classesSha256'):
            report = self.report()
            report[field] = 'c' * 64
            with self.subTest(field=field), self.assertRaisesRegex(ValueError, 'artifact differs'):
                self.inspect(report)
        for turn, field, value in ((0, 'budgetMs', 986), (1, 'budgetMs', 38),
                                   (1, 'budgetMs', 51), (1, 'graceMs', 1)):
            report = self.report()
            report['games'][0]['timings'][turn][field] = value
            with self.subTest(turn=turn, field=field), self.assertRaisesRegex(ValueError, 'deadline changed'):
                self.inspect(report)
        for field, value in (('strict', False), ('graceMs', 1), ('ioDiagnostics', True),
                             ('opponent', 'v09'), ('opponentParams', {})):
            report = self.report()
            report[field] = value
            with self.subTest(field=field), self.assertRaises(ValueError):
                self.inspect(report)

    def test_missing_clean_timing_coverage_is_rejected_but_failed_attempt_is_retained(self):
        for timings in ([], [self.report()['games'][0]['timings'][0]],
                        [self.report()['games'][0]['timings'][1]]):
            report = self.report()
            report['games'][0]['timings'] = timings
            with self.subTest(timings=timings), self.assertRaisesRegex(ValueError, 'timing'):
                self.inspect(report)
        report = self.report()
        report['games'][0]['result']['turns'] = 3
        report['games'][0]['timings'][-1].update(status='TIMEOUT', responseMs=None)
        report['games'][0].update(timeouts=1, nominalOverruns=1)
        rows = self.inspect(report)
        self.assertTrue(rows[0]['failed'])
        self.assertIsNone(rows[0]['timings'][-1]['responseMs'])
        self.assertFalse(rows[1]['failed'])

    def test_status_errors_and_transport_counters_cannot_pass(self):
        for key, value in (('timeouts', 1), ('nominalOverruns', 1), ('error', ['invalid command', None]),
                           ('status', 'EOF'), ('status', 'UNSOLICITED'), ('status', 'TIMEOUT')):
            report = self.report()
            game = report['games'][0]
            if key == 'error':
                game['result'][key] = value
            elif key == 'status':
                game['timings'][-1][key] = value
            else:
                game[key] = value
            with self.subTest(key=key, value=value):
                self.assertTrue(self.inspect(report)[0]['failed'])

    def test_retained_pass_requires_all_eight_exact_raw_reports(self):
        self.retained_pass()
        study.require_process_pass(self.frozen)
        path = self.run / 'process-8.json'
        path.write_text(path.read_text(encoding='utf-8') + ' ', encoding='utf-8')
        with self.assertRaisesRegex(ValueError, 'evidence changed'):
            study.require_process_pass(self.frozen)

    def test_raw_failures_are_rechecked_even_when_summary_claims_pass(self):
        summary = self.retained_pass()
        path = self.run / 'process-1.json'
        report = study.read(path)
        report['games'][0]['timings'][-1]['responseMs'] = None
        path.write_text(json.dumps(report), encoding='utf-8')
        summary['reports'][0] = study.pin(path)
        self.replace_summary(summary)
        with self.assertRaisesRegex(ValueError, 'raw process failure'):
            study.require_process_pass(self.frozen)

    def test_strength_never_starts_without_full_process_pass(self):
        summary = self.retained_pass()
        cases = [('status', 'FAILED'), ('status', 'INCOMPLETE'), ('complete', False),
                 ('attemptedGamesWithReports', 15), ('problemGames', 1), ('timeouts', 1),
                 ('nominalOverruns', 1), ('reports', summary['reports'][:-1])]
        with patch.object(study, 'load_freeze', return_value=self.frozen), \
                patch.object(study.runner, 'run_block', side_effect=AssertionError('Strength must not start')) as run:
            for field, value in cases:
                changed = copy.deepcopy(summary)
                changed[field] = value
                self.replace_summary(changed)
                with self.subTest(field=field, value=value), self.assertRaises(ValueError):
                    study.strength_stage()
            (self.run / 'process-summary.json').unlink()
            with self.assertRaises(FileNotFoundError):
                study.strength_stage()
        run.assert_not_called()

    def test_valid_process_pass_can_start_only_the_first_registered_strength_block(self):
        self.retained_pass()
        report = {'path': str(self.bench / 'block1.json'), 'sha256': 'd' * 64}
        verdict = {'directStage': 'STOP_FUTILITY_300'}
        with patch.object(study, 'load_freeze', return_value=self.frozen), \
                patch.object(study.runner, 'run_block', return_value=report) as run, \
                patch.object(study, 'inspect_strength') as inspect, \
                patch.object(study.gate, 'assess', return_value=verdict), \
                contextlib.redirect_stdout(io.StringIO()):
            study.strength_stage()
        run.assert_called_once_with(study.NAME, study.CANDIDATE, study.CONTROL,
                                    'block1', 300, 150, study.BASE_SEED)
        inspect.assert_called_once_with(report, self.frozen, 0)
        self.assertFalse(study.read(self.run / 'strength-summary.json')['promotion'])
        self.assertTrue(study.read(self.run / 'strength-summary.json')['localOnly'])

    def test_explicit_strength_skip_cannot_be_overridden_by_pass_summary(self):
        self.retained_pass()
        study.save(self.run / 'strength-skipped.json', {'status': 'SKIPPED_PROCESS_FAILED'})
        with self.assertRaisesRegex(ValueError, 'already skipped'):
            study.require_process_pass(self.frozen)


if __name__ == '__main__':
    unittest.main()
