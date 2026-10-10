# spatial-ppo-v9 — global town context within original stage 3

Predeclared 2026-10-10 from revision `4e896e4`, following the user's request for
the next experiment. Selected `current = v09` remains unchanged. The version
name follows the spatial series; this is supervised imitation, not a new PPO
run. Original stage 3 is useful competitive learning/value, still unresolved.

## One hypothesis

The learning audit demonstrated two legal cells with identical local inputs and
equal BUILD logits despite different delayed outcomes under a fixed continuation.
Expose distant town information at each cell through one representation change.
Keep local town injection and add, **before the first residual convolution**:

`context[channel,cell] = sum_t townEmbedding[t,channel] / (townCount * (1 + Manhattan(cell,town[t])))`.

Only present towns and real board cells contribute. No towns gives zero context.
The fixed float32 kernel uses reciprocal integer denominator above. It is
normalized by town count, not distance-weight sum. Town embeddings are the
existing linear transform of the same49 town features, including its bias.
Weights/order remain3439 floats;3424 shared/policy coefficients train and15 value
coefficients stay frozen. Board/town observations, legal masks, conditional action
interface, policy heads, greedy execution, deadline margin and rules are unchanged.
No search or value-based action ranking. Global-context-off must reproduce the
old network for the same payload; this is a mechanism check, not another candidate.

Implement isolated new Java/Python network modules. Preserve old spatial source,
weights and Python modules byte-for-byte. Add default-off Params flag
`USE_SPATIAL_V9_TOWN_CONTEXT`; the new named candidate's complete snapshot is
the v4 effective configuration plus that flag true. Historical configurations
and exact packaged artifacts must retain their previous identities.

Geometry is static per map: precompute/cache kernel and reuse primitive buffers
in Java forward passes, including deadline checks and atomic output publication.
No core changes, scoring/path reimplementation or extra learned parameters.

## Fixed training, reused data

Repeat the completed v4 recipe with the changed representation only:

- Exact v2 final warm start payload
  `b8cdd34b01aac15c025bd36d41290fb261946debe483b3af268117ed7d8eca1d`,
  fresh Adam, Python/NumPy/Torch seed20261010 after construction/loading.
- Reuse unchanged v4 teacher corpus/cache:24 train maps/48 terminal games,
  18192 conditional rows;6 validation maps/12 games,4448 rows. Same map splits
  and labels, no new teacher games, rollouts, DAgger or outcome labels.
  Validation maps are now exposed development diagnostics, not fresh confirmation.
- Exactly16 epochs, shuffled decision rows, batch64 retaining final partial batch,
  Adam lr.001, gradient norm cap1, CPU PyTorch2.8.0+cpu/NumPy2.3.5,
  two compute threads. Masked hard-label policy cross-entropy, no other loss.
  Expected4560 optimizer steps/291072 row exposures. No retries, tuning,
  extension or checkpoint selection based on results.
- Retain checkpoints0/8/16 with model/optimizer/RNG/config. Only final16 exported
  as generated `SpatialV9Weights.java`. A separately recorded generated warm-start
  copy may make integration compile before training; it is never a candidate.
- Report initial/final CE, all/unforced/build/disrupt teacher agreement and exact
  full-plan agreement on both splits. Preserve frozen value-head parameters;
  shared features change, so value remains uncalibrated and unused by the bot.

The exposed delayed-route fixture verifies representational distinguishability
with predetermined synthetic coefficients and records initial/final outputs.
It is not training data, a strength target, an optimal-action label, a reason to
retune or a basis for checkpoint selection. A changed tie alone is not improvement.

## Verification and timing

Before gradients: focused Java/Python tests for distance kernel, town masking,
context-off parity, gradients/frozen head, cooperative cutoff/atomic outputs,
integration and historical identities; scoped project tests and292 exact engine
replays. Keep the6 pre-existing unregistered Pduhard tests excluded explicitly.
Hash protocol, sources, runtime, warm-start checkpoint and unchanged data/cache.
Record technical failures and repairs before measurement; no outcome-driven fixes.

Initial/final Python/Java parity: first24 conditional training rows, tolerance1e-4
and identical masked greedy choices, as in v4. Reuse the existing saved v4 late
panel: maps10100000..10100003, requested turns0/25/50/75/99;16/20 recorded live
positions, with actual terminal omissions preserved. Replay saved reference
actions through core and match snapshots; generate no new reference decisions.
For initial/final v9 payloads, record all128 conditional inputs/outputs per model
and verify Python/Java parity. Measure serial ample60000ms then nominal36ms phases,
fresh bot per map/seat/phase with100ms warmup, no concurrent heavy jobs. Retain
cutoffs, mismatches and timing outliers; no timed rerun to obtain a pass.

After final export: targeted checks, exact `submitVersion=spatial-ppo-v9` in its
own output directory, <=100000 characters, compile-check, and exactly8 serial
strict zero-grace process games vs v09 on the existing first8 CG replay inputs
used by earlier process checks. Freeze their identities/order; no process retry.
Report process failures if any and reject deployment readiness. Freeze final
weights/source/runtime/exact artifact before strength comparisons.

## Fixed comparisons

1. Progress diagnostic: final v9 vs frozen **spatial-ppo-v4**,100 games on50 fresh
   paired generated maps, seeds23000000..23000049, two workers, CG budgets.
   Report outcomes, gaps, errors and timing; not a promotion gate.
2. Practical-v4 vs selected **v09**, base23100000, two workers, CG budgets:
   300/+200/+500 pooled games; stop strictly below50% at300 or500, survivors
   need>=55% over1000, no early pass. Run regardless of progress result if the
   artifact executes validly. No tuning/retries/extensions.

Exclude all prior/development, teacher/cache, saved-panel, process and progress
map hashes before the respective strength batch. Local failure skips boss.
Local pass still requires the matched40-game boss check before promotion; this
request does not authorize arena uploads/promotion/push. Record and commit the
completed local experiment including a rejected result, update PLAN/BOTS/DEVLOG
and retain compact reproducible evidence. The v4 corpus archive is a hashed
dependency, not duplicated data. Do not silently mark original stage3 complete.

## Results

Completed 2026-10-10. One fixed run, final-only export, no strategy tuning or
extension. Selected **v09** remains unchanged; original roadmap stage 3 remains open.

The 16-epoch run completed **4,560 optimizer steps** and
291,072 row exposures in 267.236 s. It reused
the exact v4 corpus, split, V2 warm-start and optimization recipe. All 3,439 floats
are finite; 3,424 policy/shared coefficients trained, and the 15 value-head floats
remain byte-identical. Initial payload:
`b8cdd34b01aac15c025bd36d41290fb261946debe483b3af268117ed7d8eca1d`;
final payload:
`fd616a7f454792ec47d3cfc8e5a10f63becfb889d06dbd64e0dea8bf053b4cb6`.

On the previously exposed validation maps, unforced teacher-action agreement was
**38.414%**, versus v4's **39.543%**; exact full plans were **63/1,112** versus
**77/1,112**. All-row cross-entropy was 2.053766 versus 2.033353. BUILD-unforced
agreement declined (29.448% versus 31.327%); DISRUPT improved slightly (59.442%
versus 58.813%). These teacher-prefix diagnostics show no overall imitation
improvement, and these reused maps do not provide fresh generalization evidence.

The exposed delayed-route fixture now distinguishes the two cells: final
investment-minus-alternative BUILD logit is 0 with context off and +0.3440676 with
context on, for both seats. The actual greedy choice is another cell, 242. This
verifies removal of the specific representation alias; it does not establish
selection of the scripted investment, optimal long-term ranking, or prevalence
of that limitation on normal maps. Turning context off reproduces the old
network bit-for-bit for each payload.

Verification and recorded technical incidents:

- The scoped initial Java suite ran 621 tests: 620 passed, one unchanged
  IndrillForum timing assertion failed at 37.6615 ms while Python tests were
  concurrent. That single test passed separately without concurrent load; both
  records are retained. All 17 new Java tests passed. Post-export targeted Java
  checks: **69 passed**. The six pre-existing unregistered Pduhard tests remain
  explicitly excluded.
- Python: initial run had four temporary-directory access errors; **47 tests
  passed** with a workspace temporary directory. No model/source repair followed
  these infrastructure incidents. Exact engine replay: **292/292**, before and
  after training. All historical source/payload/artifact guards passed.
- Initial/final training parity: 24 conditional rows each, identical greedy
  decisions. Replayed the saved v4 trajectories through core, matched all 16
  saved live positions, no new reference games. Late parity: **128 rows per
  payload**, no greedy mismatch; final maximum absolute error 1.060963e-5.
- Initial/final saved-panel actions each match ample actions **32/32**, no cuts,
  errors, skipped actions or calls above 36 ms. Final nominal mean/p95/max:
  **15.745/25.180/29.151 ms**.
- Exact Player: **87,202 characters**, source SHA
  `da8b3bb160ea90d0e20076060339f5e33584399906c82ea5523e453920fb2a76`.
  Strict zero-grace process sample: **8/8 clean**, first response max
  583.882 ms, later max 28.4595 ms.
  These bounded local checks are not a CG-host timing guarantee.
- The original progress-pool manifest used unsupported `purpose=progress-diagnostic`.
  BenchPool rejected it before map construction or any game. Preserve it and the
  failed log; a separately hashed `spatial-ppo-v9-vs-v4-run.json` uses the accepted
  `confirmation` metadata label with identical seeds, pair count and settings.
  This remains a progress diagnostic, not the promotion gate. The addendum was
  recorded before the first played game; no played comparison was retried.

Progress versus frozen spatial-ppo-v4: **42W/58L/0D / 100**, mean score gap
**-155.38**, errors 0/0, 118.039 s.
Fifty fresh paired maps, seeds 23000000..23000049, two workers, CG budgets.
No demonstrated strength improvement over v4 in this comparison; 42% is not a
precise population-strength estimate or a general rejection of global context.

Practical-v4 versus selected v09: **0W/300L/0D / 300**, mean score gap
**-9349.41**, errors 0/0, 615.378 s.
Prescribed **STOP_FUTILITY_300**; the remaining local blocks and boss games are skipped.
The progress/v09 reports record 0/2 candidate slow calls,
with maxima 46/57 ms; these observations remain alongside
the strict-process result. All 200 evaluation maps are distinct and exclude prior,
teacher, saved-panel and process maps; the v09 batch also excludes all progress
maps. All 863 frozen evaluation files and
78 historical files remained unchanged through evaluation. These results were
appended only after final identity verification; the pre-results protocol is retained.

Decision: retain the isolated experiment and its evidence, **reject promotion**.
The specific representation limitation is removed, but this fixed recipe has not
produced a stronger policy. It does not establish that all global context is
ineffective or that more training would fix the gap. Shared features changed, so
the unchanged value head is still uncalibrated and unused. Competitive learning,
reliable future value, and the original stage 3 remain unresolved. No further
training/search experiment is automatically started. No arena upload or push.

Evidence: [summary](evidence/spatial-ppo-v9.json),
[training](evidence/spatial-ppo-v9-training.json),
[archive index](evidence/spatial-ppo-v9-artifacts.json),
[retained archive](evidence/spatial-ppo-v9-artifacts.zip).
The archive keeps source, checkpoints 0/8/16, the pre-results protocol and freezes,
all technical incidents, numerical panels, exact submission, eight process inputs,
and raw game reports. The immutable v4 corpus archive is a hashed dependency;
it is not duplicated. Reproduction entry points are `tools/spatial/v9_distill.py`,
`tools/spatial/compare_v9_panel.py`, and the archived `build/spatial_v9_evidence.py`.
The training command refuses to overwrite a partial or completed run.
