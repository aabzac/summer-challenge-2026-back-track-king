"""Small synthetic trace/target tests; no games, models, gradients or Torch import."""
from __future__ import annotations

from contextlib import contextmanager
import copy
import hashlib
import itertools
import json
import math
from pathlib import Path
import shutil
import unittest
from unittest import mock
import uuid

from learning_audit import (ROOT, LEARNERS, OPPONENTS, SEEDS, WEIGHT_FILES, analyze, digest,
                            future_targets, game_behaviour, historical_optimizer, load_games,
                            regression_metrics, validate_game, value_metrics)


def sha(text):
    return hashlib.sha256(text.encode()).hexdigest()


def write_json(path, value):
    path.write_text(json.dumps(value, allow_nan=False), encoding="utf-8")


@contextmanager
def scratch_directory():
    path = ROOT / "build" / "learning-audit-tests" / uuid.uuid4().hex
    path.mkdir(parents=True, exist_ok=False)
    try:
        yield path
    finally:
        shutil.rmtree(path)


def fixture_game(learner=LEARNERS[0], opponent="v01", seed=SEEDS[0], seat=0, gains=None):
    gains = [[10, 4], [4, 1]] if gains is None else gains
    init = f"synthetic-init-{seed}\n"
    plans = [{"cells": [i], "paddedCells": [i], "count": 1, "cost": 1, "disrupts": [], "error": None, "skipped": []} for i in (0, 1)]
    actions = ["PLACE_TRACKS 0 0", "PLACE_TRACKS 1 0"]
    metrics = {"ownedRails": [0, 0], "ownedDesiredPaths": [0, 0], "activeDesiredPaths": 0}
    score, income, turns = [0, 0], [0, 0], []
    for at, gained in enumerate(gains):
        before = score.copy()
        score = [score[i] + gained[i] for i in (0, 1)]
        block = f"synthetic-preturn-{at}\n"
        next_block = f"synthetic-preturn-{at + 1}\n"
        calls = [{"me": who, "spec": learner if who == seat else opponent,
                  "role": "learner" if who == seat else "opponent", "budgetMs": 60000,
                  "elapsedMs": 1., "complete": True, "legal": True, "actions": actions[who]} for who in (0, 1)]
        turns.append({"turn": at, "preStateSha256": sha(init + str(at) + "\n100\n" + block),
                      "preTurnBlockP0": block, "preTurnBlockP1": block + "seat1\n",
                      "scoreBefore": before, "incomeBefore": income.copy(), "metricsBefore": copy.deepcopy(metrics),
                      "learnerValueRaw": 0., "v5ValueRaw": 0., "diagnosticElapsedMs": 1.,
                      "calls": calls, "actions": actions.copy(), "expanded": copy.deepcopy(plans), "accepted": copy.deepcopy(plans),
                      "gained": gained.copy(), "scoreAfter": score.copy(), "incomeAfter": gained.copy(),
                      "metricsAfter": copy.deepcopy(metrics), "terminalAfter": at == len(gains) - 1,
                      "postStateSha256": sha(init + str(at + 1) + "\n100\n" + next_block),
                      "neutralised": [], "inkedRegions": [], "learnerUnusedPaint": 2, "learnerStoppedWithPaint": True})
        income = gained.copy()
    return {"id": f"{learner}-{opponent}-{seed}-{seat}", "learnerSpec": learner, "opponentSpec": opponent,
            "learnerSeat": seat, "seed": seed, "mapIndex": seed - SEEDS[0], "mapHash": sha(init),
            "initBlockP0": init, "maxTurns": 100, "status": "complete", "terminal": True,
            "turnCount": len(gains), "score": score, "turns": turns}


def fixture_corpus(directory):
    games, entries = [], []
    for learner, opponent, seed, seat in itertools.product(LEARNERS, OPPONENTS, SEEDS, (0, 1)):
        # Equal paired policy identities still get separate immutable game files.
        game = fixture_game(learner, opponent, seed, seat)
        filename = game["id"] + ".json"
        path = directory / filename
        write_json(path, game)
        fields = ("id", "learnerSpec", "opponentSpec", "learnerSeat", "mapIndex", "seed", "mapHash", "status", "turnCount", "score", "terminal")
        entries.append({**{key: game[key] for key in fields}, "file": filename, "sha256": digest(path)})
        games.append(game)
    exclusion_path = directory / "exclusions.json"
    write_json(exclusion_path, {"mapHashes": []})
    weights = {spec: sha(spec) for spec in WEIGHT_FILES}
    manifest = {"schemaVersion": 1, "experiment": "spatial-learning-audit", "status": "complete", "budgetMs": 60000,
                "warmup": False, "scheduledGames": 64, "exclusionsSha256": digest(exclusion_path), "excludedMapCount": 0,
                "params": {}, "weightsSha256": weights, "games": entries, "totalTurns": sum(len(g["turns"]) for g in games),
                "maps": [{"mapIndex": seed - SEEDS[0], "seed": seed, "mapHash": sha(f"synthetic-init-{seed}\n"),
                          "initBlockP0": f"synthetic-init-{seed}\n"} for seed in SEEDS]}
    manifest_path = directory / "manifest.json"
    write_json(manifest_path, manifest)
    return manifest_path, exclusion_path, manifest, games, weights


class LearningAuditTests(unittest.TestCase):
    def test_native_units_discount_and_terminal_bonus_once(self):
        game = validate_game(fixture_game())
        targets = future_targets(game)
        self.assertAlmostEqual(targets[1]["ppo_target"], .33)
        self.assertAlmostEqual(targets[0]["ppo_target"], .06 + .99 * .33)
        self.assertAlmostEqual(targets[0]["v5_target"], .0009)
        self.assertAlmostEqual(targets[1]["v5_target"], .0003)
        opposite = future_targets(validate_game(fixture_game(seat=1)))
        for left, right in zip(targets, opposite, strict=True):
            for field in ("ppo_target", "v5_target", "ppo_income", "v5_income"):
                self.assertAlmostEqual(left[field], -right[field])

    def test_tied_outcome_has_no_terminal_bonus(self):
        targets = future_targets(validate_game(fixture_game(gains=[[10, 4], [3, 9]])))
        self.assertAlmostEqual(targets[0]["ppo_target"], .06 - .99 * .06)
        self.assertAlmostEqual(targets[1]["ppo_target"], -.06)
        self.assertEqual(targets[0]["v5_target"], 0.)
        self.assertAlmostEqual(targets[1]["v5_target"], -.0006)

    def test_income_baseline_does_not_use_known_future_terminal(self):
        game = fixture_game(gains=[[2, 1]])
        game["turns"][0]["incomeBefore"] = [10, 5]
        targets = future_targets(validate_game(game))
        self.assertAlmostEqual(targets[0]["ppo_target"], .31)
        self.assertAlmostEqual(targets[0]["ppo_income"], 5 * (1 - .99 ** 100) / .01 / 100)
        self.assertEqual(targets[0]["v5_income"], .05)
        self.assertEqual(targets[0]["v5_target"], .0001)

    def test_score_terminal_hash_and_perspective_validation(self):
        for mutation in (
            lambda g: g["turns"][0]["scoreAfter"].__setitem__(0, 999),
            lambda g: g["turns"][0].__setitem__("terminalAfter", True),
            lambda g: g["turns"][1].__setitem__("preStateSha256", "0" * 64),
            lambda g: g.__setitem__("learnerSeat", 2),
            lambda g: g["turns"][0].__setitem__("learnerValueRaw", float("nan")),
            lambda g: g["turns"][0].__setitem__("v5ValueRaw", float("inf")),
            lambda g: g["turns"][1].__setitem__("incomeBefore", [0, 0]),
        ):
            with self.subTest(mutation=mutation):
                game = fixture_game()
                mutation(game)
                with self.assertRaises(ValueError):
                    validate_game(game)

    def test_rejected_truncated_or_changed_plan_is_not_analyzed(self):
        for mutation in (
            lambda g: g["turns"][0]["accepted"][0].__setitem__("cost", 2),
            lambda g: g["turns"][0]["calls"][0].__setitem__("complete", False),
            lambda g: g["turns"][0]["calls"][0].__setitem__("actions", "WAIT"),
            lambda g: g["turns"][0].__setitem__("learnerUnusedPaint", 3),
        ):
            game = fixture_game()
            mutation(game)
            with self.assertRaises(ValueError):
                validate_game(game)

    def test_regression_uses_equal_game_not_equal_turn_weights(self):
        metrics = regression_metrics([("short", [(0., 10.)]), ("long", [(0., 0.)] * 9)])
        self.assertEqual(metrics["games"], 2)
        self.assertEqual(metrics["observations"], 10)
        self.assertAlmostEqual(metrics["bias"], 5.)
        self.assertAlmostEqual(metrics["mae"], 5.)
        self.assertAlmostEqual(metrics["rmse"], math.sqrt(50))
        self.assertIsNone(metrics["pearson"])

    def test_undefined_correlations_and_nonfinite_metrics(self):
        constant = .8839752858399296
        metrics = regression_metrics([("g", [(float(i), constant) for i in range(17)])])
        self.assertIsNone(metrics["pearson"])
        self.assertIsNone(regression_metrics([])["pearson"])
        self.assertAlmostEqual(regression_metrics([("g", [(1., 2.), (2., 4.)])])["pearson"], 1.)
        with self.assertRaises(ValueError):
            regression_metrics([("g", [(1., float("nan"))])])
        with self.assertRaises(ValueError):
            regression_metrics([("g", [(1., 2.)]), ("g", [(2., 3.)])])

    def test_contracts_bins_and_missing_milestones_remain_distinct(self):
        game = fixture_game()
        targets = future_targets(game)
        metrics = value_metrics([(game, targets)])
        self.assertGreater(metrics["ppo"]["zero"]["rmse"], 100 * metrics["v5"]["zero"]["rmse"])
        self.assertEqual(value_metrics([(game, targets)], 25, 50)["ppo"]["model"]["games"], 0)
        behaviour = game_behaviour(game, targets)
        self.assertEqual(behaviour["first_score_after_turn"], 1)
        self.assertEqual(behaviour["income_milestones"][1]["income"], None)
        self.assertFalse(behaviour["income_milestones"][1]["observed"])
        self.assertEqual(behaviour["stopped_with_paint_turns"], 2)
        zero = game_behaviour(fixture_game(gains=[[0, 0]]), future_targets(fixture_game(gains=[[0, 0]])))
        self.assertTrue(zero["zero_income_game"])
        self.assertIsNone(zero["first_score_after_turn"])

    def test_complete_crossed_schema_lineage_and_paired_outputs(self):
        with scratch_directory() as path:
            manifest_path, exclusions, _, _, weights = fixture_corpus(path)
            with mock.patch("learning_audit.frozen_weight_hashes", return_value=weights):
                _, games, lineage = load_games(manifest_path, exclusions)
            result = analyze(games)
            self.assertEqual(result["games"], 64)
            self.assertEqual(result["independent_map_clusters"], 4)
            self.assertEqual(len(result["groups"]), 8)
            self.assertEqual(len(lineage["game_files_sha256"]), 64)
            self.assertEqual(len(result["paired_deltas"]), 12)
            for group in result["groups"]:
                self.assertEqual(group["outcomes"]["games"], 8)
                self.assertEqual(group["values_all_turns"]["v5"]["model"]["games"], 8)
            for comparison in result["paired_deltas"]:
                self.assertEqual(comparison["mean_score_gap_delta"], 0.)
                self.assertEqual(comparison["mean_result_score_delta"], 0.)

    def test_missing_pair_tamper_and_excluded_map_fail(self):
        with scratch_directory() as path:
            manifest_path, exclusions, manifest, _, weights = fixture_corpus(path)
            original = copy.deepcopy(manifest)
            with mock.patch("learning_audit.frozen_weight_hashes", return_value=weights):
                manifest["games"][1] = copy.deepcopy(manifest["games"][0])
                write_json(manifest_path, manifest)
                with self.assertRaises(ValueError):
                    load_games(manifest_path, exclusions)
                manifest = copy.deepcopy(original)
                manifest["games"][0]["sha256"] = "0" * 64
                write_json(manifest_path, manifest)
                with self.assertRaises(ValueError):
                    load_games(manifest_path, exclusions)
                manifest = copy.deepcopy(original)
                write_json(exclusions, {"mapHashes": [manifest["maps"][0]["mapHash"]]})
                manifest["exclusionsSha256"], manifest["excludedMapCount"] = digest(exclusions), 1
                write_json(manifest_path, manifest)
                with self.assertRaises(ValueError):
                    load_games(manifest_path, exclusions)
                write_json(exclusions, {"gamesPerMap": {}})
                manifest["exclusionsSha256"], manifest["excludedMapCount"] = digest(exclusions), 0
                write_json(manifest_path, manifest)
                with self.assertRaisesRegex(ValueError, "mapHashes schema"):
                    load_games(manifest_path, exclusions)

    def test_historical_summary_keeps_gradient_claim_bounded(self):
        with scratch_directory() as path:
            rows = [{"iteration": i, **{field: 1. for field in (
                "reward_mean", "reward_abs_mean", "return_mean", "return_variance", "value_mse_before", "value_mse_after",
                "value_mse_div_return_variance", "policy_loss", "entropy", "approx_kl", "clip_fraction", "gradient_norm")}} for i in range(1, 5)]
            file = path / "history.json"
            write_json(file, {"status": "complete", "iterations": rows, "config": {
                "reward_scale": 100., "value_coefficient": .5, "entropy_coefficient": .01, "gradient_clip": .5}})
            result = historical_optimizer(file, 4)
            self.assertEqual(result["sha256"], digest(file))
            self.assertEqual(len(result["quarter_means"]), 4)
            self.assertEqual(result["iterations_with_mean_preclip_norm_above_cap"], 4)
            self.assertTrue(any("not per-component" in text for text in result["limits"]))


if __name__ == "__main__":
    unittest.main()
