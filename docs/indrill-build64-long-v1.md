# Indrill build64-long-v1: fixed 8,000-update budget test

Predeclared October 4, 2026, after the user accepted the proposed larger-budget
diagnostic. The completed build64-v1 run fitted 16/64 construction sets after
2,000 updates, with final build NLL 5.1629933196. This new experiment changes
only the number of optimization updates. It starts fresh and preserves the old
run, source, checkpoints and verdict.

## Fixed question and recipe

Can the same network and build-only objective fit the same 64 training positions
with **8,000 updates** instead of 2,000? Use the exact selected JSONL rows pinned
by the completed `indrill-build64-v1` freeze, in the same order, from the same
16 maps. No new export, selection, feature, label, input normalization, action
space or decoder change. All used maps remain in the original 293-map exclusion
registry, and the unchanged data retains its earlier exact core validation.

Start a fresh 64 -> 24 -> 24 model with seed **2026100401**. WI/BI stay at their
initial values; WV/BV stay zero; all four heads keep zero gradients and Adam
moments. The build head and shared trunk train with exactly the previous
batched whole-subset likelihood and backward arithmetic. Only the implementation
guard on the update counter grows to 8,000; do not alter old source or temporarily
falsify a model counter to bypass its 2,000-update guard.

Use sampling seed **2026100411**, the original shuffled-epoch sampler, batch
**16**, Adam learning rate **0.0003**, betas **0.9/0.999**, epsilon **1e-8**,
and global norm clipping at **10**. Complete exactly **8,000 updates**,
**128,000 sample visits**, **2,000 visits per observation**. No schedule, alternate
seed, regularization, early pass, best-checkpoint choice, extra gradient update
or tuning based on intermediate metrics. A failed technical contract stops the
run; it is not a fitting/capacity conclusion and must not trigger a hidden retry.

The number of visits per position now matches the successful single-position
test, but shared parameters and competition among positions still differ. This
does not isolate all causes of its earlier success.

## Exact first 2,000 updates

Recompute the full run from the original initializer. Never load old learned
parameters as its starting point. Before proceeding past update 2,000, require
every new boundary at **0..2000** to match the retained build64-v1 checkpoint:
all parameter and Adam-moment tensors byte-for-byte, update number, RNG state,
sample permutation and position. Compare sampled indices, pre-update loss and
gradient norm in every update receipt. Check the saved construction metric
payloads at 0/500/1000/1500/2000 against the original payloads, ignoring only
artifact-path/binding fields that must refer to the new run.

The new run has independent intents, checkpoints and receipts in a new output
directory. Record a write-once prefix-comparison proof before the first gradient
after 2,000. A divergence stops before more training; do not silently relax exact
matching, choose a different seed or continue with a different initial trajectory.

## Implementation and preparation

New code lives in `tools/indrill/build64_long_v1`, outputs in
`build/analysis/indrill-build64-long-v1`. Reuse reviewed pure metric/sampling and
checkpoint helpers with explicit new paths. Do not monkeypatch old modules,
redirect their output globals or invoke their old training entry points.

Before real-data gradients, verify the completed build64-v1 freeze and audit,
its original sources/runtime/selected rows and comparator references. Reuse the
prior derivative proofs where the mathematical source is unchanged. New
synthetic checks must establish equal objective/gradients/optimizer arithmetic,
the expanded guard at 2,000 and 8,000, saved-state continuation, matched sampling,
prefix-comparison rejection and incomplete-record protection. Synthetic boundary
fixtures may use artificial states; they must not be presented as real fitting.
Freeze this protocol, exact new sources, runtime, proof, prior evidence and
selected rows before launching the sole real run.

Save every update's parameters, Adam state, sampling RNG/order/position, prior
checkpoint intent and linked receipt. Resume only from a fully verified committed
boundary with unchanged source/runtime/data. A pending intent, partial checkpoint,
incomplete metric record or recorded technical failure blocks automatic replay.

## Metrics and final decisions

Record construction metrics only at updates
**0/500/1000/1500/2000/4000/6000/8000**, using the original row order and two
evaluation batches of 32. Save per-row unordered target/predicted sets, exact
count, precision/recall, paint and build NLL, plus the same descriptive map and
target-size/turn/seat breakdowns. Reuse the previous conventions, including
both-empty precision/recall equal to one. No intermediate result selects a
checkpoint or changes the budget.

Decide once after update **8,000**: **BUILD_FIT_ESTABLISHED** requires at least
**63/64** exact construction sets; otherwise **BUILD_FIT_NOT_ESTABLISHED**.
Compare the final saved rows descriptively against build64-v1's **16/64** and
NLL **5.1629933196**, reporting gained/lost/retained exact rows and net count.
Greater than 16 indicates better fitting for this fixed budget comparison, not
a significance or strength result. Preserve the old 2,000-update verdict.

Audit all **8,001** saved boundaries, fresh initializer, frozen heads, finite
arrays, exact sampling/exposure, first-2,000 comparison, Adam parameter arithmetic
from stored moments and saved metric aggregation. Audit/finalization may read
saved rows but must not create new learned predictions or gradients. Preserve a
compact archive with all startup/update/metric records and checkpoint hashes,
the eight scheduled NPZ checkpoints and explicit prior evidence references;
other NPZ files and the old raw corpus may remain locally retained and pinned.

## Interpretation and stopping boundary

An improvement supports budget sensitivity for this one recipe. Near-complete
fitting demonstrates memorization of this development panel, not transfer,
optimal actions, full-action imitation or arena strength. Failure at 8,000 does
not establish convergence or a universal architecture limit. A still-decreasing
loss alone is neither convergence nor authorization for unbounded training.

End this experiment after the fixed run, evidence audit, archive, documentation
and local commit. No Stage B, held-out model scoring, RL, new games, generated
submission, arena upload, strategy promotion or automatic budget extension.
Record success, failure or technical failure in BOTS/DEVLOG/PLAN without
rewriting historical protocols or results.
