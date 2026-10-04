"""Synthetic-only interoperability checks; never read a real teacher corpus."""
from __future__ import annotations

import copy
import hashlib
import json
from pathlib import Path
import sys

import numpy as np
import teacher_dataset as data


def rejects(row, mutate):
    altered = copy.deepcopy(row)
    mutate(altered)
    try:
        data.dataset_from_rows([altered])
    except (ValueError, KeyError):
        return
    raise AssertionError("Malformed synthetic observation was accepted")


def run(java_directory, output_directory):
    java_directory, output_directory = Path(java_directory), Path(output_directory)
    if output_directory.exists():
        raise ValueError("Refusing existing synthetic result directory")
    output_directory.mkdir(parents=True)
    row = json.loads((java_directory / "synthetic-rows.jsonl").read_text())
    java_result = json.loads((java_directory / "synthetic-java-result.json").read_text())
    assert java_result["syntheticOnly"] and java_result["passed"]
    assert data.modelinput_sha256(row) == java_result["inputSha256"] == row["inputSha256"]
    dataset = data.dataset_from_rows([row])
    assert dataset.panel_indices("A") == dataset.panel_indices("B") == [0]
    assert dataset.batch([0]).builds == [(2, 6)]
    assert dataset.batch([0]).inks == [3]
    assert dataset.items[0].player == 0 and dataset.items[0].reward == dataset.items[0].discount == 0
    assert np.signbit(dataset.rows[0]["features"][0, 0])
    positive_zero = copy.deepcopy(row); positive_zero["features"][0][0] = 0.0
    assert data.modelinput_sha256(positive_zero) != row["inputSha256"]
    rejects(row, lambda r: r.update(inputSha256="0" * 64))
    rejects(row, lambda r: r.update(teacherFrameIndex=-1))
    rejects(row, lambda r: r.update(targetCells=[6, 2]))
    rejects(row, lambda r: r.update(targetCells=[2, 2]))
    rejects(row, lambda r: r.update(targetCells=[0], acceptedPaint=1))
    rejects(row, lambda r: r.update(targetInk=0))
    rejects(row, lambda r: r.update(acceptedPaint=2))
    rejects(row, lambda r: r.update(legalBuild=[int(v) for v in r["legalBuild"]]))
    rejects(row, lambda r: r.update(split="holdout"))
    repeated = copy.deepcopy(row)
    repeated.update(observationId="2:0:0", gameId=2, targetCells=[], targetInk=-1, acceptedPaint=0)
    repeated_dataset = data.dataset_from_rows([row, repeated])
    collisions = data.collision_summary(repeated_dataset.rows, [0, 1])
    assert collisions["conflictGroups"] == 1 and collisions["exactJointCeilingCount"] == 1
    leaked = copy.deepcopy(repeated)
    leaked.update(split="holdout", panelTags=["holdout"], mapHash="different-map")
    try:
        data.dataset_from_rows([row, leaked])
    except ValueError:
        pass
    else:
        raise AssertionError("Exact input leakage accepted across different map hashes")
    fixture = output_directory / "dataset-fixture"
    fixture.mkdir()
    (fixture / "teacher.jsonl").write_text(json.dumps(row) + "\n", encoding="utf-8")
    selected = {k: row[k] for k in ("observationId", "gameId", "teacherSeat", "turn", "mapHash", "split", "panelTags", "cohortTags")}
    (fixture / "teacher-preflight.json").write_text(json.dumps({
        "schema": "indrill-capacity-preflight-v1", "syntheticOnly": True,
        "complete": True, "selection": [selected]}), encoding="utf-8")
    manifest = {"schema": data.DATA_SCHEMA, "syntheticOnly": True, "complete": True,
                "teacherJsonlSha256": hashlib.sha256((fixture / "teacher.jsonl").read_bytes()).hexdigest(),
                "preflightSha256": hashlib.sha256((fixture / "teacher-preflight.json").read_bytes()).hexdigest(),
                "rowCount": 1, "panelCounts": {"A": 1, "B": 1}}
    (fixture / "dataset-manifest.json").write_text(json.dumps(manifest), encoding="utf-8")
    assert len(data.load_dataset(fixture).items) == 1
    result = {"syntheticOnly": True, "passed": True, "javaPythonCanonicalHash": row["inputSha256"],
              "checks": ["raw_float32_signed_zero", "exact_decoder_coverage", "teacher_only_batch",
                         "invalid_input_and_target_rejection", "frame_provenance", "collision_ceiling",
                         "cross_split_input_leakage_rejection", "complete_dataset_manifest_loader"]}
    (output_directory / "result.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: loader_synthetic_checks.py SYNTHETIC_JAVA_DIR NEW_OUTPUT_DIR")
    run(sys.argv[1], sys.argv[2])
