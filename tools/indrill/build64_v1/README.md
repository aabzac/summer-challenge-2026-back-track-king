# Matched build-only fitting on the original 64 observations

This separate diagnostic follows [the fixed protocol](../../../docs/indrill-build64-v1.md).
It changes only the training objective: the same original Stage-A rows, initializer,
batch 16, seed and 2,000-update sampling sequence are retained. No old checkpoint
is continued. Ink/value parameters and their Adam moments remain frozen; ink
derivatives do not reach the shared trunk. This is a fitting diagnostic, not a
game-strength test or a submission.

The only output root is `build/analysis/indrill-build64-v1`. Preparation streams
the old JSONL once and saves the exact 64 original A lines in export order. It
verifies the old data/export freezes and completed evidence, fit-one proofs, raw
replay bindings, map exclusions, all 2,001 old sampler boundaries, and all 36
initial parameter/moment arrays. No new data export is performed.

Run the synthetic checks first, then freeze before any real-data gradient:

```powershell
python tools/indrill/build64_v1/checks.py --out build/analysis/indrill-build64-v1/setup/synthetic-first
python tools/indrill/build64_v1/run_fit.py prepare --checks build/analysis/indrill-build64-v1/setup/checks.json
python tools/indrill/build64_v1/run_fit.py run --freeze build/analysis/indrill-build64-v1/freeze.json
python tools/indrill/build64_v1/run_fit.py finalize --freeze build/analysis/indrill-build64-v1/freeze.json
```

Use the actual passing, source-pinned proof written by `checks.py`; existing
outputs cannot be overwritten. `run --resume` is available only for the original
incomplete, nonfailed run at a completely committed boundary. Any pending update
intent, incomplete metric triplet, changed binding or nonfinite state rejects
automatic continuation. Both fresh and resumed runs complete the fixed budget;
no early success stop or best-checkpoint selection is implemented.

Each update retains a linked intent, checkpoint and receipt, including Adam
moments, RNG state, permutation and position. The runner reuses the original
capacity checkpoint/restore helpers with explicit new paths; it never calls the
old training entry point or redirects its output. Sampling is checked against the
original sequence, with exactly 500 visits per observation.

Metrics use the original two batches of 32 at updates 0, 500, 1,000, 1,500 and
2,000. They retain exact construction sets, precision/recall, paint, construction
NLL and descriptive breakdowns. Finalization reads saved evidence only and
compares against the old final 15/64 construction result. The separate fixed
near-memorization screen passes at 63/64; neither verdict permits holdout scoring,
new games, RL, a strategy promotion or an extension of this run. A separate audit
must verify saved Adam arithmetic and archive references before completion.
