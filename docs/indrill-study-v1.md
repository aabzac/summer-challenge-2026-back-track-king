# How Indrill (Legend #1, Python3) plays, and what v08 does differently (2026-09-20)

Descriptive replay study of the current arena leader, plus one prospective experiment derived
from it. Nothing in the study itself changes a strategy default, frozen version or submission;
the experiment (`track-value-v1`, below) is a default-off flag screened under practical-v3.

## Snapshot and corpus

- Leaderboard 2026-09-20 (`build/cg-snapshots/leaderboard-2026-09-20.json`, gitignored): Indrill
  rank **1**, rating **36.34**, Python3, agent 6752275, Legend (18 players). #2 Pduhard- (C++)
  30.43. Top 30: 19 C++, 6 Rust, 4 Python3, 1 C.
- Corpus: all **282** completed battles of that agent (`replays/cg-indrill/`, gitignored),
  fetched with `python tools/diagnostics/indrill_study.py fetch`. Record **265-17** (94%):
  sZoom 70-4, RamzaBeoulve 61-5, Waffle3z 39-4, JOiy 35-0, Saelyos 16-1, delineate 12-1,
  Sohayb 11-0, _H3R0_ 9-1, Pduhard- 5-1, others 7-0. P0 in 152 games (140 wins).
- `gradlew cgValidate -Ppath=replays/cg-indrill`: **282/282 reproduced exactly** by `btk.core`.
  Every number below comes from the core replay (Java tool) or from viewer events cross-checked
  against recorded scoring on every turn (Python tool).

## 1. It is a fast heuristic, not a search

The referee sums `Player.getLastExectionTimeMs()` per player into `metadata.executionTimeMs`.
Indrill: median **10 ms per whole game** (mean 111, max 829), i.e. **0.2 ms per turn** median,
1.9 mean, 13 max. Opponents: median 1002 ms per game, ~20 ms/turn (RamzaBeoulve 37, sZoom 22,
Waffle3z 2, JOiy 8). Indrill never issues `AUTOPLACE`, `WAIT` or `MESSAGE`; every turn is
`PLACE_TRACKS`+`DISRUPT x y` (cell syntax, 13,947 times). The 50-ms Python budget is irrelevant:
the strength comes from the decision rules, which is why they can be read off 14,423 decisions.

## 2. Where the games are decided

Mean of all 282 games (Indrill / opponent):

| turn | income | own-majority connections | tracks on board | tracks on active paths |
|---|---|---|---|---|
| 10 | 13.3 / **14.2** | 1.52 / **1.69** | 19.7 / 19.5 | 5.5 / 5.8 |
| 20 | **56.1** / 41.1 | **4.41** / 2.42 | 34.8 / 35.0 | **15.7** / 12.7 |
| 30 | **63.0** / 42.6 | **4.48** / 2.02 | 44.9 / 47.9 | **17.8** / 13.2 |

Indrill is slightly *behind* at turn 10 and far ahead by turn 20 with the **same number of
tracks on the board**. The opponent's connections stop growing (2.4 -> 2.0) while Indrill's keep
growing; the difference is what gets destroyed, not what gets built. Games close early
(median 47 turns; 273/282 closed before turn 100, Indrill ahead at the closing ink in all 256
closed wins). Score at turn 30: 1035 vs 781.

Both sides lose about 45% of every track they place (Indrill 46%, opponents 41%); 62% of
Indrill's lost tracks were on active paths at the time. This meta is extremely destructive and
the winner is the side whose *investments* survive to earn.

## 3. Sabotage: every turn, on construction, until it inks

- **100.0% of turns** carry an accepted DISRUPT, from turn 1 in 282/282 games. Hits/turn 1.00.
- **93.6%** of Indrill's 14,423 hits land in a region that is eventually inked (opponents: 93.2%).
  Of 4,016 regions Indrill inked, 2,706 took all four hits from Indrill alone. Only 6.4% of hits
  are "wasted" on never-inked regions. Hits are not deterrence; they are a four-turn commitment.
- Hit class (region content at the moment of the hit; Indrill / opponents):
  empty **13.7% / 9.9%**, opponent construction (tracks not yet on an active path)
  **48.1% / 35.4%**, opponent active path **35.3% / 52.1%**, own-only 2.8 / 2.7.
- The class stays constant along the four hits for Indrill but not for its opponents.
  Construction-class hits by instability before the hit (0/1/2/3): Indrill **1946 / 1991 / 1736 /
  1270**, opponents 1901 / 1488 / 931 / 630. Active-class: Indrill 594 / 1074 / 1490 / 1936,
  opponents 985 / 1612 / 2181 / 2507. Opponents start on a construction region and the project
  completes before the ink (it turns "active" and earns for a few turns); Indrill's targets are
  inked while still under construction, so the opponent's paint is wasted before it earns.
- Turn 1-5: 53% of hits are empty regions (pre-emption); 95% of turn-0 targets lie on some
  cheapest completion route and 92% are adjacent to a town region, but the target is *not* the
  region with the most route traffic (rank 1 in only 10%). The exact opening rule was not
  identified.
- Only 31% of hits are in regions holding own tracks (opponents 40%); 0.78 own tracks at risk
  per hit vs 0.92.

A conditional-logit fit of the chosen region against the 25.6 candidate regions per turn
(features from the core: exact income deltas of inking, track counts, instability, recent
opponent placements, route traffic; 80/20 split, test top-1 **38%** vs 4% uniform; plain
gradient ascent, unconverged, so magnitudes are rough) gives, relative to one point of
opponent income lost per turn (coefficient 0.188):

| feature | weight | in opponent-income points |
|---|---|---|
| own income lost | -0.086 | -0.46 (own loss weighted half) |
| opponent track not yet earning | +0.162 | **+0.86 per track** |
| opponent track on an active path (on top of its income) | +0.341 | +1.8 |
| own track in the region | -0.234 | -1.25 |
| instability level already reached | +0.745 | +4.0 per level (follow-through) |
| opponent placed there last turn / two turns ago | +0.310 / +0.232 | +1.6 / +1.2 per cell |
| opponent own-only route through the region | +0.095 | +0.5 |
| same target as previous turn | +0.692 | +3.7 |

In `RolloutBot.chooseTarget` (v08) an unfinished opponent track is worth `ROLLOUT_TRACK_WEIGHT
= 0.4` against `(income change) x (8 - needed + 1)` = 5..8 per income point, i.e. about **0.05
income points per track, 17x less than Indrill**; an own unfinished track costs the same 0.4.

## 4. Construction: never idle, half of it off the cheapest routes

- Builds on **97.0%** of turns, **2.91/3 paint** (full paint on 97% of turns), 2.29 cells/turn;
  100.9 cells per game, paint/cell 1.30 (mountains 9.5%, rivers 11.1%; opponents 6.3% / 9.0%).
- Opening (turns 1-3): 2.96 cells/turn, **68% in protected town regions**, 97% of turns serve at
  least two desired pairs (the pattern behind v08's protected opening; Indrill touches 4.6 towns
  in its first nine cells).
- **48.7%** of all cells are on no cheapest completion route of any inactive pair (any-track or
  enemy-avoiding variant), rising from 12% (turns 1-3) to 49% (21-30), 63% (31-45) and 89%
  (46+). Those off-route turns (38% of build turns) place 2.13 cells/turn, 60% adjacent to own
  rails, **54% in regions where the opponent has tracks**, 18% in regions holding opponent
  active-path tracks, 16% protected; they raise own income immediately in only 15% of cases.
  Indrill spends its whole paint every turn on network work (reroutes, hub cells, hostage and
  bypass cells) whose value is not visible as an immediate cheapest completion. Its routing cost
  model is therefore not pure paint cost; this study did not identify it.
- 15% of build turns complete a pair with own placements alone; immediate own income gain per
  build turn 7.4 (opponents 5.1).

## 5. Weaknesses visible in its 17 losses

All 17 losses are early closures with Indrill behind at the last ink; **in 10 of them Indrill's
own DISRUPT delivered the closing ink** (e.g. 905008370 vs Pduhard-, 638-646). Its valuation has
no "do not close the map while behind" rule; v08 already refuses a losing one-hit closure.
Losses are short (mean 41 turns) and Indrill's own-majority connections never exceed 2 in them.

## 6. v08 on Indrill's states (`gradlew cgStudy -Ppath=replays/cg-indrill -Pbot=current`)

Over the 14,423 Indrill states: v08 matches **11.8%** of Indrill's placement cells and chooses
the same DISRUPT region in **3,819 / 14,412 (26.5%)**. v08 disrupts on **78%** of those states
(Indrill 99.9%) and places on **77%** of them, 1.81 cells/turn (Indrill 97%, 2.29). Placement
mix v08 / Indrill: town region 27 / 20%, region shared with the opponent 25 / 31%, adjacent to
own rails 39 / 53%, turns raising own income 28 / 25%.

## 7. What to copy, in order of evidence

1. **Sabotage valuation** (largest, best-quantified gap): value unfinished opponent tracks near
   income level and penalize own tracks likewise, so the opponent's project is inked before it
   earns, and a target always exists while the opponent has tracks outside town regions. This
   is `track-value-v1` below.
2. **Never idle**: 1.1 paint/turn of v08's budget is unused on Indrill's states; Indrill's
   off-route cells are mostly adjacent to own rails in regions shared with the opponent. A safe
   filler rule needs its routing model first (open).
3. **Turn-0 pre-emptive hit** on a town-adjacent corridor region; the rule is not identified and
   the corridor fallback prototype was removed for artifact size (the merged Player is at
   98,886 / 100,000 characters with the valuation flag alone).
4. Do not copy: closing the map while behind (its losses), hitting regions with own tracks.

## Experiment track-value-v1 (default-off, control frozen v08, practical-v3)

`USE_ROLLOUT_TRACK_VALUE=1` replaces `trackBalance x ROLLOUT_TRACK_WEIGHT` in both the shortlist
priority and the exact target value with `oppTracks x ROLLOUT_OPP_TRACK_VALUE (5.0) - ownTracks x
ROLLOUT_OWN_TRACK_VALUE (6.0)`, in the same income-times-turns units; 5-6 units is about one
income point in that formula, i.e. Indrill's 0.86 / 1.25 ratios rounded. Nothing else changes:
route search, evaluator, horizon, budgets, closure refusal, CGRunner margin. With the flag off
the expression is the v08 one (parity tests unchanged). Two independent variants, because the
same chooser also models the simulated opponent inside the rollout and a harsher opponent
model can suppress our own construction (the live-target caveat):

- `both`: `current?USE_ROLLOUT_TRACK_VALUE=1`, pool seeds 530000..530149.
- `self`: `...,ROLLOUT_TRACK_VALUE_SELF_ONLY=1` (our real/simulated chooser only; the opponent
  model keeps the v08 term), pool seeds 531000..531149.

Fixed screens: 300 games / 150 fresh paired maps each, CG 486/36-ms search, two workers, all
10,455 prior benchmark map identities excluded before play, reports write-once under
`build/bench/track-value-v1/`. Direct 1000 pools 532000 / 533000 are registered for a
NEEDS_DIRECT_1000 outcome; the 40-game cached-boss stage follows the v3 rule. Runner:
`python tools/run_track_value_experiment.py screen both|self`. Tests: `RolloutTrackValueTest`
(construction region outranks a one-income active region only with the flag; own tracks flip it
back; SELF_ONLY leaves the opponent model unchanged; legal actions; parameter validation).
343 Java tests pass; `btk.core` unchanged.

### Results (2026-09-20, zero bot errors, 1600 games; [evidence](evidence/track-value-v1.json))

| variant | stage | seeds | W-L-D | score | decision |
|---|---|---|---|---|---|
| both | screen 300 / 150 maps | 530000..530149 | 144-147-9 | 49.50% | **STOP_SCREEN_300** |
| self | screen 300 / 150 maps | 531000..531149 | 158-133-9 | 54.17% | NEEDS_DIRECT_1000 |
| self | direct 1000 / 500 maps | 533000..533499 | 494-477-29 | 50.85% | **NOT_PASSED** (< 55%) |

Diagnostic map-grouped intervals 38.4-60.6%, 43.1-65.3%, 44.8-56.9%; mean score difference
+22 / +241 / +58; in-process maxima 73/62, 58/50, 107/100 ms with at most four slow calls per
side (first moves included). No boss stage: a local direct failure cannot be rescued under v3.
Both flags stay OFF; v08 stays selected; nothing was rebuilt in `build/submit` or uploaded.

Why it did not move the self-play result: the candidate's accepted disruption rate stayed at
64-66% (control 64-67%). After the shared protected opening both v08 copies build along the same
cheapest routes, so the opponent's tracks are either in town regions or in regions where we hold
at least as many tracks, and 5 vs 6 per track leaves those with no positive value. The bench
games last 92 turns with placement on 56% of turns; Indrill's arena games last 47 turns with 97%
placement and 100% disruption. The valuation was calibrated on a meta the local mirror bench does
not produce, which is the same structural reason live-target-v1 and always-disrupt-v1 failed.

Suggested next steps (not started): a leader-like local reference opponent built from this study
(full paint every turn, the fitted target valuation) in the opponent panel; a user-authorized
40-game cached-boss diagnostic of the `self` variant as was done for live-target; and the
never-idle construction gap once Indrill's routing model is identified.

### Follow-up: leader-v1 reference and the coverage check (2026-09-20)

`leader-v1` (`btk.bot.reference.LeaderBot`, frozen constants from the fitted valuation above) was
added as a local-only opponent and the three retained sabotage flags were re-run against it on
one matched panel (`tools/run_leader_coverage.py`, 4 x 300 games, seeds 540000..540149;
[evidence](evidence/leader-coverage-v1.json)). v08 82.33%, track-value self 83.33%, live-target
82.33%, always-disrupt 83.00%; per-map deltas within one SE of zero, construction unchanged. The
reference reproduces the sabotage signature (99.9% accepted disruption) but places on only 41.5%
of turns (1.18/3 paint), so games still last 82-84 turns: the never-idle construction of section
4 remains the unreproduced half of the meta, and the flags do not gain from a relentless
disruptor alone. Details in DEVLOG.

## Reproduction

```powershell
python tools/diagnostics/indrill_study.py fetch                       # snapshot + 282 canonical replays
python tools/diagnostics/indrill_study.py study --out docs/evidence/indrill-study-v1.json
.\gradlew.bat cgValidate "-Ppath=replays/cg-indrill"
.\gradlew.bat cgLeaderStudy                                           # -> build/analysis/leader-study/*.csv
.\gradlew.bat cgStudy "-Ppath=replays/cg-indrill" "-Pbot=current"
```

`cgLeaderStudy` (`btk.tools.CgLeaderStudy`) replays every game through the core and writes
`sabotage.csv` (one row per candidate region per turn: exact ink deltas, track counts, route
traffic, chosen flag), `build.csv` (placed cells against cheapest completion routes),
`hits.csv` (every hit with the eventual ink turn) and `games.csv`. The rule/logit evaluations
above were run on those tables; the corpus itself is gitignored and refetchable.
