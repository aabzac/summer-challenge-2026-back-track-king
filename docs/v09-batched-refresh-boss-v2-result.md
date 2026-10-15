# v09 batched refresh: additional boss series interrupted by play limit

**Status: INCOMPLETE_TRANSPORT. Keep batching OFF and v09 selected.**

The user requested another 40 games per bot, with five-minute pauses between
each twenty games. The new fixed schedule completes **40 v09 control games**
and **28 candidate games**. The next candidate request, slot 29 (global request
69), receives HTTP 422 / API code 407 at 2026-10-15 22:11:44 UTC:
"You reached the limit of plays for a period of time."
The service provides no Retry-After value and no game ID. Preserve the runner's
conservative uncertain-creation flag and the original intent; do not repeat the
request or count it as a played loss. Candidate slots 30–40 were never requested.
**Twelve planned candidate slots remain uncompleted.**

## Retained outcomes

The full refreshed v09 control is **11W/29L/0D, 11/40 points**. Compare the
retained candidate prefix only with the same 28 control maps/seeds/seats:

- Candidate: **9W/19L/0D, 9/28 points (32.14%)**.
- Matched v09 prefix: **6W/22L/0D, 6/28 points (21.43%)**.
- Candidate improves six paired outcomes, worsens three and ties nineteen:
  three wins by both and sixteen losses by both.

Do not compare candidate 9/28 directly with control 11/40. The twelve remaining
control outcomes are retained as unmatched observations, not discarded or
replaced. The interruption was a service failure, not an outcome-based stop.

For description only, combine the original complete 40 pairs with these 28:
candidate **19W/49L/0D, 19/68 points (27.94%)**, matched control
**17W/51L/0D, 17/68 points (25.00%)**. These are 68 matched pairs, not 80.
There are 148 retained games across both series; twelve of the 80 total control
games have no second-series candidate counterpart yet. The combined view was
requested after seeing the first failure and cannot establish a new formal pass.

## Pacing and validation

All three prescribed gaps before global games 21, 41 and 61 were enforced from
the previous game's completion to the next request intent: **300.015003,
300.030197 and 300.030033 seconds**, respectively. Ordinary 20-second
spacing remained in place. Five-minute pauses therefore **did not suffice to
avoid the service limit** in this run. The service response does not identify the
quota size or reset time; neither can be inferred reliably from this sequence.

The same two exact artifacts from the original experiment were used. Full
parameters, source/classes, strict process panels, 2,912 frozen implementation
files, seven runtime components, 85 original live inputs and 109 new live inputs
remain unchanged. Candidate source is SHA-256
`2cb3a6a254fd8f37e0c9f07e2aa36565f4d61027ee44f508a5f64a93d547d61a`;
control source is
`aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac`.
The missed-deadline correction stays OFF; no strategy or timer changes occurred.

Before play, all forty new maps were checked against **23,584 prior core map
identities** and 151 completed local reports. All **68 retained canonical replays
reproduce exactly** through the unchanged shared rules engine; actual maps match
their prospective identities. The separately named partial validator checks the
retained prefix without relaxing the frozen complete-stage validators or
pretending the forty-game candidate stage is complete. No game was replaced or
retried. The only live failure is the quota response on the 69th request.

Boss remains agent 6767608, league divisions 6/3/0. Control collection runs
2026-10-15 21:18:53–21:45:59 UTC; the retained candidate games run
21:50:59–22:11:22 UTC, followed by the failed request at 22:11:44 UTC.
The initial IDE source was
privately preserved; final read-only verification confirms the exact candidate
source remains in the IDE. No arena publication, default change, promotion or
push occurred. The earlier unresolved 75-ms native-call limitation remains.

Nine offline cooldown tests passed. The initial Windows temporary-test-directory
permission failure was repaired before any live game and retained in the record.
After the quota stop, additional offline validation and archive handling were
added for incomplete evidence; all frozen live tools and protocols stay exact.

## Decision

The retained paired results are encouraging but incomplete. They do not justify
enabling the optimization. Preserve the original formal **NOT_PASSED** from v1
and the new **INCOMPLETE_TRANSPORT** separately. Do not create a pass by extending,
retrying, replacing or pooling this incomplete series. Any continuation first
needs the service restriction resolved and the uncertain request reconciled;
the current run has performed neither another play attempt nor an automatic retry.

[Prospective protocol](v09-batched-refresh-boss-v2.md),
[machine-readable retained evidence](evidence/v09-batched-refresh-boss-v2.json),
[archive index](evidence/v09-batched-refresh-boss-v2-artifacts.json),
[public evidence archive](evidence/v09-batched-refresh-boss-v2-artifacts.zip).
