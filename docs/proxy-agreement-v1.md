# proxy-agreement-v1 — where the boss's disrupt goes, and where the local boss models send it (2026-10-26)

Follow-up to `corridor-routes-v1` (user request after the live loss): K1 gained +6.5 pp against the boss-v2 proxy and
+7.7 pp against neural-v6 and lost 15 pp against the live boss. The question: what does the live boss target that the
local models do not? Tool: `btk.tools.BossTargetStudy` (`gradlew bossTargetStudy`), one row per boss turn with a
DISRUPT over the two live capture directories of the K1 session (K1's 40 games, v12's 40, same maps and seats vs boss
6767608, nickname `Boss 4`): the boss's region and the region a local model playing the boss's seat from the same
public state would disrupt, each with the opponent's (our) track count, our income from the region (tracks counted
per active path they lie on), the instability and the region's rank among the disruptable regions by our track
count. Models: the policy's proxy (`current?USE_BOSS_SABOTAGE=1,…,USE_EXPOSURE_INCOME=0,NOISE=0`), `neural-v6`,
and `v12?NOISE=0` (our own chooser in the boss's seat). Outputs in `docs/evidence/proxy-agreement-v1/`
(`summary.txt`, six CSVs, `target_summary.py`). Turn 0 is excluded (every region ties at zero tracks).

## The boss's own targeting (model-independent; 2,505 disrupts in the K1 games, 2,375 in the v12 games)

| | K1 games | v12 games |
|---|---|---|
| target = the disruptable region with the most of our tracks (top-3) | 51.2 % (80.5 %) | 50.5 % (81.0 %) |
| …by phase, top-1: turns 1–10 / 11–20 / 21–45 / 46+ | 67.6 / 42.9 / 42.0 / 59.4 % | 64.0 / 38.2 / 43.2 / 60.9 % |
| our tracks in the target (max on the board) | 4.32 (5.32) | 4.14 (5.18) |
| target holds income of ours: all / turns 11–45 / turns 46+ | 52.5 / 77 / 14 % | 55.6 / 73–82 / 21 % |
| **target = an under-construction concentration of ours (≥ 3 tracks, no income yet)** | **39.6 %** (5.8 tracks, instability 1.44) | **36.0 %** (5.6 tracks, 1.39) |
| target with none of our tracks | 0.4 % | 0.3 % |

The boss disrupts where our tracks are, not only where our income is: in the opening and the late game it goes to the
region holding the most of our tracks two turns in three; four disrupts in ten go to regions where we have three or
more tracks and no income yet — routes under construction, extensions, fill. Late (turn 46+) only one target in five
holds any income of ours.

## Agreement of the local models with the boss's region

| model in the boss's seat | K1 games | v12 games | validation level (`docs/boss-proxy-v1.md`) |
|---|---|---|---|
| boss-v2 proxy (the policy's screening opponent) | **54.1 %** | **58.1 %** | 52 % (boss-v1, corpus) |
| v12 (our chooser) | 54.5 % | 59.4 % | — |
| neural-v6 | 41.3 % | 43.9 % | — |

| boss-v2 proxy, by the concentration of our tracks (max in one region) | ≤ 5 | 6–9 | ≥ 10 |
|---|---|---|---|
| K1 games | 61 % (n = 1,511) | 45 % (925) | **29 %** (69) |
| v12 games | 61 % (1,477) | 54 % (865) | 47 % (32) |

| the proxy on the boss's attacks on our under-construction concentrations (≥ 3 tracks, no income) | K1 games | v12 games |
|---|---|---|
| agreement | **46.8 %** (993 turns) | **61.8 %** (854) |
| …turns 11–45 only | 72.7 % (231) | 70.4 % (247) |
| the proxy's region instead: income / tracks / instability | 0.75 / 3.3 / 2.26 | 1.73 / 3.6 / 1.61 |

Where the proxy and the boss differ in the middle game (turns 11–45), the pattern is the same in both arms: **the proxy
takes the bigger, fresher income region** (our income 9.3–10.6 per turn, instability 0.5–0.7), **the boss the smaller
one nearer the ink** (income 5.9–7.0, instability 1.3; the boss's region is the more unstable one in 52–62 % of the
disagreements, the proxy's in 7–22 %). In the late game it reverses: the boss opens fresh regions holding our
non-earning tracks (instability 1.35–1.45, our income there ≈ 0.5), the proxy keeps finishing inks (2.15 / 1.70).

## Reading

1. The proxy is at its validated level (54–58 % vs 52 % on the corpus); neural-v6 is well below it (41–44 %) and is
   not a model of the boss's targeting at all, whatever its playing strength. Both local models agree 4 pp less with
   the boss in the K1 games than in the v12 games on the same maps.
2. The boss's two systematic habits the proxy lacks: it **finishes the ink it is nearer to** rather than switching to the
   largest income target, and it **attacks our under-construction concentrations** (three or more tracks, no income
   yet) — 40 % of its disrupts in the K1 games, predicted by the proxy at 47 % there against 62 % in the v12 games,
   and at 29 % when a region holds ten or more of our tracks. That is exactly what corridor routes create: several
   pairs' routes converging on one region before any of them earns. The proxy, scoring regions by our income, does not
   punish it; the boss does — the mechanism of `corridor-routes-v1`'s wrong sign (+6.5 pp vs the proxy, −15 pp live).
3. Our own chooser (v12 in the boss's seat) has the same blind spot as the proxy (it is the same family of valuation),
   so the bot's threat model under-weights the risk to its own construction concentrations as well.

## What follows (user decision)

- **A boss-model candidate `boss-v3`:** the chooser's region score adds the opponent's non-earning tracks (construction
  under way) and a time-to-ink term (instability), fitted by agreement on the 1,104-game boss corpus with these 80
  games as the holdout, measured exactly as `boss-proxy-v1` was (`BossAgreementStudy`, target agreement). A proxy that
  reaches 62 %+ on the K1 games and punishes K1 in a re-screen (the sign must flip) becomes the policy's screening
  opponent (a `docs/benchmarking-v5.md` amendment, user decision). The bot side follows for free: the same chooser is
  `USE_BOSS_LITE`'s threat model.
- Until then, a construction candidate that concentrates tracks before they earn must not be trusted on the proxy
  screen alone; the live step decides.
