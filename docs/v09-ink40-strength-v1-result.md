# Ink40 local strength with the timing prerequisite waived

2026-10-18. **NOT_PASSED: 518 wins, 438 losses and 44 draws in 1,000 games,
54.0% points**, below the predeclared 55% local criterion. All three blocks
completed once. The timing prerequisite was waived at the user's explicit
request; the old timing verdict remains NOT_READY. Keep the optimization OFF.

## Scope and unchanged implementation

The [new protocol](v09-ink40-strength-v1.md) removes the timing prerequisite
for this local comparison. It preserves the completed process evidence:
16 games, 1,353 later responses, two timeouts at 50.2949/52.1667 ms. No process
game is repeated and no timeout is erased or reclassified. This local result
cannot establish external 50 ms readiness or authorize promotion.

- A: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40,USE_INCREMENTAL_INK_REFRESH=true`.
- B: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40`.

Only incremental refresh after hypothetical ink differs. Both native search
allowances are 486 ms first and 40 ms later, including after seat swaps.
Fast-path optimization and missed-deadline correction remain OFF. There is
no production source, strategy, guard, search-limit or default change.

Reuse the verified pre-fast-path native runtime and exact ink40 artifacts,
without rebuilding. Candidate source is 91,714 characters, SHA-256
`aff2dc9bf48d2f9a141012c3d47fc079bb9af9354cdfa846ef40832804db75a5`;
control 90,641 characters, SHA-256
`9510ff174e76297736144193a4c519f3728eac067d09614bb65e2f0ecd1b88d9`.
Engine 292/292 and ample 524 semantic parity remain inherited exact evidence.
The prior focused 248/249 catalog-pin failure stays disclosed; no new full
test-suite or new 40 ms supplementary guard-success claim is made. The retained
turn-40 attack and winning-closure checks used 36 ms, NOISE=0, batching OFF and
deadline correction ON; they do not establish a new combined-flags claim.

## Prospective setup

The new driver reuses the old map generation, report checks and v4 arithmetic,
changing only the expressly waived prerequisite and local-only metadata.
It still requires complete audited evidence, only the documented TIMEOUT
errors, no missing replies, exact artifacts, parameters and runtime hashes.
All **35 new synthetic tests pass**, without Java or bot calls. Older drivers,
their tests and all published ink40 timing evidence remain unchanged.

All 500 proposed maps are distinct and absent from 26,124 prior played/reserved
identities, including all 500 fast-path maps. Seeds 301018000/301118000/301218000
use 150/100/250 paired maps. The freeze pins 4,831 workspace files and 7 runtimes,
the user authorization, three prospective pools and the prior ink40 publication.
The preparation commit is 8764233; the policy runtime itself is unchanged.

The shared uninstrumented BenchmarkRunner uses two workers and fresh bots.
Complete blocks 300/+200/+500 once; stop strictly below 50% at pooled 300 or 500.
No early pass; survivors complete 1,000 and meet the numerical local criterion
only at >=55%. No tuning, replacement games, retries or pooling prior results.
Native timing outliers are retained as diagnostics and do not block this run.

## Result

- Block 1: 154W / 134L / 12D in 300 games, **53.3333%**. Continue.
- Block 2: 114W / 78L / 8D in 200 games, **59.0%**; pooled 500 **55.6%**.
  Continue to the full sample; this interim cannot pass the candidate.
- Block 3: 250W / 226L / 24D in 500 games, **52.4%**.
- Pooled: **518W / 438L / 44D, 54.0%**, local **NOT_PASSED**.
  Candidate as P0: 261W / 217L / 22D, **54.4%**;
  as P1: 257W / 221L / 22D, **53.6%**. All 500 maps played in both seats.
- Zero game errors in either arm. No discarded, replaced or repeated games.
  Native slow-move counters A/B: **8/6**, maximum reported call durations
  **65/67 ms**. These integer-ms maxima include opening calls and do not
  measure full process response time. Timing remained diagnostic throughout.

Independent reconciliation **PASS, 41,876 checks**: raw scores, seat counts,
all three stopping decisions, actual 486/40 budgets, unchanged parameters and
runtime, all 500 fresh maps, 4,831 frozen files, 7 runtimes and 427 prior
artifacts/publications. The audit validates the evidence and failed local
criterion; it does not reverse the prior timing failure.

The earlier 36 ms comparison scored 52.7% on a different fresh map sample.
The present 54.0% is descriptively 1.3 percentage points higher, but these
separate comparisons do not establish that increasing the budget caused the
difference. A modest positive effect remains possible; the required 55%
practical strength criterion was not met.

## Decision and retained evidence

Keep incremental ink OFF, default 36 ms, all other experimental flags OFF and
selected v09 unchanged. No further games or boss stage follow this local
failure. No production edits, new process games, live boss calls, upload,
promotion or push. Preserve the old NOT_READY publication and archive this
explicitly authorized local comparison separately.

[Evidence summary](evidence/v09-ink40-strength-v1.json),
[archive index](evidence/v09-ink40-strength-v1-artifacts.json),
[raw evidence archive](evidence/v09-ink40-strength-v1-artifacts.zip).
