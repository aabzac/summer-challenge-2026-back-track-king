"""Write-once local evidence phases for neural-component-audit-v1.

No selection changes, network calls, model inference or fitting. Explicit run/java
phases execute only the caller's command. Prepare retains the exact prior selection
and public replays; private request/capture bodies are never read or archived.
"""
from pathlib import Path
import hashlib
import json
import os
import subprocess
import sys
import time
import zipfile
import xml.etree.ElementTree as ET

from neural_proposal_audit import ROOT, files, read, relative, sha, stamp, write

OUT = ROOT / "build/analysis/neural-component-audit-v1"
PRIOR = ROOT / "build/analysis/neural-proposal-audit-v1"
PROTOCOL = ROOT / "docs/neural-component-audit-v1.md"
SUMMARY = ROOT / "docs/evidence/neural-component-audit-v1.json"
PRIOR_SUMMARY = ROOT / "docs/evidence/neural-proposal-audit-v1.json"
PRIOR_MANIFEST = ROOT / "docs/evidence/neural-proposal-audit-v1-artifacts.json"
PRIOR_ARCHIVE = ROOT / "docs/evidence/neural-proposal-audit-v1-artifacts.zip"
JAVA_COMMAND = ROOT / "build/spatial-v10-java.json"
FOCUS = "btk.tools.NeuralComponentStudyTest"
INDEX_DOCS = ("README.md", "docs/PLAN.md", "docs/BOTS.md", "docs/DEVLOG.md")
DEPENDENCIES = ("CgReplay", "CgValidate", "ReplayPaths", "ReplyStudy", "ReplyProbe",
                "CgDisruptStudy", "RolloutFillStudy", "NeuralProposalStudy", "NeuralComponentStudy")


def require(ok, message):
    if not ok:
        raise ValueError(message)


def ref(path):
    return {"path": relative(path), "sha256": sha(path), "bytes": path.stat().st_size}


def local(name):
    path = (ROOT / name).resolve()
    require(path.is_relative_to(ROOT) and path.is_file(), "Missing or nonlocal evidence path")
    return path


def copy_new(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open("xb") as stream:
        stream.write(source.read_bytes())
        stream.flush()
        os.fsync(stream.fileno())


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def prior_inputs():
    """Bind the completed prior evidence without rereading private 40-game records."""
    summary, manifest = read(PRIOR_SUMMARY), read(PRIOR_MANIFEST)
    selection_path, report_path = PRIOR / "selection.json", PRIOR / "results.json"
    selection, report = read(selection_path), read(report_path)
    require(sha(PRIOR_ARCHIVE) == manifest["archiveSha256"]
            and PRIOR_ARCHIVE.stat().st_size == manifest["archiveBytes"], "Prior archive changed")
    require(summary["fullReport"]["pathInArchive"] == relative(report_path)
            and summary["fullReport"]["sha256"] == sha(report_path), "Prior raw report binding changed")
    require(summary["inputs"]["selectionSha256"] == sha(selection_path)
            and report["selectionSha256"] == sha(selection_path)
            and report["status"] == "complete" and report["complete"] is True,
            "Prior completed report/selection binding changed")
    require(selection["schema"] == "neural-proposal-selection-v1"
            and selection["policy"] == "neural-v3"
            and selection["turns"] == [3, 10, 20, 30, 40, 50, 60, 70]
            and len(selection["games"]) == 8
            and [g["slot"] for g in selection["games"]] == list(range(1, 9)),
            "Original fixed selection differs")
    require(all(report[k] == summary[k] == selection[k] for k in ("buildId", "sourceSha256")),
            "Prior policy identity mismatch")
    require([sum(g["me"] == seat for g in selection["games"]) for seat in (0, 1)] == [4, 4],
            "Original seat balance differs")
    replays = []
    for game in selection["games"]:
        path = local(game["file"])
        require(path.parent == PRIOR / "replays" and path.name == f'{game["gameId"]}.json'
                and sha(path) == game["sha256"], "Selected public replay changed")
        replays.append(path)
    require(len(set(replays)) == 8, "Duplicate selected replay")
    # Git stores LF while this workspace uses CRLF. Permit only that checkout
    # transformation here; raw working bytes are separately bound to the archive.
    for path in (PRIOR_SUMMARY, PRIOR_MANIFEST):
        require(git("show", "HEAD:" + relative(path)).replace(b"\r\n", b"\n")
                == path.read_bytes().replace(b"\r\n", b"\n"),
                "Prior compact evidence differs from committed bytes")
    archived = [PRIOR_SUMMARY, selection_path, report_path, *replays]
    with zipfile.ZipFile(PRIOR_ARCHIVE) as archive:
        for path in archived:
            item = manifest["files"][relative(path)]
            require(sha(path) == item["sha256"] and path.stat().st_size == item["bytes"]
                    and archive.read(relative(path)) == path.read_bytes(),
                    "Prior archive entry differs from retained evidence")
    return selection, [PRIOR_SUMMARY, PRIOR_MANIFEST, PRIOR_ARCHIVE, selection_path,
                       report_path, *replays]


def prepare():
    require(not OUT.exists(), "Prepare requires a fresh output directory")
    require(PROTOCOL.is_file() and PROTOCOL.stat().st_size > 0, "Prospective protocol required")
    selection, prior = prior_inputs()
    preparation = {
        "schema": "neural-component-preparation-v1", "status": "prepared", "recordedAt": stamp(),
        "command": {"cwd": str(Path.cwd()), "argv": [sys.executable, *sys.argv]},
        "baseRevision": git("rev-parse", "HEAD").decode().strip(),
        "priorEvidenceCommit": git("log", "-1", "--format=%H", "--", relative(PRIOR_SUMMARY)).decode().strip(),
        "committedEvidenceCheck": "Exact committed text with checkout LF/CRLF equivalence only; "
                                  "retained raw-byte hashes are checked independently",
        "priorInputs": [ref(path) for path in prior], "protocol": ref(PROTOCOL),
        "helper": ref(Path(__file__).resolve()),
        "selectionSha256": sha(PRIOR / "selection.json"),
        "selectionPaths": "Byte-exact original selection; file paths still refer to original public replays",
        "copiedReplays": [{"file": relative(OUT / "replays" / path.name), "sha256": sha(path)}
                          for path in prior if path.parent == PRIOR / "replays"],
        "selectedGames": len(selection["games"]), "requestedPositions": 64,
        "selectionChanged": False, "scoringPerformed": False,
        "privacy": "Only public canonical replays and protocol are copied. No plan, intent, result-capture, "
                   "raw request, authentication, session or API-error bodies are read or archived here.",
        "sourceFreeze": "Separate phase after coordinated preflight",
    }
    OUT.mkdir(parents=True, exist_ok=False)
    copy_new(PRIOR / "selection.json", OUT / "selection.json")
    for path in prior:
        if path.parent == PRIOR / "replays":
            copy_new(path, OUT / "replays" / path.name)
    copy_new(PROTOCOL, OUT / "protocol-before-results.md")
    write(OUT / "preparation.json", preparation)
    print("Prepared exact prior selection and eight public replays; no measurement executed")


def run(label, command):
    require(label.replace("-", "").isalnum() and command, "Invalid command label/arguments")
    require((OUT / "preparation.json").is_file(), "Prepare before recording commands")
    paths = [OUT / (label + suffix) for suffix in ("-command.json", ".log", "-exit.json")]
    require(not any(path.exists() for path in paths), "Command label already used")
    write(paths[0], {"startedAt": stamp(), "cwd": str(ROOT), "argv": command})
    tick = time.monotonic()
    try:
        with paths[1].open("xb") as log:
            result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    except OSError as error:
        write(paths[2], {"finishedAt": stamp(), "exitCode": None, "launchError": str(error),
                         "elapsedSeconds": time.monotonic() - tick})
        raise
    write(paths[2], {"finishedAt": stamp(), "exitCode": result.returncode,
                     "elapsedSeconds": time.monotonic() - tick})
    print(paths[1].read_text(encoding="utf-8", errors="replace")[-6500:])
    return result.returncode


def capture_tests():
    target = OUT / "tests"
    require((OUT / "preparation.json").exists() and not target.exists(), "Fresh prepared test capture required")
    paths = sorted((ROOT / "build/test-results/test").glob("TEST-*.xml"))
    require(any(path.name == "TEST-" + FOCUS + ".xml" for path in paths), "Focused test report missing")
    totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    focused = None
    for path in paths:
        suite = ET.parse(path).getroot()
        counts = {key: int(suite.attrib.get(key, 0)) for key in totals}
        for key in totals:
            totals[key] += counts[key]
        if path.name == "TEST-" + FOCUS + ".xml":
            require(suite.attrib.get("name") == FOCUS and counts["tests"] > 0, "Invalid focused test suite")
            focused = counts
    require(totals["tests"] > 0 and not any(totals[k] for k in ("failures", "errors", "skipped")),
            "Tests failed, errored or skipped")
    target.mkdir()
    for path in paths:
        copy_new(path, target / path.name)
    write(target / "counts.json", {**totals, "focusClass": FOCUS, "focused": focused})
    print(json.dumps(totals))


def runtime_refs():
    command = read(JAVA_COMMAND)
    require(isinstance(command, list) and command and "-cp" in command, "Invalid Java command")
    paths = [Path(command[0]), Path(sys.executable)]
    paths += [Path(value) for value in command[command.index("-cp") + 1].split(os.pathsep)
              if Path(value).is_file()]
    return [{"path": str(path.resolve()), "sha256": sha(path)} for path in sorted(set(paths))]


def pinned_inputs(allow_protocol_append=False):
    prepared = read(OUT / "preparation.json")
    for item in prepared["priorInputs"]:
        require(sha(local(item["path"])) == item["sha256"], "Prepared prior input changed")
    prospective = OUT / "protocol-before-results.md"
    require(sha(prospective) == prepared["protocol"]["sha256"], "Prospective protocol copy changed")
    require(PROTOCOL.read_bytes().startswith(prospective.read_bytes()) if allow_protocol_append
            else PROTOCOL.read_bytes() == prospective.read_bytes(),
            "Protocol must preserve exact prospective text; only archive permits appended results")
    require((OUT / "selection.json").read_bytes() == (PRIOR / "selection.json").read_bytes(),
            "Prior selection copy changed")
    return prepared


def freeze():
    prepared = pinned_inputs()
    require((OUT / "tests/counts.json").is_file(), "Capture passing preflight tests before freeze")
    require(not (OUT / "results.json").exists(), "Freeze must precede measurement")
    pinned = files("src/main/java/**/*.java", "src/main/resources/**/*",
                   "build/classes/java/main/**/*.class", "build/resources/main/**/*",
                   "src/test/java/btk/tools/NeuralComponentStudyTest.java",
                   "src/test/java/btk/tools/NeuralProposalStudyTest.java",
                   "tools/diagnostics/neural_component_audit.py", "tools/diagnostics/neural_proposal_audit.py",
                   "build/spatial-v10-java.json", "build.gradle.kts",
                   "build/analysis/neural-component-audit-v1/**/*") + [PROTOCOL]
    pinned += [local(item["path"]) for item in prepared["priorInputs"]]
    require((ROOT / "src/main/java/btk/tools/NeuralComponentStudy.java") in pinned
            and (ROOT / "src/test/java/btk/tools/NeuralComponentStudyTest.java") in pinned,
            "Diagnostic source/test missing from freeze")
    write(OUT / "freeze.json", {"recordedAt": stamp(), "baseRevision": prepared["baseRevision"],
          "files": {relative(path): sha(path) for path in sorted(set(pinned))}, "runtime": runtime_refs()})
    print("Frozen", len(set(pinned)), "files")


def check_freeze(allow_protocol_append=False):
    frozen = read(OUT / "freeze.json")
    changed = [name for name, digest in frozen["files"].items()
               if not (allow_protocol_append and name == relative(PROTOCOL))
               and (not (ROOT / name).is_file() or sha(ROOT / name) != digest)]
    changed += [item["path"] for item in frozen["runtime"]
                if not Path(item["path"]).is_file() or sha(Path(item["path"])) != item["sha256"]]
    require(not changed, "Frozen inputs changed: " + repr(changed))
    if allow_protocol_append:
        prospective = OUT / "protocol-before-results.md"
        require(sha(prospective) == frozen["files"][relative(PROTOCOL)]
                and PROTOCOL.read_bytes().startswith(prospective.read_bytes()),
                "Only an appended results section may change the final protocol")
    return frozen


def verify():
    frozen = check_freeze()
    write(OUT / "verification.json", {"recordedAt": stamp(), "unchangedFiles": len(frozen["files"]),
          "unchangedRuntimeFiles": len(frozen["runtime"]), "changedFiles": []})
    print("Verified", len(frozen["files"]), "unchanged files")


def archive():
    check_freeze(allow_protocol_append=True)  # Recheck all other bytes after documentation.
    require(read(OUT / "verification.json")["changedFiles"] == [], "Successful verification required")
    prepared = pinned_inputs(allow_protocol_append=True)
    included = files("build/analysis/neural-component-audit-v1/**/*",
                     "src/main/java/btk/core/*.java", "src/main/java/btk/bot/current/*.java",
                     "src/main/java/btk/bot/v09/*.java", "src/main/java/btk/bot/*.java",
                     "src/main/resources/btk/bot/versions/v09.properties",
                     "src/main/java/btk/referee/Replay.java",
                     "src/test/java/btk/tools/NeuralComponentStudyTest.java",
                     "src/test/java/btk/tools/NeuralProposalStudyTest.java",
                     "tools/diagnostics/neural_component_audit.py", "tools/diagnostics/neural_proposal_audit.py")
    included += [ROOT / "src/main/java/btk/tools" / (name + ".java") for name in DEPENDENCIES]
    included += [local(item["path"]) for item in prepared["priorInputs"]
                 if item["path"] != relative(PRIOR_ARCHIVE)]
    included += [PROTOCOL, SUMMARY, JAVA_COMMAND, ROOT / "build.gradle.kts", *[ROOT / n for n in INDEX_DOCS]]
    included = sorted(set(included))
    require(all(path.is_file() for path in included), "Required archive input missing")
    require(not any("private" in path.relative_to(ROOT).parts or path.name in
                    ("intent.json", "play-response.json", "plan.json") for path in included),
            "Private/request evidence cannot be archived")
    target = ROOT / "docs/evidence/neural-component-audit-v1-artifacts.zip"
    manifest_path = target.with_suffix(".json")
    require(not target.exists() and not manifest_path.exists(), "Archive is write-once")
    manifest = {relative(path): {"sha256": sha(path), "bytes": path.stat().st_size} for path in included}
    with zipfile.ZipFile(target, "x", zipfile.ZIP_DEFLATED) as bundle:
        for path in included:
            bundle.write(path, relative(path))
    with zipfile.ZipFile(target) as bundle:
        require(bundle.testzip() is None, "Archive CRC failed")
        for name, item in manifest.items():
            require(hashlib.sha256(bundle.read(name)).hexdigest() == item["sha256"], "Archive bytes changed")
    write(manifest_path, {"recordedAt": stamp(), "archiveSha256": sha(target),
                         "archiveBytes": target.stat().st_size, "files": manifest})
    print("Archive verified", len(included), "files", target.stat().st_size, "bytes")


def main():
    require(len(sys.argv) >= 2, "Phase required: prepare/run/java/tests/freeze/verify/archive")
    phase = sys.argv[1]
    if phase == "run":
        require(len(sys.argv) >= 4, "run label command [args...]")
        raise SystemExit(run(sys.argv[2], sys.argv[3:]))
    if phase == "java":
        require(len(sys.argv) >= 4, "java label main-class [args...]")
        command = read(JAVA_COMMAND)
        command[-1] = sys.argv[3]
        raise SystemExit(run(sys.argv[2], command + sys.argv[4:]))
    require(len(sys.argv) == 2, "Unexpected phase arguments")
    handlers = {"prepare": prepare, "tests": capture_tests, "freeze": freeze,
                "verify": verify, "archive": archive}
    require(phase in handlers, "Unknown phase")
    handlers[phase]()


if __name__ == "__main__":
    main()
