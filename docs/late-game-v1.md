# late-game-v1 — what the winners build in their last third (2026-10-27)

PLAN item from `end-forecast-v1`: "late-game construction of the winners (leader corpus `replays/cg-indrill`): what the top
players build in their last third (protect active connections vs start routes) — a mechanism candidate, not a parameter."
The user's "Да" after `route-commit-v1` was parked. Corpora: Indrill's 282 contest games (Legend #1, 265 W / 17 L, all
validated) and v12's 147 arena games (agent 6814073, 89 W / 58 L, `build/analysis/arena-v12b`). Diagnostic only, no bot
change. Evidence: `docs/evidence/late-game-v1/`.

Tools: `btk.tools.CgPlayerStudy` (per-turn score traces) + `tools/diagnostics/late_game_trajectory.py`; `btk.tools.CgCellStudy`
(one row per placed track; the Indrill export regenerated with the current tool reproduces the September one exactly) +
`tools/diagnostics/late_game_cells.py`; new `btk.tools.InkRescueStudy` + `tools/diagnostics/late_game_rescue.py` (does an earning
connection survive the ink of a region on its path?).

## 1. The last third decides almost nothing

| score gap (median) and share of the game's points scored, at a fraction of the game length L | 0.2 L | 1/3 L | 0.5 L | **2/3 L** | end |
|---|---|---|---|---|---|
| v12 wins (89, median L 58) | −13 · 8 % | +42 · 28 % | +313 · 64 % | **+486 · 89 %** | +470 |
| v12 losses (58, median L 82) | −44 · 18 % | −120 · 51 % | −254 · 83 % | **−412 · 94 %** | −465 |
| Indrill wins (265, median L 48) | −4 · 5 % | +19 · 22 % | +156 · 56 % | **+244 · 83 %** | +339 |

- v12's leader changes after 2/3 L in 12 of 147 games (7 wins came from behind, 5 losses from ahead: net +2 for us); Indrill's
  in 14 of 282.
- Points scored in the last third (mean, own vs opponent): v12 wins 340 vs 230, v12 losses 216 vs 299, Indrill wins 319 vs 196
  (he wins the last third in 248 of 265 wins). **Had v12 drawn the last third of every loss, 5 of 58 losses would have turned**
  (3.4 % of the games) — the ceiling of any last-third improvement.
- The losses are decided between 0.2 L and 2/3 L (median gap −44 → −412), i.e. turns ~16–55 of an 82-turn loss.

## 2. What the paint does in each third (`*-cells.txt`)

Paint per turn, share of the paint that ever earns, points per paint point, and the class of the paint at placement
(bypass = on the shortest unstable-region-avoiding path of an active pair crossing an unstable region; complete = on or within
+1 paint of a cheapest completion of an inactive pair; filler = on no route of any kind):

| | third 2: paint · earns · pts/paint · complete / bypass / filler | **third 3**: paint · earns · pts/paint · complete / bypass / filler |
|---|---|---|
| Indrill (wins) | 2.99 · 71 % · 9.8 · 51 / 19 / 20 % | **2.72 · 26 % · 1.2** · 28 / 6 / 56 % |
| his opponents | 2.98 · 50 % · 5.8 · 47 / 16 / 27 % | 2.65 · 13 % · 0.6 · 26 / 4 / 65 % |
| v12 (wins) | 2.91 · 63 % · 12.3 · 47 / 18 / 19 % | **2.61 · 21 % · 1.2** · 19 / 6 / 58 % |
| opponents (v12 wins) | 2.57 · 66 % · 10.3 · 46 / 23 / 20 % | 1.28 · 37 % · 1.8 · 30 / 12 / 46 % |
| **v12 (losses)** | **2.77 · 35 % · 5.7 · 33 / 9 / 43 %** | 1.88 · 9 % · 0.5 · 10 / 3 / 79 % |
| opponents (v12 losses) | 2.09 · 62 % · 11.2 · 41 / 20 / 26 % | 0.80 · 30 % · 1.7 · 23 / 8 / 60 % |

In the last third the leader builds what v12 builds when it wins: mostly filler (56 vs 58 %), a quarter on completions, little
on bypasses, with the same yield (1.2 points per paint point). The difference between winners and losers sits in the **middle
third**: Indrill's middle-third paint earns 71 % against his opponents' 50 %; v12's earns 35 % against its opponents' 62 % in
its losses, where 43 % of it is filler that earns 9 % and only 9 % goes on bypasses (opponents 20 %; the route classes are
properties of the board, so both sides face the same opportunities in a game).

## 3. Protecting active connections (`*-rescue.txt`)

`risk` = an earning connection crosses a non-town region one disrupt from ink; covered = it would still pay the player after
the worst such ink. `hit` = a region of the path was inked; rescued = it still pays after the turn.

| | at-risk connections per turn | covered | inked connections per turn | rescued |
|---|---|---|---|---|
| v12 wins / its opponents | 1.38 / 1.31 | 17 % / 16 % | 0.99 / 0.95 | 18 % / 17 % |
| v12 losses / its opponents | 1.26 / 1.27 | 14 % / 13 % | 0.86 / 0.85 | 19 % / 19 % |
| Indrill wins / his opponents | 1.48 / 1.46 | 25 % / 25 % | 0.94 / 0.93 | 25 % / 25 % |

Coverage is a property of the game, not of the player: both sides of a game keep the same share of their threatened
connections (the bypass paths are shared rails), every rescue runs over tracks placed before the ink, and in the last third
coverage falls to 3–11 % in the losses of both corpora. Indrill's games have more alternative paths than v12's arena games,
for him and his opponents alike.

## Reading

1. **The last third is not a lever** for v12: it carries 6–11 % of the points of v12's games, flips 12 of 147 results (net +2
   for us), and a drawn last third would turn 5 of 58 losses. The winners' last third looks like v12's last third in its wins
   (filler-heavy, 1.2 points per paint point).
2. **Neither "protect active connections" nor "start routes" separates the winners late**: protection is shared by both
   sides of a game, and the late completion share is a quarter for everyone.
3. Where v12 loses is again the **middle third** (turns ~16–55 of a loss): its paint earns 35 % vs the opponent's 62 %, it puts
   43 % of that paint on filler and half the opponent's share on bypasses around unstable regions. v12 proposes a bypass only
   around the single region its threat model predicts (`propose` variant 2); the opponents relocate around every unstable
   region. This is the construction line again (`route-choice-v1`, `route-order-v1`, `route-commit-v1`, all on the middle game).

The late-game item is closed. No candidate follows from the last third; a middle-game bypass candidate would be the next
construction step if the user picks it.
