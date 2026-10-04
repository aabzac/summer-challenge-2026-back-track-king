import btk.bot.neural.NeuralV6Features;
import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import btk.tools.CgReplay;
import btk.tools.CgValidate;
import com.google.gson.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/** One fixed, untimed audit of actual V6 accepted prefixes; no bot calls or new games. */
public final class NeuralV6PrefixAudit {
    static final Path SELF = Path.of("tools/diagnostics/NeuralV6PrefixAudit.java");
    static final Path PLAN = Path.of("build/cg-boss/neural-v6-v09-cached-40/plan.json");
    static final Path FROZEN = Path.of("build/analysis/neural-v6/frozen-preflight.json");
    static final Path PAINT = Path.of("docs/evidence/neural-v6-boss-paint.json");
    static final Path OUT = Path.of("build/analysis/neural-v7-prefix");
    static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();
    static final int[] EMPTY = new int[0];
    static final Pattern MARKER = Pattern.compile("BTK_BUILD_V1 strategy=([^\\s;]+) build=([a-f0-9]{64}) config=([a-f0-9]{64})");
    static final LinkedHashMap<String,String> PINS = new LinkedHashMap<>();
    static final List<Map<String,Object>> ROWS = new ArrayList<>();

    record Input(int slot, int me, Path path, JsonObject result, JsonObject slotJson) {}

    public static void main(String[] args) throws Exception {
        require(args.length == 0, "No adjustable parameters; fixed corpus and filler definition");
        require(!Files.exists(OUT.resolve("intent.json")) && !Files.exists(OUT.resolve("result.json")), "Preserve the prior attempt; no implicit repeat");
        JsonObject plan = read(PLAN), frozen = read(FROZEN), paint = read(PAINT);
        pin(SELF); pin(PLAN); pin(FROZEN); pin(PAINT);
        for (String key : List.of("sourcePins", "runtimePins", "artifactPins")) {
            for (JsonElement el : frozen.getAsJsonArray(key)) {
                JsonObject expected = el.getAsJsonObject(); Path path = Path.of(expected.get("path").getAsString());
                require(hash(path).equals(expected.get("sha256").getAsString()), "Frozen V6 input changed: " + path);
                pin(path);
            }
        }
        for (String entry : System.getProperty("java.class.path").split(java.io.File.pathSeparator)) {
            Path path = Path.of(entry);
            if (Files.isRegularFile(path)) pin(path);
            else if (Files.isDirectory(path)) try (var files = Files.walk(path)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) pin(file);
            }
        }
        require(plan.get("mapHashScheme").getAsString().equals("cg-global-graphics-utf8-sha256-v1"), "Unknown map scheme");
        JsonObject build = plan.getAsJsonObject("builds").getAsJsonObject("candidate");
        require(build.get("strategy").getAsString().equals("neural-v6"), "Wrong candidate");
        require(hash(Path.of(build.get("directory").getAsString()).resolve("Player.java")).equals(build.get("sourceSha256").getAsString()), "Wrong exact artifact");
        JsonArray slots = plan.getAsJsonArray("slots"); require(slots.size() == 40, "Expected complete40 plan");
        List<Input> inputs = new ArrayList<>(); Set<Integer> seenSlots = new HashSet<>(); Set<Long> gameIds = new HashSet<>();
        for (JsonElement item : slots) {
            JsonObject slot = item.getAsJsonObject(); int number = slot.get("slot").getAsInt();
            require(number >= 1 && number <= 40 && seenSlots.add(number), "Bad slot");
            Path dir = PLAN.getParent().resolve(String.format("%04d", number));
            Path resultPath = dir.resolve("result.json"), raw = dir.resolve("canonical.json");
            JsonObject result = read(resultPath), capture = result.getAsJsonObject("capture"), attribution = capture.getAsJsonObject("attribution");
            require(result.get("planId").equals(plan.get("planId")) && result.getAsJsonObject("slot").equals(slot), "Plan identity changed");
            require(capture.get("ok").getAsBoolean() && capture.get("expectedBuildMatches").getAsBoolean(), "Unverified capture");
            require(attribution.get("buildId").equals(build.get("buildId")) && attribution.get("artifactSha256").equals(build.get("sourceSha256"))
                    && attribution.get("configSha256").equals(build.get("configSha256")), "Wrong build attribution");
            require(gameIds.add(capture.get("gameId").getAsLong()), "Duplicate candidate game");
            require(hash(raw).equals(capture.get("rawSha256").getAsString()), "Changed replay");
            Path retained = Path.of(capture.get("path").getAsString()); require(hash(raw).equals(hash(retained)), "Retained replay differs");
            pin(resultPath); pin(raw); pin(retained);
            inputs.add(new Input(number, slot.get("seat").getAsInt(), raw, result, slot));
        }
        inputs.sort(Comparator.comparingInt(Input::slot));
        Map<String,Object> output = new LinkedHashMap<>();
        output.put("schemaVersion", 1); output.put("kind", "fixed actual-prefix feasibility and cheap-fill diagnostic; not a strength test");
        output.put("createdAtUtc", Instant.now().toString()); output.put("newGames", 0); output.put("botCalls", 0);
        output.put("selection", "All40 pinned V6 boss games, every played state turn0..19; exact accepted own prefix analyzed whenever paint<3");
        output.put("prefix", "Rules.expand on actual pre-turn board; Rules.applyPlacements for only recorded own accepted cells; canonical Rules.updateConnections; no opponent action, ink, income credit or turn advance");
        output.put("memory", "Observe every actual pre-turn board and record actual accepted target; full prepare before actual prefix, mark each own cell and incremental prepare after it. No bot RNG or server timing reconstructed");
        output.put("fixedCheapFill", "At most3 cells until remaining paint exhausted: legal, affordable, outside own actual target, town-region or instability0, orthogonally adjacent to an own track. Rank by count of adjacent own earning cells descending, then town-region first, terrain cost ascending, cell index ascending. Recompute eligibility and canonical earning paths after each placement. No candidate-specific income gain is used for ranking");
        output.put("income", "Exact current canonical income before/after only own additions, via Rules.incomePerTurn. No imagined future actions, disruption or income horizon; a fact about this hypothetical board, not a win prediction");
        output.put("limitations", List.of("Recorded prefix fixes actual chosen cells/target, but does not reveal abort phase or why V6 stopped.",
                "Ample feature candidates prove eligible alternatives exist; they do not reconstruct CG deadline/JIT/RNG behavior.",
                "Cheap fill is one predeclared diagnostic rule, not a selected production policy; more paint and immediate income are not strength.",
                "Town-region stability and adjacency restrictions are policy assumptions, not game rules.",
                "Hypothetical own additions omit simultaneous opponent placements/disruption; no oracle opponent actions rank the filler.",
                "Source markers are self-reported artifact attribution; checks do not attest remote executable bytes."));
        output.put("javaVersion", System.getProperty("java.version")); output.put("javaClassPath", System.getProperty("java.class.path"));
        output.put("inputPins", pinList()); Files.createDirectories(OUT); write(OUT.resolve("intent.json"), output);
        int validated = 0;
        for (Input input : inputs) {
            analyze(input, plan, build, paint); validated++;
            System.out.println("Prefix audit " + validated + "/40; states=" + ROWS.size());
        }
        require(ROWS.size() == 800, "Unexpected early-window state count");
        require(ROWS.stream().filter(r -> (int)r.get("acceptedPaint") < 3).count() == 70, "Mismatch with retained paint audit");
        verifyPins();
        output.put("engineReplaysExact", validated); output.put("states", ROWS.size());
        output.put("summary", Map.of("all", summarize(ROWS), "heightBelow18", summarize(ROWS.stream().filter(r -> (int)r.get("height") < 18).toList()),
                "height18AndAbove", summarize(ROWS.stream().filter(r -> (int)r.get("height") >= 18).toList())));
        output.put("rows", ROWS); output.put("complete", true); output.put("finishedAtUtc", Instant.now().toString());
        write(OUT.resolve("result.json"), output);
        System.out.println("Completed prefix audit: " + GSON.toJson(output.get("summary")));
    }

    static void analyze(Input input, JsonObject plan, JsonObject build, JsonObject paint) throws Exception {
        JsonObject raw = read(input.path), outcome = input.result.getAsJsonObject("outcome");
        CgReplay replay = CgReplay.parse(raw); int me = input.me;
        require(me == 0 || me == 1, "Bad seat");
        require(replay.gameId == input.result.getAsJsonObject("capture").get("gameId").getAsLong(), "Game ID mismatch");
        require(outcome.get("seat").getAsInt() == me && outcome.get("seed").equals(input.slotJson.get("seed")), "Seat/seed mismatch");
        require(replay.seed.trim().equals("seed=" + input.slotJson.get("seed").getAsString()), "Replay seed mismatch");
        require(replay.agentIds[1-me] == plan.get("bossAgentId").getAsInt() && replay.isBossGame(), "Wrong boss");
        require(replay.turns() >= 20, "Early window incomplete");
        require(hashBytes(replay.globalGraphics.getBytes(StandardCharsets.UTF_8)).equals(outcome.get("mapHash").getAsString()), "Map mismatch");
        int markers = 0;
        for (JsonElement item : raw.getAsJsonArray("frames")) {
            JsonObject frame = item.getAsJsonObject();
            if (!frame.has("agentId") || frame.get("agentId").getAsInt() != me || !frame.has("stdout") || frame.get("stdout").isJsonNull()) continue;
            var matcher = MARKER.matcher(frame.get("stdout").getAsString());
            while (matcher.find()) { require(matcher.group(1).equals("neural-v6") && matcher.group(2).equals(build.get("buildId").getAsString())
                    && matcher.group(3).equals(build.get("configSha256").getAsString()), "Wrong source marker"); markers++; }
        }
        require(markers > 0, "No source marker");
        CgValidate.Outcome validation = CgValidate.validate(replay, false);
        require(validation.firstDivergenceTurn < 0, "Replay divergence: " + validation.reason);
        GameState actual = replay.toState(); Pathing actualPaths = new Pathing(actual.size); Rules.updateConnections(actual, actualPaths);
        String coreMapHash = hashBytes(Protocol.initBlock(actual, 0).getBytes(StandardCharsets.UTF_8));
        JsonObject expectedGame = null;
        for (JsonElement item : paint.getAsJsonArray("candidateGames")) if (item.getAsJsonObject().get("gameId").getAsLong() == replay.gameId) expectedGame = item.getAsJsonObject();
        require(expectedGame != null && expectedGame.get("coreMapHash").getAsString().equals(coreMapHash), "Missing/different retained core map");
        NeuralV6Features features = new NeuralV6Features(actual);
        for (int turn = 0; turn < 20; turn++) {
            require(actual.turn == turn && replay.failure(turn, 0) == null && replay.failure(turn, 1) == null, "Unexpected turn/failure");
            features.observe(actual, me);
            List<Action> ownActions = Action.parseLine(replay.turnStdout.get(turn)[me]);
            Rules.TurnResult expanded = new Rules.TurnResult(); Rules.PlayerMoves own = expanded.moves[me];
            Rules.expand(actual, me, ownActions, actualPaths, own, expanded);
            require(own.error == null && own.skipped.isEmpty() && own.disrupts.size() <= 1, "Unexpected invalid own action");
            int target = own.disrupts.isEmpty() ? -1 : own.disrupts.get(0);
            JsonObject expectedTurn = expectedGame.getAsJsonArray("trace").get(turn).getAsJsonObject();
            require(own.cost == expectedTurn.getAsJsonArray("paint").get(me).getAsInt() && own.count == expectedTurn.getAsJsonArray("cells").get(me).getAsInt(), "Accepted prefix mismatch");
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("slot", input.slot); row.put("gameId", replay.gameId); row.put("turn", turn); row.put("seat", me);
            row.put("width", actual.width); row.put("height", actual.height); row.put("coreMapHash", coreMapHash);
            row.put("acceptedPaint", own.cost); row.put("acceptedCells", own.count); row.put("prefixCells", Arrays.copyOf(own.cells, own.count));
            row.put("paintLeft", 3-own.cost); row.put("target", target); row.put("actualCommand", replay.turnStdout.get(turn)[me]);
            if (own.cost < 3) {
                features.prepare(actual, me, true);
                GameState work = actual.copy(); Pathing paths = new Pathing(work.size);
                for (int k = 0; k < own.count; k++) {
                    applyOwn(work, me, own.cells[k], paths); features.markPlaced(own.cells[k]); features.prepare(work, me, false);
                }
                int paintLeft = 3-own.cost; List<Integer> legal = new ArrayList<>();
                for (int c = 0; c < work.size; c++) if (work.canBuild(c) && work.placeCost(c) <= paintLeft) legal.add(c);
                int[] cells = new int[work.size]; float[][] x = new float[work.size][NeuralV6Features.BUILD_F];
                int n = features.buildCandidates(work, me, paintLeft, target, own.count, cells, x);
                require(n >= 0, "Ample candidate preparation aborted");
                row.put("legalAffordableCells", legal); row.put("legalAffordableCount", legal.size());
                row.put("v6EligibleCells", Arrays.copyOf(cells, n)); row.put("v6EligibleCount", n);
                int ownAdjacent = 0, townOnlyAdjacent = 0;
                for (int k = 0; k < n; k++) {
                    int c = cells[k]; boolean ownAdj = false, townAdj = false;
                    for (int d = 0; d < 4; d++) { int xx = work.x(c)+GameState.DX[d], yy = work.y(c)+GameState.DY[d];
                        if (!work.inBounds(xx,yy)) continue; int other = work.cell(xx,yy);
                        ownAdj |= work.owner[other] == me; townAdj |= work.isTown(other); }
                    if (ownAdj) ownAdjacent++; else if (townAdj) townOnlyAdjacent++;
                }
                row.put("v6EligibleOwnAdjacentCount", ownAdjacent); row.put("v6EligibleTownButNotOwnAdjacentCount", townOnlyAdjacent);
                List<Integer> targetAvoid = new ArrayList<>(); for (int c : legal) if (work.regionId[c] != target) targetAvoid.add(c);
                row.put("legalAffordableTargetAvoidCount", targetAvoid.size());
                int[] baseline = income(work); row.put("prefixIncome", baseline); List<Map<String,Object>> steps = new ArrayList<>();
                int spent = 0, minStepOwn = 0, minStepNet = 0;
                for (int step = 0; step < 3 && paintLeft > 0; step++) {
                    Choice choice = chooseFill(work, me, paintLeft, target);
                    if (choice.cell < 0) break;
                    int c = choice.cell, cost = work.placeCost(c); require(work.canBuild(c) && cost <= paintLeft, "Invalid diagnostic fill");
                    applyOwn(work, me, c, paths); paintLeft -= cost; spent += cost;
                    int[] after = income(work); Map<String,Object> data = new LinkedHashMap<>();
                    data.put("cell", c); data.put("x", work.x(c)); data.put("y", work.y(c)); data.put("cost", cost);
                    data.put("eligibleChoices", choice.eligible); data.put("earningOwnNeighborsBeforePlacement", choice.earningNeighbors);
                    data.put("townRegion", work.regionHasTown(work.regionId[c])); data.put("regionInstability", work.regionInstability[work.regionId[c]]);
                    data.put("income", after); data.put("ownIncomeDeltaFromPrefix", after[me]-baseline[me]);
                    data.put("netIncomeDeltaFromPrefix", (after[me]-after[1-me])-(baseline[me]-baseline[1-me]));
                    minStepOwn = Math.min(minStepOwn, after[me]-baseline[me]);
                    minStepNet = Math.min(minStepNet, (after[me]-after[1-me])-(baseline[me]-baseline[1-me]));
                    steps.add(data);
                }
                int[] filled = income(work); row.put("cheapFillSteps", steps); row.put("cheapFillPaint", spent); row.put("remainingAfterFill", paintLeft);
                row.put("filledIncome", filled); row.put("ownIncomeDelta", filled[me]-baseline[me]);
                row.put("netIncomeDelta", (filled[me]-filled[1-me])-(baseline[me]-baseline[1-me]));
                row.put("minStepOwnIncomeDelta", minStepOwn); row.put("minStepNetIncomeDelta", minStepNet);
            }
            features.recordTarget(target);
            Rules.TurnResult recorded = Rules.resolve(actual, Action.parseLine(replay.turnStdout.get(turn)[0]), Action.parseLine(replay.turnStdout.get(turn)[1]), actualPaths);
            require(recorded.moves[me].cost == own.cost && Arrays.equals(Arrays.copyOf(recorded.moves[me].cells, recorded.moves[me].count), Arrays.copyOf(own.cells, own.count)), "Expanded/resolved prefix mismatch");
            ROWS.add(row);
        }
    }

    record Choice(int cell, int eligible, int earningNeighbors) {}
    static Choice chooseFill(GameState s, int me, int paint, int target) {
        boolean[] earning = new boolean[s.size];
        for (int p = 0; p < s.pairCount; p++) for (int k = 0; k < s.pairPathLen[p]; k++) if (s.owner[s.pairPath[p][k]] == me) earning[s.pairPath[p][k]] = true;
        int best = -1, eligible = 0, bestEarn = -1; boolean bestTown = false;
        for (int c = 0; c < s.size; c++) {
            int r = s.regionId[c]; boolean town = s.regionHasTown(r);
            if (!s.canBuild(c) || s.placeCost(c) > paint || r == target || (!town && s.regionInstability[r] != 0)) continue;
            int adjacentOwn = 0, earns = 0;
            for (int d = 0; d < 4; d++) { int x = s.x(c)+GameState.DX[d], y = s.y(c)+GameState.DY[d];
                if (!s.inBounds(x,y)) continue; int other = s.cell(x,y); if (s.owner[other] == me) { adjacentOwn++; if (earning[other]) earns++; } }
            if (adjacentOwn == 0) continue; eligible++;
            if (best < 0 || earns > bestEarn || (earns == bestEarn && town && !bestTown)
                    || (earns == bestEarn && town == bestTown && s.placeCost(c) < s.placeCost(best))) { best = c; bestEarn = earns; bestTown = town; }
        }
        return new Choice(best, eligible, bestEarn);
    }
    static void applyOwn(GameState s, int me, int cell, Pathing paths) {
        int[] one = {cell}; Rules.applyPlacements(s, me == 0 ? one : EMPTY, me == 0 ? 1 : 0, me == 1 ? one : EMPTY, me == 1 ? 1 : 0);
        Rules.updateConnections(s, paths);
    }
    static int[] income(GameState s) { int[] result = new int[2]; Rules.incomePerTurn(s, result); return result; }

    static Map<String,Object> summarize(List<Map<String,Object>> rows) {
        int under = 0, left = 0, legal = 0, eligible = 0, targetAvoid = 0, fillTurns = 0, fillPaint = 0, ownPositive = 0, ownNegative = 0, netPositive = 0, netNegative = 0, ownSum = 0, netSum = 0;
        int[] prefixes = new int[4], remainders = new int[4]; Set<Long> games = new HashSet<>();
        int ownAdjTurns = 0, townOnlyTurns = 0, ownAdjCells = 0, townOnlyCells = 0;
        int minOwn = 0, minNet = 0, minStepOwn = 0, minStepNet = 0, negativeOwnStepTurns = 0, negativeNetStepTurns = 0;
        for (Map<String,Object> row : rows) {
            games.add((long)row.get("gameId")); if ((int)row.get("acceptedPaint") == 3) continue;
            under++; int remaining = (int)row.get("paintLeft"); left += remaining; prefixes[(int)row.get("acceptedCells")]++; remainders[remaining]++;
            if ((int)row.get("legalAffordableCount") > 0) legal++; if ((int)row.get("v6EligibleCount") > 0) eligible++;
            if ((int)row.get("legalAffordableTargetAvoidCount") > 0) targetAvoid++;
            int ownAdj = (int)row.get("v6EligibleOwnAdjacentCount"), townOnly = (int)row.get("v6EligibleTownButNotOwnAdjacentCount");
            if (ownAdj > 0) ownAdjTurns++; if (townOnly > 0) townOnlyTurns++; ownAdjCells += ownAdj; townOnlyCells += townOnly;
            int filled = (int)row.get("cheapFillPaint"); if (filled > 0) fillTurns++; fillPaint += filled;
            int own = (int)row.get("ownIncomeDelta"), net = (int)row.get("netIncomeDelta"); ownSum += own; netSum += net;
            if (own > 0) ownPositive++; if (own < 0) ownNegative++; if (net > 0) netPositive++; if (net < 0) netNegative++;
            minOwn = Math.min(minOwn, own); minNet = Math.min(minNet, net);
            int stepOwn = (int)row.get("minStepOwnIncomeDelta"), stepNet = (int)row.get("minStepNetIncomeDelta");
            minStepOwn = Math.min(minStepOwn, stepOwn); minStepNet = Math.min(minStepNet, stepNet);
            if (stepOwn < 0) negativeOwnStepTurns++; if (stepNet < 0) negativeNetStepTurns++;
        }
        Map<String,Object> result = new LinkedHashMap<>(); result.put("games", games.size()); result.put("states", rows.size()); result.put("underpaintStates", under);
        result.put("acceptedPrefixPlacementCountHistogram", prefixes); result.put("remainderPaintHistogram", remainders); result.put("unusedPaint", left);
        result.put("withLegalAffordableCell", legal); result.put("withV6EligibleCell", eligible); result.put("withLegalAffordableTargetAvoidCell", targetAvoid);
        result.put("withV6EligibleOwnAdjacentCell", ownAdjTurns); result.put("withV6EligibleTownButNotOwnAdjacentCell", townOnlyTurns);
        result.put("v6EligibleOwnAdjacentCellSum", ownAdjCells); result.put("v6EligibleTownButNotOwnAdjacentCellSum", townOnlyCells);
        result.put("cheapFillTurns", fillTurns); result.put("cheapFillPaint", fillPaint); result.put("unusedAfterCheapFill", left-fillPaint);
        result.put("cheapFillOwnIncomePositive", ownPositive); result.put("cheapFillOwnIncomeNegative", ownNegative); result.put("cheapFillOwnIncomeDeltaSum", ownSum);
        result.put("cheapFillNetIncomePositive", netPositive); result.put("cheapFillNetIncomeNegative", netNegative); result.put("cheapFillNetIncomeDeltaSum", netSum);
        result.put("cheapFillOwnIncomeDeltaMinimumIncludingNoChange", minOwn); result.put("cheapFillNetIncomeDeltaMinimumIncludingNoChange", minNet);
        result.put("cheapFillAnyOwnNegativeStepTurns", negativeOwnStepTurns); result.put("cheapFillAnyNetNegativeStepTurns", negativeNetStepTurns);
        result.put("cheapFillOwnStepDeltaMinimumIncludingNoChange", minStepOwn); result.put("cheapFillNetStepDeltaMinimumIncludingNoChange", minStepNet);
        return result;
    }
    static JsonObject read(Path path) throws Exception { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
    static String hash(Path path) throws Exception { return hashBytes(Files.readAllBytes(path)); }
    static String hashBytes(byte[] data) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
    static void pin(Path path) throws Exception { PINS.put(path.toString().replace('\\','/'), hash(path)); }
    static List<Map<String,String>> pinList() { List<Map<String,String>> result = new ArrayList<>(); PINS.forEach((path,sha) -> result.add(Map.of("path",path,"sha256",sha))); return result; }
    static void verifyPins() throws Exception { for (var pin : PINS.entrySet()) require(hash(Path.of(pin.getKey())).equals(pin.getValue()), "Pinned input changed: " + pin.getKey()); }
    static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    static void write(Path path, Object data) throws Exception { Files.writeString(path, GSON.toJson(data)+"\n", StandardOpenOption.CREATE_NEW); }
}
