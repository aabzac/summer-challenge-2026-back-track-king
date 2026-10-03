# neural-v3: deadline-aware execution of the retained neural-v2 weights

Predeclared October 3, 2026, before implementation results, new process games or
strength games. Baseline experiment: commit `1796158`, neural-v2 local 645/1000
against v09, but one strict timeout in eight process games. This follow-up addresses
that observed readiness failure while preserving the trained model.

## One fixed change

Keep `NeuralV2Weights` byte-identical; no training, feature formula change, new
candidate filter, closing rule, noise setting or policy search. New `NeuralV3Bot`
and `NeuralV3Features` preserve all completed calculations. Original neural-v1/v2
source and weights stay unchanged. A non-expiring deadline must reproduce the same
features, candidates and decisions as neural-v2.

The new execution path checks time between expensive feature/path/candidate units
and during candidate scoring. `Params.NEURAL_DEADLINE_RESERVE_MS=2` is fixed before
testing: stop new work at the supplied search budget minus 2 ms, in addition to the
existing external CGRunner margin and 6-ms later-placement entry guard. No tuning
of this reserve using process or strength outcomes.

An incomplete feature/candidate/scoring pass is discarded in full. If initial
preparation or sabotage selection cannot complete, return WAIT and record no chosen
sabotage target. If construction is interrupted, retain only already completed
legal placements and the fully selected, guarded sabotage action. Do not start the
first construction pass after the deadline. Feature observation/history reflects
actual returned actions; temporary state must be restored across interruptions.
Scoring interrupted after some noise draws may consume those draws; changed timed
decisions are part of this new version, not claimed identical to v2 under timeout.

Each shared-core operation remains atomic, and OS/JVM scheduling cannot be bounded
by this cooperative mechanism. The objective is to close the observed long
unchecked sequences, not assert a hard real-time guarantee. `btk.core` is unchanged.

Actual submission warmup remains the existing isolated `MainBot` path with 36-ms
slices. The bypassed direct-bot 5-ms warmup method is not the proposed fix.

## Fixed verification and evaluation

- Deterministic interrupted-pass and recovery tests, including feature/state
  restoration, retained completed actions and correct no-target history after WAIT.
- Non-expiring feature/action parity with retained v2, including both seats and
  sequential placements. No per-node allocations added to feature/evaluation loops.
- Full Java tests and CG replay validation. Regenerate previous v2/v09 into private
  directories to verify preserved source/config identities, without overwriting
  their saved artifacts or repeating their process/strength tests.
- Isolated `submitVersion=neural-v3`, exact generated weights and 100k-character
  limit. Exactly eight new strict zero-grace process games versus v09: both seats
  on saved maps 903510176, 904146009, 904148181 and 904147739. No concurrent heavy
  workload or retries; complete the panel and retain any failure.
- One fresh practical-v4 comparison versus selected v09: 300/+200/+500 games,
  two workers, seeds 2600000 / 2700000 / 2800000. Futility stop below pooled 50%
  at 300 or 500; only the full 1000 may pass at >=55%. Exclude all previous maps,
  including v2's training/probe/corpus and its just-completed strength maps.
- No source/parameter adjustment after results. A failed stage remains failed.
  No promotion from a local pass alone: require process readiness and the 40-game
  live boss stage with a recorded refresh decision for the outcome-guided control
  cache. Arena upload/promotion remain separate actions.

This is a new timed-policy candidate and gets its own strength evaluation; v2's
64.5% is motivation, not transferred confirmation. Record and commit the actual
outcome, including rejection.

## Implementation and verification

Completed with the exact retained weights (`bf650f11...` generated Java file),
unchanged neural-v1/v2 sources and shared core. All **406 Java tests** passed,
including exhaustive deterministic feature checkpoint interruptions/recovery,
both-seat noisy action parity and discarded-pass/retained-action behavior.
The engine reproduced **292/292 CG replays**. The actual isolated36-ms MainBot
warmup remains unchanged. [Verification evidence](evidence/neural-v3-verification.json)
retains the pre-results protocol,126 source pins, exact manifest and process timings.

Isolated Player: **92,497/100,000 characters**, source
`2aee3ffec84a06dd314179cdf6d97475e87cf3dc8bd01aab81f27291a6bc563f`, build
`9158eae9029544f3b1984d543c36c1e5d3bd73bfbcbb5ba3a4f633a77da13267`.
All **8/8 strict process games** passed with zero grace and no retries; max first
response677.7673ms, max later response**40.1253ms**. This includes both seats on
904148181, the map of v2's recorded timeout.

Private regeneration reproduces neural-v2's complete manifest/source/build exactly.
The retained default Player remains `1df7c131...`. A unit assertion initially confused
that older artifact with the current repository's baseline export (`66559817...`):
commitd8bd6bd had already added unused `INDRILL_INK_PENALTY=2`. Removing that existing
declaration/config entry makes normalized sources identical. Only the test expectation
was corrected; V3 packaging did not introduce this historical difference. The full
suite then passed. Both identities and the proof are retained in verification evidence.

## Local result: PASS_1000

One fixed practical-v4 evaluation, two workers, CG486/36-ms search budgets:
**194/300**, **110/200**, **317/500**. Pooled **621 wins /379 losses /0 draws =62.10%**,
zero game errors, **500 distinct paired maps**. Both futility looks cleared; the final
>=55% criterion passed. No overlap with the prospective exclusion unions, including
all768 training,3 probe,380 shared-core corpus and500 v2 benchmark maps.
[Outcome-level strength evidence](evidence/neural-v3-v4.json).

The in-process benchmark recorded one53-ms call for the candidate (seed2800231,P1),
versus11 calls over50ms for v09. This summary includes first turns with486-ms budgets
and does not record the slow call's turn, so it cannot identify a deadline violation
or its cause. It remains a timing diagnostic; the separate strict process panel passed.
Cooperative checks do not establish a hard real-time guarantee.

Historical v1's55.8% and v2's64.5% were measured on different maps. Neither difference
is a paired comparison with v3 or an estimate of the deadline change's isolated effect.
The supported conclusion is that this fixed candidate passed the local gate and the
predeclared exact-artifact process panel.

## Boss stage: authorized continuation in the permanent puzzle IDE

The old outcome-guided pool is not reused as a fresh17/40 threshold. Before live
outcomes, freeze a new40-control schedule (20P0/20P1), then40 candidate requests on
the same maps/seats, using the already verified fill-v1 artifact and documented v09
alias. The schedule excludes16,352 prior/reserved seeds and671 known CG-content
identities. Subsequent shared-core validation confirms all40 new maps are distinct
and absent from16,221 prior training/local/corpus maps.
[Pinned readiness plan](evidence/neural-v3-boss-readiness.json).

Automatic approval initially blocked transmission of the specific source artifacts;
no request was sent then. The user subsequently explicitly authorized the40+40 IDE
batch. Its first control request on2026-10-03T19:31:46Z returned **HTTP422/code270**:
"It's too late to connect to the challenge." No game ID or confirmed game outcome
was returned; the runner conservatively marks the request uncertain and retains its
intent. **One control request, zero confirmed games, zero candidate requests; no retry.**
Whether the rejected request persisted source in the remote IDE is not verified.
[Interruption evidence](evidence/neural-v3-boss-interrupted.json).

The user supplied the working [permanent puzzle IDE](https://www.codingame.com/ide/puzzle/summer-challenge-2026-back-track-king).
The old contest rejection and absence from a public catalog did not establish that
this training version was unavailable. Its official client uses a puzzle session
and owner-authenticated replay lookup `[gameId, loggedUserId]`; the old lookup with
a null owner cannot read the new private IDE games. The current IDE source was
backed up before play and matches the retained control exactly.

The first training control game **907056523** completed: **LOSS**, P1score1670
versus1761. It identifies the current **Boss4 executable6767608**, while the
bootstrap's `agentInitialId6740207` refers to a different identifier. The engine
reproduces the replay exactly; its shared-core map is absent from the previous
training/benchmark union. A separate immutable metadata amendment keeps all40
slots/builds/exclusions and copies the first intent, response and canonical bytes
unchanged, including the original intent's planId. The first loss stays slot1;
the continuation sends only39 remaining control requests. No strategy, map,
outcome or source is replaced. [Amendment evidence](evidence/neural-v3-boss-amendment.json).

The owner-lookup adapter pins its five dependency files and private bootstrap
binding. Focused offline tests verify exact play-payload preservation, retention
of the first loss, only39 new plays, normal cached-control acceptance and rejection
of changed source/slot/game/provenance. All56 distinct offline tests pass:46 existing
batch/cached tests and10 new tests. Initial failures were two incorrectly placed
test fixtures; the intended path guard rejected them and the corrected fixtures pass.
[Plumbing test evidence](evidence/neural-v3-boss-plumbing-tests.json).

## Final result: practical-v4 PASS, verified candidate

Exactly **40 control +40 candidate** games completed against the same canonical
Boss4 agent6767608, with20 games per seat in each arm. Retained v09 control:
**11 wins /29 losses /0 draws**. Neural-v3: **15 wins /25 losses /0 draws**.
Scores are37.5% versus27.5%; the candidate passes the predeclared >=control rule.
On matched maps,9 control losses became candidate wins and5 control wins became
candidate losses;6 were shared wins and20 shared losses. Candidate seat results:
P0 **7/20**, P1 **8/20**. This is a practical regression check, not proof of an
arena-ranking gain or an isolated causal effect of the deadline change.

All **80/80 new canonical replays** reproduce exactly in the unchanged shared core.
Zero explicit technical failures, map/seat/build mismatches, retries or excluded
training games. Original first loss907056523 is included once. The earlier rejected
contest request remains separately preserved with unknown outcome and no claimed game.

The unchanged v4 checker combines **621/1000 local PASS** with **15/40 versus11/40
boss PASS**, using the documented exact fill-v1/v09 control alias. Its `promotion=true`
field means gate eligibility; no strategy selection or arena publication was performed.
Full eight-game strict process readiness, source/weight/artifact identity and engine
checks also pass. [Final outcome-level audit](evidence/neural-v3-boss.json) is authoritative
for the completed boss and combined gate; the earlier local evidence retains its
historical pending-boss/consent snapshot.

**Verified candidate retained; not promoted.** The selected v09 and saved default
submission remain unchanged. The authorized IDE games sent the frozen neural-v3
source. Arena publication and strategy promotion are separate next actions.
