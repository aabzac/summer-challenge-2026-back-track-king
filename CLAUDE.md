# CLAUDE.md — Back Track King bot

Read in this order, every session: `docs/STATUS.md` (one page, current state — keep it current),
`AGENTS.md` (the working agreement; its rules apply to you unchanged), `docs/PLAN.md` (tick steps
when done), then only the parts of `docs/DEVLOG.md` / `docs/BOTS.md` the task needs (DEVLOG is
600 KB; grep by date or candidate id). Commands are in `README.md`.

## Rules that bite most often

- Selection policy is **v5** (`docs/benchmarking-v5.md`, AGENTS.md rule 3): proxy screen vs the
  boss-like screening opponent `current?USE_BOSS_SABOTAGE=1` (or `neural-v6` for a candidate that
  is itself the proxy), non-inferiority vs the selected version, then 80 paired live boss games.
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
  when you add fields). Source limit 100,000 chars; candidate D is at 99,683.
- No arena upload, no promotion, no "selected version" change without the live step and a
  DEVLOG entry. Verdicts: one BOTS.md row, one DEVLOG entry, STATUS.md updated.

## Current line of work (2026-10-22)

Chain on the same maps, each ≥ its predecessor: v09 → `boss-sabotage-v2` (`USE_BOSS_LITE`,
two-stage boss chooser) → B `opponent-model-v1` (+`USE_BOSS_OPPONENT_MODEL,USE_BOSS_OWN_MODEL`)
→ C (+`USE_BOSS_LITE_ROUTES`) → **D** (+`USE_INCREMENTAL_INK_REFRESH,USE_FAST_CHEAPEST_PATH,
USE_BATCHED_REFRESH`). D is the arena entry; its artifact and strict procCheck are in
`build/submit-stage2-d`. Open items are in `docs/PLAN.md` ("Stage 2 … spend the dividend",
"prune-v1", live series, arena fetch/analyse). Details per step: `docs/boss-sabotage-v2.md`,
`docs/stage1-opponent-model-v1.md`, `docs/boss-lite-routes-v1.md`, `docs/stage2-compute-v1.md`.

## Known test state

`gradlew test`: 25 pre-existing failures are expected — 19 via the retained-engine bytecode pin
on `btk/core/Pathing.class` (`experiment-engine-v1.json`, see `docs/bot_catalog.md`; remedy is a
new snapshot with new experiment ids, a user decision) and 6 `PduhardBeamBotTest` (bot never
registered). Anything else red is new.

## Working style

Git flow: branch → PR → merge; commit new files explicitly (`git add -A`, not `commit -a`).
Long benches: 2 workers, CG 486/36 ms budgets, fresh seeds (next free range ≥ 980000; used
ranges are listed in the evidence JSONs under `docs/evidence/`). Keep the Claude project doc
(`claude/strategy-analysis-2026-10-20.md` in the claude.ai project) in sync at milestones.
