# v09 batched refresh: additional boss series with cooldowns

Prospective protocol, 2026-10-16 (Europe/Bucharest).

User instruction: "Давай ещё по 40 игр, но сделаем таймаут по 5 минут между
каждыми 20 играми, чтобы не упереться в лимит по играм."

This explicitly authorizes a separate additional fixed 40-game arm for each bot,
80 new IDE test games. The original v1 result remains 10W/30L candidate versus
11W/29L v09, and its formal practical-v4 NOT_PASSED verdict is preserved. This
requested follow-up is exploratory; report both the new series and the combined
80 games per bot descriptively. Do not retroactively convert v1 into a pass,
change the promotion policy, tune a bot, or extend again without a new request.

## Fixed sample and timing

- Same exact artifacts as v1: candidate
  `current?NOISE=0.1,USE_BATCHED_REFRESH=true`, source SHA-256
  `2cb3a6a254fd8f37e0c9f07e2aa36565f4d61027ee44f508a5f64a93d547d61a`;
  control `v09?NOISE=0.1`, source SHA-256
  `aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac`.
  Full parameters/classes and previously passed strict process panels must match.
  Missed-deadline correction OFF. No new local strength or process reruns.
- Fixed schedule seed `2026101609`: 40 new distinct maps, 20 P0 and 20 P1.
  Play control40 first, validate it, then candidate40 on the identical schedule.
  Freeze candidate schedule and source before any control outcome.
- Shared-core freshness check excludes all prior local maps, the existing local
  1000-game pool and v1's 40 boss maps. Check other saved map sources as well.
  A repeated map blocks preparation; never silently reroll or replace it.
- Keep the usual 20-second pause between games. Before global game 21, 41 and
  61, enforce **at least 300 seconds since completion of game 20, 40 and 60**,
  respectively. The cross-arm audit counts toward the game-40 cooldown. No
  five-minute pause is needed after the final game 80 because no next game exists.
  Persist pause evidence and verify actual request/completion timestamps.
- The new pacing adapter delays before the existing runner creates the next
  play intent. Existing play payloads, artifact checks, owner lookup and
  uncertain-creation protections remain unchanged. No parallel boss sessions.
- Five minutes is the user's conservative pacing choice, not a known service
  quota or guarantee. Preserve explicit refusals; do not automatically repeat
  an uncertain or refused creation, switch accounts, or replace an outcome.

## Validation and interpretation

Read the live puzzle, boss and league before play; privately preserve current
IDE source. Expected prior boss is 6767608, league divisions 6/3/0. Canonical
identity, maps, seeds, seats, build markers and terminal outcomes must match for
every game. Run all 80 new canonical replays through the shared rules engine.
Complete the fixed sample without outcome-based stopping, except an actual
transport, technical or provenance failure that blocks safe continuation.

Report W/L/D and points (win=1, draw=0.5), paired better/worse/equal outcomes,
technical failures, actual cooldown gaps, collection dates and full provenance.
New-series candidate >= control may be described as passing the numerical
comparison in this series only. Pooled results are descriptive, since the second
series was requested after observing the first failure. Neither automatically
promotes the strategy. Batching stays OFF, v09 stays selected; no arena upload
or push. The unresolved prior 75-ms native-call observation remains unchanged.

Retain original v1 evidence and all new attempts, archive public evidence without
credentials, update BOTS/PLAN/DEVLOG and commit the completed result locally.
