package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the default API implementations of {@link CharSeq} and {@link CharArray} using a
 * minimal implementation that only provides the abstract methods.
 *
 * <p>The {@link TestChars} implementation deliberately provides nothing beyond {@link
 * CharSequence#length()}, {@link CharSequence#charAt(int)}, {@link CharArray#self()}
 * and {@link CharArray#wrap(char[], int, int)}. All other
 * behaviour is exercised through the default methods of the base interfaces.
 */
class CharArrayTest {

  /**
   * Minimal implementation of {@link CharArray} that only provides the abstract methods.
   * Everything else is inherited from the base interfaces.
   */
  private static final class TestChars implements CharArray<TestChars> {

    private final char[] buffer;
    private final int offset;
    private final int length;

    TestChars(String s) {
      this(s.toCharArray(), 0, s.length());
    }

    TestChars(char[] buffer, int offset, int length) {
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
    public TestChars self() {
      return this;
    }

    @Override
    public TestChars wrap(char[] buffer, int offset, int length) {
      return new TestChars(buffer, offset, length);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof TestChars other && contentEquals(other);
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

  private static TestChars chars(String s) {
    return new TestChars(s);
  }

  // ---------------------------------------------------------------------------
  // essential methods / self / empty / wrap
  // ---------------------------------------------------------------------------

  @Test
  void testSelfEmptyWrap() {
    TestChars s = chars("hello");
    assertSame(s, s.self());

    char[] buf = "hello".toCharArray();
    TestChars wrapped = s.wrap(buf, 1, 3);
    assertEquals(3, wrapped.length());
    assertEquals('e', wrapped.charAt(0));
    assertEquals('l', wrapped.charAt(1));
    assertEquals('l', wrapped.charAt(2));
    assertEquals("ell", wrapped.toString());
  }

  @Test
  void testCharAtAndLength() {
    TestChars s = chars("hello");
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
    TestChars s = chars("hello");
    assertEquals("ell", s.subSequence(1, 4).toString());
    assertEquals("", s.subSequence(2, 2).toString());
    assertEquals("hello", s.subSequence(0, 5).toString());
    assertSame(s, s.subSequence(0, 5), "whole range returns self");
  }

  @Test
  void testSubSequenceThrows() {
    TestChars s = chars("hello");
    assertThrows(IndexOutOfBoundsException.class, () -> s.subSequence(-1, 2));
    assertThrows(IndexOutOfBoundsException.class, () -> s.subSequence(0, 6));
    assertThrows(IndexOutOfBoundsException.class, () -> s.subSequence(3, 2));
  }

  @Test
  void testSubSequenceStart() {
    TestChars s = chars("hello");
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
    assertEquals("hello", chars("hello").slice(0, 5).toString());
    assertEquals("ello", chars("hello").slice(1, 5).toString());
    assertEquals("ell", chars("hello").slice(1, -1).toString());
    assertEquals("l", chars("hello").slice(2, -2).toString());
    assertEquals("", chars("hello").slice(2, -3).toString());
    assertEquals("", chars("hello").slice(3, -2).toString());
    assertEquals("", chars("hello").slice(3, -3).toString());
  }

  @Test
  void testSlicePattern() {
    List<TestChars> parts = new ArrayList<>();

    TestChars foo = chars("foo");
    foo.slice(chars("~."), parts::add);
    assertEquals(1, parts.size());
    assertSame(foo, parts.get(0));

    parts.clear();
    chars("foo.bar").slice(chars("~."), parts::add);
    assertEquals(2, parts.size());
    assertEquals("foo.", parts.get(0).toString());
    assertEquals("bar", parts.get(1).toString());

    parts.clear();
    chars("foo.bar.baz").slice(chars("~."), parts::add);
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
    assertEquals("hello", chars("hello").trim().toString());
    assertEquals("hello", chars(" hello").trim().toString());
    assertEquals("hello", chars("hello ").trim().toString());
    assertEquals("hello", chars(" hello ").trim().toString());
    assertEquals("hello", chars("  hello  ").trim().toString());
    assertEquals("", chars(" ").trim().toString());
    // trim does not remove \n or \r
    assertEquals("\nhello", chars("\nhello").trim().toString());
    assertEquals("hello\r", chars("hello\r").trim().toString());
  }

  @Test
  void testStrip() {
    assertEquals("hello", chars("hello").strip().toString());
    assertEquals("hello", chars(" hello ").strip().toString());
    assertEquals("hello", chars("\thello\t").strip().toString());
    assertEquals("hello", chars("\nhello\n").strip().toString());
    assertEquals("hello", chars("\rhello\r").strip().toString());
    assertEquals("hello", chars(" \t\n\r hello \r\n\t ").strip().toString());
    assertEquals("", chars(" \t\n\r ").strip().toString());
  }

  @Test
  void testShrink() {
    assertEquals("hello", chars("xxhelloxx").shrink(c -> c == 'x').toString());
    assertEquals("hello", chars("123hello456").shrink(Character::isDigit).toString());
    assertEquals("", chars("12345").shrink(Character::isDigit).toString());
    assertEquals("hello", chars("hello").shrink(c -> c == 'x').toString());
    assertEquals("", chars("").shrink(c -> true).toString());
    assertEquals("hello", chars(" hello ").shrink(CharArray::isStrip).toString());
    assertEquals("hello", chars(" hello ").shrink(CharArray::isTrim).toString());
  }

  @DisplayName("shrink(test, left, right): stripTrailing on all-whitespace is empty")
  @Test
  void testShrink_StripTrailingAllWhitespace() {
    assertEquals("", chars("   ").stripTrailing().toString());   // Bug: was " "
    assertTrue(chars("   ").stripTrailing().isEmpty());
    assertTrue(chars("\t\n\r ").stripTrailing().isEmpty());
    // mirror
    assertEquals("", chars("   ").stripLeading().toString());
    assertEquals("", chars("   ").strip().toString());
  }

  @DisplayName("stripTrailing / stripLeading agree with String on content")
  @Test
  void testStripDirections_AgreeWithString() {
    String[] samples = {
        "", " ", "   ", "\t\n\r ", "a", " a", "a ", " a ",
        "  x  ", "x  ", "  x", "abc", "  abc  def  "
    };
    for (String s : samples) {
      assertEquals(s.stripTrailing(), chars(s).stripTrailing().toString(),
          "stripTrailing(\"" + s + "\")");
      assertEquals(s.stripLeading(), chars(s).stripLeading().toString(),
          "stripLeading(\"" + s + "\")");
    }
  }

  @Test
  void testIsTrimAndIsStrip() {
    assertTrue(CharArray.isTrim(' '));
    assertTrue(CharArray.isTrim('\t'));
    assertFalse(CharArray.isTrim('\n'));
    assertFalse(CharArray.isTrim('\r'));
    assertFalse(CharArray.isTrim('a'));

    assertTrue(CharArray.isStrip(' '));
    assertTrue(CharArray.isStrip('\t'));
    assertTrue(CharArray.isStrip('\n'));
    assertTrue(CharArray.isStrip('\r'));
    assertFalse(CharArray.isStrip('a'));
  }

  // ---------------------------------------------------------------------------
  // concat / compareTo
  // ---------------------------------------------------------------------------

  @Test
  void testConcat() {
    TestChars hello = chars("hello");
    assertSame(hello, hello.concat(""));
    assertSame(hello, hello.concat(chars("")));
    assertEquals("hello", chars("").concat("hello").toString());
    assertEquals("hello world", hello.concat(" world").toString());
    assertEquals("hello world", hello.concat(chars(" world")).toString());
  }

  @Test
  void testCompareTo() {
    assertTrue(chars("a").compareTo(chars("b")) < 0);
    assertTrue(chars("b").compareTo(chars("a")) > 0);
    assertEquals(0, chars("abc").compareTo(chars("abc")));
    assertTrue(chars("abc").compareTo(chars("abcd")) < 0);
    assertTrue(chars("abcd").compareTo(chars("abc")) > 0);
    assertTrue(chars("").compareTo(chars("a")) < 0);
    assertTrue(chars("a").compareTo(chars("")) > 0);
  }

  // ---------------------------------------------------------------------------
  // indexOf / lastIndexOf / contains
  // ---------------------------------------------------------------------------

  @Test
  void testIndexOfChar() {
    TestChars t = chars("hello");
    assertEquals(0, t.indexOf('h'));
    assertEquals(2, t.indexOf('l'));
    assertEquals(-1, t.indexOf('z'));
    assertEquals(2, t.indexOf('l', 0));
    assertEquals(3, t.indexOf('l', 3));
    assertEquals(-1, t.indexOf('l', 4));
    assertEquals(-1, t.indexOf('l', 4, 5));
    assertEquals(4, chars("hello world").indexOf('o', 2, 6));
  }

  @Test
  void testLastIndexOfChar() {
    TestChars t = chars("hello");
    assertEquals(3, t.lastIndexOf('l'));
    assertEquals(1, t.lastIndexOf('e'));
    assertEquals(-1, t.lastIndexOf('z'));
    assertEquals(3, t.lastIndexOf('l', 4));
    assertEquals(2, t.lastIndexOf('l', 2));
    assertEquals(-1, t.lastIndexOf('l', 1));
    assertEquals(3, chars("hello world").lastIndexOf('l', 4, 0));
  }

  @Test
  void testContains() {
    TestChars t = chars("hello");
    assertTrue(t.contains('h'));
    assertFalse(t.contains('z'));
    assertTrue(t.contains("ell"));
    assertTrue(t.contains(""));
    assertFalse(t.contains("xyz"));
    assertTrue(t.contains(Character::isLetter));
    assertFalse(t.contains(Character::isDigit));
    assertFalse(chars("").contains(c -> true));
  }

  @Test
  void testIndexOfCharSequence() {
    TestChars t = chars("hello world hello");
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
    TestChars t = chars("hello world hello");
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
    TestChars t = chars("hello");
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
    TestChars t = chars("hello");
    assertTrue(t.contentEquals("hello"));
    assertTrue(t.contentEquals(chars("hello")));
    assertFalse(t.contentEquals("world"));
    assertFalse(t.contentEquals("hell"));
    assertFalse(t.contentEquals("helloo"));
    assertTrue(chars("").contentEquals(""));
  }

  @Test
  void testRegionMatches() {
    TestChars t = chars("hello world");
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
    assertTrue(chars("hello").matches("hello"));
    assertFalse(chars("hello").matches("world"));
    assertFalse(chars("hello").matches("hell"));
    assertFalse(chars("hello").matches("helloo"));
    assertTrue(chars("").matches(""));
    assertFalse(chars("").matches("a"));
    assertFalse(chars("a").matches(""));
  }

  @Test
  void testMatchesSubrange() {
    assertTrue(chars("hello").matches("hello world", 0, 5));
    assertTrue(chars("hello").matches("say hello", 4, 5));
    assertFalse(chars("hello").matches("hello world", 0, 4));
    assertFalse(chars("hello").matches("say hello", 3, 5));
  }

  // ---------------------------------------------------------------------------
  // toCharArray / copyTo
  // ---------------------------------------------------------------------------

  @Test
  void testToCharArray() {
    assertArrayEquals(new char[] {'h', 'e', 'l', 'l', 'o'}, chars("hello").toCharArray());
    assertArrayEquals(new char[0], chars("").toCharArray());
  }

  @Test
  void testCopyTo() {
    TestChars s = chars("hello");
    char[] buf = new char[10];
    s.copyTo(buf, 2);
    assertArrayEquals(new char[] {0, 0, 'h', 'e', 'l', 'l', 'o', 0, 0, 0}, buf);
  }

  @Test
  void testCopyToSubrange() {
    TestChars s = chars("hello");
    char[] buf = new char[10];
    s.copyTo(buf, 2, 1, 3);
    assertArrayEquals(new char[] {0, 0, 'e', 'l', 'l', 0, 0, 0, 0, 0}, buf);
  }

  // ---------------------------------------------------------------------------
  // classification / parsing
  // ---------------------------------------------------------------------------

  @Test
  void testIsTextualInteger() {
    assertTrue(chars("123").isIntegerLiteral());
    assertTrue(chars("-123").isIntegerLiteral());
    assertTrue(chars("+123").isIntegerLiteral());
    assertTrue(chars("0").isIntegerLiteral());
    assertTrue(chars("007").isIntegerLiteral());
    assertFalse(chars("").isIntegerLiteral());
    assertFalse(chars("12.3").isIntegerLiteral());
    assertFalse(chars("12a").isIntegerLiteral());
    assertFalse(chars("--1").isIntegerLiteral());
  }

  @Test
  void testIsNumericInteger() {
    assertTrue(chars("123").isIntegerValue());
    assertTrue(chars("-123").isIntegerValue());
    assertTrue(chars("0.0").isIntegerValue());
    assertTrue(chars("+1.0").isIntegerValue());
    assertTrue(chars("12.00").isIntegerValue());
    assertFalse(chars("").isIntegerValue());
    assertFalse(chars("12.3").isIntegerValue());
    assertFalse(chars("1.0000001").isIntegerValue());
  }

  @Test
  void testIsTextualDecimal() {
    assertTrue(chars("0").isNumberLiteral());
    assertTrue(chars(".0").isNumberLiteral());
    assertTrue(chars("123.0").isNumberLiteral());
    assertTrue(chars("123e-2").isNumberLiteral());
    assertTrue(chars("-123.0001E-23").isNumberLiteral());
    assertFalse(chars("").isNumberLiteral());
    assertFalse(chars(".").isNumberLiteral());
    assertFalse(chars("--0").isNumberLiteral());
    assertFalse(chars("5.x").isNumberLiteral());
  }

  @Test
  void testIsSpecialDecimal() {
    assertTrue(chars("NaN").isSpecialDoubleValue());
    assertTrue(chars("Infinity").isSpecialDoubleValue());
    assertTrue(chars("-Infinity").isSpecialDoubleValue());
    assertFalse(chars("nan").isSpecialDoubleValue());
    assertFalse(chars("").isSpecialDoubleValue());
  }

  @Test
  void testParseBoolean() {
    assertTrue(chars("true").parseBoolean());
    assertTrue(chars("TRUE").parseBoolean());
    assertFalse(chars("false").parseBoolean());
    assertFalse(chars("FALSE").parseBoolean());
    assertThrows(IllegalArgumentException.class, () -> chars("t").parseBoolean());
    assertThrows(IllegalArgumentException.class, () -> chars("fear").parseBoolean());
  }

  @Test
  void testParseInt() {
    assertEquals(123, chars("123").parseInt());
    assertEquals(-123, chars("-123").parseInt());
    assertEquals(123, chars("+123").parseInt());
    assertEquals(0, chars("0").parseInt());
    assertEquals(7, chars("007").parseInt());
    assertThrows(NumberFormatException.class, () -> chars("").parseInt());
    assertThrows(NumberFormatException.class, () -> chars("abc").parseInt());
  }

  @Test
  void testParseLong() {
    assertEquals(123L, chars("123").parseLong());
    assertEquals(-123L, chars("-123").parseLong());
    assertEquals(0L, chars("0").parseLong());
    assertThrows(NumberFormatException.class, () -> chars("").parseLong());
  }

  @Test
  void testParseDouble() {
    assertEquals(123.0, chars("123").parseDouble(), 0.0);
    assertEquals(123.5, chars("123.5").parseDouble(), 0.0);
    assertEquals(-123.5, chars("-123.5").parseDouble(), 0.0);
    assertEquals(123.0, chars("1.23e2").parseDouble(), 0.001);
  }

  @Test
  void testParseNumber() {
    assertEquals(123, chars("123").parseNumber());
    assertEquals(123.5d, chars("123.5").parseNumber().doubleValue());
    assertEquals(-123, chars("-123").parseNumber());
  }

  // ---------------------------------------------------------------------------
  // statics
  // ---------------------------------------------------------------------------

  @Test
  void testHashCodeStatic() {
    assertEquals(0, CharSeq.hashCode(chars("")));
    assertEquals(CharSeq.hashCode(chars("hello")), CharSeq.hashCode(chars("hello")));
    // sample based for long sequences - deterministic
    assertEquals(
        CharSeq.hashCode(chars("a".repeat(100))), CharSeq.hashCode(chars("a".repeat(100))));
    // short sequences use full content
    assertNotEquals(CharSeq.hashCode(chars("hello")), CharSeq.hashCode(chars("world")));
  }

  @Test
  void testCheckSubSequence() {
    assertThrows(
        IndexOutOfBoundsException.class, () -> CharArray.checkSubSequence(-1, 0, 5));
    assertThrows(
        IndexOutOfBoundsException.class, () -> CharArray.checkSubSequence(0, -1, 5));
    assertThrows(
        IndexOutOfBoundsException.class, () -> CharArray.checkSubSequence(0, 6, 5));
    assertThrows(
        IndexOutOfBoundsException.class, () -> CharArray.checkSubSequence(3, 2, 5));

    assertDoesNotThrow(() -> CharArray.checkSubSequence(0, 5, 5));
    assertDoesNotThrow(() -> CharArray.checkSubSequence(2, 2, 5));
    assertDoesNotThrow(() -> CharArray.checkSubSequence(0, 0, 0));
  }

  // ---------------------------------------------------------------------------
  // skip
  // ---------------------------------------------------------------------------

  @Test
  void testSkip() {
    // skip past characters matching the predicate; return first non-matching index
    assertEquals(3, chars("abc").skip(c -> true));
    assertEquals(0, chars("abc").skip(c -> false));
    assertEquals(3, chars("   abc").skip(c -> c == ' '));
    assertEquals(0, chars("abc   ").skip(c -> c == ' '));
    assertEquals(3, chars("   ").skip(c -> true));
    assertEquals(3, chars("   ").skip(c -> c == ' '));
    assertEquals(3, chars("123abc").skip(Character::isDigit));
    assertEquals(1, chars("a.b.c").skip(c -> c != '.'));

    // empty sequence -> -1 (startIndex 0 >= length 0)
    assertEquals(-1, chars("").skip(c -> true));
  }

  @Test
  void testSkipWithStartIndex() {
    assertEquals(2, chars("  abc").skip(c -> c == ' ', 1));
    assertEquals(3, chars("123abc").skip(Character::isDigit, 1));
    assertEquals(3, chars("123abc").skip(Character::isDigit, 2));
    assertEquals(6, chars("123abc").skip(c -> true, 2));

    // start index == length or beyond -> -1
    assertEquals(-1, chars("abc").skip(c -> true, 3));
    assertEquals(-1, chars("abc").skip(c -> true, 4));
    // negative start index -> -1
    assertEquals(-1, chars("abc").skip(c -> true, -1));
    // empty sequence with startIndex 0 -> -1
    assertEquals(-1, chars("").skip(c -> true, 0));
  }

  @Test
  void testSkipWithRange() {
    // finds first non-matching char within the window
    assertEquals(2, chars("abcdef").skip(c -> c != 'c', 0, 3));
    assertEquals(0, chars("abcdef").skip(c -> c == 'c', 0, 3));
    assertEquals(2, chars("abcdef").skip(c -> c != 'c', 2, 5));

    // all characters in [startIndex, endIndex) match -> returns endIndex
    assertEquals(3, chars("abcdef").skip(Character::isLetter, 0, 3));
    assertEquals(5, chars("abcdef").skip(Character::isLetter, 2, 5));

    // endIndex beyond length is clamped to length
    assertEquals(3, chars("abc").skip(c -> true, 0, 100));
    assertEquals(3, chars("abc").skip(c -> true, 1, 100));

    // startIndex at/after length -> -1
    assertEquals(-1, chars("abc").skip(c -> true, 3, 3));
    assertEquals(-1, chars("abc").skip(c -> true, 3, 100));
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
    TestChars t = chars("ababab");
    assertEquals(0, t.count("xx", 0));
    assertEquals(3, t.count("ab", 0));
    assertEquals(2, t.count("ab", 1));   // skips the match at 0
    assertEquals(2, t.count("ab", 2));   // skips the match at 0 too
    assertEquals(1, t.count("ab", 3));   // only the match at 2 remains
    assertEquals(0, t.count("ab", 5));   // past all matches
    assertEquals(0, t.count("ab", 6));   // == length
    assertEquals(0, t.count("ab", 100)); // > length
    // non-overlapping: the second "ab" in "aaaa" is not counted after consuming the first
    assertEquals(2, chars("aaaa").count("aa", 0));
    // adjacency: back-to-back non-overlapping
    assertEquals(2, chars("abab").count("ab", 0));
    // fragment longer than subject
    assertEquals(0, chars("ab").count("abc", 0));
    // match touching end is counted once, not twice
    assertEquals(1, chars("abc").count("c", 0));
    // empty subject
    assertEquals(0, chars("").count("a", 0));
  }

  @DisplayName("count(CharSequence, int) with empty fragment")
  @Test
  void testCount_EmptyCharArray() {
    // the empty fragment matches at every position; defining it to have 0 occurrences
    // is the only finite, useful answer (and prevents the indexOf("",k)==k fixed point)
    assertEquals(0, chars("abc").count("", 0));
    assertEquals(0, chars("abc").count("", 1));
    assertEquals(0, chars("abc").count("", 3));   // == length, would otherwise loop forever
    assertEquals(0, chars("").count("", 0));
    // a StringBuilder that is empty is the same case
    assertEquals(0, chars("abc").count(new StringBuilder(), 0));
  }

  @DisplayName("count(CharSequence) delegates to startIndex 0")
  @Test
  void testCount_NoStartIndex() {
    TestChars t = chars("ababab");
    assertEquals(3, t.count("ab"));
    assertEquals(t.count("ab", 0), t.count("ab"));
  }

  @DisplayName("count() agrees with a manual loop using indexOf")
  @Test
  void testCount_AgreesWithIndexOf() {
    String[] subjects = { "", "a", "aa", "ab", "aab", "abab", "ababab", "aaaa", "banana" };
    String[] fragments = { "a", "b", "aa", "ab", "abab", "na", "banana" };
    for (String s : subjects) {
      TestChars seq = chars(s);
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
    assertTrue(chars("abc").every(c -> true));
    assertTrue(chars("abc").every(Character::isLetter));
    assertTrue(chars("123").every(Character::isDigit));
    // non-empty, one counterexample
    assertFalse(chars("abc").every(c -> false));
    assertFalse(chars("123a").every(Character::isDigit));
    // counterexample only at the first position
    assertTrue(chars("zbc").every(Character::isLetter));
    assertFalse(chars("zbc").every(c -> c == 'z'));
    // counterexample only at the last position
    assertFalse(chars("abz").every(c -> c != 'z'));
    // single character
    assertTrue(chars("a").every(c -> c == 'a'));
    assertFalse(chars("a").every(c -> c == 'b'));
  }

  @DisplayName("every(CharPredicate) on empty is vacuously true")
  @Test
  void testEvery_Empty() {
    assertTrue(chars("").every(c -> true));
    assertTrue(chars("").every(c -> false));      // no character violates the test
    assertTrue(chars("").every(c -> c == 'z'));
    assertTrue(chars("").every(Character::isDigit));
  }

  @DisplayName("every(CharPredicate) stops at the first counterexample")
  @Test
  void testEvery_ShortCircuits() {
    int[] calls = {0};
    assertFalse(chars("abcdef").every(c -> { calls[0]++; return false; }));
    assertEquals(1, calls[0], "must stop after the first failing character");

    calls[0] = 0;
    assertTrue(chars("abc").every(c -> { calls[0]++; return true; }));
    assertEquals(3, calls[0], "must inspect every character when all pass");

    // first char passes, second fails -> two calls, not three or six
    calls[0] = 0;
    assertFalse(chars("abcdef").every(c -> { calls[0]++; return c != 'b'; }));
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
        CharArray::isTrim,
        CharArray::isStrip,
    };
    for (String s : samples) {
      TestChars seq = chars(s);
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
    assertFalse(chars("a").isBlank());
    assertFalse(chars(" a ").isBlank());
    assertFalse(chars("\ta\t").isBlank());
    assertFalse(chars(" \n x \r ").isBlank());
    // one non-blank char anywhere is enough
    assertFalse(chars(" x").isBlank());
    assertFalse(chars("x ").isBlank());
    assertFalse(chars("  x  ").isBlank());
  }

  @DisplayName("isBlank() on blank sequences")
  @Test
  void testIsBlank_True() {
    assertTrue(chars(" ").isBlank());
    assertTrue(chars("   ").isBlank());
    assertTrue(chars("\t").isBlank());
    assertTrue(chars("\n").isBlank());
    assertTrue(chars("\r").isBlank());
    assertTrue(chars(" \t\n\r").isBlank());
    assertTrue(chars("\r\n\t ").isBlank());
    assertTrue(chars("\n\n\n").isBlank());
  }

  @DisplayName("isBlank() on empty is vacuously true")
  @Test
  void testIsBlank_Empty() {
    assertTrue(chars("").isBlank());
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
      assertEquals(chars(s).every(CharSeq::isBlank), chars(s).isBlank(),
          "isBlank mismatch for \"" + s + "\"");
    }
  }

  @DisplayName("isBlank() diverges from String.isBlank() on form feed / vertical tab")
  @Test
  void testIsBlank_DivergesFromStringOnAsciiControls() {
    // documented divergence: String.isBlank() says true for these, we say false
    assertFalse(chars("\f").isBlank());
    assertFalse(chars("\u000B").isBlank());
    assertTrue("\f".isBlank(), "String.isBlank still true for form feed");

    // the JDK-canonical blank chars we agree on
    for (char c : new char[] {' ', '\t', '\n', '\r'}) {
      assertTrue(chars(String.valueOf(c)).isBlank(), "single char " + (int) c);
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
      assertEquals(CharSeq.isBlank(c), CharArray.isStrip(c),
          "isStrip/isBlank diverged for char " + i + " ('" + c + "')");
    }
    // spot-check the divergence-vs-String cases from the audit still agree
    assertEquals(CharSeq.isBlank('\f'), CharArray.isStrip('\f'));        // both false
    assertEquals(CharSeq.isBlank('\u000B'), CharArray.isStrip('\u000B')); // both false
    assertEquals(CharSeq.isBlank('\u00A0'), CharArray.isStrip('\u00A0')); // both false
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
      assertEquals(chars(s).strip().isEmpty(), chars(s).isBlank(),
          "isBlank vs strip().isEmpty() mismatch for \"" + s + "\"");
    }
  }

  @DisplayName("replace(char, char): general case")
  @Test
  void testReplace() {
    assertEquals(chars("banana"), chars("banana").replace('a', 'a'));
    assertEquals(chars("bonono"), chars("banana").replace('a', 'o'));
    assertEquals(chars("bonono"), chars("banana").replace('a', 'o'));
    assertEquals(chars("bAnAnA"), chars("banana").replace('a', 'A'));
    assertEquals(chars("b.n.n."), chars("banana").replace('a', '.'));
    // replace every occurrence, including adjacent ones
    assertEquals(chars("xxxx"), chars("aaaa").replace('a', 'x'));
    // single-char sequence
    assertEquals(chars("x"), chars("a").replace('a', 'x'));
    // no match -> unchanged content
    assertEquals(chars("banana"), chars("banana").replace('z', 'x'));
  }

  @DisplayName("replace(char, char): no-match returns self (identity)")
  @Test
  void testReplace_NoMatchReturnsSelf() {
    TestChars t = chars("banana");
    assertSame(t, t.replace('z', 'x'));
    assertSame(t, t.replace('Z', 'x'));
    TestChars empty = chars("");
    assertSame(empty, empty.replace('a', 'x'));
  }

  @DisplayName("replace(char, char): oldChar == newChar returns self (identity)")
  @Test
  void testReplace_SameCharReturnsSelf() {
    TestChars t = chars("banana");
    // present
    assertSame(t, t.replace('a', 'a'));
    // absent
    assertSame(t, t.replace('z', 'z'));
    // empty
    TestChars empty = chars("");
    assertSame(empty, empty.replace('a', 'a'));
  }

  @DisplayName("replace(char, char): result has fresh buffer")
  @Test
  void testReplace_ResultHasFreshBuffer() {
    TestChars original = chars("banana");
    TestChars replaced = original.replace('a', 'o');
    assertNotSame(original, replaced);
    // the source is unmodified
    assertEquals(chars("banana"), original);
    assertEquals("bonono", replaced.toString());
  }

  @DisplayName("replace(CharSequence, CharSequence): basic substitution")
  @Test
  void testReplaceSeq() {
    assertEquals(chars("bonono"), chars("banana").replace("a", "o"));
    assertEquals(chars("heLLo"), chars("hello").replace("l", "L"));           // adjacent
    assertEquals(chars("bbbb"), chars("aaaa").replace("a", "b"));             // all
    assertEquals(chars("xx"), chars("abcabc").replace("abc", "x"));           // from start, repeated
    assertEquals(chars("xbc"), chars("abc").replace("a", "x"));               // match at index 0
    assertEquals(chars("abx"), chars("abc").replace("c", "x"));               // match at last index
    assertEquals(chars("x"), chars("abc").replace("abc", "x"));               // whole sequence
    assertEquals(chars("axyzc"), chars("abc").replace("b", "xyz"));           // grow
    assertEquals(chars(""), chars("abcabc").replace("abc", ""));              // shrink to empty
    assertEquals(chars("xx"), chars("xabcabcx").replace("abc", ""));          // shrink to non-empty
    assertEquals(chars("heLLo world"), chars("hello world").replace("ll", "LL"));
    assertEquals(chars("ba"), chars("aaa").replace("aa", "b"));               // non-overlap from left
    assertEquals(chars("Xba"), chars("ababa").replace("aba", "X"));           // non-overlap, tail kept
  }

  @DisplayName("replace(CharSequence, CharSequence): no match returns self")
  @Test
  void testReplaceSeq_NoMatchReturnsSelf() {
    TestChars t = chars("hello");
    assertSame(t, t.replace("xyz", "Q"));
    assertSame(t, t.replace("hx", "Q"));
    assertSame(t, t.replace("hello world", "Q"));
    TestChars empty = chars("");
    assertSame(empty, empty.replace("a", "b"));
  }

  @DisplayName("replace(CharSequence, CharSequence): empty target returns self")
  @Test
  void testReplaceSeq_EmptyTargetReturnsSelf() {
    TestChars t = chars("abc");
    assertSame(t, t.replace("", "X"));       // no insertion at every position (diverges from String)
    assertSame(t, t.replace("", ""));
    TestChars empty = chars("");
    assertSame(empty, empty.replace("", "X"));
  }

  @DisplayName("replace(CharSequence, CharSequence): empty replacement removes")
  @Test
  void testReplaceSeq_EmptyReplacement() {
    assertEquals(chars("bc"), chars("abc").replace("a", ""));
    assertEquals(chars("ac"), chars("abc").replace("b", ""));
    assertEquals(chars("ab"), chars("abc").replace("c", ""));
    assertEquals(chars(""), chars("aaaa").replace("aa", ""));      // non-overlap
    assertEquals(chars("a"), chars("aaa").replace("aa", ""));     // first two removed, one remains — wait, "aaa".replace("aa","") in String = "a"
  }

  @DisplayName("replace(CharSequence, CharSequence): result has fresh buffer")
  @Test
  void testReplaceSeq_ResultHasFreshBuffer() {
    TestChars original = chars("banana");
    TestChars replaced = original.replace("an", "AN");
    assertEquals(chars("bANANa"), replaced);
    assertEquals(chars("banana"), original, "source must not be mutated");
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
          assertEquals(s.replace(tg, r), chars(s).replace(tg, r).toString(),
              "replace(\"" + tg + "\",\"" + r + "\") on \"" + s + "\"");
  }

  @DisplayName("repeat(int): basic repetition")
  @Test
  void testRepeat() {
    assertEquals(chars("ababab"), chars("ab").repeat(3));
    assertEquals(chars("abcabc"), chars("abc").repeat(2));
    assertEquals(chars("aaaaa"), chars("a").repeat(5));
    assertEquals(chars(""), chars("abcabc").repeat(0));
    assertEquals(chars("x"), chars("x").repeat(1));
    // count == 2 on a 1-char source exercises buffer sizing and offset advance
    assertEquals(chars("zz"), chars("z").repeat(2));
    // count == 2 on a 2-char source — the minimum that catches both Bug 1 and Bug 2
    assertEquals(chars("abab"), chars("ab").repeat(2));
  }

  @DisplayName("repeat(int): count 0 returns empty for any input")
  @Test
  void testRepeat_Zero() {
    assertTrue(chars("abc").repeat(0).isEmpty());
    TestChars empty = chars("");
    assertEquals(empty, chars("abc").repeat(0));
    assertEquals(empty, empty.repeat(0));
    assertEquals(0, chars("hello").repeat(0).length());
  }

  @DisplayName("repeat(int): count 1 returns self (identity)")
  @Test
  void testRepeat_Identity() {
    TestChars t = chars("abc");
    assertSame(t, t.repeat(1));
    TestChars empty = chars("");
    assertSame(empty, empty.repeat(1));
  }

  @DisplayName("repeat(int): negative count throws")
  @Test
  void testRepeat_Negative() {
    assertThrows(IllegalArgumentException.class, () -> chars("abc").repeat(-1));
    assertThrows(IllegalArgumentException.class, () -> chars("").repeat(-1));
    assertThrows(IllegalArgumentException.class, () -> chars("a").repeat(Integer.MIN_VALUE));
  }

  @DisplayName("repeat(int): empty source returns empty for any count")
  @Test
  void testRepeat_EmptySource() {
    for (int n : new int[] {0, 1, 2, 100}) {
      assertTrue(chars("").repeat(n).isEmpty(), "repeat(" + n + ") of empty should be empty");
    }
  }

  @DisplayName("repeat(int): overflow guard")
  @Test
  void testRepeat_Overflow() {
    // 3 * Integer.MAX_VALUE overflows int; must not silently return garbage
    assertThrows(IllegalArgumentException.class, () -> chars("abc").repeat(Integer.MAX_VALUE));
  }

  @DisplayName("repeat(int): result has fresh buffer")
  @Test
  void testRepeat_FreshBuffer() {
    TestChars original = chars("ab");
    TestChars repeated = original.repeat(3);
    assertNotSame(original, repeated);
    assertEquals(chars("ab"), original, "source must not be mutated");
    assertEquals(chars("ababab"), repeated);
  }

  @DisplayName("repeat(int): agrees with String.repeat")
  @Test
  void testRepeat_AgreesWithString() {
    String[] samples = { "", "a", "ab", "abc", "aaaa", "hello world" };
    int[] counts = { 0, 1, 2, 3, 5 };
    for (String s : samples)
      for (int n : counts)
        assertEquals(s.repeat(n), chars(s).repeat(n).toString(),
            "\"" + s + "\".repeat(" + n + ")");
  }

  @DisplayName("isUpperCase / isLowerCase")
  @Test
  void testCasePredicates() {
    // boundary sweep — no letter slips through
    for (char c = 0; c < 128; c++) {
      boolean upper = c >= 'A' && c <= 'Z';
      boolean lower = c >= 'a' && c <= 'z';
      assertEquals(upper, CharArray.isUpperCase(c), "isUpperCase(" + (int) c + ")");
      assertEquals(lower, CharArray.isLowerCase(c), "isLowerCase(" + (int) c + ")");
      // mutually exclusive
      assertFalse(CharArray.isUpperCase(c) && CharArray.isLowerCase(c),
          "both upper and lower for " + (int) c);
    }
    // explicit boundary cases (the ones a hand-written range check gets wrong)
    assertFalse(CharArray.isUpperCase('@'));   // 'A' - 1
    assertFalse(CharArray.isUpperCase('['));   // 'Z' + 1
    assertFalse(CharArray.isLowerCase('`'));   // 'a' - 1
    assertFalse(CharArray.isLowerCase('{'));   // 'z' + 1
    // non-ASCII untouched
    assertFalse(CharArray.isUpperCase('Ä'));
    assertFalse(CharArray.isLowerCase('ä'));
  }

  @DisplayName("toLowerCase / toUpperCase: basic")
  @Test
  void testCaseTransform() {
    assertEquals("abc", chars("ABC").toLowerCase().toString());
    assertEquals("abc", chars("aBc").toLowerCase().toString());
    assertEquals("ABC", chars("abc").toUpperCase().toString());
    assertEquals("ABC", chars("aBc").toUpperCase().toString());
    // non-letters pass through unchanged
    assertEquals("123!@#", chars("123!@#").toLowerCase().toString());
    assertEquals("123!@#", chars("123!@#").toUpperCase().toString());
    // punctuation adjacent to letters is untouched
    assertEquals("a_b-c", chars("A_B-C").toLowerCase().toString());
    assertEquals("A_B-C", chars("a_b-c").toUpperCase().toString());
  }

  @DisplayName("toLowerCase / toUpperCase: identity when nothing changes")
  @Test
  void testCaseTransform_Identity() {
    TestChars lower = chars("abc");
    assertSame(lower, lower.toLowerCase());
    TestChars upper = chars("ABC");
    assertSame(upper, upper.toUpperCase());
    TestChars digits = chars("123");
    assertSame(digits, digits.toLowerCase());
    assertSame(digits, digits.toUpperCase());
    TestChars empty = chars("");
    assertSame(empty, empty.toLowerCase());
    assertSame(empty, empty.toUpperCase());
    TestChars punct = chars("!@#");
    assertSame(punct, punct.toLowerCase());
    assertSame(punct, punct.toUpperCase());
  }

  @DisplayName("toLowerCase / toUpperCase: source unmodified")
  @Test
  void testCaseTransform_NoMutation() {
    TestChars original = chars("AbC");
    TestChars lower = original.toLowerCase();
    TestChars upper = original.toUpperCase();
    assertEquals("AbC", original.toString());
    assertNotSame(original, lower);
    assertNotSame(original, upper);
  }

  @DisplayName("toLowerCase / toUpperCase: ASCII-only, Unicode untouched")
  @Test
  void testCaseTransform_AsciiOnly() {
    // accented letters stay as-is (unlike String.toLowerCase with default locale)
    assertEquals("Ä", chars("Ä").toLowerCase().toString());
    assertEquals("ä", chars("ä").toUpperCase().toString());
    // Greek, Cyrillic untouched
    assertEquals("ΑΒΓ", chars("ΑΒΓ").toLowerCase().toString());
    assertEquals("абв", chars("абв").toUpperCase().toString());
  }

  @DisplayName("matches(CharSequence): this is the pattern, argument is the subject")
  @Test
  void testMatches_RolesArePatternAndSubject() {
    // pattern on the left, subject on the right — the reverse of String.matches(regex)
    Text pattern = Text.of("foo");
    assertTrue(pattern.matches("foo"));
    assertFalse(pattern.matches("foobar"));    // must match all of the input
    assertFalse(pattern.matches("xfoo"));      // anchored at the start
    assertFalse(pattern.matches("fo"));        // too short

    // if the roles were reversed, this would pass; it must not
    assertFalse(Text.of("foobar").matches("foo"));
  }

  @DisplayName("matches: receiver is the pattern, argument is the subject")
  @Test
  void testMatches_Roles() {
    // Use any InputExpression pattern whose metacharacters are exercised by testSlicePattern,
    // e.g. "~." matches up to and including a '.' — verify the exact semantics against
    // InputExpression's docs.
    assertTrue(Text.of("~.").matches("foo."),
        "receiver must be the pattern");
    assertFalse(Text.of("foo.").matches("~."),
        "argument must not be the pattern");
  }
}