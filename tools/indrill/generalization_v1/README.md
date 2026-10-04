# Larger-panel Indrill imitation diagnostic

See [the fixed protocol](../../../docs/indrill-generalization-v1.md).
This fresh experiment preserves the original closed capacity-v1 Stage B.
It uses the existing B2048/128-map training panel and the existing 1020/51-map
research holdout, unchanged joint objective/network, fresh B seeds, batch32 and
12,000 updates. There is no production weight export or game/arena operation.

Run from the repository root using the frozen Python/NumPy runtime. Commands
write once; do not repeat completed stages or remove evidence to obtain a rerun.

```powershell
python tools/indrill/generalization_v1/synthetic_checks.py --out build/analysis/indrill-generalization-v1/setup/checks-final
python tools/indrill/generalization_v1/run_experiment.py prepare --checks build/analysis/indrill-generalization-v1/setup/checks-final/result.json
python tools/indrill/generalization_v1/run_experiment.py run --freeze build/analysis/indrill-generalization-v1/freeze.json
python tools/indrill/generalization_v1/audit_result.py audit
python tools/indrill/generalization_v1/audit_result.py archive
```

All five commands have completed successfully; they are reproduction references,
not instructions to run again in the same directory. An initial synthetic check
failed only an expected-error-message assertion; its synthetic fixtures remain.
After that correction, a final explicit registry-pin addition overlapped the first
real preparation. Its stale proof was detected before any run, score or gradient.
The unused preparation and repair record remain in setup/rejected-preparation-1
and setup/preparation-repair.json. The accepted preparation uses checks-final.

`run --resume` accepts only a complete, verified checkpoint boundary and unchanged
source/runtime/data. Each update retains full parameters, Adam moments/clock,
sampler RNG/permutation/position, an intent and linked receipt. Any pending update,
partial evaluation or recorded failure blocks automatic replay. Training metrics
occur only at0/4000/8000/12000. Candidate and frozen Q-v2 holdout evaluations occur
once each after12,000; committed evaluations are read, never repeated.

The independent audit uses saved states, predictions and moments only. Its
bootstrap reproduction is arithmetic on saved map means, not another model
evaluation. The archive includes metadata, all checkpoint hashes and four
scheduled NPZ snapshots; other full states stay locally retained. The raw corpus,
replays and runtime binaries are not copied into the compact archive.

Completed result: `TRANSFER_EVIDENCE` against the weak frozen Q-v2 control.
Train build/ink/joint counts are 231/892/105 of 2,048; held-out counts are
111/424/42 of 1,020. Saved-state audit and archive verification pass. Absolute
imitation quality remains low, including multi-cell build agreement already on
training data. See [BOTS](../../../docs/BOTS.md) and the
[public evidence](../../../docs/evidence/indrill-generalization-v1.json).
No additional training, learned evaluation or games have started.
