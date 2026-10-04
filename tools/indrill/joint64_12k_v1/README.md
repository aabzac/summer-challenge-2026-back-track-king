# Indrill joint64 12k diagnostic

Separate continuation of the original `indrill-capacity-v1` Stage A joint model
from its full audited step 2,000 state to exactly 12,000 total updates. The old
experiment remains unchanged. See [the protocol](../../../docs/indrill-joint64-12k-v1.md).

The 64 observations, labels, sampling sequence, batch 16, network, original
equally weighted construction/ink loss and Adam settings are unchanged. The new
run restores parameters, both Adam moments, update counter, RNG, permutation and
position. Ink parameters train; only value-head parameters and moments stay zero.
There are 10,000 new updates, 160,000 new visits and 192,000 total visits
(3,000 per observation). The final joint exact count must reach 63/64.

Preparation verifies the original frozen setup, completed audit, every audited
parent pin and archive inventory. It loads only the existing selected 64-row
JSONL and checks its original panel order and construction/ink labels. The saved
construction-only 12k result is pinned as a descriptive comparison. No models
are rescored during preparation or finalization.

The step 2,000 metric is inherited byte-for-byte as a JSON payload, except its new
checkpoint reference. Original batch-32 joint evaluation runs only at steps
4,000, 6,000, 8,000, 10,000 and 12,000. There is no early pass, best-checkpoint
selection, holdout, Stage B, game batch or promotion.

Run the synthetic proof once after source review, then separate each stage:

```powershell
python tools/indrill/joint64_12k_v1/checks.py --out build/analysis/indrill-joint64-12k-v1/setup/synthetic-first
python tools/indrill/joint64_12k_v1/run_fit.py prepare --checks build/analysis/indrill-joint64-12k-v1/setup/checks.json
python tools/indrill/joint64_12k_v1/run_fit.py run --freeze build/analysis/indrill-joint64-12k-v1/freeze.json
python tools/indrill/joint64_12k_v1/run_fit.py finalize --freeze build/analysis/indrill-joint64-12k-v1/freeze.json
```

All outputs live in `build/analysis/indrill-joint64-12k-v1`. Checkpoints use
five-digit step numbers and numerical ordering across 9,999/10,000. A starting
proof binds the exact parent and inherited metric before the first new gradient.
Every new update retains an intent, checkpoint and linked receipt. `run --resume`
accepts only a complete committed boundary with unchanged sources, runtime and
bindings; partial updates or metric triplets are rejected without automatic replay.
Finalization validates saved artifacts and reports paired row changes against
joint step 2,000 and construction-only step 12,000; it performs no learned forward
passes or gradient replay. The independent audit remains a separate step.
