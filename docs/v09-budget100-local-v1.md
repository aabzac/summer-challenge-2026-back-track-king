# v09 budget100 local sensitivity experiment

Prospective protocol, 2026-10-16. The user requests a local 100 ms run to learn
whether additional time materially improves the same strategy. This is an
explicit local budget diagnostic, outside the arena's 50 ms response limit.
It cannot promote a 100 ms submission or reverse earlier experiment verdicts.

## Fixed comparison

Both arms use the identical native recipe
`current?NOISE=0.1,USE_BATCHED_REFRESH=true`, with all effective strategy
parameters equal and missed-deadline correction OFF. The existing recipe's
`TURN_BUDGET_MS` remains at its default 36. A separately compiled local harness
passes the experimental allowance directly to `Referee.playerTurnBudgetMs`:

- A: first search 486 ms; later searches 100 ms.
- B: first search 486 ms; later searches 36 ms.

The allowance follows A/B when seats swap. Each map is played twice with fresh
bot instances. Use two worker threads and run no other heavy jobs concurrently.
The source of `btk.core`, all strategy packages, Params, MainBot, CGRunner,
the standard benchmark and all existing submission artifacts remain unchanged.
No protocol parsing or submission JIT warm-up is emulated by the native runner.

## Schedule and stopping

Preflight 500 fresh, distinct maps, excluding all known previous local map
identities and previously reserved maps, including unplayed reservations.
Check generated seed intervals against all existing pool registrations.
The proposed new base is 261616000, with blocks at offsets 0/100000/200000;
freeze the validated exact seeds, hashes and pool manifests before play.

Use the project's fixed local schedule: 300 games, then 200, then 500.
Stop for futility if pooled score is strictly below 50% at cumulative 300
or 500; otherwise complete all 1,000 games. Draws count as half a win. No
tuning, outcome-selected reruns, extensions or replacement games. Preserve
technical failures and stop further blocks on invalid or incomplete evidence.
No outcome can cause an early success declaration. The final 55% threshold
is a descriptive local benchmark reference, not promotion eligibility here.

The 100 ms allowance deliberately exceeds arena limits, so the earlier
strict-50-ms process prerequisite does not apply to this explicitly requested
local diagnostic. No new boss games, arena calls or submission are included.
Existing production guards must continue to reject a 100 ms submission recipe.

## Measurement and interpretation

Before measured games, test fake-bot budget delivery on both seats, first-turn
preservation, fresh instances, metadata, and rejection of invalid inputs.
Validate the retained shared engine against the canonical baseline corpus.
Retain source/class/runtime hashes, full parameters, exact commands, raw paired
results and seed/map provenance. Compile diagnostic helpers separately against
the unchanged retained runtime; do not rebuild or replace the selected source.

Primary outcome: pooled wins/losses/draws against 36 ms, with paired map groups
as the independent units for descriptive uncertainty. Report side splits and
technical errors. Reports must say LOCAL_DIAGNOSTIC with effective budgets
486/486/100/36, promotion=false and no arena timing-readiness claim.

The strategy searches a finite candidate set with fixed route, width, reply
and depth limits. More time may complete more of that set and may return early
once it is exhausted; the experiment does not change these strategy limits.
An external per-game wrapper may aggregate existing public search counters
and native call durations, separately for first/later calls, after `play`.
It must not copy game state, change RNG, trace individual nodes or alter the
strategy timer. This adds small symmetric measurement overhead. Compare work
and time descriptively: different budgets can lead to different trajectories.
Native call durations and the referee's >50 ms counters are not protocol
response measurements or server timeouts; >50 ms is expected in this study.

At completion, audit the raw results and preservation pins, archive evidence,
update BOTS/PLAN/DEVLOG, and commit locally. Default 36 ms, batching OFF,
selected v09 and all prior formal verdicts remain unchanged.
