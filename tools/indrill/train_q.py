"""Numpy Double DQN trainer for the independent Indrill-principle prototype.

The environment, features and rewards are exported by Java's exact btk.core engine.
This program only learns from complete outcome-driven game transitions: no teacher,
path search, hand-coded action values or arena-evaluation seeds enter the loss.
See tools/indrill/README.md for the binary contracts and a reproducible round.
"""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import gzip
import hashlib
import json
import os
from pathlib import Path
import struct
import time
from dataclasses import dataclass

# Tiny board matrices are faster with one BLAS worker and do not contend with Java.
os.environ["OPENBLAS_NUM_THREADS"] = "1"
os.environ["MKL_NUM_THREADS"] = "1"
os.environ["OMP_NUM_THREADS"] = "1"
import numpy as np

INPUTS, HIDDEN = 64, 24
DATA_MAGIC, WEIGHT_MAGIC = b"BTKQ0001", b"BTKW0001"
PARAM_SHAPES = {
    "W0": (INPUTS, HIDDEN), "B0": (HIDDEN,),
    "WS": (HIDDEN, HIDDEN), "WN": (HIDDEN, HIDDEN),
    "WG": (HIDDEN, HIDDEN), "B1": (HIDDEN,),
    "WB": (HIDDEN,), "WI": (HIDDEN,), "BB": (1,), "BI": (1,),
}


def read_exact(stream, length):
    value = stream.read(length)
    if len(value) != length:
        raise ValueError(f"Truncated BTKQ export: expected {length} bytes, got {len(value)}")
    return value


def read_int(stream):
    return struct.unpack(">i", read_exact(stream, 4))[0]


def read_array(stream, count, dtype):
    dtype = np.dtype(dtype)
    return np.frombuffer(read_exact(stream, count * dtype.itemsize), dtype=dtype)


@dataclass
class State:
    features: np.ndarray             # [2, size, 64], compact replay storage
    legal_build: np.ndarray          # [size]
    legal_ink: np.ndarray            # [regions]
    builds: tuple[tuple[int, ...], tuple[int, ...]]
    inks: tuple[int, int]
    rewards: tuple[float, float]
    done: bool


@dataclass
class Episode:
    identity: str
    width: int
    height: int
    region_count: int
    regions: np.ndarray
    costs: np.ndarray
    states: list[State]

    def __post_init__(self):
        self.size = self.width * self.height
        self.region_sizes = np.bincount(self.regions, minlength=self.region_count).astype(np.float32)
        self.neighbors = np.zeros((self.size, 4), dtype=np.int32)
        self.neighbor_mask = np.zeros((self.size, 4), dtype=bool)
        for cell in range(self.size):
            x, y = cell % self.width, cell // self.width
            for d, (dx, dy) in enumerate(((0, -1), (1, 0), (0, 1), (-1, 0))):
                if 0 <= x + dx < self.width and 0 <= y + dy < self.height:
                    self.neighbors[cell, d] = (y + dy) * self.width + x + dx
                    self.neighbor_mask[cell, d] = True
        self.degrees = np.maximum(self.neighbor_mask.sum(axis=1), 1).astype(np.float32)


def read_export(path, storage_dtype=np.float16):
    """Read complete episodes; terminal states are features, never extra transitions."""
    episodes = []
    opener = gzip.open if str(path).endswith(".gz") else open
    with opener(path, "rb") as stream:
        if read_exact(stream, 8) != DATA_MAGIC:
            raise ValueError(f"{path}: wrong dataset magic")
        features, game_count = read_int(stream), read_int(stream)
        if features != INPUTS or game_count < 1:
            raise ValueError(f"{path}: expected {INPUTS} features and at least one game")
        for game in range(game_count):
            episode_id, width, height, regions_count = (read_int(stream) for _ in range(4))
            size = width * height
            if width <= 0 or height <= 0 or size > 10000 or not 0 < regions_count <= size:
                raise ValueError(f"{path}: invalid map dimensions in episode {episode_id}")
            regions = read_array(stream, size, ">i4").astype(np.int32)
            costs = read_array(stream, size, "u1").copy()
            if (regions < 0).any() or (regions >= regions_count).any():
                raise ValueError("Invalid cell region in export")
            if not np.isin(costs, (1, 2, 3)).all():
                raise ValueError("Invalid terrain cost in export")
            state_count = read_int(stream)
            if not 2 <= state_count <= 10001:
                raise ValueError("A complete episode needs start and terminal states")
            states = []
            for step in range(state_count):
                raw = read_array(stream, 2 * size * INPUTS, ">f4")
                if not np.isfinite(raw).all() or np.abs(raw).max(initial=0) > 65504:
                    raise ValueError("Nonfinite or out-of-range replay features")
                x = raw.astype(storage_dtype).reshape(2, size, INPUTS)
                legal_build = read_array(stream, size, "u1").astype(bool)
                legal_ink = read_array(stream, regions_count, "u1").astype(bool)
                builds, inks = [], []
                for player in range(2):
                    ink, count = read_int(stream), read_int(stream)
                    cells = tuple(read_array(stream, 3, ">i4").tolist())
                    if not 0 <= count <= 3 or not -1 <= ink < regions_count:
                        raise ValueError("Invalid action dimensions in export")
                    chosen = cells[:count]
                    if len(set(chosen)) != count or any(c < 0 or c >= size for c in chosen):
                        raise ValueError("Invalid placement cells in export")
                    if chosen and (not legal_build[list(chosen)].all() or costs[list(chosen)].sum() > 3):
                        raise ValueError("Illegal placement action in export")
                    if ink >= 0 and not legal_ink[ink]:
                        raise ValueError("Illegal disruption action in export")
                    builds.append(chosen)
                    inks.append(ink)
                rewards = struct.unpack(">ff", read_exact(stream, 8))
                done_byte = read_exact(stream, 1)[0]
                if done_byte not in (0, 1) or not np.isfinite(rewards).all():
                    raise ValueError("Invalid reward/done in export")
                states.append(State(x, legal_build, legal_ink, tuple(builds), tuple(inks),
                                    rewards, bool(done_byte)))
            if not states[-1].done or not states[-2].done or any(s.done for s in states[:-2]):
                raise ValueError("Done must mark exactly the last transition and terminal state")
            if states[-1].builds != ((), ()) or states[-1].inks != (-1, -1):
                raise ValueError("Terminal feature record must have empty actions")
            episodes.append(Episode(f"{Path(path).name}:{game}:{episode_id}", width, height,
                                    regions_count, regions, costs, states))
        if stream.read(1):
            raise ValueError(f"{path}: trailing data after declared episodes")
    return episodes


@dataclass
class Transition:
    episode: Episode
    step: int
    player: int
    next_step: int
    reward: float
    discount: float


def nstep_transitions(episodes, nstep=3, gamma=0.99):
    replay = []
    for episode in episodes:
        for step in range(len(episode.states) - 1):
            for player in (0, 1):
                reward, discount, endpoint = 0.0, 1.0, step
                for offset in range(nstep):
                    state = episode.states[step + offset]
                    reward += discount * state.rewards[player]
                    discount *= gamma
                    endpoint = step + offset + 1
                    if state.done:
                        discount = 0.0
                        break
                replay.append(Transition(episode, step, player, endpoint, reward, discount))
    return replay


class Batch:
    """Padding only; local neighbors and region means use the actual map topology."""
    def __init__(self, transitions, next_states=False):
        self.transitions = transitions
        b = len(transitions)
        n = max(t.episode.size for t in transitions)
        r = max(t.episode.region_count for t in transitions)
        self.x = np.zeros((b, n, INPUTS), dtype=np.float32)
        self.mask = np.zeros((b, n), dtype=bool)
        self.regions = np.zeros((b, n), dtype=np.int32)
        self.region_sizes = np.ones((b, r), dtype=np.float32)
        self.neighbors = np.zeros((b, n, 4), dtype=np.int32)
        self.neighbor_mask = np.zeros((b, n, 4), dtype=bool)
        self.degrees = np.ones((b, n), dtype=np.float32)
        self.sizes = np.empty(b, dtype=np.float32)
        self.legal_build = np.zeros((b, n), dtype=bool)
        self.legal_ink = np.zeros((b, r), dtype=bool)
        self.costs = np.ones((b, n), dtype=np.int32)
        self.builds, self.inks = [], []
        for i, item in enumerate(transitions):
            episode = item.episode
            size, regions = episode.size, episode.region_count
            state = episode.states[item.next_step if next_states else item.step]
            self.x[i, :size] = state.features[item.player]
            self.mask[i, :size] = True
            self.regions[i, :size] = episode.regions
            self.region_sizes[i, :regions] = np.maximum(episode.region_sizes, 1)
            self.neighbors[i, :size] = episode.neighbors
            self.neighbor_mask[i, :size] = episode.neighbor_mask
            self.degrees[i, :size] = episode.degrees
            self.sizes[i] = size
            self.legal_build[i, :size] = state.legal_build
            self.legal_ink[i, :regions] = state.legal_ink
            self.costs[i, :size] = episode.costs
            self.builds.append(state.builds[item.player])
            self.inks.append(state.inks[item.player])


def choose_build(values, costs, legal):
    """Exact additive paint=3 maximizer with Java's pattern/cell tie policy.

    Every feasible subset is either up to three plains, one river plus at most
    one plain, or one mountain. Each pattern keeps its best cells; equal-value
    patterns prefer plains, then river+plain, then mountain. Empty value is zero.
    """
    plains, river, mountain = [], -1, -1
    for cell in np.flatnonzero(legal):
        cell, cost = int(cell), int(costs[cell])
        if not 1 <= cost <= 3:
            raise ValueError("Paint costs must be 1, 2 or 3")
        if cost == 1:
            plains.append(cell)
            plains.sort(key=lambda c: -float(values[c]))  # stable ascending-cell ties
            del plains[3:]
        elif cost == 2 and (river < 0 or values[cell] > values[river]):
            river = cell
        elif cost == 3 and (mountain < 0 or values[cell] > values[mountain]):
            mountain = cell
    chosen = [cell for cell in plains if values[cell] > 0]
    best = np.float32(0)
    for cell in chosen:
        best = np.float32(best + values[cell])
    if river >= 0:
        plain = plains[0] if plains and values[plains[0]] > 0 else -1
        mixed = np.float32(values[river] + (values[plain] if plain >= 0 else 0))
        if mixed > best:
            best = mixed
            chosen = [river] + ([plain] if plain >= 0 else [])
    if mountain >= 0 and values[mountain] > best:
        chosen = [mountain]
    return tuple(sorted(chosen))


def choose_actions(build_q, ink_q, batch):
    builds, inks = [], []
    for i in range(len(batch.transitions)):
        builds.append(choose_build(build_q[i], batch.costs[i], batch.legal_build[i]))
        best_ink, best_value = -1, 0.0
        for region in np.flatnonzero(batch.legal_ink[i]):
            if float(ink_q[i, region]) > best_value:
                best_value, best_ink = float(ink_q[i, region]), int(region)
        inks.append(best_ink)
    return builds, inks


def action_values(build_q, ink_q, builds, inks):
    values = np.zeros(len(builds), dtype=np.float32)
    for i, (cells, ink) in enumerate(zip(builds, inks)):
        if cells:
            values[i] = build_q[i, list(cells)].sum()
        if ink >= 0:
            values[i] += ink_q[i, ink]
    return values


class Model:
    def __init__(self, rng):
        self.p = {}
        for name, shape in PARAM_SHAPES.items():
            self.p[name] = np.zeros(shape, dtype=np.float32)
            if name.startswith("W"):
                fan_in = shape[0]
                # Neighbor and global branches share the second layer's variance.
                scale = np.sqrt(2.0 / fan_in / (3 if name in ("WS", "WN", "WG") else 1))
                if name in ("WB", "WI"):
                    scale *= 0.1
                self.p[name][:] = rng.standard_normal(shape) * scale
        self.m = {name: np.zeros_like(p) for name, p in self.p.items()}
        self.v = {name: np.zeros_like(p) for name, p in self.p.items()}
        self.updates = 0

    def copy_from(self, other):
        for name in self.p:
            self.p[name][:] = other.p[name]

    def forward(self, batch, cache=False):
        p, x = self.p, batch.x
        h0 = np.maximum(x @ p["W0"] + p["B0"], 0) * batch.mask[..., None]
        ids = np.arange(len(x))[:, None, None]
        neighbors = h0[ids, batch.neighbors]
        local = (neighbors * batch.neighbor_mask[..., None]).sum(axis=2) / batch.degrees[..., None]
        global_h = h0.sum(axis=1) / batch.sizes[:, None]
        h1 = np.maximum(h0 @ p["WS"] + local @ p["WN"] +
                        (global_h @ p["WG"])[:, None] + p["B1"], 0) * batch.mask[..., None]
        region_h = np.zeros((*batch.region_sizes.shape, HIDDEN), dtype=np.float32)
        bi, cells = np.nonzero(batch.mask)
        np.add.at(region_h, (bi, batch.regions[bi, cells]), h1[bi, cells])
        region_h /= batch.region_sizes[..., None]
        build_q = h1 @ p["WB"] + p["BB"][0]
        ink_q = region_h @ p["WI"] + p["BI"][0]
        if cache:
            return build_q, ink_q, (h0, local, global_h, h1, region_h)
        return build_q, ink_q

    def loss_gradients(self, batch, targets, importance, huber=1.0):
        build_q, ink_q, cache = self.forward(batch, cache=True)
        h0, local, global_h, h1, region_h = cache
        actual = action_values(build_q, ink_q, batch.builds, batch.inks)
        errors = actual - targets
        absolute = np.abs(errors)
        losses = np.where(absolute <= huber, 0.5 * errors ** 2, huber * (absolute - 0.5 * huber))
        derivative = np.clip(errors, -huber, huber) * importance / len(errors)
        grad = {name: np.zeros_like(p) for name, p in self.p.items()}
        dh1 = np.zeros_like(h1)
        for i, (cells, ink) in enumerate(zip(batch.builds, batch.inks)):
            d = derivative[i]
            if cells:
                selected = list(cells)
                grad["WB"] += h1[i, selected].sum(axis=0) * d
                grad["BB"][0] += len(cells) * d
                dh1[i, selected] += self.p["WB"] * d
            if ink >= 0:
                grad["WI"] += region_h[i, ink] * d
                grad["BI"][0] += d
                mask = batch.mask[i] & (batch.regions[i] == ink)
                dh1[i, mask] += self.p["WI"] * (d / batch.region_sizes[i, ink])
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

    def adam(self, gradients, learning_rate, clip_norm=10.0):
        norm = float(np.sqrt(sum(float(np.sum(g * g)) for g in gradients.values())))
        factor = min(1.0, clip_norm / max(norm, 1e-12))
        self.updates += 1
        for name, p in self.p.items():
            g = gradients[name] * factor
            self.m[name][:] = 0.9 * self.m[name] + 0.1 * g
            self.v[name][:] = 0.999 * self.v[name] + 0.001 * g * g
            mhat = self.m[name] / (1.0 - 0.9 ** self.updates)
            vhat = self.v[name] / (1.0 - 0.999 ** self.updates)
            p -= learning_rate * mhat / (np.sqrt(vhat) + 1e-8)
        return norm

    def load(self, path):
        path = Path(path)
        # Passing a Java-readable .bin resumes its matching Adam/target checkpoint.
        checkpoint = path if path.suffix == ".npz" else path.with_suffix(".npz")
        if checkpoint.exists():
            with np.load(checkpoint, allow_pickle=False) as saved:
                for name, shape in PARAM_SHAPES.items():
                    value = saved[name]
                    if value.shape != shape:
                        raise ValueError(f"Wrong shape for {name}: {value.shape}")
                    self.p[name][:] = value
                    if f"adam_m_{name}" in saved:
                        self.m[name][:] = saved[f"adam_m_{name}"]
                        self.v[name][:] = saved[f"adam_v_{name}"]
                self.updates = int(saved["updates"]) if "updates" in saved else 0
            if path.suffix != ".npz":
                with path.open("rb") as stream:
                    if read_exact(stream, 8) != WEIGHT_MAGIC or read_int(stream) != INPUTS or read_int(stream) != HIDDEN:
                        raise ValueError("Checkpoint's binary has an invalid header")
                    for name, shape in PARAM_SHAPES.items():
                        value = read_array(stream, int(np.prod(shape)), ">f4").reshape(shape)
                        if not np.array_equal(value, self.p[name]):
                            raise ValueError(f"Checkpoint and self-play weight binary disagree in {name}")
                    if stream.read(1):
                        raise ValueError("Trailing weight data")
        else:
            with path.open("rb") as stream:
                if read_exact(stream, 8) != WEIGHT_MAGIC:
                    raise ValueError("Wrong weight binary magic")
                if read_int(stream) != INPUTS or read_int(stream) != HIDDEN:
                    raise ValueError("Wrong weight binary architecture")
                for name, shape in PARAM_SHAPES.items():
                    self.p[name][:] = read_array(stream, int(np.prod(shape)), ">f4").reshape(shape)
                if stream.read(1):
                    raise ValueError("Trailing weight data")
        if any(not np.isfinite(p).all() for p in self.p.values()):
            raise ValueError("Nonfinite weights")


def write_weights(model, target, binary_path, java_path=None):
    binary_path = Path(binary_path)
    binary_path.parent.mkdir(parents=True, exist_ok=True)
    blob = WEIGHT_MAGIC + struct.pack(">ii", INPUTS, HIDDEN)
    for p in model.p.values():
        blob += p.astype(">f4").tobytes()
    binary_path.write_bytes(blob)
    checkpoint = dict(model.p)
    checkpoint["updates"] = np.asarray(model.updates, dtype=np.int64)
    for name in model.p:
        checkpoint[f"adam_m_{name}"] = model.m[name]
        checkpoint[f"adam_v_{name}"] = model.v[name]
        checkpoint[f"target_{name}"] = target.p[name]
    np.savez_compressed(binary_path.with_suffix(".npz"), **checkpoint)
    if java_path:
        lines = ["package btk.bot.current;", "", "import java.util.Base64;", "",
                 "/** Generated by tools/indrill/train_q.py; do not edit weights by hand. */",
                 "public final class IndrillWeights {", "    private IndrillWeights() {}"]
        for name, p in model.p.items():
            encoded = base64.b64encode(p.astype(">f4").tobytes()).decode("ascii")
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


def load_target(model, path, rng):
    target = Model(rng)
    target.copy_from(model)
    if path:
        checkpoint = Path(path) if Path(path).suffix == ".npz" else Path(path).with_suffix(".npz")
        if checkpoint.exists():
            with np.load(checkpoint, allow_pickle=False) as saved:
                for name in model.p:
                    if f"target_{name}" in saved:
                        target.p[name][:] = saved[f"target_{name}"]
    return target


def train(model, target, transitions, args, rng):
    priorities = np.ones(len(transitions), dtype=np.float64)
    history = []
    started = time.monotonic()
    for step in range(args.steps):
        probabilities = priorities ** args.alpha
        probabilities /= probabilities.sum()
        indices = rng.choice(len(transitions), size=args.batch, replace=True, p=probabilities)
        sampled = [transitions[i] for i in indices]
        beta = args.beta + (1 - args.beta) * step / max(args.steps - 1, 1)
        importance = (len(transitions) * probabilities[indices]) ** (-beta)
        importance = (importance / importance.max()).astype(np.float32)
        current = Batch(sampled)
        # Online selects the complete legal greedy joint action. The target network
        # evaluates that same action, never its own maximizer (Double DQN).
        following = Batch(sampled, next_states=True)
        online_build, online_ink = model.forward(following)
        chosen_builds, chosen_inks = choose_actions(online_build, online_ink, following)
        target_build, target_ink = target.forward(following)
        continuation = action_values(target_build, target_ink, chosen_builds, chosen_inks)
        targets = np.asarray([t.reward for t in sampled], dtype=np.float32)
        targets += np.asarray([t.discount for t in sampled], dtype=np.float32) * continuation
        loss, errors, gradients = model.loss_gradients(current, targets, importance, args.huber)
        norm = model.adam(gradients, args.lr, args.clip)
        # A sampled item can appear twice; keep the largest observed residual.
        for index in np.unique(indices):
            priorities[index] = float(np.abs(errors[indices == index]).max()) + 1e-4
        if model.updates % args.target == 0:
            target.copy_from(model)
        if step == 0 or (step + 1) % 50 == 0 or step + 1 == args.steps:
            row = {"step": step + 1, "updates": model.updates, "loss": loss,
                   "meanAbsTd": float(np.abs(errors).mean()), "gradNorm": norm,
                   "beta": float(beta), "seconds": round(time.monotonic() - started, 3)}
            history.append(row)
            print(json.dumps(row), flush=True)
        if not np.isfinite(loss) or any(not np.isfinite(p).all() for p in model.p.values()):
            raise RuntimeError("Training diverged; refusing to export weights")
    return history


def data_paths(arguments):
    paths = []
    for argument in arguments:
        path = Path(argument)
        if path.is_dir():
            paths += sorted(path.glob("*.bin.gz")) + sorted(path.glob("*.btkq.gz"))
            paths += sorted(path.glob("*.bin"))
        else:
            paths.append(path)
    return sorted(set(paths))


def file_sha256(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as source:
        for chunk in iter(lambda: source.read(65536), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--data", nargs="+", default=[])
    parser.add_argument("--weights-in")
    parser.add_argument("--weights-out", default="build/analysis/indrill-q/weights.bin")
    parser.add_argument("--java-out", default="src/main/java/btk/bot/current/IndrillWeights.java")
    parser.add_argument("--evidence", default="build/analysis/indrill-q/training.json")
    parser.add_argument("--init-only", action="store_true")
    parser.add_argument("--seed", type=int, default=20261003)
    parser.add_argument("--steps", type=int, default=400)
    parser.add_argument("--batch", type=int, default=8)
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
    if min(args.steps, args.batch, args.target, args.nstep) < 1:
        parser.error("steps, batch, target and nstep must be positive")
    if not 0 <= args.gamma <= 1 or not 0 <= args.alpha <= 1 or not 0 <= args.beta <= 1:
        parser.error("gamma, alpha and beta must lie in [0, 1]")
    if min(args.lr, args.huber, args.clip) <= 0:
        parser.error("lr, huber and clip must be positive")
    rng = np.random.default_rng(args.seed)
    model = Model(rng)
    if args.weights_in:
        model.load(args.weights_in)
    target = load_target(model, args.weights_in, rng)
    paths, episodes, transitions, history = [], [], [], []
    if not args.init_only:
        paths = data_paths(args.data)
        if not paths:
            parser.error("Training requires --data; bootstrap with --init-only first")
        for path in paths:
            before = len(episodes)
            episodes += read_export(path, np.float32 if args.float32_replay else np.float16)
            print(f"loaded {path}: {len(episodes) - before} complete games", flush=True)
        transitions = nstep_transitions(episodes, args.nstep, args.gamma)
        history = train(model, target, transitions, args, rng)
    sha256 = write_weights(model, target, args.weights_out, args.java_out)
    evidence = {
        "createdUtc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "method": "joint additive Double DQN, n-step prioritized replay, Huber, Adam",
        "architecture": {"inputs": INPUTS, "hidden": HIDDEN,
                         "parameters": sum(p.size for p in model.p.values()),
                         "context": "one N/E/S/W neighbor-mean + board-mean message layer",
                         "waitValue": 0},
        "initOnly": args.init_only, "seed": args.seed, "games": len(episodes),
        "transitions": len(transitions), "updates": model.updates, "settings": vars(args),
        "weightsSha256": sha256, "trainingHistory": history,
        "data": [{"path": str(p), "sha256": file_sha256(p)}
                 for p in paths],
        "limits": ["prototype; this training loss does not establish arena strength",
                   "empty build and disruption action have fixed Q=0",
                   "replay priorities restart each round; Adam and target state resume"],
    }
    if args.weights_in:
        source = Path(args.weights_in)
        checkpoint = source if source.suffix == ".npz" else source.with_suffix(".npz")
        evidence["inputWeights"] = {"path": str(source), "sha256": file_sha256(source)}
        if checkpoint.exists():
            evidence["inputCheckpoint"] = {"path": str(checkpoint), "sha256": file_sha256(checkpoint)}
    evidence_path = Path(args.evidence)
    evidence_path.parent.mkdir(parents=True, exist_ok=True)
    evidence_path.write_text(json.dumps(evidence, indent=2) + "\n", encoding="utf-8")
    print(json.dumps({"weights": args.weights_out, "sha256": sha256,
                      "games": len(episodes), "transitions": len(transitions),
                      "updates": model.updates}), flush=True)


if __name__ == "__main__":
    main()
