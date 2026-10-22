# Cold-JVM timing on 30×20 maps (`cold-timing-30x20-v1`, 2026-10-22)

## Question

D's first 50 arena games are 3-8 on 30×20 while D's *best* local rows against the boss-like proxy
are on tall maps (72–76 %, pools 980000/990000). Every "spend the dividend" candidate lost on tall
maps too. Hypothesis: on CG (one fresh JVM per game) D is budget-bound over the whole construction
phase on big maps, so the hot local bench — mean 12 ms per move, "search completes early" — does
not describe the arena turn at all, and strength on 30×20 is decided by what the search manages
inside a cold 36 ms window.

## What the existing strict process check already says

`build/submit-stage2-d/process-check.json` (the user's strict `procCheck` of D's exact artifact,
2026-10-21: 8 games, one process per game, 0 timeouts, 0 grace) carries per-turn response times.
Share of later turns at ≥ 36 ms, by 10-turn window:

| map | turns 1–10 | 11–20 | 21–30 | 31–40 | 41–50 | 51–60 | later |
|---|---|---|---|---|---|---|---|
| 30×20 (game 3) | 5/10 | **10/10** | **10/10** | **10/10** | 8/10 | 2/10 | 0 |
| 29×19 (game 6) | 7/10 | **10/10** | **10/10** | **10/10** | 1/10 | 0 | 0 |
| 27×18 (game 2) | 4/10 | 8/10 | 7/10 | 5/10 | 0/5 | — | — |
| 27×18 (game 1) | 6/10 | 4/10 | 2/10 | 0 | 0 | 0 | 0 |
| 24×16, 23×15, 21×14 | 0–2 per window, otherwise 0 | | | | | | |

Maxima 37–38 ms everywhere (the 36 ms search budget plus output), no overruns. So in a fresh JVM
the exact artifact uses the *entire* window on every turn of the construction phase of a 30×20 or
29×19 map (turns ~10–45), and frees up only when the map is full; the hot bench's 12 ms mean is a
JIT artefact of 300 games in one process. The ceiling does not fade after a few turns, so it is
not a warm-up tail: it is compute pressure.

## Predeclared measurements (`tools/boss_proxy/run_cold_tall.sh`, user's machine, Java 17)

A. **Strict process check of the exact D artifact on 12 CG 30×20 maps** (`replays/cg-30x20/`,
   `ProcessCheck`, opponent v09, grace 0): share of later turns ≥ 36 ms, p95/max, timeouts.
B. **Cold paired games D vs boss-v2** on the 43 generated 30×20 maps of the two screened pools,
   both seats, **one JVM per game** (`ColdTurnTiming`, CG 486/36 ms): 86 games. Read against the
   *hot* results of the same maps and seats in `D-vs-boss2.json` of both pools (hot D: 67-19 on
   these maps, 77.9 %). The paired per-map difference hot − cold is the effect of the cold JVM on
   strength; a drop beyond the 86-game error (±10 pp) supports the hypothesis, a drop within it
   rejects "cold timing explains 3-8".
C. **M32 = D + `ROLLOUT_MAX_ROUTES=32`**, the cheapest Params-only probe of the mechanism: the
   route enumeration (`propose`, up to 128 path queries before the root×reply search, stopped only
   when half the window is gone) is capped at 64 queries, so a budget-bound turn keeps more of its
   window for the search that decides the move. Same 86 cold games, interleaved with B; plus M32's
   hot rows vs boss-v2 on both full pools (300 games each) so the hot/cold × D/M32 square is
   complete. Reading: M32 is worth a proper screen only if **cold M32 > cold D** on these maps by
   more than the paired error *and* hot M32 is not below hot D (68.7 / 67.0 %) by more than the
   cell error; otherwise the mechanism needs a source change (enumeration budget as a fraction of
   the window, map-size-dependent caps) after `prune-v1`.

No promotion decision follows from this file; it is a diagnosis plus one probe.

## Results

_(filled when the run finishes)_
