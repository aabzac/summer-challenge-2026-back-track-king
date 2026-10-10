"""Synthetic exact-resumption and fixed-continuation guards; no real-data fit."""
from __future__ import annotations

import copy
import random
import unittest
from unittest import mock

from train_spatial import SpatialNet, checkpoint, np, torch
from test_spatial_v4 import fixture_game, scratch_directory
from test_fit_probe import frozen_v4_model
from v2_support import weight_hash
from v3_support import java_payload_hash
from v4_data import row_tensors
from v4_distill import policy_rows as original_policy_rows
from v10_distill import (CONFIG, TRAIN_NAMES, V4_SHA256, batch_indices, check_diagnostic_rng,
                         export_weights, policy_rows, restore_original, restore_state,
                         rng_equal, rng_hashes, rng_snapshot)


def synthetic_state():
    random.seed(901)
    np.random.seed(902)
    torch.manual_seed(903)
    model = SpatialNet()
    model.value.requires_grad_(False)
    optimizer = torch.optim.Adam([parameter for parameter in model.parameters() if parameter.requires_grad], lr=.001)
    _, _, rows = fixture_game()
    rows = np.tile(rows, 17)  # 68 rows exercise a full minibatch and an actual tail.
    synthetic_epoch(model, optimizer, rows)
    state = {"model": copy.deepcopy(model.state_dict()), "optimizer": copy.deepcopy(optimizer.state_dict()),
             "iteration": 1, "config": {"schema": "synthetic-only"}, **rng_snapshot()}
    return model, optimizer, rows, state


def synthetic_epoch(model, optimizer, rows):
    model.train()
    order = torch.randperm(len(rows)).numpy()
    # Production shuffles use only Torch. These extra TEST-ONLY draws prove that
    # saved Python and NumPy streams also continue exactly after restoration.
    draws = (random.random(), float(np.random.random()))
    for indices in batch_indices(order):
        obs, labels, stages = row_tensors(rows[indices])
        loss = policy_rows(model, obs, labels, stages)[0].mean()
        optimizer.zero_grad(set_to_none=True)
        loss.backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1., error_if_nonfinite=True)
        optimizer.step()
    return order, draws


class V10Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)
        torch.use_deterministic_algorithms(True)

    def test_saved_resume_matches_uninterrupted_next_epoch_weights_moments_and_rng(self):
        model, optimizer, rows, saved = synthetic_state()
        value_before = {name: parameter.detach().clone() for name, parameter in model.value.named_parameters()}
        with scratch_directory() as directory:
            path = directory / "state.pt"
            checkpoint(path, model, optimizer, saved["config"], 1)
            self.assertTrue(rng_equal(saved, rng_snapshot()))
            uninterrupted_order, uninterrupted_draws = synthetic_epoch(model, optimizer, rows)
            expected_model, expected_optimizer = copy.deepcopy(model.state_dict()), copy.deepcopy(optimizer.state_dict())
            expected_rng = rng_snapshot()
            loaded = torch.load(path, map_location="cpu", weights_only=False)
            restored_model, restored_optimizer = restore_state(loaded, 1, 2)
            self.assertTrue(rng_equal(loaded, rng_snapshot()))
            resumed_order, resumed_draws = synthetic_epoch(restored_model, restored_optimizer, rows)
            self.assertTrue(np.array_equal(uninterrupted_order, resumed_order))
            self.assertEqual(uninterrupted_draws, resumed_draws)
            self.assertTrue(rng_equal(expected_rng, rng_snapshot()))
            self.assertEqual(expected_optimizer["param_groups"], restored_optimizer.state_dict()["param_groups"])
            for name, value in restored_model.state_dict().items():
                self.assertTrue(torch.equal(expected_model[name], value), name)
            for index, state in restored_optimizer.state_dict()["state"].items():
                for name, value in state.items():
                    self.assertTrue(torch.equal(expected_optimizer["state"][index][name], value), (index, name))
            self.assertTrue(all(torch.equal(parameter, value_before[name]) for name, parameter in restored_model.value.named_parameters()))
            self.assertEqual({int(state["step"]) for state in restored_optimizer.state.values()}, {4})

    def test_fixed_full_corpus_batch_order_tail_and_counts(self):
        permutation = np.arange(18192)[::-1]
        batches = list(batch_indices(permutation))
        self.assertEqual(len(batches), 285)
        self.assertEqual([len(batch) for batch in batches], [64] * 284 + [16])
        self.assertTrue(np.array_equal(np.concatenate(batches), permutation))
        self.assertEqual(CONFIG["additional_epochs"] * len(batches), 18240)
        self.assertEqual(CONFIG["initial_adam_steps"] + 18240, 22800)
        self.assertEqual(64 * 18192, CONFIG["additional_row_exposures"])
        self.assertEqual(80 * 18192, CONFIG["cumulative_row_exposures"])
        self.assertEqual(CONFIG["checkpoint_epochs"], [16, 48, 80])
        self.assertEqual(CONFIG["metric_epochs"], list(range(16, 81, 8)))

    def test_malformed_state_counter_group_order_moments_model_and_rng_rejected(self):
        _, _, _, saved = synthetic_state()
        for corruption in ("missing_rng", "rng", "epoch", "counter", "order", "lr", "missing_moment", "shape", "nan", "negative", "model", "model_order"):
            bad = copy.deepcopy(saved)
            if corruption == "missing_rng":
                del bad["python_rng"]
            elif corruption == "rng":
                bad["torch_rng"] = torch.zeros(3, dtype=torch.uint8)
            elif corruption == "epoch":
                bad["iteration"] = 2
            elif corruption == "counter":
                bad["optimizer"]["state"][0]["step"] += 1
            elif corruption == "order":
                bad["optimizer"]["param_groups"][0]["params"].reverse()
            elif corruption == "lr":
                bad["optimizer"]["param_groups"][0]["lr"] = .002
            elif corruption == "missing_moment":
                del bad["optimizer"]["state"][0]
            elif corruption == "shape":
                bad["optimizer"]["state"][0]["exp_avg"] = torch.zeros(1)
            elif corruption == "nan":
                bad["optimizer"]["state"][0]["exp_avg"].flatten()[0] = float("nan")
            elif corruption == "negative":
                bad["optimizer"]["state"][0]["exp_avg_sq"].flatten()[0] = -1
            elif corruption == "model":
                bad["model"]["stem.weight"].flatten()[0] = float("inf")
            else:
                bad["model"] = dict(reversed(list(bad["model"].items())))
            with self.assertRaises(ValueError, msg=corruption):
                restore_state(bad, 1, 2)

    def test_original_checkpoint_byte_hash_guard_precedes_loading(self):
        with scratch_directory() as directory:
            path = directory / "wrong.pt"
            path.write_bytes(b"not the original checkpoint")
            with mock.patch("v10_distill.torch.load") as load:
                with self.assertRaisesRegex(ValueError, "byte-exact"):
                    restore_original(path)
                load.assert_not_called()

    def test_rng_diagnostic_guard_checks_all_three_streams_without_silent_restore(self):
        for stream in ("python", "numpy", "torch"):
            before = rng_snapshot()
            if stream == "python":
                random.random()
            elif stream == "numpy":
                np.random.random()
            else:
                torch.rand(1)
            self.assertNotEqual(rng_hashes(before)[stream], rng_hashes(rng_snapshot())[stream])
            with self.assertRaisesRegex(ValueError, "diagnostic advanced"):
                check_diagnostic_rng(before)
        before = rng_snapshot()
        check_diagnostic_rng(before)
        self.assertTrue(rng_equal(before, rng_snapshot()))

    def test_model_head_membership_and_loss_are_frozen_helpers(self):
        model, _, _, saved = synthetic_state()
        restored, optimizer = restore_state(saved, 1, 2)
        self.assertIs(policy_rows, original_policy_rows)
        self.assertEqual(tuple(name for name, parameter in restored.named_parameters() if parameter.requires_grad), TRAIN_NAMES)
        self.assertEqual(sum(parameter.numel() for parameter in restored.parameters()), 3439)
        self.assertEqual(sum(parameter.numel() for parameter in restored.parameters() if parameter.requires_grad), 3424)
        self.assertTrue(all(not parameter.requires_grad for parameter in restored.value.parameters()))
        self.assertEqual(len(optimizer.state), 18)
        self.assertEqual(weight_hash(model), weight_hash(restored))

    def test_export_new_class_only_and_bootstrap_cannot_replace_final_or_wrong_payload(self):
        model = frozen_v4_model()
        with scratch_directory() as directory:
            old = directory / "SpatialV4Weights.java"
            old.write_text("frozen", encoding="utf-8")
            with self.assertRaises(ValueError):
                export_weights(model, old)
            self.assertEqual(old.read_text(), "frozen")
            target = directory / "SpatialV10Weights.java"
            export_weights(model, target, temporary=True)
            self.assertEqual(java_payload_hash(target), V4_SHA256)
            self.assertIn("TEMPORARY", target.read_text())
            export_weights(model, target, temporary=True)
            export_weights(model, target)
            self.assertNotIn("TEMPORARY", target.read_text())
            with self.assertRaises(ValueError):
                export_weights(model, target, temporary=True)
            with torch.no_grad():
                model.build.bias += 1
            with self.assertRaises(ValueError):
                export_weights(model, target, temporary=True)


if __name__ == "__main__":
    unittest.main()
