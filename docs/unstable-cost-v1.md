# unstable-cost-v1 — the construction-side exposure penalty (2026-10-25 night)

A parameter screen, no code change: `UNSTABLE_COST` (default 1.0) is the extra paint the cheapest-path
route proposals charge per cell per instability level of its region (`RolloutBot.propose`,
`extra[c] = round(UNSTABLE_COST × instability[c])`). Candidates `current?UNSTABLE_COST=2` (U2) and
`current?UNSTABLE_COST=3` (U3) on `current` = v12.

## Hypothesis

The 2026-10-20 analysis names this the only own-risk term of the construction (§4, defect 6) and the boss
places 0 % of its cells in regions at instability ≥ 2; our arena record still shows ~45 % of accepted cells
never earning (v12's first 50 games 44.9 %). v12 discounts the *value* of income that depends on unstable
regions; this candidate raises the *cost* of building into them, so the proposal stage prefers detours
earlier. At 3 paint per level a cell at instability 2 costs a whole turn's paint more; at 2 the penalty is
in between. A failed precedent to keep in mind: `USE_ROLLOUT_ALL_CELL_RISK` (v09 era, a per-cell risk on
construction cost, rejected: +6 pp vs the proxy, −12.5 on a 40-game live sample).

## Predeclared screen (`tools/boss_proxy/run_unstable_screen.sh`)

Fresh pool seed **1004000** (first use; 1003000..1003149 was `exposure-income-v1`), 150 maps × 2 seats =
300 paired games per cell, CG 486/36 ms, two workers, the user's machine. The proxy spec carries
`USE_EXPOSURE_INCOME=0` (since the v12 promotion the flag is not dropped by the lite chooser off).

- v5 step (a): `U2-vs-boss2`, `U3-vs-boss2`, `cur-vs-boss2` — the boss-like screening proxy on the same
  maps/seeds/seats as the `current` (= v12) control; **continue only at ≥ control + 3 pp** (paired per-map
  delta, `summarize_screen.py --control cur`).
- v5 step (b): `U2-vs-cur`, `U3-vs-cur` — head-to-head vs the selected version; **≥ 48 %**.
- Zero bot errors; move times in `current`'s band (a weight change, no new step).

Decision rule: keep a value only if its arm passes both steps; the better-passing value goes to `submit` +
strict `procCheck` and then to the live step (80 paired boss games vs `current`, user launch, on another day
because of the daily API volume). Failing values are recorded and the default stays.

## Results (2026-10-25, `docs/evidence/unstable-cost-v1/`, screen 20:29–20:51 UTC)

| cell (300 paired games, pool 1004000) | rate | W-L-D | ms/move cand. | max ms | maps h < 19 | h ≥ 19 |
|---|---|---|---|---|---|---|
| `current` (v12) vs boss-v2 (control) | **78.7 %** | 236-64-0 | 9.4 | 36 | 79.2 % | 77.3 % |
| U2 (`UNSTABLE_COST=2`) vs boss-v2 | 79.0 % | 237-63-0 | 8.3 | 36 | 77.4 % | 83.0 % |
| U3 (`UNSTABLE_COST=3`) vs boss-v2 | 80.7 % | 242-58-0 | 8.4 | 41 | 81.6 % | 78.4 % |
| U2 vs `current` | 52.7 % | 158-142-0 | 9.2 | 39 | 57.1 % | 42.0 % |
| U3 vs `current` | 48.7 % | 146-154-0 | 10.4 | 80 (3 slow, both sides) | 50.5 % | 44.3 % |

Paired per-map deltas vs the control on identical maps/seeds/seats:

| arm | delta vs `current` against boss-v2 | maps better / worse / equal | step (a) ≥ +3 pp | step (b) ≥ 48 % |
|---|---|---|---|---|
| U2 | +0.3 ± 6.6 pp | 39 / 38 / 73 | **fails** | 52.7 % |
| U3 | +2.0 ± 6.8 pp | 43 / 35 / 72 | **fails** | 48.7 % |

Zero bot errors; disrupt rate 98 % everywhere; move times in the control's band (the 80 ms maximum in the U3
head-to-head cell is a machine hiccup on both sides, as in the earlier screens).

**Verdict: both values fail step (a) — parked, `UNSTABLE_COST` stays 1.0.** The construction-side penalty adds
nothing measurable on top of v12's value-side discount against the proxy (+0.3 / +2.0, both inside the cell
error), and head-to-head the stronger penalty loses on tall maps (U3 44 %, U2 42 % at h ≥ 19) — detours cost
real paint on big boards. Together with v12's own result this says the exposure problem is now priced where
it matters (the valuation), and making construction more timid is not the next lever. For the record, v12
itself scored 78.7 % against the proxy on this pool (v11 71.7 % on pool 1003000, v10 64.7 % on 1002000 —
different pools, so not a chain reading).
