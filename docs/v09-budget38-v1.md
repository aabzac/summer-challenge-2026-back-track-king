# v09 budget38 v1: prospective process and strength experiment

Declared 2026-10-16 before new timing or strength measurements. The user requests
38 ms: first full response time and timeouts under a 50-ms limit, and only if
that check passes, strength against the current 36-ms budget.

## One-variable comparison

Use the latest batched-refresh candidate in both arms, as stated to the user:

- Candidate: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=38`.
- Control: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=36`.

All other resolved parameters are identical. Missed-deadline correction stays
OFF. This isolates the budget effect on the batched candidate; it is not a new
combined promotion comparison against selected frozen v09. Default36, selected
v09, the old failed boss gate and incomplete extra live series remain unchanged.
No live game, source upload, arena publication or push is part of this request.

Only later-turn internal allowance changes, from 36 to 38 ms. Keep the external
50-ms deadline, first-turn 1000-ms limit, its 14-ms margin, 500-ms warmup and
nominal first search486ms unchanged. Effective later margin is12ms for the
candidate,14ms for control. Preparation still consumes the internal allowance.
Do not add time inside the policy after subtracting preparation or extend a
strict ProcessBot deadline. WARN thresholds follow each internal allowance:
integer totals>38 for candidate,>36 for control; their counts are not directly
comparable as identical-threshold observations.

Add a default36 `Params.TURN_BUDGET_MS`, restricted to36/38 and supported38 only
for current native rollout. Preserve current/default, frozen-v09 and the old
batched36 generated source/config identity by excluding inactive additions from
the merger. Never edit frozen strategy packages. Keep all earlier artifacts.

For the in-process strength comparison, apply and report each participant's
actual budget independently: firstA/B486ms, laterA38/B36ms. Swap budgets with
the bots when swapping seats. The old common CG profile remains the baseline;
do not accidentally run36/36 or38/38, or label its base36 as both effective
budgets. Reject unsupported legacy-profile combinations before games.

## Preflight

Record implementation/protocol revision, exact resolved configurations, source
and class hashes, runtime and all input/map hashes before timed runs. Preserve
the default `build/submit` and old experiment directories. Export both arms
using `gradlew submit` into separate experiment directories. Candidate must
remain within100000 UTF-16 characters; control must reproduce prior batched36
source SHA256 `2cb3a6a254fd8f37e0c9f07e2aa36565f4d61027ee44f508a5f64a93d547d61a`.

Meaningful correctness checks cover first/later allowance, preparation charging,
per-seat budget swapping, first-turn/warmup invariance, invalid configurations,
frozen/default export identity and the unchanged strict external deadline.
Run relevant existing submission, process, timing and benchmark checks. No game
rules change is planned; confirm engine fidelity before trusting strength.

Freeze the final exact artifact and an immutable copy of this declaration after
preflight. Preflight repairs occur before measurement and are recorded; no
outcome-guided artifact repair or favorable retry is allowed within this experiment.

## Fixed strict process stage

Run the candidate exactly16 times, eight maps in this order, each once per seat:

1. 903510176.
2. 904146009.
3. 904148181.
4. 904147739.
5. 903184594.
6. 903182662.
7. 903507919.
8. 903584040.

Use retained canonical files pinned in the input inventory. The first four are
the established process panel; the additions are the four largest nonstandard
maps by static pair count in the retained rollout-process pool (51,44,43,43),
not selected using new runtime outcomes. Seeds7000/7001 in each two-game call;
opponent is the fixed batched36 control recipe above.

Use eight sequential `gradlew procCheck` calls, explicit candidate submission
directory, games2, grace0 and IO diagnostics OFF. External deadlines remain
1000ms first/50ms later. No other CPU-heavy job runs concurrently, no JFR or
instrumented timing variant. Retain all outputs and response timestamps,
including missing responses and outliers. Complete all declared calls once
even after a reported timing failure; do not rerun failed maps or add grace.
An infrastructure failure without complete evidence means INCOMPLETE, not PASS.

Pass requires a complete16-game sample with zero bot errors, timeouts and
nominal overruns. Summarize first and later responses separately: p50/p95/p99/
p99.9, max, threshold counts, missing replies, and games with failures. Use
reader-stamped full response time; do not substitute consumer wake-up or WARN.
This is local readiness evidence, not a universal server timing guarantee.

**Any process-stage failure or incompleteness skips the entire strength stage.**
This condition comes directly from the user's request and supersedes the older
batched-refresh experiment's permission for a local bench after process failure.

## Conditional fixed strength stage

Only after process PASS, run the practical-v4 local decision rule with the
unchanged bot recipes and exactly two workers:

- Block1:300 games/150 paired maps, seeds261016000..261016149.
- If pooled score>=50%, block2 adds200 games/100 maps,
  seeds261116000..261116099.
- If pooled score>=50% at500 games, block3 adds500 games/250 maps,
  seeds261216000..261216249.

Stop for futility strictly below50% at cumulative300 or500. No early pass;
survivors finish1000. Local numerical pass requires>=55% over1000 games,
wins1/draws0.5/losses0. Confidence intervals are descriptive. No tuning,
retries, extensions, simultaneous heavy jobs or outcome-driven map replacement.

Register all three pools and verify all500 shared-core map hashes before play:
mutually unique, disjoint from all23624 currently retained known identities and
any additional registered source discovered in preflight. Registered seed
intervals must not overlap prior ones. Missing/malformed inputs or collisions
block the stage; do not silently reroll. Each map plays both seats with fresh
instances. Preserve the effective38/36 budgets, full parameter recipes, errors,
paired coverage and source/runtime hashes in evidence and validate them before
accepting any numerical gate verdict. The nominal in-process bench excludes
protocol parsing and warmup; its timings are not the process readiness test.

A local pass is only a local budget-effect result. Boss confirmation and any
strategy promotion remain separate, unperformed work requiring fresh suitable
control evidence; the previous known boss pool has been development-exposed.

## Completion

Record actual process results and either the conditional strength result or
the reason it was skipped. Retain compact public evidence and reproducible
artifacts, update BOTS/PLAN/DEVLOG and commit the completed experiment locally,
including failure. Keep defaults and prior verdicts unchanged.

## Preflight result (before measurement)

The candidate exports at90,607 characters, source SHA256
`979cbac253699737deb01dc8ce44c99265d5362170db29f8b0e7e79edbfd1992`.
Control remains90,129 characters and byte-identical to retained batched36.
Default/current and frozen-v09 source pins also pass. Engine292/292 exact.
Budget/process/submission-focused37 tests and31 Python gate/driver tests pass.
Three parameter-inventory assertions were repaired before measurement; all13
tests across V07/V08/V09 promotion checks then pass.

The broad exploratory test run was766 tests/28 failures, **not a clean full-suite
pass**. Three were the repaired inventories. The remaining25 concern18 existing
historical-catalog Pathing pin failures, one historical neural-target Pathing
baseline mismatch, and six tests for an unrelated untracked/unregistered bot.
All13 catalog core-class paths are identical before and after this change;
catalog pins, core sources and unrelated files remain untouched. The relevant
budget/process checks passed, so these unrelated failures do not invalidate
the fixed current-native-rollout experiment. All raw failures are retained.

## Strict process result: PASS

Completed the fixed16 games once, eight maps/both seats, exact candidate above.
All16 first replies and1,505 later replies were observed. Zero bot errors,
timeouts, nominal overruns or missing replies; no later reply exceeded45ms.

- Later full response: median32.4234ms, p9539.9376ms, p9940.7075ms,
  p99.9=44.0861ms, maximum44.3156ms.
- First full response: median587.72005ms, maximum634.2294ms, below1000ms.
- Percentiles use nearest-rank except the ordinary median; first-turn high
  percentiles equal the maximum because there are only16 observations.

These are reader-stamped end-to-end local response times, including input
delivery, preparation, search and output, not WARN or consumer-wakeup times.
The sample passes the declared readiness test. It does not establish a universal
server guarantee or authorize adding the observed5.6844ms maximum headroom.
Only the tested38ms value proceeds to the conditional strength experiment.

## Strength result and decision: STOP_FUTILITY_300

The conditional first block completed300 games on150 fresh paired maps,
seeds261016000..261016149, two workers, zero bot errors. Actual first budgets
were486/486ms, later budgets38/36ms, with the allowance following bot identity
when seats swapped. Configurations differed only in TURN_BUDGET_MS.

Candidate: **143 wins,145 losses,12 draws;149/300 points =49.6667%**.
The score is strictly below50%, so the fixed v4 rule stops at300. Blocks2/3
were not started, no extensions or retries, no boss games. This does not prove
38ms intrinsically weaker; it fails to establish a useful strength improvement.
Do not reinterpret the selected300-game result as a completed1000-game test.

Native-call diagnostics in this in-process block are separate from process
deadlines: candidate max46ms/zero calls>50; control max98ms/two calls>50.
These counters include first turns, use floored milliseconds and retain no
turn index. They do not demonstrate that38ms is safer/faster than36ms, and
are not external-process timeout observations. No causal diagnosis or retry
was performed using these outliers.

**Decision: retain the default36ms budget.** The38ms candidate passed this local
timing sample but did not earn a strength change. Batching remains default OFF,
selected v09 and all earlier boss verdicts remain unchanged. The experimental
parameter and validated per-arm harness remain available for reproducibility.
The selected submission's28 files are preserved; no upload, promotion or push.

Independent final audit:325 checks PASS, all7,622 frozen files and8 runtime
files unchanged, all150 played map/seed pairs exact and fresh. No remaining
measurement or evaluation step belongs to this stopped experiment.

[Summary evidence](evidence/v09-budget38-v1.json),
[artifact index](evidence/v09-budget38-v1-artifacts.json),
[retained archive](evidence/v09-budget38-v1-artifacts.zip).
