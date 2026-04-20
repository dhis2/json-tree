package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Texts the {@link Text} API.
 *
 * @author Jan Bernitt (with most of the simple test cases created by AI)
 */
class TextTest {

  @Test
  void testCopyOf() {
    Text original = Text.of("hello");
    Text copy = Text.copyOf(original);
    assertEquals(original, copy);
    assertNotSame(original, copy);
  }

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
    // empty returns 0? but lastIndexOf empty? spec says return 0? we follow
    // implementation.
    assertEquals(0, t.lastIndexOf(""));
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

  @DisplayName("slice(int, int)")
  @Test
  void testSlice() {
    assertEquals(Text.of("hello"), Text.of("hello").slice(0, 5));
    assertEquals(Text.of("ello"), Text.of("hello").slice(1, 5));
    assertEquals(Text.of("ell"), Text.of("hello").slice(1, -1));
    assertEquals(Text.of("l"), Text.of("hello").slice(2, -2));
    assertEquals(Text.of(""), Text.of("hello").slice(2, -3));
    assertEquals(Text.of(""), Text.of("hello").slice(3, -2));
    assertEquals(Text.of(""), Text.of("hello").slice(3, -3));
  }

  @DisplayName("trim()")
  @Test
  void testTrim() {
    assertEquals(Text.of("hello"), Text.of("hello").trim());
    assertEquals(Text.of("hello"), Text.of(" hello").trim());
    assertEquals(Text.of("hello"), Text.of("hello ").trim());
    assertEquals(Text.of("hello"), Text.of(" hello ").trim());
    assertEquals(Text.of("hello"), Text.of("  hello  ").trim());
    assertEquals(Text.of(""), Text.of(" ").trim());
  }

  @DisplayName("slice(Text, Consumer)")
  @Test
  void testSlice_Pattern() {
    List<Text> parts = new ArrayList<>();

    Text foo = Text.of("foo");
    foo.slice(Text.of("~."), parts::add);
    assertEquals(List.of(foo), parts);
    assertSame(foo, parts.get(0));

    parts.clear();
    Text.of("foo.bar").slice(Text.of("~."), parts::add);
    assertEquals(List.of(Text.of("foo."), Text.of("bar")), parts);

    parts.clear();
    Text.of("foo.bar.baz").slice(Text.of("~."), parts::add);
    assertEquals(List.of(Text.of("foo."), Text.of("bar."), Text.of("baz")), parts);
  }

  @DisplayName("toCharArray()")
  @Test
  void testToCharArray() {
    Text t = Text.of("hello");
    assertArrayEquals(new char[] {'h', 'e', 'l', 'l', 'o'}, t.toCharArray());
  }

  @DisplayName("isTextualInteger()")
  @Test
  void testIsTextualInteger() {
    assertTrue(Text.of(123).isTextualInteger());
    assertTrue(Text.of("123").isTextualInteger());
    assertTrue(Text.of(-123).isTextualInteger());
    assertTrue(Text.of("+123").isTextualInteger());
    assertTrue(Text.of("0").isTextualInteger());
    assertTrue(Text.of("-0").isTextualInteger());
    assertTrue(Text.of("+0").isTextualInteger());
    assertTrue(Text.of("007").isTextualInteger());
    assertTrue(Text.of("9999999999999999999999999999").isTextualInteger());

    assertFalse(Text.of("").isTextualInteger());
    assertFalse(Text.of(".01").isTextualInteger());
    assertFalse(Text.of("12a").isTextualInteger());
    assertFalse(Text.of("12.3").isTextualInteger());
    assertFalse(Text.of("--1").isTextualInteger());
  }

  @DisplayName("isNumericInteger()")
  @Test
  void testIsNumericInteger() {
    assertTrue(Text.of(123).isNumericInteger());
    assertTrue(Text.of("123").isNumericInteger());
    assertTrue(Text.of(-123).isNumericInteger());
    assertTrue(Text.of("+123").isNumericInteger());
    assertTrue(Text.of("0").isNumericInteger());
    assertTrue(Text.of("-0").isNumericInteger());
    assertTrue(Text.of("+0").isNumericInteger());
    assertTrue(Text.of("007").isNumericInteger());
    assertTrue(Text.of("9999999999999999999999999999").isNumericInteger());

    assertFalse(Text.of("").isNumericInteger());
    assertFalse(Text.of(".01").isNumericInteger());
    assertFalse(Text.of("12a").isNumericInteger());
    assertFalse(Text.of("12.3").isNumericInteger());
    assertFalse(Text.of("--1").isNumericInteger());
    assertFalse(Text.of("1.0000001").isNumericInteger());

    assertTrue(Text.of("0.0").isNumericInteger());
    assertTrue(Text.of("+1.0").isNumericInteger());
    assertTrue(Text.of("-1.0").isNumericInteger());
    assertTrue(Text.of("1.0").isNumericInteger());
    assertTrue(Text.of("12.00").isNumericInteger());
  }

  @DisplayName("isTextualDecimal()")
  @Test
  void testIsTextualDecimal() {
    assertTrue(Text.of("0").isTextualDecimal());
    assertTrue(Text.of(".0").isTextualDecimal());
    assertTrue(Text.of("0.").isTextualDecimal());
    assertTrue(Text.of("-0").isTextualDecimal());
    assertTrue(Text.of("-.0").isTextualDecimal());
    assertTrue(Text.of("-0.").isTextualDecimal());
    assertTrue(Text.of("+0").isTextualDecimal());
    assertTrue(Text.of("+.0").isTextualDecimal());
    assertTrue(Text.of("+0.").isTextualDecimal());
    assertTrue(Text.of("123").isTextualDecimal());
    assertTrue(Text.of("123.0").isTextualDecimal());
    assertTrue(Text.of("-123.0").isTextualDecimal());
    assertTrue(Text.of("123e-2").isTextualDecimal());
    assertTrue(Text.of("123.0e234").isTextualDecimal());
    assertTrue(Text.of("-123.0001E-23").isTextualDecimal());

    assertFalse(Text.of("").isTextualDecimal());
    assertFalse(Text.of("0a").isTextualDecimal());
    assertFalse(Text.of("-0a").isTextualDecimal());
    assertFalse(Text.of("--0").isTextualDecimal());
    assertFalse(Text.of("+-0").isTextualDecimal());
    assertFalse(Text.of("++0").isTextualDecimal());
    assertFalse(Text.of("-+0").isTextualDecimal());
    assertFalse(Text.of("+0a").isTextualDecimal());
    assertFalse(Text.of("+").isTextualDecimal());
    assertFalse(Text.of("-").isTextualDecimal());
    assertFalse(Text.of(".").isTextualDecimal());
    assertFalse(Text.of(".0e").isTextualDecimal());
    assertFalse(Text.of(".0E").isTextualDecimal());
    assertFalse(Text.of("0.e").isTextualDecimal());
    assertFalse(Text.of("0.E").isTextualDecimal());
    assertFalse(Text.of("x0").isTextualDecimal());
    assertFalse(Text.of("x").isTextualDecimal());
    assertFalse(Text.of("5.x").isTextualDecimal());
    assertFalse(Text.of("5.0ex").isTextualDecimal());
  }

  @DisplayName("isSpecialDecimal()")
  @Test
  void testIsSpecialDecimal() {
    assertTrue(Text.of("NaN").isSpecialDecimal());
    assertTrue(Text.of("Infinity").isSpecialDecimal());
    assertTrue(Text.of("-Infinity").isSpecialDecimal());
    assertFalse(Text.of("").isSpecialDecimal());
    assertFalse(Text.of("Nan").isSpecialDecimal());
    assertFalse(Text.of("nan").isSpecialDecimal());
    assertFalse(Text.of("NaNa").isSpecialDecimal());
    assertFalse(Text.of("infinity").isSpecialDecimal());
    assertFalse(Text.of("-infinity").isSpecialDecimal());
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

  @DisplayName("parseInt()")
  @Test
  void testParseLong() {
    assertEquals(123L, Text.of(123).parseLong());
    assertEquals(123L, Text.of("123").parseLong());
    assertEquals(-123L, Text.of(-123).parseLong());
    assertEquals(-123L, Text.of("-123").parseLong());
    assertEquals(123L, Text.of("+123").parseLong());
    assertEquals(0L, Text.of("0").parseLong());
    assertEquals(0L, Text.of("-0").parseLong());
    assertEquals(7L, Text.of("007").parseLong());
    assertThrows(NumberFormatException.class, () -> Text.of("").parseLong());
    assertThrows(NumberFormatException.class, () -> Text.of("12a").parseLong());
    assertThrows(NumberFormatException.class, () -> Text.of("12.3").parseLong());
  }

  @Test
  void testParseBoolean() {
    assertTrue(Text.of("true").parseBoolean());
    assertTrue(Text.of("TRUE").parseBoolean());
    assertFalse(Text.of("false").parseBoolean());
    assertFalse(Text.of("FALSE").parseBoolean());
    assertThrowsExactly(IllegalArgumentException.class, () -> Text.of("t").parseBoolean());
    assertThrowsExactly(IllegalArgumentException.class, () -> Text.of("f").parseBoolean());
    assertThrowsExactly(IllegalArgumentException.class, () -> Text.of("tear").parseBoolean());
    assertThrowsExactly(IllegalArgumentException.class, () -> Text.of("fear").parseBoolean());
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

  @DisplayName("Text.of(int) 0 to 999")
  @Test
  void testOf_Int0To999() {
    for (int i = 0; i < 1000; i++) {
      assertEquals(String.valueOf(i), Text.of(i).toString());
    }
  }

  @DisplayName("Text.of(int) -1 to -999")
  @Test
  void testOf_IntNeg1To999() {
    for (int i = -1; i > -1000; i--) {
      assertEquals(String.valueOf(i), Text.of(i).toString());
    }
  }

  @DisplayName("Text.of(int) edge")
  @Test
  void testOf_Int_EdgeCases() {
    assertEquals(String.valueOf(Integer.MAX_VALUE), Text.of(Integer.MAX_VALUE).toString());
    assertEquals(String.valueOf(Integer.MIN_VALUE), Text.of(Integer.MIN_VALUE).toString());
  }

  @Test
  void testOf_String_Cache() {
    Text hello = Text.of("hello");
    assertEquals("hello", hello.toString());
    assertSame(hello, Text.of("hello"), "should be same from cache");
    Text hero = Text.of("hero");
    assertSame(hello, Text.of("hello"), "still same from cache");
    assertSame(hero, Text.of("hero"), "also same");
    Text help = Text.of("help");
    assertEquals("help", help.toString());
    assertEquals("hero", hero.toString(), "content has not changed");
    assertSame(help, Text.of("help"), "now help is cached");
    assertNotSame(hero, Text.of("hero"), "hero is not cached any more");
    assertSame(hello, Text.of("hello"), "but hello is still cached");
  }

  @Test
  void testOf_String_CacheLimit() {
    Text cached = Text.of("a234567890123456");
    assertSame(cached, Text.of("a234567890123456"));
    Text tooLong = Text.of("a2345678901234567");
    assertNotSame(tooLong, Text.of("a2345678901234567"));
  }

  @DisplayName("Text.hashCode(Text)")
  @Test
  void testHashCode_Indexes() {
    Set<Integer> hashes = new HashSet<>(1000);
    for (int i = 0; i < 1000; i++) {
      hashes.add(Text.of(i).hashCode());
    }
    assertEquals(1000, hashes.size());
  }

  @DisplayName("Text.hashCode(Text)")
  @Test
  void testHashCode_Indexes_Long() {
    Set<Integer> hashes = new HashSet<>(1000);
    char[] characters = "abcdefghijk".toCharArray();
    for (int i = 0; i < 1000; i++) {
      // make 2 characters different
      int offset = i % characters.length;
      characters[offset] = (char) ((int) characters[offset] + (i % 7));
      offset = i % 7;
      characters[offset] = (char) ((int) characters[offset] + (i % 13));
      hashes.add(Text.of(new String(characters)).hashCode());
    }
    // in other words: in an 11-character text with 2 characters varying
    // there are 4.6% collisions
    // this is with poor "randomisation" of the variation
    assertEquals(954, hashes.size());
  }
}
