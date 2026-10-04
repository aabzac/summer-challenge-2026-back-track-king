# Indrill build64-v1: matched construction-only fitting

Predeclared October 4, 2026, after `indrill-fit-one-v1` fitted its one fixed
position in both arms. This separate diagnostic tests the effect of removing the
ink objective from the original 64-observation Stage-A training recipe. The old
joint run remains closed, with its recorded 15/64 exact construction sets and
6/64 complete actions. No old checkpoint is continued or replaced.

## Fixed comparison

Use exactly the same 64 Stage-A observations from 16 maps, in their original
export/panel order. Stream and retain their exact JSONL lines from the frozen
`indrill-capacity-v1` dataset; verify tuples, full input hashes, accepted labels,
manifest and old saved metric row identities. The existing core-derived features,
costs, masks, topology and labels are unchanged. No new export or selection by
model error, game outcome or action type is allowed. Preserve the existing
293-map exclusion registry; all 16 used maps must already be covered.

Reuse the original validated dataset adapter, whole-subset likelihood and frozen
construction decoder. The compact Q network remains 64 -> 24 -> 24. Initialize
afresh with model seed **2026100401**, exactly matching the original Stage-A
checkpoint at update zero, including parameters and zero Adam moments. WV/BV
start at zero. WI/BI remain at their original initial values; their gradients
and moments, and those of WV/BV, stay zero throughout. Shared trunk and build-head
parameters train. Ink outputs may still change when the shared trunk changes.

Use exactly **2,000 updates**, batch **16**, hence **32,000 sample visits** and
**500 visits per observation**. Use sampling seed **2026100411** and the original
permutation/epoch loop. Verify each saved order, RNG state, sample position and
chosen batch against the retained old run. No seed search, schedule, extra
updates, early success stop or best-checkpoint selection. Adam learning rate
0.0003, betas 0.9/0.999, epsilon 1e-8 and global gradient norm clip 10 remain fixed.

For each observation, the construction loss is `log Z - sum(q_i, teacher_set)`,
over unordered legal subsets with total exported cost at most three, including
empty exactly once. Average the 16 per-observation losses/derivatives with the
original batch arithmetic. Remove ink derivatives before shared-trunk
backpropagation. Do not merely freeze the ink head while leaving its derivative
in the trunk, average separately evaluated single-row forwards, add TD/value
targets, alter input normalization or change the decoder.

Removing ink loss also changes global clipping and Adam's trajectory. These are
consequences of this ablation, not separately isolated mechanisms. Any observed
effect is specific to this fixed seed, panel, architecture and optimization budget.

## Preparation and integrity

Use separate `tools/indrill/build64_v1` and
`build/analysis/indrill-build64-v1` paths. Old sources, bot weights, prior freezes,
results and selected v09 are immutable inputs. Reuse frozen checkpoint helpers
only with explicit new paths; never invoke an old training entry point.

Before any real-data gradient, pass synthetic checks of batched derivative
scaling, padding, finite differences, frozen heads, identical initialization and
checkpoint/resume/sampling boundaries. Check existing objective proofs and source
pins. Freeze this protocol, new sources, synthetic proof, selected rows, original
completed audit/comparator, data/export freezes, old sampling proof and runtime.
The retained 293 exact core validations establish the unchanged export's engine
provenance; this diagnostic changes no core rules or Java bot code.

Retain each update's intent, linked checkpoint/receipt, parameters, Adam moments,
RNG state, sample order and position. Resume only from a completely verified
committed boundary with unchanged sources/runtime/data. A pending intent, partial
metric record, nonfinite state or changed binding stops automatic continuation;
do not replay ambiguous computation or hide a technical failure with a new seed.

## Saved metrics and decisions

At updates **0/500/1000/1500/2000**, evaluate only these 64 training observations,
using the original metric order and two batches of 32. Record per-row predicted
and target unordered cell sets, exact construction count, precision/recall, paint
and build NLL. Both-empty precision/recall is one; exactly one empty gives both
zero. Also retain descriptive breakdowns by map, teacher cardinality, cost
pattern, turn band, seat and skipped-action flag where applicable. Evaluation
does not select checkpoints or trigger early decisions. No new ink/joint metric
or holdout model score is needed.

After all 2,000 updates, compare saved per-row construction results with the old
joint run's retained final metrics, without rerunning that old model. Report
candidate exact count, old **15/64**, net difference, gained/lost/retained exact
rows and construction NLL. Greater than 15 indicates better construction fitting
for this fixed comparison; equal is no exact-count improvement; less is worse.
This descriptive comparison is not a significance test or strength gate.

Separately label **BUILD_FIT_ESTABLISHED** only at **at least 63/64** final exact
construction sets, otherwise **BUILD_FIT_NOT_ESTABLISHED**. This is a bounded
near-memorization diagnostic, not the old joint-action pass, and does not unlock
Stage B or any automatic follow-on run. Smaller gains remain worth recording
without being called complete fitting.

Audit the full saved chain, original initialization, matched sample sequence,
finiteness, frozen heads, recorded Adam arithmetic and metric aggregation. Audit
and finalization must not run new learned predictions or gradients. Retain compact
evidence with all receipts/hash references and the five scheduled checkpoints;
other checkpoints and the original raw corpus may remain locally hash-pinned.
Verify archived output references and startup/metric metadata before completion.

## Interpretation and stopping boundary

A large improvement supports removing the ink task in this recipe; it does not
prove a particular gradient-conflict mechanism or better arena play. A small gain,
tie or regression does not establish a universal capacity limit or disprove
Indrill's method. Failure at a finite budget can still involve optimization,
representation or sample competition; decreasing loss is not convergence.

This run ends after its fixed training, saved-evidence audit, documentation and
local commit. No B/holdout evaluation, RL, new game, generated submission, arena
upload, strategy promotion or extension of this run is included. Record the
result, including failures, in BOTS/DEVLOG/PLAN and preserve all prior verdicts.
