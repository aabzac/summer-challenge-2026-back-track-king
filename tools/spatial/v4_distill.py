"""Frozen v4 hard-action distillation; prepare lossless cache, then one fixed run.

No teacher games, PPO, hyperparameter overrides, checkpoint selection or resume.
"""
from __future__ import annotations

import argparse
import math
import os
from pathlib import Path
import random
import time

from train_spatial import ROOT, Bridge, SpatialNet, checkpoint, np, torch
from model import tensor_observations
from run_v2_training import peak_working_set_bytes, runtime_identities, write_json
from v2_support import weight_hash
from v3_support import WARM_START_SHA256, frozen_history_integrity, java_payload_hash
from v4_data import (ROW_BYTES, ROW_DTYPE, digest_file, parity_observations, prepare_cache,
                     read_json, row_tensors, verify_cache)

CONFIG = {"schema": "spatial-ppo-v4-distillation", "seed": 20261010, "epochs": 16,
          "minibatch_decisions": 64, "learning_rate": .001, "gradient_clip": 1.,
          "torch_threads": 2, "checkpoint_epochs": [0, 8, 16],
          "checkpoint_iteration_field": "completed supervised epochs; not PPO updates",
          "checkpoint_purpose": "inspection/reproduction; no exact-resume interface",
          "warm_start_payload_sha256": WARM_START_SHA256,
          "objective": "mean masked hard-label cross entropy over all four decision rows",
          "shuffle": "torch.randperm of training decision rows each epoch; retain last batch",
          "value_head": "frozen parameters; shared trunk changes; values remain uncalibrated",
          "metric_epochs": [0, 16], "parity_rows": 24,
          "parity_selection": "first six teacher turns in train seed/seat/turn/stage order"}


def verify_freeze(path):
    frozen = read_json(path)
    files = frozen["files"]
    if not files:
        raise ValueError("An empty provenance freeze cannot authorize data or gradients")
    for name, expected in files.items():
        if digest_file(ROOT / name) != expected:
            raise AssertionError(f"Frozen input changed: {name}")
    return {"path": str(Path(path).resolve()), "sha256": digest_file(path), "verified_files": len(files)}


def source_identities():
    paths = list((ROOT / "tools/spatial").glob("*.py"))
    paths += [p for p in (ROOT / "src/main/java/btk").rglob("*.java") if p.name != "SpatialV4Weights.java"]
    paths += [p for p in (ROOT / "src/main/resources/btk").rglob("*") if p.is_file()]
    paths += [ROOT / "docs/spatial-ppo-v4.md"]
    return {p.relative_to(ROOT).as_posix(): digest_file(p) for p in sorted(set(paths))}


def historical_integrity():
    result = frozen_history_integrity()
    evidence_path = ROOT / "docs/evidence/spatial-ppo-v3-training.json"
    evidence = read_json(evidence_path)
    # Factory/catalog files legitimately gained a new variant; previous training
    # modules, spatial runtime and core still have their exact historical bytes.
    frozen = {name: digest for name, digest in evidence["config"]["source_sha256"].items()
              if name.startswith(("tools/spatial/", "src/main/java/btk/core/"))
              or name.startswith("src/main/java/btk/bot/neural/Spatial")}
    for name, expected in frozen.items():
        if digest_file(ROOT / name) != expected:
            raise AssertionError(f"Frozen v3 source changed: {name}")
    payload = ROOT / "src/main/java/btk/bot/neural/SpatialV3Weights.java"
    if java_payload_hash(payload) != evidence["final_weights_sha256"]:
        raise AssertionError("Frozen v3 generated weights changed")
    return {**result, "v3_evidence_sha256": digest_file(evidence_path),
            "v3_verified_source_count": len(frozen), "v3_weights_source_sha256": digest_file(payload),
            "v3_weights_payload_sha256": evidence["final_weights_sha256"]}


def full_runtime_identity(command):
    result = runtime_identities(command)
    position = command.index("-cp") if "-cp" in command else command.index("-classpath")
    for entry in command[position + 1].split(os.pathsep):
        path = Path(entry)
        if path.is_dir():
            for file in (path / "btk").rglob("*"):
                if file.is_file() and file.name not in ("SpatialV4Weights.class", "SpatialV4Bot.class"):
                    result["compiled_engine_and_classpath_sha256"][str(file.resolve())] = digest_file(file)
    return result


def fresh_student(model):
    if weight_hash(model) != WARM_START_SHA256:
        raise AssertionError("V4 must start from the exact frozen v2 final payload")
    model.value.requires_grad_(False)
    random.seed(CONFIG["seed"])
    np.random.seed(CONFIG["seed"])
    torch.manual_seed(CONFIG["seed"])
    return torch.optim.Adam([p for p in model.parameters() if p.requires_grad], lr=CONFIG["learning_rate"])


def policy_rows(model, obs, labels, stages):
    build, disrupt, _ = model(obs)
    if not torch.isfinite(build).all() or not torch.isfinite(disrupt).all():
        raise FloatingPointError("Non-finite student policy logits")
    if not ((stages >= 0) & (stages <= 3)).all() or not ((labels >= 0) & (labels <= 600)).all():
        raise ValueError("Invalid conditional label/stage")
    is_disrupt = (stages == 3)[:, None]
    logits = torch.where(is_disrupt, disrupt, build)
    masks = torch.where(is_disrupt, obs["disruptMask"], obs["buildMask"])
    if not masks.any(-1).all() or not masks.gather(1, labels[:, None]).all():
        raise ValueError("Empty action mask or illegal hard-label target")
    masked = logits.masked_fill(~masks, -torch.inf)
    losses = torch.nn.functional.cross_entropy(masked, labels, reduction="none")
    return losses, masked.argmax(-1), masks.sum(-1) > 1


def evaluate(model, rows):
    model.eval()
    masks = {"all": lambda s, u: torch.ones_like(u), "unforced": lambda s, u: u,
             "build": lambda s, u: s < 3, "disrupt": lambda s, u: s == 3,
             "build_unforced": lambda s, u: (s < 3) & u,
             "disrupt_unforced": lambda s, u: (s == 3) & u}
    stats = {name: {"rows": 0, "correct": 0, "cross_entropy_sum": 0.} for name in masks}
    correct_plans = np.empty(len(rows), dtype=bool)
    with torch.no_grad():
        for start in range(0, len(rows), CONFIG["minibatch_decisions"]):
            obs, labels, stages = row_tensors(rows[start:start + CONFIG["minibatch_decisions"]])
            losses, predictions, unforced = policy_rows(model, obs, labels, stages)
            correct = predictions == labels
            correct_plans[start:start + len(labels)] = correct.numpy()
            for name, selector in masks.items():
                selected = selector(stages, unforced)
                stats[name]["rows"] += int(selected.sum())
                stats[name]["correct"] += int(correct[selected].sum())
                stats[name]["cross_entropy_sum"] += float(losses[selected].double().sum())
    if len(rows) % 4:
        raise ValueError("Full-plan diagnostic requires four consecutive teacher-prefix rows")
    for values in stats.values():
        count = values["rows"]
        values["cross_entropy"] = values.pop("cross_entropy_sum") / count if count else None
        values["agreement"] = values["correct"] / count if count else None
    full = correct_plans.reshape(-1, 4).all(1)
    return {"slices": stats, "full_plans": len(full), "exact_full_plans": int(full.sum()),
            "exact_full_plan_agreement": float(full.mean()),
            "interpretation": "teacher-prefix agreement; not student rollouts or game strength"}


def verify_parity(bridge, model, panel, output, label):
    with torch.no_grad():
        python = [value.numpy() for value in model(tensor_observations(panel))]
    reply = bridge.request(op="forward", weights=model.encoded_weights(), observations=panel)["outputs"]
    java = [np.asarray([row[name] for row in reply], dtype=np.float32)
            for name in ("buildLogits", "disruptLogits", "value")]
    if len(panel) != CONFIG["parity_rows"] or any(a.shape != b.shape for a, b in zip(python, java)):
        raise AssertionError("Fixed parity panel/output shape mismatch")
    if any(not np.isfinite(value).all() for value in python + java):
        raise FloatingPointError("Non-finite Python/Java parity output")
    errors = {key: float(np.max(np.abs(a - b)))
              for key, a, b in zip(("build", "disrupt", "value"), python, java)}
    greedy = True
    for i, name in enumerate(("buildMask", "disruptMask")):
        mask = np.asarray([row[name] for row in panel], dtype=bool)
        greedy &= bool(np.array_equal(np.where(mask, python[i], -np.inf).argmax(1),
                                      np.where(mask, java[i], -np.inf).argmax(1)))
    result = {"observations": len(panel), "max_abs_error": errors, "tolerance": 1e-4,
              "greedy_equal": greedy, "weights_sha256": weight_hash(model),
              "passed": greedy and max(errors.values()) <= 1e-4}
    write_json(Path(output) / f"parity-{label}.json", result)
    if not result["passed"]:
        raise AssertionError(f"Python/Java parity failed: {result}")
    return result


def export_v4_weights(model, path):
    path = Path(path)
    if path.name != "SpatialV4Weights.java":
        raise ValueError("V4 may generate only SpatialV4Weights.java")
    payload = model.encoded_weights()
    chunks = [payload[start:start + 100] for start in range(0, len(payload), 100)]
    source = ("package btk.bot.neural;\n\n"
              "/** Generated by tools/spatial/v4_distill.py; do not edit weights manually. */\n"
              "public final class SpatialV4Weights {\n"
              "    private SpatialV4Weights() {}\n"
              "    public static final String DATA =\n")
    source += "\n".join('        "' + part + ('";' if i == len(chunks) - 1 else '" +')
                        for i, part in enumerate(chunks)) + "\n}\n"
    path.write_text(source, encoding="utf-8")


def prepare(args):
    guards = {"collection": verify_freeze(args.collection_freeze), "data": verify_freeze(args.data_freeze)}
    started = time.perf_counter()
    report = prepare_cache(args.corpus, args.cache, args.excluded_maps)
    if guards != {"collection": verify_freeze(args.collection_freeze), "data": verify_freeze(args.data_freeze)}:
        raise AssertionError("Provenance freezes changed during cache preparation")
    report.update(provenance_freezes=guards, prepare_seconds=time.perf_counter() - started)
    write_json(Path(args.cache) / "cache.json", report)
    print({"prepared": True, **report["summary"], "seconds": report["prepare_seconds"]}, flush=True)


def train(args):
    out, evidence, cache = Path(args.output), Path(args.evidence), Path(args.cache)
    if out.exists() or evidence.exists():
        raise FileExistsError("Refusing to reuse a complete or partial v4 run/output evidence")
    guards = {"collection": verify_freeze(args.collection_freeze), "data": verify_freeze(args.data_freeze)}
    validation_start = time.perf_counter()
    data = verify_cache(cache)
    if data.get("provenance_freezes") != guards:
        raise AssertionError("Cache was not prepared against these exact provenance freezes")
    command = read_json(args.java_command_json)
    config = {**CONFIG, "provenance_freezes": guards, "cache_sha256": digest_file(cache / "cache.json"),
              "data": data, "java_command": command, "java_command_sha256": digest_file(args.java_command_json),
              "source_sha256": source_identities(), "frozen_history": historical_integrity(),
              "runtime": full_runtime_identity(command), "warm_start_checkpoint_sha256": digest_file(args.warm_start)}
    if torch.__version__ != "2.8.0+cpu" or np.__version__ != "2.3.5":
        raise RuntimeError("Fixed v4 requires PyTorch 2.8.0+cpu and NumPy 2.3.5")
    torch.set_num_threads(CONFIG["torch_threads"])
    torch.set_num_interop_threads(1)
    torch.use_deterministic_algorithms(True)
    model = SpatialNet()
    saved = torch.load(args.warm_start, map_location="cpu", weights_only=False)
    model.load_state_dict(saved["model"])
    del saved
    optimizer = fresh_student(model)
    frozen_value = {name: parameter.detach().clone() for name, parameter in model.value.named_parameters()}
    initial_vector = torch.cat([p.detach().flatten().clone() for p in model.parameters()])
    config["expected_adam_steps"] = CONFIG["epochs"] * math.ceil(data["splits"]["train"]["rows"] / CONFIG["minibatch_decisions"])
    config["trainable_parameters"] = sum(p.numel() for p in model.parameters() if p.requires_grad)
    out.mkdir(parents=True, exist_ok=False)
    evidence.parent.mkdir(parents=True, exist_ok=True)
    write_json(out / "config.json", config)
    report = {"status": "running", "config": config, "epochs": [], "initial_weights_sha256": weight_hash(model),
              "cache_validation_seconds": time.perf_counter() - validation_start}
    started, mappings = time.perf_counter(), {}

    def emit(message):
        import json
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
        emit({"started": True, "epochs": 16, "training_rows": len(training), "expected_adam_steps": config["expected_adam_steps"]})
        with Bridge(command) as bridge:
            report["initial_parity"] = verify_parity(bridge, model, panel, out, "initial")
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
            report["final_parity"] = verify_parity(bridge, model, panel, out, "final")
        adam_steps = sorted({int(state["step"]) for state in optimizer.state.values()})
        if adam_steps != [config["expected_adam_steps"]]:
            raise AssertionError("Fixed supervised optimizer step count mismatch")
        if source_identities() != config["source_sha256"] or historical_integrity() != config["frozen_history"]:
            raise AssertionError("Historical or current frozen source changed during training")
        for filename, expected in config["runtime"]["compiled_engine_and_classpath_sha256"].items():
            if digest_file(filename) != expected:
                raise AssertionError(f"Compiled runtime changed: {filename}")
        if guards != {"collection": verify_freeze(args.collection_freeze), "data": verify_freeze(args.data_freeze)}:
            raise AssertionError("Provenance freezes changed during training")
        if digest_file(cache / "cache.json") != config["cache_sha256"] or verify_cache(cache) != data:
            raise AssertionError("Validated corpus/cache changed during training")
        if digest_file(args.warm_start) != config["warm_start_checkpoint_sha256"]:
            raise AssertionError("Frozen warm-start checkpoint changed")
        checkpoint(out / "final.pt", model, optimizer, config, 16)
        export_v4_weights(model, ROOT / "src/main/java/btk/bot/neural/SpatialV4Weights.java")
        (out / "final-weights.base64").write_text(model.encoded_weights(), encoding="ascii")
        final_vector = torch.cat([p.detach().flatten() for p in model.parameters()])
        report.update(status="complete", final_weights_sha256=weight_hash(model), adam_steps_per_parameter=adam_steps,
                      total_optimizer_steps=sum(row["optimizer_steps"] for row in report["epochs"]),
                      trained_decision_rows=16 * len(training), value_head_parameters_unchanged=True,
                      weight_l2_change=float((final_vector - initial_vector).norm()),
                      weight_max_abs_change=float((final_vector - initial_vector).abs().max()),
                      total_seconds=time.perf_counter() - started, python_peak_working_set_bytes=peak_working_set_bytes())
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
    commands = parser.add_subparsers(dest="operation", required=True)
    cache_parser = commands.add_parser("prepare", help="validate complete corpus and create a fresh disk cache")
    cache_parser.add_argument("--corpus", default="build/spatial-ppo-v4-data")
    cache_parser.add_argument("--excluded-maps", required=True)
    train_parser = commands.add_parser("train", help="one frozen 16-epoch run; no resume or tuning")
    train_parser.add_argument("--java-command-json", required=True)
    train_parser.add_argument("--output", default="build/spatial-ppo-v4")
    train_parser.add_argument("--evidence", default="docs/evidence/spatial-ppo-v4-training.json")
    train_parser.add_argument("--warm-start", default="build/spatial-ppo-v2/final.pt")
    for command_parser in (cache_parser, train_parser):
        command_parser.add_argument("--cache", default="build/spatial-ppo-v4-cache")
        command_parser.add_argument("--collection-freeze", default="build/analysis/spatial-ppo-v4/precollection-freeze.json")
        command_parser.add_argument("--data-freeze", default="build/analysis/spatial-ppo-v4/data-freeze.json")
    arguments = parser.parse_args()
    (prepare if arguments.operation == "prepare" else train)(arguments)
