# end-forecast-v1 — can a bot forecast the end of the game, and would it help? (2026-10-26)

From the idle-cell diagnostic (`docs/idle-cells-v12.md`): 76 % of the paint placed in the last third of a
game never earns, for both sides. The route proposals credit a route with
`earning = min(ROLLOUT_ROUTE_HORIZON = 16, turnsLeft) − turns + 1` earning turns, where `turnsLeft` is
`100 − turn` — the hard cap, not the real end (a game ends when no pair's towns can be connected through
non-inked cells any more; median length 65–71). Question: is there a cheap public-board forecast of the
remaining turns that beats that, inside the horizon the proposals use?

## Study (`btk.tools.EndgameStudy`, `docs/evidence/end-forecast-v1/`)

One row per turn of every validated replay: active pairs, inactive pairs still connectable through
non-inked cells (`Pathing.reachableAvoidingInk`), impossible pairs, inked regions, unstable regions and
their instability sum, cell counts, score difference, and the true remaining turns. Two corpora: v12's 50
arena games (3,447 turns) and the canonical boss corpus (`replays/cg/boss`, 1,104 games, 77,211 turns;
328 of them reach the 100-turn cap). Fits by least squares on each corpus (scratch script output in
`v12-fit.txt`, `boss-fit.txt`).

| forecast of the remaining turns | v12 games: MAE late third / bias | boss corpus: MAE late / bias | **error inside the 16-turn horizon, late third** (v12 / boss) |
|---|---|---|---|
| baseline `100 − turn` | 25.2 / +25.2 | 22.5 / +22.5 | **4.5 / 3.6** (truth averages 10.7 / 10.8 of 16) |
| `1.1 × possibleInactive + 27` | 12.5 / +12.2 | 11.3 / +11.1 | 4.5 / 3.6 |
| `+ activePairs` | 11.8 / +11.1 | 10.8 / +10.2 | 4.5 / 3.6 |
| `+ inked-region share` (coef ≈ −56…−60) | 11.5 / +7.3 | 12.0 / +7.9 | 4.8 / 4.0 |
| `+ turn` | 11.5 / +6.9 | 12.3 / +8.2 | 4.7 / 3.9 |
| simple `4 × possible + 4` | 10.3 / −5.4 | 9.7 / −6.7 | 6.7 / 6.4 |

The remaining turns are almost unrelated to how many pairs are still connectable (mean remaining at 0
connectable inactive pairs: 23–27 turns — the game goes on while the *active* connections survive the
inking; at 10 pairs: 46–50), and the best static fit still misses by 11–12 turns in the last third. What
matters for the bot is the error **inside the 16-turn earning horizon**, and there no forecast beats the
baseline cap: 3.6–4.5 turns for the cap, the same or worse for every fit, 6–8 for the simple rules.

## Reading

A board-feature forecast of the game's end is not a lever: the end is decided by the opponent's future
inking, which the public board does not carry. The proposals' cap already absorbs most of the error; the
residual is a systematic **~4 earning turns too many** credited to late routes (the truth inside the
horizon averages 10.8 of 16). The only cheap consequence to test is the cap itself — a shorter
`ROLLOUT_ROUTE_HORIZON` makes every proposal's earning credit closer to the late-game truth (at the price
of under-crediting early routes, which the rollout's own 8-turn simulation partly compensates).

## Next step taken — `run_horizon_screen.sh`

`current?ROLLOUT_ROUTE_HORIZON=12` (H12) and `=10` (H10) vs boss-v2 with `current` (= v12) as the same-map
control and head-to-head, fresh pool 1007000, 300 paired games per cell (v5 steps a+b as usual; the proxy
spec carries `USE_EXPOSURE_INCOME=0`).

## Results (2026-10-26, `docs/evidence/end-forecast-v1/horizon-summary.txt`, screen 06:19–06:39 UTC)

| cell (300 paired games, pool 1007000) | rate | W-L-D | ms/move cand. | max ms | maps h < 19 | h ≥ 19 |
|---|---|---|---|---|---|---|
| `current` (v12, horizon 16) vs boss-v2 (control) | **79.0 %** | 237-63-0 | 8.2 | 36 | 77.6 % | 82.2 % |
| H12 vs boss-v2 | 78.3 % | 235-65-0 | 8.7 | 47 | 78.1 % | 78.9 % |
| H10 vs boss-v2 | 76.3 % | 229-71-0 | 8.3 | 38 | 76.2 % | 76.7 % |
| H12 vs `current` | 44.0 % | 132-168-0 | 7.9 | 36 | 42.9 % | 46.7 % |
| H10 vs `current` | 52.7 % | 158-142-0 | 7.7 | 40 | 51.9 % | 54.4 % |

Paired per-map deltas vs the control on identical maps/seeds/seats:

| arm | delta vs `current` against boss-v2 | maps better / worse / equal | step (a) ≥ +3 pp | step (b) ≥ 48 % |
|---|---|---|---|---|
| H12 (`ROLLOUT_ROUTE_HORIZON=12`) | −0.7 ± 6.0 pp | 28 / 32 / 90 | fails | 44.0 % fails |
| H10 (`ROLLOUT_ROUTE_HORIZON=10`) | −2.7 ± 5.8 pp | 27 / 36 / 87 | fails | 52.7 % |

Zero bot errors; disrupt rate 98.2–98.5 % everywhere; move times in the control's band.

**Verdict: both horizons fail step (a) — the default 16 stays; the end-of-game line is closed.** Crediting late
routes with fewer earning turns buys nothing against the proxy (the tall-map proxy rate even drops from 82 % to
77–79 %), and the two head-to-head results (44 % / 53 %) do not order the horizons at all — the horizon is
not where the last-third waste comes from. Taken with the study: the end of a game is not forecastable from
the board inside the horizon the bot uses, and the horizon itself is not a lever. What the last third needs,
if anything, is a different *kind* of late-game construction (protecting active connections instead of
starting routes), which is a mechanism, not a parameter — an idea for a future diagnostic of what the
winners build in their last third (the leader corpus, `replays/cg-indrill`), not a next-step candidate yet.
