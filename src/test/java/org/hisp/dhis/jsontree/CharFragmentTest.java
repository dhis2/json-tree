package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the default API implementations of {@link CharSeq} and {@link CharFragment} using a
 * minimal implementation that only provides the abstract methods.
 *
 * <p>The {@link TestSeq} implementation deliberately provides nothing beyond {@link
 * CharSequence#length()}, {@link CharSequence#charAt(int)}, {@link CharFragment#self()}
 * and {@link CharFragment#wrap(char[], int, int)}. All other
 * behaviour is exercised through the default methods of the base interfaces.
 */
class CharFragmentTest {

  /**
   * Minimal implementation of {@link CharFragment} that only provides the abstract methods.
   * Everything else is inherited from the base interfaces.
   */
  private static final class TestSeq implements CharFragment<TestSeq> {

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
    assertEquals("hello", seq(" hello ").shrink(CharFragment::isStrip).toString());
    assertEquals("hello", seq(" hello ").shrink(CharFragment::isTrim).toString());
  }

  @DisplayName("shrink(test, left, right): stripTrailing on all-whitespace is empty")
  @Test
  void testShrink_StripTrailingAllWhitespace() {
    assertEquals("", seq("   ").stripTrailing().toString());   // Bug: was " "
    assertTrue(seq("   ").stripTrailing().isEmpty());
    assertTrue(seq("\t\n\r ").stripTrailing().isEmpty());
    // mirror
    assertEquals("", seq("   ").stripLeading().toString());
    assertEquals("", seq("   ").strip().toString());
  }

  @DisplayName("stripTrailing / stripLeading agree with String on content")
  @Test
  void testStripDirections_AgreeWithString() {
    String[] samples = {
        "", " ", "   ", "\t\n\r ", "a", " a", "a ", " a ",
        "  x  ", "x  ", "  x", "abc", "  abc  def  "
    };
    for (String s : samples) {
      assertEquals(s.stripTrailing(), seq(s).stripTrailing().toString(),
          "stripTrailing(\"" + s + "\")");
      assertEquals(s.stripLeading(), seq(s).stripLeading().toString(),
          "stripLeading(\"" + s + "\")");
    }
  }

  @Test
  void testIsTrimAndIsStrip() {
    assertTrue(CharFragment.isTrim(' '));
    assertTrue(CharFragment.isTrim('\t'));
    assertFalse(CharFragment.isTrim('\n'));
    assertFalse(CharFragment.isTrim('\r'));
    assertFalse(CharFragment.isTrim('a'));

    assertTrue(CharFragment.isStrip(' '));
    assertTrue(CharFragment.isStrip('\t'));
    assertTrue(CharFragment.isStrip('\n'));
    assertTrue(CharFragment.isStrip('\r'));
    assertFalse(CharFragment.isStrip('a'));
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
    assertTrue(seq("123").isIntegerLiteral());
    assertTrue(seq("-123").isIntegerLiteral());
    assertTrue(seq("+123").isIntegerLiteral());
    assertTrue(seq("0").isIntegerLiteral());
    assertTrue(seq("007").isIntegerLiteral());
    assertFalse(seq("").isIntegerLiteral());
    assertFalse(seq("12.3").isIntegerLiteral());
    assertFalse(seq("12a").isIntegerLiteral());
    assertFalse(seq("--1").isIntegerLiteral());
  }

  @Test
  void testIsNumericInteger() {
    assertTrue(seq("123").isIntegerValue());
    assertTrue(seq("-123").isIntegerValue());
    assertTrue(seq("0.0").isIntegerValue());
    assertTrue(seq("+1.0").isIntegerValue());
    assertTrue(seq("12.00").isIntegerValue());
    assertFalse(seq("").isIntegerValue());
    assertFalse(seq("12.3").isIntegerValue());
    assertFalse(seq("1.0000001").isIntegerValue());
  }

  @Test
  void testIsTextualDecimal() {
    assertTrue(seq("0").isNumberLiteral());
    assertTrue(seq(".0").isNumberLiteral());
    assertTrue(seq("123.0").isNumberLiteral());
    assertTrue(seq("123e-2").isNumberLiteral());
    assertTrue(seq("-123.0001E-23").isNumberLiteral());
    assertFalse(seq("").isNumberLiteral());
    assertFalse(seq(".").isNumberLiteral());
    assertFalse(seq("--0").isNumberLiteral());
    assertFalse(seq("5.x").isNumberLiteral());
  }

  @Test
  void testIsSpecialDecimal() {
    assertTrue(seq("NaN").isSpecialDoubleValue());
    assertTrue(seq("Infinity").isSpecialDoubleValue());
    assertTrue(seq("-Infinity").isSpecialDoubleValue());
    assertFalse(seq("nan").isSpecialDoubleValue());
    assertFalse(seq("").isSpecialDoubleValue());
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
        IndexOutOfBoundsException.class, () -> CharFragment.checkSubSequence(-1, 0, 5));
    assertThrows(
        IndexOutOfBoundsException.class, () -> CharFragment.checkSubSequence(0, -1, 5));
    assertThrows(
        IndexOutOfBoundsException.class, () -> CharFragment.checkSubSequence(0, 6, 5));
    assertThrows(
        IndexOutOfBoundsException.class, () -> CharFragment.checkSubSequence(3, 2, 5));

    assertDoesNotThrow(() -> CharFragment.checkSubSequence(0, 5, 5));
    assertDoesNotThrow(() -> CharFragment.checkSubSequence(2, 2, 5));
    assertDoesNotThrow(() -> CharFragment.checkSubSequence(0, 0, 0));
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

  @Test
  void testContentEqualsIgnoreCaseASCII_Letters() {
    assertTrue(Text.of("hello").contentEqualsIgnoreCaseASCII("HELLO"));
    assertTrue(Text.of("TRUE").contentEqualsIgnoreCaseASCII("true"));
    assertTrue(Text.of("False").contentEqualsIgnoreCaseASCII("fAlSe"));
  }

  @Test
  void testContentEqualsIgnoreCaseASCII_NotLetters() {
    // the | 0x20 trick must not collapse punctuation
    assertFalse(Text.of("[").contentEqualsIgnoreCaseASCII("{"));
    assertFalse(Text.of("_").contentEqualsIgnoreCaseASCII("\u007f"));
    assertFalse(Text.of("1").contentEqualsIgnoreCaseASCII("!"));
    // non-ASCII compared exactly
    assertFalse(Text.of("É").contentEqualsIgnoreCaseASCII("é"));
  }

  @Test
  void testContentEqualsIgnoreCaseASCII_Lengths() {
    assertFalse(Text.of("true").contentEqualsIgnoreCaseASCII("truex"));
    assertFalse(Text.of("").contentEqualsIgnoreCaseASCII("a"));
  }

  @Test
  void testParseBoolean_TrailingGarbage_Throws() {
    // regression: the previous implementation returned true for any 5-char
    // input starting with "true" (it never checked charAt(4))
    assertThrows(IllegalArgumentException.class, () -> Text.of("truex").parseBoolean());
    assertThrows(IllegalArgumentException.class, () -> Text.of("TRUE!").parseBoolean());
  }

  @DisplayName("count(CharSequence, int)")
  @Test
  void testCount() {
    TestSeq t = seq("ababab");
    assertEquals(0, t.count("xx", 0));
    assertEquals(3, t.count("ab", 0));
    assertEquals(2, t.count("ab", 1));   // skips the match at 0
    assertEquals(2, t.count("ab", 2));   // skips the match at 0 too
    assertEquals(1, t.count("ab", 3));   // only the match at 2 remains
    assertEquals(0, t.count("ab", 5));   // past all matches
    assertEquals(0, t.count("ab", 6));   // == length
    assertEquals(0, t.count("ab", 100)); // > length
    // non-overlapping: the second "ab" in "aaaa" is not counted after consuming the first
    assertEquals(2, seq("aaaa").count("aa", 0));
    // adjacency: back-to-back non-overlapping
    assertEquals(2, seq("abab").count("ab", 0));
    // fragment longer than subject
    assertEquals(0, seq("ab").count("abc", 0));
    // match touching end is counted once, not twice
    assertEquals(1, seq("abc").count("c", 0));
    // empty subject
    assertEquals(0, seq("").count("a", 0));
  }

  @DisplayName("count(CharSequence, int) with empty fragment")
  @Test
  void testCount_EmptyFragment() {
    // the empty fragment matches at every position; defining it to have 0 occurrences
    // is the only finite, useful answer (and prevents the indexOf("",k)==k fixed point)
    assertEquals(0, seq("abc").count("", 0));
    assertEquals(0, seq("abc").count("", 1));
    assertEquals(0, seq("abc").count("", 3));   // == length, would otherwise loop forever
    assertEquals(0, seq("").count("", 0));
    // a StringBuilder that is empty is the same case
    assertEquals(0, seq("abc").count(new StringBuilder(), 0));
  }

  @DisplayName("count(CharSequence) delegates to startIndex 0")
  @Test
  void testCount_NoStartIndex() {
    TestSeq t = seq("ababab");
    assertEquals(3, t.count("ab"));
    assertEquals(t.count("ab", 0), t.count("ab"));
  }

  @DisplayName("count() agrees with a manual loop using indexOf")
  @Test
  void testCount_AgreesWithIndexOf() {
    String[] subjects = { "", "a", "aa", "ab", "aab", "abab", "ababab", "aaaa", "banana" };
    String[] fragments = { "a", "b", "aa", "ab", "abab", "na", "banana" };
    for (String s : subjects) {
      TestSeq seq = seq(s);
      for (String f : fragments) {
        int expected = 0;
        for (int i = s.indexOf(f); i >= 0; i = s.indexOf(f, i + f.length())) expected++;
        assertEquals(expected, seq.count(f, 0), "count(" + f + ") in \"" + s + "\"");
        assertEquals(expected, seq.count(f), "count(" + f + ") in \"" + s + "\"");
      }
    }
  }

  @DisplayName("every(CharPredicate)")
  @Test
  void testEvery() {
    // non-empty, all true
    assertTrue(seq("abc").every(c -> true));
    assertTrue(seq("abc").every(Character::isLetter));
    assertTrue(seq("123").every(Character::isDigit));
    // non-empty, one counterexample
    assertFalse(seq("abc").every(c -> false));
    assertFalse(seq("123a").every(Character::isDigit));
    // counterexample only at the first position
    assertTrue(seq("zbc").every(Character::isLetter));
    assertFalse(seq("zbc").every(c -> c == 'z'));
    // counterexample only at the last position
    assertFalse(seq("abz").every(c -> c != 'z'));
    // single character
    assertTrue(seq("a").every(c -> c == 'a'));
    assertFalse(seq("a").every(c -> c == 'b'));
  }

  @DisplayName("every(CharPredicate) on empty is vacuously true")
  @Test
  void testEvery_Empty() {
    assertTrue(seq("").every(c -> true));
    assertTrue(seq("").every(c -> false));      // no character violates the test
    assertTrue(seq("").every(c -> c == 'z'));
    assertTrue(seq("").every(Character::isDigit));
  }

  @DisplayName("every(CharPredicate) stops at the first counterexample")
  @Test
  void testEvery_ShortCircuits() {
    int[] calls = {0};
    assertFalse(seq("abcdef").every(c -> { calls[0]++; return false; }));
    assertEquals(1, calls[0], "must stop after the first failing character");

    calls[0] = 0;
    assertTrue(seq("abc").every(c -> { calls[0]++; return true; }));
    assertEquals(3, calls[0], "must inspect every character when all pass");

    // first char passes, second fails -> two calls, not three or six
    calls[0] = 0;
    assertFalse(seq("abcdef").every(c -> { calls[0]++; return c != 'b'; }));
    assertEquals(2, calls[0]);
  }

  @DisplayName("every(CharPredicate) is the dual of contains(CharPredicate)")
  @Test
  void testEvery_DualOfContains() {
    String[] samples = { "", "a", "ab", "abc", "123", "12a", "  ", "\t\n" };
    CharPredicate[] predicates = {
        c -> true,
        c -> false,
        Character::isLetter,
        Character::isDigit,
        c -> c == 'a',
        c -> c != 'a',
        CharFragment::isTrim,
        CharFragment::isStrip,
    };
    for (String s : samples) {
      TestSeq seq = seq(s);
      for (CharPredicate p : predicates) {
        assertEquals(
            seq.every(p),
            !seq.contains(c -> !p.test(c)),
            "every vs contains dual failed for \"" + s + "\"");
      }
    }
  }

  @DisplayName("isBlank() on non-blank sequences")
  @Test
  void testIsBlank_False() {
    assertFalse(seq("a").isBlank());
    assertFalse(seq(" a ").isBlank());
    assertFalse(seq("\ta\t").isBlank());
    assertFalse(seq(" \n x \r ").isBlank());
    // one non-blank char anywhere is enough
    assertFalse(seq(" x").isBlank());
    assertFalse(seq("x ").isBlank());
    assertFalse(seq("  x  ").isBlank());
  }

  @DisplayName("isBlank() on blank sequences")
  @Test
  void testIsBlank_True() {
    assertTrue(seq(" ").isBlank());
    assertTrue(seq("   ").isBlank());
    assertTrue(seq("\t").isBlank());
    assertTrue(seq("\n").isBlank());
    assertTrue(seq("\r").isBlank());
    assertTrue(seq(" \t\n\r").isBlank());
    assertTrue(seq("\r\n\t ").isBlank());
    assertTrue(seq("\n\n\n").isBlank());
  }

  @DisplayName("isBlank() on empty is vacuously true")
  @Test
  void testIsBlank_Empty() {
    assertTrue(seq("").isBlank());
  }

  @DisplayName("isBlank() agrees with every(isBlank)")
  @Test
  void testIsBlank_AgreesWithEvery() {
    String[] samples = {
        "", " ", "\t", "\n", "\r",
        "  \t\n\r ", "a", " a", "a ", " \ta",
        "abc", " \u00A0 ",       // NBSP is NOT blank here
        " \f ",                    // form feed is NOT blank here (String.isBlank would say true)
        " \u000B "                 // vertical tab is NOT blank here
    };
    for (String s : samples) {
      assertEquals(seq(s).every(CharSeq::isBlank), seq(s).isBlank(),
          "isBlank mismatch for \"" + s + "\"");
    }
  }

  @DisplayName("isBlank() diverges from String.isBlank() on form feed / vertical tab")
  @Test
  void testIsBlank_DivergesFromStringOnAsciiControls() {
    // documented divergence: String.isBlank() says true for these, we say false
    assertFalse(seq("\f").isBlank());
    assertFalse(seq("\u000B").isBlank());
    assertTrue("\f".isBlank(), "String.isBlank still true for form feed");

    // the JDK-canonical blank chars we agree on
    for (char c : new char[] {' ', '\t', '\n', '\r'}) {
      assertTrue(seq(String.valueOf(c)).isBlank(), "single char " + (int) c);
    }
  }

  @DisplayName("isBlank(char) matches the set used by every(isBlank)")
  @Test
  void testIsBlankChar() {
    assertTrue(CharSeq.isBlank(' '));
    assertTrue(CharSeq.isBlank('\t'));
    assertTrue(CharSeq.isBlank('\n'));
    assertTrue(CharSeq.isBlank('\r'));

    assertFalse(CharSeq.isBlank('a'));
    assertFalse(CharSeq.isBlank('\f'));
    assertFalse(CharSeq.isBlank('\u000B'));
    assertFalse(CharSeq.isBlank('\u001C'));
    assertFalse(CharSeq.isBlank('\u00A0'));
  }

  @DisplayName("isStrip(char) agrees with CharSeq.isBlank(char)")
  @Test
  void testIsStrip_AgreesWithIsBlank() {
    // whole ASCII range, so no char slips through a future "improvement" of one
    // predicate without the other
    for (int i = 0; i < 128; i++) {
      char c = (char) i;
      assertEquals(CharSeq.isBlank(c), CharFragment.isStrip(c),
          "isStrip/isBlank diverged for char " + i + " ('" + c + "')");
    }
    // spot-check the divergence-vs-String cases from the audit still agree
    assertEquals(CharSeq.isBlank('\f'), CharFragment.isStrip('\f'));        // both false
    assertEquals(CharSeq.isBlank('\u000B'), CharFragment.isStrip('\u000B')); // both false
    assertEquals(CharSeq.isBlank('\u00A0'), CharFragment.isStrip('\u00A0')); // both false
  }

  @DisplayName("isBlank() is equivalent to strip().isEmpty()")
  @Test
  void testIsBlank_EquivalentToStripEmpty() {
    String[] samples = {
        "", " ", "  ", "\t", "\n", "\r",
        " \t\n\r ", "\r\n\t ", "\n\n\n",
        "a", " a", "a ", " a ", "abc",
        "\f", "\u000B", " \u00A0 "       // ASCII controls that String considers blank
    };
    for (String s : samples) {
      assertEquals(seq(s).strip().isEmpty(), seq(s).isBlank(),
          "isBlank vs strip().isEmpty() mismatch for \"" + s + "\"");
    }
  }

  @DisplayName("replace(char, char): general case")
  @Test
  void testReplace() {
    assertEquals(seq("banana"), seq("banana").replace('a', 'a'));
    assertEquals(seq("bonono"), seq("banana").replace('a', 'o'));
    assertEquals(seq("bonono"), seq("banana").replace('a', 'o'));
    assertEquals(seq("bAnAnA"), seq("banana").replace('a', 'A'));
    assertEquals(seq("b.n.n."), seq("banana").replace('a', '.'));
    // replace every occurrence, including adjacent ones
    assertEquals(seq("xxxx"), seq("aaaa").replace('a', 'x'));
    // single-char sequence
    assertEquals(seq("x"), seq("a").replace('a', 'x'));
    // no match -> unchanged content
    assertEquals(seq("banana"), seq("banana").replace('z', 'x'));
  }

  @DisplayName("replace(char, char): no-match returns self (identity)")
  @Test
  void testReplace_NoMatchReturnsSelf() {
    TestSeq t = seq("banana");
    assertSame(t, t.replace('z', 'x'));
    assertSame(t, t.replace('Z', 'x'));
    TestSeq empty = seq("");
    assertSame(empty, empty.replace('a', 'x'));
  }

  @DisplayName("replace(char, char): oldChar == newChar returns self (identity)")
  @Test
  void testReplace_SameCharReturnsSelf() {
    TestSeq t = seq("banana");
    // present
    assertSame(t, t.replace('a', 'a'));
    // absent
    assertSame(t, t.replace('z', 'z'));
    // empty
    TestSeq empty = seq("");
    assertSame(empty, empty.replace('a', 'a'));
  }

  @DisplayName("replace(char, char): result has fresh buffer")
  @Test
  void testReplace_ResultHasFreshBuffer() {
    TestSeq original = seq("banana");
    TestSeq replaced = original.replace('a', 'o');
    assertNotSame(original, replaced);
    // the source is unmodified
    assertEquals(seq("banana"), original);
    assertEquals("bonono", replaced.toString());
  }

  @DisplayName("replace(CharSequence, CharSequence): basic substitution")
  @Test
  void testReplaceSeq() {
    assertEquals(seq("bonono"), seq("banana").replace("a", "o"));
    assertEquals(seq("heLLo"), seq("hello").replace("l", "L"));           // adjacent
    assertEquals(seq("bbbb"), seq("aaaa").replace("a", "b"));             // all
    assertEquals(seq("xx"), seq("abcabc").replace("abc", "x"));           // from start, repeated
    assertEquals(seq("xbc"), seq("abc").replace("a", "x"));               // match at index 0
    assertEquals(seq("abx"), seq("abc").replace("c", "x"));               // match at last index
    assertEquals(seq("x"), seq("abc").replace("abc", "x"));               // whole sequence
    assertEquals(seq("axyzc"), seq("abc").replace("b", "xyz"));           // grow
    assertEquals(seq(""), seq("abcabc").replace("abc", ""));              // shrink to empty
    assertEquals(seq("xx"), seq("xabcabcx").replace("abc", ""));          // shrink to non-empty
    assertEquals(seq("heLLo world"), seq("hello world").replace("ll", "LL"));
    assertEquals(seq("ba"), seq("aaa").replace("aa", "b"));               // non-overlap from left
    assertEquals(seq("Xba"), seq("ababa").replace("aba", "X"));           // non-overlap, tail kept
  }

  @DisplayName("replace(CharSequence, CharSequence): no match returns self")
  @Test
  void testReplaceSeq_NoMatchReturnsSelf() {
    TestSeq t = seq("hello");
    assertSame(t, t.replace("xyz", "Q"));
    assertSame(t, t.replace("hx", "Q"));
    assertSame(t, t.replace("hello world", "Q"));
    TestSeq empty = seq("");
    assertSame(empty, empty.replace("a", "b"));
  }

  @DisplayName("replace(CharSequence, CharSequence): empty target returns self")
  @Test
  void testReplaceSeq_EmptyTargetReturnsSelf() {
    TestSeq t = seq("abc");
    assertSame(t, t.replace("", "X"));       // no insertion at every position (diverges from String)
    assertSame(t, t.replace("", ""));
    TestSeq empty = seq("");
    assertSame(empty, empty.replace("", "X"));
  }

  @DisplayName("replace(CharSequence, CharSequence): empty replacement removes")
  @Test
  void testReplaceSeq_EmptyReplacement() {
    assertEquals(seq("bc"), seq("abc").replace("a", ""));
    assertEquals(seq("ac"), seq("abc").replace("b", ""));
    assertEquals(seq("ab"), seq("abc").replace("c", ""));
    assertEquals(seq(""), seq("aaaa").replace("aa", ""));      // non-overlap
    assertEquals(seq("a"), seq("aaa").replace("aa", ""));     // first two removed, one remains — wait, "aaa".replace("aa","") in String = "a"
  }

  @DisplayName("replace(CharSequence, CharSequence): result has fresh buffer")
  @Test
  void testReplaceSeq_ResultHasFreshBuffer() {
    TestSeq original = seq("banana");
    TestSeq replaced = original.replace("an", "AN");
    assertEquals(seq("bANANa"), replaced);
    assertEquals(seq("banana"), original, "source must not be mutated");
  }

  @DisplayName("replace(CharSequence, CharSequence): result agrees with String.replace")
  @Test
  void testReplaceSeq_AgreesWithString() {
    String[] subjects = { "", "a", "aa", "ab", "abc", "aaa", "abab", "abcabc", "banana", "hello world", "ababa" };
    String[] targets = { "a", "b", "aa", "ab", "abc", "abcabc", "aba", "xyz" };
    String[] repls = { "", "x", "XY", "abc", "abcd" };
    for (String s : subjects)
      for (String tg : targets)
        for (String r : repls)
          assertEquals(s.replace(tg, r), seq(s).replace(tg, r).toString(),
              "replace(\"" + tg + "\",\"" + r + "\") on \"" + s + "\"");
  }

  @DisplayName("repeat(int): basic repetition")
  @Test
  void testRepeat() {
    assertEquals(seq("ababab"), seq("ab").repeat(3));
    assertEquals(seq("abcabc"), seq("abc").repeat(2));
    assertEquals(seq("aaaaa"), seq("a").repeat(5));
    assertEquals(seq(""), seq("abcabc").repeat(0));
    assertEquals(seq("x"), seq("x").repeat(1));
    // count == 2 on a 1-char source exercises buffer sizing and offset advance
    assertEquals(seq("zz"), seq("z").repeat(2));
    // count == 2 on a 2-char source — the minimum that catches both Bug 1 and Bug 2
    assertEquals(seq("abab"), seq("ab").repeat(2));
  }

  @DisplayName("repeat(int): count 0 returns empty for any input")
  @Test
  void testRepeat_Zero() {
    assertTrue(seq("abc").repeat(0).isEmpty());
    TestSeq empty = seq("");
    assertEquals(empty, seq("abc").repeat(0));
    assertEquals(empty, empty.repeat(0));
    assertEquals(0, seq("hello").repeat(0).length());
  }

  @DisplayName("repeat(int): count 1 returns self (identity)")
  @Test
  void testRepeat_Identity() {
    TestSeq t = seq("abc");
    assertSame(t, t.repeat(1));
    TestSeq empty = seq("");
    assertSame(empty, empty.repeat(1));
  }

  @DisplayName("repeat(int): negative count throws")
  @Test
  void testRepeat_Negative() {
    assertThrows(IllegalArgumentException.class, () -> seq("abc").repeat(-1));
    assertThrows(IllegalArgumentException.class, () -> seq("").repeat(-1));
    assertThrows(IllegalArgumentException.class, () -> seq("a").repeat(Integer.MIN_VALUE));
  }

  @DisplayName("repeat(int): empty source returns empty for any count")
  @Test
  void testRepeat_EmptySource() {
    for (int n : new int[] {0, 1, 2, 100}) {
      assertTrue(seq("").repeat(n).isEmpty(), "repeat(" + n + ") of empty should be empty");
    }
  }

  @DisplayName("repeat(int): overflow guard")
  @Test
  void testRepeat_Overflow() {
    // 3 * Integer.MAX_VALUE overflows int; must not silently return garbage
    assertThrows(IllegalArgumentException.class, () -> seq("abc").repeat(Integer.MAX_VALUE));
  }

  @DisplayName("repeat(int): result has fresh buffer")
  @Test
  void testRepeat_FreshBuffer() {
    TestSeq original = seq("ab");
    TestSeq repeated = original.repeat(3);
    assertNotSame(original, repeated);
    assertEquals(seq("ab"), original, "source must not be mutated");
    assertEquals(seq("ababab"), repeated);
  }

  @DisplayName("repeat(int): agrees with String.repeat")
  @Test
  void testRepeat_AgreesWithString() {
    String[] samples = { "", "a", "ab", "abc", "aaaa", "hello world" };
    int[] counts = { 0, 1, 2, 3, 5 };
    for (String s : samples)
      for (int n : counts)
        assertEquals(s.repeat(n), seq(s).repeat(n).toString(),
            "\"" + s + "\".repeat(" + n + ")");
  }

  @DisplayName("isUpperCase / isLowerCase")
  @Test
  void testCasePredicates() {
    // boundary sweep — no letter slips through
    for (char c = 0; c < 128; c++) {
      boolean upper = c >= 'A' && c <= 'Z';
      boolean lower = c >= 'a' && c <= 'z';
      assertEquals(upper, CharFragment.isUpperCase(c), "isUpperCase(" + (int) c + ")");
      assertEquals(lower, CharFragment.isLowerCase(c), "isLowerCase(" + (int) c + ")");
      // mutually exclusive
      assertFalse(CharFragment.isUpperCase(c) && CharFragment.isLowerCase(c),
          "both upper and lower for " + (int) c);
    }
    // explicit boundary cases (the ones a hand-written range check gets wrong)
    assertFalse(CharFragment.isUpperCase('@'));   // 'A' - 1
    assertFalse(CharFragment.isUpperCase('['));   // 'Z' + 1
    assertFalse(CharFragment.isLowerCase('`'));   // 'a' - 1
    assertFalse(CharFragment.isLowerCase('{'));   // 'z' + 1
    // non-ASCII untouched
    assertFalse(CharFragment.isUpperCase('Ä'));
    assertFalse(CharFragment.isLowerCase('ä'));
  }

  @DisplayName("toLowerCase / toUpperCase: basic")
  @Test
  void testCaseTransform() {
    assertEquals("abc", seq("ABC").toLowerCase().toString());
    assertEquals("abc", seq("aBc").toLowerCase().toString());
    assertEquals("ABC", seq("abc").toUpperCase().toString());
    assertEquals("ABC", seq("aBc").toUpperCase().toString());
    // non-letters pass through unchanged
    assertEquals("123!@#", seq("123!@#").toLowerCase().toString());
    assertEquals("123!@#", seq("123!@#").toUpperCase().toString());
    // punctuation adjacent to letters is untouched
    assertEquals("a_b-c", seq("A_B-C").toLowerCase().toString());
    assertEquals("A_B-C", seq("a_b-c").toUpperCase().toString());
  }

  @DisplayName("toLowerCase / toUpperCase: identity when nothing changes")
  @Test
  void testCaseTransform_Identity() {
    TestSeq lower = seq("abc");
    assertSame(lower, lower.toLowerCase());
    TestSeq upper = seq("ABC");
    assertSame(upper, upper.toUpperCase());
    TestSeq digits = seq("123");
    assertSame(digits, digits.toLowerCase());
    assertSame(digits, digits.toUpperCase());
    TestSeq empty = seq("");
    assertSame(empty, empty.toLowerCase());
    assertSame(empty, empty.toUpperCase());
    TestSeq punct = seq("!@#");
    assertSame(punct, punct.toLowerCase());
    assertSame(punct, punct.toUpperCase());
  }

  @DisplayName("toLowerCase / toUpperCase: source unmodified")
  @Test
  void testCaseTransform_NoMutation() {
    TestSeq original = seq("AbC");
    TestSeq lower = original.toLowerCase();
    TestSeq upper = original.toUpperCase();
    assertEquals("AbC", original.toString());
    assertNotSame(original, lower);
    assertNotSame(original, upper);
  }

  @DisplayName("toLowerCase / toUpperCase: ASCII-only, Unicode untouched")
  @Test
  void testCaseTransform_AsciiOnly() {
    // accented letters stay as-is (unlike String.toLowerCase with default locale)
    assertEquals("Ä", seq("Ä").toLowerCase().toString());
    assertEquals("ä", seq("ä").toUpperCase().toString());
    // Greek, Cyrillic untouched
    assertEquals("ΑΒΓ", seq("ΑΒΓ").toLowerCase().toString());
    assertEquals("абв", seq("абв").toUpperCase().toString());
  }
}