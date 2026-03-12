package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TextTest {

    @DisplayName("indexOf(char)")
    @Test
    void testIndexOf_char() {
        Text t = Text.of("hello");
        assertEquals(0, t.indexOf('h'), "first char");
        assertEquals(2, t.indexOf('l'), "middle char");
        assertEquals(-1, t.indexOf('z'), "not found");
    }

    @DisplayName("indexOf(char, int)")
    @Test
    void testIndexOf_char_int() {
        Text t = Text.of("hello");
        assertEquals(2, t.indexOf('l', 0));
        assertEquals(3, t.indexOf('l', 3));
        assertEquals(-1, t.indexOf('l', 4));
        assertEquals(-1, t.indexOf('h', 1));
    }

    @DisplayName("indexOf(char, int, int)")
    @Test
    void testIndexOf_char_int_int() {
        Text t = Text.of("hello world");
        assertEquals(2, t.indexOf('l', 0, 5));
        assertEquals(-1, t.indexOf('l', 4, 5));
        assertEquals(4, t.indexOf('o', 2, 6));
    }

    @DisplayName("lastIndexOf(char)")
    @Test
    void testLastIndexOf_char() {
        Text t = Text.of("hello");
        assertEquals(3, t.lastIndexOf('l'));
        assertEquals(1, t.lastIndexOf('e'));
        assertEquals(-1, t.lastIndexOf('z'));
    }

    @DisplayName("lastIndexOf(char, int)")
    @Test
    void testLastIndexOf_char_int() {
        Text t = Text.of("hello");
        assertEquals(3, t.lastIndexOf('l', 4));
        assertEquals(2, t.lastIndexOf('l', 2));
        assertEquals(-1, t.lastIndexOf('l', 1));
    }

    @DisplayName("lastIndexOf(char, int, int)")
    @Test
    void testLastIndexOf_char_int_int() {
        Text t = Text.of("hello world");
        assertEquals(3, t.lastIndexOf('l', 4, 0));
        assertEquals(3, t.lastIndexOf('l', 3, 2));
        assertEquals(2, t.lastIndexOf('l', 2, 1));
        assertEquals(-1, t.lastIndexOf('l', 1, 0));
    }

    @DisplayName("contains(char)")
    @Test
    void testContains_char() {
        Text t = Text.of("hello");
        assertTrue(t.contains('h'));
        assertTrue(t.contains('l'));
        assertFalse(t.contains('z'));
    }

    @DisplayName("contains(CharSequence)")
    @Test
    void testContains_CharSequence() {
        Text t = Text.of("hello world");
        assertTrue(t.contains("world"));
        assertTrue(t.contains(""));
        assertFalse(t.contains("xyz"));
    }

    @DisplayName("indexOf(CharSequence)")
    @Test
    void testIndexOf_CharSequence() {
        Text t = Text.of("hello world");
        assertEquals(0, t.indexOf("hello"));
        assertEquals(6, t.indexOf("world"));
        assertEquals(2, t.indexOf("llo"));
        assertEquals(-1, t.indexOf("xyz"));
        assertEquals(0, t.indexOf("")); // empty returns 0
    }

    @DisplayName("indexOf(CharSequence, int)")
    @Test
    void testIndexOf_CharSequence_int() {
        Text t = Text.of("hello world hello");
        assertEquals(12, t.indexOf("hello", 1));
        assertEquals(-1, t.indexOf("world", 8));
        assertEquals(14, t.indexOf("llo", 5));
    }

    @DisplayName("indexOf(CharSequence, int, int)")
    @Test
    void testIndexOf_CharSequence_int_int() {
        Text t = Text.of("hello world hello");
        assertEquals(12, t.indexOf("hello", 8, 18));
        assertEquals(-1, t.indexOf("world", 8, 12));
        assertEquals(-1, t.indexOf("hello", 0, 4));
        assertEquals(-1, t.indexOf("hello", 0, 4));
    }

    @DisplayName("lastIndexOf(CharSequence)")
    @Test
    void testLastIndexOf_CharSequence() {
        Text t = Text.of("hello world hello");
        assertEquals(12, t.lastIndexOf("hello"));
        assertEquals(6, t.lastIndexOf("world"));
        assertEquals(14, t.lastIndexOf("llo"));
        assertEquals(-1, t.lastIndexOf("xyz"));
        assertEquals(
            0,
            t.lastIndexOf("")); // empty returns 0? but lastIndexOf empty? spec says return 0? we follow
        // implementation.
    }

    @DisplayName("lastIndexOf(CharSequence, int)")
    @Test
    void testLastIndexOf_CharSequence_int() {
        Text t = Text.of("hello world hello");
        assertEquals(0, t.lastIndexOf("hello", 10));
        assertEquals(-1, t.lastIndexOf("hello", -1));
    }

    @DisplayName("lastIndexOf(CharSequence, int, int)")
    @Test
    void testLastIndexOf_CharSequence_int_int() {
        Text t = Text.of("hello world hello");
        assertEquals(0, t.lastIndexOf("hello", 10, 0));
        assertEquals(-1, t.lastIndexOf("world", 5, 0));
    }

    @DisplayName("startsWith(CharSequence)")
    @Test
    void testStartsWith_CharSequence() {
        Text t = Text.of("hello");
        assertTrue(t.startsWith("he"));
        assertTrue(t.startsWith(""));
        assertFalse(t.startsWith("el"));
    }

    @DisplayName("startsWith(CharSequence, int)")
    @Test
    void testStartsWith_CharSequence_int() {
        Text t = Text.of("hello");
        assertTrue(t.startsWith("el", 1));
        assertTrue(t.startsWith("", 3));
        assertTrue(t.startsWith("lo", 3));
    }

    @DisplayName("endsWith(CharSequence)")
    @Test
    void testEndsWith() {
        Text t = Text.of("hello");
        assertTrue(t.endsWith("lo"));
        assertTrue(t.endsWith(""));
        assertFalse(t.endsWith("el"));
    }

    @DisplayName("contentEquals(CharSequence)")
    @Test
    void testContentEquals() {
        Text t = Text.of("hello");
        assertTrue(t.contentEquals("hello"));
        assertTrue(t.contentEquals(Text.of("hello")));
        assertFalse(t.contentEquals("world"));
        assertFalse(t.contentEquals("hell"));
        assertFalse(t.contentEquals("helloo"));
    }

    @DisplayName("regionMatches(int, CharSequence)")
    @Test
    void testRegionMatches_int_CharSequence() {
        Text t = Text.of("hello");
        assertTrue(t.regionMatches(0, "hello"));
        assertTrue(t.regionMatches(1, "ell"));
        assertTrue(t.regionMatches(2, "llo"));
        assertFalse(t.regionMatches(2, "llo "));
        assertTrue(t.regionMatches(3, "lo"));
        assertFalse(t.regionMatches(1, "hell"));
    }

    @DisplayName("regionMatches(int, CharSequence, int, int)")
    @Test
    void testRegionMatches_int_CharSequence_int_int() {
        Text t = Text.of("hello world");
        assertTrue(t.regionMatches(6, "world", 0, 5));
        assertTrue(t.regionMatches(6, "beautiful world", 10, 5));
        assertFalse(t.regionMatches(6, "world", 1, 4)); // offset 1 => "orld"
        assertFalse(t.regionMatches(0, "hello", 2, 3)); // "llo" vs "hel"
    }

    @DisplayName("subSequence(int, int)")
    @Test
    void testSubSequence() {
        Text t = Text.of("hello");
        assertEquals(Text.of("ell"), t.subSequence(1, 4));
        assertEquals(Text.of(""), t.subSequence(2, 2));
        assertEquals(Text.of("hello"), t.subSequence(0, 5));
        // check that it returns a Text instance (not necessarily a new one for whole range)
        assertSame(t, t.subSequence(0, 5), "subSequence(0,length()) should return this");
    }

    @DisplayName("toCharArray()")
    @Test
    void testToCharArray() {
        Text t = Text.of("hello");
        assertArrayEquals(new char[] {'h', 'e', 'l', 'l', 'o'}, t.toCharArray());
    }

    @DisplayName("isInt()")
    @Test
    void testIsInt() {
        assertTrue(Text.of(123).isInt());
        assertTrue(Text.of("123").isInt());
        assertTrue(Text.of(-123).isInt());
        assertTrue(Text.of("+123").isInt());
        assertTrue(Text.of("0").isInt());
        assertTrue(Text.of("-0").isInt());
        assertTrue(Text.of("+0").isInt());
        assertTrue(Text.of("007").isInt());
        assertFalse(Text.of("").isInt());
        assertFalse(Text.of("12a").isInt());
        assertFalse(Text.of("12.3").isInt());
        assertFalse(Text.of("--1").isInt());
    }

    @DisplayName("parseInt()")
    @Test
    void testParseInt() {
        assertEquals(123, Text.of(123).parseInt());
        assertEquals(123, Text.of("123").parseInt());
        assertEquals(-123, Text.of(-123).parseInt());
        assertEquals(-123, Text.of("-123").parseInt());
        assertEquals(123, Text.of("+123").parseInt());
        assertEquals(0, Text.of("0").parseInt());
        assertEquals(0, Text.of("-0").parseInt());
        assertEquals(7, Text.of("007").parseInt());
        assertThrows(NumberFormatException.class, () -> Text.of("").parseInt());
        assertThrows(NumberFormatException.class, () -> Text.of("12a").parseInt());
        assertThrows(NumberFormatException.class, () -> Text.of("12.3").parseInt());
    }

    @DisplayName("compareTo(Text)")
    @Test
    void testCompareTo() {
        assertTrue(Text.of("a").compareTo(Text.of("b")) < 0);
        assertTrue(Text.of("b").compareTo(Text.of("a")) > 0);
        assertEquals(0, Text.of("abc").compareTo(Text.of("abc")));
        assertTrue(Text.of("abc").compareTo(Text.of("abcd")) < 0);
        assertTrue(Text.of("abcd").compareTo(Text.of("abc")) > 0);
    }

    @DisplayName("equals(Object)")
    @Test
    void testEquals() {
        Text t1 = Text.of("hello");
        Text t2 = Text.of("hello");
        Text t3 = Text.of("world");
        assertEquals(t1, t2);
        assertNotEquals(t1, t3);
        assertNotEquals(t1, "hello"); // must not equal a String
        assertNotEquals(t1, null);
    }

    @DisplayName("hashCode()")
    @Test
    void testHashCode() {
        Text t1 = Text.of("hello");
        Text t2 = Text.of("hello");
        Text t3 = Text.of("world");
        assertEquals(t1.hashCode(), t2.hashCode());
        // not required but likely different for different strings
        assertNotEquals(t1.hashCode(), t3.hashCode());
    }

    @DisplayName("Text.of(int) cached")
    @Test
    void testOf_IndexCache() {
        for (int i = 0; i < 1000; i++) {
            assertEquals(String.valueOf(i), Text.of(i).toString());
        }
    }

    @DisplayName("Text.of(int) uncached")
    @Test
    void testOf_IndexFallback() {
        for (int i : List.of(-44, 234567, 78943)) {
            assertEquals(String.valueOf(i), Text.of(i).toString());
        }
    }

    @DisplayName("Text.hashCode(Text)")
    @Test
    void testHashCode_Indexes() {
        Set<Integer> hashes = new HashSet<>(1000);
        for (int i = 0; i < 1000; i++) {
            hashes.add( Text.of( i ).hashCode() );
        }
        assertEquals( 1000, hashes.size() );
    }

    @DisplayName("Text.hashCode(Text)")
    @Test
    void testHashCode_Indexes_Long() {
        Set<Integer> hashes = new HashSet<>(1000);
        char[] characters = "abcdefghijk".toCharArray();
        for (int i = 0; i < 1000; i++) {
            // make 2 characters different
            int offset = i % characters.length;
            characters[offset] = (char) ((int)characters[offset] + (i % 7));
            offset = i % 7;
            characters[offset] = (char) ((int)characters[offset] + (i % 13));
            hashes.add( Text.of( new String(characters) ).hashCode() );
        }
        // in other words: in an 11-character text with 2 characters varying
        // there are 4.6% collisions
        // this is with poor "randomisation" of the variation
        assertEquals( 954, hashes.size() );
    }
}