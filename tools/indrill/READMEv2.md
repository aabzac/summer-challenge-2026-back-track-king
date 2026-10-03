# indrill-q-v2: a learned value baseline

The modeling change from v1 is
`Q(s,fullAction) = V(s) + sum(selectedCellAdvantages) + selectedInkAdvantage`.
`V(s)` is `mean(h1 across all cells) dot WV + BV`; it appears exactly once.
The empty build and empty ink have zero **advantage**, so full WAIT has learned
value `V(s)`. WAIT transitions train the value head and the shared representation.
The greedy successor's full Q can therefore be negative. The unchanged exact
budget-three maximizer still chooses the additive action advantages.

The 64/24/24 observation/network, Java rules, reward, gamma .99, 3-step returns,
Double DQN, prioritized sampling, Huber 1, Adam .0003, gradient cap 10 and target
period 100 remain the same. Model size is 3,387 float32 parameters. This change
does not add construction-set or build/ink interactions, and the V/advantage
decomposition lacks a centering constraint. Strength still requires a frozen
practical-v4 evaluation; numerical tests and TD loss are not a promotion gate.

Default training is 2,000 optimizer updates per invocation, batch 32. The
fixed experiment plan is an initial stage on the 192 old games, followed by six
fixed collection/training stages of 128 new games (14,000 updates in total).
No benchmark-guided retries or tuning are permitted within that experiment.

`run_v2_training.py` runs the complete recorded schedule. It refuses an existing
plan unless explicitly given `--resume`, which verifies completed stages and
continues the same experiment after an environment interruption. Completed
games and updates are never rerun. The checked-in model is already trained;
retain its evidence and use a separate version and paths for a new experiment.

After the fixed run, generate the Java probe/map exclusions and audit the
actual episode records, accumulated replay identities, optimizer counts,
checkpoint chain and exact generated Java tensor bytes before strength testing:

```powershell
gradlew indrillV2Probe
python tools/indrill/audit_v2_training.py
```

The independent audit also verifies the exact 960 hashes in `gamesPerMap`,
the field consumed by the strength runner. Its output is write-once and makes
no convergence or strategy-strength claim. The experiment plan and final
decision are recorded in [docs/indrill-q-v2.md](../../docs/indrill-q-v2.md).

```powershell
python tools/indrill/train_q_v2.py --init-only --seed 20261004 --weights-out build/analysis/indrill-q-v2/weights-init.bin --evidence build/analysis/indrill-q-v2/init.json
python tools/indrill/train_q_v2.py --data build/analysis/indrill-q/round-1.bin.gz build/analysis/indrill-q/round-2.bin.gz build/analysis/indrill-q/round-3.bin.gz build/analysis/indrill-q/round-4.bin.gz --weights-in build/analysis/indrill-q-v2/weights-init.bin --weights-out build/analysis/indrill-q-v2/weights-0.bin --evidence build/analysis/indrill-q-v2/train-0.json
```

Random initialization is the default. An explicitly requested alternative is
`--init-only --warm-start-v1 <old-v1.bin>`, which copies v1's ten inference
tensors, initializes WV/BV to zero, and resets optimizer/target/replay state.
It is a separate initialization choice, not an automatic fallback after failure.

The BTKQ0001 dataset reader, batch padding, legal masks and deterministic greedy
maximizer are imported from v1, whose files remain unchanged. Replay features
are stored as float16 by default and calculated in float32. Every replay item
gets a stable 64-bit key from its dataset-content hash, episode, turn and player.
Checkpoints retain Adam, the exact target network, item keys and priorities.
Matching items restore their priorities even if file ordering/replay size changes;
new items receive the previous maximum priority (one for a fresh checkpoint).
Beta runs .4 to 1 per invocation. Both-player off-policy experiences are retained.

Weight binary `BTKW0002` uses the v1 int32 64/24 header and the old ten float32
big-endian tensors in the same order, followed by WV[24] and BV[1]. Generated
Java class is `btk.bot.current.IndrillV2Weights`, with the exact float bits.
Binary and matching `.npz` inference parameters must agree before resume.
Every training evidence file records source data/model/checkpoint hashes.

```powershell
python tools/indrill/test_train_q_v2.py
python tools/indrill/check_parity_v2.py --weights <frozen-v2.bin> --probe <probe.jsonl> --out <write-once-parity.json>
```

The v2 Java probe uses the v1 raw-float32 JSONL metadata plus scalar `stateValue`
(schemaVersion 1 or 2). The predeclared numerical rule is
`absoluteError <= 1e-4 + 1e-6*abs(JavaValue)` for cell advantages, region
advantages, V and the full selected Q. This accommodates sequential float32
summation at larger value magnitudes. Exact sampled greedy actions remain
mandatory. The checker records absolute and scaled errors with all input hashes.
It requires the completed 18-position manifest, the exact fixed observation
tuples, every observation's final weight hash, and matching probe/exclusion
file hashes; partial or stale probes cannot pass.
