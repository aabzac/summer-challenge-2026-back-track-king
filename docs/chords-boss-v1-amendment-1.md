# Chords boss screen: explicit recovery amendment

Registered 2026-09-19 local (2026-09-18 UTC), before any resumed game.
The user explicitly requested excluding the failed request and completing the
original schedule, and clarified **40 games per strategy, 80 games total**.
The original protocol, 80-slot plan and interruption evidence remain unchanged.

## Known information at this decision

- Ten complete map pairs have been observed: candidate 3W/7L, v07 2W/8L.
- Original slot 21, candidate on pair 11, seed `36225550`, player seat 1,
  returned HTTP 422. Its original intent and error are retained. No game ID or
  outcome is known, and the original response body was not retained.
- Read-only investigation did not find an IDE history that can recover this ID.
  A separate v07 diagnostic game succeeded but is excluded from this comparison.
- Whether the original request created a game remains unknown. This amendment
  does not claim the failure occurred before execution or was independent of outcome.

## Exactly one authorized new attempt

Exclude the original unknown HTTP request from the analyzed outcomes. Authorize
one new play for **slot 21 only**, with exactly its already frozen source, parameters,
boss, seed and seat. Retain a separate authorization with the amendment and hashes
of the original intent/error before issuing it. Save the second attempt's intent,
response and canonical replay separately; never overwrite the first attempt.

Use the second attempt's result regardless of win, loss, draw or a completed bot
error. If the original game's ID is discovered later, retain it separately and do
not select between the two outcomes. Another uncertain play response stops the run;
there is no automatic third attempt. Incomplete canonical retrieval may be retried
read-only using its already received game ID.

After resolving slot 21, complete slots 22 through 80 in their original order.
No map replacements, new seeds, strategy changes, outcome-driven stops or sample
extension are authorized. Run sequentially with a five-second inter-game pause.
At successful completion the comparison contains 80 analyzed games, 40 matched
pairs, and 81 play attempts; the actual number of created games may be unknown.
The six separate technical games remain excluded.

## Interpretation and reporting

This is an **amended exploratory completion**, decided after seeing partial results.
Retain the original incomplete audit and export a separate amended final audit.
Report both strategy counts, the paired discordances and the original exact paired
test calculation, clearly labeled exploratory. The transport exclusion, timing gap
and post-interim amendment limit interpretation; they are not silently discarded.

No result promotes the strategy or overrides the completed local strength gate.
Selected v07, default-off flags and the original submission artifacts remain unchanged.

Original protocol: [chords-boss-v1.md](chords-boss-v1.md).
Original interruption: [evidence](evidence/chords-boss-v1.interrupted.json).
