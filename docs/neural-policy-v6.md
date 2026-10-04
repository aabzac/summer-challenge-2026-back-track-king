# neural-v6: exact resumable feature paths

Predeclared 2026-10-04 before candidate performance/process/strength results.
Baseline revision: `60e6027`. Selected local strength control remains **v09**;
the implementation and diagnostic reference is frozen **neural-v5**.

## Hypothesis and sole change

The frozen V3 profile attributed 58.89% of tall-map play time to direct feature
Dijkstra. V5 reduced allocations but did not establish a clear speedup, and failed
one strict process game. This motivates reusing repeated searches, not assuming
that local timing explains CG arena losses or that all process failures are bot CPU.

V6 keeps V5's exact features, candidate order, weights (`NeuralV2Weights`), noise,
deadline reserves, warmup and hypothetical completion income. Replace only repeated
feature-side Dijkstra searches with four lazily resumed searches for the current
source town and semantic block mask: unrestricted, block opponent, block own,
and threatened regions. Clear all caches on every prepare, including incremental
calls and retries; switching source also starts afresh. Never key by the identity
of the mutable blocked array. No strategy parameter or new weight file is needed.

Retain (cost, cell) heap order, strict relaxation, N/E/S/W expansion and the
popped-but-not-expanded target. Before searching onward, expand that pending target
even when the heap is empty. Preserve fresh public cheapestCostPath/lastCost behavior
with a separate reset search. The new shared-core helper is feature routing only;
it does not replace referee AUTOPLACE or connection tie-breaking. Old source and
merged submission identities remain unchanged; the helper ships only with V6.

## Correctness and mechanism

- Differential path tests against frozen V5: exact cell sequence, target costs,
  fresh tentative costs, arbitrary target orders/repeats, zero-cost cycles,
  pending-target chains, ties, blocked/inked source, resets and source changes.
- Full/incremental features, actions, history and interruption/recovery tests.
- Full Java tests and cgValidate after adding the core helper.
- One fixed saved-state comparison: existing 40 neural-v3 arena games, turns 0..19,
  800 states, V5 versus V6. Bit-exact complete feature rows and ample-time actions;
  same per-state RNG, alternating arm order, disposable 500-ms warmup per arm.
  Timing/allocation surrounds play only. A single shared-JVM pass is a development
  diagnostic, not a CG timing reproduction or strength test. Retain all results.
- Build exact V6 via submit, check 100000-character limit and merged decisions;
  regenerate V5, V3 and v09 separately and verify original artifact identities.

## Fixed evaluation

Freeze source, resources, protocol, runtime and exact artifact before evaluation.
No outcome-guided changes, tuning, retries or stage extensions.

1. Eight strict zero-grace process games versus v09, both seats on maps 903510176,
   904146009, 904148181 and 904147739. Run alone and finish the complete panel.
   Preserve every failure; aggregate IO latency cannot identify child CPU alone.
2. One practical-v4 comparison against **v09**, two workers, CG 486/36-ms nominal
   search budgets. Fresh pools: 300 games from seed3900000, +200 from4000000,
   +500 from4100000. Exclude all registered/training/arena/boss/diagnostic maps
   via the established shared-core identity tooling. Stop if pooled score is
   strictly below50% at300 or500; survivors play1000, final pass at>=55% only.
   A strength pass cannot override process failure.
3. A surviving process-ready candidate needs exactly40 new live-boss games against
   a valid cached v09 control, same maps/seeds/seats, score at least control's.
   Proposed cache: `build/cg-boss/neural-v3-v09-training-amended-40/plan.json`,
   arm `candidate` (the control-only refresh used that historical arm label),
   SHA256 `840f2885be5f3e059bb5696ff8686210d9befa82c902a8b45856de70cdf2f30a`.
   Its40 individually validated games scored11/40,20 in each seat, collected
   2026-10-03 20:09-20:31 UTC. The v09/fill-v1 build alias is explicit in the
   existing gate tool. V4/V5 added no boss outcomes or outcome-guided pool tuning;
   reuse is a practical regression check, not fresh confirmation. Historical
   canonical boss6767608 and league6/3/0 require a live identity/session recheck.
   Verify cache identity/lifetime before play. Keep source transmission within
   existing user authorization; arena publication/promotion and pushes are separate.
   If this stage cannot be completed, report it pending rather than claiming a pass.

Record actual results separately, update BOTS/PLAN/DEVLOG and locally commit the
completed experiment, including rejection or an unfinished live stage.
