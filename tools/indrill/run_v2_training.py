"""Run the fixed indrill-q-v2 training schedule, without strength testing or tuning.

All successful stages are immutable. --resume resumes the same recorded plan after an
environment interruption, verifying completed stages instead of replaying games/updates.
The separate completed-training audit is still required before strength evaluation.
"""
from __future__ import annotations

import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/analysis/indrill-q-v2"
OLD = [f"build/analysis/indrill-q/round-{i}.bin.gz" for i in range(1, 5)]
EPSILON = [.30, .25, .20, .15, .12, .10]
SEED = 20261004


def sha(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def run(label, args):
    log = OUT / f"{label}.log"
    if log.exists():
        # Retain incomplete attempt output as well; only an explicit resume reaches here.
        attempt = 2
        while log.with_name(f"{label}-environment-attempt-{attempt}.log").exists():
            attempt += 1
        log = log.with_name(f"{label}-environment-attempt-{attempt}.log")
    print(f"STAGE {label}", flush=True)
    with log.open("x", encoding="utf-8") as capture:
        proc = subprocess.Popen(args, cwd=ROOT, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, encoding="utf-8", errors="replace")
        for line in proc.stdout:
            capture.write(line)
            capture.flush()
            if any(tag in line for tag in ("Collected", "step", "Saved", "BUILD", "Error", "Exception", "Traceback")):
                print(line.rstrip(), flush=True)
        code = proc.wait()
    if code:
        raise SystemExit(f"Stage {label} failed ({code}); preserve its output and inspect {log}")


def verified_output(binary, evidence, data, *, stage=None, previous=None):
    if not binary.exists() and not evidence.exists():
        return False
    if not binary.exists() or not evidence.exists() or not binary.with_suffix(".npz").exists():
        raise SystemExit(f"Incomplete stage artifact: {binary}; no automatic training retry")
    meta = json.loads(evidence.read_bytes())
    if meta["weightsSha256"] != sha(binary):
        raise SystemExit(f"Frozen weights changed: {binary}")
    expected = {sha(ROOT / item) for item in data}
    actual = {item["sha256"] for item in meta.get("data", [])}
    if expected != actual:
        raise SystemExit(f"Completed stage has a different dataset: {evidence}")
    settings = meta["settings"]
    if settings["seed"] != SEED:
        raise SystemExit(f"Completed stage has a different seed: {evidence}")
    if stage is None:
        if not meta["initOnly"] or meta["games"] != 0 or meta["updates"] != 0:
            raise SystemExit(f"Initialization artifact is not an unused random model: {evidence}")
    else:
        if (meta["initOnly"] or meta["warmStartV1"] or meta["games"] != 192 + 128 * stage
                or meta["updates"] != 2000 * (stage + 1)):
            raise SystemExit(f"Completed stage has the wrong training counts: {evidence}")
        for key, value in {"steps": 2000, "batch": 32, "target": 100, "gamma": .99,
                           "nstep": 3, "alpha": .6, "beta": .4, "lr": .0003,
                           "huber": 1., "clip": 10., "float32_replay": False}.items():
            if settings[key] != value:
                raise SystemExit(f"Completed stage changed {key}: {evidence}")
        if (meta["inputWeights"]["sha256"] != sha(previous)
                or meta["inputCheckpoint"]["sha256"] != sha(previous.with_suffix(".npz"))):
            raise SystemExit(f"Completed stage has a different predecessor: {evidence}")
    # Verify every inference tensor against its checkpoint and inspect optimizer/target
    # state too. Counts alone cannot authenticate an accidentally replaced checkpoint.
    import numpy as np
    import train_q_v2 as model_api
    with binary.open("rb") as stream:
        tensors = model_api.read_weight_tensors(stream)
    with np.load(binary.with_suffix(".npz"), allow_pickle=False) as saved:
        if int(saved["schemaVersion"]) != 2 or int(saved["updates"]) != meta["updates"]:
            raise SystemExit(f"Checkpoint metadata disagrees: {binary}")
        for name, shape in model_api.PARAM_SHAPES.items():
            if (saved[name].shape != shape or saved[name].dtype != np.dtype("float32")
                    or not np.array_equal(saved[name].view(np.uint32), tensors[name].view(np.uint32))):
                raise SystemExit(f"Checkpoint inference tensor differs: {name}")
            for prefix in ("adam_m_", "adam_v_", "target_"):
                value = saved[prefix + name]
                if value.shape != shape or not np.isfinite(value).all():
                    raise SystemExit(f"Invalid checkpoint {prefix + name}")
                if prefix == "adam_v_" and (value < 0).any():
                    raise SystemExit("Invalid negative Adam second moment")
        keys, priorities = saved["replay_keys"], saved["replay_priorities"]
        if keys.shape != priorities.shape or len(keys) != meta["transitions"]:
            raise SystemExit("Replay checkpoint dimensions disagree with retained transitions")
        if not np.isfinite(priorities).all() or (priorities <= 0).any():
            raise SystemExit("Invalid checkpoint priorities")
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resume", action="store_true")
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    sources = ["tools/indrill/train_q.py", "tools/indrill/train_q_v2.py",
               "src/main/java/btk/tools/IndrillV2Collect.java",
               "src/main/java/btk/bot/current/IndrillV2Net.java",
               "src/main/java/btk/bot/current/IndrillV2Bot.java",
               "src/main/java/btk/bot/current/IndrillFeatures.java",
               "src/main/java/btk/core/InkProjection.java"]
    plan = {"schemaVersion": 1, "experiment": "indrill-q-v2", "seed": SEED,
            "bootstrap": [{"path": p, "sha256": sha(ROOT / p)} for p in OLD],
            "blocks": [{"games": 128, "seed": 2100000 + i * 1000, "epsilon": e}
                       for i, e in enumerate(EPSILON)],
            "updatesPerStage": 2000, "batch": 32, "totalGames": 960,
            "totalUpdates": 14000, "sampledVisits": 448000,
            "sourceSha256": {p: sha(ROOT / p) for p in sources},
            "strengthGate": "separate practical-v4 vs v09, seed 1500000, two workers"}
    plan_path = OUT / "training-plan.json"
    if plan_path.exists():
        if not args.resume or json.loads(plan_path.read_bytes()) != plan:
            raise SystemExit("Existing training plan: only an explicit resume of exactly the same plan is allowed")
    else:
        if args.resume:
            raise SystemExit("No recorded plan to resume")
        plan_path.write_text(json.dumps(plan, indent=2) + "\n", encoding="utf-8")
    train = [sys.executable, "tools/indrill/train_q_v2.py"]
    initial = OUT / "weights-init.bin"
    init_meta = OUT / "init.json"
    if not verified_output(initial, init_meta, []):
        run("initialize", train + ["--init-only", "--seed", str(SEED), "--weights-out", str(initial),
                                    "--evidence", str(init_meta)])
    data = list(OLD)
    previous = initial
    for stage in range(7):
        if stage:
            dataset = OUT / f"round-{stage}.bin.gz"
            sidecar = Path(str(dataset) + ".json")
            if dataset.exists() or sidecar.exists():
                if not dataset.exists() or not sidecar.exists():
                    raise SystemExit(f"Incomplete collection output {dataset}; no automatic game retries")
                meta = json.loads(sidecar.read_bytes())
                if meta["datasetSha256"] != sha(dataset) or meta["weightsSha256"] != sha(previous):
                    raise SystemExit("Completed collection differs from frozen data/model")
                if (meta["games"] != 128 or meta["seed"] != 2100000 + (stage - 1) * 1000
                        or meta["epsilon"] != EPSILON[stage - 1] or meta["opponent"] != "mixed"
                        or len(meta["results"]) != 128):
                    raise SystemExit("Completed collection differs from the fixed schedule")
                if sorted(row["seed"] for row in meta["results"]) != list(range(meta["seed"], meta["seed"] + 128)):
                    raise SystemExit("Completed collection has different or repeated game seeds")
            else:
                run(f"collect-{stage}", [str(ROOT / "gradlew.bat"), "indrillV2Collect", "--console=plain",
                    "-Pgames=128", f"-Pseed={2100000 + (stage - 1) * 1000}",
                    f"-Pweights={previous}", f"-Pout={dataset}", f"-Pepsilon={EPSILON[stage - 1]}", "-Popponent=mixed"])
            data.append(dataset.relative_to(ROOT).as_posix())
        weights = OUT / f"weights-{stage}.bin"
        evidence = OUT / f"train-{stage}.json"
        if not verified_output(weights, evidence, data, stage=stage, previous=previous):
            run(f"train-{stage}", train + ["--data", *data, "--weights-in", str(previous),
                "--weights-out", str(weights), "--evidence", str(evidence), "--seed", str(SEED),
                "--steps", "2000", "--batch", "32"])
        previous = weights
        print(f"COMPLETE stage={stage} weights={sha(previous)}", flush=True)
    result = {"complete": True, "planSha256": sha(plan_path), "weightsSha256": sha(previous),
              "weights": previous.relative_to(ROOT).as_posix(), "games": 960, "updates": 14000,
              "trainingEvidence": f"build/analysis/indrill-q-v2/train-6.json"}
    completion = OUT / "training-complete.json"
    if completion.exists():
        if json.loads(completion.read_bytes()) != result:
            raise SystemExit("Existing completion marker differs from the verified fixed schedule")
    else:
        with completion.open("x", encoding="utf-8") as stream:
            stream.write(json.dumps(result, indent=2) + "\n")
    print("FIXED TRAINING COMPLETE; freeze weights before the independent strength gate", flush=True)


if __name__ == "__main__":
    main()
