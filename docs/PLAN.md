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
- [ ] Guarantee time for sabotage on large maps: reserve a phase budget / compute a fallback
      before route search; verify on captured failure states and varied opponents before promotion.
- [ ] Check actual partial placements against score-difference regressions, not only the value
      of a fully completed candidate route. Do not attribute every bad build to idle extension.
- [ ] Add map-size-stratified reports and independent held-out maps; broaden the opponent pool.

## Someday / maybe

- JIT warm-up inside bench threads to remove spurious "slow" counts in the arena
- Remove the now-redundant `tools/CgInkStudy.java` when doing a tooling cleanup
- Automated paste channel into the CG IDE (Cursor browser blocks file:// and localhost fetch)
