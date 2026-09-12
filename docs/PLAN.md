# Plan

Roadmap with status. History and reasoning live in `DEVLOG.md`, per-version numbers in `BOTS.md`.
Update the checkboxes when a step is finished; add new steps at the bottom of the open phase.

## Phase 1 — infrastructure (done 2026-09-13)

- [x] Gradle/Java 17 skeleton, package layout, `AGENTS.md`, Cursor rules
- [x] `btk.core` rules engine shared by bot, referee and tools; JUnit tests
- [x] CG replay download (`cgFetch`) and engine validation against them (`cgValidate`) — 107/107
- [x] Full rules inferred from Gold replays (ink at 4 disrupts, game end) — `docs/rules/inferred_gold.md`
- [x] Referee, procedural + real maps, parallel benchmark with Wilson CI, side swap
- [x] Process-level submission check (`procCheck`): protocol, first-turn warm-up, 50 ms turns
- [x] Single-file merger (`submit`) → `build/submit/Player.java`
- [x] Replay visualizer for local and CG-derived replays
- [x] Study tool for what top players do (`cgStudy`)
- [x] Bots v00 (autoplace), v01 (greedy), v02 (eval + DISRUPT), v03 (vulnerability eval, backups)

## Phase 2 — leagues (open)

- [ ] Paste `Player.java` (v03) into the CG IDE, submit, leave Wood — **manual step**
- [ ] Capture the Bronze statement verbatim into `docs/rules/bronze.md`; reconcile with
      `inferred_gold.md` (POIs, AUTOBUILD, invalid-command handling, disrupt on town regions)
- [ ] Add rules that only appear in the statement to `btk.core` + tests; re-run `cgValidate`
- [ ] Re-fetch replays of the current top 20 every few days (`cgFetch`), keep `cgValidate` green

## Phase 3 — strength (open; one feature per version, bench-gated)

Gaps measured in `BOTS.md` → "What top Gold players do". Candidate order:

- [ ] Reference opponent that reproduces the Gold meta (aggressive disrupts, ~50-turn games) so
      self-play benches transfer to the arena
- [ ] No idle turns: useful placements when nothing raises the value now (extend lines,
      pre-build in town/shared regions)
- [ ] Disrupt every turn: pre-emptive targets on the opponent's likely corridors; closing the map
      when ahead / keeping it open when behind
- [ ] Short lookahead: 2-3 turn plan search over (placements, disrupt) with the engine
- [ ] Parameter tuning only after the above, with >= 1000-game benches
- [ ] Check timing after every hot-path change (`procCheck`), keep first turn < 800 ms

## Someday / maybe

- JIT warm-up inside bench threads to remove spurious "slow" counts in the arena
- Remove `tools/CgInkStudy.java` once `inferred_gold.md` is confirmed by the statement
- Automated paste channel into the CG IDE (Cursor browser blocks file:// and localhost fetch)
