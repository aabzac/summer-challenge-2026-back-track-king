"""Reduce the fixed 48-root Java fit panel; no model calls, fitting, or Python rules."""
from __future__ import annotations

import argparse
import base64
import copy
import hashlib
from pathlib import Path
import time

from fit_probe import V4_SHA256, audit_lineage, validate_selection
from label_audit import (Reducer, hash_string, input_bytes, load_corpus, require,
                         strict_json, validate_turn, write_new)
from v4_data import digest_file, local_file, verify_cache

MODEL_SLOTS = {"initial": "v4", "final": "v9"}  # Reused reducer slots only; neither model has V9 context.
IDENTITY = ("id", "split", "seed", "mapHash", "teacherSeat", "opponentSpec")


def read(path):
    return strict_json(Path(path).read_text(encoding="utf-8-sig"))


def translate_record(record):
    """Translate only a private copy, keeping saved initial/final identities unambiguous."""
    translated = copy.deepcopy(record)
    for values in [translated["ownPlans"], *(row["models"] for row in translated["rows"])]:
        require(set(values) == set(MODEL_SLOTS), "Expected initial/final model identities")
        converted = {slot: values[name] for name, slot in MODEL_SLOTS.items()}
        values.clear()
        values.update(converted)
    return translated


def validate_panel(panel, selection, training, data, selection_sha, payload_file_sha):
    require(panel.get("schemaVersion") == 1 and panel.get("experiment") == "spatial-fit-probe-v1"
            and panel.get("phase") == "own-prefix-panel" and panel.get("status") == "complete", "Incomplete/unsupported Java fit panel")
    require(panel["townContext"] is False and panel["warmup"] is False and panel["budgetMs"] == 60000
            and panel["valueHeadByteExact"] is True, "Java panel changed the fixed runtime/value-head contract")
    require(training["status"] == "complete" and training["initial_weights_sha256"] == V4_SHA256
            and training["total_optimizer_steps"] == 3072 and training["trained_decision_rows"] == 196608
            and training["value_head_parameters_unchanged"] is True and training["candidate_exported"] is False,
            "Incomplete/different fixed fitting run")
    weights = {"initial": V4_SHA256, "final": hash_string(training["final_weights_sha256"])}
    require(panel["weightsSha256"] == weights and panel["finalPayloadFileSha256"] == payload_file_sha, "Java/training payload identities differ")
    require(panel["corpusManifestSha256"] == selection["corpusManifestSha256"] == data["manifest_sha256"]
            == training["config"]["corpus_manifest_sha256"], "Panel/selection/training corpus differs")
    require(panel["selectionSha256"] == selection_sha == training["config"]["selection_sha256"]
            and training["config"]["selection_manifest"] == selection, "Panel and trainer used different selected roots")
    require(selection["rootCount"] == 48 and selection["rowCount"] == 192
            and panel["selectedRoots"] == 48 and panel["selectedRows"] == 192
            and panel["reconstructedTurns"] == 4548 and panel["reconstructedRows"] == 18192, "Fixed panel coverage mismatch")
    source_games = sorted((game for game in data["games"] if game["split"] == "train"), key=lambda game: (game["seed"], game["teacherSeat"]))
    roots, games = selection["roots"], panel["games"]
    require(len(source_games) == len(roots) == len(games) == 48 and len({game["id"] for game in games}) == 48,
            "Missing/duplicate Java fit games")
    files = set()
    for source, selected, game in zip(source_games, roots, games):
        for key in IDENTITY:
            require(game[key] == selected[key] == source[key], f"Selected game identity differs: {key}")
        require(game["mapIndex"] == selected["mapIndex"] == source["mapIndex"] and game["status"] == "complete"
                and game["selectedTurn"] == selected["turn"] and 0 <= selected["turn"] < source["turns"]
                and selected["cacheRowStart"] == source["cacheRowStart"] + 4 * selected["turn"], "Selected root lineage mismatch")
        require(game["regeneratedRawSha256"] == source["rawSha256"] and game["reconstructedTurns"] == source["turns"]
                and game["reconstructedRows"] == source["rowCount"] and game["selectedRoots"] == 1 and game["selectedRows"] == 4,
                "Java did not reconstruct the complete original game")
        require(game["file"] not in files, "Two games share an output artifact")
        files.add(game["file"])
        hash_string(game["sha256"])
    require(sum(game["reconstructedTurns"] for game in games) == 4548
            and sum(game["reconstructedRows"] for game in games) == 18192, "Per-game reconstructed coverage differs")
    return weights


def compare_training_counts(models, training):
    """Exact integer agreement checks, not a new parity threshold or a fit-success gate."""
    checked = {}
    for name in MODEL_SLOTS:
        result = models[name]
        expected = training[f"{name}_metrics"]["selected"]
        slices = result["teacher_prefix"]
        # The old label reducer separates all/unforced; derive the forced counts exactly.
        forced_rows = slices["all"]["rows"] - slices["unforced"]["rows"]
        forced_correct = slices["all"]["exact_correct"] - slices["unforced"]["exact_correct"]
        counts = {key: (value["rows"], value["exact_correct"]) for key, value in slices.items()}
        counts["forced"] = (forced_rows, forced_correct)
        require(set(counts) == set(expected["slices"]), "Training and Java metric slices differ")
        for category, (rows, correct) in counts.items():
            metric = expected["slices"][category]
            require(type(metric["rows"]) is int and type(metric["correct"]) is int
                    and (rows, correct) == (metric["rows"], metric["correct"]), f"{name}/{category} Java/Python agreement counts differ")
            rate = correct / rows if rows else None
            require(metric["agreement"] == rate, f"{name}/{category} training agreement rate is inconsistent")
        roots = result["root_counts"]
        require(expected["full_plans"] == roots["roots"] == 48
                and expected["exact_full_plans"] == roots["teacher_prefix_all_four_exact"]
                and expected["exact_full_plan_agreement"] == expected["exact_full_plans"] / 48,
                f"{name} teacher-prefix all-four agreement differs")
        checked[name] = {"slice_counts_equal": True, "slices": list(counts), "teacher_prefix_all_four_equal": True}
    return checked


def reduce_records(panel_path, panel, selection, data, mappings, metadata):
    reducer = Reducer()
    games = {game["id"]: game for game in data["games"]}
    files, roots = {}, []
    for game, chosen in zip(panel["games"], selection["roots"]):
        path = local_file(Path(panel_path).parent, game["file"])
        require(digest_file(path) == game["sha256"], "Selected Java record file hash mismatch")
        files[str(path)] = game["sha256"]
        record = read(path)
        require(record["weightsSha256"] == panel["weightsSha256"] and record["townContext"] is False,
                "Selected record uses different weights/context")
        source = games[game["id"]]
        start = chosen["cacheRowStart"]
        rows = mappings["train"][start:start + 4]
        require(len(rows) == 4 and record["turn"] == chosen["turn"]
                and record["mapIndex"] == chosen["mapIndex"], "Selected root/row coverage differs")
        hashes = [hashlib.sha256(input_bytes(row)).hexdigest() for row in rows]
        require(chosen["inputSha256"] == hashes, "Selection input identities differ from original cache")
        measured, plans, permutations = validate_turn(translate_record(record), source, metadata[source["id"]]["turns"][chosen["turn"]], rows)
        reducer.add("train", measured, plans, permutations)
        models = {}
        for name, slot in MODEL_SLOTS.items():
            all_four = all(row["exact"] for row in measured[slot])
            models[name] = {"teacher_prefix_all_four_exact": all_four, **plans[slot],
                            "teacher_prefix_vs_own_ordered_disagree": all_four != plans[slot]["ordered_full_plan_equal"]}
        roots.append({**{key: chosen[key] for key in IDENTITY}, "turn": chosen["turn"], "models": models})
    result = reducer.report()["train"]
    result["models"] = {name: result["models"][slot] for name, slot in MODEL_SLOTS.items()}
    for name, values in result["models"].items():
        require(values["root_counts"]["roots"] == 48 and values["teacher_prefix"]["all"]["rows"] == 192, "Reducer omitted selected examples")
        count = sum(root["models"][name]["teacher_prefix_vs_own_ordered_disagree"] for root in roots)
        values["root_counts"]["teacher_prefix_vs_own_ordered_disagree"] = count
        values["root_rates"]["teacher_prefix_vs_own_ordered_disagree"] = count / 48
    return result, roots, files


def run(args):
    output = Path(args.out)
    if output.exists():
        raise FileExistsError("Refusing to overwrite a complete or failed fit-panel reduction")
    started, mappings = time.perf_counter(), {}
    try:
        panel_path, selection_path, training_path = map(Path, (args.panel, args.selection, args.training_report))
        panel, selection, training = map(read, (panel_path, selection_path, training_path))
        identities = {str(path.resolve()): digest_file(path) for path in (panel_path, selection_path, training_path)}
        initial_path = training_path.parent / "initial-weights.base64"
        final_path = training_path.parent / "final-weights.base64"
        payloads = {}
        for name, path in (("initial", initial_path), ("final", final_path)):
            payloads[name] = base64.b64decode(path.read_text(encoding="ascii").strip(), validate=True)
            require(len(payloads[name]) == 3439 * 4, "Diagnostic payload length differs")
            identities[str(path.resolve())] = digest_file(path)
        require(hashlib.sha256(payloads["initial"]).hexdigest() == V4_SHA256
                and hashlib.sha256(payloads["final"]).hexdigest() == training["final_weights_sha256"]
                and payloads["initial"][3424 * 4:] == payloads["final"][3424 * 4:], "Training payload/value-head identities differ")
        data, corpus, mappings, metadata, _ = load_corpus(args.cache)
        cache_path = Path(args.cache) / "cache.json"
        cache_sha = digest_file(cache_path)
        require(cache_sha == selection["cacheSha256"] == training["config"]["cache_sha256"], "Prepared/trained cache identity differs")
        identities[str(cache_path.resolve())] = cache_sha
        _, groups, lineage = audit_lineage(selection["labelAuditReport"], data)
        require(all(selection[key] == value for key, value in lineage.items()), "Selection label-audit lineage differs")
        validate_selection(selection, selection_path, data, corpus, mappings, groups)
        identities[str(Path(selection["labelAuditReport"]).resolve())] = selection["labelAuditReportSha256"]
        identities[str(Path(selection["collisionGroups"]).resolve())] = selection["collisionGroupsSha256"]
        selected_binary = local_file(selection_path.parent, selection["selectedRowsFile"])
        identities[str(selected_binary)] = selection["selectedRowsSha256"]
        weights = validate_panel(panel, selection, training, data, digest_file(selection_path), digest_file(final_path))
        comparisons, roots, files = reduce_records(panel_path, panel, selection, data, mappings, metadata)
        identities.update(files)
        crosscheck = compare_training_counts(comparisons["models"], training)
        require(verify_cache(args.cache) == data, "Original corpus/cache changed during reduction")
        for path, expected in identities.items():
            require(digest_file(path) == expected, "Fit panel input changed during reduction")
        report = {"schemaVersion": 1, "experiment": "spatial-fit-probe-v1", "phase": "panel-reduction", "status": "complete",
                  "weightsSha256": weights, "coverage": {"games": 48, "maps": 24, "selected_roots": 48, "selected_rows": 192,
                      "reconstructed_turns": 4548, "reconstructed_rows": 18192},
                  "comparisons": comparisons, "per_root": roots, "training_metric_crosscheck": crosscheck,
                  "provenance": {"filesSha256": identities, "corpusManifestSha256": data["manifest_sha256"]},
                  "limits": ["Same selected training examples; no generalization or strength claim.",
                      "Teacher-prefix all-four agreement is distinct from actual own-prefix ordered/set/state agreement.",
                      "Income equality alone is not semantic next-state equality.",
                      "Only the original teacher trajectory continues; all student alternatives end after one core transition.",
                      "Initial/final are both the unchanged context-off V4 architecture; internal reused reducer slots do not identify V9."],
                  "seconds": time.perf_counter() - started}
        write_new(output, report)
    except Exception as exc:
        if not output.exists():
            write_new(output, {"schemaVersion": 1, "experiment": "spatial-fit-probe-v1", "phase": "panel-reduction", "status": "failed",
                               "failure": {"type": type(exc).__name__, "message": str(exc)}})
        raise
    finally:
        for rows in mappings.values():
            rows._mmap.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--panel", required=True)
    parser.add_argument("--selection", required=True)
    parser.add_argument("--training-report", required=True, help="Complete metrics.json beside initial/final-weights.base64")
    parser.add_argument("--cache", default="build/spatial-ppo-v4-cache")
    parser.add_argument("--out", required=True)
    run(parser.parse_args())
