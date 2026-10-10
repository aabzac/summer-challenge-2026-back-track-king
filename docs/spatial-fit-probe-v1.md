# spatial-fit-probe-v1 — bounded same-example fitting probe

## Prospective contract

Declared 2026-10-10 before subset preparation, any model evaluation or gradients.
The user authorized the next step proposed by spatial-label-audit-v1. This is a
diagnostic within original roadmap stage 3, not a new arena candidate. Question:
can the unchanged V4 training pipeline learn a small fixed subset of its existing
teacher examples when given repeated exposure? Selected v09 remains unchanged.

### Deterministic subset and sources

Use the complete original V4 corpus/cache and its original TRAIN split. Select
exactly one root from each of its 48 training games: 48 roots / 192 consecutive
conditional rows, all four rows per selected root. Both seats, all 24 training
maps and the original opponent schedule remain represented.

Identify conflicting labels for exactly identical observed inputs within TRAIN
only, using the retained label-audit definition (all observation fields, both
masks, explicit stage; no label/raw turn/seat metadata). Exclude an entire root
if any of its rows belongs to a conflicting TRAIN input group. Bind this exclusion
to the unchanged label audit and corpus identities. Consistent duplicate inputs
are allowed. For each game, rank all remaining turns by SHA-256 of these exact
UTF-8 bytes, then by numeric turn if hashes tie:

```text
spatial-fit-probe-v1\n{id}\n{turn}\n
```

Here `{id}` is the original game ID and `{turn}` is its decimal zero-based turn;
`\n` denotes a literal newline byte. Choose the lowest rank. Use no model output,
income, win/loss, phase or difficulty filter. If any game has no eligible root,
fail preparation without replacing that game or reducing coverage. Retain every
exclusion, selected root/row lineage, and byte/hash identity. Freeze the subset
before the first model evaluation or optimizer step.

### One fixed training run

- Initial checkpoint: original `build/spatial-ppo-v4/final.pt`, with payload SHA
  `89de11f4239844c55c6e61c987ee2443fd5d79a1c71af782d2686e836ec6f3ef`.
  Verify against the frozen Java V4 payload and historical training evidence.
- Unchanged `model.SpatialNet`, 3,439 coefficients, original V4 mean masked
  hard-label cross entropy across all four decision stages. Preserve forced-row
  treatment and original ordered targets; no remaining-set objective or PPO.
- Freeze the 15 value-head coefficients; train the same 3,424 policy/shared
  coefficients. Shared features change, so value estimates remain uncalibrated.
- Fresh Adam, learning rate 0.001, original default betas/epsilon and zero weight
  decay; gradient norm clipping 1.0. Seed Python/NumPy/PyTorch with 20261010.
  CPU PyTorch 2.8.0+cpu / NumPy 2.3.5, two Torch workers, one interop worker,
  deterministic algorithms, shuffled `torch.randperm` of the 192 rows each epoch.
- Exactly **1,024 epochs**, 64-row minibatches, **3,072 optimizer steps** and
  **196,608 row exposures**. No early stopping, checkpoint selection, extension,
  restart, alternate seed, architecture/loss change or parameter search.
- Retain atomic checkpoints at epochs 0/512/1024 with model, optimizer and RNG
  state, plus initial/final standalone payloads in the diagnostic output directory.
  Log epoch losses/gradient summaries compactly every 64 epochs. Retain errors.
  No generated Java weights, factory registration or shipped artifact changes.

### Measurements and interpretation

Evaluate initial and final weights on (a) the selected subset, (b) all remaining
original TRAIN roots excluded from this probe, and (c) original validation.
The complement was already used to train the V4 initializer; validation is also
previously exposed development data. Neither is a fresh generalization test.
Additionally evaluate the selected subset every 64 epochs to describe the fixed
learning curve without selecting a checkpoint. Report hard-label cross entropy,
agreement and counts for forced/unforced choices, BUILD, DISRUPT, stage-zero BUILD
and teacher-prefix all-four agreement. No arbitrary agreement percentage is a
strength gate and no fitting result promotes a strategy.

Verify initial/final Python–Java parity using the first six selected roots in
game/turn order (24 rows), the original absolute tolerance 1e-4 and exact masked
greedy agreement. Replay all 48 saved TRAIN trajectories through core and verify
their original raw observation-stream hashes. At each selected root run the
actual initial/final Java policies with their own prefixes and ample 60,000-ms
budgets. Compare ordered and unordered complete plans, disruption and semantic
next state with the teacher using the identical recorded simultaneous opponent.
All alternatives end after that one transition; only the original teacher
trajectory continues. No new teacher calls or new games. Report skipped/late calls
as failures, never omit roots. Teacher-prefix predictions must not be stitched
into a purported actual student plan.

A strong same-example improvement establishes fitting ability on this subset
only; it does not establish useful strategy, full-corpus fit or generalization.
Failure or a plateau cannot distinguish capacity, representation aliases and
optimization by itself. Excluding exact-input conflicts does not remove every
possible model-induced alias or missing teacher variable. Observe the endpoint
and curve; do not diagnose an optimizer defect solely from disagreement.

### Verification and retention

Use isolated new offline tools/tests and frozen helpers. Run meaningful synthetic
tests and `cgValidate` before gradients. Freeze this prospective contract, new
sources/classes, fixed configuration, selected rows, initializer and source/cache
identities; verify historical tracked files and final identities afterwards.
Keep only the three checkpoints and compact logs/panels, not per-update artifacts.
Archive the protocol before results, settings, source/tests, subset manifest,
selected bytes, optimizer/RNG checkpoints, raw metrics/panels, commands and errors.
Reference unchanged V4 and label-audit archives by hash rather than duplicating
the full corpus. Update README/PLAN/BOTS/DEVLOG and make the authorized local commit.

No strength bench, boss games, submission/process candidate check, arena upload,
push or promotion is part of this fitting diagnostic. Original stage 3 remains
open regardless of the fitting score. Any subsequent strategy experiment needs
its own predeclared test; do not automatically expand this run after seeing results.

## Completed result — 2026-10-10

The unchanged V4 pipeline **learned all selected examples**. Selection retained
48 roots / 192 rows after excluding 58 conflict-bearing TRAIN roots, with one
root per game and both seats on all 24 maps. No game lacked an eligible root.
The single run completed exactly 1,024 epochs, 3,072 Adam steps for every active
parameter, and 196,608 row exposures. Total elapsed time was 279.746 seconds,
including evaluation and guards; training/evaluation accounted for 252.500 s.
There were no training retries, extensions or checkpoint selection.

On the selected subset:

- All decisions: **118/192 (61.458%) → 192/192 (100%)**.
- Unforced decisions: **72/146 (49.315%) → 146/146**; the 46 forced decisions
  already matched initially and remain separate from this improvement.
- Unforced BUILD: 39/98 → 98/98; stage-zero unforced BUILD: 16/45 → 45/45;
  DISRUPT: 33/48 → 48/48.
- Mean hard-label cross entropy over all rows: **1.517398 → 0.000181729**.
- Teacher-prefix all-four agreement: 8/48 → 48/48.

The first **recorded** exact fit occurs at epoch 192 (576 optimizer steps), after
189/192 at epoch 128. It stays exact at every later recorded point through the
fixed endpoint at epoch 1,024, while loss continues falling. Measurements every
64 epochs do not identify the precise first optimizer step that achieved it.

The independent Java panel confirms **actual own-prefix full plans**, rather
than only predictions after teacher choices: ordered plans, unordered plans and
complete semantic next states each improve **8/48 → 48/48**. All 48 trajectories,
4,548 turns and 18,192 rows were reconstructed with exact original stream hashes.
No roots were skipped, and the trainer/Java counts agree for all 15 reported
teacher-prefix slices and all-four agreement. Student alternatives end after the
one compared transition; these are not full games played by the fitted model.
Panel collection took 22.422 s; reduction took 11.732 s excluding startup.

The broader measurements deteriorate:

- Remaining TRAIN examples (18,000 rows / 4,500 roots): unforced agreement
  **6,179/14,282 (43.264%) → 4,300/14,282 (30.108%)**; all-row mean loss
  1.743708 → 9.714891. Teacher-prefix complete plans 591/4,500 → 362/4,500.
- Previously exposed validation (4,448 rows / 1,112 roots): unforced agreement
  **1,471/3,720 (39.543%) → 969/3,720 (26.048%)**; all-row mean loss
  2.033353 → 11.219319. Teacher-prefix complete plans 77/1,112 → 35/1,112.

Those complete-plan counts outside the subset use teacher prefixes, not additional
Java own-prefix runs. The complement was used to train the V4 initializer and
validation has already informed development; neither is a fresh generalization
test. The observed deterioration is consistent with narrow memorization and
forgetting under this deliberately tiny-set objective. No claim about game
strength, full-corpus capacity or calibrated future value follows.

Verification: **23 focused tests pass** (11 Java, including four new tests;
12 Python), plus **292/292 exact CG replays**. Both initial and final numerical
parity checks pass on 24 rows with exact masked-greedy agreement. Final maximum
absolute error is 0.0000724793, within 0.0001. All 15 value coefficients are
unchanged, although shared features changed and value output is uncalibrated.
Independent source review found no blocker. A nonblocking tensor-to-scalar test
warning is retained in the raw log. Final identity checks passed for 903
historical files and 642 frozen inputs/executables before these results were
appended. Historical models, core and shipped artifacts were preserved.

Final diagnostic payload SHA-256:
`ccb7032707da27c3daac11cdcf2e24ff088cd1b9e05b19ceeb7ea9555bc6b4ee`.
The archive retains epochs 0/512/1024 with model/optimizer/RNG state, selected
inputs and lineage, full metrics/curve, raw Java panels, source/tests, the
prospective contract and freezes, command records, logs and identity checks.

Decision: this diagnostic is complete. Same-example fitting works on this subset;
full-corpus fitting, useful value and competitive learning are still unresolved.
Selected v09 remains unchanged, and original roadmap stage 3 remains open.
The next proposed experiment is one fixed longer full-corpus run from **original
V4**, with unchanged architecture/loss and recorded train/validation curves.
Declare the extra budget and optimizer/RNG handling beforehand; restoring the
original training state isolates continuation more closely than a fresh optimizer.
Do not start from these memorized weights or select a checkpoint after inspecting
results. Agreement stays a diagnostic, not a strength gate. No such follow-up,
strength bench, candidate export, submission, boss call, upload or push was run here.

Evidence: [machine-readable report](evidence/spatial-fit-probe-v1.json),
[archive index](evidence/spatial-fit-probe-v1-artifacts.json),
[retained archive](evidence/spatial-fit-probe-v1-artifacts.zip). Original V4 and
label-audit archives are preserved as hashed dependencies.

## Reproduction

Restore the original V4 corpus/cache, initializer and label-audit dependency paths
recorded in the archive. Build Java once before freezing sources/classes. Run the
`SpatialFitPanelTest` and `SpatialLabelAuditTest` Java tests, Python unittest
discovery for `test_fit_probe.py` and `test_fit_probe_panel.py`, and `cgValidate`.
The archived `build/spatial_fit_probe_evidence.py` records commands and verifies
historical/frozen identities. Original exact commands and runtime versions are
retained. Outputs are write-once: reproduce in a fresh checkout/output directory.

```text
python build/spatial_fit_probe_evidence.py init
python tools/spatial/fit_probe.py prepare
python build/spatial_fit_probe_evidence.py freeze
python tools/spatial/fit_probe.py train
```

Before `init`, restore the prospective protocol/source files; before freeze also
save the Java command array
for `btk.tools.spatial.SpatialBridge` to the protocol's `java-command.json` path.
The default paths are the original recorded lineage. After training, invoke main
class `btk.tools.spatial.SpatialFitPanel` with the saved Java/classpath and arguments:

```text
build/spatial-ppo-v4-data build/analysis/spatial-fit-probe-v1/selection/selection.json build/spatial-fit-probe-v1/final-weights.base64 <fresh-panel-dir>
```

Then reduce, binding the metrics file beside its initial/final payloads:

```text
python tools/spatial/fit_probe_panel.py --panel <fresh-panel-dir>/manifest.json --selection build/analysis/spatial-fit-probe-v1/selection/selection.json --training-report build/spatial-fit-probe-v1/metrics.json --out <fresh-summary.json>
python build/spatial_fit_probe_evidence.py verify
```

Restore the archived pre-results protocol for an exact prospective-source check;
append results only after final verification, then archive the completed run.
