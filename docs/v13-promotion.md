# v13 promotion — Bypass Routes

The user selected candidate B2 (`bypass-routes-v1`, `current?USE_BYPASS_ROUTES=true`) on 2026-10-27 ("Делаем v13") after
the same-map proxy screen, its replication and the first live boss session (`docs/bypass-routes-v1.md`). This promotes the
measured spec exactly as screened, process-checked and live-tested (artifact `build/submit-bypass-b2`, source sha
`1e7c4e92…`, config `8c4b3d6d…`); it does not retune anything.

**Deviation from policy v5, recorded as the user's decision:** step (c) asks for 80 candidate games against the live boss at
≥ control + 5 pp. One 40-map session was played (80 API games, candidate and control in the same session): **B2 24/40 =
60.0 % vs `current` (v12) 21/40 = 52.5 %, paired +7.5 pp, 5 : 2 discordant, p = 0.23 — above the session threshold**; the
second session was skipped on the user's explicit choice (offered: session 2 first, recommended; or v13 now), as at the v10,
v11 and v12 promotions. Steps (a), 1a and (b): **82.3 % vs boss-v2 against v12's 79.3 % on pool 1019000 (+3.0 ± 5.3 pp),
replicated on pool 1020000 (+5.3 ± 5.9 pp; pooled +4.2 ± 4.0 pp over 300 maps)**, **55.0 % head-to-head vs v12**. The live
evidence rests on seven discordant pairs.

## Selected policy

v12 Exposure Income plus one construction change: **after the ordinary proposals, every own earning connection whose path
crosses non-town regions at instability ≥ 2 gets a proposal of the cheapest route that avoids all of them**, valued half by
the plain income gain and half by the income difference after those regions are inked with the route built against the same
inks without it (exposure-discounted; ≤ 16 path queries per turn under the ordinary reserve; own proposals only;
`RolloutBot.proposeBypasses`, `// BYPASS` block, `BYPASS_MIN_INSTABILITY` 2, `BYPASS_WEIGHT` 0.5). The rollout values these
roots like any route. Search width/replies/depth and every other weight are v12's. Sibling arm parked: B3 (instability ≥ 3,
h2h 46.7 %).

## What changed in the repository

- `Params`: `USE_BYPASS_ROUTES` defaults to true (instability 2, weight 0.5 unchanged). `FrozenParams` keeps the three fields at
  their off values for every snapshot that predates them (v12 included), so `v12` resolves to the same 133 values as before.
- `btk.bot.v13`: `RolloutBotV13`, `BossSabotageV13`, `ProtectedOpeningV13` — byte copies of `current` with only package, class
  names and the bot label changed (`V13PromotionTest` hashes). `versions/v13.properties` = the 136 effective values of
  `BotFactory.configuration("current")` = v12's 133 plus the flag and its two parameters = the params of B2's artifact manifest.
- `BotFactory`: `v13` registered and selected (`SELECTED_VERSION`, display name `v13`); a policy that never reads the flag
  (EvalBot, heuristic, neural proposal) or a frozen version drops the inherited default silently, an explicit `=1` is refused
  (the v10–v12 rule); `v12?USE_BYPASS_ROUTES=1` is refused; the v12 configuration on current is `current?USE_BYPASS_ROUTES=0`
  (`LiveTargetExperimentParams.V12_ON_CURRENT`, config `d7803b42…`; `BotFactory.V13_FLAGS_OFF`). **The boss-v2 proxy spec must
  add `USE_BYPASS_ROUTES=0`**: since the promotion a `USE_BOSS_SABOTAGE=1` spec with the bypass flag on is refused (the proxy
  was validated without it), so an old proxy string fails loudly instead of measuring a different opponent. The proxy spec is
  now `current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,`
  `USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_EXPOSURE_INCOME=0,USE_BYPASS_ROUTES=0`
  (`tools/boss_proxy/run_replication.sh` updated; the earlier one-off screen scripts keep their historical strings and are
  refused if re-run).
- `FileMerger`: `-PsubmitVersion=v13` exports the frozen copy readably, pruned like `current`; catalog: `v13` selected, `v12`
  historical.
- Tests: `V13PromotionTest` (record, frozen-source identity, guards, v12 unchanged, the proxy refusal), `V13SubmissionTest`
  (default artifact = B2's configuration, frozen export, compiles); the v12 tests measure `V12_ON_CURRENT`; `E_SPEC`, `B_SPEC`,
  `D_SPEC`, `V09_ON_CURRENT`, `V10/V11_ON_CURRENT` carry `USE_BYPASS_ROUTES=0`; `BossNoClose*`, `BossThreatRootSubmissionTest`
  proxy/v11 specs likewise; `BypassRoutes*Test` expect the default on; `BotCatalogTest` expects `v13`. Every current-based
  artifact pin moved with the label (`v13 (modified)`); for each pinned configuration the pre- and post-promotion artifacts were
  merged from the last commit and from the new tree and differ **only** in the marker's label and build id and the header
  comment (config hashes unchanged): v09-on-current `9ab8ebac` → `92d50888`, GUARD `80245370` → `1fa974e7`, BASE `d3b3c7cd` →
  `a04462ae`, BASE+ink `0920dbc3` → `61121860`, +38 `680b0d8e` → `cbde7814`, +40 `d2fd0db2` → `1daea8e0`, +40+ink `89d5d9ff` →
  `ea3cbfc6`, v10-on-current `ec331323` → `110cf9f2`, v11-on-current `48c92aab` / build `0b73d984` → `b0aa036b` / `69906fdc`,
  v12-on-current (the previous default) `109be1f3` / build `ce1d3a3b` → `bfd02a63` / `d1ae6759` (91,349 → 91,371). Frozen `v12`
  (`c89aa7ad…`), `v11` (`b12a1943…`), `v10` (`9c3b4008…`) and `v09` (`aca0be45…`) keep their bytes.

## Packaging verification (2026-10-27)

- Default `gradlew submit` (`build/submit-v13`): **94,816 / 100,000 chars** (readable), source sha `3d3e861d…`, build
  `fb92323e…`, **config sha `8c4b3d6d…` = candidate B2's artifact** (its text differs only in the initializer literal and the
  label: `MESSAGE v13 | BTK_BUILD_V1 strategy=current build=fb92323e… config=8c4b3d6d…`, header `// BTK strategy: current, v13`).
  Frozen export `-PsubmitVersion=v13` (`build/submit-v13-frozen`): 94,781 chars, sha `7b7dac9a…`, build `dfc845db…`.
- Strict process check of the default artifact vs v09 (4 games, 0 grace): **0 problem games**, 0 timeouts, first response
  ≤ 573 ms, later responses ≤ 43.2 ms, 4/4 wins (`docs/evidence/v13-promotion/default-artifact-process-check.json`, manifests
  alongside). B2's own artifact passed the same check before the live series.
- Test suite: 863 tests, **25 failures, all pre-existing** (19 via the retained-engine bytecode pin on `btk/core/Pathing.class`,
  6 `PduhardBeamBotTest`).

## State after the promotion

`current` = **v13 Bypass Routes**; frozen copy `btk.bot.v13`; `v12` remains a frozen opponent and the mirror of the previous
selection. **The arena still runs the v12 artifact (agent 6814073, config `d7803b42…`) until the user uploads
`build/submit-v13/Player.java`** (config `8c4b3d6d…`); after the upload, `cgFetch` / `cgAnalyze` of the first games. Policy v5 for
the next candidate: proxy screen with `current` (= v13) as the same-map control — **the proxy spec gains `USE_BYPASS_ROUTES=0`** —
step 1a replication, non-inferiority vs `current`, live vs `current`. Nothing pushed to the arena by the agent.
