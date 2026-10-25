package btk.submit;

import btk.bot.BotFactory;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** prune-v1 (2026-10-25): what the current rollout artifact no longer carries, and that it stays self-consistent. */
class PruneV1SubmissionTest {
    private static final Pattern DECLARED = Pattern.compile("public (?:boolean|int|double|String) ([A-Z_0-9]+)=");
    private static final Pattern EMBEDDED = Pattern.compile("new Params\\(\"([^\"]*)\"\\)");

    @Test void currentArtifactShipsNeitherTheLocalOnlyFieldsNorTheUnusedClassesAndStaysSelfConsistent() throws Exception {
        Set<String> localOnly = FileMerger.localOnlyParams();
        assertTrue(localOnly.size() >= 80 && localOnly.contains("USE_ROLLOUT_SPREAD") && localOnly.contains("W_OPP")
                && localOnly.contains("HEURISTIC_MAX_ROUTES") && !localOnly.contains("ROLLOUT_WIDTH")
                && !localOnly.contains("USE_HEURISTIC_POLICY") && !localOnly.contains("NOISE"), localOnly.toString());
        String current = FileMerger.merge("current");
        FileMerger.checkSourceSize(current);
        int paramsStart = current.indexOf("class Params");
        assertTrue(paramsStart > 0);
        Set<String> declared = new TreeSet<>();
        Matcher m = DECLARED.matcher(current);
        while (m.find()) declared.add(m.group(1));
        for (String field : localOnly) assertFalse(declared.contains(field), field + " must not ship");
        for (String kept : List.of("ROLLOUT_WIDTH", "USE_ROLLOUT_QUICK_ROUTE", "USE_ROLLOUT_NETWORK_AFTER_BASE", "USE_BOSS_LITE",
                "UNSTABLE_COST", "USE_DISRUPT", "NOISE", "SEED", "USE_HEURISTIC_POLICY", "ISOLATED_ROUTE_WARMUP"))
            assertTrue(declared.contains(kept), kept);
        Matcher e = EMBEDDED.matcher(current);
        assertTrue(e.find(), "embedded configuration");
        for (String item : e.group(1).split(",")) {
            String key = item.substring(0, item.indexOf('='));
            assertTrue(declared.contains(key), "embedded " + key + " must be a shipped field");
            assertFalse(localOnly.contains(key), key);
        }
        assertFalse(current.contains("class NetworkChords"));
        assertFalse(current.contains("class TacticalPathing"));
        assertFalse(current.contains("addSpreadRoots") || current.contains("addChordRoots") || current.contains("reconsider(")
                || current.contains("observeLive") || current.contains("quickBuildTurns") || current.contains("captureDependencies"));
        assertTrue(current.contains("class NetworkCells") && current.contains("searchNetworkAfterBase") && current.contains("chooseBossLite"));
        // The resolved configuration (and therefore the arena identity's config hash) is unchanged by the prune.
        var configuration = new TreeMap<>(BotFactory.configuration("current"));
        assertEquals(130, configuration.size(), "the full configuration still carries every field");
        for (String flag : BotFactory.PRUNED_ROLLOUT_FLAGS) assertEquals("false", configuration.get(flag), flag);
        assertEquals("f0e507ac336a12610c7f14c3b20c3fa57527ced3a34bceb88826a4bf01d127fb", FileMerger.configSha256("current"),
                "the arena artifact's config hash (D = v10)");
        // The frozen export keeps everything.
        String frozen = FileMerger.merge("v10");
        assertTrue(frozen.contains("class NetworkChords") && frozen.contains("class TacticalPathing") && frozen.contains("addSpreadRoots"));
        assertTrue(frozen.contains("USE_ROLLOUT_SPREAD=false") && frozen.contains("W_OPP="));
    }
}
