# Arena losses of v12 — who beats it and how (2026-10-26)

The user's pick after the end-forecast line closed: the 18 losses among v12's first 50 arena games (puzzle agent
6813957, `docs/evidence/arena-2026-10-25-v12-agent-6813957.json`). Sources: the `cgAnalyze` traces of all 50 games
(per turn: income before the turn, points gained, cells and paint, disrupts, tracks inked, counterfactual deltas),
the per-turn table of `EndgameStudy`, the per-cell table of `cgCellStudy`, and the replay metadata (the opponent's
arena score at game time). Summaries in `docs/evidence/arena-losses-v12/`.

## Correction to the idle-cell diagnostic first

`docs/idle-cells-v12.md` (2026-10-25) split the per-cell table by its `leader` column, which is the index of the
named player's *seat* in that game, not a side flag — so "v12 vs opponents" there was in fact "games we played as
P1 vs games as P0", both sides mixed. With the correct split (`player == leader`):

| 50 games | v12 | opponents |
|---|---|---|
| cells placed (paint) | 7,657 (9,334) | 6,005 (7,271) |
| **cells that never earn** | **3,439 = 44.9 %** (44.2 % of paint) | **1,614 = 26.9 %** |
| …lost before earning: inked / neutralised | 1,088 / 445 | 804 / 445 |
| …**kept to the end, never on an active connection** | **1,906** (1,244 with ≥ 10 turns left; of those 868 on no route hypothesis at placement, 229 extensions, 81 completions) | **365** (264 with ≥ 10 turns left) |
| idle share by phase (early / mid / last third) | 16 % / 46 % / 85 % | 18 % / 30 % / 57 % |
| inked idle cells placed at instability 0 | 1,042 of 1,088 | 691 of 804 |
| earned cells: median delay / mean points | 2 turns / 40.8 | 1 turn / 36.2 |

The `cgAnalyze` totals agree (never-earning accepted cells: v12 44.9 %, opponents 26.9 %). So the earlier headline
"we are at the field's level" was wrong: **we leave 45 % of our cells idle, the field 27 %, and the excess is
almost entirely cells kept to the end on no connection** — five times the opponents' count. What stands from the
earlier note: the inked idle cells are placed into stable regions on both sides (the exposure is created after
placement), and the collisions are symmetric. The DEVLOG/STATUS statements of 2026-10-25 are superseded by this
section.

## The 18 losses

| | losses (18) | wins (32) |
|---|---|---|
| opponent's arena score at game time | 25.3 (12 of 18 ≥ 25.6) | 24.8 (13 of 32 ≥ 25.6) |
| record vs opponents scored ≥ 25.6 / below | **6-10** | **26-8** |
| final score, v12 / opponent | 3,027 / 4,028 | 3,678 / 2,520 |
| paint spent, v12 / opponent | 186 / 152 | 187 / 142 |
| cells, v12 / opponent | 156 / 129 | 151 / 115 |
| **points per paint, v12 / opponent** | **15.5 / 25.7** | 20.3 / 17.2 |
| turns with a placement, v12 / opponent | 88 % / 73 % | 96 % / 76 % |
| first income turn, v12 / opponent | 3.4 / 2.8 | 3.3 / 2.2 |
| cumulative income at turn 10 / 20 / 24, v12 vs opponent | 163 / 693 / 979 vs 217 / 918 / 1,297 | 149 / 926 / 1,418 vs 151 / 725 / 1,053 |
| income per placed cell at turn 10 / 20 / 30, v12 vs opponent | 1.18 / 1.04 / 1.09 vs 1.51 / 1.40 / 1.31 | 1.09 / 1.85 / 1.48 vs 0.83 / 1.62 / 0.97 |
| last turn with a lead (fraction of the game) | 0.00–0.47, never past half; 6 of 18 never led | — |
| our tracks inked / theirs | 76 / 83 | 64 / 70 |
| value of our disrupts (score if omitted) / of theirs | −419 / +191 | −210 / +331 |
| game length; inked regions at the end | 74 turns; 58 % | 66; 52 % |
| map sizes | 29×19 ×4, 21×14 ×3, 24×16 ×3, 27×18 ×3, 26×17 ×2, 30×20 ×1; seats 9/9 | |
| repeat opponents | TSM540 0-2, Carlos4825 0-2, LePiire 1-2, FREELOADER 2-2 | |

Every one of the 18 losses has the opponent ahead of us in **points per paint** (ratios 0.36–0.90). Paint spent and
cells placed are the same as in wins, and our sabotage is not weaker in losses (our disrupts are worth more there,
the opponents' less). The losses are decided by **construction efficiency from the first ten turns on**: with equal
cell counts the opponent's cells earn 1.3–1.5 points per turn each, ours 1.0–1.2, and the gap never closes — no
loss was led past the midpoint.

## Reading

1. The field's better players build *less and better*: they place on three turns in four and keep 73 % of their
   cells earning; we place on 93 % of turns (`USE_ROLLOUT_FILL` since v09, single-cell network roots) and keep
   55 %. The ~38 purposeless cells per game are paint that an efficient opponent turns into income.
2. Against a v09-style builder (the boss-v2 proxy) the extra cells pay — `fill-off-v1` lost 3.8 pp vs the proxy
   and 57 % head-to-head — because the proxy's own construction is as loose as ours. Against the arena's top half
   they do not: the proxy and the mirror cannot see this, which is why five post-v12 screens found nothing.
3. The cheapest test of the hypothesis "the purposeless cells are the fill (and the network roots)" is a screen
   against an **arena-like builder**: `neural-v6` (policy v5's degenerate-clause opponent, 100 % disrupt profile)
   with `current` as the same-map control — run below.
4. The structural lever, if the hypothesis holds, is a construction rule of the form "place only cells with a
   route purpose; keep the paint otherwise" — paint does not carry over, so this is only right when the
   purposeless cell has negative value (it feeds the boss-like chooser's cell count and reveals nothing useful).
   That is a mechanism to screen against both the proxy and neural-v6, and then live.

## Cross-check — `fill-off-v1` against neural-v6 (`tools/boss_proxy/run_fill_nv6_screen.sh`, pool 1008000)

| cell (300 paired games, 2 workers, CG timing) | vs neural-v6 | W-L | paired delta vs `current` (150 maps) |
|---|---|---|---|
| `current` (= v12) | **56.7 %** | 170-130 | — |
| `current?USE_ROLLOUT_FILL=0` | 47.0 % | 141-159 | **−9.7 ± 6.9 pp** (maps better / worse / equal 30 / 57 / 63) |

Against the arena-like builder the fill is worth *more* than against the proxy (−3.8 pp there). The hypothesis "the
purposeless cells are ballast against efficient builders" is **rejected**, and the rules say why: paint does not carry
over (`docs/rules/bronze.md`: "These points do not carry over to the next turn"), so a fill cell costs nothing. The
opponents' 27 % of turns without a placement are forgone cells, not thrift, and "points per paint" is the wrong lens.
The 1,906 idle kept cells are free paint plus whatever they do as ink magnets, and the net of the two is positive on
both opponents.

## What the losses are, then — the income gap decomposed

Per game, means over the 18 losses / 32 wins (`efficiency.txt` in the evidence folder; income is one point per turn
per own track on each active shortest path, so a track on two paths earns twice):

| | losses: v12 / opponent | wins: v12 / opponent |
|---|---|---|
| cells placed | 156.5 / 128.6 | 151.2 / 115.3 |
| **cells that earn at least once** | **79.3 / 95.7** (51 % / 74 %) | 87.2 / 83.4 (58 % / 72 %) |
| points per earning cell | 38.2 / 42.1 | 42.2 / 32.4 |
| active span of an earning cell | 30.6 / 26.0 turns | 27.0 / 22.7 |
| **connections served per earning cell-turn** (points ÷ active turns) | **1.25 / 1.62** | 1.56 / 1.43 |
| earning cells at multiplicity < 1.2 / ≥ 2.5 | 50 % / 27 % vs 40 % / 33 % | 42 % / 32 % vs 42 % / 29 % |
| **turns from placement to first income** | **4.3 / 2.2** | 3.5 / 2.7 |
| earning cells later inked | 57 % / 68 % | 54 % / 66 % |
| income per turn at turn 10 / 20 / 30 | 32 / 55 / 82 vs 42 / 75 / 104 | 31 / 100 / 116 vs 24 / 89 / 76 |
| turns with no placement | 15 % / 29 % | 5 % / 27 % |

The opponents who beat us put **16 more cells per game on active paths with 28 fewer placements**; each of those
cells serves **1.62 connections per turn against our 1.25**, and it starts earning **two turns earlier**. In the wins
the same two numbers are ours (1.56 vs 1.43; the opponent's cells are the single-connection ones). Their tracks are
inked *more* often than ours (68 % vs 57 %), so survival is not the gap, and sabotage is not either. The gap is where
the cells go: on corridors shared by several desired connections, next to paths that already earn. Our extra cells
go on hypotheses that take four turns to pay or never pay (868 of the kept-idle cells with ≥ 10 turns left lay on no
route hypothesis at placement).

In the bot (`RolloutBot.propose`) every route proposal is the paint-cheapest path of *one* pair (plain, around the
opponent's cells, or around the threatened region), valued by the full-board income after completion — so a route
that happens to complete two pairs is valued, but the path itself is never bent toward cells that other pairs' paths
would use. The network roots and the fill add single cells next to own tracks. Nothing in the search prefers a
corridor.

## Verdict and the next candidate

- The 18 losses are **construction-efficiency losses to the field's top half** (6-10 against opponents scored ≥ 25.6,
  26-8 below): equal cell counts, 1.3× our income per cell from turn 10 on, never led past the midpoint.
- The mechanism behind the field's efficiency is **multiplicity**: cells on shortest paths that serve two or more
  connections, extended from paths that already earn.
- Not the lever: the fill (−3.8 pp vs the proxy, −9.7 pp vs neural-v6), the forecast and the horizon
  (`end-forecast-v1`), collisions and placement-time instability (`collision-avoidance-v1`, `unstable-cost-v1`).
- **Next candidate: shared-corridor routes** — a fourth proposal variant per pair whose path cost discounts every
  cell that lies on the current shortest completion path of another desired pair (and own active paths), so that the
  proposed route bends toward corridors; valued exactly as the other proposals are (full-board income, exposure
  discount). Own merger block, default off. Screen vs the proxy with `current` as the same-map control **and** vs
  neural-v6 (the arena-like builder; `run_fill_nv6_screen.sh` has the pattern), then live. The leader-corpus study in
  `docs/PLAN.md` (multiplicity and delay of the top players' earning cells) is the cheap confirmation of the shape
  before the mechanism is written.
