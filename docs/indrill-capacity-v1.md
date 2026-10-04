# Indrill capacity-v1 — supervised representation diagnostic

This protocol fixes the method, data boundaries and optimizer budget.
Installation alone establishes no export, fitting or transfer result. Review
the input/runtime freezes and setup checks before execution. Commands are in
[the tool runbook](../tools/indrill/capacity_v1/README.md).

## Question and fixed representation

Can the current compact Indrill Q action representation learn a small set of
Indrill teacher decisions by direct supervision, and then transfer to other maps?
Keep Java `IndrillFeatures` (64 raw float32 features, INDRILL_INK_PENALTY=2), the
64->24->24 ReLU network, one neighbour-mean/global-mean block, region-mean ink head,
and additive budget-three action decoder. No reward, TD bootstrap, target network,
prioritized replay, new features, extra layers, sequential placement conditioning,
data augmentation, or opponent action inputs.

Use a fresh V2-shaped model for each training stage. Set WV and BV to zero and
keep their gradients/optimizer moments zero: V(s) cancels from the conditional
probability of actions. The other 3,362 parameters are trainable. Keeping the
extra 25 value parameters in the file is a packaging choice, not learned value.
This examines supervised fitting under a fixed optimizer budget. Failure is
evidence against this particular representation/optimization recipe; it cannot
by itself prove insufficient mathematical network capacity or disprove RL.

## Existing input availability and interfaces

Read-only inspection confirmed these files/directories exist:

- `replays/cg-indrill`: 282 canonical game JSON files.
- `replays/cg-top10-v2`: 86 canonical files, including 11 appearances of Indrill.
  The exact 11 game IDs are 905455435, 905455393, 905455347, 905455346, 905455155,
  905455111, 905455102, 905454402, 905454189, 905453964, 905452339.
- `docs/evidence/indrill-q-v2-corpus.json`: retained game IDs, paths, hashes,
  authoritative player identities and prior 86/86 engine validation.
- `build/analysis/neural-data/meta.json`: old 282-game manifest and explicit 40
  `gameList[*].holdout=true` records. Preserve those exact IDs, not a new "last40".
  The old 14,423-turn export is 34/42-feature sequential/sampled-candidate data;
  it cannot replace a 64-feature, whole-turn dataset. It also omitted 1,330
  teacher placement labels outside its old candidate filter.
- `build/analysis/indrill-q-v2/weights-6.bin` and matching `.npz`: frozen Q-v2
  comparator. Do not resume their optimizer or train from them.

Reusable Java interfaces:

- `CgReplay.load(Path)`, `toState()`, `nicknames`, `agentIds`, `turnStdout`,
  `failure(turn, player)`, `turns()`. Seat is `agents[].index`, not finishing rank.
- `Rules.expand(state, player, actions, pathing, playerMoves, turnResult)` supplies
  accepted distinct placement cells and accepted disruptions. `Rules.resolve`
  advances both recorded players after the observation/target have been exported.
- `new IndrillFeatures(state, BotFactory.parameters("indrill-q-v2"))`,
  `prepare(state, teacherSeat)`, public `x`, `buildCost`, `legalInk`, `neighbors`,
  `regionSizes`. Export `GameState.placeCost` and core-provided legality, not Python
  reconstructions of game rules.
- `IndrillV2Probe.java:138` is a schema example for raw features and topology;
  its private `observation` method is not a public reusable API.
- Full map identity is SHA-256 of UTF-8 `Protocol.initBlock(initial, 0)`, matching
  `BenchmarkRunner`. Do not substitute a graphics-string hash or seed alone.

Reusable Python interfaces in `tools/indrill/train_q.py`:

- `State(features, legal_build, legal_ink, builds, inks, rewards, done)`;
  `Episode(identity, width, height, region_count, regions, costs, states)` computes
  padding/topology metadata. For a teacher-only row, use the teacher perspective
  in slot 0, duplicate features in unused slot 1, and never sample slot 1.
- `Transition(episode, step, player, next_step, reward, discount)` is accepted by
  `Batch(transitions, next_states=False)`. A one-state supervised row can use
  `(step, player, next_step, reward, discount)=(0,0,0,0,0)`; do not call
  `nstep_transitions` or read successor data.
- `choose_actions(build_q, ink_q, batch)` returns sorted cell tuples and ink IDs;
  `choose_build(values, costs, legal)` preserves Java's deterministic ties.
- `train_q_v2.Model(rng)`; `forward(batch, cache=True)` returns
  `(build, ink, value, (h0, local, global_h, h1, region_h, mean_h1))`.
- Inherited `Model.adam(gradients, learning_rate, clip_norm=10.0)` updates all
  entries of `model.p`, `model.m`, `model.v` and increments `model.updates`.
- Existing `loss_gradients` is TD regression, not a supervised classification
  loss; do not call it with fabricated scalar Q targets. Implement a separate
  diagnostic loss/backward adapter without rewriting the frozen trainers.
- `check_parity.load_probe` demonstrates shape/topology checks but also requires
  Java-Q fields; copy/adapt its validation contract, do not invent Q metadata.

Write all new diagnostic outputs under a new `build/analysis/indrill-capacity-v1`
directory, with write-once manifests and distinct stage A/B checkpoints. Never
invoke existing training CLIs with default Java output paths. Existing
`write_weights(..., java_path=None)` can avoid generated Java writes, but a
diagnostic-specific checkpoint writer is clearer than fabricating a target net.

## Teacher export, validity and leakage boundary

Export a new dataset from recorded replays, not new self-play. For each selected
turn, prepare the original pre-turn state before either player's actions. Target
is `(sorted accepted own cells, accepted own disruption or -1)`. Cell order in
stdout does not matter; empty build and empty ink are valid labels. AUTOPLACE is
expanded only by core. The intended teacher cells remain the target even if a
simultaneous opponent placement later makes a cell neutral.

Pin raw replay bytes and input manifest. Verify every used replay with core and
the existing CG validation mechanism. Never select by game winner, eventual
income, opponent action, teacher outcome, or model score. Missing/unparsable
teacher stdout or an explicit server failure excludes that turn with its reason;
it must not become a fabricated WAIT example. A well-formed line with skipped
commands contributes its accepted subset, labelled `hadSkippedActions`; report
this subgroup separately. Reconstruction of later states must still reproduce
the server rather than dropping earlier turns from the simulation.

Check all labels before training: cells distinct, legal in exported pre-turn
mask, total exported price <=3, target legal or NONE. Any disagreement between
core-accepted moves and these masks is a contract failure: stop and retain the
evidence, do not quietly discard the difficult label or widen the action space.
As a decoder coverage check, assign +1 to each chosen cell and -1 to every other
legal cell, and similarly +1 to the selected ink/-1 to other inks (all -1 for
NONE). The exact frozen decoder must recover every target. This proves label
availability to unconstrained logits, not realizability by shared network weights.

## Deterministic splits and bounded observations

Before training, create the full game/map manifest from all 282 old games and the
11 extra Indrill games. Deduplicate exact game IDs; contradictory bytes for one
ID stop setup. Group all games by the core full-map hash, regardless of seat or
opponent. A group containing any old holdout ID or any of the 11 later IDs belongs
to holdout in its entirety. Never move one of those games into training.

Report old-holdout and later-11 results separately; a map present in both is one
map in the combined result, with both cohort tags. Other games on those maps are
holdout companions, not extra independent maps. These are development holdouts:
the old holdout already informed neural work and the later corpus was inspected
for engine/research purposes. There is no claim of pristine confirmation.

Order eligible training map groups by ascending
`SHA256("indrill-capacity-v1/train/" + fullMapHash)` (lowercase ASCII hex input).
Within each group choose the lowest numeric gameId containing at least 16 valid
teacher turns. Such a group is eligible for the bounded training panel. Do not
choose a game or turn according to its action label. If fewer than 128 eligible
training groups exist, stop setup as insufficient data; do not change the rule
after looking at training behavior.

- Stage A: first 16 groups, four turn-index quantiles per representative game,
  exactly 64 observations. From its sorted valid turn list of length T, use
  indices `floor(j*(T-1)/3)`, j=0..3. This includes the first and last valid turn.
- Stage B: first 128 groups, 16 turn-index quantiles per representative game,
  exactly 2048 observations. Indices `floor(j*(T-1)/15)`, j=0..15. A is a training
  subset in map identity, not a validation set; B starts independently.
- Holdout: one representative per held-out full-map group, preferring the lowest
  gameId among the 11 later IDs, otherwise the lowest gameId among the old 40,
  otherwise the lowest group gameId. Export min(20,T) unique evenly spaced valid
  turns using the same endpoint formula. Maps with no valid turn are reported
  as unavailable, never replaced by a training map. This bounds the primary
  holdout panel to at most 51*20 observations before map deduplication.

Archive selected `(mapHash, gameId, teacherSeat, turn)` tuples before gradients.
After raw feature export, hash the complete model input: dimensions, ordered
float32 feature bits, topology, region membership/counts, legal masks and costs.
Any identical input across training and holdout stops setup for leakage review;
do not continue with overlapping rows or silently resample. A reviewed revised
split would be another pre-training protocol, not a tuned restart.

## Feature collisions, symmetries and ambiguity

Within each split, keep and report exact repeated-input groups and their joint
label histograms. For a deterministic policy, the empirical exact-match ceiling
for such a group is its largest label count. Sum these maxima to report an
observational ceiling. Conflicting labels may reflect teacher randomness or
history omitted from the observation; they do not necessarily indicate a bug.
Do not delete conflicts, select the convenient label, or count the same exact
observation as independent evidence. If the Stage-A ceiling is below 63/64,
classify `INPUT_ALIASING_LIMIT` and do not run either training stage.

Do not label geometric reflection/rotation as an architecture symmetry without
proof: raw x/y features and directional border/region features can distinguish
them. A sufficient real-arithmetic local invariance certificate is identical cell features plus
the same normalized multiset of neighbour feature vectors (and the same global
feature multiset). In real arithmetic that forces equal hidden states for every choice of weights.
Equal normalized multisets of those cell signatures also certify equal region
pooled representations. These sufficient conditions are not a complete test of
network expressiveness. Report any target contradicted by a certified tie and
the decoder's exact tie rule; distinguish this mathematical constraint from a
failure of the optimizer. A simplistic equality of neighbour counts is not a
sufficient signature. No augmentation or feature change belongs in this run.

The ignored checker now reports mathematical normalized-multiset signatures and
ordered refinements for the frozen Java17 scalar implementation separately.
An ordered refinement preserves the exact neighbour operation order/divisor and,
for regions, equal-size ordered cell pooling. Finite intermediates are required.
These refinements are **not** universal NumPy runtime equality certificates:
synthetic checks found row-dependent matrix rounding with identical operands.
Same-cost shortlist and ink-scan tie constraints are conditional on finite equal
logits and resolve before aggregate pattern scores; no claim extends to other
patterns, unequal costs or arbitrary floating-point implementations. Retain
`decoderIncompatibilityNotProven` where a universal bound is not established.
Do not subtract these descriptive ties from the empirical exact-input ceiling or
add an early stop. `certificate-synthetic-first.json` and
`certificate-java-first.json` are implementation witnesses with source/class pins,
not a proof of optimization success or universal impossibility. The loader binds
the complete certificate report to inputs and labels, and the trainer checks this
binding before either stage. The eventual reviewed freeze must pin the checker,
both witness reports, and their tested runtime; no real export or fit has run.

## Whole-subset likelihood and exact output derivatives

For one observation let C be legal cells, c_i their exported costs, q_i build
logits, and B*=the accepted unordered teacher set. With paint budget K=3:

    Z_build = sum_{B subset C, sum(c_i for i in B)<=3} exp(sum(q_i for i in B))
    L_build = log(Z_build) - sum(q_i for i in B*)

The empty set is included once, with score 0. No negative candidate sampling,
fixed cardinality, ordering multiplier, or separate per-cell binary loss.

Compute an exact-cost forward DP in log space, legal cells sorted by cell ID:

    A_0(0)=0; A_0(k>0)=-infinity
    A_{i+1}(k)=logaddexp(A_i(k), q_i+A_i(k-c_i))
    logZ=logsumexp(A_m(0),...,A_m(3))

An out-of-range term is -infinity. Use an analogous exact-cost suffix DP S_i(k)
over cells i..m-1, with S_m(0)=0. Then:

    p_i = exp(q_i + logsumexp_{a+b<=3-c_i}(A_i(a)+S_{i+1}(b)) - logZ)
    dL_build/dq_i = p_i - 1[i in B*]

This uses O(m*K) storage and O(m*K^2) arithmetic; K=3 is fixed. Use float64 for
DP logsumexp/marginals, cast output derivatives to float32 for the unchanged
network computation. Never enumerate O(m^3) triplets on real boards.

For legal regions R, ink logits u_r, teacher r* (-1 means NONE):

    Z_ink = 1 + sum_{r in R} exp(u_r)
    L_ink = log(Z_ink) - (u_r* if r*>=0 else 0)
    dL_ink/du_r = softmax(u_r with fixed NONE logit 0) - 1[r=r*]

Train mean(L_build + L_ink), equal per-observation/head weight, temperature 1.
Illegal/padded cells and regions have zero derivatives. This joint distribution
factorizes exactly like the existing build/ink action model; it cannot create
interactions the architecture does not represent.

For a batch, divide output derivatives by batch size once. Reuse forward caches:

    gradWB = sum_{b,c}(dBuild_bc * h1_bc); gradBB=sum(dBuild)
    gradWI = sum_{b,r}(dInk_br * region_h_br); gradBI=sum(dInk)
    dh1_bc = dBuild_bc*WB + dInk_b,region(c)*WI/regionSize_b,region(c)

Continue through ReLU/WS/WN/WG/W0 using the existing `loss_gradients` trunk algebra
(including neighbour-gradient scatter and global averaging), but via a separate
diagnostic implementation. WV/BV gradients are exactly zero. Do not fake scalar
TD targets to obtain these different per-output derivatives.

Before training, compare DP partitions/marginals with exhaustive legal subsets
on tiny boards; check NONE/empty/all-illegal cases and target permutations. Verify
output and end-to-end weight gradients by finite differences away from ReLU zero,
including masked padding and multiple regions. These are implementation checks,
not empirical architecture searches. All failures stop setup with evidence.

## Fixed training budgets and one-shot decisions

Both stages use raw float32 features and computation, Adam learning rate .0003,
clip norm10, no decay/dropout/schedule/augmentation. Each stage independently
initializes `train_q_v2.Model(np.random.default_rng(seed))`, zeros WV/BV, and zeros
all optimizer moments/counters. Batch order is shuffled without replacement each
epoch using a separate RNG. Checkpoints/evaluation outputs are write-once.

- A: model seed2026100401, sampling seed2026100411, exactly2000 updates, batch16.
  This is32,000 visits over64 observations. Record descriptive training metrics
  at0/500/1000/1500/2000; decide only on the final weights. No early success,
  alternate seed, extra update, best-checkpoint selection, or holdout evaluation.
- A pass: exact joint action agreement >=63/64 on the fixed training panel,
  with implementation/coverage checks passed. Otherwise `FIT_NOT_ESTABLISHED`;
  stop before B, retain all traces, and investigate representation/optimization
  later. The threshold is an operational memorization screen, not a capacity proof.
- B, only after A pass: fresh model seed2026100402, sampling seed2026100412,
  exactly4000 updates, batch32 on2048 states (128,000 visits). Do not copy A's
  weights. Training metrics at0/1000/2000/3000/4000 are descriptive only. Evaluate
  held-out rows once at final weights. Frozen Q-v2 is the predeclared comparator.
- Nonfinite loss, gradients or weights stop the run as `NUMERICAL_FAILURE`, with
  the current checkpoint and original configuration retained. No remedial tuning
  inside this experiment. An interrupted host may resume the exact checkpoint
  with optimizer/RNG/sample-position state; completed updates are not replayed.

Report exact unordered-set agreement, ink/NONE agreement, exact joint action,
cell precision/recall (both-empty convention stated), predicted/teacher paint,
NLL and errors by cardinality, cost pattern, turn0..19/20..49/50+, and seat.
Compute per-map means first; do not treat individual turns as independent games.
Give old-holdout and later11 cohorts separately. Do not inspect held-out scores
between updates or use them to choose another seed/loss/budget.

For a bounded transfer decision, compare B and frozen Q-v2 on the same held-out
map means of exact build-set agreement and ink agreement. Use10,000 paired map
bootstrap resamples, RNG seed2026100421, percentile95% intervals for differences.
This is a diagnostic decision convention, not an overall95% guarantee or strength
gate. `TRANSFER_EVIDENCE` requires both lower interval bounds >0, at least20
distinct held-out maps, plus all checks. Otherwise `TRANSFER_NOT_ESTABLISHED`;
report whether B fit its larger training sample and the observed train/holdout
gap. Improvement over weak Q-v2 does not mean champion-level imitation or arena
strength; absolute accuracy and the later11 cohort remain essential context.

If A fits and B transfers, the next separately designed experiment may test an
imitation prior with corrected RL objective. If A fails, prioritize the fixed
optimization/representation problem; if A fits but B does not transfer,
prioritize generalization/data limitations before larger RL. No outcome here
promotes a bot or triggers live games. Exclude all diagnostic maps from any later
fresh practical-v4 strength pool and preserve the historical Q-v1/Q-v2 verdicts.
