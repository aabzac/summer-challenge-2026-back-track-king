package btk.tools;

import btk.core.Action;
import btk.core.GameState;
import btk.core.Pathing;
import btk.core.Protocol;
import btk.core.Rules;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed offline v09 continuation/closure panel. No policy is registered or changed. */
public final class V09ContinuationStudy {
    static final Gson JSON = new GsonBuilder().serializeNulls().create();
    private static final Gson PRETTY = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
    private static final int[] EMPTY = new int[0];
    private static final String AUDIT_HASH = "bad756d65fbc1cb8d02f29e94b777bc2eae06cf0ea21a4977629dc48c1325981";
    record Plan(int[] cells, int target, String action) {}
    record Source(JsonObject row, CgReplay replay, Path path) {}
    record Inspected(Map<String, Object> row, boolean qualifies, int target) {}

    static void require(boolean ok, String message) { if (!ok) throw new IllegalStateException(message); }
    static String hash(Path path) throws Exception { return ReplyStudy.hash(Files.readAllBytes(path)); }
    static Plan accepted(GameState s, int me, String text) {
        require(text != null, "Missing source action");
        var tr = new Rules.TurnResult();
        List<Action> actions = Action.parseLine(text);
        Rules.expand(s, me, actions, new Pathing(s.size), tr.moves[me], tr);
        var m = tr.moves[me];
        require(!tr.unknownActions && m.error == null && m.skipped.isEmpty() && m.disrupts.size() <= 1,
                "Rejected/skipped source action: " + text);
        return new Plan(Arrays.copyOf(m.cells, m.count), m.disrupts.isEmpty() ? -1 : m.disrupts.get(0), Action.format(actions));
    }
    static boolean waiting(Plan action) { return action.cells.length == 0 && action.target < 0; }
    static boolean wins(V09ClosureProbe.Outcome o) { return o.terminal() && o.scoreDifference() > 0; }
    static boolean contains(int[] array, int value) { for (int x : array) if (x == value) return true; return false; }
    static void add(Map<String, Integer> totals, String key, int value) { totals.merge(key, value, Integer::sum); }
    static void flag(Map<String, Integer> totals, String key, boolean value) { add(totals, key, value ? 1 : 0); }

    static List<Map<String, Object>> oracleClosures(GameState s, int me, int[] ownCells, Plan opponent) {
        var without = V09ClosureProbe.evaluate(s, me, ownCells, -1, opponent.cells, opponent.target);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int region = 0; region < s.regionCount; region++) {
            if (s.regionInked[region] || s.regionHasTown(region) || s.regionInstability[region] < 2) continue;
            var after = V09ClosureProbe.evaluate(s, me, ownCells, region, opponent.cells, opponent.target);
            if (after.topologyClosed()) rows.add(Map.of("region", region, "outcome", after,
                    "opponentAlreadyClosesWithoutOurHit", without.topologyClosed()));
        }
        return rows;
    }

    static Inspected inspect(GameState s, int me, Plan actual, Plan opponent,
                             Map<String, Integer> totals) throws Exception {
        String stateHash = ReplyStudy.stateHash(s);
        V09ContinuationProbe probe = V09ContinuationProbe.prepare(s, me);
        var full = probe.selected(); Plan ample = new Plan(full.cells(), full.target(), full.action());
        var info = probe.targetInfo(ample.cells);
        var closures = V09ClosureProbe.scan(s, me, ample.cells);
        var actualPassive = V09ClosureProbe.evaluate(s, me, actual.cells, actual.target, EMPTY, -1);
        var amplePassive = V09ClosureProbe.evaluate(s, me, ample.cells, ample.target, EMPTY, -1);
        var actualOracle = V09ClosureProbe.evaluate(s, me, actual.cells, actual.target, opponent.cells, opponent.target);
        var ampleOracle = V09ClosureProbe.evaluate(s, me, ample.cells, ample.target, opponent.cells, opponent.target);
        var allOracleClosures = oracleClosures(s, me, ample.cells, opponent);
        boolean oracleWinningOption = allOracleClosures.stream()
                .anyMatch(r -> wins((V09ClosureProbe.Outcome) r.get("outcome")));
        var nativeRoots = probe.openingSelected() ? List.<V09ContinuationProbe.Root>of() : probe.roots();
        List<Map<String, Object>> rawRootTargets = new ArrayList<>(); Set<Integer> nativeShortlist = new HashSet<>();
        Set<Integer> nativeTargets = new HashSet<>();
        if (!closures.isEmpty()) for (var root : nativeRoots) {
            var rootInfo = probe.targetInfo(root.cells());
            for (int target : rootInfo.shortlist()) if (target >= 0) nativeShortlist.add(target);
            if (!root.suppressed() && root.target() >= 0) nativeTargets.add(root.target());
            rawRootTargets.add(Map.of("root", root, "targetInfo", rootInfo));
        }
        List<Map<String, Object>> closureRows = new ArrayList<>();
        int winning = 0, omitted = 0, joint = 0, winningMissedActual = 0, winningMissedAmple = 0;
        for (var c : closures) {
            var row = new LinkedHashMap<String, Object>();
            var oracle = V09ClosureProbe.evaluate(s, me, ample.cells, c.region(), opponent.cells, opponent.target);
            row.put("candidate", c); row.put("oracleWithRecordedOpponent", oracle);
            row.put("inChooserShortlist", contains(info.shortlist(), c.region()));
            row.put("inAnyNativeRawRootShortlist", nativeShortlist.contains(c.region()));
            row.put("inUnsuppressedNativeRootTargets", nativeTargets.contains(c.region()));
            row.put("chosenByChooser", info.chosen() == c.region());
            row.put("emittedByAmple", ample.target == c.region()); row.put("emittedInReplay", actual.target == c.region());
            boolean guaranteed = c.hitsNeeded() == 1 && wins(c.versusPassive());
            row.put("guaranteedOneHitWin", guaranteed);
            if (c.hitsNeeded() == 1) {
                require(c.versusPassive().topologyClosed() && oracle.topologyClosed(), "One-hit cut must close");
                require(c.versusPassive().scoreDifference() == s.score[me] - s.score[1-me]
                        && oracle.scoreDifference() == c.versusPassive().scoreDifference()
                        && oracle.ownIncome() == 0 && oracle.opponentIncome() == 0, "Banked closure invariant");
            }
            if (guaranteed) {
                winning++; if (!contains(info.shortlist(), c.region())) omitted++;
                if (!(actualPassive.topologyClosed() && wins(actualPassive))) winningMissedActual++;
                if (!(amplePassive.topologyClosed() && wins(amplePassive))) winningMissedAmple++;
            }
            if (c.hitsNeeded() == 2 && wins(c.withJointHit())) joint++;
            closureRows.add(row);
        }
        var row = new LinkedHashMap<String, Object>();
        row.put("turn", s.turn); row.put("stateSha256", stateHash); row.put("score", s.score.clone());
        int[] income = new int[2]; Rules.incomePerTurn(s, income); row.put("income", income);
        row.put("recorded", actual); row.put("recordedOpponent", opponent); row.put("ample", ample);
        row.put("opening", probe.openingSelected()); row.put("ownRoutes", probe.ownCount());
        row.put("opponentRoutes", probe.opponentCount()); row.put("replies", probe.replies());
        row.put("ordinaryComplete", probe.ordinaryComplete()); row.put("networkComplete", probe.networkComplete());
        row.put("targetInfoAfterFullConstruction", info); row.put("closures", closureRows);
        row.put("nativeRawRootTargetsAtClosingPositions", rawRootTargets);
        row.put("allClosingTargetsVsRecordedOpponent", allOracleClosures);
        row.put("recordedVsPassive", actualPassive); row.put("ampleVsPassive", amplePassive);
        row.put("recordedVsRecorded", actualOracle); row.put("ampleVsRecorded", ampleOracle);
        row.put("guaranteedWinningRegions", winning); row.put("omittedWinningRegions", omitted);
        row.put("missedGuaranteedByActual", winningMissedActual > 0);
        row.put("missedGuaranteedByAmple", winningMissedAmple > 0);
        row.put("recordedAlreadyTerminalWinVsPassive", wins(actualPassive));
        row.put("ampleAlreadyTerminalWinVsPassive", wins(amplePassive));
        row.put("recordedAlreadyTerminalWinVsRecorded", wins(actualOracle));
        row.put("ampleAlreadyTerminalWinVsRecorded", wins(ampleOracle));
        row.put("missedImmediateWinByActualVsRecorded", winningMissedActual > 0 && !wins(actualOracle));
        row.put("missedImmediateWinByAmpleVsRecorded", winningMissedAmple > 0 && !wins(ampleOracle));
        row.put("conditionalJointWinningRegions", joint);
        boolean qualifies = false, deferred = false; int attack = -1;
        if (!probe.openingSelected() && probe.ownCount() == 0) {
            for (var r : probe.roots()) if (r.index() == probe.waitRoot()) attack = r.target();
            if (attack >= 0) {
                int root = probe.waitRoot();
                var attackTrace = probe.trace(root, attack, V09ContinuationProbe.Variant.BASELINE);
                var waitTrace = probe.trace(root, -1, V09ContinuationProbe.Variant.BASELINE);
                var noPromise = probe.trace(root, -1, V09ContinuationProbe.Variant.NO_FUTURE_OWN_DISRUPT);
                var passiveAttack = probe.trace(root, attack, V09ContinuationProbe.Variant.PASSIVE_OPPONENT);
                var passiveWait = probe.trace(root, -1, V09ContinuationProbe.Variant.PASSIVE_OPPONENT);
                boolean waitPreferred = waitTrace.score().value() > attackTrace.score().value() + 1e-9;
                boolean promised = waitTrace.replies().stream().anyMatch(r -> r.turns().stream()
                        .anyMatch(t -> t.offset() > 0 && t.targets()[me] >= 0));
                boolean reversed = attackTrace.score().value() > noPromise.score().value() + 1e-9;
                deferred = waiting(ample) && waitPreferred && promised;
                qualifies = waiting(ample) && waitPreferred && reversed;
                row.put("attackTrace", attackTrace); row.put("waitTrace", waitTrace);
                row.put("noFutureOwnDisruptTrace", noPromise); row.put("passiveOpponentAttackTrace", passiveAttack);
                row.put("passiveOpponentWaitTrace", passiveWait); row.put("nativeWaitPreferred", waitPreferred);
                row.put("nativeWaitPromisesLaterAttack", promised); row.put("noPromiseReversesPreference", reversed);
                flag(totals, "emptyPortfolioWithNominatedAttack", true);
                flag(totals, "nativeWaitPreferred", waitPreferred); flag(totals, "waitPromisesLaterAttack", promised);
                flag(totals, "noPromiseReversesPreference", reversed);
                flag(totals, "passiveOpponentPrefersAttack", passiveAttack.score().value() > passiveWait.score().value() + 1e-9);
            }
        }
        row.put("nominatedEmptyPortfolioAttack", attack); row.put("deferredEmittedWait", deferred);
        row.put("qualifiesReplannedProbe", qualifies);
        add(totals, "states", 1); flag(totals, "emptyOwnPortfolio", probe.ownCount() == 0);
        flag(totals, "recordedFullWait", waiting(actual)); flag(totals, "ampleFullWait", waiting(ample));
        flag(totals, "recordedAndAmpleDeferredWait", waiting(actual) && deferred);
        flag(totals, "deferredEmittedWait", deferred); flag(totals, "qualifiesReplannedProbe", qualifies);
        add(totals, "closureRegions", closures.size()); add(totals, "guaranteedWinningRegions", winning);
        add(totals, "omittedWinningRegions", omitted); add(totals, "conditionalJointWinningRegions", joint);
        flag(totals, "statesWithGuaranteedWin", winning > 0); flag(totals, "statesWithOmittedWinningRegion", omitted > 0);
        flag(totals, "statesMissedGuaranteedByActual", winningMissedActual > 0);
        flag(totals, "statesMissedGuaranteedByAmple", winningMissedAmple > 0);
        flag(totals, "statesMissedImmediateWinByActualVsRecorded", winningMissedActual > 0 && !wins(actualOracle));
        flag(totals, "statesMissedImmediateWinByAmpleVsRecorded", winningMissedAmple > 0 && !wins(ampleOracle));
        flag(totals, "winningOpportunityButActualAlreadyWins", winning > 0 && wins(actualOracle));
        flag(totals, "winningOpportunityButAmpleAlreadyWins", winning > 0 && wins(ampleOracle));
        flag(totals, "statesWithOracleClosingWin", oracleWinningOption);
        flag(totals, "statesMissedOracleClosingWinByAmple", oracleWinningOption && !wins(ampleOracle));
        require(stateHash.equals(ReplyStudy.stateHash(s)), "Study mutated input");
        return new Inspected(row, qualifies, attack);
    }

    /** Eight real replans vs pure WAIT; a diagnostic pairwise selector, not a production bot. */
    static Map<String, Object> episode(GameState initial, int me, boolean diagnostic) throws Exception {
        GameState s = initial.copy(); Pathing pathing = new Pathing(s.size);
        List<Map<String, Object>> turns = new ArrayList<>();
        for (int step = 0; step < 8 && !Rules.gameOver(s, pathing); step++) {
            var p = V09ContinuationProbe.prepare(s, me); var chosen = p.selected();
            String action = chosen.action(); int target = chosen.target(); boolean changed = false;
            if (diagnostic && !p.openingSelected() && p.ownCount() == 0
                    && chosen.cells().length == 0 && target < 0) {
                int nominated = -1;
                for (var r : p.roots()) if (r.index() == p.waitRoot()) nominated = r.target();
                if (nominated >= 0) {
                    double a = p.invokeOriginal(p.waitRoot(), nominated).value();
                    double w = p.invokeOriginal(p.waitRoot(), -1).value();
                    double noPromise = p.trace(p.waitRoot(), -1,
                            V09ContinuationProbe.Variant.NO_FUTURE_OWN_DISRUPT).score().value();
                    if (w > a + 1e-9 && a > noPromise + 1e-9) {
                        target = nominated; action = Action.format(List.of(Action.disrupt(target))); changed = true;
                    }
                }
            }
            var tr = me == 0
                    ? Rules.resolve(s, Action.parseLine(action), List.of(Action.waitAction()), pathing)
                    : Rules.resolve(s, List.of(Action.waitAction()), Action.parseLine(action), pathing);
            require(tr.moves[me].error == null && tr.moves[me].skipped.isEmpty(), "Illegal replan action");
            int[] income = new int[2]; Rules.incomePerTurn(s, income);
            var row = new LinkedHashMap<String, Object>(); row.put("turn", s.turn - 1);
            row.put("nativeAction", chosen.action()); row.put("action", action); row.put("changed", changed);
            row.put("target", target); row.put("score", s.score.clone()); row.put("income", income);
            row.put("inkedRegions", List.copyOf(tr.inkedRegions)); row.put("terminal", Rules.gameOver(s, pathing));
            row.put("stateSha256", ReplyStudy.stateHash(s)); turns.add(row);
        }
        var out = new LinkedHashMap<String, Object>(); out.put("diagnosticSelector", diagnostic);
        out.put("opponent", "pure WAIT, no recorded future actions"); out.put("turns", turns);
        out.put("finalScoreDifference", s.score[me] - s.score[1-me]); out.put("terminal", Rules.gameOver(s, pathing));
        out.put("scope", "Eight-step replanning diagnostic, not match strength or a registered candidate"); return out;
    }

    static List<Source> load(Path audit) throws Exception {
        require(hash(audit).equals(AUDIT_HASH), "Unexpected source audit");
        var document = JsonParser.parseString(Files.readString(audit)).getAsJsonObject();
        var plan = document.getAsJsonObject("controlPlan");
        require(hash(Path.of(plan.get("path").getAsString())).equals(plan.get("sha256").getAsString()), "Control plan changed");
        require(document.get("complete").getAsBoolean() && document.get("games").getAsInt() == 40, "Expected complete40");
        List<Source> sources = new ArrayList<>(); Set<Long> ids = new HashSet<>(); Set<String> maps = new HashSet<>();
        for (var element : document.getAsJsonArray("rows")) {
            var row = element.getAsJsonObject(); var canonical = row.getAsJsonObject("canonical");
            Path path = Path.of(canonical.get("path").getAsString());
            require(hash(path).equals(canonical.get("sha256").getAsString()), "Replay changed: " + path);
            CgReplay replay = CgReplay.load(path);
            require(replay.gameId == row.get("gameId").getAsLong() && ids.add(replay.gameId), "Game id mismatch/duplicate");
            require(replay.mapSeed().isPresent() && replay.mapSeed().getAsLong() == row.get("seed").getAsLong(), "Seed mismatch");
            require(row.get("slot").getAsInt() == sources.size() + 1, "Slot order mismatch");
            String map = ReplyStudy.hash(Protocol.initBlock(replay.toState(), 0).getBytes(StandardCharsets.UTF_8));
            require(map.equals(row.get("coreMapHash").getAsString()) && maps.add(map), "Core map mismatch/duplicate");
            var validation = CgValidate.validate(replay, false);
            require(validation.firstDivergenceTurn < 0, "Replay divergence: " + replay.gameId + " " + validation.reason);
            for (int t = 0; t < replay.turns(); t++) for (int p = 0; p < 2; p++)
                require(replay.failure(t, p) == null && replay.turnStdout.get(t)[p] != null, "Failed/missing source turn");
            sources.add(new Source(row, replay, path));
        }
        require(sources.size() == 40, "Expected40 sources"); return sources;
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("sourceAudit outputDirectory historicalReplay");
        Path out = Path.of(args[1]); require(!Files.exists(out), "Refuse to overwrite diagnostic directory");
        List<Source> sources = load(Path.of(args[0]));
        CgReplay historical = CgReplay.load(Path.of(args[2]));
        require(historical.gameId == 904554072 && CgValidate.validate(historical, false).firstDivergenceTurn < 0,
                "Historical regression identity/validation");
        Files.createDirectories(out);
        Map<String, Integer> totals = new LinkedHashMap<>(); List<Map<String, Object>> games = new ArrayList<>();
        List<Map<String, Object>> episodes = new ArrayList<>();
        Path rowsPath = out.resolve("positions.jsonl");
        try (BufferedWriter writer = Files.newBufferedWriter(rowsPath, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW)) {
            for (Source source : sources) {
                GameState s = source.replay.toState(); Pathing pathing = new Pathing(s.size); Rules.updateConnections(s, pathing);
                int me = source.row.get("seat").getAsInt(); Map<String, Integer> counts = new LinkedHashMap<>();
                boolean branchDone = false; int previousDeferredTurn = -2, previousTarget = -1;
                int recordedWaitStreak = 0, longestRecordedWait = 0;
                for (int t = 0; t < source.replay.turns(); t++) {
                    require(!Rules.gameOver(s, pathing) && s.turn == t, "Unexpected pre-turn termination");
                    Plan actual = accepted(s, me, source.replay.turnStdout.get(t)[me]);
                    Plan enemy = accepted(s, 1-me, source.replay.turnStdout.get(t)[1-me]);
                    if (waiting(actual)) {
                        if (recordedWaitStreak == 0) add(counts, "recordedWaitEpisodes", 1);
                        recordedWaitStreak++; longestRecordedWait = Math.max(longestRecordedWait, recordedWaitStreak);
                    } else recordedWaitStreak = 0;
                    Inspected inspected = inspect(s, me, actual, enemy, counts); var row = inspected.row;
                    row.put("slot", source.row.get("slot").getAsInt()); row.put("gameId", source.replay.gameId);
                    row.put("me", me); row.put("sourceResult", source.row.get("result").getAsString());
                    if (Boolean.TRUE.equals(row.get("deferredEmittedWait"))) {
                        if (t != previousDeferredTurn + 1 || inspected.target != previousTarget) add(counts, "deferredEpisodes", 1);
                        previousDeferredTurn = t; previousTarget = inspected.target;
                    }
                    if (inspected.qualifies && !branchDone) {
                        var episode = new LinkedHashMap<String, Object>(); episode.put("gameId", source.replay.gameId);
                        episode.put("turn", t); episode.put("me", me); episode.put("stateSha256", ReplyStudy.stateHash(s));
                        episode.put("baseline", episode(s, me, false)); episode.put("diagnostic", episode(s, me, true));
                        episodes.add(episode); branchDone = true;
                    }
                    writer.write(JSON.toJson(row)); writer.newLine();
                    Rules.resolve(s, Action.parseLine(source.replay.turnStdout.get(t)[0]),
                            Action.parseLine(source.replay.turnStdout.get(t)[1]), pathing);
                }
                require(s.score[0] == source.replay.finalScores[0] && s.score[1] == source.replay.finalScores[1], "Final replay score");
                counts.forEach((key, value) -> add(totals, key, value));
                for (String key : List.of("deferredEmittedWait", "qualifiesReplannedProbe", "statesWithGuaranteedWin",
                        "statesWithOmittedWinningRegion", "statesMissedGuaranteedByActual", "statesMissedGuaranteedByAmple",
                        "statesMissedImmediateWinByActualVsRecorded", "statesMissedImmediateWinByAmpleVsRecorded",
                        "statesWithOracleClosingWin", "statesMissedOracleClosingWinByAmple"))
                    flag(totals, "games_" + key, counts.getOrDefault(key, 0) > 0);
                var game = new LinkedHashMap<String, Object>(); game.put("source", source.row); game.put("counts", counts);
                game.put("longestRecordedFullWaitStreak", longestRecordedWait); games.add(game);
                System.out.println("Complete game " + games.size() + "/40: " + source.replay.gameId + " " + JSON.toJson(counts));
                writer.flush();
            }
        }
        require(totals.get("states") == 2936, "Expected exactly2936 live source turns");
        GameState hs = historical.toState(); Pathing hp = new Pathing(hs.size); Rules.updateConnections(hs, hp);
        for (int t = 0; t < 37; t++) Rules.resolve(hs, CgPlayerStudy.effectiveActions(historical,t,0), CgPlayerStudy.effectiveActions(historical,t,1),hp);
        var h = inspect(hs, 0, accepted(hs,0,historical.turnStdout.get(37)[0]),
                accepted(hs,1,historical.turnStdout.get(37)[1]), new LinkedHashMap<>());
        h.row.put("gameId", historical.gameId); h.row.put("sourcePolicy", "historical user-attributed v07, evaluated with frozen v09");
        h.row.put("baselineReplans", episode(hs, 0, false)); h.row.put("diagnosticReplans", episode(hs, 0, true));
        write(out.resolve("historical.json"), h.row); write(out.resolve("episodes.json"), episodes);
        var report = new LinkedHashMap<String, Object>(); report.put("schema", "v09-continuation-v1"); report.put("complete", true);
        report.put("createdAt", Instant.now().toString()); report.put("games", 40); report.put("engineExact", 40);
        report.put("historicalEngineExact", 1); report.put("spec", "v09?NOISE=0"); report.put("ampleMs", 60_000);
        report.put("summary", totals); report.put("gameRows", games); report.put("branchEpisodes", episodes.size());
        report.put("scope", "Historical development trajectories, complete frozen deterministic ample decisions; no strength/timing claim, no new live games");
        report.put("positions", Map.of("path", rowsPath.toString(), "sha256", hash(rowsPath)));
        report.put("historical", Map.of("path", out.resolve("historical.json").toString(), "sha256", hash(out.resolve("historical.json"))));
        report.put("episodes", Map.of("path", out.resolve("episodes.json").toString(), "sha256", hash(out.resolve("episodes.json"))));
        write(out.resolve("report.json"), report); System.out.println(PRETTY.toJson(totals));
    }
    static void write(Path path, Object value) throws Exception {
        Files.writeString(path, PRETTY.toJson(value), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
}
