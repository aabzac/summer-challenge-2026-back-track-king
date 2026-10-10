"""One fixed ridge fit of the 15 value coefficients on frozen v4 features.

Existing teacher games are reused without new play, gradients or optimizer epochs.
"""
from __future__ import annotations

import argparse
import base64
import json
import os
from pathlib import Path
import time

from train_spatial import ROOT, Bridge, SpatialNet, np, torch
from model import WEIGHT_ORDER
from run_v2_training import peak_working_set_bytes, runtime_identities, write_json
from v2_support import weight_hash
from v3_support import java_payload_hash
from v4_data import (ROW_DTYPE, digest_file, local_file, parity_observations, read_json,
                     row_tensors, validate_layout, validate_metadata, validate_rows, verify_cache)
from v4_distill import historical_integrity, verify_freeze, verify_parity

V4_SHA256 = "89de11f4239844c55c6e61c987ee2443fd5d79a1c71af782d2686e836ec6f3ef"
POLICY_FLOATS = 3424
CONFIG = {"schema": "spatial-ppo-v5-value-ridge", "warm_start_payload_sha256": V4_SHA256,
          "feature_count": 14, "value_parameter_count": 15, "frozen_policy_parameters": POLICY_FLOATS,
          "batch_size": 64, "torch_threads": 2, "ridge_lambda": .01, "constant_std_threshold": 1e-12,
          "score_scale": 10000., "gamma": 1., "terminal_bonus": 0., "public_max_turns": 100,
          "train_rows": 4548, "validation_rows": 1112, "weighting": "one equal weight per observed teacher turn",
          "objective": "mean squared residual plus .01 times squared standardized coefficients; unpenalized intercept",
          "feature_standardization": "training mean and population standard deviation only",
          "target": "(final teacher score gap - current preturn teacher score gap)/10000",
          "checkpoint_fit_counts": [0, 1], "checkpoint_purpose": "model/config/ridge inspection; no optimizer or RNG recovery claim",
          "parity_selection": "first 24 training stage-zero rows in existing seed/seat/turn order",
          "metric_turn_bins": [[0, 25], [25, 50], [50, 75], [75, 100]],
          "interpretation": "recorded v09 continuation against its pool; exposed validation maps; not counterfactual or on-policy value"}


def policy_bytes(model):
    return base64.b64decode(model.encoded_weights(), validate=True)[:POLICY_FLOATS * 4]


def historical_v4_identity():
    previous = historical_integrity()
    evidence_path = ROOT / "docs/evidence/spatial-ppo-v4-training.json"
    evidence = read_json(evidence_path)
    if evidence["status"] != "complete" or evidence["final_weights_sha256"] != V4_SHA256:
        raise AssertionError("V4 completed training identity does not match the fixed warm start")
    # Integration may add Params/factory entries; old feature/runtime/math files
    # and all old spatial Python modules must retain their original bytes.
    frozen = {name: expected for name, expected in evidence["config"]["source_sha256"].items()
              if name.startswith(("tools/spatial/", "src/main/java/btk/core/", "src/main/java/btk/bot/neural/Spatial"))}
    for name, expected in frozen.items():
        if digest_file(ROOT / name) != expected:
            raise AssertionError(f"Frozen v4 source changed: {name}")
    weights = ROOT / "src/main/java/btk/bot/neural/SpatialV4Weights.java"
    if java_payload_hash(weights) != V4_SHA256:
        raise AssertionError("Frozen v4 Java weights changed")
    return {"v1_v2_v3": previous, "v4_training_evidence_sha256": digest_file(evidence_path),
            "v4_verified_source_count": len(frozen), "v4_weights_source_sha256": digest_file(weights),
            "v4_payload_sha256": V4_SHA256}


def current_source_identities():
    paths = list((ROOT / "tools/spatial").glob("*.py"))
    paths += [p for p in (ROOT / "src/main/java/btk").rglob("*.java") if p.name != "SpatialV5Weights.java"]
    paths += [p for p in (ROOT / "src/main/resources/btk").rglob("*") if p.is_file()]
    paths += [ROOT / "docs/spatial-ppo-v5.md"]
    return {p.relative_to(ROOT).as_posix(): digest_file(p) for p in sorted(set(paths))}


def current_runtime_identity(command):
    result = runtime_identities(command)
    position = command.index("-cp") if "-cp" in command else command.index("-classpath")
    for entry in command[position + 1].split(os.pathsep):
        path = Path(entry)
        if path.is_dir():
            for file in (path / "btk").rglob("*"):
                if file.is_file() and file.name not in ("SpatialV5Weights.class", "SpatialV5Bot.class"):
                    result["compiled_engine_and_classpath_sha256"][str(file.resolve())] = digest_file(file)
    return result


def turn_targets(metadata, max_turns=100, score_scale=10000.):
    """Use recorded core scores/gains, not Python scoring or future terminal time."""
    if max_turns != CONFIG["public_max_turns"] or score_scale != CONFIG["score_scale"]:
        raise ValueError("Target horizon/normalization differs from the frozen v5 recipe")
    seat = metadata["teacherSeat"]
    turns = metadata["turns"]
    if seat not in (0, 1) or not turns or len(turns) > max_turns:
        raise ValueError("Invalid teacher seat or completed-game length")
    final = np.asarray(metadata["score"], dtype=np.float64)
    if final.shape != (2,) or not np.isfinite(final).all():
        raise ValueError("Invalid final core scores")
    previous = np.zeros(2, dtype=np.float64)
    previous_gained = np.zeros(2, dtype=np.float64)
    targets, incomes, preturn_gaps = [], [], []
    for index, row in enumerate(turns):
        gained, after = np.asarray(row["gained"], dtype=np.float64), np.asarray(row["scoreAfter"], dtype=np.float64)
        if (row["turn"] != index or row["rowStart"] != 4 * index or gained.shape != (2,) or after.shape != (2,)
                or not np.isfinite(gained).all() or not np.isfinite(after).all() or (gained < 0).any()
                or not np.array_equal(previous + gained, after)
                or bool(row["terminalAfter"]) != (index == len(turns) - 1)):
            raise ValueError("Recorded core score/turn/terminal accounting is inconsistent")
        gap = previous[seat] - previous[1 - seat]
        targets.append(((final[seat] - final[1 - seat]) - gap) / score_scale)
        incomes.append((max_turns - index) * (previous_gained[seat] - previous_gained[1 - seat]) / score_scale)
        preturn_gaps.append(gap)
        previous, previous_gained = after, gained
    if not np.array_equal(previous, final):
        raise ValueError("Final scores differ from the last core transition")
    return np.asarray(targets), np.asarray(incomes), np.asarray(preturn_gaps)


def extract_features(model, rows):
    """A pre-hook reads the existing linear layer input; forward math is untouched."""
    if len(rows) == 0 or not (rows["stage"] == 0).all():
        raise ValueError("Critic extraction requires nonempty stage-zero rows only")
    features = np.empty((len(rows), 14), dtype=np.float32)
    initial_values = np.empty(len(rows), dtype=np.float32)
    captured = []

    def capture(_module, inputs):
        if len(inputs) != 1 or inputs[0].ndim != 2 or inputs[0].shape[1] != 14:
            raise AssertionError("Unchanged value layer no longer has z[14]")
        captured.append(inputs[0].detach().cpu().numpy().copy())

    handle = model.value.register_forward_pre_hook(capture)
    model.eval()
    try:
        with torch.no_grad():
            for start in range(0, len(rows), CONFIG["batch_size"]):
                obs, _, _ = row_tensors(rows[start:start + CONFIG["batch_size"]])
                if not all(torch.isfinite(obs[name]).all() for name in ("planes", "towns", "scalars")):
                    raise ValueError("Non-finite source features")
                captured.clear()
                values = model(obs)[2].numpy()
                if len(captured) != 1 or captured[0].shape != (len(values), 14):
                    raise AssertionError("Expected exactly one value-input observation per batch")
                if not np.isfinite(captured[0]).all() or not np.isfinite(values).all():
                    raise FloatingPointError("Non-finite extracted critic input/output")
                features[start:start + len(values)] = captured[0]
                initial_values[start:start + len(values)] = values
    finally:
        handle.remove()
    return features, initial_values


def ridge_fit(features, targets):
    x, y = np.asarray(features, dtype=np.float64), np.asarray(targets, dtype=np.float64)
    if x.ndim != 2 or x.shape[1] != 14 or len(x) == 0 or y.shape != (len(x),):
        raise ValueError("Ridge requires nonempty Nx14 features and N targets")
    if not np.isfinite(x).all() or not np.isfinite(y).all():
        raise ValueError("Ridge input must be finite")
    mean, std, target_mean = x.mean(0), x.std(0, ddof=0), float(y.mean())
    active = std > CONFIG["constant_std_threshold"]
    standardized = (x[:, active] - mean[active]) / std[active]
    centered = y - target_mean
    if active.any():
        gram = standardized.T @ standardized / len(x)
        system = gram + CONFIG["ridge_lambda"] * np.eye(int(active.sum()))
        rhs = standardized.T @ centered / len(x)
        beta = np.linalg.solve(system, rhs)
        singular = np.linalg.svd(standardized, compute_uv=False)
        residual = system @ beta - rhs
        rank = int(np.linalg.matrix_rank(standardized))
        condition = float(np.linalg.cond(system))
    else:
        beta, singular, residual = np.empty(0), np.empty(0), np.empty(0)
        rhs, rank, condition = np.empty(0), 0, None
    weights = np.zeros(14, dtype=np.float64)
    weights[active] = beta / std[active]
    bias = target_mean - float(weights @ mean)
    predictions = x @ weights + bias
    values = np.concatenate((weights, [bias]))
    if not np.isfinite(values).all() or not np.isfinite(values.astype(np.float32)).all():
        raise FloatingPointError("Ridge coefficients cannot be represented as finite float32")
    return {"weights_float64": weights.tolist(), "bias_float64": bias,
            "weights_float32": weights.astype(np.float32).tolist(), "bias_float32": float(np.float32(bias)),
            "feature_mean": mean.tolist(), "feature_population_std": std.tolist(),
            "active_columns": np.flatnonzero(active).tolist(), "dropped_columns": np.flatnonzero(~active).tolist(),
            "target_mean": target_mean, "standardized_coefficients": beta.tolist(),
            "standardized_design_rank": rank, "standardized_design_singular_values": singular.tolist(),
            "regularized_system_condition": condition, "solver_residual_l2": float(np.linalg.norm(residual)),
            "solver_relative_residual_l2": float(np.linalg.norm(residual) / max(np.linalg.norm(rhs), 1e-300)),
            "float64_training_mse": float(np.square(predictions - y).mean()),
            "ridge_penalty": float(CONFIG["ridge_lambda"] * np.square(beta).sum()),
            "objective": float(np.square(predictions - y).mean() + CONFIG["ridge_lambda"] * np.square(beta).sum())}


def install_head(model, fit, expected_policy):
    if policy_bytes(model) != expected_policy:
        raise AssertionError("Policy bytes changed before value fitting")
    with torch.no_grad():
        model.value.weight.copy_(torch.tensor(fit["weights_float32"], dtype=torch.float32).reshape(1, 14))
        model.value.bias.copy_(torch.tensor([fit["bias_float32"]], dtype=torch.float32))
    if policy_bytes(model) != expected_policy:
        raise AssertionError("Value fit changed frozen policy/shared bytes")


def predict_head(model, features):
    values = []
    with torch.no_grad():
        for start in range(0, len(features), CONFIG["batch_size"]):
            values.append(model.value(torch.from_numpy(np.asarray(features[start:start + CONFIG["batch_size"]], dtype=np.float32))).numpy()[:, 0])
    result = np.concatenate(values)
    if not np.isfinite(result).all():
        raise FloatingPointError("Non-finite exported-head prediction")
    return result


def regression_metrics(targets, predictions):
    y, p = np.asarray(targets, dtype=np.float64), np.asarray(predictions, dtype=np.float64)
    if y.ndim != 1 or p.shape != y.shape or not np.isfinite(y).all() or not np.isfinite(p).all():
        raise ValueError("Regression metrics require matching finite vectors")
    if len(y) == 0:
        return {"rows": 0, **{key: None for key in ("rmse", "mae", "bias", "r_squared", "pearson", "rmse_score_points", "mae_score_points")}}
    error, centered_y, centered_p = p - y, y - y.mean(), p - p.mean()
    y_ss, p_ss = float(centered_y @ centered_y), float(centered_p @ centered_p)
    mse, mae = float(np.square(error).mean()), float(np.abs(error).mean())
    pearson = float(np.clip((centered_y @ centered_p) / np.sqrt(y_ss * p_ss), -1., 1.)) if y_ss > 0 and p_ss > 0 else None
    return {"rows": len(y), "rmse": float(np.sqrt(mse)), "mae": mae, "bias": float(error.mean()),
            "r_squared": 1 - float(error @ error) / y_ss if y_ss > 0 else None, "pearson": pearson,
            "rmse_score_points": float(np.sqrt(mse)) * CONFIG["score_scale"], "mae_score_points": mae * CONFIG["score_scale"]}


def grouped_metrics(targets, predictions, lineage):
    if len(lineage) != len(targets):
        raise ValueError("Regression grouping lineage mismatch")
    groups = {"all": np.ones(len(targets), dtype=bool)}
    for opponent in ("v01", "spatial-ppo-v2", "v09"):
        groups["opponent:" + opponent] = np.asarray([row["opponentSpec"] == opponent for row in lineage])
    for lower, upper in CONFIG["metric_turn_bins"]:
        groups[f"turn:{lower}-{upper - 1}"] = np.asarray([lower <= row["turn"] < upper for row in lineage])
    return {group: {name: regression_metrics(targets[mask], prediction[mask]) for name, prediction in predictions.items()}
            for group, mask in groups.items()}


def export_v5_weights(model, path):
    path = Path(path)
    if path.name != "SpatialV5Weights.java":
        raise ValueError("V5 may generate only SpatialV5Weights.java")
    payload = model.encoded_weights()
    chunks = [payload[start:start + 100] for start in range(0, len(payload), 100)]
    source = ("package btk.bot.neural;\n\n"
              "/** Generated by tools/spatial/v5_value.py; only the 15 value coefficients are fitted. */\n"
              "public final class SpatialV5Weights {\n"
              "    private SpatialV5Weights() {}\n"
              "    public static final String DATA =\n")
    source += "\n".join('        "' + part + ('";' if i == len(chunks) - 1 else '" +')
                        for i, part in enumerate(chunks)) + "\n}\n"
    path.write_text(source, encoding="utf-8")


def save_checkpoint(path, model, config, fit_count, fit):
    torch.save({"model": model.state_dict(), "config": config, "fit_count": fit_count,
                "ridge": fit, "method": "closed-form ridge; no optimizer/RNG recovery claim"}, path)


def run(args):
    out, evidence, cache = Path(args.output), Path(args.evidence), Path(args.cache)
    if out.exists() or evidence.exists():
        raise FileExistsError("Refusing to reuse v5 fit output or evidence")
    freeze = verify_freeze(args.source_freeze)
    validation_start = time.perf_counter()
    data = verify_cache(cache)
    manifest = read_json(Path(data["corpus_dir"]) / "manifest.json")
    validate_layout(manifest, set(read_json(data["exclusions_path"])["gamesPerMap"]), data["exclusions_sha256"])
    if data["splits"]["train"]["rows"] != 4 * CONFIG["train_rows"] or data["splits"]["validation"]["rows"] != 4 * CONFIG["validation_rows"]:
        raise AssertionError("Frozen v4 teacher-turn counts changed")
    v4_evidence = read_json(ROOT / "docs/evidence/spatial-ppo-v4-training.json")
    if data != v4_evidence["config"]["data"] or digest_file(cache / "cache.json") != v4_evidence["config"]["cache_sha256"]:
        raise AssertionError("Cache differs from the exact completed v4 corpus/cache")
    command = read_json(args.java_command_json)
    config = {**CONFIG, "source_freeze": freeze, "source_sha256": current_source_identities(),
              "historical_artifacts": historical_v4_identity(), "runtime": current_runtime_identity(command),
              "java_command": command, "java_command_sha256": digest_file(args.java_command_json),
              "warm_start_checkpoint_sha256": digest_file(args.warm_start), "cache_sha256": digest_file(cache / "cache.json"),
              "v4_corpus": data, "corpus_archive_sha256": digest_file(ROOT / "docs/evidence/spatial-ppo-v4-artifacts.zip")}
    if torch.__version__ != "2.8.0+cpu" or np.__version__ != "2.3.5":
        raise RuntimeError("Frozen runtime requires PyTorch 2.8.0+cpu/NumPy 2.3.5")
    torch.set_num_threads(CONFIG["torch_threads"])
    torch.set_num_interop_threads(1)
    torch.use_deterministic_algorithms(True)
    model = SpatialNet()
    loaded = torch.load(args.warm_start, map_location="cpu", weights_only=False)
    model.load_state_dict(loaded["model"])
    del loaded
    if weight_hash(model) != V4_SHA256:
        raise AssertionError("V5 requires the exact final v4 checkpoint payload")
    model.requires_grad_(False)
    original_policy = policy_bytes(model)
    import hashlib
    config["frozen_policy_sha256"] = hashlib.sha256(original_policy).hexdigest()
    out.mkdir(parents=True, exist_ok=False)
    evidence.parent.mkdir(parents=True, exist_ok=True)
    write_json(out / "config.json", config)
    report = {"status": "running", "config": config, "initial_weights_sha256": weight_hash(model),
              "input_validation_seconds": time.perf_counter() - validation_start}
    started, mappings, extracted, all_lineage = time.perf_counter(), {}, {}, {}

    def emit(message):
        line = json.dumps(message, allow_nan=False)
        print(line, flush=True)
        with (out / "run.log").open("a", encoding="utf-8") as stream:
            stream.write(line + "\n")

    try:
        save_checkpoint(out / "initial.pt", model, config, 0, None)
        (out / "initial-weights.base64").write_text(model.encoded_weights(), encoding="ascii")
        emit({"started": True, "train_turns": CONFIG["train_rows"], "validation_turns": CONFIG["validation_rows"], "ridge_solves": 1})
        extraction_start = time.perf_counter()
        for split, part in data["splits"].items():
            rows = np.memmap(cache / part["file"], dtype=ROW_DTYPE, mode="r")
            mappings[split] = rows
            targets, incomes, lineage, gaps, expected_start = [], [], [], [], 0
            for game in sorted((g for g in data["games"] if g["split"] == split), key=lambda g: g["cacheRowStart"]):
                if game["cacheRowStart"] != expected_start:
                    raise AssertionError("Cached game row ranges are not contiguous")
                metadata = read_json(local_file(data["corpus_dir"], game["metadata"]))
                validate_metadata(game, metadata)
                validate_rows(rows[expected_start:expected_start + game["rowCount"]], game, metadata)
                target, income, gap = turn_targets(metadata)
                targets.extend(target)
                incomes.extend(income)
                gaps.extend(gap)
                lineage.extend({"split": split, "seed": game["seed"], "mapHash": game["mapHash"], "game": game["id"],
                                "teacherSeat": game["teacherSeat"], "opponentSpec": game["opponentSpec"],
                                "turn": turn, "stage": 0, "cacheRow": expected_start + 4 * turn,
                                "metadata": game["metadata"], "metadataSha256": game["metadataSha256"]}
                               for turn in range(game["turns"]))
                expected_start += game["rowCount"]
            if expected_start != len(rows) or len(targets) != len(rows) // 4:
                raise AssertionError("Teacher turn extraction lost/duplicated a row")
            features, old_values = extract_features(model, rows[::4])
            extracted[split] = {"features": features, "targets": np.asarray(targets),
                                "income_baseline": np.asarray(incomes), "preturn_gap": np.asarray(gaps), "initial_values": old_values}
            all_lineage[split] = lineage
        report["extraction_seconds"] = time.perf_counter() - extraction_start
        np.savez_compressed(out / "features-targets.npz", **{split + "_" + name: value for split, values in extracted.items() for name, value in values.items()})
        write_json(out / "row-lineage.json", all_lineage)
        panel = parity_observations(mappings["train"][::4][:24])
        write_json(out / "parity-observations.json", panel)
        write_json(out / "parity-row-lineage.json", all_lineage["train"][:24])
        with Bridge(command) as bridge:
            report["initial_parity"] = verify_parity(bridge, model, panel, out, "initial")
            solve_start = time.perf_counter()
            fitted = ridge_fit(extracted["train"]["features"], extracted["train"]["targets"])
            report["solve_seconds"] = time.perf_counter() - solve_start
            install_head(model, fitted, original_policy)
            report["fit"] = fitted
            report["metrics"], report["float32_rounding"] = {}, {}
            predictions_artifact = {}
            for split, values in extracted.items():
                predictions = predict_head(model, values["features"])
                raw64 = values["features"].astype(np.float64) @ np.asarray(fitted["weights_float64"]) + fitted["bias_float64"]
                delta = predictions.astype(np.float64) - raw64
                report["float32_rounding"][split] = {"max_abs_prediction_change": float(np.abs(delta).max()),
                                                    "rmse_prediction_change": float(np.sqrt(np.square(delta).mean()))}
                baselines = {"fitted_head": predictions, "zero": np.zeros(len(predictions)),
                             "train_mean": np.full(len(predictions), fitted["target_mean"]),
                             "income_extrapolation": values["income_baseline"], "initial_v4_head": values["initial_values"]}
                report["metrics"][split] = grouped_metrics(values["targets"], baselines, all_lineage[split])
                predictions_artifact[split + "_fitted_float32"] = predictions
                predictions_artifact[split + "_fitted_float64"] = raw64
            np.savez_compressed(out / "predictions.npz", **predictions_artifact)
            report["final_parity"] = verify_parity(bridge, model, panel, out, "final")
        if policy_bytes(model) != original_policy:
            raise AssertionError("Policy/shared bytes changed")
        if current_source_identities() != config["source_sha256"] or historical_v4_identity() != config["historical_artifacts"]:
            raise AssertionError("Current/frozen sources changed during value fitting")
        if verify_freeze(args.source_freeze) != freeze:
            raise AssertionError("V5 pre-fit freeze changed")
        for filename, expected in config["runtime"]["compiled_engine_and_classpath_sha256"].items():
            if digest_file(filename) != expected:
                raise AssertionError(f"Compiled runtime changed: {filename}")
        if verify_cache(cache) != data or digest_file(cache / "cache.json") != config["cache_sha256"]:
            raise AssertionError("Old v4 corpus/cache changed during fit")
        if digest_file(args.warm_start) != config["warm_start_checkpoint_sha256"]:
            raise AssertionError("V4 checkpoint changed during fit")
        save_checkpoint(out / "final.pt", model, config, 1, fitted)
        export_v5_weights(model, ROOT / "src/main/java/btk/bot/neural/SpatialV5Weights.java")
        (out / "final-weights.base64").write_text(model.encoded_weights(), encoding="ascii")
        report.update(status="complete", final_weights_sha256=weight_hash(model), ridge_solves=1,
                      frozen_policy_parameters=POLICY_FLOATS, fitted_value_parameters=15, policy_bytes_unchanged=True,
                      artifact_sha256={name: digest_file(out / name) for name in ("features-targets.npz", "row-lineage.json", "predictions.npz", "initial.pt", "final.pt")},
                      total_seconds=time.perf_counter() - started, python_peak_working_set_bytes=peak_working_set_bytes())
        write_json(out / "metrics.json", report)
        write_json(evidence, report)
        emit({"complete": True, "final_weights_sha256": report["final_weights_sha256"],
              "seconds": report["total_seconds"], "validation": report["metrics"]["validation"]["all"]["fitted_head"]})
    except Exception as exc:
        report.update(status="failed", failure={"type": type(exc).__name__, "message": str(exc)}, total_seconds=time.perf_counter() - started)
        write_json(out / "metrics.json", report)
        write_json(out / "failure.json", report["failure"])
        write_json(evidence, report)
        emit({"failed": True, **report["failure"]})
        raise
    finally:
        for rows in mappings.values():
            rows._mmap.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", default="build/spatial-ppo-v5")
    parser.add_argument("--evidence", default="docs/evidence/spatial-ppo-v5-training.json")
    parser.add_argument("--java-command-json", default="build/spatial-v5-java.json")
    parser.add_argument("--source-freeze", default="build/analysis/spatial-ppo-v5/pre-fit-freeze.json")
    parser.add_argument("--cache", default="build/spatial-ppo-v4-cache")
    parser.add_argument("--warm-start", default="build/spatial-ppo-v4/final.pt")
    run(parser.parse_args())
