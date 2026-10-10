# Neural proposal audit v1 — saved full actions versus v09

Predeclared 2026-10-11 from `8f533d8`, after the user authorized the proposed
small offline check. This is a diagnosis of candidate coverage, not a new bot,
training run or strength comparison. Selected v09 and all policy/core/weight
sources remain unchanged. Apply the codingame-bot-contest workflow and the
project's existing practical-v4 policy to any later strategy experiment.

## Fixed question and sample

Can a recorded full action of the retained **neural-v3** policy supply an
alternative absent from v09's portfolio that v09's existing lookahead values
more highly? Distinguish missing proposals from a changed continuation model,
omitted leftover fill, and merely reproducing an existing action.

- Source: the exact-build-attributed canonical candidate games in
  `build/cg-boss/neural-v3-boss-40/plan.json`, not the later arena cohort with
  uncertain source attribution. Verify the historical individual capture
  identities and candidate build before reconstructing states. Attribution uses
  the retained source and matching replay build markers; the capture explicitly
  does not independently prove which source the remote server executed.
- Take the first four scheduled games for each candidate seat, then preserve
  original schedule order. This selects the first eight slots, without using
  winner, score, policy choices or future trajectory quality for selection.
- Request pre-turn states **3, 10, 20, 30, 40, 50, 60, 70** from each game:
  at most 64 positions on eight reused development maps. Turn 3 follows v09's
  fixed three-turn opening; no opening flags are disabled. Keep genuine early
  terminal omissions; do not replace them with other games or turns.
- Use the candidate's recorded full command on each pre-turn board, including
  its explicit disruption choice. Expand and validate through `btk.core`.
  Do not call a neural model, fit weights, refill its action or consult the
  recorded opponent reply during counterfactual evaluation. A recorded command
  can reflect the original neural bot's deadline cutoff; it is not a newly
  recomputed ample-budget greedy plan.
- Validate all eight entire saved replays with `CgValidate`, reconstruct with
  `Rules.resolve`, and record game/map/state/file identities and requested versus
  expanded actions. Historical actions advance the replay only, never a
  counterfactual rollout. No new games or network requests.

## Comparison semantics

Use fresh current and frozen v09 with the complete v09 parameter snapshot and
only `NOISE=0`. Each play or complete tuple evaluation receives an ample 60,000-ms
wall-clock budget, serially. Require legal outputs, unchanged inputs and exact
current/frozen action, selected-root and value parity. An interrupted or otherwise
invalid diagnostic must be reported as such, not treated as a rejected proposal.

Reconstruct the actually searched raw root/target tuples, including attack and
no-attack branches, root ordering, duplicate suppression and network-after-base
phase. Here "network" in the existing rollout code means the railway graph.
Reproduce the original raw winning tuple/value and emitted action. Obtain every
target-specific complete first action using v09's existing leftover-fill method.
Keep raw and filled action-set coverage separate; identity is the unordered
construction set plus the disruption target. Report duplicate multiplicities.
Keep generated-but-suppressed duplicates visible too: a matching filled action
or construction there also prevents calling the neural proposal genuinely new.

For the primary comparison, score **every distinct complete baseline action,
the actually emitted baseline action, and the one recorded neural action** with
the same existing private v09 rollout. Only the first action differs. All use
continuation route 0 after that action, as arbitrary extra roots do in the
existing implementation, and the same baseline opponent routes, replies,
eight-turn horizon, robustness aggregation and terminal/tail evaluation.
Do not create an oracle opponent or a new value function. Retain the ordinary
root-specific raw scores separately; they are not the comparison denominator.
Use reusable existing root storage transiently and restore it after each probe.

This common continuation isolates the first-action comparison but is a model
assumption, not the policy the neural bot would follow thereafter. Equal complete
actions must receive equal comparison scores. A candidate can be called a
**novel model-preferred proposal** only when absent from the full baseline action
set and strictly better than its best score by more than 1e-9. Also report its
difference from the emitted baseline action, build-only/target-only coverage,
per-game counts, and all nonpositive results. Do not silently count the known
fill-evaluation mismatch as a new neural benefit.

## Completion and interpretation

Before the saved-state measurement: focused meaningful tests of action identity,
core legality, injection/restoration, matching-action scores, continuation and
frozen-baseline reconstruction; baseline 292-replay engine validation; independent
review; pin prospective protocol, selected inputs, full configuration and relevant
source/classes. Keep production, frozen bots, weights and old reports unchanged.

Run the single fixed panel and retain every row, omission, error and command log.
No outcome-guided extension, extra neural arm, parameter sweep, nominal timing
panel, full-game strength batch or automatic production integration follows.
The result establishes at most a candidate-generation opportunity under v09's
own model. Reused neural trajectories do not measure frequency on v09's actual
games, population strength, recovered wins or contest-runtime feasibility.

If no novel model-preferred actions occur, close this bounded proposal check
without adding a hybrid. If they do occur, identify their construction/target
patterns and propose one next bounded hypothesis. Prefer a small deterministic
proposal rule when the evidence supports one; do not infer such a rule from one
example or treat the score proxy as a promotion gate. Production integration
would be a separate version with the existing artifact/process/strength checks.

Archive the eight canonical records, selection/provenance, protocol, tool/tests,
runtime/source identities and one compact result file. Update PLAN/BOTS/DEVLOG
and commit the completed diagnostic locally. No new submission is built because
production and packaging do not change; no upload, promotion or push.

## Completed result — 2026-10-11

**DIAGNOSTIC_COMPLETE_PROPOSAL_OPPORTUNITY.** The one fixed panel completed:
51 live positions from eight games; 13 requested late positions were omitted
because the original game had already ended. No replacements or extra scoring
panel. All eight complete source games reproduce exactly, with no skipped
historical actions. The historical engineered-feature `neural-v3` MLP supplied
the recorded actions; this is unrelated to `spatial-ppo-v3`.

Of the 51 neural full actions:

- 48 are absent from all generated complete v09 actions, including suppressed
  duplicates; 47 introduce a new construction set and one changes only the target.
- **8 beat the best existing complete action under the common evaluator, 12 tie,
  and 31 score worse.** The eight positive cases span six games, four per seat.
  Among the 48 novel actions, the split is 8 better / 12 equal / 28 worse.
- Three actions are already covered. None becomes covered only after fill;
  none matches only a suppressed filled tuple. The target-only novel case ties.
- Comparing only against the actually emitted baseline would produce 14 positive
  cases. The stronger portfolio comparison eliminates six of those claims.
- Reconstruction covers 542 searched root/target tuples, 522 distinct complete
  actions summed across positions and six suppressed tuples. Thirty-eight
  positions have two retained replies; thirteen have one. No fabricated reply is
  added when v09 has fewer routes.

Per-game positive/available counts in the fixed schedule are **1/5, 0/5, 1/8,
2/6, 1/6, 0/8, 1/8, 2/5**. The report retains every row and all terminal omissions.

### What the positive examples establish

All eight have a new construction set. Four contain one construction cell,
three contain two and one contains three; cell count is not paint cost. Two
two-cell proposals use separate board locations, while one is an adjacent pair.
There is no established single cheap construction rule from these examples.

In **seven of eight** cases the disruption target is also absent from v09's
generated complete actions. These are joint construction/target alternatives;
their advantage cannot yet be assigned to construction alone. One cleaner
construction example is game **907064176, turn 20**: neural cells `(8,10)` and
`(13,6)` with target 18 improve the common proxy by 14.5 over the best baseline
full action. Both cells occur separately in existing proposals, but that pair
does not. This is one observed combination opportunity, not a general rule.

Game **907063898, turn 30** has a +1,000,015 proxy difference: the baseline's
common rollout scores -1,000,505 with its terminal-loss bonus, versus neural
-490. That discontinuity is not a million actual points or proof that a real
loss was prevented. Do not average these model deltas into a strength claim.

### Verification and retained evidence

- 68 focused Java tests pass, including seven new synthetic diagnostic tests.
  The first preflight failed to compile a test's captured loop variable; it was
  fixed before measurement. Both attempts are retained. This is a scoped check,
  not a claim that the unrelated in-progress full suite passed.
- Baseline engine validation: **292/292** existing CG replays; selected source
  panel: **8/8** complete replays. Current/frozen-v09 parity, native selection
  reconstruction, legal actions and unchanged inputs/storage pass on all 51 rows.
- Independent review preceded measurement. Suppressed-duplicate novelty was
  clarified conservatively before freezing; both prospective protocol snapshots
  are retained. After the panel, all **581** pinned source, class, resource and
  input files matched. Production bots, core, weights and prior reports unchanged.
- Independent report reconciliation rechecked coverage from the actual tuples,
  reply-score aggregation, maxima, deltas, per-game totals and sample identities.

[Compact result and all 51 action rows](evidence/neural-proposal-audit-v1.json),
[archive index](evidence/neural-proposal-audit-v1-artifacts.json),
[retained evidence](evidence/neural-proposal-audit-v1-artifacts.zip).
The archive includes selected public canonical replays, source and protocol
snapshots, all raw tuples/scores, command logs and tests. Historical private
plan/capture records are referenced by hashes, not bundled. The original lineage
preflight requires those retained local records; this is not a standalone copy
of the entire repository/runtime.

### Decision and one proposed follow-up

Keep selected **v09** and retain this measured proposal opportunity. The signal
justifies investigating missed action choices, but does not establish an arena
gain or justify adding neural inference to the turn budget. In particular, it
does not rescue the rejected `ROOT_FILL` or `ALL_CELL_RISK` experiments.

The one proposed next diagnostic, **not run or automatically scheduled**, is to
cross the two action components on this same fixed 51-state panel: neural
construction with emitted-v09 disruption, and emitted-v09 construction with
neural disruption. Compare them to the original full actions and the unchanged
best-existing-action denominator with the same common continuation. This can
separate construction, target choice and their interaction before choosing a
small v09 modification. No additional training or model size is warranted by
this audit alone. Original spatial roadmap stages 3–5 remain unresolved.
