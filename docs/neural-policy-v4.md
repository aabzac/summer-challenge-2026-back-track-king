# neural-v4: lower construction exploration, fixed outcome-training repeat

Predeclared October 4, 2026 (Europe/Bucharest), before new collection, optimizer
updates, process or strength outcomes. Baseline revision `ea33c58`. The user agreed
to the proposed experiment and reports that the ongoing neural-v3 arena placement
currently looks worse than v09. That comparison is user-reported and has not yet
been established on matched arena opponents/maps. A bounded replay audit runs
independently; exact engine reproduction precedes interpretation.

## One hypothesis

Repeat neural-v2 outcome training from the exact original neural-v1 weights, with
**construction sampling temperature 0.5 instead of 1**. Sabotage stays at 1.
The retained diagnostic sampled 128 selectable decisions per head in each of
eight previous rounds: construction matched the same student's maximum logit
in 283/1024 cases; sabotage in 715/1024. Pure argmax approximates, but does not
equal, deployment with NOISE=0.1 and deadlines. This motivates the new candidate;
it is not a strength result or proof that temperature 0.5 is optimal.

Keep feature definitions, candidate filtering, architecture, sequential placements,
closing guards, normalization and inference behavior unchanged. Use the identical
deadline-aware neural-v3 runtime and shared frozen NeuralV3Features, replacing only
generated model weights. Original v1/v2/v3 files, weights and artifacts stay frozen.
Local `current` and its stored default submission stay v09. No automatic arena
publication or strategy promotion is part of this training experiment.

## Objective and fixed training

Clone the exact shipped v1 int16 blobs; do not continue the outcome-trained v3
checkpoint. Use the previous optimizer/RNG seed 20261005 and the same eight rounds
of 192 games (1,536 games, 768 paired maps), cycling by map pair through v09,
neural-v1 and v07. Frozen opponents receive CG 486/36-ms budgets; offline student
collection completes its action sequence, as in v2. No new opponent mix, critic,
reward shaping, entropy bonus, extra updates or checkpoint selection.

Terminal WDL reward +1/0/-1, gamma 1, opponent-cohort leave-one-game-out baseline,
uniform selectable-decision minibatches, no advantage standardization. Clipped
actor surrogate ratio 0.2, teacher KL coefficient 0.02, Adam learning rate 0.00003,
batch 64, four epochs/head/round, global gradient norm cap 1, no weight decay.

For each head, the actor uses the actual sampling distribution softmax(logits/T)
and stored behavior log-probability, including the 1/T actor-gradient factor.
Teacher regularization remains KL(frozen-v1 at T=1 || current at T=1), with its
original gradient. Quantized round starts, float32 Java/Python probability parity,
optimizer/RNG continuation and complete legal masks remain mandatory. Old T=1
datasets cannot become on-policy data for T=0.5. Metadata binds both temperatures;
malformed/mismatched data fail before updates.

Fresh training bases: 3100000, 3101000, ..., 3107000, 96 consecutive maps each.
One six-game/three-map numerical probe: 3109000..3109002, excluded from training
and confirmation. Register all prior corpus/training/benchmark/boss/arena maps
through shared-core Protocol hashes; reject overlaps and preserve failed stages.
Completed files are immutable; resume only completed stages with identical pins.
No implicit retries, outcome-selected checkpoints, restarts or additional rounds.

## Verification and fixed evaluation

- New separate collector/trainer, immutable run manifest and generated
  NeuralV4Weights. Retain every original V2 tool unchanged.
- Analytic/finite-difference tests for head-specific actor temperatures and the
  unchanged T=1 teacher KL, masking/clipping, recorded behavior probabilities,
  quantized rebase, resume and numerical parity. Full Java tests and cgValidate.
- Isolated `submit -PsubmitVersion=neural-v4 -PsubmitOut=build/submit-neural-v4`.
  Verify final source, config, classes, weights and the 100,000-character limit;
  confirm retained v3 export identity is unchanged in a private output directory.
- Exactly eight new strict zero-grace process games against v09, both seats on
  maps 903510176, 904146009, 904148181 and 904147739. No heavy concurrent workload;
  complete the panel and retain all failures, no retries.
- One practical-v4 comparison against **neural-v3**, the explicitly selected
  control for this follow-up: seeds 3200000 /3300000 /3400000, 300/+200/+500 games,
  two workers and CG budgets. Exclude all previous maps and all new training/probe
  and inspected arena maps. Futility only below pooled50% at300/500; final >=55%
  over1000. No early pass. Failure ends evaluation; no tuning/retry.
- A local/process survivor still requires exactly40 new live boss games matched
  to a valid individually verified control cache. The candidate arm of the just
  completed neural-v3 boss plan provides40 control outcomes (15/40). This one
  temperature hypothesis comes from old training-state diagnostics, not fitting
  that boss map pool; reuse may serve as a practical regression check if the same
  boss/league/artifact provenance remains valid. If replay-guided strategy changes
  or boss changes invalidate it, stop and register a fresh control decision first.
  Live source transmission must remain within the user's authorized scope.

The intended cached control is precisely the **candidate** arm of
`build/cg-boss/neural-v3-boss-40/plan.json`, plan ID
`d0e1d42e499718b68d90fe42e426a4cfc8e96a47b05da4464128327bdf280415`:
neural-v3 build `9158eae9029544f3b1984d543c36c1e5d3bd73bfbcbb5ba3a4f633a77da13267`,
Boss4 executable6767608, Silver,15W25L,20 games per seat. Its completed immutable
audit is `docs/evidence/neural-v3-boss.json`, SHA-256
`41e6be8a7419524234930929a168ae7a460f7c5df924732c41cbdf4b407e5233`.
This identity clarification is recorded during training, before any V4 process,
strength or live boss result; it changes neither the stated control nor its pool.

The user's early arena observation is a generalization warning, not overridden
by passing a local gate. Record the arena audit and candidate result separately.
Even a local/boss pass does not establish an arena-ranking improvement. Commit the
completed experiment and actual verdict, including failure or an incomplete live stage.
