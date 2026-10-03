# indrill-q-v2 — learned position value and WAIT

Follow-up requested October 3, 2026, after the forum-inspired v1 lost all 300 games against v09.
This is a new local candidate; v1 sources/weights and selected v09 stay frozen.

## Hypothesis fixed before training

The v1 approximation sums full cell/region logits and compares the result with a fabricated
WAIT value of zero. In its retained probe at seed 2009003, turn 25, the losing perspective
assigned negative values to every legal construction and disruption, choosing full WAIT.
The winning perspective assigned large positive values broadly across available actions.
This supports a state-value/action-cardinality problem; it is not a causal decomposition of
all 300 benchmark losses. No timing, protocol, scoring-sign or inference-export bug was found.

V2 changes the Q approximation to `V(state) + sum(build advantages) + ink advantage`.
The learned V is a scalar head over the mean of the second hidden cell layer and enters
every complete action exactly once. Full WAIT has Q=V and trains the value head/shared
representation. Greedy action selection maximizes advantages; adding V cannot change it.
The input features, 24-channel shallow architecture, additive budget-three knapsack,
legality, ink head, original reward and Double-DQN learning rule stay the same.
Interactions among placements or between build and ink remain outside this experiment.

## Predeclared learning schedule

- Start a new random model with seed 20261004, without copying v1 learned weights.
- Bootstrap from the four retained v1 datasets (192 games, seeds 2000000/2001000/2002000/
  2003000, 48 each). No v1 strength-benchmark game is used for learning.
- Train 2,000 batches of 32 after this bootstrap.
- Collect six new blocks of 128 games at seed bases 2100000, 2101000, 2102000, 2103000,
  2104000, 2105000. Exploration is fixed at .30/.25/.20/.15/.12/.10.
- Each new block uses the latest model, with the same half-self/quarter-v09/quarter-neural
  opponent mix. Frozen opponents alternate seats within each opponent cohort. Self-play
  has independent exploration; identical greedy openings remain a known limitation.
- After every block train 2,000 batches of 32 on all accumulated games. Continue Adam,
  target-network state and replay priorities where the data identities match. New replay
  items receive the existing maximum priority. The final schedule is 960 distinct games,
  14,000 updates and 448,000 sampled transition visits; repeated prioritized visits are
  not unique transitions.
- Same learning rate .0003, gamma .99, three-step returns, Huber 1, gradient cap 10,
  target period 100, priority alpha .6 and beta .4-to-1 within each stage. Float16 replay,
  float32 computation and exact float32 Java export.
- Freeze after the last stage. No evaluation-guided tuning, additional training or retries.
  Logged TD residuals and per-round collection results are diagnostics, not strength gates.

The reward remains delta(projected ink-all score difference)/100 plus terminal win/draw/loss.
It predominantly rewards score margin and is not discounted policy-invariant shaping with
gamma .99. This limitation is retained so the architectural experiment does not also change
the learning objective. A future reward experiment must use another version and bench.

## Server games and exclusion policy

The user authorized downloading more public server games if useful. A bounded top-player
refresh is independent of this learning schedule. New public games validate engine fidelity
and can become teacher data in a separately designed imitation experiment; they are not
silently mixed into this outcome-driven v2 training set. Preserve raw replay bytes and
record the actual fetch result, including unavailable/empty responses.

Register all 960 training map hashes and three fixed numerical-probe maps before strength
evaluation. Retain all existing exclusion evidence. The fresh v4 gate starts at seed 1500000;
its continuation blocks use the standard offsets +100000/+200000. Compare with selected v09
using two workers, 300/+200/+500 pooled games, futility only below 50% at 300 or 500, final
local pass at >=55% of 1000. A local failure ends evaluation.

The live cached-boss stage remains conditional on a local survivor and the outstanding cache
refresh/authorization decision; this request to improve training and download public games
does not by itself request uploads or live boss runs. No automatic promotion or push.

Before the timed strength gate, run meaningful model/WAIT-gradient tests, Java/numpy parity,
full Java tests and engine validation. Build an isolated submission and play the same fixed
strict process panel as v1: exactly two games each, both seats, against v09 on CG maps
903510176, 904146009, 904148181, 904147739, zero grace, without CPU-heavy concurrent jobs.
Retain the selected `build/submit` artifact unchanged. Record results here and in BOTS,
DEVLOG, PLAN, then commit the completed experiment locally.

Numerical parity uses a predeclared float32 tolerance `1e-4 + 1e-6 * abs(Java value)` for
both advantage heads, the state value and full selected Q. Require exact agreement of all
sampled greedy actions. The relative term allows harmless summation differences on larger
state values; it does not relax the action-agreement check or change the model.

## Result

Completed October 3, 2026. **0 W / 300 L / 0 D vs v09; STOP_FUTILITY_300.**
No local continuation or boss games, no promotion, upload, push, tuning or retries.
Selected v09 and its saved submission remain unchanged. The combined value-head,
larger-training and balanced-seat experiment does not isolate their individual effects.

The fixed random-start run finished all seven training stages: **960 unique games,
186,162 transitions, 14,000 optimizer updates, 448,000 sampled visits**. The independent
binary/checkpoint audit verified actual episode records and retained replay keys,
Adam/target state, predecessor hashes, all source pins and exact generated Java tensor
bytes. Final binary SHA-256:
`c5cfc9c82ec34a2d3d9d91ec5e7d487fa654cca4a9583602dac00daecdd9340f`.
The final sampled absolute TD residual was 44.03 and varied substantially during training;
this is not evidence of convergence. The six new collection blocks lost all 192 games
against v09 and all 192 against neural-v1; these exploratory training games were diagnostics.

Verification:

- 386 Java tests and 29 related Python tests passed, including value/WAIT gradients,
  full-action baseline counting, optimizer/replay retention and complete probe provenance.
- 292/292 retained server games and **86/86 newly downloaded public top-player games**
  reproduced exactly. The old Indrill replay bytes were preserved; public download audit:
  [indrill-q-v2-corpus.json](evidence/indrill-q-v2-corpus.json).
- All 18 fixed numerical observations passed the predeclared absolute-plus-relative
  float32 tolerance; 8,514 values checked, exact build and ink choices **18/18 each**.
  Maximum absolute error: build 0.0000191, ink 0.00000763, V 0.0001188, full Q 0.0001221.
- Isolated source: **82,755/100,000 characters**, compiled and exact-source verified;
  SHA-256 `2a35a982850bcb2e657528fa20ffd0ca3520fae7ed7f0c38a48f98077f63ee65`.
  Fixed strict process panel: **0/8 problem games**, 800 turns, zero timeouts/overruns,
  first response <=679.183 ms, later response <=43.888 ms (consumer <=43.978 ms).
- All 960 training hashes and three disjoint probe hashes were present in the gate's
  exclusion union before play. The completed block used 150 new full-map hashes, both
  seats, two workers, CG timing, seed base 1500000; zero bot errors.

There is a limited positive score diagnostic: on the same eight process-map/seat cases,
the candidate's mean score rose from v1's 11,509.75 to **21,020.875**, and was higher in all
eight. Both versions lost all eight. This reused panel is small, and timed v09 choices can
vary; it is not a win-strength gate. The separate fresh v2 benchmark averaged **8,945.48
vs 22,342.26**, a deficit of 13,396.79 per game. Its maps differ from v1's benchmark.

V2 placed 67,189 cells using 75,891 paint, against v09's 43,093 cells / 57,774 paint.
Placement occurred on **86.41% vs 66.30%** of turns; accepted disruption on **34.09% vs
68.58%**. Every game lasted 100 turns. More construction did not produce winning income,
and the learned disruption policy was weak. These observations do not prove a unique cause.
In-process benchmark maximum calls were 52/53 ms across all turns, including the larger
first-turn allowance; the strict process panel is the separate later-deadline evidence.

Known approximation limits remain: no placement-set or build/ink interactions, an
uncentered V/advantage decomposition and a margin-dominated shaping reward. The learned
WAIT defect is repaired and tested, but a much larger fixed training run still did not
establish useful win strength. Further objective/action-model or teacher-prior work needs
a separately named version and predeclared evaluation.

Two check-launch mistakes were corrected without changing the frozen model or discarding
games: parity referenced a nonexistent `train_q.ROOT` (fixed repository-root resolution,
added an end-to-end CLI regression), and an unquoted PowerShell JSON property prevented
Gradle from starting the first process test (quoted properties; no game had started).
The running driver retained its original source while resume-only verification guards
were strengthened; the started driver and unchanged calculation-source pins are recorded.

Complete training, numerical, process and benchmark evidence:
[indrill-q-v2.json](evidence/indrill-q-v2.json).

## October 3 follow-up review: what the failed runs actually establish

This is a source/code/evidence review, not another strength run. The author's
[original post](https://www.codingame.com/forum/t/summer-challenge-2026-feedback-strategies/208574/5)
was read again in the browser. Historical strength verdicts remain unchanged.

The useful starting point is the contrast between **neural-v1's 558/1000 vs v09**
(but failed boss regression, 12/40 vs 17/40) and **both Q candidates' 0/300**.
Learning useful actions from the leader has already worked locally. Learning them
from our current random-start Q pipeline has not. Neither result establishes a
ceiling on reinforcement learning or reproduces the unpublished champion model.

### Highest-priority learning-objective issue

The author describes an early score signal and a late reward dominated by winning.
Our implementation adds raw projected score-difference increments divided by 100
to a terminal outcome of only +/-1. These are different scales: a final margin
of 10,000 points contributes about 100 undiscounted reward units, versus one for
winning, when projection starts at zero and ends at actual score. With gamma .99,
even that telescoping interpretation no longer describes the discounted objective.
This is a confirmed objective property, not proof it alone caused every loss.

`InkProjection.scores` also destroys all disruptable regions, then returns already
earned scores if no connection remains possible. In those states its difference
provides no extra advance signal for unfinished routes. Raising a region's
instability without changing the board or earned score cannot change this
projection: it was already hypothetically inked. Delayed credit through Q-learning
is still possible; the shaping itself does not teach those preparatory hits.
Do not assume our interpretation of the author's brief reward description is his
actual implementation or normalization.

The retained final collection block was audited without training or new games:
**128 games, 12,446 unique turns / 24,892 perspective records**. On every turn,
the shaped reward after subtracting the terminal outcome matched the ordinary
earned-score-difference increment divided by 100 (tolerance .01 reward units).
The projection added no different delta on this entire block. Absolute
undiscounted episode return: median **52.87**, 90th percentile **253.84**, maximum
**548.14**, against terminal magnitude 1. In **32/128 games (25%)**, even the
terminal-step shaping magnitude exceeded 1. This is descriptive evidence from
the last training block, not a population strength result or a proof about all
possible boards. Both perspectives duplicate magnitudes, not independent samples.

[Audit evidence](evidence/indrill-reward-audit-v1.json) includes every episode and
the independently verified compressed dataset SHA-256 `097cc7b8...39f`.
Reproduce with `python tools/diagnostics/indrill_reward_audit.py --out build/analysis/indrill-review/reproduced.json`
(requires the retained dataset; refuses an existing output). The tool only reads
exported scores/actions/rewards; game rules are not reimplemented.

### Action model and training limits

- V2 correctly trains WAIT and counts V once, but adding the same V to all actions
  cannot directly change their ordering. The experiment also changed training
  volume, random initialization and seat balance, so its effects are not isolated.
- The model still sums fixed cell advantages and chooses ink independently. It
  cannot condition a cell's marginal value on the other chosen cells or this
  turn's ink target. The game contains real complementarity: in saved boss game
  904554568, turn 26, a three-cell chord changes income 132:126 to 64:186, while
  every proper subset leaves immediate income unchanged
  ([subset study](network-chords-v1.md)). This demonstrates a risk for the action
  approximation, not a proof that long-term Q must equal immediate income.
- The original post itself uses whole-quota knapsack and regional mean pooling.
  Neither proves that its undisclosed joint-value parameterization matches ours;
  conversely, neither justifies claiming that the author used sequential actions
  or that regional mean pooling was an implementation mistake.
- The 3,387-parameter model has one neighbor-mean block and global averaging.
  Capacity and optimization are still unmeasured separately. Best/Good/Path and
  the all-cell instability surcharge are present; simply adding those same
  features again would not address the current failure.
- 448,000 sampled visits over 186,162 final transitions is about 2.41 visits per
  transition in aggregate, unequally distributed by prioritized replay. The last
  stage alone samples 64,000 visits over that full pool. Final sampled absolute
  TD error is 44.03 with Huber threshold 1. These numbers do not establish
  convergence, and v2 does not show that more training could never help.

### Recommended next work, in order

1. **Separate capacity from RL credit assignment.** Freeze the current Q features
   and action representation. Run a separately named supervised diagnostic using
   the existing leader corpus, with splits grouped by full map/game. Fit the ink
   target and the complete unordered placement set; do not count a different
   ordering of the same triplet as an error. Report small-subset fit, held-out
   set/cell agreement, target agreement and unrepresentable/filtered teacher
   actions. There is no TD bootstrap in this diagnostic. Failure to fit even the
   training subset sends work to representation/optimization; a fit shifts the
   priority toward reward, exploration and the outcome-learning pipeline.
   Existing teacher holdouts are development diagnostics, not fresh confirmation.
2. **For the next strength candidate, prefer an imitation prior.** Start outcome
   fine-tuning from retained neural-v1, whose sequential, target-conditioned
   construction already plays competitively locally. Keep its features/action
   architecture fixed for that experiment, retain a teacher-regularization term,
   and predeclare a win-oriented reward, opponent mix and fixed update budget.
   Use fresh local training maps against v09, neural-v1 and historical snapshots;
   do not train on the cached boss regression maps. Track earning/survival of
   placed cells, coherent completions and ink behavior, not paint usage alone.
   This is a proposed new experiment, not a promise of improvement or promotion.
3. If continuing the Q lineage instead, make reward normalization/objective a
   separate version. Test terminal dominance explicitly on saved late states and
   measure the shaping contribution by phase before collecting a large corpus.
   Do not simultaneously change reward, action interactions and architecture and
   then attribute a result to one of them.

All candidate training data and diagnostic maps must be excluded from a fresh
practical-v4 strength gate. Preserve 300/+200/+500, two workers and futility-only
looks; do not rerun the rejected versions. A local survivor still needs the boss
stage with a valid refreshed control cache and the applicable authorization.

### Smaller independent opportunity in selected v09

`RolloutBot.propose` supplies an instability surcharge, but
`Pathing.cheapestPath` applies extra cost only to empty cells; existing rails have
zero entry cost. The alternate route only blocks one predicted threat region.
The leader's all-cell risk cost, already implemented by `TacticalPathing` for the
Q features, suggests a narrow **additional bypass-candidate** experiment in v09.
First audit saved threatened states: which affordable routes are newly generated,
which are pruned, and which are actually evaluated at nominal time? Measure exact
core income preserved under explicitly hypothetical ink; recorded future enemy
actions are oracle diagnostics only. Avoid forcing defense: an earlier forced
defense experiment already failed. Do not change canonical pathfinding semantics
to implement a strategy proposal. Any new core helper still needs tests and
`cgValidate`.

A direct merger of neural-v1 and v09 requires a packaging plan: selected v09
already occupies 95,278 of 100,000 source characters. It is not a free addition.

### Corrections to historical interpretations

Keep old results, but do not reuse the old claim that sub-millisecond inference
means a heuristic. The author trained a neural Q policy. Broad route-class coverage
also does not identify his complete routing valuation, and neural-v1's boss failure
does not by itself prove covariate shift. Those were interpretations, not isolated
causal experiments.
