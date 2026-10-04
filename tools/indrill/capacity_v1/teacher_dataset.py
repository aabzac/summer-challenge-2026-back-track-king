"""Validated teacher-only diagnostic adapter for frozen train_q.

No module import reads a real dataset. See schema-v1.md for byte-level input identity.
"""
from __future__ import annotations

from collections import Counter, defaultdict
from dataclasses import dataclass
import hashlib
import json
from pathlib import Path
import struct
import sys

import numpy as np

ROOT = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(ROOT / "tools" / "indrill"))
import train_q as q

OBS_SCHEMA = "indrill-capacity-observation-v1"
DATA_SCHEMA = "indrill-capacity-dataset-v1"


def modelinput_sha256(row):
    digest = hashlib.sha256(b"BTKCI001")
    for key in ("width", "height", "size", "regionCount", "featureCount", "paintBudget"):
        digest.update(struct.pack(">i", int(row[key])))
    for key, dtype in (("regions", ">i4"), ("costs", ">i4"), ("legalBuild", "u1"),
                       ("buildCost", ">i4"), ("legalInk", "u1"), ("neighbors", ">i4"),
                       ("regionSizes", ">i4"), ("features", ">f4")):
        values = np.asarray(row[key], dtype=dtype).ravel(order="C")
        digest.update(struct.pack(">i", values.size))
        digest.update(values.tobytes(order="C"))
    return digest.hexdigest()


def collision_summary(rows, indices):
    groups = defaultdict(Counter)
    for i in indices:
        row = rows[int(i)]
        label = json.dumps([list(row["targetCells"]), int(row["targetInk"])], separators=(",", ":"))
        groups[row["inputSha256"]][label] += 1
    count = sum(sum(labels.values()) for labels in groups.values())
    ceiling = sum(max(labels.values()) for labels in groups.values())
    detail = [{"inputSha256": key, "count": sum(labels.values()),
               "labelCounts": dict(sorted(labels.items())), "maxLabelCount": max(labels.values())}
              for key, labels in sorted(groups.items()) if sum(labels.values()) > 1]
    return {"observations": count, "uniqueInputs": len(groups),
            "conflictGroups": sum(len(labels) > 1 for labels in groups.values()),
            "exactJointCeilingCount": ceiling,
            "exactJointCeilingRate": ceiling / count if count else None, "groups": detail}


def _int_array(row, name, shape):
    raw = np.asarray(row[name])
    if raw.shape != shape or raw.dtype.kind not in "iu" or raw.dtype.kind == "b":
        raise ValueError(f"{name}: expected integer array {shape}, got {raw.shape}/{raw.dtype}")
    if (raw < -2**31).any() or (raw >= 2**31).any():
        raise ValueError(f"{name}: out of int32 range")
    return raw.astype(np.int32)


def _bool_array(row, name, shape):
    raw = np.asarray(row[name])
    if raw.shape != shape or raw.dtype.kind != "b":
        raise ValueError(f"{name}: expected boolean array {shape}")
    return raw


def _adapt(row):
    row = dict(row)
    if row.get("schema") != OBS_SCHEMA:
        raise ValueError("Wrong observation schema")
    for key in ("width", "height", "size", "regionCount", "featureCount", "paintBudget", "gameId", "teacherSeat", "turn", "teacherFrameIndex"):
        if type(row.get(key)) is not int:
            raise ValueError(f"{key}: expected integer")
    w, h, n, r = (row[k] for k in ("width", "height", "size", "regionCount"))
    if min(w, h, n, r) < 1 or n != w*h or r > n or row["featureCount"] != 64 or row["paintBudget"] != 3:
        raise ValueError("Invalid observation dimensions")
    if row["teacherSeat"] not in (0, 1) or row["turn"] < 0 or row["teacherFrameIndex"] < 1:
        raise ValueError("Invalid teacher identity/frame provenance")
    if row["observationId"] != f"{row['gameId']}:{row['teacherSeat']}:{row['turn']}":
        raise ValueError("Observation identity disagrees with provenance")
    if row["split"] not in ("train", "holdout"):
        raise ValueError("Invalid split")
    if not row["panelTags"] or len(set(row["panelTags"])) != len(row["panelTags"]):
        raise ValueError("Missing/repeated panel tags")
    allowed = {"A", "B"} if row["split"] == "train" else {"holdout"}
    if not set(row["panelTags"]).issubset(allowed):
        raise ValueError("Panel tags cross train/holdout boundary")
    x = np.asarray(row["features"], dtype=np.float32)
    if x.shape != (n, 64) or not np.isfinite(x).all():
        raise ValueError("Features must be finite float32 [size,64]")
    arrays = {"regions": _int_array(row, "regions", (n,)), "costs": _int_array(row, "costs", (n,)),
              "buildCost": _int_array(row, "buildCost", (n,)), "neighbors": _int_array(row, "neighbors", (n, 4)),
              "regionSizes": _int_array(row, "regionSizes", (r,)),
              "legalBuild": _bool_array(row, "legalBuild", (n,)), "legalInk": _bool_array(row, "legalInk", (r,))}
    if (arrays["regions"] < 0).any() or (arrays["regions"] >= r).any() or not np.isin(arrays["costs"], (1, 2, 3)).all():
        raise ValueError("Invalid region IDs/costs")
    if not np.array_equal(arrays["buildCost"], np.where(arrays["legalBuild"], arrays["costs"], 0)):
        raise ValueError("Build cost disagrees with exported legality/cost")
    cells = tuple(row["targetCells"])
    if any(type(c) is not int for c in cells) or tuple(sorted(set(cells))) != cells:
        raise ValueError("Target cells must be sorted unique integers")
    if any(c < 0 or c >= n or not arrays["legalBuild"][c] for c in cells):
        raise ValueError("Illegal teacher cell")
    paint = sum(int(arrays["costs"][c]) for c in cells)
    if paint > 3 or paint != row["acceptedPaint"]:
        raise ValueError("Invalid accepted paint")
    ink = row["targetInk"]
    if type(ink) is not int or ink < -1 or ink >= r or (ink >= 0 and not arrays["legalInk"][ink]):
        raise ValueError("Illegal teacher ink target")
    if row["hadSkippedActions"] != bool(row["skippedActions"]):
        raise ValueError("Skipped-action flag disagrees with evidence")
    row.update(arrays); row["features"] = x
    if modelinput_sha256(row) != row["inputSha256"]:
        raise ValueError("Canonical input hash mismatch")
    state = q.State(np.broadcast_to(x, (2, n, 64)), arrays["legalBuild"], arrays["legalInk"],
                    (cells, ()), (ink, -1), (0.0, 0.0), False)
    episode = q.Episode(row["observationId"], w, h, r, arrays["regions"], arrays["costs"], [state])
    if not np.array_equal(arrays["neighbors"], np.where(episode.neighbor_mask, episode.neighbors, -1)):
        raise ValueError("Exported neighbor topology mismatch")
    if not np.array_equal(arrays["regionSizes"], episode.region_sizes):
        raise ValueError("Exported region sizes mismatch")
    oracle_build = np.full(n, -1, dtype=np.float32); oracle_build[list(cells)] = 1
    oracle_ink = np.full(r, -1, dtype=np.float32)
    if ink >= 0: oracle_ink[ink] = 1
    item = q.Transition(episode, 0, 0, 0, 0.0, 0.0)
    result = q.choose_actions(oracle_build[None], oracle_ink[None], q.Batch([item]))
    if result != ([cells], [ink]):
        raise ValueError("Accepted teacher target is not available to frozen decoder")
    return row, item


@dataclass
class Dataset:
    rows: list
    items: list
    panels: dict
    manifest: dict

    def batch(self, indices):
        return q.Batch([self.items[int(i)] for i in indices])

    def panel_indices(self, name):
        return list(self.panels.get(name, []))


def dataset_from_rows(rows, manifest=None):
    adapted, items, panels, seen = [], [], defaultdict(list), set()
    for raw in rows:
        row, item = _adapt(raw)
        if row["observationId"] in seen:
            raise ValueError("Duplicate observation tuple")
        seen.add(row["observationId"])
        for panel in row["panelTags"]: panels[panel].append(len(adapted))
        adapted.append(row); items.append(item)
    train_maps = {r["mapHash"] for r in adapted if r["split"] == "train"}
    hold_maps = {r["mapHash"] for r in adapted if r["split"] == "holdout"}
    train_inputs = {r["inputSha256"] for r in adapted if r["split"] == "train"}
    hold_inputs = {r["inputSha256"] for r in adapted if r["split"] == "holdout"}
    if train_maps & hold_maps or train_inputs & hold_inputs:
        raise ValueError("Training/holdout map or model-input overlap")
    info = dict(manifest or {})
    info["collisionSummary"] = {key: collision_summary(adapted, ids) for key, ids in panels.items()}
    # Local import avoids any corpus work at module load and keeps the checker independent.
    from representation_certificates import certificate_report
    info["representationCertificates"] = certificate_report(adapted)
    return Dataset(adapted, items, dict(panels), info)


def load_dataset(directory, require_complete=True):
    directory = Path(directory)
    manifest = json.loads((directory / "dataset-manifest.json").read_text(encoding="utf-8"))
    if manifest.get("schema") != DATA_SCHEMA or require_complete and manifest.get("complete") is not True:
        raise ValueError("Dataset is incomplete or wrong schema")
    source = directory / "teacher.jsonl"
    digest = hashlib.sha256()
    with source.open("rb") as stream:
        while chunk := stream.read(1 << 20): digest.update(chunk)
    if digest.hexdigest() != manifest["teacherJsonlSha256"]:
        raise ValueError("Dataset bytes differ from completion manifest")
    plan = directory / "teacher-preflight.json"
    if hashlib.sha256(plan.read_bytes()).hexdigest() != manifest["preflightSha256"]:
        raise ValueError("Dataset preflight bytes differ from manifest")
    preflight = json.loads(plan.read_text(encoding="utf-8"))
    if preflight.get("schema") != "indrill-capacity-preflight-v1" or preflight.get("complete") is not True:
        raise ValueError("Preflight incomplete or wrong schema")
    with source.open(encoding="utf-8") as stream:
        data = dataset_from_rows((json.loads(line) for line in stream if line.strip()), manifest)
    if len(data.rows) != manifest["rowCount"] or {k: len(v) for k, v in data.panels.items()} != manifest["panelCounts"]:
        raise ValueError("Dataset row/panel counts differ from manifest")
    expected = {r["observationId"]: r for r in preflight["selection"]}
    if set(expected) != {r["observationId"] for r in data.rows}:
        raise ValueError("Dataset does not implement exact preflight selection")
    for row in data.rows:
        selected = expected[row["observationId"]]
        for key in ("gameId", "teacherSeat", "turn", "mapHash", "split", "panelTags", "cohortTags"):
            if row[key] != selected[key]:
                raise ValueError(f"Dataset/preflight mismatch in {key}")
    return data
