# route-commit-v1 — finish the route you started (2026-10-27, construction line, step 3)

Flag `USE_ROUTE_COMMIT` (+ `COMMIT_BONUS`, default 10.0 rollout-value points, finite and ≥ 0), `// COMMIT` merger block,
current-only rollout experiment, default off; the default artifact (v12, 91,349 chars, config `d7803b42…`) is
byte-identical with it off and both fields leave the configuration (`RouteCommitTest`, `RouteCommitSubmissionTest`;
field-count pins +2).

## Hypothesis (`docs/route-order-v1.md`)

v12's completions take four turns longer than their paint requires (opponents one to two): it starts a route, switches
to another the next turn, and finishes the first later; 30 % of a completion's cells wait four or more turns for income
(opponents 13 %). Each turn the search re-plans from scratch, and another route's 3-cell prefix can outscore the
continuation of the route already started, although the started route's remaining cost is lower.

## Mechanism

After each turn (`actions`), the bot records the unbuilt cells of the route it chose (`recordCommitment`: the chosen
ordinary route's path cells still buildable after this turn's placements; a turn without an ordinary route — opening,
network root, nothing — clears it). On the next turn only, a root that places at least one cell on those cells gets
`COMMIT_BONUS` added to its rollout value in `searchRoots` (`onCommitted`). The rollout still values every root with
the same scenarios: the bonus breaks near-ties toward continuation and does not force it. Warm-up turns never carry a
commitment over (cleared at turn 0). Cost: one membership test per root.

Arms: C10 (bonus 10, about two-thirds of a turn of mid-game income lead) and C25.

## Predeclared screen (`tools/boss_proxy/run_commit_screen.sh`)

Fresh pool seed **1017000** (first use; 1016000..1016149 was `threat-weight-v1`), 150 maps × 2 seats = 300 paired games
per cell, CG 486/36 ms, two workers.

- v5 step (a): `C10-vs-boss2`, `C25-vs-boss2`, `cur-vs-boss2` — the policy proxy with `current` (= v12) as the same-map
  control; continue only at ≥ control + 3 pp.
- v5 step (b): `C10-vs-cur`, `C25-vs-cur` — head-to-head; ≥ 48 %.
- Step 1a for an arm that passes (a): `tools/boss_proxy/run_replication.sh` on fresh pool 1018000; continue only at a
  positive replication delta and a pooled delta ≥ +3 pp. Then the artifact, the strict process check and the live step.

## Results

(filled in when the screen completes — `docs/evidence/route-commit-v1/`)
