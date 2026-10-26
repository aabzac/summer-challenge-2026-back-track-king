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

## Results

(filled in when the screen completes — `docs/evidence/opp-hold-v1/`)
