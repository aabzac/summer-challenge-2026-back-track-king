# Plan

Roadmap with status. History and reasoning live in `DEVLOG.md`, per-version numbers in `BOTS.md`.
Update the checkboxes when a step is finished; add new steps at the bottom of the open phase.

## Objective clarification (2026-10-06)

The user clarified that 70% was an illustrative question, not a requested target.
The assistant imposed that fitting screen; retain its historical result below,
but do not carry it forward as a requirement. The objective is arena strength,
and exact teacher-action agreement is only a diagnostic. No further imitation
training is automatically scheduled by the completed fitting experiments.

Future exploratory training should use a compact log and a handful of atomic
checkpoints, retaining settings, data/split identities, optimizer/RNG recovery and
final metrics. Per-update full NPZ plus two JSON files and auditing every Adam
step are not the default. Profile a bounded run before choosing batching,
compiled loss code or a framework migration. Preserve the existing engine,
numerical, artifact and practical-v4 strength checks. The still-open v09
all-cell-risk route-candidate idea below is the recommended next strategy
direction for discussion; no new strategy experiment was launched in this review.

## Return to v09: rollout-risk-v1 (2026-10-06)

- [x] Audit all-cell instability-cost bypasses on 64 saved threatened positions:
      30 retained new construction sets in 15 positions, matched controls zero.
      Mechanism evidence only. [Protocol and result](rollout-risk-v1.md).
- [x] Implement one default-off route proposal with the existing evaluator and
      budgets. Check actual actions (6/64 changed), default-off parity, engine
      fidelity (292/292), tests and exact submission (99,733 chars; strict 8/8).
- [x] Complete the fixed 300/+200/+500 practical-v4 comparison against v09 on
      500 fresh paired maps: 518 wins / 434 losses / 48 draws, 54.2% overall.
      Zero game errors; two workers throughout. Final NOT_PASSED (<55%).
- [x] Close the experiment, preserve evidence and record the rejected result.
      Boss stage skipped after local failure; flag stays OFF, selected v09 and
      saved default submission unchanged. No further neural training or promotion.

## Indrill fit512-v1 (2026-10-04/05)

- [x] Predeclare the user-authorized intermediate panel: first 32 ranked B maps,
      16 positions per map, fixed 512 rows; fresh B initializer, unchanged model
      and objective. Exactly 48,000 updates with batch 32 gives 3,000 visits per
      row. Final fitting target is at least 359/512 exact full actions (70%).
      [Protocol](indrill-fit512-v1.md). No automatic extension or promotion.
- [x] Independently review orchestration, run synthetic checks and freeze source,
      runtime, exact selection, original corpus/provenance and predecessor evidence.
      Final proof and freeze pass; 512 unique inputs, no conflicting labels,
      no map/input overlap with the fixed 1,020-row holdout.
- [x] Complete fixed training measurements at 0/3k/12k/24k/48k and final-only
      descriptive holdout evaluation. Report multi-cell construction explicitly.
      Train build/ink/joint 107/299/59 of 512: FIT70_NOT_ESTABLISHED (59 < 359).
      Holdout 85/396/25 of 1020, below saved B2048's 111/424/42; multi-cell
      builds exact in 41/404 training and 25/838 held-out rows. Fixed run closed.
- [x] Audit saved state/metrics without new learned scoring: PASS for all 48,001
      checkpoints, exact sampler exposures and saved Adam parameter arithmetic.
- [x] Preserve the compact evidence archive, finish documentation and commit locally.
      Packaging and independent streaming review PASS: 96,058 files, five scheduled
      full states, all member hashes and gzip integrity checked. The other 47,996
      full states remain locally retained. No further training or game run follows.

## Indrill generalization-v1 (2026-10-04)

- [x] Predeclare a separate larger-panel diagnostic after joint64-12k-v1's pass:
      fixed 2,048 training rows/128 maps and 1,020 research-holdout rows/51 maps;
      fresh original B seeds, unchanged joint objective/network, batch 32,
      exactly 12,000 updates. Preserve original capacity-v1 Stage B as unexecuted.
      [Protocol](indrill-generalization-v1.md).
- [x] Review and verify the separate wrapper, synthetic checks, original data and
      runtime/source bindings; freeze before gradients.
- [x] Complete the one fixed training run and final-only candidate/Q-v2 holdout
      comparison. Report training curve, map-aggregated absolute accuracy and gaps,
      old40/later11 cohorts, and the predeclared diagnostic transfer decision.
      Final train build/ink/joint 231/892/105 of 2048; holdout 111/424/42 of 1020,
      Q-v2 control 29/8/1. TRANSFER_EVIDENCE versus this weak control; absolute
      imitation remains low and this is not arena-strength evidence.
- [x] Audit retained states/results, preserve compact evidence/archive, update
      documentation and commit. No new games, CG export/upload or promotion.
      PASS: 12,001 checkpoints, exact saved Adam arithmetic and sampler; archived
      24,062 files including four scheduled checkpoints.
- [ ] Separately diagnose low training agreement on the larger panel before RL or
      strength testing. Distinguish optimizer/budget and representation limitations;
      no additional training, altered architecture or game run is started by this result.
      The proposed B2048 continuation to 24k was not run. User chose the separate
      fit512-v1 diagnostic above after discussing its purpose and sample size.

## Phase 1 â€” infrastructure (done 2026-09-13)

- [x] Gradle/Java 17 skeleton, package layout, `AGENTS.md`, Cursor rules
- [x] `btk.core` rules engine shared by bot, referee and tools; JUnit tests
- [x] CG replay download (`cgFetch`) and engine validation against them (`cgValidate`) â€” 108/108
- [x] Full rules inferred from Silver replays (ink at 4 disrupts, game end) â€” `docs/rules/inferred_silver.md`
- [x] Referee, procedural + real maps, parallel benchmark with Wilson CI, side swap
- [x] Process-level submission check (`procCheck`): protocol, first-turn warm-up, 50 ms turns
- [x] Single-file merger (`submit`) â†’ `build/submit/Player.java`
- [x] Enforce the 100,000-character submission limit; explicitly restore frozen v05 with
      `submit -PsubmitVersion=v05`, recording resolved configuration and source hash.
- [x] Replay visualizer for local and CG-derived replays
- [x] Study tool for what top players do (`cgStudy`)
- [x] Bots v00 (autoplace), v01 (greedy), v02 (eval + DISRUPT), v03 (vulnerability eval, backups)

## Phase 2 â€” leagues (open)

- [x] Paste `Player.java` (v03) into the CG IDE and advance â€” Wood 2 â†’ Wood 1 â†’ Bronze â†’ Silver;
      initially around rank 350/645, with Gold not yet open at that historical checkpoint
- [x] Record user snapshot, 2026-09-15: Gold opened today; our entry remains in Silver,
      rank 235/622. User-reported, not an API verification or proof of warmup/strategy improvement.
- [x] Capture the Bronze statement verbatim into `docs/rules/bronze.md`; reconcile with
      `inferred_silver.md` â€” all inferred rules confirmed; Bronze is the final rule set
- [x] Referee source found (linked from the statement) and cloned to `tmp/referee-src`
      (gitignored): impossible actions are skipped, not fatal â†’ `Rules.expand` aligned + tests
- [x] Port `GridMaker` 1:1 (CG supplies `SecureRandom` SHA1PRNG through `java.util.Random`) â†’
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

## Phase 3 â€” strength (open; one feature per version, bench-gated)

Gaps measured in `BOTS.md` â†’ "What top Silver players do". Candidate order:

- [x] Add an independent sabotage-first reference and matched opponent panel: pressure-v1,
      v05, v03, aggressive v02. Pressure baseline: 99.9% disruption, 61.3-turn games; v05 wins
      69.0% over 300 paired development games. Local behavioral coverage, not proven top-player imitation.
- [ ] Calibrate/extend reference styles against fresh arena behavior; pressure-v1 builds on only
      56.7% of turns, so it does not cover every strong opponent. Preserve recorded reference ids.
- [x] No idle turns: v04 spends otherwise idle paint extending its network through stable nearby
      cells, preferring town regions â€” 64.7% vs v03 over 300 paired games
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
      CI **43.24–65.42%**, gate not passed. Older v05 check: **124-76, 62.0%**, 200 games.
      Default-off flag retained, no promotion/tuning/confirmation, core and frozen bots unchanged.
      225 tests pass; candidate/restored-default process samples both 0/12 problems,
      original 8 plus 4 missing-side checks; all four maps on both sides covered.
      Final artifact and exact results: quick-income-experiment.md.
- [ ] Diagnose quick-route selection and value on fixed shared pre-turn states: distinguish
      candidate availability, complete timed scenario coverage and eventual choice; include wins
      and losses, known-state predictions versus recorded-action counterfactuals. No parameter
      search yet. The subsequent user-requested practical-policy evaluation below authorizes
      fresh confirmation of the unchanged candidate before that optional mechanism study.

- [x] Adopt practical-300-1000-v1 in the contest skill, project rules and report labels:
      direct 300/60% or fresh 1000/55%, then matched common-opponent 300/+3 points or fresh
      1000/nonnegative difference. CI is diagnostic; no overall 95% guarantee.
- [x] Apply practical-300-1000-v1 to unchanged quick-income: reused screen 54.33%; fresh
      1000 vs v06 = **525-465-10, 53.00%**, below 55%. Direct failure stops the rule;
      matched v05 stages skipped, no promotion, v06/Player unchanged. 226 Java + six Python
      tests pass, skill valid, 264/264 arena replays exact. See quick-income-practical-v1.md.

- [ ] Fix ProcessCheck map/side scheduling: current map index `i` and player `i%2` repeat the
      same side for even-sized map pools. Use paired map selection with a regression test; audit
      older process coverage before claiming both sides. Quick-income samples were supplemented
      with only the four missing combinations per artifact; strength BenchmarkRunner is already paired.

- [x] Add first-screen continuation floor in practical-300-1000-v2: <=52% stops,
      >52% and <60% receives fresh 1000, >=60% passes directly. Updated skill, rules,
      report/checker and boundary tests. Historical quick-income verdict unchanged; no new bench.

- [x] Try quick follow-up 1 (earlier complete comparison): 55.67%/300 then 49.70%/1000,
      NOT PASSED. 227 tests, 264 exact replays; all standby-paused games retained.
      See quick-income-followups.md for fixed protocol, 77-state coverage study and limitations.
- [x] Quick follow-up 2: income per actual construction turn, 150-138-12 / 300 = 52.00%;
      STOP_SCREEN_300 at the strict >52% floor, 229 tests pass. No continuation or promotion.
- [x] Quick follow-up 3: predicted-threat survival filter, 153-142-5 / 300 = 51.8333%;
      STOP_SCREEN_300, 230 tests pass. All three done, 1900 games, no promotion.
      Temporary sleep/display requests released; first experiment standby limitation recorded.

## Someday / maybe

- JIT warm-up inside bench threads to remove spurious "slow" counts in the arena
- Remove the now-redundant `tools/CgInkStudy.java` when doing a tooling cleanup
- Automated paste channel into the CG IDE (Cursor browser blocks file:// and localhost fetch)

## Independent rollout follow-ups (registered 2026-09-16)

- [x] Diverse opponent projects: 151-148-1 / 300 = 50.50%; STOP_SCREEN_300, flag OFF.
- [x] Reconsider own continuation: 52.1667% / 300 then 48.40% / fresh 1000; NOT PASSED.
- [x] Extra sabotage target: 136-144-20 / 300 = 48.6667%; STOP_SCREEN_300.
      All three complete: 1900 games / 950 fresh maps, zero errors, 236 tests; retain v06.

See docs/rollout-followups.md for frozen variants, pools and conditional stages.

## Current development focus (2026-09-17)

- [x] Resume original quick-income-v1 as the base for further improvements at the user's
      request. Recipe and historical evidence: docs/working-candidate.md. Later flags OFF.
      Control and selected submission remain v06; this is not a new strength experiment.

- [x] Export exact quick-income candidate beside Player as PlayerQuickIncome.java for the
      requested arena trial. Compilation passes; strict process check has 1/8 timeout games,
      recorded and disclosed. All 23 original submission files unchanged; no promotion/upload.


- [x] Test two independent construction projects on resumed QuickIncome: 53.6667% / 300
      then 50.60% / fresh 1000 vs v06, NOT PASSED; fixed separate QuickIncome ablation 51.50% / 300.
      1600 games / 800 new maps, zero errors, 241 Java + seven gate tests; 264 exact replays.
      Keep USE_ROLLOUT_SPREAD OFF, original QuickIncome base and both arena files unchanged.
      Mechanism, protocol and evidence: docs/spread-construction-v1.md.

- [x] Analyze a distinct network-control strategy: all 133 saved boss states / 14 validated
      replays, exhaustive 37,172 legal own single-cell actions through core. In 45 states a
      positive cell outside every retained route beats every ordinary root on immediate
      known-board net income; 83/94 changed directed-pair paths retain length. No strength
      games or policy change. Proposal/evidence: docs/network-control-strategy.md.
- [x] Test default-off single-cell network rerouting on QuickIncome: 145-155-0 / 300
      vs v06 = 48.3333%, STOP_SCREEN_300; fixed separate QuickIncome ablation 152-148-0 /
      300 = 50.6667%. NOT PASSED; no continuation, tuning or promotion.
      600 games / 300 fresh paired maps, zero errors; 254 Java + seven gate tests,
      264 exact arena replays. Final identity/pairing audit passes, all 25 existing submission
      files unchanged. Retain NETWORK OFF and selected v06. docs/network-single-cell-v1.md.
      Diagnostic cost: ordinary comparisons fall 937 -> 804 at nominal time, unchanged with
      ample time; single-cell leftover paint and fixed continuation remain possible costs.

- [x] Test network search only after complete ordinary QuickIncome search. Direct 62.50% / 300
      vs v06 PASS; common-v05 matched screen both 57.3333% / 300, then separate matched 1000:
      candidate 64.90%, v06 56.90%, +8.00 points, strength PASS. Fixed independent QuickIncome
      ablation 59.6667% / 300. Total 3200 games / 950 fresh distinct maps, zero errors.
      261 tests, 264 exact replays, 113 audit checks; unchanged ample ordinary action in all 133
      diagnostic states. Exact candidate strict process 0/8 problems, max later 44.6592 ms.
      All 25 existing submission files preserved. Mode OFF, v06 selected; no promotion/upload.
      Full results and exact candidate identity: docs/network-after-base-v1.md.

- [x] Record the user's pre-v07 arena snapshot (2026-09-18): QuickIncome currently deployed,
      rank 111 / 627. User-reported; exact upload hash and placement status unverified.
      Keep this checkpoint separate from the new local v07 selection and its packaging checks.

- [x] Promote the verified network-after-base-v1 candidate to v07 at the user's request.
      Freeze unchanged policy and all 98 parameters; select current/main/v07, protect old
      frozen defaults and pin historical diagnostic recipes. 273 tests, 264 exact CG replays.
      Main Player is 75,288 characters, strict 0/8 problem games, max later 44.7744 ms.
      Previous v06 submission archived; no upload/push. Evidence: docs/v07-promotion.md.

## v07 boss and arena follow-ups (2026-09-18)

- [x] Validate and render the latest 20 user-attributed v07 boss games: 6 wins, 14 losses,
      zero failed/skipped actions. Build filtered gallery with exact multi-cell-steal and
      stalled-sabotage examples. Full arena corpus now 292 exact games; boss corpus 50 exact.
      See boss-v07-loss-analysis.md and the linked replay gallery.
- [x] Verify joined-route synergy already uses whole-network desired-pair income. Diagnose
      compressed-route bridge dependencies: 561 repeated candidate observations in 127/1581
      saved states. Add opt-in dependency correction and independent opt-in two/three-cell
      network chords. 286 Java tests + seven gate tests pass; default current matches frozen
      v07 on all 146 fixed public-state checks. Neither feature changes selected defaults.
- [x] Fetch 28 new arena games and record a reproducible opening survey: leaders strongly
      favor protected ownership near several towns; collisions limit realized town coverage.
      This is observational evidence for a later cross-route opening experiment, not a strength
      result. See arena-opening-survey-v1.md.
- [x] Complete route-dependencies-v1 vs v07: 133-160-7 / 300 = 45.50%, STOP_SCREEN_300.
      Zero errors; no continuation or promotion. Keep the dependency flag OFF.
- [x] Complete network-chords-v1 vs v07: 54.3333% / 300 then 54.40% / fresh 1000,
      NOT_PASSED at required 55%. Zero errors; no common-opponent/process continuation.
      Both independent experiments: 1600 games / 800 fresh maps; final identity audit passes.
      Both flags OFF, v07 selected, all 26 original submission files unchanged.
- [x] Trace the separate postponed-sabotage fixed point in 904554072 t37; the dependency
      fix does not change its WAIT decision. Do not replace it with an untested unconditional rule.

- [ ] Independently test WAIT without promised future sabotage in no-project states, with
      multi-turn mechanism traces before strength games. See boss-v07-loss-analysis.md.
- [ ] Independently test protected opening ownership across multiple town approaches;
      prospective design and risks in arena-opening-survey-v1.md. Do not combine with chords.

## Independent live-boss evaluation (2026-09-18)

- [x] Defer the recurring postponed-sabotage follow-up at the user's request; focus on
      testing existing network chords against the real boss. No policy/default changes.
- [x] Move replay folders bot -> boss with complete byte/hash checks (105 original files,
      plus three subsequent manual captures from the old server). Add missing-old-path
      compatibility and recursive version/build discovery without raw/metadata duplicates.
- [x] Add first-turn build/config MESSAGE, immutable source registry and qualified replay
      attribution. Canonical stdout identifies builds; missing markers stay unknown.
- [x] Import the user-provided HAR into an ignored local session file, preserving original
      IDE source. Independent Python API runner works with the browser closed; no userscript
      batch fallback needed. Restart local capture server at protocolVersion 2.
- [x] Verify 299 Java tests, 57 Python tests, 292 arena + 50 original boss exact replays,
      both isolated tagged artifacts in four strict process games each (zero problems),
      and five live API technical games including identical-map and both-seat checks.
- [x] Predeclare the separate 40-pair live-boss comparison, exact paired binary test and
      both artifact hashes. Historical 6/20 is not a known 30% population rate. See
      boss-evaluation-protocol.md, boss-api.md and chords-boss-v1.md.
- [x] Complete the fixed 80-game live-boss comparison under explicit amendments 1 and 2, audit and render every saved result;
      report this separate evidence without changing the failed local promotion verdict.


### Live-boss interruption recorded (2026-09-19 local; run 2026-09-18 UTC)

- [x] Preserve and independently audit all 20/80 completed games (ten complete pairs):
      chords 3W/7L, v07 2W/8L. No completed-sample test or improvement verdict.
- [x] Record unresolved slot 21 HTTP 422 without issuing a second play. Read-only
      authorization succeeds; one separate technical diagnostic game also succeeds.
      The earlier response body/game ID is unavailable, so the original cause is unknown.
- [x] Render and validate all 79 saved boss replays; publish a partial comparison gallery.
      Original build/submit files remain byte-identical, v07 selected, new flags OFF.
- [x] Superseded by user-authorized amendment 1: recover the actual game ID for slot 21 before resuming the
      same frozen 80-game plan. Never delete its intent, replace it or pool another sample.
      See evidence/chords-boss-v1.interrupted.json and boss-api.md.

- [x] Final verification: 299 Java + 71 Python tests; incomplete canonical lookup recovery
      regression, 79/79 boss renders, exact statistical evidence reproduction.


### Amended live-boss completion (2026-09-19)

- [x] Honor the user's explicit exception for failed HTTP requests; complete the same
      forty-map-pair schedule with separate authorized attempts at slots 21 and 51,
      respecting the reported rate limit with a cooldown and longer pauses.
- [x] Independently audit 80 analyzed games / 82 attempts, unchanged first fifty results,
      original plan/source/configuration/process evidence, boss, maps and seats.
- [x] Record candidate 9W/31L/0D / 40 = 22.5% outright wins; v07 10W/30L/0D / 40 = 25.0%; exploratory paired p = 0.725585938, with protocol deviation visible.
- [x] Validate/render all 139 saved boss replays and refresh the eighty-game gallery.
- [x] Verify 89 Python tests; keep all twenty-six submission files and policy defaults
      unchanged. Original recovery-ID prerequisite is superseded by explicit amendment 1.


## Cached-control boss selection v3 (2026-09-19)

- [x] Adopt the user-approved cached boss criterion: 40 NEW candidate games, same 40
      maps/seeds/seats, score >= verified control, wins 1 / draws 0.5, equality passing.
- [x] Preserve direct 300 / conditional 1000 thresholds; replace mandatory older/style stages.
      Permit sequential boss games alongside required local 1000, without rescuing local failures.
- [x] Implement immutable control reuse, generic candidate/control source arms, ancestry,
      no automatic replay of uncertain requests, canonical/league/build/technical checks.
- [x] Add the combined v3 checker and meaningful threshold, pairing, corruption, recovery,
      cached ancestry and concurrent-stage tests; retain the historical v2 tools and verdicts.
- [x] Verify the actual initial v07 cache: 40 maps / 20+20 seats / 10 points, and document cache
      refresh, collection dates, hidden boss drift and known-map statistical limitations.
- [x] Update project instructions, active policy docs and benchmark reporting. No new games
      or strategy selection changes. Full rule and commands: boss-cache-policy-v3.md.


## Replay viewer usability (2026-09-19)

- [x] Export the real CG seed with exact decimal text; preserve unknown seeds and valid local zero.
- [x] Group colored player names, scores and turn income; label connection ownership and final results.
- [x] Fix cell hover scope/stale-turn data, playback reset, empty-viewer shortcuts and stale replay caching.
- [x] Regenerate the existing 139 boss and 7 arena renders; verify canonical bytes remain unchanged.
- [x] Check seed edge cases, the full Java suite, existing bot-label checks and browser interactions.

## Independent search and opening experiments (2026-09-19)

- [x] Implement default-off RHEA construction continuations after complete v07 base/network search.
- [x] Implement independent protected opening claims for turns 0-2, returning to v07 afterward.
- [x] Verify mechanisms, default-off parity, core replay fidelity, submission packaging and strict timing.
- [x] Freeze both candidates and separate fresh pools; run each fixed v3 300-game screen against v07.
- [x] Complete every required fresh 1000 and cached-control 40-game boss stage after a qualifying screen.
- [x] Record both outcomes and commit; strategy selection remains a separate decision.

Completed: RHEA 44.8333% / 300, NOT_PASSED; protected opening 62% / 300 and
13/40 vs cached v07 10/40, PASS. No direct 1000 was required. Both flags remain OFF;
v07 remains selected. Results, readiness and replay evidence are recorded in BOTS/DEVLOG.

## Promote Protected Opening to v08 (2026-09-19)

- [x] Select the exact approved 107-parameter candidate and freeze both v08 classes.
- [x] Preserve historical versions and archive the previous selected submission.
- [x] Verify configuration/source/action parity, full tests and replay fidelity.
- [x] Build default and frozen v08 exports, complete the fixed eight-game strict process panel.
- [x] Record exact artifact identities and outcomes; commit the authorized local promotion.

Verification complete: 338 tests and 292 + 179 replays pass. Fixed main-artifact panel
completed 8 games with 1 timeout (903510176, P1, turn 29, 67.5805 ms / 50 ms).
v08 is selected locally; timing readiness remains OPEN. No retries or arena upload.

- [ ] Investigate the retained v08 timing failure in a separate prospective follow-up;
      keep the complete 1/8 failed panel and do not retry it as promotion evidence.

## Boss-winners study and sabotage target experiments (2026-09-19)

- [x] Calibrate the promotion target from the public API: boss rating 29.69, ours 24.91;
      now-Gold players beat the boss 55.7%, fast promotions 70-95%; v08 is at 13/40.
- [x] Build the winners corpus (368 boss games vs now-Gold players) and the reproducible study
      tool `tools/diagnostics/boss_winners_study.py`; record findings in boss-winners-study-v1.md.
- [x] Test live-project sabotage targeting (`USE_ROLLOUT_LIVE_TARGET`): 52.67% / 300, then
      51.45% / fresh 1000 vs v08, NOT_PASSED. Flag OFF.
- [x] Test never-idle disruption (`ROLLOUT_ALWAYS_DISRUPT`): 49.17% / 300, STOP_SCREEN_300. Flag OFF.
- [x] User authorized the boss stage for live-target as a diagnostic after the local failure:
      strict process 0/8, then 8/40 vs cached v08 13/40, NOT_PASSED; 40/40 replays exact.
- [ ] Separate the live-target boost for our real choice from its use inside the simulated
      opponent before concluding that live-project targeting itself is useless.
- [ ] Explain what Gold players buy with their late "dead" construction (36% of cells placed in
      turns 31-45 never score) before proposing a construction-side change.

## Indrill (Legend #1) study and track-value experiment (2026-09-20)

- [x] Fetch all 282 battles of the leader Indrill (Python3, 36.34), validate 282/282 exactly,
      and study them with `indrill_study.py` and the core-based `cgLeaderStudy` feature export:
      sub-millisecond heuristic, 100% disruption, 97% full-paint construction, 93.6% of hits end
      in inks, 48% construction-class hits inked before they earn. See indrill-study-v1.md.
- [x] Calibrate its target valuation (conditional logit over 14,423 decisions) and implement
      the default-off `USE_ROLLOUT_TRACK_VALUE` (+ `SELF_ONLY`) in RolloutBot; 343 tests pass;
      merged candidate 98,886 / 100,000 characters (a corridor fallback was removed for size).
- [x] Fixed practical-v3 stages vs v08: both-chooser 49.50% / 300 STOP; self-only 54.17% / 300
      then 50.85% / 1000, NOT_PASSED. Zero errors, 1600 games. Flags OFF, v08 selected.
- [x] Build a leader-like local reference opponent `leader-v1` (`LeaderBot`, frozen): full paint
      every turn, sticky DISRUPT on unfinished opponent construction (fitted Indrill valuation),
      protected opening. Registered like pressure-v1 (BotFactory + `leader-v1.properties` +
      catalog entry), reusing only existing Params fields so frozen v06-v08 records are untouched.
      `LeaderBotTest` (6 tests) + full Java suite pass. 40-game smoke vs v08: 100% accepted
      disruption vs 62%, loses on strength (~15%) - a screening opponent, not a strength bench.
- [x] Re-screen the retained sabotage flags (track-value self, live-target, always-disrupt)
      against `leader-v1` as a behavioral coverage check (`tools/run_leader_coverage.py`, matched
      panel, 4 x 300 games, seeds 540000..540149, zero errors): v08 82.33%, flags 83.33 / 82.33 /
      83.00%, per-map deltas within one SE of zero; construction unchanged (1.31-1.32 cells/turn).
      Not a gate; all flags stay OFF. Found that leader-v1 places on only 41.5% of turns (1.18/3
      paint) - it covers the leader's sabotage meta, not its construction meta; docs corrected.
- [x] Identify Indrill's routing model behind the 48.7% "off-route" cells: there is none. Per-cell
      study (`cgCellStudy`, `indrill-construction-v1.md`): 86.8% of his cells through turn 45 are
      within +1 paint of some optimal completion or on take-over / bypass paths; the rest is late
      filler. The edge is 60% vs 47% of cells earning and full paint on 97% of turns.
- [x] `leader-v2` reference (never-idle construction, leader-v1 sabotage): 90% placement on his
      states, 20% vs v08; behavioural opponent only.
- [x] Silver boss cell study (40 cached v08 games): equal points/paint, boss spends 1.80 vs our
      1.55 paint/turn, gap in turns 21-45. RolloutBot has no leftover-paint rule.
- [x] fill-v1: `USE_ROLLOUT_FILL` default-off, practical-v4 **569/1000 = 56.90% PASS_1000** vs v08,
      zero errors. NEEDS_BOSS_CACHED_40.
- [x] User decision: RHEA removed from `bot/current` (frozen v08 keeps it). Isolated artifact
      95,279 / 100,000 characters; strict `procCheck` 0/8 problem games (first <= 711 ms, later
      <= 42.4 ms).
- [x] Cached-boss stage (user-authorized): **17 W / 23 L vs cached v08 13 / 27**, PASS; 40/40
      replays exact. Combined v4 verdict **PASS, promotion true** (`evidence/fill-v1-decision.json`).
      The v4 gate gained the explicit `v08` control alias (build `c6b4deae…`, later default-off
      fields at defaults); 126 Python tests pass.
- [x] User decision: **v09 Never Idle** promoted (`bot/v09` frozen, 117-field snapshot,
      `USE_ROLLOUT_FILL=true` default, v01-v08 pinned to false). Default artifact 95,278 chars,
      strict 8-game panel 0/8, 355 Java + 126 Python tests, 292/292 + 259/259 replays exact.
      `v09-promotion.md`, `evidence/v09-promotion.json`.
- [ ] Upload `build/submit/Player.java` (v09, source `1df7c131…`) to the arena; record the
      arena snapshot and the exact deployed hash in BOTS.md.
- [ ] After outcome-guided development on the boss pool, refresh the cached-boss control before
      the next candidate (`tools/boss_cached.py` on v09; the v4 alias `v09` = build `4cda8b1b…`
      already points at the boss-tested artifact).
- [ ] Identify Indrill's turn-0 pre-emptive target rule (town-adjacent region on a cheapest
      route, not the max-traffic one) before re-adding a corridor fallback; mind artifact size.

## Adopt practical-1000-futility-boss-cache-v4 (2026-09-20)

- [x] Adopt the user-approved policy: one pooled 1000-game local comparison (blocks 300/+200/+500)
      with futility-only interim looks (<50% at cumulative 300 or 500 stops), final gate >=55% over
      all 1000; no early pass. The 40-game cached-boss check is unchanged from v3 and may run
      concurrently once the 500-game look clears; a local failure cannot be rescued.
- [x] Implement `tools/practical_gate_v4.py` (pooled assessor + futility + boss combine, reusing
      the v3 cached-boss checker) and `tools/run_practical_v4.py` (three-block runner that stops
      automatically on a futility look, excludes all prior benchmark maps, write-once reports).
- [x] Add `tools/test_practical_gate_v4.py` (thresholds, both futility looks, rejection-only,
      freshness, changed-conditions, boss integration, block sizes, CLI write-once): 11 tests /
      8 subtests. Full suite 125 Python tests pass; v2/v3 checkers and verdicts unchanged.
- [x] Update AGENTS.md #3, README, docs/benchmarking.md (active section; v3 marked superseded).
- [ ] Re-run the retained track-value-v1 `self` candidate under v4 as the first live use of the
      new runner (optional; its v3 verdict was already NOT_PASSED at 50.85%/1000).

## neural-v1: imitation policy learned from the leader (2026-09-20)

- [x] Assess neural approaches for the 50-ms / 100k-character constraints; choose behaviour cloning
      of Indrill with conditional-logit MLPs over engine-grounded candidates (docs/neural-policy-v1.md).
- [x] `btk.bot.neural`: shared `NeuralFeatures` (sabotage 29 / build 42 features, board-only memory,
      feature-side Dijkstra with cost asserted equal to `Pathing.cheapestPath`), `NeuralNet` (int16
      blobs + explicit standardisation), generated `NeuralWeights`, `NeuralBot` (target first, then
      sequential placements; closing-ink guard; 6-ms placement reserve).
- [x] `cgNeuralExport` (candidate datasets through the core, sampled negatives, 40 holdout games) and
      `tools/neural/train_neural.py` (numpy, 40 epochs, linear baseline, quantisation check, Java export).
- [x] Register `neural-v1` (BotFactory, `neural-v1.properties`, catalog, FileMerger `submitVersion`).
      Seven `NeuralBotTest` tests + merged-artifact parity test; 362 Java tests pass.
- [x] Train: sabotage 55.5% / build 23.4% held-out top-1 (uniform 3.1% / 1.1%, linear 50.2% / 20.4%).
- [x] Timing: exporter 4.9 ms/turn features; strict 8-game process check 0/8, later <= 30.6 ms
      (after replacing the referee-exact path search in features: 49.1 ms before).
- [x] Practical-v4 vs v09: 558/1000 = **55.80% PASS_1000**; leader-v1 style check 84%.
- [x] Cached-boss 40-game stage (user-authorised): isolated artifact 0/8 strict; **12 W / 28 L vs
      cached v09 17 / 23**, NOT_PASSED; 40/40 replays exact. Combined v4: **NOT_PASSED**, v09 stays.
- [ ] Follow-ups: fine-tune the same net on local outcomes (ES / policy gradient with `bench` as
      fitness, imitation as prior) and bridge the boss domain with v09-vs-boss states; add other
      Legend players' replays (`-Ppath`/`-Pplayer` lists); value head for the closure margin;
      hybrid = imitation candidates as the rollout's move generator.

## Indrill forum Q-learning prototype (2026-10-03)

- [x] Read the author's post and correct the earlier heuristic inference; document actual
      Q-learning/grid-observation/two-action-head principle and undisclosed details.
- [x] Implement allocation-free core tactical maps, Best/Good/Path aggregates and compact
      outcome-trained grid Q policy; preserve original core and frozen bot identities.
- [x] Add exact-core episode export, numpy Double DQN/n-step/prioritized training,
      generated float32 weights and numerical/terminal/gradient/knapsack checks.
- [x] Complete the fixed 192-game/1,600-update training; freeze indrill-q-v1 weights.
- [x] Verify 379 Java + 126 tools Python + 5 numpy tests, 292/292 arena replays, exact
      generated/binary/merged identities, 18-position numeric parity and strict 0/8 process problems.
- [x] Run practical-v4 vs v09 on 150 fresh paired maps, excluding training/probe maps:
      0/300 score, zero errors, STOP_FUTILITY_300. Evaluation complete; boss skipped,
      no promotion or retries. Record complete evidence and retain rejected candidate.
- [x] Separate indrill-q-v2: learned state-value baseline including WAIT, larger fixed
      random-start schedule (960 games, 14,000 updates), balanced frozen-control seats,
      retained optimizer/target/replay identities; preserve v1 and selected v09.
- [x] Download bounded top-player refresh (86 new unique public games); preserve old
      replay bytes and verify 86/86 with the core. Keep this corpus separate from RL data.
- [x] Independent actual-dataset/checkpoint/map-exclusion audit; 386 Java + 29 related
      Python tests, 292/292 old arena replays, complete 18-position parity, exact isolated
      artifact and strict process 0/8. Exclude all 960 training + 3 probe maps before play.
- [x] indrill-q-v2 practical-v4 vs v09: 0/300, zero errors, STOP_FUTILITY_300; fixed full
      first block complete, continuations/boss skipped, no promotion/tuning/retries.
      Record score diagnostics and full evidence in BOTS/DEVLOG/indrill-q-v2.md.
- [ ] Separate future versions: placement/ink interaction model, learning-objective
      change or teacher-policy prior. Predeclare one experiment and evaluation per version;
      additional games alone did not make v2 competitive.
- [x] Re-read the Indrill forum post, audit Q-v1/v2 and imitation evidence, and inspect
      reward records: 128 final-block games / 12,446 turns; projection changes no reward
      deltas beyond earned-score increments; terminal shaping exceeds outcome in 25% of
      games. Record `evidence/indrill-reward-audit-v1.json` and the Q-v2 follow-up review.
- [x] Separate representation from outcome learning with the fixed supervised
      `indrill-capacity-v1` teacher-set/ink diagnostic on existing Q observations.
      Complete Stage A with FIT_NOT_ESTABLISHED; details and bounded next step below.
- [x] The previously proposed neural-v1 outcome fine-tuning was completed as neural-v2;
      its trained weights are retained by neural-v3/v5/v6. It is not a new pending proposal.
- [x] Independent v09 opportunity: audit all-cell instability-cost bypass candidates on
      saved threatened states, then implement one default-off proposal experiment.
      Completed as [rollout-risk-v1](rollout-risk-v1.md): 54.2% over 1000 paired
      games versus v09; NOT_PASSED, flag OFF and boss stage skipped.

## Indrill capacity-v1: supervised fitting diagnostic (2026-10-04)

- [x] Freeze the existing Q representation, whole-subset/ink likelihood, deterministic
      map-group split and fixed optimizer budgets; preserve original bots and weights.
      [Protocol](indrill-capacity-v1.md).
- [x] Validate all 293 teacher replays exactly and export 3,068 valid pre-turn observations.
      Independently verify raw bytes, full-map grouping, old40/later11 retention and exact
      quantiles: A64/16 maps, B2048/128 maps, holdout1020/51 maps; A is contained in B.
- [x] Verify input/label/decoder contracts, gradients, checkpoint lineage and resume rules.
      No cross-split exact-input overlap, input-label conflicts or sufficient structural
      tie groups found; these checks do not establish universal network capacity.
- [x] Complete all 2,000 Stage-A updates, batch16 / 32,000 sample visits. Final agreement:
      joint6/64, build15/64, ink26/64; mean joint NLL17.19242878 -> 7.49216108.
      FIT_NOT_ESTABLISHED against the final-only joint63/64 requirement. Stage B and
      holdout model scoring were not run; no new games, strength result or promotion.
      [Completed evidence](evidence/indrill-capacity-v1.json).
- [x] Complete the separate bounded `indrill-fit-one-v1` investigation below: first
      fixed A position, construction-only network and independent per-cell-logit control.
      Preserve the old fixed-budget failure; no seed search or continuation of A.

## Indrill fit-one-v1: fixed-position construction check (2026-10-04)

- [x] Predeclare first A tuple 905008481:0:0, unchanged subset loss/decoder, independent
      free logits and fresh network, build-only, exactly 2000 updates per arm.
      [Protocol](indrill-fit-one-v1.md); no selection by previous model errors.
- [x] Verify build-only gradients, frozen heads, Adam/masks and checkpoint integrity
      on synthetic inputs; freeze source/runtime/selected row before real gradients.
      57 sampled network derivatives, free-logit gradients/Adam and both-arm resume
      boundaries pass; freeze 90692459... fixes the first A row and original artifacts.
- [x] Complete both arms at exactly 2,000 updates/visits: both FINAL_SET_FIT, exact
      teacher set [75,178,179], 3 paint, precision/recall 1. Network NLL 14.39639247 ->
      0.002980866; free-logit final NLL 10.73031938. Both are exact at the scheduled
      500/1000/1500/2000 checks; no early success stop, old A continuation or new games.
- [x] Pass the final audit: both 2,001-checkpoint chains, exact initialization,
      finite/frozen state and every Adam step verified bit-exactly.
      [Final evidence](evidence/indrill-fit-one-v1.json). No holdout scoring, promotion,
      generalization, strength or universal capacity-ceiling claim.
- [x] Archive and commit the completed fitting diagnostic and audit.
      8,162 archive entries, 7,700,236 bytes; all payload hashes and 22 startup/metric
      metadata files verified. Remaining checkpoints are retained locally and pinned.
- [x] Predeclare a separate build-only diagnostic on the same 64 A positions versus
      the retained joint run, fixing the old seed, batch 16, 2,000 updates, sample order
      and exposure. The one-position test gave 2,000 visits versus about 500 per old A
      row, so it does not isolate ink/build interference. Continue with the separate
      build64-v1 diagnostic below; keep the old run closed and preserve its results.

## Indrill build64-v1: matched construction-only fitting (2026-10-04)

- [x] Predeclare the original 64 A observations, fresh original initialization,
      sampling seed/order and fixed 2,000 updates / batch 16 / 500 visits per row.
      Remove only the ink training objective; preserve old sources and weights.
      [Protocol](indrill-build64-v1.md).
- [x] Check batched build-only derivatives, frozen heads and persistence on synthetic
      inputs; bind original initialization/sampling proof and freeze before training.
      Synthetic checks pass, including epoch-boundary resume and incomplete metrics;
      freeze 04157ecc... binds the original 64 rows and all 2,001 old sampler states.
- [x] Complete all 2,000 updates / 32,000 sample visits and compare saved final rows:
      16/64 exact construction sets versus old 15/64; 3 gained, 2 lost, 13 retained
      exact and 46 inexact in both. Build NLL 13.65655966 -> 5.16299332, versus old
      final 5.56911593. BUILD_FIT_NOT_ESTABLISHED against the separate final-only
      63/64 screen; the net +1 is descriptive, not a strength or significance claim.
      [Evidence](evidence/indrill-build64-v1.json).
- [x] Audit saved optimizer/sampling/metric evidence, archive, document and commit.
      All 2,001 checkpoints and original sampler states pass; Adam steps are bit-exact.
      Compact archive: 4,119 files / 6,027,939 bytes, including all production metadata.
      No held-out scoring, extra updates, new games or strategy promotion.
- [x] Predeclare a separate fresh build-only budget diagnostic: same 64
      observations, seed and batch 16, with 8,000 updates and the unchanged first
      2,000-update sequence; decide once at final 63/64. Continue through the
      separate build64-long-v1 experiment below. Preserve this closed run.

## Indrill build64-long-v1: fixed 8,000-update budget test (2026-10-04)

- [x] Predeclare fresh original initialization, exact same 64 observations,
      optimizer/sampler/batch 16, exactly 8,000 updates and 2,000 visits per row.
      Compare to retained build64-v1 16/64; final-only memorization screen 63/64.
      [Protocol](indrill-build64-long-v1.md).
- [x] Verify the expanded counter guard, unchanged objective arithmetic and saved
      boundary/prefix checks on synthetic inputs; freeze before real gradients.
      Synthetic checks pass; freeze 2ad21f45... binds the original selected rows,
      completed build64-v1 evidence, runtime and unchanged training arithmetic.
- [x] Reproduce all first 2,001 states and five metric payloads exactly, then
      complete the fixed 8,000-update run without tuning or early success.
      Final 59/64 (92.1875%) versus 16/64, with 43 gained and none lost. Build NLL
      falls from 5.16299332 at update 2,000 to 0.58229369 at 8,000. Total 128,000 sample visits,
      exactly 2,000 per row. BUILD_FIT_NOT_ESTABLISHED against final-only 63/64.
      The budget increase substantially improves this fixed-panel fit; five sets
      remain incorrect, without proving convergence, capacity limits or strength.
      [Evidence](evidence/indrill-build64-long-v1.json).
- [x] Audit every saved optimizer/sampling/metric record, archive results,
      document the outcome and commit. No holdout scoring, new games or promotion.
      All 8,001 states and 8,000 Adam steps pass bit-exactly; 1,341 clipped updates.
      Exact prefix: 2,001 states / five metrics. Verified archive: 16,073 files /
      19,106,278 bytes; earlier verdicts and production weights remain unchanged.
- [x] Predeclare one separate fixed 12,000-update diagnostic, keeping
      the architecture and optimizer, final-only 63/64 and no automatic extensions.
      Continue through build64-12k-v1 below; preserve this closed 8,000-step run.
      All five residual-error NLLs still decrease at the last two saved checks,
      so a plateau or need for wider layers/new features has not been established.

## Indrill build64-12k-v1: fixed total 12,000-update diagnostic (2026-10-04)

- [x] Predeclare exact full-state continuation from audited long-v1 step 8,000
      in a separate run: 4,000 new updates, unchanged optimizer/architecture/data,
      total 192,000 visits / 3,000 per row. Final-only 63/64 versus parent 59/64.
      [Protocol](indrill-build64-12k-v1.md).
- [x] Verify unchanged mathematics, full-state restoration, sampler continuation,
      update guards and numeric checkpoint ordering across 9,999/10,000;
      freeze new source/proof/runtime/data and verified parent evidence.
      Synthetic checks pass; freeze c227dc9b... binds all 28,099 verified parent
      input pins, the exact full starting state and original selected rows.
- [x] Prove the exact starting state, inherit saved baseline metrics without
      rescoring, then complete steps 8,001..12,000 and the two fixed evaluations.
      Exactly 4,000 new updates / 12,000 total; final 64/64, five gained, none lost
      versus parent 59/64. NLL 0.58229369 -> 0.05434280; intermediate 10,000 result
      63/64 did not shorten the budget. BUILD_FIT_ESTABLISHED for construction on
      this panel, not joint actions or arena strength. No automatic Stage B.
      [Evidence](evidence/indrill-build64-12k-v1.json).
- [x] Audit all 4,001 new saved boundaries, archive results, record the outcome
      and commit. No automatic additional training, holdout scoring or new games.
      All 4,000 new Adam steps are bit-exact; 24 clipped updates. Exact parent fork,
      inherited baseline, 28,099 parent audit pins and 16,073 parent archive entries
      pass. All 158/158 target cells match. New archive verified: 8,083 files /
      11,938,938 bytes, retaining 8,013 production metadata records.
- [x] Predeclare a separate matched joint build+ink diagnostic on the original
      64 observations at 12,000 total updates, from its full original 2,000-step
      checkpoint; retain seeds, Adam and joint loss, final-only joint >=63/64.
      User accepted; continue in joint64-12k-v1 below. The old joint result cannot
      isolate joint-objective difficulty from budget, since build-only reached
      only 16/64 under that same budget; its later pass leaves ink learning open.

## Indrill joint64-12k-v1: matched full-action fitting (2026-10-04)

- [x] Predeclare full-state continuation of original joint Stage A from 2,000
      to 12,000 total updates, same 64 rows/16 maps, sampler, Adam and joint loss.
      Final-only joint >=63/64; compare saved build-only 12k results descriptively.
      [Protocol](indrill-joint64-12k-v1.md).
- [x] Verify unchanged joint mathematics, restoration and persistence on synthetic
      data; freeze all source/runtime/data/proof and verified parent evidence.
      Single synthetic proof PASS; freeze 37062d4b... verifies 6,322 original
      parent audit pins, exact selected rows and saved build-only comparison.
- [x] Prove exact parent fork, inherit step-2,000 metrics, execute exactly 10,000
      new updates and the five fixed evaluations; decide only at total 12,000.
      Final 63/64 joint, 64/64 build and 63/64 ink: JOINT_FIT_ESTABLISHED.
      Joint gains 57, loses none and retains all six parent successes; the one
      remaining error is an ink choice. Mean joint NLL 7.49216108 -> 0.32454612.
      Exactly 160,000 new visits / 192,000 total, or 3,000 total visits per row.
      Build-only at the same budget also fits 64/64, with build NLL 0.05434280
      versus joint training's 0.13778353. This is fixed-panel fitting evidence,
      not generalization or arena strength. Earlier finite-budget verdicts stand.
      [Evidence](evidence/indrill-joint64-12k-v1.json).
- [x] Audit all 10,001 new boundaries, archive, record results and commit.
      All 10,000 new Adam steps are bit-exact; 1,796 clipped updates. Exact fork,
      inherited baseline, 6,322 parent audit pins and 4,190 parent ZIP entries
      pass. All 158/158 construction cells match. Verified archive: 20,095 files /
      28,260,826 bytes. No automatic Stage B, held-out scoring, further fitting
      or new games.
- [ ] Suggested next, unstarted: define a separate fixed diagnostic with a larger
      training panel and map-separated holdout before RL or strength testing.
      Keep the original Stage B closed; this pass alone does not start another run.

## neural-v2: outcome fine-tuning from neural-v1 (2026-10-03)

- [x] Fix the single candidate hypothesis and training/evaluation protocol before results:
      retained imitation weights, terminal WDL, clipped actor updates plus teacher KL;
      8 x 192 paired training games, final checkpoint only (`neural-policy-v2.md`).
- [x] Implement separate candidate/collector/trainer and isolated packaging; preserve the
      four neural-v1 source files and selected v09 submission byte-for-byte.
- [x] Pass math, closure/sequence, initial action parity, checkpoint and numerical checks:
      396 Java tests, 22 Python tests, 3,704 bit-exact candidate scores; 292/292 CG replays.
- [x] Run all eight fixed rounds; independently verify data/weights/source pins and exclusions:
      1,536 games / 768 maps, final weights aba87337..., no overlap with probe/corpus.
- [x] Freeze final weights, engine/tests/parity/strict process panel, fresh practical-v4 vs v09:
      local645/1000 (64.5%) PASS; strict process1/8 failed (50.3003ms), boss not started.
- [x] Record actual branch/outcome and any required boss-cache refresh/live stage; no automatic
      promotion. Commit the completed candidate, checks and results including rejection.
- [x] Separate runtime follow-up: keep neural-v2 weights, interrupt incomplete feature passes
      safely and return completed legal actions; neural-v3 strict8/8 and fresh621/1000 local pass.

## neural-v3: deadline-aware retained neural policy (2026-10-03)

- [x] Predeclare the single deadline change, 2-ms internal reserve, unchanged v2 weights,
      fixed process panel and fresh practical-v4 seeds (`neural-policy-v3.md`).
- [x] Implement isolated features/bot; verify interruption recovery and unlimited parity.
- [x] Build and verify exact artifact, prior artifact identities, tests/engine and strict8/8:
      406 tests,292/292 replays,max later process response40.1253ms.
- [x] Complete one fresh practical-v4 evaluation versus v09:621/1000 (62.1%),zero errors,
      500 disjoint paired maps; record actual readiness and the subsequent boss result.
- [x] Commit the completed experiment, preserving original neural-v1/v2 and selected v09.
- [x] Continue via the user-supplied permanent puzzle IDE; preserve old contest rejection,
      pin owner replay access and actual Boss4 ID6767608, retain game907056523 as slot1 LOSS
      through an immutable metadata amendment with the same40slots/builds/exclusions.
- [x] Finish authorized fresh40 control +40 matched candidate boss games:15/40 vs11/40,
      PASS. All80 replays exact, zero technical failures;40 maps disjoint from16,221 prior
      maps. Combined v4 and readiness pass; candidate retained without promotion.

## neural-v3: arena follow-up (2026-10-04)

- [x] Record the user's arena submission and ongoing placement. The preceding IDE
      readback matches the exact tested artifact; arena agent/source/rank remain unverified.
- [x] Inspect latest40 new arena games:20W20L,40/40 exact engine/build,zero technical
      failures. Verified arena134/712 Silver;13/20 losses behind at turn20. Three
      apparent self-closures require simultaneous opponent disruption; no guaranteed
      recovery or matched v09 comparison established (`evidence/neural-v3-arena-20261004.json`).
- [x] Measure current-student sampled-versus-greedy disagreement on retained data:
      128 selectable decisions/head/round, 1,024 per head; build283/1024 (27.64%),
      sabotage715/1024 (69.82%). Pure argmax approximates deployed argmax+small noise;
      this is an offline diagnostic, not new strength evidence. Teacher-v1 agreement
      is a different metric (`evidence/neural-v3-exploration-gap.json`).
- [x] Candidate hypothesis: a fresh repetition of the outcome-training protocol with
      build-only sampling temperature0.5, keeping sabotage temperature1 and other
      training/runtime choices fixed. Align actor probabilities/gradients and parity
      with the actual collection distribution; predeclare before any new collection.
- [ ] Audit saved states for missed complete placement bundles and closing decisions
      made before construction. Use shared core; oracle opponent replies are diagnostics only.
- [x] Inspect early accepted paint by board size in the same40 arena games: on11 tall
      maps62.3% of nominal paint versus99.4% for opponents;2W9L. All first turns fill3.
      79/84 own37-51ms warnings coincide with partial paint, but replay lacks abort phase.
- [x] Probe the same fixed first20 states of all40 games with ample versus nominal CG
      budgets:800 identical action pairs, all spend3paint legally against WAIT, zero
      aborts, nominal max31.5294ms. CG underpaint was not reproduced locally; no new
      games or claim of matching CG CPU/RNG. Preparation is80.65% of time on tall maps.
      Evidence: `evidence/neural-v3-budget-probe-20261004.json`.
- [x] Predeclare exact feature-preparation optimization with frozen V3 weights and
      feature/action parity: profile repeated completionGain/connection updates and
      scratch-path allocations, then independently check actual CG timing. Keep a cheap
      construction fallback as a separate policy experiment if needed.

## neural-v6: exact resumable feature paths (2026-10-04)

- [x] Predeclare one implementation change, frozen V5 policy and V2 weights,
      selected v09 control, fixed diagnostics/process/pools (`neural-policy-v6.md`).
- [x] Add shared-core resumable feature paths and separate V6 bot integration;
      preserve tie-breaking, mask/reset behavior and public fresh-search costs.
- [x] Verify path/feature/action parity:452 Java tests,292 exact CG replays,
      previous artifact identities,800 actions and11,789,067 floats exact.
      Local play wall -22.73% overall/-30.82% tall maps; one diagnostic pass.
- [x] Freeze exact artifact; strict process8/8 PASS (max later36.2853ms) and fresh
      practical-v4 versus v09:643W357L0D/1000,64.30%,PASS_1000,zero errors.
- [x] Complete authorized40-game cached-boss series:20W20L versus cached11W29L,
      all40 exact, no technical failures. Combined PASS; IDE contains exact V6.
      Actual CG early paint96.0% overall/88.33% tall, with22 early wall warnings.
- [x] Record actual result, preserve evidence
      and commit without changing the selected strategy or arena submission.
- [x] Separate follow-up: inspect remaining CG tall-map under-spending and useful
      construction fallbacks using frozen V6. Do not infer missed legal moves or
      server timeouts solely from paint totals/self-reported wall warnings.

## Neural under-spending follow-up before returning to Indrill (2026-10-04)

User explicitly chose to finish the remaining large-map under-spending work first,
then return to Indrill. Preserve the completed V6 experiment and its frozen artifact.

- [x] Audit all first20 states of the existing40 V6 boss games; replay the actual
      accepted own prefix before measuring affordable candidates and a cheap fill.
      Separate legal availability, immediate income and unknown future strength.
      All70 underpaint turns have legal/V6-eligible moves;96 unused paint. Exploratory
      fill spends all96 but has2 negative immediate net-income cases; no win inference.
- [x] Use that diagnostic to predeclare one new candidate, with unchanged V2 weights,
      complete-choice preservation and bounded runtime; do not start a tuning series.
      V7 adds timeout-only stable own-network extension; see neural-policy-v7.md.
- [x] Verify correctness, ample-time parity where the fallback is inactive, injected
      cutoff behavior and exact submission/process checks:466 tests,800 matching
      actions,96/96 diagnostic fill paint,292+40 exact replays,strict8/8 PASS.
- [x] Complete the fixed practical-v4 local strength gate against selected v09:
      653W347L0D/1000,65.30%,PASS_1000,two workers,500 fresh maps,zero errors;
      all prospective map/source/runtime/artifact pins independently verified.
- [x] Complete the authorized fresh40-control +40-candidate boss regression:
      V7 14W26L versus v09 15W25L, combined NOT_PASSED; all80 replays exact,
      all40 actual maps/seats match the prospective fresh pool, zero technical failures
      or retries. Exact V7 remains in IDE; default v09 and arena are unchanged.
- [x] Record and retain the rejected experiment with its completed evidence and
      separate immutable live archive (207 files/657,157 bytes); commit this result.
      Early paint 2276/2400 overall,1321/1440 on 24 tall maps does not demonstrate
      resolved under-spending or superiority over V6's different historical maps.
      See evidence/neural-v7-final.json; retain unfinished ideas as future work.
- [x] Return to Indrill through the separate `indrill-capacity-v1` diagnostic above:
      Stage A completed with FIT_NOT_ESTABLISHED; Stage B and holdout scoring skipped.
      Further fitting investigation remains separately bounded future work.

## neural-v5: exact hypothetical completion income (2026-10-04)

- [x] Profile frozen V3 on the fixed800 arena states; all actions match previous
      ample-time decisions. Direct Dijkstra dominates tall-map time58.89%; route
      completion income is16.15%, with95.0% of play allocations in its connections.
- [x] Predeclare bounded exact-income optimization only, unchanged V3 weights,
      separate core helper and preserved prior submissions (`neural-policy-v5.md`).
- [x] Verify core/feature/action parity: 432 tests, 292 exact engine replays, 800 actions
      and 11,789,067 feature values bit exact. Allocations -88.30% overall / -93.46%
      on tall maps; wall time -1.85% / -0.77%, no clear speedup from one local pass.
- [x] Freeze exact artifact and complete strict8-game panel: FAILED 1/8, map903510176
      P0turn11, response absent by50ms; send34.6403ms/consumer66.3022ms, no retry.
- [x] Run fresh practical-v4 versus selected v09, seeds3600000/3700000/3800000;
      two workers, 610W389L1D/1000 (61.05%), PASS_1000, all500maps fresh, zero errors.
      Process failure blocks boss games; combined LOCAL_PASS_BUT_PROCESS_FAILED.
- [x] Record actual verdict, retain compact evidence and commit; no arena promotion.
- [x] Separate next proposal implemented as V6: reuse four Dijkstra searches per source town/mask within
      prepare, preserving (cost,cell) order and the popped-but-unexpanded target.
      Invalidate on every prepare/source change; preserve public fresh-path API.

## neural-v4: construction temperature 0.5 (2026-10-04)

- [x] Predeclare one temperature change, original v1 initialization, same eight-round
      training budget and frozen v3 runtime (`neural-policy-v4.md`); preserve v09 default.
- [x] Verify separate collector/trainer:414 Java+31 Python tests, unchanged teacher KL,
      4,168 bit-exact Java/Python probe scores. All1,271 prospective maps disjoint from
      16,379 previous maps; exact v3 export retained and292/292 archived replays match.
- [x] Complete fixed1,536-game/768-map training; independent132-source, lineage/RNG,
      numeric and map audits PASS. Export only final round8 weights4cf8303b...d499.
      Build sampled-maximum agreement587/1024 (57.3%); mechanism only, not strength.
- [x] Verify final artifact, unchanged v3,414 tests,292/292 engine replays and4,168
      bit-exact final-model scores. Strict panel COMPLETE but FAILED1/8: map904148181,
      P1turn38,response52.0318ms versus50ms; no retry. Readiness remains NOT_PASSED.
- [x] Complete fixed practical-v4 vs neural-v3:149W150L1D/300 (49.8333%),150 fresh maps,
      zero errors, STOP_FUTILITY_300. No continuation or boss; candidate REJECTED.
- [x] Retain compact artifacts and commit completed experiment and arena audit;
      no automatic arena upload or promotion.
