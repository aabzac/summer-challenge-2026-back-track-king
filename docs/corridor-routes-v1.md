# corridor-routes-v1 — route proposals that bend toward shared corridors (2026-10-26)

Flag `USE_CORRIDOR_ROUTES` (+ `CORRIDOR_WEIGHT`, default 1.0, finite and ≥ 0), `// CORRIDOR` merger block, current-only
rollout experiment. Default off: the default artifact (v12, 91,349 chars, config `d7803b42…`) is byte-identical with the
flag off and the two fields leave the configuration while off (`CorridorRoutesSubmissionTest`, `CorridorRoutesTest`).

## Hypothesis (from the arena-loss diagnostic, `docs/arena-losses-v12.md`)

v12's 18 arena losses are construction-efficiency losses to the field's top half: with equal cell counts the
opponent's earning cells serve **1.62 active connections per turn against our 1.25** and start earning two turns
earlier. Income is one point per turn per own track on each active shortest path, so a track on two paths earns
twice; the winners' cells sit on corridors that several desired connections share. In the bot every route proposal
(`RolloutBot.propose`) is the paint-cheapest path of *one* pair — plain, around the opponent's cells, or around the
threatened region — valued by the full-board income after completion. A route that happens to complete two pairs is
valued, but no path is ever bent toward cells that other pairs' paths would use; the network roots and the fill add
single cells next to own tracks. Nothing in the search prefers a corridor.

## Mechanism

A fourth proposal variant per pair, run after the ordinary proposals under the same time reserve and capped at
`ROLLOUT_MAX_ROUTES` queries per turn:

1. While the ordinary variant-0 proposals are generated for the bot's own seat, the free cells of every pair's cheapest
   path are recorded (`corridorRecord`) and counted per cell (`corridorCount`).
2. For each pair with a recorded path, the path search is repeated with scaled costs (`corridorCosts`): a free cell's
   paint cost is divided by `1 + CORRIDOR_WEIGHT × (number of *other* pairs whose cheapest path uses the cell)`, the
   instability surcharge (`UNSTABLE_COST`) is kept, nothing is blocked; costs are scaled by 12 to keep integer paths.
   The query is skipped when no free cell of the board is shared.
3. The resulting route is valued exactly as the other proposals are (full-board income after completion, exposure
   discount, threat scenario, earning horizon over turns to build) and competes for the same `ROLLOUT_WIDTH` slots, so
   a corridor route wins only when the rollout likes it — the effective-cost discount only changes *which* path is
   proposed, never its value.

Cost: at most `pairCount` extra path queries per turn, each bounded by the turn timer; one pass over the cells per
query. Two weights: 1.0 (a cell shared with one other pair costs half) and 2.0 (a third).

Counters: `corridorQueries`, `corridorProposals` (public, per turn).

## Predeclared screen (`tools/boss_proxy/run_corridor_screen.sh`)

Fresh pool seed **1009000** (first use; 1008000..1008149 was the fill cross-check of `arena-losses-v12`), 150 maps × 2
seats = 300 paired games per cell, CG 486/36 ms, two workers, the user's machine; proxy spec with
`USE_EXPOSURE_INCOME=0`.

- v5 step (a): `K1-vs-boss2`, `K2-vs-boss2`, `cur-vs-boss2` — the boss-like screening proxy on the same maps/seeds/seats
  as the `current` (= v12) control; **continue only at ≥ control + 3 pp** (paired per-map delta,
  `summarize_screen.py --control cur`).
- v5 step (b): `K1-vs-cur`, `K2-vs-cur` — head-to-head vs the selected version; **≥ 48 %**.
- Arena-like cross-check: `K1-vs-nv6`, `K2-vs-nv6`, `cur-vs-nv6` — neural-v6 (the opponent of policy v5's degenerate
  clause; the mechanism targets what the arena's top half does, which the proxy cannot see). Read as the paired delta vs
  `cur-vs-nv6`; a candidate that passes (a)+(b) but loses here is suspect, one that passes here but fails (a) is parked
  (the proxy is the policy's screen).

Then, for an arm that passes, v5 step (c): 80 paired live boss games (≥ control + 5 pp).

## Results

(filled in when the screen completes — `docs/evidence/corridor-routes-v1/`)
