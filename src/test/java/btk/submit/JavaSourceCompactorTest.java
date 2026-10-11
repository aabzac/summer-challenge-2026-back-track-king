package btk.submit;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class JavaSourceCompactorTest {
    @Test void preservesLiteralContentsAndEscapesWhileRemovingOnlyActualComments() {
        String source = "class Example { String text = \"// keep /* keep */ \\\"quoted\\\" \\\\ \\s\"; "
                + "char quote = '\\\''; char octal = '\\123'; /* removed */ int x = 1; // removed\r\n}";
        String compact = JavaSourceCompactor.compact(source);
        assertTrue(compact.contains("\"// keep /* keep */ \\\"quoted\\\" \\\\ \\s\""));
        assertTrue(compact.contains("'\\\''")); assertTrue(compact.contains("'\\123'"));
        assertFalse(compact.contains("removed"));
        assertEquals(JavaSourceCompactor.tokens(source), JavaSourceCompactor.tokens(compact));
    }

    @Test void preventsOperatorIdentifierNumberAndCommentBoundaryMerges() {
        String source = "a + + b; a - - b; a / * b; a / / b; a > > > b; a < < b; "
                + "a & & b; a : : b; a - > b; a . . . b; int name; 1 e2; 1 . 0; "
                + "var " + (char) 0x03bb + " = 0x1.fp2 + .5e+2 + 1.e-3 + 0b101_001L + 077 + 1_000L;";
        String compact = JavaSourceCompactor.compact(source);
        assertEquals(JavaSourceCompactor.tokens(source), JavaSourceCompactor.tokens(compact));
        for (String protectedBoundary : List.of("+ +", "- -", "/ *", "/ /", "> >", "< <", "& &", ": :", "- >", ". .", "int name", "1 e2", "1 ."))
            assertTrue(compact.contains(protectedBoundary), protectedBoundary);
        assertTrue(compact.contains("0x1.fp2")); assertTrue(compact.contains("0b101_001L"));
    }

    @Test void acceptsEveryNumericLiteralFamilyWithoutChangingSpelling() {
        List<String> expected = List.of("0", "7", "1_000L", "07_7", "0xCA_FE", "0X1L", "0b10_01L",
                "1.", ".5", "1.25e-3D", "1e+4", "1f", "0x1.fp+3", "0X.8P-1f");
        String source = String.join(" ; ", expected);
        assertEquals(expected, JavaSourceCompactor.tokens(JavaSourceCompactor.compact(source)).stream()
                .filter(token -> !token.equals(";")).toList());
    }

    @Test void failsClosedBeforePrelexicalUnicodeTranslationAndUnsupportedSyntax() {
        String rawEscape = "\\" + "u0061";
        for (String source : List.of("int " + rawEscape + ";", "// " + rawEscape + "\nint a;",
                "String a=\"\\" + rawEscape + "\";", "String text=\"\"\"\nhello\n\"\"\";",
                "/* unfinished", "String s=\"unfinished", "char c='ab';", "String s=\"\\q\";", "`"))
            assertThrows(IllegalArgumentException.class, () -> JavaSourceCompactor.compact(source), source);
    }
}
