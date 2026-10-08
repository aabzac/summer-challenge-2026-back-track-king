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
