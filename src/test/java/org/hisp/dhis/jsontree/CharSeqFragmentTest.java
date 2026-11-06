package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the default API implementations of {@link CharSeq} and {@link CharSeqFragment} using a
 * minimal implementation that only provides the abstract methods.
 *
 * <p>The {@link TestSeq} implementation deliberately provides nothing beyond {@link
 * CharSequence#length()}, {@link CharSequence#charAt(int)}, {@link CharSeqFragment#self()},
 * {@link CharSeqFragment#empty()} and {@link CharSeqFragment#wrap(char[], int, int)}. All other
 * behaviour is exercised through the default methods of the base interfaces.
 */
class CharSeqFragmentTest {

  /**
   * Minimal implementation of {@link CharSeqFragment} that only provides the abstract methods.
   * Everything else is inherited from the base interfaces.
   */
  private static final class TestSeq implements CharSeqFragment<TestSeq> {

    private final char[] buffer;
    private final int offset;
    private final int length;

    TestSeq(String s) {
      this(s.toCharArray(), 0, s.length());
    }

    TestSeq(char[] buffer, int offset, int length) {
      this.buffer = buffer;
      this.offset = offset;
      this.length = length;
    }

    @Override
    public int length() {
      return length;
    }

    @Override
    public char charAt(int index) {
      if (index < 0 || index >= length) throw new IndexOutOfBoundsException("index " + index);
      return buffer[offset + index];
    }

    @Override
    public TestSeq self() {
      return this;
    }

    @Override
    public TestSeq empty() {
      return new TestSeq(new char[0], 0, 0);
    }

    @Override
    public TestSeq wrap(char[] buffer, int offset, int length) {
      return new TestSeq(buffer, offset, length);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof TestSeq other && contentEquals(other);
    }

    @Override
    public int hashCode() {
      return CharSeq.hashCode(this);
    }

    @Override
    public String toString() {
      return new String(buffer, offset, length);
    }
  }

  private static TestSeq seq(String s) {
    return new TestSeq(s);
  }

  // ---------------------------------------------------------------------------
  // essential methods / self / empty / wrap
  // ---------------------------------------------------------------------------

  @Test
  void testSelfEmptyWrap() {
    TestSeq s = seq("hello");
    assertSame(s, s.self());
    TestSeq e = s.empty();
    assertEquals(0, e.length());
    assertTrue(e.isEmpty());
    assertSame(e, e.self());
    assertEquals("", e.toString());

    char[] buf = "hello".toCharArray();
    TestSeq wrapped = s.wrap(buf, 1, 3);
    assertEquals(3, wrapped.length());
    assertEquals('e', wrapped.charAt(0));
    assertEquals('l', wrapped.charAt(1));
    assertEquals('l', wrapped.charAt(2));
    assertEquals("ell", wrapped.toString());
  }

  @Test
  void testCharAtAndLength() {
    TestSeq s = seq("hello");
    assertEquals(5, s.length());
    assertEquals('h', s.charAt(0));
    assertEquals('o', s.charAt(4));
    assertThrows(IndexOutOfBoundsException.class, () -> s.charAt(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> s.charAt(5));
  }

  // ---------------------------------------------------------------------------
  // subSequence / slice
  // ---------------------------------------------------------------------------

  @Test
  void testSubSequence() {
    TestSeq s = seq("hello");
    assertEquals("ell", s.subSequence(1, 4).toString());
    assertEquals("", s.subSequence(2, 2).toString());
    assertEquals("hello", s.subSequence(0, 5).toString());
    assertSame(s, s.subSequence(0, 5), "whole range returns self");
  }

  @Test
  void testSubSequenceThrows() {
    TestSeq s = seq("hello");
    assertThrows(IndexOutOfBoundsException.class, () -> s.subSequence(-1, 2));
    assertThrows(IndexOutOfBoundsException.class, () -> s.subSequence(0, 6));
    assertThrows(IndexOutOfBoundsException.class, () -> s.subSequence(3, 2));
  }

  @Test
  void testSubSequenceStart() {
    TestSeq s = seq("hello");
    assertEquals("hello", s.subSequence(0).toString());
    assertEquals("ello", s.subSequence(1).toString());
    assertEquals("o", s.subSequence(4).toString());
    assertEquals("", s.subSequence(5).toString());
    assertSame(s, s.subSequence(0));
    assertThrows(IndexOutOfBoundsException.class, () -> s.subSequence(-1));
    assertThrows(IndexOutOfBoundsException.class, () -> s.subSequence(6));
  }

  @Test
  void testSlice() {
    assertEquals("hello", seq("hello").slice(0, 5).toString());
    assertEquals("ello", seq("hello").slice(1, 5).toString());
    assertEquals("ell", seq("hello").slice(1, -1).toString());
    assertEquals("l", seq("hello").slice(2, -2).toString());
    assertEquals("", seq("hello").slice(2, -3).toString());
    assertEquals("", seq("hello").slice(3, -2).toString());
    assertEquals("", seq("hello").slice(3, -3).toString());
  }

  @Test
  void testSlicePattern() {
    List<TestSeq> parts = new ArrayList<>();

    TestSeq foo = seq("foo");
    foo.slice(seq("~."), parts::add);
    assertEquals(1, parts.size());
    assertSame(foo, parts.get(0));

    parts.clear();
    seq("foo.bar").slice(seq("~."), parts::add);
    assertEquals(2, parts.size());
    assertEquals("foo.", parts.get(0).toString());
    assertEquals("bar", parts.get(1).toString());

    parts.clear();
    seq("foo.bar.baz").slice(seq("~."), parts::add);
    assertEquals(3, parts.size());
    assertEquals("foo.", parts.get(0).toString());
    assertEquals("bar.", parts.get(1).toString());
    assertEquals("baz", parts.get(2).toString());
  }

  // ---------------------------------------------------------------------------
  // trim / strip / shrink
  // ---------------------------------------------------------------------------

  @Test
  void testTrim() {
    assertEquals("hello", seq("hello").trim().toString());
    assertEquals("hello", seq(" hello").trim().toString());
    assertEquals("hello", seq("hello ").trim().toString());
    assertEquals("hello", seq(" hello ").trim().toString());
    assertEquals("hello", seq("  hello  ").trim().toString());
    assertEquals("", seq(" ").trim().toString());
    // trim does not remove \n or \r
    assertEquals("\nhello", seq("\nhello").trim().toString());
    assertEquals("hello\r", seq("hello\r").trim().toString());
  }

  @Test
  void testStrip() {
    assertEquals("hello", seq("hello").strip().toString());
    assertEquals("hello", seq(" hello ").strip().toString());
    assertEquals("hello", seq("\thello\t").strip().toString());
    assertEquals("hello", seq("\nhello\n").strip().toString());
    assertEquals("hello", seq("\rhello\r").strip().toString());
    assertEquals("hello", seq(" \t\n\r hello \r\n\t ").strip().toString());
    assertEquals("", seq(" \t\n\r ").strip().toString());
  }

  @Test
  void testShrink() {
    assertEquals("hello", seq("xxhelloxx").shrink(c -> c == 'x').toString());
    assertEquals("hello", seq("123hello456").shrink(Character::isDigit).toString());
    assertEquals("", seq("12345").shrink(Character::isDigit).toString());
    assertEquals("hello", seq("hello").shrink(c -> c == 'x').toString());
    assertEquals("", seq("").shrink(c -> true).toString());
    assertEquals("hello", seq(" hello ").shrink(CharSeqFragment::isStrip).toString());
    assertEquals("hello", seq(" hello ").shrink(CharSeqFragment::isTrim).toString());
  }

  @Test
  void testIsTrimAndIsStrip() {
    assertTrue(CharSeqFragment.isTrim(' '));
    assertTrue(CharSeqFragment.isTrim('\t'));
    assertFalse(CharSeqFragment.isTrim('\n'));
    assertFalse(CharSeqFragment.isTrim('\r'));
    assertFalse(CharSeqFragment.isTrim('a'));

    assertTrue(CharSeqFragment.isStrip(' '));
    assertTrue(CharSeqFragment.isStrip('\t'));
    assertTrue(CharSeqFragment.isStrip('\n'));
    assertTrue(CharSeqFragment.isStrip('\r'));
    assertFalse(CharSeqFragment.isStrip('a'));
  }

  // ---------------------------------------------------------------------------
  // concat / compareTo
  // ---------------------------------------------------------------------------

  @Test
  void testConcat() {
    TestSeq hello = seq("hello");
    assertSame(hello, hello.concat(""));
    assertSame(hello, hello.concat(seq("")));
    assertEquals("hello", seq("").concat("hello").toString());
    assertEquals("hello world", hello.concat(" world").toString());
    assertEquals("hello world", hello.concat(seq(" world")).toString());
  }

  @Test
  void testCompareTo() {
    assertTrue(seq("a").compareTo(seq("b")) < 0);
    assertTrue(seq("b").compareTo(seq("a")) > 0);
    assertEquals(0, seq("abc").compareTo(seq("abc")));
    assertTrue(seq("abc").compareTo(seq("abcd")) < 0);
    assertTrue(seq("abcd").compareTo(seq("abc")) > 0);
    assertTrue(seq("").compareTo(seq("a")) < 0);
    assertTrue(seq("a").compareTo(seq("")) > 0);
  }

  // ---------------------------------------------------------------------------
  // indexOf / lastIndexOf / contains
  // ---------------------------------------------------------------------------

  @Test
  void testIndexOfChar() {
    TestSeq t = seq("hello");
    assertEquals(0, t.indexOf('h'));
    assertEquals(2, t.indexOf('l'));
    assertEquals(-1, t.indexOf('z'));
    assertEquals(2, t.indexOf('l', 0));
    assertEquals(3, t.indexOf('l', 3));
    assertEquals(-1, t.indexOf('l', 4));
    assertEquals(-1, t.indexOf('l', 4, 5));
    assertEquals(4, seq("hello world").indexOf('o', 2, 6));
  }

  @Test
  void testLastIndexOfChar() {
    TestSeq t = seq("hello");
    assertEquals(3, t.lastIndexOf('l'));
    assertEquals(1, t.lastIndexOf('e'));
    assertEquals(-1, t.lastIndexOf('z'));
    assertEquals(3, t.lastIndexOf('l', 4));
    assertEquals(2, t.lastIndexOf('l', 2));
    assertEquals(-1, t.lastIndexOf('l', 1));
    assertEquals(3, seq("hello world").lastIndexOf('l', 4, 0));
  }

  @Test
  void testContains() {
    TestSeq t = seq("hello");
    assertTrue(t.contains('h'));
    assertFalse(t.contains('z'));
    assertTrue(t.contains("ell"));
    assertTrue(t.contains(""));
    assertFalse(t.contains("xyz"));
    assertTrue(t.contains(Character::isLetter));
    assertFalse(t.contains(Character::isDigit));
    assertFalse(seq("").contains(c -> true));
  }

  @Test
  void testIndexOfCharSequence() {
    TestSeq t = seq("hello world hello");
    assertEquals(0, t.indexOf("hello"));
    assertEquals(6, t.indexOf("world"));
    assertEquals(2, t.indexOf("llo"));
    assertEquals(-1, t.indexOf("xyz"));
    assertEquals(0, t.indexOf(""));
    assertEquals(12, t.indexOf("hello", 1));
    assertEquals(-1, t.indexOf("world", 8));
    assertEquals(12, t.indexOf("hello", 8, 18));
    assertEquals(-1, t.indexOf("hello", 0, 4));
  }

  @Test
  void testLastIndexOfCharSequence() {
    TestSeq t = seq("hello world hello");
    assertEquals(12, t.lastIndexOf("hello"));
    assertEquals(6, t.lastIndexOf("world"));
    assertEquals(14, t.lastIndexOf("llo"));
    assertEquals(-1, t.lastIndexOf("xyz"));
    assertEquals(0, t.lastIndexOf(""));
    assertEquals(0, t.lastIndexOf("hello", 10));
    assertEquals(-1, t.lastIndexOf("hello", -1));
    assertEquals(0, t.lastIndexOf("hello", 10, 0));
  }

  // ---------------------------------------------------------------------------
  // startsWith / endsWith / contentEquals / regionMatches
  // ---------------------------------------------------------------------------

  @Test
  void testStartsWithAndEndsWith() {
    TestSeq t = seq("hello");
    assertTrue(t.startsWith("he"));
    assertTrue(t.startsWith(""));
    assertFalse(t.startsWith("el"));
    assertTrue(t.startsWith("el", 1));
    assertTrue(t.startsWith("lo", 3));

    assertTrue(t.endsWith("lo"));
    assertTrue(t.endsWith(""));
    assertFalse(t.endsWith("el"));
  }

  @Test
  void testContentEquals() {
    TestSeq t = seq("hello");
    assertTrue(t.contentEquals("hello"));
    assertTrue(t.contentEquals(seq("hello")));
    assertFalse(t.contentEquals("world"));
    assertFalse(t.contentEquals("hell"));
    assertFalse(t.contentEquals("helloo"));
    assertTrue(seq("").contentEquals(""));
  }

  @Test
  void testRegionMatches() {
    TestSeq t = seq("hello world");
    assertTrue(t.regionMatches(6, "world"));
    assertTrue(t.regionMatches(6, "beautiful world", 10, 5));
    assertFalse(t.regionMatches(6, "world", 1, 4));
    assertFalse(t.regionMatches(0, "hello", 2, 3));
    // negative / out-of-range guards
    assertFalse(t.regionMatches(-1, "hello"));
    assertFalse(t.regionMatches(0, "hello", -1, 5));
    assertFalse(t.regionMatches(10, "world"));
    // zero-length always matches when indices are valid
    assertTrue(t.regionMatches(3, "xyz", 1, 0));
  }

  // ---------------------------------------------------------------------------
  // matches
  // ---------------------------------------------------------------------------

  @Test
  void testMatches() {
    assertTrue(seq("hello").matches("hello"));
    assertFalse(seq("hello").matches("world"));
    assertFalse(seq("hello").matches("hell"));
    assertFalse(seq("hello").matches("helloo"));
    assertTrue(seq("").matches(""));
    assertFalse(seq("").matches("a"));
    assertFalse(seq("a").matches(""));
  }

  @Test
  void testMatchesSubrange() {
    assertTrue(seq("hello").matches("hello world", 0, 5));
    assertTrue(seq("hello").matches("say hello", 4, 5));
    assertFalse(seq("hello").matches("hello world", 0, 4));
    assertFalse(seq("hello").matches("say hello", 3, 5));
  }

  // ---------------------------------------------------------------------------
  // toCharArray / copyTo
  // ---------------------------------------------------------------------------

  @Test
  void testToCharArray() {
    assertArrayEquals(new char[] {'h', 'e', 'l', 'l', 'o'}, seq("hello").toCharArray());
    assertArrayEquals(new char[0], seq("").toCharArray());
  }

  @Test
  void testCopyTo() {
    TestSeq s = seq("hello");
    char[] buf = new char[10];
    s.copyTo(buf, 2);
    assertArrayEquals(new char[] {0, 0, 'h', 'e', 'l', 'l', 'o', 0, 0, 0}, buf);
  }

  @Test
  void testCopyToSubrange() {
    TestSeq s = seq("hello");
    char[] buf = new char[10];
    s.copyTo(buf, 2, 1, 3);
    assertArrayEquals(new char[] {0, 0, 'e', 'l', 'l', 0, 0, 0, 0, 0}, buf);
  }

  // ---------------------------------------------------------------------------
  // classification / parsing
  // ---------------------------------------------------------------------------

  @Test
  void testIsTextualInteger() {
    assertTrue(seq("123").isTextualInteger());
    assertTrue(seq("-123").isTextualInteger());
    assertTrue(seq("+123").isTextualInteger());
    assertTrue(seq("0").isTextualInteger());
    assertTrue(seq("007").isTextualInteger());
    assertFalse(seq("").isTextualInteger());
    assertFalse(seq("12.3").isTextualInteger());
    assertFalse(seq("12a").isTextualInteger());
    assertFalse(seq("--1").isTextualInteger());
  }

  @Test
  void testIsNumericInteger() {
    assertTrue(seq("123").isNumericInteger());
    assertTrue(seq("-123").isNumericInteger());
    assertTrue(seq("0.0").isNumericInteger());
    assertTrue(seq("+1.0").isNumericInteger());
    assertTrue(seq("12.00").isNumericInteger());
    assertFalse(seq("").isNumericInteger());
    assertFalse(seq("12.3").isNumericInteger());
    assertFalse(seq("1.0000001").isNumericInteger());
  }

  @Test
  void testIsTextualDecimal() {
    assertTrue(seq("0").isTextualDecimal());
    assertTrue(seq(".0").isTextualDecimal());
    assertTrue(seq("123.0").isTextualDecimal());
    assertTrue(seq("123e-2").isTextualDecimal());
    assertTrue(seq("-123.0001E-23").isTextualDecimal());
    assertFalse(seq("").isTextualDecimal());
    assertFalse(seq(".").isTextualDecimal());
    assertFalse(seq("--0").isTextualDecimal());
    assertFalse(seq("5.x").isTextualDecimal());
  }

  @Test
  void testIsSpecialDecimal() {
    assertTrue(seq("NaN").isSpecialDecimal());
    assertTrue(seq("Infinity").isSpecialDecimal());
    assertTrue(seq("-Infinity").isSpecialDecimal());
    assertFalse(seq("nan").isSpecialDecimal());
    assertFalse(seq("").isSpecialDecimal());
  }

  @Test
  void testParseBoolean() {
    assertTrue(seq("true").parseBoolean());
    assertTrue(seq("TRUE").parseBoolean());
    assertFalse(seq("false").parseBoolean());
    assertFalse(seq("FALSE").parseBoolean());
    assertThrows(IllegalArgumentException.class, () -> seq("t").parseBoolean());
    assertThrows(IllegalArgumentException.class, () -> seq("fear").parseBoolean());
  }

  @Test
  void testParseInt() {
    assertEquals(123, seq("123").parseInt());
    assertEquals(-123, seq("-123").parseInt());
    assertEquals(123, seq("+123").parseInt());
    assertEquals(0, seq("0").parseInt());
    assertEquals(7, seq("007").parseInt());
    assertThrows(NumberFormatException.class, () -> seq("").parseInt());
    assertThrows(NumberFormatException.class, () -> seq("abc").parseInt());
  }

  @Test
  void testParseLong() {
    assertEquals(123L, seq("123").parseLong());
    assertEquals(-123L, seq("-123").parseLong());
    assertEquals(0L, seq("0").parseLong());
    assertThrows(NumberFormatException.class, () -> seq("").parseLong());
  }

  @Test
  void testParseDouble() {
    assertEquals(123.0, seq("123").parseDouble(), 0.0);
    assertEquals(123.5, seq("123.5").parseDouble(), 0.0);
    assertEquals(-123.5, seq("-123.5").parseDouble(), 0.0);
    assertEquals(123.0, seq("1.23e2").parseDouble(), 0.001);
  }

  @Test
  void testParseNumber() {
    assertEquals(123, seq("123").parseNumber());
    assertEquals(123.5d, seq("123.5").parseNumber().doubleValue());
    assertEquals(-123, seq("-123").parseNumber());
  }

  // ---------------------------------------------------------------------------
  // statics
  // ---------------------------------------------------------------------------

  @Test
  void testHashCodeStatic() {
    assertEquals(0, CharSeq.hashCode(seq("")));
    assertEquals(CharSeq.hashCode(seq("hello")), CharSeq.hashCode(seq("hello")));
    // sample based for long sequences - deterministic
    assertEquals(
        CharSeq.hashCode(seq("a".repeat(100))), CharSeq.hashCode(seq("a".repeat(100))));
    // short sequences use full content
    assertNotEquals(CharSeq.hashCode(seq("hello")), CharSeq.hashCode(seq("world")));
  }

  @Test
  void testCheckSubSequence() {
    assertThrows(
        IndexOutOfBoundsException.class, () -> CharSeqFragment.checkSubSequence(-1, 0, 5));
    assertThrows(
        IndexOutOfBoundsException.class, () -> CharSeqFragment.checkSubSequence(0, -1, 5));
    assertThrows(
        IndexOutOfBoundsException.class, () -> CharSeqFragment.checkSubSequence(0, 6, 5));
    assertThrows(
        IndexOutOfBoundsException.class, () -> CharSeqFragment.checkSubSequence(3, 2, 5));

    assertDoesNotThrow(() -> CharSeqFragment.checkSubSequence(0, 5, 5));
    assertDoesNotThrow(() -> CharSeqFragment.checkSubSequence(2, 2, 5));
    assertDoesNotThrow(() -> CharSeqFragment.checkSubSequence(0, 0, 0));
  }

  // ---------------------------------------------------------------------------
  // skip
  // ---------------------------------------------------------------------------

  @Test
  void testSkip() {
    // skip past characters matching the predicate; return first non-matching index
    assertEquals(3, seq("abc").skip(c -> true));
    assertEquals(0, seq("abc").skip(c -> false));
    assertEquals(3, seq("   abc").skip(c -> c == ' '));
    assertEquals(0, seq("abc   ").skip(c -> c == ' '));
    assertEquals(3, seq("   ").skip(c -> true));
    assertEquals(3, seq("   ").skip(c -> c == ' '));
    assertEquals(3, seq("123abc").skip(Character::isDigit));
    assertEquals(1, seq("a.b.c").skip(c -> c != '.'));

    // empty sequence -> -1 (startIndex 0 >= length 0)
    assertEquals(-1, seq("").skip(c -> true));
  }

  @Test
  void testSkipWithStartIndex() {
    assertEquals(2, seq("  abc").skip(c -> c == ' ', 1));
    assertEquals(3, seq("123abc").skip(Character::isDigit, 1));
    assertEquals(3, seq("123abc").skip(Character::isDigit, 2));
    assertEquals(6, seq("123abc").skip(c -> true, 2));

    // start index == length or beyond -> -1
    assertEquals(-1, seq("abc").skip(c -> true, 3));
    assertEquals(-1, seq("abc").skip(c -> true, 4));
    // negative start index -> -1
    assertEquals(-1, seq("abc").skip(c -> true, -1));
    // empty sequence with startIndex 0 -> -1
    assertEquals(-1, seq("").skip(c -> true, 0));
  }

  @Test
  void testSkipWithRange() {
    // finds first non-matching char within the window
    assertEquals(2, seq("abcdef").skip(c -> c != 'c', 0, 3));
    assertEquals(0, seq("abcdef").skip(c -> c == 'c', 0, 3));
    assertEquals(2, seq("abcdef").skip(c -> c != 'c', 2, 5));

    // all characters in [startIndex, endIndex) match -> returns endIndex
    assertEquals(3, seq("abcdef").skip(Character::isLetter, 0, 3));
    assertEquals(5, seq("abcdef").skip(Character::isLetter, 2, 5));

    // endIndex beyond length is clamped to length
    assertEquals(3, seq("abc").skip(c -> true, 0, 100));
    assertEquals(3, seq("abc").skip(c -> true, 1, 100));

    // startIndex at/after length -> -1
    assertEquals(-1, seq("abc").skip(c -> true, 3, 3));
    assertEquals(-1, seq("abc").skip(c -> true, 3, 100));
  }
}