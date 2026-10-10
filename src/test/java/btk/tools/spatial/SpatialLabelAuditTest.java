package btk.tools.spatial;

import btk.bot.Bot;
import btk.bot.Params;
import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialObservation;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic actions and explicitly constructed weights only; no new native teacher data. */
class SpatialLabelAuditTest {
    @Test void savedTrajectoryRegeneratesExactBinaryAndUsesActualOwnPrefixesOnBothSeats() throws Exception {
        for (int seat = 0; seat < 2; seat++) {
            GameState initial = board(); initial.maxTurns = 1;
            List<Action> teacher = List.of(Action.place(2,0), Action.place(3,0), Action.place(4,0));
            Fixture fixture = recorded(initial, seat, teacher, List.of(Action.waitAction()));
            SpatialLabelAudit.Model[] models = models(prefixWeights());
            ByteArrayOutputStream regenerated = new ByteArrayOutputStream(); StringWriter text = new StringWriter();
            SpatialTeacherExport.BinaryRows binary = new SpatialTeacherExport.BinaryRows(regenerated);
            try (BufferedWriter writer = new BufferedWriter(text)) {
                assertEquals(1, SpatialLabelAudit.collectGame(initial.copy(), identity(seat), fixture.metadata,
                        models, binary, writer, () -> 0L));
            }
            assertArrayEquals(fixture.bytes, regenerated.toByteArray()); assertEquals(4, binary.rows);
            JsonObject root = JsonParser.parseString(text.toString().trim()).getAsJsonObject();
            assertEquals(seat, root.get("teacherSeat").getAsInt());
            assertEquals(JsonParser.parseString("[2,3,4]"), root.getAsJsonObject("teacher").get("cells"));
            JsonArray rows = root.getAsJsonArray("rows");
            assertEquals(JsonParser.parseString("[2,3,4]"), rows.get(0).getAsJsonObject().get("remainingLabels"));
            assertEquals(JsonParser.parseString("[3,4]"), rows.get(1).getAsJsonObject().get("remainingLabels"));
            assertEquals(JsonParser.parseString("[4]"), rows.get(2).getAsJsonObject().get("remainingLabels"));
            for (String model : new String[]{"v4", "v9"}) {
                JsonObject own = root.getAsJsonObject("ownPlans").getAsJsonObject(model);
                // The own prefix grows 0 -> 1 -> 2. Teacher-prefix next decisions are 0,3,4:
                // stitching those independent predictions would create the wrong student plan.
                assertEquals(JsonParser.parseString("[0,1,2]"), own.get("cells"));
                int[] teacherPrefixGreedy = new int[3];
                for (int stage = 0; stage < 3; stage++) teacherPrefixGreedy[stage] = rows.get(stage).getAsJsonObject()
                        .getAsJsonObject("models").getAsJsonObject(model).get("greedy").getAsInt();
                assertArrayEquals(new int[]{0,3,4}, teacherPrefixGreedy);
                assertFalse(own.get("semanticEqual").getAsBoolean()); assertTrue(own.get("gainedEqual").getAsBoolean());
                assertFalse(own.get("buildSetEqual").getAsBoolean()); assertTrue(own.get("complete").getAsBoolean());
            }
            assertEquals(6, root.getAsJsonArray("permutations").size());
            for (var item : root.getAsJsonArray("permutations")) assertTrue(item.getAsJsonObject().get("semanticEqual").getAsBoolean());
            assertEquals(0, initial.turn); assertEquals(GameState.NO_TRACK, initial.owner[0]);
        }
    }

    @Test void anyRecordedPrestatePlanRowIncomeEventOrTerminalCorruptionFailsClosed() throws Exception {
        GameState initial = board(); initial.maxTurns = 1;
        Fixture fixture = recorded(initial, 0, List.of(Action.place(2,0)), List.of(Action.waitAction()));
        for (String corruption : new String[]{"preStateSha256", "teacherExpanded", "acceptedOpponent", "gained", "scoreAfter",
                "neutralised", "inkedRegions", "terminalAfter", "labels", "prefixes", "paintBefore", "stoppedBefore", "calls", "rowStart"}) {
            JsonObject record = fixture.metadata.getAsJsonArray("turns").get(0).getAsJsonObject().deepCopy();
            switch (corruption) {
                case "preStateSha256" -> record.addProperty(corruption, "00".repeat(32));
                case "teacherExpanded", "acceptedOpponent" -> record.getAsJsonObject(corruption).addProperty("cost", 99);
                case "gained", "scoreAfter" -> record.add(corruption, JsonParser.parseString("[999,0]"));
                case "neutralised", "inkedRegions" -> record.add(corruption, JsonParser.parseString("[999]"));
                case "terminalAfter" -> record.addProperty(corruption, false);
                case "labels" -> record.add(corruption, JsonParser.parseString("[3,600,600,600]"));
                case "prefixes" -> record.add(corruption, JsonParser.parseString("[[],[3],[3],[3]]"));
                case "paintBefore" -> record.add(corruption, JsonParser.parseString("[3,1,1,1]"));
                case "stoppedBefore" -> record.add(corruption, JsonParser.parseString("[true,false,true,true]"));
                case "calls" -> record.getAsJsonArray(corruption).get(0).getAsJsonObject().addProperty("complete", false);
                case "rowStart" -> record.addProperty(corruption, 4);
                default -> fail("Unexpected fixture mutation");
            }
            GameState state = initial.copy(); String before = SpatialLabelAudit.semanticHash(state);
            SpatialLabelAudit.Model[] models = models(prefixWeights());
            for (var model : models) model.bot.init(state.copy(), 0);
            assertThrows(IllegalStateException.class, () -> SpatialLabelAudit.auditRoot(state, 0, "v01", record, models,
                    new SpatialObservation(state), new SpatialTeacherExport.BinaryRows(OutputStream.nullOutputStream()), () -> 0L), corruption);
            assertEquals(before, SpatialLabelAudit.semanticHash(state), corruption);
        }
    }

    @Test void permutationsRetainSameOpponentCollisionAndInkWithMixedCostsAndBothSeats() {
        for (int seat = 0; seat < 2; seat++) {
            GameState root = board(); root.terrain[2] = GameState.RIVER;
            root.instability[1] = 3; root.regionInstability[1] = 3;
            List<Action> teacher = List.of(Action.place(2,0), Action.place(1,0), Action.disrupt(1));
            List<Action> opponent = List.of(Action.place(1,0));
            Pathing pathing = new Pathing(root.size);
            Rules.PlayerMoves expanded = SpatialTeacherExport.validate(root, seat, teacher, pathing);
            GameState reference = root.copy(); Rules.TurnResult next = Rules.resolve(reference,
                    seat == 0 ? teacher : opponent, seat == 0 ? opponent : teacher, pathing);
            assertEquals(List.of(1), next.neutralised); assertEquals(List.of(1), next.inkedRegions);
            assertTrue(reference.inked[1]); assertEquals(GameState.NO_TRACK, reference.owner[1]);
            JsonArray permutations = SpatialLabelAudit.permutations(root, seat, expanded, opponent, reference, next.gained);
            assertEquals(2, permutations.size());
            assertEquals(JsonParser.parseString("[2,1]"), permutations.get(0).getAsJsonObject().get("cells"));
            assertEquals(JsonParser.parseString("[1,2]"), permutations.get(1).getAsJsonObject().get("cells"));
            for (var item : permutations) {
                JsonObject permutation = item.getAsJsonObject();
                assertEquals(3, permutation.get("cost").getAsInt()); assertEquals(1, permutation.get("disrupt").getAsInt());
                assertTrue(permutation.get("semanticEqual").getAsBoolean()); assertTrue(permutation.get("gainedEqual").getAsBoolean());
                assertEquals(SpatialLabelAudit.semanticHash(reference), permutation.get("nextStateSha256").getAsString());
            }
            assertEquals(0, root.turn); assertFalse(root.inked[1]);
        }
    }

    @Test void semanticHashDistinguishesEqualIncomeAndAllDynamicFieldsButIgnoresUnusedPathCapacity() {
        GameState root = board(), a = root.copy(), b = root.copy(); Pathing pathing = new Pathing(root.size);
        Rules.TurnResult ar = Rules.resolve(a, List.of(Action.place(1,0)), List.of(Action.waitAction()), pathing);
        Rules.TurnResult br = Rules.resolve(b, List.of(Action.place(2,0)), List.of(Action.waitAction()), pathing);
        assertArrayEquals(ar.gained, br.gained); assertArrayEquals(a.score, b.score);
        assertNotEquals(SpatialLabelAudit.semanticHash(a), SpatialLabelAudit.semanticHash(b));
        for (int field = 0; field < 7; field++) {
            GameState changed = a.copy();
            switch (field) {
                case 0 -> changed.instability[1]++;
                case 1 -> changed.inked[1] = true;
                case 2 -> changed.regionInstability[1]++;
                case 3 -> changed.regionInked[1] = true;
                case 4 -> changed.score[1]++;
                case 5 -> changed.turn++;
                case 6 -> changed.maxTurns--;
            }
            assertNotEquals(SpatialLabelAudit.semanticHash(a), SpatialLabelAudit.semanticHash(changed));
        }
        GameState connected = new GameState(3, 1, new int[]{0,1,2}, new int[3], new int[]{0,2}, new int[]{0,0}, new int[][]{{1},{}});
        connected.owner[1] = 0; Rules.updateConnections(connected, new Pathing(3));
        assertEquals(3, connected.pairPathLen[0]); String expected = SpatialLabelAudit.semanticHash(connected);
        GameState extended = connected.copy(); extended.pairPath[0] = Arrays.copyOf(extended.pairPath[0], 9);
        extended.pairPath[0][8] = 123456;
        assertEquals(expected, SpatialLabelAudit.semanticHash(extended));
        extended.pairPath[0][1] = 99;
        assertNotEquals(expected, SpatialLabelAudit.semanticHash(extended));
    }

    @Test void remainingSetProbabilityHasStableMaskingAndRejectsIllegalOrDuplicateTargets() {
        float[] logits = {1000,1000,999,5000}; boolean[] mask = {true,true,true,false};
        JsonObject result = SpatialLabelAudit.prediction(logits, mask, 1, new int[]{1,0});
        assertEquals(0, result.get("greedy").getAsInt()); assertEquals(3, result.get("legalCount").getAsInt());
        double one = 1 / (2 + Math.exp(-1));
        assertEquals(one, result.get("labelProbability").getAsDouble(), 1e-12);
        assertEquals(2 * one, result.get("remainingProbability").getAsDouble(), 1e-12);
        assertTrue(result.get("greedyInRemaining").getAsBoolean());
        assertThrows(IllegalStateException.class, () -> SpatialLabelAudit.prediction(logits, mask, 1, new int[]{3}));
        assertThrows(IllegalStateException.class, () -> SpatialLabelAudit.prediction(logits, mask, 1, new int[]{1,1}));
        logits[0] = Float.NaN;
        assertThrows(IllegalStateException.class, () -> SpatialLabelAudit.prediction(logits, mask, 1, new int[]{1}));
    }

    @Test void stoppedTeacherRowsKeepOnlyStopAndInputHashExcludesLabelAndRawTurnOnly() throws Exception {
        GameState root = board(); root.maxTurns = 1;
        Fixture fixture = recorded(root, 0, List.of(Action.waitAction()), List.of(Action.waitAction()));
        SpatialLabelAudit.Model[] models = models(prefixWeights()); for (var model : models) model.bot.init(root.copy(), 0);
        JsonObject result = SpatialLabelAudit.auditRoot(root, 0, "v01", fixture.metadata.getAsJsonArray("turns").get(0).getAsJsonObject(), models,
                new SpatialObservation(root), new SpatialTeacherExport.BinaryRows(OutputStream.nullOutputStream()), () -> 0L);
        assertEquals(1, result.getAsJsonArray("permutations").size());
        for (int stage = 0; stage < 4; stage++) {
            JsonObject row = result.getAsJsonArray("rows").get(stage).getAsJsonObject();
            assertEquals(JsonParser.parseString("[600]"), row.get("remainingLabels"));
            if (stage == 1 || stage == 2) {
                assertTrue(row.get("stopped").getAsBoolean());
                assertEquals(1, row.getAsJsonObject("models").getAsJsonObject("v4").get("legalCount").getAsInt());
            }
        }
        byte[] row = Arrays.copyOfRange(fixture.bytes, 16, 16 + SpatialTeacherExport.ROW_BYTES);
        String hash = SpatialLabelAudit.inputHash(row, 0); ByteBuffer bytes = ByteBuffer.wrap(row).order(ByteOrder.LITTLE_ENDIAN);
        bytes.putInt(row.length - 12, 17); bytes.putInt(row.length - 4, 99);
        assertEquals(hash, SpatialLabelAudit.inputHash(row, 0));
        assertNotEquals(hash, SpatialLabelAudit.inputHash(row, 1));
        row[0] ^= 1; assertNotEquals(hash, SpatialLabelAudit.inputHash(row, 0));
    }

    @Test void interruptedOwnPrefixNeverProducesAComparison() {
        GameState root = board(); Params params = new Params(); params.SPATIAL_RESERVE_MS = 60_000;
        SpatialLabelAudit.Model model = new SpatialLabelAudit.Model(false, encode(prefixWeights()), params, () -> 0L);
        model.bot.init(root, 0); Pathing pathing = new Pathing(root.size);
        Rules.PlayerMoves teacher = SpatialTeacherExport.validate(root, 0, List.of(Action.waitAction()), pathing);
        GameState next = root.copy(); Rules.TurnResult expected = Rules.resolve(next, List.of(Action.waitAction()), List.of(Action.waitAction()), pathing);
        assertThrows(IllegalStateException.class, () -> SpatialLabelAudit.ownPlan(root, 0, model, teacher,
                List.of(Action.waitAction()), next, expected.gained, () -> 0L));
        assertEquals(0, root.turn);
    }

    private record Fixture(JsonObject metadata, byte[] bytes) {}
    private static Fixture recorded(GameState initial, int seat, List<Action> teacher, List<Action> opponent) throws Exception {
        GameState state = initial.copy(); JsonArray turns = new JsonArray(); ByteArrayOutputStream output = new ByteArrayOutputStream();
        SpatialTeacherExport.playGame(state, seat, new Stub(teacher), new Stub(opponent), Integer.MAX_VALUE, () -> 0L,
                new SpatialTeacherExport.BinaryRows(output), turns, new JsonObject(), "v01");
        JsonObject metadata = new JsonObject(); metadata.add("turns", turns); metadata.addProperty("turnCount", state.turn);
        JsonArray score = new JsonArray(); for (int n : state.score) score.add(n); metadata.add("score", score);
        return new Fixture(metadata, output.toByteArray());
    }
    private static JsonObject identity(int seat) {
        JsonObject identity = new JsonObject(); identity.addProperty("id", "synthetic-p" + seat); identity.addProperty("split", "train");
        identity.addProperty("mapIndex", 0); identity.addProperty("seed", 0); identity.addProperty("mapHash", "00".repeat(32));
        identity.addProperty("teacherSeat", seat); identity.addProperty("opponentSpec", "v01"); return identity;
    }
    private static SpatialLabelAudit.Model[] models(float[] weights) {
        return new SpatialLabelAudit.Model[]{new SpatialLabelAudit.Model(false, encode(weights), new Params(), () -> 0L),
                new SpatialLabelAudit.Model(true, encode(weights), new Params(), () -> 0L)};
    }
    private static float[] prefixWeights() {
        float[] weights = new float[SpatialNet.WEIGHT_COUNT];
        weights[1728] = 1; weights[13 * 9 + 3] = 10; weights[3376] = 1; weights[3408] = -1; weights[3423] = 1;
        return weights;
    }
    private static String encode(float[] weights) {
        ByteBuffer bytes = ByteBuffer.allocate(weights.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float weight : weights) bytes.putFloat(weight); return Base64.getEncoder().encodeToString(bytes.array());
    }
    private static GameState board() {
        int[] regions = new int[12]; Arrays.setAll(regions, i -> i);
        return new GameState(6, 2, regions, new int[12], new int[]{0,5}, new int[]{1,1}, new int[][]{{1},{}});
    }
    private record Stub(List<Action> actions) implements Bot {
        public String name() { return "synthetic"; }
        public void init(GameState state, int me) {}
        public List<Action> play(GameState state, int me, long budgetMs) { return actions; }
    }
}
