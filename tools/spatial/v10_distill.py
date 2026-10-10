"""One fixed 64-epoch continuation of original V4 Adam/model/RNG state."""
from __future__ import annotations

import argparse
import hashlib
import json
import os
from pathlib import Path
import pickle
import random
import time

from train_spatial import ROOT, Bridge, SpatialNet, np, torch
from model import WEIGHT_ORDER
from run_v2_training import peak_working_set_bytes, runtime_identities
from v2_support import weight_hash
from v3_support import java_payload_hash
from v4_data import ROW_DTYPE, digest_file, parity_observations, read_json, row_tensors, verify_cache
from v4_distill import CONFIG as V4_CONFIG, policy_rows, verify_freeze, verify_parity
from fit_probe import evaluate, save_checkpoint, save_metrics, save_payload
from label_audit import require, write_new
from v9_distill import historical_integrity

V4_SHA256 = "89de11f4239844c55c6e61c987ee2443fd5d79a1c71af782d2686e836ec6f3ef"
CHECKPOINT_SHA256 = "1720eefba47171c0d1fef201c3eccd13c0b74dc906483dc8b9f42bac136733f2"
TRAIN_NAMES = tuple(name for name in WEIGHT_ORDER if not name.startswith("value."))
CONFIG = {"schema": "spatial-ppo-v10-continuation", "initial_epoch": 16, "additional_epochs": 64,
          "final_epoch": 80, "checkpoint_epochs": [16, 48, 80], "metric_epochs": list(range(16, 81, 8)),
          "training_rows": 18192, "validation_rows": 4448, "minibatch_decisions": 64,
          "batches_per_epoch": 285, "last_batch_rows": 16,
          "initial_adam_steps": 4560, "additional_adam_steps": 18240, "final_adam_steps": 22800,
          "midpoint_adam_steps": 13680,
          "additional_row_exposures": 1164288, "cumulative_row_exposures": 1455360,
          "learning_rate": .001, "gradient_clip": 1., "torch_threads": 2, "interop_threads": 1,
          "initial_payload_sha256": V4_SHA256, "initial_checkpoint_sha256": CHECKPOINT_SHA256,
          "rng": "restore saved Python/NumPy/Torch states after constructors/loading; never reseed",
          "optimizer": "restore original Adam group, step counters and moments; no reset or scheduler",
          "model": "unchanged model.SpatialNet;3439 floats;3424 trainable;15 value coefficients frozen",
          "objective": "unchanged V4 mean masked hard-label cross entropy over all four decision rows",
          "shuffle": "one torch.randperm(18192) per added epoch;64-row batches retaining16-row tail",
          "checkpoint_iteration_field": "cumulative supervised epoch,16/48/80",
          "checkpoint_purpose": "bounded continuation evidence; no alternate checkpoint selection or further resume CLI",
          "parity_rows": 24, "parity_selection": "first six original TRAIN teacher turns, same24rows asV4",
          "value_limit": "frozen head coefficients;shared features change;uncalibrated and unused",
          "validation_limit": "original split retained; previously exposed development data"}


def rng_snapshot():
    state = np.random.get_state()
    return {"python_rng": random.getstate(), "numpy_rng": (state[0], state[1].copy(), *state[2:]),
            "torch_rng": torch.get_rng_state().clone()}


def rng_equal(left, right):
    a, b = left["numpy_rng"], right["numpy_rng"]
    return (left["python_rng"] == right["python_rng"] and torch.equal(left["torch_rng"], right["torch_rng"])
            and a[0] == b[0] and np.array_equal(a[1], b[1]) and a[2:] == b[2:])


def rng_hashes(state):
    return {"torch": hashlib.sha256(state["torch_rng"].numpy().tobytes()).hexdigest(),
            "numpy": hashlib.sha256(pickle.dumps(state["numpy_rng"], protocol=4)).hexdigest(),
            "python": hashlib.sha256(pickle.dumps(state["python_rng"], protocol=4)).hexdigest()}


def restore_state(saved, expected_epoch, expected_steps):
    """Validate a SpatialNet checkpoint, restore Adam, then restore global RNG last."""
    require({"model", "optimizer", "iteration", "config", "torch_rng", "numpy_rng", "python_rng"} <= set(saved),
            "Incomplete saved model/Adam/RNG checkpoint")
    require(saved["iteration"] == expected_epoch, "Unexpected saved cumulative epoch")
    model = SpatialNet()
    state = saved["model"]
    template = model.state_dict()
    require(tuple(state) == WEIGHT_ORDER, "Saved parameter names/order differ from frozen model")
    for name, tensor in state.items():
        require(isinstance(tensor, torch.Tensor) and tensor.dtype == torch.float32
                and tensor.shape == template[name].shape and torch.isfinite(tensor).all(), "Malformed/non-finite saved coefficient tensor")
    model.load_state_dict(state)
    model.value.requires_grad_(False)
    require(tuple(name for name, parameter in model.named_parameters() if parameter.requires_grad) == TRAIN_NAMES,
            "Adam parameter order/value-head exclusion differs")
    optimizer = torch.optim.Adam([parameter for parameter in model.parameters() if parameter.requires_grad], lr=.001)
    opt = saved["optimizer"]
    require(set(opt) == {"state", "param_groups"} and len(opt["param_groups"]) == 1, "Expected one original Adam parameter group")
    group = opt["param_groups"][0]
    expected_group = optimizer.state_dict()["param_groups"][0]
    require(group == expected_group, "Saved Adam options/parameter order differ from original fixed recipe")
    ids = group["params"]
    require(ids == list(range(18)) and set(opt["state"]) == set(ids), "Missing/extra Adam moment entries")
    for index, name in zip(ids, TRAIN_NAMES):
        moment = opt["state"][index]
        require(set(moment) == {"step", "exp_avg", "exp_avg_sq"}, "Unexpected Adam moment layout")
        step = moment["step"]
        require(isinstance(step, torch.Tensor) and step.ndim == 0 and torch.isfinite(step)
                and float(step) == expected_steps, "Saved Adam counter differs")
        for key in ("exp_avg", "exp_avg_sq"):
            tensor = moment[key]
            require(isinstance(tensor, torch.Tensor) and tensor.dtype == torch.float32
                    and tensor.shape == state[name].shape and torch.isfinite(tensor).all(), "Malformed/non-finite Adam moment")
        require((moment["exp_avg_sq"] >= 0).all(), "Negative Adam second moment")
    try:
        # Validate with private generators before touching the global streams.
        require(saved["torch_rng"].dtype == torch.uint8 and saved["torch_rng"].ndim == 1, "Invalid Torch RNG tensor")
        torch.Generator(device="cpu").set_state(saved["torch_rng"])
        np.random.RandomState().set_state(saved["numpy_rng"])
        random.Random().setstate(saved["python_rng"])
    except (TypeError, RuntimeError, KeyError, AttributeError, ValueError) as exc:
        raise ValueError("Invalid saved random-generator state") from exc
    optimizer.load_state_dict(opt)
    random.setstate(saved["python_rng"])
    np.random.set_state(saved["numpy_rng"])
    torch.set_rng_state(saved["torch_rng"])
    require(rng_equal(saved, rng_snapshot()), "Random-generator restoration mismatch")
    return model, optimizer


def restore_original(path):
    require(digest_file(path) == CHECKPOINT_SHA256, "Expected byte-exact original V4 final.pt")
    saved = torch.load(path, weights_only=False, map_location="cpu")
    require(saved["config"]["schema"] == "spatial-ppo-v4-distillation", "Wrong original checkpoint schema")
    for name, expected in V4_CONFIG.items():
        require(saved["config"][name] == expected, f"Original V4 recipe mismatch: {name}")
    model, optimizer = restore_state(saved, 16, 4560)
    require(weight_hash(model) == V4_SHA256, "Original V4 checkpoint payload mismatch")
    require(java_payload_hash(ROOT / "src/main/java/btk/bot/neural/SpatialV4Weights.java") == V4_SHA256,
            "Frozen V4 Java payload changed")
    evidence_path = ROOT / "docs/evidence/spatial-ppo-v4-training.json"
    evidence = read_json(evidence_path)
    require(evidence["status"] == "complete" and evidence["final_weights_sha256"] == V4_SHA256
            and evidence["total_optimizer_steps"] == 4560 and evidence["config"] == saved["config"],
            "Original checkpoint differs from historical V4 evidence")
    return model, optimizer, saved, {"checkpoint_sha256": CHECKPOINT_SHA256, "payload_sha256": V4_SHA256,
        "evidence_sha256": digest_file(evidence_path), "restored_epoch": 16, "restored_adam_steps": 4560,
        "restored_rng_sha256": rng_hashes(saved), "optimizer_tensors": 18,
        "moment_shapes_finite_order_validated": True, "global_rng_restored_last": True}


def batch_indices(permutation):
    for start in range(0, len(permutation), CONFIG["minibatch_decisions"]):
        yield permutation[start:start + CONFIG["minibatch_decisions"]]


def check_diagnostic_rng(before):
    require(rng_equal(before, rng_snapshot()), "A diagnostic advanced a restored training RNG stream")


def source_identities():
    paths = list((ROOT / "tools/spatial").glob("*.py"))
    paths += [path for path in (ROOT / "src/main/java/btk").rglob("*.java") if path.name != "SpatialV10Weights.java"]
    paths += [path for path in (ROOT / "src/main/resources/btk").rglob("*") if path.is_file()]
    paths += [ROOT / "docs/spatial-ppo-v10.md"]
    return {path.relative_to(ROOT).as_posix(): digest_file(path) for path in sorted(set(paths))}


def runtime_identity(command):
    require(command[-1] == "btk.tools.spatial.SpatialBridge", "Continuation parity requires original SpatialBridge")
    result = runtime_identities(command)
    at = command.index("-cp") if "-cp" in command else command.index("-classpath")
    for entry in command[at + 1].split(os.pathsep):
        path = Path(entry)
        if path.is_dir():
            for file in (path / "btk").rglob("*"):
                if file.is_file() and file.name not in ("SpatialV10Weights.class", "SpatialV10Bot.class"):
                    result["compiled_engine_and_classpath_sha256"][str(file.resolve())] = digest_file(file)
    return result


def export_weights(model, path, temporary=False):
    path = Path(path)
    require(path.name == "SpatialV10Weights.java", "V10 may generate only SpatialV10Weights.java")
    payload = model.encoded_weights()
    if temporary:
        require(weight_hash(model) == V4_SHA256, "Compile-only payload must be exact V4")
        if path.exists():
            require("temporary" in path.read_text(encoding="utf-8").lower() and java_payload_hash(path) == V4_SHA256,
                    "Refusing to replace a non-temporary or different existing V10 source")
    marker = "TEMPORARY compile-only exact V4 initializer; final V10 training export replaces this file. " if temporary else ""
    source = ("package btk.bot.neural;\n\n/** " + marker
              + "Generated by tools/spatial/v10_distill.py; do not edit weights manually. */\n"
              "public final class SpatialV10Weights {\n    private SpatialV10Weights() {}\n"
              "    public static final String DATA =\n")
    chunks = [payload[start:start + 100] for start in range(0, len(payload), 100)]
    source += "\n".join('        "' + part + ('";' if index == len(chunks) - 1 else '" +')
                        for index, part in enumerate(chunks)) + "\n}\n"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(source, encoding="utf-8")


def bootstrap(args):
    evidence = Path(args.evidence)
    if evidence.exists():
        raise FileExistsError("Refusing to overwrite bootstrap provenance")
    model, _, _, identity = restore_original(args.checkpoint)
    export_weights(model, args.weights_source, temporary=True)
    write_new(evidence, {"status": "complete", "purpose": "compile-only original V4 payload; not V10 candidate",
                         **identity, "source": str(Path(args.weights_source).resolve()),
                         "source_sha256": digest_file(args.weights_source)})
    print(json.dumps({"bootstrap": True, "payload_sha256": V4_SHA256}), flush=True)


def train(args):
    out, evidence, cache = Path(args.output), Path(args.evidence), Path(args.cache)
    if out.exists() or evidence.exists():
        raise FileExistsError("Refusing to reuse a complete or partial V10 continuation")
    mappings, report = {}, None
    started = time.perf_counter()
    try:
        freeze = verify_freeze(args.freeze)
        data = verify_cache(cache)
        command = read_json(args.java_command_json)
        require(torch.__version__ == "2.8.0+cpu" and np.__version__ == "2.3.5", "Fixed continuation runtime versions differ")
        torch.set_num_threads(CONFIG["torch_threads"])
        torch.set_num_interop_threads(CONFIG["interop_threads"])
        torch.use_deterministic_algorithms(True)
        model, optimizer, original, restoration = restore_original(args.checkpoint)
        target_weights = ROOT / "src/main/java/btk/bot/neural/SpatialV10Weights.java"
        require("temporary" in target_weights.read_text(encoding="utf-8").lower()
                and java_payload_hash(target_weights) == V4_SHA256, "Expected compile-only exact V4 V10 placeholder")
        require(data == original["config"]["data"] and digest_file(cache / "cache.json") == original["config"]["cache_sha256"],
                "Continuation requires byte-exact original V4 cache/data lineage")
        require(data["splits"]["train"]["rows"] == 18192 and data["splits"]["validation"]["rows"] == 4448,
                "Original complete corpus coverage differs")
        config = {**CONFIG, "source_freeze": freeze, "restoration": restoration,
                  "source_sha256": source_identities(), "historical_integrity": historical_integrity(),
                  "runtime": runtime_identity(command), "java_command": command,
                  "java_command_sha256": digest_file(args.java_command_json),
                  "cache_sha256": digest_file(cache / "cache.json"), "data": data,
                  "initial_generated_source_sha256": digest_file(target_weights),
                  "trainable_parameters": sum(parameter.numel() for parameter in model.parameters() if parameter.requires_grad)}
        require(config["trainable_parameters"] == 3424 and rng_equal(original, rng_snapshot()),
                "Setup altered restored RNG or trainable parameter membership")
        del original
        frozen_value = {name: parameter.detach().clone() for name, parameter in model.value.named_parameters()}
        initial_vector = torch.cat([parameter.detach().flatten().clone() for parameter in model.parameters()])
        out.mkdir(parents=True, exist_ok=False)
        evidence.parent.mkdir(parents=True, exist_ok=True)
        write_new(out / "config.json", config)
        report = {"status": "running", "config": config, "initial_weights_sha256": weight_hash(model),
                  "learning_curve": [], "optimization_blocks": [], "epochs": [],
                  "setup_seconds": time.perf_counter() - started}

        def emit(message):
            line = json.dumps(message, allow_nan=False)
            print(line, flush=True)
            with (out / "run.log").open("a", encoding="utf-8") as stream:
                stream.write(line + "\n")

        for split, part in data["splits"].items():
            mappings[split] = np.memmap(cache / part["file"], dtype=ROW_DTYPE, mode="r")
        training = mappings["train"]
        panel = parity_observations(training[:24])
        write_new(out / "parity-observations.json", panel)
        write_new(out / "parity-row-lineage.json", [
            {"row": index, "seed": game["seed"], "teacherSeat": game["teacherSeat"], "mapHash": game["mapHash"],
             "turn": (index - game["cacheRowStart"]) // 4, "stage": index % 4}
            for index in range(24) for game in data["games"] if game["split"] == "train"
            and game["cacheRowStart"] <= index < game["cacheRowStart"] + game["rowCount"]])
        save_checkpoint(out / "initial.pt", model, optimizer, config, 16)
        save_payload(out / "initial-weights.base64", model)
        emit({"started": True, "initial_epoch": 16, "final_epoch": 80, "restored_adam_steps": 4560,
              "additional_epochs": 64, "additional_optimizer_steps": 18240})
        with Bridge(command) as bridge:
            before = rng_snapshot()
            report["initial_parity"] = verify_parity(bridge, model, panel, out, "initial")
            evaluation_started = time.perf_counter()
            initial_metrics = {split: evaluate(model, rows) for split, rows in mappings.items()}
            check_diagnostic_rng(before)
            report["initial_metrics"] = initial_metrics
            report["learning_curve"].append({"epoch": 16, "metrics": initial_metrics,
                "evaluation_seconds": time.perf_counter() - evaluation_started, "rng_unchanged": True})
            report["first_additional_shuffle_rng_sha256"] = rng_hashes(rng_snapshot())
            save_metrics(out / "metrics.json", report)
            block_started, block_loss, block_norm, block_steps, block_rows = time.perf_counter(), 0., 0., 0, 0
            total_steps, total_rows = 0, 0
            for epoch in range(17, 81):
                epoch_started = time.perf_counter()
                model.train()
                permutation = torch.randperm(len(training)).numpy()
                epoch_steps, epoch_rows = 0, 0
                epoch_loss, epoch_norm = 0., 0.
                for indices in batch_indices(permutation):
                    obs, labels, stages = row_tensors(training[indices])
                    losses, _, _ = policy_rows(model, obs, labels, stages)
                    loss = losses.mean()
                    if not torch.isfinite(loss):
                        raise FloatingPointError("Non-finite continuation loss")
                    optimizer.zero_grad(set_to_none=True)
                    loss.backward()
                    norm = torch.nn.utils.clip_grad_norm_(model.parameters(), 1., error_if_nonfinite=True)
                    optimizer.step()
                    if any(not torch.isfinite(parameter).all() for parameter in model.parameters()):
                        raise FloatingPointError("Non-finite continuation coefficient")
                    count = len(labels)
                    weighted_loss, gradient_norm = float(loss.detach()) * count, float(norm)
                    block_loss += weighted_loss
                    block_norm += gradient_norm
                    epoch_loss += weighted_loss
                    epoch_norm += gradient_norm
                    block_steps += 1
                    epoch_steps += 1
                    block_rows += count
                    epoch_rows += count
                require(epoch_steps == 285 and epoch_rows == 18192 and len(indices) == 16,
                        "Original batch count/tail/exposure schedule changed")
                total_steps += epoch_steps
                total_rows += epoch_rows
                report["epochs"].append({"epoch": epoch, "rows": epoch_rows, "optimizer_steps": epoch_steps,
                    "cumulative_adam_steps": 4560 + total_steps, "optimization_cross_entropy": epoch_loss / epoch_rows,
                    "mean_gradient_norm_before_clip": epoch_norm / epoch_steps,
                    "seconds": time.perf_counter() - epoch_started})
                require(all(torch.equal(parameter, frozen_value[name]) for name, parameter in model.value.named_parameters()),
                        "Frozen original V4 value coefficients changed")
                if epoch == 48:
                    save_checkpoint(out / "midpoint.pt", model, optimizer, config, 48)
                    report["midpoint_weights_sha256"] = weight_hash(model)
                if epoch == 80:
                    # Retain the only predeclared endpoint before any post-run
                    # metrics/parity/provenance failure could prevent inspection.
                    save_checkpoint(out / "final.pt", model, optimizer, config, 80)
                    save_payload(out / "final-weights.base64", model)
                if epoch % 8 == 0:
                    block = {"epoch_start": epoch - 7, "epoch_end": epoch, "optimizer_steps": block_steps,
                             "row_exposures": block_rows, "optimization_cross_entropy": block_loss / block_rows,
                             "mean_gradient_norm_before_clip": block_norm / block_steps,
                             "seconds": time.perf_counter() - block_started}
                    before = rng_snapshot()
                    evaluation_started = time.perf_counter()
                    metrics = {split: evaluate(model, rows) for split, rows in mappings.items()}
                    check_diagnostic_rng(before)
                    curve = {"epoch": epoch, "metrics": metrics,
                             "evaluation_seconds": time.perf_counter() - evaluation_started, "rng_unchanged": True}
                    report["learning_curve"].append(curve)
                    report["optimization_blocks"].append(block)
                    save_metrics(out / "metrics.json", report)
                    emit({"epoch": epoch, "adam_steps": 4560 + total_steps, "optimization": block,
                          "train_all": metrics["train"]["slices"]["all"], "validation_all": metrics["validation"]["slices"]["all"]})
                    block_started, block_loss, block_norm, block_steps, block_rows = time.perf_counter(), 0., 0., 0, 0
            report["final_metrics"] = report["learning_curve"][-1]["metrics"]
            before = rng_snapshot()
            report["final_parity"] = verify_parity(bridge, model, panel, out, "final")
            check_diagnostic_rng(before)
        adam_steps = sorted({int(state["step"]) for state in optimizer.state.values()})
        require(adam_steps == [22800] and total_steps == 18240 and total_rows == 1164288,
                "Fixed continuation step/exposure counts differ")
        require(source_identities() == config["source_sha256"] and historical_integrity() == config["historical_integrity"]
                and verify_freeze(args.freeze) == freeze, "Frozen source/history changed during continuation")
        for path, expected in config["runtime"]["compiled_engine_and_classpath_sha256"].items():
            require(digest_file(path) == expected, "Compiled runtime changed during continuation")
        require(digest_file(args.checkpoint) == CHECKPOINT_SHA256 and digest_file(args.java_command_json) == config["java_command_sha256"],
                "Original checkpoint or Java command changed")
        require(verify_cache(cache) == data and digest_file(cache / "cache.json") == config["cache_sha256"],
                "Original corpus/cache changed during continuation")
        require(digest_file(target_weights) == config["initial_generated_source_sha256"], "Generated destination changed before final export")
        export_weights(model, target_weights)
        final_vector = torch.cat([parameter.detach().flatten() for parameter in model.parameters()])
        report.update(status="complete", final_weights_sha256=weight_hash(model), final_epoch=80,
                      additional_optimizer_steps=total_steps, total_optimizer_steps=4560 + total_steps,
                      adam_steps_per_parameter=adam_steps, trained_decision_rows=total_rows,
                      cumulative_trained_decision_rows=291072 + total_rows, value_head_parameters_unchanged=True,
                      final_rng_sha256=rng_hashes(rng_snapshot()),
                      weight_l2_change=float((final_vector - initial_vector).norm()),
                      weight_max_abs_change=float((final_vector - initial_vector).abs().max()),
                      total_seconds=time.perf_counter() - started, python_peak_working_set_bytes=peak_working_set_bytes())
        save_metrics(out / "metrics.json", report)
        write_new(evidence, report)
        emit({"complete": True, "final_epoch": 80, "adam_steps": 22800,
              "final_weights_sha256": report["final_weights_sha256"], "seconds": report["total_seconds"]})
    except Exception as exc:
        if report is not None:
            failure = {"type": type(exc).__name__, "message": str(exc)}
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
    bootstrap_parser = commands.add_parser("bootstrap", help="validate original state and emit only a compile-time initializer")
    bootstrap_parser.add_argument("--weights-source", default="src/main/java/btk/bot/neural/SpatialV10Weights.java")
    bootstrap_parser.add_argument("--evidence", default="build/analysis/spatial-ppo-v10/bootstrap.json")
    train_parser = commands.add_parser("train", help="one original-state continuation; no resume/tuning overrides")
    train_parser.add_argument("--cache", default="build/spatial-ppo-v4-cache")
    train_parser.add_argument("--java-command-json", default="build/spatial-v10-java.json")
    train_parser.add_argument("--freeze", default="build/analysis/spatial-ppo-v10/pretraining-freeze.json")
    train_parser.add_argument("--output", default="build/spatial-ppo-v10")
    train_parser.add_argument("--evidence", default="docs/evidence/spatial-ppo-v10-training.json")
    for command in (bootstrap_parser, train_parser):
        command.add_argument("--checkpoint", default="build/spatial-ppo-v4/final.pt")
    args = parser.parse_args()
    (bootstrap if args.operation == "bootstrap" else train)(args)
