# Intermediate-panel Indrill learning diagnostic

See the [predeclared protocol](../../../docs/indrill-fit512-v1.md).
Fixed 512 positions from the first 32 originally ranked B maps, 16 positions per
map; fresh B initializer and unchanged model/objective. Exactly 48,000 batch-32
updates give 3,000 visits per position. Final joint agreement >=359/512 meets the
new 70% diagnostic target. This is not a game-strength gate.

From the repository root, using the frozen Python/NumPy environment:

```powershell
python tools/indrill/fit512_v1/synthetic_checks.py --out build/analysis/indrill-fit512-v1/setup/checks-final
python tools/indrill/fit512_v1/run_experiment.py prepare --checks build/analysis/indrill-fit512-v1/setup/checks-final/result.json
python tools/indrill/fit512_v1/run_experiment.py run --freeze build/analysis/indrill-fit512-v1/freeze.json
python tools/indrill/fit512_v1/audit_result.py audit
python tools/indrill/fit512_v1/audit_result.py archive
```

Stages write once. Never remove evidence to repeat a completed or ambiguous stage.
Resume only through the runner's verified complete checkpoint boundary. Training
measurements are fixed at 0/3k/12k/24k/48k; the candidate's final research-holdout
evaluation runs exactly once and is descriptive. No comparator model is rescored.

The audit uses saved states, moments, targets and predictions only. The tar.gz
archive retains source/protocol/proof/selection, metadata, saved metric reports,
all checkpoint hashes and five full scheduled NPZ snapshots. Other full states,
raw corpus, replay and runtime binaries remain locally retained and pinned.
Historical experiments, Java/core, production weights and CodinGame stay unchanged.
