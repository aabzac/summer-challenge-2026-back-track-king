# spatial-ppo-v1 — first complete spatial PPO pipeline

Status: completed technical stage, strength rejected, 2026-10-08. Authorized first stage of the Pduhard-inspired
plan. This is a technical and bounded learning experiment, not a top-three bot
reproduction or a strategy promotion. Existing untracked beam/linear-probe work
is outside this change. Selected v09 remains the control.

## Hypothesis and fixed scope

A compact spatial policy/value can learn through the exact shared Java engine,
export to Java, and execute legal complete actions within the submission budget.
No search, distillation, teacher imitation, reward shaping, or opponent-pool
curriculum in this first run. The model is a fresh convolutional network, not an
extension of the earlier linear PPO probe.

## Interface contract (v1)

- Package `btk.bot.neural`: SpatialObservation, SpatialNet, SpatialWeights,
  SpatialBot. Training/bridge tooling in `btk.tools.spatial`; Python in
  `tools/spatial`. Production classes depend only on bot/core/JDK.
- Board W=30, H=20, S=600; NCHW flattened float32, 24 planes, zero padding.
  Tables: 12 towns x49 floats, townCells[12] padded board indices (-1 missing),
  regionIds[600] (-1 padding), scalars[6].
- Plan stages: three BUILD choices then one DISRUPT choice. BUILD indices 0..599
  are padded cells, 600=STOP. After STOP or no paint, later BUILD stages have
  only STOP legal. DISRUPT uses region ids 0..599, 600=no disruption.
  Selected own cells are a pending prefix, never applied to the real board.
  Both seats decide from the same pre-turn state; one core resolve follows.
- Network: stem Conv2d(24,8,3,padding=1)+ReLU; Linear(49,8) town encoding
  scattered onto real town cells; residual block Conv(8,8,3)+ReLU+Conv(8,8,3),
  added to input then ReLU; masked board mean -> Linear(8,8), broadcast/add then
  ReLU. Masked global mean after injection concatenated with six scalars forms
  z[14]. Linear(8,1) cell build logits; Linear(8,1) cell ink logits pooled by
  mean into regions; three Linear(14,1) heads for STOP, no-disrupt and value.
  Recompute all layers for each decision in this first teacher-style version.
- Weight order: stem.weight, stem.bias, town.weight, town.bias, res1.weight,
  res1.bias, res2.weight, res2.bias, global.weight, global.bias, build.weight,
  build.bias, ink.weight, ink.bias, stop.weight, stop.bias, noink.weight,
  noink.bias, value.weight, value.bias. PyTorch row-major flattened tensors;
  little-endian float32 Base64 payload (no quantization in v1).
- Java API: SpatialObservation constants W/H/S/PLANES/TOWNS/TOWN_FEATURES;
  public float[] planes,towns,scalars; int[] townCells,regionIds;
  boolean[] buildMask,disruptMask (601 each).
  constructor(GameState); prepare(GameState,int me); prefix(int[] paddedCells,
  int count,int paint,int stage,boolean stopped). prepare computes base state;
  prefix updates selected rails/scalars/masks without core transitions.
- Java net: SpatialNet(String base64); forward(SpatialObservation);
  public float[] buildLogits,disruptLogits (601); public float value.
  SpatialWeights.DATA is generated. SpatialBot(Params) is the frozen v1 policy.

## Verification and bounded run

Fixed before gradients: initialize seed 20261008; fresh maps from seed 9700000;
4 iterations x 4 environments x 16 real turns = 256 environment turns / 512
seat-turn samples (continuing unfinished games between rollouts). Each batch has
2 optimization epochs, minibatch32, Adam learning rate 0.0003, PPO clip0.2,
entropy coefficient0.01, value coefficient0.5, gradient norm cap0.5,
gamma0.99, GAE lambda0.95; reward divided by100. CPU PyTorch2.8.0, two compute
threads. This short run tests the pipeline, not convergence or learning strength;
no tuning, extra iterations or reruns to improve the outcome.

Independent final strength screen: practical-v4 versus v09, base seed9800000,
two workers, 300/+200/+500, stopped at the mandatory futility look if applicable.
Process panel: eight games on the existing replay maps, both seats in the runner's
fixed order, strict deadlines and zero grace, separate `build/submit-spatial-v1`.

Verify observation masks, simultaneous collisions, score-delta rewards,
terminal/reset boundaries, full-plan log probabilities and Python/Java numerical
and greedy-action parity. Rewards are income gap per completed turn plus +/-30
at actual terminal, globally scaled for optimization; gamma applies per turn.
PPO uses freshly collected complete-plan trajectories from the current policy.
Save compact initial/final checkpoints including optimizer/RNG/settings.

Run a bounded self-play training smoke test and report actual critic/policy
metrics without imposing a teacher-agreement target or declaring arena strength.
Measure frozen final Java inference and exact artifact process behavior. A
separately frozen practical-v4 local bench is the strength verdict if reached;
no arena upload or promotion is authorized by this technical stage.

## Completed run and verdict

The complete observation -> self-play -> PPO -> export -> Java action pipeline
works. The frozen candidate scored **0W/300L/0D against v09** and stopped at
`STOP_FUTILITY_300`. It is a retained technical prototype, not a competitive
replacement. Selected v09 and the saved default submission remain unchanged.
No boss games, arena upload, promotion, extra training or outcome-guided retuning.

The network has 3,439 trainable float32 weights. The one fixed training run took
16.60 seconds: 256 environment turns, 512 seat samples and 32 optimizer steps.
All 2,048 internal decisions were legal. The four environments reached turn 64
each; **zero training games finished**. Terminal bonuses and resets were tested,
but the trained policy never encountered an actual terminal in this run.
Weights changed (L2 distance 0.16576, maximum absolute change 0.008492); this alone
does not demonstrate useful learning. Last-rollout critic MSE decreased from
3.05554 to 3.04436, too little evidence to claim a useful value estimate.

Collection took 13.31 seconds and optimization 1.49 seconds. Collection includes
policy inference, serialization, transport and the Java environment; this timing
does not isolate which component limits throughput. Checkpoints preserve model,
optimizer, configuration and RNG, but not live environment state; v1 does not
support exact continuation from its final checkpoint.

Initial and final Python/Java comparisons both passed on the same 64 observations:
four maps, both seats, all four stages at turns 0 and 15. Final maximum absolute
error was 1.79e-7, and all masked greedy choices matched exactly. This panel does
not establish parity coverage of late dense boards.

## Checks and timing limits

- 510 Java tests passed with only the pre-existing untracked
  `PduhardBeamBotTest` excluded. Its six cases refer to an already-unregistered
  `pduhard-beam-v1`. The initial full run had nine failures: those six plus three
  parameter-count assertions updated for the new spatial-only field. Historical
  parameter/artifact identities and the new compiled submission parity passed.
  The full workspace suite is therefore not claimed clean. Both test-run logs
  and the exclusion configuration are retained; a later targeted seven-test run
  replaced the original XML reports, so the archive does not contain 510 XML cases.
- Seven Python tests passed, including terminal/rollout GAE, complete-plan PPO
  ratios, forced actions, legal masks, finite optimization and export.
- Shared engine unchanged; **292/292 CG replays reproduced exactly**.
- Packaged source: **84,315/100,000 characters**. Strict exact-artifact process
  panel: **8/8 without errors, timeouts or nominal deadline overruns**, zero grace.
  Maximum first response 983.8379 ms, later 36.5099 ms. The first-response margin to
  1000 ms is small; these local results are not a CG-host timing guarantee.
- Separate serial profile: 128 seat samples on four maps / 64 early positions,
  125/128 nominal/ample plans equal. Three nominal interruptions changed the
  disruption decision; all 384 paint units were retained, with zero uninterrupted
  mismatches or skipped actions. Nominal mean 18.985 ms, p95 32.021 ms, max 38.823 ms;
  one call exceeded the local 36 ms target. Preparation mean 0.412 ms; one network
  forward mean 5.003 ms. Later dense boards remain outside this diagnostic panel.

The final payload SHA256 is
`bdac3d79ac5965c08f04a98b4e59e07ec19760f81b917ca2a941027ec0b32a69`.
The tested Player source SHA256 is
`d9507cb3e0d68de4f526cfa9da06a2b1c909182e9992f4b7a3bb6cab3d24d6d7`.

## Fixed strength screen and provenance

Practical-v4 block1 used seed 9800000, two workers, CG budgets and 150 fresh paired
maps. All 150 are distinct and disjoint from 20,161 prior maps, including the eight
training/profile maps. Result: 0/300 points (0%), mean score difference -16,393.08,
zero game errors. The in-process runner recorded 14 candidate and 10 control slow
moves; these are not exact-artifact process timeouts. Runtime 510.979 seconds.
The mandatory futility stop ended the evaluation; no later block or boss stage ran.

Two setup attempts failed before any games: the exclusion manifest needed
positive exposure counts, then the candidate needed its local catalog entry.
Both original logs are retained. The corrections changed no policy, weight,
map seeds or evaluation recipe. The catalog-only source/resource amendment was
recorded before the successful run. Post-run verification matched all 432 frozen
files against the amended manifest and matched effective parameters/artifact
identities. The wider manifest was captured after training; 21 trainer/engine
source hashes were separately recorded before gradients. The original protocol
is preserved as `build/spatial-ppo-v1/protocol-before-results.md` in the archive.

## Next bounded experiment

The next learning stage should collect complete games on a larger fresh map set,
retain terminal outcomes, and measure improvement over an untrained policy
before paying for another full strength screen. Keep this model and PPO recipe
fixed initially so additional learning can be distinguished from an architecture
change. Profile collection before selecting a large training budget. Add a
late-game timing/parity panel before treating the deployment path as robust.
Opponent pools, distillation and value-guided search remain subsequent stages.
No such new run is part of this completed experiment.

Reproduction commands and checkpoint limitations: [trainer README](../tools/spatial/README.md).
Results: [summary](evidence/spatial-ppo-v1.json),
[training metrics](evidence/spatial-ppo-v1-training.json),
[retained artifacts](evidence/spatial-ppo-v1-artifacts.zip) and
[archive index](evidence/spatial-ppo-v1-artifacts.json).
