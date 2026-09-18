# Chords boss screen: respect the reported play-rate limit

Registered 2026-09-19 local (2026-09-18 UTC), after 50 analyzed games and before
resuming the remaining schedule. The user's instruction remains to exclude the
failed request and complete forty games per strategy, eighty analyzed games total.
This adds a second documented exception; all previous records remain unchanged.

## Observed refusal

Original slot 51 returned HTTP 422 at `2026-09-18T21:54:20.423603+00:00`.
This time the response was retained: API error id **407**, message
"You reached the limit of plays for a period of time."
Only these safe fields and byte hashes are exported in
[rate-limit evidence](evidence/chords-boss-v1.rate-limit.json).
No game ID or result was returned. The exact limit and window are not established.
This identifies the new refusal; it does not establish the cause of the old slot-21
error, whose response body was unavailable.

## Cooldown and completion

Make no new play request before **2026-09-18 22:04:21 UTC**, a chosen ten-minute
pause after the refusal. This is a conservative local wait, not a claimed official
CodinGame cooldown. Do not bypass the limit using another session or account.

Then authorize one separately recorded second attempt for **slot 51 only**, with
the exact original source, parameters, seed, seat and boss. Retain the original
intent/error, authorization and amendment snapshot. Record the result regardless
of win/loss/draw or a completed bot error; never select between attempts. Another
uncertain or rejected second attempt stops for diagnosis, without a third attempt.

Complete slots 52 through 80 sequentially, increasing the pause between games to
**20 seconds**. Keep every scheduled map and build, with no tuning, sample extension
or outcome-driven stopping. The original 50 completed outcomes are retained.

Successful completion will contain **80 analyzed games, 40 matched pairs and
82 recorded play attempts**: the original unknown slot-21 request and the reported
rate-limit refusal at slot 51 are excluded from outcomes. The six separate technical
games remain excluded. The actual number of games created by the original unknown
request is still unknown.

The final independent audit and gallery must disclose both exceptions and report
the exact paired test as exploratory. This amendment is made after intermediate
results were available and does not restore the original unamended protocol.
There is no strategy promotion or change to the previous failed local strength gate.
