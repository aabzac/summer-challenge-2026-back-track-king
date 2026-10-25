# v12 promotion — Exposure Income

The user selected candidate E1 (`stage4-exposure-income-v1`, `current?USE_EXPOSURE_INCOME=true`) on
2026-10-25 ("Промоушн 12") after the same-map proxy screen and the first live boss session
(`docs/stage4-exposure-income-v1.md`). This promotes the measured spec exactly as screened,
process-checked and live-tested (artifact `build/submit-stage4-e1`, source sha `3635c212…`, config
`d7803b42…`); it does not retune anything.

**Deviation from policy v5, recorded as the user's decision:** step (c) asks for 80 candidate games
against the live boss at ≥ control + 5 pp. One 40-map session was played (80 API games, candidate and
control fresh in the same session): **E1 20/40 = 50.0 % vs `current` (v11) 18/40 = 45.0 %, paired +5.0 pp,
8 : 6 discordant, p = 0.40 — the session threshold met exactly**, the gain on small maps and the eight tall
maps 1 : 4 against E1; the second session was skipped on the user's explicit instruction, as at the v10 and
v11 promotions. Steps (a) and (b) were met on fresh pool 1003000: **75.3 % vs boss-v2 against v11's 71.7 %
on the same maps (+3.7 ± 6.7 pp, 41/31/78 maps)** and **60.3 % head-to-head vs v11**, the chain's largest
head-to-head gain. The live evidence is at the edge of the rule, not beyond it.

## Selected policy

v11 Root Target plus one valuation change: **every active connection's income is weighted by the
survivability of the unstable non-town regions its path crosses**, (1 − instability / 4) each, in the
rollout's `ROLLOUT_TAIL` term and in the route proposals' completion gain and `protectedGain`
(`RolloutBot.exposureIncome`, `// EXPOSURE_INCOME` block; `EXPOSURE_INCOME_WEIGHT` 1.0 — the half weight
lost the proxy gain and is parked). Search width/replies/depth and every other weight are v11's; the
construction rules are untouched. Sibling flags parked: `USE_BOSS_THREAT_MODEL`.

## What changed in the repository

- `Params`: `USE_EXPOSURE_INCOME` defaults to true (weight 1.0 unchanged). `FrozenParams` keeps the two
  fields at their off values for every snapshot that predates them (v11 included), so `v11` resolves to the
  same 131 values as before (`v11-confirmed.properties`).
- `btk.bot.v12`: `RolloutBotV12`, `BossSabotageV12`, `ProtectedOpeningV12` — byte copies of `current` with
  only package, class names and the bot label changed (`V12PromotionTest` hashes). `versions/v12.properties`
  = the 133 effective values of `BotFactory.configuration("current")` = v11's 131 plus the flag and its
  weight = the params of E1's artifact manifest.
- `BotFactory`: `v12` registered and selected (`SELECTED_VERSION`, display name `v12`); a policy that never
  reads the flag (EvalBot, heuristic, neural proposal) or a frozen version drops the inherited default
  silently, an explicit `=1` is refused (the v10/v11 rule); the v11 configuration on current is
  `current?USE_EXPOSURE_INCOME=0` (`LiveTargetExperimentParams.V11_ON_CURRENT`, config `179dcc66…`);
  `v11?USE_EXPOSURE_INCOME=1` is refused; the 17 pruned experiments are refused on `v12` as on `current`.
  **The boss-v2 proxy spec written as `current?USE_BOSS_SABOTAGE=1,` + the seven v10 offs now carries the
  v12 flag** (the discount does not depend on the lite chooser): the test helper `V10_OFF` switches it off
  explicitly, and the screen scripts' `BOSS2` spec must add `USE_EXPOSURE_INCOME=0` for the proxy to stay the
  validated opponent — recorded in `docs/boss-proxy-v1.md`'s successor note below and in STATUS.
- `FileMerger`: `-PsubmitVersion=v12` exports the frozen copy readably, pruned like `current`; catalog:
  `v12` selected, `v11` historical.
- Tests: `V12PromotionTest` (record, frozen-source identity, guards, v11 unchanged), `V12SubmissionTest`
  (default artifact = E1's configuration, frozen export, compiles); tests that measured the v11
  configuration say `V11_ON_CURRENT` (`V11PromotionTest`, `V11SubmissionTest`, `ExposureIncome*Test`,
  `BossThreatRoot*Test` with their specs carrying the flag off); `B_SPEC` and `D_SPEC` carry
  `USE_EXPOSURE_INCOME=0`; `BotCatalogTest` expects `v12`. Every current-based artifact pin moved with the
  label (`v12 (modified)`): v09-on-current `a1043347` → `9ab8ebac`, GUARD `a7001517` → `80245370`, BASE
  `f8595a4a` → `d3b3c7cd`, BASE+ink `50bd16a0` → `0920dbc3`, +38 `77fb8cb9` → `680b0d8e`, +40 `bb40cea3` →
  `d2fd0db2`, +40+ink `572aa79b` → `89d5d9ff`, v10-on-current `f8754bca` → `ec331323`, v11-on-current
  (the previous default) `cf1972a1` / build `35176f24` → `48c92aab` / `0b73d984` (88,990 → 89,012). Frozen
  `v11` (`b12a1943…`), `v10` (`9c3b4008…`) and `v09` (`aca0be45…`) keep their bytes.

## Packaging verification (2026-10-25)

- Default `gradlew submit` (`build/submit-v12`): **91,349 / 100,000 chars** (readable), source sha
  `109be1f3…`, build `ce1d3a3b…`, **config sha `d7803b42…` = candidate E1's artifact** (its text differs only
  in the initializer literal and the label: `MESSAGE v12 | BTK_BUILD_V1 strategy=current build=ce1d3a3b…
  config=d7803b42…`, header `// BTK strategy: current, v12`). Frozen export `-PsubmitVersion=v12`: 91,314
  chars, sha `c89aa7ad…`, build `e6902a6c…`.
- Strict process check of the default artifact vs v09 (4 games, 0 grace, pool `replays/cg`): **0 problem
  games**, 0 timeouts, first response ≤ 650 ms, later responses ≤ 37.5 ms, 3/4 wins
  (`docs/evidence/v12-promotion/default-artifact-process-check.json`, manifests alongside). E1's own
  artifact passed the same check before the live series.
- Test suite: 837 tests, **25 failures, all pre-existing** (19 via the retained-engine bytecode pin on
  `btk/core/Pathing.class`, 6 `PduhardBeamBotTest`).

## State after the promotion

`current` = **v12 Exposure Income**; frozen copy `btk.bot.v12`; `v11` remains a frozen opponent and the
mirror of the previous selection. **The arena still runs the v11 artifact (agent 6813813, config
`179dcc66…`) until the user uploads `build/submit-v12/Player.java`** (config `d7803b42…`); after the upload,
`cgFetch` / `cgAnalyze` of the first 50 games. Policy v5 for the next candidate: proxy screen with `current`
(= v12) as the same-map control — **the proxy spec gains `USE_EXPOSURE_INCOME=0`** — non-inferiority vs
`current`, live vs `current`. Nothing pushed to the arena by the agent.
