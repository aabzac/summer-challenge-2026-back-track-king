"""Run the predeclared neural-v4 schedule. No checkpoint selection or strength games.

An explicit --resume verifies completed stages of the same source-pinned plan.
Partial collections/updates are preserved and stop the driver instead of retrying.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import math
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/analysis/neural-v4"
TRAINER = "tools/neural/train_outcome_v4.py"
SEED = 20261005
ROUNDS = 8
GAMES = 192


def sha(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read(path):
    return json.loads(Path(path).read_bytes())


def write_new(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as stream:
        stream.write(json.dumps(value, indent=2) + "\n")


def require(ok, message):
    if not ok:
        raise ValueError(message)


def run(label, args):
    log = OUT / f"{label}.log"
    require(not log.exists(), f"Previous attempt retained: {log}; no implicit retry")
    env = dict(os.environ, OPENBLAS_NUM_THREADS="1", OMP_NUM_THREADS="1", MKL_NUM_THREADS="1")
    print(f"STAGE {label}", flush=True)
    with log.open("x", encoding="utf-8") as capture:
        proc = subprocess.Popen(args, cwd=ROOT, env=env, stdout=subprocess.PIPE,
                                stderr=subprocess.STDOUT, text=True, encoding="utf-8", errors="replace")
        for line in proc.stdout:
            capture.write(line)
            capture.flush()
            if any(tag in line for tag in ("Collected", "epoch", "fine tuning", "bootstrap", "Saved", "FAIL", "Error", "Exception", "Traceback")):
                print(line.rstrip(), flush=True)
        require(proc.wait() == 0, f"Failed {label}; inspect retained {log}")


def verify_collection(directory, weights, stage):
    if not directory.exists():
        return False
    require((directory / "meta.json").exists(), f"Incomplete collection: {directory}")
    meta, games = read(directory / "meta.json"), read(directory / "games.json")
    base = 3100000 + (stage - 1) * 1000
    require(meta["schema"] == "BTKNEURALRL4" and meta["games"] == GAMES and len(games) == GAMES,
            "Collection schema/count mismatch")
    require(meta["seed"] == base and "temperature" not in meta
            and meta["sabotageTemperature"] == 1 and meta["buildTemperature"] == .5
            and meta["teacherTemperature"] == 1 and meta["weightsSha256"] == sha(weights),
            "Collection plan or behavior weights mismatch")
    require(meta["opponentFirstBudgetMs"] == 486 and meta["opponentTurnBudgetMs"] == 36,
            "Collection opponent budget mismatch")
    for name, digest in meta["checksums"].items():
        require(sha(directory / name) == digest, f"Changed dataset {directory / name}")
    seen = {}
    for i, game in enumerate(games):
        opponent = ("v09", "neural-v1", "v07")[(i // 2) % 3]
        require(game["id"] == i and game["seed"] == base + i // 2 and game["studentSeat"] == i % 2
                and game["opponent"] == opponent, "Collection map/seat/opponent schedule mismatch")
        diff = game["scoreStudent"] - game["scoreOpponent"]
        require(game["outcome"] == (diff > 0) - (diff < 0), "Collection outcome mismatch")
        require(len(game["mapHash"]) == 64, "Invalid map hash")
        if i % 2:
            require(game["mapHash"] == games[i - 1]["mapHash"], "Pair has different maps")
        else:
            require(game["mapHash"] not in seen, "Repeated map in training block")
            seen[game["mapHash"]] = game["seed"]
    return True


def verify_update(stage, previous_weights, previous_checkpoint, weights, checkpoint, evidence, dataset):
    existing = [p.exists() for p in (weights, checkpoint, evidence)]
    if not any(existing):
        return False
    require(all(existing), f"Incomplete optimizer output at stage {stage}")
    result = read(evidence)
    require(result["round"] == stage and result["games"] == GAMES, "Wrong completed training round")
    require(result["inputWeightsSha256"] == sha(previous_weights)
            and result["inputCheckpointSha256"] == sha(previous_checkpoint), "Wrong training predecessor")
    require(result["outputWeightsSha256"] == sha(weights)
            and result["outputCheckpointSha256"] == sha(checkpoint), "Changed training artifact")
    for name, digest in result["inputDataSha256"].items():
        require(sha(dataset / name) == digest, "Training data changed after update")
    import train_outcome_v4 as trainer
    require(result["config"] == trainer.CONFIG, "Training objective changed")
    original_prior = trainer.read_shipped_weights()
    _, _, hashes = trainer.validate_collection(dataset, previous_weights, original_prior)
    require(result["inputDataSha256"] == hashes, "Training data pin set differs from complete collection")
    previous_models, previous_prior, expected_rng, previous_rounds = trainer.load_checkpoint(previous_checkpoint)
    models, prior, rng, rounds = trainer.load_checkpoint(checkpoint)
    require(prior == previous_prior == original_prior, "Teacher prior changed")
    require(len(rounds) == stage and rounds == previous_rounds + [{"dataSha256": hashes, "games": GAMES}],
            "Checkpoint rounds mismatch")
    exported = read(weights)
    for name in trainer.HEADS:
        model, previous = models[name], previous_models[name]
        reference = trainer.decode(original_prior[name])
        require(trainer.export_head(model) == exported[name], "Checkpoint/export mismatch")
        require(model.sizes == reference.sizes and model.initial_blob == reference.initial_blob
                and trainer.np.array_equal(model.mean, reference.mean)
                and trainer.np.array_equal(model.inv_std, reference.inv_std), "Checkpoint frozen state changed")
        require(all(trainer.np.isfinite(p).all() for p in model.params + model.m + model.v)
                and all((v >= 0).all() for v in model.v), "Invalid checkpoint tensor")
        decisions = len(trainer.np.fromfile(dataset / (name + ".idx"), dtype="<i4").reshape(-1, 6))
        recorded = result["heads"][name]
        require(recorded["decisions"] == decisions and decisions > 0, "Checkpoint decision count mismatch")
        require(recorded["rebase"]["parametersExactlyEqualDecodedBehavior"] is True
                and recorded["rebase"]["optimizerStepRetained"] == previous.step, "Checkpoint rebase mismatch")
        steps = math.ceil(decisions / trainer.CONFIG["batch"])
        require(len(recorded["history"]) == trainer.CONFIG["epochs"], "Wrong update epoch count")
        for epoch, metrics in enumerate(recorded["history"], 1):
            require(metrics["epoch"] == epoch and metrics["optimizerStep"] == previous.step + epoch * steps,
                    "Checkpoint optimizer schedule mismatch")
            expected_rng.permutation(decisions)
        require(model.step == previous.step + trainer.CONFIG["epochs"] * steps,
                "Checkpoint optimizer step mismatch")
    require(rng.bit_generator.state == expected_rng.bit_generator.state, "Checkpoint RNG continuation changed")
    return True


def verify_plan(path, expected, resume):
    """No implicit retry, source change or newly added production file on resume."""
    if path.exists():
        require(resume and read(path) == expected, "Resume requires identical recorded plan and source pins")
    else:
        require(not resume, "No plan to resume")
        write_new(path, expected)


def verify_bootstrap(weights, checkpoint, trainer):
    require(weights.exists() and checkpoint.exists(), "Incomplete bootstrap")
    models, prior, rng, rounds = trainer.load_checkpoint(checkpoint)
    require(not rounds and read(weights) == trainer.read_shipped_weights() == prior,
            "Bootstrap is not exact unchanged v1")
    require(rng.bit_generator.state == trainer.np.random.default_rng(SEED).bit_generator.state,
            "Bootstrap RNG seed/state changed")
    for name, model in models.items():
        reference = trainer.decode(prior[name])
        require(model.step == 0 and model.sizes == reference.sizes
                and model.initial_blob == reference.initial_blob
                and trainer.np.array_equal(model.mean, reference.mean)
                and trainer.np.array_equal(model.inv_std, reference.inv_std)
                and all(trainer.np.array_equal(a, b) for a, b in zip(model.params, reference.params))
                and all(trainer.np.count_nonzero(p) == 0 for p in model.m + model.v),
                "Bootstrap model/optimizer differs from exact unchanged v1")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resume", action="store_true")
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    import train_outcome_v4 as trainer
    require(trainer.CONFIG["lr"] == .00003 and trainer.CONFIG["epochs"] == 4
            and trainer.CONFIG["batch"] == 64 and trainer.CONFIG["clip"] == .2
            and trainer.CONFIG["kl"] == .02 and trainer.CONFIG["gradNorm"] == 1
            and trainer.CONFIG["temperatures"] == {"sabotage": 1., "build": .5}
            and trainer.CONFIG["teacherTemperature"] == 1.,
            "Trainer differs from predeclared hyperparameters")
    sources = [TRAINER, "tools/neural/train_neural.py", "tools/neural/run_outcome_v4.py",
               "tools/neural/audit_outcome_v4.py", "build.gradle.kts", "docs/evidence/neural-v1-training.json"]
    # Pin every production dependency, including newly added files. The final generated
    # weight class is an output, never used by the collector's JSON policy loader.
    sources += [p.relative_to(ROOT).as_posix() for p in sorted((ROOT / "src/main/java").rglob("*.java"))
                if p.name != "NeuralV4Weights.java"]
    sources += [p.relative_to(ROOT).as_posix() for p in sorted((ROOT / "src/main/resources").rglob("*")) if p.is_file()]
    plan = {"schemaVersion": 1, "experiment": "neural-v4", "seed": SEED,
            "config": trainer.CONFIG, "rounds": ROUNDS, "gamesPerRound": GAMES,
            "totalGames": ROUNDS * GAMES, "distinctMaps": ROUNDS * GAMES // 2,
            "mapSeedBases": [3100000 + i * 1000 for i in range(ROUNDS)],
            "sourceSha256": {p: sha(ROOT / p) for p in sources},
            "strengthGate": "practical-v4 vs neural-v3; 3200000/3300000/3400000; two workers"}
    plan_path = OUT / "training-plan.json"
    verify_plan(plan_path, plan, args.resume)
    weights, checkpoint = OUT / "weights-0.json", OUT / "checkpoint-0.npz"
    if weights.exists() or checkpoint.exists():
        verify_bootstrap(weights, checkpoint, trainer)
    else:
        run("init", [sys.executable, TRAINER, "init", "--seed", str(SEED),
                     "--out-weights", str(weights), "--out-checkpoint", str(checkpoint)])
        verify_bootstrap(weights, checkpoint, trainer)
    all_games = []
    for stage in range(1, ROUNDS + 1):
        dataset = OUT / f"round-{stage}"
        if not verify_collection(dataset, weights, stage):
            run(f"collect-{stage}", [str(ROOT / "gradlew.bat"), "neuralV4Collect", "--console=plain",
                f"-Pgames={GAMES}", f"-Pseed={3100000 + (stage - 1) * 1000}",
                f"-Pweights={weights}", f"-Pout={dataset}", "-PsabotageTemp=1", "-PbuildTemp=0.5"])
            verify_collection(dataset, weights, stage)
        next_weights, next_checkpoint = OUT / f"weights-{stage}.json", OUT / f"checkpoint-{stage}.npz"
        evidence = OUT / f"train-{stage}.json"
        if not verify_update(stage, weights, checkpoint, next_weights, next_checkpoint, evidence, dataset):
            run(f"train-{stage}", [sys.executable, TRAINER, "train", "--data", str(dataset),
                "--weights", str(weights), "--checkpoint", str(checkpoint),
                "--out-weights", str(next_weights), "--out-checkpoint", str(next_checkpoint),
                "--evidence", str(evidence)])
            verify_update(stage, weights, checkpoint, next_weights, next_checkpoint, evidence, dataset)
        weights, checkpoint = next_weights, next_checkpoint
        all_games.extend(read(dataset / "games.json"))
        print(f"COMPLETE round={stage}/{ROUNDS}; weights={sha(weights)}", flush=True)
    maps = Counter(g["mapHash"] for g in all_games)
    require(len(maps) == 768 and set(maps.values()) == {2}, "Training rounds overlap full map identities")
    result = {"complete": True, "planSha256": sha(plan_path), "games": len(all_games),
              "distinctMaps": len(maps), "weights": str(weights.relative_to(ROOT)), "weightsSha256": sha(weights),
              "checkpointSha256": sha(checkpoint), "gamesPerMap": maps}
    marker = OUT / "training-complete.json"
    if marker.exists():
        require(read(marker) == result, "Completion marker changed")
    else:
        write_new(marker, result)
    print("FIXED TRAINING COMPLETE; final checkpoint only", flush=True)


if __name__ == "__main__":
    main()
