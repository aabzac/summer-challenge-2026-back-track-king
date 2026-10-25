# CLAUDE.md — Back Track King bot

Read in this order, every session: `docs/STATUS.md` (one page, current state — keep it current),
`AGENTS.md` (the working agreement; its rules apply to you unchanged), `docs/PLAN.md` (tick steps
when done), then only the parts of `docs/DEVLOG.md` / `docs/BOTS.md` the task needs (DEVLOG is
600 KB; grep by date or candidate id). Commands are in `README.md`.

## Rules that bite most often

- Selection policy is **v5** (`docs/benchmarking-v5.md`, AGENTS.md rule 3): proxy screen vs the
  boss-like screening opponent — since the v12 promotion (2026-10-25 night) its spec is
  `current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_EXPOSURE_INCOME=0`
  (the v11 flag is dropped by the lite chooser off; the v12 flag must be switched off explicitly, or the proxy is not the validated opponent)
  (`current?USE_BOSS_SABOTAGE=1` alone is refused; or `neural-v6` for a candidate that is itself the
  proxy) — non-inferiority vs the selected version (`current` = v12), then 80 paired live boss games.
  The local mirror vs v09 is a regression guard, never the objective.
- **Compare a candidate only with a control measured on the same maps, seeds and seats, in the
  same run.** Absolute scores move 10 pp between map pools; deltas do not. The screen scripts in
  `tools/boss_proxy/run_*.sh` carry their own controls — copy that pattern.
- Any new per-turn step not bounded by the turn timer is timed in a **fresh JVM per game** on
  30×20 maps (`gradlew bossChooserTiming -Pmaps=1`, `btk.tools.ColdTurnTiming`); the hot-JIT local
  bench under-reports such costs 2–3×. The strict exact-artifact `procCheck` (zero grace) is the
  gate before any upload.
- Every experimental flag ships inside its own `// NAME_BEGIN/END` merger block (see
  `FileMerger`), so artifacts with the flag off stay byte-identical to the pinned hashes
  (`BossSabotageSubmissionTest`, `V07/V08/V09PromotionTest` pin the `Params` field count — bump it
  when you add fields). Source limit 100,000 chars; since `readable-export-v1` (2026-10-25) a `current`
  export is **readable** (one statement per line, tab indentation, comments dropped) and the default
  artifact is **91,349** chars since the v12 promotion (v11's readable default 88,997 / 88,990 after the marker change, v10's 88,671, `prune-v1`'s compact form 70,397; a `current` export is compacted only
  when its readable form would exceed the limit, frozen exports keep their historical bytes). The
  first-turn `MESSAGE` reads `v12 | BTK_BUILD_V1 strategy=current build=… config=…` (the version id only, `(modified)` for
  experiments; the header comment of a current export reads `// BTK strategy: current, v12`)
  (`BotFactory.displayName`; bump `BotFactory.SELECTED_VERSION` / `VERSION_NAMES` at a promotion). `Params` fields the shipped rollout never reads live in `// LOCAL_ONLY_BEGIN/END`
  blocks (stripped from current rollout artifacts only); a new field the bot reads goes outside them.
  The 17 pruned rollout flags (`BotFactory.PRUNED_ROLLOUT_FLAGS`) are refused on `current`; run their
  mechanisms on `v10?FLAG=1` (tests in `btk.bot.v10`); they are refused on `v11`/`v12` too. The v11 configuration on current is
  `current?USE_EXPOSURE_INCOME=0` (`LiveTargetExperimentParams.V11_ON_CURRENT`), the v10 one adds `USE_BOSS_ROOT_TARGET=0`
  (`V10_ON_CURRENT`); every current-based artifact pin moves at a promotion because the display name sits in `BUILD_MARKER`
  and the selected version in the header comment (config hashes do not move).
- No arena upload, no promotion, no "selected version" change without the live step and a
  DEVLOG entry. Verdicts: one BOTS.md row, one DEVLOG entry, STATUS.md updated.

## Current line of work (2026-10-25)

**v12 "Exposure Income" is the selected version since 2026-10-25 night** (`docs/v12-promotion.md`, user decision after
`stage4-exposure-income-v1`: E1 = `current?USE_EXPOSURE_INCOME=true`, +3.7 pp vs the proxy over v11, 60.3 % h2h, live
session 1 at the +5 pp threshold exactly): the flag is a `Params` default, `btk.bot.v12` the frozen copy, default artifact
91,349 chars / config `d7803b42…` (`build/submit-v12`), **arena upload by the user pending** (the arena runs v11, agent
6813813). Next candidate: whatever `docs/PLAN.md` lists next, screened with `current` (= v12) as the same-map control and the
proxy spec above. Before it: **v11 "Root Target" was the selected version from 2026-10-25 evening** (`docs/v11-promotion.md`, user decision after
`stage4-threat-model-v1`: B = `current?USE_BOSS_ROOT_TARGET=true`, +8.8 pp vs the proxy over v10, 57.0 % h2h, live
session 1 inconclusive +2.5 pp): the flag is a `Params` default, `btk.bot.v11` is the frozen copy, default artifact
88,997 chars / config `179dcc66…` (`build/submit-v11`), **arena upload by the user pending** (the arena still runs
v10, agent 6811139). Next candidate = the exposure-discounted own valuation (Stage 4, second half) screened with
`current` (= v11) as the same-map control; `USE_BOSS_THREAT_MODEL` is parked. Before it: **v10 "Boss Model" was the selected version from 2026-10-23** (`docs/v10-promotion.md`): D's seven flags
are `Params` defaults, `btk.bot.v10` is the frozen copy, the arena runs the same configuration (agent
6811139 since 2026-10-24/25 = the fresh default artifact; D was 6807052). Pre-promotion tests express v09
as `LiveTargetExperimentParams.V09_ON_CURRENT` (`current?` + `V10_OFF`). **`prune-v1` done 2026-10-25**
(`docs/prune-v1.md`): `current` = v10 behaviour without the rejected experiments; **`readable-export-v1`
done 2026-10-25** (DEVLOG): readable `current` exports, default artifact 88,671 chars, strategy name in the
first-turn message, config `f0e507ac…` unchanged. Next: Stage 3–4 valuation (never waste the disrupt point; exposure-discounted own valuation + bypass),
screened vs the proxy with `current` as the same-map control. History of the chain:

Chain on the same maps, each ≥ its predecessor: v09 → `boss-sabotage-v2` (`USE_BOSS_LITE`,
two-stage boss chooser) → B `opponent-model-v1` (+`USE_BOSS_OPPONENT_MODEL,USE_BOSS_OWN_MODEL`)
→ C (+`USE_BOSS_LITE_ROUTES`) → **D** (+`USE_INCREMENTAL_INK_REFRESH,USE_FAST_CHEAPEST_PATH,
USE_BATCHED_REFRESH`). D is the arena entry; its artifact and strict procCheck are in
`build/submit-stage2-d`. Open items are in `docs/PLAN.md` (Stage 3–4, the optional live session 2,
arena fetch/analyse). Details per step: `docs/boss-sabotage-v2.md`,
`docs/stage1-opponent-model-v1.md`, `docs/boss-lite-routes-v1.md`, `docs/stage2-compute-v1.md`.

## Known test state

`gradlew test` (822 tests on 2026-10-25): 25 pre-existing failures are expected — 19 via the retained-engine bytecode pin
on `btk/core/Pathing.class` (`experiment-engine-v1.json`, see `docs/bot_catalog.md`; remedy is a
new snapshot with new experiment ids, a user decision) and 6 `PduhardBeamBotTest` (bot never
registered). Anything else red is new.

## Working style

Git flow: branch → PR → merge; commit new files explicitly (`git add -A`, not `commit -a`).
Long benches: 2 workers, CG 486/36 ms budgets, fresh seeds (next free range ≥ 980000; used
ranges are listed in the evidence JSONs under `docs/evidence/`). Keep the Claude project doc
(`claude/strategy-analysis-2026-10-20.md` in the claude.ai project) in sync at milestones.
