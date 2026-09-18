# Arena opening survey v1 — protected ownership before route completion

Date: 2026-09-18. This is a descriptive replay survey and a proposal for a separate
future experiment. No strategy, parameter default, frozen version or submission was
changed by this survey; no strength games or process-readiness checks were run.

The clearest fresh pattern is that leaders attempt to acquire track ownership near
several towns, especially inside regions containing towns, before completing those
routes. This suggests an opening based on future shared-network traffic rather than
only on the value of finishing one selected route. The observations do not establish
that copying this behavior would beat v07.

## Fixed inputs and measurement

The evidence contains 48 canonical CG games: 23 newly downloaded leader games, five
new own arena games, and the 20 latest boss games selected for the parent study.
The leader/own selection is exactly the 28 `saved` entries in
`build/cg-fetch-v07-study.log`; the two entries reported as already present are not
added. Boss IDs run from 904553513 through 904555504, with the exact fixed list in the
reproducer. Each raw file's relative path, game ID, byte count and SHA-256 is recorded
in [the evidence JSON](evidence/arena-opening-survey-v1.json).

The leader sample includes both sides of 23 games, **46 game-sides, not 46 independent
games**. Players and pairings repeat: RamzaBeoulve contributes 17 sides, Indrill five,
sZoom seven, Pduhard- five, Waffle3z five, zasmu five, delineate one and Saelyos one.
Leader, own-arena and boss cohorts have different maps and opponents. Their opening
and income means are descriptive; they cannot rank strength or isolate a causal effect.
The boss-side v07 assignment comes from the user. The own arena metadata identifies
agent 6747874; this survey does not verify the uploaded source hash.

All turn numbers below are one-based. An **attempt** is an explicit `PLACE_TRACKS`
occurrence in stdout. Neutral collisions count as an attempt by each player. An
**accepted owned placement** is a recorded type-1 event attributed to player 0 or 1;
neutral owner-2 events are excluded. This counts placements, not the surviving track
stock at the end of the window. There are no repeated coordinates within the measured
three-turn attempt windows and no opening `AUTOPLACE` commands.

A **protected** cell belongs to an initial-map region containing a town:
`GameState.regionHasTown` and the `Rules.expand` disruption checks establish that this
region cannot be inked. Adjacency means Manhattan distance one to a town; the number
of towns is the union touched by the side's measured cells. Protection preserves the
track itself, not its connection to the wider network or its future income.

The script reads existing viewer events; it does not simulate game rules or compute
paths or hypothetical scores. Income is summed from recorded type-3 point events.
Observed placement/ink events maintain occupancy solely to classify empty-region
disruptions. Event point sums agree with recorded final scores in all 48 games; no
nonempty server summaries are present. This is an internal data check, not a substitute
for the parent study's separate core replay validation.

## Opening pattern

- **Leaders:** first turn, 124/138 attempts are protected (**89.9%**) and 99/138 are
  adjacent to towns. At least two towns are touched in 36/46 sides and at least three
  in 16/46. First three turns: 271/411 attempts protected (**65.9%**), 228/411 adjacent
  to towns, mean **4.26 distinct towns**.
- **v07 against the boss:** first turn, 12/58 attempts protected (**20.7%**), 26/58
  adjacent to towns; at least two towns in 6/20 sides and three in none. First three:
  45/170 protected (**26.5%**), 70/170 adjacent, mean **3.05 towns**.
- **Boss:** first turn, 20/60 attempts protected; first three, 53/173 protected
  (**30.6%**), 62/173 adjacent, mean **2.85 towns**. The distinct leader opening is
  not a description of the boss's dominant behavior.
- **Our five fresh arena games:** first turn, 4/15 attempts protected and exactly one
  town touched in each game. First three: 11/43 protected, mean **2.40 towns**.

Competition materially changes the interpretation. The leaders' 411 attempts become
**247 owned placements plus 82 neutral collision cells**; each neutral cell accounts
for two attempts. Of the owned placements, **145/247 (58.7%)** are protected. Their
mean number of towns touched by actually owned placements is **2.696**, essentially
the same as v07's **2.70**. For v07, **39/148 owned placements (26.4%)** are protected.
Thus the evidence for intended multi-town claims is stronger than the evidence for
realized ownership spread. The protected-ownership difference remains visible.

Indrill is a particularly clear example: 41/45 attempts over the first three turns
are protected, with mean 4.6 towns touched. In **904811378**, Indrill opens with
`(5,10), (21,3), (12,7)`: three different towns, all protected. Turn two adds
`(13,8), (12,6), (27,11)`; turn three adds `(6,11), (19,3), (2,6)`.
All nine attempts are in protected regions, spanning five adjacent towns. The raw
commands are retained in the evidence. RamzaBeoulve's broader sample has 105/153
protected attempts over the first three turns and mean 4.29 towns touched.

Recorded early income does not prove a benefit. The leader sample first scores at
mean turn 4.50 and has mean 149.59 points after turn 15; v07/boss score first at mean
turn 3.00/3.35 and have mean 370.25/532.05 points after turn 15. Different maps,
opponents, collisions and sabotage make the leader-versus-boss comparison unsuitable
for strength claims. On the paired actual boss games, v07's earlier first scoring
turn also does not imply a higher subsequent income. All individual values and
turn-8/15/20 summaries are retained.

## Another visible pattern: disruption before tracks exist

Leaders disrupt on the first turn in 41/46 sides; **39** of those targets are wholly
empty even after this turn's recorded placements. In the first three turns they issue
130 disruptions, **92 into empty regions**. Across the full sample, disruption occurs
on 2172/2200 player-turns (**98.7%**), including 198 empty-region events.

v07 issues no first-turn disruption in the 20 boss games, only 27/60 possible opening
three-turn disruptions, and no empty-region disruption across all 1581 player-turns.
The boss issues 17/20 first-turn disruptions, ten into empty regions; its first-three
total is 52/60, including 12 empty-region events. A region is called empty only when
it has no own, enemy or neutral track in the recorded occupancy immediately before
the disruption event. This observation is consistent with advance territorial denial,
but does not prove the players' intent or the counterfactual value of the targets.

## What has already been tried

Neither protection nor early disruption is entirely new to this project:

- **v04 safe idle network extension:** preferred uninkable town regions when spending
  otherwise idle paint. Its historical 300-game screen against v03 was **194-106,
  64.7%**, and it was promoted. This was a fallback extension policy, not an opening
  ownership auction across several incomplete projects. See the corresponding entry
  in [DEVLOG](DEVLOG.md).
- **HeuristicBot:** filled protected cells of its selected route before other cells.
  The whole independent heuristic scored **126-174/300, 42.0% against v05**; protection
  was not isolated as an ablation. See [the heuristic policy](heuristic-policy.md).
- **Predicted early disruption:** predicted the opponent's best route on turns 0-1.
  Its historical paired v03 result was **85-85-30/200, 50.0%**; it was rejected.
  Matching leader-like early disruption was therefore already shown insufficient as
  a promotion argument. Territorial cutset planning would be a different hypothesis.
- **SPREAD:** divided paint between two retained complete-route projects, requiring
  independent disruptible regions. It failed its direct gate (53.6667%/300, then
  50.60%/1000 versus v06); the separate QuickIncome ablation was 51.50%/300. See
  [the experiment](spread-construction-v1.md). This does not test ownership claims
  spread over many city approaches without a commitment to complete each project.

The existing network-after-base generator requires a positive immediate income
difference for a singleton. An opening claim with zero current income does not pass
that filter. Ordinary v07 proposals rank completed routes and construct their ordered
affordable prefixes; no recorded experiment specifically values a cross-route opening
ownership portfolio by its future traffic.

## Concrete next experiment concept

Propose an independent **protected opening claims** policy on the then-selected
control. The first prototype should change only opening construction; keep sabotage,
later rollout behavior, budgets and runner margin unchanged. Do not combine it with
empty-region sabotage or multi-cell network chords in the same strength experiment.

Use the first three turns as a fixed initial scope. During the existing core route
proposal queries, accumulate a primitive per-cell potential-traffic count across
distinct desired town pairs, before the small retained-route shortlist discards most
projects. Deduplicate alternate geometries for the same pair; do not count reverse
copies of one proposal as independent evidence. Prefer affordable free cells inside
town regions, weight demand by the remaining paint needed to make its connection
useful, and cap a single city's share of the three-paint bundle. This can acquire
several valuable city approaches while their immediate income is zero. Use core
pathing and core scoring for all mechanism diagnostics, without an alternative rules
implementation. Candidate parameters and every bound belong in `Params`.

This is a proposal, not a frozen protocol. Exact weights, quota, selection behavior
and fallback must be fixed before strength games. First demonstrate that the policy
creates the intended legal bundles on both sides of saved positions, accounts for
terrain packing and anticipated neutral collisions, and stays within the turn budget.
The present eight-turn rollout may undervalue a claim completed much later; simply
adding a zero-income root without addressing that evaluation limitation is not a
complete implementation of this concept. A fixed opening rule is one clean initial
way to test the hypothesis without claiming the current rollout measures its full
future value.

Then run one prospectively fixed practical-v2 comparison against the selected control,
including the required matched older/style opponent and exact-artifact checks only
as the gate prescribes. Behavioral similarity and this replay sample are not a gate.
The proposed opening policy remains unimplemented; this report records no benchmark,
promotion or upload.

## Reproduction

The raw replay files are gitignored and must be available at the recorded paths.
Run from the repository root:

```powershell
python tools/diagnostics/arena_opening_survey_v1.py --check docs/evidence/arena-opening-survey-v1.json
```

This checks exact JSON equality, including every raw SHA-256, per-side measurement and
aggregate. To regenerate at an explicitly chosen output path:

```powershell
python tools/diagnostics/arena_opening_survey_v1.py --out build/analysis/arena-opening-survey-v1-reproduced.json
```

The [reproducer](../tools/diagnostics/arena_opening_survey_v1.py) uses only Python's
standard library. No current/frozen strategy source, submission artifact or benchmark
pool is read or changed by reproduction.
