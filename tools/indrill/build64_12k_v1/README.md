# Fixed total-12,000-update construction diagnostic

This separate experiment follows [the predeclared protocol](../../../docs/indrill-build64-12k-v1.md).
It continues the audited **full step-8,000 checkpoint** from `build64-long-v1`
in a new output directory. Parameters, Adam moments and update counter, sampling
RNG, permutation and position are restored exactly. The original 8,000 updates
are inherited evidence; they are not executed again. The completed parent and
its 59/64 result remain unchanged.

Execute exactly 4,000 new updates, total steps 8,001 through 12,000, with the
same 64 observations, build-only loss, model, optimizer and batch 16. This adds
64,000 sample visits / 1,000 per observation, for a total of 192,000 visits /
3,000 per observation. Decide once at update 12,000: at least 63/64 exact sets
establishes fitting for this diagnostic. No early pass or automatic extension.

```powershell
python tools/indrill/build64_12k_v1/checks.py --out build/analysis/indrill-build64-12k-v1/setup/synthetic-first
python tools/indrill/build64_12k_v1/run_fit.py prepare --checks build/analysis/indrill-build64-12k-v1/setup/checks.json
python tools/indrill/build64_12k_v1/run_fit.py run --freeze build/analysis/indrill-build64-12k-v1/freeze.json
python tools/indrill/build64_12k_v1/run_fit.py finalize --freeze build/analysis/indrill-build64-12k-v1/freeze.json
```

Use the actual passing source-pinned synthetic proof. All outputs are write-once
under `build/analysis/indrill-build64-12k-v1`. Preparation and the pre-run check
verify the parent's completed audit, every audited input pin, source/data/runtime
freeze, checkpoint/metric receipts and archive index/ZIP. The exact original
selected 64-row file is referenced directly; no corpus copy or selection occurs.

Before the first new gradient, `run/start-proof.json` binds the new initial
checkpoint to every parent parameter/Adam array, sampler state and update clock.
The initial receipt links to the parent's final receipt. Every new update retains
its intent, full NPZ and linked receipt. Checkpoints use five-digit names
`step-08000` through `step-12000` and are sorted numerically.

The baseline metric at 8,000 is the already audited parent payload with only its
checkpoint reference changed. It is never scored again. New model evaluations
occur only at 10,000 and 12,000, in the original order and batches of 32.
Finalization validates saved records without learned predictions or gradients,
and compares final construction sets/NLL with retained 59/64 and 0.5822936894583486.

`run --resume` accepts only an incomplete, nonfailed original run at a completely
verified committed boundary. It restores the actual optimizer clock and sampler;
no batch or ambiguous computation is replayed. Partial metric/update records,
changed bindings or nonfinite state stop continuation. The independent audit
checks all 4,001 new boundaries, inherited provenance and saved Adam arithmetic.

This is further development on the same panel, not independent confirmation or
a game-strength test. Neither outcome automatically starts Stage B, holdout
scoring, RL, new games, another training run, submission or strategy promotion.
