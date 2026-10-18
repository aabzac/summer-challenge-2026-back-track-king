"""Fake-only waiver and unchanged report/stopping checks: no Java or bot calls."""
from copy import deepcopy
import unittest
from unittest.mock import patch
import run_v09_ink40_local_strength as local
import test_v09_ink40_strength as previous


def evidence():
    rows = [{'map': str(i // 2) + '.json', 'seat': i % 2, 'seed': 7000 + i % 2,
             'turns': 100, 'responses': 100, 'errors': [None, None], 'timeouts': 0,
             'overruns': 0, 'missing': 0, 'badStatuses': 0} for i in range(16)]
    for i in (3, 11):
        rows[i].update(timeouts=1, overruns=1, badStatuses=1)
        rows[i]['errors'][1] = 'exception: java.lang.IllegalStateException: TIMEOUT turn 36 (deadline 50 ms)'
    p = {'games': 16, 'pass': False, 'errors': 2, 'timeouts': 2, 'overruns': 2, 'missing': 0}
    ready = {'complete': True, 'status': 'NOT_READY', 'strengthReady': False, 'engineExact': 292,
             'parity': {'positions': 524, 'pass': True, 'reused': True}, 'process': dict(p),
             'artifacts': {k: {'charactersUtf16': 99000} for k in ('artifact', 'artifact-off')}}
    audit = {'complete': True, 'status': 'PASS', 'checks': 100, 'promotion': False,
             'newBossGames': 0, 'newGameOrPolicyCalls': 0,
             'readiness': {'status': 'NOT_READY', 'strengthReady': False}, 'strengthReady': False,
             'process': dict(p, botErrors=2, badStatuses=2, rows=rows,
                             first={'count': 16, 'maxMs': 700}, later={'count': 1584, 'above50Ms': 2})}
    return ready, audit


class WaiverTest(unittest.TestCase):
    def test_retained_timeouts_authorize_only_local_strength(self):
        local.waiver_guards(*evidence())
        self.assertTrue(local.AUTHORIZATION['timeoutGateWaived'])
        self.assertTrue(local.AUTHORIZATION['localStrengthOnly'])
        self.assertFalse(local.AUTHORIZATION['promotion'])

    def test_incomplete_readiness_rejected(self):
        r, a = evidence(); r['complete'] = False
        with self.assertRaises(ValueError): local.waiver_guards(r, a)

    def test_engine_shortfall_rejected(self):
        r, a = evidence(); r['engineExact'] = 291
        with self.assertRaises(ValueError): local.waiver_guards(r, a)

    def test_parity_failure_rejected(self):
        r, a = evidence(); r['parity']['pass'] = False
        with self.assertRaises(ValueError): local.waiver_guards(r, a)

    def test_missing_response_rejected(self):
        r, a = evidence(); r['process']['missing'] = 1
        with self.assertRaises(ValueError): local.waiver_guards(r, a)

    def test_cap_violation_rejected(self):
        r, a = evidence(); r['artifacts']['artifact']['charactersUtf16'] = 100001
        with self.assertRaises(ValueError): local.waiver_guards(r, a)

    def test_missing_artifact_rejected(self):
        r, a = evidence(); del r['artifacts']['artifact-off']
        with self.assertRaises(ValueError): local.waiver_guards(r, a)

    def test_failed_audit_rejected(self):
        r, a = evidence(); a['status'] = 'FAIL'
        with self.assertRaises(ValueError): local.waiver_guards(r, a)

    def test_rewritten_readiness_rejected(self):
        r, a = evidence(); a['readiness']['status'] = 'PASS'
        with self.assertRaises(ValueError): local.waiver_guards(r, a)

    def test_audit_counter_mismatch_rejected(self):
        r, a = evidence(); a['process']['timeouts'] = 1
        with self.assertRaises(ValueError): local.waiver_guards(r, a)

    def test_generic_exception_rejected(self):
        r, a = evidence(); a['process']['rows'][3]['errors'][1] = 'exception: NullPointerException'
        with self.assertRaisesRegex(ValueError, 'TIMEOUT'): local.waiver_guards(r, a)

    def test_incomplete_raw_rows_rejected(self):
        r, a = evidence(); a['process']['rows'].pop()
        with self.assertRaises(ValueError): local.waiver_guards(r, a)

    def test_missing_response_hidden_in_row_rejected(self):
        r, a = evidence(); a['process']['rows'][0]['responses'] -= 1
        with self.assertRaises(ValueError): local.waiver_guards(r, a)

    def test_wrong_error_seat_rejected(self):
        r, a = evidence(); a['process']['rows'][3]['errors'].reverse()
        with self.assertRaisesRegex(ValueError, 'TIMEOUT'): local.waiver_guards(r, a)


class ReportGuardsTest(previous.ReportGuardsTest):
    def setUp(self):
        changed = patch.object(previous, 'driver', local.base)
        changed.start(); self.addCleanup(changed.stop)
        super().setUp()


class FixedScheduleTest(previous.FixedScheduleTest):
    def setUp(self):
        changed = patch.object(previous, 'driver', local.base)
        changed.start(); self.addCleanup(changed.stop)


if __name__ == '__main__':
    unittest.main()
