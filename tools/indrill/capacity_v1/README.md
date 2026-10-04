# Capacity diagnostic commands

The fixed method is in [the protocol](../../../docs/indrill-capacity-v1.md).
This tool does not promote a bot or start live games. Run from the repository root.
Each stage writes new files and refuses an existing attempt. Review failures;
do not delete evidence or rename output directories to obtain a rerun.

Installation, compilation and synthetic checks do not establish readiness of the
real exporter. Review the real preflight/export and their frozen inputs separately.
Preparation-era outputs are historical evidence; regenerate checks from these
installed source paths. Python files retain `parents[3]` as the repository root.

## Compile once and bind the runtime

After the preceding neural experiment has finished, ensure production classes are
current. The launcher compiles only the standalone tools into an isolated folder;
it changes neither Gradle nor bot/core source. Pass the existing retained Gson jar.

```powershell
.\gradlew.bat classes
python tools/indrill/capacity_v1/export_launcher.py compile --gson build/analysis/neural-v3-arena-20261004/gson-2.11.0.jar
```

This writes `build/analysis/indrill-capacity-v1/setup/export-execution-freeze.json`
before any real preflight/export. It binds source files, every generated exporter
class (including nested classes), production classes/resources, the complete JDK
file inventory and bytes, Gson, and the explicit ordered classpath. Compilation
intent and output are retained. A failed/partial compile is not automatically retried.

## Fresh synthetic checks at the installed paths

```powershell
python tools/indrill/capacity_v1/export_launcher.py verify
$capacityExecution = Get-Content -Raw build/analysis/indrill-capacity-v1/setup/export-execution-freeze.json | ConvertFrom-Json
& $capacityExecution.java -cp $capacityExecution.classpath IndrillTeacherExport synthetic build/analysis/indrill-capacity-v1/setup/synthetic-java
python tools/indrill/capacity_v1/loader_synthetic_checks.py build/analysis/indrill-capacity-v1/setup/synthetic-java build/analysis/indrill-capacity-v1/setup/synthetic-loader
python tools/indrill/capacity_v1/synthetic_checks.py --out build/analysis/indrill-capacity-v1/setup/synthetic-checks-first.json
python tools/indrill/capacity_v1/certificate_synthetic_checks.py build/analysis/indrill-capacity-v1/setup/certificate-synthetic-first.json
& $capacityExecution.java -cp $capacityExecution.classpath IndrillCertificateSynthetic build/analysis/indrill-capacity-v1/setup/certificate-java-first.json
python tools/indrill/capacity_v1/trainer_synthetic_checks.py --out-dir build/analysis/indrill-capacity-v1/setup/synthetic-trainer
python tools/indrill/capacity_v1/export_launcher.py verify
```

Stop at any nonzero exit. The trainer rehearsal uses tiny synthetic observations
and injected short schedules; its A pass/fail summaries test control flow and are
not learned-fit results. The real trainer remains fixed at A=2000 and B=4000 updates.
The certificate reports preserve the Java/NumPy rounding limitation and add no gate.

## Real data stages: execute only after setup review

These are future commands, not actions performed by installation. Inspect the
preflight selection, old40/later11 grouping, exclusions and validation before export.

```powershell
python tools/indrill/capacity_v1/export_launcher.py preflight
# Review dataset/teacher-preflight.json and setup/preflight-result.json before continuing.
python tools/indrill/capacity_v1/export_launcher.py export
# Review dataset/teacher.jsonl and dataset-manifest.json, including coverage/collisions.
python tools/indrill/capacity_v1/prepare_freeze.py
```

The launcher verifies the execution freeze immediately before and after both real
stages. The separate `training-freeze.json` binds the installed code, current
synthetic checks, completed dataset, frozen comparator and the export execution
manifest. Its workspace pins are flattened; external JDK/Gson pins stay in the
execution manifest and are rechecked by the same launcher at trainer startup/end.
`prepare_freeze.py` runs no gradients and does not approve the real selection by
itself. Keep the human/code review alongside these machine checks.

After that review, the separately invoked fixed training commands are:

```powershell
python tools/indrill/capacity_v1/train_capacity.py --stage A --freeze build/analysis/indrill-capacity-v1/training-freeze.json
# B is allowed only after the retained final A_PASS, never a descriptive interim score.
python tools/indrill/capacity_v1/train_capacity.py --stage B --freeze build/analysis/indrill-capacity-v1/training-freeze.json
```

Resume uses the exact original freeze and `--resume`; partial checkpoint/update
intents require offline recovery rather than silent replay. No command here writes
generated Java weights, modifies the frozen comparator, submits to CG or promotes a bot.
