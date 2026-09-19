# live-target-v1 and always-disrupt-v1 (2026-09-19, both not promoted)

Two independent default-off sabotage experiments on the selected v08 policy, motivated by
[boss-winners-study-v1.md](boss-winners-study-v1.md). Control is frozen **v08**; policy is
**practical-300-1000-boss-cache-v3**. Both flags stay OFF, v08 stays selected, no artifact was
rebuilt for upload. Complete outcomes, parameters, map hashes and report hashes:
[evidence/live-target-v1.json](evidence/live-target-v1.json).

## Mechanisms

- `current?USE_ROLLOUT_LIVE_TARGET=1`. `RolloutBot` diffs the observed board against the previous
  observation and remembers the turn each track appeared. `chooseTarget` adds
  `ROLLOUT_LIVE_WEIGHT` (12, in income x turns) per opponent cell placed within the last
  `ROLLOUT_LIVE_TURNS` (2) observed turns to both the shortlist priority and the exact value, so a
  region where the opponent is building *now* outranks a stale unfinished segment of equal size.
  The same counts are used unchanged inside the rollout, for both players' simulated choosers.
  Hypothesis from the study: Gold players' hits land on the boss's live project 32% of the time
  (43% of their construction-class hits); ours 17% / 14% in losses.
- `current?ROLLOUT_ALWAYS_DISRUPT=1`. When no shortlisted region has positive value, spend the
  disruption point on the best shortlisted region that does not reduce our current income
  difference and does not hold more of our tracks than the opponent's; a losing one-hit closure is
  still refused. Hypothesis: Gold players disrupt on 99-100% of midgame turns, we on about 90%.

Both are pure target-selection changes: construction search, routes, horizon, evaluator, budgets
and the CGRunner margin are unchanged. Disabled current agrees with frozen v08 (V08PromotionTest
parity across seats, early/evolving boards and saved boss positions); the frozen v08 policy
section of the merged submission is pinned by hash. `RolloutLiveTargetTest` covers: identical
choice without observed placements, a live two-cell segment outranking a five-cell stale
segment, expiry after the window, the forced target's legality and the refused losing closure.
340 Java tests pass. `btk.core` is unchanged.

## Fixed stages (CG 486/36-ms search, two workers, fresh generated paired maps, all prior map
hashes from `build/bench`, `build/analysis` and `docs/evidence` excluded before play)

| experiment | stage | seeds | result | score | decision |
|---|---|---|---|---|---|
| live-target-v1 | screen 300 / 150 maps | 520000..520149 | 153-137-10 | 52.67% | NEEDS_FRESH_1000 |
| live-target-v1 | direct 1000 / 500 maps | 522000..522499 | 492-463-45 | 51.45% | **NOT_PASSED** (< 55%) |
| always-disrupt-v1 | screen 300 / 150 maps | 521000..521149 | 147-152-1 | 49.17% | **STOP_SCREEN_300** |

Zero bot errors in all 1600 games. Map-grouped diagnostic intervals: 41.6-63.8%, 45.4-57.5%,
38.1-60.3%. Mean score difference +252 / +126 / -47. In-process maxima 66/103, 56/48 and 49/43 ms
with at most one slow call per side; these include first moves and are not process deadlines.
No boss stage was started: the direct stage decides first and a local failure cannot be rescued.
No pooling, tuning, retries or discarded games.

## Reading the result

The self-play bench does not reproduce the situation the mechanism targets. Against the boss our
games last 53-57 turns when we win and the boss disrupts every turn; in these v08-vs-v08 games
both sides accept a disruption on only 64-67% of turns and games last 89-91 turns. Always-disrupt
raised the candidate's disruption rate to 79% without any strength gain, and the live-project
boost changed target choice without moving the self-play result away from 50%. The study's
finding is specific to the boss (it reveals its project through its placements and completes 70%
of projects against us); whether hitting its live project actually lowers that completion rate
was not tested here, because policy v3 requires the local direct stage first.

Suggested next step for the user's decision, not taken: allow a *boss-directed* screen order for
mechanisms whose evidence comes from boss replays, i.e. run the fixed 40-game cached-control
boss comparison before or instead of the self-play 1000 for such candidates, keeping the local
300 as a non-regression check. The candidate, its tests and its exact configuration are retained
for that purpose. Raw reports: `build/bench/live-target-v1/{screen,direct}.json`,
`build/bench/always-disrupt-v1/screen.json` (hashes in the evidence file).
