# Two independent strategy experiments, registered before strength play

Policy: **practical-300-1000-boss-cache-v3**. Selected control is frozen **v07**.
The user authorized implementation and evaluation of a small evolutionary search
and a protected opening. These are two independent default-off candidates. Neither
candidate includes the other's flag, route dependencies, network chords, or a
combination of prior rejected experiments. Selection, upload and push are separate
decisions. Complete experiments, including failures, are recorded and committed.

## Hypotheses and implementation boundaries

**strategy-search-rhea-v1** uses `current?USE_ROLLOUT_RHEA=1`. The hypothesis is that
several construction-priority continuations for an identical first move can reveal
better projects than v07's fixed continuation. Keep the current root proposals,
eight-turn horizon, two opponent scenarios, greedy sabotage and final evaluator.
Seed a small elite population from completed v07 comparisons, mutate one later
route priority or a suffix, and compare complete scenarios through `btk.core`.
Ordinary and singleton network comparisons retain priority; incomplete evolutionary
comparisons cannot replace the saved move. The initial bounds are two elites and
twelve mutation attempts. There is no new route generator, crossover, parameter
tuning, or persistence of raw route indices between observations in this version.
This tests a bounded local evolutionary search under a rolling horizon; it does not
claim that arbitrary larger RHEA populations or deeper searches are better.

**strategy-search-opening-v1** uses `current?USE_PROTECTED_OPENING=1`. The hypothesis
is that claiming affordable cells next to towns, in regions which contain towns,
can preserve future scoring ownership before the opponent occupies them. The
opening lasts the first three turns. Reuse ordinary canonical route observations
to estimate traffic, spread claims with a one-cell-per-town quota, and discount
the predicted opponent's immediate placements by a factor of 0.25. All selection
and fallback rules are frozen before play; subsequent behavior remains the v07
policy. This changes opening action selection, not the core protection or scoring
rules. A separate recorded mechanism audit establishes which actions actually
change, their legality, and whether the intended protected cells are selected.

Every parameter, including inactive fields, is captured from `BotFactory` at
preflight. Candidate and v07 configurations must have exactly one changed flag.
Disabled current must have the same full configuration as v07. Final source,
compiled classes, copied resources, isolated candidate exports, strict process
reports and orchestration files are pinned. Any code or parameter change after
preflight invalidates continuation under these experiment identities.

## Fixed local pools and branch rules

Each map produces two games with fresh bots, A in each seat once. Use the authentic
CG SHA1PRNG generator, bot seed 1, noise 0.1, nominal CG search timing 486/36 ms, and
two workers. Two workers match the existing local protocols and keep local stages
serial; they are not a process deadline guarantee. Process checks run before heavy
bench work. A required local 1000 may overlap a sequential remote boss batch.
A shared nonblocking OS lock covers both local variants, including their
check/start boundary; simultaneous runner invocations cannot launch two local stages.

- RHEA screen: seeds **500000..500149**, 150 maps / **300 games**.
- RHEA direct continuation: seeds **501000..501499**, 500 maps / **1000 games**.
- Opening screen: seeds **510000..510149**, 150 maps / **300 games**.
- Opening direct continuation: seeds **511000..511499**, 500 maps / **1000 games**.

Existing pool declarations and recorded seed intervals are checked before launch.
All map hashes found in prior benchmark and diagnostic reports under `build/bench`,
`build/analysis` and `docs/evidence` are excluded prospectively. Each stage also
excludes new evidence since preflight and every completed/retained report from
either candidate. The benchmark generates and checks all maps before starting
games. A collision fails the stage; no substitute seeds, sampling until fresh,
or removal of inconvenient maps is allowed. Every stage is single-use, even after
an interruption. Preserve all outcomes, bot errors and timing outliers.

At **<=52%** in the 300 screen, stop that candidate with `NOT_PASSED`; do not start
direct continuation or boss games. At **>=60%**, the local stage passes without
1000. Only **>52% and <60%** requires a separate fixed 1000 at **>=55%**. Score draws
as half a win. Do not pool stages, tune after outcomes, retry a failed sample, or
use a boss result to rescue a local failure. Confidence intervals are diagnostic.

## Boss control, readiness and identity

Both candidates use the accepted v07 control from
`build/cg-boss/chords-v07-boss-40/plan.json`, arm `control`, plan ID
`3d951ec7e4ad1878e4e135d11b9f3a74b4611964074918c0d223cc06e90ee24b`.
The frozen cache is verified through `boss_cached.control_snapshot` before launch
and before/after each local stage. Its forty individual canonical replays, seed,
seat, outcome, boss/league, source/build, process evidence and amendments remain
pinned. Its observed score is 10/40; comparison uses the verified cached score,
not a hard-coded future threshold. These are intentionally reused regression
maps, not fresh confirmation. Reassess cache suitability before each series and
refresh on a changed boss/league or after outcome-guided development cycles.

Exactly **40 new candidate boss games** are required after a qualifying screen,
on those same map seeds and seats. Ties with the cached control pass. Boss calls
remain sequential with the existing pacing and no replacement of unknown or
failed requests. Finish any started boss and local continuation stages even if
the other stage fails. Preserve nonpromoting boss evidence after local failure.
The local runner never calls the game API and does not launch boss games.

To exclude the boss fixtures from fresh local confirmation, the required
`--boss-local-maps` file hashes `Protocol.initBlock(initialState, 0)` through the
shared Java map/replay tooling. Its schema is `hashScheme` =
`sha256-init-block-player-0`, `sourcePlanSha256`, and forty `maps` records containing
`mapHash`, `seed`, `gameId`, `rawSha256`. All records must match the pinned cache.
Do not compare local map hashes with CG graphics hashes as proof of disjointness.

New unused `Params` declarations enlarge local v07 configuration metadata without
changing the old frozen policy. A separate exact-source compatibility certificate
pins the newly generated isolated v07 source and cached source. It reconstructs the
cached source byte-for-byte by removing only the declared new fields, their
embedded parameter assignments, and the build marker difference. It must also
prove those field names are not referenced elsewhere in the frozen export.
The final combined assessment may project only these certified unused fields out
of the local control's comparison metadata. All original full local parameters and
reports remain retained, and candidate parameters are never projected. This does
not claim the newly compiled v07 export is the cached executable or alter the strict
global v3 checker. Cached control fields must match exactly.

Before strength games, both isolated candidate sources must pass the source-size,
merged-source/protocol checks and a strict exact-artifact process check. Preserve
the selected `build/submit` tree byte-for-byte. Engine fidelity and disabled-current
parity are prerequisites; changing `btk.core` additionally requires the mandated
core tests and `cgValidate`. These process samples establish local readiness only.
Preflight also calls the read-only `verifySubmission` task for both candidate exports
and the new isolated v07 export. It reconstructs each source with the current local
merger, checks exact source bytes and structural manifest equality, and pins the
verification logs. Source/classes/tooling and selected submission hashes must stay
unchanged throughout these checks and every subsequent stage. Equal flags alone
are insufficient to associate a previous artifact with the current local policy.

## Offline orchestration

After implementation, mechanism checks, final compilation and isolated artifacts,
freeze both experiments once:

```powershell
python tools/run_strategy_search_experiments.py --preflight --rhea-artifact build/cg-boss/artifacts/strategy-search-rhea-v1 --opening-artifact build/cg-boss/artifacts/strategy-search-opening-v1 --boss-local-maps build/analysis/strategy-search-v1/boss-local-maps.json --control-equivalence build/analysis/strategy-search-v1/control-equivalence.json
python tools/run_strategy_search_experiments.py --variant rhea --stage screen
python tools/run_strategy_search_experiments.py --variant opening --stage screen
```

Launch a direct continuation only when its own screen requires it:

```powershell
python tools/run_strategy_search_experiments.py --variant rhea --stage direct
python tools/run_strategy_search_experiments.py --variant opening --stage direct
```

Every stage writes its immutable full report, compact durable evidence, standalone
local v3 decision, start/completion or failure records, and task-scoped power-request
record. A qualifying local decision remains `NEEDS_BOSS_CACHED_40` until boss evidence
is supplied. A screen failure can be finalized with no boss plan:

```powershell
python tools/run_strategy_search_experiments.py --variant rhea --finalize
python tools/run_strategy_search_experiments.py --variant opening --finalize --boss-plan build/cg-boss/strategy-search-opening-v1/plan.json
```

Use the actual variant and prospective boss plan path. If a boss stage was started,
supply that plan even after a local failure; an incomplete started stage prevents
finalization. A final local/boss `PASS` remains a strength result, not automatic
promotion, upload, push, or a general guarantee about arena rank.
