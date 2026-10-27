# bypass-routes-v1 — relocate threatened connections in the middle game (2026-10-27, construction line)

Flag `USE_BYPASS_ROUTES` (+ `BYPASS_MIN_INSTABILITY`, default 2, in 1..3; `BYPASS_WEIGHT`, default 0.5, in [0, 1]),
`// BYPASS` merger block, current-only rollout experiment, default off; the default artifact (v12, 91,349 chars, config
`d7803b42…`) is byte-identical with it off and the three fields leave the configuration (`BypassRoutesTest`,
`BypassRoutesSubmissionTest`; field-count pins +3). The user's "Давай обходы" after `late-game-v1`.

## Hypothesis (`docs/late-game-v1.md`)

v12's arena losses are decided in the middle third (median gap −44 at 0.2 L → −412 at 2/3 L). There its paint earns 35 %
against the opponents' 62 %; 43 % of it is filler that earns 9 %, and it puts 9 % on bypasses around unstable regions against
the opponents' 20 % (wins: 18 vs 23 %; Indrill's bypass cells earn 79 % against his opponents' 63 %). The route classes are
board properties, so both sides face the same bypass opportunities. v12 proposes a bypass only around the single region its
threat model predicts the opponent will disrupt next (`propose` variant 2): the instability surcharge applies to free cells,
so an active pair's cheapest path always runs over its existing rails and never re-routes.

## Mechanism

After the ordinary and corridor proposals, for each pair (same rotating start, at most 16 path queries, the same reserve as
the ordinary proposals) whose active path holds at least one own track and crosses non-town regions at instability ≥
`BYPASS_MIN_INSTABILITY`: the cheapest route between its towns with every such region of the path blocked (the usual
instability surcharge on free cells, other players' rails allowed). If it needs paint and pays inside the 16-turn horizon,
its value is `(1 − BYPASS_WEIGHT) × plain gain + BYPASS_WEIGHT × protected gain`, where the plain gain is the income
difference with the route built minus the current one and the protected gain is the income difference after those regions
are inked with the route built minus the same inks without it (exposure-discounted income, as v12 values every route). A
positive value enters the bounded proposal pool like any route (both ends); the rollout then decides as usual. Own
proposals only; the opponent's portfolio is unchanged.

Arms: **B2** (instability ≥ 2: two disrupts from ink) and **B3** (≥ 3: one disrupt from ink), weight 0.5 (= the threat weight).

## Predeclared screen (`tools/boss_proxy/run_bypass_screen.sh`)

Fresh pool seed **1019000** (first use; 1017000 / 1018000 were `route-commit-v1`), 150 maps × 2 seats = 300 paired games per
cell, CG 486/36 ms, two workers.

- v5 step (a): `B2-vs-boss2`, `B3-vs-boss2`, `cur-vs-boss2` — the policy proxy with `current` (= v12) as the same-map
  control; continue only at ≥ control + 3 pp.
- v5 step (b): `B2-vs-cur`, `B3-vs-cur` — head-to-head; ≥ 48 %.
- Step 1a for an arm that passes (a): `tools/boss_proxy/run_replication.sh` on fresh pool 1020000; continue only at a
  positive replication delta and a pooled delta ≥ +3 pp. Then the artifact, the strict process check and the live step.

## Results (2026-10-27, `docs/evidence/bypass-routes-v1/`)

**Step (a) + (b), pool 1019000** (300 paired games per cell, `current` as the same-map control; screen 09:21–09:53 UTC):

| cell | rate | W-L-D | paired delta vs `current` (150 maps) | maps better / worse / equal |
|---|---|---|---|---|
| `cur-vs-boss2` | 79.3 % | 238-62-0 | — | — |
| **`B2-vs-boss2`** (instability ≥ 2) | **82.3 %** | 247-53-0 | **+3.0 ± 5.3 pp** | 32 / 23 / 95 |
| `B3-vs-boss2` (instability ≥ 3) | 83.0 % | 249-51-0 | +3.7 ± 5.1 pp | 29 / 20 / 101 |
| **`B2-vs-cur`** | **55.0 %** | 165-135-0 | — (h < 19: 55.8 %, h ≥ 19: 53.3 %) | — |
| `B3-vs-cur` | 46.7 % | 140-160-0 | — | — |

0 errors in 1,500 games; time per move in the control's band (15.2–16.0 ms here against 15.5 for `current`; the machine ran
about twice this morning's per-move times for every cell alike). Both arms pass step (a); **B3 fails step (b) (46.7 % < 48 %)
→ stops**; B2 passes (a) at the threshold and (b).

**Step 1a, replication on fresh pool 1020000** (`replication/`, 09:54–10:06 UTC): `cur-vs-boss2` 78.7 %, `B2-vs-boss2`
84.0 % → **+5.3 ± 5.9 pp** (40 / 26 / 84); **pooled over 300 maps +4.2 ± 4.0 pp** → step 1a passes (positive replication,
pooled ≥ +3 pp). **B2 passes steps (a), 1a and (b).**

## B2's artifact, strict process check and the live plan

- `gradlew submit "-PsubmitVersion=current?USE_BYPASS_ROUTES=true" -PsubmitOut=build/submit-bypass-b2` → **94,839 / 100,000
  chars** (readable), sha `1e7c4e92…`, build `f62155d3…`, **config `8c4b3d6d…`**, label `v12 (modified)`.
- Strict `procCheck` (zero grace, 4 games vs v09): **0 problem games, 0 timeouts**, first response ≤ 657 ms, later ≤ 38.9 ms;
  3/4 wins (four games say nothing about strength).
- Live plan for v5 step (c), frozen with no network calls: `build/cg-boss/bypass-b2-v12-boss-40/plan.json` (copy
  `live-plan.json`), id `6063fbb0…`, matched design, 40 maps × 2 builds = 80 API games, boss 6767608, schedule seed
  2026102702, 1,278 earlier maps excluded, candidate `build/submit-bypass-b2`, control `build/submit-v12` (build `ce1d3a3b…`,
  the arena entry). Pass rule: B2 ≥ v12 + 5 pp over the 40 paired maps (a second session completes the 80-game sample).
  Launch (Claude, under the allow rule, on the user's word):

  ```
  bash tools/run_boss_paced.sh build/cg-boss/bypass-b2-v12-boss-40/plan.json build/cg-boss/private/v09-ink45-boss-v1/session.json 20 300 20 build/cg-boss/private/v09-ink45-boss-v1/start-session.json 0722a2fe30a43359747729078cfa150d027fc9c6fc7a9e3fbb102210563accba 1172955
  ```

## Live boss, v5 step (c) session 1 (2026-10-27 10:41–12:58 UTC, `evidence/bypass-routes-v1/live-session-1/`)

Launched by Claude on the user's "запускай" under the allow rule. 80 API games, 0 authorized retries, runner report complete
with no issues, **40/40 (B2) and 160/160 (the v12 capture directory, four sessions) replays exact** (`cgValidate`). One
interruption: at 11:28 UTC the read-only canonical lookup of slot 29 (game 912366310, already played, its play response saved)
failed and the runner stopped after 28 results; the same command resumed it at 12:17 UTC and fetched slot 29 without a new
play (the documented recovery, `docs/boss-api.md`); no slot was played twice.

**B2 24/40 = 60.0 %, `current` (v12, the arena artifact) 21/40 = 52.5 %** against boss 6767608 on 40 matched maps; discordant
pairs **5 : 2** (33 equal), paired **+7.5 pp** (threshold +5.0), one-sided p = 0.23; seat P0 10/20 vs 9/20, P1 14/20 vs 12/20;
by map size (B2 / v12 points over the maps): 21×14 6/6 (9), 23×15 4/3 (7), 24×16 2/1 (3), 26×17 3/3 (5), 27×18 3/2 (6),
29×19 5/5 (7), 30×20 1/1 (3).

**Reading.** The session passes the +5 pp threshold, on 7 discordant pairs. Live sessions of the line: D +22.5, B +2.5, E1 +5.0
(both promoted), K1 −15.0, Q −5.0, C10 −10.0, **B2 +7.5**. Policy v5 decides step (c) on 80 candidate games; a second 40-map
session completes the sample (today already used 160 API games, so a second session belongs to another day). **Decision open
to the user:** promote B2 to v13 now (as E1 was after its first session), or play session 2 first.
