# collision-avoidance-v1 — an explicit aversion to the opponent's predicted cells (2026-10-25 night)

Flag `USE_COLLISION_AVOIDANCE` (+ `COLLISION_PENALTY`, default 5.0 rollout-value points per shared cell, must be
finite and ≥ 0), `// COLLISION` merger block, current-only rollout experiment. Default off: the default artifact
(v12, 91,349 chars, config `d7803b42…`) is byte-identical with the flag off and the two fields leave the
configuration while off (`CollisionAvoidanceSubmissionTest`, `CollisionAvoidanceTest`).

## Hypothesis (from the idle-cell diagnostic, `docs/idle-cells-v12.md`)

456 of our 6,656 arena cells (6.9 % of our paint) were neutralised by same-cell placements with the opponent,
as many as the opponents lose. The rollout already predicts the opponent's first-turn cells (its reply routes
packed into one turn of paint, `fillBuild`) and resolves collisions in the reply scenarios (`Rules.applyPlacements`
makes the cell neutral), so the search *sees* a collision — but as a symmetric loss (both sides lose the paint,
the score difference barely moves) and only inside the replies it plays. The candidate adds an explicit
aversion: before `searchRoots`, the opponent's predicted cells for the reply scenarios are collected once
(`predictCollisions`), and every root's rollout value loses `COLLISION_PENALTY` × (cells it shares with them).
Two weights, 5 and 15 points (a route's rollout value is a score difference plus three turns of income
difference; 15 points ≈ one turn of a mid-game income lead). Cost: one `fillBuild` per reply per turn plus a
few comparisons per root — nothing measurable. If the search already prices collisions correctly, the penalty
is noise or a loss (it makes the bot yield cells the opponent wants).

## Predeclared screen (`tools/boss_proxy/run_collision_screen.sh`)

Fresh pool seed **1006000** (first use; 1005000..1005149 was `fill-off-v1`), 150 maps × 2 seats = 300 paired
games per cell, CG 486/36 ms, two workers, the user's machine; proxy spec with `USE_EXPOSURE_INCOME=0`.

- v5 step (a): `C5-vs-boss2`, `C15-vs-boss2`, `cur-vs-boss2` — the boss-like screening proxy on the same
  maps/seeds/seats as the `current` (= v12) control; **continue only at ≥ control + 3 pp** (paired per-map delta,
  `summarize_screen.py --control cur`).
- v5 step (b): `C5-vs-cur`, `C15-vs-cur` — head-to-head vs the selected version; **≥ 48 %**.
- Zero bot errors; move times in `current`'s band.

Decision rule: keep a weight only if its arm passes both steps; the better-passing weight goes to `submit` +
strict `procCheck` and then to the live step (80 paired boss games vs `current`, user launch, on another day).
Failing arms are recorded and parked.

## Results (2026-10-25, `docs/evidence/collision-avoidance-v1/`, screen 21:39–22:03 UTC)

| cell (300 paired games, pool 1006000) | rate | W-L-D | ms/move cand. | max ms | maps h < 19 | h ≥ 19 |
|---|---|---|---|---|---|---|
| `current` (v12) vs boss-v2 (control) | **77.0 %** | 231-69-0 | 11.8 | 57 | 73.9 % | 86.5 % |
| C5 (penalty 5) vs boss-v2 | 73.0 % | 219-81-0 | 11.2 | 58 | 70.4 % | 81.1 % |
| C15 (penalty 15) vs boss-v2 | 71.7 % | 215-85-0 | 11.2 | 69 | 69.0 % | 79.7 % |
| C5 vs `current` | 48.0 % | 144-156-0 | 11.1 | 52 | 47.8 % | 48.6 % |
| C15 vs `current` | 42.0 % | 126-174-0 | 11.3 | 40 | 42.5 % | 40.5 % |

Paired per-map deltas vs the control on identical maps/seeds/seats:

| arm | delta vs `current` against boss-v2 | maps better / worse / equal | step (a) ≥ +3 pp | step (b) ≥ 48 % |
|---|---|---|---|---|
| C5 | **−4.0 ± 7.0 pp** | 37 / 44 / 69 | fails | 48.0 % |
| C15 | **−5.3 ± 6.5 pp** | 33 / 43 / 74 | fails | 42.0 % |

Zero bot errors; disrupt rate 98.3 % everywhere; move times in the control's band.

**Verdict: rejected, the flag stays off.** Both weights lose against the proxy and the stronger one loses
head-to-head too (42 %): the explicit aversion makes the bot yield the cells the opponent wants, and the
search had already priced the collision correctly in its reply scenarios (a symmetric paint loss is worth
little in score difference). The 6.9 % of paint neutralised in the arena is therefore not a lever either —
it is the cost of contesting the same corridors, paid by both sides. Together with `unstable-cost-v1` and
`fill-off-v1` this closes the idle-cell line: the diagnostic's only remaining item is the last-third
waste, which needs a game-end forecast (both sides, 76 % idle), a larger piece of work.
