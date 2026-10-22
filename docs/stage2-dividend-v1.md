# Stage 2 — spending the compute dividend (`stage2-dividend-v1`, 2026-10-22)

## Why

D (`docs/stage2-compute-v1.md`) finishes its ordinary search early: mean 11.8 ms per move against
C's 18.6 inside the same 36 ms window, ×2 simulated turns per real turn. The plan's next step is to
spend that slack on *more search* without touching what is valued: two separate same-map screens,
each one parameter of the rollout, each read against D on identical maps, seeds and seats.

| candidate | spec | what changes |
|---|---|---|
| **R3** | D + `ROLLOUT_REPLIES=3` | opponent replies evaluated per root inside the rollout: 2 → 3 (width stays 6) |
| **D10** | D + `ROLLOUT_DEPTH=10` | rollout depth in simulated turns: 8 → 10 |

D = `current?NOISE=0.1,USE_BOSS_LITE=true,USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true,USE_BOSS_LITE_ROUTES=true,USE_INCREMENTAL_INK_REFRESH=true,USE_FAST_CHEAPEST_PATH=true,USE_BATCHED_REFRESH=true`
(the arena artifact `build/submit-stage2-d`, 99,683 chars). Neither candidate adds source: both are
`Params` values, so a promoted candidate ships inside the same artifact size.

## Predeclared design (`tools/boss_proxy/run_dividend_screen.sh`)

- Fresh pool: base seed **980000** (first use of the 980000 range; 970000/960000 carry the
  v2 → B → C → D chain), 150 generated maps, 300 paired games per cell (both seats), 2 workers,
  CG timing profile (486 ms first turn / 36 ms search), user's Windows machine, Java 17.0.12.
- Cells, in run order: R3 vs D, D10 vs D; then R3 / D10 / **D** vs boss-v2
  (`current?USE_BOSS_SABOTAGE=1`), vs neural-v6, vs v09 — the D rows are the same-map controls,
  run in the same session with the same seeds and seats.
- Reading, fixed before the first game (same rule as D's own screen):
  1. keep a candidate only if it is **≥ 50 % against D head-to-head** and **no opponent row is
     below D's control row by more than the cell error** (±5.7 pp at 300 games);
  2. policy v5 step (a) continuation requires **candidate ≥ D control + 3 pp vs boss-v2**;
  3. a candidate that passes (1) but not (2) is "kept as neutral" only if it is cheaper or
     simpler — neither is, so such a candidate is parked, not promoted;
  4. time per move and `slow` counts are reported for both arms; a candidate that raises the
     candidate's `max` turn time above D's band on the same maps is flagged for the cold-JVM
     timing step before any further use, whatever its win rate.
- Draws count half. The map-grouped Hoeffding interval in the JSON is a diagnostic. The paired
  per-map delta against the control (`tools/boss_proxy/summarize_screen.py`) is the number to
  read for the opponent rows: it removes the map draw from the comparison.
- No extensions, no re-runs, no pooling with the 970000 rows. Evidence:
  `docs/evidence/stage2-dividend-v1/` (the 11 reports with traces, `screen.log`).

## Results (2026-10-22, `docs/evidence/stage2-dividend-v1/`, 3,300 games, zero errors)

| opponent | D (control) | R3 = D + `ROLLOUT_REPLIES=3` | D10 = D + `ROLLOUT_DEPTH=10` |
|---|---|---|---|
| D head-to-head | — | 51.7 % (155-145) | 53.3 % (160-140) |
| boss-v2 | **68.7 %** (206-94) | 64.7 % (194-106), paired **−4.0 ± 7.6 pp** | 65.8 % (197-102-1), paired **−2.8 ± 6.7** |
| neural-v6 | 51.7 % (155-145) | 53.7 % (161-139), +2.0 ± 7.2 | 54.0 % (162-138), +2.3 ± 7.1 |
| v09 | 63.3 % (190-110) | 67.0 % (201-99), +3.7 ± 7.5 | 63.0 % (189-111), −0.3 ± 6.9 |

Paired deltas are candidate minus control on identical maps, seeds and seats (150 maps each,
`summarize_screen.py`). Time per move (mean / max, ms): D 12.3–16.1 / 40–47; R3 14.2–17.8 / 40–47 plus
one 147 ms outlier in the v09 cell (v09 itself 134 ms there); D10 12.8–18.1 / 38–49. `slow` counts
0 everywhere except that outlier. Tall-map split (h ≥ 19) against boss-v2: D 76.3 %, R3 68.4 %,
D10 68.4 %; low maps 66.1 / 63.4 / 65.0 %.

### Reading against the predeclared rules

1. Both candidates are above 50 % head-to-head and no opponent row is below D's by more than the
   cell error → the formal "keep" condition holds for both, but the head-to-head margins
   (+1.7 / +3.3 pp) are inside ±5.7 pp: no demonstrated gain.
2. **Step (a) continuation fails for both**: R3 64.7 % and D10 65.8 % against D's 68.7 % vs boss-v2
   (needed ≥ 71.7 %). Paired deltas are negative on the only opponent that matters for the gate.
3. Neither is cheaper or simpler → **both parked; D unchanged.** No live step.
4. Timing: no structural change (means +2 ms, maxima in D's band); the 147 ms outlier is shared with
   the opponent in the same cell and is not attributed to the candidate.

### Why the dividend does not convert

D's mean move time barely rises when the rollout is given more replies or more depth: the search
still finishes early on most turns, so the extra work is done almost for free — and it rarely
changes the chosen action (h2h within noise). Where it does cost, large maps against the boss-like
opponent, the deeper/wider rollout crowds out root coverage and both candidates lose 8 pp on
h ≥ 19 — the opposite of what the arena's 30×20 weakness (3-8 in D's first 50 games) needs. The
slack must be spent on *different* work, not more of the same: more routes per root
(`ROLLOUT_MAX_ROUTES`, 64), a second root pass while time remains, or the Stage 3–4 valuation
changes (exposure-discounted own valuation, bypass). The 980000 pool is consumed; the next screen
starts at ≥ 990000.
