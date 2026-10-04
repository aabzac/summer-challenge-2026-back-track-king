# Indrill build64-12k-v1: fixed total 12,000-update diagnostic

Predeclared October 4, 2026, after the user accepted the proposed 12,000-update
experiment. The completed build64-long-v1 remains closed at 8,000 updates,
59/64 exact construction sets and mean build NLL 0.5822936894583486, with its
original BUILD_FIT_NOT_ESTABLISHED verdict. This is a separate development
diagnostic, not independent confirmation or a strength evaluation.

## Fixed recipe and starting procedure

Test whether the unchanged build-only model can fit at least 63 of the same
64 training observations from 16 maps after **12,000 total updates**. Preserve
features, row order, labels, subset likelihood/decoder, 64 -> 24 -> 24 model,
model seed 2026100401, sampling seed 2026100411, batch 16, Adam learning rate
0.0003, betas 0.9/0.999, epsilon 1e-8 and global norm clipping at 10.
WI/BI remain at their original initialization, WV/BV zero, and all four heads'
Adam moments zero. Build head and shared trunk continue the identical arithmetic.

Create a new run from the audited long-v1 **final step 8,000 full checkpoint**:
all parameters, first/second Adam moments, optimizer update counter, sampling
RNG, permutation and position. This is exact state continuation, not a fresh
initialization, a weights-only warm start or a mutation of the closed parent.
Do not reset the optimizer bias-correction clock or replay the last batch.
The original first 8,000 updates are inherited evidence, not newly executed.

Before new real-data gradients, validate the parent's completed public audit,
checkpoint-chain manifest, all audited input pins, source/runtime/data freeze,
final checkpoint/receipt, final metrics and archive references. The new initial
checkpoint at total step 8,000 must match every parent array byte-for-byte,
including sampler order, plus update counter, RNG and sample position. Only the
new artifact bindings differ. Save a write-once starting-state proof before
update 8,001. The old 8,001-state chain remains immutable and referenced.

Execute exactly **4,000 new updates**, total steps 8,001 through 12,000:
64,000 new sample visits / 1,000 per observation. The whole trajectory then has
192,000 visits / 3,000 per observation. Keep the original shuffled-epoch sampler.
No alternate seed, schedule, parameter change, early pass, chosen checkpoint,
automatic extension, tuning or hidden retry. Technical failure stops the run
and is not evidence of a fitting limit.

## Implementation and checks

New implementation: tools/indrill/build64_12k_v1. New output:
build/analysis/indrill-build64-12k-v1. Reference the exact original selected
64-row file directly; no corpus export, raw copy or additional selection.
All maps remain in the original 293-map exclusion registry with unchanged
engine validation provenance. No Java/core or production weight change.

Reuse frozen pure helpers without modifying their modules or globals. New
objective/backprop function bodies must match the previous implementation;
only the explicit update invariant extends to 12,000. Never spoof a production
counter to bypass an old guard. Before preparation, verify synthetic arithmetic,
full-state restoration across 8,000, numerical checkpoint ordering across
9,999/10,000, update limits, sampler continuation/exposure, partial-state and
metric protection. Artificial high-step fixtures are not real training.
Reuse source-pinned derivative proofs where mathematics is identical.

Freeze new sources, this protocol, synthetic proof, runtime, unchanged recipe,
selected rows and parent evidence before the first new gradient. Each new update
has a write-once prior-state intent, NPZ checkpoint and linked receipt. New
checkpoints cover total steps 8,000..12,000. Resume only an incomplete run at a
fully verified committed boundary with identical bindings. Pending updates,
partial metric records or recorded technical failure prohibit automatic replay.
Checkpoint ordering must use numeric steps, including the five-digit boundary.

## Metrics, final decision and retained evidence

The baseline at total step **8,000** reuses the verified saved parent metric
payload, with explicit parent provenance and the new initial checkpoint binding;
do not evaluate that model again. New learned evaluations occur only at total
steps **10,000 and 12,000**, in the original row order and batches of 32.
Save unordered target/predicted sets, exact counts, build NLL, overlap/paint and
the existing breakdowns. No intermediate score changes the budget or decision.

Decide once at **12,000**: final construction exact count >=63/64 gives
BUILD_FIT_ESTABLISHED; otherwise BUILD_FIT_NOT_ESTABLISHED. Compare descriptively
against the retained **59/64** and NLL **0.5822936894583486**, including gained,
lost, retained and still-inexact rows. This threshold screens memorization only;
it does not establish transfer, optimal actions, full-action imitation or strength.
Repeated outcome-guided budget experiments use this same development panel.

Read-only finalization and an independently reviewed saved-state audit verify
all 4,001 new boundaries, exact parent fork, sampler/exposure, finite tensors,
frozen heads/moments, Adam parameter arithmetic from stored moments, saved metric
aggregation and final decision. Do not recompute learned predictions/gradients
during these checks. Preserve an archive containing all new metadata, checkpoint
hashes, the three scheduled NPZ snapshots, exact selected rows and pinned prior
archive references. Other new NPZ states remain locally retained and pinned.

End after this fixed run, audit, archive, documentation and local commit. Even a
pass does not automatically start Stage B, held-out model scoring, RL, more
training, new games, submission, arena upload or promotion. Preserve all earlier
protocols, artifacts and verdicts. Record the actual outcome in BOTS/DEVLOG/PLAN.
