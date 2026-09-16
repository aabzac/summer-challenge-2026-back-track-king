# Quick-income candidate under practical-300-1000-v1

## Protocol registered before new games, 2026-09-16

The user explicitly requested adopting the practical selection policy and applying it to the
latest candidate. This supersedes the old prohibition on further quick-income confirmation,
not the historical result or its old verdict. There is no strategy or parameter change.

- Candidate A: `current?USE_ROLLOUT_QUICK_ROUTE=1`, selected control B: frozen `v06`.
  Common opponent C: frozen `v05`. Implementation and complete effective parameters must match
  the original `quick-income-v1` reports; source and class hashes are verified before launch.
- Reuse the completed 300-game direct screen, **160-134-6, 54.33%**, seeds 40000..40149.
  The adoption is retrospective. This score is below 60%, so the next stage is fixed at
  **1000 new games**, seeds **42000..42499**, both sides of 500 distinct generated maps.
  Direct pass requires >=55% in these 1000 alone, draws counted half; no pooling with the 300.
- If the direct stage passes, A vs C and B vs C receive **300 games each**, the same 150 fresh
  maps, seeds **43000..43149**, both sides. Candidate-control difference >=3 percentage points
  passes the common-opponent stage. Compute differences on matched map/side results.
- Otherwise, conditional on direct success, A vs C and B vs C each receive **1000 new games**,
  the same 500 maps, seeds **44000..44499**. Candidate score >= control score passes; ties pass.
  These 1000 alone decide the stage. The old unmatched 200-game v05 result is diagnostic only.
- Stop promotion evaluation if the direct 1000 fails; do not spend common-opponent games on a
  candidate already rejected by this rule. Stop on bot errors, input/identity mismatch or an
  invalid/missing report. No tuning, retry, early win-rate stopping or extra continuation.
- Authentic generator, nominal CG budgets 486/36 ms, two workers, bot RNG SEED=1. Execute runs
  sequentially without concurrent heavy jobs. Each stage excludes all known prior map hashes
  and earlier stages; the two members of a matched stage intentionally share its maps.
  Reserved confirmation-v4 stays unused. All results, including errors and timing outliers,
  are retained. Intervals are descriptive; this policy has no overall 95% statistical guarantee.
- If both strength stages pass, verify the unchanged candidate's archived submission and strict
  process evidence before a readiness report. Keep the flag off and selected v06 until promotion
  is explicitly authorized. This request authorizes evaluation, not arena upload or promotion.

Policy and protocol are snapshotted before play under `build/analysis/quick-income-practical-v1/`.
Full benchmark reports are saved under `build/bench/quick-income-practical-v1/`; compact durable
evidence and the decision are recorded after the required branches finish. Preserve original
reports and verdicts in `quick-income-experiment.md` and `docs/evidence/quick-income-v1.json`.

## Results

The fixed continuation completed **1000 games / 500 distinct maps**, seeds 42000..42499:
**525 wins, 465 losses, 10 draws**, score **53.00%**. Required score: **55.00%**.
**Direct stage NOT PASSED.** The complete series took 1704.1 seconds (28m24s), with zero bot
errors. No retry, changed parameters, discarded games or pooling with the old 300-game screen.

The descriptive map-grouped 95% interval is **46.93-59.07%**. It is not the decision criterion;
the candidate fails the new practical threshold itself. This result does not prove equivalence,
harm or absence of a smaller benefit. The old 54.33% screen retains its original verdict.

Common-opponent 300/1000 stages were **not run**, as predeclared after a direct failure. Their
43000/44000 pools and reserved confirmation-v4 remain unused. The historical unmatched 62% vs
v05 is not substituted for a comparison with the current bot.

A as P0/P1: **269/500 and 256/500 wins**; draws are included only in the aggregate 53% score.
Average score difference **+67.8**, average game length **91.6 turns**. Candidate/control search
averages **18.20/18.17 ms**, maxima **85/97 ms**, nominal-budget slow moves **3/5**. All timing
outliers are retained; in-process clocks do not establish external-process timing readiness.

Decision: **retain the default-off candidate, no promotion; selected v06 unchanged**. This
evaluation did not change strategy code, Params, core, frozen versions or Player.java. All
**39 original class hashes** and the complete effective A/B parameter sets match the old screen.
The 500 new maps exclude all **4324** known prior benchmark identities. Final Player SHA-256
remains `5807068e5d50c7bb3acd395866b74f8c9a972f7c8b19fc323dfda18cff413266`.
No new process checks are needed for this rejected strength candidate; its historical process
evidence remains preserved and the existing v06 timing investigation remains open.

Validation: **226 Java tests**, **six Python checker tests**, installed skill validation, and
**264/264 exact arena replays**. Full report:
`build/bench/quick-income-practical-v1/direct-1000.json`. Decision and preserved pre-run protocol:
`build/analysis/quick-income-practical-v1/`. Durable complete per-map/per-side results, parameters,
source/class hashes, prior-map exclusions and the decision:
[docs/evidence/quick-income-practical-v1.json](evidence/quick-income-practical-v1.json).
