"""Independent v2: joint Q = learned state value + additive action advantages.

Keeps v1's environment/reward/data/architecture and greedy action maximizer.
The new state baseline is added once, including for full WAIT. This module never
modifies v1 sources, checkpoints or generated weights. See READMEv2.md.
"""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import hashlib
import json
from pathlib import Path
import struct
import time

import train_q as v1
import numpy as np

INPUTS, HIDDEN = v1.INPUTS, v1.HIDDEN
WEIGHT_MAGIC = b"BTKW0002"
PARAM_SHAPES = dict(v1.PARAM_SHAPES, WV=(HIDDEN,), BV=(1,))


class Model(v1.Model):
    def __init__(self, rng):
        super().__init__(rng)
        self.p["WV"] = (rng.standard_normal(HIDDEN) * np.sqrt(2.0 / HIDDEN) * .1).astype(np.float32)
        self.p["BV"] = np.zeros(1, dtype=np.float32)
        for name in ("WV", "BV"):
            self.m[name] = np.zeros_like(self.p[name])
            self.v[name] = np.zeros_like(self.p[name])
        self.replay_keys = np.empty(0, dtype=np.uint64)
        self.replay_priorities = np.empty(0, dtype=np.float64)

    def forward(self, batch, cache=False):
        build, ink, base_cache = super().forward(batch, cache=True)
        h0, local, global_h, h1, region_h = base_cache
        mean_h1 = h1.sum(axis=1) / batch.sizes[:, None]
        value = mean_h1 @ self.p["WV"] + self.p["BV"][0]
        if cache:
            return build, ink, value, (h0, local, global_h, h1, region_h, mean_h1)
        return build, ink, value

    def loss_gradients(self, batch, targets, importance, huber=1.0):
        build, ink, value, cache = self.forward(batch, cache=True)
        h0, local, global_h, h1, region_h, mean_h1 = cache
        actual = value + v1.action_values(build, ink, batch.builds, batch.inks)
        errors = actual - targets
        absolute = np.abs(errors)
        losses = np.where(absolute <= huber, .5 * errors ** 2, huber * (absolute - .5 * huber))
        derivative = np.clip(errors, -huber, huber) * importance / len(errors)
        grad = {name: np.zeros_like(p) for name, p in self.p.items()}
        # V(s) appears once for EVERY action. Full WAIT therefore updates the
        # value head and representation, even though both action sums are empty.
        grad["WV"] = mean_h1.T @ derivative
        grad["BV"][0] = derivative.sum()
        dh1 = (derivative[:, None, None] * self.p["WV"][None, None, :]
               / batch.sizes[:, None, None]) * batch.mask[..., None]
        for i, (cells, ink_region) in enumerate(zip(batch.builds, batch.inks)):
            d = derivative[i]
            if cells:
                selected = list(cells)
                grad["WB"] += h1[i, selected].sum(axis=0) * d
                grad["BB"][0] += len(cells) * d
                dh1[i, selected] += self.p["WB"] * d
            if ink_region >= 0:
                grad["WI"] += region_h[i, ink_region] * d
                grad["BI"][0] += d
                mask = batch.mask[i] & (batch.regions[i] == ink_region)
                dh1[i, mask] += self.p["WI"] * (d / batch.region_sizes[i, ink_region])
        dz1 = dh1 * (h1 > 0)
        grad["WS"] = np.einsum("bni,bnj->ij", h0, dz1, optimize=True)
        grad["WN"] = np.einsum("bni,bnj->ij", local, dz1, optimize=True)
        grad["WG"] = global_h.T @ dz1.sum(axis=1)
        grad["B1"] = dz1.sum(axis=(0, 1))
        dh0 = dz1 @ self.p["WS"].T
        neighbor_gradient = (dz1 @ self.p["WN"].T) / batch.degrees[..., None]
        for direction in range(4):
            bi, cells = np.nonzero(batch.neighbor_mask[:, :, direction])
            np.add.at(dh0, (bi, batch.neighbors[bi, cells, direction]), neighbor_gradient[bi, cells])
        dh0 += ((dz1.sum(axis=1) @ self.p["WG"].T) / batch.sizes[:, None])[:, None]
        dz0 = dh0 * (h0 > 0)
        grad["W0"] = np.einsum("bni,bnj->ij", batch.x, dz0, optimize=True)
        grad["B0"] = dz0.sum(axis=(0, 1))
        return float(np.mean(losses * importance)), errors, grad

    def load(self, path):
        path = Path(path)
        if path.suffix != ".npz":
            with path.open("rb") as stream:
                tensors = read_weight_tensors(stream)
        else:
            tensors = None
        checkpoint = path if path.suffix == ".npz" else path.with_suffix(".npz")
        if checkpoint.exists():
            with np.load(checkpoint, allow_pickle=False) as saved:
                if "schemaVersion" not in saved or int(saved["schemaVersion"]) != 2:
                    raise ValueError("Expected v2 checkpoint, not a v1 optimizer")
                for name, shape in PARAM_SHAPES.items():
                    if saved[name].shape != shape or not np.isfinite(saved[name]).all():
                        raise ValueError(f"Invalid checkpoint parameter {name}")
                    self.p[name][:] = saved[name]
                    if tensors is not None and not np.array_equal(tensors[name], self.p[name]):
                        raise ValueError(f"Checkpoint and self-play binary disagree in {name}")
                    self.m[name][:] = saved[f"adam_m_{name}"]
                    self.v[name][:] = saved[f"adam_v_{name}"]
                self.updates = int(saved["updates"])
                if "replay_keys" in saved:
                    self.replay_keys = saved["replay_keys"].astype(np.uint64)
                    self.replay_priorities = saved["replay_priorities"].astype(np.float64)
                    if self.replay_keys.shape != self.replay_priorities.shape:
                        raise ValueError("Replay priority checkpoint shapes disagree")
                    if (self.replay_priorities <= 0).any() or not np.isfinite(self.replay_priorities).all():
                        raise ValueError("Replay checkpoint priorities must be positive/finite")
        elif tensors is not None:
            for name in self.p:
                self.p[name][:] = tensors[name]
        else:
            raise ValueError(f"Missing checkpoint: {checkpoint}")

    def warm_start_v1(self, path):
        """Copy v1's ten inference tensors; deliberately reset value/optimizer."""
        source = v1.Model(np.random.default_rng(0))
        source.load(path)
        for name in v1.PARAM_SHAPES:
            self.p[name][:] = source.p[name]
        self.p["WV"].fill(0)
        self.p["BV"].fill(0)
        for name in self.p:
            self.m[name].fill(0)
            self.v[name].fill(0)
        self.updates = 0
        self.replay_keys = np.empty(0, dtype=np.uint64)
        self.replay_priorities = np.empty(0, dtype=np.float64)


def read_weight_tensors(stream):
    if v1.read_exact(stream, 8) != WEIGHT_MAGIC:
        raise ValueError("Expected BTKW0002; use --warm-start-v1 for old weights")
    if v1.read_int(stream) != INPUTS or v1.read_int(stream) != HIDDEN:
        raise ValueError("Wrong v2 model dimensions")
    tensors = {}
    for name, shape in PARAM_SHAPES.items():
        tensors[name] = v1.read_array(stream, int(np.prod(shape)), ">f4").astype(np.float32).reshape(shape)
        if not np.isfinite(tensors[name]).all():
            raise ValueError(f"Nonfinite model tensor {name}")
    if stream.read(1):
        raise ValueError("Trailing v2 weight data")
    return tensors


def load_target(model, path, rng):
    target = Model(rng)
    target.copy_from(model)
    if path:
        checkpoint = Path(path) if Path(path).suffix == ".npz" else Path(path).with_suffix(".npz")
        if checkpoint.exists():
            with np.load(checkpoint, allow_pickle=False) as saved:
                for name, shape in PARAM_SHAPES.items():
                    value = saved[f"target_{name}"]
                    if value.shape != shape or not np.isfinite(value).all():
                        raise ValueError(f"Invalid target checkpoint tensor {name}")
                    target.p[name][:] = value
    return target


def write_weights(model, target, binary_path, java_path=None):
    binary_path = Path(binary_path)
    binary_path.parent.mkdir(parents=True, exist_ok=True)
    blob = WEIGHT_MAGIC + struct.pack(">ii", INPUTS, HIDDEN)
    for name in PARAM_SHAPES:
        blob += model.p[name].astype(">f4").tobytes()
    binary_path.write_bytes(blob)
    checkpoint = dict(model.p)
    checkpoint.update(schemaVersion=np.asarray(2), updates=np.asarray(model.updates, dtype=np.int64),
                      replay_keys=model.replay_keys, replay_priorities=model.replay_priorities)
    for name in model.p:
        checkpoint[f"adam_m_{name}"] = model.m[name]
        checkpoint[f"adam_v_{name}"] = model.v[name]
        checkpoint[f"target_{name}"] = target.p[name]
    np.savez_compressed(binary_path.with_suffix(".npz"), **checkpoint)
    if java_path:
        lines = ["package btk.bot.current;", "", "import java.util.Base64;", "",
                 "/** Generated by tools/indrill/train_q_v2.py; do not edit weights by hand. */",
                 "public final class IndrillV2Weights {", "    private IndrillV2Weights() {}"]
        for name in PARAM_SHAPES:
            encoded = base64.b64encode(model.p[name].astype(">f4").tobytes()).decode("ascii")
            lines.append(f'    public static final float[] {name} = decode("{encoded}");')
        lines += ["", "    private static float[] decode(String text) {",
                  "        byte[] bytes = Base64.getDecoder().decode(text);",
                  "        float[] out = new float[bytes.length / 4];",
                  "        for (int i = 0, j = 0; i < out.length; i++, j += 4) {",
                  "            int bits = ((bytes[j] & 255) << 24) | ((bytes[j + 1] & 255) << 16)",
                  "                    | ((bytes[j + 2] & 255) << 8) | (bytes[j + 3] & 255);",
                  "            out[i] = Float.intBitsToFloat(bits);", "        }", "        return out;",
                  "    }", "}", ""]
        java_path = Path(java_path)
        java_path.parent.mkdir(parents=True, exist_ok=True)
        java_path.write_text("\n".join(lines), encoding="utf-8")
    return hashlib.sha256(blob).hexdigest()


def replay_keys(transitions):
    keys = np.empty(len(transitions), dtype=np.uint64)
    for i, t in enumerate(transitions):
        identity = f"{t.episode.source_digest}:{t.episode.identity}:{t.step}:{t.player}"
        keys[i] = int.from_bytes(hashlib.sha256(identity.encode("utf-8")).digest()[:8], "big")
    if len(np.unique(keys)) != len(keys):
        raise ValueError("Duplicate replay item IDs or replay-key hash collision")
    return keys


def restore_priorities(model, keys):
    cached = dict(zip(model.replay_keys.tolist(), model.replay_priorities.tolist()))
    new_priority = max(cached.values(), default=1.0)
    restored = sum(int(key) in cached for key in keys)
    priorities = np.asarray([cached.get(int(key), new_priority) for key in keys], dtype=np.float64)
    return priorities, restored, new_priority


def train(model, target, transitions, keys, args, rng):
    priorities, restored, new_priority = restore_priorities(model, keys)
    history, started = [], time.monotonic()
    print(json.dumps({"replayItems": len(keys), "restoredPriorities": restored,
                      "newItemPriority": new_priority}), flush=True)
    for step in range(args.steps):
        probabilities = priorities ** args.alpha
        probabilities /= probabilities.sum()
        indices = rng.choice(len(transitions), size=args.batch, replace=True, p=probabilities)
        sampled = [transitions[i] for i in indices]
        beta = args.beta + (1 - args.beta) * step / max(args.steps - 1, 1)
        importance = (len(transitions) * probabilities[indices]) ** (-beta)
        importance = (importance / importance.max()).astype(np.float32)
        current, following = v1.Batch(sampled), v1.Batch(sampled, next_states=True)
        online_build, online_ink, _ = model.forward(following)
        chosen_builds, chosen_inks = v1.choose_actions(online_build, online_ink, following)
        target_build, target_ink, target_value = target.forward(following)
        continuation = target_value + v1.action_values(target_build, target_ink, chosen_builds, chosen_inks)
        targets = np.asarray([t.reward for t in sampled], dtype=np.float32)
        targets += np.asarray([t.discount for t in sampled], dtype=np.float32) * continuation
        loss, errors, gradients = model.loss_gradients(current, targets, importance, args.huber)
        norm = model.adam(gradients, args.lr, args.clip)
        for index in np.unique(indices):
            priorities[index] = float(np.abs(errors[indices == index]).max()) + 1e-4
        if model.updates % args.target == 0:
            target.copy_from(model)
        if step == 0 or (step + 1) % 100 == 0 or step + 1 == args.steps:
            b, i, value = model.forward(current)
            greedy_builds, greedy_inks = v1.choose_actions(b, i, current)
            greedy_value = value + v1.action_values(b, i, greedy_builds, greedy_inks)
            row = {"step": step + 1, "updates": model.updates, "loss": loss,
                   "meanAbsTd": float(np.abs(errors).mean()), "gradNorm": norm, "beta": float(beta),
                   "meanStateValue": float(value.mean()), "meanGreedyJointQ": float(greedy_value.mean()),
                   "fullWaitCount": sum(not cells and ink < 0 for cells, ink in zip(current.builds, current.inks)),
                   "seconds": round(time.monotonic() - started, 3)}
            history.append(row)
            print(json.dumps(row), flush=True)
        if not np.isfinite(loss) or any(not np.isfinite(p).all() for p in model.p.values()):
            raise RuntimeError("Training diverged; refusing to export")
    model.replay_keys, model.replay_priorities = keys, priorities
    return history, restored, new_priority


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", nargs="+", default=[])
    parser.add_argument("--weights-in")
    parser.add_argument("--warm-start-v1")
    parser.add_argument("--weights-out", default="build/analysis/indrill-q-v2/weights-0.bin")
    parser.add_argument("--java-out", default="src/main/java/btk/bot/current/IndrillV2Weights.java")
    parser.add_argument("--evidence", default="build/analysis/indrill-q-v2/training.json")
    parser.add_argument("--init-only", action="store_true")
    parser.add_argument("--seed", type=int, default=20261004)
    parser.add_argument("--steps", type=int, default=2000)
    parser.add_argument("--batch", type=int, default=32)
    parser.add_argument("--target", type=int, default=100)
    parser.add_argument("--gamma", type=float, default=.99)
    parser.add_argument("--nstep", type=int, default=3)
    parser.add_argument("--alpha", type=float, default=.6)
    parser.add_argument("--beta", type=float, default=.4)
    parser.add_argument("--lr", type=float, default=3e-4)
    parser.add_argument("--huber", type=float, default=1.0)
    parser.add_argument("--clip", type=float, default=10.0)
    parser.add_argument("--float32-replay", action="store_true")
    args = parser.parse_args(argv)
    if args.weights_in and args.warm_start_v1:
        parser.error("Choose v2 resume or v1 warm start, not both")
    if min(args.steps, args.batch, args.target, args.nstep) < 1:
        parser.error("steps, batch, target and nstep must be positive")
    if not 0 <= args.gamma <= 1 or not 0 <= args.alpha <= 1 or not 0 <= args.beta <= 1:
        parser.error("gamma, alpha and beta must lie in [0,1]")
    if min(args.lr, args.huber, args.clip) <= 0:
        parser.error("lr, huber and clip must be positive")
    rng, source_provenance = np.random.default_rng(args.seed), {}
    model = Model(rng)
    if args.weights_in:
        model.load(args.weights_in)
    elif args.warm_start_v1:
        model.warm_start_v1(args.warm_start_v1)
    source_path = args.weights_in or args.warm_start_v1
    if source_path:
        source = Path(source_path)
        source_provenance["inputWeights"] = {"path": str(source), "sha256": v1.file_sha256(source)}
        checkpoint = source if source.suffix == ".npz" else source.with_suffix(".npz")
        if checkpoint.exists():
            source_provenance["inputCheckpoint"] = {"path": str(checkpoint), "sha256": v1.file_sha256(checkpoint)}
    target = load_target(model, args.weights_in, rng)
    episodes, paths, source_data, history = [], [], [], []
    transitions, restored, new_priority = [], 0, 1.0
    if not args.init_only:
        paths = v1.data_paths(args.data)
        if not paths:
            parser.error("Training needs --data; initialize first with --init-only")
        for path in paths:
            sha = v1.file_sha256(path)
            collected = v1.read_export(path, np.float32 if args.float32_replay else np.float16)
            for episode in collected:
                episode.source_digest = sha
            episodes.extend(collected)
            source_data.append({"path": str(path), "sha256": sha})
            print(f"loaded {path}: {len(collected)} games", flush=True)
        transitions = v1.nstep_transitions(episodes, args.nstep, args.gamma)
        keys = replay_keys(transitions)
        history, restored, new_priority = train(model, target, transitions, keys, args, rng)
    sha = write_weights(model, target, args.weights_out, args.java_out)
    evidence = {
        "schemaVersion": 2, "createdUtc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "method": "Double DQN with learned state baseline + additive advantages",
        "architecture": {"inputs": INPUTS, "hidden": HIDDEN,
                         "parameters": sum(p.size for p in model.p.values()),
                         "fullActionQ": "V(s) + sum selected cell advantages + selected ink advantage",
                         "stateValue": "mean h1 across all board cells dot WV + BV",
                         "fullWaitGradient": True},
        "initOnly": args.init_only, "warmStartV1": bool(args.warm_start_v1), "seed": args.seed,
        "games": len(episodes), "transitions": len(transitions), "updates": model.updates,
        "restoredPriorities": restored, "newItemPriority": new_priority,
        "settings": vars(args), "weightsSha256": sha, "trainingHistory": history,
        "data": source_data, **source_provenance,
        "limits": ["additive advantages still omit within-build and build/ink interactions",
                   "V/advantage decomposition is not identifiable without a centering constraint",
                   "reward/gamma and both-player off-policy replay unchanged from v1",
                   "loss/finite weights do not establish policy strength"],
    }
    evidence_path = Path(args.evidence)
    evidence_path.parent.mkdir(parents=True, exist_ok=True)
    evidence_path.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"weights": args.weights_out, "sha256": sha, "games": len(episodes),
                      "transitions": len(transitions), "updates": model.updates}), flush=True)


if __name__ == "__main__":
    main()
