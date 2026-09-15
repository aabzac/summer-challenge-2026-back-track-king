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
3. **One change, one version, one bench.** A strategy change is done only when `gradlew bench`
   against the previous version (>= 200 games, sides swapped) is recorded in `docs/BOTS.md`.
   Gate to promote: A wins >= 55% and the 95% CI excludes 50%. Also spot-check against an older
   version to catch non-transitive "improvements".
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
