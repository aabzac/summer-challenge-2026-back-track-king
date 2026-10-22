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

## Part 2 (2026-10-22, predeclared before the first game) — coverage instead of depth

Part 1 showed that D's search finishes early and that more replies or depth neither spend the
slack nor help. Part 2 spends it on *coverage* of the route enumeration and of the portfolio, both
again plain `Params` values (no source, same artifact size):

| candidate | spec | what changes |
|---|---|---|
| **M128** | D + `ROLLOUT_MAX_ROUTES=128` | the cap on route queries per real turn rises from 2×64 to 2×128. `propose` runs pairs × up to 3 variants × 2 players from a rotating start; on a 30×20 map (~38 pairs ≈ 228 queries) the 128-query cap leaves ~40 % of the pairs unexamined each turn — the enumeration is no longer truncated. |
| **W8** | D + `ROLLOUT_WIDTH=8` | the per-player route portfolio grows from 6 to 8 slots: two more roots to search and two more candidate opponent replies to choose from (replies evaluated stay 2, depth 8). |

Design identical to part 1 (`tools/boss_proxy/run_dividend2_screen.sh`): fresh pool **seed 990000**
(first use of that range), 150 maps, 300 paired games per cell, 2 workers, CG timing; M128 / W8 vs D
head-to-head, then each and **D** vs boss-v2, neural-v6, v09 on the same maps, seeds and seats in the
same session. Reading rules 1–4 above apply unchanged: keep only at ≥ 50 % vs D with no opponent row
below D's control by more than the cell error; v5 step (a) continuation at ≥ D + 3 pp vs boss-v2;
a candidate that passes (1) but not (2) is parked; timing read from the same games (M128's extra
queries land in the `propose` phase that the fast cheapest path made cheap — the `max` turn time on
tall maps is the number to watch). Evidence `docs/evidence/stage2-dividend-v1/part2/`.

### Part 2 results (2026-10-22, `docs/evidence/stage2-dividend-v1/part2/`, 3,300 games, zero errors)

| opponent | D (control) | M128 = D + `ROLLOUT_MAX_ROUTES=128` | W8 = D + `ROLLOUT_WIDTH=8` |
|---|---|---|---|
| D head-to-head | — | **47.3 %** (142-158) | 52.0 % (156-144) |
| boss-v2 | **67.0 %** (201-99) | 65.7 % (197-103), paired −1.3 ± 6.8 pp | 65.8 % (197-102-1), paired −1.2 ± 7.1 |
| neural-v6 | 49.7 % (149-151) | 48.3 % (145-155), −1.3 ± 5.9 | 53.7 % (161-139), +4.0 ± 7.9 |
| v09 | 67.0 % (201-99) | 63.0 % (189-111), −4.0 ± 6.6 | 68.7 % (206-94), +1.7 ± 7.3 |

Tall-map split (h ≥ 19) head-to-head against D: M128 **41.2 %**, W8 56.2 %; against boss-v2: D 72.5 %,
M128 67.5 %, W8 67.5 %. Mean time per move: D 12.4–17.1 ms, M128 12.8–17.2, W8 13.6–18.1 — again
only 1–2 ms more. Several cells show isolated 100–260 ms turns on *both* sides (D-vs-boss2: D 173,
boss-v2 260; M128-vs-D: 201 / 89), clustered in time (19:31–19:52 UTC) — machine hiccups, not a
candidate property; in the cells without them all maxima are 38–79 ms.

### Reading against the predeclared rules

- **M128 is rejected**: 47.3 % head-to-head (rule 1 fails), below D's control on every opponent row
  and worst where it was meant to help — tall maps, 41.2 % vs D. Lifting the route-query cap makes
  `propose` examine every pair on big maps, but the extra queries are paid inside the same window
  and crowd out the root×reply search that actually decides the move.
- **W8 passes the formal reading** (52.0 % h2h, boss-v2 deficit inside the error) and **fails the
  step (a) gate**: 65.8 % against D's 67.0 % vs boss-v2 (needed ≥ 70.0 %). Its gains are against
  neural-v6 (+4.0) and v09 (+1.7), the two opponents the proxy study marked as *not* the target.
  Neither cheaper nor simpler → **parked.** D unchanged. No live step.

### Conclusion of the "spend the dividend" line

Four search-size parameters, each in its own predeclared same-map screen against D with fresh pools
(980000, 990000): replies 2→3, depth 8→10, route queries 128→256, portfolio 6→8. None beats D
against the boss-like proxy (64.7 / 65.8 / 65.7 / 65.8 % against D's 68.7 / 67.0 %); every one of
them loses on tall maps against it (D 72–76 % → 67–68 %). The compute dividend D earned with the
runtime flags does not convert through *more of the same search*: the search already completes,
and any extra per-turn work costs coverage exactly on the large maps where the window is tight. What
is left for the slack is (a) the Stage 3–4 valuation changes (never waste the disrupt point,
exposure-discounted own valuation, bypass), which need source and therefore the `prune-v1` step
first (artifact 99,683 / 100,000), or (b) keeping it as timing margin on CG hardware — the arena's
30×20 weakness (3-8) coexists with D's *best* local rows on tall maps (72–76 % vs boss-v2), which
points at cold-JVM timing on 30×20 (the known chooser cost in a fresh JVM) rather than at search
size; `btk.tools.ColdTurnTiming` on D's exact artifact is the cheap check before anything else.
Pools 980000 and 990000 are consumed; the next screen starts at ≥ 1000000.
