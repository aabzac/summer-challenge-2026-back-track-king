"""Audit completed neural-v2 training without training, exporting weights, or playing games.

Run only after training-complete.json exists. All inputs are read-only. The three
outputs (durable evidence and training/probe benchmark exclusions) are write-once.
Defaults address the predeclared local neural-v2 run; no stage selection is offered.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import json
import math
import os
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
HEADS = ("sabotage", "build")
OPPONENTS = ("v09", "neural-v1", "v07")
MAP_SCHEME = "sha256-utf8-Protocol.initBlock(initialState,0)"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def read(path):
    return json.loads(Path(path).read_bytes())


def sha(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def relative(path):
    return Path(path).resolve().relative_to(ROOT).as_posix()


def pin(path):
    return {"path": relative(path), "sha256": sha(path)}


def write_new(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as stream:
        stream.write(json.dumps(value, indent=2, allow_nan=False) + "\n")


def check_schedule(games, seed, count):
    require(len(games) == count, "Wrong collection game count")
    maps, seats = Counter(), Counter()
    for index, game in enumerate(games):
        require(game["id"] == index and game["seed"] == seed + index // 2
                and game["studentSeat"] == index % 2
                and game["opponent"] == OPPONENTS[(index // 2) % 3], "Map/seat/opponent schedule differs")
        require(type(game["turns"]) is int and 0 < game["turns"] <= 100, "Invalid completed turn count")
        difference = game["scoreStudent"] - game["scoreOpponent"]
        require(game["outcome"] == (difference > 0) - (difference < 0), "Outcome differs from final scores")
        require(re.fullmatch("[0-9a-f]{64}", game["mapHash"]) is not None, "Invalid map identity")
        if index % 2:
            require(game["mapHash"] == games[index - 1]["mapHash"], "Two seats have different map content")
        else:
            require(game["mapHash"] not in maps, "Map repeated outside its pair")
        maps[game["mapHash"]] += 1
        seats[(game["opponent"], game["studentSeat"])] += 1
    require(len(maps) == count // 2 and set(maps.values()) == {2}, "Wrong unique paired map count")
    require(set(seats.values()) == {count // 6}, "Unbalanced opponent seats")
    return maps


def check_model(model, teacher, expected_step, np, trainer):
    reference = trainer.decode(teacher)
    require(model.step == expected_step, "Optimizer step count differs from fixed four-epoch schedule")
    require(model.sizes == reference.sizes and model.initial_blob == reference.initial_blob,
            "Checkpoint architecture/original teacher changed")
    require(np.array_equal(model.mean, reference.mean) and np.array_equal(model.inv_std, reference.inv_std),
            "Checkpoint normalization changed")
    require(all(np.isfinite(p).all() for p in model.params + model.m + model.v), "Non-finite checkpoint tensor")
    require(all((v >= 0).all() for v in model.v), "Negative Adam second moment")
    if expected_step == 0:
        require(all(np.array_equal(p, q) for p, q in zip(model.params, reference.params)), "Bootstrap weights differ")
        require(all(np.count_nonzero(p) == 0 for p in model.m + model.v), "Bootstrap optimizer is not empty")


def check_head(directory, name, meta, games, advantages, behavior, teacher, np, trainer):
    head = trainer.DataHead(directory, name, meta, advantages)
    try:
        require(len(head.idx) == meta[name + "Decisions"] and len(head.x) == meta[name + "Rows"],
                name + ": metadata counts differ from actual binary records")
        game_ids, turns, placement = head.idx[:, 0], head.idx[:, 1], head.idx[:, 2]
        require((game_ids >= 0).all() and (game_ids < len(games)).all(), "Unknown game in candidate index")
        max_turns = np.asarray([g["turns"] for g in games])[game_ids]
        require((turns >= 0).all() and (turns < max_turns).all(), "Candidate turn outside completed episode")
        require((placement >= 0).all() and (placement <= (0 if name == "sabotage" else 2)).all(),
                "Invalid decision sequence index")
        validation = trainer.check_data_policy(head, trainer.decode(behavior), trainer.decode(teacher))
        return {"decisions": len(head.idx), "rows": len(head.x), "gamesWithDecisions": head.game_count,
                "decisionsPerGameMin": head.game_decisions_min, "decisionsPerGameMax": head.game_decisions_max,
                "validation": validation}
    finally:
        head.x._mmap.close()
        head.teacher._mmap.close()


def check_corpus(path):
    corpus = read(path)
    require(corpus["hashScheme"] == MAP_SCHEME, "Corpus map hash scheme differs from local benchmark")
    maps = Counter()
    for item in corpus["replays"]:
        replay = Path(item["path"]).resolve()
        require(replay.is_relative_to(ROOT) and sha(replay) == item["sha256"], "Corpus replay changed")
        require(re.fullmatch("[0-9a-f]{64}", item["mapHash"]) is not None, "Invalid corpus map hash")
        maps[item["mapHash"]] += 1
    require(dict(maps) == corpus["gamesPerMap"], "Corpus map tally differs from pinned replay records")
    return maps, {**pin(path), "replays": len(corpus["replays"]), "distinctMaps": len(maps),
                  "mapDerivation": "NeuralV2Verify reconstructed canonical replays through shared core; replay byte pins rechecked"}


def check_probe(run, prior, np, trainer):
    dataset, weights_path = run / "probe", run / "bootstrap.json"
    weights = read(weights_path)
    require(weights == prior, "Probe was not collected from exact original v1 weights")
    meta, games, hashes = trainer.validate_collection(dataset, weights_path, prior)
    require(meta["seed"] == 2209000 and meta["opponentFirstBudgetMs"] == 486
            and meta["opponentTurnBudgetMs"] == 36, "Probe fixed plan changed")
    maps = check_schedule(games, 2209000, 6)
    advantages, cohorts = trainer.advantages_for_games(games)
    python_path, java_path = run / "probe-python.json", run / "probe-java.json"
    summary_path = run / "probe-parity-summary.json"
    python, java, saved = read(python_path), read(java_path), read(summary_path)
    require(python["dataSha256"] == hashes and python["games"] == 6 and python["cohorts"] == cohorts,
            "Saved Python probe identity/cohorts differ")
    require(java["weightsSha256"] == sha(weights_path) and java["compiledGeneratedWeightsMatch"] is True
            and java["selection"] == "first32DecisionsPerHead/allCandidates", "Saved Java probe identity differs")
    require(java["dataSha256"] == {key: value for key, value in hashes.items() if key.endswith((".bin", ".idx"))},
            "Saved Java probe dataset differs")
    heads, scores_checked, case_index = {}, 0, 0
    for name in HEADS:
        heads[name] = check_head(dataset, name, meta, games, advantages, weights[name], prior[name], np, trainer)
        require(all(heads[name][key] == python["heads"][name][key]
                    for key in ("decisions", "rows", "decisionsPerGameMin", "decisionsPerGameMax")),
                "Saved Python probe counts differ")
        head = trainer.DataHead(dataset, name, meta, advantages)
        try:
            model = trainer.decode(weights[name])
            for group in head.idx[:32]:
                require(case_index < len(java["cases"]), "Java probe missing candidate decision")
                case = java["cases"][case_index]
                game, turn, k, start, count, _ = map(int, group)
                require(all(case[key] == value for key, value in
                            (("head", name), ("game", game), ("turn", turn), ("k", k),
                             ("rowStart", start), ("count", count))), "Java probe candidate index differs")
                values = trainer.java_scores(model, np.asarray(head.x[start:start + count]))
                recorded = np.asarray(case["scores"], dtype=np.float32)
                require(np.array_equal(values.view(np.uint32), recorded.view(np.uint32)), "Java/Python score bits differ")
                require(int(np.argmax(values)) == case["argmax"], "Java/Python selected argmax differs")
                scores_checked += count
                case_index += 1
        finally:
            head.x._mmap.close()
            head.teacher._mmap.close()
    require(case_index == len(java["cases"]) == 64, "Wrong Java probe case count")
    expected = {"cases": case_index, "candidateScores": scores_checked, "bitExactScores": True,
                "allArgmaxMatch": True, "javaEvidenceSha256": sha(java_path)}
    require(saved == expected, "Saved probe parity summary differs from independently recomputed scores")
    return maps, {"games": 6, "distinctMaps": 3, "purpose": "fixed plumbing probe; excluded from strength and training",
                  "cohorts": cohorts, "heads": heads, "dataSha256": hashes,
                  "javaPythonParity": expected,
                  "inputs": [pin(weights_path), pin(python_path), pin(java_path), pin(summary_path)]}


def audit(run, corpus_path):
    complete_path, plan_path = run / "training-complete.json", run / "training-plan.json"
    require(complete_path.is_file(), "Training is incomplete; audit never starts/resumes it")
    complete, plan = read(complete_path), read(plan_path)
    require(complete["complete"] is True and complete["planSha256"] == sha(plan_path), "Completion plan pin differs")
    require(plan["experiment"] == "neural-v2" and plan["seed"] == 20261005
            and plan["rounds"] == 8 and plan["gamesPerRound"] == 192
            and plan["totalGames"] == 1536 and plan["distinctMaps"] == 768
            and plan["mapSeedBases"] == [2200000 + i * 1000 for i in range(8)], "Fixed training plan changed")
    for name, expected in plan["sourceSha256"].items():
        require(sha(ROOT / name) == expected, "Pinned source changed: " + name)
    for variable in ("OPENBLAS_NUM_THREADS", "OMP_NUM_THREADS", "MKL_NUM_THREADS"):
        os.environ[variable] = "1"
    import numpy as np
    import train_outcome_v2 as trainer
    require(plan["config"] == trainer.CONFIG, "Trainer hyperparameters differ from plan")
    prior = trainer.read_shipped_weights()
    weights_path, checkpoint_path = run / "weights-0.json", run / "checkpoint-0.npz"
    models, checkpoint_prior, _, lineage = trainer.load_checkpoint(checkpoint_path)
    require(read(weights_path) == prior == checkpoint_prior and lineage == [], "Invalid original bootstrap")
    previous_rng = read_checkpoint_rng(checkpoint_path, np)
    require(previous_rng == np.random.default_rng(plan["seed"]).bit_generator.state, "Bootstrap RNG seed differs")
    optimizer_steps = dict.fromkeys(HEADS, 0)
    for name in HEADS:
        check_model(models[name], prior[name], 0, np, trainer)
    bootstrap = {"weights": pin(weights_path), "checkpoint": pin(checkpoint_path), "exactOriginalBlobs": True}
    corpus_maps, corpus_summary = check_corpus(corpus_path)
    probe_maps, probe_summary = check_probe(run, prior, np, trainer)
    require(not (set(corpus_maps) & set(probe_maps)), "Probe overlaps teacher/boss/process corpus")
    all_maps, rounds, expected_lineage = Counter(), [], []
    for stage in range(1, 9):
        dataset = run / f"round-{stage}"
        evidence_path = run / f"train-{stage}.json"
        next_weights, next_checkpoint = run / f"weights-{stage}.json", run / f"checkpoint-{stage}.npz"
        evidence = read(evidence_path)
        meta, games, hashes = trainer.validate_collection(dataset, weights_path, prior)
        require(meta["seed"] == plan["mapSeedBases"][stage - 1]
                and meta["studentBudgetMs"] == 60000 and meta["opponentFirstBudgetMs"] == 486
                and meta["opponentTurnBudgetMs"] == 36, "Collection timing/seed differs from fixed plan")
        maps = check_schedule(games, plan["mapSeedBases"][stage - 1], 192)
        require(not (set(maps) & (set(all_maps) | set(corpus_maps) | set(probe_maps))), "Training maps overlap prior data")
        all_maps.update(maps)
        require(evidence["round"] == stage and evidence["games"] == 192 and evidence["config"] == plan["config"]
                and evidence["inputDataSha256"] == hashes, "Training evidence data/settings differ")
        for key, path in (("inputWeightsSha256", weights_path), ("inputCheckpointSha256", checkpoint_path),
                          ("outputWeightsSha256", next_weights), ("outputCheckpointSha256", next_checkpoint)):
            require(evidence[key] == sha(path), "Training lineage pin differs: " + key)
        models, checkpoint_prior, _, lineage = trainer.load_checkpoint(next_checkpoint)
        expected_lineage.append({"dataSha256": hashes, "games": 192})
        require(checkpoint_prior == prior and lineage == expected_lineage, "Checkpoint data ancestry differs")
        advantages, cohorts = trainer.advantages_for_games(games)
        require(cohorts == evidence["cohorts"], "Recorded cohort outcomes differ from actual games")
        exported, behavior, heads = read(next_weights), read(weights_path), {}
        expected_rng = np.random.default_rng()
        expected_rng.bit_generator.state = previous_rng
        for name in HEADS:
            actual = check_head(dataset, name, meta, games, advantages, behavior[name], prior[name], np, trainer)
            recorded = evidence["heads"][name]
            require(all(actual[key] == recorded[key] for key in
                        ("decisions", "rows", "gamesWithDecisions", "decisionsPerGameMin", "decisionsPerGameMax")),
                    "Training evidence counts differ from actual candidate records")
            require(recorded["rebase"]["parametersExactlyEqualDecodedBehavior"] is True
                    and recorded["rebase"]["optimizerStepRetained"] == optimizer_steps[name], "Invalid quantized round rebase")
            steps_per_epoch = math.ceil(actual["decisions"] / plan["config"]["batch"])
            require(len(recorded["history"]) == 4, "Wrong number of training epochs")
            for epoch, entry in enumerate(recorded["history"], 1):
                require(entry["epoch"] == epoch and entry["optimizerStep"] == optimizer_steps[name] + epoch * steps_per_epoch,
                        "Recorded optimizer updates differ from fixed epoch schedule")
                require(all(math.isfinite(value) for value in entry.values()), "Non-finite training metric")
                expected_rng.permutation(actual["decisions"])
            optimizer_steps[name] += 4 * steps_per_epoch
            check_model(models[name], prior[name], optimizer_steps[name], np, trainer)
            require(trainer.export_head(models[name]) == exported[name], "Final exported weights differ from checkpoint")
            heads[name] = {**actual, "rebase": recorded["rebase"], "history": recorded["history"],
                           "quantization": {key: value for key, value in recorded["quantization"].items() if key != "cases"},
                           "checkpointOptimizerStep": models[name].step}
        previous_rng = read_checkpoint_rng(next_checkpoint, np)
        require(previous_rng == expected_rng.bit_generator.state, "Checkpoint RNG differs from exact minibatch permutation schedule")
        rounds.append({"round": stage, "games": len(games), "distinctMaps": len(maps),
                       "mapSeedBase": meta["seed"], "cohorts": cohorts, "heads": heads,
                       "inputDataSha256": hashes, "evidence": pin(evidence_path),
                       "inputWeights": pin(weights_path), "inputCheckpoint": pin(checkpoint_path),
                       "outputWeights": pin(next_weights), "outputCheckpoint": pin(next_checkpoint)})
        weights_path, checkpoint_path = next_weights, next_checkpoint
        print(f"Verified neural-v2 round {stage}/8: 192 games / 96 paired maps", flush=True)
    require(len(all_maps) == 768 and set(all_maps.values()) == {2} and dict(all_maps) == complete["gamesPerMap"],
            "Complete training map registry differs from actual game records")
    require(complete["games"] == 1536 and complete["distinctMaps"] == 768
            and (ROOT / complete["weights"]).resolve() == weights_path.resolve() and complete["weightsSha256"] == sha(weights_path)
            and complete["checkpointSha256"] == sha(checkpoint_path), "Completion artifact identity differs")
    # Detect edits during the audit as well as at entry.
    for name, expected in plan["sourceSha256"].items():
        require(sha(ROOT / name) == expected, "Pinned source changed during audit: " + name)
    result = {"schemaVersion": 1, "experiment": "neural-v2", "complete": True, "auditTool": pin(__file__),
              "plan": pin(plan_path), "completion": pin(complete_path), "sourceSha256": plan["sourceSha256"],
              "config": plan["config"], "seed": plan["seed"], "games": 1536, "distinctMaps": 768,
              "mapHashScheme": MAP_SCHEME, "trainingSeats": {"P0": 768, "P1": 768},
              "bootstrap": bootstrap, "finalWeights": pin(weights_path), "finalCheckpoint": pin(checkpoint_path),
              "optimizerSteps": optimizer_steps, "checkpointLineageVerified": True, "rngScheduleVerified": True,
              "corpus": corpus_summary, "probe": probe_summary, "mapOverlaps": {"trainingCorpus": 0, "trainingProbe": 0, "probeCorpus": 0},
              "rounds": rounds, "selection": "fixed final round; no checkpoint selection or outcome-guided extension",
              "strength": "not assessed here; collection outcomes are training diagnostics"}
    return result, all_maps, probe_maps


def read_checkpoint_rng(path, np):
    with np.load(path, allow_pickle=False) as checkpoint:
        return json.loads(str(checkpoint["metadata"]))["rng"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run", type=Path, default=ROOT / "build/analysis/neural-v2")
    parser.add_argument("--corpus", type=Path, default=ROOT / "build/bench/neural-v2-corpus-excluded.json")
    parser.add_argument("--out", type=Path, default=ROOT / "docs/evidence/neural-v2-training.json")
    args = parser.parse_args()
    training_out, probe_out = (ROOT / "build/bench" / f"neural-v2-{name}-excluded.json" for name in ("training", "probe"))
    outputs = (args.out.resolve(), training_out, probe_out)
    require(len(set(outputs)) == 3 and all(not path.exists() for path in outputs), "Preserve existing audit/exclusion outputs")
    evidence, training, probe = audit(args.run.resolve(), args.corpus.resolve())
    for path, maps, purpose in ((training_out, training, "training"), (probe_out, probe, "fixed numerical probe")):
        write_new(path, {"schemaVersion": 1, "purpose": "neural-v2 " + purpose + " maps excluded from strength games",
                         "hashScheme": MAP_SCHEME, "planSha256": evidence["plan"]["sha256"],
                         "gamesPerMap": dict(sorted(maps.items()))})
    evidence["benchmarkExclusions"] = [pin(training_out), pin(probe_out), pin(args.corpus)]
    write_new(args.out, evidence)
    print(f"Audit passed: 1536 training games / 768 maps; wrote {args.out}", flush=True)


if __name__ == "__main__":
    main()
