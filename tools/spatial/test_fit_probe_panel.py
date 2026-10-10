"""Synthetic reduction/provenance checks; no inference, fitting or game simulation."""
import copy
import unittest

from fit_probe_panel import (V4_SHA256, compare_training_counts, translate_record,
                             validate_panel)
from label_audit import Reducer, validate_turn
from test_label_audit import fixture


def sample_models():
    game, metadata, rows, original = fixture()
    saved = copy.deepcopy(original)
    for values in [saved["ownPlans"], *(row["models"] for row in saved["rows"])]:
        initial, final = values.pop("v4"), values.pop("v9")
        values.update(initial=initial, final=final)
    reducer = Reducer()
    for _ in range(48):
        measured, plans, permutations = validate_turn(translate_record(saved), game, metadata["turns"][0], rows)
        reducer.add("train", measured, plans, permutations)
    report = reducer.report()["train"]["models"]
    return saved, {"initial": report["v4"], "final": report["v9"]}


def training_counts(models):
    report = {}
    for name, model in models.items():
        slices = {key: {"rows": item["rows"], "correct": item["exact_correct"], "agreement": item["exact_agreement"]}
                  for key, item in model["teacher_prefix"].items()}
        rows = slices["all"]["rows"] - slices["unforced"]["rows"]
        correct = slices["all"]["correct"] - slices["unforced"]["correct"]
        slices["forced"] = {"rows": rows, "correct": correct, "agreement": correct / rows if rows else None}
        count = model["root_counts"]["teacher_prefix_all_four_exact"]
        report[f"{name}_metrics"] = {"selected": {"slices": slices, "full_plans": 48, "exact_full_plans": count,
                                                      "exact_full_plan_agreement": count / 48}}
    return report


def lineage_fixture():
    games, roots, panel_games = [], [], []
    start = 0
    for index in range(48):
        turns = 36 if index == 47 else 96  # 4,548 full source turns, distinct from 48 scored roots.
        identity = {"id": f"train-{index // 2:02d}-p{index % 2}", "split": "train", "seed": 12000000 + index // 2,
                    "mapHash": f"{index // 2:064x}", "teacherSeat": index % 2, "opponentSpec": "v01", "mapIndex": index // 2}
        game = dict(identity, turns=turns, rowCount=4 * turns, cacheRowStart=start, rawSha256="a" * 64)
        games.append(game)
        roots.append(dict(identity, turn=1, cacheRowStart=start + 4))
        panel_games.append(dict(identity, status="complete", selectedTurn=1, file=f"games/{identity['id']}.json", sha256="b" * 64,
                                regeneratedRawSha256="a" * 64, reconstructedTurns=turns, reconstructedRows=4 * turns, selectedRoots=1, selectedRows=4))
        start += turns * 4
    data = {"manifest_sha256": "c" * 64, "games": games}
    selection = {"corpusManifestSha256": "c" * 64, "rootCount": 48, "rowCount": 192, "roots": roots}
    training = {"status": "complete", "initial_weights_sha256": V4_SHA256, "final_weights_sha256": "d" * 64,
                "total_optimizer_steps": 3072, "trained_decision_rows": 196608, "value_head_parameters_unchanged": True,
                "candidate_exported": False, "config": {"corpus_manifest_sha256": "c" * 64, "selection_sha256": "e" * 64,
                                                        "selection_manifest": copy.deepcopy(selection)}}
    panel = {"schemaVersion": 1, "experiment": "spatial-fit-probe-v1", "phase": "own-prefix-panel", "status": "complete",
             "townContext": False, "warmup": False, "budgetMs": 60000, "valueHeadByteExact": True,
             "weightsSha256": {"initial": V4_SHA256, "final": "d" * 64}, "finalPayloadFileSha256": "f" * 64,
             "corpusManifestSha256": "c" * 64, "selectionSha256": "e" * 64,
             "selectedRoots": 48, "selectedRows": 192, "reconstructedTurns": 4548, "reconstructedRows": 18192, "games": panel_games}
    return panel, selection, training, data


class FitProbePanelTests(unittest.TestCase):
    def test_reused_slots_are_private_and_teacherprefix_never_substitutes_own_plan(self):
        saved, models = sample_models()
        self.assertEqual(set(saved["ownPlans"]), {"initial", "final"})
        self.assertEqual(set(saved["rows"][0]["models"]), {"initial", "final"})
        self.assertEqual(models["initial"]["root_counts"]["teacher_prefix_all_four_exact"], 0)
        self.assertEqual(models["initial"]["root_counts"]["unordered_full_plan_equal"], 48)
        self.assertEqual(models["initial"]["root_counts"]["semantic_next_state_equal"], 48)
        self.assertEqual(models["final"]["root_counts"]["same_gain_different_state"], 48)
        self.assertEqual(models["final"]["root_counts"]["semantic_next_state_equal"], 0)

    def test_selected_metric_counts_include_forced_stage_slices_and_all_four(self):
        _, models = sample_models()
        training = training_counts(models)
        self.assertTrue(compare_training_counts(models, training)["initial"]["slice_counts_equal"])
        for category in ("forced", "stage0", "build_unforced", "all"):
            changed = copy.deepcopy(training)
            changed["final_metrics"]["selected"]["slices"][category]["correct"] += 1
            with self.assertRaisesRegex(ValueError, "counts differ"):
                compare_training_counts(models, changed)
        changed = copy.deepcopy(training)
        changed["initial_metrics"]["selected"]["exact_full_plans"] = 1
        with self.assertRaisesRegex(ValueError, "all-four"):
            compare_training_counts(models, changed)

    def test_fixed_panel_lineage_rejects_duplicate_missing_rawhash_weights_and_selection_drift(self):
        panel, selection, training, data = lineage_fixture()
        validate_panel(panel, selection, training, data, "e" * 64, "f" * 64)
        for kind in ("missing", "duplicate", "raw", "weights", "turn", "file", "context", "rows", "selection"):
            changed = copy.deepcopy(panel)
            if kind == "missing":
                changed["games"].pop()
            elif kind == "duplicate":
                changed["games"][1] = copy.deepcopy(changed["games"][0])
            elif kind == "raw":
                changed["games"][0]["regeneratedRawSha256"] = "0" * 64
            elif kind == "weights":
                changed["weightsSha256"]["final"] = "0" * 64
            elif kind == "turn":
                changed["games"][0]["selectedTurn"] = 2
            elif kind == "file":
                changed["games"][1]["file"] = changed["games"][0]["file"]
            elif kind == "context":
                changed["townContext"] = True
            elif kind == "rows":
                changed["selectedRows"] = 196
            else:
                changed["selectionSha256"] = "0" * 64
            with self.assertRaises(ValueError, msg=kind):
                validate_panel(changed, selection, training, data, "e" * 64, "f" * 64)


if __name__ == "__main__":
    unittest.main()
