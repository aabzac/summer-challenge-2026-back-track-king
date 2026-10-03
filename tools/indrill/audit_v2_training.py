"""Independent read-only audit of the completed, fixed indrill-q-v2 schedule.

Run after all training stages and training-map registration, before timed benches.
Only --out is written, once; no training, weight export or external calls occur.
"""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import gzip
import hashlib
import json
from pathlib import Path
import re
import struct
import sys
import time

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
SHAPES = {
    "W0": (64, 24), "B0": (24,), "WS": (24, 24), "WN": (24, 24),
    "WG": (24, 24), "B1": (24,), "WB": (24,), "WI": (24,),
    "BB": (1,), "BI": (1,), "WV": (24,), "BV": (1,),
}
HYPERPARAMS = {
    "seed": 20261004, "steps": 2000, "batch": 32, "target": 100,
    "gamma": .99, "nstep": 3, "alpha": .6, "beta": .4,
    "lr": .0003, "huber": 1.0, "clip": 10.0, "float32_replay": False,
}
EPSILON = [.30, .25, .20, .15, .12, .10]
REQUIRED_SOURCES = {
    "tools/indrill/train_q.py", "tools/indrill/train_q_v2.py",
    "src/main/java/btk/tools/IndrillV2Collect.java",
    "src/main/java/btk/bot/current/IndrillV2Net.java",
    "src/main/java/btk/bot/current/IndrillV2Bot.java",
    "src/main/java/btk/bot/current/IndrillFeatures.java",
    "src/main/java/btk/core/InkProjection.java",
}


class AuditError(ValueError):
    pass


def require(condition, message):
    if not condition:
        raise AuditError(message)


def sha(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def rooted(path, root):
    path = Path(path)
    return path.resolve() if path.is_absolute() else (root / path).resolve()


def same_path(left, right, root):
    return rooted(left, root) == rooted(right, root)


def load_json(path):
    require(Path(path).is_file(), f"Missing completed artifact: {path}")
    with Path(path).open("r", encoding="utf-8-sig") as stream:
        return json.load(stream)


def exact(stream, length):
    data = stream.read(length)
    require(len(data) == length, "Truncated dataset/weight binary")
    return data


def integer(stream):
    return struct.unpack(">i", exact(stream, 4))[0]


def discard(stream, length):
    while length:
        part = min(length, 65536)
        exact(stream, part)
        length -= part


def scan_dataset(path, digest, expected_games):
    """Count actual complete episodes/transitions without retaining feature tensors.

    This validates the exported data contract/legality masks, not game scoring.
    The BTKQ format does not encode full initial town/desired-pair metadata, so
    seed-to-full-map identity additionally relies on the registered core map index.
    """
    episodes, keys = [], []
    opener = gzip.open if str(path).endswith(".gz") else open
    with opener(path, "rb") as stream:
        require(exact(stream, 8) == b"BTKQ0001", f"Wrong dataset magic: {path}")
        require(integer(stream) == 64, "Unexpected feature count")
        games = integer(stream)
        require(games == expected_games, f"Dataset header game count differs: {path}")
        for game in range(games):
            episode, width, height, regions = (integer(stream) for _ in range(4))
            size = width * height
            require(episode == game and width > 0 and height > 0 and size <= 10000
                    and 0 < regions <= size, "Invalid/duplicate episode ID or dimensions")
            cell_regions = np.frombuffer(exact(stream, size * 4), dtype=">i4")
            costs = np.frombuffer(exact(stream, size), dtype="u1")
            require((cell_regions >= 0).all() and (cell_regions < regions).all(), "Invalid cell-region IDs")
            require(np.isin(costs, (1, 2, 3)).all(), "Invalid exported paint costs")
            states = integer(stream)
            require(2 <= states <= 101, "Expected a complete game with at most 100 turns")
            identity = f"{Path(path).name}:{game}:{episode}"
            for step in range(states):
                discard(stream, 2 * size * 64 * 4)
                legal_build = np.frombuffer(exact(stream, size), dtype="u1")
                legal_ink = np.frombuffer(exact(stream, regions), dtype="u1")
                require(np.isin(legal_build, (0, 1)).all() and np.isin(legal_ink, (0, 1)).all(),
                        "Invalid exported legal masks")
                actions = []
                for player in (0, 1):
                    ink, count, c0, c1, c2 = struct.unpack(">iiiii", exact(stream, 20))
                    require(-1 <= ink < regions and 0 <= count <= 3, "Invalid exported action dimensions")
                    cells = (c0, c1, c2)[:count]
                    unused = (c0, c1, c2)[count:]
                    require(all(c == -1 for c in unused), "Placement padding must be -1")
                    require(len(set(cells)) == count and all(0 <= c < size for c in cells),
                            "Invalid or repeated selected cells")
                    require(all(legal_build[c] for c in cells) and sum(int(costs[c]) for c in cells) <= 3,
                            "Placement violates the exported legal mask/budget")
                    require(ink < 0 or legal_ink[ink], "Disruption violates the exported legal mask")
                    actions.append((cells, ink))
                    if step < states - 1:
                        key_text = f"{digest}:{identity}:{step}:{player}"
                        keys.append(int.from_bytes(hashlib.sha256(key_text.encode()).digest()[:8], "big"))
                r0, r1, done = struct.unpack(">ffB", exact(stream, 9))
                require(np.isfinite((r0, r1)).all() and r0 == -r1, "Rewards are nonfinite or not antisymmetric")
                require(done == int(step >= states - 2), "Done flags do not identify exactly the final transition/state")
                if step == states - 1:
                    require(actions == [((), -1), ((), -1)] and r0 == 0 and r1 == 0,
                            "Terminal feature record must have no action/reward")
            episodes.append({"episode": episode, "width": width, "height": height,
                             "regions": regions, "states": states, "turns": states - 1})
        require(not stream.read(1), f"Trailing dataset records: {path}")
    keys = np.asarray(keys, dtype=np.uint64)
    require(len(np.unique(keys)) == len(keys), "Duplicate/colliding replay IDs within dataset")
    return {"games": games, "transitions": len(keys), "turns": sum(e["turns"] for e in episodes),
            "episodes": episodes}, keys


def check_collection(meta, path, digest, count, base_seed, epsilon, model_hash, balanced_seats):
    require(meta.get("schema") == "BTKQ0001" and meta.get("features") == 64, "Wrong collection metadata schema")
    for key, expected in (("games", count), ("seed", base_seed), ("epsilon", epsilon),
                          ("opponent", "mixed"), ("datasetSha256", digest)):
        require(meta.get(key) == expected, f"Collection {path}: unexpected {key}")
    require(meta.get("weightsSha256") == model_hash, f"Collection {path}: wrong frozen input model")
    require(meta.get("reward") == "delta projectedInkAll score difference / 100 + terminal {-1,0,1}; opponent antisymmetric",
            f"Collection {path}: changed recorded reward definition")
    results = meta.get("results", [])
    require(len(results) == count, f"Collection {path}: incomplete results list")
    seeds, cohorts = [], {"self": 0, "v09": 0, "neural-v1": 0}
    seats = {"v09": [0, 0], "neural-v1": [0, 0]}
    for game, result in enumerate(results):
        seed = base_seed + game
        control = "self" if game % 4 < 2 else "v09" if game % 4 == 2 else "neural-v1"
        seat = (game // 4) % 2 if balanced_seats else game % 2
        require(result.get("seed") == seed and result.get("opponent") == control
                and result.get("controlSeat") == seat, f"Collection {path}: seed/opponent/seat schedule differs")
        require(isinstance(result.get("turns"), int) and 1 <= result["turns"] <= 100,
                "Invalid collection turn count")
        require(all(isinstance(result.get(f"score{p}"), int) for p in (0, 1)), "Missing collected game scores")
        seeds.append(seed)
        cohorts[control] += 1
        if control != "self":
            seats[control][seat] += 1
    require(len(set(seeds)) == count, "Collection repeats a declared seed")
    require(cohorts == {"self": count // 2, "v09": count // 4, "neural-v1": count // 4},
            "Collection opponent proportions differ")
    if balanced_seats:
        require(all(s == [count // 8, count // 8] for s in seats.values()), "New frozen-opponent seats are not balanced")
    return seeds, {"opponents": cohorts, "frozenOpponentSeats": seats}


def weight_checkpoint(binary, expected_updates, expected_keys):
    with Path(binary).open("rb") as stream:
        require(exact(stream, 8) == b"BTKW0002", "Expected v2 weight binary")
        require((integer(stream), integer(stream)) == (64, 24), "Wrong weight architecture")
        tensors = {}
        for name, shape in SHAPES.items():
            value = np.frombuffer(exact(stream, int(np.prod(shape)) * 4), dtype=">f4").astype(np.float32).reshape(shape)
            require(np.isfinite(value).all(), f"Nonfinite binary tensor {name}")
            tensors[name] = value
        require(not stream.read(1), "Trailing binary tensors")
    checkpoint = Path(binary).with_suffix(".npz")
    expected_names = set(SHAPES) | {"schemaVersion", "updates", "replay_keys", "replay_priorities"}
    expected_names |= {prefix + name for prefix in ("adam_m_", "adam_v_", "target_") for name in SHAPES}
    with np.load(checkpoint, allow_pickle=False) as saved:
        require(set(saved.files) == expected_names, "Unexpected/missing checkpoint fields")
        require(int(saved["schemaVersion"]) == 2 and int(saved["updates"]) == expected_updates,
                "Wrong checkpoint schema or actual optimizer update count")
        for name, shape in SHAPES.items():
            online = saved[name]
            require(online.shape == shape and online.dtype == np.float32
                    and np.array_equal(online.view(np.uint32), tensors[name].view(np.uint32)),
                    f"Binary/checkpoint online parameter mismatch: {name}")
            for prefix in ("adam_m_", "adam_v_", "target_"):
                value = saved[prefix + name]
                require(value.shape == shape and value.dtype == np.float32 and np.isfinite(value).all(),
                        f"Invalid optimizer/target tensor {prefix}{name}")
                if prefix == "adam_v_":
                    require((value >= 0).all(), f"Negative Adam second moment: {name}")
                if prefix == "target_":
                    require(np.array_equal(value.view(np.uint32), online.view(np.uint32)),
                            f"Final target is not synchronized on the 100-update boundary: {name}")
                if expected_updates == 0 and prefix in ("adam_m_", "adam_v_"):
                    require(not value.any(), "Random initialization has nonzero optimizer history")
        keys, priorities = saved["replay_keys"], saved["replay_priorities"]
        require(keys.dtype == np.uint64 and keys.ndim == 1, "Invalid checkpoint replay-key type")
        require(priorities.shape == keys.shape and np.isfinite(priorities).all() and (priorities > 0).all(),
                "Invalid replay priorities")
        require(len(np.unique(keys)) == len(keys), "Duplicate checkpoint replay IDs")
        require(np.array_equal(np.sort(keys), np.sort(expected_keys)),
                "Replay checkpoint IDs do not match the actual accumulated datasets")
        facts = {"updates": int(saved["updates"]), "replayItems": len(keys),
                 "priorityMinimum": float(priorities.min()) if len(priorities) else None,
                 "priorityMaximum": float(priorities.max()) if len(priorities) else None,
                 "targetMatchesOnline": True, "onlineMatchesBinary": True,
                 "optimizerFinite": True, "binarySha256": sha(binary), "checkpointSha256": sha(checkpoint)}
    return facts, tensors


def generated_java_payload(path, binary):
    source = Path(path).read_text(encoding="utf-8")
    require("package btk.bot.current;" in source and "public final class IndrillV2Weights" in source,
            "Wrong generated Java class/package")
    fields = re.findall(r'public static final float\[\] (\w+) = decode\("([A-Za-z0-9+/=]*)"\);', source)
    require([name for name, _ in fields] == list(SHAPES), "Generated Java tensor names/order differ")
    payload = bytearray()
    for (name, encoded), shape in zip(fields, SHAPES.values()):
        raw = base64.b64decode(encoded, validate=True)
        require(len(raw) == int(np.prod(shape)) * 4, f"Wrong Java payload length {name}")
        payload.extend(raw)
    data = Path(binary).read_bytes()
    require(bytes(payload) == data[16:], "Generated Java float bytes differ from the frozen final binary")
    return {"path": str(path), "sourceSha256": sha(path), "weightPayloadSha256": hashlib.sha256(payload).hexdigest(),
            "tensors": len(fields), "parameters": len(payload) // 4, "equalsFinalBinary": True}


def check_settings(meta, evidence_path, weights, previous, expected_data, root, is_init):
    settings = meta.get("settings", {})
    for key, expected in HYPERPARAMS.items():
        require(settings.get(key) == expected, f"{evidence_path}: changed learning setting {key}")
    require(settings.get("init_only") is is_init and meta.get("initOnly") is is_init,
            "Initialization/training mode differs")
    require(meta.get("warmStartV1") is False and settings.get("warm_start_v1") is None,
            "The fixed random-start experiment must not warm start")
    require(meta.get("seed") == 20261004 and meta.get("schemaVersion") == 2, "Wrong model seed/schema")
    require(same_path(settings.get("weights_out", ""), weights, root)
            and same_path(settings.get("evidence", ""), evidence_path, root), "Output/evidence paths differ")
    java = root / "src/main/java/btk/bot/current/IndrillV2Weights.java"
    require(same_path(settings.get("java_out", ""), java, root), "Stage wrote outside the requested v2 Java class")
    require(settings.get("weights_in") is None if is_init else same_path(settings.get("weights_in", ""), previous, root),
            "Stage resumed the wrong model")
    paths = [rooted(path, root) for path in settings.get("data", [])]
    require(len(paths) == len(set(paths)) and set(paths) == set(expected_data), "Stage settings have a different/repeated replay dataset")
    if is_init:
        require(meta.get("games") == 0 and meta.get("transitions") == 0 and meta.get("updates") == 0,
                "Initializer already trained")
        require(not meta.get("trainingHistory") and not meta.get("data"), "Initializer has training/source data")
    else:
        for field, source in (("inputWeights", previous), ("inputCheckpoint", previous.with_suffix(".npz"))):
            recorded = meta.get(field, {})
            require(same_path(recorded.get("path", ""), source, root) and recorded.get("sha256") == sha(source),
                    f"Broken input hash chain: {field}")


def check_map_index(maps, all_seeds):
    """Validate both the declared inventory and the field consumed by the gate."""
    require(type(maps.get("schemaVersion")) is int and maps["schemaVersion"] == 1
            and isinstance(maps.get("maps"), list), "Wrong training map-index schema")
    require(maps.get("trainingOnly") is True
            and maps.get("hashScheme") == "sha256-init-block-player-0",
            "Training exclusion manifest must use the gate's canonical map-hash scheme")
    mapping = {}
    for item in maps["maps"]:
        seed, map_hash = item.get("seed"), item.get("mapHash")
        require(type(seed) is int and re.fullmatch(r"[0-9a-f]{64}", str(map_hash)), "Invalid seed/map fingerprint")
        require(seed not in mapping, "Map index repeats a seed")
        mapping[seed] = map_hash
    require(set(mapping) == set(all_seeds), "Training-only map index does not contain exactly the declared training seeds")
    training_hashes = {mapping[seed] for seed in all_seeds}
    require(len(training_hashes) == len(all_seeds), "Declared training maps repeat a full-map fingerprint")
    consumed = maps.get("gamesPerMap")
    require(isinstance(consumed, dict), "Exclusion manifest is missing gamesPerMap consumed by practical-v4")
    require(all(type(count) is int and count == 1 for count in consumed.values()),
            "Every fixed training exclusion must have integer gamesPerMap count one")
    require(consumed == {map_hash: 1 for map_hash in training_hashes},
            "Gate-consumed gamesPerMap differs from the complete declared training map inventory")
    return mapping


def run_audit(root, directory, maps_path):
    started = time.monotonic()
    plan_path, complete_path = directory / "training-plan.json", directory / "training-complete.json"
    plan, complete = load_json(plan_path), load_json(complete_path)
    require(plan.get("schemaVersion") == 1 and plan.get("experiment") == "indrill-q-v2"
            and plan.get("seed") == 20261004, "Wrong training plan")
    blocks = [{"games": 128, "seed": 2100000 + i * 1000, "epsilon": value} for i, value in enumerate(EPSILON)]
    require(plan.get("blocks") == blocks and plan.get("updatesPerStage") == 2000 and plan.get("batch") == 32,
            "Plan is not the predeclared seven-stage schedule")
    require((plan.get("totalGames"), plan.get("totalUpdates"), plan.get("sampledVisits")) == (960, 14000, 448000),
            "Declared totals differ from the fixed schedule")
    require(complete.get("complete") is True and complete.get("planSha256") == sha(plan_path),
            "No matching full-completion marker")
    recorded_sources = plan.get("sourceSha256", {})
    require(set(recorded_sources) == REQUIRED_SOURCES, "Plan's source inventory differs")
    source_facts = []
    for relative, expected in recorded_sources.items():
        path, actual = rooted(relative, root), sha(rooted(relative, root))
        require(actual == expected, f"Training source changed after plan declaration: {relative}")
        source_facts.append({"path": relative, "sha256": actual})

    # Count/hash each immutable dataset once; subsequent stages consume its facts.
    datasets, dataset_keys, all_seeds = {}, {}, []
    expected_old = [root / f"build/analysis/indrill-q/round-{i}.bin.gz" for i in range(1, 5)]
    require(len(plan.get("bootstrap", [])) == 4, "Wrong bootstrap dataset count")
    for i, (entry, expected_path) in enumerate(zip(plan["bootstrap"], expected_old)):
        require(same_path(entry["path"], expected_path, root), "Bootstrap paths differ from the retained four v1 blocks")
        digest = sha(expected_path)
        require(entry["sha256"] == digest, "Bootstrap bytes changed")
        meta_path = Path(str(expected_path) + ".json")
        meta = load_json(meta_path)
        origin = root / f"build/analysis/indrill-q/weights-{i}.bin"
        seeds, cohort = check_collection(meta, expected_path, digest, 48, 2000000 + i * 1000,
                                         [.25, .20, .15, .10][i], sha(origin), False)
        binary, keys = scan_dataset(expected_path, digest, 48)
        require([e["turns"] for e in binary["episodes"]] == [g["turns"] for g in meta["results"]],
                "Bootstrap binary turn counts differ from results")
        datasets[expected_path] = {"path": str(expected_path), "sha256": digest, "metadataSha256": sha(meta_path),
                                   "seedBase": seeds[0], "seedCount": len(seeds), "binary": binary, **cohort}
        dataset_keys[expected_path] = keys
        all_seeds.extend(seeds)
        print(f"audited bootstrap {i + 1}/4: {binary['games']} games", flush=True)

    initial, init_path = directory / "weights-init.bin", directory / "init.json"
    init = load_json(init_path)
    check_settings(init, init_path, initial, None, [], root, True)
    init_facts, _ = weight_checkpoint(initial, 0, np.empty(0, dtype=np.uint64))
    require(init.get("weightsSha256") == init_facts["binarySha256"], "Initialization hash differs")
    init_facts.update(evidenceSha256=sha(init_path))
    previous, stage_facts, active_data, total_visits = initial, [], list(expected_old), 0
    for stage in range(7):
        if stage:
            path = directory / f"round-{stage}.bin.gz"
            digest, meta_path = sha(path), Path(str(path) + ".json")
            meta, spec = load_json(meta_path), blocks[stage - 1]
            seeds, cohort = check_collection(meta, path, digest, spec["games"], spec["seed"], spec["epsilon"], sha(previous), True)
            binary, keys = scan_dataset(path, digest, 128)
            require([e["turns"] for e in binary["episodes"]] == [g["turns"] for g in meta["results"]],
                    "New binary turn counts differ from recorded results")
            datasets[path] = {"path": str(path), "sha256": digest, "metadataSha256": sha(meta_path),
                              "seedBase": seeds[0], "seedCount": len(seeds), "binary": binary, **cohort}
            dataset_keys[path] = keys
            all_seeds.extend(seeds)
            active_data.append(path)
            print(f"audited collection {stage}/6: {binary['games']} games", flush=True)
        evidence_path, weights = directory / f"train-{stage}.json", directory / f"weights-{stage}.bin"
        evidence = load_json(evidence_path)
        check_settings(evidence, evidence_path, weights, previous, active_data, root, False)
        actual_sources = {}
        for source in evidence.get("data", []):
            resolved = rooted(source["path"], root)
            require(resolved not in actual_sources, "Duplicate source dataset in training evidence")
            actual_sources[resolved] = source["sha256"]
        require(actual_sources == {path: datasets[path]["sha256"] for path in active_data},
                "Training evidence contains a different dataset/hash chain")
        actual_games = sum(datasets[path]["binary"]["games"] for path in active_data)
        actual_transitions = sum(datasets[path]["binary"]["transitions"] for path in active_data)
        require(actual_games == 192 + 128 * stage and evidence.get("games") == actual_games,
                "Actual/evidence game counts disagree")
        require(evidence.get("transitions") == actual_transitions and evidence.get("updates") == 2000 * (stage + 1),
                "Actual transition/update counts disagree")
        keys = np.concatenate([dataset_keys[path] for path in active_data])
        facts, _ = weight_checkpoint(weights, 2000 * (stage + 1), keys)
        require(evidence.get("weightsSha256") == facts["binarySha256"], "Stage output weight hash differs")
        previous_items = 0 if stage == 0 else stage_facts[-1]["replayItems"]
        require(evidence.get("restoredPriorities") == previous_items, "Not all existing priorities were restored")
        expected_new_priority = 1.0 if stage == 0 else stage_facts[-1]["priorityMaximum"]
        require(evidence.get("newItemPriority") == expected_new_priority, "Fresh replay items used the wrong initial priority")
        history = evidence.get("trainingHistory", [])
        expected_steps = [1] + list(range(100, 2001, 100))
        require([h.get("step") for h in history] == expected_steps, "Missing/different optimizer history checkpoints")
        require([h.get("updates") for h in history] == [stage * 2000 + step for step in expected_steps],
                "History optimizer counters differ")
        for row in history:
            require(all(np.isfinite(row[field]) for field in ("loss", "meanAbsTd", "gradNorm", "beta", "meanStateValue", "meanGreedyJointQ")),
                    "Nonfinite training-history diagnostic")
        facts.update(stage=stage, games=actual_games, transitions=actual_transitions,
                     evidenceSha256=sha(evidence_path), lastDiagnostic=history[-1])
        stage_facts.append(facts)
        total_visits += evidence["settings"]["steps"] * evidence["settings"]["batch"]
        previous = weights

    actual_games = sum(data["binary"]["games"] for data in datasets.values())
    require(len(all_seeds) == len(set(all_seeds)) == actual_games == 960, "Seed/game totals repeat or differ")
    final_updates = stage_facts[-1]["updates"]
    require(complete.get("games") == actual_games and complete.get("updates") == final_updates
            and complete.get("weightsSha256") == sha(previous)
            and same_path(complete.get("weights", ""), previous, root)
            and same_path(complete.get("trainingEvidence", ""), directory / "train-6.json", root),
            "Completion marker contradicts independently measured facts")
    maps = load_json(maps_path)
    mapping = check_map_index(maps, all_seeds)
    java_path = root / "src/main/java/btk/bot/current/IndrillV2Weights.java"
    java = generated_java_payload(java_path, previous)
    return {"schemaVersion": 1, "experiment": "indrill-q-v2", "passed": True,
            "createdUtc": dt.datetime.now(dt.timezone.utc).isoformat(),
            "planSha256": sha(plan_path), "completionSha256": sha(complete_path),
            "auditorSha256": sha(Path(__file__)), "sources": source_facts,
            "initialization": init_facts, "stages": stage_facts, "datasets": list(datasets.values()),
            "actualGames": actual_games, "actualTransitions": stage_facts[-1]["transitions"],
            "actualUpdates": final_updates, "sampledVisits": total_visits,
            "uniqueDeclaredSeeds": len(set(all_seeds)), "uniqueDeclaredMapHashes": actual_games,
            "mapIndex": {"path": str(maps_path), "sha256": sha(maps_path), "coveredTrainingSeeds": actual_games,
                         "extraSeeds": 0, "gateConsumedHashes": len(maps["gamesPerMap"]),
                         "gateConsumedCountsAllOne": True, "hashScheme": maps["hashScheme"], "trainingOnly": True},
            "generatedFinalJava": java, "finalWeightsSha256": sha(previous),
            "limitations": ["source hashes checked against the recorded plan, not an external signature",
                            "binary contract audit is not a reimplementation of scoring/pathfinding",
                            "full seed-to-map identity is declared by the core-generated map index",
                            "no convergence or strategy-strength verdict follows from this audit"],
            "seconds": round(time.monotonic() - started, 3)}


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", default=str(ROOT))
    parser.add_argument("--dir", default="build/analysis/indrill-q-v2")
    parser.add_argument("--maps", default="build/bench/indrill-q-v2-training-excluded.json")
    parser.add_argument("--out", default="build/analysis/indrill-q-v2/training-audit.json")
    args = parser.parse_args(argv)
    root = Path(args.root).resolve()
    directory, maps, out = (rooted(path, root) for path in (args.dir, args.maps, args.out))
    if out.exists():
        parser.error("Audit evidence already exists; output is write-once")
    require((directory / "training-complete.json").is_file(), "All fixed stages must finish before this audit")
    require(maps.is_file(), "Register all training maps before this audit")
    result = run_audit(root, directory, maps)
    out.parent.mkdir(parents=True, exist_ok=True)
    with out.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, indent=2)
        stream.write("\n")
    print(json.dumps({key: result[key] for key in ("passed", "actualGames", "actualTransitions", "actualUpdates",
                      "sampledVisits", "uniqueDeclaredSeeds", "uniqueDeclaredMapHashes", "finalWeightsSha256", "seconds")}))
    return 0


if __name__ == "__main__":
    sys.exit(main())
