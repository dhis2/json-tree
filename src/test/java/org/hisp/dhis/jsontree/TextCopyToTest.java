package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for {@link Text#copyTo(char[], int)} and
 * {@link Text#copyTo(char[], int, int, int)}.
 *
 * <p>These can be added to {@code TextTest} alongside the other Text method
 * tests; presented as a standalone class so it can be dropped in wherever
 * convenient.
 */
class TextCopyToTest {

    // ===== copyTo(dest, offset) =====

    @Test
    void copyTo_fullText_atZero() {
        char[] dest = new char[5];
        Text.of("hello").copyTo(dest, 0);
        assertEquals("hello", new String(dest));
    }

    @Test
    void copyTo_fullText_atNonZeroOffset() {
        char[] dest = "xxxxxxxxxx".toCharArray();
        Text.of("hello").copyTo(dest, 5);
        assertEquals("xxxxxhello", new String(dest));
    }

    @Test
    void copyTo_emptyText_isNoOp() {
        char[] dest = "abc".toCharArray();
        Text.of("").copyTo(dest, 1);
        assertEquals("abc", new String(dest));
    }

    @Test
    void copyTo_singleChar() {
        char[] dest = new char[1];
        Text.of("a").copyTo(dest, 0);
        assertEquals("a", new String(dest));
    }

    @Test
    void copyTo_sliceView_respectsSliceBounds() {
        char[] dest = new char[3];
        Text.of("hello").slice(1, 4).copyTo(dest, 0);
        assertEquals("ell", new String(dest));
    }

    @Test
    void copyTo_consecutiveAppends() {
        char[] dest = new char[6];
        Text.of("abc").copyTo(dest, 0);
        Text.of("def").copyTo(dest, 3);
        assertEquals("abcdef", new String(dest));
    }

    @Test
    void copyTo_longText() {
        String s = "abcdefghij".repeat(100);
        char[] dest = new char[s.length()];
        Text.of(s).copyTo(dest, 0);
        assertEquals(s, new String(dest));
    }

    // ===== copyTo(dest, offset, from, length) =====

    @Test
    void copyToSlice_middlePortion() {
        char[] dest = new char[3];
        Text.of("hello").copyTo(dest, 0, 1, 3);
        assertEquals("ell", new String(dest));
    }

    @Test
    void copyToSlice_fromStart() {
        char[] dest = new char[3];
        Text.of("hello").copyTo(dest, 0, 0, 3);
        assertEquals("hel", new String(dest));
    }

    @Test
    void copyToSlice_toEnd() {
        char[] dest = new char[3];
        Text.of("hello").copyTo(dest, 0, 2, 3);
        assertEquals("llo", new String(dest));
    }

    @Test
    void copyToSlice_entireText() {
        char[] dest = new char[5];
        Text.of("hello").copyTo(dest, 0, 0, 5);
        assertEquals("hello", new String(dest));
    }

    @Test
    void copyToSlice_zeroLength_isNoOp() {
        char[] dest = "abc".toCharArray();
        Text.of("hello").copyTo(dest, 1, 2, 0);
        assertEquals("abc", new String(dest));
    }

    @Test
    void copyToSlice_singleChar() {
        char[] dest = new char[1];
        Text.of("hello").copyTo(dest, 0, 2, 1);
        assertEquals("l", new String(dest));
    }

    @Test
    void copyToSlice_atNonZeroDestOffset() {
        char[] dest = "xxxxxxxxxx".toCharArray();
        Text.of("hello").copyTo(dest, 3, 1, 3);
        assertEquals("xxxellxxxx", new String(dest));
    }

    @Test
    void copyToSlice_leavesOutsideUntouched() {
        char[] dest = ".....".toCharArray();
        Text.of("hello").copyTo(dest, 1, 0, 3);
        assertEquals(".hel.", new String(dest));
    }

    @Test
    void copyToSlice_consecutiveCopiesFromDifferentSlices() {
        char[] dest = new char[4];
        Text t = Text.of("abcdef");
        t.copyTo(dest, 0, 0, 2);  // "ab"
        t.copyTo(dest, 2, 4, 2);  // "ef"
        assertEquals("abef", new String(dest));
    }

    @Test
    void copyToSlice_fromSliceView() {
        // Slice-of-slice: outer slice is "llo wo", inner copies "lo "
        char[] dest = new char[3];
        Text.of("hello world").slice(2, 8).copyTo(dest, 0, 1, 3);
        assertEquals("lo ", new String(dest));
    }

    @Test
    void copyToSlice_sourceUnchanged() {
        Text t = Text.of("hello");
        char[] dest = new char[3];
        t.copyTo(dest, 0, 1, 3);
        assertEquals("hello", t.toString());
    }

    @Test
    void copyToSlice_surrogatePair_copyAsCodeUnits() {
        // Text operates on UTF-16 code units. A surrogate pair spans two
        // chars; copying a slice that includes it must faithfully copy both.
        String withEmoji = "a\uD83D\uDE00b";   // "a😀b" — length 4
        char[] dest = new char[4];
        Text.of(withEmoji).copyTo(dest, 0, 0, 4);
        assertEquals(withEmoji, new String(dest));
    }

    @Test
    void copyTo_fullAndSliceAreEquivalent() {
        for (String s : new String[]{"", "a", "hello", "hello world", "a\uD83D\uDE00b"}) {
            Text t = Text.of(s);
            char[] a = new char[s.length()];
            char[] b = new char[s.length()];
            t.copyTo(a, 0);
            t.copyTo(b, 0, 0, t.length());
            assertEquals(new String(a), new String(b),
                "copyTo overloads disagree for: " + s);
        }
    }
}