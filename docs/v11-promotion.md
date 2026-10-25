# v11 promotion — Root Target

The user selected candidate B (`stage4-threat-model-v1`, `current?USE_BOSS_ROOT_TARGET=true`) on
2026-10-25 ("давай продвинем версию на арену") after the same-map proxy screen and the first live boss
session (`docs/stage4-threat-model-v1.md`). This promotes the measured spec exactly as screened,
process-checked and live-tested (artifact `build/submit-stage4-b`, source sha `ea4bfe7a…`, config
`179dcc66…`); it does not retune anything.

**Deviation from policy v5, recorded as the user's decision:** step (c) asks for 80 candidate games
against the live boss at ≥ control + 5 pp. One 40-map session was played (80 API games, candidate and
control fresh in the same session): **B 21/40 = 52.5 % vs `current` (v10) 20/40 = 50.0 %, paired +2.5 pp,
10 : 9 discordant, p = 0.50 — inconclusive**, below the session threshold; the second session was skipped
on the user's explicit instruction, as D's was at the v10 promotion. Steps (a) and (b) were met on fresh
pool 1002000: **73.5 % vs boss-v2 against v10's 64.7 % on the same maps (+8.8 ± 7.1 pp, 54/32/64 maps)**
and **57.0 % head-to-head vs v10**. The promotion therefore rests on the proxy screen and the user's
call; the live evidence is neutral, not supporting.

## Selected policy

v10 Boss Model plus one change inside the rollout: **every root's first simulated own turn disrupts the
real-turn lite target (`liteHeld`)** — the region the real turn actually disrupts — instead of a per-root
`chooseTarget` pick, and the no-disrupt scenario is not searched (the real turn never wastes the point).
The construction is thereby valued under the sabotage line the bot really plays (`// BOSS_ROOT` block,
`RolloutBot.play`, `searchRoots`, `addNetworkRoots`). Side effect: ≈ 28 fewer ink simulations and half the
comparisons per turn, mean move time 7.9 ms vs 11.7 on the screen. Search width/replies/depth and every
weight are v10's (`ROLLOUT_WIDTH` 6, `ROLLOUT_REPLIES` 2, `ROLLOUT_DEPTH` 8, `BOSS_OPPONENT_REPLIES` 2,
`BOSS_HOLD_MARGIN` 1.0, `BOSS_LITE_ROUTE_WEIGHT` 0.371747, `NOISE` 0.1). The sibling flag
`USE_BOSS_THREAT_MODEL` (parked, +1.5 pp) stays off and keeps its block.

## What changed in the repository

- `Params`: `USE_BOSS_ROOT_TARGET` defaults to true. `FrozenParams` keeps it off for every snapshot that
  predates it (v10 included), so `v10` resolves to the same 130 values as before (`v10-confirmed.properties`).
- `btk.bot.v11`: `RolloutBotV11`, `BossSabotageV11`, `ProtectedOpeningV11` — byte copies of `current`
  with only package, class names and the bot label changed (verified by hash with the identity changes
  undone, `V11PromotionTest`). `versions/v11.properties` = the 131 effective values of
  `BotFactory.configuration("current")` = v10's 130 plus the flag = the params of B's artifact manifest.
- `BotFactory`: `v11` registered and selected (`SELECTED_VERSION`, display name "v11 Root Target"); the flag
  lives inside the lite chooser, so **switching the lite chooser off, or a policy that never reads it, drops
  the inherited default silently** while an explicit `USE_BOSS_ROOT_TARGET=1` is still refused (the v10
  rule). Consequences: the boss-v2 proxy spec
  `current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0`
  and the v09 configuration on current (`V09_ON_CURRENT`) are **unchanged**; the v10 configuration on
  current is `current?USE_BOSS_ROOT_TARGET=0` (`LiveTargetExperimentParams.V10_ON_CURRENT`, config
  `f0e507ac…`); `v10?USE_BOSS_ROOT_TARGET=1` is refused (the frozen v10 never had the flag); the 17 pruned
  experiments are refused on `v11` as on `current`.
- `FileMerger`: `-PsubmitVersion=v11` exports the frozen copy readably, pruned like `current` (the copy is
  the pruned algorithm); catalog: `v11` selected, `v10` historical.
- Tests: `V11PromotionTest` (record, frozen-source identity, guards, v10 unchanged), `V11SubmissionTest`
  (default artifact = B's configuration, frozen export, compiles). Tests that measured the v10 configuration
  on `current` now say `V10_ON_CURRENT` (`V10PromotionTest`, `V10SubmissionTest`, `PruneV1Test`,
  `PruneV1SubmissionTest`, `BossThreatRoot*Test`); `D_SPEC` carries `USE_BOSS_ROOT_TARGET=0`. **Every pin of
  a current-based artifact moved** although their tokens did not: since `readable-export-v1` the artifact
  text carries the selected version's display name in `BUILD_MARKER`, so "v10 Boss Model (modified)" became
  "v11 Root Target (modified)" (+1 char): v09-on-current `82f166f3` → `3fe0370e` (75,920 → 75,921), GUARD
  `2c8cc252` → `d4b8b59e`, BASE `fa52a7d7` → `7fa98f9a`, BASE+ink `71258164` → `2554be21`, +38 `7f7d7fd2` →
  `82741e35`, +40 `ea0eae2e` → `29c83f91`, +40+ink `17350f22` → `1e69495a`, the v10 default `e5da0dcb` →
  `3ec21a76` (88,671 → 88,683). Build ids (marker emptied) and config hashes are untouched. Frozen `v10`
  (`9c3b4008…`) and `v09` (`aca0be45…`) keep their bytes.

## Packaging verification (2026-10-25)

- Default `gradlew submit` (`build/submit-v11`): **88,997 / 100,000 chars** (readable), source sha
  `ff111d54…`, build `002ca691…`, **config sha `179dcc66…` = candidate B's artifact** (its text differs
  only in the one initializer literal and the display name: `MESSAGE v11 Root Target | BTK_BUILD_V1
  strategy=current build=002ca691… config=179dcc66…`). Frozen export `-PsubmitVersion=v11`: 88,955 chars,
  sha `b12a1943…`, build `a5b36e2d…`.
- Strict process check of the default artifact vs v09 (4 games, 0 grace, pool `replays/cg`): **0 problem
  games**, 0 timeouts, first response ≤ 577 ms, later responses ≤ 38.7 ms, 3/4 wins
  (`docs/evidence/v11-promotion/default-artifact-process-check.json`, manifests alongside). B's own artifact
  passed the same check earlier today (`docs/evidence/stage4-threat-model-v1/B-artifact-process-check.json`).
- Test suite: 830 tests, **25 failures, all pre-existing** (19 via the retained-engine bytecode pin on
  `btk/core/Pathing.class`, 6 `PduhardBeamBotTest`); everything else green, including the rewritten
  promotion and submission tests.

## State after the promotion

`current` = **v11 Root Target**; frozen copy `btk.bot.v11`; `v10` remains a frozen opponent and the mirror
of the previous selection. **The arena still runs the v10 artifact (agent 6811139, config `f0e507ac…`)
until the user uploads `build/submit-v11/Player.java`** (config `179dcc66…`); after the upload, `cgFetch`
/ `cgAnalyze` of the first 50 games is the next check. Policy v5 for the next candidate: proxy screen with
`current` (= v11) as the same-map control, non-inferiority vs `current`, live vs `current`. Nothing pushed
to the arena by the agent.
