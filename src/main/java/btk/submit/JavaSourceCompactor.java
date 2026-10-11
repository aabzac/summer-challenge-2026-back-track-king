package btk.submit;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Packaging-only Java 17 whitespace/comment removal. No names or literal spellings change. */
final class JavaSourceCompactor {
    private JavaSourceCompactor() {}

    private static final String DIGITS = "[0-9](?:[0-9_]*[0-9])?";
    private static final String HEX = "[0-9a-fA-F](?:[0-9a-fA-F_]*[0-9a-fA-F])?";
    private static final String EXP = "[eE][+-]?" + DIGITS;
    private static final Pattern[] NUMBERS = {
            Pattern.compile("0[xX](?:" + HEX + "\\.?|(?:" + HEX + ")?\\." + HEX + ")[pP][+-]?" + DIGITS + "[fFdD]?"),
            Pattern.compile(DIGITS + "\\.(?:" + DIGITS + ")?(?:" + EXP + ")?[fFdD]?"),
            Pattern.compile("\\." + DIGITS + "(?:" + EXP + ")?[fFdD]?"),
            Pattern.compile(DIGITS + EXP + "[fFdD]?"),
            Pattern.compile(DIGITS + "(?:" + EXP + ")?[fFdD]"),
            Pattern.compile("0[xX]" + HEX + "[lL]?"),
            Pattern.compile("0[bB][01](?:[01_]*[01])?[lL]?"),
            Pattern.compile("0(?:_*[0-7])+[lL]?"),
            Pattern.compile("(?:0|[1-9](?:[0-9_]*[0-9])?)[lL]?")
    };
    private static final String[] SYMBOLS = {
            ">>>=", "<<=", ">>=", ">>>", "...", "==", "<=", ">=", "!=", "&&", "||", "++", "--",
            "<<", ">>", "+=", "-=", "*=", "/=", "&=", "|=", "^=", "%=", "->", "::",
            "(", ")", "{", "}", "[", "]", ";", ",", ".", "@", "=", ">", "<", "!", "~", "?", ":",
            "+", "-", "*", "/", "&", "|", "^", "%"
    };

    static String compact(String source) {
        List<String> original = tokens(source);
        StringBuilder out = new StringBuilder(source.length());
        String previous = null;
        for (String token : original) {
            if (previous != null && needsSeparator(previous, token)) out.append(' ');
            out.append(token);
            previous = token;
        }
        out.append('\n');
        String result = out.toString();
        // This also fails closed if a future token combination spans more than one boundary.
        if (!original.equals(tokens(result)))
            throw new IllegalArgumentException("Compaction changed the Java token sequence");
        return result;
    }

    private static boolean needsSeparator(String a, String b) {
        // Three individually separated dots could become one ellipsis across two boundaries.
        if (a.equals(".") && b.equals(".")) return true;
        try {
            List<String> together = tokens(a + b);
            return together.size() != 2 || !together.get(0).equals(a) || !together.get(1).equals(b);
        } catch (IllegalArgumentException unsupported) {
            // In particular, joining slash and star must not start an unterminated comment.
            return true;
        }
    }

    /** Exact raw token spellings; comments and Java lexical whitespace alone are discarded. */
    static List<String> tokens(String source) {
        // Java translates these before tokenization, even in comments. Do not approximate the
        // eligibility/parity rules or allow removing a separator to alter that translation.
        if (source.contains("\\" + "u"))
            throw new IllegalArgumentException("Raw Unicode escape introducers are not supported");
        List<String> result = new ArrayList<>();
        for (int at = 0; at < source.length();) {
            char c = source.charAt(at);
            if (c == ' ' || c == '\t' || c == '\f' || c == '\r' || c == '\n') { at++; continue; }
            if (source.startsWith("//", at)) {
                at += 2;
                while (at < source.length() && source.charAt(at) != '\r' && source.charAt(at) != '\n') at++;
                continue;
            }
            if (source.startsWith("/*", at)) {
                int end = source.indexOf("*/", at + 2);
                if (end < 0) throw unsupported(at, "Unterminated comment");
                at = end + 2; continue;
            }
            int end;
            if (c == '\'' || c == '"') {
                if (source.startsWith("\"\"\"", at)) throw unsupported(at, "Text blocks are not supported");
                end = literalEnd(source, at, c);
            } else if (Character.isJavaIdentifierStart(source.codePointAt(at))) {
                end = at + Character.charCount(source.codePointAt(at));
                while (end < source.length() && Character.isJavaIdentifierPart(source.codePointAt(end)))
                    end += Character.charCount(source.codePointAt(end));
            } else if (c >= '0' && c <= '9' || c == '.' && at + 1 < source.length()
                    && source.charAt(at + 1) >= '0' && source.charAt(at + 1) <= '9') {
                end = at;
                for (Pattern number : NUMBERS) {
                    var match = number.matcher(source).region(at, source.length());
                    if (match.lookingAt()) end = Math.max(end, match.end());
                }
                if (end == at) throw unsupported(at, "Unsupported number");
            } else {
                end = at;
                for (String symbol : SYMBOLS) if (source.startsWith(symbol, at)) {
                    end = at + symbol.length(); break;
                }
                if (end == at) throw unsupported(at, "Unsupported Java lexeme");
            }
            result.add(source.substring(at, end)); at = end;
        }
        return result;
    }

    private static int literalEnd(String source, int start, char quote) {
        int units = 0;
        for (int at = start + 1; at < source.length();) {
            char c = source.charAt(at++);
            if (c == quote) {
                if (quote == '\'' && units != 1) throw unsupported(start, "Invalid character literal");
                return at;
            }
            if (c == '\n' || c == '\r') throw unsupported(start, "Line break in ordinary literal");
            if (c == '\\') {
                if (at >= source.length()) throw unsupported(start, "Unterminated literal escape");
                char escaped = source.charAt(at++);
                if (escaped >= '0' && escaped <= '7') {
                    int limit = escaped <= '3' ? 2 : 1;
                    while (limit-- > 0 && at < source.length() && source.charAt(at) >= '0'
                            && source.charAt(at) <= '7') at++;
                } else if ("btnfrs\"'\\".indexOf(escaped) < 0)
                    throw unsupported(at - 1, "Unsupported literal escape");
            }
            units++;
        }
        throw unsupported(start, "Unterminated literal");
    }

    private static IllegalArgumentException unsupported(int at, String reason) {
        return new IllegalArgumentException(reason + " at source offset " + at);
    }
}
