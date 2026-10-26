# boss-v3 — refitting the boss model on the live boss (2026-10-26)

The user's "Давай" to the proposal at the end of `proxy-agreement-v1`: can the boss-v2 proxy's target chooser be refitted
so that it agrees more with the live boss on the games where it disagreed most (K1's corridor games), and does a proxy
that agrees more flip `corridor-routes-v1`'s sign? Three things were tried, in order: a refit of the target logit with
new features, the hold margin, and the sign check. Evidence in `docs/evidence/boss-v3/`.

## 1. Refit of the target logit with a game-level holdout — no gain

`tools/boss_proxy/fit_boss_target_v3.py` over the whole canonical boss corpus (`gradlew cgLeaderStudy` + `bossIntentStudy`,
1,180 games, 73,133 boss turns with a DISRUPT, 26,676 commitment turns = target switches, the fit set of boss-v1), with
the 80 games of the K1 live session held out by game (K1 arm 901 commitment turns, v12 arm 858). Three feature sets:
the 13 shipped ones (`base`), plus indicators instability == 2 / == 3 (`ink`, the time-to-ink shape), plus the opponent's
non-earning tracks and oppCells × instability (`mix`). L2 1e-3, 3,000 gradient steps on standardised features.

| weights | train top-1 (24,917 commitment turns) | holdout K1 / v12, commitment turns | holdout K1 / v12, every disrupt turn (argmax, no hold rule) |
|---|---|---|---|
| **shipped W (boss-v1 fit on 855 games)** | 0.529 | **0.562 / 0.515** | **0.719 / 0.686** |
| `base` refit | 0.525 | 0.556 / 0.509 | 0.718 / 0.688 |
| `ink` refit | 0.524 | 0.554 / 0.507 | 0.719 / 0.690 |
| `mix` refit | 0.517 | 0.534 / 0.498 | 0.679 / 0.653 |

The refitted `base` weights land within a few hundredths of the shipped ones (oppCells 0.763 vs 0.775, instability
0.893 vs 0.885, dOpp 0.130 vs 0.137); `ink` gives instability-3 a *negative* indicator (−0.43: at a commitment turn the
boss does not open a one-hit region — the explicit closure rule covers those); `mix` puts +0.39 on the non-earning
tracks and loses on every holdout. **On this feature family the shipped weights are the optimum; the boss's attacks on
under-construction concentrations are not a weight the logit is missing.** The scaffolding for a `USE_BOSS_V3` weight
set was built and then reverted (it changes `BossSabotage.java`, which the V10–V12 promotion tests pin as a frozen
helper; nothing to ship).

Two readings from the same table: at the *commitment* level the model predicts the K1 games *better* than the v12
games (0.56 vs 0.52), and the per-turn argmax without a hold rule reaches 0.72 / 0.69 — far above the 54 / 58 % the
real proxy scored in `proxy-agreement-v1`. The gap to the real proxy is in the chooser's state, not in the weights:
the hold rule, the candidate filter and the recent-placement counter it keeps from its own observations.

## 2. The hold margin — a uniform +4.5 pp, not the K1 blind spot

`BossTargetStudy` with the proxy at `BOSS_HOLD_MARGIN` 0 / 0.5 / 1.0 (shipped) / 2 on the same 80 games
(`margin-summary.txt`):

| margin | agreement, K1 games | v12 games | K1 games at ≥ 10 of our tracks in a region | on the boss's under-construction attacks, K1 / v12 | turns 11–45, K1 / v12 |
|---|---|---|---|---|---|
| **0** | **58.8 %** | **62.6 %** | 26 % | 49.8 / 65.2 % | 65.8 / 62.6 % |
| 0.5 | 56.4 % | 60.1 % | 26 % | 48.2 / 63.5 % | 63.7 / 59.5 % |
| 1.0 (shipped) | 54.1 % | 58.1 % | 29 % | 46.8 / 61.8 % | 61.3 / 57.7 % |
| 2 | 49.7 % | 52.7 % | 30 % | 44.4 / 56.1 % | 56.4 / 53.1 % |

A margin of 0 (re-evaluate every turn, no stickiness) agrees 4.5 pp more with the live boss in both arms — the boss
switches targets more readily than boss-v1's hold calibration assumed — but the K1-specific deficit is untouched: the
under-construction attacks stay at 50 % vs 65 %, the ≥ 10-track regions at 26 %. The margin is a small, uniform
improvement of the proxy, not the blind spot.

## 3. The sign check — K1 against the margin-0 proxy (`tools/boss_proxy/run_margin_screen.sh`, pool 1010000)

Fresh pool 1010000, 150 maps × 2 seats, CG timing, `current` (= v12) as the same-map control, proxy spec with
`BOSS_HOLD_MARGIN=0` (`docs/evidence/boss-v3/margin-screen/`):

| cell | rate | W-L-D | paired delta vs `current` |
|---|---|---|---|
| `cur-vs-boss2m0` | 72.3 % | 217-83-0 | — |
| `K1-vs-boss2m0` | 74.7 % | 224-76-0 | **+2.3 ± 7.5 pp** (maps better / worse / equal 44 / 39 / 67) |

K1 still does not lose to the better-agreeing proxy (+2.3 pp against +6.5 pp vs the margin-1 proxy on pool 1009000, both
inside the noise of each other; the live boss had it at −15 pp). **The sign does not flip.**

## Verdict

`boss-v3` is closed with no change to the proxy: the target logit cannot be improved on its feature family (the shipped
weights are the corpus optimum, the holdout confirms it), the hold margin is a uniform +4.5 pp of agreement that neither
reaches the K1 blind spot (the boss's attacks on under-construction concentrations: 50 % vs 65 % predicted) nor flips
K1's sign, and a proxy change would need its own fingerprint and live validation (`docs/boss-proxy-v1.md`) for a gain
that does not address the problem. What the boss does on an opponent's concentrated, not-yet-earning construction is
not a function of the 13 region features the model sees; finding it means new features (the opponent's partial routes
and where they are heading, a non-linear model) — research, not a screen fix.

Consequences for the policy (`docs/benchmarking-v5.md`, no amendment made):

- The proxy screen remains the policy's step (a); for **construction changes that concentrate tracks before they earn**
  the proxy's sign is not trusted (this is its documented blind spot), and the live step decides. A construction
  change that *extends active paths* (tracks that earn at once, regions the boss already values by income) sits inside
  the proxy's competence.
- `BOSS_HOLD_MARGIN=0` is noted as a better-agreeing variant of the proxy (58.8 / 62.6 % vs 54.1 / 58.1 %) for a future
  proxy revision, not adopted.

The next construction lever from `docs/arena-losses-v12.md` is the other trait of the field's top half — **two turns
from placement to first income against our four** — and it lives in the proxy's competent zone: a proposal valuation
that prefers routes earning within a turn or two (extensions of active paths, one-turn completions) over long
hypotheses. That is the recommended next candidate (user decision).
