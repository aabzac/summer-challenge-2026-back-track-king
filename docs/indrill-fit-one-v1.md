# Indrill fit-one-v1: one-position construction diagnostic

Predeclared October 4, 2026, after `indrill-capacity-v1` failed its fixed
memorization screen (6/64 complete actions). This is a new diagnostic, not a
continuation, new bot, strength gate or permission to run CG games. Preserve all
previous sources, trained weights, evidence and selected v09.

## Question and fixed input

Can the unchanged compact Q network learn the construction set on one fixed
position when the ink objective and competition between training positions are
removed? A separate free-cell-logit control checks fitting with the same subset
likelihood and decoder, without shared network parameters.

Select the **first observation in the retained Stage-A panel's export order**,
before any new model scores: `905008481:0:0`, map
`cffa8f3e2bf34d3b9e279ad5ef8ff9185a9f909a1288c2e7e351578d9295d952`.
Derive this choice from the old preflight selection, then stream the exact row
from the frozen teacher JSONL. Do not select by previous error, ease, outcome or
teacher action. Reuse the reviewed core-derived pre-turn features, costs, masks
and accepted unordered construction label. Verify the row's full input hash and
the existing loader/decoder contract. No new feature export, altered legality,
opponent actions, successor state, held-out scores or new games enter this run.

Before training, verify the old training freeze and completed audit, select and
retain the row, pass the new synthetic implementation checks, then freeze the
new protocol, exact sources, runtime/version, selected row and old input/artifact
bindings. Use new `tools/indrill/fit_one_v1` and
`build/analysis/indrill-fit-one-v1` paths. Never modify the old diagnostic tools.

## Two fixed arms

Both arms execute **exactly 2,000 updates**, batch one (the same position on every
update), Adam learning rate 0.0003, betas 0.9/0.999, epsilon 1e-8, global gradient
norm clipping at 10. No schedule, regularization, augmentation, alternate seed,
extra update or early success stop. Start both independently; the network run is
not conditional on the control passing. Run serially and retain both outcomes.

1. **Free logits:** one float32 scalar per board cell, initialized to zero. Use
   the frozen subset loss and its float64 partition/marginals, casting the output
   gradient to float32. Only legal cells receive gradients; illegal logits and
   their optimizer moments remain zero. This is a diagnostic positive control,
   not a deployable policy. Its random seed is inapplicable.
2. **Network:** fresh `train_q_v2.Model` with seed 2026100401, the unchanged
   64->24->24 architecture and raw float32 features. Use the same initializer as
   capacity-v1; set WV/BV to zero. Train the construction head and shared trunk
   only. WI/BI retain their initial values; WI/BI/WV/BV gradients and Adam moments
   remain zero. No restored A checkpoint or learned Q-v2 weight is used.

The loss is only `L_build = log Z - sum(q_i for i in teacher_set)`, over all
unordered legal subsets with total exported cost at most three, including empty
once. Reuse `capacity_v1.subset_objective.subset_loss` and the original
`train_q.choose_build` decoder. No separate per-cell binary objective, cardinality
penalty, ink loss, TD target or learned state-value loss is introduced.

New network backpropagation propagates only the construction output derivatives
through the unchanged trunk. Verify sampled finite-difference derivatives,
masked padding, zero frozen-head derivatives/moments, free-logit gradients and
Adam update arithmetic on synthetic inputs before any real-position gradient.

## Recorded results and integrity

Record descriptive metrics at updates 0/500/1000/1500/2000: exact unordered set,
cell precision/recall, accepted-cost sum of the predicted/teacher set, subset NLL,
and decoded/teacher set logit scores. Both-empty precision/recall is one;
exactly one empty gives both zero. These metrics cannot select checkpoints.

The only arm outcome is whether the **final decoded set equals the teacher set**.
Report NLL separately: correct argmax does not mean concentrated teacher
probability, convergence, strength or good behavior on another position.

Store a separate checkpoint and receipt for every completed update, with parameters,
Adam state and update number; fixed batch identity makes sampling RNG unnecessary.
Keep each update intent linked to its prior checkpoint. Resume only from a fully
verified completed boundary with the original freeze and runtime. An ambiguous
uncommitted update, nonfinite state or changed input stops that run; do not hide it
with an automatic retry or changed settings. Audit all checkpoint links, finiteness,
frozen heads, saved metric counts and the final decision. A technical failure remains
a technical failure, not a model-capacity conclusion.

## Interpretation and scope

- Both fit: this one position is trainable under the simplified objective. The
  previous 64-position failure may involve joint optimization, shared parameters,
  ink/build interaction or budget; this test does not distinguish those causes.
- Free logits fit, network does not: investigate the fixed network/trunk and
  optimization recipe. This does not prove a universal architecture ceiling.
- Network fits, free logits do not: the proposed control's budget/implementation
  needs review; network success prevents inferring that the label/loss is unusable.
- Neither fits: inspect this fixed setup and optimization before more RL.
  Failure of a finite-budget control alone does not prove a loss/decoder bug.

The single position is development data, deliberately not a representative sample.
Neither arm measures generalization, complete-action imitation, arena strength or
the feasibility of Indrill's full method. Do not run B/holdout, extend old A, retrain
a deployable bot or promote a strategy from these results. Preserve the existing
293-map exclusion registry and explicitly bind this used map to it. Record the
completed diagnostic (including failures) in BOTS/DEVLOG/PLAN and commit locally.
