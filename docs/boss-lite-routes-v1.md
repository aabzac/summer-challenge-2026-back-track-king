# boss-lite-routes-v1 — the route-traffic term back, from the rollout's own portfolio (2026-10-21)

Flag `USE_BOSS_LITE_ROUTES` (+ `BOSS_LITE_ROUTE_WEIGHT`, default the fitted 0.371747), `// BOSS_ROUTES`
merger block, requires `USE_BOSS_LITE`. Candidate C = `opponent-model-v1` + this flag:
`current?NOISE=0.1,USE_BOSS_LITE=true,USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true,USE_BOSS_LITE_ROUTES=true`
(artifact 96,373 chars, compiles; B's and v2's artifacts unchanged).

## Hypothesis (Stage 3 of the 2026-10-20 plan, the cheap half)

The fitted boss logit has an `oppRouteTraffic` term (+0.37): the boss hits regions its opponent's
likely next routes cross, including empty corridors ("block"). `boss-sabotage-v2` dropped it because
computing it the v1 way costs two path searches per inactive pair. The rollout already proposes
both players' route portfolios every real turn (`routes[player]`, up to `ROLLOUT_MAX_ROUTES` each);
summing 1/paint-cost over the previous turn's portfolio of the chooser's opponent, once per region a
route crosses, gives the same signal at O(routes × length) and no path search. The term enters the
cheap stage, makes such regions candidates, and lifts the WAIT rule for them (v1 semantics). Inside
the rollout the opponent model uses our own portfolio the same way (frozen for the real turn).

Offline the route features added nothing to the cheap model (41.4 % vs 41.5 %), and dropping them
from the two-stage chooser cost nothing (51.1 % vs 50.6 %) — so the expected gain is not in
target accuracy on boss commitment turns but in the turns the chooser would otherwise WAIT
(v2 disrupts on 86–92 % of turns against v09, v1 on 99 %; smoke with the term: 98 %).

## Predeclared screen

Same 150 maps as the Stage 1 screen (seed 970000), 300 paired games per cell, CG 486/36 ms,
two workers, `tools/boss_proxy/run_routes_screen.sh`: C vs B head-to-head, C vs neural-v6, C vs
v09, C vs boss-v2; read against B's same-map rows (45.3 / 63.0 / 62.3 %). Keep the flag only if
C ≥ B head-to-head and on no row worse than B by more than the ±2.9 pp cell error.

## Results (2026-10-21, `docs/evidence/boss-lite-routes-v1/`)

| opponent | B (opponent-model-v1) | C = B + route term |
|---|---|---|
| B head-to-head | — | 50.0 % (150-150) |
| boss-v2 | 62.3 % | 60.3 % (181-119, −2.0) |
| neural-v6 | 45.3 % | **52.0 %** (156-144, **+6.7**) |
| v09 | 63.0 % | 62.8 % (188-111-1, −0.2) |

Zero errors; C disrupts on 97–98 % of turns (B 93–96 %). Both predeclared conditions hold (tie
head-to-head, no row below B by more than the cell error); the one gain is against neural-v6,
2.3 cell errors, i.e. exactly where v1's corridor blocks were expected to matter and where arena
transfer is judged. **Flag kept**; C is the next arena candidate after B's live result —
one change at a time. Second-pool confirmation (seed 960000, `confirm.log`): C vs neural-v6 **57.0 %** (171-129), B
**56.0 %** (168-132) → +1.0 on this pool; pooled over both pools (600 games) C 54.5 % vs B 50.7 %,
**+3.8 pp** — the first pool's +6.7 was partly noise, the sign holds. (B's own gain over v2
against neural-v6 is confirmed on both pools: 56.0 vs 50.3 and 45.3 vs 40.0, +5.5 pooled.)
`BOSS_HOLD_MARGIN` sweep on C, same maps, 300 games each: 0.5 → 51.0 % vs 1.0; 2.0 → 50.0 % (150-150-0) — both inside the cell error, the fitted 1.0 stays.
