# Incremental ink at 45 ms: candidate-only boss experiment

Declared 2026-10-18 after commit 60e7cf0 and before any 45 ms measurement or
live game. The user's next request is to try 45 ms in the preceding format:
exactly 40 candidate games against the live boss, on the same maps and seats,
with the timing prerequisite explicitly removed. This is an exploratory
budget experiment, not promotion or a rerun of the failed local strength gate.

## Fixed candidate and control

Candidate: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=45,USE_INCREMENTAL_INK_REFRESH=true`.
Export through `gradlew submit` into `build/analysis/v09-ink45-boss-v1/artifact`.
Only permit the extra 45 ms budget in the existing validation/export logic;
keep strategy, safeguards, CG margin, default 36 ms and experimental defaults
unchanged. Fast path and missed-deadline correction stay OFF. Verify the
generated strategy/core classes equal the 40 ms artifact; only budget-bearing
wrapper/parameter classes may differ. Enforce the 100,000 UTF-16 character cap.

Primary cached control is the candidate arm of
`build/cg-boss/v09-ink40-boss-v1-candidate-40/plan.json`, collected October 18:
11 wins, 29 losses, 0 draws, source SHA-256
`aff2dc9bf48d2f9a141012c3d47fc079bb9af9354cdfa846ef40832804db75a5`.
It has the SAME incremental-ink policy at 40 ms. Verify all 40 individual
canonical outcomes, build identities, maps, seeds, seats and boss identity.
Play the 40 cached cases once, in their original order, 20 per seat. No new
control games, local strength games, replacement maps or tuning during play.
Compare W/L/D, points (win 1, draw 0.5) and paired better/worse/equal results.
The earlier no-ink40 13/40 is not this experiment's primary control.

## Validation and interpretation

Run one fixed minimal exact-artifact `procCheck`: two games on saved map
`replays/cg/903510176.json`, both seats, first-response limit 1000 ms and later
limit 50 ms, zero grace. This checks the package/protocol required by the
working agreement; timings are diagnostic only and do not gate boss play.
Retain the first result including failures, without rerunning until pass.
A scoped adapter may accept the authentic failed report with an explicit
waiver; it must not alter the report or label it a timing pass. Do not run
another broad timing panel. Keep the previous 40 ms full-panel NOT_READY
(2 timeouts/16 games) and local NOT_PASSED (54.0%/1000) as historical context.

Before play, read the live authenticated owner, puzzle, boss/league and public
update metadata; privately back up the current IDE source. The control boss
is 6767608, league 6/3/0, public updateTime 1792272795496. A different boss or
league blocks this comparison. Changed update metadata makes comparison
historical-only; matching metadata does not prove identical hidden conditions.
The reused, previously exposed pool is a regression observation, not fresh
confirmation. No promotion is authorized by this experiment.

Read-only discovery at 13:53:23 UTC on October 18 found the same owner, boss
and league, but public updateTime changed to 1792330879349. Thus the 11/40
control is historical context under potentially changed boss conditions;
do not declare a comparable-condition boss pass from this series.

Pin the candidate, full diagnostic report, control cache, helpers, protocol
and schedule before the first play. Validate all cached and new maps/replays
through the shared core. Record technical errors and stop on transport,
provenance or game failure without replacing games. Persist the intent before
each play; uncertain creation must not be replayed. A known game ID may be
retrieved read-only. Respect service refusals without account/session switching.

## Pacing and completion

Use one sequential session, ordinary completion-to-next-request delay of at
least 20 seconds. Preserve the user's 300-second pause after each 20 games:
new game 1 must be at least 300 seconds after the preceding 40 ms game 40
completed (2026-10-18T11:47:57.691749+00:00), and new game 21 at least 300
seconds after new game 20. Record actual gaps. This is pacing, not a claimed
quota-reset guarantee.

Report the completed fixed sample or exact interruption, paired comparison,
technical failures, artifact diagnostics and shared-core validation. Archive
public evidence without credentials, update BOTS/PLAN/DEVLOG and commit locally.
IDE test play saves the candidate source in the website IDE. No arena upload,
push, change to selected v09 or change to the default 36 ms budget.
