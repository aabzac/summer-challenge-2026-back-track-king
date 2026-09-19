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

## Boss stage (user-authorized diagnostic after the local failure)

Policy v3 permits the 40-game cached-control comparison to run concurrently with the local
1000 once the screen exceeds 52%; it was launched afterwards instead, on the user's request,
knowing that the completed local failure already blocks promotion. Prerequisites first:
isolated artifact `build/cg-boss/artifacts/live-target-v1` (97,895 characters, source SHA-256
`53b53ac2…`, build `f514e6d7…`), strict process check **0/8 problem games** vs v08 on boss maps
(first max 680.6 ms, later max 41.3 ms, zero grace; report SHA-256 `3e4408d6…`). Control is the
exact measured v08 build `c6b4deae…` from the opening series (its 40 games, **13 W / 27 L**),
pinned by `tools/boss_cached.py plan --control-arm candidate`; plan ID `ad668655…`.

Forty sequential candidate games on the same 40 maps, seeds and seats, 2026-09-19 18:22-18:45
UTC, no failed or uncertain requests: **8 W / 32 L / 0 D = 20%** against the cached **13/40**,
**NOT_PASSED**. Per map/seat: candidate-only wins 5, control-only wins 10, both 3, neither 22.
All 40 canonical replays reproduce exactly through `btk.core`
(`replays/cg/boss/current/f514e6d7…/`). The boss's own rating in these games was 30.99-31.19,
up from 29.69 in the morning snapshots. Full report: [evidence/live-target-v1-boss.json](evidence/live-target-v1-boss.json).

So the mechanism does not help against the boss either, at least at weight 12 / window 2 and
with the boost applied inside the rollout for both players. One caveat this run cannot
separate: the same counts also make the *simulated boss* target our live project, which makes
our own fresh construction look more dangerous in the rollout and can suppress it; the
observed drop (8 vs 13) may come from that construction side effect rather than from our
target choice. Testing the boost for our real choice only would be a new experiment.

## Reading the local result

The self-play bench does not reproduce the situation the mechanism targets. Against the boss our
games last 53-57 turns when we win and the boss disrupts every turn; in these v08-vs-v08 games
both sides accept a disruption on only 64-67% of turns and games last 89-91 turns. Always-disrupt
raised the candidate's disruption rate to 79% without any strength gain, and the live-project
boost changed target choice without moving the self-play result away from 50%. The study's
finding is specific to the boss (it reveals its project through its placements and completes 70%
of projects against us); whether hitting its live project actually lowers that completion rate
was not tested here, because policy v3 requires the local direct stage first.

The user then authorized the boss stage above as a diagnostic; it failed as well, so the
boss-directed screen order question is moot for this candidate. The candidate, its tests and
its exact configuration are retained OFF. Raw reports: `build/bench/live-target-v1/{screen,direct}.json`,
`build/bench/always-disrupt-v1/screen.json`, `build/cg-boss/live-target-v1-boss-40/` (hashes in
the evidence files).
