# Indrill-principle Q-learning prototype

`train_q.py` learns the joint placement/disruption action from Java-exported local
games, using numpy only. It uses actual rewards rather than leader imitation.
The rules, legal moves, feature construction and all game outcomes belong to the
Java engine; Python does not implement scoring or pathfinding.

The compact model is `64 -> 24 ReLU`, then one `24 -> 24 ReLU` message layer
combining each cell, its four-neighbor mean and the whole-board mean. Both action
heads share the resulting cell representation. Placement value is the sum of its
distinct selected cell values; disruption value uses the mean over its region.
The exact additive knapsack maximizer has paint budget 3. The empty build and
empty disruption each have fixed value zero. This is a prototype simplification,
so it is not a full reproduction of the author's architecture or training scale.
Consequently the greedy joint bootstrap is nonnegative: it can always choose
both empty actions. A transition that chooses both empty actions sends no
gradient through the model, even if its TD target is nonzero. The representation
therefore cannot learn an independent value for waiting or fully represent a
negative value for the best available action. This limitation stays frozen for
this experiment; changing the value baseline would be a separate version/bench.

Training uses Double DQN (online action selection, target action evaluation),
three-step returns, prioritized replay, importance weights, Huber loss, Adam and
gradient clipping. A single joint TD loss updates both heads and the shared
representation. State storage defaults to float16 to reduce replay memory;
computation and exported weights are float32. Use `--float32-replay` if needed.
Both players' experiences are retained. Terminal returns never bootstrap or
cross an episode boundary.

Bootstrap before the first Java self-play export:

```powershell
python tools/indrill/train_q.py --init-only --weights-out build/analysis/indrill-q/weights-0.bin
```

An example training round after collecting `round-1.bin.gz`:

```powershell
python tools/indrill/train_q.py --data build/analysis/indrill-q/round-1.bin.gz --weights-in build/analysis/indrill-q/weights-0.bin --weights-out build/analysis/indrill-q/weights-1.bin --steps 400 --batch 8 --evidence build/analysis/indrill-q/round-1-training.json
```

Subsequent rounds pass the previous weight binary and their explicitly selected
replay files to `--data` (multiple paths allowed). The matching `.npz` checkpoint
retains Adam and target weights. Priorities restart at one per training invocation.
The default target period is 100 optimizer updates, gamma .99, n-step 3,
priority alpha .6 and beta .4 increasing to 1 within the invocation. Model size
is 3,362 float parameters. Loss is a training diagnostic, not a strength result;
arena qualification must use the separately frozen benchmark policy.

Generated files are the Java-readable `.bin`, matching `.npz` checkpoint,
`btk.bot.current.IndrillWeights.java`, and an evidence JSON with source hashes,
settings and training diagnostics. Do not edit learned weights by hand.
Checks: `python tools/indrill/test_train_q.py`.

Before strength evaluation, compare the frozen model's Java and numpy inference
on the Java `IndrillProbe` observations:

```powershell
python tools/indrill/check_parity.py --weights build/analysis/indrill-q/weights-4.bin --probe build/analysis/indrill-q/parity/probe.jsonl --out build/analysis/indrill-q/parity/evidence.json
```

This reads exact float32 features, verifies topology/region sizes and matching
binary/checkpoint parameters, requires both Q heads to agree within `1e-4`
absolute error and all sampled greedy actions to agree exactly. Evidence is
write-once and records input hashes. It establishes sampled numerical parity;
floating-point values near action ties can differ on other positions.

## Dataset contract

All numbers are big endian. The file may be gzip compressed (`.gz`). The header
is the eight ASCII bytes `BTKQ0001`, int32 feature count (64), int32 game count.
For each game:

1. int32 episode ID, width, height, region count.
2. int32 cell region IDs, `width*height` entries in row-major order.
3. uint8 terrain paint costs, one per cell, each 1, 2 or 3.
4. int32 state count (turn count plus the terminal board).
5. For each state: float32 player-0 features then player-1 features, each
   `[cell][64]`; uint8 legal-build flags per cell; uint8 legal-disruption flags
   per region; action 0 then action 1, each int32 disruption ID (-1 for none),
   int32 selected-cell count, three int32 cells (unused slots -1); float32
   reward 0 then reward 1; one Java boolean done byte.

Each nonterminal record describes the action and reward of the transition into
the following state. `done` is true on exactly the penultimate transition and
the terminal feature record. The terminal record has no action and zero rewards.
Cell indices are `y*width+x`; region means include every cell in that region.

## Weight contract

The Java-readable binary starts with ASCII `BTKW0001`, int32 input count 64,
int32 hidden count 24. Big-endian float32 parameters follow in this exact order:
`W0[64,24], B0[24], WS[24,24], WN[24,24], WG[24,24], B1[24],
WB[24], WI[24], BB[1], BI[1]`. Matrices are flattened with the input dimension
first. Neighbor means omit out-of-board cells and exclude self. The generated
Java arrays decode the same exact float bits from base64, avoiding inference
quantization differences.
