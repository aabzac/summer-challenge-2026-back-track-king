# Ink40 candidate-only boss observation

2026-10-18. **Complete: 11 wins, 29 losses, no draws in exactly 40 candidate
games, 27.5% points.** All 40 replay results reproduce exactly through the
shared core; there were no technical game failures or replacement attempts.
This follows the user's explicit candidate-only request after the local 54.0%
result. Keep the optimization OFF.

## Exact candidate and schedule

Candidate: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=40,USE_INCREMENTAL_INK_REFRESH=true`.
Use the same tested 91,714-character artifact, source SHA-256
`aff2dc9bf48d2f9a141012c3d47fc079bb9af9354cdfa846ef40832804db75a5`.
No strategy, parameter, build or selected-default changes.

Exactly 40 candidate games were scheduled on the saved batched40 control's
40 seeds/maps/seats in the same order, 20 games per side. No new control games.
The exact B40 artifact without incremental ink matches the local control;
its October 16 records contain 13 wins and 27 losses. Every cache record was
rechecked, and all 40 inherited replays reproduce through the shared core.

The live owner, puzzle, boss 6767608 and league 6/3/0 were verified before
play, and the current IDE source was backed up privately. However, the boss's
public update timestamp changed from 1792077863355 to 1792272795496.
The new candidate result is therefore primary; old 13/40 is historical
context, not evidence of unchanged comparison conditions. These maps were
already exposed to outcome-guided development.

An initial read-only request using the obsolete default IDE session returned
HTTP422/API270 and created no game. The latest successful session of the same
account supplied the retained discovery. No play request was retried.

## Explicit exception and preparation

The original 16-game timing panel remains NOT_READY with two timeouts at
50.2949/52.1667 ms. Its eight original reports and independent audit are
pinned. The genuine passing first two-game report is used only by the unchanged
artifact reader to verify package identity; the full panel is not relabelled.
The completed local 518W/438L/44D, 54.0%, NOT_PASSED result is unchanged.

The user authorized this diagnostic boss series despite those outcomes.
Twenty-three synthetic helper tests passed without games or network calls.
The fixed plan, exact artifacts, full prior evidence, source dependencies and
new helper were frozen before the first intent. The new map helper calls the
preserved CgReplay/CgValidate/shared-core implementation without strategy calls
or production rebuilds. Public output excludes credentials and private IDE reads.

Ordinary completion-to-next-request spacing is at least 20 seconds, with at
least 300 seconds after game 20. This preserves the user's requested pacing
without claiming a known quota reset. Unknown creation or technical failures
are retained without automatic replacement.

## Results and decision

The first 20 games scored **8W/12L**; the second 20 scored **3W/17L**.
As player 0 the candidate scored **5W/15L**, and as player 1 **6W/14L**.
Exactly 40 first-attempt intents produced 40 distinct canonical games; no
pending or unstarted slots, technical failures, new play refusals or new
control games. The earlier HTTP422/API270 was a read-only discovery failure,
not a failed or replaced game.

For historical context, the same-map B40 control had **13W/27L (32.5%)**.
Candidate outcomes are better on **5** maps, worse on **7** and equal on **28**:
net **-2 wins / -5 percentage points**. Because the public boss update metadata
changed between collections, these are descriptive historical differences;
they do not isolate the optimization's effect or establish a boss-stage pass.
The experiment-level status is **NOT_ASSESSED_CHANGED_BOSS**, separately from
the legacy cached tool's raw score/technical-sample arithmetic.

New requests ran from 14:19:29 to final completion at 14:47:57 on October 18,
Europe/Bucharest (raw evidence retains UTC). The old B40 games were collected
on October 16. Actual game20-completion to game21-intent gap was
**300.039822 seconds**; the minimum ordinary gap was **21.228412 seconds**.
The requested five-minute pause was respected.

Independent reconciliation **PASS_RETAINED_EVIDENCE**, with **942 pinned
inputs** verified: every raw outcome, source/build/map/seed/seat/boss identity,
request count, pacing, exact artifacts and historical verdicts. Both the
40 inherited B40 replays and the 40 new candidate replays reproduce exactly.
All 23 synthetic helper checks passed before planning; the live helper and
its dependency hashes remained unchanged.

Decision: retain the completed observation and leave incremental ink OFF.
There is no demonstrated reason here to override the prior local threshold
failure. This observation does not alter the earlier 54.0% NOT_PASSED or
16-game NOT_READY timing verdict. Default 36 ms, experimental flags OFF and
selected v09 remain unchanged. IDE test play saved the tested candidate there;
no arena submission or push occurred. Archive and commit the completed work.

[Prospective protocol](v09-ink40-boss-v1.md),
[evidence](evidence/v09-ink40-boss-v1.json),
[archive index](evidence/v09-ink40-boss-v1-artifacts.json),
[raw public archive](evidence/v09-ink40-boss-v1-artifacts.zip).
