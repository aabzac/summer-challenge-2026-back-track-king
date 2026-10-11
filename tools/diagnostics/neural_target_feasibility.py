"""Write-once logs and pins for the bounded target-module feasibility diagnostic."""
import hashlib
import json
import pathlib
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
OUT = ROOT / "build/analysis/neural-target-feasibility-v1"
PROTOCOL = ROOT / "docs/neural-target-feasibility-v1.md"
SELECTION = ROOT / "build/analysis/neural-proposal-audit-v1/selection.json"
JAVA = ROOT / "build/spatial-v10-java.json"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def read(path):
    return json.loads(path.read_text(encoding="utf-8-sig"))


def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("x", encoding="utf-8", newline="\n") as stream:
        json.dump(value, stream, indent=2)
        stream.write("\n")


def run(label, command):
    OUT.mkdir(parents=True, exist_ok=True)
    log = OUT / f"{label}.log"
    with log.open("x", encoding="utf-8") as stream:
        result = subprocess.run(command, cwd=ROOT, stdout=stream, stderr=subprocess.STDOUT)
    write(OUT / f"{label}.command.json", {"command": command, "exitCode": result.returncode,
                                          "logSha256": sha(log)})
    print(f"{label}: exit={result.returncode}; {log.relative_to(ROOT)}")
    if result.returncode:
        print(log.read_text(encoding="utf-8", errors="replace")[-6000:])
        raise SystemExit(result.returncode)


def freeze():
    files = set()
    for directory in ("src/main/java", "src/main/resources", "build/classes/java/main",
                      "src/test/java/btk/tools/target", "build/test-results/test"):
        files.update(p for p in (ROOT / directory).rglob("*") if p.is_file())
    files.update([PROTOCOL, pathlib.Path(__file__).resolve(), SELECTION, JAVA,
                  ROOT / "src/test/java/btk/tools/NeuralTargetSizeProbeTest.java",
                  ROOT / "docs/evidence/neural-component-audit-v1.json",
                  ROOT / "docs/evidence/neural-proposal-audit-v1.json"])
    files.update(p for p in (OUT / "baseline").rglob("*") if p.is_file())
    for game in read(SELECTION)["games"]:
        files.add(ROOT / game["file"])
    runtime = json.loads(JAVA.read_text(encoding="utf-8-sig"))
    external = [pathlib.Path(runtime[0]), pathlib.Path(sys.executable)]
    external += [pathlib.Path(s) for s in runtime[runtime.index("-cp") + 1].split(";") if s.endswith(".jar")]
    pins = {str(p.relative_to(ROOT)).replace("\\", "/"): sha(p) for p in sorted(files)}
    write(OUT / "freeze.json", {"files": pins, "runtime": {str(p): sha(p) for p in external}})
    (OUT / "protocol-before-measurement.md").write_bytes(PROTOCOL.read_bytes())
    print(f"Frozen {len(pins)} files and {len(external)} runtimes")


def verify(allow_result=False):
    pins = read(OUT / "freeze.json")
    for name, digest in pins["files"].items():
        path = ROOT / name
        if allow_result and path == PROTOCOL:
            assert path.read_bytes().startswith((OUT / "protocol-before-measurement.md").read_bytes())
        else:
            assert sha(path) == digest, name
    for name, digest in pins["runtime"].items():
        assert sha(pathlib.Path(name)) == digest, name
    print(f"Verified {len(pins['files'])} files and {len(pins['runtime'])} runtimes")


def archive():
    verify(True)
    destination = ROOT / "docs/evidence/neural-target-feasibility-v1-artifacts.zip"
    files = {p for p in OUT.rglob("*") if p.is_file() and p.suffix != ".class"}
    files.update((PROTOCOL, ROOT / "docs/evidence/neural-target-feasibility-v1.json", pathlib.Path(__file__).resolve(),
                  ROOT / "src/main/java/btk/tools/NeuralTargetFeasibility.java",
                  ROOT / "src/main/java/btk/tools/NeuralTargetSizeProbe.java",
                  ROOT / "src/test/java/btk/tools/NeuralTargetSizeProbeTest.java", SELECTION))
    for folder in ("src/main/java/btk/tools/target", "src/test/java/btk/tools/target"):
        files.update(p for p in (ROOT / folder).rglob("*.java"))
    for game in read(SELECTION)["games"]:
        files.add(ROOT / game["file"])
    entries = []
    with zipfile.ZipFile(destination, "x", compression=zipfile.ZIP_DEFLATED) as archive_file:
        for path in sorted(files):
            name = path.relative_to(ROOT).as_posix()
            archive_file.write(path, name)
            entries.append({"path": name, "bytes": path.stat().st_size, "sha256": sha(path)})
    write(destination.with_suffix(".json"), {"archive": destination.name, "sha256": sha(destination),
          "bytes": destination.stat().st_size, "files": entries,
          "scope": "Public replays, protocol, new code, logs, baseline artifact and pins; historical private lineage remains local."})
    print(f"Archived {len(files)} files; {destination.stat().st_size} bytes")


if __name__ == "__main__":
    mode, *args = sys.argv[1:]
    if mode == "run": run(args[0], args[1:])
    elif mode == "java":
        command = read(JAVA); command[-1] = args[1]
        run(args[0], command + args[2:])
    elif mode == "freeze": freeze()
    elif mode == "verify": verify()
    elif mode == "archive": archive()
    else: raise SystemExit("run LABEL EXE ARGS | java LABEL MAIN ARGS | freeze | verify | archive")
