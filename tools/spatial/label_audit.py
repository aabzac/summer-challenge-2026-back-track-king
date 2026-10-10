"""Bounded descriptive audit of frozen hard labels and saved Java comparisons.

No model updates, game simulation, new teacher calls or alternative objectives.
"""
from __future__ import annotations

import argparse
from collections import Counter
import hashlib
import itertools
import json
import math
from pathlib import Path
import re
import time

from train_spatial import ROOT, np
from v4_data import (ROW_DTYPE, digest_file, local_file, read_json, validate_layout,
                     validate_metadata, validate_rows, verify_cache)
from v4_distill import verify_freeze

MODELS = ("spatial-ppo-v4", "spatial-ppo-v9")
WEIGHTS = {"spatial-ppo-v4": "89de11f4239844c55c6e61c987ee2443fd5d79a1c71af782d2686e836ec6f3ef",
           "spatial-ppo-v9": "fd616a7f454792ec47d3cfc8e5a10f63becfb889d06dbd64e0dea8bf053b4cb6"}
INPUT_BYTES = ROW_DTYPE.fields["label"][1]
SLICES = ("all", "unforced", "build", "disrupt", "build_unforced", "disrupt_unforced",
          *(f"stage{stage}" for stage in range(4)), *(f"stage{stage}_unforced" for stage in range(4)))


def require(condition, message):
    if not condition:
        raise ValueError(message)


def strict_json(text):
    def invalid(value):
        raise ValueError(f"Non-finite JSON constant: {value}")
    return json.loads(text, parse_constant=invalid)


def input_bytes(row):
    """All actual observation fields, both masks, and stage; no label/raw turn."""
    return row.tobytes()[:INPUT_BYTES] + np.asarray(row["stage"], dtype="<i4").tobytes()


def selected_slice(stage, unforced, name):
    base = name.removesuffix("_unforced")
    if (name == "unforced" or name.endswith("_unforced")) and not unforced:
        return False
    return (base in ("all", "unforced") or base == "build" and stage < 3
            or base == "disrupt" and stage == 3 or base == f"stage{stage}")


def collision_audit(mappings, lineage):
    """Keep only hashes/provenance; byte-compare every repeated digest via source rows."""
    groups = {}
    for split, rows in mappings.items():
        require(split in ("train", "validation") and rows.dtype == ROW_DTYPE, "Unexpected row split/dtype")
        require(len(rows) == len(lineage[split]), "Collision row provenance coverage mismatch")
        for index, row in enumerate(rows):
            stage, label = int(row["stage"]), int(row["label"])
            require(stage in range(4) and label in range(601), "Invalid collision stage/label")
            mask = row["disruptMask" if stage == 3 else "buildMask"]
            require(np.isin(mask, (0, 1)).all() and bool(mask[label]), "Malformed mask or illegal collision label")
            unforced = int(mask.sum()) > 1
            payload = input_bytes(row)
            digest = hashlib.sha256(payload).hexdigest()
            if digest in groups:
                first_split, first_index, _ = groups[digest]["members"][0]
                require(payload == input_bytes(mappings[first_split][first_index]), "SHA256 collision between unequal observed inputs")
            else:
                groups[digest] = {"stage": stage, "unforced": unforced, "members": []}
            groups[digest]["members"].append((split, index, label))
    summary = {}
    for split in (*mappings, "all"):
        summary[split] = {}
        for category in SLICES:
            counts = Counter()
            for group in groups.values():
                if not selected_slice(group["stage"], group["unforced"], category):
                    continue
                labels = Counter(label for member_split, _, label in group["members"]
                                 if split == "all" or member_split == split)
                if not labels:
                    continue
                size = sum(labels.values())
                counts.update(rows=size, unique_inputs=1, majority_correct=max(labels.values()))
                if size > 1:
                    counts.update(duplicate_groups=1, rows_in_duplicate_groups=size)
                if len(labels) > 1:
                    counts.update(conflicting_groups=1, rows_in_conflicting_groups=size,
                                  minority_label_rows=size - max(labels.values()))
            keys = ("rows", "unique_inputs", "majority_correct", "duplicate_groups", "rows_in_duplicate_groups",
                    "conflicting_groups", "rows_in_conflicting_groups", "minority_label_rows")
            summary[split][category] = {key: counts[key] for key in keys}
            summary[split][category]["empirical_majority_label_agreement_ceiling"] = (
                counts["majority_correct"] / counts["rows"] if counts["rows"] else None)
    evidence, cross_split = [], 0
    for digest, group in groups.items():
        if len(group["members"]) < 2:
            continue
        labels = Counter(label for _, _, label in group["members"])
        mixed = len({split for split, _, _ in group["members"]}) > 1
        cross_split += int(mixed)
        evidence.append({"input_sha256": digest, "stage": group["stage"], "unforced": group["unforced"],
                         "cross_split": mixed, "conflicting": len(labels) > 1,
                         "label_counts": {str(label): count for label, count in sorted(labels.items())},
                         "rows": [{"split": split, "cache_row": index, "label": label, **lineage[split][index]}
                                  for split, index, label in group["members"]]})
    return {"input_fields": [name for name in ROW_DTYPE.names if name not in ("label", "turn")],
            "identity": "SHA256 plus exact byte verification for repeated digests; includes explicit stage",
            "excluded_fields": ["label", "raw turn bookkeeping", "game/split/seat metadata"],
            "cross_split_duplicate_groups": cross_split, "groups": summary,
            "interpretation": "Finite recorded-multiset hard-label ceiling only; byte identity is conservative, not global learnability or model capacity"}, evidence


def load_corpus(cache):
    cache = Path(cache)
    data = verify_cache(cache)
    old = read_json(ROOT / "docs/evidence/spatial-ppo-v4-training.json")
    require(old["status"] == "complete" and data == old["config"]["data"]
            and digest_file(cache / "cache.json") == old["config"]["cache_sha256"], "Audit requires the original complete V4 cache lineage")
    corpus = Path(data["corpus_dir"])
    manifest = read_json(corpus / "manifest.json")
    exclusions = set(read_json(data["exclusions_path"])["gamesPerMap"])
    summary = validate_layout(manifest, exclusions, data["exclusions_sha256"])
    require(summary["rows"] == 22640 and summary["turns"] == 5660, "Fixed corpus coverage changed")
    source_games = {game["id"]: game for game in manifest["games"]}
    require(len(source_games) == 60 and len(data["games"]) == 60, "Expected all 60 original games")
    mappings, metadata, lineage = {}, {}, {}
    try:
        for split, part in data["splits"].items():
            mappings[split] = np.memmap(cache / part["file"], dtype=ROW_DTYPE, mode="r")
            lineage[split] = []
            games = sorted((g for g in data["games"] if g["split"] == split), key=lambda g: g["cacheRowStart"])
            at = 0
            for game in games:
                require(game["id"] in source_games and {k: v for k, v in game.items() if k != "cacheRowStart"} == source_games[game["id"]],
                        "Cache game differs from original corpus manifest")
                require(game["cacheRowStart"] == at and game["id"] not in metadata, "Duplicate/gapped cached game coverage")
                meta = read_json(local_file(corpus, game["metadata"]))
                validate_metadata(game, meta)
                validate_rows(mappings[split][at:at + game["rowCount"]], game, meta)
                metadata[game["id"]] = meta
                lineage[split].extend({"game": game["id"], "seed": game["seed"], "teacher_seat": game["teacherSeat"],
                                       "turn": local // 4, "stage": local % 4}
                                      for local in range(game["rowCount"]))
                at += game["rowCount"]
            require(at == len(mappings[split]) == part["rows"], "Missing/extra cache rows")
        require(set(metadata) == set(source_games), "Missing/extra corpus game metadata")
        return data, manifest, mappings, metadata, lineage
    except Exception:
        for rows in mappings.values():
            rows._mmap.close()
        raise


def probability(value, name):
    require(type(value) in (int, float) and math.isfinite(value) and -1e-12 <= value <= 1 + 1e-12,
            f"Invalid probability: {name}")
    return max(0., min(1., float(value)))


def hash_string(value):
    require(isinstance(value, str) and re.fullmatch(r"[0-9a-f]{64}", value), "Malformed SHA256 identity")
    return value


def pair(value, name):
    require(isinstance(value, list) and len(value) == 2
            and all(type(item) is int and item >= 0 for item in value), f"Malformed {name} pair")
    return value


def bool_equal(value, expected, name):
    require(type(value) is bool and value == expected, f"Inconsistent {name} flag")


def checked_cells(value):
    require(isinstance(value, list) and len(value) <= 3 and len(set(value)) == len(value)
            and all(type(cell) is int and 0 <= cell < 600 for cell in value), "Malformed build cells")
    return value


def plan_comparison(teacher, own):
    """Compare recorded facts only; legality and successor simulation belong to core."""
    cells = checked_cells(own["cells"])
    require(type(own["disrupt"]) is int and 0 <= own["disrupt"] <= 600, "Malformed disruption action")
    require(type(own["cost"]) is int and 0 <= own["cost"] <= 3, "Malformed paint cost")
    require(own["complete"] is True and type(own["elapsedMs"]) in (int, float)
            and math.isfinite(own["elapsedMs"]) and 0 <= own["elapsedMs"] < 60000, "Incomplete/over-budget saved own-prefix plan")
    ordered = cells == teacher["cells"]
    unordered = set(cells) == set(teacher["cells"])
    disruption = own["disrupt"] == teacher["disrupt"]
    semantic = hash_string(own["nextStateSha256"]) == teacher["nextStateSha256"]
    gained = pair(own["gained"], "student gained") == teacher["gained"]
    scores = pair(own["scoreAfter"], "student score") == teacher["scoreAfter"]
    require(gained == scores, "Same root must preserve gained/score equality equivalence")
    require(not semantic or gained, "Equal complete successor state has different gain")
    for name, expected in (("orderedBuildEqual", ordered), ("buildSetEqual", unordered),
                           ("disruptEqual", disruption), ("semanticEqual", semantic), ("gainedEqual", gained)):
        bool_equal(own[name], expected, name)
    return {"ordered_build_equal": ordered, "build_set_equal": unordered, "disrupt_equal": disruption,
            "ordered_full_plan_equal": ordered and disruption, "unordered_full_plan_equal": unordered and disruption,
            "semantic_next_state_equal": semantic, "immediate_gained_equal": gained,
            "same_gain_different_state": gained and not semantic,
            "different_plan_same_state": semantic and not (ordered and disruption)}


def validate_turn(record, game, metadata_turn, rows):
    for name in ("id", "split", "seed", "mapHash", "teacherSeat", "opponentSpec"):
        require(record[name] == game[name], f"Java root game identity mismatch: {name}")
    require(record["turn"] == metadata_turn["turn"] and len(rows) == 4, "Missing/duplicate Java root turn")
    require(record["preStateSha256"] == metadata_turn["preStateSha256"], "Java root differs from recorded teacher trajectory")
    hash_string(record["semanticRootSha256"])
    teacher = record["teacher"]
    expected = metadata_turn["teacherExpanded"]
    require(teacher["cells"] == expected["cells"] and teacher["cost"] == expected["cost"]
            and teacher["disrupt"] == (600 if expected["disrupt"] == -1 else expected["disrupt"]), "Teacher plan differs from frozen metadata")
    checked_cells(teacher["cells"])
    hash_string(teacher["nextStateSha256"])
    require(pair(teacher["gained"], "teacher gained") == metadata_turn["gained"]
            and pair(teacher["scoreAfter"], "teacher score") == metadata_turn["scoreAfter"], "Teacher next score/gain mismatch")
    decisions = record["rows"]
    require(isinstance(decisions, list) and len(decisions) == 4, "Expected four teacher-prefix rows")
    measured = {model: [] for model in ("v4", "v9")}
    for stage, (decision, row) in enumerate(zip(decisions, rows)):
        label = int(row["label"])
        require(decision["stage"] == stage == int(row["stage"]) and decision["label"] == label,
                "Java conditional label/stage differs from cache")
        require(decision["prefix"] == metadata_turn["prefixes"][stage]
                and decision["paint"] == metadata_turn["paintBefore"][stage]
                and decision["stopped"] == metadata_turn["stoppedBefore"][stage], "Java teacher-prefix bookkeeping differs")
        require(decision["inputSha256"] == hashlib.sha256(input_bytes(row)).hexdigest(), "Java observed-input identity differs from original cache")
        remaining = (teacher["cells"][stage:] or [600]) if stage < 3 else [teacher["disrupt"]]
        require(decision["remainingLabels"] == remaining, "Remaining teacher action set differs")
        mask = row["disruptMask" if stage == 3 else "buildMask"]
        require(all(bool(mask[target]) for target in remaining), "Remaining teacher action is not legal on saved prefix")
        legal_count = int(mask.sum())
        require(set(decision["models"]) == {"v4", "v9"}, "Missing/extra teacher-prefix model")
        for model, values in decision["models"].items():
            greedy = values["greedy"]
            require(type(greedy) is int and 0 <= greedy <= 600 and bool(mask[greedy]), "Illegal saved masked greedy action")
            require(type(values["legalCount"]) is int and values["legalCount"] == legal_count, "Java legal mask count mismatch")
            label_probability = probability(values["labelProbability"], "label")
            remaining_probability = probability(values["remainingProbability"], "remaining teacher set")
            require(label_probability <= remaining_probability + 1e-12, "Set mass smaller than included original-label mass")
            if len(remaining) == 1:
                require(abs(label_probability - remaining_probability) <= 1e-12, "Singleton remaining target differs from original-label probability")
            if legal_count == 1:
                require(greedy == label and abs(label_probability - 1) <= 1e-12, "Forced decision does not have unit label probability")
            bool_equal(values["greedyInRemaining"], greedy in remaining, "remaining-set membership")
            measured[model].append({"stage": stage, "unforced": legal_count > 1,
                                    "exact": greedy == label, "remaining": greedy in remaining,
                                    "label_probability": label_probability, "remaining_probability": remaining_probability})
    require(set(record["ownPlans"]) == {"v4", "v9"}, "Missing/extra own-prefix model")
    plans = {model: plan_comparison(teacher, own) for model, own in record["ownPlans"].items()}
    permutations = record["permutations"]
    expected_orders = set(itertools.permutations(teacher["cells"]))
    require(len(permutations) == len(expected_orders)
            and {tuple(item["cells"]) for item in permutations} == expected_orders, "Missing/duplicate teacher permutations")
    permutation_metrics = []
    for item in permutations:
        equal = hash_string(item["nextStateSha256"]) == teacher["nextStateSha256"]
        same_gain = pair(item["gained"], "permutation gained") == teacher["gained"]
        require((pair(item["scoreAfter"], "permutation score") == teacher["scoreAfter"]) == same_gain,
                "Permutation gained/score equality mismatch")
        bool_equal(item["semanticEqual"], equal, "permutation semantic equality")
        bool_equal(item["gainedEqual"], same_gain, "permutation gain equality")
        require(not equal or same_gain, "Equal permutation successor has different gain")
        if item["cells"] == teacher["cells"]:
            require(equal and same_gain, "Original teacher permutation must reproduce teacher successor")
        permutation_metrics.append({"semantic_equal": equal, "gained_equal": same_gain,
                                    "same_gain_different_state": same_gain and not equal})
    return measured, plans, permutation_metrics


class Reducer:
    def __init__(self):
        self.prefix = {(split, model, category): Counter() for split in ("train", "validation", "all")
                       for model in ("v4", "v9") for category in SLICES}
        self.plans = {(split, model): Counter() for split in ("train", "validation", "all") for model in ("v4", "v9")}
        self.permutations = {split: Counter() for split in ("train", "validation", "all")}

    def add(self, split, measured, plans, permutations):
        for group in (split, "all"):
            for model in ("v4", "v9"):
                for decision in measured[model]:
                    for category in SLICES:
                        if selected_slice(decision["stage"], decision["unforced"], category):
                            self.prefix[group, model, category].update(rows=1, exact=int(decision["exact"]),
                                remaining=int(decision["remaining"]), label_probability=decision["label_probability"],
                                remaining_probability=decision["remaining_probability"])
                self.plans[group, model].update(roots=1, **{name: int(value) for name, value in plans[model].items()},
                    teacher_prefix_all_four_exact=int(all(row["exact"] for row in measured[model])),
                    teacher_prefix_all_four_remaining=int(all(row["remaining"] for row in measured[model])))
            self.permutations[group].update(roots=1, permutations=len(permutations),
                roots_all_permutations_semantic_equal=int(all(item["semantic_equal"] for item in permutations)))
            for item in permutations:
                self.permutations[group].update({name: int(value) for name, value in item.items()})

    def report(self):
        result = {}
        for split in ("train", "validation", "all"):
            result[split] = {"models": {}, "teacher_permutations": dict(self.permutations[split])}
            for model in ("v4", "v9"):
                slices = {}
                for category in SLICES:
                    values = self.prefix[split, model, category]
                    n = values["rows"]
                    slices[category] = {"rows": n, "exact_correct": values["exact"], "remaining_correct": values["remaining"],
                        "exact_agreement": values["exact"] / n if n else None,
                        "remaining_set_agreement": values["remaining"] / n if n else None,
                        "mean_original_label_probability": values["label_probability"] / n if n else None,
                        "mean_remaining_set_probability": values["remaining_probability"] / n if n else None}
                counts = self.plans[split, model]
                rates = {name: value / counts["roots"] for name, value in counts.items() if name != "roots"} if counts["roots"] else {}
                result[split]["models"][model] = {"teacher_prefix": slices,
                    "root_counts": {"roots": counts["roots"], **dict(counts)}, "root_rates": rates}
        return result


def validate_java_manifest(java, corpus, cache_games):
    require(java.get("schemaVersion") == 1 and java.get("experiment") == "spatial-label-audit-v1"
            and java.get("status") == "complete", "Incomplete/unsupported Java label audit")
    require(java["weightsSha256"] == {name.removeprefix("spatial-ppo-"): digest for name, digest in WEIGHTS.items()},
            "Java audit used different frozen model weights")
    require(java["corpusManifestSha256"] == corpus, "Java/cached corpus identity mismatch")
    expected = {game["id"]: game for game in cache_games}
    require(len(java["games"]) == len(expected) and {game["id"] for game in java["games"]} == set(expected),
            "Missing/duplicate/extra Java games")
    require(java["totalRows"] == sum(game["rowCount"] for game in cache_games)
            and java["totalTurns"] == sum(game["turns"] for game in cache_games), "Java coverage totals differ")
    files = set()
    for game in java["games"]:
        source = expected[game["id"]]
        for key in ("split", "seed", "mapHash", "teacherSeat", "opponentSpec", "rowCount"):
            require(game[key] == source[key], f"Java game lineage mismatch: {key}")
        require(game["turnCount"] == source["turns"] and game["regeneratedRawSha256"] == source["rawSha256"],
                "Java did not regenerate the exact complete original conditional stream")
        require(game["file"] not in files, "Java games share an output path")
        files.add(game["file"])
        hash_string(game["sha256"])


def reduce_java(manifest_path, data, mappings, metadata):
    manifest_path = Path(manifest_path)
    manifest = strict_json(manifest_path.read_text(encoding="utf-8-sig"))
    validate_java_manifest(manifest, data["manifest_sha256"], data["games"])
    games = {game["id"]: game for game in data["games"]}
    reducer = Reducer()
    identities = {str(manifest_path.resolve()): digest_file(manifest_path)}
    total_roots = 0
    for game in manifest["games"]:
        source = games[game["id"]]
        file = local_file(manifest_path.parent, game["file"])
        require(digest_file(file) == game["sha256"], "Java per-game JSONL hash mismatch")
        identities[str(file)] = game["sha256"]
        turns = metadata[game["id"]]["turns"]
        seen = 0
        with file.open(encoding="utf-8-sig") as stream:
            for index, line in enumerate(stream):
                require(bool(line.strip()) and index < len(turns), "Blank/extra Java root line")
                record = strict_json(line)
                start = source["cacheRowStart"] + 4 * index
                rows = mappings[source["split"]][start:start + 4]
                measured, plans, permutations = validate_turn(record, source, turns[index], rows)
                reducer.add(source["split"], measured, plans, permutations)
                seen += 1
        require(seen == len(turns) == game["turnCount"], "Incomplete Java per-game root coverage")
        require(digest_file(file) == game["sha256"], "Java game changed during reduction")
        total_roots += seen
    require(total_roots == manifest["totalTurns"], "Reducer root accounting mismatch")
    result = reducer.report()
    for split in ("train", "validation", "all"):
        rows = sum(game["rowCount"] for game in games.values() if split == "all" or game["split"] == split)
        for model in ("v4", "v9"):
            values = result[split]["models"][model]
            require(values["teacher_prefix"]["all"]["rows"] == rows and values["root_counts"]["roots"] * 4 == rows,
                    "Reducer omitted conditional rows or own-prefix plans")
    return result, identities


def write_new(path, value):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as stream:
        json.dump(value, stream, indent=2, allow_nan=False)
        stream.write("\n")


def run(args):
    output = Path(args.output)
    collision_path = output.parent / "collision-groups.json"
    if output.exists() or collision_path.exists():
        raise FileExistsError("Refusing to overwrite a complete or partial label audit")
    started, mappings = time.perf_counter(), {}
    try:
        freeze = verify_freeze(args.freeze)
        data, corpus, mappings, metadata, lineage = load_corpus(args.cache)
        cache_hash = digest_file(Path(args.cache) / "cache.json")
        collisions, evidence = collision_audit(mappings, lineage)
        comparisons, identities = reduce_java(args.java_manifest, data, mappings, metadata)
        require(collisions["groups"]["all"]["all"]["rows"] == corpus["rows"], "Collision audit omitted original rows")
        require(verify_freeze(args.freeze) == freeze, "Source freeze changed during audit")
        require(verify_cache(args.cache) == data and digest_file(Path(args.cache) / "cache.json") == cache_hash,
                "Corpus/cache changed during audit")
        for name, expected in identities.items():
            require(digest_file(name) == expected, "Java audit input changed during reduction")
        write_new(collision_path, {"schemaVersion": 1, "groups": evidence})
        report = {"schemaVersion": 1, "experiment": "spatial-label-audit-v1", "status": "complete",
                  "coverage": {"games": 60, "maps": 30, "turn_roots": corpus["totalTurns"],
                               "conditional_rows": corpus["rows"], "models": list(MODELS)},
                  "weightsSha256": WEIGHTS, "provenance": {"source_freeze": freeze,
                      "cache": str(Path(args.cache).resolve()), "cache_sha256": cache_hash,
                      "corpus_manifest_sha256": data["manifest_sha256"], "java_files_sha256": identities},
                  "collisions": collisions, "comparisons": comparisons,
                  "collision_evidence": {"path": str(collision_path.resolve()), "sha256": digest_file(collision_path),
                                         "duplicate_groups": len(evidence)},
                  "limits": ["Previously exposed development corpus; descriptive finite-sample results only.",
                      "All row means weight conditional rows equally; all root rates weight recorded turn roots equally.",
                      "Teacher-prefix all-four agreement is a separate diagnostic, never a stitched actual student plan.",
                      "Remaining-set mass diagnoses ordering only; it is not an alternative training objective.",
                      "Byte identity is conservative and majority-label ceiling is only for this recorded multiset.",
                      "Equal immediate gain with a different semantic state is not action equivalence.",
                      "Same next state is relative to the same recorded simultaneous opponent action, not every opponent.",
                      "Teacher disagreement does not establish inferior strategy; no value or strength conclusion."],
                  "seconds": time.perf_counter() - started}
        write_new(output, report)
        print(json.dumps({"status": "complete", "report": str(output), "rows": corpus["rows"],
                          "turn_roots": corpus["totalTurns"], "duplicate_groups": len(evidence),
                          "seconds": report["seconds"]}, allow_nan=False), flush=True)
    except Exception as exc:
        if not output.exists():
            write_new(output, {"schemaVersion": 1, "experiment": "spatial-label-audit-v1", "status": "failed",
                               "failure": {"type": type(exc).__name__, "message": str(exc)},
                               "seconds": time.perf_counter() - started})
        raise
    finally:
        for rows in mappings.values():
            rows._mmap.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cache", default="build/spatial-ppo-v4-cache")
    parser.add_argument("--java-manifest", required=True)
    parser.add_argument("--freeze", default="build/analysis/spatial-label-audit-v1/sources-freeze.json")
    parser.add_argument("--output", default="build/analysis/spatial-label-audit-v1/report.json")
    run(parser.parse_args())
