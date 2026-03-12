package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Some tests to see if the {@link JsonPointer#decode()} is according to spec.
 *
 * @author Jan Bernitt (with AI suggestions for the cases to test)
 */
class JsonPointerTest {

    @Test
    void rootPointer_shouldReturnRootPath() {
        assertEquals(JsonPath.SELF, new JsonPointer("").decode());
    }

    @Test
    void singleSlash_shouldReturnSingleEmptySegment() {
        // One empty segment is represented by a single dot (or empty string?).
        // Using ".." would be two empty segments; "." would be one empty segment.
        // We choose "." as the representation for a path with one empty segment.
        assertEquals(JsonPath.of(""), new JsonPointer("/").decode());
    }

    @Test
    void doubleSlash_shouldReturnTwoEmptySegments() {
        // Two consecutive dots represent ["", ""]
        assertEquals(JsonPath.of("", ""), new JsonPointer("//").decode());
    }

    @Test
    void simpleToken_shouldReturnSingleSegment() {
        assertEquals(JsonPath.of("foo"), new JsonPointer("/foo").decode());
    }

    @Test
    void multipleTokens_shouldReturnMultipleSegments() {
        assertEquals(JsonPath.of("foo.bar"), new JsonPointer("/foo/bar").decode());
    }

    @Test
    void emptySegmentBetweenTokens_shouldPreserveEmptySegment() {
        assertEquals(JsonPath.of("foo", "", "bar"), new JsonPointer("/foo//bar").decode());
    }

    @Test
    void trailingEmptySegment_shouldIncludeEmptySegment() {
        assertEquals(JsonPath.of("foo","bar", ""), new JsonPointer("/foo/bar/").decode());
    }

    @Test
    void escapedSlash_shouldDecodeToSlash() {
        assertEquals(JsonPath.of("a/b"), new JsonPointer("/a~1b").decode());
    }

    @Test
    void escapedTilde_shouldDecodeToTilde() {
        assertEquals(JsonPath.of("m~n"), new JsonPointer("/m~0n").decode());
    }

    @Test
    void multipleEscapes_shouldDecodeCorrectly() {
        // ~0 → ~, ~1 → /, so segment becomes "~/"
        assertEquals(JsonPath.of("~/"), new JsonPointer("/~0~1").decode());
    }

    @Test
    void escapedSequenceFollowedByPlainText_shouldDecode() {
        // "~0" → "~", then "1" remains → "~1"
        assertEquals(JsonPath.of("~1"), new JsonPointer("/~01").decode());
    }

    @Test
    void escapedSequenceAtEnd_shouldDecode() {
        assertEquals(JsonPath.of("foo~"), new JsonPointer("/foo~0").decode());
    }

    @Test
    void multipleEscapedSequences_shouldDecode() {
        // ~ → / → ~ → /  => "~/~/"
        assertEquals(JsonPath.of("~/~/"), new JsonPointer("/~0~1~0~1").decode());
    }

    @Test
    void mixedPlainAndEscaped_shouldDecode() {
        assertEquals(JsonPath.of("foo~bar/baz"), new JsonPointer("/foo~0bar~1baz").decode());
    }

    @Test
    void complexExample_fromRfc() {
        // Example from RFC 6901: "/a~1b" → ["a/b"]
        assertEquals(JsonPath.of("a/b"), new JsonPointer("/a~1b").decode());
    }
}