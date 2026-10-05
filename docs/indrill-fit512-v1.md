# Indrill fit512-v1: intermediate-panel learning diagnostic

Predeclared October 4, 2026 after the user authorized the proposed experiment on
512 positions. This is a separate development experiment following the completed
64-position and 2,048-position diagnostics. Their data, sources and verdicts stay
unchanged. The unstarted 24k continuation of B2048 is superseded by this experiment.

## Question and fixed sample

Can the unchanged compact model learn at least 70% of full teacher actions on a
fixed intermediate panel when each example receives the same 3,000 visits as the
successful 64-position run? How do build, ink and multi-cell construction develop,
and what final agreement remains on maps outside this model's training panel?
This diagnoses learning at an intermediate scale, not arena strength or a unique
causal limit of the architecture.

Reuse the exact original capacity-v1 teacher export, original validated engine
provenance and map-exclusion registry. From the 128 B map groups, rank map hashes
by SHA256 of the UTF-8 string `indrill-capacity-v1/train/` plus mapHash, exactly as
in the original selection. Take the first 32 maps, then retain their B rows in
the original export order: exactly 16 positions per map, 512 total. Do not take
the first 512 exported rows: export row order differs from map rank. All original
A64 observations must be included. Pin indices, map hashes and observation IDs
before model scoring or gradients. The SHA256 digest of UTF-8 observation IDs,
each followed by LF including the last ID, is
6cc2f87084eb20b577e41f09bc73baea3dd18df6a5263cf7122c113f9f6b8d36.

Use the original 1,020 research-holdout rows from 51 disjoint maps unchanged.
No new export, label edits, easy-example selection, augmentation or discarded
failures. Check map/input disjointness, legal labels, decoder coverage and input
collisions. If a conflicting-input ceiling is below 359/512, report the limitation
and stop before training rather than changing the sample. Existing research
holdouts and the larger training panel have been inspected previously; this is
outcome-informed development, not pristine independent confirmation.

## Fixed model, budget and decision

- Fresh initialization with model seed 2026100402, the same initializer as B2048;
  sampling seed 2026100412. No learned weights or optimizer state are inherited.
- Unchanged float32 64 -> 24 -> 24 network, features, decoder and joint objective:
  exact legal unordered-build-subset NLL plus legal ink/NONE NLL, equal weights.
  Inactive WV/BV weights and their moments remain zero.
- Batch 32, shuffled without replacement per epoch; Adam learning rate 0.0003,
  betas 0.9/0.999, epsilon 1e-8, gradient norm clipping at 10. No schedule,
  temperature, architecture, objective, seed or feature changes.
- Exactly 48,000 updates, 1,536,000 sample visits: exactly 3,000 per position.
  This matches A64's per-example exposure, not its number of optimizer updates,
  batch size, data difficulty or initial weights. It is four times B2048's update
  budget; comparisons cannot isolate sample size as the only causal variable.
- Training measurements only at 0 / 3,000 / 12,000 / 24,000 / 48,000 updates.
  These correspond to 0 / 187-or-188 / 750 / 1,500 / 3,000 visits per position.
  Interim measurements describe the curve; they do not change the fixed schedule.
- Decide only at 48,000: at least 359/512 exact joint actions gives
  FIT70_ESTABLISHED; otherwise FIT70_NOT_ESTABLISHED. This is a new operational
  fitting target, not an existing historical gate, a statistical confidence
  guarantee or a 70% win-rate claim. No early pass, tuning, retry or extension.

Runtime estimate from the preceding B2048 run is approximately 2.5-3 hours for
training plus preparation and audit; this is not a time-dependent stopping rule.
Preserve partial work if the user explicitly stops it. Numerical, identity or
persistence errors stop execution and are recorded; never replay an ambiguous
partial update or partial evaluation automatically.

## Measurements and interpretation

Retain every sampled update's full model/Adam/sampler checkpoint, linked intent
and receipt. Record per-row targets and predictions plus original metrics:
exact unordered build set, ink and joint action; cell precision/recall, paint and
head/joint NLL. Aggregate by map and retain cardinality, cost pattern, turn band,
seat and skipped-action breakdowns. Explicitly report multi-cell and three-cell
construction so empty/single-cell actions cannot hide that weakness.

After the fixed final update, evaluate only this candidate once on all 1,020
held-out observations, regardless of fitting verdict. Retain old40/later11 cohorts
and train/holdout gaps. This is descriptive and cannot rescue a failed fitting
target or trigger more training. No Q-v2 scoring, transfer gate, bootstrap test,
checkpoint selection, or new model evaluation during the audit. Any comparison
with the completed B2048 report uses its pinned saved metrics, not a rerun.

Good training agreement with poor held-out agreement suggests memorization that
does not sufficiently transfer. Low agreement already on training leaves budget,
optimization and representation unresolved. Good held-out agreement motivates a
separately designed game experiment; exact imitation is not itself game strength.
Failure after this fixed budget does not prove a capacity ceiling.

## Implementation, verification and completion

New tools: tools/indrill/fit512_v1. Outputs: build/analysis/indrill-fit512-v1.
Reuse original pure math, data, evaluation and generic persistence helpers without
editing old files or mutating old module globals. Review new orchestration and
run small synthetic checks before preparation. Freeze this protocol, the exact
selection, all active source/runtime dependencies, proof, data, predecessor
evidence and map-exclusion bindings before the first learned evaluation/gradient.

Audit saved checkpoint identities, full sampler exposures, finite tensors, zero
inactive head/moments, saved Adam parameter arithmetic and saved metric aggregates
without reconstructing learned logits or gradients. Those audit limits remain
explicit. Archive frozen sources/protocol/proof, selection, production metadata,
metric reports, every checkpoint hash and five scheduled full NPZ snapshots in a
verified compact tar.gz with a hash index. Other full states, raw corpus, replays
and runtime binaries remain locally retained and pinned; exclude raw/private data
from the compact archive. Record all outcomes in README/BOTS/PLAN/DEVLOG and make
the authorized local commit. No Java/core edits, RL, new games, production export,
CG IDE changes, arena upload or strategy promotion are part of this experiment.
