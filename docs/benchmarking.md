# Reproducible bot comparisons

## Paired maps

`gradlew bench -Pbot1=current -Pbot2=v03 -Pgames=200 -Pthreads=2 -Pseed=1000`
runs 100 pairs: generated seeds 1000 through 1099, each once with A as P0 and once with A as P1.
Both games start from the same map and have fresh bot instances and dynamic state. Pairs are
scheduled in parallel and aggregated in their original order. `-Pgames` must be positive and
even, `-Pthreads` positive, and generated seed ranges must neither overflow nor contain zero.

With `-Pmaps=replays/cg`, pair i uses replay i modulo the pool size, so both sides always receive
the same replay map. The reported seed is the pair's run identifier; a replay filename and the
map hash identify the actual replay map. An explicitly requested empty, missing or malformed
pool fails rather than falling back to generated maps.

The benchmark's wall-clock budget can still make decisions sensitive to JIT, CPU contention and
GC. Fixed maps and parameters reproduce inputs, not necessarily every timed search decision.
Use few worker threads for confirmation runs and check submission timing with `procCheck`.

## Search-time profiles

`bench`, `run` and the optional local bot in `cgStudy` now default to `-Ptiming=cg`:

- First search: `CGRunner.FIRST_TURN_MS - MARGIN_MS - WARMUP_MS` = **486 ms**.
- Later searches: `CGRunner.TURN_MS - MARGIN_MS` = **36 ms**.
- `-Ptiming=legacy` explicitly restores the historical local **1000/50 ms** allowance.
  Historical `cgStudy` used a separate hard-coded 40 ms, not this legacy profile.

These are **nominal search budgets**, not exact CG execution emulation: the in-process runners
do not parse the protocol or run the submission's 500-ms JIT warm-up. The actual CG runner also
subtracts elapsed parsing/state preparation and actual warm-up time, so it can give less time.
CPU speed, JIT history, RNG consumed by warm-up, GC and scheduling still differ. No extra warm-up
is charged on every local game. No submission constants or strategy were changed by this fix.

The profile and both numerical budgets are saved in report schema v2 and printed in the console.
Reports without these fields predate alignment; do not silently compare them as equivalent runs.
`procCheck` caps only its **in-process opponent** to CG search budgets. Its external Player keeps
the full 1000/50-ms IO deadlines with **zero grace by default**; do not subtract the margin twice.
An explicit nonzero grace is diagnostic only. Inspect measured response times, not just the timeout count.

For a timing-control experiment, run both profiles sequentially on the same new seed range and
same frozen bots. Do not run other CPU-heavy jobs concurrently. This tests sensitivity to the
budget, not arena strength; both reference bots may have the same scheduling weakness.

## Optional process input diagnostics

`procCheck -PprocessDiagnostics=true -Pout=build/analysis/my-input-diagnostic.json` adds host-side
IO evidence. It is off by default and refuses an existing report path, so a diagnostic rerun
cannot silently replace earlier evidence. Choose the maps, sides and sample size before running;
keep failures and do not retry until a run passes.

Each timing's optional `io` object splits `sendMs` into `prepareMs` (Protocol.turnBlock), `writeMs`
and `flushMs`, and records `inputChars`. Preparation and both write phases still consume the
original absolute deadline; no grace or extra bot time is introduced. `responseMs` remains the
reader's timestamp, not the waiting thread's wake-up time. Null means no response was observed.

`hostThreadCpuMs` is the calling host thread's CPU delta across the call, **not child bot CPU**.
Its resolution is platform-dependent: the first Windows diagnostic observed only 0/15.625-ms
increments, so zero does not mean no CPU work and a short-call delta can exceed its wall time.
`hostGcMillis` is the host JVM's coarse, JVM-wide collection-time delta, not a per-phase pause
measurement. Unsupported/disabled counters are null, not zero. Counter snapshots slightly bracket
the wall-time interval; neither delta pinpoints causality. For example, a long write with little
host-thread CPU can reflect pipe backpressure or descheduling; it does not prove slow search.
Child GC/scheduling and stdout-reader scheduling are not isolated by these counters.

Only non-OK statuses retain the exact turn block as `failedInput` (including diagnostic GRACE).
This block needs the recorded map's initialization and prior game state for meaningful replay;
it is not a complete game or a reproducer of JVM timing history. IO exceptions before phase
completion can terminate without a timing entry, as before. Diagnostics add measurement overhead:
use them for attribution, not as interchangeable timing-readiness evidence from an uninstrumented
run. They do not change Player.java, and neither mode is a hard watchdog for a blocked pipe.

## Frozen configurations

`Params` remains the live source of defaults for `current` and the submitted `main` bot.
Historical specs v01, v02 and v03 load the corresponding classpath resource
`btk/bot/versions/vNN.properties`. All 26 current parameter values were explicitly captured at
commit `8364ad5`, preserving the reference behavior in use when this protection was added.
v00 has no tunable parameters.

Use `BotFactory.create(spec)` to instantiate reference bots. It loads the snapshot into a fresh
`Params`, applies query overrides, then calls the historical implementation's parameterized
constructor. Frozen source packages are unchanged; their legacy no-argument constructors are
not the supported entry point for reproducible comparisons. Missing/empty snapshots and unknown
parameters fail. `main` and v00 reject query overrides instead of silently ignoring them.

`v03?NOISE=0.2,USE_BACKUP=0` overrides only this invocation; later `v03` instances still use the
snapshot. Snapshot values are never derived from future `Params` defaults. Newly introduced
fields are not read by older frozen strategy source and need not be backfilled into old files.

When freezing the next version:

1. Copy the current strategy into its new vNN package and rename its classes, as in `AGENTS.md`.
2. Add `src/main/resources/btk/bot/versions/vNN.properties` with every parameter's explicit value
   for the candidate. Use the full `paramsA` or `paramsB` object from its benchmark report; write
   `KEY=value` entries, not just differences from the current defaults.
3. Register the constructor in `BotFactory`. The factory requires the new version's snapshot.
4. Record the code commit, effective config, report path and comparisons in `BOTS.md` / `DEVLOG.md`.
   Do not retune an existing version's source or snapshot; use an explicit query or a new version.

Core rules and shared utilities remain shared. A parameter snapshot is not an archival copy of
the whole executable; retain the code revision along with reports when comparing across changes
to the engine or runtime.

## Reports and uncertainty

`-Pout=build/bench/current-v03.json` chooses the report path. Otherwise each run writes
`build/bench/bench_<timestamp>.json`. Reports contain the requested specs, full resolved parameter
values, Java version, thread count, seed range start, map source, every pair's map hash and both
games' scores/errors/timing, plus aggregates and the statistical gate decision. Scores within
each game's result are indexed by player position P0/P1.

Draws count as half a win. Both games on a map, and any repeated visits to the same map, are
grouped together using SHA-256 of its complete initialization protocol. Different replay files
containing the same map belong to the same group.

The primary 95% interval uses a conservative Hoeffding bound for independent map groups. Each
map's mean score lies in [0,1]; its weight w is games on that map divided by total games. The
half-width is `sqrt(log(40) * sum(w*w) / 2)`, clipped to [0,1] around A's aggregate win rate.
Equal-weight repetition of the same maps does not narrow the interval. With 100 distinct maps,
the half-width is about 13.6 percentage points, so a modest gain needs substantially more maps
than the minimum 200-game smoke run. Use additional generated seeds to increase evidence, not
repeated passes over a small replay pool.

This bound assumes independent representative map groups; it cannot compensate for an
unrepresentative opponent or map selection. The former per-game Wilson interval remains in the
text report for historical comparison and does not control promotion.

The statistical gate requires at least 200 games, A's win rate >=55%, the map-grouped lower bound
above 50%, and no bot errors. Passing it does not replace the older-opponent and process-timing
checks from `AGENTS.md`. The benchmark saves its report and exits unsuccessfully on bot errors;
a statistically inconclusive run is a successful measurement, not a command failure.

The two thresholds serve different purposes: 55% is a chosen minimum observed effect, whereas
the confidence bound is protection against sampling noise. With 300 games on 150 distinct maps,
the current distribution-free bound is about +/-11.1 percentage points: the effective passing
rate is therefore above 61.1%, not merely 55%. Lowering only the 55% threshold would not rescue
a 54.3% result with a [43.2%, 65.4%] interval. An inconclusive result is not proof of no benefit.
The bound is deliberately conservative and does not use the observed map-level variance.

A comparison through a common opponent must rerun both candidates with the same maps, sides,
opponent parameters and timing profile. Compare matched map results, not unrelated historical
percentages. Doing better against v03 does not imply winning head-to-head against v04 or doing
better against the arena population. A different interval method or acceptance policy should
be chosen before the next confirmation experiment, not selected to make a completed run pass.

`cgValidate` now fails the command on any divergence, malformed replay or empty input. This
allows a failed fidelity check to stop a scripted sequence instead of merely printing a warning.

## Height strata and behavior (report schema 3)

Every pair now includes width, height, townCount and pairCount from its exact initialization.
`byHeight` contains W/L/D, games and distinct map identities, pointwise map-grouped confidence,
score difference, average turns and early closures. `behavior` contains the corresponding whole-run
summary. The overall promotion rule is unchanged; there is **no per-height promotion gate** or
multiple-comparison correction. Small slices are exploratory, and must not be cherry-picked.

The referee collects accepted placement/disruption turns, accepted cells and paint expenditure
directly from `Rules.resolve`. Per-game arrays are P0/P1; `behavior` and `byHeight` arrays are **A/B**.
Rates divide summed accepted-action turns by summed game turns, not the number of games. A region
inked by both players does not mean one player sent two disruptions. Skipped/illegal requests are
not accepted moves. `closedEarly` excludes bot-error terminations and turn-limit endings.

Schema 2 reports lack these fields: missing metrics are unknown, not zero. Existing top-level fields
and the map-grouped gate remain available in schema 3. Preflight generates map initializations
before worker games; timing/JIT scheduling can differ from older benchmark implementations.

## Named development and confirmation pools

Checked-in manifests define the generator, exact seed range, purpose and number of map pairs:

- `bench/pools/development-v1.json`: 150 maps, seeds 20000..20149, 300 games. Reusable development
  panel; once used for screening these are not independent confirmation data.
- `bench/pools/confirmation-v4.json`: 500 maps, seeds 92000..92499, 1000 games. Reserved for a
  predeclared fixed candidate. No games from this pool were played when registered on 2026-09-16.
- `confirmation-v2.json` (90000..90499) and `confirmation-v3.json` (91000..91499) were consumed
  by the fixed rollout-v1 2x1000 confirmation. Neither is an untouched reserve; exclude both
  result reports (or their combined gamesPerMap index) in future confirmation experiments.

The original confirmation-v1 file is retained for historical identity but **retired as a reserve**:
the rollout-v1 development screen inadvertently used seeds 30000..30149. That run remains
development evidence, not confirmation. See `bench/pools/README.md` and DEVLOG for the correction.

With `-Ppool`, games default to the complete manifest (an explicit game count must match).
Combining a pool with `-Pmaps` or `-Pseed` fails; ranges cannot contain seed zero or overflow.
Without `-Ppool`, existing commands remain supported, defaulting to 200 games / seed 1000.
Default worker count is now 2, so available CPU count no longer silently changes the bench setup.

Confirmation requires `-PexcludeReports`: comma-separated **files**, each a benchmark report or
suite `index.json`. All selected initialization hashes are compared before any bot is created;
one overlap rejects the run. Missing/malformed/empty evidence fails. Report paths and SHA-256
are recorded along with the pool identity and manifest hash.

Example, after fixing a candidate and recording its development result:

```powershell
.\gradlew.bat bench "-Pbot1=current" "-Pbot2=v05" "-Ppool=bench/pools/confirmation-v4.json" "-PexcludeReports=build/analysis/rollout-confirmation-2x1000/prior-maps.json,build/bench/rollout-confirmation-2x1000/combined.json,build/bench/my-candidate-panel/index.json" "-Pout=build/bench/my-candidate-confirmation.json"
```

This validates disjointness against the **supplied evidence**, not every experiment ever run.
It does not automatically know about deleted reports, unrecorded tests, or previously consumed
confirmation data. Retain reports; after inspecting a confirmation result, do not tune and reuse
that pool as a new independent confirmation. Create a new named pool and include all earlier
development/confirmation report identities in the exclusion list. Merely calling a pool
"confirmation" is not evidence of statistical independence.

## Matched opponent panel

`benchSuite` runs the checked-in `bench/opponents-v1.json` sequentially (each match-up uses two
workers by default): v05, pressure-v1, v03, and v02 with future-track weights 1.0/0.2. Exact self-spec
is omitted; otherwise every opponent receives the same initialization hashes, seeds and sides.

```powershell
.\gradlew.bat benchSuite "-Pbot=v05" "-Pout=build/bench/my-v05-panel"
.\gradlew.bat benchSuite "-Pbot=current" "-Pout=build/bench/my-candidate-panel"
```

Compare matching opponent reports, not an average that can hide a regression against one style.
The suite writes `opponent-N.json` after each completed match-up and an `index.json` with completed
report hashes and a completion flag. It stops on bot errors and rejects an existing output directory.
The index's union of map identities is accepted by `-PexcludeReports`; its map counts are evidence
for exclusion, **not additional independent coverage across opponents**. No suite-level promotion gate.

`pressure-v1` is an independent local-only policy in `btk.bot.reference`, with its own fixed consumed
parameter snapshot. It chooses sabotage before construction, weights near-term income and public
potential corridors, favors maintaining pressure on an existing target, then builds cheapest
profitable routes over an eight-turn horizon. It avoids its chosen sabotage region for new tracks,
and does not use v05's nested vulnerability evaluator. A known-state losing immediate map closure
is rejected. No exact opponent reply is available to it.

Its purpose is behavioral coverage, not promotion to Player.java or proof of top-player imitation.
Keep this id/source/snapshot fixed once baseline results are recorded; use a new reference id for
future policy revisions. All knobs are in Params; old/current submission strategies ignore the
PRESSURE_* fields, and reference/benchmark classes are excluded from the submission whitelist.

## Bot identities and optional experiment opponents

`gradlew botCatalog` lists descriptive names and separates versions, independent references,
mutable working entries and retained experiments. Commands and report matching still use ids
such as `v05` or `exp-reply-rerank`, not display labels. See `bot_catalog.md` for recipes and the
strict implementation guard. `botA`/`botB` are additive schema-3 metadata; complete parameters
remain in `paramsA`/`paramsB`. An experiment's pinned preset hash does not include explicit query
overrides: those remain visible in the full spec and resolved parameters.

The catalog does not add rejected experiments to the default panel, promote a policy, or rewrite
old reports. The guard identifies strategy/core class bytes; it does not freeze the map generator,
referee, timing profile or Java runtime. Compare those separately as before.
