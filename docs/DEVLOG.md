# Dev log

Format per entry: context / hypothesis -> what was done -> result -> decision.

## 2026-09-16 — v06 promotion: packaging protocol (declared before final process games)

- User explicitly requested promotion/packaging after the completed strength and timing checks.
  Base revision: e99a947. Freeze the confirmed independent RolloutBot as v06; select it in current,
  MainBot and the catalog. No strategy tuning or core changes. Existing 2x1000 confirmation and
  older-opponent screens supply strength evidence; packaging does not create a new strength claim.
- Verify the frozen source differs only by package/class/constructor/name, and that all resolved
  parameters match the archived confirmation record. Preserve every older frozen source/snapshot.
  Test default/frozen submission decisions, isolated warmup and historical experiment selection.
- Final fixed sample: the ordinary `submit` artifact, 42 serial games against v04 on the same
  21 real maps in `build/analysis/rollout-process-v1/maps`, each side once. Strict 1000/50-ms
  process deadlines, zero grace, IO diagnostics off, one attempt, no filtering or retries.
  Record `build/analysis/v06-promotion/final-process.json`, retaining all failures if any.
  Check the exact generated source/classes and archive the manifest before play. This is an
  artifact check, not independent population-strength evidence or a CG-host timing guarantee.
- Local commit is authorized; arena upload and push are outside this request. Results follow
  in the completion entry at the end of this log.

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

### Strategy experiment: reserve time for sabotage (not promoted, 2026-09-14)

- Starting checkpoint committed as **824f00d** (CG timing alignment and arena loss analysis).
- Hypothesis: stop route/idle search 6 ms before the existing deadline so the free sabotage
  phase can run on large maps. Added `Params.DISRUPT_RESERVE_MS`, an earlier phase-deadline
  check in `Timer`, and build-loop checks. No scoring/pathing formula, frozen source, total
  allowance or CGRunner margin changed. `0` restores the old scheduling; disabling sabotage
  also disables the reserve. Atomic evaluations can still overrun a cutoff: this is not a hard
  real-time guarantee. The parameter is **0 by default after the failed promotion screen**.
- First ran `test cgValidate`: all tests green, **158/158** arena replays exact.
- Single-thread, warmed probes on 11 actual states from three lost games, each repeated three
  times at the 36-ms allowance (`NOISE=0`, no hypothetical continuation): v04 **26/33** sabotage
  commands, current reserve=0 **27/33**, reserve=6 **33/33**. In both ChaosCat t40 states
  (903509641 / 903508669), v04 and reserve=0 omit sabotage in **6/6** probes; reserve=6 returns
  **DISRUPT 46 / DISRUPT 15** in **6/6**, taking 30-32 ms. Maximum over all reserve probes:
  **34 ms**. Probe order/JIT/host load remain timing confounders; this is a mechanism check,
  not an estimate of arena strength. Source/report: `tmp/ProbeReserveTiming.java` and
  `build/analysis/reserve6-probes.json` (local/gitignored).
- Checked in compact authentic map-seed/stdout prefixes for both failures, plus deterministic
  tests: only the build deadline expires, sabotage retains the original clock; reserve=0
  matches v04 on a fully searched state; disabled sabotage does not starve building; closing
  while behind is still rejected. Tests use ample overall time rather than brittle 36-ms
  assertions. Regression fixtures live in `src/test/resources/btk/bot/`.
- Screen: reserve=6 vs frozen v04, **300 games / 150 distinct downloaded CG maps**, sides
  swapped, fresh bots, 2 workers, CG **486/36 ms**, first 150 sorted maps of the 158-map pool.
  **54.3%**, W-L-D **162-136-2**, map-grouped 95% CI **[43.2%, 65.4%]**, mean score diff
  **+636.5**, mean **74.1** turns; no bot errors. **Promotion gate not passed**.
  Report: `build/bench/reserve6-v04-cg.json`. Rerun explicitly with:
  `gradlew bench "-Pbot1=current?DISRUPT_RESERVE_MS=6" -Pbot2=v04 -Pgames=300
  -Pmaps=replays/cg -Pthreads=2 "-Pout=build/bench/reserve6-v04-cg-rerun.json"`.
  The original report calls A `current` because the experimental default was 6 during the run;
  its resolved parameters retain that value. Frozen v04 ignores the new field.
- Post-hoc size diagnostic: h14 20/40 wins, h15 19/38, h16 13 wins +2 draws /28 games,
  h17 19/36, h18 34/66, h19 33/50, h20 24/42. Height 19-20 combined **57/92 (62.0%)** is a
  small selected subset, not an independent gate. Small maps mostly finish search before the
  reserve matters. Do not infer that a larger-map gain generalizes to diverse arena opponents.
- Experimental `submit` compile check passed. Process check: **0/8** problem games (4 normal
  corpus maps plus both sides of each ChaosCat failure map), first response max **663 ms**,
  later max **37 ms**; no timeouts. Large-map process harness: `tmp/ProcessReserveCheck.java`
  (same process/referee protocol with an explicit replay-map path). These are local timings,
  not CG-host guarantees or a win-rate sample. No 1000-game confirmation or older-opponent
  promotion check after the failed screen.
- Decision: retain a **default-off experimental flag**, no v05 and no new arena recommendation.
  Do not mix in a partial-build fix or tune reserve values on the same sample. Next approach:
  compute a safe fallback sabotage before route search, then refine if time remains; also keep
  the diverse-opponent/held-out-map work open. Captured-state success alone is insufficient.
- Final default-off state: **70 JUnit tests**, **158/158** CG replays, `submit` compile check
  and **0/4** process problem games; first response max **624 ms**, later max **44 ms**.
  Warnings above the internal 36-ms search target still occur with the old scheduling, but
  these responses stay under 50 ms. The rebuilt Player contains the disabled experiment;
  the source is not byte-identical to the previous submission, so no unchanged-hash claim.

### Matched common-opponent check and threshold discussion (2026-09-14)

- User proposed testing reserve=6 against v03 and comparing with v04-v03. Hypothesis: the
  direct-v04 result may miss useful performance against another opponent. Re-ran both versions
  instead of comparing candidate results against historical percentages from different maps.
- Preselected **300 games each**, new authentic generated seeds **5000..5149**, 150 side-swapped
  pairs, 2 threads, CG **486/36 ms**. Ran control then candidate sequentially to avoid mutual CPU
  interference. All 150 map hashes and seeds match; v03 parameters/timing also match, no bot
  errors. Before these runs, `cgValidate` reproduced **158/158** games exactly.
- Control `v04` vs `v03`: **61.3%**, W-L-D **184-116-0**, map-grouped Hoeffding 95% CI
  **[50.2%, 72.4%]**, mean score diff **+380.8**, 82.9 turns, 176.1 seconds.
  Report: `build/bench/v04-v03-holdout5000.json`.
- Candidate `current?DISRUPT_RESERVE_MS=6` vs `v03`: **69.3%**, W-L-D **208-92-0**, CI
  **[58.2%, 80.4%]**, mean score diff **+813.3**, 82.3 turns, 147.0 seconds.
  Report: `build/bench/reserve6-v03-holdout5000.json`.
- Common arguments: `-Pbot2=v03 -Pgames=300 -Pthreads=2 -Pseed=5000`; omit `-Pmaps` to use the
  generator, set the above bot1 spec and a distinct `-Pout` path for each report. Both runs are
  on new maps, not the old seeds 4000..4149 or the previous downloaded-map screen.
- Matched game outcomes: candidate gains **33** wins and loses **9**, **258** unchanged;
  net **+24/300 = +8 pp**. Map-level means improve on **24**, regress on **3**, tie on **123**.
  Diagnostic paired-map percentile bootstrap: **[+4.3, +11.7] pp**, 20,000 resamples, xorshift32
  seed 20260914, each draw resamples all four outcomes of one common map together. Approximate
  interval for the difference against v03 only, not head-to-head odds. Fixed-order/JIT/host
  variation and arena opponent selection are not covered. It is NOT a replacement gate.
  Comparison details: `build/bench/reserve6-v03-holdout5000-comparison.json`.
- Explained the existing policy: observed rate >=55% AND lower confidence bound >50%, plus
  error/timing checks. The map-grouped distribution-free bound ignores observed variance;
  at 150 maps it is +/-11.1 pp, so the effective passing rate is >61.1%. Lowering just 55%
  cannot make the previous 54.3% [43.2%, 65.4%] pass. An inconclusive test does not show that
  the candidate has no benefit. Added this explanation to `benchmarking.md`; acceptance code
  and AGENTS policy are unchanged.
- Decision: this is a meaningful positive common-opponent signal, so keep reserve=6 as a
  candidate worth independent confirmation. Do not infer transitivity or arena strength:
  v03 remains related to v04, and the direct-v04 gate is still not passed. No v05, no parameter
  changes, no submission, no commit. A future policy for accepting smaller effects should be
  selected before the next confirmatory test, not retrofitted to this result.

### Reserve confirmation and aggressive-reference control (2026-09-14)

Predeclared before running: checkpoint **cb0f440**, candidate
`current?DISRUPT_RESERVE_MS=6`, no other candidate changes or threshold changes.

- Primary confirmation: **1000 games**, 500 new authentic generated maps, seeds **6000..6499**,
  candidate vs frozen v04, both sides, CG 486/36-ms search budget, 2 threads.
  Report: `build/bench/reserve6-v04-confirm6000.json`.
- Additional common-opponent control: **300 games each** on the same new seeds **8000..8149**,
  v04 then candidate against `v02?W_SELF_LOSS=0.5,W_FUTURE_TRACK=2.0,W_FUTURE_OWN=0.1`.
  Reports: `build/bench/v04-v02aggressive8000.json` and
  `build/bench/reserve6-v02aggressive8000.json`.
- This artificial reference discounts its own sabotage losses and emphasizes enemy tracks.
  v02 lacks v03/v04 vulnerability evaluation and backup routes; its frozen source is unchanged.
  It is still from the same strategy family, not an independently validated arena-meta bot.
  No search over opponent weights will be performed. The control cannot replace broad
  opponent diversity. Runs are sequential to avoid inter-benchmark CPU interference.
- Decision will use the existing primary gate; auxiliary opponent results are corroboration,
  not a way to bypass it. No adaptive extension or early stopping based on intermediate wins.

Results and decision:

- Primary 1000-game confirmation: **57.7%**, W-L-D **575-421-4**, map-grouped 95% CI
  **[51.6%, 63.8%]**, mean score difference **+879.1**, mean **75.1** turns, **538.7 s**.
  Candidate wins as P0 **281/500**, as P1 **294/500**. All 500 map hashes are distinct;
  no bot errors. The unchanged primary promotion gate **passes**. The test continued after
  the connection interruption; it was not restarted or extended based on its intermediate rate.
- Aggressive-v02 control on seeds 8000..8149, 300 games each:
  - v04: **67.7%**, W-L-D **203-97-0**, map-grouped CI **[56.6%, 78.8%]**, mean diff
    **+342.3**, **76.3** turns, **89.2 s**.
  - reserve=6: **70.0%**, W-L-D **210-90-0**, CI **[58.9%, 81.1%]**, mean diff **+620.0**,
    **75.8** turns, **75.6 s**.
  - All **150** distinct map hashes/seeds, v02 configuration and timing settings match.
    Candidate gains **14** wins, loses **7**, **279** unchanged; map-pair totals improve on
    **10**, worsen on **6**, tie on **134**. No bot errors. The positive +2.3-pp estimate
    is small and is not evidence of equivalence/non-inferiority or arena improvement.
- Decision: primary gate passes and neither common-opponent control shows a negative point
  estimate (v03 +8 pp, aggressive-v02 +2.3 pp). Promote the already-tested reserve=6 as **v05**;
  no evaluation-weight tuning or acceptance-rule changes. The old 300-game downloaded-map
  screen remains recorded as inconclusive, not retroactively relabelled as a passing test.
  Arena diversity is still incomplete: the additional profile is a fixed artificial stress
  control from our own family, not a new independent implementation or validated meta model.
- Set current's reserve default to 6; freeze source in `bot/v05`, register in BotFactory and
  save **every parameter from the primary report** in `versions/v05.properties`. Tests check
  the full snapshot/current match, factory wiring, independent overrides, and current/frozen
  action agreement on a fully searched state. No changes to existing v01-v04 sources/snapshots,
  core rules, total budgets or CGRunner margin. Disable reserve with `current?DISRUPT_RESERVE_MS=0`.
- Final verification: **72 JUnit tests**, **158/158** CG arena replays exact, merged submission
  compiled. **0/8** process problem games: 4 normal corpus maps and both sides of each captured
  large failure map (903509641, 903508669). First response max **676 ms**, later max **34 ms**;
  no local timeouts. Parallel bench timing outliers are not used as process timing estimates.
- Startup caveat identified in review: generic `Bot.warmup` calls `play` with 5 ms, which is
  below the 6-ms reserve and therefore skips route search. The process checks include this
  actual startup behavior and still pass; no warm-up change was mixed into this experiment.
  Explicit route warm-up remains a separate follow-up, as do atomic evaluation overruns.
- Submission ready: `build/submit/Player.java`, SHA-256
  `5307e4f0f53b39ef8768d364e24fcf701d4f9ca3eafa444aa23a38d3a34a9c5a`.
  No upload/arena result claimed. The previous experiment was committed as **cb0f440**;
  this confirmation/promotion work is left uncommitted for review.

### v05 arena checkpoint and known-state build-income guard (2026-09-14)

- User reports v05 uploaded: rank **321** at **43%** placement, about 54 completed games;
  remembers v03 around **335** before the v04 upload. These are user observations, not matched
  strength measurements. Public fetch snapshot: **aabzac**, agent **6736850**, rank **333**,
  score **18.11**. Agent id identifies this submission entry; source hash is not public.
- Fetched **56** completed games (requested up to 60), **38 wins / 18 losses**, no own server
  failures. All **214/214** corpus games reproduce exactly. `cgAnalyze -Pagent=6736850` wrote
  `build/analysis/agent-6736850.json`. Heights 19-20: **11/18 wins**, versus v04's earlier 1/20;
  opponents and placement stage differ, so this is not a causal improvement estimate.
- Loss disruption rate **75.8%** (v04 earlier 40.1%), average loss length **92.8** turns.
  Accepted own cells never earning: **1431/2979** in losses; this includes possible useful
  blocking/unfinished work. Same-turn counterfactuals identify **24** harmful build turns in
  **14/18** losses, and **42** in wins. Do not sum these into hypothetical match outcomes.
- Viewed 903584094 (RTJ) at t13 / slider14 and 903583608 (Reh934) at t27 / slider28. Important
  distinction: RTJ's 71-point same-turn build harm involves opponent sabotage; on the known
  pre-turn board the build actually improves income difference by **44**. A no-lookahead guard
  cannot predict that case. Reh934's build already hurts known-state income difference by **26**
  (52 with actual simultaneous actions). fsicre game 903584040 t52 hurts by **34**, and old
  DiogoGiassi 903507615 t46 by **38**, even without opponent actions. Existing v05 reproduces
  those constructions with idle extension disabled. Probe: `tmp/ProbePartialBuild.java`.
- Single experiment: `GUARD_BUILD_INCOME` (default **false**) simulates a candidate's actual
  affordable cells in execution order and vetoes a decrease in raw own-minus-opponent income.
  The same guard covers idle cells. Complete-route evaluation remains the ranking heuristic;
  no opponent-future knowledge, sabotage changes, warm-up fix or weight tuning. Rules/income
  computation stay in core. This may be too conservative about worthwhile temporary losses.
- Preselected screen: `current?GUARD_BUILD_INCOME=1` vs frozen v05, **428 games** covering all
  **214** downloaded arena maps once per side, CG 486/36-ms profile, 2 threads, base seed 1000.
  Report: `build/bench/guard-income-v05-cg428.json`. No adaptive extension based on interim wins;
  the flag will remain off unless the normal gate and subsequent confirmation pass.

Outcome:

- Guard replaces known harmful builds in 903583608 t27, 903584040 t52 and 903507615 t46 in
  local probes; RTJ t13 remains allowed, correctly illustrating the distinction between
  known-board harm and harm conditional on an opponent's simultaneous sabotage. Visualizer
  action/income panels for both viewed games matched the recorded trace.
- **428 games / 214 distinct CG maps**, W-L-D **206-222-0**, candidate **48.1%**,
  map-grouped 95% CI **[38.8%, 57.4%]**, average score difference **-58.1**, **73.8** turns,
  **198.5 s**, no bot errors. Fixed sample completed; gate **not passed**. This does not prove
  the candidate is weaker, but there is no basis for enabling it or running a promotion.
- Keep the experimental flag **false**, current strategy **v05**, frozen versions untouched.
  Checked-in map-seed/stdout prefixes and tests verify known-board harm, legal alternative
  placements and simulation isolation. No arbitrary parameter tuning or warm-up changes.
  Next candidate mechanism: preserve the valuable route while trying a different affordable
  placement order, so a shortcut need not be activated early. That is a hypothesis, not yet code.
- Final checks: **74 tests passed**, **214/214** CG replays exact. No 1000-game confirmation or
  process-level candidate promotion check after this failed screen. Existing submission was
  deliberately NOT regenerated: `build/submit/Player.java` remains the uploaded v05 artifact,
  SHA-256 `5307e4f0f53b39ef8768d364e24fcf701d4f9ca3eafa444aa23a38d3a34a9c5a`.
- Arena caveats: the 38 wins include one opponent timeout (903584839), none by us. There are
  still **218** recorded turns in **34** games without sabotage where the full-information
  same-turn oracle finds a positive immediate sabotage delta. This oracle knows the opponent's
  actual current actions; those opportunities are not all knowable to the bot and are not all
  proof of timer starvation. Neither the reserve nor this build experiment solves prediction.
- No new commit or upload. Updated BOTS/PLAN with provisional v05 arena evidence; remembered
  v03 rank and incomplete placement are kept separate from measured bench results.

### 2026-09-14 — selected-route build direction experiment

- Hypothesis: the hard income veto rejected useful long-term routes. Keep the route evaluation
  unchanged and choose which end to build first, using the immediate raw income difference
  after only the affordable placements. No opponent future actions enter the evaluation.
- Added default-off `CHOOSE_BUILD_DIRECTION`. Once a route is selected, compare forward and
  reverse placement prefixes, preserving uncontested-first priority and terrain-cost skipping.
  Reverse only on a strict income improvement; ties retain the old direction. Neither direction
  is vetoed if both lose income. Skip the extra work when the full route fits or build time is up.
  Reuse scratch states; no added per-candidate buffers, weights, route ranking or RNG draws.
  The prior `GUARD_BUILD_INCOME` remains false. Combining it with this flag still applies its
  historical forward-prefix veto before direction selection; this experiment does not test that
  combination. Sabotage timing/reserve and frozen v05 are unchanged.
- Fixed screen specified before results: 428 games / all 214 downloaded CG arena maps, both
  sides, 2 threads, CG timing profile, candidate
  `current?CHOOSE_BUILD_DIRECTION=1,GUARD_BUILD_INCOME=0` versus `v05`.
  Output: `build/bench/build-direction-v05-cg428.json`. Engine check: 214/214 exact.
- Ample-time, NOISE=0 probes: Reh934 903583608 t27 now builds `10,11` instead of `7,11;8,11`;
  fsicre 903584040 t52 builds `17,6;17,7` instead of `18,11`. DiogoGiassi 903507615 t46 still
  builds the harmful `12,2` shortcut. RTJ 903584094 t13 is unchanged with completed search;
  its recorded damage depends on simultaneous opponent actions, not the known-board prefix.
  These probes diagnose selected positions, not match outcomes or process timing.
- Completed screen: **224-204-0**, **52.3%**, map-grouped 95% CI **[43.1%, 61.6%]**,
  average score difference **+76.8**, **70.5** turns; A as P0 **115/214**, P1 **109/214**.
  **173.5 s**, no bot errors, no nominal-budget slow moves. Search-only average time was
  **5.25 ms** versus **5.22 ms**; this is not a process-level timing guarantee.
- Decision: **gate NOT PASSED**. Keep `CHOOSE_BUILD_DIRECTION=false`,
  `GUARD_BUILD_INCOME=false`, current/default strategy v05. The positive point estimate is
  inconclusive; it does not justify enabling the feature or adjusting the promotion threshold.
  No 1000-game confirmation, older-opponent promotion check or candidate process check after
  the failed screen. No v06, commit or upload. The previous veto's 48.1% and this 52.3% are
  separate screens versus v05, not a direct statistically established comparison.
- Final checks: **79 tests passed**; **214/214** CG replays exact before the bench (core unchanged).
  Existing v05 `build/submit/Player.java` was not regenerated; SHA-256 remains
  `5307e4f0f53b39ef8768d364e24fcf701d4f9ca3eafa444aa23a38d3a34a9c5a`.

### 2026-09-14 — next-experiment diagnosis: early sabotage fallback

- Request: propose the next experiment after the two failed build screens; no strategy change.
  Re-read the working agreement, README, PLAN, BOTS and recent DEVLOG entries. Current remains
  v05, reserve=6; both build flags remain false.
- Recounted the existing agent-6736850 report: **168** turns without our sabotage have a
  positive same-turn oracle alternative across **16/18 losses** (218 turns across all outcomes
  were already documented). These are opportunities under recorded opponent actions, not
  168 proven timer failures or additive lost match points.
- Validated and reconstructed four selected missed-sabotage states through core. With only
  our recorded placements and the opponent doing nothing, inking the already-instability-3
  target improves immediate income difference: timbersson game **903583443**, t27 region29
  **+366**, t31 region41 **+311**, t32 region41 **+354**; Irioth game **903583695**, t56 region10
  **+121**. Turns are zero-based. These deltas differ from the simultaneous-action oracle.
- Warmed local probes of frozen `v05?NOISE=0` choose those same sabotage targets at both
  36-ms and ample 1000-ms budgets. Probe: `tmp/ProbeV05Disrupt.java`; output:
  `build/analysis/v05-disrupt-proposal-probes.txt`. This establishes known-board opportunities
  and that the existing evaluation can select them. It does NOT reproduce the server omission
  or prove its cause: local CPU, warm-up, RNG and execution history differ.
- Proposed single candidate: a default-off early sabotage fallback, initially considering
  regions at instability 3 before expensive route search; retain existing scoring weights,
  6-ms reserve and normal post-build sabotage refinement. Revalidate the saved target after
  actual affordable placements, including the existing losing-map-closure exclusion, before
  emitting it. Account for this recheck in the time budget. Do not emit a stale pre-build target.
- Suggested evaluation: deterministic scheduling/isolation regressions and these captured
  states first; fixed 428-game screen versus v05 on the 214 existing paired CG maps, then
  independent 1000-game confirmation on unused generated maps if the screen passes. Use the
  existing >=55% / map-grouped lower CI >50% gate, older/aggressive-reference controls and
  submit/process checks before promotion. Inspect results by map size without subset promotion.
- Decision: propose this scheduling experiment before another build heuristic or weight sweep.
  No candidate implementation, strategy benchmark, version freeze or submission performed.

### 2026-09-14 — early ready-to-ink sabotage fallback screen

- Implemented default-off `EARLY_DISRUPT_FALLBACK`. Before route search it deterministically
  scores only legal regions at instability 3 with existing tracks. It consumes no strategy RNG.
  The normal noisy post-build scan remains authoritative when it completes. The saved target is
  used only if that scan hits the timer without finding a target; after any placement, its score
  and map-closing safety are recomputed first. Existing scoring weights, reserve, build selection
  and frozen v05 are unchanged.
- The disruption scratch counters are now bot-owned primitive buffers rather than per-turn array
  allocations. With the experiment disabled, full-search action equivalence against frozen v05 is
  covered by the existing test. New tests force the normal scan to start after its deadline,
  verify recovery of a ready captured target, default-off behavior, and the losing-close rule.
- Captured v05 probes: at zero internal budget, frozen v05 returns WAIT while the candidate emits
  regions 29/41/41/10 in the four diagnosed timbersson/Irioth states. At 36 ms and ample time both
  choose the same placements and sabotage. This is deterministic mechanism coverage, not a timing
  or match-strength result.
- Predeclared screen: `current?EARLY_DISRUPT_FALLBACK=1,GUARD_BUILD_INCOME=0,CHOOSE_BUILD_DIRECTION=0`
  versus frozen v05, **428 games / all 214 downloaded arena maps**, both sides, 2 threads, CG
  timing, base seed 1000. Existing gate: at least 55% and map-grouped lower 95% bound above 50%;
  no adaptive extension. If it fails, leave the flag off and do not run confirmation/promotion.

Outcome:

- Completed the fixed sample: **212-214-2**, candidate **49.8%**, map-grouped 95% CI
  **[40.5%, 59.1%]**, average score difference **-28.9**, **73.3** turns, **179.0 s**.
  A as P0 won 100/214; as P1, 112/214. All 214 map hashes are distinct; no bot errors or
  nominal-budget slow moves. Search-only time was 5.07 ms average for the candidate and 5.30 ms
  for v05; this is not a process timing result. Report:
  `build/bench/early-disrupt-v05-cg428.json`.
- Exploratory height split: h14 28-28, h15 32-32, h16 22-22-2, h17 22-22, h18 42-40,
  h19 29-39, h20 37-31. The opposing h19/h20 estimates and small selected strata do not support
  a size-specific promotion or follow-up tuning.
- Decision: **gate NOT PASSED**. Keep `EARLY_DISRUPT_FALLBACK=false` and current at v05.
  The mechanism recovers forced deadline cases, but this broad screen gives no evidence of a net
  match-strength gain. Do not run a 1000-game confirmation, older-opponent check, process
  promotion check, v06 freeze or new submission for this candidate.
- Verification: **82 tests passed** and **214/214** CG replays reproduced exactly before the
  bench. Core rules and frozen bot sources were unchanged. Existing v05 submission was not
  regenerated.

### 2026-09-14 — top-4 routes against top-2 likely replies

- Hypothesis from the user: retain several routes that already pass v05's long-term value filter,
  predict likely opponent moves from the same pre-turn state, resolve each combination
  simultaneously, and choose the route that survives best. This targets collisions, current-turn
  sabotage and harmful affordable prefixes together rather than adding another local veto.
- Implemented default-off `USE_REPLY_RERANK` with `BUILD_BEAM_WIDTH=4` and
  `OPP_REPLY_WIDTH=2`. Only the first build decision of the turn is reranked. Candidate identity
  is the actual affordable placement prefix; duplicate prefixes from different complete paths are
  collapsed. The original best route remains the fallback if any later phase runs out of time.
- Our top four use the unchanged v05 completed-route formula and RNG. The opponent's two routes
  use the same formula deterministically from the original board. Each reply also predicts the
  opponent's existing sabotage choice. For every combination, core applies both placement arrays,
  the predicted sabotage, ink, connection updates and scoring in referee order. The selected
  route maximizes its worst value across the usable replies; ties keep v05's original order.
- After the simultaneous turn, the evaluator hypothetically completes that same route. Its score
  combines the immediate score difference with the existing projected route gain, completion
  delay and instability risk. A route crossing a newly inked cell is rejected. No second-turn
  opponent reaction, learned probabilities, new evaluation weights or parameter sweep are part of
  this experiment. Old `USE_OPP_MODEL` and all three failed experimental flags remain off.
- Buffers for four routes, two replies and scratch states are allocated at bot initialization.
  Later-turn phase cutoffs leave time for our candidates, opponent candidates, reply evaluation
  and the existing 6-ms sabotage reserve. If a cutoff prevents complete reranking, the stored v05
  top candidate is used.
- Deterministic seed-1000 regression: both player perspectives change from prefix
  `1,2;1,3;2,3` to `6,11;7,11;8,11`, avoiding the modelled symmetric first choice; emitted actions
  are legal. Captured build probes remain legal; the candidate changes RTJ t13 but deliberately
  makes no claim that every historical harmful prefix is fixed.
- Non-gating smoke check: 20 games / 10 fresh generated maps, seeds 9000..9009, sides swapped:
  **11-9**, 55.0%, no errors or nominal-budget slow moves; candidate average search 5.23 ms,
  max 33 ms. Report: `build/bench/reply-rerank-smoke20.json`. The sample is only a wiring/timing
  check and is not promotion evidence.
- Predeclared screen: candidate
  `current?USE_REPLY_RERANK=1,BUILD_BEAM_WIDTH=4,OPP_REPLY_WIDTH=2,USE_OPP_MODEL=0,GUARD_BUILD_INCOME=0,CHOOSE_BUILD_DIRECTION=0,EARLY_DISRUPT_FALLBACK=0`
  versus frozen v05, **428 games / all 214 downloaded arena maps**, both sides, 2 threads, CG
  timing, base seed 1000. Existing gate: at least 55% and map-grouped lower 95% bound above 50%;
  no adaptive extension or tuning. A failed screen leaves the feature default-off and stops the
  promotion sequence.

Outcome:

- Completed the fixed sample: **199-229-0**, candidate **46.5%**, map-grouped 95% CI
  **[37.2%, 55.8%]**, average score difference **-50.8**, **66.7** turns, **247.1 s**.
  A as P0 won 102/214; as P1, 97/214. All 214 map hashes are distinct and there were no bot
  errors. Search-only time averaged **9.75 ms** for the candidate versus **6.50 ms** for v05;
  the candidate had 3 nominal-budget slow moves (max 61 ms) versus none for v05 (max 45 ms).
  This parallel in-process measurement is not a full process timing check. Report:
  `build/bench/reply-rerank-v05-cg428.json`.
- Exploratory height split: h14 **23-33**, h15 **35-29**, h16 **15-31**, h17 **22-22**,
  h18 **39-43**, h19 **33-35**, h20 **32-36**. The selected strata are small and inconsistent;
  they do not support enabling or tuning the feature for a map-size subset.
- Decision: **gate NOT PASSED**. Keep `USE_REPLY_RERANK=false`; current remains v05. The exact
  one-turn model is materially more expensive and its likely-reply maximin choice loses overall.
  Do not tune K/R after observing this screen, and do not run a 1000-game confirmation,
  older-opponent promotion check, process promotion check, v06 freeze or new submission.
- Verification: **84 tests passed** and **214/214** CG replays reproduced exactly before the
  bench. Core rules and frozen bot sources were unchanged. Existing v05 submission was not
  regenerated.

### 2026-09-14 — reply-rerank K/R diagnostic ablation

- Follow-up requested after the failed K=4/R=2 screen. Predeclared comparison on the same fresh
  generated maps: K=4/R=1 and K=2/R=1, each versus frozen v05 for **300 games / 150 maps**,
  both sides, 2 threads, CG timing, base seed 10000. All earlier failed flags and
  `USE_OPP_MODEL` remain off. Reusing the same maps is intentional for matched diagnosis.
- K=4/R=1 isolates removal of the second-reply worst-case choice. K=2/R=1 additionally reduces
  our route beam and search cost. These are diagnostic screens after observing K=4/R=2; neither
  can be promoted from this comparison. A result at least 55% with its map-grouped lower 95%
  bound above 50% would justify a separately predeclared independent confirmation.

Outcome:

- K=4/R=1: **148-152-0**, **49.3%**, map-grouped 95% CI **[38.2%, 60.4%]**, average score
  difference **+25.5**, **64.0** turns. Candidate search averaged **7.58 ms**, max 53 ms with
  1 nominal-budget slow move; v05 averaged 4.66 ms. Report:
  `build/bench/reply-rerank-k4r1-v05-gen10000.json`.
- K=2/R=1: **166-132-2**, **55.7%**, map-grouped 95% CI **[44.6%, 66.8%]**, average score
  difference **+138.4**, **67.7** turns. Candidate search averaged **6.84 ms**, max 53 ms with
  2 nominal-budget slow moves; v05 averaged 4.27 ms. Report:
  `build/bench/reply-rerank-k2r1-v05-gen10000.json`.
- On the common sample, K=2/R=1 has a **+6.3 percentage-point** aggregate advantage over
  K=4/R=1 and 18 more wins. The aggregate reports do not retain the per-game joint outcomes,
  so this is a matched-map descriptive comparison, not a paired confidence interval between
  variants. It suggests that considering fewer own alternatives is better than reranking four,
  but does not establish the cause.
- Decision: both diagnostic gates **NOT PASSED**. K=4/R=1 misses the 55% point threshold;
  K=2/R=1 reaches it, but its map-grouped lower confidence bound is below 50%. Following the
  predeclared rule, do not extend the sample adaptively or start an independent confirmation.
  Keep `USE_REPLY_RERANK=false`, current at v05, and do not create v06 or a new submission.

### 2026-09-15 — sabotage forecast, predeclared diagnostic protocol

- User approved an adaptive opponent model, starting with prediction only; no change to bot
  decisions in this step. User also confirmed the arena's 100,000-character source limit and
  requested restoration of the temporary Player artifact to frozen v05.
- Offline `cgForecast` compares five fixed experts (current income damage, track balance,
  imminent completion, persistence of an observed target, no effective sabotage). Equal initial
  weights, Bayesian likelihood updates with `FORECAST_DECAY=0.95`, categorical smoothing
  `FORECAST_FLOOR=0.10`; no sweep. Core computes destruction/income and observation transitions.
  These Params fields are diagnostic only; forecast code is in tools and never ships.
- Compare adaptive versus equal fixed mixture AND the single expert selected by development
  mean game log-loss. Primary loss is categorical negative log likelihood; also retain Brier,
  top-1 and target-only hits. Prediction happens before applying either current command; updates
  see only next-board instability/ink and our own known accepted action. Opponent command text
  is confined to the harness and truth audit. Ignore failed terminal turns and skip ambiguous
  observations rather than using privileged labels. Reset learning every game.
- Fixed corpus: current 214 canonical arena replays, filtered to agent 6736850 (56 previously
  fetched games). Split by opponent agent SHA-256("forecast-v1:" + agent), first byte modulo 3:
  bucket 0 holdout, others development. No opponent entry crosses the split. This is a new
  diagnostic split of already inspected games, NOT pristine arena confirmation.
- Run the fixed protocol once without tuning. Report mean per-opponent held-out log-loss
  improvement and paired opponent-bootstrap intervals (2,000 samples, seed 15092026). Evidence
  to consider integration: positive improvement with lower 95% bounds above zero against BOTH
  controls, no observation/truth mismatches. Even passing this diagnostic is not win-rate proof:
  clean future data and a separately gated strategy experiment remain required.

Outcome:

- Fixed diagnostic completed without tuning: 40 development games / 35 opponent agents and
  16 holdout games / 15 opponents; 4,258 observations, zero unknowns or truth mismatches,
  one failed terminal turn excluded. Development selected the persistence expert.
- Holdout mean-per-game log loss: adaptive 1.7166, fixed mixture 1.8165, persistence 1.9544;
  top-1: 69.9%, 57.7%, 68.2%. Mean-per-opponent improvement versus fixed is
  +0.0928, 95% CI [-0.0450, +0.2220]; versus persistence +0.2292 [+0.0861, +0.3841].
- Decision: diagnostic gate NOT PASSED (fixed-mixture interval crosses zero). Keep the model
  tools-only, do not tune on this holdout, integrate decisions, run promotion benches or freeze
  v06. Full protocol/results: `sabotage_forecast.md`; trace report:
  `build/analysis/sabotage-forecast-6736850.json` (local, gitignored).
- Added explicit `submit -PsubmitVersion=v05`, resolved frozen configuration, a 100,000-character
  pre-write limit, and submission manifest. Frozen sources and core rules unchanged. This
  restores v05 strategy with current shared runtime, not the original byte-for-byte artifact.
  Source: 62,821 UTF-16 characters / UTF-8 bytes, SHA-256
  `30a6fcd2e56c8ec52901ec3c9f329711c7cce25841cbd55b9b9cf8fa27496647`.
- Verification: 91 tests (including isolated compilation/action parity of merged v05), 214/214
  exact CG replays. External artifact versus v04: four standard games plus both sides of
  `903509641` and `903508669`, 7/8 wins, zero registered timeouts/problem games. These are
  process smoke checks, not strength evidence. First responses 669-773 ms; later maxima
  18-38 ms in seven games and 180 ms in the last. The existing queue-based harness grants
  30-ms grace and only counts missing poll results as timeouts; it did not flag the outlier.
  Cause is unproven; no strict 50-ms guarantee. Track timing-harness investigation separately.

### 2026-09-15 — process deadline accounting, no strategy change

- Next step: investigate the 180-ms accepted response before further strategy experiments.
  Found two accounting defects: `poll` received a fresh full timeout AFTER input preparation /
  write, and success depended only on receiving a line, without checking its arrival deadline.
  Consumer scheduling can also inflate elapsed time after an on-time reader arrival. The old
  logs cannot establish which event caused that particular 180-ms observation.
- Timestamp reader arrivals and subtract serialization/write/flush time from a single absolute
  turn allowance. Accept an on-time reader arrival even if the consumer wakes later; reject a
  late queued line. Default grace changes from implicit 30 ms to zero; explicit diagnostic
  `-PgraceMs` remains available and reports nominal overruns. EOF is separate from timeout.
  End the process session on failure instead of returning WAIT and risking stdout misalignment.
- Record every turn's send/arrival/consumer timing and status in JSON, alongside map, side,
  opponent configuration, source SHA-256 and actual class-directory hash. This is local observed
  wall time, not a reconstruction of CG CPU time; reader scheduling and blocked writes remain
  limitations. No core, strategy, frozen source or submission runtime changes.
- Fixed verification: deterministic deadline/queue tests plus two actual child-process fixtures;
  full tests, rebuild frozen v05, then four default-map games and both sides of captured maps
  903509641 and 903508669 against v04, sequentially, strict zero grace. Do not relax deadlines
  or rerun until green if failures appear. This is process evidence, not a strength bench.

Outcome:

- **99 tests passed**, including eight new deadline/transport tests: delayed consumer with an
  on-time arrival, late queued answer, write-time accounting, explicit grace, missing answer,
  EOF, boundary/unsolicited answer, and real child timeout/EOF fixtures. Core was unchanged;
  the most recent fidelity run remains 214/214 exact replays.
- Rebuilt v05: source still 62,821 characters, SHA-256
  `30a6fcd2e56c8ec52901ec3c9f329711c7cce25841cbd55b9b9cf8fa27496647`.
  All three runs used the same class-directory SHA-256
  `b6cb320ea6b849da80b1f7debcdd550d9173c916d011f23a23170732bd84cfd8`.
- Fixed strict sample: **7/8 games without process errors**, **one timeout**, 4/8 wins vs v04
  (wins are smoke-check data only). Default four games passed; both games on 903508669 passed.
  On 903509641, P0 turn 53 had send time **1.2339 ms**, reader arrival **50.7868 ms**,
  consumer completion **56.2206 ms**. A line was actually returned by the queue AFTER the
  absolute deadline, demonstrating the acceptance bug addressed here; the new check rejected
  it and failed the task. It would also have fitted the old 80-ms allowance.
- Other games' later maximum arrivals ranged 16.8258-46.1326 ms. First arrivals ranged
  612.1949-959.3518 ms: within the formal 1,000-ms limit, but one exceeds our 800-ms headroom
  target. No relaxed-grace reruns or strategy edits were made to hide the failure.
- Reports: `build/analysis/process-strict-v05-default4.json`,
  `build/analysis/process-strict-v05-903509641.json`,
  `build/analysis/process-strict-v05-903508669.json`. Each contains all per-turn observations.
- Decision: deadline accounting fix is verified; **v05 did not pass this strict local sample**.
  The old 180-ms event remains unattributed. Next is phase-level timing diagnosis on large maps,
  not promotion of an adaptive strategy or a claim of CG-host timeout probability.

### 2026-09-15 — two-step timing investigation and interrupted-evaluation screen

- User requested a commit and two next steps. Committed preceding tools as `75ba0ca`.
- Step 1: opt-in primitive phase counters in current's baseline-equivalent search (frozen v05
  source untouched), plus `cgTiming` on validated states from 903509641 and 903508669, both
  sides. 400 calls, 486/36-ms search allowances, 500-ms standard warmup; reset to recorded
  positions after each call. This is not the local match trajectory where turn 53 timed out,
  nor an exact frozen-executable profile. Instrumentation and JIT influence wall time.
- Baseline: build 10,172.10 ms, inclusive nested vulnerability eval 5,487.35 ms (**53.9%**),
  sabotage 678.92 ms. Example t59 P0 on 903509641: build **35.24 ms**, longest eval **4.04 ms**,
  beyond the intended 30-ms build boundary of a 36-ms call. All four warmups ran about
  500 ms with **zero route-search calls**: the 5-ms call budget is below the 6-ms reserve.
  Report: `build/analysis/search-timing-baseline.json`. Old exact timeout cause is unproven.
- Step 2 hypothesis: default-off `INTERRUPT_VULN_EVAL` checks the build deadline between
  vulnerability-region simulations. Incomplete evaluations return a sentinel, not an optimistic
  partial value; no candidate RNG is consumed and only fully evaluated candidates may replace
  the saved best route. Incomplete base evaluation skips that search. Sabotage retains its
  original allowance; no warmup, weights, reserve or core changes. Reply-rerank combination
  is rejected until explicitly supported. A single core simulation remains atomic.
- Predeclared screen: `current?INTERRUPT_VULN_EVAL=1,PROFILE_SEARCH=0` vs frozen v05, **300
  games / 150 generated paired maps**, seed 12000, two workers, CG timing. All previous
  experimental flags off. Existing >=55% / map-grouped lower CI >50% gate; no adaptive sample
  extension. Only if passed: independent confirmation and older-opponent checks before promotion.
- Additional mechanism check: same cgTiming replay states once with the flag; diagnostic only.
  Process check candidate merged with an explicit parameter query, two sides of both captured
  large maps, then restore v05 and check four default maps. No relaxed grace or rerun-until-green.
  Candidate artifact is temporary; no upload or automatic promotion.

Outcome:

- Mechanism diagnostic completed once: 400 candidate calls, build 10,167.48 ms, nested eval
  5,440.23 ms, sabotage 677.55 ms. Across the 396 later-turn calls, build p95 decreased from
  **32.3034 to 30.5352 ms**, maximum from 35.4970 to 32.9619 ms, calls above 31 ms from
  **90 to 7**. Discarded 214 incomplete evaluations. Overall work still uses the available
  allowance (including sabotage), and atomic core operations can still overrun; this is tighter
  phase accounting, not a hard deadline guarantee. Sequential instrumented runs are not a
  controlled CPU speedup comparison. Report: `build/analysis/search-timing-interrupted.json`.
- Fixed strength screen: **154-146-0**, **51.3%**, map-grouped 95% CI **40.2-62.4%**,
  average score difference **+38.9**, 73.0 turns, 167.6 s. Candidate as P0 74/150, as P1 80/150;
  no bot errors. Search averaged 7.06 ms vs 7.01 ms; candidate max 61 ms / one nominal-budget
  slow move, reference max 36 ms / zero. Report: `build/bench/interrupt-eval-v05-gen12000.json`.
- Decision: **gate NOT PASSED**. Keep `INTERRUPT_VULN_EVAL=false`, current v05; no v06,
  independent confirmation, older-opponent promotion bench or upload. The separately
  predeclared process checks are diagnostic, not an exception to the strength gate.
- **103 tests passed**, **214/214 exact replays**. Tests cover expired evaluation sentinel,
  no build after cutoff while sabotage remains possible, unsupported reply-rerank combination,
  ample-time action parity with frozen v05, and explicit parameter embedding. Core and frozen
  sources unchanged. `PROFILE_SEARCH=false` during strength and process checks.
- Temporary candidate submission: **85,368 / 100,000 characters**, SHA-256
  `36f058fb39deb664a2797d64633c5ba165419f85eea8aae780aa1ca8d1e506f5`.
  Both sides of both large maps vs v04: **0/4 process failures**, 3/4 wins (smoke only),
  first 676.24-729.62 ms, later max 38.8861 ms, strict zero grace. Reports:
  `build/analysis/process-interrupt-eval-{903509641,903508669}.json`.
- Restored v05 strategy artifact: **62,959 characters**, SHA-256
  `491fadca4ec22867e886859b88e0d66beda58e7cf6cbff38ec5f3d62aa1ebba3`.
  Two unused, default-off Params fields account for the changed v05 packaging; no candidate
  implementation ships in this restored source. Four default-map process games: **0/4 failures**,
  first 675.89-853.44 ms, later max 38.3078 ms, zero grace. The 853.44-ms first response still
  exceeds the 800-ms headroom target despite fitting the formal 1,000-ms deadline. Report:
  `build/analysis/process-v05-restored-after-interrupt.json`. This does not erase earlier failures.
- Both requested steps are complete. Next separately scoped experiment: reach route search
  during warmup without changing live RNG/state semantics, then check startup headroom.
  New work after `75ba0ca` remains uncommitted for review.

### 2026-09-15 — isolated route warmup runtime experiment

- User requested commit and warmup fix; preceding phase study committed as `fc568af`.
- Add a default-off submission-runtime flag `ISOLATED_ROUTE_WARMUP` in MainBot. Construct a
  disposable instance of the SAME merged strategy/parameters, reset a scratch state each call,
  use `WARMUP_SLICE_MS=36` within the existing 500-ms allowance. Init/reset costs count toward
  that allowance, and no undersized final slice is started. Individual calls are still cooperative
  and can overrun. Live delegate, its RNG and game state are never used for warmup.
- Generic Bot warmup, frozen strategy implementations, core rules, search budgets, margin,
  scoring and all failed strategy flags remain unchanged. A mainless in-process strength bench
  does not invoke this hook and would not evaluate it; this is a runtime fix, not v06 promotion
  or evidence of a higher win rate. Time-limited decisions may change when JIT coverage changes.
- Predeclared verification: scheduler/state/RNG tests, actual route-call counter check, merged
  frozen-v05 parity with/without isolated warmup, full tests; sequential strict process sample
  against v04, **8 games each** (four default maps plus both sides of 903509641 and 903508669).
  Build v05 with flag 0 for control and flag 1 for candidate; compare exact hashed artifacts.
  No concurrent CPU-heavy tasks, relaxed grace, tuning or rerun-until-green.
- To enable the runtime fix by default: candidate must pass all 8 formal deadlines/protocol
  checks, all first replies below the existing 800-ms headroom target, and semantic tests green.
  Compare control descriptively, not as a statistical CPU-speed or arena-strength claim. If this
  passes, rebuild default v05 and verify the resulting source is the tested candidate or rerun
  its process checks. Otherwise keep flag off and restore/recheck default v05.

Outcome:

- **109 tests passed**, **214/214 exact CG replays**. Six added tests cover scheduler setup/reset
  accounting, fractional remaining time, route search actually reached, live RNG/action parity
  over successive turns for both players, zero/invalid allowance, and compiled frozen-v05
  warmup isolation. No core, frozen source or policy change; a strength bench without the
  submission warmup hook would not test this fix and was not run.
- Fixed control sample: 0/8 process failures, first-response mean **626.9413 ms**, range
  **573.9943-696.8371 ms**; later max **38.2203 ms**. Source SHA-256
  `e4de383989b1efedee45436dbe1c459f44d316e707feb6c0acaa3099e220254a`, 64,788 characters.
  Control flag off; the inactive isolated scheduler's rounding was tightened before the
  candidate build (nanosecond remainder, covered by a regression test).
- Fixed candidate sample: 0/8 failures, first mean **554.2242 ms**, range **541.9555-577.8871 ms**;
  later max **38.2355 ms**. Source SHA-256
  `a5c4dd51d883c294bd29845eea3cfdfd1932404688e9c7c4d65e5849924d8cd3`, 64,776 characters.
  Both variants used zero grace; 4/8 control and 5/8 candidate wins are smoke data, not strength
  evidence. No timing tuning or rerun-until-green. Reports:
  `build/analysis/warmup-{control,candidate}-{default4,903509641,903508669}.json`.
- Runtime gate **PASSED**. Enabled `ISOLATED_ROUTE_WARMUP` by default (36-ms slice unchanged).
  The observed first-response improvement is descriptive: variants run sequentially, JIT and
  host load vary, and the scheduler can leave up to one slice unused rather than forcing a tiny
  final call. Do not attribute the entire difference to compilation alone or promise CG timing.
- Default flip changes generated source, so rebuilt default v05 and checked another fixed
  eight games on the same map schedule. **0/8 process failures**, first mean **574.0500 ms**,
  range **540.9463-620.8807 ms**, later max **38.115 ms**; 5/8 wins (smoke only).
  Reports: `build/analysis/warmup-final-{default4,903509641,903508669}.json`.
- Final `build/submit/Player.java`: v05 strategy plus verified warmup runtime, **64,775 characters**,
  SHA-256 `748b564a77a0d3293a925913f396a60d4598783501bc4a74a9a6624f6fadbbc5`.
  No v06, no upload. Generic `cgTiming` bypasses MainBot and still measures old generic warmup;
  its counters must not be used as evidence against the new submission hook.
- Previous work is committed as `fc568af`; this warmup change remains uncommitted for review.

### 2026-09-15 — arena checkpoint and next diagnostic

- User authorized committing the warmup fix and reported that Gold opened today; our entry
  remains in **Silver, rank 235/622**. Record as user-reported, not independently API verified.
  Follow-up: user confirms the previous build is deployed, not the new isolated warmup artifact.
  The exact deployed source hash and current placement status remain unverified.
- Clarify benchmark status: no new ordinary strength bench was run for the MainBot warmup
  fix, because that benchmark bypasses the hook. The 8+8 process comparison and final 8-game
  artifact check measure startup/protocol/timing, not statistically established playing strength.
  Last strategy screen remains interrupted eval: 51.3% vs v05, not promoted.
- Next planned step: fetch a bounded latest sample (up to 50 completed games for aabzac),
  identify arena entry and fresh-vs-existing game IDs, validate before analysis, and inspect
  fresh losses separately from the old corpus. Do not attribute rank changes to warmup or mix
  arena entries/league populations into a single improvement claim. No upload or submission.

Outcome of the bounded diagnostic:

- Committed the preceding warmup work and initial league snapshot as `1a63fd7`.
- `cgFetch -Pplayer=aabzac -Plimit=50`: arena entry **6739024**, 50 saved, none existing;
  canonical arena corpus grew from 214 to 264. API global-query rank was 363, score 18.5501;
  this is not the same rank scope as the user's Silver 235/622 snapshot.
- `cgValidate cgAnalyze -Pagent=6739024 -Pout=build/analysis/agent-6739024-2026-09-15.json`:
  **264/264 exact**, **25 wins / 25 losses**, 38 distinct opponent entries; zero own failures,
  one opponent timeout. Analysis IDs match all 50 newly fetched files; no old entry mixed in.
- Hypothesis check: old large-map sabotage starvation is not a sufficient explanation here.
  Losses: own accepted disruption on 84.7% of turns vs opponent 88.6%; own cells never earning
  51.4% vs opponent 28.3%. Heights 19-20 yield 5-4 (one timeout win), not the old 1-19 pattern;
  small, selected samples with different opponents cannot establish a version improvement.
- Captured one-turn oracle alternatives reveal both harmful builds and missed sabotage targets.
  They are not additive recoverable final points or deployable opponent predictions. Prior income
  veto / early fallback experiments remain rejected; do not silently re-enable them.
- Decision: leave strategy/submission unchanged. Next narrow diagnostic is sabotage target
  valuation on Meruem 903970829 and OldJohn 903956941, separating timer truncation from the
  long-horizon value formula and simultaneous opponent actions. Full findings, reproduction and
  exact sample IDs: `arena-analysis-2026-09-15.md`. Fresh report/docs are a new uncommitted step.

### 2026-09-15 — sabotage valuation versus truncated search

- Committed the prior arena diagnostic as `674aaa4` at user request. Next bounded step:
  explain Meruem 903970829 turns 43/46 and OldJohn 903956941 turns 19/28, without changing policy.
- Hypotheses: missed immediate inks can result from incomplete timed scans, long-horizon target
  valuation, or differences between known-state and recorded-opponent counterfactual evaluation.
- Added tools-only `cgDisrupt`: invokes the existing private EvalBot scorer/selector via reflection,
  with v05 parameters. No duplicated rules/evaluation formula or shipped diagnostic hooks. Full
  noise-free scans use accepted recorded own placements; unknown unbuilt route marks cannot be
  recovered from stdout. Timed current runs follow recorded prefixes at 486/36 ms with old generic
  warmup, not the isolated MainBot hook or an asserted reconstruction of the deployed artifact.
- Fixed one-pass sample, command `gradlew cgValidate cgDisrupt`: **264/264 exact**. Meruem t43/t46
  full selection 7/6 agrees with recorded selection. OldJohn t19/t28 full selection 27/12, whereas
  recorded outputs omit sabotage. Local timed runs choose 7/6/27/12 and all four scans complete
  (33.45/29.81/17.52/18.30 ms). No reruns to select favorable timings, no original-CG-cause claim.
- Meruem t43: ready region 5 known-state income-difference benefit +177, value 6608.1;
  region 7 needs two hits but scores 9256.9 on its larger hypothetical effect. At t46 region 6
  needs four hits yet scores 3272.6, above ready region 3 (3106.2) and region 5 (3063.5).
  Oracle region-5 benefit +189 at t46 is only +51 against WAIT: opponent action materially
  changes this choice. OldJohn ready targets are already positive without oracle information.
- Decision: full valuation can explain the Meruem preference without a scan cutoff, but does
  not establish the original OldJohn cause or prove that immediate ink wins more games. Next
  strategy experiment should isolate a discount for additional hits/delayed sabotage effect,
  preserving the closing rule, not enable the rejected early-fallback or blanket income-veto flags.
- Added four compact pre-turn fixtures and three tests: known-state targets/components and
  input preservation, RNG/noise isolation, expired versus complete scan. **112 tests pass**.
  Tests do not assert wall-clock timings. `git diff --check` clean.
- No strategy/core/frozen-source changes; no strength bench warranted for this tools-only step.
  Prepared Player.java SHA-256 remains
  `748b564a77a0d3293a925913f396a60d4598783501bc4a74a9a6624f6fadbbc5`.
  Detailed findings: `sabotage-valuation.md`; full local report:
  `build/analysis/disrupt-diagnostic.json`. New tooling and docs left uncommitted for review.

### 2026-09-15 — delayed-sabotage discount: predeclared screen

- Previous diagnostic committed as `dc4b056`. Hypothesis: the v05 target evaluator overvalues
  benefits requiring several future hits while the board changes. Test ONE neutral-default
  parameter `DISRUPT_DELAY_FACTOR=1.0`, candidate **0.65**: multiply nonterminal target value
  (including empty-track fallback) by factor^(needed-1). Ready inks and terminal close decisions
  stay unchanged. No additional allocations, opponent model or timer changes.
- Candidate source is the working-tree change on `dc4b056`; frozen v05 source/parameters untouched.
  Main screen: `current?DISRUPT_DELAY_FACTOR=0.65` vs `v05`, **300 games / 150 paired generated
  maps, seeds 13000..13149**, two workers, CG nominal budgets 486/36 ms. Maps are new relative
  to the recorded bench seed ranges. Noise 0.1 / bot SEED 1; all other parameters at v05 defaults,
  all previous experimental flags off. Full resolved configurations/hashes in JSON reports.
- Regardless of main result, common-opponent check: candidate AND frozen v05 separately against
  `v02?ALWAYS_DISRUPT=1,W_FUTURE_TRACK=1.0,W_FUTURE_OWN=0.2`, **200 games / 100 paired generated
  maps each, seeds 14000..14099**, same budgets/workers. This is an aggressive stress profile,
  not a claim that v02 represents all real opponents. Compare matched outcomes, not old numbers.
- Fixed sample, no tuning or reruns until significance. Gate unchanged: >=55%, map-grouped 95%
  lower bound >50%, zero errors; older-opponent/process checks separate. A promising screen still
  needs independent confirmation before promotion. Fail/inconclusive -> keep factor 1, no v06.
- Verification: captured-state mechanism/neutral parity and closing tests, full tests/cgValidate;
  build candidate and four strict process games (zero grace) on default maps, then restore frozen
  v05 build and repeat its four-game check. No concurrent heavy workloads during process checks.
  Save hashes/reports and leave a verified v05 artifact, never the experimental submission.

Initial results:

- Mechanism: noise-free captured Meruem targets change 7 -> 5 at t43 and 6 -> 3 at t46;
  ready-region values and both OldJohn targets remain unchanged. Neutral factor preserves full
  v05 actions on all four captured states for both players. Terminal win/loss tests pass.
- **116 tests pass, 264/264 CG replays exact.** Main fixed screen: **49.3%**, W-L-D **146-150-4**,
  300 games / 150 distinct maps; map-grouped 95% CI **38.2-60.4%**, average score difference
  **-71.0**, zero bot errors. **Gate not passed**; no confirmation or promotion on this evidence.
  Report: `build/bench/disrupt-delay065-v05-gen13000.json`.
- Source identity for the screen (raw file SHA-256): EvalBot.java
  `2b0c0121cfb8abfe5bc336ba030050194dae481bd4c863640d7631af2d7ed64d`, Params.java
  `2be7d34d95216d3dc5166083d8c2eecd80d97df5a8c6b697765ed170fd9ba430`.
  Note: frozen v02 reads the track weights but not ALWAYS_DISRUPT; that explicit flag in the
  historical stress spec is inert. Both common-opponent runs use the identical resolved spec.

Completed outcome:

- Matched aggressive-v02 check: candidate **73.25%**, **146-53-1**, CI **59.7-86.8%**;
  frozen v05 **76.5%**, **153-47-0**, CI **62.9-90.1%**, 200 games / 100 distinct maps each.
  Same opponent configuration, map hashes, seeds and sides verified. Candidate adds 15 wins,
  loses 22, leaves 163 outcomes unchanged; half-credit delta **-6.5/200 (-3.25 percentage points)**.
  This is descriptive, not a statistically established regression. Reports:
  `build/bench/disrupt-delay065-v02aggressive-gen14000.json` and
  `build/bench/v05-v02aggressive-gen14000.json`. Main and stress map hashes have zero overlap
  with each other or with prior available benchmark reports.
- Fixed four-game strict process sample, opponent v04, maps 903182120/903182383/903182464/
  903182662, alternating P0/P1. Candidate **0/4 failures**, first max **678.3579 ms**, later max
  **36.5757 ms**. Source **87,843 characters**, SHA-256
  `eeb7283d4f091b8c9719e40460add81252fc33090516f5b54e0148572ae64f2a`.
  Report: `build/analysis/disrupt-delay065-process.json`.
- Restored with `submit -PsubmitVersion=v05` and repeated four strict games: **0/4 failures**,
  first max **636.2177 ms**, later max **39.4285 ms**. One internal 38-ms warning is below the
  formal 50-ms deadline; no grace or nominal overrun. Final Player.java **64,846 characters**,
  SHA-256 `089db72f16977f26b1c49c910ff1b31735524aba630e236e3d21520451555810`.
  Its hash changed because the shared Params field and embedded config were added; frozen v05
  never reads the experimental field. Report: `build/analysis/disrupt-delay-restored-v05-process.json`.
  Both process samples happened to win 2/4: protocol/timing smoke only, not strength evidence.
- **Decision: do not promote; DISRUPT_DELAY_FACTOR stays 1.0.** Selected positions improved in
  the intended way, but the fixed main screen and older-opponent control do not support adoption.
  No tuning, independent confirmation, new version or upload. Experimental code/tests/docs remain
  uncommitted for user review. The prepared submission is frozen v05 plus isolated runtime warmup,
  not the discounted-sabotage candidate. Tests and replay validation passed before the fixed runs;
  only documentation changed afterward. `git diff --check` clean.

### 2026-09-15 — varied reference opponent and benchmark pools (plan)

- User requested roadmap items 1-2, not another current-policy change. Add local-only
  `pressure-v1`: sabotage-first, persistent target pressure and short-horizon route construction,
  no nested vulnerability evaluation. Keep its consumed Params in a named fixed snapshot.
  This is a behavioral stress opponent; resemblance to top arena play must be measured, not asserted.
- Extend paired reports with immutable map geometry, height strata, accepted build/disrupt
  frequency and game duration. Preserve overall promotion gate and map-grouped uncertainty;
  per-height slices are exploratory, not independent promotion gates.
- Add explicit development (seeds 20000..20149) and reserved confirmation (30000..30499) pool
  manifests. Confirmation requires prior development reports and rejects initialization-hash
  overlap before playing. Do not play the real confirmation pool in this implementation run.
  Add sequential opponent-suite runner without pooling opponents into one promotion statistic.
- Fixed development baseline after tests/fidelity: v05 vs pressure-v1, v03 and aggressive v02
  (track weights 1.0/0.2), 300 paired games per opponent, two workers, 486/36-ms nominal budgets.
  No policy promotion or tuning from this baseline. Preserve current/frozen policy source;
  regenerate and process-check frozen v05 only if shared submission Params text changes.

Completed outcome:

- Implemented local-only `pressure-v1`, fixed consumed parameter snapshot, factory registration
  and four policy/legality tests. No current/vNN policy source or core rule changes. Shared Params
  gains five PRESSURE_* fields unused by submission strategies; merger tests exclude the reference
  and benchmark classes. Reference source SHA-256:
  `2df3e14f7f3affbb52fdebce430c1901575b9a7d13ca671d9bce80f2225d2243`.
- Schema 3 adds geometry, accepted-action counters, A/B-oriented behavior and height slices.
  Overall map-grouped gate is unchanged. BenchPool validates full named manifests and requires
  supplied development evidence for confirmation; initialization-hash overlap fails before bots
  are created. Suite preserves per-opponent reports and hashes, rejects overwrites and skips exact
  self-spec. No global holdout-use ledger: independence is checked only against supplied reports.
- Fixed command: `gradlew benchSuite -Pbot=v05 -Ppool=bench/pools/development-v1.json
  -Popponents=bench/opponents-v1.json -Pthreads=2 -Pout=build/bench/panel-v1-v05-development`.
  Three completed 300-game match-ups, 150 distinct maps each, same seeds/hashes/sides verified:
  - pressure-v1: **207-93-0, 69.0%**, CI **57.9-80.1%**, mean score difference **+627.0**;
    mean **61.28 turns**, **267/300** early closures, accepted disruption A/B **82.7/99.9%**,
    accepted placement turns A/B **99.2/56.7%**.
  - v03: **209-91-0, 69.7%**, CI **58.6-80.8%**, mean difference **+858.3**;
    mean **80.32 turns**, **206/300** early closures, disruption **67.8/85.2%**.
  - aggressive v02 (future track/own weights 1.0/0.2): **217-83-0, 72.3%**,
    CI **61.2-83.4%**, mean difference **+582.9**; mean **81.77 turns**, **184/300** early
    closures, disruption **67.1/88.7%**. No inert ALWAYS_DISRUPT override in this panel spec.
  All match-ups have zero bot errors. Early closures are joint match outcomes, not causal
  attribution to one bot. The third run had in-process max times **260/243 ms** and **5/7**
  slow moves; nominal in-process budgets do not enforce external deadlines. No strength promotion.
- All height counts sum to their whole runs. Height-20 results, on the same 22 maps / 44 games:
  v05 wins **24** vs pressure, **36** vs v03, **24** vs aggressive v02. Small exploratory slices,
  not proof of a height-specific effect. Suite index is complete with **150**, not 450,
  distinct map hashes. Zero overlap with all **31** previously available benchmark reports.
  The real confirmation-v1 pool remains **unplayed**; tests use separate temporary manifests.
- Decision: retain pressure-v1 as a fixed aggressive stress reference, not a top-player clone.
  Its high sabotage frequency and shorter matches add coverage, but low construction frequency
  leaves calibration/additional styles open. Future candidates should use matched panel reports,
  then a predeclared independent confirmation; do not average opponents into a promotion score.
- Final checks: **127 tests passed; 264/264 CG replays exact**. Rebuilt with
  `submit -PsubmitVersion=v05`: **65,201/100,000 characters**, source SHA-256
  `d03dd610ca210922436fdefb3c3f4f18f8815d22e9d06e85a6466461f2817d29`, compiled-classes SHA-256
  `e03c16d0a4f01eba37e4b85860f5bb01577dd7b6c7940a07c30cd9d55f44525b`.
  Source text changed through shared unused Params fields; frozen v05 policy is unchanged.
- Fixed strict process check (`procCheck -Pbot=v04 -Pgames=4`, zero grace) **failed 1/4 games**.
  First response max **627.9647 ms**. Failure: game 3, map **903182662**, P1, turn **41**:
  `sendMs=66.6288`, `responseMs=null`, `consumerMs=66.654`, 50-ms deadline. The measured stall
  is in input preparation/write/flush, not an observed 66-ms bot response; scheduling/transport
  versus other causes are unresolved. Successful later responses max **37.7679 ms**.
  Report retained at `build/analysis/panel-v1-restored-v05-process.json`. No retry-to-green,
  timing fix, upload or timing-readiness claim. Add this diagnostic follow-up to PLAN.
- Documentation and completed roadmap items updated. Implementation remains uncommitted for
  user review. No new current-policy experiment was started and no reference was retuned.

### 2026-09-15 — descriptive bot catalog and retained experiment recipes

- User accepted descriptive version names plus a catalog separating historical versions,
  independent local references and unpromoted experiments. Implementation is local tooling:
  preserve vNN ids/classes, current policy, shared Params/core, default panel and promotion rule.
- Added catalog entries for v00 Autoplace, v01 Greedy Routes, v02 Route Evaluation, v03 Risk Aware,
  v04 Network Extension, v05 Disrupt Reserve, pressure-v1 Sabotage First, and mutable current/main.
  `gradlew botCatalog [-Pbot=<spec>]` lists entries or shows a recipe, full parameters and identity.
- Retained six named configurations: exp-income-guard, exp-build-direction, exp-early-disrupt,
  exp-reply-rerank (K4/R2), exp-interruptible-vuln, exp-disrupt-delay065. No per-flag source copies.
  They pin all 46 parameters and 17 current/core dependency class hashes from source revision
  `218457a9b6ce72a9e06c5b6a9102112a6c7e25e2`; compiled-engine fingerprint
  `05b2595d987156fef7e49baec8ad84900e0673d6cf634fcfa426bb817803343a`.
  Classpath drift or an incomplete snapshot fails before playing; explicit overrides are labelled.
  Compiler/debug changes may also invalidate the conservative guard. Old ids must not be retargeted.
- Historical experiment results remain historical: this shared catalog snapshot is not an archive
  of every earlier executable. Retaining an experiment does not promote it or put it in the default
  opponent panel. Descriptions, evidence links and restoration/revision limitations are documented
  in `bot_catalog.md`. No automatic git checkout, JAR archive or new strategy was added.
- Benchmark/suite reports retain machine ids and add label/status/provenance metadata. LocalRunner
  keeps exact input specs in meta.bots and adds optional botDetails. The visualizer prefers recorded
  labels, looks up old local ids when the catalog is available, and never interprets CG nicknames
  as local strategies. Unknown names / unavailable catalog preserve the original text.
- Verification: **134 Java tests passed, 264/264 CG replays exact**, plus **9 Node label checks**.
  New tests cover preset/config isolation, missing fields, implementation drift, both-player
  first-move equivalence for all six recipes, explicit overrides, report ids, local-vs-CG identity
  and submission exclusion. Browser testing caught a Windows Java/console encoding mismatch in
  the initial separator; final labels use ASCII separators and need no global encoding change.
- CLI wiring smoke: exp-reply-rerank vs v05, **4 games / 2 paired maps**, seeds 40000..40001,
  two workers, nominal 486/36 ms, **zero errors**, max times 31/26 ms. Report:
  `build/bench/catalog-reply-smoke.json`. W-L-D happened to be 4-0-0, CI 4.0-100.0%:
  **wiring smoke only, not strength evidence or promotion**. Later label-only corrections do not
  change the report's stored original labels. Confirmation-v1 was not played.
- Browser checks: new local v05 vs exp-income-guard replay on seed 40100, old local replay
  `game_20260913_021834_-176203007668178900.json`, CG arena 903584094; labels and last-turn
  navigation work, CG still shows aabzac/RTJ. Three local visual-QA runs on the same seed were
  produced while fixing the label encoding (not a benchmark); final checked file is
  `replays/local/game_20260915_215747_40100.json`.
- `submit -PsubmitVersion=v05` compile-checks and reproduces the **same** 65,201-character source,
  SHA-256 `d03dd610ca210922436fdefb3c3f4f18f8815d22e9d06e85a6466461f2817d29`.
  No shipped source/config or packaging changes, no new process timing claim. The preceding
  1/4 strict-process failure remains unresolved; no retry-to-green or arena upload.
- Decision: catalog ready for user review, no policy promotion or parameter retuning. Changes
  remain uncommitted pending an explicit request.

### 2026-09-15 — compact shared-skill update and catalog commit

- User requested the agreed shared lessons and a commit before the next step. Added two short
  paragraphs to the personal codingame-bot-contest skill: stable ids vs readable labels and
  retained experiments vs promotion (architecture.md); a small behaviorally varied opponent panel
  with per-opponent results (benchmarking.md). Main SKILL.md and invocation metadata unchanged;
  no project-specific bytecode mechanism, flags, seed ranges or result numbers were generalized.
- Personal skill files live under `C:/Users/garus/.agents/skills/codingame-bot-contest`, outside
  this repository and outside any Git repository. They are updated locally, not part of the
  project commit. Reviewed both additions and existing reference routing. quick_validate.py
  could not start because PyYAML is absent in both available Python runtimes; no dependency
  installation or unrelated configuration change was made. Existing frontmatter is untouched.
- Commit the completed catalog/tooling with its prior 134-test, 9-label-check and 264-replay
  evidence. Next work is the recorded strict-process timeout diagnosis, not a strategy change.

### 2026-09-15 — process input timeout phase diagnosis (plan)

- Catalog committed as 91c77f7. Original failure has sendMs=66.6288 but no reader response:
  that aggregate combines Protocol.turnBlock, Writer.write and flush. The old report cannot
  distinguish serialization CPU, blocked pipe or descheduling/GC. Do not infer slow bot search.
- Add optional process-side IO telemetry: preparation/write/flush wall times, input size,
  caller-thread CPU and host-JVM GC-time deltas. Keep original absolute deadline, zero grace,
  timestamped reader decision and default instrumentation-off path. Capture failed input only
  when diagnostics are enabled. The counters identify intervals, not a universal causal oracle.
- After deterministic tests, one fixed eight-game run on the first eight sorted canonical CG
  maps, opponent v04, alternating sides, frozen v05 artifact, 1000/50-ms deadlines, zero grace.
  This includes the original four-map check, including P1 on 903182662. Run serially without
  other heavy tasks. Retain all outcomes; no repeat-until-green or strategy/budget changes.
  Report: build/analysis/process-input-diagnostic8.json. No strength bench or confirmation pool.

### 2026-09-15 — process input diagnostics implemented; old stall not reproduced

- Added opt-in `procCheck -PprocessDiagnostics=true`: separate input preparation/write/flush,
  input character count, caller-thread CPU and host JVM GC-time deltas; exact turn input retained
  on non-OK statuses. Initialization of management counters is outside the turn deadline.
  Default is off, reader timestamps still decide success, all input phases consume the same
  absolute allowance, and explicit diagnostic runs refuse an existing report path.
- Added six tests: phase split, timed-out input retention/no extra allowance, reader boundary
  with diagnostics, unchanged clock-read sequence when off, explicit flag validation and report
  overwrite refusal. **140 tests passed; 264/264 CG replays exact**. No core, Params, strategy,
  frozen package or submission runtime changes; the experiment classpath guards still pass.
- Ran exactly the predeclared eight games once, serially, on the first eight CG maps in sorted
  order, frozen v05 vs v04, alternating P0/P1, seeds 7000..7007, strict 1000/50 ms, grace 0,
  Java 17.0.12. **0/8 problem games, 0 nominal overruns**, 497 measured turns (489 after first).
  Observed W-L-D 5-3-0 is not strength evidence. No confirmation-pool games or arena upload.
  Report `build/analysis/process-input-diagnostic8.json`, SHA-256
  `78a831017827d060570ee90607e0809533a01fbdb8c059371d44d906187b7e95`.
- Exact Player source and classes hashes match the preceding failed run:
  source `d03dd610ca210922436fdefb3c3f4f18f8815d22e9d06e85a6466461f2817d29`,
  classes `e03c16d0a4f01eba37e4b85860f5bb01577dd7b6c7940a07c30cd9d55f44525b`.
  First response max **708.4809 ms**, later response max **36.808901 ms**.
  Later input send max **4.348399 ms** on 903183758/P1/turn54, of which flush **4.287699 ms**.
  Across later turns the phase maxima were prepare **1.551 ms**, write **0.5812 ms**,
  flush **4.287699 ms** (separate maxima, not one turn).
- Original map/side/turn 903182662/P1/41: send **0.3903 ms**, response **33.891699 ms**;
  prepare/write/flush **0.2083/0.1595/0.0225 ms**, input 10,941 chars. This is a new timed game,
  not reproduction of the original exact input or JVM history. The old input was not saved.
- Long first-turn send intervals were localized to flush (max **162.109101 ms**), consistent
  with startup/pipe waiting but not proof of the old later-turn cause. No host GC-time increments
  occurred in the measured windows. Host CPU deltas were only **0/15.625 ms** (37 nonzero calls),
  too coarse for short phases; zero is not evidence of zero CPU work. Child GC and scheduling
  remain unobserved; instrumentation itself adds overhead.
- Decision: diagnostic tooling complete, **original 66.6288-ms stall remains unexplained**.
  Retain both reports. Do not treat this instrumented pass as a timing fix, uninstrumented
  readiness result or permission to relax deadlines. No further rerun in this step. If it recurs,
  use captured phase/input evidence to choose a targeted host/child scheduling or GC investigation.
  Documented the command and counter limitations; changes remain uncommitted for user review.

### 2026-09-15 — narrow reply rerank versus pressure: predeclared development follow-up

- Committed process-input diagnostics as `3c46599` at the user's request. Next bounded step
  uses the new independent pressure reference to revisit the retained K2/R1 reply model, not
  another timing retry or a change to live strategy. Prior K2/R1 result was 55.7% vs v05 with
  CI 44.6-66.8%: inconclusive, selected after wider beams failed, not promotion evidence.
- Hypothesis: the affordable K2/R1 search might help against a sabotage-first policy as well
  as a related v05 opponent. Alternatively its same-family reply prediction may fail against
  pressure-v1. Keep exactly this configuration; no K/R/weight tuning from the new outcomes.
- Candidate spec `exp-reply-rerank?BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1` uses the catalog's
  pinned engine-v1 (source 218457a), full 46-parameter snapshot and explicit overrides. Only
  USE_REPLY_RERANK=true and K2/R1 differ from the neutral current configuration; other failed
  experiments and USE_OPP_MODEL stay off. Frozen v05 and pressure-v1 retain their snapshots.
  Current, core, Params, experiment identities and Player.java will not be edited.
- Run test/cgValidate and verify the candidate configuration/engine guard first. Then exactly
  three sequential 300-game match-ups, 150 paired generated maps per match-up, both sides,
  development-v1 seeds 20000..20149, two workers, nominal CG 486/36-ms search budgets:
  (1) candidate vs v05; (2) v05 vs pressure-v1; (3) candidate vs pressure-v1. No concurrent
  CPU-heavy work. Fresh report paths under build/bench/narrow-reply-pressure-v1/ preserve all
  outcomes. Run all three even if the first is inconclusive; stop on bot errors, not win rate.
- This is exploratory development on an already-inspected pool, not independent confirmation.
  New v05/pressure control is required for the common-opponent comparison; historical 69.0%
  is context only. Match exact map hashes, seeds, sides, opponent config and timing. Report
  direct-v05 and pressure results separately, including timing and descriptive height slices.
- Decision fixed before play: only a direct-v05 result >=55%, map-grouped 95% lower bound >50%,
  zero bot errors AND no point-estimate loss vs the fresh v05/pressure control can justify
  proposing a separate confirmation. The pressure point check is a screening heuristic, not
  proof of noninferiority. Otherwise keep the feature off. Do not extend samples, consume
  confirmation-v1, generate a candidate Player, freeze v06 or upload in this step. Passing this
  screen would still require independent confirmation, an older opponent and process checks.

Completed outcome (fixed sample, no retuning):

- Preflight passed: 140-test suite up to date, 264/264 CG replays exact; catalog verified
  engine SHA-256 `05b2595d987156fef7e49baec8ad84900e0673d6cf634fcfa426bb817803343a` and all
  intended effective flags. Working tree had only this predeclared log after commit 3c46599.
- Candidate vs v05 completed: **172-128-0, 57.3%**, 300 games / 150 distinct paired maps,
  map-grouped CI **46.2-68.4%**, average score diff **+104.6**, **67.8** turns, **171.0 s**.
  P0/P1 wins both 86/150. No bot errors or nominal slow moves; search mean/max A **9.24/35 ms**,
  B **6.32/34 ms**. Accepted sabotage A/B **89.4/89.8%**, 263/300 early closures.
  Statistical gate not passed; this is another development observation, not confirmation.
  Report: `build/bench/narrow-reply-pressure-v1/candidate-v05.json`.
- Fresh control v05 vs pressure-v1 completed: **208-92-0, 69.3%**, 300 games / 150 distinct
  paired maps, CI **58.2-80.4%**, average score diff **+529.5**, **59.8** turns, **116.6 s**.
  No bot errors or nominal slow moves; search mean/max A **7.58/42 ms**, B **4.15/43 ms**.
  Accepted sabotage A/B **84.7/99.9%**, 276/300 early closures. The printed PASS is v05's
  comparison with a local reference, not a candidate promotion. Report:
  `build/bench/narrow-reply-pressure-v1/control-pressure.json`.
- Candidate vs pressure-v1 completed: **198-102-0, 66.0%**, 300 games / 150 distinct paired
  maps, CI **54.9-77.1%**, average score diff **+633.2**, **60.4** turns, **143.8 s**.
  No bot errors or nominal slow moves; search mean/max A **10.46/37 ms**, B **4.04/50 ms**.
  Accepted sabotage A/B **84.0/99.9%**, 277/300 early closures. Again, the printed PASS against
  pressure is not the required direct-v05 gate. Report:
  `build/bench/narrow-reply-pressure-v1/candidate-pressure.json`.
- Verified all three reports use the same 150 initialization hashes, indices, seeds, map geometry,
  sides, pool SHA-256, Java 17.0.12 and 486/36-ms/two-worker profile. Candidate configurations
  match between runs; v05's and pressure's full configurations match their respective controls.
  Against pressure, joining by map hash and A's side gives **46 added wins / 56 lost wins /
  198 unchanged games**, net **-3.33 percentage points**. Pair totals improve on **26** maps,
  worsen on **40**, tie on **84**. These are matched descriptive outcomes, not a causal estimate
  free of time-limited scheduling/run-order effects or proof of statistical inferiority.
- Descriptive height split versus pressure (candidate/control wins): h14 **17/19** of 24 games,
  h15 **40/35** of 48, h16 **25/30** of 46, h17 **30/31** of 42, h18 **31/41** of 54,
  h19 **27/28** of 42, h20 **28/24** of 44. Slices are small and inconsistent; do not introduce
  a height-specific switch from this result. P0 net +3 wins, P1 net -13, also exploratory.
- Mean score difference against pressure improves (+633.2 vs +529.5) while win rate falls;
  larger score margins in some games do not compensate for fewer wins. Search averages are
  higher for the candidate (10.46 vs 7.58 ms in the two pressure match-ups), measured on
  different evolving trajectories, not as a matched-position CPU microbenchmark.
- Retained report SHA-256 values (candidate-v05 / control-pressure / candidate-pressure):
  `dfcf276222ccff64d5bd2316787aa6129ee06852171b812c6c88bed36359dceb`,
  `6e270be72cf50edc96040755e5aadcce020819af4b9b2f8492f83c6c66362708`,
  `8b01b8dfe29e69f7a9fb60bf99245c55ed1561ebdd41b51220695851a38102b6`.
- Decision: **do not promote**. Both predeclared conditions fail: direct-v05 CI includes 50%,
  and pressure win rate is below its fresh control. No additional variants, sample extension,
  confirmation-pool games, older-opponent promotion run, candidate submission or process rerun.
  Current stays v05, USE_REPLY_RERANK=false; retained K4/R2 id and explicit K2/R1 override
  remain available, without retargeting any catalog entry. Player.java/source hash is unchanged.
  The original process-input stall remains open; no new process-readiness claim.
- Next hypothesis, not established causality: the same-family predicted response may rank
  affordable prefixes poorly against a sabotage-first policy. Before another beam-size change,
  compare prediction and candidate-ranking quality on shared captured states, separating known
  state, predicted response and recorded-action oracle diagnostics. This is a future step,
  not extra games/tuning added to the completed 900-game screen. Findings documented in BOTS;
  this turn's documentation remains uncommitted for review.

### 2026-09-15 — shared-state narrow reply audit (predeclared)

- Prior 900-game screen committed as 6aad987. Tools-only diagnosis; preserve the pinned engine,
  Params/current/core/frozen sources, Player.java, default flags and promotion policy.
- Capture exactly 24 new local games: generated seeds 20000..20005, both sides, each of v05
  and the retained K2/R1 candidate vs pressure-v1. Nominal CG 486/36-ms budgets, serial games.
  These are development maps selected by seed order, not by outcome. Save full action replays
  once, before analysis in a separate JVM; they are not recreations of earlier bench trajectories.
- Reconstruct shared pre-turn states at t10/20/30/40/50 through core; retain early-ended games
  and report unavailable turns, rather than substituting later seeds. At most 120 positions.
- Probe the existing private first-build/reranking methods via tools-only reflection, no policy
  copies or shipped hooks. Fresh diagnostic instances use NOISE=0 (explicit diagnostic override),
  K2/R1, other failed flags off. A zero-budget play primes existing preparation code; first-build
  probe time excludes this priming. Ample 60-second allowance is a completion ceiling, not sleep.
  Check complete probe choice against the emitted first prefix from full play.
- On the SAME ample-time candidate set, compare unchanged replyAwareValue under no opponent
  action (known-state), predicted reply and the captured pressure action (oracle). Resolve/check
  effects through shared core. Prediction metrics: accepted placement-set overlap/exact agreement,
  accepted disruption target agreement, and whether replacing prediction changes the ranking.
  Also compare oracle heuristic ranking with immediate score-difference increments from core;
  a conflict can be an investment tradeoff, not proof the long-horizon evaluator is wrong.
- Compare fresh full play at 36 ms and ample time on each identical state, alternating order
  by sample index. Initialization is outside play; no historical RNG/JIT/CPU reconstruction.
  Record complete emitted actions and prefix differences, not just elapsed time. Budget-sensitive
  choices are not proof of the original benchmark loss or timeout cause. Analysis overhead/JIT
  differs from real games. Do not infer completion from elapsed time alone.
- Save corpus plus all per-state/candidate evidence under build/analysis/reply-study-v1; refuse
  overwrites and permit reanalysis of the saved corpus without fresh games. Add deterministic
  isolation/core-simulation/report tests; run full tests and cgValidate. This is diagnosis, not
  another strength screen: no confirmation pool, parameter sweep, candidate submission or upload.
  Summarize findings/limitations and the next justified hypothesis, then commit at user request.

### 2026-09-15 — shared-state narrow reply audit (completed)

- Added tools-only `replyStudy` capture/analyze and `ReplyProbe`, using the retained policy's
  actual private methods rather than copying evaluation/pathing. No core, current, Params,
  frozen-version or submission-source changes. The catalog id still denotes K4/R2; the audit
  explicitly requests K2/R1. Recorded opponent actions enter only after prediction is fixed.
- Ran the predeclared capture and analysis once each: **24/24** games without bot errors,
  **110** pre-turn observations, **10** unavailable after early end. There are **108** distinct
  state/perspective identities; correlated turns and repeated states are not independent trials.
  Every replay was reconstructed and checked through core before probing. Capture manifest and
  per-game hashes preserve the evidence; incomplete, duplicate or wrong-cohort inputs and output
  overwrites are rejected. Later validation hardening did not change measurement semantics.
- First-build candidates exist in **81 observations / 79 unique states** (two choices in 77).
  All 81 ample-time probes match the first prefix emitted by full play. Remaining 29 observations
  do not enter prediction-agreement denominators. Source-v05 contributes 53 observations,
  source-K2/R1 57; full per-policy and per-state evidence is retained.
- Pressure's nonempty build is predicted exactly in **12/55 (21.8%)** observations; including
  six empty/empty matches gives **18/81**. Cell intersection **48**, predicted **192**, actual
  **144**: precision **25.0%**, recall **33.3%**. Predicted construction occurs in **20/26**
  observed no-build cases. Sabotage target matches in **61/81 (75.3%)** eligible observations.
- Substituting the recorded response strictly improves selected value under the SAME heuristic
  and two-candidate set in **4 observations / 3 unique states**. One example repeats across
  source policies; a separate argmax change is only a tie and is excluded. Thus poor action
  prediction alone does not explain ranking quality in this sample. These are limited-heuristic
  counterfactuals, not recoverable game points or an oracle-policy win-rate estimate.
- Oracle projected value and immediate score increment prefer different options in **11**
  observations. For seed 20000/P0/t30, projected values are **1382.67 vs 963.33**, immediate
  increments **0 vs +51**. This can be an investment tradeoff, not evaluator failure; do not
  revive a hard immediate-income veto from this diagnostic.
- Fresh full play at nominal 36 ms versus ample time changes **0/81 first prefixes**. Complete
  actions differ in **5/110** observations, only in later/leftover construction; first prefix
  and own sabotage match in each. Nominal mean/max **12.04/31.25 ms**, ample **16.65/112.52 ms**.
  These alternating-order local measurements do not reconstruct historical RNG/JIT/CPU state,
  establish the cause of earlier losses, or close the original process-input stall.
- Added three compact checked-in fixtures from retained data: seed 20002/P0/t10 placement
  forecast, seed 20003/P1/t10 construction-plus-sabotage mismatch, seed 20000/P0/t30 projection
  tradeoff. Tests cover oracle isolation, actual-play prefix agreement, optimized-simulation/core
  agreement, replay reconstruction, invalid evidence, denominator handling and rejected/tied
  values. **149 tests pass; cgValidate 264/264 exact; git diff --check passes.**
- Evidence: `build/analysis/reply-study-v1/report.json`, SHA-256
  `6c407ef3dc420b0cb85257b5be6340b65a302f1a45ca10fd0d313fc3792be7bd`;
  capture manifest SHA-256
  `652b2c28ca30e62ea974d58d740fb9210f8f76d213ed905364102d059f196d0b`.
  Deduplication/fixture extraction used these saved files without rerunning games or measurements.
  Method, limits and findings are in `docs/reply-study.md`, with BOTS/PLAN updated.
- Decision: **keep v05 and USE_REPLY_RERANK=false**, no v06, parameter tuning, new strength
  bench, confirmation games, submission regeneration or upload. Retained Player.java SHA-256
  remains `d03dd610ca210922436fdefb3c3f4f18f8815d22e9d06e85a6466461f2817d29`.
  Next hypothesis: inspect the joint effect of the first build and OUR later sabotage on shared
  states, since current reranking omits that interaction. This is a candidate explanation, not
  established causality or authorization to promote an untested policy. Commit this completed
  diagnosis at the user's explicit request.

### 2026-09-15 — joint first-build / own-sabotage audit (predeclared)

- Follow-up to 994d290; tools-only. Reuse the SAME hashed 24-game capture and 110 recorded
  positions/report. No new games, full-play timing reruns, tuning, promotion or submission change.
  Reconstruct/validate all states; regenerate deterministic NOISE=0 candidates and check routes,
  prefixes, predictions and no-own-hit values against the retained report before interpreting.
- For each first-build candidate, select our sabotage with the existing complete known-state
  chooser after that affordable prefix, preserving full planned-route marks. Ignore leftover
  construction explicitly. Select a common pre-build target separately. Neither chooser sees
  predicted or recorded opponent actions. Reuse the pinned policy and full parameters.
- Compare no own hit, common pre-build hit, and per-prefix hit under known WAIT / predicted
  reply / recorded-action oracle. Keep the original no-own-action baseline fixed within each
  opponent scenario. Reuse the actual replyAwareValue formula by staging only our instability
  increment before its simulation (no early ink); check every joint result against Rules.resolve.
  This is a diagnostic extension of its projection, not addition of a differently scaled sabotage
  score. Terminal outcomes and routes inked this turn are reported separately; projected value
  can be misleading at game end and can discount a shared sabotage benefit by route duration.
- Report strict ranking changes (not tie breaks), whether targets depend on the route, physical
  prefix losses / route invalidation, and oracle value of the prediction-selected joint action
  versus the old route plus its own known-state target. Do NOT select own targets from the oracle
  or sum counterfactual deltas as match gains. Count repeated states and separate source policies.
- Preserve new evidence under build/analysis/reply-study-v1/joint-report.json, refusing overwrite.
  Add isolation/core-equivalence/regression tests, run tests and cgValidate. Record a bounded
  next hypothesis from the result; current remains v05 and the reply experiment remains off.

### 2026-09-15 — joint first-build / own-sabotage audit (completed)

- Added tools-only `jointReplyStudy`; factored capture validation and deterministic candidate
  preparation from existing tools. No copied scoring/pathing formula or changes to current,
  Params, core, frozen versions, catalog identities or submission code. The shared private
  projection is reused with one staged own instability increment; each joint successor must
  equal full Rules.resolve, including same-cell clashes and simultaneous fourth hits.
- Analyzed the original sample once: **110 observations / 108 distinct state-perspectives**,
  **81 / 79** with build candidates. All regenerated candidates, predictions and original
  no-own-hit values match the saved report. No new capture, games or full-play timing runs.
  Known-state own targets are chosen before any oracle evaluation; sample mapping, recorded
  actions, manifest/engine/full parameters are checked. Existing evidence is never overwritten.
- Targets depend on the prefix in **8/81** observations, differ from the common pre-build
  target in at least one choice in **13/81**. For the old selected prefix, the target agrees
  with saved ample full play **81/81**, despite excluding leftover construction.
- Strict ranking changes (fixed target vs no hit / adapted target vs no hit / adapted vs fixed):
  known WAIT **1/3/2**, predicted **2/3/1**, oracle **2/3/1**. Prediction-selected adapted pair
  vs old prefix + its own adapted target: oracle projection **3 better / 0 worse / 78 equal**,
  or **3/0/76** distinct states. One change is from source-v05, two from source-K2/R1, across
  three different maps. Counts remain correlated development observations, not strength trials.
- Preserved all three changed cases as seed/pre-turn-history/state-hash regression fixtures,
  with separately labelled opponent oracle and expected fixed/adapted values:
  seed 20000/P1/t40 -> prefix 1/target45, projected 1441.46 vs 521.5, immediate +40;
  seed 20004/P1/t10 -> prefix 1/target9, projected 583.1 vs 324.8, immediate 0;
  seed 20005/P0/t20 -> prefix 0/target29, projected 1204 vs 802.2, immediate -11.
  These effects are NOT summed as match points. No selected joint action is terminal; the
  one existing oracle-inked prefix remains (single candidate), so no claim of repairing it.
- Important counterexample to assigning one target per route: on seed 20000/P1/t40, the
  already evaluated original prefix + common target45 scores **2120**, better than either
  adapted pair, with +47 immediate difference over the old pair. Thus a different sabotage
  choice, not necessarily a different route, may be preferable under the SAME projection.
  Our standalone target chooser and joint evaluator do not optimize the same objective.
- Limitations: projected completion/risk formula unchanged, delayed hit benefits incompletely
  represented, terminal projections can mislead, and a shared sabotage benefit can be discounted
  by route duration. Prefix-target search is not exhaustive; no future own target decisions or
  leftover-paint policy are modeled. Oracle never chooses targets and cannot justify promotion.
- Report `build/analysis/reply-study-v1/joint-report.json`, SHA-256
  `b3fe234a7b462814299128a26af63cd025e4b74884202521ef2ea1ce8c4198b5`;
  prior report and capture hashes remain respectively
  `6c407ef3dc420b0cb85257b5be6340b65a302f1a45ca10fd0d313fc3792be7bd` and
  `652b2c28ca30e62ea974d58d740fb9210f8f76d213ed905364102d059f196d0b`.
  Post-analysis fixture extraction and invalid-value hardening used saved evidence, no sample rerun.
- Decision: **v05 unchanged; USE_REPLY_RERANK=false; no v06, bench, artifact rebuild or upload**.
  Next bounded experiment: two prefixes crossed with deduplicated known-state targets (WAIT,
  pre-build target, per-prefix targets), preserving the old pair and fallback. Verify mechanism
  and cost, then use a default-off flag and normal fixed paired bench before any promotion.
  See joint-reply-study.md; BOTS and PLAN updated. Changes remain uncommitted for user review.
- Final verification: **156 tests pass; cgValidate 264/264 exact; git diff --check clean**.
  Player.java SHA-256 remains
  `d03dd610ca210922436fdefb3c3f4f18f8815d22e9d06e85a6466461f2817d29`.
  Seven new tests cover saved examples, oracle isolation, both-sided simultaneous ink,
  candidate/sample drift, ties/invalid values and non-overwriting output. No process rerun;
  the earlier input-send stall remains unresolved and is not explained by this audit.

### 2026-09-15 — bounded joint-action screen (predeclared)

- Commit prior diagnosis as d4670dd. Preserve the original experiment manifest/ids using ONE
  local archive of its exact Params/EvalBot class bytes, child-loaded against the still-verified
  shared Bot/core/Timer. Do not retarget ids or copy a policy per flag. Archived configuration
  remains its original 46 fields; new unsupported flags are rejected. Tools inspect the actual
  retained class. Archive is excluded from submissions and original fingerprints stay identical.
- Add default-off USE_JOINT_ACTION to current, requiring K2/R1 reply reranking and sabotage,
  with other earlier experimental flags off. Compare two affordable prefixes crossed with at
  most four distinct known-state targets: WAIT, pre-build target, both prefix-specific targets.
  Targets are deterministic, include full planned-route marks, and never use recorded replies.
- Preserve original K2/R1 prefix and its known-state sabotage as tie/failure reference. Compare
  only fully evaluated pairs with identical reply coverage on the existing build-phase clock;
  incomplete target preparation or baseline evaluation returns to normal play. A complete,
  strictly better pair can replace it. Never extend the timer or change the 6-ms reserve.
  When a new pair is committed, emit exactly its prefix/target (no unmodeled leftover extension);
  otherwise retain normal leftover construction and sabotage. This behavioral distinction is
  part of the candidate, not a claim to isolate evaluation cost alone.
- Reuse the existing projection including immediate score/completion risk, not an extra weighted
  sabotage score. Resolve both hits through core; retain documented projection/terminal limits.
  Test saved three-state mechanisms, disabled-policy equivalence to the archived engine, equal
  coverage/fallbacks and legal emitted actions. Full tests and cgValidate before strength screen.
- Fixed exploratory screen: current?USE_REPLY_RERANK=1,BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1,
  USE_JOINT_ACTION=1 vs frozen v05, 300 games / 150 paired generated seeds 21000..21149,
  2 workers, CG 486/36-ms search profile, unchanged NOISE=0.1 and all other defaults. No sample
  extension/tuning. Gate remains >=55% and map-grouped 95% lower bound >50%; no promotion here.
  Independent-style follow-up: same candidate and fresh v05 controls vs pressure-v1 on these
  SAME maps, 300 games each, regardless of screen result, for transfer evidence (not promotion).
- Before benches, run one serial recorded-state cost check on the saved 110-position corpus:
  alternate control/candidate call order, fresh NOISE=0 instances at 36 ms, record all actions,
  decisions and elapsed times. This is not a reconstruction of old timeouts or CG CPU time.
- Build candidate and run one strict 4-game process sample vs v04 on the existing CG pool,
  then restore frozen-v05 submission and run the same fixed 4-game process sample. Preserve
  all reports, including failures; no retry-to-green or upload. Source-size and executable
  identity checked for each artifact. Record results and next step; unpromoted flags stay off.

### 2026-09-15 — bounded joint-action screen (implementation and checks)

- Implemented USE_JOINT_ACTION=false in Params/current. At most two prefixes x four distinct
  targets; old-pair tie/fallback, whole-reply coverage, build-clock cutoff, no per-node allocations.
  Emit only the evaluated prefix/target on a strict improvement; otherwise normal play remains.
  Same core resolution and old projection; no new scoring rules or terminal utility formula.
- Preserved exact v1 Params/EvalBot class bytes in one local archive, original manifest unchanged.
  Child loading isolates only those two classes; shared Bot/core/Timer are hash-verified and
  called directly, without per-move reflection/serialization. Old specs still report 46 fields
  and reject new flags. Tools now reflect the actual retained instance; original fixtures pass.
  Archive SHA-256 `79fc2d11d6aa403edab9ff9475cb3bb6741efd62e46a566566f14e9c659bca95`.
  Retained engine fingerprint remains
  `05b2595d987156fef7e49baec8ad84900e0673d6cf634fcfa426bb817803343a`.
- Captured cross-product regressions select prefix0/target45, prefix0/WAIT, prefix0/target29.
  Current flag-off choices match retained code, including sequential known-state play. Tests
  cover both-hit/core agreement, original projected values, expired-clock fallback, cleared
  route marks/restored noise, exact emitted prefixes, unsupported combos and no disabled buffers.
  Full tests before screen: **160 pass; cgValidate 264/264 exact**. Extra archive/merger assertions
  were added during the bench and are checked in the final verification, not run concurrently.
- Single serial cost run: **110 positions**, **72** complete joint scans, **5** committed pairs,
  **8** different full actions. Control mean/max **12.8077/31.8443 ms**, candidate
  **13.4978/31.2982 ms**, zero calls above 36 ms. NOISE=0, fresh instances, alternating order;
  this measures neither original-loss causality nor CG process deadlines. Report
  `build/analysis/joint-action-v1/cost.json`, SHA-256
  `377c6b9d37d67cf283e0ffb82910e4b82dd2ddb504a60e57d4faec512d4285da`.
- Measured strategy source hashes: EvalBot
  `e69497ff356729fc9e3a477dee3140274d791f7c22234f7248c2c2533357b84f`, Params
  `8df5e35ec46a826303e0ec48ee6a3e54c8ae8ebc4f4c0dcf24207ea2e3bf2416`.
  Same source throughout all three matchups; cost JSON preserves compiled class hashes too.

### 2026-09-15 — bounded joint-action screen (outcome)

- Completed exactly the three predeclared 300-game matchups / 150 paired maps each, generated
  seeds 21000..21149, 2 workers, Java 17.0.12, CG 486/36 ms, full resolved parameters retained:
  candidate-v05 **130-169-1, 43.5%, map-grouped CI 32.4-54.6%**, mean score diff -319.6;
  v05-pressure **195-105-0, 65.0%, CI 53.9-76.1%**, mean diff +573.0;
  candidate-pressure **196-104-0, 65.3%, CI 54.2-76.4%**, mean diff +489.9.
  Gate failed against v05; a PASS printed for beating pressure is not the promotion comparison.
- Verified same 150 map hashes, seeds and sides across all runs, exact full candidate/v05/pressure
  configs in their corresponding controls. Matched pressure outcomes: **57 added / 56 lost wins /
  187 unchanged**, net +0.33 pp, descriptive rather than proof of equivalence/improvement.
  No bot errors in all 900 games. In-process >50-ms slow counters A/B: 0/0, 2/0, 1/0;
  search means 9.73/6.62, 6.52/3.60, 11.06/3.99 ms respectively. These are not process deadlines.
  Candidate disruption versus pressure is 79.8% vs v05's 84.2%; different trajectories, not
  causal evidence that skipping sabotage explains the direct-v05 loss.
- Report SHA-256, candidate-v05 / control-pressure / candidate-pressure:
  `e50802f80ef102f9fecc08e7c2cd86dbf2a48986bd1183a8f7105c722d183705`,
  `c697d5919016a22174742ed4fd48a1fb38d17895ba92929a532e708a90fc0ef8`,
  `59770ff3104df22786361d77d113bd528ee29ce7d8e5513e960f4d4f93d94ead`.
  Reports in `build/bench/joint-action-v1/`; no extensions, variants or confirmation games.
- Final **162 tests pass, cgValidate 264/264 exact**. Candidate `submit` compiles at **93,881**
  characters; strict predeclared four-game process check vs v04 has **0 failures**, reader maxima
  first **669.6448 ms**, later **37.239199 ms**. Source SHA-256
  `f7e635a5df3c9277df97e7c8c08a018b3eac4a56c8589d5581c65adefea4798e`, executable classes
  `0f9a3295cda6798a2333903839715e8cb10912d81b0ccbdfb87f7a3c0a2bede6`.
- Restored with submitVersion=v05, **65,269** characters; same four maps/sides/seeds, **0 failures**,
  reader maxima first **650.75 ms**, later **38.8801 ms**. Source SHA-256
  `5d0621acb2f4b819c0d9ce10a0589502832a5dea554c2e4d33549c66d2019484`, executable classes
  `d197313df6573657cb37c7c195dd274cc8bb5ed160476275cd762233442d9748`.
  Artifact differs from previous v05 because the current Params includes the unused new flag;
  frozen v05/core/shared runtime sources are untouched. Existing input-send stall remains open.
- Process reports candidate/restored-v05 SHA-256:
  `39dd6abaea02283a5ed4316d8aa84d6db76843576cd58efbae5d6e7a277f2b5c`,
  `038c636e6ad86e85b6381ae2f83fc8f9bf5617548728ca1a46fb8d6fd5d67f02`,
  under `build/analysis/joint-action-v1/`. Both use zero grace; no retry-to-green or upload.
- Decision: **not promoted**, keep USE_JOINT_ACTION=false and USE_REPLY_RERANK=false, no v06.
  Next diagnostic: fixed direct joint-on/off K2/R1 ablation, not comparison with old percentages
  on different maps. This separates the added flag from the base experimental branch before
  attributing losses or changing target/WAIT policy. Promotion still requires the original gate.
  BOTS/PLAN/experiment docs updated. Only the previous diagnosis was committed as requested;
  this new experiment remains uncommitted for review.

### 2026-09-16 — joint flag on/off ablation (predeclared)

- Committed the completed joint-action screen as `b4d8424`. No strategy, core, reference,
  parameter-default or submission changes in this follow-up. Verify the existing test results
  and run cgValidate before measuring; keep the restored frozen-v05 Player.java unchanged.
- Hypothesis: the additional joint prefix/target policy may lose strength relative to its own
  K2/R1 base, rather than the prior 43.5% vs v05 being solely a different-map/base-policy effect.
  Direct head-to-head isolates the enabled feature package (including cost, target/WAIT choice
  and suppressed leftover building); it does not isolate those components or explain arena rank.
- A: `current?USE_REPLY_RERANK=1,BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1,USE_JOINT_ACTION=1`.
  B: the SAME current implementation and parameters, except `USE_JOINT_ACTION=0`.
  Use current on both sides, not the archived engine, to avoid a code-generation/class-loader
  difference in the on/off comparison. All 47 effective parameters will be retained in JSON;
  verify that the new flag is their only difference and A matches the previous candidate.
- Fixed sample: exactly 300 games / 150 paired authentic generated maps, seeds 21000..21149,
  reusing the previous joint-screen development maps (NOT fresh independent confirmation).
  Fresh bots/state each game, both P0/P1 per initialization. Map seeds do not seed the bot RNG:
  its unchanged seed is Params.SEED * 1_000_003 + playerId * 7919, Params.SEED=1, NOISE=0.1.
  Thus the initial RNG stream is fixed per side across maps. Two workers, Java 17.0.12,
  CG search profile 486/36 ms, unchanged 6-ms reserve. No concurrent timing-heavy work.
- One run only, no sample extension, tuning, extra opponent campaign or reserved confirmation
  pool. Preserve `build/bench/joint-action-v1/joint-on-off.json` at a previously unused path.
  Record map-grouped Hoeffding 95% CI, W/L/D, both sides, accepted behavior and timing/errors.
  An interval spanning 50% is inconclusive, not equivalence. A directional result describes
  only this head-to-head; neither outcome bypasses the failed v05 promotion gate.
- EvalBot/Params source hashes remain respectively
  `e69497ff356729fc9e3a477dee3140274d791f7c22234f7248c2c2533357b84f` /
  `8df5e35ec46a826303e0ec48ee6a3e54c8ae8ebc4f4c0dcf24207ea2e3bf2416`.
  Source/code revision and full configs identify both variants; no new version or upload.
  No hot-path or artifact changes, so no new submit/procCheck run is needed for this diagnostic;
  earlier process checks are limited historical evidence and the input-send stall remains open.

### 2026-09-16 — joint flag on/off ablation (outcome)

- Completed the single predeclared run: **139-159-2, 46.7%**, 300 games / 150 paired maps,
  map-grouped Hoeffding CI **35.6-57.8%**, mean score difference **-150.0**. A as P0:
  **67-82-1**; as P1: **72-77-1**. Zero bot errors; elapsed **194.759 s**.
  All 150 initialization hashes/seeds match the previous joint screen. Both current configs
  have 47 fields, differing only in USE_JOINT_ACTION; A exactly matches prior candidate params.
- Accepted sabotage A/B **80.72% / 82.32%**, construction **91.81% / 92.01%**, cells/turn
  **2.188 / 2.205**, paint/turn **2.708 / 2.730**; **76.5** turns/game, **241/300** early closures.
  Descriptive trajectory-level differences, not causal attribution to WAIT or skipped leftovers.
  No selected height slice is used for tuning, a new sample or a changed decision threshold.
- Search mean **8.125 / 7.685 ms**, max **52 / 46 ms**, >50-ms local slow counts **1 / 0**.
  In-process timing is not a protocol deadline test. No new submit/procCheck because code and
  artifact stayed unchanged; no inference about the old unexplained input-send stall.
- **Decision: inconclusive, no promotion or tuning.** No confirmed gain over K2/R1, but the
  interval includes both benefit and harm; it does not prove inferiority or equivalence. This
  head-to-head isolates the enabled package, not its target policy, lost leftover builds or
  search overhead separately, and cannot apportion the prior loss against v05 (non-transitivity).
  Keep USE_REPLY_RERANK=false and USE_JOINT_ACTION=false; no v06 or confirmation-pool games.
- Report `build/bench/joint-action-v1/joint-on-off.json`, SHA-256
  `f4c7a87d1716d980cd9789a82904c151d6eb21345748dcf695d6c41f594af704`;
  strategy revision `b4d842442e5feb96801c9d0d802dcc947b18639d`, source hashes unchanged.
  Compiled EvalBot/Params SHA-256 respectively
  `fbb5cf08c41d5246044fb7764c62686e201ec56cd55575114cb18ba7a6ef6f32` /
  `64132b4e52857f2317ccf13b01d812b6eb5711b30e5c9047256329aa672889ac`.
  Initial launch was denied access to the Gradle wrapper lock before any game started;
  the authorized launch completed once. No game-sample rerun or retry-to-green.
- Existing **162 tests / zero failures** remained up-to-date, fresh **cgValidate 264/264 exact**.
  Player.java still frozen v05, SHA-256
  `5d0621acb2f4b819c0d9ce10a0589502832a5dea554c2e4d33549c66d2019484`.
  Next: audit full emitted action differences on the saved 110 states through core, separating
  no-hit/leftover changes and known/predicted/oracle evidence before any policy revision.
  PLAN/BOTS/experiment notes updated. This follow-up changes only documentation, uncommitted.

### 2026-09-16 — simple heuristic policy v1 (predeclared)

- User chose a simple policy for OUR bot, not a boss clone. Pause joint-branch diagnosis.
  Add independent HeuristicBot behind USE_HEURISTIC_POLICY=false. Preserve EvalBot, frozen
  versions, pressure reference, core rules and retained experiment identities.
- No general state evaluator or predicted replies. Known-board sabotage ranks enemy tracks and
  active-path exposure minus our exposure, with target persistence. Construction ranks local
  route ownership/paint features, prefers keeping a useful route, avoids its sabotage target
  and highly unstable free cells; safe idle extension. Only losing-close safety uses hypothetical
  ink/core connectivity/income. No copied scoring/pathing, no claim to implement the boss.
- Fixed cap of 64 path queries per turn, deterministic order/seeded ties, no random noise.
  Emergency timer on, measured and counted; disabling it is a local debug option only.
  Test replayability with ample/different budgets, legality, work bound, target memory/avoidance,
  losing closure, expired-clock fallback, default/archived compatibility and merged-policy parity.
- One screen: 300 games / 150 paired generated maps, seeds 22000..22149, 2 workers, CG 486/36 ms,
  current?USE_HEURISTIC_POLICY=1 vs v05. Candidate uses SEED=1 deterministic ties; v05 unchanged
  NOISE=0.1. Regardless of result, one 200-game style check vs pressure-v1, seeds 22000..22099.
  No weights changed after results, no sample extension or confirmation-pool games. Style result
  is not improvement over v05 without matched control. Normal >=55%/map-CI gate is required.
- Run tests/cgValidate before benches. Candidate submit + one strict four-game procCheck vs v04,
  then restore v05 + same four-game procCheck. Preserve failures, no retry-to-green/upload.
  Select only the requested policy source in Player to stay below 100000 characters. Record full
  configs/source/artifact hashes and outcomes. Keep all new changes uncommitted for review.

### 2026-09-16 — simple heuristic policy v1 (outcome)

- Implemented the independent policy and selected-policy merger/warmup. Default Params selector
  remains false; current EvalBot, core, frozen versions, pressure-v1 and retained experiment
  identities are unchanged. Tests cover legality/input immutability, fixed-work action equality,
  cap/emergency fallback, target persistence/avoidance, final-turn closure safety, wiring and
  merged-policy/warmup parity. Updated old tests to distinguish newly added unused fields from
  archived parameter sets, without modifying historical snapshots or bytecode manifests.
- Before strength measurement: **169 tests passed; cgValidate 264/264 arena games exact**.
  Separately validated **21/21 boss games**, including the five newest files. Directory validation
  is non-recursive; the boss corpus is not included in the 264 count. No rules changes.
- Fixed main screen, two workers, Java 17.0.12, CG 486/36-ms search budgets: **126-174-0,
  42.0%**, 300 games / 150 distinct paired generated maps, seeds 22000..22149. Map-grouped
  Hoeffding 95% CI **30.9-53.1%**, average score difference **-205.66**, elapsed **81.782 s**.
  A as P0 **72-78**, as P1 **54-96**. Gate not passed; interval still includes 50%, not proof
  that every simple heuristic is weaker. No post-result tuning, extra samples or confirmation.
- Main-screen accepted sabotage A/B **87.36% / 87.91%**, placement **83.02% / 80.72%**,
  **76.42** turns/game and **172/300** early closures. Mean move time **1.320 / 4.776 ms**,
  max **37 / 36 ms**, zero >50-ms slow counts or bot errors. Different policies create different
  trajectories, so this is not an isolated performance comparison. No aggregate emergency-stop
  count is present in these reports; zero slow counts does not prove the cutoff never fired.
- Predeclared style check: **104-95-1, 52.25%** vs pressure-v1 on 200 games / 100 paired maps,
  seeds 22000..22099, CI **38.67-65.83%**, average difference **+158.74**, elapsed **33.836 s**.
  Same candidate configuration and first 100 map hashes verified against the main screen.
  Accepted sabotage **66.39% / 99.35%**, placement **91.90% / 45.97%**; mean move time
  **1.178 / 2.275 ms**, max **33 / 37 ms**, zero slow moves or errors. No matched new v05 control,
  so do not compare with historical pressure win rates or infer a general strength improvement.
- Candidate strict process check vs v04, four fixed maps/sides, zero grace: **0/4 problem games**,
  first-response max **566.9921 ms**, later max **24.3857 ms**. Restored frozen-v05 source and
  ran the same fixed check once: **0/4 problems**, first max **656.3044 ms**, later max **34.8016 ms**.
  No concurrent heavy jobs, retries-to-green or upload. These small samples do not explain or
  resolve the earlier frozen-v05 66.6288-ms input-send stall; that item stays open.
- Candidate Player: **62,746 / 100,000 characters**, SHA-256
  `da23979ca05f3f3a6765c7c9134f09af224261813f78a557d28085fb44aa8738`;
  archived locally with its manifest as `build/analysis/heuristic-v1/Player-candidate.java`.
  Final restored Player: **65,984 characters**, SHA-256
  `18432faa975b38b5c1dd0ac0d4961cec28e8107d4ad0dd828c150342a6ba328b`.
  Its bytes changed because of shared dispatch and unused parameter declarations, not v05 policy.
- Reports and SHA-256:
  - `build/bench/heuristic-v1/candidate-v05.json`:
    `99f4040cbc4084b8d2d731e3d60a9fa0ff4fd47521efdf31241fa07e6dd6d9b1`.
  - `build/bench/heuristic-v1/candidate-pressure.json`:
    `bf1436c804cf62d110d890c42b43de77428130a4e8dfdfcd8c7eaddfad7caa88`.
  - `build/analysis/heuristic-v1/candidate-process.json`:
    `5348e20943ff46b9ed5add6868d6796d72834a42eae3f0bdac263e12de18c9cc`.
  - `build/analysis/heuristic-v1/restored-v05-process.json`:
    `1ec78538f7573e11bff2f9cf1d2fbcca22a27cf1bb986b7acb2ab17ef5125805`.
- All 55 resolved parameters are in each benchmark/manifest (the independent policy consumes
  only its documented subset). Source identity: worktree on `f02881c`, HeuristicBot SHA-256
  `89873c737617856775ddf96982055e58893a7b12764763131a3036d81ec8fc51`, Params
  `414da964a96d04facb87703b8c5f8409c00defc6591f30a0e82a81d96a394aa4`, MainBot
  `a51d1089db8b2495f8407acb88ac4d6521781eb86d6e82c062562e6706755f88`.
  Compiled HeuristicBot / Params SHA-256:
  `71f1909d9d53fc92122f2f00233baf059e0ec1be226555db579bbcdc45444ce1` /
  `1592bf0184c7b3ea2b425335cf888de746e84e42f0277eed5e84a4e38042d98f`.
- Two launch failures preceded all games: wrapper-cache permission, then PowerShell splitting
  an unquoted `.json` property. Corrected permission/quoting; each game sample ran exactly once.
- Decision: **retain as an opt-in simple baseline, no v06 or promotion**. The next proposed
  single-feature experiment is bounded detour-paint sabotage priority, from already observed
  opponent routes, not guessed replies. Current v1 only uses track counts/exposure; no evidence
  here yet attributes losses to that omission. README/BOTS/PLAN and heuristic-policy.md updated;
  new work remains uncommitted as no commit was requested in this turn.

### 2026-09-16 — heuristic detour priority v1 (predeclared)

- User approved the next single-feature experiment. Prior heuristic work remains uncommitted;
  do not commit implicitly. Preserve its source/config identity and archived merged artifact;
  capture deterministic old-policy action-history hashes before editing the policy. Compare
  detour ON/OFF in the SAME current implementation; OFF must retain the prior algorithm.
- Hypothesis: track/exposure counts miss the paint cost of reconnecting an existing route after
  ink. Add default-off USE_HEURISTIC_DETOUR. Construction/idle/close rules and all old weights
  stay fixed. Use core cheapest paths with the entire target region blocked; current active
  connections have zero baseline paint cost. Already-built bypass => zero added cost; no path
  => bounded cap 12. This is a static recovery-cost feature, not an opponent action forecast.
- Bound work: top 4 positive base targets, top 4 unique undirected active town pairs ranked by
  absolute (enemy tracks - 2 * own tracks). For each crossed target, one core path query. Weight
  recovery cost by that pair's signed ownership imbalance / owned track count; use strongest
  enemy burden minus strongest own burden, not a sum of overlapping recovery projects. Clip
  costs at 12 paint, add weight 1 before division by hits remaining and existing stickiness.
  At most 16 added queries; ignore the whole partial adjustment if the emergency timer fires.
  Unfinished routes and future construction are outside this first experiment.
- Fixed samples: 300 games ON vs OFF, then 300 ON vs frozen v05, then 200 ON vs pressure-v1.
  Generated paired maps, seeds 25000..25149 (style check first 100), both sides, 2 workers,
  Java 17.0.12, CG 486/36 ms. Candidate spec current?USE_HEURISTIC_POLICY=1,USE_HEURISTIC_DETOUR=1;
  control current?USE_HEURISTIC_POLICY=1,USE_HEURISTIC_DETOUR=0. SEED=1 deterministic ties;
  v05 NOISE=0.1 unchanged. No parameter tuning, sample extension or confirmation-pool games.
  Same-code ON/OFF measures the feature package including cost. Frozen-v05 >=55% and lower
  map-grouped Hoeffding 95% bound >50% remains the strength gate; other checks stay separate.
  Do not compare the new v05/pressure results with unmatched old 42%/52.25% as a causal gain.
- Tests + arena/boss cgValidate before benches. Verify core pathing on bypass/no-bypass fixtures,
  own collateral, candidate/work bounds, input isolation, OFF history, ON reproducibility and
  merged parity/warmup. Candidate submit + ONE strict 4-game process check vs v04; preserve
  candidate artifact, restore frozen v05 + same strict check. No uploads or retry-to-green.
  Preserve reports under new build/bench/heuristic-detour-v1 and build/analysis/heuristic-detour-v1.

- Pre-benchmark protocol correction (no games measured yet): the full-sequence unit test found
  that two identical simple policies produce neutral-rail mirrors and never invoke detour queries
  on its two generated maps. Use an existing opponent-owned route in that test to exercise the
  intended branch. Do NOT spend a strength run on known low-coverage symmetric self-play.
  Replace the 300 ON-vs-OFF series with **300 OFF-vs-v05**, then **300 ON-vs-v05** on the same
  150 maps/sides, seeds 25000..25149. Keep the predeclared 200 ON-vs-pressure style check.
  Report matched v05 outcomes (added/lost wins) separately from candidate's direct v05 gate;
  these do not establish direct ON-vs-OFF superiority. Fixed total 800 games, no extension.
  The captured pre-edit OFF action-history hash remains a regression guard, but mostly neutral
  self-play is limited sabotage coverage; explicit owned-route fixtures cover that branch.

### 2026-09-16 — heuristic detour priority v1 (outcome)

- Implemented the capped 4-region x 4-connection recovery-paint feature through existing core
  pathing. Only the new flag selects it; construction/idle/close logic and old weights are
  unchanged. All new buffers are allocated only when enabled. Interruption clears the whole
  partial adjustment. No core, frozen policy, pressure reference or archived experiment changes.
- **177 tests passed**, including the pre-edit v1 action-history digest
  `c7c5273fb330afb0ed6dad99879eeaa2471c79511f1ca539016aa754e432109b`;
  owned-route fixtures verify free bypass, terrain price/cap, impossible reconnection, own
  collateral, undirected-pair deduplication, bounded work, input isolation and ON replayability.
  Merged ON parity/warmup is checked on an active opponent-owned connection. One initial test
  failed because generated mirror self-play never exercised detours; corrected its fixture and
  amended the screen before any benchmark, without changing policy parameters. Fresh validations:
  **264/264 arena + 21/21 boss replays exact** (separate non-recursive directories).
- Followed the amended fixed protocol exactly: OFF vs v05 **140-160-0, 46.67%**, 300 games /
  150 distinct paired maps, seeds 25000..25149; Hoeffding CI **35.58-57.76%**, mean score
  difference **-160.11**, elapsed **79.890 s**. P0 **63-87**, P1 **77-73**. Mean local move time
  A/B **1.217 / 4.591 ms**, max **31 / 34 ms**, zero slow moves/errors. Accepted sabotage
  **86.35% / 87.78%**, placement **81.88% / 80.05%**, **154/300** early closures.
- ON vs v05 **135-165-0, 45.0%**, same 300 games / 150 map hashes and sides, CI **33.91-56.09%**,
  mean score difference **-149.8**, elapsed **83.536 s**. P0 **69-81**, P1 **66-84**. Mean local
  move time A/B **1.395 / 4.620 ms**, max **37 / 34 ms**, zero slow moves/errors. Accepted sabotage
  **85.22% / 87.52%**, placement **81.05% / 79.21%**, **142/300** early closures. Turn averages
  ON/OFF **80.40 / 79.16**. Runtime figures are from different resulting trajectories, not an
  isolated cost benchmark. These reports do not aggregate emergency cutoff counts.
- Verified all 60 candidate/control fields: only USE_HEURISTIC_DETOUR differs. Opponent params,
  map hashes/seeds and timing profiles match. Joined by map/side: **31 added wins, 36 lost wins,
  233 unchanged outcomes**, net **-1.67 percentage points**. Descriptive matched outcome change,
  not direct ON/OFF superiority, causal component attribution or a claim of statistically proven
  harm. No post-result weight changes, map-slice tuning, additional games or confirmation run.
- Predeclared style check: ON vs pressure-v1 **110-90-0, 55.0%**, 200 games / 100 paired maps,
  first 100 map hashes and candidate config match. CI **41.42-68.58%**, mean difference **+187.37**,
  elapsed **42.956 s**. P0 **50-50**, P1 **60-40**. Mean local move time **1.606 / 2.782 ms**,
  max **36 / 39 ms**, zero slow moves/errors. Sabotage **65.08% / 99.24%**, placement
  **91.16% / 45.15%**, **92/200** early closures. No matched OFF pressure series, so no improvement
  claim against the old 52.25% on different maps and no pooled opponent win rate.
- Candidate submit + one strict process check vs v04: **0/4 problem games**, max first response
  **644.917 ms**, later **30.3946 ms**. Restored frozen-v05 source + same check once: **0/4 problems**,
  first **674.2755 ms**, later **34.5849 ms**. Zero grace, no concurrent heavy jobs, retries-to-green
  or upload. Neither small process sample resolves the historical 66.6288-ms host input-send stall.
- Candidate artifact: **68,374 / 100,000 characters**, SHA-256
  `c0f6c433089a58bd1968ca7f8fd4d01d2372bbbaacc4e0eaf2f5b5243f033035`, preserved with manifest at
  `build/analysis/heuristic-detour-v1/Player-candidate.java`. Final Player is **v05**, **66,343 chars**,
  SHA-256 `45c25fc41d74c41b79498e8bab410dedf0e4530e8db9381382c16e11d976c6e8`.
  New unused Params declarations alter its bytes but not frozen strategy source/decisions.
- Report SHA-256 (all paths local/gitignored):
  - `build/bench/heuristic-detour-v1/control-v05.json`:
    `5edc91c67201e55f91b5bba224d59aaec04468dc2fa1cd59443b37b3594567c3`.
  - `build/bench/heuristic-detour-v1/candidate-v05.json`:
    `62323651cdbda46fcc0fa69e82932da24d610305484cda9c909da9b89f7b4fab`.
  - `build/bench/heuristic-detour-v1/candidate-pressure.json`:
    `3055c8c51a77564a97b5810b11d0dbe8cd1a55b47eb63871b9aff1a1f9e1cfc7`.
  - `build/analysis/heuristic-detour-v1/candidate-process.json`:
    `1f098af5d9c8f506b999ba9e1dc855d44a7bb8581ce9e6600ac7eb937d237769`.
  - `build/analysis/heuristic-detour-v1/restored-v05-process.json`:
    `11d14abd088da02ae83e9d20766f44e860f1833fb07609a0ab4c571cb2b16da2`.
- Source identity (worktree on f02881c, including prior uncommitted heuristic wiring): HeuristicBot
  `55418dc24d19332bebc28f3d3900681c0584e6e8424e376d5f7d347996f6b2b2`, Params
  `f7d92a9e5af293bccadb018706a112cfe54d299bbad005c17018197fed86d0ee`.
  Compiled HeuristicBot / Params:
  `67e40d4ea2ce0554cebadaa476a957635539ede267ecf1c233a5146b79a3be85` /
  `65d9fd351bcd8436c228b4de35ae0ac101b6f2f3333cd94a9fddbfd1b12c94dd`.
- **Decision: not promoted.** USE_HEURISTIC_POLICY=false and USE_HEURISTIC_DETOUR=false;
  frozen v05 remains the submission. Mechanism works on fixtures but no strength gain shown
  against the matched v05 control; active-route-only recovery price omits unfinished routes and
  score/ownership changes on alternative paths. Those limitations are hypotheses, not established
  loss causes. No next tuning campaign started. README/BOTS/PLAN/heuristic docs updated;
  all prior and new changes remain uncommitted, as requested workflow requires explicit commit.

### 2026-09-16 — close heuristic experiments and adopt automatic local commits

- User requested committing both completed heuristic experiments and making local commits the
  default after verified experiments and recorded results, including non-promoted outcomes.
  Added the preference to AGENTS.md and the project rule; no automatic push/upload/promotion.
- Both heuristic selectors remain off and Player.java remains frozen v05. Prior reports and
  negative/inconclusive results are preserved. Next scope: inspect the five newly added boss
  replays for early defensive rerouting after observed sabotage, before choosing another change.

### 2026-09-16 — five new boss games: observed-threat bypass study

- Committed the two completed heuristic experiments and automatic-local-commit preference as
  `27ed521`. Chose the user's replay-analysis option before another policy change. Fixed sample:
  `replays/cg/bot/904047070.json`, `904047111.json`, `904047151.json`, `904047204.json`,
  `904047262.json`. All identify P1 as **Boss 4, agent 6740207**, via `agents[].index` and
  `arenaboss.nickname`; same boss identity as the previous five. P0 is aabzac IDE agent -1:
  exact submitted source/config is unknown, so do not label these frozen-v05 results.
- Fidelity first: **264/264 arena and 26/26 boss replays exact**, separate non-recursive
  directory validations. The five selected files have no failure turns; boss wins all five.
  P0/P1 final scores in file order: 562/1168, 2238/5803, 1179/1215, 1929/2611, 2042/3014.
  This small selected sample is descriptive, not an arena strength estimate.
- Added `cgBossStudy` / `CgBossDefenseStudy`, tools-only. Requires explicit comma-separated
  `-Ppaths`; every input is validated before analysis. `-Pout` refuses existing files; otherwise
  a timestamped output is used. No engine, policy, parameters, frozen opponents or Player edits.
  General CgReplay still only recognizes legacy boss id -2, not positive ids with `arenaboss`:
  recorded the name/render-routing issue in PLAN; this study reads the authoritative marker.
- Probe method: use the **pre-turn board**, accepted prior-turn opponent disruption, and only
  already unstable, uninked, non-town regions with boss tracks. For each current active desired
  town connection crossing such a region and carrying boss tracks, ask core for cheapest paint
  cost avoiding the entire region. Repeat on an isolated copy with **only accepted boss
  placements** applied through core: no opponent placements, current disruption/ink or scoring.
  Cost -1 means impossible; positive finite cost decreasing is progress, decreasing to 0 is a
  completed alternative connection. Existing zero-cost bypasses do not count as new progress.
  Check each direction's canonical active route before deduplicating unordered town pairs.
- Observation boundary: previous opponent target is already observable; recorded same-turn
  stdout is retained only as context and never used to choose the threatened region or path.
  First observed hit means that prior target's instability was zero before its accepted hit.
  The probe covers active routes only, not unfinished projects. It measures connectivity/paint,
  NOT preserved ownership income, intent, response causality or subsequent game-win benefit.
  Shared track construction can help several connections; counts below use region-turns, not
  independent pairs/games. Positive cost is not a claim that completion fits the remaining time.
- **Sabotage pattern:** 313/315 boss turns (99.37%), versus P0 268/315 (85.08%). The only boss
  omissions are turn 0 of 904047070 and 904047262. Of 235 turns with a current boss target and
  its previous target still uninked/non-town, 195 repeat that target (82.98%). This conditional
  measure excludes turns without a current target. Targets on the known board: opponent-only
  253, mixed 54, empty 5, own-only 1; occasional self-collateral is real, its intent is unknown.
- **Building pattern:** boss places 461 cells / 595 paint on 200/315 turns; P0 689 cells /
  914 paint on 310/315 turns. Boss messages: build 144, steal 45, fortify 10, block 1, none 115.
  More spending is not automatically more useful spending; these totals cannot establish why
  P0 lost. `fortify` is a branch label, not a ground-truth defense annotation: only 2/10 tagged
  turns reduce the measured bypass cost of any currently probed threatened region.
- **Timing of bypass contribution:** after a prior accepted opponent hit, 203 region-turns
  remain eligible threats; 150 cross a probed active boss connection, and 93 have at least one
  positive finite bypass cost. Boss placements reduce a cost in **44/93**, completing at least
  one alternative in **16/93**. Breakdown by current instability:
  - 1 hit: 66 threat observations, 45 with active connections, 30 positive-cost opportunities;
    **4/30 progress, 0 completions**.
  - 2 hits: 71 observations, 53 active, 35 opportunities; **25/35 progress, 10 completions**.
  - 3 hits: 66 observations, 52 active, 28 opportunities; **15/28 progress, 6 completions**.
  These are repeated observations from five trajectories, not independent trials or a test of
  "boss reacts at instability 2". Other building may incidentally reduce bypass cost.
- Concrete examples (all turn ids **zero-based pre-turn decisions**, not viewer frame labels):
  - 904047070, region 22, town pair 3->2: at t6 / instability 1, bypass stays 3->3 paint;
    at t7 / instability 2, boss builds (2,8), (2,6), (2,7), reducing it **3->0**. At t8 /
    instability 3 it is already 0. The completing message is `build 3>0`, not `fortify`.
  - 904047204, region 18, t12 / instability 1: boss's three placements reduce a shared bypass
    **7->4** for six active pairs. This is compatible with immediate preparation after first hit.
  - 904047262, region 27: t31 / instability 2 reduces **4->1**; t32 / instability 3 reduces
    **1->0** for five active pairs. Both turns are labelled `build`. These examples support
    pre-ink alternatives, but not an always-immediate response rule.
- Source comparison: EvalBot already generates `USE_BACKUP` candidates for active pairs crossing
  the single `worstRegion` from vulnerability evaluation. That region can be merely hypothetical;
  candidates compete in the general gain/horizon/cost/risk/noise ranking. No explicit defense
  deadline or dedicated observed-hit priority exists. Proposed next experiment: a bounded,
  default-off cheap-bypass priority for actually threatened live income, conditioned on finishing
  before ink. Reuse core scoring so connectivity is not mistaken for retained income. One feature,
  predeclared matched screen plus older/style control; no tuning/promotion based on this sample.
- Verification: **181 tests passed**, including four new boss-study tests (positive-id identity,
  both player sides/input isolation, partial versus complete bypass, reverse-direction exposure).
  During review corrected deduplication to check reverse canonical route exposure before skipping
  a town pair. Preserved initial schema-1 report; reran the SAME five files as schema 2. Counts
  above were unchanged. No new games, strength benchmark, process run or upload in this study.
- Final local report: `build/analysis/boss-defense-20260916/new-five-v2.json`, SHA-256
  `bb31e54d2873d5b6751791adf680c9e2affd97859fd71713d99eedbd8f3fb219`.
  Replay SHA-256 in sample order:
  `a2be5a790b5ff529436c328877044f73bcef5e5cbd3c96aff8e32b7571a4f5a3`,
  `4afe1af071a480d3e861e8d689bbdcc12cbbc9f8d91f212f1bdea8a7740a677b`,
  `f669271e1deb2f480fc1f453c1f7589c6f899453faf6211e985d1353f7deaea9`,
  `2aa68c71217c52af023e58edb08109a6ec1f2f51b21751cdb97c3ce03c86bf11`,
  `41d280e01cdba32da728339e8d7ab13b2c673d5dd35ee7b89bc009a9c14a6e9e`.
  Final Player unchanged, v05 SHA-256
  `45c25fc41d74c41b79498e8bab410dedf0e4530e8db9381382c16e11d976c6e8`.

### 2026-09-16 — observed-threat defense priority v1 (predeclared)

- User approved the next single-feature experiment. Base revision b4e4c1e; candidate
  `current?USE_DEFENSE_PRIORITY=1`, baseline frozen v05; all unrelated experiments off.
  Hypothesis: explicitly prioritizing cheap pre-ink recovery of active income can outperform
  letting backups compete only in general completed-position evaluation. Boss observations
  motivate the test, not a claim about the cause of our own losses.
- Once per turn, before ordinary own construction, examine at most 3 already unstable regions
  carrying our tracks on active routes, highest instability first, then region id. For each,
  try at most 4 exposed directed desired pairs using core cheapest paths. Avoid the target and
  all other already unstable non-town regions. Ignore zero-cost, impossible or >6-paint routes.
  Simulate the actual uncontested-first affordable placement order, at 3 paint per turn, to
  verify completion within remaining hits (including this turn) and remaining game turns.
  This assumes one further enemy hit per turn; no new opponent forecast or attribution of old
  instability to a particular player. Replan each turn; no persistent commitment in this v1.
- Core income after hypothetical ink must show actual own loss without building, and the
  completed bypass must recover both own income and raw income difference. Each simulated
  affordable prefix must preserve the current raw income difference; this gate applies only
  to forced defense. Choose the highest recovered difference / (paint * remaining hits),
  deterministic ties. Ordinary construction handles leftovers; sabotage logic is unchanged.
  At most 12 extra path queries, existing build deadline/reserve retained; interrupted defense
  returns only a fully checked candidate. Feature-specific buffers allocated only when enabled.
- Fixed screen: **300 games / 150 paired generated maps**, seeds **27000..27149**, candidate
  versus v05. Older aggressive control: **200 candidate-vs-v02 and 200 v05-vs-v02 games** on
  seeds 27000..27099, opponent `v02?W_FUTURE_TRACK=1.0,W_FUTURE_OWN=0.2` in both controls.
  Java 17.0.12, two workers, CG 486/36 ms,
  default SEED=1 and NOISE=0.1, full configs/hashes saved in reports. No post-result tuning,
  sample extension or confirmation-pool use. Promotion requires >=55% and lower map-grouped
  Hoeffding 95% bound >50%, no errors, plus independent confirmation before freezing if the
  screen passes. Older-control results stay separate, never pooled into the direct gate.
- Before benches: unit tests, captured known-state fixture, OFF/frozen ample-time parity,
  merged ON parity/warmup, arena + boss cgValidate. After benches: candidate submit and one
  strict four-game process run vs v04, preserve candidate source/manifest, restore v05 and
  repeat the same fixed process sample once. No retry-to-green, upload or implicit promotion.
  Reports: build/bench/defense-priority-v1 and build/analysis/defense-priority-v1; preserve all
  failures and source/artifact identities. Commit implementation and results when finished.

- Implementation checks before the first game: seven new defense tests plus merged-policy
  parity/warmup; **189 tests passed** and **264 arena + 26 boss replays exact**. Captured fixture
  contains only the 14 prior stdout lines before P1 turn 7 of 904047070, not that turn's reply.
  Tests cover both sides, useful two-turn recovery, packing waste (three river cells require
  three turns despite costing six paint), deadline/cost caps, free/impossible/no-damage cases,
  enemy-benefiting bypass rejection, original state isolation and ample-time OFF/frozen parity.
- Initial test failures, before any bench: merged current reached **101,174 characters**;
  changed packaging only to strip surrounding line indentation, preserving token/newline and
  ordinary string-literal contents. Explicitly reject unsupported Java text blocks rather than
  alter their semantics. Source files/frozen policies retain their formatting. Full merged
  current/heuristic/frozen tests passed after this change. Also extended the frozen-parameter
  expectation for the five new fields ignored by v05; no historical parameter resource edited.
- Source identities used by all three benches: EvalBot SHA-256
  `2c521893a2762cdd9f902fb4f75cf1138a1ff5e8fa716223fc4885ee314d0d6e`, Params
  `460e487e4554ef17cf62c809d0b76c22cad7d2b901c49b1eaec7917bee459882`;
  compiled EvalBot `bcb1ff8b2a1c88c4c9bb759f1635570ea0c2ffb9fb1f891620b96832154fc020`,
  Params `47a62e732cacab4f4c8159bb4dcaf5dece1d5435ebee887417827e49b2161d07`.

### 2026-09-16 — observed-threat defense priority v1 (outcome)

- Followed the fixed direct screen: **141-157-2, 47.33%** versus frozen v05, 300 games /
  150 paired generated maps, seeds 27000..27149. Map-grouped Hoeffding 95% CI **36.24-58.42%**;
  no promotion or confirmation run. P0 wins 75/150, P1 66/150; mean score difference **-183.69**,
  mean length **71.61 turns**, 256/300 early closures, zero bot errors. Local search mean
  A/B **4.356 / 4.628 ms**, maxima **38 / 38 ms**, zero >50-ms slow moves. Accepted sabotage
  **84.19% / 83.98%**, placement **93.92% / 93.86%**. Elapsed 108.964 s. Configuration comparison
  confirms that USE_DEFENSE_PRIORITY is the only differing resolved parameter; historical
  strategy source is nevertheless a separate implementation from current.
- Candidate older-style control: **127-73-0, 63.5%** vs
  `v02?W_FUTURE_TRACK=1.0,W_FUTURE_OWN=0.2`, 200 games / 100 paired maps, seeds 27000..27099.
  CI **49.92-77.08%**, score difference **+334.03**, length **79.35**, 135/200 early closures.
  P0 wins 67/100, P1 60/100; zero errors/slow moves; mean search **4.036 / 2.233 ms**, max
  **35 / 38 ms**. Sabotage **67.71% / 88.87%**, placements **97.49% / 48.82%**. Elapsed 57.983 s.
  Verified these maps match the direct screen's first 100 hashes; no slice-based tuning.
- Matched frozen-v05 older control: **147-53-0, 73.5%**, CI **59.92-87.08%** on the same
  100 paired maps/opponent config/timing. Score difference **+521.51**, length **81.03**,
  134/200 early closures. P0 wins 78/100, P1 69/100; no errors/slow moves; search mean
  **4.253 / 2.045 ms**, max **37 / 36 ms**. Sabotage **65.97% / 88.89%**, placement
  **96.30% / 47.53%**. Elapsed 59.454 s. Joined map/side outcomes: candidate **19 added wins,
  39 lost wins, 142 unchanged**. All resolved A configs differ only in USE_DEFENSE_PRIORITY;
  B configs and all selected map hashes match. No mismatched historical-rate comparison.
- Decision: **keep USE_DEFENSE_PRIORITY=false, no new frozen version**. Direct screen fails
  the practical/confidence gate; the older control does not support promotion either. This
  does not establish statistical harm/equivalence or isolate any one heuristic subcondition.
  Candidate can force useful recovery in the captured position, but the broad tradeoff is not
  beneficial enough to adopt. Completion assumes no intervening enemy placements/other inks,
  unchanged current contested ordering, and one enemy hit per turn; ordinary later building
  and our sabotage may change the plan. Known-state projections are not guaranteed protection.
  Timings come from different resulting trajectories, not an isolated speed comparison.
  No post-result parameter edits, strength games beyond the declared 700, or confirmation use.
- Candidate artifact: **76,878 / 100,000 characters**, source SHA-256
  `12ccfe8ae3af70d0d75c912cbd99ef9fe13f1332eedb31a985bdd66a38dd9ccc`, archived at
  `build/analysis/defense-priority-v1/Player-candidate.java` with `candidate-manifest.json`.
  Candidate strict process check vs v04: **0/4 problem games**, first-response max **807.3832 ms**,
  later max **39.233 ms**. Passes the actual 1000/50-ms deadlines in this small sample, but two
  first responses exceed the separate 800-ms headroom target. Do not claim arena timing safety.
- Restored **frozen v05**, now **49,870 chars** after whitespace-only packaging and five unused
  parameter declarations; SHA-256 `77c5954ceded17137fe2645467d7b0602159eb5992f5ce0c368d15d087cc489c`.
  Its one fixed strict process check **FAILED 4/4 games, all turn 0**. Files/positions:
  903182120 P0, 903182383 P1, 903182464 P0, 903182662 P1; identical to the candidate sample.
  Input preparation/send took **366.677 / 894.5249 / 1112.2006 / 1138.9977 ms**. No timestamped
  response was received; timeout consumers woke at **1008.5119 / 1017.689 / 1112.2506 / 1139.0472 ms**.
  Thus the last two consume the entire deadline before input sending finishes. Console "reader"
  values in these failure cases are not actual response timestamps; JSON responseMs is null.
  Candidate first input sending was 209.831..245.980 ms. No IO-phase diagnostics were enabled,
  so preparation vs blocking write/host scheduling/JVM startup remains unidentified. The two
  sequential artifacts do not isolate the cause, and this is not proof of a search regression.
  No concurrent strength runs, retries-to-green, runtime fix or upload. Retain the failed report;
  selected policy is v05 but the final artifact has **no fresh process-readiness pass**.
- Reports SHA-256 under `build/bench/defense-priority-v1/`:
  `candidate-v05.json` = `6414e7894ed59bb4c6691acbd4b6623e7e0d43391bf1a0c82964594439c6aa98`;
  `candidate-v02.json` = `503c149cb2bbc71ac63ddac7781eb2fd2c29855b3e3ffe27b972cbd6ffd9ee34`;
  `control-v02.json` = `048365b34667ea817479265d4341a6d56788cc90ddb25cef4d9cdb73b81c9566`.
  Process reports under `build/analysis/defense-priority-v1/`:
  `candidate-process.json` = `3f7c6b97587478e9485cbb8e7ef7115c87d5cd8c3e1333a16ce96046ec9a5a52`;
  `restored-v05-process.json` = `8d2a13bee70553b1de6aca43c90efee2eff3a45c34b6dbf0f8b6398fd697735a`.
  Executable tree SHA-256 candidate/restored:
  `0ba0f900efc327ec9710ad0cafc7506d5926a90f702e82641534372f864c7095` /
  `23f7463c3954b7d98cf0d2a04ee74a63b5dab41e4d6eca2c3a53610e4d5ec7bd`.
- Updated README/BOTS/PLAN; completed rejected experiment is committed per the user's standing
  preference. No core, historical bot, historical parameter, reference or catalog archive edits.

### 2026-09-16 — startup/input diagnosis before short own-build planning (predeclared)

- User requested step 1 then step 2: diagnose first-turn process timeouts, then test a bounded
  own-build plan over 2-3 turns. Keep all prior failures. First run the existing IO diagnostic
  on exactly four v05-v04 process games, same default corpus ordering/sides as the failed run,
  zero grace and unchanged Player source `77c5954ceded17137fe2645467d7b0602159eb5992f5ce0c368d15d087cc489c`.
  Report: build/analysis/startup-input-v2/diagnostic4.json. No concurrent benchmarks.
  Split preparation/write/flush with host CPU/GC counters before deciding a fix or further probe;
  a non-recurrence is inconclusive, not cause identification or permission to retry to green.

### 2026-09-16 — startup/input diagnosis (bounded outcome)

- Ran the predeclared four games ONCE on unchanged v05 source/executable, with existing IO
  diagnostics: **0/4 failures**, first responses **563.906 / 635.880 / 581.293 / 584.942 ms**,
  later max **35.4419 ms**. Old 4/4 failures remain retained; this is a diagnostic sample, not
  evidence that the previous failure is fixed. No runtime/strategy/deadline/grace changes.
- First input preparation **0.045..0.142 ms**, write **0.013..0.094 ms**, flush
  **76.333 / 143.608 / 91.804 / 80.206 ms**: almost all send time lies in flush, not protocol
  formatting. Host thread CPU counters were 0 at their available resolution, GC delta 0.
  Waiting for pipe/child readiness or host scheduling is plausible; the old uninstrumented
  367..1139-ms sends cannot be apportioned retrospectively. No claim of JIT/antivirus/CPU cause.
  During later turns phase maxima: preparation 0.305, write 0.202, flush 0.354 ms.
- Fixed a reporting bug only: procCheck no longer labels timeout consumer elapsed time as a
  reader response. Console now separates first response (or none), send, consumer and measured
  later response. Two deterministic tests cover missing responses and delayed consumers;
  process clock, submitted artifact and JSON timestamp semantics are unchanged.
- Report `build/analysis/startup-input-v2/diagnostic4.json`, SHA-256
  `4b4e40c2528945040749931c853ebea6349b395f5ff95bdd7ca584dba5675e13`.
  Source is still `77c5954ceded17137fe2645467d7b0602159eb5992f5ce0c368d15d087cc489c`.
  Keep root cause open; no repeated uninstrumented run merely to obtain a pass. Proceed to the
  user-requested own-build experiment with its own fixed artifact/process checks and caveats.

### 2026-09-16 — short own-build plan v1 (predeclared)

- One default-off feature, USE_OWN_BUILD_PLAN: retain up to three ordinary root route candidates,
  enumerate their priority order over three actual affordable construction turns (at most 39
  nodes / 27 leaves). Spend leftovers on the other retained routes in stable order. Use core
  placement, connections and scoring; rank by earned score difference plus the existing
  vulnerability-adjusted terminal income over the remaining horizon. No enemy actions or own
  sabotage in this frozen-board projection; normal sabotage follows the emitted first action.
  Replan from observations next turn. Root candidates still use the old completed-route scorer.
- Deduplicate complete first-turn placement sets, emit exactly the selected prefix, and discard
  an incomplete rollout comparison at the existing build cutoff. Fixed branch limits do not make
  the whole bot timing-independent: root enumeration and cooperative deadlines remain timed.
  No other experiment, weight, RNG seed, budget, warmup or rule changes. Width/depth both 3;
  horizon limited by remaining game turns. Keep ordinary best-route fallback.
- Fixed development screen: candidate vs frozen v05, 300 games / 150 new paired generated maps,
  seeds 29000..29149, CG 486/36-ms search, 2 workers. Older/aggressive control: candidate and
  fresh v05 each play 200 games against v02?W_FUTURE_TRACK=1.0,W_FUTURE_OWN=0.2, the same
  paired maps 29000..29099. Total 700 games, no tuning/slice selection or extension after results.
  Reports under build/bench/own-build-plan-v1/. Require >=55% and map-grouped Hoeffding95
  lower bound >50% before any separately declared independent confirmation; no automatic promotion.
- Before strength: full tests, both arena/boss engine validation, off-mode parity, affordable
  rollout/score/coverage tests and merged submission parity/size. After strength, one four-game
  diagnostic-enabled zero-grace process sample each for candidate and restored frozen v05,
  vs v04 on the default corpus, no simultaneous heavy jobs. Preserve reports and candidate
  source/manifest in build/analysis/own-build-plan-v1/, including failures; no retry-to-green.
- Implementation verification before strength: **200 tests passed**, including eight new own-plan
  tests and merged ON/warmup parity. Core unchanged; **264 arena + 26 boss replays exact**.
  Tests cover actual cost packing, core score equality on both stages of a turn, early income
  (28 vs 26 in a controlled two-project ordering), full-first-action deduplication, 39-node cap,
  endgame depth, expired-search fallback, exact emitted prefix without idle additions, captured
  known-state reproducibility and OFF/frozen-v05 parity. No strength-result-driven edits.
  Source SHA-256 EvalBot `4ca4f89ba8772b2144dd25511561fe8d90ec913ee55f8e5b9a2edc9cb0c757bc`,
  Params `d7c9616bf2241dfd6af8f7a113404dea4292b24f222fda570d0bcf9c15580132`;
  compiled EvalBot `0578f61a8e32fd0e9ebea035ccbfded7c443b36709c64ed82bc6a00b6cae6560`,
  Params `ccc4040decdac8a19f2475eb0e2a93a5e32c8719f953e299b24efecac81fabd1`.

### 2026-09-16 — short own-build plan v1 (outcome)

- Fixed direct screen: **147-153-0, 49.0%** vs v05, 300 games / 150 paired generated maps,
  seeds 29000..29149. Map-grouped Hoeffding 95% CI **37.91-60.09%**; gate not passed.
  P0 68/150, P1 79/150; mean score difference **-45.76**, length **62.83 turns**, 276/300
  early closures. No bot errors; search mean A/B **8.039 / 6.994 ms**, max **69 / 39 ms**,
  slow moves **1 / 0**. In-process outlier is not an independently measured process timeout
  or an identified cause. Accepted sabotage **93.27% / 93.04%**, placement **94.79% / 95.33%**.
  Elapsed 152.160 s. No weight changes, additional parameter sweep or confirmation games.
- Candidate older/aggressive control: **133-67-0, 66.5%**, CI **52.92-80.08%**, 200 games /
  100 paired maps 29000..29099 vs v02?W_FUTURE_TRACK=1.0,W_FUTURE_OWN=0.2. P0 67/100,
  P1 66/100; difference **+309.36**, length **70.53**, 155 early closures. Search mean
  **7.315 / 3.145 ms**, max **38 / 61**, slow **0 / 1**, no bot errors. Accepted sabotage
  **80.94% / 94.86%**, placement **96.76% / 55.58%**. Elapsed 81.277 s.
- Fresh matched v05 control: **150-50-0, 75.0%**, CI **61.42-88.58%**, P0 71/100, P1 79/100;
  difference **+717.56**, length **82.33**, 131 early closures. Search mean **5.534 / 2.828 ms**,
  max **36 / 44**, no slow moves/errors. Sabotage **66.75% / 88.87%**, placement
  **98.37% / 49.06%**; elapsed 78.187 s. Candidate adds **34** wins but loses **51**, with
  **115 unchanged** outcomes. Verified all 100 map hashes against both controls/direct screen,
  B configurations identical, A configurations differ only in USE_OWN_BUILD_PLAN. Frozen and
  current remain distinct implementations with OFF parity tests. These are matched descriptive
  results, not a test of one subcomponent or a speed estimate on identical trajectories.
- Decision: **USE_OWN_BUILD_PLAN=false, no new version or confirmation**. Earlier-income ordering
  works in the unit fixture, but the whole policy does not pass the strength gate. No claim of
  equivalence or statistical proof of harm. Limitations: only three old-scored root projects;
  no new routes at future nodes, no WAIT alternative, no opponent construction/sabotage or our
  future sabotage. Projects beyond three turns can lose terminal value; terminal evaluation
  still uses the old vulnerability penalty. First-turn leftover building is limited to this pool,
  and full ordinary re-search/idle extension is suppressed after a committed plan. Actual
  sabotage follows, so the construction projection is not the predicted full game outcome.
  Search-node bounds are deterministic; time-truncated root enumeration/emergency fallback are
  not. These are hypotheses for any future revision, not established reasons for the result.
- Candidate submission **82,376 / 100,000 characters**, SHA-256
  `38d64417684cecb524ad137bcf072fce18662350fdf1795f7a2d7078ce172589`; archived source plus
  manifest in build/analysis/own-build-plan-v1/. One diagnostic-enabled strict process sample
  vs v04: **0/4 problem games**, first max **645.4417 ms**, later max **35.8022 ms**, zero grace.
  Restored **frozen v05**: **50,038 chars**, SHA-256
  `29039a4e96e3f68cc95411db3f4180e866e0dcdc2f16146ed69345872e359f5d`, three unused new Params
  declarations, unchanged policy/runtime. Its one process sample: **0/4 failures**, first max
  **639.3366 ms**, later max **35.1695 ms**. Both samples have first-send time almost entirely
  in flush (~88..132 ms); no seconds-long recurrence. Prior failures stay retained/unexplained;
  these small local samples do not guarantee CG timing safety. No concurrent heavy jobs/retries.
- Bench report SHA-256 under build/bench/own-build-plan-v1/:
  candidate-v05.json `e805d2ee3a7af587021b25bd58bd88ad2dc9c60d81b2953ff269622f020cd621`;
  candidate-v02.json `718d115ed1dbc2785da29d632e05e588af34234ef27694b6a7535c2dda17aafc`;
  control-v02.json `bcb80df22a9fd0598e60e2dd576cf765fa399e603d94b420f1eafe9359301a42`.
  Process report SHA-256 under build/analysis/own-build-plan-v1/:
  candidate-process.json `a15e80dde6c01f5c95368df28be4dd6ad0c51ba11a11a82db5236352dc5be614`;
  restored-v05-process.json `17924cd103fc184eee86a50915eed901fdbb2bd8e5a4a27a823e298bf695ec60`.
  Executable tree hashes candidate/restored:
  `be3f4b65f4c6083f9ee2187666bcfcc33b93ce7b88b310045de9c19612fa9c4c` /
  `8a1f550abd3363abc754e06574b6853db5c5396b3cf688f350ef10587ca0c966`.
- README/BOTS/PLAN updated; 700 predeclared strength games completed, no post-result policy
  edits, automatic local commit per working agreement. No core/frozen/reference archive changes,
  arena upload or push. Current and final Player remain selected v05.

### 2026-09-16 — independent adversarial rollout v1 (predeclared)

- User requested a radically different, potentially stronger strategy. Implement a separate
  RolloutBot behind USE_ROLLOUT_POLICY=false, leaving EvalBot and all prior experiments untouched.
  Retain a small portfolio of routes for each player, simulate affordable construction and
  simultaneous sabotage over eight turns, and rank first actions by earned score plus a short
  terminal income tail. Opponent scenarios use two different retained construction priorities;
  sabotage reacts to each simulated pre-turn board and that player's own intended construction.
  Compare building priorities jointly with immediate sabotage versus WAIT; replan next real turn.
  This is a bounded model, not a prediction of actual opponent actions or full minimax search.
- Root route ranking only proposes actions; final comparison includes construction costs,
  neutral collisions, ink before income, and game termination through the shared core. Add a
  reusable core search workspace to avoid per-node allocations, with differential tests against
  Rules.resolve and cgValidate after the core change. Never score incomplete scenario coverage.
  Keep a legal independent fallback when the time limit interrupts the search.
- Fixed development screen: current?USE_ROLLOUT_POLICY=1 vs v05, 300 games / 150 generated
  paired maps, seeds 30000..30149. Then 200 games each vs frozen v03 and pressure-v1 on
  seeds 30000..30099. CG search budgets 486/36 ms, two workers, bot SEED=1, no other experimental
  flags. Reports build/bench/rollout-v1/. These are development maps, not the reserved confirmation
  pool. Record full resolved parameters and code/artifact hashes before strength games.
- Gate: >=55% and map-grouped Hoeffding95 lower bound >50% vs v05, no bot errors; older/style
  results remain separate checks. No post-result edits, tuning, sample extensions or automatic
  promotion. A passing screen can justify a separately declared fixed confirmation; a failed
  screen leaves current=v05. Record implementation corrections made before strength testing.
- Verify unit tests, default-off parity, both replay corpora, candidate-only merger and warmup
  parity, source size, then one diagnostic-enabled zero-grace four-game procCheck vs v04 for
  candidate and restored v05, serially without benchmarks. Preserve failures; no retries-to-green.
  Archive source/manifests under build/analysis/rollout-v1/ and commit the completed experiment.
- Before strength: **214 tests passed**, **264 arena + 27 boss replays exact**. Four core
  differential/storage tests and nine policy tests cover simultaneous collision and double
  disruption, income after ink, affordable cost packing, active/inactive path-buffer reuse,
  input isolation, endgame, future opponent sabotage, root WAIT, bounded coverage and expired
  search; merged-policy/warmup parity also passes. Existing OFF/frozen-v05 regressions pass.
- Initial implementation extended Pathing/Rules, triggering 17 archive-identity test failures.
  Corrected BEFORE any strength runs: keep both existing classes bytecode-identical and add only
  SearchState, which calls the canonical Pathing.shortestPath into retained buffers and delegates
  placements/disruption/scoring/termination to Rules. This uses one BFS per directed pair instead
  of the existing allocating per-destination updater. No historical archive hashes were changed.
  Also fixed a Java test declaration and retention of the better duplicate-prefix continuation.
- One serial work audit on ALL 110 previously saved public states (36 ms, fresh instances,
  no recorded enemy actions supplied): **110/110** had a complete action/scenario comparison,
  **660** complete comparisons, **10,423** simulated turns; mean **24.224 ms**, max **36.134 ms**.
  No illegal/skipped output or input mutation. This is work-cost evidence, not a strength test
  or process deadline guarantee. `build/analysis/rollout-v1/cost.json`; no parameter adjustment.
- Candidate source **56,008 / 100,000 chars**, SHA-256
  `d7f259ca8ecf70757458e69282f89c2597151e5e895c8d3753490707e1c92c8f`.
  Source, complete parameter manifest and source/class identities are archived under
  `build/analysis/rollout-v1/`; base revision `7439062`. Candidate process check is run before
  strength so an unusable artifact is identified early; the restored-v05 check follows the screen.
- Candidate strict process sample: **0/4 problem games**, first response max **579.574 ms**,
  later max **40.587 ms**, no grace. The wrapper initially could not open its Gradle lock when
  invoked in a composite shell command; no game started on that invocation. The standalone
  command ran the single declared process sample. No process retries or deadline adjustments.
- **Protocol correction during the direct screen:** the chosen development seeds 30000..30149
  overlap the previously reserved confirmation-v1 range (30000..30499), contrary to the pool
  statement above. Detected after 100 completed games; do not erase/restart/relabel the run.
  Keep all 700 declared development games and unchanged candidate/configuration. No tuning
  occurred, but this screen must not later be reused as independent confirmation. Retire v1 as
  a reserve while preserving its original file. Register confirmation-v2, seeds 90000..90499,
  after inspecting the existing bench report seed ranges (40000 also has a prior smoke run).
  No games on v2 here; future use still requires exact map-hash exclusion against development
  reports. README/benchmarking/pool-status docs point to v2. This is a protocol mistake, not an
  engine or strategy correction; the fixed screen's paired outcome remains development evidence.

### 2026-09-16 — independent adversarial rollout v1 (outcome)

- Fixed direct development screen: **170-130-0, 56.67%** vs v05, 300 games / 150 paired
  generated maps, seeds 30000..30149. Map-grouped Hoeffding95 **45.58-67.76%**: practical
  threshold reached, confidence gate **not passed**. P0 84/150, P1 86/150; mean score difference
  **+277.17**, length **72.36** turns, 213/300 early closures. No bot errors; search mean A/B
  **19.40 / 5.58 ms**, max **55 / 34 ms**, slow **2 / 0**. The two in-process slow moves are
  not process-deadline measurements or an identified cause. Accepted sabotage **84.42 / 84.26%**.
  Elapsed **283.089 s**. The separate per-game Wilson interval is not used to bypass the
  map-grouped gate. No extension, strategy/parameter edits or confirmation after this result.
  Report `build/bench/rollout-v1/candidate-v05.json`, SHA-256
  `aed6dff387b9aa820795bf7537c17a76cc00e9db689c23dc7cacfcfe565c65fb`.
- Older-opponent check vs frozen v03: **137-63-0, 68.5%**, 200 games / 100 paired maps,
  map-grouped Hoeffding95 **54.92-82.08%**. P0 69/100, P1 68/100; score difference **+961.75**,
  length **79.115**, 103/200 early closures. Search mean A/B **17.02 / 4.79 ms**, max **71 / 39**,
  slow **2 / 0**, no errors. Accepted sabotage **69.75 / 83.85%**, placement **52.76 / 51.14%**;
  elapsed **180.156 s**. All 100 map hashes match the direct screen. This opponent-specific
  pass does not replace the failed v05 confidence gate or establish superiority to v05 through
  an unmatched historical percentage. Report `build/bench/rollout-v1/candidate-v03.json`, SHA-256
  `dde968314f4cb755e864c31a6622986e9aea4e99c3225feb0e8c5b6139736abe`.
- Style check vs pressure-v1: **149-51-0, 74.5%**, 200 games / 100 paired maps, Hoeffding95
  **60.92-88.08%**. P0 79/100, P1 70/100; score difference **+1118.315**, length **78.265**,
  109/200 early closures. Search mean A/B **19.486 / 3.855 ms**, max **196 / 296 ms**, slow
  **14 / 6**; no bot errors. Sabotage **61.29 / 94.42%**, placement **57.55 / 44.48%**;
  elapsed **190.677 s**. Large in-process outliers affect both policies; no host/JIT/GC cause
  is established. All maps and candidate parameters match the other runs. Report
  `build/bench/rollout-v1/candidate-pressure.json`, SHA-256
  `f61127d1fade3557f42a4e5082d71b047b21c527a61b215fba0f288177a00a7f`.
- **Decision: retain the candidate, USE_ROLLOUT_POLICY=false; no v06 or promotion.** Its point
  estimate is promising against v05 and it wins both separate reference checks, but the v05
  confidence gate was not passed. These 700 games complete the fixed development experiment;
  no additional games, independent confirmation or parameter tuning. Reference results cannot
  establish improvement over v05 through historical rates. Candidate placement **54.68%** vs
  v05's **93.33%** in the direct match is a behavior difference, not proof that waiting causes
  the gain. Fixed route portfolios, four-region sabotage shortlist, two similar opponent scenarios
  and constant terminal-income tail remain limitations, detailed in rollout-policy.md.
- Restored frozen-v05: **52,219 chars**, source SHA-256
  `1b7ba04b299e7e31af66c8d2de45654cec2a78b80233dac673402aae825214a3`.
  Policy/existing core unchanged; new unused Params fields and SearchState are included by the
  merger. Its single diagnostic-enabled strict process sample **FAILED 4/4**: 903182120 P0
  turn 6, 903182383 P1 turn 17, 903182464 P0 turn 0, 903182662 P1 turn 0. First measured
  responses **866.880 / 827.068 ms** in the two games that started exceed the 800-ms headroom
  target. First input sends **344.605 / 266.768 / 512.240 / 1178.099 ms**, almost entirely
  flush (**344.051 / 266.132 / 511.868 / 1177.608 ms**). Last two have no response timestamp;
  the last send alone exhausts the 1000-ms deadline.
- Later failures: turn 6 send **2.076 ms**, response **134.928**, consumer **235.420**; turn 17
  send **176.552**, response **53.240**, consumer **176.605**, preparation **18.203** and flush
  **158.168 ms**. Reader timestamp can precede completion of host flush. Host-thread CPU and
  host GC counters are zero at their available resolution; this neither identifies scheduling,
  pipe or child-runtime cause nor proves no CPU use. No competing strength jobs during process
  checks, runtime changes or retries-to-green. Earlier startup trouble remains unresolved, now
  with another instrumented failure. Candidate's separate 0/4 failure sample does not isolate
  a strategy/artifact cause. Final Player is v05 **without a fresh process-readiness pass**.
- Process hashes under `build/analysis/rollout-v1/`:
  `candidate-process.json` = `2fb0c3efc1324c408cca7b2d350e80517709cc902250b9ef464b875b51f5f7e0`;
  `restored-v05-process.json` = `cfbb607079c4113240ab1ba41e08363fd5e57680e9eb191919ad2fdb134c556c`.
  Cost report = `df359b4c90e25c109a2920516535fad482235e410ef3adb176ad4dc3761711af`.
  Candidate/restored executable tree hashes:
  `fcadccc0d2df020b57d026cc1cbf0fb23987dbd3d02d5bf8d7467d74aef279e6` /
  `41de201baf91ea8ec6c2ead8ff0ac423deb78049cab1e468e9fe49f1de222f83`.
  Both sources and manifests archived beside reports; all candidate source/class hashes were
  verified unchanged across strength runs. README/BOTS/PLAN, policy and pool status updated;
  completed unpromoted experiment committed locally, including failures. No upload or push.

### 2026-09-16 — rollout fixed 2x1000 confirmation (predeclared protocol)

- User explicitly requests two 1000-game runs after the completed 700-game screen. This is a
  new fixed experiment, not an adaptive extension of its 300-game direct comparison. Hypothesis:
  unchanged rollout-v1 is stronger than frozen v05 on fresh maps. Source revision `13aee8e`;
  candidate `current?USE_ROLLOUT_POLICY=1`, reference `v05`, original full effective parameters.
- Two sequential runs, each 500 distinct generated maps with both sides: confirmation-v2 seeds
  **90000..90499**, then new confirmation-v3 seeds **91000..91499**. CG timing **486/36 ms**,
  **two workers**, RNG SEED=1. Complete both regardless of intermediate win rate; no parameter,
  source, clock or sample-size changes, and no retries to improve results.
- Primary outcome is the pooled fixed **2000 games / 1000 distinct map groups**, using the
  existing map-grouped Hoeffding95 rule (>=55%, lower bound >50%, zero bot errors). Also report
  each 1000-game result, its unchanged gate and side/timing metrics separately. Do not choose
  the better series or include the old development games in confirmation. A statistical pass
  alone does not authorize promotion or establish process timing readiness.
- Before play, archive all existing benchmark map identities in
  `build/analysis/rollout-confirmation-2x1000/prior-maps.json`, with original report paths/hashes.
  Run 1 excludes that union; run 2 excludes it plus run 1. The runner checks exact map hashes
  before play. Source/class provenance and unchanged v05 Player hash are saved beside it.
  Reports: `build/bench/rollout-confirmation-2x1000/run-1.json` and `run-2.json`; refuse to
  overwrite existing artifacts. Revalidate downloaded arena/boss games before these benches.
- Keep the earlier failed restored-v05 process report; this request is for strength samples,
  with no packaging changes or new process-readiness claim. Default policy and Player remain
  v05. Record both outcomes, consume both pools, update BOTS/PLAN and commit the experiment.

### 2026-09-16 — rollout fixed 2x1000 confirmation (outcome)

- Fresh validation before play: **264/264 arena**, **16/16 bot** and **5/5 bot/raw** downloaded
  replays reproduced exactly. No core or strategy edits. Prior-map index contains **3076**
  identities from **57** reports/indexes; one old comparison summary without gamesPerMap was
  skipped, with both its original source reports included. Index SHA-256
  `275b0cb81008c494434f14aad69611a914f28d84113e1df5e4c730f0bc6797c1`.
- Run 1, confirmation-v2: **605-395-0, 60.5%**, **1000 games / 500 distinct paired maps**,
  map-grouped Hoeffding95 **54.4264-66.5736%**; individual statistical gate **PASS**. P0
  **308/500**, P1 **297/500**. Mean score difference **+338.303**, length **74.134** turns,
  **657** early closures. No bot errors. Search mean A/B **19.0412 / 5.7730 ms**, max
  **61 / 39 ms**, slow **2 / 0**. Accepted placement **53.9496 / 91.8634%**, sabotage
  **84.2839 / 83.3221%**. Elapsed **953.064 s**. These in-process outliers are not process
  deadline measurements. Full effective parameters and source identities match the original
  development candidate. Run 2 started unmodified as predeclared, regardless of this pass.
  Report `build/bench/rollout-confirmation-2x1000/run-1.json`, SHA-256
  `85835d5bbb4c70088de2971dfaec3f999088f6caf048d41724d16c985a5dacad`.
- Run 2, confirmation-v3: **625-375-0, 62.5%**, **1000 games / 500 distinct paired maps**,
  Hoeffding95 **56.4264-68.5736%**, individual statistical gate **PASS**. P0 **319/500**,
  P1 **306/500**. Mean score difference **+377.823**, length **73.862** turns, **665** early
  closures. No bot errors. Search mean A/B **21.8514 / 7.7070 ms**, max **515 / 461 ms**,
  slow **307 / 180**. Accepted placement **53.7029 / 91.3338%**, sabotage **85.5704 / 82.5350%**.
  Elapsed **1127.641 s**. Report `build/bench/rollout-confirmation-2x1000/run-2.json`, SHA-256
  `572150fb6952df2b21057bd31c6c175be6d318e21c2e37e61001b8cab5999934`.
- **Primary pooled result: 1230-770-0, 61.5% over 2000 games / 1000 distinct map groups**,
  Hoeffding95 **57.2053-65.7947%**, statistical gate **PASS**. P0 **627/1000**, P1 **603/1000**;
  mean score difference **+358.063**, length **73.998**, **1322** early closures. No bot errors.
  Total benchmark time **2080.705 s** (34m 40.7s). Aggregate report
  `build/bench/rollout-confirmation-2x1000/combined.json`, SHA-256
  `3416e572775d6834727215886d15f9b3f2db83546f2465f397025671540b4eea`.
- Rechecked every source/resource/class hash, full effective A/B parameters against the original
  development report, both manifest identities, all excluded-report hashes, exact seed ranges,
  map counts and side pairing. The pools are disjoint from each other and all supplied prior
  maps. Player remains unchanged v05, SHA-256
  `1b7ba04b299e7e31af66c8d2de45654cec2a78b80233dac673402aae825214a3`.
- Timing limitation: run 2 has slow moves in **35 candidate games / 24 reference games**, **36**
  games total (25 map pairs). Preserve every result; do not filter slow games or retry. Both
  policies experienced large outliers, but this does not identify host scheduling, GC or search
  as the cause. The in-process referee records slow moves without enforcing process deadlines;
  timed action selection can also change under contention. The win-rate gate passes on these
  measured games; neither that interval nor zero bot errors establishes arena timing safety.
  Detailed descriptive audit: `build/analysis/rollout-confirmation-2x1000/run-2-timing.json`.
- Postprocessing correction: the first temporary aggregate selected PowerShell integer Max/Min
  overloads while clipping the interval, yielding invalid [1,1]. Archived it outside the bench
  corpus as `combined-invalid-ci.json`, along with the original script, before correcting to
  explicit doubles. Both Java-generated run reports are untouched. The corrected aggregation
  reconstructs both canonical run intervals and checks the pooled width and bracketing; no
  change to the confidence method or decision rule, no games rerun.
- **Decision: strength gate passed in each full run and the predeclared combined result.**
  This confirms an advantage over frozen v05 on these held-out generated maps at the nominal
  budgets. Keep USE_ROLLOUT_POLICY=false/current=v05: the user requested measurement, not
  promotion. Earlier v03/pressure-v1 checks remain development evidence. No new submission,
  procCheck, strategy edits, v06, upload or push; unresolved timing evidence remains open.
  Both confirmation-v2/v3 pools are consumed. Register untouched confirmation-v4 seeds
  **92000..92499** for future fixed candidates, still requiring all prior map-hash exclusions.
  Update BOTS/PLAN/policy and pool documentation; commit this completed experiment locally.

### 2026-09-16 — rollout process readiness v1 (predeclared protocol)

- User authorizes investigating timing of the ready submission after the fixed 2x1000 strength
  pass. Hypothesis: unchanged RolloutBot can complete real stdin/stdout games within strict
  1000/50-ms deadlines; compare frozen-v05 to distinguish candidate-specific failures from
  shared runner/host symptoms. Base `3bff099`, no strategy, parameter or clock changes.
- Fixed readiness sample: **42 games per artifact**, sequentially **candidate then v05**, each
  vs **frozen v04**, grace **0**, IO diagnostics **off**. Twenty-one maps, exactly three per
  height 14..20; include 903182120/383/464/662 (earlier process sample), fill each height by
  descending pair count, descending width, filename, excluding duplicate initialization hashes.
  This deliberately stresses complexity and is not a random strength sample. The odd-size
  sorted pool is visited twice: games g and g+21 use the same map and opposite sides.
  No concurrent benchmarks or other heavy jobs started by the agent. Temporal order remains
  a limitation: the control is not a simultaneous measure of host conditions.
- Pool and canonical initialization/replay SHA-256 identities saved under
  `build/analysis/rollout-process-v1/`; pool.json SHA-256
  `5f2231e153cb6362fb39821027d8b9d19645ab833f721945d5acc309c84d85f6`.
  Preserve source, resolved manifest, executable hashes and per-turn reports for both artifacts.
  Rebuild each through submit; verify candidate/v05 hashes against the tested strength-era
  artifacts. Final selected/default submission stays v05; this is not promotion or upload.
- Readiness sample passes only with **zero problem games and zero nominal overruns**. Report
  first/later response percentiles/maxima, missing responses, input send and consumer timing;
  assess headroom separately (first <=800 ms, later <=45 ms), not as grace or a changed deadline.
  Include every attempted game, including startup failures; complete both fixed samples even
  after a failure, unless the harness cannot continue. No extra games to obtain a green result.
- If either strict sample fails, run a separate **10-game IO-diagnostic sample per artifact**
  on the four mandatory maps plus largest selected map 903510176, both sides through the same
  odd-pool method, candidate then v05, same opponent and deadlines. This is mechanism evidence,
  not a replacement readiness sample. Existing diagnostics separate host preparation/write/flush
  and host CPU/GC; they cannot isolate child search/JIT/GC or prove a scheduling cause. Further
  instrumentation, if the observed failure requires it, must be declared and retained separately.
- Record all results and limitations, update BOTS/PLAN and commit the completed experiment.

### 2026-09-16 — rollout process readiness v1 (outcome)

- Both fixed samples completed on **21 identical maps, both sides, 42 games per artifact**.
  External Player, frozen-v04 opponent, strict **1000/50 ms**, **zero grace**, IO diagnostics
  **off**, one game at a time, candidate then control. No strategy/core/clock edits, tuning,
  extra strength games or retries. Each submit compile-check passed. Existing source and class
  identities match the previously tested artifacts; pool hashes and map/side schedules verified.
- **Candidate PASS: 0/42 problem games, 0 timeouts, 0 nominal overruns, 3190 OK responses**
  (42 startup + **3148 later**), none missing. First response max **662.5903 ms**, median
  **601.0143**, p95 **656.5423**. Later median **29.6447 ms**, p95 **38.1741**, p99 **39.0466**,
  max **47.8570**. Percentiles use nearest rank; startup is never pooled with ordinary turns.
  Startup input send max **153.0560 ms**, later send max **4.0486 ms**. The sample passes the
  strict deadline rule, but its separately declared 45-ms headroom target is missed twice:
  903584040 P0 turn 4 **45.3283 ms** (send 0.1763, consumer 45.4774), and 903188024 P0 turn 8
  **47.8570 ms** (send 0.0890, consumer 47.9334). Worst observed remaining margin **2.143 ms**.
  Small send times do not distinguish child work from delayed reader scheduling.
- **v05 PASS: 0/42 problem games, 0 timeouts, 0 nominal overruns, 3115 OK responses**
  (42 startup + **3073 later**), none missing. First response max **668.3051 ms**, median
  **570.3037**, p95 **647.6124**. Later median **6.6631 ms**, p95 **34.1837**, p99 **37.6252**,
  max **40.0882**. Startup send max **154.1543 ms**, later send max **4.6886**. Both headroom
  targets met in this control sample. One consumer wake-up is later than 50 ms: 903505433 P1
  turn 23, response **32.5729 ms**, consumer **66.5841**, send **0.2136**. The already queued
  answer is correctly accepted by its reader timestamp. This is evidence of a host-side
  consumption delay on this turn, not an explanation of the historical 515/461-ms outliers.
- Conditional 10-game IO diagnostics were **not triggered**, since neither fixed sample failed.
  The old large bench/process stalls did not recur here; their cause is **not established**.
  Do not claim they were fixed or caused by Java GC/JIT, host scheduling, or either strategy.
  These are local artifact-readiness measurements; sequential runs and different resulting
  game states limit causal comparisons, and success does not guarantee the CG host or rare
  failures. Candidate uses more of the available time and has two narrow-margin observations.
- Source/class hashes unchanged: candidate source
  `d7f259ca8ecf70757458e69282f89c2597151e5e895c8d3753490707e1c92c8f`, classes
  `fcadccc0d2df020b57d026cc1cbf0fb23987dbd3d02d5bf8d7467d74aef279e6`;
  v05 source `1b7ba04b299e7e31af66c8d2de45654cec2a78b80233dac673402aae825214a3`, classes
  `41de201baf91ea8ec6c2ead8ff0ac423deb78049cab1e468e9fe49f1de222f83`.
  Both source/manifest/executable snapshots are in `build/analysis/rollout-process-v1/`.
- Reports in that directory: `candidate-strict.json` SHA-256
  `f1ebf5ca1e11cddb633a7c16418e9cb24d6bd7f30082cfac8a0ef516776892d9`;
  `v05-strict.json` = `4af48ab7d9faf563b504152222c345c4b2b3383f0f0bb14c671faccd75e15a9c`;
  `summary.json` = `b9140be60c532872d76298c81e3777718148de1ae7b9abfe5747fce92df2e34b`.
  All raw turns, maps and original reports retained. The 30/42 vs 26/42 wins against v04 are
  incidental to this selected timing sample, not a new population-strength comparison.
- **Decision:** fixed local timing check passed for unchanged RolloutBot and v05. Together with
  the prior 2x1000 strength pass and older-opponent screens, this supplies local evidence for
  considering a trial on the arena; no promotion/upload requested here. Leave default/current
  v05 and final `build/submit/Player.java` restored to v05, now with a fresh strict 42-game pass.
  The separately archived `Player-candidate.java` is the exact submit-generated, tested candidate.
  No shipping code or parameter changes; update BOTS/PLAN/policy and commit the experiment.

## 2026-09-16 — v06 selected and packaged; final process check failed 1/42

- **Scope/result:** user-authorized promotion of the already confirmed RolloutBot, without tuning.
  Freeze `RolloutBotV06` plus a complete snapshot copied from confirmation run 1. Register v06,
  set the rollout selector true for current/MainBot and mark v06 selected in the catalog.
  Older frozen files/snapshots remain unchanged. The snapshot loader supplies the original false
  selector for older snapshots predating that field; named archived engines remain isolated.
- **Identity:** working RolloutBot source remains SHA-256
  `80091279236072ccfd390ea1b4a78cea10761dbc3b149e646e0ab4cff0b7b60f`.
  Frozen v06 differs only by package/class/constructor/name. Full current/main/v06 parameters
  match the archived 13aee8e confirmation record, guarded by an independent test fixture.
  Multi-turn decision parity covers current, frozen v06 and MainBot; generated default and
  frozen submissions also match, including isolated warmup. Core and CGRunner are unchanged.
- **Strength evidence carried forward:** 605-395 and 625-375 vs v05; pooled 1230-770 (61.5%),
  map-grouped Hoeffding95 57.2053-65.7947%, 1000 distinct paired maps, both individual gates pass.
  Prior older-opponent screens: 68.5% vs v03 and 74.5% vs pressure-v1, 200 games each. No new
  strength sample, tuning, reserve consumption or reinterpretation of old timing outliers.
- **Integration verification:** 217 tests pass; `cgValidate` reproduces 264/264 arena replays.
  Initial test run had ten failures from old implicit-current selectors, version-name suffix
  expectations and a method-name assertion shared by both policies. Corrected those tests to
  preserve their original coverage; no policy fixes. Legacy diagnostic tools explicitly choose
  EvalBot. README and policy documentation distinguish historical recipes from the new default.
- **Packaging:** FileMerger supports frozen v06 and embeds full resolved parameters even in a
  default build. Frozen-v06 artifact: 55,991 characters, SHA-256
  `19de918ecced53a04f5df3c41dcd129d466d085a1854335e6ebc669fe1b619c8`.
  Final ordinary-submit artifact: **56,007 characters**, SHA-256
  `7f4d94e40e5252d09b86df6e1b8c208e92c86d98a521d858eaf182a9178c2c9c`, classes-tree
  `2cb6b2830feec19e99d3656a685489c57e98d31d7f6d96de057f07827fbcf0c8`.
  Exact source comparison with the prior tested candidate finds only the boolean selector
  default false -> true; its embedded effective parameters are identical. Both builds compile.
  Gradle launcher cache access was denied before two task starts; rerun with cache permission
  started the tasks. No game sample was started or discarded by those launcher failures.
- **Fixed final process result: FAIL, 1/42 problem games**, 21 maps each on both sides,
  serial vs v04, zero grace, no IO diagnostic instrumentation. 3147 responses: 42 first turns,
  3105 later. Startup max **671.9882 ms**, p95 **646.1796 ms**; later p95 **38.1085 ms**,
  p99 **39.8142 ms**, max **50.462 ms**. Three later responses exceed the separate 45-ms
  headroom target: 903185759 P1 turn 9 (46.6145 ms, send 9.7123), 903188024 P0 turn 8
  (46.2100 ms, send 0.0742), and the failure below. Incidental 30/42 wins are not strength evidence.
- **Failure inspection:** map **903510176, P0, turn 73**, deadline 50 ms, send **0.1640 ms**,
  reader response **50.4620 ms**, consumer **50.5301 ms**, status TIMEOUT. The reader timestamp
  itself is late; this is not merely late consumer wakeup with an on-time response. Input send
  does not explain this overrun. Existing timings do not separate child search/GC from host or
  reader scheduling, and do not identify the cause. The prior candidate 0/42 result remains valid
  for that earlier sample but does not turn this final sample into a pass. No retries or grace
  changes; exact failed artifact, classes, manifests and all games retained.
- **Evidence:** `build/analysis/v06-promotion/final-process.json` SHA-256
  `94fbd6aae09200099eae33214e948b93852ebc1107d3249bc2d757770ea100df`;
  `summary.json` SHA-256 `4f62c4f0129dc2e807d9efe9b6cf28f2994f05bb6d6c7f982eb3f81803caa4f0`.
  `Player-current.java`, `manifest.json` and `classes/` preserve the final executed artifact;
  `Player-frozen-v06.java` and `frozen-v06-manifest.json` preserve the alternative frozen build.
- **Decision:** v06 is frozen and selected locally as requested; final Player contains v06.
  Record packaging as complete and the final strict timing check as failed, leaving timing
  readiness explicitly open. Do not claim submission readiness, change the confirmed policy
  without another experiment, or erase the failure by rerunning. No arena upload or push.
  Commit the completed promotion work and its failed check under the standing working agreement.

## 2026-09-16 — user-reported v06 arena gain and 30-game boss loss diagnosis

- **Context:** user uploaded the current bot and reports rank **153/645**, versus **270** for
  v05 before upload. User assigns the first 15 of 30 saved boss games to v05 and the last 15
  to current/v06. Record that attribution explicitly; IDE agent -1 does not carry a verified
  source hash. The arena rank is a user snapshot, not a fetched leaderboard or controlled effect.
- **Fixed sample:** every canonical JSON directly in `replays/cg/bot`, ordered by game id;
  v05-labelled 904021106..904125203, v06-labelled 904125321..904148315. All 30 feature aabzac
  P0 vs Boss 4 P1, positive boss agent 6740207 identified through `arenaboss` and slot index.
  There are **30 distinct initial maps**, with no paired overlap between cohorts. Raw fallback
  captures in the nested directory are outside this explicitly counted sample, not failed games
  discarded from it. **30/30 replays exact; no failures or skipped commands** for either player.
- **Hypothesis/method:** inspect losses before choosing another feature. Reuse validated
  `CgPlayerStudy.analyze` for accepted placements, income and one-turn recorded-action
  counterfactuals. New tools-only `cgBossLossStudy` enforces cohort counts and preserves output;
  `cgRolloutBossProbe` verifies input hashes and tests all 1082 new-cohort pre-turn observations
  with fresh frozen v06, NOISE=0 and 60,000-ms allowance. The latter is a completed bounded-work
  mechanism audit, not reconstruction of CG timing/RNG or a population-strength benchmark.
  It calls frozen candidate generation/target selection, using actual enemy commands only to
  assess coverage afterward. Route completion and all scoring/pathing use core.
- **Descriptive result:** v05 **2-13**, v06 **5-10**. Own accepted cells never subsequently
  earning: **970/2054 (47.22%)** vs **398/1549 (25.69%)**; boss **205/1432 (14.32%)** vs
  **230/1576 (14.59%)**. Different maps and the small samples prohibit treating these changes
  as controlled effect sizes. This supports investigating construction quality, not a new gate.
- **Primary mechanism:** at 904148181 t0, core exposes a useful route costing 4 paint (+4 own,
  +4 net income if completed), while all six noise-free retained routes cost >=18 and the chosen
  one costs 24. Its first command matches the replay. Recorded first income: t16 vs boss t1;
  pre-turn t16 score 0-392, final 1899-3387. At t5 a 3-paint route is absent while the minimum
  retained cost is 17. Within the first 20 turns, zero own income plus an omitted cheaper useful
  <=6-paint route occurs in 48 observations from all 10 losses AND 25 observations from all five
  wins. Repeated, post-selected observations establish a recurring portfolio restriction, not
  causation or guaranteed value after opponent interference. Completed-network ranking can
  exclude cheap routes before affordable rollout; a deeper rollout cannot consider absent roots.
- **Alternative hypothesis checked:** first-turn sabotage target coverage is **779/917 (84.95%)**.
  In losses, **21/23** recorded disruption turns with >=30 immediate net-point impact are already
  covered. Example 904147739 t22: actual/predicted target31, prior instability3, no-enemy-hit
  one-turn delta +113; exposed bypass still costs4. These oracle deltas reset to actual states
  and are not additive final-score gains. Simple prior-target persistence matches **576/678**
  eligible attacks; current two scenarios cover **605/678** on the same slice. Construction
  exact-set coverage is only **118/673** nonempty boss builds, but different actions can be
  strategically equivalent. Neither number alone supports widening the search. All 1082 probes
  complete at least one comparison; own recorded placement sets are covered in 971 states.
- **Evidence:** `docs/boss-v06-loss-analysis.md` records interpretation, limitations and commands.
  Full reports under `build/analysis/boss-v06-losses-v1/`: `cohorts.json` SHA-256
  `7ab85b8bdb849fbcac9985a0a526f557f477c4a8e35dd1194fd86e93722c0876`;
  `rollout-probe.json` `c2569c38aa14f7a6e2c612f115af7c00cb27a4e8d00012140a84da26120661a4`;
  `summary.json` `9e131306820080e168badba63916179501dc8a253f5193c4989b59694ec7a020`.
  Reports retain raw-input, state and probe-class hashes, parameters and observation boundaries.
  Two compact fixtures (904148181 t0, 904147739 t20) preserve initialization and prior history
  separately from same-turn diagnostic labels. **220 tests pass**, including three probe tests
  for exact fixture restoration, terrain costs, net income, both sides and input isolation.
  Initial new-test compilation needed an explicit checked-exception declaration; corrected,
  no runtime or policy defect. No core, bot, Params, frozen-version or submission changes.
- **Decision:** next single-change experiment should reserve one existing route slot for a
  bounded cheap positive-net-income candidate while own income is zero, leaving selection to
  the existing rollout. Default-off, no forced defense, no wider/deeper search or sabotage
  retuning in the same experiment. Require fresh paired screen vs v06 and an older/style check
  before any confirmation/promotion. The boss sample is development evidence only. v06 stays
  selected; final Player hash remains `7f4d94e40e5252d09b86df6e1b8c208e92c86d98a521d858eaf182a9178c2c9c`.
  No new games or upload by the agent. These failure-free boss records do not explain or erase
  the separate local packaging timeout; keep that investigation open. Commit the diagnostic work.

## 2026-09-16 � corrected boss cohorts and rendered collection

- User corrected the boundary: **16 v05 / 14 v06**, first v06 **904146009**; game 904125321
  belongs to v05. The preceding entry used the initial 15/15 report and is superseded for all
  cohort-dependent totals. Preserve its v1 files; new v2 reports recompute all 30 games and
  re-probe the 1002 v06 states with the same frozen noise-free policy.
- Correct outcomes: **v05 2-14**, **v06 5-9**. Own cells never earning: **1114/2235 (49.84%)**
  versus **254/1368 (18.57%)**; unpaired descriptive evidence. Quick-route gap in the first 20
  turns: **46 states / 9 losses**, **25 states / 5 wins**. Boss-target coverage **707/837**;
  high-immediate-damage loss slice remains **21/23**. These corrections preserve the original
  mechanism hypothesis but do not establish causality or a promotion gate.
- Parser now recognizes positive-ID bosses via `arenaboss`, names them via `nickname`, and
  preserves authoritative `agents.index`; legacy -2 still works. Regression covers both slots.
  All **30/30** validated and rendered to `replays/rendered/cg/bot/`. Local collection
  `replays/rendered/cg/boss-30.html` shows the corrected groups; browser checked opening and
  terminal frames, correct score and Boss 4 label. Canonical replay inputs unchanged.
- Setup corrections before any strategy match: a parser insertion was initially in the wrong
  scope (compile failure); combining rendering and analysis with their shared `-Pout` created
  a directory instead of an analysis file. Moved only the newly generated files into the correct
  render directory, then ran tasks separately. No prior reports were overwritten. First test
  used `gameId` instead of fixture `sourceGameId`; corrected. All **224 tests** then passed and
  **264/264 arena** replays reproduced exactly. Nickname regression rerun after the final metadata fix.
- Quick-income experiment protocol, default-off implementation and checks are documented in
  `docs/quick-income-experiment.md`; results will be appended after the fixed samples.

## 2026-09-16 � quick-income route-slot experiment, not promoted

- **Hypothesis:** retain one cheap positive-own/net-income completion among existing six routes
  at zero own income; the rollout decides whether to build it. Default-off
  `USE_ROLLOUT_QUICK_ROUTE`, limit `ROLLOUT_QUICK_MAX_PAINT=6`. Reuse bounded path proposals,
  minimum paint / maximum net-gain tie-break, canonical direction. Replace a matching first
  prefix or final slot; no extra route query, widened search, sabotage or opponent-model change.
- **Predeclared protocol:** `docs/quick-income-experiment.md`; fresh development pools 40000..40149
  (300 games vs frozen v06) and 41000..41099 (200 vs v05), both sides, two workers, CG 486/36-ms
  timing, RNG SEED=1. Exclusion index covers 4074 previous map identities; second sample also
  excludes the first. All 250 new maps distinct. No parameter adjustment, extension or reserve use.
  A PowerShell unquoted pool argument failed before any game; quoted the path and started once.
- **Main result:** **160-134-6 (54.33%) vs v06**, map-grouped CI **43.24�65.42%**, zero errors.
  P0/P1 wins 75/150 and 85/150; avg score difference +153.6. Neither practical nor confidence
  gate passed. A/B average search 16.71/16.72 ms, maxima 58/40 ms, slow moves 2/0, retained.
- **Older check:** **124-76 (62.0%) vs v05**, CI **48.42�75.58%**, zero errors; P0/P1 wins
  68/100 and 56/100. A/B averages 18.29/5.17 ms, maxima 96/36 ms, slow 2/0. This separate panel
  does not estimate an improvement over v06 against v05; no matched control was declared.
- **Mechanism and safety:** captured 904148181 t0 now retains a 4-paint candidate on both sides
  within the same query and simulation bounds. Negative net income, >6 paint, inked routes,
  income-start/reset and expired-turn fallback covered. Disabled policy matches frozen v06
  over evolving boards. Merged candidate parameters/decisions and isolated warmup also tested.
  **225 tests, zero failures**; **264 arena + 30 boss replays exact**, no core/frozen changes.
- **Exact artifact checks:** candidate source 57,404 chars, SHA
  `8fb0831f44a7f0ca98ada59371fc2dd7537d1743ac711f78c21a8d7e2023dbfd`;
  fixed strict process sample **0/8 problems**, 583 turns, first/later max **747.670/47.118 ms**,
  one later response >45 ms. Restored default-off source 57,405 chars, SHA
  `5807068e5d50c7bb3acd395866b74f8c9a972f7c8b19fc323dfda18cff413266`;
  same fixed sample **0/8 problems**, 639 turns, first/later max
  **718.680/45.982 ms**, 1 later response >45 ms.
  Sequential local samples, no retries; neither proves CG-host safety or explains the old v06
  50.462-ms failure. Keep that historical issue open.
- **Decision:** not promoted; flag remains false, selected policy v06, no v07 or upload. Preserve
  this inconclusive experiment and investigate retained-candidate selection/value/clock coverage
  before another strategic change. Success on one fixture is not population-strength evidence.
- **Evidence:** full bench reports under `build/bench/quick-income-v1/`, artifacts/process reports
  under `build/analysis/quick-income-v1/`, complete compact parameters/map outcomes/hashes in
  `docs/evidence/quick-income-v1.json`. Main report SHA
  `669168ea5d6f57f274627f15d4e3063e25c0d82fcf7691418951e2b32a5c35fb`;
  older-check SHA `ce0b084f8f114212d34207873a498a64ee01a6196aaf0c16432b63d0ef1c4aea`.
  Commit completed experiment and corrected/rendered boss analysis under the standing agreement.

- **Final process-coverage audit/correction:** ProcessCheck map index `i`, player `i%2` repeated
  the same sides on the even four-map pool; the initial eight-game samples did not implement
  the intended both-side coverage. Preserved them and predeclared only four missing combinations
  per exact artifact (904146009 P0, 903510176 P1, 904148181 P0, 904147739 P1), using numbered
  map copies with unchanged contents. Supplements **0/4 problems each**, source AND executable
  hashes match originals. Combined **0/12 problems each**, 8 distinct map/side combinations;
  candidate 913 turns, default 947 turns. No failed sample discarded or retried.
  Both supplemental reports and protocol preserved. README/PLAN warn about checker scheduling;
  BenchmarkRunner's 500 strength games already paired correctly. Final Player restored to the
  same default-off hash, no policy changes after benchmark.


## 2026-09-16 - adopt practical-300-1000-v1 at the user's request

- User selected fixed practical stages instead of the old strict confidence gate: direct
  300 games at >=60%, otherwise separate fresh 1000 at >=55%; then matched candidate/control
  vs one common older/style opponent, 300 each at >=+3 percentage points, otherwise fresh
  1000 each at a nonnegative candidate-control difference. Draws count half; ties pass only
  the final matched comparison. Stop promotion evaluation on direct failure.
- Updated the user skill codingame-bot-contest (entrypoint and benchmarking reference),
  AGENTS, the strategy rule, README, BOTS header, PLAN and benchmark documentation. The
  historical strict-CI verdict remains in old evidence and as a labelled report diagnostic;
  it no longer controls the active policy. No overall 95% guarantee is claimed.
- Added exact half-win thresholds to the benchmark report and a separate report checker for
  complete stage outcomes, unchanged parameters/conditions, distinct maps, matched seeds,
  fresh continuations and all stopping branches. Neither tool promotes a bot or uploads.
- Verification: 226 Java tests pass, six Python report-checker tests pass, installed skill
  passes quick_validate; 264/264 arena replays exact. Initial validation needed access to
  the existing Gradle cache and Python temporary files plus a build-local PyYAML dependency;
  those environment issues were resolved before declaring validation successful.
- Applying the rule to the latest quick-income candidate is explicitly retrospective for its
  old 300-game screen (54.33%); the old verdict is preserved. The complete continuation
  protocol, new pools and original 39 strategy-class hashes were captured before new games.
  No strategy, parameter, core, frozen version or submission change. Results follow below.


## 2026-09-16 - quick-income practical-policy continuation, not promoted

- **Question:** does unchanged quick-income pass the newly adopted practical policy on a
  larger fresh sample? User explicitly requested applying the policy to this existing candidate.
  Reused original screen 160-134-6 (54.33%) routes to 1000; old strict-gate verdict preserved.
- **Protocol before new games:** quick-income-practical-v1.md and immutable launch snapshot.
  Candidate/source/Params unchanged, 39 old class hashes verified. CG budgets 486/36 ms,
  two workers, RNG SEED=1; 500 new map pairs, seeds 42000..42499, exclude all 4324 prior hashes.
- **Result:** 525-465-10, **53.00%**, zero errors, **55% threshold not met**. All 1000 completed
  once in 1704.1 s. CI 46.93-59.07% is descriptive only. A P0/P1 wins 269/500 and 256/500;
  mean score difference +67.8, game length 91.6. Search mean A/B 18.20/18.17 ms, max 85/97,
  slow moves 3/5. No failed or slow games discarded and no cause assigned to timing outliers.
- **Decision:** stop at direct failure; conditional matched 300/1000 vs v05 not run. Those
  pools and confirmation-v4 remain untouched. Preserve the candidate for possible mechanism
  work; no tuning, second attempt, new version, promotion or arena upload in this evaluation.
  A sub-threshold result is not proof of no smaller benefit. Selected v06 and default-off
  Player unchanged; source SHA 5807068e5d50c7bb3acd395866b74f8c9a972f7c8b19fc323dfda18cff413266.
- **Verification:** 226 Java tests, six Python checker tests, skill validator pass,
  264/264 arena replays exact. The report checker independently reconstructs every outcome,
  verifies stage disjointness and complete parameter/condition equality, and returns
  NOT_PASSED / SKIPPED_DIRECT_FAILURE. All source and class identities rechecked after play.
- **Evidence:** full direct report `build/bench/quick-income-practical-v1/direct-1000.json`, SHA `ea15ba58a19630573bdd8b118516fbfe893543e6257185374fd49fda25017780`;
  durable `docs/evidence/quick-income-practical-v1.json`, SHA `8a42e2b114983372435512f3f1b0ae5f4877c6086090c033bd4288a66d604e11`. Includes both
  screen and continuation per-side results, parameters, all prior map hashes, pre-run protocol,
  source/class identities and exact decision. Commit completed evaluation under AGENTS rule 8.


## 2026-09-16 - first-screen continuation floor, practical-300-1000-v2

- User requested an additional lower cutoff before spending 1000 games, asking whether 52%
  or 53% was preferable. Selected a strict >52% floor to favor retaining borderline improvements:
  the alternatives differ by only three win-equivalent points in a 300-game screen. This is
  a practical cost/sensitivity choice, not a statistically calibrated or proven optimal value.
- New first-screen branches after all 300 games: <=52% stops and preserves the candidate;
  >52% and <60% continues to separate fresh 1000 at >=55%; >=60% passes the direct stage.
  Other thresholds, matched opponent stages, no overall 95% claim and process checks unchanged.
- Updated the installed contest skill reference, active project rules/docs, Java reporting and
  Python evaluator. Reports now identify v2; preserved all v1 protocols, raw evidence and verdicts.
  Integer half-win units implement <=312 stop, 313..359 continue, >=360 pass, including draws.
  The evaluator refuses extra stages after a screen stop instead of silently consuming them.
- Verification: all seven BenchmarkRunner tests and all seven Python evaluator tests pass;
  installed skill passes quick_validate. Boundary cases cover 51.833%, exactly 52%, 52.166%,
  53%, 59.833% and exactly 60%. No strategy/core/runtime change or new strength games.
- Latest quick-income screen 54.33% still clears this floor; its existing separate 1000-game
  result 53.00% still fails the unchanged 55% threshold. This clarification leaves the recorded
  verdict and selected v06 unchanged. Historical reports are not rewritten as v2 evidence.


## 2026-09-16 - quick-followup-early-v1, not promoted

- User requested all three quick-income follow-ups in order; independent variants, fixed v06
  control and v05 common opponent. Protocol: docs/quick-income-followups.md, practical v2.
- Hypothesis: quick root in last slot may miss complete comparisons. Default-off EARLY flag
  compares it after root zero, preserving portfolio, continuations, widths and fallback.
- Shared-state diagnostic: 77 positions from wins AND losses; nominal full quick coverage
  original/early 53/61, selected 14/21. Fresh NOISE=0 instances, not original arena timing.
- Screen 300: 164-130-6, 55.67%; fresh 1000: 489-495-16, 49.70%. Zero bot errors.
  NOT PASSED; no matched v05 stages, retry, promotion, upload or process readiness claim.
- Windows Modern Standby interrupted both series (maxima up to 266542 ms), independently
  confirmed by Kernel-Power events. All games preserved. Temporary system sleep inhibition
  started during continuation; insufficient on battery, replaced by a task-scoped system AND
  display request after the series. No global power-setting change. Timing is limited evidence.
- 227 tests pass; 264/264 replay validation exact. Recorded source/class identities unchanged.
  Durable full per-map evidence and diagnosis: docs/evidence/quick-followup-early-*.json.
- Commit this completed experiment before activating value; all experiment defaults remain off.


## 2026-09-16 - quick-followup-value-v1, stopped at 52 percent

- Independent second follow-up: early flag OFF; quick selection ranks expected net earnings
  per actual affordable construction turn using core placements on a private scratch board.
  No new path queries, wider search, core changes or retuning. Protocol fixed before play.
- Mechanism tests on both sides: more income at equal two-turn completion; three river cells
  need three turns despite six paint. 229 Java tests pass; default v06 equivalence preserved.
- Fresh 300 paired games, seeds 110000..110149: 150-138-12, 52.00%, zero errors.
  practical v2 requires strictly MORE than 52% to continue: STOP_SCREEN_300. No extra games,
  common-opponent stages, retry, promotion or new process claim. Max search A/B 51/42 ms.
- Same 77 recorded states: nominal quick retained/full/selected 70/64/10; ample 70/70/12.
  Diagnostics only. Evidence: docs/evidence/quick-followup-value-v1.json and diagnostic-v1.json.
- Source/class hashes verified unchanged across play. Commit completed experiment, retain OFF.


## 2026-09-16 - quick-followup-safe-v1; all three follow-ups complete

- Independent third follow-up, EARLY/VALUE OFF: retain a cheap quick route only if its net
  income gain survives the already predicted ink region. Reuse the existing core evaluation.
- Both-side regression demonstrates a six-paint survivor replacing a destroyed three-paint
  route, and unchanged cheap choice without a threat. 230 Java tests pass.
- Fresh 300 paired games, seeds 120000..120149: 153-142-5, 51.8333%, zero errors.
  STOP_SCREEN_300; no direct 1000 or matched v05 stages. Max search A/B 46/45 ms, slow 0/0.
- Shared 77-state diagnosis: nominal quick retained/full/selected 64/52/14; ample 67/66/19,
  one ample duplicate actual-action root skipped. Not a population strength test.
- All three experiments complete: 1900 new games on 950 distinct maps, zero bot errors,
  none passed practical v2. Do not compare unmatched percentages as causal improvements.
  Early series has confirmed standby interruptions; all data retained without retries.
- Selected v06 and Player SHA 5807068e5d50c7bb3acd395866b74f8c9a972f7c8b19fc323dfda18cff413266
  unchanged. All flags OFF; no promotion/upload or new process readiness claim.
- Both temporary system/display power requests released; no global power setting changes.
  Host events/request timestamps retained in docs/evidence/quick-followups-host-timing-v1.json.
- Reports/protocol: docs/quick-income-followups.md; complete per-map evidence and parameters in
  docs/evidence/quick-followup-safe-v1.json. Commit final completed experiment under AGENTS #8.

## 2026-09-16 - rollout-followup-diverse-v1

- Independent default-off diverse replies: choose another retained town connection before a
  reverse route, then a different corridor; same reply count, depth and path-query bounds.
- 133 recorded states: ample same-town-pair replies 91/101 -> 10/101, unchanged 1187 complete
  comparisons and 13753 queries, 23 changed actions. Mechanism evidence only.
- Fresh 300 paired games, seeds 130000..130149: 151-148-1 = 50.50%, zero errors.
  STOP_SCREEN_300 under practical-300-1000-v2. No 1000, common-opponent stage or retry.
  Search maxima A/B 59/45 ms, slow 1/0; elapsed 574.6 seconds. No process-readiness claim.
- 232 Java tests and seven gate tests pass; 264/264 arena replays exact. Source/class hashes
  matched across the fixed run. Frozen versions and selected Player unchanged; flag OFF.
- Protocol/results: docs/rollout-followups.md; durable full per-map evidence and diagnostic
  in docs/evidence/rollout-followup-diverse-v1.json and diverse-diagnostic-v1.json.
  Shared orchestration extracted without changing the old quick-variant protocol.

## 2026-09-17 - rollout-followup-replan-v1

- Independent default-off own-route reconsideration after four forecast turns, among the same
  retained routes. Core net income and remaining paint; no new paths or opponent replanning.
- 234 Java tests pass, including both-side priority reversal after partial construction,
  ink rejection, deadline invalidation, captured legality and default-v06 equivalence.
- 133-state study: ample unchanged 1187 comparisons/13753 queries, 1236 priority changes,
  12 changed actions. Nominal complete comparisons 558 -> 524; nine changed actions.
- Fresh screen 300: 154-141-5 = 52.1667%, zero errors. Strict >52% continuation applies.
- Independent 1000: 469-501-30 = 48.40%, zero errors, below 55%; NOT PASSED.
  Max search A/B 55/47 ms, slow 2/0; elapsed 1775.3 seconds. No matched-v05 stage or retry.
- All source/class identities verified before/after both stages. Core unchanged since 264/264
  exact replay validation. Retain flag OFF, selected v06 and original Player unchanged.
- Protocol/results: docs/rollout-followups.md; durable complete outcomes/parameters/identities
  in docs/evidence/rollout-followup-replan-v1.json; shared-state diagnostic alongside it.

## 2026-09-17 - rollout-followup-target-v1; all three complete

- Independent default-off second target for the winning construction only, after ordinary
  search. Existing completed shortlist; at most one additional full scenario comparison.
- 236 Java tests pass: distinct positive alternate, timeout/closure reset, both-side recorded
  middle-game coverage, unchanged construction, legal actions and default-v06 equivalence.
- 133 shared states: ample 105 extra comparisons / 17 alternate selections, same path queries;
  nominal 31 extra / four selected. The useful alternate often lacks time to be evaluated.
- Fresh 300, seeds 150000..150149: 136-144-20 = 48.6667%, zero errors; STOP_SCREEN_300.
  Max search A/B 87/59 ms, slow 2/2, elapsed 499.0 seconds. All timing outliers retained;
  no external-process readiness claim. No 1000, matched-v05 stage, retry or promotion.
- All three independent experiments complete: 1900 games / 950 disjoint fresh maps, zero
  bot errors. Diverse 50.50%/300; replan 52.1667%/300 then 48.40%/1000; target 48.6667%/300.
  Each failed the practical gate. Unmatched variant percentages do not rank the variants.
- Final audit recomputed every decision, checked map exclusions/pairing, identical diagnostic
  state hashes and single enabled experimental flags. 236 Java + seven policy tests pass;
  core unchanged since 264/264 exact replay validation. All experiment flags stay OFF.
- Selected v06 and Player SHA 5807068e5d50c7bb3acd395866b74f8c9a972f7c8b19fc323dfda18cff413266
  unchanged. Historical selected-v06 strict process timing issue remains open, unaffected.
- Protocol/results: docs/rollout-followups.md. Durable full evidence: rollout-followup-target-v1
  and target-diagnostic-v1 JSONs under docs/evidence; rollout-followups-final-audit-v1.json.

## 2026-09-17 - resume original quick-income development base

- User requested returning to the first candidate evaluated under the 300/1000 policy.
  Identified original quick-income-v1: v06 plus <=6-paint income-route retention.
- Historical 54.3333%/300 then 53.00%/independent 1000 retains its NOT PASSED verdict.
- Recorded explicit working recipe in docs/working-candidate.md, all six later experiment
  flags OFF; verified factory resolves the requested configuration. Exact archived candidate
  source SHA matches the original report, 8fb0831f44a7f0ca98ada59371fc2dd7537d1743ac711f78c21a8d7e2023dbfd.
- Documentation-only development-focus change: no strategy/default change, new game series,
  promotion or upload. Working code is not described as the historical immutable executable.
  Selected Player SHA remains 5807068e5d50c7bb3acd395866b74f8c9a972f7c8b19fc323dfda18cff413266.

## 2026-09-17 - separate exact quick-income artifact for user's arena trial

- User requested the candidate beside, not instead of, selected Player.java. Exported the
  exact previously submit-generated archived source as build/submit/PlayerQuickIncome.java.
  57,404 characters; SHA 8fb0831f44a7f0ca98ada59371fc2dd7537d1743ac711f78c21a8d7e2023dbfd.
- Fresh Java 17 compilation and isolated gradlew procCheck, four individual maps x two sides
  vs v05, 1000/50-ms strict deadlines, no grace: 1/8 problem games. Map 904146009, P1,
  turn index 25 timed out. Response not observed; 66.002-ms consumer wait is not child runtime.
  No retries, discarded failures, code/timing changes, new strength claim or promotion.
- All 23 pre-existing submission files hash-identical, including selected Player, classes
  and manifest. Separate candidate metadata supplied. User will perform the arena trial;
  no upload done. Current strict process check is NOT PASSED, communicated with the artifact.
- Full artifact identity, preservation hashes and all per-turn reports retained in
  docs/evidence/quick-income-arena-v1.json; usage/status in docs/working-candidate.md.


## 2026-09-17 - two independent construction projects on QuickIncome

- Hypothesis: distributing paint over independent useful routes limits how much one sabotage
  campaign can suppress, potentially compensating for slower first income. User requested trial.
- Implemented default-off USE_ROLLOUT_SPREAD, max two extra first actions from one ranked pair
  without shared disruptible regions. Actual 2+1 / 1+2 paint; same split in eight-turn forecasts;
  finish projects affordable this turn, abandon destroyed routes. Ordinary actions still compete.
  No new paths, core/frozen-opponent changes, tuning between stages or default promotion.
- Regression tests cover real terrain packing, region independence, delayed income, split
  continuation, completion, ink abandonment, legal output, input isolation and bounded work.
  241 Java tests, seven gate tests pass; core reproduces all 264 arena replays exactly.
- Fixed 133-state boss diagnostic vs QuickIncome: ample-time splits available in 46 states,
  selected in four, 158 split comparisons; same 13753 route queries. Nominal selected in seven,
  104 split comparisons; timing changes also change proposal coverage. Diagnostic, not strength.
- Prospectively fixed 300 vs v06: 160-138-2 = 53.6667%; fresh 1000: 502-490-8 = 50.60%.
  Direct gate NOT PASSED at 55%; common-opponent stages skipped. Fixed separate 300-game
  ablation vs identical QuickIncome with only SPREAD switched off: 150-141-9 = 51.50%.
  Small observed edge does not establish superiority. Historical QuickIncome 53% on different
  maps is not used as a matched comparison. No pooling, retries or discarded timing outliers.
- Completed 1600 games / 800 new maps, zero errors. Source/class identities verified throughout;
  ablation differs in exactly one parameter. All 25 pre-existing submission files unchanged.
  Retain flag OFF and QuickIncome development base; v06 stays selected. No new process-readiness
  check after strength failure, and no arena-readiness claim; search outliers retained in evidence.
- Limits: only existing portfolio routes, strict region independence, eight-turn forecast,
  extra search cost. This result concerns this bounded implementation, not all possible spreading.
  Full protocol/results and durable evidence links: docs/spread-construction-v1.md.

## 2026-09-17 - network-control strategy analysis and single-cell coverage probe

- User requested project analysis and a different strategy, with any CG-data requirements.
  Hypothesis: cheap local ownership rerouting is missing from town-pair cheapest-paint,
  all-enemy-avoidance and one-threat-avoidance route families. New objective/action unit:
  profitable changes to the shared network, including equal-length path switching.
- Reused ALL 133 previously fixed SPREAD diagnostic states, not a loss-only or opportunity-
  selected subset. All 14 boss replays validate and state hashes match. Exhaustively scored
  37,172 legal single placements through core, with no simultaneous enemy build or either
  sabotage. Compared all ample-time NOISE=0 QuickIncome construction roots, including WAIT.
- Positive single-cell gain: 70/133 states; positive cell outside every retained route: 69/133;
  such an absent cell beats every root on immediate net income in 45/133, across all 14 games
  (13 states from wins, 32 from losses). No newly activated pair in these 45 best missing moves;
  83/94 changed directed-pair paths retain length, 11 shorten. Repeated observations are not
  independent match outcomes, additive recovered points or estimates of win-rate improvement.
- Example 904146157 t20, (3,7), three paint: income 151:94 -> 159:82, net +20, best root +0.
  Example 904147970 t50, (12,13), one paint: 28:80 -> 32:68, net +16, only ordinary build WAIT.
  Both exclude sabotage and the opponent's simultaneous move; future investment can favor
  the old action. This establishes candidate coverage, not a proven better full policy.
- Probe class hash/full parameters match the earlier QuickIncome baseline; all 133 reproduced
  ample-time actions match its saved actions. Inputs remain unchanged. A synthetic positive
  example gives +4 net income and an own-rail negative control gives -2, using the same core.
- Fresh arena cgValidate: 264/264 exact. No main/test/core/default/frozen/submission source
  edits, strength games or process-readiness claim. Offline diagnostic sources retained in
  tools/diagnostics; full evidence and derived summary in docs/evidence/network-control-*.
- Decision: propose a separate default-off SINGLE-cell candidate-generator experiment first;
  multi-cell bundles, sabotage changes and multi-region closure planning remain separate.
  Existing data suffice for a prototype; fresh current-agent and top-player replays would
  test arena relevance. Analysis and proposed validation: docs/network-control-strategy.md.


## 2026-09-17 - single-cell network rerouting on QuickIncome

- Hypothesis: single-cell changes to an active network can redirect income, including on
  equal-length paths, and offer valuable first actions missing from ordinary route proposals.
  User authorized the experiment after the exhaustive known-board analysis.
- Implemented default-off USE_ROLLOUT_NETWORK with bounds 24 exact cells, two new singleton
  roots and 12-ms reserve; active-path usage orders cells, core computes exact net income.
  No leftover paint filler, no change to sabotage, opponent policy or later rollout routes.
  New geometric helpers are isolated in core/NetworkCells; all old core bytecode is preserved.
- Added seven core-filter and six policy tests; 254 Java tests and seven practical-gate tests
  pass, 264/264 arena replays exact. Fixed 133-state study reproduces all old baseline ample
  actions, validates all 14 replays and inputs. Known useful cell evaluated in 70/70 states;
  nominal roots compared in 68 states, selected in 26; ample compared 70, selected 22.
- Froze sources/classes/resources, full parameters, protocol, pools and existing artifacts.
  Fixed 300 vs v06: 145-155-0 = 48.3333%, STOP_SCREEN_300; skip 1000 and common-opponent stages.
  Predeclared independent 300 ablation vs identical QuickIncome with only NETWORK OFF:
  152-148-0 = 50.6667%; slight observed edge is not established superiority or promotion support.
  Both fixed stages completed; no outcome-based tuning, retries, discarded games or pooling.
- Total 600 games / 300 fresh maps, both sides, zero errors. Independent audit confirms all
  227 source/class/resource identities, 14 infrastructure identities, 165 prior evidence/pool
  identities, 25 original submission files, map disjointness and exact one-flag ablation.
  System/display sleep request acquired and released on the same thread, no global change.
  Initial preflight cache-lock sandbox failure preceded all games and is preserved separately.
- Nominal ordinary comparisons fall 937 -> 804 (-14.2%); with ample time 1187 -> 1187.
  Eight changed nominal actions do not select network; four nominal singleton choices choose
  ordinary construction with ample time. This proves search-coverage sensitivity, not the
  cause of match losses. Unspent paint and fixed route-zero continuation remain hypotheses.
- Preserve timing outliers: screen max A/B 89/58 ms, slow 3/1; ablation 111/90 ms, slow 3/2.
  These in-process counters are not strict child-process timing evidence. As declared, skip
  new submit/procCheck after strength failure; prior arena-artifact timing limitations remain.
- Decision: NOT PASSED, keep NETWORK OFF, original QuickIncome research base and selected v06.
  No frozen-version edits, promotion or upload. No additional CG data needed for this test.
  Full protocol, results and durable evidence: docs/network-single-cell-v1.md. Results added
  only after frozen-input audit; commit the completed negative experiment under AGENTS #8.


## 2026-09-17 - network search after complete ordinary search

- User authorized the first proposed follow-up: preserve ordinary QuickIncome comparisons,
  then evaluate network singletons only with remaining time. Hypothesis: avoid the earlier
  interleaved generator's loss of ordinary search coverage without removing useful new actions.
- Added independent default-off USE_ROLLOUT_NETWORK_AFTER_BASE, old NETWORK OFF; both cannot
  be enabled together. Unchanged cell ranking/gains, 24 cells / 2 roots / 12 ms reserve, paint usage,
  sabotage and rollout continuation. Common comparison loop extracted; snapshot ordinary action/value,
  require full ordinary coverage before late work and full replies before strict improvement.
- Seven new regression tests cover both sides, baseline parity, order, legal output/input
  isolation, no available new moves, invalid modes and deterministic deadline/partial behavior.
  Initial public diagnostic-array reset failure preserved and fixed before any strength game;
  full rerun 261/261 tests, 264/264 arena replays exact. No core/frozen-policy changes.
- All 133 saved states / 14 boss games validate. All old baseline ample actions reproduced;
  candidate ordinary ample action/value/route/target/cells/comparisons/query count match exactly.
  Nominal ordinary counts 888 baseline / 887 candidate, 69 extra comparisons, 22 states compared,
  12 network selections. Eight ordinary action differences remain clock-sensitive. All 133
  ample final actions match the earlier network implementation; no new strategic value formula.
- Frozen practical-v2: direct 187-112-1 / 300 = 62.5% vs v06, PASS_300. Matched v05 screen both
  172-128 / 300 = 57.3333%, difference 0; proceed only to the prescribed fresh matched 1000 each.
  Candidate 649-351 = 64.9%, v06 569-431 = 56.9%, difference +8.0 points, strength PASS.
  No pooling, tuning, early stopping, retries or additional outcome-selected matches.
- Independent fixed 300 ablation vs identical QuickIncome with only AFTER_BASE OFF:
  179-121 = 59.6667%. Descriptive map-grouped CI 48.58-70.76% spans 50%; do not label the ablation
  a separate statistical guarantee or apply the generic printed bench continuation threshold.
- Total 3200 games / 950 distinct fresh maps, complete sides and zero bot errors. Shared maps
  only in declared matched common-opponent pairs. Final independent audit 113 checks pass:
  227 main source/class/resources, 14 infrastructure identities, prior evidence/pools, 98 parameters
  per bot and all 25 original submission files exact. Benchmark power request acquired/released.
  All outliers retained, including four slow moves in full-control and one in ablation candidate.
- After audit, isolated exact-source gradlew submit produces 75,290-character Player, SHA
  9bb5d7b49dc3cb09baa2562deb64880634c43a48982a1c6a1c5c44579137230c. Staged main bytecode and
  full configuration match measured launch. Fixed four individual maps x two sides vs v05,
  1000/50-ms deadlines, zero grace: 0/8 problems/timeouts/nominal overruns; max later 44.6592 ms,
  first 633.8189 ms, zero later responses over 45 ms. All reports kept, no retries. Separate power
  request released and all original submission files preserved. This is a bounded local sample.
- Decision: strength PASS and exact-artifact process PASS. Retain verified candidate OFF,
  original QuickIncome research base and selected v06; no freeze/promotion/upload authorized.
  No additional CG data needed. Protocol/evidence docs/network-after-base-v1.md; exact audit
  and artifact scripts archived under tools/diagnostics/network-after-base-v1. Results appended
  after all frozen checks; commit completed experiment under AGENTS #8.


## 2026-09-18 - select v07 Network After Base and record pre-upload arena checkpoint

- User requested making the verified candidate the main strategy; no new hypothesis or tuning.
  Prior practical-v2 evidence: direct v06 187-112-1 / 300 = 62.5%; matched v05 screen tied,
  prescribed fresh matched 1000 each 64.9% vs 56.9%, +8.0 points. Fixed ablation 59.6667%.
- Copy current RolloutBot to v07 with package/class/name changes only. Freeze all 98 measured
  parameters from network-after-base-v1 launch; register v07 and catalog selection. Enable
  QUICK_ROUTE and NETWORK_AFTER_BASE defaults only. Core/current policy and old frozen versions
  unchanged. Historical snapshots explicitly retain OFF for newly selected absent fields;
  old diagnostic recipes/regression fixtures pin their original construction modes.
- Completed 273/273 tests, zero failures/errors/skips; 264/264 exact replay validation. Tests
  cover full configuration, prior frozen versions, current/main/v07 action parity, active late
  network choice, merged source parity and isolated warmup. No strength games repeated.
- gradlew submit regenerated main Player: 75,288 characters, SHA-256
  3c94f8ed5cd0cc16933e45b7fab7e8d48faafb9a1f52c952f601cd3fbcfc42c7.
  Versus the previously tested candidate, only two default declarations changed false to true;
  embedded effective candidate configuration stayed identical. All 25 old submit files archived
  under build/analysis/v07-promotion/previous-submit; QuickIncome export preserved in place.
- Fixed final artifact panel: maps 903510176, 904146009, 904148181, 904147739, both sides vs v05,
  1000/50-ms deadlines, zero grace, no retries. 0/8 problem games/timeouts/nominal overruns;
  max later response 44.7744 ms, first 608.3554 ms, no later response over 45 ms. Source/classes/
  parameters/maps unchanged throughout; temporary Windows execution-state request released.
  This is bounded local process evidence, not a CG-host guarantee; old timing failures remain
  recorded and are not relabelled as passes. Full reports and protocol: docs/v07-promotion.md.
- User reports the actual CG entry before intended v07 upload is QuickIncome, rank 111 / 627.
  Record the report separately from local selected v07; exact deployed hash and placement status
  unverified. No v07 upload performed by the agent or reported at this checkpoint.
- Decision: v07 selected locally and main Player ready. Commit this completed promotion under
  AGENTS #8; no push or arena upload.

## 2026-09-18 - v07 boss diagnosis and route-dependencies-v1

- User requested latest 20 v07 boss games plus renders, route-synergy audit and new strategy.
  Exact user-attributed cohort 904553513..904555504: 6 wins, 14 losses, no failed/skipped actions.
  Rendered all 20 and created replays/rendered/cg/boss-v07-20.html with curated turn jumps.
- Joined existing routes already contribute through canonical whole-network desired-pair income.
  Missing generation, rather than an omitted additive score term, limits that idea.
- Confirmed compressed-route continuation mismatch: existing rail-block bridges can be omitted
  from the stored paint recipe. Of 1581 states, 127 contain 561 repeated positive candidate
  observations vulnerable to recorded destruction of a hidden bridge. These are not selected
  mistakes or independent samples; actual recorded ink is diagnostic only.
- Hypothesis: retain canonical completed-path region dependencies without changing route order,
  cost or ranking, preventing disconnected simulated projects. Default-off flag implemented;
  conservative witness can also abandon a project when another connected detour remains.
- Fixed 300 vs v07, fresh seeds 190000..190149, paired sides, two workers, CG timing:
  133-160-7 = 45.50%, diagnostic CI 34.41-56.59%, zero errors, 538.091 seconds.
  Search maxima A/B 151/116 ms, slow 1/1, all preserved. STOP_SCREEN_300, NOT_PASSED.
  No continuation, common-opponent stage, tuning/retry or process-readiness claim.
- Shared-state audit: 30/146 changed dependency decisions; disabled current equals frozen v07
  on all 146, every action legal, full input state unchanged. 286 Java and seven Python gate
  tests pass; full 292 arena and 50 boss games reproduce exactly after new core chord helper.
- Decision: retain dependency correction OFF. It fixes the constructed mechanism but does not
  establish stronger play. Selected v07 and all original submission files remain unchanged.
- Separate network-chords experiment uses only its own flag and fresh maps; it must receive
  its own result. Boss examples have zero-gain subsets but +128/+78 complete-group income gain.
- Additional observations: 28 new arena replays; protected multi-town opening attempts by
  leaders, with separate accepted-owned/collision statistics. Stalled 904554072 t37 is an
  independent opponent/continuation-model problem: dependency correction still chooses WAIT.
- Evidence and reproducible tools: boss-v07-loss-analysis.md, arena-opening-survey-v1.md,
  route-dependencies-v1.md and corresponding docs/evidence JSON files. No upload or push.


## 2026-09-18 - network-chords-v1, completed independent v07 follow-up

- Hypothesis: bounded connected two/three-cell actions recover network rerouting whose
  incomplete subsets have zero immediate value. Boss examples 904554568 t26 and 904555424
  t28 demonstrate +128/+78 net-income changes through the core, excluding simultaneous moves.
- Implemented as USE_ROLLOUT_NETWORK_CHORDS, default OFF. Canonical allocation-free core
  generator, paint <=3, both ends touch existing connectors, same-component rerouting allowed.
  Only after complete ordinary and singleton comparisons; 24 ranked candidates, two positive
  roots, existing 12-ms reserve, full simultaneous comparisons only. Dependency fix stays OFF.
- Mechanism checks on 146 fixed public states: 15 changed/chord-selected decisions in 11 games,
  all replacing construction v07 already selected. Nine extend its former singleton. Legal
  actions and full input isolation; disabled current matches v07 in all 146. At the exact boss
  triple, the bounded candidate retains the old singleton; no claim it reproduces every steal.
- Fixed screen seeds 200000..200149: 163-137-0 / 300 = 54.3333%; separate fresh confirmation
  seeds 201000..201499: 544-456-0 / 1000 = 54.40%, below 55%. NOT_PASSED. No pooling,
  tuning/retry, common-opponent stage or exact-artifact process check after direct failure.
  Both stages zero errors; timing maxima A/B 122/72 and 80/83 ms, slow 2/3 and 5/5, retained.
  Elapsed 534.304 + 1764.208 seconds. Diagnostic confirmation map CI 48.33-60.47%.
- Independent final audit passes: 1600 games / 800 fresh distinct maps across both experiments;
  reports match frozen bots, parameters, exact seed pools, workers and timing. Source/classes,
  prior evidence and all 26 original submission files unchanged; temporary wake requests
  released. Audit also recomputes public-state membership and action-change counters.
- 286 Java tests and seven gate tests pass; 292 arena and 50 boss replays reproduce exactly.
  Browser QA confirms rendered gallery, six-win filter and exact turn jumps. Raw replay files
  and regenerable renders remain gitignored; study tools and derived evidence are retained.
- Decision: keep both new modes OFF and selected v07 unchanged. Chords show a modest observed
  edge but do not satisfy the predeclared strength gate. No new process-readiness claim,
  promotion, upload or push. Commit the completed experiments and documentation under AGENTS #8.
- Separate next hypotheses are documented, not implemented: protected opening ownership across
  multiple town approaches, and own WAIT without promised future sabotage in no-project states.
  The latter isolates the recurring postponement in 904554072; replacing an opponent reply with
  a passive scenario alone is not a demonstrated fix. See boss-v07-loss-analysis.md and
  arena-opening-survey-v1.md for evidence, reproduction, limits and concrete next designs.


## 2026-09-18 - independent boss API tooling and build provenance

- User direction: postpone recurring sabotage-WAIT work and evaluate existing network chords
  against the real boss; prefer a reusable independent script over a Tampermonkey play loop.
- Confirmed actual frontend TestSession/play [handle, answer] format and the captured
  IDE_CODE/BOSS selector [-1,-2]. Public canonical replay maps this sentinel to live Boss 4
  agent 6740207. A sanitized HAR contained no authorization; the user's replacement export
  supplied credentials. Stored only in ignored build/cg-boss/private/session.json. The imported
  original source backup matches old selected v07 SHA256 3c94f8ed5cd0cc16933e45b7fab7e8d48faafb9a1f52c952f601cd3fbcfc42c7.
- Added stdlib API transport/HAR import and fixed-plan batch runner. No redirects or retries
  of play creation; intent is durable before each request. Read-only canonical lookup resumes
  by returned/recovered game ID. Exact artifact/process/source/config hashes, map/seed/seat,
  positive boss identity and marker are checked. Matched and independent designs preserve all
  attempted/completed games; immutable schedules reject changed evidence or replaced games.
- First-turn MESSAGE carries source/config build identity because canonical replays retain
  stdout, not stderr. Immutable local registry verifies marker-to-source mapping; it is not
  independent remote execution attestation. Missing/unregistered markers remain unknown.
- Migrated 105 raw/rendered files bot -> boss with SHA/size equality. The old running capture
  server subsequently wrote three new files to bot; safely moved these to boss/unknown,
  recorded a separate tail audit and restarted the identified server (protocolVersion 2).
  Canonical replay bytes and historical evidence/runner hashes were not rewritten. Active
  Java tools accept historical missing paths and discover boss version/build subdirectories.
- Added isolated -PsubmitOut for submit/procCheck. Both tagged exports are under build/cg-boss/
  artifacts; all 26 existing build/submit files remain byte-identical. Frozen strategies,
  Params defaults, core rules and the local strength gate are unchanged.
- Verification: 299 Java tests; 15 API + 23 batch + 12 capture + seven existing gate Python
  tests. Windows TemporaryDirectory permission errors required rerunning affected offline
  suites outside the sandbox; all passed. Exact original corpus: 292 arena + 50 boss.
  Each tagged artifact passed four strict process games, zero errors/timeouts/nominal overruns.
  These are new infrastructure checks, not continuation of the failed local chords protocol.
- Live API smoke: one tagged v07 game and two matched-map pairs, both seats and both builds.
  All five created/fetched/saved successfully with correct markers and requested seeds.
  Independent runner needs no browser, Tampermonkey or local save server after session import.
  API play updates saved IDE code; original source backup is retained. No arena submission.
- Predeclared separate exploratory screen: 40 matched fresh maps, 80 calls, balanced seats
  and AB/BA, fixed exact one-sided discordance test, no extending/tuning/replacing games.
  Old 6/20 remains historical context. Protocol: docs/chords-boss-v1.md; statistics and runner
  instructions: docs/boss-evaluation-protocol.md and docs/boss-api.md. Results recorded separately.


## 2026-09-19 local - live boss screen interrupted; reusable runner verified

- Hypothesis: network chords may improve results against the actual boss even though
  the completed local confirmation was 54.4%, below its 55% gate. This is separate
  exploratory evidence, without promotion or reopening the failed local experiment.
- Froze docs/chords-boss-v1.md and the 40-pair/80-game plan before its first game.
  Completed ten fresh matched map/seat pairs: chords 3W/7L, v07 2W/8L. Independent
  canonical audit checks ranks, boss identity, maps, seeds, seats, build markers,
  local source/configuration/executable/process hashes and all 26 original submission
  files. Evidence is explicitly INTERRUPTED, with no significance test or verdict.
- At slot 21, TestSession/play returned HTTP 422 and no recoverable game ID. Original
  error body was not retained. Never retried that creation request or replaced the
  scheduled game. Read-only authenticated lookup succeeds; arena history does not
  contain these IDE games, including the last confirmed ID 904840552. Thus it cannot
  establish whether slot 21 exists. Current stage cannot safely resume without that ID.
- Added bounded private HTTP error retention for future diagnosis, keeping ordinary
  errors/metadata redacted. A distinct one-game technical plan completed as game
  904843581 with tagged v07; it neither retries slot 21 nor enters the comparison.
  Six technical games plus 20 comparison games completed through the independent API.
  The original 422 cause is unknown; a successful later call is not evidence that the
  earlier request failed before creation. No further play attempts were made.
- All 79 currently saved boss games validate exactly and are rendered. Partial gallery
  replays/rendered/cg/chords-boss-v1.html provides 20 working replay links and outcome
  filters; missing pairs are visible and no final p-value is presented.
- Reusable workflow, historical-control thresholds and replay attribution are documented
  in boss-api.md, boss-evaluation-protocol.md and replay_format.md. The browser can close
  after session import. User-provided HAR, credentials, source backup and error bodies
  stay in ignored private storage. The live capture server runs protocolVersion 2.
- Decision: preserve the incomplete fixed sample, leave selected v07 and flags unchanged,
  and commit the completed infrastructure plus interrupted evidence. Recovery requires
  the actual missing game ID; do not force another attempt or reinterpret ten pairs as
  a completed smaller experiment. No arena submission, strategy promotion or push.

- Final verification: 299 Java tests and 71 Python tests (18 API, 25 batch, 12 capture,
  nine independent-audit, seven existing gate tests), all passing. Exact statistical
  threshold/power evidence reproduced; all 79 saved boss replays validated/rendered.
  Final review also fixed premature canonical retention: incomplete replay responses
  are retained separately until final ranks/endScreen/map/seed exist. Regression
  verifies one play and five lookups across four incomplete responses, with no early
  immutable capture. The original interrupted screen is unaffected.
- Preserve the byte-hashed protocol/auditor/evidence through Git line-ending conversion
  using explicit attributes; credentials and all private diagnostics remain ignored.


## 2026-09-19 - complete the explicitly amended 40-pair boss comparison

- User explicitly requested excluding the failed request and completing the schedule;
  clarified forty games per strategy (eighty total). Registered separate amendment 1
  before resuming, preserving the prospective protocol/plan and interrupted audit bytes.
- Added explicit authorize-retry for an unresolved HTTP 422 only. Original intent and
  error stay immutable; a second request has separate evidence and authorization hashes.
  Known outcomes cannot be replaced, and an uncertain second attempt is not retried.
  The report distinguishes analyzed games, attempted requests and amended interpretation.
- One new attempt for slot 21 completed as game 904855281. Slots 22 through 50 then ran
  sequentially with five-second pauses. Slot 51 received HTTP 422/API code 407 and
  the explicit message that the temporary play limit was reached. The response was
  retained privately and its safe code/message/hash published in rate-limit evidence.
- Registered amendment 2 before another game: wait at least ten minutes after the
  refusal, retry slot 51 once on its original build/map/seat, then slots 52 through 80
  with twenty-second inter-game pauses. No alternate account/session, map replacement,
  tuning, outcome-driven stopping or sample extension. Both excluded requests remain
  recorded; the earlier slot-21 cause is still unknown.
- Final results: candidate 9W/31L/0D / 40 = 22.5% outright wins; v07 10W/30L/0D / 40 = 25.0%. Candidate-only winning pairs 5, control-only winning pairs
  6; exploratory exact one-sided p = 0.725585938. inconclusive for improvement.
  Draws are non-wins for this test; W/L/D and half-win scores remain in the evidence.
- Independent final audit recomputes canonical outcomes and checks all forty matched
  maps, same boss/league, original twenty results, artifact/process hashes, amendment,
  exactly eighty analyzed game IDs and eighty-two intents. It also excludes the separate
  post-plan diagnostic map and verifies all twenty-six original submission files.
  Original protocol/interrupted evidence/old auditor and first fifty result bytes remain
  unchanged; the new auditor
  exports a separate amended evidence file, never a replacement for the original audit.
- Verification: 89 Python tests across API/batch/capture/gate/original-audit/amended-audit
  suites pass (33 batch and seven amended-audit tests). Java code is unchanged from the
  previously passing 299 tests. All 139 saved boss replays validate and render exactly.
  Comparison gallery links all eighty analyzed games and marks both excluded HTTP attempts.
- The version folders now contain 44 v07 and 42 current replays: forty comparison games
  each plus four/two technical games. Six technical games are excluded from this screen.
- Decision: record the exploratory result and preserve v07 selection/default-off flags.
  No promotion, arena submission or push. The earlier local 54.4% confirmation remains
  below its 55% gate. Both interrupted and amended evidence are retained.


## 2026-09-19 - adopt v3 with a reusable boss control

- User approved retaining the saved control instead of rerunning it for each candidate:
  exactly forty NEW candidate games on identical cached maps, seeds and seats; compare
  total win-equivalent points, candidate >= control, draws half and equality passing.
- Direct thresholds remain <=52% stop / >=60% pass after 300; otherwise separate fresh
  1000 at >=55%. Boss replaces required older/style 300+300 / conditional 1000+1000.
  After a qualifying screen the required local 1000 and sequential boss batch may overlap;
  both started stages complete absent an integrity/transport/readiness interruption.
  Local failure retains concurrent boss evidence without permitting promotion.
- Updated AGENTS, the existing bot rule, README, active benchmarking/version guidance and
  BenchmarkRunner report policy/wording. Added boss-cache-policy-v3.md with refresh and
  hidden-boss-drift limits. Historical v2 checker/runners/audits and result evidence remain
  unchanged; new tooling uses a separate policy ID rather than reinterpreting old results.
- Added candidate-only boss_cached plan/run/report/recover: immutable cache/canonical/build
  pins, complete 40 control maps and 20/20 seats, exact candidate pairing, boss and required
  league division metadata, retained technical failures, uncertain-request no-replay.
  Generic complete candidate/control arms are supported, including verified cached ancestry.
  Reports retain collection dates, original amendments and reused-pool inference limits.
- Added boss_practical_gate combining canonical boss assessment with validated direct
  reports. It checks full parameter/spec identity, relative half-win thresholds, completeness,
  local-stage freshness, and concurrent failure branches. It cannot attest local executable
  identity or compare incompatible local protocol versus boss graphics map hashes; these
  remain explicit experiment-provenance checks, not fabricated freshness assurances.
- Read-only actual control validation: forty unique maps,20 P0 / 20 P1, 10W/30L, exact v07 build,
  boss 6740207 and league divisions 6/3/0; source series remains amended exploratory with both
  excluded requests retained. No API requests, new candidate plans or matches in this adoption.
- Verification: 299 Java tests passed; final BenchmarkRunnerTest passed after report wording
  correction. All 113 Python tests pass (92 tool tests plus 21 API tests), including 13 new
  cached-runner and 11 combined-gate tests. Independent
  review caught stale report wording and missing required league fields; both were fixed.
- Decision: v3 applies to future evaluations only. Selected v07 and both experimental flags
  stay unchanged; no promotion, arena upload or push. Refresh the pool after development
  cycles guided by its outcomes and when the boss/league changes; no arbitrary expiry count
  was invented. Existing chords 54.4% local failure and 9/40 boss result retain their decisions.


## 2026-09-19 - repair replay seeds and improve viewer readability

- User reported seed 0 and requested clearer associations between nicknames, colors and scores.
  Cause: CgValidate constructed the derived Replay with a hardcoded zero. Pass the canonical
  refereeInput seed instead; retain numeric meta.seed and add exact decimal meta.seedText to
  prevent JavaScript rounding signed 64-bit values. Missing CG seeds stay absent, invalid seeds
  fail export, and real local zero remains valid. No core rules or strategy changes.
- Group player name, P0/P1 color, current score and earned points in one panel per player. Label
  initial/current/final scores, the final winner, connection ownership columns and active count.
  Add seed copy, control titles/accessibility labels and a stacked layout for narrow windows.
- Fixed the cell-hover ReferenceError (region state was scoped only inside draw), refresh hovered
  data on turn changes, clear playback on replay load, pause on manual seek, restart Play at end,
  guard empty-viewer keyboard actions and avoid double drop handlers. Fetch replay JSON without
  cache so re-rendered metadata appears after refresh. Player labels remain plain text.
- Verification: all 302 Java tests pass, including three new seed/export regressions; nine existing
  bot-label checks pass, JavaScript parses. Rendered exactly 139 boss and seven existing arena
  games through the canonical validator. All 146 source replay byte hashes remain identical;
  all boss render data except seed metadata is identical. Two older arena exports also refresh
  earlier renderer output under the current validator; canonical evidence is unchanged.
- Browser checks: initial/late/final positions, P0/P1 winner mapping including a timeout loss,
  19-digit seed display, copy-button success, old local zero versus old CG placeholder, empty
  viewer shortcuts, hover updated by keyboard seek, navigation after button focus, and a 620px
  layout. Fresh verification tab reports no JavaScript errors. Snapshot checks are in ignored
  tmp/visualizer-review; derived replay files remain ignored. No match-strength bench is needed
  for this presentation/metadata fix. Record and commit the completed maintenance change.


## 2026-09-19 - strategy-search-rhea-v1, fixed screen completed and rejected

- **Hypothesis:** the same first construction/sabotage action can have better later
  route priorities than v07's fixed continuation. Independent default-off
  `current?USE_ROLLOUT_RHEA=1` seeds two elites from completed baseline comparisons and
  makes at most twelve later-priority or suffix mutations. Keep the retained routes,
  eight-turn horizon, two opponent scenarios, sabotage chooser, evaluator and deadline.
  Ordinary and singleton network comparisons retain priority; incomplete candidate
  scenario sets cannot replace the saved answer. No crossover or cross-turn raw-route
  persistence. The independent opening candidate is not combined with this policy.
- **Prospective controls:** `strategy-search-v1.md` and the shared immutable preflight
  freeze sources, classes, full parameters, pools and exact exported artifacts. Only
  the RHEA flag differs from frozen v07. Core-derived boss exclusions and prior local
  reports are excluded from the fresh local pool; no outcomes guide tuning or retries.
  Read-only merger verification binds exported source/configuration to the local
  snapshot, and exact-source reconstruction certifies only the seven unused new
  control metadata fields. All original reports and selected submission files remain.
- **Mechanism/readiness before strength:** 323 Java tests and 114 Python tests pass;
  292/292 arena and 139/139 boss replays reproduce exactly. All 158 saved-state ample
  decisions match frozen v07 with the flag OFF; 1106 measured decisions pass legality
  and input-isolation checks. RHEA changes 17/158 ample actions; nominally it starts in
  50 states, completes 410/435 attempted extra comparisons and selects its continuation
  in 12 states. A selection can keep the same emitted first action. These are mechanism
  observations, not independent match-strength evidence.
- **Fixed strength result:** all 300 games / 150 fresh paired maps, seeds 500000..500149,
  CG 486/36-ms search, two workers: **134 wins, 165 losses, one draw = 44.8333%**.
  Map-grouped descriptive Hoeffding 95% interval **33.7444808918-55.9221857749%**;
  total score difference -46329, mean **-154.43**, zero errors, elapsed **591.907 s**.
  Under practical-300-1000-boss-cache-v3 this is **STOP_SCREEN_300 / NOT_PASSED**.
  No direct 1000 or live-boss games are started for this candidate; no pooling,
  intermediate stopping, changed parameters, discarded games or retry.
- **Timing limits:** local benchmark maxima A/B **199/44 ms**, slow counters **14/0**.
  Both fields include first moves, whose search allowance is 486 ms; slow means an
  in-process call exceeding 50 ms regardless of turn. The report does not retain slow
  turn numbers, so 199 ms cannot be identified as an ordinary-turn maximum and all
  fourteen calls cannot be labelled ordinary. Seed 500001 with candidate as P0 has
  two slow calls, establishing at least one later call above 50 ms. Preserve this
  concern without claiming its cause or a CodinGame process timeout.
- **Separate exact-artifact process sample:** four games, both seats represented,
  1000/50-ms deadlines, zero grace, **0/4 problems**, 400 timestamped responses.
  First maximum **753.534999 ms**; later p95 **38.5061**, p99 **42.736401**, maximum
  **45.4681 ms**. Two later responses exceed the separate 45-ms headroom target.
  This small successful sample does not establish full-benchmark or CG-host safety.
  Export size 96,208 characters; source SHA-256
  `505ad8ab0894f865e8d3f7e01ab16749331262e7b9d6554392ad23f001192683`, build ID
  `9e21c8e91d7b92f23e7c0457890e190b4dc10201e047542ed1137b66bdaba4de`.
- **Decision:** retain the tested candidate OFF, with frozen v07 selected. This exact
  evolutionary continuation experiment fails its strength gate; it does not establish
  that every RHEA design fails. No further tuning, promotion, version freeze or upload.
  Record the completed rejected experiment and commit it under AGENTS #8.
- **Evidence:** [prospective protocol](strategy-search-v1.md),
  [readiness, source/class hashes and diagnostic aggregates](evidence/strategy-search-v1-readiness.json),
  [complete final outcome and frozen launch](evidence/strategy-search-rhea-v1.json).
  Raw report `build/bench/strategy-search-v1/rhea/screen.json` SHA-256
  `4e4748173500fe9c90d8d6cfa55d932ee406bff752a9042935bb19200eb505b7`;
  durable final JSON SHA-256
  `0a23726d87c29dc71d2c9b803abeb0b0cbcd4a6570dd0b949119f053f282f321`.


## 2026-09-19 - strategy-search-opening-v1 passed; both independent experiments complete

- **Hypothesis:** acquire future scoring ownership at protected town approaches before
  the opponent occupies them, even while immediate income is zero. Independent
  `current?USE_PROTECTED_OPENING=1` can change construction only during the first three
  turns. Reuse canonical ordinary route observations before shortlist truncation;
  sum distinct directed-pair demand weighted by remaining paint, divide by the cell's
  actual paint cost, apply a one-cell-per-adjacent-town quota and 0.25 collision
  discount. Existing sabotage chooser sees the selected construction; subsequent
  policy remains v07. RHEA and the other rejected experiment flags stay OFF.
- **Fixed local stage:** full 300 games / 150 new paired maps, seeds 510000..510149,
  authentic generator, CG 486/36-ms search, two workers: **186 wins, 114 losses,
  zero draws = 62.00%**. Zero errors; total score difference +123206, mean
  **+410.6866667**; elapsed **526.188 s**. Diagnostic map-grouped Hoeffding 95%
  interval **50.9111475585-73.0888524415%**. Practical v3 **PASS_300**; the separate
  1000-game stage was not required and was not started. No pooling, tuning or retries.
- **Exact cached-boss stage:** forty new sequential candidate games, all forty pinned
  control maps in identical seeds and seats (20 P0 / 20 P1), against Boss 4 agent
  6740207. Candidate **13W/27L/0D**, cached frozen v07 **10W/30L/0D**: 13 versus 10
  win-equivalent points, observed difference +7.5 percentage points. All forty
  individual results verified, no issues, request retries or replacement games.
  Candidate completion times span 2026-09-19 11:29:11 through 11:50:07 UTC. The combined
  final decision is **PASS**, with `ASSESSED_AFTER_DIRECT_PASS`. The known pool is a
  practical regression check, not fresh confirmation or a statistical superiority
  guarantee. Cached historical results are reused individually, not replaced by a rate.
- **Mechanism and correctness:** the frozen pre-strength audit covers 158 states and
  1106 legal, input-isolated decisions; disabled current equals frozen v07 in all
  158 ample decisions. The opening changes all 36 eligible states, uses 96 paint in
  those bundles, and matches current in all 122 ample post-window states. All
  **323 Java and 114 Python tests** pass. Earlier **292/292 arena** validation remains;
  after collecting the forty new boss games the final check reproduces **179/179 boss
  replays exactly** and renders **179/179**. Log:
  `build/analysis/strategy-search-v1/final-boss-validation.log`.
- **Separate timing evidence:** exact exported opening artifact passed four strict
  process games with both seats represented, 1000/50-ms deadlines and zero grace:
  **0/4 problems**, 367 timestamped responses, startup maximum **701.8207 ms**;
  later p95 **38.181499**, p99 **38.6317**, maximum **38.693099 ms**. No response crosses
  the separate 45-ms later / 800-ms startup headroom targets. Local strength bench
  maxima A/B **75/156 ms**, slow counters **2/2**, include first moves with the 486-ms
  search allowance. Slow-turn indices are absent; do not call these ordinary-turn
  maxima or infer a CG process failure. All timing outliers remain in the reports;
  the four-game process pass is a bounded sample, not a full-run or arena guarantee.
- **Exact artifact:** 96,208 characters; source SHA-256
  `eac9ed574d1f54bcca04f911ee6d782011ca11d279c553be86bd86f482501362`, build ID
  `c6b4deae785d05d8fca5822393e8f805353541bc16345f9a0e4b89bf0871fb6b`.
  Read-only merger checks bind the exported source/configuration to the frozen local
  code. The cached control compatibility certificate removes only seven proven unused
  metadata fields from the checker view; original full reports and artifacts remain.
- **Combined decision:** complete both separately frozen experiments, **640 new strength
  evaluation games** (300 RHEA local + 300 opening local + 40 opening boss), in addition
  to separate readiness process samples. RHEA remains rejected at its 44.8333% screen;
  the protected opening passes the current practical rule and is retained as a verified
  default-off candidate. Keep selected v07, both new defaults OFF and the original
  `build/submit` tree unchanged. No new frozen strategy, promotion, arena submission
  or push. Record and commit the completed experiments under AGENTS #8.
- **Evidence:** [prospective protocol](strategy-search-v1.md),
  [shared readiness and exact identities](evidence/strategy-search-v1-readiness.json),
  [opening final outcome and frozen launch](evidence/strategy-search-opening-v1.json),
  [independent final audit](evidence/strategy-search-v1-final-audit.json),
  [archived exact artifacts and supporting records](evidence/strategy-search-v1-artifacts.zip).
  Boss plan: `build/cg-boss/strategy-search-opening-v1/plan.json`, ID
  `c514bb6a89eedbe2702ef15e4cb00c3a9b770cb713a1ef79259f081c99e2db86`.
  Local screen SHA-256 `31febc468afa1467e2874e77471f8537559b45cf4e25cffcd2781f0549989cc0`;
  durable final JSON SHA-256 `59e948791feac06dfbedee495437f755ce542c81fc601cca34bdeee21bd64ef5`.


## 2026-09-19 - select exact protected-opening v08; new timing panel not passed

- **Authorized change and hypothesis:** the user selected the already measured protected
  opening as v08. This is a version/default change, not a new strategic experiment.
  Freeze `RolloutBotV08` and `ProtectedOpeningV08`; enable `USE_PROTECTED_OPENING` by
  default and retain every one of the candidate's **107** effective parameters. RHEA
  remains OFF. The first three turns claim affordable protected town approaches using
  accumulated directed-pair traffic, the existing cost/collision weighting and city
  quota. An empty opening falls back to ordinary v07 construction, as do all later
  turns. Sabotage selection and all strategy source bytes remain unchanged.
- **Strength basis:** reuse the completed practical-v3 opening result, **186W/114L/0D
  over 300 fresh local games (62%)** and **13W/27L/0D over 40 new boss games** against
  individually cached v07 **10W/30L/0D** on the same maps/seats. No 1000 stage was
  required. Promotion runs no new strength games, tuning or outcome-guided changes.
  The known boss pool remains a practical regression test, not fresh statistical proof.
- **Identity and historical compatibility:** all 107 measured parameters equal current,
  main and v08; both frozen classes match measured sources after identity-only renames.
  Tests pin all **15** v00-v07 source/snapshot files. Older snapshots explicitly resolve
  the absent opening flag to false, with query overrides applied afterward. Existing
  historical fixtures explicitly disable the new default where they represent old
  policy. **280** ample-time decisions over **70** states agree across the measured
  recipe/current/main/v08, with legal actions and untouched observations. The 64
  evolving positions include 24 early positions (22 opening selections, two valid
  fallbacks), 40 later positions, both seats and every opening turn; six additional
  decisions use saved boss positions.
- **Tests and engine:** the first full 338-test run found two fixture assumptions:
  the current-as-v05 recipe needed an explicit opening OFF, and a new coverage assertion
  wrongly required all 24 early positions to select opening even when no claim exists.
  Fix only those test assumptions: retain per-state action/selection parity, require
  opening coverage at every early turn on both seats, and zero opening count for the
  ordinary fallback. No strategy change. The final **338/338 Java tests pass**, with
  zero failures/errors/skips; **292/292 arena and 179/179 boss replays reproduce exactly**.
- **Packaging:** preserve the previous submission's **26** files and byte identities in
  `build/analysis/v08-promotion/previous-submit`. Export and compile the actual default
  `build/submit/Player.java`: **96,207 characters**, below 100,000. Its source differs
  from the measured candidate export only in the default opening flag and recomputed
  build marker. Source SHA-256
  `079ffa5859b2c7bed2fe6026fa50ab2080604f4a21fd2043f05509f1f385718d`, build ID
  `74d41b1ff096e2972e9e740096f5ff1224ed7e293cd855c3ab650e1e5046b8c3`;
  classes SHA-256 `e89b571f078ce5c345f6d719b6357e57f8d14775057fd1cfdc1ef6479d76a3e3`.
  A separate frozen-v08 export is compiled and verified for source/configuration; it
  is not the artifact exercised in the following process panel.
- **Prospective exact-artifact process result:** complete the declared four regression
  maps, both seats each, sequentially against v07, **eight games total**, 1000/50-ms
  deadlines and zero grace. **NOT_PASSED: one problem game, seven pass.** Map
  `903510176.json`, P1, zero-based **turn 29** records **TIMEOUT**: response
  **67.5805 ms**, send **11.0924 ms**, consumer **71.537699 ms**, deadline **50 ms**.
  This is an observed process failure; the report does not establish its cause.
  All remaining declared games were completed, with no retries, discarded outliers,
  replacement maps, grace changes or instrumentation changes. Source/classes and
  every frozen input remained unchanged. Temporary Windows execution-state request
  was released; global power settings were unchanged.
- **Decision:** v08 remains **selected locally** as the user requested, but submission
  timing readiness is **OPEN / NOT_PASSED**. Do not describe this export as ready.
  The earlier candidate's four successful strict process games remain valid historical
  evidence and cannot override the new exact-artifact failure. Preserve every result
  and the old submission backup; no further tuning or process retries in this promotion,
  no new strength bench, arena upload or push. Record this completed selection and its
  failed readiness check without erasing either outcome.
- **Evidence:** [prospective promotion protocol](v08-promotion.md),
  [full timing reports, parameters and exact identities](evidence/v08-promotion.json),
  [independent verification](evidence/v08-promotion-verification.json),
  [archived promotion artifacts](evidence/v08-promotion-artifacts.zip).
  Final test/arena log: `build/analysis/v08-promotion/tests-and-arena-final.log`;
  boss validation/export log: `build/analysis/v08-promotion/boss-and-main-export.log`.


## 2026-09-19 - boss-winners study; live-target-v1 and always-disrupt-v1 rejected locally

- **Question:** what must change to leave Silver, and what do players who beat Boss 4 do
  differently from v07/v08? Public API snapshots: the boss carries rating **29.69**, top of
  Silver 29.08, our v08 entry **24.91** (rank 316 global / 42 in Silver). Over the boss's last
  500 battles it wins 49.2%; players now in Gold beat it **55.7%** (368 games) and the fast
  promotions 70-95%. v08 scores 13/40 on the cached pool: the boss win rate must roughly double.
- **New corpus and tool:** `tools/diagnostics/boss_winners_study.py fetch` saves the boss's
  battles against now-Gold players as canonical `gameResult` files (368 games, gitignored);
  `study` computes phase income split (own/stolen), active/exclusive/contested connections,
  boss project completion from its `build a>b` MESSAGE, hit classes with a live-project flag,
  build and disruption rates. Reconstructed income matches recorded scoring on all 35,501 turns.
- **Findings:** against us the boss keeps about one more own-majority active connection than
  against Gold players in every phase and outcome (5.62 vs 4.53 when it wins, 3.93 vs 2.94
  when it loses), worth ~15 income/turn; its stolen income is identical. Its projects complete
  70.0% against us versus 58.0% against Gold winners, and are abandoned after an ink in 7.6% vs
  15.2%. Sabotage rate, hit classes, hit instability, destruction timing, path geometry and
  protected share are all similar. The one large sabotage difference: Gold winners' hits land
  in a region where the boss placed within the last two turns **31.7%** of the time (42.6% of
  construction-class hits); ours **17.2% / 13.9%** in losses. Empty-region pre-emption is 1-2%
  for everyone. Self-play v08 mirrors accept disruption on only 64% of turns and last 91 turns
  against 53-57 versus the boss. Full report: `boss-winners-study-v1.md`,
  evidence `evidence/boss-winners-study-v1.json`.
- **Implementation:** two independent default-off `Params` flags on the v08 policy.
  `USE_ROLLOUT_LIVE_TARGET` (weight 12 per opponent cell placed in the last 2 observed turns,
  applied to shortlist priority and exact target value, static inside rollouts) and
  `ROLLOUT_ALWAYS_DISRUPT` (least-harmful shortlisted target instead of WAIT; losing closure
  still refused). Construction search, evaluator, budgets and core unchanged. Disabled current
  keeps v08 parity; the frozen v08 policy section of the merged submission is pinned by hash;
  `RolloutLiveTargetTest` covers live-over-stale, window expiry, forced-target legality and the
  refused closure. **340 Java tests pass.** Merged current is 97,896 characters.
- **Fixed stages vs frozen v08, CG 486/36 ms, two workers, fresh paired maps with all prior
  hashes excluded:** live-target screen seeds 520000..520149 **153-137-10 = 52.6667%**,
  NEEDS_FRESH_1000; direct seeds 522000..522499 **492-463-45 = 51.45%**, **NOT_PASSED**.
  Always-disrupt screen seeds 521000..521149 **147-152-1 = 49.1667%**, **STOP_SCREEN_300**;
  its accepted disruption rate rose from 63.5% to 78.9% with no strength gain. 1600 games, zero
  errors, in-process maxima <= 103 ms with at most one slow call per side. No boss stage was
  started, no pooling, tuning or retries. `evidence/live-target-v1.json` holds full parameters,
  map hashes and report SHA-256 (screen 7bdd513d..., direct 445d79ef..., always 239b2bb4...).
- **Decision:** both flags remain OFF, v08 remains selected; nothing rebuilt or uploaded.
  The self-play bench does not reproduce the boss situation the mechanism targets; whether
  hitting the boss's live project lowers its 70% completion rate is untested because policy v3
  requires the local direct stage first. Proposed for the user: a boss-directed screen order for
  mechanisms motivated by boss replays (fixed 40-game cached comparison before the self-play
  1000, local 300 as non-regression). Details and limits: `live-target-v1.md`.
- Also: the contest skill was synchronized from the ChatGPT-agent copy into the Cursor copy
  (benchmarking.md added; Cursor browser constraints kept as an example in cg-api.md).
- **Boss stage (user-authorized diagnostic, same day):** isolated artifact for
  `current?USE_ROLLOUT_LIVE_TARGET=1` (97,895 chars, source `53b53ac2...`, build
  `f514e6d7...`) passed a strict 0/8 process check (later max 41.3 ms). Forty sequential
  candidate games on the cached v08 pool (control = exact measured opening build
  `c6b4deae...`, 13/40): **8 W / 32 L = 20%**, NOT_PASSED; candidate-only wins 5,
  control-only 10. 40/40 replays exact; boss rating observed 30.99-31.19 (29.69 in the
  morning). The rollout applies the same live counts to the simulated boss, so the drop
  may be a construction side effect (our fresh construction looks more threatened) rather
  than a worse target choice; not separated here. Evidence `evidence/live-target-v1-boss.json`.
## 2026-09-20 - Indrill (Legend #1, Python3) replay study; track-value-v1 rejected locally

- **Question (user):** the arena leader Indrill is written in Python3 and leads Legend by six
  rating points (36.34 vs 30.43); what does it do, and can our candidate do the same?
- **Corpus and tools:** all 282 completed battles of agent 6752275 (265-17, 94%), fetched by
  the new `tools/diagnostics/indrill_study.py fetch` into gitignored `replays/cg-indrill/`;
  `cgValidate` reproduces **282/282** exactly. `indrill_study.py study` (viewer events,
  income cross-checked on every turn) and the new core-based `gradlew cgLeaderStudy`
  (`btk.tools.CgLeaderStudy`: per-candidate-region sabotage table with exact ink deltas,
  construction rows against cheapest completion routes, hit/ink sequences, game facts) wrote
  the datasets under `build/analysis/leader-study/`. Evidence `evidence/indrill-study-v1.json`.
- **Key findings** (full report `indrill-study-v1.md`): (1) Indrill is a **sub-millisecond
  heuristic**, not a search: referee `executionTimeMs` median 10 ms per whole game (0.2 ms/turn)
  against ~20 ms/turn for its C++ opponents. (2) It disrupts on **100.0%** of turns from turn 1,
  builds on 97% with 2.91/3 paint, never issues WAIT/AUTOPLACE/MESSAGE, uses `DISRUPT x y`.
  (3) **93.6%** of its hits end in an ink (2,706 of 4,016 inked regions took four own hits);
  48% of hits are on opponent *construction* (opponents 35%) and the class stays construction
  through instability 0..3 (1946/1991/1736/1270) whereas opponents' targets complete before the
  ink: Indrill wastes the opponent's paint before it earns. (4) At turn 10 it is slightly behind
  (income 13.3 vs 14.2); at turn 20 it has 56 vs 41 income and 4.4 vs 2.4 own-majority
  connections with the *same* track count on the board. (5) A conditional logit over 14,423
  target decisions (test top-1 38% vs 4% uniform) values an unfinished opponent track at ~0.86
  opponent-income points, an own track at -1.25, +4 per instability level reached, and weights
  own income loss at half of the opponent's; in v08's `chooseTarget` a track is worth 0.4
  against 5-8 per income point, about 17x less. (6) 48.7% of its cells are on no cheapest
  completion route (89% after turn 45): full-paint network work in regions shared with the
  opponent; its routing cost model was not identified. (7) Its 17 losses are all early
  closures while behind, ten delivered by its own ink; v08 already refuses a losing closure.
  (8) v08 on Indrill's states (`cgStudy`): 11.8% placement-cell agreement, 26.5% same target,
  disrupts on 78% and places on 77% of those states (1.81 cells/turn).
- **Implementation:** default-off `USE_ROLLOUT_TRACK_VALUE` replaces the single
  `trackBalance x ROLLOUT_TRACK_WEIGHT` term in shortlist priority and exact target value by
  `oppTracks x ROLLOUT_OPP_TRACK_VALUE (5.0) - ownTracks x ROLLOUT_OWN_TRACK_VALUE (6.0)`;
  `ROLLOUT_TRACK_VALUE_SELF_ONLY` restricts it to our own chooser so the simulated opponent
  keeps the v08 term (the live-target caveat). Flag-off expression is byte-identical to v08.
  A corridor-denial fallback prototype (turn-0 empty-region hit) was removed: the merged
  artifact exceeded the 100,000-character limit (100,125) and Indrill's turn-0 rule was not
  identified (95% on a cheapest route, 92% town-adjacent, but not the max-traffic region).
  Merged candidate is **98,886** characters. `RolloutTrackValueTest` (3 tests): construction
  region outranks a one-income active region only with the flag, own tracks flip it back,
  SELF_ONLY leaves the opponent model unchanged, legal actions, parameter validation.
  **343 Java tests pass**; `btk.core` unchanged; new fields registered in
  `LiveTargetExperimentParams` so frozen v06-v08 records stay untouched.
- **Fixed stages vs frozen v08** (CG 486/36 ms, two workers, fresh authentic pools registered
  under `bench/pools/track-value-v1-*.json`, 10,455-10,755 prior map hashes excluded before
  play, `tools/run_track_value_experiment.py`, reports write-once in `build/bench/track-value-v1/`):
  - both choosers, seeds 530000..530149: **144-147-9 = 49.50%**, **STOP_SCREEN_300**;
    accepted disruption 64.6% vs 64.0%, mean diff +22, 92 turns/game.
  - self only, seeds 531000..531149: **158-133-9 = 54.17%**, NEEDS_DIRECT_1000; then seeds
    533000..533499: **494-477-29 = 50.85%**, **NOT_PASSED** (< 55%), CI 44.8-56.9%,
    disruption 65.7% vs 66.5%, P0/P1 246/248 of 500 each, 276/1000 early closures.
  Zero bot errors in all 1600 games; in-process maxima 73/62, 58/50, 107/100 ms with at most
  four slow calls per side (first moves included; not process deadlines). No boss stage (a
  local direct failure cannot be rescued), no pooling, tuning or retries.
  Evidence `evidence/track-value-v1.json`.
- **Reading:** the candidate's accepted-disruption rate did not rise in self-play (64-66% both
  sides): after the shared protected opening both v08 copies build along the same cheapest
  routes, so most opponent tracks sit in town regions or in regions where we hold at least as
  many tracks, and 5 vs 6 per track keeps those unhittable. The bench games last 92 turns with
  placement on 56% of turns, against 47-turn games with 97% placement and 100% disruption in
  Indrill's arena; the local mirror bench does not create the situations the valuation is for.
  This is the third sabotage-valuation flag (after live-target and always-disrupt) to fail the
  same bench for the same structural reason.
- **Decision:** both flags OFF, v08 selected, nothing rebuilt in `build/submit` or uploaded.
  Proposed next steps for the user: (a) a behaviourally calibrated leader-like local reference
  opponent (full paint every turn, Indrill's fitted target valuation) in the panel, so that
  sabotage mechanisms are screened against the meta they target; (b) a user-authorized 40-game
  cached-boss diagnostic of the self-only variant as done for live-target; (c) the never-idle
  construction gap (1.1 unused paint/turn on Indrill's states) once its routing model is known.

## 2026-09-20 - Adopt practical-1000-futility-boss-cache-v4

- **User decision:** change the candidate criterion to a single 1000-game run with an early
  stop, instead of the v3 300-screen then conditional fresh 1000. Assessed the proposal and
  refined it: keep interim looks strictly *futility-only* (reject, never promote early), add a
  cheaper look at 300 as well as the user's 500, and gate boss on the 500-look clearing so the
  rate-limited boss quota is never spent on a candidate the local run then aborts.
- **Policy v4:** one pooled 1000-game local comparison vs the selected control on fresh disjoint
  maps, three registered blocks (300 / +200 / +500). Two pre-specified futility looks stop when
  the pooled score is strictly below 50% at cumulative 300 or 500 games; survivors always play
  the full 1000 and are gated once at >=55%. No early pass (an early accept would bias the
  promotion direction; futility only ever stops a losing run and cannot inflate false promotion).
  At n=500 a true-55% candidate falls under 50% ~1% of the time, so a genuine passer is almost
  never aborted. The 40-game cached-boss check is unchanged from v3 and may run concurrently
  once the 500-look clears; a local failure can never be rescued by boss. Two workers stay the
  rule for the timed bench (more threads starve the 486/36-ms search and can bias A vs B).
- **Why not the user's exact shape:** going straight to 1000 for everyone is slower than v3 on
  the common failing candidate (which v3 kills at 300). The 300+500 futility looks fold the old
  screen into the single 1000 so clearly-losing candidates still die at 300, borderline at 500,
  and only survivors reach 1000 - and we never play a throwaway screen on separate maps.
- **Implementation:** 	ools/practical_gate_v4.py (pooled assessor + futility + boss combine,
  reusing v3's checked_boss/oss_cached), 	ools/run_practical_v4.py (three-block runner,
  stops automatically on a futility look, excludes every prior benchmark map hash, write-once
  reports and per-block decisions). 	ools/test_practical_gate_v4.py: 11 tests / 8 subtests
  covering both futility boundaries (strict <50%, exact-50% survives), the 55% final gate with
  draw half-credit, rejection-only interims, cross-block freshness, changed conditions/params/
  bots, block sizes, boss-cannot-rescue and CLI write-once. The v2 and v3 modules, tests and all
  historical verdicts are untouched. **125 Python tests pass** (was 89/114); Java unaffected.
- **Docs:** AGENTS.md #3, README and docs/benchmarking.md updated; v3 kept as superseded but
  reproducible. The general contest skill is deliberately not changed (project-specific policy).
- **Not yet run:** no games under v4 yet. First live use will be re-screening the retained
  track-value-v1 self candidate (its v3 verdict was NOT_PASSED at 50.85%/1000).

## 2026-09-20 - leader-v1 reference opponent (Indrill-style)

- **Why:** the v08 self-play mirror does not reproduce the meta our sabotage flags target
  (mirrors: 92 turns, 56% placement, 64% disruption), so live-target, always-disrupt and
  track-value all failed the bench for the same structural reason. Added a non-mirror reference
  that plays like the arena leader Indrill, so sabotage-heavy candidates can be screened against
  the meta they are meant for.
- **What:** tk.bot.reference.LeaderBot, registered as the local-only leader-v1 exactly like
  pressure-v1 (BotFactory case + FrozenParams + leader-v1.properties + catalog entry). It is a
  fast heuristic: DISRUPT every turn on the opponent's *unfinished* construction using the study's
  fitted valuation (opponent track 5, own track -6, +8 per instability level reached,
  previous-target stickiness x1.7, own income loss weighted half, empty-corridor pre-emption on
  town approaches), full paint every turn on the most valuable affordable route, and a protected
  town-approach opening for the first three turns. It refuses a losing one-hit closure. The
  behavioral constants are frozen inside the class and never retuned; only NOISE/SEED/UNSTABLE_COST
  come from Params, so no new Params field was added and the frozen v06-v08 records are untouched.
- **Verification:** LeaderBotTest (6 tests): frozen-parameter loading, full paint spent + DISRUPT
  every turn + determinism at NOISE=0 without mutating input, inking unfinished opponent
  construction over stale/own-track regions, refusing a losing close while taking a winning one,
  a protected-town opening, and a legal WAIT at zero budget. **349 Java tests pass** (+6);
  tk.core and every frozen strategy are unchanged. A 40-game smoke vs v08 (seeds 770000,
  CG timing) shows the intended signature: **100% accepted disruption vs v08's 62%**, leader-v1
  losing ~15% on strength. uild/analysis/leader-v1-smoke.json.
- **Honest limits:** leader-v1 reproduces the leader's relentless-sabotage behavior, not the
  leader's arena strength. It builds only cheapest routes and lacks Indrill's ~48% off-route
  network construction (his routing model was not identified), so it loses to v08 and is a
  screening opponent, not a strength benchmark or a claim to reproduce the player. A reference
  result is not a promotion gate and stays separate from the practical-v4 stages.
- **Next (not run):** re-screen the retained sabotage flags (track-value self, live-target,
  always-disrupt) against leader-v1 as behavioral coverage.

## 2026-09-20 - leader-coverage-v1: retained sabotage flags vs leader-v1 (no effect; reference gap found)

- **Question:** the three sabotage flags (track-value self, live-target, always-disrupt) failed
  the practical bench for one structural reason: the v08 mirror never produces the leader's
  relentless-sabotage meta. Against `leader-v1`, which disrupts on every turn, do they change our
  result or behavior at all, and in which direction? Descriptive coverage check, not a gate.
- **Design (predeclared in `tools/run_leader_coverage.py`):** four arms vs `leader-v1` on the
  SAME fresh 150 paired maps (matched panel), new pool `bench/pools/leader-coverage-v1.json`
  (seeds 540000..540149), CG 486/36-ms search, two workers, 300 games per arm, sequential. The
  11,575 map identities from every prior report under `build/bench`, `build/analysis` and
  `docs/evidence` (130 files) were excluded before the first game. Reports, logs and the summary
  are write-once under `build/bench/leader-coverage-v1/`; summary copied to
  `docs/evidence/leader-coverage-v1.json`. Zero bot errors in 1,200 games; ~4.5-5 min per arm.
- **Results (candidate first; "dMap" = mean per-map score delta vs control, 150 maps):**

  | arm | W-L-D | score | diff | turns | closures | our place / disrupt | leader disrupt | dMap (SE) |
  |---|---|---|---|---|---|---|---|---|
  | control v08 | 247-53-0 | 82.33% | +1426 | 83.6 | 123 | 57.6% / 55.9% | 99.9% | - |
  | track-value self | 250-50-0 | 83.33% | +1452 | 82.9 | 128 | 57.5% / 56.5% | 99.9% | +0.010 (0.026) |
  | live-target | 247-53-0 | 82.33% | +1306 | 82.0 | 125 | 56.7% / 58.0% | 99.9% | 0.000 (0.030) |
  | always-disrupt | 249-51-0 | 83.00% | +1474 | 81.5 | 137 | 56.9% / 78.0% | 97.6% | +0.007 (0.029) |

  Per game: candidate-only / control-only wins 31/28, 35/35, 39/37; 87-95 of 150 maps tied
  exactly. Map-grouped Hoeffding intervals of the arm scores 71-94%. In-process maxima 55/87/43/299
  ms with 1/3/0/8 slow calls on our side (first moves included; the 299 ms belongs to
  always-disrupt and is worth a look before any reuse of that flag, not a process deadline).
- **Reading:** (1) None of the flags moves the result against a 100%-disruption opponent either;
  the deltas are within one standard error of zero. The "wrong meta" explanation therefore does
  not rescue them: even where the opponent behaves like the leader on sabotage, valuing its
  unfinished tracks (track-value), its live project (live-target) or hitting every turn
  (always-disrupt, 56% -> 78% accepted disruption) changes neither our win rate nor our
  construction (1.31-1.32 cells/turn in all four arms). (2) v08's own accepted disruption is
  *lower* against leader-v1 (56%) than in mirrors (64%), and games still last 82-84 turns against
  Indrill's 47: leader-v1 does not create the short, destructive games either. (3) The reason is
  visible in the reference's construction, which the leader-v1 entry above overstated: LeaderBot
  builds only when some cheapest route has positive projected income within HORIZON=8, so it
  places on **41.5% of turns, 0.95 cells and 1.18 of 3 paint per turn**, against Indrill's 97%,
  2.29 cells, 2.91 paint. It reproduces the leader's *sabotage* signature (99.9% accepted
  disruption), not the *never-idle construction* signature; v08 out-builds it 1.32 to 0.95
  cells/turn and wins 82%. The docs are corrected accordingly (BOTS.md, README).
- **Decision:** all three flags stay OFF and retained; v08 stays selected; nothing rebuilt or
  uploaded. leader-v1 stays as registered (frozen constants; a changed reference would be a new
  id). The behavioral-coverage idea is exhausted for these three flags: they would need a new
  mechanism, not a new opponent. The open gap is now construction-side: a `leader-v2` reference
  can only reproduce the 97%-placement meta once Indrill's off-route routing model (48.7% of its
  cells) is identified, which is also the prerequisite for our own never-idle rule.

## 2026-09-20 - Per-cell construction study: no hidden routing model; the gap is unused paint

- **Question (user):** the goal is a candidate that plays like the leader and gets us past the
  Silver boss. What does Indrill's construction actually do with the 48.7% "off-route" cells?
- **Tool:** `gradlew cgCellStudy` (`btk.tools.CgCellStudy`): one row per placed track for both
  players; at placement time the paint slack of the best route THROUGH the cell over the optimal
  completion of every inactive pair (two Dijkstra runs per pair, so every equal-cost alternative
  counts), the own-only and unstable-avoiding variants, cell-count slack of an own-only path
  against an active pair's current connection (take-over) and of an unstable-avoiding path
  (bypass); then the cell's life: first earning turn, the pairs it served and their class at
  placement time, peak simultaneous pairs, lifetime points, loss turn. `tools/diagnostics/
  cell_study.py` aggregates. Report `indrill-construction-v1.md`; evidence
  `evidence/indrill-cell-study-v1.json`, `evidence/boss4-cell-study-v08-40.json`.
- **Findings, Indrill corpus (282 games):** 40.1% of his cells lie on *some* paint-optimal
  completion route (single tie-broken path: 36.5%), 61.0% within +1 paint, **77.6% explained**
  by near-optimal completion / take-over / bypass, **86.8% through turn 45**; the rest is late
  filler when no inactive pair is reachable (87% of the unexplained cells). His opponents:
  38.1 / 59.1 / 73.7 / 82.8%. Same places, different outcomes: 59.7% of his cells ever earn
  (opponents 46.9%), 11.7 vs 8.1 points per paint, 3.6% vs 9.4% abandoned near-optimal
  projects; his earning probability stays 70-77% at any slack, the opponents' falls to 25-51%
  beyond +2. Off-route earning cells: completion 35 / bypass 35 / take-over 27% (opponents
  35 / 15 / 49%). 89% of all points on both sides come from cells on >= 3 connections at
  their peak. Conclusion: the construction side of the leader is near-optimal completion,
  full paint every turn, commitment, relocation around threatened regions and filler; there is
  no hidden routing model to copy.
- **Findings, Silver boss (40 cached v08 games, 13-27, 78.5 turns):** equal points per paint
  (27.0 vs 27.2), equal route classes, but paint per turn **1.55 (v08) vs 1.80 (boss)**,
  placement 54% vs 61%, and the gap sits in turns 21-45 (2.44 / 1.75 vs 2.94 / 2.19). RolloutBot
  has no leftover-paint rule: when the "wait" root wins or the remaining proposals do not fit,
  the paint is lost (`USE_IDLE_EXTEND` is read only by EvalBot/HeuristicBot).
- **leader-v2 reference** (`LeaderBot2`, local-only, leader-v1 sabotage + never-idle
  construction: 24-turn horizon, shared/enemy-avoiding variants, cheapest-completion fallback,
  filler next to own earning rails; `LeaderBot2Test`, 4 tests): places on 90% of Indrill's
  states (2.15 cells/turn; leader-v1 71% / 1.64), cell agreement 11.4%, target agreement 25.7%
  (leader-v1 25.5%, v08 26.5%; the fitted valuation is no closer to his choices than v08's own
  chooser). Against v08: **8-32 (20%)**, seeds 771000, `build/analysis/leader-v2-smoke-v08.json`.
  Stays a behavioural opponent, not a candidate; it showed the gap is unused paint, not routes.
  The leader-v1 catalog description ("full paint every turn") was corrected.
- **Experiment fill-v1: `USE_ROLLOUT_FILL`** (default off, `ROLLOUT_FILL_MAX_INSTABILITY=0`):
  after the v08 search, leftover paint goes to free cells outside the target region in town
  regions or regions at instability 0, adjacent to an own track; +4 on a proposed route, +3 next
  to an own earning track (else +1), +2 town region, minus terrain cost. Search, routes,
  evaluator, target unchanged; `RolloutFillTest` (3 tests). Smoke 40 games vs v08: 25-15,
  placement 89.5% vs 50%, paint 2.62 vs 1.39. **Practical-v4** (first live use of the v4 runner):
  block 1 **176-124 (58.67%)**, block 2 **109-91 (54.50%)**, block 3 **284-216 (56.80%)**,
  pooled **569/1000 = 56.90%, PASS_1000**; zero errors; candidate places on 90-91% of turns
  (v08 53-56%), 2.65 vs 1.5 paint/turn. Block 1 has 12 slow in-process calls per side (max
  133 / 120 ms) because a study export and the test suite ran concurrently during that block
  (my mistake; blocks 2-3 clean at 0 / 3 slow, max 47-93 ms); symmetric, not a process deadline.
  Verdict `NEEDS_BOSS_CACHED_40`. Evidence `evidence/fill-v1.json`; reports `build/bench/fill-v1/`.
- **Blocker:** the isolated artifact `submit -PsubmitVersion="current?USE_ROLLOUT_FILL=1"` is
  **100,646 / 100,000 characters** after compacting the filler to ~1.5k. `current` already ships
  every rejected default-off rollout experiment (RHEA ~3.5k, network chords ~2.7k, spread ~2k,
  route dependencies, replan, second target, diverse replies, quick variants, live-target,
  always-disrupt, track-value); the merger already strips comments. `FileMergerTest` fails on
  `current` builds while the fill code is present, so the fill code (RolloutBot, Params, its
  test, `LiveTargetExperimentParams`) is left **uncommitted** pending the user's decision which
  rejected experiment to delete from `bot/current` (frozen v06-v08 untouched). No boss games,
  no upload, v08 stays selected.

## 2026-09-20 - RHEA removed from current; fill-v1 artifact, process check, cached-boss stage

- **User decision:** drop RHEA (strategy-search-rhea-v1, rejected 2026-09-19) from `bot/current`
  to make room for the fill candidate. Removed from `RolloutBot`: the elite/seed population,
  `seedRhea` / `retainRhea` / `searchRhea` / `compareRhea`, the `activePlan` hook in the rollout
  and the RHEA branches of the constructor guard, `init` and `play` (about 4.5k source
  characters). The three `Params` fields stay because frozen `RolloutBotV08` reads them;
  `current` now rejects `USE_ROLLOUT_RHEA=1` with an explicit error. `RolloutRheaTest` deleted;
  `FileMergerTest` uses `current?USE_PROTECTED_OPENING=0` as its independent candidate. The
  frozen v08 policy bytes are unchanged (pinned hash test still passes). `SearchExperimentStudy`
  and `tools/run_strategy_search_experiments.py` remain as historical tooling; their RHEA specs
  can no longer be instantiated on `current`.
- **Artifact:** `submit -PsubmitVersion="current?USE_ROLLOUT_FILL=1" -PsubmitOut=build/cg-boss/
  artifacts/fill-v1` -> **95,279 / 100,000 characters**, source SHA-256 `7d55b4fb…`, build
  `4cda8b1b…`. Strict process check (`procCheck -PsubmitOut=... -Pbot=v08 -Pgames=8
  -Pmap=replays/cg/boss`, zero grace): **0/8 problem games**, 5/8 wins, first response
  <= 711 ms, later responses <= 42.4 ms, no timeouts or nominal overruns
  (`build/cg-boss/artifacts/fill-v1/process-check.json`). **349 Java tests pass** (7 RHEA tests
  removed, 3 fill tests and 4 leader-v2 tests added).
- **Cached-boss stage (user-authorized):** control = the measured v08 build `c6b4deae…` (40 games,
  13 W / 27 L, candidate arm of `build/cg-boss/strategy-search-opening-v1/plan.json`), Boss 4
  `6740207`. First plan `build/cg-boss/fill-v1-boss-40` (plan `1288b07b…`) stopped at slot 1
  with HTTP 422 / API code 73 "Only 1 executor running at the same time for a test session":
  no game was created; cached mode forbids replacement attempts, so the folder is retained as
  evidence and a fresh plan `build/cg-boss/fill-v1-boss-40b` was registered two minutes later.
  Forty sequential candidate games (plan `95596d9c…`, 2026-09-20 14:01-14:23 UTC, no failed or
  uncertain requests): **17 W / 23 L / 0 D** against the cached **13 / 27**; `bossStage PASS`.
  Per map/seat: candidate-only wins 11, control-only 7, both 6, neither 16; P0 8/20, P1 9/20.
  All 40 canonical replays reproduce exactly (`cgValidate`, `replays/cg/boss/current/4cda8b1b…`).
  Reports: `build/cg-boss/fill-v1-boss-40b/report.json` -> `evidence/fill-v1-boss.json`.
- **Combined v4 verdict:** `practical_gate_v4.py --block1..3 --boss-plan` -> `PASS_1000` + boss
  `PASS` -> **strengthStatus PASS, promotion true** (`evidence/fill-v1-decision.json`). The v3
  cached-boss checker compares bot spec strings and full parameter maps, which cannot match when
  the local control is the frozen id `v08` while the cached control was built as
  `current?USE_PROTECTED_OPENING=1` before the later default-off fields existed. Added an
  explicit alias to the active v4 gate only: `FROZEN_CONTROL_ALIASES['v08']` accepts exactly the
  documented build `c6b4deae…` (the v08 promotion pins it as the measured artifact) and requires
  every field missing from the cached snapshot to be a known later default-off field at its
  default value; the relabelling is recorded in the verdict (`bossControlAlias`). v2/v3 checkers
  unchanged; 12 v4 tests (+1), 126 Python tests pass.
- **Not done:** promotion (copy `current` to `bot/v09`, default `USE_ROLLOUT_FILL=true`, freeze,
  rebuild `build/submit`, upload) needs the user's decision. Selected remains v08.

## 2026-09-20 - v09 Never Idle selected and packaged

- **User decision:** promote fill-v1. Protocol as for v08 ([v09-promotion.md](v09-promotion.md)).
- **Freeze:** `bot/v09/RolloutBotV09` + `ProtectedOpeningV09` are byte copies of `current` with
  only package, class names and label changed (verified by hash with the identity changes undone);
  `v09.properties` = the 117 measured values (identical in the three local blocks and the boss
  artifact manifest). `Params.USE_ROLLOUT_FILL` default -> true; `FrozenParams` pins
  `USE_ROLLOUT_FILL=false` for every snapshot that predates the field, so v01-v08 keep leaving
  paint unused (asserted). BotFactory / FileMerger / catalog know v09; v08 is `historical`.
  `run_practical_v4.py` defaults its control to v09 and the v4 gate has the alias
  `v09` = build `4cda8b1b…` (the boss-tested fill-v1 artifact) for future cached-boss stages.
- **Tests:** V08PromotionTest became historical (`current?USE_ROLLOUT_FILL=0` reproduces v08, as
  V07's did at the v08 promotion); rollout fixtures opt out of the fill default; new
  `V09PromotionTest` (5 tests: exact 117-field record for current/main/v09, frozen-source
  identity, 18 historical pins, decision parity on 96 evolving positions with the filler
  exercised and on saved boss positions, RHEA refused); `FileMergerTest` checks that frozen v09
  and current ship identical policy bytes and configurations. **355 Java + 126 Python tests
  pass**; `cgValidate` 292/292 arena, 259/259 boss.
- **Artifacts:** previous `build/submit` (28 files) backed up with hashes and intact afterwards.
  Default `gradlew submit`: **95,278 chars**, source `1df7c131…`, build `c56c83b8…`; differs from
  the boss-tested `4cda8b1b…` only in the fill default declaration and the marker (same config
  hash `aed03344…`). Frozen export `-PsubmitVersion=v09`: 95,254 chars, build `b755f9b4…`,
  0/4 strict games.
- **Strict 8-game panel (default artifact, v08 protocol: same four maps and order, both seats,
  vs frozen v08, zero grace, no concurrent load): 0/8 problem games**, first responses 544-736
  ms, later <= 47.2 ms, no timeouts or nominal overruns
  (`build/analysis/v09-promotion/process-check-1..4.json`, hashes in
  `evidence/v09-promotion.json`). The v08 artifact's 1/8 record stays as is.
- **State:** v09 selected; `current` = v09; `build/submit/Player.java` is the v09 default
  artifact ready for the user's upload. Nothing pushed or uploaded by the agent.

## 2026-09-20 - neural-v1: behaviour-cloned policy from the leader's replays (local PASS_1000)

- **Ask:** a new strategy built on a neural-network method, with main logic unlike the existing
  EvalBot/RolloutBot line, learning from the Indrill corpus. **Assessment** (docs/neural-policy-v1.md
  section 1): behaviour cloning with conditional-logit MLPs over engine-grounded candidate rows is
  the only approach that fits the data (14,423 exactly reproduced turns), the 50-ms turn and the
  100k-character source limit; AlphaZero-style self-play, a neural evaluator inside RolloutBot and a
  raw-grid CNN were rejected for v1 (reasons recorded there).
- **Implementation:** `btk.bot.neural.{NeuralFeatures, NeuralNet, NeuralWeights, NeuralBot}`,
  `btk.tools.CgNeuralExport` (`gradlew cgNeuralExport`), `tools/neural/train_neural.py` (numpy;
  torch is not installed). Registered as `neural-v1` (BotFactory, `versions/neural-v1.properties`
  with NOISE/SEED/USE_CLOSE/USE_DISRUPT only, catalog category `candidate`, FileMerger
  `-PsubmitVersion=neural-v1` renaming `NeuralBot` to `EvalBot` like the frozen builds). No Params
  field, `current`, frozen version or default changed; `FileMergerTest` asserts `current` builds
  contain no neural code.
- **Data:** 282 games, 14,423 sabotage decisions (34.1 legal regions each, 0 without a target),
  31,594 placements in stdout order on the intermediate board (120 affordable candidates each;
  1,330 = 4.2% of the leader's cells fall outside the candidate filter and are skipped). Training
  games keep the chosen row + 40 sampled negatives; the 40 latest games are the holdout with full
  candidate sets.
- **Training (40 epochs, Adam, cosine decay, seed 20260920):** sabotage 29->32->16->1 holdout top-1
  **55.5%** (top-3 77.2%, uniform 3.1%, linear conditional logit 50.2%); build 42->48->24->1 top-1
  **23.4%** (top-3 42.5%, uniform 1.1%, linear 20.4%; per placement index 22.7 / 25.6 / 21.5%).
  int16 quantisation reproduces the float numbers once standardisation is shipped explicitly:
  folding it into layer 0 lost 15 points in the smoke run (recorded in the doc). 13,512 base64
  characters of weights.
- **Timing work:** first strict process check reached **49.1 ms** later response (0/8 problems but
  no margin). Profile: ~20 inactive pairs per turn, 76 us per referee-exact `Pathing.cheapestPath`
  (its AutobuildAStar block flood-fill), three route recomputations per turn. Fixes: skip the
  income simulation for cells with fewer than two passable neighbours (provably zero), reuse the
  shared route when the own-only / opponent-own-only variant cannot differ, recompute the
  opponent's routes only once per turn, skip pairs separated by ink via a component labelling, and
  a feature-side plain Dijkstra (`NeuralFeatures.cheapestCostPath`, optimal cost asserted equal to
  the core's on evolving boards in `NeuralBotTest`). Features 12.3 -> 4.9 ms/turn; final exact
  artifact (87,815 chars, source `19ca607a...`, build `73f774fb...`) strict check **0/8**, first
  <= 751 ms, later **<= 30.6 ms** (`build/analysis/neural-v1/process-check.json`).
- **Strength (practical-v4, control v09, `run_practical_v4.py --seed 900000`):** 160-140 (53.33%),
  119-81 (59.50%), 279-221 (55.80%) -> pooled **558/1000 = 55.80% PASS_1000**, zero errors, all
  12,245 prior maps excluded. Accepted disruption 99.6% vs 74.7%, placement turns 87% vs 84%,
  2.60 vs 2.49 paint/turn, 66% early closures, 72-75 turns; 2.8-3.1 ms mean move vs 17.5-18.8.
  Style check 200 games vs leader-v1 (seeds 910000): **168-32 (84%)**. Smoke 40 games vs v09 with
  the first weights: 20-20.
- **Tests:** 7 new `NeuralBotTest` (weights/feature consistency, legality and near-100% disruption
  over 40-turn games on both seats vs v09, closing-ink guard on a corridor fixture, board-only
  memory attribution, path-cost equality, deterministic rows and sequential placement), one merged
  `neural-v1` parity/warmup test in `FileMergerTest`; **362 Java tests pass**. Core unchanged.
- **Not done / next:** the 40-game cached-boss stage (live CG games, user authorisation) and any
  selection; v09 stays selected and `build/submit/Player.java` is untouched (the neural artifact is
  in `build/submit-neural/`). Evidence: `docs/evidence/neural-v1-training.json`,
  `docs/evidence/neural-v1-v4.json`, `build/bench/neural-v1/`.
