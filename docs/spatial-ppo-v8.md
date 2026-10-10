# spatial-ppo-v8 — paired deeper score lookahead

Status: completed and rejected for promotion, 2026-10-10.
The original prospective protocol is retained below and in the archive.
User authorized the next stage after completed v7. One bounded runtime experiment;
selected current/v09, core, network, observation and all older artifacts stay fixed.
No training, arena upload, push or promotion is part of this experiment.

## Hypothesis and fixed design

V7 scored 1W/299L against v09; 22 of 30 complete saved-state comparisons tied,
including ten with both returns zero. A longer horizon may distinguish delayed
construction consequences. These observations motivate this test, not a claim
that short lookahead caused the losses or that longer lookahead will improve play.

Keep the v4 payload SHA
`89de11f4239844c55c6e61c987ee2443fd5d79a1c71af782d2686e836ec6f3ef`.
Keep V7's original greedy plan and one alternative first BUILD with its fresh
conditional suffix. Preserve original disruption, legal masks, remaining paint,
STOP, duplicate-set skip, same-root opponent and fixed root-prior continuation.
No extra network passes, learned leaf value, discount or extrapolated income.

The single strategy change is paired deeper lookahead. Use TWO reusable core
SearchStates, reset from the same root, and advance base then alternative one
virtual turn at a time. Both players choose from the SAME preturn child state
before that branch resolves. At most seven neural forwards and sixteen core
transitions; previous transitions are not replayed when extending the horizon.

Publish checkpoints at virtual horizons 2, 4, 6 and 8, or earlier when BOTH
branches have terminated. A terminal branch is absorbing: retain its final score,
do not resolve or award more income. Actual steps may differ between branches.
Compare only after both have reached the same virtual horizon or are terminal,
and the deadline check succeeds. Returns are long final-score-gap minus root-gap;
do not add checkpoint returns together. Ties keep greedy. Always use the deepest
published checkpoint, even if a shallower checkpoint preferred the alternative.

The common deadline includes greedy, proposals, opponent and all simulation.
A cutoff before the first checkpoint returns the whole greedy plan. A later
cutoff returns the winner of the deepest fully published checkpoint. A completed
base branch alone can never replace either published score. Keep published
scores/steps/terminal flags separate from partial work. Any invalid/nonfinite
proposal, including a later continuation, rejects search entirely and keeps greedy.

Params: USE_SPATIAL_V8_SEARCH=false by default, true only in the v8 snapshot;
SPATIAL_V8_MIN_SEARCH_MS=12; SPATIAL_V8_DEPTH=8, supported even values 2..8;
SPATIAL_RESERVE_MS=2. V5/V6/V7 search flags are inactive. Start only after complete
greedy and at least12ms remaining. Preserve CGRunner margins and ordinary warmup.
No parameter sweeps or changes based on panel/strength results.

Frozen root priors are an approximate continuation for both sides and become
staler with depth. Exact core transitions establish rule fidelity, not accurate
opponent prediction. Core pathing calls remain indivisible; the work/deadline
bounds do not guarantee timing on another host or in CG.

## Verification and fixed saved panel

Before measurement, test search-off parity, depth2 parity with V7, delayed-payoff
preference reversal, deepest-checkpoint selection, cutoff before first checkpoint
and after partial deeper work, partial score isolation, invalid continuation,
asymmetric/odd-step terminal and both-terminal early stop, both seats, simultaneous
continuation, input immutability, diagnostics reset and legal full-plan fallback.
Preserve exact v1-v7/current/v09/neural-v2 submission bytes, compiled/local parity
and supported parameter bounds. No new Python numerical parity is needed for the
unchanged network implementation. Run scoped Java tests and 292-game cgValidate.
The six pre-existing unrelated untracked Pduhard tests remain explicitly excluded.

Freeze protocol, source, effective parameters, compiled runtime, input identities
and exact submission before measurements. Reuse first four original v4 training
maps with teacher-P0 histories; requested preturns0/25/50/75/99, both perspectives.
Replay complete histories and verify metadata, state hashes, score and terminal
boundaries. Missing post-terminal positions are retained without replacement.
This exposed development panel is mechanism evidence, not a strength test.

Four SERIAL phases: v4 ample, v8 search-off ample, v8 ample, v8 nominal36ms.
Ample60000ms; warmup100ms per phase/map/seat. Report proposal creation/duplicates,
attempts, usable checkpoints versus full-depth completion, checkpoint depths,
changed actions, cutoffs/phases, actual core steps and mean/p95/max timing.
Nominal output may differ from the FINAL ample result: verify it against the ample
result at the SAME published horizon. Record final-result differences separately.
No checkpoint means greedy; invalid continuation also means greedy.

After all timing, independently reconstruct conditional proposals with fresh
network forwards and both trajectories using public Rules.resolve. Audit every
AMPLE published checkpoint: exact scores, actual steps, terminal flags, absorbing
terminal behavior and final selection. Retain actions and state hashes per step.
Nominal receives same-horizon score/action and legality/fallback checks, not an
independent replay of hidden simulator state. Original input stays unchanged.
Retain depth2 versus deeper preference changes/ties as diagnostics only.

Compile the separate submitted Player, check <=100000 characters, then run ONE
strict zero-grace eight-game procCheck versus v09 on the established process maps.
No timing retry. Panel, process and timed strength run serially, without concurrent
CPU-heavy work. Record preparation failures or technical blockers honestly.

## Fixed strength gate and closeout

Only strength comparison: practical-v4 versus selected v09, two workers, CG
budgets, base seed15600000; subsequent blocks15700000/15800000. Exclude all prior,
development, teacher and process maps. Fixed300/+200/+500 games, stop strictly
below50% at cumulative300/500; survivors finish1000, final pass>=55%. No early
pass, tuning, extension, retry or extra mirrored progress bench.

An exact-artifact process failure independently blocks readiness. Local failure
skips boss. Local success still requires the matched40 live-boss comparison and
execution authorization; no arena change or promotion is implied here.

Record results in README/BOTS/DEVLOG/PLAN, retain protocol/source/runtime freezes,
all checks, raw games, exact artifact and hashed original v4 corpus dependency.
Commit the completed experiment locally, including a rejection. Wider branching,
useful long-horizon value and competitive training remain open; deeper simulation
with frozen priors alone does not complete the original top-3 strategy.

## Completed results — 2026-10-10

Implemented two reusable simulations with paired checkpoints2/4/6/8, absorbing
terminals and deepest-complete-pair fallback. V7 proposals and v4 weights remain
unchanged. No new training, core, network or observation changes.

Verification: 595 scoped Java tests pass, including 8 v8 tests and
separate packaging/registration checks. The six unrelated untracked Pduhard tests
are excluded; this is not an unqualified full-suite claim. Historical spatial
v1-v7/current/v09/neural-v2 submission bytes are preserved. All292 archived CG
games reproduce exactly. No new Python parity run for unchanged inference.
Asymmetric-terminal bookkeeping is exercised by an explicitly synthetic injected
fixture; ordinary v8 construction branches do not establish a reachable example
of different terminal times under the identical disruption policy.

Saved panel: 17/20 requested live positions,
both seats, 34 calls per phase; missing post-terminal slots are not
replaced. Full teacher histories/metadata/state hashes/scores match core.
Search-off matches v4. Ample: 34 complete proposals,
4 duplicates, 30 usable completed comparisons,
5 changed actions. Nominal36ms: 27 attempts,
27 complete proposals, 3 duplicates,
19 usable comparisons, 3 changed actions,
6 cutoffs, 0 invalid proposals and
0 interrupted greedy calls.

Nominal mean/p95/max 27.404/34.031/34.053ms;
0 calls exceed the supplied budget. Ample mean/max
23.985/40.432ms. These are serial warmed measurements on
four exposed maps, not host/CG guarantees or an unbiased speed comparison.
Committed horizon counts (0 means no checkpoint; other values are virtual
horizons, not actual steps after terminal):
ample {'0': 4, '1': 4, '2': 0, '3': 0, '4': 0, '5': 0, '6': 0, '7': 0, '8': 26}; nominal {'0': 15, '1': 4, '2': 0, '3': 0, '4': 1, '5': 0, '6': 0, '7': 0, '8': 14}.
Reached configured depth or both terminal: ample 30, nominal
18. Nominal cutoffs retaining a usable pair:
1; without a pair 5.
A usable checkpoint is not a completed depth8 search. Same-horizon comparisons:
19; differences from the FINAL ample action:
2 (diagnostic only). Original per-checkpoint evidence
and actual work counts are retained in the summary and raw panel.
The one observed retained-pair cutoff kept the greedy plan at depth4, with scores
-384/-388; partial work at steps6/5 did not overwrite that result. Thus this panel
does not demonstrate a cutoff retaining an alternative winner (synthetic tests
cover that case). Both differences from final ample have no nominal checkpoint:
one opponent-phase cutoff and one admission skip, not conflicting completed
decisions at different horizons. The18 nominal reached-end calls include4 early
terminal calls;14 actually reach depth8.

After timing, fresh conditional forwards and public Rules.resolve independently
reconstruct ample proposals, 60 trajectories,
424 core transitions and 108 published
checkpoints. Final ample choice differs from the first checkpoint in
2 cases; this is a horizon
diagnostic, not a match-strength claim. Original inputs,
common-root opponent, current-child legality, simultaneous actions, exact long
score deltas, actual step counts and absorbing terminal behavior are checked.
Nominal outputs receive same-horizon score/action and legal fallback checks;
their hidden simulator states are not independently replayed. Exact transitions
do not establish an accurate continuation policy or a strength improvement.

Exact Player: 97,190 characters, SHA
`3a477686dd9606029da960d2d38380da30ebffb22307e0a41af3abaa5572aaff`, build `96ac84c2a58a8000884b0aa9692ccdf168c51511d3285dce33259003a8909287`.
One strict zero-grace process panel:8/8 clean; maximum first response
605.209ms, later 37.450ms. No retry.

Practical-v4 vs selected v09: **2W/298L/0D/300**, score **0.67%**;
**STOP_FUTILITY_300**. Mean score difference -9724.26; errors
candidate/control [0, 0], slow native calls [2, 1], maximum native
times [95, 52]ms. Two workers, CG budgets, base seed15600000,
150 fresh paired maps exclude 21,747
prior/development maps. No tuning, extension or retry; local failure skips boss.
All 758 frozen files remain unchanged through evaluation.

Decision: retain the completed experiment and reject promotion; selected v09
stays unchanged. No arena upload or push. Wider branching, better continuation,
useful long-horizon value and competitive training remain open.

Evidence: [summary](evidence/spatial-ppo-v8.json),
[archive index](evidence/spatial-ppo-v8-artifacts.json),
[retained archive](evidence/spatial-ppo-v8-artifacts.zip).
Archive includes original protocol, source/runtime freezes, tests, complete panel,
checkpoint audit, exact submission and raw games; original v4 corpus is a hashed
dependency rather than a second copy.
