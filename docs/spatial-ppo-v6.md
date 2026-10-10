# spatial-ppo-v6 — bounded two-turn construction search

Status: completed and rejected for promotion, 2026-10-10.
The original prospective protocol is retained below and in the evidence archive.
User authorized the next stage after completed v5. This is one new search
experiment on the frozen v4 policy, not new training or a full top-3 reproduction.
The historical spatial-ppo name is retained; no PPO or value fitting runs here.
Selected current/v09, all older sources/weights and their artifacts stay unchanged.

## Hypothesis and fixed scope

V5's learned critic was poor on the v09 validation subset, completed only 14/34
nominal saved-position comparisons, and lost 0W/300L against selected v09.
Its v4 comparison had 98 zero-zero draws and was weak strength evidence.
The next bounded step adds construction branching and a second exact turn while
using actual simulated score, with no learned leaf value or extrapolated income.
The candidate tests this integrated tactical reranker; it does not separately
identify the contribution of construction branching, depth and evaluation changes
relative to v5. Its direct algorithmic baseline is greedy v4.

Keep the exact v4 payload SHA
`89de11f4239844c55c6e61c987ee2443fd5d79a1c71af782d2686e836ec6f3ef`.
No model, observation or core changes. SpatialV6Bot repeats the small greedy
readout to capture existing network outputs before overwrite; all original
conditional forwards, masks, paint spending, tie order and complete-plan fallback
are preserved with search disabled. The unused value output is never ranked.

Candidate0 is the complete greedy v4 plan. Candidate1 replaces only its LAST
actually placed cell with the highest-logit other legal cell of the SAME paint
cost in that placement's original conditional observation. Earlier cells and
the final greedy disruption are identical. STOP is excluded as an alternative.
If the last placement has no such candidate, or no placement was made, skip
search; do not replace an earlier slot or change spent paint. Strict logit ties
follow the original padded-index order. The candidate may not gain a new suffix.

Capture our stage-zero build/disrupt logits during ordinary greedy readout.
Predict the opponent from one stage-zero pass on the same original preturn board.
Cached-logit packing repeatedly takes the highest currently legal build logit
(or STOP), preserving core costs and paint, then the best legal disruption.
Cache both sides' root logits. Root opponent actions are fixed before either
candidate is simulated and are identical for both. They never observe our plan.

For each candidate, reset SearchState to the original board and resolve the
simultaneous root plans through the existing core. If terminal, stop that branch.
Otherwise produce both second-turn plans from the fixed ROOT logits under the
same child's CURRENT legality and costs, before resolving either plan. These
cached logits are explicitly an approximate continuation policy: no fresh
conditional CNN passes or hypothetical-policy oracle is claimed. Resolve the
second turn through core and stop. No new pathing/scoring implementation.

Rank the exact long-integer cumulative score-gap change after one terminal or
two live transitions: final simulated gap minus the original banked gap. Do not
add banked score twice, use a terminal bonus, income extrapolation or v5 critic.
Both trajectories must finish at their terminal/depth boundary within the common
deadline before any choice changes. Equal values retain candidate0. Any cutoff
or invalid/nonfinite network proposal returns the complete original greedy plan.
Record attempts, complete comparisons, changed plans, cutoffs, node/step counts
and both integer returns. No per-node streams/boxing/collections.

Fixed Params: USE_SPATIAL_V6_SEARCH=false by default, true for the separate v6
snapshot; SPATIAL_V6_MIN_SEARCH_MS=12; SPATIAL_V6_DEPTH=2 (supported bounds 1..2,
candidate remains 2); SPATIAL_RESERVE_MS=2. V5 search is inactive in v6. No sweeps.
The total turn deadline includes greedy readout, opponent preparation, candidate
simulation and result selection. Preserve CGRunner.MARGIN_MS and greedy warmup.
At most the ordinary greedy forwards plus one opponent forward and four exact
transitions are needed; this is a work bound, not a promised speed improvement.

## Verification and one fixed saved-state panel

Before measurement: synthetic tests for same-cost legal replacement, the last
actual placement followed by STOP, no-alternative fallback, both seats, same-root
opponent isolation, changed child legality, simultaneous continuations, one-step
terminal/two-step live score accounting, integer ties and injected cutoffs.
Verify search-off ample-time parity against v4 and compiled standalone parity.
Run scoped Java tests and cgValidate (292 archived games). The six unrelated
pre-existing untracked Pduhard tests remain explicitly excluded. Check exact
historical submission bytes, especially v1-v5/current/v09.

Freeze sources, compiled runtime, parameters, protocol and retained input
identities before the timed panel. No new training, smoke games or teacher games.
Re-use the first four archived v4 training maps, each teacher-P0 trajectory,
requested preturns 0/25/50/75/99, both evaluated perspectives. Replay all recorded
actions through core; check hashes, score accounting and terminal state. Keep
missing post-terminal slots without replacement. This is exposed development data.

Four serial phases: v4 ample, v6 search-off ample, v6 ample, v6 nominal36ms.
Ample is 60000ms; warmup100ms per phase/map/seat, as in the prior panel.
Check original-state immutability, legality, equal construction count/cost,
unchanged prefix/disruption, fallback and complete-pair acceptance. Search-off
must match v4; complete nominal comparisons must match ample. Report coverage,
actual differences and mean/p95/max timings without retiming or speed claims.

After all timing phases, independently reconstruct the ample candidate branches
with public Rules.resolve (the bot uses SearchState). Recompute both root policy
priors, re-mask the child's legal moves, and retain per-step plans/scores/hashes.
Verify both exact cumulative integer returns, depth/terminal stopping and shared
root opponent. Mark the panel complete only after this audit succeeds. No new
Python-Java numeric panel is needed for unchanged weights/network/observations.

Build the separate exact v6 artifact with submit, compile-check <=100000 characters,
then one strict zero-grace eight-game process check against v09 on the established
process maps. Retain failures; no retry to green. Timed panels/process/strength run
serially, without concurrent CPU-heavy work. Record preparation defects separately.

## Fixed strength comparison and decision

No additional mirrored spatial progress series: v5's 98 zero-zero draws showed
its limited usefulness. The sole strength comparison is practical-v4 versus
selected frozen v09, two workers, CG budgets, base seed14300000 and subsequent
blocks at +100000/+200000. Exclude all prior, development, teacher and process maps.
Complete fixed blocks 300/+200/+500. Stop strictly below50% at cumulative300/500;
survivors play all1000, with >=55% required once at1000. No early pass, tuning,
retries or extension of a failed block. Preserve policy v4's diagnostic-only CIs.

After a valid implementation, a process failure remains an independent blocker;
the fixed local series can still be retained as strength evidence without rescuing
readiness. A local failure skips boss games. Local success still requires the
matched40 live-boss comparison and its execution authorization before promotion.
No upload, push or strategy promotion is implied by this request.

Record results in BOTS/DEVLOG/PLAN, retain exact sources/artifact, diagnostics,
raw games and prior-corpus dependency, then make the authorized local commit,
including a rejected experiment. This implements only two candidates and two
turns: broader/conditional branching, longer search, useful long-horizon value
and competitive training remain open work.

## Completed results — 2026-10-10

Implementation and checks: 569 scoped Java tests pass, including nine new
synthetic v6 cases and four new packaging/registration checks. The six unrelated
pre-existing untracked Pduhard tests remain excluded; this is not an unqualified
full-suite claim. No core/model/training changes. All 292 archived CG replays
reproduce exactly; prior v1-v5/current/v09/neural-v2 artifacts remain byte-identical.
The full v4 payload is unchanged. No Python training or new numerical-parity run
was needed for the unchanged network and observation.

Saved panel: first four original training maps, teacher P0 trajectories;
17/20 requested preturn positions, both seats,
34 calls per phase. All recorded transitions/scores/hashes match core;
post-terminal positions are retained as missing without replacement. Search-off
matches v4 throughout. Ample comparisons complete 30/34,
change 5 plans, and perform 116 core transitions.
Nominal36ms: 30 attempts, 30 complete comparisons,
5 changed plans, 0 search cutoffs,
0 interrupted greedy plans, 0 calls over
the supplied budget. All completed nominal comparisons agree with ample;
0 nominal actions differ from ample overall.
Nominal mean/p95/max: 15.136/20.947/23.740ms.
Ample mean/max: 12.411/22.181ms.
These serial warmed phases are coverage/mechanism diagnostics, not a speed
comparison or a guarantee of CG-host timing.

After all timings, independent public Rules.resolve reconstruction verifies
60 candidate trajectories and 116
transitions across 30 complete ample comparisons. Root-prior hashes,
same-conditional last-cell alternatives, identical root opponent, current child
legality, simultaneous second-turn plans, exact integer returns and terminal/depth
boundaries all agree. Original input state, build count/cost, prefix/disruption,
tie behavior and full-plan cutoffs are checked. This verifies the implemented
opponent approximation; it does not establish that the predicted replies are good.
Of the 30 complete ample comparisons,
20 have equal returns; in
8 both returns are zero. Short-horizon score
often cannot distinguish these two candidates on this exposed panel. This is a
diagnostic limitation, not an isolated causal explanation of match strength.

Exact Player: 92,020 characters; source SHA
`4019ea1ab9c0e95081d5a56bcd27eff3a6b2ba9c87dbec2134b66ca94dd6d9e6`;
build `c08f050fbe5f319427b0fd6c608d0505de7585bbdba56c1da2b8c484bfbad0a4`.
One strict zero-grace process panel: 8/8 clean; maximum first response
650.624ms, later 32.239ms. No timing retry.

Practical-v4 vs selected v09: **1W/299L/0D/300**, score 0.33%,
mean score difference -9330.59; **STOP_FUTILITY_300**.
Errors candidate/control [0, 0], slow native calls [2, 3],
maximum native call times [129, 60]ms. All 150 fresh paired maps
exclude 21,447 previously used/development maps;
two CG-budget workers throughout, base seed14300000. No mirrored spatial progress
series, extension, tuning or retry. The local failure skips the boss stage.
All 732 frozen files stay byte-identical through evaluation.

Decision: preserve the completed tactical-search experiment; reject promotion.
Selected current/v09 stays unchanged, with no upload or push. Construction
branching and two exact turns are implemented in this bounded form. Broad
conditional branching, longer-horizon search, reliable future value and competitive
training remain open. The comparison does not separately identify the effects of
branching, depth, score-only ranking and opponent approximation.

Evidence: [summary](evidence/spatial-ppo-v6.json),
[archive index](evidence/spatial-ppo-v6-artifacts.json),
[retained archive](evidence/spatial-ppo-v6-artifacts.zip).
The archive retains the original prospective protocol, source/runtime freezes,
tests, complete saved-state panel/audit, exact submission and raw strength reports.
Its hashed corpus dependency is the unchanged v4 archive; no new training corpus.
