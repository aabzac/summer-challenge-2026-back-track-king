# Joint first-build / own-sabotage audit

Tools-only follow-up to [the shared-state reply audit](reply-study.md), 2026-09-15. The hypothesis
is that first-build reranking misses interactions with our subsequent sabotage. No strategy,
parameter, shared-core or submission changes; no strength or process-readiness claim.

```powershell
.\gradlew.bat jointReplyStudy
```

Defaults: `-Ppath=build/analysis/reply-study-v1/capture`,
`-Pprior=build/analysis/reply-study-v1/report.json`,
`-Pout=build/analysis/reply-study-v1/joint-report.json`.
The capture and prior report are read-only; an existing output is rejected. There is no capture
or timing mode: reuse the saved sample, not newly played games. Both source and oracle actions
are checked against the reconstructed positions. Engine identity, full diagnostic parameters,
manifest hash, regenerated routes/prefixes/predictions and original values must match.

## Fixed comparison

Reuse all 110 observations from the same 24 games / six development maps, including 29 without
first-build candidates. There are 108 distinct state/perspectives and 79 distinct candidate
states (81 observations). No replacement of early-ended games or additional sample selection.
The retained K2/R1 engine uses NOISE=0 in diagnostics; the original capture used NOISE=0.1.

For each of the same retained affordable prefixes, compare:

- **None:** no own sabotage, reproducing the original probe exactly.
- **Fixed:** the same target for every prefix, chosen by the existing sabotage policy before
  building, with no own planned-route marks.
- **Adapted:** a target chosen separately after each prefix, using the actual private sabotage
  chooser with the full route's initially buildable cells marked, as in real play.

Target selection sees only the known board and our planned construction. It never sees the
predicted or actual opponent action. Leftover-paint construction is excluded. Each target scan
must finish; subsequent evaluation cannot change targets. Evaluate all three variants against
opponent WAIT, the saved prediction, and the recorded-action oracle. The oracle evaluates a
prediction-selected pair; it does not choose an implementable action or target.

Use the existing private `replyAwareValue` projection, not a sum with the differently scaled
`disruptValue`. Its baseline remains our WAIT with no own sabotage against the SAME opponent
response. Stage just our instability increment, without early ink, before the private simulation;
the simulation applies placements and the opposing hit before inking/scoring. EVERY combination
is compared with full `Rules.resolve` on the original board. Tests cover simultaneous collisions,
two hits reaching four, both player positions, state isolation and missing/invalid values.

This retains the old completion/risk heuristic and its limitations. A route that inks is rejected
as null, not zero. Projected value may be misleading when the actual game ends, and a sabotage
benefit can be discounted differently by different routes' completion times. Terminal flags and
actual one-turn score leads are separate evidence. No hard immediate-income veto is introduced.
Strict improvements alone count; ties preserve the previous choice. These are limited candidate
comparisons, not exhaustive joint-action search or final-game utility.

## Results

One analysis run on the retained corpus; all positions, candidate sets and old values matched.
No new games or full-play timing measurements. Checked-in regression tests subsequently replay
three selected examples; that is verification, not a repeated sample measurement.

- On **8/81** candidate observations, the two prefixes produce different sabotage targets.
  On **13/81**, at least one target differs from the pre-build target. The target for the original
  chosen prefix matches saved ample full play in **81/81** cases despite omitting leftover builds.
- Against the predicted response, fixed-target joint evaluation changes the preferred prefix
  in **2** observations; adapted-target evaluation changes it in **3**. One of the three needs
  target adaptation. Under the known-WAIT scenario the corresponding counts are **1 / 3 / 2**;
  under the oracle they are **2 / 3 / 1**. These are strict ranking changes, not tie breaks.
- The prediction-selected adapted pair improves oracle projected value in **3** observations,
  worsens it in **0**, and ties in **78**, relative to the old selected prefix plus its own adapted
  target. All three changes are distinct states on three different maps; one comes from source
  v05 and two from source K2/R1. Unique-state counts are **3 / 0 / 76**, not independent trials.
- The old and new selected pairs each lose prefix cells to ink in **one** oracle observation;
  this is the same single-candidate position and was already rejected by the original oracle.
  No selected pair ends a game immediately. The diagnosis does not repair this missing alternative.

Three preserved examples (`src/test/resources/btk/bot/joint-reply-study.json`):

- **Seed 20000 / P1 / t40:** old prefix chooses target 32 (instability 0), alternative chooses
  ready-to-ink target 45 (instability 3). Adapted joint ranking switches from prefix 0 to 1:
  oracle projected values **521.5 vs 1441.46**, current-turn difference improves by **40**.
  Important limitation: already evaluated **prefix 0 + common target 45** scores **2120**, above
  either adapted pair, and improves the current-turn difference by **47** relative to the old
  pair. Thus changing ONLY the target may be preferable under this same heuristic. The normal
  sabotage chooser's preferred target is not necessarily the joint evaluator's preferred target.
- **Seed 20004 / P1 / t10:** both prefixes choose target 9, raising instability from 2 to 3.
  Oracle projected value of prefix 0 drops from **1015 to 324.8**, while prefix 1 remains **583.1**.
  The joint choice switches to prefix 1 with **zero** current-turn score improvement. This is a
  projection/risk effect, not evidence of immediate damage avoided or match points recovered.
- **Seed 20005 / P0 / t20:** old prefix 1 chooses target 37 (instability 0), prefix 0 chooses
  ready-to-ink target 29. Joint values **1204 vs 802.2** favor prefix 0. Its immediate increment
  is **19 vs 30**, i.e. **11 fewer** current-turn points. Applying common target 29 to prefix 1
  instead destroys that future route. The projection prefers a tradeoff, not an immediate gain.

Do not sum these deltas into recovered match points or interpret 3/0 as a win-rate estimate.
Other prefixes, targets, future actions, opponents and arena timings remain outside this test.

Report SHA-256: `b3fe234a7b462814299128a26af63cd025e4b74884202521ef2ea1ce8c4198b5`.
Prior report: `6c407ef3dc420b0cb85257b5be6340b65a302f1a45ca10fd0d313fc3792be7bd`.
Capture manifest: `652b2c28ca30e62ea974d58d740fb9210f8f76d213ed905364102d059f196d0b`.

Verification: **156 tests pass, 264/264 CG replays exact**. Player.java and its source hash are
unchanged. This tools-only step does not rerun or certify external-process timing.

**Decision:** retain v05 and keep reply reranking disabled. There is a concrete interaction worth
testing, not a proven stronger bot. Next bounded experiment: compare pairs from the same two
prefixes and a small deduplicated set of known-state targets (WAIT, pre-build target, both
prefix-specific targets), rather than assigning one target permanently to each prefix. Preserve
the old legal pair, equal scenario coverage and the time reserve. First verify the mechanism and
cost; any strategy implementation then needs a default-off flag, fixed paired strength screen
and submission/process checks under the normal promotion policy. Do not promote from this audit.
