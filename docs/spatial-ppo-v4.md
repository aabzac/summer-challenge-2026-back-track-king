# spatial-ppo-v4 — distillation from frozen v09

Status: completed and rejected for strength, 2026-10-10 (Europe/Bucharest).
Final versus v2: **98W/2L/0D / 100**. Versus selected v09: **0W/300L/0D / 300**, `STOP_FUTILITY_300`. Candidate rejected for strength; boss skipped, selected v09 unchanged.
The prospective protocol below was frozen before teacher collection and gradients;
its original bytes and hashes are retained in the evidence archive.
The user authorized this next stage. The historical
spatial-ppo-v4 name identifies the version; this experiment uses supervised
hard-action distillation, not PPO. Selected v09 and all prior spatial versions
remain unchanged.

## Hypothesis and fixed scope

Learning v09's complete decisions may improve the compact spatial policy beyond
v2's self-play behavior. Warm-start the exact v2 final weights
`b8cdd34b01aac15c025bd36d41290fb261946debe483b3af268117ed7d8eca1d`, with fresh Adam
and Python/NumPy/Torch seed 20261010 after construction/loading. Keep the existing
3,439-weight architecture, observations, action masks and Java runtime. No online
rollouts from the student, DAgger, PPO, reward tuning, architecture changes or search.

The teacher is frozen v09 with its complete parameter snapshot, serial 60,000-ms
allowance, no warmup, and mandatory full-search completion or intentional opening.
The fixed opponent uses the same ample regime and must complete. This teacher
regime differs from the CG-budget evaluation. Both bots choose from separate copies
of the same unchanged pre-turn state; neither sees the other's selected plan.
The shared core owns all legality, action expansion and simultaneous resolution.

## Fixed data and training recipe

- Training: 24 unique maps, seeds 12000000..12000023. Opponent for map index i is
  [v01, spatial-ppo-v2, v09][i modulo 3]. Play each map twice with the teacher as
  P0/P1 and fresh bots/states: 48 complete games, eight maps per opponent.
- Validation: six different maps, seeds 12100000..12100005, the same opponent
  mapping and both teacher seats: 12 complete games, two maps per opponent.
  Split by map before any data, never by turn; all maps must be distinct and absent
  from the saved prior/development exclusions. No replacements after poor outcomes.
- Every actual pre-terminal teacher turn produces exactly four conditional records:
  up to three ordered placements, STOP for remaining construction slots, then one
  disruption-region ID or STOP. Java's existing SpatialObservation supplies the
  exact teacher-prefix features and masks; do not reorder construction, infer
  labels from outcomes, or recompute rules in Python. Require each target legal,
  no skipped actions, and the reconstructed full plan equal to the teacher's
  core-expanded plan. Save original actions, prefixes, map/turn/seat and outcomes.
- Store exact float32 observations in lossless compressed binary files, with a
  documented versioned schema, hashes and explicit complete/failure metadata.
  Python uses a disk-backed cache and minibatch copies; no float16 quantization.
  Partial or malformed datasets fail; never silently filter invalid labels.
- One fixed 16-epoch run, shuffled decision rows, minibatch 64 (retain last batch),
  Adam lr 0.001, gradient norm cap 1.0, CPU PyTorch 2.8.0+cpu/NumPy 2.3.5,
  two compute threads. Loss is mean masked cross-entropy on the active policy head;
  each real turn contributes three build records and one disrupt record. Singleton
  masks have zero loss/gradient. No entropy, value, PPO or outcome loss.
- Freeze the value-head parameters. Shared feature layers still change, so its
  resulting values are uncalibrated and are not evidence for value-guided search.
  Report this limitation; the production greedy policy does not use the value.
- Save initial, epoch-8 and final-16 model/optimizer/RNG checkpoints. Only final-16
  is exported/evaluated as a candidate. Never select a checkpoint from validation,
  extend epochs or retune. Report actual row counts and resulting optimizer steps.
- Measure initial/final cross-entropy, legal greedy teacher agreement, agreement
  excluding forced singleton choices, build/disrupt slices and exact full-plan
  agreement on both splits. Validation uses teacher prefixes and is diagnostic;
  closed-loop strength is measured separately on untouched maps.

## Verification and immutable evidence

Before collection: tests for conditional label conversion (cost, STOP, disruption,
ordering), simultaneous pre-state use, binary roundtrip, malformed data, map split,
masked loss and frozen value head; existing scoped tests and 292 archived engine
replays. Preserve the six pre-existing untracked Pduhard test exclusions explicitly.
Record all technical failures and fixes. Freeze source/runtime/protocol/parameters
after preflight and before the real teacher corpus; freeze corpus before gradients.

One declared no-gradient smoke uses seeds 11900000..11900002, opponent i modulo 3,
teacher seat i modulo 2, at most two real turns each, stopping on actual terminal.
It checks transport/labels only; all three maps join exclusions. No extra games
or outcome-driven adjustments after smoke. Synthetic unit fixtures are not strength
evidence. The first six teacher turns of the training corpus supply 24 conditional
observations for initial/final Python/Java parity (tolerance 1e-4 and equal greedy
choices), without filtering rows after inspecting differences.

After final export: targeted Java and Python tests, cgValidate, exact separate
submission and one strict eight-game zero-grace process check versus v09. Use the
unchanged v2 late-panel tool once with v2/v4 payloads on its four exposed reference
maps and requested turns 0/25/50/75/99. Its historical v2 metadata is a tool label;
new paths and exact payload hashes bind this v4 check. Preserve missing terminal
slots and all timing failures. No concurrent CPU-heavy jobs during timing/strength
checks. Historical model, collector, PPO, bridge, weights and submission identities
must remain intact. Freeze final source/weights before strength games.

## Fixed comparisons and decision

1. Progress diagnostic: final v4 versus exact v2, 100 games on 50 paired fresh maps,
   seeds 12200000..12200049, two workers, CG budgets. Report W/D/L, score difference,
   map-grouped diagnostic uncertainty, errors and timings. This is not promotion.
2. Independent practical-v4 versus selected v09, base seed 12300000, two workers,
   blocks 300/+200/+500; mandatory futility stops strictly below 50% at 300/500,
   final local pass only at >=55% over all 1,000. Exclude all prior, teacher train,
   validation, smoke/process/late-panel and progress maps. Run regardless of the
   diagnostic outcome when execution is valid; no retries or tuning.

A local failure skips boss games. A local pass still requires the project's matched
40-game boss check before any promotion. No promotion, upload or push is authorized
by this experiment. Record all outcomes, update BOTS/DEVLOG/PLAN, retain reproducible
evidence and commit the completed experiment locally, including a rejection.

## Completed evidence and interpretation

All 60 games finished at their actual terminals on 30 predeclared maps, including
17 early terminals. The corpus contains 5,660 real teacher turns and
22,640 exact float32 conditional rows: 18,192 training rows on 24 maps and
4,448 validation rows on six different maps. All 11,320 teacher/opponent calls
completed legally; all expanded and accepted plans agree. Independent streaming
audit verified every binary record, prefix, mask, outcome, gzip CRC and hash.
Compressed game data totals 52,289,086 bytes; the disposable raw
disk cache is not duplicated in the archive.

The single 16-epoch run completed 4,560 Adam updates and
291,072 row exposures in 215.833 seconds. Checkpoints
at epochs 0/8/16 retain Adam steps 0/2,280/4,560 and RNG/configuration state.
Only the final model was exported and compared. All 15 value-head parameters
remain unchanged, while 3,424 policy/shared parameters were trainable. Shared
features changed, so value outputs remain uncalibrated and unsuitable as an
established search critic. Peak Python working set, including mapped data pages:
1676.0 MiB.

On held-out maps, mean cross-entropy changed from
4.061915 to 2.033353.
All-choice agreement rose from 17.83% to
49.44%; excluding forced singleton choices,
from 1.75% to
39.54%. Final unforced build agreement
is 31.33%, disruption agreement
58.81%. Exact full-plan agreement
is 77/1112 (6.92%);
training full-plan agreement is 599/4548
(13.17%). These use teacher prefixes and are
imitation diagnostics, not closed-loop match strength.

Preflight: 545 scoped Java tests and 30 Python tests passed. After final export,
five targeted Java tests and all 30 Python tests passed; 292/292 engine replays
reproduced exactly both before collection and after training. Six pre-existing
untracked Pduhard tests remain excluded; this is not an unqualified full-suite pass.
No core or historical spatial model/runtime/weights changed. No training, process
or strength retry, checkpoint selection, or outcome-driven tuning occurred.

Initial/final training parity passes all 24 observations per model. The fixed
late panel captured 16/20 requested positions (four genuinely post-terminal slots
missing, no replacements). All 128 observations per model pass numerical and
greedy parity; final maximum error 9.89437e-06.
Both models have 32/32 identical ample/nominal/traced plans, zero cuts/errors/skips.
Final nominal mean/p95/max: 13.614/
20.395/20.489 ms.
The reused tool's historical v2 label is not the candidate identity: exact v4
payload hashes and separate output paths identify this measurement.

Exact Player: 84,315 characters, source SHA `a961638dca644f880a12224f4e385fcfbb9efbe52d5a3744e2a0c2267276bdb2`,
build `de52c874c686f2729fe95f543b6ebe3992206478157a150cbf1e69f4190b6767`. Strict zero-grace process check: eight games, zero problem
games; first response max 593.798 ms, later max
30.016 ms. This finite local panel is not a CG-host timing
guarantee. The timed progress bench still recorded 2/0
slow calls (candidate/control), and the v09 comparison 2/0;
preserve these observations alongside the clean process result.

Progress versus exact v2: **98W/2L/0D / 100**, mean score difference
+4474.32, zero game errors, 128.545 s.
All 50 maps are fresh and paired, seeds 12200000..12200049, two workers, CG budgets.
Map-grouped Hoeffding diagnostic 95% interval:
[78.79%, 100.00%]. This establishes a large
improvement against that particular earlier model.

Practical-v4 versus v09: **0W/300L/0D / 300**, mean score difference
-9736.15, zero game errors, 569.416 s.
150 separate fresh paired maps, two workers, CG budgets, `STOP_FUTILITY_300`.
The mandatory futility stop ends evaluation; no further local blocks or boss games.
All 200 evaluation maps are distinct and
exclude prior/training/validation/smoke/process/late-panel maps. The v09 index also
excludes all 50 v2-comparison maps. All 896 final
frozen files remained byte-identical through evaluation.

Decision: retain the teacher-data/distillation pipeline and stronger spatial
reference, reject this payload for promotion. V09 stays selected. The result does
not establish whether more data, a different architecture, or search would close
the gap; those require separate experiments. Value calibration and value-guided
search remain later work, not completed by this supervised stage.

Evidence: [summary](evidence/spatial-ppo-v4.json),
[training](evidence/spatial-ppo-v4-training.json),
[archive index](evidence/spatial-ppo-v4-artifacts.json),
[complete retained archive](evidence/spatial-ppo-v4-artifacts.zip).
The archive contains compressed exact teacher data, metadata, checkpoints,
predeclared protocol/source/runtime manifests, independent audits, test logs,
numerical panels, exact submission and raw comparison reports.
