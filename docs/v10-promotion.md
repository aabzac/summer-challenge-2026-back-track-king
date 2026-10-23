# v10 promotion — Boss Model

The user selected D (`stage2-compute-v1`) on 2026-10-23 ("Давай продвинем D без 2 сессии") after the
first live boss session: 18/40 against v09's 9/40 in the same IDE session, paired +22.5 pp
(`docs/stage2-d-live-boss-v1.md`). This promotes the measured spec
`current?NOISE=0.1,USE_BOSS_LITE=true,USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true,USE_BOSS_LITE_ROUTES=true,USE_INCREMENTAL_INK_REFRESH=true,USE_FAST_CHEAPEST_PATH=true,USE_BATCHED_REFRESH=true`
exactly as uploaded to the arena on 2026-10-21 (artifact `build/submit-stage2-d`, source sha `0e4050c4…`,
puzzle agent 6807052); it does not retune anything.

**Deviation from policy v5, recorded as the user's decision:** step (c) asks for 80 candidate games
(40 on the cached control's maps plus 40 on a second pool with a fresh control). Only the first
40-map session was played (80 API games, candidate and control both fresh in the same session);
the second session was skipped on the user's explicit instruction. Steps (a) and (b) were met along
the chain v2 → B → C → D on the same maps (each link ≥ its predecessor; D 67.0 % vs v09, 65.0 % vs
boss-v2), not by a single 300-game non-inferiority run of D against v09 — the chain is treated as
one candidate, as `docs/stage2-compute-v1.md` anticipated.

## Selected policy

v09 Never Idle plus: the two-stage boss-like chooser at the real turn (`USE_BOSS_LITE`,
`docs/boss-sabotage-v2.md`), the same chooser for the simulated opponent and for our own future
disrupts inside the rollout (`USE_BOSS_OPPONENT_MODEL`, `USE_BOSS_OWN_MODEL`,
`docs/stage1-opponent-model-v1.md`), the route-traffic term from the rollout's own portfolio
(`USE_BOSS_LITE_ROUTES`, `docs/boss-lite-routes-v1.md`), and the three parity-validated runtime
flags (`USE_INCREMENTAL_INK_REFRESH`, `USE_FAST_CHEAPEST_PATH`, `USE_BATCHED_REFRESH`,
`docs/stage2-compute-v1.md`). Search width/replies/depth and every weight are v09's
(`ROLLOUT_WIDTH` 6, `ROLLOUT_REPLIES` 2, `ROLLOUT_DEPTH` 8, `BOSS_OPPONENT_REPLIES` 2,
`BOSS_HOLD_MARGIN` 1.0, `BOSS_LITE_ROUTE_WEIGHT` 0.371747, `NOISE` 0.1).

## What changed in the repository

- `Params`: the seven flags default to true. `FrozenParams` keeps them off for every snapshot that
  predates them (v01–v09, the neural/spatial/leader snapshots), so every frozen version behaves
  exactly as recorded (`v09` resolves to the same 117 values as before).
- `btk.bot.v10`: `RolloutBotV10`, `BossSabotageV10`, `ProtectedOpeningV10` — byte copies of
  `current` with only package, class names and the bot label changed (verified by hash with the
  identity changes undone, `V10PromotionTest`). `versions/v10.properties` = the 130 effective
  values of `BotFactory.configuration("current")` = the params of the arena artifact's manifest.
- `BotFactory`: `v10` registered; the rollout-only flags may run in `current`, `main` and `v10`. A
  policy that never reads them (EvalBot, heuristic, neural proposal) or a frozen version drops the
  *inherited defaults* silently, but an explicit `=true` is still refused, as before. Consequence
  for the screening proxy: **`current?USE_BOSS_SABOTAGE=1` alone is now refused** (lite chooser and
  sabotage chooser exclude each other); the boss-v2 proxy is
  `current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0`
  — byte-for-byte the validated opponent (`docs/boss-proxy-v1.md`).
- `FileMerger`: `-PsubmitVersion=v10` exports the frozen copy (renamed and compacted like
  `current`); catalog: `v10` selected, `v09` historical.
- Tests: every pre-promotion test keeps its measured configuration through
  `LiveTargetExperimentParams.V10_OFF` / `V09_ON_CURRENT` / `v09Params()` (the v09 configuration
  expressed on `current`), so the v09-era default artifact still hashes to `ab0954a4…` and `v09` to
  `aca0be45…`; pins of artifacts that *enable* one of the seven flags changed because the shipped
  `Params` initializers now read `true` (behaviour identical, resolved configuration identical).
  New `V10PromotionTest` (record, frozen-source identity, guards, v09 unchanged, spec rules) and
  `V10SubmissionTest` (default artifact = D's configuration, both baselines intact, compiles).
  `V09DeferredCommitStudy` pins its diagnostic specs to the v09 configuration.

## Packaging verification (2026-10-23)

- Default `gradlew submit` (`build/submit-v10`): **99,676 / 100,000 chars**, source sha
  `1c891234…`, build `8c7ef3d9…`, **config sha `f0e507ac…` = the arena artifact's** (its text
  differs only in the seven initializer literals, −7 chars). `verifySubmission` exact.
  Frozen export `-PsubmitVersion=v10`: 99,598 chars, sha `9c3b4008…`.
- Strict process check of the default artifact vs v09 (4 games, 0 grace): **0 problem games**,
  0 timeouts, later responses ≤ 37.8 ms (`docs/evidence/v10-promotion/default-artifact-process-check.json`).
  The arena artifact itself passed the user's strict 8-game check on 2026-10-21.
- Test suite: 818 tests, **25 failures, all pre-existing** (19 via the retained-engine bytecode pin
  on `btk/core/Pathing.class`, 6 `PduhardBeamBotTest`); everything else green, including the
  rewritten promotion, submission and mechanism tests.

## State after the promotion

`current` = **v10 Boss Model**; frozen copy `btk.bot.v10`; the arena keeps running the D artifact
(`build=2f896714…`, same configuration — no re-upload needed; a fresh upload would carry build
`8c7ef3d9…`). v09 remains available as a frozen opponent and as the regression mirror. First task
after the promotion: `prune-v1` (artifact 99,676 / 100,000). Nothing pushed to the arena by the agent.
