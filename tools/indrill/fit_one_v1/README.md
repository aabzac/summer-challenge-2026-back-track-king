# Indrill fit-one-v1

Fixed build-only diagnostic on the first observation in the previous Stage-A
panel's export order. [Protocol](../../../docs/indrill-fit-one-v1.md). All outputs
go to `build/analysis/indrill-fit-one-v1`; old diagnostics and bot weights are inputs.

Run the synthetic objective and runner checks first. They use artificial boards,
preserve existing reports, and bind their tested sources. Then, after review:

```powershell
python tools/indrill/fit_one_v1/checks.py
python tools/indrill/fit_one_v1/runner_checks.py --out build/analysis/indrill-fit-one-v1/setup/runner-check-fixtures-first
python tools/indrill/fit_one_v1/run_fit.py prepare --checks build/analysis/indrill-fit-one-v1/setup/checks.json --runner-checks build/analysis/indrill-fit-one-v1/setup/runner-checks.json
python tools/indrill/fit_one_v1/run_fit.py run --arm free
python tools/indrill/fit_one_v1/run_fit.py run --arm network
python tools/indrill/fit_one_v1/run_fit.py finalize
```

`prepare` verifies the original training/export freezes and completed evidence,
streams the first A row, validates it with the existing adapter, retains its exact
JSONL bytes and writes the new source/runtime/data freeze before any gradient.
Each arm completes 2,000 updates even if a descriptive checkpoint already decodes
the teacher set. The arms start independently and run serially.

Each update has a prior-checkpoint intent, parameters/Adam checkpoint and linked
receipt. `run --arm free --resume` (or `network`) verifies the full existing chain
and unchanged freeze/runtime before continuing from a completed boundary. An
uncommitted intent, partial checkpoint, changed input or technical failure blocks
automatic replay. Existing outputs are never overwritten. Finalization verifies
both complete chains and the five saved metric triplets per arm; it does not run
fresh model scoring or gradients.

The final decoded construction set decides each arm's fitting result. NLL and
set scores remain descriptive. This run creates no games, holdout evaluation,
deployable weights, promotion or universal capacity claim.
