# ppo-probe-v1 — does a short PPO loop move?

Requested 6 October 2026 after the beam reconstruction. This checks whether the
training rule from the 3rd-place post-mortem moves a small policy at all.
It is not that bot: the post's network is a convolutional residual net trained for
about 50,000 iterations of 512 games. This probe is a linear policy.

## Fixed before the run

- Both seats play the current weights. Reward each turn is this turn's score gap
  (my points minus the opponent's). A finished game adds +30 to the winner and
  −30 to the loser.
- Discount 0.99. Four epochs, clip 0.2, Adam learning rate 0.01, entropy coefficient
  0.01, value coefficient 0.5. Advantages are standardized once per batch.
- Inputs are public board features, including whether a free cell lies on an
  unfinished cheapest path. The reward does not include that feature.
- 24 iterations, 16 games each. Training seeds 9600000 + iteration * 16 + game.
- Weight seed 20261006. Action RNG seed is that plus 17.
- Before training, the same greedy policy plays both sides on eval seeds
  9610000..9610029, both seats. Wins for the two labels must match, or the run stops.
- After training, greedy trained weights play the frozen initial weights on those
  same 30 maps, both seats (60 games). Draws count half. The learning check passes
  at a score of at least 0.60. That number is not a match against v09 and does not
  promote anything.
- The result file is write-once: `build/analysis/ppo-probe-v1/result.json`.

```powershell
.\gradlew.bat ppoProbe --offline --console=plain "-Pout=build/analysis/ppo-probe-v1/result.json"
```

## Result (6 October 2026)

**PASS_LEARNING: 60 wins, 0 losses, 0 draws** on the 30 held-out maps, both seats.
The run took 256 seconds. v09 was not in this comparison.

The identical initial policy draws all 60 mirror games: on an empty board both seats see the same cheapest paths and pick the same cells. After training, the weight on "this cell is on an unfinished path" moved from 0.0075 to 0.627, and the weight on "this region swings path income to the opponent" moved from 0.0083 to 0.671. Component entropy stayed near 3.6–3.9, so the sampled policy did not collapse to one action. Per-update KL stayed under 0.003.

The value head did not fit. Mean squared error stayed between about 8×10⁴ and 10⁶, so the baseline is a weak part of this short linear probe. The policy gradient still moved the two weights that the reward can see.

This is 384 self-play games. The post's run is about 50,000 iterations of 512 games on a convolutional network. No further iterations, no retune, no match against v09.

## Against v09 (same 30 maps)

Diagnostic only, asked after the learning check. Initial weights and trained weights each play frozen v09 on seeds 9610000..9610029, both seats, CG budgets 486/36 ms. 60 games per policy.

Both sides lose every game: initial 0-60-0, trained 0-60-0. The win rate does not move. The score margin does. Mean score difference versus v09 goes from −8330 to −3435, a shift of +4895 points. The trained policy is closer on 57 of the 60 seats and farther on 3. This is not a promotion and does not change selected v09.
