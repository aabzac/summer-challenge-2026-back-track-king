"""Synthetic label/plan accounting tests; no teacher/student games or training."""
from __future__ import annotations

import copy
import hashlib
import json
import unittest

from train_spatial import np
from test_spatial_v4 import fixture_game, scratch_directory
from v4_data import HEADER, ROW_DTYPE, digest_file
from label_audit import (Reducer, WEIGHTS, collision_audit, input_bytes, plan_comparison,
                         reduce_java, strict_json, validate_java_manifest, validate_turn, write_new)


def fixture():
    game, metadata, rows = fixture_game()
    rows["buildMask"][0, 19] = 1  # Both remaining teacher cells are legal at stage0.
    game["cacheRowStart"] = 0
    game["rawSha256"] = hashlib.sha256(HEADER + rows.tobytes()).hexdigest()
    teacher = {"cells": [7, 19], "disrupt": 5, "cost": 3,
               "nextStateSha256": "b" * 64, "gained": [10, 0], "scoreAfter": [10, 0]}
    record = {key: game[key] for key in ("id", "split", "seed", "mapHash", "teacherSeat", "opponentSpec")}
    record.update(turn=0, preStateSha256=metadata["turns"][0]["preStateSha256"],
                  semanticRootSha256="c" * 64, teacher=teacher, rows=[], ownPlans={}, permutations=[])
    for stage, row in enumerate(rows):
        label = int(row["label"])
        remaining = ([7, 19], [19], [600], [5])[stage]
        decision = {"stage": stage, "label": label, "prefix": metadata["turns"][0]["prefixes"][stage],
                    "paint": metadata["turns"][0]["paintBefore"][stage],
                    "stopped": metadata["turns"][0]["stoppedBefore"][stage],
                    "inputSha256": hashlib.sha256(input_bytes(row)).hexdigest(),
                    "remainingLabels": remaining, "models": {}}
        for model in ("v4", "v9"):
            greedy = (19 if model == "v4" else 600) if stage == 0 else label
            original_probability = [.2, .7, 1., .6][stage]
            mass = (.8 if model == "v4" else .3) if stage == 0 else original_probability
            decision["models"][model] = {"greedy": greedy,
                "legalCount": int(row["disruptMask" if stage == 3 else "buildMask"].sum()),
                "labelProbability": original_probability, "remainingProbability": mass,
                "greedyInRemaining": greedy in remaining}
        record["rows"].append(decision)
    for model, cells in (("v4", [19, 7]), ("v9", [7])):
        own = {"cells": cells, "disrupt": 5, "cost": 3 if model == "v4" else 1,
               "nextStateSha256": "b" * 64 if model == "v4" else "d" * 64,
               "gained": [10, 0], "scoreAfter": [10, 0], "elapsedMs": .1, "complete": True,
               "orderedBuildEqual": False, "buildSetEqual": model == "v4", "disruptEqual": True,
               "semanticEqual": model == "v4", "gainedEqual": True}
        record["ownPlans"][model] = own
    for cells in ([7, 19], [19, 7]):
        record["permutations"].append({"cells": cells, "nextStateSha256": "b" * 64,
            "gained": [10, 0], "scoreAfter": [10, 0], "semanticEqual": True, "gainedEqual": True})
    return game, metadata, rows, record


def manifest_fixture(game, filename="game.jsonl", file_hash="e" * 64):
    item = {key: game[key] for key in ("id", "split", "seed", "mapHash", "teacherSeat", "opponentSpec", "rowCount")}
    item.update(file=filename, sha256=file_hash, turnCount=game["turns"], regeneratedRawSha256=game["rawSha256"])
    return {"schemaVersion": 1, "experiment": "spatial-label-audit-v1", "status": "complete",
            "corpusManifestSha256": "f" * 64, "weightsSha256": {key.removeprefix("spatial-ppo-"): value for key, value in WEIGHTS.items()},
            "games": [item], "totalRows": game["rowCount"], "totalTurns": game["turns"]}


class LabelAuditTests(unittest.TestCase):
    def test_observed_byte_identity_includes_every_field_and_stage_excludes_label_rawturn(self):
        _, _, rows, _ = fixture()
        row = rows[0].copy()
        identity = input_bytes(row)
        altered = row.copy()
        altered["label"], altered["turn"] = 19, 77
        self.assertEqual(identity, input_bytes(altered))
        for name in ROW_DTYPE.names:
            if name in ("label", "turn"):
                continue
            changed = row.copy()
            if name == "stage":
                changed[name] = 1
            else:
                changed[name].reshape(-1)[0] += 1
            self.assertNotEqual(identity, input_bytes(changed), name)

    def test_conflict_ceilings_per_split_and_conservative_cross_split_evidence(self):
        _, _, rows, _ = fixture()
        train = np.repeat(rows[:1], 3)
        train["label"] = [7, 7, 19]
        train["turn"] = [0, 1, 2]
        validation = rows[:1].copy()
        validation["label"] = 19
        mappings = {"train": train, "validation": validation}
        lineage = {split: [{"game": split, "turn": i} for i in range(len(values))] for split, values in mappings.items()}
        summary, evidence = collision_audit(mappings, lineage)
        self.assertEqual(summary["groups"]["train"]["all"]["empirical_majority_label_agreement_ceiling"], 2 / 3)
        self.assertEqual(summary["groups"]["validation"]["all"]["empirical_majority_label_agreement_ceiling"], 1)
        self.assertEqual(summary["groups"]["all"]["all"]["empirical_majority_label_agreement_ceiling"], .5)
        self.assertEqual(summary["groups"]["all"]["stage0"]["rows_in_conflicting_groups"], 4)
        self.assertEqual(summary["cross_split_duplicate_groups"], 1)
        self.assertEqual(evidence[0]["label_counts"], {"7": 2, "19": 2})
        self.assertEqual(len(evidence[0]["rows"]), 4)

    def test_forced_stop_is_separate_and_cannot_conflict_legally(self):
        _, _, rows, _ = fixture()
        forced = np.repeat(rows[2:3], 2)
        summary, evidence = collision_audit({"train": forced}, {"train": [{}, {}]})
        self.assertEqual(summary["groups"]["train"]["all"]["rows"], 2)
        self.assertEqual(summary["groups"]["train"]["unforced"]["rows"], 0)
        self.assertIsNone(summary["groups"]["train"]["unforced"]["empirical_majority_label_agreement_ceiling"])
        self.assertFalse(evidence[0]["conflicting"])
        forced["label"][1] = 19
        with self.assertRaisesRegex(ValueError, "illegal collision label"):
            collision_audit({"train": forced}, {"train": [{}, {}]})

    def test_teacher_prefix_membership_is_separate_from_own_unordered_plan(self):
        game, metadata, rows, record = fixture()
        measured, plans, permutations = validate_turn(record, game, metadata["turns"][0], rows)
        self.assertFalse(measured["v4"][0]["exact"])
        self.assertTrue(measured["v4"][0]["remaining"])
        self.assertTrue(plans["v4"]["unordered_full_plan_equal"])
        self.assertFalse(plans["v4"]["ordered_full_plan_equal"])
        reducer = Reducer()
        reducer.add("train", measured, plans, permutations)
        report = reducer.report()["train"]["models"]["v4"]
        self.assertEqual(report["teacher_prefix"]["all"]["exact_agreement"], .75)
        self.assertEqual(report["teacher_prefix"]["all"]["remaining_set_agreement"], 1.)
        self.assertEqual(report["root_counts"]["teacher_prefix_all_four_exact"], 0)
        self.assertEqual(report["root_counts"]["unordered_full_plan_equal"], 1)

    def test_equal_gain_different_state_never_counted_as_semantic_equivalence(self):
        _, _, _, record = fixture()
        result = plan_comparison(record["teacher"], record["ownPlans"]["v9"])
        self.assertTrue(result["same_gain_different_state"])
        self.assertFalse(result["semantic_next_state_equal"])
        bad = copy.deepcopy(record["ownPlans"]["v9"])
        bad["semanticEqual"] = True
        with self.assertRaises(ValueError):
            plan_comparison(record["teacher"], bad)

    def test_turn_input_probability_forced_mask_and_permutation_corruption_rejected(self):
        game, metadata, rows, record = fixture()
        for kind in ("hash", "label", "forced", "nan", "mass", "illegal", "missing_permutation", "duplicate_permutation", "state_flag"):
            bad = copy.deepcopy(record)
            if kind == "hash":
                bad["rows"][0]["inputSha256"] = "0" * 64
            elif kind == "label":
                bad["rows"][0]["label"] = 19
            elif kind == "forced":
                bad["rows"][2]["models"]["v4"]["labelProbability"] = .5
            elif kind == "nan":
                bad["rows"][0]["models"]["v4"]["labelProbability"] = float("nan")
            elif kind == "mass":
                bad["rows"][0]["models"]["v4"]["remainingProbability"] = .1
            elif kind == "illegal":
                bad["rows"][0]["models"]["v4"]["greedy"] = 42
            elif kind == "missing_permutation":
                bad["permutations"].pop()
            elif kind == "duplicate_permutation":
                bad["permutations"][1] = copy.deepcopy(bad["permutations"][0])
            else:
                bad["permutations"][1]["semanticEqual"] = False
            with self.assertRaises(ValueError, msg=kind):
                validate_turn(bad, game, metadata["turns"][0], rows)

    def test_manifest_exact_coverage_payload_rawhash_and_no_duplicate_games(self):
        game, _, _, _ = fixture()
        original = manifest_fixture(game)
        validate_java_manifest(original, "f" * 64, [game])
        for kind in ("missing", "duplicate", "weights", "rawhash", "rows", "seat"):
            bad = copy.deepcopy(original)
            if kind == "missing":
                bad["games"] = []
            elif kind == "duplicate":
                bad["games"].append(copy.deepcopy(bad["games"][0]))
            elif kind == "weights":
                bad["weightsSha256"]["v4"] = "0" * 64
            elif kind == "rawhash":
                bad["games"][0]["regeneratedRawSha256"] = "0" * 64
            elif kind == "rows":
                bad["totalRows"] += 4
            else:
                bad["games"][0]["teacherSeat"] = 1
            with self.assertRaises(ValueError, msg=kind):
                validate_java_manifest(bad, "f" * 64, [game])

    def test_jsonl_reduction_requires_all_roots_and_exact_file_hashes(self):
        game, metadata, rows, record = fixture()
        with scratch_directory() as directory:
            game_file = directory / "game.jsonl"
            game_file.write_text(json.dumps(record) + "\n", encoding="utf-8")
            manifest_path = directory / "manifest.json"
            manifest = manifest_fixture(game, file_hash=digest_file(game_file))
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            data = {"manifest_sha256": "f" * 64, "games": [game]}
            result, identities = reduce_java(manifest_path, data, {"train": rows}, {game["id"]: metadata})
            self.assertEqual(result["all"]["models"]["v9"]["root_counts"]["same_gain_different_state"], 1)
            self.assertEqual(len(identities), 2)
            game_file.write_text("", encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "hash mismatch"):
                reduce_java(manifest_path, data, {"train": rows}, {game["id"]: metadata})
            manifest["games"][0]["sha256"] = digest_file(game_file)
            manifest_path.write_text(json.dumps(manifest), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "Incomplete Java"):
                reduce_java(manifest_path, data, {"train": rows}, {game["id"]: metadata})

    def test_write_once_and_strict_nonfinite_json(self):
        with scratch_directory() as directory:
            target = directory / "report.json"
            write_new(target, {"value": 1})
            with self.assertRaises(FileExistsError):
                write_new(target, {"value": 2})
            self.assertEqual(json.loads(target.read_text())["value"], 1)
        with self.assertRaises(ValueError):
            strict_json('{"value":NaN}')


if __name__ == "__main__":
    unittest.main()
