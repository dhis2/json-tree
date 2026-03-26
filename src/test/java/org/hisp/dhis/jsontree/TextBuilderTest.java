package org.hisp.dhis.jsontree;

import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link TextBuilder}.
 *
 * @author Jan Bernitt
 */
class TextBuilderTest {

  @Test
  void testAppendChar() {
    assertEquals(
        str ->
            str.append('a')
                .append('b')
                .append('c')
                .append('\n')
                .append('\t')
                .append('\\')
                .append('©')
                .append('\uD83D'),
        txt ->
            txt.append('a')
                .append('b')
                .append('c')
                .append('\n')
                .append('\t')
                .append('\\')
                .append('©')
                .append('\uD83D'));
  }

  @Test
  void testAppendCharSequence() {
    assertEquals(
        str ->
            str.append("")
                .append("Hello")
                .append(" ")
                .append("World")
                .append((CharSequence) null)
                .append("A©\uD83D\uDE00"),
        txt ->
            txt.append("")
                .append("Hello")
                .append(" ")
                .append("World")
                .append((CharSequence) null)
                .append("A©\uD83D\uDE00"));
  }

  @Test
  void testAppendCharSequenceRange() {
    assertEquals(
        str -> str.append("Hello, world!", 0, 13), txt -> txt.append("Hello, world!", 0, 13));
    assertEquals(
        str -> str.append("Hello, world!", 7, 13), txt -> txt.append("Hello, world!", 7, 13));
    assertEquals(
        str -> str.append("Hello, world!", 1, 5), txt -> txt.append("Hello, world!", 1, 5));
    assertEquals(
        str -> str.append("Hello, world!", 3, 3), txt -> txt.append("Hello, world!", 3, 3));
  }

  @Test
  void testAppendCharArray() {
    assertEquals(
        str -> str.append("Hello, world!".toCharArray()),
        txt -> txt.append("Hello, world!".toCharArray()));
  }

  @Test
  void testAppendCharArrayRange() {
    assertEquals(
        str -> str.append("Hello, world!".toCharArray(), 0, 13),
        txt -> txt.append("Hello, world!".toCharArray(), 0, 13));
    assertEquals(
        str -> str.append("Hello, world!".toCharArray(), 7, 6),
        txt -> txt.append("Hello, world!".toCharArray(), 7, 6));
    assertEquals(
        str -> str.append("Hello, world!".toCharArray(), 1, 5),
        txt -> txt.append("Hello, world!".toCharArray(), 1, 5));
  }

  @Test
  void testAppendInt() {
    assertEquals(
        str ->
            str.append(0)
                .append(42)
                .append(-123)
                .append(Integer.MAX_VALUE)
                .append(Integer.MIN_VALUE)
                .append(10)
                .append(-5)
                .append(0),
        txt ->
            txt.append(0)
                .append(42)
                .append(-123)
                .append(Integer.MAX_VALUE)
                .append(Integer.MIN_VALUE)
                .append(10)
                .append(-5)
                .append(0));
  }

  @Test
  void testAppendLong() {
    assertEquals(
        str ->
            str.append(0L)
                .append(9876543210L)
                .append(-1234567890123L)
                .append(Long.MAX_VALUE)
                .append(Long.MIN_VALUE)
                .append(100L)
                .append(-200L)
                .append(300L),
        txt ->
            txt.append(0L)
                .append(9876543210L)
                .append(-1234567890123L)
                .append(Long.MAX_VALUE)
                .append(Long.MIN_VALUE)
                .append(100L)
                .append(-200L)
                .append(300L));
  }

  @Test
  void testAppendDouble() {
    assertEquals(
        str ->
            str.append(3.14159)
                .append(-2.5)
                .append(Double.NaN)
                .append(Double.POSITIVE_INFINITY)
                .append(Double.NEGATIVE_INFINITY)
                .append(1.1)
                .append(-2.22)
                .append(3.333)
                .append(4.4444),
        txt ->
            txt.append(3.14159)
                .append(-2.5)
                .append(Double.NaN)
                .append(Double.POSITIVE_INFINITY)
                .append(Double.NEGATIVE_INFINITY)
                .append(1.1)
                .append(-2.22)
                .append(3.333)
                .append(4.4444));
  }

  @Test
  void testAppendDouble_EdgeCases() {
    assertEquals(
        str ->
            str.append(-0d)
                .append(1d)
                .append(0.0d)
                .append(1.23456789e8)
                .append(0.2)
                .append(0.3)
                .append(0.1 + 0.2),
        txt ->
            txt.append(-0d)
                .append(1d)
                .append(0.0d)
                .append(1.23456789e8)
                .append(0.2)
                .append(0.3)
                .append(0.1 + 0.2));
  }

  @Test
  void testAppendCodePoint() {
    // 0x10FFFF is max range
    assertEquals(
        str ->
            str.appendCodePoint('A')
                .appendCodePoint(0x20AC)
                .appendCodePoint(0x1F600)
                .appendCodePoint(0x10FFFF),
        txt ->
            txt.appendCodePoint('A')
                .appendCodePoint(0x20AC)
                .appendCodePoint(0x1F600)
                .appendCodePoint(0x10FFFF));
  }

  @Test
  void testAppendCharSequence_Errors() {
    assertThrows(
        IndexOutOfBoundsException.class,
        str -> str.append("Hello", -1, 2),
        txt -> txt.append("Hello", -1, 2));
    assertThrows(
        IndexOutOfBoundsException.class,
        str -> str.append("Hello", 1, 10),
        txt -> txt.append("Hello", 1, 10));
    assertThrows(
        IndexOutOfBoundsException.class,
        str -> str.append("Hello", 3, 2),
        txt -> txt.append("Hello", 3, 2));
  }

  @Test
  void testAppendCharArray_Errors() {
    assertThrows(
        NullPointerException.class,
        str -> str.append((char[]) null),
        txt -> txt.append((char[]) null));
  }

  private static void assertEquals(Consumer<StringBuilder> expected, Consumer<Appender> actual) {
    StringBuilder e = new StringBuilder();
    expected.accept(e);
    TextBuilder a = new TextBuilder();
    actual.accept(a);
    Assertions.assertEquals(e.toString(), a.toString(), "StringBuilder != TextBuilder");
    Assertions.assertEquals(e.length(), a.length());
    // also compare against a StringBuilder wrapped in Appender
    Appender wrapped = Appender.of(new StringBuilder());
    actual.accept(wrapped);
    Assertions.assertEquals(
        e.toString(), wrapped.toString(), "StringBuilder != Appender(StringBuilder)");
    // and compare against a wrapped StringBuffer (to test default methods)
    Appender wrappedDefault = Appender.of(new StringBuffer());
    actual.accept(wrappedDefault);
    Assertions.assertEquals(
        e.toString(), wrappedDefault.toString(), "StringBuilder != Appender(StringBuffer)");
  }

  private static void assertThrows(
      Class<? extends Throwable> expectedType,
      Consumer<StringBuilder> expected,
      Consumer<Appender> actual) {
    StringBuilder e = new StringBuilder();
    Assertions.assertThrows(expectedType, () -> expected.accept(e));
    TextBuilder a = new TextBuilder();
    Assertions.assertThrows(expectedType, () -> actual.accept(a));
    // also compare against a StringBuilder wrapped in Appender
    Appender wrapped = Appender.of(new StringBuilder());
    Assertions.assertThrows(expectedType, () -> actual.accept(wrapped));
    // and compare against a wrapped StringBuffer (to test default methods)
    Appender wrappedDefault = Appender.of(new StringBuffer());
    Assertions.assertThrows(expectedType, () -> actual.accept(wrappedDefault));
  }
}
