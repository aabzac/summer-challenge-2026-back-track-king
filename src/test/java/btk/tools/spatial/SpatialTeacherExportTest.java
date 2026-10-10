package btk.tools.spatial;

import btk.bot.Bot;
import btk.bot.neural.SpatialObservation;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic states and stub policies only: these tests collect no native teacher data. */
class SpatialTeacherExportTest {
    private static final int BUILD_MASK_OFFSET = 62_424, DISRUPT_MASK_OFFSET = 63_025, LABEL_OFFSET = 63_626;

    @Test void orderedMixedCostLabelsCanonicalizeRegionAndExposeOnlyEarlierOwnSelections() throws Exception {
        GameState s = rectangle(); s.terrain[6] = GameState.RIVER;
        List<Action> actions = List.of(Action.place(3, 1), Action.place(1, 1), Action.disruptAt(2, 1));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        SpatialTeacherExport.BinaryRows rows = new SpatialTeacherExport.BinaryRows(bytes);
        JsonObject audit = new JsonObject();
        SpatialTeacherExport.writeTeacherRows(s, 1, SpatialTeacherExport.validate(s, 1, actions, new Pathing(s.size)),
                new SpatialObservation(s), rows, audit);
        assertEquals(4, rows.rows);
        assertEquals(JsonParser.parseString("[33,31,600,7]"), audit.get("labels"));
        assertEquals(JsonParser.parseString("[[],[33],[33,31],[33,31]]"), audit.get("prefixes"));
        assertEquals(JsonParser.parseString("[3,2,0,0]"), audit.get("paintBefore"));
        assertEquals(JsonParser.parseString("[false,false,false,true]"), audit.get("stoppedBefore"));
        assertEquals(16 + 4 * 63_638, bytes.size());
        for (int owner : s.owner) assertEquals(GameState.NO_TRACK, owner);
        assertEquals(0, s.turn);
        ByteBuffer binary = ByteBuffer.wrap(bytes.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        int[] labels = {33,31,600,7};
        for (int stage = 0; stage < 4; stage++) {
            int at = row(stage);
            assertEquals(labels[stage], binary.getInt(at + LABEL_OFFSET));
            assertEquals(stage, binary.getInt(at + LABEL_OFFSET + 4));
            assertEquals(0, binary.getInt(at + LABEL_OFFSET + 8));
            assertEquals(stage >= 1 ? 1f : 0f, binary.getFloat(at + 4 * (13 * 600 + 33)));
            assertEquals(stage >= 2 ? 1f : 0f, binary.getFloat(at + 4 * (13 * 600 + 31)));
            assertEquals(0f, binary.getFloat(at + 4 * (8 * 600 + 33)));
            assertEquals(0f, binary.getFloat(at + 4 * (13 * 600 + 599)));
        }
        assertEquals(1, maskCount(binary, 2, BUILD_MASK_OFFSET));
        assertEquals(1, binary.get(row(3) + DISRUPT_MASK_OFFSET + 7));
    }

    @Test void stopWithPaintRemainingForcesOnlyLaterBuildStagesAndThreePlacementsKeepOrder() throws Exception {
        GameState s = rectangle();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        SpatialTeacherExport.BinaryRows rows = new SpatialTeacherExport.BinaryRows(bytes);
        JsonObject audit = new JsonObject();
        SpatialTeacherExport.writeTeacherRows(s, 0,
                SpatialTeacherExport.validate(s, 0, List.of(Action.place(3,1)), new Pathing(s.size)),
                new SpatialObservation(s), rows, audit);
        assertEquals(JsonParser.parseString("[33,600,600,600]"), audit.get("labels"));
        ByteBuffer data = ByteBuffer.wrap(bytes.toByteArray()).order(ByteOrder.LITTLE_ENDIAN);
        assertTrue(maskCount(data, 1, BUILD_MASK_OFFSET) > 1);
        assertEquals(1, maskCount(data, 2, BUILD_MASK_OFFSET));
        assertEquals(1, data.get(row(2) + BUILD_MASK_OFFSET + 600));
        JsonObject allThree = new JsonObject();
        SpatialTeacherExport.writeTeacherRows(s, 0,
                SpatialTeacherExport.validate(s, 0, List.of(Action.place(3,1), Action.place(1,1), Action.place(2,1)), new Pathing(s.size)),
                new SpatialObservation(s), rows, allThree);
        assertEquals(JsonParser.parseString("[33,31,32,600]"), allThree.get("labels"));
        assertEquals(JsonParser.parseString("[[],[33],[33,31],[33,31,32]]"), allThree.get("prefixes"));
    }

    @Test void nativeAutoplaceUsesCoreExpandedOrderAndNeverPlacesPadding() throws Exception {
        GameState s = new GameState(4, 2, new int[]{0,1,2,3,4,5,6,7}, new int[8],
                new int[]{0,3}, new int[]{1,1}, new int[][]{{1},{}});
        Rules.PlayerMoves moves = SpatialTeacherExport.validate(s, 0,
                List.of(Action.autoplace(0,1,3,1), Action.disruptAt(1,1)), new Pathing(s.size));
        JsonObject audit = new JsonObject(); ByteArrayOutputStream output = new ByteArrayOutputStream();
        SpatialTeacherExport.writeTeacherRows(s, 0, moves, new SpatialObservation(s),
                new SpatialTeacherExport.BinaryRows(output), audit);
        assertEquals(JsonParser.parseString("[31,32,600,5]"), audit.get("labels"));
        assertEquals(2, moves.count); assertEquals(2, moves.cost);
        assertEquals(5, moves.cells[0]); assertEquals(6, moves.cells[1]);
    }

    @Test void bothSeatsSeeSamePreTurnAndCoreAcceptedCollisionSurvivesImmediateInkingAudit() throws Exception {
        GameState s = line(); s.regionInstability[1] = 3; s.instability[1] = 3;
        StubBot teacher = new StubBot(List.of(Action.place(1,0), Action.disrupt(1)));
        StubBot opponent = new StubBot(List.of(Action.place(1,0), Action.disrupt(1)));
        teacher.mutateCopy = true; opponent.mutateCopy = true;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SpatialTeacherExport.BinaryRows rows = new SpatialTeacherExport.BinaryRows(output);
        JsonArray records = new JsonArray(); JsonObject stats = new JsonObject();
        SpatialTeacherExport.playGame(s, 1, teacher, opponent, 1, () -> 0L, rows, records, stats, "v01");
        assertEquals(1, teacher.seat); assertEquals(0, opponent.seat);
        assertEquals(GameState.NO_TRACK, teacher.ownerSeen); assertEquals(GameState.NO_TRACK, opponent.ownerSeen);
        assertEquals(1, teacher.calls); assertEquals(1, opponent.calls); assertEquals(4, rows.rows);
        assertEquals(0, s.score[0]); assertEquals(0, s.score[1]);
        assertEquals(GameState.NO_TRACK, s.owner[1]); assertTrue(s.inked[1]); assertEquals(5, s.regionInstability[1]);
        JsonObject turn = records.get(0).getAsJsonObject();
        assertEquals(turn.get("teacherExpanded"), turn.get("acceptedTeacher"));
        assertEquals(turn.get("opponentExpanded"), turn.get("acceptedOpponent"));
        assertEquals(JsonParser.parseString("[1]"), turn.get("neutralised"));
        assertEquals(JsonParser.parseString("[1]"), turn.get("inkedRegions"));
        assertEquals(JsonParser.parseString("[1,600,600,1]"), turn.get("labels"));
        assertTrue(turn.get("terminalAfter").getAsBoolean());
        assertEquals("opponent", turn.getAsJsonArray("calls").get(0).getAsJsonObject().get("role").getAsString());
        assertEquals(1, stats.getAsJsonObject("teacher:v09").get("completed").getAsInt());
    }

    @Test void actualTerminalAndDeclaredSmokeCapProduceNoExtraDecisions() throws Exception {
        for (boolean terminal : new boolean[]{false, true}) {
            GameState s = line(); if (terminal) s.maxTurns = 1;
            StubBot a = new StubBot(List.of(Action.waitAction())), b = new StubBot(List.of(Action.waitAction()));
            SpatialTeacherExport.BinaryRows rows = new SpatialTeacherExport.BinaryRows(new ByteArrayOutputStream());
            JsonArray records = new JsonArray();
            SpatialTeacherExport.playGame(s, 0, a, b, terminal ? Integer.MAX_VALUE : 2, () -> 0L,
                    rows, records, new JsonObject(), "v01");
            int turns = terminal ? 1 : 2;
            assertEquals(turns, s.turn); assertEquals(turns, a.calls); assertEquals(turns, b.calls);
            assertEquals(4 * turns, rows.rows); assertEquals(turns, records.size());
            assertEquals(terminal, Rules.gameOver(s, new Pathing(s.size)));
            for (int t = 0; t < turns; t++) {
                JsonObject record = records.get(t).getAsJsonObject();
                assertEquals(t, record.get("turn").getAsInt()); assertEquals(t * 4, record.get("rowStart").getAsInt());
                assertEquals(JsonParser.parseString("[600,600,600,600]"), record.get("labels"));
            }
        }
    }

    @Test void skippedOrOverBudgetNativePlanFailsBeforeRowsOrStateTransitionAndRetainsDiagnostics() throws Exception {
        for (boolean late : new boolean[]{false, true}) {
            GameState s = line();
            StubBot teacher = new StubBot(late ? List.of(Action.waitAction()) : List.of(Action.place(0,0)));
            StubBot opponent = new StubBot(List.of(Action.waitAction()));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            SpatialTeacherExport.BinaryRows rows = new SpatialTeacherExport.BinaryRows(bytes);
            JsonArray records = new JsonArray(); JsonObject stats = new JsonObject(); AtomicLong time = new AtomicLong();
            assertThrows(RuntimeException.class, () -> SpatialTeacherExport.playGame(s, 0, teacher, opponent, 1,
                    () -> time.getAndAdd(late ? 60_000_000_000L : 0L), rows, records, stats, "v01"));
            assertEquals(0, s.turn); assertEquals(0, rows.rows); assertEquals(16, bytes.size()); assertEquals(0, opponent.calls);
            JsonObject call = records.get(0).getAsJsonObject().getAsJsonArray("calls").get(0).getAsJsonObject();
            assertFalse(call.get("complete").getAsBoolean()); assertTrue(call.has("failure"));
            assertEquals(late, call.get("legal").getAsBoolean());
            assertEquals(late ? 60000.0 : 0.0, call.get("elapsedMs").getAsDouble());
            assertEquals(1, stats.getAsJsonObject("teacher:v09").get("failed").getAsInt());
        }
        GameState s = line();
        assertThrows(IllegalArgumentException.class, () -> SpatialTeacherExport.validate(s, 0,
                List.of(Action.place(1,0), Action.place(1,0)), new Pathing(s.size)));
    }

    @Test void gzipBinaryPreservesExactFloatBitsArraysMaskBytesHeaderAndUnalignedInts() throws Exception {
        GameState s = rectangle(); SpatialObservation observation = new SpatialObservation(s); observation.prepare(s, 1);
        observation.planes[100] = -0.0f; observation.towns[100] = Float.intBitsToFloat(0x3eaaaaab);
        observation.scalars[5] = Float.MIN_VALUE;
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            SpatialTeacherExport.BinaryRows writer = new SpatialTeacherExport.BinaryRows(gzip);
            writer.write(observation, 600, 0, 23);
        }
        byte[] raw;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed.toByteArray()))) { raw = gzip.readAllBytes(); }
        assertEquals(63_654, raw.length);
        ByteBuffer b = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN); byte[] magic = new byte[8]; b.get(magic);
        assertArrayEquals("BTKSD001".getBytes(java.nio.charset.StandardCharsets.US_ASCII), magic);
        assertEquals(63_638, b.getInt()); assertEquals(0, b.getInt());
        for (float[] array : new float[][]{observation.planes, observation.towns, observation.scalars})
            for (float value : array) assertEquals(Float.floatToRawIntBits(value), b.getInt());
        for (int value : observation.townCells) assertEquals(value, b.getInt());
        for (int value : observation.regionIds) assertEquals(value, b.getInt());
        for (boolean value : observation.buildMask) assertEquals(value ? 1 : 0, b.get());
        for (boolean value : observation.disruptMask) assertEquals(value ? 1 : 0, b.get());
        assertEquals(600, b.getInt()); assertEquals(0, b.getInt()); assertEquals(23, b.getInt()); assertFalse(b.hasRemaining());
        ByteArrayOutputStream invalidOutput = new ByteArrayOutputStream();
        SpatialTeacherExport.BinaryRows invalid = new SpatialTeacherExport.BinaryRows(invalidOutput);
        assertThrows(IllegalArgumentException.class, () -> invalid.write(observation, 599, 0, 0));
        observation.planes[0] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> invalid.write(observation, 600, 0, 0));
        assertEquals(0, invalid.rows); assertEquals(16, invalidOutput.size());
    }

    @Test void fixedWholeMapSchedulesAndExclusionPreflightDoNotSubstituteSeeds() {
        List<SpatialTeacherExport.MapSpec> maps = SpatialTeacherExport.schedule("corpus");
        String[] opponents = {"v01", "spatial-ppo-v2", "v09"};
        assertEquals(30, maps.size());
        for (int i = 0; i < 30; i++) {
            boolean train = i < 24; int index = train ? i : i - 24;
            SpatialTeacherExport.MapSpec map = maps.get(i);
            assertEquals(train ? "train" : "validation", map.split()); assertEquals(index, map.index());
            assertEquals((train ? 12_000_000L : 12_100_000L) + index, map.seed());
            assertEquals(opponents[index % 3], map.opponent());
        }
        List<SpatialTeacherExport.MapSpec> smoke = SpatialTeacherExport.schedule("smoke"); assertEquals(3, smoke.size());
        for (int i = 0; i < 3; i++) { assertEquals(11_900_000L + i, smoke.get(i).seed()); assertEquals(opponents[i], smoke.get(i).opponent()); }
        assertThrows(IllegalArgumentException.class, () -> SpatialTeacherExport.schedule("replacement"));
        Set<String> seen = new HashSet<>();
        assertThrows(IllegalArgumentException.class, () -> SpatialTeacherExport.requireFresh("old", Set.of("old"), seen));
        assertTrue(seen.isEmpty()); SpatialTeacherExport.requireFresh("fresh", Set.of("old"), seen);
        assertThrows(IllegalArgumentException.class, () -> SpatialTeacherExport.requireFresh("fresh", Set.of("old"), seen));
        assertEquals(Set.of("fresh"), seen);
    }

    private static int row(int stage) { return 16 + stage * 63_638; }
    private static int maskCount(ByteBuffer bytes, int stage, int offset) {
        int n = 0; for (int i = 0; i < 601; i++) n += bytes.get(row(stage) + offset + i); return n;
    }
    private static GameState rectangle() {
        int[] regions = new int[10]; Arrays.setAll(regions, i -> i);
        return new GameState(5, 2, regions, new int[10], new int[]{0,4}, new int[]{1,1}, new int[][]{{1},{}});
    }
    private static GameState line() {
        return new GameState(3, 1, new int[]{0,1,2}, new int[3], new int[]{0,2}, new int[]{0,0}, new int[][]{{1},{}});
    }
    private static final class StubBot implements Bot {
        final List<Action> actions; int seat = -1, calls, ownerSeen; boolean mutateCopy;
        StubBot(List<Action> actions) { this.actions = actions; }
        public String name() { return "synthetic fixture"; }
        public void init(GameState s, int me) { seat = me; s.score[0] = 123456; }
        public List<Action> play(GameState s, int me, long budgetMs) {
            assertEquals(seat, me); assertEquals(60_000L, budgetMs); assertEquals(0, s.score[0]);
            calls++; ownerSeen = s.owner[1];
            if (mutateCopy) { s.score[0] = 123456; s.owner[1] = me; }
            return actions;
        }
    }
}
