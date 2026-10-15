# v09 batched refresh: boss result

**Decision: NOT_PASSED. Keep batching OFF and v09 selected.**

The candidate completes exactly 40 new boss games: **10 wins, 30 losses,
no draws (10/40 points)**. The refreshed v09 control scores **11 wins,
29 losses, no draws (11/40 points)** on the same 40 maps, seeds and seats.
The required candidate score is at least the control score; 10 < 11 fails.
The earlier local result remains **535W/402L/63D over 1000 games, 56.65%**.
That local pass cannot override the failed boss stage.
On paired maps, the candidate improves 6 outcomes, worsens 7 and ties 27
(4 wins by both, 23 losses by both). This is an outcome comparison, not an
attribution of individual decisions or the unknown timing outlier.

## Why the control was refreshed

The October 4 cache is technically intact: 40 unique games/maps, 20 games per
seat, exact v09 build and full parameters, 15W/25L. Its age alone does not make
it invalid. All 40 games were subsequently used in continuation diagnostics,
and this optimization followed that development cycle. Under the adopted cache
policy it was unsuitable for this promotion decision without a refresh.

Before any new game, freeze schedule seed 2026101509 and both exact builds.
The shared-core map check finds zero overlap with **23,544 previous maps**;
254 saved map sources were scanned. Collect exactly 40 fresh v09 games, verify
that cache, then run exactly 40 candidate games on its unchanged schedule.
Each arm has 20 P0 and 20 P1 games. No tuning, extensions, replacements, retries
or protocol amendments occur. The original cache remains preserved.

## Exact builds and validation

- Candidate `current?NOISE=0.1,USE_BATCHED_REFRESH=true`, source SHA-256
  `2cb3a6a254fd8f37e0c9f07e2aa36565f4d61027ee44f508a5f64a93d547d61a`,
  90,129 characters. Control `v09?NOISE=0.1`, source SHA-256
  `aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac`.
- Both full parameter maps, source and executable classes match the completed
  local comparison. The missed-deadline correction remains OFF. The preserved
  exact-artifact process panels pass 8 games per arm; no new process trial or
  local strength rerun was used to change the earlier result.
- **All 80 new canonical replays reproduce exactly** through the shared engine.
  Actual maps equal the prospectively generated maps. Every game has matching
  seed, seat, build marker, retained source, boss identity and league.
- Zero technical failures, uncertain requests or replacements. Boss 4 is
  agent **6767608**, league divisions **6/3/0**. Control collection:
  2026-10-15 20:07:05–20:29:59 UTC; candidate: 20:31:35–20:54:56 UTC.
- Independent audit verifies all **2,912 previously frozen files**, seven runtime
  components and **85 pre-live files** unchanged, and applies the unchanged
  practical-v4 checker: local PASS_1000, boss NOT_PASSED, combined NOT_PASSED.
- The IDE source was backed up privately before play. Final read-only lookup
  confirms the exact tested candidate remains in the IDE. No arena publication,
  default change, strategy promotion or push was performed.

One preparation-only guard initially rejected command-intent JSON mistakenly
included in a process-report glob. The validator correctly stopped before any
network call or game. The helper was changed to the four explicit report names;
the failure and repair are retained. No measured attempt was repeated.

## Interpretation

The optimization still has demonstrated equal-work speed gains and a passing
local strength result. This 40-game boss comparison fails the project's practical
selection rule by one point. That small difference is not proof that batching
is intrinsically weaker; it is sufficient to reject promotion under the fixed
rule. Do not extend this series or replace outcomes to obtain a pass.

The previous 75-ms native-call outlier remains unresolved: its record has no turn
number and the counter includes first turns. The strict process sample and this
clean boss sample do not establish a universal 50-ms guarantee. An unchanged
public boss ID also does not prove an unchanged hidden implementation or runtime.

Preserve the optional implementation for future separately designed work. The
fresh v09 cache is retained with its individual outcomes; suitability must still
be assessed before any later use, especially after outcome-guided development.

[Prospective protocol](v09-batched-refresh-boss-v1.md),
[machine-readable result](evidence/v09-batched-refresh-boss-v1.json),
[archive index](evidence/v09-batched-refresh-boss-v1-artifacts.json),
[source and raw evidence archive](evidence/v09-batched-refresh-boss-v1-artifacts.zip).
