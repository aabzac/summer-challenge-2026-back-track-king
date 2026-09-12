# Authentic CodinGame map generator

`btk.referee.MapGenerator.generate(long seed)` replaces the approximate generator. Local games
and benchmarks use it automatically unless `-Pmap` / `-Pmaps` supplies downloaded replay maps.
The result contains the same dimensions, terrain, region IDs, town IDs/coordinates and ordered
desired connections as CodinGame for the given nonzero seed.

## Provenance and compatibility

The algorithm and map constants come from
[GridMaker.java](https://github.com/CGjupoulton/SummerChallenge2026/blob/23b2a02781d02751b723432865889fbc5dd75e35/src/main/java/com/codingame/game/grid/GridMaker.java)
and `Game.java` at referee commit `23b2a02781d02751b723432865889fbc5dd75e35`.
`btk.referee.GridMaker` preserves the generation steps and uses minimal internal versions of the
upstream grid data classes. Dependency injection, gameplay-only fields/methods and unused
`makingOf` grid snapshots are omitted. Generation data are converted to the existing `GameState`;
scoring, pathfinding and turn resolution remain in `btk.core`.

The upstream `GridMaker` accepts a `java.util.Random`, but
[MultiplayerGameManager](https://github.com/CodinGame/codingame-game-engine/blob/master/engine/core/src/main/java/com/codingame/gameengine/core/MultiplayerGameManager.java)
actually supplies `SecureRandom.getInstance("SHA1PRNG")` followed by `setSeed(long)`. Using
`new Random(seed)` reproduces none of the 107 downloaded maps. The port requires SHA1PRNG and
fails explicitly if it is unavailable. Seed 0 is rejected because `SecureRandom.setSeed(0L)` is a
no-op: the original CG engine then uses system entropy, so that seed cannot reproduce a map.

Compatibility depends on Java 17 behavior, including:

- The exact RNG methods, their order and short-circuit conditions; float constants/arithmetic.
- Row-major `LinkedHashMap` insertion and `Collections.shuffle` behavior.
- Original `Coord.hashCode()` / `compareTo()` and `HashSet` iteration during mountain/zone growth.
- Ordered neighbours, town placement retries/fallback, and sequential reciprocal-connection removal.

Each call creates its own RNG and generator. No mutable generation state is shared between
benchmark threads. Collections are confined to map initialization, outside bot search and turn
timing. Neither generator class ships in `Player.java`.

## Validation and usage

```bash
gradlew test
gradlew cgMapValidate
gradlew cgValidate
gradlew run -Pbot0=current -Pbot1=v03 -Pseed=-176203007668178900
```

`cgMapValidate` accepts `-Ppath=<replay file or directory>`, just like `cgValidate`. It compares
every cell's terrain/region and every town's coordinates/desired list against the independent
viewer data. A mismatch, missing/invalid seed, malformed replay or empty input directory causes
a nonzero exit status. `cgValidate` separately checks gameplay using the downloaded actions.

The checked-in `src/test/resources/btk/referee/cg-maps.json` contains verbatim map graphics and
seed strings extracted from seven downloaded games, one per height 14 through 20:
903182120, 903182383, 903182662, 903182848, 903183513, 903183758, 903183814.
These fixtures are independent of the generator and do not require the gitignored replay corpus
or referee checkout. Tests also cover parallel generation, fresh state, signed 64-bit seeds and
referee property parsing.

Validated on Java 17.0.12: **107/107 maps match exactly**, **107/107 games reproduce exactly**,
and **35 JUnit tests pass**. The 20-game parallel self-play smoke run completed without action
errors; its scores are not a strategy promotion benchmark. Details are in `DEVLOG.md`.
