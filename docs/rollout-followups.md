# Three rollout follow-ups, registered before play

Requested order: diverse opponent plans, own-plan reconsideration, extra sabotage target.
Each is an independent default-off change from selected v06; all quick-income flags and
the other two new flags remain OFF. Control B is frozen v06, common opponent C frozen v05.
No tuning after outcomes, cumulative variants, retries, automatic promotion or arena upload.

1. **diverse / USE_ROLLOUT_DIVERSE_REPLIES:** preserve the first opponent route and the
   number of scenarios. Prefer the highest-ranked retained route connecting a different
   unordered town pair for each further reply; otherwise a different corridor (exact/reverse
   path equality), otherwise the next unused route. No new path queries or larger portfolio.
2. **replan / USE_ROLLOUT_REPLAN:** after four simulated turns, once per rollout, reconsider
   our own first-priority route among the existing portfolio. Rank unfinished uninked routes
   by exact core net-income gain after completion times remaining earning horizon divided by
   ceil(remaining paint / 3), using the existing route horizon. Prefer the prior route on ties;
   retain it if no positive alternative. No new paths, opponent replanning or threat scan.
   Incomplete reconsideration discards the whole rollout comparison. Depth remains eight.
3. **target / USE_ROLLOUT_SECOND_TARGET:** retain the second positive sabotage score from
   the existing four-region shortlist when the root target scan completes. First finish the
   ordinary construction/best-target/WAIT search. If time remains, compare one extra target
   for the winning construction root only, against every existing opponent scenario. This
   bounds extra work to one comparison (at most 16 simulated turns) and preserves normal
   root coverage. Winning immediate closure has no alternate; partial comparisons discarded.

Mechanism diagnostics: same pre-turn positions at turns 0,5,10,15,20,30,40,50,60,70,80,90
from all 14 corrected v06 boss games, wins and losses. Fresh NOISE=0 baseline and candidate,
nominal 486/36 ms and ample 60000 ms; input hashes and legality checked. These are diagnostics,
not strength or reproduction of original arena timing. All confirmation maps are separate.

## Fixed strength protocol

practical-300-1000-v2, draws half; authentic generator, bot SEED=1, nominal CG 486/36 ms,
two workers, sequential stages without other heavy jobs. Reserved pools:

- diverse: screen 130000..130149; direct 131000..131499; style 132000..132149;
  full 133000..133499.
- replan: screen 140000..140149; direct 141000..141499; style 142000..142149;
  full 143000..143499.
- target: screen 150000..150149; direct 151000..151499; style 152000..152149;
  full 153000..153499.

300 A vs B: <=52% stops; >52% and <60% requires separate fixed 1000 at >=55%; >=60% skips
direct 1000. Direct failure stops before common-opponent stages. After success, A vs C and
B vs C each play 300 on the same fresh 150 maps; >=+3 percentage points passes. Otherwise
each plays 1000 on the same separate 500 maps; candidate >= control passes, including ties.
Every stage excludes all known previous map hashes and earlier variants/stages, except the
intentional paired common-opponent control. Freeze full parameters and source/class hashes,
verify identities before/after every stage, preserve all outcomes, errors and timing outliers.
No pooling, retuning, outcome-based early stopping or repeated screens. CI is descriptive.

Tests, core fidelity and default-v06 equivalence precede play. If strength passes, additionally
build and archive the exact candidate submission and run strict process checks serially on
maps 903510176, 904146009, 904148181, 904147739, two games per individual map (both sides).
Keep existing selected Player intact/restored; a failed timing check is not retried to pass.
Passing these samples is no guarantee on the arena host. No readiness claim without them.
Record each result in BOTS/DEVLOG/PLAN and commit each completed experiment locally.

## 1. Diverse opponent projects - mechanism checks

- 232 Java tests pass, including both-side captured-state legality and reply selection with
  reverse routes, another town pair, a detour, and no retained route. Default-v06 equivalence
  remains green. Core unchanged; 264/264 downloaded arena replays reproduced exactly.
- Fixed study: 133 states from the 14 v06 boss games. With ample time, both policies have two
  replies in 101 states: same town pair 91 -> 10; same corridor 87 -> 7. Both execute 1187
  complete comparisons and 13753 path queries; actions differ on 23 states.
- Nominal baseline/candidate: two-reply states 100/101; same pair 92/13, same corridor 88/10;
  555/559 complete comparisons, 12238/12303 queries; actions differ on 26 states. Timed proposal
  coverage differs, so these are mechanism diagnostics, not causal strength estimates.
- Durable diagnostic: docs/evidence/rollout-followup-diverse-diagnostic-v1.json.

### Strength result: STOP_SCREEN_300

300 games / 150 fresh maps: **151-148-1, 50.50%**, zero errors. Below the >52%
continuation floor: no direct 1000 or matched v05 stages. Mean score difference +84.8;
search maxima 59/45 ms, slow moves 1/0; elapsed 574.6 seconds. All games retained.
Source/class identities verified before/after; seven gate tests also pass. Keep flag OFF,
selected v06/Player unchanged, no process-readiness or arena improvement claim.
Durable strength evidence: docs/evidence/rollout-followup-diverse-v1.json.

## 2. Mid-rollout own-plan reconsideration - mechanism checks

- Independent REPLAN flag; DIVERSE and all quick flags OFF. Once after four simulated turns,
  revalue at most six existing own routes with core income after completion and remaining paint.
  No new path queries; expired partial reconsideration invalidates that whole comparison.
- 234 Java tests pass: a partly built river route becomes preferable to the old project, inked
  routes are rejected, expired reconsideration returns no result; both sides, input isolation,
  captured-state legality, work bounds and default-v06 equivalence checked. Core unchanged.
- Same 133 recorded states. Ample baseline/candidate: 1187/1187 comparisons, 13753/13753 queries;
  candidate reconsiders 2304 times, changes priority 1236 times in 76 states; 12 changed actions.
- Nominal baseline/candidate: 558/524 comparisons, 12345/12272 queries; 1064 reconsiderations,
  520 priority changes in 62 states; nine changed actions. This added work can reduce coverage.
  Diagnostic evidence only: docs/evidence/rollout-followup-replan-diagnostic-v1.json.

### Screen: continuation required

Fresh 300 / 150 maps, seeds 140000..140149: **154-141-5 = 52.1667%**, zero errors.
The strict >52% floor is passed by half a win-equivalent point. Continue unchanged to the
predeclared separate 1000, seeds 141000..141499; only that sample must reach 55%.
Screen search maxima A/B 45/39 ms, slow 0/0; elapsed 558.6 seconds. No outcome retuning.

### Independent 1000: NOT PASSED

**469-501-30 = 48.40%**, zero errors, below 55%. No matched common-opponent stage,
retry, promotion or process-readiness claim. Search maxima A/B 55/47 ms, slow 2/0;
elapsed 1775.3 seconds; mean score difference +25.2. All source/class hashes verified
unchanged across both stages. Retain OFF and selected v06/Player unchanged.
Durable full strength evidence: docs/evidence/rollout-followup-replan-v1.json.
