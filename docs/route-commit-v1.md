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

## Results (2026-10-27, `docs/evidence/route-commit-v1/`)

**Step (a), pool 1017000** (300 paired games per cell, `current` as the same-map control):

| cell | rate | W-L-D | paired delta vs `current` (150 maps) | maps better / worse / equal |
|---|---|---|---|---|
| `cur-vs-boss2` | 75.3 % | 226-74-0 | — | — |
| **`C10-vs-boss2`** (bonus 10) | **82.3 %** | 247-53-0 | **+7.0 ± 5.5 pp** | 40 / 21 / 89 |
| `C25-vs-boss2` (bonus 25) | 80.0 % | 240-60-0 | +4.7 ± 5.6 pp | 38 / 24 / 88 |
| **`C10-vs-cur`** | **52.7 %** | 158-142-0 | — (small maps 52.3 %, h ≥ 19: 53.8 %) | — |
| `C25-vs-cur` | 49.0 % | 147-153-0 | — | — |

Time per move unchanged (6.6 / 6.7 ms vs 7.0); 0 errors in 1,500 games. Both arms pass step (a), both pass (b).

**Step 1a, replication on fresh pool 1018000** (`replication/`):

| arm | selection pool | replication | **pooled (300 maps)** | step 1a |
|---|---|---|---|---|
| **C10** | +7.0 ± 5.5 pp | **+2.5 ± 5.1 pp** (28 / 22 / 100) | **+4.8 ± 3.8 pp** | **continue** |
| C25 | +4.7 ± 5.6 pp | +0.7 ± 6.1 pp (37 / 31 / 82) | +2.7 ± 4.1 pp | stop |

(`cur-vs-boss2` 77.7 % on pool 1018000; C10 80.2 %, C25 78.3 %.) **C10 (bonus 10) passes steps (a), 1a and (b)** — the first
replicated proxy gain since v12, its pooled interval clear of zero; C25 stops (parked).

## C10's artifact, strict process check and the live plan

- `gradlew submit "-PsubmitVersion=current?USE_ROUTE_COMMIT=true" -PsubmitOut=build/submit-commit-c10` → **92,813 / 100,000
  chars** (readable), sha `63e26828…`, build `45dc8325…`, **config `f3229a23…`**, label `v12 (modified)`.
- Strict `procCheck` (zero grace, 4 games vs v09): **0 problem games, 0 timeouts**, first response ≤ 618 ms, later ≤ 37.7 ms;
  4/4 wins (four games say nothing about strength).
- Live plan for v5 step (c), frozen with no network calls: `build/cg-boss/commit-c10-v12-boss-40/plan.json` (copy
  `live-plan.json`), id `4cb1514d…`, matched design, 40 maps × 2 builds = 80 API games, boss 6767608, schedule seed
  2026102701, candidate `build/submit-commit-c10`, control `build/submit-v12` (build `ce1d3a3b…`, the arena entry). Pass
  rule: C10 ≥ v12 + 5 pp over the 40 paired maps (the second session completes the 80-game sample). No API games played
  on 2026-10-27 before this plan. Launch (Claude, under the allow rule, on the user's word):

  ```
  bash tools/run_boss_paced.sh build/cg-boss/commit-c10-v12-boss-40/plan.json build/cg-boss/private/v09-ink45-boss-v1/session.json 20 300 20 build/cg-boss/private/v09-ink45-boss-v1/start-session.json 0722a2fe30a43359747729078cfa150d027fc9c6fc7a9e3fbb102210563accba 1172955
  ```

## Live boss, v5 step (c) session 1 (2026-10-27 06:56–07:58 UTC, `evidence/route-commit-v1/live-session-1/`)

Launched by Claude on the user's "Запускай" under the allow rule. 80 API games, 0 technical failures, 0 authorized
retries, **40/40 (C10) and 120/120 (the v12 capture directory, three sessions) replays exact** (`cgValidate`). **C10 18/40
= 45.0 %, `current` (v12, the arena artifact) 22/40 = 55.0 %** against boss 6767608 on 40 matched maps; discordant pairs
**3 : 7** (30 equal), paired **−10.0 pp**, one-sided p = 0.95; seat P0 11/20 vs 12/20, P1 7/20 vs 10/20; by map size
(C10 / v12 points over the maps): 21×14 3/5 (6), 23×15 2/4 (6), 24×16 3/2 (5), 26×17 2/2 (3), 27×18 0/2 (2), 29×19 7/5 (12),
30×20 1/2 (6).

**Reading.** The session fails the +5 pp threshold. Policy v5 decides step (c) on 80 candidate games (a second 40-map
session completes the sample); after −10 pp the second session would need about +20 pp for the pooled sample to reach
+5 pp. The proxy gain is replicated (pooled +4.8 ± 3.8 pp over 300 maps), the live sample is thin: only 10 of the 40
pairs are discordant, and a 3 : 7 split is what an equal pair of bots produces about one time in six. Live sessions of
the line: D +22.5, B +2.5, E1 +5.0, K1 −15.0, Q −5.0, C10 −10.0 pp. **Decision open to the user:** the second session
(80 API games) or park C10 (recommended; the flag stays in the code, default off).
