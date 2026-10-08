# spatial-ppo-v3 — fixed opponent pool

Status: completed and rejected for strength, 2026-10-09 (Europe/Bucharest).
Final versus v2: **6W/94L/0D / 100**. Versus selected v09: **0W/300L/0D**,
`STOP_FUTILITY_300`. No promotion; selected v09 unchanged.
The prospective protocol below was frozen before gradients; its exact original
copy and hashes are retained in the evidence archive.
The user authorized the next planned stage: learning against a pool of opponents.
Selected v09 and all prior spatial models remain unchanged.

## Hypothesis and scope

Warm-starting from v2 and collecting new experience against several fixed policies
may improve the spatial policy beyond v2's narrow self-play behavior. This is one
bounded follow-up experiment, not a causal ablation separating the pool effect
from additional training. No architecture, observation, reward or PPO-objective
change; no imitation loss, distillation, search or checkpoint selection.

The trained policy starts from the exact v2 final model payload
`b8cdd34b01aac15c025bd36d41290fb261946debe483b3af268117ed7d8eca1d`.
Adam starts fresh; reset Python/NumPy/Torch RNG to seed20261009 after constructing
the network and loading v2 weights, before first sampling. This is a warm start with new
environments, not an exact continuation of the v2 optimizer/environment state.

## Fixed training recipe

- 128 iterations x 8 environments x 16 real turns = **16,384 environment turns**.
  Only one learner seat per environment enters PPO: **16,384 learner samples**,
  four conditional choices per sample, exactly **1,024 optimizer steps**.
- Environment IDs 0/1 use `spatial-ppo-v2-init`, 2/3 use `spatial-ppo-v2`,
  4/5 use frozen `v01`, and 6/7 use frozen `v09`. Learner seat is environment ID
  modulo two. Opponent and seat assignments never change on resets. Thus each
  opponent receives exactly 4,096 learner turns, 2,048 from each learner seat.
  Number of completed games may differ because episode lengths differ.
- Fixed opponents use their complete frozen parameter snapshots and greedy
  production decisions, serially with a 60,000-ms ample allowance and no warmup.
  Record timing and require search completion (or v09's intentional opening),
  no interrupted spatial inference and legal complete output. This is a training
  opponent regime, not a measurement of their CG-budget arena performance.
- Opponent plans are chosen once from the unchanged pre-turn state and cached
  before the learner chooses its conditional action sequence. They never observe
  that sequence. Both complete plans go through one existing shared-core resolve.
  Opponent actions, probabilities and values must never enter the learner loss.
- Initial seeds 11000000..11000007, then sequential seeds only after actual
  terminals. No map replacements on poor outcomes. Check every generated map
  against prior and diagnostic exposure hashes; report per-opponent/seat terminal
  records, score gaps, episode lengths, resets and unfinished trajectories.
- Same 3,439-weight model and v2 PPO: two epochs, minibatch32, Adam lr0.0003,
  clip0.2, entropy0.01, value0.5, gradient cap0.5, gamma0.99, GAE lambda0.95;
  reward (income gap + terminal +/-30)/100, one discount per real turn.
- CPU PyTorch2.8.0+cpu, NumPy2.3.5, two compute threads. Initial, midpoint64 and
  final128 checkpoints with optimizer/settings/RNG; final only is evaluated.
  No extra training, outcome-guided tuning, early selection or failed-run overwrite.
  Checkpoints do not promise exact environment continuation.

Preserve v1/v2 model, collector, PPO, bridge, runtime, generated weights and
evidence. New pool tooling wraps the existing Java bridge and adds a learner-only
collector. The generated final payload lives in `SpatialV3Weights`; local named
bot and submission use the unchanged spatial production runtime.

## Verification before and after training

Before gradients, test fixed seat/opponent mapping, opponent-plan caching,
simultaneity, legal plan conversion, terminal-only reset, learner-only PPO rows,
reward perspective, GAE boundaries and log-probability recomputation. Freeze this
protocol, source/runtime identities, full effective parameters and exclusions.
Retain test logs/XML and any technical preflight failures. Exclude only the known
pre-existing untracked PduhardBeamBotTest class from the scoped Java suite.

A single no-gradient bridge smoke may use seeds10900000..10900007 for up to eight
WAIT-learner turns per slot (stop a slot on actual terminal); all eight maps then join exclusions. This checks
integration/timing, not strength. No outcome-driven adjustment follows it.
Training parity uses all eight learner seats and four stages at first-rollout
turns0/15 (64 observations), checked before gradients. Additional fixed slots
are initial maps in environments0/1 at actual turns63/95, learner seat only,
all four stages (16 requested slots). Missing terminal-truncated slots stay
missing. Compare exact initial/final checkpoints to the combined panel.

Post-export: relevant Java/Python tests, 292 archived replay engine validation,
exact submit artifact and one strict eight-game zero-grace process panel.
Retain any failures without timing retries. Same v2 late-panel tool, using v2
weights as initial and v3 weights as final, reuses its four fixed reference maps
10100000..10100003 and requested turns0/25/50/75/99. Those are an exposed regression
panel, not fresh strength evidence. Its historical `experiment=spatial-ppo-v2`
metadata is a tool label; new report paths and exact payload hashes bind v3.
Verify both models' saved conditional Python/Java outputs and nominal/ample plans;
do not replace slots absent after actual terminal states.

## Fixed comparisons and decision

1. Progress diagnostic: final `spatial-ppo-v3` versus its exact starting policy
   `spatial-ppo-v2`, 100 games / 50 paired fresh maps, seeds11200000..11200049.
   Two workers, CG budgets. Report W/D/L, map-grouped uncertainty, score gap,
   errors and timing. No promotion decision or causal pool-effect claim.
2. Independent practical-v4 versus selected frozen v09, base seed11300000,
   two workers, 300/+200/+500 and mandatory futility stops below50% at300/500.
   Final local pass only at >=55% over all1,000. Exclude all prior, training,
   smoke/panel and progress-comparison maps. No concurrent CPU-heavy jobs.

Run the v09 stage regardless of diagnostic outcome when execution is valid.
No automatic promotion, upload, boss source change or push. A local pass would
still require the project's matched40 boss stage and explicit promotion workflow.
Record the actual result, update BOTS/DEVLOG/PLAN, archive compact reproducible
evidence and commit the completed experiment locally, including a rejection.

## Completed results

The one fixed run completed all 128 iterations, **16,384 environment turns and
learner samples**, 65,536 legal conditional decisions and 1,024 Adam steps.
There were 183 completed games (15,974 turns) and eight unfinished trajectories
(410 turns), on 191 unique fresh maps. Each of eight fixed slots contributed 2,048
turns; each opponent contributed 4,096 completed native decisions, with zero failed
calls. No opponent samples entered PPO. Independent accounting verified rewards
against score differences plus exactly one terminal bonus (max error 2.85e-13),
all seat/opponent assignments and map exclusions.

Training took 1,260.24 s: collection 1,181.82 s and learning 67.94 s; native opponent
time 349.07 s is already included in collection, not additional. Python peak working
set 492,666,880 bytes. Checkpoints 0/64/128 contain Adam steps 0/512/1,024; only final128
was evaluated. The initial payload is exact v2 with fresh optimizer/RNG, and final
checkpoint, generated Java and Base64 payload agree:
`da267469ea5ca284191a9a4901ea37b81e860d6615622cc68eb2952d8f5f98bd`.
Weight L2 change 4.73814. Training outcomes use a changing stochastic learner and
ample-budget fixed opponents; they are not the final strength measurement.

Verification passed 531 scoped Java tests before training, eight targeted bridge
tests after the transport fix, five final-artifact Java tests, 21 Python tests
before and after training, and seven tests in the separately preserved additional
regression file after training. The six known pre-existing untracked Pduhard tests
remain excluded; this is not an unqualified full-suite pass. Engine validation
passed 292/292 archived replays before and after export. No core changes.

Training parity covered 64 initial observations and 72 combined observations for
each saved model; eight of 16 requested late training slots were absent after actual
terminals. Final max absolute error 6.104e-5, zero greedy mismatches. The separate
exposed late-position regression captured 16/20 requested positions, 128 conditional
observations per model, zero greedy mismatches, final max error 4.617e-5. Both
models produced 32/32 identical traced/ample/nominal plans, without interruption,
errors or skipped actions. Final nominal mean 8.622 ms, p95 14.953 ms, max 18.153 ms.
This is finite local timing evidence, not a CG-host guarantee.

Exact submission: 84,315 characters, SHA256
`2e873833da40b7b73c585016fc8fe0039b77d256fe60445f60eaf1b4c967dfc7`,
build identity `b8fc8389e90931095a13d707a9ec4fc78bbcbc06e84859511d64e89bb479a210`.
The strict zero-grace process panel was 8/8 clean, with max first response 579.756 ms
and max later response 29.237 ms. Previous spatial submissions and selected v09
preserve their exact prior identities.

Final versus exact v2: **6W/94L/0D (6%)**, 3/50 wins from each seat, mean score
difference -404.05. Fifty fresh paired maps, two workers, CG budgets, zero game
errors and zero slow calls for either bot, 101.023 s. Map-grouped Hoeffding 95%
interval [0%,25.21%] is diagnostic. This is a clear regression against the starting
policy in this comparison; it does not isolate opponent diversity from the
effect of additional learning.

Independent practical-v4 versus selected v09: **0W/300L/0D**, mean score
difference -11,820.94, zero game errors, slow calls 0/1, 484.535 s. The 150 paired fresh
maps use seeds 11300000..11300149. The mandatory verdict is `STOP_FUTILITY_300`;
blocks 2/3 and boss games were not run. All 200 evaluation maps are distinct and
disjoint from 20,814 prior/development exclusions; the v09 index also excludes the
50 progress maps (20,864 total). All eight archived process maps and the four
late-panel maps were already excluded. Do not compare score gaps across different
versions' unmatched historical map sets as a controlled improvement estimate.

## Retained technical incidents and decision

Before any training, the new Java wrapper omitted null weight hashes for v01/v09
on its JSON wire while Python expected the field. Enabled null serialization in
the new wrapper and added an exact-wire regression; eight bridge tests and the
single declared 64-turn WAIT smoke passed. Old bridge/runtime files were unchanged.

A coordination error added one test method 15 seconds after the 573-file training
freeze. This test module is never imported by the trainer, and no tests ran during
training. The expanded test file was retained separately, and the original six-test
source restored byte-for-byte; all 573 frozen files then matched. Neither production
code, optimizer, model, original freeze/config nor the training run was changed or
restarted. The preserved seven-test copy passed after training. The incident is
recorded explicitly; an end-of-run hash match alone does not claim that no transient
test-file edit occurred. The first post-training Java command also failed before
tasks ran because `--tests` followed `submit`; the original log is retained, and the
corrected command completed the same declared checks. No strength/timing retries.

All 641 evaluation-frozen files matched after both comparisons. **Reject v3 for
strength**, retain the fixed-pool collector and complete evidence, keep v09 selected.
No extra training, checkpoint selection, promotion, upload or push. The reason for
the regression is not established by these checks. Distillation and value-guided
search remain separate future stages; this run does not establish the competitive
strength of the larger published top-3 system.

Evidence: [summary](evidence/spatial-ppo-v3.json),
[training record](evidence/spatial-ppo-v3-training.json),
[archive index](evidence/spatial-ppo-v3-artifacts.json),
[reproducible archive](evidence/spatial-ppo-v3-artifacts.zip).
The archive retains checkpoints, exact artifacts, raw outcomes, source snapshots,
the prospective protocol, test logs/XML, incidents and independent audits.
