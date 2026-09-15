# Sabotage prediction: offline prototype

The predictor is local-only (`btk.tools.SabotageForecast`), not wired into `current` or submission.
It tests whether opponent history helps predict the NEXT effective disruption, not whether it
increases win rate. All game rules and hypothetical state transitions come from `btk.core`.

## Reproduce

```powershell
.\gradlew.bat cgForecast "-Pagent=6736850" "-Pout=build/analysis/sabotage-forecast-6736850.json"
```

Optional `-Ppath` selects the canonical arena corpus; `-Pparams` takes comma-separated Params
overrides for a separately declared experiment. The fixed run uses `FORECAST_DECAY=0.95` and
`FORECAST_FLOOR=0.10` plus the current `W_SELF_LOSS=1.2`. It does not tune on holdout results.

Five expert target choices: greatest current income damage after ink, greatest track balance
damage, track damage with preference for nearly inked regions, persistence of the last observed
target (income fallback), and no effective sabotage. Experts use a smoothed categorical
distribution over currently non-inked regions plus NONE. The adaptive mixture updates Bayesian
log weights with forgetting after observations; the fixed mixture retains equal weights. These
are deliberately limited heuristics, not a learned model of all possible opponent strategies.

## Information boundary

Predictions are made before either current command is resolved. Only the replay harness reads
both command lines, to advance the true state and audit labels. The observer receives previous
state, next-state instability/ink and our own accepted disruption (derivable from our own output).
It enumerates core transitions to find a unique matching opponent effect. Already inked targets
are indistinguishable from no effective sabotage. Unknown observations do not update weights;
failed terminal turns are excluded. Owner changes/placements are not used for learning yet.

Reset the model every match. An eventual live integration MUST update from real protocol turns
only: `Bot.warmup` currently invokes play on artificial turn numbers, and hypothetical search
states must never train the observer. The offline model has no such hook into play.

## Fixed diagnostic and outcome, 2026-09-15

- Corpus: 56 games of observer agent 6736850 within the 214-game validated arena pool. Split by
  SHA-256(`forecast-v1:<opponentAgent>`), first byte modulo 3: 0 holdout, others development.
  40 development games / 35 opponents; 16 holdout games / 15 opponents. No opponent agent
  crosses the split. These previously inspected games are NOT pristine confirmation data.
- 4,258 scored observations, zero unknowns or truth mismatches; one failed terminal turn skipped.
  Tests additionally cover same-region simultaneous sabotage, crossing the ink threshold,
  protocol round-tripping, no-effect/unknown transitions and posterior isolation/reset.
- Development mean-per-game log loss: adaptive **1.7610**, fixed mixture **1.7348**. Best single
  development expert: persistence (**2.0403**), selected without holdout labels.
- Holdout mean-per-game log loss (lower is better): adaptive **1.7166**, fixed **1.8165**,
  persistence **1.9544**. Top-1 accuracy averaged over games: **69.9% / 57.7% / 68.2%**.
  Conditional on actual effective sabotage, micro hits: adaptive **790/1,139**, fixed **638/1,139**.
- Paired mean-per-opponent log-loss improvement, opponent-bootstrap 95% intervals (2,000 draws):
  versus fixed **+0.0928 [-0.0450, +0.2220]**; versus persistence **+0.2292 [+0.0861, +0.3841]**.
  The predeclared diagnostic required positive lower bounds against BOTH controls: **not passed**.
  Higher top-1 accuracy alone does not prove better calibrated probabilities or strategic value.
- Offline prediction time: mean **0.66 ms**, p95 **1.90 ms**, maximum **183.77 ms** including cold
  execution/JIT. Observation reconstruction is separate and not included. This is neither a live
  50-ms guarantee nor a process check; the model is not shipped.

Decision: retain the diagnostic tool, do not enable a strategy flag, freeze v06 or run promotion
benches. Any next model/calibration experiment needs a new declared comparison; clean future
data and actual gameplay/timing checks remain necessary before deploying adaptive decisions.
