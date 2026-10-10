# spatial-ppo-v5 — calibrated value head and bounded disruption search

Status: completed, 2026-10-10; strength rejected, no promotion.
The prospective protocol is preserved below; actual results follow at the end.
The user authorized the next stage after v4. This is the first bounded value-guided
search experiment, not the complete Pduhard search or deeper construction search.
The historical spatial-ppo name remains; this stage uses supervised regression,
not PPO. Selected v09, old spatial weights/runtime and default submission stay intact.

## Hypothesis and scope

V4's policy beat v2 98/100 but lost 0/300 to v09. Its inherited value head became
uncalibrated when distillation changed shared features. Refit only that head and
test whether one-turn exact simulation can improve the choice of disruption.
The fitted evaluator plus bounded search are one integrated candidate; results
do not isolate regression fitting from search or reproduce the original top-3 bot.

Keep the exact first 3,424 v4 policy/shared float32 weights, payload SHA
`89de11f4239844c55c6e61c987ee2443fd5d79a1c71af782d2686e836ec6f3ef`.
Replace only the final 15 value coefficients, using separate SpatialV5Weights.
The unchanged SpatialBot/SpatialNet/SpatialObservation supply greedy construction
and disruption, with existing deadlines and SPATIAL_RESERVE_MS=2. No core change.

## Fixed supervised fit

Reuse all stage-zero rows of the exact archived v4 teacher corpus: 4,548 training
turns on 24 maps and 1,112 validation turns on six different maps. Their outcomes
are already exposed development evidence, not fresh confirmation. Retain the
original map split, metadata, hashes and corpus/archive lineage; do not select rows.
One observed teacher turn has one training weight. Do not add opposite-seat rows.

Extract z[14], the input of the existing value linear layer, from frozen v4 with
a PyTorch forward pre-hook, batch64, CPU two threads. No changes to model math.
The target is `(final teacher score gap - current preturn teacher score gap)/10000`,
gamma=1, no terminal bonus. Thus it estimates remaining score difference under
the recorded v09 continuation against its fixed opponent pool. This is not a
calibrated prediction under the student/search policy or a counterfactual oracle.

One deterministic float64 ridge solve, with no sweeps or optimizer epochs:
train-only feature mean and population standard deviation; drop columns whose
standard deviation is <=1e-12; center targets; leave intercept unpenalized.
Objective: mean squared residual + 0.01 * sum of squared standardized coefficients.
Convert coefficients back to raw-z coordinates and round to float32 for export.
Save initial/final checkpoints, exact extracted features/targets/row lineage,
normalization, solver residual, rank/singular values/condition diagnostics and
float64-to-float32 prediction change. No Adam/RNG recovery claims for a closed solve.

Report train/validation RMSE, MAE, bias, R-squared and Pearson (undefined=null),
overall, by recorded opponent and by fixed 25-turn bins. Compare with zero future
return, train-only mean return, and current-income extrapolation. The latter uses
the previous turn's exact core gained (zero at turn0) and public maxTurns-turn,
not the actual future terminal time. No arbitrary fitting threshold or retuning.
All metrics use the exported float32 head. Verify unchanged policy bytes and
initial/final Python-Java forward parity on the first 24 training stage-zero rows,
tolerance1e-4 and identical masked greedy policy choices.

## Exact bounded runtime

New SpatialV5Bot delegates the original greedy plan to unchanged SpatialBot with
the v5 payload (policy coefficients are unchanged). Construction is never changed.
Candidate0 is its greedy disruption, candidate1 the legal runner-up logit from the
same final conditional observation; STOP is a legal candidate. Stable index ties
follow the original policy. No search if the greedy plan was interrupted or the
second candidate does not exist.

Predict one approximate opponent plan from the same unchanged preturn state:
one frozen-policy stage-zero network pass, repeatedly pack the highest legal
cached construction logit until STOP or paint is exhausted, plus its best legal
disruption. These cached logits do not represent the full conditional opponent
policy. This single prediction is fixed and shared across both candidates; neither
candidate reveals its action to the opponent. No opponent parameter tuning.

SearchState.set/resolve is the only simultaneous transition implementation.
Reuse primitive arrays/state/observation buffers; no per-node collections.
For each candidate, evaluate `one-step score-gap change/10000 + V(successor)`;
terminal successors have future V=0. Do not add current banked score twice or a
terminal bonus. Both leaves must be finite and completely evaluated before any
choice changes. Exact ties retain candidate0. Any cutoff returns the original
greedy plan. Never compare one complete value to an unfinished value.

Params: USE_SPATIAL_V5_SEARCH=false by default, true in the v5 snapshot;
SPATIAL_V5_MIN_SEARCH_MS=12, SPATIAL_RESERVE_MS=2. These fixed values are not tuned.
Outer deadline includes greedy, opponent prediction, simulation and leaf inference.
Record attempts/completions/cutoffs/changes and both leaf values. Search off must
retain the v4 greedy behavior. No multi-turn or construction branching in this v5.

## Verification and retained evidence

Before fit: synthetic target/accounting, ridge/constant-feature/malformed-data
tests; Java legality, same-preturn/opponent-isolation, terminal values, input
immutability, complete-pair fallback and original-plan tie tests. Preserve previous
artifact byte identities, including current/v09 and spatial v1-v4. Run scoped Java
and Python tests, cgValidate292; the six unrelated untracked Pduhard tests remain
excluded explicitly. Freeze source/protocol/parameters and old data before fit.
No real-game smoke or new teacher games are needed: existing complete corpus and
synthetic fixtures cover preparation. Record technical failures without erasing them.

After final fit, run one fixed saved-trajectory panel: first four v4 training maps,
teacher seatP0 game, requested preturns0/25/50/75/99, both evaluated perspectives.
Replay recorded simultaneous actions through core and verify state identities;
preserve genuinely missing post-terminal slots, no replacement. Run ample and
nominal phases serially. Verify legal preserved construction, baseline fallback,
complete-leaf accounting, parameter/payload identities, numerical parity, input
immutability and timings. The diagnostic maps are reused development evidence.
Report nominal/ample choice differences and search completion coverage honestly.

Build separate exact submit artifact, compile-check size <=100000 characters,
then one strict zero-grace eight-game process check against v09. No concurrent
CPU-heavy work during timing/strength runs. Retain all timing failures; no retries
to green. Freeze final sources/weights/artifact before strength evaluation.

## Fixed comparisons and decision

1. Progress: v5 versus exact v4, 100 games on 50 fresh paired maps, seeds
   13200000..13200049, two workers, CG budgets. This diagnoses improvement over
   the spatial baseline, not promotion. Record W/D/L, errors, timings and uncertainty.
2. Practical-v4: v5 versus selected frozen v09, base seed13300000, two workers,
   300/+200/+500 games. Stop strictly below50% at300/500; no early pass; require
   >=55% at1000 for a local pass. Exclude all prior/development/progress maps.
   Run independently of the progress outcome when execution is valid, without tuning.

A local failure skips boss games. A local pass still requires the matched40 live
boss check and authorization before promotion. No promotion/upload/push is implied.
Record all outcomes, update BOTS/DEVLOG/PLAN, archive compact reproducible evidence
and locally commit this completed experiment, including a rejection. Deeper and
construction-branching search remain separate work, not silently marked complete.


## Completed result

Completed one fixed ridge solve in 9.394s (feature extraction
3.969s, linear solve 0.009749s). All 3,424
policy/shared floats remain exact; 15 value coefficients were fitted. The 14-column
design has 12 active columns; constant columns 9/10 are dropped, regularized
condition 486.65, solver residual 5.52e-16. No new training games, sweeps or retries.
Initial payload: `89de11f4239844c55c6e61c987ee2443fd5d79a1c71af782d2686e836ec6f3ef`.
Final payload: `efb3be39fcb3fedfbbe88c2cf433e0027e86f59548c422dd047c246db9d37f81`.

Original teacher split: 4,548 training turns on 24 maps; 1,112 validation turns on 6
different maps. Validation is reused development data. Fitted validation RMSE
8920.42 score points versus income-extrapolation
10783.03, train-mean
12427.49, zero
15875.63. Fitted MAE
5919.27 is worse than income-extrapolation
5464.57. Overall R-squared
0.4807, Pearson
0.7066; these aggregate metrics do not establish search utility.

The v09-opponent validation subset (312 turns) is particularly poor: fitted RMSE
8151.21, versus zero
967.36 and income
1453.00; Pearson
0.0305. This critic estimates recorded v09
continuations against a mixed pool, not the student continuation or causal
differences between hypothetical search leaves. The fit is not an established
strong-opponent evaluator.

Independent audit: 100 checks pass, with a reporting caveat. Floating-point
centering gave 10 tiny non-null Pearson entries for a constant train-mean baseline.
They are mathematically undefined, interpreted as null in the retained audit.
Original reports are preserved; no refit or outcome-guided correction was made.
This does not affect the fitted weights, RMSE/MAE, search or game results.

Verification: 556 scoped Java tests and 38 Python tests pass. The six pre-existing
untracked Pduhard tests remain excluded; this is not an unqualified full-suite claim.
The initial run had three stale field-count assertions after adding two inactive
v5 parameters; only their counts/descriptions were repaired before source freeze.
Original failed logs/XML are retained. Post-fit 11 targeted Java tests pass;
292/292 archived CG games reproduce exactly. Prior submissions stay byte-identical.
Initial/final 24-row Python-Java parity passes, max final error 7.93e-6.

Saved-trajectory panel: first 4 original training maps, teacher P0 trajectories,
17/20 requested preturn positions survive before terminal, both perspectives.
All recorded transitions/hashes/scores match core; no new games or replacement slots.
Search-off matches v4 in all 34 calls. With ample time, 34/34 pairs complete and
16 decisions change. Under 36 ms, 14/34 complete, 23 start,
9 started searches cut off,6 decisions change; all greedy
plans complete. Construction, input state, legality and complete-pair fallbacks
are checked. Nominal and ample actions differ in 10/34
calls. Nominal mean/p95/max: 28.571/34.054/34.279 ms.
Ample mean/max: 17.613/39.164 ms. Serial warmed phases are
mechanism/coverage diagnostics, not a speed comparison or CG-host guarantee.
All 68 successor observations pass Python-Java parity (max error 1.60e-5), with
matching greedy choices; independent leaf accounting agrees with recorded search.

Exact Player: 90,906 characters, source SHA `f5793756bf20c48af7302418b70b8fe92499b3c3eaefd3291999f1b4160550c2`,
build `26c3306d1edc09fc4a17eb2c64d57728fc195a5e0fdfcad9dd66a6af48335040`. One strict zero-grace process panel: 8 games,
0 problem games; first max 589.085 ms,
later max 37.756 ms. No timing retry.

Progress against exact spatial-v4: **1W/1L/98D/100**, score 50.00%,
mean score difference +6.75, errors [0, 0],
slow calls (candidate/control) [3, 0]. Fresh 50 paired maps,
maximum native call times (candidate/control): [65, 34] ms.
Seeds 13200000..13200049, two CG-budget workers. Map-grouped 95% diagnostic interval:
[30.79%,69.21%]. Progress evidence only, not promotion.
All 98 draws are exactly 0:0, with matching placement/paint totals. This symmetric
matchup is weak evidence for general strength; totals do not prove identical
per-turn actions. The two non-draws are candidate P1 on seeds 13200000/13200001.
V5 issues 3,661 disruptions against v4's 1,131, so unchanged full play is not an
explanation for the aggregate tie. Neither non-draw contains errors or slow calls.

Practical-v4 against selected v09: **0W/300L/0D/300**, score 0.00%,
mean score difference -8575.49, errors [0, 0],
slow calls [2, 0], maximum native call times [69, 43] ms;
**STOP_FUTILITY_300**. The mandatory futility stop ends
evaluation; no additional local blocks or boss games. All
200 evaluation maps are distinct, exclude
21247 previously used/development maps, and preserve
the fixed two-worker CG timing profile. The strength exclusion index includes
the progress maps. All 873 final frozen files
remain byte-identical through evaluation.

Decision: preserve the completed evaluator/search experiment and reject promotion.
Selected v09 and the arena stay unchanged. A fitted critic and working one-turn
search are now implemented; reliable student/counterfactual value, deeper search
and construction branching remain open. These results do not isolate fit quality,
opponent prediction and the restricted action search as separate causal effects.

Evidence: [summary](evidence/spatial-ppo-v5.json),
[fit](evidence/spatial-ppo-v5-training.json),
[archive index](evidence/spatial-ppo-v5-artifacts.json),
[retained archive](evidence/spatial-ppo-v5-artifacts.zip).
The compact archive retains exact features/targets/lineage, checkpoints 0/1,
protocol/source/runtime freezes, audits/tests, saved-state panel, exact submit
and raw bench reports. Its hashed dependency is the unchanged v4 corpus archive;
the large raw memmap cache is not duplicated.
