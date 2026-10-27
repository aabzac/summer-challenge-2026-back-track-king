package btk.bot;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BotCatalogTest {
    @Test void labelsDoNotRenameExistingIdsOrUnknownPlayers() {
        assertEquals("v05 - Disrupt Reserve", BotCatalog.displayName("v05"));
        assertEquals("v06 - Simultaneous Rollout", BotCatalog.displayName("v06"));
        assertEquals("v07 - Network After Base", BotCatalog.displayName("v07"));
        assertEquals("v08 - Protected Opening", BotCatalog.displayName("v08"));
        assertEquals("v09 - Never Idle", BotCatalog.displayName("v09"));
        assertEquals("v10 - Boss Model", BotCatalog.displayName("v10"));
        assertEquals("v11 - Root Target", BotCatalog.displayName("v11"));
        assertEquals("v12 - Exposure Income", BotCatalog.displayName("v12"));
        assertEquals("v13 - Bypass Routes", BotCatalog.displayName("v13"));
        assertEquals("selected", BotCatalog.describe("v13").status());
        assertEquals("historical", BotCatalog.describe("v12").status(), "v12 became historical at the v13 promotion (2026-10-27)");
        assertEquals("historical", BotCatalog.describe("v11").status(), "v11 became historical at the v12 promotion (2026-10-25)");
        assertEquals("historical", BotCatalog.describe("v10").status(), "v10 became historical at the v11 promotion (2026-10-25)");
        assertEquals("historical", BotCatalog.describe("v09").status(), "v09 became historical at the v10 promotion (2026-10-23)");
        assertEquals("historical", BotCatalog.describe("v08").status());
        assertEquals("historical", BotCatalog.describe("v07").status());
        assertEquals("historical", BotCatalog.describe("v06").status());
        assertEquals("historical", BotCatalog.describe("v05").status());
        assertEquals(java.util.List.of("v13"), BotCatalog.entries().stream()
                .filter(entry -> entry.status().equals("selected")).map(BotCatalog.Entry::id).toList());
        assertEquals("v04 - Network Extension [NOISE=0.2]", BotCatalog.displayName("v04?NOISE=0.2"));
        assertEquals("external player", BotCatalog.displayName("external player"));
        assertEquals("v03", BotFactory.create("v03").name().split("\\?", 2)[0]);
        assertEquals("working", BotCatalog.describe("current").category());
        assertEquals("local_only", BotCatalog.describe("pressure-v1").status());
        assertEquals("neural-v6 - Neural Resumable Feature Paths", BotCatalog.displayName("neural-v6"));
        assertEquals("candidate", BotCatalog.describe("neural-v6").category());
        assertEquals("local_only", BotCatalog.describe("neural-v6").status());
        assertInstanceOf(btk.bot.neural.NeuralV6Bot.class, BotFactory.create("neural-v6"));
        assertEquals("2", BotFactory.configuration("neural-v6").get("NEURAL_DEADLINE_RESERVE_MS"));
        assertEquals(4, BotFactory.parameters("neural-v6?NEURAL_DEADLINE_RESERVE_MS=4").NEURAL_DEADLINE_RESERVE_MS);
        assertEquals(0.1, BotFactory.parameters("neural-v6").NOISE);
        assertFalse(BotFactory.configuration("current").containsKey("NEURAL_DEADLINE_RESERVE_MS"));
        assertFalse(BotFactory.configuration("neural-v6").containsKey("USE_NEURAL_FALLBACK"));
        // The closed neural (v1-v5, v7), spatial-ppo, indrill and leader lines left on 2026-10-27 (repo-cleanup tier 3).
        for (String gone : new String[]{"neural-v1", "neural-v5", "neural-v7", "spatial-ppo-v1", "spatial-ppo-v10", "indrill-q-v2", "indrill-forum-v1", "leader-v1"})
            assertThrows(IllegalArgumentException.class, () -> BotFactory.create(gone), gone);
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("exp-does-not-exist"));
        // The six retained exp-* recipes (rejected on v05) left with their archived engine on 2026-10-27.
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("exp-reply-rerank"));
        assertTrue(BotCatalog.entries().stream().noneMatch(entry -> entry.category().equals("experiment")));
    }
}
