"""Numerical parity of v2 value + action advantages on Java float32 probes."""
import argparse
import datetime as dt
import json
from pathlib import Path
import sys

import check_parity as probe_v1
import train_q as v1
import train_q_v2 as v2
import numpy as np

PROBE_SEEDS = [2109003, 2109004, 2109005]
PROBE_TURNS = [0, 12, 25]
PROBE_TUPLES = {(seed, turn, player) for seed in PROBE_SEEDS for turn in PROBE_TURNS for player in (0, 1)}


def validate_probe_integrity(manifest, rows, weights_sha, probe_sha, exclusion_hashes):
    """A numerical pass requires the complete fixed panel from this exact model."""
    if type(manifest.get("schemaVersion")) is not int or manifest["schemaVersion"] != 1:
        raise ValueError("Expected the completed Java probe manifest schemaVersion=1")
    if manifest.get("complete") is not True or type(manifest.get("rows")) is not int or manifest["rows"] != 18:
        raise ValueError("Java probe manifest must declare all 18 completed observations")
    if (manifest.get("probeSeeds") != PROBE_SEEDS or manifest.get("probeTurns") != PROBE_TURNS
            or any(type(value) is not int for value in manifest["probeSeeds"] + manifest["probeTurns"])):
        raise ValueError("Java probe manifest differs from the fixed seeds/turns")
    if manifest.get("weightsSha256") != weights_sha or manifest.get("probeSha256") != probe_sha:
        raise ValueError("Java probe manifest does not match the supplied frozen weights/probe bytes")
    for key, expected in exclusion_hashes.items():
        if manifest.get(key) != expected:
            raise ValueError(f"Java probe manifest exclusion fingerprint differs: {key}")
    if len(rows) != 18:
        raise ValueError("Numerical parity requires all 18 fixed probe observations")
    tuples = []
    for row in rows:
        identity = tuple(row.get(key) for key in ("seed", "turn", "player"))
        if any(type(value) is not int for value in identity):
            raise ValueError("Probe observation seed/turn/player must be integers")
        if row.get("weightsSha256") != weights_sha:
            raise ValueError("Probe observation was produced by another/missing weight fingerprint")
        tuples.append(identity)
    if len(set(tuples)) != 18 or set(tuples) != PROBE_TUPLES:
        raise ValueError("Probe panel repeats or omits a fixed seed/turn/player observation")
    return {"complete": True, "expectedObservations": 18, "uniqueObservedTuples": 18,
            "probeSeeds": PROBE_SEEDS, "probeTurns": PROBE_TURNS,
            "allRowWeightsMatchBinary": True, "exclusionHashes": exclusion_hashes}


def compare(model, rows, tolerance=1e-4, relative_tolerance=1e-6):
    samples, total_values = [], 0
    for row in rows:
        if row.get("schemaVersion") not in (1, 2):
            raise ValueError("Expected v1-shared/v2 probe metadata schema")
        shared_metadata = dict(row, schemaVersion=1)
        batch, java_build, java_ink = probe_v1.load_probe(shared_metadata)
        build, ink, value = model.forward(batch)
        java_value = float(row["stateValue"])
        if not np.isfinite(java_value):
            raise ValueError("Probe stateValue is nonfinite")
        builds, inks = v1.choose_actions(build, ink, batch)
        build_error = float(np.abs(build[0] - java_build).max(initial=0))
        ink_error = float(np.abs(ink[0] - java_ink).max(initial=0))
        value_error = abs(float(value[0]) - java_value)
        numpy_joint = float(value[0] + v1.action_values(build, ink, builds, inks)[0])
        java_joint = np.float32(java_value)
        for cell in row["selectedCells"]:
            java_joint = np.float32(java_joint + java_build[cell])
        if row["selectedInk"] >= 0:
            java_joint = np.float32(java_joint + java_ink[row["selectedInk"]])
        if "selectedValue" in row:
            java_joint = float(row["selectedValue"])
        build_scaled = float((np.abs(build[0] - java_build) /
                              (tolerance + relative_tolerance * np.abs(java_build))).max(initial=0))
        ink_scaled = float((np.abs(ink[0] - java_ink) /
                            (tolerance + relative_tolerance * np.abs(java_ink))).max(initial=0))
        value_scaled = value_error / (tolerance + relative_tolerance * abs(java_value))
        joint_scaled = abs(numpy_joint - java_joint) / (tolerance + relative_tolerance * abs(java_joint))
        total_values += len(java_build) + len(java_ink) + 1
        samples.append({"seed": row["seed"], "turn": row["turn"], "player": row["player"],
                        "buildMaxAbs": build_error, "inkMaxAbs": ink_error, "valueMaxAbs": value_error,
                        "jointMaxAbs": float(abs(numpy_joint - java_joint)),
                        "buildScaledError": build_scaled, "inkScaledError": ink_scaled,
                        "valueScaledError": value_scaled, "jointScaledError": float(joint_scaled),
                        "buildAgrees": list(builds[0]) == row["selectedCells"],
                        "inkAgrees": inks[0] == row["selectedInk"],
                        "javaValue": java_value, "numpyValue": float(value[0]),
                        "javaCells": row["selectedCells"], "numpyCells": list(builds[0]),
                        "javaInk": row["selectedInk"], "numpyInk": inks[0]})
    if not samples:
        raise ValueError("No probe observations")
    result = {"schemaVersion": 2, "observations": len(samples), "qValues": total_values,
              "toleranceAbs": tolerance, "toleranceRelative": relative_tolerance, "samples": samples,
              "buildActionAgreements": sum(s["buildAgrees"] for s in samples),
              "inkActionAgreements": sum(s["inkAgrees"] for s in samples),
              "limitation": "sampled float32 agreement; untested near ties may differ"}
    for key in ("buildMaxAbs", "inkMaxAbs", "valueMaxAbs", "jointMaxAbs"):
        result[key] = max(sample[key] for sample in samples)
    for key in ("buildScaledError", "inkScaledError", "valueScaledError", "jointScaledError"):
        result[key] = max(sample[key] for sample in samples)
    result["passed"] = all(result[key] <= 1 for key in ("buildScaledError", "inkScaledError", "valueScaledError", "jointScaledError"))
    result["passed"] &= result["buildActionAgreements"] == len(samples) and result["inkActionAgreements"] == len(samples)
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--weights", required=True)
    parser.add_argument("--probe", required=True)
    parser.add_argument("--manifest", help="Defaults to manifest.json beside probe.jsonl")
    parser.add_argument("--out", required=True)
    parser.add_argument("--tolerance", type=float, default=1e-4)
    parser.add_argument("--rtol", type=float, default=1e-6)
    args = parser.parse_args(argv)
    if not np.isfinite(args.tolerance) or args.tolerance <= 0 or not np.isfinite(args.rtol) or args.rtol < 0:
        parser.error("absolute tolerance must be positive/finite; rtol nonnegative/finite")
    out, weights = Path(args.out), Path(args.weights)
    if out.exists():
        parser.error("Evidence output already exists")
    binary = weights.with_suffix(".bin") if weights.suffix == ".npz" else weights
    if not binary.is_file():
        parser.error("Java-readable BTKW0002 binary is required")
    probe = Path(args.probe)
    rows = [json.loads(line) for line in probe.read_text(encoding="utf-8-sig").splitlines() if line.strip()]
    manifest_path = Path(args.manifest) if args.manifest else probe.with_name("manifest.json")
    if not manifest_path.is_file():
        parser.error("A completed Java probe manifest is required")
    manifest = json.loads(manifest_path.read_text(encoding="utf-8-sig"))
    weights_sha, probe_sha = v1.file_sha256(binary), v1.file_sha256(probe)
    exclusion_paths = {
        "exclusionSha256": Path(__file__).resolve().parents[2] / "build/bench/indrill-q-v2-training-excluded.json",
        "probeExclusionSha256": Path(__file__).resolve().parents[2] / "build/bench/indrill-q-v2-probe-excluded.json",
    }
    if any(not path.is_file() for path in exclusion_paths.values()):
        parser.error("Both core-generated training/probe exclusion manifests are required")
    exclusion_hashes = {key: v1.file_sha256(path) for key, path in exclusion_paths.items()}
    integrity = validate_probe_integrity(manifest, rows, weights_sha, probe_sha, exclusion_hashes)
    model = v2.Model(np.random.default_rng(0))
    model.load(binary)
    result = compare(model, rows, args.tolerance, args.rtol)
    result["createdUtc"] = dt.datetime.now(dt.timezone.utc).isoformat()
    result["weights"] = {"path": str(binary), "sha256": weights_sha}
    result["probe"] = {"path": str(probe), "sha256": probe_sha}
    result["manifest"] = {"path": str(manifest_path), "sha256": v1.file_sha256(manifest_path), **integrity}
    checkpoint = binary.with_suffix(".npz")
    if checkpoint.exists():
        result["checkpoint"] = {"path": str(checkpoint), "sha256": v1.file_sha256(checkpoint)}
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, indent=2)
        stream.write("\n")
    print(json.dumps({key: result[key] for key in ("passed", "observations", "qValues", "buildMaxAbs",
                      "inkMaxAbs", "valueMaxAbs", "jointMaxAbs", "buildActionAgreements", "inkActionAgreements")}))
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
