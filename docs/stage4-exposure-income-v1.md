# stage4-exposure-income-v1 — income weighted by region survivability (2026-10-25)

Flag `USE_EXPOSURE_INCOME` (+ `EXPOSURE_INCOME_WEIGHT`, default 1.0, must be within [0, 1]), `// EXPOSURE_INCOME`
merger block, current-only rollout experiment (refused on `v11`, `v10`, EvalBot). Default off: the default
artifact (v11, 88,997 chars, config `179dcc66…`) is byte-identical with the flag off and the two fields
leave the configuration while off (`ExposureIncomeSubmissionTest`, `ExposureIncomeTest`).

## Hypothesis (Stage 4 of the 2026-10-20 plan, second half: own exposure, H2)

Both the rollout's terminal value and the route proposals value income with `Rules.incomePerTurn`: one point
per own cell on an active connection, whatever the state of the regions the connection runs through. The
boss inks the region with the most of our tracks in four consecutive hits and places 0 % of its own cells in
regions at instability ≥ 2; our arena record shows 46 % of accepted cells never earning (v11's first 50
games 46.6 %, v10's 46.3 %, D's 40.7 %). Inside the rollout's eight simulated turns the opponent model already
inks the held target explicitly; beyond the horizon (the `ROLLOUT_TAIL` × income term) and at the proposal
stage (completion gain, `protectedGain`) a connection through a region at instability 3 still counts as a
full point per cell per turn.

The candidate replaces those three income readings by a **survivability-weighted income**: for each active
connection, (the player's cells on the path) × Π over the distinct unstable non-town regions on the path of
(1 − w · instability / 4). A connection through one region at instability 3 is worth a quarter of its face
value at w = 1; town regions never ink and are exempt; the discount applies symmetrically to both players, so
the opponent's income in a region we are about to ink is discounted the same way. Effect expected: routes
and tails that depend on doomed regions lose value, bypasses and stable extensions gain it, and the
proposal stage stops queuing completions through regions the boss is already filling. Compute: one extra
O(path length) pass per income reading (stamp-marked regions, allocation-free).

Precedent to keep in mind: `USE_ROLLOUT_ALL_CELL_RISK` (v09 era, a per-cell risk on *construction* cost) was
+6 pp vs the proxy but −12.5 on a 40-game live sample; the proxy under-punishes bypass-heavy construction
(`docs/boss-proxy-v1.md` §6.2). This candidate discounts *income* rather than cost and leaves the construction
rules untouched; the live step is still the decisive one.

## Predeclared screen (`tools/boss_proxy/run_stage4b_screen.sh`)

Fresh pool seed **1003000** (first use; 1002000..1002149 was `stage4-threat-model-v1`), 150 maps × 2 seats =
300 paired games per cell, CG 486/36 ms, two workers, the user's machine. Arms E1 (w = 1.0) and E05 (w = 0.5):

- v5 step (a): `E1-vs-boss2`, `E05-vs-boss2`, `cur-vs-boss2` — the boss-like screening proxy on the same
  maps/seeds/seats as the `current` (= v11) control, same session; **continue only at ≥ control + 3 pp** (paired
  per-map delta, `summarize_screen.py --control cur`).
- v5 step (b): `E1-vs-cur`, `E05-vs-cur` — head-to-head vs the selected version; **≥ 48 %**.
- Zero bot errors; move times in `current`'s band (no new unbounded step: the pass is bounded like
  `incomePerTurn`).

Decision rule: keep a weight only if its arm passes both steps; the better-passing weight goes to `submit` +
strict `procCheck` and then to the live step (80 paired boss games vs `current`, user launch). Any failing arm
is recorded and parked.

## Results (2026-10-25, `docs/evidence/stage4-exposure-income-v1/`, screen 17:26–17:48 UTC)

| cell (300 paired games, pool 1003000) | rate | W-L-D | ms/move cand. | max ms | maps h < 19 | h ≥ 19 |
|---|---|---|---|---|---|---|
| `current` (v11) vs boss-v2 (control) | **71.7 %** | 215-85-0 | 9.6 | 40 | 68.0 % | 83.3 % |
| **E1** (w = 1.0) vs boss-v2 | **75.3 %** | 226-74-0 | 9.7 | 45 | 72.4 % | 84.7 % |
| E05 (w = 0.5) vs boss-v2 | 72.7 % | 218-82-0 | 7.9 | 37 | 73.7 % | 69.4 % |
| **E1 vs `current`** | **60.3 %** | 181-119-0 | 11.1 | 76 (1 slow, both sides) | 59.2 % | 63.9 % |
| E05 vs `current` | 54.0 % | 162-138-0 | 11.1 | 56 (2 slow, both sides) | 53.1 % | 56.9 % |

Paired per-map deltas vs the control on identical maps/seeds/seats:

| arm | delta vs `current` against boss-v2 | maps better / worse / equal | step (a) ≥ +3 pp | step (b) ≥ 48 % |
|---|---|---|---|---|
| **E1** `USE_EXPOSURE_INCOME` (w 1.0) | **+3.7 ± 6.7 pp** | 41 / 31 / 78 | **PASS (at the gate)** | **60.3 % PASS** |
| E05 (w 0.5) | +1.0 ± 7.4 pp | 41 / 40 / 69 | fails | 54.0 % passes |

Zero bot errors; disrupt rate 98.3 % in every arm; mean move time unchanged (the extra income pass costs
nothing measurable). The move-time spikes in the two head-to-head cells (76 / 56 ms, one or two moves of
300 games, on *both* sides of the table) are machine hiccups during the run, not the flag: the proxy cells
show 37–45 ms maxima, and the strict process check below is clean.

**Verdict.** E1 passes v5 steps (a) and (b): +3.7 pp vs the proxy over v11 (just above the +3 pp gate, inside
the cell error) and **60.3 % head-to-head vs v11 — the largest head-to-head gain of the chain** (B over v10 was
57.0 %, D over C 53.7 %). The weight matters: at 0.5 the proxy gain vanishes (+1.0) although the head-to-head
stays positive (54.0 %), so the full discount is the candidate and **E05 is parked**. Tall maps: E1 84.7 % vs
83.3 % against the proxy, 63.9 % head-to-head — no tall-map loss. Mechanism reading: with the full discount a
connection through a region at instability 3 is worth a quarter of its face value in the tail and in the
completion gain, which moves the portfolio toward bypasses and stable extensions; the mirror gain (60 %) is
larger than the proxy gain (+3.7), the same shape as every valuation change so far — the live step decides.

## E1's artifact, strict process check and the live plan (2026-10-25)

- `gradlew submit "-PsubmitVersion=current?USE_EXPOSURE_INCOME=true" -PsubmitOut=build/submit-stage4-e1` →
  **91,368 / 100,000 chars**, sha `3635c212…`, build `582ad892…`, **config `d7803b42…`**, label
  `v11 Root Target (modified)`.
- Strict `procCheck` (zero grace, 4 games vs v09): **0 problem games, 0 timeouts**, first response ≤ 627 ms,
  later responses ≤ 38.5 ms (1/4 wins — four games say nothing about strength;
  `evidence/stage4-exposure-income-v1/E1-artifact-process-check.json`, manifest alongside).
- Live plan for v5 step (c), frozen with no network calls: `build/cg-boss/stage4-e1-v11-boss-40/plan.json`, id
  `db85a30d…`, matched design, 40 maps × 2 builds = 80 API games, boss 6767608, schedule seed 2026102503,
  candidate = `build/submit-stage4-e1`, control = `build/submit-v11` (the selected v11 artifact, build
  `002ca691…`), bound to the puzzle-IDE session like every series. Pass rule as before: E1 ≥ v11 + 5 pp over the
  40 paired maps; the 80-game policy sample completes with a second session. **Recommended for another day**:
  80 API games were already played today (15:05–16:06) and the 2026-10-16 run hit the service limit near its
  70th request of a 160-game day. Launch, by the user on this machine:

  ```
  bash tools/run_boss_paced.sh build/cg-boss/stage4-e1-v11-boss-40/plan.json build/cg-boss/private/v09-ink45-boss-v1/session.json 20 300 20 build/cg-boss/private/v09-ink45-boss-v1/start-session.json 0722a2fe30a43359747729078cfa150d027fc9c6fc7a9e3fbb102210563accba 1172955
  ```
