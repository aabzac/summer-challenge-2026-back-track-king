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

## Active practical selection policy (2026-09-20)

The active policy is **practical-1000-futility-boss-cache-v4**. It replaces the v3
screen/conditional-direct shape with one pooled 1000-game local comparison plus the unchanged
40-game cached boss. Adoption is prospective; the v2 and v3 checkers and every historical
verdict stay exactly as recorded.

- Local strength: a single fixed **1000-game** comparison of candidate versus the selected
  control on fresh disjoint maps, played in three registered blocks and pooled into one score:
  block 1 = 300 games / 150 maps, block 2 = 200 / 100 (cumulative 500), block 3 = 500 / 250
  (cumulative 1000).
- Two pre-specified interim looks are **futility only** — they can reject the candidate but
  never promote it early: stop when the pooled score is strictly below **50%** at cumulative
  300 or at 500 games. A candidate that survives both looks always plays the full 1000, and the
  promotion decision is made once, at **>=55%** over all 1000 games. There is no early pass.
- Boss: **40 new candidate games** on the cached control's exact 40 maps/seeds/seats, scored
  relative to the pinned control (wins=1, draws=0.5; candidate total >= control passes; ties
  pass). Unchanged from v3. By policy the boss batch may run concurrently once the 500-game look
  clears; a local failure can never be rescued by boss results.
- Rationale: at n=500 a candidate whose true rate is 55% falls under 50% about 1% of the time,
  so a genuine passer is almost never aborted; a futility look only ever stops a losing run, so
  it cannot inflate the false-promotion rate. Pooling one 1000-game sample is cleaner than a
  separate 300 screen plus a fresh 1000 on other maps, and it never plays a throwaway screen:
  clearly-losing candidates die at 300, borderline ones at 500, only survivors reach 1000.
- No tuning, extending or retrying stages until they pass. Engine fidelity, zero errors and
  strict exact-artifact process checks remain mandatory. No overall 95% guarantee is claimed;
  confidence intervals stay diagnostic. Keep the timed strength bench at two worker threads:
  more threads speed up wall-clock but starve the timed 486/36-ms search and can bias A vs B.

Run `python tools/run_practical_v4.py --spec <candidate> --name <id> --seed <base>` for the
local blocks (it stops automatically on a futility look), then combine with
`python tools/practical_gate_v4.py --block1 <r1> --block2 <r2> --block3 <r3> --boss-plan <cached>`.
The cached boss batch itself is unchanged: `tools/boss_cached.py`. When the local control is a
frozen id whose cached boss games were played as the measured `current?...` artifact (today
`v08` = build `c6b4deae…`), the v4 checker accepts that documented pair through
`FROZEN_CONTROL_ALIASES`, provided every parameter present in the cached snapshot matches and
every later field is a known default-off field at its default; the relabelling is recorded as
`bossControlAlias` in the verdict. Any other spec/build mismatch is still rejected.

### Superseded (still available for reproduction): practical-300-1000-boss-cache-v3 (2026-09-19)

The v3 specification and initial accepted v07 cache are in
[boss-cache-policy-v3.md](boss-cache-policy-v3.md); its checker is `tools/boss_practical_gate.py`.
Experiments completed under v3 keep their recorded policy and verdict.

- Direct candidate versus selected control: 300 games / 150 fresh paired maps;
  >=60% passes, <=52% stops. Only >52% and <60% continues to a separate fresh
  1000 games / 500 maps, passing at >=55% in that continuation alone.
- Boss: **40 new candidate games** on the cached control's exact 40 maps/seeds/seats,
  20 P0 and 20 P1. Reuse the individual verified control outcomes. Candidate total
  >= control total passes; equality passes. Wins=1, draws=0.5 in both domains.
- This replaces the required older/style-opponent 300/1000 stages. Boss API calls
  stay sequential, but may overlap the required local 1000 after a qualifying screen.
  Complete both started stages; direct failure cannot be rescued by boss results.
- Cache reuse is a practical regression check, not fresh independent confirmation
  or proof of noninferiority. Refresh for boss/league changes and after development
  cycles guided by this pool; retain exact control build, dates and map evidence.
- No tuning, extending or retrying completed stages until they pass. Engine fidelity,
  zero errors and strict exact-artifact process checks remain mandatory. Run process
  timing checks before heavy local concurrent work. Neither strength nor process checks
  authorize promotion/upload. No overall 95% statistical guarantee is claimed.

Use `python tools/boss_practical_gate.py --screen <300-report>` and, when required,
`--direct <1000-report>`, plus `--boss-plan <cached-candidate-plan>` for the full gate.
An incomplete local or boss stage cannot pass. The checker validates local pairing,
freshness between direct stages, settings and full parameters, plus verified cached
boss outcomes. Exact local source/class provenance and exclusions from older experiments
still require a prospective experiment manifest. Local protocol hashes and boss graphics
hashes have different definitions and must not be treated as directly comparable.

Adoption is prospective. Previously completed experiments, including network chords,
keep their recorded policy and verdict. The old tool and protocols below remain
available for historical reproduction; new evaluations use v3.

## Historical practical selection policy (2026-09-16, superseded by v3)

Policy **practical-300-1000-v2** added a >52% continuation floor to v1. Freeze
candidate A, selected control B, common older/style opponent C and all settings before play.
Draws count as half a win. Every map is played with both sides; all stages use distinct fresh
maps and nominal CG timing. Keep stages separate and complete them regardless of running score.

1. **Direct screen:** A vs B, exactly 300 games / 150 maps. Score >=60% passes this stage.
   Score <=52% stops evaluation; retain the candidate and evidence without automatic continuation.
2. Only for **52% < score <60%**, direct continuation: separate 1000 games / 500 new maps. Score >=55% in these
   1000 alone passes; otherwise stop promotion evaluation. Never pool the first 300 with these.
3. After direct success, **matched common-opponent screen:** A vs C and B vs C, 300 games each
   on the SAME new 150 maps, seeds and sides, with matching C parameters, timing and workers.
   Compare map-level paired scores. Candidate-control advantage >=3 percentage points passes.
4. Otherwise **matched continuation:** A vs C and B vs C, 1000 games each on the SAME separate
   new 500 maps. Candidate score >= control score in this larger sample alone passes; ties pass.
   A loss stops evaluation. Preserve the candidate and all evidence; no automatic retries.

Exactly 52% stops; exactly 60% passes the direct screen. The 52% floor favors sensitivity over
the more aggressive 53% alternative: their difference is only three win-equivalent points in
300 games. This is a practical choice to save work, not a proven optimum or proof that a stopped
candidate lacks benefit. It affects only the first direct screen; the 1000-game threshold and
common-opponent stages are unchanged. Historical v1 reports and decisions remain as recorded.

Both strength stages are required. The 3-point threshold is a practical cutoff, not an error
margin. This policy has **no overall 95% statistical guarantee**; confidence intervals remain
descriptive diagnostics. Do not tune candidates, change methods, or extend sample sizes between
stages. Predeclare all pools and branches. Error-free benches, engine fidelity and strict process
checks of the exact submission are still required; a strength pass is not authorization to
promote or upload. Stage provenance and complete matching must be checked outside a single report.

Explicitly requested retrospective adoption may reuse an existing 300-game screen only after
verifying the unchanged strategy and complete parameters. Keep its old verdict and label the
policy change; subsequent stages must be new. Do not reuse unmatched historical percentages
for the common-opponent comparison. The first v1 application remains historical:
[quick-income-practical-v1.md](quick-income-practical-v1.md). Its 54.33% screen also clears the
new floor; the 53% result in the subsequent 1000 still fails 55%. No replay or verdict rewrite.

Evaluate historical v2 stages with `python tools/practical_gate.py --screen <300-game-report>`.
Add `--direct <1000-game-report>` when required; then `--style-candidate` and `--style-control`
for the matched 300-game reports, and `--full-candidate` / `--full-control` for the conditional
matched 1000-game reports. `--out <new-file.json>` writes compact evidence without overwriting.
The checker validates complete per-side outcomes, distinct/fresh maps between stages, exact
thresholds, unchanged parameters, matching map seeds and timing/worker/Java conditions. It
reports the next required stage or the strength verdict, never process readiness or promotion.
Source/class identity and disjointness from all older experiments still require the experiment
manifest and benchmark exclusions. Test the checker with
`python -m unittest discover -s tools -p test_practical_gate.py`.

## Reports and uncertainty

`-Pout=build/bench/current-v03.json` chooses the report path. Otherwise each run writes
`build/bench/bench_<timestamp>.json`. Reports contain the requested specs, full resolved parameter
values, Java version, thread count, seed range start, map source, every pair's map hash and both
games' scores/errors/timing, plus aggregates and decision diagnostics. Scores within
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

The historical `promotionGatePassed` field is retained for report compatibility: it tests the
OLD >=200 games, score >=55%, CI lower bound >50%, zero-error rule. It is explicitly marked
diagnostic-only by `promotionGateRole` and the console. The active policy id is `promotionPolicy`.
`practicalDirectStage` checks only the 300/1000 direct-size threshold, complete distinct map pairs,
CG timing and errors; it cannot validate prior-stage provenance or the required cached boss check.
Never treat either single-report field as a full promotion decision. The benchmark saves its
report and exits unsuccessfully on bot errors; a failed strength threshold is a successful
measurement, not a command failure.

Under the historical policy, 55% was a chosen minimum observed effect, whereas
the confidence bound protected against sampling noise. With 300 games on 150 distinct maps,
the distribution-free bound is about +/-11.1 percentage points: the old effective passing
rate is therefore above 61.1%, not merely 55%. Lowering only the 55% threshold would not rescue
a 54.3% result with a [43.2%, 65.4%] interval. An inconclusive result is not proof of no benefit.
The bound is deliberately conservative and does not use the observed map-level variance.

A fresh local comparison through a common opponent must rerun both candidates with the same
maps, sides, opponent parameters and timing profile. V3 explicitly allows the documented boss
cache exception: reuse individual pinned matched outcomes, with its time-drift and known-map
limitations, not an unrelated historical percentage. Doing better against v03 does not imply winning head-to-head against v04 or doing
better against the arena population. Choose the policy before fresh evaluation. An explicit
user-directed policy change does not retroactively turn an old failure into independent proof.

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
