# Spatial PPO v1

First complete, deliberately small spatial policy/value experiment. The fixed
recipe and interpretation are in `docs/spatial-ppo-v1.md`. This is a short
technical smoke run, not a recipe claimed to produce a competitive policy.

Java owns all game rules, paths, observations and legal masks. Python owns the
neural network and optimizer. Both players construct a complete plan from the
same state before the bridge applies a single simultaneous core transition.

## Runtime and commands

The completed run used Python 3.12.14, CPU PyTorch 2.8.0+cpu and NumPy 2.3.5.
Libraries can live under gitignored `build/spatial-deps`; the training script
adds that directory automatically. No global package install is required.

```powershell
# Using a Python 3.12 interpreter:
python -m pip install --target build/spatial-deps --index-url https://download.pytorch.org/whl/cpu torch==2.8.0+cpu numpy==2.3.5
.\gradlew.bat spatialRuntime --offline --console=plain
python tools/spatial/train_spatial.py --java-command-json build/spatial-java.json
.\gradlew.bat submit '-PsubmitVersion=spatial-ppo-v1' '-PsubmitOut=build/submit-spatial-v1'
.\gradlew.bat procCheck '-PsubmitOut=build/submit-spatial-v1' '-Pbot=v09' '-Pgames=8' '-Pout=build/analysis/spatial-ppo-v1/process.json'
```

The completed training directory refuses reuse. Retraining requires a new
experiment, weight artifact and strength bench; do not change the preserved
recipe or regenerate weights merely to rerun tests. The trainer writes
`SpatialWeights.java`; do not edit its payload by hand.

For the Python tests, put `build/spatial-deps` on PYTHONPATH and run
`python -m unittest discover -s tools/spatial -p test_spatial.py`.
Java tests are `SpatialObservationTest`, `SpatialBridgeTest`, `SpatialNetTest`,
`SpatialBotTest` and the spatial case in `FileMergerTest`.

## Evidence

The initial/final checkpoints contain model, optimizer, initialization settings
and Python/NumPy/Torch RNG state. They are retained for reproduction and inspection,
not as resumable ongoing environment snapshots: continuation of this exact run
would also require its four live Java states and collector position. V1 has no
resume command. Only the initial and final checkpoints are saved, not every update.

The 64-observation parity panel covers four maps, both seats, four decision
stages at real turns 0 and 15. Final testing uses the same panel. No terminal
occurred in the four-update training run; terminal bonuses and reset boundaries
are verified separately by tests. Policy logits and value are compared within
1e-4, and masked greedy choices must agree exactly.

The serial `btk.tools.spatial.SpatialProfile` main class profiles four fixed
diagnostic maps separately from process deadlines and strength matches. It refuses
to overwrite its report. Timing is a local measurement, not a CG-host guarantee.

## Complete-game follow-up: v2

`run_v2_training.py` imports the unchanged v1 model, collector and PPO code. Its
fixed recipe is 64 iterations x 8 environments x 16 real turns, with fresh maps
and actual terminal resets. See [the protocol and results](../../docs/spatial-ppo-v2.md).
The initial, midpoint and final checkpoints are retained; the final checkpoint
is the tested candidate, without checkpoint selection from match results.

The new generated classes are `SpatialInitialWeights` and `SpatialV2Weights`.
The named bots `spatial-ppo-v2-init` and `spatial-ppo-v2` select these weights while
delegating to the unchanged v1 Java inference. All v1 artifacts stay frozen.

```powershell
# Historical fixed-run command; existing output directories are deliberately rejected.
python tools/spatial/run_v2_training.py --java-command-json build/spatial-java.json
# Tests include terminal/reset accounting, initial-weight identity and instrumentation neutrality.
python -m unittest discover -s tools/spatial -p 'test_*.py' -v
.\gradlew.bat submit '-PsubmitVersion=spatial-ppo-v2' '-PsubmitOut=build/submit-spatial-v2'
```

`SpatialLatePanel` captures one fixed v09-v09 trajectory per map and saves staged
observations plus Java outputs before comparing ample/nominal calls serially.
Its positional arguments are initial Base64 file, final Base64 file and a fresh
output directory. `compare_v2_panel.py --checkpoint ... --panel ... --out ...`
checks those saved outputs against the selected Python checkpoint without games.
Missing positions after an actual terminal are preserved as missing. Reports
refuse replacement; a new training experiment needs new weights, recipe and bench.

## Fixed opponent pool: v3

`SpatialPoolBridge` wraps the frozen bridge. Eight fixed slots cover four frozen
opponents (`spatial-ppo-v2-init`, `spatial-ppo-v2`, `v01`, `v09`) and both learner
seats. Opponents choose once from pre-turn state before learner sampling; only
learner trajectories enter the unchanged PPO objective. See the
[fixed protocol](../../docs/spatial-ppo-v3.md).

V3 loads v2 final weights with a fresh Adam state and RNG, then collects exactly
16,384 learner turns. The initial/midpoint/final checkpoints are retained and
only the final weights export to `SpatialV3Weights`. This is not exact resumption
of v2, and existing output directories/evidence are rejected.

```powershell
# The command JSON has the same classpath as spatialRuntime, with main SpatialPoolBridge.
python tools/spatial/run_v3_training.py --java-command-json build/spatial-v3-java.json
python -m unittest discover -s tools/spatial -p 'test_*.py' -v
.\gradlew.bat submit '-PsubmitVersion=spatial-ppo-v3' '-PsubmitOut=build/submit-spatial-v3'
```

The archived experiment contains the exact command JSON, exclusion manifests,
settings, checkpoint lineage, test evidence and generated submission. The v3
late-position regression reuses v2's diagnostic maps/tool; exact payload hashes
and new output paths identify the tested weights, not the tool's old v2 label.

Completed fixed run: 183 terminal games, 16,384 learner turns, 1,024 Adam steps.
Final versus v2: 6W/94L/0D; versus v09: 0W/300L/0D, STOP_FUTILITY_300. Technical
checks passed, but strength regressed; this v3 payload is not selected or promoted.
