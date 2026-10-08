"""Compare saved Java late-panel outputs to an exact saved PyTorch checkpoint."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

from train_spatial import SpatialNet, np, tensor_observations, torch
from v2_support import weight_hash


def compare_panel(model, panel):
    expected_hash = weight_hash(model)
    if panel["weightsSha256"] != expected_hash:
        raise AssertionError("Java panel weights do not match the supplied checkpoint")
    rows = panel["rows"]
    if not rows:
        raise AssertionError("No observed panel positions to compare")
    errors = {"build": 0.0, "disrupt": 0.0, "value": 0.0}
    mismatches = []
    for start in range(0, len(rows), 32):
        batch = rows[start:start + 32]
        with torch.no_grad():
            py_build, py_ink, py_values = [out.numpy() for out in model(tensor_observations([r["observation"] for r in batch]))]
        if not all(np.isfinite(out).all() for out in (py_build, py_ink, py_values)):
            raise AssertionError("Non-finite Python panel output")
        for i, row in enumerate(batch):
            java = row["java"]
            java_build = np.asarray(java["buildLogits"], dtype=np.float32)
            java_ink = np.asarray(java["disruptLogits"], dtype=np.float32)
            if (java_build.shape != (601,) or java_ink.shape != (601,)
                    or not np.isfinite(java_build).all() or not np.isfinite(java_ink).all()
                    or not np.isfinite(java["value"])):
                raise AssertionError("Malformed or non-finite Java panel output")
            errors["build"] = max(errors["build"], float(np.abs(java_build - py_build[i]).max()))
            errors["disrupt"] = max(errors["disrupt"], float(np.abs(java_ink - py_ink[i]).max()))
            errors["value"] = max(errors["value"], abs(float(java["value"]) - float(py_values[i])))
            is_ink = row["stage"] == 3
            mask = np.asarray(row["observation"]["disruptMask" if is_ink else "buildMask"], dtype=bool)
            if not mask.any():
                raise AssertionError("Empty legal mask in saved panel")
            py_choice = int(np.where(mask, py_ink[i] if is_ink else py_build[i], -np.inf).argmax())
            java_choice = int(np.where(mask, java_ink if is_ink else java_build, -np.inf).argmax())
            if py_choice != java["selected"] or java_choice != java["selected"]:
                mismatches.append({"row": start + i, "seed": row["seed"], "turn": row["turn"],
                                   "me": row["me"], "stage": row["stage"], "python_selected": py_choice,
                                   "java_selected": java["selected"], "java_logits_selected": java_choice})
    finite = all(np.isfinite(value) for value in errors.values())
    return {"label": panel["label"], "weights_sha256": expected_hash, "observations": len(rows),
            "seeds": sorted({r["seed"] for r in rows}), "turns": sorted({r["turn"] for r in rows}),
            "observations_by_turn": {str(turn): sum(r["turn"] == turn for r in rows) for turn in sorted({r["turn"] for r in rows})},
            "max_abs_error": errors, "tolerance": 1e-4, "greedy_mismatches": mismatches,
            "passed": finite and max(errors.values()) <= 1e-4 and not mismatches}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--checkpoint", required=True)
    parser.add_argument("--panel", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()
    target = Path(args.out)
    if target.exists():
        raise FileExistsError("Refusing to overwrite an existing parity report")
    torch.set_num_threads(2)
    saved = torch.load(args.checkpoint, weights_only=False, map_location="cpu")
    model = SpatialNet()
    model.load_state_dict(saved["model"])
    report = compare_panel(model, json.loads(Path(args.panel).read_text(encoding="utf-8-sig")))
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(report, indent=2, allow_nan=False) + "\n", encoding="utf-8")
    print(json.dumps(report))
    if not report["passed"]:
        raise SystemExit("Python/Java late-panel parity failed")
