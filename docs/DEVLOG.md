# Dev log

Format per entry: context / hypothesis -> what was done -> result -> decision.

## 2026-09-13 (contest day 6 of 14) — infrastructure day

- Stack decided: Java 17 + Gradle (familiarity over C++ speed; top-10 is 9x C++, 1x Python, so the
  game is decided by evaluation quality rather than raw speed). Skeleton, FileMerger idea and CG
  capture tooling ported from the Spring 2026 (Troll Farm) project.
- Leaderboard snapshot: 1233 players, Silver open (divisionIndex 3 = 595 players), Gold not yet.
- Wood 2 statement captured to `docs/rules/wood2.md`. Only Wood rules visible until promotion.
- `btk.core` written from the Wood statement + Java starter protocol. 15 JUnit tests
  (cost/budget, neutral clash, N/E/S/W tie-break incl. statement example 2, AUTOPLACE expansion,
  protocol round trip).
- Discovery: CG `gameResult/findByGameId` is public and returns **both players' stdout per turn**
  plus viewer events. `CgFetch` downloaded 107 Silver games of the top 15; `CgValidate` replays them
  through our engine.
  - Result: placements (cost, budget, neutral) match CG in 107/107 games up to the first ink
    effects (turns 4-11). Scoring diverges as soon as regions get inked -> DISRUPT/ink rules must
    be modelled; they are only readable from Bronze.
  - Observed: DISRUPT costs no paint; a region gets inked after N disrupts (N=2 and N=4 seen —
    depends on something: region size? tracks inside? both players?); inked regions lose tracks and
    connections break; games last 40 or 55 turns (variable — end condition unknown).
  - Top players issue exactly one DISRUPT every turn from turn 0.
- `Player.java` (v01) verified via `ProcessCheck`: first turn ~690 ms (600 ms JIT warm-up), no
  timeouts, 4/4 wins vs v00 on real CG maps.
- Visualizer works for local replays; a CG-derived local replay is written by
  `cgValidate -Pshow=<id>` for side-by-side inspection.
- Blocker for engine fidelity: Bronze statement. Manual paste of Player.java needed (Cursor browser
  cannot reach localhost, so automated paste channel is not available).

### Rules reverse-engineered from replays (later the same day)

- `CgInkStudy` over 2750 ink events: ink at instability 4 (both players' disrupts add up),
  `DISRUPT id` and `DISRUPT x y` both valid, disrupt costs no paint, town regions never targeted,
  inked regions permanent, all tracks removed. Game ends at 100 turns or when no pair can be
  connected through non-inked cells (verified on every early-ending game).
- Implemented in `btk.core` (+5 JUnit tests). **`cgValidate`: 107/107 replays reproduced exactly**
  (placements, ink, per-turn scores, paths, end turn, final score). Written up in
  `docs/rules/inferred_silver.md`.
- Connection recomputation now does one BFS per destination town (`Pathing.updateAllConnections`);
  the eval bot calls it a few hundred times per turn.

### v02 EvalBot

- Self-play with deterministic bots produced 195/200 draws: both sides choose the same best path
  and every cell becomes neutral. Added `NOISE`/`SEED` params (player id mixed into the seed) for
  benches; noted "opponent placement prediction" as a real strategic feature for v03.
- PowerShell/cmd eat `&` in `-Pbot2=current?A=1&B=2`; `Params` now also accepts `,` as separator.
- Ablation results in `docs/BOTS.md`. Frozen as `btk.bot.v02`. `procCheck`: first turn 720-810 ms
  (warm-up lowered to 500 ms), later turns <= 12 ms, avg ~2 ms.

### v03 (same night)

- Opponent model (predict their route, surcharge contested cells, place them last): no effect
  (47-51%). Zero-sum logic: a clash neutralises their track too, so avoiding it is not free.
- `CgPolicyStudy` (`gradlew cgStudy`): what winners do in the 107 Silver replays — see BOTS.md.
  Main gaps found: idle turns, early closing of the map, disrupt frequency.
- Vulnerability eval (income after the opponent's best single ink) + backup routes: 60% vs the
  same bot without backups, 58.5% vs v02. Closing rule and HORIZON made no measurable difference in
  self-play because our games do not close.
- Deterministic Player.java vs deterministic v02 produced 0-0 games (perfect mirrors, every cell
  neutral). `NOISE=0.1` is now the default. Timer margin raised to 14 ms after a 44 ms turn in
  `procCheck`; timer checked per candidate variant.
- Stopped tuning here: first get out of Wood with v03, then get the Bronze statement.

### Bronze reached (01:36)

- v03 went Wood 2 → Wood 1 → Bronze on the first submission. Bronze statement captured to
  `docs/rules/bronze.md`; it links the **referee source** (github.com/CGjupoulton/SummerChallenge2026).
- Every inferred rule confirmed by the source. Bronze is the last league with rule changes.
  POIs / buying are dead code. One real difference found: CG skips impossible actions one by one
  (only unparsable commands disqualify); our engine failed the whole line. `Rules.expand` now
  mirrors `Game.doActions` (`PlayerMoves.skipped`); tests updated; `cgValidate` still 107/107.
- Next infrastructure win: port `GridMaker` for authentic maps (and seed-exact CG map replay).

### Authentic GridMaker port

- Hypothesis: porting the referee's generation steps and preserving RNG/collection order will
  make new local maps representative of CG and reproduce downloaded maps directly from seed.
- Replaced the approximate `MapGenerator` with the map-only port of referee commit
  `23b2a02781d02751b723432865889fbc5dd75e35`. Kept hash/iteration order, floats, shuffles,
  retries, river splitting and connection filtering; removed unused debug snapshots and engine
  dependencies. Local runners automatically use the port. No strategy or `btk.core` changes.
- First validation failed **0/107**: `new Random(seed)` was the wrong RNG. Checking whether
  seed strings had lost precision via a double conversion also gave **0/107**; rejected that
  hypothesis (the public API returns the same seed strings). The game engine's
  `MultiplayerGameManager` supplies **SHA1PRNG SecureRandom**, despite GridMaker's `Random` type.
  Using that exact initialization gives **107/107 maps identical**, including region IDs and
  desired-connection order. CG seed 0 leaves the PRNG unseeded and is explicitly rejected.
- Added `CgReplay.mapSeed()` (Java properties, signed long parsing), `gradlew cgMapValidate`
  (fails on mismatches or missing input), and seven independent checked-in CG map fixtures
  covering all possible heights. Regression coverage includes parallel generation and fresh
  state as well as signed seed boundaries. `gradlew test`: **35 tests passed**;
  `gradlew cgValidate`: **107/107 full games reproduced exactly**.
- Integration: `gradlew run -Pbot0=current -Pbot1=v03 -Pseed=-176203007668178900` produced the
  expected 23x15 map, 6 towns and 12 pairs (CG game 903182120); match completed, 0-0 in 100 turns.
  Parallel smoke bench: `gradlew bench -Pbot1=v03 -Pbot2=v03 -Pgames=20 -Pthreads=2`, generated
  seeds 1000..1019, W-L-D **5-9-6**, A **40.0% [21.9-61.3]**, avg 90.5 turns, no action errors,
  7 seconds. Timing max 51 ms, with the existing local/JIT timing outliers; this is an
  infrastructure smoke check, not evidence for promoting a bot version.
- Decision: use the authentic generator by default, retain replay pools, close the GridMaker
  PLAN item. No new bot version: strategy is unchanged. See `docs/map_generator.md` for
  provenance, reproducibility details and commands.

### Paired benchmarks and fixed reference parameters

- Hypothesis: swapping sides on the same map and fixing reference parameters will make local
  comparisons more reliable. Previously side swaps changed the map too, and changing `Params`
  defaults silently changed historical opponents.
- Each benchmark pair now plays the same initial map twice with fresh bots and swapped sides.
  Generated seeds advance per pair; replay pools wrap per pair. Reports save full resolved
  parameters, map hashes, both game results, seeds, timing and the statistical gate as JSON.
  The primary 95% interval groups repeated maps and uses a conservative Hoeffding bound;
  the old per-game Wilson interval remains diagnostic only.
- Added explicit v01/v02/v03 parameter snapshots of the effective defaults at `8364ad5`.
  These preserve the current reference checkpoint, not every original historical benchmark's
  configuration. Overrides apply after snapshots; frozen strategy packages are unchanged.
  Empty/malformed explicit map pools and failed/empty `cgValidate` runs now fail the command.
- Validation: `gradlew test cgValidate cgMapValidate` passed: **46 tests**, **107/107 full games**,
  **107/107 generated maps**. Regression tests cover actual paired inputs and bot lifetimes,
  repeated replay maps, report contents, snapshot independence and validation failures.
- Sanity bench: `gradlew bench -Pbot1=current -Pbot2=v03 -Pgames=200 -Pthreads=2 -Pseed=1000
  "-Pout=build/bench/paired-current-v03.json"`: 100 distinct generated maps, seeds 1000..1099,
  W-L-D **86-82-32**, A **51.0%**, map-grouped 95% CI **[37.4%, 64.6%]**, mean score diff
  **+102.0**, 86.9 turns, no bot errors, 64.8 seconds. Current and v03 have the same strategy;
  this is consistent with parity. Timed decisions can still vary with JIT/GC and scheduling.
- Older-opponent smoke check: `gradlew bench -Pbot1=current -Pbot2=v02 -Pgames=40 -Pthreads=2
  -Pmaps=replays/cg "-Pout=build/bench/paired-current-v02-replays.json"`: 20 distinct replay maps,
  W-L-D **22-14-4**, A **60.0%**, map-grouped 95% CI **[29.6%, 90.4%]**, mean score diff
  **+228.4**, 88.2 turns, no bot errors, 10.2 seconds. This sample is too small for promotion.
  Maximum local move times were 51/52 ms in the first run and 53/20 ms in the second;
  these runs do not establish submission timing safety.
- Decision: adopt paired comparisons and parameter snapshots; no strategy change or new bot
  version. Neither run passes the statistical promotion gate. Procedure and assumptions are
  documented in `docs/benchmarking.md`, and the infrastructure PLAN item is complete.
- Workspace cleanup after an accidental IDE staging operation: removed `.idea`, `.gradle`,
  build/replay/temp artifacts and the module file from the Git index. Existing ignore rules
  cover them; local files were preserved.

### CG replay player mapping fix

- Hypothesis: `lastBattles.players[].position` identifies in-game P0/P1. Arena game 903337834
  disproved it: the battle record listed winner `aabzac` at position 0, while the authoritative
  game result had `MiniWall` at agent index 0 and reported `$0 has not provided 1 lines in time`.
- Corpus check: 61/108 downloaded replays had `btkNicknames` reversed, exactly the 61 games whose
  P1 won. Therefore battle `position` is finishing order, not game position.
- Fix: `CgFetch` now keys names by `gameResult.agents[].index` (with a `userId` fallback), and
  `CgReplay` treats the old `btkNicknames` as fallback rather than overriding authoritative agent
  pseudonyms. Existing downloaded files are repaired at read time; no re-fetch is required.
- Added regression tests for a P1-win/winner-first battle record. `gradlew test`: **48 tests
  passed**. Targeted `cgValidate` now labels game 903337834 as `MiniWall vs aabzac`; its expected
  remaining divergence is the separately identified unsupported timeout semantics. No rules or
  strategy changed.

### CG replay timeout handling

- Hypothesis: CG retains a timed-out player's late stdout in the game result but does not apply
  it. Game 903337834 confirms this: `$0` (`MiniWall`) timed out, its three late placements are
  present in stdout but absent from viewer events, while P1's actions and scoring completed.
- `CgReplay` now parses timeout summaries per turn/player. `CgValidate` preserves the late text
  for diagnostics, resolves the failed player as `WAIT`, still checks the opponent's terminal
  actions and scoring, then applies the server-side `-1` result and records the failure in the
  generated visualizer replay. No `btk.core` rule was added because process timeouts are outside
  game semantics.
- Validation: `gradlew test` passed **49 tests**; targeted game 903337834 now reproduces exactly
  as `MiniWall vs aabzac`, 7 turns, final **-1–57**; the full corpus is **108/108 exact**. The
  generated final visualizer turn shows MiniWall's effective action as `WAIT`, the timeout error,
  a `-1–57` score, and a P1 win.

### Boss replay capture and AUTOPLACE fidelity

- Captured IDE boss game 903365560 by intercepting `TestSession/play`. Its response is already the
  same top-level frame document as `gameResult/findByGameId` (115 frames, both players' stdout,
  seed and final 5857–601 score), only without `agents`. The game id is immediately readable from
  the public game-result endpoint, which adds the IDE pseudo and a boss agent with id `-2`.
- Replaced the old stderr-reconstruction plan with a dedicated userscript: extract `gameId`, fetch
  the canonical document and save it under `replays/cg/bot/`. The raw response is retained under
  `bot/raw/` only when the canonical lookup fails.
- The first boss validation exposed an equal-cost AUTOPLACE tie missed by the arena corpus. The
  referee's `AutobuildAStar` compares cost and then the incoming N/E/S/W direction, and does not
  replace a state on an equal-cost shorter route. Local `Pathing` incorrectly preferred fewer
  cells, producing a different route. Aligned the primitive heap with the referee and added a
  regression test using the captured boss map and command.
- Split boss captures from arena data: canonical results now use `replays/cg/bot/`, while the raw
  `TestSession/play` response is written to `bot/raw/` only if the canonical lookup fails. Existing
  captures were sorted into the same layout. The save server and installed userscript must both be
  restarted/reloaded after this protocol change (`bot` / `bot_raw` save kinds).
- Validation after the split: the arena corpus remains **108/108 exact** and all three boss maps
  reproduce from their seeds. Game 903365560 is exact; games 903416847 and 903417464 expose further
  boss `AUTOPLACE` tie-breaking differences (**1/3 exact**) to address separately.
- PowerShell's legacy Windows console encoding could not print the Unicode arrow in the save
  server's success log. The file write completed, but logging then raised `UnicodeEncodeError`, the
  server returned HTTP 500, and the userscript invoked its browser-download fallback. Replaced the
  arrow with ASCII, sent the HTTP success before diagnostic logging, and made userscript 1.2 show
  distinct messages for local saves and browser fallbacks.
- A follow-up showed three Python servers simultaneously listening on port 7777. Python's
  `HTTPServer` enables address reuse, which on Windows allowed new and stale copies to share the
  port; requests then reached arbitrary versions. The save server now uses a threaded,
  exclusive-address server and exits with a clear message if another instance already owns 7777.
- Boss game 903428605 exposed the remaining AUTOPLACE discrepancy at turn 2. Referee
  `AutobuildAStar` flood-fills an existing connected rail block and emits its non-rail boundary in
  BFS order as successors of one state; local Dijkstra walked the zero-cost rails individually,
  changing equal-cost queue order. `Pathing.cheapestPath` now mirrors the block expansion with
  primitive scratch buffers and preserves the full returned connector path. Added a captured-map
  regression test. Validation: **52 tests**, arena **108/108 exact**, boss **9/9 exact**, and both
  map corpora reproduce from their seeds.

### Separate CG validation from visualizer rendering

- `cgValidate` previously combined corpus validation with an optional `-Pshow` export into the
  ambiguously named `replays/cg-local/`. It now performs validation only.
- Added `cgRender -Ppath=<file-or-directory>`: it requires exact engine reproduction, then writes
  the normalized visualizer replay under `replays/rendered/cg/arena/` or `.../bot/`. Classification
  uses canonical agent id `-2`, not the input path.
- Removed unused legacy save-server kinds `replay` and `replay_raw`; arena downloads already come
  directly from `cgFetch`. Only `bot_raw` remains as the TestSession canonical-lookup fallback.
- Validation: JUnit passed; `cgValidate` remains **108/108 arena** and **9/9 boss**. `cgRender`
  produced and schema-checked one replay of each category, and the four existing `cg-local`
  artifacts were regenerated under the new layout.

### Sabotage state in the replay visualizer

- Replay turns now preserve the engine's per-region instability and permanent ink state. The
  visualizer progressively thickens a region outline at instability 1–3 and fills the region
  after it is inked, matching the main visual language of the CG viewer. Hover text also reports
  the region's sabotage state.
- Existing visualizer JSON stays compatible but has no historical sabotage snapshots; rerun
  `cgRender` (or generate a new local game) to see the effect. Validation: JUnit passed and boss
  replay 903443347 rendered successfully; visual checks covered the first outline and final fill.

### Arena status correction

- Corrected the league naming used for the top-player corpus: division index 3 is Silver, not
  Gold. The 107-replay measurements and validation results are unchanged; only their label and
  the historical inference document name were corrected.
- v03 is currently in Silver at roughly rank 350/645. It usually wins the available boss games;
  Gold has not opened yet, so a later automatic placement is possible but not assumed.

### Strategy experiment: post-ink route prebuild (rejected)

- Hypothesis: when no route increases income immediately, spend otherwise idle paint on an
  alternate route that restores income after the most vulnerable active-path region is inked.
- The targeted test confirmed the intended behavior. On the 108-replay policy study, placement
  frequency moved only **73% → 74%** and placed cells **1.76 → 1.79 per turn**. Paired benchmark versus
  frozen v03 on 100 generated maps (200 games, seed 2000): **51.5%**, W-L-D **88-82-30**,
  map-grouped 95% CI **[37.9%, 65.1%]**, average score difference +227.1.
- Decision: promotion gate not passed and the measured idle-turn gap barely changed; remove the
  implementation and move to pre-emptive disruption rather than tuning this weak signal.

### Strategy experiment: predicted early disruption (rejected)

- Hypothesis: top Silver players first disrupt on turn 0–1 in 92/108 games, while the bot cannot
  rank empty regions. Predict the opponent's best route and use its regions as early targets.
- Predicting every turn exceeded the useful 50-ms search budget: policy-study placement fell to
  68%. Restricting prediction to turns 0–1 restored placement to 73%, raised disrupt agreement
  **2433 → 2576** states and total disrupts **5576 → 5900** on the 108-replay study.
- Paired benchmark versus frozen v03 on the same 100 generated maps (200 games, seed 2000):
  **50.0%**, W-L-D **85-85-30**, map-grouped 95% CI **[36.4%, 63.6%]**, average score difference
  +97.7. Decision: behavior became more leader-like but strength did not improve; reject.

### v04: safe idle network extension

- Hypothesis: current places on 73% of replay states versus 88% for top Silver players. When no
  complete route raises the evaluation, use leftover paint to grow from existing own rails or a
  town, prefer uninkable town regions, and avoid non-town regions already at instability 2+.
- Policy study: placement frequency **73% → 100%**, placed cells **1.76 → 2.44 per turn**, adjacency to own rails
  **36% → 53%** (top players: 51%). Income-raising rate among placement turns fell 23% → 17%, so strength was
  decided by the paired benchmark rather than behavioral similarity.
- Versus frozen v03 on 150 generated maps (300 paired games, seed 2000): **64.7%**, W-L-D
  **194-106-0**, map-grouped 95% CI **[53.6%, 75.8%]**, average score difference +405.3. The
  promotion gate passed. A 100-game v02 spot-check gave 54%, exactly matching a v03-v02 control on
  the same 50 maps. Equal point estimates on this small sample do not establish equivalence or
  prove absence of a non-transitive regression.
- `submit` compile check passed. Eight process games against v03 had no errors/timeouts; first turn
  max 745 ms and later-turn max 43 ms. Frozen as v04 and retained in `current`.
- Manual CG check: v04 beat the currently available boss **7/7**. This confirms stability but is
  not treated as a strength benchmark because Gold is still closed and the boss may be weak;
  promotion remains provisional until games against real Silver arena opponents are observed.

### CG search-budget alignment and first v04 arena loss study (2026-09-14)

- Hypothesis: the previous local 1000/50-ms search allowance overestimates the submission's
  effective budget, and comparisons against related strategies miss arena-specific failures.
- Added `TimingProfile.CG` derived from `CGRunner` constants: nominal first/later search
  **486/36 ms**, default for bench/run/cgStudy. Explicit `-Ptiming=legacy` retains **1000/50 ms**
  for controls. Bench schema v2 records both budgets and profile. The local opponent in
  `procCheck` is capped, but external process IO deadlines stay **1000/50 ms** plus existing
  30-ms local grace. Actual CG preparation time, CPU and JIT history remain different; these
  are search allowances, not a claim of exact process emulation.
- Control experiment (not a new strategy): frozen v04 vs v03, new generated seeds **4000..4149**,
  **300 games / 150 side-swapped pairs**, 2 threads, profiles run sequentially:
  - CG: **63.7%**, W-L-D **191-109-0**, map-grouped 95% CI **[52.6%, 74.8%]**,
    mean score diff **+249.1**, 80.9 turns, 216.1 s; no bot errors.
    Report: `build/bench/v04-v03-cg-4000.json`.
  - Legacy: **63.7%**, W-L-D **191-109-0**, same CI, mean score diff **+334.2**,
    79.7 turns, 196.6 s; no bot errors. Report: `build/bench/v04-v03-legacy-4000.json`.
  - All 150 map hashes match. Winners differ in **36/300** individual games despite equal
    aggregate rates. This does not prove timing has no effect; it shows the previous-version
    advantage survives alignment. Both bots share the same phase scheduling weakness.
- Fetched **50** latest completed games for `aabzac`, current agent **6735801**. API snapshot:
  rank **477**, score **11.78**; placement completion and source hash are not available here.
  All new games and maps reproduce exactly: **158/158** for both full corpora.
- Added `cgAnalyze -Pagent=<id>` with validation as a hard precondition, correct P0/P1 mapping,
  accepted actions/paint, per-turn traces and **same-turn-only** counterfactuals. Server failures
  ignore late stdout and disable counterfactual advice. Tests cover AUTOPLACE/paint, mutation
  isolation, both player perspectives, simultaneous disruption, timeouts and invalid input.
- Observations: **23 wins / 27 losses**, no server timeouts. Heights 14-17: **18/20 wins**;
  heights 19-20: **1/20**. In losses our disruption rate is **40.1%**, opponents' **82.4%**;
  **20/27** losses last 100 turns. Opponents differ across size strata: these are exploratory
  signals, not a causal map-size estimate or arena win-rate confidence interval.
- Local probes on actual large-map states reproduce route search exhausting the timer before
  sabotage. At t40 in game **903509641**, 36/50 ms omit sabotage, while diagnostic 200 ms finds
  DISRUPT 46. The behavior persists with idle extension disabled; similarly reproduced in
  **903508669**. Captured stdout lacks per-phase timings, so not every missing arena command
  can be attributed conclusively to the deadline. Local probe report/source are retained under
  `build/analysis/timing-probes.json` and `tmp/ProbeReplayTiming.java`.
- A separate partial-build issue appears in **903507615**: at t46, placing (12,2) changes income
  from the no-build alternative **433-300** to **410-315**, worsening current score difference
  by **38**. Both players' disruption commands are held fixed. The cell is also chosen with
  idle extension disabled, so it is not evidence specifically against v04's fallback.
- Rendered games 903509641, 903508669 and 903507615 under `replays/rendered/cg/arena/`. Visually
  checked the ChaosCat t40 and DiogoGiassi t46 episodes (viewer sliders **41** and **47**, since
  position 0 is the initial state); their actions and income match the diagnostic trace.
- Final checks: **63 JUnit tests passed**, `submit` compile check passed; `procCheck` **0/4 problem
  games**, first response max **765 ms**, later max **43 ms**. Some turns exceed the internal
  36-ms target but stay below the external 50-ms limit. `Player.java` SHA-256 is unchanged:
  `af6978a5f34d3049168e5c93ae3e0f29abc94b24b7b467de8f0b0788bd4e933c`.
- Decision: keep v04 and its parameters unchanged, no new version/submission. Next experiment:
  guarantee time for sabotage before spending the full allowance on route search; then tackle
  partial-build safety separately and broaden reference opponents. Detailed findings and caveats
  are in `arena-analysis-2026-09-14.md`. Also corrected old policy-study descriptions: cells are
  not paint, and the income-raising percentage uses placement turns as its denominator.
