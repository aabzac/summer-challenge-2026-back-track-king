# spatial-label-audit-v1 — fixed label and action-equivalence audit

## Prospective contract

Declared 2026-10-10, before scoring this audit. This is one bounded diagnostic
inside original roadmap stage 3, following spatial-ppo-v9. It creates no policy,
weights, training run, new teacher games, or submission. Selected v09 is unchanged.

Question: how much of the observed low imitation agreement can be accounted for
by construction ordering or contradictory labels for exactly identical observed
inputs? Separately measure whether the frozen students' actual complete plans
reach the same next state as the recorded teacher. A teacher disagreement is
not itself evidence that the student's action is strategically worse.

### Fixed sources and coverage

- Use the complete original v4 teacher corpus and its original map split:
  60 terminal games, 30 maps, 5,660 turn roots and 22,640 conditional rows.
  Training: 48 games / 24 maps / 4,548 roots / 18,192 rows;
  validation: 12 games / 6 maps / 1,112 roots / 4,448 rows.
- Evaluate the frozen spatial-ppo-v4 and spatial-ppo-v9 payloads. Their SHA-256
  identities are respectively
  `89de11f4239844c55c6e61c987ee2443fd5d79a1c71af782d2686e836ec6f3ef`
  and `fd616a7f454792ec47d3cfc8e5a10f63becfb889d06dbd64e0dea8bf053b4cb6`.
- Include every recorded root/row, without selecting errors, easy cases, early
  turns, favourable games or alternative checkpoints. Keep the existing split.
- These are previously exposed development data. Results are descriptive,
  with no fresh generalization, strength or promotion claim.

### Measurements

1. Reconstruct every recorded teacher trajectory from its saved initial map and
   simultaneous actions using `btk.core`. Verify pre-state identities, expanded
   and accepted plans, income, scores, events and terminal boundaries. Regenerate
   the conditional observation stream and match each original raw binary hash.
   No teacher is called again and no future recorded trajectory is attached to
   a diverged student state.
2. Hash all observed input fields, legal masks and the explicit decision stage,
   excluding the label and raw turn bookkeeping. Count exact repeated inputs
   with conflicting recorded labels, preserving row provenance. Report majority
   label agreement bounds for this finite recorded multiset, by split and
   decision category, with forced choices separated. Byte-identical grouping is
   conservative: it does not identify all functionally equivalent observations.
   It is not a global learnability or model-capacity ceiling, and does not prove
   that any particular hidden variable or teacher randomness caused a conflict.
3. On each recorded teacher prefix, score the original next label and the set of
   still-unbuilt teacher cells. The latter contains STOP only when none remain;
   disruption retains the original single target. Report masked greedy agreement
   and probability mass for both targets, separating forced and unforced choices.
   This is an ordering diagnostic, not a replacement training objective.
4. Run both actual frozen greedy policies from each recorded root with ample
   60,000-ms budgets and their own prefixes. Validate complete actions through
   core. Report ordered construction agreement, unordered construction agreement,
   disruption agreement, and both forms of complete-plan agreement. Do not stitch
   teacher-prefix predictions into a purported student plan.
5. Resolve each student plan with the same recorded simultaneous opponent plan.
   Compare the complete semantic next state, including active derived paths, with
   the teacher successor. Report equal immediate income but different next state
   separately. No later counterfactual rollout or optimality claim is made.
6. For each teacher plan, enumerate every permutation of its construction cells
   (at most six, including the original), retaining disruption and opponent plan.
   Check actual core next-state identity, rather than assuming order is irrelevant.

### Execution, verification and retention

Implement isolated offline tools and meaningful synthetic tests; do not edit
historical bots, models, data helpers or core. Verify engine fidelity with
`cgValidate` and run the new tests before collection. Freeze this pre-results
contract, executable sources/classes, corpus/cache identities and historical
tracked source/artifact hashes before real-data scoring. Run the complete fixed
audit once into fresh output directories; preserve technical failures, if any.
Infrastructure repairs do not authorize changing coverage or outcome criteria.

The complete raw collector output, duplicate-input provenance, summaries, tests,
commands and freezes are retained in a compact archive. The v4/v9 evidence archives
are hashed dependencies rather than duplicated corpora/checkpoints. Verify frozen
identities again before appending results and committing the completed diagnostic.

No practical-v4 strength batch or new process submission is created: this audit
changes no strategy or shipped runtime. Their historical results remain in force.
It cannot pass the original stage-3 strength objective. The result may motivate
one later objective/capacity experiment, but no further training is part of this
contract. Record results in PLAN/BOTS/DEVLOG and make an authorized local commit;
no push, arena upload or promotion.

## Results

Completed 2026-10-10. All 60 recorded games / 5,660 roots / 22,640 rows were
included, with both frozen students evaluated at every root. All 60 regenerated
binary streams match the original raw hashes. Core replay matched every saved
pre-state, accepted plan, income, score, collision/ink event and terminal boundary.
Collection took 527.187 s; reduction took 38.664 s excluding Python startup.
There were no collection or reduction failures, missing roots or interrupted calls.

Exact-input contradictions exist, but their measured contribution is small.
There are 340 repeated-input groups containing 680 rows; 37 groups contain two
different labels, forcing at least 37 errors among 22,640 recorded rows.
The finite-multiset majority-label bound is 99.8366% overall, 99.8406% on training
and 99.8201% on validation. Training unforced BUILD has 28 conflicting groups and
a 99.7166% bound. There are no cross-split exact-input duplicates. These bounds
are loose, dominated by unique inputs; they do not establish that this network
can achieve them or exclude other representation aliases. Hidden teacher state
and absolute seat are possible explanations, not identified causes of every
observed conflict.

Construction order is interchangeable in this corpus: **all 21,702 permutations**
across all 5,660 roots reach exactly the same semantic successor as the teacher,
with the same saved simultaneous opponent. That includes 17,007 training and
4,695 validation permutations. This measured invariance does not make arbitrary
different construction sets equivalent.

On recorded teacher prefixes, allowing any remaining teacher construction gives
a modest increase in unforced BUILD agreement:

- Training V4: 3,388/9,880 (34.2915%) exact next label, versus 3,769/9,880
  (38.1478%) in the remaining teacher set. Mean target probability rises from
  0.212634 to 0.237897.
- Training V9: 3,353/9,880 (33.9372%) exact, versus 3,715/9,880 (37.6012%)
  in the remaining set. Mean target probability rises from 0.204899 to 0.229197.
- Validation V4: 817/2,608 (31.3267%) exact, versus 896/2,608 (34.3558%).
  Mean target probability rises from 0.184666 to 0.207677.
- Validation V9: 768/2,608 (29.4479%) exact, versus 855/2,608 (32.7837%).
  Mean target probability rises from 0.178078 to 0.201061.

Disruption is unchanged by this diagnostic. Validation unforced agreement over
both heads rises from 39.5430% to 41.6667% for V4 and from 38.4140% to 40.7527%
for V9. Forced choices are counted separately in the retained report.

Actual own-prefix full plans improve even less when ignoring construction order:

- Training V4: 599 ordered matches become 609 unordered matches out of 4,548;
  V9: 604 become 617.
- Validation V4: **77 become 80 out of 1,112**; V9: **63 become 65**.
  Full semantic next-state agreement is respectively **80/1,112 (7.19%)** and
  **65/1,112 (5.85%)**.
- Over all 5,660 roots, V4 changes from 676 ordered matches to 689 unordered
  matches; V9 changes from 667 to 682. In this measured sample, semantic successor
  agreement exactly equals unordered full-plan agreement for both students.

These complete-plan measurements use the students' actual conditional prefixes.
For comparison, teacher-prefix all-four remaining-set membership is 90/1,112 for
V4 and 83/1,112 for V9; those numbers must not be presented as actual full-plan
agreement. Merely relaxing a conditional label does not supply a consistent
alternative student suffix.

Equal immediate income is a much weaker condition. On validation it occurs in
588 V4 roots and 616 V9 roots, but **508 and 551** of those respectively have a
different full successor. Across all roots, 2,658/3,347 income-equal V4 choices
and 2,735/3,417 income-equal V9 choices have different states. No strategic or
future-return equivalence follows from their same immediate income.

Verification: **7 Java tests and 9 Python tests passed**, plus **292/292 exact CG
replays**. The first compile preflight caught a boolean `unknownActions` field
mistakenly used as a collection; it was corrected before tests, source freeze and
any real-data scoring. The failed compile log is retained. An independent review
found no remaining blocker. Final identity verification passed for **895 historical
tracked files and 448 frozen evaluation files**, before appending these results.
No core, historical policy, weights, selected strategy or shipped runtime changed.

Decision: complete this diagnostic within original stage 3. The measured ordering
penalty and exact-input contradictions do not account for most of the present
fitting gap. Low agreement already exists on training states under teacher
prefixes, so learner trajectory shift alone cannot explain it. This does not
identify insufficient capacity versus optimization, prove imitation is the best
route to strength, or prove that an order-aware objective could not help.

The next proposed bounded check is a small, deterministically selected TRAIN
subset with no detected exact-input label conflicts, keeping all four rows per
root and the unchanged V4 architecture/objective. A fixed-budget fitting probe
would ask whether the current pipeline can learn those same examples before
changing network size or starting another full strategy run. It would diagnose
fitting only, with no arbitrary agreement percentage used as a strength gate.
This is a proposal; no such training was launched here. Original stage 3 remains
open and v09 remains selected. No strength batch, submission, boss call, upload
or push was performed.

Evidence: [machine-readable report](evidence/spatial-label-audit-v1.json),
[archive index](evidence/spatial-label-audit-v1-artifacts.json),
[retained archive](evidence/spatial-label-audit-v1-artifacts.zip).
The archive contains every raw root comparison, every duplicate-input group,
the pre-results contract/freezes, source/tests, command/exit records and logs.
The unchanged v4/v9 archives are hashed dependencies.

## Reproduction

Restore the recorded v4 corpus/cache lineage and unchanged V4/V9 dependencies.
The inflated cache is disposable and can be rebuilt with the frozen `v4_data`
helpers; preserve its original directory lineage when reproducing this exact run.
Run `gradlew test --tests btk.tools.spatial.SpatialLabelAuditTest`, Python unittest
discovery for `test_label_audit.py`, and `gradlew cgValidate`. The archived
`build/spatial_label_audit_evidence.py` records commands, creates the prospective
source freeze and verifies it after collection.

After `gradlew spatialRuntime`, obtain Java/classpath from
`build/spatial-runtime.json` and invoke main class
`btk.tools.spatial.SpatialLabelAudit` with the original corpus directory and a
fresh output directory. Then run:

```text
python tools/spatial/label_audit.py --java-manifest <collector>/manifest.json --freeze <flat-source-freeze.json> --output <fresh-report.json>
```

The flat freeze maps repository-relative paths to SHA-256 strings, including
the new audit tools, unchanged models/core and source data. Outputs are write-once;
the original command records and runtime paths are retained in the archive.
