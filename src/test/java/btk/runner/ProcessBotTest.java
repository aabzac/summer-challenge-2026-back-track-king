package btk.runner;

import btk.core.GameState;
import btk.core.Protocol;
import org.junit.jupiter.api.Test;

import java.io.Writer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class ProcessBotTest {
    private static final long MS = 1_000_000;

    /** Simulate write cost, reader arrival and delayed consumer scheduling independently. */
    private static final class Transport extends LinkedBlockingQueue<ProcessBot.Response> {
        final AtomicLong clock = new AtomicLong(1000 * MS);
        long sendMs, receiveMs, consumeMs, requestedNs;
        String line = "WAIT";
        boolean absent;

        Transport(long sendMs, long receiveMs, long consumeMs) {
            this.sendMs = sendMs; this.receiveMs = receiveMs; this.consumeMs = consumeMs;
        }

        @Override public ProcessBot.Response poll(long timeout, TimeUnit unit) {
            requestedNs = unit.toNanos(timeout);
            clock.set((1000 + consumeMs) * MS);
            return absent ? null : new ProcessBot.Response(line, (1000 + receiveMs) * MS);
        }

        ProcessBot bot() {
            Writer writer = new Writer() {
                @Override public void write(char[] text, int offset, int length) {}
                @Override public void flush() { clock.set((1000 + sendMs) * MS); }
                @Override public void close() {}
            };
            return new ProcessBot(writer, this, clock::get);
        }
    }

    @Test void timelyArrivalSurvivesLateConsumerWakeup() {
        Transport io = new Transport(3, 35, 180);
        ProcessBot bot = io.bot();
        assertDoesNotThrow(() -> bot.play(state(), 0, 50));
        assertEquals(47 * MS, io.requestedNs);
        assertEquals(0, bot.timeouts);
        assertEquals("OK", bot.timings.get(0).status());
        assertEquals(35.0, bot.timings.get(0).responseMs());
        assertEquals(180.0, bot.timings.get(0).consumerMs());
    }

    @Test void queuedLateLineIsRejectedEvenWhenPollReturnsIt() {
        Transport io = new Transport(3, 180, 180);
        ProcessBot bot = io.bot();
        assertThrows(IllegalStateException.class, () -> bot.play(state(), 0, 50));
        assertEquals(1, bot.timeouts);
        assertEquals("TIMEOUT", bot.timings.get(0).status());
        assertThrows(IllegalStateException.class, () -> bot.play(state(), 0, 50));
        assertEquals(1, bot.timings.size()); // late output cannot become the next turn's answer
    }

    @Test void inputWritingConsumesTheSameDeadline() {
        Transport io = new Transport(60, 65, 65);
        ProcessBot bot = io.bot();
        assertThrows(IllegalStateException.class, () -> bot.play(state(), 0, 50));
        assertEquals(0, io.requestedNs);
        assertEquals(1, bot.timeouts);
    }

    @Test void explicitGraceIsReportedAsNominalOverrun() {
        Transport io = new Transport(2, 60, 65);
        ProcessBot bot = io.bot(); bot.graceMs = 30;
        assertDoesNotThrow(() -> bot.play(state(), 0, 50));
        assertEquals(1, bot.nominalOverruns);
        assertEquals(0, bot.timeouts);
        assertEquals("GRACE", bot.timings.get(0).status());
    }

    @Test void noAnswerIsTerminalAndEofIsNotTimeout() {
        Transport missing = new Transport(1, 0, 50); missing.absent = true;
        ProcessBot bot = missing.bot();
        assertThrows(IllegalStateException.class, () -> bot.play(state(), 0, 50));
        assertEquals(1, bot.timeouts);
        Transport eof = new Transport(1, 3, 4); eof.line = null;
        ProcessBot ended = eof.bot();
        assertThrows(IllegalStateException.class, () -> ended.play(state(), 0, 50));
        assertEquals(0, ended.timeouts);
        assertEquals("EOF", ended.timings.get(0).status());
    }

    @Test void deadlineBoundaryAndUnsolicitedLine() {
        ProcessBot exact = new Transport(1, 50, 51).bot();
        assertDoesNotThrow(() -> exact.play(state(), 0, 50));
        ProcessBot early = new Transport(1, -1, 2).bot();
        assertThrows(IllegalStateException.class, () -> early.play(state(), 0, 50));
        assertEquals("UNSOLICITED", early.timings.get(0).status());
    }

    @Test void realChildTimeoutEndsSession() throws Exception {
        ProcessBot bot = child("slow");
        try {
            GameState state = state(); bot.init(state, 0);
            assertDoesNotThrow(() -> bot.play(state, 0, 5000)); // startup handshake, not timing evidence
            state.turn = 1;
            assertThrows(IllegalStateException.class, () -> bot.play(state, 0, 10));
            assertEquals(1, bot.timeouts);
            assertThrows(IllegalStateException.class, () -> bot.play(state, 0, 5000));
        } finally { bot.close(); }
    }

    @Test void realChildEofWakesReader() throws Exception {
        ProcessBot bot = child("exit");
        try {
            GameState state = state(); bot.init(state, 0);
            assertThrows(IllegalStateException.class, () -> bot.play(state, 0, 5000));
            assertEquals(0, bot.timeouts);
            assertEquals("EOF", bot.timings.get(0).status());
        } finally { bot.close(); }
    }

    private static ProcessBot child(String mode) throws Exception {
        String classpath = Path.of(ProcessBotTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + java.io.File.pathSeparator
                + Path.of(Protocol.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return new ProcessBot("fixture", List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-cp", classpath, Child.class.getName(), mode));
    }

    public static class Child {
        public static void main(String[] args) throws Exception {
            var reader = new Protocol.TokenReader(System.in);
            var init = Protocol.readInit(reader);
            Protocol.readTurn(reader, init.state, init.myId);
            if (args[0].equals("exit")) return;
            System.out.println("WAIT"); System.out.flush();
            Protocol.readTurn(reader, init.state, init.myId);
            Thread.sleep(500);
            System.out.println("WAIT"); System.out.flush();
        }
    }

    private static GameState state() {
        return new GameState(3, 1, new int[]{0, 1, 2}, new int[3],
                new int[]{0, 2}, new int[]{0, 0}, new int[][]{{1}, {}});
    }
}
