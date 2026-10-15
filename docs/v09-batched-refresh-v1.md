# v09 batched refresh: prospective experiment v1

Declared 2026-10-15 before measurements. This experiment tests one default-off
implementation optimization on clean v09. The user authorized checking whether
batched refresh improves complete-game performance. The previous missed-deadline
candidate and its failed strength/process results remain historical evidence.

## Fixed recipes and question

- Candidate: `current?NOISE=0.1,USE_BATCHED_REFRESH=true`.
- Selected frozen control: `v09?NOISE=0.1`.
- Implementation reference: `current?NOISE=0.1` (batching off).

`USE_ROLLOUT_MISSED_DEADLINE` is false in all arms. Candidate and current-OFF full
parameters must match, with only `USE_BATCHED_REFRESH=true` added to the candidate.
Frozen v09 omits two unsupported current-only fields from its serialized manifest:
`USE_ROLLOUT_ALL_CELL_RISK=false` and `USE_ROLLOUT_ROOT_FILL=false`. Those exact
inactive values are the only allowed serialization difference; all remaining
parameters must match. This clarification followed a read-only configuration-audit
rejection before any study or game; its successful parameter captures are retained
and reused without rerunning or replacing them. No strategy/configuration changed.
Frozen
v09 source, scoring rules, search/guard decisions, timers and margin stay fixed.
Within each refresh, the optimization shares one breadth-first search among
connection pairs with the same destination. It does not skip whole refreshes or
add the deferred-sabotage correction, forecast caching,
new actions or altered evaluation weights. No parameter tuning follows results.

The question is whether the same intended search semantics, with less repeated
refresh work, improve decisions under the existing time budget. Timed actions
can change because a different amount of search finishes. Correctness evidence,
process readiness and local strength remain three separate outcomes.

## Preflight and captured-state study

Run focused correctness/submission tests and engine replay validation before
trusting measurements. Preserve the selected `build/submit` artifact. Export and
compile three independent artifacts into this experiment's `artifact`,
`artifact-off` and `artifact-v09` directories, respectively. The source limit is
100,000 Java UTF-16 characters; pin each exact source, manifest and executable.

The fixed recorded-state study uses games907134258,907134581,907134651,907134720,
all recorded nonterminal decision states and both seats. It compares all three
exact artifacts, preserving source states and rotating arm order. Use ample
5000ms search allowance without warmup for semantic comparisons and nominal486/36ms
allowances for separate timed observations, with isolated500ms disposable warmup.
Retain every state, action and diagnostic, including differences and incomplete
searches. Do not select states using new outcomes, repair code from these results
and silently restart, or infer complete-game strength from captured positions.
The study writes `build/analysis/v09-batched-refresh-v1/study/report.json`.
Any unexplained semantic discrepancy blocks strength interpretation and must be
reported before deciding on a separately declared follow-up.

Register the protocol and exclusions before this study. After preflight and the
fixed study, freeze the final source/classes/resources, full configurations,
runtime, all three artifacts, study evidence and an immutable copy of this
prospective text. The final report appends results without replacing that copy.

## Exact-artifact process sample

Run the enabled artifact through the existing `gradlew procCheck` implementation,
four sequential calls of two games each, in this fixed order:

1. `replays/cg/903510176.json`.
2. `replays/cg/boss/904146009.json`.
3. `replays/cg/boss/904148181.json`.
4. `replays/cg/boss/904147739.json`.

Each call uses the candidate once in each seat, seeds7000/7001, opponent `v09`,
zero grace and IO diagnostics off. The opponent's resolved parameters must equal
the explicit `v09?NOISE=0.1` strength control. Use the experiment-specific enabled
submission directory; never regenerate or substitute an artifact between calls.
There is no JFR or extra instrumentation. Complete all eight declared games once,
retaining failures; do not retry a map or increase grace. Infrastructure failure
without a complete report remains an incomplete sample, not a process pass.

Require no bot errors, timeouts or nominal overruns for this sample to pass.
Record reader-stamped response times separately from host send/consumer times.
These are full1000/50ms process deadlines; the in-process opponent uses486/36ms.
An eight-game pass is limited local evidence, not a universal timing guarantee.

## Registered practical-v4 local sequence

Use unchanged `tools/run_practical_v4.py` and the v4 decision rule:

- Block1:300 games on150 paired maps, seeds251015000..251015149.
- If pooled score is at least50%, block2 adds200 games on100 maps,
  seeds251115000..251115099.
- If pooled score is still at least50%, block3 adds500 games on250 maps,
  seeds251215000..251215249.
- Stop strictly below50% at cumulative300 or500 games. Survivors complete1000;
  the local threshold is >=55% over all1000. There is no early pass.

Register all three pools before play. Each map appears in both seats with fresh
bots. Wins count1, draws0.5, losses0. Use exactly two workers and the unchanged CG
nominal486/36ms search budgets. No other CPU-heavy work runs concurrently. The
local runner does not include protocol parsing or the submission's500ms warmup;
its slow calls cannot certify50ms process readiness.

Exclude every available prior benchmark map identity, the verified retained
development-map index, current canonical saved CG replay maps and the40 retained
v09 source maps. Identity is SHA256 of UTF-8 `Protocol.initBlock(initialState,0)`.
Validate all input hashes, reject registered seed-interval overlap, and require
new maps to be disjoint from exclusions and earlier blocks. Missing or malformed
inputs fail; do not replace rejected seeds. Record actual parameters, timing
profile, ordered seeds, paired counts, errors and map identities per block.

The one fixed local strength sequence may run after a complete process sample
fails, because the user requested evaluating improvement. Such a run remains
`LOCAL_ONLY / NOT_READY_PROCESS_FAILED`, even if local strength passes. Bot or
engine errors, input drift or an incomplete process sample stop execution.
Preserve all outputs; no outcome-guided changes, retries, extra arms or sample
extensions. A futility stop ends local evaluation immediately.

## Scope and completion

`tools/diagnostics/v09_batched_refresh.py` provides separate preparation,
freeze, process, strength, verification and summary steps. It reuses the existing
v4 runner and read-only utilities; old experiment tools are not edited.
Hash-check frozen inputs before/after process calls and each strength block.

No boss games, uploads, pushes, strategy promotion or version selection changes
are authorized by this experiment. A local pass is only a local result; full
promotion policy still requires valid boss evidence and process readiness.
Record all results and limitations in BOTS/DEVLOG/PLAN, preserve compact evidence
and relevant raw artifacts, and commit the completed experiment locally,
including failure or inconclusive outcomes. Prior unrelated pduhard/ppo payloads
remain excluded; their map identities may be retained as exposure exclusions.

## Completed implementation and correctness checks

The candidate introduces `USE_BATCHED_REFRESH=false` and enables it only in the
four current RolloutBot SearchStates. Each refresh visits the used destination
towns, runs the existing BFS once per destination, and walks each pair into its
existing output buffer using the original N/E/S/W tie-break. Distances are
recomputed every time; no cross-state cache, extra matrix or per-node allocation
was added. The old constructor and all frozen bot packages remain unchanged.
The opt-in constructor deliberately retains the old initial refresh once.

All159 focused core/rollout/submission tests and four diagnostic-helper tests
pass. Core differential cases cover repeated noncontiguous destinations, ties,
neutral ownership, disappearing/reappearing connections with buffer identity,
copy/isolation, ink, simultaneous construction/collision/double disruption,
terminal detection and persistent legal trajectories. The engine reproduces
292/292 baseline CG replays exactly; the four study sources validate separately.

Candidate submission: **90,129/100,000 characters**, SHA256
`2cb3a6a254fd8f37e0c9f07e2aa36565f4d61027ee44f508a5f64a93d547d61a`.
Lexical compaction preserves normalized tokens and every executable class byte
with marker/debug positions normalized. Disabled current remains98,709 characters
and SHA256 `ab0954a4545f25222fce6bccf8fa11866d276266e96b4728b502c83996d847e5`;
frozen-v09 remains SHA256
`aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac`.
The previous missed-deadline candidate export is also unchanged. The selected
saved submission is preserved; this experiment's exports live separately.

One preparation-only guard rejected the initial configuration capture because
frozen v09 omits two inactive current-only fields from serialization. The helper
was corrected to allow only `USE_ROLLOUT_ALL_CELL_RISK=false` and
`USE_ROLLOUT_ROOT_FILL=false`; candidate versus current-OFF still differs only in
batching. The four successful read-only captures were verified and reused.
The rejection is retained in `prepare-preflight-failure.json`. No study or game
had run, no parameters changed, and no measured trial was retried.

## Captured-state result

All524 positions across four games and both seats complete both phases:
3,144 generated-policy calls. All1,048 ample arm comparisons are exact, including
ordered actions, accepted construction, sabotage targets, floating-point bits,
every semantic diagnostic and identical diagnostic keysets. Every arm completes
all524 ample searches:5,850 comparisons and87,630 simulated turns each.

For identical ample work, current-OFF totals33,878.1519ms and candidate20,959.5306ms:
**38.13% less measured play time**. Median call57.27495→36.1413ms;445/524 calls
are faster. All four games improve in aggregate, by24.19%..43.94%. Excluding
turn0 gives38.40% less time. These are serial diagnostic wall measurements,
including JVM/scheduling effects, not a portable CPU or strength guarantee.

At nominal486/36ms, completed searches increase234→298/524, comparisons
2,143→3,299 (**+53.94%**) and simulated turns30,828→48,449. Frozen-v09 completes
236/524 searches. Candidate differs from current-OFF on182 actions; even frozen
v09 versus current-OFF differs on150 timed actions. Both timer cutoffs and
subsequent RNG history affect nominal results; ample equality establishes the
intended semantics. Maximum nominal play calls are37.0778ms frozen,
41.8928ms OFF,43.0125ms candidate; none exceeds50ms. This is not a process test.

The independent review recomputes all row counts, diagnostics and totals from
the retained report. Source/exposure preparation includes23,044 excluded map
identities,1,647 saved CG replay files in11 index chunks,247 prior report pins,
and disjointness from122 other registered seed intervals. The experiment freezes
2,912 input/source/class files and seven external runtime files.

## Exact-artifact process result

The declared eight games all pass: **zero errors, timeouts or nominal overruns**.
Across678 later-turn responses, maximum reader-stamped response time is
**46.7275ms**; maximum first response is601.2576ms against1000ms. Each report
matches the frozen candidate source/class hashes and fixed map/seat/seed list.
All four calls were completed once with zero grace, without retries or JFR.
This passes the fixed local readiness sample; it does not guarantee every host
or future game will meet50ms. Whole-game strength is assessed separately below.

## Completed local strength result

**PASS_1000 / PROCESS_SAMPLE_PASS / BOSS_NOT_RUN.** All three registered blocks
complete once, without tuning, extensions, map replacements or retries:

- Block1:168W/112L/20D over300 games,59.3333% points; continue.
- Block2:99W/84L/17D over200 games; cumulative267W/196L/37D,
  285.5/500 points=57.1%; continue.
- Block3:268W/206L/26D over500 games.
- Pooled: **535W/402L/63D,566.5/1000 points=56.65%**. This exceeds the
  prospective55% final local threshold. No interim result was an early pass.

All500 paired maps are distinct, disjoint from23,044 prior identities and
between blocks. Both seats, ordered seeds, full configurations, pool hashes,
exclusions, two workers and486/36ms timing match the declaration. There are
zero bot errors or missing games. Timed blocks took475.599+307.870+792.068=
1575.537seconds in total. Candidate P0:261W/208L/31D (55.3% points);
P1:274W/194L/32D (58.0%). Mean final score difference+327.758 over90,743 turns.
The conservative map-grouped diagnostic interval is50.58%..62.72%; it is not
the selection rule or an overall95% guarantee for the sequential protocol.

One in-process candidate call reached75ms: block3, seed251215005, candidate P1
(that game was a candidate win). Candidate/control maxima75/48ms and slow
counts1/0. The local referee counts calls above50ms on **all turns**, including
the first turn with a486ms allowance. Its aggregate record has no per-turn
timestamps, so this observation cannot identify the turn, establish a late-turn
timeout, or attribute a cause. It is retained as a timing limitation; it does
not replace the separate exact-process8/8 result. No timing retry or new
instrumented run was performed.

All2,912 frozen files and seven runtime files verify unchanged after the full
sequence. Independent audits recompute the captured-state and process results,
map freshness, paired outcomes and v4 decisions from raw evidence. The local
runner retains legacy v3 fields in its raw report; the controlling verdict is
the unchanged **v4** sidecar decision, `PASS_1000`.

The optimization demonstrates a local strength improvement under the declared
practical gate. Preserve it as an opt-in candidate; selected v09 and the default
batching flag remain unchanged. The missed-deadline correction remains OFF;
this experiment makes no claim about its attack-at40 mechanism. Before any
promotion, the separate40-game live-boss comparison needs a valid matched
control cache. No boss games, upload, promotion or push were performed here.

[Machine-readable result](evidence/v09-batched-refresh-v1.json),
[archive index](evidence/v09-batched-refresh-v1-artifacts.json),
[retained source and raw evidence](evidence/v09-batched-refresh-v1-artifacts.zip).
