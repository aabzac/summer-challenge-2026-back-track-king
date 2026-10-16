# Boss follow-up: completed 36 ms series and matched 40 ms comparison

**COMPLETE_EXPLORATORY, 2026-10-16.** The interrupted batched-refresh 36 ms
series is complete, followed by exactly 40 games of the unchanged 40 ms
artifact on the same seeds, maps, seats and order:

- Batched 40 ms: **13 wins, 27 losses, 0 draws — 13/40 points (32.5%)**.
- Batched 36 ms: **10 wins, 30 losses, 0 draws — 10/40 points (25.0%)**.
- Cached v09: **11 wins, 29 losses, 0 draws — 11/40 points (27.5%)**.

Against 36 ms, 40 ms improves 7 outcomes, worsens 4 and ties 29: a net
**+3 wins / +7.5 percentage points**. Against v09 it improves 7, worsens 5
and ties 28: **+2 wins / +5 percentage points**. This is a positive descriptive result,
not sufficient evidence to enable the optimization or increase the default
budget. The original batched-v1 NOT_PASSED and budget40 local NOT_PASSED
(51.0% over 1,000 games, below 55%) remain unchanged. Keep default 36 ms,
batching OFF and selected v09.

## Completed previous series

The 28 preserved candidate games were 9W19L. All 12 remaining slots 29–40
completed, adding 1W11L and producing the final 10W30L. Against the v09
control's 11W29L, batched 36 ms improves 6 outcomes, worsens 7 and ties 27.

The first new request was at 2026-10-16 06:23:45.317932 UTC; the last
completion was at 06:30:37.648606 UTC. The original 28 candidate games and
the 40 control games were collected on October 15. All 40 completed
candidate replays reproduce exactly through the shared core.

Original slot 29/global 69 retains its HTTP 422/API 407 refusal and
conservative unknown-creation flag. Under the user's explicit continuation
instruction, a separate amendment authorized precisely one second attempt;
the successful new game is 909201117. No original evidence was overwritten
or claimed to prove that no game was created. The unknown original request
is unscored. Slots 30–40 were new first attempts. The previous
[INCOMPLETE_TRANSPORT report](v09-batched-refresh-boss-v2-result.md) and its
archive remain immutable historical snapshots.

Across original v1 and completed v2, batched 36 ms totals **20W60L/80** and
v09 totals **22W58L/80**. Pooling is descriptive: the second series was
requested after observing the first. It does not create a new formal pass.

## Matched 40 ms stage

The exact artifact and schedule were frozen before any continuation outcome.
The first request was at 2026-10-16 06:35:39.290213 UTC; the final completion
was at 07:02:59.284567 UTC. Exactly 40 first attempts produced 40 canonical
results, with no replacements, new controls, tuning or outcome-based stops.

The first 20 games scored 5/20, versus 6/20 for 36 ms and 4/20 for v09.
The final 20 scored 8/20, versus 4/20 and 7/20 respectively. By seat, 40 ms
scored 5/20 as player 0 and 8/20 as player 1; 36 ms scored 3/20 and 7/20,
and v09 scored 7/20 and 4/20. These subdivisions are descriptive only.

The 40-vs-36 improved slots are 10, 17, 24, 30, 31, 35 and 37; worsened
slots are 1, 2, 4 and 33. Full matched rows, seeds, map hashes and game IDs
are retained in the evidence, including unchanged outcomes.

## Validation and pacing

- Read-only discovery and a fresh pre-40 check confirmed owner, puzzle,
  boss 6767608, league 6/3/0 and public update time 1792077863355. The saved
  control's 40 individual outcomes, maps and source markers were verified.
- The shared core reproduces all 120 canonical games exactly: 40 cached v09,
  40 completed 36 ms and 40 new 40 ms. All three arms match the same 40 maps,
  seeds, seats and order. No technical game failures or new API refusals.
- Actual completion-to-next-intent gaps were **301.641607 s** before 40 ms
  slot 1 and **300.015568 s** before slot 21. The minimum ordinary gap among
  new requests was **20.334483 s**. Five-minute pauses do not establish an
  official quota/reset guarantee.
- All 325 existing evidence/source files and 39 prospective frozen files
  remain byte-identical. Independent audit: PASS, no issues; exactly 52 new
  play intents and 52 new known games (12 + 40), with the old unknown attempt
  retained separately. Twelve resume-adapter tests and twelve follow-up
  guard/cooldown tests passed before live play. Old boss tools are unchanged.
- The 40 ms source remains 90,641 characters, SHA-256
  `9510ff174e76297736144193a4c519f3728eac067d09614bb65e2f0ecd1b88d9`.
  The 36 ms source remains
  `2cb3a6a254fd8f37e0c9f07e2aa36565f4d61027ee44f508a5f64a93d547d61a`.
  Strategy parameters match except the later 36/40 ms budget.

## Interpretation and retained evidence

These maps were already exposed to outcome-guided analysis. The requested
matched comparison is a regression observation, not fresh confirmation.
Unchanged public boss metadata does not prove unchanged hidden implementation
or execution conditions; the control and candidate collection dates differ.
Replay build markers corroborate locally retained artifacts but do not
independently prove the remotely executed source.

No new local timing or strength games were run. The previous exact-artifact
16-game process panel still supplies the full-response evidence: zero
timeouts/overruns, later maximum 47.8363 ms. Its separate local strength-run
native-call outlier of 59 ms remains recorded. Absence of technical failures
in this boss series does not establish a universal 50 ms timing guarantee.

Decision: retain the positive 40 ms observation, keep both historical failed
verdicts and current defaults, and do not promote from this reused pool.
IDE play saved the tested code there; no arena submission or push occurred.

[Prospective protocol](v09-boss-budget40-followup-v1.md),
[audit and full paired evidence](evidence/v09-boss-budget40-followup-v1.json),
[archive index](evidence/v09-boss-budget40-followup-v1-artifacts.json),
[public artifact archive](evidence/v09-boss-budget40-followup-v1-artifacts.zip).
The archive excludes private sessions/readbacks and raw transport bodies;
their hashes are retained where needed. Prior published reports and archives
are referenced by hash, without changing their historical verdicts.
