package btk.core;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {

    @Test
    void initAndTurnRoundTrip() throws Exception {
        GameState s = TestMaps.of(new String[]{
                "0.~",
                "^.1"}, new int[][]{{1}, {0}});
        TestMaps.track(s, 0, 1, 0);
        TestMaps.track(s, 1, 1, 1);
        Pathing p = new Pathing(s.size);
        Rules.updateConnections(s, p);
        s.score[0] = 7;
        s.score[1] = 3;

        String text = Protocol.initBlock(s, 1) + Protocol.turnBlock(s, 1);
        Protocol.TokenReader r = new Protocol.TokenReader(
                new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII)));
        Protocol.Init init = Protocol.readInit(r);
        assertEquals(1, init.myId);
        GameState t = init.state;
        assertEquals(3, t.width);
        assertEquals(2, t.height);
        assertArrayEquals(s.terrain, t.terrain);
        assertArrayEquals(new int[]{1}, t.desired[0]);
        assertArrayEquals(new int[]{0}, t.desired[1]);
        Protocol.TurnInfo ti = Protocol.readTurn(r, t, 1);
        assertEquals(3, ti.myScore);
        assertEquals(7, ti.foeScore);
        assertEquals(7, t.score[0]);
        assertArrayEquals(s.owner, t.owner);
        assertEquals("0-1,1-0", ti.activeRaw[t.cell(1, 0)]);
        assertNull(ti.activeRaw[t.cell(2, 0)]);
        assertFalse(r.hasNext());
    }

    @Test
    void tokenReaderHandlesNegativeAndStrings() throws Exception {
        Protocol.TokenReader r = new Protocol.TokenReader(new ByteArrayInputStream(
                "-1 0 0 x\n2 5 1 1-2,3-4\n".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(-1, r.nextInt());
        assertEquals(0, r.nextInt());
        assertEquals(0, r.nextInt());
        assertEquals("x", r.next());
        assertEquals(2, r.nextInt());
        assertEquals(5, r.nextInt());
        assertEquals("1", r.next());
        assertEquals("1-2,3-4", r.next());
        assertNull(r.next());
    }
}
