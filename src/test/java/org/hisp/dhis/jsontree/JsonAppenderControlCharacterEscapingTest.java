package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

/**
 * Regression tests for https://github.com/dhis2/json-tree/issues/103
 *
 * <p>Before PR #104, {@code JsonAppender.appendEscaped(int)} had an unreachable
 * {@code case -31}. All control characters below {@code 0x20} that did not have
 * an explicit short escape fell through to {@code default} and were written
 * literally to the JSON output, producing invalid JSON according to RFC 8259.
 *
 * <p>These tests should fail on v1.9.4 and earlier, and pass with the escaping
 * implementation from PR #104.
 */
class JsonAppenderControlCharacterEscapingTest {

    @Test
    void allControlCharactersBelow0x20AreEscaped() {
        StringBuilder input = new StringBuilder();
        StringBuilder expectedEscaped = new StringBuilder();

        for (int c = 0; c < 0x20; c++) {
            input.append((char) c);
            expectedEscaped.append(expectedJsonEscape((char) c));
        }

        String actual = jsonString(input.toString());

        assertEquals("{\"s\":\"" + expectedEscaped + "\"}", actual);
        assertRoundTrip(input.toString(), actual);
    }

    @Test
    void specificLowControlsAreEscapedAsUnicode() {
        // These are the characters that used to be emitted raw by the default
        // branch because case -31 was unreachable.
        assertControlEscaped('\u0000', "\\u0000"); // NUL
        assertControlEscaped('\u0001', "\\u0001"); // SOH
        assertControlEscaped('\u000B', "\\u000B"); // vertical tab
        assertControlEscaped('\u000E', "\\u000E"); // shift out
        assertControlEscaped('\u0010', "\\u0010"); // data link escape
        assertControlEscaped('\u001F', "\\u001F"); // unit separator
    }

    @Test
    void standardShortEscapesAreStillUsed() {
        assertControlEscaped('\b', "\\b");
        assertControlEscaped('\f', "\\f");
        assertControlEscaped('\n', "\\n");
        assertControlEscaped('\r', "\\r");
        assertControlEscaped('\t', "\\t");
    }

    @Test
    void controlsMixedWithOtherEscapesAreCorrect() {
        String input = "a\u0001\"b\nc\u001F\\d\t";
        String expected = "{\"s\":\"a\\u0001\\\"b\\nc\\u001F\\\\d\\t\"}";

        String actual = jsonString(input);

        assertEquals(expected, actual);
        assertRoundTrip(input, actual);
    }

    @Test
    void repeatedControlCharactersAreAllEscaped() {
        // Also exercises the bulk-append / buffer-flush path from PR #104.
        String input = "\u0001".repeat(5000);
        String expected = "{\"s\":\"" + "\\u0001".repeat(5000) + "\"}";

        String actual = jsonString(input);

        assertEquals(expected, actual);
        assertRoundTrip(input, actual);
    }

    private static void assertControlEscaped(char c, String expectedEscaped) {
        String actual = jsonString(String.valueOf(c));

        assertEquals("{\"s\":\"" + expectedEscaped + "\"}", actual);
        assertFalse(
                actual.contains(String.valueOf(c)),
                "Raw control character U+%04X must not appear in JSON output"
                        .formatted((int) c));
        assertRoundTrip(String.valueOf(c), actual);
    }

    private static String jsonString(String value) {
        return JsonBuilder.createObject(obj -> obj.addString("s", value))
                .getDeclaration()
                .toString();
    }

    private static void assertRoundTrip(String expectedValue, String json) {
        // JsonMixed.of uses a strict JSON parser. Before the fix, raw control
        // characters made this fail with a parse exception.
        JsonObject parsed = JsonMixed.of(json);
        assertEquals(expectedValue, parsed.getString("s").string());
    }

    private static String expectedJsonEscape(char c) {
        return switch (c) {
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> "\\u%04X".formatted((int) c);
        };
    }
}