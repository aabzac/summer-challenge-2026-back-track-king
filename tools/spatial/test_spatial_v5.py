"""Synthetic v5 value-target/ridge checks; no corpus extraction or actual fitting run."""
from __future__ import annotations

import base64
import copy
import re
import unittest

from train_spatial import ROOT, SpatialNet, np, torch
from model import WEIGHT_ORDER
from test_spatial_v4 import fixture_game, scratch_directory
from v2_support import weight_hash
from v3_support import java_payload_hash
from v4_data import digest_file, row_tensors
from v5_value import (CONFIG, V4_SHA256, export_v5_weights, extract_features, grouped_metrics,
                      install_head, policy_bytes, predict_head, regression_metrics, ridge_fit,
                      save_checkpoint, turn_targets)


def two_turn_metadata(seat=0):
    return {"teacherSeat": seat, "score": [13, 13], "turns": [
        {"turn": 0, "rowStart": 0, "gained": [10, 4], "scoreAfter": [10, 4], "terminalAfter": False},
        {"turn": 1, "rowStart": 4, "gained": [3, 9], "scoreAfter": [13, 13], "terminalAfter": True}]}


def frozen_v4_model():
    model = SpatialNet()
    source = (ROOT / "src/main/java/btk/bot/neural/SpatialV4Weights.java").read_text(encoding="utf-8")
    payload = "".join(re.findall(r'"([A-Za-z0-9+/=]+)"', source))
    weights = np.frombuffer(base64.b64decode(payload, validate=True), dtype="<f4")
    state, at = model.state_dict(), 0
    with torch.no_grad():
        for name in WEIGHT_ORDER:
            count = state[name].numel()
            state[name].copy_(torch.from_numpy(weights[at:at + count].copy()).reshape_as(state[name]))
            at += count
    assert at == 3439 and weight_hash(model) == V4_SHA256
    return model


def orthogonal_fixture():
    x = np.ones((4, 14), dtype=np.float64)
    x[:, 0] = [-1., 1., -1., 1.]
    x[:, 1] = [-1., -1., 1., 1.]
    return x, 3 + 2 * x[:, 0] - 4 * x[:, 1]


class V5Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)

    def test_remaining_target_seat_sign_and_no_future_terminal_leak(self):
        y0, income0, gap0 = turn_targets(two_turn_metadata(0))
        y1, income1, gap1 = turn_targets(two_turn_metadata(1))
        np.testing.assert_allclose(y0, [0., -.0006])
        np.testing.assert_allclose(y1, -y0)
        np.testing.assert_array_equal(gap0, [0., 6.])
        np.testing.assert_array_equal(gap1, -gap0)
        np.testing.assert_allclose(income0, [0., .0594])
        np.testing.assert_allclose(income1, -income0)
        # At the last observed preturn, future value after its real transition is
        # zero: the target is exactly the final immediate gap, without a bonus.
        self.assertEqual(y0[-1], (3 - 9) / CONFIG["score_scale"])

    def test_inconsistent_score_and_terminal_metadata_rejected(self):
        for corruption in ("score", "gain", "early", "order", "final", "nan"):
            metadata = two_turn_metadata()
            if corruption == "score":
                metadata["turns"][0]["scoreAfter"][0] += 1
            elif corruption == "gain":
                metadata["turns"][0]["gained"][0] = -1
            elif corruption == "early":
                metadata["turns"][0]["terminalAfter"] = True
            elif corruption == "order":
                metadata["turns"][1]["rowStart"] = 0
            elif corruption == "final":
                metadata["score"] = [14, 13]
            else:
                metadata["turns"][1]["gained"][0] = float("nan")
            with self.assertRaises(ValueError):
                turn_targets(metadata)
        with self.assertRaises(ValueError):
            turn_targets(two_turn_metadata(), max_turns=2)
        with self.assertRaises(ValueError):
            turn_targets(two_turn_metadata(), score_scale=100.)

    def test_closed_form_ridge_scaling_and_unpenalized_intercept(self):
        features, target = orthogonal_fixture()
        fit = ridge_fit(features, target)
        expected = np.zeros(14)
        expected[:2] = [2 / 1.01, -4 / 1.01]
        np.testing.assert_allclose(fit["weights_float64"], expected, atol=1e-14)
        self.assertAlmostEqual(fit["bias_float64"], 3.)
        self.assertEqual(fit["active_columns"], [0, 1])
        self.assertEqual(fit["dropped_columns"], list(range(2, 14)))
        self.assertEqual(fit["standardized_design_rank"], 2)
        self.assertLess(fit["solver_residual_l2"], 1e-12)
        shifted = features.copy()
        shifted[:, 0] = 7 + 10 * shifted[:, 0]
        shifted_fit = ridge_fit(shifted, target)
        np.testing.assert_allclose(shifted @ shifted_fit["weights_float64"] + shifted_fit["bias_float64"],
                                   features @ fit["weights_float64"] + fit["bias_float64"], atol=1e-12)
        scaled = ridge_fit(features, target * .01)
        np.testing.assert_allclose(scaled["weights_float64"], np.asarray(fit["weights_float64"]) * .01, atol=1e-14)
        self.assertAlmostEqual(scaled["bias_float64"], fit["bias_float64"] * .01)

    def test_constant_features_rank_deficiency_and_bad_fit_inputs(self):
        features = np.ones((4, 14))
        fit = ridge_fit(features, np.arange(4.))
        self.assertEqual(fit["active_columns"], [])
        self.assertEqual(fit["bias_float64"], 1.5)
        self.assertIsNone(fit["regularized_system_condition"])
        features[:, 0] = np.arange(4.)
        features[:, 1] = features[:, 0]
        fit = ridge_fit(features, np.arange(4.))
        self.assertEqual(fit["standardized_design_rank"], 1)
        self.assertTrue(np.isfinite(fit["regularized_system_condition"]))
        self.assertLess(fit["solver_relative_residual_l2"], 1e-12)
        for x, y in ((features[:, :13], np.arange(4.)), (features, np.arange(3.)),
                     (np.empty((0, 14)), np.empty(0)), (features * np.nan, np.arange(4.)),
                     (features, np.asarray([1., 2., 3., np.inf]))):
            with self.assertRaises(ValueError):
                ridge_fit(x, y)

    def test_hook_reads_stage_zero_z_including_short_batch_and_removes_itself(self):
        model = frozen_v4_model()
        rows = np.tile(fixture_game()[2][::4], 65)
        rows["scalars"][:, 0] = np.arange(65) / 100
        old_bytes = model.encoded_weights()
        features, values = extract_features(model, rows)
        self.assertEqual(features.shape, (65, 14))
        self.assertEqual(features.dtype, np.float32)
        np.testing.assert_array_equal(features[:, 8:], rows["scalars"])
        np.testing.assert_array_equal(values, predict_head(model, features))
        self.assertEqual(model.encoded_weights(), old_bytes)
        self.assertEqual(len(model.value._forward_pre_hooks), 0)
        rows["stage"][0] = 1
        with self.assertRaises(ValueError):
            extract_features(model, rows)
        rows["stage"][0] = 0
        rows["planes"][0, 0, 0, 0] = np.nan
        with self.assertRaises(ValueError):
            extract_features(model, rows)
        self.assertEqual(len(model.value._forward_pre_hooks), 0)

    def test_head_installation_preserves_policy_logits_and_historical_exports(self):
        model = frozen_v4_model()
        old_policy = policy_bytes(model)
        obs, _, _ = row_tensors(fixture_game()[2])
        with torch.no_grad():
            before = model(obs)
        fit = ridge_fit(*orthogonal_fixture())
        install_head(model, fit, old_policy)
        with torch.no_grad():
            after = model(obs)
        self.assertTrue(torch.equal(before[0], after[0]))
        self.assertTrue(torch.equal(before[1], after[1]))
        self.assertFalse(torch.equal(before[2], after[2]))
        self.assertEqual(len(old_policy), 3424 * 4)
        self.assertEqual(policy_bytes(model), old_policy)
        names = ("SpatialWeights.java", "SpatialInitialWeights.java", "SpatialV2Weights.java", "SpatialV3Weights.java", "SpatialV4Weights.java")
        original_paths = [ROOT / "src/main/java/btk/bot/neural" / name for name in names]
        hashes = {path: digest_file(path) for path in original_paths}
        with scratch_directory() as directory:
            output = directory / "SpatialV5Weights.java"
            export_v5_weights(model, output)
            self.assertEqual(java_payload_hash(output), weight_hash(model))
            for name in names:
                with self.assertRaises(ValueError):
                    export_v5_weights(model, directory / name)
            checkpoint = directory / "final.pt"
            save_checkpoint(checkpoint, model, CONFIG, 1, fit)
            stored = torch.load(checkpoint, map_location="cpu", weights_only=False)
            self.assertEqual(stored["fit_count"], 1)
            self.assertEqual(stored["ridge"], fit)
            self.assertNotIn("optimizer", stored)
            self.assertNotIn("torch_rng", stored)
        self.assertEqual({path: digest_file(path) for path in original_paths}, hashes)
        with self.assertRaises(AssertionError):
            install_head(model, fit, b"different frozen policy")

    def test_regression_metrics_constant_empty_nonfinite_and_score_units(self):
        result = regression_metrics(np.asarray([1., 2., 3.]), np.asarray([2., 3., 4.]))
        self.assertEqual(result["rmse"], 1.)
        self.assertEqual(result["mae"], 1.)
        self.assertEqual(result["bias"], 1.)
        self.assertEqual(result["rmse_score_points"], 10000.)
        self.assertAlmostEqual(result["pearson"], 1.)
        constant_target = regression_metrics(np.ones(3), np.arange(3.))
        self.assertIsNone(constant_target["r_squared"])
        self.assertIsNone(constant_target["pearson"])
        constant_prediction = regression_metrics(np.arange(3.), np.ones(3))
        self.assertIsNone(constant_prediction["pearson"])
        self.assertEqual(regression_metrics(np.empty(0), np.empty(0))["rows"], 0)
        with self.assertRaises(ValueError):
            regression_metrics(np.asarray([1.]), np.asarray([np.nan]))

    def test_fixed_opponent_and_turn_slices(self):
        lineage = [{"opponentSpec": opponent, "turn": turn}
                   for opponent, turn in (("v01", 0), ("spatial-ppo-v2", 25), ("v09", 50), ("v09", 99))]
        target = np.arange(4.)
        metrics = grouped_metrics(target, {"fitted_head": target, "zero": np.zeros(4)}, lineage)
        self.assertEqual(metrics["all"]["fitted_head"]["rmse"], 0.)
        self.assertEqual(metrics["opponent:v09"]["fitted_head"]["rows"], 2)
        for key in ("turn:0-24", "turn:25-49", "turn:50-74", "turn:75-99"):
            self.assertEqual(metrics[key]["zero"]["rows"], 1)
        with self.assertRaises(ValueError):
            grouped_metrics(target, {"zero": np.zeros(4)}, lineage[:-1])


if __name__ == "__main__":
    unittest.main()
