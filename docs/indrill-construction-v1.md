# Where the leader's paint goes: per-cell construction study, and what v08 leaves unused (2026-09-20)

Follow-up to [indrill-study-v1.md](indrill-study-v1.md), which left one question open: 48.7% of
Indrill's cells were "off every cheapest completion route" and his routing model was not
identified. This study follows every placed track through its life instead of scoring a turn
against a single tie-broken path. Tool: `gradlew cgCellStudy` (`btk.tools.CgCellStudy`, one row
per placed track for both players, engine facts from `btk.core`, analysis-only BFS for the route
hypotheses); aggregation `python tools/diagnostics/cell_study.py`. Evidence:
[indrill-cell-study-v1.json](evidence/indrill-cell-study-v1.json) (282 Indrill games, 32,924
Indrill cells, 34,563 opponent cells) and
[boss4-cell-study-v08-40.json](evidence/boss4-cell-study-v08-40.json) (the 40 cached v08 vs
Silver-boss games, 4,012 v08 cells, 4,466 boss cells).

## 1. There is no hidden routing model

A cell counts as *on some optimal route* when the paint-cheapest completion of some inactive
pair can pass through it at no extra cost (two Dijkstra runs per pair, so every equal-cost
alternative counts, not just the referee's tie-broken path). Share of cells:

| | single cheapest path (old) | some optimal route | within +1 paint | explained* | explained*, turns 0-45 |
|---|---|---|---|---|---|
| Indrill | 36.5% | 40.1% | 61.0% | 77.6% | **86.8%** |
| his opponents | 34.6% | 38.1% | 59.1% | 73.7% | 82.8% |

\* within +1 paint of an optimal completion (shared or enemy-avoiding or unstable-avoiding), or
on an own-only path not longer than an active pair's current connection (take-over), or on the
shortest unstable-region-avoiding path of an active pair that crosses an unstable region
(bypass). The remaining 22% are 87% cells placed when **no inactive pair was reachable at all**
(83% of them after turn 30): pure filler, 62% adjacent to own rails, earning 16% of the time.

Indrill and his opponents build in the same places by these classes. What differs is what
happens to the cells afterwards:

| | ever earns | lost before earning | points / paint | points / earning cell | pairs served (peak) |
|---|---|---|---|---|---|
| Indrill | **59.7%** | 22.5% | **11.7** | 24.9 | 4.4 |
| opponents | 46.9% | 25.9% | 8.1 | 20.7 | 4.1 |

Earning probability by paint slack over the optimum (0 / 1 / 2 / 3+): Indrill 70 / 73 / 77 /
71-74%, opponents 61 / 60 / 68 / 25-51%. Opponents' far-from-optimal cells mostly never earn;
Indrill's do. Among near-optimal mid-game cells 3.6% of his are alive-but-never-earning
(abandoned projects) against 9.4% for opponents. 89% of all points on both sides come from cells
that sit on three or more active connections at their peak: the game is hubs, not routes.

Off-route earning cells, by the mechanism that made them earn (pair inactive at placement =
completion; active opponent-majority = take-over; active through an unstable region = bypass):
Indrill completion 35% / **bypass 35%** / take-over 27%, opponents 35 / 15 / 49%. His off-route
work is largely relocating connections around regions that are about to ink, which is why his
connection count keeps growing while theirs stops (turn 20-30 in the first study).

So the construction side of the leader is: near-optimal completion like everybody else,
full paint every turn (2.91/3, 97% of turns), finish what you start, relocate around threatened
regions, and filler when nothing is left. The extra points come from a higher fraction of paint
that ends up earning, and from his sabotage making the opponent's paint not earn.

## 2. The same picture against the Silver boss, with the gap on our side

The 40 cached v08 vs Boss 4 games (13-27, mean length 78.5 turns), same tool:

| | cells / turn | paint / turn | placement turns | ever earns | points / paint |
|---|---|---|---|---|---|
| v08 | 1.28 | **1.55** | 54% | 82% | 27.0 |
| Boss 4 | 1.42 | **1.80** | 61% | 88% | 27.2 |

Equal efficiency per paint point; the boss simply puts 16% more paint on the board. By phase
(paint per turn, v08 / boss): turns 0-2 2.77 / 3.00, 3-10 2.96 / 3.00, 11-20 2.92 / 3.00,
**21-30 2.44 / 2.94, 31-45 1.75 / 2.19**, 46+ 0.31 / 0.50. v08 starts leaving paint on the table
from turn 20; the boss keeps building (99% / 81% of turns in those phases vs our 85% / 70%), and
its cells placed in turns 21-45 still earn 84-90% of the time. `RolloutBot` has no leftover-paint
rule at all (the `USE_IDLE_EXTEND` field is read only by the older EvalBot/HeuristicBot): when
the rollout prefers its "wait" root or the remaining proposals do not fit the leftover paint, the
paint is lost. Unused paint per turn over the 40 games: **1.45 (v08) vs 1.20 (boss)**.

## 3. leader-v2 reference (construction rebuilt, sabotage unchanged)

`btk.bot.reference.LeaderBot2`, local-only `leader-v2`: same sabotage constants as leader-v1;
construction = most valuable completion route over a 24-turn horizon (shared or enemy-avoiding
variant), cheapest remaining completion when nothing is profitable inside the horizon, leftover
paint next to own earning rails (town regions first, instability <= 1). On Indrill's 14,423
states it places on **90% of turns, 2.15 cells/turn** (leader-v1 71% / 1.64; Indrill 97% /
2.29); cell agreement 11.4%, target agreement 25.7% (leader-v1 11.8% / 25.5%; v08 11.8% /
26.5%). Against v08 (40 games, seeds 771000, `build/analysis/leader-v2-smoke-v08.json`) it wins
**8-32 (20%)**, leader-v1 15%; in those long mirror-like games it still places on only 61% of
turns because nothing remains to complete. A never-idle heuristic is not a search bot; the
reference stays a behavioural opponent. Its value was to show that the construction gap is
about *unused paint*, not about route choice.

## 4. Experiment fill-v1: `USE_ROLLOUT_FILL` (default off)

After the v08 search has chosen its cells and target, spend the leftover paint on free cells
outside the target region whose region is a town region or has instability
<= `ROLLOUT_FILL_MAX_INSTABILITY` (0), adjacent to an own track (new cells included); score
+4 on a proposed route, +3 next to an own *earning* track (else +1 next to any own track), +2 in
a town region, minus terrain cost. Nothing in the search, the routes, the evaluator or the target
choice changes; with the flag off the code path is not entered (`RolloutFillTest`: defaults off,
v08 leaves paint unused on a finished board, the filler spends it only where allowed, never in
the target region, and does not touch a full-paint opening). 40-game smoke vs v08 (seeds 772000):
25-15, placement 89.5% vs 50%, paint 2.62 vs 1.39 per turn, zero errors.

### Practical-v4 result: local PASS_1000, boss stage not started

`python tools/run_practical_v4.py --spec "current?USE_ROLLOUT_FILL=1" --name fill-v1 --seed
560000`, control v08, fresh pools 560000 / 660000 / 760000 (150 / 100 / 250 paired maps), every
prior benchmark map excluded, CG 486/36-ms search, two workers, zero bot errors in 1000 games:

| block | seeds | W-L-D | score | pooled | look |
|---|---|---|---|---|---|
| 1 | 560000..560149 | 176-124-0 | 58.67% | 58.67% / 300 | clears futility (>= 50%) |
| 2 | 660000..660099 | 109-91-0 | 54.50% | 57.00% / 500 | clears futility |
| 3 | 760000..760249 | 284-216-0 | 56.80% | **56.90% / 1000** | **PASS_1000** (>= 55%) |

Diagnostic map-grouped intervals 47.6-69.8%, 40.9-68.1%, 48.2-65.4%; mean score difference
+206 / +19 / +199. Behaviour: candidate places on 90-91% of turns (v08 53-56%), 2.65 vs 1.5
paint per turn; accepted disruption 63-65% (candidate, the usual mirror level) vs 78-80% for
v08, which now finds more regions worth inking because the candidate has more tracks on the
board; games 89-91 turns, early closures 91 / 71 / 167. Block 1 shows 12
slow in-process calls on each side with maxima 133 / 120 ms because a study export and the test
suite ran concurrently on the same machine during that block (blocks 2-3: 0 / 3 slow calls,
maxima 47-93 ms); it affects both sides symmetrically and is not a process deadline.

Verdict under v4: `directStage PASS_1000`, `strengthStatus NEEDS_BOSS_CACHED_40`. The 40-game
cached-boss stage (`tools/boss_cached.py`, arena submissions) is the user's call.

**Blocker before any boss game: the merged artifact does not fit.** `submit
-PsubmitVersion="current?USE_ROLLOUT_FILL=1" -PsubmitOut=build/cg-boss/artifacts/fill-v1`
stops at **100,646 / 100,000 characters** (the flag adds ~1.7k; `current` was already at
~98.9k because every rejected default-off rollout experiment still ships in it: RHEA ~3.5k,
network chords ~2.7k, spread ~2k, route dependencies, replan, second target, diverse replies,
quick-route variants, live-target, always-disrupt, track-value). The merger already strips
comments and indentation, so only code removal helps: deleting any one of RHEA / chords / spread
from `bot/current` (frozen v06-v08 copies are untouched) frees enough. `FileMergerTest` fails
on `current` builds until that decision is made, so the fill code stays uncommitted.

## Reproduction

```powershell
.\gradlew.bat cgCellStudy                                              # -> build/analysis/leader-study/cells.csv
.\gradlew.bat cgCellStudy "-Ppath=replays/cg/boss/current/<v08 build hash>" "-Pplayer=Boss 4" "-Pout=build/analysis/boss-cell-study"
python tools/diagnostics/cell_study.py build/analysis/leader-study/cells.csv --label Indrill --out docs/evidence/indrill-cell-study-v1.json
python tools/diagnostics/cell_study.py build/analysis/boss-cell-study/cells.csv --label "Boss 4" --out docs/evidence/boss4-cell-study-v08-40.json
.\gradlew.bat cgStudy "-Ppath=replays/cg-indrill" "-Pbot=leader-v2"
```
