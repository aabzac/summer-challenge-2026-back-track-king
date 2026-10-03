"""Read-only exploration diagnostics after the complete fixed neural-v2 schedule.

python tools/neural/exploration_v2.py

Reads the saved sampling decisions, never runs games or updates a model. All
decisions within a head receive equal weight. These stochastic training outcomes
are not a measurement of the deployed greedy policy's strength.
"""
import argparse
import json
from pathlib import Path

import numpy as np

import train_outcome_v2 as trainer


def outcomes(games):
    if not games:
        raise ValueError("Empty game group")
    return {"games": len(games),
            "wins": sum(game["outcome"] == 1 for game in games),
            "draws": sum(game["outcome"] == 0 for game in games),
            "losses": sum(game["outcome"] == -1 for game in games),
            "averageTurns": sum(game["turns"] for game in games) / len(games)}


def head_diagnostics(directory, name):
    index = np.fromfile(directory / f"{name}.idx", dtype="<i4").reshape(-1, 6)
    logp = np.fromfile(directory / f"{name}.logp", dtype="<f4")
    teacher = np.memmap(directory / f"{name}.teacher", dtype="<f4", mode="r")
    if len(index) != len(logp) or not len(index):
        raise ValueError(f"{name}: invalid decision count")
    starts, counts, labels = index[:, 3], index[:, 4], index[:, 5]
    if starts[0] != 0 or not np.array_equal(starts[1:], (starts + counts)[:-1]):
        raise ValueError(f"{name}: noncontiguous candidate rows")
    if np.any(counts < 2) or np.any(labels < 0) or np.any(labels >= counts) or starts[-1] + counts[-1] != len(teacher):
        raise ValueError(f"{name}: invalid candidate count or label")
    if not np.isfinite(logp).all() or np.any(logp > 1e-6):
        raise ValueError(f"{name}: invalid chosen behavior log probabilities")
    teacher_argmax_count, teacher_max_probability_sum = 0, 0.
    try:
        for start in range(0, len(index), 512):
            group = index[start:start + 512]
            positions = np.arange(int(group[:, 4].max()))[None, :]
            mask = positions < group[:, 4, None]
            offsets = group[:, 3, None] + np.where(mask, positions, 0)
            logits = np.asarray(teacher[offsets], dtype=np.float64)
            if not np.isfinite(logits[mask]).all():
                raise ValueError(f"{name}: non-finite teacher logit")
            logits[~mask] = -np.inf
            maxima = logits.max(axis=1)
            chosen = logits[np.arange(len(group)), group[:, 5]]
            teacher_argmax_count += int(np.sum(chosen == maxima))
            teacher_max_probability_sum += float(np.sum(1. / np.exp(logits - maxima[:, None]).sum(axis=1)))
    finally:
        teacher._mmap.close()
    behavior_probability_sum = float(np.exp(logp.astype(np.float64)).sum())
    return summarize_head({"decisions": len(index), "candidateRows": int(counts.sum()),
                           "chosenFrozenTeacherArgmaxCount": teacher_argmax_count,
                           "chosenBehaviorProbabilitySum": behavior_probability_sum,
                           "frozenTeacherMaxProbabilitySum": teacher_max_probability_sum})


def summarize_head(result):
    decisions = result["decisions"]
    return {**result,
            "chosenFrozenTeacherArgmaxFraction": result["chosenFrozenTeacherArgmaxCount"] / decisions,
            "meanChosenBehaviorProbability": result["chosenBehaviorProbabilitySum"] / decisions,
            "meanFrozenTeacherMaxSoftmaxProbability": result["frozenTeacherMaxProbabilitySum"] / decisions,
            "meanCandidates": result["candidateRows"] / decisions}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--data", type=Path, default=trainer.ROOT / "build/analysis/neural-v2")
    parser.add_argument("--out", type=Path, default=trainer.ROOT / "docs/evidence/neural-v2-exploration.json")
    args = parser.parse_args(argv)
    if args.out.exists():
        raise ValueError(f"Refusing to overwrite diagnostic evidence: {args.out}")
    marker_path = args.data / "training-complete.json"
    marker = json.loads(marker_path.read_text(encoding="utf-8"))
    if marker.get("complete") is not True or marker.get("games") != 1536 or marker.get("distinctMaps") != 768:
        raise ValueError("Requires the complete fixed eight-round training run")
    plan_path = args.data / "training-plan.json"
    plan = json.loads(plan_path.read_text(encoding="utf-8"))
    if marker["planSha256"] != trainer.sha256(plan_path) or plan["rounds"] != 8 or plan["gamesPerRound"] != 192:
        raise ValueError("Completion marker differs from fixed plan")
    result = {"schemaVersion": 1, "experiment": "neural-v2", "samplingTemperature": 1,
              "definitions": {
                  "population": "All selectable student decisions from all eight complete training rounds; forced and singleton decisions omitted by collector.",
                  "outcomes": "Win/draw/loss from student seat, terminal outcome +1/0/-1; averageTurns is arithmetic mean of saved game turns.",
                  "chosenFrozenTeacherArgmaxFraction": "Fraction of recorded decisions where the chosen candidate's stored frozen-v1 logit equals the maximum over the same full candidate set; exact float32 ties count as agreement.",
                  "meanChosenBehaviorProbability": "Arithmetic mean over decisions of exp(saved chosen-action behavior logp), using the actual quantized policy sampled that round at temperature 1.",
                  "meanFrozenTeacherMaxSoftmaxProbability": "Arithmetic mean over decisions of max softmax(stored frozen-v1 candidate logits / 1); describes prior concentration on visited states.",
                  "aggregation": "Uniform decision weighting per head. Pooled fractions/probabilities use summed numerators divided by summed decision counts; game outcomes/turns use uniform game weighting.",
                  "interpretation": "Diagnostics of stochastic collection on training maps. They do not establish strength or improvement of deployed greedy policies and are not a strength gate."},
              "inputSha256": {"training-complete.json": trainer.sha256(marker_path),
                                "training-plan.json": trainer.sha256(plan_path)},
              "toolSha256": trainer.sha256(Path(__file__)), "rounds": []}
    prior, all_games = trainer.read_shipped_weights(), []
    sums = {name: {key: 0 for key in ("decisions", "candidateRows", "chosenFrozenTeacherArgmaxCount",
                                     "chosenBehaviorProbabilitySum", "frozenTeacherMaxProbabilitySum")} for name in trainer.HEADS}
    for stage in range(1, 9):
        directory = args.data / f"round-{stage}"
        weights = args.data / f"weights-{stage - 1}.json"
        meta, games, hashes = trainer.validate_collection(directory, weights, prior)
        if len(games) != 192:
            raise ValueError(f"Round {stage} has an unexpected game count")
        training_evidence_path = args.data / f"train-{stage}.json"
        training_evidence = json.loads(training_evidence_path.read_text(encoding="utf-8"))
        if training_evidence["inputDataSha256"] != hashes or training_evidence["inputWeightsSha256"] != trainer.sha256(weights):
            raise ValueError(f"Round {stage} data differs from the actual training input")
        stage_result = {"round": stage, "allOpponents": outcomes(games),
                        "byOpponent": {opponent: outcomes([g for g in games if g["opponent"] == opponent])
                                       for opponent in sorted({g["opponent"] for g in games})},
                        "heads": {name: head_diagnostics(directory, name) for name in trainer.HEADS},
                        "inputSha256": {**{f"round-{stage}/{name}": digest for name, digest in hashes.items()},
                                          weights.name: trainer.sha256(weights),
                                          training_evidence_path.name: trainer.sha256(training_evidence_path)}}
        for name in trainer.HEADS:
            if stage_result["heads"][name]["decisions"] != meta[name + "Decisions"] or stage_result["heads"][name]["candidateRows"] != meta[name + "Rows"]:
                raise ValueError(f"Round {stage} {name} metadata count mismatch")
            for key in sums[name]:
                sums[name][key] += stage_result["heads"][name][key]
        all_games.extend(games)
        result["rounds"].append(stage_result)
        print(f"Exploration diagnostics complete for round {stage}/8", flush=True)
    result["pooled"] = {"allOpponents": outcomes(all_games),
                         "byOpponent": {opponent: outcomes([g for g in all_games if g["opponent"] == opponent])
                                        for opponent in sorted({g["opponent"] for g in all_games})},
                         "heads": {name: summarize_head(sums[name]) for name in trainer.HEADS}}
    trainer.write_json(args.out, result)
    print(json.dumps(result["pooled"], indent=2), flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
