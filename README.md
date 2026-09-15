# Back Track King — CodinGame Summer Challenge 2026 bot

Java 17 / Gradle. One rules engine (`btk.core`) shared by the bot, the local referee and the CG
replay validator. See `AGENTS.md` for the working agreement and `docs/` for rules, version history
and dev log.

## Quick start

```bash
gradlew test                                   # rules unit tests
gradlew run -Pbot0=current -Pbot1=v01 -Pseed=3  # one game -> replays/local/*.json
gradlew bench -Pbot1=current -Pbot2=v01 -Pgames=300 -Pmaps=replays/cg
gradlew benchSuite -Pbot=current               # matched local opponent panel on development-v1
gradlew botCatalog                            # names, categories and experiment status
gradlew botCatalog -Pbot=exp-income-guard      # pinned recipe, implementation identity and parameters
gradlew submit                                 # -> build/submit/Player.java (compile-checked)
gradlew submit -PsubmitVersion=v05              # restore frozen v05 source + snapshot, not current experiments
gradlew procCheck -Pbot=v01 -Pgames=4          # Player.java as external process, CG protocol + timing
gradlew cgFetch -Ptop=20 -Plimit=10            # download top players' replays -> replays/cg/
gradlew cgFetch -Pgame=903337834               # download one known arena/IDE game id
gradlew cgValidate                             # validate our engine against the arena replay corpus
gradlew cgRender -Ppath=replays/cg/903337834.json # validated replay -> rendered/cg/arena
gradlew cgMapValidate                          # regenerate CG maps from refereeInput seeds, compare exactly
gradlew cgAnalyze -Pagent=6735801               # validated games of one arena entry -> build/analysis/
gradlew cgForecast -Pagent=6736850              # offline adaptive sabotage prediction, no strategy changes
gradlew cgTiming                               # phase timing on two captured large-map replay trajectories
gradlew cgDisrupt                              # full sabotage valuation vs timed search on captured losses
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
| `btk.referee` | `Referee` (in-process games), `MapGenerator`/`GridMaker` (authentic CG maps by seed), `Replay` (visualizer JSON) |
| `btk.runner` | `LocalRunner`, `BenchmarkRunner` (paired maps, confidence grouped by map, JSON reports), `Maps` (authentic generation or replay maps), `ProcessBot`/`ProcessCheck` |
| `btk.tools` | `CgFetch` (leaderboard -> battles -> gameResult), `CgReplay` (parse + rebuild map), `CgValidate` (engine vs CG events), `CgRender` (validated visualizer export), `CgMapValidate` (generated maps vs CG viewer data) |
| `visualizer/index.html` | drag & drop a replay JSON; paths, ownership per connection, hover info |

## Reproducible benchmarks

Bots have stable command ids and descriptive labels: `v03 - Risk Aware`, `v04 - Network Extension`,
`v05 - Disrupt Reserve`. Six retained, **not promoted** experiments can also be selected by name:
`exp-income-guard`, `exp-build-direction`, `exp-early-disrupt`, `exp-reply-rerank`,
`exp-interruptible-vuln`, `exp-disrupt-delay065`. For example:

```powershell
.\gradlew.bat bench "-Pbot1=exp-income-guard" "-Pbot2=v05" "-Ppool=bench/pools/development-v1.json"
```

Named experiments pin complete parameters and verify their compiled engine snapshot; they do not
silently follow future `current` changes. They are optional local opponents, not additions to the
default panel or allowed `submitVersion` values. See [the bot catalog](docs/bot_catalog.md) for
historical evidence, limitations and how to retain/revise an experiment.

`bench` requires an even number of games: 200 games means 100 map pairs, each played with A as
P0 and P1. Reference bots `v01`–`v05` load fixed parameter snapshots; explicit query overrides
are applied afterward. `current` uses the live `Params` defaults (currently v05, 6-ms sabotage
reserve). `current?DISRUPT_RESERVE_MS=0` disables the reserve for an ablation.

```bash
gradlew bench -Pbot1=current -Pbot2=v03 -Pgames=200 -Pthreads=2 -Pseed=1000 "-Pout=build/bench/current-v03.json"
```

Every run saves a JSON report (by default under `build/bench/`) containing full resolved
parameters, seeds, map hashes, both games in each pair and timing. `bench`, `run` and `cgStudy`
use nominal CG search budgets (486 ms first / 36 ms later); `-Ptiming=legacy` restores the old
1000/50-ms local allowance. The profile is saved in each bench report. Confidence is grouped by
distinct map; replaying the same map does not manufacture independent evidence. The old
per-game Wilson interval is retained as a diagnostic, not as the promotion gate. See
[docs/benchmarking.md](docs/benchmarking.md) for details and the version-freezing procedure.

Explicit empty or malformed replay pools fail instead of silently switching to generated maps.
`cgValidate` also exits with an error for divergences, malformed input or an empty replay corpus.

For the named development pool and a behaviorally different reference:

```powershell
.\gradlew.bat bench "-Pbot1=current" "-Pbot2=pressure-v1" "-Ppool=bench/pools/development-v1.json"
.\gradlew.bat benchSuite "-Pbot=v05" "-Pout=build/bench/my-v05-panel"
```

`pressure-v1` is a local-only sabotage-first reference, not a promoted submission. The suite plays
the same paired maps against v05, pressure-v1, v03 and aggressive v02, skipping an exact self-spec.
Each opponent has its own report; no combined win rate is treated as a promotion decision.
Reports include height slices, accepted action frequencies and early closures; the default is
two worker threads. Choose a fresh suite output directory to preserve earlier evidence.

`bench/pools/confirmation-v1.json` reserves a different 1000-game pool. Use it only for a fixed
candidate, with `-PexcludeReports=<development report or suite index.json>`: overlapping map hashes
are rejected before any game starts. See [benchmarking.md](docs/benchmarking.md) for limitations
and confirmation commands. Do not tune on this reserved pool.

For arena diagnosis, fetch the player's latest games, then analyze the agent id printed by the
fetcher (keeps different arena entries separate):

```bash
gradlew cgFetch -Pplayer=aabzac -Plimit=50
gradlew cgAnalyze -Pagent=6735801
```

`cgAnalyze` validates each selected arena replay before reporting accepted placements/paint,
disrupts, first income and per-turn diagnostics. All per-player arrays in its JSON are indexed
by P0/P1; each game records `me`. Turns are zero-based. Counterfactuals change only the current
simultaneous turn and then reset to the recorded state; their score deltas are not projected
match outcomes and must not be summed as lost final points. Failed-server turns have no
counterfactual recommendations. Boss replays are excluded.

## Authentic maps by seed

`run` and `bench` use the ported CodinGame generator by default. To play on the exact map from
CG game 903182120, use its signed `refereeInput` seed without a `-Pmap` override:

```bash
gradlew run -Pbot0=current -Pbot1=v03 -Pseed=-176203007668178900
gradlew cgMapValidate "-Ppath=replays/cg/903182120.json"
```

`-Pmap` / `-Pmaps` still select maps directly from downloaded replays. Generator provenance,
seed constraints and regression fixtures are documented in [docs/map_generator.md](docs/map_generator.md).

## Visualizer

```bash
python -m http.server 7799
# http://127.0.0.1:7799/visualizer/index.html?file=/replays/local/<game>.json
```

Canonical CG files are validation inputs, not visualizer documents. Render one only after exact
engine validation:

```bash
gradlew cgRender "-Ppath=replays/cg/903337834.json"
# http://127.0.0.1:7799/visualizer/index.html?file=/replays/rendered/cg/arena/903337834.json
```

Boss games are detected from agent id `-2` and written under `replays/rendered/cg/bot/` instead.
`cgValidate` only validates and never creates files.

### Capturing an IDE/boss game

`tools/codingame_replay_export.user.js` intercepts the browser's `TestSession/play` response,
extracts its `gameId`, and downloads the canonical replay through `gameResult/findByGameId` without
reconstructing anything from stderr. Start `python tools/save_server.py` before pressing
**PLAY MY CODE**; the replay is written directly to `replays/cg/bot/<gameId>.json`, keeping it
separate from arena games. The original response is retained under `replays/cg/bot/raw/` only when
the canonical game-result lookup fails. Without the local server the userscript falls back to a
browser download. Run `gradlew cgValidate "-Ppath=replays/cg/bot"` to validate the boss corpus, or
`gradlew cgRender "-Ppath=replays/cg/bot/<gameId>.json"` to validate one game and produce its
visualizer document under `replays/rendered/cg/bot/`.

## Submitting

The arena source limit is **100,000 characters** (user-confirmed). `submit` checks the UTF-16
length conservatively before writing, prints UTF-8 bytes separately, and compile-checks the
result. `build/submit/manifest.json` records the selected strategy, full resolved parameters,
character count and SHA-256 of the exact generated source.

`submit` still defaults to `current`. To reproduce the confirmed v05 without experimental source,
use `gradlew submit -PsubmitVersion=v05`: the merger reads frozen v05, generates it as `EvalBot`,
and embeds its snapshot in the generated `MainBot`. Frozen files and working `current` stay
unchanged. This restores the strategy, not the byte-for-byte historical artifact (shared core,
runtime and packaging are current). Other frozen versions are not yet supported by this option.
For an explicitly temporary experiment, `submit "-PsubmitVersion=current?INTERRUPT_VULN_EVAL=1"`
embeds the full resolved candidate parameters and records them in the manifest. This overwrites
the submission artifact; restore `submit -PsubmitVersion=v05` and rerun `procCheck` afterward.
Always run `procCheck` after building; `-Pbot` selects its OPPONENT, not the submitted strategy.
Use `-Pmap=replays/cg/<id>.json -Pgames=2` to check both sides of a particular difficult map.

Submission `MainBot` now warms a disposable instance of the selected strategy with 36-ms calls
inside the existing 500-ms warmup allowance. This reaches route search without consuming the
live bot's RNG or mutating its state. `ISOLATED_ROUTE_WARMUP=true` is the runtime default;
`submit "-PsubmitVersion=v05?ISOLATED_ROUTE_WARMUP=0"` builds the old-warmup diagnostic control.
The v05 policy itself is unchanged. Core operations can still overrun a cooperative slice;
local timing checks do not guarantee identical behavior on the CG host.

`procCheck` now defaults to **zero grace**: 1,000 ms first response / 50 ms later. One clock
includes turn serialization and stdin write/flush; stdout lines are timestamped by the reader.
Late lines fail even when the waiting thread resumes with a nonempty queue. A timeout or EOF
ends the process session, so stale output cannot become the next turn's answer.
`-PgraceMs=30` explicitly restores extra allowance for diagnosis, not a strict timing pass;
nominal deadline overruns remain reported. `-Pout=build/analysis/process-check.json` selects
the per-turn JSON report (also written when games fail). It separates send time, reader arrival
and consumer wake-up, and records source/executable hashes. Reader scheduling and host load
still affect the measurement; it is not CG CPU time or a hard real-time watchdog for blocked IO.

`-PprocessDiagnostics=true` optionally splits input preparation/write/flush and records host CPU/GC
counter deltas plus failed input. It is off by default, requires an unused report path and does
not change deadlines or Player.java. These are host-side clues, not measured bot search time;
see [process diagnostics](docs/benchmarking.md#optional-process-input-diagnostics).

1. `gradlew test && gradlew bench ...` (gate in `AGENTS.md`)
2. `gradlew submit && gradlew procCheck`
3. Paste `build/submit/Player.java` into the CG IDE (language Java), PLAY MY CODE, then SUBMIT.
4. Record the version and CG rank in `docs/BOTS.md`.

## Opponent prediction study

`cgForecast` evaluates a lightweight adaptive sabotage model without changing our bot's actions.
It predicts from each pre-turn state and learns only after observing the next state; held-out
opponent entries never enter baseline selection. See [docs/sabotage_forecast.md](docs/sabotage_forecast.md)
for the fixed experiment, information boundaries, results and limitations.

`cgTiming` profiles baseline route search, its nested vulnerability evaluation and sabotage
on recorded states. Optional `-Ppaths` is a comma-separated replay list; `-Pparams` selects an
explicit diagnostic variant and `-Pout` saves all per-turn observations. It enables
`PROFILE_SEARCH` only for that run. Evaluation time is INCLUDED in route time, not additional.
These instrumented current calls are not the frozen executable or the local timeout match;
use them for attribution, and use uninstrumented `procCheck` for process deadline evidence.
`cgTiming` invokes the generic strategy warmup, not the isolated `MainBot` submission hook;
its warmup counters do not measure the new submission runtime.
