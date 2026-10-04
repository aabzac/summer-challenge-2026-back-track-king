# Fixed longer-budget build fitting

This separate run follows [the predeclared protocol](../../../docs/indrill-build64-long-v1.md).
It starts a fresh network on the same 64 observations as completed `build64-v1`,
with the same initialization, features, build-only objective, Adam, batch 16 and
sampling order. Only the fixed training budget changes: 8,000 updates, 128,000
sample visits, exactly 2,000 visits per observation. Final construction agreement
must reach 63/64 to establish fitting under this diagnostic. No early pass,
checkpoint selection, holdout scoring or game-strength claim is included.

The output root is `build/analysis/indrill-build64-long-v1`. Preparation verifies
the previous source/data freeze and completed audit, and references its exact
selected 64-row file. It does not export or copy the raw teacher corpus. Old
sources, outputs and production weights remain immutable inputs.

```powershell
python tools/indrill/build64_long_v1/checks.py --out build/analysis/indrill-build64-long-v1/setup/synthetic-first
python tools/indrill/build64_long_v1/run_fit.py prepare --checks build/analysis/indrill-build64-long-v1/setup/checks.json
python tools/indrill/build64_long_v1/run_fit.py run --freeze build/analysis/indrill-build64-long-v1/freeze.json
python tools/indrill/build64_long_v1/run_fit.py finalize --freeze build/analysis/indrill-build64-long-v1/freeze.json
```

Use the actual passing source-pinned synthetic proof; existing outputs are never
overwritten. The source/runtime/data freeze precedes every real-data gradient.
`run --resume` accepts only an incomplete, nonfailed run with an entirely verified
committed boundary. Pending updates, partial metric records, changed bindings or
nonfinite state prohibit automatic continuation.

Every new checkpoint from 0 through 2,000 is compared against completed
`build64-v1`: all parameter and Adam arrays, RNG state, sample permutation and
position, update number, chosen batch, loss and unclipped gradient norm. The
first five saved metric payloads must also match, excluding their checkpoint
references. Any mismatch is a technical failure. A write-once
`run/prefix-proof.json` binds the complete comparison before update 2,001 can
compute a gradient. This proves that the extra budget follows the original
trajectory rather than a changed recipe.

The frozen sampler, metric evaluation and validation helpers are reused with
explicit new paths. Metrics use two batches of 32 at updates 0, 500, 1,000, 1,500,
2,000, 4,000, 6,000 and 8,000. Finalization reads retained evidence only, verifies
the full chain and matched prefix, and compares final construction sets and NLL
with the previous 16/64 result. A separate audit checks saved Adam arithmetic and
archive references. This run creates no automatic follow-on experiment, bot
promotion, submission or arena upload.
