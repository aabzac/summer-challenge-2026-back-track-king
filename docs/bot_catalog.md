# Bot names

One machine-readable catalog: `src/main/resources/btk/bot/catalog.json`. `BotCatalog` supplies
labels to the CLI, benchmark reports, suite index and new local replay metadata. Commands still
accept the original ids; the labels do not rename frozen Java classes or change strategy behavior.

```powershell
.\gradlew.bat botCatalog
.\gradlew.bat botCatalog "-Pbot=v12"
.\gradlew.bat run "-Pbot0=v05" "-Pbot1=neural-v6" "-Pseed=40100"
.\gradlew.bat bench "-Pbot1=current" "-Pbot2=v12" "-Ppool=bench/pools/development-v1.json"
```

## Three roles, not one ranking

- **Versions**: v00 Autoplace, v01 Greedy Routes, v02 Route Evaluation, v03 Risk Aware,
  v04 Network Extension, v05 Disrupt Reserve, v06 Simultaneous Rollout,
  v07 Network After Base, v08 Protected Opening, v09 Never Idle, v10 Boss Model, v11 Root Target, v12 Exposure Income,
  **v13 Bypass Routes (selected, 2026-10-27: the exact bypass-routes-v1 candidate B2)**.
  `selected` identifies the chosen policy, not proof
  of the exact arena upload or process-timing readiness. Historical versions remain reference
  opponents; their numbers are a development history, not an ordering against every possible bot.
- **Local references**: pressure-v1 Sabotage First is an independent strategy intended to broaden
  behavioral coverage (it is in the default `benchSuite` panel), not an arena promotion; boss-v1 is the
  frozen boss imitation of the boss-proxy line. leader-v1/v2 were removed in repo-cleanup tier 3 (2026-10-27).
- **Candidates**: neural-v6 Neural Resumable Feature Paths is the one learned bot kept, as the second
  screening opponent (`submit -PsubmitVersion=neural-v6` ships its `btk.bot.neural` classes; its weights are a
  frozen generated file). The other neural, spatial-ppo and indrill candidates were removed in repo-cleanup
  tier 3; their verdicts stay in [the BOTS archive](archive/BOTS-2026-09-13_10-06.md) and DEVLOG.
- **Experiments**: until 2026-10-27 six `exp-*` recipes of the v05 era (income guard, build direction, early
  disrupt, reply rerank, interruptible evaluation, delay 0.65; all `not_promoted`) ran on an archived EvalBot
  engine (`RetainedEngine`, `experiment-engine-v1*.json`). That engine stopped matching the shared core on
  2026-10-18 (fast cheapest path changed `Pathing.class`), so the recipes could no longer run and their 19
  tests stayed red. They were removed in repo-cleanup; their screens and verdicts remain in
  [the BOTS archive](archive/BOTS-2026-09-13_10-06.md) and the engine at source revision `218457a`.
  Current experiments are plain `current?KEY=value` specs, frozen in a `vNN` copy only at a promotion.

`current - Working Copy` and `main - Submission Wrapper` are explicitly **mutable** working
entries. In particular, `main` is not a label for whichever Player.java happens to exist on disk.

## Reports, replay compatibility and panel selection

Benchmark schema 3 keeps `a`/`b` and `paramsA`/`paramsB` unchanged and adds `botA`/`botB` identity
objects. Suite indices retain original opponent ids and add identities. Historical JSON files are
not rewritten. Display names are metadata, never the matching key for statistical comparisons.

New LocalRunner replays retain exact input specs in `meta.bots` and record optional `meta.botDetails`
with labels and categories (replays recorded before 2026-10-27 may also carry an `exp-*` engine identity). The viewer prefers those recorded labels. For
older **local** replays it can read the same catalog over HTTP and label recognized ids; if the
catalog cannot load, it keeps original ids. Unknown names and all CG player nicknames are unchanged.
Presentation uses plain text, not HTML supplied by replay metadata.

The default opponent panel is unchanged. Keep a small varied panel, then select diagnostic opponents
for the hypothesis under test; arena promotion still requires the existing
version/confirmation/process-check workflow.

Checks: `gradlew test`, `node visualizer/bot-labels.test.js`; manual browser check with a local
version-vs-experiment replay. Catalog work does not fix the previously recorded process timeout.
