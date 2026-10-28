# Back Track King bot — working agreement

CodinGame Summer Challenge 2026 bot in Java 17. Goal: climb the arena as high as possible.
Read `docs/STATUS.md` first (one page of current state), `README.md` for commands, `docs/PLAN.md` for the roadmap and open steps, `docs/BOTS.md` for
the version history, `docs/DEVLOG.md` for what has been tried. Tick `docs/PLAN.md` when a step is
finished.

## Non-negotiables

1. **Game rules live only in `btk.core`.** Bots, referee and tools consume it; nobody re-implements
   scoring or pathing elsewhere. Rules changes require a JUnit test in `src/test/java/btk/core`.
2. **Engine fidelity before strategy.** After any `btk.core` change run `gradlew cgValidate`. Local
   benches are only trusted while the engine reproduces downloaded CG games. When the local bench
   and the CG arena disagree, run `cgValidate` first, then form hypotheses.
3. **One change, one version, one bench.** Record every experiment in `docs/BOTS.md`.
   Practical strength gate **v5 (adopted 2026-10-21, `docs/benchmarking-v5.md`; step (c) amended 2026-10-27)**,
   three required steps, a failure at any step stops the candidate and keeps its evidence:
   (a) **proxy screen** — 300 paired games on fresh maps, candidate vs the validated boss-like
   screening opponent (`current?USE_BOSS_SABOTAGE=1` while `docs/boss-proxy-v1.md` records it as
   valid), with the selected control on the same maps/seeds/seats in the same run; continue only
   at candidate ≥ control + 3 pp; **(a′) replication (amended 2026-10-26)** — an arm that passes (a) is
   re-screened on a fresh pool against the same proxy with `current` as the same-map control, and continues only
   at a positive replication delta and a pooled delta ≥ +3 pp (`tools/boss_proxy/run_replication.sh`,
   `tools/boss_proxy/pooled_delta.py`). For a candidate that is itself the screening opponent, or when
   no proxy is valid, replace (a)+(b) by the v4 pooled 1000-game run vs the selected version
   (300/+200/+500, futility below 50 % at 300/500, pass ≥ 55 %).
   (b) **non-inferiority** — 300 paired games vs the selected version; continue only at ≥ 48 %.
   (c) **live filter + arena (amended 2026-10-27, `docs/arena-uploads.md`)** — one 40-game live boss session on
   the cached control's maps/seeds/seats with a same-session control; continue at a paired delta ≥ 0. Then the
   candidate goes to the arena: fetch the live entry before every upload (`python tools/arena_uploads.py fetch`;
   CG lists the live agent's games only), upload the candidate and run `... watch` through its placement (~120
   games; a league promotion replaces the agent at the end), re-upload the same artifact and watch again; roll back
   to the selected version when the candidate's mean placement score is below the selected version's uploads in
   the same league by more than M (2 × the upload-to-upload SD, ≈ 1.0 from v13's Silver and Gold pairs) and its
   performance rating is lower by more than 2 SE; otherwise promote. The former 80/120-game live
   confirmation is historical.
   New heuristic bots (not flags on `current`) get a predeclared tuning budget on development
   maps before step (a). Draws = half. Confidence intervals are diagnostics, not a gate. Engine,
   errors and exact-artifact process checks remain mandatory. Keep the timed strength bench at
   two workers. The v4 policy, its tools (`run_practical_v4.py`, `practical_gate_v4.py`), the
   cached-boss mechanism of `docs/boss-cache-policy-v3.md` and all historical verdicts are
   preserved as recorded. Re-validate the proxy whenever it or the live boss changes.
4. **Parameters, not file copies.** All weights/flags are fields of `btk.bot.Params`; ablations are
   run as `current?KEY=value` bot specs. Never edit `btk.bot.vNN` packages — they are frozen
   reference opponents. New version = copy `bot/current` to `bot/vNN`, rename classes, register in
   `BotFactory`, add a row to `docs/BOTS.md`.
5. **Hot path is allocation-free.** Search/eval code uses primitive arrays and the buffers in
   `Pathing`; no streams, lambdas, boxing or collections per node. Turn budget is 50 ms including
   input parsing; keep `CGRunner.MARGIN_MS`.
6. **Submission = `gradlew submit` + `gradlew procCheck`.** Never paste anything else into CG.
   Only `btk.core`, `btk.bot.{Bot,Params,Timer,MainBot}`, `btk.bot.current` and `btk.submit.CGRunner`
   ship (see `FileMerger`); `-PsubmitVersion=neural-v6` ships its `btk.bot.neural` classes instead of
   `bot/current`. Referee/runner/tools must not be referenced from those packages.
   neural-v6's weights (`btk.bot.neural.NeuralV2Weights`) are a frozen generated file; its training scripts left
   with the closed neural line (repo-cleanup tier 3, 2026-10-27; in git history). Never hand-edit them.
7. **Write it down.** Every experiment (also failures) gets a DEVLOG entry: hypothesis, bench
   result, decision. Statement text of each league goes to `docs/rules/<league>.md` verbatim.
8. **Commit completed experiments by default.** After verification and recording the results,
   commit the experiment and its documentation without asking again, including rejected or
   inconclusive experiments. Respect a later request not to commit; exclude unrelated changes.
   This authorizes local commits, not pushes, arena uploads or strategy promotion.
   **What an experiment commits (2026-10-27, repo-cleanup):** the protocol doc, the DEVLOG/BOTS/PLAN/STATUS
   updates, the code and tests, and the evidence JSON/TXT under `docs/evidence/` (screen cells, live-session
   results: `pooled_delta.py` and the replays checks read them). Never commit archives or binary exports
   (`*.zip`, `*.tar.gz`, `*.csv.gz` are gitignored under `docs/evidence/`): keep them local and record their
   sha256 in a JSON manifest when a result depends on them. A new file over 1 MB needs a reason in its DEVLOG entry.
   DEVLOG/PLAN/BOTS stay short: closed periods move verbatim to `docs/archive/`.
9. **Tests follow the code they test.** Keep tests for the selected version, the screening proxy and
   neural-v6, the engine, referee, runner and packaging, and the frozen-version pins. A rejected or parked
   experiment leaves `current` together with its tests (the prune-v1/v2 pattern; the frozen copy that still
   carries it remains runnable). A red test is fixed or removed, never left as "expected".

## Layout

```
src/main/java/btk/core      GameState, Rules, Pathing, Protocol, Action   (ships to CG)
src/main/java/btk/bot       Bot, Params, Timer, MainBot, BotFactory; current/ (ships), vNN/ (frozen)
src/main/java/btk/bot/neural  neural-v6, the screening opponent (features, MLP, generated weights; ships only for neural-v6)
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
- `python tools/arena_uploads.py fetch` before every upload, `... watch` through each placement; `... compare` — our uploads by
  version under `replays/arena/<label>/upload-N/` (`docs/arena-uploads.md`)
- `gradlew cgValidate`; `gradlew cgRender "-Ppath=replays/cg/<gameId>.json"` for visualizer JSON
- `gradlew cgStudy "-Pbot=current"` — what winners do in the replays + agreement of our bot with them
- visualizer: `python -m http.server 7799` then `http://127.0.0.1:7799/visualizer/index.html?file=/replays/local/<file>.json`
