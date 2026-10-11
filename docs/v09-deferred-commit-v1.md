# v09 missed sabotage deadline correction v1

Declared 2026-10-11 before candidate scores or trajectories. The user authorized
one targeted correction, OFF by default, followed only by fixed local position
and control checks. This is an exposed development screen, not a strength trial
or a promotion decision. Selected v09 remains unchanged by default.

## Hypothesis and fixed recipe

The completed full-action diagnostic found real cases in which a native forecast
keeps moving the same attack into the future. It also found valid delayed wins
and adverse effects from earlier attacks. Test only a missed, previously recorded
deadline after preserving the exact complete native construction and checking
each retained opponent reply. Implementation feasibility is uncertain until the
preflight; empirical outcomes must not change this recipe or sample.

Add `Params.USE_ROLLOUT_MISSED_DEADLINE`, default **false**, to current policy.
Frozen version packages and core rules remain unchanged. With the flag OFF,
native actions must remain exactly equal to frozen v09. Use `NOISE=0`, ample
60000ms and original native search parameters for this diagnostic; this does
not establish performance under the 50ms production turn budget.

At a complete native decision, consider a commitment only when all of the
following hold:

1. The current own retained route portfolio is empty and the complete emitted
   native action has no sabotage. Recover its exact selected raw root, including
   network roots and tie order; do not substitute the old WAIT root.
2. That selected root nominates a legal region **r**. Keep its exact emitted
   construction cells, in order, for every comparison; changing the target must
   not rerun filler or remove cells exposed to the proposed hit.
3. In **every** native W continuation reply, the first future own sabotage is on
   that same region r. W uses the full native construction and no initial hit.
   A reply with no future own hit, or another first target, fails eligibility.
4. W has no terminal WIN **or DRAW** in any reply.

These W-only conditions arm or maintain a commitment. Do not evaluate A or Wr0
merely to create memory, or while the stored deadline has not yet passed.
Only once an existing commitment is strictly overdue, apply these additional
intervention guards:

5. A, the same construction with an initial hit on r, has no terminal LOSS in
   any reply.
6. In every reply, A has strictly greater native utility than **Wr0**. Wr0 keeps
   the same full first construction and no initial sabotage, then suppresses only
   subsequent own hits on r; future hits on other regions and all opponent
   actions remain native. This is not the earlier all-future-sabotage W0 ablation.
   Preserve the original own continuation, opponent replies, horizon and scoring.

When eligible and no commitment is active, record r and its absolute deadline:
the **latest** first-hit due turn across the native W replies. Later forecasts
must not move that stored deadline. Observe successive real pre-turn states with
one persistent bot instance. On a later eligible turn, intervene only when
**current turn > stored due turn**, strictly after the original deadline.
Retest W eligibility and the overdue-only A/Wr0 guards against the current known
state before emitting the hit.
The only change to the native complete action is adding the one legal DISRUPT r.
After emitting it, clear the commitment.

Clear memory on init or seat change; observation gaps, repeated or out-of-order
turns; an emitted own disruption; a changed nominated target; a new nonempty own
portfolio; an invalid or inked target; incomplete search or provenance; a failed
W eligibility guard; or a failed overdue A/Wr0 safety guard when those guards are
due to be evaluated. A later eligible decision may create a new commitment after
the old one has been cleared, but cannot roll a still-active deadline forward.
An opponent's partial hit is an observed board change requiring fresh guards,
not an inferred own execution. Keep warm-up state isolated from the live bot.
The hot path must use bounded primitive buffers and preserve the runner margin.

## Fixed sample and information boundary

Reuse the exact selection from `v09-full-action-v1`, pinned through the new
selection's `priorSelection` object. No new outcome may add, remove or replace
a position. The source pool remains all40 verified v09 control games; only the
967 previously nominated empty-portfolio states, spanning39 games, enter the
position check. Their original labels are retained:

- 830 already-disrupting states;
- 39 exploratory construction/no-hit reversals;
- 90 construction/no-hit WAIT-preferred states without that reversal;
- three former native-attack/tie controls;
- five full-WAIT controls.

Repeated-decision starts are the same **56** prior starts: all39 exploratory
states, the first prior state per game among the90 (nine starts), all three
native-attack/tie controls and all five full-WAIT controls. They span23 games
and supply88 retained native-reply comparisons and56 passive comparisons.
Historical **904554072/t37/P0** remains separate, adding two native and one
passive comparison. The six terminal-win controls in907134720/P1, turns
46,47,49,50,51,52 remain included within the39, not additional starts.

These states, labels and outcomes have already been examined. This is deliberately
fixed development evidence, not independent confirmation. Pin exact source
replays, state hashes, prior native full actions, prior selection, and the baseline
sources/default artifact captured before edits. Validate all40 source games and
the historical replay with core before measurement. Historical future commands,
scores and outcomes never enter bot state, memory, forecasts or opponent choices.

## Memory initialization and paired trajectories

For **each** declared start and opponent regime/reply, create separate OFF and ON
bot instances with **fresh empty commitment memory**. Initialize each on the
same start state. There is **no replay-prefix or teacher-forced priming** and no
use of the old counterfactual trajectories as executed candidate history.
Pre-start commitments are intentionally untested. Each bot then persists across
all consecutive turns of its own branch; never reset memory at the next turn.

Both branches start with the complete native no-hit action. Do not force an
attack at offset0. Subsequent actions come from their persistent OFF/ON current
policy on their own resulting states. Use a fresh frozen-v09 probe at each own
decision to verify the native full action and reveal any unintended state/RNG
leakage; it does not replace the persistent candidate memory.

Run exactly **H=min(8,turnsLeft)** resolved turns including the initial action,
stopping only at core termination or an explicit technical failure. No extension
to chase a pending deadline. Native search, emitted actions and board changes
must remain legal. The initial counterpart's construction is identical before
any intervention; later branches may diverge through actual core outcomes.

Opponent conditions are fixed:

- **Primary:** each initial frozen native reply identity separately. Retain that
  initial opponent route portfolio and recompute its native construction/target
  on the current branch board each turn. The initial opponent probe stays fixed;
  do not replace it with each new own-decision probe, replay obsolete commands,
  or read the other player's simultaneous action.
- **Secondary:** pure WAIT throughout. Keep these results separate from native
  reply results and from the target-specific Wr0 forecast ablation.

All967 selected states receive exact OFF-versus-frozen-v09 full-action checks.
Those states are a position equivalence panel, not fictitious ON histories.
Process source observations chronologically where a stateful instance is reused;
do not claim isolated ON calls measure a missed-deadline correction. No additional
ON shadow policy outcomes are part of this declared screen.

## Fixed acceptance criteria

The local screen passes only when **all** conditions below hold:

1. All967 OFF actions equal frozen v09, including cell order and target. Focused
   tests establish reset/isolation, strict lateness, immutable deadlines, legal
   full construction, native reply parity and the target-specific ablation.
2. Every ON intervention obeys the frozen guards and preserves the native full
   construction. No unrelated native decision changes or illegal actions occur.
3. The sustained region33 case **907134651/t38/P1** receives a correction under
   the matched native opponent condition and executes the originally promised
   target earlier than its OFF branch. This must be an actual emitted hit in the
   bounded trajectory, not merely improved proxy utility or a changed forecast.
4. Across every primary reply comparison, preserve each OFF terminal win within
   H and introduce no ON terminal loss where OFF is not already a terminal loss.
   Report all six terminal-win controls individually.
5. At any start affected by a primary ON intervention, no retained native reply
   may have a worse earned score difference or terminal outcome than OFF. A
   favorable pooled score cannot compensate for an adverse reply. Terminal
   outcomes and earned score are separate; a larger score cannot compensate for
   loss of an OFF terminal win or creation of a terminal loss.

The region11 case **907135823/t35/P0** may remain unchanged because a changing
target should invalidate the commitment. Report that result rather than relaxing
the same-target guard. Preserve all other original controls and report every
negative, null, censored or technical result. Fresh memory means an original
deadline may pass before the candidate has a qualifying commitment; do not
retroactively prime it after observing the result.

Report interventions, stored and current forecast targets/deadlines, clearing
reasons and guard failures; actual first hits on the original nominated target;
original promise timing; and final earned score/income, ink events and terminal
W/D/L. Count source states/games and reply comparisons separately. Overlapping
starts and replies are correlated. Refreshed native forecasts use fresh opponent
portfolios and are not the initial fixed opponent that advances a trajectory.

## Verification, retention and decision

Before any candidate score: finish focused tests and independent review; check
engine fidelity; and freeze this declaration, exact selection, relevant production
and diagnostic sources/classes/resources/runtime, and before-edit baseline.
Test artifacts and the default submission stay separate; do not replace a saved
selected artifact with the experimental ON artifact. No timing or strength claim
follows from ample-time correctness. Keep any process verification distinct from
the fixed position/trajectory screen.

The runner must refuse to overwrite completed results. Execute the fixed panel
once. If a technical failure interrupts it, preserve the output and explain the
fault before any corrected continuation; never tune the recipe, extend H, add
states or retry valid outcomes to improve the verdict. Independently reconcile
all identities, guards, counts and paired results and verify the frozen inputs.

Output `positions.jsonl` (967 OFF equivalence rows), `episodes.jsonl` (56 starts),
`historical.json` and a compact report. Retain raw forecasts, memory diagnostics,
trajectories, original declaration, selection, tests and source/artifact pins.
Record the completed result in PLAN/BOTS/DEVLOG and commit it locally under the
repository agreement, excluding unrelated pduhard/ppo work.

A pass is only a local correctness/mechanism result. A failure leaves the default
flag OFF and is recorded without a second tuned candidate. No automatic strength
bench, new boss games, training, promotion, arena upload or push is authorized by
this screen. Any later candidate strength trial needs its own prospective change
and the repository practical-v4 gate on fresh maps.

## Completed result — 2026-10-11

**LOCAL_SCREEN_PASSED; SOURCE_SIZE_BLOCKED; no promotion.** The fixed candidate
is `current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true`; its flag remains false by
default. One complete measurement, with no outcome-driven edits or retries.
The prior sample and eight-turn horizon were unchanged.

All967 OFF actions matched the pinned frozen-v09 complete actions. All OFF
episode trajectories also matched the earlier full-action study's no-hit arms,
including state hashes, ordered actions, scores and termination. All candidate
construction cells were identical to the native full action on that same board.
W/A and the target-specific Wr0 forecasts matched independent calculations using
frozen v09 decisions and core transitions. All1129 frozen files were unchanged
before and after independent reconciliation.

### Primary native-opponent result

The56 starts supplied88 paired native-reply trajectories: **one improved earned
score difference,87 ties,zero regressions**. Only one pair changed a decision:

- **907134651/start38/P1, region33, native reply0:** arm due39 at38, preserve
  due39 while the forecast moves later at39, intervene at40 while W now predicts
 41. Native construction remains the single cell288. OFF first attacks and inks
 33 at42; ON does so at40. Final eight-turn score difference **1026 -> 1071,
 +45**. Both branches remain nonterminal. At the intervention W=1095, A=1084,
 Wr0=933: the correction acts because the remembered deadline failed, despite
 the optimistic new W forecast still preferring another delay.

This reduces lateness from three turns to one; it does **not** repair the
already missed original due39. Original first-hit on-time counts remain60/88,
and nominated-target on-time counts remain47/74 actual promises (14 additional
reply observations contained no such promise). Do not report this as a newly
won game or as improved overall win rate.

All **eight** primary OFF terminal wins remain wins. The six required controls
in907134720/P1 have identical actions, final states, scores and ending turns:

- Start46: WIN at48, score difference1971; first hit47.
- Start47: WIN at49, score difference1969; first hit48.
- Start49: WIN at50, score difference1967; first hit50.
- Start50: WIN at51, score difference1965; first hit51.
- Start51: WIN at52, score difference1963; first hit52.
- Start52: WIN at53, score difference1961; first hit53.

No candidate intervention occurs in these controls. Overall terminal pairs are
79 NONTERMINAL/NONTERMINAL, eight WIN/WIN and one LOSS/LOSS; no lost OFF win or
new ON terminal loss. The previously adverse region45/start73 case also keeps
both native replies unchanged. Region11/start35 remains unchanged under the
native opponent: the first future target changes to39, so the old commitment
clears instead of forcing11. At907135435/start67, the overdue correction at69
is vetoed because A predicts a terminal loss, retaining the nonterminal branch.

### Secondary and historical results

Pure-WAIT opposition yields56 pairs: two positive,54 tied,zero negative; all
remain nonterminal. The region33 intervention is again+45. Region11 at
907135823/start35 now retains its forecast target and is attacked at37 after
due36, improving the eight-turn score difference **-598 -> -551 (+47)**.
This passive-only effect is separate from the primary result.

Historical904554072/start37/P0 has no intervention under either native reply
or pure WAIT. Native arms still attack29 at38 and end-2012; passive arms still
do not attack29 and end-2190. All three comparisons remain nonterminal and
unchanged. The correction is deliberately narrow and does not solve every
unexecuted forecast.

### Verification and readiness

- **64 focused tests passed**, including ten synthetic memory/guard tests and
  independent target-only ablation checks on both seats. Engine validation:
 292/292 baseline replays, all40 source games and the historical replay exact.
- Initial preflight found a stale total-field assertion in the promotion tests:
  it omitted the pre-existing neural-target flag as well as the new flag.
  Updated field accounting and verified reset of the new flag on historical
  parameter loading; all historical measured values and frozen sources remain
  unchanged. A final preflight also added explicit oracle depth/timeout checks.
  These changes preceded measurement; initial logs and protocol revision1 are
  retained. Revision2 only clarifies W-only arming versus overdue-only A/Wr0.
- The merged default current source is still exactly **98,709** characters,
  SHA256 `ab0954a4545f25222fce6bccf8fa11866d276266e96b4728b502c83996d847e5`.
  Frozen v09 is also byte-identical. Disabled experiment blocks and its parameter
  are omitted by FileMerger. The previously saved95278-byte selected export was
  preserved separately; it is not the fresh current merge.
- The enabled candidate compiles with submission-only dependencies, but its
  normal FileMerger export is rejected: **108,524 / 100,000 characters**. No
  candidate artifact was exported, uploaded or process-tested. This technical
  stop remains in force despite the local screen passing. Ample60000ms checks
  do not establish coverage or speed at the production50ms budget.

Decision: retain this default-off implementation as a promising local mechanism
result. Selected v09 stays unchanged. No strength games, boss games, promotion
or upload were performed. Before any later strength/readiness claim, compact
the candidate without changing its recipe, verify the exact submitted process
and production-time behavior, then use a separately declared practical-v4 trial.

Compact [evidence](evidence/v09-deferred-commit-v1.json),
[archive index](evidence/v09-deferred-commit-v1-artifacts.json) and
[complete retained artifacts](evidence/v09-deferred-commit-v1-artifacts.zip).

## Premeasurement clarification

Revision2 clarifies W-only arming and overdue-only A/Wr0 evaluation. The original
revision1 declaration and selection are retained separately. This resolves the
implementation-spec wording before any candidate score or trajectory; the
intervention guards, sample, memory initialization and acceptance criteria are
unchanged. No empirical result informed the clarification.
