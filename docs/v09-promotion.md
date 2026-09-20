# v09 promotion - Never Idle

The user selected the fill-v1 candidate on 2026-09-20 after its combined practical-v4 pass.
This promotes the measured policy `current?USE_ROLLOUT_FILL=1` from commit a4a9e56; it does
not retune the strategy or repeat its completed strength experiment.

## Selected policy

v08 Protected Opening plus one rule: after the rollout search has chosen its cells and target,
leftover paint is spent on free cells outside the target region whose region is a town region
or has instability 0, adjacent to an own track (new cells included); preference +4 on a proposed
route, +3 next to an own earning track (else +1), +2 town region, minus terrain cost
(`RolloutBot.fill`). Search, routes, evaluator and target choice are the v08 ones. RHEA
(rejected) is no longer part of the shipped source; `USE_ROLLOUT_RHEA=1` is refused by
`current`/v09 while frozen v08 keeps its copy.

Use every one of the **117** effective parameters recorded for the candidate in
[fill-v1.json](evidence/fill-v1.json) (identical in all three local blocks and in the boss
artifact manifest). Enable only `USE_ROLLOUT_FILL`; every other retained experiment stays off.
Freeze `RolloutBot` and `ProtectedOpening` in `bot/v09`, changing only package, class/constructor
names and the bot label; preserve v00-v08 sources and snapshots (pre-v09 snapshots pin
`USE_ROLLOUT_FILL=false` so they keep leaving paint unused).

Completed evidence: **176-124 / 109-91 / 284-216 = 569/1000 (56.90%)** over fresh paired maps
versus v08 (practical-v4, futility looks cleared at 300 and 500, final gate >= 55%), then
**17 W / 23 L** over 40 new boss games versus the cached v08 control's **13 / 27** on the same
maps, seeds and seats. Both stages pass ([decision](evidence/fill-v1-decision.json)). The boss
panel is a practical regression check, not fresh statistical confirmation. The candidate
artifact also passed a strict 8-game process check before the boss games. Study behind the
mechanism: [indrill-construction-v1.md](indrill-construction-v1.md).

## Packaging verification

Before replacing `build/submit`, every existing file was copied to
`build/analysis/v09-promotion/previous-submit` with byte hashes in `before.json` (28 files,
all intact after the panel). Checks performed:

- `V09PromotionTest`: current / main / v09 / `current?USE_ROLLOUT_FILL=1` resolve to exactly the
  117 recorded values; the frozen classes with only their identity changes undone hash to the
  measured sources; 18 historical v00-v08 sources and snapshots unchanged and pinned; decision
  parity of measured, current, main and frozen v09 across 96 evolving positions (filler
  exercised) and the saved boss positions; `v09?USE_ROLLOUT_RHEA=1` is refused.
- `FileMergerTest`: frozen v09 and current ship byte-identical policy sections and identical
  full configurations; the historical v07/v08 merges and pinned policy hashes are unchanged.
- **355 Java tests, 126 Python tests**, `cgValidate` **292/292** arena and **259/259** boss
  replays exact. The engine is unchanged.

## Artifacts

- Default `build/submit/Player.java` (`gradlew submit`): **95,278 / 100,000 characters**, source
  SHA-256 `1df7c131…`, build `c56c83b8…`, config `aed03344…`. It differs from the boss-tested
  fill-v1 artifact (`7d55b4fb…`, build `4cda8b1b…`, same config hash) only in the
  `USE_ROLLOUT_FILL` default declaration and the recalculated build marker.
- Frozen export `build/cg-boss/artifacts/v09/Player.java` (`-PsubmitVersion=v09`): 95,254
  characters, source `df504a0d…`, build `b755f9b4…`, same 117 parameters; strict process check
  0/4 problem games vs v08 on boss maps.

## Strict process panel (default artifact)

Same protocol as the v08 promotion: four regression maps in this order, two games each (both
seats), opponent frozen v08, 1000/50-ms deadlines, zero grace, no instrumentation, no
concurrent CPU work:

1. replays/cg/903510176.json
2. replays/cg/boss/904146009.json
3. replays/cg/boss/904148181.json
4. replays/cg/boss/904147739.json

**0/8 problem games.** First responses 544-736 ms, later responses <= 47.2 ms, no timeouts, no
nominal overruns. Reports `build/analysis/v09-promotion/process-check-1..4.json` (hashes in the
evidence file). The v08 artifact's earlier 1/8 failure (67.6 ms on map 903510176) stays
recorded; this panel is a separate result for the new artifact, not a clearance of that one.

## Result

**v09 is selected locally and its default artifact passed the exact-artifact process panel.**
`current` defaults to the v09 policy; `submit` produces it. Arena upload is the user's action
and is outside this promotion request. Evidence: [v09-promotion.json](evidence/v09-promotion.json).
