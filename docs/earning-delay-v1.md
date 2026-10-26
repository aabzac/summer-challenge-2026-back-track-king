# earning-delay-v1 — routes that earn within a turn or two (2026-10-26)

Two fields in one `// DELAY` merger block, current-only rollout experiments, defaults off; the default artifact (v12,
91,349 chars, config `d7803b42…`) is byte-identical with both off and both leave the configuration at their defaults
(`EarningDelaySubmissionTest`, `EarningDelayTest`):

- `USE_QUICK_ALWAYS` (default false): the cheapest positive completion (≤ `ROLLOUT_QUICK_MAX_PAINT` = 6 paint, a
  positive own income after completion) is kept among the root routes **every turn**, not only before the first
  income as the v06 quick-income slot does (`considerQuick` / `retainQuickRoute`). A candidate-generation change: the
  rollout still decides.
- `DELAY_POWER` (default 1.0): a proposal's value `gain × earning / turns` divides by `turns^DELAY_POWER` instead
  (turns = ceil(cost / 3) to complete), so one-turn completions rank further ahead of long hypotheses in the
  `ROLLOUT_WIDTH` slots. An ordering change: the rollout still decides among the retained routes.

## Hypothesis (from the arena-loss diagnostic, `docs/arena-losses-v12.md`)

The field's top half has two construction traits we lack: multiplicity (`corridor-routes-v1` — passed the proxy,
lost the live step, see `docs/boss-v3.md` for why the proxy cannot see it) and **earning delay: their cells earn 2.2
turns after placement, ours 4.3**; in the K1 live games the same (2.1 vs 3.4–3.6). Our proposals are valued by
`gain × earning / turns`, which already prefers quick routes, but the quick completion is only *retained* while our
income is zero, and a long route with a large gain outranks a cheap completion with a small one. Cells on long
routes wait for the whole route (and 33 of them per game are inked before they earn, `docs/corridor-routes-v1.md`).
Both arms push the construction toward cells that earn at once — tracks the boss values by income, inside the
proxy's competent zone (`docs/proxy-agreement-v1.md`), unlike the corridor concentration.

## Predeclared screen (`tools/boss_proxy/run_delay_screen.sh`)

Fresh pool seed **1011000** (first use; 1010000..1010149 was `boss-v3`'s sign check), 150 maps × 2 seats = 300 paired
games per cell, CG 486/36 ms, two workers, the user's machine; proxy spec with `USE_EXPOSURE_INCOME=0`.

- v5 step (a): `Q-vs-boss2`, `D2-vs-boss2`, `cur-vs-boss2` — the boss-like screening proxy on the same maps/seeds/seats
  as the `current` (= v12) control; **continue only at ≥ control + 3 pp** (paired per-map delta,
  `summarize_screen.py --control cur`).
- v5 step (b): `Q-vs-cur`, `D2-vs-cur` — head-to-head vs the selected version; **≥ 48 %**.
- Arena-like cross-check: `Q-vs-nv6`, `D2-vs-nv6`, `cur-vs-nv6` — neural-v6; read as the paired delta vs `cur-vs-nv6`.

Then, for an arm that passes, v5 step (c): 80 paired live boss games (≥ control + 5 pp), launched under the allow
rule.

## Results (2026-10-26, `docs/evidence/earning-delay-v1/`)

| cell (300 paired games) | rate | W-L-D | paired delta vs `current` (150 maps) | maps better / worse / equal |
|---|---|---|---|---|
| `cur-vs-boss2` | 76.3 % | 229-71-0 | — | — |
| **`Q-vs-boss2`** (`USE_QUICK_ALWAYS`) | **82.7 %** | 248-52-0 | **+6.3 ± 5.6 pp** | 43 / 23 / 84 |
| `D2-vs-boss2` (`DELAY_POWER=2`) | 76.7 % | 230-70-0 | +0.3 ± 6.7 pp | 42 / 39 / 69 |
| **`Q-vs-cur`** | **52.7 %** | 158-142-0 | — (small maps 53.4 %, h ≥ 19: 51.1 %) | — |
| `D2-vs-cur` | 49.7 % | 149-151-0 | — | — |
| `cur-vs-nv6` | 58.7 % | 176-124-0 | — | — |
| `Q-vs-nv6` | 58.7 % | 176-124-0 | +0.0 ± 7.1 pp | 40 / 37 / 73 |
| `D2-vs-nv6` | 63.7 % | 191-109-0 | +5.0 ± 7.7 pp | 49 / 36 / 65 |

Time per move: Q 8.4 ms, D2 8.4 ms, `current` 8.3 ms. 0 errors in 2,400 games.

**Q passes v5 step (a) (+6.3 pp, the tightest paired interval of the line: 84 of 150 maps unchanged, 43 better, 23
worse) and step (b) (52.7 %)**; against neural-v6 it is exactly neutral (+0.0 pp, 40/37). D2 fails (a) (+0.3 pp) and is
parked: steeper ordering of the proposals changes nothing the rollout did not already decide. Q is a
candidate-generation change — the rollout always sees the cheapest positive completion — and its gain is against the
boss-like opponent; the live step decides, as it did for K1.

## Q's artifact, strict process check and the live plan (2026-10-26)

- `gradlew submit "-PsubmitVersion=current?USE_QUICK_ALWAYS=true" -PsubmitOut=build/submit-delay-q` →
  **91,619 / 100,000 chars** (readable), sha `4584fb1d…`, build `a880a04a…`, **config `41996066…`**, label
  `v12 (modified)` (`evidence/earning-delay-v1/Q-artifact-manifest.json`).
- Strict `procCheck` (zero grace, 4 games vs v09): **0 problem games, 0 timeouts**, first response ≤ 725 ms, later
  responses ≤ 49.3 ms (one game; the other three ≤ 38.1 ms) under the 50 ms zero-grace limit; 2/4 wins
  (`evidence/earning-delay-v1/Q-artifact-process-check.json`).
- Live plan for v5 step (c), frozen with no network calls: `build/cg-boss/delay-q-v12-boss-40/plan.json` (copy in
  `evidence/earning-delay-v1/live-plan.json`), id `f426db1c…`, matched design, 40 maps × 2 builds = 80 API games,
  boss 6767608, schedule seed 2026102602, candidate = `build/submit-delay-q`, control = `build/submit-v12` (the
  selected v12 artifact, build `ce1d3a3b…`, the arena entry), bound to the puzzle-IDE session like every series.
  Pass rule as before: Q ≥ v12 + 5 pp over the 40 paired maps. **80 API games were already played today (K1's
  session, 12:04–13:04 UTC)**; a second 80 reaches the day's 160th request, which went through on 2026-10-25 but
  the 2026-10-16 run hit the service limit near its 70th request of a 160-game day — the paced runner stops on an
  API error and the plan is resumable from the first unplayed slot. Launch (Claude, under the allow rule, on the
  user's word):

  ```
  bash tools/run_boss_paced.sh build/cg-boss/delay-q-v12-boss-40/plan.json build/cg-boss/private/v09-ink45-boss-v1/session.json 20 300 20 build/cg-boss/private/v09-ink45-boss-v1/start-session.json 0722a2fe30a43359747729078cfa150d027fc9c6fc7a9e3fbb102210563accba 1172955
  ```

## Live boss, v5 step (c) session 1 (2026-10-26 17:54–18:57 UTC, `evidence/earning-delay-v1/live-session-1/`)

Launched by Claude on the user's "запускай" under the allow rule; the day's 81st–160th API requests all went through.
80 API games, 0 technical failures, 0 authorized retries, **40/40 (Q) and 80/80 (the v12 capture directory, both
sessions) replays exact** (`cgValidate`). **Q 18/40 = 45.0 %, `current` (v12, the arena artifact) 20/40 = 50.0 %**
against boss 6767608 on 40 matched maps; discordant pairs **9 : 11** (20 equal: 9 both won, 11 both lost), paired
**−5.0 pp**, one-sided p = 0.75; seat P0 10/20 vs 10/20, seat P1 8/20 vs 10/20; by map size (Q / v12 points over the
maps): 21×14 1/2 (4), 23×15 3/5 (7), 24×16 3/2 (6), 26×17 3/1 (4), 27×18 5/2 (6), 29×19 1/2 (4), **30×20 2/6 (9)**.
Mean own score Q 2,919 vs v12 3,015, the boss's 3,067 vs 3,058. Runner report: complete, 80 attempted requests,
protocol "original fixed".

**Verdict: Q fails step (c) (−5.0 pp against a +5 pp threshold) — parked; the flag stays in the code, default off.**
Live sessions of the line: D +22.5, B +2.5, E1 +5.0, K1 −15.0, Q −5.0 pp. The second construction trait of the
arena's top half does not transfer to the live boss either: both candidates taken from `docs/arena-losses-v12.md`
gained +6 pp against the boss-v2 proxy and lost against the boss (−15 and −5). Q's loss sits on the tall maps (30×20:
2 vs 6 of 9), where a kept cheap completion displaces a longer route from the six slots. The policy's one
predeclared extension (a second 40-map session, 120 games in all) is open to the user; the prior after −5 pp is low.

**What this closes.** The construction line opened by the arena-loss diagnostic (multiplicity, earning delay) is
exhausted at the proxy's level: the screen passes construction changes the live boss does not reward. Further
construction candidates need either a screening opponent that models the boss's reaction to construction (which
`boss-v3` could not fit) or a live step first. The remaining levers in `docs/PLAN.md` are on the sabotage and
valuation side, where the proxy's sign has held so far.
