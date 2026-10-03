"""Check numpy vs Java Q inference on raw-float32 evolving-board probes.

Java IndrillProbe reads the same BTKW0001 binary and writes probe.jsonl. This
check is independent of training/strength evaluation and never changes weights.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
from pathlib import Path
import sys

import train_q as q
import numpy as np


def load_probe(row):
    if row.get("schemaVersion") != 1:
        raise ValueError("Expected IndrillProbe schemaVersion=1")
    width, height, regions = (int(row[key]) for key in ("width", "height", "regionCount"))
    size = width * height
    if row["size"] != size or min(width, height, regions) < 1:
        raise ValueError("Probe map dimensions disagree")
    x = np.asarray(row["features"], dtype=np.float32)
    if x.shape != (size, q.INPUTS) or not np.isfinite(x).all():
        raise ValueError("Probe features must be finite raw float32 [size,64]")
    region_ids = np.asarray(row["regions"], dtype=np.int32)
    costs = np.asarray(row["costs"], dtype=np.uint8)
    build_cost = np.asarray(row["buildCost"], dtype=np.int32)
    legal_build = np.asarray(row["legalBuild"], dtype=bool)
    legal_ink = np.asarray(row["legalInk"], dtype=bool)
    if any(a.shape != (size,) for a in (region_ids, costs, build_cost, legal_build)):
        raise ValueError("Probe cell metadata has wrong length")
    if legal_ink.shape != (regions,) or not np.isin(costs, (1, 2, 3)).all():
        raise ValueError("Probe region mask or terrain cost is invalid")
    if (region_ids < 0).any() or (region_ids >= regions).any():
        raise ValueError("Probe has invalid region IDs")
    if not np.array_equal(build_cost, np.where(legal_build, costs, 0)):
        raise ValueError("Probe build-cost mask disagrees with terrain costs")
    builds = tuple(int(cell) for cell in row["selectedCells"])
    ink = int(row["selectedInk"])
    state = q.State(np.stack((x, x)), legal_build, legal_ink,
                    (builds, ()), (ink, -1), (0.0, 0.0), False)
    episode = q.Episode(f"{row['seed']}:{row['turn']}:{row['player']}",
                        width, height, regions, region_ids, costs, [state])
    expected_neighbors = np.where(episode.neighbor_mask, episode.neighbors, -1)
    if not np.array_equal(np.asarray(row["neighbors"]), expected_neighbors):
        raise ValueError("Probe neighbor topology disagrees with board dimensions")
    if not np.array_equal(np.asarray(row["regionSizes"]), episode.region_sizes):
        raise ValueError("Probe region counts disagree with cell metadata")
    java_build = np.asarray(row["buildQ"], dtype=np.float32)
    java_ink = np.asarray(row["inkQ"], dtype=np.float32)
    if java_build.shape != (size,) or java_ink.shape != (regions,):
        raise ValueError("Probe Q heads have invalid dimensions")
    if not np.isfinite(java_build).all() or not np.isfinite(java_ink).all():
        raise ValueError("Probe Q heads are nonfinite")
    transition = q.Transition(episode, 0, 0, 0, 0.0, 0.0)
    return q.Batch([transition]), java_build, java_ink


def compare(model, rows, tolerance=1e-4):
    samples = []
    values_compared, all_build, all_ink = 0, True, True
    for row in rows:
        batch, java_build, java_ink = load_probe(row)
        build, ink = model.forward(batch)
        build_delta = np.abs(build[0] - java_build)
        ink_delta = np.abs(ink[0] - java_ink)
        build_error, ink_error = float(build_delta.max(initial=0)), float(ink_delta.max(initial=0))
        build_relative = float((build_delta / np.maximum(np.abs(java_build), 1e-7)).max(initial=0))
        ink_relative = float((ink_delta / np.maximum(np.abs(java_ink), 1e-7)).max(initial=0))
        chosen_build, chosen_ink = q.choose_actions(build, ink, batch)
        build_agrees = list(chosen_build[0]) == row["selectedCells"]
        ink_agrees = chosen_ink[0] == row["selectedInk"]
        all_build &= build_agrees
        all_ink &= ink_agrees
        values_compared += len(java_build) + len(java_ink)
        samples.append({"seed": row["seed"], "turn": row["turn"], "player": row["player"],
                        "buildMaxAbs": build_error, "inkMaxAbs": ink_error,
                        "buildMaxRelative": build_relative, "inkMaxRelative": ink_relative,
                        "buildAgrees": build_agrees, "inkAgrees": ink_agrees,
                        "javaCells": row["selectedCells"], "numpyCells": list(chosen_build[0]),
                        "javaInk": row["selectedInk"], "numpyInk": chosen_ink[0]})
    if not samples:
        raise ValueError("Probe contains no board observations")
    build_max = max(s["buildMaxAbs"] for s in samples)
    ink_max = max(s["inkMaxAbs"] for s in samples)
    return {
        "passed": build_max <= tolerance and ink_max <= tolerance and all_build and all_ink,
        "toleranceAbs": tolerance, "observations": len(samples), "qValues": values_compared,
        "buildMaxAbs": build_max, "inkMaxAbs": ink_max,
        "buildMaxRelative": max(s["buildMaxRelative"] for s in samples),
        "inkMaxRelative": max(s["inkMaxRelative"] for s in samples),
        "buildActionAgreements": sum(s["buildAgrees"] for s in samples),
        "inkActionAgreements": sum(s["inkAgrees"] for s in samples),
        "scope": "raw-float32 sampled evolving board positions, both perspectives",
        "limitation": "sampled action agreement; floating-point near ties can differ on other positions",
        "samples": samples,
    }


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--weights", required=True)
    parser.add_argument("--probe", required=True)
    parser.add_argument("--out", required=True, help="Write-once evidence JSON")
    parser.add_argument("--tolerance", type=float, default=1e-4)
    args = parser.parse_args(argv)
    if not np.isfinite(args.tolerance) or args.tolerance <= 0:
        parser.error("tolerance must be finite and positive")
    out = Path(args.out)
    if out.exists():
        parser.error(f"Evidence already exists: {out}")
    weights = Path(args.weights)
    binary = weights.with_suffix(".bin") if weights.suffix == ".npz" else weights
    # Java reads the binary, while training resumes the associated checkpoint.
    # Model.load verifies exact parameter equality when the checkpoint exists.
    if not binary.is_file():
        parser.error("The Java-readable weight binary is required, even with --weights .npz")
    model = q.Model(np.random.default_rng(0))
    model.load(binary)
    probe = Path(args.probe)
    rows = []
    with probe.open("r", encoding="utf-8-sig") as stream:
        for line_number, line in enumerate(stream, 1):
            if line.strip():
                try:
                    rows.append(json.loads(line))
                except json.JSONDecodeError as error:
                    raise ValueError(f"Invalid probe JSON at line {line_number}") from error
    result = compare(model, rows, args.tolerance)
    result["createdUtc"] = dt.datetime.now(dt.timezone.utc).isoformat()
    result["weights"] = {"path": str(binary), "sha256": q.file_sha256(binary)}
    checkpoint = binary.with_suffix(".npz")
    if checkpoint.exists():
        result["checkpoint"] = {"path": str(checkpoint), "sha256": q.file_sha256(checkpoint)}
    result["probe"] = {"path": str(probe), "sha256": q.file_sha256(probe)}
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, indent=2)
        stream.write("\n")
    print(json.dumps({key: result[key] for key in ("passed", "observations", "qValues",
                      "buildMaxAbs", "inkMaxAbs", "buildActionAgreements", "inkActionAgreements")}))
    return 0 if result["passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
