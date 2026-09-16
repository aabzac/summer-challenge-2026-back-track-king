# Plan

Roadmap with status. History and reasoning live in `DEVLOG.md`, per-version numbers in `BOTS.md`.
Update the checkboxes when a step is finished; add new steps at the bottom of the open phase.

## Phase 1 — infrastructure (done 2026-09-13)

- [x] Gradle/Java 17 skeleton, package layout, `AGENTS.md`, Cursor rules
- [x] `btk.core` rules engine shared by bot, referee and tools; JUnit tests
- [x] CG replay download (`cgFetch`) and engine validation against them (`cgValidate`) — 108/108
- [x] Full rules inferred from Silver replays (ink at 4 disrupts, game end) — `docs/rules/inferred_silver.md`
- [x] Referee, procedural + real maps, parallel benchmark with Wilson CI, side swap
- [x] Process-level submission check (`procCheck`): protocol, first-turn warm-up, 50 ms turns
- [x] Single-file merger (`submit`) → `build/submit/Player.java`
- [x] Enforce the 100,000-character submission limit; explicitly restore frozen v05 with
      `submit -PsubmitVersion=v05`, recording resolved configuration and source hash.
- [x] Replay visualizer for local and CG-derived replays
- [x] Study tool for what top players do (`cgStudy`)
- [x] Bots v00 (autoplace), v01 (greedy), v02 (eval + DISRUPT), v03 (vulnerability eval, backups)

## Phase 2 — leagues (open)

- [x] Paste `Player.java` (v03) into the CG IDE and advance — Wood 2 → Wood 1 → Bronze → Silver;
      initially around rank 350/645, with Gold not yet open at that historical checkpoint
- [x] Record user snapshot, 2026-09-15: Gold opened today; our entry remains in Silver,
      rank 235/622. User-reported, not an API verification or proof of warmup/strategy improvement.
- [x] Capture the Bronze statement verbatim into `docs/rules/bronze.md`; reconcile with
      `inferred_silver.md` — all inferred rules confirmed; Bronze is the final rule set
- [x] Referee source found (linked from the statement) and cloned to `tmp/referee-src`
      (gitignored): impossible actions are skipped, not fatal → `Rules.expand` aligned + tests
- [x] Port `GridMaker` 1:1 (CG supplies `SecureRandom` SHA1PRNG through `java.util.Random`) →
      authentic maps by `refereeInput` seed; `cgMapValidate` 107/107, seven checked-in map fixtures
      covering all heights. Seed 0 is non-reproducible in CG and rejected; see `map_generator.md`.
- [ ] Re-fetch replays of the current top 20 every few days (`cgFetch`), keep `cgValidate` green
- [x] Capture boss `TestSession/play`: it returns a replay document and public `gameId`;
      userscript stores the canonical `gameResult` under `replays/cg/bot/` automatically.
- [x] Separate CG validation from visualizer export: `cgValidate` is read-only; `cgRender` writes
      validated files under `replays/rendered/cg/{arena,bot}/`.
- [x] Recognize positive boss agent ids with `agents[].arenaboss` in the general CgReplay parser
      and renderer routing, including nickname and authoritative player index. Both slots tested.
      All 30 boss games rendered under `replays/rendered/cg/bot/`; collection `boss-30.html`
      separates corrected 16 v05 / 14 v06 groups. Browser checked at initial and terminal turns.

## Phase 3 — strength (open; one feature per version, bench-gated)

Gaps measured in `BOTS.md` → "What top Silver players do". Candidate order:

- [x] Add an independent sabotage-first reference and matched opponent panel: pressure-v1,
      v05, v03, aggressive v02. Pressure baseline: 99.9% disruption, 61.3-turn games; v05 wins
      69.0% over 300 paired development games. Local behavioral coverage, not proven top-player imitation.
- [ ] Calibrate/extend reference styles against fresh arena behavior; pressure-v1 builds on only
      56.7% of turns, so it does not cover every strong opponent. Preserve recorded reference ids.
- [x] No idle turns: v04 spends otherwise idle paint extending its network through stable nearby
      cells, preferring town regions — 64.7% vs v03 over 300 paired games
- [ ] Disrupt every turn: pre-emptive targets on the opponent's likely corridors; closing the map
      when ahead / keeping it open when behind
- [ ] Short lookahead: 2-3 turn plan search over (placements, disrupt) with the engine
- [ ] Parameter tuning only after the above, with >= 1000-game benches
- [ ] Check timing after every hot-path change (`procCheck`), keep first turn < 800 ms
- [x] Harden comparisons: paired maps with fresh bots, fixed parameters for historical versions,
      full JSON reports, confidence grouped by distinct map, strict replay validation/input checks
      (see `benchmarking.md`).
- [x] Fix CG replay player mapping: `lastBattles.players[].position` is finishing order, while
      `gameResult.agents[].index` is the authoritative in-game P0/P1 position.
- [x] Reproduce timeout-ending CG games: ignore retained late stdout, resolve the opponent's final
      actions/scoring, and apply the timed-out player's terminal `-1` score.
- [x] Align local search budgets with CG's margin/warm-up allowance; explicit legacy control,
      timing profile recorded in bench JSON. Actual process IO remains a separate check.
- [x] Analyze 50 fresh games of arena agent 6735801 after exact validation: 23 wins / 27 losses;
      large-map collapse and disruption starvation identified (see `arena-analysis-2026-09-14.md`).
- [x] Reserve time for sabotage on large maps: v05 cuts build search short by 6 ms, verified
      on captured failures, 1000 paired games vs v04 and common-opponent v03/aggressive-v02
      controls. Atomic evaluations can still overrun the cutoff; this is not a hard guarantee.
- [x] Test a 6-ms build-phase reserve on captured failures and 300 paired CG-map games:
      recovered sabotage, but 54.3% vs v04 did not pass the promotion gate; kept default-off.
      Regression prefixes and deterministic scheduling tests are checked in. Next alternative:
      compute a safe sabotage fallback before route search without permanently reserving 6 ms.
- [x] Matched older-opponent follow-up on new seeds 5000..5149: reserve=6 scores 69.3% vs v03,
      v04 scores 61.3%; 33 added / 9 lost wins in 300 matched games. This justified keeping
      the candidate for the independent confirmation completed below.
- [x] Independent v05 confirmation: 57.7% vs v04 over 1000 games (seeds 6000..6499), CI
      51.6-63.8%; aggressive-v02 control 70.0% vs v04's 67.7%. Freeze v05 and enable reserve.
      72 tests, 158 exact replays, 0/8 process problem games; submission generated.
- [x] User uploaded v05; record agent 6736850 and analyze first 56 fresh games: 38-18,
      no own timeouts, 214/214 corpus games exact. Placement is still ongoing.
- [ ] After v05 placement completes, compare a later sample; do not infer strength from rank alone.
- [x] Fetch and analyze a later separate arena entry, 6739024: 50 new games, 25-25,
      no own failures; 264/264 corpus replays exact. User confirms previous runtime is deployed.
      Placement completion and exact deployment hash remain unknown; see arena-analysis-2026-09-15.md.
- [x] Diagnose sabotage target valuation on captured losses (Meruem / OldJohn): full evaluator
      reproduces Meruem target preferences; local timed scans complete on all four positions.
      OldJohn missed outputs are not reproduced locally, so original cause remains unknown.
      Known-state vs oracle deltas differ; see sabotage-valuation.md. No strategy change.
- [x] Screen delayed-sabotage discount (factor 0.65 per additional hit): 49.3% vs v05 on
      300 games / 150 fresh paired maps, CI 38.2-60.4%; not promoted. Aggressive-v02 control:
      candidate 73.3% vs v05's 76.5% on 200 matched games each. Keep factor 1.0 (neutral).
      116 tests, 264 exact replays, candidate/restored-v05 0/4 process failures each.
- [x] Fix submission warm-up scheduling: MainBot uses a disposable same-strategy instance,
      copied states and 36-ms slices within the existing 500-ms allowance. Route search now
      runs without consuming live RNG. Candidate 0/8 strict process failures, first max 577.89 ms
      versus control 696.84 ms; runtime default enabled, no policy/version promotion. See DEVLOG.
- [ ] Check actual partial placements against score-difference regressions, not only the value
      of a fully completed candidate route. Do not attribute every bad build to idle extension.
- [x] Screen a known-state income veto: fixes captured harmful builds but only 48.1% vs v05
      on all 214 paired CG maps. Keep `GUARD_BUILD_INCOME` default-off; no v06.
- [x] Screen alternative placement order within a valuable route: 52.3% vs v05 on 428 games /
      214 CG maps, CI 43.1-61.6%; not promoted. `CHOOSE_BUILD_DIRECTION` stays default-off.
      Delays two captured harmful shortcuts but does not solve simultaneous-opponent prediction.
- [x] Screen an early ready-to-ink sabotage fallback against v05 on all 214 paired CG maps:
      49.8% over 428 games, CI 40.5-59.1%; not promoted, flag remains default-off.
- [x] Screen top-4 build-prefix reranking against top-2 likely simultaneous opponent replies:
      46.5% over 428 games / 214 CG maps, CI 37.2-55.8%; slower and not promoted.
      Fresh-map diagnostics: K=4/R=1 scored 49.3%; K=2/R=1 scored 55.7%, but its
      map-grouped CI was 44.6-66.8%. Keep `USE_REPLY_RERANK` default-off; no confirmation.
- [x] Add schema-3 height-stratified reports, accepted-action metrics, named development and
      reserved confirmation pools; reject overlap with supplied prior report hashes before games.
      Sequential benchSuite preserves separate opponent reports. Confirmation-v1 remains unplayed.
- [x] Offline adaptive sabotage forecast on 56 v05 arena games, split by opponent agent:
      holdout top-1 69.9%, but log-loss improvement versus fixed mixture has a CI crossing zero.
      Diagnostic gate not passed; keep tools-only, no strategy integration or v06.
      See `sabotage_forecast.md` for the fixed protocol and observation boundary.
- [x] Fix process deadline measurement: single allowance including input sending, timestamped
      reader arrivals separate from consumer wake-up, zero default grace, terminal timeout/EOF,
      per-turn JSON. Eight new tests and a fixed eight-game strict run; see DEVLOG.
- [x] Investigate v05 large-map timing with phase-level evidence before changing strategy:
      strict run caught P0 turn 53 on 903509641 at 50.7868 ms (consumer 56.2206 ms);
      the other seven games passed. One first response was 959.35 ms, within 1,000 ms but
      above our 800-ms headroom target. `cgTiming` on 400 recorded-state calls attributes 53.9%
      of route-search time to nested vulnerability evaluation; build calls can overrun the phase
      boundary. Exact old timeout cause remains unknown; instrumentation is not the frozen process.
- [x] Screen interruptible nested vulnerability eval: 51.3% vs v05 on 300 games / 150 paired
      generated maps, CI 40.2-62.4%. Not promoted; flag off. Tighter measured build-phase
      cutoff and 0/4 candidate process failures are mechanism/timing evidence, not strength.
- [ ] Diagnose the latest frozen-v05 strict process failure: 1/4 games in the panel-tooling
      check, P1 turn 41 on 903182662; input preparation/send took 66.6288 ms before a response
      was observed. Do not attribute this to search without evidence. Keep the failed report
      `build/analysis/panel-v1-restored-v05-process.json`; no timing-readiness claim or retry-to-green.
- [x] Add opt-in process input phase/host-counter diagnostics without changing deadlines or Player.
      Six new tests; one fixed eight-game diagnostic run had 0 failures, later response max
      36.81 ms and input send max 4.35 ms. Original stall did not recur, so its cause remains open.
      Preserve process-input-diagnostic8.json; counter limits and findings are in DEVLOG.

- [x] Add descriptive bot labels without renaming frozen classes; retain six named local
      experiment recipes with full parameters and a classpath implementation guard. Reports and
      local replays preserve ids plus labels/provenance; CG names remain untouched. No promotion
      or expansion of the default opponent panel. See bot_catalog.md.
- [x] Reassess retained narrow reply rerank (K2/R1) against v05 and independent pressure-v1:
      three fixed 300-game development match-ups. Direct v05 57.3%, CI 46.2-68.4%; pressure
      66.0% vs fresh v05 control 69.3%, 46 added/56 lost wins. Keep off, no confirmation or
      parameter tuning; current/Player unchanged. See DEVLOG.
- [x] Audit narrow-rerank prediction and prefix ranking on 110 shared-state observations from
      24 captured games. Exact build forecast 12/55; oracle improves ranking in 4 observations
      (3 unique states); nominal/ample first prefix matches 81/81. Five later-build differences.
      Prediction, projection and timing limitations separated; keep off. See reply-study.md.
- [x] Inspect first-build/own-sabotage interaction on the same 110 observations: target depends
      on prefix in 8/81 cases; adapted joint choice changes in 3 unique states (oracle projection
      improves in 3, worsens in 0, not match outcomes). One example prefers the old prefix with
      a different target. Keep v05 unchanged; see joint-reply-study.md.
- [x] Screen two prefixes x known-state sabotage targets with old-pair fallback. Cost check
      13.50 vs 12.81 ms; direct v05 43.5% (300 games), CI 32.4-54.6%; pressure 65.3% vs
      fresh v05 65.0% on the same maps. Not promoted; flags off, Player restored to v05.
      162 tests, 264 exact replays, candidate/restored process checks 0/4 failures each.
      Retain old experiment bytecode/ids in one local archive; see joint-action-experiment.md.
- [x] Isolate the joint flag with a fixed direct on/off K2/R1 ablation: 139-159-2, 46.7%,
      map-grouped CI 35.6-57.8% on 300 games / the same 150 joint-screen development maps.
      Inconclusive, not proof of harm or equivalence; no promotion, tuning or confirmation games.
      Both sides use the same current code, differing in exactly one parameter. Player unchanged.
- [ ] Audit the actually emitted full actions in saved joint-on/off states: separate no-hit choices
      and suppressed leftover building from projected pair value before revising this branch.
      Reuse the saved 110-position cost sample; compare through core, keep predicted vs recorded
      opponent actions separate. Mechanism diagnosis only, not another strength/confirmation run.
      Deferred while testing the user-chosen independent heuristic branch.
- [x] Start an independent fixed-work heuristic policy for our own bot, not a boss clone.
      Sticky known-board sabotage, simple route priorities and target avoidance; default-off.
      Screen: 42.0% vs v05 (300 games), CI 30.9-53.1%; style check 52.25% vs pressure-v1
      (200 games), CI 38.7-65.8%. No promotion or tuning. 169 tests, 264 arena + 21 boss replays
      exact, candidate/restored-v05 strict process samples each 0/4 failures. Player restored to v05.
- [x] Screen bounded recovery-paint sabotage priority on observed active routes, with unchanged
      construction and other weights. Matched v05 screen: ON 45.0% vs OFF 46.7%, 300 games each;
      31 added / 36 lost wins. ON CI 33.9-56.1%; style check 55.0% vs pressure-v1, 200 games,
      CI 41.4-68.6%. No promotion; USE_HEURISTIC_DETOUR remains off. 177 tests, 264 arena + 21
      boss replays exact, candidate/restored-v05 process samples each 0/4 failures; Player restored.
      Direct ON/OFF self-play was replaced before benchmarking because neutral-rail mirrors did
      not exercise sabotage. OFF history and explicit active-route fixtures guard the baseline.

- [x] Inspect the five new Boss 4 replays (904047070/111/151/204/262): 313/315 sabotage turns,
      first-hit bypass progress in 4/30 eligible observed cases, later-hit progress more common.
      Added a validated known-board probe, four tests; 181 tests and 264 arena + 26 boss replays
      exact. Existing EvalBot backup already searches bypasses; no policy change. See DEVLOG.
- [x] Screen deadline-aware cheap-bypass priority in EvalBot, default-off: 141-157-2 (47.33%)
      vs v05 on 300 games / 150 new paired maps; CI 36.24-58.42%, gate not passed. Matched
      aggressive-v02 control: candidate 63.5% vs v05 73.5% on 200 games each, 19 added / 39 lost
      wins. Keep USE_DEFENSE_PRIORITY off; no tuning/confirmation or new frozen version.
      Core unchanged; 189 tests, 264 arena + 26 boss replays exact. See DEVLOG for artifacts.
- [ ] Diagnose the new restored-v05 startup/input failure before another timing-readiness claim:
      defense-priority-v1/restored-v05-process.json failed 4/4 at turn 0. Input send took
      367..1139 ms; two sends exhausted 1000 ms, with no response timestamp in any failure.
      Candidate passed 0/4 but first max 807 ms misses the headroom target. Preserve both
      reports; input preparation/blocking write/host/JVM startup are not yet separated.
- [x] Run the fixed four-game IO diagnostic on that unchanged v05: no failures, first max
      635.88 ms / later 35.44 ms. Start input time is almost entirely flush (76..144 ms), not
      formatting (<0.15 ms). Correct misleading console response labels, two regression tests.
      The older seconds-long stall did not recur: its exact cause remains open, not fixed.

- [x] Screen bounded three-turn own construction planning (three root routes, at most 27 plans):
      147-153 (49.0%) vs v05 on 300 games / 150 paired maps, CI 37.91-60.09%; not promoted.
      Matched aggressive-v02 control: candidate 66.5% vs v05 75.0%, 200 games each; 34 added /
      51 lost wins. USE_OWN_BUILD_PLAN stays off; no tuning or confirmation. 200 tests, 264 arena
      + 26 boss replays exact, candidate/restored-v05 process samples both 0/4 failures. Final
      Player restored to v05; previous startup failure cause still open. See DEVLOG for limits.

- [x] Complete independent eight-turn simultaneous rollout strategy: **170-130 (56.67%)** vs
      v05 over 300 paired games, CI **45.58-67.76%**, confidence gate not passed. Separate
      200-game checks: **68.5%** vs v03, **74.5%** vs pressure-v1. Keep USE_ROLLOUT_POLICY off;
      no new version/confirmation/tuning. 214 tests, 264 arena + 27 boss replays exact, candidate
      process 0/4 failures. See DEVLOG/rollout-policy.md for fixed portfolios and timing outliers.
- [x] Correct rollout-v1 seed/reserve overlap: keep fixed screen outcomes as development evidence,
      retire confirmation-v1 without altering its historical file, register untouched v2
      (90000..90499). Future confirmation requires map-hash exclusions.
- [ ] Diagnose renewed instrumented restored-v05 process failure: rollout-v1 sample failed 4/4
      (turns 6,17,0,0); first flush 266..1178 ms, one later flush 158 ms, another later response
      134.9 ms after only 2.1-ms send. Historical cause remains open; the later fixed strict
      rollout-process-v1 samples pass 0/42 problems each, without reproducing this failure.

- [x] Complete user-requested fixed 2x1000 rollout-v1 vs v05 confirmation: **605-395 (60.5%)**
      and **625-375 (62.5%)**, individual gates pass. Pooled **1230-770 (61.5%)**, map-grouped
      CI **57.21-65.79%**, gate pass, zero errors. Disjoint pools v2/v3 consumed; v4 is a fresh
      reserve. Source/configuration unchanged. Current remains v05; no promotion requested.
- [ ] Investigate confirmation timing outliers before a readiness claim: run 2 max A/B
      **515/461 ms**, slow **307/180** across 36 games, compared with run 1 **61/39 ms**, slow
      **2/0**. All results retained; cause remains unknown. Later fixed serial process checks
      pass, but do not reproduce/explain the old spikes or establish safety on the CG host.

- [x] Complete fixed strict process readiness check of unchanged RolloutBot and v05: **0/42
      problems each**, 21 maps with both sides, 1000/50-ms deadlines and zero grace. Candidate
      max first/later **662.59/47.857 ms**, p99 later **39.05 ms**; v05 **668.31/40.09 ms**.
      Two candidate turns exceed the separate 45-ms headroom target. No conditional diagnostics
      triggered, no retries or strategy changes. Final Player v05; candidate artifact archived.

- [x] Freeze confirmed RolloutBot as v06 and select it in current/MainBot/catalog at the user's
      request. Full parameters match the fixed 2x1000 record; frozen source differs only in names.
      Default/frozen builds compile and match decisions; 217 tests pass, 264 arena replays exact.
      Final Player 56,007 chars. Final fixed process check completed but **FAILED 1/42**, no retry.
- [ ] Resolve v06 timing readiness before claiming the final artifact passes: 903510176 P0
      turn 73 response **50.462 ms** (50-ms deadline), send **0.164 ms**, consumer **50.5301 ms**.
      Prior candidate 0/42 pass does not erase this result. Preserve the exact failing artifact
      and report under `build/analysis/v06-promotion/`; distinguish child runtime from host/reader
      scheduling before changing budgets or policy. v06 selected locally, no upload performed.

- [x] Record user-reported v06 arena upload/rank 153/645 (previous v05 rank 270); analyze all
      30 Boss 4 games with the corrected user-attributed 16/14 split (first v06: 904146009).
      Outcomes 2-14 vs 5-9; 30/30 exact, no failures/skipped commands. Probe all 1002 new-cohort
      states using frozen, noise-free v06. Original v1 reports retained; corrected v2 reports added.
      Short useful routes can be absent from the six-route portfolio; 21/23 large-damage loss
      turns already have the correct boss target in the model. 220 tests pass, two compact
      pre-turn fixtures retained. See boss-v06-loss-analysis.md; selected v06/Player unchanged.
- [x] Test one cheap positive-net-income slot inside the existing six-route portfolio when
      own income is zero. Fixed screen vs v06: **160-134-6, 54.33%**, 300 games / 150 maps,
      CI **43.24�65.42%**, gate not passed. Older v05 check: **124-76, 62.0%**, 200 games.
      Default-off flag retained, no promotion/tuning/confirmation, core and frozen bots unchanged.
      225 tests pass; candidate/restored-default process samples both 0/12 problems,
      original 8 plus 4 missing-side checks; all four maps on both sides covered.
      Final artifact and exact results: quick-income-experiment.md.
- [ ] Diagnose quick-route selection and value on fixed shared pre-turn states: distinguish
      candidate availability, complete timed scenario coverage and eventual choice; include wins
      and losses, known-state predictions versus recorded-action counterfactuals. No further
      parameter search or confirmation until the mechanism is understood.

- [ ] Fix ProcessCheck map/side scheduling: current map index `i` and player `i%2` repeat the
      same side for even-sized map pools. Use paired map selection with a regression test; audit
      older process coverage before claiming both sides. Quick-income samples were supplemented
      with only the four missing combinations per artifact; strength BenchmarkRunner is already paired.

## Someday / maybe

- JIT warm-up inside bench threads to remove spurious "slow" counts in the arena
- Remove the now-redundant `tools/CgInkStudy.java` when doing a tooling cleanup
- Automated paste channel into the CG IDE (Cursor browser blocks file:// and localhost fetch)
