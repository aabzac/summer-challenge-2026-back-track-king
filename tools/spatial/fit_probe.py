"""Fixed same-example fitting diagnostic; no candidate, Java export or new play."""
from __future__ import annotations

import argparse
from collections import Counter, defaultdict
import hashlib
import json
import os
from pathlib import Path
import random
import time

from train_spatial import ROOT, Bridge, SpatialNet, checkpoint, np, torch
from run_v2_training import peak_working_set_bytes, runtime_identities
from v2_support import weight_hash
from v3_support import java_payload_hash
from v4_data import ROW_BYTES, ROW_DTYPE, digest_file, parity_observations, read_json, row_tensors, verify_cache
from v4_distill import policy_rows, verify_freeze, verify_parity
from label_audit import collision_audit, input_bytes, load_corpus, require, write_new

V4_SHA256 = "89de11f4239844c55c6e61c987ee2443fd5d79a1c71af782d2686e836ec6f3ef"
CONFIG = {"schema": "spatial-fit-probe-v1", "seed": 20261010, "epochs": 1024,
          "root_count": 48, "training_rows": 192, "minibatch_decisions": 64,
          "learning_rate": .001, "gradient_clip": 1., "torch_threads": 2, "interop_threads": 1,
          "adam_betas": [.9, .999], "adam_epsilon": 1e-8, "adam_weight_decay": 0.,
          "expected_optimizer_steps": 3072, "expected_row_exposures": 196608,
          "checkpoint_epochs": [0, 512, 1024], "metric_interval": 64, "parity_rows": 24,
          "warm_start_payload_sha256": V4_SHA256, "model": "unchanged model.SpatialNet",
          "objective": "unchanged V4 mean masked hard-label cross entropy over all four decision rows",
          "shuffle": "torch.randperm of the 192 decision rows each epoch",
          "value_head": "15 frozen coefficients; shared features change; values uncalibrated",
          "selection_rank": "SHA256 UTF8 spatial-fit-probe-v1\\n{id}\\n{turn}\\n, ascending hex then numeric turn",
          "selection_exclusion": "whole root if any row belongs to contradictory exact-input group within TRAIN only",
          "selection_other_filters": "none; fail if any of the 48 games has no eligible root",
          "checkpoint_purpose": "inspection/reproduction; no exact-resume interface or checkpoint selection",
          "checkpoint_iteration_field": "completed supervised epochs; not PPO updates",
          "comparison_limit": "subset memorization only; complement already trained by initializer and validation already exposed",
          "export": "standalone diagnostic payloads only; no generated Java source or registered candidate"}


def source_identities():
    paths = list((ROOT / "tools/spatial").glob("*.py"))
    paths += list((ROOT / "src/main/java/btk").rglob("*.java"))
    paths += [p for p in (ROOT / "src/main/resources/btk").rglob("*") if p.is_file()]
    paths += [ROOT / "docs/spatial-fit-probe-v1.md"]
    return {p.relative_to(ROOT).as_posix(): digest_file(p) for p in sorted(set(paths))}


def runtime_identity(command):
    require(command[-1] == "btk.tools.spatial.SpatialBridge", "Probe parity requires the unchanged SpatialBridge")
    result = runtime_identities(command)
    at = command.index("-cp") if "-cp" in command else command.index("-classpath")
    for entry in command[at + 1].split(os.pathsep):
        path = Path(entry)
        if path.is_dir():
            for file in (path / "btk").rglob("*"):
                if file.is_file():
                    result["compiled_engine_and_classpath_sha256"][str(file.resolve())] = digest_file(file)
    return result


def training_conflicts(groups):
    """Cross-split-only disagreement does not disqualify a TRAIN root."""
    excluded = defaultdict(set)
    for group in groups:
        members = [row for row in group["rows"] if row["split"] == "train"]
        if len({row["label"] for row in members}) > 1:
            for row in members:
                excluded[row["game"], row["turn"]].add(group["input_sha256"])
    return {key: sorted(values) for key, values in excluded.items()}


def rank_turn(game_id, turn):
    return hashlib.sha256(f"spatial-fit-probe-v1\n{game_id}\n{turn}\n".encode("utf-8")).hexdigest()


def select_roots(games, maps, mappings, collision_groups):
    excluded = training_conflicts(collision_groups)
    map_index = {(item["split"], item["seed"]): item["mapIndex"] for item in maps}
    roots, exclusions = [], []
    for game in sorted((game for game in games if game["split"] == "train"), key=lambda game: (game["seed"], game["teacherSeat"])):
        eligible = []
        for turn in range(game["turns"]):
            key = game["id"], turn
            if key in excluded:
                exclusions.append({"id": game["id"], "turn": turn, "conflictingInputSha256": excluded[key]})
            else:
                eligible.append((rank_turn(game["id"], turn), turn))
        require(bool(eligible), f"No conflict-free root in training game {game['id']}; no replacement permitted")
        rank, turn = min(eligible)
        start = game["cacheRowStart"] + 4 * turn
        rows = mappings["train"][start:start + 4]
        require(len(rows) == 4 and np.array_equal(rows["stage"], np.arange(4)) and (rows["turn"] == turn).all(),
                "Selected root is not four consecutive original conditional rows")
        roots.append({key: game[key] for key in ("id", "split", "seed", "mapHash", "teacherSeat", "opponentSpec")}
                     | {"mapIndex": map_index["train", game["seed"]], "turn": turn, "rankSha256": rank,
                        "cacheRowStart": start, "inputSha256": [hashlib.sha256(input_bytes(row)).hexdigest() for row in rows]})
    return roots, exclusions


def audit_lineage(path, data):
    path = Path(path)
    report = read_json(path)
    require(report.get("status") == "complete" and report.get("experiment") == "spatial-label-audit-v1",
            "Selection requires the completed frozen label audit")
    require(report["provenance"]["corpus_manifest_sha256"] == data["manifest_sha256"], "Label audit references a different corpus")
    evidence = Path(report["collision_evidence"]["path"])
    require(digest_file(evidence) == report["collision_evidence"]["sha256"], "Archived collision evidence changed")
    groups = read_json(evidence)["groups"]
    require(len(groups) == report["collision_evidence"]["duplicate_groups"], "Archived collision evidence count mismatch")
    return report, groups, {"labelAuditReport": str(path.resolve()), "labelAuditReportSha256": digest_file(path),
                            "collisionGroups": str(evidence.resolve()), "collisionGroupsSha256": digest_file(evidence)}


def prepare(args):
    target = Path(args.selection)
    binary = target.parent / "selected-rows.bin"
    if target.exists() or binary.exists():
        raise FileExistsError("Refusing to reuse a complete or partial prepared probe selection")
    started, mappings = time.perf_counter(), {}
    sources = source_identities()
    try:
        data, corpus, mappings, _, lineage = load_corpus(args.cache)
        old, groups, identities = audit_lineage(args.label_audit, data)
        recomputed, recomputed_groups = collision_audit(mappings, lineage)
        require(recomputed == old["collisions"] and recomputed_groups == groups,
                "Frozen collision audit does not reproduce from the unchanged complete corpus")
        roots, exclusions = select_roots(data["games"], corpus["maps"], mappings, groups)
        require(len(roots) == 48 and len({root["id"] for root in roots}) == 48, "Expected one root from all 48 TRAIN games")
        chosen = np.concatenate([np.arange(root["cacheRowStart"], root["cacheRowStart"] + 4) for root in roots])
        require(len(chosen) == 192 and len(set(chosen.tolist())) == 192, "Selected conditional row coverage mismatch")
        selected = mappings["train"][chosen]
        require(verify_cache(args.cache) == data, "Corpus/cache changed during deterministic preparation")
        require(source_identities() == sources, "Sources changed during deterministic preparation")
        require(audit_lineage(args.label_audit, data)[2] == identities, "Label audit changed during preparation")
        target.parent.mkdir(parents=True, exist_ok=True)
        with binary.open("xb") as stream:
            stream.write(selected.tobytes())
        selection = {"schemaVersion": 1, "experiment": "spatial-fit-probe-v1", "status": "complete",
                     "config": CONFIG, "corpusManifestSha256": data["manifest_sha256"],
                     "cacheSha256": digest_file(Path(args.cache) / "cache.json"),
                     "initialWeightsSha256": V4_SHA256, **identities,
                     "roots": roots, "rootCount": 48, "rowCount": 192, "excludedRoots": exclusions,
                     "selectedRowsFile": binary.name, "selectedRowsSha256": digest_file(binary),
                     "selectedRowsBytes": binary.stat().st_size, "sourceSha256": sources,
                     "prepareSeconds": time.perf_counter() - started}
        require(selection["selectedRowsBytes"] == 192 * ROW_BYTES, "Selected binary size mismatch")
        write_new(target, selection)
        print(json.dumps({"prepared": True, "selection": str(target), "roots": 48, "rows": 192,
                          "excluded_roots": len(exclusions), "seconds": selection["prepareSeconds"]}), flush=True)
    except Exception as exc:
        if not target.exists():
            write_new(target, {"schemaVersion": 1, "experiment": "spatial-fit-probe-v1", "status": "failed",
                               "failure": {"type": type(exc).__name__, "message": str(exc)}})
        raise
    finally:
        for rows in mappings.values():
            rows._mmap.close()


def validate_selection(selection, path, data, corpus, mappings, groups):
    require(selection.get("status") == "complete" and selection.get("experiment") == "spatial-fit-probe-v1"
            and selection.get("schemaVersion") == 1 and selection["config"] == CONFIG,
            "Prepared selection/config does not match the fixed probe")
    require(selection["initialWeightsSha256"] == V4_SHA256 and selection["corpusManifestSha256"] == data["manifest_sha256"],
            "Selection initializer/corpus lineage mismatch")
    roots, exclusions = select_roots(data["games"], corpus["maps"], mappings, groups)
    require(selection["roots"] == roots and selection["excludedRoots"] == exclusions, "Frozen deterministic root choice changed")
    require(len(roots) == selection["rootCount"] == 48 and selection["rowCount"] == 192, "Incorrect fixed selection size")
    require(selection["selectedRowsFile"] == "selected-rows.bin", "Unexpected selected-row artifact path")
    binary = Path(path).parent / selection["selectedRowsFile"]
    require(binary.stat().st_size == selection["selectedRowsBytes"] == 192 * ROW_BYTES
            and digest_file(binary) == selection["selectedRowsSha256"], "Selected row bytes changed")
    indices = np.concatenate([np.arange(root["cacheRowStart"], root["cacheRowStart"] + 4) for root in roots])
    require(binary.read_bytes() == mappings["train"][indices].tobytes(), "Selected bytes differ from original full-root source rows")
    return indices


class RowSelection:
    """Index a memmap in minibatches, without copying the full TRAIN complement."""
    def __init__(self, rows, indices):
        self.rows, self.indices = rows, np.asarray(indices, dtype=np.int64)

    def __len__(self):
        return len(self.indices)

    def __getitem__(self, item):
        return self.rows[self.indices[item]]


def partition_rows(training, selected):
    selected = np.asarray(selected, dtype=np.int64)
    require(selected.shape == (192,) and len(np.unique(selected)) == 192
            and (selected >= 0).all() and (selected < len(training)).all(), "Invalid selected row index coverage")
    groups = selected.reshape(-1, 4)
    require((groups[:, 0] % 4 == 0).all() and np.array_equal(groups, groups[:, :1] + np.arange(4)),
            "Probe/complement partition must retain complete roots")
    mask = np.ones(len(training), dtype=bool)
    mask[selected] = False
    return RowSelection(training, selected), RowSelection(training, np.flatnonzero(mask))


def fresh_probe(model):
    require(weight_hash(model) == V4_SHA256, "Probe must start from exact V4 final coefficients")
    model.value.requires_grad_(False)
    random.seed(CONFIG["seed"])
    np.random.seed(CONFIG["seed"])
    torch.manual_seed(CONFIG["seed"])
    return torch.optim.Adam([parameter for parameter in model.parameters() if parameter.requires_grad], lr=CONFIG["learning_rate"])


def evaluate(model, rows):
    """Frozen V4 loss/predictions, with additional stage and forced-choice slices."""
    names = ["all", "forced", "unforced", "build", "disrupt", "build_unforced", "disrupt_unforced"]
    names += [f"stage{stage}" for stage in range(4)] + [f"stage{stage}_unforced" for stage in range(4)]
    stats = {name: Counter() for name in names}
    require(len(rows) > 0 and len(rows) % 4 == 0, "Metric rows must contain complete ordered roots")
    correct_plans = np.empty(len(rows), dtype=bool)
    model.eval()
    with torch.no_grad():
        for start in range(0, len(rows), CONFIG["minibatch_decisions"]):
            obs, labels, stages = row_tensors(rows[start:start + CONFIG["minibatch_decisions"]])
            require(torch.equal(stages, torch.arange(start, start + len(stages)) % 4), "Metric rows lost stage order")
            loss, predicted, unforced = policy_rows(model, obs, labels, stages)
            correct = predicted == labels
            correct_plans[start:start + len(labels)] = correct.numpy()
            masks = {"all": torch.ones_like(unforced), "forced": ~unforced, "unforced": unforced,
                     "build": stages < 3, "disrupt": stages == 3,
                     "build_unforced": (stages < 3) & unforced, "disrupt_unforced": (stages == 3) & unforced}
            for stage in range(4):
                masks[f"stage{stage}"] = stages == stage
                masks[f"stage{stage}_unforced"] = (stages == stage) & unforced
            for name, mask in masks.items():
                stats[name].update(rows=int(mask.sum()), correct=int(correct[mask].sum()), loss=float(loss[mask].double().sum()))
    slices = {name: {"rows": values["rows"], "correct": values["correct"],
                     "cross_entropy": values["loss"] / values["rows"] if values["rows"] else None,
                     "agreement": values["correct"] / values["rows"] if values["rows"] else None}
              for name, values in stats.items()}
    plans = correct_plans.reshape(-1, 4).all(1)
    return {"slices": slices, "full_plans": len(plans), "exact_full_plans": int(plans.sum()),
            "exact_full_plan_agreement": float(plans.mean()),
            "interpretation": "teacher-prefix four-row agreement; actual own-prefix plans measured separately in Java"}


def save_checkpoint(path, model, optimizer, config, epoch):
    path = Path(path)
    temporary = path.with_name(path.name + ".tmp")
    if path.exists() or temporary.exists():
        raise FileExistsError("Refusing to overwrite or resume an existing checkpoint")
    checkpoint(temporary, model, optimizer, config, epoch)
    temporary.replace(path)


def save_metrics(path, report):
    path = Path(path)
    temporary = path.with_name(path.name + ".tmp")
    temporary.write_text(json.dumps(report, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    temporary.replace(path)


def save_payload(path, model):
    with Path(path).open("x", encoding="ascii") as stream:
        stream.write(model.encoded_weights())


def train(args):
    out, evidence = Path(args.output), Path(args.evidence)
    if out.exists() or evidence.exists():
        raise FileExistsError("Refusing to reuse a complete or partial fixed fit probe")
    mappings, report = {}, None
    started = time.perf_counter()
    try:
        freeze = verify_freeze(args.freeze)
        frozen_files = read_json(args.freeze)["files"]
        selection_path = Path(args.selection).resolve()
        selection_name = selection_path.relative_to(ROOT).as_posix()
        require(frozen_files.get(selection_name) == digest_file(selection_path), "Training freeze does not bind the prepared selection")
        selection = read_json(selection_path)
        data, corpus, mappings, _, _ = load_corpus(args.cache)
        _, groups, identities = audit_lineage(selection["labelAuditReport"], data)
        require(all(selection[key] == value for key, value in identities.items()), "Selection label-audit lineage changed")
        require(selection["cacheSha256"] == digest_file(Path(args.cache) / "cache.json"), "Selected cache identity changed")
        selected_indices = validate_selection(selection, selection_path, data, corpus, mappings, groups)
        binary = selection_path.parent / selection["selectedRowsFile"]
        require(frozen_files.get(binary.relative_to(ROOT).as_posix()) == digest_file(binary), "Training freeze does not bind selected row bytes")
        subsets = dict(zip(("selected", "train_complement_previously_trained"), partition_rows(mappings["train"], selected_indices)))
        subsets["validation_previously_exposed"] = mappings["validation"]
        require(len(subsets["train_complement_previously_trained"]) == 18000
                and len(subsets["validation_previously_exposed"]) == 4448, "Fixed complement/validation coverage differs")
        v4_evidence_path = ROOT / "docs/evidence/spatial-ppo-v4-training.json"
        v4 = read_json(v4_evidence_path)
        require(v4["status"] == "complete" and v4["final_weights_sha256"] == V4_SHA256
                and java_payload_hash(ROOT / "src/main/java/btk/bot/neural/SpatialV4Weights.java") == V4_SHA256,
                "V4 historical initializer identity differs")
        command = read_json(args.java_command_json)
        config = {**CONFIG, "source_freeze": freeze, "selection": str(selection_path),
                  "selection_sha256": digest_file(selection_path), "selection_manifest": selection,
                  "source_sha256": source_identities(), "runtime": runtime_identity(command),
                  "java_command": command, "java_command_sha256": digest_file(args.java_command_json),
                  "warm_start_checkpoint_sha256": digest_file(args.warm_start),
                  "v4_training_evidence_sha256": digest_file(v4_evidence_path),
                  "cache_sha256": digest_file(Path(args.cache) / "cache.json"),
                  "corpus_manifest_sha256": data["manifest_sha256"]}
        require(torch.__version__ == "2.8.0+cpu" and np.__version__ == "2.3.5", "Fixed probe runtime versions differ")
        torch.set_num_threads(CONFIG["torch_threads"])
        torch.set_num_interop_threads(CONFIG["interop_threads"])
        torch.use_deterministic_algorithms(True)
        model = SpatialNet()
        saved = torch.load(args.warm_start, weights_only=False, map_location="cpu")
        require(saved["iteration"] == 16 and saved["config"]["schema"] == "spatial-ppo-v4-distillation", "Expected original V4 final checkpoint")
        model.load_state_dict(saved["model"])
        del saved
        optimizer = fresh_probe(model)
        config["trainable_parameters"] = sum(parameter.numel() for parameter in model.parameters() if parameter.requires_grad)
        require(config["trainable_parameters"] == 3424 and sum(p.numel() for p in model.parameters()) == 3439,
                "Unchanged parameter counts differ")
        frozen_value = {key: value.detach().clone() for key, value in model.value.named_parameters()}
        initial_vector = torch.cat([p.detach().flatten().clone() for p in model.parameters()])
        out.mkdir(parents=True, exist_ok=False)
        evidence.parent.mkdir(parents=True, exist_ok=True)
        write_new(out / "config.json", config)
        report = {"status": "running", "config": config, "initial_weights_sha256": weight_hash(model),
                  "learning_curve": [], "optimization_blocks": [], "validation_seconds": time.perf_counter() - started}

        def emit(message):
            line = json.dumps(message, allow_nan=False)
            print(line, flush=True)
            with (out / "run.log").open("a", encoding="utf-8") as stream:
                stream.write(line + "\n")

        panel = parity_observations(subsets["selected"][:24])
        write_new(out / "parity-observations.json", panel)
        write_new(out / "parity-row-lineage.json", [dict(root, stage=stage) for root in selection["roots"][:6] for stage in range(4)])
        save_checkpoint(out / "initial.pt", model, optimizer, config, 0)
        save_payload(out / "initial-weights.base64", model)
        training_started = time.perf_counter()
        emit({"started": True, "epochs": 1024, "rows": 192, "expected_optimizer_steps": 3072})
        with Bridge(command) as bridge:
            report["initial_parity"] = verify_parity(bridge, model, panel, out, "initial")
            evaluation_started = time.perf_counter()
            report["initial_metrics"] = {name: evaluate(model, rows) for name, rows in subsets.items()}
            report["initial_evaluation_seconds"] = time.perf_counter() - evaluation_started
            report["learning_curve"].append({"epoch": 0, "metrics": report["initial_metrics"]["selected"]})
            save_metrics(out / "metrics.json", report)
            block_started, block_loss, block_norm, block_steps = time.perf_counter(), 0., 0., 0
            total_steps, row_exposures = 0, 0
            for epoch in range(1, CONFIG["epochs"] + 1):
                model.train()
                permutation = torch.randperm(CONFIG["training_rows"]).numpy()
                for start in range(0, CONFIG["training_rows"], CONFIG["minibatch_decisions"]):
                    obs, labels, stages = row_tensors(subsets["selected"][permutation[start:start + CONFIG["minibatch_decisions"]]])
                    losses, _, _ = policy_rows(model, obs, labels, stages)
                    loss = losses.mean()
                    if not torch.isfinite(loss):
                        raise FloatingPointError("Non-finite fixed-probe loss")
                    optimizer.zero_grad(set_to_none=True)
                    loss.backward()
                    norm = torch.nn.utils.clip_grad_norm_(model.parameters(), CONFIG["gradient_clip"], error_if_nonfinite=True)
                    optimizer.step()
                    if any(not torch.isfinite(parameter).all() for parameter in model.parameters()):
                        raise FloatingPointError("Non-finite updated fixed-probe coefficient")
                    block_loss += float(loss.detach()) * len(labels)
                    block_norm += float(norm)
                    block_steps += 1
                    total_steps += 1
                    row_exposures += len(labels)
                require(all(torch.equal(parameter, frozen_value[name]) for name, parameter in model.value.named_parameters()),
                        "Frozen value-head coefficients changed")
                if epoch == 512:
                    save_checkpoint(out / "midpoint.pt", model, optimizer, config, epoch)
                    report["midpoint_weights_sha256"] = weight_hash(model)
                if epoch == 1024:
                    # Preserve the sole fixed endpoint even if a later diagnostic
                    # fails. Completion is declared only after all final checks.
                    save_checkpoint(out / "final.pt", model, optimizer, config, epoch)
                    save_payload(out / "final-weights.base64", model)
                if epoch % CONFIG["metric_interval"] == 0:
                    optimization_seconds = time.perf_counter() - block_started
                    evaluation_started = time.perf_counter()
                    metric = evaluate(model, subsets["selected"])
                    curve = {"epoch": epoch, "metrics": metric, "evaluation_seconds": time.perf_counter() - evaluation_started}
                    block = {"epoch_start": epoch - 63, "epoch_end": epoch, "optimizer_steps": block_steps,
                             "row_exposures": block_steps * 64, "optimization_cross_entropy": block_loss / (block_steps * 64),
                             "mean_gradient_norm_before_clip": block_norm / block_steps, "seconds": optimization_seconds}
                    report["optimization_blocks"].append(block)
                    report["learning_curve"].append(curve)
                    save_metrics(out / "metrics.json", report)
                    emit({"epoch": epoch, "optimizer_steps": total_steps, "optimization": block,
                          "selected_metrics": metric})
                    block_started, block_loss, block_norm, block_steps = time.perf_counter(), 0., 0., 0
            evaluation_started = time.perf_counter()
            report["final_metrics"] = {"selected": report["learning_curve"][-1]["metrics"],
                                      **{name: evaluate(model, rows) for name, rows in subsets.items() if name != "selected"}}
            report["final_evaluation_seconds"] = time.perf_counter() - evaluation_started
            report["final_parity"] = verify_parity(bridge, model, panel, out, "final")
        adam_steps = sorted({int(state["step"]) for state in optimizer.state.values()})
        require(adam_steps == [3072] and total_steps == 3072 and row_exposures == 196608,
                "Fixed probe optimizer/exposure counts differ")
        require(source_identities() == config["source_sha256"] and verify_freeze(args.freeze) == freeze,
                "Frozen probe source changed during training")
        for path, expected in config["runtime"]["compiled_engine_and_classpath_sha256"].items():
            require(digest_file(path) == expected, "Compiled runtime changed during probe")
        for path, expected in ((args.java_command_json, config["java_command_sha256"]),
                               (args.warm_start, config["warm_start_checkpoint_sha256"]),
                               (selection_path, config["selection_sha256"]),
                               (v4_evidence_path, config["v4_training_evidence_sha256"])):
            require(digest_file(path) == expected, "A frozen probe input changed")
        require(verify_cache(args.cache) == data and digest_file(Path(args.cache) / "cache.json") == config["cache_sha256"],
                "Original corpus/cache changed during probe")
        require(audit_lineage(selection["labelAuditReport"], data)[2] == identities, "Label audit changed during probe")
        validate_selection(selection, selection_path, data, corpus, mappings, groups)
        final_vector = torch.cat([p.detach().flatten() for p in model.parameters()])
        report.update(status="complete", final_weights_sha256=weight_hash(model), total_optimizer_steps=total_steps,
                      adam_steps_per_parameter=adam_steps, trained_decision_rows=row_exposures,
                      value_head_parameters_unchanged=True, weight_l2_change=float((final_vector - initial_vector).norm()),
                      weight_max_abs_change=float((final_vector - initial_vector).abs().max()),
                      training_and_evaluation_seconds=time.perf_counter() - training_started,
                      total_seconds=time.perf_counter() - started, python_peak_working_set_bytes=peak_working_set_bytes(),
                      candidate_exported=False)
        save_metrics(out / "metrics.json", report)
        write_new(evidence, report)
        emit({"complete": True, "optimizer_steps": total_steps, "row_exposures": row_exposures,
              "final_weights_sha256": report["final_weights_sha256"], "seconds": report["total_seconds"]})
    except Exception as exc:
        failure = {"type": type(exc).__name__, "message": str(exc)}
        if report is not None:
            report.update(status="failed", failure=failure, total_seconds=time.perf_counter() - started)
            save_metrics(out / "metrics.json", report)
            write_new(out / "failure.json", failure)
            if not evidence.exists():
                write_new(evidence, report)
        raise
    finally:
        for rows in mappings.values():
            rows._mmap.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="operation", required=True)
    prepare_parser = commands.add_parser("prepare", help="one deterministic fixed root per training game; no inference")
    prepare_parser.add_argument("--label-audit", default="build/analysis/spatial-label-audit-v1/report.json")
    train_parser = commands.add_parser("train", help="one fixed 1024-epoch fitting diagnostic; no resume")
    train_parser.add_argument("--warm-start", default="build/spatial-ppo-v4/final.pt")
    train_parser.add_argument("--java-command-json", default="build/analysis/spatial-fit-probe-v1/java-command.json")
    train_parser.add_argument("--freeze", default="build/analysis/spatial-fit-probe-v1/sources-freeze.json")
    train_parser.add_argument("--output", default="build/spatial-fit-probe-v1")
    train_parser.add_argument("--evidence", default="build/analysis/spatial-fit-probe-v1/training-report.json")
    for command in (prepare_parser, train_parser):
        command.add_argument("--cache", default="build/spatial-ppo-v4-cache")
        command.add_argument("--selection", default="build/analysis/spatial-fit-probe-v1/selection/selection.json")
    args = parser.parse_args()
    (prepare if args.operation == "prepare" else train)(args)
