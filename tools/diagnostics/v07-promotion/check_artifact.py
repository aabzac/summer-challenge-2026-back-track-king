"""Fixed eight-game process panel for the newly packaged v07; no retries or uploads."""
import hashlib
import importlib.util
import json
from pathlib import Path
import subprocess
import sys

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[3]
WORK = ROOT / "build/analysis/v07-promotion"
DURABLE = ROOT / "docs/evidence/v07-promotion.json"
HELPER = ROOT / "tools/diagnostics/network-after-base-v1/check_artifact.py"
spec = importlib.util.spec_from_file_location("prior_artifact_helpers", HELPER)
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)
h.WORK = WORK


def execute(args, name):
    log = WORK / (name + ".log")
    started = h.now()
    with log.open("x", encoding="utf-8") as out:
        process = subprocess.Popen(args, cwd=ROOT, stdout=subprocess.PIPE,
                                   stderr=subprocess.STDOUT, text=True, encoding="utf-8",
                                   errors="replace", creationflags=subprocess.CREATE_NO_WINDOW)
        try:
            for line in process.stdout:
                out.write(line)
                out.flush()
                print(line, end="", flush=True)
            code = process.wait()
        except BaseException:
            if process.poll() is None:
                subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"],
                               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                               creationflags=subprocess.CREATE_NO_WINDOW, check=False)
            raise
    return {"command": args, "exitCode": code, "startedAt": started,
            "finishedAt": h.now(), "logSha256": h.sha(log)}


def main():
    h.require(not DURABLE.exists() and not (WORK / "process-started.json").exists(),
              "Preserve completed or interrupted attempts; no retry")
    before = h.read(WORK / "before.json")
    manifest = h.read(ROOT / "build/submit/manifest.json")
    h.require(manifest["strategy"] == "current" and manifest["params"] == before["parameters"],
              "Main submission must use the exact measured candidate parameters")
    player = ROOT / "build/submit/Player.java"
    h.require(h.sha(player) == manifest["sha256"], "Manifest/source mismatch")
    old = ROOT / "build/analysis/network-after-base-v1/artifact-workspace/build/submit/Player.java"
    expected = old.read_bytes()
    for flag in ("USE_ROLLOUT_QUICK_ROUTE", "USE_ROLLOUT_NETWORK_AFTER_BASE"):
        find = ("public boolean " + flag + " = false;").encode()
        h.require(expected.count(find) == 1, "Unexpected measured default declaration")
        expected = expected.replace(find, ("public boolean " + flag + " = true;").encode())
    h.require(player.read_bytes() == expected,
              "Packaged source must differ only in the two explicitly enabled defaults")
    for group in ("frozenPrior", "frozenSnapshots", "evidence"):
        h.verify_entries(ROOT, before[group], group)
    h.verify_entries(WORK / "previous-submit", before["previousSubmission"], "Archived v06")
    artifact = h.tree(ROOT / "build/submit", ROOT)
    inputs = {}
    for folder in ("src/main/java", "src/main/resources", "build/classes/java/main", "build/resources/main"):
        inputs.update(h.tree(ROOT / folder, ROOT))
    result = {"schemaVersion": 1, "version": "v07", "startedAt": h.now(),
              "beforeSha256": h.sha(WORK / "before.json"), "protocolSha256": h.sha(ROOT / "docs/v07-promotion.md"),
              "runnerSha256": h.sha(Path(__file__)), "helperSha256": h.sha(HELPER),
              "sourceRevision": before["sourceRevision"], "manifest": manifest,
              "policySourceUnchanged": True, "onlySourceChanges": ["QUICK_ROUTE default false to true", "NETWORK_AFTER_BASE default false to true"],
              "measuredCandidateSha256": h.sha(old), "artifactFileHashes": artifact,
              "inputIdentities": inputs, "priorFrozenIdentities": before["frozenPrior"],
              "priorSnapshotIdentities": before["frozenSnapshots"], "priorEvidenceIdentities": before["evidence"],
              "previousSubmissionIdentities": before["previousSubmission"],
              "maps": [], "commands": [], "errors": [], "processReadiness": "incomplete"}
    h.save(WORK / "process-started.json", result)
    failure = None
    try:
        with h.power_request():
            executable_hash = None
            for map_name in before["processMaps"]:
                h.verify_entries(ROOT, inputs, "Source/compiled inputs")
                h.require(h.tree(ROOT / "build/submit", ROOT) == artifact, "Exact artifact changed")
                game_id = Path(map_name).stem
                map_path = ROOT / map_name
                map_hash = h.sha(map_path)
                report_path = WORK / ("process-" + game_id + ".json")
                h.require(not report_path.exists(), "Never overwrite a report")
                args = [str(ROOT / "gradlew.bat"), "procCheck", "--console=plain",
                        "-x", "compileJava", "-x", "processResources", "-Pbot=v05", "-Pgames=2",
                        "-Pmap=" + str(map_path), "-PgraceMs=0", "-PprocessDiagnostics=false",
                        "-Pout=" + str(report_path)]
                command = execute(args, "process-" + game_id)
                result["commands"].append(command)
                report = h.read(report_path)
                h.require(report["sourceSha256"] == manifest["sha256"], "Executed source mismatch")
                if executable_hash is None:
                    executable_hash = report["classesSha256"]
                h.require(report["classesSha256"] == executable_hash, "Executed class identity changed")
                launch = h.read(ROOT / "build/bench/network-after-base-v1/launch.json")
                h.require(report["opponent"] == "v05" and report["opponentParams"] == launch["resolvedParameters"]["v05"], "Control changed")
                h.require(report["strict"] and report["graceMs"] == 0 and not report["ioDiagnostics"], "Changed deadlines")
                games = report["games"]
                h.require(len(games) == 2 and [g["player"] for g in games] == [0, 1], "Incomplete sides")
                h.require(all(Path(g["map"]).name == game_id + ".json" for g in games), "Wrong map")
                h.require(h.sha(map_path) == map_hash, "Map changed during run")
                problems = sum(g["timeouts"] > 0 or any(e is not None for e in g["result"]["error"]) for g in games)
                h.require(report["problems"] == problems and (command["exitCode"] == 0) == (problems == 0), "Status/report mismatch")
                result["maps"].append({"gameId": game_id, "map": map_name, "mapSha256": map_hash,
                                       "reportSha256": h.sha(report_path), "classesSha256": executable_hash,
                                       "problems": problems, "games": [h.summary_game(g) for g in games]})
            result["gamesChecked"] = sum(len(m["games"]) for m in result["maps"])
            result["problemGames"] = sum(m["problems"] for m in result["maps"])
            h.require(result["gamesChecked"] == 8, "Incomplete fixed panel")
            result["processReadiness"] = "PASS" if result["problemGames"] == 0 else "NOT_PASSED"
    except BaseException as error:
        result["processReadiness"] = "ERROR"
        result["errors"].append(repr(error))
        failure = error
    finally:
        try:
            h.verify_entries(ROOT, inputs, "Source/compiled inputs")
            h.require(h.tree(ROOT / "build/submit", ROOT) == artifact, "Exact artifact changed")
            h.verify_entries(WORK / "previous-submit", before["previousSubmission"], "Archived v06")
            result["allFrozenInputsUnchanged"] = True
        except BaseException as error:
            result["errors"].append(repr(error))
            result["allFrozenInputsUnchanged"] = False
            result["processReadiness"] = "ERROR"
            failure = failure or error
        power = WORK / "artifact-power-request.json"
        if power.exists():
            result["powerRequest"] = h.read(power)
        result["finishedAt"] = h.now()
        h.save(DURABLE, result)
    print("PROMOTION PROCESS", result["processReadiness"], "problem games", result.get("problemGames"), flush=True)
    if failure:
        raise failure
    return 0 if result["processReadiness"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
