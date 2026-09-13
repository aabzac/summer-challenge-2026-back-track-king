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
the full 1000/50-ms IO deadlines (and the existing 30-ms local process grace); do not subtract the
margin twice. Inspect measured response times, not just the timeout count.

For a timing-control experiment, run both profiles sequentially on the same new seed range and
same frozen bots. Do not run other CPU-heavy jobs concurrently. This tests sensitivity to the
budget, not arena strength; both reference bots may have the same scheduling weakness.

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

`cgValidate` now fails the command on any divergence, malformed replay or empty input. This
allows a failed fidelity check to stop a scripted sequence instead of merely printing a warning.
