# route-order-v1 — v12 leaves routes half-built (2026-10-27, construction line, step 2)

The user's "Порядок" after `route-choice-v1` (the stronger players pick the same routes among our candidates): does the
order in which the cells of multi-turn routes are placed explain why the opponents' tracks earn sooner with the same
completions? Data: `cgCellStudy` over the 147 v12 arena games (`build/analysis/arena-v12b/cells.csv`), the route-choice
exports of `route-choice-v1`; outputs in `docs/evidence/route-order-v1/`.

A **completion event** = a player's cells that first earn on the same turn (one route finished, possibly switching on
several pairs); its span = turns from its first placed cell to that first income; the minimum = ⌈paint / 3⌉.

| completion events with first income in turns 10–25 | losses: v12 / opponent | wins: v12 / opponent | lost to Ando: v12 / Ando |
|---|---|---|---|
| events per game | 6.7 / 8.4 | 7.0 / 6.9 | 6.4 / 8.5 |
| cells / paint per event | 4.9 · 5.8 / 4.2 · 5.0 | 5.1 · 6.0 / 4.4 · 5.3 | 4.5 · 5.5 / 4.2 · 5.1 |
| turns from the first cell to income (median) vs the minimum | 6.65 (4) vs 2.30 / 3.22 (2) vs 1.99 | 6.57 (5) vs 2.35 / 3.98 (2) vs 2.08 | 6.36 (3) / 2.38 (1.5) |
| **excess over the minimum** | **+4.35 / +1.23** | **+4.21 / +1.90** | **+4.13 / +0.37** |
| events with excess ≥ 2 turns | 52 % / 27 % | 53 % / 34 % | 49 % / 17 % |
| turns from half the event's paint to income (median) | 2.92 (2) / 1.49 (1) | 2.92 (2) / 2.02 (1) | |
| cells placed ≥ 4 turns before the income | **30 % / 13 %** | 30 % / 20 % | |
| events whose first cell is ≥ 3 turns before the second | 26 % / 12 % | 23 % / 12 % | |
| excess of the final push only (cells placed within ⌈paint/3⌉ + 2 turns of the income) | +0.09 / −0.28 | +0.07 / −0.13 | |
| early cells (≥ 4 turns before income) that were an own-route completion at placement | 82 % / 65 % | 80 % / 64 % | |
| placement turns whose earning cells feed > 1 completion | 18 % / 28 % | 20 % / 23 % | |

Route continuation (`route-choice-v1` labels, placement turns 8–26): after starting a route that needs more than one
turn of paint, the next turn continues the same pair **24 % (losses) / 27 % (wins) for v12 against 32 % / 30 %** for the
opponents.

## Reading

1. **v12 leaves routes half-built.** Its completions take four turns longer than their paint requires (the opponents'
   one to two, Ando's under half a turn when he beats us); 30 % of a completion's cells were placed four or more turns
   before it paid, and they were placed as an own-route completion — a route the bot started and left.
2. **The final push is as fast as the opponents'** (excess of the late cells ≈ 0 for both) and v12 does not split one
   turn's paint over several routes (it splits less than the opponents). The delay is **cross-turn switching**: start
   route A, next turn start B, come back to A later — a consequence of re-planning from scratch every turn, where
   another route's 3-cell prefix outscores A's continuation.
3. Half-built routes earn nothing and sit exposed: v12's cells placed in turns 10–19 are inked before they earn 8.1 % of
   the time in the losses (opponents 5.1 %, `arena-refresh-v12.md`).

Candidate: **route commitment** (`docs/route-commit-v1.md`) — a rollout-value bonus for a root that continues the
unbuilt part of last turn's chosen route, screened under policy v5 with step 1a.
