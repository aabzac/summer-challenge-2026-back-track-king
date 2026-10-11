"""Write-once local target-proposal experiment; no submission, boss games or promotion.

prepare | run LABEL EXE ARGS... | java LABEL MAIN ARGS... | freeze | strength |
verify [LABEL] | summary | archive

The strength command delegates the unchanged practical-v4 runner. Its only hook verifies
the prospective source/configuration/input freeze before and after each completed block.
"""
from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import time
from datetime import datetime, timezone
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
NAME = "neural-target-strength-v1"
OUT = ROOT / "build/analysis" / NAME
BENCH = ROOT / "build/bench" / NAME
PRIOR = ROOT / "build/bench" / (NAME + "-prior") / "maps.json"
PROTOCOL = ROOT / "docs" / (NAME + ".md")
SUMMARY = ROOT / "docs/evidence" / (NAME + ".json")
JAVA = ROOT / "build/spatial-v10-java.json"
SPEC, CONTROL, SEED = "current?USE_NEURAL_TARGET_PROPOSAL=1", "v09", 231011000
PROPOSAL = ROOT / "docs/evidence/neural-proposal-audit-v1.json"
SELECTION = ROOT / "build/analysis/neural-proposal-audit-v1/selection.json"
PRACTICAL_ARGS = ["--spec", SPEC, "--control", CONTROL, "--name", NAME, "--seed", str(SEED)]
sys.path.insert(0, str(ROOT / "tools"))
import run_practical_v4 as practical  # noqa: E402


def now():
    return datetime.now(timezone.utc).isoformat()


def require(condition, message):
    if not condition:
        raise ValueError(message)


def sha(path):
    with Path(path).open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def read(path):
    return json.loads(Path(path).read_text(encoding="utf-8-sig"))


def rel(path):
    return Path(path).resolve().relative_to(ROOT).as_posix()


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, indent=2, allow_nan=False)
        stream.write("\n")


def identity(path):
    return {"path": rel(path), "sha256": sha(path), "bytes": path.stat().st_size}


def run(label, command):
    require(re.fullmatch(r"[a-zA-Z0-9_-]+", label) is not None and command, "Invalid command label")
    OUT.mkdir(parents=True, exist_ok=True)
    log = OUT / (label + ".log")
    require(not log.exists(), "Existing log is never replaced")
    write(OUT / (label + "-command.json"), {"startedAt": now(), "cwd": str(ROOT), "argv": command})
    start = time.monotonic()
    try:
        with log.open("xb") as stream:
            result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT)
        code, error = result.returncode, None
    except BaseException as exc:
        code, error = -1, repr(exc)
        write(OUT / (label + "-exit.json"), {"finishedAt": now(), "exitCode": code,
              "elapsedSeconds": time.monotonic() - start, "error": error,
              "logSha256": sha(log) if log.exists() else None})
        raise
    write(OUT / (label + "-exit.json"), {"finishedAt": now(), "exitCode": code,
          "elapsedSeconds": time.monotonic() - start, "error": error, "logSha256": sha(log)})
    print(f"{label}: exit={code}; {rel(log)}", flush=True)
    if code:
        print(log.read_text(encoding="utf-8", errors="replace")[-5000:])
        raise SystemExit(code)


def java(label, main, *args):
    command = list(read(JAVA))
    command[-1] = main
    run(label, command + list(args))


def normalized_maps(data):
    maps = data.get("gamesPerMap")
    require(isinstance(maps, dict) and maps, "Missing nonempty map membership")
    for key, count in maps.items():
        require(re.fullmatch(r"[0-9a-f]{64}", key) is not None and type(count) is int and count > 0,
                f"Invalid prior map membership: {key}")
    return dict.fromkeys(maps, 1)


def replay_directories():
    # Canonical public replay files only; raw captures, requests and metadata are not loaded.
    directories = set()
    for tree in sorted((ROOT / "replays").glob("cg*")):
        if not tree.is_dir():
            continue
        for path in tree.rglob("*.json"):
            if re.fullmatch(r"[0-9]+\.json", path.name) and not any(
                    part.lower() in {"raw", "_metadata", "rendered"} for part in path.relative_to(tree).parts):
                directories.add(path.parent)
    require(directories, "No saved canonical CG replay directories found")
    return sorted(directories)


def prepare():
    require(not BENCH.exists() and not PRIOR.exists(), "Experiment bench/prior output already exists")
    require(PROTOCOL.is_file(), "Prospective protocol must exist first")
    write(OUT / "intent.json", {"recordedAt": now(), "spec": SPEC, "control": CONTROL,
          "standardRunner": ["tools/run_practical_v4.py", *PRACTICAL_ARGS],
          "scope": "Local strength only; size/process/boss/promotion deferred", "protocol": identity(PROTOCOL),
          "sourceCommit": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=ROOT, text=True).strip()})
    # Register every future block before any game; reject reused seed intervals even before core hash checks.
    intervals = [(SEED + offset, SEED + offset + pairs - 1) for _, _, pairs, offset in practical.BLOCKS]
    own = {practical.pool_path(NAME, block) for block, *_ in practical.BLOCKS}
    for path in (ROOT / "bench/pools").glob("*.json"):
        if path in own:
            continue
        old = read(path)
        if old.get("generator") == "cg-sha1prng" and type(old.get("seedStart")) is int and type(old.get("pairs")) is int:
            start, end = old["seedStart"], old["seedStart"] + old["pairs"] - 1
            require(all(end < a or start > b for a, b in intervals), f"Prior pool seed overlap: {path}")
    pools = [practical.ensure_pool(NAME, block, pairs, SEED + offset)
             for block, _, pairs, offset in practical.BLOCKS]
    directories = replay_directories()
    java("saved-cg-maps", "btk.tools.NeuralV2Verify", "maps",
         ",".join(rel(path) for path in directories), "unused", rel(OUT / "saved-cg-maps.json"))
    cg = read(OUT / "saved-cg-maps.json")
    cg_maps = normalized_maps(cg)
    for item in cg["replays"]:
        require(sha(Path(item["path"])) == item["sha256"], "Saved replay hash drift")
    proposal, selection = read(PROPOSAL), read(SELECTION)
    require(proposal["inputs"]["selectionSha256"] == sha(SELECTION), "Audit selection identity mismatch")
    raw_ref = proposal["fullReport"]
    require(sha(ROOT / raw_ref["pathInArchive"]) == raw_ref["sha256"], "Audit report identity mismatch")
    rows = proposal["games"]
    require(len(rows) == len(selection["games"]) == 8, "Expected eight development audit maps")
    audit = {}
    for selected, row in zip(selection["games"], rows):
        require((selected["slot"], selected["gameId"], selected["sha256"]) ==
                (row["slot"], row["gameId"], row["replaySha256"]), "Audit game lineage mismatch")
        require(sha(ROOT / selected["file"]) == selected["sha256"], "Audit replay hash mismatch")
        audit[row["mapSha256"]] = 1
    require(len(audit) == 8, "Development maps must be distinct")
    normalized_maps({"gamesPerMap": audit})
    prior_file = practical.build_exclusion_index(OUT, "prior")
    prior = read(prior_file)
    union = normalized_maps(prior) | cg_maps | audit
    write(PRIOR, {"purpose": "Prior benchmarks plus all saved canonical CG maps and eight development audit maps",
          "hashScheme": "sha256-utf8-Protocol.initBlock(initialState,0)", "gamesPerMap": union,
          "priorBenchIndex": identity(prior_file), "savedCG": identity(OUT / "saved-cg-maps.json"),
          "auditSummary": identity(PROPOSAL), "auditMaps": audit})
    configs = {}
    for label, spec in (("candidate", SPEC), ("control", CONTROL)):
        java("params-" + label, "btk.tools.BotCatalogTool", spec)
        lines = (OUT / ("params-" + label + ".log")).read_text(encoding="utf-8").splitlines()
        params = dict(match.groups() for line in lines if (match := re.fullmatch(r"  ([A-Z][A-Z0-9_]*)=(.*)", line)))
        require(params and "USE_ROLLOUT_POLICY" in params, "Could not read full effective parameters")
        configs[label] = {"spec": spec, "params": params}
    require(configs["candidate"]["params"].get("USE_NEURAL_TARGET_PROPOSAL") == "true", "Candidate is not enabled")
    require(configs["control"]["params"].get("USE_NEURAL_TARGET_PROPOSAL", "false") == "false", "Control changed")
    write(OUT / "prepared.json", {"recordedAt": now(), "pools": [identity(p) for p in pools], "configs": configs,
          "priorUnion": identity(PRIOR), "priorBenchMaps": len(prior["gamesPerMap"]),
          "priorReports": {name: sha(ROOT / name) for name in prior["sourceFiles"]},
          "savedCGDirectories": [rel(p) for p in directories], "savedCGReplays": len(cg["replays"]),
          "savedCGMaps": len(cg_maps), "developmentAuditMaps": len(audit), "excludedMaps": len(union),
          "allPoolsRegisteredBeforePlay": True})
    print(f"Prepared three pools and {len(union)} excluded map identities", flush=True)


def inventory():
    patterns = ["src/main/java/**/*.java", "src/main/resources/**/*", "src/test/java/**/*.java",
                "build/classes/java/main/**/*.class", "build/resources/main/**/*", "build/test-results/test/TEST-*.xml",
                "tools/practical_gate*.py", "tools/boss*.py", "tools/run_practical_v4.py",
                "tools/test_run_practical_v4.py", "gradlew", "gradlew.bat", "gradle/wrapper/*", "build.gradle.kts"]
    return {p.resolve() for pattern in patterns for p in ROOT.glob(pattern) if p.is_file()}


def freeze():
    prepared = read(OUT / "prepared.json")
    require(not BENCH.exists(), "Freeze must precede the first strength invocation")
    baseline = OUT / "baseline/Player.java"
    require(baseline.is_file() and sha(baseline) == "ab0954a4545f25222fce6bccf8fa11866d276266e96b4728b502c83996d847e5",
            "Fresh unchanged default baseline artifact is required in analysis/baseline")
    xml_files = sorted((ROOT / "build/test-results/test").glob("TEST-*.xml"))
    require(xml_files, "Preflight JUnit XML is missing")
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    for path in xml_files:
        root = ET.parse(path).getroot()
        for key in totals:
            totals[key] += int(root.attrib.get(key, 0))
    require(totals["tests"] > 0 and totals["failures"] == totals["errors"] == 0, "Preflight tests failed")
    tests = OUT / "preflight-tests"
    tests.mkdir()
    for path in xml_files:
        with (tests / path.name).open("xb") as stream:
            stream.write(path.read_bytes())
    write(tests / "counts.json", totals)
    saved = OUT / "protocol-before-results.md"
    with saved.open("xb") as stream:
        stream.write(PROTOCOL.read_bytes())
    files = inventory() | {PROTOCOL, Path(__file__).resolve(), JAVA, PROPOSAL, SELECTION, PRIOR}
    files.update(ROOT / name for name in prepared["priorReports"])
    files.update(ROOT / entry["path"] for entry in prepared["pools"])
    files.update(p for p in OUT.rglob("*") if p.is_file())
    files.update(Path(entry["path"]).resolve() for entry in read(OUT / "saved-cg-maps.json")["replays"])
    files.update(ROOT / entry["file"] for entry in read(SELECTION)["games"])
    command = read(JAVA)
    external = {Path(command[0]), Path(sys.executable)}
    external.update(Path(s) for s in command[command.index("-cp") + 1].split(os.pathsep) if s.endswith(".jar"))
    java_home = Path(command[0]).parent.parent
    external.update(p for p in [java_home / "release", java_home / "bin/server/jvm.dll", java_home / "lib/modules"] if p.is_file())
    write(OUT / "freeze.json", {"recordedAt": now(), "sourceCommit": read(OUT / "intent.json")["sourceCommit"],
          "files": {rel(p): sha(p) for p in sorted(files)}, "inventory": sorted(rel(p) for p in inventory()),
          "runtime": {str(p): sha(p) for p in sorted(external)}, "configs": prepared["configs"],
          "gitStatus": subprocess.check_output(["git", "status", "--short"], cwd=ROOT, text=True)})
    print(f"Frozen {len(files)} files and {len(external)} runtimes", flush=True)


def verify(allow_results=False):
    pins = read(OUT / "freeze.json")
    require(sorted(rel(p) for p in inventory()) == pins["inventory"], "Source/runtime inventory changed")
    for name, digest in pins["files"].items():
        path = ROOT / name
        if allow_results and path == PROTOCOL:
            original = OUT / "protocol-before-results.md"
            require(sha(original) == digest and path.read_bytes().startswith(original.read_bytes()), "Protocol was not append-only")
        else:
            require(path.is_file() and sha(path) == digest, f"Frozen file changed: {name}")
    for name, digest in pins["runtime"].items():
        require(sha(Path(name)) == digest, f"Runtime changed: {name}")
    return {"recordedAt": now(), "unchangedFiles": len(pins["files"]), "unchangedRuntimes": len(pins["runtime"]), "changedFiles": []}


def checked_block(report, block):
    data = report["data"]
    prepared = read(OUT / "prepared.json")
    number = [x[0] for x in practical.BLOCKS].index(block)
    _, games, pairs, offset = practical.BLOCKS[number]
    require((data["a"], data["b"], data["threads"], data["baseSeed"]) == (SPEC, CONTROL, 2, SEED + offset), "Block settings drift")
    require(data["paramsA"] == prepared["configs"]["candidate"]["params"] and
            data["paramsB"] == prepared["configs"]["control"]["params"], "Effective configuration drift")
    pool = practical.pool_path(NAME, block)
    require(data["poolSha256"] == sha(pool) and data["poolId"] == NAME + "-" + block, "Pool identity drift")
    require([row["seed"] for row in data["pairs"]] == list(range(SEED + offset, SEED + offset + pairs)), "Unexpected map seed order")
    excluded = BENCH / (block + "-excluded.json")
    actual_excluded = {name.replace("\\", "/"): value for name, value in data["excludedReports"].items()}
    require(actual_excluded == {rel(excluded): sha(excluded)}, "Actual exclusion input drift")
    used = set(normalized_maps(read(PRIOR)))
    for previous, *_ in practical.BLOCKS[:number]:
        used.update(read(BENCH / (previous + ".json"))["gamesPerMap"])
    require(used <= set(normalized_maps(read(excluded))), "Exclusion omitted prior/development/CG maps")
    require(not used.intersection(report["maps"]), "A measured map was exposed earlier")
    return {"block": block, "games": games, "freshPairedMaps": pairs, "report": identity(BENCH / (block + ".json")),
            "excluded": identity(excluded), "paramsMatched": True, "freshnessMatched": True}


def strength_child():
    require(not BENCH.exists(), "No rerun/resume: preserve existing strength outputs")
    write(OUT / "before-strength-verification.json", verify())
    original = practical.run_block

    def guarded(*args):
        verify()
        try:
            report = original(*args)
        except BaseException as error:
            write(OUT / (args[3] + "-failure-verification.json"), {"error": repr(error), "verification": verify()})
            raise
        audit = checked_block(report, args[3])
        audit["verification"] = verify()
        write(OUT / (args[3] + "-verification.json"), audit)
        return report

    practical.run_block = guarded
    sys.argv = [str(ROOT / "tools/run_practical_v4.py"), *PRACTICAL_ARGS]
    practical.main()
    write(OUT / "after-strength-verification.json", verify())
    print("Local sequence finished; no boss games, candidate process check, packaging or promotion was launched.", flush=True)


def summary():
    verification = verify()
    reports, blocks = [], []
    for block, games, *_ in practical.BLOCKS:
        path = BENCH / (block + ".json")
        if not path.exists():
            break
        report = practical.read_report(path, games)
        blocks.append(checked_block(report, block))
        reports.append(report)
        require(read(BENCH / (block + "-decision.json")) == practical.gate.assess(*reports), "Saved decision differs from standard gate")
    require(reports and all(not (BENCH / (x[0] + ".json")).exists() for x in practical.BLOCKS[len(reports):]), "Noncontiguous reports")
    result = practical.gate.assess(*reports)
    require(result["directStage"] in {"STOP_FUTILITY_300", "STOP_FUTILITY_500", "NOT_PASSED", "PASS_1000"}, "Local stage incomplete")
    require((OUT / "after-strength-verification.json").is_file() and read(OUT / "strength-exit.json")["exitCode"] == 0, "Strength invocation incomplete")
    data = [r["data"] for r in reports]
    counters, candidate_games, remaining_min, remaining_max = {}, 0, [], []
    for item in data:
        for pair in item["pairs"]:
            for seat, key in enumerate(("aAsP0", "aAsP1")):
                diagnostic = pair[key].get("targetProposalP" + str(seat))
                require(isinstance(diagnostic, dict) and diagnostic, "Missing candidate after-game diagnostics")
                require(pair[key].get("targetProposalP" + str(1 - seat)) is None, "Control unexpectedly has candidate diagnostics")
                for name, value in diagnostic.items():
                    require(type(value) is int and value >= 0, "Invalid candidate diagnostic count")
                    if name not in {"remainingNanosMin", "remainingNanosMax"}:
                        counters[name] = counters.get(name, 0) + value
                require(diagnostic["remainingNanosMin"] <= diagnostic["remainingNanosMax"], "Invalid remaining-time extrema")
                if diagnostic["remainingSamples"]:
                    remaining_min.append(diagnostic["remainingNanosMin"])
                    remaining_max.append(diagnostic["remainingNanosMax"])
                else:
                    require(diagnostic["remainingNanosMin"] == diagnostic["remainingNanosMax"] == 0,
                            "Unsampled extrema must have the declared zero convention")
                candidate_games += 1
    write(OUT / "final-verification.json", verification)
    write(SUMMARY, {"schema": NAME, "recordedAt": now(), "sourceCommit": read(OUT / "intent.json")["sourceCommit"],
          "candidate": SPEC, "control": CONTROL, "selectedBot": CONTROL, "promotion": False,
          "scope": "Local native strength only; source packaging, exact candidate process and boss checks deferred",
          "localDecision": result, "games": sum(d["games"] for d in data), "freshPairedMaps": sum(len(r["maps"]) for r in reports),
          "wins": sum(d["winsA"] for d in data), "losses": sum(d["winsB"] for d in data), "draws": sum(d["draws"] for d in data),
          "nativeSlowCalls": [sum(d["slowA"] for d in data), sum(d["slowB"] for d in data)],
          "nativeMaxMs": [max(d["maxMsA"] for d in data), max(d["maxMsB"] for d in data)],
          "errors": [sum(d["errorsA"] for d in data), sum(d["errorsB"] for d in data)],
          "candidateDiagnosticGames": candidate_games, "candidateDiagnosticTotals": counters,
          "candidateRemainingBudgetNanos": {"samples": counters["remainingSamples"],
              "total": counters["remainingNanosTotal"], "min": min(remaining_min) if remaining_min else 0,
              "max": max(remaining_max) if remaining_max else 0},
          "blocks": blocks, "prepared": identity(OUT / "prepared.json"), "freeze": identity(OUT / "freeze.json"),
          "verification": verification, "candidateProcessCheck": False, "bossGames": 0, "candidateSubmissionCreated": False,
          "limits": ["Native timing is not an external-process timing guarantee.",
                     "Only the predeclared local futility/1000-game decision is assessed; there is no promotion.",
                     "Any packaging or further strategy work requires a separate next stage."]})


def archive():
    verify(allow_results=True)
    require(SUMMARY.is_file(), "Completed summary required")
    files = {p for folder in [OUT, BENCH, PRIOR.parent] for p in folder.rglob("*") if p.is_file()}
    files.update({PROTOCOL, SUMMARY, Path(__file__).resolve(), JAVA})
    files.add(SELECTION)
    files.update(ROOT / game["file"] for game in read(SELECTION)["games"])
    files.update(practical.pool_path(NAME, x[0]) for x in practical.BLOCKS)
    for pattern in ["src/main/java/btk/core/*.java", "src/main/java/btk/bot/*.java", "src/main/java/btk/bot/current/*.java",
                    "src/main/java/btk/bot/v09/*.java", "src/main/java/btk/runner/*.java", "src/main/java/btk/referee/*.java",
                    "src/main/java/btk/tools/target/*.java", "src/test/java/btk/tools/target/*.java",
                    "src/main/java/btk/submit/*.java", "src/main/resources/btk/**/*", "tools/practical_gate*.py", "tools/boss*.py"]:
        files.update(p for p in ROOT.glob(pattern) if p.is_file())
    files.update(ROOT / name for name in ["tools/run_practical_v4.py", "build.gradle.kts", "src/main/java/btk/bot/neural/NeuralNet.java",
                  "src/main/java/btk/bot/neural/NeuralV2Weights.java", "src/main/java/btk/tools/NeuralV2Verify.java",
                  "src/test/java/btk/submit/LocalTargetPrototypeTest.java", "docs/evidence/neural-target-feasibility-v1.json"])
    destination = ROOT / "docs/evidence" / (NAME + "-artifacts.zip")
    with zipfile.ZipFile(destination, "x", zipfile.ZIP_DEFLATED) as output:
        for path in sorted(files):
            output.write(path, rel(path))
    with zipfile.ZipFile(destination) as check:
        require(check.testzip() is None, "Archive CRC failure")
        require(all(hashlib.sha256(check.read(rel(p))).hexdigest() == sha(p) for p in files), "Archive member mismatch")
    write(destination.with_suffix(".json"), {"archive": identity(destination), "files": [identity(p) for p in sorted(files)],
          "dependencies": [identity(ROOT / "docs/evidence/neural-target-feasibility-v1-artifacts.zip")],
          "scope": "New local experiment, source/runtime hashes, test XML, local reports/logs, eight public audit replays and relevant code. Other prior/CG inputs are hash-referenced; no private capture/auth files."})


def main():
    mode, *args = sys.argv[1:]
    if mode == "prepare": prepare()
    elif mode == "run": run(args[0], args[1:])
    elif mode == "java": java(args[0], args[1], *args[2:])
    elif mode == "freeze": freeze()
    elif mode == "strength": run("strength", [sys.executable, str(Path(__file__).resolve()), "_strength"])
    elif mode == "_strength": strength_child()
    elif mode == "verify": write(OUT / ((args[0] if args else "verification") + ".json"), verify())
    elif mode == "summary": summary()
    elif mode == "archive": archive()
    else: raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
