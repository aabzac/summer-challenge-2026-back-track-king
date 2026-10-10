# spatial-ppo-v10 — fixed full-corpus continuation from V4

Predeclared 2026-10-11 (Europe/Bucharest), from revision `43a02f1`, after the user
authorized the next step. The small-set probe demonstrated fitting ability but
worsened agreement elsewhere. Test whether additional optimization on the full
original distribution improves imitation and actual play. This is supervised
imitation within original roadmap stage 3, not PPO or a new architecture.
Selected v09 remains unchanged; promotion, arena upload and push are not authorized.

## Fixed continuation

- Start from original `build/spatial-ppo-v4/final.pt`, epoch 16, Adam step 4,560.
  Checkpoint SHA-256:
  `1720eefba47171c0d1fef201c3eccd13c0b74dc906483dc8b9f42bac136733f2`;
  payload: `89de11f4239844c55c6e61c987ee2443fd5d79a1c71af782d2686e836ec6f3ef`.
  Never initialize from the memorized fit-probe or V9 weights.
- Restore the original Adam state and Python/NumPy/Torch RNG after constructing
  the model and optimizer. Do not reset Adam, reseed or call `fresh_student`.
  Verify parameter ordering, finite moments, saved step and RNG state. Model
  construction and diagnostics must not perturb the restored shuffle stream.
- Use unchanged `model.SpatialNet`, 3,439 floats; train the same 3,424 shared/policy
  coefficients and freeze all 15 value-head coefficients before creating Adam.
  Shared features change: the value output remains uncalibrated and unused.
- Keep original observations, masks, ordered hard labels and mean masked policy
  cross entropy, with no PPO, entropy, value, outcome or remaining-set loss.
  Reuse exact original corpus/cache: 48 TRAIN games / 24 maps / 18,192 rows;
  12 validation games / six maps / 4,448 rows. Preserve every example, including
  detected label conflicts, and the original map split. No new teacher decisions.
- Exactly **64 additional epochs**, total epochs **17 through 80**. Batch 64,
  shuffled once per epoch with `torch.randperm`; retain the 16-row final batch
  and its original per-batch mean loss. Each epoch has 285 optimizer steps.
  Additional **18,240 steps / 1,164,288 row exposures**, ending at Adam step
  **22,800**, with total historical-plus-new exposures 1,455,360.
- Adam lr 0.001, betas (0.9, 0.999), epsilon 1e-8, weight decay 0, gradient
  norm cap 1.0. CPU PyTorch 2.8.0+cpu / NumPy 2.3.5, two compute workers and one
  interop worker, deterministic algorithms, same float32 math as V4.
- Keep atomic checkpoints at total epochs **16 / 48 / 80**, with model, Adam,
  all RNG and settings; expected Adam steps 4,560 / 13,680 / 22,800. Save the
  final checkpoint and standalone payload immediately after the fixed final
  updates, before post-training checks. Record technical failures and retain
  the endpoint; do not restart gradients to repair a later reporting failure.
- Only final epoch 80 is exported to generated `SpatialV10Weights.java` and
  evaluated as a candidate. A generated exact-V4 bootstrap may compile the new
  integration before training; record its identity separately. No early stop,
  extension, alternate seed, tuning, model selection or extra training arm.

## Measurements and verification

Report all-row cross entropy, all/forced/unforced, BUILD/DISRUPT and per-stage
agreement plus teacher-prefix all-four agreement at epochs 16/24/32/40/48/56/
64/72/80 on the complete train and validation splits. Also retain per-epoch
optimization losses, gradients, timing and step counts. Evaluation uses no RNG;
verify that it preserves all three saved streams. Previously exposed validation
is development evidence, not fresh confirmation. These counts do not substitute
for actual student rollouts or constitute an agreement-based strength gate.

Before training: test exact next-update equivalence for restored model/Adam/RNG,
frozen value head, tail/count handling, invalid checkpoint rejection and isolated
candidate packaging/configuration. Run relevant existing V4/math/integration tests
and `cgValidate`; preserve unrelated untracked experiments. No core or historical
spatial source, weights, training tools or old submission artifact may change.
Freeze prospective protocol, sources/classes, effective parameters, original
checkpoint and corpus/cache before any real model scoring or gradients.

Initial/final Python–Java parity uses the first 24 conditional training rows,
the unchanged `SpatialBridge`, absolute tolerance 1e-4 and exact masked-greedy
agreement. After export, replay the immutable saved V4 late-panel trajectories
through core on maps 10100000..10100003, requested turns 0/25/50/75/99. Keep the
16 existing live positions and four terminal omissions; generate no reference
decisions. On initial/final payloads check all 128 conditional rows per model and
measure actual own-prefix plans, serial ample 60,000-ms and nominal 36-ms phases,
100-ms warmup per model/map/seat/phase. Preserve cutoffs, errors and outliers.

Build `submitVersion=spatial-ppo-v10` in a separate output directory, compile and
check <=100,000 characters. Run exactly eight serial strict zero-grace process
games versus v09 on the same first eight saved CG replay inputs as V4; freeze
their identities and order beforehand. No timed retry to obtain a pass. No
CPU-heavy work concurrently with timing/process/strength checks. A failed
readiness check stays a failure; distinguish deployment readiness from a valid
in-process diagnostic. Invalid artifacts must not enter strength evaluation.

## Fixed game comparisons

1. Progress diagnostic: final V10 versus frozen **spatial-ppo-v4**, 100 games on
   50 fresh paired maps, seeds **24000000..24000049**, two workers, CG budgets.
   The pool uses the supported `confirmation` metadata label but is only a
   progress diagnostic, with no promotion threshold.
2. Practical-v4 versus selected **v09**, base seed **24100000**, two workers and
   CG budgets: one pooled 1,000-game comparison in blocks 300/+200/+500, offsets
   0/+100000/+200000. Stop strictly below 50% at cumulative 300 or 500; survivors
   require >=55% over all 1,000, no early pass. Run regardless of imitation or
   progress scores when execution is valid; no strategy retries or tuning.

Exclude all prior/development, teacher/cache, saved-panel, process and earlier
comparison map hashes before each relevant batch; record immutable indices and
verify pairwise disjointness. Report errors, timing, W/D/L and mean score gap.
Local failure skips the boss. A local pass would still require the separate
matched 40-game cached-boss check and applicable authorization before promotion;
this experiment does not authorize an arena upload. Do not claim the full gate
passed based only on local games or silently mark original stage 3 complete.

Archive prospective protocol/freezes, sources/tests, configuration, checkpoints,
curves/parity, exact submission/process inputs, raw panels, game reports,
commands/logs/errors and final identity checks. The unchanged V4 corpus archive
is a hashed dependency. Update README/PLAN/BOTS/DEVLOG and commit the completed
experiment locally, including rejection or an inconclusive technical result.


## Completed results (2026-10-11)

The one fixed run completed **64 additional epochs / 18,240 additional Adam
steps**, continuing original V4 epoch 16/step 4,560 through epoch 80/step 22,800.
It reused all 18,192 TRAIN rows and 4,448 validation rows with the original
model, ordered masked loss, Adam moments and restored Python/NumPy/Torch RNG.
There were **1,164,288 additional row exposures**, 1,455,360 including original V4.
Training took 1424.602 s; Python peak working set was
1,756,749,824 bytes. No new teacher decisions,
alternate checkpoint selection, capacity change, search or value fitting.

First and last fixed curve points:

- Epoch 16 TRAIN: unforced 6,251/14,428 (43.325%), all-row cross entropy 1.741320, teacher-prefix all-four 599/4,548 (13.171%).
- Epoch 80 TRAIN: unforced 7,337/14,428 (50.853%), all-row cross entropy 1.484091, teacher-prefix all-four 829/4,548 (18.228%).
- Epoch 16 validation: unforced 1,471/3,720 (39.543%), all-row cross entropy 2.033353, teacher-prefix all-four 77/1,112 (6.924%).
- Epoch 80 validation: unforced 1,361/3,720 (36.586%), all-row cross entropy 2.051781, teacher-prefix all-four 34/1,112 (3.058%).

Thus TRAIN unforced agreement changes 6,251→7,337/14,428 (43.325%→50.853%), and teacher-prefix full
plans 599→829/4,548. Validation unforced changes 1,471→1,361/3,720 (39.543%→36.586%), full plans
77→34/1,112. These are predictions under the recorded teacher prefixes, not
whole plans generated under the student's own choices. Validation was previously
exposed development data; agreement and loss are diagnostics, with no strength
threshold or fresh-generalization claim.

All nine prospective curve points are retained, without choosing an endpoint:

- Epoch 16: TRAIN unforced 6,251/14,428 (43.325%), all-row cross entropy 1.741320, teacher-prefix all-four 599/4,548 (13.171%); validation unforced 1,471/3,720 (39.543%), all-row cross entropy 2.033353, teacher-prefix all-four 77/1,112 (6.924%).
- Epoch 24: TRAIN unforced 6,590/14,428 (45.675%), all-row cross entropy 1.658618, teacher-prefix all-four 654/4,548 (14.380%); validation unforced 1,458/3,720 (39.194%), all-row cross entropy 2.001099, teacher-prefix all-four 50/1,112 (4.496%).
- Epoch 32: TRAIN unforced 6,858/14,428 (47.533%), all-row cross entropy 1.608926, teacher-prefix all-four 741/4,548 (16.293%); validation unforced 1,450/3,720 (38.978%), all-row cross entropy 1.994967, teacher-prefix all-four 64/1,112 (5.755%).
- Epoch 40: TRAIN unforced 6,908/14,428 (47.879%), all-row cross entropy 1.597238, teacher-prefix all-four 736/4,548 (16.183%); validation unforced 1,411/3,720 (37.930%), all-row cross entropy 2.017488, teacher-prefix all-four 61/1,112 (5.486%).
- Epoch 48: TRAIN unforced 7,066/14,428 (48.974%), all-row cross entropy 1.552974, teacher-prefix all-four 790/4,548 (17.370%); validation unforced 1,438/3,720 (38.656%), all-row cross entropy 2.008892, teacher-prefix all-four 45/1,112 (4.047%).
- Epoch 56: TRAIN unforced 7,113/14,428 (49.300%), all-row cross entropy 1.533547, teacher-prefix all-four 776/4,548 (17.062%); validation unforced 1,439/3,720 (38.683%), all-row cross entropy 1.976316, teacher-prefix all-four 50/1,112 (4.496%).
- Epoch 64: TRAIN unforced 7,079/14,428 (49.064%), all-row cross entropy 1.523433, teacher-prefix all-four 776/4,548 (17.062%); validation unforced 1,392/3,720 (37.419%), all-row cross entropy 2.035975, teacher-prefix all-four 38/1,112 (3.417%).
- Epoch 72: TRAIN unforced 7,182/14,428 (49.778%), all-row cross entropy 1.500690, teacher-prefix all-four 802/4,548 (17.634%); validation unforced 1,401/3,720 (37.661%), all-row cross entropy 2.021440, teacher-prefix all-four 44/1,112 (3.957%).
- Epoch 80: TRAIN unforced 7,337/14,428 (50.853%), all-row cross entropy 1.484091, teacher-prefix all-four 829/4,548 (18.228%); validation unforced 1,361/3,720 (36.586%), all-row cross entropy 2.051781, teacher-prefix all-four 34/1,112 (3.058%).

### Actual play and readiness

Progress against original **spatial-ppo-v4**: **66W/34L/0D / 100** on 50 fresh
paired maps, score 66.000%, mean gap +733.90,
errors [0, 0]. The pool's `confirmation`
metadata is a runner label; this comparison has no promotion threshold.
Native slow calls A/B: 2/0; maximum move time
A/B: 52/37 ms. Here A is V10 and B is V4;
in the practical blocks below B is v09. Native bench timing outliers are reported
separately from the strict external-process readiness check.

Practical-v4 against selected **v09**: **0W/300L/0D / 300**, pooled score
0.000%, mean gap -9815.90;
**STOP_FUTILITY_300**, errors [0, 0].

- Block 1: **0W/300L/0D / 300**, score 0.000%, mean score gap -9815.90, errors [0, 0]; native slow calls A/B 2/0, maximum move time A/B 68/50 ms.

Boss skipped after local failure (`SKIPPED_LOCAL_FAILURE`); no later or replacement local block was played after the stopping decision.

Verification:

- Preflight: 43 Java and 25 Python tests pass,
  including 7 new Java / 7 new Python tests;
  post-export targeted Java 7 pass.
  Shared core reproduces 292/292 saved CG replays exactly.
- Initial/final training parity: 24 conditional rows each. Saved V4 late panel:
  16 live positions of 20 requested, four genuine terminal omissions, no new
  reference decisions; 128 conditional rows per model match Python/Java within
  1e-4 (largest recorded error 1.71661377e-05) with equal masked choices.
- Actual own-prefix timing uses serial ample/nominal phases and 100-ms warmup.
  Final nominal plans match ample 32/32;
  cutoffs 0, calls above 36 ms 0.
  Nominal mean/p95/max 22.393/26.706/26.871 ms.
  This saved-state profile is not an arena deadline guarantee.
- Exact `Player.java`: 84,318 characters, 84,318 bytes.
  Eight strict zero-grace process games preserve the frozen V4 file order,
  seats and run seeds: 0 problem games, 0 timeouts,
  0 nominal overruns; maximum first/later response
  739.759/37.024 ms.
- 200 actually evaluated
  comparison maps are pairwise disjoint and excluded from
  22,101 prior/development maps, including
  corpus, saved late-panel and process identities. All actual bench exclusions
  and predeclared seed-to-map identities were checked.
- Final identity verification preserves 812 frozen files and
  910 historical files. Original V1–V9/core/data remain intact.

The fixed local strength comparison rejected this candidate. Selected **v09 remains unchanged**; no promotion, arena upload or push.
This endpoint's measured changes in fitting and play do not by themselves
identify the remaining cause of the learning gap. All 15 value-head coefficients
are unchanged, while shared features moved: future value remains uncalibrated
and unused. Original roadmap **stage 3 remains open**. No next architecture,
objective or search experiment is selected by this result.

### Identities and reproduction

- Original V4 checkpoint: `1720eefba47171c0d1fef201c3eccd13c0b74dc906483dc8b9f42bac136733f2`.
- Initial payload: `89de11f4239844c55c6e61c987ee2443fd5d79a1c71af782d2686e836ec6f3ef`.
- Final payload: `169a11ae5f0c7a0f8756cd087c92e0770b704ca36287180c3639fe509b3af471`.
- Submission source SHA-256: `6011ed30a37b5b015180be78477a63f896438ece6fe7fc21a27b33c2b47ad9b0`.
- Compiled submission classes SHA-256: `3dae5d09c14c7f6a2f74be7dd9248064bdc8b4d247a98c046879d4a6d33bc70a`.
- Summary SHA-256 at documentation update: `c2561d9d79fbbd76aed8f83b277412e2218446faa2fcae2e05087066c2f33912`.
- Prospective protocol bytes: `fdb22654085ea42a3a0101a7a11a1d934c5bba64d94e6cf739e685e00a158086`;
  the exact pre-results file is retained as `protocol-before-results.md` in the archive.

- `initial.pt`: reported epoch 16, Adam step 4,560; SHA-256 `76f632769a18f06634513d15449f69efbf961fa4e58c4cfc893ae8f8ad56a434`.
- `midpoint.pt`: reported epoch 48, Adam step 13,680; SHA-256 `f209b44b2d10efa0acc64190d8eb3a8159d0062a386517c8048cca2f55cc1d37`.
- `final.pt`: reported epoch 80, Adam step 22,800; SHA-256 `8d76af2cef2f305a3d12517ef0b7a8624041173cc1523237f4fb42f39f73be93`.

Checkpoint epoch/step metadata was validated by the fixed trainer; the summary
binds their bytes without independently unpickling them. The exact corpus is a
hashed dependency of the V4 archive, not a new collection.

Reproduction starts from the recorded original V4 checkpoint and corpus/cache.
`tools/spatial/v10_distill.py bootstrap` validates the original state and produces
only the exact-V4 compile placeholder. The one `train` invocation restores the
model/Adam/RNG and executes the fixed 64 added epochs; it refuses occupied output
paths. Build only the isolated `submitVersion=spatial-ppo-v10` artifact after
final export. `SpatialV10Panel` takes initial payload, final payload, a fresh
output directory and the immutable saved V4 panel; `compare_v2_panel.py` checks
each model. Replay the exact process inputs before the fixed progress/practical
comparisons. These are descriptions of the preserved run, not authorization to
repeat training or games.

Exact command arrays, working directories, exit statuses and logs are retained
under `build/analysis/spatial-ppo-v10/`: `bootstrap-command.json`, `engine-preflight-command.json`, `java-preflight-command.json`, `late-panel-command.json`, `late-parity-final-command.json`, `late-parity-initial-command.json`, `map-preflight-command.json`, `postexport-tests-command.json`, `process-command.json`, `progress-command.json`, `python-preflight-command.json`, `strength-command.json`, `submit-command.json`, `training-command.json`.
The artifact archive retains those paths, sources, settings, checkpoints,
curves, raw reports, prospective protocol and identity freezes. After all checks,
`python build/spatial_v10_results.py` creates the write-once summary;
`python build/spatial_v10_document.py` appends these results. Both refuse to
silently replace completed outputs.

[Summary](evidence/spatial-ppo-v10.json),
[training evidence](evidence/spatial-ppo-v10-training.json),
[artifact manifest](evidence/spatial-ppo-v10-artifacts.json),
[artifact archive](evidence/spatial-ppo-v10-artifacts.zip).
