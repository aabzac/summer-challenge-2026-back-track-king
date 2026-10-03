package btk.submit;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.core.Action;
import btk.core.GameState;
import btk.referee.MapGenerator;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;

class FileMergerTest {
    @TempDir Path temp;

    @Test void characterLimitIsCheckedBeforePublishing() {
        assertDoesNotThrow(() -> FileMerger.checkSourceSize("a".repeat(100_000)));
        assertThrows(IllegalStateException.class, () -> FileMerger.checkSourceSize("a".repeat(100_001)));
        assertThrows(IllegalArgumentException.class, () -> FileMerger.merge("v99"));
        assertThrows(IllegalArgumentException.class, () -> FileMerger.merge("exp-income-guard"));
    }

    @Test void explicitCandidateParametersAreEmbeddedWithoutChangingDefaults() throws Exception {
        String source = FileMerger.merge("current?USE_ROLLOUT_POLICY=0,INTERRUPT_VULN_EVAL=1");
        FileMerger.checkSourceSize(source);
        assertTrue(source.contains("INTERRUPT_VULN_EVAL=true"));
        assertTrue(source.contains("PROFILE_SEARCH=false"));
        assertFalse(new btk.bot.Params().INTERRUPT_VULN_EVAL);
    }

    @Test void jointCandidateFitsAndShipsNoArchive() throws Exception {
        String source = FileMerger.merge("current?USE_ROLLOUT_POLICY=0,USE_REPLY_RERANK=1,BUILD_BEAM_WIDTH=2,OPP_REPLY_WIDTH=1,USE_JOINT_ACTION=1");
        FileMerger.checkSourceSize(source);
        assertTrue(source.contains("USE_JOINT_ACTION=true"));
        assertTrue(source.contains("chooseJointAction"));
        assertFalse(source.contains("class RetainedEngine"));
        assertFalse(source.contains("experiment-engine-v1-classes"));
        assertFalse(new btk.bot.Params().USE_JOINT_ACTION);
    }

    @Test void restoredV05CompilesAndMatchesFrozenDecisions() throws Exception {
        checkFrozenDecisions("v05", false);
    }

    @Test void rolloutSubmissionCompilesMatchesAndWarmsOnlySelectedPolicy() throws Exception {
        String spec = "current?USE_ROLLOUT_POLICY=1";
        String source = FileMerger.merge(spec);
        assertTrue(source.contains("private double rollout("));
        assertFalse(source.contains("chooseCandidateWithReplies"));
        assertFalse(source.contains("class HeuristicBot"));
        assertTrue(FileMerger.merge("current").contains("private double rollout("));
        assertFalse(FileMerger.merge("v05").contains("private double rollout("));
        checkFrozenDecisions(spec, true);
    }

    @Test void quickRouteSubmissionPreservesCandidateParametersAndWarmupDecisions() throws Exception {
        String spec = "current?USE_PROTECTED_OPENING=0,USE_ROLLOUT_QUICK_ROUTE=1,USE_ROLLOUT_NETWORK_AFTER_BASE=0";
        assertTrue(FileMerger.merge(spec).contains("USE_ROLLOUT_QUICK_ROUTE=true"));
        assertTrue(new btk.bot.Params().USE_ROLLOUT_QUICK_ROUTE);
        checkFrozenDecisions(spec, true);
    }

    @Test void mergedV05WarmupIsIsolatedAndUsesTheFrozenStrategy() throws Exception {
        checkFrozenDecisions("v05?ISOLATED_ROUTE_WARMUP=1", true);
    }

    @Test void historicalV06SubmissionPreservesParametersAndIsolatedWarmup() throws Exception {
        String source = FileMerger.merge("v06");
        assertTrue(source.contains("USE_ROLLOUT_POLICY=true"));
        assertTrue(source.contains("ROLLOUT_DEPTH=8"));
        assertTrue(source.contains("USE_ROLLOUT_QUICK_ROUTE=false"));
        assertTrue(source.contains("USE_ROLLOUT_NETWORK_AFTER_BASE=false"));
        assertTrue(source.contains("private double rollout("));
        checkFrozenDecisions("v06", true);
    }

    @Test void frozenV07AndExplicitOpeningDisabledKeepMeasuredParametersAndExactFrozenPolicy() throws Exception {
        var launch = JsonParser.parseString(Files.readString(Path.of("docs/evidence/network-after-base-v1.json")))
                .getAsJsonObject().getAsJsonObject("launch");
        Map<String, String> measured = new TreeMap<>();
        launch.getAsJsonObject("resolvedParameters").getAsJsonObject(launch.get("candidate").getAsString())
                .entrySet().forEach(entry -> measured.put(entry.getKey(), entry.getValue().getAsString()));
        for (String spec : new String[]{"v07", "current?USE_PROTECTED_OPENING=0,USE_ROLLOUT_FILL=0"}) {
            var actual = new TreeMap<>(BotFactory.configuration(spec));
            assertEquals("false", actual.remove("USE_ROLLOUT_ROUTE_DEPENDENCIES"));
            assertEquals("false", actual.remove("USE_ROLLOUT_NETWORK_CHORDS"));
            btk.bot.SearchExperimentParams.removeAndCheck(actual);
            btk.bot.LiveTargetExperimentParams.removeAndCheck(actual);
            assertEquals(measured, actual);
        }
        String current = FileMerger.merge("current?USE_PROTECTED_OPENING=0"), frozen = FileMerger.merge("v07");
        assertTrue(current.contains("USE_ROLLOUT_QUICK_ROUTE=true"));
        assertTrue(current.contains("USE_ROLLOUT_NETWORK_AFTER_BASE=true"));
        assertTrue(current.contains("USE_ROLLOUT_NETWORK=false"));
        assertTrue(frozen.contains("private void searchNetworkAfterBase("));
        assertFalse(frozen.contains("RolloutBotV07"));
        // Current deliberately contains later experiments; preserve the historical
        // frozen policy bytes independently of those additions and of shared Params fields.
        int policyStart = frozen.indexOf("static final class EvalBot implements Bot {");
        int policyEnd = frozen.indexOf("static interface Bot {", policyStart);
        assertTrue(policyStart >= 0 && policyEnd > policyStart);
        assertEquals("b3765f55b30aa34f2aafdc5444c6fbb273f3f43415c45cf93b88c3ae6558759a",
                FileMerger.sha256(frozen.substring(policyStart, policyEnd)));
        assertTrue(current.contains("USE_ROLLOUT_ROUTE_DEPENDENCIES=false"));
        assertTrue(current.contains("USE_ROLLOUT_NETWORK_CHORDS=false"));
        checkFrozenDecisions("current?USE_PROTECTED_OPENING=0", true);
        checkFrozenDecisions("v07", true);
    }

    @Test void selectedAndFrozenV08KeepExactApprovedOpeningConfigurationAndPolicy() throws Exception {
        var launch = JsonParser.parseString(Files.readString(Path.of("docs/evidence/strategy-search-opening-v1.json")))
                .getAsJsonObject().getAsJsonObject("launch");
        Map<String, String> measured = new TreeMap<>();
        launch.getAsJsonObject("resolvedParameters").getAsJsonObject("current?USE_PROTECTED_OPENING=1")
                .entrySet().forEach(entry -> measured.put(entry.getKey(), entry.getValue().getAsString()));
        for (String spec : new String[]{"current?USE_ROLLOUT_FILL=0", "v08"}) {
            var actual = new TreeMap<>(BotFactory.configuration(spec));
            btk.bot.LiveTargetExperimentParams.removeAndCheck(actual);
            assertEquals(measured, actual, "historical v08 measured configuration");
        }
        String current = FileMerger.merge("current");
        assertEquals(current, FileMerger.merge("current?USE_PROTECTED_OPENING=1"));
        assertEquals(current, FileMerger.merge("current?USE_ROLLOUT_FILL=1"));
        assertTrue(current.contains("USE_PROTECTED_OPENING=true"));
        assertTrue(current.contains("USE_ROLLOUT_RHEA=false"));
        assertTrue(current.contains("USE_ROLLOUT_LIVE_TARGET=false"));
        assertTrue(current.contains("ROLLOUT_ALWAYS_DISRUPT=false"));
        FileMerger.checkSourceSize(current);
        String frozen = FileMerger.merge("v08");
        FileMerger.checkSourceSize(frozen);
        assertTrue(frozen.contains("static final class ProtectedOpening"));
        assertFalse(frozen.contains("RolloutBotV08"));
        assertFalse(frozen.contains("ProtectedOpeningV08"));
        // Current deliberately contains later default-off experiments; preserve the selected
        // frozen policy bytes independently of those additions and of shared Params fields.
        assertEquals("7f2fe8385f91a264ba3592620841045964dce334638cfa2e4df40670596661ef",
                FileMerger.sha256(policySection(frozen)));
        checkFrozenDecisions("v08", true, false, true);
        checkFrozenDecisions("current", true, false, true);
    }

    /** Shipped strategy classes between the first policy class and the shared Bot interface. */
    private static String policySection(String merged) {
        int start = merged.indexOf("static final class EvalBot implements Bot {");
        int end = merged.indexOf("static interface Bot {", start);
        assertTrue(start >= 0 && end > start);
        return merged.substring(start, end);
    }

    @Test void selectedSourcePreservesApprovedArtifactExceptTheDefaultFlagAndBuildMarker() throws Exception {
        String approved;
        try (var archive = new ZipFile("docs/evidence/strategy-search-v1-artifacts.zip")) {
            var entry = archive.getEntry("build/cg-boss/artifacts/strategy-search-opening-v1/Player.java");
            assertNotNull(entry, "approved source must remain in durable evidence");
            try (var input = archive.getInputStream(entry)) {
                approved = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        String previousDefault = "public boolean USE_PROTECTED_OPENING = false;\n";
        assertEquals(approved.indexOf(previousDefault), approved.lastIndexOf(previousDefault));
        assertTrue(approved.contains(previousDefault));
        assertTrue(FileMerger.merge("current").contains("public boolean USE_PROTECTED_OPENING = true;\n"));
        // Later default-off Params fields changed the shipped source outside the policy classes;
        // the frozen v08 policy itself must still be the approved bytes apart from its label.
        String frozen = FileMerger.normalizeMarker(FileMerger.merge("v08"))
                .replace("public String name() { return \"v08\"; }",
                        "public String name() { return \"current?\" + p.diff(); }");
        assertEquals(policySection(FileMerger.normalizeMarker(approved)), policySection(frozen),
                "frozen v08 must ship the approved policy bytes");
    }

    @Test void selectedAndFrozenV09ShipTheSameNeverIdlePolicyAndFullConfiguration() throws Exception {
        String current = FileMerger.merge("current"), frozen = FileMerger.merge("v09");
        FileMerger.checkSourceSize(current);
        FileMerger.checkSourceSize(frozen);
        assertTrue(current.contains("USE_ROLLOUT_FILL=true"));
        assertTrue(frozen.contains("USE_ROLLOUT_FILL=true"));
        assertTrue(frozen.contains("private void fill("));
        assertFalse(frozen.contains("searchRhea"));
        assertFalse(frozen.contains("RolloutBotV09"));
        assertFalse(frozen.contains("ProtectedOpeningV09"));
        String normalized = FileMerger.normalizeMarker(frozen)
                .replace("public String name() { return \"v09\"; }",
                        "public String name() { return \"current?\" + p.diff(); }");
        assertEquals(policySection(FileMerger.normalizeMarker(current)), policySection(normalized),
                "frozen v09 must ship the selected policy bytes");
        assertEquals(BotFactory.configuration("current"), BotFactory.configuration("v09"));
        checkFrozenDecisions("v09", true, false, true);
        checkFrozenDecisions("current", true, false, true);
    }

    @Test void frozenV07SubmissionMatchesAnActiveNetworkChoiceIncludingWarmup() throws Exception {
        checkFrozenDecisions("v07?NOISE=0,USE_DISRUPT=0", true, true);
    }

    @Test void independentSearchCandidatesCompileAndKeepTheirExactConfiguration() throws Exception {
        for (String spec : new String[]{"current?USE_PROTECTED_OPENING=0", "current?USE_PROTECTED_OPENING=1"}) {
            String source = FileMerger.merge(spec);
            assertTrue(source.contains("static final class ProtectedOpening"));
            checkFrozenDecisions(spec, true);
        }
        assertFalse(FileMerger.merge("v07").contains("class ProtectedOpening"));
    }

    @Test void artifactAuditRejectsSameConfigurationWithDifferentSource() throws Exception {
        String spec = "current?USE_PROTECTED_OPENING=0", source = FileMerger.merge(spec);
        Files.writeString(temp.resolve("Player.java"), source);
        Files.writeString(temp.resolve("manifest.json"),
                new com.google.gson.Gson().toJson(FileMerger.manifest(spec, source)));
        assertDoesNotThrow(() -> ArtifactCheck.main(new String[]{spec, temp.toString()}));
        Files.writeString(temp.resolve("Player.java"), source + "\n// changed source\n");
        assertThrows(IllegalStateException.class, () -> ArtifactCheck.main(new String[]{spec, temp.toString()}));
    }

    @Test void heuristicSubmissionCompilesMatchesAndWarmsOnlySelectedPolicy() throws Exception {
        String spec = "current?USE_ROLLOUT_POLICY=0,USE_HEURISTIC_POLICY=1,HEURISTIC_EMERGENCY_STOP=0";
        String source = FileMerger.merge(spec);
        assertTrue(source.contains("chooseTarget"));
        assertFalse(source.contains("chooseJointAction"));
        assertFalse(source.contains("evalState"));
        assertFalse(source.contains("new HeuristicBot"));
        assertFalse(source.contains("private double rollout("));
        checkFrozenDecisions(spec, true);
    }

    private void checkFrozenDecisions(String spec, boolean warmup) throws Exception {
        checkFrozenDecisions(spec, warmup, false);
    }

    private void checkFrozenDecisions(String spec, boolean warmup, boolean networkFixture) throws Exception {
        checkFrozenDecisions(spec, warmup, networkFixture, false);
    }

    private void checkFrozenDecisions(String spec, boolean warmup, boolean networkFixture,
                                      boolean openingFixture) throws Exception {
        String source = FileMerger.merge(spec);
        FileMerger.checkSourceSize(source);
        if (!spec.contains("USE_DEFENSE_PRIORITY=1") && !spec.contains("USE_OWN_BUILD_PLAN=1")) {
            assertFalse(source.contains("chooseCandidateWithReplies"));
        }
        assertFalse(source.contains("btk.bot.v05"));
        assertFalse(source.contains("btk.bot.v06"));
        assertFalse(source.contains("btk.bot.v07"));
        assertFalse(source.contains("btk.bot.v08"));
        assertFalse(source.contains("btk.bot.v09"));
        assertFalse(source.contains("class PressureBot"));
        assertFalse(source.contains("class BenchPool"));
        assertFalse(source.contains("class BenchmarkSuite"));
        assertFalse(source.contains("class BotCatalog"));
        assertFalse(source.contains("class ReplyProbe"));
        assertFalse(source.contains("class ReplyStudy"));
        assertFalse(source.contains("class JointReplyStudy"));
        assertFalse(source.contains("class JointActionCheck"));
        assertFalse(source.contains("class RetainedEngine"));
        assertFalse(source.contains("experiment-engine-v1"));
        Path file = temp.resolve("Player.java");
        Files.writeString(file, source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-encoding", "UTF-8", "-d", temp.toString(), file.toString()));
        try (var loader = new URLClassLoader(new java.net.URL[]{temp.toUri().toURL()},
                ClassLoader.getPlatformClassLoader())) {
            Class<?> stateClass = loader.loadClass("Player$GameState");
            var ctor = stateClass.getDeclaredConstructor(int.class, int.class, int[].class, int[].class,
                    int[].class, int[].class, int[][].class);
            ctor.setAccessible(true);
            Class<?> mainClass = loader.loadClass("Player$MainBot");
            var mainCtor = mainClass.getDeclaredConstructor(); mainCtor.setAccessible(true);
            var init = mainClass.getMethod("init", stateClass, int.class); init.setAccessible(true);
            var play = mainClass.getMethod("play", stateClass, int.class, long.class); play.setAccessible(true);
            Class<?> actionClass = loader.loadClass("Player$Action");
            var format = actionClass.getMethod("format", java.util.List.class); format.setAccessible(true);
            for (int me = 0; me < 2; me++) {
                GameState s = MapGenerator.generate(1000);
                if (openingFixture) {
                    s = new GameState(9, 1, new int[]{0, 0, 1, 2, 2, 2, 3, 4, 4}, new int[9],
                            new int[]{0, 4, 8}, new int[3], new int[][]{{1, 2}, {2}, {}});
                }
                if (networkFixture) {
                    int[] regions = new int[63];
                    for (int cell = 0; cell < regions.length; cell++) regions[cell] = cell;
                    s = new GameState(9, 7, regions, new int[63],
                            new int[]{1, 7}, new int[]{3, 3}, new int[][]{{1}, {}});
                    for (int cell : new int[]{29, 30, 21, 22, 23, 32, 33}) s.owner[cell] = 1 - me;
                    s.turn = 10;
                    btk.core.Rules.updateConnections(s, new btk.core.Pathing(s.size));
                }
                if (spec.contains("USE_HEURISTIC_DETOUR=1")) {
                    int[] regions = new int[15];
                    for (int c = 0; c < regions.length; c++) regions[c] = c;
                    s = new GameState(5, 3, regions, new int[15], new int[]{0, 4}, new int[]{1, 1}, new int[][]{{1}, {0}});
                    for (int x = 1; x < 4; x++) s.owner[s.cell(x, 1)] = 1 - me;
                    btk.core.Rules.updateConnections(s, new btk.core.Pathing(s.size));
                }
                if (spec.contains("USE_DEFENSE_PRIORITY=1")) {
                    s = new GameState(5,3,new int[]{3,3,3,3,3,0,1,1,1,2,4,4,4,4,4},new int[15],
                            new int[]{0,4},new int[]{1,1},new int[][]{{1},{0}});
                    for(int x=1;x<4;x++) s.owner[s.cell(x,1)] = me;
                    btk.core.Rules.disrupt(s,1); btk.core.Rules.disrupt(s,1);
                    btk.core.Rules.updateConnections(s,new btk.core.Pathing(s.size));
                }
                Object generatedState = ctor.newInstance(s.width, s.height, s.regionId, s.terrain,
                        s.townX, s.townY, s.desired);
                for (String field : new String[]{"turn", "maxTurns"}) {
                    var generatedField = stateClass.getField(field); generatedField.setAccessible(true);
                    generatedField.setInt(generatedState, field.equals("turn") ? s.turn : s.maxTurns);
                }
                var owners = stateClass.getField("owner"); owners.setAccessible(true);
                System.arraycopy(s.owner, 0, owners.get(generatedState), 0, s.size);
                for(String field : new String[]{"instability", "regionInstability"}) {
                    var f = stateClass.getField(field); f.setAccessible(true);
                    int[] values = field.equals("instability") ? s.instability : s.regionInstability;
                    System.arraycopy(values,0,f.get(generatedState),0,values.length);
                }
                Class<?> pathClass = loader.loadClass("Player$Pathing");
                var pathCtor = pathClass.getDeclaredConstructor(int.class); pathCtor.setAccessible(true);
                var update = loader.loadClass("Player$Rules").getMethod("updateConnections", stateClass, pathClass);
                update.setAccessible(true); update.invoke(null, generatedState, pathCtor.newInstance(s.size));
                Object generatedBot = mainCtor.newInstance();
                var paramsField = mainClass.getDeclaredField("params"); paramsField.setAccessible(true);
                Object generatedParams = paramsField.get(generatedBot);
                Map<String, String> embedded = new TreeMap<>();
                for (var field : generatedParams.getClass().getFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers())) continue;
                    field.setAccessible(true); embedded.put(field.getName(), String.valueOf(field.get(generatedParams)));
                }
                assertEquals(BotFactory.configuration(spec), embedded, "merged full effective parameters");
                init.invoke(generatedBot, generatedState, me);
                if (warmup) {
                    var warm = mainClass.getMethod("warmup", stateClass, int.class, long.class);
                    warm.setAccessible(true); warm.invoke(generatedBot, generatedState, me, 150L);
                    var calls = mainClass.getField("warmupCalls"); calls.setAccessible(true);
                    assertTrue(calls.getInt(generatedBot) > 0);
                }
                Bot frozen = BotFactory.create(spec); frozen.init(s, me);
                var expectedActions = frozen.play(s.copy(), me, 60_000);
                String expected = Action.format(expectedActions);
                if (openingFixture) {
                    assertEquals(3, expectedActions.stream().filter(a -> a.type == Action.Type.PLACE).count());
                    for (var action : expectedActions) if (action.type == Action.Type.PLACE)
                        assertTrue(s.regionHasTown(s.regionId[s.cell(action.x, action.y)]));
                    assertTrue(frozen.getClass().getField("selectedOpening").getBoolean(frozen),
                            "fixture must exercise the protected opening in the packaged policy");
                }
                if (networkFixture) assertEquals("PLACE_TRACKS 4 3", expected);
                assertEquals(expected,
                        format.invoke(null, play.invoke(generatedBot, generatedState, me, 60_000L)));
            }
        }
    }

    @Test void neuralSubmissionShipsOnlyTheImitationPolicyAndMatchesDecisions() throws Exception {
        String source = FileMerger.merge("neural-v1");
        assertTrue(source.contains("static final class NeuralFeatures"));
        assertTrue(source.contains("static final class NeuralNet"));
        assertTrue(source.contains("static final class NeuralWeights"));
        assertFalse(source.contains("NeuralBot"));
        assertFalse(source.contains("private double rollout("));
        assertFalse(source.contains("class RolloutBot"));
        assertFalse(source.contains("class HeuristicBot"));
        assertFalse(source.contains("class ProtectedOpening"));
        assertTrue(source.contains("NOISE=0.1"));
        checkFrozenDecisions("neural-v1", true);
        assertFalse(FileMerger.merge("current").contains("NeuralFeatures"));
        assertThrows(IllegalArgumentException.class, () -> FileMerger.merge("neural-v3"));
    }

    @Test void neuralV2SubmissionIsIsolatedAndMatchesCompiledPolicyDecisions() throws Exception {
        String source = FileMerger.merge("neural-v2");
        assertTrue(source.contains("static final class NeuralFeatures"));
        assertTrue(source.contains("static final class NeuralNet"));
        assertTrue(source.contains("static final class NeuralV2Weights"));
        assertFalse(source.contains("static final class NeuralWeights"));
        assertFalse(source.contains("NeuralV2Bot"));
        assertFalse(source.contains("class NeuralBot"));
        assertFalse(source.contains("class NeuralV2Collect"));
        assertFalse(source.contains("private double rollout("));
        assertFalse(source.contains("class HeuristicBot"));
        assertFalse(source.contains("class ProtectedOpening"));
        FileMerger.checkSourceSize(source);
        checkFrozenDecisions("neural-v2", true);
        assertFalse(FileMerger.merge("neural-v1").contains("NeuralV2"));
        assertFalse(FileMerger.merge("current").contains("NeuralV2"));
    }

    @Test void indrillSubmissionShipsOnlyTheActionValuePolicyAndMatchesDecisions() throws Exception {
        String source = FileMerger.merge("indrill-q-v1");
        assertTrue(source.contains("static final class IndrillFeatures"));
        assertTrue(source.contains("static final class IndrillNet"));
        assertTrue(source.contains("static final class IndrillWeights"));
        assertFalse(source.contains("IndrillBot"));
        assertFalse(source.contains("NeuralFeatures"));
        assertFalse(source.contains("private double rollout("));
        assertFalse(source.contains("class HeuristicBot"));
        assertFalse(source.contains("class ProtectedOpening"));
        assertFalse(source.contains("class IndrillCollect"));
        assertTrue(source.contains("static final class TacticalPathing"));
        assertFalse(source.contains("class InkProjection"));
        assertTrue(source.contains("INDRILL_INK_PENALTY=2"));
        checkFrozenDecisions("indrill-q-v1", true);
        assertFalse(FileMerger.merge("current").contains("IndrillFeatures"));
        assertFalse(FileMerger.merge("current").contains("class TacticalPathing"));
        assertFalse(FileMerger.merge("v09").contains("class InkProjection"));
        assertThrows(IllegalArgumentException.class, () -> FileMerger.merge("indrill-q-v3"));
    }

    @Test void detourSubmissionMatchesOnActiveRoutesIncludingItsWarmup() throws Exception {
        checkFrozenDecisions("current?USE_ROLLOUT_POLICY=0,USE_HEURISTIC_POLICY=1,USE_HEURISTIC_DETOUR=1,HEURISTIC_EMERGENCY_STOP=0", true);
    }
    @Test void v2SubmissionKeepsItsBaselineAndPreservesFrozenV1Artifact() throws Exception {
        String source = FileMerger.merge("indrill-q-v2");
        assertTrue(source.contains("static final class IndrillV2Net"));
        assertTrue(source.contains("static final class IndrillV2Weights"));
        assertTrue(source.contains("public float stateValue"));
        assertFalse(source.contains("IndrillV2Bot"));
        assertFalse(source.contains("static final class IndrillNet"));
        assertFalse(source.contains("static final class IndrillWeights"));
        assertFalse(source.contains("class InkProjection"));
        FileMerger.checkSourceSize(source);
        checkFrozenDecisions("indrill-q-v2", true);
        assertEquals("c186d548aef00b8a6f777f7a9e50742b93a8ecf5aadb9e68c4932c9e4aebb8db",
                FileMerger.sha256(FileMerger.merge("indrill-q-v1")));
    }
    @Test void defenseSubmissionMatchesOnDamagedActiveRouteIncludingWarmup() throws Exception {
        checkFrozenDecisions("current?USE_ROLLOUT_POLICY=0,USE_DEFENSE_PRIORITY=1",true);
    }
    @Test void ownPlanSubmissionMatchesIncludingWarmup() throws Exception {
        checkFrozenDecisions("current?USE_ROLLOUT_POLICY=0,USE_OWN_BUILD_PLAN=1", true);
    }
}
