# Incremental ink refresh: 36 ms strength result

2026-10-18. **NOT_PASSED: 497 wins, 443 losses, 60 draws in 1,000 games,
52.7% points**, below the predeclared 55% local threshold. Keep incremental
refresh OFF. The speed improvement from the preceding diagnostic did not
produce enough strength improvement to pass this experiment. This does not
establish that the optimization has zero effect.

Default 36 ms, batching OFF, incremental refresh OFF, missed-deadline correction
OFF and selected v09 remain unchanged. No new boss games, process games,
arena upload, promotion or push were performed. Local failure ends this
evaluation; no extension or boss result can rescue it.

## Controlled comparison

The [prospective protocol](v09-ink-strength-v1.md) compares the same batched36
base with exactly one implementation flag differing:

- Candidate A: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,USE_INCREMENTAL_INK_REFRESH=true`.
- Control B: `current?NOISE=0.1,USE_BATCHED_REFRESH=true`.

All other effective parameters, strategy, safeguards, search limits, RNG and
timer settings match. Both arms receive 486 ms on the first turn and 36 ms
later. The shared, uninstrumented BenchmarkRunner uses two workers and a fresh
bot instance per game. This isolates incremental refresh on the requested
batched base; it is not a comparison against selected production v09.

All 500 map identities were generated through the shared core and frozen
before play. They are distinct and absent from 25,124 prior played/reserved
map identities; registered seed intervals were also checked. Each map is
played in both seats. All 3,819 frozen workspace files, seven external runtime
files and the exact classpath inventory match before and after each block;
105 selected/prior artifact files remain unchanged. Implementation commit:
`04399a83731d9d3ffdb7040163cccff66fe06edc`.

The exact candidate has 91,202 characters, source SHA-256
`6217083e6319f69a29619f83c7de43d99876153b1dc9d043ca3376f5bccf8124`.
Control has 90,129 characters, source SHA-256
`2cb3a6a254fd8f37e0c9f07e2aa36565f4d61027ee44f508a5f64a93d547d61a`.
The already validated runtime and artifacts were reused without rebuilding.

## Fixed stages and decision

All three stages completed once, without retries, replacements or tuning:

- Block 1, 300 games, base seed 281018000: **157W / 125L / 18D = 55.3333%**.
  The cumulative score is above the 50% futility threshold; continue.
- Block 2, 200 games, base seed 281118000: **90W / 90L / 20D = 50.0%**.
  Cumulative **53.2% after 500**; continue, with no early pass.
- Block 3, 500 games, base seed 281218000: **250W / 228L / 22D = 52.2%**.
  Cumulative **52.7% after 1,000**; final **NOT_PASSED**.

Wins count one point, draws one half: 527 points, versus 550 required. Both
arms have zero reported game errors. Candidate as P0: 249W / 221L / 30D,
52.8%; as P1: 248W / 222L / 30D, 52.6%.

The unchanged practical-v4 checker supplies the stopping and final decision.
A descriptive paired-map Hoeffding 95% interval is 46.63% to 58.77%; it is
not adjusted for interim selection, is not an overall 95% guarantee and has
no role in the fixed threshold decision.

## Timing and inherited readiness

Across 90,370 native calls per arm, mean recorded time is **16.13 ms for A
versus 17.73 ms for B**. These are integer-ms measurements on diverging game
trajectories, not an equal-work speed test. The earlier fixed-state diagnostic
measured 15.64% less time for identical work and 14.98% more comparisons at
36 ms; those figures do not replace the complete-game strength result.

Retain all four native calls reported above 50 ms:

- A, block 1, seed 281018031, candidate P1: 53 ms.
- B, block 1, seed 281018032, candidate P0: 51 ms.
- A, block 1, seed 281018074, candidate P1: 52 ms.
- A, block 3, seed 281218234, candidate P0: 63 ms.

These per-game counters include first-turn calls and do not identify the turn.
They exclude protocol IO and cannot establish full-response deadline failures
or latency percentiles. No outlier was discarded or replayed.

Hash verification preserves the preceding exact-artifact readiness evidence:
292/292 exact engine replays, all 524 ample-search positions identical, and
eight strict process games with zero errors/timeouts/overruns/missing replies.
Their 792 later full responses have maximum 46.5054 ms. No new process sample
was run for this unchanged artifact, and that finite sample is not a guarantee
for every future response.

The prior focused suite passed 248/249 tests, including all ten new tests;
the historical exp-income-guard catalog-pin failure remains documented. This
is not a full-suite-green claim. The supplementary turn-40 attack and six
winning-closure controls used the historical nonbatched deadline-ON recipe.
The all-three-flags recipe remains blocked at 100,447 characters; this does
not affect the main 91,202-character, deadline-OFF candidate.

The new driver passes 21 synthetic tests without bot calls. Its first attempt
retains four Windows TEMP permission errors and 17 passes. The unchanged
tests then pass with TEMP redirected into the workspace; both logs are saved.

## Audit and retained evidence

Independent audit **PASS, 21,584 checks**: raw score/winner consistency, pooled
and seat counts, every map identity, parameters, budgets, stopping decisions,
frozen runtime, inherited readiness and artifact preservation. No game-domain
measurement was repeated for the audit or archive.

The [boss-cache assessment](v09-ink-strength-v1-boss-cache-note.md) is read-only.
The correct control would be batched36, not v09; the old exposed/amended pool
is unsuitable for fresh confirmation. Because this local gate failed, no
boss stage is started. Earlier batching and budget verdicts remain unchanged.

Raw reports, decisions, logs, preflight map identities, freeze and independent
audit are retained in the [evidence summary](evidence/v09-ink-strength-v1.json)
and [archive index](evidence/v09-ink-strength-v1-artifacts.json). The archive
contains this experiment's new evidence and references unchanged prior
artifacts by hash; unrelated workspace experiments are excluded.
