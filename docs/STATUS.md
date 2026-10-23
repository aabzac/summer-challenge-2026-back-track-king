# STATUS — one page, keep current (updated 2026-10-23)

Read this before anything else; it replaces reconstructing the state from DEVLOG/BOTS/PLAN.

## Where we are

| item | value |
|---|---|
| Selected local version | **v09 "Never Idle"** (`btk.bot.current.RolloutBot`, 36 ms search of 50), frozen copy `btk.bot.v09`, artifact 95,278 chars |
| Arena entry (last verified) | **D (`stage2-compute-v1`) since 2026-10-21 evening = puzzle agent 6807052** (marker `build=2f896714… config=f0e507ac…` in all 50 fetched games). 2026-10-22: **30W/20L, 0 failed turns, rank 317 overall = #7 of 712 in Silver, score 28.06**; by map 30×20 **3-8**, 29×19 7-4, 27×18 7-3, 23×15 7-1, 21×14 2-0; tall maps (h ≥ 19) 10-12, low 20-8; 35 placement games 24-11, then 15 games vs --CC-- (Gold, rank 197) 6-9; disrupt on 97.7 % of turns, 40.7 % of accepted cells never earned (`docs/evidence/arena-2026-10-22-D-agent-6807052.json`). Baseline = the previous entry (contest-era `current` build `4cda8b1b`, agent 6755892): 23W/27L, 30×20 4-11, tall 6-9, 41–46 % never earned (`docs/evidence/arena-2026-10-21-v09-agent-6755892.json`). **Note:** the *challenge* leaderboard is frozen since the contest ended and still names agent 6755892; the live arena is the multiplayer *puzzle* leaderboard — `CgFetch` queries it first since 2026-10-22. |
| League / boss | Silver. Boss 4 = agent **6767608** in the permanent puzzle (was 6740207 during the contest), rating ~30–31 vs ours ~22–25. Boss public metadata changed on 2026-10-18; cached controls before that are historical. |
| v09 vs live boss, 40 fixed maps | 17/40 (09-20) · 11/40 (10-03) · 15/40 (10-04) · 11/40 (10-15) — same code; ±7.5 pp noise |
| Last experiment | 2026-10-23 **`cold-timing-30x20-v1` round 3 ("холод-300")**: fresh range 1000000, 159 tall maps × 2 seats — D cold (one JVM per game) **76.4 %** vs boss-v2 against D hot 73.9 % on the same maps/seats, paired **+2.5 ± 6.6 pp** (30×20: −0.6 ± 8.8) → **cold-JVM hypothesis rejected**; D6 / W4 closed (−1.9 / −1.3 vs cold D). Hot D on the 600-map range 66.9 %, strongest on 30×20 (78 %). The arena's 3-8 on 30×20 is not timing; next measurements: live boss series (step c), `cgAnalyze` of D's 30×20 losses. Earlier: 2026-10-22 (late) **`cold-timing-30x20-v1`**: D's exact artifact in a fresh process is at the 36–38 ms ceiling on **34 % of later turns on 30×20** (14–31 of the first 40 per game, 0 timeouts, max 45.5) — the hot bench's 12 ms/move is a JIT artefact. Cold paired games (one JVM per game) vs boss-v2 on the same 43 30×20 maps as the hot bench: pooled 172 games 70.9 % vs hot 77.9 % (paired −7.0 ± 12.5; the cold control itself moved 9 pp between runs) → effect not measurable at this size. Probes: M32 (`ROLLOUT_MAX_ROUTES=32`) rejected (hot −11/−19 pp, cold ±0); D6 (`ROLLOUT_DEPTH=6`) / W4 (`ROLLOUT_WIDTH=4`) parked (cold +5.8 ± 12, hot −4.4/−5.0). **Method change: big-map work is screened cold** (`run_cold_tall*.sh`, ≥ 300 paired games/arm). D unchanged. Earlier: 2026-10-22 (later) **`stage2-dividend-v1` part 2** (M128 = D + `ROLLOUT_MAX_ROUTES=128`, W8 = D + `ROLLOUT_WIDTH=8`; fresh pool 990000, same design): M128 **47.3 % vs D** (tall maps 41.2 %), 65.7 vs boss-v2 against D's 67.0 → rejected; W8 52.0 % vs D, 65.8 vs boss-v2 → fails the +3 pp gate, parked. **Dividend line closed: four search-size parameters (replies 3, depth 10, routes 128, width 8), none beats D vs the proxy, all lose on tall maps.** Slack left for Stage 3–4 valuation (needs `prune-v1` first) or as CG timing margin; cheap next check = `ColdTurnTiming` of D's artifact on 30×20 (arena 3-8 there vs D's best local rows on tall maps). Next free pool ≥ 1000000. Earlier the same day: 2026-10-22 **`stage2-dividend-v1`** (R3 = D + `ROLLOUT_REPLIES=3`, D10 = D + `ROLLOUT_DEPTH=10`; fresh pool 980000, 300/cell, same-map D controls): h2h 51.7 % / 53.3 %; vs boss-v2 64.7 / 65.8 against D's 68.7 (paired −4.0 / −2.8 pp); vs neural-v6 53.7 / 54.0 (D 51.7); vs v09 67.0 / 63.0 (D 63.3) — **both fail the step (a) +3 pp gate → parked, D unchanged.** Mean move time rises only ~2 ms: the slack is not spent by more replies/depth, and both lose 8 pp on tall maps vs boss-v2; next lever = routes/second root pass or the Stage 3–4 valuation (`docs/stage2-dividend-v1.md`). Before it: 2026-10-21 **`opponent-model-v1`** (v2 + boss-like simulated opponent, `…USE_BOSS_LITE=true,USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true`): same-map screen ≥ v2 on every row (57.8 % head-to-head, +14 pp vs boss-v2, +5.3 vs neural-v6, ±0 vs v09); v5 steps 1–2 PASS; **live pending**. **`boss-sabotage-v2`**: step 1 PASS on two pools (+11.5/+9.3 pp vs neural-v6), step 2 PASS_1000 (63.6 % vs v09); **live pending**. Before it, `boss-sabotage-v1` (`current?NOISE=0.1,USE_BOSS_SABOTAGE=true`): 67.45 %/1000 vs v09 locally, **but arena/boss worse (user report)** — v5 step 3 FAILED in substance. Diagnosis: (1) the gain is mostly v09-specific — neural-v6/neural-v1 beat the candidate 58 %/53.5 % (§6.2 of boss-proxy-v1.md: boss-v2 = this candidate); (2) the real-turn chooser runs inside the 36 ms window without timer checks and costs 16–26 ms per turn on 30×20 maps in a cold JVM (CG = one game per JVM; local bench = hot JIT, 5–10 ms) — `docs/evidence/stage1-opponent-model-v1/boss-chooser-timing*.log`. Nothing promoted since 2026-09-20. |
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
  v2 → B → C → D, each ≥ its predecessor on the same maps. D **uploaded 2026-10-21 evening (puzzle agent 6807052; strict procCheck 0 timeouts in `build/submit-stage2-d`)**; first 50 arena games 30W/20L (arena row). `stage2-dividend-v1` (2026-10-22): replies 3 / depth 10 / routes 128 / width 8 — all four parked or rejected, the "spend the dividend" line is closed (see the Last experiment row).
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
3. ~~Fetch the new agent's arena games~~ — done 2026-10-22 for D (30W/20L, see the arena row); the 40-game live boss series for D (`tools/boss_cached.py`, paired v09 control) is still open.

## Where things live

`docs/BOTS.md` version table · `docs/DEVLOG.md` chronological log (600 KB) · `docs/PLAN.md` checkboxes ·
`build/cg-boss/` live boss series · `replays/cg/boss/**` canonical boss replays (855) ·
`replays/cg-boss-winners/` 368 Gold-vs-boss games · `replays/cg-indrill/` 282 leader games.
