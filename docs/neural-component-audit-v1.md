# Neural component audit v1 — construction and disruption crossover

Predeclared 2026-10-11 from `d39ed96`, following the user's authorization of the
next step proposed in [neural-proposal-audit-v1](neural-proposal-audit-v1.md).
This is one saved-state mechanism diagnostic. No new bot, training, neural
inference, games, production changes, submission, upload or promotion.

## Fixed sample and question

Use exactly the preceding audit's 51 live positions on eight historical
neural-v3 games, with the same 13 terminal omissions. This is the older MLP
line, not the spatial policy. Preserve original order, both seats, accepted
recorded commands and full emitted v09 actions. Do not select only the eight
previous positive examples, extend the panel or replace positions.

Pin the committed prior summary, raw report, selection, canonical replays and
archive identities. Revalidate all eight entire source games with the existing
loader. Before scoring any new crossed actions, reconstruct every prior row
with the unchanged tool and require exact equality: states, current/frozen v09
choice, raw and filled tuples, full scores, coverage and omissions. A mismatch
invalidates this run; it is not an experimental negative or a reason to retune.

The replay/source attribution limits remain: local artifact and replay markers
match, but remote executed source is not independently proved. Recorded neural
actions can include their original deadline cutoff. No actual future opponent
action enters counterfactual evaluation; historical actions only rebuild states.

## Four fixed actions

For each position, use these complete first actions without changing either
component, preserving the construction order as recorded/emitted:

- **E**: emitted v09 construction and emitted v09 disruption.
- **C**: recorded neural construction and emitted v09 disruption.
- **D**: emitted v09 construction and recorded neural disruption.
- **N**: recorded neural construction and recorded neural disruption.

Do not refill construction after swapping the target, pick another target,
repair a move or rerun either policy to make a combination look better. Expand
through `btk.core`. If a crossed action is illegal, retain its reason and missing
score; exclude it only from comparisons requiring that action, with explicit
denominators. Missing is not a loss, zero effect or evidence of interaction.

Use `v09?NOISE=0`, ample 60,000-ms play/tuple budgets, the same private rollout,
retained own continuation route 0, original opponent routes/replies, depth,
robustness and terminal/tail evaluation as the prior audit. All four actions
share the same context. Independently reproduce E/N per-reply values exactly;
identical full actions must have identical values. Borrowed root storage,
input state, parameters and selected diagnostics must remain unchanged.

**H** remains the prior best complete existing v09 action under that common
continuation. It is not replaced by E, native root-specific values or a new
target search. Report C/D coverage against all generated filled actions,
including suppressed duplicates, and gaps to both E and H.

## Predeclared summaries and interpretation

Across all 51 states, report better/equal/worse/missing for C and D versus E and
H, per-game counts and every row. Strictly better means a gap greater than
1e-9. Report the original eight N>H novel examples as a clearly conditional
subgroup, with four threshold categories: only C exceeds H; only D exceeds H;
both separately exceed H; neither separately exceeds H. Missing components
form a separate unavailable category.

Retain conditional construction effects C−E and N−D, conditional disruption
effects D−E and N−C, and arithmetic interaction N−C−D+E, including per-reply
values. The threshold categories and interaction sign are different facts:
neither alone clearing H does not prove positive interaction. Robust aggregation,
terminal bonuses and the fixed continuation can all affect the arithmetic.
Do not average large terminal-bonus gaps into claims about real points or wins.

Choose at most one bounded next strategy hypothesis from the measured pattern
and previous rejected experiments. If the cause remains mixed, say so. This
diagnostic does not establish on-policy frequency, strength, timing feasibility
or a sufficient reason to ship the whole neural model. No outcome-guided extra
arm, new scoring panel or automatic implementation follows this run.

## Completion

Review the new tool and focused synthetic tests before measurement; validate the
292-replay baseline corpus and the eight source games. Freeze source, classes,
configuration, protocol and inputs. Run the panel once, preserve errors and
omissions, verify pinned files afterwards, and independently reconcile the
result. Archive compact evidence with references to the prior archive. Update
README/PLAN/BOTS/DEVLOG and commit locally under the standing agreement. Keep
the original audit and unrelated in-progress files intact. Any later strategy
experiment uses the existing practical-v4 policy and exact-artifact checks.

## Completed result — 2026-10-11

**DIAGNOSTIC_COMPLETE_TARGET_PROPOSAL_PRIORITY.** All 51 prior rows reproduced
exactly before the crossover phase. The single panel completed on the same
eight games with the same 13 terminal omissions. All 204 E/C/D/N actions were
legal; no component was repaired and no score was missing.

Across all 51 positions, against **H**, the best complete existing v09 action:

- Construction-only replacement **C: 3 better /15 equal /33 worse**. The three
  improvements occur on three games and all three are new full actions.
- Target-only replacement **D: 7 better /24 equal /20 worse**. The seven
  improvements occur on six games and all seven are new full actions.
- Against the actually emitted E, C is **8 better /18 equal /25 worse** and
  D is **9 better /33 equal /9 worse**. These are distinct denominators: blindly
  replacing v09's target can hurt, even on this development panel.

Among the original eight positive full-neural examples, **five** exceed H only
with the target replacement, **two** only with construction replacement, and
**one** with either replacement separately. None requires both replacements
to clear H. This threshold observation does not establish additive behavior:
the eight arithmetic interactions are one positive, four zero and three negative.
Across all 51, the corresponding interaction counts are 7/39/5.

Target-only positive/available counts in the original eight-game order are
**1/5, 0/5, 0/8, 2/6, 1/6, 1/8, 1/8, 1/5**. The construction-only counts are
**0/5, 0/5, 1/8, 1/6, 0/6, 0/8, 0/8, 1/5**. All rows, including the negative
results and per-reply effects, are retained.

### Concrete distinctions

- Game907063775/turn20: changing only target 5→33 scores 445.5 versus E/H=411.5;
  changing only construction scores 411.0. The full neural action scores 445.0.
  The target supplies the positive model change in this example.
- Game907064062/turn30 was not in the original eight positives. Its full neural
  action is 23.5 below H, while retaining v09's construction and changing WAIT
  to target37 is 81.5 above H. Measuring all 51 positions matters.
- Game907064176/turn20 retains target18: the new two-cell combination remains
  the construction-only example, +14.5 above H.
- Game907063898/turn30 assigns the +1,000,015 difference to construction: C=N
  at -490, D=E=H at -1,000,505. The existing terminal-bonus discontinuity remains;
  this is not actual gained points or a demonstrated recovered game.

The seven D>H gaps are 7 to 145 in the existing proxy. Do not convert them into
expected real points or win rate. The construction/target conclusions apply
only to these recorded actions, fixed replies and retained continuation.

### Verification and evidence

75 focused tests pass, including seven new synthetic crossover tests, with no
failed preflight attempt. Baseline CG validation is **292/292** and source-game
validation **8/8**. Every prior row, original E/N reply score, H, parameter set,
portfolio identity and input state matches; contexts restore after evaluation.
All **595** frozen files and **four** runtime/dependency files match after the
panel. Independent source review and report reconciliation pass. Production,
core, weights and the previous experiment remain unchanged.

[Summary and all action rows](evidence/neural-component-audit-v1.json),
[archive index](evidence/neural-component-audit-v1-artifacts.json),
[retained archive](evidence/neural-component-audit-v1-artifacts.zip).
The archive includes prospective protocol, new tool/tests/helper, logs, public
replays, prior raw report and compact evidence, source snapshots and this result.
It references the prior archive by hash instead of embedding its ZIP. Private
historical lineage records and the complete build environment are not bundled;
the original lineage loader still requires those retained local records.

## Decision and one bounded next hypothesis

Keep **v09** selected. Prioritize a target proposal over transferring neural
construction. The next proposed candidate is a **target-only adapter**: retain
the full emitted v09 construction, propose at most one legal alternate target
using frozen neural-v3's sabotage scorer, compare E and D with identical complete
rollouts, and keep E on a tie, cutoff or unavailable proposal. Never compare D's
full-action score to the native raw selectedValue, and never refill after the
target switch. This candidate is proposed, not implemented or evaluated here.

Its first boundary must be feasibility, with no training: exact target-only
feature extraction, submission size and remaining turn time. The sabotage head
has 29→32→16→1 units and 1,505 weights, but its encoded weights alone consume
4,340 characters against about 4,722 characters of retained v09 headroom. The
existing complete feature class and scorer cannot simply be added. Construction-
only feature work can be omitted, but route searches, public-board history and
hypothetical region destruction remain; no timing benefit is established.

Historical neural-v3 used noise and construction-head RNG draws, and a hybrid
will have its own state/history. A deterministic target adapter is therefore
a new proposal policy, not a guarantee of reproducing these recorded targets.
History must record the action actually emitted, including fallback. Stop that
prototype if it cannot meet artifact and timing constraints without changing
v09's required search; no automatic strength run or feature simplification.

This proposal is distinct from merely enabling the failed heuristic
`SECOND_TARGET` experiment, although the present panel does not prove the neural
target differs from that heuristic runner-up in every position. Historical
live-target, always-disrupt, track-value, joint-action, ROOT_FILL and ALL_CELL_RISK
failures remain intact. Original spatial stages 3–5 remain unresolved.
