# STATUS — one page, keep current (updated 2026-10-21)

Read this before anything else; it replaces reconstructing the state from DEVLOG/BOTS/PLAN.

## Where we are

| item | value |
|---|---|
| Selected local version | **v09 "Never Idle"** (`btk.bot.current.RolloutBot`, 36 ms search of 50), frozen copy `btk.bot.v09`, artifact 95,278 chars |
| Arena entry (last verified) | **opponent-model-v1 (B) since 2026-10-21 evening** — marker `build=4ac43802… config=dcb8f038…` confirmed byte-identical to the B artifact; no fetched games of it yet. The 50 games fetched 2026-10-21 22:29 (agent 6755892) are the previous entry, **v09-era build `4cda8b1b`: 23W/27L, 0 timeouts, rank 316 overall ≈ #22 of 706 in Silver**, 30×20 maps 4/11, 21×14 1/7, 41–46 % of accepted cells never earned (`docs/evidence/arena-2026-10-21-v09-agent-6755892.json`). Earlier today boss-sabotage-v1 (worse, taken down), before that neural-v3 (2026-10-04). |
| League / boss | Silver. Boss 4 = agent **6767608** in the permanent puzzle (was 6740207 during the contest), rating ~30–31 vs ours ~22–25. Boss public metadata changed on 2026-10-18; cached controls before that are historical. |
| v09 vs live boss, 40 fixed maps | 17/40 (09-20) · 11/40 (10-03) · 15/40 (10-04) · 11/40 (10-15) — same code; ±7.5 pp noise |
| Last experiment | 2026-10-21 **`opponent-model-v1`** (v2 + boss-like simulated opponent, `…USE_BOSS_LITE=true,USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true`): same-map screen ≥ v2 on every row (57.8 % head-to-head, +14 pp vs boss-v2, +5.3 vs neural-v6, ±0 vs v09); v5 steps 1–2 PASS; **live pending**. **`boss-sabotage-v2`**: step 1 PASS on two pools (+11.5/+9.3 pp vs neural-v6), step 2 PASS_1000 (63.6 % vs v09); **live pending**. Before it, `boss-sabotage-v1` (`current?NOISE=0.1,USE_BOSS_SABOTAGE=true`): 67.45 %/1000 vs v09 locally, **but arena/boss worse (user report)** — v5 step 3 FAILED in substance. Diagnosis: (1) the gain is mostly v09-specific — neural-v6/neural-v1 beat the candidate 58 %/53.5 % (§6.2 of boss-proxy-v1.md: boss-v2 = this candidate); (2) the real-turn chooser runs inside the 36 ms window without timer checks and costs 16–26 ms per turn on 30×20 maps in a cold JVM (CG = one game per JVM; local bench = hot JIT, 5–10 ms) — `docs/evidence/stage1-opponent-model-v1/boss-chooser-timing*.log`. Nothing promoted since 2026-09-20. |
| Experimental flags | all OFF (`USE_BOSS_SABOTAGE` new 2026-10-20, `USE_BATCHED_REFRESH`, `USE_INCREMENTAL_INK_REFRESH`, `USE_FAST_CHEAPEST_PATH`, `USE_ROLLOUT_MISSED_DEADLINE`, `USE_ROLLOUT_ALL_CELL_RISK`, `USE_ROLLOUT_ROOT_FILL`, live-target, track-value, chords, …) |
| Contest | over (post-mortems 2026-09-21: top 4 are RL nets); arena continues as a multiplayer puzzle |
| Test suite (2026-10-21) | 810 tests, **25 pre-existing failures** after the field-count fix: 19 via the retained-engine bytecode pin (`experiment-engine-v1.json`, Sept-14 revision `218457a`) — live `btk/core/Pathing.class` changed on 2026-10-18 (incremental-ink + fast-path blocks); 6 `PduhardBeamBotTest` (untracked, bot `pduhard-beam-v1` never registered, noted in DEVLOG 2026-10-08). Neither is caused by the boss-sabotage work (`btk/core` untouched). Options: new engine snapshot + new experiment ids (`bot_catalog.md`), or leave red. |

## Current plan (2026-10-20, see claude/strategy-analysis-2026-10-20.md in the Claude project)

- **Stage 0 (done 2026-10-21): fix measurement.** 855 boss replays studied; fitted target model
  (held-out top-1 51 %). `boss-v1` reference = behavioural proxy only (30.5 % vs v09).
  **`current?USE_BOSS_SABOTAGE=1` ("boss-v2") = accepted screening opponent**: boss-like
  fingerprint, 62 % vs v09; ordering check 6/8 directions vs the live boss, neural-v6's +22 pp
  reproduced (+20), two caveats (neural-v1, all-cell-risk: the proxy under-punishes bypass-heavy
  construction) — `docs/boss-proxy-v1.md` §6.2. **Policy v5 adopted 2026-10-21**
  (`docs/benchmarking-v5.md`, AGENTS.md rule 3).
- **`boss-sabotage-v2`** (`current?NOISE=0.1,USE_BOSS_LITE=true`, `docs/boss-sabotage-v2.md`):
  two-stage chooser (cheap logit shortlist → top-4 ink rescoring, no path searches, timer-guarded),
  offline 51.1 % vs 51.5 % full; artifact 93,695 chars `a8c739fd…`; cold-JVM timing = v09's (max
  38–40 ms on 30×20 vs v1's 44–53). **v5 steps 1–2 PASS (2026-10-21): vs neural-v6 50.3 % against
  the v09 control's 38.8 % (+11.5 pp); 1000 vs v09 = 622W/350L/28D = 63.6 %, zero errors.**
  Step 3 (user): `gradlew test`, `submit`, strict `procCheck`, 80 live boss games.
- **Later the same day — two more same-map steps on top of B** (`docs/boss-lite-routes-v1.md`,
  `docs/stage2-compute-v1.md`): C = B + `USE_BOSS_LITE_ROUTES` (route-traffic term from the
  rollout's own portfolio): 50.0 % vs B, +3.8 pp vs neural-v6 pooled over two pools, neutral
  elsewhere — kept. D = C + `USE_INCREMENTAL_INK_REFRESH,USE_FAST_CHEAPEST_PATH,USE_BATCHED_REFRESH`
  (profile: 44 % of the window in route enumeration, 36 % in the chooser's ink refreshes): ×2
  simulated turns, **53.7 % vs C, 67.0 % vs v09, 65.0 % vs boss-v2, 49.7 % vs neural-v6**;
  mean 11.8 ms/turn → search completes early. Artifact 99,683 chars (limit 100,000). Chain:
  v2 → B → C → D, each ≥ its predecessor on the same maps. Next upload candidate after B's
  arena/live reading: D (strict procCheck first).
- **Stage 1 `opponent-model-v1`** = v2 + `USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true`
  (`docs/stage1-opponent-model-v1.md`): same-map screen 2026-10-21 (300 games/cell, seed 970000)
  — **57.8 % vs v2 head-to-head, 62.3 % vs boss-v2 (v2: 48.3), 45.3 % vs neural-v6 (v2: 40.0),
  63.0 % vs v09 (v2: 62.7)**; v5 steps 1–2 PASS; cold-JVM max 36–38 ms. Artifact 95,421 chars.
  **Recommended live candidate** (v2 is the fallback): live-test one at a time, 80 paired boss
  games each.
- Stage 2: algorithmic compute cuts (skip zero-delta ink evaluations, targets once per simulated turn).
- Stage 3–4: never waste the disrupt point; exposure-discounted own valuation + bypass.

## Open decisions for the user

0. Run `gradlew test` + `gradlew submit "-PsubmitVersion=current?USE_BOSS_SABOTAGE=1" -PsubmitOut=build/submit-boss-sab` + `procCheck` on your machine (the cloud workspace has no JUnit and no CG-like timing).

1. ~~Adopt policy v5~~ — adopted 2026-10-21.
2. Arena entry: **take boss-sabotage-v1 down** — re-upload neural-v3 (previous) or neural-v6 (best boss sample 20/40; tall-map paint 88 %); v09 was never uploaded.
3. Fetch the new agent's arena games (`gradlew cgFetch -Pplayer=<nick> -Plimit=50`, `gradlew cgAnalyze -Pagent=<id>`) to count failed turns on tall maps, and run the 40-game boss series (`tools/boss_cached.py`) so the live number replaces "seems worse".

## Where things live

`docs/BOTS.md` version table · `docs/DEVLOG.md` chronological log (600 KB) · `docs/PLAN.md` checkboxes ·
`build/cg-boss/` live boss series · `replays/cg/boss/**` canonical boss replays (855) ·
`replays/cg-boss-winners/` 368 Gold-vs-boss games · `replays/cg-indrill/` 282 leader games.
