"""Outcome-only fine tuning of the exact shipped neural-v1 policy (numpy only).

Init:  python tools/neural/train_outcome_v4.py init --out-weights W.json --out-checkpoint C.npz
Train: python tools/neural/train_outcome_v4.py train --data COLLECTED --checkpoint C.npz
           --weights W.json --out-weights W2.json --out-checkpoint C2.npz --evidence E.json

For each opponent cohort, A_g = outcome_g - mean(outcome_h for h != g). No
advantage scaling, discounting, critic, score shaping, entropy bonus, or weight
decay. The same fixed advantage applies to all selectable decisions of a game.
Each head minimizes mean_d [-min(r_d A_g, clip(r_d,.8,1.2) A_g)
                              + .02 KL(frozen_v1_T1(.|s_d) || policy_T1(.|s_d))].
The actor ratio uses pi_T: sabotage T=1, construction T=.5. The actor gradient
includes 1/T; the teacher KL remains at T=1, with its original gradient. This is
the only learning-policy change from outcome-v2; RNG and optimizer settings match.
All decisions have equal weight: the episodic policy gradient sums action
log probabilities, without dividing by action-dependent game length. The teacher and
behavior distributions use the complete candidate mask recorded by the collector.
The teacher remains the original v1; behavior log probabilities remain those of
the actual quantized sampling policy throughout all four update epochs.

Normalization, weights, Adam moments/steps, original prior blobs, and RNG state
are checkpointed. At each new round float parameters rebase onto the exact exported
int16 collection policy; Adam state and RNG persist across rounds. Chosen behavior
log probabilities stay fixed in the data. Final export diagnostics include
Java-order float32 scoring samples.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import time

# Small matrix workloads must not launch large nested BLAS thread pools.
for _variable in ("OPENBLAS_NUM_THREADS", "OMP_NUM_THREADS", "MKL_NUM_THREADS"):
    os.environ.setdefault(_variable, "1")
import numpy as np

from train_neural import Mlp, java_string

ROOT = Path(__file__).resolve().parents[2]
HEADS = ("sabotage", "build")
CONFIG = {"clip": .2, "kl": .02, "batch": 64, "epochs": 4,
          "lr": 3e-5, "gradNorm": 1., "weightDecay": 0.,
          "temperatures": {"sabotage": 1., "build": .5}, "teacherTemperature": 1.,
          "gamma": 1., "advantageNormalization": False,
          "baseline": "per-opponent leave-one-game-out outcome mean",
          "decisionWeight": "uniform; no per-game length normalization"}


def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            h.update(chunk)
    return h.hexdigest()


def read_shipped_weights(source=None):
    source = Path(source or ROOT / "src/main/java/btk/bot/neural/NeuralWeights.java")
    text = source.read_text(encoding="utf-8")
    evidence = json.loads((ROOT / "docs/evidence/neural-v1-training.json").read_text(encoding="utf-8"))
    result = {}
    for name in HEADS:
        match = re.search(r"public static final String " + name.upper() + r"\s*=\s*(.*?);", text, re.S)
        if match is None:
            raise ValueError(f"Missing shipped {name} weights in {source}")
        strings = re.findall(r'"([A-Za-z0-9+/=]*)"', match.group(1))
        result[name] = {"sizes": evidence["heads"][name]["sizes"], "base64": "".join(strings)}
    return result


def decode(item):
    """Use the shipped scale/inverse-std float32 values without recomputing them."""
    sizes = list(item["sizes"])
    model = Mlp(sizes, np.random.default_rng(0))
    blob = base64.b64decode(item["base64"], validate=True)
    f = sizes[0]
    model.mean = np.frombuffer(blob, dtype="<f4", count=f).copy()
    model.inv_std = np.frombuffer(blob, dtype="<f4", count=f, offset=4 * f).copy()
    offset = 8 * f
    for layer, (inputs, outputs) in enumerate(zip(sizes[:-1], sizes[1:])):
        scale = np.float32(struct.unpack_from("<f", blob, offset)[0])
        offset += 4
        q = np.frombuffer(blob, dtype="<i2", count=inputs * outputs, offset=offset)
        model.w[layer][...] = q.reshape(outputs, inputs).T.astype(np.float32) * scale
        offset += 2 * inputs * outputs
        q = np.frombuffer(blob, dtype="<i2", count=outputs, offset=offset)
        model.b[layer][...] = q.astype(np.float32) * scale
        offset += 2 * outputs
    if offset != len(blob) or not all(np.isfinite(p).all() for p in model.params):
        raise ValueError("Invalid weight blob")
    if not np.isfinite(model.mean).all() or not np.isfinite(model.inv_std).all() or (model.inv_std <= 0).any():
        raise ValueError("Invalid frozen normalization")
    model.initial_blob = blob
    return model


def export_head(model):
    # Preserve exact bootstrap artifact, including the producer's rounding choices.
    if model.step == 0:
        blob = model.initial_blob
    else:
        blob = bytearray(model.mean.astype("<f4").tobytes())
        blob += model.inv_std.astype("<f4").tobytes()
        for weights, biases in zip(model.w, model.b):
            scale = np.float32(max(float(np.abs(weights).max()), float(np.abs(biases).max()), 1e-12) / 32767.)
            blob += struct.pack("<f", scale)
            blob += np.clip(np.rint(weights.T / scale), -32767, 32767).astype("<i2").tobytes()
            blob += np.clip(np.rint(biases / scale), -32767, 32767).astype("<i2").tobytes()
        blob = bytes(blob)
    return {"sizes": model.sizes, "base64": base64.b64encode(blob).decode("ascii")}


def log_softmax(scores, mask):
    if scores.shape != mask.shape or not np.all(mask.any(axis=1)):
        raise ValueError("Every decision requires a nonempty candidate mask")
    scores = np.where(mask, scores.astype(np.float64), -np.inf)
    shifted = scores - scores.max(axis=1, keepdims=True)
    return shifted - np.log(np.exp(shifted).sum(axis=1, keepdims=True))


def objective(scores, mask, labels, old_logp, teacher_scores, advantages, weights,
              clip=CONFIG["clip"], kl_coefficient=CONFIG["kl"], temperature=1.):
    """Pure masked surrogate and analytic d(loss)/d(logit), useful for finite differences."""
    if not np.isfinite(temperature) or temperature <= 0:
        raise ValueError("Actor temperature must be positive and finite")
    logp = log_softmax(np.asarray(scores, dtype=np.float64) / temperature, mask)
    student_t1_logp = log_softmax(scores, mask)
    teacher_logp = log_softmax(teacher_scores, mask)
    p, q = np.exp(logp), np.exp(teacher_logp)
    rows = np.arange(len(labels))
    ratio = np.exp(logp[rows, labels] - old_logp)
    if not np.isfinite(ratio).all():
        raise ValueError("Non-finite behavior ratio")
    clipped = np.clip(ratio, 1. - clip, 1. + clip)
    surrogate = np.minimum(ratio * advantages, clipped * advantages)
    # Avoid 0 * (-inf - -inf) at padding without changing genuine tiny probabilities.
    delta = np.where(mask, teacher_logp, 0.) - np.where(mask, student_t1_logp, 0.)
    divergence = np.sum(q * delta, axis=1)
    scale = weights / len(labels)
    loss = np.sum(scale * (-surrogate + kl_coefficient * divergence))
    active = ((advantages > 0) & (ratio < 1. + clip)) | ((advantages < 0) & (ratio > 1. - clip))
    coefficient = -scale * advantages * ratio * active
    grad = -coefficient[:, None] * p
    grad[rows, labels] += coefficient
    grad /= temperature
    grad += (kl_coefficient * scale)[:, None] * (np.exp(student_t1_logp) - q)
    grad[~mask] = 0.
    metrics = {"loss": float(loss), "policyLoss": float(np.sum(-scale * surrogate)),
               "teacherKl": float(np.sum(scale * divergence)),
               "clipFraction": float(np.mean((ratio < 1. - clip) | (ratio > 1. + clip))),
               "ratioMean": float(ratio.mean()), "ratioMax": float(ratio.max())}
    return float(loss), grad, metrics


def loss_and_grads(model, batch, temperature=1.):
    x, mask, labels, old_logp, teacher, advantages, weights = batch
    acts = model.forward((x - model.mean) * model.inv_std)
    loss, grad, metrics = objective(acts[-1][..., 0], mask, labels, old_logp, teacher, advantages, weights,
                                    temperature=temperature)
    d = grad[..., None].astype(np.float32)
    grads_w, grads_b = [None] * len(model.w), [None] * len(model.w)
    for layer in range(len(model.w) - 1, -1, -1):
        grads_w[layer] = np.einsum("bci,bco->io", acts[layer], d)
        grads_b[layer] = d.sum(axis=(0, 1))
        if layer > 0:
            d = (d @ model.w[layer].T) * (acts[layer] > 0)
    return loss, grads_w + grads_b, metrics


def advantages_for_games(games):
    ids = [g["id"] for g in games]
    if len(set(ids)) != len(ids):
        raise ValueError("Duplicate game ids")
    cohorts, advantages = {}, {}
    for game in games:
        if game["outcome"] not in (-1, 0, 1):
            raise ValueError("Outcome must be -1/0/+1 from the student seat")
        cohorts.setdefault(game["opponent"], []).append(game)
    stats = {}
    for opponent, cohort in cohorts.items():
        if len(cohort) < 2:
            raise ValueError(f"Need at least two games versus {opponent} for leave-one-out baseline")
        total = sum(g["outcome"] for g in cohort)
        for game in cohort:
            advantages[game["id"]] = game["outcome"] - (total - game["outcome"]) / (len(cohort) - 1)
        stats[opponent] = {"games": len(cohort), "wins": sum(g["outcome"] == 1 for g in cohort),
                           "draws": sum(g["outcome"] == 0 for g in cohort),
                           "losses": sum(g["outcome"] == -1 for g in cohort), "meanOutcome": total / len(cohort)}
    return advantages, stats


class DataHead:
    def __init__(self, directory, name, meta, advantages):
        directory = Path(directory)
        self.name = name
        self.features = list(meta[name + "Features"])
        width = len(self.features)
        self.x = np.memmap(directory / f"{name}.bin", dtype="<f4", mode="r").reshape(-1, width)
        self.idx = np.fromfile(directory / f"{name}.idx", dtype="<i4").reshape(-1, 6)
        self.old_logp = np.fromfile(directory / f"{name}.logp", dtype="<f4")
        self.teacher = np.memmap(directory / f"{name}.teacher", dtype="<f4", mode="r")
        if len(self.idx) == 0 or len(self.old_logp) != len(self.idx) or len(self.teacher) != len(self.x):
            raise ValueError(f"{name}: inconsistent or empty data lengths")
        starts, counts, labels = self.idx[:, 3], self.idx[:, 4], self.idx[:, 5]
        if starts[0] != 0 or (counts < 2).any() or not np.array_equal(starts[1:], (starts + counts)[:-1]):
            raise ValueError(f"{name}: rows must be contiguous non-forced candidate groups")
        if starts[-1] + counts[-1] != len(self.x) or (labels < 0).any() or (labels >= counts).any():
            raise ValueError(f"{name}: label or row count mismatch")
        if not np.isfinite(self.old_logp).all() or (self.old_logp > 1e-6).any():
            raise ValueError(f"{name}: invalid behavior log probabilities")
        for begin in range(0, len(self.x), 65536):
            if not np.isfinite(self.x[begin:begin + 65536]).all() or not np.isfinite(self.teacher[begin:begin + 65536]).all():
                raise ValueError(f"{name}: non-finite features/teacher")
        self.advantages = np.asarray([advantages[int(g)] for g in self.idx[:, 0]], dtype=np.float64)
        _, counts_per_game = np.unique(self.idx[:, 0], return_counts=True)
        self.weights = np.ones(len(self.idx), dtype=np.float64)
        self.game_count = len(counts_per_game)
        self.game_decisions_min = int(counts_per_game.min())
        self.game_decisions_max = int(counts_per_game.max())

    def gather(self, selected):
        groups = self.idx[selected]
        size = int(groups[:, 4].max())
        index = np.zeros((len(groups), size), dtype=np.int64)
        mask = np.zeros_like(index, dtype=bool)
        for i, row in enumerate(groups):
            start, count = row[3:5]
            index[i, :count] = np.arange(start, start + count)
            mask[i, :count] = True
        x = np.asarray(self.x[index]).copy()
        x[~mask] = 0.
        teacher = np.asarray(self.teacher[index]).copy()
        teacher[~mask] = 0.
        return (x, mask, groups[:, 5], self.old_logp[selected].astype(np.float64), teacher,
                self.advantages[selected], self.weights[selected])


def java_scores(model, raw):
    """Reference the exact operation order of NeuralNet.score, for exported parity cases."""
    h = (np.asarray(raw, dtype=np.float32) - model.mean) * model.inv_std
    for layer, (weights, biases) in enumerate(zip(model.w, model.b)):
        result = np.empty((len(h), len(biases)), dtype=np.float32)
        for output in range(len(biases)):
            total = np.full(len(h), biases[output], dtype=np.float32)
            for feature in range(weights.shape[0]):
                total += weights[feature, output] * h[:, feature]
            result[:, output] = total if layer == len(model.w) - 1 else np.maximum(total, 0.)
        h = result
    return h[:, 0]


def check_data_policy(head, behavior, prior):
    # Deterministic evenly spaced whole decisions, no outcome-based selection.
    selected = np.unique(np.linspace(0, len(head.idx) - 1, min(128, len(head.idx)), dtype=int))
    max_logp_error, max_teacher_error, max_numpy_logp_error = 0., 0., 0.
    logp_matches = True
    for start in range(0, len(selected), CONFIG["batch"]):
        batch = head.gather(selected[start:start + CONFIG["batch"]])
        x, mask, labels, old_logp, teacher = batch[:5]
        logits = java_scores(behavior, x.reshape(-1, x.shape[-1])).reshape(mask.shape)
        temperature = CONFIG["temperatures"][head.name]
        expected = log_softmax(logits.astype(np.float64) / temperature, mask)[np.arange(len(labels)), labels]
        prior_logits = java_scores(prior, x.reshape(-1, x.shape[-1])).reshape(mask.shape)
        numpy_logits = behavior.forward((x - behavior.mean) * behavior.inv_std)[-1][..., 0]
        numpy_logp = log_softmax(numpy_logits.astype(np.float64) / temperature, mask)[np.arange(len(labels)), labels]
        max_logp_error = max(max_logp_error, float(np.abs(expected - old_logp).max()))
        logp_matches = logp_matches and bool(np.all(np.abs(expected - old_logp) <= 1e-4 + 1e-5 * np.abs(old_logp)))
        max_teacher_error = max(max_teacher_error, float(np.abs(prior_logits[mask] - teacher[mask]).max()))
        max_numpy_logp_error = max(max_numpy_logp_error, float(np.abs(numpy_logp - old_logp).max()))
        logp_matches = logp_matches and bool(np.all(np.abs(numpy_logp - old_logp) <= 1e-4 + 1e-5 * np.abs(old_logp)))
    if not logp_matches or max_teacher_error > 2e-4:
        raise ValueError(f"{head.name}: collected policy mismatch: logp={max_logp_error}, numpyLogp={max_numpy_logp_error}, teacher={max_teacher_error}")
    return {"decisionsChecked": len(selected), "maxBehaviorLogpError": max_logp_error,
            "maxTeacherScoreError": max_teacher_error, "maxNumpyTrainingLogpError": max_numpy_logp_error,
            "logpTolerance": "1e-4 + 1e-5 * abs(logp)"}


def rebase_to_export(model, item):
    """Rebase parameters only; retain Adam moments, step, RNG, and original prior blob."""
    behavior = decode(item)
    if model.sizes != behavior.sizes or not np.array_equal(model.mean, behavior.mean) or not np.array_equal(model.inv_std, behavior.inv_std):
        raise ValueError("Rebase must preserve architecture and frozen normalization")
    maximum = max(float(np.max(np.abs(p - q))) for p, q in zip(model.params, behavior.params))
    for parameter, quantized in zip(model.params, behavior.params):
        parameter[...] = quantized
    return {"maxParameterRebaseDelta": maximum, "optimizerStepRetained": model.step,
            "parametersExactlyEqualDecodedBehavior": all(np.array_equal(p, q) for p, q in zip(model.params, behavior.params))}


def train_head(model, head, rng):
    history = []
    for epoch in range(CONFIG["epochs"]):
        total = {k: 0. for k in ("loss", "policyLoss", "teacherKl", "clipFraction", "ratioMean")}
        maximum_norm = 0.
        order = rng.permutation(len(head.idx))
        for start in range(0, len(order), CONFIG["batch"]):
            selected = order[start:start + CONFIG["batch"]]
            _, grads, metrics = loss_and_grads(model, head.gather(selected), CONFIG["temperatures"][head.name])
            norm = float(np.sqrt(sum(np.sum(g.astype(np.float64) ** 2) for g in grads)))
            if not np.isfinite(norm):
                raise ValueError("Non-finite gradient")
            maximum_norm = max(maximum_norm, norm)
            if norm > CONFIG["gradNorm"]:
                grads = [g * (CONFIG["gradNorm"] / norm) for g in grads]
            model.adam(grads, CONFIG["lr"], weight_decay=0.)
            for key in total:
                total[key] += metrics[key] * len(selected)
        record = {"epoch": epoch + 1, **{k: v / len(order) for k, v in total.items()},
                  "maxUnclippedGradNorm": maximum_norm, "optimizerStep": model.step}
        history.append(record)
        print(f"{head.name} epoch {epoch + 1}/{CONFIG['epochs']}: loss={record['loss']:.6f} "
              f"priorKL={record['teacherKl']:.6f} clipped={record['clipFraction']:.4f}", flush=True)
    return history


def write_json(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as target:
        target.write(json.dumps(value, indent=2, allow_nan=False) + "\n")


def save_checkpoint(path, models, prior, rng, rounds):
    meta = {"schema": "BTKNEURALV4CHECKPOINT1", "config": CONFIG, "prior": prior,
            "rng": rng.bit_generator.state, "rounds": rounds, "heads": {}}
    arrays = {}
    for name, model in models.items():
        meta["heads"][name] = {"sizes": model.sizes, "step": model.step,
                                "initialBlob": base64.b64encode(model.initial_blob).decode("ascii")}
        arrays[name + "_mean"] = model.mean
        arrays[name + "_inv_std"] = model.inv_std
        for kind, values in (("p", model.params), ("m", model.m), ("v", model.v)):
            for i, value in enumerate(values):
                arrays[f"{name}_{kind}_{i}"] = value
    arrays["metadata"] = np.asarray(json.dumps(meta))
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("xb") as target:
        np.savez_compressed(target, **arrays)


def load_checkpoint(path):
    models = {}
    with np.load(path, allow_pickle=False) as data:
        meta = json.loads(str(data["metadata"]))
        if meta["schema"] != "BTKNEURALV4CHECKPOINT1" or meta["config"] != CONFIG:
            raise ValueError("Checkpoint schema/objective configuration mismatch")
        for name in HEADS:
            h = meta["heads"][name]
            model = decode({"sizes": h["sizes"], "base64": h["initialBlob"]})
            model.step = h["step"]
            model.mean[...] = data[name + "_mean"]
            model.inv_std[...] = data[name + "_inv_std"]
            for kind, values in (("p", model.params), ("m", model.m), ("v", model.v)):
                for i, value in enumerate(values):
                    value[...] = data[f"{name}_{kind}_{i}"]
            models[name] = model
    rng = np.random.default_rng()
    rng.bit_generator.state = meta["rng"]
    return models, meta["prior"], rng, meta["rounds"]


def write_java(path, heads, summary):
    lines = ["package btk.bot.neural;", "", "/** GENERATED by tools/neural/train_outcome_v4.py; do not edit by hand. */",
             "public final class NeuralV4Weights {", "    private NeuralV4Weights() {}", "",
             f"    public static final String TRAINING = {json.dumps(summary)};"]
    for name, item in heads.items():
        sizes = ", ".join(str(s) for s in item["sizes"][1:])
        lines.append(f"    public static final int[] {name.upper()}_SIZES = {{NeuralFeatures.{name.upper()}_F, {sizes}}};")
        lines.append(java_string(name.upper(), item["base64"]).rstrip())
    lines.append("}")
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def parity_cases(model, item, head):
    rows = np.unique(np.linspace(0, len(head.x) - 1, min(32, len(head.x)), dtype=int))
    raw = np.asarray(head.x[rows])
    quant = decode(item)
    original = model.forward((raw - model.mean) * model.inv_std)[-1][:, 0]
    quant_numpy = quant.forward((raw - quant.mean) * quant.inv_std)[-1][:, 0]
    java = java_scores(quant, raw)
    return {"maxQuantizationScoreError": float(np.max(np.abs(original - java))),
            "maxNumpyVsJavaOrderScoreError": float(np.max(np.abs(quant_numpy - java))),
            "cases": [{"row": int(row), "features": features.tolist(), "score": float(score)}
                      for row, features, score in zip(rows, raw, java)]}


def validate_collection(directory, weights_path, prior):
    directory = Path(directory)
    meta = json.loads((directory / "meta.json").read_text(encoding="utf-8"))
    games = json.loads((directory / "games.json").read_text(encoding="utf-8"))
    if (meta.get("schema") != "BTKNEURALRL4" or "temperature" in meta
            or any(meta.get(name + "Temperature") != value for name, value in CONFIG["temperatures"].items())
            or meta.get("teacherTemperature") != CONFIG["teacherTemperature"]):
        raise ValueError("Collection schema or sampling temperature differs from fixed objective")
    weight_hash = sha256(weights_path)
    if meta.get("weightsSha256") != weight_hash or meta.get("studentWeightsSha256") != weight_hash:
        raise ValueError("Collection weights hash differs from supplied behavior policy")
    if meta.get("idxFields") != ["game", "turn", "k", "rowStart", "rowCount", "labelOffset"]:
        raise ValueError("Collection index field order mismatch")
    if meta.get("games") != len(games):
        raise ValueError("Collection game count mismatch")
    frozen = json.loads((ROOT / "docs/evidence/neural-v1-training.json").read_text(encoding="utf-8"))["dataMeta"]
    for name in HEADS:
        if meta.get(name + "Features") != frozen[name + "Features"]:
            raise ValueError(f"{name}: feature order differs from frozen v1")
        expected_teacher_hash = hashlib.sha256(prior[name]["base64"].encode("ascii")).hexdigest()
        if meta.get("teacher" + name.title() + "BlobSha256") != expected_teacher_hash:
            raise ValueError(f"{name}: frozen teacher blob hash mismatch")
    expected = {f"{name}.{extension}" for name in HEADS for extension in ("bin", "idx", "logp", "teacher")} | {"games.json"}
    if set(meta.get("checksums", {})) != expected:
        raise ValueError("Collection checksums must cover all expected data files")
    hashes = {}
    for name, expected_hash in meta["checksums"].items():
        hashes[name] = sha256(directory / name)
        if hashes[name] != expected_hash:
            raise ValueError(f"Collection checksum mismatch: {name}")
    hashes["meta.json"] = sha256(directory / "meta.json")
    return meta, games, hashes


def require_new_outputs(args):
    outputs = [args.out_weights, args.out_checkpoint]
    if args.command == "train":
        outputs.append(args.evidence)
    resolved = [Path(p).resolve() for p in outputs]
    if len(set(resolved)) != len(resolved):
        raise ValueError("Outputs must have distinct paths")
    for path in resolved:
        if path.exists():
            raise ValueError(f"Refusing to overwrite existing experiment output: {path}")
    if args.java_out and Path(args.java_out).resolve() in resolved:
        raise ValueError("Java source cannot overlap other outputs")


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = parser.add_subparsers(dest="command", required=True)
    init = sub.add_parser("init", help="Clone exact shipped v1 bytes and initialize optimizer")
    init.add_argument("--seed", type=int, default=20261005)
    train = sub.add_parser("train", help="Run one fixed four-epoch update on one on-policy collection")
    for p in (init, train):
        p.add_argument("--out-weights", required=True)
        p.add_argument("--out-checkpoint", required=True)
        p.add_argument("--java-out")
    train.add_argument("--checkpoint", required=True)
    train.add_argument("--weights", required=True, help="Exact JSON used by this collection")
    train.add_argument("--data", required=True)
    train.add_argument("--evidence", required=True)
    args = parser.parse_args(argv)
    require_new_outputs(args)
    started = time.monotonic()
    if args.command == "init":
        prior = read_shipped_weights()
        models = {name: decode(prior[name]) for name in HEADS}
        rng, rounds = np.random.default_rng(args.seed), []
        weights = prior
        summary = "neural-v4 bootstrap: exact neural-v1 imitation weights; no outcome updates yet"
    else:
        models, prior, rng, rounds = load_checkpoint(args.checkpoint)
        if prior != read_shipped_weights():
            raise ValueError("Frozen shipped v1 prior changed since initialization")
        weights = json.loads(Path(args.weights).read_text(encoding="utf-8"))
        if any(weights[name] != export_head(models[name]) for name in HEADS):
            raise ValueError("Collection weights differ from checkpoint export")
        directory = Path(args.data)
        meta, games, data_hashes = validate_collection(directory, args.weights, prior)
        if any(r["dataSha256"] == data_hashes for r in rounds):
            raise ValueError("This checkpoint already trained on the supplied collection")
        advantages, cohorts = advantages_for_games(games)
        evidence = {"schemaVersion": 1, "config": CONFIG, "data": str(directory),
                    "inputWeightsSha256": sha256(args.weights), "inputCheckpointSha256": sha256(args.checkpoint),
                    "inputDataSha256": data_hashes,
                    "round": len(rounds) + 1, "games": len(games), "cohorts": cohorts,
                    "advantageMin": min(advantages.values()), "advantageMax": max(advantages.values()), "heads": {}}
        for name in HEADS:
            head = DataHead(directory, name, meta, advantages)
            model = models[name]
            if len(head.features) != model.sizes[0]:
                raise ValueError(f"{name}: feature count mismatch")
            prior_model = decode(prior[name])
            if not np.array_equal(model.mean, prior_model.mean) or not np.array_equal(model.inv_std, prior_model.inv_std):
                raise ValueError("Normalization changed")
            validation = check_data_policy(head, decode(weights[name]), prior_model)
            rebase = rebase_to_export(model, weights[name])
            history = train_head(model, head, rng)
            weights[name] = export_head(model)
            evidence["heads"][name] = {"decisions": len(head.idx), "rows": len(head.x),
                                        "gamesWithDecisions": head.game_count,
                                        "decisionsPerGameMin": head.game_decisions_min, "decisionsPerGameMax": head.game_decisions_max,
                                        "validation": validation, "rebase": rebase,
                                        "history": history, "quantization": parity_cases(model, weights[name], head)}
        rounds.append({"dataSha256": evidence["inputDataSha256"], "games": len(games)})
        summary = f"neural-v4 outcome fine tuning: {len(rounds)} rounds, {sum(r['games'] for r in rounds)} games; fixed v1 prior KL 0.02"
        evidence["summary"] = summary
        evidence["seconds"] = time.monotonic() - started
    write_json(args.out_weights, weights)
    save_checkpoint(args.out_checkpoint, models, prior, rng, rounds)
    if args.java_out:
        write_java(args.java_out, weights, summary)
    if args.command == "train":
        evidence["outputWeightsSha256"] = sha256(args.out_weights)
        evidence["outputCheckpointSha256"] = sha256(args.out_checkpoint)
        if args.java_out:
            evidence["outputJavaSha256"] = sha256(args.java_out)
        write_json(args.evidence, evidence)
    print(summary, flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
