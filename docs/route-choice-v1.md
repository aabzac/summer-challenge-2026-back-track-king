# route-choice-v1 — do the stronger players pick different routes? (2026-10-27, construction line, step 1)

The user chose the construction line ("Строка" = «стройка») after the arena refresh: route proposals that reproduce the
stronger players' middle-game choices. Step 1 measures whether such a signal exists at the level our bot decides on —
the choice among candidate routes. Tools: `btk.tools.RouteChoiceExport` (new), `tools/route_choice/fit_route_choice.py`
(new); data in `docs/evidence/route-choice-v1/`.

## Data

For every placement turn of a target player, `RouteChoiceExport` lists the candidates our proposal step would consider —
per desired pair the paint-cheapest completion over any tracks (variant 0) and over own tracks only (variant 1; for an
already active pair only the own-only route, a backup / takeover candidate) — with 19 features on the pre-turn board
(cost, turns to complete, income change for both sides, our proposal value = gain difference × earning turns / turns,
pairs switched on, path length, own and opponent tracks on the path, own share after completion, instability on the
free cells and on the path, adjacency to own tracks, completes this turn, active pair, variant), and labels the chosen
candidate = the one whose free cells overlap the cells actually placed that turn the most (ties: the cheaper).

| population (turns 5–40) | games | placement turns with candidates | labeled |
|---|---|---|---|
| opponents of v12, arena re-upload | 147 | 4,613 | 79 % |
| v12, same games | 147 | 4,861 | 83 % |
| opponents / v12, first upload | 50 / 50 | 1,633 / 1,672 | 81 / 83 % |
| Indrill (leader corpus) | 282 | 9,055 | 78 % |

## Result, turns 10–19, game-level holdout (20 %)

| predictor of the chosen route (≈ 38 candidates per turn) | strong opponents (score ≥ 27): top-1 / top-3 | v12's own choices: top-1 / top-3 |
|---|---|---|
| **our proposal value** (gain difference × earning / turns) | **0.281 / 0.438** | **0.290 / 0.394** |
| conditional logit on the 19 features (Newton–Raphson fit) | 0.183 / 0.386 | 0.108 / 0.293 |
| cheapest route | 0.131 / 0.216 | 0.124 / 0.212 |

1. **Our own proposal value predicts the strong opponents' route choices as well as it predicts ours** (top-1 0.28 vs
   0.29). Among the candidate routes our bot generates, the stronger players and v12 choose much the same; a linear
   imitation model over route features does not improve on it (0.18). Route-level imitation would not change what we
   build — the construction gap of `docs/arena-refresh-v12.md` is not the choice among these candidates.
2. The second-order differences of the chosen routes (strong opponents vs v12): routes over **already active pairs**
   (backup / takeover) 25 % vs 17 %, own-only routes 49 vs 44 %, cells adjacent to own tracks 2.07 vs 1.85 per route,
   lower proposal value (235 vs 307) and gain difference (42 vs 49), similar cost (8.9 vs 7.8) and completes-now share
   (23 vs 21 %).

## What this leaves

The middle-game gap (`arena-refresh-v12.md`: same completions, the opponent's tracks earn sooner — 53 vs 39 % within one
turn — and keep paying longer) does not come from picking other routes. Remaining hypotheses, in order of cost:
(a) **cell order within routes** — which cells of a multi-turn route are placed first (the opponent's cells earn sooner
with the same completions); (b) **backup routes for active pairs** — the one route class the strong players choose
clearly more often (25 vs 17 %), a cheap flag to screen under step 1a; (c) the candidate generation itself (routes our
step never proposes), which this export cannot see.
