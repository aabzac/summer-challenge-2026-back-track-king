package btk.submit;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.neural.SpatialV10Weights;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Rules;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SpatialV10SubmissionTest {
    @TempDir Path directory;

    @Test void onlyV10PayloadShipsInTheUnchangedGreedyRuntimeAndCompiledDecisionsMatch() throws Exception {
        String spec = "spatial-ppo-v10", source = FileMerger.merge(spec); FileMerger.checkSourceSize(source);
        var declaration = java.util.regex.Pattern.compile("String DATA\\s*=\\s*([^;]+);").matcher(source);
        assertTrue(declaration.find()); var chunks = java.util.regex.Pattern.compile("\"([A-Za-z0-9+/=]+)\"").matcher(declaration.group(1));
        StringBuilder payload = new StringBuilder(); while (chunks.find()) payload.append(chunks.group(1));
        assertEquals(SpatialV10Weights.DATA, payload.toString()); assertFalse(declaration.find());
        assertTrue(source.contains("return \"spatial-ppo-v10\";"));
        for (String omitted : new String[]{"SpatialV10Bot", "SpatialV10Weights", "SpatialV9Net", "SpatialV9Weights", "SpatialV4Weights",
                "SpatialVariantBot", "SpatialBridge", "BotFactory", "BotCatalog", "USE_SPATIAL_V9_TOWN_CONTEXT", "USE_SPATIAL_V8_SEARCH"})
            assertFalse(source.contains(omitted), omitted);
        Path file = directory.resolve("Player.java"); Files.writeString(file, source);
        assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, "-encoding", "UTF-8", "-d", directory.toString(), file.toString()));
        try (URLClassLoader loader = new URLClassLoader(new java.net.URL[]{directory.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            Class<?> stateClass = loader.loadClass("Player$GameState"), mainClass = loader.loadClass("Player$MainBot");
            var constructor = stateClass.getDeclaredConstructor(int.class,int.class,int[].class,int[].class,int[].class,int[].class,int[][].class);
            constructor.setAccessible(true); var mainConstructor = mainClass.getDeclaredConstructor(); mainConstructor.setAccessible(true);
            var init = mainClass.getMethod("init",stateClass,int.class); init.setAccessible(true);
            var play = mainClass.getMethod("play",stateClass,int.class,long.class); play.setAccessible(true);
            var warm = mainClass.getMethod("warmup",stateClass,int.class,long.class); warm.setAccessible(true);
            var format = loader.loadClass("Player$Action").getMethod("format",java.util.List.class); format.setAccessible(true);
            Class<?> pathClass = loader.loadClass("Player$Pathing"); var pathConstructor = pathClass.getDeclaredConstructor(int.class); pathConstructor.setAccessible(true);
            var update = loader.loadClass("Player$Rules").getMethod("updateConnections",stateClass,pathClass); update.setAccessible(true);
            for (int seat = 0; seat < 2; seat++) for (int turn : new int[]{0,37}) {
                int[] regions = new int[12]; for (int i = 0; i < regions.length; i++) regions[i] = i;
                GameState state = new GameState(6,2,regions,new int[12],new int[]{0,5},new int[]{1,1},new int[][]{{1},{0}});
                state.turn = turn; state.terrain[2] = GameState.RIVER;
                if (turn > 0) { state.owner[1] = 0; state.owner[2] = 1; Rules.disrupt(state,3); }
                Rules.updateConnections(state,new Pathing(state.size));
                Object generatedState = constructor.newInstance(state.width,state.height,state.regionId,state.terrain,state.townX,state.townY,state.desired);
                for (String field : new String[]{"turn","maxTurns"}) { var f = stateClass.getField(field); f.setAccessible(true); f.setInt(generatedState,field.equals("turn") ? state.turn : state.maxTurns); }
                for (String field : new String[]{"owner","instability","regionInstability","score","inked","regionInked"}) {
                    var target = stateClass.getField(field); target.setAccessible(true); Object values = GameState.class.getField(field).get(state);
                    System.arraycopy(values,0,target.get(generatedState),0,java.lang.reflect.Array.getLength(values));
                }
                update.invoke(null,generatedState,pathConstructor.newInstance(state.size)); Object generated = mainConstructor.newInstance();
                var params = mainClass.getDeclaredField("params"); params.setAccessible(true); Object embedded = params.get(generated);
                Map<String,String> configuration = new TreeMap<>();
                for (var f : embedded.getClass().getFields()) if (!java.lang.reflect.Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true); configuration.put(f.getName(),String.valueOf(f.get(embedded)));
                }
                assertEquals(BotFactory.configuration(spec),configuration);
                init.invoke(generated,generatedState,seat); warm.invoke(generated,generatedState,seat,2L);
                Bot local = BotFactory.create(spec); local.init(state,seat);
                assertEquals(Action.format(local.play(state.copy(),seat,60_000)),format.invoke(null,play.invoke(generated,generatedState,seat,60_000L)));
                assertEquals("WAIT",format.invoke(null,play.invoke(generated,generatedState,seat,2L)));
            }
        }
    }

    @Test void registrationPreservesHistoricalSpatialAndSelectedArtifactBytes() throws Exception {
        Map<String,String> hashes = Map.ofEntries(
                Map.entry("spatial-ppo-v1","d9507cb3e0d68de4f526cfa9da06a2b1c909182e9992f4b7a3bb6cab3d24d6d7"),
                Map.entry("spatial-ppo-v2","a3d0fdb7e587478fc4b6f63cd64bb6f607c34b64384cd41cc091bcec375107a1"),
                Map.entry("spatial-ppo-v3","2e873833da40b7b73c585016fc8fe0039b77d256fe60445f60eaf1b4c967dfc7"),
                Map.entry("spatial-ppo-v4","a961638dca644f880a12224f4e385fcfbb9efbe52d5a3744e2a0c2267276bdb2"),
                Map.entry("spatial-ppo-v5","f5793756bf20c48af7302418b70b8fe92499b3c3eaefd3291999f1b4160550c2"),
                Map.entry("spatial-ppo-v6","4019ea1ab9c0e95081d5a56bcd27eff3a6b2ba9c87dbec2134b66ca94dd6d9e6"),
                Map.entry("spatial-ppo-v7","32235e07e7582a0ed9929c2c7e6bb53a1a9cdeed54870cf949dd884f159bae74"),
                Map.entry("spatial-ppo-v8","3a477686dd9606029da960d2d38380da30ebffb22307e0a41af3abaa5572aaff"),
                Map.entry("spatial-ppo-v9","da8b3bb160ea90d0e20076060339f5e33584399906c82ea5523e453920fb2a76"),
                Map.entry(btk.bot.LiveTargetExperimentParams.V09_ON_CURRENT,"bfe4dd9b3e00fbee6c386411c486621cfb2b0f72b78b2e479a82659b0b3c1e78"),
                Map.entry("v09","aca0be459116e78b97400ba0a0abfaa151aca7cc97db7804b8466e8cda788fac"));
        for (var entry : hashes.entrySet()) assertEquals(entry.getValue(),FileMerger.sha256(FileMerger.merge(entry.getKey())),entry.getKey());
    }
}
