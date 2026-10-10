"""Synthetic selection, metric, optimizer and checkpoint invariants for the probe."""
from __future__ import annotations

import base64
import copy
import hashlib
import re
import unittest
from unittest import mock

from train_spatial import ROOT, SpatialNet, np, torch
from model import WEIGHT_ORDER
from test_spatial_v4 import FixedHeadModel, fixture_game, scratch_directory
from v4_data import ROW_DTYPE, row_tensors
from v4_distill import policy_rows as original_policy_rows
from fit_probe import (CONFIG, V4_SHA256, RowSelection, evaluate, fresh_probe, partition_rows,
                       policy_rows, rank_turn, save_checkpoint, save_payload, select_roots, training_conflicts)
from v2_support import weight_hash


def frozen_v4_model():
    model = SpatialNet()
    source = (ROOT / "src/main/java/btk/bot/neural/SpatialV4Weights.java").read_text(encoding="utf-8")
    encoded = "".join(re.findall(r'"([A-Za-z0-9+/=]+)"', source))
    raw = np.frombuffer(base64.b64decode(encoded, validate=True), dtype="<f4")
    state, at = model.state_dict(), 0
    with torch.no_grad():
        for name in WEIGHT_ORDER:
            count = state[name].numel()
            state[name].copy_(torch.from_numpy(raw[at:at + count].copy()).reshape_as(state[name]))
            at += count
    assert at == 3439
    return model


def selection_fixture():
    games, maps, pieces = [], [], []
    for index in range(2):
        game, _, rows = fixture_game(12000000 + index)
        game.update(turns=3, rowCount=12, cacheRowStart=12 * index)
        games.append(game)
        maps.append({"split": "train", "seed": game["seed"], "mapIndex": index})
        game_rows = np.tile(rows, 3)
        game_rows["turn"] = np.repeat(np.arange(3), 4)
        pieces.append(game_rows)
    return games, maps, {"train": np.concatenate(pieces)}


def collision_group(members, digest="a" * 64):
    return {"input_sha256": digest,
            "rows": [{"split": split, "game": game, "turn": turn, "label": label}
                     for split, game, turn, label in members]}


class FitProbeTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)

    def test_rank_is_canonical_bytes_and_selection_is_order_independent(self):
        game_id = "train-12000000-0"
        expected = hashlib.sha256(b"spatial-fit-probe-v1\ntrain-12000000-0\n2\n").hexdigest()
        self.assertEqual(rank_turn(game_id, 2), expected)
        games, maps, mappings = selection_fixture()
        first, excluded = select_roots(games, maps, mappings, [])
        second, _ = select_roots(list(reversed(games)), list(reversed(maps)), mappings, [])
        self.assertEqual(first, second)
        self.assertEqual(excluded, [])
        for root, game in zip(first, games):
            best = min((rank_turn(game["id"], turn), turn) for turn in range(3))
            self.assertEqual((root["rankSha256"], root["turn"]), best)
            self.assertEqual(len(root["inputSha256"]), 4)
            self.assertEqual(root["cacheRowStart"], game["cacheRowStart"] + 4 * root["turn"])

    def test_conflicts_are_train_only_and_disqualify_whole_roots(self):
        games, maps, mappings = selection_fixture()
        first_id, second_id = games[0]["id"], games[1]["id"]
        consistent = collision_group([("train", first_id, 0, 7), ("validation", "heldout", 2, 19)])
        self.assertEqual(training_conflicts([consistent]), {})
        repeated = collision_group([("train", first_id, 0, 7), ("train", first_id, 1, 7)])
        self.assertEqual(training_conflicts([repeated]), {})
        roots, _ = select_roots(games, maps, mappings, [])
        banned = roots[0]["turn"]
        conflict = collision_group([("train", first_id, banned, 7), ("train", second_id, 0, 19)])
        result, exclusions = select_roots(games, maps, mappings, [conflict])
        self.assertNotEqual(result[0]["turn"], banned)
        self.assertNotEqual(result[1]["turn"], 0)
        self.assertEqual({(row["id"], row["turn"]) for row in exclusions}, {(first_id, banned), (second_id, 0)})

    def test_no_eligible_game_fails_without_replacement_or_smaller_coverage(self):
        games, maps, mappings = selection_fixture()
        first_id, second_id = games[0]["id"], games[1]["id"]
        groups = [collision_group([("train", first_id, turn, 7), ("train", second_id, 0, 19)], str(turn) * 64)
                  for turn in range(3)]
        with self.assertRaisesRegex(ValueError, "No conflict-free root"):
            select_roots(games, maps, mappings, groups)

    def test_selection_ignores_outcomes_and_rejects_missing_conditional_rows(self):
        games, maps, mappings = selection_fixture()
        before = select_roots(games, maps, mappings, [])
        changed = copy.deepcopy(games)
        for game in changed:
            game.update(score=[999999, 0], teacherAgreement=0., outcome="win")
        self.assertEqual(select_roots(changed, maps, mappings, []), before)
        at = before[0][0]["cacheRowStart"]
        mappings["train"]["stage"][at] = 3
        with self.assertRaisesRegex(ValueError, "four consecutive"):
            select_roots(games, maps, mappings, [])

    def test_partition_keeps_whole_roots_and_avoids_full_complement_copy(self):
        rows = np.zeros(200, dtype=ROW_DTYPE)
        rows["turn"] = np.repeat(np.arange(50), 4)
        selected = np.arange(192)
        subset, complement = partition_rows(rows, selected)
        self.assertEqual((len(subset), len(complement)), (192, 8))
        self.assertIs(complement.rows, rows)
        self.assertEqual(complement[:4]["turn"].tolist(), [48] * 4)
        self.assertEqual(subset[np.array([2, 0])].tobytes(), rows[[2, 0]].tobytes())
        for bad in (np.arange(191), np.zeros(192, dtype=int), np.roll(selected, 1)):
            with self.assertRaises(ValueError):
                partition_rows(rows, bad)

    def test_fresh_v4_adam_rng_frozen_value_and_exact_original_loss(self):
        model = frozen_v4_model()
        self.assertEqual(weight_hash(model), V4_SHA256)
        optimizer = fresh_probe(model)
        first_permutation = torch.randperm(192)
        optimizer = fresh_probe(model)
        self.assertTrue(torch.equal(first_permutation, torch.randperm(192)))
        self.assertEqual(len(optimizer.state), 0)
        self.assertEqual(sum(parameter.numel() for parameter in model.parameters() if parameter.requires_grad), 3424)
        self.assertEqual(optimizer.defaults["lr"], .001)
        self.assertEqual(optimizer.defaults["betas"], (.9, .999))
        self.assertEqual(optimizer.defaults["eps"], 1e-8)
        self.assertEqual(optimizer.defaults["weight_decay"], 0)
        self.assertIs(policy_rows, original_policy_rows)
        _, _, rows = fixture_game()
        obs, labels, stages = row_tensors(rows)
        original_head = {key: value.clone() for key, value in model.value.state_dict().items()}
        original_build = model.build.weight.detach().clone()
        loss, _, _ = policy_rows(model, obs, labels, stages)
        self.assertEqual(float(loss[2]), 0.)
        optimizer.zero_grad(set_to_none=True)
        loss.mean().backward()
        self.assertTrue(all(parameter.grad is None for parameter in model.value.parameters()))
        torch.nn.utils.clip_grad_norm_(model.parameters(), 1., error_if_nonfinite=True)
        optimizer.step()
        self.assertFalse(torch.equal(original_build, model.build.weight))
        self.assertTrue(all(torch.equal(value, original_head[key]) for key, value in model.value.state_dict().items()))
        self.assertEqual({int(value["step"]) for value in optimizer.state.values()}, {1})

    def test_detailed_metrics_separate_forced_stage_zero_and_teacher_prefix_plan(self):
        _, _, rows = fixture_game()
        model = FixedHeadModel()
        with torch.no_grad():
            model.build[0, 600] = 3.
        result = evaluate(model, rows)
        self.assertEqual(result["slices"]["forced"]["rows"], 1)
        self.assertEqual(result["slices"]["forced"]["cross_entropy"], 0.)
        self.assertEqual(result["slices"]["stage0"]["agreement"], 0.)
        self.assertEqual(result["slices"]["all"]["agreement"], .75)
        self.assertEqual(result["exact_full_plan_agreement"], 0.)
        self.assertEqual(result["slices"]["stage3"]["agreement"], 1.)
        altered = rows.copy()
        altered["stage"][0] = 3
        with self.assertRaisesRegex(ValueError, "stage order"):
            evaluate(model, altered)

    def test_atomic_checkpoint_contains_optimizer_rng_and_refuses_reuse(self):
        model = frozen_v4_model()
        optimizer = fresh_probe(model)
        with scratch_directory() as directory:
            target = directory / "initial.pt"
            save_checkpoint(target, model, optimizer, CONFIG, 0)
            saved = torch.load(target, weights_only=False, map_location="cpu")
            self.assertEqual(saved["iteration"], 0)
            self.assertEqual(saved["optimizer"]["state"], {})
            self.assertEqual(saved["config"], CONFIG)
            self.assertTrue({"torch_rng", "numpy_rng", "python_rng"}.issubset(saved))
            self.assertFalse((directory / "initial.pt.tmp").exists())
            with self.assertRaises(FileExistsError):
                save_checkpoint(target, model, optimizer, CONFIG, 0)
            payload = directory / "final-weights.base64"
            save_payload(payload, model)
            self.assertEqual(payload.read_text(), model.encoded_weights())
            with self.assertRaises(FileExistsError):
                save_payload(payload, model)
            with mock.patch("fit_probe.checkpoint", side_effect=RuntimeError("synthetic failure")):
                with self.assertRaises(RuntimeError):
                    save_checkpoint(directory / "failed.pt", model, optimizer, CONFIG, 512)
            self.assertFalse((directory / "failed.pt").exists())

    def test_fixed_budget_counts_and_checkpoint_schedule(self):
        self.assertEqual(CONFIG["epochs"] * (CONFIG["training_rows"] // CONFIG["minibatch_decisions"]), 3072)
        self.assertEqual(CONFIG["epochs"] * CONFIG["training_rows"], 196608)
        self.assertEqual(CONFIG["checkpoint_epochs"], [0, 512, 1024])
        self.assertEqual(CONFIG["metric_interval"], 64)
        self.assertEqual(CONFIG["root_count"] * 4, CONFIG["training_rows"])


if __name__ == "__main__":
    unittest.main()
