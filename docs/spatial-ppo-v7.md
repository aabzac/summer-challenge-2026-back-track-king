# spatial-ppo-v7 — conditional root construction branch

Status: completed and rejected for promotion, 2026-10-10.
The original prospective protocol is retained below and in the archive.
User authorized the next stage after completed v6. One bounded runtime experiment
on frozen v4 weights; no PPO, weight fit, core change or automatic promotion.
Selected current/v09 and all historical versions/artifacts remain unchanged.

## Hypothesis and fixed implementation

V6 achieved 1W/299L and changed five of 30 complete saved-state comparisons.
Twenty comparisons tied. Its alternative changed only the final cell at equal
cost. V7 expands the possible construction bundle by branching at the first
decision and genuinely recomputing the conditional suffix. The same short-horizon
ranking remains a limitation; wider proposals alone are not a full top-3 replica.

Keep exact v4 payload SHA
`89de11f4239844c55c6e61c987ee2443fd5d79a1c71af782d2686e836ec6f3ef`.
Candidate0 is the complete greedy v4 plan. Preserve its decisions, conditional
forwards, tie order and interruption behavior with search disabled. Capture both
stage-zero own logits without an additional forward. The unused value is ignored.

Candidate1 forces the highest-logit OTHER legal non-STOP BUILD from the original
stage-zero mask. Exclude the original first BUILD if one was selected; if greedy
selected STOP, choose the best legal BUILD. Different terrain cost is allowed.
After this first cell, create each remaining build decision with the actual new
prefix and remaining paint, using fresh ordinary stage1/2 network forwards.
STOP and exhausted paint behave exactly as in greedy readout. No new stage3
forward: both candidates retain the original complete greedy DISRUPT. Root
disruption legality does not depend on the unexecuted construction plan.

If no alternative root BUILD exists, skip. If a completed alternative has the
same unordered build set as candidate0, skip it without trying a third proposal.
No same-cost/count restriction; both complete plans must obey core legality and
the paint budget. Original inputs remain untouched. This is width2 with one
root deviation and conditional completion, not a full beam or search over all
root actions. No cached off-prefix logits are used for alternative construction.

As in v6, one opponent stage-zero pass on the unchanged original board defines
the common root opponent plan. It sees neither candidate. Root-prior packing
takes the best currently legal BUILD or STOP, then best legal disruption.
For each candidate reset SearchState(root), resolve both root plans, stop if
terminal; otherwise form BOTH next-turn plans from their own fixed ROOT priors
under the SAME current child's legality/costs, then resolve simultaneously.
The second-turn policy is deliberately approximate (no fresh child inference).

Rank exact long final-score-gap minus root-score-gap, after two transitions or
earlier terminal. No learned value, discount, income extrapolation or bonus.
Both candidate trajectories must complete within one deadline before any change.
Equal returns keep candidate0. A cutoff in alternative generation, opponent
prediction or either simulation returns the whole original greedy plan. Invalid
or nonfinite proposals cannot replace greedy. Capture attempts, alternative
completion/duplicates, completed comparisons, chosen branch, cutoffs and stages,
root priors/opponent, trajectory lengths/terminal flags and integer returns.

Params: USE_SPATIAL_V7_SEARCH=false by default and true only in the separate v7
snapshot; SPATIAL_V7_MIN_SEARCH_MS=12; SPATIAL_V7_DEPTH=2 (supported1..2, fixed2);
SPATIAL_RESERVE_MS=2. V5/V6 search flags are inactive. No tuning/sweeps.
Start search only after greedy completes and at least12ms remain. One deadline
includes all work. Preserve CGRunner.MARGIN_MS and ordinary neural warmup.
Upper work bound: greedy<=4 + alternative<=2 + opponent1 network passes, and
four core transitions. Primitive reusable buffers in search; no per-node
allocations/collections. The work bound is not a timing guarantee.

## Checks and one fixed saved-state panel

Before measurement: synthetic tests for fresh conditional suffix, actual root
choice and masks, different costs/counts, original STOP then forced build,
suffix STOP/exhausted paint, duplicate-set skip, both seats, same-root opponent,
simultaneous current-child continuation, terminal/depth score accounting, ties,
cutoffs in all phases, input immutability and ample search-off parity with v4.
Test exact compiled artifact actions and preserve v1-v6/current/v09/neural-v2
submission bytes. Run scoped Java tests and cgValidate (292 archived games).
The six unrelated pre-existing untracked Pduhard tests remain explicitly excluded.

Freeze source, parameters, compiled runtime, protocol and input identities before
the saved panel. Reuse first four v4 training maps, teacher-P0 trajectories,
requested preturns0/25/50/75/99, both perspectives. Replay entire recorded games,
check metadata/hash/score/terminal identity; retain missing post-terminal slots
without replacement. This is exposed development data, not strength evidence.

Four SERIAL phases: v4 ample, v7 search-off ample, v7 ample, v7 nominal36ms.
Ample60000ms; warmup100ms per phase/map/seat. Verify search-off parity, legal
plans, original greedy disruption, input immutability, complete-pair choice and
whole-plan cutoffs. Complete nominal decisions must agree with ample. Separately
report alternative creation, duplicate skips, cutoff phase, comparisons, changed
actions and mean/p95/max timing. No retiming or claim of a host speedup.

AFTER all timing, independently reconstruct the original greedy plan and the
alternative from fresh conditional network passes and original masks. Rebuild
root priors/opponent and both scored trajectories with public Rules.resolve,
not the bot's SearchState. Retain own/opponent actions, per-step score/hash and
terminal state. Compare exact returns/steps/terminal and final selection for
complete ample comparisons; nominal calls are checked by completed-action
agreement, not a second independent timing/trajectory run. Panel complete only
after successful audit. No new Python parity is needed for unchanged network.

Build the separate exact artifact with submit, compile-check<=100000chars,
then ONE strict zero-grace eight-game procCheck vs v09 on established process
maps. No timing retry. Panel/process/strength run serially, without concurrent
CPU-heavy work. Preserve preparation failures and technical blockers honestly.

## Fixed strength comparison and closeout

Sole strength comparison: practical-v4 vs selected frozen v09, two workers,
CG budgets, base seed15300000, subsequent blocks+100000/+200000. Exclude all prior,
development, teacher and process maps. Fixed300/+200/+500 games, stop strictly
below50% at cumulative300/500, final pass>=55% over1000; no early pass, tuning,
retry, extension or mirrored spatial progress series. CIs are diagnostic only.

A process failure is an independent readiness blocker even if local strength
passes. Local failure skips boss. Local success would still require the matched
40 live-boss comparison and execution authorization before promotion. No arena
upload, push or strategy promotion is implied by the request.

Record results in README/BOTS/DEVLOG/PLAN, retain exact code/artifact, all checks,
raw games and hashed v4 corpus dependency, then make the authorized local commit,
including rejection. Wider multi-branch search, longer reliable lookahead,
useful long-horizon value and competitive training remain open work.

## Completed results — 2026-10-10

Implementation and checks: 583 scoped Java tests pass, including 10
new v7 synthetic tests and four new packaging/registration checks. The six
unrelated pre-existing untracked Pduhard tests remain explicitly excluded; this
is not an unqualified full-suite claim. Core, network, observation and v4 weights
are unchanged. All 292 archived CG games reproduce exactly. Historical
v1-v6/current/v09/neural-v2 submission bytes are preserved. No training or new
Python numerical-parity run for the unchanged inference implementation.

Saved panel: first four original v4 training maps and teacher-P0 trajectories,
17/20 requested preturns survive before terminal;
both perspectives, 34 calls per phase. Full recorded game histories,
metadata, pre-state hashes, score and terminal boundaries match core. Missing
post-terminal positions are not replaced. Search-off matches v4 throughout.

Ample: 34 attempts, 34 completed proposals,
4 duplicate build sets skipped, 30 complete
comparisons, 3 changed plans. Nominal36ms: 31 attempts,
30 completed proposals, 3 duplicates,
26 complete comparisons, 3 changes, 2 search
cutoffs. Cutoff phases: {'alternative': 1, 'opponent': 1, 'base': 0, 'candidate': 0}. Invalid proposals:
0; greedy interruptions 0; calls over
the supplied budget 0. Completed nominal decisions agree
with ample; 0 nominal actions differ overall.
Nominal mean/p95/max: 23.051/34.050/34.421ms.
Ample mean/max: 20.415/43.583ms. These serial warmed
phases are mechanism/coverage evidence, not a speed comparison or host guarantee.
Among completed ample proposals, 13
change construction count, and 3
branch after original root STOP. These are proposal changes, not necessarily
accepted changes to the final action.

After timing, independent fresh conditional forwards reconstruct greedy and
alternative proposals. Public Rules.resolve verifies 60
trajectories and 112 transitions for 30 complete
AMPLE comparisons: root priors/opponent, conditional construction, current child
legality, simultaneous continuation, exact long returns and terminal/depth bounds.
Nominal calls receive legality/fallback/completed-action checks; their hidden
simulator states are not independently replayed. Original input and greedy
disruption stay unchanged. The plans may differ in construction count/cost.
The audit establishes correct implementation of the approximate continuation;
it does not establish accurate opponent prediction.
Of 30 complete ample comparisons,
22 tie and
10 have both returns zero. These are exposed
panel diagnostics, not a causal explanation of match strength.

Exact Player: 94,796 characters; source SHA
`32235e07e7582a0ed9929c2c7e6bb53a1a9cdeed54870cf949dd884f159bae74`;
build `4512b010ca41943037e28f840b197b94b89d92ba1c6bbaa5eaadade9d34abc12`. One strict zero-grace process panel: 8/8 clean;
first response maximum 645.189ms, later maximum
36.548ms. No timing retry.

Practical-v4 vs selected v09: **1W/299L/0D/300**, score 0.33%;
**STOP_FUTILITY_300**. Mean score difference -9308.25;
errors candidate/control [0, 0], slow native calls [3, 0],
maximum native call times [119, 41]ms. All 150 fresh paired
maps exclude 21,597 prior/development maps. Two
CG-budget workers, base seed15300000; fixed block decision with no tuning,
extension or retry. Local failure skips boss. All
745 frozen files remain unchanged through evaluation.

Decision: retain the completed conditional-construction experiment and reject
promotion. Selected current/v09 stays unchanged; no upload or push. V7 implements
one root alternative with a genuinely conditional suffix. It does not implement
full multi-branch search or solve the known short-horizon/continuation limitations.
Wider branching, longer reliable lookahead, useful future value and competitive
training remain open.

Evidence: [summary](evidence/spatial-ppo-v7.json),
[archive index](evidence/spatial-ppo-v7-artifacts.json),
[retained archive](evidence/spatial-ppo-v7-artifacts.zip).
The archive contains original prospective protocol, source/runtime freezes,
tests, full saved panel/trajectory audit, exact submission and raw bench reports.
Its hashed corpus dependency is the unchanged v4 archive.
