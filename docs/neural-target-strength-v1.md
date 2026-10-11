# Neural target strength v1

Declared 2026-10-11 from `c0740d0`, before strength games. The user explicitly
requested strength testing first and deferring the source-size limit until a
benefit is demonstrated. This supersedes the preceding feasibility stop for
this local experiment only. No upload, promotion or extra time is implied.

## One fixed candidate

Candidate `current?USE_NEURAL_TARGET_PROPOSAL=1`, control `v09`. All existing v09
parameters, including its NOISE=0.1, stay unchanged. The neural target proposal
is deterministic, using the unchanged exact TargetProposer, original sabotage
weights and closing guard from neural-target-feasibility-v1. No training, feature
simplification, alternative head, runner-up or threshold tuning.

The prototype is local-only: BotFactory selects a wrapper around the unchanged
current RolloutBot. No strategy logic changes in current/frozen bots; existing
private rollout is called through a prebound MethodHandle without varargs or
per-node allocations. The enabled flag is rejected by FileMerger, avoiding a
baseline artifact falsely labelled as the hybrid. Flag-off artifacts must remain
byte-identical to the preceding fresh current artifact. No candidate artifact or
strict candidate process check is claimed in this strength-first stage.

1. Run unchanged v09 search and its normal final fill with the ordinary allowance.
   Retain the complete emitted construction and target E. Observe the actual public
   board every turn, including opening, incomplete search and deadline fallback.
2. Only after complete ordinary and network search, outside the protected opening,
   and with time remaining, obtain one complete neural target. -2 is abort; a
   completed -1 is a legitimate no-disruption alternative. If it equals E, stop.
3. Score complete E and complete D with the SAME retained own route-0 continuation,
   opponent replies, depth and robust aggregation. Temporarily borrow the WAIT
   root and restore its storage/count afterward. Never compare with native raw
   selectedValue and never refill after changing the target.
4. Accept D only if both comparisons finish, both scores are finite, D exceeds E
   by more than 1e-9 and the deadline still permits commitment. Otherwise return E
   unchanged. Record only the actually emitted target, never an unselected proposal.

The wrapper starts one absolute deadline at entry. The baseline keeps its full
normal allowance and starts its own existing timer moments later; extra work
clamps that deadline downward to the wrapper deadline, never restarts or extends
it. Check before/after feature work and every rollout and before commitment.
Existing baseline atomic overruns and the tiny entry/history overhead are not
a hard process timing guarantee. No reserved slice is taken from baseline search.
Warmup, if called, uses a disposable instance and cannot pollute live history.

## Registered local strength sequence

Use unchanged practical-v4 local rules and `tools/run_practical_v4.py`:

- Exactly 300 paired games /150 maps, seed start **231011000**.
- If pooled score is at least 50%, add 200 /100 maps at **231111000**.
- If pooled score is still at least 50%, add 500 /250 maps at **231211000**.
- Stop strictly below 50% at cumulative 300 or 500; no early pass. A survivor
  completes all 1000, with one final local threshold of at least 55%.

Two workers, nominal CG budgets 486/36 ms, new bot instances per game, both seats
per map, draws worth half. Register all pools before play. Exclude all prior local
benchmark maps and saved CG/development maps under the same Protocol.initBlock
hash convention; verify disjointness between blocks. No stage retries, seed
replacement, tuning or extra arms after results. Fail closed on engine/bot errors.

Collect per-game proposal/acceptance/fallback counters after timed play, so a flat
strength result can be distinguished from an adapter that almost never runs.
Do not use those counters for mid-experiment tuning or extending the sample.

The requested deliverable is local strength. Boss-cache evaluation, packaging
and exact-artifact process validation remain outstanding even if the local gate
passes; neither that pass nor this user request authorizes upload or promotion.

## Preflight and evidence

Before the first strength game: focused tests for construction preservation,
paired score equivalence, ties/cutoffs/history, disabled baseline parity, factory
selection and submission guard; engine replay validation; independent code review.
Build only the unchanged default in a separate output directory and compare its
source hash with `ab0954a4545f25222fce6bccf8fa11866d276266e96b4728b502c83996d847e5`.
Freeze source/classes, parameters, protocol, pools, inputs and runtime. Verify
before/after each complete block. Retain every report, failure and interim verdict.

Update PLAN/BOTS/DEVLOG/README, archive compact evidence and commit the completed
experiment locally, including rejection. Preserve the earlier feasibility result
and unrelated untracked work. No new training or automatic follow-on experiment.

## Completed result — 2026-10-11

**STOP_FUTILITY_300 / REJECTED**: 129 wins, 154 losses, 17 draws; 137.5/300
points (**45.8333%**). The fixed first block contains 150 fresh maps, each played
in both seats, seeds231011000..231011149, two workers, nominal486/36ms budgets.
All maps are disjoint from the22,744 prior/development/saved-CG map identities.
Block1 took513.252s. No bot errors or missing games. Blocks2/3 were registered
before play but never run; no retries, parameter changes or additional arms.

The optional module was active, rather than silently falling back everywhere:

- 24,871 candidate turns;15,312 structurally eligible after complete v09 search.
- 15,305 proposal attempts;7 eligible turns had no remaining budget.
- 7,301 proposals repeated the original target;248 feature passes hit the deadline.
- 7,146 complete equal-context E/D comparisons;610 comparison cutoffs.
- 754 accepted target changes in237/300 games (**3.0316% of all turns**).
- No invalid proposals, nonfinite comparisons or completed no-disruption proposals.

These counts describe mechanism use, not the causal strength of individual changes.
The paired local result does not establish a strength improvement for this fixed
integration. It does not prove that all possible neural target integrations are
weaker. No compression or source-limit work is justified by this candidate's result.
Selected v09 and its default flags remain unchanged; the experiment is closed.

Native timing recorded2/5 slow calls and56/77ms maxima (candidate/control).
This is not an exact-artifact process pass; neither candidate packaging nor
process/boss checks were run. The size limit did not stop this experiment.

## Verification and retained evidence

- 102 unique focused tests have passing latest results. The first combined run
  had101 passes and one test-fixture failure: isolated town regions had no legal
  protected opening cells. Only that fixture changed; the affected eight tests
  then passed. Both invocations and the original failure XML are retained.
- Engine validation:292/292 archived replays reproduced exactly, no core edits.
- Fresh default artifact:98,709 characters, same pinned SHA-256 as above; the
  default export remains byte-identical. Enabled local-only export is rejected.
- Independent source/test and raw-result reviews; every one of2,636 frozen files
  and seven runtime/dependency files remained unchanged across the single block.
- Full game reports, per-game counters, decisions, logs, exclusions, preflight
  evidence, prospective protocol snapshot and relevant source are retained.

[Summary and complete local decision](evidence/neural-target-strength-v1.json),
[archive index](evidence/neural-target-strength-v1-artifacts.json),
[artifact archive](evidence/neural-target-strength-v1-artifacts.zip).
