# Bot versions

Gate: A vs previous >= 55% on >= 200 games (two sides on each map), map-grouped 95% CI excluding
50%, no bot errors. Process timing and an older opponent remain separate promotion checks.
`current` = `btk.bot.current.EvalBot`; frozen copies in `btk.bot.vNN`.

`BotFactory` loads fixed defaults for v01–v03 from `src/main/resources/btk/bot/versions/`, then
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
| v01 `GreedyBot` | 2026-09-13 | Dijkstra cheapest path per inactive pair; rank by `(free + own - opp) * turnsEarning / (turnsToFinish+1)`; spend 3 paint along the best path, leftover to next pair. Idles (`WAIT`) once every pair is connected. No DISRUPT. | vs v00: 66% [56-75], n=100, CG maps (Wood rules) | pending — first submission |
| v02 `EvalBot` | 2026-09-13 | Simulation-evaluated candidates: per pair, cheapest route (any tracks) + own route avoiding opponent tracks (steal); value = income delta after completion (mine − opp) × turns earning × instability risk / (turnsToFinish+1). Free DISRUPT chosen by simulating ink of each region (opp income lost − 1.2 × own loss, + not-yet-earning tracks) / disrupts needed. Routing surcharge for unstable regions. `NOISE` param to break self-play mirrors. | vs v01: 100% (300-0), +29k pts. Ablations (both sides NOISE=0.2, n=300): no DISRUPT 97.3%, no STEAL 87.5%, no instability avoidance 54.8% [49-60]. | pending |

| v03 `EvalBot` | 2026-09-13 | v02 + state value = income diff − W_VULN × worst single ink the opponent could do (my loss − their loss, / disrupts needed, only regions on active paths with my tracks). Third candidate type: backup route for an active pair avoiding the most threatened region. Closing rule: an ink that leaves no connectable pair is taken only when ahead, never when behind. `HORIZON` param (default 100). `NOISE=0.1` default (deterministic mirrors gave 0-0 games). Opponent model (`USE_OPP_MODEL`, collision surcharge) implemented but off: 47-51% in ablations. | vs v02: 58.5% [51.6-65.1], n=200. Ablations (n=300): no backup 60.2% [54.5-65.5] sig.; W_VULN=0 & no backup 52.7%; USE_CLOSE=0 48% (self-play games rarely close); HORIZON 20/30/50 → 52/53/43%. Process check: first turn ≤ 660 ms, later ≤ 38 ms. | **submission candidate** |

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

## What top Gold players do (`gradlew cgStudy -Pbot=current`, 107 replays, winner's moves)

- 97/107 games close early (no connectable pair), average ~50 turns. Disrupt on 92% of turns,
  first disrupt at turn 0-1 in 86% of games, targets mostly regions with opponent-only tracks.
- Place on 88% of turns, 2.15 cells/turn (v03: 69%, 1.66 — idles when nothing raises income now).
  29% of their cells go to regions shared with the opponent (hostage effect), 20% to town regions
  (uninkable), 51% adjacent to own tracks.
- v03 agrees with the winner's disrupt region 46% of the time, placement cells 13%.

## Known gaps (to become versions)

- Self-play does not reproduce the Gold meta (90-turn games vs 50): benches vs v02/v03 may not
  transfer. Needs a more aggressive reference opponent or evaluation against replay states.
- Idle turns: build something useful when no candidate raises the value (pre-build, extend lines).
- Pre-emptive disrupts of empty regions on the opponent's likely corridors; disrupt every turn.
- No lookahead: a 2-3 turn plan search over (placements, disrupt) with the engine.
- Arena timing shows 100-300 ms outliers under 13 threads (JIT/GC contention); not seen in
  `procCheck`. Keep checking `procCheck` after every change to the hot path.
