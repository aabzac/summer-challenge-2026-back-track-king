# Geometry-only cheapest path: conditional strength comparison

Declared 2026-10-18 before diagnostic results or new strength games, starting
at baf59a1. This is conditional on the independently checked readiness gates
in [the path diagnostic protocol](v09-fast-path-v1.md).

Candidate A is `current?NOISE=0.1,USE_BATCHED_REFRESH=true,USE_FAST_CHEAPEST_PATH=true`.
Control B is `current?NOISE=0.1,USE_BATCHED_REFRESH=true`. All effective Params
fields must match except the default-OFF `USE_FAST_CHEAPEST_PATH` flag. Both
arms keep incremental ink refresh and missed-deadline correction OFF, with
the same strategy, safeguards, search limits, RNG and timer settings.

Do not prepare or freeze strength inputs, or play any strength game, until
the new diagnostic's complete readiness record passes. Require the exact new
artifacts/runtime and unchanged frozen inputs; 292 exact engine replays;
524-position exact ample parity of actions, values and semantic diagnostics;
supplementary ample guard parity plus the turn-40 target-33 attack and all six
winning closures at 36 ms; every tested source at or below 100,000 characters;
eight completed strict process games with zero errors, timeouts, deadline
overruns or missing replies; strictly positive equal-work time reduction and
strictly more completed comparisons at 36 ms. Preserve all failed attempts
and outliers. The known historical catalog-pin test failure remains disclosed
and must not be confused with a clean full-suite pass.

Reuse the diagnostic's frozen new native runtime and exact generated ON/OFF
artifacts without rebuilding. Confirm every source/class/runtime hash and the
complete classpath inventory, including absence of added class files. Preserve
all 260 selected/prior artifact files pinned before implementation. Do not
compare changed production sources with obsolete pre-implementation hashes;
the new diagnostic freeze supplies their tested identities.

Use the existing shared BenchmarkRunner, uninstrumented, two workers, nominal
CG search budgets 486 ms first / 36 ms later for BOTH arms. Fresh bot instances
per game, both seats on every map. Native in-process timing is not a substitute
for the diagnostic's strict full-response 1000/50 ms process sample.

Use the unchanged practical-v4 LOCAL schedule: block1 300 games / 150 maps,
block2 +200 / 100 maps, block3 +500 / 250 maps. Base seeds are 291018000,
291118000 and 291218000. Register all three pools and compute all 500 map
identities with shared MapGenerator and Protocol before play. Exclude every
known prior played/reserved map identity, including the preceding 500-map
ink-strength series and saved CG maps, and verify all registered seed
intervals. No overlapping maps, substitutions or replacement pools.

Complete each started block. Wins count one point and draws one half. Stop
only when the pooled score is strictly below 50% at cumulative 300 or 500;
there is no early pass. Survivors complete all 1,000 games and pass locally
only at at least 55%. Use the unchanged practical_gate_v4 reader and decision.
No retry, tuning, limit change or extension after seeing outcomes. Preserve
errors and timing outliers. Independently recount raw winners/scores, seats,
maps, parameters, budgets, input identities and stopping. Any confidence
interval is descriptive and cannot change the declared decision.

This measures the isolated path optimization on the same batched36 base. It
does not promote that previously rejected base or compare against selected
production v09. Local failure ends this evaluation and leaves the flag OFF;
no boss result can rescue it. A local pass supports further evaluation only:
fresh matched boss confirmation and a production-control comparison are
separate requirements. The exposed/amended old batched pool is not eligible
fresh boss evidence. No live requests, arena uploads, promotion, default
changes or push are part of this experiment.

Freeze all new strength inputs, runtime and diagnostic evidence before play;
verify before and after each complete block. Keep outputs write-once. Archive
raw reports, logs, decisions, map preflight and independent audit; update
BOTS/PLAN/DEVLOG and commit the completed experiment locally. If diagnostic
readiness fails, retain the prepared harness as unexecuted and do not start
this conditional strength schedule.
