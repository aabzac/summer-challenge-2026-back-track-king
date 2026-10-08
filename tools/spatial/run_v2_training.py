"""One prospectively fixed complete-game PPO run; imports the frozen v1 math.

python tools/spatial/run_v2_training.py --java-command-json build/spatial-java.json
No checkpoint/environment resume or hyperparameter overrides are offered.
"""
from __future__ import annotations

import argparse
import ctypes
import hashlib
import json
import os
from pathlib import Path
import platform
import random
import re
import subprocess
import sys
import time

from train_spatial import (ROOT, DEFAULT_CONFIG, Collector, SpatialNet, checkpoint,
                           np, parity, torch, update)
from v2_support import GameAudit, INITIAL_SHA256, TimedBridge, TimedSpatialNet, export_java_class, weight_hash

CONFIG = {**DEFAULT_CONFIG, "schema": "spatial-ppo-v2", "map_seed_start": 10000000,
          "iterations": 64, "environments": 8, "rollout_turns": 16,
          "checkpoint_iterations": [0, 32, 64], "expected_environment_turns": 8192,
          "expected_seat_samples": 16384, "expected_adam_steps": 1024,
          "checkpoint_purpose": "inspection/reproduction; environment state is not saved for exact resume",
          "initial_payload_sha256": INITIAL_SHA256,
          "late_training_panel": {"initial_environment_slots": [0, 1], "turns": [63, 95],
                                  "seats": [0, 1], "stages": [0, 1, 2, 3]}}


def sha256(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def source_identities():
    sources = list(Path(__file__).parent.glob("*.py"))
    for pattern in ("src/main/java/btk/core/*.java", "src/main/java/btk/tools/spatial/*.java",
                    "src/main/java/btk/bot/neural/SpatialNet.java",
                    "src/main/java/btk/bot/neural/SpatialObservation.java",
                    "src/main/java/btk/bot/neural/SpatialBot.java",
                    "src/main/java/btk/referee/MapGenerator.java", "src/main/java/btk/referee/GridMaker.java"):
        sources.extend(ROOT.glob(pattern))
    return {str(p.relative_to(ROOT)).replace("\\", "/"): sha256(p) for p in sorted(set(sources))}


def frozen_v1_integrity():
    evidence_path = ROOT / "docs/evidence/spatial-ppo-v1-training.json"
    evidence = json.loads(evidence_path.read_text(encoding="utf-8-sig"))
    expected = evidence["config"]["source_sha256"]
    for name, digest in expected.items():
        if sha256(ROOT / name) != digest:
            raise AssertionError(f"Frozen v1 source differs from original training evidence: {name}")
    weights_path = ROOT / "src/main/java/btk/bot/neural/SpatialWeights.java"
    payload = "".join(re.findall(r'"([A-Za-z0-9+/=]+)"', weights_path.read_text(encoding="utf-8")))
    import base64
    if hashlib.sha256(base64.b64decode(payload)).hexdigest() != evidence["final_weights_sha256"]:
        raise AssertionError("Frozen v1 generated weights changed")
    return {"evidence_sha256": sha256(evidence_path), "verified_source_count": len(expected),
            "weights_source_sha256": sha256(weights_path), "weights_payload_sha256": evidence["final_weights_sha256"]}


def runtime_identities(command):
    java_version = subprocess.run([command[0], "-version"], capture_output=True, text=True, check=True)
    classpath_at = command.index("-cp") if "-cp" in command else command.index("-classpath")
    files = []
    for item in command[classpath_at + 1].split(os.pathsep):
        entry = Path(item)
        if entry.is_file():
            files.append(entry)
        elif entry.is_dir():
            for pattern in ("btk/core/**/*.class", "btk/tools/spatial/SpatialBridge*.class",
                            "btk/bot/neural/SpatialNet*.class", "btk/bot/neural/SpatialObservation*.class",
                            "btk/referee/MapGenerator*.class", "btk/referee/GridMaker*.class"):
                files.extend(entry.glob(pattern))
    if not any(p.name == "SpatialBridge.class" for p in files):
        raise FileNotFoundError("Compiled SpatialBridge identity could not be established")
    return {"python_version": platform.python_version(), "python_executable": sys.executable,
            "python_executable_sha256": sha256(sys.executable), "platform": platform.platform(),
            "torch_version": torch.__version__, "numpy_version": np.__version__,
            "java_version": (java_version.stdout + java_version.stderr).strip(),
            "java_executable_sha256": sha256(command[0]),
            "compiled_engine_and_classpath_sha256": {str(p.resolve()): sha256(p) for p in sorted(set(files))}}


def peak_working_set_bytes():
    if os.name != "nt":
        import resource
        return resource.getrusage(resource.RUSAGE_SELF).ru_maxrss * 1024
    class Counters(ctypes.Structure):
        _fields_ = [("cb", ctypes.c_ulong), ("PageFaultCount", ctypes.c_ulong),
                    *[(name, ctypes.c_size_t) for name in ("PeakWorkingSetSize", "WorkingSetSize",
                       "QuotaPeakPagedPoolUsage", "QuotaPagedPoolUsage", "QuotaPeakNonPagedPoolUsage",
                       "QuotaNonPagedPoolUsage", "PagefileUsage", "PeakPagefileUsage")]]
    counters = Counters()
    counters.cb = ctypes.sizeof(counters)
    current_process = ctypes.windll.kernel32.GetCurrentProcess
    current_process.restype = ctypes.c_void_p
    get_memory = ctypes.windll.psapi.GetProcessMemoryInfo
    get_memory.argtypes = (ctypes.c_void_p, ctypes.POINTER(Counters), ctypes.c_ulong)
    if not get_memory(current_process(), ctypes.byref(counters), counters.cb):
        return None
    return int(counters.PeakWorkingSetSize)


def same_rollout_value_metrics(model, rollout):
    with torch.no_grad():
        stage_zero = {key: data[::4] for key, data in rollout["observations"].items()}
        values = torch.cat([model({key: data[start:start + 32] for key, data in stage_zero.items()})[2]
                            for start in range(0, len(rollout["actions"]), 32)])
        targets = rollout["returns"].flatten()
        mse = float((values - targets).square().mean())
        variance = float(targets.var(unbiased=False))
        return {"value_mse_after": mse, "value_mse_units": "squared normalized return (reward /100)",
                "return_variance": variance,
                "value_mse_div_return_variance": mse / variance if variance > 1e-12 else None}


def first_panel(collector):
    # Fixed first four environments, both seats, all stages, rollout turns 0/15.
    full = collector.parity_observations
    if len(full) != 128:
        raise AssertionError(f"Unexpected frozen collector initial panel size {len(full)}")
    selected = [full[offset + i] for offset in range(0, 128, 16) for i in range(8)]
    collector.parity_observations.clear()
    return selected


def write_json(path, value):
    Path(path).write_text(json.dumps(value, indent=2, allow_nan=False) + "\n", encoding="utf-8")


def run(args):
    config = dict(CONFIG)
    command_path = Path(args.java_command_json)
    command = json.loads(command_path.read_text(encoding="utf-8-sig"))
    exclusion_path = Path(args.excluded_maps)
    excluded = set(json.loads(exclusion_path.read_text(encoding="utf-8-sig"))["gamesPerMap"])
    out, evidence = Path(args.output), Path(args.evidence)
    if out.exists() or evidence.exists():
        raise FileExistsError("Refusing to reuse v2 output directory or evidence; preserve prior attempts")
    config["java_command"] = command
    config["frozen_v1_integrity"] = frozen_v1_integrity()
    config["java_command_file_sha256"] = sha256(command_path)
    config["source_sha256"] = source_identities()
    config["runtime"] = runtime_identities(command)
    config["protocol_sha256"] = sha256(ROOT / "docs/spatial-ppo-v2.md")
    config["prior_map_exclusion_index"] = {"path": str(exclusion_path), "sha256": sha256(exclusion_path),
                                          "map_hash_count": len(excluded)}
    if torch.__version__ != "2.8.0+cpu" or np.__version__ != "2.3.5":
        raise AssertionError("Runtime versions differ from the prospectively fixed v2 protocol")
    random.seed(config["initialization_seed"])
    np.random.seed(config["initialization_seed"])
    torch.manual_seed(config["initialization_seed"])
    torch.set_num_threads(config["torch_threads"])
    torch.set_num_interop_threads(1)
    torch.use_deterministic_algorithms(True)
    model = TimedSpatialNet()
    if weight_hash(model) != INITIAL_SHA256:
        raise AssertionError("Initializer does not exactly match frozen v1 initial payload")
    initial_state = {key: value.detach().clone() for key, value in model.state_dict().items()}
    initial_vector = torch.cat([p.detach().flatten().clone() for p in model.parameters()])
    optimizer = torch.optim.Adam(model.parameters(), lr=config["learning_rate"])
    out.mkdir(parents=True, exist_ok=False)
    evidence.parent.mkdir(parents=True, exist_ok=True)
    write_json(out / "config.json", config)
    report = {"status": "running", "config": config, "iterations": [], "initial_weights_sha256": weight_hash(model)}
    audit = GameAudit(config["map_seed_start"], config["environments"], config["terminal_bonus"],
                      config["reward_scale"], excluded)
    started = time.perf_counter()

    def emit(value):
        line = json.dumps(value, allow_nan=False)
        print(line, flush=True)
        with (out / "run.log").open("a", encoding="utf-8") as stream:
            stream.write(line + "\n")

    try:
        checkpoint(out / "initial.pt", model, optimizer, config, 0)
        (out / "initial-weights.base64").write_text(model.encoded_weights(), encoding="ascii")
        export_java_class(model, ROOT / "src/main/java/btk/bot/neural/SpatialInitialWeights.java", "SpatialInitialWeights")
        emit({"started": True, "iterations": 64, "environment_turns": 8192, "seat_samples": 16384,
              "initial_weights_sha256": report["initial_weights_sha256"]})
        with TimedBridge(command, audit) as bridge:
            collector = Collector(bridge, config)
            panel = []
            for iteration in range(1, config["iterations"] + 1):
                before_transport = dict(bridge.timings)
                before_forward = model.forward_seconds
                before_batches = dict(model.forward_seconds_by_batch)
                collection_start = time.perf_counter()
                rollout = collector.collect(model)
                collection_seconds = time.perf_counter() - collection_start
                transport = {key: value - before_transport[key] for key, value in bridge.timings.items()}
                forward_seconds = model.forward_seconds - before_forward
                policy_forward_seconds = (model.forward_seconds_by_batch.get(16, 0.0) - before_batches.get(16, 0.0))
                if iteration == 1:
                    panel = first_panel(collector)
                    write_json(out / "initial-panel-observations.json", panel)
                    report["initial_parity_before_gradients"] = parity(bridge, model, panel, out, "initial")
                learning_start = time.perf_counter()
                metrics = update(model, optimizer, rollout, epochs=config["epochs"], minibatch=config["minibatch"],
                                 clip=config["clip"], value_coefficient=config["value_coefficient"],
                                 entropy_coefficient=config["entropy_coefficient"])
                learning_seconds = time.perf_counter() - learning_start
                critic_metrics = same_rollout_value_metrics(model, rollout)
                row = {"iteration": iteration, "environment_turns": collector.total_turns,
                       "seat_samples": len(rollout["actions"]), "completed_games": len(audit.completed),
                       "terminal_seat_samples": int(rollout["dones"].sum()),
                       "collection_seconds": collection_seconds, "learning_seconds": learning_seconds,
                       "collection_forward_seconds": forward_seconds,
                       "policy_and_bootstrap_forward_seconds": policy_forward_seconds,
                       "logprob_recheck_forward_seconds": forward_seconds - policy_forward_seconds,
                       "transport": transport,
                       "collection_other_seconds": collection_seconds - sum(transport.values()) - forward_seconds,
                       "reward_mean": float(rollout["rewards"].mean()),
                       "reward_abs_mean": float(rollout["rewards"].abs().mean()),
                       "return_mean": float(rollout["returns"].mean()),
                       "value_mse_before": float((rollout["values"] - rollout["returns"]).square().mean()),
                       "logprob_recompute_max_error": rollout["logprob_recompute_max_error"],
                       "value_recompute_max_error": rollout["value_recompute_max_error"],
                       **critic_metrics, **metrics}
                report["iterations"].append(row)
                del rollout
                if iteration == 32:
                    checkpoint(out / "midpoint.pt", model, optimizer, config, iteration)
                    report["midpoint_weights_sha256"] = weight_hash(model)
                report["episodes"] = audit.summary()
                write_json(out / "metrics.json", report)
                if iteration % 8 == 0:
                    recent = report["iterations"][-8:]
                    emit({"iteration": iteration, "environment_turns": collector.total_turns,
                          "completed_games": len(audit.completed), "maps": len(audit.maps),
                          "last_value_mse_before": row["value_mse_before"], "last_value_mse_after": row["value_mse_after"],
                          "last_reward_abs_mean": row["reward_abs_mean"],
                          "collection_seconds_last8": sum(r["collection_seconds"] for r in recent),
                          "learning_seconds_last8": sum(r["learning_seconds"] for r in recent)})
            episodes = audit.summary()
            adam_steps = sorted({int(state["step"]) for state in optimizer.state.values()})
            if (collector.total_turns != 8192 or audit.total_steps != 8192 or len(audit.completed) == 0
                    or audit.resets != len(audit.completed) or audit.pending_terminal_ids or adam_steps != [1024]):
                raise AssertionError("Fixed training length, optimizer or terminal/reset accounting failed")
            late_rows = [audit.late_rows[key] for key in sorted(audit.late_rows)]
            write_json(out / "late-training-panel.json", late_rows)
            extended_panel = panel + [row["observation"] for row in late_rows]
            report["final_extended_parity"] = parity(bridge, model, extended_panel, out, "final-extended")
            with torch.random.fork_rng():
                initial_model = SpatialNet()
            initial_model.load_state_dict(initial_state)
            report["initial_extended_parity"] = parity(bridge, initial_model, extended_panel, out, "initial-extended")
            report["episodes"] = episodes
            report["legal_decisions"] = collector.legal_decisions
            report["total_decisions"] = collector.total_decisions
            report["seat_samples"] = collector.total_turns * 2
            report["adam_steps_per_parameter"] = adam_steps
            report["bridge_calls"] = bridge.calls
            report["bridge_calls_by_op"] = bridge.calls_by_op
            report["transport_total"] = bridge.timings
        if source_identities() != config["source_sha256"]:
            raise AssertionError("Source files changed during the fixed run")
        if frozen_v1_integrity() != config["frozen_v1_integrity"]:
            raise AssertionError("Frozen v1 evidence or artifact changed during the fixed run")
        for path, expected in config["runtime"]["compiled_engine_and_classpath_sha256"].items():
            if sha256(path) != expected:
                raise AssertionError(f"Compiled engine/classpath changed during the fixed run: {path}")
        checkpoint(out / "final.pt", model, optimizer, config, 64)
        export_java_class(model, ROOT / "src/main/java/btk/bot/neural/SpatialV2Weights.java", "SpatialV2Weights")
        (out / "final-weights.base64").write_text(model.encoded_weights(), encoding="ascii")
        final_vector = torch.cat([p.detach().flatten() for p in model.parameters()])
        report.update(status="complete", final_weights_sha256=weight_hash(model),
                      weight_l2_change=float((final_vector - initial_vector).norm()),
                      weight_max_abs_change=float((final_vector - initial_vector).abs().max()),
                      total_seconds=time.perf_counter() - started,
                      python_peak_working_set_bytes=peak_working_set_bytes())
        write_json(out / "metrics.json", report)
        write_json(evidence, report)
        emit({"complete": True, "environment_turns": 8192, "seat_samples": 16384,
              "completed_games": len(audit.completed), "maps": len(audit.maps),
              "final_weights_sha256": report["final_weights_sha256"], "total_seconds": report["total_seconds"]})
    except Exception as exc:
        report.update(status="failed", failure={"type": type(exc).__name__, "message": str(exc)},
                      total_seconds=time.perf_counter() - started)
        write_json(out / "metrics.json", report)
        write_json(out / "failure.json", report["failure"])
        write_json(evidence, report)
        emit({"failed": True, **report["failure"]})
        raise


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-command-json", required=True)
    parser.add_argument("--output", default="build/spatial-ppo-v2")
    parser.add_argument("--evidence", default="docs/evidence/spatial-ppo-v2-training.json")
    parser.add_argument("--excluded-maps", default="build/analysis/spatial-ppo-v2/prior-excluded.json")
    run(parser.parse_args())
