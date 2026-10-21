# STATUS — one page, keep current (updated 2026-10-20)

Read this before anything else; it replaces reconstructing the state from DEVLOG/BOTS/PLAN.

## Where we are

| item | value |
|---|---|
| Selected local version | **v09 "Never Idle"** (`btk.bot.current.RolloutBot`, 36 ms search of 50), frozen copy `btk.bot.v09`, artifact 95,278 chars |
| Arena entry (last verified) | **neural-v3**, agent 6779375, uploaded 2026-10-04, Silver 134/712, rating 22.4. User: "looks worse than v09". **v09 was never uploaded** (PLAN item open). |
| League / boss | Silver. Boss 4 = agent **6767608** in the permanent puzzle (was 6740207 during the contest), rating ~30–31 vs ours ~22–25. Boss public metadata changed on 2026-10-18; cached controls before that are historical. |
| v09 vs live boss, 40 fixed maps | 17/40 (09-20) · 11/40 (10-03) · 15/40 (10-04) · 11/40 (10-15) — same code; ±7.5 pp noise |
| Last experiment | 2026-10-21 `boss-sabotage-v1` (`current?NOISE=0.1,USE_BOSS_SABOTAGE=true`): **674W/325L/1D = 67.45 %/1000 vs v09, PASS_1000**; artifact 94,845 chars; procCheck + 80 live boss games pending. Nothing promoted since 2026-09-20. |
| Experimental flags | all OFF (`USE_BOSS_SABOTAGE` new 2026-10-20, `USE_BATCHED_REFRESH`, `USE_INCREMENTAL_INK_REFRESH`, `USE_FAST_CHEAPEST_PATH`, `USE_ROLLOUT_MISSED_DEADLINE`, `USE_ROLLOUT_ALL_CELL_RISK`, `USE_ROLLOUT_ROOT_FILL`, live-target, track-value, chords, …) |
| Contest | over (post-mortems 2026-09-21: top 4 are RL nets); arena continues as a multiplayer puzzle |

## Current plan (2026-10-20, see claude/strategy-analysis-2026-10-20.md in the Claude project)

- **Stage 0 (done 2026-10-20, ordering check vs boss-v2 still running): fix measurement.**
  855 boss replays studied; fitted target model (held-out top-1 51 %). `boss-v1` reference =
  behavioural proxy only (30.5 % vs v09). **`current?USE_BOSS_SABOTAGE=1` ("boss-v2") = screening
  opponent**: boss-like fingerprint, **55.25 %/400 vs v09** — also the first candidate since v09 with
  a lead (screen only; no 1000-game run, procCheck or live games yet). **Policy v5 adopted 2026-10-21**
  (`docs/benchmarking-v5.md`, AGENTS.md rule 3). Details `docs/boss-proxy-v1.md`.
- Stage 1: asymmetric opponent model inside the rollout (boss-like second scenario).
- Stage 2: algorithmic compute cuts (skip zero-delta ink evaluations, targets once per simulated turn).
- Stage 3–4: never waste the disrupt point; exposure-discounted own valuation + bypass.

## Open decisions for the user

0. Run `gradlew test` + `gradlew submit "-PsubmitVersion=current?USE_BOSS_SABOTAGE=1" -PsubmitOut=build/submit-boss-sab` + `procCheck` on your machine (the cloud workspace has no JUnit and no CG-like timing).

1. ~~Adopt policy v5~~ — adopted 2026-10-21.
2. Arena entry: re-upload v09 (baseline) or neural-v6 (best boss sample 20/40, tall-map paint 88 %) or leave neural-v3.

## Where things live

`docs/BOTS.md` version table · `docs/DEVLOG.md` chronological log (600 KB) · `docs/PLAN.md` checkboxes ·
`build/cg-boss/` live boss series · `replays/cg/boss/**` canonical boss replays (855) ·
`replays/cg-boss-winners/` 368 Gold-vs-boss games · `replays/cg-indrill/` 282 leader games.
