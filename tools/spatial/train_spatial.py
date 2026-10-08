"""Bounded on-policy spatial PPO through the exact Java simultaneous engine.

Example: python tools/spatial/train_spatial.py --java-command-json build/spatial-java.json
The command file is a JSON array, e.g. ["java", "-cp", "...", "btk.tools.spatial.SpatialBridge"].
PyTorch may be installed locally under build/spatial-deps; no global install is needed.
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import json
from pathlib import Path
import random
import sys
import time

ROOT = Path(__file__).resolve().parents[2]
LOCAL_DEPS = ROOT / "build" / "spatial-deps"
if LOCAL_DEPS.is_dir():
    sys.path.insert(0, str(LOCAL_DEPS))

import numpy as np
import torch
from bridge import Bridge
from model import SpatialNet, evaluate_plan, masked_distribution, tensor_observations
from ppo import gae, update


DEFAULT_CONFIG = {
    "schema": "spatial-ppo-v1", "initialization_seed": 20261008,
    "map_seed_start": 9700000, "iterations": 4, "environments": 4,
    "rollout_turns": 16, "epochs": 2, "minibatch": 32,
    "learning_rate": 3e-4, "gamma": 0.99, "gae_lambda": 0.95,
    "clip": 0.2, "entropy_coefficient": 0.01, "value_coefficient": 0.5,
    "reward_scale": 100.0, "terminal_bonus": 30.0, "gradient_clip": 0.5,
    "torch_threads": 2, "opponents": "current/current-both-seats",
}


def checkpoint(path, model, optimizer, config, iteration):
    torch.save({"model": model.state_dict(), "optimizer": optimizer.state_dict(),
                "config": config, "iteration": iteration,
                "torch_rng": torch.get_rng_state(), "numpy_rng": np.random.get_state(),
                "python_rng": random.getstate()}, path)


def flatten_states(states):
    return [obs for state in states for obs in state["obs"]]


def parity(bridge, model, observations, output_dir, label):
    """Compare all raw outputs and legal greedy actions for both policy heads."""
    with torch.no_grad():
        py_build, py_ink, py_value = [x.numpy() for x in model(tensor_observations(observations))]
    result = bridge.request(op="forward", weights=model.encoded_weights(), observations=observations)
    outputs = result["outputs"]
    java_build = np.asarray([o["buildLogits"] for o in outputs], np.float32)
    java_ink = np.asarray([o["disruptLogits"] for o in outputs], np.float32)
    java_value = np.asarray([o["value"] for o in outputs], np.float32)
    mask_build = np.asarray([o["buildMask"] for o in observations], bool)
    mask_ink = np.asarray([o["disruptMask"] for o in observations], bool)
    errors = {"build": float(np.max(np.abs(py_build - java_build))),
              "disrupt": float(np.max(np.abs(py_ink - java_ink))),
              "value": float(np.max(np.abs(py_value - java_value)))}
    greedy_equal = (np.array_equal(np.where(mask_build, py_build, -np.inf).argmax(-1),
                                   np.where(mask_build, java_build, -np.inf).argmax(-1)) and
                    np.array_equal(np.where(mask_ink, py_ink, -np.inf).argmax(-1),
                                   np.where(mask_ink, java_ink, -np.inf).argmax(-1)))
    report = {"observations": len(observations), "max_abs_error": errors,
              "tolerance": 1e-4, "greedy_equal": bool(greedy_equal)}
    (output_dir / f"parity-{label}.json").write_text(json.dumps(report, indent=2) + "\n")
    if len(observations) < 20 or max(errors.values()) > 1e-4 or not greedy_equal:
        raise AssertionError(f"Python/Java parity failed: {report}")
    return report


class Collector:
    def __init__(self, bridge, config):
        self.bridge, self.config = bridge, config
        self.next_seed = config["map_seed_start"]
        seeds = self.new_seeds(config["environments"])
        self.states = bridge.request(op="reset", seeds=seeds)["envs"]
        self.maps = [self.map_metadata(state, seed) for state, seed in zip(self.states, seeds)]
        self.parity_observations = []
        self.finished = []
        self.total_turns = 0
        self.total_decisions = 0
        self.legal_decisions = 0

    def new_seeds(self, n):
        seeds = list(range(self.next_seed, self.next_seed + n))
        self.next_seed += n
        return seeds

    @staticmethod
    def map_metadata(state, seed):
        return {"seed": seed, **{key: state[key] for key in ("id", "width", "height", "mapSha256") if key in state}}

    def collect(self, model):
        nenv, nseat = len(self.states), 2 * len(self.states)
        tensor_steps, action_steps, logprob_steps, value_steps = [], [], [], []
        reward_steps, done_steps = [], []
        for _ in range(self.config["rollout_turns"]):
            env_ids = [state["id"] for state in self.states for _ in (0, 1)]
            seats = [seat for _ in self.states for seat in (0, 1)]
            prefixes = [[] for _ in range(nseat)]
            stopped = [False] * nseat
            observations = flatten_states(self.states)
            stage_tensors, actions, logprobs = [], [], []
            for stage in range(4):
                if stage:
                    observations = self.bridge.request(op="observe", envIds=env_ids, seats=seats,
                                                       prefixes=prefixes, stages=[stage] * nseat,
                                                       stopped=stopped)["obs"]
                if self.total_turns in (0, nenv * (self.config["rollout_turns"] - 1)):
                    self.parity_observations.extend(observations)
                obs_tensor = tensor_observations(observations)
                with torch.no_grad():
                    build, ink, value = model(obs_tensor)
                    logits = ink if stage == 3 else build
                    mask = obs_tensor["disruptMask" if stage == 3 else "buildMask"]
                    dist = masked_distribution(logits, mask)
                    action = dist.sample()
                    lp = dist.log_prob(action)
                selected_legal = mask.gather(1, action[:, None]).all()
                if not selected_legal:
                    raise AssertionError("Sampled illegal action")
                self.total_decisions += nseat
                self.legal_decisions += nseat
                if stage == 0:
                    value_steps.append(value)
                if stage < 3:
                    for i, cell in enumerate(action.tolist()):
                        if cell == 600:
                            stopped[i] = True
                        else:
                            prefixes[i].append(cell)
                stage_tensors.append(obs_tensor)
                actions.append(action)
                logprobs.append(lp)
            # Each complete plan's four conditionals remain adjacent for PPO.
            tensor_steps.append({key: torch.stack([o[key] for o in stage_tensors], dim=1)
                                 .reshape(nseat * 4, *stage_tensors[0][key].shape[1:])
                                 for key in stage_tensors[0]})
            action_steps.append(torch.stack(actions, dim=1))
            logprob_steps.append(torch.stack(logprobs, dim=1).sum(1))
            disruptions = actions[3].tolist()
            plans = [[{"cells": prefixes[2 * e + seat], "disrupt": disruptions[2 * e + seat]}
                      for seat in (0, 1)] for e in range(nenv)]
            new_states = self.bridge.request(op="step", envIds=[s["id"] for s in self.states],
                                             plans=plans)["envs"]
            reward_steps.append(torch.tensor([
                (s["rewardGap"][seat] + self.config["terminal_bonus"] * s["terminalSign"][seat])
                / self.config["reward_scale"] for s in new_states for seat in (0, 1)], dtype=torch.float32))
            done_steps.append(torch.tensor([s["done"] for s in new_states for _ in (0, 1)]))
            ended = [s for s in new_states if s["done"]]
            if ended:
                self.finished.extend({"id": s["id"], "turn": s["turn"], "score": s["score"]}
                                     for s in ended)
                seeds = self.new_seeds(len(ended))
                replacements = self.bridge.request(op="reset", envIds=[s["id"] for s in ended],
                                                   seeds=seeds)["envs"]
                self.maps.extend(self.map_metadata(state, seed) for state, seed in zip(replacements, seeds))
                by_id = {s["id"]: s for s in replacements}
                new_states = [by_id.get(s["id"], s) for s in new_states]
            self.states = new_states
            self.total_turns += nenv
        with torch.no_grad():
            _, _, bootstrap = model(tensor_observations(flatten_states(self.states)))
        rewards, values, dones = map(torch.stack, (reward_steps, value_steps, done_steps))
        advantages, returns = gae(rewards, values, dones, bootstrap,
                                 self.config["gamma"], self.config["gae_lambda"])
        rollout = {"observations": {key: torch.cat([o[key] for o in tensor_steps]) for key in tensor_steps[0]},
                   "actions": torch.cat(action_steps), "logprobs": torch.stack(logprob_steps),
                   "values": values, "rewards": rewards, "dones": dones,
                   "advantages": advantages, "returns": returns}
        # Recompute from retained conditional observations/masks before updating.
        differences, value_differences = [], []
        with torch.no_grad():
            for start in range(0, len(rollout["actions"]), 32):
                obs = {key: data[start * 4:(start + 32) * 4] for key, data in rollout["observations"].items()}
                lp, _, v = evaluate_plan(model, obs, rollout["actions"][start:start + 32])
                differences.append((lp - rollout["logprobs"].flatten()[start:start + 32]).abs().max())
                value_differences.append((v - values.flatten()[start:start + 32]).abs().max())
        rollout["logprob_recompute_max_error"] = float(torch.stack(differences).max())
        rollout["value_recompute_max_error"] = float(torch.stack(value_differences).max())
        if rollout["logprob_recompute_max_error"] > 1e-4 or rollout["value_recompute_max_error"] > 1e-4:
            raise AssertionError("Stored full-plan log probability or stage-zero value drifted")
        return rollout


def run(args):
    config = dict(DEFAULT_CONFIG)
    command = json.loads(Path(args.java_command_json).read_text(encoding="utf-8-sig"))
    out = Path(args.output)
    out.mkdir(parents=True, exist_ok=True)
    if (out / "initial.pt").exists():
        raise FileExistsError(f"Refusing to overwrite a prior fixed run in {out}")
    random.seed(config["initialization_seed"])
    np.random.seed(config["initialization_seed"])
    torch.manual_seed(config["initialization_seed"])
    torch.set_num_threads(config["torch_threads"])
    torch.set_num_interop_threads(1)
    torch.use_deterministic_algorithms(True)
    config["torch_version"] = torch.__version__
    config["numpy_version"] = np.__version__
    config["java_command"] = command
    sources = list(Path(__file__).parent.glob("*.py"))
    sources += list((ROOT / "src/main/java/btk/core").glob("*.java"))
    sources += list((ROOT / "src/main/java/btk/tools/spatial").glob("*.java"))
    sources += [p for p in (ROOT / "src/main/java/btk/bot/neural").glob("Spatial*.java") if p.name != "SpatialWeights.java"]
    config["source_sha256"] = {str(p.relative_to(ROOT)).replace("\\", "/"): hashlib.sha256(p.read_bytes()).hexdigest()
                               for p in sorted(sources)}
    (out / "config.json").write_text(json.dumps(config, indent=2) + "\n")
    model = SpatialNet()
    optimizer = torch.optim.Adam(model.parameters(), lr=config["learning_rate"])
    initial = torch.cat([p.detach().flatten().clone() for p in model.parameters()])
    checkpoint(out / "initial.pt", model, optimizer, config, 0)
    (out / "initial-weights.base64").write_text(model.encoded_weights())
    report = {"config": config, "iterations": [],
              "initial_weights_sha256": hashlib.sha256(base64.b64decode(model.encoded_weights())).hexdigest()}
    started = time.perf_counter()
    with Bridge(command) as bridge:
        collector = Collector(bridge, config)
        for iteration in range(config["iterations"]):
            collection_start = time.perf_counter()
            rollout = collector.collect(model)
            collection_seconds = time.perf_counter() - collection_start
            if iteration == 0:
                (out / "parity-observations.json").write_text(json.dumps(collector.parity_observations))
                report["initial_parity"] = parity(bridge, model, collector.parity_observations, out, "initial")
            learning_start = time.perf_counter()
            metrics = update(model, optimizer, rollout, epochs=config["epochs"], minibatch=config["minibatch"],
                             clip=config["clip"], value_coefficient=config["value_coefficient"],
                             entropy_coefficient=config["entropy_coefficient"])
            learning_seconds = time.perf_counter() - learning_start
            with torch.no_grad():
                stage_zero = {key: data[::4] for key, data in rollout["observations"].items()}
                after_value = torch.cat([model({key: data[start:start + 32] for key, data in stage_zero.items()})[2]
                                         for start in range(0, len(rollout["actions"]), 32)])
                value_mse_after = float((after_value - rollout["returns"].flatten()).square().mean())
            row = {"iteration": iteration + 1, "environment_turns": collector.total_turns,
                   "seat_samples": len(rollout["actions"]), "collection_seconds": collection_seconds,
                   "learning_seconds": learning_seconds, "reward_mean": float(rollout["rewards"].mean()),
                   "reward_abs_mean": float(rollout["rewards"].abs().mean()),
                   "return_mean": float(rollout["returns"].mean()),
                   "return_variance": float(rollout["returns"].var(unbiased=False)),
                   "value_mse_before": float((rollout["values"] - rollout["returns"]).square().mean()),
                   "value_mse_after": value_mse_after,
                   "logprob_recompute_max_error": rollout["logprob_recompute_max_error"],
                   "value_recompute_max_error": rollout["value_recompute_max_error"], **metrics}
            report["iterations"].append(row)
            print(json.dumps(row), flush=True)
            (out / "metrics.json").write_text(json.dumps(report, indent=2) + "\n")
        report["final_parity"] = parity(bridge, model, collector.parity_observations, out, "final")
        report["environment_turns"] = collector.total_turns
        report["seat_samples"] = collector.total_turns * 2
        report["legal_decisions"] = collector.legal_decisions
        report["total_decisions"] = collector.total_decisions
        report["finished_games"] = collector.finished
        report["last_scores"] = [s["score"] for s in collector.states]
        report["next_map_seed"] = collector.next_seed
        report["maps"] = collector.maps
        report["bridge_calls"] = bridge.calls
    final = torch.cat([p.detach().flatten() for p in model.parameters()])
    report["weight_l2_change"] = float((final - initial).norm())
    report["weight_max_abs_change"] = float((final - initial).abs().max())
    report["total_seconds"] = time.perf_counter() - started
    report["final_weights_sha256"] = hashlib.sha256(base64.b64decode(model.encoded_weights())).hexdigest()
    checkpoint(out / "final.pt", model, optimizer, config, config["iterations"])
    model.export_java(args.export_java)
    (out / "final-weights.base64").write_text(model.encoded_weights())
    (out / "metrics.json").write_text(json.dumps(report, indent=2) + "\n")
    if args.evidence:
        evidence = Path(args.evidence)
        evidence.parent.mkdir(parents=True, exist_ok=True)
        evidence.write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps({"complete": True, "output": str(out), "weight_l2_change": report["weight_l2_change"],
                      "total_seconds": report["total_seconds"]}), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-command-json", required=True)
    parser.add_argument("--output", default="build/spatial-ppo-v1")
    parser.add_argument("--export-java", default="src/main/java/btk/bot/neural/SpatialWeights.java")
    parser.add_argument("--evidence", default="docs/evidence/spatial-ppo-v1-training.json")
    run(parser.parse_args())
