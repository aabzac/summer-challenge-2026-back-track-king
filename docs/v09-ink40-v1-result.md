# Incremental ink refresh at 40 ms: result

2026-10-18. Complete: **NOT_READY**. All 16 strict process games finished once;
two later responses exceeded 50 ms. The conditional strength stage is
**NOT_RUN_READINESS_FAILED**. Keep default36 and the optimization OFF.

## Controlled condition

The [prospective protocol](v09-ink40-v1.md) returns to the preceding
incremental update of connections after hypothetical ink:

- A: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40,USE_INCREMENTAL_INK_REFRESH=true`.
- B: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40`.

Both receive nominal 486/40 ms. Only incremental refresh differs; fast path
and missed-deadline correction are OFF. This would isolate the optimization
at 40 ms. It is not a 40-versus-36 comparison or a match against selected v09.
The earlier 52.7% / 1,000 result at 36 ms retains its NOT_PASSED verdict.

Use the preserved pre-fast-path ink runtime and pinned JDK/dependencies, so
the newer path implementation cannot affect the comparison. Native classes
and resources are copied into an isolated directory; no production files
are edited. The new export tooling strips the disabled path experiment.

## Artifact and inherited evidence

Three fresh `gradlew submit` exports compile: A40, B40 and provenance-only A36.
A36 exactly reproduces the prior ink source; B40 exactly reproduces the
historical budget40 control. A40/A36 generated strategy and core classes match;
only budget-related classes may differ. A metadata helper verifies native and
generated parameters, 486/40 allowances, first/later external 1000/50 ms,
unchanged margin14 and warmup500. It never plays or initializes a game.

The new candidate is **91,714 / 100,000 characters**. Control B40 is 90,641;
provenance A36 is 91,202. First-turn handling remains unchanged; input parsing
and preparation still consume the configured later budget.
Candidate source SHA-256 is
`aff2dc9bf48d2f9a141012c3d47fc079bb9af9354cdfa846ef40832804db75a5`,
build ID `24d23afd80815de8319b8d0b37ac44cc2ae255d3b297af8045176e497f406edd`.

Inherit the independently audited 292 exact engine replays and all 524 ample
action/diagnostic matches from the same old runtime. Prior focused tests were
248/249, including ten new passes and the known historical exp-income-guard
catalog-pin failure; no clean full-suite claim or fresh JUnit run is made.
Prior turn-40/six-closure guards used the 36 ms, nonbatched deadline-ON recipe;
they are historical mechanism evidence, not a new 40 ms combined-flags check.

The conditional strength driver passes **28 synthetic tests** without Java
or bot calls. It remains unexecuted if timing fails. Preservation includes
427 prior artifact/publication files plus immutable audited runtime/evidence;
old mutable workspace-source hashes are retained as provenance rather than
incorrectly compared to newer files. No measured stage is retried.

## Strict process panel

Exactly 16 games, candidate in both seats on each of the eight previously
declared budget40 maps, against B40, seeds7000/7001. Shared ProcessCheck uses
the exact A40 artifact, external first/later limits1000/50 ms, zero grace,
diagnostics OFF. Finish all games even after a reported failure.

All 16 first responses and **1,353 later responses** are retained, with zero
missing replies. There are **two timeouts and two nominal overruns**, causing
two game errors; these are the same two events, not six independent failures.

- Map904146009, candidate P1, turn36: **50.2949 ms**; send8.9199 ms,
  consumer50.6143 ms.
- Map903182662, candidate P1, turn41: **52.1667 ms**; send10.4478 ms,
  consumer52.3276 ms.

These are reader-stamped full response times. The distinct send and consumer
timestamps are retained; diagnostics were OFF, so they do not establish the
causal split between input preparation, pipe delivery, scheduling and child
search. WARN durations cannot substitute for the external deadline.

Later responses: median **33.8388 ms**, p95 **42.5540 ms**, p99 **43.0957 ms**,
maximum **52.1667 ms**. Exactly two exceed each of45/48/50 ms. First responses:
median617.41295 ms, maximum681.5245 ms, all within1,000 ms. Percentiles use
nearest rank, with the ordinary median. No map or failed game was replaced.

Independent reconciliation **PASS, 26,908 checks** confirms these retained
failures, all 2,467 frozen workspace files, seven runtime files and 427 prior
artifacts. Audit PASS describes evidence integrity; timing readiness remains
**NOT_READY** and authorizes no strength games.

## Decision

Any failed process stage skips the entire conditional strength schedule.
No strength games, pool generation, live boss calls, arena upload, promotion
or push. Keep default36 and all experimental flags OFF, selected v09 and all
earlier verdicts. A timing failure does not establish the variant's strength
at 40 ms or prove that every future response exceeds its deadline.

Retain the [evidence summary](evidence/v09-ink40-v1.json) and
[archive index](evidence/v09-ink40-v1-artifacts.json), including all raw responses,
frozen artifacts, inherited provenance and the unexecuted conditional harness.
