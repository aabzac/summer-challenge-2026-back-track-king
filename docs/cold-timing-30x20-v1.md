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

### Round 2 results (`docs/evidence/cold-timing-30x20-v1/round2/`, 258 cold games, 0 errors)

| vs boss-v2 | cold, 43 maps × 2 seats (one JVM per game) | hot, pool 980000 (300 games; D 68.7 %) | hot on the pool's 23 30×20 maps (D 76.1 %) |
|---|---|---|---|
| **D (fresh cold control)** | 65-21 = **75.6 %** | — | — |
| D6 = D + `ROLLOUT_DEPTH=6` | 70-16 = 81.4 %, paired +5.8 ± 11.8 | 64.3 % (−4.4) | 67.4 % |
| W4 = D + `ROLLOUT_WIDTH=4` | 70-16 = 81.4 %, paired +5.8 ± 12.7 | 63.7 % (−5.0) | 82.6 % |

Cold mean time per move: D 25.4 ms, D6 24.1, W4 24.1 (the per-comparison saving is simply spent
on more comparisons inside the same budget-bound turn). Isolated 50–131 ms turns on both sides
during this late-evening run (hot rows: 98–125 ms maxima for both arms and for boss-v2) — the
machine, not the candidates.

**The control moved more than the probes.** The fresh cold D is 75.6 % where round 1's cold D was
66.3 % on the same maps and seats: +9.3 ± 12.0 pp between two runs of the *same* configuration.
Pooled over both rounds (172 cold games) cold D is 70.9 % against hot D's 77.9 %: **paired
−7.0 ± 12.5 pp**. So:

- The timing fact stands (A: 34 % of later turns at the ceiling in a fresh process, up to 31 of the
  first 40). Its *strength* effect against the proxy on 30×20 is **not measurable at this sample
  size** — somewhere between nothing and about −15 pp; the round-1 reading of −11.6 was within
  that band, not a number.
- **D6 and W4 are parked.** Their cold gains (+5.8) are half the paired error and their hot rows
  sit 4–5 pp under D's control (inside the ±5.7 cell error, but on the wrong side); neither meets
  the predeclared bar for a full cold screen.
- The cold instrument at 86 games (±12 pp) resolves only large effects. A decisive cold series
  needs ≥ 300 paired games per arm (≈ 25 min per arm at the measured 15 cold games per minute),
  on 30×20 and 29×19 maps from a fresh pool, with the control in the same session.

### Where this leaves the 30×20 question

The arena's 3-8 on 30×20 (11 games) is consistent with any of: the small sample, a different
opponent mix on big maps, or a cold-JVM strength loss of a few pp that the proxy series cannot
resolve yet. Nothing in this file justifies a change to D. The two measurements that would settle
it are (1) the 80-game live boss series for D (v5 step c, which is also the promotion gate), and
(2) a ≥ 300-game cold paired series D vs boss-v2 on tall maps with a same-session hot control on
the same maps. Pools 980000/990000 (tall subsets) are consumed for cold work too; next free range
≥ 1000000.

## Round 3 — the decisive cold series ("холод-300", predeclared 2026-10-22 late, before the first game)

On the user's choice. Fresh range **1000000..1000599** (600 generated maps, first use of the
1000000 range); its tall subset is **159 maps: 86 of 30×20 and 73 of 29×19** → 318 games per arm
with both seats (`tools/boss_proxy/run_cold_tall3.sh`, user's machine, CG 486/36 ms).

1. **Hot reference**: D vs boss-v2 on all 600 maps × 2 seats in one JVM with 2 workers (1,200
   games; the tall games are read per map and seat, the full row doubles as D's all-map control
   for this range).
2. **Cold arms**, one JVM per game, both seats, on the 159 tall maps, interleaved per seed so all
   arms share machine conditions: **D** (control), **D6** = D + `ROLLOUT_DEPTH=6`, **W4** = D +
   `ROLLOUT_WIDTH=4`.

Reading, fixed now:
- **Primary**: paired cold D − hot D over the 318 tall games (95 % interval ≈ ±7 pp at this size).
  The cold-JVM strength effect is *established* if the interval excludes zero; it is *material*
  if the point estimate is at or beyond −5 pp. Reported for 30×20 and 29×19 separately as well.
- **Secondary**: paired cold D6 − cold D and cold W4 − cold D, same session, same maps and seats.
  A probe advances to the ordinary v5 route (hot non-inferiority vs D on a fresh pool, then live)
  only if its paired cold gain's interval excludes zero; its earlier hot rows (64.3 / 63.7 % vs
  D's 68.7 on pool 980000) are inside the cell error and stay the hot reference.
- Time per move and maxima of all arms are reported; isolated machine spikes (seen at 100–260 ms
  on both sides in earlier late-evening runs) are listed, not attributed.
- No extension, no re-run, no pooling with rounds 1–2 (different maps). Evidence
  `docs/evidence/cold-timing-30x20-v1/round3/`.

### Round 3 results

_(filled when the run finishes)_

