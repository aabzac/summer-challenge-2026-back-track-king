# Rollout first-turn fill evaluation (2026-10-06)

User authorized trying the full-action evaluation idea on clean selected v09.
`USE_ROLLOUT_ALL_CELL_RISK` stays OFF. No change to weights, opponent policy,
future-turn rollout construction, route generation or search depth is part of
this experiment. The existing local/boss decisions remain historical evidence.

## Bounded mechanism check

Use all 110 already captured development positions in
`build/analysis/reply-study-v1/capture`, in the existing loader's order. These
are older v05-family trajectories, not a representative sample of current v09
arena games. No new capture, learning or outcome-based position selection.

Run fresh clean current/v09 policies with `NOISE=0` and ample time. For the
existing first-turn construction/target alternatives, apply the existing `fill`
procedure separately to attack and no-attack branches, because it excludes the
chosen target region. Re-evaluate using the existing rollout, with only our
first-turn cells replaced. Future construction, replies and evaluation stay the
same. Record omitted filler, full-action ranking changes, legal actions and input
isolation. Core comparisons against opponent WAIT are mechanism diagnostics,
not forecasts of actual opponents or evidence of recovered wins.

Preserve one report, with state identities and per-position outcomes. If filling
adds no cells or never changes a non-tied choice, close the idea before strength
games. Otherwise implement one default-off candidate, and check the actual policy
on the same panel before its fixed strength comparison. This diagnostic does not
set a target percentage or a strength threshold of its own.

## Conditional candidate and strength check

Candidate `current?USE_ROLLOUT_ROOT_FILL=1`, control frozen `v09`. Evaluate a
deterministic filled first action separately for each root/target, use that same
action for every reply, and emit exactly the selected completed action. Keep
opening and incomplete-search fallback behavior. Retain original future-turn
rollout and opponent construction. Primitive reusable buffers; no per-root/node
allocation. Flag OFF must preserve selected v09 behavior and frozen identities.

Build separately to `build/submit-rollout-fill-root-v1`; preserve the saved default
submission. Before strength testing require the engine validation, appropriate
tests, source limit and exactly eight strict process games: both seats against
v09 on maps 903510176, 904146009, 904148181, 904147739. Zero grace, no process
retry for a pass, run serially without heavy concurrent work.

Practical-v4: two workers, fresh paired maps in 300/+200/+500 blocks, seeds
4800000 / 4900000 / 5000000, experiment id `rollout-fill-root-v1`. Existing runner
excludes prior local maps. Stop only for futility below 50% at 300/500; final
local threshold >=55% over 1000, no early pass or tuning/retries. Boss readiness
and the 40-game cache check remain required for an overall pass; a local failure
ends evaluation. No arena publication, selected-strategy promotion or IDE write
is part of the current local experiment.

## Mechanism result

Completed the fixed 110-row check (108 unique inputs), with exact fresh v09/current
parity and reconstruction of the baseline choice/comparison count. Baseline selected
actions received final filler in 45 positions. Among evaluated alternatives, 527
tuples received filler, 272 model values changed and 90 immediate own-vs-WAIT
income outcomes changed. Ranking changed in 58 positions (including ties).

Nine winning tuples changed: two are tied continuations with identical emitted
actions; seven produce different full actions and strictly higher filled-model
values, on seven unique inputs. Value differences are 8, 11, 8, 32.5, 55.5, 68
and 4. These are correlated development observations and model scores, not wins.
All outputs legal and inputs isolated; engine validation 292/292 exact.

This supports the single conditional candidate above. The mechanism report
remains unchanged.

## Implementation readiness

`USE_ROLLOUT_ROOT_FILL=false` is independent of the rejected risk experiment.
The implementation uses reusable primitive scratch buffers, separately fills
each root/target before evaluating replies, and retains that exact completed
action. Opening/fallback and future-turn construction retain existing behavior.
Raw-root deduplication and initial target selection are deliberately unchanged.

Actual policy matches the saved filled winner action and model value on all
110 positions with ample time, including the seven changed full actions. In
the descriptive alternating 36-ms ON/OFF check, ordinary search completes on
49 versus 50 of 110 positions, mean time 27.457/27.425 ms, and emitted actions differ on eight.
Timed ON/OFF differ from their ample answers on 45/44 positions respectively;
this is not a strength or strict-process timing claim.

The 480-test full run had one new fixture failure: its unfinished connection
spent all paint before reaching fill. Replacing it with a completed protected
connection makes the intended fill assertion applicable; the corrected targeted
test passes. Production unchanged after that full run; the other 479 tests
passed, including frozen hashes and default-off parity. Independent code review
found no blocker. No core changes; replay validation remains 292/292 exact.

Current-rollout exports remove safe punctuation-adjacent newlines to fit the
source limit; comment newlines remain, text blocks fail loudly, historical
exports retain exact bytes. Compiled decision parity covers the new candidate
and the previous risk variant. Candidate is 98,708/100,000 characters, source
`57753e5b615f6df823aa61030f9df518b38f34428583ba3f963b7a82cb417ccb`, build
`907238f108da380d21ddcbea59925d1e23f8515e3b8d44456e0bf445c30d7343`.
The registered eight strict games passed with zero problems or nominal overruns;
maximum later response 44.3567 ms. Source/class/parameter pins were frozen before
these games and rechecked before strength. Default saved v09 source unchanged.

## Strength result and decision

Completed the registered practical-v4 comparison against frozen v09, two workers,
CG 486/36-ms search budgets, with unchanged code and parameters:

- Block 1, seed 4800000, 300 games: 157W/141L/2D, 52.6667%.
- Block 2, seed 4900000, 200 games: 110W/90L/0D, 55.0%; pooled 500 score 53.6%.
- Block 3, seed 5000000, 500 games: 273W/227L/0D, 54.6%.

Total **540W/458L/2D = 541/1000 points, 54.1%**, zero game errors. Both interim
looks clear, but final score misses the 55% gate: **NOT_PASSED**. Boss stage
SKIPPED_LOCAL_FAILURE. All 500 paired maps are distinct and excluded from the
prior local index of 19,351 maps / 196 reports. No tuning, stage retries or extension.

The observed local advantage is insufficient under the agreed selection rule;
it does not establish an arena improvement. Leave both ROOT_FILL and ALL_CELL_RISK
OFF, selected v09 and its saved default artifact unchanged. No CG write, new boss
games, arena publication or promotion. The IDE therefore still contains the
previously tested risk candidate from the separate user-requested boss diagnostic.

[Compact evidence](evidence/rollout-fill-root-v1.json) and the
[80-member archive](evidence/rollout-fill-root-v1-artifacts.zip) retain the
prospective protocol, source/class/artifact identities, mechanism and actual-policy
reports, original/corrected test logs, strict process reports, fixed pools and full
strength reports. Experiment closed for local commit.

## User-requested boss diagnostic (2026-10-06)

After the completed local rejection, the user requested: "А запусти с боссом игры".
This authorizes a separate fixed 40-game IDE diagnostic of the same ROOT_FILL
artifact above. Preserve the original 54.1% / NOT_PASSED local verdict; no arena
publication or strategy promotion is authorized by this diagnostic.

Control: verified `candidate` arm of
`build/cg-boss/neural-v7-v09-fresh-control-40/plan.json`, exact frozen v09 source
`aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac`.
The October 4 cache contains 15W25L, 40 distinct maps and 20 games per seat.
Fresh read-only discovery confirms Boss 4 agent 6767608, league 6/3/0. Reuse after
the risk diagnostic is intentional regression testing; ROOT_FILL was developed
on separate local states. The two-day-old control and unchanged public identity
cannot prove unchanged hidden boss implementation or execution conditions.

Revalidated the candidate against all four exact-source/executable strict process
reports (eight games, zero problems). Preserve the current IDE source privately
before any play; its actual source hash is
`a7771129318eb53ec752c0bea45292a4bc90fc2e582a3102109661eff8c55a45`.
This fresh read supersedes earlier assumptions about the IDE's current contents.

Run exactly the cached 40 seeds/maps/seats, 20-second spacing, one request per
slot, no tuning, extensions or automatic uncertain-play retries. Retain every
executed outcome, verify canonical map/seat/seed/boss/build attribution, and validate
all 40 replays through the shared engine. Final IDE will contain the ROOT_FILL
candidate; the previous code is backed up. Local selected/default v09 stays unchanged.

Plan: `build/cg-boss/rollout-fill-root-v1-boss-40/plan.json`, plan ID
`bb08e172797c368e0db0edad727288227f7b83d41102e2564d555eb8d6c152fd`.

Initial execution status: the automatic approval reviewer rejected process launch before
any play request. Its stated reason is that the request for boss games did not
explicitly authorize sending this candidate source to the third-party IDE.
No game was created and no IDE code was changed. Exact source, private backup,
40-slot plan and independent binding review were ready. The user then explicitly
confirmed uploading this ROOT_FILL candidate to the IDE for the fixed 40 boss games
("подтверждаю"). Resume the same approved action and plan; no game needs replacement.

Completed exactly 40 candidate games, one request per slot, no replacements:
**16W24L0D (40.0%)**, versus cached v09 **15W25L0D (37.5%)**. Difference +1
point / +2.5 percentage points. Eight maps change from control losses to candidate
wins, seven from control wins to candidate losses, and 25 have equal outcomes.
The separate boss score check is **PASS**, with zero provenance/technical issues.
The one-win advantage on this reused 40-map pool does not establish stronger arena
performance. Original local **54.1%, NOT_PASSED** remains unchanged; no promotion.

All 40 canonical replays reproduce exactly through `cgValidate`. Final read-only
IDE check matches the candidate source `57753e5b...417ccb` byte-for-byte; Java is
selected, and the pre-series source `a7771129...c55a45` remains privately backed up.
No new control games, tuning, extra candidate games or arena publication. Local
flags remain OFF, selected/default v09 source unchanged.

[Boss evidence](evidence/rollout-fill-root-v1-boss.json) retains every paired
outcome and the original local verdict. The separate
[1.34 MB archive](evidence/rollout-fill-root-v1-boss-artifacts.zip) contains both
40-game canonical sets, exact identities, process/engine/readback evidence and both
protocol snapshots, 268 members. SHA256:
`d6eb707de1660a014377fb71c47a0b4c21095f24fb5afcbc828825d0fd6a9423`.
Private session files, original IDE source and raw play responses are excluded.
