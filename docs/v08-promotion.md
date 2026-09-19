# v08 promotion - Protected Opening

The user selected the verified protected-opening candidate on 2026-09-19.
This promotes the measured policy from commit 49df922; it does not retune the
strategy or repeat its completed strength experiment.

## Selected policy

Use every one of the 107 effective parameters recorded for
`current?USE_PROTECTED_OPENING=1` in the frozen
[experiment evidence](evidence/strategy-search-opening-v1.json).
Enable only that default; RHEA and other rejected experiments remain off.
Freeze RolloutBot and its ProtectedOpening helper in v08, changing only package,
class/constructor/helper names and the bot label. Preserve v00-v07 source and
parameter snapshots. Old snapshots must not inherit the new opening default.

For turns 0-2, claim affordable protected cells adjacent to towns using potential
traffic from existing canonical route queries. Spread the claims with a one-cell
quota per adjacent town per turn; discount cells in the predicted opponent build
prefix by 0.25. Reuse normal sabotage selection. If no eligible claim exists, or
from turn 3 onward, use the ordinary v07 construction policy.

Completed practical-v3 evidence: 186W/114L/0D over 300 fresh paired games versus
v07 (62%), then 13W/27L/0D over 40 new boss games versus the cached control's
10W/30L/0D on the same maps and sides. Both stages pass; no 1000-game continuation
is required. This reused boss pool is a practical regression check, not fresh
statistical confirmation. The prior candidate also passed four strict process games.

## Prospective packaging verification

Before replacing build/submit, copy every existing file into
build/analysis/v08-promotion/previous-submit and record byte hashes in before.json.
Pin all older frozen sources/snapshots and the completed experiment evidence.

Verify the full effective configuration of current/main/v08 against the measured
candidate, normalized frozen source equality for both classes, and ample-time
action parity in opening and later states. Run the full unit suite and replay
validation. Explain every difference between the measured exported source and
the promoted default source. Check the 100,000-character limit.

Build the actual default build/submit/Player.java with gradlew submit. Compile a
separate v08 export as well. After source/class hashes are fixed, run exactly four
strict procCheck invocations against frozen v07, two games each for both sides, on
these existing regression maps in this order:

1. replays/cg/903510176.json
2. replays/cg/boss/904146009.json
3. replays/cg/boss/904148181.json
4. replays/cg/boss/904147739.json

Use 1000/50-ms deadlines, zero grace, no optional instrumentation and no concurrent
CPU-heavy checks. No retries, discarded outliers or replacement maps. Finish all
eight games if reports remain valid, even after a timing failure. Retain every
report and check that source/classes/configuration remain fixed. Hold a temporary
Windows execution-state request during the panel and release it in finally.

A failed exact-artifact panel leaves timing readiness open; the earlier candidate
sample cannot clear a new failure. These games check packaging/timing, not fresh
strength. Record results and commit locally. Arena submission and push are outside
this promotion request.

## Result

**v08 is selected locally. Exact-artifact process readiness remains OPEN.**

- All 338 tests pass, including all 107 effective parameters, normalized frozen
  source equality for both classes, unchanged historical sources/snapshots, and
  280 measured/current/main/v08 decisions across 70 opening and later positions.
- All 292 arena and 179 boss replays reproduce exactly. The game engine is unchanged.
- The new default export differs from the approved candidate only in the enabled
  opening default declaration and its recalculated build marker. Size: 96,207 of
  100,000 characters. Source SHA-256:
  `079ffa5859b2c7bed2fe6026fa50ab2080604f4a21fd2043f05509f1f385718d`.
  Build ID: `74d41b1ff096e2972e9e740096f5ff1224ed7e293cd855c3ab650e1e5046b8c3`.
- The entire fixed eight-game strict panel completed, with **one problem game**.
  On map 903510176, P1, turn 29, the observed response was **67.5805 ms** against
  a 50-ms deadline; send time was 11.0924 ms and consumer time 71.537699 ms.
  The report records one timeout and one nominal overrun. The other seven games
  passed. No retries, increased grace, discarded outcomes or parameter changes.
- This observation does not identify the cause of the delay. The earlier four-game
  candidate process pass does not clear this failure of the newly packaged artifact.
  The selected local version is v08, but the file is not certified ready for arena upload.
- The separate frozen-v08 export compiles and matches the full configuration and
  normalized policy source. The strict panel tested the default main export only;
  it is not a process-readiness claim for the separate frozen export.
- All old v00-v07 sources/snapshots and prior strength evidence are unchanged. All
  26 previous submission files are preserved byte-for-byte. Source, compiled classes,
  configuration and maps stayed fixed throughout the panel; the temporary power
  request was released.

No fresh strength experiment, retuning, arena submission or push was performed.
The next timing investigation must retain this complete failed panel separately.

[Process results](evidence/v08-promotion.json),
[immutable prospective protocol](evidence/v08-promotion-protocol.json),
[independent verification](evidence/v08-promotion-verification.json),
[exact artifacts and complete timings](evidence/v08-promotion-artifacts.zip).
