# Indrill generalization-v1: larger-panel imitation and held-out maps

Predeclared October 4, 2026, after the user authorized the next 2,048-position
training experiment and evaluation on maps outside its training panel. This is
outcome-informed development following joint64-12k-v1, not a reopening of the
closed capacity-v1 Stage B. All previous budgets, artifacts and verdicts stand.

## Question and data

Does the unchanged compact Indrill representation learn useful teacher-action
preferences across more maps, and do those preferences transfer beyond the
training maps? The completed 63/64 joint fitting result motivates this question;
it neither establishes generalization nor supplies the new initial weights.

Reuse the original validated capacity-v1 export and its frozen selection exactly:
2,048 training observations (panel B, 128 maps) and 1,020 held-out observations
(51 maps). Do not export again, select by outcome, replace rows, augment inputs,
change labels or move maps between splits. Verify original data, selection,
input/label identities, legality, decoder coverage and map/input disjointness.
Preserve the old40 and later11 cohort tags and report them separately. These are
existing research holdouts, previously used or inspected in earlier development;
they are not pristine confirmation or independent evidence of arena strength.

The original 293-replay exact-engine provenance, execution freeze and future
strength-map exclusion registry remain in force. No Java/core changes, new games,
production weight export, submission, arena action or promotion are part of this
experiment.

## Fixed training recipe

- Fresh model seed 2026100402 and sampling seed 2026100412, originally reserved
  for the unused B recipe. No warm start from the fitted 64 positions or Q-v2.
- Same raw float32 64 -> 24 -> 24 network and original capacity-v1 supervised
  objective: exact legal unordered-build-subset NLL plus legal ink/NONE NLL,
  equally weighted per observation. All shared, build and ink weights train;
  WV/BV and their Adam moments remain zero. No extra features or loss changes.
- Batch 32, shuffled without replacement per epoch; Adam learning rate 0.0003,
  betas 0.9/0.999, epsilon 1e-8, gradient norm cap 10. No decay, augmentation,
  temperature change, dropout, learning-rate schedule or parameter sweep.
- Exactly 12,000 updates: 384,000 sample visits, 187.5 epochs, with each row
  visited 187 or 188 times. This is three times the original unexecuted B budget,
  chosen before new learned scores in light of the prior small-panel curves.
  It is a bounded transfer pilot, not a promise to memorize 2,048 observations.
- Measure the complete training panel only at steps 0, 4,000, 8,000 and 12,000,
  using the original deterministic evaluation batches of 32. These descriptive
  measurements never change the schedule or select a best checkpoint.

Initialize optimizer moments and update counter at zero. Retain full checkpoints,
optimizer/sampler RNG/order/position, per-update intent and linked receipts in the
new output directory. Use numeric five-digit checkpoint names. Resume only a
verified complete boundary with identical bindings; a pending update, partial
evaluation, contract or numerical failure stops automatic progress. Preserve the
failure and do not rerun, silently repair, extend the budget or try another seed.

## Final evaluation and interpretation

After all 12,000 updates, score the 1,020 held-out observations once with the final
candidate and once with frozen Q-v2 (retained weights-6.bin), under a write-once
evaluation intent. The comparator is inference-only and never initializes training.
No intermediate holdout scores, checkpoint selection, outcome-dependent exclusions
or additional learned scoring during the audit.

Reuse the original metrics: exact unordered build set, exact ink/NONE and joint
action; cell precision/recall; predicted/teacher paint; build, ink and joint NLL.
Keep per-row targets/predictions and breakdowns by turn band, seat, cardinality,
cost pattern, skipped actions and holdout cohort. Both-empty precision/recall is
one; exactly one empty is zero. Aggregate per map first, including NLL and the
train/holdout gap. Report absolute agreement, not just improvement over weak Q-v2.
Cross-model NLL is descriptive: Q-v2's reward-trained scores are not calibrated
imitation probabilities, so NLL against that comparator is not a transfer gate.

Reuse the prospective diagnostic transfer convention: paired differences versus
Q-v2 in held-out per-map exact build and ink agreement, 10,000 paired map bootstrap
resamples, seed 2026100421, percentile 95% intervals. TRANSFER_EVIDENCE requires
both lower bounds strictly above zero, at least 20 held-out maps, and all process
checks. Otherwise TRANSFER_NOT_ESTABLISHED. This convention is not an overall
95% guarantee, a strength gate or a claim to reproduce the champion.

There is no new binary training-fit threshold. Show the full fixed training curve
and final train/holdout gap. Low training agreement means a transfer failure cannot
isolate generalization from limited optimization or representation. Complete the
single final holdout even if training accuracy is low; stop after the fixed run.

## Implementation, verification and completion

New tools live in tools/indrill/generalization_v1; outputs live in
build/analysis/indrill-generalization-v1. Reuse validated pure data, objective,
forward/backward, decoder and metric routines; preserve all frozen prior modules
and their globals. Before gradients, independently review the wrapper and perform
small synthetic checks of initialization, optimizer/sampler restoration, update
and evaluation boundaries, guards against partial/repeated evaluation, frozen
value weights and original objective identity. Reuse pinned derivative proofs for
unchanged mathematics rather than replaying experiments.

Freeze this protocol, every active source dependency, runtime, proof, original
export/execution/data manifests, comparator, settings and the completed joint64
prerequisite before the first real gradient. Verify those bindings at startup and
completion. Audit the saved checkpoint chain, sampler exposure, finite tensors,
frozen value head, Adam parameter arithmetic and saved metric aggregation without
recomputing learned predictions or gradients. Preserve compact evidence, the
scheduled checkpoints and an archive with hashes; retain other full checkpoints
locally. Record results in README/BOTS/PLAN/DEVLOG and commit the completed
experiment. Any subsequent training, RL or strength run is a separate step.
