# v04: first arena loss diagnosis

## Scope and reproducibility

Snapshot taken on 2026-09-14 from the public CG API: aabzac, agent **6735801**, rank **477**,
score **11.78**. The user identifies the latest submission as v04; the public replay gives an
agent id, not a verifiable source-code hash. Completion of arena placement was not established.

Fetched the latest **50 completed games**, ids 903504921..903511768 (not every id in that range).
All contain the selected agent. This is a recent, matchmaking-selected sample with repeated
opponents, not a randomized strength test or proof of a change in ranking.

Commands:

```powershell
.\gradlew.bat cgFetch -Pplayer=aabzac -Plimit=50
.\gradlew.bat cgValidate cgMapValidate
.\gradlew.bat cgAnalyze -Pagent=6735801
```

Both fidelity checks passed **158/158**, including all 50 new games. No game rules or strategy
were modified. Canonical replays remain in `replays/cg`; the local, gitignored report is
`build/analysis/agent-6735801.json`. It contains the exact game list and per-turn traces. Later
fetches may add games to the default corpus; preserve this report for the dated snapshot.

`cgAnalyze` uses accepted moves from `Rules.resolve`, including AUTOPLACE expansion and paint
cost, not just command counts. All arrays use P0/P1; `me` identifies our position. Turn numbers
below are **zero-based**. Final scores include server failure penalties; per-turn income and
scores are the game-rule results before such penalties. Timeout turns ignore retained late
stdout and have no counterfactual recommendations.

## Observed outcomes

**23 wins, 27 losses, no draws or server timeouts.**

- Height 14: 2 wins / 0 losses; height 15: 8 / 1; height 16: 5 / 0; height 17: 3 / 1.
- Height 18: 4 / 6; height 19: 0 / 10; height 20: 1 / 9.
- Combined: heights 14-17 **18/20 wins**, heights 19-20 **1/20 wins**.
- 20/27 losses reach the 100-turn limit; losses average **95.4 turns**, wins **81.1**.
- In losses, we disrupt on **1033/2576 turns (40.1%)**, opponents on **2122/2576 (82.4%)**.
  In wins the corresponding rates are **57.6%** and **77.0%**. Skipping a disrupt is not
  intrinsically wrong; a legal/profitable target or enough time may be absent.
- In losses we spend **2.46 paint/turn**, opponents **1.84**. Yet **1900/5284 (36.0%)** accepted
  cells never earn an own point during the recorded game, versus **518/3663 (14.1%)** for the
  opponents. This includes clashes and cells destroyed before earning; it does not imply all
  such placements were useless (blocking, threats and future possibilities can matter).
- We first earn later than the opponent in **18/27 losses**, but also in **10/23 wins**.

Map size and opponent identity are confounded. The sample does not prove that larger maps alone
cause losses. It does give a much more specific diagnostic target than aggregate arena rank.
The old top-player corpus's roughly 50-turn games are not representative of these opponents.

## Finding 1: route search can starve sabotage

In `EvalBot.play`, candidate building, idle extension and `chooseDisrupt` share one `Timer`.
Sabotage is last; its region loop is guarded by `!timer.up()`. A route search that uses the
allowance can return valid placements and no sabotage. Thus **no server timeout does not mean
all decision phases ran**. This scheduling pattern is shared by v03 and v04.

Game **903509641**, P1 against ChaosCat, 30x20, 12 towns / 48 desired pairs:

- Final **15237-51873**, only **9 disrupt turns out of 100**.
- Region **46** receives our three disrupts at t1, t2, t3, then remains available.
- At t9, the recorded action has placements but no sabotage. Keeping those placements and the
  opponent's same-turn action, adding DISRUPT 46 improves the immediate score difference by
  **73 points** and does not end the game.
- At t40 the recorded action is `PLACE_TRACKS 13 2` with no sabotage. The same-turn alternative
  DISRUPT 46 improves the score difference by **68 points**, without ending the game.

Local timing probes on that exact t40 pre-turn state, frozen `v04?NOISE=0`, three repetitions
per allowance after warm-up:

- **36 ms:** `PLACE_TRACKS 13 2`, no sabotage, elapsed 38 ms in all three probes.
- **50 ms:** the same action without sabotage, elapsed 50-53 ms.
- **200 ms (diagnostic only):** adds another placement and `DISRUPT 46`, elapsed 122-126 ms.
- With `USE_IDLE_EXTEND=0`, the missing sabotage at 36/50 ms and its return at 200 ms remain.

The second large-map game **903508669**, P1 against ChaosCat, confirms the mechanism: t40 returns
two placements without sabotage at 36/50 ms, but includes DISRUPT 15 with the generous allowance.
Its actual first disrupt was only at t90 and it ended **13014-21016**.

The probes are recorded in `build/analysis/timing-probes.json`; the local diagnostic source is
`tmp/ProbeReplayTiming.java`. They use NOISE=0 and probe recorded states, not the original CG
process's random/JIT history. Allowances ran in fixed order, so exact elapsed-time comparisons
include warm-up drift. The strong repeated t40 failure demonstrates local phase starvation;
without per-phase server telemetry it cannot explain every absent arena command. **200 ms is
not a proposed submission budget.**

Priority experiment: reserve search time for sabotage or obtain a cheap legal fallback before
route search, then improve it while time remains. Keep the global 50-ms limit and safety margin.
Test both large-map captured states and diverse opponents; do not promote solely on self-play.

## Finding 2: some partial builds hurt the current score balance

Across the 27 losses, **51 turns in 24 games** have a better immediate score difference if all
our placements that turn are omitted while keeping both recorded sabotage commands and the
opponent's placements. This also happens on 34 turns in wins, so it is a tactical diagnostic,
not an automatic label of a losing strategy.

Concrete example: **903507615**, P0 against DiogoGiassi, final **20881-21760**:

- t46: we build **(12,2)**; the opponent only disrupts region 37. Actual income is **410-315**.
  Without our placement the income is **433-300**, an immediate score-difference improvement
  of **38**. No hidden opponent placement is needed for this counterexample.
- t59: we build **(14,5)**; omitting it improves the current score difference by **24**.
- Both primary cells are also selected by frozen v04 with idle extension disabled in the local
  probe. Therefore these examples do **not** establish that the v04 idle-extension feature
  caused the problem; the older full-route/partial-build evaluation is implicated as well.
- We lead through t63, then fall behind at t64 after income drops. That is a useful replay
  window, not proof that reversing one earlier placement would win the match.

The builder evaluates a completed candidate network, then emits only the affordable part. The
actual partial network may change shortest paths unfavorably before the plan is completed.
Possible follow-up: evaluate the actual emitted placement prefix and sabotage together, while
allowing explicitly valued multi-turn investments rather than blindly rejecting every short-term
loss. First fix phase scheduling; this is a separate strategy experiment.

## Counterfactual limits

All alternatives use the opponent's **same simultaneous turn** actions and `btk.core` for full
resolution. The actual trace is never advanced from an alternative state. Later recorded enemy
commands would be responses to a different board and cannot serve as a reactive opponent.

The report's best disruption maximizes **this turn's score difference** only; a higher immediate
value may worsen long-term survival or close the game while behind. Terminal status is recorded.
Do not add deltas across different hypothetical branches or equate them with final lost points.

## Decision

The timing control used 150 new generated maps (seeds 4000..4149), two games per map, frozen
v04 versus v03 and two worker threads. Both CG 486/36-ms and legacy 1000/50-ms profiles produced
**191-109-0 (63.7%)**, map-grouped 95% CI **[52.6%, 74.8%]**. Individual winners changed in
**36/300 games**. Mean score difference was +249.1 versus +334.2; identical win rates do not
establish identical behavior or absence of a timing effect. Reports are under
`build/bench/v04-v03-{cg,legacy}-4000.json`.

Both illustrated episodes were also checked in the visualizer. Its position **41** corresponds
to diagnostic t40 for ChaosCat, and position **47** to t46 for DiogoGiassi: position 0 is the
initial state. The three cited canonical replays have rendered copies under
`replays/rendered/cg/arena/`.

Retain v04 unchanged for now. Align local measurement budgets, preserve the fresh loss corpus,
and prioritize guaranteed sabotage computation on large maps. Follow with partial-build
evaluation and an opponent pool that tests different styles. No new submission is needed for
the tooling-only changes in this checkpoint.
