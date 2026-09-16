package btk.tools;

import btk.bot.BotFactory;
import btk.bot.current.RolloutBot;
import btk.core.*;
import java.nio.file.*;
import java.util.*;

/** One fixed serial legality/work-cost audit on previously captured public pre-turn states. */
public final class RolloutCheck {
    public static void main(String[] args) throws Exception {
        Path capture=Path.of(args[0]), out=Path.of(args[1]);
        if(Files.exists(out)) throw new IllegalArgumentException("Choose a fresh report path");
        String spec="current?USE_ROLLOUT_POLICY=1";
        List<Map<String,Object>> samples=new ArrayList<>();
        double total=0,max=0; int compared=0,totalComparisons=0,totalNodes=0;
        for(var sample:ReplyStudy.loadPositions(capture)) {
            GameState s=sample.state(); String before=ReplyStudy.stateHash(s);
            var bot=(RolloutBot)BotFactory.create(spec); bot.init(s,sample.me());
            long start=System.nanoTime(); var actions=bot.play(s,sample.me(),36);
            double elapsed=(System.nanoTime()-start)/1e6;
            var tr=new Rules.TurnResult(); Rules.expand(s,sample.me(),actions,new Pathing(s.size),tr.moves[sample.me()],tr);
            if(tr.hasError(sample.me()) || !tr.moves[sample.me()].skipped.isEmpty())
                throw new IllegalStateException("Illegal output at "+sample.game()+":"+s.turn);
            if(!before.equals(ReplyStudy.stateHash(s))) throw new IllegalStateException("Input mutated");
            Map<String,Object> row=new LinkedHashMap<>();
            row.put("game",sample.game()); row.put("turn",s.turn); row.put("me",sample.me());
            row.put("stateSha256",before); row.put("elapsedMs",elapsed); row.put("actions",Action.format(actions));
            row.put("routeQueries",bot.routeQueries); row.put("completedComparisons",bot.completedComparisons);
            row.put("simulatedTurns",bot.simulatedTurns); row.put("selectedRoute",bot.selectedRoute);
            row.put("selectedTarget",bot.selectedTarget); samples.add(row);
            total+=elapsed; max=Math.max(max,elapsed); totalComparisons+=bot.completedComparisons;
            totalNodes+=bot.simulatedTurns; if(bot.completedComparisons>0)compared++;
        }
        Map<String,Object> summary=new LinkedHashMap<>();
        summary.put("positions",samples.size()); summary.put("withCompletedComparison",compared);
        summary.put("comparisons",totalComparisons); summary.put("simulatedTurns",totalNodes);
        summary.put("meanMs",total/samples.size()); summary.put("maxMs",max);
        Map<String,Object> report=new LinkedHashMap<>(); report.put("spec",spec);
        report.put("params",BotFactory.configuration(spec)); report.put("summary",summary); report.put("samples",samples);
        report.put("javaVersion",System.getProperty("java.version"));
        report.put("classSha256",ReplyStudy.hash(Files.readAllBytes(Path.of("build/classes/java/main/btk/bot/current/RolloutBot.class"))));
        report.put("captureManifestSha256",ReplyStudy.hash(Files.readAllBytes(capture.resolve("manifest.json"))));
        report.put("method","Fresh instances, serial 36-ms search on ALL retained public states, init outside clock. "
                +"Legality and input-isolation audit; not strength or process timing evidence. No oracle actions supplied.");
        Files.createDirectories(out.toAbsolutePath().getParent());
        Files.writeString(out,ReplyStudy.JSON.toJson(report),StandardOpenOption.CREATE_NEW);
        System.out.println(ReplyStudy.JSON.toJson(summary));
    }
}
