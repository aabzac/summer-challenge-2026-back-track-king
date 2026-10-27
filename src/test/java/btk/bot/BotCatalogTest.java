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
        assertEquals("neural-v2 - Neural Outcome Fine-tuning", BotCatalog.displayName("neural-v2"));
        assertEquals("candidate", BotCatalog.describe("neural-v2").category());
        assertEquals("local_only", BotCatalog.describe("neural-v2").status());
        assertEquals(BotFactory.configuration("neural-v1"), BotFactory.configuration("neural-v2"));
        assertInstanceOf(btk.bot.neural.NeuralV2Bot.class, BotFactory.create("neural-v2"));
        assertEquals(0.0, BotFactory.parameters("neural-v2?NOISE=0").NOISE);
        assertEquals(0.1, BotFactory.parameters("neural-v2").NOISE);
        assertEquals("candidate", BotCatalog.describe("neural-v3").category());
        assertInstanceOf(btk.bot.neural.NeuralV3Bot.class, BotFactory.create("neural-v3"));
        assertEquals("2", BotFactory.configuration("neural-v3").get("NEURAL_DEADLINE_RESERVE_MS"));
        assertEquals(4, BotFactory.parameters("neural-v3?NEURAL_DEADLINE_RESERVE_MS=4").NEURAL_DEADLINE_RESERVE_MS);
        assertFalse(BotFactory.configuration("neural-v2").containsKey("NEURAL_DEADLINE_RESERVE_MS"));
        assertFalse(BotFactory.configuration("current").containsKey("NEURAL_DEADLINE_RESERVE_MS"));
        assertEquals("candidate", BotCatalog.describe("neural-v4").category());
        assertEquals("local_only", BotCatalog.describe("neural-v4").status());
        assertInstanceOf(btk.bot.neural.NeuralV4Bot.class, BotFactory.create("neural-v4"));
        assertEquals(BotFactory.configuration("neural-v3"), BotFactory.configuration("neural-v4"));
        assertEquals(4, BotFactory.parameters("neural-v4?NEURAL_DEADLINE_RESERVE_MS=4").NEURAL_DEADLINE_RESERVE_MS);
        assertEquals(0.1, BotFactory.parameters("neural-v4").NOISE);
        assertEquals("neural-v5 - Neural Exact Feature Optimization", BotCatalog.displayName("neural-v5"));
        assertEquals("candidate", BotCatalog.describe("neural-v5").category());
        assertEquals("local_only", BotCatalog.describe("neural-v5").status());
        assertInstanceOf(btk.bot.neural.NeuralV5Bot.class, BotFactory.create("neural-v5"));
        assertEquals(BotFactory.configuration("neural-v3"), BotFactory.configuration("neural-v5"));
        assertEquals(4, BotFactory.parameters("neural-v5?NEURAL_DEADLINE_RESERVE_MS=4").NEURAL_DEADLINE_RESERVE_MS);
        assertEquals(0.1, BotFactory.parameters("neural-v5").NOISE);
        assertEquals("neural-v6 - Neural Resumable Feature Paths", BotCatalog.displayName("neural-v6"));
        assertEquals("candidate", BotCatalog.describe("neural-v6").category());
        assertEquals("local_only", BotCatalog.describe("neural-v6").status());
        assertInstanceOf(btk.bot.neural.NeuralV6Bot.class, BotFactory.create("neural-v6"));
        assertEquals(BotFactory.configuration("neural-v5"), BotFactory.configuration("neural-v6"));
        assertEquals(4, BotFactory.parameters("neural-v6?NEURAL_DEADLINE_RESERVE_MS=4").NEURAL_DEADLINE_RESERVE_MS);
        assertEquals(0.1, BotFactory.parameters("neural-v6").NOISE);
        assertEquals("candidate", BotCatalog.describe("neural-v7").category());
        assertEquals("local_only", BotCatalog.describe("neural-v7").status());
        assertInstanceOf(btk.bot.neural.NeuralV7Bot.class, BotFactory.create("neural-v7"));
        assertTrue(BotFactory.parameters("neural-v7").USE_NEURAL_FALLBACK);
        assertFalse(BotFactory.parameters("neural-v7?USE_NEURAL_FALLBACK=0").USE_NEURAL_FALLBACK);
        var v7Configuration = new java.util.TreeMap<>(BotFactory.configuration("neural-v7"));
        assertEquals("true", v7Configuration.remove("USE_NEURAL_FALLBACK"));
        assertEquals("0", v7Configuration.remove("NEURAL_FALLBACK_MAX_INSTABILITY"));
        assertEquals(BotFactory.configuration("neural-v6"), v7Configuration);
        assertFalse(BotFactory.configuration("neural-v6").containsKey("USE_NEURAL_FALLBACK"));
        assertEquals("indrill-q-v1 - Indrill Action Values", BotCatalog.displayName("indrill-q-v1"));
        assertEquals("candidate", BotCatalog.describe("indrill-q-v1").category());
        assertEquals("local_only", BotCatalog.describe("indrill-q-v1").status());
        assertEquals("indrill-forum-v1 - Indrill Forum Readout", BotCatalog.displayName("indrill-forum-v1"));
        assertEquals("candidate", BotCatalog.describe("indrill-forum-v1").category());
        assertInstanceOf(btk.bot.forum.IndrillForumBot.class, BotFactory.create("indrill-forum-v1"));
        assertEquals("indrill-q-v2 - Learned Position Value", BotCatalog.displayName("indrill-q-v2"));
        assertEquals("candidate", BotCatalog.describe("indrill-q-v2").category());
        assertEquals("local_only", BotCatalog.describe("indrill-q-v2").status());
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("exp-does-not-exist"));
        // The six retained exp-* recipes (rejected on v05) left with their archived engine on 2026-10-27.
        assertThrows(IllegalArgumentException.class, () -> BotFactory.create("exp-reply-rerank"));
        assertTrue(BotCatalog.entries().stream().noneMatch(entry -> entry.category().equals("experiment")));
    }
}
