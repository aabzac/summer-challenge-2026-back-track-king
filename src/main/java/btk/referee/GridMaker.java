package btk.referee;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

import btk.core.GameState;

/**
 * Map-generation-only port of CGjupoulton/SummerChallenge2026 GridMaker, commit
 * 23b2a02781d02751b723432865889fbc5dd75e35. See docs/map_generator.md.
 * Keep RNG calls, float arithmetic, collection order and Coord hashing identical to upstream.
 * Collections here are used only before a game, outside the bot's search/turn budget.
 */
final class GridMaker {
    private static final int MIN_GRID_HEIGHT = 14, MAX_GRID_HEIGHT = 20;
    private static final float ASPECT_RATIO = 1.5f;
    private static final int MIN_TOWN_DISTANCE = 4;
    private static final int AVERAGE_TILES_PER_ZONE_COEFF_TO_GRID_HEIGHT = 2;
    private static final int AVERAGE_TILES_PER_TOWN = 50;
    private static final float RIVER_SPLIT_PROBA = 0.055f;
    private static final float RIVER_TO_LAND_MIN_RATIO = 0.07f;
    private static final int MIN_MOUNTAINS = 2;
    private static final float MOUNTAIN_TO_CELL_RATIO = 0.04f;
    private static final int MIN_RIVER_LENGTH = 3;

    private class River {
        public Coord current; // equal to last item in history
        public List<Coord> history;
        public Direction preferredDirection;

        public River(Coord coord, List<Coord> history, Direction preferredDirection) {
            this.current = coord;
            this.history = new ArrayList<>(history);
            this.history.add(coord);
            this.preferredDirection = preferredDirection;
        }

        boolean isStart() {
            return history.size() == 1;
        }
    }

    private Random random;
    private int h;
    private int w;
    private LinkedList<Coord> freeBorders;
    private LinkedList<Coord> freeCoords;
    private Grid grid;

    private boolean isCorner(int x, int y) {
        return (x == 0 && y == 0)
            || (x == 0 && y == h - 1)
            || (x == w - 1 && y == 0)
            || (x == w - 1 && y == h - 1);
    }

    private boolean isEdge(int x, int y) {
        return x == 0 || y == 0 || x == w - 1 || y == h - 1;
    }

    private boolean hasWaterNearby(Grid grid, Coord tile, List<Coord> riverHistory) {
        List<Coord> ignoreCoords = riverHistory.subList(Math.max(riverHistory.size() - 2, 0), riverHistory.size());

        var neighs = grid.getNeighbours(tile, Grid.ADJACENCY_8);
        return neighs.stream()
            .filter(n -> {
                return !ignoreCoords.contains(n);
            })
            .anyMatch(n -> grid.get(n).isWater());
    }

    private boolean isAtNFromSides(Coord coord, int n) {
        return coord.x - n < 0 || coord.y - n < 0 || coord.x + n > w - 1 || coord.y + n > h - 1;
    }

    private List<Coord> getAvailableNeighbours(Grid grid, List<Coord> coords, Predicate<Tile> checkAccessible) {
        Set<Coord> availables = new HashSet<>();
        for (Coord coord : coords) {
            List<Coord> neighs = grid.getNeighbours(coord);
            for (Coord neigh : neighs) {
                if (checkAccessible.test(grid.get(neigh))) {
                    availables.add(neigh);
                }
            }
        }

        return availables.stream().toList();
    }

    private List<Coord> getMountainAvailableNeighbours(Grid grid, List<Coord> mountains) {
        return getAvailableNeighbours(grid, mountains, this::isAccessible);
    }

    public void init(Random random) {
        this.random = random;
        this.h = random.nextInt(MIN_GRID_HEIGHT, MAX_GRID_HEIGHT + 1);
        this.w = Math.round(h * ASPECT_RATIO);
    }

    private void initializeGrid() {
        LinkedList<Coord> freeBorders = new LinkedList<>();

        this.grid = new Grid(w, h, true);
        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                grid.get(x, y).setType(Tile.TYPE_GRASS);
                if (isEdge(x, y) && !isCorner(x, y)) {
                    freeBorders.add(grid.get(x, y).coord);
                }
            }
        }

        LinkedList<Coord> freeCoords = new LinkedList<>(grid.cells.values().stream().map(t -> t.coord).toList());

        Collections.shuffle(freeBorders, random);
        Collections.shuffle(freeCoords, random);

        this.freeBorders = freeBorders;
        this.freeCoords = freeCoords;
    }

    public GameState make() {

        initializeGrid();

        // Making mountains
        makeMountains();

        // Making rivers
        makeRivers();

        // Making zones
        int averageTilesPerZone = h / AVERAGE_TILES_PER_ZONE_COEFF_TO_GRID_HEIGHT;
        int nZones = Math.max(1, (h * w) / averageTilesPerZone);

        List<Zone> zones = makeZones(nZones);

        // Making towns
        int nTowns = Math.max(4, (h * w) / AVERAGE_TILES_PER_TOWN); //(1/60%)
        List<Town> towns = makeTowns(zones, nTowns, averageTilesPerZone);

        // Making connections
        makeTownConnections(towns);

        grid.towns = towns;
        grid.zones = zones;

        return grid.toState();
    }

    private void makeTownConnections(List<Town> towns) {
        for (Town t : towns) {
            List<Town> otherTowns = new ArrayList<>(towns.stream().filter(ot -> !ot.equals(t)).toList());
            Collections.shuffle(otherTowns, random);
            int atLeast = Math.min(3, otherTowns.size());
            int atMost = Math.max(atLeast, otherTowns.size() - 4);
            t.desiredConnections = otherTowns.subList(0, random.nextInt(atLeast, atMost + 1));
            t.desiredConnections.sort(Comparator.comparingInt(Town::getId));
        }

        // Remove reciprocal connections
        for (Town t : towns) {
            t.desiredConnections = t.desiredConnections.stream().filter(
                ot -> !ot.desiredConnections.contains(t)
            ).toList();
        }
    }

    private List<Town> makeTowns(List<Zone> zones, int nTowns, int averageTilesPerZone) {
        LinkedList<Zone> availableZones = new LinkedList<>(zones);
        List<Zone> blacklist = new ArrayList<>(zones.size());

        List<Town> towns = new ArrayList<>();

        int step = Math.max(1, AVERAGE_TILES_PER_TOWN / averageTilesPerZone);
        int retries = 100;
        if (availableZones.isEmpty()) {
            return towns;
        }
        for (int i = 0; i < nTowns; i++) {
            int index = random.nextInt(i * step, (i + 1) * step);
            Zone zone = availableZones.get(index % availableZones.size());
            boolean found = false;

            if (!blacklist.contains(zone)) {
                LinkedList<Coord> townCoords = new LinkedList<>(zone.getCoords());
                Collections.shuffle(townCoords, random);

                while (!townCoords.isEmpty()) {
                    Coord townCoord = townCoords.poll();
                    if (
                        isAccessible(grid.get(townCoord))
                            && !isEdge(townCoord.x, townCoord.y)
                            && towns.stream().allMatch(town -> townCoord.manhattanTo(town.coord) >= MIN_TOWN_DISTANCE)
                    ) {
                        towns.add(new Town(i, townCoord));
                        found = true;
                        blacklist.add(zone);
                        zone.getNeighbours().stream().map(zid -> zones.get(zid)).forEach(blacklist::add);
                        break;
                    }
                }
            }

            if (!found && retries > 0) {
                i--;
                retries--;
            }
        }

        int townsLeftToPlace = nTowns - towns.size();
        availableZones.removeAll(blacklist);
        Collections.shuffle(availableZones, random);

        for (int i = 0; i < townsLeftToPlace; ++i) {
            if (availableZones.isEmpty()) {
                break;
            }
            Zone z = availableZones.poll();
            LinkedList<Coord> townCoords = new LinkedList<>(z.getCoords());
            Collections.shuffle(townCoords, random);

            while (!townCoords.isEmpty()) {
                Coord townCoord = townCoords.poll();
                if (
                    towns.stream().allMatch(town -> townCoord.manhattanTo(town.coord) >= MIN_TOWN_DISTANCE)
                ) {
                    towns.add(new Town(i, townCoord));
                    break;
                }
            }

        }

        int idx = 0;
        for (Town town : towns) {
            town.id = idx++;
        }

        for (Town town : towns) {
            Tile tile = grid.get(town.coord);
            Zone zone = zones.get(tile.getZoneId());
            zone.addTown(town);
            tile.townId = town.id;
            tile.setType(Tile.TYPE_GRASS);
        }

        return towns;
    }

    private List<Zone> makeZones(int nZones) {
        int cols = (int) Math.ceil(Math.sqrt(nZones));
        int rows = (int) Math.ceil((double) nZones / cols);
        double cellH = (double) h / rows;
        List<Zone> zones = new ArrayList<>();

        for (int i = 0; i < nZones; i++) {
            int row = i / cols;
            int col = i % cols;
            double x, y;

            if (row == rows - 1 && nZones % cols != 0) {
                int lastRowPoints = nZones % cols;
                double cellW = (double) w / lastRowPoints;
                x = (col + 0.5) * cellW;
            } else {
                double cellW = (double) w / cols;
                x = (col + 0.5) * cellW;
            }

            y = (row + 0.5) * cellH;
            Coord zoneCenter = new Coord((int) x, (int) y);
            Zone zoneSeed = new Zone(i, new ArrayList<>(List.of(zoneCenter)));
            grid.get(zoneCenter).setZoneId(i);
            zones.add(zoneSeed);

        }

        int zoneId = 0;
        Set<Integer> blockedZones = new HashSet<>();
        while (true) {

            if (!blockedZones.contains(zoneId)) {
                if (zones.size() <= zoneId) {
                    return zones;
                }
                Zone zone = zones.get(zoneId);
                List<Coord> neighs = getAvailableNeighbours(grid, zone.getCoords(), t -> t.getZoneId() == -1);
                if (neighs.isEmpty()) {
                    blockedZones.add(zoneId);
                    if (blockedZones.size() == nZones) {
                        break;
                    }
                } else {
                    Coord neighToAdd = neighs.get(random.nextInt(neighs.size()));
                    grid.get(neighToAdd).setZoneId(zoneId);
                    zone.getCoords().add(neighToAdd);
                }
            }
            zoneId = (zoneId + 1) % nZones;
        }

        // Cache neighbours
        for (Zone zone : zones) {
            Set<Integer> allNeighs = new HashSet<>();
            for (Coord coord : zone.getCoords()) {
                List<Coord> neighs = grid.getNeighbours(coord);
                for (Coord neigh : neighs) {
                    int otherId = grid.get(neigh).getZoneId();
                    if (otherId != zone.id) {
                        allNeighs.add(otherId);
                    }
                }
            }
            zone.setNeighbours(allNeighs.stream().toList());
        }

        return zones;
    }

    private void makeRivers() {
        int nRiverCells = Math.round(w * h * RIVER_TO_LAND_MIN_RATIO);

        LinkedList<Coord> availableRiverSources = new LinkedList<>(freeBorders);
        if (random.nextBoolean()) {
            availableRiverSources.addFirst(new Coord(w / 2, h / 2));
        } else {
            availableRiverSources.addFirst(new Coord(w / 2, h / 2 + 1));
        }

        boolean initialRiver = true;

        List<River> generatedRivers = new ArrayList<>();

        while (nRiverCells > 0 && !availableRiverSources.isEmpty()) {
            Coord riverStart = availableRiverSources.poll();
            freeBorders.remove(riverStart);

            Direction direction = getDirectionFromRiverStart(riverStart);

            if (hasWaterNearby(grid, riverStart, List.of())) {
                continue;
            }
            LinkedList<River> riversToExpand = new LinkedList<>();

            //  System.out.println("RIVER START " + riverStart);

            createWater(riversToExpand, new River(riverStart, new ArrayList<>(), direction));
            nRiverCells--;

            while (!riversToExpand.isEmpty()) {

                River river = riversToExpand.poll();
                Coord current = river.current;

                if (isEdge(current.x, current.y) && !river.isStart()) {
                    // End of this river
                    generatedRivers.add(river);
                    continue;
                }

                List<Coord> neighs = getAvailableNeighboursForRiverToFlow(river);

                if (neighs.isEmpty()) {
                    // End of this river
                    generatedRivers.add(river);
                    continue;
                }

                boolean goingToSplit = nRiverCells > 0
                    && neighs.size() >= 2
                    && random.nextFloat() <= RIVER_SPLIT_PROBA;

                goingToSplit |= initialRiver;
                initialRiver = false;

                LinkedHashMap<Coord, Float> weights = createRiverFlowWeights(river, neighs);

                Coord nextCoord = getRandomCoord(weights);

                River nextRiver = new River(
                    nextCoord,
                    river.history,
                    goingToSplit ? Direction.fromCoord(nextCoord.sub(river.current)) : river.preferredDirection
                );

                createWater(riversToExpand, nextRiver);

                nRiverCells--;

                if (!goingToSplit) {
                    continue;
                }

                List<Coord> remainingNeighs = new ArrayList<>(neighs.stream().filter(coord -> !coord.equals(nextCoord)).toList());
                Collections.shuffle(remainingNeighs, random);

                if (remainingNeighs.isEmpty()) {
                    continue;
                }
                Coord splitCoord = remainingNeighs.get(0);

                nextRiver = new River(
                    splitCoord,
                    river.history,
                    Direction.fromCoord(splitCoord.sub(river.current))
                );
                createWater(riversToExpand, nextRiver);
                nRiverCells--;
            }

        }

        deleteShortRivers(generatedRivers);

    }

    private void deleteShortRivers(List<River> generatedRivers) {
        for (River river : generatedRivers) {
            if (river.history.size() < MIN_RIVER_LENGTH) {
                for (Coord coord : river.history) {
                    grid.get(coord).setType(Tile.TYPE_GRASS);
                    if (!freeCoords.contains(coord)) {
                        freeCoords.add(coord);
                    }
                }
            }
        }
    }

    private LinkedHashMap<Coord, Float> createRiverFlowWeights(River river, List<Coord> neighs) {
        LinkedHashMap<Coord, Float> weights = new LinkedHashMap<>();
        neighs.forEach(neig -> {
            weights.put(neig, getWeight(neig, river.current, river.preferredDirection));
        });
        return weights;
    }

    private boolean isAccessible(Tile t) {
        return !t.isWater() && t.getType() != Tile.TYPE_MOUNTAIN;
    }

    private List<Coord> getAvailableNeighboursForRiverToFlow(River river) {
        return grid.getNeighbours(river.current).stream().filter(
            n -> {
                return !hasWaterNearby(grid, n, river.history)
                    && !grid.get(n).isWater()
                    && (!river.isStart() || !isEdge(n.x, n.y)) && isAccessible(grid.get(n));
            }
        ).toList();
    }

    private Direction getDirectionFromRiverStart(Coord riverStart) {
        return riverStart.x == 0 ? Direction.EAST
            : (riverStart.y == 0 ? Direction.SOUTH
                : (riverStart.x == w - 1 ? Direction.WEST
                    : (riverStart.y == h - 1 ? Direction.NORTH : Direction.UNSET)));
    }

    private void makeMountains() {
        int nMountains = Math.max(MIN_MOUNTAINS, Math.round(random.nextFloat(w * h * MOUNTAIN_TO_CELL_RATIO)));
        if (w * h < 10) {
            nMountains = 0;
        }

        for (int i = 0; i < nMountains; ++i) {
            int mountainSize = random.nextInt(2, 8);
            Coord mountainBase = freeCoords.poll();
            if (mountainBase == null) {
                return;
            }
            // System.out.println("Mountain of size " + mountainSize + " from coord " + mountainBase);
            List<Coord> mountainCoords = new ArrayList<>(mountainSize);
            grid.get(mountainBase).setType(Tile.TYPE_MOUNTAIN);
            mountainCoords.add(mountainBase);
            freeBorders.remove(mountainBase);

            for (int j = 0; j < mountainSize; j++) {
                List<Coord> neighs = getMountainAvailableNeighbours(grid, mountainCoords);
                if (neighs.isEmpty()) {
                    break;
                }
                Coord newMountain = neighs.get(random.nextInt(neighs.size()));
                grid.get(newMountain).setType(Tile.TYPE_MOUNTAIN);
                mountainCoords.add(newMountain);
                freeBorders.remove(newMountain);
                freeCoords.remove(newMountain);
            }
        }

    }

    private void createWater(LinkedList<River> riversToExpand, River riverToAdd) {
        riversToExpand.add(riverToAdd);
        grid.get(riverToAdd.current).setType(Tile.TYPE_WATER);
        freeCoords.remove(riverToAdd.current);
    }

    private float getWeight(Coord neig, Coord current, Direction preferredDirection) {
        Coord direction = neig.sub(current);
        if (direction.equals(preferredDirection.coord)) {
            return 1.75f;

        } else if (direction.equals(preferredDirection.opposite().coord)) {
            return 0.25f;

        }
        return 1.0f;

    }

    private Coord getRandomCoord(LinkedHashMap<Coord, Float> weights) {
        float totalWeight = 0f;
        for (float weight : weights.values()) {
            totalWeight += weight;
        }

        float rand = (float) (random.nextFloat() * totalWeight);
        float cumulative = 0f;

        for (Map.Entry<Coord, Float> entry : weights.entrySet()) {
            cumulative += entry.getValue();
            if (rand < cumulative) {
                Coord key = entry.getKey();
                weights.remove(key);
                return key;
            }
        }

        // fallback, should not happen if weights are valid
        return weights.keySet().iterator().next();
    }
    // Minimal map data from the upstream grid classes; no referee/gameplay dependencies.
    private static final class Coord implements Comparable<Coord> {
        final int x, y;

        Coord(int x, int y) {
            this.x = x;
            this.y = y;
        }

        Coord sub(Coord c) {
            return new Coord(x - c.x, y - c.y);
        }

        int manhattanTo(Coord c) {
            return Math.abs(x - c.x) + Math.abs(y - c.y);
        }

        @Override
        public int hashCode() {
            // HashSet iteration order is part of the generator's seed compatibility.
            return 31 * (31 + x) + y;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Coord c && x == c.x && y == c.y;
        }

        @Override
        public int compareTo(Coord c) {
            return x != c.x ? Integer.compare(x, c.x) : Integer.compare(y, c.y);
        }
    }

    private enum Direction {
        NORTH(0, -1), EAST(1, 0), SOUTH(0, 1), WEST(-1, 0), UNSET(0, 0);

        final Coord coord;

        Direction(int x, int y) {
            coord = new Coord(x, y);
        }

        static Direction fromCoord(Coord coord) {
            for (Direction d : values()) if (d.coord.equals(coord)) return d;
            return UNSET;
        }

        Direction opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case EAST -> WEST;
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case UNSET -> UNSET;
            };
        }
    }

    private static final class Tile {
        static final int TYPE_GRASS = GameState.PLAINS;
        static final int TYPE_WATER = GameState.RIVER;
        static final int TYPE_MOUNTAIN = GameState.MOUNTAIN;
        final Coord coord;
        int type;
        int zoneId = -1;
        int townId = -1;

        Tile(Coord coord) { this.coord = coord; }
        void setType(int type) { this.type = type; }
        int getType() { return type; }
        boolean isWater() { return type == TYPE_WATER; }
        int getZoneId() { return zoneId; }
        void setZoneId(int zoneId) { this.zoneId = zoneId; }
    }

    private static final class Grid {
        static final Coord[] ADJACENCY = {
            Direction.NORTH.coord, Direction.EAST.coord, Direction.SOUTH.coord, Direction.WEST.coord
        };
        static final Coord[] ADJACENCY_8 = {
            Direction.NORTH.coord, Direction.EAST.coord, Direction.SOUTH.coord, Direction.WEST.coord,
            new Coord(-1, -1), new Coord(1, 1), new Coord(1, -1), new Coord(-1, 1)
        };
        final int width, height;
        final LinkedHashMap<Coord, Tile> cells = new LinkedHashMap<>();
        List<Town> towns;
        List<Zone> zones;

        Grid(int width, int height, boolean unusedSymmetry) {
            this.width = width;
            this.height = height;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    Coord coord = new Coord(x, y);
                    cells.put(coord, new Tile(coord));
                }
            }
        }

        Tile get(int x, int y) { return cells.get(new Coord(x, y)); }
        Tile get(Coord coord) { return get(coord.x, coord.y); }

        List<Coord> getNeighbours(Coord pos) { return getNeighbours(pos, ADJACENCY); }

        List<Coord> getNeighbours(Coord pos, Coord[] adjacency) {
            List<Coord> neighbours = new ArrayList<>();
            for (Coord delta : adjacency) {
                Coord n = new Coord(pos.x + delta.x, pos.y + delta.y);
                if (get(n) != null) neighbours.add(n);
            }
            return neighbours;
        }

        GameState toState() {
            int[] regions = new int[width * height], terrain = new int[width * height];
            for (Tile tile : cells.values()) {
                int cell = tile.coord.y * width + tile.coord.x;
                regions[cell] = tile.zoneId;
                terrain[cell] = tile.type;
            }
            int[] tx = new int[towns.size()], ty = new int[towns.size()];
            int[][] desired = new int[towns.size()][];
            for (Town town : towns) {
                tx[town.id] = town.coord.x;
                ty[town.id] = town.coord.y;
                desired[town.id] = town.desiredConnections.stream().mapToInt(Town::getId).toArray();
            }
            return new GameState(width, height, regions, terrain, tx, ty, desired);
        }
    }

    private static final class Zone {
        final int id;
        final List<Coord> coords;
        List<Integer> neighbours;
        final List<Town> towns = new ArrayList<>();

        Zone(int id, List<Coord> coords) {
            this.id = id;
            this.coords = coords;
        }

        List<Coord> getCoords() { return coords; }
        List<Integer> getNeighbours() { return neighbours; }
        void setNeighbours(List<Integer> neighbours) { this.neighbours = neighbours; }
        void addTown(Town town) { towns.add(town); }
    }

    private static final class Town {
        int id;
        final Coord coord;
        List<Town> desiredConnections;

        Town(int id, Coord coord) {
            this.id = id;
            this.coord = coord;
        }

        int getId() { return id; }
    }
}
