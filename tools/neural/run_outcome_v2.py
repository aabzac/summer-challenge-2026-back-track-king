"""Run the predeclared neural-v2 schedule. No checkpoint selection or strength games.

An explicit --resume verifies completed stages of the same source-pinned plan.
Partial collections/updates are preserved and stop the driver instead of retrying.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/analysis/neural-v2"
TRAINER = "tools/neural/train_outcome_v2.py"
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
    base = 2200000 + (stage - 1) * 1000
    require(meta["schema"] == "BTKNEURALRL1" and meta["games"] == GAMES and len(games) == GAMES,
            "Collection schema/count mismatch")
    require(meta["seed"] == base and meta["temperature"] == 1 and meta["weightsSha256"] == sha(weights),
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
    import train_outcome_v2 as trainer
    require(result["config"] == trainer.CONFIG, "Training objective changed")
    models, prior, rng, rounds = trainer.load_checkpoint(checkpoint)
    require(prior == trainer.read_shipped_weights(), "Teacher prior changed")
    require(len(rounds) == stage and all(r["games"] == GAMES for r in rounds), "Checkpoint rounds mismatch")
    exported = read(weights)
    for name in trainer.HEADS:
        require(trainer.export_head(models[name]) == exported[name], "Checkpoint/export mismatch")
    return True


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--resume", action="store_true")
    args = parser.parse_args()
    OUT.mkdir(parents=True, exist_ok=True)
    import train_outcome_v2 as trainer
    require(trainer.CONFIG["lr"] == .00003 and trainer.CONFIG["epochs"] == 4
            and trainer.CONFIG["batch"] == 64 and trainer.CONFIG["clip"] == .2
            and trainer.CONFIG["kl"] == .02 and trainer.CONFIG["gradNorm"] == 1,
            "Trainer differs from predeclared hyperparameters")
    sources = [TRAINER, "tools/neural/train_neural.py", "tools/neural/run_outcome_v2.py",
               "src/main/java/btk/tools/NeuralV2Collect.java",
               "src/main/java/btk/bot/neural/NeuralV2Bot.java",
               "src/main/java/btk/bot/neural/NeuralBot.java",
               "src/main/java/btk/bot/neural/NeuralFeatures.java",
               "src/main/java/btk/bot/neural/NeuralNet.java",
               "src/main/java/btk/bot/neural/NeuralWeights.java",
               "src/main/java/btk/bot/BotFactory.java",
               "src/main/java/btk/bot/FrozenParams.java",
               "src/main/java/btk/bot/Params.java",
               "src/main/java/btk/bot/Timer.java",
               "src/main/java/btk/runner/TimingProfile.java",
               "src/main/resources/btk/bot/versions/v07.properties",
               "src/main/resources/btk/bot/versions/v09.properties",
               "src/main/resources/btk/bot/versions/neural-v1.properties",
               "build.gradle.kts",
               "docs/evidence/neural-v1-training.json"]
    sources += [p.relative_to(ROOT).as_posix() for folder in ("core", "referee", "bot/v07", "bot/v09")
                for p in sorted((ROOT / "src/main/java/btk" / folder).glob("*.java"))]
    plan = {"schemaVersion": 1, "experiment": "neural-v2", "seed": SEED,
            "config": trainer.CONFIG, "rounds": ROUNDS, "gamesPerRound": GAMES,
            "totalGames": ROUNDS * GAMES, "distinctMaps": ROUNDS * GAMES // 2,
            "mapSeedBases": [2200000 + i * 1000 for i in range(ROUNDS)],
            "sourceSha256": {p: sha(ROOT / p) for p in sources},
            "strengthGate": "practical-v4 vs v09; 2300000/2400000/2500000; two workers"}
    plan_path = OUT / "training-plan.json"
    if plan_path.exists():
        require(args.resume and read(plan_path) == plan, "Resume requires identical recorded plan and source pins")
    else:
        require(not args.resume, "No plan to resume")
        write_new(plan_path, plan)
    weights, checkpoint = OUT / "weights-0.json", OUT / "checkpoint-0.npz"
    if weights.exists() or checkpoint.exists():
        require(weights.exists() and checkpoint.exists(), "Incomplete bootstrap")
        models, prior, rng, rounds = trainer.load_checkpoint(checkpoint)
        require(not rounds and all(m.step == 0 for m in models.values())
                and read(weights) == trainer.read_shipped_weights() == prior, "Bootstrap is not exact unchanged v1")
    else:
        run("init", [sys.executable, TRAINER, "init", "--seed", str(SEED),
                     "--out-weights", str(weights), "--out-checkpoint", str(checkpoint)])
    all_games = []
    for stage in range(1, ROUNDS + 1):
        dataset = OUT / f"round-{stage}"
        if not verify_collection(dataset, weights, stage):
            run(f"collect-{stage}", [str(ROOT / "gradlew.bat"), "neuralV2Collect", "--console=plain",
                f"-Pgames={GAMES}", f"-Pseed={2200000 + (stage - 1) * 1000}",
                f"-Pweights={weights}", f"-Pout={dataset}", "-Ptemp=1"])
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
