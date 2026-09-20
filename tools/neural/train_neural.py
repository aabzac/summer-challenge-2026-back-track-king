"""Train the neural-v1 imitation policy (numpy only) and emit btk.bot.neural.NeuralWeights.

  .\\gradlew.bat cgNeuralExport                       # -> build/analysis/neural-data
  python tools/neural/train_neural.py [--data build/analysis/neural-data] [--epochs 40]
                                      [--out src/main/java/btk/bot/neural/NeuralWeights.java]
                                      [--evidence docs/evidence/neural-v1-training.json]

Two conditional-logit scorers (softmax over the candidate rows of one decision, cross-entropy on
the chosen row): a sabotage head over legal DISRUPT regions and a build head over affordable
placement cells. Hidden layers are ReLU; input standardisation is folded into the first layer at
export; weights ship as int16 per-layer-scaled base64 blobs (the Java side is NeuralNet). Games
flagged `holdout` by the exporter are never trained on and are evaluated with their full candidate
sets; the training games use the exporter's sampled negatives.
"""
import argparse
import base64
import datetime
import json
import os
import struct
import sys
import time

import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
IDX_FIELDS = ("game", "turn", "k", "rowStart", "rowCount", "labelOffset", "holdout")
HEADS = {  # name -> (rows file, index file, hidden layers, meta feature key)
    "sabotage": ("sabotage.bin", "sabotage.idx", (32, 16), "sabotageFeatures"),
    "build": ("build.bin", "build.idx", (48, 24), "buildFeatures"),
}
JAVA_CHUNK = 30000  # keep every string literal far below the 65535-byte class-file limit


# ---------------------------------------------------------------- data

class Head:
    def __init__(self, data_dir, name, meta):
        rows_file, idx_file, self.hidden, feature_key = HEADS[name]
        self.name = name
        self.features = list(meta[feature_key])
        f = len(self.features)
        self.x = np.fromfile(os.path.join(data_dir, rows_file), dtype=np.float32).reshape(-1, f)
        idx = np.fromfile(os.path.join(data_dir, idx_file), dtype=np.int32).reshape(-1, len(IDX_FIELDS))
        self.idx = idx
        train = idx[idx[:, 6] == 0]
        hold = idx[idx[:, 6] == 1]
        self.train_groups = train
        self.holdout_groups = hold
        rows = np.concatenate([np.arange(s, s + c) for s, c in zip(train[:, 3], train[:, 4])])
        self.mean = self.x[rows].mean(axis=0)
        self.std = np.maximum(self.x[rows].std(axis=0), 1e-3)
        if not np.all(np.isfinite(self.x)):
            raise SystemExit(f"{name}: non-finite feature values in export")

    def batches(self, groups, batch, rng, standardise=True):
        order = rng.permutation(len(groups)) if rng is not None else np.arange(len(groups))
        for i in range(0, len(order), batch):
            yield self.gather(groups[order[i:i + batch]], standardise)

    def gather(self, groups, standardise=True):
        """Padded candidate tensor [B, Cmax, F], mask [B, Cmax], labels [B]."""
        counts = groups[:, 4]
        cmax = int(counts.max())
        b = len(groups)
        index = np.zeros((b, cmax), dtype=np.int64)
        mask = np.zeros((b, cmax), dtype=bool)
        for i, (start, count) in enumerate(zip(groups[:, 3], counts)):
            index[i, :count] = np.arange(start, start + count)
            mask[i, :count] = True
        x = self.x[index]
        if standardise:
            x = (x - self.mean) / self.std
        x[~mask] = 0
        return x.astype(np.float32), mask, groups[:, 5].astype(np.int64)


# ---------------------------------------------------------------- model

class Mlp:
    def __init__(self, sizes, rng):
        self.sizes = list(sizes)
        self.w, self.b = [], []
        for i, o in zip(sizes[:-1], sizes[1:]):
            self.w.append((rng.standard_normal((i, o)) * np.sqrt(2.0 / i)).astype(np.float32))
            self.b.append(np.zeros(o, dtype=np.float32))
        self.params = self.w + self.b
        self.m = [np.zeros_like(p) for p in self.params]
        self.v = [np.zeros_like(p) for p in self.params]
        self.step = 0

    def forward(self, x):
        acts = [x]
        h = x
        for l, (w, b) in enumerate(zip(self.w, self.b)):
            z = h @ w + b
            h = z if l == len(self.w) - 1 else np.maximum(z, 0)
            acts.append(h)
        return acts

    def loss_and_grads(self, x, mask, labels):
        acts = self.forward(x)
        scores = acts[-1][..., 0]
        scores = np.where(mask, scores, -1e9)
        scores = scores - scores.max(axis=1, keepdims=True)
        p = np.exp(scores) * mask
        p /= p.sum(axis=1, keepdims=True)
        b = np.arange(len(labels))
        nll = -np.log(np.maximum(p[b, labels], 1e-12)).mean()
        top1 = float((scores.argmax(axis=1) == labels).mean())
        d = p.copy()
        d[b, labels] -= 1
        d /= len(labels)
        d = d[..., None] * mask[..., None]  # dL/dscore, [B,C,1]
        grads_w, grads_b = [None] * len(self.w), [None] * len(self.w)
        for l in range(len(self.w) - 1, -1, -1):
            a = acts[l]
            grads_w[l] = np.einsum("bci,bco->io", a, d)
            grads_b[l] = d.sum(axis=(0, 1))
            if l > 0:
                d = (d @ self.w[l].T) * (acts[l] > 0)
        return nll, top1, grads_w + grads_b

    def adam(self, grads, lr, weight_decay=1e-5, beta1=0.9, beta2=0.999, eps=1e-8):
        self.step += 1
        for i, (p, g) in enumerate(zip(self.params, grads)):
            if p.ndim == 2:
                g = g + weight_decay * p
            self.m[i] = beta1 * self.m[i] + (1 - beta1) * g
            self.v[i] = beta2 * self.v[i] + (1 - beta2) * g * g
            mh = self.m[i] / (1 - beta1 ** self.step)
            vh = self.v[i] / (1 - beta2 ** self.step)
            p -= (lr * mh / (np.sqrt(vh) + eps)).astype(np.float32)

    def scores(self, x, mask):
        s = self.forward(x)[-1][..., 0]
        return np.where(mask, s, -1e9)


def evaluate(model, head, groups, batch=256, standardise=True):
    nll, top1, top3, n = 0.0, 0, 0, 0
    for x, mask, labels in head.batches(groups, batch, None, standardise):
        s = model.scores(x, mask)
        s = s - s.max(axis=1, keepdims=True)
        p = np.exp(s) * mask
        p /= p.sum(axis=1, keepdims=True)
        b = np.arange(len(labels))
        nll += -np.log(np.maximum(p[b, labels], 1e-12)).sum()
        order = np.argsort(-s, axis=1)
        top1 += int((order[:, 0] == labels).sum())
        top3 += int((order[:, :3] == labels[:, None]).any(axis=1).sum())
        n += len(labels)
    uniform = float(np.mean(1.0 / groups[:, 4]))
    return {"decisions": int(n), "top1": top1 / n, "top3": top3 / n, "nll": float(nll / n),
            "uniformTop1": uniform, "candidatesMean": float(groups[:, 4].mean())}


def evaluate_per_k(model, head, groups, standardise=True):
    out = {}
    for k in sorted(set(groups[:, 2].tolist())):
        sub = groups[groups[:, 2] == k]
        out[str(k)] = evaluate(model, head, sub, standardise=standardise)
    return out


# ---------------------------------------------------------------- export

def quantize(mean, std, w_list, b_list):
    """Blob = float32 mean[F], float32 invStd[F], then per layer: float32 scale, int16 W[out][in], int16 b[out].

    Standardisation is shipped explicitly (not folded into layer 0): folding spreads the first-layer
    weights over the feature scales (1e-3 .. 1e3) and int16 quantisation then destroys the small ones.
    """
    blob = bytearray()
    blob += mean.astype("<f4").tobytes()
    blob += (1.0 / std).astype("<f4").tobytes()
    for w, b in zip(w_list, b_list):
        scale = float(max(np.abs(w).max(), np.abs(b).max(), 1e-12)) / 32767.0
        blob += struct.pack("<f", scale)
        wq = np.clip(np.rint(w.T / scale), -32767, 32767).astype("<i2")  # Java expects [out][in]
        bq = np.clip(np.rint(b / scale), -32767, 32767).astype("<i2")
        blob += wq.tobytes()
        blob += bq.tobytes()
    return bytes(blob)


def dequantize_model(model, blob):
    """Re-read the blob exactly as NeuralNet does, for a quantisation check in numpy."""
    class Q:
        pass
    q = Q()
    q.w, q.b, q.sizes = [], [], model.sizes
    f = model.sizes[0]
    q.mean = np.frombuffer(blob, dtype="<f4", count=f, offset=0).astype(np.float32)
    q.inv_std = np.frombuffer(blob, dtype="<f4", count=f, offset=4 * f).astype(np.float32)
    off = 8 * f
    for i, o in zip(model.sizes[:-1], model.sizes[1:]):
        scale = struct.unpack_from("<f", blob, off)[0]
        off += 4
        wq = np.frombuffer(blob, dtype="<i2", count=o * i, offset=off).reshape(o, i)
        off += 2 * o * i
        bq = np.frombuffer(blob, dtype="<i2", count=o, offset=off)
        off += 2 * o
        q.w.append((wq.T * scale).astype(np.float32))
        q.b.append((bq * scale).astype(np.float32))
    assert off == len(blob)
    q.forward = lambda x: Mlp.forward(q, (x - q.mean) * q.inv_std)
    q.scores = lambda x, mask: Mlp.scores(q, x, mask)
    return q


def java_string(name, text):
    chunks = [text[i:i + JAVA_CHUNK] for i in range(0, len(text), JAVA_CHUNK)] or [""]
    if len(chunks) == 1:
        return f'    public static final String {name} = "{chunks[0]}";\n'
    parts = ",\n".join(f'            "{c}"' for c in chunks)
    return f"    public static final String {name} = String.join(\"\",\n{parts});\n"


def write_java(path, heads, summary):
    lines = ["package btk.bot.neural;", "",
             "/**", " * GENERATED by tools/neural/train_neural.py; do not edit by hand.",
             f" * {summary}", " */",
             "public final class NeuralWeights {", "    private NeuralWeights() {}", "",
             f'    public static final String TRAINING = "{summary}";']
    for name, item in heads.items():
        sizes = ", ".join(str(s) for s in item["sizes"][1:])
        const = "SABOTAGE_F" if name == "sabotage" else "BUILD_F"
        lines.append(f"    public static final int[] {name.upper()}_SIZES = {{NeuralFeatures.{const}, {sizes}}};")
        lines.append(java_string(name.upper(), item["base64"]).rstrip("\n"))
    lines.append("}")
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")


# ---------------------------------------------------------------- main

def train_head(head, args, rng, log):
    sizes = [len(head.features), *head.hidden, 1]
    model = Mlp(sizes, rng)
    linear = Mlp([len(head.features), 1], rng)
    steps_per_epoch = (len(head.train_groups) + args.batch - 1) // args.batch
    total = steps_per_epoch * args.epochs
    history = []
    t0 = time.time()
    for epoch in range(args.epochs):
        ep_nll, ep_top1, ep_n = 0.0, 0.0, 0
        for x, mask, labels in head.batches(head.train_groups, args.batch, rng):
            progress = model.step / max(1, total)
            lr = args.lr * 0.5 * (1 + np.cos(np.pi * progress))
            nll, top1, grads = model.loss_and_grads(x, mask, labels)
            model.adam(grads, lr, args.weight_decay)
            lnll, _, lgrads = linear.loss_and_grads(x, mask, labels)
            linear.adam(lgrads, lr, args.weight_decay)
            ep_nll += nll * len(labels)
            ep_top1 += top1 * len(labels)
            ep_n += len(labels)
        hold = evaluate(model, head, head.holdout_groups)
        history.append({"epoch": epoch + 1, "trainNll": float(ep_nll / ep_n), "trainTop1": float(ep_top1 / ep_n),
                        "holdoutNll": hold["nll"], "holdoutTop1": hold["top1"]})
        log(f"  {head.name} epoch {epoch + 1:3d} train nll {ep_nll / ep_n:.4f} top1 {ep_top1 / ep_n:.3f}"
            f" | holdout nll {hold['nll']:.4f} top1 {hold['top1']:.3f} top3 {hold['top3']:.3f} ({time.time() - t0:.0f}s)")
    blob = quantize(head.mean, head.std, model.w, model.b)
    # The quantised network reads raw feature rows exactly like the Java NeuralNet.
    quant = dequantize_model(model, blob)
    result = {
        "sizes": sizes,
        "parameters": int(sum(p.size for p in model.params)),
        "trainDecisions": int(len(head.train_groups)),
        "holdout": evaluate(model, head, head.holdout_groups),
        "holdoutLinearBaseline": evaluate(linear, head, head.holdout_groups),
        "holdoutQuantizedFolded": evaluate(quant, head, head.holdout_groups, standardise=False),
        "trainSampledTop1": history[-1]["trainTop1"],
        "history": history,
        "base64": base64.b64encode(blob).decode("ascii"),
        "blobBytes": len(blob),
    }
    if head.name == "build":
        result["holdoutPerPlacementIndex"] = evaluate_per_k(quant, head, head.holdout_groups, standardise=False)
    return result


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--data", default=os.path.join(ROOT, "build", "analysis", "neural-data"))
    ap.add_argument("--out", default=os.path.join(ROOT, "src", "main", "java", "btk", "bot", "neural", "NeuralWeights.java"))
    ap.add_argument("--evidence", default=os.path.join(ROOT, "docs", "evidence", "neural-v1-training.json"))
    ap.add_argument("--epochs", type=int, default=40)
    ap.add_argument("--batch", type=int, default=512)
    ap.add_argument("--lr", type=float, default=2e-3)
    ap.add_argument("--weight-decay", type=float, default=1e-5)
    ap.add_argument("--seed", type=int, default=20260920)
    ap.add_argument("--heads", default="sabotage,build")
    ap.add_argument("--no-java", action="store_true", help="evaluate only; do not rewrite NeuralWeights.java")
    args = ap.parse_args()

    meta = json.load(open(os.path.join(args.data, "meta.json"), encoding="utf-8"))
    rng = np.random.default_rng(args.seed)
    log = lambda s: print(s, flush=True)
    results = {}
    for name in args.heads.split(","):
        head = Head(args.data, name, meta)
        log(f"{name}: {len(head.train_groups)} train / {len(head.holdout_groups)} holdout decisions, "
            f"{len(head.features)} features, {head.x.shape[0]} rows")
        results[name] = train_head(head, args, rng, log)
        results[name]["features"] = head.features
        h = results[name]["holdout"]
        log(f"{name}: holdout top1 {h['top1']:.3f} (uniform {h['uniformTop1']:.3f}, linear "
            f"{results[name]['holdoutLinearBaseline']['top1']:.3f}, quantised {results[name]['holdoutQuantizedFolded']['top1']:.3f}),"
            f" {results[name]['parameters']} parameters, {len(results[name]['base64'])} base64 chars")

    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%MZ")
    players = sorted({g["player"] for g in meta["gameList"]})
    summary = (f"trained {stamp} on {meta['games']} games of {'/'.join(players)} ({meta['holdoutGames']} holdout); "
               + "; ".join(f"{n} holdout top1 {r['holdoutQuantizedFolded']['top1']:.3f}" for n, r in results.items()))
    evidence = {
        "schemaVersion": 1, "trainedAt": stamp, "data": os.path.relpath(args.data, ROOT).replace("\\", "/"),
        "dataMeta": {k: v for k, v in meta.items() if k != "gameList"},
        "players": players, "args": {k: v for k, v in vars(args).items() if k not in ("data", "out", "evidence")},
        "heads": {n: {k: v for k, v in r.items() if k != "base64"} for n, r in results.items()},
        "summary": summary,
    }
    os.makedirs(os.path.dirname(args.evidence), exist_ok=True)
    with open(args.evidence, "w", encoding="utf-8") as f:
        json.dump(evidence, f, indent=2)
    log(f"evidence -> {args.evidence}")
    if not args.no_java:
        if set(results) != {"sabotage", "build"}:
            raise SystemExit("Java export needs both heads")
        write_java(args.out, results, summary)
        log(f"weights -> {args.out} ({sum(len(r['base64']) for r in results.values())} base64 chars)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
