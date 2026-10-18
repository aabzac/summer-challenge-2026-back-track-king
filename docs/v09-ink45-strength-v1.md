# Ink45 versus ink40: local budget strength experiment

Declared 2026-10-18 at commit 341741f, before any new strength game. The user
requests an ordinary local benchmark after the 45 ms boss observation. Compare
the same incremental-ink candidate at 45 and 40 ms to isolate the additional 5 ms.
The earlier explicit waiver of the timing prerequisite remains in force.

Candidate A: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=45,USE_INCREMENTAL_INK_REFRESH=true`.
Control B: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40,USE_INCREMENTAL_INK_REFRESH=true`.
Both arms use the same strategy, parameters and guards except TURN_BUDGET_MS.
Fast path and missed-deadline correction are OFF. First-search budget 486 ms
for both arms; actual later budgets 45 for A and 40 for B, including after seat
swaps. Native search timing is diagnostic, not a full-response deadline test.

## Runtime and retained evidence

Use a separately frozen native runtime with verified unchanged shared core,
strategy and runner semantics from the previously measured ink40 runtime.
If that runtime rejects 45, add only the necessary budget-validation support in
a separately retained wrapper overlay; do not rebuild or change strategy/core.
Verify resolved full parameters, actual per-seat allowances and byte identities
before play. Preserve the exact submitted 45 and 40 artifacts and their evidence.
Do not change production strategy or frozen reference packages for this bench.

The exact 45 package is 91,748 characters, source SHA-256
`1118fc8ad63d8b58b7d1f2a5f0e50c4605314e63d370518a93d19ae1cb7953a4`.
The ink40 package is 91,714 characters, SHA-256
`aff2dc9bf48d2f9a141012c3d47fc079bb9af9354cdfa846ef40832804db75a5`.
Retain the failed 45 two-game process diagnostic (51.6577/51.0781 ms versus 50),
the failed ink40 full 16 diagnostic, and all historical verdicts. Do not run
another process panel or require timing success before this local comparison.
Actual game errors still prevent a clean local conclusion.

The completed 45 boss observation 14 W/26 L includes startup failure 14 and changed
public boss metadata. Keep that result separate; it is not local benchmark data
or a clean boss promotion pass. No new live/boss/control requests are authorized
or needed for the present local-only task.

## Fixed maps and stopping rule

Register 500 fresh paired maps before any games, checking shared-core map hashes
against prior played/reserved local and CG maps and all registered seed ranges.
The prospective ranges are:

- Block 1: 300 games/150 maps, seeds 401018000..401018149.
- Block 2: +200 games/100 maps, seeds 401118000..401118099.
- Block 3: +500 games/250 maps, seeds 401218000..401218249.

Each map is played once with A as P0 and once with A as P1, with fresh bots.
Keep exactly two workers and avoid concurrent CPU-heavy work. This is one
pooled sample under practical-v4 arithmetic: win 1, draw 0.5. Stop strictly below
50% at cumulative 300 or 500 games; neither look can pass early. Survivors play
all 1000 and meet the local numerical criterion only at >=55%. Complete each
started block. No tuning, extensions, retries, replacement maps or pooling with
older experiments. Five-minute pauses concern the live-service quota and are
not required for this local benchmark.

The shared practical-v4 tool currently recognizes budgets through 40. A scoped,
tested reader may admit this exact 45/40 pair while preserving raw report hashes,
per-arm timing validation, settings/freshness checks and all stopping arithmetic.
Do not rewrite historical reports or frozen policy tools to force acceptance.

Freeze protocol, input pools/exclusions, runtime/classes/parameters and driver
before play. Independently recount all raw scores, paired seats, maps, errors,
budgets and stopping decisions. Retain skipped stages after a futility stop.
Archive public evidence, update BOTS/PLAN/DEVLOG and commit locally. A local
pass alone cannot override timing/technical/boss qualifications. No default
change, promotion, arena upload or push; selected v09, default 36 and experimental flags OFF remain.
