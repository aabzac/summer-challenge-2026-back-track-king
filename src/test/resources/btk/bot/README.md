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
benchmark result and why the experiment is not enabled by default.
