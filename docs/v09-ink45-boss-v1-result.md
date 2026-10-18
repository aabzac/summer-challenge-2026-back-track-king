# Incremental ink at 45 ms: completed boss observation

Completed 2026-10-18. All 40 originally scheduled first attempts are retained:
**14 wins, 26 losses, 0 draws (35.0%)**, including one technical startup loss.
The same incremental-ink policy at 40 ms had 11W/29L (27.5%) on these maps,
seeds and seats. The descriptive difference is +3 wins / +7.5 percentage points:
8 better outcomes, 5 worse and 27 equal. This is historical comparison only;
the public boss update metadata changed between collections.

## Artifact and verification

Candidate: `current?NOISE=0.1,USE_BATCHED_REFRESH=true,TURN_BUDGET_MS=45,USE_INCREMENTAL_INK_REFRESH=true`.
Source: 91,748 UTF-16 characters, SHA-256
`1118fc8ad63d8b58b7d1f2a5f0e50c4605314e63d370518a93d19ae1cb7953a4`.
Build: `9424a240c57b0e3542e3c6543dd704eb60eee857011f1cd2a7c2bc3705883781`.
Only the 45 ms option was added to the native-rollout budget validation/export.
All generated strategy, core and Params classes match the tested ink40 artifact;
only MainBot and the build-marker-bearing CGRunner classes differ. Default
current/v09/36/38/40 exports retain their exact prior source identities.

All 26 focused Java checks pass. Final launch-helper checks pass 27/27; the
continuation checks pass 11/11. Earlier synthetic fixture and offline planning
failures, their fixes and their original records remain in the archive.
No core or strategy changes were made. Margin14, first1000, warmup500 and the
external50 ms limit remain unchanged; fast path and deadline correction are OFF.

The one fixed two-game exact-artifact process diagnostic failed on both seats:
P0 turn5 response51.6577 ms and P1 turn6 response51.0781 ms against50 ms.
The user waived the timing prerequisite; neither report was changed or rerun
until pass. This diagnostic does not establish reliable compliance with50 ms.
The earlier ink40 full16 NOT_READY and local54.0%/1000 NOT_PASSED remain intact.

## Attempted and qualified results

Both halves scored 7W/13L. By seat, P0 scored6W/14L and P1 scored8W/12L.
All40 distinct maps match the cached schedule; no control games, replacement
games, retries, pending requests or unstarted slots remain.

Slot14 (game909722283) failed at startup/first output. The canonical replay
reports termination before the language main entry point and
`umount proc: Device or resource busy`. No candidate output/build marker exists.
The exact requested source is pinned, but actual execution identity is UNKNOWN.
The underlying cause and a causal connection to the45 ms budget are unproven.
The original launcher stopped; after diagnosis a separately recorded
[continuation amendment](v09-ink45-boss-v1-continuation.md) authorized only the
remaining unattempted slots15–40 within the original40-request scope. Slot14
was retained as a loss and never replayed. No additional failures occurred.

The secondary qualified sample is14W/25L in39 games (35.90%), compared with
11W/28L (28.21%) on exactly the same39 historical control slots. It excludes
only slot14 from both sides and never replaces the primary14/40 result.
Shared core verifies all40 initial maps and exactly reproduces all39 qualified
replays. The failed startup game is map-verified only, not engine-exact.

The independent audit verifies1,013 input pins and reconciles all40 first
attempts, outcomes, source identities and pacing. Ordinary completion-to-next-
request gap is at least22.826970 seconds; game20-to21 is300.027878 seconds.
The prior ink40 final game to the first45 request gap is8083.032134 seconds.
Candidate completions span14:02:57–14:38:49 UTC on October18; control completions
span11:19:46–11:47:57 UTC that day.

## Interpretation and decision

Owner, boss6767608 and league6/3/0 match the control. Public boss updateTime
changed from1792272795496 to1792330879349; this does not itself prove a strategy
change, but comparable execution conditions are not established. The pool is
also previously exposed. The +3 wins are an exploratory signal, not confirmed
improvement attributable to the extra5 ms.

Status: COMPLETE_WITH_RETAINED_TECHNICAL_FAILURE. Technical status NOT_PASSED,
readiness false; boss status NOT_ASSESSED_CHANGED_BOSS_AND_TECHNICAL_FAILURE.
The evidence audit passing means records reconcile, not that the candidate
passes timing, technical or promotion gates. No local45 strength stage was run.
Keep45 ms experimental, default36, experimental flags OFF and selectedv09.
No arena submission or push. The45 ms source remains in the website IDE after
test play; its prior source was privately backed up.

[Prospective protocol](v09-ink45-boss-v1.md),
[public evidence](evidence/v09-ink45-boss-v1.json),
[archive index](evidence/v09-ink45-boss-v1-artifacts.json),
[artifact archive](evidence/v09-ink45-boss-v1-artifacts.zip).
