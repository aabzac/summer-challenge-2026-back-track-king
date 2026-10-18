# Incremental ink refresh: prospective strength comparison

Declared 2026-10-18, before new games. Implementation commit: 04399a8.

The user requests the same 36 ms version with and without incremental ink
refresh. Candidate A is `current?NOISE=0.1,USE_BATCHED_REFRESH=true,USE_INCREMENTAL_INK_REFRESH=true`;
control B is `current?NOISE=0.1,USE_BATCHED_REFRESH=true`. All effective parameters
must match except that one default-off implementation flag. Keep deadline
correction OFF and all strategy, guards, search caps, RNG and timer settings.

Use the already tested native runtime and exact generated artifacts from
v09-ink-refresh-v1. Source hashes are 6217083e...bccf8124 for A and
2cb3a6a2...d547d61a for B; record full identities in the freeze. Verify the prior
292/292 engine check, 524-state exact ample parity, eight strict process games
and unchanged source/classes/runtime by hash. Reuse that exact-artifact readiness
evidence rather than replay completed measurements. Preserve its known historical
catalog test failure and supplementary guard packaging limitation.

Run the existing shared BenchmarkRunner, uninstrumented, two workers, nominal
CG allowances 486 ms first / 36 ms later for BOTH arms. Fresh bot instances
per game, both player seats on every map. This is in-process strength timing,
not a substitute for the prior full-response 1000/50 ms process sample.

Use the practical-v4 LOCAL schedule unchanged: block1 300 games / 150 maps,
block2 +200 / 100, block3 +500 / 250. Base seed 281018000, offsets 0, 100000,
200000. Pre-register all three pools and hash all 500 maps using the shared
core before play. Exclude all prior played and reserved map identities, saved
CG maps and registered seed intervals. No replacements, retries, retuning,
changing limits or extending stages after seeing outcomes. Complete each
started block. Preserve errors and timing outliers.

Wins count 1, draws 0.5. At cumulative 300 and 500, stop only if pooled score
is strictly below 50%; no early pass. Survivors complete 1000, with a single
local pass threshold of at least 55%. Use the unchanged practical_gate_v4
reader/checker. Independently recount raw results, score/winner consistency,
seats, maps, parameters, actual budgets, source identities and stopping.
Confidence intervals are descriptive and do not alter the decision.

This isolates incremental refresh on the requested batched36 base, which is
not the selected production v09. A local failure means leave the optimization
OFF. A local pass supports continuing to the required boss confirmation; it
does not authorize changing either default flag or overturn earlier batching
verdicts. The correct boss control is batched36, not v09. The old batched40-map
pool was exposed to outcome-guided development and contains an amended retry,
so the standard cache reader rejects it: a future boss confirmation requires
a fresh exact-control 40-game pool followed by 40 matched candidate games.
No live boss calls, arena upload, default changes, promotion or push are part
of this local comparison. Record the local decision and remaining gate clearly.

Freeze all inputs before the first game; verify before and after each complete
block. Keep new outputs write-once and preserve failures. Archive raw reports,
logs, decisions, map audit, runtime identities and an independent audit; update
BOTS/PLAN/DEVLOG and commit the completed experiment locally.
