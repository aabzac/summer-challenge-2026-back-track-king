# Stage 1 — boss-like opponent model inside the rollout (`opponent-model-v1`, drafted 2026-10-21)

Status: **the two-stage chooser is implemented for the real turn** as `boss-sabotage-v2`
(`RolloutBot.chooseBossLite`, flag `USE_BOSS_LITE`, `docs/boss-sabotage-v2.md`, 2026-10-21); the
in-rollout opponent model (`USE_BOSS_OPPONENT_MODEL`) is the next step on the same code.
`boss-sabotage-v1` failed on the arena (DEVLOG 2026-10-21), partly because of the cost analysed
below.

## Why

`RolloutBot` simulates the opponent with our own target chooser (`chooseTarget`, symmetric
and myopic: `rollout()` line "targets[player] = chooseTarget(targetBoard.state, player)").
The boss does not play like that: it disrupts on 99 % of turns, holds one region until it inks
(hold rate 71 %), and picks regions by the fitted logit of `docs/boss-proxy-v1.md`. Every
rollout value we compute against the boss is therefore computed against the wrong opponent,
which is the first systemic cause named in the 2026-10-20 analysis. `boss-sabotage-v1`
replaced only *our* real-turn DISRUPT with the fitted chooser and gained 67.45 % vs v09; the
simulated opponent (and our own simulated future disrupts) still use `chooseTarget`.

## Cost problem and the two-stage chooser

The full `BossSabotage.choose` is far too heavy for the rollout: per call it runs two path
searches per inactive pair (`cheapestPath` + `weightedPath`) and a complete ink simulation
(`Rules.ink` + `updateConnections` + income) per candidate region. `chooseTarget` is called
~2 × depth 8 = 16 times per rollout, several hundred rollouts per real turn.

Refits on the same 20,088 commitment sets (80/20 split, seed 1; `tools/boss_proxy/
fit_boss_target_lite.py`, outputs `build/analysis/boss-lite/*.json`):

| model | features | held-out top-1 |
|---|---|---|
| full (shipped) | 13 incl. dOpp, dOwn, closes, oppRouteTraffic, routesAny | **51.0 %** |
| lite0 | cheap counts only (oppCells, oppActive, ownCells, ownActive, oppPlacedPrev2, instab, adjTown, neutral, logFree) | 40.0 % |
| lite1 | lite0 + closes | 41.5 % |
| lite2 | lite1 + oppRouteTraffic, routesAny | 41.4 % |
| lite3 | lite2 + hitPrevByMe, hitPrevByOpp | 46.7 % — **discarded**: `hitPrevByMe` = −9.0 is an artifact (a commitment turn is defined as a switch away from the previous target), not a preference |

The route features add nothing once the income deltas are absent; the ~10 pp live in dOpp/dOwn
(and `closes`), i.e. in the ink simulation. So keep the ink simulation but only for a shortlist,
the same shape as `chooseTarget` itself: **(1)** the cheap part of the full logit (all weights
except dOpp, dOwn, closes) ranks every region in O(cells); **(2)** the top-K are rescored with
the complete logit after one ink simulation each — K = `ROLLOUT_TARGETS` = 4 is exactly the
number of ink simulations `chooseTarget` already pays per call.
`tools/boss_proxy/shortlist_recall.py` on all 20,088 commitment sets (mean 22.2 candidates;
evidence `docs/evidence/stage1-opponent-model-v1/shortlist-recall.log`):

| K | chosen region inside the shortlist | two-stage top-1 |
|---|---|---|
| 1 | 38.1 % | 38.1 % |
| 2 | 59.8 % | 46.6 % |
| 3 | 72.3 % | 49.5 % |
| **4** | **80.7 %** | **50.6 %** |
| 6 | 89.8 % | 51.3 % |
| 8 | 94.5 % | 51.5 % (= the full chooser) |

**K = 4 loses 0.9 pp against the full chooser at the cost of `chooseTarget`.** The route
features (`oppRouteTraffic`, `routesAny`) are dropped from the in-rollout chooser (no path
searches); `adjTownRegion` is static per map; `oppPlacedPrev2` needs the placement turn of each
track, which the rollout knows for simulated placements and `BossSabotage.observe` records for
real ones.

## Mechanism (default-off flags on `current`) — implemented 2026-10-21 (`// BOSS_MODEL` block), untested for strength

- `USE_BOSS_OPPONENT_MODEL` (boolean): in `rollout()`, for `player != me`, the target comes
  from `BossLite.chooseHeld(board, player, held)` instead of `chooseTarget`; `held` starts
  from the opponent's observed current target (the region whose instability rose last turn
  without our hit) and is carried through the simulated turns, so the simulated boss keeps
  hitting the same region until it inks unless another beats it by `BOSS_HOLD_MARGIN`.
- `BOSS_OPPONENT_REPLIES` (int, default 2): how many of the reply scenarios use the boss
  model (2 = both, 1 = scenario 0 only, keeping one `chooseTarget` opponent as a hedge).
- `USE_BOSS_OWN_MODEL` (boolean, separate): our *own* simulated future targets also use the
  two-stage chooser, for consistency with the real-turn `USE_BOSS_SABOTAGE` choice.
- Candidate construction, portfolio, evaluation, timer and all guards unchanged. The
  simulated opponent's construction stays `fillBuild` on its reply route.

Everything ships inside `// BOSS_SABOTAGE_BEGIN/END` (or a sibling block) so artifacts with
the flags off remain byte-identical to the pinned hashes.

## Predeclared comparison (policy v5)

Candidate A: `current?NOISE=0.1,USE_BOSS_SABOTAGE=true,USE_BOSS_OPPONENT_MODEL=true`.
Candidate B: A + `USE_BOSS_OWN_MODEL=true`. Control: `boss-sabotage-v1` if live-confirmed,
otherwise v09; both candidates are compared against the same control in the same run.

1. Proxy screen vs the current screening opponent `current?USE_BOSS_SABOTAGE=1` — note the
   candidate differs from the proxy (the proxy does not model its opponent), so step 1 applies:
   300 paired games / 150 fresh maps, continue at ≥ control + 3 pp.
2. Non-inferiority vs v09: 300 paired games, ≥ 48 %.
3. Live: 80 paired boss games, ≥ control + 5 pp.

Compute check before any games: simulated turns per real turn at 36 ms with the model on vs
off (the chooser must not cost more than ~15 % of the search; otherwise shrink K or the
cheap feature set and re-record). Exact-artifact strict `procCheck` 8 games.

## Results (2026-10-21, 300 paired games per cell, seed 970000 = the same 150 maps for every cell, CG 486/36 ms, zero errors)

Candidate A = `current?NOISE=0.1,USE_BOSS_LITE=true,USE_BOSS_OPPONENT_MODEL=true`;
candidate B = A + `USE_BOSS_OWN_MODEL=true`; control = `boss-sabotage-v2` (lite) on the same maps.

| opponent | v2 (lite) | A: opponent model | B: A + own model |
|---|---|---|---|
| v2 head-to-head | — | 54.8 % (164-135-1) | **57.8 %** (173-126-1) |
| boss-v2 (`current?USE_BOSS_SABOTAGE=1`) | 48.3 % | 55.7 % (+7.4) | **62.3 %** (+14.0) |
| neural-v6 | 40.0 % | **48.7 %** (+8.7) | 45.3 % (+5.3) |
| v09 | 62.7 % | 56.7 % (−6.0) | **63.0 %** (+0.3) |
| v09 vs neural-v6 (pool reference) | 30.7 % | | |

Reports `docs/evidence/stage1-opponent-model-v1/screen/*.json`, log `model-screen.log`.

Reading. Modelling the opponent as boss-like pays most where the opponent *is* boss-like (+14 pp
against boss-v2, +7.8 head-to-head against v2, +5–9 pp against neural-v6) and costs A 6 pp
against v09, whose disrupts (77–79 % of turns, re-targeted every turn) the model mis-predicts.
Making our own simulated disrupts consistent with the real-turn chooser (B) removes that cost
without losing the gains: B is at or above v2 on every row. B is the Stage 1 candidate,
**`opponent-model-v1`**. Policy v5 for B (it is not the proxy, so no degenerate clause): step 1
vs the screening opponent boss-v2 — 62.3 % against v2's 48.3 % on the same maps and far above the
v09 control (38.0 % on the 910000 pool) → PASS; step 2 — 63.0 % vs v09 ≥ 48 % → PASS; step 3 —
live, pending. A single 300-game cell has a ±2.9 pp standard error; the head-to-head and boss-v2
rows are well outside it, the neural-v6 row is at the edge.

Compute (cold JVM, 30×20, `compute-check-cold.log`): B's turn maximum 36–38 ms against v2's
40 ms; the simulated-turn count per real turn stays in the same 45–85 band (it depends on the
position more than on the chooser; the rollout is timer-bound either way). Artifact with both
flags: 95,421 chars, compiles.

Caveats. Same-map comparisons only: v2's absolute score against neural-v6 moved from 50.3 % to
40.0 % between the 960000 and 970000 pools while its delta over v09 stayed at +11.5/+9.3 pp. The
model's gain is largest against boss-like opponents, which the live boss is; the arena is mixed,
so the neural-v6 row (+5.3 pp) is the better guide to arena transfer than the boss-v2 row.

