# CLAUDE.md — Back Track King bot

Read in this order, every session: `docs/STATUS.md` (one page, current state — keep it current),
`AGENTS.md` (the working agreement; its rules apply to you unchanged), `docs/PLAN.md` (tick steps
when done), then only the parts of `docs/DEVLOG.md` / `docs/BOTS.md` the task needs (since repo-cleanup
2026-10-27 they keep only the period from 2026-10-20; older entries are verbatim in `docs/archive/` — grep there
by date or candidate id). Commands are in `README.md`.

## Rules that bite most often

- Selection policy is **v5** (`docs/benchmarking-v5.md`, AGENTS.md rule 3): proxy screen vs the
  boss-like screening opponent — since the v13 promotion (2026-10-27) its spec is
  `current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0,USE_EXPOSURE_INCOME=0,USE_BYPASS_ROUTES=0`
  (the v11 flag is dropped by the lite chooser off; the v12 flag must be switched off explicitly, or the proxy is not the validated opponent;
  a sabotage spec with the v13 bypass flag on is refused)
  (`current?USE_BOSS_SABOTAGE=1` alone is refused; or `neural-v6` for a candidate that is itself the
  proxy) — **since 2026-10-26 a step-(a) pass is replicated on a fresh pool before anything else (positive replication delta,
  pooled delta ≥ +3 pp; `tools/boss_proxy/run_replication.sh` + `pooled_delta.py`)** — non-inferiority vs the selected version
  (`current` = v13), then **since 2026-10-27 one 40-game live boss session as a filter (delta ≥ 0) and two arena uploads
  of the candidate** (`docs/arena-uploads.md`; rollback when its mean placement score is below the selected version's uploads
  in the same league by more than M ≈ 1.4 and the performance rating agrees).
  The local mirror vs v09 is a regression guard, never the objective.
- **Fetch our live arena entry before every upload** (`python tools/arena_uploads.py fetch`, files the games under the
  gitignored `replays/arena/<label>/upload-N/`): CG lists the games of the live agent only, a replaced upload's game
  list is gone. **Run `... watch` right after every Submit** (fetches until placement reaches 100 %): a league
  promotion at the end of placement replaces the agent (v13's second upload lost its list that way, 2026-10-27).
  We are in **Gold** since then; scores compare within a league only.
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
  artifact is **94,816** chars since the v13 promotion (v12's 91,349; v11's readable default 88,997 / 88,990 after the marker change, v10's 88,671, `prune-v1`'s compact form 70,397; a `current` export is compacted only
  when its readable form would exceed the limit, frozen exports keep their historical bytes). The
  first-turn `MESSAGE` reads `v13 | BTK_BUILD_V1 strategy=current build=… config=…` (the version id only, `(modified)` for
  experiments; the header comment of a current export reads `// BTK strategy: current, v13`)
  (`BotFactory.displayName`; bump `BotFactory.SELECTED_VERSION` / `VERSION_NAMES` at a promotion). `Params` fields the shipped rollout never reads live in `// LOCAL_ONLY_BEGIN/END`
  blocks (stripped from current rollout artifacts only); a new field the bot reads goes outside them.
  The 17 pruned rollout flags (`BotFactory.PRUNED_ROLLOUT_FLAGS`) are refused on `current`; run their
  mechanisms on `v10?FLAG=1`; they are refused on `v11`/`v12`/`v13` too. **prune-v2 (2026-10-27)** removed the parked
  experiments of v12's time from current — `USE_COLLISION_AVOIDANCE`, `USE_CORRIDOR_ROUTES`, `USE_QUICK_ALWAYS`/`DELAY_POWER`,
  `BOSS_OPP_HOLD_MARGIN`, `USE_BOSS_NO_CLOSE`, `USE_ROUTE_COMMIT`, `USE_BOSS_THREAT_MODEL`: refused on current, run on
  `v13?FLAG=1` (the threat model on v11–v13); `btk.bot.PruneV2Test`. Their `Params` fields and merger blocks stay for the frozen
  copies. The v12 configuration on current is
  `current?USE_BYPASS_ROUTES=0` (`LiveTargetExperimentParams.V12_ON_CURRENT`), the v11 one adds `USE_EXPOSURE_INCOME=0`
  (`V11_ON_CURRENT`), the v10 one `USE_BOSS_ROOT_TARGET=0` (`V10_ON_CURRENT`); every current-based artifact pin moves at a promotion because the display name sits in `BUILD_MARKER`
  and the selected version in the header comment (config hashes do not move).
- No arena upload, no promotion, no "selected version" change without the live step and a
  DEVLOG entry. Verdicts: one BOTS.md row, one DEVLOG entry, STATUS.md updated.

## Current line of work (2026-10-27)

**v13 "Bypass Routes" is the selected version since 2026-10-27** (`docs/v13-promotion.md`, user decision "Делаем v13" after
`bypass-routes-v1`: B2 = `current?USE_BYPASS_ROUTES=true`, +3.0 pp vs the proxy over v12 replicated to pooled +4.2 pp, 55.0 % h2h,
live session 1 +7.5 pp, 24/40 vs 21/40; session 2 skipped on the user's choice): the flag is a `Params` default, `btk.bot.v13` the
frozen copy, default artifact 94,816 chars / config `8c4b3d6d…` (`build/submit-v13`, strict procCheck 0 problems), **on the
arena since 2026-10-27** (upload-1 = agent 6818214: 120 placement games 75W/45L, CG 29.23, rank 316, Silver; before it v12, agent 6814073);
**its re-upload (6818421) reached 30.24 at 92 % and was promoted to Gold** — first noise measurement, M ≈ 1.4 (`docs/arena-uploads.md`);
v13's Gold placement = agent 6818546 (upload-3), and one more v13 Submit in Gold completes the Gold baseline. Next candidate: screened with `current` (= v13) as the same-map control
and the proxy spec above (with `USE_BYPASS_ROUTES=0`), step 1a, h2h, the live filter vs `current`, then two arena uploads. Before it:
**v12 "Exposure Income" was the selected version from 2026-10-25 night** (`docs/v12-promotion.md`, user decision after
`stage4-exposure-income-v1`: E1 = `current?USE_EXPOSURE_INCOME=true`, +3.7 pp vs the proxy over v11, 60.3 % h2h, live
session 1 at the +5 pp threshold exactly): the flag is a `Params` default, `btk.bot.v12` the frozen copy, default artifact
91,349 chars / config `d7803b42…` (`build/submit-v12`), **arena upload by the user pending** (the arena runs v11, agent
6813813). Next candidate: whatever `docs/PLAN.md` lists next, screened with `current` (= v12) as the same-map control and the
proxy spec above — since `arena-losses-v12` (2026-10-26) that is **shared-corridor routes** (the field's top half beats v12 on
multiplicity: 1.62 connections per earning cell-turn vs our 1.25), and a candidate aimed at the arena is screened vs neural-v6 as
well (`tools/boss_proxy/run_fill_nv6_screen.sh` pattern); the fill is free paint and stays. **`corridor-routes-v1` (2026-10-26) passed
(a)+(b) and the neural-v6 cross-check** (K1 = `current?USE_CORRIDOR_ROUTES=true`: +6.5 pp vs the proxy, 51.7 % h2h, +7.7 pp vs
neural-v6; artifact `build/submit-corridor-k1`, config `299cafd9…`) — **but it lost its live session 15 pp (18/40 vs v12's 24/40, 3 : 9 discordant) → rejected, flag parked off**; for a construction
change the proxy and neural-v6 predicted the sign wrongly, and `proxy-agreement-v1` (2026-10-26, `docs/proxy-agreement-v1.md`) found why: the boss sends 36–40 % of its disrupts to our
under-construction concentrations and finishes near inks, the income-scoring proxy does not (agreement 54 % in K1 games, 29 % at ≥ 10
of our tracks in a region; neural-v6 41–44 %). `boss-v3` (2026-10-26, `docs/boss-v3.md`) then showed the proxy cannot be refitted into that blind spot (shipped weights = corpus optimum;
`BOSS_HOLD_MARGIN=0` +4.5 pp agreement uniformly; K1 vs the margin-0 proxy +2.3 pp, no sign flip) — closed, proxy unchanged; construction
changes that concentrate tracks before they earn are not trusted on the proxy's sign. `earning-delay-v1` (2026-10-26, `docs/earning-delay-v1.md`): **Q = `current?USE_QUICK_ALWAYS=true` passed (a)+(b)**
(+6.3 ± 5.6 pp vs the proxy, 52.7 % h2h, neutral vs neural-v6; artifact `build/submit-delay-q`, config `41996066…`); **but its live
session lost 5 pp (18/40 vs v12's 20/40, 9 : 11 discordant) → parked, flag off**. Both construction traits of the arena's top half passed the proxy
and failed the live boss (K1 −15, Q −5): the construction line is closed at the proxy's level. `opp-hold-v1` (2026-10-26) then closed the
sabotage / valuation side's last cheap lever (the boss model's hold margin: 0 → −8 pp, 2 → flat; 1.0 stays); `boss-v4` (2026-10-26, `docs/boss-v4.md`) then
showed there is **no construction blind spot**: on a fresh pool the policy proxy gives K1 −1.7 pp (was +6.5) and Q −6.0 pp (was +6.3), agreeing
with both live signs — the passes were selection noise (best of two arms, ±7 pp, +3 pp gate). **Proposed policy-v5 amendment (user decision):
replicate step (a) on a fresh pool before (b) and the live step.** `proxy-agreement-v1`'s blind spot was the proxy's winning-closure shortcut
in the boss's won tails (`USE_BOSS_NO_CLOSE`, default off, not validated).
**`route-commit-v1` (2026-10-27): C10 = `current?USE_ROUTE_COMMIT=true` passed (a) +7.0 pp, step 1a (pooled +4.8 ± 3.8 pp) and (b) 52.7 %**;
**but its live session 1 lost 10 pp (18/40 vs v12's 22/40, 3 : 7 discordant) → parked (user's decision, no session 2), flag off**. `late-game-v1` (2026-10-27, `docs/late-game-v1.md`): the last third is not a lever
(6–11 % of v12's points, 5 of 58 losses within a drawn last third; protection is shared by both sides); the gap is the middle third
(v12's paint earns 35 vs 62 % in its losses, bypass share 9 vs 20 %). **`bypass-routes-v1` (2026-10-27): B2 = `current?USE_BYPASS_ROUTES=true`
passed (a) +3.0 pp, 1a (pooled +4.2 ± 4.0 pp) and (b) 55.0 %**; artifact `build/submit-bypass-b2` (config `8c4b3d6d…`); **its live session 1
won +7.5 pp (24/40 vs v12's 21/40, 5 : 2 discordant)**; v13 now or session 2 first is the user's decision.
Live series are now launched by Claude: allow rule `Bash(bash tools/run_boss_paced.sh *)` in `.claude/settings.local.json` (2026-10-26). Before it: **v11 "Root Target" was the selected version from 2026-10-25 evening** (`docs/v11-promotion.md`, user decision after
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

`gradlew test` (429 tests on 2026-10-27, after repo-cleanup tier 3): **0 failures expected**; anything red is new. The 25
long-standing failures (the six `exp-*` recipes' engine pin and the unregistered `pduhard-beam-v1`) left with their code
(`docs/repo-cleanup-v1.md`). Tests follow the code they test (AGENTS.md rule 9).

## Working style

Git flow: branch → PR → merge; commit new files explicitly (`git add -A`, not `commit -a`). Evidence archives and
binary exports (`*.zip`, `*.tar.gz`, `*.csv.gz` under `docs/evidence/`) are gitignored; the 68 files untracked on 2026-10-27
are listed with sha256 and their adding commit in `docs/evidence/untracked-evidence-2026-10-27.json`.
Long benches: 2 workers, CG 486/36 ms budgets, fresh seeds (next free range ≥ 980000; used
ranges are listed in the evidence JSONs under `docs/evidence/`). Keep the Claude project doc
(`claude/strategy-analysis-2026-10-20.md` in the claude.ai project) in sync at milestones.
