"""One fixed V4-recipe distillation with V9's distance-weighted town context.

Reuses the exact existing teacher corpus/cache and immutable CE/evaluation
helpers. No games, model selection, hyperparameter overrides or resume.
"""
from __future__ import annotations

import argparse
import copy
import json
import math
import os
from pathlib import Path
import time

from train_spatial import ROOT, Bridge, SpatialNet, checkpoint, np, torch
from model import tensor_observations
from model_v9 import SpatialV9Net
from run_v2_training import peak_working_set_bytes, runtime_identities, write_json
from v2_support import weight_hash
from v3_support import WARM_START_SHA256, java_payload_hash
from v4_data import ROW_DTYPE, digest_file, parity_observations, read_json, row_tensors, verify_cache
from v4_distill import (CONFIG as V4_CONFIG, evaluate, fresh_student, policy_rows,
                        verify_freeze, verify_parity)

CONFIG = {**V4_CONFIG, "schema": "spatial-ppo-v9-distillation",
          "architecture": "original local town scatter plus distance-weighted town context before residual",
          "town_context": True, "context_real_cells_only": True,
          "context_formula": "sum_t embedding[t,ch]/(presentTownCount*(1+Manhattan(cell,town)))",
          "context_accumulation": "float32 town order 0..11, add completed sum once; zero if no towns",
          "weight_count": 3439, "expected_trainable_parameters": 3424,
          "expected_training_rows": 18192, "expected_validation_rows": 4448,
          "expected_optimizer_steps": 4560, "expected_row_exposures": 291072,
          "comparison": "same V2 warm start, corpus, split, seed, optimizer and fixed schedule as V4",
          "validation_limit": "original map split retained; validation already exposed by earlier experiments",
          "value_limit": "15 value coefficients frozen; changed shared features; no calibrated-value claim"}


def source_identities():
    paths = list((ROOT / "tools/spatial").glob("*.py"))
    paths += [p for p in (ROOT / "src/main/java/btk").rglob("*.java") if p.name != "SpatialV9Weights.java"]
    paths += [p for p in (ROOT / "src/main/resources/btk").rglob("*") if p.is_file()]
    paths += [ROOT / "docs/spatial-ppo-v9.md"]
    return {p.relative_to(ROOT).as_posix(): digest_file(p) for p in sorted(set(paths))}


def historical_integrity():
    """Compare immutable math/weights, not the intentionally extended Params/factory."""
    result = {}
    for version, weights in ((1, "SpatialWeights"), (2, "SpatialV2Weights"),
                             (3, "SpatialV3Weights"), (4, "SpatialV4Weights"), (5, "SpatialV5Weights")):
        evidence_path = ROOT / f"docs/evidence/spatial-ppo-v{version}-training.json"
        evidence = read_json(evidence_path)
        # V1 predates the explicit status field; its final payload/source hashes
        # below are still mandatory. All later formats must declare completion.
        if evidence.get("status", "complete" if version == 1 else None) != "complete":
            raise AssertionError(f"Historical V{version} is not complete")
        frozen = {name: expected for name, expected in evidence["config"]["source_sha256"].items()
                  if name.startswith(("tools/spatial/", "src/main/java/btk/core/",
                                      "src/main/java/btk/bot/neural/Spatial"))}
        for name, expected in frozen.items():
            if digest_file(ROOT / name) != expected:
                raise AssertionError(f"Frozen V{version} source changed: {name}")
        payload = ROOT / f"src/main/java/btk/bot/neural/{weights}.java"
        if java_payload_hash(payload) != evidence["final_weights_sha256"]:
            raise AssertionError(f"Frozen V{version} generated payload changed")
        result[str(version)] = {"evidence_sha256": digest_file(evidence_path), "verified_sources": len(frozen),
                                "weights_source_sha256": digest_file(payload),
                                "weights_payload_sha256": evidence["final_weights_sha256"]}
    return result


def runtime_identity(command):
    result = runtime_identities(command)
    position = command.index("-cp") if "-cp" in command else command.index("-classpath")
    files = result["compiled_engine_and_classpath_sha256"]
    for entry in command[position + 1].split(os.pathsep):
        path = Path(entry)
        if path.is_dir():
            for file in (path / "btk").rglob("*"):
                if file.is_file() and file.name not in ("SpatialV9Weights.class", "SpatialV9Bot.class"):
                    files[str(file.resolve())] = digest_file(file)
    if not any(Path(name).name == "SpatialV9Bridge.class" for name in files):
        raise FileNotFoundError("Compiled SpatialV9Bridge identity missing")
    return result


def verify_v4_recipe(data, cache, warm_start):
    path = ROOT / "docs/evidence/spatial-ppo-v4-training.json"
    previous = read_json(path)
    if previous["status"] != "complete" or previous["initial_weights_sha256"] != WARM_START_SHA256:
        raise AssertionError("V4 reference experiment identity mismatch")
    for key, value in V4_CONFIG.items():
        if previous["config"][key] != value or (key != "schema" and CONFIG[key] != value):
            raise AssertionError(f"Frozen V4 training recipe differs: {key}")
    if previous["config"]["data"] != data or previous["config"]["cache_sha256"] != digest_file(cache / "cache.json"):
        raise AssertionError("V9 requires the exact existing V4 corpus, cache and map split")
    if previous["config"]["warm_start_checkpoint_sha256"] != digest_file(warm_start):
        raise AssertionError("V9 requires the exact V4 experiment's V2 starting checkpoint")
    if (data["splits"]["train"]["rows"] != CONFIG["expected_training_rows"]
            or data["splits"]["validation"]["rows"] != CONFIG["expected_validation_rows"]):
        raise AssertionError("Fixed V4 row counts changed")
    return {"path": str(path.resolve()), "sha256": digest_file(path),
            "exact_recipe_cache_split_warm_start": True}


class RoutedBridge:
    """Adapt immutable V4 parity helper to the new bridge's model switches."""
    def __init__(self, bridge, town_context, legacy=False):
        self.bridge, self.town_context, self.legacy = bridge, town_context, legacy
        self.reply = None

    def request(self, **message):
        self.reply = self.bridge.request(**message, townContext=self.town_context, legacy=self.legacy)
        return self.reply


def verify_context_off(bridge, model, panel, output, label):
    # This diagnostic must not consume the training shuffle's random stream.
    with torch.random.fork_rng(devices=[]):
        legacy = SpatialNet()
    legacy.load_state_dict(model.state_dict())
    disabled = copy.deepcopy(model)
    disabled.town_context = False
    obs = tensor_observations(panel)
    with torch.no_grad():
        if not all(torch.equal(a, b) for a, b in zip(disabled(obs), legacy(obs))):
            raise AssertionError("Context-off Python model is not bitwise identical to frozen SpatialNet")
    off_route, legacy_route = RoutedBridge(bridge, False), RoutedBridge(bridge, False, True)
    off = verify_parity(off_route, disabled, panel, output, f"{label}-context-off")
    old = verify_parity(legacy_route, legacy, panel, output, f"{label}-legacy")
    for key in ("buildLogits", "disruptLogits", "value"):
        a = np.asarray([row[key] for row in off_route.reply["outputs"]], dtype=np.float32)
        b = np.asarray([row[key] for row in legacy_route.reply["outputs"]], dtype=np.float32)
        if a.tobytes() != b.tobytes():
            raise AssertionError("Context-off Java model is not bitwise identical to frozen SpatialNet")
    return {"python_bitwise_equal": True, "java_bitwise_equal": True,
            "disabled_python_java": off, "legacy_python_java": old}


def export_v9_weights(model, path):
    path = Path(path)
    if path.name != "SpatialV9Weights.java":
        raise ValueError("V9 may generate only SpatialV9Weights.java")
    payload = model.encoded_weights()
    chunks = [payload[start:start + 100] for start in range(0, len(payload), 100)]
    source = ("package btk.bot.neural;\n\n"
              "/** Generated by tools/spatial/v9_distill.py; do not edit weights manually. */\n"
              "public final class SpatialV9Weights {\n"
              "    private SpatialV9Weights() {}\n"
              "    public static final String DATA =\n")
    source += "\n".join('        "' + part + ('";' if i == len(chunks) - 1 else '" +')
                        for i, part in enumerate(chunks)) + "\n}\n"
    path.write_text(source, encoding="utf-8")


def train(args):
    out, evidence, cache = Path(args.output), Path(args.evidence), Path(args.cache)
    if out.exists() or evidence.exists():
        raise FileExistsError("Refusing to reuse a complete or partial V9 run/evidence")
    guards = {"source": verify_freeze(args.source_freeze), "data": verify_freeze(args.data_freeze)}
    validation_start = time.perf_counter()
    data = verify_cache(cache)
    reference = verify_v4_recipe(data, cache, args.warm_start)
    command = read_json(args.java_command_json)
    config = {**CONFIG, "provenance_freezes": guards, "v4_reference": reference,
              "cache_sha256": digest_file(cache / "cache.json"), "data": data,
              "java_command": command, "java_command_sha256": digest_file(args.java_command_json),
              "source_sha256": source_identities(), "frozen_history": historical_integrity(),
              "runtime": runtime_identity(command), "warm_start_checkpoint_sha256": digest_file(args.warm_start)}
    if torch.__version__ != "2.8.0+cpu" or np.__version__ != "2.3.5":
        raise RuntimeError("Fixed V9 requires PyTorch 2.8.0+cpu and NumPy 2.3.5")
    torch.set_num_threads(CONFIG["torch_threads"])
    torch.set_num_interop_threads(1)
    torch.use_deterministic_algorithms(True)
    model = SpatialV9Net()
    saved = torch.load(args.warm_start, map_location="cpu", weights_only=False)
    model.load_state_dict(saved["model"])
    del saved
    optimizer = fresh_student(model)
    frozen_value = {name: parameter.detach().clone() for name, parameter in model.value.named_parameters()}
    initial_vector = torch.cat([p.detach().flatten().clone() for p in model.parameters()])
    expected_steps = CONFIG["epochs"] * math.ceil(data["splits"]["train"]["rows"] / CONFIG["minibatch_decisions"])
    config["trainable_parameters"] = sum(p.numel() for p in model.parameters() if p.requires_grad)
    if expected_steps != CONFIG["expected_optimizer_steps"] or config["trainable_parameters"] != 3424:
        raise AssertionError("Fixed parameter/optimizer counts changed")
    out.mkdir(parents=True, exist_ok=False)
    evidence.parent.mkdir(parents=True, exist_ok=True)
    write_json(out / "config.json", config)
    report = {"status": "running", "config": config, "epochs": [], "initial_weights_sha256": weight_hash(model),
              "cache_validation_seconds": time.perf_counter() - validation_start}
    started, mappings = time.perf_counter(), {}

    def emit(message):
        line = json.dumps(message, allow_nan=False)
        print(line, flush=True)
        with (out / "run.log").open("a", encoding="utf-8") as stream:
            stream.write(line + "\n")

    try:
        for split, part in data["splits"].items():
            mappings[split] = np.memmap(cache / part["file"], dtype=ROW_DTYPE, mode="r")
        training = mappings["train"]
        panel = parity_observations(training[:CONFIG["parity_rows"]])
        write_json(out / "parity-observations.json", panel)
        write_json(out / "parity-row-lineage.json", [
            {"row": index, "seed": game["seed"], "teacherSeat": game["teacherSeat"], "mapHash": game["mapHash"],
             "turn": (index - game["cacheRowStart"]) // 4, "stage": index % 4}
            for index in range(CONFIG["parity_rows"])
            for game in data["games"] if game["split"] == "train"
            and game["cacheRowStart"] <= index < game["cacheRowStart"] + game["rowCount"]])
        checkpoint(out / "initial.pt", model, optimizer, config, 0)
        (out / "initial-weights.base64").write_text(model.encoded_weights(), encoding="ascii")
        emit({"started": True, "epochs": 16, "training_rows": len(training), "expected_adam_steps": expected_steps})
        with Bridge(command) as bridge:
            report["initial_context_off"] = verify_context_off(bridge, model, panel, out, "initial")
            report["initial_parity"] = verify_parity(RoutedBridge(bridge, True), model, panel, out, "initial")
            evaluate_start = time.perf_counter()
            report["initial_metrics"] = {split: evaluate(model, rows) for split, rows in mappings.items()}
            report["initial_evaluation_seconds"] = time.perf_counter() - evaluate_start
            write_json(out / "metrics.json", report)
            for epoch in range(1, CONFIG["epochs"] + 1):
                epoch_start = time.perf_counter()
                model.train()
                permutation = torch.randperm(len(training)).numpy()
                loss_sum, norm_sum, steps, input_seconds = 0., 0., 0, 0.
                for start in range(0, len(training), CONFIG["minibatch_decisions"]):
                    input_start = time.perf_counter()
                    obs, labels, stages = row_tensors(training[permutation[start:start + CONFIG["minibatch_decisions"]]])
                    input_seconds += time.perf_counter() - input_start
                    losses, _, _ = policy_rows(model, obs, labels, stages)
                    loss = losses.mean()
                    if not torch.isfinite(loss):
                        raise FloatingPointError("Non-finite supervised loss")
                    optimizer.zero_grad(set_to_none=True)
                    loss.backward()
                    norm = torch.nn.utils.clip_grad_norm_(model.parameters(), CONFIG["gradient_clip"], error_if_nonfinite=True)
                    optimizer.step()
                    if any(not torch.isfinite(p).all() for p in model.parameters()):
                        raise FloatingPointError("Non-finite updated parameter")
                    loss_sum += float(loss.detach()) * len(labels)
                    norm_sum += float(norm)
                    steps += 1
                if any(not torch.equal(parameter, frozen_value[name]) for name, parameter in model.value.named_parameters()):
                    raise AssertionError("Frozen value-head parameters changed")
                row = {"epoch": epoch, "rows": len(training), "optimizer_steps": steps,
                       "optimization_cross_entropy": loss_sum / len(training), "mean_gradient_norm_before_clip": norm_sum / steps,
                       "seconds": time.perf_counter() - epoch_start, "minibatch_input_seconds": input_seconds}
                report["epochs"].append(row)
                if epoch == 8:
                    checkpoint(out / "midpoint.pt", model, optimizer, config, epoch)
                    report["midpoint_weights_sha256"] = weight_hash(model)
                write_json(out / "metrics.json", report)
                emit(row)
            evaluate_start = time.perf_counter()
            report["final_metrics"] = {split: evaluate(model, rows) for split, rows in mappings.items()}
            report["final_evaluation_seconds"] = time.perf_counter() - evaluate_start
            report["final_context_off"] = verify_context_off(bridge, model, panel, out, "final")
            report["final_parity"] = verify_parity(RoutedBridge(bridge, True), model, panel, out, "final")
        adam_steps = sorted({int(state["step"]) for state in optimizer.state.values()})
        if adam_steps != [expected_steps] or sum(row["optimizer_steps"] for row in report["epochs"]) != expected_steps:
            raise AssertionError("Fixed supervised optimizer step count mismatch")
        if source_identities() != config["source_sha256"] or historical_integrity() != config["frozen_history"]:
            raise AssertionError("Historical or current frozen source changed during training")
        for filename, expected in config["runtime"]["compiled_engine_and_classpath_sha256"].items():
            if digest_file(filename) != expected:
                raise AssertionError(f"Compiled runtime changed: {filename}")
        if guards != {"source": verify_freeze(args.source_freeze), "data": verify_freeze(args.data_freeze)}:
            raise AssertionError("Provenance freezes changed during training")
        if digest_file(args.java_command_json) != config["java_command_sha256"]:
            raise AssertionError("Java launch command changed during training")
        if digest_file(cache / "cache.json") != config["cache_sha256"] or verify_cache(cache) != data:
            raise AssertionError("Validated corpus/cache changed during training")
        if verify_v4_recipe(data, cache, args.warm_start) != reference:
            raise AssertionError("Frozen V4 experiment reference changed")
        checkpoint(out / "final.pt", model, optimizer, config, 16)
        export_v9_weights(model, ROOT / "src/main/java/btk/bot/neural/SpatialV9Weights.java")
        (out / "final-weights.base64").write_text(model.encoded_weights(), encoding="ascii")
        final_vector = torch.cat([p.detach().flatten() for p in model.parameters()])
        report.update(status="complete", final_weights_sha256=weight_hash(model), adam_steps_per_parameter=adam_steps,
                      total_optimizer_steps=sum(row["optimizer_steps"] for row in report["epochs"]),
                      trained_decision_rows=16 * len(training), value_head_parameters_unchanged=True,
                      weight_l2_change=float((final_vector - initial_vector).norm()),
                      weight_max_abs_change=float((final_vector - initial_vector).abs().max()),
                      total_seconds=time.perf_counter() - started, python_peak_working_set_bytes=peak_working_set_bytes())
        if report["trained_decision_rows"] != CONFIG["expected_row_exposures"]:
            raise AssertionError("Fixed decision-row exposure count mismatch")
        write_json(out / "metrics.json", report)
        write_json(evidence, report)
        emit({"complete": True, "final_weights_sha256": report["final_weights_sha256"],
              "optimizer_steps": report["total_optimizer_steps"], "seconds": report["total_seconds"]})
    except Exception as exc:
        report.update(status="failed", failure={"type": type(exc).__name__, "message": str(exc)},
                      total_seconds=time.perf_counter() - started)
        write_json(out / "metrics.json", report)
        write_json(out / "failure.json", report["failure"])
        write_json(evidence, report)
        emit({"failed": True, **report["failure"]})
        raise
    finally:
        for rows in mappings.values():
            rows._mmap.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-command-json", default="build/spatial-v9-java.json")
    parser.add_argument("--output", default="build/spatial-ppo-v9")
    parser.add_argument("--evidence", default="docs/evidence/spatial-ppo-v9-training.json")
    parser.add_argument("--warm-start", default="build/spatial-ppo-v2/final.pt")
    parser.add_argument("--cache", default="build/spatial-ppo-v4-cache")
    parser.add_argument("--source-freeze", default="build/analysis/spatial-ppo-v9/pretraining-freeze.json")
    parser.add_argument("--data-freeze", default="build/analysis/spatial-ppo-v4/data-freeze.json")
    train(parser.parse_args())
