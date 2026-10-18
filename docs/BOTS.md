# Bot versions

Active strength policy (2026-09-20): **practical-1000-futility-boss-cache-v4**. One pooled
1000-game comparison in blocks 300/+200/+500; stop below 50% at 300/500, final pass >=55%
over all 1000, no early pass. Also require exactly 40 NEW
candidate games against the live boss on the saved control's maps/seeds/seats, scoring
at least as much as the cached control (draws half, ties pass). This replaces mandatory
older/style-opponent stages. Sequential boss calls may run alongside the required local
1000; direct failure cannot be rescued. Reused boss maps are a practical regression pool,
not fresh confirmation. Engine, errors and exact-artifact process checks still apply.
The boss mechanism remains [the v3 cache rule](boss-cache-policy-v3.md); local selection follows
[benchmarking.md](benchmarking.md). The former v3 local thresholds remain in historical entries.
Historical entries retain the policy and verdict in force when those experiments ran.
`current` = **v09**, `btk.bot.current.RolloutBot`; frozen copies in `btk.bot.vNN`.

Strength experiment 2026-10-18: **v09-ink-strength-v1**, **NOT_PASSED**.
Same batched36/NOISE0.1 base in both arms; only incremental ink refresh differs.
Two workers, 486/36 ms both, 500 fresh paired maps disjoint from 25,124 known
identities. Fixed 300/+200/+500: 157W125L18D / 90W90L20D / 250W228L22D;
pooled **497W443L60D / 1,000 = 52.7%**, below 55%, zero game errors.
Native mean 16.13 vs 17.73 ms; candidate three calls >50 ms, max63, control
one, max51. Counters include first turns and are not full-response deadlines.
Reuse unchanged prior engine292/292, ample524 and strict8 process readiness;
retain known historical catalog test failure and guard packaging limitation.
21 new synthetic checks pass; independent audit PASS, 21,584 checks,
3,819 files +7 runtimes and105 prior artifacts preserved. No new process/boss
games, retries, upload or promotion. Keep default36, all experimental flags
OFF and selectedv09; earlier verdicts unchanged.
[Result](v09-ink-strength-v1-result.md), [evidence](evidence/v09-ink-strength-v1.json).

Forecast optimization 2026-10-18: **v09-ink-refresh-v1**, **COMPLETE_DIAGNOSTIC**.
Default-off selective refresh after hypothetical ink; recompute only connections
that lost a connector, preserving canonical ties, strategy, timers and safeguards.
Main comparison on the existing batched36/NOISE0.1 base: all **524 ample inputs
match exactly**, 5,850 comparisons and 87,630 simulated turns per arm; equal-work
time **-15.64%**. At36 ms, comparisons **2,523 to 2,901 (+14.98%)**, completed
searches254 to295. Native max43.9605 ms. Supplementary historical nonbatched
deadline controls preserve turn40 attack and all six winning closures, zero
lost wins. Main artifact91,202 characters; strict8 process games PASS, zero
errors/timeouts/overruns/missing, later full-response max46.505 ms. Engine292/292
exact; focused tests248/249 with one retained historical catalog-pin failure.
The three-flag batching/deadline/incremental combination exceeds the source cap
at100,447 and remains rejected; guard comparison uses the documented amended
historical recipe. No strength or boss gate, upload or promotion. Keep default36,
batching/incremental/deadline OFF and selectedv09; prior verdicts unchanged.
[Result](v09-ink-refresh-v1-result.md), [evidence](evidence/v09-ink-refresh-v1.json).

Local timing diagnostic measured2026-10-16, recorded2026-10-18:
**v09-budget100-local-v1**, **COMPLETE_DIAGNOSTIC**, no promotion eligibility.
Identical batched-refresh/NOISE0.1 policy, local later allowances100 vs36 ms,
first486 both, two workers, 500 fresh paired maps disjoint from24,624 known maps.
Fixed300/+200/+500 completed once:157W126L17D /111W79L10D /280W202L18D;
pooled **548W407L45D /1,000 =57.05%**, zero errors. Later native mean19.179 vs
13.689 ms; completed comparisons+27.64%, simulated turns+29.09%, ordinary
completion97.47% vs84.05%. At100,12.25% of later calls exceed50 ms; this is
compute sensitivity, not an arena-ready budget. Engine292/292 exact, fake-only
harness checks and14 Python tests PASS; preserve2,523 frozen files+8 runtimes
and28 selected submission files. Production strategy/source unchanged;
default36, batching OFF, selectedv09 and earlier verdicts stay unchanged.
No reruns after chat interruption, boss/API work, upload or promotion.
[Result](v09-budget100-local-v1-result.md),
[evidence](evidence/v09-budget100-local-v1.json).

Exploratory boss follow-up 2026-10-16: **v09-boss-budget40-followup-v1**,
**COMPLETE_EXPLORATORY / HISTORICAL_NOT_PASSED_UNCHANGED**. Complete the old
batched36 v2 panel with 12 new games: final **10W30L**, cached v09 **11W29L**.
Then exactly 40 unchanged budget40 games on the same maps/seeds/seats/order:
**13W27L**. Paired 40-vs-36: better7/worse4/equal29, net +3 wins; 40-vs-v09:
better7/worse5/equal28, net +2. All 120 canonical replays exact; 52 new intents
produce 52 known games, zero new technical failures/refusals/replacements.
Preserve the original unknown 422/407 attempt29 and its one explicitly amended
attempt2 separately. Actual boundary gaps 301.641607/300.015568 seconds.
Independent audit PASS; all 325 baseline +39 frozen files unchanged; 24 focused
Python tests passed before play. Positive descriptive boss result does not
rescue the prior formal batching failure or budget40 local 51.0%/1,000 failure.
Keep default36, batching OFF and selected v09. No arena submission or promotion.
[Follow-up result](v09-boss-budget40-followup-v1-result.md),
[evidence](evidence/v09-boss-budget40-followup-v1.json).

Budget experiment 2026-10-16: **v09-budget40-v1**, **NOT_PASSED**.
Same batched-refresh/NOISE0.1 native strategy, later allowance 40 vs 36 ms;
first/warmup/external deadlines unchanged. Exact40 export 90,641 characters;
default/frozen/batched36/budget38 source identities preserved. Strict16 PASS:
1,479 later replies, p99 43.8367/max 47.8363 ms, nine >45 ms, none >48 ms;
zero errors/timeouts/overruns/missing. First maximum 732.0729 ms.
Fresh v4 blocks 300/+200/+500, two workers: 151W141L8D / 104W90L6D /
233W237L30D; pooled **488W468L44D/1,000 = 51.0%**, zero errors, below 55%.
Native-call diagnostic maximum 59 ms, one candidate call >50 ms; these counters
include first turns and are distinct from the full-response process check.
Keep36, batching OFF and selected v09; no boss, upload or promotion.
45 focused Java tests, 46 Python tests and 292 exact engine replays passed;
prior unrelated full-suite failures were not changed or claimed resolved.
[Protocol and result](v09-budget40-v1.md), [evidence](evidence/v09-budget40-v1.json).

Budget experiment2026-10-16: **v09-budget38-v1**, **STOP_FUTILITY_300**.
Same batched-refresh/NOISE0.1 native strategy in both arms; only later internal
allowance38ms vs36ms. First/warmup/external1000/50 unchanged. Exact38 export
90,607/100,000 characters;36 export byte-identical to prior batched candidate.
Strict16-game panel PASS:1,505 later responses, p9940.7075/max44.3156ms,
zero errors/timeouts/overruns/missing;16 first replies max634.2294ms.
Conditional local block on150 fresh paired maps: **143W145L12D/300 =49.6667%**,
zero errors, two workers, actual later budgets38/36. Stop below50% at300;
no further blocks or boss games. Timing fits this local sample; strength gain
not established. Keep36ms, batching OFF and selected v09; no promotion.
Full-suite preflight retained25 unrelated failures after repairing three
inventory assertions; relevant checks and292/292 engine replays pass.
[Protocol and result](v09-budget38-v1.md), [evidence](evidence/v09-budget38-v1.json).

Timing telemetry audit 2026-10-16: **v09-budget-telemetry-v1**, read-only;
no new games, policy calls or budget changes. Current target36ms within50ms,
margin14ms; WARN logs only integer totals>36 after stdout flush. Candidate68:
1599 WARNs/4811 later turns, conditional median38/max98ms,9>50 in8 games;
control80:2096/5717,median38/max106ms,13>50 in12 games; no explicit live failures.
Full local candidate responses678: p9941.3965/max46.7275ms,zero overruns.
WARN median is censored and cannot justify+5ms. Propose one separate38ms
experiment, not implemented; keep36ms and all prior verdicts unchanged.
[Budget and measurement explanation](v09-budget-telemetry-v1.md).

Offline diagnosis 2026-10-16: **v09-batched-loss-diagnosis-v1**, completed with
no strategy change or new games. All10 candidate-loss/v09-win pairs among68
completed pairs included (7 original+3 additional);20 replays/1363 turns exact.
All first own differences occur on identical states at turns1–3, before the
boss diverges;6 are protected-opening decisions. Actual-parameter ample panel:
761states/2283calls, frozen v09/current OFF/ON actions, work and value bits exact.
Zero missed guaranteed immediate one-hit winning closures in761 candidate states.
Six largest income drops involve known one-hit cuts; both native replies already
predict the correct target in6/6, and one-turn consequences match in5/6 with
recorded own actions. Thus omitted sabotage is not the supported explanation.
Core-only60 crossings identify v2-010/t11 as a concrete recovery hypothesis:
forecast income3:15→62:13 over8 turns, recorded continuation14:28; different
later actions/RNG prevent causal attribution. Next diagnostic: repeated selection
of the promised repair, with saved wins as controls. Late v1-020 self-closing
loss already has a forced boss win via region19; no recovered win is claimed.
Batching OFF, selected v09 and prior verdicts unchanged. These ten pairs are
development-exposed; refresh boss control before confirming a new strategy.
[Diagnosis and retained evidence](v09-batched-loss-diagnosis-v1.md).

Additional exploratory boss series 2026-10-16: **v09-batched-refresh-boss-v2**,
**INCOMPLETE_TRANSPORT / ORIGINAL_NOT_PASSED_UNCHANGED**. User requests another
40 games per arm with five-minute pauses after each twenty. Fresh control40
completes at **11W/29L**; candidate stops after **9W/19L over 28 games** when
request29 receives HTTP422/API407 (service play limit, no Retry-After/game ID).
Matched control prefix28 is **6W/22L**; candidate better6/worse3/equal19.
All68 retained canonical replays exact; all40 maps fresh against23,584 prior
identities. Actual three gaps300.015003/300.030197/300.030033seconds. No retries
or replacements; preserve uncertain intent29 and11 unstarted slots. Pooling
only completed matched pairs gives19/68 candidate versus17/68 control,
descriptive only. Twelve candidate slots remain unfinished; no full-series
pass or promotion. Exact builds/parameters/process panels unchanged; batching
stays OFF, v09 selected. [Retained result and evidence](v09-batched-refresh-boss-v2-result.md).

Completed boss continuation 2026-10-15: **v09-batched-refresh-boss-v1**,
**LOCAL_PASS_1000 / BOSS_NOT_PASSED / OVERALL_NOT_PASSED**. The unchanged
batched-refresh candidate scores **10W/30L/0D** against a fresh v09 control's
**11W/29L/0D**, exactly 40 matched maps/seeds/seats. The old October 4 cache
passes integrity checks but was used in outcome-guided diagnostics; refresh
40 control games before the fixed 40 candidate games. All maps fresh against
23,544 prior identities; all 80 canonical replays exact, zero technical failures
or replacements. Exact candidate/control source, classes and full parameters
match the completed local comparison; strict process evidence remains valid.
Local 56.65% does not rescue the failed boss stage. Keep batching OFF and v09
selected; no extension, promotion, arena publication or push. The prior 75-ms
native-call limitation remains. [Full result and evidence](v09-batched-refresh-boss-v1-result.md).

Completed optimization experiment 2026-10-15: **v09-batched-refresh-v1**,
**LOCAL_PASS_1000 / PROCESS_SAMPLE_PASS / BOSS_NOT_RUN**. Candidate
`current?NOISE=0.1,USE_BATCHED_REFRESH=true` versus `v09?NOISE=0.1`,
missed-deadline OFF: **535W402L63D/1000,56.65% points**,500 fresh paired maps,
zero errors. Interim pooled59.3333%/300 and57.1%/500; no early pass or tuning.
One allocation-free BFS per destination within each SearchState refresh;
strategy, canonical path ties and timers unchanged. All524 ample positions
match frozen/current-OFF exactly; identical work takes38.13% less wall time.
Nominal comparisons2143→3299; completed searches234→298.163 tests pass,
292 CG replays exact,90,129-character export; prior exports remain exact.
Strict process8/8 clean,678 later responses,max46.7275ms. Local strength has
one75ms native-call outlier; its turn/cause are unknown and the counter also
includes first turns. Preserve this limitation, no timing retry. All2,912
frozen inputs+7 runtimes unchanged. Batch flag stays OFF, v09 stays selected;
Boss games were pending at this local-only snapshot; the later continuation
above completes and rejects the combined gate. [Local protocol and evidence](v09-batched-refresh-v1.md).

Completed local strength experiment 2026-10-11: **v09-deferred-strength-v1**,
**STOP_FUTILITY_300 / LOCAL_NOT_PASSED_AND_PROCESS_FAILED**. Exact tested
`current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true` versus selected frozen v09:
134W142L24D,146/300 points (**48.6667%**),150 fresh paired maps, zero bot errors.
Two workers, CG486/36ms; all maps disjoint from22,894 previous identities.
Blocks2/3 pre-registered but skipped at the first futility stop, no retry/tuning.
This compares the whole candidate (including NOISE=0) with v09(NOISE=0.1),
not the causal effect of one flag. Engine292 exact, fresh98,572-character export
and classes match the prior candidate;2,589 frozen inputs+7 runtimes unchanged.
Old strict50ms failure remains; no new process/boss check, promotion or upload.
Selected v09 unchanged; no automatic path-batching follow-up.
[Protocol and result](v09-deferred-strength-v1.md).

Completed diagnostic 2026-10-11: **v09-runtime-cause-v1**,
**PARTIAL_ATTRIBUTION / FOLLOWUP_SELECTED / ORIGINAL_PROCESS_FAIL_UNCHANGED**.
Old56.0151ms includes child52ms; old99.5029/64.5907ms have zero supplemental
forecast work. Exact historical causes remain unknown. One unchanged-artifact
JFR panel completes57starts/147pairs,2,281 decisions; forced-input persistence
fails after two measured calls, and child JFR startup breaks the separate process
probe at t0. Both tool failures retained, no retry. Measured-only samples show
refresh40.26% and cheapestPath39.00%; four new overruns have no recorded GC pause.
Required native r33t40 still intervenes28.8426ms; six WIN controls exact, eight
native wins retained. Select allocation-free destination-batched core refresh as
the sole follow-up; not implemented or proven50ms. Production/default/v09 unchanged.
[Diagnosis and acceptance plan](v09-runtime-cause-v1.md).

Completed runtime optimization 2026-10-11: **v09-deferred-forecast-reuse-v1**,
**SEMANTIC_PARITY_PASSED / REQUIRED_MECHANISM_OBSERVED / PROCESS_FAILED**.
Supplemental forecasts reuse canonical paths only when construction is empty;
strategy, guards, full construction and14ms margin are unchanged. Previous ON
versus optimized ON:147 ample comparisons/1,140 decisions per arm match exactly,
including all old diagnostics;9,968 redundant board refreshes removed.31 focused
tests pass;292 baseline+40 cohort+1 historical replays exact. Export98,572 chars.
At nominal36ms, required r33 attack executes at40 instead of42 in32.8008ms,
with all guards complete and45 refreshes saved. Six closing controls stay exact;
all eight native wins survive. Strict process panel fails:7/8 clean, one56.0151ms
timeout at904148181/P0/t33. Keep experiment OFF and selected v09; no retries,
strength/boss trial, promotion or upload. [Protocol/evidence](v09-deferred-forecast-reuse-v1.md).

Completed packaging/runtime experiment 2026-10-11: **v09-deferred-commit-runtime-v1**,
same default-off candidate, **PACKAGING_PASSED / PROCESS_FAILED / REQUIRED_MECHANISM_NOT_OBSERVED**.
Lexical compaction reduces108,524 to98,401 characters (1,599 spare), preserving30,526
normalized Java tokens and all24 Java17 `-g:none` classes. Strategy and14ms margin
unchanged.83 focused tests pass;292 engine replays exact. Strict eight-game process
panel:7 clean, one51.0289ms timeout at904147739/P1/t54. Exact exported classes on
57 old starts/147 paired nominal36ms episodes: zero native interventions; required
region33 correction runs out during Wr0 at40 and still attacks42, losing the ample
benefit. Two passive-only interventions preserve guards/build; all six closing
controls remain exact and eight native wins preserved.927 frozen inputs and629
baseline copies verified. Timing-limited OFF/ON score differences are not strength
evidence. Keep flag OFF and selected v09; no retry, tuning, promotion or upload.
[Protocol/evidence](v09-deferred-commit-runtime-v1.md).

Completed local correction screen 2026-10-11: **v09-deferred-commit-v1**,
`current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true` (default OFF), **LOCAL_SCREEN_PASSED /
SOURCE_SIZE_BLOCKED**. Same full construction, immutable missed deadline,
same-first-target consensus, terminal protections and target-only Wr0 guard.
Same56 starts/88 native reply pairs: one+45 earned-score change,87 ties,zero
regressions. Region33 attacks at40 instead of42 after due39; lateness decreases,
no new terminal win. All eight OFF wins preserved, including six closing
controls. Passive56 pairs: two positive/54 tied; historical case unchanged.
967 OFF actions and all OFF trajectories exact;64 tests,292+40+1 exact replays,
1129 frozen inputs unchanged. Default export byte-identical; enabled source
108,524/100,000 characters, normal export rejected and process/timing unverified.
No strength or boss games, promotion or upload; retain v09.
[Protocol/evidence](v09-deferred-commit-v1.md).

Completed diagnostic experiment 2026-10-11: **v09-full-action-v1**, no registered
candidate or strength games. Same complete construction compared at967 states;
exact root/fill provenance,183 network roots.38/39 old reversals remain, but in
the39 subgroup39/52 nominated-target promises execute on time;13 miss deadlines
across nine starts/six games.56 cohort starts yield88 native reply pairs: bounded
earned-score A−W36 positive/19 negative/33 tied; no recovered terminal win. Four
starts change W=WIN to A=NONTERMINAL within eight turns.56 passive pairs and one
historical start remain separately labelled.32 tests,292+40+1 exact replays,
693 frozen files unchanged. Forecast/execution mismatch confirmed; blanket earlier
sabotage is not supported. Keep v09. [Protocol/evidence](v09-full-action-v1.md).

Completed diagnostic 2026-10-11: **v09-continuation-v1**, no new bot or strength
trial. Fixed full40-game v09 cohort,2,936states: five full-WAIT deferred states in
one episode, zero qualifying changes after removing future own sabotage. Ten
guaranteed winning cuts, three native shortlist omissions, but all original
actions already win against the recorded reply; zero recovered immediate wins.
Exploratory construction/no-sabotage subgroup39states/15games supports selecting
a construction-preserving full-action preflight, not forced attacks; raw forecasts
omit final fill and include delayed terminal wins forecast by the native model.17 tests,292+40+1
exact replay validations,665 frozen files unchanged. Keep v09; follow-up selected
but not implemented. [Protocol/evidence](v09-continuation-v1.md).

Completed local strength experiment 2026-10-11: **neural-target-strength-v1**,
`current?USE_NEURAL_TARGET_PROPOSAL=1`, **STOP_FUTILITY_300 / REJECTED**.
At the user's request, source packaging was deferred. One frozen neural target
is compared against the original target with the complete unchanged v09 build,
equal rollout context and deadline fallback. **129W154L17D/300 = 45.83%** versus
v09 on 150 fresh paired maps; CG search budgets, two workers, zero bot errors.
7,146 E/D comparisons complete, 754/24,871 turns change target (3.03%). Native
slow calls2/5 and maxima56/77ms are not strict process validation. 102 unique
focused tests pass after one fixture correction, 292 exact engine replays,
2,636 files +7 runtimes verified; default source remains byte-identical.
No further blocks, candidate artifact, boss games, upload or promotion; v09 stays.
[Protocol/evidence](neural-target-strength-v1.md). The previous feasibility verdict
below remains historical; this separate experiment explicitly waived its size stop.

Completed feasibility experiment 2026-10-11: **neural-target-feasibility-v1**,
**STOP_DIRECT_PORT_SIZE**. Isolated deterministic frozen-V3 target module matches
49,735 feature values bitwise on 51 states/1,715 candidates. Its direct addition
to fresh current requires **119,594/100,000 characters**, before E/D integration.
Warm local module median **1.30 ms**, p95 **6.20 ms**; fits remaining baseline
budget in **22/51** states (22/24 with complete baseline search). 85 focused tests,
292+8 exact replays, 576 frozen files +4 runtimes verified. No registered bot,
integrated adapter, candidate artifact or strength games; keep v09 and stop this
direct port. [Protocol/evidence](neural-target-feasibility-v1.md).

Completed diagnostic 2026-10-11: **neural-component-audit-v1**, no registered
candidate or strength bench. All 51 prior rows reproduced exactly, then four
construction/target combinations evaluated without refill. Versus the best
complete v09 action: target-only **7 better /24 equal /20 worse**;
construction-only **3 better /15 equal /33 worse**. Original eight positives:
five only-target, two only-construction, one either, none needing both to clear
the proxy threshold. 75 focused tests, 292+8 exact replays, 595 frozen files
and four runtime files unchanged. Prioritize target-only adapter feasibility;
size/time and strength remain unproved, v09 selected.
[Protocol/evidence](neural-component-audit-v1.md).

Completed diagnostic 2026-10-11: **neural-proposal-audit-v1**, no registered
candidate or strength bench. Recorded historical neural-v3 full actions on
51 live positions / eight games; 13 terminal omissions. Forty-eight new full
actions, including 47 new construction sets. Under the same retained route-0
continuation: **8 better / 12 tied / 31 worse** than the best complete v09
alternative. Positives span six games; seven also change the disruption target,
so construction alone is not established as the cause. 68 focused tests pass,
292+8 replays exact, 581 frozen files preserved. Keep v09; no NN integration or
strength claim. [Protocol/evidence](neural-proposal-audit-v1.md).

Completed 2026-10-11: **spatial-ppo-v10**, full-corpus continuation from original V4.
Same architecture, ordered imitation objective and greedy runtime; restore
original Adam/RNG, add 64 epochs / 18,240 steps, ending at epoch 80 / step 22,800.
TRAIN unforced 6,251→7,337/14,428 (43.325%→50.853%); exposed validation 1,471→1,361/3,720 (39.543%→36.586%); teacher-prefix
full plans 599→829/4,548 / 77→34/1,112, respectively. Versus original V4:
**66W/34L/0D / 100**; practical-v4 versus v09: **0W/300L/0D / 300, STOP_FUTILITY_300**.
Strict process 8/8 clean. Strength rejected; no promotion/upload, v09 selected,
original stage 3 open. [Protocol/evidence](spatial-ppo-v10.md).

Completed diagnostic 2026-10-10: **spatial-fit-probe-v1**, no registered candidate
or strength bench. Unchanged V4, 48 fixed TRAIN roots / 192 rows, exactly 1,024
epochs / 3,072 updates. Decisions 118→192/192; actual Java ordered/unordered full
plans and semantic successors 8→48/48. Unforced agreement on the TRAIN complement
43.264%→30.108%, exposed validation 39.543%→26.048%: narrow fitting, no strength
claim. 23 focused tests pass, 292 exact engine replays; selected v09 unchanged,
original stage 3 open. [Protocol/evidence](spatial-fit-probe-v1.md).

Completed diagnostic 2026-10-10: **spatial-label-audit-v1**, no new model or bench.
All 60 original teacher games / 5,660 roots / 22,640 rows reproduced exactly.
All 21,702 teacher construction permutations preserve the full next state, but
actual validation full-plan agreement only changes 77→80/1,112 (V4) and
63→65/1,112 (V9) when order is ignored. Exact-input conflicts force 37 label
errors out of 22,640; measured ordering/contradictions do not explain most of
the fitting gap. 7 Java / 9 Python tests pass, 292 exact engine replays.
Selected v09 unchanged; original stage 3 remains open.
[Protocol/evidence](spatial-label-audit-v1.md).

Completed 2026-10-10: **spatial-ppo-v9**, one global distance-weighted town-context
change with the exact v4 teacher recipe. Fixed 16 epochs/4,560 steps; validation
unforced agreement 38.414% versus v4 39.543%. The synthetic alias is broken,
but the progress comparison is **42W/58L/0D/100** versus v4. Practical-v4 versus
v09: **0W/300L/0D / 300**, **STOP_FUTILITY_300**, errors [0, 0].
Strict process 8/8 clean; strength rejected, v09 selected, original stage 3 open.
[Protocol/evidence](spatial-ppo-v9.md).

Completed diagnostic 2026-10-10: **spatial-learning-audit-v1**, returning to original
roadmap point3. No new bot/weights or promotion. Four frozen policies, four fresh
maps, both v01/v09 opponents and both seats: 64 complete games/6,280 turns, ample
budgets; independently replayed with every stage-zero value checked. V4: 8W/0L
vs v01, 0W/8L vs v09; matched gap improves over v2. V5 transfer is worse than
income extrapolation in all eight groups; v3's native critic improves while its
policy regresses. Exact delayed-route local alias demonstrated. Stage3 remains
open; one global town-context hypothesis proposed. 604 scoped Java/11 Python
tests,292 exact replays. [Protocol/evidence](spatial-learning-audit-v1.md).

Completed 2026-10-10: **spatial-ppo-v8**, frozen v4 weights/v7 proposals,
paired exact-score checkpoints at depths2/4/6/8 with deepest-complete-pair fallback.
Nominal saved panel: 19/34 usable comparisons, 3 changes.
Practical-v4 vs v09: **2W/298L/0D/300**, **STOP_FUTILITY_300**; errors [0, 0].
Strict process8/8 clean, 595 scoped tests,292 exact replays. Rejected for
strength; selected v09 unchanged. [Protocol/evidence](spatial-ppo-v8.md).

Completed 2026-10-10: **spatial-ppo-v7**, frozen v4 weights, alternative first
BUILD with a fresh conditional suffix, unchanged two-turn exact-score ranking.
Nominal saved panel: 30 complete proposals,
3 duplicates, 26/34 comparisons, 3 changes.
Practical-v4 vs v09: **1W/299L/0D/300**, **STOP_FUTILITY_300**; errors [0, 0].
Strict process 8/8 clean, 583 scoped Java tests, 292 exact replays.
Strength rejected; selected v09 unchanged. [Protocol/evidence](spatial-ppo-v7.md).

Completed 2026-10-10: **spatial-ppo-v6**, unchanged v4 weights, two same-cost
last-placement candidates and two exact turns, ranked by actual score delta.
Nominal saved panel: 30/34 comparisons complete,
5 decisions change. Independent reconstruction verifies all
60 ample trajectories. Practical-v4 vs v09: **1W/299L/0D/300**,
**STOP_FUTILITY_300**. Errors [0, 0]; strict process 8/8 clean.
569 scoped Java tests, 292 exact replays. Strength rejected; selected v09 unchanged.
[Protocol and evidence](spatial-ppo-v6.md).

Completed 2026-10-10: **spatial-ppo-v5**, one fixed value-head ridge fit
with exact v4 policy/shared weights, plus two-disruption one-ply core search.
Validation RMSE 8920 vs income 10783 but MAE 5919 vs 5465; v09-only evaluation remains
poor. Nominal saved-panel search completes 14/34 comparisons and changes 6 decisions.
Progress vs v4: **1W/1L/98D/100**. Practical-v4 vs v09:
**0W/300L/0D/300**, **STOP_FUTILITY_300**. Errors [0, 0]; strict process 8/8 clean.
556 scoped Java/38 Python tests, 292 exact replays. Rejected for promotion;
selected v09 unchanged. [Protocol and evidence](spatial-ppo-v5.md).

Completed 2026-10-10: **spatial-ppo-v4**, supervised hard-action distillation
from frozen v09, warm-start v2, unchanged architecture/runtime. 60 complete games,
22,640 conditional rows, 16 epochs, 4,560 Adam steps. Held-out unforced teacher
agreement 39.54%; exact full plans
6.92% (teacher-prefix diagnostics).
Final vs v2: **98W/2L/0D / 100**; practical-v4 vs v09: **0W/300L/0D / 300**,
`STOP_FUTILITY_300`. Zero game errors; strict process 8/8 clean, 292 engine replays
exact. Local bench slow calls 2/0 vs v2 and
2/0 vs v09. Improved over v2, rejected for strength;
boss skipped, selected v09 unchanged. Value head frozen during distillation;
resulting values uncalibrated.
[Protocol and evidence](spatial-ppo-v4.md).

Completed 2026-10-09: **spatial-ppo-v3**, fixed four-opponent pool from v2 weights,
unchanged model/runtime/PPO, 16,384 learner turns, 183 completed games and 1,024
optimizer steps. Final versus v2: **6W/94L/0D / 100**, regression on 50 fresh paired
maps. Practical-v4 versus v09: **0W/300L/0D**, 150 separate fresh paired maps,
`STOP_FUTILITY_300`; zero game errors. Final numerical parity and 32/32 nominal/ample
plans agree, strict process 8/8 clean, 292 engine replays exact. Candidate rejected,
boss skipped, selected v09 unchanged. No causal claim separating opponent-pool
effect from additional training. [Protocol and retained evidence](spatial-ppo-v3.md).

Completed 2026-10-09: **spatial-ppo-v2**, same compact model/runtime, fixed
8,192-turn self-play run: 92 completed games, eight unfinished, 1,024 optimizer
steps. Final versus exact initializer: **96W/4L/0D (96%)** on 50 fresh paired maps.
Practical-v4 versus v09: **0W/300L/0D**, 150 separate fresh paired maps,
`STOP_FUTILITY_300`, zero game errors. Late-panel numerical parity and 32/32
nominal/ample plans agree; strict process 8/8 clean; 292 engine replays exact.
Learning against the weak initializer is established; competitive strength is not.
Candidate rejected, boss skipped, selected v09 unchanged.
[Protocol, checks and retained evidence](spatial-ppo-v2.md).

Completed 2026-10-08: **spatial-ppo-v1**, first complete compact spatial CNN/PPO
pipeline inspired by Pduhard. Fixed smoke training: 256 environment turns,
512 seat samples, 32 optimizer steps, 3,439 weights; all 2,048 internal decisions
legal, but zero completed training games. Initial/final Java/Python parity 64/64;
510 scoped Java tests, 7 Python tests, 292 exact replays, strict process 8/8 clean.
The full suite still includes six failing tests from the pre-existing untracked
beam prototype. Local timing has occasional cooperative cutoffs/slow calls.
Practical-v4 vs v09, seed 9800000, two workers: **0W/300L/0D (0%)** on 150 fresh
paired maps, zero game errors, `STOP_FUTILITY_300`. Boss skipped. Technical
pipeline retained; candidate rejected for strength, selected v09 unchanged.
[Protocol, evidence and limits](spatial-ppo-v1.md).

Verified arena snapshot (2026-10-04, Europe/Bucharest): user-uploaded **neural-v3**,
agent6779375, Silver **134/712**, score22.4, placement100%, inProgress=false.
The fixed latest40 games give20W20L across22 opponent agents; all40 reproduce exactly,
carry the tested build/config markers, and show no technical failures or skipped own
actions. Losses often develop early:13/20 behind at turn20,18/19 surviving losses behind
at turn40. Three apparent self-closures require simultaneous opponent disruption;
none ends against opponent WAIT. These diagnostics establish neither a matched v09
comparison nor recoverable wins. The user reports apparent regression relative to v09.
[Arena evidence](evidence/neural-v3-arena-20261004.json). Local `current` and the
default exported artifact still identify v09.

Additional retrospective timing clue on those same40 games: on11 maps of height18-20,
V3 spends411/660 nominal paint in turns0-19 (62.3%) versus656/660 (99.4%) for opponents,
and wins2/11. On29 shorter maps it spends1714/1740 (98.5%). All40 first moves use3paint;
the deficit begins after the first-turn allowance. Own stderr has84 warnings at37-51ms,
79 on turns spending<3paint; no official timeout is reported. Cooperative interruption
is a credible hypothesis, not yet established as the cause of each loss. Aggregate
whole-game paint totals conceal this opening deficit. [Evidence and limitations](evidence/neural-v3-arena-paint-20261004.json).

The single fixed local follow-up tested all800 saved states with nominal486/36ms
and ample60000ms: all800 action pairs are identical, legal against WAIT and spend3paint;
no aborts, maximum nominal call31.5294ms. CG underpainting (161 of these states)
was not reproduced locally. Preparation accounts for80.65% of measured nominal time
on tall maps (mean10.87ms of13.48ms). This demonstrates available complete construction,
not recovered wins or proven CG timing causality. Next priority is exact feature
preparation optimization with frozen V3 weights and explicit CG timing observation.
The coupled diagnostic RNG differs from CG, so availability of a continuation after
the actual CG-selected prefix is not established. Preparation timing combines full
and incremental calls; repeated hypothetical connection recomputation needs profiling.
[Saved-state diagnostic](evidence/neural-v3-budget-probe-20261004.json).

October 3 diagnostic review (no new bot/version or strength games): Q-v1/v2's
0/300 outcomes remain rejected. The final Q-v2 training block's 128 games / 12,446
turns show no shaping delta beyond earned score increments; terminal-step shaping
exceeds the +/-1 outcome in 25% of games. See the
[review and proposed next experiments](indrill-q-v2.md#october-3-follow-up-review-what-the-failed-runs-actually-establish)
and [reward audit](evidence/indrill-reward-audit-v1.json).

Selected on 2026-09-20: **v09 Never Idle**, the exact fill-v1 candidate
(`current?USE_ROLLOUT_FILL=1`): v08 plus leftover paint spent on stable/protected cells next to
own rails; RHEA code removed from the shipped source. Practical-v4 vs v08: **569/1000 (56.90%)**
PASS_1000; boss: **17/40 vs cached v08 13/40**, PASS. All 117 effective parameters are frozen.
Default artifact 95,278 characters, strict 8-game process panel **0/8** problem games.
Promotion record: [v09-promotion.md](v09-promotion.md).

Previously selected on 2026-09-19: **v08 Protected Opening**, the exact approved opening candidate.
Direct vs v07: **62% / 300**; boss: **13/40 vs cached v07 10/40**, practical-v3 PASS.
All 107 effective parameters are frozen. Promotion and exact-artifact readiness:
[v08-promotion.md](v08-promotion.md).

Previously selected on 2026-09-18: **v07 Network After Base**, the exact network-after-base-v1 candidate.
Direct v06 screen: **62.50% / 300**. Matched fresh v05 confirmation: **64.90% vs 56.90%**
for v06 / 1000 each, **+8.00 points**. Full 98-field snapshot; current defaults enable QuickIncome
and after-base network search. Packaging and main Player identity: [v07-promotion.md](v07-promotion.md).
Earlier selection/default statements below describe historical checkpoints.

Latest user-reported arena snapshot (2026-09-18), immediately BEFORE the intended v07 upload:
**QuickIncome deployed, rank 111 / 627**. This supersedes the earlier v06 arena snapshot below.
At that checkpoint v07 was selected locally; an actual v07 upload had not been reported or performed.
Exact deployed QuickIncome source hash and placement status are not independently verified.

Research proposal, 2026-09-17: **local network control**, initially single-cell ownership
rerouting on already active connections. A known-board audit of the existing 133 boss states
finds 45 states where a cell outside every retained QuickIncome route improves immediate net
income more than every ordinary construction root. This excludes simultaneous actions and
future value; it is action-space evidence, not a strength result or a new bot version.
Proposal, limits and full evidence: [network-control-strategy.md](network-control-strategy.md).
Completed follow-up: network-single-cell-v1 scores 48.3333% vs v06 (300),
50.6667% vs QuickIncome (separate 300); NOT PASSED, flag OFF.
QuickIncome remains the development base; no default, submission or frozen-policy change.
Protocol and final evidence: [network-single-cell-v1.md](network-single-cell-v1.md).

v06 was selected on 2026-09-16 at the user's request after fixed 2x1000 confirmation:
**1230-770 (61.5%) vs v05**, map-grouped 95% CI **57.21-65.79%**. Both individual gates pass;
older-opponent checks are **68.5% vs v03** and **74.5% vs pressure-v1**, 200 games each.
The frozen policy and all parameters match that evidence. Earlier sections below retain their
historical decisions; the current selection supersedes their prior default-v05 status.

Packaging checks at v06 promotion: **217 tests pass**, **264/264 arena replays exact**. The promotion-time default Player is
56,007 characters, source `7f4d94e40e5252d09b86df6e1b8c208e92c86d98a521d858eaf182a9178c2c9c`.
Its fixed strict process check **FAILED 1/42 games**: map 903510176, P0 turn 73, response
**50.462 ms** with a 50-ms deadline (send 0.164 ms). This supersedes a readiness claim based
only on the earlier candidate's 0/42 pass. v06 is selected locally; timing readiness is open,
no arena upload by the agent. Report and executable archive: `build/analysis/v06-promotion/`.

Readable labels and six runnable retained experiments are listed in [bot_catalog.md](bot_catalog.md)
and `gradlew botCatalog`. Version ids remain stable; `EvalBotVNN` class names remain frozen.
Named experiments are local, not promoted, and verify a pinned engine/configuration snapshot.
Their historical screen results are not new evidence for the shared catalog snapshot.

Latest user-reported arena snapshot (2026-09-16): **v06 uploaded, 153/645**, after **rank 270
for v05** before upload. Exact deployed source hash is not independently verified. The 30 saved
Boss 4 games split by the user's corrected first-16/last-14 assignment give **v05 2-14**, **v06 5-9**,
with the first v06 game **904146009**,
on 30 distinct unpaired maps; all validate exactly, with no failures or skipped commands.
See [the loss diagnosis](boss-v06-loss-analysis.md) for route-portfolio evidence and the next
single-change proposal. No strategy change or new strength confirmation in this analysis.

Previous user-reported arena snapshot (2026-09-15): **Gold opened; still Silver, 235/622**.
User confirms the arena still runs the previous build, without the new isolated warmup;
the exact deployed source hash and placement status remain unverified. League changes
alter the ranking population, so this is not directly comparable to the old rank snapshots.
The warmup fix has process-timing evidence, not a new strength-benchmark result: the ordinary
in-process benchmark does not invoke MainBot's submission warmup.

Fresh diagnostic: arena entry **6739024**, 50 newly downloaded games, **25 wins / 25 losses**,
zero own failures, one win by opponent timeout; **264/264** corpus replays exact.
This is a separate entry/sample from 6736850, not a controlled strength comparison.
See `arena-analysis-2026-09-15.md` for scope, loss evidence and the next hypothesis.

Sabotage follow-up (`cgDisrupt`): known-state full scans reproduce Meruem's recorded target
preferences; local timed scans finish at all four selected positions. OldJohn's missed sabotage
is not reproduced, leaving its original cause open. Diagnostics only: v05 and Player.java unchanged,
no new strength bench or promotion. See `sabotage-valuation.md`.

Delayed-sabotage experiment (2026-09-15): `DISRUPT_DELAY_FACTOR=0.65` scores **49.3%** vs v05,
146-150-4 over 300 games / 150 new paired maps, map-grouped CI **38.2-60.4%**. Gate not passed.
On 200 matched games each vs aggressive v02, candidate **73.3%** (146-53-1), v05 **76.5%**
(153-47-0); no common-opponent support for promotion. Keep **factor 1.0**, no v06.
116 tests / 264 exact replays; candidate and restored frozen-v05 process samples each 0/4 failures.
Full protocol, source/artifact hashes and reports are in DEVLOG and `sabotage-valuation.md`.

## Experiment: indrill-forum-v1 (2026-10-06, STOP_FUTILITY_300)

Hand readout of Indrill's published route observation, id `indrill-forum-v1`.
No learned weights. Priority-path and good-cell scores, exact paint-3 knapsack,
mean region ink. Selected v09 and `current` unchanged.

Practical-v4 vs frozen v09, seed 9300000, 150 fresh paired maps, two workers,
CG 486/36 ms: **14W/286L/0D = 4.7% over 300 games**. Zero errors, zero slow moves.
Mean score difference −2791. Candidate paint 1.49/turn on 50.2% of turns; v09
2.83/turn on 95.9%. **STOP_FUTILITY_300**; boss skipped. Not promoted.
[Protocol/result](indrill-forum-v1.md), [evidence](evidence/indrill-forum-v1.json).

## Experiment: rollout-fill-root-v1 (2026-10-06, NOT_PASSED)

Candidate `current?USE_ROLLOUT_ROOT_FILL=1` evaluates the existing leftover-paint
fill as part of each root/target's first action and emits that same completed
action. Based on clean v09, ALL_CELL_RISK OFF. Future construction, opponent model,
weights and route generation unchanged. New flag defaults OFF.

Fixed development diagnosis: 45/110 selected baseline actions had unmodelled fill;
seven unique inputs change to strictly better filled-model full actions. Actual
policy reproduces all 110 predicted choices/values with ample time. This is
mechanism evidence, not strength. Engine 292/292 exact; 479 tests initially pass,
one new fixture corrected and its targeted test passes (480 covered in total).
Candidate 98,708 characters; safe current-only newline compaction preserves frozen
artifacts and compiled decisions. Eight strict process games, zero problems,
maximum later response 44.3567 ms.

Practical-v4 against frozen v09: **157W/141L/2D**, **110W/90L/0D**,
**273W/227L/0D**; pooled **540W/458L/2D = 54.1% over 1000 games**.
500 fresh paired maps, two workers, zero game errors, unchanged source/params.
Both futility looks clear, final 55% gate missed: **NOT_PASSED**;
boss SKIPPED_LOCAL_FAILURE. Keep the feature OFF and selected/default v09 unchanged.
No tuning, retries, CG write, arena upload or promotion. IDE remains the prior
risk candidate. [Protocol/result](rollout-fill-root-v1.md),
[compact evidence](evidence/rollout-fill-root-v1.json),
[80-member archive](evidence/rollout-fill-root-v1-artifacts.zip).

Subsequent explicitly confirmed boss diagnostic, exactly 40 candidate IDE games:
**16W24L (40.0%) vs cached v09 15W25L (37.5%)**, same October 4 maps/seats,
Boss 4 agent6767608. Eight candidate-only wins, seven control-only wins, 25 equal
outcomes: +1 point. Separate boss score **PASS**; this small difference on a known
pool does not establish arena improvement or override the local NOT_PASSED verdict.
All 40 replays exact, no technical/provenance errors, retries or replacement games.
Final IDE source is the exact ROOT_FILL candidate `57753e5b...417ccb`; the actual
preceding source `a7771129...c55a45` is privately backed up. Selected/default local
v09 unchanged, both experiment flags OFF; no arena upload or promotion.
[Boss evidence](evidence/rollout-fill-root-v1-boss.json).

## Experiment: rollout-risk-v1 (2026-10-06, NOT_PASSED)

Candidate `current?USE_ROLLOUT_ALL_CELL_RISK=1` adds one route proposal for our live
earning connections crossing unstable unprotected regions. Existing rails/towns
also pay the instability surcharge in this proposal's search cost. Canonical game
pathing, evaluation weights, opponent model and search budgets are unchanged;
extra proposals compete for the same portfolio and budget. The flag defaults OFF.

Saved-state mechanism check: 30 retained new construction sets in 15/64 positions;
matched empty-only-risk controls zero. Actual integrated actions changed in 6/64
ample-time positions. These are diagnostics, not win rates. Engine validation
292/292; test coverage and frozen/default-off checks pass after updating three
old parameter-inventory expectations. Exact candidate 99,733 characters; strict
process panel 8/8, no timeout/nominal overrun, maximum later response 43.969299 ms.

Fixed practical-v4 against frozen v09: block scores 53.0% / 56.0% / 54.2%,
pooled **518 wins / 434 losses / 48 draws = 54.2% over 1000 games** on 500 fresh
paired maps, two workers, zero game errors. **NOT_PASSED** at the final 55% gate;
boss stage skipped after local failure. No tuning, extension, CG upload or
promotion. Selected v09 and its saved default submission remain unchanged.
[Protocol/result](rollout-risk-v1.md), [evidence](evidence/rollout-risk-v1.json),
[55-member archive](evidence/rollout-risk-v1-artifacts.zip).

Subsequent user-requested boss diagnostic (October 6), exactly 40 games:
**candidate 10W30L (25.0%) vs cached v09 15W25L (37.5%)**, same October 4 control
maps/seats, Boss 4 agent 6767608. Four candidate-only wins, nine control-only wins,
27 equal outcomes. All 40 replays exact; no technical failures, retries or missing
provenance. Separate boss result NOT_PASSED; original local verdict unchanged.
Final IDE contains this exact risk candidate (source 51280c19...cb2a38); previous
neural-v7 is privately backed up. Local selected/default v09 unchanged, no arena
publication or promotion. [Boss evidence](evidence/rollout-risk-v1-boss.json).

## Diagnostic: indrill-fit512-v1 (2026-10-04/05, FIT70_NOT_ESTABLISHED)

User-authorized intermediate fitting diagnostic, separate from the closed A64
and B2048 runs. Fixed first 32 B maps by original hash ranking, 16 rows per map;
same fresh initializer as B2048, unchanged network/features/objective/optimizer.
Exactly 48,000 updates at batch 32 gives 3,000 visits per position, matching
A64's exposure count. Training metrics at 0/3k/12k/24k/48k; final joint exact
>=359/512 is the new 70% operational fitting target. Multi-cell construction is
reported separately. Final candidate-only holdout is descriptive; no new Q-v2
scoring, game, export or promotion. [Protocol](indrill-fit512-v1.md).

Training curve at 0/3k/12k/24k/48k: exact build **5/52/69/82/107**, ink
**0/208/248/272/299**, joint **0/25/28/42/59**, out of 512. Final agreement is
**20.90% build / 58.40% ink / 11.52% joint**; **59 < 359**, so the 70% fitting
target is not established. Final mean build/ink/joint NLL is
**5.89206582 / 1.39088312 / 7.28294893**. Multi-cell construction is exact in
**41/404 (10.15%)** rows, including **24/281 (8.54%)** three-cell rows.

The final-only holdout has **85/1020 build (8.33%), 396/1020 ink (38.82%) and
25/1020 joint (2.45%)**. Mean build/ink/joint NLL is
**9.26619495 / 2.34977481 / 11.61596977**; multi-cell builds match in
**25/838 (2.98%)** rows, three-cell builds in **7/573 (1.22%)**. These subgroup
percentages are row rates, not the separately recorded subgroup map means.

Saved B2048 predictions on the same 512 training positions give **54/229/20**,
versus this run's **107/299/59**; on the same holdout they give **111/424/42**,
versus **85/396/25**. Fitting improved while held-out imitation worsened.
The final post-hoc A64 subset is **15/37/8 of 64** (remaining 448: **92/262/51**).
Matching 3,000 visits per row does not make this a size-only comparison with A64:
seed, batch size, update count and accompanying examples also differ. Low training
fit and a larger train/holdout gap do not identify an architectural ceiling or a
single optimization cause. Close the fixed diagnostic without extension or a
strength claim; construction remains the main unresolved fitting problem.

Saved-state audit PASS: all **48,001 checkpoints / 48,000 updates**, exact fresh
initializer and sampler, **1,536,000 visits / 3,000 per observation**, finite
tensors and zero inactive value state. Adam parameter arithmetic is bit-exact
from the stored moments; no gradients or learned predictions were reconstructed.
Saved labels, predictions and metric arithmetic were checked independently.
The verified compact archive contains **96,058 files / 48,479,500 bytes**,
including all production metadata and five scheduled full NPZ states. The other
47,996 full states remain locally retained and hash-pinned. A separately reviewed
packaging-only helper avoids repeated Windows path resolution; its source and
amendment are identified in the index. Training and frozen audit were not restarted.
[Audited evidence](evidence/indrill-fit512-v1.json),
[archive index](evidence/indrill-fit512-v1-artifacts.json).

## Diagnostic: indrill-generalization-v1 (2026-10-04, TRANSFER_EVIDENCE)

Separate user-authorized larger-panel imitation experiment, fresh model seed
2026100402 and sampler 2026100412, unchanged compact network/joint subset+ink
objective, batch 32 and exactly 12,000 updates. The 2,048 training observations
cover 128 maps; 384,000 visits give 1,024 rows 187 visits and 1,024 rows 188 visits.
No fitted 64-row model weights or optimizer state were inherited. Original capacity-v1
Stage B and all historical verdicts remain closed and unchanged.

Training curve at 0/4k/8k/12k: exact build 32/206/224/231; ink 0/775/815/892;
joint 0/87/94/105, out of 2,048. Mean joint NLL 17.05155459 -> 9.95451873 ->
9.58018708 -> 9.33998860. Final agreement: build 11.2793%, ink 43.5547%,
joint 5.1270%; cell precision 22.4447% / recall 21.7122%. Final build/ink NLL
7.42589894/1.91408965. Training accuracy improves slowly after the first 4k.

Final-only holdout: 1,020 observations from 51 disjoint research maps. Candidate
build 111/1020 (10.8824%), ink 424/1020 (41.5686%), joint 42/1020 (4.1176%);
frozen Q-v2: 29/1020 (2.8431%), 8/1020 (0.7843%), 1/1020 (0.0980%). Candidate
cell precision 21.8301% / recall 21.2745%, paint 2.94510 versus teacher 2.94412,
joint NLL 9.76911484. Q-v2 joint NLL 17.44010432 is descriptive only because its
reward-trained scores are not calibrated imitation probabilities.

Map-paired candidate-minus-Q-v2 agreement differences: build +8.0392 percentage
points, percentile 95% interval [+6.0784,+10.0025]; ink +40.7843 points,
interval [+38.0392,+43.5294]. Both lower bounds exceed zero under the fixed
10,000-resample/51-map convention: TRANSFER_EVIDENCE. These are development
holdouts and a weak control, not pristine confirmation or evidence of arena wins.

Cohorts: old40 (800 rows) build 10.625%, ink 40.375%, joint 4.0%; later11 (220 rows)
build 11.8182%, ink 45.9091%, joint 4.5455%. Holdout-minus-training differences are
build -0.3969, ink -1.9861 and joint -1.0093 percentage points. The low training
agreement already limits current imitation quality; the small gap does not prove
universal generalization or an architecture ceiling. Budget/optimization and
representation remain unresolved. Investigate training fit before a separately
designed RL or strength experiment; do not simply extend this closed run.

Saved subgroup metrics locate the main weakness in multi-cell construction:
training 49/1,622 (3.02%), holdout 28/838 (3.34%). Of the 111 exact held-out builds,
83 are empty or single-cell sets. Three-cell sets alone score 18/1,135 (1.59%)
on training and 11/573 (1.92%) on holdout. This is descriptive, not a new gate or
proof that the additive head causes the problem. Q-v2 also selects ink NONE in
767/1,020 held-out rows, versus zero for both teacher and candidate, underscoring
the weakness of that comparator.

Independent saved-state audit: PASS. All 12,001 checkpoints, fresh initialization,
sampler/RNG and 12,000 Adam parameter steps verify exactly (maximum difference 0);
249 updates use clipping. Audit reaggregates saved predictions and metrics without
new learned forward passes or gradients. The verified compact archive contains
24,062 files / 31,692,635 bytes, including four scheduled NPZ checkpoints;
all other full states remain locally retained and hash-pinned. No new Java/core,
production weights, games, submission, arena upload or promotion.
[Protocol](indrill-generalization-v1.md), [evidence](evidence/indrill-generalization-v1.json).

## Diagnostic: indrill-joint64-12k-v1 (2026-10-04, JOINT_FIT_ESTABLISHED)

The separate joint continuation completes exactly **10,000 new updates / 12,000
total**, starting from the original capacity-v1 Stage A full step-2,000 checkpoint.
All parameters, Adam moments, optimizer clock and sampler are restored; the parent
trajectory is inherited rather than executed again. The same 64 observations from
16 maps, labels, architecture, joint loss, seeds and batch 16 remain fixed. WI/BI
train; only the value head and its moments remain zero. New exposure is 160,000
visits / 2,500 per row; total exposure is 192,000 visits / 3,000 per row. The saved
step-2,000 metric is inherited without rescoring.

Final agreement is **63/64 joint (98.4375%), 64/64 build and 63/64 ink**, meeting
the final-only 63/64 screen: **JOINT_FIT_ESTABLISHED**. Versus original joint
step 2,000, joint agreement gains 57 rows, loses none, retains six and leaves
one inexact. Construction gains 49 and retains all 15; ink gains 37 and retains
all 26. The sole residual error chooses ink region 27 instead of teacher region
14; its three-cell construction set is correct.

At total updates 2,000/4,000/6,000/8,000/10,000/12,000, exact counts are
**6/19/30/47/58/63 joint**, **15/28/41/54/63/64 build** and
**26/37/45/55/59/63 ink**. Mean joint NLL falls **7.49216108 -> 0.32454612**;
final build/ink NLL is **0.13778353 / 0.18676259**. The matched construction-only
12k result also has 64/64 exact sets, with lower build NLL **0.05434280**.
These are descriptive comparisons of saved rows; neither control is rescored.
All 158/158 target and predicted construction cells match, with precision/recall
100% and 192/192 paint. The independent audit verifies all 10,001 new states and
10,000 new Adam steps bit-exactly (maximum difference zero), including 1,796
clipped new updates. The exact parent fork and inherited baseline pass; all
6,322 parent audit pins and 4,190 parent ZIP entries are verified. The new archive
passes for all 20,095 files / 28,260,826 bytes.

The retained representation and original joint objective can fit at least 63
full actions on this inspected development panel. The old 2,000-update failure
did not establish a representation barrier or an inability to learn both heads.
The comparison does not isolate a unique cause of the earlier RL failure:
joint gradients also change clipping and Adam history. No generalization,
teacher optimality, universal capacity or arena-strength claim follows. Earlier
verdicts remain unchanged; no old Stage B, holdout scoring, new games, production
weights or promotion is triggered, and selected v09 stays in place.

Suggested next diagnostic, **unstarted**: a separately scoped larger training
panel with a map-separated holdout, before returning to RL or strength testing.
It needs its own fixed protocol; this pass does not reopen the closed Stage B.
[Protocol](indrill-joint64-12k-v1.md), [evidence](evidence/indrill-joint64-12k-v1.json),
[archive index](evidence/indrill-joint64-12k-v1-artifacts.json).

## Diagnostic: indrill-build64-12k-v1 (2026-10-04, BUILD_FIT_ESTABLISHED)

The separate continuation completes exactly **4,000 new updates / 12,000 total**
from the audited long-v1 full step-8,000 checkpoint. Parameters, Adam moments,
update counter and sampler are restored exactly; the parent trajectory is not
executed again. The same 64 observations, architecture, build-only objective,
seeds and batch 16 remain fixed. New exposure is 64,000 visits / 1,000 per row;
total exposure is 192,000 visits / 3,000 per row. The saved parent baseline is
inherited without another model evaluation.

Final agreement is **64/64 exact construction sets**, passing the final-only
63/64 screen: **BUILD_FIT_ESTABLISHED**. Compared with parent 59/64, five rows
improve, none regress, all 59 successes remain and no construction errors remain
on this panel. At total updates 8,000/10,000/12,000, exact counts are **59/63/64**
and mean build NLL is **0.58229369 / 0.19659455 / 0.05434280**. The 10,000-update
observation does not trigger early success; the full declared budget completes.
All 158/158 target and predicted cells match, with precision/recall 100% and
192/192 paint. The audit verifies all 4,001 new states and 4,000 Adam steps
bit-exactly (maximum difference zero), including 24 clipped new updates. The
exact full-state fork and inherited baseline pass; all 28,099 parent audit pins
and 16,073 parent archive entries are verified. The new archive passes for all
8,083 files / 11,938,938 bytes.

This establishes construction fitting for these 64 development positions under
the retained representation and decoder. WI/BI remained frozen, so it does not
establish full-action learning, transfer or arena strength, and does not satisfy
the old joint-action criterion or automatically unlock Stage B. The parent and
all earlier finite-budget verdicts remain unchanged. No holdout scores, new games,
production-weight change or promotion follow; selected v09 remains in place.

The separate matched joint build+ink diagnostic at **12,000 total updates** is
recorded above, continuing the full original 2,000-update checkpoint with the
same seeds, Adam and joint loss. This construction-only result remains unchanged. The old
2,000-update result (joint 6/64, build 15/64, ink 26/64) did not isolate difficulty
from the joint objective: build-only also reached only 16/64 at that budget.
[Protocol](indrill-build64-12k-v1.md), [evidence](evidence/indrill-build64-12k-v1.json),
[archive index](evidence/indrill-build64-12k-v1-artifacts.json).

## Diagnostic: indrill-build64-long-v1 (2026-10-04, BUILD_FIT_NOT_ESTABLISHED)

The separately frozen longer-budget run completes exactly **8,000 updates,
batch 16, 128,000 sample visits / 2,000 per observation** on the same 64 A positions
from 16 maps. It starts fresh with the original seed, build-only objective,
optimizer and sample order. Before update 2,001, the complete first 2,001 states
match retained build64-v1 parameters, Adam moments, RNG/order/position and sampled
batches/losses/gradient norms exactly; the first five metric payloads also match.

Final construction agreement is **59/64 (92.1875%) versus 16/64**: 43 gained,
none lost, 16 retained exact and five inexact in both. At updates
0/500/1000/1500/2000/4000/6000/8000 the saved exact counts are
**0/8/12/13/16/28/48/59**. Mean build NLL falls from **5.16299332** at
the old 2,000-update endpoint to **0.58229369** at 8,000. The fixed final-only
63/64 requirement remains unmet: **BUILD_FIT_NOT_ESTABLISHED**. All 8,000 updates
complete without early stopping or checkpoint selection. The final audit verifies
all 8,001 states and 8,000 Adam steps bit-exactly, with maximum difference zero;
1,341 updates use clipping. The 2,001-state/five-metric prefix also passes.
Archive verification passes for all 16,073 files / 19,106,278 bytes.

The final five errors were never exact at the eight saved observations: four
substitute one cell in a three-cell set; the fifth selects one cost-three cell
instead of three unit-cost cells. All five NLLs decrease from update 6,000 to
8,000. Overall macro precision/recall is 96.35%, with 151/158 target cells
recovered. These sparse observations do not describe behavior between checks.

The exact shared prefix makes the larger optimization budget the change behind
this substantial fitting improvement. The old 2,000-update budget was limiting
for this recipe; its failure did not establish an inability to fit these labels.
Five final construction sets remain wrong. This does not establish convergence,
a universal representation limit, full-action imitation, transfer or arena
strength. No Stage B, holdout model scoring, new games, production-weight change
or promotion occurs; selected v09 and all earlier verdicts remain unchanged.
The separate continuation to **12,000 total updates** is completed above with
the same architecture and optimizer. Its full-state starting checkpoint is this
run's audited step 8,000; this historical result and verdict remain unchanged.
[Protocol](indrill-build64-long-v1.md), [evidence](evidence/indrill-build64-long-v1.json),
[archive index](evidence/indrill-build64-long-v1-artifacts.json).

## Diagnostic: indrill-build64-v1 (2026-10-04, BUILD_FIT_NOT_ESTABLISHED)

The separate construction-only run completes exactly **2,000 updates, batch 16,
32,000 sample visits / 500 per observation**, on the original 64 A positions from
16 maps. Initial parameters and moments, model seed 2026100401, sampling seed
2026100411 and the complete original sample order are matched. The unchanged
64->24->24 network learns the build objective only; WI/BI/WV/BV parameters and
moments remain frozen, and ink derivatives do not enter the shared trunk.

Final construction agreement is **16/64 (25%) versus the retained joint run's
15/64 (23.4375%)**: 3 gained, 2 lost, 13 retained exact, and 46 inexact in both.
This is a net gain of one exact set, not a significance or strength result. Mean
build NLL falls **13.65655966 -> 5.16299332**; the old final build NLL is
**5.56911593**. Scheduled exact counts at 0/500/1000/1500/2000 updates are
0/8/12/13/16; all 2,000 updates run without an early success stop. The separate
final-only 63/64 build memorization requirement is not met:
**BUILD_FIT_NOT_ESTABLISHED**. The saved-state audit passes all 2,001 checkpoints,
original initialization/sampling and bit-exact Adam parameter arithmetic. All 64
initial construction metric rows match the original run exactly. Archive verification
passes, including every startup/update/metric record.

Saved final exact counts by target size are 9/11 for one cell (old 8/11), 2/12 for
two cells (old 3/12), and 5/41 for three cells (old 4/41). Of the 48 final errors,
46 were never exact at any of the five scheduled observations; two had been exact
earlier. This sparse curve does not reveal behavior between those observations.

Removing the ink objective provides only a small fitting gain in this fixed
recipe. It does not establish gradient conflict as the earlier failure's cause,
generalization, arena strength or a universal capacity limit. Global clipping and
Adam trajectories also change when the ink loss is removed. There are no new
games, holdout model scores, production weights or strategy promotion; v09 remains
selected. The old joint and single-position runs remain closed and unchanged.

The separately predeclared fresh **8,000-update** follow-up is completed above,
retaining the same seed, batch 16 and first 2,000-update sequence. It tests the
larger budget with 2,000 visits per row, matching the single-position exposure.
The declining NLL at this run's endpoint motivated that test; it did not prove
convergence or promise success. This original fixed-budget verdict is preserved.
[Protocol](indrill-build64-v1.md), [evidence](evidence/indrill-build64-v1.json),
[archive index](evidence/indrill-build64-v1-artifacts.json).

## Diagnostic: indrill-fit-one-v1 (2026-10-04, both arms FINAL_SET_FIT)

Both fixed arms completed exactly **2,000 updates / 2,000 visits**, batch one,
on the first retained A tuple `905008481:0:0`. Selection followed the existing
export order before new model scores. Construction alone is trained: independent
zero-initialized cell logits and a fresh network with the unchanged 64->24->24
representation, seed 2026100401. WI/BI/WV/BV parameters and moments stay frozen;
the previous A checkpoint is not resumed.

Both final decoders return the accepted teacher set **[75,178,179]**, spending
3 paint with precision/recall 1. Both are already exact at the scheduled
500/1000/1500-update observations and still complete all 2,000 updates.
Network NLL falls **14.39639247 -> 0.002980866**; free-logit NLL falls
**14.19641723 -> 10.73031938**. Each final-only arm verdict is **FINAL_SET_FIT**.
Correct free-logit argmax with much larger NLL does not establish concentrated
teacher probability or convergence. The final audit verifies both 2,001-checkpoint
chains, initialization, finite/frozen state and every Adam update bit-exactly.
The compact archive preserves all update receipts and five scheduled checkpoints
per arm; other checkpoints remain locally retained and hash-pinned. Archive bytes
and startup/metric metadata pass verification.

This establishes fitting for one development position under the simplified
objective. It does not explain the earlier 64-position joint failure: this row
received 2,000 visits, versus about 500 per row there, while both the objective
and competition between positions changed. No generalization, arena strength or
universal capacity limit is established. No new games, holdout scoring, promotion
or production-weight change occurred; selected/default v09 remains unchanged.

The separate matched 64-position construction-only follow-up is completed above,
with the original seed, batch 16, 2,000 updates, sample order and per-position
exposure. It does not extend or retry the old failed run.
[Protocol](indrill-fit-one-v1.md), [final evidence](evidence/indrill-fit-one-v1.json),
[archive index](evidence/indrill-fit-one-v1-artifacts.json),
[archive metadata check](evidence/indrill-fit-one-v1-archive-check.json).

## Diagnostic: indrill-capacity-v1 (2026-10-04, FIT_NOT_ESTABLISHED)

Completed supervised fitting diagnostic after the bounded neural-v7 experiment.
It retains the Q policy's 64-feature, 24/24-hidden-unit representation and additive
whole-set construction/ink decoder, with a fresh model and a whole-subset likelihood
objective. It is a diagnostic, not a new promoted bot or a strength evaluation.

All 293 source games reproduce exactly with the core. The independently audited
export contains 3,068 valid observations, preserving the old 40 and later 11
holdout maps: Stage A has 64 observations from 16 maps, Stage B has 2,048 from
128 maps, and holdout has 1,020 from 51 maps. Stage A is a subset of B. Accepted
teacher actions are paired with their original pre-turn features; map groups and
exact inputs do not cross the training/holdout boundary. No input-label conflicts
or sufficient structural tie groups were found by the retained checks.

Stage A completes its predeclared **2,000 updates, batch 16, 32,000 sample visits**.
Final exact agreement is **6/64 joint (9.375%), 15/64 build (23.4375%), and
26/64 ink (40.625%)**. Mean joint NLL decreases **17.19242878 -> 7.49216108**.
The final-only requirement is at least 63/64 joint; verdict **FIT_NOT_ESTABLISHED**.
Stage B training and holdout model scoring were therefore not run. No continuation,
new games, strength gate or promotion is inferred from this fitting result.

The fixed optimization recipe has not learned even the small panel. Loss is still
decreasing, so this does not establish convergence or prove that the representation
cannot fit it. Absence of detected collisions/ties is not a universal capacity proof.
The separate single-position and matched 64-position fitting follow-ups are
completed above; neither establishes that another large RL run is ready.
Selected/default v09 remains unchanged.
All 2,001 checkpoints and 2,000 update links pass the final lineage audit; recorded
Adam steps and sampling state match exactly. Compact evidence retains all receipts
and five scheduled checkpoints; raw data and other checkpoints remain locally pinned.
[Protocol](indrill-capacity-v1.md), [final evidence](evidence/indrill-capacity-v1.json),
[artifact archive](evidence/indrill-capacity-v1-artifacts.json).

## Candidate: neural-v7 Deadline Extension (2026-10-04, NOT_PASSED / NOT_PROMOTED)

One follow-up to V6's remaining CG under-spending before returning to Indrill.
Same V6 features and V2 weights; only deadline/reserve cutoffs enable a bounded
stable/protected extension next to own rails. Completed neural choices and target
remain intact, and completed empty-candidate decisions do not trigger fill.
Earning membership is frozen at the accepted prefix; no path recomputation in fill.

The fixed actual-prefix diagnostic validates all40 V6 boss replays and800 early
states. All70 underpaint turns have legal and V6-eligible continuations (96 paint,
77 on tall maps). Its exploratory fill spends all96 but changes immediate net
income in3 positive/2 negative states (65 neutral; min -3, total +2). More paint is
not a strength result. Production fill on those same70 prefixes also recovers96/96
paint; all800 ample-time actions match V6. Fixed-clock and real-clock fill actions
match in70/70 calls; local max0.2768ms, p95 0.2145ms. This ample-time diagnostic
does not establish recovery under CG's actual remaining budget.

All466 Java tests pass, baseline engine292/292 exact; the separate diagnostics
validate40/40 V6 games. Exact artifact99,358/100,000 characters, source53551d0e...e7ea8;
regenerated V6/V3/v09 are unchanged. Strict8-game zero-grace process panel PASS8/8,
max first711.7606ms and later36.8594ms, no retries. The original three failures in
old parameter-count assertions and a repaired diagnostic assertion are retained;
neither required a candidate-policy change.

Fixed practical-v4 against selected v09 completes178W122L/136W64L/339W161L,
pooled653W347L0D/1000 (65.30%), PASS_1000, two workers, zero errors. All500 maps
from4200000/4300000/4400000 match the prospective registry and are disjoint from
18,300 prior maps. P0score65.4%, P1score65.2%; source/runtime/artifact pins remain
stable. This is qualification versus v09, not direct superiority over V6.
The authorized live stage completes 40 fresh v09 controls and 40 matched V7 games:
**V7 14W26L0D versus v09 15W25L0D**, below the required control score. Combined
practical-v4 verdict **NOT_PASSED**. The exact v09 control passes its separately
declared strict 8 checks (max later 41.4440ms). All 80 unique replays reproduce exactly,
and all 40 actual core-map hashes/seats match the prospective schedule, disjoint
from 18,800 prior and V7-local maps. Zero technical failures, skipped commands,
uncertain requests or retries; losses were not replaced.

Actual first 20 turns spend 2276/2400 paint (94.8333%) overall and 1321/1440 (91.7361%)
on 24 height 18-20 maps, where the outcome is 6W18L. Own stderr contains 132 WARN lines
over full games and 82 in the first 20 turns; these wall warnings are not official
timeouts. These results do not demonstrate that under-spending is resolved. Historical
V6 used different maps, so these descriptive metrics and scores do not establish V7>V6.

Final readback confirms exact V7 in IDE; the original V6 source backup is retained.
Selected/default strategy remains v09, with no promotion or arena publication.
This bounded neural experiment is closed; the subsequent Indrill representation diagnostic
is completed above. The immutable live archive retains 207 public files/657,157 bytes separately
from the sealed local archive; canonical/private responses remain hash-pinned outside it.
[Protocol](neural-policy-v7.md), [prefix evidence](evidence/neural-v7-prefix.json),
[production mechanism](evidence/neural-v7-mechanism.json),
[strict verification](evidence/neural-v7-verification.json),
[audited local gate](evidence/neural-v7-v4.json),
[prepared live comparison](evidence/neural-v7-boss-preparation.json),
[final audit](evidence/neural-v7-final.json), [combined gate](evidence/neural-v7-decision.json),
[CG paint](evidence/neural-v7-boss-paint.json), [live archive](evidence/neural-v7-live-artifacts.json).

## Candidate: neural-v6 Resumable Feature Paths (2026-10-04, PASS / NOT_PROMOTED)

Frozen V5 policy and V2 weights; the sole change reuses feature Dijkstra searches
within each prepare and source town, separately for four semantic blocking masks.
Retain exact route tie-breaking, pending target expansion and fresh public costs.
No tuning, changed reserves or training. Selected control remains v09.
[Predeclared protocol](neural-policy-v6.md):452 Java tests and292 exact CG replays;
800 saved actions and11,789,067 feature values bit exact against V5. One local cost
pass reduced play wall22.73% overall/30.82% on height18-20, preparation33.36%/40.15%;
play allocations unchanged. This is local mechanism evidence, not proof of CG gain.

Exact artifact96,619/100,000characters, source0597d5b7...90b155; original V5/V3/v09
submissions unchanged. Strict8-game zero-grace process panel PASS8/8, max later
response36.2853ms, no retries. Fresh practical-v4 vs v09:199-101/136-64/308-192,
pooled643W357L0D/1000 (64.30%), PASS_1000,500 disjoint maps, zero bot errors.
P0score62.0%, P1score66.6%; no paired strength comparison with V5/V3 was run.

User authorized exactly40 IDE boss games after the local pass. Current V3 IDE source
was backed up, live boss/league checked, and unchanged cached v09 control11/40
pinned. Completed40 new games:20W20L versus cached11W29L, PASS, no technical
failures, retries or replacement outcomes. All40 new replays reproduce exactly;
the descriptive audit also validates all40 cached controls. The unchanged default
corpus separately remains292/292 exact. Final combined practical-v4 verdict PASS;
this is eligibility, not automatic promotion. At completion, IDE contained the exact V6 source;
no arena publication or local selected-strategy change was performed.

Actual CG first20turns:2304/2400paint (96.0%) overall,583/660 (88.33%) on11 tall
maps, versus the cached v09's2352/2400 and660/660. No skipped actions/server failures.
Own stderr has22 early WARN lines (17 with<3paint),31 over full games; two later-turn
warnings report53/66ms without official timeout. These are self-reported wall times,
not server deadline failures. Remaining tall-map under-spending motivates a separate
follow-up; the different earlier V3 arena sample cannot establish a paired gain.
[Saved-state parity/cost](evidence/neural-v6-saved-state.json),
[readiness](evidence/neural-v6-verification.json),
[local strength](evidence/neural-v6-v4.json), [boss](evidence/neural-v6-boss.json),
[CG paint](evidence/neural-v6-boss-paint.json), [final audit](evidence/neural-v6-final.json),
[combined gate](evidence/neural-v6-decision.json), [retained artifacts](evidence/neural-v6-artifacts.json).

## Candidate: neural-v5 Exact Completion Income (2026-10-04, LOCAL_PASS / PROCESS_FAILED)

Retain V3 weights and policy; replace only hypothetical route-completion income
with an additive exact core helper that avoids state copying and path allocation.
Baseline profiling identifies a bounded target:16.15% of play time on tall maps,
but95.0% of measured allocation comes from its connection updates. Feature Dijkstra
remains the majority time cost and is outside this experiment. Separate V5 sources
and packaging preserve V1-V4 and selected v09. All 432 tests and 292 engine replays
pass. Saved-state parity: 800 actions and 11,789,067 feature values bit exact;
allocation falls 88.30% overall / 93.46% on tall maps. Wall time changes only
-1.85% / -0.77%; this one local pass establishes no clear timing improvement.
Strict process panel fails 1/8: map903510176, P0 turn11, no response by 50ms.
sendMs=34.6403 and consumerMs=66.3022 do not isolate bot CPU as the cause.
No retries; source 34259281...34730, 95,627 characters. The fixed practical-v4
comparison versus v09 passes: **610W/389L/1D /1000 (61.05%)**, blocks 185/300,
117.5/200 and 308/500; all 500 maps fresh and zero bot errors. P0 score59.0%,
P1 score63.1%. In-process timing also has occasional overruns; neither local
strength nor reduced allocation clears the strict process failure. Combined
verdict **LOCAL_PASS_BUT_PROCESS_FAILED**, boss skipped, no promotion/CG write.
This is independent confirmation versus v09, not evidence of stronger play than V3.
[Predeclared protocol](neural-policy-v5.md), [baseline profile](evidence/neural-v5-feature-profile.json),
[saved-state parity/cost](evidence/neural-v5-saved-state.json),
[readiness audit](evidence/neural-v5-verification.json), [strength audit](evidence/neural-v5-v4.json),
[retained artifacts](evidence/neural-v5-artifacts.json).

## Candidate: neural-v4 Construction Temperature 0.5 (2026-10-04, REJECTED)

Repeat the fixed neural-v2 outcome-training schedule from the exact original v1 prior,
lowering only construction sampling temperature from1 to0.5. Sabotage remains at1;
the actor probabilities and gradient match the new distribution, while teacher KL
stays at1. Use the frozen v3 deadline-aware runtime with a separate generated weight
class. All8x192 training games and the independent audit completed; final checkpoint
only, weight hash4cf8303b...d499. Final414 tests,292/292 engine replays and4,168 bit-exact
scores pass. Strict process panel failed1/8:904148181 asP1,turn38,response52.0318ms
against50ms. No retry; not ready for CG. The predeclared practical-v4 comparison
against neural-v3 also fails: **149W/150L/1D /300 (49.8333%)**,150 fresh paired maps,
two workers, **STOP_FUTILITY_300**, zero game errors. No blocks2/3 or boss games.
Build sampled-maximum agreement improved from27.64% to57.32%, but the intended
mechanism did not produce a passing strength result. No tuning, retry, checkpoint
selection or promotion. Selected local `current` remains v09; arena V3 was not changed.
[Predeclared protocol](neural-policy-v4.md), [training](evidence/neural-v4-training.json),
[mechanism](evidence/neural-v4-exploration.json), [readiness](evidence/neural-v4-verification.json),
[local verdict](evidence/neural-v4-v4.json), [retained artifacts](evidence/neural-v4-artifacts.json).

## Candidate: neural-v3 Deadline-Aware Neural Policy (2026-10-03, PASS / NOT_PROMOTED)

Same exact neural-v2 weights; one fixed runtime change with a 2-ms internal reserve,
cooperative feature/scoring interruption and retention of completed legal actions.
No retraining or weight selection. Practical-v4 vs v09: **621/1000 (62.10%)**,
blocks **194/300, 110/200, 317/500**, **PASS_1000**, zero game errors and 500 fresh
paired maps. All **406 tests**, **292/292** replay validations and **8/8 strict
process games** passed; max later process response **40.1253 ms**. The in-process
strength bench separately recorded one 53-ms call (its summary does not identify
the turn); this is not a hard real-time guarantee. No process or strength retries.
Exact Player:92,497 characters, source2aee3ffe..., build9158eae9.... V1/V2 remain
unchanged; the selected default artifact is preserved.

Authorized live comparison completed in the permanent puzzle IDE: **neural-v3
15W/25L versus v09 11W/29L**, each 40 games on the same maps/seats against Boss4
agent6767608. Nine losses became wins and five wins became losses; net +4/40.
All **80/80** new replays reproduce exactly, zero technical failures, no retries
or excluded training games. All 40 maps are disjoint from 16,221 prior maps.
Practical-v4 **PASS**, exact-artifact readiness passed; **not promoted**, v09 stays
selected. The earlier rejected contest intent remains unresolved and preserved
separately; the first training loss stays slot1 through an immutable boss-identity
amendment. [Protocol/results](neural-policy-v3.md), [final boss/combined audit](evidence/neural-v3-boss.json),
[local strength](evidence/neural-v3-v4.json), [verification](evidence/neural-v3-verification.json),
[historical boss interruption](evidence/neural-v3-boss-interrupted.json).

## Candidate: neural-v2 Outcome Fine-tuning (2026-10-03, LOCAL_PASS / PROCESS_NOT_READY)

The separate experiment **neural-v2** is predeclared in
[neural-policy-v2.md](neural-policy-v2.md): outcome fine-tuning from retained
neural-v1 with unchanged inference features/action model, terminal WDL and a
teacher prior. Fixed training completed: 1,536 games / 768 maps, final checkpoint
only. Practical-v4 vs v09: **645/1000 (64.50%)**, blocks190/300,123/200,332/500,
**PASS_1000**. Historical neural-v1's 55.8% used different maps, not a head-to-head.
396 Java / 22 Python tests, 292/292 exact replays, 3,704 bit-exact numerical scores.
Exact Player 88,938 characters, source 16c49e63..., strict process **1/8 failed**:
904148181 P0 turn 3 response 50.3003 ms against 50 ms. No retries. Actual submission
already uses isolated 36-ms warmup; the direct bot's 5-ms method is bypassed.
Boss not started; outcome-guided control pool needs a refresh decision.
Retain as a promising local candidate, not ready or promoted; selected v09 stays.
[Training](evidence/neural-v2-training.json), [strength](evidence/neural-v2-v4.json),
[artifact/process](evidence/neural-v2-verification.json), [diagnostics](evidence/neural-v2-exploration.json).

## Candidate: indrill-q-v2 Learned Position Value (2026-10-03, STOP_FUTILITY_300)

New random-start outcome-trained version: **V(state) + additive build/ink advantages**,
including learned WAIT. 3,387 parameters; exact budget-three construction. V enters every
complete action once. Fixed larger schedule: **960 games / 186,162 transitions / 14,000
updates / 448,000 sampled visits**; balanced seats within frozen-opponent cohorts,
continued Adam/target/replay-priority state. Final weights `c5cfc9c8...`; v1 retained unchanged.

Practical-v4 vs selected **v09**: **0 W / 300 L / 0 D**, zero errors, 150 fresh paired maps,
two workers, all 960 training + 3 probe maps excluded. **STOP_FUTILITY_300, NOT_PASSED**;
no local continuation or boss games. Mean score 8,945 vs 22,342; placement 86.4% vs 66.3%,
accepted disruption 34.1% vs 68.6%. Extra construction did not produce winning income.
On the separate reused eight-map/seat process panel, mean own score rose from v1's 11,510
to 21,021 (higher in 8/8); both lost 8/8. Diagnostic only, not a win-strength comparison.

386 Java + 29 related Python tests pass; 292 old + 86 newly downloaded public arena games
reproduce exactly. Independent audit verifies all actual replay records, update counts,
checkpoint chain and exact Java tensor bytes. Numerical parity: both actions 18/18, 8,514
values within declared absolute-plus-relative float32 tolerance. Exact isolated artifact
**82,755 characters**, source `2a35a982...`, build `eede222b...`; strict process **0/8 problems**,
later response <=43.888 ms. Selected v09 and its saved submission unchanged.

State-value/WAIT bug repaired; additive action interactions, uncentered advantages and the
margin-shaped reward remain limitations. This combined experiment does not isolate the
effects of architecture, training volume and seat balancing. Design and result:
[indrill-q-v2.md](indrill-q-v2.md); [complete evidence](evidence/indrill-q-v2.json),
[public corpus audit](evidence/indrill-q-v2-corpus.json).

## Candidate: indrill-q-v1 Indrill Action Values (2026-10-03, STOP_FUTILITY_300)

Compact outcome-trained prototype inspired by [Indrill's published description](https://www.codingame.com/forum/t/summer-challenge-2026-feedback-strategies/208574/5),
independent of rollout and the earlier imitation net. Risk-aware Best/Good/Path channels,
a 3,362-parameter grid network, region-mean ink head and exact additive whole-quota build
knapsack; greedy inference. Trained on **192 games / 37,694 transitions / 1,600 updates**;
final weights `6ebd02d1...`. This is not the unpublished champion model or full BTR.

Practical-v4 vs **v09**: **0 W / 300 L / 0 D**, 0%, zero errors, two workers, 150 new paired
maps; **STOP_FUTILITY_300, NOT_PASSED**. All 13,040 prior map identities, including 192
training maps and 3 numerical-probe maps, excluded. No continuation, boss batch or promotion.
Selected v09 and the exact default submission remain unchanged.

Separate candidate artifact **81,823 / 100,000 characters**, source `c186d548...`,
build `a3fbee95...`; strict eight-game panel **0/8 problem games**, first <=583.2 ms,
later <=18.2 ms. 379 Java tests, 126 Python tools tests and 5 numpy training tests pass;
292/292 arena replays exact. Python/Java parity: 18/18 sampled actions for both heads,
maximum Q difference 0.000031. Core's original rules/pathing classes and all frozen bots
unchanged; new observation helpers stay in the core.

Limits: tiny training budget, additive actions without cell interactions, fixed-zero WAIT
without a state-value baseline, nonnegative bootstrap, approximate reward shaping and
training seat asymmetry. The failure rejects this particular prototype, not the published
RL principle. Retained as `indrill-q-v1`; new weights require a separately named experiment.
Design: [indrill-q-v1.md](indrill-q-v1.md); complete evidence:
[indrill-q-v1.json](evidence/indrill-q-v1.json).

## Candidate: neural-v1 Neural Imitation (2026-09-20, local PASS_1000, boss NOT_PASSED)

Independent strategy with its own shipped package (`btk.bot.neural`, `submit -PsubmitVersion=neural-v1`,
87,815 characters): two small conditional-logit MLPs trained by behaviour cloning on the 282 replays
of the Legend #1 Indrill choose the DISRUPT region (29 features per legal region, 1,505 weights,
**55.5%** held-out top-1 among 34.6 candidates) and each placement cell in sequence (42 features per
affordable cell, 3,265 weights, **23.4%** top-1 / 42.5% top-3 among 114 candidates). Features are
computed through `btk.core` (exact ink and completion income deltas, cheapest / own-only / bypass /
take-over routes, board-derived memory); the only hand-written rules are the closing-ink guard.
Practical-v4 vs **v09**: **160-140 / 119-81 / 279-221 = 558/1000 (55.80%) PASS_1000**, zero errors,
accepted disruption 99.6% vs 74.7%, 66% early closures. Style check **168-32 (84%)** vs leader-v1.
Exact artifact strict process check **0/8** problem games, later responses <= 30.6 ms.
User-authorised cached-boss stage on the pinned v09 maps/seeds/seats: **12 W / 28 L vs cached v09
17 / 23**, bossStage **NOT_PASSED** (candidate-only wins 8, control-only 13, both 4, neither 15;
40/40 replays exact). Combined v4 verdict **NOT_PASSED, promotion false**; v09 stays selected.
Design, data and training: [neural-policy-v1.md](neural-policy-v1.md); evidence
[neural-v1-training.json](evidence/neural-v1-training.json), [neural-v1-v4.json](evidence/neural-v1-v4.json).

## Local reference panel (not submission versions)

`leader-v1` (added 2026-09-20) is a fast heuristic calibrated on the arena leader Indrill
(Legend #1, Python3; see [indrill-study-v1.md](indrill-study-v1.md)): a sticky DISRUPT every
turn on the opponent's unfinished construction (opponent track worth 5, own track -6, +8 per
instability level reached, previous-target stickiness, own income loss weighted half), greedy
cheapest-route construction and a protected town-approach opening. Its constants are frozen
inside `LeaderBot` and never retuned; only NOISE/SEED/UNSTABLE_COST come from
`leader-v1.properties`. Measured vs v08 (300 games, leader-coverage-v1): **99.9% accepted
disruption vs v08's 56%**, but it places on only **41.5% of turns (0.95 cells, 1.18/3 paint)**
against the leader's 97% / 2.29 / 2.91, and v08 wins **82%**. It reproduces the leader's
sabotage signature, not its never-idle construction and not its strength; games last 84 turns,
not the leader's 47. Use it to check that a sabotage mechanism behaves as intended against a
relentless disruptor; it is not a strength benchmark and not a claim to reproduce the player.
Select with `-Pbot2=leader-v1`.

`pressure-v1` is a separate sabotage-first, sticky-target, short-horizon policy. Its fixed consumed
parameters live in `btk/bot/versions/pressure-v1.properties`; the policy stays outside the merged
submission. Do not retune this reference id after recording its baseline. Use `benchSuite` to
compare each candidate against the matched v05 / pressure-v1 / v03 / aggressive-v02 panel.

On development-v1 (150 maps, seeds 20000..20149), frozen v05 vs pressure-v1 is **207-93 (69.0%)**,
CI **57.9-80.1%**. Pressure uses accepted sabotage on **99.9%** of turns but builds on only
**56.7%**; games average **61.3 turns**, **267/300** close early. This supplies different tactical
pressure, not evidence that it reproduces the whole arena meta or should replace v05.

Height breakdown illustrates why match-ups must stay separate: v05 wins **24/44** height-20
games vs pressure-v1 but **36/44** vs v03 on the same maps. These small slices have wide intervals
and are exploratory. Whole-run v05 vs v03 is **209-91 (69.7%)**, CI **58.6-80.8%**.
New schema-3 reports and reserved confirmation pool are described in `benchmarking.md`.
No current-policy promotion is made by this infrastructure/reference work.

Third matched baseline, v05 vs aggressive v02: **217-83 (72.3%)**, CI **61.2-83.4%**,
300 games; 81.8 turns average. All three reports use the same 150 distinct maps and have zero
bot errors. In-process timing spikes in this last series (max 260/243 ms, 5/7 slow moves) remain
diagnostic, not a process-deadline pass. Reports: `build/bench/panel-v1-v05-development/`.

Verification: **127 tests, 264/264 exact replays**. Rebuilt frozen-v05 Player.java is 65,201
characters, but the fixed strict process sample **failed 1/4 games**: P1 turn 41 on 903182662,
66.6288 ms spent preparing/sending input, no timestamped response. This does not establish a
66-ms bot search or identify the cause. Preserve the failed report; investigate before claiming
submission timing readiness. No retry-to-green or upload; strategy source is unchanged.

## Independent simultaneous rollout v1 (2026-09-16, initial screen; later promoted as v06)

`current?USE_ROLLOUT_POLICY=1` selects a separate RolloutBot. It searches eight affordable turns
of both players' construction and sabotage, comparing earned score across two opponent route
priorities. Core SearchState provides reusable exact simulation; historical policies and existing
core implementations are unchanged. [Design and limitations](rollout-policy.md); evidence in DEVLOG.

Fixed development results, CG 486/36-ms search, two workers, paired generated maps:

- vs **v05: 170-130-0, 56.67%**, 300 games / 150 maps, Hoeffding95 **45.58-67.76%**.
  Mean score difference +277.17. The confidence gate is **not passed**.
- vs **v03: 137-63-0, 68.5%**, 200 games / 100 maps, CI **54.92-82.08%**.
- vs **pressure-v1: 149-51-0, 74.5%**, 200 games / 100 maps, CI **60.92-88.08%**.

No bot errors; candidate parameters unchanged throughout all 700 games. Reference results do not
replace the v05 gate. Keep USE_ROLLOUT_POLICY=false and current=v05; retain the candidate without
a new frozen version at the end of that screen. Development seeds 30000..30149 inadvertently
overlapped reserved confirmation-v1: preserve the screen as development evidence and retire that
reserve. The subsequent user-requested fixed confirmation is recorded below; see
[pool status](../bench/pools/README.md).

**214 tests**, **264 arena + 27 boss exact replays**. All 110 saved-state work/legality checks
completed at least one full comparison. Candidate submission 56,008 chars; strict process sample
**0/4 failures**, first max 579.57 ms, later 40.59 ms. In-process outliers include 196/296-ms
candidate/opponent maxima against pressure; do not infer arena timing safety.

Restored v05 submission **52,219 chars**, source `1b7ba04b299e7e31af66c8d2de45654cec2a78b80233dac673402aae825214a3`.
Its strict process sample **FAILED 4/4**, two later turns and two first turns. First input send
took 267..1178 ms, dominated by flush; one send exhausted the deadline. Cause remains open;
both reports retained, no retries or upload. Final Player is v05 **without a fresh process pass**.
Reports/artifacts: `build/bench/rollout-v1/`, `build/analysis/rollout-v1/`.

### Fixed confirmation: two independent 1000-game series (2026-09-16)

Unchanged `13aee8e` strategy/parameters vs v05, CG 486/36-ms budgets, two workers, sequential
runs on disjoint fresh maps; all prior benchmark map identities excluded before play:

- confirmation-v2, seeds 90000..90499: **605-395-0, 60.5%**, map-grouped Hoeffding95
  **54.43-66.57%**, gate **PASS**. P0 308/500, P1 297/500; score difference +338.303.
- confirmation-v3, seeds 91000..91499: **625-375-0, 62.5%**, CI **56.43-68.57%**, gate
  **PASS**. P0 319/500, P1 306/500; score difference +377.823.
- Predeclared pooled result: **1230-770-0, 61.5%**, **2000 games / 1000 distinct maps**,
  CI **57.21-65.79%**, gate **PASS**. P0 627/1000, P1 603/1000; score difference +358.063.

Zero bot errors. Source/class hashes, effective parameters and unchanged v05 Player verified.
Run 1 search max A/B 61/39 ms, slow 2/0; run 2 max **515/461 ms**, slow **307/180**, affecting
36 games for either side. Keep all games: no filtering or retries. In-process timing outliers
are not process-deadline measurements, and their cause remains unknown. Statistical passes
support the measured head-to-head advantage, not an arena timing-readiness claim.

**Not promoted**: this was a measurement request; current=v05 and USE_ROLLOUT_POLICY=false.
No packaging changes or new process sample. Consumed v2/v3 are no longer reserves; untouched
v4 is registered for future confirmation. Reports: `build/bench/rollout-confirmation-2x1000/`;
protocol, full outcome, hashes and timing limitations in DEVLOG.

### Fixed strict process check (2026-09-16)

Both unchanged submit-generated artifacts passed **42 games each**, 21 real maps with both
sides, vs frozen v04, serial games, **1000/50-ms deadlines, zero grace**, no IO instrumentation.
Candidate and control source/executable hashes match the earlier artifacts.

- **RolloutBot: 0/42 problems**, 3190 responses, no timeouts or nominal overruns. Startup max
  **662.59 ms**; ordinary-turn p95 **38.17 ms**, p99 **39.05 ms**, max **47.857 ms**.
  Two turns exceed the separate 45-ms headroom target; worst remaining margin **2.143 ms**.
- **v05: 0/42 problems**, 3115 responses. Startup max **668.31 ms**; ordinary-turn p95
  **34.18 ms**, p99 **37.63 ms**, max **40.09 ms**. One timely answer (32.57 ms) was consumed
  by the host at 66.58 ms; it was correctly accepted using the reader timestamp.

Conditional diagnostics were not triggered. Earlier large delays did not recur; their cause
remains unknown, not fixed. This is a local timing pass, not a guarantee about CodinGame or
a new strength comparison. Current/default/final Player remains v05, now freshly process-tested.
Exact candidate: `build/analysis/rollout-process-v1/Player-candidate.java` (56,008 characters),
source hash `d7f259ca8ecf70757458e69282f89c2597151e5e895c8d3753490707e1c92c8f`.
Reports and manifests in the same directory; full protocol, hashes and outcome in DEVLOG.

## Short own-build planning (2026-09-16, not promoted)

`current?USE_OWN_BUILD_PLAN=1`: up to three old-scored root routes, three actual affordable build
turns, at most 39 nodes / 27 leaves. Uses core scoring, earned points and existing terminal
vulnerability value. No opponent moves or future sabotage; no new future routes. Incomplete
comparisons fall back, and only the first planned placement set is emitted before normal sabotage.

Fixed direct screen vs v05: **147-153-0, 49.0%**, map-grouped 95% CI **37.91-60.09%**, 300 games /
150 paired generated maps, seeds 29000..29149. Gate not passed. Matched aggressive-v02 control,
200 games each on seeds 29000..29099: candidate **133-67 (66.5%)**, frozen v05 **150-50 (75.0%)**;
34 added / 51 lost wins, 115 unchanged. No promotion, tuning, confirmation or new frozen version.

All runs use CG 486/36-ms search and two workers, no bot errors. Candidate direct-screen mean
8.04 ms vs 6.99 ms, one 69-ms in-process outlier; not a process timeout diagnosis. **200 tests**,
**264 arena + 26 boss replays exact**. Candidate/restored-v05 diagnostic-enabled zero-grace
process samples each **0/4 failures**; first maxima 645.44 / 639.34 ms, later 35.80 / 35.17 ms.
Old startup failures remain unexplained; small successful samples are not a general safety claim.

Flag stays **off**. Final Player restored from **v05**, 50,038 chars; experiment artifact 82,376
chars, both below 100,000. Protocol, limitations, exact hashes and retained reports are recorded
in DEVLOG under own-build-plan-v1. Frozen strategy files and reference archives were not edited.

## Observed-threat defense priority (2026-09-16, not promoted)

`current?USE_DEFENSE_PRIORITY=1` adds a bounded first-build priority for a <=6-paint bypass of
already damaged active income. It uses core pathing/scoring, checks actual per-turn packing and
non-losing prefixes, and replans each turn. It is separate from the independent heuristic policy;
other experiments remain off, default current/selected submission remains v05.

Fixed direct screen: **141-157-2, 47.33%** vs v05 on **300 games / 150 paired generated maps**,
seeds 27000..27149; map-grouped Hoeffding 95% CI **36.24-58.42%**, mean score difference
**-183.69**, zero errors. **No promotion**, no confirmation run or parameter tuning.

Matched older/style control against `v02?W_FUTURE_TRACK=1.0,W_FUTURE_OWN=0.2`, 200 games each
on seeds 27000..27099: candidate **127-73 (63.5%)**, CI **49.92-77.08%**; frozen v05
**147-53 (73.5%)**, CI **59.92-87.08%**. Same map hashes, sides, opponent config and timing;
candidate adds 19 wins but loses 39, with 142 unchanged outcomes. These are descriptive
common-opponent results, not another direct comparison or a statistical proof of general harm.

All three runs use CG 486/36-ms budgets, two workers, default SEED/NOISE, with zero bot errors
or >50-ms in-process slow moves. Seven new defense tests plus merged ON parity/warmup: **189
tests passed**, **264 arena + 26 boss replays exact**. Full controls, process checks and source
hashes: defense-priority-v1 entries in DEVLOG; local reports under `build/bench/defense-priority-v1`.
Candidate process check: 0/4 failures, first max 807.38 ms (above 800-ms headroom target), later
39.23 ms. Restored-v05 check **failed 4/4, all at turn 0**, with input sending taking up to
1139 ms and no recorded response. Source restored to v05, but **not freshly timing-approved**;
preserve the failure for separate input/startup diagnosis, no retry-to-green or upload.

## Simple heuristic candidate (2026-09-16, not promoted)

`current?USE_HEURISTIC_POLICY=1` selects an independent known-board policy: sticky sabotage,
local ownership/paint route priorities, avoiding its target region, fixed path-query cap and
emergency-clock protection. No general position evaluator or reply search. Details and debug
commands: [heuristic-policy.md](heuristic-policy.md). Default current and selected Player remain v05.

Fixed screen vs v05: **126-174-0, 42.0%**, 300 games / 150 paired generated maps, seeds
22000..22149; map-grouped CI **30.9-53.1%**, mean difference **-205.7**. No promotion.
Mean local move time **1.32 vs 4.78 ms**; this is timing on different match trajectories, not an
isolated speed benchmark. Independent style check vs pressure-v1: **104-95-1, 52.25%** on
200 games / 100 paired maps, CI **38.7-65.8%**; no reliable advantage or matched v05 comparison.
Both runs have zero bot errors. No parameter tuning or confirmation games after these results.

**169 tests**, arena **264/264** and boss **21/21** replays exact. Candidate and restored v05 each
passed one strict four-game process sample: later-response maxima **24.39 / 34.80 ms**, respectively.
These small checks do not resolve the earlier unexplained host input-send stall. This simple v1
is a runnable experimental baseline, not evidence that heuristic policies cannot climb Silver.

Detour follow-up: `current?USE_HEURISTIC_POLICY=1,USE_HEURISTIC_DETOUR=1` adds capped recovery
paint on existing connections (up to 16 extra path queries), with unchanged construction.
Matched v05 screen on seeds 25000..25149: **ON 135-165 (45.0%)**, CI **33.9-56.1%**;
**OFF 140-160 (46.7%)**, CI **35.6-57.8%**; 300 games / 150 paired maps each. The feature adds
31 wins and loses 36 on identical map/side match-ups, with no other config difference. No
promotion or established advantage; this is not a direct ON-vs-OFF match. Pressure style check:
**110-90 (55.0%)**, 200 games, CI **41.4-68.6%**; no matched OFF pressure control was run.

All runs have zero errors. Main-screen mean move time ON/OFF **1.39 / 1.22 ms**, on different
trajectories; not an isolated cost measurement. **177 tests**, **264 + 21** exact replays,
candidate/restored-v05 strict process samples **0/4 problems** each (later maxima **30.39 / 34.58 ms**).
Keep both new policy and detour selector default-off; final Player is frozen v05. Full protocol,
pre-benchmark mirror correction, artifact hashes and limits are in DEVLOG/heuristic-policy.md.

## Frozen submission versions

`BotFactory` loads fixed defaults for v01ÃÂÃÂ¢ÃÂÃÂÃÂÃÂv05 from `src/main/resources/btk/bot/versions/`, then
applies explicit overrides. Early snapshots preserve the effective defaults at `8364ad5`,
while later versions record their promoted configuration; they do not retrospectively
reconstruct the original defaults used by every historical bench.
When freezing a new version, add its parameter snapshot as well as its source and factory entry.
See `benchmarking.md` for the complete procedure.

The rows below are historical: their original benchmarks alternated sides across different maps
and used per-game Wilson intervals. They have not been relabelled as results of the new paired
benchmark. New reports use map-grouped Hoeffding bounds for the statistical promotion gate.

| version | date | change | bench vs prev | CG result |
|---|---|---|---|---|
| spatial-ppo-v10 | 2026-10-11 | Continue original V4 model/Adam/RNG for 64 full-corpus epochs; same model/loss/runtime. | Vs v4: **66W/34L/0D / 100**; practical-v4 vs v09: **0W/300L/0D / 300**, STOP_FUTILITY_300. Strict process 8/8 clean. | Not uploaded; strength rejected, stage 3 open. [Evidence](spatial-ppo-v10.md). |
| spatial-ppo-v9 | 2026-10-10 | Add distance-weighted town context; unchanged v4 corpus and fixed 16-epoch imitation recipe. | Vs v4: **42W/58L/0D/100**; practical-v4 vs v09: **0W/300L/0D / 300**, STOP_FUTILITY_300. Strict process 8/8 clean. | Not uploaded; strength rejected, stage 3 open. [Evidence](spatial-ppo-v9.md). |
| spatial-ppo-v8 | 2026-10-10 | Frozen v4/v7 proposals; paired score checkpoints2/4/6/8, absorbing terminals and deepest completed fallback. | Practical-v4 vs v09: **2W/298L/0D/300**, STOP_FUTILITY_300. Strict process8/8 clean. | Not uploaded; strength rejected. [Evidence](spatial-ppo-v8.md). |
| spatial-ppo-v7 | 2026-10-10 | Frozen v4; root construction alternative with a fresh conditional suffix; exact two-turn ranking. | Practical-v4 vs v09: **1W/299L/0D/300**, STOP_FUTILITY_300. Strict process 8/8 clean. | Not uploaded; strength rejected. [Evidence](spatial-ppo-v7.md). |
| spatial-ppo-v6 | 2026-10-10 | Frozen v4; two same-cost last-cell candidates, two exact turns, score-delta ranking; no training. | Practical-v4 vs v09: **1W/299L/0D/300**, STOP_FUTILITY_300. Strict process 8/8 clean. | Not uploaded; strength rejected. [Evidence](spatial-ppo-v6.md). |
| spatial-ppo-v5 | 2026-10-10 | Fixed v4 policy; 15-coefficient teacher value fit; two-disruption one-ply search. | V4: **1W/1L/98D/100**. Practical-v4 vs v09: **0W/300L/0D/300**, STOP_FUTILITY_300. Strict process 8/8 clean. | Not uploaded; strength rejected. [Evidence](spatial-ppo-v5.md). |
| spatial-ppo-v4 | 2026-10-10 | V2 warm start; hard v09 teacher actions, 60 complete games, 22,640 rows, 16 epochs. Same model/runtime; no PPO/value loss. | V2: **98W/2L/0D / 100**. Practical-v4 vs v09: **0W/300L/0D / 300**, STOP_FUTILITY_300. Zero game errors; strict process 8/8 clean. | Not uploaded; stronger than v2, strength rejected, no promotion. [Evidence](spatial-ppo-v4.md). |
| spatial-ppo-v3 | 2026-10-09 | V2 warm start; fixed four-opponent pool, 16,384 learner turns, 183 completed training games. Unchanged model/PPO/runtime. | V2: **6W/94L/0D / 100**. Practical-v4 vs v09: **0W/300L/0D**, STOP_FUTILITY_300. Zero errors; strict process 8/8 clean. | Not uploaded; regression against v2, rejected, no promotion. [Evidence](spatial-ppo-v3.md). |
| spatial-ppo-v2 | 2026-10-09 | Same spatial model; fixed 8,192-turn self-play, 92 completed training games, separate final/initializer weights. | Initializer: **96W/4L/0D / 100**. Practical-v4 vs v09: **0W/300L/0D**, STOP_FUTILITY_300. Zero errors; strict process 8/8 clean. | Not uploaded; strength rejected, no promotion. [Evidence](spatial-ppo-v2.md). |
| spatial-ppo-v1 | 2026-10-08 | Separate 3,439-weight spatial policy/value CNN; simultaneous exact-core self-play, full-plan PPO, generated Java inference. Fixed 256-turn technical training run. | Practical-v4 vs v09: **0W/300L/0D (0%)**, 150 fresh paired maps, STOP_FUTILITY_300. Zero errors; strict process 8/8 clean. | Not uploaded; no promotion. [Evidence](spatial-ppo-v1.md). |
| v00 Autoplace | 2026-09-13 | `AUTOPLACE` towards the first inactive desired pair. Wood 2 exit. | ÃÂÃÂ¢ÃÂÃÂÃÂÃÂ | not submitted |
| v01 Greedy Routes | 2026-09-13 | Dijkstra cheapest path per inactive pair; rank by `(free + own - opp) * turnsEarning / (turnsToFinish+1)`; spend 3 paint along the best path, leftover to next pair. Idles (`WAIT`) once every pair is connected. No DISRUPT. | vs v00: 66% [56-75], n=100, CG maps (Wood rules) | not submitted separately |
| v02 Route Evaluation | 2026-09-13 | Simulation-evaluated candidates: per pair, cheapest route (any tracks) + own route avoiding opponent tracks (steal); value = income delta after completion (mine ÃÂÃÂ¢ÃÂÃÂÃÂÃÂ opp) ÃÂÃÂÃÂÃÂ turns earning ÃÂÃÂÃÂÃÂ instability risk / (turnsToFinish+1). Free DISRUPT chosen by simulating ink of each region (opp income lost ÃÂÃÂ¢ÃÂÃÂÃÂÃÂ 1.2 ÃÂÃÂÃÂÃÂ own loss, + not-yet-earning tracks) / disrupts needed. Routing surcharge for unstable regions. `NOISE` param to break self-play mirrors. | vs v01: 100% (300-0), +29k pts. Ablations (both sides NOISE=0.2, n=300): no DISRUPT 97.3%, no STEAL 87.5%, no instability avoidance 54.8% [49-60]. | not submitted separately |

| v03 Risk Aware | 2026-09-13 | v02 + state value = income diff ÃÂÃÂ¢ÃÂÃÂÃÂÃÂ W_VULN ÃÂÃÂÃÂÃÂ worst single ink the opponent could do (my loss ÃÂÃÂ¢ÃÂÃÂÃÂÃÂ their loss, / disrupts needed, only regions on active paths with my tracks). Third candidate type: backup route for an active pair avoiding the most threatened region. Closing rule: an ink that leaves no connectable pair is taken only when ahead, never when behind. `HORIZON` param (default 100). `NOISE=0.1` default (deterministic mirrors gave 0-0 games). Opponent model (`USE_OPP_MODEL`, collision surcharge) implemented but off: 47-51% in ablations. | vs v02: 58.5% [51.6-65.1], n=200. Ablations (n=300): no backup 60.2% [54.5-65.5] sig.; W_VULN=0 & no backup 52.7%; USE_CLOSE=0 48% (self-play games rarely close); HORIZON 20/30/50 ÃÂÃÂ¢ÃÂÃÂÃÂÃÂ 52/53/43%. Process check: first turn ÃÂÃÂ¢ÃÂÃÂÃÂÃÂ¤ 660 ms, later ÃÂÃÂ¢ÃÂÃÂÃÂÃÂ¤ 38 ms. | **Silver, around 350/645**; Gold not yet open; usually wins boss games |
| v04 Network Extension | 2026-09-14 | v03 + idle extension: when no complete route improves evaluation, spend remaining paint next to own rails/towns; prefer uninkable town regions and avoid non-town regions at instability ÃÂÃÂ¢ÃÂÃÂÃÂÃÂ¥2. | vs v03: **64.7%**, W-L-D 194-106-0, n=300/150 paired maps, map-grouped 95% CI **[53.6%, 75.8%]**, avg score diff +405. Control vs v02: 54%, identical to v03 on the same 50 maps. Process check: 0/8 problem games, first turn ÃÂÃÂ¢ÃÂÃÂÃÂÃÂ¤745 ms, later ÃÂÃÂ¢ÃÂÃÂÃÂÃÂ¤43 ms. | Boss **7/7**; awaiting Silver arena result |
| v05 Disrupt Reserve | 2026-09-14 | v04 + 6-ms reserve for sabotage: cut route/idle search short on the original turn clock; evaluation formulas and CGRunner margin unchanged. | Confirmation vs v04: **57.7%**, W-L-D **575-421-4**, n=1000/500 new paired maps, map-grouped 95% CI **[51.6%, 63.8%]**, avg diff **+879.1**. Common opponents: v03 **69.3% vs v04's 61.3%**; aggressive v02 **70.0% vs v04's 67.7%**, each 300 matched games. Process check: **0/8** problem games, first <=676 ms, later <=34 ms. | Uploaded as agent **6736850**. User: rank 321 at 43%; API snapshot rank 333. First 56 fetched games: **38-18**, no own timeouts; placement ongoing |
| v06 Simultaneous Rollout | 2026-09-16 | Independent eight-turn simultaneous construction/sabotage rollout; six route proposals, two opponent priorities; exact confirmed policy and full parameter snapshot. Selected by the user. | Fixed confirmation vs v05: **1230-770 (61.5%)**, 2x1000 games / 1000 distinct paired maps, map-grouped 95% CI **57.21-65.79%**; both individual gates pass. Older checks: v03 **68.5%**, pressure-v1 **74.5%**, 200 games each. Earlier process 0/42; final packaged source **FAILED 1/42**, later response 50.462 ms. | User-reported upload: 153/645, previous v05 rank 270; exact deployed hash unverified; local timing issue remains open |
| v07 Network After Base | 2026-09-18 | QuickIncome ordinary search first; single-cell network actions only after complete ordinary comparisons. Exact 98-field measured snapshot; selected by the user. | Direct v06: **62.50% / 300**; matched fresh v05: **64.90% vs 56.90% / 1000 each**, +8.00 points, practical-v2 PASS. Independent QuickIncome ablation **59.6667% / 300** (diagnostic). 273 tests, 264 exact replays; final Player strict **0/8**, max later **44.7744 ms**. | Locally selected and packaged; not uploaded by agent. Pre-upload CG checkpoint: user reports QuickIncome **111 / 627**. [Promotion evidence](v07-promotion.md). |
| v08 Protected Opening | 2026-09-19 | v07 plus first-three-turn claims of protected town approaches (one cell per adjacent town per turn, opponent prefix discounted 0.25). Exact 107-field measured snapshot; selected by the user. | Direct v07: **62% / 300**; boss **13/40 vs cached v07 10/40**, practical-v3 PASS. 338 tests; default artifact 96,207 chars, strict panel **1/8** problem games (67.6 ms). | Selected locally 2026-09-19; timing readiness left open. [Promotion evidence](v08-promotion.md). |
| v09 Never Idle | 2026-09-20 | v08 plus leftover paint spent after the search on stable/protected free cells next to own rails (proposed-route and earning-rail preference); RHEA code removed from the shipped source. Exact 117-field measured snapshot; selected by the user. | Practical-v4 vs v08: **176-124 / 109-91 / 284-216 = 569/1000 (56.90%)** PASS_1000; boss **17/40 vs cached v08 13/40**, PASS. 355 tests, 292+259 exact replays; default artifact 95,278 chars, strict panel **0/8**, later <= 47.2 ms. | Selected locally and packaged 2026-09-20; upload is the user's action. [Promotion evidence](v09-promotion.md). |

## Paired benchmark checkpoint (2026-09-13)

Infrastructure verification at strategy commit `8364ad5`, using the fixed parameter snapshots
and two worker threads. No new version or strategy promotion:

- `current` vs `v03`: 200 games on 100 generated maps, seeds 1000..1099; W-L-D **86-82-32**,
  A **51.0%**, map-grouped 95% CI **[37.4%, 64.6%]**. No bot errors; statistical gate not passed.
  Report: `build/bench/paired-current-v03.json` (local, gitignored).
- `current` vs `v02`: 40 games on 20 maps from `replays/cg`; W-L-D **22-14-4**, A **60.0%**,
  map-grouped 95% CI **[29.6%, 90.4%]**. No bot errors; smoke check only, gate not passed.
  Report: `build/bench/paired-current-v02-replays.json` (local, gitignored).

Full commands, timing limitations and validation results are recorded in `DEVLOG.md`.

## What top Silver players do (`gradlew cgStudy -Pbot=current`, 107 replays, winner's moves)

- 97/107 games close early (no connectable pair), average ~50 turns. Disrupt on 92% of turns,
  first disrupt at turn 0-1 in 86% of games, targets mostly regions with opponent-only tracks.
- Place on 88% of turns, 2.15 cells/turn (baseline current/v03 study: 73%, 1.76; v04 idle
  extension: 100%, 2.44 ÃÂÃÂ¢ÃÂÃÂÃÂÃÂ it now overbuilds rather than idles).
  29% of their cells go to regions shared with the opponent (hostage effect), 20% to town regions
  (uninkable), 51% adjacent to own tracks.
- v04 agrees on 15.8% of winner placement cells; its 53% adjacency to own rails closely matches
  the winners' 51%. The reported 17% versus 31% income-raising rate is **among turns with
  placements**, not all turns. The hypothetical placement-only check ignores simultaneous
  sabotage and does not measure how the bot's own games develop.

These historical policy-study measurements are diagnostics, not arena outcomes. Counts such as
1.76 and 2.44 are **cells/turn**, not paint/turn. Legacy study counts only explicit PLACE_TRACKS
commands (not accepted AUTOPLACE expansion) and is not the player-specific loss-analysis tool.

## v04 arena diagnostic checkpoint (2026-09-14)

- Public API snapshot: `aabzac`, agent **6735801**, rank **477**, score **11.78**; placement
  completion was not independently established. Latest 50 completed games: **23 wins / 27 losses**,
  no server timeouts. This matchmaking-selected sample is not an independent strength benchmark.
- Height 14-17: **18/20 wins**; height 18: **4/10**; height 19-20: **1/20**. Opponents differ, so
  these are exploratory strata rather than a causal estimate of map size.
- Captured large-map states reproduce loss of the sabotage phase under tight search budgets;
  it runs after route search on the same timer. This also happens with idle extension disabled.
- Full findings, one-turn placement counterexamples and limits: `arena-analysis-2026-09-14.md`.
- No strategy change / no v05. Timing-aligned v04-v03 control on new seeds 4000..4149:
  **63.7%**, W-L-D **191-109-0**, 300 games, map-grouped 95% CI **[52.6%, 74.8%]**.
  The previous-version advantage persists under the smaller allowance; it does not establish
  broader arena strength. Equal 54% estimates against v02 from the old small control did not
  establish equivalence or prove non-regression.

## Default-off experiment: sabotage time reserve (2026-09-14)

Historical initial screen, before the confirmation below. `current?DISRUPT_RESERVE_MS=6` cuts
route/idle search short on the same turn clock; `0` (then the default) retains v04 scheduling.
Frozen v01-v04 do not read this field. Evaluation formulas, total budget and margin are unchanged.

- Screen vs v04: **54.3%**, W-L-D **162-136-2**, 300 games on 150 distinct downloaded CG maps,
  sides swapped, 2 threads, CG profile; map-grouped 95% CI **[43.2%, 65.4%]**, mean score diff
  **+636.5**, no bot errors. **Gate not passed**. Report: `build/bench/reserve6-v04-cg.json`.
- On two captured large-map t40 states, all 6 repeat probes now emit sabotage at 36 ms; both
  v04 and reserve=0 omit it in all 6. This verifies the local failure mechanism, not arena wins.
- Post-hoc height 19-20 subset: **57/92 wins (62.0%)**; smaller maps are mostly unchanged.
  This selected small subset is diagnostic, not an independent promotion benchmark.
- Experimental submission: 0/8 process problem games, including both sides of the two failure
  maps; first response <=663 ms, later <=37 ms. Not a measurement of CG-host timing.
- Initial decision: leave the flag **off**, without a 1000-game confirmation. The subsequent
  matched v03 check below supports further testing, but does not pass the direct-v04 gate.
  Partial-build safety and diverse-opponent validation remain open.

## Common-opponent follow-up: reserve=6 versus v03 (2026-09-14)

Both campaigns used new authentic generated seeds **5000..5149**, 150 identical distinct maps
(hashes verified), both sides, 300 games each, 2 threads, CG 486/36-ms search profile. Runs were
sequential: control then candidate; no strategy/default/threshold changes.

- v04 vs v03: **61.3%**, W-L-D **184-116-0**, map-grouped 95% CI **[50.2%, 72.4%]**,
  mean score diff **+380.8**. Report: `build/bench/v04-v03-holdout5000.json`.
- `current?DISRUPT_RESERVE_MS=6` vs v03: **69.3%**, W-L-D **208-92-0**, same-method CI
  **[58.2%, 80.4%]**, mean score diff **+813.3**. No bot errors in either campaign.
  Report: `build/bench/reserve6-v03-holdout5000.json`.
- Matched games: **33 added wins, 9 lost wins, 258 unchanged**; net **+8 percentage points**.
  Map-pair totals improved on 24 maps, worsened on 3, tied on 123. A diagnostic paired-map
  percentile bootstrap (20,000 samples, seed 20260914) estimates the difference against v03
  at **[+4.3, +11.7] pp**. This approximate interval is not the acceptance gate; it does not
  account for host/JIT/order effects or different arena opponents. Comparison report:
  `build/bench/reserve6-v03-holdout5000-comparison.json`.
- Decision: retain as a promising, default-off candidate, not a disproven improvement. The old
  direct-v04 result remains inconclusive. v03 is a related reference, not diverse arena coverage;
  beating it more often is not transitive proof of beating v04. No v05 or threshold change.

## v05 confirmation and promotion (2026-09-14)

- Fixed candidate from **cb0f440**, `DISRUPT_RESERVE_MS=6`, against v04 on new authentic seeds
  **6000..6499**: 1000 games / 500 distinct paired maps, 2 threads, CG 486/36-ms profile.
  **57.7%**, W-L-D **575-421-4**, map-grouped CI **[51.6%, 63.8%]**, no bot errors.
  Existing gate passed without changing the criterion. Report: `build/bench/reserve6-v04-confirm6000.json`.
- Aggressive reference is the fixed spec
  `v02?W_SELF_LOSS=0.5,W_FUTURE_TRACK=2.0,W_FUTURE_OWN=0.1`, not a new independent strategy.
  On the same 150 fresh maps (seeds **8000..8149**, both sides, 300 games each), v04 scores
  **67.7% (203-97)** and reserve=6 **70.0% (210-90)**. Candidate adds **14** wins and loses **7**;
  279 unchanged. This small positive difference does not establish equivalence/non-inferiority
  or arena strength. No errors. Reports: `build/bench/{v04,reserve6}-v02aggressive8000.json`.
- Together with the earlier matched v03 check, this supports promotion after the primary gate.
  Frozen as **v05** with the full confirmation-report parameter snapshot; current defaults now
  enable the 6-ms reserve. Evaluation formulas and v01-v04 source/snapshots are unchanged.
- `test`: **72 passed**; `cgValidate`: **158/158** exact; `submit`: compiled;
  process checks: **0/8** problem games, including both sides of the two large failure maps.
  First response max **676 ms**, later max **34 ms**, no local timeouts. CG-host timing differs.
- Ready file: `build/submit/Player.java`, SHA-256
  `5307e4f0f53b39ef8768d364e24fcf701d4f9ca3eafa444aa23a38d3a34a9c5a`.
  Manual boss/arena verification remains pending; no CG upload was performed.

## v05 arena checkpoint and build-income guard screen (2026-09-14)

- User reports rank **321** at **43%** placement (~54 games); API fetch snapshot shows rank
  **333**, score **18.11**, agent **6736850**. These are provisional observations, not matched
  comparisons with the remembered v03 rank (~335). The public API does not expose source hash.
- **56** fetched games: **38 wins / 18 losses**, no own server failures; one win includes an
  opponent timeout. All **214/214** corpus replays reproduce exactly. Height 19-20 record:
  **11/18 wins** (previous v04 sample 1/20); different opponents/placement stages prevent a
  causal version comparison. In losses, sabotage frequency is **75.8%**, versus v04's earlier
  **40.1%**. Report: `build/analysis/agent-6736850.json`.
- Harmful-build examples persist: **24** simultaneous-turn counterexamples in **14/18** losses.
  Known-board-only examples are checked in for Reh934 t27 (-26 income difference), fsicre
  t52 (-34), and older DiogoGiassi t46 (-38). RTJ t13's -71 requires the opponent's simultaneous
  sabotage; its construction alone improves income difference by +44, so it is a different
  prediction problem. These deltas are not additive match-score losses.
- Tested `current?GUARD_BUILD_INCOME=1`: veto candidates whose actual affordable prefix or
  idle placement reduces raw current income difference, using core simulation. It finds
  alternative builds on the three known-board regressions without using future opponent moves.
- Screen vs v05: **48.1%**, W-L-D **206-222-0**, **428 games / 214 distinct downloaded maps**,
  map-grouped CI **[38.8%, 57.4%]**, mean score diff **-58.1**, no errors. Gate not passed.
  Report: `build/bench/guard-income-v05-cg428.json`. No confirmation or promotion.
- Keep **v05** and `GUARD_BUILD_INCOME=false`. The hard veto is not supported as an overall
  improvement, despite fixing selected positions; the wide interval does not prove inferiority.
  Next hypothesis: change affordable placement order (e.g. build the other end first) to delay
  a harmful shortcut without discarding a valuable future route. No v06 or new submission.

## Unpromoted experiment: build direction (2026-09-14)

- `current?CHOOSE_BUILD_DIRECTION=1,GUARD_BUILD_INCOME=0` preserves the selected route but
  compares the affordable cells from either end using known-board income difference.
  Ties retain forward order; a temporarily losing route is not rejected.
- Versus **v05**, all **214 distinct CG arena maps**, both sides, **428 games**, 2 threads,
  CG timing: **52.3%**, **224-204-0**, map-grouped 95% CI **43.1-61.6%**, average score
  difference **+76.8**, no bot errors. Report: `build/bench/build-direction-v05-cg428.json`.
- **Not promoted**: the interval includes 50% and the point estimate is below 55%.
  Keep both experimental flags false; current and the existing submission remain v05.
  Fixes selected Reh934/fsicre prefixes but not the captured DiogoGiassi shortcut.
  No confirmation/older-opponent/process promotion checks after this failed screen.

## Unpromoted experiment: early ready-to-ink sabotage fallback (2026-09-14)

- `current?EARLY_DISRUPT_FALLBACK=1,GUARD_BUILD_INCOME=0,CHOOSE_BUILD_DIRECTION=0` saves a
  deterministic instability-3 target before route search. The normal post-build sabotage scan
  remains authoritative when complete; a target affected by placements is rescored before use.
- Versus **v05**, all **214 distinct CG arena maps**, both sides, **428 games**, 2 threads,
  CG timing: **49.8%**, **212-214-2**, map-grouped 95% CI **40.5-59.1%**, average score
  difference **-28.9**, no bot errors or nominal-budget slow moves. Report:
  `build/bench/early-disrupt-v05-cg428.json`.
- **Not promoted**: the point estimate is below 55% and the interval includes 50%.
  `EARLY_DISRUPT_FALLBACK` stays false; current and the uploaded submission remain v05.
  No confirmation, older-opponent or process promotion checks were run.

## Unpromoted experiment: top-4 routes vs top-2 likely replies (2026-09-14)

- `current?USE_REPLY_RERANK=1,BUILD_BEAM_WIDTH=4,OPP_REPLY_WIDTH=2` keeps four distinct
  affordable build prefixes, predicts two deterministic opponent build-and-sabotage replies,
  resolves each pair through the core, then selects the prefix with the best worst-case value.
  The original v05 choice remains the timeout fallback; all earlier failed flags are off.
- Versus **v05**, all **214 distinct CG arena maps**, both sides, **428 games**, 2 threads,
  CG timing: **46.5%**, **199-229-0**, map-grouped 95% CI **37.2-55.8%**, average score
  difference **-50.8**, no bot errors. Candidate search averaged **9.75 ms** versus **6.50 ms**
  and recorded 3 nominal-budget slow moves. Report:
  `build/bench/reply-rerank-v05-cg428.json`.
- **Not promoted**: the point estimate is below 55% and the interval includes 50%.
  `USE_REPLY_RERANK` stays false; current and the uploaded submission remain v05.
  No confirmation, older-opponent or process promotion checks were run after the failed screen.
- Predeclared fresh-map diagnostics on the same 150 generated maps, 300 games each:
  K=4/R=1 scored **49.3% (148-152-0)**, CI **38.2-60.4%**; K=2/R=1 scored
  **55.7% (166-132-2)**, CI **44.6-66.8%**. Reports:
  `build/bench/reply-rerank-{k4r1,k2r1}-v05-gen10000.json`.
- K=2/R=1 is the better point estimate and cheaper average search of the two diagnostics, but
  its lower bound does not exclude 50%. Both stay off; the sample was not adaptively extended
  and no independent confirmation was started.

## Retained narrow reply rerank: pressure follow-up (2026-09-15)

Fixed candidate `exp-reply-rerank?BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1`, pinned engine-v1 and
full snapshot, other experiments off. Three sequential 300-game match-ups on development-v1
(150 identical paired maps, seeds 20000..20149), two workers, nominal CG 486/36 ms:

- Candidate vs v05: **172-128-0, 57.3%**, map-grouped CI **46.2-68.4%**; primary gate not passed.
- Fresh v05 vs pressure-v1 control: **208-92-0, 69.3%**, CI **58.2-80.4%**.
- Candidate vs pressure-v1: **198-102-0, 66.0%**, CI **54.9-77.1%**. Beating the reference is
  not the candidate's promotion gate; its pressure result is below v05's control.

Matched map/side comparison against pressure: **46 added wins / 56 lost wins / 198 unchanged games**,
net **-3.33 pp**; map-pair totals improve/worsen/tie on **26/40/84** maps. Descriptive evidence,
not proof of statistical inferiority. Candidate mean search against pressure is **10.46 ms**
versus v05's **7.58 ms**, on different evolving trajectories. All 900 games have zero bot errors
and nominal-budget slow moves; no new process timing check or readiness claim.

**Keep disabled, no v06 or confirmation.** No parameter tuning, current/source/runtime changes
or Player.java rebuild. This already-inspected development pool is not independent confirmation.
Reports: `build/bench/narrow-reply-pressure-v1/`; full protocol, hashes and slices in DEVLOG.
Next diagnostic: test predicted responses and prefix ranking on shared states before widening
or retuning the beam. The results alone do not identify prediction error as the cause.

## Shared-state K2/R1 diagnosis (2026-09-15)

Tools-only follow-up: 24 newly captured games on six fixed development maps, both sides and
v05/K2-R1 source trajectories against pressure. 110 observations, 108 distinct state/perspectives;
NOISE=0 fresh-instance probes, no strategy changes. See [reply-study.md](reply-study.md).

- First-build model active on 81 observations: exact nonempty construction forecast **12/55**,
  disruption targets **61/81**. These are selected-state prediction metrics, not arena accuracy.
- Actual-response oracle improves the choice under the SAME heuristic in **4 observations /
  3 unique states**. Eleven observations prefer a projected route over a better immediate gain;
  that can be investment, not a proven evaluation bug.
- First prefix matches nominal/ample full play in **81/81** cases. Five of all 110 full actions
  differ only in subsequent construction, with unchanged first prefix and sabotage target.
- Keep disabled; no new strength bench, promotion, confirmation pool or Player rebuild.
  Next hypothesis: inspect joint build/own-sabotage valuation, not wider search from this evidence.

## Joint first-build / own-sabotage diagnosis (2026-09-15)

Same saved 110 observations, no new games or policy changes. The real sabotage chooser sees
known state and each affordable prefix/full planned route, never the recorded opponent action.
Targets differ across prefixes in **8/81** candidate observations. Including each prefix's target
changes the predicted joint choice in **3 unique states**; oracle projected value improves in 3,
worsens in 0. Immediate score effects are **+40 / 0 / -11**, not recoverable final-match points.

One of those examples values **the original prefix + another already known target** above either
prefix with its normal chooser's target. This supports testing a small set of joint action pairs,
not forcing each route to keep one sabotage target. Current/v05/Player stay unchanged and reply
reranking stays disabled. Method, regression examples and limitations: [joint-reply-study.md](joint-reply-study.md).

## Unpromoted experiment: bounded joint build/sabotage (2026-09-15)

`current?USE_REPLY_RERANK=1,BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1,USE_JOINT_ACTION=1` compares
two prefixes x at most four known-state targets, retaining the old pair on ties/incomplete setup.
Committed pairs emit exactly the evaluated prefix/target; otherwise normal leftover building
remains. Defaults are unchanged. See [joint-action-experiment.md](joint-action-experiment.md).

- Fixed 300 games / 150 paired generated maps, seeds 21000..21149, CG 486/36 ms, two workers:
  candidate vs v05 **130-169-1, 43.5%, CI 32.4-54.6%**. Gate failed; no v06 or confirmation.
- Same maps vs pressure-v1: candidate **196-104, 65.3%**, fresh v05 **195-105, 65.0%**.
  Matched outcomes: **57 added wins / 56 lost / 187 unchanged**, not a transfer improvement
  or proof of equivalence. No comparison with a historical K2/R1 percentage on other maps.
- Saved-state cost check: candidate/control mean **13.50/12.81 ms**, 5 improved pairs on 110
  observations; 72 complete scans, no call >36 ms. Mechanism/cost evidence, not strength.
- **162 tests, 264/264 exact CG replays**. Candidate 93,881 characters, restored-v05 65,269;
  both strict fixed process samples **0/4 failures**, first/later reader maxima respectively
  **669.64/37.24 ms** and **650.75/38.88 ms**. The prior process-input stall remains unexplained.
- Keep USE_JOINT_ACTION=false and USE_REPLY_RERANK=false. Player.java is restored frozen-v05
  with the current Params/runtime; source hash changed due to the new unused flag, not strategy.
  Original retained experiment ids/configurations/code hashes are preserved by one shared
  two-class archive, never shipped. Next diagnostic is a direct on/off joint-flag ablation
  within K2/R1 before inferring whether the regression belongs to this flag or the base branch.

## Diagnostic: direct joint-on/off K2/R1 ablation (2026-09-16)

Same current implementation at `b4d8424`, both K2/R1; full 47-field configurations differ ONLY
in `USE_JOINT_ACTION`. One fixed 300-game run / 150 paired generated maps, seeds 21000..21149,
reusing the joint-screen development pool. Two workers, CG 486/36 ms, NOISE=0.1; no new policy.

- Joint-on vs joint-off: **139-159-2, 46.7%**, map-grouped CI **35.6-57.8%**; mean score
  difference **-150.0**. P0 **67-82-1**, P1 **72-77-1**. Zero bot errors.
- Accepted sabotage **80.7% / 82.3%**, construction **91.8% / 92.0%**; paint/turn
  **2.708 / 2.730**. These are different game trajectories, not a same-state causal comparison
  or evidence that more actions would improve play. Mean game length **76.5** turns.
- Search mean **8.13 / 7.69 ms**, max **52 / 46 ms**, >50-ms slow counts **1 / 0**;
  nominal in-process measurements, not process timeouts. No new artifact or process check.
- **Inconclusive; keep off.** No confirmed benefit, but the interval also does not establish
  inferiority or equivalence. This comparison cannot apportion the prior loss against v05
  between K2/R1 and the joint flag; matchups are not transitive. No tuning or confirmation.
- Source/defaults/frozen references and v05 Player.java unchanged. Existing **162-test** results
  remain up-to-date; fresh **cgValidate 264/264 exact**. Report, hashes and next mechanism audit:
  [joint-action-experiment.md](joint-action-experiment.md#direct-onoff-follow-up-2026-09-16).

## Offline diagnostic: adaptive sabotage forecast (2026-09-15)

- No strategy change or v06. Five fixed experts plus adaptive weights are evaluated by
  `cgForecast`, outside the submitted bot. 56 games of agent 6736850: 40 development games,
  16 holdout games, split by opponent agent. Existing inspected corpus, not fresh confirmation.
- Holdout top-1: adaptive **69.9%**, fixed mixture **57.7%**, persistence **68.2%**.
  Mean-per-opponent log-loss improvement versus fixed: **+0.0928**, 95% CI **[-0.0450, +0.2220]**;
  versus development-selected persistence: **+0.2292 [+0.0861, +0.3841]**.
- Diagnostic gate **not passed** against both controls. No gameplay bench or promotion;
  current stays v05. Protocol, limitations and command: `sabotage_forecast.md`.
- Restored `Player.java` from frozen v05 strategy with current shared runtime: **62,821 / 100,000
  characters**. Generated manifest records parameters and SHA-256. 0/8 process problem games
  versus v04, including both sides of two captured large maps; a 180-ms measured later response
  without a registered timeout prevents claiming strict arena timing compliance.

## Unpromoted experiment: interrupt nested vulnerability evaluation (2026-09-15)

- `current?INTERRUPT_VULN_EVAL=1,PROFILE_SEARCH=0` checks the existing build cutoff between
  region simulations, discards incomplete candidate values, and retains the best completed
  candidate. No changed evaluation weights, sabotage reserve or warmup. Other experiments off.
- Fixed screen vs **v05**, **300 games / 150 generated paired maps**, seeds 12000..12149,
  CG timing, 2 workers: **51.3% (154-146-0)**, map-grouped 95% CI **40.2-62.4%**, average
  score difference **+38.9**, no bot errors. Candidate search averaged 7.06 ms (max 61, one
  nominal-budget slow move), reference 7.01 ms (max 36). Report:
  `build/bench/interrupt-eval-v05-gen12000.json`.
- **Not promoted**: neither the practical nor confidence gate passed. Flag remains false,
  current remains v05; no confirmation or older-opponent promotion bench. The separately
  predeclared process checks are timing diagnosis, not promotion evidence.
- Recorded-state profiling suggests tighter phase timing: later build p95 32.30 -> 30.54 ms,
  calls above 31 ms 90 -> 7 of 396. This is instrumented, sequential before/after evidence,
  not a controlled CPU speedup estimate or guarantee of CG deadlines. 214 incomplete evals
  were discarded. See DEVLOG for artifact checks and the unresolved warmup gap.

## Runtime fix: isolated route warmup (2026-09-15)

- **v05 strategy unchanged; no v06 or strength claim.** MainBot now warms a disposable instance
  of the selected strategy with 36-ms calls within the existing 500-ms allowance. Live RNG and
  game state are isolated. Generic Bot warmup remains unchanged for frozen strategy consumers.
- Fixed local process comparison against v04, 8 games each: old warmup first-response mean/max
  **626.94 / 696.84 ms**, isolated warmup **554.22 / 577.89 ms**; both 0/8 failures, later
  maximum **38.22 / 38.24 ms**. Sequential timing sample, not a statistical speedup claim.
- Runtime acceptance gate passed: semantic tests green, candidate no protocol/deadline failures,
  all first responses below 800 ms. `ISOLATED_ROUTE_WARMUP=true` is now the runtime default;
  failed strategy flags remain off. In-process strength benches do not exercise this hook.
- Final default v05 artifact independently rechecked: 0/8 failures, first max **620.88 ms**,
  later max **38.115 ms**, 109 tests and 214/214 exact replays. **64,775 / 100,000 characters**,
  source SHA-256 `748b564a77a0d3293a925913f396a60d4598783501bc4a74a9a6624f6fadbbc5`.
  Timed decisions can differ with JIT/hardware even though ample-time strategy semantics match.

## Unpromoted experiment: quick income portfolio slot (2026-09-16)

- `current?USE_ROLLOUT_QUICK_ROUTE=1` retains one <=6-paint positive-own/net-income route
  inside the existing six slots while our income is zero. Reuses existing path proposals;
  unchanged depth, opponent model, sabotage, budgets and other parameters. Default-off.
- Fixed direct screen vs **v06**: **160-134-6**, **54.33%** with half credit for draws;
  **300 games / 150 distinct paired maps**, seeds 40000..40149, CG 486/36-ms budget, two workers.
  Map-grouped 95% CI **43.24ÃÂÃÂ65.42%**, zero errors. **Gate not passed**.
- Separate older-opponent check vs **v05**: **124-76-0 (62.0%)**, **200 games / 100 paired maps**,
  seeds 41000..41099, CI **48.42ÃÂÃÂ75.58%**, zero errors. No matched v06 control in this panel;
  the historical v06/v05 percentage is not a baseline for estimating a new improvement.
- No promotion, tuning or confirmation: selected **v06**, flag **false**, no v07 or upload.
  The mechanism works on the captured opening (4-paint candidate retained), but population
  improvement is unproven. The next question is selection/value/clock coverage of retained routes.
- **225 tests pass**; **264 arena + 30 boss replays exact**. Candidate strict process sample
  **0/8 problems**, max first/later response **747.670/47.118 ms**. Final default-off process
  **0/8 problems**, max first/later **718.680/45.982 ms**.
  Final coverage audit found repeated sides in the even-sized process map pool; original reports
  retained, four previously missing map/side combinations checked per exact artifact, **0/4**
  each. Combined **0/12 problems each**, all four maps on both sides. See
  [the experiment report](quick-income-experiment.md) for the protocol deviation and all results.
  Bench maxima A/B were **58/40 ms** vs v06 and **96/36 ms** vs v05; 2/0 slow moves each.
  These outliers are retained and do not establish a cause or explain the older v06 timeout.
- Full reports: `build/bench/quick-income-v1/`; durable parameters, hashes and paired outcomes:
  `docs/evidence/quick-income-v1.json`. Updated default Player: **57,405 characters**, SHA-256
  `5807068e5d50c7bb3acd395866b74f8c9a972f7c8b19fc323dfda18cff413266`.

## Known gaps (to become versions)

- Self-play does not reproduce the top Silver meta (v04 still averages 84 turns vs about 50):
  benches against frozen versions may not
  transfer. Needs a more aggressive reference opponent or evaluation against replay states.
- v04 overbuilds locally (100% of turns vs 88% for winners); refine which extensions are useful
  using replay-state evaluation rather than maximizing paint usage itself.
- Pre-emptive disrupts of empty regions on the opponent's likely corridors; disrupt every turn.
- No lookahead: a 2-3 turn plan search over (placements, disrupt) with the engine.
- Parallel benches show 100-300 ms outliers under 13 threads. The 2026-09-15 strict process
  check (zero grace) also caught one local v05 deadline miss in eight games: 50.7868 ms on
  903509641, P0 turn 53; consumer resumed at 56.2206 ms. The other seven games passed.
  This does not establish CG-host timeout frequency or prove a JIT/GC cause. Profile large-map
  work and first-turn headroom before the next strategy experiment; source/artifact unchanged.


## Quick-income reevaluation under practical-300-1000-v1 (2026-09-16)

- User explicitly adopted a new practical policy and applied it to the unchanged latest
  candidate, `current?USE_ROLLOUT_QUICK_ROUTE=1`. Historical verdicts above remain intact.
- Reused 300-game screen: **160-134-6, 54.33%**, below the new 60% fast-pass threshold.
- Separate fresh direct continuation vs **v06**: **525-465-10, 53.00%**, **1000 games / 500
  distinct paired maps**, seeds 42000..42499, CG 486/36-ms budgets, two workers, zero errors.
  New practical threshold **55% not met**; descriptive CI **46.93-59.07%** is not the gate.
- Common-opponent comparisons vs v05 **skipped after direct failure**, as predeclared. No
  reinterpretation of the old unmatched 62% as evidence of improvement over current.
- **Not promoted; v06 selected, quick-route flag false.** All 39 original class hashes and
  full parameters match; 4324 older map identities excluded. Strategy/Player unchanged.
  Search maxima A/B 85/97 ms, slow moves 3/5, retained; no new process-readiness claim.
- 226 Java tests, six Python checker tests, installed skill valid, 264/264 arena replays exact.
  Protocol, results and durable evidence: [quick-income-practical-v1.md](quick-income-practical-v1.md).


## 2026-09-16 - quick-followup-early-v1, not promoted

- User requested all three quick-income follow-ups in order; independent variants, fixed v06
  control and v05 common opponent. Protocol: docs/quick-income-followups.md, practical v2.
- Hypothesis: quick root in last slot may miss complete comparisons. Default-off EARLY flag
  compares it after root zero, preserving portfolio, continuations, widths and fallback.
- Shared-state diagnostic: 77 positions from wins AND losses; nominal full quick coverage
  original/early 53/61, selected 14/21. Fresh NOISE=0 instances, not original arena timing.
- Screen 300: 164-130-6, 55.67%; fresh 1000: 489-495-16, 49.70%. Zero bot errors.
  NOT PASSED; no matched v05 stages, retry, promotion, upload or process readiness claim.
- Windows Modern Standby interrupted both series (maxima up to 266542 ms), independently
  confirmed by Kernel-Power events. All games preserved. Temporary system sleep inhibition
  started during continuation; insufficient on battery, replaced by a task-scoped system AND
  display request after the series. No global power-setting change. Timing is limited evidence.
- 227 tests pass; 264/264 replay validation exact. Recorded source/class identities unchanged.
  Durable full per-map evidence and diagnosis: docs/evidence/quick-followup-early-*.json.
- Commit this completed experiment before activating value; all experiment defaults remain off.


## 2026-09-16 - quick-followup-value-v1, stopped at 52 percent

- Independent second follow-up: early flag OFF; quick selection ranks expected net earnings
  per actual affordable construction turn using core placements on a private scratch board.
  No new path queries, wider search, core changes or retuning. Protocol fixed before play.
- Mechanism tests on both sides: more income at equal two-turn completion; three river cells
  need three turns despite six paint. 229 Java tests pass; default v06 equivalence preserved.
- Fresh 300 paired games, seeds 110000..110149: 150-138-12, 52.00%, zero errors.
  practical v2 requires strictly MORE than 52% to continue: STOP_SCREEN_300. No extra games,
  common-opponent stages, retry, promotion or new process claim. Max search A/B 51/42 ms.
- Same 77 recorded states: nominal quick retained/full/selected 70/64/10; ample 70/70/12.
  Diagnostics only. Evidence: docs/evidence/quick-followup-value-v1.json and diagnostic-v1.json.
- Source/class hashes verified unchanged across play. Commit completed experiment, retain OFF.


## 2026-09-16 - quick-followup-safe-v1; all three follow-ups complete

- Independent third follow-up, EARLY/VALUE OFF: retain a cheap quick route only if its net
  income gain survives the already predicted ink region. Reuse the existing core evaluation.
- Both-side regression demonstrates a six-paint survivor replacing a destroyed three-paint
  route, and unchanged cheap choice without a threat. 230 Java tests pass.
- Fresh 300 paired games, seeds 120000..120149: 153-142-5, 51.8333%, zero errors.
  STOP_SCREEN_300; no direct 1000 or matched v05 stages. Max search A/B 46/45 ms, slow 0/0.
- Shared 77-state diagnosis: nominal quick retained/full/selected 64/52/14; ample 67/66/19,
  one ample duplicate actual-action root skipped. Not a population strength test.
- All three experiments complete: 1900 new games on 950 distinct maps, zero bot errors,
  none passed practical v2. Do not compare unmatched percentages as causal improvements.
  Early series has confirmed standby interruptions; all data retained without retries.
- Selected v06 and Player SHA 5807068e5d50c7bb3acd395866b74f8c9a972f7c8b19fc323dfda18cff413266
  unchanged. All flags OFF; no promotion/upload or new process readiness claim.
- Both temporary system/display power requests released; no global power setting changes.
  Host events/request timestamps retained in docs/evidence/quick-followups-host-timing-v1.json.
- Reports/protocol: docs/quick-income-followups.md; complete per-map evidence and parameters in
  docs/evidence/quick-followup-safe-v1.json. Commit final completed experiment under AGENTS #8.

## 2026-09-16 - rollout-followup-diverse-v1

- Independent default-off diverse replies: choose another retained town connection before a
  reverse route, then a different corridor; same reply count, depth and path-query bounds.
- 133 recorded states: ample same-town-pair replies 91/101 -> 10/101, unchanged 1187 complete
  comparisons and 13753 queries, 23 changed actions. Mechanism evidence only.
- Fresh 300 paired games, seeds 130000..130149: 151-148-1 = 50.50%, zero errors.
  STOP_SCREEN_300 under practical-300-1000-v2. No 1000, common-opponent stage or retry.
  Search maxima A/B 59/45 ms, slow 1/0; elapsed 574.6 seconds. No process-readiness claim.
- 232 Java tests and seven gate tests pass; 264/264 arena replays exact. Source/class hashes
  matched across the fixed run. Frozen versions and selected Player unchanged; flag OFF.
- Protocol/results: docs/rollout-followups.md; durable full per-map evidence and diagnostic
  in docs/evidence/rollout-followup-diverse-v1.json and diverse-diagnostic-v1.json.
  Shared orchestration extracted without changing the old quick-variant protocol.

## 2026-09-17 - rollout-followup-replan-v1

- Independent default-off own-route reconsideration after four forecast turns, among the same
  retained routes. Core net income and remaining paint; no new paths or opponent replanning.
- 234 Java tests pass, including both-side priority reversal after partial construction,
  ink rejection, deadline invalidation, captured legality and default-v06 equivalence.
- 133-state study: ample unchanged 1187 comparisons/13753 queries, 1236 priority changes,
  12 changed actions. Nominal complete comparisons 558 -> 524; nine changed actions.
- Fresh screen 300: 154-141-5 = 52.1667%, zero errors. Strict >52% continuation applies.
- Independent 1000: 469-501-30 = 48.40%, zero errors, below 55%; NOT PASSED.
  Max search A/B 55/47 ms, slow 2/0; elapsed 1775.3 seconds. No matched-v05 stage or retry.
- All source/class identities verified before/after both stages. Core unchanged since 264/264
  exact replay validation. Retain flag OFF, selected v06 and original Player unchanged.
- Protocol/results: docs/rollout-followups.md; durable complete outcomes/parameters/identities
  in docs/evidence/rollout-followup-replan-v1.json; shared-state diagnostic alongside it.

## 2026-09-17 - rollout-followup-target-v1; all three complete

- Independent default-off second target for the winning construction only, after ordinary
  search. Existing completed shortlist; at most one additional full scenario comparison.
- 236 Java tests pass: distinct positive alternate, timeout/closure reset, both-side recorded
  middle-game coverage, unchanged construction, legal actions and default-v06 equivalence.
- 133 shared states: ample 105 extra comparisons / 17 alternate selections, same path queries;
  nominal 31 extra / four selected. The useful alternate often lacks time to be evaluated.
- Fresh 300, seeds 150000..150149: 136-144-20 = 48.6667%, zero errors; STOP_SCREEN_300.
  Max search A/B 87/59 ms, slow 2/2, elapsed 499.0 seconds. All timing outliers retained;
  no external-process readiness claim. No 1000, matched-v05 stage, retry or promotion.
- All three independent experiments complete: 1900 games / 950 disjoint fresh maps, zero
  bot errors. Diverse 50.50%/300; replan 52.1667%/300 then 48.40%/1000; target 48.6667%/300.
  Each failed the practical gate. Unmatched variant percentages do not rank the variants.
- Final audit recomputed every decision, checked map exclusions/pairing, identical diagnostic
  state hashes and single enabled experimental flags. 236 Java + seven policy tests pass;
  core unchanged since 264/264 exact replay validation. All experiment flags stay OFF.
- Selected v06 and Player SHA 5807068e5d50c7bb3acd395866b74f8c9a972f7c8b19fc323dfda18cff413266
  unchanged. Historical selected-v06 strict process timing issue remains open, unaffected.
- Protocol/results: docs/rollout-followups.md. Durable full evidence: rollout-followup-target-v1
  and target-diagnostic-v1 JSONs under docs/evidence; rollout-followups-final-audit-v1.json.

## 2026-09-17 - separate exact quick-income artifact for user's arena trial

- User requested the candidate beside, not instead of, selected Player.java. Exported the
  exact previously submit-generated archived source as build/submit/PlayerQuickIncome.java.
  57,404 characters; SHA 8fb0831f44a7f0ca98ada59371fc2dd7537d1743ac711f78c21a8d7e2023dbfd.
- Fresh Java 17 compilation and isolated gradlew procCheck, four individual maps x two sides
  vs v05, 1000/50-ms strict deadlines, no grace: 1/8 problem games. Map 904146009, P1,
  turn index 25 timed out. Response not observed; 66.002-ms consumer wait is not child runtime.
  No retries, discarded failures, code/timing changes, new strength claim or promotion.
- All 23 pre-existing submission files hash-identical, including selected Player, classes
  and manifest. Separate candidate metadata supplied. User will perform the arena trial;
  no upload done. Current strict process check is NOT PASSED, communicated with the artifact.
- Full artifact identity, preservation hashes and all per-turn reports retained in
  docs/evidence/quick-income-arena-v1.json; usage/status in docs/working-candidate.md.


## 2026-09-17 - spread-construction-v1 (not promoted)

QuickIncome plus default-off USE_ROLLOUT_SPREAD: compare two independent projects with
2+1 / 1+2 paint, maintain the split inside rollouts, finish affordable routes immediately.
Screen vs v06: 160-138-2 / 300 = 53.6667%; fresh direct stage: 502-490-8 / 1000 = 50.60%,
NOT PASSED at 55%. Common-v05 stages skipped. Fixed separate ablation vs QuickIncome:
150-141-9 / 300 = 51.50%, diagnostic only. Total 1600 games / 800 fresh maps, zero errors.
241 Java + seven gate tests pass; 264/264 arena replays exact. All 25 existing submission
files unchanged. Flag OFF, v06 selected, original QuickIncome remains development base.
Protocol, mechanism limitations and evidence: docs/spread-construction-v1.md.


## 2026-09-17 - network-single-cell-v1 (not promoted)

QuickIncome plus default-off USE_ROLLOUT_NETWORK: evaluate up to 24 legal single-cell
network changes, retain two positive net-income roots, unchanged rollout and sabotage.
Screen vs v06: 145-155-0 / 300 = 48.3333%, STOP_SCREEN_300 under practical-v2.
No direct 1000 or common-opponent continuation. Fixed independent NETWORK-only ablation
vs QuickIncome: 152-148-0 / 300 = 50.6667%, diagnostic, no established improvement.
600 games / 300 distinct fresh paired maps; zero errors, every timing outlier preserved.
254 Java tests and seven gate tests pass; 264/264 arena replays exact. All 25 existing
submission files unchanged. No new process-readiness test after strength failure.
Fixed-state diagnostic: ordinary comparisons 937 -> 804 at nominal time, unchanged 1187
with ample time; useful known singleton evaluated in 70/70 applicable states.
Retain flag OFF, original QuickIncome research base and selected v06. No promotion/upload.
Protocol, limitations, complete paired outcomes and audit: docs/network-single-cell-v1.md.


## 2026-09-17 - network-after-base-v1 (verified candidate, not promoted)

Default-off USE_ROLLOUT_NETWORK_AFTER_BASE on original QuickIncome. Complete the ordinary
search first, then use remaining time for the unchanged singleton network generator (24 cells,
2 roots, 12 ms reserve). Old NETWORK OFF; full ordinary comparison required before late phase,
partial replies cannot replace the saved answer. New actions require strictly better rollout value.
Direct vs v06: 187-112-1 / 300 = 62.50%, PASS_300. Matched 300 each vs v05: both 172-128 = 57.3333%;
separate matched 1000 each: candidate 649-351 = 64.90%, v06 569-431 = 56.90%, +8.00 points, PASS.
Independent AFTER_BASE-only ablation vs QuickIncome: 179-121 / 300 = 59.6667%, diagnostic only.
3200 games / 950 distinct fresh maps, zero errors; 261 Java tests, 264 exact arena replays.
113 final audit checks pass. Exact 75,290-character candidate: strict process 0/8 problem games,
max later response 44.6592 ms, no later response over 45 ms. All 25 original submission files unchanged.
Keep mode OFF and v06 selected; no promotion/upload. Full protocol/results/artifact hashes:
[network-after-base-v1.md](network-after-base-v1.md).

## 2026-09-18 - route-dependencies-v1 (not promoted)

Default-off `current?USE_ROLLOUT_ROUTE_DEPENDENCIES=1`, selected control v07.
Retain canonical completed-route region dependencies alongside unchanged compressed paint
recipes, then abandon a simulated project if an omitted bridge region inks. This repairs the
captured continuation mismatch but conservatively rejects surviving alternate detours.

Fixed direct screen, 150 fresh maps / 300 paired games, seeds 190000..190149:
**133-160-7, 45.50%**; map-grouped diagnostic CI **34.41-56.59%**, zero bot errors.
**STOP_SCREEN_300 / NOT_PASSED**. No direct 1000, common-opponent stage, retry or promotion.
Candidate/control maximum in-process search 151/116 ms, slow counts 1/1; all outliers retained.
Elapsed 538.091 seconds. No exact-artifact process claim after strength failure.

Mechanism: 561 repeated candidate-family observations in 127/1581 boss states; no claim they
were selected mistakes. In 146 fixed ample-time public states, dependency correction changes
30 actions; disabled current agrees with frozen v07 in all 146. 286 Java + seven gate tests
pass; 292 arena and 50 boss replays exact. Both new flags remain OFF and selected Player stays
unchanged. Protocol: [route-dependencies-v1.md](route-dependencies-v1.md); complete outcomes,
parameters and source/class identities: [evidence](evidence/route-dependencies-v1.json).


## 2026-09-18 - network-chords-v1 (not promoted)

Default-off `current?USE_ROLLOUT_NETWORK_CHORDS=1`, selected control v07; dependency flag OFF.
After complete ordinary and singleton network searches, propose affordable connected pairs
and triples touching existing rails at both ends. Rank a bounded 24-candidate prefix, retain
two positive whole-network-income roots, and use the unchanged simultaneous rollout.
Core subset tests reproduce a boss triple whose every proper subset earns zero but whose
complete group shifts net income by +128 per turn. This is a local mechanism, not a win claim.

Fixed screen: **163-137-0 / 300 = 54.3333%**, requiring fresh direct confirmation.
Separate fixed confirmation: **544-456-0 / 1000 = 54.40%**, below the required 55%.
**NOT_PASSED / SKIPPED_DIRECT_FAILURE**. No common-opponent stage, pooled decision, retry,
parameter adjustment or exact-artifact process stage. Zero bot errors in both complete stages.
Diagnostic map-grouped CI in confirmation: 48.33-60.47%; mean score difference +125.051.
Search maxima A/B: screen 122/72 ms (slow 2/3), confirmation 80/83 ms (slow 5/5).
All outliers retained; elapsed 534.304 + 1764.208 seconds.

Mechanism probe: 15/146 ample-time decisions change, all replacing existing construction;
the exact three-cell boss example still selects the old singleton under the bounded search.
Both independent experiments total **1600 games / 800 distinct fresh paired maps**. Final
audit verifies complete stages, source/class/parameter/pool identities and all 26 original
submission files. 286 Java + seven gate tests pass; 292 arena and 50 boss replays exact.
Both new flags remain OFF, v07 selected. No promotion, push, upload or new process-readiness
claim. [Protocol](network-chords-v1.md), [evidence](evidence/network-chords-v1.json),
[final audit](evidence/v07-followups-final-audit.json).


## Live boss screen: chords-boss-v1 (2026-09-18 UTC; recorded 2026-09-19 local)

Separate exploratory comparison of the existing default-off network-chords candidate
against tagged v07 on Boss 4 (6740207). No new policy/version or promotion.
Fixed prospective design: 40 fresh matched map pairs, 80 games, balanced seats and
execution order. **INTERRUPTED: 20/80 games, ten complete pairs**; candidate **3W/7L**,
control **2W/8L**. These are descriptive partial counts, with no p-value or verdict.

Slot 21 returned HTTP 422; creation outcome/game ID is unknown. It was not retried or
replaced. Authorization checks and a separate diagnostic smoke succeeded afterward;
the original error remains unexplained. Six technical games are excluded from the
screen. Exact isolated artifacts each passed four strict process games. This tooling
verification does not alter network-chords-v1's failed local strength gate.

[Frozen protocol](chords-boss-v1.md), [independent interruption evidence](evidence/chords-boss-v1.interrupted.json),
[API runner and recovery](boss-api.md). Both flags remain OFF; selected v07 and all
26 original submission files are unchanged. No arena submission or push.


## Completed amended boss comparison (2026-09-19)

**AMENDED_EXPLORATORY_COMPLETE**: candidate 9W/31L/0D / 40 = 22.5% outright wins; v07 10W/30L/0D / 40 = 25.0%.
Forty matched map/seat pairs, 80 analyzed games and 82 play attempts. The original
unknown HTTP 422 at slot 21 is excluded under the user's explicit amendment; its
one separately recorded new attempt uses the original source/seed/seat/boss.
Slot 51 was subsequently refused with explicit API rate-limit code 407. A second
amendment records the cooldown, longer inter-game pause and one new attempt there.
Discordant pairs: candidate-only wins 5, control-only wins 6; exploratory
one-sided exact paired p = 0.725585938. Interpretation: inconclusive for improvement.

The protocol change followed the first twenty observed results; the original
creation outcome remains unknown. This is exploratory evidence, not an unamended
confirmation or strategy promotion. Original local gate remains NOT_PASSED.
All original submission artifacts, frozen policy source and default-off flags remain
unchanged. [Amendment 1](chords-boss-v1-amendment-1.md),
[rate-limit amendment](chords-boss-v1-amendment-2.md),
[independent final evidence](evidence/chords-boss-v1.amended.json).


## Adopt cached-boss practical selection v3 (2026-09-19)

The user adopted **practical-300-1000-boss-cache-v3** prospectively. Direct 300/conditional
1000 thresholds remain unchanged; 40 new candidate boss games against individually pinned
cached control results replace the mandatory older/style-opponent stages. Draws count half,
and equality with the cache passes. Sequential boss calls may overlap the required local
1000, but a direct failure cannot be rescued. The initial accepted v07 cache is 10W/30L,
40 unique maps, 20 seats each; its ten points are not an asserted true 25% win probability.
No strategy version/default change, new experiment or reinterpretation of historical failures.
See [the complete rule](boss-cache-policy-v3.md).


## 2026-09-19 - strategy-search-rhea-v1 (not promoted)

Independent default-off `current?USE_ROLLOUT_RHEA=1`, control frozen v07. Test whether
optimizing later construction priorities improves the first move: two elites, twelve
mutation attempts, unchanged eight-turn simultaneous rollout, opponent scenarios,
sabotage and evaluator. Evolution starts after ordinary and singleton network search;
only fully compared continuations can replace the saved move. No new routes or tuning.

Fixed practical-v3 screen: **134-165-1 / 300 = 44.8333%**, 150 fresh paired maps,
seeds 500000..500149, CG 486/36-ms search and two workers. Map-grouped descriptive
Hoeffding 95% interval **33.74-55.92%**; mean score difference **-154.43**, zero bot
errors. **STOP_SCREEN_300 / NOT_PASSED**: no direct 1000 or boss stage, pooling or retry.
Keep the flag OFF and v07 selected; no frozen version, strategy promotion or upload.

Readiness: **323 Java + 114 Python tests**, **292/292 arena + 139/139 boss replays exact**,
**158/158** disabled-current/frozen-v07 ample-action parity. RHEA changed 17 ample-time
actions and completed 410 extra nominal comparisons in the mechanism sample. The
separate exact-artifact strict process sample passed **0/4 problem games**; maximum
later response **45.4681 ms** (two above the separate 45-ms headroom target), first
maximum **753.534999 ms**. This bounded sample does not guarantee full-benchmark timing.
The in-process bench reports maxima **199/44 ms**, slow counters **14/0**; these include
first moves with a 486-ms allowance and do not identify the slow turn numbers. At least
one later call exceeds 50 ms; all observations remain retained. See the DEVLOG entry.

[Protocol](strategy-search-v1.md), [readiness and exact artifact identities](evidence/strategy-search-v1-readiness.json),
[complete final evidence](evidence/strategy-search-rhea-v1.json).


## 2026-09-19 - strategy-search-opening-v1 (verified, not promoted)

Independent default-off `current?USE_PROTECTED_OPENING=1`, control frozen v07. For the
first three turns, claim affordable protected cells next to towns using potential
traffic from ordinary canonical route queries, a one-cell-per-town quota and a 0.25
predicted-collision discount. Then use the v07 policy. Sabotage uses the existing
chooser on the chosen construction. No RHEA combination or outcome-guided tuning.

Fixed practical-v3 local screen: **186-114-0 / 300 = 62.00%**, 150 fresh paired maps,
seeds 510000..510149, CG 486/36-ms search, two workers, zero errors. Diagnostic
map-grouped Hoeffding 95% interval **50.91-73.09%**; mean score difference **+410.687**.
**PASS_300**: no direct 1000 required. Exactly **40 new sequential boss games** on the
pinned control's maps/seeds/seats: candidate **13W/27L/0D**, cached v07 **10W/30L/0D**;
13 >= 10 points, no issues, retries or replacement games. Final **PASS**. This is the
practical filter on a reused regression pool, not fresh statistical confirmation.

Mechanism: all **36** eligible diagnostic opening states select the new policy;
all **122** post-window ample actions match current v07. **323 Java + 114 Python**
tests pass; 292 arena replays exact. Final expanded boss corpus: **179/179 exact and
179/179 rendered**. Exact-artifact process sample: **0/4 problem games**, 367 responses,
first maximum **701.8207 ms**, later maximum **38.693099 ms**, zero grace. Local bench
maxima **75/156 ms**, slow counters **2/2**, include first moves; the process sample
does not guarantee all in-process or CG-host timings.

**Both independent experiments are complete:** RHEA fails at 44.8333%; protected
opening passes at 62% locally and 13 versus 10 cached boss points. Strength evaluation
used **640 new games: 600 local + 40 live boss**, with readiness games counted separately.
Retain opening as a verified default-off candidate. Selected current remains **v07**;
no promotion, new frozen version, arena submission or push.

[Protocol](strategy-search-v1.md), [readiness](evidence/strategy-search-v1-readiness.json),
[opening final evidence](evidence/strategy-search-opening-v1.json),
[independent final audit](evidence/strategy-search-v1-final-audit.json),
[exact artifacts and supporting records](evidence/strategy-search-v1-artifacts.zip).


## 2026-09-19 - v08 Protected Opening (selected locally; timing readiness OPEN)

The user authorized selecting the exact protected-opening candidate. `current`, `main`
and frozen `v08` resolve all **107** measured parameters unchanged. The opening default
is now ON; RHEA remains OFF. `RolloutBotV08` and `ProtectedOpeningV08` differ from the
measured classes only in identity. The first-three-turn opening and its ordinary-policy
fallback are unchanged. v00-v07 sources and parameter snapshots remain frozen.

Selection uses the completed **186W/114L/0D / 300 = 62%** local screen and **13/40**
boss wins versus **10/40** individually cached v07 wins. No new strength evaluation,
retuning or reinterpretation of that practical reused-pool result was performed.
Promotion checks pass **338 Java tests**, **292/292 arena + 179/179 boss** exact replays,
all-parameter/source verification, and **280 ample-time decisions on 70 states** across
measured/current/main/v08, including both seats, the opening, fallback and later policy.

The newly exported default artifact is **96,207 characters**, source SHA-256
`079ffa5859b2c7bed2fe6026fa50ab2080604f4a21fd2043f05509f1f385718d`, build ID
`74d41b1ff096e2972e9e740096f5ff1224ed7e293cd855c3ab650e1e5046b8c3`.
Its complete fixed process panel is **NOT_PASSED: 1/8 problem games**. On map
903510176 as P1, zero-based turn 29, response **67.5805 ms** exceeds the **50-ms,
zero-grace** deadline (send **11.0924 ms**, consumer **71.537699 ms**). The other seven
games pass. All eight were completed on unchanged source/classes; no retries or
replacement maps. The earlier candidate's four-game pass cannot clear this failure.
Frozen v08 export has compilation/configuration/source checks only; these eight games
check the actual default export. **Selected locally does not mean ready to upload.**

Keep the user's v08 selection; leave timing readiness **OPEN**. The previous submission's
26 files are retained with byte hashes. No grace change, tuning, arena upload or push.
[Promotion protocol](v08-promotion.md), [full process evidence](evidence/v08-promotion.json),
[independent verification](evidence/v08-promotion-verification.json),
[artifacts and supporting records](evidence/v08-promotion-artifacts.zip).


## 2026-09-19 - live-target-v1 / always-disrupt-v1 (not promoted); boss-winners study

Motivating study: Boss 4 rating 29.69 vs our 24.91; now-Gold players beat it 55.7% (368 games),
v08 13/40. Against us the boss keeps ~1 more active connection than against Gold players and
completes 70% of its projects (58% vs Gold winners); Gold winners hit its live project (region
with a boss placement in the last 2 turns) on 31.7% of hits, we on 17.2% in losses. Other
sabotage aggregates are the same. See [boss-winners-study-v1.md](boss-winners-study-v1.md).

Two default-off flags on v08, control frozen v08, practical-v3, fresh paired maps, zero errors:

- `current?USE_ROLLOUT_LIVE_TARGET=1`: screen **153-137-10 / 300 = 52.67%**, then fresh direct
  **492-463-45 / 1000 = 51.45%**, **NOT_PASSED** at 55%. No boss stage.
- `current?ROLLOUT_ALWAYS_DISRUPT=1`: screen **147-152-1 / 300 = 49.17%**, **STOP_SCREEN_300**.

Both flags OFF, v08 selected; 340 Java tests pass; merged current 97,896 characters. Self-play
v08 mirrors accept disruption on 64-67% of turns and last ~90 turns, unlike the 53-57-turn boss
games, so the local bench does not exercise the targeted situation. [Protocol and limits](live-target-v1.md),
[evidence](evidence/live-target-v1.json).
Boss stage for live-target (user-authorized after the local failure, diagnostic only): exact
isolated artifact, strict process 0/8, **8/40 vs cached v08 13/40**, NOT_PASSED; 40/40 replays
exact. Boss rating observed 30.99-31.19 during the run. [Details](live-target-v1.md).


## 2026-09-20 - Indrill study; track-value-v1 (not promoted)

Replay study of the Legend #1 (Indrill, Python3, 36.34, 265-17 over 282 games, all reproduced
exactly): a **sub-millisecond heuristic** (0.2 ms/turn median) that disrupts on 100% of turns,
builds with 2.91/3 paint on 97% of turns, lands 93.6% of hits in regions that get inked, and
puts 48% of hits on opponent *construction* so projects are inked before they earn. Same track
count as its opponent at turn 20 but 4.4 vs 2.4 own-majority connections. v08 agrees with 11.8%
of its cells and 26.5% of its targets and disrupts on 78% of its states. Fitted target valuation:
an unfinished opponent track ~0.86 opponent-income points, an own track -1.25, +4 per instability
level; v08's track term is ~17x smaller. Full report: [indrill-study-v1.md](indrill-study-v1.md).

Default-off `USE_ROLLOUT_TRACK_VALUE` (opponent track 5, own track 6, replacing the 0.4 balance
weight) and `ROLLOUT_TRACK_VALUE_SELF_ONLY`; control frozen v08, practical-v3, fresh paired maps,
zero errors, 1600 games:

- both choosers: **144-147-9 / 300 = 49.50%**, STOP_SCREEN_300.
- self only: **158-133-9 / 300 = 54.17%**, then fresh direct **494-477-29 / 1000 = 50.85%**,
  NOT_PASSED at 55%. No boss stage.

Accepted disruption stayed at 64-66% for both sides: in v08 mirrors the opponent's tracks sit in
town regions or in regions we share, and the bench games last 92 turns with placement on 56% of
turns, against Indrill's 47-turn, 97%-build, 100%-disrupt arena games. Both flags OFF, v08
selected; merged candidate 98,886 characters. [Evidence](evidence/track-value-v1.json).

## 2026-09-20 - leader-coverage-v1: sabotage flags vs leader-v1 (no effect, not a gate)

Matched panel against the local `leader-v1` reference (99.9% accepted disruption), same fresh
150 paired maps for all arms, seeds 540000..540149, CG timing, 300 games each, zero errors:
v08 **247-53 (82.33%)**; track-value self **250-50 (83.33%)**, mean per-map delta +0.010 (SE
0.026); live-target **247-53 (82.33%)**, 0.000 (0.030); always-disrupt **249-51 (83.00%)**,
+0.007 (0.029), our accepted disruption 56% -> 78%. No flag changes the result or our
construction (1.31-1.32 cells/turn everywhere). leader-v1 itself places on only 41.5% of turns
(1.18/3 paint), so games still last 82-84 turns; it covers the leader's sabotage meta, not its
construction meta. All flags stay OFF, v08 selected. Runner `tools/run_leader_coverage.py`;
[evidence](evidence/leader-coverage-v1.json).

## 2026-09-20 - Per-cell construction study; leader-v2; fill-v1 local PASS_1000 (artifact too large)

[indrill-construction-v1.md](indrill-construction-v1.md). Following every placed track through its
life: 86.8% of Indrill's cells through turn 45 are within one paint of an optimal completion
route or on a take-over / bypass path; no hidden routing model. His edge is that 60% of his
cells earn (opponents 47%), 11.7 vs 8.1 points per paint, with full paint on 97% of turns. In
the 40 cached Silver-boss games v08 and the boss earn the same 27 points per paint, but the boss
spends **1.80 paint/turn vs our 1.55** (turns 21-45: 2.94 / 2.19 vs 2.44 / 1.75); RolloutBot has
no leftover-paint rule. `leader-v2` (local reference, never-idle construction) places on 90% of
Indrill's states but loses 20% to v08. Candidate `current?USE_ROLLOUT_FILL=1` (leftover paint on
stable/protected cells next to own rails): practical-v4 **176-124 / 109-91 / 284-216 = 569/1000
(56.90%) PASS_1000**, zero errors, placement 90% vs 54%. The first artifact was 100,646 /
100,000 characters; RHEA (rejected) was removed from `current` on the user's decision ->
**95,279** characters, strict process check **0/8** problem games. Cached-boss stage on the pinned
v08 maps: **17 W / 23 L vs 13 / 27**, PASS; 40/40 replays exact. Combined v4 verdict **PASS,
promotion true**; promotion to v09 awaits the user. [Evidence](evidence/fill-v1.json),
[decision](evidence/fill-v1-decision.json), [boss](evidence/fill-v1-boss.json).
