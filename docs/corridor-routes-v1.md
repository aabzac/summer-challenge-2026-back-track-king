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

## Results (2026-10-26, `docs/evidence/corridor-routes-v1/`)

| cell (300 paired games) | rate | W-L-D | paired delta vs `current` (150 maps) | maps better / worse / equal |
|---|---|---|---|---|
| `cur-vs-boss2` | 71.5 % | 214-85-1 | — | — |
| **`K1-vs-boss2`** (weight 1) | **78.0 %** | 234-66-0 | **+6.5 ± 7.2 pp** | 50 / 32 / 68 |
| `K2-vs-boss2` (weight 2) | 72.7 % | 218-82-0 | +1.2 ± 6.8 pp | 45 / 44 / 61 |
| **`K1-vs-cur`** | **51.7 %** | 155-145-0 | — (small maps 50.0 %, h ≥ 19: 55.0 %) | — |
| `K2-vs-cur` | 50.3 % | 151-149-0 | — (52.0 % / 47.0 %) | — |
| `cur-vs-nv6` | 57.0 % | 171-129-0 | — | — |
| **`K1-vs-nv6`** | **64.7 %** | 194-106-0 | **+7.7 ± 7.4 pp** | 50 / 33 / 67 |
| `K2-vs-nv6` | 62.3 % | 187-113-0 | +5.3 ± 8.0 pp | 49 / 37 / 64 |

Time per move: K1 8.6 ms, K2 8.3 ms, `current` 9.7 ms (the corridor pass runs under the existing route reserve; no
cost). 0 errors in 2,400 games.

**K1 (weight 1) passes v5 step (a) (+6.5 pp, above +3) and step (b) (51.7 %, above 48 %), and it gains the same
against neural-v6 (+7.7 pp)** — the first candidate since the proxy line started whose gain shows against the
arena-like builder as well, which is what the arena-loss diagnostic asked for. K2 (weight 2) fails (a) (+1.2 pp) and
is parked: a cell shared by one other pair at a third of its paint bends routes too far.

## K1's artifact, strict process check and the live plan (2026-10-26)

- `gradlew submit "-PsubmitVersion=current?USE_CORRIDOR_ROUTES=true" -PsubmitOut=build/submit-corridor-k1` →
  **93,468 / 100,000 chars** (readable), sha `deeefb19…`, build `ae716f57…`, **config `299cafd9…`**, label
  `v12 (modified)` (`evidence/corridor-routes-v1/K1-artifact-manifest.json`).
- Strict `procCheck` (zero grace, 4 games vs v09): **0 problem games, 0 timeouts**, first response ≤ 725 ms, later
  responses ≤ 38.1 ms (2/4 wins — four games say nothing about strength;
  `evidence/corridor-routes-v1/K1-artifact-process-check.json`).
- Live plan for v5 step (c), frozen with no network calls: `build/cg-boss/corridor-k1-v12-boss-40/plan.json`
  (copy in `evidence/corridor-routes-v1/live-plan.json`), id `67ba025f…`, matched design, 40 maps × 2 builds = 80 API
  games, boss 6767608, schedule seed 2026102601, candidate = `build/submit-corridor-k1`, control = `build/submit-v12`
  (the selected v12 artifact, build `ce1d3a3b…`, the arena entry), bound to the puzzle-IDE session like every series.
  Pass rule as before: K1 ≥ v12 + 5 pp over the 40 paired maps; the 80-game policy sample completes with a second
  session. No API games were played today before this plan. Launch, by the user on this machine:

  ```
  bash tools/run_boss_paced.sh build/cg-boss/corridor-k1-v12-boss-40/plan.json build/cg-boss/private/v09-ink45-boss-v1/session.json 20 300 20 build/cg-boss/private/v09-ink45-boss-v1/start-session.json 0722a2fe30a43359747729078cfa150d027fc9c6fc7a9e3fbb102210563accba 1172955
  ```

## Live boss, v5 step (c) session 1 (2026-10-26 12:04–13:04 UTC, `evidence/corridor-routes-v1/live-session-1/`)

Launched by Claude after the user's "Добавь правило" (allow rule `Bash(bash tools/run_boss_paced.sh *)` in
`.claude/settings.local.json` — the first series not launched by hand). 80 API games, 0 technical failures, 0 authorized
retries, **80/80 replays exact** (`cgValidate`, both capture directories). **K1 18/40 = 45.0 %, `current` (v12, the arena
artifact) 24/40 = 60.0 %** against boss 6767608 on 40 matched maps; discordant pairs **3 : 9**, paired **−15.0 pp**,
one-sided p = 0.98; seat P0 9/20 vs 14/20, seat P1 9/20 vs 10/20; by map size (K1 / v12 points over the maps): 21×14 3/3
(5), 23×15 4/7 (10), 24×16 2/2 (4), 26×17 3/2 (6), 27×18 1/3 (4), 29×19 4/6 (8), 30×20 1/1 (3). Runner report: complete,
80 attempted requests, protocol "original fixed".

**Verdict: K1 fails step (c) — rejected; the flag stays in the code, default off (parked).** The corridor routes gain
+6.5 pp against the boss-v2 proxy and +7.7 pp against neural-v6 and lose 15 pp against the live boss on the same maps
as the control — the clearest proxy-vs-live divergence of the line (D +22.5, B +2.5, E1 +5.0, K1 −15.0 pp live). Both
local opponents are models of the boss built around our own bots' construction; routes bent toward shared corridors
(longer, concentrated) are something the real boss punishes, and the obvious hypothesis is that the shared corridors
concentrate our cells in the regions it inks. The next step, if the user wants it, is a diagnostic of these 80 games
(`cgCellStudy` over both capture directories on the same maps: where K1's corridor cells end up — inked, idle, or earning
less than v12's); and for construction changes the proxy's predictive value is in question.
