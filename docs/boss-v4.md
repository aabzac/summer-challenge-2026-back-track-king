# boss-v4 — the proxy's blind spot is its closure shortcut, not its weights (2026-10-26)

Follow-up of `boss-v3` (refit, no gain) and `opp-hold-v1` (hold margin, no gain), the default next step after the user's
"давай дальше": what predicts the live boss's attacks on our under-construction concentrations — 36–40 % of its disrupts
in `proxy-agreement-v1`, where the boss-v2 proxy agreed 46.8 % (K1 games) vs 61.8 % (v12 games)? Evidence in
`docs/evidence/boss-v4/`.

## 1. The weights are not the problem (`tools/boss_proxy/fit_boss_target_v4.py`)

The target logit on `CgLeaderStudy`'s full feature table (25 columns: the 13 shipped plus the boss's own active tracks,
our last-turn placements, our own-only completion routes through the region and their cost — "a route we are about to
finish" —, the boss's own routes, the cheapest shared route's cost, the region we hit last turn, region size, instability
2 / 3 indicators, and interactions with our non-earning tracks; `oppChosen` excluded as a leak, `hitPrevByMe` as a
switch-turn selection artifact), fitted on the 24,917 commitment turns of the corpus with the 80 K1-session games held
out:

| weights | holdout K1: commitment / every disrupt / **under-construction attacks** | holdout v12: commitment / every / under-construction |
|---|---|---|
| shipped (boss-v1) | 0.562 / 0.719 / **0.822** | 0.515 / 0.686 / **0.819** |
| refit, 13 features | 0.556 / 0.718 / 0.822 | 0.509 / 0.688 / 0.820 |
| refit, 25 features | 0.564 / 0.716 / 0.795 | 0.513 / 0.687 / 0.793 |
| refit, 25 + interactions | 0.563 / 0.716 / 0.797 | 0.507 / 0.684 / 0.792 |

**The shipped weights predict the boss's attacks on our under-construction regions at 82 % in both arms** — no blind
spot in the model; the new features add nothing (our near-complete routes get +0.49 in the interaction fit, our
last-turn placements +0.47, the boss's own active tracks +0.51, but the holdout does not move). The gap to the proxy's
47 % on the same 993 turns is in the proxy's use of the model.

## 2. The proxy's closure shortcut (`btk.tools.BossChooserProbe`)

A tools-side copy of `BossSabotage.chooseInternal` replayed over both capture directories, dumping every candidate's
features exactly as the proxy computes them, joined with the study table on (game, turn, region) (`probe-join.txt`):

- Features agree with the study on every column except the route traffic (25 % of rows differ: the proxy's tactical
  path avoids instability ≥ 2) and the recent-placement window (6 %: three turns vs two); swapping either in changes
  the argmax agreement by ≤ 1 pp.
- **The probe's argmax without the closure rule: 81.3 % (K1) / 81.1 % (v12) on the under-construction attacks** —
  the fitted model's level. The difference is the rule that returns the first *winning* one-hit closure (a region at
  instability 3 whose ink leaves no desired pair possible, the boss ahead after it) at once.
- A winning closure is available on **38.4 % of the under-construction-attack turns in the K1 games vs 17.2 % in the
  v12 games** (15.9 % vs 6.8 % of all disrupt turns); the proxy takes it every time; **the live boss takes it 2.0 % /
  6.2 % of the time** — and goes on to win every one of those 35 games anyway. The boss keeps disrupting our
  construction in a won position instead of ending the game.

**Correction to `proxy-agreement-v1`.** Excluding the turns with a winning closure available, the proxy agrees with
the boss equally in both arms:

| proxy (margin 1.0), BossTargetStudy rows | K1 games | v12 games |
|---|---|---|
| all disrupt turns (as reported) | 54.1 % | 58.1 % |
| **no winning closure available** | **64.0 %** (2,106) | **62.0 %** (2,213) |
| …under-construction attacks | **75.8 %** (612) | **74.7 %** (707) |
| …regions with ≥ 10 of our tracks | 83 % (24) | 83 % (18) |
| winning closure available | 1.8 % (399) | 5.6 % (161) |

The K1-specific "blind spot" was the K1 games' long won tails for the boss (K1 lost more, and the corridors created
one-region chokepoints), not a targeting habit the model lacks. `proxy-agreement-v1`'s reading — "the boss attacks our
under-construction concentrations, the income-scoring proxy does not" — is withdrawn; its numbers stand.

## 3. Why the closure shortcut could produce the wrong sign

In a real game the shortcut ends the game when the proxy's one-line check says it is ahead (its score plus its income
after the ink against ours). Construction changes that create one-region chokepoints (corridor routes) give the proxy
more of these exits; if the check misjudges the end (it ignores the opponent's placements and income on the closing
turn), the proxy throws games the boss would have won by continuing — a gain for the candidate against the proxy that
does not exist against the boss. That is testable without the live boss.

## 4. Validation of a no-close proxy (`tools/boss_proxy/run_noclose_screen.sh`, pool 1014000)

`USE_BOSS_NO_CLOSE` (`// BOSS_NOCLOSE` block, current-only, needs `USE_BOSS_SABOTAGE`, default off; default and proxy
artifacts byte-identical, `BossNoCloseTest`, `BossNoCloseSubmissionTest`): `BossSabotage` skips one-hit closures, as the
live boss does 94–98 % of the time. The V10–V12 promotion tests now hash the current `BossSabotage` with default-off
experiment blocks stripped (the default export) against the freeze; the frozen copies are unchanged.

Predeclared reading (committed before the first game): the no-close proxy is validated for construction changes if, on
the same maps/seeds/seats with `current` (= v12) as the control, **K1's delta ≤ 0** (live −15 pp), **Q's delta ≤ 0**
(live −5 pp), and **the v11 configuration's delta ≤ 0** (`current?USE_EXPOSURE_INCOME=0`; live, v12 beat it by +5 pp).
K1 against the policy proxy runs on the same pool as the same-map comparison.

| cell (300 paired games, pool 1014000) | rate | paired delta vs `current` on the same opponent | maps better / worse / equal | predeclared |
|---|---|---|---|---|
| `cur-vs-nc` (no-close proxy) | 80.7 % | — | — | |
| `K1-vs-nc` | 83.3 % | **+2.7 ± 5.7 pp** | 36 / 28 / 86 | ≤ 0 — **fails** |
| `Q-vs-nc` | 83.7 % | **+3.0 ± 6.0 pp** | 39 / 29 / 82 | ≤ 0 — **fails** |
| `V11-vs-nc` (v11 configuration) | 77.0 % | −3.7 ± 7.1 pp | 40 / 41 / 69 | ≤ 0 — passes |
| `cur-vs-boss2` (policy proxy) | 80.3 % | — | — | |
| `K1-vs-boss2` | 78.7 % | **−1.7 ± 6.1 pp** | 27 / 30 / 93 | (replication) |
| `Q-vs-boss2` (follow-up cell) | 74.3 % | **−6.0 ± 6.7 pp** | 31 / 48 / 71 | (replication) |

0 errors in 2,100 games.

**The no-close proxy is not validated** (two of the three predeclared signs fail), and in real games the closure
shortcut barely matters: `current` scores 80.7 % against the no-close proxy and 80.3 % against the policy proxy on the
same maps. The closure rule explains the *replay* agreement gap of §2 — turns that occur in the boss's won tails — not a
screen error. `USE_BOSS_NO_CLOSE` stays in the code, default off.

## 5. The finding that matters: neither construction pass replicates on a fresh pool

The same pool replicated both construction candidates against the **policy** proxy, on maps neither was selected on:

| candidate | proxy screen (its own pool) | **replication, pool 1014000** | live boss |
|---|---|---|---|
| K1 `corridor-routes-v1` | +6.5 ± 7.2 pp (pool 1009000, best of 2 arms) | **−1.7 ± 6.1 pp** | −15.0 pp |
| Q `earning-delay-v1` | +6.3 ± 5.6 pp (pool 1011000, best of 2 arms) | **−6.0 ± 6.7 pp** | −5.0 pp |

On fresh maps the proxy agrees with the live sign for both. **There was no construction blind spot in the proxy**: the
two passes were selection noise. A 300-game step (a) has a ±6–7 pp interval per arm; picking the better of two arms and
gating at +3 pp passes a null candidate roughly one screen in three, and the live session (40 paired maps, ±15 pp) then
rejects it. Withdrawn with this: "the construction line is closed at the proxy's level" (`earning-delay-v1`,
`corridor-routes-v1`), "the proxy rewards construction changes the live boss does not" (`boss-v3`), and
`proxy-agreement-v1`'s blind-spot reading (§2 above). The numbers of those entries stand; their readings do not.

**Proposed amendment to policy v5 (`docs/benchmarking-v5.md`, user decision):** an arm that passes step (a) is replicated
on a fresh pool against the same proxy with `current` as the same-map control **before** step (b) and the live step;
it continues only if the replication's paired delta is positive and the pooled delta of both pools is ≥ +3 pp. Cost: one
300-game cell pair (~10 min) per passing arm; it would have saved 160 API games today. Retrospectively, the promoted
steps should pass it: the v11 configuration on `current` scored −3.7 pp here (v12's Exposure Income gain replicated in
sign), B (`USE_BOSS_ROOT_TARGET`) was not re-run.
