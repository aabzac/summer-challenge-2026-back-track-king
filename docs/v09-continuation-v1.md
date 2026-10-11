# v09 continuation diagnostic v1

Reproduce with `gradlew v09ContinuationStudy -Pout=<new-output-directory>`.
The fixed retained replay paths must be present; output refuses overwrite.

Declared 2026-10-11 before diagnostic results. The user requested diagnosis of
repeatedly deferred sabotage and missed ways to finish a game, then selection of
one promising improvement for a later experiment. This is an offline mechanism
study, not a strength trial or a change to selected v09.

## Fixed input and information boundary

Use **all 40** v09 control games listed, in slot order, by
`docs/evidence/neural-v7-control-boss.json`. Verify its pinned plan, each replay
hash, game id, seat, seed and map identity. This is the October 4 control cohort,
not a selection of losses. Revalidate every complete replay with `CgValidate`.
These previously exposed boss maps are development data. Inspect **every live
pre-turn state** on the v09 seat, retaining all terminal turns and outcomes.
The known historical v07 game **904554072, pre-turn 37, P0** is a separately
labelled regression case; never count it as a v09 trajectory or in cohort rates.

Reconstruct states only through `btk.core`. On each state run frozen
`v09?NOISE=0` with an ample 60,000-ms diagnostic allowance, so source policy,
proposal limits and continuation are unchanged while timing/noise do not hide
the mechanism. The recorded live actions and deterministic ample decisions are
distinct observations. No nominal-time or arena-strength inference is permitted.
Recorded current/future opponent actions may be used only in explicitly labelled
oracle checks, never by the policy, proposals or target ranking.

## Sabotage postponement

Record the actual and ample decisions, both route counts, complete-search status,
and raw WAIT-root target. On all non-opening states with an empty own route
portfolio and a legal nominated WAIT-root target, retain both native opponent
reply values and exact eight-turn traces for attacking now and waiting first.
Reproduce every native trace value through the unchanged private rollout.

Independently measure two counterfactuals, with identical initial construction,
horizon, evaluation and scenario aggregation:

1. For the empty own no-construction/no-disruption root only, disable **future
   own sabotage** throughout the forecast. Leave the attacking branch and both
   opponent scenarios unchanged. This tests uncommitted future own actions.
2. Replace the opponent with **pure WAIT**, separately for attacking and waiting.
   Keep our native continuation. This diagnoses opponent-model mismatch; it is
   not combined with the first change and is not a strength reference.

Distinguish a native preference for WAIT, a promised later attack, an actual
emitted no-construction WAIT (final fill can change a raw root), and repeated
recorded waiting. Count games/contiguous episodes separately from correlated
turns. Report score improvements as proxy units, never recovered match points.

For the historical state and at most the **first qualifying state per cohort
game**, run eight successive replanned decisions against pure WAIT, stopping at
true termination. Qualification: empty own portfolio, full emitted WAIT,
nominated attack, native WAIT preferred, and disabling future own sabotage makes
that attack strictly preferable. Compare unchanged v09 with a diagnostic selector
that replaces full WAIT by that same existing attack only under that condition.
This bounded branch probe checks persistence/completion of destruction; it is
not a registered candidate, match-win test or evidence against an active boss.
No outcome-driven extra starts, retuning or sample expansion.

## Finishing opportunities

At every state exhaustively inspect legal regions at instability 2 or 3.
Hypothetically ink each with core and retain regions whose removal leaves **no
desired pair potentially connectable**. Evaluate exact simultaneous transitions:

- one own hit versus a passive opponent, preserving complete ample construction;
- at instability 2, one own hit plus one same-region opponent hit, separately
  labelled cooperative/two-hit;
- the same own alternatives versus the **recorded** opponent action, labelled
  one-turn oracle, and compare with both actual and ample baseline actions.

Additionally sweep all legal own regions at instability 2/3 against that fixed
recorded opponent action, including joint closures of **two different regions**.
Record whether the opponent would already close the board without our hit.
These extra opportunities are oracle-conditional and never unconditional wins.

Separate topology closure, turn-limit termination, wins, draws and losses.
For a legal **one-hit** cut that removes all potential desired paths, the banked
score decides the result against every legal simultaneous reply: construction
cannot restore potential connectivity through ink, additional sabotage only
removes cells, and no active connection can score. Verify this invariant through
core and tests; report these guaranteed closures separately from conditional
two-hit and other oracle wins.
Expose the exact native chooser's shortlist and chosen target after the same
construction. A missing shortlist entry, an unchosen winning action, and a
different action that already wins are different counts. Include all regions,
negative findings and losing closures; do not count duplicate opportunities as
independent games. Do not call oracle or cooperative wins unconditional wins.

## Verification, record and decision

Add tools-only helpers and tests for both seats, native rollout parity,
counterfactual isolation, simultaneous double hits, score/timing boundaries,
legal actions and input/context preservation. No bot/core/parameter/weight edits.
Review before measurement; run focused tests and baseline replay validation.
Pin sources, compiled classes, configuration, protocol and all input identities
before the single complete panel; verify afterwards. Reports refuse overwrite.
Preserve source replays and full per-position evidence in an archive, with a
compact checked-in summary. Record corrections/failures rather than retrying
measurements for a preferable result.

Choose one later hypothesis by reproduced decision mechanism, coverage across
games, expected cost and risk of removing useful waiting. A null result is valid.
Do not launch a production change, strength bench, upload or promotion as part
of this diagnostic. A future candidate needs its own declared practical-v4
300/+200/+500 local sequence, valid cached-control boss stage and exact-artifact
checks. Update PLAN/BOTS/DEVLOG and commit the completed diagnostic locally.

## Completed results — 2026-10-11

The declaration above was frozen before the single panel; its original bytes are
retained as `prospective-protocol.md` in the evidence archive. All **40 games /
2,936 live states** were inspected: 20 games per seat, source outcomes 15W/25L.
These are correlated states on exposed development maps, not 2,936 independent
trials. Frozen v09 with ample time is not a reconstruction of historical timing.

### Primary postponement diagnostic: no qualifying full-WAIT change

The corpus contains 83 recorded full-WAIT states, 78 ample full-WAIT states, and
967 empty-portfolio states with a nominated attack. Exactly **five states in one
episode/game** both emit full WAIT and prefer it while forecasting a later attack:
game **907134780, P1, turns 87–91**. None changes the pairwise preference when
future own sabotage is removed. At turn87 the proxy values are attack
**−1,001,115**, native WAIT **−1,127**, and no-future-sabotage WAIT **−1,127**.
The attack forecasts a losing terminal state; the large gap includes the terminal
utility bonus. Against pure WAIT both alternatives tie at −1,127. This is not
evidence of a profitable attack being repeatedly postponed.

Consequently **zero cohort positions qualify** for the declared eight-step
replanning probe; `episodes.json` is empty. We did not widen its eligibility after
seeing this result. The 470 rows where attack beats no-future-sabotage WAIT are
not 470 fixes: many already preferred attacking under the native evaluation.

The separately labelled old v07 state **904554072/t37/P0**, evaluated with v09,
reproduces the raw forecast discrepancy: attack **−2,044**, WAIT **−2,012**,
no-future-sabotage WAIT **−2,457**. With a passive opponent attack is −1,656 versus
WAIT −1,745. However, v09 actually emits three construction cells rather than
full WAIT. Both declared repeated-decision branches therefore retain exactly the
same actions over eight turns; final score is 938:3128, with no termination.
This old case establishes a raw continuation mechanism, not a repaired v09 game.

### Finishing diagnostic: shortlist gaps, no recovered win in this cohort

The exhaustive scan finds **708 position/region cuts**, including **10 ready
one-hit winning cuts in ten games** and 25 cooperative two-hit winning cuts.
Three ready winning regions are absent from every native raw-root shortlist:

- **907134651/t48/P1**, region25, banked advantage1026; v09 attacks37.
- **907134958/t46/P0**, region16, advantage381; v09 only constructs.
- **907136431/t65/P0**, region33, advantage1121; v09 attacks8.

In each case the recorded boss action supplies the fourth hit to the winning
cut, so the original action already wins that same turn. The other seven ready
cuts are used. Across all ten states both recorded and ample baseline actions
already win against the recorded opponent: **zero missed realized immediate
wins**, despite three missed opportunities to guarantee the result independently
of the opponent. This distinction does not mean the shortlist is complete.

The broader recorded-opponent oracle finds winning closures in 17 states across
14 games. Three earlier closures are missed, on the preceding turns47/45/64 of
the same three games. Each needs our hit together with the recorded boss hit to
an instability2 region; it fails against WAIT. All three original games are
already wins. Neither these conditional one-turn gains nor the three shortlist
omissions demonstrate recovered match wins. Synthetic tests do prove that an
omitted guaranteed winning cut can matter in other possible states.

### Exploratory construction subgroup — hypothesis generation only

A **post-panel, read-only subgroup** of the existing rows includes full actions
with construction but no sabotage. There are 247 such empty-portfolio states in
27 games; native raw WAIT is preferred in129/21. In **39 states /15 games /
27 contiguous same-target episodes**, removing future own sabotage reverses that
raw pairwise preference. All39 have construction in the ample emitted action;
**18 states /8 games** also omit sabotage in the recorded action (17 construct,
one is full WAIT). Pure-opponent-WAIT comparison prefers attack in31/15.

For example, **907134857/t26/P0** emits `PLACE_TRACKS 10 2` and no sabotage.
The nominated region is17; raw attack is −219, raw WAIT −211, and WAIT without
future sabotage −231. These are proxy branch values, not recovered points.

Important counterexample: six rows in winning game **907134720**, P1 turns46,
47,49–52, value native WAIT around **+1,001,970** because it forecasts a terminal
win, versus roughly +1,980 for immediate attack. Removing future sabotage erases
that winning continuation. A blanket early-attack rule could discard useful
waiting. Million-unit differences are terminal utility bonuses.

These forecasts use the **raw empty construction root**, before final fill.
The chosen raw-root identity was not logged; a full action may originate from
network search. The nominated target remains the same after full construction
in all39 cases, but this does not establish equal resulting states or values.
The subgroup neither proves that the emitted action is wrong nor identifies the
best alternative across all roots. No additional simulations or expanded
replanning episodes were run after this exploratory finding.

## Selected next hypothesis

Prioritize **construction-preserving evaluation of deferred sabotage when the
own route portfolio is empty**, including turns that spend leftover paint.
The proposed improvement is to avoid valuing a no-attack decision solely through
future own attacks which repeated replanning may keep postponing. It has wider
observed mechanism coverage than the full-WAIT selector, while closure guards
show no realized win benefit in this cohort.

Before a strength candidate, declare one bounded full-action experiment:

1. Retain native selected-root and fill provenance. Compare the **same complete
   construction** with the original no-attack choice and the existing nominated
   target; preserve opponent scenarios, depth and aggregation.
2. Add the isolated no-future-own-sabotage comparison and inspect bounded repeated
   decisions, including the terminal-win counterexamples and declared controls.
   Diagnose whether a promised attack is executed and whether a forecast delayed
   winning closure is lost. Keep oracle replies out of policy evaluation.
3. Only if that mechanism survives, implement one default-off Params hypothesis
   in current, retaining v09 on ties, incomplete comparisons or deadline expiry.
   Check construction legality after target changes and the real 50-ms budget.
   Freeze/version it and apply practical-v4 on fresh local maps; verify/refresh
   the boss cache after this outcome-guided development cycle before its stage.

This follow-up was **selected, not implemented or strength-tested**. The current
diagnostic does not justify forced sabotage, a strength claim, or promotion.
Selected v09 remains unchanged; the closure guard stays a lower-priority
robustness idea.

## Verification and retained evidence

- **17 focused tests pass**: nine closure, five continuation, three integration.
- Baseline **292/292** CG replays exact; panel **40/40** and historical **1/1**
  independently revalidated before evaluating positions.
- Every baseline trace matches the frozen private rollout's scalar and full
  core state; counterfactual isolation, both seats, legal simultaneous actions,
  terminal boundaries and caller/context preservation are checked.
- **665 frozen files** (sources, classes, resources, configuration, runtime,
  default submission and input identities) match before/after the panel.
  Later result documentation edits are outside that completed measurement.
- Independent source, provenance and raw-result reviews agree. One seed-format
  comparison was corrected before measurement. A postprocessing field-name
  typo was corrected once; only reconciliation reran, not the panel.
- No bot/core/parameter edits, new games, candidate submission, upload, promotion
  or push. Exact candidate process checks do not apply to tools-only diagnostics.

[Compact evidence](evidence/v09-continuation-v1.json),
[archive index](evidence/v09-continuation-v1-artifacts.json),
[full evidence archive](evidence/v09-continuation-v1-artifacts.zip).
The archive preserves all2,936 rows and full traces, 40 source replays plus the
historical case, original declaration, source snapshots, test XML and run log.
Extract its repository-relative paths into a separate checkout to reproduce;
installed Java/Gradle dependencies are required and their hashes are retained.
