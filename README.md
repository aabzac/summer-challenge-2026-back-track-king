# Back Track King — CodinGame Summer Challenge 2026 bot

Java 17 / Gradle. One rules engine (`btk.core`) shared by the bot, the local referee and the CG
replay validator. See `AGENTS.md` for the working agreement and `docs/` for rules, version history
and dev log.

## Quick start

```bash
gradlew test                                   # rules unit tests
gradlew run -Pbot0=current -Pbot1=v01 -Pseed=3  # one game -> replays/local/*.json
gradlew bench -Pbot1=current -Pbot2=v01 -Pgames=300 -Pmaps=replays/cg
gradlew submit                                 # -> build/submit/Player.java (compile-checked)
gradlew procCheck -Pbot=v01 -Pgames=4          # Player.java as external process, CG protocol + timing
gradlew cgFetch -Ptop=20 -Plimit=10            # download top players' replays -> replays/cg/
gradlew cgValidate                             # replay CG games through our engine, report divergences
```

On PowerShell quote properties containing dots or `?`: `gradlew cgValidate "-Ppath=replays/cg/903184412.json"`,
`gradlew bench "-Pbot1=current?NOISE=0.2,USE_STEAL=0" -Pbot2=v02`. Use `,` (not `&`) between params —
cmd.exe swallows `&`. Bench two deterministic clones with different `SEED`/`NOISE`, otherwise they
mirror each other and every game is a draw.

## Pieces

| piece | what it does |
|---|---|
| `btk.core` | `GameState` (primitive arrays), `Rules` (validation, simultaneous resolution, connections, scoring), `Pathing` (BFS shortest path with N/E/S/W tie-break, Dijkstra by paint cost), `Protocol` (fast CG IO), `Action` |
| `btk.bot` | `Bot` interface, `Params` (all tunables, overridable as `current?KEY=v&K2=v`), `Timer`, `MainBot` (what ships), `BotFactory`, `current/` working strategy, `vNN/` frozen versions |
| `btk.submit` | `CGRunner` (CG entry point: fast parsing, JIT warm-up on turn 1, connection cross-check), `FileMerger` (single `Player.java`) |
| `btk.referee` | `Referee` (in-process games), `MapGenerator` (procedural, approximate), `Replay` (visualizer JSON) |
| `btk.runner` | `LocalRunner`, `BenchmarkRunner` (parallel, side swap, Wilson CI), `Maps` (procedural or real CG maps), `ProcessBot`/`ProcessCheck` |
| `btk.tools` | `CgFetch` (leaderboard -> battles -> gameResult), `CgReplay` (parse + rebuild map), `CgValidate` (engine vs CG events) |
| `visualizer/index.html` | drag & drop a replay JSON; paths, ownership per connection, hover info |

## Visualizer

```bash
python -m http.server 7799
# http://127.0.0.1:7799/visualizer/index.html?file=/replays/local/<game>.json
```

`gradlew cgValidate "-Pshow=<gameId>"` also writes `replays/cg-local/<gameId>.json` — the CG game
as replayed by our engine — which the visualizer opens the same way.

## Submitting

1. `gradlew test && gradlew bench ...` (gate in `AGENTS.md`)
2. `gradlew submit && gradlew procCheck`
3. Paste `build/submit/Player.java` into the CG IDE (language Java), PLAY MY CODE, then SUBMIT.
4. Record the version and CG rank in `docs/BOTS.md`.
