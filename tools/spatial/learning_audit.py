"""Describe completed frozen-policy traces; no simulation, gradients or weight writes.

python tools/spatial/learning_audit.py --manifest build/spatial-learning-audit/games/manifest.json \
    --output build/analysis/spatial-learning-audit/report.json --excluded-maps path/to/index.json
"""
from __future__ import annotations

import argparse
import base64
import hashlib
import itertools
import json
import math
from pathlib import Path
import re
import statistics

ROOT = Path(__file__).resolve().parents[2]
LEARNERS = ("spatial-ppo-v2-init", "spatial-ppo-v2", "spatial-ppo-v3", "spatial-ppo-v4")
OPPONENTS = ("v01", "v09")
SEEDS = tuple(range(22000000, 22000004))
BINS = ((0, 25), (25, 50), (50, 75), (75, 100))
MILESTONES = (0, 25, 50, 75, 99)
GAMMA, PPO_SCALE, V5_SCALE, TERMINAL_BONUS = .99, 100., 10000., 30.
WEIGHT_FILES = dict(zip((*LEARNERS, "spatial-ppo-v5"), (
    "SpatialInitialWeights.java", "SpatialV2Weights.java", "SpatialV3Weights.java",
    "SpatialV4Weights.java", "SpatialV5Weights.java")))
TRACE_COLUMNS = ("turn", "gained", "income_after", "score_gap_after", "owned_paths_after",
                 "owned_rails_after", "paint_used", "unused_paint", "stopped_with_paint")


def require(condition, message):
    if not condition:
        raise ValueError(message)


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def read_json(path):
    def invalid_constant(value):
        raise ValueError(f"Nonfinite JSON constant: {value}")
    return json.loads(Path(path).read_text(encoding="utf-8-sig"), parse_constant=invalid_constant)


def finite(value, name):
    require(type(value) in (int, float) and math.isfinite(value), f"Invalid finite number: {name}")
    return float(value)


def integer(value, name, minimum=0):
    require(type(value) is int and value >= minimum, f"Invalid integer: {name}")
    return value


def pair(value, name):
    require(isinstance(value, list) and len(value) == 2, f"Invalid pair: {name}")
    return [integer(x, name) for x in value]


def hash_string(value):
    require(isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value), "Invalid SHA256")
    return value


def frozen_weight_hashes():
    result = {}
    for spec, name in WEIGHT_FILES.items():
        source = (ROOT / "src/main/java/btk/bot/neural" / name).read_text(encoding="utf-8")
        encoded = "".join(re.findall(r'"([A-Za-z0-9+/=]+)"', source))
        raw = base64.b64decode(encoded, validate=True)
        require(len(raw) == 3439 * 4, "Unexpected frozen weight payload length")
        result[spec] = hashlib.sha256(raw).hexdigest()
    return result


def validate_plan(plan):
    require(isinstance(plan, dict) and plan.get("error") is None and plan.get("skipped") == [], "Rejected/skipped recorded plan")
    count, cost = integer(plan["count"], "plan count"), integer(plan["cost"], "plan cost")
    require(count <= 3 and cost <= 3, "Recorded plan exceeds public action/paint bounds")
    for key in ("cells", "paddedCells"):
        require(isinstance(plan[key], list) and len(plan[key]) == count and len(set(plan[key])) == count, "Plan cell/count mismatch")
        for cell in plan[key]:
            integer(cell, key)
    require(isinstance(plan["disrupts"], list) and len(plan["disrupts"]) <= 1, "Unexpected disruption count")
    for region in plan["disrupts"]:
        integer(region, "disruption")


def validate_metrics(metrics):
    pair(metrics["ownedRails"], "owned rails")
    pair(metrics["ownedDesiredPaths"], "owned desired paths")
    integer(metrics["activeDesiredPaths"], "active desired paths")


def validate_game(game):
    """Check trace accounting only. Java's independent replay owns rule fidelity."""
    require(game.get("status") == "complete" and game.get("terminal") is True, "Incomplete game")
    require(game["learnerSpec"] in LEARNERS and game["opponentSpec"] in OPPONENTS, "Unexpected policy")
    seat = integer(game["learnerSeat"], "learner seat")
    require(seat in (0, 1) and game["maxTurns"] == 100, "Unexpected seat/public horizon")
    require(game["seed"] in SEEDS and game["mapIndex"] == game["seed"] - SEEDS[0], "Unexpected fixed map")
    init = game["initBlockP0"]
    require(isinstance(init, str) and hashlib.sha256(init.encode()).hexdigest() == game["mapHash"], "Game map hash mismatch")
    turns = game["turns"]
    require(isinstance(turns, list) and 1 <= len(turns) <= 100 and game["turnCount"] == len(turns), "Invalid complete-game turn count")
    previous = [0, 0]
    previous_hash, previous_income, previous_metrics = None, None, None
    for at, row in enumerate(turns):
        require(row["turn"] == at and pair(row["scoreBefore"], "score before") == previous, "Preturn score/turn mismatch")
        gained, after = pair(row["gained"], "gained"), pair(row["scoreAfter"], "score after")
        require(after == [previous[i] + gained[i] for i in (0, 1)], "Core gained/score accounting mismatch")
        before_income, after_income = pair(row["incomeBefore"], "income before"), pair(row["incomeAfter"], "income after")
        validate_metrics(row["metricsBefore"])
        validate_metrics(row["metricsAfter"])
        if at:
            require(row["preStateSha256"] == previous_hash and before_income == previous_income
                    and row["metricsBefore"] == previous_metrics, "Consecutive recorded states do not join")
        for key in ("preTurnBlockP0", "preTurnBlockP1"):
            require(isinstance(row[key], str) and bool(row[key]), "Missing preturn protocol state")
        expected = hashlib.sha256((init + str(at) + "\n100\n" + row["preTurnBlockP0"]).encode()).hexdigest()
        require(hash_string(row["preStateSha256"]) == expected, "Preturn protocol hash mismatch")
        hash_string(row["postStateSha256"])
        finite(row["learnerValueRaw"], "learner value")
        finite(row["v5ValueRaw"], "v5 value")
        require(finite(row["diagnosticElapsedMs"], "diagnostic elapsed") >= 0, "Negative diagnostic time")
        require(type(row["terminalAfter"]) is bool and row["terminalAfter"] == (at == len(turns) - 1), "Terminal must occur exactly on last recorded transition")
        require(isinstance(row["actions"], list) and len(row["actions"]) == 2
                and all(isinstance(x, str) and x for x in row["actions"]), "Missing recorded actions")
        require(len(row["expanded"]) == 2 and len(row["accepted"]) == 2, "Missing expanded/accepted plans")
        for who in (0, 1):
            validate_plan(row["expanded"][who])
            validate_plan(row["accepted"][who])
            require(row["expanded"][who] == row["accepted"][who], "Requested plan differs from accepted expansion")
        require(row["learnerUnusedPaint"] == 3 - row["accepted"][seat]["cost"]
                and type(row["learnerStoppedWithPaint"]) is bool, "Paint/STOP diagnostics mismatch")
        require(row["learnerStoppedWithPaint"] == (row["learnerUnusedPaint"] > 0), "Unused-paint flag mismatch")
        calls = row["calls"]
        require(len(calls) == 2 and {call["me"] for call in calls} == {0, 1}, "Missing/duplicate player call")
        for call in calls:
            is_learner = call["me"] == seat
            require(call["spec"] == game["learnerSpec" if is_learner else "opponentSpec"]
                    and call["role"] == ("learner" if is_learner else "opponent")
                    and call["budgetMs"] == 60000 and call["complete"] is True and call["legal"] is True
                    and call["actions"] == row["actions"][call["me"]], "Wrong/truncated/illegal recorded policy call")
            require(0 <= finite(call["elapsedMs"], "call elapsed") < 60000, "Call exceeded declared ample budget")
        previous, previous_hash, previous_income, previous_metrics = after, row["postStateSha256"], after_income, row["metricsAfter"]
    require(pair(game["score"], "final score") == previous, "Final score differs from trace")
    return game


def future_targets(game):
    """Monte Carlo targets from recorded gains; no prediction or future baseline leakage."""
    seat, turns = game["learnerSeat"], game["turns"]
    final_gap = game["score"][seat] - game["score"][1 - seat]
    sign = (final_gap > 0) - (final_gap < 0)
    ppo = [0.] * len(turns)
    future = 0.
    for at in range(len(turns) - 1, -1, -1):
        row = turns[at]
        reward = (row["gained"][seat] - row["gained"][1 - seat]
                  + (TERMINAL_BONUS * sign if row["terminalAfter"] else 0.)) / PPO_SCALE
        future = reward + GAMMA * future
        ppo[at] = future
    records = []
    for row, target in zip(turns, ppo, strict=True):
        at = row["turn"]
        pre_gap = row["scoreBefore"][seat] - row["scoreBefore"][1 - seat]
        income_gap = row["incomeBefore"][seat] - row["incomeBefore"][1 - seat]
        public_remaining = game["maxTurns"] - at
        records.append({"turn": at, "ppo_target": target,
                        "v5_target": (final_gap - pre_gap) / V5_SCALE,
                        "ppo_prediction": finite(row["learnerValueRaw"], "learner value"),
                        "v5_prediction": finite(row["v5ValueRaw"], "v5 value"),
                        "ppo_income": income_gap * (1 - GAMMA ** public_remaining) / (1 - GAMMA) / PPO_SCALE,
                        "v5_income": income_gap * public_remaining / V5_SCALE})
    return records


def regression_metrics(clusters):
    """Equal game weights, equal turn weights within each contributing game; no CI."""
    clusters = [(str(name), list(rows)) for name, rows in clusters if rows]
    if not clusters:
        return {"games": 0, "observations": 0, "bias": None, "mae": None, "rmse": None, "pearson": None}
    require(len({name for name, _ in clusters}) == len(clusters), "Duplicate regression game cluster")
    count = len(clusters)
    triples = [(finite(y, "target"), finite(p, "prediction"), 1. / (count * len(rows)))
               for _, rows in clusters for y, p in rows]
    bias = math.fsum(w * (p - y) for y, p, w in triples)
    mae = math.fsum(w * abs(p - y) for y, p, w in triples)
    mse = math.fsum(w * (p - y) ** 2 for y, p, w in triples)
    mean_y = math.fsum(w * y for y, _, w in triples)
    mean_p = math.fsum(w * p for _, p, w in triples)
    ys, ps = [y for y, _, _ in triples], [p for _, p, _ in triples]
    def variable(values):
        return max(values) - min(values) > 1e-12 * max(1., abs(max(values)), abs(min(values)))
    pearson = None
    if variable(ys) and variable(ps):
        yy = math.fsum(w * (y - mean_y) ** 2 for y, _, w in triples)
        pp = math.fsum(w * (p - mean_p) ** 2 for _, p, w in triples)
        yp = math.fsum(w * (y - mean_y) * (p - mean_p) for y, p, w in triples)
        if yy > 0 and pp > 0:
            pearson = max(-1., min(1., yp / math.sqrt(yy * pp)))
    return {"games": count, "observations": len(triples), "bias": bias, "mae": mae,
            "rmse": math.sqrt(mse), "pearson": pearson}


def value_metrics(games_and_targets, start=0, stop=100):
    result = {}
    for contract in ("ppo", "v5"):
        predictions = {}
        for name, suffix in (("model", "prediction"), ("zero", None), ("current_income", "income")):
            clusters = [(game["id"], [(row[contract + "_target"], row[contract + "_" + suffix] if suffix else 0.)
                                     for row in targets if start <= row["turn"] < stop])
                        for game, targets in games_and_targets]
            predictions[name] = regression_metrics(clusters)
        result[contract] = predictions
    return result


def game_behaviour(game, targets):
    seat, turns = game["learnerSeat"], game["turns"]
    gap = game["score"][seat] - game["score"][1 - seat]
    onset = next((row["turn"] + 1 for row in turns if row["gained"][seat] > 0), None)
    income_onset = next((row["turn"] + 1 for row in turns if row["incomeAfter"][seat] > 0), None)
    trace = [[row["turn"], row["gained"][seat], row["incomeAfter"][seat], row["scoreAfter"][seat] - row["scoreAfter"][1-seat],
              row["metricsAfter"]["ownedDesiredPaths"][seat], row["metricsAfter"]["ownedRails"][seat],
              row["accepted"][seat]["cost"], row["learnerUnusedPaint"], row["learnerStoppedWithPaint"]] for row in turns]
    milestones = []
    for at in MILESTONES:
        row = turns[at] if at < len(turns) else None
        milestones.append({"preturn": at, "observed": row is not None,
                           "income": row["incomeBefore"][seat] if row else None,
                           "owned_desired_paths": row["metricsBefore"]["ownedDesiredPaths"][seat] if row else None,
                           "missing_reason": None if row else "at or after actual terminal"})
    return {**{key: game[key] for key in ("id", "learnerSpec", "opponentSpec", "learnerSeat", "seed", "mapHash")},
            "turns": len(turns), "learner_score": game["score"][seat], "opponent_score": game["score"][1-seat],
            "score_gap": gap, "result": "win" if gap > 0 else "loss" if gap < 0 else "draw",
            "result_score": 1. if gap > 0 else 0. if gap < 0 else .5,
            "first_score_after_turn": onset, "first_positive_income_after_turn": income_onset,
            "zero_income_game": onset is None, "never_positive_public_income": income_onset is None,
            "paint_used": sum(row["accepted"][seat]["cost"] for row in turns),
            "unused_paint": sum(row["learnerUnusedPaint"] for row in turns),
            "stopped_with_paint_turns": sum(row["learnerStoppedWithPaint"] for row in turns),
            "placements": sum(row["accepted"][seat]["count"] for row in turns),
            "final_owned_desired_paths": turns[-1]["metricsAfter"]["ownedDesiredPaths"][seat],
            "max_owned_desired_paths": max(row["metricsAfter"]["ownedDesiredPaths"][seat] for row in turns),
            "income_milestones": milestones, "behaviour_trace": trace,
            "value_metrics": value_metrics([(game, targets)])}


def outcome_summary(rows):
    return {"games": len(rows), "independent_map_clusters": len({row["mapHash"] for row in rows}),
            "wins": sum(row["result"] == "win" for row in rows), "draws": sum(row["result"] == "draw" for row in rows),
            "losses": sum(row["result"] == "loss" for row in rows),
            "mean_score": statistics.fmean(row["result_score"] for row in rows),
            "mean_score_gap": statistics.fmean(row["score_gap"] for row in rows),
            "zero_income_games": sum(row["zero_income_game"] for row in rows),
            "mean_first_score_after_turn_among_nonzero": statistics.fmean(row["first_score_after_turn"] for row in rows if not row["zero_income_game"])
            if any(not row["zero_income_game"] for row in rows) else None,
            "mean_paint_used_per_turn": statistics.fmean(row["paint_used"] / row["turns"] for row in rows),
            "mean_unused_paint_turn_fraction": statistics.fmean(row["stopped_with_paint_turns"] / row["turns"] for row in rows),
            "mean_max_owned_desired_paths": statistics.fmean(row["max_owned_desired_paths"] for row in rows)}


def paired_deltas(rows):
    lookup = {(r["learnerSpec"], r["opponentSpec"], r["seed"], r["learnerSeat"]): r for r in rows}
    result = []
    for control, candidate in itertools.combinations(LEARNERS, 2):
        for opponent in OPPONENTS:
            pairs = []
            for seed, seat in itertools.product(SEEDS, (0, 1)):
                before, after = (lookup[(learner, opponent, seed, seat)] for learner in (control, candidate))
                require(before["mapHash"] == after["mapHash"], "Paired comparison map mismatch")
                pairs.append({"seed": seed, "learnerSeat": seat, "mapHash": before["mapHash"],
                              "control_game": before["id"], "candidate_game": after["id"],
                              "score_gap_delta": after["score_gap"] - before["score_gap"],
                              "result_score_delta": after["result_score"] - before["result_score"],
                              "zero_income_became_positive": before["zero_income_game"] and not after["zero_income_game"],
                              "positive_income_became_zero": after["zero_income_game"] and not before["zero_income_game"]})
            per_map = [{"seed": seed, "mean_score_gap_delta": statistics.fmean(row["score_gap_delta"] for row in pairs if row["seed"] == seed),
                        "mean_result_score_delta": statistics.fmean(row["result_score_delta"] for row in pairs if row["seed"] == seed)} for seed in SEEDS]
            result.append({"control": control, "candidate": candidate, "opponent": opponent,
                           "games": len(pairs), "independent_map_clusters": 4,
                           "mean_score_gap_delta": statistics.fmean(row["score_gap_delta"] for row in pairs),
                           "mean_result_score_delta": statistics.fmean(row["result_score_delta"] for row in pairs),
                           "per_map": per_map, "pairs": pairs})
    return result


def historical_optimizer(path, expected_iterations):
    report = read_json(path)
    rows, config = report["iterations"], report["config"]
    require(report["status"] == "complete" and len(rows) == expected_iterations
            and [r["iteration"] for r in rows] == list(range(1, expected_iterations + 1)), "Incomplete historical optimizer log")
    fields = ("reward_mean", "reward_abs_mean", "return_mean", "return_variance", "value_mse_before", "value_mse_after",
              "value_mse_div_return_variance", "policy_loss", "entropy", "approx_kl", "clip_fraction", "gradient_norm")
    curve = [{"iteration": row["iteration"], **{field: finite(row[field], field) for field in fields}} for row in rows]
    block_size = expected_iterations // 4
    blocks = [{"first_iteration": start + 1, "last_iteration": start + block_size,
               **{field: statistics.fmean(r[field] for r in curve[start:start + block_size]) for field in fields}}
              for start in range(0, expected_iterations, block_size)]
    return {"source": str(Path(path)), "sha256": digest(path), "iterations": expected_iterations,
            "reward_scale": config["reward_scale"], "value_coefficient": config["value_coefficient"],
            "entropy_coefficient": config["entropy_coefficient"], "gradient_clip": config["gradient_clip"],
            "iterations_with_mean_preclip_norm_above_cap": sum(r["gradient_norm"] > config["gradient_clip"] for r in curve),
            "iterations_with_mean_approx_kl_above_002": sum(r["approx_kl"] > .02 for r in curve),
            "quarter_means": blocks, "curve": curve,
            "limits": ["Gradient norm is the minibatch mean before clipping, not per-component attribution.",
                       "Value errors use the same updated rollout and changing bootstrapped GAE targets, not held-out Monte Carlo calibration.",
                       "Raw joint entropy depends on legal-action counts and STOP frequency; it is not normalized across states.",
                       "Both-seat self-play mean reward is algebraically zero; actor/critic domination and causal regression are not established."]}


def load_games(manifest_path, exclusion_path):
    manifest_path = Path(manifest_path).resolve()
    manifest = read_json(manifest_path)
    require(manifest.get("schemaVersion") == 1 and manifest.get("experiment") == "spatial-learning-audit"
            and manifest.get("status") == "complete" and manifest.get("scheduledGames") == 64
            and manifest.get("budgetMs") == 60000 and manifest.get("warmup") is False, "Wrong/incomplete fixed audit manifest")
    expected_weights = frozen_weight_hashes()
    require(manifest["weightsSha256"] == expected_weights, "Manifest weights differ from frozen payloads")
    exclusion_data = read_json(exclusion_path)
    require(isinstance(exclusion_data.get("mapHashes"), list), "Exclusions must use the exact Java-consumed mapHashes schema")
    excluded_hashes = [hash_string(value) for value in exclusion_data["mapHashes"]]
    exclusions = set(excluded_hashes)
    require(len(exclusions) == len(excluded_hashes), "Duplicate exclusion hash")
    require(manifest["exclusionsSha256"] == digest(exclusion_path) and manifest["excludedMapCount"] == len(exclusions), "Exclusion index lineage mismatch")
    maps = manifest["maps"]
    require(len(maps) == 4 and {m["seed"] for m in maps} == set(SEEDS), "Wrong map set")
    require(len({m["mapHash"] for m in maps}) == 4, "Duplicate map content")
    by_seed = {}
    for row in maps:
        require(row["mapIndex"] == row["seed"] - SEEDS[0]
                and hashlib.sha256(row["initBlockP0"].encode()).hexdigest() == hash_string(row["mapHash"])
                and row["mapHash"] not in exclusions, "Map hash/index/freshness mismatch")
        by_seed[row["seed"]] = row
    expected_schedule = set(itertools.product(LEARNERS, OPPONENTS, SEEDS, (0, 1)))
    seen, files, games = set(), {}, []
    require(len(manifest["games"]) == 64, "Wrong number of game files")
    for entry in manifest["games"]:
        key = tuple(entry[k] for k in ("learnerSpec", "opponentSpec", "seed", "learnerSeat"))
        require(key in expected_schedule and key not in seen, "Missing/duplicate/unexpected scheduled game")
        seen.add(key)
        path = (manifest_path.parent / entry["file"]).resolve()
        require(path.is_relative_to(manifest_path.parent) and str(path) not in files, "Repeated/escaping game file")
        file_hash = digest(path)
        require(file_hash == hash_string(entry["sha256"]), "Game file hash mismatch")
        game = validate_game(read_json(path))
        for field in ("id", "learnerSpec", "opponentSpec", "learnerSeat", "mapIndex", "seed", "mapHash", "status", "turnCount", "score", "terminal"):
            require(entry[field] == game[field], f"Manifest/game {field} mismatch")
        original = by_seed[game["seed"]]
        require(game["mapHash"] == original["mapHash"] and game["initBlockP0"] == original["initBlockP0"], "Game uses a different map")
        games.append(game)
        files[str(path)] = file_hash
    require(seen == expected_schedule and len({g["id"] for g in games}) == 64
            and sum(len(g["turns"]) for g in games) == manifest["totalTurns"], "Final schedule/turn/id accounting mismatch")
    return manifest, games, {"manifest": str(manifest_path), "manifest_sha256": digest(manifest_path),
                             "exclusion_index": str(Path(exclusion_path).resolve()), "exclusions_sha256": digest(exclusion_path),
                             "game_files_sha256": files, "frozen_payload_sha256": expected_weights}


def analyze(games):
    game_targets = [(game, future_targets(game)) for game in games]
    behaviours = [game_behaviour(game, targets) for game, targets in game_targets]
    groups = []
    for learner, opponent in itertools.product(LEARNERS, OPPONENTS):
        selected = [(g, t) for g, t in game_targets if g["learnerSpec"] == learner and g["opponentSpec"] == opponent]
        rows = [r for r in behaviours if r["learnerSpec"] == learner and r["opponentSpec"] == opponent]
        groups.append({"learner": learner, "opponent": opponent, "outcomes": outcome_summary(rows),
                       "native_head_status": "uncalibrated after trunk-only policy distillation" if learner == LEARNERS[3] else "native PPO units; greedy/opponent distribution shift",
                       "v5_status": "teacher-trained value transfer to this named learner continuation",
                       "values_all_turns": value_metrics(selected),
                       "values_by_preturn_bin": [{"start_inclusive": start, "stop_exclusive": stop,
                                                  "metrics": value_metrics(selected, start, stop)} for start, stop in BINS]})
    return {"status": "complete", "experiment": "spatial-learning-audit", "games": len(games),
            "turns": sum(len(g["turns"]) for g in games), "independent_map_clusters": len({g["mapHash"] for g in games}),
            "contracts": {"ppo": {"gamma": GAMMA, "scale": PPO_SCALE, "terminal_bonus": TERMINAL_BONUS,
                                    "target": "sum gamma^k*(recorded gained gap + terminal sign*30)/100"},
                          "v5": {"gamma": 1., "scale": V5_SCALE, "terminal_bonus": 0.,
                                   "target": "(final learner gap - preturn learner gap)/10000"},
                          "income_baselines": "Hold current PUBLIC preturn income gap constant through public turn100; discount only PPO; no terminal bonus or actual-future-length leakage.",
                          "metric_weighting": "Each contributing game has equal weight; its observed turns have equal weight within that game. No CI/significance test."},
            "limits": ["Only four independent fresh map clusters; ample60000ms calls without warmup are not CG-budget strength evidence.",
                       "Targets and errors stay grouped by acting learner and opponent; future returns from different policies are never pooled.",
                       "Native PPO targets match reward units but greedy inference/opponents differ from sampled training continuation.",
                       "V4 native value is uncalibrated: its head froze while shared features changed.",
                       "V5 was fitted on v09 teacher trajectories; all student groups are transfer checks, not counterfactual action-ranking validation.",
                       "Raw PPO and V5 error units are different; direct cross-contract ranking is invalid.",
                       "Undefined/constant-target or constant-prediction correlations are null. Turns are correlated.",
                       "The exporter's stopped-with-paint flag means total cost<3, including forced STOP or insufficient affordable legal choices; it does not establish voluntary/premature stopping.",
                       "Owned-desired-path counts mean active desired routes containing at least one rail of that player, not exclusively owned routes or newly completed connections.",
                       "Python validates trace accounting only; independent Java core replay establishes legality/scoring/path fidelity."],
            "groups": groups, "paired_deltas": paired_deltas(behaviours), "behaviour_trace_columns": TRACE_COLUMNS,
            "per_game": behaviours}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--manifest", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--excluded-maps", required=True)
    parser.add_argument("--history-v2", default=str(ROOT / "docs/evidence/spatial-ppo-v2-training.json"))
    parser.add_argument("--history-v3", default=str(ROOT / "docs/evidence/spatial-ppo-v3-training.json"))
    args = parser.parse_args()
    output = Path(args.output)
    require(not output.exists(), "Refusing to overwrite prior analysis")
    manifest, games, lineage = load_games(args.manifest, args.excluded_maps)
    result = analyze(games)
    result["lineage"] = {**lineage, "analyzer_sha256": digest(__file__)}
    result["effective_params"] = manifest["params"]
    result["historical_optimizer"] = {"v2": historical_optimizer(args.history_v2, 64), "v3": historical_optimizer(args.history_v3, 128)}
    output.parent.mkdir(parents=True, exist_ok=True)
    with output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, indent=2, allow_nan=False)
        stream.write("\n")
    print(json.dumps({"status": "complete", "games": result["games"], "turns": result["turns"], "map_clusters": 4, "output": str(output)}))


if __name__ == "__main__":
    main()
