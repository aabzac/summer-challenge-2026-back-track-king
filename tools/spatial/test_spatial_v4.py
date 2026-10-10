"""Synthetic corpus/loss regressions; no teacher games or experiment gradients."""
from __future__ import annotations

from contextlib import contextmanager
import copy
import gzip
import hashlib
import io
import json
from pathlib import Path
import shutil
import unittest
from unittest import mock
import uuid

from train_spatial import ROOT, np, torch
from test_spatial_v3 import load_frozen_v2_model
from v2_support import weight_hash
from v3_support import WARM_START_SHA256
from v4_data import (HEADER, OPPONENTS, ROW_BYTES, ROW_DTYPE, SPLITS, digest_file, inflate_game,
                     parity_observations, prepare_cache, row_tensors, validate_layout,
                     validate_metadata, validate_rows, verify_cache)
from v4_distill import CONFIG, evaluate, export_v4_weights, fresh_student, policy_rows, verify_freeze, verify_parity


@contextmanager
def scratch_directory():
    path = ROOT / "build" / "spatial-v4-tests" / uuid.uuid4().hex
    path.mkdir(parents=True, exist_ok=False)
    try:
        yield path
    finally:
        shutil.rmtree(path)


def fixture_game(seed=12000000, seat=0, split="train", opponent="v01"):
    game_id = f"{split}-{seed}-{seat}"
    game = {"id": game_id, "split": split, "seed": seed, "teacherSeat": seat,
            "mapHash": hashlib.sha256(str(seed).encode()).hexdigest(), "opponentSpec": opponent,
            "binary": game_id + ".bin.gz", "metadata": game_id + ".json", "terminal": True,
            "turns": 1, "rowCount": 4, "uncompressedBytes": 16 + 4 * ROW_BYTES}
    teacher = {"cells": [7, 19], "disrupt": 5, "cost": 3}
    other = {"cells": [], "disrupt": -1, "cost": 0}
    audit = {"turn": 0, "rowStart": 0, "labels": [7, 19, 600, 5],
             "prefixes": [[], [7], [7, 19], [7, 19]], "paintBefore": [3, 2, 0, 0],
             "stoppedBefore": [False, False, False, True],
             "teacherActions": "PLACE 7;PLACE 19;DISRUPT 5", "opponentActions": "WAIT",
             "teacherExpanded": teacher, "opponentExpanded": other,
             "acceptedTeacher": copy.deepcopy(teacher), "acceptedOpponent": copy.deepcopy(other),
             "gained": [10, 0], "scoreAfter": [10, 0], "terminalAfter": True,
             "preStateSha256": "a" * 64,
             "calls": [{"me": seat, "role": "teacher", "spec": "v09", "complete": True,
                        "legal": True, "budgetMs": 60000, "elapsedMs": 1.},
                       {"me": 1 - seat, "role": "opponent", "spec": opponent, "complete": True,
                        "legal": True, "budgetMs": 60000, "elapsedMs": 1.}]}
    metadata = {**game, "teacherSpec": "v09", "status": "complete", "turnCount": 1,
                "score": [10, 0], "turns": [audit]}
    rows = np.zeros(4, dtype=ROW_DTYPE)
    rows["planes"][:, 0] = 1.
    rows["planes"][:, 1, 0, 7] = 1.
    rows["townCells"] = -1
    rows["regionIds"] = 5
    rows["scalars"][:, 1] = np.arange(4) / 3
    rows["label"] = audit["labels"]
    rows["stage"] = np.arange(4)
    rows["buildMask"][:, 600] = 1
    rows["buildMask"][0, 7] = rows["buildMask"][1, 19] = 1
    rows["disruptMask"][:, 600] = rows["disruptMask"][:, 5] = 1
    return game, metadata, rows


def fixture_layout():
    games, maps = [], []
    for split, (start, count) in SPLITS.items():
        for index in range(count):
            for seat in (0, 1):
                game, _, _ = fixture_game(start + index, seat, split, OPPONENTS[index % 3])
                games.append(game)
            maps.append({name: game[name] for name in ("split", "seed", "mapHash", "opponentSpec")})
    return {"schemaVersion": 1, "format": "BTKSD001", "mode": "corpus", "status": "complete",
            "exclusionsSha256": "f" * 64, "excludedMapCount": 0,
            "rows": 240, "totalTurns": 60, "maps": maps, "games": games}


class FixedHeadModel(torch.nn.Module):
    def __init__(self):
        super().__init__()
        self.build = torch.nn.Parameter(torch.zeros(4, 601))
        self.disrupt = torch.nn.Parameter(torch.zeros(4, 601))

    def forward(self, obs):
        return self.build, self.disrupt, torch.zeros(4)


class V4Tests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        torch.set_num_threads(2)

    def test_exact_binary_roundtrip_and_corrupt_header_size_hash(self):
        game, metadata, rows = fixture_game()
        raw = HEADER + rows.tobytes()
        game["rawSha256"] = hashlib.sha256(raw).hexdigest()
        self.assertEqual(ROW_DTYPE.itemsize, 63638)
        self.assertEqual(ROW_DTYPE.fields["label"][1], 63626)
        with scratch_directory() as directory:
            binary = directory / "game.bin.gz"
            binary.write_bytes(gzip.compress(raw))
            output = io.BytesIO()
            inflate_game(binary, output, game)
            restored = np.frombuffer(output.getvalue(), dtype=ROW_DTYPE)
            self.assertEqual(restored.tobytes(), rows.tobytes())
            validate_metadata(game, metadata)
            validate_rows(restored, game, metadata)
            for altered in (b"WRONG001" + raw[8:], raw[:-1], raw + b"x", raw[:16] + b"x" + raw[17:]):
                binary.write_bytes(gzip.compress(altered))
                with self.assertRaises(ValueError):
                    inflate_game(binary, io.BytesIO(), game)

    def test_fixed_whole_map_split_and_exclusion_lineage(self):
        manifest = fixture_layout()
        self.assertEqual(validate_layout(manifest, set(), "f" * 64)["games"], 60)
        for corruption in ("missing", "overlap", "pair", "opponent", "exclusion"):
            bad = copy.deepcopy(manifest)
            excluded = set()
            if corruption == "missing":
                bad["games"].pop()
            elif corruption == "overlap":
                for game in bad["games"][-2:]:
                    game["mapHash"] = bad["games"][0]["mapHash"]
            elif corruption == "pair":
                bad["games"][1]["mapHash"] = "z" * 64
            elif corruption == "opponent":
                bad["games"][0]["opponentSpec"] = "v09"
            else:
                excluded.add(bad["games"][0]["mapHash"])
                bad["excludedMapCount"] = 1
            with self.assertRaises(ValueError):
                validate_layout(bad, excluded, "f" * 64)

    def test_ordered_actions_exact_accepted_plan_and_complete_calls(self):
        game, metadata, _ = fixture_game()
        validate_metadata(game, metadata)
        for corruption in ("order", "accepted_teacher", "accepted_opponent", "call", "prestate", "prefix"):
            bad = copy.deepcopy(metadata)
            audit = bad["turns"][0]
            if corruption == "order":
                audit["labels"][:2] = [19, 7]
            elif corruption == "accepted_teacher":
                audit["acceptedTeacher"]["cells"].pop()
            elif corruption == "accepted_opponent":
                audit["acceptedOpponent"]["disrupt"] = 1
            elif corruption == "call":
                audit["calls"][0]["complete"] = False
            elif corruption == "prestate":
                audit["preStateSha256"] = ""
            else:
                audit["prefixes"][2] = [19, 7]
            with self.assertRaises(ValueError):
                validate_metadata(game, bad)

    def test_binary_finiteness_masks_labels_and_stage_order(self):
        game, metadata, rows = fixture_game()
        for corruption in ("nan", "illegal", "mask_byte", "empty", "stage", "label", "region"):
            bad = rows.copy()
            if corruption == "nan":
                bad["planes"][0, 0, 0, 0] = np.nan
            elif corruption == "illegal":
                bad["buildMask"][0, 7] = 0
            elif corruption == "mask_byte":
                bad["buildMask"][0, 7] = 2
            elif corruption == "empty":
                bad["disruptMask"][0] = 0
            elif corruption == "stage":
                bad["stage"][0] = 3
            elif corruption == "label":
                bad["label"][0] = 8
            else:
                bad["regionIds"][0, 0] = 600
            with self.assertRaises(ValueError):
                validate_rows(bad, game, metadata)

    def test_cache_roundtrip_hash_guards_and_no_reuse(self):
        # Two complete synthetic files exercise both memmap splits; the separate
        # layout test covers all 60 fixed game headers without large fixture I/O.
        with scratch_directory() as directory:
            corpus = directory / "corpus"
            corpus.mkdir()
            cache = directory / "cache"
            excluded = directory / "exclusions.json"
            excluded.write_text(json.dumps({"gamesPerMap": {}}), encoding="utf-8")
            games = []
            for split, seed in (("train", 12000000), ("validation", 12100000)):
                game, metadata, rows = fixture_game(seed, split=split)
                raw = HEADER + rows.tobytes()
                (corpus / game["binary"]).write_bytes(gzip.compress(raw))
                (corpus / game["metadata"]).write_text(json.dumps(metadata), encoding="utf-8")
                game.update(rawSha256=hashlib.sha256(raw).hexdigest(), sha256=digest_file(corpus / game["binary"]),
                            metadataSha256=digest_file(corpus / game["metadata"]))
                games.append(game)
            (corpus / "manifest.json").write_text(json.dumps({"games": games}), encoding="utf-8")
            with mock.patch("v4_data.validate_layout", return_value={"fixture_only": True}):
                result = prepare_cache(corpus, cache, excluded)
                self.assertEqual(verify_cache(cache), result)
                with self.assertRaises(FileExistsError):
                    prepare_cache(corpus, cache, excluded)
            self.assertEqual(result["splits"]["train"]["bytes"], 4 * ROW_BYTES)
            with (cache / "train.bin").open("r+b") as stream:
                stream.write(b"x")
            with self.assertRaisesRegex(ValueError, "cache changed"):
                verify_cache(cache)

    def test_active_head_four_decision_mean_and_singleton_gradient(self):
        _, _, rows = fixture_game()
        obs, labels, stages = row_tensors(rows)
        model = FixedHeadModel()
        losses, _, unforced = policy_rows(model, obs, labels, stages)
        torch.testing.assert_close(losses, torch.tensor([np.log(2), np.log(2), 0., np.log(2)], dtype=torch.float32))
        self.assertEqual(unforced.tolist(), [True, True, False, True])
        losses.mean().backward()
        self.assertAlmostEqual(float(model.build.grad[0, 7]), -.125)
        self.assertTrue((model.build.grad[2:] == 0).all())
        self.assertTrue((model.disrupt.grad[:3] == 0).all())
        self.assertTrue((model.build.grad[:, 399] == 0).all())
        labels[0] = 399
        with self.assertRaisesRegex(ValueError, "illegal"):
            policy_rows(model, obs, labels, stages)

    def test_fresh_v2_warm_start_optimizer_changes_policy_preserves_value_and_history(self):
        model = load_frozen_v2_model()
        old_names = ("SpatialWeights.java", "SpatialInitialWeights.java", "SpatialV2Weights.java", "SpatialV3Weights.java")
        old_paths = [ROOT / "src/main/java/btk/bot/neural" / name for name in old_names]
        hashes = {path: digest_file(path) for path in old_paths}
        optimizer = fresh_student(model)
        self.assertEqual(weight_hash(model), WARM_START_SHA256)
        self.assertEqual(len(optimizer.state), 0)
        self.assertEqual(sum(p.numel() for p in model.parameters() if p.requires_grad), 3424)
        value_before = {name: tensor.clone() for name, tensor in model.value.state_dict().items()}
        trunk_before = model.stem.weight.detach().clone()
        _, _, rows = fixture_game()
        obs, labels, stages = row_tensors(rows)
        losses, _, _ = policy_rows(model, obs, labels, stages)
        optimizer.zero_grad(set_to_none=True)
        losses.mean().backward()
        torch.nn.utils.clip_grad_norm_(model.parameters(), CONFIG["gradient_clip"], error_if_nonfinite=True)
        optimizer.step()
        self.assertFalse(torch.equal(trunk_before, model.stem.weight))
        for name, tensor in model.value.state_dict().items():
            self.assertTrue(torch.equal(value_before[name], tensor))
        self.assertTrue(all(p.grad is None for p in model.value.parameters()))
        with scratch_directory() as directory:
            export_v4_weights(model, directory / "SpatialV4Weights.java")
            self.assertIn("public final class SpatialV4Weights", (directory / "SpatialV4Weights.java").read_text())
            for name in old_names:
                with self.assertRaises(ValueError):
                    export_v4_weights(model, directory / name)
        self.assertEqual({path: digest_file(path) for path in old_paths}, hashes)

    def test_teacher_prefix_metrics_distinguish_forced_rows_and_exact_plan(self):
        _, _, rows = fixture_game()
        model = FixedHeadModel()
        with torch.no_grad():
            model.build[1, 600] = 1.
        result = evaluate(model, rows)
        self.assertEqual(result["slices"]["all"]["correct"], 3)
        self.assertEqual(result["slices"]["unforced"]["rows"], 3)
        self.assertEqual(result["slices"]["unforced"]["correct"], 2)
        self.assertEqual(result["exact_full_plans"], 0)

    def test_frozen_input_guard_and_nonfinite_java_parity_fail(self):
        model = load_frozen_v2_model()
        _, _, rows = fixture_game()
        panel = parity_observations(np.tile(rows, 6))
        class NanBridge:
            def request(self, **message):
                return {"outputs": [{"buildLogits": [float("nan")] * 601,
                                     "disruptLogits": [0.] * 601, "value": 0.} for _ in message["observations"]]}
        with scratch_directory() as directory:
            frozen_file = directory / "fixture.txt"
            frozen_file.write_text("first", encoding="utf-8")
            freeze = directory / "freeze.json"
            freeze.write_text(json.dumps({"files": {str(frozen_file): digest_file(frozen_file)}}), encoding="utf-8")
            self.assertEqual(verify_freeze(freeze)["verified_files"], 1)
            frozen_file.write_text("changed", encoding="utf-8")
            with self.assertRaisesRegex(AssertionError, "Frozen input changed"):
                verify_freeze(freeze)
            with self.assertRaises(FloatingPointError):
                verify_parity(NanBridge(), model, panel, directory, "fixture")


if __name__ == "__main__":
    unittest.main()
