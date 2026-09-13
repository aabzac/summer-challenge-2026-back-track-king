# CodinGame replay format (gameResult/findByGameId)

Downloaded by `gradlew cgFetch` into `replays/cg/<gameId>.json`. No login needed. Parsed by
`btk.tools.CgReplay`.

## Top level

| field | meaning |
|---|---|
| `gameId` | id |
| `refereeInput` | Java properties containing `seed=<signed long>`; `CgReplay.mapSeed()` parses it and `cgMapValidate` checks exact map reproduction with `MapGenerator` |
| `agents[]` | `{index (0/1 = in-game player position), codingamer:{userId,pseudo}, agentId, score, rank}` — authoritative player mapping |
| `btkNicknames` | compatibility field added by CgFetch, keyed by the authoritative `agents[].index`; the parser prefers `agents[].codingamer.pseudo` |
| `ranks[]`, `scores[]` | final ranking; final scores are more reliably read from the last frame `endScreen` |
| `metadata` | per-player stats, keys suffixed `_0/_1`: `tracksPlaced`, `tracksPlacedOnPlains/River/Mountains`, `zonesInked`, `enemyTracksInkedOut`, `ownTracksInkedOut`, `poisServed`, `totalPois`, `autobuildCalled`, `extraTilesInConnection`, `averageTrackOwnershipPercentagePerActiveConnection`, `executionTimeMs` |
| `frames[]` | see below |

## Frames

- `frames[0]`: init. `view` = `" 0\n{ "global": { "graphics": "<map>" }, "frame": {...} }"`.
- Then, per turn, two frames: agent 0 (`keyframe=false`, `view=" N\n"`) and agent 1 (`keyframe=true`,
  `view=" N\n{ "duration": ms, "graphics": "<events>" }"`). Each frame carries that agent's `stdout`
  (the full command line) — for BOTH players. `gameInformation`/`summary` are empty in this game.
- Last keyframe also has `"endScreen": [[score0, score1], "logo.png", ["645 points", "913 points"]]`.
- A timeout is reported in the terminal keyframe `summary`, for example
  `$0 has not provided 1 lines in time`. CG may still retain that player's late `stdout`, but did
  not apply it; `CgReplay.turnFailures` records the failure and `CgValidate` resolves that player
  as `WAIT` before applying the terminal `-1` score.
- Number of turns varies between games (observed 40 and 55) — the end condition is not yet known.

## Global graphics (map)

`width|height|3|<w*h cells "regionId type">|townCount|<towns "id x y desired">`

Cells are listed left-to-right, top-to-bottom. `desired` is `"1,3,4"` or `"x"`. The constant `3`
after height is unexplained (paint per turn?).

## Turn graphics (events)

`count|type|startMs|endMs|data|type|startMs|endMs|data|...` (data tokens separated by spaces).

| type | data | meaning (inferred) |
|---|---|---|
| 1 | `player x y k` | track placed; `k` = 1 normally, 2 seen when both players hit the same cell (neutral?) — verify |
| 2 | `player regionId` | DISRUPT issued |
| 3 | `player points townA townB` | scoring event for connection A-B this turn |
| 5 | `regionId x y x y ...` | region inked (cells listed); tracks in it are removed ("inked out") |
| 6 | `townA townB` | connection state changed (path changed or connection lost) |
| 7 | `townA townB x y x y ...` | active connection path (incl. both towns) |
| 8 | `x y` | path cell highlight animation (one per cell of a new path) |
| 4 | ? | not yet observed |

## What CgValidate compares

For every turn: set of cells whose owner changed (vs. type-1 events), per-player points gained
(vs. sum of type-3 events), and, when type-7 events exist, exact path cells per connection.
Placement semantics (cost, budget, neutral clash) match CG in all downloaded games up to the
first DISRUPT effects; scoring diverges once regions get inked — Bronze rules needed.
