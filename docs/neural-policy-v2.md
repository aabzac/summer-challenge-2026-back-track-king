# neural-v2: outcome fine-tuning from the retained imitation policy

Predeclared October 3, 2026, before collection/training/strength results. User asked
to improve neural-v1 first following the Indrill review. Selected control remains
v09. This is one independent candidate; neural-v1's original weights and source
are retained, and default submission/promotion remain separate decisions.

## Hypothesis and fixed mechanism

Neural-v1 learned useful actions from the leader: 558/1000 vs v09, but failed its
boss regression 12/40 vs the control's 17/40. Outcome fine-tuning from that policy
may improve its decisions in the states reached by its own play. Keep feature
definitions, candidate filtering, sequential construction, closing-ink guards,
network dimensions and deployed normalization unchanged. Only trained weights
change strategic preferences. New generated weights live in `NeuralV2Weights`,
never in the retained `NeuralWeights` file.

Training samples softmax actions at temperature 1 over the full legal/guarded
candidate sets. It records actual behavior log probabilities, raw features and
frozen teacher logits on the same states, after earlier placements. Forced
closing actions and decisions with no choice do not need policy gradients.
All dynamics/scoring use `btk.core`; no opponent's simultaneous action is observed
before choosing the student's move. Only student decisions are training samples.

The learning objective is terminal win/draw/loss (+1/0/-1), gamma 1, with no score
margin or ink-all shaping. Use the clipped policy-gradient surrogate from
[PPO](https://arxiv.org/abs/1707.06347), with a Monte Carlo outcome and an
opponent-cohort leave-one-game-out outcome baseline. There is no learned critic
or TD bootstrap. This is an actor-only adaptation, not a full PPO reproduction.
Advantages are not standardized. Every opponent cohort must contain >=2 games.

Optimize each head on uniform decision minibatches. Do not divide individual
episodes by their action-dependent length. The loss is the negative clipped
surrogate plus 0.02 times forward KL(frozen teacher || current) on the full
available candidate distribution. Clip ratio 0.2, global gradient norm cap 1,
Adam learning rate 0.00003, no weight decay, minibatch 64, four epochs per head
per round. Earlier rounds are not replayed as on-policy data. Continue optimizer
and RNG state; preserve exact inference-normalization bytes.

## Fixed training and data separation

- Initialization: decode the exact shipped neural-v1 int16 blobs. Initial
  re-export must preserve these blobs exactly. Training RNG seed 20261005.
- Eight rounds of 192 games each: 96 fresh maps played in both student seats.
  The opponent cycles by map pair through v09, neural-v1 and v07, giving 64 games
  per opponent per round. Seed bases 2200000, 2201000, ..., 2207000; each round
  consumes 96 consecutive map seeds. Total 1,536 games / 768 distinct maps.
- Every round uses the latest exported student. Student exploration uses an
  independent reproducible RNG per game. Frozen opponents use nominal 486/36-ms
  budgets. Offline student collection completes the full action sequence; deployed
  play retains v1's 6-ms placement reserve. This collection/deployment timing
  distinction is explicit and checked separately by strict process tests.
- After each complete block, train only that block for the prescribed four
  epochs per head. Quantize/export using the original per-layer int16 format.
  Quantized behavior probabilities and learner probabilities must agree at the
  beginning of each round to the declared numerical tolerance; retain optimizer
  state while reconciling any float/quantized boundary explicitly.
- Freeze the final round, without checkpoint selection, benchmark tuning,
  additional rounds or restarts chosen using outcomes. Collection outcomes and
  losses are diagnostics, not strength gates.
- Fixed plumbing/numerical probe: six games / three map pairs at seeds
  2209000..2209002, excluded from training and strength maps. Synthetic fixtures
  and teacher-action parity tests do not select hyperparameters.
- Use no boss-cache maps or strength games for training. Register all training
  and probe map hashes in the benchmark exclusion union, together with previous
  experiments and the original teacher corpus.

The immutable run manifest pins source hashes, previous weights/checkpoints,
data file hashes, all settings and expected stage counts. Completed stages are
not overwritten. Interrupted partial outputs are retained; resuming requires
verification of the same plan rather than silently replaying completed games.

## Verification and strength gate

Before training: meaningful gradient/clip/KL/baseline tests; exact boot weights;
greedy v1/v2 action parity with the v1 blobs; sequential feature and closure-mask
tests; Java/Python logit/probability checks on the fixed probe. Checkpoint resume
must preserve optimizer/RNG and verify data/model identities.

After training: verify all eight complete datasets/checkpoint links and generated
Java weights, numerical scoring/action parity, Java tests and `cgValidate`.
Build an isolated `submit -PsubmitVersion=neural-v2` artifact. Run exactly two
strict zero-grace process games, both seats, against v09 on each saved map
903510176, 904146009, 904148181 and 904147739. Keep CPU-heavy work sequential
with strict process checks. These are process fixtures, not strength confirmation.

Local practical-v4: candidate neural-v2 vs selected v09, seed base 2300000,
continuations 2400000 and 2500000, two workers, nominal CG timing. Complete blocks
300/+200/+500; stop only below pooled 50% at 300 or 500; final local pass >=55%
over all 1000. Exclude prior/training/probe maps before play. No retries or early pass.

A local survivor still requires the 40-game boss stage and all exact-artifact
checks. The existing outcome-guided boss pool needs a recorded refresh decision;
do not silently reuse its historical 17/40 as a fresh control or promote after
local passage alone. Live play/arena upload and strategy promotion follow the
project's existing authorization boundaries. Record any outstanding stage honestly.

## Completed training and implementation verification

All eight fixed rounds completed: **1,536 games / 768 paired maps**, with
116,135 sabotage and 245,268 construction decisions. Four passes over each round
produced 7,268 sabotage and 15,352 construction optimizer updates. The final
weights JSON is `aba8733720be99b707ea2af8765f1076ad20eff9d92e05930767b1d4aecc78ac`;
generated `NeuralV2Weights.java` is
`bf650f1102323d73f77bb9e98a1a8c1472a20b11e599e5c145499cc9e4fa93e2`.
No checkpoint was selected using strength results and no training stage was retried.

The independent [training audit](evidence/neural-v2-training.json) verified source
pins, every data file, all checkpoint links, exact optimizer update counts and RNG
permutation state. Training maps, the three probe maps, and 380 distinct original
teacher/boss/process maps do not overlap. The benchmark exclusion union also includes
all previously registered local experiments. On Windows, the audit helper needed a
path-normalization correction; no training data, weights or pinned code changed.

All **396 Java tests**, **22 Python tests** and **292/292 CG replay validations**
passed. Both the bootstrap and final generated models match Java/Python float32
scores bit-for-bit on the fixed probe's **3,704 candidates / 64 complete decisions**,
including every argmax. Original neural-v1's four source files are unchanged.

The separate Player has **88,938 / 100,000 characters**, source
`16c49e637b808e82686651368155ea77032848faf9b3c83007433d69d6eb02a9`, build
`071aacbf815fc11a98e3ee7adabca37c785ac440cf935bb7134079d165a7b7dd`.
The default selected-v09 Player remains byte-identical (`1df7c131...`).

## Exact-artifact process result: NOT_READY

The fixed eight-game panel completed with **1/8 problem games**, no retries.
Map **904148181, P0, turn 3** exceeded the strict 50-ms response limit:
**50.3003 ms** (send 0.4933 ms); the preceding turn took 47.9425 ms.
Player stderr itself reports 47 and 49 ms at turns 2 and 3, so the issue cannot
be attributed solely to consumer scheduling. The other seven games passed.
Full artifact identity and per-game timing summary:
[verification evidence](evidence/neural-v2-verification.json).

Read-only code review finds a credible inherited budget gap: initial feature
preparation, sabotage candidate generation/scoring and the mandatory first build
can run without a deadline check; the 6-ms guard before later builds starts another
complete uncapped feature/candidate/scoring pass. Exact connection-delta features
can recalculate connections per candidate. The timing report does not isolate phase,
JIT, GC or CPU costs, so this is a structural risk rather than a proven attribution
of the single observed timeout.

An initially suspected 5-ms warmup gap does **not** describe the shipped artifact.
Its generated `CGRunner` creates `MainBot`, whose `ISOLATED_ROUTE_WARMUP=true`
path already invokes a disposable policy with `WARMUP_SLICE_MS=36`.
`NeuralV2Bot.warmup(...play(...,5))` is bypassed. Merely changing that method or
adding another 36-ms wrapper would not justify a new process attempt.

The next runtime experiment should profile the expensive feature phases and make
them interruptible, discarding an incomplete feature pass and returning completed
legal actions. Changing deadline behavior can change timed decisions and requires
a new version and fresh strength evaluation; simply increasing the late-build
reserve does not protect the initial uncapped work. No such change is folded into
this fixed candidate.

## Local strength result: PASS_1000

The one practical-v4 evaluation completed all 1,000 games / 500 fresh paired maps
with two workers. Blocks were **190/300 (63.33%)**, **123/200 (61.50%)**, and
**332/500 (66.40%)**: pooled **645 wins / 355 losses / 0 draws = 64.50%**.
Both futility looks cleared, and the final >=55% threshold passed. All full map
identities are disjoint from prior reports, training, probe and teacher/boss corpus.
[Full benchmark and decision](evidence/neural-v2-v4.json).

Historical neural-v1 scored 558/1000 (55.8%) against v09 on different maps and at
a different time. The new score is 8.7 percentage points higher, but this is not a
paired neural-v2-vs-neural-v1 test or proof of that exact comparative improvement.
It does provide a complete local gate pass for the fixed outcome-trained policy.

The experiment is complete and retained, **not promoted**: process readiness failed
and the boss stage was not started. The next separately versioned experiment keeps
these exact weights and addresses interrupted feature computation under the deadline;
its changed timed behavior needs a new practical-v4 evaluation on fresh maps.

## Training diagnostics and remaining scope

Exploratory collection scored **202 wins / 0 draws / 1,334 losses**. This is not
the deployed policy's strength. Across sampled states, choices match the frozen
v1 network's maximum logit in **66.38%** of sabotage decisions and **26.33%** of
construction decisions (exact ties count as agreement). These comparisons concern
the retained network on student states, not Indrill's original recorded actions.
[Exploration evidence](evidence/neural-v2-exploration.json).

This quantifies a substantial collection/deployment difference at temperature 1.
More conservative exploration is a possible separate training experiment, not a
retuning of this run or a demonstrated explanation of its strength result.
The runtime issue and the unplayed boss stage prevent readiness/promotion regardless
of the local score. Existing outcome-guided boss maps need a recorded refresh
decision; their historical v09 17/40 is not treated as fresh confirmation.
