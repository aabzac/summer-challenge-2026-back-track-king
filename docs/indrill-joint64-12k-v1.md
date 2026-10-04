# Indrill joint64-12k-v1: matched full-action fitting diagnostic

Predeclared October 4, 2026, after the user accepted the proposed joint diagnostic.
The original capacity-v1 Stage A remains closed at 2,000 updates with joint 6/64,
build 15/64 and ink 26/64, FIT_NOT_ESTABLISHED. The separate construction-only
build64-12k-v1 remains closed at 64/64 and mean build NLL 0.05434279870100711.
This is an outcome-informed development experiment on the same training panel.

## Fixed recipe and starting state

Test whether the unchanged joint model fits at least 63 of the original 64 full
teacher actions after **12,000 total updates**. Keep the same 16 maps, row order,
features, build and ink labels, legal masks, subset likelihood and decoder,
64 -> 24 -> 24 architecture, model seed 2026100401, sampling seed 2026100411,
batch 16, Adam learning rate 0.0003, betas 0.9/0.999, epsilon 1e-8 and norm clip 10.
Reuse the original equally weighted sum of build and ink NLL and its gradients
from capacity_v1/subset_objective.py. WI/BI train together with the shared trunk
and build head. Only WV/BV and their Adam moments remain zero.

Fork the audited **original joint Stage-A step-2,000 full checkpoint**, including
every parameter, both Adam moments, optimizer clock, sampling RNG, permutation
and position. Do not initialize from the construction-only model or reset Adam.
The first 2,000 updates are inherited evidence, not newly executed. Preserve the
parent files and its verdict; this separate run does not reopen its Stage B.

Before new gradients, verify the original parent audit, checkpoint-chain manifest,
all audited pins, source/runtime/data freeze, final checkpoint and receipt, saved
final metric and archive. Verify the exact 64-row file already retained by
build64-v1 against the original A selection and input/label bindings; use that
file directly, with no export, raw corpus copy or additional selection. Bind the
completed build-only 12k comparison evidence and saved final rows as well.
Save a write-once proof that the new step-2,000 checkpoint matches all parent
arrays byte-for-byte and its optimizer/sampler metadata, except new run bindings.

Execute exactly **10,000 new updates**, steps 2,001..12,000: 160,000 new sample
visits / 2,500 per row. Total exposure is 192,000 visits / 3,000 per row, matching
build64-12k-v1. Retain shuffled epochs and the original arithmetic. No alternate
seed, learning schedule, early success stop, best-checkpoint selection, tuning,
automatic extension or hidden retry. A technical failure stops this run and does
not establish a fitting limit.

## Implementation and preflight

New source: tools/indrill/joint64_12k_v1. New output:
build/analysis/indrill-joint64-12k-v1. Frozen prior modules and globals stay unchanged.
Reuse pure original math and evaluation helpers; the new wrapper adds explicit
state/shape/update invariants through 12,000 without spoofing counters. Before
preparation, check joint-gradient identity, trainable ink/frozen value heads,
full-state restoration without Adam reset, sampler/exposure continuation,
numeric checkpoint ordering across 9,999/10,000, terminal bounds, partial-state
rejection and metric protection on synthetic data. Reuse pinned prior derivative
proofs for unchanged mathematics. Synthetic high-step fixtures are not training.

Freeze this protocol, all active source, proof, runtime, recipe, selected rows,
parent and comparison evidence before the first new gradient. Each new update
has a prior-state intent, full NPZ state and linked receipt. New checkpoint
filenames use five-digit numeric steps and cover 2,000..12,000. Resume only a
fully verified committed boundary with identical bindings. A pending update,
partial metric or recorded failure prohibits automatic replay.

The original 293-map exclusion registry and exact-engine provenance remain
unchanged. This work changes no Java, core rules, submission or production weights.

## Fixed measurements and decision

At **2,000**, inherit the original verified metric payload without rescoring,
with the new checkpoint binding and explicit parent provenance. New learned
evaluations occur only at **4,000, 6,000, 8,000, 10,000 and 12,000**, in original
row order and original evaluation batches of 32. Retain target/predicted build
sets and ink choices, build/ink/joint exact agreement, each head's NLL and their
sum, overlap/paint, collisions and the original breakdowns. Intermediate scores
do not alter the budget or final decision.

Decide once at **12,000**: joint exact count >=63/64 yields
JOINT_FIT_ESTABLISHED; otherwise JOINT_FIT_NOT_ESTABLISHED. Compare saved paired
rows descriptively with original joint step 2,000 (joint 6, build 15, ink 26)
and construction-only step 12,000 (build 64/64, NLL 0.05434279870100711), including
gained/lost/retained/inexact counts where applicable. Do not rescore controls.
This screens memorization on an inspected panel; neither a pass nor a failure
establishes generalization, optimal teacher actions, universal network capacity,
the cause of the earlier RL failure, or arena strength.

Read-only finalization and an independently reviewed audit verify all 10,001 new
boundaries, the exact parent fork, global sampler/exposure, finite tensors,
frozen value state, Adam parameter arithmetic from stored moments, saved metric
aggregation and final verdict. No audit prediction or gradient recomputation.
Retain a compact archive of all new metadata, checkpoint hashes, six scheduled
NPZ snapshots, exact selected rows and pinned prior archive references. Other
new full checkpoints remain locally retained and pinned.

End after the fixed run, audit, archive, documentation and local commit. A pass
does not automatically start Stage B, holdout scoring, RL, further fitting,
games, submission, arena upload or promotion. Preserve earlier verdicts.
