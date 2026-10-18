"""Synthetic report/budget/futility tests only; no Java, bot, network or game calls."""
from copy import deepcopy
from contextlib import contextmanager
import io
import json
from pathlib import Path
import tempfile
import unittest
import uuid
from unittest.mock import patch

import run_v09_ink45_local_strength as driver
import test_v09_ink40_strength as previous
import practical_gate_v4 as shared_gate

PARAMS_B = {'NOISE': '0.1', 'USE_BATCHED_REFRESH': 'true', 'USE_ROLLOUT_POLICY': 'true',
            'USE_INCREMENTAL_INK_REFRESH': 'true', 'TURN_BUDGET_MS': '40'}
PARAMS_A = dict(PARAMS_B, TURN_BUDGET_MS='45')
_old_report = previous.synthetic_report


@contextmanager
def retained_fixture(*args, **kwargs):
    folder = driver.HARNESS / 'fake-fixtures' / uuid.uuid4().hex
    folder.mkdir(parents=True)
    yield str(folder)


def synthetic_report(index, wins, draws=0):
    raw = _old_report(index, wins, draws)
    raw['paramsA'], raw['paramsB'] = dict(PARAMS_A), dict(PARAMS_B)
    raw['turnSearchBudgetMsA'], raw['turnSearchBudgetMsB'] = 45, 40
    return raw


class NewFixture:
    def setUp(self):
        for name, value in (('driver', driver), ('PARAMS_A', PARAMS_A), ('PARAMS_B', PARAMS_B),
                            ('synthetic_report', synthetic_report)):
            change = patch.object(previous, name, value)
            change.start(); self.addCleanup(change.stop)
        super().setUp()


class ReportGuardsTest(NewFixture, previous.ReportGuardsTest):
    pass


class FixedScheduleTest(NewFixture, previous.FixedScheduleTest):
    pass


class ScopedBudgetsTest(unittest.TestCase):
    def raw(self):
        with patch.object(previous, 'driver', driver):
            return synthetic_report(0, 150)

    def test_exact_unequal_budgets_and_raw_hash_preserved(self):
        with tempfile.TemporaryDirectory(prefix='ink45-reader-fake-') as folder:
            path = Path(folder) / 'report.json'
            path.write_text(json.dumps(self.raw()), encoding='utf-8')
            before = path.read_bytes()
            parsed = driver.gate.read_report(path, 300)
            self.assertEqual(path.read_bytes(), before)
            self.assertEqual(parsed['sha256'], driver.sha(path))
            self.assertEqual(list(driver.gate.effective_budgets(parsed).values()), [486, 486, 45, 40])

    def test_shared_gate_still_rejects45(self):
        self.assertIsNot(driver.gate, shared_gate)
        with self.assertRaisesRegex(ValueError, 'Unsupported'):
            shared_gate.effective_budgets({'data': self.raw()})

    def test_budget_seat_identity_swap_is_rejected(self):
        raw = self.raw(); raw['turnSearchBudgetMsA'], raw['turnSearchBudgetMsB'] = 40, 45
        with self.assertRaises(ValueError): driver.gate.effective_budgets({'data': raw})

    def test_missing_per_arm_evidence_is_rejected(self):
        for fields in (driver.gate.ARM_BUDGET_FIELDS, driver.gate.ARM_BUDGET_FIELDS[:1]):
            raw = self.raw()
            for key in fields: del raw[key]
            with self.subTest(fields=fields), self.assertRaises(ValueError): driver.gate.effective_budgets({'data': raw})

    def test_mismatched_spec_and_params_is_rejected(self):
        for key, value in (('a', driver.B), ('b', driver.A)):
            raw = self.raw(); raw[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError): driver.gate.effective_budgets({'data': raw})
        raw = self.raw(); raw['paramsA']['TURN_BUDGET_MS'] = '40'
        with self.assertRaises(ValueError): driver.gate.effective_budgets({'data': raw})

    def test_only_budget_may_differ(self):
        driver.params_guard(PARAMS_A, PARAMS_B)
        for key, value in (('NOISE', '0'), ('USE_INCREMENTAL_INK_REFRESH', 'false'), ('USE_FAST_CHEAPEST_PATH', 'true')):
            pa = dict(PARAMS_A); pa[key] = value
            with self.subTest(key=key), self.assertRaises(ValueError): driver.params_guard(pa, PARAMS_B)

    def test_nonrollout_policy_is_rejected(self):
        for key in ('USE_HEURISTIC_POLICY', 'USE_NEURAL_TARGET_PROPOSAL', 'USE_ROLLOUT_MISSED_DEADLINE'):
            raw = self.raw(); raw['paramsA'][key] = 'true'
            with self.subTest(key=key), self.assertRaises(ValueError): driver.gate.effective_budgets({'data': raw})

    def test_waiver_is_local_and_never_authorizes_new_process_or_boss(self):
        self.assertTrue(driver.AUTHORIZATION['timeoutGateWaived'])
        self.assertTrue(driver.AUTHORIZATION['localStrengthOnly'])
        self.assertTrue(driver.AUTHORIZATION['noNewProcessGames'])
        self.assertFalse(driver.AUTHORIZATION['promotion'])
        self.assertEqual(driver.AUTHORIZATION['newBossGames'], 0)
        self.assertEqual(driver.AUTHORIZATION['newProcessGames'], 0)


if __name__ == '__main__':
    driver.need(not driver.HARNESS.exists(), 'Preserve existing synthetic test evidence')
    driver.HARNESS.mkdir(parents=True)
    sources = [Path(__file__), Path(driver.__file__), Path(previous.__file__),
               Path(previous.driver.__file__), Path(shared_gate.__file__),
               driver.ROOT / 'tools/practical_gate.py', driver.ROOT / 'tools/run_practical_v4.py',
               driver.ROOT / 'tools/diagnostics/V09Ink45StrengthPreflight.java']
    driver.save(driver.HARNESS / 'source-pins.json', {driver.rel(p): driver.sha(p) for p in sources})
    stream = io.StringIO()
    suite = unittest.TestSuite(unittest.defaultTestLoader.loadTestsFromTestCase(cls)
                               for cls in (ReportGuardsTest, FixedScheduleTest, ScopedBudgetsTest))
    with patch.object(driver.subprocess, 'Popen', side_effect=AssertionError('No native calls in synthetic tests')), \
         patch.object(tempfile, 'TemporaryDirectory', side_effect=retained_fixture):
        result = unittest.TextTestRunner(stream=stream, verbosity=2).run(suite)
    with (driver.HARNESS / 'driver-tests.log').open('x', encoding='utf-8', newline='\n') as output:
        output.write(stream.getvalue())
    driver.save(driver.HARNESS / 'driver-tests-exit.json', {'at': driver.now(), 'exitCode': 0 if result.wasSuccessful() else 1,
        'tests': result.testsRun, 'failures': len(result.failures), 'errors': len(result.errors), 'noGamesOrBotCalls': True,
        'fixtureRepair': {'priorHarness': 'build/analysis/v09-ink45-strength-harness-v1',
            'reason': 'Sandbox Temp denied fixture access; use retained workspace fake-fixtures. No assertion or game changes.'},
        'logSha256': driver.sha(driver.HARNESS / 'driver-tests.log')})
    print(stream.getvalue(), end='')
    raise SystemExit(0 if result.wasSuccessful() else 1)
