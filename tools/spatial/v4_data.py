"""Lossless BTKSD001 corpus validation and disposable, disk-backed row caches."""
from __future__ import annotations

import gzip
import hashlib
import json
from pathlib import Path
import struct

from train_spatial import np, torch

MAGIC = b"BTKSD001"
ROW_DTYPE = np.dtype([
    ("planes", "<f4", (24, 20, 30)), ("towns", "<f4", (12, 49)), ("scalars", "<f4", (6,)),
    ("townCells", "<i4", (12,)), ("regionIds", "<i4", (600,)),
    ("buildMask", "u1", (601,)), ("disruptMask", "u1", (601,)),
    ("label", "<i4"), ("stage", "<i4"), ("turn", "<i4")], align=False)
ROW_BYTES = 63638
assert ROW_DTYPE.itemsize == ROW_BYTES
HEADER = MAGIC + struct.pack("<ii", ROW_BYTES, 0)
OPPONENTS = ("v01", "spatial-ppo-v2", "v09")
SPLITS = {"train": (12000000, 24), "validation": (12100000, 6)}


def digest_file(path):
    digest = hashlib.sha256()
    with Path(path).open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def read_json(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def local_file(root, relative):
    root = Path(root).resolve()
    path = (root / relative).resolve()
    if path == root or not path.is_relative_to(root) or not path.is_file():
        raise ValueError(f"Corpus file must exist inside its directory: {relative}")
    return path


def validate_layout(manifest, exclusions, exclusion_sha):
    if (manifest.get("schemaVersion") != 1 or manifest.get("format") != "BTKSD001"
            or manifest.get("mode") != "corpus" or manifest.get("status") != "complete"):
        raise ValueError("Only a complete fixed BTKSD001 corpus may be trained")
    if manifest["exclusionsSha256"] != exclusion_sha or manifest["excludedMapCount"] != len(exclusions):
        raise ValueError("Corpus exclusion lineage does not match its frozen index")
    games = manifest["games"]
    expected = {(split, start + i, seat): OPPONENTS[i % 3]
                for split, (start, count) in SPLITS.items() for i in range(count) for seat in (0, 1)}
    found, map_hashes, rows, turns = {}, {}, 0, 0
    names = set()
    for game in games:
        key = (game["split"], game["seed"], game["teacherSeat"])
        if key not in expected or key in found or game["opponentSpec"] != expected[key]:
            raise ValueError("Wrong/duplicate map split, seat or opponent in teacher corpus")
        if not game["terminal"] or game["turns"] <= 0 or game["rowCount"] != 4 * game["turns"]:
            raise ValueError("Incomplete game or missing conditional rows")
        if game["uncompressedBytes"] != 16 + game["rowCount"] * ROW_BYTES:
            raise ValueError("Incorrect inflated size declared for game")
        for name in (game["binary"], game["metadata"]):
            if name in names:
                raise ValueError("Two corpus games share an artifact path")
            names.add(name)
        map_key = (game["split"], game["seed"])
        map_hash = game["mapHash"]
        if map_hash in exclusions:
            raise ValueError("Teacher map overlaps prior/development exposure")
        if map_key in map_hashes and map_hashes[map_key] != map_hash:
            raise ValueError("Paired seats do not share the same map")
        map_hashes[map_key] = map_hash
        found[key] = game
        rows += game["rowCount"]
        turns += game["turns"]
    if set(found) != set(expected) or len(set(map_hashes.values())) != 30:
        raise ValueError("Expected exactly 30 disjoint maps and both teacher seats per map")
    manifest_maps = {(item["split"], item["seed"]): item["mapHash"] for item in manifest["maps"]}
    if len(manifest["maps"]) != 30 or manifest_maps != map_hashes:
        raise ValueError("Manifest map list disagrees with its game lineage")
    if rows != manifest["rows"] or turns != manifest["totalTurns"]:
        raise ValueError("Corpus total rows/turns mismatch")
    return {"rows": rows, "turns": turns, "maps": 30, "games": 60,
            "splits": {split: {"games": sum(g["split"] == split for g in games),
                               "maps": count, "rows": sum(g["rowCount"] for g in games if g["split"] == split)}
                       for split, (_, count) in SPLITS.items()}}


def validate_metadata(game, metadata):
    for key in ("split", "seed", "mapHash", "teacherSeat", "opponentSpec", "binary", "rowCount"):
        if metadata[key] != game[key]:
            raise ValueError(f"Game metadata lineage mismatch: {key}")
    if (metadata["teacherSpec"] != "v09" or metadata["status"] != "complete"
            or not metadata["terminal"] or metadata["turnCount"] != game["turns"]
            or len(metadata["turns"]) != game["turns"]):
        raise ValueError("Teacher game metadata is incomplete")
    if "id" in game and metadata["id"] != game["id"]:
        raise ValueError("Game metadata id mismatch")
    for turn, audit in enumerate(metadata["turns"]):
        if audit["turn"] != turn or audit["rowStart"] != turn * 4:
            raise ValueError("Teacher metadata turns/rows are not contiguous")
        cells, disrupt = audit["teacherExpanded"]["cells"], audit["teacherExpanded"]["disrupt"]
        if (len(cells) > 3 or len(set(cells)) != len(cells) or any(not 0 <= c < 600 for c in cells)
                or not -1 <= disrupt < 600):
            raise ValueError("Expanded teacher plan cannot be represented by four decisions")
        labels = cells + [600] * (3 - len(cells)) + [600 if disrupt == -1 else disrupt]
        if audit["labels"] != labels:
            raise ValueError("Hard labels disagree with ordered core-expanded teacher plan")
        if audit["prefixes"] != [cells[:min(stage, len(cells))] for stage in range(4)]:
            raise ValueError("Teacher prefix/order mismatch")
        if any(audit[name] != audit[expanded] for name, expanded in
               (("acceptedTeacher", "teacherExpanded"), ("acceptedOpponent", "opponentExpanded"))):
            raise ValueError("Core accepted plan differs from the declared expanded plan")
        if len(audit["paintBefore"]) != 4 or len(audit["stoppedBefore"]) != 4:
            raise ValueError("Conditional prefix audit is incomplete")
        if not audit["teacherActions"] or not audit["opponentActions"] or len(audit["preStateSha256"]) != 64:
            raise ValueError("Missing original actions or simultaneous pre-state identity")
        calls = audit["calls"]
        expected_calls = {(game["teacherSeat"], "teacher", "v09"),
                          (1 - game["teacherSeat"], "opponent", game["opponentSpec"])}
        if len(calls) != 2 or {(c["me"], c["role"], c["spec"]) for c in calls} != expected_calls:
            raise ValueError("Teacher/opponent call lineage mismatch")
        if any(not c["complete"] or not c["legal"] or c["budgetMs"] != 60000
               or not np.isfinite(c["elapsedMs"]) or not 0 <= c["elapsedMs"] < 60000 for c in calls):
            raise ValueError("Teacher/opponent call failed its fixed completion regime")
        if bool(audit["terminalAfter"]) != (turn == game["turns"] - 1):
            raise ValueError("Game rows cross a real terminal boundary")
    if metadata["score"] != metadata["turns"][-1]["scoreAfter"]:
        raise ValueError("Terminal score metadata mismatch")


def validate_rows(records, game, metadata):
    if len(records) != game["rowCount"]:
        raise ValueError("Game row count mismatch")
    labels = np.asarray([row["labels"] for row in metadata["turns"]], dtype=np.int32).reshape(-1)
    for start in range(0, len(records), 128):
        rows = records[start:start + 128]
        index = np.arange(start, start + len(rows))
        if not np.array_equal(rows["stage"], index % 4) or not np.array_equal(rows["turn"], index // 4):
            raise ValueError("Binary conditional stage/turn order mismatch")
        if not np.array_equal(rows["label"], labels[start:start + len(rows)]):
            raise ValueError("Binary hard labels differ from teacher action audit")
        for name in ("planes", "towns", "scalars"):
            if not np.isfinite(rows[name]).all():
                raise ValueError("Non-finite feature in teacher corpus")
        for name in ("townCells", "regionIds"):
            if not ((rows[name] >= -1) & (rows[name] < 600)).all():
                raise ValueError("Out-of-range padded board index")
        for name in ("buildMask", "disruptMask"):
            if not ((rows[name] == 0) | (rows[name] == 1)).all() or not rows[name].any(axis=1).all():
                raise ValueError("Malformed or empty legal mask")
        active = np.where((rows["stage"] == 3)[:, None], rows["disruptMask"], rows["buildMask"])
        if not ((rows["label"] >= 0) & (rows["label"] <= 600)).all():
            raise ValueError("Hard label outside policy output range")
        if not active[np.arange(len(rows)), rows["label"]].all():
            raise ValueError("Illegal teacher target; no silent filtering is permitted")


def inflate_game(binary, output, game):
    digest = hashlib.sha256()
    expected = game["uncompressedBytes"]
    total = 0
    with gzip.open(binary, "rb") as source:
        header = source.read(16)
        if header != HEADER:
            raise ValueError("Unsupported/corrupt BTKSD001 binary header")
        digest.update(header)
        total += 16
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            total += len(chunk)
            if total > expected:
                raise ValueError("Inflated game exceeds its declared byte count")
            digest.update(chunk)
            output.write(chunk)
    if total != expected or digest.hexdigest() != game["rawSha256"]:
        raise ValueError("Inflated game size or raw SHA256 mismatch")


def prepare_cache(corpus_dir, cache_dir, exclusion_path):
    corpus, cache = Path(corpus_dir), Path(cache_dir)
    if cache.exists():
        raise FileExistsError("Refusing to reuse a prepared or partial distillation cache")
    manifest_path = corpus / "manifest.json"
    manifest = read_json(manifest_path)
    exclusions = set(read_json(exclusion_path)["gamesPerMap"])
    summary = validate_layout(manifest, exclusions, digest_file(exclusion_path))
    cache.mkdir(parents=True, exist_ok=False)
    report = {"status": "preparing", "format": "BTKSD001", "rowBytes": ROW_BYTES,
              "corpus_dir": str(corpus.resolve()), "manifest_sha256": digest_file(manifest_path),
              "exclusions_path": str(Path(exclusion_path).resolve()), "exclusions_sha256": digest_file(exclusion_path),
              "summary": summary, "splits": {}, "files": {}, "games": []}
    try:
        for split in SPLITS:
            filename = split + ".bin"
            games = sorted((g for g in manifest["games"] if g["split"] == split), key=lambda g: (g["seed"], g["teacherSeat"]))
            offset = 0
            with (cache / filename).open("xb") as stream:
                for game in games:
                    binary, metadata_path = local_file(corpus, game["binary"]), local_file(corpus, game["metadata"])
                    if digest_file(binary) != game["sha256"] or digest_file(metadata_path) != game["metadataSha256"]:
                        raise ValueError("Compressed corpus or action audit hash mismatch")
                    metadata = read_json(metadata_path)
                    validate_metadata(game, metadata)
                    inflate_game(binary, stream, game)
                    report["files"][str(binary.resolve())] = game["sha256"]
                    report["files"][str(metadata_path.resolve())] = game["metadataSha256"]
                    report["games"].append({**game, "cacheRowStart": offset})
                    offset += game["rowCount"]
            records = np.memmap(cache / filename, dtype=ROW_DTYPE, mode="r")
            try:
                for game in (g for g in report["games"] if g["split"] == split):
                    start = game["cacheRowStart"]
                    validate_rows(records[start:start + game["rowCount"]], game, read_json(corpus / game["metadata"]))
            finally:
                records._mmap.close()
            report["splits"][split] = {"file": filename, "rows": offset, "bytes": (cache / filename).stat().st_size,
                                        "sha256": digest_file(cache / filename)}
        report["status"] = "complete"
    except Exception as exc:
        report.update(status="failed", failure={"type": type(exc).__name__, "message": str(exc)})
        (cache / "cache.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
        raise
    (cache / "cache.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    return report


def verify_cache(cache_dir):
    cache = Path(cache_dir)
    report = read_json(cache / "cache.json")
    if report["status"] != "complete" or report["rowBytes"] != ROW_BYTES:
        raise ValueError("Prepared corpus cache is incomplete")
    if digest_file(Path(report["corpus_dir"]) / "manifest.json") != report["manifest_sha256"]:
        raise ValueError("Corpus manifest changed after validation")
    if digest_file(report["exclusions_path"]) != report["exclusions_sha256"]:
        raise ValueError("Corpus exclusion index changed")
    for filename, expected in report["files"].items():
        if digest_file(filename) != expected:
            raise ValueError("Compressed corpus or teacher action metadata changed")
    for part in report["splits"].values():
        path = local_file(cache, part["file"])
        if path.stat().st_size != part["rows"] * ROW_BYTES or digest_file(path) != part["sha256"]:
            raise ValueError("Disk-backed training cache changed")
    return report


def row_tensors(rows):
    floats = ("planes", "towns", "scalars")
    integers = ("townCells", "regionIds")
    result = {name: torch.from_numpy(np.array(rows[name], dtype=np.float32, copy=True)) for name in floats}
    result.update({name: torch.from_numpy(np.array(rows[name], dtype=np.int64, copy=True)) for name in integers})
    result.update({name: torch.from_numpy(np.array(rows[name], dtype=bool, copy=True)) for name in ("buildMask", "disruptMask")})
    return result, torch.from_numpy(np.array(rows["label"], dtype=np.int64, copy=True)), torch.from_numpy(np.array(rows["stage"], dtype=np.int64, copy=True))


def parity_observations(rows):
    return [{name: row[name].astype(bool).tolist() if name.endswith("Mask") else row[name].reshape(-1).tolist()
             for name in ("planes", "towns", "scalars", "townCells", "regionIds", "buildMask", "disruptMask")}
            for row in rows]
