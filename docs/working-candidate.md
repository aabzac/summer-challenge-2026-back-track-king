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
current?USE_ROLLOUT_QUICK_ROUTE=1,ROLLOUT_QUICK_MAX_PAINT=6,USE_ROLLOUT_QUICK_EARLY=0,USE_ROLLOUT_QUICK_VALUE=0,USE_ROLLOUT_QUICK_SAFE=0,USE_ROLLOUT_DIVERSE_REPLIES=0,USE_ROLLOUT_REPLAN=0,USE_ROLLOUT_SECOND_TARGET=0
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
