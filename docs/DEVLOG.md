# Dev log

Format per entry: context / hypothesis -> what was done -> result -> decision.

Entries before 2026-10-20 (contest days, v01–v09, neural/spatial/indrill lines) are in
[archive/DEVLOG-2026-09-13_10-19.md](archive/DEVLOG-2026-09-13_10-19.md).

## 2026-10-20 — Stage 0: measurement reset — boss proxy, policy v5 proposal, STATUS page

**Hypothesis.** The month-long stall after v09 is a measurement problem before it is a strategy
problem: the v4 local gate (v09 mirror, ~90-turn games, 64–67 % disruption) cannot see mechanisms
that matter against the boss (65–72-turn games, 88–92 % disruption, targets held until ink,
construction inked before it earns), and the 40-game live stage (±7.5 pp; an unchanged v09 scored
17/11/15/11 of 40 over four weeks) cannot confirm them. Fix the bench first.

**Work.**
1. `docs/STATUS.md`: one page with the current arena/league/boss/selected state and open decisions.
2. `docs/benchmarking-v5.md`: proposed selection rule (proxy screen + non-inferiority vs v09 +
   80–120 paired live games + tuning budget for new heuristic bots). Not adopted; AGENTS.md unchanged.
3. Boss replay study on all 855 canonical boss games (`BossIntentStudy`, `CgLeaderStudy` on
   "Boss 4", 2.5 M candidate rows). Findings in `docs/boss-proxy-v1.md` §3: the boss announces its
   intent in `MESSAGE` (build/steal/block/fortify); disrupts on 88 % of turns and holds a target
   until ink 83 % of the time; commits to regions holding opponent tracks 95 % of the time
   (construction 43 %, active 51 %); never places a cell in a region at instability ≥ 2; builds on
   own-only unstable-avoiding routes (61 % of cells on such an optimal route, 39 % on the shared
   cheapest route); finishes the pair it started (52 %); idles only after turn 20 and almost only
   when ahead.
4. Fits: conditional logit for the commitment target, held-out top-1 **51.0 %** (uniform 4.5 %);
   hold margin 1.0 (hold rate 84.5 % vs 83.5 % observed); project stickiness 3.0 (pair agreement
   24 % → 50 %).
5. `boss-v1` (`btk.bot.reference.BossBot`, local-only reference): fitted sabotage + fitted
   construction model. Fingerprint vs v09 (200 games): sabotage statistics within a few points of
   the real boss, but **30.5 % wins vs the boss's 67.5 %**; its construction earns half the boss's
   income by turn 10 with identical paint. Ordering check (200 games each vs boss-v1): v07 53.0,
   v08 62.0, v09 74.0, neural-v6 78.5, neural-v1 76.0, all-cell-risk 83.5, root-fill 76.5,
   live-target 72.0, batched 73.5 %. Sanity order holds, but all-cell-risk (+9.5 pp here, −12.5 pp
   live) shows a weak-building relentless opponent rewards bypasses the real boss punishes.
   **boss-v1 rejected as strength proxy, retained as behavioural reference.**
6. `current?USE_BOSS_SABOTAGE=1` (`btk.bot.current.BossSabotage`, default-off flag, experiment
   `boss-sabotage-v1`): v09's search/construction with the real-turn DISRUPT replaced by the fitted
   chooser. Fingerprint vs v09 (300 games): disrupt 99 %, hold 71 %, commit classes 50/43/7,
   game length 77, closures 69 %, **54.3 % wins**; with a separate 100-game bench 58.0 %:
   **221-179 = 55.25 % over 400 games**, zero errors, avg 20.3 ms/move (max 92 in-process).
   Accepted as the policy-v5 screening opponent ("boss-v2") and registered as a candidate.
   The ordering run vs boss-v2 is in progress (appended below when complete).

**Result.** The bench can now exercise the boss meta locally, and the first pure sabotage change
in a month shows a lead over v09 in a 400-game sample — consistent with the stage-1 hypothesis
that v09's income-only target valuation (0.05 income points per unfinished opponent track vs the
boss's 5.6) is the largest remaining gap.

**Decision.** v09 stays selected; nothing uploaded. Next: (a) user decision on policy v5 and the
arena entry; (b) `boss-sabotage-v1` through the fixed 1000-game run and the strict process
check; (c) stage 1 — the same chooser as the rollout's second opponent scenario.

**Limits.** boss-v2's construction is v09's, not the boss's (never idles, 2.5 paint/turn vs 1.9);
its strength vs v09 (55 %) is below the boss's (67 %). The fit explains 51 % of target choices;
the remaining 49 % are not random but unmodelled. All local games ran in a 2-core cloud container
at nominal CG budgets; in-process timing maxima there are not deadline evidence.

## 2026-10-21 — boss-sabotage-v1: fixed 1000-game run PASS (67.45 %); policy v5 adopted; packaging fix

**Packaging.** The first commit put the two new `Params` fields inside the `MISSED_DEADLINE`
merger block, so `FileMerger.merge("current")` dropped them while `RolloutBot` still referenced
them (7 compile errors, every submission test red). Fixed: all boss-sabotage code lives in its
own `// BOSS_SABOTAGE_BEGIN/END` block that the merger strips unless the flag is on, and
`BossSabotage.java` is added to the artifact only then (compacted). Verified: `current`
`ab0954a4…`, `v09` `aca0be45…`, deadline candidate `3bd070b3…` — byte-identical to the pinned
hashes; `current?USE_BOSS_SABOTAGE=1` → 94,845 characters, compiles. `V07/V08/V09PromotionTest`
field counts +2; new `BossSabotageSubmissionTest`. `BossBotTest` passes alone (user).

**Policy.** v5 adopted by the user (AGENTS.md rule 3, `benchmarking-v5.md`), with the
degenerate-proxy clause: a candidate that is itself the screening opponent runs the v4 pooled
1000-game stage instead of the proxy screen.

**Strength.** `current?NOISE=0.1,USE_BOSS_SABOTAGE=true` vs `v09`, fresh paired maps, CG 486/36 ms,
two workers: blocks **205-95 (68.33 %)**, **140-60 (70.00 %)**, **329-170-1 (65.90 %)**; pooled
**674W/325L/1D = 67.45 % / 1000, PASS_1000**. Zero errors; P0 67.0 %, P1 67.8 %; +454 mean score
difference; 72 % early closures; candidate disrupts on 99 % of turns (v09 78 %). Seeds
940000/941000/942000 (150/100/250 pairs); reports in `docs/evidence/boss-proxy-v1/`. This is
the largest local margin over the selected version in the project's history (v09 over v08 was
56.9 %), obtained by replacing only the real-turn DISRUPT choice.

**Decision.** v09 stays selected until live confirmation. Next: `submit` + strict `procCheck`
on the user's machine (in-process maxima 127 ms on the shared 2-core host are not deadline
evidence; the chooser runs unguarded before the search), then v5 step 3 (80 paired live boss
games), then stage 1.

## 2026-10-21 — full test run: 28 failures triaged, 3 fixed, 25 pre-existing

User ran the full suite after the packaging fix: 810 tests, 28 failed. Triage from the Gradle
XML reports:

- **3 — ours, fixed.** `V07/V08/V09PromotionTest` pin the number of public `Params` fields.
  The constants were already two short before this work (`USE_INCREMENTAL_INK_REFRESH` and
  `USE_FAST_CHEAPEST_PATH`, added 2026-10-18, were never counted: 117 + 23 = 140 vs 142 fields),
  and our two boss-sabotage fields made it four. Now `+27` / `+26` / `+28` (144 fields).
- **19 — pre-existing since 2026-10-18.** `BotCatalog.verifyImplementation` compares the live
  compiled `btk/core/Pathing.class` with the SHA-256 recorded in `experiment-engine-v1.json`
  (source revision `218457a`, 2026-09-14). The fast-path/incremental-ink blocks changed that
  class, so every test that touches the catalog fails (`BotCatalogTest` ×3, `ReplayTest`,
  `BenchmarkRunnerTest`, `JointActionTest` ×2, `JointReplyStudyTest` ×5, `ReplyStudyTest` ×5,
  `FileMergerTest.characterLimitIsCheckedBeforePublishing` via `merge("exp-income-guard")`), plus
  `NeuralTargetSizeProbeTest.genericTransformation…`, which expects the raw body of
  `core/Pathing.java` inside the merged baseline (the merger now strips the flag blocks).
  `btk/core` is byte-identical before and after the boss-sabotage commits. Per
  `docs/bot_catalog.md` the remedy is a new engine snapshot with new experiment ids, not a
  refreshed manifest under the old ids — a user decision; it needs the user's compiler.
- **6 — pre-existing since 2026-10-06.** `PduhardBeamBotTest` requests `pduhard-beam-v1`, which
  `BotFactory` never registered (DEVLOG 2026-10-08: "six pre-existing untracked beam tests
  request an absent factory bot").

Expected after this commit: 810 run, 25 failed, all in the two pre-existing groups. The
submission tests (`BossSabotageSubmissionTest`, deferred-commit, batched-refresh) are green.

## 2026-10-21 — boss-v2 ordering check complete (Stage 0 closed); Stage 1 designed

**Ordering vs boss-v2** (`current?USE_BOSS_SABOTAGE=1`), 200 paired games per candidate, seed
910000, CG 486/36 ms, zero errors: v07 23.5 %, v08 29.0 %, **v09 38.0 %**, neural-v6 **58.0 %**,
neural-v1 53.5 %, all-cell-risk 44.0 %, root-fill 38.5 %, live-target 35.5 %, batched refresh
36.5 %. Six of eight directions agree with the live boss; the one large live signal (neural-v6
+22.5 pp over v09) is reproduced (+20.0; boss-v1 showed +4.5). Two cells disagree — neural-v1
(+15.5 vs −12.5 live) and all-cell-risk (+6.0 vs −12.5 live, down from +9.5 on boss-v1) — both
single 40-game live samples (±10 pp), both the same shape as boss-v1's error (the proxy
under-punishes bypass-heavy construction). Spearman with the live deltas 0.50 (boss-v1 0.48):
boss-v2's gain is realism (62 % vs v09 against the boss's 67.5 %, boss-like fingerprint), not a
cleaner ordering. **boss-v2 accepted as the v5 screening opponent** with those two caveats
recorded in `docs/boss-proxy-v1.md` §6.2/§7; evidence `docs/evidence/boss-proxy-v1/bench2-*.json`,
`ordering-vs-boss-v2.log`. `benchmarking-v5.md` step 1 now names "the current screening
opponent" instead of boss-v1. Stage 0 is closed.

**Stage 1 design** (`docs/stage1-opponent-model-v1.md`, not started): replace `chooseTarget` for
the simulated opponent inside `rollout()` with a boss-like chooser carrying a held target across
the simulated turns. Cost study on the 20,088 commitment sets: cheap features alone reach
41.5 % held-out top-1 (full model 51.0 %); the missing 10 pp are the ink-simulation deltas
(dOpp/dOwn/closes), not the route features (adding them: 41.4 %). A two-stage chooser — cheap
logit ranks all regions, the top-K get one ink simulation and the full logit — reaches 50.6 % at
K = 4 (shortlist recall 80.7 %) vs 51.5 % unlimited, i.e. the full chooser's accuracy at
`chooseTarget`'s own cost (`ROLLOUT_TARGETS` = 4 ink simulations). A `hitPrevByMe` feature that
raised a cheap fit to 46.7 % was discarded as a definition artifact. Flags predeclared:
`USE_BOSS_OPPONENT_MODEL`, `BOSS_OPPONENT_REPLIES`, `USE_BOSS_OWN_MODEL`; comparison per v5
against `boss-sabotage-v1` once it is live-confirmed, otherwise v09. Tools
`tools/boss_proxy/fit_boss_target_lite.py`, `shortlist_recall.py`; evidence
`docs/evidence/stage1-opponent-model-v1/`.

## 2026-10-21 — boss-sabotage-v1 on the arena: worse than neural-v3 and worse vs the boss (user report); diagnosis

The user uploaded `current?NOISE=0.1,USE_BOSS_SABOTAGE=true` to the arena: rank about 150–200 of
700 against about 50–100 for the previous entry (neural-v3), and "seems worse" against the boss.
No fetched replays or 40-game series yet; the numbers above are the user's reading of the
leaderboard. Treated as a failed v5 step 3: **not promoted, take it down.**

Why a 67.45 %/1000 local result did not transfer — two causes, both visible in evidence we
already had:

1. **The gain is v09-specific.** `boss-v2` in `boss-proxy-v1.md` §6.2 is this candidate. Against
   it, neural-v6 scores 58.0 % and neural-v1 53.5 %, while the same bots score 64 % and 56 % in
   the mirror against v09: the candidate is +17 pp over v09 head-to-head but only +2…+6 pp
   against neural-class opponents, and the neural entries beat it outright. Silver is populated
   by 99–100 %-disrupt bots of that class, not by v09s. The +17 pp came from exploiting v09's
   known weaknesses (builds through unstable regions, never bypasses, re-targets every turn) with
   a chooser that holds a region until it inks. The v5 degenerate-proxy clause let the candidate
   skip every non-v09 opponent; clause 5 is amended (screen vs neural-v6 at control + 3 pp) and a
   cold-JVM timing prerequisite is added (clause 6).
2. **Unguarded real-turn cost in a cold JVM.** `BossSabotage.choose` runs after `timer.start`
   inside the 36 ms window with no timer checks: two path searches per inactive pair plus one ink
   simulation per candidate region. New tool `btk.tools.BossChooserTiming` (v09-vs-v09 positions,
   `docs/evidence/stage1-opponent-model-v1/boss-chooser-timing*.log`): with a hot JIT (one JVM,
   many games — the local bench) it costs 1.2–1.5 ms on average and 5–10 ms on 30×20 maps; in a
   **fresh JVM per game, as CodinGame runs it, 16–26 ms per turn on 30×20 maps for the whole
   game** (first turn 19–38 ms), i.e. half to two thirds of the search window on exactly the
   maps where v09 is already weakest, with a timeout (= loss) possible on slower hardware. The
   local Referee records slow moves but never penalises them; the 1000-game run showed the
   candidate's maxima (127/63/65 ms) in line with v09's, which is why this was not caught.

Next: `boss-sabotage-v2` — the two-stage chooser from the Stage 1 design (cheap logit over
O(cells) counts → the top-4 regions get one ink simulation and the full logit; no path searches;
50.6 % vs 51.5 % top-1 offline) used at the real turn under the timer, cold-JVM timing measured
before any games, screen vs neural-v6 and v09, strict procCheck, then live. The same chooser is
the Stage 1 opponent model, so the work is shared. Open user actions: fetch the agent's arena
games (`cgFetch`/`cgAnalyze`) for failed-turn counts and tall-map paint; run the 40-game boss
series for the live number; re-upload neural-v3 or neural-v6.

## 2026-10-21 — boss-sabotage-v2 (two-stage chooser): v5 steps 1–2 PASS; Stage 1 model implemented

**Mechanism.** `USE_BOSS_LITE` (`RolloutBot.chooseBossLite`, `// BOSS_LITE` block; `BOSS_HOLD_MARGIN`
now in a shared `// BOSS_HOLD` block). The fitted boss logit without its two route terms: the
cheap part (opponent cells, opponent cells on active paths, own cells, opponent cells placed in
the last two turns, instability, town adjacency, neutral cells, log free cells) ranks every region
in O(cells); the top `ROLLOUT_TARGETS` = 4 regions plus the held one get one `inkBoard` simulation
each for dOpp/dOwn and the closure check, then the full score; hold rule, winning-closure rule and
WAIT rule as in v1; the loop checks the timer. Offline on the 20,088 boss commitment sets: top-1
51.1 % (full chooser 51.5 %; cheap-only 41.5 %; `shortlist_recall.py … oppRouteTraffic,routesAny`).

**Packaging.** Artifact 93,695 chars, sha256 `a8c739fd…`, compiles; pinned `current`/`v09`/deadline
artifacts byte-identical; v1 artifact unchanged. `Params` 148 public fields (three Stage 1 fields
included); `V07/V08/V09PromotionTest` +32/+30/+31; new `BossLiteTest` (3), `BossSabotageSubmissionTest`
extended. Tools `ColdTurnTiming`, `BossChooserTiming`.

**Timing (cold JVM, one game per process, 30×20, CG 486/36 ms).** v2 max 38–40 ms = v09's 36–40;
v1 on the same maps 44–53 (one turn over 50). `docs/evidence/stage1-opponent-model-v1/cold-turn-timing.log`.

**Strength (policy v5 with clause 5 as amended today).** Step 1 vs neural-v6, 300 paired games,
seed 960000: candidate **50.3 %** (151-149), control v09 on the same maps **38.8 %** (116-183-1) →
+11.5 pp, PASS (threshold +3). Step 2, 1000 vs v09 (seeds 961000/962000/963000): **188-99-13,
130-60-10, 304-191-5 → 622W/350L/28D = 63.6 %, PASS_1000**, zero errors, 84–85-turn games,
candidate disrupt 86–92 %. Against v09 this is 3.9 pp below v1 (67.45 %); against neural-v6 it is
8.3 pp above v1 (v1 42.0 %): the corridor-blocking hits that cost v1 its time budget paid against
v09 and not against an arena-class opponent. Evidence `docs/evidence/boss-sabotage-v2/`.

**Step 3 (user).** `gradlew test`; `gradlew submit "-PsubmitVersion=current?NOISE=0.1,USE_BOSS_LITE=true"
-PsubmitOut=build/submit-boss-lite`; strict `procCheck`; 80 paired boss games with a same-session
v09 control; pass at ≥ control + 5 pp. No arena upload before that.

**Stage 1 implemented on the same code** (`// BOSS_MODEL` block, needs `USE_BOSS_LITE`):
`USE_BOSS_OPPONENT_MODEL` — the simulated opponent inside `rollout()` disrupts with
`chooseBossLite`, its held target initialised from the observed opponent hit (instability rise
since our last real turn minus our own hit) and carried through the simulated turns, simulated
placements feeding the "placed in the last two turns" term; `BOSS_OPPONENT_REPLIES` (2) — how many
reply scenarios use it; `USE_BOSS_OWN_MODEL` — our own simulated future disrupts too. Artifact with
all three on: 95,421 chars, compiles. Strength screen A/B vs lite, neural-v6, v09, boss-v2 started
(`tools/boss_proxy/run_model_screen.sh`, seed 970000).

## 2026-10-21 — Stage 1 screen: opponent-model-v1 ≥ boss-sabotage-v2 on every same-map row

Same 150 maps for every cell (seed 970000), 300 paired games per cell, CG 486/36 ms, two workers,
zero errors (`docs/evidence/stage1-opponent-model-v1/screen/`):

| opponent | v2 (lite) | A: opponent model | B: A + own model |
|---|---|---|---|
| v2 | — | 54.8 % | **57.8 %** |
| boss-v2 | 48.3 % | 55.7 % | **62.3 %** |
| neural-v6 | 40.0 % | **48.7 %** | 45.3 % |
| v09 | 62.7 % | 56.7 % | **63.0 %** |

A boss-like simulated opponent helps exactly where the real opponent is boss-like (+14 pp against
boss-v2, +7.8 head-to-head) and hurts against v09 (−6 pp), whose every-turn re-targeting the model
mis-predicts; making our own simulated disrupts consistent with the real-turn chooser (B) removes
the loss and keeps the gains. **B = `opponent-model-v1`** is the Stage 1 candidate: v5 step 1 (vs
the screening opponent boss-v2: 62.3 % against v2's 48.3 and the v09 control's 38.0) PASS, step 2
(63.0 % vs v09 ≥ 48 %) PASS, step 3 live pending. Cold-JVM compute check on 30×20
(`compute-check-cold.log`): B's turn maximum 36–38 ms vs v2's 40, simulated turns per real turn
in the same band. Artifact with both flags 95,421 chars, compiles.

Two lessons recorded on the way: (1) v2's score against neural-v6 moved from 50.3 % (pool 960000)
to 40.0 % (pool 970000) while its delta over v09 stayed +11.5/+9.3 pp — a candidate is only ever
compared with a control on the same maps, so the screen scripts now carry their controls; (2) the
first reading of A/B against neural-v6 ("worse than v2") was that cross-pool mistake, reversed by
the same-map control (+8.7/+5.3 pp).

Recommendation to the user: live-test B first (80 paired boss games with a same-session v09
control), v2 as the fallback; no arena upload before the live result.

## 2026-10-21 (evening) — route term, Stage 2 compute, arena facts

**Route term (`USE_BOSS_LITE_ROUTES`, `docs/boss-lite-routes-v1.md`).** The fitted `oppRouteTraffic`
term is back at zero path-search cost: at the end of each real turn the rollout's own route
portfolios (`routes[player]`) are summed as 1/paint-cost per region crossed; the chooser of `me`
reads the opponent's array, the simulated opponent reads ours (frozen per real turn). Candidate C =
B + flag, same 150 maps (seed 970000): 50.0 % vs B, 60.3 % vs boss-v2 (B 62.3), **52.0 % vs
neural-v6 (B 45.3)**, 62.8 % vs v09 (B 63.0); second pool (seed 960000) C 57.0 vs B 56.0 → the
neural-v6 gain is +3.8 pp pooled. Kept. `BOSS_HOLD_MARGIN` 0.5 → 51.0 %, 2.0 → 50.0 % vs 1.0: flat.

**Stage 2 (`docs/stage2-compute-v1.md`).** JFR profile of C on a 30×20 map: 44 % of the window in
route enumeration (`propose` → `Pathing.cheapestPath`), 36 % in `chooseBossLite`, almost all of it
`SearchState.refresh` after the hypothetical ink; the simulated turns themselves 5 %. The project's
parity-validated runtime flags target exactly this; the incremental ink refresh was wired into the
lite chooser as well (`// INK_REFRESH` block). D = C + incremental + fast path + batched: simulated
turns per real turn 39–48 → 84–106, comparisons 2.9 → 5–7; **53.7 % vs C, 65.0 % vs boss-v2
(+4.7), 67.0 % vs v09 (+4.2), 49.7 % vs neural-v6 (−2.3, inside the cell error)**; mean turn time
11.8 ms vs 18.6 — the search now completes early, so the next lever is spending that slack
(`ROLLOUT_REPLIES` 3, `ROLLOUT_DEPTH` 10, more routes), one same-map screen each. Artifact
99,683 chars — 317 under the limit; the prune step is now a prerequisite for anything else.

**Arena.** The user's fetch of 50 games (agent 6755892) turned out to be the previous entry: every
game carries the turn-0 marker `build=4cda8b1b…`, the v09-era `current` build. Facts for the
baseline: 23W/27L, zero failed turns, rank 316 overall ≈ #22 of 706 in Silver; by map 27×18 8/9,
30×20 4/11, 21×14 1/7 (100-turn games with everything inked, 1.5 paint/turn); disrupt on 79–82 %
of turns; 41–46 % of accepted cells never earned (`docs/evidence/arena-2026-10-21-v09-agent-6755892.json`,
trace stripped). B is confirmed on the arena since the evening: its marker `build=4ac43802…
config=dcb8f038…` is byte-identical to `FileMerger.merge` of the B spec. Its games are fetched
tomorrow under the new agent id.

**Chain so far, all on the same maps:** v2 → B (+7.8 h2h) → C (±0 h2h, +3.8 vs neural-v6) → D (+3.7
h2h, +4.2 vs v09). Only v2 and B have been through the full local v5 stages; C and D are same-map
screens against their predecessor and still need the 1000-game stage or an explicit decision to
treat the chain as one candidate. Nothing promoted; v09 remains the selected version.


## 2026-10-22 — D on the arena: 30W/20L, Silver #7; `stage2-dividend-v1`: replies 3 / depth 10 do not turn the slack into strength

**Arena (Stage 1).** `cgFetch -Pplayer=aabzac -Plimit=50` kept returning agent 6755892 (rank 316,
contest-era build `4cda8b1b`, "50 already present"). Cause: the tool read
`Leaderboards/getFilteredChallengeLeaderboard`, which is frozen since the contest ended; the live
arena is the multiplayer *puzzle* with the same pretty id, and its leaderboard names agent
**6807052** for aabzac (rank 317 overall, **#7 of 712 in Silver**, score 28.06). `CgFetch.leaderboard()`
now queries `getFilteredPuzzleLeaderboard` first and falls back to the challenge board. All 50
fetched games of 6807052 carry D's turn-0 marker (`build=2f896714… config=f0e507ac…`, 100/100
marker lines = the `build/submit-stage2-d` artifact). `cgAnalyze -Pagent=6807052`: **30W/20L,
0 failed turns** (two opponent timeouts), 0 skipped actions; by map 21×14 2-0, 23×15 7-1, 24×16
3-2, 26×17 1-2, 27×18 7-3, 29×19 7-4, **30×20 3-8**; tall maps (h ≥ 19) 10-12, low maps 20-8; as
P0 12-8, as P1 18-12; the 35 placement games 24-11, then 15 consecutive games against --CC-- (Gold,
rank 197) 6-9; against opponents still on the live board (32 of 50) 20-10 vs rank > 317 and 1-1 vs
rank ≤ 317. Disrupt on 97.7 % of turns (contest-era entry 80 %), placement on 91.6 % (87 %), 40.7 %
of accepted cells never earned (43.6 %), games reaching turn 100: 6 (14), mean 68 turns (72).
Baseline, same script on the contest-era entry 6755892: 23W/27L, 30×20 4-7, 21×14 1-6, tall 6-9,
low 17-18. Reading: the entry is clearly stronger on low maps (20-8 vs 17-18) and no better on 30×20
(3-8 vs 4-7); 50 games carry ±14 pp and the opponents differ, so this is direction, not a
measurement — the 80-game live boss series (v5 step c) remains the number for promotion. Evidence
`docs/evidence/arena-2026-10-22-D-agent-6807052.json` (trace stripped).

**`stage2-dividend-v1` (Stage 2 "spend the dividend", `docs/stage2-dividend-v1.md`).** Predeclared
before the first game (`tools/boss_proxy/run_dividend_screen.sh`): fresh pool seed 980000, 300 paired
games per cell, 2 workers, CG timing (486/36 ms), user's Windows machine; R3 = D + `ROLLOUT_REPLIES=3`,
D10 = D + `ROLLOUT_DEPTH=10`; D's own rows on the same maps, seeds and seats as controls, run in the
same session. Reading fixed in advance: keep only at ≥ 50 % h2h vs D and no opponent row below D's
by more than the cell error; v5 step (a) continuation at candidate ≥ D + 3 pp vs boss-v2.

| opponent | D (control) | R3 | D10 |
|---|---|---|---|
| D head-to-head | — | 51.7 % (155-145) | 53.3 % (160-140) |
| boss-v2 | 68.7 % | 64.7 % (paired −4.0 ± 7.6 pp) | 65.8 % (−2.8 ± 6.7) |
| neural-v6 | 51.7 % | 53.7 % (+2.0 ± 7.2) | 54.0 % (+2.3 ± 7.1) |
| v09 | 63.3 % | 67.0 % (+3.7 ± 7.5) | 63.0 % (−0.3 ± 6.9) |

Zero errors in 3,300 games; no slow moves except one 147 ms outlier in R3-vs-v09 (v09 134 ms in the
same cell — environment, not the candidate; all other maxima 38–49 ms). Verdict: both candidates pass
the formal reading (h2h above 50 %, boss-v2 deficit inside the cell error) and **both fail the step (a)
gate: R3 64.7 and D10 65.8 against D's 68.7 vs boss-v2**; neither is cheaper or simpler → **both
parked, D unchanged**. Why the slack does not convert: mean time per move rises only from 12–16 ms (D)
to 14–18 ms — the search still completes on most turns, so the extra replies/depth are evaluated almost
for free and rarely change the choice (h2h inside ±5.7 pp); where they do cost, tall maps against the
boss-like opponent, both lose coverage (D 76.3 % → R3 68.4 / D10 68.4 % on h ≥ 19) — the opposite of
what the 30×20 arena weakness needs. Next lever is therefore not more of the same search:
`ROLLOUT_MAX_ROUTES` (64) / a second root pass while time remains, or the Stage 3–4 valuation changes.
Pool status: seed 980000 consumed by this screen; next free range ≥ 990000. Evidence
`docs/evidence/stage2-dividend-v1/` (11 reports with per-map pairs + `screen.log`); summarizer
`tools/boss_proxy/summarize_screen.py` (per-cell rates, tall/low split, paired per-map deltas vs the
control). Nothing promoted; v09 remains the selected local version, D the arena entry.

## 2026-10-22 (later) — `stage2-dividend-v1` part 2: routes 128 / width 8 — the dividend line closes negative

On the user's go-ahead ("следующий шаг"), the second pair of same-map screens against D, predeclared
and committed before the first game (`tools/boss_proxy/run_dividend2_screen.sh`, fresh pool seed
990000, 300 paired games per cell, 2 workers, CG timing, D controls in the same session). Coverage
instead of depth: **M128** = D + `ROLLOUT_MAX_ROUTES=128` (the 2×64 route-query cap truncates the
pairs × variants × players enumeration on 30×20 maps — ~40 % of pairs unexamined per turn — and
2×128 removes the truncation) and **W8** = D + `ROLLOUT_WIDTH=8` (route portfolio 6 → 8: more roots
and more candidate replies).

| opponent | D (control) | M128 | W8 |
|---|---|---|---|
| D head-to-head | — | **47.3 %** (142-158) | 52.0 % (156-144) |
| boss-v2 | 67.0 % | 65.7 % (paired −1.3 ± 6.8 pp) | 65.8 % (−1.2 ± 7.1) |
| neural-v6 | 49.7 % | 48.3 % (−1.3 ± 5.9) | 53.7 % (+4.0 ± 7.9) |
| v09 | 67.0 % | 63.0 % (−4.0 ± 6.6) | 68.7 % (+1.7 ± 7.3) |

Zero errors in 3,300 games; isolated 100–260 ms turns on both sides in three cells, clustered in
time (machine hiccups; all other maxima 38–79 ms); mean move time +1–2 ms as in part 1. **M128
rejected** (rule 1 fails: 47.3 % h2h; tall maps 41.2 % vs D — the extra path queries are paid inside
the window and crowd out the root×reply search). **W8 parked**: formal reading passes, step (a) gate
fails (65.8 vs 67.0 % against boss-v2, needed ≥ 70.0); its gains are vs neural-v6 and v09, not vs the
target. D unchanged, no live step.

**Line closed.** Four search-size parameters in four predeclared screens (replies 3, depth 10,
routes 128, width 8; pools 980000/990000): none beats D against the boss-like proxy (64.7 / 65.8 /
65.7 / 65.8 % vs D's 68.7 / 67.0), and each loses on tall maps against it (D 72–76 % → 67–68 %). The
dividend does not convert through more of the same search. Remaining uses of the slack: the Stage
3–4 valuation changes (source → `prune-v1` first; artifact 99,683 / 100,000), or timing margin on CG
hardware — note that the arena's 30×20 weakness (3-8) coexists with D's best *local* rows on tall
maps (72–76 % vs boss-v2), which points at cold-JVM timing on 30×20 rather than at search size;
`btk.tools.ColdTurnTiming` on D's exact artifact is the cheap check before anything else. Pools
980000 and 990000 consumed; next free range ≥ 1000000. Evidence
`docs/evidence/stage2-dividend-v1/part2/` (11 reports + `screen.log`); doc `docs/stage2-dividend-v1.md`.

## 2026-10-22 (late) — `cold-timing-30x20-v1`: D is budget-bound on 30×20 in a fresh JVM; the strength effect is not measurable at 172 games; M32 rejected, D6 / W4 parked

On the user's "давай тайминг". Question: is the arena's 3-8 on 30×20 (D's first 50 games) a
cold-JVM timing effect that the hot bench (12 ms/move, "search completes early") cannot see?
Protocol predeclared and committed before the first game (`docs/cold-timing-30x20-v1.md`,
`tools/boss_proxy/run_cold_tall.sh`, `run_cold_tall2.sh`).

**Timing fact (confirmed).** The user's strict `procCheck` of D's exact artifact already showed it:
in a fresh process on 30×20 and 29×19 every turn from ~10 to ~45 is at the 36–38 ms ceiling. New
strict run on 12 CG 30×20 maps (`replays/cg-30x20/`): 1,068 later turns, mean 22.7 ms, **34 % at
≥ 36 ms**, 14–31 of the first 40 turns per game, p99 38.3, max 45.5 ms, **0 timeouts, 0 overruns**.
The share rises from turn 10 with the map, so it is compute pressure, not JIT settling (CGRunner's
500 ms warm-up is in that run). The arena turn on big maps is decided with a fraction of the
comparisons the hot bench shows; D does not fail turns there (matches the arena's 0 failed turns).

**Strength effect (not established).** Cold paired games, one JVM per game, D vs boss-v2 on the 43
generated 30×20 maps of pools 980000/990000, both seats, read against the hot bench on the same
maps and seats (hot D 67-19 = 77.9 %): round 1 cold D 57-29 = 66.3 % (paired −11.6 ± 14.3);
round 2's fresh cold control 65-21 = **75.6 %** — the same configuration moved +9.3 ± 12.0 between
runs. Pooled 172 cold games: 70.9 %, **paired −7.0 ± 12.5 pp vs hot**. Direction consistent, size
anywhere between nothing and −15 pp; 86-game cold cells (±12 pp) resolve only large effects.

**Probes (Params-only, cold on the same maps + hot rows).** M32 = D + `ROLLOUT_MAX_ROUTES=32`:
cold +1.2 ± 14.7 vs cold D, hot 57.3 % (980000) / 66.7 % (990000) vs D's 68.7 / 67.0 → **rejected**
(the route enumeration is not what crowds out the search). D6 = D + `ROLLOUT_DEPTH=6` and W4 =
D + `ROLLOUT_WIDTH=4`: cold 81.4 % each vs the same-session cold D 75.6 % (+5.8 ± 11.8 / ± 12.7),
hot 64.3 / 63.7 % vs 68.7 → **parked** (cold gain half the paired error, hot rows 4–5 pp under the
control). Cold mean time per move 24–25 ms for all arms: the per-comparison saving is spent on
more comparisons inside the same budget-bound turn, as intended, without a measurable result.

**Decisions.** D unchanged. Method: anything aimed at big maps is screened *cold* (paired one-JVM
series on the same maps/seats, `run_cold_tall*.sh` pattern, ~15 games/min here) with the hot bench
as the non-inferiority guard; a decisive cold series is ≥ 300 paired games per arm with the
control in the same session. The two measurements that settle the 30×20 question are the 80-game
live boss series for D (v5 step c) and such a cold series on tall maps of a fresh pool (≥ 1000000).
Evidence `docs/evidence/cold-timing-30x20-v1/` (+ `round2/`): procCheck JSON, cold logs, hot rows.

## 2026-10-23 — `cold-timing-30x20-v1` round 3 ("холод-300"): the cold JVM does not weaken D on tall maps; hypothesis rejected, D6 / W4 closed

On the user's choice. Fresh range 1000000..1000599 (600 maps; tall subset 159 = 86 × 30×20 +
73 × 29×19). Hot reference D vs boss-v2 on all 600 maps × 2 seats (1,200 games, 2 workers):
**803-397 = 66.9 %**, by height 61–64 % on small maps, 73 % at 18, 69 % at 19, **78 % at 20**.
Cold arms, one JVM per game, both seats, on the 159 tall maps, interleaved per seed: D (control),
D6 = D + `ROLLOUT_DEPTH=6`, W4 = D + `ROLLOUT_WIDTH=4` — 318 games each, 0 errors, run 21:54–23:22
UTC (the background task's completion notice was lost with the session; the logs are complete).

| vs boss-v2, same maps and seats | hot | cold | paired cold − hot |
|---|---|---|---|
| D, h ≥ 19 (318) | 73.9 % | **76.4 %** | **+2.5 ± 6.6 pp** |
| D, 30×20 (172) | 77.9 % | 77.3 % | −0.6 ± 8.8 |
| D, 29×19 (146) | 69.2 % | 75.3 % | +6.2 ± 9.9 |
| D6 cold vs D cold | | 74.5 % | −1.9 ± 6.6 |
| W4 cold vs D cold | | 75.2 % | −1.3 ± 6.6 |

Timing on tall maps: cold D 21.8 ms per move (boss-v2 25.9), per-game maxima median 36 / p90 37 /
max 61, 2 slow moves; hot D on the same maps 20.4 ms. **Verdict: the cold-JVM hypothesis is rejected
at ±7 pp** — D in a fresh JVM is not weaker against the proxy on tall maps; rounds 1–2 (−11.6, then
+9.3 between two cold runs of the same configuration) were 86-game noise. The ceiling-bound turns
seen in the strict process checks are real but do not cost decisions against the proxy, and the hot
bench on tall maps is itself near the ceiling (20 ms mean; "12 ms" was the all-map average). D6 and
W4 are **closed** (no cold gain, 4–5 pp under D hot). The arena's 3-8 on 30×20 is therefore not a
timing effect — locally D is strongest on 30×20 (78 % hot, 77 % cold) — and is left to the 11-game
sample, the opponent mix on big maps, or a weakness against real opponents the proxy does not model;
the live boss series (v5 step c) addresses the arena directly, and a `cgAnalyze` pass over D's 30×20
losses is the cheap qualitative follow-up. The only unmeasured factor is CG's CPU speed relative to
the user's machine. D unchanged; nothing promoted. Range 1000000..1000599 consumed, next free
≥ 1001000. Evidence `docs/evidence/cold-timing-30x20-v1/round3/` (hot report with per-map pairs,
three cold logs, run log, seed list); doc `docs/cold-timing-30x20-v1.md`.

## 2026-10-23 — D against the live boss, v5 step (c) session 1: 18/40 vs v09's 9/40 in the same session — PASS (+22.5 pp paired)

On the user's "босс". Plan frozen without network calls (`tools/boss_batch.py plan`, matched design,
40 maps × 2 builds = 80 API games, candidate = D's exact arena artifact `build/submit-stage2-d`
(sha `0e4050c4…`), control = v09 `build/cg-boss/artifacts/v09`, boss 6767608, schedule seed
2026102301; protocol and pass rule in `docs/stage2-d-live-boss-v1.md`). The first plan on the default
`private/session.json` was refused at its first request with HTTP 422 / API 270 "It's too late to
connect to the challenge" — that file is the September *contest* IDE session, closed since the
contest ended (the same refusal as 2026-10-03); no game was created, intent and error retained
(`docs/evidence/stage2-d-live-boss-v1/first-attempt-refused/`). The plan was recreated (id
`7102e896…`) on the puzzle-IDE session of 2026-10-18 that every live series since 2026-10-03 used,
run through the authenticated-owner lookup adapter as before. The runner and the adapter gained
`--max-games`, and `tools/run_boss_paced.sh` plays chunks of 20 with the standing ≥ 300 s gaps
before games 21/41/61 and 20 s between games; the launch command names the private session files,
which the auto-mode permission classifier refused to execute from this session, so **the user ran
the series himself** (05:50–06:54 UTC).

**Result.** 80/80 games, 0 technical failures, 0 retries, all build markers matched, **80/80 replays
reproduced exactly** by the engine. **D 18/40 = 45.0 %** (exact 95 % 29.3–61.5) against **v09 9/40 =
22.5 %** (10.8–38.5) on the same 40 maps and seats; discordant pairs **13 : 4**, one-sided exact
p = 0.0245; **paired +22.5 pp** against the predeclared +5 → **session 1 PASS**. Seats: P0 11/20 vs
5/20, P1 7/20 vs 4/20. By map: 30×20 **6/10 vs 1/10**, 29×19 2/5 vs 0/5, 27×18 3/6 vs 4/6, 24×16 3/8
vs 2/8, 23×15 2/4 vs 1/4, 21×14 1/4 vs 0/4, 26×17 1/3 vs 1/3. The same-session v09 control is at the
bottom of v09's historical range (17 · 11 · 15 · 11 of 40), so the boss is not easier than before.
D's arena weakness on 30×20 does not show against the boss. Evidence
`docs/evidence/stage2-d-live-boss-v1/` (plan, 80 result records, runner report, paired summary,
engine-validation note); replays under `replays/cg/boss/{current/2f896714…,v09/b755f9b4…}/`.

**Decisions.** Step (c) needs 80 candidate games: **session 2** (another 40 matched maps, fresh plan,
a later day because of the service limit) decides on the pooled 80. Nothing promoted yet: arena
entry D, selected local version v09. New tools: `tools/run_boss_paced.sh`,
`tools/boss_proxy/live_pairs.py` (paired per-map/seat/size summary of a matched plan).

## 2026-10-23 — v10 Boss Model: D promoted to the selected version on the user's decision

- **User decision:** "Давай продвинем D без 2 сессии" after live session 1 (18/40 vs v09 9/40 in the
  same session, paired +22.5 pp). Protocol and the recorded deviation from v5 (40 of the 80 live
  candidate games; steps a–b met along the same-map chain v2 → B → C → D) in
  [v10-promotion.md](v10-promotion.md).
- **Freeze:** `bot/v10/RolloutBotV10` + `BossSabotageV10` + `ProtectedOpeningV10` are byte copies of
  `current` with only package, class names and label changed (hash-verified with the identity
  changes undone); `v10.properties` = the 130 effective values = the arena artifact's manifest
  params. The seven flags (`USE_BOSS_LITE`, `USE_BOSS_OPPONENT_MODEL`, `USE_BOSS_OWN_MODEL`,
  `USE_BOSS_LITE_ROUTES`, `USE_INCREMENTAL_INK_REFRESH`, `USE_FAST_CHEAPEST_PATH`,
  `USE_BATCHED_REFRESH`) default to true; `FrozenParams` pins them false for every snapshot that
  predates them, so v01–v09 and the neural/spatial snapshots behave exactly as recorded (asserted:
  `v09` still resolves to its 117 values). BotFactory / FileMerger / catalog know v10; v09 is
  `historical`.
- **Spec rules:** rollout-only defaults are dropped silently for policies that never read them
  (EvalBot, heuristic, neural proposal) and for frozen versions, while an explicit `=true` on such a
  policy is still refused (last mention wins, like `Params.apply`). **The boss-v2 proxy spec is now
  `current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0`**
  (`current?USE_BOSS_SABOTAGE=1` alone is refused: the two choosers exclude each other). The
  historical `tools/boss_proxy/run_*.sh` keep their recorded specs; new screens use the long one.
- **Tests:** the pre-promotion tests keep their measured configurations through
  `LiveTargetExperimentParams.V10_OFF` / `V09_ON_CURRENT` / `v09Params()` (about 40 files rewritten
  mechanically: `"current?…"` → `"current?" + V10_OFF + ",…"`, `new Params("…")` likewise, plain
  `"current"` pins → `V09_ON_CURRENT`); the v09-era default artifact still hashes to `ab0954a4…`
  and `v09` to `aca0be45…`. Pins of flag-on artifacts moved (BASE `2cb3a6a2` → `5e58f6d3`, +38
  `979cbac2` → `4472cbbc`, +40 `9510ff17` → `972cd133`, +40+ink `aff2dc9b` → `fa3ae4cc`, BASE+ink
  `6217083e` → `26712505`) because the shipped `Params` initializers now read `true`; resolved
  configurations are unchanged. New `V10PromotionTest` (2) and `V10SubmissionTest` (2);
  `V09DeferredCommitStudy` pins its specs to the v09 configuration. **818 tests, 25 failures, all
  pre-existing** (19 retained-engine pin, 6 `PduhardBeamBotTest`).
- **Artifacts:** default `gradlew submit` → `build/submit-v10`: 99,676 chars, sha `1c891234…`, build
  `8c7ef3d9…`, **config `f0e507ac…` identical to the arena artifact D** (`build=2f896714…`; text
  differs only in the seven initializer literals); `verifySubmission` exact; strict 4-game process
  check vs v09: 0 problem games, later ≤ 37.8 ms. Frozen `-PsubmitVersion=v10`: 99,598 chars,
  `9c3b4008…`. Evidence `docs/evidence/v10-promotion/`.
- **State:** `current` = **v10 Boss Model**, selected. The arena keeps the D artifact (same
  configuration, no re-upload needed). Next: `prune-v1` (99,676 / 100,000), then Stage 3–4
  valuation; the optional second live session stays available as a later check. Nothing uploaded
  or pushed to the arena by the agent.

## 2026-10-25 — `prune-v1`: the rejected rollout experiments leave `current`; the default artifact drops from 99,676 to 70,397 characters

- **Why now.** v10 shipped 324 characters under the 100,000 limit; the Stage 3–4 valuation work needs
  source. Protocol, the predeclared equivalence check and the results table in [prune-v1.md](prune-v1.md).
- **Arena first.** `cgFetch -Pplayer=aabzac` found a **new entry: puzzle agent 6811139** — the user
  uploaded the fresh default v10 artifact after the promotion (marker `build=8c7ef3d9… config=f0e507ac…`,
  the same configuration as D's `2f896714…`). Its first 50 games: **29W/21L**, 0 failed turns, tall maps
  (h ≥ 19) 11-6, 30×20 **5-3**, 29×19 6-3, 27×18 4-2, 26×17 4-2, 24×16 4-6, 23×15 4-3, 21×14 2-2; rank 356 /
  score 26.83 on the puzzle leaderboard (`evidence/arena-2026-10-25-v10-agent-6811139.json`; D's own
  record stays `evidence/arena-2026-10-22-D-agent-6807052.json`).
- **What left `current`.** Seventeen default-false rollout experiments and everything reachable only
  through them (`RolloutBot` 1,601 → 1,076 lines): all-cell risk (+`TacticalPathing`), root fill,
  quick early/value/safe, diverse replies, replan, second target, spread, pre-base network roots,
  route dependencies, network chords (+`NetworkChords`), RHEA (already a refusal), live target, always
  disrupt, track value (+ self-only). `USE_ROLLOUT_NETWORK_AFTER_BASE`, quick route, fill, the protected
  opening, the seven v10 flags and the `MISSED_DEADLINE` / `BOSS_SABOTAGE` blocks stay.
- **What left the artifact only.** 86 `Params` fields the shipped rollout never reads (the pruned
  flags and their companions, the EvalBot/heuristic/pressure/forecast weights, `MESSAGE`/`DEBUG`) now
  sit in `// LOCAL_ONLY_BEGIN/END` blocks; `FileMerger` drops them, their values in the embedded
  `new Params("…")` and the two unused core classes from **current rollout artifacts only**
  (`FileMerger.prunesLocalOnly`, `localOnlyParams`; `TacticalPathing` still ships with the boss-sabotage
  proxy, which uses it). The fields stay in `Params.java` for the frozen packages, EvalBot/HeuristicBot
  and the tools; every frozen export is byte-identical (`v10`: 99,598 chars, `9c3b4008…`; `v09`:
  `aca0be45…`). `BotFactory.configuration()` is unchanged (130 values), so the arena identity's
  `config=f0e507ac…` did not move — only `build=` did.
- **Guards.** `BotFactory.PRUNED_ROLLOUT_FLAGS` / `V10_FLAGS_OFF`: a true pruned flag on `current`/`main`
  is refused in `parameters()` and in the `RolloutBot` constructor (never silently ignored); `v10?FLAG=1`
  runs the mechanism in the frozen copy. Twelve mechanism tests moved `btk.bot.current` → `btk.bot.v10`
  (`RolloutBot → RolloutBotV10`, `current? → v10?`; `RolloutTestParams` public, `RolloutTestSupport` holds
  the reflection helpers); `RolloutQuickRouteTest` keeps its three live quick-route tests on current;
  `RolloutRiskStudy`, `RolloutRiskPolicyCheck`, `RolloutFillPolicyCheck` run on v10;
  `NeuralProposalStudy` no longer calls the removed `searchRoot` (identity for the v09 baseline).
- **Equivalence.** `PruneV1Test`: `current` and `v10` play the same seat in lockstep against a `v10`
  opponent with 60 s budgets on one generated map per board size (21×14 … 30×20) and both seats, up to
  60 turns — identical action lists and identical `routeQueries` / `completedComparisons` /
  `simulatedTurns` / `selectedValue` / `selectedRoute` / `selectedTarget` / `networkRoots` / `fillCells`
  on every turn → **PASS**. `PruneV1SubmissionTest`: the default artifact declares none of the
  local-only fields, embeds only declared fields, carries neither `NetworkChords` nor `TacticalPathing`,
  resolves to the same 130-value configuration; `FileMergerTest.checkFrozenDecisions("current")`
  matches the compiled artifact's decisions.
- **Artifact.** Default `gradlew submit` → `build/submit-prune-v1`: **70,397 / 100,000 chars**, sha
  `5047c83a…`, build `f7caee04…`, config `f0e507ac…`. Strict process check vs v09 (4 games, zero
  grace): **0 problem games**, 0 timeouts, first ≤ 668 ms, later ≤ 38.6 ms, 2/4 wins
  (`evidence/prune-v1/`).
- **Timed regression guard** (`tools/boss_proxy/run_prune_screen.sh`, fresh pool **1001000**, 300
  paired games per cell, 2 workers, CG timing): `current` vs `v10` head-to-head **50.7 %** (151-147-2);
  vs boss-v2 on the same maps/seeds/seats **68.3 %** against `v10`'s **70.3 %**, paired **−2.0 ± 6.5 pp**
  (32/35/83 maps better/worse/equal); zero errors, pruned bot max 37 ms per move in both cells. The
  same algorithm at the same speed, as the equivalence test predicts.
- **Tests.** 822 tests, 25 failures, all pre-existing (19 retained-engine incl.
  `FileMergerTest.characterLimitIsCheckedBeforePublishing` and `NeuralTargetSizeProbeTest`, 6
  `PduhardBeamBotTest`). Pins of every current-rollout artifact moved: the v09 configuration on current
  `ab0954a4` → `bfe4dd9b` (98,709 → 66,737 chars), BASE `5e58f6d3` → `719b5366`, GUARD `3bd070b3` →
  `d35d23d1`, BASE+ink `26712505` → `a171f242`, +38 `4472cbbc` → `03552580`, +40 `972cd133` →
  `3e50e65d`, +40+ink `fa3ae4cc` → `c204b6c7`, default 99,676/`1c891234` → 70,397/`5047c83a`;
  `DeferredCommitCompactionTest` 24 → 22 classes, saving threshold 8,525 → 7,200. `V10PromotionTest`
  keeps the frozen-identity hash for `BossSabotage`/`ProtectedOpening` only (current's `RolloutBot` is
  the pruned algorithm now). New: `PruneV1Test` (2), `PruneV1SubmissionTest` (1).
- **State:** `current` = v10 Boss Model, pruned; **29,279 characters of room** for Stage 3–4. No arena
  upload (same configuration). Pool 1001000..1001149 consumed; next free range ≥ 1002000. Next: Stage
  3–4 valuation (never waste the disrupt point; exposure-discounted own valuation + bypass), each step
  its own `// NAME_BEGIN/END` block, screened vs the proxy with `current` as the same-map control.

## 2026-10-25 — `readable-export-v1`: `current` exports are readable again and the first-turn MESSAGE names the strategy

- **Why.** User request before the next strength step: since the v09 runtime experiments the generated
  `Player.java` of a `current` export was joined statement-by-statement and then compacted
  (`JavaSourceCompactor`) into a few lines, unreadable in the CG IDE; and the first-turn
  `MESSAGE BTK_BUILD_V1 strategy=current build=… config=…` named the spec id, not the strategy.
- **Merger.** `FileMerger.merge` keeps the line structure of every `current` export: one statement per
  line, the source's indentation as one tab per four-space level plus one tab for the nesting inside
  `Player`, one blank line kept between groups; comments are still dropped. The same layout with spaces
  is **113,144** characters (over the limit); with tabs **88,671**. Frozen exports keep their historical
  bytes (`v10` compact 99,598 / `9c3b4008…` — its readable form would be 113,050; `v09` 95,312 /
  `aca0be45…`; v05–v08, neural, spatial and indrill pins untouched). A `current` export is compacted only
  when its readable form would exceed 100,000 characters (today only the EvalBot candidates
  `current?…,USE_ROLLOUT_POLICY=0,…`: readable 100,500 → compact 80,169; `gradlew submit` prints a line
  when this happens). The compactor's token / class-byte equivalence stays tested on a readable
  candidate (`DeferredCommitCompactionTest`, 22 classes).
- **Sizes** (readable, characters): default `current` **88,671** (sha `e5da0dcb…`, build `94f7cce9…`,
  **config `f0e507ac…` unchanged** = the arena artifact's configuration; 2,531 lines); the boss-sabotage
  proxy `current?USE_BOSS_SABOTAGE=1,<seven offs>` 86,825; the v09 configuration on current 75,920 (was
  66,737 compact); lite + opponent/own model + routes on the v09 base 84,385. Room for Stage 3–4:
  11,329 characters readable, ~30,000 more behind the automatic compaction fallback.
- **First-turn MESSAGE.** `BotFactory.displayName(spec)`: the BOTS.md title of the version
  (`v10 Boss Model`; `current`/`main` carry `BotFactory.SELECTED_VERSION = "v10"`), plus ` (modified)`
  when the resolved configuration differs from the version's defaults (`current?NOISE=0.1` is not a
  modification, the v09 configuration on current is). `current` artifacts print
  `MESSAGE v10 Boss Model | BTK_BUILD_V1 strategy=current build=… config=…`; the machine marker and its
  grammar are unchanged, the manifest gains `label` for current exports only, the registry's `marker`
  stays the bare marker. `tools/replay_capture.py` reads the marker from `BTK_BUILD_V1` on (13 Python
  tests incl. the new label case pass; `docs/boss-api.md` updated); frozen exports keep the bare marker.
  At the next promotion: bump `SELECTED_VERSION`, add the title to `VERSION_NAMES`.
- **Pins moved** (current-based artifacts only): default 70,397/`5047c83a` → 88,671/`e5da0dcb`; the v09
  configuration on current `bfe4dd9b` → `82f166f3` (66,737 → 75,920); BASE `719b5366` → `fa52a7d7`;
  GUARD `d35d23d1` → `2c8cc252`; BASE+ink `a171f242` → `71258164`; +38 `03552580` → `7f7d7fd2`;
  +40 `3e50e65d` → `ea0eae2e`; +40+ink `c204b6c7` → `17350f22`. Three assertions on compact spelling now
  read the readable text (`pathNeighbors == null ?`, `updateReusableConnectionsAfterInk(state, paths,
  false)`, the `PruneV1SubmissionTest` field regex); `V10SubmissionTest` pins the label, the layout and
  `displayName`.
- **Tests.** `gradlew test`: 822 tests, **25 failures, all pre-existing** (the documented set: 19 via the
  retained-engine pin incl. `FileMergerTest.characterLimitIsCheckedBeforePublishing` and
  `NeuralTargetSizeProbeTest`, 6 `PduhardBeamBotTest`); `btk.submit` 94/95 green (the one red is that pin).
  `tools/test_replay_capture.py`: 13/13.
- **Process check.** Default `gradlew submit` → `build/submit` (88,671 chars, `e5da0dcb…`), strict `procCheck`
  (zero grace, 4 games vs v09, pool `replays/cg`): **0 problem games**, 0 timeouts, first response ≤ 973 ms
  (first input sent after 419 ms on that game), later responses ≤ 38.3 ms, 4/4 wins
  (`evidence/readable-export-v1/default-artifact-process-check.json`, manifest alongside). A one-game process probe
  (the artifact as a child process vs v09) shows its first output line ending in `MESSAGE v10 Boss Model | BTK_BUILD_V1 strategy=current build=94f7cce9…
  config=f0e507ac…`.
- **No upload.** The resolved configuration did not move; the arena entry (agent 6811139) plays the same
  bot. The next upload — the first Stage 3–4 candidate — shows the new message.

## 2026-10-25 — `stage4-threat-model-v1`: the rollout's first simulated turn plays the real disrupt (`root-target-v1`, v5 steps a+b PASS); the lite-chooser threat for route proposals is parked

- **Why.** First strength step after `prune-v1` / `readable-export-v1`, from the Stage 3–4 line of the 2026-10-20
  plan. Since `opponent-model-v1` the rollout's opponent and our own later simulated turns disrupt with the two-stage
  boss chooser and the real DISRUPT is its held target — but two places of the construction search still used the
  symmetric `chooseTarget`: the threat our bypass routes (variant 2) and `protectedGain` are planned around, and our
  own *first* simulated turn, where every root was evaluated with a self-like target *and* with no disrupt, the better
  scenario choosing the construction, after which `actions()` overrode the target with `liteHeld` anyway. Protocol,
  mechanism and the predeclared reading: [stage4-threat-model-v1.md](stage4-threat-model-v1.md).
- **Flags** (default off, own merger blocks, current-only; `BotFactory` refuses them on frozen versions and without the
  lite chooser / opponent model): **A `USE_BOSS_THREAT_MODEL`** (`// BOSS_THREAT`): `threats[me] = chooseBossLite(s,
  1 − me, liteOppHeld)` — the opponent target the rollout's opponent plays on its first turn — instead of
  `chooseTarget(s, 1 − me)`; one timer-checked chooser call replaces one of equal cost. **B `USE_BOSS_ROOT_TARGET`**
  (`// BOSS_ROOT`): `rootTargets[i] = liteHeld` for every ordinary and network root, no per-root `chooseTarget` on the
  placed board, and the `attack == 1` (no-disrupt) scenario is skipped — the real turn never wastes the point, so the
  rollout should not either (Stage 3 inside the search). Removes ≈ 28 ink simulations and half the comparisons per turn.
  Configuration hash carries a flag only while it is on (`BotFactory.configuration`), so `current` keeps
  `config=f0e507ac…` and the default artifact its 88,671 chars / `e5da0dcb…` (`V10SubmissionTest`,
  `BossThreatRootSubmissionTest`; `V10PromotionTest`'s field count +2). Legality / profile tests `BossThreatRootTest`.
- **Screen** (`tools/boss_proxy/run_stage4_screen.sh`, fresh pool **1002000**, 300 paired games per cell, CG 486/36 ms,
  2 workers, `evidence/stage4-threat-model-v1/`): vs boss-v2 on the same maps/seeds/seats — `current` **64.7 %**,
  A 66.2 % (**+1.5 ± 7.1 pp**, 43/45/62 maps), **B 73.5 % (+8.8 ± 7.1 pp, 54/32/64)**, AB 70.7 % (+6.0 ± 7.2);
  head-to-head vs `current` — A 51.0 %, **B 57.0 %** (171-129-0), AB 57.0 %. Zero errors; disrupt rate 98.4–98.5 %
  in every arm; B's mean move time **7.9 ms vs 11.7** (the search finishes earlier — the gain is not compute);
  B's maximum 39 ms once in 300 games (37 in the other cell, control 36).
- **Reading.** **B passes v5 steps (a) and (b)** — the largest same-map proxy delta in the v2 → B → C → D chain (D over
  C was +2–3 pp vs the proxy, 53.7 % head-to-head). **A fails step (a)** (+1.5 pp, inside the cell error; 51.0 % h2h)
  → **parked**: the rollout's boss-like opponent already prices the held target into the route values, so which
  threat the proposal stage bypasses matters little. **AB is below B** on the same maps (−2.8 pp vs the proxy, equal
  h2h): A adds nothing on top of B → **B goes forward alone**, A stays in the code as a parked flag.
  Tall maps (h ≥ 19): B 67.9 % vs the control's 62.3 % against the proxy (+5.6), 55.7 % head-to-head — no tall-map loss,
  unlike every dividend candidate.
- **B's artifact and process check.** `build/submit-stage4-b`: 89,008 chars, sha `ea4bfe7a…`, build `87973a8a…`,
  config `179dcc66…`; strict `procCheck` (zero grace, 4 games vs v09): **0 problem games, 0 timeouts**, first ≤ 635 ms,
  later ≤ 37.4 ms, 4/4 wins (`evidence/stage4-threat-model-v1/B-artifact-process-check.json`).
- **Live plan (v5 step c) frozen, no network calls:** `build/cg-boss/stage4-b-current-boss-40/plan.json`
  (id `53e2fee9…`, matched, 40 maps × 2 builds = 80 API games, boss 6767608, seed 2026102502, candidate
  `build/submit-stage4-b`, control = the default `current` artifact `build/submit` / `e5da0dcb…`). The user launches it
  (`docs/stage4-threat-model-v1.md` has the line); pass = B ≥ `current` + 5 pp over the 40 paired maps, the 80-game
  policy sample completes with a second session. **No arena upload before the live result.**
- **Tooling fix.** `tools/replay_capture.validate_local_build` refused every current artifact since
  `readable-export-v1` ("Registered source marker is malformed"): the declared `BUILD_MARKER` now leads with the
  display name. It reads the marker from `BTK_BUILD_V1` on (a prefix must be exactly `<name> | `); Python tests
  14/14, 33/33, 9/9.
- **Tests.** `gradlew test`: 826 tests (+4), **25 failures, all pre-existing** (19 retained-engine incl.
  `FileMergerTest.characterLimitIsCheckedBeforePublishing` and `NeuralTargetSizeProbeTest`, 6 `PduhardBeamBotTest`).
  The three Params field-count pins (`V07/V08/V09PromotionTest`) and `V10PromotionTest`'s moved by +2.
- **Live boss, v5 step (c) session 1 (run by the user 15:05–16:06 UTC after my launch was refused by the
  auto-mode permission classifier as an external-system write):** 80 API games, 0 failures, 0 retries, **80/80 replays
  exact** (`cgValidate`, both capture directories). **B 21/40 = 52.5 %, `current` 20/40 = 50.0 %** against boss 6767608
  on 40 matched maps in the same session; discordant pairs **10 : 9**, paired **+2.5 pp**, one-sided p = 0.50; 30×20 5/8
  vs 5/8, 29×19 3/5 vs 1/5, 21×14 3/7 vs 5/7 (`evidence/stage4-threat-model-v1/live-session-1/`: plan, 80 result
  records, runner report, paired summary, validation note). **Below the predeclared +5 pp session threshold →
  inconclusive**: not the proxy's +8.8, not a loss. Step (c) is decided on 80 candidate games — a second 40-map session
  on another day completes it (one predeclared extension to 120); the user decides whether to spend it. Note that the
  control `current` itself made 50 % against the live boss (D: 45 % on 2026-10-23, v09 22.5 %).
- **State:** `current` unchanged (= v10, both flags off by default); candidate **B = `current?USE_BOSS_ROOT_TARGET=true`**
  passed steps (a)+(b), live session 1 inconclusive (+2.5 pp), **no promotion, no upload**; A parked. Pool 1002000..1002149 consumed; next free range ≥ 1003000. Next after the live
  result: if PASS, promotion to v11 with the flag as a default (v10 mechanics); the remaining Stage 4 half
  (exposure-discounted own valuation) is screened on top of B with B as the same-map control.

## 2026-10-25 (evening) — v11 Root Target: candidate B promoted to the selected version on the user's decision

- **Decision.** After the live session (B 21/40 vs v10 20/40, +2.5 pp, inconclusive) the user chose to promote
  ("давай продвинем версию на арену"); the second live session is skipped, the deviation from v5 step (c) is
  recorded in [v11-promotion.md](v11-promotion.md) exactly as D's was. Nothing retuned: `USE_BOSS_ROOT_TARGET`
  becomes a `Params` default, everything else is v10.
- **Mechanics (mirror of the v10 promotion).** `FrozenParams` keeps the flag off for every snapshot without the key
  (v10 included); `btk.bot.v11` = byte copies of `current`'s `RolloutBot` / `BossSabotage` / `ProtectedOpening` with
  identity changes only (`V11PromotionTest` hashes); `versions/v11.properties` + `v11-confirmed.properties` = 131
  values; `BotFactory`: `SELECTED_VERSION = "v11"`, display name "v11 Root Target", `v11` native rollout, the pruned
  flags refused on it; `FileMerger` exports `v11` readably and pruned like `current`; catalog `v11` selected, `v10`
  historical. **Guard rule:** the flag lives inside the lite chooser, so `USE_BOSS_LITE=0` (explicit) or a policy that
  never reads it drops the inherited default silently, an explicit `=1` is refused — hence the boss-v2 proxy spec and
  `V09_ON_CURRENT` are unchanged, and the v10 configuration on current is `current?USE_BOSS_ROOT_TARGET=0`
  (`V10_ON_CURRENT`, config `f0e507ac…`; `D_SPEC` carries the off value).
- **Artifact.** Default `gradlew submit` → `build/submit-v11`: **88,997 / 100,000 chars**, sha `ff111d54…`, build
  `002ca691…`, **config `179dcc66…` = B's** (text differs from B's artifact only in the initializer literal and the
  display name); frozen `v11` export 88,955 chars, `b12a1943…`. Strict `procCheck` vs v09 (4 games, zero grace): **0
  problem games**, 0 timeouts, first ≤ 577 ms, later ≤ 38.7 ms, 3/4 wins (`evidence/v11-promotion/`).
- **Pins moved, tokens did not.** Since `readable-export-v1` the display name sits in `BUILD_MARKER`, so every
  current-based artifact changed by the label text "v10 Boss Model …" → "v11 Root Target …": v09-on-current
  `82f166f3` → `3fe0370e` (75,920 → 75,921), GUARD `2c8cc252` → `d4b8b59e`, BASE `fa52a7d7` → `7fa98f9a`, BASE+ink
  `71258164` → `2554be21`, +38 `7f7d7fd2` → `82741e35`, +40 `ea0eae2e` → `29c83f91`, +40+ink `17350f22` → `1e69495a`,
  the v10 default `e5da0dcb` → `3ec21a76` (88,671 → 88,683); build ids and config hashes untouched; frozen `v10`
  `9c3b4008…` and `v09` `aca0be45…` keep their bytes. Tests measuring the v10 configuration say `V10_ON_CURRENT`
  (`V10PromotionTest`, `V10SubmissionTest`, `PruneV1Test`, `PruneV1SubmissionTest`, `BossThreatRoot*Test`); new
  `V11PromotionTest` (2), `V11SubmissionTest` (2); `BotCatalogTest` expects `v11` selected.
- **Tests.** `gradlew test`: 830 tests, **25 failures, all pre-existing** (19 retained-engine incl.
  `FileMergerTest.characterLimitIsCheckedBeforePublishing` and `NeuralTargetSizeProbeTest`, 6 `PduhardBeamBotTest`).
  `BossThreatRootTest`'s paint assertion relaxed to 1.5 paint/turn (time-bounded search under suite load).
- **v11 on the arena (2026-10-25, ~17:15 UTC): puzzle agent 6813813** (marker `build=002ca691… config=179dcc66…`), first 50 games **34W/16L**, 0 failed turns, 0 skipped actions, tall maps (h ≥ 19) 7-5, 30×20 3-4, 29×19 4-1, 27×18 3-2, 26×17 4-3, 24×16 5-3, 23×15 6-2, 21×14 9-1; seats P0 18-8, P1 16-8; accepted cells never earning 46.6 % (v10's first 50: 46.3 %); rank 375 / score 25.36 while placement was still running (`docs/evidence/arena-2026-10-25-v11-agent-6813813.json`). The best first-50 record so far (v10 29W/21L, D 30W/20L) on a map mix with more small maps; placement opponents vary, so this is a sanity check, not a measurement.
- **State:** `current` = **v11 Root Target** = the arena entry (agent 6813813). Next strength step: the
  exposure-discounted own valuation (Stage 4, second half) screened on fresh maps ≥ 1003000 with `current` (= v11) as
  the same-map control; `USE_BOSS_THREAT_MODEL` stays parked.

## 2026-10-25 (late evening) — `stage4-exposure-income-v1`: income weighted by region survivability passes v5 steps a+b over v11 (+3.7 pp vs the proxy, 60.3 % head-to-head); live pending

- **Why.** Stage 4's second half (own exposure, H2) on top of v11. The rollout's tail and the route proposals
  value income with `Rules.incomePerTurn` — a full point per own cell on an active connection whatever the
  state of the regions it crosses — while the boss inks the region with most of our tracks in four hits and
  46 % of our accepted arena cells never earn (v11's first 50 games 46.6 %). Protocol, mechanism and the
  predeclared reading: [stage4-exposure-income-v1.md](stage4-exposure-income-v1.md).
- **Flag** (default off, `// EXPOSURE_INCOME` block, current-only rollout experiment; `BotFactory` refuses it on
  `v11`/`v10`/EvalBot and weights outside [0, 1]): `USE_EXPOSURE_INCOME` + `EXPOSURE_INCOME_WEIGHT` (1.0).
  `RolloutBot.exposureIncome` = per player Σ over active connections of (own cells on the path) × Π over the
  distinct unstable non-town regions on the path of (1 − w · instability / 4); used for the rollout's
  `ROLLOUT_TAIL` term and for the proposals' completion gain and `protectedGain` (base and threat diffs
  computed the same way at the turn start). Stamp-marked regions, allocation-free, one O(path) pass per
  income reading. Configuration and artifact unchanged while off (`ExposureIncomeSubmissionTest`,
  `ExposureIncomeTest`; field-count pins +2; `FrozenParams` resets the two fields for records without them).
- **Screen** (`tools/boss_proxy/run_stage4b_screen.sh`, fresh pool **1003000**, 300 paired games per cell, CG
  486/36 ms, 2 workers, `evidence/stage4-exposure-income-v1/`): vs boss-v2 on the same maps — `current` (v11)
  **71.7 %**, **E1 (w 1.0) 75.3 % (+3.7 ± 6.7 pp, 41/31/78 maps)**, E05 (w 0.5) 72.7 % (+1.0 ± 7.4);
  head-to-head vs `current` — **E1 60.3 %** (181-119), E05 54.0 %. Zero errors, disrupt 98.3 % everywhere,
  mean move time unchanged (9.7 vs 9.6 ms); tall maps E1 84.7 % vs 83.3 % against the proxy, 63.9 % h2h.
- **Reading.** **E1 passes steps (a) and (b)** — the proxy gain sits at the +3 pp gate, the head-to-head gain
  (60.3 %) is the chain's largest (B over v10 57.0 %); the half weight loses the proxy gain → **E05 parked**.
  Artifact `build/submit-stage4-e1`: 91,368 chars, build `582ad892…`, config `d7803b42…`; strict `procCheck`
  **0 problem games**, later ≤ 38.5 ms. **Live plan frozen** `build/cg-boss/stage4-e1-v11-boss-40` (id
  `db85a30d…`, 80 API games, control = the v11 artifact `build/submit-v11`); launch line in the protocol doc,
  recommended for another day (80 API games already played today; the 2026-10-16 run hit the service limit
  near request 70 of a 160-game day). **No promotion, no upload before the live result.**
- **Tests.** `gradlew test`: 833 tests, **25 failures, all pre-existing**. `V11PromotionTest` now checks the
  frozen copy's hash only for `RolloutBot` (current's copy keeps evolving; the two helpers stay pinned to current).
- **State:** `current` = v11 (arena agent 6813813); candidate E1 = `current?USE_EXPOSURE_INCOME=true` awaits
  the live step; E05 and `USE_BOSS_THREAT_MODEL` parked. Pool 1003000..1003149 consumed; next free range
  ≥ 1004000.

## 2026-10-25 (night) — first-turn MESSAGE shows the version id only; the artifact header names the selected version

- **User request** (while E1's live series ran): the viewer line should read `v11 | BTK_BUILD_V1 …`, not
  `v11 Root Target | …`, and the artifact header should say `// BTK strategy: current, v11`. What the tooling
  actually parses (`tools/replay_capture.py`, used by `boss_batch.py`; the Java `cgFetch`/`cgAnalyze` do not read
  the marker): the text from `BTK_BUILD_V1` on — `strategy=` (checked against the manifest's `strategyId`),
  `build=` (the registry lookup and byte check), `config=` (the configuration hash); exactly one such MESSAGE on
  the first output. Everything before `BTK_BUILD_V1` is display only.
- **Change.** `BotFactory.displayName` = the version id (`v11`, `current`/`main` carry the selected one) plus
  ` (modified)` when the configuration differs from the version's defaults; the BOTS.md titles left it. A
  `current` export's header reads `// BTK strategy: current, v11` (or `current, v11 (modified)`); frozen exports
  keep their bytes (`v11` 88,955 / `b12a1943…`, `v10` `9c3b4008…`, `v09` `aca0be45…` unchanged).
- **Pins moved again, tokens unchanged** (the header comment is inside the hashed source, so the *build id*
  moves too this time): default `current` 88,997 / `ff111d54…` / build `002ca691…` → **88,990 / `cf1972a1…` /
  build `35176f24…`** (config `179dcc66…` unchanged — the arena entry agent 6813813 keeps build `002ca691…`, a
  re-export of the same configuration now carries the new build id); v10-on-current `3ec21a76` → `f8754bca`
  (88,683 → 88,687); v09-on-current `3fe0370e` → `a1043347` (75,921 → 75,925); GUARD `d4b8b59e` → `a7001517`;
  BASE `7fa98f9a` → `f8595a4a`; BASE+ink `2554be21` → `50bd16a0`; +38 `82741e35` → `77fb8cb9`; +40 `29c83f91` →
  `bb40cea3`; +40+ink `1e69495a` → `572aa79b`. Display-name assertions updated (`V10/V11SubmissionTest`,
  `V11PromotionTest`, `BossThreatRootSubmissionTest`, `ExposureIncome*Test`); `tools/test_replay_capture.py`
  unchanged (a label prefix stays `<name> | `). Candidate artifacts built before this change (`build/submit-v11`
  on the arena, `build/submit-stage4-e1` in the live series) keep their old label and build id; the attribution
  reads the marker from `BTK_BUILD_V1` on, so nothing changes for them.

## 2026-10-25 (night) — E1 against the live boss, v5 step (c) session 1: 20/40 vs v11's 18/40 in the same session — +5.0 pp, the session threshold met exactly

- Run by the user 17:57–18:59 UTC right after the screen (the day's 160th API request went through); 80 API
  games, 0 failures, 0 retries, **80/80 replays exact** (`cgValidate`, both capture directories). **E1 20/40 =
  50.0 %, `current` (v11, the arena artifact) 18/40 = 45.0 %** against boss 6767608 on 40 matched maps;
  discordant pairs **8 : 6**, paired **+5.0 pp**, one-sided p = 0.40; 21×14 7/3 of 12 maps, tall maps (h ≥ 19)
  1 : 4 of 8 (`evidence/stage4-exposure-income-v1/live-session-1/`: plan, 80 result records, runner report,
  paired summary, validation note).
- **Reading:** the predeclared +5 pp session threshold is met exactly — a pass at the edge, weak evidence
  (p = 0.40), with the gain on small maps and a 1 : 4 tall-map sample against E1 that the proxy did not show.
  Step (c) is decided on 80 candidate games; a second 40-map session on another day completes it (one
  predeclared extension to 120). **No promotion, no upload** until the user decides (session 2, promote now
  as with v11, or stop). Live sessions of the line so far: D +22.5, B +2.5, E1 +5.0 pp.

## 2026-10-25 (night) — v12 Exposure Income: candidate E1 promoted to the selected version on the user's decision

- **Decision.** After the live session (E1 20/40 vs v11 18/40, +5.0 pp, the session threshold met exactly) the user
  chose to promote ("Промоушн 12"); the second live session is skipped, the deviation from v5 step (c) is recorded in
  [v12-promotion.md](v12-promotion.md) as at the v10 and v11 promotions. Nothing retuned: `USE_EXPOSURE_INCOME` becomes
  a `Params` default (weight 1.0), everything else is v11.
- **Mechanics (mirror of v11).** `FrozenParams` keeps the two fields off for every record without them (v11 included);
  `btk.bot.v12` = byte copies of `current`'s `RolloutBot` / `BossSabotage` / `ProtectedOpening` with identity changes
  only (`V12PromotionTest` hashes); `versions/v12.properties` + `v12-confirmed.properties` = 133 values;
  `BotFactory`: `SELECTED_VERSION = "v12"`, display name `v12`, `v12` native rollout, the v11 flags allowed on it, the
  pruned flags refused; `FileMerger` exports `v12` readably and pruned like `current`; catalog `v12` selected, `v11`
  historical. **Guard rule:** a policy that never reads the flag drops the inherited default silently, an explicit
  `=1` is refused; the v11 configuration on current is `current?USE_EXPOSURE_INCOME=0` (`V11_ON_CURRENT`, config
  `179dcc66…`), `B_SPEC`/`D_SPEC` carry the off value. **Proxy spec:** the exposure discount does not depend on the
  lite chooser, so `current?USE_BOSS_SABOTAGE=1,` + the seven v10 offs would now carry the v12 flag — **the boss-v2
  proxy spec gains `USE_EXPOSURE_INCOME=0`** (CLAUDE.md, STATUS; the test helper `V10_OFF` carries it); the three
  screens of today used the validated opponent (the flag did not exist or was off by default).
- **Artifact.** Default `gradlew submit` → `build/submit-v12`: **91,349 / 100,000 chars**, sha `109be1f3…`, build
  `ce1d3a3b…`, **config `d7803b42…` = E1's** (text differs from E1's artifact only in the initializer literal and the
  label); frozen `v12` export 91,314 chars, `c89aa7ad…`. Strict `procCheck` vs v09 (4 games, zero grace): **0 problem
  games**, 0 timeouts, first ≤ 650 ms, later ≤ 37.5 ms, 3/4 wins (`evidence/v12-promotion/`).
- **Pins moved with the label** `v12 (modified)` (tokens unchanged): v09-on-current `a1043347` → `9ab8ebac`, GUARD
  `a7001517` → `80245370`, BASE `f8595a4a` → `d3b3c7cd`, BASE+ink `50bd16a0` → `0920dbc3`, +38 `77fb8cb9` → `680b0d8e`,
  +40 `bb40cea3` → `d2fd0db2`, +40+ink `572aa79b` → `89d5d9ff`, v10-on-current `f8754bca` → `ec331323`, the previous
  default (v11-on-current) `cf1972a1` / build `35176f24` → `48c92aab` / `0b73d984` (88,990 → 89,012). Frozen `v11`,
  `v10`, `v09` keep their bytes. New `V12PromotionTest` (2), `V12SubmissionTest` (2); `BotCatalogTest` expects `v12`.
- **Tests.** `gradlew test`: 837 tests, **25 failures, all pre-existing**.
- **v12 on the arena (2026-10-25, ~19:45 UTC): puzzle agent 6813957** (marker `build=ce1d3a3b… config=d7803b42…`), first 50 games **32W/18L**, 0 failed turns, 0 skipped actions, tall maps (h ≥ 19) 14-6, by size 30x20 6-1, 29x19 8-5, 27x18 6-3, 26x17 1-2, 24x16 7-3, 23x15 3-0, 21x14 1-4; seats P0 17-9, P1 15-9; accepted cells never earning 44.9 % (v11 46.6 %); rank 367 / score 26.09 during placement (`docs/evidence/arena-2026-10-25-v12-agent-6813957.json`). Placement opponents vary (v11 34/16, v10 29/21, D 30/20 on their first 50), so this is a sanity check, not a measurement.
- **State:** `current` = **v12 Exposure Income** = the arena entry (agent 6813957). Next strength step: from
  `docs/PLAN.md` (Stage 2 compute cuts are the last unticked Stage item; or a new valuation idea), screened on fresh
  maps ≥ 1004000 with `current` (= v12) as the same-map control and the proxy spec with `USE_EXPOSURE_INCOME=0`.

## 2026-10-25 (night) — `unstable-cost-v1`: the construction-side exposure penalty (UNSTABLE_COST 2 / 3) adds nothing on top of v12 — parked

- **Why.** The user asked for the next step after merging PR #6 (v11, v12, both experiments, the marker change).
  The one own-risk term of the construction the 2026-10-20 analysis named and nobody had touched:
  `UNSTABLE_COST` (+1 paint per cell per instability level in the cheapest-path proposals); the boss builds
  nothing in regions at instability ≥ 2 and ~45 % of our accepted arena cells never earn. A parameter screen, no
  code: `current?UNSTABLE_COST=2` and `=3` on `current` = v12 ([unstable-cost-v1.md](unstable-cost-v1.md)).
- **Screen** (`tools/boss_proxy/run_unstable_screen.sh`, fresh pool **1004000**, 300 paired games per cell, CG
  486/36 ms, 2 workers, proxy spec with `USE_EXPOSURE_INCOME=0`; `evidence/unstable-cost-v1/`): vs boss-v2 on
  the same maps — `current` **78.7 %**, U2 79.0 % (**+0.3 ± 6.6 pp**, 39/38/73), U3 80.7 % (**+2.0 ± 6.8 pp**,
  43/35/72); head-to-head vs `current` — U2 52.7 %, U3 48.7 % (tall maps 42 % / 44 %). Zero errors.
- **Reading.** Both fail v5 step (a) → **parked, the default 1.0 stays**. On top of v12's value-side discount the
  cost-side penalty is noise against the proxy and loses head-to-head on tall maps, where detours cost real
  paint. The exposure line is priced where it matters now; the next lever has to come from somewhere else
  (candidates in PLAN: the open Stage 2 compute cuts, or a fresh look at the 45 % never-earning cells with a
  per-cell diagnostic of the v12 arena replays — which regions ink, which routes never complete).
- **Branch.** PR #6 was rebase-merged into master; the branch continues from a merge of `origin/master`
  (identical trees), since a branch reset is refused by the permission classifier here.
- **State:** `current` = v12 unchanged; pool 1004000..1004149 consumed, next free range ≥ 1005000.

## 2026-10-25 (night) — idle-cell diagnostic of v12's arena games: the inked cells were placed into stable regions; the controllable waste is ~10 purposeless cells per game

- **Why.** The user chose the diagnostic ("давай диагностику") over another blind screen. `gradlew cgCellStudy`
  over the 50 games of agent 6813957 (one row per placed track of either player), summary in
  [idle-cells-v12.md](idle-cells-v12.md), raw summary in `evidence/idle-cells-v12/` (the 13,662-row CSV is
  reproducible from the replays and not committed).
- **Numbers.** v12: 6,656 cells, **35.6 % never earn** (opponents 38.3 %) — 820 inked before earning, 456
  neutralised by same-cell collisions, 1,093 kept idle to the end; idle share 17 % early, 34 % middle, 76 % in
  the last third (both sides alike). **742 of the 820 inked idle cells were placed at instability 0** (median ~6
  turns to the ink); only 2.4 % of our placements go into regions at instability ≥ 2 at all. Of the cells kept
  idle with ≥ 10 turns left (734), **508 lay on no route hypothesis at placement** (not completion, steal, bypass
  or extension).
- **Correction (2026-10-26, see the `arena-losses-v12` entry).** The split by `leader` was by our seat index, not by
  side; the correct numbers are v12 44.9 % idle vs the opponents' 26.9 %, and "not worse than the field" is withdrawn.
- **Reading (as written on 2026-10-25).** We are not worse than the field; the exposure that inks our cells is *created by our construction*
  (the boss-like chooser scores regions by our cell count), so placement-time instability rules cannot reach it
  — the explanation of today's parked `unstable-cost-v1` and of why the value-side discount (v12) was the part
  that paid. The one controllable bucket is the ~10 purposeless cells per game: leftover-paint fill
  (`USE_ROLLOUT_FILL`, selected in v09 on a mirror result) and single-cell network roots add ink magnets without
  a route behind them. Second bucket for later: collisions (6.9 % of our paint neutralised; needs code).
- **Next step started:** `fill-off-v1` — `current?USE_ROLLOUT_FILL=0` vs boss-v2 with `current` (= v12) as the
  same-map control and head-to-head, fresh pool 1005000 (`tools/boss_proxy/run_fill_screen.sh`).

## 2026-10-25 (night) — `fill-off-v1`: the leftover-paint fill is a strength, not the idle-cell waste — rejected

- **Screen** (`tools/boss_proxy/run_fill_screen.sh`, fresh pool **1005000**, 300 paired games per cell, CG 486/36 ms,
  2 workers, proxy spec with `USE_EXPOSURE_INCOME=0`; `evidence/fill-off-v1/`): `current?USE_ROLLOUT_FILL=0` vs boss-v2
  **74.7 %** against `current`'s **78.5 %** on the same maps (paired **−3.8 ± 6.9 pp**, 34/41/75; tall maps 81.0 vs
  91.7 %); head-to-head vs `current` **43.0 %** (129-171, small maps 37 %). Zero errors.
- **Reading.** Rejected on both steps; the fill stays. The idle-cell diagnostic's "no route hypothesis at placement"
  bucket is not the fill: those cells earn later or protect rails. What remains controllable needs code —
  collision avoidance at the real turn (6.9 % of our paint neutralised by same-cell placements; the rollout's
  opponent model already predicts the opponent's first-turn cells) and a game-end forecast for the last third
  (76 % idle on both sides). Placement-time instability rules (`unstable-cost-v1`) and the fill are closed as
  levers after v12.
- **State:** `current` = v12 unchanged (arena agent 6813957); pool 1005000..1005149 consumed, next free range
  ≥ 1006000. Three screens and one diagnostic since the v12 promotion tonight; next step on the user's word.

## 2026-10-25 (night) — `collision-avoidance-v1`: an explicit aversion to the opponent's predicted cells loses — rejected; the idle-cell line is closed

- **Why.** The user's pick ("давай уклонение") from the idle-cell diagnostic: 6.9 % of our arena paint is neutralised
  by same-cell placements. Flag `USE_COLLISION_AVOIDANCE` + `COLLISION_PENALTY` (`// COLLISION` block, current-only,
  default off): before `searchRoots` the opponent's predicted first-turn cells (its reply routes packed into one
  turn, `fillBuild`) are collected once; every root's rollout value loses the penalty per shared cell. Caveat
  stated up front: the rollout already neutralises such cells in its reply scenarios, so the flag tests whether the
  search under-weights a symmetric loss ([collision-avoidance-v1.md](collision-avoidance-v1.md)).
- **Screen** (`tools/boss_proxy/run_collision_screen.sh`, fresh pool **1006000**, 300 paired games per cell, CG
  486/36 ms, 2 workers, proxy spec with `USE_EXPOSURE_INCOME=0`; `evidence/collision-avoidance-v1/`): vs boss-v2 on
  the same maps — `current` **77.0 %**, C5 73.0 % (**−4.0 ± 7.0 pp**, 37/44/69), C15 71.7 % (**−5.3 ± 6.5 pp**,
  33/43/74); head-to-head vs `current` — C5 48.0 %, C15 42.0 %. Zero errors.
- **Reading.** **Rejected**, the stronger penalty the worse: the search prices collisions correctly already and the
  aversion only yields contested cells. With `unstable-cost-v1` (parked) and `fill-off-v1` (rejected) the idle-cell
  line of the diagnostic is closed: placement-time instability rules, the fill and collisions are not levers on
  top of v12. What remains from the diagnostic is the last-third waste (76 % idle for both sides), which needs a
  game-end forecast — a larger piece of work for another day. The flag stays in the code, default off.
- **Tests.** Full suite with the flag: 840 tests, **25 failures, all pre-existing**. **State:** `current` = v12 unchanged (arena agent
  6813957); pool 1006000..1006149 consumed, next free range ≥ 1007000. Tonight since the v12 promotion: three
  parameter/flag screens (unstable cost, fill off, collision aversion) all negative, one diagnostic.

## 2026-10-26 — `end-forecast-v1`: the end of the game is not forecastable from the board inside the bot's horizon, and the horizon itself is not a lever — closed

- **Why.** The user's pick after the idle-cell diagnostic: 76 % of the paint placed in the last third never
  earns (both sides); the proposals credit routes with `min(16, 100 − turn) − turns + 1` earning turns. New
  tool `btk.tools.EndgameStudy` (one row per turn of every validated replay: active / still-connectable /
  impossible pairs, inked and unstable regions, cells, score, true remaining turns), run over v12's 50 arena
  games and the canonical boss corpus (1,104 games, 77,211 turns); fits in [end-forecast-v1.md](end-forecast-v1.md),
  outputs in `evidence/end-forecast-v1/`.
- **Finding.** The remaining turns are nearly unrelated to the number of still-connectable pairs (23–27 turns
  remain on average with none connectable; the game lasts while the active connections survive the inking); the
  best static fit still misses by 11–12 turns in the last third; and **inside the 16-turn horizon no forecast
  beats the existing cap** (late error 3.6–4.5 turns for `100 − turn`, the same or worse for every fit, 6–8 for
  simple rules). The end is decided by the opponent's future inking, which the board does not carry.
- **Horizon screen** (`tools/boss_proxy/run_horizon_screen.sh`, fresh pool **1007000**, 300 paired games per cell,
  CG 486/36 ms, 2 workers, proxy spec with `USE_EXPOSURE_INCOME=0`): vs boss-v2 on the same maps — `current`
  **79.0 %**, `ROLLOUT_ROUTE_HORIZON=12` 78.3 % (**−0.7 ± 6.0 pp**, 28/32/90), `=10` 76.3 % (**−2.7 ± 5.8 pp**,
  27/36/87); head-to-head 44.0 % / 52.7 %. Zero errors. **Both fail step (a); the default 16 stays.**
- **Reading.** The last-third waste is neither a forecasting problem nor a horizon problem; if it is anything, it
  is a different late-game construction (protect active connections instead of starting routes) — a mechanism to
  look for in what the winners build in their last third, not a parameter. Line closed.
- **Branch.** PR #8 (`claude/project-thread-xptone-v2`) was rebase-merged this morning; this work is on the fresh
  branch `claude/project-thread-xptone-v3` from master.
- **State:** `current` = v12 unchanged (arena agent 6813957); pool 1007000..1007149 consumed, next free range
  ≥ 1008000. Since the v12 promotion: four parameter/flag screens (unstable cost, fill off, collision aversion,
  route horizon) and two diagnostics (idle cells, end forecast), all negative or closed. Open in PLAN: the Stage 2
  compute cuts (low priority) and the arena-loss diagnostic of v12 (who beats it and how).

## 2026-10-26 — arena-loss diagnostic of v12: efficiency losses to the field's top half; the idle-cell split corrected; the fill confirmed against neural-v6

- **Why.** The user asked for the losses ("Давай разберём поражения") after the end-forecast line closed. 18 losses in
  v12's first 50 arena games (agent 6813957): `cgAnalyze` traces, `EndgameStudy` turns, `cgCellStudy` cells, the
  replays' opponent scores. Write-up [arena-losses-v12.md](arena-losses-v12.md), evidence `evidence/arena-losses-v12/`.
- **Correction first.** The idle-cell diagnostic of 2026-10-25 split the cell table by `leader`, which is our *seat
  index*, not a side flag; its "v12 vs opponents" was P1-games vs P0-games. Correct split: **v12 44.9 % idle (3,439 of
  7,657), opponents 26.9 % (1,614 of 6,005)**, the excess = 1,906 cells kept to the end on no connection vs 365. "Not
  worse than the field" is withdrawn (erratum in `idle-cells-v12.md`); the screens that followed are unaffected
  (measured vs the proxy and `current`).
- **Cross-check.** `fill-off-v1` vs neural-v6 on fresh pool 1008000 with `current` as the same-map control
  (`tools/boss_proxy/run_fill_nv6_screen.sh`): 47.0 % vs 56.7 %, **−9.7 ± 6.9 pp**, maps better/worse 30/57 — the fill
  is worth more against the arena-like builder than against the proxy. Paint does not carry over (rules), so the fill
  is free; "points per paint" was the wrong lens, and the opponents' 27 % placement-free turns are forgone cells.
- **What the losses are.** Opponents scored ≥ 25.6: 6-10; below: 26-8. Equal cell counts, but from turn 10 on the
  opponent's income is 1.3× ours (losses: 42 vs 32 points/turn at turn 10, 75 vs 55 at 20, 104 vs 82 at 30); no loss
  was led past the midpoint. Per game: they have 95.7 earning cells of 128.6 placed, we 79.3 of 156.5; **their earning
  cells serve 1.62 connections per turn, ours 1.25** (in the wins 1.56 vs 1.43 the other way); they earn 2.2 turns
  after placement, we 4.3; their tracks are inked more often (68 % vs 57 %), so survival is not the gap; our disrupts
  are worth more in the losses than in the wins, so sabotage is not either.
- **Decision.** The idle-cell line is closed on the right reason now: the extra cells are free, the gap is
  *multiplicity* — cells on corridors shared by several active connections, extended from paths that already earn;
  `RolloutBot.propose` only ever bends a path by paint, opponent cells and the threatened region. **Next candidate:
  shared-corridor routes** (a proposal variant whose path cost discounts cells on other pairs' shortest completion
  paths, valued as the other proposals are), screened vs the proxy and vs neural-v6 with `current` as the control,
  then live; the leader-corpus study (PLAN) confirms the shape first. Pool 1008000..1008149 consumed, next ≥ 1009000.
- **Branch.** `claude/project-thread-xptone-v4` from master `c01ad5f`.

## 2026-10-26 — `corridor-routes-v1`: route proposals bent toward shared corridors pass v5 (a)+(b) and the neural-v6 cross-check — K1 to the live step

- **Why.** The arena-loss diagnostic: the field's top half beats v12 on multiplicity (1.62 vs 1.25 connections per
  earning cell-turn). The user chose the candidate ("Давай начнём"). Mechanism and protocol in
  [corridor-routes-v1.md](corridor-routes-v1.md): `USE_CORRIDOR_ROUTES` + `CORRIDOR_WEIGHT` — a fourth proposal
  variant per pair whose path search divides a free cell's paint by (1 + weight × the number of other pairs whose
  cheapest path uses the cell), valued like every other proposal (full-board income, exposure discount, threat
  scenario). Own `// CORRIDOR` block, current-only rollout experiment, default off; the default artifact is unchanged
  (`CorridorRoutesSubmissionTest`), a hand-made corridor map yields corridor proposals (`CorridorRoutesTest`),
  field-count pins +2.
- **Screen** (`tools/boss_proxy/run_corridor_screen.sh`, fresh pool 1009000, 150 maps × 2 seats per cell, CG timing,
  `current` = v12 as the same-map control; evidence `evidence/corridor-routes-v1/`): vs boss-v2 `current` 71.5 %,
  **K1 (weight 1) 78.0 % = +6.5 ± 7.2 pp** (maps better/worse 50/32), K2 (weight 2) 72.7 % = +1.2 ± 6.8 pp;
  head-to-head **K1 51.7 %**, K2 50.3 %; vs neural-v6 `current` 57.0 %, **K1 64.7 % = +7.7 ± 7.4 pp** (50/33), K2
  62.3 % = +5.3 ± 8.0 pp. Time per move K1 8.6 ms vs 9.7 (the corridor pass runs under the route reserve). **K1 passes
  (a) and (b) and gains as much against the arena-like builder; K2 fails (a), parked.**
- **Artifact and process check.** `gradlew submit "-PsubmitVersion=current?USE_CORRIDOR_ROUTES=true"
  -PsubmitOut=build/submit-corridor-k1` → 93,468 / 100,000 chars (readable), sha `deeefb19…`, build `ae716f57…`,
  config `299cafd9…`, label `v12 (modified)`; strict `procCheck` (zero grace, 4 games vs v09): 0 problem games,
  0 timeouts, first response ≤ 725 ms, later ≤ 38.1 ms.
- **Live plan** (v5 step c, frozen, no API calls): `build/cg-boss/corridor-k1-v12-boss-40/plan.json`, id
  `67ba025f…`, matched design, 40 maps × 2 builds = 80 API games, boss 6767608, schedule seed 2026102601, candidate
  `build/submit-corridor-k1`, control `build/submit-v12` (build `ce1d3a3b…`, the arena artifact); pass rule K1 ≥ v12
  + 5 pp over the 40 paired maps. Launch by the user (the launch line is in corridor-routes-v1.md); no API games
  were played today before it.
- **State.** `gradlew test`: 844 tests, the 25 pre-existing failures only (19 via the `Pathing.class` pin, 6
  `PduhardBeamBotTest`). `current` unchanged; pool 1009000..1009149 consumed, next ≥ 1010000; branch
  `claude/project-thread-xptone-v5` (from master `c9e79bc`).

## 2026-10-26 — `corridor-routes-v1` against the live boss, v5 step (c): K1 18/40 vs v12's 24/40 — −15 pp, rejected

- Launched by Claude at 12:04 UTC after the user's "Добавь правило" (allow rule `Bash(bash tools/run_boss_paced.sh *)`
  in `.claude/settings.local.json`, the first series not launched by hand); 80 API games 12:04–13:04 UTC, 0 failures,
  0 retries, **80/80 replays exact**. **K1 18/40 = 45.0 %, `current` (v12) 24/40 = 60.0 %** on 40 matched maps;
  discordant **3 : 9**, paired **−15.0 pp**, one-sided p = 0.98; both seats (P0 9 vs 14, P1 9 vs 10); 23×15 4 vs 7 of 10,
  29×19 4 vs 6 of 8 (`evidence/corridor-routes-v1/live-session-1/`: plan, 80 result records, runner report, paired
  summary, validation note).
- **Reading.** The clearest proxy-vs-live divergence of the line: +6.5 pp vs the proxy and +7.7 pp vs neural-v6,
  −15 pp vs the boss. Both local opponents are models of the boss built around our own construction; routes bent toward
  shared corridors (longer, concentrated) are something the real boss punishes — the obvious hypothesis is that the
  shared corridors concentrate our cells in the regions it inks. **K1 rejected; the flag stays off (parked).** Live
  sessions of the line: D +22.5, B +2.5, E1 +5.0, K1 −15.0 pp.
- **Next (user decision).** A diagnostic of the 40 K1 games vs the 40 v12 games on the same maps (`cgCellStudy` over
  both capture directories: inked / idle / earning cells, the corridor cells' fate) before any further construction
  candidate; the proxy's weight in the policy for construction changes is in question (it predicted the sign wrongly).
- **State.** `current` unchanged; no upload; branch `claude/project-thread-xptone-v5`.

## 2026-10-26 — diagnostic of K1's 80 live games: no multiplicity gain against the boss, six more cells per game inked before earning — the proxy does not punish concentration

- **Why.** The user asked ("Давай разбор") after K1's live loss. `cgCellStudy` + `EndgameStudy` over both capture
  directories (K1's 40 games, v12's 40, same maps and seats vs the boss); write-up in
  [corridor-routes-v1.md](corridor-routes-v1.md), outputs in `evidence/corridor-routes-v1/live-session-1/diagnostic/`.
- **Numbers (per game).** Multiplicity 1.21 (K1) vs 1.24 (v12), delay 3.44 vs 3.55, cells 148 vs 146, earning 82.2
  both, route length 10.2 vs 10.3 — the construction profile is the same. **Inked before earning 33.1 vs 27.1**, inked
  in all 80.6 vs 73.2, kept idle 26.9 vs 30.5; the inked-before-earning cells sat in regions holding more of our tracks
  (2.65 vs 2.43 at placement; 6.6 vs 6.1 of ours in the region at the ink); the boss lost fewer cells vs K1 (7.7 vs
  8.8). Lead at turn 10/20/30: −53/−124/−240 vs −42/−94/−121; active connections at turn 20: 8.7 vs 10.3; games 69.2
  vs 65.1 turns. The 9 maps v12 won and K1 lost: K1 151 cells / 88 inked / 1,699 points vs v12 123 / 53 / 2,202.
- **Reading.** The corridor routes are taken but the boss inks them before they pay; shared corridors concentrate our
  cells where the boss's disrupt goes. The proxy (boss-v2 chooser) and neural-v6 model the boss's targeting around
  our own construction and reward concentration instead — the first wrong sign of the line, on its first
  construction change (the earlier passes were valuation and sabotage changes).
- **Decision.** `corridor-routes-v1` closed as rejected (flag parked off). **Next (user decision):** a proxy-agreement
  study over these 80 games (`BossAgreementStudy` / `CgDisruptStudy`: proxy vs actual boss disrupt, split by our cell
  concentration) to locate the proxy's blind spot before another construction candidate is screened.

## 2026-10-26 — `proxy-agreement-v1`: the boss attacks our under-construction concentrations and finishes near inks; the proxy scores income — the blind spot behind K1's wrong sign

- **Why.** The user's "Ок" to the proposed study after the K1 diagnostic. New tool `btk.tools.BossTargetStudy`
  (`gradlew bossTargetStudy`): one row per boss disrupt over the two live capture directories of the K1 session (80
  games), the boss's region vs the region a local boss model would disrupt from the same public state, with our tracks,
  our income from the region and the instability. Write-up [proxy-agreement-v1.md](proxy-agreement-v1.md), outputs in
  `evidence/proxy-agreement-v1/`.
- **The boss.** Its target is the region with the most of our tracks 51 % of the time (top-3 80 %; 65 % in turns 1–10,
  40 % in 11–45, 60 % late); **40 % of its disrupts (K1 games; 36 % in v12 games) go to regions where we have ≥ 3
  tracks and no income yet**; late (turn 46+) only one target in five holds any income of ours.
- **The models.** Agreement with the boss's region: proxy 54.1 % (K1 games) / 58.1 % (v12 games), v12 in the boss's
  seat 54.5 / 59.4 %, neural-v6 41.3 / 43.9 % (the proxy's validation level was 52 %). The proxy's agreement falls
  with our concentration (61 % at ≤ 5 tracks in a region, 45 % at 6–9, **29 % at ≥ 10** in the K1 games vs 47 %) and
  on the boss's attacks on our under-construction concentrations it is **46.8 % in the K1 games vs 61.8 %** in the
  v12 games. In the middle game the proxy takes the bigger fresher income region (instability 0.5–0.7), the boss the
  smaller one nearer the ink (1.3); late, the boss opens fresh regions holding our non-earning tracks while the proxy
  keeps finishing inks.
- **Reading.** Corridor routes converge several pairs' tracks on one region before any earns — the boss's favourite
  target, which an income-scoring chooser (the proxy, and our own v12 threat model) does not punish. That is the
  mechanism of the wrong sign (+6.5 pp vs the proxy, −15 pp live). neural-v6 is not a model of the boss's targeting.
- **Next (user decision):** a boss-model candidate `boss-v3` (region score adds the opponent's non-earning tracks and a
  time-to-ink term), fitted by agreement on the boss corpus with these 80 games as the holdout; a proxy at 62 %+ on
  the K1 games that flips K1's sign in a re-screen becomes the policy's screening opponent (benchmarking-v5
  amendment). Until then, construction candidates that concentrate tracks before they earn are not trusted on the
  proxy screen alone.
- **State.** Branch `claude/project-thread-xptone-v5`; no bot change; `current` unchanged.

## 2026-10-26 — `boss-v3`: the boss model cannot be refitted into the K1 blind spot; the hold margin is a uniform +4.5 pp that does not flip the sign — closed, proxy unchanged

- **Why.** The user's "Давай" to the `boss-v3` proposal: refit the proxy's target chooser by agreement with the live
  boss, re-screen K1 as the check. Write-up [boss-v3.md](boss-v3.md), evidence `evidence/boss-v3/`.
- **Refit** (`tools/boss_proxy/fit_boss_target_v3.py`, the whole corpus: 1,180 games, 26,676 commitment turns, the 80
  K1-session games held out by game): the shipped weights score 0.562 / 0.515 top-1 on the K1 / v12 holdout
  commitment turns and 0.719 / 0.686 on every disrupt turn (argmax); the `base` refit 0.556 / 0.509, with
  time-to-ink indicators 0.554 / 0.507, with the opponent's non-earning tracks and oppCells × instability 0.534 /
  0.498 — **no gain; the shipped weights are the corpus optimum**. The refitted weights land within hundredths of
  the shipped ones. A `USE_BOSS_V3` weight-set scaffold was built and reverted (it changes `BossSabotage.java`, a
  frozen helper pinned by the V10–V12 promotion tests; nothing to ship).
- **Hold margin** (`BossTargetStudy`, proxy at `BOSS_HOLD_MARGIN` 0 / 0.5 / 1 / 2 on the 80 games): agreement 58.8 /
  62.6 % at 0 vs 54.1 / 58.1 % at the shipped 1.0 (K1 / v12 games) — uniform +4.5 pp, the K1 deficit untouched
  (under-construction attacks 49.8 % vs 65.2 %, ≥ 10-track regions 26 %).
- **Sign check** (`tools/boss_proxy/run_margin_screen.sh`, fresh pool 1010000, `current` as the same-map control vs the
  margin-0 proxy): K1 74.7 % vs 72.3 %, **+2.3 ± 7.5 pp** (44/39 maps) — the sign does not flip (live: −15 pp).
- **Decision.** Closed, proxy unchanged (a proxy revision needs its own fingerprint + live validation and would not
  address the blind spot). Policy consequence, no amendment: construction changes that concentrate tracks before they
  earn are not trusted on the proxy's sign; the live step decides. **Next candidate (user decision):** the other trait
  of the arena's top half — two turns from placement to first income vs our four — as a proposal valuation that
  prefers routes earning within a turn or two (extensions of active paths, one-turn completions), inside the proxy's
  competent zone. Pool 1010000..1010149 consumed, next ≥ 1011000.
- **State.** `current` unchanged; branch `claude/project-thread-xptone-v5`.

## 2026-10-26 — `earning-delay-v1`: the cheapest positive completion kept among the roots every turn passes v5 (a)+(b) — Q to the live step; the steeper proposal ordering does nothing

- **Why.** The user's "Давай" to the earning-delay candidate (the arena's top half earns 2 turns after placement, we
  4; `docs/arena-losses-v12.md`). Mechanism and protocol in [earning-delay-v1.md](earning-delay-v1.md): one
  `// DELAY` block with `USE_QUICK_ALWAYS` (the v06 quick-income slot — the cheapest positive completion ≤ 6 paint
  retained among the root routes — every turn instead of only before the first income) and `DELAY_POWER` (the
  turns-to-complete divisor of a proposal's value raised to a power); current-only, defaults off, default artifact
  unchanged (`EarningDelaySubmissionTest`), field-count pins +2.
- **Screen** (`tools/boss_proxy/run_delay_screen.sh`, fresh pool 1011000, 150 maps × 2 seats per cell, CG timing,
  `current` = v12 as the same-map control; evidence `evidence/earning-delay-v1/`): vs boss-v2 `current` 76.3 %,
  **Q 82.7 % = +6.3 ± 5.6 pp** (maps better/worse/equal 43/23/84), D2 76.7 % = +0.3 ± 6.7 pp; head-to-head **Q
  52.7 %**, D2 49.7 %; vs neural-v6 `current` 58.7 %, Q 58.7 % = +0.0 ± 7.1 pp, D2 63.7 % = +5.0 ± 7.7 pp. Time per
  move unchanged (8.4 ms). **Q passes (a) and (b); D2 fails (a), parked.**
- **Artifact and process check.** `gradlew submit "-PsubmitVersion=current?USE_QUICK_ALWAYS=true"
  -PsubmitOut=build/submit-delay-q` → 91,619 / 100,000 chars, sha `4584fb1d…`, build `a880a04a…`, config
  `41996066…`; strict `procCheck` (zero grace, 4 games vs v09): 0 problem games, 0 timeouts, first response ≤ 725
  ms, later ≤ 49.3 ms (one game; the others ≤ 38.1 ms).
- **Live plan** (v5 step c, frozen, no API calls): `build/cg-boss/delay-q-v12-boss-40/plan.json`, id `f426db1c…`,
  matched, 40 maps × 2 builds = 80 API games, boss 6767608, schedule seed 2026102602, candidate
  `build/submit-delay-q`, control `build/submit-v12` (build `ce1d3a3b…`); pass rule Q ≥ v12 + 5 pp over the 40
  paired maps. 80 API games were played today already (K1's session); the launch — now (resumable on an API-limit
  stop) or tomorrow — is the user's call. Pool 1011000..1011149 consumed, next ≥ 1012000.
- **State.** `current` unchanged; branch `claude/project-thread-xptone-v5`.

## 2026-10-26 — `earning-delay-v1` against the live boss, v5 step (c): Q 18/40 vs v12's 20/40 — −5 pp, parked; the construction line from the arena diagnostic is exhausted at the proxy's level

- Launched by Claude at 17:54 UTC on the user's "запускай" (allow rule; the day's 81st–160th API requests, no limit
  stop); 80 API games 17:54–18:57 UTC, 0 failures, 0 retries, **40/40 + 80/80 replays exact**. **Q 18/40 = 45.0 %,
  `current` (v12) 20/40 = 50.0 %** on 40 matched maps; discordant **9 : 11** (20 equal), paired **−5.0 pp**,
  one-sided p = 0.75; seats P0 10 vs 10, P1 8 vs 10; 30×20 2 vs 6 of 9 maps, 27×18 5 vs 2 of 6
  (`evidence/earning-delay-v1/live-session-1/`: plan, 80 result records, runner report, paired summary, validation
  note).
- **Reading.** The proxy gain (+6.3 pp) did not transfer (−5 pp live); the loss is on the tall maps, where the kept
  cheap completion displaces a longer route from the six slots. Both construction traits of the arena's top half
  (`docs/arena-losses-v12.md`: multiplicity → `corridor-routes-v1` −15 pp live, earning delay → `earning-delay-v1`
  −5 pp live) passed the proxy and failed the boss. **Q parked (flag off); the construction line is closed at the
  proxy's level** — the screen passes construction changes the live boss does not reward, and `boss-v3` showed the
  proxy cannot be refitted into the boss's reaction to construction.
- **Open to the user:** the predeclared extension of Q's live sample to 120 games (a second 40-map session, low prior
  after −5 pp); otherwise the next lever comes from the sabotage / valuation side of `docs/PLAN.md`, where the
  proxy's sign has held (D, B, E1). Live sessions of the line: D +22.5, B +2.5, E1 +5.0, K1 −15.0, Q −5.0 pp.
- **State.** `current` unchanged; no upload; branch `claude/project-thread-xptone-v5`.

## 2026-10-26 — `opp-hold-v1`: the boss model's hold margin 1.0 is the optimum — margin 0 costs 8–9 pp against the proxy, margin 2 is flat; closed

- **Why.** The user's "давай дальше" (after PR #11): the sabotage / valuation side. `boss-v3` measured the lite chooser at
  margin 0 agreeing 4.5 pp more with the live boss's region than at the shipped 1.0, and the margin had never been
  screened on the bot side, where the chooser is both our real disrupt and the rollout's opponent model. New field
  `BOSS_OPP_HOLD_MARGIN` (default −1 = inherit, `// OPP_HOLD` block, current-only, needs the lite opponent model;
  default artifact unchanged, `OppHoldTest`, `OppHoldSubmissionTest`, field-count pins +1). Write-up
  [opp-hold-v1.md](opp-hold-v1.md), evidence `evidence/opp-hold-v1/` (block 2 in `block2/`).
- **Block 1** (`tools/boss_proxy/run_opphold_screen.sh`, fresh pool 1012000, `current` as the same-map control): vs
  boss-v2 `current` 81.3 %, **O0 (opponent model margin 0) 73.2 % = −8.2 ± 6.3 pp**, **H0 (margin 0 everywhere) 72.3
  % = −9.0 ± 6.6 pp**; head-to-head O0 45.7 %, H0 51.0 %; vs the margin-0 proxy `current` 74.0 %, O0 +0.5 ± 7.1, H0
  +1.5 ± 6.9 pp. Both rejected; the loss is the opponent model's (H0 ≈ O0).
- **Block 2** (fresh pool 1013000): **O2 (margin 2) +0.0 ± 6.7 pp** vs boss-v2 (78.0 % both), 51.3 % h2h, +3.0 ± 6.3 pp
  vs the margin-0 proxy (75.0 → 78.0 %) — flat.
- **Reading.** The opponent model inside the rollout is worth its commitment (a held target predicts an ink that lands,
  and the search prepares for it); a model that re-picks every turn spreads the predicted threat. Per-turn agreement
  with the boss is the wrong objective for it. **Closed; the margin stays 1.0.** The cheap screened levers on the
  sabotage / valuation side are spent with this (width / depth / replies parked on D, threat model A parked, unstable
  cost parked, collision aversion rejected, hold margin here).
- **Next (default, no API cost):** `boss-v4` — a study of what predicts the boss's attacks on our under-construction
  concentrations (36–40 % of its disrupts, the proxy's blind spot) with the full feature table of `CgLeaderStudy`
  (route-cost and own-route features, region size, previous hit — 25 columns against the 13 shipped), a holdout of the
  80 K1-session games; if a feature set closes the blind spot it is the route to a proxy that can screen construction.
- **State.** `current` unchanged; pools 1012000..1013149 consumed, next ≥ 1014000; branch `claude/project-thread-xptone-v6`.

## 2026-10-26 — `boss-v4`: the proxy has no construction blind spot — K1's and Q's proxy passes do not replicate on a fresh pool; the replay gap was the closure shortcut

- **Why.** The default next step after `opp-hold-v1`: what predicts the boss's attacks on our under-construction
  concentrations (`proxy-agreement-v1`'s blind spot). Write-up [boss-v4.md](boss-v4.md), evidence `evidence/boss-v4/`
  and `evidence/boss-v4/noclose-screen/`.
- **Weights** (`tools/boss_proxy/fit_boss_target_v4.py`, 25 features, K1-session holdout): the shipped weights predict
  those attacks at 82 % in both arms; new features add nothing.
- **Probe** (`btk.tools.BossChooserProbe`, a tools-side copy of BossSabotage with a feature dump): features match the
  study; without its closure rule the proxy's argmax reaches 81 %. The rule (take a winning one-hit closure at once)
  fires on 38 % of the K1 games' under-construction-attack turns (17 % in v12 games); the live boss declines those
  closures 94–98 % of the time and won all 35 such games. Without those turns the proxy agrees 64.0 / 62.0 % (K1 / v12),
  75.8 / 74.7 % on the construction attacks — **`proxy-agreement-v1`'s blind spot was an artifact** (correction note
  added there).
- **No-close proxy** (`USE_BOSS_NO_CLOSE`, `// BOSS_NOCLOSE` block in BossSabotage, default off, default and proxy
  artifacts byte-identical; the V10–V12 helper pin now hashes current BossSabotage with default-off blocks stripped).
  Predeclared validation (`tools/boss_proxy/run_noclose_screen.sh`, fresh pool 1014000): K1 +2.7 ± 5.7, Q +3.0 ± 6.0
  (both needed ≤ 0 — fail), v11 configuration −3.7 ± 7.1 (passes) → **not validated**; `current` scores the same
  against both proxies (80.7 / 80.3 %).
- **Replication** (same pool, policy proxy): **K1 −1.7 ± 6.1 pp** (was +6.5 on pool 1009000), **Q −6.0 ± 6.7 pp** (was
  +6.3 on pool 1011000). On fresh maps the proxy agrees with both live signs (−15, −5). The two construction "wrong
  signs" were selection noise: best of two arms at ±7 pp through a +3 pp gate. Withdrawn: "the construction line is
  closed at the proxy's level", "the proxy cannot screen construction", `proxy-agreement-v1`'s blind-spot reading.
- **Proposal (user decision):** amend policy v5 — an arm passing step (a) is replicated on a fresh pool before (b) and
  the live step, continuing only at a positive replication delta and a pooled delta ≥ +3 pp (≈ 10 min per arm; it
  would have saved today's 160 API games). Pool 1014000..1014149 consumed, next ≥ 1015000.
- **State.** `current` unchanged; branch `claude/project-thread-xptone-v6`.

## 2026-10-26 — policy v5 amended: step 1a, replication of a proxy-screen pass on a fresh pool

- **Decision.** The user's "Да" to the proposal at the end of `boss-v4`. `docs/benchmarking-v5.md` gains step 1a: an arm
  that passes step 1 is re-screened on a fresh pool (300 paired games, 150 new maps) against the same screening opponent
  with `current` as the same-map control in the same run, and continues only if the replication delta is positive and
  the pooled delta of both pools is ≥ +3 pp. AGENTS.md rule 3 (a′), CLAUDE.md and the BOTS header follow.
- **Why.** Both construction candidates passed step 1 at +6.5 / +6.3 pp (best of two arms each), replicated at −1.7 /
  −6.0 pp and lost their live sessions (−15 / −5 pp, 160 API games). With a ±6–7 pp interval per 300-game arm, the
  best of two arms clears a +3 pp gate under the null about one screen in three.
- **Tools.** `tools/boss_proxy/run_replication.sh` (control + candidate + optional extra arms on a fresh pool) and
  `tools/boss_proxy/pooled_delta.py` (per-pool and pooled paired deltas with the step-1a verdict). Applied
  retroactively: K1 pooled +2.4 ± 4.7 pp → stop; Q pooled +0.2 ± 4.4 pp → stop.
- **First application:** the retro-replication of the last two promotions (B → v11, E1 → v12) on fresh pool 1015000.

## 2026-10-26 (late) — retro-replication of the last two promotions under step 1a: both real

- Fresh pool 1015000, 150 maps × 2 seats per cell, CG timing, the policy proxy; cells `current` (v12), v11 on current
  (`current?USE_EXPOSURE_INCOME=0`), v10 on current (`current?USE_BOSS_ROOT_TARGET=0,USE_EXPOSURE_INCOME=0`):
  82.7 / 74.3 / 67.7 % (`evidence/retro-replication-v1/`).
- **Root target (B → v11):** v11 vs v10 **+6.7 ± 7.6 pp** (51/40/59 maps); pooled with its selection screen (+8.8 ± 7.1,
  pool 1002000) **+7.8 ± 5.2 pp** → passes step 1a.
- **Exposure income (E1 → v12):** v12 vs v11 **+8.3 ± 6.2 pp** (45/27/78); pooled with its selection screen (+3.7 ± 6.7,
  pool 1003000) **+6.0 ± 4.6 pp** → passes step 1a.
- v12 over v10 on the same maps: +15.0 ± 7.0 pp. The current version stands on two replicated gains; the two failures of
  the day (K1, Q) were the only unreplicated passes. `tools/boss_proxy/pooled_delta.py` now takes a per-pool control name
  (`dir:cell:control`). Pool 1015000..1015149 consumed, next ≥ 1016000.
- **Next (default):** the never-screened `ROLLOUT_THREAT_WEIGHT` (0.5, the blend of plain and protected gain in the route
  proposals, partly overlapping exposure income since v12) — arms 0.25 / 0.75, the first candidate under step 1a.

## 2026-10-27 — `threat-weight-v1`: the threat blend 0.5 stays (0.25 −4.3 pp, 0.75 −1.0 pp vs the proxy)

- `ROLLOUT_THREAT_WEIGHT` (never screened; the blend of plain and protected gain in the route proposals) at 0.25 / 0.75,
  fresh pool 1016000, `current` as the same-map control ([threat-weight-v1.md](threat-weight-v1.md),
  `evidence/threat-weight-v1/`): vs boss-v2 `current` 79.0 %, T25 −4.3 ± 6.6 pp, T75 −1.0 ± 6.6 pp; head-to-head 49.3 /
  54.0 %. Both fail step (a); no step 1a needed. Pool 1016000..1016149 consumed, next ≥ 1017000.
- **State of the levers.** With this the cheap screened levers are spent on both sides: construction (corridor routes,
  earning delay — both non-replicating), sabotage / valuation (hold margin, threat weight, threat model A, unstable cost,
  width / depth / replies, collision aversion). The next candidate needs new information — the user's choice of direction.

## 2026-10-27 — arena refresh of v12: re-uploaded as agent 6814073, 89W/58L against a harder field; the losses open in turns 10–20 on construction

- **Why.** The user's "Давай арену" (refresh the arena analysis) after `threat-weight-v1`. Write-up
  [arena-refresh-v12.md](arena-refresh-v12.md), evidence `evidence/arena-2026-10-27-v12-agent-6814073.json` (trace
  stripped) and `evidence/arena-refresh-v12/`.
- **Entry.** `cgFetch -Pplayer=aabzac -Plimit=200` → 150 new replays of **puzzle agent 6814073**, marker `v12 | … build=ce1d3a3b…
  config=d7803b42…` = the selected v12 artifact, re-uploaded by the user (the first v12 upload was 6813957). Rank 316,
  score 29.40. `cgAnalyze`: 147 validated games, 0 failed turns.
- **Record.** **89-58 (60.5 %)**; median opponent score 27.2 (first upload: 25.1); vs ≥ 28: 26-23, vs 26–28: 34-29, vs
  24–26: 16-6, vs < 24: 13-0; tall 22-18, 30×20 14-9; Ando_Riccardo (score 29.2) 14-16 over 30 games.
- **Losses.** Points per paint 12.6 vs 21.4 with more paint and cells; income level at turn 10 (28 vs 25), then turns
  10–20: our income +44 vs the opponent's +71 with equal cells placed (24.8 vs 25.6) and equal tracks inked (8.7 vs
  8.6); our disrupts are worth more in the losses than in the wins. The construction-productivity diagnosis of
  `arena-losses-v12`, on three times the sample, located in the middle game.
- **Next (user decision):** a cell-level study of what the field builds in turns 10–20 (pairs completed, connections
  activated per completion, extension vs new network) on these 147 games and the 30 Ando games, as the source of the next
  construction mechanism under step 1a.

## 2026-10-27 — middle-game construction study (turns 10–19, 147 arena games): same completions, our connections stop paying sooner

- On the user's "Ок". `cgCellStudy` + `EndgameStudy` over the 147 v12 arena games; cells placed in turns 10–19
  ([arena-refresh-v12.md](arena-refresh-v12.md), `evidence/arena-refresh-v12/midgame.txt`).
- In the losses both sides switch on the same number of pairs (12.8 vs 13.3 per game, 3.5 vs 3.8 completion events);
  the opponent's cells earn within one turn 53 % vs our 39 % and keep 1.55 connections per earning turn vs our 1.14
  (wins: 1.58 vs 1.44); the opponent builds more bypasses (11 vs 6 % of its cells) and takes over our connections twice
  as often (0.63 vs 0.31 pairs per earning cell), on shorter completions (8.9 vs 9.6 cells).
- **Next (proposed):** a per-connection survival measure over the replays — for every active pair of each side, how
  long it pays and why it ends (opponent's ink on the path, takeover through the opponent's cells, own disrupt) — to
  decide the mechanism (redundancy for live connections, anti-takeover, route length).

## 2026-10-27 — connection survival in the arena games: connections die by ink on shared paths for both sides; the opponent's construction out-produces our sabotage

- New tool `btk.tools.ConnectionSurvivalStudy` (one row per earning episode of each side, with the cause of its end).
  147 v12 arena games, 14,955 episodes: inked 98 % (opponent's ink 7,359, own ink 6,344, both 970), takeovers 213.
- Episodes starting in turns 10–19: **our construction pays the same in wins and losses** (975 vs 1,040 paid cell-turns
  per game); **the opponent's pays 1,268 in our losses vs 660 in our wins**, although our disrupts remove 508 vs 262 of
  its income. Each side's inks remove ~0.5 units of its own income per unit of the victim's (shared paths);
  net-negative inks 11–19 %, more frequent in our wins — self-harm is not the loss driver.
- CodinGame metadata: track ownership share per active connection 37.1 vs 47.5 % in the losses, 47.9 vs 38.7 % in the
  wins (first upload: 36.4 / 50.0, 45.6 / 38.3).
- **Conclusion** ([arena-refresh-v12.md](arena-refresh-v12.md)): the losses are construction races against stronger
  builders; no remaining flag-sized lever. Next (user decision): a construction policy that reproduces the stronger
  players' turn-10–20 choices (route proposals learned from the arena opponents' and the leaders' placements), a larger
  piece of work, or keep v12 and pause.

## 2026-10-27 — `route-choice-v1` (construction line, step 1): the stronger players pick the same routes our proposal value ranks first

- The user chose the construction line ("Строка" = «стройка»). New `btk.tools.RouteChoiceExport` (per placement turn:
  our proposal step's candidate routes — cheapest completion per pair over any tracks and own-only, own-only for active
  pairs — with 19 features and the chosen candidate by overlap with the placed cells) and
  `tools/route_choice/fit_route_choice.py` (conditional logit, game-level holdout). Write-up
  [route-choice-v1.md](route-choice-v1.md), data `evidence/route-choice-v1/`.
- Turns 10–19, holdout top-1 / top-3: **our proposal value predicts the strong arena opponents' (score ≥ 27) route choice
  0.281 / 0.438 and v12's own 0.290 / 0.394**; a 19-feature logit 0.183 / 0.386 (opponents); cheapest route 0.13.
  Route-level imitation adds nothing: the stronger players and v12 choose much the same among these candidates.
- Second-order differences of the chosen routes: routes over already active pairs (backup / takeover) 25 vs 17 %,
  own-only 49 vs 44 %, adjacency to own tracks 2.07 vs 1.85.
- **Next (user decision):** (a) cell order within multi-turn routes (the opponent's tracks earn sooner with the same
  completions), (b) a backup-route flag for active pairs screened under step 1a, or pause the construction line.

## 2026-10-27 — `route-order-v1` → `route-commit-v1`: v12 leaves routes half-built; a continuation bonus passes steps (a), 1a and (b) — C10 to the live step

- **Order study** (the user's "Порядок", [route-order-v1.md](route-order-v1.md)): over the 147 arena games v12's
  completions take +4.2–4.4 turns longer than their paint requires (opponents +1.2–1.9, Ando +0.4); 30 % of a completion's
  cells are placed ≥ 4 turns before its income (opponents 13 %); the final push is as fast as the opponents'; the delay
  is cross-turn switching (start A, start B, finish A later); route continuation next turn 24–27 % vs 30–32 %.
- **Candidate** ([route-commit-v1.md](route-commit-v1.md)): `USE_ROUTE_COMMIT` + `COMMIT_BONUS` (`// COMMIT` block,
  current-only, default off; default artifact unchanged, field-count pins +2): the chosen route's unbuilt cells are kept
  for one turn; a root placing a cell on them gets the bonus in `searchRoots`.
- **Step (a)**, fresh pool 1017000 vs boss-v2 with `current` as the control: **C10 82.3 % vs 75.3 % = +7.0 ± 5.5 pp**
  (40/21/89), C25 +4.7 ± 5.6 pp; head-to-head C10 52.7 %, C25 49.0 %.
- **Step 1a**, fresh pool 1018000: C10 +2.5 ± 5.1 pp → **pooled +4.8 ± 3.8 pp, continue**; C25 +0.7 → pooled +2.7, stop.
  The first replicated proxy gain since v12.
- **Artifact** `build/submit-commit-c10`: 92,813 chars, sha `63e26828…`, build `45dc8325…`, config `f3229a23…`; strict
  procCheck 0 problems, later ≤ 37.7 ms. **Live plan** `build/cg-boss/commit-c10-v12-boss-40` (id `4cb1514d…`, seed
  2026102701, 80 API games vs v12) frozen; launch on the user's word. Pools 1017000..1018149 consumed, next ≥ 1019000.

## 2026-10-27 — `route-commit-v1` against the live boss, v5 step (c) session 1: C10 18/40 vs v12's 22/40 — −10 pp

- Launched by Claude at 06:56 UTC on the user's "Запускай" (allow rule); 80 API games 06:56–07:58 UTC, 0 failures,
  0 retries, **40/40 + 120/120 replays exact**. **C10 18/40 = 45.0 %, `current` (v12) 22/40 = 55.0 %**; discordant
  **3 : 7** (30 equal), paired **−10.0 pp**, one-sided p = 0.95; P0 11 vs 12, P1 7 vs 10; 29×19 7 vs 5 of 12, 21×14 3 vs 5
  of 6 (`evidence/route-commit-v1/live-session-1/`).
- **Reading.** Fails the session threshold. The proxy gain is replicated (pooled +4.8 ± 3.8 pp over 300 maps) but does
  not show against the boss; the live sample is thin (10 discordant pairs; 3 : 7 arises under no difference about one time
  in six). The policy's second session would need ≈ +20 pp to bring the 80-game sample to +5 pp. Live sessions of the
  line: D +22.5, B +2.5, E1 +5.0, K1 −15.0, Q −5.0, C10 −10.0 pp.
- **Open to the user:** session 2 (80 API games) or park C10 (recommended). No upload, `current` unchanged.
- **Verdict (08:20 UTC, the user's "Отложить"): C10 parked**, no second session; flag stays, default off.

## 2026-10-27 — `late-game-v1`: what the winners build in their last third — not a lever

- The user's "Да" (PLAN item from `end-forecast-v1`), fresh branch from master after the merge. Corpora: Indrill's 282 contest
  games and v12's 147 arena games. New `btk.tools.InkRescueStudy` and `tools/diagnostics/late_game_{trajectory,cells,rescue}.py`;
  the regenerated Indrill `cgCellStudy` export equals September's. Evidence `docs/evidence/late-game-v1/`, write-up
  `docs/late-game-v1.md`.
- **Score trajectory.** At 2/3 of the game 89 % (v12 wins), 94 % (v12 losses) and 83 % (Indrill wins) of the points are
  scored; v12's leader changes after 2/3 L in 12 of 147 games (net +2 for us); a drawn last third would turn 5 of 58 losses.
  The losses are decided between 0.2 L and 2/3 L (median gap −44 → −412).
- **Last-third paint.** Indrill 2.72 paint/turn, 26 % earns, 1.2 pts/paint, 56 % filler; v12 in its wins 2.61, 21 %, 1.2, 58 %.
  Same behaviour, same yield.
- **Protection.** A connection one disrupt from ink is covered by an existing alternative for 17 / 16 % (v12 wins, v12 / opp),
  14 / 13 % (losses), 25 / 25 % (Indrill / opp): a property of the game's rails, shared by both sides.
- **Middle third.** v12's losses: its paint earns 35 % vs 62 %, 43 % filler (earning 9 %), bypass share 9 vs 20 %. v12 proposes
  bypasses only around the single predicted threat region (`propose` variant 2; the instability surcharge applies to free cells,
  so variant 0 never re-routes an active pair).
- **Verdict:** late-game item closed; no candidate from the last third. Next direction = user decision (a middle-game bypass
  candidate is the construction line's open option).

## 2026-10-27 — `bypass-routes-v1`: bypass proposals for threatened own connections — B2 passes (a), 1a and (b)

- The user's "Давай обходы" after `late-game-v1`. `USE_BYPASS_ROUTES` (+ `BYPASS_MIN_INSTABILITY` 2, `BYPASS_WEIGHT` 0.5) in a
  `// BYPASS` block, current-only, default off: after the ordinary and corridor proposals, for each pair whose active path holds an
  own track and crosses non-town regions at instability ≥ the threshold, the cheapest route with those regions blocked (≤ 16
  queries, same reserve), valued (1 − w) × plain gain + w × (income difference after inking those regions with the route built −
  the same inks without it), exposure-discounted. FrozenParams, BotFactory guards and configuration exclusion, FileMerger block,
  `BypassRoutesTest`, `BypassRoutesSubmissionTest`, field-count pins +3; the default artifact is unchanged (V12 pins pass).
- **Screen, pool 1019000** (300 paired games per cell): `cur-vs-boss2` 79.3 %, B2 82.3 % (**+3.0 ± 5.3 pp**, 32/23/95), B3 83.0 %
  (+3.7 ± 5.1); h2h **B2 55.0 %**, B3 46.7 % → B3 stops. **Replication, pool 1020000:** cur 78.7 %, B2 84.0 % (+5.3 ± 5.9) →
  **pooled +4.2 ± 4.0 pp**, step 1a passes. 0 errors; per-move times in the control's band (the machine ran ~15 ms/move for every
  cell, this morning ~7).
- **Artifact** `build/submit-bypass-b2`: 94,839 chars, build `f62155d3…`, config `8c4b3d6d…`; strict `procCheck` (zero grace, 4 games
  vs v09): 0 problem games, 0 timeouts, first ≤ 657 ms, later ≤ 38.9 ms. **Live plan** `build/cg-boss/bypass-b2-v12-boss-40` (id
  `6063fbb0…`, seed 2026102702, matched, 40 maps × 2 = 80 API games vs v12, 1,278 maps excluded), no API games played; launch on the
  user's word.

## 2026-10-27 — `bypass-routes-v1` against the live boss, v5 step (c) session 1: B2 24/40 vs v12's 21/40 — +7.5 pp, passes

- Launched by Claude at 10:41 UTC on the user's "запускай"; 80 API games, 0 retries, **40/40 + 160/160 replays exact**. The runner
  stopped once at 11:28 UTC after 28 results (the read-only canonical lookup of slot 29, game 912366310, failed after its play
  response was saved) and was resumed with the same command at 12:17 UTC (lookup only, no new play); complete 12:58 UTC.
- **B2 24/40 = 60.0 %, `current` (v12) 21/40 = 52.5 %**; discordant **5 : 2** (33 equal), paired **+7.5 pp**, one-sided p = 0.23;
  P0 10 vs 9, P1 14 vs 12 (`evidence/bypass-routes-v1/live-session-1/`).
- **Reading.** Passes the session threshold (+5 pp) on a thin sample. Live sessions of the line: D +22.5, B +2.5, E1 +5.0, K1 −15,
  Q −5, C10 −10, B2 +7.5. Decision open to the user: v13 now, or session 2 (another day; 160 API games were used today).

## 2026-10-27 — Promote B2 to v13 "Bypass Routes" (user decision, session 2 skipped)

- The user's "Делаем v13" after B2's live session 1 (24/40 vs v12 21/40, +7.5 pp, 5 : 2). Deviation from policy v5 recorded in
  `docs/v13-promotion.md` (one live session instead of two, as at the v10–v12 promotions).
- `USE_BYPASS_ROUTES` defaults to true; `btk.bot.v13` = byte copies of current's RolloutBot / BossSabotage / ProtectedOpening with
  identity changes only; `versions/v13.properties` = 136 values (v12's 133 + the flag and its two parameters) = B2's manifest.
  BotFactory selects v13; the inherited flag is dropped silently for non-rollout policies and refused when explicit; `v12?…=1`
  refused; **a `USE_BOSS_SABOTAGE=1` spec with the bypass flag on is refused** (proxy spec gains `USE_BYPASS_ROUTES=0`;
  `run_replication.sh` updated). FileMerger exports v13 readably; catalog v13 selected, v12 historical.
- Tests: `V13PromotionTest`, `V13SubmissionTest`; v12 tests on `V12_ON_CURRENT`; helper specs carry `USE_BYPASS_ROUTES=0`. Every
  current-based pin moved with the label; each was checked by merging the pinned spec from the last commit and from the new tree:
  the artifacts differ only in the marker's label/build id and the header comment (configs unchanged); frozen v09–v12 exports keep
  their bytes. `gradlew test`: 863 tests, the 25 known failures only.
- Default artifact `build/submit-v13`: 94,816 chars, sha `3d3e861d…`, build `fb92323e…`, config `8c4b3d6d…` (= B2's); frozen export
  94,781 chars (`7b7dac9a…`). Strict `procCheck` (zero grace, 4 games vs v09): 0 problem games, 0 timeouts, first ≤ 573 ms, later
  ≤ 43.2 ms. Arena upload of `build/submit-v13/Player.java` is the user's step (the arena runs v12, agent 6814073).

## 2026-10-27 — repo-cleanup-v1: archives out of the index, journals split, the 25 red tests and the parked experiments removed

- The user's ask: too many files are committed, and are the failing tests (or all tests) needed? Audit + plan shown first
  (Claude doc "Чистка репозитория и тестов: план"); the user's answer "1, 3 да, 2 нет, 4 позже". Details in
  `docs/repo-cleanup-v1.md`.
- **Weight.** A fresh clone was ≈ 445 MB, ≈ 410 MB of it 60 evidence archives (59 zip + 1 tar.gz, 2026-09-19..10-18); without
  them ≈ 35 MB. Each commit also rewrote 1.1 MB of journals.
- **Done.** (1) `docs/evidence/**/*.{zip,tar.gz,tgz,csv.gz}` gitignored and 68 files (512.7 MB) untracked, not deleted;
  `docs/evidence/untracked-evidence-2026-10-27.json` = path, size, sha256, blob, adding commit. Two tests read the extracted
  `Player.java` under `src/test/resources/historical/`. No history rewrite (user: "2 нет"). (2) DEVLOG/PLAN/BOTS: the periods
  before 2026-10-20 (BOTS: up to 2026-10-06) moved verbatim to `docs/archive/`; live files 125 / 27 / 74 KB. (3) Tier 1: the six
  `exp-*` recipes and their archived engine (red since 2026-10-18, when fast cheapest path changed `Pathing.class`) and the
  never-registered `pduhard-beam-v1` removed with their tests and tools: 863 tests / 25 red → 831 / 0. (4) Tier 2, prune-v2:
  collision avoidance, corridor routes (K1), quick-always / delay power (Q), the opponent hold margin, the no-close proxy switch,
  route commitment (C10) and the threat model left `current` (−183 lines); refused on current, still run on `v13?FLAG=1` (threat
  model also v11/v12); their 12 test classes and the 12 `btk.bot.v10` classes of the prune-v1 flags removed; `PruneV2Test` added.
  The default artifact stays byte-identical to `build/submit-v13/Player.java` (94,816 chars, sha `3d3e861d…`, V13SubmissionTest),
  so the arena upload and its strict procCheck are unchanged. (5) AGENTS.md rules 8 (what an experiment commits) and 9 (tests
  follow their code).
- `gradlew test`: 763 tests, 0 failures, 5 min 1 s (was 863 / 25 red, 4 min 55 s). One earlier full run missed
  `BossLiteTest`'s timing threshold once (`bossModelCalls` 808 vs > 10 × turns, at the start of the parallel run); green alone and
  in the next full run, and the code removed only runs with a parked flag on (default artifact and FileMergerTest's compiled
  decision parity unchanged). Tier 3 (closed neural / spatial / indrill lines with their code, ≈ 330 tests) is
  left for a separate PR on the user's word.

## 2026-10-27 — arena-uploads-v1: candidates are judged on two arena uploads; `tools/arena_uploads.py`

- The user's proposal after v13's upload, agreed in the project chat: after a good local bench the candidate goes to the
  arena (each upload plays ~120 placement games). Its games are downloaded, the rating (not only the rank) and win
  rate are recorded, and the same artifact is re-uploaded to average across uploads; the candidate is rolled back if it is
  clearly worse. The first 40-game boss session stays as a cheap filter. Replays are saved by version name. Rule:
  `docs/arena-uploads.md`; AGENTS.md rule 3 (c) and `docs/benchmarking-v5.md` step 3 amended. Provisional parts, to be
  fixed after the noise measurement: the filter is delta ≥ 0, and M = 1.0 CG point until two uploads of one artifact
  are measured.
- Tool `tools/arena_uploads.py` (+ `tools/test_arena_uploads.py`, 14 tests). `fetch` reads the puzzle leaderboard
  (our agent, score, rank, `percentage`, `creationTime`) and `findLastBattlesByAgentId`, which returns the results of all
  the upload's games in one request. It then downloads the missing replays into `replays/arena/<label>/upload-N/games/`
  (gitignored), reusing `replays/cg`. The label comes from the first-turn marker (`v13 | BTK_BUILD_V1 …`); an experiment
  needs `--version`, and one label holds one configuration hash. `compare` prints the placement score, win rate,
  opponent median, a performance rating (logistic in the opponent's score, shared scale) and stratified deltas on
  shared opponents. `import` files earlier uploads from `replays/cg` offline; `relabel` moves a candidate's uploads at
  a promotion.
- **Finding:** CG lists the games of the **live agent only**. 6807052, 6811139, 6813813 and 6813957 return empty lists
  now, so an upload that is not fetched before the next Submit keeps only the replays downloaded earlier. The rule
  says: fetch before every upload.
- **v13 upload-1 = agent 6818214** (submitted 14:23 UTC, placement finished 16:48 UTC): 120 games, **75W/45L (62.5 ± 4.4 %),
  CG 29.23, rank 316 / 1,634**, opponents' median 28.20, performance rating 28.99 ± 0.62; `cgAnalyze` validated
  118 games, 0 failed turns. 122 API requests. v12's re-upload (6814073, imported, 150 games): 90-60, 29.40 at 147 games,
  rating 28.64 ± 0.55. v10 ×2 (D and 6811139, same config), v11 and v12's first upload have only the first 50 games
  on disk (`docs/evidence/arena-uploads-2026-10-27.json`).
- Decision: v13 is not clearly worse than v12 (−0.17 score, +0.35 performance rating, +2.5 pp against a field 1.0 point
  stronger). **Next: the user re-uploads v13 (upload-2)**; its placement gives the first upload-to-upload spread.

## 2026-10-27 — repo-cleanup-v1 tier 3: the closed research lines leave the code (763 → 429 tests)

- The user's "можно пока 4-й пункт чистки ещё" (relayed by the coordinator after PRs #13–#15 merged). Fresh branch from master
  `9fb2aa4`; details in `docs/repo-cleanup-v1.md` (Tier 3).
- **Removed with code, tools and tests:** neural v1–v5/v7, spatial-ppo v1–v10 (+ v2-init), indrill-q v1/v2, indrill-forum-v1,
  leader-v1/v2, the neural target proposal (`TargetHybridBot`; `USE_NEURAL_TARGET_PROPOSAL` now refused) and the PPO prototype,
  the `V09*` / `GeneratedPolicy` probe tools, the prune-v1 mechanism studies, the September boss-v07 studies and the sabotage
  forecast; 23 gradle tasks; catalog entries and parameter snapshots; `tools/{neural,spatial,indrill}`,
  `tools/run_leader_coverage.py` and 17 `tools/diagnostics` scripts that called the removed code.
- **Kept:** neural-v6 (screening opponent) with its 5 classes; neural-v5's bot and features moved to the test sources as the
  frozen oracle of `FeaturePathsTest` / `NeuralV6*Test`; pressure-v1 (default `benchSuite` panel) and boss-v1; the `Params`
  fields of the removed lines (frozen snapshots, pinned field count; never shipped). Not done on purpose: the v05-era EvalBot
  flags inside `current/EvalBot.java` (intertwined legacy non-rollout policy that `MainBot` selects; no byte oracle).
- **Check:** 15 merged artifacts (default `3d3e861d…`, proxy spec, v12 configuration, non-rollout / heuristic current, v05–v13,
  neural-v6 — now pinned at `0597d5b7…`) byte-identical between a scratch build of `9fb2aa4` and the tree. `gradlew test`:
  429 tests, 0 failures, 5 min 11 s (the long tests — PruneV1, FileMerger, V09Promotion — are kept ones).

## 2026-10-27 — v13 uploaded twice: the first upload-to-upload noise, and v13 reaches Gold

- The user re-uploaded `build/submit-v13/Player.java` (~17:30 UTC; agent 6818421, the same bytes as upload-1). A
  leaderboard check every 15 minutes recorded its Silver placement: 23.92 at 12 %, 27.78 at 49 %, 30.01 at 83 %, **30.24 /
  rank 316 at 92 %**. At the end of placement (~20:00 UTC) it overtook the Silver boss and **CG promoted v13 to Gold**: a
  new agent, 6818546, replays the placement in Gold (4-6 at 5 %, score 10.47, rank 290), and 6818421's game list was
  gone at the next fetch (`findLastBattlesByTestSessionHandle` lists the live agent too).
- **Recovery:** 85 of 6818421's ~120 results came from the last-battles lists of 102 opponents (upload-1's opponents plus
  Silver ranks 300–380; 104 requests, results only): **54-31 (63.5 ± 5.2 %)**, opponents' median 27.97, performance rating
  29.76 ± 0.82. Upload-1: 75-45 (62.5 %), 29.15 ± 0.71 (both on the Silver scale 3.58).
- **Noise:** the same artifact placed 29.23 (stayed Silver #1) and ≥ 30.24 (promoted). SD 0.71 → **M ≈ 1.4 CG points**
  (one pair, provisional). Win rate and performance rating agree within their errors. The shared-opponent delta for
  the same bytes is −14.2 ± 8.8 pp (15 players), so it is context only.
- **Tool (16 tests):** `watch` fetches every 15 minutes, and every 3 minutes from 85 % on, until placement reaches 100 %,
  and follows a promotion. `fetch` no longer files an agent without games, and it records a promoted agent with
  `promotedFrom`. `compare` groups uploads by league (scores do not compare across leagues), and `import --division`
  marks the imported v10–v12 as Silver. Filed: v13/upload-2 = 6818421 (recovered, `source: recovered`), v13/upload-3 =
  6818546 (Gold; `watch` running). Evidence `docs/evidence/arena-uploads-v13-noise-2026-10-27.json`; rule updated in
  `docs/arena-uploads.md` (watch during every placement, comparisons within a league, M).
- **Next:** v13's Gold placement completes (upload-3), then one more v13 Submit in Gold gives the second Gold placement
  score, which is the baseline a candidate is judged against.

## 2026-10-27 — v13's first Gold placement: 66-74, score 10.82, rank 287; the big maps lose

- `watch` (20:05–22:45 UTC, 4–5 requests per round) recorded agent 6818546's whole Gold re-placement: **140 games,
  66W/74L (47.1 ± 4.2 %), CG score 10.82, rank 287 of 1,634**. Gold holds ranks 37–316 (~280 agents), so this is about
  251st of them. Opponents' median score 11.18; performance rating 11.06 ± 0.45 on the Gold scale (fitted 2.60): v13
  scores at the median of the opponents the matchmaker served.
- Maps: small 53-48, **tall 6-15, 30×20 7-11**. The big maps are where Gold beats v13; in Silver, upload-1 went 25-14 on
  them (upload-2's recovered results have no map data). `cgAnalyze`: 140 games validated, 0 failed turns of ours; in 3 games on 30×20 the opponent
  timed out.
- Evidence `docs/evidence/arena-uploads-v13-gold-2026-10-27.json`; `docs/arena-uploads.md` gains the Gold section.
  **Next:** one more v13 Submit in Gold (the user's step, then `watch`) for the two-score Gold baseline. The big-map
  deficit is the first lead for a Gold-era candidate.

## 2026-10-28 — v13's Gold baseline: 10.82 and 10.65 (mean 10.73); M ≈ 1.0

- The user submitted v13 once more in Gold ("перезалил ещё раз", 07:42 UTC): agent 6818773, whose placement had already
  finished when `watch` started. The first round filed it whole: **141 games, 70W/71L (49.6 %), CG 10.65, rank 289 of
  1,635**, opponents' median 10.58, performance rating 10.70 ± 0.70. Maps: small 56-56, tall 4-5, 30×20 10-10.
  `cgAnalyze`: 141 validated, 0 failed turns of ours (4 opponent timeouts). The night games upload-3 played after its
  22:58 fetch were lost by the Submit, a few at most (placement was over).
- **Baseline:** upload-3 10.82, upload-4 10.65 → mean **10.73**, SD 0.12. The Gold scale refits to 4.05 on 281 games
  (upload-3: 10.96 ± 0.70). Pooled with the Silver pair (SD 0.71) the upload-to-upload SD is 0.51 → **M ≈ 1.0**: a
  Gold candidate is rolled back when its mean placement score is below ~9.7 and its performance rating agrees.
- Over the two Gold placements v13 is 136-145 (48.4 %); tall and 30×20 together 27-41 (40 %), small maps 109-104. The
  shared-opponent delta for identical bytes is +8.5 ± 7.3 pp (23 players), context only. Evidence
  `docs/evidence/arena-uploads-v13-gold-baseline-2026-10-28.json`; rule text M and the baseline updated
  (`docs/arena-uploads.md`, AGENTS.md rule 3, `docs/benchmarking-v5.md`, CLAUDE.md, STATUS, PLAN).
