# v06 boss losses — 2026-09-16

The next experiment should improve **which construction routes reach the rollout search**.
Keep v06 as selected. This investigation changes diagnostic tools only, not the submitted bot.

## Sample and provenance

The user reports an arena upload and rank **153/645**, compared with rank **270** for v05
before uploading. This is a user-reported snapshot, not an API-verified deployment hash or a
controlled comparison of ranks. The locally packaged source remains
`7f4d94e40e5252d09b86df6e1b8c208e92c86d98a521d858eaf182a9178c2c9c`.

All 30 canonical files directly in `replays/cg/bot` were included, sorted by game id. The user's
first-15/last-15 assignment labels the cohorts v05/v06. The IDE replay does not independently
record the source version; agent -1 alone cannot verify that assignment.

- v05-labelled: 904021106 through 904125203, **2 wins / 13 losses**.
- v06-labelled: 904125321 through 904148315, **5 wins / 10 losses**.
- All games: aabzac P0, Boss 4 P1, boss agent **6740207**, identified by `agents.index` and
  `arenaboss`, not the array's finishing order. **30 distinct maps**, no map shared between cohorts.
- **30/30 replay validations exact; zero failures and zero skipped commands**, both players.
  These are descriptive, unpaired samples. They do not replace the prior 2x1000 strength test.

## What improved in these records

Cells we placed that never subsequently appeared on an income-producing connection:
v05 **970/2054 (47.22%)**, v06 **398/1549 (25.69%)**. Boss: **205/1432 (14.32%)** and
**230/1576 (14.59%)**, respectively. v06 spends less paint overall, **1891 over 1082 turns**,
compared with v05's **2600 over 1043 turns**. Different maps prevent attributing the entire
difference to the strategy, but the new games contain substantially less unproductive building.
This metric does not measure return on paint or prove that a never-earning investment was irrational.

## Main finding: quick income can be absent from the route portfolio

The frozen v06 was examined on **all 1082 recorded pre-turn states** of the new cohort.
Each probe uses a fresh instance, `v06?NOISE=0`, and a 60,000-ms allowance so the bounded search
can finish. This is a mechanism probe, not a reconstruction of the original timed search or RNG
history. All probes completed at least one full comparison; their root portfolios contain the
recorded placement set in **971/1082** positions. Actual same-turn boss actions are never policy inputs.

Example: **904148181, turn 0** (turn indices below are zero-based):

- Core finds a connection costing **4 paint**, with positive own and net income gain (+4/+4)
  if completed on the known board. That is an optimistic construction opportunity: future
  disruption or different opponent building could remove its value.
- All six routes retained by the frozen policy in the noise-free probe cost **at least 18 paint**;
  its selected route costs **24**. Its first placement command matches the recorded command.
- In the actual game, our first income arrives on **turn 16**, versus boss turn 1. Before that
  turn the score is **0–392**; afterward **32–454**. Final result: **1899–3387**.
- At recorded turn 5, a useful completed route costs **3 paint**, while the cheapest retained
  route costs **17**. The observation is not confined to the initial map.

Across first-20-turn observations with zero own income, a useful route costing at most 6 paint
is cheaper than every retained route in **48 states from all 10 losses**. The same condition
appears in **25 states from all 5 wins**. These repeated observations are neither independent
trials nor proof that this condition causes defeat. They establish a recurring restriction of
the candidate set, including in successful games.

In `RolloutBotV06.propose`, routes compete using hypothetical completed-network income before
the affordable eight-turn simulation. Only six retained routes reach that simulation. A short
modest connection can therefore lose its place to a large hypothetical network; deeper search
alone cannot recover a candidate that was already discarded. This is a search-design tradeoff,
not an illegal action or a proven winning replacement.

**Proposed single change:** reserve one of the existing six route slots for a cheap, positive-net
income route when our current income is zero. Let the unchanged rollout choose whether to use
it; do not force construction. Start with a bounded cost limit and parameters in `Params`,
default-off. Keep width/depth, sabotage model and other weights unchanged. A later experiment
must check clock cost, compare against frozen v06 on fresh paired development maps, and check
v05 or pressure-v1 separately before any confirmation/promotion.

## Sabotage prediction is not the first bottleneck to widen

The actual frozen first-turn opponent scenarios cover the boss's chosen target in
**779/917 attacks (84.95%)**. Among v06 losses, for the **23 turns** where omitting the boss's
recorded same-turn sabotage improves immediate net points by at least 30, the model includes
the real target in **21/23**. This threshold is an exploratory descriptive slice, not a held-out test.

Examples with correctly anticipated targets:

- **904147739 t22**, region 31: prior instability 3; known income 227/116, actual gained 88/169.
  Removing only the boss's recorded disruption improves this turn's net points by **113**.
  A probed alternative path still costs 4 paint, unchanged by our recorded placements.
- **904147809 t22**, region 9: an exposed active connection has a 3-paint bypass; our placements
  leave that cost unchanged. The same-turn no-enemy-disrupt diagnostic is **+83 net points**.

These are recorded-action counterfactuals: the opponent's same-turn command is known after the
fact, and each branch resets to the actual next state. The deltas are **not additive recoverable
match points**. A geometric bypass need not preserve our ownership income, and a route that
looks defensive is not automatically better than the chosen investment. Both examples suggest
examining construction alternatives and their valuation before adding more sabotage scenarios.

A simple repeat-the-previous-target baseline also does not beat the existing model on its
eligible slice: **576/678** correct for repetition, versus **605/678** covered by the two current
scenarios when the previous target remains legal and the boss attacks. Do not replace the model
with target stickiness based only on selected examples.

The exact boss placement set is covered by the two construction scenarios on **118/673 turns
with nonempty boss construction (17.53%)**. That is a later modeling question; multiple different
placement sets can have similar strategic effects. Low exact-action agreement alone does not
justify a wider or more expensive search.

## Verification and artifacts

Reproduction, with fresh output paths if reports already exist:

```powershell
.\gradlew.bat cgValidate "-Ppath=replays/cg/bot"
.\gradlew.bat cgBossLossStudy -Pbefore=15 -Ptotal=30 "-Pout=build/analysis/boss-v06-losses-v1/cohorts.json"
.\gradlew.bat cgRolloutBossProbe "-Pinput=build/analysis/boss-v06-losses-v1/cohorts.json" "-Pout=build/analysis/boss-v06-losses-v1/rollout-probe.json"
.\gradlew.bat test
```

The cohort command refuses changed file counts and existing output. Each replay's SHA-256 is
stored in the report; the probe checks those hashes and revalidates the input. Diagnostic route
completion, placements, scoring and pathing all use core. No rules are reimplemented.

- `cohorts.json`: `7ab85b8bdb849fbcac9985a0a526f557f477c4a8e35dd1194fd86e93722c0876`.
- `rollout-probe.json`: `c2569c38aa14f7a6e2c612f115af7c00cb27a4e8d00012140a84da26120661a4`.
- `summary.json`: `9e131306820080e168badba63916179501dc8a253f5193c4989b59694ec7a020`.

Compact positions are retained in `src/test/resources/btk/bot/rollout-boss-positions.json`:
904148181 t0 and 904147739 t20. Their initial graphics plus prior actions reconstruct the exact
pre-turn hash through core. Same-turn actions are clearly marked diagnostic labels only.
**220 tests pass.** Tests also check terrain costs, player perspective, net-income versus mere connectivity,
unreachable inked paths and input isolation.
The reused defense helper's JSON field `bossTracks` refers to the probed player's own tracks
in `priorTargetDefense`; this probe passes our player index to that helper, not the boss index.

No policy, frozen version, default parameter or Player.java changes; no new matches, upload,
strength benchmark or claim to fix the earlier local process timeout. The absence of failures
in these 30 boss games does not explain that separate local 50.462-ms overrun.
