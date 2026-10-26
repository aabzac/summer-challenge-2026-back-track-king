# opp-hold-v1 — the hold margin of the bot's boss model (2026-10-26)

Field `BOSS_OPP_HOLD_MARGIN` (default −1.0 = inherit `BOSS_HOLD_MARGIN`, must be finite), `// OPP_HOLD` merger block,
current-only experiment of the lite opponent model (`USE_BOSS_LITE` + `USE_BOSS_OPPONENT_MODEL`). At its default the
default artifact (v12, 91,349 chars, config `d7803b42…`) is byte-identical and the field leaves the configuration
(`OppHoldSubmissionTest`, `OppHoldTest`). The existing `BOSS_HOLD_MARGIN` (1.0) needs no code to vary.

## Hypothesis (from `docs/boss-v3.md`)

The lite chooser holds its committed region unless another region's score beats it by `BOSS_HOLD_MARGIN` (1.0, set in
`boss-proxy-v1` from the corpus hold *rate*, never screened on the bot side). On the 80 live games of the K1 session,
the same chooser with **margin 0 picks the live boss's region 58.8 / 62.6 % of the time against 54.1 / 58.1 % at 1.0**
(K1 / v12 games; uniform, every phase): the live boss switches targets more readily than the hold calibration assumed.
The bot uses this chooser twice: for its own real disrupt (`liteHeld`), and, since v10, as the model of the opponent's
disrupt inside every rollout (`USE_BOSS_OPPONENT_MODEL`, `simOppHeld`). A more accurate model of the boss's disrupt is a
valuation change — the rollout prices what the boss will ink next — not a construction change, so it sits on the side
of the line where the proxy's sign has held (D, B, E1).

## Arms

- **O0 = `current?BOSS_OPP_HOLD_MARGIN=0`** — margin 0 only when the chooser plays the simulated opponent (and the parked
  threat model); our own real disrupt keeps 1.0.
- **H0 = `current?BOSS_HOLD_MARGIN=0`** — margin 0 everywhere: our real disrupt re-evaluates every turn too.

## Predeclared screen (`tools/boss_proxy/run_opphold_screen.sh`)

Fresh pool seed **1012000** (first use; 1011000..1011149 was `earning-delay-v1`), 150 maps × 2 seats = 300 paired games
per cell, CG 486/36 ms, two workers, the user's machine.

- v5 step (a): `O0-vs-boss2`, `H0-vs-boss2`, `cur-vs-boss2` — the policy's screening proxy (margin 1.0) on the same
  maps/seeds/seats as the `current` (= v12) control; **continue only at ≥ control + 3 pp**.
- v5 step (b): `O0-vs-cur`, `H0-vs-cur` — head-to-head vs the selected version; **≥ 48 %**.
- Cross-check against the better-agreeing proxy: `O0-vs-boss2m0`, `H0-vs-boss2m0`, `cur-vs-boss2m0` (proxy with
  `BOSS_HOLD_MARGIN=0`), paired delta vs `cur-vs-boss2m0`. An opponent model matched to the margin-0 boss is expected to
  look worse against the margin-1 proxy than it is; if O0 gains against the margin-0 proxy and is neutral against the
  policy proxy, that is recorded and the live step decides with the user.

Then, for an arm that passes, v5 step (c): 80 paired live boss games (≥ control + 5 pp), launched under the allow rule on
the user's word (the 2026-10-26 budget of 160 API games is spent).

## Results, block 1 — margin 0 (2026-10-26, pool 1012000, `docs/evidence/opp-hold-v1/`)

| cell (300 paired games) | rate | W-L-D | paired delta vs `current` (150 maps) | maps better / worse / equal |
|---|---|---|---|---|
| `cur-vs-boss2` | 81.3 % | 244-56-0 | — | — |
| `O0-vs-boss2` (opponent model margin 0) | 73.2 % | 219-80-1 | **−8.2 ± 6.3 pp** | 31 / 50 / 69 |
| `H0-vs-boss2` (margin 0 everywhere) | 72.3 % | 217-83-0 | **−9.0 ± 6.6 pp** | 26 / 46 / 78 |
| `O0-vs-cur` | **45.7 %** | 137-163-0 | — (small maps 47.0 %, h ≥ 19: 40.6 %) | — |
| `H0-vs-cur` | 51.0 % | 153-147-0 | — | — |
| `cur-vs-boss2m0` (proxy with margin 0) | 74.0 % | 222-78-0 | — | — |
| `O0-vs-boss2m0` | 74.5 % | 223-76-1 | +0.5 ± 7.1 pp | 39 / 38 / 73 |
| `H0-vs-boss2m0` | 75.5 % | 226-73-1 | +1.5 ± 6.9 pp | 38 / 34 / 78 |

0 errors in 2,400 games (time per move 8–13 ms in every cell of this run, the control's included).

**Both margin-0 arms fail step (a) with a significant loss** (−8.2 and −9.0 pp, the intervals clear of zero); O0 also
fails step (b) (45.7 %). Against the better-agreeing margin-0 proxy they are neutral (+0.5 / +1.5 pp), so the matched
opponent model buys nothing even against an opponent that really does re-evaluate every turn. The loss is the opponent
model's: H0 (own disrupt at margin 0 too) is within a point of O0. Reading: what the rollout's opponent model is worth
is its *commitment* — a model that holds a region predicts an ink that will land and the search prepares for it; a
model that re-picks every turn spreads the predicted threat over regions it never finishes. A higher agreement with the
boss's region on a turn (+4.5 pp in `boss-v3`) is the wrong objective for the model inside the rollout.

## Block 2 — a stickier opponent model, margin 2 (pool 1013000)

`O2 = current?BOSS_OPP_HOLD_MARGIN=2` against both proxies and head-to-head, with `current` as the same-map control on
a fresh pool (the margin-1 proxy rewards a bot whose model matches it, so the margin-0 proxy is the guard):
`SEED=1013000 OUT=build/analysis/opphold-v1b CELLS="cur-vs-boss2 O2-vs-boss2 O2-vs-cur cur-vs-boss2m0 O2-vs-boss2m0"
bash tools/boss_proxy/run_opphold_screen.sh`. O2 goes to the live step only if it gains against both proxies.

| cell (300 paired games, pool 1013000) | rate | W-L-D | paired delta vs `current` | maps better / worse / equal |
|---|---|---|---|---|
| `cur-vs-boss2` | 78.0 % | 234-66-0 | — | — |
| `O2-vs-boss2` | 78.0 % | 234-66-0 | **+0.0 ± 6.7 pp** | 38 / 36 / 76 |
| `O2-vs-cur` | 51.3 % | 154-146-0 | — (small maps 53.9 %, h ≥ 19: 45.7 %) | — |
| `cur-vs-boss2m0` | 75.0 % | 225-75-0 | — | — |
| `O2-vs-boss2m0` | 78.0 % | 234-66-0 | +3.0 ± 6.3 pp | 37 / 32 / 81 |

O2 fails step (a) (+0.0 pp); the +3.0 pp against the margin-0 proxy is inside the noise; head-to-head neutral. (This run's
time per move was 12–13 ms in the candidate and the margin-0 cells against 8 ms in `cur-vs-boss2`: machine load during
the run, no slow-move excess, the same in control and candidate cells against the same opponent.)

## Verdict

**`opp-hold-v1` closed: the shipped hold margin 1.0 is the optimum of the scan** — margin 0 for the opponent model
−8.2 pp, everywhere −9.0 pp, margin 2 +0.0 pp against the policy proxy; against the margin-0 proxy +0.5 / +1.5 / +3.0
pp, all noise. `BOSS_OPP_HOLD_MARGIN` stays in the code at its default (inherit). The rollout's opponent model is worth
its commitment to a target, not its per-turn agreement with the boss: the better-agreeing margin 0 costs 8 pp, and
`boss-v3`'s agreement metric is not the right objective for the model inside the search.

With this, the cheap screened levers on the sabotage / valuation side are spent too: search width, depth and replies
(`stage2-dividend-v1`, parked on D), the threat model A (`stage4-threat-model-v1`, parked), unstable cost
(`unstable-cost-v1`, parked), collision aversion (rejected), the hold margin (here). Pool 1012000..1012149 and
1013000..1013149 consumed, next ≥ 1014000.
