# Incremental ink at 40 ms: candidate-only boss regression

Declared 2026-10-18, before any new game, after commit a8f0440. The user asks
for 40 games against the bot for the candidate only. In the continuing boss
comparison this authorizes exactly 40 live IDE boss games of the tested ink40
candidate, despite the retained local NOT_PASSED and process NOT_READY. The
earlier request removed the timing prerequisite; no new timing games or
retries of the completed local sample are needed. This is exploratory evidence,
not a promotion stage or a revision of either historical verdict.

## Fixed artifact and comparison

Candidate: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40,USE_INCREMENTAL_INK_REFRESH=true`.
Use the unchanged 91,714-character source from `build/analysis/v09-ink40-v1/artifact`,
SHA-256 `aff2dc9bf48d2f9a141012c3d47fc079bb9af9354cdfa846ef40832804db75a5`.
Fast path and missed-deadline correction remain OFF. No new build or strategy edit.

Take the candidate arm of
`build/cg-boss/v09-boss-budget40-followup-v1-budget40-40/plan.json` as the cached
control: the exact batched40 version without incremental ink, source
`9510ff174e76297736144193a4c519f3728eac067d09614bb65e2f0ecd1b88d9`.
Its 40 individually retained outcomes are 13 wins and 27 losses, collected
October 16. Play exactly once on each of its 40 seeds/maps/seats in the same
order, 20 games per seat. No new control games, replacement maps, tuning or
outcome-based stopping. Compare points (win 1, draw 0.5), W/L/D and paired
better/worse/equal outcomes. Reaching 13 points is descriptive only.

The retained v09 11/40 and batched36 10/40 arms are different policies;
they are secondary context, not the primary control. This pool was exposed
to outcome-guided development. Its reuse is intentional regression observation,
not fresh confirmation. Do not refresh it by creating unrequested control games.

## Validation and explicit timing exception

Verify all cached raw outcomes and source/build/map/seed/seat/boss identities.
Before play, check the authenticated owner, puzzle and live boss/league,
privately preserve the current IDE source and compare the public boss update
metadata with the cache. The recorded boss is 6767608, league 6/3/0; changed
identity or league blocks use of this comparison. Matching public metadata
does not establish identical hidden implementation or execution conditions.

Pre-game discovery on October 18 confirmed the same owner, boss and league,
but public `updateTime` changed from 1792077863355 to 1792272795496. Retain
both values. Proceed with the specifically requested candidate-only games;
the old 13/40 is historical context under potentially different conditions,
not a valid fresh head-to-head control or a basis for declaring this boss
comparison passed. Report the new candidate outcome on its own first.
The initial read-only lookup used the obsolete September session and returned
HTTP422/API270; no play was attempted. The latest October16 session of the
same account supplied the successful discovery and current source backup.

Retain and pin all eight reports from the complete 16-game strict process
sample: two timeouts at 50.2949/52.1667 ms, no missing replies, NOT_READY.
The existing passing `process-1.json` may satisfy the unchanged artifact
reader's package-identity check; it is not evidence that the full panel passed.
Do not edit, relabel or replace failed process evidence. No new process games.
Keep the completed 1,000-game local result, 518W/438L/44D = 54.0%, NOT_PASSED.

Freeze the exact artifacts, complete timing evidence, cached control and
schedule, protocol, dependencies and new helper before the first intent.
Verify each returned game and reproduce all new canonical replays through
the shared core. A technical failure or provenance mismatch is retained and
stops further play; no automatic replacement. An uncertain creation keeps its
intent and cannot be replayed; an already-known game ID permits read-only
retrieval. Respect service refusals without changing accounts or sessions.

## Pacing and completion

One sequential session, ordinary 20-second completion-to-next-request spacing.
Preserve the user's earlier request for at least 300 seconds after game 20
before game 21. Record actual gaps. Five minutes is requested pacing, not a
known quota reset. No parallel boss batches.

Report the fixed completed series or its precise interruption, technical
failures, primary paired comparison, collection dates and engine validation.
Archive public evidence without credentials/private readbacks; update
BOTS/PLAN/DEVLOG and commit locally. Defaults remain 36 ms, all experimental
flags OFF and selected v09 unchanged. IDE test play saves this candidate's
source in the website IDE; no arena submission, promotion or push.
