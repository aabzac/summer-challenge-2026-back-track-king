"""Fixed-pool PPO: warm-start v2 weights, fresh Adam/RNG, learner samples only.

python tools/spatial/run_v3_training.py --java-command-json build/spatial-v3-java.json
The run is fixed by docs/spatial-ppo-v3.md. There is no adaptive extension or resume.
"""
from __future__ import annotations

import argparse
import json
from pathlib import Path
import random
import time

from train_spatial import ROOT, SpatialNet, checkpoint, np, parity, torch, update
from run_v2_training import CONFIG as V2_CONFIG, peak_working_set_bytes, same_rollout_value_metrics, sha256, write_json
from v2_support import TimedBridge, TimedSpatialNet, weight_hash
from pool_collector import PoolCollector
from v3_support import POOL, WARM_START_SHA256, PoolAudit, export_v3_weights, frozen_history_integrity, v3_runtime_identities, v3_source_identities

CONFIG = {**V2_CONFIG, "schema": "spatial-ppo-v3", "initialization_seed": 20261009,
          "map_seed_start": 11000000, "iterations": 128, "environments": 8, "rollout_turns": 16,
          "checkpoint_iterations": [0, 64, 128], "expected_environment_turns": 16384,
          "expected_seat_samples": 16384, "expected_adam_steps": 1024,
          "opponents": list(POOL), "learner_seats": [slot % 2 for slot in range(8)],
          "initial_payload_sha256": WARM_START_SHA256, "warm_start": "v2 final weights only",
          "optimizer_initialization": "fresh Adam, no restored moments or step counter",
          "rng_initialization": "seed Python/NumPy/Torch with 20261009 after loading weights",
          "opponent_budget_ms": 60000, "opponent_loss_samples": 0,
          "late_training_panel": {"initial_environment_slots": [0, 1], "turns": [63, 95],
                                  "learner_seats_by_slot": {"0": 0, "1": 1}, "stages": [0, 1, 2, 3]}}


def fresh_learner(warm_start_path):
    saved = torch.load(warm_start_path, map_location="cpu", weights_only=False)
    if saved["iteration"] != 64 or saved["config"]["schema"] != "spatial-ppo-v2":
        raise AssertionError("Warm start must be the fixed v2 final checkpoint")
    model = TimedSpatialNet()
    model.load_state_dict(saved["model"], strict=True)
    if weight_hash(model) != WARM_START_SHA256 or not all(torch.isfinite(p).all() for p in model.parameters()):
        raise AssertionError("Warm-start weights differ from the finite frozen v2 final payload")
    random.seed(CONFIG["initialization_seed"])
    np.random.seed(CONFIG["initialization_seed"])
    torch.manual_seed(CONFIG["initialization_seed"])
    optimizer = torch.optim.Adam(model.parameters(), lr=CONFIG["learning_rate"])
    if optimizer.state:
        raise AssertionError("New experiment must start with an empty Adam state")
    return model, optimizer


def pool_rollout_metrics(rollout):
    return {spec: {"learner_samples": rollout["rewards"][:, [i for i, x in enumerate(POOL) if x == spec]].numel(),
                   "reward_mean": float(rollout["rewards"][:, [i for i, x in enumerate(POOL) if x == spec]].mean()),
                   "reward_abs_mean": float(rollout["rewards"][:, [i for i, x in enumerate(POOL) if x == spec]].abs().mean()),
                   "terminal_samples": int(rollout["dones"][:, [i for i, x in enumerate(POOL) if x == spec]].sum())}
            for spec in dict.fromkeys(POOL)}


def run(args):
    config = dict(CONFIG)
    command_path, exclusion_path = Path(args.java_command_json), Path(args.excluded_maps)
    warm_start_path = Path(args.warm_start)
    command = json.loads(command_path.read_text(encoding="utf-8-sig"))
    excluded = set(json.loads(exclusion_path.read_text(encoding="utf-8-sig"))["gamesPerMap"])
    out, evidence = Path(args.output), Path(args.evidence)
    if out.exists() or evidence.exists():
        raise FileExistsError("Refusing to reuse v3 output directory or evidence")
    config.update(java_command=command, java_command_file_sha256=sha256(command_path),
                  warm_start_checkpoint_sha256=sha256(warm_start_path),
                  frozen_history=frozen_history_integrity(), source_sha256=v3_source_identities(),
                  runtime=v3_runtime_identities(command), protocol_sha256=sha256(ROOT / "docs/spatial-ppo-v3.md"),
                  prior_map_exclusion_index={"path": str(exclusion_path), "sha256": sha256(exclusion_path), "map_hash_count": len(excluded)})
    if torch.__version__ != "2.8.0+cpu" or np.__version__ != "2.3.5":
        raise AssertionError("Runtime differs from the fixed protocol")
    torch.set_num_threads(2)
    torch.set_num_interop_threads(1)
    torch.use_deterministic_algorithms(True)
    model, optimizer = fresh_learner(warm_start_path)
    initial_state = {key: value.detach().clone() for key, value in model.state_dict().items()}
    initial_vector = torch.cat([p.detach().flatten().clone() for p in model.parameters()])
    out.mkdir(parents=True, exist_ok=False)
    evidence.parent.mkdir(parents=True, exist_ok=True)
    write_json(out / "config.json", config)
    report = {"status": "running", "config": config, "iterations": [], "initial_weights_sha256": weight_hash(model)}
    audit = PoolAudit(config["map_seed_start"], 8, config["terminal_bonus"], config["reward_scale"], excluded)
    started = time.perf_counter()

    def emit(value):
        line = json.dumps(value, allow_nan=False)
        print(line, flush=True)
        with (out / "run.log").open("a", encoding="utf-8") as stream:
            stream.write(line + "\n")

    try:
        emit({"started": True, "iterations": 128, "environment_turns": 16384,
              "learner_samples": 16384, "warm_start_weights_sha256": weight_hash(model)})
        with TimedBridge(command, audit) as bridge:
            collector = PoolCollector(bridge, config)
            config["actual_opponent_identities_before_gradients"] = dict(audit.opponent_identities)
            write_json(out / "config.json", config)
            checkpoint(out / "initial.pt", model, optimizer, config, 0)
            (out / "initial-weights.base64").write_text(model.encoded_weights(), encoding="ascii")
            panel = []
            for iteration in range(1, 129):
                before_transport = dict(bridge.timings)
                before_forward = model.forward_seconds
                before_batches = dict(model.forward_seconds_by_batch)
                before_opponent_ms = sum(value["elapsed_ms"] for value in audit.opponent_calls.values())
                collection_start = time.perf_counter()
                rollout = collector.collect(model)
                collection_seconds = time.perf_counter() - collection_start
                if rollout["actions"].shape != (128, 4):
                    raise AssertionError("PPO batch must contain exactly one learner sample per real transition")
                transport = {key: value - before_transport[key] for key, value in bridge.timings.items()}
                forward_seconds = model.forward_seconds - before_forward
                policy_seconds = model.forward_seconds_by_batch.get(8, 0.) - before_batches.get(8, 0.)
                if iteration == 1:
                    if len(collector.parity_observations) != 64:
                        raise AssertionError("Unexpected fixed first-rollout parity panel size")
                    panel = list(collector.parity_observations)
                    collector.parity_observations.clear()
                    write_json(out / "initial-panel-observations.json", panel)
                    report["initial_parity_before_gradients"] = parity(bridge, model, panel, out, "initial")
                learning_start = time.perf_counter()
                metrics = update(model, optimizer, rollout, epochs=2, minibatch=32, clip=config["clip"],
                                 value_coefficient=config["value_coefficient"], entropy_coefficient=config["entropy_coefficient"])
                learning_seconds = time.perf_counter() - learning_start
                critic_metrics = same_rollout_value_metrics(model, rollout)
                row = {"iteration": iteration, "environment_turns": collector.total_turns,
                       "learner_samples": 128, "opponent_samples": 0, "completed_games": len(audit.completed),
                       "terminal_learner_samples": int(rollout["dones"].sum()),
                       "collection_seconds": collection_seconds, "learning_seconds": learning_seconds,
                       "collection_forward_seconds": forward_seconds,
                       "policy_and_bootstrap_forward_seconds": policy_seconds,
                       "logprob_recheck_forward_seconds": forward_seconds - policy_seconds,
                       "opponent_seconds": (sum(value["elapsed_ms"] for value in audit.opponent_calls.values()) - before_opponent_ms) / 1000.,
                       "transport": transport,
                       "collection_other_seconds": collection_seconds - sum(transport.values()) - forward_seconds,
                       "reward_mean": float(rollout["rewards"].mean()), "reward_abs_mean": float(rollout["rewards"].abs().mean()),
                       "return_mean": float(rollout["returns"].mean()),
                       "value_mse_before": float((rollout["values"] - rollout["returns"]).square().mean()),
                       "logprob_recompute_max_error": rollout["logprob_recompute_max_error"],
                       "value_recompute_max_error": rollout["value_recompute_max_error"],
                       "per_pool": pool_rollout_metrics(rollout), **critic_metrics, **metrics}
                report["iterations"].append(row)
                del rollout
                if iteration == 64:
                    checkpoint(out / "midpoint.pt", model, optimizer, config, iteration)
                    report["midpoint_weights_sha256"] = weight_hash(model)
                report["episodes"] = audit.summary()
                write_json(out / "metrics.json", report)
                if iteration % 8 == 0:
                    recent = report["iterations"][-8:]
                    emit({"iteration": iteration, "environment_turns": collector.total_turns,
                          "completed_games": len(audit.completed), "maps": len(audit.maps),
                          "last_value_mse_before": row["value_mse_before"], "last_value_mse_after": row["value_mse_after"],
                          "collection_seconds_last8": sum(item["collection_seconds"] for item in recent),
                          "learning_seconds_last8": sum(item["learning_seconds"] for item in recent)})
            adam_steps = sorted({int(state["step"]) for state in optimizer.state.values()})
            opponent_calls = sum(value["count"] for value in audit.opponent_calls.values())
            if (collector.total_turns != 16384 or audit.total_steps != 16384 or opponent_calls != 16384
                    or len(audit.completed) == 0 or audit.resets != len(audit.completed)
                    or audit.pending_terminal_ids or adam_steps != [1024]):
                raise AssertionError("Fixed sample/optimizer/opponent/terminal accounting failed")
            report["java_opponent_stats"] = bridge.request(op="stats")["opponentStats"]
            for stats in report["java_opponent_stats"]:
                if (stats["calls"] != audit.opponent_calls[stats["opponentSpec"]]["count"]
                        or stats["failed"] != 0 or stats["completed"] != stats["calls"]):
                    raise AssertionError("Java opponent counters disagree with collection")
            late_rows = [audit.late_rows[key] for key in sorted(audit.late_rows)]
            write_json(out / "late-training-panel.json", late_rows)
            extended_panel = panel + [row["observation"] for row in late_rows]
            report["final_extended_parity"] = parity(bridge, model, extended_panel, out, "final-extended")
            with torch.random.fork_rng():
                initial_model = SpatialNet()
            initial_model.load_state_dict(initial_state)
            report["initial_extended_parity"] = parity(bridge, initial_model, extended_panel, out, "initial-extended")
            report.update(episodes=audit.summary(), learner_samples=16384, opponent_loss_samples=0,
                          legal_decisions=collector.legal_decisions, total_decisions=collector.total_decisions,
                          adam_steps_per_parameter=adam_steps, bridge_calls=bridge.calls,
                          bridge_calls_by_op=bridge.calls_by_op, transport_total=bridge.timings)
        if v3_source_identities() != config["source_sha256"] or frozen_history_integrity() != config["frozen_history"]:
            raise AssertionError("Frozen source or historical artifacts changed during training")
        for path, expected in config["runtime"]["compiled_engine_and_classpath_sha256"].items():
            if sha256(path) != expected:
                raise AssertionError(f"Compiled training runtime changed: {path}")
        checkpoint(out / "final.pt", model, optimizer, config, 128)
        export_v3_weights(model, ROOT / "src/main/java/btk/bot/neural/SpatialV3Weights.java")
        (out / "final-weights.base64").write_text(model.encoded_weights(), encoding="ascii")
        final_vector = torch.cat([p.detach().flatten() for p in model.parameters()])
        report.update(status="complete", final_weights_sha256=weight_hash(model),
                      weight_l2_change=float((final_vector - initial_vector).norm()),
                      weight_max_abs_change=float((final_vector - initial_vector).abs().max()),
                      total_seconds=time.perf_counter() - started, python_peak_working_set_bytes=peak_working_set_bytes())
        write_json(out / "metrics.json", report)
        write_json(evidence, report)
        emit({"complete": True, "environment_turns": 16384, "learner_samples": 16384,
              "completed_games": len(audit.completed), "maps": len(audit.maps),
              "final_weights_sha256": report["final_weights_sha256"], "total_seconds": report["total_seconds"]})
    except Exception as exc:
        report.update(status="failed", failure={"type": type(exc).__name__, "message": str(exc)},
                      opponent_failures=audit.opponent_failures, total_seconds=time.perf_counter() - started)
        write_json(out / "metrics.json", report)
        write_json(out / "failure.json", report["failure"])
        write_json(evidence, report)
        emit({"failed": True, **report["failure"]})
        raise


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-command-json", required=True)
    parser.add_argument("--output", default="build/spatial-ppo-v3")
    parser.add_argument("--evidence", default="docs/evidence/spatial-ppo-v3-training.json")
    parser.add_argument("--warm-start", default="build/spatial-ppo-v2/final.pt")
    parser.add_argument("--excluded-maps", default="build/analysis/spatial-ppo-v3/training-excluded.json")
    run(parser.parse_args())
