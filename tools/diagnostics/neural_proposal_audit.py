"""Write-once run records and evidence bundle for the saved-action v09 audit.

This helper runs explicit local commands and preserves their evidence. It never
trains, uploads, changes a policy or chooses another experimental sample.
"""
from pathlib import Path
from datetime import datetime, timezone
import hashlib
import json
import subprocess
import sys
import time
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/analysis/neural-proposal-audit-v1"
PROTOCOL = ROOT / "docs/neural-proposal-audit-v1.md"


def stamp():
    return datetime.now(timezone.utc).isoformat()


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def relative(path):
    return path.relative_to(ROOT).as_posix()


def sha(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def write(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8") as stream:
        json.dump(data, stream, indent=2, allow_nan=False)
        stream.write("\n")


def files(*patterns):
    return sorted({path for pattern in patterns for path in ROOT.glob(pattern) if path.is_file()})


def run(label, command):
    assert label.replace("-", "").isalnum() and command
    write(OUT / (label + "-command.json"), {"startedAt": stamp(), "cwd": str(ROOT), "argv": command})
    tick = time.monotonic()
    with (OUT / (label + ".log")).open("xb") as log:
        result = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT)
    write(OUT / (label + "-exit.json"), {"finishedAt": stamp(), "exitCode": result.returncode,
          "elapsedSeconds": time.monotonic() - tick})
    print((OUT / (label + ".log")).read_text(encoding="utf-8", errors="replace")[-6500:])
    return result.returncode


def main():
    phase = sys.argv[1]
    if phase == "run":
        raise SystemExit(run(sys.argv[2], sys.argv[3:]))
    if phase == "java":
        command = read(ROOT / "build/spatial-v10-java.json")
        command[-1] = sys.argv[3]
        raise SystemExit(run(sys.argv[2], command + sys.argv[4:]))
    if phase == "tests":
        target = OUT / "tests"
        target.mkdir()
        totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
        for path in sorted((ROOT / "build/test-results/test").glob("TEST-*.xml")):
            root = ET.parse(path).getroot()
            for key in totals:
                totals[key] += int(root.attrib.get(key, 0))
            with (target / path.name).open("xb") as stream:
                stream.write(path.read_bytes())
        assert totals["tests"] and not any(totals[key] for key in ("failures", "errors", "skipped")), totals
        write(target / "counts.json", totals)
        print(json.dumps(totals))
        return
    if phase == "freeze":
        selection = read(OUT / "selection.json")
        protocol_copy = OUT / "protocol-before-results.md"
        if not protocol_copy.exists():
            with protocol_copy.open("xb") as stream:
                stream.write(PROTOCOL.read_bytes())
        assert protocol_copy.read_bytes() == PROTOCOL.read_bytes()
        pinned = files("src/main/java/**/*.java", "src/main/resources/**/*",
                       "build/classes/java/main/**/*.class", "build/resources/main/**/*",
                       "src/test/java/btk/tools/NeuralProposalStudyTest.java",
                       "tools/diagnostics/neural_proposal_audit.py", "build/neural_proposal_prepare.py",
                       "build/spatial-v10-java.json", "build.gradle.kts",
                       "build/analysis/neural-proposal-audit-v1/**/*") + [PROTOCOL]
        pinned += [ROOT / game["file"] for game in selection["games"]]
        pinned += [ROOT / selection["sourcePlanPath"]]
        write(OUT / "freeze.json", {"recordedAt": stamp(), "baseRevision": "8f533d8",
              "files": {relative(path): sha(path) for path in sorted(set(pinned))}})
        print("Frozen", len(set(pinned)), "files")
        return
    if phase == "verify":
        frozen = read(OUT / "freeze.json")
        changed = [name for name, digest in frozen["files"].items()
                   if not (ROOT / name).is_file() or sha(ROOT / name) != digest]
        assert not changed, changed
        write(OUT / "verification.json", {"recordedAt": stamp(),
              "unchangedFiles": len(frozen["files"]), "changedFiles": []})
        print("Verified", len(frozen["files"]), "unchanged files")
        return
    if phase == "archive":
        selection = read(OUT / "selection.json")
        assert read(OUT / "verification.json")["changedFiles"] == []
        included = files("build/analysis/neural-proposal-audit-v1/**/*",
                         "src/main/java/btk/core/*.java", "src/main/java/btk/bot/current/*.java",
                         "src/main/java/btk/bot/v09/*.java", "src/main/java/btk/bot/*.java",
                         "src/main/resources/btk/bot/versions/v09.properties",
                         "src/main/java/btk/tools/NeuralProposalStudy.java",
                         "src/test/java/btk/tools/NeuralProposalStudyTest.java",
                         "tools/diagnostics/neural_proposal_audit.py", "build/neural_proposal_prepare.py")
        included += [ROOT / game["file"] for game in selection["games"]]
        included += [ROOT / ("src/main/java/btk/tools/" + name + ".java") for name in
                     ("CgReplay", "CgValidate", "ReplayPaths", "ReplyStudy", "ReplyProbe", "CgDisruptStudy", "RolloutFillStudy")]
        included += [ROOT / name for name in ("build.gradle.kts", "build/spatial-v10-java.json",
                     "docs/neural-proposal-audit-v1.md", "docs/evidence/neural-proposal-audit-v1.json",
                     "README.md", "docs/PLAN.md", "docs/BOTS.md", "docs/DEVLOG.md")]
        # The private original plan/request files are hashed by preparation,
        # never bundled. Canonical records contain the replay, not credentials.
        included = sorted(set(included))
        assert all(path.is_file() for path in included)
        target = ROOT / "docs/evidence/neural-proposal-audit-v1-artifacts.zip"
        manifest = {relative(path): {"sha256": sha(path), "bytes": path.stat().st_size} for path in included}
        with zipfile.ZipFile(target, "x", zipfile.ZIP_DEFLATED) as archive:
            for path in included:
                archive.write(path, relative(path))
        with zipfile.ZipFile(target) as archive:
            assert archive.testzip() is None
            for name, item in manifest.items():
                assert hashlib.sha256(archive.read(name)).hexdigest() == item["sha256"]
        write(ROOT / "docs/evidence/neural-proposal-audit-v1-artifacts.json",
              {"recordedAt": stamp(), "archiveSha256": sha(target),
               "archiveBytes": target.stat().st_size, "files": manifest})
        print("Archive verified", len(included), "files", target.stat().st_size, "bytes")
        return
    raise SystemExit("Unknown phase")


if __name__ == "__main__":
    main()
