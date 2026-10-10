# Spatial learning audit v1 — original roadmap point 3

Predeclared 2026-10-10, base revision `794a482`. The user selected the original
plan's useful-learning/value diagnosis after distinguishing that plan from the
additional v4–v8 experiments. This is a diagnostic of frozen policies, not v9,
new training, a strength gate or a promotion. Selected `current = v09` stays fixed.

## Questions and fixed scope

1. What useful complete-game behaviour did v2/v3/v4 learn relative to the fixed
   initializer: score, income onset, construction and connections?
2. Do their critic outputs predict the actual continuation they produce? Does the
   frozen v5 critic transfer from teacher games to student games?
3. Can the representation distinguish placements whose benefit is delayed?
   Separate a structural mechanism test from evidence of playing strength.

Run exactly 64 complete games, serial, no warmup: seeds 22000000..22000003,
both opponents `v01` and `v09` on every map, both learner seats, and learners
`spatial-ppo-v2-init`, `spatial-ppo-v2`, `spatial-ppo-v3`, `spatial-ppo-v4`.
All bot calls receive 60000 ms to isolate finite-policy behaviour from contest
timeouts. This is an ample-budget diagnostic, not a CG-budget comparison.
Record elapsed times and reject erroneous, skipped or truncated actions.
Validate all four map hashes against the saved prior-map union before play.
There are only four independent map clusters; no significance/strength claims.
Matched learners share map, opponent and seat. No outcome-driven extension,
checkpoint selection, tuning, extra teacher games or optimizer updates.

For every real turn retain preturn protocol states, requested and accepted plans,
core scores/income/connections, terminal status, acting model's stage-zero value,
and the unchanged v5 value. Independently replay every retained game through
`btk.core`; all scoring, legality and path calculations remain in that package.

## Targets and interpretation

PPO critic target: Monte Carlo return of each complete recorded learner game,
discount gamma .99 per real turn, reward `(income gap + terminal sign * 30)/100`.
This is a native-unit transfer check, not in-distribution validation: the original
training sampled policies/opponent mixtures; these games use greedy policies.
The v4 critic is explicitly uncalibrated because distillation changed its trunk
while freezing the value-head coefficients. Do not treat it as a fitted critic.

V5 target: `(final learner score gap - preturn learner score gap)/10000`, gamma1,
no terminal bonus. Its training used v09 teacher continuation, so student-game
evaluation tests transfer; it does not validate counterfactual search ranking.
Report error separately by learner, opponent and fixed turn bins
0..24/25..49/50..74/75..99. Compare zero and current-income extrapolation to the
public turn100 limit, without using future termination or future income.
RMSE, MAE, bias and correlation are descriptive; undefined correlations are null.
Each contributing game has equal metric weight, with equal weights for observed
turns within that game. Unused paint is recorded as a STOP proxy, not proof of a
voluntary premature STOP: affordable legal choices may have run out.
Turns are correlated, not independent experimental samples. Do not rank raw errors
across PPO and v5 units. Preserve per-game metrics and paired score differences.

The fixed mechanism probe uses an exposed synthetic board and scripted legal
continuations with a known delayed connection, validated by core transitions.
It checks identical local observation patches/equal build logits and records
policy/value response before and after completion. If exact local aliases and a
valid route require separate fixtures, report them separately. No fixture fitting,
weight changes or interpretation as evidence of population strength.

Historical optimizer logs may be summarized: reward/value scale, gradient norms,
KL and clipping. They do not retain actor/critic component gradients, so gradient
domination remains a hypothesis, not a demonstrated cause of v3 regression.

## Decision rules and completion

- Better score/income than initialization demonstrates useful but limited learning.
- Improvement against v01 without comparable v09 results indicates a competitive
  planning gap; it does not isolate its architectural or optimization cause.
- A critic worse than simple baselines on student continuations is not suitable
  for reuse unchanged in search on those continuations.
- Exact local aliases with different delayed consequences establish a mechanism
  limitation. Frequency in real games and benefit of a fix require a new experiment.
- Finish with one justified next implementation hypothesis; do not automatically
  train it or extend this audit into another strategy version.

Before measurement: scoped Java tests, focused Python tests, 292-replay engine
check, frozen source/runtime/protocol/map identities. Preserve existing unrelated
untracked experiments; the scoped Java suite excludes only the pre-existing
unregistered `PduhardBeamBotTest`. No submission changes are made, so this audit
does not need new submission/process/strength/boss batches. Existing frozen bot
bytes are checked before and after. Archive compact traces, report in PLAN/BOTS/
DEVLOG and commit the completed diagnostic locally. No upload or push.

## Results

Completed the single fixed run: **64 terminal games, 6,280 real turns**, four
fresh map hashes disjoint from **21,897** prior maps. All requested/accepted
actions were legal, all policy calls completed, no retries or extra games.
Collection lasted about 6m27s. Independent replay checked all 64 games/6,280
transitions, both perspectives and both recorded stage-zero values on every turn.
All 645 frozen source/runtime/protocol files and 130 historical policy/core/source
files were unchanged through measurement and analysis. Scoped Java: **604 passed**;
Python: **11 passed**; engine: **292/292** captured games reproduced exactly.
Six pre-existing unrelated Pduhard tests were excluded as declared.

### Useful learning exists, competitive play remains unresolved

Each result below has eight games on the same four maps with both seats:

- Initializer: 0W/8L against both opponents; mean score gaps -19,722 vs v01 and
  -20,325 vs v09.
- V2: 0W/8L against both; gaps -4,460 vs v01 and -13,734 vs v09. It improves the
  matched gap over initialization by 15,262 and 6,590 respectively.
- V3: 0W/8L against both; gaps -14,595 and -18,207. Its policy regresses relative
  to v2 in this diagnostic, consistent with the earlier direct comparison.
- V4: **8W/0L against v01**, gap **+11,162**; **0W/8L against v09**, gap **-8,460**.
  Compared with v2, its paired gap improves by **15,622** vs v01 and **5,274** vs
  v09, with positive mean improvement on each of the four maps for both opponents.

Every game, including initialization, earned income. V4 first scores against
v09 after 9.5 turns on average versus v2's 9.0; its improvement is not evidence
of earlier first connections. Track participation and unused paint are retained
as descriptive traces: ownership of any rail on a connection is not exclusive
ownership, and unused paint is not necessarily a voluntary premature STOP.
These four ample-budget map clusters do not establish a population win rate or
replace the practical-v4 promotion gate.

### Value: distinguish prediction from action choice

The frozen **v5 teacher critic transfers poorly**: its RMSE and MAE are worse
than current-income extrapolation in all eight learner/opponent groups. On
v4's games against v09, remaining-score RMSE is approximately **12,905 points**
versus **4,753** for the income baseline and **5,984** for zero prediction.
Its average bias is about +8,652 points, with correlation -0.785. Do not reuse
this head unchanged as student-continuation value in search.

This does **not** mean value learning failed everywhere. The native **v3 PPO
critic beats both baselines on its own trajectories** against both opponents.
Against v09, RMSE is **61.11** in PPO's discounted reward units versus **101.53**
for zero and **71.01** for current income; correlation is 0.718. Its positive bias
remains 34.33. Better prediction of this weaker policy's continuation neither
rescues its choices nor validates counterfactual action ranking or transfer to v4.
V4's native head remains explicitly uncalibrated after policy distillation.

Historical v3 logs show mean pre-clipping gradient norms increasing from 143 to
1,118 across update quarters while the cap is 0.5; 41/128 update means have KL
above .02. V2 has none. Critic/policy loss imbalance is a hypothesis worth keeping,
but component gradients were not retained and no causal attribution is established.

### Demonstrated delayed-route representation limitation

On the declared constructed board, investment A=(10,9) and alternative B=(10,15)
have bit-identical **1,176** local input floats and no nearby town embedding.
All four models give exactly equal BUILD logits/probabilities to A and B, for
both seats; each is part of a 266-cell tie. The same legal continuation yields:

```text
Initial A -> identical continuation -> gains 0, 0, 0, 0, 0, 15
Initial B -> identical continuation -> gains 0, 0, 0, 0, 0,  0
```

The three local convolutions and common global vector cannot distinguish these
two local inputs by changing their weights alone. This is a structural alias,
not proof that A is globally optimal, that such states are frequent, or that it
caused the match losses. Neither compared cell was the actual greedy first choice.
The script is nonterminal and was not used for training or selection.

V5 prefers B during intermediate steps 3–5, then raises A's prediction by about
4,166 score units when the connection appears. The new income/path features are
directly visible at that point. That response does not establish foresight; the
head's full-game teacher target is not the six-turn script's measured score.

### Decision and the single next hypothesis

The diagnostic is complete; **original stage 3 stays open**. Keep v09 selected.
Useful policy learning is real, v5's unchanged transfer is rejected, and a
specific long-range representation limitation is demonstrated. No new bot,
weights, training, strength gate, boss games, upload or push occurred.

The next bounded hypothesis is **one representation change**: add a global
distance-weighted broadcast of the existing learned town embeddings to every
cell, retaining the local town injection. Use fixed nonzero distance weights
and normalization by town count, not by the sum of distance weights that could
erase spatial magnitude. This gives cells information about distant desired
destinations without adding a deeper search or changing game rules.

A separate experiment should first verify that the architectural alias can be
broken, then repeat the fixed v4 teacher-data/loss/16-epoch recipe, preserve its
splits, and check runtime and closed-loop performance. The exposed fixture is a
mechanism test, not training data. This proposal is neither implemented nor
claimed to fix the critic by this audit; future critic work must respect the
actual continuation policy and opponent distribution.

Evidence: [summary](evidence/spatial-learning-audit-v1.json),
[archive index](evidence/spatial-learning-audit-v1-artifacts.json),
[retained archive](evidence/spatial-learning-audit-v1-artifacts.zip).
The archive contains the original pre-result protocol, 64 full trajectories,
independent replay/value verification, per-game/binned analysis, historical
optimizer summaries, mechanism trace, tests and source/runtime hashes. Historical
code/weights are referenced by base revision `794a482` and verified hashes;
the new diagnostic source is included.

Reanalysis of retained traces, with a fresh output path:

```powershell
python tools/spatial/learning_audit.py --manifest build/analysis/spatial-learning-audit-v1/games/manifest.json --output build/analysis/spatial-learning-audit-v1/reanalysis.json --excluded-maps build/analysis/spatial-learning-audit-v1/exclusions.json
```

The Java entry points are `btk.tools.spatial.SpatialLearningAudit` (collection or
`verify <completed-directory>`) and `btk.tools.spatial.SpatialLearningMechanism`.
The archived run helper records the exact command/runtime used. Output files
refuse overwriting; source tests use synthetic fixtures, not the fixed fresh maps.
