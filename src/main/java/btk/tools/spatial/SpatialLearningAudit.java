package btk.tools.spatial;

import btk.bot.Bot;
import btk.bot.BotFactory;
import btk.bot.neural.SpatialBot;
import btk.bot.neural.SpatialInitialWeights;
import btk.bot.neural.SpatialNet;
import btk.bot.neural.SpatialObservation;
import btk.bot.neural.SpatialV2Weights;
import btk.bot.neural.SpatialV3Bot;
import btk.bot.neural.SpatialV3Weights;
import btk.bot.neural.SpatialV4Bot;
import btk.bot.neural.SpatialV4Weights;
import btk.bot.neural.SpatialV5Weights;
import btk.bot.neural.SpatialVariantBot;
import btk.bot.v09.RolloutBotV09;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.referee.MapGenerator;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

/** Fixed matched-policy trajectory diagnostic, never a candidate strength gate or trainer. */
public final class SpatialLearningAudit {
    static final long BUDGET_MS = 60_000;
    static final List<String> LEARNERS = List.of("spatial-ppo-v2-init", "spatial-ppo-v2", "spatial-ppo-v3", "spatial-ppo-v4");
    static final List<String> OPPONENTS = List.of("v01", "v09");
    private static final Gson JSON = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    record GameSpec(int mapIndex, long seed, String learnerSpec, String opponentSpec, int learnerSeat) {
        String id() { return "map" + mapIndex + "-" + opponentSpec + "-" + learnerSpec + "-p" + learnerSeat; }
    }
    private SpatialLearningAudit() {}

    static List<GameSpec> schedule() {
        List<GameSpec> result = new ArrayList<>();
        for (int map = 0; map < 4; map++) for (String opponent : OPPONENTS)
            for (int seat = 0; seat < 2; seat++) for (String learner : LEARNERS)
                result.add(new GameSpec(map, 22_000_000L + map, learner, opponent, seat));
        return List.copyOf(result);
    }

    /** Arguments: fresh-output-directory exclusions.json (nonempty canonical mapHashes array). */
    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("verify")) { verify(Path.of(args[1])); return; }
        if (args.length != 2) throw new IllegalArgumentException("SpatialLearningAudit fresh-output-directory exclusions.json");
        Path out = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(out.getParent()); Files.createDirectory(out);
        JsonObject manifest = new JsonObject(); JsonArray maps = new JsonArray(), games = new JsonArray();
        manifest.addProperty("schemaVersion", 1); manifest.addProperty("experiment", "spatial-learning-audit");
        manifest.addProperty("status", "collecting"); manifest.addProperty("createdAt", Instant.now().toString());
        manifest.addProperty("budgetMs", BUDGET_MS); manifest.addProperty("warmup", false);
        manifest.addProperty("scheduledGames", 64); manifest.addProperty("totalTurns", 0);
        manifest.addProperty("javaVersion", System.getProperty("java.version"));
        manifest.addProperty("role", "Matched frozen-policy learning/value diagnostic; no strength gate, training or promotion");
        manifest.addProperty("valueDefinitions", "Preturn stage0: learnerValueRaw is acting model value (v4 uncalibrated); v5ValueRaw is frozen teacher-continuation critic. PPO and v5 targets have different units and policies.");
        manifest.addProperty("exclusionManifest", Path.of(args[1]).toAbsolutePath().normalize().toString());
        manifest.add("maps", maps); manifest.add("games", games);
        writeNew(out.resolve("intent.json"), manifest);
        try {
            byte[] exclusionBytes = Files.readAllBytes(Path.of(args[1]));
            Set<String> excluded = exclusions(JsonParser.parseString(new String(exclusionBytes, StandardCharsets.UTF_8)).getAsJsonObject());
            manifest.addProperty("exclusionsSha256", sha(exclusionBytes)); manifest.addProperty("excludedMapCount", excluded.size());
            JsonObject params = new JsonObject(), weights = new JsonObject();
            for (String learner : LEARNERS) {
                params.add(learner, JSON.toJsonTree(BotFactory.configuration(learner)));
                weights.addProperty(learner, sha(Base64.getDecoder().decode(payload(learner))));
            }
            for (String opponent : OPPONENTS) params.add(opponent, JSON.toJsonTree(BotFactory.configuration(opponent)));
            weights.addProperty("spatial-ppo-v5", sha(Base64.getDecoder().decode(SpatialV5Weights.DATA)));
            manifest.add("params", params); manifest.add("weightsSha256", weights);
            List<GameState> initial = new ArrayList<>(); Set<String> used = new HashSet<>();
            // Validate every scheduled map before constructing or calling any playing bot.
            for (int map = 0; map < 4; map++) {
                long seed = 22_000_000L + map; GameState state = MapGenerator.generate(seed);
                String init = Protocol.initBlock(state, 0), hash = sha(init.getBytes(StandardCharsets.UTF_8));
                requireFresh(hash, excluded, used);
                JsonObject entry = new JsonObject(); entry.addProperty("mapIndex", map); entry.addProperty("seed", seed);
                entry.addProperty("mapHash", hash); entry.addProperty("initBlockP0", init); maps.add(entry); initial.add(state);
            }
            manifest.addProperty("mapPreflightComplete", true);
            writeNew(out.resolve("preflight.json"), manifest);
            int totalTurns = 0;
            for (GameSpec spec : schedule()) {
                JsonObject game = identity(spec, maps.get(spec.mapIndex).getAsJsonObject());
                game.addProperty("maxTurns", initial.get(spec.mapIndex).maxTurns);
                game.addProperty("budgetMs", BUDGET_MS); game.addProperty("warmup", false);
                game.add("learnerParams", params.get(spec.learnerSpec)); game.add("opponentParams", params.get(spec.opponentSpec));
                game.addProperty("learnerWeightsSha256", weights.get(spec.learnerSpec).getAsString());
                game.addProperty("v5WeightsSha256", weights.get("spatial-ppo-v5").getAsString());
                String file = spec.id() + ".json"; JsonObject index = identity(spec, maps.get(spec.mapIndex).getAsJsonObject());
                index.remove("initBlockP0"); index.addProperty("file", file); games.add(index);
                GameState state = initial.get(spec.mapIndex).copy(); Exception failure = null;
                game.addProperty("status", "collecting"); game.add("turns", new JsonArray());
                try {
                    playGame(state, spec.learnerSpec, spec.opponentSpec, spec.learnerSeat,
                            BotFactory.create(spec.learnerSpec), BotFactory.create(spec.opponentSpec),
                            new SpatialNet(payload(spec.learnerSpec)), new SpatialNet(SpatialV5Weights.DATA), System::nanoTime, game);
                } catch (Exception ex) { failure = ex; }
                boolean terminal = Rules.gameOver(state, new Pathing(state.size));
                String status = failure == null && terminal ? "complete" : "failed";
                game.addProperty("status", status); game.addProperty("terminal", terminal);
                game.addProperty("turnCount", state.turn); game.add("score", JSON.toJsonTree(state.score));
                game.addProperty("finalStateSha256", stateHash(state));
                game.addProperty("failure", failure == null ? null : failure.toString());
                writeNew(out.resolve(file), game);
                index.addProperty("sha256", sha(Files.readAllBytes(out.resolve(file))));
                index.addProperty("status", status); index.addProperty("turnCount", state.turn);
                index.addProperty("terminal", terminal); index.add("score", JSON.toJsonTree(state.score));
                totalTurns += state.turn; manifest.addProperty("totalTurns", totalTurns);
                if (failure != null) throw failure;
                require(terminal, "Scheduled game ended without actual terminal");
                System.out.println(spec.id() + " turns=" + state.turn + " score=" + Arrays.toString(state.score));
            }
            require(games.size() == 64, "Incomplete fixed schedule");
            manifest.addProperty("status", "complete"); manifest.addProperty("completedAt", Instant.now().toString());
            writeNew(out.resolve("manifest.json"), manifest);
        } catch (Exception failure) {
            manifest.addProperty("status", "failed"); manifest.addProperty("failure", failure.toString());
            manifest.addProperty("failedAt", Instant.now().toString()); writeNew(out.resolve("manifest.json"), manifest); throw failure;
        }
    }

    /** Test fixtures inject synthetic bots and networks; production uses the fixed BotFactory schedule. */
    static void playGame(GameState state, String learnerSpec, String opponentSpec, int learnerSeat,
                         Bot learner, Bot opponent, SpatialNet learnerNet, SpatialNet criticNet,
                         LongSupplier clock, JsonObject game) throws Exception {
        require(state.turn == 0 && (learnerSeat == 0 || learnerSeat == 1), "Expected a fresh game and valid seat");
        Pathing pathing = new Pathing(state.size); Rules.updateConnections(state, pathing);
        SpatialObservation observation = new SpatialObservation(state);
        Bot[] bots = learnerSeat == 0 ? new Bot[]{learner, opponent} : new Bot[]{opponent, learner};
        SpatialBot spatial = spatialDelegate(learner);
        JsonArray turns = game.getAsJsonArray("turns");
        if (turns == null) { turns = new JsonArray(); game.add("turns", turns); }
        for (int seat = 0; seat < 2; seat++) bots[seat].init(state.copy(), seat);
        while (!Rules.gameOver(state, pathing)) {
            require(state.turn < state.maxTurns, "Core terminal/turn limit inconsistency");
            JsonObject row = new JsonObject(); turns.add(row);
            String before = stateHash(state); row.addProperty("turn", state.turn); row.addProperty("preStateSha256", before);
            row.addProperty("preTurnBlockP0", Protocol.turnBlock(state, 0)); row.addProperty("preTurnBlockP1", Protocol.turnBlock(state, 1));
            row.add("scoreBefore", JSON.toJsonTree(state.score)); row.add("incomeBefore", JSON.toJsonTree(income(state)));
            row.add("metricsBefore", metrics(state));
            long diagnosticStart = clock.getAsLong();
            observation.prepare(state, learnerSeat); observation.prefix(null, 0, GameState.PAINT_PER_TURN, 0, false);
            learnerNet.forward(observation); criticNet.forward(observation);
            require(Float.isFinite(learnerNet.value) && Float.isFinite(criticNet.value), "Nonfinite preturn value");
            row.addProperty("learnerValueRaw", learnerNet.value); row.addProperty("v5ValueRaw", criticNet.value);
            row.addProperty("diagnosticElapsedMs", (clock.getAsLong() - diagnosticStart) / 1e6);
            JsonArray calls = new JsonArray(), commands = new JsonArray(), expanded = new JsonArray();
            row.add("calls", calls); row.add("actions", commands); row.add("expanded", expanded);
            List<List<Action>> actions = new ArrayList<>();
            for (int seat = 0; seat < 2; seat++) {
                JsonObject call = new JsonObject(); calls.add(call);
                String spec = seat == learnerSeat ? learnerSpec : opponentSpec;
                List<Action> selected = call(bots[seat], seat == learnerSeat ? spatial : null, state, seat,
                        spec, seat == learnerSeat ? "learner" : "opponent", clock, call);
                actions.add(selected); commands.add(Action.format(selected)); expanded.add(plan(state, validate(state, seat, selected, pathing)));
            }
            require(before.equals(stateHash(state)), "Policy/value inspection mutated the shared preturn state");
            int cost = expanded.get(learnerSeat).getAsJsonObject().get("cost").getAsInt();
            row.addProperty("learnerUnusedPaint", GameState.PAINT_PER_TURN - cost);
            row.addProperty("learnerStoppedWithPaint", cost < GameState.PAINT_PER_TURN);
            Rules.TurnResult result = Rules.resolve(state, actions.get(0), actions.get(1), pathing);
            JsonArray accepted = new JsonArray(); row.add("accepted", accepted);
            for (int seat = 0; seat < 2; seat++) {
                require(!result.hasError(seat) && result.moves[seat].skipped.isEmpty() && !result.unknownActions, "Core rejected played actions");
                JsonObject actual = plan(state, result.moves[seat]); accepted.add(actual);
                require(actual.equals(expanded.get(seat)), "Expanded/accepted core plan mismatch");
            }
            row.add("gained", JSON.toJsonTree(result.gained)); row.add("scoreAfter", JSON.toJsonTree(state.score));
            row.add("incomeAfter", JSON.toJsonTree(income(state))); row.add("metricsAfter", metrics(state));
            row.add("neutralised", JSON.toJsonTree(result.neutralised)); row.add("inkedRegions", JSON.toJsonTree(result.inkedRegions));
            row.addProperty("terminalAfter", Rules.gameOver(state, pathing)); row.addProperty("postStateSha256", stateHash(state));
        }
    }

    private static List<Action> call(Bot bot, SpatialBot spatial, GameState state, int seat,
                                    String spec, String role, LongSupplier clock, JsonObject row) {
        row.addProperty("me", seat); row.addProperty("spec", spec); row.addProperty("role", role);
        row.addProperty("budgetMs", BUDGET_MS); row.addProperty("complete", false); row.addProperty("legal", false);
        long start = clock.getAsLong();
        try {
            List<Action> actions = List.copyOf(bot.play(state.copy(), seat, BUDGET_MS));
            double ms = (clock.getAsLong() - start) / 1e6; row.addProperty("elapsedMs", ms);
            row.addProperty("actions", Action.format(actions));
            validate(state, seat, actions, new Pathing(state.size)); row.addProperty("legal", true);
            boolean complete = true;
            if (spatial != null) {
                complete = !spatial.lastInterrupted; row.addProperty("interrupted", spatial.lastInterrupted);
                row.addProperty("completedDecisions", spatial.lastCompletedDecisions);
            } else if (bot instanceof RolloutBotV09 v09) {
                complete = v09.ordinarySearchComplete || v09.selectedOpening;
                row.addProperty("ordinarySearchComplete", v09.ordinarySearchComplete); row.addProperty("selectedOpening", v09.selectedOpening);
            } else row.addProperty("completionEvidence", "finite greedy/stub algorithm without timer exits");
            require(complete && ms < BUDGET_MS, "Incomplete/over-budget policy: " + spec);
            row.addProperty("complete", true); return actions;
        } catch (RuntimeException failure) {
            row.addProperty("failure", failure.toString()); throw failure;
        } finally {
            if (!row.has("elapsedMs")) row.addProperty("elapsedMs", (clock.getAsLong() - start) / 1e6);
        }
    }

    /** Read existing delegate diagnostics without modifying any historical wrapper or policy. */
    static SpatialBot spatialDelegate(Bot bot) throws ReflectiveOperationException {
        if (bot instanceof SpatialBot spatial) return spatial;
        if (bot instanceof SpatialVariantBot || bot instanceof SpatialV3Bot || bot instanceof SpatialV4Bot) {
            Field delegate = bot.getClass().getDeclaredField("delegate"); delegate.setAccessible(true);
            return (SpatialBot) delegate.get(bot);
        }
        return null;
    }

    static Rules.PlayerMoves validate(GameState state, int seat, List<Action> actions, Pathing pathing) {
        Rules.TurnResult result = new Rules.TurnResult(); Rules.expand(state, seat, actions, pathing, result.moves[seat], result);
        require(!result.hasError(seat) && result.moves[seat].skipped.isEmpty() && !result.unknownActions, "Illegal/skipped/unknown action");
        return result.moves[seat];
    }

    static JsonObject plan(GameState state, Rules.PlayerMoves moves) {
        JsonObject result = new JsonObject(); int[] cells = Arrays.copyOf(moves.cells, moves.count), padded = new int[moves.count];
        for (int i = 0; i < cells.length; i++) padded[i] = SpatialObservation.paddedCell(state, cells[i]);
        result.add("cells", JSON.toJsonTree(cells)); result.add("paddedCells", JSON.toJsonTree(padded));
        result.addProperty("count", moves.count); result.addProperty("cost", moves.cost);
        result.add("disrupts", JSON.toJsonTree(moves.disrupts)); result.addProperty("error", moves.error);
        result.add("skipped", JSON.toJsonTree(moves.skipped)); return result;
    }

    static JsonObject metrics(GameState state) {
        int[] rails = new int[2], paths = new int[2]; int active = 0;
        for (int owner : state.owner) if (owner == 0 || owner == 1) rails[owner]++;
        for (int pair = 0; pair < state.pairCount; pair++) if (state.pairPath[pair] != null) {
            active++; boolean own0 = false, own1 = false;
            for (int at = 0; at < state.pairPathLen[pair]; at++) {
                int owner = state.owner[state.pairPath[pair][at]]; own0 |= owner == 0; own1 |= owner == 1;
            }
            if (own0) paths[0]++; if (own1) paths[1]++;
        }
        JsonObject result = new JsonObject(); result.add("ownedRails", JSON.toJsonTree(rails));
        result.add("ownedDesiredPaths", JSON.toJsonTree(paths)); result.addProperty("activeDesiredPaths", active); return result;
    }

    /** Replay retained games through core rules and recompute only the recorded preturn values. */
    static void verify(Path directory) throws Exception {
        Path out = directory.toAbsolutePath().normalize(), output = out.resolve("replay-audit.json");
        require(!Files.exists(output), "Replay audit output already exists");
        JsonObject audit = new JsonObject(); audit.addProperty("status", "running");
        audit.addProperty("method", "All saved games replayed with public Rules.resolve and exact acting-learner/V5 stage0 values; no bot play or fitting calls");
        JsonArray verified = new JsonArray(); audit.add("games", verified);
        try {
            byte[] bytes = Files.readAllBytes(out.resolve("manifest.json"));
            audit.addProperty("manifestSha256", sha(bytes)); JsonObject manifest = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
            require("complete".equals(manifest.get("status").getAsString())
                    && "spatial-learning-audit".equals(manifest.get("experiment").getAsString())
                    && manifest.get("schemaVersion").getAsInt() == 1 && manifest.get("scheduledGames").getAsInt() == 64
                    && manifest.get("budgetMs").getAsLong() == BUDGET_MS && !manifest.get("warmup").getAsBoolean()
                    && manifest.get("mapPreflightComplete").getAsBoolean(), "Incomplete/wrong diagnostic manifest");
            for (String spec : LEARNERS) {
                equal(manifest.getAsJsonObject("params").get(spec), BotFactory.configuration(spec), "Learner configuration");
                require(sha(Base64.getDecoder().decode(payload(spec))).equals(manifest.getAsJsonObject("weightsSha256").get(spec).getAsString()), "Learner payload identity changed");
            }
            for (String spec : OPPONENTS) equal(manifest.getAsJsonObject("params").get(spec), BotFactory.configuration(spec), "Opponent configuration");
            String v5Hash = sha(Base64.getDecoder().decode(SpatialV5Weights.DATA));
            require(v5Hash.equals(manifest.getAsJsonObject("weightsSha256").get("spatial-ppo-v5").getAsString()), "V5 critic identity changed");
            JsonArray maps = manifest.getAsJsonArray("maps"), games = manifest.getAsJsonArray("games");
            require(maps.size() == 4 && games.size() == 64, "Wrong diagnostic map/game count");
            Set<String> distinctMaps = new HashSet<>();
            for (int at = 0; at < 4; at++) {
                JsonObject map = maps.get(at).getAsJsonObject(); String hash = sha(map.get("initBlockP0").getAsString().getBytes(StandardCharsets.UTF_8));
                require(map.get("mapIndex").getAsInt() == at && map.get("seed").getAsLong() == 22_000_000L + at
                        && hash.equals(map.get("mapHash").getAsString()) && distinctMaps.add(hash), "Map identity mismatch");
            }
            int total = 0;
            for (int at = 0; at < 64; at++) {
                GameSpec spec = schedule().get(at); JsonObject index = games.get(at).getAsJsonObject();
                JsonObject map = maps.get(spec.mapIndex).getAsJsonObject();
                checkIdentity(index, spec, map);
                require(index.get("file").getAsString().equals(spec.id() + ".json"), "Unexpected game file");
                byte[] gameBytes = Files.readAllBytes(out.resolve(spec.id() + ".json"));
                require(sha(gameBytes).equals(index.get("sha256").getAsString()), "Game hash mismatch");
                JsonObject game = JsonParser.parseString(new String(gameBytes, StandardCharsets.UTF_8)).getAsJsonObject();
                checkIdentity(game, spec, map); equal(game.get("initBlockP0"), map.get("initBlockP0"), "Initial protocol");
                require(game.get("maxTurns").getAsInt() == 100 && game.get("budgetMs").getAsLong() == BUDGET_MS
                        && !game.get("warmup").getAsBoolean(), "Wrong production turn/call bounds");
                equal(game.get("learnerParams"), manifest.getAsJsonObject("params").get(spec.learnerSpec), "Game learner params");
                equal(game.get("opponentParams"), manifest.getAsJsonObject("params").get(spec.opponentSpec), "Game opponent params");
                equal(game.get("learnerWeightsSha256"), manifest.getAsJsonObject("weightsSha256").get(spec.learnerSpec), "Game learner payload");
                equal(game.get("v5WeightsSha256"), v5Hash, "Game critic payload");
                for (String field : List.of("status", "turnCount", "score", "terminal")) equal(game.get(field), index.get(field), "Indexed " + field);
                JsonObject result = verifyGame(game); result.addProperty("id", spec.id()); result.addProperty("sha256", sha(gameBytes));
                verified.add(result); total += result.get("turnCount").getAsInt();
            }
            require(total == manifest.get("totalTurns").getAsInt(), "Total replay turn count mismatch");
            audit.addProperty("status", "complete"); audit.addProperty("passed", true);
            audit.addProperty("gameCount", verified.size()); audit.addProperty("totalTurns", total); writeNew(output, audit);
            System.out.println("Learning audit replay verified: " + verified.size() + " games / " + total + " turns");
        } catch (Exception failure) {
            audit.addProperty("status", "failed"); audit.addProperty("passed", false); audit.addProperty("failure", failure.toString());
            writeNew(output, audit); throw failure;
        }
    }

    static JsonObject verifyGame(JsonObject game) throws Exception {
        return verifyGame(game, new SpatialNet(payload(game.get("learnerSpec").getAsString())), new SpatialNet(SpatialV5Weights.DATA));
    }

    /** Synthetic tests may inject value heads; production always binds the recorded frozen learner. */
    static JsonObject verifyGame(JsonObject game, SpatialNet learnerNet, SpatialNet criticNet) throws Exception {
        require("complete".equals(game.get("status").getAsString()) && game.get("terminal").getAsBoolean(), "Incomplete saved game");
        String init = game.get("initBlockP0").getAsString();
        require(sha(init.getBytes(StandardCharsets.UTF_8)).equals(game.get("mapHash").getAsString()), "Game map hash mismatch");
        GameState state = Protocol.readInit(new Protocol.TokenReader(new ByteArrayInputStream(init.getBytes(StandardCharsets.UTF_8)))).state;
        state.maxTurns = game.get("maxTurns").getAsInt();
        require(state.maxTurns > 0 && state.maxTurns <= 100, "Invalid saved game turn bound");
        Pathing pathing = new Pathing(state.size); Rules.updateConnections(state, pathing);
        SpatialObservation observation = new SpatialObservation(state);
        int learnerSeat = game.get("learnerSeat").getAsInt(); require(learnerSeat == 0 || learnerSeat == 1, "Invalid learner seat");
        JsonArray turns = game.getAsJsonArray("turns");
        for (int at = 0; at < turns.size(); at++) {
            JsonObject row = turns.get(at).getAsJsonObject();
            require(!Rules.gameOver(state, pathing) && state.turn == at && row.get("turn").getAsInt() == at, "Invalid preturn/terminal boundary");
            equal(row.get("preStateSha256"), stateHash(state), "Preturn hash");
            equal(row.get("preTurnBlockP0"), Protocol.turnBlock(state, 0), "P0 preturn protocol");
            equal(row.get("preTurnBlockP1"), Protocol.turnBlock(state, 1), "P1 preturn protocol");
            equal(row.get("scoreBefore"), state.score, "Preturn score"); equal(row.get("incomeBefore"), income(state), "Preturn income");
            equal(row.get("metricsBefore"), metrics(state), "Preturn construction metrics");
            require(Double.isFinite(row.get("learnerValueRaw").getAsDouble()) && Double.isFinite(row.get("v5ValueRaw").getAsDouble())
                    && Double.isFinite(row.get("diagnosticElapsedMs").getAsDouble()) && row.get("diagnosticElapsedMs").getAsDouble() >= 0, "Invalid value diagnostic");
            observation.prepare(state, learnerSeat); observation.prefix(null, 0, GameState.PAINT_PER_TURN, 0, false);
            learnerNet.forward(observation); criticNet.forward(observation);
            require(Float.isFinite(learnerNet.value) && Float.isFinite(criticNet.value)
                    && Float.floatToRawIntBits(learnerNet.value) == Float.floatToRawIntBits(row.get("learnerValueRaw").getAsFloat())
                    && Float.floatToRawIntBits(criticNet.value) == Float.floatToRawIntBits(row.get("v5ValueRaw").getAsFloat()), "Frozen stage0 value mismatch");
            JsonArray commands = row.getAsJsonArray("actions"), expanded = row.getAsJsonArray("expanded"), calls = row.getAsJsonArray("calls");
            require(commands.size() == 2 && expanded.size() == 2 && calls.size() == 2, "Missing simultaneous player decision");
            List<List<Action>> actions = new ArrayList<>();
            for (int seat = 0; seat < 2; seat++) {
                actions.add(Action.parseLine(commands.get(seat).getAsString()));
                equal(expanded.get(seat), plan(state, validate(state, seat, actions.get(seat), pathing)), "Expanded plan");
                JsonObject call = calls.get(seat).getAsJsonObject(); double elapsed = call.get("elapsedMs").getAsDouble();
                require(call.get("me").getAsInt() == seat && call.get("complete").getAsBoolean() && call.get("legal").getAsBoolean()
                        && call.get("budgetMs").getAsLong() == BUDGET_MS && Double.isFinite(elapsed) && elapsed >= 0 && elapsed < BUDGET_MS, "Invalid/incomplete policy call");
                equal(call.get("actions"), commands.get(seat), "Call command");
                equal(call.get("role"), seat == learnerSeat ? "learner" : "opponent", "Call role");
                equal(call.get("spec"), game.get(seat == learnerSeat ? "learnerSpec" : "opponentSpec"), "Call policy");
                require(!call.has("interrupted") || !call.get("interrupted").getAsBoolean(), "Interrupted learner call");
                require(!call.has("ordinarySearchComplete") || call.get("ordinarySearchComplete").getAsBoolean()
                        || call.get("selectedOpening").getAsBoolean(), "Incomplete v09 call");
            }
            int unused = GameState.PAINT_PER_TURN - expanded.get(learnerSeat).getAsJsonObject().get("cost").getAsInt();
            equal(row.get("learnerUnusedPaint"), unused, "Unused paint"); equal(row.get("learnerStoppedWithPaint"), unused > 0, "STOP diagnostic");
            Rules.TurnResult result = Rules.resolve(state, actions.get(0), actions.get(1), pathing);
            JsonArray accepted = row.getAsJsonArray("accepted"); require(accepted.size() == 2 && !result.unknownActions, "Malformed accepted plans");
            for (int seat = 0; seat < 2; seat++) {
                require(!result.hasError(seat) && result.moves[seat].skipped.isEmpty(), "Replay rejected action");
                equal(accepted.get(seat), plan(state, result.moves[seat]), "Accepted plan"); equal(accepted.get(seat), expanded.get(seat), "Expansion/acceptance");
            }
            equal(row.get("gained"), result.gained, "Turn gains"); equal(row.get("scoreAfter"), state.score, "Post-turn score");
            equal(row.get("incomeAfter"), income(state), "Post-turn income"); equal(row.get("metricsAfter"), metrics(state), "Post-turn construction metrics");
            equal(row.get("neutralised"), result.neutralised, "Collision cells"); equal(row.get("inkedRegions"), result.inkedRegions, "Inked regions");
            equal(row.get("postStateSha256"), stateHash(state), "Post-turn hash");
            equal(row.get("terminalAfter"), Rules.gameOver(state, pathing), "Terminal flag");
            require(row.get("terminalAfter").getAsBoolean() == (at == turns.size() - 1), "Final-only terminal boundary");
        }
        require(!turns.isEmpty() && Rules.gameOver(state, pathing) && state.turn == game.get("turnCount").getAsInt(), "Replay did not reach recorded actual terminal");
        equal(game.get("score"), state.score, "Final score"); equal(game.get("finalStateSha256"), stateHash(state), "Final hash");
        JsonObject result = new JsonObject(); result.addProperty("passed", true); result.addProperty("turnCount", state.turn);
        result.add("score", JSON.toJsonTree(state.score)); result.addProperty("finalStateSha256", stateHash(state)); return result;
    }

    private static void checkIdentity(JsonObject entry, GameSpec spec, JsonObject map) {
        equal(entry.get("id"), spec.id(), "Game id"); equal(entry.get("mapIndex"), spec.mapIndex, "Map index");
        equal(entry.get("seed"), spec.seed, "Map seed"); equal(entry.get("mapHash"), map.get("mapHash"), "Map hash");
        equal(entry.get("learnerSpec"), spec.learnerSpec, "Learner spec"); equal(entry.get("opponentSpec"), spec.opponentSpec, "Opponent spec");
        equal(entry.get("learnerSeat"), spec.learnerSeat, "Learner seat");
    }
    private static void equal(com.google.gson.JsonElement actual, Object expected, String message) {
        require(actual != null && actual.equals(JSON.toJsonTree(expected)), message + " mismatch");
    }

    static Set<String> exclusions(JsonObject json) {
        require(json.has("mapHashes") && json.get("mapHashes").isJsonArray() && !json.getAsJsonArray("mapHashes").isEmpty(), "Nonempty mapHashes exclusion manifest required");
        Set<String> result = new HashSet<>();
        for (var item : json.getAsJsonArray("mapHashes")) {
            require(item.isJsonPrimitive() && item.getAsJsonPrimitive().isString(), "Invalid excluded map hash");
            String hash = item.getAsString(); require(hash.matches("[0-9a-f]{64}") && result.add(hash), "Malformed/duplicate excluded map hash");
        }
        return result;
    }
    static void requireFresh(String hash, Set<String> excluded, Set<String> used) {
        require(!excluded.contains(hash), "Scheduled map is in prior exclusions: " + hash);
        require(used.add(hash), "Scheduled maps share an identity: " + hash);
    }
    static String payload(String learner) {
        return switch (learner) {
            case "spatial-ppo-v2-init" -> SpatialInitialWeights.DATA;
            case "spatial-ppo-v2" -> SpatialV2Weights.DATA;
            case "spatial-ppo-v3" -> SpatialV3Weights.DATA;
            case "spatial-ppo-v4" -> SpatialV4Weights.DATA;
            default -> throw new IllegalArgumentException("Unsupported frozen learner: " + learner);
        };
    }
    private static JsonObject identity(GameSpec spec, JsonObject map) {
        JsonObject result = map.deepCopy(); result.addProperty("id", spec.id());
        result.addProperty("learnerSpec", spec.learnerSpec); result.addProperty("opponentSpec", spec.opponentSpec);
        result.addProperty("learnerSeat", spec.learnerSeat); return result;
    }
    private static int[] income(GameState state) { int[] out = new int[2]; Rules.incomePerTurn(state, out); return out; }
    static String stateHash(GameState state) throws Exception {
        return sha((Protocol.initBlock(state, 0) + state.turn + "\n" + state.maxTurns + "\n" + Protocol.turnBlock(state, 0)).getBytes(StandardCharsets.UTF_8));
    }
    static String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    private static void require(boolean condition, String message) { if (!condition) throw new IllegalStateException(message); }
    static void writeNew(Path path, JsonObject value) throws Exception { Files.writeString(path, JSON.toJson(value), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW); }
}
