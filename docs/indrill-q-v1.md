# indrill-q-v1 — forum-inspired Q-learning prototype

Source: [Indrill, ranked first, September 21, 2026](https://www.codingame.com/forum/t/summer-challenge-2026-feedback-strategies/208574/5).
This experiment was requested October 3. It implements a compact version of the described
principle, not the author's unpublished network, weights or full training pipeline.

The earlier replay study's inference that sub-millisecond decisions meant a heuristic was
incorrect. The author describes Q-learning, an algorithm derived from Rainbow/BTR, a
size-independent grid network inspired by KataGo, and greedy inference without search.

## Implemented observation and action model

`btk.bot.current` is independent of EvalBot/RolloutBot and the earlier behaviour-cloned
`neural-v1`. It receives 64 float channels per cell. Thirty-four describe ownership, terrain,
town protection, region boundaries, neighboring cells, coordinates, time, accumulated scores
and exact current income. Thirty describe oriented desired connections:

- Standard entry costs: free tracks/towns, terrain paint costs on empty cells.
- Alternative costs: standard cost plus `INDRILL_INK_PENALTY * instability` (default 2),
  including occupied cells; town regions have no risk penalty.
- Best cells satisfy `distance(A,B) + distance(B,C) = distance(A,C)`.
- Good cells satisfy the same expression with slack at most the cost removed by building B.
  On the risk map building removes paint cost, not the disruption penalty.
- Path cells belong to one optimal simple path chosen by N/E/S/W DFS, including zero-cost
  rail components.
- Each group aggregates count, hypothetical completed-route income difference, minimum
  stability, stability times income, and minimum all-in completion urgency. Urgency is the
  `ceil(paint/3)` lower bound; terrain packing can need extra turns.

Distances and tactical paths live in additive `btk.core.TacticalPathing`. A hypothetical
completed route is valued through the core's canonical scored path and
`SearchState.connectionIncomeDifference`, not its tactical path. Original `Pathing`, `Rules`
and `SearchState` are unchanged, preserving retained historical engine identities.

The network has 3,362 float32 parameters: a 64-to-24 cell layer, then a 24-channel layer
combining the cell, the mean of its four neighbors and the mean of the board. Two heads share
this representation. The ink head averages cells by region. The build head scores a **whole
set** as the sum of its selected cell values. An exact budget-three knapsack maximizer checks
three plains, one river plus one plain, and one mountain. Empty actions have fixed Q=0;
unused paint is allowed. Inference has no random noise, game rollouts or hand-written move
valuation. It emits explicit placements and at most one disruption.

The fixed zero value of a full WAIT means that such transitions generate no parameter
gradient and the greedy successor value cannot be negative. This restricted Q approximation
lacks a state-value baseline; it is a material limitation of this first experiment.

This shallow neighbor/board-mean model is a deliberately smaller approximation to the grid
architecture in the post. Its additive build head cannot learn interactions among the chosen
cells or between the build and ink actions; the post does not disclose how the author
parameterized his joint build value. It is not a claim to reproduce KataGo or full BTR.

## Outcome-driven training

`IndrillCollect` generates games with the authentic map generator and resolves every turn
using `Rules`. Exploration exists only in that offline tool. It exports complete episodes,
both player perspectives, legal masks, chosen full actions and rewards. No leader labels are
used. The opponent mix is fixed: half self-play, one quarter frozen v09, one quarter neural-v1.
Both players' transitions are retained; Q-learning is off-policy.
The fixed collection order places v09 in seat 0 and neural-v1 in seat 1; self-play includes
both perspectives. This training distribution is not a matched strength comparison.

The Python numpy trainer uses Double DQN, three-step returns, prioritized replay with
importance weights, a target network, Huber loss, Adam and gradient clipping. Online weights
choose the successor's entire joint action; target weights evaluate that same action. The
shared joint TD error updates both heads and the cell/context layers. Replays use float16
storage, with float32 calculations; exported Java weights preserve the exact float32 bits.

Reward is `delta(projected ink-all score difference)/100 + terminal win/draw/loss`.
`btk.core.InkProjection` instantly inks every **legally disruptable** region, freezes the
remaining board and projects its exact income to the turn cap only if the game can continue.
Earned scores persist. This is our explicit interpretation of the author's briefly stated
ink-all reward, not a new final-scoring rule or his undisclosed formula. With gamma .99,
the undiscounted potential difference defines a shaped training objective; no claim of
discounted policy invariance is made.

Predeclared training schedule, before strength evaluation:

1. Initialize seed 20261003.
2. Collect four blocks of 48 new games at seeds 2000000, 2001000, 2002000, 2003000;
   exploration .25, .20, .15, .10 respectively.
3. After each block train 400 minibatches of eight transitions on all collected blocks.
   Learning rate .0003, gamma .99, n-step 3, Huber 1, gradient norm cap 10,
   target update every 100 steps, priority alpha .6, beta .4-to-1 each round.
4. Continue optimizer and target checkpoints; priorities restart per round. Freeze after
   exactly 192 games and 1,600 optimizer updates. No benchmark-guided training or retries.

This small training budget establishes a runnable experiment. It is not remotely comparable
to the author's year of pipeline development and undisclosed training scale. Lower TD loss
is not evidence of game strength.

## Reproduction and verification

See `tools/indrill/README.md` for binary contracts and training commands. Java data collection:

```powershell
.\gradlew.bat indrillCollect -Pgames=48 -Pseed=2000000 "-Pweights=build/analysis/indrill-q/weights-0.bin" "-Pout=build/analysis/indrill-q/round-1.bin.gz" "-Pepsilon=0.25" -Popponent=mixed
```

Generated `IndrillWeights.java` must never be hand edited. Retraining produces a new weight
artifact and a separately named, freshly benchmarked experiment. The frozen candidate is
selectable as `indrill-q-v1`; it does not change the selected v09 or `current`.

```powershell
.\gradlew.bat submit -PsubmitVersion=indrill-q-v1 "-PsubmitOut=build/analysis/indrill-q-v1/submission"
.\gradlew.bat procCheck -Pbot=v09 -Pgames=2 "-Pmap=replays/cg/903510176.json" "-PsubmitOut=build/analysis/indrill-q-v1/submission" "-Pout=build/analysis/indrill-q-v1/process-check-1.json" -PgraceMs=0
python tools/run_practical_v4.py --spec indrill-q-v1 --control v09 --name indrill-q-v1 --seed 1200000
```

Process panel: exactly two games on each of 903510176, boss 904146009, 904148181, 904147739,
both seats, strict zero grace, no simultaneous CPU-heavy work. Practical-v4 is one comparison
on fresh disjoint maps: 300/+200/+500 games, two workers, futility stops below pooled 50%
at 300/500, final local pass at 55% of 1000. No early pass or tuning.

The live 40-game boss stage requires authorization and the outstanding control-cache refresh
decision after prior outcome-guided development. A local failure ends strength evaluation.
The experiment's final evidence and verdict are recorded in BOTS/DEVLOG and
`docs/evidence/indrill-q-v1.json`.

## Result (October 3)

**Rejected locally: 0 wins, 300 losses, 0 draws, zero execution errors.** The fixed v4
runner stopped at `STOP_FUTILITY_300`. No additional local blocks or boss games ran;
v09 remains selected. All 13,040 prior map hashes, including the 192 training maps and
three numerical-probe maps, were excluded. Training weights froze at SHA256
`6ebd02d14ab578684de6876d6399398500d9ecde4aa31c7457cfb7e628b1fd53`.

The eight strict process games all passed timing/error checks, although all were lost:
first response <=583.176 ms, later <=18.1987 ms. The isolated 81,823-character artifact
has source SHA256 `c186d548aef00b8a6f777f7a9e50742b93a8ecf5aadb9e68c4932c9e4aebb8db`,
build ID `a3fbee955554ddb914fa58c41a05107eb7e8b1adda4476b91e81d5ae93b3ccd3`.
379 Java tests, 126 Python tools tests and five numpy trainer tests pass; 292 arena
replays reproduce exactly. Generated Java weight bytes equal the frozen training binary.
Both heads chose the same sampled actions in Python and Java on all 18 observations;
maximum absolute Q errors are 0.000031 (build) and 0.000016 (ink).

This experiment establishes the implementation and measurements, not a successful RL bot.
The final training residuals remain large; neither convergence nor adequate action-value
capacity was established. A next experiment should first address the zero-baseline value
restriction and interactions within a construction set, then predeclare a substantially
larger training budget. Changing any of those requires new weights and a new evaluation.
