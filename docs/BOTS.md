# Bot versions

Active strength policy (2026-10-21, step 1a added 2026-10-26): **practical-v5** (`benchmarking-v5.md`): proxy screen vs the
validated boss-like opponent (+3 pp over the control on the same maps), **replication of a pass on a fresh pool (positive,
pooled ≥ +3 pp)**, non-inferiority vs the
selected version (≥ 48 %), 80 paired live boss games (≥ control + 5 pp; one extension to 120).
For a candidate that is itself the proxy, the v4 pooled 1000-game run replaces the first two steps.
Previous policy (2026-09-20 to 2026-10-20): **practical-1000-futility-boss-cache-v4**. One pooled
1000-game comparison in blocks 300/+200/+500; stop below 50% at 300/500, final pass >=55%
over all 1000, no early pass. Also require exactly 40 NEW
candidate games against the live boss on the saved control's maps/seeds/seats, scoring
at least as much as the cached control (draws half, ties pass). This replaces mandatory
older/style-opponent stages. Sequential boss calls may run alongside the required local
1000; direct failure cannot be rescued. Reused boss maps are a practical regression pool,
not fresh confirmation. Engine, errors and exact-artifact process checks still apply.
The boss mechanism remains [the v3 cache rule](boss-cache-policy-v3.md); local selection follows
[benchmarking.md](benchmarking.md). The former v3 local thresholds remain in historical entries.
Historical entries retain the policy and verdict in force when those experiments ran.
`current` = **v12 Exposure Income** (since 2026-10-25 night; v11 Root Target 2026-10-25, v10 Boss Model 2026-10-23),
`btk.bot.current.RolloutBot`; frozen copies in `btk.bot.vNN`.

**`bypass-routes-v1`: B2 = `current?USE_BYPASS_ROUTES=true` — 2026-10-27, passed v5 (a), 1a and (b), live session 1 +7.5 pp → promoted to v13:** for each own
earning connection crossing non-town regions at instability ≥ 2 the bot proposes the cheapest route avoiding them, valued half by the
plain income gain and half by the income kept after those regions are inked (`// BYPASS` block, default off), from `late-game-v1` (the
losses are decided in the middle third, where v12 puts 9 % of its paint on bypasses vs the opponents' 20 %). Step (a), pool 1019000:
**+3.0 ± 5.3 pp** over `current` vs boss-v2 (82.3 vs 79.3 %), h2h **55.0 %**; step 1a, pool 1020000: +5.3 ± 5.9 → **pooled +4.2 ±
4.0 pp**. B3 (instability ≥ 3): +3.7 pp but h2h 46.7 % → stop. Artifact 94,839 chars (config `8c4b3d6d…`), strict procCheck 0 problems;
live session 1 (80 games vs v12 on 40 matched maps): **B2 24/40 vs v12 21/40, +7.5 pp (5 : 2 discordant, p = 0.23), 40/40 + 160/160
replays exact** — passes the session threshold; **promoted to v13 "Bypass Routes"** on the user's decision (session 2 skipped;
[v13 promotion](v13-promotion.md): default artifact 94,816 chars, config `8c4b3d6d…`). [Protocol and results](bypass-routes-v1.md),
[evidence](evidence/bypass-routes-v1/).

**`route-commit-v1`: C10 = `current?USE_ROUTE_COMMIT=true` — 2026-10-27, passed v5 (a), 1a and (b), live session 1 −10 pp → parked:** a root that
continues the unbuilt part of last turn's chosen route gets `COMMIT_BONUS` (10) rollout-value points (`// COMMIT` block, default off),
from the order study `route-order-v1` (v12 leaves routes half-built: completions +4.3 turns over the minimum vs the opponents' +1.5).
Step (a), pool 1017000: **+7.0 ± 5.5 pp** over `current` vs boss-v2 (82.3 vs 75.3 %), h2h **52.7 %**; step 1a, pool 1018000: +2.5 ± 5.1 →
**pooled +4.8 ± 3.8 pp**. Bonus 25: +4.7 then +0.7 (pooled +2.7) → stop. Artifact 92,813 chars (config `f3229a23…`), strict procCheck 0
problems; live session 1 (80 games vs v12 on 40 matched maps): **C10 18/40 vs v12 22/40, −10.0 pp (3 : 7 discordant, p = 0.95), 40/40 +
120/120 replays exact** — fails the session threshold; **parked on the user's decision** ("Отложить", no second session). [Protocol and results](route-commit-v1.md),
[evidence](evidence/route-commit-v1/).

**`threat-weight-v1` — 2026-10-27, parameter screen, closed (0.5 stays):** `ROLLOUT_THREAT_WEIGHT` 0.25 / 0.75 vs boss-v2 on fresh pool
1016000 with `current` as the same-map control: −4.3 ± 6.6 / −1.0 ± 6.6 pp, head-to-head 49.3 / 54.0 %. Both fail step (a).
[Screen](threat-weight-v1.md), [evidence](evidence/threat-weight-v1/).

**`retro-replication-v1` — 2026-10-26, step 1a applied to the last two promotions, both real:** retro-replication on fresh pool 1015000 vs the policy proxy (`tools/boss_proxy/run_replication.sh`, `docs/evidence/retro-replication-v1/`):
**root target (v11 vs v10 on current) +6.7 ± 7.6 pp → pooled with its selection screen +7.8 ± 5.2 pp; exposure income (v12 vs v11)
+8.3 ± 6.2 pp → pooled +6.0 ± 4.6 pp; both pass step 1a**; v12 over v10 on the same maps +15.0 ± 7.0 pp.
[Evidence](evidence/retro-replication-v1/).

**`boss-v4` — 2026-10-26, proxy diagnosis + no-close validation + replication, no bot change:** the boss's attacks on our
under-construction regions are predicted at 82 % by the shipped weights; the replay agreement gap was the proxy's winning-closure
shortcut (the live boss declines such closures 94–98 %); `USE_BOSS_NO_CLOSE` (proxy skips closures, default off) fails its predeclared
validation (K1 +2.7, Q +3.0, v11 config −3.7 pp, pool 1014000). **Replication against the policy proxy on the same fresh pool: K1 −1.7 ±
6.1 pp (was +6.5), Q −6.0 ± 6.7 pp (was +6.3)** — both construction passes were selection noise; on fresh maps the proxy agrees with
the live signs. Proposed policy amendment: replicate step (a) on a fresh pool before the live step. [Study](boss-v4.md),
[evidence](evidence/boss-v4/).

**`opp-hold-v1` — 2026-10-26, hold-margin scan of the bot's boss model, closed (1.0 stays):** new field `BOSS_OPP_HOLD_MARGIN`
(the lite chooser's margin when it plays the simulated opponent; `// OPP_HOLD` block, default inherit). Pool 1012000 vs boss-v2 with
`current` as the same-map control: opponent model margin 0 **−8.2 ± 6.3 pp** (45.7 % h2h), margin 0 everywhere (`BOSS_HOLD_MARGIN=0`)
**−9.0 ± 6.6 pp** (51.0 %); pool 1013000: margin 2 +0.0 ± 6.7 pp (51.3 %); against the margin-0 proxy all three within noise (+0.5 /
+1.5 / +3.0). The rollout's opponent model is worth its commitment, not its per-turn agreement with the boss. [Protocol and
results](opp-hold-v1.md), [evidence](evidence/opp-hold-v1/).

**`earning-delay-v1`: Q = `current?USE_QUICK_ALWAYS=true` — 2026-10-26, passed v5 (a)+(b), failed (c) against the live boss — parked:** the cheapest positive
completion (≤ 6 paint) is kept among the root routes every turn, not only before the first income (`// DELAY` block, default off).
Fresh pool 1011000 vs boss-v2 with `current` (v12) as the same-map control: **82.7 % vs 76.3 % (+6.3 ± 5.6 pp, maps 43/23/84)**,
head-to-head **52.7 %**; vs neural-v6 58.7 % vs 58.7 % (+0.0 ± 7.1 pp). D2 = `current?DELAY_POWER=2` (the proposal value's
turns-to-complete divisor squared): +0.3 pp vs the proxy, 49.7 % h2h, +5.0 pp vs neural-v6 — fails (a), parked. Q artifact 91,619
chars (config `41996066…`), strict procCheck 0 problems; live session 1 (80 games vs v12 on 40 matched maps, launched by Claude on the user's word): **Q 18/40 =
45.0 % vs v12 20/40 = 50.0 %, paired −5.0 pp (9 : 11 discordant, p = 0.75), 40/40 + 80/80 replays exact → parked, flag off**; the
loss sits on the tall maps (30×20 2 vs 6 of 9). Both construction traits of the arena's top half passed the proxy and failed the boss
(K1 −15, Q −5): the construction line is closed at the proxy's level. [Protocol and results](earning-delay-v1.md), [evidence](evidence/earning-delay-v1/).

**`boss-v3` — 2026-10-26, boss-model refit + hold-margin study + sign check, closed, proxy unchanged:** the target logit refitted on the
whole corpus (1,180 games) with the 80 K1-session games held out scores no better than the shipped weights (holdout commitment top-1
0.556 / 0.509 vs 0.562 / 0.515; time-to-ink indicators and non-earning tracks add nothing); `BOSS_HOLD_MARGIN=0` agrees 58.8 / 62.6 %
with the live boss vs 54.1 / 58.1 % at 1.0 (uniform, the K1 blind spot untouched); K1 vs the margin-0 proxy on fresh pool 1010000 with
`current` as the control: 74.7 % vs 72.3 %, +2.3 ± 7.5 pp — the sign does not flip (live −15 pp). Construction changes that concentrate
tracks before they earn are not trusted on the proxy's sign. [Study](boss-v3.md), [evidence](evidence/boss-v3/).

**`proxy-agreement-v1` — 2026-10-26, study of the boss's targeting on K1's 80 live games (`BossTargetStudy`), no bot change:** the
boss disrupts the region with the most of our tracks 51 % of the time and sends 36–40 % of its disrupts to our under-construction
concentrations (≥ 3 tracks, no income yet); the boss-v2 proxy agrees with its region 58.1 % in the v12 games but 54.1 % in the K1
games, 29 % when a region holds ≥ 10 of our tracks, 46.8 % on the under-construction attacks (61.8 % in v12 games); neural-v6
41–44 %. The proxy scores income, the boss scores construction and time-to-ink — the blind spot behind K1's wrong sign. Next
(user decision): a `boss-v3` chooser fitted by agreement, re-screen K1 as the check. [Study](proxy-agreement-v1.md),
[evidence](evidence/proxy-agreement-v1/).

**`corridor-routes-v1` = `current?USE_CORRIDOR_ROUTES=true` (K1) — 2026-10-26, passed v5 (a)+(b), failed (c) against the live boss — rejected:** a fourth
route proposal per pair bent toward cells that other pairs' cheapest paths use (a free cell's paint ÷ (1 + `CORRIDOR_WEIGHT` × other
pairs), valued like every proposal; `// CORRIDOR` block, default off). Fresh pool 1009000 vs boss-v2 with `current` (v12) as the
same-map control: **78.0 % vs 71.5 % (+6.5 ± 7.2 pp)**, head-to-head **51.7 %**; vs neural-v6 **64.7 % vs 57.0 % (+7.7 ± 7.4 pp)**.
Weight 2: +1.2 pp vs the proxy (fails a), 50.3 % h2h, +5.3 pp vs neural-v6 — parked. Artifact 93,468 chars (config `299cafd9…`),
strict procCheck 0 problems; live session 1 (80 games vs v12 on 40 matched maps, launched by Claude under the user's allow rule):
**K1 18/40 = 45.0 % vs v12 24/40 = 60.0 %, paired −15.0 pp (3 : 9 discordant, p = 0.98), 80/80 replays exact → rejected, flag parked
off**; the proxy and neural-v6 predicted the sign wrongly (live sessions of the line: D +22.5, B +2.5, E1 +5.0, K1 −15.0). [Protocol and results](corridor-routes-v1.md), [evidence](evidence/corridor-routes-v1/).

**`arena-losses-v12` — 2026-10-26, diagnostic + cross-check, closed:** v12's 18 arena losses (agent 6813957) are
construction-efficiency losses to the field's top half (6-10 vs opponents scored ≥ 25.6, 26-8 below): equal cell counts, the
opponent's earning cells serve 1.62 active connections per turn against our 1.25 and earn 2 turns earlier; sabotage and survival
are not the gap. The idle-cell split of 2026-10-25 is corrected (v12 44.9 % idle vs the field's 26.9 %; erratum in
`idle-cells-v12.md`). Cross-check `current?USE_ROLLOUT_FILL=0` vs neural-v6 on fresh pool 1008000 with `current` as the same-map
control: 47.0 % vs 56.7 % (−9.7 ± 6.9 pp) — the fill stays on both opponents (paint does not carry over, so the fill is free).
Next candidate: shared-corridor routes. [Write-up](arena-losses-v12.md), [evidence](evidence/arena-losses-v12/).

**`end-forecast-v1` — 2026-10-26, study + parameter screen on `current` = v12, closed:** no board feature forecasts the
remaining turns better than the existing 16-turn earning cap inside that cap (`btk.tools.EndgameStudy`, v12's 50 arena
games + the 1,104-game boss corpus: late error 3.6–4.5 turns for `100 − turn`, the same or worse for every fit);
`ROLLOUT_ROUTE_HORIZON` 12 / 10 vs boss-v2 on fresh pool 1007000 with `current` as the same-map control: −0.7 ± 6.0 /
−2.7 ± 5.8 pp, head-to-head 44.0 / 52.7 % — the default 16 stays. [Study and results](end-forecast-v1.md),
[evidence](evidence/end-forecast-v1/).

**`collision-avoidance-v1` = `current?USE_COLLISION_AVOIDANCE=true` — 2026-10-25 night, rejected:** a root's rollout value loses
`COLLISION_PENALTY` points per cell shared with the opponent's predicted first-turn cells (own merger block, default off). Fresh pool
1006000 vs boss-v2 with `current` (v12) as the same-map control: penalty 5 → 73.0 % vs 77.0 % (−4.0 ± 7.0 pp), penalty 15 → 71.7 %
(−5.3 ± 6.5); head-to-head 48.0 % / 42.0 %. The search already prices collisions in its reply scenarios; the aversion yields contested
cells. Closes the idle-cell line with `unstable-cost-v1` and `fill-off-v1`. [Protocol and results](collision-avoidance-v1.md),
[evidence](evidence/collision-avoidance-v1/).

**`fill-off-v1` — 2026-10-25 night, parameter screen on `current` = v12, rejected:** `USE_ROLLOUT_FILL=0` (the leftover-paint fill
of v09 switched off, motivated by the idle-cell diagnostic `docs/idle-cells-v12.md`) vs boss-v2 on fresh pool 1005000 with `current`
as the same-map control: 74.7 % vs 78.5 % (paired −3.8 ± 6.9 pp, tall maps 81 vs 92 %), head-to-head 43.0 % (small maps 37 %). The
fill is a strength; it stays. [Diagnostic and results](idle-cells-v12.md), [evidence](evidence/fill-off-v1/).

**`unstable-cost-v1` — 2026-10-25 night, parameter screen on `current` = v12, parked:** `UNSTABLE_COST` 2 and 3 (the
cheapest-path penalty per cell per instability level, default 1) vs boss-v2 on fresh pool 1004000 with `current` as the
same-map control: U2 79.0 % vs 78.7 % (+0.3 ± 6.6 pp), U3 80.7 % (+2.0 ± 6.8) — both below the +3 pp gate; head-to-head
52.7 % / 48.7 % with tall maps 42 % / 44 %. Default stays. [Protocol and results](unstable-cost-v1.md),
[evidence](evidence/unstable-cost-v1/).

**v12 Exposure Income — promoted 2026-10-25 night on the user's decision** ("Промоушн 12"): the exact stage4-exposure-income-v1
candidate E1 (`current?USE_EXPOSURE_INCOME=true`) became the default of `current`; frozen copy `btk.bot.v12`; `v12.properties`
= the 133 effective values = E1's artifact manifest. v5 record: proxy screen on pool 1003000 75.3 % vs boss-v2 against v11's
71.7 % on the same maps (+3.7 ± 6.7 pp), 60.3 % head-to-head vs v11; live boss session 1 20/40 vs v11 18/40 (+5.0 pp, the
session threshold met exactly, p = 0.40, tall maps 1 : 4 of 8) — the second session was skipped by the user. Default artifact
91,349 chars (sha `109be1f3…`, build `ce1d3a3b…`, config `d7803b42…` = E1's), strict 4-game check 0 problems; 837 tests, 25
pre-existing failures. **The boss-v2 proxy spec now needs `USE_EXPOSURE_INCOME=0` explicitly**; the v11 configuration on
current is `current?USE_EXPOSURE_INCOME=0`. **Arena: uploaded by the user the same night = puzzle agent 6813957, first 50 games
32W/18L, 0 failed turns, tall 14-6** (`evidence/arena-2026-10-25-v12-agent-6813957.json`). [Protocol](v12-promotion.md),
[evidence](evidence/v12-promotion/). v11 is historical.

**`exposure-income-v1` = `current?USE_EXPOSURE_INCOME=true` — 2026-10-25 late evening, Stage 4 second half, v5 steps (a)+(b)
PASS, live session 1 at the threshold, promoted to v12 the same night:** every connection's income is weighted by the survivability of the unstable non-town regions its path
crosses, (1 − instability / 4) each, in the rollout's tail and in the route proposals' gains. Same-map screen on fresh pool
1003000 (300 paired games/cell, CG timing, `docs/stage4-exposure-income-v1.md`): **75.3 % vs boss-v2 against v11's 71.7 %
(paired +3.7 ± 6.7 pp, 41/31/78 maps — at the +3 pp gate)**, **60.3 % head-to-head vs v11** (181-119, the chain's largest),
zero errors, move time unchanged. Half weight (E05): +1.0 pp vs the proxy, 54.0 % h2h → **parked**. Artifact
`build/submit-stage4-e1` 91,368 chars, config `d7803b42…`, strict 4-game check 0 problems; live plan
`build/cg-boss/stage4-e1-v11-boss-40` (80 API games vs the v11 artifact). **Live boss, v5 step (c) session 1 (2026-10-25,
user launch): E1 20/40 = 50.0 % vs v11 18/40 = 45.0 % in the same session, discordant 8 : 6, paired +5.0 pp (p = 0.40),
80/80 replays exact → the +5 pp session threshold met exactly (pass at the edge); tall maps 1 : 4 of 8; session 2 (40 more
maps) would decide step (c) on the pooled 80 — the user's call. No promotion, no upload.**
[Protocol and results](stage4-exposure-income-v1.md), [evidence](evidence/stage4-exposure-income-v1/).

**v11 Root Target — promoted 2026-10-25 on the user's decision** ("давай продвинем версию на арену"): the exact
stage4-threat-model-v1 candidate B (`current?USE_BOSS_ROOT_TARGET=true`) became the default of `current`; frozen copy
`btk.bot.v11`; `v11.properties` = the 131 effective values = B's artifact manifest. v5 record: proxy screen on pool 1002000
73.5 % vs boss-v2 against v10's 64.7 % on the same maps (+8.8 ± 7.1 pp), 57.0 % head-to-head vs v10; live boss session 1
21/40 vs v10 20/40 (+2.5 pp, inconclusive) — the second session was skipped by the user, so the live evidence is neutral.
Default artifact 88,997 chars (sha `ff111d54…`, build `002ca691…`, config `179dcc66…` = B's), strict 4-game check 0 problems;
830 tests, 25 pre-existing failures. The boss-v2 proxy spec is unchanged (the lite chooser off drops the v11 flag); the v10
configuration on current is `current?USE_BOSS_ROOT_TARGET=0`. **Arena: uploaded by the user the same evening = puzzle agent
6813813, first 50 games 34W/16L, 0 failed turns, tall 7-5, 30×20 3-4** (`evidence/arena-2026-10-25-v11-agent-6813813.json`).
[Protocol](v11-promotion.md), [evidence](evidence/v11-promotion/). v10 is historical.

**v10 Boss Model — promoted 2026-10-23 on the user's decision** ("без 2 сессии"): the exact stage2-compute-v1
candidate D (`NOISE=0.1` + `USE_BOSS_LITE`, `USE_BOSS_OPPONENT_MODEL`, `USE_BOSS_OWN_MODEL`, `USE_BOSS_LITE_ROUTES`,
`USE_INCREMENTAL_INK_REFRESH`, `USE_FAST_CHEAPEST_PATH`, `USE_BATCHED_REFRESH`) became the defaults of `current`;
frozen copy `btk.bot.v10`; `v10.properties` = the 130 effective values = the arena artifact's manifest. v5 record:
chain v2 → B → C → D each ≥ its predecessor on the same maps (D 67.0 % vs v09, 65.0 % vs boss-v2), live session 1
18/40 vs v09 9/40 (+22.5 pp paired); the second live session was skipped by the user. Default artifact 99,676 chars,
config sha `f0e507ac…` = the arena entry (build `2f896714…`, agent 6807052), strict 4-game check 0 problems; 818 tests,
25 pre-existing failures. The boss-v2 proxy spec is now `current?USE_BOSS_SABOTAGE=1,USE_BOSS_LITE=0,USE_BOSS_OPPONENT_MODEL=0,USE_BOSS_OWN_MODEL=0,USE_BOSS_LITE_ROUTES=0,USE_INCREMENTAL_INK_REFRESH=0,USE_FAST_CHEAPEST_PATH=0,USE_BATCHED_REFRESH=0`.
[Protocol](v10-promotion.md), [evidence](evidence/v10-promotion/). v09 is historical.

**`prune-v1` — 2026-10-25, a code change to `current`, not a strength candidate:** the 17 rejected
rollout experiments and their helpers left `RolloutBot` (1,601 → 1,076 lines) and 86 never-read `Params`
fields moved into `// LOCAL_ONLY` blocks that `FileMerger` drops from current rollout artifacts; default
artifact **99,676 → 70,397 chars** (sha `5047c83a…`, build `f7caee04…`, config `f0e507ac…` unchanged),
frozen `v10` export byte-identical. Equivalence: `PruneV1Test` lockstep vs `v10` with ample time — identical
decisions and counters on every turn (PASS); timed guard on pool 1001000: 50.7 % head-to-head vs `v10`,
68.3 % vs boss-v2 against `v10`'s 70.3 % on the same maps (paired −2.0 ± 6.5 pp); strict 4-game process
check 0 problems. The historical mechanisms run on `v10?FLAG=1` (tests in `btk.bot.v10`); `current?FLAG=1`
is refused. Arena entry since 2026-10-24/25: agent 6811139 (the fresh default v10 artifact, 29W/21L on its
first 50). [Protocol and results](prune-v1.md), [evidence](evidence/prune-v1/).

**`root-target-v1` = `current?USE_BOSS_ROOT_TARGET=true` — 2026-10-25, Stage 3–4 step 1, v5 steps (a)+(b) PASS,
live pending:** our first simulated turn in every rollout root disrupts the real-turn lite target (`liteHeld`), the
per-root `chooseTarget` and the "no disrupt" scenario are gone — the construction is now valued under the sabotage line
the real turn actually plays. Same-map screen on fresh pool 1002000 (300 paired games/cell, CG timing, 2 workers,
`docs/stage4-threat-model-v1.md`): **73.5 % vs boss-v2 against `current`'s 64.7 % (paired +8.8 ± 7.1 pp, 54/32/64
maps)**, **57.0 % head-to-head vs `current`** (171-129), zero errors, mean move 7.9 ms vs 11.7 (less work, not more).
The sibling flag `USE_BOSS_THREAT_MODEL` (A: the route proposals' predicted opponent target from the lite chooser)
is **parked**: +1.5 ± 7.1 pp vs the proxy (fails the +3 pp gate), 51.0 % h2h; A+B 70.7 % vs the proxy (+6.0) is below
B alone. Default artifact unchanged (both flags off, own merger blocks `// BOSS_ROOT` / `// BOSS_THREAT`). Strict
process check of B's exact artifact: 0 problem games. **Live boss, v5 step (c) session 1 (2026-10-25, user launch):
B 21/40 = 52.5 % vs `current` 20/40 = 50.0 % in the same session, discordant 10 : 9, paired +2.5 pp (p = 0.50), 80/80
replays exact → below the +5 pp session threshold, inconclusive; session 2 (40 more maps) would decide step (c) on the
pooled 80 — the user's call. No promotion, no upload.**
[Protocol and results](stage4-threat-model-v1.md), [evidence](evidence/stage4-threat-model-v1/).

**Live boss, 2026-10-23 (`stage2-d-live-boss-v1`, v5 step c session 1):** D's exact arena artifact vs v09
in the same IDE session, matched 40 maps × 2 builds = 80 API games vs boss 6767608 — **D 18/40 = 45.0 %,
v09 9/40 = 22.5 %**, discordant pairs 13 : 4, one-sided p = 0.0245, **paired +22.5 pp → PASS** (threshold
+5); 30×20 6/10 vs 1/10; 0 technical failures, 80/80 replays exact. Session 2 (40 more maps) decides
step (c) on the pooled 80. [Details](stage2-d-live-boss-v1.md), [evidence](evidence/stage2-d-live-boss-v1/).

Candidates 2026-10-22 (`stage2-dividend-v1`, two separate same-map screens against D, fresh pool
seed 980000, 300 games/cell, 2 workers, CG timing, D's own rows on the same maps as controls):
**R3** = D + `ROLLOUT_REPLIES=3` — 51.7 % vs D, 64.7 % vs boss-v2 (D 68.7; paired −4.0 ± 7.6 pp),
53.7 % vs neural-v6 (D 51.7), 67.0 % vs v09 (D 63.3). **D10** = D + `ROLLOUT_DEPTH=10` — 53.3 % vs D,
65.8 % vs boss-v2 (paired −2.8 ± 6.7), 54.0 % vs neural-v6, 63.0 % vs v09. Zero errors, no new
source. Both pass the formal "≥ 50 % h2h, no row below D by more than the cell error" reading and
**both fail the v5 step (a) gate (≥ D + 3 pp vs boss-v2) → parked, D unchanged.** Mean move time
rises only 12–16 → 14–18 ms: the slack is not spent by more replies/depth; on tall maps vs boss-v2
both lose coverage (D 76.3 % → 68.4 %). **Part 2 (pool 990000, same design): M128** = D +
`ROLLOUT_MAX_ROUTES=128` — **47.3 % vs D** (tall maps 41.2 %), 65.7 % vs boss-v2 (D 67.0; paired −1.3),
48.3 % vs neural-v6 (D 49.7), 63.0 % vs v09 (D 67.0) → **rejected**; **W8** = D + `ROLLOUT_WIDTH=8` —
52.0 % vs D, 65.8 % vs boss-v2 (paired −1.2), 53.7 % vs neural-v6 (+4.0), 68.7 % vs v09 (+1.7) → fails
the +3 pp gate, **parked**. Four search-size parameters, none beats D against the proxy and all lose
on tall maps against it: the dividend line is closed. [Details](stage2-dividend-v1.md),
[evidence](evidence/stage2-dividend-v1/).
**Arena, D (puzzle agent 6807052, first 50 games fetched 2026-10-22):** **30W/20L, 0 failed turns**,
rank 317 overall = **#7 of 712 in Silver**, score 28.06; 30×20 maps 3/11, h ≥ 19 10-12, low maps 20-8;
35 placement games 24-11, then 15 games vs --CC-- (Gold) 6-9; disrupt 97.7 % of turns, 40.7 % of
accepted cells never earned ([evidence](evidence/arena-2026-10-22-D-agent-6807052.json)). The
challenge leaderboard is frozen; `cgFetch` reads the puzzle leaderboard since 2026-10-22.

Candidates 2026-10-21 (evening), each screened on the same 150 maps (seed 970000, 300 games/cell) against its predecessor:
**boss-lite-routes-v1 (C)** = opponent-model-v1 + `USE_BOSS_LITE_ROUTES=true` — route-traffic term from the
rollout's own previous-turn portfolio (no path searches): 50.0 % vs B, 52.0 % vs neural-v6 (B 45.3;
second pool 57.0 vs 56.0 → +3.8 pooled), 62.8 % vs v09, 60.3 % vs boss-v2; artifact 96,373.
[Details](boss-lite-routes-v1.md). **stage2-compute-v1 (D)** = C + `USE_INCREMENTAL_INK_REFRESH,
USE_FAST_CHEAPEST_PATH,USE_BATCHED_REFRESH` (incremental refresh now also inside the lite chooser):
×2 simulated turns, **53.7 % vs C, 67.0 % vs v09, 65.0 % vs boss-v2, 49.7 % vs neural-v6**, mean
11.8 ms/turn; artifact 99,683. [Details](stage2-compute-v1.md). Neither live-tested; D is on the arena since 2026-10-21 evening (agent 6807052, see above).
**Arena baseline, v09-era build `4cda8b1b` (agent 6755892, 50 games fetched 2026-10-21):** 23W/27L, 0
timeouts, ≈ #22 of 706 in Silver; 30×20 maps 4/11, 21×14 1/7; 41–46 % of accepted cells never earned.

Candidate 2026-10-21 (latest): **opponent-model-v1 — v2 + `USE_BOSS_OPPONENT_MODEL=true,USE_BOSS_OWN_MODEL=true`, v5 steps 1–2 PASS, live pending.**
Stage 1 of the 2026-10-20 plan: the simulated opponent inside the rollout disrupts with the same
two-stage boss chooser (held target observed from the board and carried through the simulated
turns), and so do our own simulated future disrupts. Same-map screen (300 games/cell, seed 970000):
**57.8 %** vs boss-sabotage-v2, **62.3 %** vs boss-v2 (v2: 48.3), **45.3 %** vs neural-v6 (v2: 40.0,
v09: 30.7), **63.0 %** vs v09 (v2: 62.7); the opponent model alone (A) lost 6 pp vs v09, the own
model removes that. Cold-JVM max 36–38 ms on 30×20; artifact 95,421 chars. Recommended live
candidate, v2 the fallback. [Details](stage1-opponent-model-v1.md).

Candidate 2026-10-21 (later): **boss-sabotage-v2 — `current?NOISE=0.1,USE_BOSS_LITE=true`, v5 steps 1–2 PASS, live pending.**
Two-stage boss chooser at the real turn (cheap fitted logit ranks regions in O(cells), top-4 get one
ink simulation and the full score; no path searches; held target; timer-guarded) — offline 51.1 %
top-1 vs 51.5 % for the full chooser. Artifact 93,695 chars `a8c739fd…`; cold-JVM max 38–40 ms on
30×20 (v09 36–40, v1 44–53). Step 1 vs neural-v6: **50.3 %** against the v09 control's 38.8 % on
the same maps (+11.5 pp, threshold +3). Step 2 vs v09, 300/+200/+500: 64.8 / 67.5 / 61.3 %,
pooled **622W/350L/28D = 63.6 %**, zero errors. [Details](boss-sabotage-v2.md).

Candidate 2026-10-21: **boss-sabotage-v1 — local 1000-game stage PASS, 67.45 %; arena 2026-10-21 (user upload): rank ~150–200/700, worse than neural-v3 and worse vs the boss → NOT PROMOTED** (DEVLOG 2026-10-21: v09-specific gain, neural-v6 58 % against it; unguarded chooser 16–26 ms cold-JVM on 30×20 maps).
`current?NOISE=0.1,USE_BOSS_SABOTAGE=true` vs frozen v09 on fresh paired maps (seeds
940000/941000/942000), CG 486/36 ms, two workers: **205-95 / 140-60 / 329-170-1 = 674W/325L/1D**,
zero errors, P0 67.0 % / P1 67.8 %, +454 mean score difference, 99 % accepted disruption vs 78 %.
Artifact 94,845 characters (compacted), compiles; default-off flag leaves `current`/`v09`
artifacts byte-identical. Pending under policy v5: strict `procCheck` and 80 paired live boss
games. v09 stays selected. [Details](boss-proxy-v1.md#63-strength-of-the-flag-itself-boss-sabotage-v1-candidate-id).

Measurement reset 2026-10-20: **boss-proxy-v1 / boss-sabotage-v1 (screen)**. All 855 canonical
boss replays studied (`BossIntentStudy`, `CgLeaderStudy` on "Boss 4"); conditional-logit target
model, held-out top-1 51.0 %. New local-only reference `boss-v1` (`btk.bot.reference.BossBot`):
boss-like sabotage, fitted construction; fingerprint matches the boss's sabotage but wins only
30.5 % vs v09 (boss: 67.5 %) — **rejected as strength proxy, kept as behavioural reference**.
New default-off flag on `current`: `USE_BOSS_SABOTAGE` (`btk.bot.current.BossSabotage`, the same
fitted chooser replacing v09's real-turn DISRUPT). `current?USE_BOSS_SABOTAGE=1` vs v09:
**221W/179L/0D = 55.25 % over 400 games** (100 + 300, seeds 920000/930000, fresh maps, CG
486/36 ms, zero errors); disrupt 99 %, games 77 turns, 69 % closures. Accepted as the policy-v5
screening opponent ("boss-v2"); as a candidate it has only this screen — no fixed 1000-game
run, strict process check or live games yet. Ordering check vs boss-v1 (200 games each): v07 53.0,
v08 62.0, v09 74.0, neural-v6 78.5, neural-v1 76.0, all-cell-risk 83.5, root-fill 76.5,
live-target 72.0, batched 73.5 %. Ordering check vs boss-v2 (2026-10-21, 200 games each):
v07 23.5, v08 29.0, v09 38.0, neural-v6 58.0, neural-v1 53.5, all-cell-risk 44.0, root-fill 38.5,
live-target 35.5, batched 36.5 % — 6/8 live directions reproduced, caveats neural-v1 and
all-cell-risk ([§6.2](boss-proxy-v1.md#62-ordering)). v09 stays selected; nothing uploaded.
[Protocol and results](boss-proxy-v1.md), [policy v5](benchmarking-v5.md), [STATUS](STATUS.md).

Local budget experiment 2026-10-18: **v09-ink45-strength-v1, NOT_PASSED**.
Same incremental-ink candidate at 45 versus 40 ms, with batching ON and
NOISE=0.1; only budget differs. Fresh 500 paired maps, two workers, actual
486/45 versus 486/40 ms. Blocks 146W/144L/10D, 99W/95L/6D, 245W/229L/26D;
pooled **490W/468L/42D, 51.1% <55%**, zero game errors. Timing gate explicitly
waived; no new process or boss games. All 29 synthetic checks pass; old
strategy/core/runner bytes preserved through minimal budget-validation overlay.
Independent audit PASS: 42,028 checks, 3,824 pins; seats 52.1%/50.1%.
Keep 45 experimental, selected v09/default 36/flags OFF and prior timing failures.
[Result](v09-ink45-strength-v1-result.md), [evidence](evidence/v09-ink45-strength-v1.json).

Budget experiment 2026-10-18: **v09-ink45-boss-v1, 14W/26L in40 attempts (35.0%)**,
including one retained startup failure at slot14. Same incremental-ink policy
as cached ink40 (11W/29L); only budget45/40 differs. Matched outcomes8 better,
5 worse,27 equal. Boss public update metadata changed, so +3 wins is historical
descriptive evidence, not a confirmed budget gain. All40 maps verified;39
qualified replays exact (14W/25L), slot14 actual execution UNKNOWN/map-only.
Original launcher stopped; separately recorded continuation ran only untouched
slots15–40, no replacements. New source91,748 chars;26 Java,27 helper and11
continuation checks pass. Minimal strict2-game process diagnostic failed twice
(51.6577/51.0781ms), timing gate explicitly waived. Retain technical NOT_PASSED,
readinessfalse, old local54% and timing failures; keep default36/flagsOFF/v09.
Public evidence audit PASS with1,013 pins, pause300.027878s; no arena upload/push.
[Result](v09-ink45-boss-v1-result.md), [evidence](evidence/v09-ink45-boss-v1.json).

Requested boss observation 2026-10-18: **v09-ink40-boss-v1, complete 11W/29L,
27.5% in 40 games**. Exact ink40 candidate, no new control games or strategy
edits. Same maps/seeds/seats/order as cached B40 13W/27L; 5 better, 7 worse,
28 equal outcomes. Live boss6767608/league6/3/0 unchanged but public update
timestamp changed, so old13/40 is historical context only: experiment boss
status NOT_ASSESSED_CHANGED_BOSS. All40 first attempts and replay validations
complete, zero technical failures; 23 synthetic helper tests pass. Ordinary
minimum gap21.228412s, game20-to21 gap300.039822s. Independent evidence audit
PASS with942 input pins. User explicitly authorized this candidate-only series
despite retained timing NOT_READY and local54% NOT_PASSED; neither is revised.
Keep incremental ink OFF, default36 and selectedv09; no arena submission/push.
[Result](v09-ink40-boss-v1-result.md), [evidence](evidence/v09-ink40-boss-v1.json).

Local strength experiment 2026-10-18: **v09-ink40-strength-v1, NOT_PASSED**.
User explicitly waived the timeout prerequisite for this local comparison;
the prior NOT_READY timing result below remains unchanged. Same batched40/
NOISE0.1 base, ink ON/OFF, fast path/deadline OFF, preserved native runtime.
Fresh 500 paired maps, two workers, actual 486/40 ms each arm. Blocks
154W/134L/12D (53.3333%), 114W/78L/8D (59.0%; pooled 55.6%),
250W/226L/24D (52.4%); all 1,000 games **518W/438L/44D, 54.0% <55%**.
Seats 54.4%/53.6%, zero game errors. Native slow counters 8/6, reported
maxima 65/67 ms include opening calls; diagnostic only, not full-response
measurements. New driver 35 synthetic tests pass; independent reconciliation
PASS 41,876 checks. No new process or boss games, production edits or promotion.
Keep default36/all experimental flags OFF and selectedv09. Separate samples
at36 (52.7%) and40 (54.0%) do not establish a causal budget improvement.
[Result](v09-ink40-strength-v1-result.md), [evidence](evidence/v09-ink40-strength-v1.json).

Budget condition 2026-10-18: **v09-ink40-v1, NOT_READY**;
strength **NOT_RUN_READINESS_FAILED**. Previous incremental-ink optimization,
same batched40/NOISE0.1 base, inkON/OFF, fast path/deadlineOFF, no production edits.
Preserved old native runtime and inherited engine292/ample524 evidence; fresh
exports reproduce A36/B40 identities, A40 main91,714 characters. All16 strict
process games complete once, first/later1000/50 ms, zero grace, opponentB40.
Of1,353 later responses, two time out at50.2949/52.1667 ms (maps904146009/P1/
turn36 and903182662/P1/turn41); two associated game errors, zero missing replies.
Median33.8388,p95 42.5540,p99 43.0957 ms; first max681.5245 ms. No timing retries,
new strength maps/games or boss calls. Conditional harness28 synthetic tests pass.
Keep default36/all experimental flags OFF, selectedv09 and all earlier verdicts.
[Result](v09-ink40-v1-result.md), [evidence](evidence/v09-ink40-v1.json).

Forecast experiment 2026-10-18: **v09-fast-path-v1**, with
**v09-fast-path-strength-v1: STOP_FUTILITY_300**. Default-off geometry-only
neighbor reuse in cheapestPath; strategy, path ties, safeguards and budgets
unchanged. Same batched36/NOISE0.1 base, incremental ink/deadline correction OFF.
All524 ample decisions/diagnostics exact; equal-work time-7.22%, completed
comparisons at36 ms2,995 to3,127 (+4.41%), complete searches281 to276.
Turn40 attack and six winning closures preserved. Engine292/292 exact;
focused260/261 including12 new passes, one retained historical catalog-pin
failure. Retain initial100,008-character guard failure and identifier-only
premeasurement repair; final main91,481 and guard99,924 characters.
Strict8 process games PASS,773 later responses, max46.708 ms, no errors,
timeouts, overruns or missing replies. Fresh-map strength: two workers,
486/36 ms, **134W146L20D /300 =48.0%**, zero errors. Stop at the first
futility look; no block2/3 or boss games. Native maxima43/41 ms exclude IO.
Keep fast path, batching, incremental ink and deadline flags OFF, default36
and selectedv09; no upload, promotion or push. Preserve earlier verdicts.
[Result](v09-fast-path-v1-result.md), [evidence](evidence/v09-fast-path-v1.json).

Strength experiment 2026-10-18: **v09-ink-strength-v1**, **NOT_PASSED**.
Same batched36/NOISE0.1 base in both arms; only incremental ink refresh differs.
Two workers, 486/36 ms both, 500 fresh paired maps disjoint from 25,124 known
identities. Fixed 300/+200/+500: 157W125L18D / 90W90L20D / 250W228L22D;
pooled **497W443L60D / 1,000 = 52.7%**, below 55%, zero game errors.
Native mean 16.13 vs 17.73 ms; candidate three calls >50 ms, max63, control
one, max51. Counters include first turns and are not full-response deadlines.
Reuse unchanged prior engine292/292, ample524 and strict8 process readiness;
retain known historical catalog test failure and guard packaging limitation.
21 new synthetic checks pass; independent audit PASS, 21,584 checks,
3,819 files +7 runtimes and105 prior artifacts preserved. No new process/boss
games, retries, upload or promotion. Keep default36, all experimental flags
OFF and selectedv09; earlier verdicts unchanged.
[Result](v09-ink-strength-v1-result.md), [evidence](evidence/v09-ink-strength-v1.json).

Forecast optimization 2026-10-18: **v09-ink-refresh-v1**, **COMPLETE_DIAGNOSTIC**.
Default-off selective refresh after hypothetical ink; recompute only connections
that lost a connector, preserving canonical ties, strategy, timers and safeguards.
Main comparison on the existing batched36/NOISE0.1 base: all **524 ample inputs
match exactly**, 5,850 comparisons and 87,630 simulated turns per arm; equal-work
time **-15.64%**. At36 ms, comparisons **2,523 to 2,901 (+14.98%)**, completed
searches254 to295. Native max43.9605 ms. Supplementary historical nonbatched
deadline controls preserve turn40 attack and all six winning closures, zero
lost wins. Main artifact91,202 characters; strict8 process games PASS, zero
errors/timeouts/overruns/missing, later full-response max46.505 ms. Engine292/292
exact; focused tests248/249 with one retained historical catalog-pin failure.
The three-flag batching/deadline/incremental combination exceeds the source cap
at100,447 and remains rejected; guard comparison uses the documented amended
historical recipe. No strength or boss gate, upload or promotion. Keep default36,
batching/incremental/deadline OFF and selectedv09; prior verdicts unchanged.
[Result](v09-ink-refresh-v1-result.md), [evidence](evidence/v09-ink-refresh-v1.json).

Local timing diagnostic measured2026-10-16, recorded2026-10-18:
**v09-budget100-local-v1**, **COMPLETE_DIAGNOSTIC**, no promotion eligibility.
Identical batched-refresh/NOISE0.1 policy, local later allowances100 vs36 ms,
first486 both, two workers, 500 fresh paired maps disjoint from24,624 known maps.
Fixed300/+200/+500 completed once:157W126L17D /111W79L10D /280W202L18D;
pooled **548W407L45D /1,000 =57.05%**, zero errors. Later native mean19.179 vs
13.689 ms; completed comparisons+27.64%, simulated turns+29.09%, ordinary
completion97.47% vs84.05%. At100,12.25% of later calls exceed50 ms; this is
compute sensitivity, not an arena-ready budget. Engine292/292 exact, fake-only
harness checks and14 Python tests PASS; preserve2,523 frozen files+8 runtimes
and28 selected submission files. Production strategy/source unchanged;
default36, batching OFF, selectedv09 and earlier verdicts stay unchanged.
No reruns after chat interruption, boss/API work, upload or promotion.
[Result](v09-budget100-local-v1-result.md),
[evidence](evidence/v09-budget100-local-v1.json).

Exploratory boss follow-up 2026-10-16: **v09-boss-budget40-followup-v1**,
**COMPLETE_EXPLORATORY / HISTORICAL_NOT_PASSED_UNCHANGED**. Complete the old
batched36 v2 panel with 12 new games: final **10W30L**, cached v09 **11W29L**.
Then exactly 40 unchanged budget40 games on the same maps/seeds/seats/order:
**13W27L**. Paired 40-vs-36: better7/worse4/equal29, net +3 wins; 40-vs-v09:
better7/worse5/equal28, net +2. All 120 canonical replays exact; 52 new intents
produce 52 known games, zero new technical failures/refusals/replacements.
Preserve the original unknown 422/407 attempt29 and its one explicitly amended
attempt2 separately. Actual boundary gaps 301.641607/300.015568 seconds.
Independent audit PASS; all 325 baseline +39 frozen files unchanged; 24 focused
Python tests passed before play. Positive descriptive boss result does not
rescue the prior formal batching failure or budget40 local 51.0%/1,000 failure.
Keep default36, batching OFF and selected v09. No arena submission or promotion.
[Follow-up result](v09-boss-budget40-followup-v1-result.md),
[evidence](evidence/v09-boss-budget40-followup-v1.json).

Budget experiment 2026-10-16: **v09-budget40-v1**, **NOT_PASSED**.
Same batched-refresh/NOISE0.1 native strategy, later allowance 40 vs 36 ms;
first/warmup/external deadlines unchanged. Exact40 export 90,641 characters;
default/frozen/batched36/budget38 source identities preserved. Strict16 PASS:
1,479 later replies, p99 43.8367/max 47.8363 ms, nine >45 ms, none >48 ms;
zero errors/timeouts/overruns/missing. First maximum 732.0729 ms.
Fresh v4 blocks 300/+200/+500, two workers: 151W141L8D / 104W90L6D /
233W237L30D; pooled **488W468L44D/1,000 = 51.0%**, zero errors, below 55%.
Native-call diagnostic maximum 59 ms, one candidate call >50 ms; these counters
include first turns and are distinct from the full-response process check.
Keep36, batching OFF and selected v09; no boss, upload or promotion.
45 focused Java tests, 46 Python tests and 292 exact engine replays passed;
prior unrelated full-suite failures were not changed or claimed resolved.
[Protocol and result](v09-budget40-v1.md), [evidence](evidence/v09-budget40-v1.json).

Budget experiment2026-10-16: **v09-budget38-v1**, **STOP_FUTILITY_300**.
Same batched-refresh/NOISE0.1 native strategy in both arms; only later internal
allowance38ms vs36ms. First/warmup/external1000/50 unchanged. Exact38 export
90,607/100,000 characters;36 export byte-identical to prior batched candidate.
Strict16-game panel PASS:1,505 later responses, p9940.7075/max44.3156ms,
zero errors/timeouts/overruns/missing;16 first replies max634.2294ms.
Conditional local block on150 fresh paired maps: **143W145L12D/300 =49.6667%**,
zero errors, two workers, actual later budgets38/36. Stop below50% at300;
no further blocks or boss games. Timing fits this local sample; strength gain
not established. Keep36ms, batching OFF and selected v09; no promotion.
Full-suite preflight retained25 unrelated failures after repairing three
inventory assertions; relevant checks and292/292 engine replays pass.
[Protocol and result](v09-budget38-v1.md), [evidence](evidence/v09-budget38-v1.json).

Timing telemetry audit 2026-10-16: **v09-budget-telemetry-v1**, read-only;
no new games, policy calls or budget changes. Current target36ms within50ms,
margin14ms; WARN logs only integer totals>36 after stdout flush. Candidate68:
1599 WARNs/4811 later turns, conditional median38/max98ms,9>50 in8 games;
control80:2096/5717,median38/max106ms,13>50 in12 games; no explicit live failures.
Full local candidate responses678: p9941.3965/max46.7275ms,zero overruns.
WARN median is censored and cannot justify+5ms. Propose one separate38ms
experiment, not implemented; keep36ms and all prior verdicts unchanged.
[Budget and measurement explanation](v09-budget-telemetry-v1.md).

Offline diagnosis 2026-10-16: **v09-batched-loss-diagnosis-v1**, completed with
no strategy change or new games. All10 candidate-loss/v09-win pairs among68
completed pairs included (7 original+3 additional);20 replays/1363 turns exact.
All first own differences occur on identical states at turns1–3, before the
boss diverges;6 are protected-opening decisions. Actual-parameter ample panel:
761states/2283calls, frozen v09/current OFF/ON actions, work and value bits exact.
Zero missed guaranteed immediate one-hit winning closures in761 candidate states.
Six largest income drops involve known one-hit cuts; both native replies already
predict the correct target in6/6, and one-turn consequences match in5/6 with
recorded own actions. Thus omitted sabotage is not the supported explanation.
Core-only60 crossings identify v2-010/t11 as a concrete recovery hypothesis:
forecast income3:15→62:13 over8 turns, recorded continuation14:28; different
later actions/RNG prevent causal attribution. Next diagnostic: repeated selection
of the promised repair, with saved wins as controls. Late v1-020 self-closing
loss already has a forced boss win via region19; no recovered win is claimed.
Batching OFF, selected v09 and prior verdicts unchanged. These ten pairs are
development-exposed; refresh boss control before confirming a new strategy.
[Diagnosis and retained evidence](v09-batched-loss-diagnosis-v1.md).

Additional exploratory boss series 2026-10-16: **v09-batched-refresh-boss-v2**,
**INCOMPLETE_TRANSPORT / ORIGINAL_NOT_PASSED_UNCHANGED**. User requests another
40 games per arm with five-minute pauses after each twenty. Fresh control40
completes at **11W/29L**; candidate stops after **9W/19L over 28 games** when
request29 receives HTTP422/API407 (service play limit, no Retry-After/game ID).
Matched control prefix28 is **6W/22L**; candidate better6/worse3/equal19.
All68 retained canonical replays exact; all40 maps fresh against23,584 prior
identities. Actual three gaps300.015003/300.030197/300.030033seconds. No retries
or replacements; preserve uncertain intent29 and11 unstarted slots. Pooling
only completed matched pairs gives19/68 candidate versus17/68 control,
descriptive only. Twelve candidate slots remain unfinished; no full-series
pass or promotion. Exact builds/parameters/process panels unchanged; batching
stays OFF, v09 selected. [Retained result and evidence](v09-batched-refresh-boss-v2-result.md).

Completed boss continuation 2026-10-15: **v09-batched-refresh-boss-v1**,
**LOCAL_PASS_1000 / BOSS_NOT_PASSED / OVERALL_NOT_PASSED**. The unchanged
batched-refresh candidate scores **10W/30L/0D** against a fresh v09 control's
**11W/29L/0D**, exactly 40 matched maps/seeds/seats. The old October 4 cache
passes integrity checks but was used in outcome-guided diagnostics; refresh
40 control games before the fixed 40 candidate games. All maps fresh against
23,544 prior identities; all 80 canonical replays exact, zero technical failures
or replacements. Exact candidate/control source, classes and full parameters
match the completed local comparison; strict process evidence remains valid.
Local 56.65% does not rescue the failed boss stage. Keep batching OFF and v09
selected; no extension, promotion, arena publication or push. The prior 75-ms
native-call limitation remains. [Full result and evidence](v09-batched-refresh-boss-v1-result.md).

Completed optimization experiment 2026-10-15: **v09-batched-refresh-v1**,
**LOCAL_PASS_1000 / PROCESS_SAMPLE_PASS / BOSS_NOT_RUN**. Candidate
`current?NOISE=0.1,USE_BATCHED_REFRESH=true` versus `v09?NOISE=0.1`,
missed-deadline OFF: **535W402L63D/1000,56.65% points**,500 fresh paired maps,
zero errors. Interim pooled59.3333%/300 and57.1%/500; no early pass or tuning.
One allocation-free BFS per destination within each SearchState refresh;
strategy, canonical path ties and timers unchanged. All524 ample positions
match frozen/current-OFF exactly; identical work takes38.13% less wall time.
Nominal comparisons2143→3299; completed searches234→298.163 tests pass,
292 CG replays exact,90,129-character export; prior exports remain exact.
Strict process8/8 clean,678 later responses,max46.7275ms. Local strength has
one75ms native-call outlier; its turn/cause are unknown and the counter also
includes first turns. Preserve this limitation, no timing retry. All2,912
frozen inputs+7 runtimes unchanged. Batch flag stays OFF, v09 stays selected;
Boss games were pending at this local-only snapshot; the later continuation
above completes and rejects the combined gate. [Local protocol and evidence](v09-batched-refresh-v1.md).

Completed local strength experiment 2026-10-11: **v09-deferred-strength-v1**,
**STOP_FUTILITY_300 / LOCAL_NOT_PASSED_AND_PROCESS_FAILED**. Exact tested
`current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true` versus selected frozen v09:
134W142L24D,146/300 points (**48.6667%**),150 fresh paired maps, zero bot errors.
Two workers, CG486/36ms; all maps disjoint from22,894 previous identities.
Blocks2/3 pre-registered but skipped at the first futility stop, no retry/tuning.
This compares the whole candidate (including NOISE=0) with v09(NOISE=0.1),
not the causal effect of one flag. Engine292 exact, fresh98,572-character export
and classes match the prior candidate;2,589 frozen inputs+7 runtimes unchanged.
Old strict50ms failure remains; no new process/boss check, promotion or upload.
Selected v09 unchanged; no automatic path-batching follow-up.
[Protocol and result](v09-deferred-strength-v1.md).

Completed diagnostic 2026-10-11: **v09-runtime-cause-v1**,
**PARTIAL_ATTRIBUTION / FOLLOWUP_SELECTED / ORIGINAL_PROCESS_FAIL_UNCHANGED**.
Old56.0151ms includes child52ms; old99.5029/64.5907ms have zero supplemental
forecast work. Exact historical causes remain unknown. One unchanged-artifact
JFR panel completes57starts/147pairs,2,281 decisions; forced-input persistence
fails after two measured calls, and child JFR startup breaks the separate process
probe at t0. Both tool failures retained, no retry. Measured-only samples show
refresh40.26% and cheapestPath39.00%; four new overruns have no recorded GC pause.
Required native r33t40 still intervenes28.8426ms; six WIN controls exact, eight
native wins retained. Select allocation-free destination-batched core refresh as
the sole follow-up; not implemented or proven50ms. Production/default/v09 unchanged.
[Diagnosis and acceptance plan](v09-runtime-cause-v1.md).

Completed runtime optimization 2026-10-11: **v09-deferred-forecast-reuse-v1**,
**SEMANTIC_PARITY_PASSED / REQUIRED_MECHANISM_OBSERVED / PROCESS_FAILED**.
Supplemental forecasts reuse canonical paths only when construction is empty;
strategy, guards, full construction and14ms margin are unchanged. Previous ON
versus optimized ON:147 ample comparisons/1,140 decisions per arm match exactly,
including all old diagnostics;9,968 redundant board refreshes removed.31 focused
tests pass;292 baseline+40 cohort+1 historical replays exact. Export98,572 chars.
At nominal36ms, required r33 attack executes at40 instead of42 in32.8008ms,
with all guards complete and45 refreshes saved. Six closing controls stay exact;
all eight native wins survive. Strict process panel fails:7/8 clean, one56.0151ms
timeout at904148181/P0/t33. Keep experiment OFF and selected v09; no retries,
strength/boss trial, promotion or upload. [Protocol/evidence](v09-deferred-forecast-reuse-v1.md).

Completed packaging/runtime experiment 2026-10-11: **v09-deferred-commit-runtime-v1**,
same default-off candidate, **PACKAGING_PASSED / PROCESS_FAILED / REQUIRED_MECHANISM_NOT_OBSERVED**.
Lexical compaction reduces108,524 to98,401 characters (1,599 spare), preserving30,526
normalized Java tokens and all24 Java17 `-g:none` classes. Strategy and14ms margin
unchanged.83 focused tests pass;292 engine replays exact. Strict eight-game process
panel:7 clean, one51.0289ms timeout at904147739/P1/t54. Exact exported classes on
57 old starts/147 paired nominal36ms episodes: zero native interventions; required
region33 correction runs out during Wr0 at40 and still attacks42, losing the ample
benefit. Two passive-only interventions preserve guards/build; all six closing
controls remain exact and eight native wins preserved.927 frozen inputs and629
baseline copies verified. Timing-limited OFF/ON score differences are not strength
evidence. Keep flag OFF and selected v09; no retry, tuning, promotion or upload.
[Protocol/evidence](v09-deferred-commit-runtime-v1.md).

Completed local correction screen 2026-10-11: **v09-deferred-commit-v1**,
`current?NOISE=0,USE_ROLLOUT_MISSED_DEADLINE=true` (default OFF), **LOCAL_SCREEN_PASSED /
SOURCE_SIZE_BLOCKED**. Same full construction, immutable missed deadline,
same-first-target consensus, terminal protections and target-only Wr0 guard.
Same56 starts/88 native reply pairs: one+45 earned-score change,87 ties,zero
regressions. Region33 attacks at40 instead of42 after due39; lateness decreases,
no new terminal win. All eight OFF wins preserved, including six closing
controls. Passive56 pairs: two positive/54 tied; historical case unchanged.
967 OFF actions and all OFF trajectories exact;64 tests,292+40+1 exact replays,
1129 frozen inputs unchanged. Default export byte-identical; enabled source
108,524/100,000 characters, normal export rejected and process/timing unverified.
No strength or boss games, promotion or upload; retain v09.
[Protocol/evidence](v09-deferred-commit-v1.md).

Completed diagnostic experiment 2026-10-11: **v09-full-action-v1**, no registered
candidate or strength games. Same complete construction compared at967 states;
exact root/fill provenance,183 network roots.38/39 old reversals remain, but in
the39 subgroup39/52 nominated-target promises execute on time;13 miss deadlines
across nine starts/six games.56 cohort starts yield88 native reply pairs: bounded
earned-score A−W36 positive/19 negative/33 tied; no recovered terminal win. Four
starts change W=WIN to A=NONTERMINAL within eight turns.56 passive pairs and one
historical start remain separately labelled.32 tests,292+40+1 exact replays,
693 frozen files unchanged. Forecast/execution mismatch confirmed; blanket earlier
sabotage is not supported. Keep v09. [Protocol/evidence](v09-full-action-v1.md).

Completed diagnostic 2026-10-11: **v09-continuation-v1**, no new bot or strength
trial. Fixed full40-game v09 cohort,2,936states: five full-WAIT deferred states in
one episode, zero qualifying changes after removing future own sabotage. Ten
guaranteed winning cuts, three native shortlist omissions, but all original
actions already win against the recorded reply; zero recovered immediate wins.
Exploratory construction/no-sabotage subgroup39states/15games supports selecting
a construction-preserving full-action preflight, not forced attacks; raw forecasts
omit final fill and include delayed terminal wins forecast by the native model.17 tests,292+40+1
exact replay validations,665 frozen files unchanged. Keep v09; follow-up selected
but not implemented. [Protocol/evidence](v09-continuation-v1.md).

Completed local strength experiment 2026-10-11: **neural-target-strength-v1**,
`current?USE_NEURAL_TARGET_PROPOSAL=1`, **STOP_FUTILITY_300 / REJECTED**.
At the user's request, source packaging was deferred. One frozen neural target
is compared against the original target with the complete unchanged v09 build,
equal rollout context and deadline fallback. **129W154L17D/300 = 45.83%** versus
v09 on 150 fresh paired maps; CG search budgets, two workers, zero bot errors.
7,146 E/D comparisons complete, 754/24,871 turns change target (3.03%). Native
slow calls2/5 and maxima56/77ms are not strict process validation. 102 unique
focused tests pass after one fixture correction, 292 exact engine replays,
2,636 files +7 runtimes verified; default source remains byte-identical.
No further blocks, candidate artifact, boss games, upload or promotion; v09 stays.
[Protocol/evidence](neural-target-strength-v1.md). The previous feasibility verdict
below remains historical; this separate experiment explicitly waived its size stop.

Completed feasibility experiment 2026-10-11: **neural-target-feasibility-v1**,
**STOP_DIRECT_PORT_SIZE**. Isolated deterministic frozen-V3 target module matches
49,735 feature values bitwise on 51 states/1,715 candidates. Its direct addition
to fresh current requires **119,594/100,000 characters**, before E/D integration.
Warm local module median **1.30 ms**, p95 **6.20 ms**; fits remaining baseline
budget in **22/51** states (22/24 with complete baseline search). 85 focused tests,
292+8 exact replays, 576 frozen files +4 runtimes verified. No registered bot,
integrated adapter, candidate artifact or strength games; keep v09 and stop this
direct port. [Protocol/evidence](neural-target-feasibility-v1.md).

Completed diagnostic 2026-10-11: **neural-component-audit-v1**, no registered
candidate or strength bench. All 51 prior rows reproduced exactly, then four
construction/target combinations evaluated without refill. Versus the best
complete v09 action: target-only **7 better /24 equal /20 worse**;
construction-only **3 better /15 equal /33 worse**. Original eight positives:
five only-target, two only-construction, one either, none needing both to clear
the proxy threshold. 75 focused tests, 292+8 exact replays, 595 frozen files
and four runtime files unchanged. Prioritize target-only adapter feasibility;
size/time and strength remain unproved, v09 selected.
[Protocol/evidence](neural-component-audit-v1.md).

Completed diagnostic 2026-10-11: **neural-proposal-audit-v1**, no registered
candidate or strength bench. Recorded historical neural-v3 full actions on
51 live positions / eight games; 13 terminal omissions. Forty-eight new full
actions, including 47 new construction sets. Under the same retained route-0
continuation: **8 better / 12 tied / 31 worse** than the best complete v09
alternative. Positives span six games; seven also change the disruption target,
so construction alone is not established as the cause. 68 focused tests pass,
292+8 replays exact, 581 frozen files preserved. Keep v09; no NN integration or
strength claim. [Protocol/evidence](neural-proposal-audit-v1.md).

Completed 2026-10-11: **spatial-ppo-v10**, full-corpus continuation from original V4.
Same architecture, ordered imitation objective and greedy runtime; restore
original Adam/RNG, add 64 epochs / 18,240 steps, ending at epoch 80 / step 22,800.
TRAIN unforced 6,251→7,337/14,428 (43.325%→50.853%); exposed validation 1,471→1,361/3,720 (39.543%→36.586%); teacher-prefix
full plans 599→829/4,548 / 77→34/1,112, respectively. Versus original V4:
**66W/34L/0D / 100**; practical-v4 versus v09: **0W/300L/0D / 300, STOP_FUTILITY_300**.
Strict process 8/8 clean. Strength rejected; no promotion/upload, v09 selected,
original stage 3 open. [Protocol/evidence](spatial-ppo-v10.md).

Completed diagnostic 2026-10-10: **spatial-fit-probe-v1**, no registered candidate
or strength bench. Unchanged V4, 48 fixed TRAIN roots / 192 rows, exactly 1,024
epochs / 3,072 updates. Decisions 118→192/192; actual Java ordered/unordered full
plans and semantic successors 8→48/48. Unforced agreement on the TRAIN complement
43.264%→30.108%, exposed validation 39.543%→26.048%: narrow fitting, no strength
claim. 23 focused tests pass, 292 exact engine replays; selected v09 unchanged,
original stage 3 open. [Protocol/evidence](spatial-fit-probe-v1.md).

Completed diagnostic 2026-10-10: **spatial-label-audit-v1**, no new model or bench.
All 60 original teacher games / 5,660 roots / 22,640 rows reproduced exactly.
All 21,702 teacher construction permutations preserve the full next state, but
actual validation full-plan agreement only changes 77→80/1,112 (V4) and
63→65/1,112 (V9) when order is ignored. Exact-input conflicts force 37 label
errors out of 22,640; measured ordering/contradictions do not explain most of
the fitting gap. 7 Java / 9 Python tests pass, 292 exact engine replays.
Selected v09 unchanged; original stage 3 remains open.
[Protocol/evidence](spatial-label-audit-v1.md).

Completed 2026-10-10: **spatial-ppo-v9**, one global distance-weighted town-context
change with the exact v4 teacher recipe. Fixed 16 epochs/4,560 steps; validation
unforced agreement 38.414% versus v4 39.543%. The synthetic alias is broken,
but the progress comparison is **42W/58L/0D/100** versus v4. Practical-v4 versus
v09: **0W/300L/0D / 300**, **STOP_FUTILITY_300**, errors [0, 0].
Strict process 8/8 clean; strength rejected, v09 selected, original stage 3 open.
[Protocol/evidence](spatial-ppo-v9.md).

Completed diagnostic 2026-10-10: **spatial-learning-audit-v1**, returning to original
roadmap point3. No new bot/weights or promotion. Four frozen policies, four fresh
maps, both v01/v09 opponents and both seats: 64 complete games/6,280 turns, ample
budgets; independently replayed with every stage-zero value checked. V4: 8W/0L
vs v01, 0W/8L vs v09; matched gap improves over v2. V5 transfer is worse than
income extrapolation in all eight groups; v3's native critic improves while its
policy regresses. Exact delayed-route local alias demonstrated. Stage3 remains
open; one global town-context hypothesis proposed. 604 scoped Java/11 Python
tests,292 exact replays. [Protocol/evidence](spatial-learning-audit-v1.md).

Completed 2026-10-10: **spatial-ppo-v8**, frozen v4 weights/v7 proposals,
paired exact-score checkpoints at depths2/4/6/8 with deepest-complete-pair fallback.
Nominal saved panel: 19/34 usable comparisons, 3 changes.
Practical-v4 vs v09: **2W/298L/0D/300**, **STOP_FUTILITY_300**; errors [0, 0].
Strict process8/8 clean, 595 scoped tests,292 exact replays. Rejected for
strength; selected v09 unchanged. [Protocol/evidence](spatial-ppo-v8.md).

Completed 2026-10-10: **spatial-ppo-v7**, frozen v4 weights, alternative first
BUILD with a fresh conditional suffix, unchanged two-turn exact-score ranking.
Nominal saved panel: 30 complete proposals,
3 duplicates, 26/34 comparisons, 3 changes.
Practical-v4 vs v09: **1W/299L/0D/300**, **STOP_FUTILITY_300**; errors [0, 0].
Strict process 8/8 clean, 583 scoped Java tests, 292 exact replays.
Strength rejected; selected v09 unchanged. [Protocol/evidence](spatial-ppo-v7.md).

Completed 2026-10-10: **spatial-ppo-v6**, unchanged v4 weights, two same-cost
last-placement candidates and two exact turns, ranked by actual score delta.
Nominal saved panel: 30/34 comparisons complete,
5 decisions change. Independent reconstruction verifies all
60 ample trajectories. Practical-v4 vs v09: **1W/299L/0D/300**,
**STOP_FUTILITY_300**. Errors [0, 0]; strict process 8/8 clean.
569 scoped Java tests, 292 exact replays. Strength rejected; selected v09 unchanged.
[Protocol and evidence](spatial-ppo-v6.md).

Completed 2026-10-10: **spatial-ppo-v5**, one fixed value-head ridge fit
with exact v4 policy/shared weights, plus two-disruption one-ply core search.
Validation RMSE 8920 vs income 10783 but MAE 5919 vs 5465; v09-only evaluation remains
poor. Nominal saved-panel search completes 14/34 comparisons and changes 6 decisions.
Progress vs v4: **1W/1L/98D/100**. Practical-v4 vs v09:
**0W/300L/0D/300**, **STOP_FUTILITY_300**. Errors [0, 0]; strict process 8/8 clean.
556 scoped Java/38 Python tests, 292 exact replays. Rejected for promotion;
selected v09 unchanged. [Protocol and evidence](spatial-ppo-v5.md).

Completed 2026-10-10: **spatial-ppo-v4**, supervised hard-action distillation
from frozen v09, warm-start v2, unchanged architecture/runtime. 60 complete games,
22,640 conditional rows, 16 epochs, 4,560 Adam steps. Held-out unforced teacher
agreement 39.54%; exact full plans
6.92% (teacher-prefix diagnostics).
Final vs v2: **98W/2L/0D / 100**; practical-v4 vs v09: **0W/300L/0D / 300**,
`STOP_FUTILITY_300`. Zero game errors; strict process 8/8 clean, 292 engine replays
exact. Local bench slow calls 2/0 vs v2 and
2/0 vs v09. Improved over v2, rejected for strength;
boss skipped, selected v09 unchanged. Value head frozen during distillation;
resulting values uncalibrated.
[Protocol and evidence](spatial-ppo-v4.md).

Completed 2026-10-09: **spatial-ppo-v3**, fixed four-opponent pool from v2 weights,
unchanged model/runtime/PPO, 16,384 learner turns, 183 completed games and 1,024
optimizer steps. Final versus v2: **6W/94L/0D / 100**, regression on 50 fresh paired
maps. Practical-v4 versus v09: **0W/300L/0D**, 150 separate fresh paired maps,
`STOP_FUTILITY_300`; zero game errors. Final numerical parity and 32/32 nominal/ample
plans agree, strict process 8/8 clean, 292 engine replays exact. Candidate rejected,
boss skipped, selected v09 unchanged. No causal claim separating opponent-pool
effect from additional training. [Protocol and retained evidence](spatial-ppo-v3.md).

Completed 2026-10-09: **spatial-ppo-v2**, same compact model/runtime, fixed
8,192-turn self-play run: 92 completed games, eight unfinished, 1,024 optimizer
steps. Final versus exact initializer: **96W/4L/0D (96%)** on 50 fresh paired maps.
Practical-v4 versus v09: **0W/300L/0D**, 150 separate fresh paired maps,
`STOP_FUTILITY_300`, zero game errors. Late-panel numerical parity and 32/32
nominal/ample plans agree; strict process 8/8 clean; 292 engine replays exact.
Learning against the weak initializer is established; competitive strength is not.
Candidate rejected, boss skipped, selected v09 unchanged.
[Protocol, checks and retained evidence](spatial-ppo-v2.md).

Completed 2026-10-08: **spatial-ppo-v1**, first complete compact spatial CNN/PPO
pipeline inspired by Pduhard. Fixed smoke training: 256 environment turns,
512 seat samples, 32 optimizer steps, 3,439 weights; all 2,048 internal decisions
legal, but zero completed training games. Initial/final Java/Python parity 64/64;
510 scoped Java tests, 7 Python tests, 292 exact replays, strict process 8/8 clean.
The full suite still includes six failing tests from the pre-existing untracked
beam prototype. Local timing has occasional cooperative cutoffs/slow calls.
Practical-v4 vs v09, seed 9800000, two workers: **0W/300L/0D (0%)** on 150 fresh
paired maps, zero game errors, `STOP_FUTILITY_300`. Boss skipped. Technical
pipeline retained; candidate rejected for strength, selected v09 unchanged.
[Protocol, evidence and limits](spatial-ppo-v1.md).

Verified arena snapshot (2026-10-04, Europe/Bucharest): user-uploaded **neural-v3**,
agent6779375, Silver **134/712**, score22.4, placement100%, inProgress=false.
The fixed latest40 games give20W20L across22 opponent agents; all40 reproduce exactly,
carry the tested build/config markers, and show no technical failures or skipped own
actions. Losses often develop early:13/20 behind at turn20,18/19 surviving losses behind
at turn40. Three apparent self-closures require simultaneous opponent disruption;
none ends against opponent WAIT. These diagnostics establish neither a matched v09
comparison nor recoverable wins. The user reports apparent regression relative to v09.
[Arena evidence](evidence/neural-v3-arena-20261004.json). Local `current` and the
default exported artifact still identify v09.

Additional retrospective timing clue on those same40 games: on11 maps of height18-20,
V3 spends411/660 nominal paint in turns0-19 (62.3%) versus656/660 (99.4%) for opponents,
and wins2/11. On29 shorter maps it spends1714/1740 (98.5%). All40 first moves use3paint;
the deficit begins after the first-turn allowance. Own stderr has84 warnings at37-51ms,
79 on turns spending<3paint; no official timeout is reported. Cooperative interruption
is a credible hypothesis, not yet established as the cause of each loss. Aggregate
whole-game paint totals conceal this opening deficit. [Evidence and limitations](evidence/neural-v3-arena-paint-20261004.json).

The single fixed local follow-up tested all800 saved states with nominal486/36ms
and ample60000ms: all800 action pairs are identical, legal against WAIT and spend3paint;
no aborts, maximum nominal call31.5294ms. CG underpainting (161 of these states)
was not reproduced locally. Preparation accounts for80.65% of measured nominal time
on tall maps (mean10.87ms of13.48ms). This demonstrates available complete construction,
not recovered wins or proven CG timing causality. Next priority is exact feature
preparation optimization with frozen V3 weights and explicit CG timing observation.
The coupled diagnostic RNG differs from CG, so availability of a continuation after
the actual CG-selected prefix is not established. Preparation timing combines full
and incremental calls; repeated hypothetical connection recomputation needs profiling.
[Saved-state diagnostic](evidence/neural-v3-budget-probe-20261004.json).

October 3 diagnostic review (no new bot/version or strength games): Q-v1/v2's
0/300 outcomes remain rejected. The final Q-v2 training block's 128 games / 12,446
turns show no shaping delta beyond earned score increments; terminal-step shaping
exceeds the +/-1 outcome in 25% of games. See the
[review and proposed next experiments](indrill-q-v2.md#october-3-follow-up-review-what-the-failed-runs-actually-establish)
and [reward audit](evidence/indrill-reward-audit-v1.json).

Selected on 2026-09-20: **v09 Never Idle**, the exact fill-v1 candidate
(`current?USE_ROLLOUT_FILL=1`): v08 plus leftover paint spent on stable/protected cells next to
own rails; RHEA code removed from the shipped source. Practical-v4 vs v08: **569/1000 (56.90%)**
PASS_1000; boss: **17/40 vs cached v08 13/40**, PASS. All 117 effective parameters are frozen.
Default artifact 95,278 characters, strict 8-game process panel **0/8** problem games.
Promotion record: [v09-promotion.md](v09-promotion.md).

Previously selected on 2026-09-19: **v08 Protected Opening**, the exact approved opening candidate.
Direct vs v07: **62% / 300**; boss: **13/40 vs cached v07 10/40**, practical-v3 PASS.
All 107 effective parameters are frozen. Promotion and exact-artifact readiness:
[v08-promotion.md](v08-promotion.md).

Previously selected on 2026-09-18: **v07 Network After Base**, the exact network-after-base-v1 candidate.
Direct v06 screen: **62.50% / 300**. Matched fresh v05 confirmation: **64.90% vs 56.90%**
for v06 / 1000 each, **+8.00 points**. Full 98-field snapshot; current defaults enable QuickIncome
and after-base network search. Packaging and main Player identity: [v07-promotion.md](v07-promotion.md).
Earlier selection/default statements below describe historical checkpoints.

Latest user-reported arena snapshot (2026-09-18), immediately BEFORE the intended v07 upload:
**QuickIncome deployed, rank 111 / 627**. This supersedes the earlier v06 arena snapshot below.
At that checkpoint v07 was selected locally; an actual v07 upload had not been reported or performed.
Exact deployed QuickIncome source hash and placement status are not independently verified.

Research proposal, 2026-09-17: **local network control**, initially single-cell ownership
rerouting on already active connections. A known-board audit of the existing 133 boss states
finds 45 states where a cell outside every retained QuickIncome route improves immediate net
income more than every ordinary construction root. This excludes simultaneous actions and
future value; it is action-space evidence, not a strength result or a new bot version.
Proposal, limits and full evidence: [network-control-strategy.md](network-control-strategy.md).
Completed follow-up: network-single-cell-v1 scores 48.3333% vs v06 (300),
50.6667% vs QuickIncome (separate 300); NOT PASSED, flag OFF.
QuickIncome remains the development base; no default, submission or frozen-policy change.
Protocol and final evidence: [network-single-cell-v1.md](network-single-cell-v1.md).

v06 was selected on 2026-09-16 at the user's request after fixed 2x1000 confirmation:
**1230-770 (61.5%) vs v05**, map-grouped 95% CI **57.21-65.79%**. Both individual gates pass;
older-opponent checks are **68.5% vs v03** and **74.5% vs pressure-v1**, 200 games each.
The frozen policy and all parameters match that evidence. Earlier sections below retain their
historical decisions; the current selection supersedes their prior default-v05 status.

Packaging checks at v06 promotion: **217 tests pass**, **264/264 arena replays exact**. The promotion-time default Player is
56,007 characters, source `7f4d94e40e5252d09b86df6e1b8c208e92c86d98a521d858eaf182a9178c2c9c`.
Its fixed strict process check **FAILED 1/42 games**: map 903510176, P0 turn 73, response
**50.462 ms** with a 50-ms deadline (send 0.164 ms). This supersedes a readiness claim based
only on the earlier candidate's 0/42 pass. v06 is selected locally; timing readiness is open,
no arena upload by the agent. Report and executable archive: `build/analysis/v06-promotion/`.

Readable labels and six runnable retained experiments are listed in [bot_catalog.md](bot_catalog.md)
and `gradlew botCatalog`. Version ids remain stable; `EvalBotVNN` class names remain frozen.
Named experiments are local, not promoted, and verify a pinned engine/configuration snapshot.
Their historical screen results are not new evidence for the shared catalog snapshot.

Latest user-reported arena snapshot (2026-09-16): **v06 uploaded, 153/645**, after **rank 270
for v05** before upload. Exact deployed source hash is not independently verified. The 30 saved
Boss 4 games split by the user's corrected first-16/last-14 assignment give **v05 2-14**, **v06 5-9**,
with the first v06 game **904146009**,
on 30 distinct unpaired maps; all validate exactly, with no failures or skipped commands.
See [the loss diagnosis](boss-v06-loss-analysis.md) for route-portfolio evidence and the next
single-change proposal. No strategy change or new strength confirmation in this analysis.

Previous user-reported arena snapshot (2026-09-15): **Gold opened; still Silver, 235/622**.
User confirms the arena still runs the previous build, without the new isolated warmup;
the exact deployed source hash and placement status remain unverified. League changes
alter the ranking population, so this is not directly comparable to the old rank snapshots.
The warmup fix has process-timing evidence, not a new strength-benchmark result: the ordinary
in-process benchmark does not invoke MainBot's submission warmup.

Fresh diagnostic: arena entry **6739024**, 50 newly downloaded games, **25 wins / 25 losses**,
zero own failures, one win by opponent timeout; **264/264** corpus replays exact.
This is a separate entry/sample from 6736850, not a controlled strength comparison.
See `arena-analysis-2026-09-15.md` for scope, loss evidence and the next hypothesis.

Sabotage follow-up (`cgDisrupt`): known-state full scans reproduce Meruem's recorded target
preferences; local timed scans finish at all four selected positions. OldJohn's missed sabotage
is not reproduced, leaving its original cause open. Diagnostics only: v05 and Player.java unchanged,
no new strength bench or promotion. See `sabotage-valuation.md`.

Delayed-sabotage experiment (2026-09-15): `DISRUPT_DELAY_FACTOR=0.65` scores **49.3%** vs v05,
146-150-4 over 300 games / 150 new paired maps, map-grouped CI **38.2-60.4%**. Gate not passed.
On 200 matched games each vs aggressive v02, candidate **73.3%** (146-53-1), v05 **76.5%**
(153-47-0); no common-opponent support for promotion. Keep **factor 1.0**, no v06.
116 tests / 264 exact replays; candidate and restored frozen-v05 process samples each 0/4 failures.
Full protocol, source/artifact hashes and reports are in DEVLOG and `sabotage-valuation.md`.

Sections from 2026-09-13 to 2026-10-06 (v01–v09 experiments, neural, indrill, the reference panel, frozen-version
notes) are in [archive/BOTS-2026-09-13_10-06.md](archive/BOTS-2026-09-13_10-06.md).
