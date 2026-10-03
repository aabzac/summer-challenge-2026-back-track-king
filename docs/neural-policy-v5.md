# neural-v5: exact hypothetical completion income

Predeclared 2026-10-04 before candidate performance/process/strength results.
Baseline: `d5c0463`. The prior V4 temperature experiment is rejected and is not
the weight source for this experiment. Selected local control remains v09.

## Hypothesis and one change

On the fixed 40 neural-v3 arena replays, early paint usage is low on tall maps.
The previous 800-state local probe does not reproduce that deficit, but feature
preparation accounts for 80.65% of measured time on tall maps. This motivates
profiling, not a claim that timing explains the arena results.

First profile the frozen V3 preparation on those same 800 states with isolated
instrumentation. Keep its calls, allocations and nested timing evidence distinct
from the subsequent uninstrumented candidate comparison.

The completed baseline profile confirms a bounded target: on tall maps, direct
feature Dijkstra accounts for58.89% of instrumented play time, completionGain
for16.15%, and its connection updates for13.61%. Those connection updates allocate
95.0% of measured play bytes. V5 targets allocation and a minority time component;
it does not claim to remove the majority pathfinding cost. Dijkstra reuse remains
a separate future experiment, not an outcome-guided addition to V5.

V5 keeps the exact `NeuralV2Weights` used by V3, every feature formula, candidate
order, closing rule, noise parameter, deadline reserve and warmup behavior.
Replace only `completionGain`'s hypothetical whole-state copy, path materialization
and income scan with an additive `btk.core.HypotheticalIncome` helper. It applies
the same buildable route cells to a scratch ownership view, performs canonical
connection BFS/descent, and sums both players' income across all desired pairs.
It retains N/E/S/W tie-breaking and does not mutate the source state.

Do not change feature Dijkstra, single-cell build simulation, sabotage simulation,
deadlines, inference weights or training. Old neural/core source files stay frozen;
the new core helper ships only with V5, preserving earlier submission identities.
No new tunable parameter is needed for this exact implementation substitution.

## Verification before freezing the candidate

- Differential core tests against old `Rules.updateConnections + incomePerTurn`,
  including small exhaustive boards, ties, directed/duplicate/self pairs, neutral
  ownership, blocked/buildable cells, scratch reuse and source immutability.
- Full/incremental feature and candidate order parity, noisy action/history parity,
  and interruption/recovery tests against V3. A completed call must be bit exact;
  wall-clock interrupted decisions may differ as part of this runtime version.
- Full Java tests and `cgValidate` after the additive core change.
- One fixed saved-state comparison: all 40 already retained arena games, turns
  0..19, both implementations on the actual board prefixes. Compare complete
  feature rows and actions, then uninstrumented ample-time call cost with a common
  per-state RNG seed, alternating arm order and a documented disposable warmup.
  Local timing is diagnostic, not a reproduction of CG CPU or RNG history.
- Preserve old frozen source hashes and regenerate old V3/v09 into separate output
  directories for exact identity checks. Build V5 through `submit`; enforce the
  100,000-character limit and verify the merged program's decisions.

## Fixed evaluation after source/artifact freeze

- Exactly eight strict zero-grace process games versus v09, both seats on maps
  903510176, 904146009, 904148181 and 904147739. Run alone, complete the panel,
  retain failures; no process retries or reserve tuning.
- One practical-v4 comparison against selected **v09**, with two workers and
  CG 486/36-ms search budgets. Pools: 300 games starting at seed 3600000,
  +200 at 3700000, +500 at 3800000. Exclude all previously registered, training,
  boss, arena and diagnostic maps using the existing shared-core map identity.
  Stop below pooled 50% at 300/500; only >=55% over all 1000 passes. This evaluates
  the candidate independently; historical V3 strength is not transferred to V5.
- No outcome-guided source/parameter adjustments, stage extensions or reruns.
  A process failure remains a readiness failure even if local strength passes.
- A surviving, process-ready candidate still needs exactly 40 new boss games on
  a valid cached v09 control pool before a combined pass. Check the cache identity,
  lifetime and any needed refresh before starting; pin the plan and artifact.
  Source transmission and any new live series must stay within user authorization.
  Arena publication, promotion and pushes are separate actions.

Retain results separately from this predeclared protocol. Record and commit the
actual outcome, including rejection or an unfinished live stage.
