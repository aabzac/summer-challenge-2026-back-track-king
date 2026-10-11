# v09 supplemental forecast reuse v1

Declared and frozen before new measurements, 2026-10-11. The user authorized
reducing repeated forecast work without changing strategy or safety guards,
followed by fixed correctness and runtime checks. No strength trial, live boss
request, strategy promotion or arena upload is included.

## Preserved baseline and scope

Before production edits, preserve HEAD
`4d8918d85fb6ef7efa9cca5777c6149f9be42bb6`, including693 copied files: relevant
sources, compiled classes, resources, the selected export and exact enabled and
disabled runtime artifacts. The manifest is
`build/analysis/v09-deferred-forecast-reuse-v1/baseline-before-optimization.json`,
SHA256 `bf398162f23d1c85573078c1db512522fe318c1cb2135a24792074c9ef495e88`.
It pins the preceding complete protocols, raw results and evidence archives.
Unrelated pduhard/ppo work is excluded from preserved payload.

The enabled reference is
`current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true`,98401 characters, SHA256
`52bc192dabca0ea648f75c19f80ceda6fb70535c24dcf3c5c076e7286940d7e4`.
Its source, full parameters and executable classes are copied to the new
experiment's `reference-enabled` directory. This is the exact prior runtime
artifact, not a reconstruction from modified source. The preceding disabled
artifact is also preserved and supplies the OFF arm of the new nominal panel.

Fresh pre-edit merge checks confirm the default current source remains98709
characters, SHA256
`ab0954a4545f25222fce6bccf8fa11866d276266e96b4728b502c83996d847e5`;
frozen-v09 remains SHA256
`aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac`.
The saved selected95278-byte export remains SHA256
`1df7c131e6069f140b65d107491f5acbab855fb1fd88a6b1d567fc02e396be19`.
Never overwrite it. Candidate artifacts go to a new experiment directory.

## Semantic boundary

Optimize exactly one duplicated operation inside supplemental deadline
forecasts: when `moveCount == 0`, skip `targetBoard.set` and `placeOwn.refresh`
and let `chooseTarget` read the already canonical `work.state`. With no proposed
own construction, that source board is the same board the copy/refresh would
produce. This shortcut applies only to supplemental forecasts. Native search,
nonempty-construction paths and existing shared/core behavior remain unchanged.
There is no forecast-prefix cache, cross-decision reuse or new search parameter.
The hot path stays bounded and allocation-free.

Preserve all effective parameters, native search order, complete ordered
construction cells, opponent portfolios/reply order, continuation behavior,
scoring, terminal detection and target-specific suppression. Preserve the fixed
same-target first-future-hit rule, W terminal-WIN/DRAW veto, strict `turn > due`,
latest initial due across replies, no deadline rolling, A terminal-LOSS veto and
strict per-reply `A > Wr0`. Wr0 must still suppress only future own attacks on the
pinned target. Memory clearing and isolated warm-up remain unchanged.

No shorter forecast depth, fewer replies, weaker guard, altered time reserve,
different target, search-order change or early guessed acceptance is allowed.
Incomplete computation must still fail closed. Reducing redundant work may
allow more complete computation under a fixed timer; that is the optimization
being tested, not permission to change the underlying complete forecast result.

All existing common diagnostics, including counters, must retain their ample
values. An optional newly introduced physical-operation counter may differ from
an absent baseline field, but cannot replace a semantic equality check.
Complete semantic diagnostics must retain their values: native
choice, full cells, nominee, reply identity, forecast utilities and terminal
codes, first future hit/absolute due, pending/committed target and deadline,
guard status, and whether an intervention is emitted. Floating values are
compared bit-exactly; missing and incomplete forecasts cannot count as equal
completed semantic evidence.

## Fixed ample-time equivalence panel

Use all56 cohort starts and the separate historical904554072/t37/P0, exactly as
selected in the prior runtime experiment. The cohort has88 retained-native and56
pure-WAIT comparisons; history adds two native and one passive comparison:
**57 starts,147 opponent conditions**. Preserve the original state hashes,
initial reply identities, source-audit ordering and old labels. No result may
add, remove or replace a start.

For each condition, run two exact compiled artifacts: preserved enabled
reference and optimized enabled candidate. Use fresh live memory per branch,
persistent within at most eight resolved turns. Both branches start from the
same known state and use the same retained initial opponent policy; opponent
actions depend only on the simultaneous pre-turn board. Passive WAIT and the
historical case remain separate. No prefix priming, forced attack, teacher-forced
candidate history or extended horizon is allowed.

Use60000ms per own decision and **no warm-up** on either ample arm, solely for
complete-behavior equivalence. Require both artifacts to
complete supplemental forecasts whenever the baseline does, and require exact
actions, ordered cells, state hashes, scores, terminal outcomes and semantic
deadline/forecast diagnostics across the full corresponding histories. A value
comparison alone is insufficient. Unsupported or incomplete rows must not be
dropped. Synthetic tests must exercise any reuse invalidation boundary and both
seats, including partial opponent hits and changed construction.

An equality failure is a correctness blocker: preserve it and repair only an
implementation error without changing the declared semantic recipe. Keep every
preflight attempt. Do not inspect timing outcomes until correctness is complete
and source/protocol/executable pins are frozen. The final optimized artifact
must pass the normal100000-character export limit, submission-only compilation
and unchanged default/frozen export pins. Preserve engine validation evidence
for292 baseline replays,40 source games and the historical replay.

## Fixed timed panels

After the ample equality gate and final freeze, run the candidate through the
same eight strict ProcessCheck games as the preceding runtime check: maps
903510176,904146009,904148181,904147739 in that order, candidate seats0 then1 on
each, four sequential two-game calls, seeds7000/7001 per call, opponent v09,
1000/50ms IO deadlines, zero grace and no optional IO diagnostics. Pin exact map
bytes, source, manifest and executable class hashes. Complete all eight once
even after a failure; retain every report and do not retry or replace a case.

Then complete the fixed147-condition nominal36ms panel once, irrespective of
strict-process success. Compare the preserved **OFF** artifact against the
optimized enabled candidate, with a fresh isolated generated-class
loader per arm and500ms disposable warm-up. Each live bot starts with empty
memory and persists through its own consecutive branch states. Measure play
only; state preparation, formatting and diagnostics remain outside the measured
interval. Use source-audit game order and ascending turns, retained native reply
order then passive WAIT, OFF before ON per condition, and historical last.
The ample panel uses the same order with previous ON before optimized ON.
Report the resulting JIT/scheduling limitation. Do not treat nominal timing as
end-to-end50ms process emulation.

Count incomplete native/W/A/Wr0 stages, timeouts, complete supplemental work,
valid interventions, search time distributions and calls exceeding36/50ms.
Verify each intervention against all original guards and exact native cells.
Compare the required907134651/start38/P1 region33 case, both907135823/start35/P0
conditions, historical904554072 and all six907134720/P1 terminal-win controls
at starts46,47,49,50,51,52. Report every observed terminal and bounded-score
change, including negative ones; timings and score changes are not strength
estimates. Previous runtime OFF and ON trajectories are pinned context; the new
nominal OFF trajectories are fresh measurements, not reused old observations.

## Decision boundary

Report separately complete-behavior equivalence, source size, strict process
readiness and nominal mechanism coverage. Any ample semantic mismatch prevents
a correctness pass. Any strict process timeout/error prevents a readiness pass.
Mechanism success requires a valid actual correction in the required native
region33 case, not merely successful forecasting or passive-only activation.
Do not rescue a failed stage with another stage's result.

More complete forecasts or a faster work counter do not prove an arena gain.
The selection is exposed development evidence, starts/replies are correlated,
and fresh classloaders with fixed execution order can produce different timed
native decisions. A passed local panel would only support the next separately
authorized check. A failed panel ends this fixed evaluation; no outcome-guided
guard/search changes, repeated timing until success, automatic strength trial,
promotion or upload follows.

## Recorded results, 2026-10-11

**Ample equivalence passed and the required native correction occurred within
the nominal search budget. Strict process readiness failed.** The enabled
candidate remains experimental and disabled by default. The fixed evaluation
ended after its declared stages; there was no timing retry, outcome-guided
change, strength test, live boss request or promotion.

The original declaration above remains byte-exact in
`build/analysis/v09-deferred-forecast-reuse-v1/prospective-protocol.md`, SHA256
`13a185660f41c454319fc5cf365520c792d8b4cb54c9fd2246191b85a7654f19`.
Selection SHA256 is
`85f081b6997d73cd3d1128ddc3a5258111478477b7eb389c3fe4905a3dd4d622`.
After all runs, independent reconciliation verified all **918 frozen inputs**,
all **693 preserved baseline files** and all three separately sealed analysis
script hashes unchanged. No strategy or selection edits followed measurement.

Preflight passed 31 focused tests on the first attempt and reproduced all
292 baseline engine replays exactly. The fixed source cohort and historical
replay retain their verified provenance. The exact enabled export
`current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true` compiles independently and is
**98,572 characters**, leaving 1,428 under the 100,000 limit; SHA256
`3bd070b3802744778473e93cebed8148ba07a7b243bd744c8ac486076d304a2c`.
It is 171 characters larger than the preserved enabled reference. The default
current merge, frozen v09 merge and saved selected export retain the three
hashes recorded above. Runtime OFF uses the exact preserved disabled artifact,
whose `current?NOISE=0` specification is equivalent to spelling the default
`USE_ROLLOUT_MISSED_DEADLINE=false` explicitly.

### Complete-behavior equivalence

All **147 previous-ON/new-ON conditions**, comprising 56 cohort starts and the
separate historical start, passed. Across **1,140 decisions per arm**, ordered
actions, construction, state chains, scores, terminal outcomes, all common
semantic diagnostics and counters agree exactly; finite floating diagnostics
agree bit-for-bit. The declared exclusions are artifact identity, measured wall
time and the new `deadlineReusedBoards` counter. This is exact equivalence on
the fixed panel, not a universal proof.

The new counter records **9,968 skipped board-copy/connection-refresh
operations** across 452 decisions: 4,695 in the 90 native conditions including
history, and 5,273 in the 57 passive conditions including history. Both arms
produce the same three corrections: native region 33 at turn 40, passive region
33 at turn 40 and passive region 11 at turn 37. Each correction skips 45 such
operations. These counts describe removed physical work; the semantic forecast
turns and comparisons are unchanged. They do not measure an isolated wall-time
speedup.

### Strict process check

All eight prescribed games were attempted once. Seven passed; the candidate
timed out on **map 904148181, P0, turn 33**, responding in **56.0151 ms** against
the 50 ms limit with zero grace. There were 508 OK responses and one TIMEOUT.
Across 501 responses after the first turn, median response time was 37.7327 ms,
p95 38.8172 ms, p99 43.8894 ms and maximum 56.0151 ms. The eight first responses
were within their separate 1,000 ms allowance, maximum 716.399 ms.

All process reports pin the exact candidate source above and executable-class
SHA256 `9f331092f279670c9d1e1d3ee2a9311c2e52a4f455c3735a65afbbc2421fb478`.
This stage is **FAIL**, irrespective of the nominal mechanism result. These
observations do not identify the cause of the overrun.

### Nominal 36 ms mechanism panel

The complete panel contains **88 native and 56 passive cohort comparisons**,
plus two native and one passive historical comparisons. It records 1,141 ON
decisions; 377 use the shortcut, skipping 7,330 board-copy/refresh operations.
Every recorded intervention preserves the exact ordered native construction,
passes every original guard and respects the fixed committed deadline.

The required **907134651/start 38/P1, region 33** native condition now intervenes
at turn 40 in **32.8008 ms**. The original due remains turn 39; `40 > 39` is the
declared overdue trigger. W, A and Wr0 each complete the sole retained reply,
with values 1,095, 1,084 and 933 respectively and no terminal veto. Cell 288 is
preserved. This decision skips 45 copy/refresh operations while retaining all
24 supplemental forecast turns. OFF first hits region 33 at turn 42, ON at
turn 40; bounded earned-score differences are 1,033 versus 1,074, a **+41**
contrast. Both branches remain nonterminal. This is the required actual native
correction; it is not an on-time hit by the original due, a new terminal win,
or a guarantee that future timed calls will succeed.

The two passive interventions also occur: region 33 at turn 40 in 30.137 ms,
and region 11 at turn 37 in 34.1071 ms. Their OFF/ON bounded score differences
are 1,026/1,076 (+50) and -598/-551 (+47), respectively; all four branches
remain nonterminal. Native **907135823/start 35/P0, region 11** still has no
correction: OFF hits at turn 36, ON does not hit within the horizon, and the
bounded scores are -470/-714 (-244). These timed contrasts include native
search differences and do not isolate the optimization's score effect.

In the 88 native cohort comparisons, bounded ON-minus-OFF score contrasts are
15 positive, 14 negative and 59 tied. Terminal labels are 76
NONTERMINAL/NONTERMINAL, eight WIN/WIN, three LOSS/LOSS and one
LOSS/NONTERMINAL. The last is **907134258/start 86/P1**, with no intervention
and scores -864/-868: the ON branch is censored at the horizon, not a recovered
win. None loses an OFF terminal win or introduces a new ON terminal loss within
this horizon. Passive contrasts are five positive, six negative and 45 tied;
all 56 pairs remain nonterminal.

Native ON records 37 incomplete/timeout decisions: 29 incomplete W, one
incomplete Wr0 and seven timeouts. Passive ON records 31: 17 incomplete W,
three incomplete A, three incomplete Wr0 and eight timeouts. History adds one
incomplete A. The preceding runtime run had 58 native and 38 passive such
decisions; this descriptive comparison uses separately timed runs and is not
a controlled causal speedup estimate.

Among the 669 native cohort play calls per arm, OFF/ON medians are
24.629/25.6949 ms, p95 36.2263/36.2217 ms, p99 36.7516/36.578 ms and maxima
38.9221/43.9193 ms. OFF has 104 and ON 125 calls over 36 ms; neither has a
native cohort call over 50 ms. Among the 448 passive calls per arm, OFF/ON
medians are 21.2374/22.8676 ms, p95 36.1837/36.2569 ms and maxima
40.5996/99.5029 ms; 64/92 exceed 36 ms.

There are **two separate passive play-only ON overruns above 50 ms**, both in
907135435/start 59/P1: turn 61 is 99.5029 ms (`native-attack`) and turn 62 is
64.5907 ms (`timeout`). Both have zero supplemental forecast work and zero
reuse. They are not the strict process timeout above, and this evidence does
not establish their cause. Nominal play-only timing excludes input/output and
cannot certify the process deadline.

### Retained controls and historical case

All six native terminal-win controls in **907134720/P1** remain WIN/WIN with no
intervention, exact final states matching both the old ample and preceding
runtime runs. Starts 46, 47, 49, 50, 51 and 52 retain scores 1,971, 1,969,
1,967, 1,965, 1,963 and 1,961 and first target hits at turns 47, 48, 50, 51,
52 and 53 respectively. Their passive counterparts remain nonterminal and
unchanged at scores 1,959, 1,957, 1,953, 1,951, 1,949 and 1,947. These are six
correlated starts from one game, not six independent games.

Historical **904554072/start 37/P0** has no intervention in any of its three
conditions. Each native reply retains OFF/ON scores -2,012 and first target 29
hit at turn 38; the passive condition retains -2,190 with no target hit. All
remain nonterminal. ON final states match old ample in all three conditions;
OFF does so in two. Its sole incomplete A occurs in the passive condition at
turn 39 (36.0953 ms). No history play call exceeds 50 ms.

Across the 144 cohort conditions, ON final states match the preceding runtime
run in 64 cases and OFF in 66. No previous ON terminal win is lost and no new
ON terminal loss appears, including history. Exact ample equality does not
imply identical actions under a real timer: execution order, fresh classloaders,
JIT and scheduling may affect when native search stops. The raw pair records
retain every positive and negative score contrast. No timed score or outcome
contrast here is a strength estimate.

### Decision and evidence

Record **semantic parity PASS**, **source-size PASS**, **required native
mechanism observed**, and **strict process readiness FAIL** separately. Keep
selected v09 and the default-off flag unchanged. The optimization preserves
the measured complete behavior and removes repeated work, but it does not yet
make the enabled candidate ready for the 50 ms process limit. A further
investigation would need its own declared scope; this failed readiness stage
does not authorize a retry or a strength/promotion trial.

`docs/evidence/v09-deferred-forecast-reuse-v1.json` is the compact record.
The artifact archive and its index retain the original prospective declaration,
selection, 693-file baseline, 918-file freeze, exact source and classes for all
arms, preflight and engine evidence, every ample/nominal/process result,
reconciliation and independent review. Prior archives and external runtimes
are hash-pinned instead of recursively embedded; unrelated pduhard/ppo payloads
are excluded. The primary independent reconciliation is SHA256
`2363b02652002b3729188bde0c8db3374c3586fdde244cfcbd083bf5cd464f63`.
