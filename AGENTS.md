# Back Track King bot — working agreement

CodinGame Summer Challenge 2026 bot in Java 17. Goal: climb the arena as high as possible.
Read `README.md` for commands, `docs/PLAN.md` for the roadmap and open steps, `docs/BOTS.md` for
the version history, `docs/DEVLOG.md` for what has been tried. Tick `docs/PLAN.md` when a step is
finished.

## Non-negotiables

1. **Game rules live only in `btk.core`.** Bots, referee and tools consume it; nobody re-implements
   scoring or pathing elsewhere. Rules changes require a JUnit test in `src/test/java/btk/core`.
2. **Engine fidelity before strategy.** After any `btk.core` change run `gradlew cgValidate`. Local
   benches are only trusted while the engine reproduces downloaded CG games. When the local bench
   and the CG arena disagree, run `cgValidate` first, then form hypotheses.
3. **One change, one version, one bench.** Record every experiment in `docs/BOTS.md`.
   Practical strength gate (v3): candidate vs selected control, 300 paired games; >=60% passes,
   <=52% stops, only >52% and <60% continues to separate 1000 fresh games at >=55%.
   Also play exactly 40 NEW candidate games against the live boss on the saved control's
   40 seeds/maps/seats; reuse its individually verified replay outcomes, not a historical rate.
   Candidate score >= cached control score passes (ties pass); wins=1, draws=0.5, losses=0.
   This replaces the mandatory older/style-opponent 300/1000 stages. Boss calls stay sequential;
   after a qualifying 300 screen, the boss batch may run alongside the required local 1000.
   Complete both started stages; a local failure cannot be rescued by boss results.
   Fixed complete stages, no tuning/retries/pooling; stop promotion evaluation on direct failure.
   Confidence intervals are diagnostics, not a gate or an overall 95% guarantee. Follow
   `docs/benchmarking.md` and `docs/boss-cache-policy-v3.md`; engine, errors and exact-artifact
   process checks remain mandatory. Reused boss maps are a practical regression pool, not fresh
   confirmation; check cache validity and refresh after outcome-guided development cycles.
   Apply v3 prospectively; preserve historical policies/verdicts and frozen v2 tools.
4. **Parameters, not file copies.** All weights/flags are fields of `btk.bot.Params`; ablations are
   run as `current?KEY=value` bot specs. Never edit `btk.bot.vNN` packages — they are frozen
   reference opponents. New version = copy `bot/current` to `bot/vNN`, rename classes, register in
   `BotFactory`, add a row to `docs/BOTS.md`.
5. **Hot path is allocation-free.** Search/eval code uses primitive arrays and the buffers in
   `Pathing`; no streams, lambdas, boxing or collections per node. Turn budget is 50 ms including
   input parsing; keep `CGRunner.MARGIN_MS`.
6. **Submission = `gradlew submit` + `gradlew procCheck`.** Never paste anything else into CG.
   Only `btk.core`, `btk.bot.{Bot,Params,Timer,MainBot}`, `btk.bot.current` and `btk.submit.CGRunner`
   ship (see `FileMerger`). Referee/runner/tools must not be referenced from those packages.
7. **Write it down.** Every experiment (also failures) gets a DEVLOG entry: hypothesis, bench
   result, decision. Statement text of each league goes to `docs/rules/<league>.md` verbatim.
8. **Commit completed experiments by default.** After verification and recording the results,
   commit the experiment and its documentation without asking again, including rejected or
   inconclusive experiments. Respect a later request not to commit; exclude unrelated changes.
   This authorizes local commits, not pushes, arena uploads or strategy promotion.

## Layout

```
src/main/java/btk/core      GameState, Rules, Pathing, Protocol, Action   (ships to CG)
src/main/java/btk/bot       Bot, Params, Timer, MainBot, BotFactory; current/ (ships), vNN/ (frozen)
src/main/java/btk/submit    CGRunner (ships), FileMerger
src/main/java/btk/referee   Referee, MapGenerator, Replay (JSON for visualizer)
src/main/java/btk/runner    LocalRunner, BenchmarkRunner, Maps, ProcessBot, ProcessCheck
src/main/java/btk/tools     CgFetch, CgReplay, CgValidate, CgRender, CgPolicyStudy
visualizer/index.html       replay viewer (local + CG-derived replays)
replays/local, replays/cg   gitignored
docs/                       rules/, PLAN.md, BOTS.md, DEVLOG.md, replay_format.md
```

## Commands

- `gradlew test` — unit tests
- `gradlew run -Pbot0=current -Pbot1=v01 -Pseed=5 [-Pmap=replays/cg]`
- `gradlew bench -Pbot1=current -Pbot2=v01 -Pgames=300 -Pmaps=replays/cg`
- `gradlew submit` then `gradlew procCheck -Pbot=v01 -Pgames=4`
- `gradlew cgFetch -Ptop=20 -Plimit=10`, `gradlew cgFetch -Pplayer=<pseudo>`
- `gradlew cgValidate`; `gradlew cgRender "-Ppath=replays/cg/<gameId>.json"` for visualizer JSON
- `gradlew cgStudy "-Pbot=current"` — what winners do in the replays + agreement of our bot with them
- visualizer: `python -m http.server 7799` then `http://127.0.0.1:7799/visualizer/index.html?file=/replays/local/<file>.json`
