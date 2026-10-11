# v09 missed-deadline candidate packaging and runtime v1

Declared 2026-10-11 before new timing measurements. The user authorized making
the existing default-off candidate fit the submission limit without changing its
behavior, then checking whether the correction actually operates within the
production time allowance. This is a packaging and runtime check, not a strength
trial, strategy promotion, arena upload or a new search-policy experiment.

## Preserved baseline and allowed change

The exact candidate is
`current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true`, as measured in
[the preceding local screen](v09-deferred-commit-v1.md). Before packaging edits,
preserve its merged, uncompressed source: **108524 UTF-16 characters**,
SHA256 `da4487e574fee5bc691724d81157183f7319bc74a39a2a699d5cea5a2b9e0c56`.
It is an oversized comparison reference, not an accepted submission. Capture its
complete parameters, build marker, source, compiled classes and associated
repository source/classes/resources. The baseline has629 copied files; its
manifest also pins the preceding selection, full raw results, evidence and ZIP.
Compilation of this reference is allowed; registering or uploading it is not.

Only semantics-preserving source packaging is authorized: remove dispensable
comments/whitespace with a Java-aware transformation. Preserve identifiers,
operators, literals, token order, program statements, full parameters, selected
root/fill logic, guard recipe, deadline memory and simulation budgets. Do not
weaken a guard, reserve less time, reduce work, change search order or adjust a
deadline after seeing runtime outcomes. Unsupported lexical constructs must fail
closed. New packaging is restricted to this explicitly enabled candidate.

The disabled default current merge must remain98709 characters, SHA256
`ab0954a4545f25222fce6bccf8fa11866d276266e96b4728b502c83996d847e5`;
the frozen-v09 merge must remain SHA256
`aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac`.
The previously saved selected `build/submit/Player.java` remains the distinct
95278-byte export, SHA256
`1df7c131e6069f140b65d107491f5acbab855fb1fd88a6b1d567fc02e396be19`.
Write the new candidate only to a dedicated runtime-experiment submission
directory. Never replace the selected export or change the default-off flag.

## Equivalence before timing

Require all of the following before timing the packaged artifact:

1. The normal FileMerger limit check accepts the final candidate at no more than
   100000 UTF-16 characters, and its manifest/source/classes identify the exact
   artifact later passed to the process checks.
2. Independently verify the Java token sequence against the preserved
   uncompressed source, permitting only the build-marker literal to differ.
   Normalize that provenance literal alone and compile both variants with the
   same Java17 compiler/options and without debug position tables; require
   identical class-file names and bytes. Changing the marker must not conceal
   any other literal or executable change.
3. Focused scanner/packaging tests cover comments, whitespace, strings/chars,
   escaped contents and token boundaries, including accidental operator or
   identifier joining. Default and frozen export pins remain exact.
4. Token and normalized-bytecode identity establish packaging equivalence; the
   existing ample-time traces remain reference outcomes only. There is no new
   empirical ample-time panel. Actual36ms decisions may differ from those ample
   traces because search completion depends on elapsed time; that is the runtime
   question, not grounds to adjust the policy or discard a result.

Engine and source-state fidelity remain prerequisites. Preserve the existing
exact292 baseline validations and check all40 source replays plus the historical
replay used by this experiment. Retain every preflight failure and correction;
source/selection/protocol and the final executable are frozen before timing.

## Fixed runtime sample

Reuse exactly the preceding56 cohort episode starts and separate historical
start904554072/t37/P0. Their source-state hashes, labels and initial opponent
reply identities come from the pinned preceding selection. No new timing result
may add, remove or replace a start. The cohort spans23 games; native conditions
provide88 comparisons and pure-WAIT conditions56. Historical native replies add
two comparisons and passive WAIT adds one. Overlapping starts and opponent
replies are correlated; they are not independent games or strength observations.

For each start/regime/reply, create separate OFF and ON arms. OFF uses
`current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=false`; ON uses the preserved enabled
specification. Instantiate their generated nested bot classes from the exact
separate compiled submission artifacts through isolated classloaders. Bridge
the state without changing any field, including the actual absolute turn.
Own commitment memory starts empty and persists within each branch for at most
eight resolved turns, including the initial turn. This yields144 cohort pairs
plus three historical pairs: **147 paired comparisons**,294 own branches.
There is no replay-prefix priming, no teacher-forced execution of old candidate
actions, no forced initial attack, no repeated reset on successive states and no
horizon extension to finish a deadline. Native opponents retain the exact
initial frozen portfolio/reply and recompute on each branch's current board;
passive WAIT remains secondary. Opponent actions are chosen from the same
pre-turn state without observing the simultaneous own decision.

The nominal-search panel uses **36ms on every measured decision**, including the
first decision at a retained midgame start. This equals50ms minus the existing
14ms margin. It is not a full CG-process deadline: parsing, state preparation,
JIT history, scheduling and GC differ. Each arm receives initialization plus
**500ms disposable warm-up**, preserving fresh empty live commitment memory.
Measure the play call only; state copying, action formatting and diagnostic
extraction occur outside that interval. Use source-audit game order and ascending
turns within each game, native replies in their saved order, then passive
WAIT, and OFF before ON for each comparison. Keep CPU-heavy work separate from
all timed stages. Do not silently mix first-turn486ms allowances into this
midgame panel. This fixed order has possible JIT/scheduling effects; do not
interpret a wall-time difference between OFF and ON as an unbiased speedup.

Record native search completion, W/A/target-specific-Wr0 completion, guard status,
stored target/due, action, preserved construction, emitted interventions and
elapsed time. An incomplete forecast is a coverage limitation and must preserve
the existing fail-closed behavior. Do not infer the mechanism ran merely because
the process returned a legal native action before its deadline.

The required positive reference remains907134651/start38/P1, region33: at ample
time the commitment due39 produces a hit40 instead of42. Report whether that
same correction actually occurs at the nominal budget and whether the stored
deadline/guards remain valid. Region11/start35 under native replies may remain
unchanged, as before. Report the six previously winning native controls in
907134720/P1, starts46,47,49,50,51,52 individually, plus historical904554072.
Faster execution of a target does not by itself establish a game win.

## Strict exact-artifact process panel

Run exactly eight games: four established saved process maps, each twice with
candidate seats0 then1, in this fixed order:

1. `replays/cg/903510176.json`;
2. `replays/cg/boss/904146009.json`;
3. `replays/cg/boss/904148181.json`;
4. `replays/cg/boss/904147739.json`.

Use the selected frozen opponent v09, four sequential two-game ProcessCheck
calls, seeds7000/7001 in each call, one game at a time. Pass the explicit
candidate submission directory. Use strict1000ms first-turn/50ms later IO
deadlines and **zero grace**, with optional IO diagnostics disabled. Do not
subtract the margin twice: CGRunner already applies it to its search budget.
Pin all four replay bytes and the exact candidate source, manifest and classes.
Run this strict process panel before the nominal36ms panel. Complete the
independent nominal panel once even if the process panel has a failure; its
mechanism result cannot rescue failed strict readiness.

Complete all eight once, preserving every intent/log/report even after a
failure. No replacement maps, restarts until success, extensions or favorable
subsamples. Report errors, invalid outputs, timeouts, nominal overruns and
response-time distribution/maxima by first/later turn. These eight games check
process readiness, not candidate strength; their win rate is not a promotion
criterion. A process pass alone does not establish that the missed-deadline
correction ever activated.

The targeted mechanism panel uses the exact compiled Player bot classes through
the declared bridge, rather than sending a midgame board as CGRunner's turn0.
Such a reset would silently change turn-sensitive behavior. Its actual absolute
turns and fresh live memory must be preserved. This classloader panel does not
measure stdin parsing, stdout transport or end-to-end50ms process response;
those aspects belong to the separate eight-game ordinary ProcessCheck.

## Decision and reporting boundary

Report three separate outcomes: packaging equivalence/size, strict process
readiness and mechanism coverage at the nominal budget. A size or equivalence
failure prevents timing a different policy. Any strict process failure prevents
a readiness pass. A runtime mechanism claim requires actual valid interventions
on the declared histories; timing out safely or falling back everywhere is not
mechanism success. Preserve all terminal controls and report bounded earned
score/terminal outcomes separately from forecast utility.

If the candidate fails to fit, fails equivalence or does not activate reliably
within the allotted time, stop with that result. Do not change the policy,
search work or safety rules during this experiment. Selected v09 remains
unchanged. No automatic strength trial, boss request, promotion or upload follows
any local success. A later optimization or strength experiment needs its own
prospective scope.

## Results — 2026-10-11

**Packaging passed; strict process readiness failed; the required native-opponent
correction was not reproduced at36ms.** Both declared panels were completed once,
without policy changes, replacement cases or timing retries. Selected v09 and
the default-off setting remain unchanged.

### Packaging and preflight

The enabled source shrank from108524 to **98401 characters**, saving10123 and
leaving1599 below the100000 limit. The accepted artifact is
`build/analysis/v09-deferred-commit-runtime-v1/artifact-on/Player.java`, SHA256
`52bc192dabca0ea648f75c19f80ceda6fb70535c24dcf3c5c076e7286940d7e4`.
The transformation removes comments and redundant whitespace outside literals.
No policy parameters, identifiers, literals,
operators or statements changed, apart from the provenance marker generated for
the new source representation.

After emptying only that marker literal, all30526 Java tokens match the retained
reference and **all24 compiled class files are byte-identical** with Java17,
UTF-8, `--release 17 -g:none`. Default current and frozen-v09 merged hashes remain
exact; the previously saved selected export was not overwritten. ON and OFF
artifacts have separate experiment directories and source/class pins.
The actual OFF export uses `current?NOISE=0`, the equivalent default-false
shorthand for the protocol's explicit
`current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=false`; this does not change its
effective parameters or generated source.

Preflight passed83 unique focused tests and292/292 engine replay validations.
The first test pass exposed one Unicode test-fixture encoding error; replacing
that fixture with an encoding-independent equivalent made all four scanner tests
pass. The compactor implementation and candidate policy did not change in that
repair. Both initial and repaired evidence are retained. Independent
reconciliation checked all **927 frozen input pins** unchanged before and after
reading the results, and all **629 preserved pre-edit files** remain exact.

### Strict process panel

Seven of eight games passed. The last game, map904147739 with the candidate asP1,
timed out at turn54: the recorded response arrived at **51.0289ms**, beyond the
strict50ms deadline. There was one timeout and one nominal overrun. The other
633 recorded turn responses were OK. Calls1–3 exited0; call4 exited1. No grace or
retry was used, so process readiness is **FAIL**.

Across626 later-turn responses, the median was37.4157ms, p95 was38.6627ms, p99
was39.9812ms and the maximum was51.0289ms. The eight first responses ranged from
563.7489 to664.3543ms against their1000ms allowance. These are host-observed IO
response times, not isolated bot CPU or search times. The single timeout does not
identify a unique scheduling, input, GC or search cause.

### Nominal36ms mechanism panel

All56 cohort starts completed144 OFF/ON comparisons; the separate historical
start completed three more. Each branch used its exact generated artifact,
fresh live memory and500ms disposable warm-up. All recorded interventions passed
the fixed deadline/target/reply guards and retained the complete native cells.

There were **zero interventions in88 native-opponent comparisons**. The required
region33 case907134651/start38/P1 armed due39 and waited at39. At40, W and A
completed, but the target-specific Wr0 forecast did not complete before the
timer expired; the guard cleared the commitment without attacking. At41 a new
commitment due42 was armed, and native policy attacked33 at42. OFF and ON both
ended nonterminal at score difference1026. The previous ample-time ON hit40 and
its+45 effect were therefore not reproduced under the required native condition.

Across669 native ON decisions,58 stopped the correction through incomplete
forecasts or timeout:42 incomplete-W, seven incomplete-Wr0 and nine timeout.
No incomplete-native-search or incomplete-A status occurred in that group.
Maximum measured play time was41.3811ms ON and37.9404ms OFF;180 ON and140 OFF
calls exceeded36ms, although none exceeded50ms. This play-only result does not
override the separate end-to-end process timeout.

The56 passive-WAIT comparisons contained two valid interventions:

- Region33/start38 attacked at40 after due39, with full cell288 unchanged.
  Its measured play call took34.5480ms; OFF/ON bounded score differences were
  1026/1074, a+48 difference in this timed pair.
- Region11/start35 attacked at37 after due36, retaining cells159,162,182.
  Its measured call took35.5159ms. OFF also attacked11 at37 under its own timed
  search; both arms ended at-551. The intervention did not improve this pair's
  hit time or bounded score.

Passive ON had38 incomplete/timeout decisions among448:29 incomplete-W, one
incomplete-A, five incomplete-Wr0 and three timeout. These passive-only
activations do not establish the required native mechanism coverage.

### Controls and interpretation

All six native terminal-win controls retain the exact ample final state in both
arms, with no interventions:

- 907134720/P1 start46: WIN at48, score difference1971, first hit47.
- Start47: WIN at49,1969, first hit48.
- Start49: WIN at50,1967, first hit50.
- Start50: WIN at51,1965, first hit51.
- Start51: WIN at52,1963, first hit52.
- Start52: WIN at53,1961, first hit53.

Native terminal pairs were77 NONTERMINAL/NONTERMINAL, eight WIN/WIN and three
LOSS/LOSS: no OFF terminal win was lost and no new ON terminal loss appeared.
All56 passive pairs remained nonterminal. Historical904554072 had no
intervention: both native replies still ended at-2012 in each arm and first
attacked29 at38; passive OFF ended-1834 with its first hit39, versus ON-2012 and
hit41. None of those historical final state hashes equals its ample counterpart.

The native bounded-score signs were13 positive,18 negative and57 tied despite
zero interventions. Passive signs were seven positive, five negative and44 tied.
Only36 native OFF finals and37 native ON finals match their ample reference;
OFF and ON final states match each other in43/88 native pairs. These differences
show budget-sensitive native search and fixed-order JIT/scheduling effects; they
cannot be attributed solely to the correction or presented as a strength gain.
In particular, the passive+48 is a timed trajectory difference, not a clean
estimate of the correction's isolated effect.

Decision: retain the useful packaging change and the experimental default-off
policy, but **do not claim runtime readiness or native mechanism success**.
The source-size blocker is resolved; the strict process failure and missing
required36ms correction remain. No optimization, timing retry, strength bench,
boss request, strategy promotion or upload followed these results.

Compact [evidence](evidence/v09-deferred-commit-runtime-v1.json),
[archive index](evidence/v09-deferred-commit-runtime-v1-artifacts.json) and
[retained artifacts](evidence/v09-deferred-commit-runtime-v1-artifacts.zip).
