# How now-Gold players beat Boss 4, compared with our boss games (2026-09-19)

Descriptive replay study. No strategy default, frozen version or submission changed by the study
itself; the two experiments it motivated are recorded separately in [live-target-v1.md](live-target-v1.md).

## What promotion actually requires

Public API snapshots taken on 2026-09-19 (`build/cg-snapshots/`, gitignored; the aggregates are in
[evidence/boss-winners-study-v1.json](evidence/boss-winners-study-v1.json)):

- Boss 4 (agent 6740207) carries rating **29.69** in its `gameResult.agents[]` entries; the top of
  Silver is 29.08 and our v08 entry is at **24.91** (rank 316 global, 42nd in Silver). Promotion
  needs a rating above the boss, not a rank.
- The boss's last 500 arena battles: it wins **49.2%** overall. Grouping its opponents by their
  current league: players now in **Gold** beat it **55.7%** of the time over their whole placement
  (368 games, 205 wins), and the ones that promoted quickly beat it 70-95% (aropan 14/15,
  abdelmathin 19/20, Sahcim 19/24, AndreasHuber 14/18). The current top of Silver (matsu7874,
  29.08) beats it 7/15 and stays in Silver.
- v08 scored 13/40 (32.5%) on the cached boss pool; v07 10/40. The required change is roughly a
  doubling of the boss win rate, not a few points.

## Corpora

- **winners:** `replays/cg-boss-winners/`, 368 canonical `gameResult` files, every completed boss
  battle against a player whose current league index is 4 (Gold), both their wins and losses.
  Fetched with `python tools/diagnostics/boss_winners_study.py fetch`.
- **ours:** `replays/cg/boss/**`, the 179 canonical boss games of v05-v08 (48 wins, 131 losses).

All numbers below come from viewer events; the per-turn income reconstructed from active paths
matched the recorded scoring events on all 35,501 turns. Both corpora face the same boss build.

## Where the games diverge

| measure (turns 11-20 / 21-30) | Gold beats boss | boss beats Gold | we beat boss | boss beats us |
|---|---|---|---|---|
| boss income per turn | 45.0 / 54.5 | 60.9 / 83.8 | 63.5 / 76.0 | **76.9 / 97.6** |
| boss income from own-majority paths | 29.9 / 32.1 | 47.8 / 64.9 | 44.6 / 48.9 | **64.6 / 80.5** |
| boss own-majority active connections | 2.94 / 2.83 | 4.53 / 5.22 | 3.93 / 4.05 | **5.62 / 6.03** |
| boss project completion (MESSAGE `build a>b`) | 58.0% | 64.7% | 65.2% | **70.0%** |
| boss projects abandoned after an ink | 15.2% | 10.5% | 12.1% | **7.6%** |
| game length (median) | 52 | 57 | 53 | **100** |

Against us the boss keeps about one more active connection than against Gold players in every
phase and outcome, worth roughly 15 income per turn; its stolen income (cells on our paths) is the
same in both corpora. The gap opens between turns 6 and 20: in our losses the score difference is
already -295 at turn 20 (Gold losses: -147).

What is **not** different, to a first approximation: sabotage rate in the midgame (Gold 99-100%,
us 90-93%), the class of regions hit (about 30% opponent construction / 65% opponent active paths
for both), the instability at the moment of a hit, tracks destroyed by turn 20/30/45 (about 12-14
and 22-25 boss tracks for both), path length and regions per connection, and the share of
protected (town-region) cells. Pre-emptive hits on empty regions are rare for everyone
(1-2% of hits), so "disrupt empty corridors" is not what distinguishes the winners.

## The one sabotage difference that is large

Hits are classified by whether the target region received an opponent placement within the last
two turns, i.e. whether it is the opponent's *live* project:

| hits in a region with an opponent placement in the last 2 turns | all hits | construction-class hits |
|---|---|---|
| Gold players, wins | **31.7%** | **42.6%** |
| Gold players, losses | 25.3% | 36.6% |
| our bots, wins | 24.6% | 26.9% |
| our bots, losses | **17.2%** | **13.9%** |
| boss (all corpora) | 18-25% | 17-30% |

Our construction-class hits mostly land on stale, abandoned boss segments. Gold players hit the
segment the boss is building right now, which is consistent with the lower completion rate and the
higher paint waste they inflict (boss dead-cell share 16% against them versus 11% against us).
In `RolloutBot.chooseTarget` an unfinished opponent track is worth `ROLLOUT_TRACK_WEIGHT = 0.4`
regardless of age, against `(income change) x 8 turns` for an active connection; nothing in the
valuation distinguishes a live project from a two-week-old stump.

## Other observations worth keeping

- Gold players build on 92-99% of turns through turn 45 and place about 111 cells per game in
  54 turns; we build on 61-86% of turns in 21-45 and place 95-99 cells in 85 turns. Their late
  construction is often "dead" (36% of cells placed in turns 31-45 never join a scoring path),
  so the extra cells are not simply extra income; what they buy is not resolved by this study.
- Our lost games run to turn 100 with both bots waiting (build 5%, disrupt 40% in turns 61+); the
  boss is ahead and does not need to act. This is a consequence of the earlier deficit, not its
  cause.
- The boss never builds in a region at instability >= 2 (0.0% of its cells); Gold players and we
  do so in 1-3% of placements.
- Self-play between v08 copies reaches an accepted disruption on only 64% of turns and lasts 91
  turns on average, versus 53-57 turns against the boss; the local bench does not reproduce the
  boss meta, as `BOTS.md` has noted since v04.

## Reproduction

```powershell
python tools/diagnostics/boss_winners_study.py fetch            # snapshots + winners corpus
python tools/diagnostics/boss_winners_study.py study --out docs/evidence/boss-winners-study-v1.json
```

`study` refuses to overwrite existing evidence without `--force` and fails on any income mismatch.
The raw replay corpora are gitignored; the leaderboard/battle snapshots are dated and live under
`build/cg-snapshots/`.
