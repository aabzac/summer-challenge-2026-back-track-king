# Stage 2 — compute, algorithmically (`stage2-compute-v1`, 2026-10-21)

## Where candidate C spends its 36 ms (JFR, 1 ms sampling, one 30×20 game vs v09, cold JVM)

Samples attributed to the candidate (`docs/evidence/boss-lite-routes-v1/profile-C-samples.txt`):

| inclusive | method |
|---|---|
| 44 % | `propose` — route enumeration at the real turn (`Pathing.cheapestPath`, 32 % of all samples) |
| 40 % | `rollout` — of which `chooseBossLite` **36 %**, almost all of it `SearchState.refresh` after the hypothetical ink (`Pathing.shortestPath`, 53 % of all samples) |
| 5 % | `SearchState.resolve` (the simulated turns themselves) |

So on a 30×20 map the search completes only ~3 root×reply comparisons per turn: route
enumeration takes almost half the window and the boss chooser's four ink simulations per call
take most of the rest; the simulated turns are cheap. Two of the three runtime flags the project
already validated for decision parity on 524 saved states target exactly these: incremental ink
refresh (recompute only the paths the hypothetical ink broke) and the fast cheapest path (geometry
reuse in `propose`); batched refresh is the third. The incremental refresh was wired only into
`chooseTarget`; it is now also used by `chooseBossLite` (same `// INK_REFRESH` block semantics:
`inkBoard.inkAndRefresh(r)` when `USE_INCREMENTAL_INK_REFRESH`).

## Compute check (`SimTurns`, hot JVM, two 30×20 maps, `compute-runtime-flags.log`)

| configuration | simulated turns / real turn | comparisons / turn |
|---|---|---|
| C | 39 · 48 | 2.9 · 2.9 |
| C + incremental ink refresh | 58 · 85 | 4.3 · 5.7 |
| + fast cheapest path | 60 · 124 | 4.2 · 8.1 |
| + batched refresh (= **D**) | 84 · 106 | 5.2 · 7.3 |

Roughly ×2 simulated turns and ×2 comparisons inside the same 36 ms; turn maxima unchanged
(36–42 ms). Artifact sizes: C 96,373 · +incremental 97,520 · +fast path 98,872 · **D 99,683**
(limit 100,000 — nothing else fits on top of D; the prune step of the plan becomes a
prerequisite for Stages 3–4).

## Predeclared screen

D = C + `USE_INCREMENTAL_INK_REFRESH=true,USE_FAST_CHEAPEST_PATH=true,USE_BATCHED_REFRESH=true`,
same 150 maps (seed 970000), 300 paired games per cell (`tools/boss_proxy/run_runtime_screen.sh`):
D vs C, D vs neural-v6, D vs v09, D vs boss-v2; read against C's rows (52.0 / 62.8 / 60.3 %).
Keep D if ≥ C head-to-head and no row below C by more than the cell error. Note the project's
record: batched refresh alone passed the v09 mirror (56.65 %) and did not transfer to the boss;
the runtime flags change *how much* is searched, not what is valued, so a live check is still
required before promotion.

## Results (2026-10-21, `docs/evidence/stage2-compute-v1/`)

| opponent | C | D = C + runtime flags |
|---|---|---|
| C head-to-head | — | **53.7 %** (161-139) |
| boss-v2 | 60.3 % | **65.0 %** (195-105, +4.7) |
| neural-v6 | 52.0 % | 49.7 % (149-151, −2.3) |
| v09 | 62.8 % | **67.0 %** (201-99, +4.2) |

Zero errors. Both predeclared conditions hold. D's mean time per move is **11.8 ms** against C's
18.6 on the same games: with the three flags the ordinary search now *completes* on most turns
and returns early — the compute dividend is real and only partly spent. That is the lever for
the next step: more replies/roots/depth inside the same 36 ms (e.g. `ROLLOUT_REPLIES` 3,
`ROLLOUT_DEPTH` 10), each as a separate same-map screen against D. Timing on CG hardware for
these three flags was checked by the user's earlier procCheck runs individually; the combination
needs its own strict procCheck before any upload (artifact 99,683 chars, sha256 per `submit`).
