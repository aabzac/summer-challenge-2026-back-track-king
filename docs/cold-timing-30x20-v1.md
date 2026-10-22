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

## Results (2026-10-22 evening, `docs/evidence/cold-timing-30x20-v1/`)

**A. Exact artifact, 12 CG 30×20 maps, fresh process per game, strict (0 grace):** 0 timeouts,
0 overruns, 0 problems; 1,068 later turns, mean 22.7 ms, **34 % of all later turns at ≥ 36 ms**,
p99 38.3, max 45.5 ms (one turn); of the first 40 turns of each game 14–31 are at the ceiling
(median 24/40). The share rises from turn ~10 as the map develops, so it is compute pressure,
not JIT settling (CGRunner's 500 ms first-turn warm-up is included in this run).

**B/C. Cold paired games vs boss-v2, one JVM per game, the same 43 generated 30×20 maps and seats as
the hot bench** (172 cold games, 0 errors, 0 slow moves; cold D 24.9 ms/turn mean, boss-v2 29.5):

| vs boss-v2, 43 maps × 2 seats | hot bench (JIT warm) | cold (one JVM per game) | cold − hot, paired |
|---|---|---|---|
| **D** | 67-19 = **77.9 %** | 57-29 = **66.3 %** | **−11.6 ± 14.3 pp** (better/worse/equal 15/25/46) |
| M32 = D + `ROLLOUT_MAX_ROUTES=32` | 51-35 = 59.3 % | 58-28 = 67.4 % | +8.1 ± 14.9 |
| M32 − D | **−18.6 ± 13.2** | +1.2 ± 14.7 | |

M32's full hot rows vs boss-v2: 57.3 % on pool 980000 (D 68.7), 66.7 % on 990000 (D 67.0).
Per seat, cold D: 76.7 % as P0, 55.8 % as P1 (hot 74.4 / 81.4) — 43 games per cell, not read.

### Reading

1. **Diagnosis confirmed.** On 30×20 the arena turn is budget-bound through the construction
   phase; the hot bench's "12 ms mean, search completes early" is a JIT artefact. The window is
   never overrun (max 45.5 < 50 ms, 0 timeouts in 20 strict process games), which matches the
   arena's 0 failed turns: D does not fail turns on big maps, it decides them with a fraction of
   the comparisons the hot bench gives it.
2. **Strength effect: supported, not yet a number.** Cold D loses 11.6 pp against the proxy on the
   same maps and seats, at the edge of the 86-game error. The direction is the hypothesis; the size
   needs a larger same-session cold series (the tool runs ~15 cold games per minute here).
3. **M32 rejected.** Capping the route enumeration does not buy anything cold (+1.2 ± 14.7) and
   costs 11–19 pp hot: the enumeration is not what crowds out the search, or what it saves is not
   spent usefully. The "enumeration budget" mechanism is not confirmed.
4. **Consequence for method.** Any work aimed at 30×20 must be screened cold: the paired one-JVM
   series on the same maps and seats is the instrument (`run_cold_tall.sh` pattern), the hot bench
   only as the non-inferiority guard. The lever is cheaper work per comparison (the Stage 2
   algorithmic cuts of the plan) or fewer, better-chosen comparisons — not search size.

## Round 2 probes (predeclared before the first game, 2026-10-22 late)

Params-only candidates that trade rollout cost for root×reply coverage inside a budget-bound turn —
useless hot (the search already completes), possibly useful cold:

| candidate | spec | rationale |
|---|---|---|
| **D6** | D + `ROLLOUT_DEPTH=6` | each comparison simulates 6 turns instead of 8 (≈ −25 % per comparison) → more roots × replies evaluated before the window closes |
| **W4** | D + `ROLLOUT_WIDTH=4` | 4 roots instead of 6 → a complete pass over roots × replies fits more often; each root is judged on both replies rather than the first roots on one |

Same 43 maps × 2 seats cold vs boss-v2, interleaved with a fresh cold D arm (same-session control,
replaces the earlier cold D row for the paired reading); then hot rows D6 / W4 vs boss-v2 on pool
980000 (300 games, D control 68.7 % there). Reading: a probe is worth a proper cold screen only if
cold probe − cold D > 0 beyond the paired error **and** its hot row is within the cell error of D's.
