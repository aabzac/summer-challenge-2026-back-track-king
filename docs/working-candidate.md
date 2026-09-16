# Working development base: original quick-income candidate

Resumed at the user's request on 2026-09-17. Future strategy work starts from the original
quick-income candidate, rather than plain v06. This selects a research base, not a promotion.

- Candidate identity: `quick-income-v1`, v06 rollout policy with one cheap-income portfolio slot.
- When own income is zero, retain an existing route costing at most six paint that would give
  positive own and net income. Prefer lower cost, then larger gain. The normal rollout still
  decides whether to build it; the route is not forced.
- Control opponent and selected arena strategy: frozen `v06`.
- All later quick-income and rollout experiments are OFF in this base.

Explicit working spec (only add a new experimental change deliberately):

```text
current?USE_ROLLOUT_QUICK_ROUTE=1,ROLLOUT_QUICK_MAX_PAINT=6,USE_ROLLOUT_QUICK_EARLY=0,USE_ROLLOUT_QUICK_VALUE=0,USE_ROLLOUT_QUICK_SAFE=0,USE_ROLLOUT_DIVERSE_REPLIES=0,USE_ROLLOUT_REPLAN=0,USE_ROLLOUT_SECOND_TARGET=0,USE_ROLLOUT_SPREAD=0
```

This is the maintained working implementation with later flags disabled, not a byte-identical
historical executable. The exact archived candidate remains available at
`build/analysis/quick-income-v1/candidate/Player.java`, verified SHA-256:
`8fb0831f44a7f0ca98ada59371fc2dd7537d1743ac711f78c21a8d7e2023dbfd`.

Historical results against v06: 300 games **160-134-6 = 54.3333%**; independent practical-policy
1000 **525-465-10 = 53.00%**, below the required 55%. Draws count half. Resuming development
does not change those verdicts or establish a new result. See [the original practical-policy
report](quick-income-practical-v1.md) for the fixed protocol and evidence.

The selected v06 defaults and existing `build/submit/Player.java` remain unchanged. Record a
concrete new hypothesis and fresh pools before the next experiment; compare the resulting
candidate against v06 under the existing practical policy. No new games or promotion requested
as part of this return to the development base.

## Separate arena-trial export (2026-09-17)

At the user's subsequent request, the exact archived candidate is available beside the selected
file as `build/submit/PlayerQuickIncome.java` (57,404 characters; SHA-256 as above). Its generated
entry class remains `Player`; paste the complete file into the arena editor. Metadata is in
`build/submit/PlayerQuickIncome.manifest.json`. This export deliberately uses the historical
submission itself, not a fresh merge of the later working implementation.

Java 17 recompilation passed. Fixed strict process checks vs v05 covered four maps, both sides,
eight games, with zero grace. **One timeout:** map 904146009, P1, turn index 25, 50-ms deadline.
No response was observed before timeout; the recorded 66.002 ms consumer time is not measured
bot response/search time. The other seven games had no process problems. No retry or timing fix
was made to this exact historical artifact; do not describe it as passing current process checks.

All 23 pre-existing files under `build/submit`, including Player.java, its manifest and classes,
retain their original hashes. Candidate compilation/checking used an isolated directory. No arena
upload or promotion performed. Full evidence: [quick-income-arena-v1.json](evidence/quick-income-arena-v1.json).

## Distributed-construction follow-up (2026-09-17)

The separate default-off `USE_ROLLOUT_SPREAD` experiment on this base completed 1600 games:
53.6667% / 300 then 50.60% / fresh 1000 against v06, below the required 55%; independent
300-game ablation against this base scored 51.50%. Retain SPREAD=0 in the working recipe.
All 25 existing submission files unchanged. [Protocol and results](spread-construction-v1.md).
