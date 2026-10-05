# v09 all-cell risk route diagnostic (2026-10-06)

The user authorized returning to v09 after the neural/Indrill reassessment.
Hypothesis: charging instability on existing tracks as well as empty cells can
generate useful bypass actions that the current route portfolio misses.
This is a candidate-generation idea; it does not establish a neural dead end.

## Bounded mechanism check

- Use the existing, hash-verified `reply-study-v1/capture` pre-turn positions.
  Select the first 64 eligible positions in its stable loading order, or all if
  fewer qualify. Eligibility uses only the current board: a live canonical
  connection earns on our tracks and traverses an unstable, unprotected region.
  Recorded future actions and game outcomes do not select or evaluate proposals.
- Keep v09's parameters, except `NOISE=0` for reproducible diagnosis. Use the
  current implementation with ample time to inspect its complete portfolio;
  do not interpret this as nominal timing or game-strength evidence.
- For at most 64 qualifying active pairs per state in index order, query one
  all-cell-risk path and one matched empty-cell-risk control with the same
  tactical path finder. This separates risk on existing tracks from differences
  in path tie-breaking. Entry cost is the existing paint cost (zero on existing
  tracks/towns) plus `round(UNSTABLE_COST * instability)` on all cells. Reuse
  `btk.core.TacticalPathing`; canonical scoring/pathing is unchanged. Inspect
  affordable prefixes from both ends, with no weight sweep. Report the two
  query counts separately.
- Record new legal construction sets, current completion/threat-evaluation
  rejection, portfolio competition, and existing-rollout value where safely
  inspectable. All scoring/destruction/transitions use `btk.core`. Verify input
  isolation. A diagnostic hypothetical ink is not a predicted actual reply.
- Retain one report with input/source identities and per-position results.
  Do not create per-position artifacts or a persistence/audit framework.

If the mechanism supplies no usable additional candidates, stop and record the
negative result. If it supplies candidates worth testing, implement one separate
default-off route proposal under `Params`, keeping the existing evaluator,
opponent model and time budget. Check actual candidate coverage and exact-artifact
readiness before the strength comparison. Do not force defense or change the
evaluation weights in the same experiment.

## Conditional strength check

Candidate: `current?USE_ROLLOUT_ALL_CELL_RISK=1`, with every other current default
unchanged. The flag defaults false. Only our qualifying live connections receive
variant 3, after the existing three proposal types for each pair. It competes in
the ordinary six-route portfolio and continues that route inside rollouts.
The existing total query cap, time reserve, depth, replies and evaluation remain;
the extra queries may displace later old queries under that unchanged budget.

Fresh block seeds are 4500000 / 4600000 / 4700000 under experiment id
`rollout-risk-v1`, with the existing runner's prior-map exclusion checks. The
diagnostic capture uses seeds 20000..20005 and is development data. Use the existing
practical-v4 300/+200/+500 comparison against frozen v09, two workers, futility-only
looks below 50% at 300/500 and the final 55% threshold at 1000. Preserve engine,
errors and process checks. Build to `build/submit-rollout-risk-v1`; keep the selected
saved submission. The strict process panel is exactly eight games against v09,
both seats on maps 903510176, 904146009, 904148181 and 904147739, zero grace,
serially without other CPU-heavy work. Preserve failures; no retry for a pass.
Local failure ends evaluation. A local survivor still
requires the boss stage and valid control cache under the existing policy; any
IDE write must be within the user's authorization. No automatic strategy promotion.

## Result

The saved-state check completed: 64 selected from 91 eligible / 110 available
positions. All-cell risk produced 30 retained new filled construction sets in 15
positions; the matched empty-only controls produced none. Existing isolated-root
rollouts evaluated candidates in all 15; five exceeded the saved baseline value,
including a duplicated board. These are correlated development examples and
modelled values, not wins or a strength rate. Legality/input-isolation checks and
nine targeted tests pass; the baseline engine reproduces 292/292 CG games.

This justified the single conditional candidate above. The mechanism report's
protocol hash identifies the pre-run registration, before this result/strength
registration was appended. The report is not rerun or reinterpreted as a strength
test.

The integrated policy changed the actual construction/disruption action in 6/64
saved positions with ample time and `NOISE=0`. The nominal-budget candidate differed
from its ample-time result in 31/64 positions; this has no matched nominal baseline
and is not evidence of a performance regression or improvement. Maximum ample-time
query count was 128; legality and input isolation passed.

Exact-artifact readiness passed: merged candidate 99,733/100,000 characters,
strict process panel 8/8 with no timeout or nominal overrun (maximum first response
649.096401 ms; later response 43.969299 ms). The full suite ran 474 tests: 471
passed, with three old parameter-inventory expectations failing on the new
default-off field. Those expectations were corrected; all ten tests in the three
affected classes passed on the targeted rerun. Other coverage, including frozen
identity, default-off parity, merger and new mechanism tests, passed in the full
run. Independent read-only review found no blocking issues.

The fixed practical-v4 comparison completed, with wins/losses/draws from the
candidate's perspective:

- Block 1, seed 4500000, 300 games: 154/136/10, 53.0% score.
- Block 2, seed 4600000, 200 games: 104/80/16, 56.0%; pooled 500 score 54.2%.
- Block 3, seed 4700000, 500 games: 260/218/22, 54.2%.
- Total: **518 wins / 434 losses / 48 draws, 542/1000 points (54.2%)**.

All 500 paired maps are distinct across blocks and excluded from the prior local
map index; both players have zero reported game errors. Two workers and the
486/36 ms CG search budgets were retained throughout. No tuning, retries or
additional games followed the result.

Decision: **NOT_PASSED**, because the final score is below the predeclared 55%
threshold. This is an observed advantage in this local sample, not established
arena improvement. The boss stage is `SKIPPED_LOCAL_FAILURE`; no CG write, arena
upload or promotion occurred. The new flag remains false, and selected v09 and
the saved default submission remain unchanged.

[Public evidence](evidence/rollout-risk-v1.json) records artifact identities and
the final decision. The [evidence archive](evidence/rollout-risk-v1-artifacts.zip)
contains 55 members (2,706,798 bytes): the fixed reports, preflight registration,
map pools, exact candidate/control submissions and supporting sources. Its SHA256
is `f8fd71093913d911b357ad5a2b35642003087626cdd197b400d26c78a8117cd0`.
