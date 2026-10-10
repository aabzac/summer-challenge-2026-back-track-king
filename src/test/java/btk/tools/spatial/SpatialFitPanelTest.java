package btk.tools.spatial;

import btk.bot.Bot;
import btk.bot.Params;
import btk.bot.neural.SpatialNet;
import btk.core.Action;
import btk.core.GameState;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic source trajectories and explicit weights only, without real-data selection or fits. */
class SpatialFitPanelTest {
    @Test void selectedRootUsesBothActualPayloadsAndReplaysAllOtherRootsWithoutInference() throws Exception {
        for (int seat = 0; seat < 2; seat++) {
            Fixture fixture = fixture(seat); GameState replay = fixture.initial.copy();
            AtomicInteger initialCalls = new AtomicInteger(), finalCalls = new AtomicInteger();
            SpatialLabelAudit.Model[] models = models(initialCalls, finalCalls);
            ByteArrayOutputStream regenerated = new ByteArrayOutputStream();
            SpatialTeacherExport.BinaryRows binary = new SpatialTeacherExport.BinaryRows(regenerated);
            JsonObject chosen = SpatialFitPanel.evaluateGame(replay, identity(0, seat), fixture.metadata, fixture.selection,
                    models, binary, () -> 0L);
            assertEquals(1, chosen.get("turn").getAsInt()); assertEquals(3, replay.turn); assertEquals(12, binary.rows);
            assertArrayEquals(fixture.bytes, regenerated.toByteArray());
            assertEquals(Set.of("initial", "final"), chosen.getAsJsonObject("ownPlans").keySet());
            assertEquals(JsonParser.parseString("[0,1,2]"), chosen.getAsJsonObject("ownPlans").getAsJsonObject("initial").get("cells"));
            assertEquals(JsonParser.parseString("[]"), chosen.getAsJsonObject("ownPlans").getAsJsonObject("final").get("cells"));
            assertEquals(JsonParser.parseString("[2,3,4]"), chosen.getAsJsonObject("teacher").get("cells"));
            for (var row : chosen.getAsJsonArray("rows"))
                assertEquals(Set.of("initial", "final"), row.getAsJsonObject().getAsJsonObject("models").keySet());
            // Cooperative clock is consulted only by each actual own-prefix play, not plain
            // teacher-prefix forwards. A direct single play makes exactly the same calls.
            AtomicInteger expectedInitial = new AtomicInteger(), expectedFinal = new AtomicInteger();
            SpatialLabelAudit.Model[] direct = models(expectedInitial, expectedFinal);
            GameState selectedState = fixture.initial.copy();
            btk.core.Rules.resolve(selectedState, seat == 0 ? List.of(Action.place(6,0)) : List.of(Action.waitAction()),
                    seat == 0 ? List.of(Action.waitAction()) : List.of(Action.place(6,0)), new btk.core.Pathing(selectedState.size));
            for (SpatialLabelAudit.Model model : direct) { model.bot.init(selectedState.copy(), seat); model.bot.play(selectedState.copy(), seat, 60_000); }
            assertEquals(expectedInitial.get(), initialCalls.get()); assertEquals(expectedFinal.get(), finalCalls.get());
            assertTrue(initialCalls.get() > 0); assertTrue(finalCalls.get() > 0);
            assertEquals(0, fixture.initial.turn); assertEquals(GameState.NO_TRACK, fixture.initial.owner[0]);
            assertEquals(SpatialLabelAudit.semanticHash(fixture.terminal), SpatialLabelAudit.semanticHash(replay));
        }
    }

    @Test void wrongSelectedInputHashOrInvalidTurnFailsAndNeverEmitsAnAlternativePanel() throws Exception {
        Fixture fixture = fixture(0);
        for (boolean badHash : new boolean[]{true, false}) {
            JsonObject selection = fixture.selection.deepCopy();
            if (badHash) selection.getAsJsonArray("inputSha256").set(2, new com.google.gson.JsonPrimitive("ff".repeat(32)));
            else selection.addProperty("turn", 3);
            assertThrows(IllegalStateException.class, () -> SpatialFitPanel.evaluateGame(fixture.initial.copy(), identity(0,0), fixture.metadata,
                    selection, models(new AtomicInteger(), new AtomicInteger()),
                    new SpatialTeacherExport.BinaryRows(OutputStream.nullOutputStream()), () -> 0L));
        }
    }

    @Test void corruptedUnselectedRootIsRejectedBeforeAnySelectedPolicyCall() throws Exception {
        Fixture fixture = fixture(0); JsonObject metadata = fixture.metadata.deepCopy();
        metadata.getAsJsonArray("turns").get(0).getAsJsonObject().addProperty("preStateSha256", "ff".repeat(32));
        AtomicInteger initialCalls = new AtomicInteger(), finalCalls = new AtomicInteger();
        GameState state = fixture.initial.copy();
        assertThrows(IllegalStateException.class, () -> SpatialFitPanel.evaluateGame(state, identity(0,0), metadata,
                fixture.selection, models(initialCalls, finalCalls), new SpatialTeacherExport.BinaryRows(OutputStream.nullOutputStream()), () -> 0L));
        assertEquals(0, initialCalls.get()); assertEquals(0, finalCalls.get()); assertEquals(0, state.turn);
    }

    @Test void fixedSelectionRejectsMissingDuplicateValidationOutOfRangeAndMismatchedLineage() {
        JsonObject source = syntheticSource(), selection = syntheticSelection(source);
        assertDoesNotThrow(() -> SpatialFitPanel.validateSelection(source, selection));
        for (String corruption : new String[]{"missing", "duplicate", "validation", "turn", "cache", "hash", "weights", "seat"}) {
            JsonObject changed = selection.deepCopy(); JsonArray roots = changed.getAsJsonArray("roots");
            JsonObject root = roots.get(0).getAsJsonObject();
            switch (corruption) {
                case "missing" -> roots.remove(47);
                case "duplicate" -> roots.set(1, roots.get(0).deepCopy());
                case "validation" -> root.addProperty("split", "validation");
                case "turn" -> root.addProperty("turn", 3);
                case "cache" -> root.addProperty("cacheRowStart", 8);
                case "hash" -> root.getAsJsonArray("inputSha256").remove(3);
                case "weights" -> changed.addProperty("initialWeightsSha256", "00".repeat(32));
                case "seat" -> root.addProperty("teacherSeat", 1);
            }
            assertThrows(IllegalStateException.class, () -> SpatialFitPanel.validateSelection(source, changed), corruption);
        }
    }

    private record Fixture(GameState initial, GameState terminal, JsonObject metadata, JsonObject selection, byte[] bytes) {}
    private static Fixture fixture(int seat) throws Exception {
        int[] regions = new int[16]; Arrays.setAll(regions, i -> i);
        GameState initial = new GameState(8,2,regions,new int[16],new int[]{0,7},new int[]{1,1},new int[][]{{1},{}});
        initial.maxTurns = 3; GameState terminal = initial.copy(); JsonArray turns = new JsonArray(); ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        SpatialTeacherExport.playGame(terminal, seat, new Script(true), new Script(false), Integer.MAX_VALUE, () -> 0L,
                new SpatialTeacherExport.BinaryRows(bytes), turns, new JsonObject(), "v01");
        JsonObject metadata = new JsonObject(); metadata.add("turns", turns); metadata.addProperty("turnCount", terminal.turn);
        JsonArray score = new JsonArray(); for (int n : terminal.score) score.add(n); metadata.add("score", score);
        JsonObject selection = new JsonObject(); selection.addProperty("turn", 1); JsonArray hashes = new JsonArray();
        byte[] raw = bytes.toByteArray();
        for (int stage = 0; stage < 4; stage++) {
            int start = 16 + (4 + stage) * SpatialTeacherExport.ROW_BYTES;
            hashes.add(SpatialLabelAudit.inputHash(Arrays.copyOfRange(raw, start, start + SpatialTeacherExport.ROW_BYTES), stage));
        }
        selection.add("inputSha256", hashes); return new Fixture(initial, terminal, metadata, selection, raw);
    }
    private record Script(boolean teacher) implements Bot {
        public String name() { return "synthetic"; }
        public void init(GameState state, int me) {}
        public List<Action> play(GameState state, int me, long budgetMs) {
            if (!teacher || state.turn == 2) return List.of(Action.waitAction());
            return state.turn == 0 ? List.of(Action.place(6,0)) : List.of(Action.place(2,0), Action.place(3,0), Action.place(4,0));
        }
    }
    private static SpatialLabelAudit.Model[] models(AtomicInteger initialClock, AtomicInteger finalClock) {
        float[] initial = new float[SpatialNet.WEIGHT_COUNT]; initial[1728] = 1; initial[13 * 9 + 3] = 10;
        initial[3376] = 1; initial[3408] = -1; initial[3423] = 1;
        float[] last = initial.clone(); last[3408] = 100;
        return new SpatialLabelAudit.Model[]{new SpatialLabelAudit.Model(false, encode(initial), new Params(), () -> { initialClock.incrementAndGet(); return 0L; }),
                new SpatialLabelAudit.Model(false, encode(last), new Params(), () -> { finalClock.incrementAndGet(); return 0L; })};
    }
    private static String encode(float[] weights) {
        ByteBuffer bytes = ByteBuffer.allocate(weights.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float weight : weights) bytes.putFloat(weight); return Base64.getEncoder().encodeToString(bytes.array());
    }
    private static JsonObject identity(int map, int seat) {
        JsonObject id = new JsonObject(); id.addProperty("id", String.format(java.util.Locale.ROOT, "train-%02d-p%d", map, seat));
        id.addProperty("split", "train"); id.addProperty("mapIndex", map); id.addProperty("seed", 12_000_000L + map);
        id.addProperty("mapHash", "00".repeat(32)); id.addProperty("teacherSeat", seat); id.addProperty("opponentSpec", "v01"); return id;
    }
    private static JsonObject syntheticSource() {
        JsonObject source = new JsonObject(); JsonArray games = new JsonArray(); source.add("games", games);
        for (int i = 0; i < 60; i++) { JsonObject game = identity(i / 2, i % 2); game.addProperty("turns", 3); game.addProperty("rowCount", 12); games.add(game); }
        return source;
    }
    private static JsonObject syntheticSelection(JsonObject source) {
        JsonObject selection = new JsonObject(); selection.addProperty("schemaVersion", 1); selection.addProperty("experiment", "spatial-fit-probe-v1");
        selection.addProperty("status", "complete"); selection.addProperty("rootCount", 48); selection.addProperty("rowCount", 192);
        selection.addProperty("initialWeightsSha256", SpatialLabelAudit.V4_SHA); JsonArray roots = new JsonArray(); selection.add("roots", roots);
        for (int i = 0; i < 48; i++) {
            JsonObject root = source.getAsJsonArray("games").get(i).getAsJsonObject().deepCopy(); root.addProperty("turn", 1); root.addProperty("cacheRowStart", i * 12 + 4);
            JsonArray hashes = new JsonArray(); for (int stage = 0; stage < 4; stage++) hashes.add("00".repeat(32)); root.add("inputSha256", hashes); roots.add(root);
        }
        return selection;
    }
}
