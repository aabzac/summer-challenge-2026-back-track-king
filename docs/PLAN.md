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
      currently around rank 350/645, with Gold not yet open
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

## Phase 3 — strength (open; one feature per version, bench-gated)

Gaps measured in `BOTS.md` → "What top Silver players do". Candidate order:

- [ ] Reference opponent that reproduces the top Silver meta (aggressive disrupts, ~50-turn games) so
      self-play benches transfer to the arena
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
- [ ] Revisit warm-up scheduling: generic 5-ms warm-up calls are below v05's 6-ms reserve,
      so they skip route search (confirmed: zero route-search calls in four profiled cases).
      Next independent experiment: warm routes explicitly while preserving RNG/state semantics;
      verify startup headroom and later turns separately. Historical process passes are not a
      guarantee: a recent first response took 959.35 ms, above the 800-ms headroom target.
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
- [ ] Add map-size-stratified reports and independent held-out maps; broaden the opponent pool.
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

## Someday / maybe

- JIT warm-up inside bench threads to remove spurious "slow" counts in the arena
- Remove the now-redundant `tools/CgInkStudy.java` when doing a tooling cleanup
- Automated paste channel into the CG IDE (Cursor browser blocks file:// and localhost fetch)
