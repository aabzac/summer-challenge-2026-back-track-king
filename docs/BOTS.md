# Bot versions

Gate: A vs previous >= 55% on >= 200 games (two sides on each map), map-grouped 95% CI excluding
50%, no bot errors. Process timing and an older opponent remain separate promotion checks.
`current` = `btk.bot.current.EvalBot`; frozen copies in `btk.bot.vNN`.

`BotFactory` loads fixed defaults for v01–v04 from `src/main/resources/btk/bot/versions/`, then
applies explicit overrides. The snapshots preserve the effective defaults at commit `8364ad5`;
they do not retrospectively reconstruct the original defaults used by every historical bench.
When freezing a new version, add its parameter snapshot as well as its source and factory entry.
See `benchmarking.md` for the complete procedure.

The rows below are historical: their original benchmarks alternated sides across different maps
and used per-game Wilson intervals. They have not been relabelled as results of the new paired
benchmark. New reports use map-grouped Hoeffding bounds for the statistical promotion gate.

| version | date | change | bench vs prev | CG result |
|---|---|---|---|---|
| v00 `AutoplaceBot` | 2026-09-13 | `AUTOPLACE` towards the first inactive desired pair. Wood 2 exit. | — | not submitted |
| v01 `GreedyBot` | 2026-09-13 | Dijkstra cheapest path per inactive pair; rank by `(free + own - opp) * turnsEarning / (turnsToFinish+1)`; spend 3 paint along the best path, leftover to next pair. Idles (`WAIT`) once every pair is connected. No DISRUPT. | vs v00: 66% [56-75], n=100, CG maps (Wood rules) | not submitted separately |
| v02 `EvalBot` | 2026-09-13 | Simulation-evaluated candidates: per pair, cheapest route (any tracks) + own route avoiding opponent tracks (steal); value = income delta after completion (mine − opp) × turns earning × instability risk / (turnsToFinish+1). Free DISRUPT chosen by simulating ink of each region (opp income lost − 1.2 × own loss, + not-yet-earning tracks) / disrupts needed. Routing surcharge for unstable regions. `NOISE` param to break self-play mirrors. | vs v01: 100% (300-0), +29k pts. Ablations (both sides NOISE=0.2, n=300): no DISRUPT 97.3%, no STEAL 87.5%, no instability avoidance 54.8% [49-60]. | not submitted separately |

| v03 `EvalBot` | 2026-09-13 | v02 + state value = income diff − W_VULN × worst single ink the opponent could do (my loss − their loss, / disrupts needed, only regions on active paths with my tracks). Third candidate type: backup route for an active pair avoiding the most threatened region. Closing rule: an ink that leaves no connectable pair is taken only when ahead, never when behind. `HORIZON` param (default 100). `NOISE=0.1` default (deterministic mirrors gave 0-0 games). Opponent model (`USE_OPP_MODEL`, collision surcharge) implemented but off: 47-51% in ablations. | vs v02: 58.5% [51.6-65.1], n=200. Ablations (n=300): no backup 60.2% [54.5-65.5] sig.; W_VULN=0 & no backup 52.7%; USE_CLOSE=0 48% (self-play games rarely close); HORIZON 20/30/50 → 52/53/43%. Process check: first turn ≤ 660 ms, later ≤ 38 ms. | **Silver, around 350/645**; Gold not yet open; usually wins boss games |
| v04 `EvalBot` | 2026-09-14 | v03 + idle extension: when no complete route improves evaluation, spend remaining paint next to own rails/towns; prefer uninkable town regions and avoid non-town regions at instability ≥2. | vs v03: **64.7%**, W-L-D 194-106-0, n=300/150 paired maps, map-grouped 95% CI **[53.6%, 75.8%]**, avg score diff +405. Control vs v02: 54%, identical to v03 on the same 50 maps. Process check: 0/8 problem games, first turn ≤745 ms, later ≤43 ms. | Boss **7/7**; awaiting Silver arena result |

## Paired benchmark checkpoint (2026-09-13)

Infrastructure verification at strategy commit `8364ad5`, using the fixed parameter snapshots
and two worker threads. No new version or strategy promotion:

- `current` vs `v03`: 200 games on 100 generated maps, seeds 1000..1099; W-L-D **86-82-32**,
  A **51.0%**, map-grouped 95% CI **[37.4%, 64.6%]**. No bot errors; statistical gate not passed.
  Report: `build/bench/paired-current-v03.json` (local, gitignored).
- `current` vs `v02`: 40 games on 20 maps from `replays/cg`; W-L-D **22-14-4**, A **60.0%**,
  map-grouped 95% CI **[29.6%, 90.4%]**. No bot errors; smoke check only, gate not passed.
  Report: `build/bench/paired-current-v02-replays.json` (local, gitignored).

Full commands, timing limitations and validation results are recorded in `DEVLOG.md`.

## What top Silver players do (`gradlew cgStudy -Pbot=current`, 107 replays, winner's moves)

- 97/107 games close early (no connectable pair), average ~50 turns. Disrupt on 92% of turns,
  first disrupt at turn 0-1 in 86% of games, targets mostly regions with opponent-only tracks.
- Place on 88% of turns, 2.15 cells/turn (baseline current/v03 study: 73%, 1.76; v04 idle
  extension: 100%, 2.44 — it now overbuilds rather than idles).
  29% of their cells go to regions shared with the opponent (hostage effect), 20% to town regions
  (uninkable), 51% adjacent to own tracks.
- v04 agrees on 15.8% of winner placement cells; its 53% adjacency to own rails closely matches
  the winners' 51%. The reported 17% versus 31% income-raising rate is **among turns with
  placements**, not all turns. The hypothetical placement-only check ignores simultaneous
  sabotage and does not measure how the bot's own games develop.

These historical policy-study measurements are diagnostics, not arena outcomes. Counts such as
1.76 and 2.44 are **cells/turn**, not paint/turn. Legacy study counts only explicit PLACE_TRACKS
commands (not accepted AUTOPLACE expansion) and is not the player-specific loss-analysis tool.

## v04 arena diagnostic checkpoint (2026-09-14)

- Public API snapshot: `aabzac`, agent **6735801**, rank **477**, score **11.78**; placement
  completion was not independently established. Latest 50 completed games: **23 wins / 27 losses**,
  no server timeouts. This matchmaking-selected sample is not an independent strength benchmark.
- Height 14-17: **18/20 wins**; height 18: **4/10**; height 19-20: **1/20**. Opponents differ, so
  these are exploratory strata rather than a causal estimate of map size.
- Captured large-map states reproduce loss of the sabotage phase under tight search budgets;
  it runs after route search on the same timer. This also happens with idle extension disabled.
- Full findings, one-turn placement counterexamples and limits: `arena-analysis-2026-09-14.md`.
- No strategy change / no v05. Timing-aligned v04-v03 control on new seeds 4000..4149:
  **63.7%**, W-L-D **191-109-0**, 300 games, map-grouped 95% CI **[52.6%, 74.8%]**.
  The previous-version advantage persists under the smaller allowance; it does not establish
  broader arena strength. Equal 54% estimates against v02 from the old small control did not
  establish equivalence or prove non-regression.

## Default-off experiment: sabotage time reserve (2026-09-14)

No v05 promotion. `current?DISRUPT_RESERVE_MS=6` cuts route/idle search short on the same turn
clock, leaving sabotage the final 6 ms; `0` (default) retains v04 scheduling. Frozen versions
do not read this field. Evaluation formulas, total turn budget and CGRunner margin are unchanged.

- Screen vs v04: **54.3%**, W-L-D **162-136-2**, 300 games on 150 distinct downloaded CG maps,
  sides swapped, 2 threads, CG profile; map-grouped 95% CI **[43.2%, 65.4%]**, mean score diff
  **+636.5**, no bot errors. **Gate not passed**. Report: `build/bench/reserve6-v04-cg.json`.
- On two captured large-map t40 states, all 6 repeat probes now emit sabotage at 36 ms; both
  v04 and reserve=0 omit it in all 6. This verifies the local failure mechanism, not arena wins.
- Post-hoc height 19-20 subset: **57/92 wins (62.0%)**; smaller maps are mostly unchanged.
  This selected small subset is diagnostic, not an independent promotion benchmark.
- Experimental submission: 0/8 process problem games, including both sides of the two failure
  maps; first response <=663 ms, later <=37 ms. Not a measurement of CG-host timing.
- Initial decision: leave the flag **off**, without a 1000-game confirmation. The subsequent
  matched v03 check below supports further testing, but does not pass the direct-v04 gate.
  Partial-build safety and diverse-opponent validation remain open.

## Common-opponent follow-up: reserve=6 versus v03 (2026-09-14)

Both campaigns used new authentic generated seeds **5000..5149**, 150 identical distinct maps
(hashes verified), both sides, 300 games each, 2 threads, CG 486/36-ms search profile. Runs were
sequential: control then candidate; no strategy/default/threshold changes.

- v04 vs v03: **61.3%**, W-L-D **184-116-0**, map-grouped 95% CI **[50.2%, 72.4%]**,
  mean score diff **+380.8**. Report: `build/bench/v04-v03-holdout5000.json`.
- `current?DISRUPT_RESERVE_MS=6` vs v03: **69.3%**, W-L-D **208-92-0**, same-method CI
  **[58.2%, 80.4%]**, mean score diff **+813.3**. No bot errors in either campaign.
  Report: `build/bench/reserve6-v03-holdout5000.json`.
- Matched games: **33 added wins, 9 lost wins, 258 unchanged**; net **+8 percentage points**.
  Map-pair totals improved on 24 maps, worsened on 3, tied on 123. A diagnostic paired-map
  percentile bootstrap (20,000 samples, seed 20260914) estimates the difference against v03
  at **[+4.3, +11.7] pp**. This approximate interval is not the acceptance gate; it does not
  account for host/JIT/order effects or different arena opponents. Comparison report:
  `build/bench/reserve6-v03-holdout5000-comparison.json`.
- Decision: retain as a promising, default-off candidate, not a disproven improvement. The old
  direct-v04 result remains inconclusive. v03 is a related reference, not diverse arena coverage;
  beating it more often is not transitive proof of beating v04. No v05 or threshold change.

## Known gaps (to become versions)

- Self-play does not reproduce the top Silver meta (v04 still averages 84 turns vs about 50):
  benches against frozen versions may not
  transfer. Needs a more aggressive reference opponent or evaluation against replay states.
- v04 overbuilds locally (100% of turns vs 88% for winners); refine which extensions are useful
  using replay-state evaluation rather than maximizing paint usage itself.
- Pre-emptive disrupts of empty regions on the opponent's likely corridors; disrupt every turn.
- No lookahead: a 2-3 turn plan search over (placements, disrupt) with the engine.
- Arena timing shows 100-300 ms outliers under 13 threads (JIT/GC contention); not seen in
  `procCheck`. Keep checking `procCheck` after every change to the hot path.
