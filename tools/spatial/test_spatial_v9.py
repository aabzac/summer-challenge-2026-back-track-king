"""Synthetic V9 representation/recipe regressions; no real-data training or play."""
from __future__ import annotations

import copy
import unittest
from unittest import mock

from train_spatial import np, torch
from model import SpatialNet, WEIGHT_ORDER, tensor_observations
from model_v9 import SpatialV9Net, ordered_town_context, town_kernel
from test_spatial_v3 import load_frozen_v2_model
from test_spatial_v4 import fixture_game, scratch_directory
from v2_support import weight_hash
from v3_support import WARM_START_SHA256, java_payload_hash
from v4_data import parity_observations, row_tensors
from v4_distill import CONFIG as V4_CONFIG, fresh_student, policy_rows
from v9_distill import (CONFIG, RoutedBridge, export_v9_weights, historical_integrity,
                        verify_context_off, verify_v4_recipe)


def fixture_observations():
    _, _, rows = fixture_game()
    rows["planes"][:, 0] = 0
    rows["planes"][:, 0, :8, :20] = 1
    rows["townCells"][:, :2] = [0, 19]
    rows["towns"][:, 0, 0] = 1
    rows["towns"][:, 1, 1] = 2
    return rows


class SyntheticBridge:
    def __init__(self, model):
        self.new = copy.deepcopy(model)
        self.old = SpatialNet()
        self.old.load_state_dict(model.state_dict())
        self.calls = []

    def request(self, **message):
        self.calls.append(message)
        self.new.town_context = message["townContext"]
        model = self.old if message["legacy"] else self.new
        with torch.no_grad():
            build, ink, value = model(tensor_observations(message["observations"]))
        return {"outputs": [{"buildLogits": build[i].tolist(), "disruptLogits": ink[i].tolist(),
                             "value": float(value[i])} for i in range(len(value))]}


class V9Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)

    def test_kernel_count_distance_absent_towns_and_padding(self):
        towns = torch.full((2, 12), -1, dtype=torch.int64)
        towns[0, :2] = torch.tensor([0, 31])
        valid = torch.zeros(2, 1, 20, 30)
        valid[:, :, :2, :3] = 1
        kernel = town_kernel(towns, valid)
        self.assertEqual(kernel.shape, (2, 12, 600))
        self.assertEqual(kernel.dtype, torch.float32)
        self.assertEqual(float(kernel[0, 0, 0]), .5)
        self.assertEqual(float(kernel[0, 1, 0]), float(np.float32(1 / 6)))
        self.assertEqual(float(kernel[0, 0, 31]), float(np.float32(1 / 6)))
        self.assertEqual(float(kernel[0, 1, 31]), .5)
        self.assertEqual(float(kernel[0, :, 1].sum()), .5)  # No distance-sum normalization.
        self.assertEqual(int(torch.count_nonzero(kernel[:, :, 3])), 0)
        self.assertEqual(int(torch.count_nonzero(kernel[:, 2:])), 0)
        self.assertEqual(int(torch.count_nonzero(kernel[1])), 0)

    def test_context_order_and_gradient_reaches_distant_town_embedding(self):
        embeddings = torch.zeros(1, 8, 12)
        embeddings[0, 0, :3] = torch.tensor([1e20, -1e20, 3.])
        kernel = torch.ones(1, 12, 600)
        self.assertEqual(float(ordered_town_context(embeddings, kernel)[0, 0, 0]), 3.)
        embeddings.requires_grad_(True)
        towns = torch.full((1, 12), -1, dtype=torch.int64)
        towns[0, 0] = 599
        geometry = town_kernel(towns, torch.ones(1, 1, 20, 30))
        ordered_town_context(embeddings, geometry)[0, 0, 0].backward()
        self.assertEqual(float(embeddings.grad[0, 0, 0]), float(np.float32(1 / 49)))
        self.assertEqual(int(torch.count_nonzero(embeddings.grad[:, :, 1:])), 0)

    def test_context_off_and_zero_towns_exact_model_state_and_export_order(self):
        original = load_frozen_v2_model()
        model = SpatialV9Net(False)
        model.load_state_dict(original.state_dict())
        self.assertEqual(tuple(model.state_dict()), WEIGHT_ORDER)
        self.assertEqual(sum(p.numel() for p in model.parameters()), 3439)
        self.assertEqual(model.encoded_weights(), original.encoded_weights())
        obs, _, _ = row_tensors(fixture_observations())
        with torch.no_grad():
            self.assertTrue(all(torch.equal(a, b) for a, b in zip(model(obs), original(obs))))
            obs["townCells"].fill_(-1)
            model.town_context = True
            self.assertTrue(all(torch.equal(a, b) for a, b in zip(model(obs), original(obs))))

    def test_distant_feature_breaks_local_alias_without_removing_local_scatter(self):
        model = SpatialV9Net()
        with torch.no_grad():
            for parameter in model.parameters():
                parameter.zero_()
            model.town.weight[0, 0] = 1
            model.build.weight[0, 0] = 1
        obs, _, _ = row_tensors(fixture_observations())
        obs["towns"].zero_()
        obs["towns"][:, 1, 0] = 2
        with torch.no_grad():
            build = model(obs)[0]
            self.assertGreater(float(build[0, 1]), float(build[0, 0]))
            self.assertEqual(float(build[0, 19]), 3.)  # Linear embedding2 local + context2/2.
            model.town_context = False
            off = model(obs)[0]
            self.assertEqual(float(off[0, 0]), float(off[0, 1]))
            self.assertEqual(float(off[0, 19]), 2.)

    def test_fixed_adam_recipe_masks_frozen_head_and_finite_gradients(self):
        original = load_frozen_v2_model()
        model = SpatialV9Net()
        model.load_state_dict(original.state_dict())
        optimizer = fresh_student(model)
        self.assertEqual(weight_hash(model), WARM_START_SHA256)
        self.assertEqual(sum(p.numel() for p in model.parameters() if p.requires_grad), 3424)
        self.assertEqual(len(optimizer.state), 0)
        old_value = {key: val.clone() for key, val in model.value.state_dict().items()}
        old_town = model.town.weight.detach().clone()
        obs, labels, stages = row_tensors(fixture_observations())
        losses, predictions, unforced = policy_rows(model, obs, labels, stages)
        self.assertEqual(float(losses[2]), 0.)
        self.assertFalse(bool(unforced[2]))
        self.assertTrue(obs["buildMask"].gather(1, predictions[:, None])[stages < 3].all())
        optimizer.zero_grad(set_to_none=True)
        losses.mean().backward()
        self.assertTrue(all(p.grad is None for p in model.value.parameters()))
        torch.nn.utils.clip_grad_norm_(model.parameters(), CONFIG["gradient_clip"], error_if_nonfinite=True)
        optimizer.step()
        self.assertFalse(torch.equal(model.town.weight, old_town))
        self.assertTrue(all(torch.isfinite(p).all() for p in model.parameters()))
        self.assertTrue(all(torch.equal(old_value[key], val) for key, val in model.value.state_dict().items()))
        self.assertEqual({int(state["step"]) for state in optimizer.state.values()}, {1})
        for key in V4_CONFIG:
            if key != "schema":
                self.assertEqual(CONFIG[key], V4_CONFIG[key])

    def test_off_parity_routing_preserves_rng_and_rejects_java_mismatch(self):
        model = SpatialV9Net()
        model.load_state_dict(load_frozen_v2_model().state_dict())
        bridge = SyntheticBridge(model)
        panel = parity_observations(np.tile(fixture_observations(), 6))
        with scratch_directory() as directory:
            before = torch.get_rng_state().clone()
            result = verify_context_off(bridge, model, panel, directory, "fixture")
            self.assertTrue(torch.equal(before, torch.get_rng_state()))
            self.assertTrue(result["python_bitwise_equal"] and result["java_bitwise_equal"])
            self.assertEqual([(row["townContext"], row["legacy"]) for row in bridge.calls], [(False, False), (False, True)])
            bad = SyntheticBridge(model)
            bad.old.build.bias.data += .01
            with self.assertRaises(AssertionError):
                verify_context_off(bad, model, panel, directory, "bad")

    def test_export_targets_only_new_class_and_roundtrips_exact_payload(self):
        model = SpatialV9Net()
        model.load_state_dict(load_frozen_v2_model().state_dict())
        with scratch_directory() as directory:
            old = directory / "SpatialV4Weights.java"
            old.write_bytes(b"frozen")
            with self.assertRaises(ValueError):
                export_v9_weights(model, old)
            self.assertEqual(old.read_bytes(), b"frozen")
            target = directory / "SpatialV9Weights.java"
            export_v9_weights(model, target)
            self.assertEqual(java_payload_hash(target), WARM_START_SHA256)

    def test_recipe_guard_checks_exact_old_cache_and_checkpoint(self):
        data = {"splits": {"train": {"rows": 18192}, "validation": {"rows": 4448}}}
        reference = {"status": "complete", "initial_weights_sha256": WARM_START_SHA256,
                     "config": {**V4_CONFIG, "data": data, "cache_sha256": "same", "warm_start_checkpoint_sha256": "same"}}
        with mock.patch("v9_distill.read_json", return_value=reference), mock.patch("v9_distill.digest_file", return_value="same"):
            from pathlib import Path
            self.assertTrue(verify_v4_recipe(data, Path("fixture"), "fixture")["exact_recipe_cache_split_warm_start"])
            altered = copy.deepcopy(data)
            altered["splits"]["train"]["rows"] -= 4
            with self.assertRaises(AssertionError):
                verify_v4_recipe(altered, Path("fixture"), "fixture")
            reference["config"]["learning_rate"] = .002
            with self.assertRaises(AssertionError):
                verify_v4_recipe(data, Path("fixture"), "fixture")

    def test_historical_guard_ignores_extended_params_but_protects_old_math(self):
        evidence = {"status": "complete", "final_weights_sha256": "payload", "config": {"source_sha256": {
            "src/main/java/btk/bot/Params.java": "previous-params",
            "tools/spatial/model.py": "same"}}}
        with mock.patch("v9_distill.read_json", return_value=evidence), mock.patch("v9_distill.digest_file", return_value="same"), \
                mock.patch("v9_distill.java_payload_hash", return_value="payload"):
            self.assertEqual(len(historical_integrity()), 5)
            evidence["config"]["source_sha256"]["tools/spatial/model.py"] = "changed"
            with self.assertRaises(AssertionError):
                historical_integrity()


if __name__ == "__main__":
    unittest.main()
