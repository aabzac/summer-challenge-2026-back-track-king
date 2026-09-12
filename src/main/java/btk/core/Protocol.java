package btk.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * CodinGame stdin/stdout protocol: fast token reader for the bot side and writers for the
 * referee side (so an external process sees exactly what CG would send).
 */
public final class Protocol {

    private Protocol() {}

    /** Result of parsing the initialization block. */
    public static final class Init {
        public int myId;
        public GameState state;
    }

    /** Per-turn data that is not part of {@link GameState} but useful for cross-checks. */
    public static final class TurnInfo {
        public int myScore, foeScore;
        /** Server-reported "a-b" pairs per cell (raw), or null when "x". */
        public String[] activeRaw;
    }

    // ------------------------------------------------------------------ reading

    public static Init readInit(TokenReader in) throws IOException {
        Init init = new Init();
        init.myId = in.nextInt();
        int width = in.nextInt();
        int height = in.nextInt();
        int size = width * height;
        int[] regionId = new int[size];
        int[] terrain = new int[size];
        for (int i = 0; i < size; i++) {
            regionId[i] = in.nextInt();
            terrain[i] = in.nextInt();
        }
        int townCount = in.nextInt();
        int[] ids = new int[townCount];
        int[] tx = new int[townCount];
        int[] ty = new int[townCount];
        String[] desiredRaw = new String[townCount];
        for (int i = 0; i < townCount; i++) {
            ids[i] = in.nextInt();
            tx[i] = in.nextInt();
            ty[i] = in.nextInt();
            desiredRaw[i] = in.next();
        }
        // Towns are indexed by id; the protocol lists them in id order but do not rely on it.
        int[] townX = new int[townCount];
        int[] townY = new int[townCount];
        int[][] desired = new int[townCount][];
        for (int i = 0; i < townCount; i++) {
            int id = ids[i];
            townX[id] = tx[i];
            townY[id] = ty[i];
            desired[id] = parseIdList(desiredRaw[i]);
        }
        init.state = new GameState(width, height, regionId, terrain, townX, townY, desired);
        return init;
    }

    /** Reads one turn block into {@code s} (owner/instability/inked) and returns extra info. */
    public static TurnInfo readTurn(TokenReader in, GameState s, int myId) throws IOException {
        TurnInfo ti = new TurnInfo();
        ti.myScore = in.nextInt();
        ti.foeScore = in.nextInt();
        s.score[myId] = ti.myScore;
        s.score[1 - myId] = ti.foeScore;
        ti.activeRaw = new String[s.size];
        for (int i = 0; i < s.size; i++) {
            s.owner[i] = in.nextInt();
            s.instability[i] = in.nextInt();
            String inked = in.next();
            s.inked[i] = !"0".equals(inked) && !"false".equals(inked);
            String active = in.next();
            ti.activeRaw[i] = "x".equals(active) ? null : active;
        }
        return ti;
    }

    public static int[] parseIdList(String raw) {
        if (raw == null || raw.equals("x") || raw.isEmpty()) return new int[0];
        String[] parts = raw.split(",");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i].trim());
        return out;
    }

    // ------------------------------------------------------------------ writing

    public static String initBlock(GameState s, int playerId) {
        StringBuilder sb = new StringBuilder(s.size * 6 + 64);
        sb.append(playerId).append('\n');
        sb.append(s.width).append(' ').append(s.height).append('\n');
        for (int i = 0; i < s.size; i++) {
            sb.append(s.regionId[i]).append(' ').append(s.terrain[i]).append('\n');
        }
        sb.append(s.townCount).append('\n');
        for (int t = 0; t < s.townCount; t++) {
            sb.append(t).append(' ').append(s.townX[t]).append(' ').append(s.townY[t]).append(' ');
            if (s.desired[t].length == 0) {
                sb.append('x');
            } else {
                for (int i = 0; i < s.desired[t].length; i++) {
                    if (i > 0) sb.append(',');
                    sb.append(s.desired[t][i]);
                }
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    public static String turnBlock(GameState s, int playerId) {
        String[] active = activeStrings(s);
        StringBuilder sb = new StringBuilder(s.size * 12 + 32);
        sb.append(s.score[playerId]).append(' ').append(s.score[1 - playerId]).append('\n');
        for (int i = 0; i < s.size; i++) {
            sb.append(s.owner[i]).append(' ')
              .append(s.instability[i]).append(' ')
              .append(s.inked[i] ? '1' : '0').append(' ')
              .append(active[i] == null ? "x" : active[i]).append('\n');
        }
        return sb.toString();
    }

    /** "a-b,c-d" per cell in pair order (as CG appears to do), null when none. */
    public static String[] activeStrings(GameState s) {
        List<StringBuilder> per = new ArrayList<>(s.size);
        for (int i = 0; i < s.size; i++) per.add(null);
        for (int p = 0; p < s.pairCount; p++) {
            int[] path = s.pairPath[p];
            if (path == null) continue;
            for (int i = 0; i < s.pairPathLen[p]; i++) {
                int c = path[i];
                StringBuilder sb = per.get(c);
                if (sb == null) {
                    sb = new StringBuilder();
                    per.set(c, sb);
                } else {
                    sb.append(',');
                }
                sb.append(s.pairFrom[p]).append('-').append(s.pairTo[p]);
            }
        }
        String[] out = new String[s.size];
        for (int i = 0; i < s.size; i++) out[i] = per.get(i) == null ? null : per.get(i).toString();
        return out;
    }

    // ------------------------------------------------------------------ token reader

    /** Minimal whitespace-token reader over an InputStream; far faster than Scanner. */
    public static final class TokenReader {
        private final InputStream in;
        private final byte[] buf = new byte[1 << 16];
        private int len, pos;
        private final byte[] tok = new byte[1 << 12];

        public TokenReader(InputStream in) {
            this.in = in;
        }

        private int read() throws IOException {
            if (pos == len) {
                len = in.read(buf, 0, buf.length);
                pos = 0;
                if (len <= 0) {
                    len = 0;
                    return -1;
                }
            }
            return buf[pos++];
        }

        /** Returns next token or null at EOF. */
        public String next() throws IOException {
            int b = read();
            while (b != -1 && b <= ' ') b = read();
            if (b == -1) return null;
            int n = 0;
            while (b != -1 && b > ' ') {
                if (n < tok.length) tok[n++] = (byte) b;
                b = read();
            }
            return new String(tok, 0, n);
        }

        public int nextInt() throws IOException {
            int b = read();
            while (b != -1 && b <= ' ') b = read();
            if (b == -1) throw new IOException("EOF");
            boolean neg = false;
            if (b == '-') {
                neg = true;
                b = read();
            }
            int v = 0;
            while (b >= '0' && b <= '9') {
                v = v * 10 + (b - '0');
                b = read();
            }
            return neg ? -v : v;
        }

        /** True if more non-whitespace input exists (blocks until data or EOF). */
        public boolean hasNext() throws IOException {
            while (true) {
                if (pos < len) {
                    if (buf[pos] > ' ') return true;
                    pos++;
                    continue;
                }
                len = in.read(buf, 0, buf.length);
                pos = 0;
                if (len <= 0) {
                    len = 0;
                    return false;
                }
            }
        }
    }

    /** Convenience for tests: parse a whole init block from a string. */
    public static Init parseInit(String text) {
        try {
            return readInit(new TokenReader(new java.io.ByteArrayInputStream(
                    text.getBytes(java.nio.charset.StandardCharsets.US_ASCII))));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
