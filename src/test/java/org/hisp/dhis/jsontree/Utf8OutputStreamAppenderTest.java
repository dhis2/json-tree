package org.hisp.dhis.jsontree;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link Utf8OutputStreamAppender}.
 *
 * <p>Because the sink encodes to UTF-8 bytes on an {@link OutputStream}, tests here decode the
 * captured bytes back to a {@link String} and compare against expectations. For cases where
 * {@link StringBuilder} would behave differently — in particular, lone surrogates are replaced by
 * U+FFFD on a UTF-8 sink but preserved by {@link StringBuilder} — the expected string is spelled
 * out explicitly rather than going through the {@link StringBuilder} equivalence harness.
 *
 * @author Jan Bernitt (with AI)
 */
class Utf8OutputStreamAppenderTest {

  private static final String REPLACEMENT = "\uFFFD"; // U+FFFD

  /*
   * appendAscii
   */

  @Test
  void testAppendAsciiChar() {
    assertEquals(
        "abc\n\t\\",
        encode(a -> {
          a.appendAscii('a');
          a.appendAscii('b');
          a.appendAscii('c');
          a.appendAscii('\n');
          a.appendAscii('\t');
          a.appendAscii('\\');
        }));
  }

  @Test
  void testAppendAsciiCharSequence() {
    assertEquals(
        "Hello World",
        encode(a -> {
          a.appendAscii("");
          a.appendAscii("Hello");
          a.appendAscii(" ");
          a.appendAscii("World");
        }));
  }

  @Test
  void testAppendAsciiCharSequenceRange() {
    assertEquals("Hello, world!", encode(a -> a.appendAscii("Hello, world!", 0, 13)));
    assertEquals("world!", encode(a -> a.appendAscii("Hello, world!", 7, 13)));
    assertEquals("ello", encode(a -> a.appendAscii("Hello, world!", 1, 5)));
    assertEquals("", encode(a -> a.appendAscii("Hello, world!", 3, 3)));
  }

  @Test
  void testAppendAsciiNull() {
    assertEquals(
        "null|null|",
        encode(a -> {
          a.appendAscii(null);
          a.appendAscii("|");
          a.appendAscii(null, 0, 4);
          a.appendAscii("|");
          a.appendAscii(null, 0, 0);
        }));
  }

  @Test
  void testAppendAsciiNull_EmptyRange() {
    assertEquals("", encode(a -> a.appendAscii(null, 0, 0)));
    assertEquals("", encode(a -> a.appendEncoded(null, 0, 0)));
  }

  @Test
  void testAppendAsciiNull_PartialRange() {
    // "null" sliced to [1,3) → "ul"
    assertEquals("ul", encode(a -> a.appendAscii(null, 1, 3)));
  }

  /*
   * appendEncoded
   */

  @Test
  void testAppendEncodedChar_Ascii() {
    assertEquals("xyz", encode(a -> {
      a.appendEncoded('x');
      a.appendEncoded('y');
      a.appendEncoded('z');
    }));
  }

  @Test
  void testAppendEncodedChar_TwoByte() {
    assertEquals("©", encode(a -> a.appendEncoded('©')));
  }

  @Test
  void testAppendEncodedChar_ThreeByte() {
    assertEquals("€", encode(a -> a.appendEncoded('€')));
  }

  @Test
  void testAppendEncodedChar_LoneHighSurrogate() {
    assertEquals(REPLACEMENT, encode(a -> a.appendEncoded('\uD83D')));
  }

  @Test
  void testAppendEncodedChar_LoneLowSurrogate() {
    assertEquals(REPLACEMENT, encode(a -> a.appendEncoded('\uDE00')));
  }

  @Test
  void testAppendEncodedCharSequence_Ascii() {
    assertEquals("Hello World", encode(a -> a.appendEncoded("Hello World")));
  }

  @Test
  void testAppendEncodedCharSequence_Mixed() {
    assertEquals(
        "a©b€c\uD83D\uDE00d",
        encode(a -> a.appendEncoded("a©b€c\uD83D\uDE00d")));
  }

  @Test
  void testAppendEncodedCharSequence_SurrogatePair() {
    assertEquals("\uD83D\uDE00", encode(a -> a.appendEncoded("\uD83D\uDE00")));
  }

  @Test
  void testAppendEncodedCharSequence_LoneHighSurrogate() {
    assertEquals(REPLACEMENT, encode(a -> a.appendEncoded("\uD83D")));
  }

  @Test
  void testAppendEncodedCharSequence_LoneLowSurrogate() {
    assertEquals(REPLACEMENT, encode(a -> a.appendEncoded("\uDE00")));
  }

  @Test
  void testAppendEncodedCharSequence_HighSurrogateNotFollowedByLow() {
    assertEquals(REPLACEMENT + "a", encode(a -> a.appendEncoded("\uD83Da")));
  }

  @Test
  void testAppendEncodedCharSequence_LowSurrogateInMiddle() {
    assertEquals(
        "a" + REPLACEMENT + "b", encode(a -> a.appendEncoded("a\uDE00b")));
  }

  @Test
  void testAppendEncodedCharSequence_Range() {
    assertEquals(
        "©b€", encode(a -> a.appendEncoded("a©b€c", 1, 4)));
  }

  @Test
  void testAppendEncodedCharSequence_RangeCutsSurrogatePair() {
    assertEquals(REPLACEMENT, encode(a -> a.appendEncoded("\uD83D\uDE00", 0, 1)));
  }

  @Test
  void testAppendEncodedCharSequence_Text() {
    Text text = Text.of("a©b\uD83D\uDE00c");
    assertEquals(
        "a©b\uD83D\uDE00c", encode(a -> a.appendEncoded(text, 0, text.length())));
  }

  /*
   * appendJsonEscaped
   */

  @Test
  void testAppendJsonEscaped_Null() {
    assertEquals("null", encode(a -> a.appendJsonEscaped(null)));
  }

  @Test
  void testAppendJsonEscaped_Empty() {
    assertEquals("\"\"", encode(a -> a.appendJsonEscaped("")));
  }

  @Test
  void testAppendJsonEscaped_Plain() {
    assertEquals("\"hello\"", encode(a -> a.appendJsonEscaped("hello")));
  }

  @Test
  void testAppendJsonEscaped_Escapes() {
    assertEquals("\"\\\"\"", encode(a -> a.appendJsonEscaped("\"")));
    assertEquals("\"\\\\\"", encode(a -> a.appendJsonEscaped("\\")));
    assertEquals("\"\\n\"", encode(a -> a.appendJsonEscaped("\n")));
    assertEquals("\"\\r\"", encode(a -> a.appendJsonEscaped("\r")));
    assertEquals("\"\\t\"", encode(a -> a.appendJsonEscaped("\t")));
    assertEquals("\"\\b\"", encode(a -> a.appendJsonEscaped("\b")));
    assertEquals("\"\\f\"", encode(a -> a.appendJsonEscaped("\f")));
  }

  @Test
  void testAppendJsonEscaped_Controls() {
    assertEquals("\"\\u0000\"", encode(a -> a.appendJsonEscaped("\u0000")));
    assertEquals("\"\\u0001\"", encode(a -> a.appendJsonEscaped("\u0001")));
    assertEquals("\"\\u000B\"", encode(a -> a.appendJsonEscaped("\u000B")));
    assertEquals("\"\\u001A\"", encode(a -> a.appendJsonEscaped("\u001A")));
    assertEquals("\"\\u001F\"", encode(a -> a.appendJsonEscaped("\u001F")));
  }

  @Test
  void testAppendJsonEscaped_JavaScriptLineSeparators() {
    assertEquals("\"\\u2028\"", encode(a -> a.appendJsonEscaped("\u2028")));
    assertEquals("\"\\u2029\"", encode(a -> a.appendJsonEscaped("\u2029")));
  }

  @Test
  void testAppendJsonEscaped_NonAsciiPassthrough() {
    assertEquals("\"©\"", encode(a -> a.appendJsonEscaped("©")));
    assertEquals(
        "\"\uD83D\uDE00\"", encode(a -> a.appendJsonEscaped("\uD83D\uDE00")));
  }

  @Test
  void testAppendJsonEscaped_Mixed() {
    assertEquals(
        "\"a\\\"b©\uD83D\uDE00d\\n\"",
        encode(a -> a.appendJsonEscaped("a\"b©\uD83D\uDE00d\n")));
  }

  @Test
  void testAppendJsonEscaped_SurrogatePairInEscapePath() {
    // leading quote forces the escape path; the pair sits in a clean run
    assertEquals(
        "\"\\\"x\uD83D\uDE00\\\"a\"",
        encode(a -> a.appendJsonEscaped("\"x\uD83D\uDE00\"a")));
  }

  @Test
  void testAppendJsonEscaped_LoneSurrogate() {
    assertEquals(
        "\"" + REPLACEMENT + "\"", encode(a -> a.appendJsonEscaped("\uD83D")));
  }

  @Test
  void testAppendJsonEscaped_TextWithLongCleanRuns() {
    Text input = Text.of("aaaaaaaaaaaaaaaa\"bbbbbbbbbbbbbbbb");
    assertEquals(
        "\"aaaaaaaaaaaaaaaa\\\"bbbbbbbbbbbbbbbb\"",
        encode(a -> a.appendJsonEscaped(input)));
  }

  /*
   * appendCodePoint
   */

  @Test
  void testAppendCodePoint_Ascii() {
    assertEquals("A", encode(a -> a.appendCodePoint('A')));
  }

  @Test
  void testAppendCodePoint_TwoByte() {
    assertEquals("©", encode(a -> a.appendCodePoint(0x00A9)));
  }

  @Test
  void testAppendCodePoint_ThreeByte() {
    assertEquals("€", encode(a -> a.appendCodePoint(0x20AC)));
  }

  @Test
  void testAppendCodePoint_Supplementary() {
    assertEquals("\uD83D\uDE00", encode(a -> a.appendCodePoint(0x1F600)));
  }

  @Test
  void testAppendCodePoint_Supplementary_NotSurrogate() {
    // regression: 0x1D800 truncates to 0xD800 as a char, but must not be treated as a surrogate
    // U+1D800 encodes to F0 9D A0 80, which round-trips through StandardCharsets.UTF_8 as
    // the surrogate pair \uD836\uDC00 (not \uD835\uDC00 — 0xD800 is the pair offset, not cp>>10)
    assertEquals("\uD836\uDC00", encode(a -> a.appendCodePoint(0x1D800)));
  }

  @Test
  void testAppendCodePoint_MaxValid() {
    assertEquals("\uDBFF\uDFFF", encode(a -> a.appendCodePoint(0x10FFFF)));
  }

  @Test
  void testAppendCodePoint_LoneSurrogate() {
    assertEquals(REPLACEMENT, encode(a -> a.appendCodePoint(0xD800)));
    assertEquals(REPLACEMENT, encode(a -> a.appendCodePoint(0xDFFF)));
  }

  @Test
  void testAppendCodePoint_Invalid() {
    assertThrows(
        IllegalArgumentException.class, () -> encode(a -> a.appendCodePoint(-1)));
    assertThrows(
        IllegalArgumentException.class, () -> encode(a -> a.appendCodePoint(0x110000)));
    assertThrows(
        IllegalArgumentException.class, () -> encode(a -> a.appendCodePoint(Integer.MIN_VALUE)));
    assertThrows(
        IllegalArgumentException.class, () -> encode(a -> a.appendCodePoint(Integer.MAX_VALUE)));
  }

  /*
   * appendInt / appendLong / appendDouble
   */

  @Test
  void testAppendInt() {
    assertEquals("0", encode(a -> a.appendInt(0)));
    assertEquals("42", encode(a -> a.appendInt(42)));
    assertEquals("-123", encode(a -> a.appendInt(-123)));
    assertEquals(
        String.valueOf(Integer.MAX_VALUE), encode(a -> a.appendInt(Integer.MAX_VALUE)));
    assertEquals(
        String.valueOf(Integer.MIN_VALUE), encode(a -> a.appendInt(Integer.MIN_VALUE)));
  }

  @Test
  void testAppendLong() {
    assertEquals("0", encode(a -> a.appendLong(0L)));
    assertEquals("9876543210", encode(a -> a.appendLong(9876543210L)));
    assertEquals("-1234567890123", encode(a -> a.appendLong(-1234567890123L)));
    assertEquals(
        String.valueOf(Long.MAX_VALUE), encode(a -> a.appendLong(Long.MAX_VALUE)));
    assertEquals(
        String.valueOf(Long.MIN_VALUE), encode(a -> a.appendLong(Long.MIN_VALUE)));
  }

  @Test
  void testAppendDouble() {
    assertEquals("3.14159", encode(a -> a.appendDouble(3.14159)));
    assertEquals("-2.5", encode(a -> a.appendDouble(-2.5)));
    assertEquals("NaN", encode(a -> a.appendDouble(Double.NaN)));
    assertEquals("Infinity", encode(a -> a.appendDouble(Double.POSITIVE_INFINITY)));
    assertEquals("-Infinity", encode(a -> a.appendDouble(Double.NEGATIVE_INFINITY)));
    assertEquals("1.1", encode(a -> a.appendDouble(1.1)));
    assertEquals("-2.22", encode(a -> a.appendDouble(-2.22)));
    assertEquals("3.333", encode(a -> a.appendDouble(3.333)));
    assertEquals("4.4444", encode(a -> a.appendDouble(4.4444)));
  }

  @Test
  void testAppendDouble_EdgeCases() {
    assertEquals("-0.0", encode(a -> a.appendDouble(-0d)));
    assertEquals("1.0", encode(a -> a.appendDouble(1d)));
    assertEquals("0.0", encode(a -> a.appendDouble(0.0d)));
    assertEquals("1.23456789E8", encode(a -> a.appendDouble(1.23456789e8)));
    assertEquals("0.2", encode(a -> a.appendDouble(0.2)));
    assertEquals("0.3", encode(a -> a.appendDouble(0.3)));
    assertEquals(
        "0.30000000000000004", encode(a -> a.appendDouble(0.1 + 0.2)));
  }

  @Test
  void testAppendDouble_IntegralBoundary() {
    // |value| < 1e7 → appendLong + ".0"; at/above 1e7 → String.valueOf (scientific notation)
    assertEquals("9999999.0", encode(a -> a.appendDouble(9999999.0)));
    assertEquals("1.0E7", encode(a -> a.appendDouble(10000000.0)));
    assertEquals("-9999999.0", encode(a -> a.appendDouble(-9999999.0)));
    assertEquals("-1.0E7", encode(a -> a.appendDouble(-10000000.0)));
  }

  /*
   * Buffer boundary behaviour — the sink uses a fixed 8192-byte buffer, so writes that cross
   * the boundary exercise the flush logic in each of appendAscii, appendEncoded, and the escape
   * writer. These tests deliberately span several buffer fills and check the full decoded output.
   */

  @Test
  void testBufferBoundary_AsciiOverflow() {
    String big = "x".repeat(20_000); // > 2 buffer fills
    assertEquals(big, encode(a -> a.appendAscii(big)));
  }

  @Test
  void testBufferBoundary_MultiByteOverflow() {
    String big = "€".repeat(5000); // 3 bytes each = 15,000 bytes
    assertEquals(big, encode(a -> a.appendEncoded(big, 0, big.length())));
  }

  @Test
  void testBufferBoundary_AsciiThenSupplementary() {
    String big = "x".repeat(8190) + "\uD83D\uDE00";
    assertEquals(big, encode(a -> a.appendEncoded(big, 0, big.length())));
  }

  @Test
  void testBufferBoundary_JsonEscapedOverflow() {
    // long plain run with one escaped char mid-way; forces flush inside the escape path
    String big = "x".repeat(10_000) + "\n" + "y".repeat(10_000);
    assertEquals("\"" + "x".repeat(10_000) + "\\n" + "y".repeat(10_000) + '"', encode(a -> a.appendJsonEscaped(big)));
  }

  @Test
  void testChunkBoundary_SurrogatePairStraddlingFirstChunk() {
    // With a fresh buffer (pos = 0), the first chunk is exactly BUFFER_SIZE/4 = 2048 chars.
    // Place a surrogate pair so the high surrogate would be the last char of that chunk.
    String input = "x".repeat(2047) + "\uD83D\uDE00" + "y";
    assertEquals(input, encode(a -> a.appendEncoded(input)));
  }

  @Test
  void testChunkBoundary_SurrogatePairStraddlingRepeatedChunks() {
    // Fill the buffer with pairs such that every 2048-char chunk boundary risks a split
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < 5000; i++) sb.append("x").append("\uD83D\uDE00");
    String input = sb.toString();
    assertEquals(input, encode(a -> a.appendEncoded(input)));
  }

  /*
   * close()
   */

  @Test
  void testClose_FlushesAndClosesUnderlying() {
    RecordingStream out = new RecordingStream();
    Appender a = Appender.ofUTF8(out);
    a.appendAscii("hi");
    assertEquals(0, out.bytes.size(), "data should still be buffered in appender");
    assertFalse(out.closed, "underlying stream must not be closed before close()");
    a.close();
    assertTrue(out.closed, "underlying stream must be closed by Appender.close()");
    assertEquals("hi", out.bytes.toString(StandardCharsets.UTF_8));
  }

  /*
   * Helpers
   */

  private static String encode(Consumer<Appender> actions) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (Appender a = Appender.ofUTF8(out)) {
      actions.accept(a);
    }
    return out.toString(StandardCharsets.UTF_8);
  }

  /**
   * Minimal {@link OutputStream} that records bytes and flags {@link #close()}, so tests can
   * distinguish "still buffered inside the appender" from "flushed to the sink".
   */
  private static final class RecordingStream extends OutputStream {
    final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    boolean closed = false;

    @Override
    public void write(int b) {
      bytes.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) {
      bytes.write(b, off, len);
    }

    @Override
    public void close() {
      closed = true;
    }
  }
}