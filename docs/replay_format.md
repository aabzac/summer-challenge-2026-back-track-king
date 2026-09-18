# CodinGame replay format (gameResult/findByGameId)

Downloaded by `gradlew cgFetch` into `replays/cg/<gameId>.json`. No login needed. Parsed by
`btk.tools.CgReplay`.

IDE and boss runs return the same frame document directly from `TestSession/play`, but omit most
agent information. `tools/codingame_replay_export.user.js` captures its game id and immediately
re-fetches the canonical document through the public `gameResult/findByGameId` endpoint into
`replays/cg/boss/<strategy>/<build-id>/` when its first-turn build marker matches a locally retained
submission manifest. Unverified captures go to `boss/unknown/`; the raw response is kept in
`boss/raw/unknown/` as a fallback. Capture metadata lives separately in `boss/_metadata/` so the
canonical CG bytes are preserved. The marker identifies a self-reported build, not proof of the
code executed remotely. Existing files moved from `bot/` remain flat in `boss/` and have no new
build attribution. IDE play requests may use `-1` for current code and `-2` as a boss sentinel;
canonical replays can identify that boss by a positive id and an `arenaboss` object instead.
Player positions always come from `agents[].index`, not array order or the sign of an agent id.

Each new capture sidecar is `boss/_metadata/<gameId>.json`. It records the canonical file path
and SHA-256, player/boss slots, capture time and qualified attribution. `unknown` means no usable
first-output marker; `unverified-marker` means a marker exists but its source/configuration cannot
be verified against the local immutable registry. Both use `boss/unknown/` for new captures.
Only `marker-matches-local-artifact` assigns a new capture to its strategy/build directory;
`executionIdentityIndependentlyVerified` remains `false`. A requested build label never substitutes
for this check. Identical retries preserve replay and sidecar bytes, recheck current attribution,
and reject conflicting evidence; historical paths/metadata are not silently relabelled.

Canonical CG documents are kept as validation evidence and are not opened directly by our
visualizer. `cgValidate` only compares them with `btk.core`. `cgRender -Ppath=<file-or-directory>`
performs the same exact validation and writes the reconstructed visualizer schema to
`replays/rendered/cg/arena/` or `replays/rendered/cg/boss/`; the boss category is detected from
`arenaboss` or legacy agent id `-2`. Fully local games continue to use `replays/local/`.

Rendered metadata carries the actual CG `refereeInput` seed as both `meta.seed` (compatibility
number) and `meta.seedText` (exact signed 64-bit decimal string). JavaScript viewers must prefer
the string to avoid rounding large seeds. Missing CG seeds omit both fields; malformed seeds
fail export. Old CG exports used a placeholder `seed:0`: re-render them to recover the real seed.
The viewer shows unknown for that legacy placeholder or an unsafe number without `seedText`;
a genuine local zero remains valid. Canonical input files are never rewritten by this update.

Java replay tools accept old `replays/cg/bot/...` and `replays/rendered/cg/bot/...` paths when
the old location is absent and the corresponding `boss/...` location exists. Historical reports
keep their recorded paths and hashes. Boss directory discovery is recursive, accepts numeric
game filenames and excludes `raw/` and `_metadata/`; the default `replays/cg/` arena pool stays
flat so its map sample does not silently change. Duplicate boss game filenames across folders
are rejected rather than counting a capture twice or overwriting one rendered game.

Visualizer turns include end-of-turn `regionInstability[]` and `regionInked[]` snapshots. The
viewer uses them to thicken a region's outline after each of the first three successful DISRUPTs
and to fill the region after the fourth. Older visualizer JSON remains loadable, but needs to be
rendered again to show this effect.

LocalRunner exports now also contain optional `meta.botDetails[]` with descriptive local bot
names and experiment provenance; `meta.bots[]` retains exact command specs. The viewer prefers
recorded labels and can look up recognized ids in older local replays. CG nicknames are never
resolved through this catalog. Older files without these optional fields still load normally.

## Top level

| field | meaning |
|---|---|
| `gameId` | id |
| `refereeInput` | Java properties containing `seed=<signed long>`; `CgReplay.mapSeed()` parses it and `cgMapValidate` checks exact map reproduction with `MapGenerator` |
| `agents[]` | `{index (0/1 = in-game player position), codingamer:{userId,pseudo}, agentId, score, rank}` — authoritative player mapping; boss agent id `-2` is displayed as `Boss` |
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
