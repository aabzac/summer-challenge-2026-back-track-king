# Statement — Wood 2 (captured 2026-09-13 from the CG IDE)

League objective 1: connect two towns to form an active train connection to instantly win the game.
The Boss AI skips its turns. Fail to form a connection within 100 turns -> loss. Win 3 of 5 to advance.

## Rules

Both players use paint to draw train tracks on a magic map; connecting towns brings prosperity.

### Map

Grid of cells, each with one of three types:

- Type 0: plains
- Type 1: river
- Type 2: mountains

The grid is partitioned into regions (contiguous cells, unique `regionId`). Regions are susceptible to
disruption by players (details in the next league). Some regions contain a town; towns are only on plain cells.

### Towns

Multiple towns placed randomly, one per region at most, and no two regions sharing a border both contain a town.
Each town has a unique `townId` and a list `desiredConnections` of town ids it wants to be connected to via tracks.
Desired connections are unilateral (0 wants 1 does not imply 1 wants 0). A town may have zero desired connections
but is always the subject of at least one other town's desire.

### Placing tracks

Each turn a player gets 3 paint points; unused points are lost.

- 1 point: track on plains
- 2 points: track on river
- 3 points: track on mountains

Track owner = playerId (0/1). If both players place on the same cell in the same turn the owner is 2 (neutral).
A track cannot be placed on a town or on an existing track. Once placed, a track connects to orthogonally adjacent
tracks and towns.

### Connections

For each pair of towns where one has the other in `desiredConnections`: if at least one path exists, the shortest
path becomes the active connection. A path is an uninterrupted sequence of orthogonally adjacent cells with a track
or a town. Among multiple shortest paths, the chosen one prioritizes directions in the order NORTH, EAST, SOUTH, WEST
when moving from the requesting town to the desired town.

At the end of every turn, each active connection gives 1 point to each player for every track they own in the path.

Example 2 (from statement): only the shortest path is used; with two equally short paths from 0 to 1, EAST beats
SOUTH so the path through town 2 is chosen (paths may pass through other towns).

### Actions

At least one action per turn, separated by `;`:

- `PLACE_TRACKS x y` — place a track on a free cell.
- `AUTOPLACE fromX fromY toX toY` — generates PLACE_TRACKS actions for the cheapest path (in paint points) from
  `from` to `to`; does nothing if a path already exists. The generated actions replace this command.
  At most one AUTOPLACE per turn.
- `MESSAGE text` — shown in the viewer.
- `WAIT`

Defeat: no command in time, or an invalid command. (Wood: objective not met within 100 turns.)

## Protocol

Initialization input:

```
myId
width height
height*width lines: regionId type          (left to right, top to bottom)
townCount
townCount lines: townId townX townY desiredConnections   ("1,2,4" or "x")
```

Per-turn input:

```
myScore foeScore
height*width lines: trackOwner instability inked partOfActiveConnections
```

- `trackOwner`: -1 none, 0/1 owner, 2 neutral
- `instability`: unused in this league
- `inked`: unused in this league (starter parses it as a string, "0" = false)
- `partOfActiveConnections`: "1-2,1-3,4-7" or "x"

Output: one line of actions.

Constraints: response time 50 ms per turn, 1000 ms first turn; 21 <= width <= 30; 14 <= height <= 20;
4 <= townCount <= 12.

## Observed in Silver replays (not yet in statement)

- Actions `DISRUPT <regionId>` (top players use one per turn), metadata fields: `zonesInked`, `enemyTracksInkedOut`,
  `ownTracksInkedOut`, `poisServed`, `totalPois`, `autobuildCalled`, `extraTilesInConnection`,
  `averageTrackOwnershipPercentagePerActiveConnection`, `tracksPlacedOnPlains/River/Mountains`.
- Silver game length: 40 turns (81 frames).
- Starters: Java https://gist.github.com/CGjupoulton/17397683833e324a7b8c7c7c642239d3
