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
  Deployment of the new warmup artifact and current placement status are not yet established.
- Clarify benchmark status: no new ordinary strength bench was run for the MainBot warmup
  fix, because that benchmark bypasses the hook. The 8+8 process comparison and final 8-game
  artifact check measure startup/protocol/timing, not statistically established playing strength.
  Last strategy screen remains interrupted eval: 51.3% vs v05, not promoted.
- Next planned step: fetch a bounded latest sample (up to 50 completed games for aabzac),
  identify arena entry and fresh-vs-existing game IDs, validate before analysis, and inspect
  fresh losses separately from the old corpus. Do not attribute rank changes to warmup or mix
  arena entries/league populations into a single improvement claim. No upload or submission.
