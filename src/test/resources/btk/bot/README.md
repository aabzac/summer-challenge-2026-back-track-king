# Sabotage regression states

`sabotage-starvation.json` contains map seeds and both players' recorded stdout for turns 0..39
of CG games 903509641 and 903508669. In both, aabzac is P1 and ChaosCat is P0. The original
full games passed `cgValidate` before extraction. These are compact replay prefixes, not full
CG downloads and not hypothetical continuations. `MapGenerator` recreates the initial map;
`Rules.resolve` reconstructs the state just before turn 40.

At that state the positive sabotage targets are regions 46 and 15 respectively. A deterministic
unit test expires only the build phase, leaving ample time for sabotage; it does not assert
millisecond performance. Separate single-thread probes at the real 36-ms search allowance
reproduced starvation in v04 and recovered sabotage with a 6-ms reserve. See DEVLOG for the
initial inconclusive screen and the subsequent 1000-game confirmation that promoted the
6-ms reserve as v05. The default is now 6 ms; 0 remains available as an ablation.

`build-income-guard.json` contains compact prefixes for Reh934 (903583608 t27), fsicre
(903584040 t52), and the older DiogoGiassi game (903507615 t46). Expected deltas are for
recorded own placements ALONE on the pre-turn state: -26, -34, -38 respectively. Neither
player's current sabotage nor the opponent's current placements are applied in this test.
This deliberately differs from the simultaneous-turn counterfactual in `cgAnalyze`.

`BuildDirectionTest` also uses the first two prefixes to check that building from the other
end delays the harmful shortcut. It does not claim to fix the third position. Synthetic tests
cover both player perspectives, emitted placement order, cost skipping, contested-last priority,
ties, full-route equivalence and scratch-state isolation. Both experimental flags remain off
unless independently promoted; the direction experiment is run with the hard veto disabled.
