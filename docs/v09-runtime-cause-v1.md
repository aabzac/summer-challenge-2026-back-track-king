# v09 runtime cause diagnosis v1

Declared 2026-10-11 before new timing observations. The user requested diagnosis
of the three retained overruns, followed by selection of one narrow runtime fix
that preserves strategy, safeguards and the region 33 intervention at turn 40.
This stage selects a follow-up; it does not change or promote the policy.

## Retained evidence and limitations

Baseline commit: `684f325`. The enabled source is the exact 98,572-character
`v09-deferred-forecast-reuse-v1/artifact-on/Player.java`, SHA256
`3bd070b3802744778473e93cebed8148ba07a7b243bd744c8ac486076d304a2c`.
Keep all source, parameters, executable classes, prior reports and verdicts intact.

The strict failure is map 904148181/P0/turn 33: response 56.0151 ms, send
3.0814 ms, consumer 63.8926 ms. Child stderr separately reports 52 ms inside
CGRunner. Consumer wake-up alone therefore cannot explain this failure.
The old process check did not record a replay or failed input, so its exact
state, action and JVM history cannot be reconstructed from that report.

The nominal failures are 907135435/start 59/P1/PURE_WAIT/ON: turn 61 takes
99.5029 ms and turn 62 takes 64.5907 ms. Both record zero supplemental forecast
work. Their exact inputs can be reconstructed from the source replay and saved
ON action prefix. The old run did not collect CPU, GC, compilation or phase
timestamps. New instrumentation cannot retroactively establish its cause.

## Fixed diagnostic observations

Freeze the new tool source and exact artifact/input hashes after compilation and
preflight, before either diagnostic stage. No other CPU-heavy work runs alongside
the stages. Preserve every result, including missing reproduction and failures.

1. Run the complete previous nominal panel once: 56 cohort starts/144 conditions
   plus the historical start/3 conditions, same source ordering, retained native
   opponent portfolios and passive WAIT, original eight-turn horizon. Preserved
   OFF precedes optimized ON, each with its own generated-class loader, 500 ms
   disposable warm-up, fresh branch memory and 36 ms per decision. Policy classes
   remain byte-exact. Add tools-only JFR play markers, CPU and GC counter snapshots
   around the original reflective play call; keep state preparation, formatting
   and diagnostic copying outside that interval. Record JVM profile events and
   1 ms execution samples. This is attribution data, not an uninstrumented timing
   gate or a rerun that replaces the prior failed evaluation.
   After the panel, in the same recording, run one separate eight-input probe of
   the retained PURE_WAIT ON 907135435/start 59/P1 trace. Reconstruct every input
   and successor from the old recorded actions through core and require every
   old state hash to match. Use one fresh enabled loader, 500 ms warm-up and 36 ms
   decisions with persistent memory, but advance by the saved actions rather
   than the new choices. Label this forced-input attribution and keep it outside
   all mechanism/strength totals. This guarantees observing the two historical
   board inputs; it does not reproduce their JVM history or prove their cause.
2. Run one new diagnostic full game on map 904148181, candidate P0, frozen v09
   opponent, seed 7000. Use the exact exported candidate with child JFR, host IO
   diagnostics, full replay and every input/action retained. Set external grace
   to 5,000 ms solely to retain late replies and subsequent evidence; the child
   keeps its unchanged 1,000/50 ms allowances, 14 ms margin and 500 ms warm-up.
   Report all responses beyond the original deadline as overruns. Dump the child
   recording before closing it. This is a new timed trajectory, not the lost old
   strict trajectory, and cannot overturn the existing PROCESS_FAILED verdict.

Do not repeat either stage to obtain a favorable sample. A failed tool preflight
may be repaired before measurements; any interrupted diagnostic must be retained
and explicitly reported rather than silently replaced. No strength/boss games,
arena upload, push or strategy promotion belong to this investigation.

## Interpretation and selection

Separate observed wall time, coarse Windows thread CPU, JVM-wide GC counters,
recorded stop-the-world pauses and sampled stacks. A sample is not exact phase
duration; overlapping compilation is not proof that compilation caused a pause.
An absence of a sampled frame/event does not exclude it. Fixed order and fresh
loaders perturb JIT and GC behavior. Keep diagnostics separate from readiness.

Use the source audit plus observations to choose exactly one narrowly scoped
follow-up. Prefer removing provably redundant work or ensuring expired work
cannot start another expensive phase. Do not shorten forecast depth, reduce
reply coverage, weaken terminal/counterfactual checks, move the committed deadline,
increase the time allowance, shrink the existing margin or change strategy weights.
Any proposed cutoff must preserve the completed native construction and existing
fail-closed commitment handling. Do not promise a wall-time guarantee against
arbitrary JVM or host scheduling pauses.

The selected follow-up must include explicit acceptance checks: ample action,
forecast and state parity; exact full construction; unchanged six WIN controls;
actual native r33 intervention at turn 40; source below 100,000 characters;
and a separately declared zero-grace exact-artifact process check. A successful
instrumented observation alone is insufficient for any readiness claim.

## Result: diagnosis limited; one follow-up selected

**PARTIAL_ATTRIBUTION / FOLLOWUP_SELECTED / ORIGINAL_PROCESS_FAIL_UNCHANGED.**
No production code, artifact, strategy, safeguards, timer or selected version
changed. This investigation selects an experiment, not a verified 50 ms fix.

The five focused preflight tests passed. The single instrumented main panel
completed all 57 starts/147 comparisons: 294 complete arms, 1,141 OFF and 1,140
ON decisions. Raw episodes and history were retained. Two diagnostic-tool
failures limit the additional evidence; neither stage was repeated:

- The forced-input probe validated all eight saved boards and successors before
  measurement, but its incremental report used a CREATE_NEW writer twice. The
  second save failed. JSON retains turn 59; JFR retains calls at 59 and 60.
  The original problematic inputs at 61 and 62 were **not measured** by this
  probe. The overall nominal command exited 1 and has no final report.json.
  The main panel and JFR remain usable; the full declared stage is incomplete.
- The new process diagnostic failed at turn 0: a JFR startup information line
  entered stdout despite the requested logging configuration and was rejected
  as an action. Its apparent timing status OK measures the service line, not
  a valid bot reply. JFR.dump then timed out after 30 seconds, leaving a zero-byte
  recording. The exact reason for the attach/dump hang is unknown. The wrapper
  exited 0 after saving its report, which correctly says complete=false.
  There is no child CPU/GC trace, no new full game and no deadline-pass evidence.

These are failures in this investigation's tools, not new strategy failures.
The frozen tool sources are retained as the record of this failed diagnostic
revision. Do not reuse the forced-input persistence or child startup configuration
as a working harness without a separately tested repair. No production change
was made to accommodate either failure.

## What the three old overruns establish

The 56.0151 ms process response includes an independently logged 52 ms in the
child's CGRunner response cycle. The host consumer's later 63.8926 ms wake-up
is therefore not the sole cause. The previous turn's 10.9719 ms send also shows
transport/scheduling variability, without attributing it. The missing original
input and JVM trace prevent assigning the excess to a specific search phase,
GC, compilation, or host scheduling.

At the old 99.5029/64.5907 ms nominal calls, all extra deferred-forecast counters
are zero. The first had 67 route queries and zero completed comparisons; the
second had no route query. Consequently the previous supplemental-forecast
optimization cannot directly remove this work. The second call can time out
before route generation. There is no old phase/CPU/GC trace proving why.

In the new main panel, the same named start's ON/PURE_WAIT trajectory diverges
at action 60. Its calls at 61 and 62 take 17.4342 and 10.0870 ms, but have different
input hashes. They are **not reproductions** of the two old problematic inputs.

## New JVM evidence and code audit

The usable recording contains 2,283 play markers: 2,281 main-panel decisions
plus the two partial forced-input calls. There are 292 calls above the 36 ms
search budget and four above 50 ms, all at a different start, 907135538/60/P0:
native reply 1/OFF at turn 63 takes 52.5876 ms and turn 64 takes 291.0473 ms;
PURE_WAIT/ON at turns 61 and 63 takes 65.3131 and 52.6405 ms. Keep the 291 ms
outlier; no trimming or favorable rerun was performed.

All four have zero recorded GCPhasePause and safepoint overlap. Their coarse
thread CPU deltas are 15.625, 0, 15.625 and 0 ms; this Windows counter resolution
and its wider snapshot bounds cannot support an exact residual-time breakdown.
Compilation overlaps all four. One ExecuteVMOperation overlaps 118.9408 ms of
the 291 ms call, but its interval is not a measured pause of the play thread.
These observations do not establish that JIT caused the excess, nor do they
attribute the historical failures. Sparse execution samples show shortest-path
refresh/target selection and cheapest-path route preparation. Whole-recording
sample totals include warm-up and must not be presented as decision-only shares.

Filtering to the measured play markers gives 3,090 same-thread samples, with
SearchState.refresh in 1,244 (40.26%) and cheapestPath in 1,205 (39.00%). None
of the per-play retained stack tables is truncated; 466 calls have no samples.
Refresh appears under chooseTarget in 843 samples, placeOwn in 205 and resolve
in 95. These inclusive sample shares support prioritizing redundant refresh
work; they are not precise time shares or predicted speedups.

The source audit identifies uninterruptible work between timer checks:

- Initial target/ income/threat preparation; full route preparation and completion
  refresh within propose; a resolve includes canonical refresh, score and game-over.
- Remaining root copies/refreshes can start after the budget has expired. Final
  fill also prepares/scans its buffers before its timed candidate loop.
- CGRunner calculates remaining time before copying the input for bot.play;
  the search subsequently starts a relative timer. Its minimum-five-ms fallback
  can also request work after the outer allowance is spent. This is a real budget
  accounting gap, but does not explain in-process nominal calls on its own.

None of these locations has a measured old phase duration. Timer checks are
cooperative and cannot preempt an in-flight helper or a JVM/host pause.

## Selected follow-up: batch canonical path refresh by destination

Select **an allocation-free, opt-in batched SearchState.refresh**, with one BFS
per distinct destination town instead of one per requested town pair. This is
the only selected production follow-up; it is not implemented in this diagnostic.

Today SearchState.refresh repeatedly calls Pathing.shortestPath for every pair,
recomputing the same destination distance map. Add a core Pathing overload that
uses its existing dist/queue buffers, runs BFS once per destination, then calls
the existing canonical walk into SearchState's preallocated paths[p]. Retain
the original pair indices, N/E/S/W tie-breaking and null/zero-length convention.
No per-node allocation or town-by-cell matrix is needed. The existing public
updateAllConnections is not suitable unchanged: it allocates done[] and can
allocate path arrays. Keep the old/default API path and frozen opponents exact;
enable the new mode explicitly via Params in the future candidate.

This removes provably redundant work from initial target evaluation, construction
completion, native forecasts and supplemental forecasts. It addresses a broader
set of timer gaps than another supplemental-only optimization. It also frees
time before W/A/Wr0 rather than shortening their coverage. It does not optimize
cheapestPath or eliminate arbitrary JVM/host stalls, so its ability to pass 50 ms
remains an experimental question. Do not infer a total speedup from the reduction
in BFS count or from inclusive stack samples.

Alternatives considered but not selected: skipping expired root/fill preparation
may save only a small tail; changing deadline accounting does not cover nominal
in-process overruns; skipping untouched sabotage regions has narrower coverage
than destination batching. Changing depth, reply count, guards, deadline memory,
the margin or time allowance is excluded.

## Strategy observations and future acceptance

In this diagnostic, the required 907134651/start38/P1 native trajectory still
arms region 33 due 39, waits at 39, and intervenes at 40 in **28.8426 ms**.
W=1095, A=1084, Wr0=933; the single retained reply completes in all three forecasts, with the
same full construction [288]. The passive r33 intervention at 40 takes
28.4220 ms; the passive r11 intervention at 37 takes 32.9635 ms. Instrumentation
and a different runtime history mean these are not speedup estimates.
All eight OFF terminal wins remain ON wins; six named winning-closure controls
remain exact. No ON decision changes its native construction. These observations
validate retention in the unchanged artifact, not the unimplemented follow-up.

Before accepting the selected optimization, separately freeze and run:

1. Core differential tests for canonical ties, both owners, disconnected and
   restored paths, reuse of every output buffer and complete resolve; cgValidate.
2. Ample old-ON/new-ON parity across all 147 conditions: ordered full actions,
   state chains, bit-exact W/A/Wr0, terminal results, commitment and safeguards.
3. Nominal persistent choices from turn 38 through the actual r33 attack at 40,
   all full-build checks and six named winning-closure controls.
4. Exact export below 100,000 characters, submit/procCheck with zero grace and
   the fixed process panel. Retain every timeout; no deadline or guard relaxation.

Keep the deferred correction OFF and selected v09 unchanged. No strength/boss
trial, arena upload, promotion or push was performed.

[Machine-readable result](evidence/v09-runtime-cause-v1.json),
[archive index](evidence/v09-runtime-cause-v1-artifacts.json),
[retained evidence](evidence/v09-runtime-cause-v1-artifacts.zip).
