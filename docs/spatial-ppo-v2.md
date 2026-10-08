# spatial-ppo-v2 — complete-game self-play learning

Status: completed 2026-10-09 (Europe/Bucharest), rejected for strength.
Final versus exact initializer: **96W/4L/0D (96%)**. Final versus selected v09:
**0W/300L/0D**, mandatory `STOP_FUTILITY_300`. Selected v09 remains unchanged.
The protocol below was frozen on 2026-10-08, before gradients; its original
bytes are retained as `protocol-before-results.md` in the evidence archive.
This experiment changed training exposure, retaining v1's model, observation,
PPO objective and Java execution.

## Hypothesis and fixed training

Learning from complete self-play games can improve the compact spatial policy
relative to its exact untrained initializer. A successful technical pipeline or
movement in loss is not itself evidence of stronger play.

- Same 3,439-weight network, spatial observations and simultaneous shared-core
  transition. Preserve SpatialBot/Net/Observation/Weights, the shared bridge and
  all four v1 Python implementation files; extend only variant/tooling integration.
- Fresh initialization with seed 20261008, exactly matching v1's initial payload;
  do not resume its four-update model or select between checkpoints.
- Exactly 64 iterations x 8 environments x 16 real turns = 8,192 environment
  turns / 16,384 seat samples. Environments continue across rollout boundaries
  and reset to fresh maps only on actual terminal states.
- Initial map seeds 10000000..10000007, then sequential new seeds per reset.
  Record each map hash, terminal score/outcome, segment length and unfinished
  final state. All training maps join the later evaluation exclusion index.
- Same v1 PPO: two epochs, minibatch 32, Adam learning rate 0.0003, clip 0.2,
  entropy coefficient 0.01, value coefficient 0.5, gradient cap 0.5,
  gamma 0.99, GAE lambda 0.95. Reward is per-turn income gap plus terminal
  +/-30, divided by 100. Discount once per real turn; optimize joint plan ratios.
- CPU PyTorch 2.8.0+cpu, NumPy 2.3.5, two compute threads. Exactly 1,024 optimizer
  steps if the full run completes. No opponent pool, teacher, search or tuning.
- Save initial, midpoint (iteration 32) and final checkpoints with optimizer,
  configuration and RNG. They are inspection/reproduction artifacts, not exact
  environment-resume checkpoints. Compact metrics for all iterations; final
  policy is always iteration 64. No extension or retry to improve the outcome.

Use a new runner importing the frozen v1 collector/model/PPO; instrument timing
without altering its math or RNG. Attribute measured model forward time and
transport encoding/decoding/wait separately where possible. Transport wait
includes Java computation and OS scheduling; do not label it pure IPC cost.
Refuse existing run outputs, non-finite optimization or numerical parity failure.
Preserve failed attempts rather than replacing their evidence.

## Verification and late positions

Before gradients, test new wiring and collector terminal/reset accounting,
record source/runtime/compiled-engine identities and this protocol. After export,
repeat relevant Java/Python checks and cgValidate; no core edits are planned.
Keep v1/default artifact hashes unchanged. V2 has separate generated weights,
full frozen parameters and a separate packaged Player.

Training parity retains the first-rollout panel. Additional training observations
may be captured only at fixed initial-map turns 63 and 95 for environment slots
0 and 1, both seats and all four decision stages; missing slots stay missing.

Separate fixed late panel: four maps seeded 10100000..10100003, one v09-v09
trajectory per map, serial 60000-ms decision allowance with completion evidence.
Capture actual turns 0, 25, 50, 75 and 99 if still live. Retain exact trajectories
and input states; never replace maps or terminal-truncated slots. On the saved
states compare initial/final Python and Java raw outputs (tolerance 1e-4) and
masked greedy actions, using each policy's own conditional prefixes. Separately
compare final Java ample and nominal 36-ms plans and timing. This is a diagnostic,
not proof that every future position meets the time budget.

Reference trajectories have no warmup and must complete ordinary search or the
intentional protected-opening decision within the 60000-ms allowance. Stop after
capturing turn 99, without forcing that last partial trajectory to terminate.
Candidate timing uses 100-ms warmup per map/seat/model/phase; complete the ample
phase before the nominal phase for both initial and final weights. Export all
four conditional stages, including forced STOP, and check the traced plan against
the ample production bot's actual action.

Build with submitVersion=spatial-ppo-v2 into build/submit-spatial-v2. Strict
exact-artifact process panel: eight games using the existing replay-map runner's
fixed order and both seats, zero grace, separate write-once report. Preserve
timing failures; do not rerun the same candidate until it passes.

## Two fixed comparisons

1. Learning diagnostic: final `spatial-ppo-v2` versus exact initializer
   `spatial-ppo-v2-init`, 100 games / 50 fresh paired maps, seeds 10200000..10200049,
   CG nominal budgets and two workers. Report wins/draws/losses, paired-map
   uncertainty, score difference, errors and timing. Descriptive only: no
   promotion or overall-strength claim, no outcome-driven checkpoint selection.
2. Selected-control strength: practical-v4 versus frozen v09, base seed 10300000,
   two workers, fixed 300/+200/+500 stages and standard mandatory futility stops.
   Exclude prior benchmark, training, late-panel and initializer-diagnostic maps.
   No concurrent CPU-heavy work during timing tests or either comparison.

Run the v09 comparison independently of whether the initializer diagnostic looks
favorable, provided engine and execution are valid. Any technical failure must
be reported explicitly; a strength result cannot establish technical readiness.
No automatic promotion, arena upload or boss source write. If the local gate
passes, the unchanged project's boss requirement still remains before promotion.

## Interpretation and retention

This is 32 times v1's exposure, still a bounded experiment rather than a promise
of convergence. Improvement over the initializer would show local learning
progress, not strength against v09 or the arena. Terminal/reset coverage and
late-position checks close specific gaps from v1. Keep weights/checkpoints,
map identities, exact submission, test logs, comparison reports and hashes in a
compact archive; write actual results to BOTS/DEVLOG/PLAN and commit locally.

## Completed results — 2026-10-09

The single fixed run completed all 64 iterations: **8,192 environment turns,
16,384 seat samples and 1,024 optimizer steps**. All 65,536 conditional decisions
were legal. There were 92 completed games covering 7,902 turns, followed by eight
unfinished episodes covering 290 turns. All eight environments advanced exactly
1,024 turns; the 92 actual terminal resets generated sequential maps, for 100
distinct training maps in total. No overlap with the 20,311 prior map hashes.

Training took 726.008 seconds, including 644.859 seconds collecting experience
and 71.324 seconds optimizing. Python peak working set was 566,030,336 bytes
(about 540 MiB). Collection includes model inference, serialization, Java work
and OS scheduling. Initial/midpoint/final checkpoints have 0/512/1,024 optimizer
steps and retain settings/RNG; live environment states are not included.
The final checkpoint was fixed in advance, without choosing from match results.

Final weight payload SHA256:
`b8cdd34b01aac15c025bd36d41290fb261946debe483b3af268117ed7d8eca1d`.
The exact initializer matches v1's initializer; all v1 source/weights and shared
rules remain unchanged. Weight L2 movement was 1.29804. Last-rollout critic MSE
changed from 0.781802 to 0.757391 on that same rollout; this is not a strength test.

### Verification

- Training parity: initial 64 observations and extended 88 observations for both
  initial/final weights pass. All masked greedy choices match Java; final extended
  maximum absolute numerical error is 5.07e-7.
- The independent late panel captured 16 of 20 requested positions. Three
  reference games ended before their remaining slots; no map or slot was replaced.
  Both policies pass 128 conditional observations, with zero greedy mismatches;
  final maximum absolute error is 8.92e-7. Both have 32/32 nominal plans identical
  to ample and traced plans, zero interruptions/errors/skips. Final nominal mean
  16.429 ms, p95 22.228 ms, max 22.752 ms. This is a finite local panel.
- Fifteen spatial Python tests pass. Initial scoped Java run: 519 tests, one
  failure in a new regression test that used a stale saved-default artifact hash.
  Isolated compilation of the previous commit proved old/new `current` merge
  output byte-identical; the expectation was corrected. The first targeted retry
  raced that correction and retained the same failure. Corrected targeted five
  tests and final trained-artifact five tests pass. Six tests from the unrelated
  pre-existing untracked beam prototype were excluded. Original XML/logs are saved;
  no claim is made that the full unscoped suite passed.
- Shared engine: 292/292 archived CG replays reproduce exactly, including the
  final post-export validation. No core changes.
- Exact submission: 84,315 characters, source SHA256
  `a3d0fdb7e587478fc4b6f63cd64bb6f607c34b64384cd41cc091bcec375107a1`,
  build identity `da1e22650d73cddb469f8f0dfd74bdecf4520943c495977e4c0e3f9f361704ae`.
  Strict eight-game process panel, zero grace: **0/8 problem games**, first response
  max 669.1594 ms, later max 26.6249 ms. This does not guarantee all future deadlines.

### Fixed comparisons

Learning diagnostic, seeds 10200000..10200049, 50 paired maps / 100 games:
**96W/4L/0D**, 48/50 wins from each seat, mean score difference **+731.98**.
Map-grouped Hoeffding 95% interval: [76.79%, 100%], descriptive only. Zero errors;
slow calls 2 candidate / 0 initializer. Two workers, CG timing, 109.643 seconds.
This establishes improvement against this exact initializer on fresh maps.

Practical-v4 versus v09, seeds 10300000..10300149, 150 paired maps / 300 games:
**0W/300L/0D**, mean score difference **-11,081.06**, zero errors;
slow calls 3 candidate / 6 control. Two workers, CG timing, 396.546 seconds.
Mandatory `STOP_FUTILITY_300`; no further blocks, boss games or retries.
The two comparisons used 200 distinct maps, disjoint from all 20,415 prior plus
training/panel maps. The v09 exclusion index also contains all 50 initializer maps.

Before the initializer's first game, its first launch failed because recursively
summed historical exclusion counts overflowed Java's integer field. No comparison
games ran in that failed launch. Normalization preserved the exact 20,415-key
map union and changed counts to presence markers of 1. The v4 index builder now
does this directly and rejects invalid counts; two regression tests pass. A first
test-harness attempt failed on temporary-directory permissions before assertions;
the corrected harness uses an isolated workspace directory. Model, parameters,
seeds and decision rules did not change. Original index, failed logs and original
runner are retained; frozen v2/v3 gate tools and historical results are untouched.

All **631 frozen files** (625 original plus six exclusion-tool amendment files)
matched after both comparisons. The amendment was frozen before any comparison
game. The saved default Player remains unchanged; integration also preserves
previous-commit `current`, frozen v09, spatial-v1 and neural-v2 merged bytes.

### Decision and retained evidence

Retain the complete-game training pipeline and the positive initializer result;
reject this candidate for competitive strength. The training budget is still
small, and this test neither reproduces the top player's bot nor rules out the
larger self-play approach. Opponent pools, teacher training and search remain
separate future experiments. No automatic promotion, upload or additional training.

[Summary](evidence/spatial-ppo-v2.json),
[full training metrics](evidence/spatial-ppo-v2-training.json),
[artifact index](evidence/spatial-ppo-v2-artifacts.json) and
[archive](evidence/spatial-ppo-v2-artifacts.zip) retain checkpoints, original
protocol, source snapshots, test logs/XML, exact Player, saved panels, raw paired
outcomes, exclusion identities and independent audits. Completed work is recorded
in BOTS/DEVLOG/PLAN and committed locally.
