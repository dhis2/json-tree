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

  @DisplayName("concat()")
  @Test
  void testConcat() {
    Text hello = Text.of("hello");
    assertSame(hello, hello.concat(""));
    assertSame(hello, hello.concat(Text.of("")));
    assertEquals(hello, Text.of("").concat("hello"));
    assertSame(hello, Text.of("").concat(hello));

    assertEquals(Text.of("hello world!"), Text.of("hello").concat(" world!"));
    assertEquals(Text.of("hello world!"), Text.of("hello").concat(Text.of(" world!")));
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

  @DisplayName("parseInt()/parseLong() on int cache 0-999")
  @Test
  void testParseInt_IntCache_Loop() {
    for (int i = 0; i < 1000; i++) {
      Text t = Text.of(i);
      assertEquals(i, t.parseInt(), "parseInt of " + i);
      assertEquals(i, t.parseLong(), "parseLong of " + i);
    }
  }

  @DisplayName("parseInt()/parseLong() on subSlices of the int cache")
  @Test
  void testParseInt_IntCache_Slices() {
    for (int i = 0; i < 1000; i++) {
      Text t = Text.of(i);
      String s = String.valueOf(i);
      for (int from = 0; from < s.length(); from++) {
        for (int to = from + 1; to <= s.length(); to++) {
          Text slice = t.subSequence(from, to);
          String expected = s.substring(from, to);
          Text slice2 = Text.of(expected);
          String msg = "of(" + i + ").subSequence(" + from + "," + to + ") = " + expected;
          assertEquals(Integer.parseInt(expected), slice.parseInt(), "parseInt " + msg);
          assertEquals(Long.parseLong(expected), slice.parseLong(), "parseLong " + msg);
          assertEquals(slice2.parseInt(), slice.parseInt());
          assertTrue(slice.isTextualInteger(), "isTextualInteger " + msg);
          assertTrue(slice.isNumericInteger(), "isNumericInteger " + msg);
        }
      }
    }
  }

  @DisplayName("empty slice of the int cache")
  @Test
  void testEmptySlice_IntCache() {
    // subSequence(0,0) of an of(int) keeps the ZERO_TO_999 buffer but is empty
    Text empty = Text.of(5).subSequence(0, 0);
    assertTrue(empty.isEmpty());
    assertEquals(0, empty.length());
    assertFalse(empty.isTextualInteger());
    assertFalse(empty.isNumericInteger());
    assertFalse(empty.isTextualDecimal());
    assertThrows(NumberFormatException.class, empty::parseInt);
    assertThrows(NumberFormatException.class, empty::parseLong);

    // same on a 3-digit slice
    Text emptyFromBig = Text.of(123).subSequence(2, 2);
    assertTrue(emptyFromBig.isEmpty());
    assertFalse(emptyFromBig.isTextualInteger());
    assertThrows(NumberFormatException.class, emptyFromBig::parseInt);
    assertThrows(NumberFormatException.class, emptyFromBig::parseLong);

    // the 3-digit slice trimmed to nothing on both ends still works
    Text trimmed = Text.of(999).subSequence(1, 1);
    assertTrue(trimmed.isEmpty());
    assertFalse(trimmed.isTextualInteger());
    assertThrows(NumberFormatException.class, trimmed::parseInt);
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
    Text cached = Text.of("attributeOptionCombo");
    assertSame(cached, Text.of("attributeOptionCombo"));
    Text tooLong = Text.of("attributeOptionCombo2");
    assertNotSame(tooLong, Text.of("attributeOptionCombo2"));
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

  @DisplayName("trimInteger(): already canonical")
  @Test
  void testTrimInteger_plainIntegers() {
    assertEquals(Text.of("42"), Text.of("42").trimInteger());
    assertEquals(Text.of("-42"), Text.of("-42").trimInteger());
    assertEquals(Text.of("0"), Text.of("0").trimInteger());
    assertEquals(Text.of("0"), Text.of("-0").trimInteger()); // sign dropped on zero
    assertEquals(Text.of("9007199254740993"), Text.of("9007199254740993").trimInteger());
  }

  @DisplayName("trimInteger(): zero forms collapse to \"0\"")
  @Test
  void testTrimInteger_zeroForms() {
    assertEquals(Text.of("0"), Text.of("0.0").trimInteger());
    assertEquals(Text.of("0"), Text.of("0.00").trimInteger());
    assertEquals(Text.of("0"), Text.of("0.000000").trimInteger());
    assertEquals(Text.of("0"), Text.of("-0.0").trimInteger());
    assertEquals(Text.of("0"), Text.of("-0.00").trimInteger());
    assertEquals(Text.of("0"), Text.of("00").trimInteger());
    assertEquals(Text.of("0"), Text.of("-000").trimInteger());
    assertEquals(Text.of("0"), Text.of("00.00").trimInteger());
    assertEquals(Text.of("0"), Text.of("-00.00").trimInteger());
  }

  @DisplayName("trimInteger(): leading zeros stripped")
  @Test
  void testTrimInteger_leadingZeros() {
    assertEquals(Text.of("7"), Text.of("007").trimInteger());
    assertEquals(Text.of("-7"), Text.of("-007").trimInteger());
    assertEquals(Text.of("100"), Text.of("00100").trimInteger());
    assertEquals(Text.of("-100"), Text.of("-00100").trimInteger());
    assertEquals(Text.of("9007199254740993"), Text.of("0009007199254740993").trimInteger());
  }

  @DisplayName("trimInteger(): trailing fraction zeros dropped")
  @Test
  void testTrimInteger_trailingFraction() {
    assertEquals(Text.of("42"), Text.of("42.0").trimInteger());
    assertEquals(Text.of("42"), Text.of("42.00").trimInteger());
    assertEquals(Text.of("42"), Text.of("42.000000").trimInteger());
    assertEquals(Text.of("-42"), Text.of("-42.0").trimInteger());
    assertEquals(Text.of("-42"), Text.of("-42.00").trimInteger());
    assertEquals(Text.of("100"), Text.of("100.0").trimInteger());
    assertEquals(Text.of("100"), Text.of("00100.00").trimInteger());
    assertEquals(Text.of("9007199254740993"), Text.of("9007199254740993.00").trimInteger());
  }

  @DisplayName("trimInteger(): equivalent forms compare equal")
  @Test
  void testTrimInteger_equivalence() {
    // forms that represent the same integer must trim to identical text
    assertTrue(Text.of("42").trimInteger().contentEquals(Text.of("42.0").trimInteger()));
    assertTrue(Text.of("-42").trimInteger().contentEquals(Text.of("-42.00").trimInteger()));
    assertTrue(Text.of("0").trimInteger().contentEquals(Text.of("-0").trimInteger()));
    assertTrue(Text.of("0").trimInteger().contentEquals(Text.of("0.00").trimInteger()));
    assertTrue(Text.of("7").trimInteger().contentEquals(Text.of("007").trimInteger()));

    // the reason the helper exists: large integers that round to the same
    // double must still be distinguishable as text
    assertTrue(
        Text.of("9007199254740993").trimInteger()
            .contentEquals(Text.of("9007199254740993.00").trimInteger()));
    assertFalse(
        Text.of("9007199254740993").trimInteger()
            .contentEquals(Text.of("9007199254740992").trimInteger()));
  }

  @DisplayName("trimInteger(): empty text returns self")
  @Test
  void testTrimInteger_empty() {
    Text empty = Text.of("");
    assertSame(empty, empty.trimInteger());
  }

  @DisplayName("trimInteger(): already-canonical returns equivalent text")
  @Test
  void testTrimInteger_noOpContent() {
    // When nothing needs trimming the implementation may return the same
    // instance (via the subSequence whole-range optimisation) or a copy;
    // only content equality is part of the contract.
    Text t = Text.of("42");
    assertEquals(t, t.trimInteger());
  }

  @DisplayName("trimInteger(): leading + stripped")
  @Test
  void testTrimInteger_leadingPlus() {
    // JSON numbers never carry a '+', but trimInteger treats both signs as
    // sign so that numeric equivalence holds across all numeric text forms
    assertEquals(Text.of("42"), Text.of("+42").trimInteger());
    assertEquals(Text.of("7"), Text.of("+007").trimInteger());
    assertEquals(Text.of("100"), Text.of("+00100").trimInteger());
    assertEquals(Text.of("0"), Text.of("+0").trimInteger());
    assertEquals(Text.of("0"), Text.of("+0.0").trimInteger());
    assertEquals(Text.of("0"), Text.of("+0.00").trimInteger());
    assertEquals(Text.of("42"), Text.of("+42.0").trimInteger());
    assertEquals(Text.of("42"), Text.of("+42.00").trimInteger());
    assertEquals(
        Text.of("9007199254740993"), Text.of("+9007199254740993").trimInteger());
  }

  @DisplayName("trimInteger(): sign-equivalence across forms")
  @Test
  void testTrimInteger_signedEquivalence() {
    // all forms of the same integer compare equal after trimming
    assertTrue(
        Text.of("42").trimInteger()
            .contentEquals(Text.of("+42").trimInteger()));
    assertTrue(
        Text.of("+42").trimInteger()
            .contentEquals(Text.of("42.00").trimInteger()));
    assertTrue(
        Text.of("+0").trimInteger()
            .contentEquals(Text.of("-0").trimInteger()));
    assertTrue(
        Text.of("+0").trimInteger()
            .contentEquals(Text.of("0").trimInteger()));
  }

  @DisplayName("contains(CharPredicate)")
  @Test
  void testContains_CharPredicate() {
    Text t = Text.of("hello");
    assertTrue(t.contains(Character::isLetter));
    assertTrue(t.contains(c -> c == 'h'));
    assertTrue(t.contains(c -> c == 'o'));
    assertFalse(t.contains(c -> c == 'z'));
    assertFalse(t.contains(Character::isDigit));
    assertFalse(Text.of("").contains(c -> true));
    assertTrue(Text.of("a1").contains(Character::isDigit));
    assertTrue(Text.of("a1").contains(c -> c == '1'));
  }

  @DisplayName("subSequence(int)")
  @Test
  void testSubSequence_int() {
    Text t = Text.of("hello");
    assertEquals(Text.of("hello"), t.subSequence(0));
    assertEquals(Text.of("ello"), t.subSequence(1));
    assertEquals(Text.of("o"), t.subSequence(4));
    assertEquals(Text.of(""), t.subSequence(5));
    assertSame(t, t.subSequence(0), "subSequence(0) should return this");
    assertThrows(IndexOutOfBoundsException.class, () -> t.subSequence(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> t.subSequence(6));
  }

  @DisplayName("strip()")
  @Test
  void testStrip() {
    assertEquals(Text.of("hello"), Text.of("hello").strip());
    assertEquals(Text.of("hello"), Text.of(" hello ").strip());
    assertEquals(Text.of("hello"), Text.of("\thello\t").strip());
    assertEquals(Text.of("hello"), Text.of("\nhello\n").strip());
    assertEquals(Text.of("hello"), Text.of("\rhello\r").strip());
    assertEquals(Text.of("hello"), Text.of(" \t\n\r hello \r\n\t ").strip());
    assertEquals(Text.of(""), Text.of(" \t\n\r ").strip());
    assertEquals(Text.of("a b"), Text.of("  a b  ").strip());
    // strip differs from trim: newlines are removed
    assertEquals(Text.of("\nhello"), Text.of("\nhello").trim());
    assertEquals(Text.of("hello"), Text.of("\nhello").strip());
  }

  @DisplayName("shrink(CharPredicate)")
  @Test
  void testShrink() {
    assertEquals(Text.of("hello"), Text.of("xxhelloxx").shrink(c -> c == 'x'));
    assertEquals(Text.of("hello"), Text.of("123hello456").shrink(Character::isDigit));
    assertEquals(Text.of(""), Text.of("12345").shrink(Character::isDigit));
    assertEquals(Text.of("hello"), Text.of("hello").shrink(c -> c == 'x'));
    assertEquals(Text.of(""), Text.of("").shrink(c -> true));
    // strip/trim can be expressed via shrink
    assertEquals(Text.of("hello"), Text.of(" hello ").shrink(CharFragment::isStrip));
    assertEquals(Text.of("hello"), Text.of(" hello ").shrink(CharFragment::isTrim));
    // contrast: trim does not remove \n
    assertEquals(Text.of("\nhello"), Text.of("\nhello").shrink(CharFragment::isTrim));
    assertEquals(Text.of("hello"), Text.of("\nhello").shrink(CharFragment::isStrip));
  }

  @DisplayName("contentMemoryEquals(Text)")
  @Test
  void testContentMemoryEquals() {
    Text original = Text.of("hello world");
    Text slice = original.subSequence(0, 5);
    assertTrue(original.contentMemoryEquals(original));
    assertTrue(slice.contentMemoryEquals(slice));
    assertTrue(original.contentMemoryEquals(slice));
    assertTrue(slice.contentMemoryEquals(original));
    assertTrue(slice.contentMemoryEquals(original.subSequence(2, 4)));

    // different buffers
    Text other = Text.of("abcdefghijklmnopqrstuvwxyz");
    assertFalse(original.contentMemoryEquals(other));
    assertFalse(other.contentMemoryEquals(original));

    // EMPTY shares with EMPTY
    assertTrue(Text.EMPTY.contentMemoryEquals(Text.EMPTY));
  }

  @DisplayName("offsetIn(Text)")
  @Test
  void testOffsetIn() {
    Text original = Text.of("hello world");
    assertEquals(0, original.offsetIn(original));

    Text slice = original.subSequence(6); // "world"
    assertEquals(6, slice.offsetIn(original));

    Text prefix = original.subSequence(0, 5); // "hello"
    assertEquals(0, prefix.offsetIn(original));

    Text middle = original.subSequence(2, 8); // "llo wo"
    assertEquals(2, middle.offsetIn(original));

    // parent is not enclosed by children
    assertEquals(-1, original.offsetIn(slice));
    assertEquals(-1, original.offsetIn(prefix));

    // siblings do not enclose each other
    assertEquals(-1, prefix.offsetIn(slice));
    assertEquals(-1, slice.offsetIn(prefix));

    // different buffer
    Text unrelated = Text.of("something entirely different");
    assertEquals(-1, original.offsetIn(unrelated));
    assertEquals(-1, unrelated.offsetIn(original));
  }

  @DisplayName("skip(CharPredicate)")
  @Test
  void testSkip() {
    assertEquals(3, Text.of("abc").skip(c -> true));
    assertEquals(0, Text.of("abc").skip(c -> false));
    assertEquals(3, Text.of("   abc").skip(c -> c == ' '));
    assertEquals(0, Text.of("abc   ").skip(c -> c == ' '));
    assertEquals(3, Text.of("   ").skip(c -> true));
    assertEquals(3, Text.of("123abc").skip(Character::isDigit));
    assertEquals(-1, Text.of("").skip(c -> true));
  }

  @DisplayName("skip(CharPredicate, int)")
  @Test
  void testSkipWithStart() {
    assertEquals(3, Text.of("123abc").skip(Character::isDigit, 1));
    assertEquals(6, Text.of("123abc").skip(c -> true, 2));
    assertEquals(-1, Text.of("abc").skip(c -> true, 3));
    assertEquals(-1, Text.of("abc").skip(c -> true, -1));
  }

  @DisplayName("skip(CharPredicate, int, int)")
  @Test
  void testSkipWithRange() {
    assertEquals(3, Text.of("abc").skip(c -> true, 0, 3));
    assertEquals(3, Text.of("abc").skip(c -> true, 0, 100));
    assertEquals(2, Text.of("abcdef").skip(c -> c != 'c', 0, 3));
    assertEquals(0, Text.of("abcdef").skip(c -> c == 'c', 0, 3));
    assertEquals(-1, Text.of("abc").skip(c -> true, 3, 100));
  }

  @DisplayName("offsetIn(Text) of zero-length subSequence")
  @Test
  void testOffsetIn_EmptySubSequence() {
    Text t = Text.of("hello world"); // [0, 11)

    // subSequence(k, k) is empty at k
    assertEquals(0, t.subSequence(0, 0).offsetIn(t));
    assertEquals(5, t.subSequence(5, 5).offsetIn(t));
    assertEquals(11, t.subSequence(11, 11).offsetIn(t));
    // subSequence(k) === subSequence(k, length) -> empty at length for k == length
    assertEquals(11, t.subSequence(11).offsetIn(t));

    // all empty slices share the backing buffer
    assertTrue(t.subSequence(0, 0).contentMemoryEquals(t));
    assertTrue(t.subSequence(5, 5).contentMemoryEquals(t));
    assertTrue(t.subSequence(11, 11).contentMemoryEquals(t));

    // zero-length slices at different offsets don't contain each other
    assertEquals(-1, t.subSequence(3, 3).offsetIn(t.subSequence(7, 7)));
    assertEquals(-1, t.subSequence(7, 7).offsetIn(t.subSequence(3, 3)));
    // but each contains itself (position-wise, even when instances differ)
    assertEquals(0, t.subSequence(5, 5).offsetIn(t.subSequence(5, 5)));

    // nested: empty inside a sub-slice is correctly located in the root
    Text word = t.subSequence(6); // "world" = [6, 11)
    assertEquals(0, word.subSequence(0, 0).offsetIn(word));
    assertEquals(6, word.subSequence(0, 0).offsetIn(t));
    assertEquals(5, word.subSequence(5, 5).offsetIn(word));
    assertEquals(11, word.subSequence(5, 5).offsetIn(t));

    // an empty at the end of a parent is inside it (boundary semantics)...
    assertEquals(11, t.subSequence(11, 11).offsetIn(t));
    // ...but not inside a shorter prefix
    assertEquals(-1, t.subSequence(11, 11).offsetIn(t.subSequence(0, 5)));
  }

  @DisplayName("offsetIn(Text) of zero-length slice")
  @Test
  void testOffsetIn_EmptySlice() {
    Text t = Text.of("hello world"); // [0, 11)

    // slice(k) = subSequence(k, length); empty at length for k == length
    assertEquals(11, t.slice(11).offsetIn(t));

    // slice(k, k) is empty at k
    assertEquals(0, t.slice(0, 0).offsetIn(t));
    assertEquals(5, t.slice(5, 5).offsetIn(t));

    // slice(k, e) with e < k clamps to subSequence(k, k) -> empty at k (start)
    assertEquals(5, t.slice(5, 3).offsetIn(t));
    assertEquals(5, t.slice(5, 0).offsetIn(t));
    // ...including when negative e would extend past start
    assertEquals(5, t.slice(5, -10).offsetIn(t));
    assertEquals(5, t.slice(5, -20).offsetIn(t));

    // slice(0, -length) -> empty at start
    assertEquals(0, t.slice(0, -11).offsetIn(t));
  }

  @DisplayName("offsetIn(Text) of trim/strip/shrink to empty")
  @Test
  void testOffsetIn_ShrunkToEmpty() {
    // when every character is removed by shrink, the resulting empty slice
    // points to the END of the shrunk region
    Text spaces = Text.of("   "); // [0, 3)
    assertEquals(3, spaces.trim().offsetIn(spaces));
    assertEquals(3, spaces.strip().offsetIn(spaces));
    assertEquals(3, spaces.shrink(c -> true).offsetIn(spaces));
    assertTrue(spaces.trim().contentMemoryEquals(spaces));

    Text tabs = Text.of("\t\t"); // [0, 2)
    assertEquals(2, tabs.trim().offsetIn(tabs));
    assertEquals(2, tabs.strip().offsetIn(tabs));

    Text newlines = Text.of("\n\n"); // [0, 2)
    // trim only strips spaces and tabs -> \n kept -> returns self
    assertSame(newlines, newlines.trim());
    assertEquals(0, newlines.trim().offsetIn(newlines));
    // strip removes \n -> empty at end
    assertEquals(2, newlines.strip().offsetIn(newlines));

    Text abc = Text.of("abc"); // [0, 3)
    assertEquals(3, abc.shrink(c -> true).offsetIn(abc));
    // nothing to trim/strip -> self() kept, offset preserved
    assertSame(abc, abc.trim());
    assertSame(abc, abc.strip());
    assertEquals(0, abc.trim().offsetIn(abc));

    // trim/strip/shrink on an already-empty slice returns the same slice
    Text t = Text.of("hello");
    Text empty = t.subSequence(2, 2);
    assertSame(empty, empty.trim());
    assertSame(empty, empty.strip());
    assertSame(empty, empty.shrink(c -> true));
    assertEquals(2, empty.trim().offsetIn(t));

    // Text.EMPTY is its own trimmed/stripped/shrunk self
    Text e = Text.EMPTY;
    assertSame(e, e.trim());
    assertSame(e, e.strip());
    assertSame(e, e.shrink(c -> true));
    assertEquals(0, e.trim().offsetIn(e));
  }

  @DisplayName("offsetIn(Text) of zero-length int cache slices")
  @Test
  void testOffsetIn_EmptyIntCache() {
    // Text.of(int) returns new instances sharing the ZERO_TO_999 buffer
    Text fiveA = Text.of(5);
    Text fiveB = Text.of(5);
    Text emptyA = fiveA.subSequence(0, 0);
    Text emptyB = fiveB.subSequence(0, 0);
    assertTrue(emptyA.isEmpty());
    assertTrue(emptyB.isEmpty());

    // same buffer position -> each contains the other (position-wise)
    assertEquals(0, emptyA.offsetIn(fiveA));
    assertEquals(0, emptyA.offsetIn(fiveB));
    assertEquals(0, emptyB.offsetIn(fiveA));
    assertEquals(0, emptyA.offsetIn(emptyB));
    assertEquals(0, emptyB.offsetIn(emptyA));

    // empty at end of the int slice
    Text emptyEnd = fiveA.subSequence(1, 1);
    assertEquals(1, emptyEnd.offsetIn(fiveA));
    // empty at start vs empty at end don't contain each other
    assertEquals(-1, emptyA.offsetIn(emptyEnd));
    assertEquals(-1, emptyEnd.offsetIn(emptyA));

    // 3-digit cache value behaves the same
    Text n = Text.of(123);
    assertEquals(0, n.subSequence(0, 0).offsetIn(n));
    assertEquals(3, n.subSequence(3, 3).offsetIn(n));
  }

  @DisplayName("offsetIn(Text) and concat on empty")
  @Test
  void testOffsetIn_EmptyConcat() {
    Text t = Text.of("hello");
    Text emptySlice = t.subSequence(2, 2);

    // concat("") on a non-empty slice returns self -> offset preserved
    Text hello = t.subSequence(0, 5);
    assertSame(hello, hello.concat(""));
    assertEquals(0, hello.concat("").offsetIn(t));

    // concat("") on an empty slice now preserves the slice, and therefore the
    // buffer + offset, so the position is retained in both directions
    Text concatResult = emptySlice.concat("");
    assertSame(emptySlice, concatResult);
    assertTrue(concatResult.contentMemoryEquals(t));
    assertEquals(2, concatResult.offsetIn(t));
    assertEquals(0, concatResult.offsetIn(emptySlice));
    assertEquals(0, emptySlice.offsetIn(concatResult));

    // a real suffix still allocates a fresh buffer -> offset is lost
    Text appended = emptySlice.concat("x");
    assertEquals("x", appended.toString());
    assertEquals(-1, appended.offsetIn(t));

    // Text.EMPTY is its own empty-slice identity
    assertSame(Text.EMPTY, Text.EMPTY.concat(""));
    assertEquals(0, Text.EMPTY.concat("").offsetIn(Text.EMPTY));
  }
}
