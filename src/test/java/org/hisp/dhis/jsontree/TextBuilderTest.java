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
        str -> {
          str.append('a');
          str.append('b');
          str.append('c');
          str.append('\n');
          str.append('\t');
          str.append('\\');
          str.append('©');
          str.append('\uD83D');
        },
        txt -> {
          txt.appendAscii('a');
          txt.appendAscii('b');
          txt.appendAscii('c');
          txt.appendAscii('\n');
          txt.appendAscii('\t');
          txt.appendAscii('\\');
          txt.appendEncoded('©');
          txt.appendEncoded('\uD83D');
        });
  }

  @Test
  void testAppendCharSequence() {
    String nonAscii = "A©\uD83D\uDE00";
    assertEquals(
        str -> {
          str.append("");
          str.append("Hello");
          str.append(" ");
          str.append("World");
          str.append(nonAscii);
        },
        txt -> {
          txt.appendAscii("");
          txt.appendAscii("Hello");
          txt.appendAscii(" ");
          txt.appendAscii("World");
          // use the 3-arg form on purpose: the 2-arg default currently routes
          // through appendAscii, which is wrong for non-ASCII on encoded sinks
          txt.appendEncoded(nonAscii, 0, nonAscii.length());
        });
  }

  @Test
  void testAppendNullCharSequence() {
    TextBuilder txt = new TextBuilder();
    txt.appendAscii(null);
    txt.appendAscii("|");
    txt.appendEncoded(null);
    txt.appendAscii("|");
    txt.appendAscii(null, 0, 4);
    txt.appendAscii("|");
    txt.appendEncoded(null, 0, 4);
    Assertions.assertEquals("null|null|null|null", txt.toString());
  }

  @Test
  void testAppendNullCharSequence_ZeroLength() {
    TextBuilder txt = new TextBuilder();
    txt.appendAscii(null, 0, 0);
    txt.appendEncoded(null, 0, 0);
    Assertions.assertEquals("", txt.toString());
  }

  @Test
  void testAppendNullCharSequence_OutOfBounds() {
    assertThrows(
        IndexOutOfBoundsException.class,
        str -> str.append((CharSequence) null, 0, 5),
        txt -> txt.appendAscii(null, 0, 5));
    assertThrows(
        IndexOutOfBoundsException.class,
        str -> str.append((CharSequence) null, 0, 5),
        txt -> txt.appendEncoded(null, 0, 5));
    assertThrows(
        IndexOutOfBoundsException.class,
        str -> str.append((CharSequence) null, -1, 3),
        txt -> txt.appendEncoded(null, -1, 3));
    assertThrows(
        IndexOutOfBoundsException.class,
        str -> str.append((CharSequence) null, 3, 2),
        txt -> txt.appendEncoded(null, 3, 2));
  }

  @Test
  void testAppendCharSequenceRange() {
    assertEquals(
        str -> str.append("Hello, world!", 0, 13),
        txt -> txt.appendAscii("Hello, world!", 0, 13));
    assertEquals(
        str -> str.append("Hello, world!", 7, 13),
        txt -> txt.appendAscii("Hello, world!", 7, 13));
    assertEquals(
        str -> str.append("Hello, world!", 1, 5),
        txt -> txt.appendAscii("Hello, world!", 1, 5));
    assertEquals(
        str -> str.append("Hello, world!", 3, 3),
        txt -> txt.appendAscii("Hello, world!", 3, 3));
  }


  @Test
  void testAppendInt() {
    assertEquals(
        str -> {
          str.append(0);
          str.append(42);
          str.append(-123);
          str.append(Integer.MAX_VALUE);
          str.append(Integer.MIN_VALUE);
          str.append(10);
          str.append(-5);
          str.append(0);
        },
        txt -> {
          txt.appendInt(0);
          txt.appendInt(42);
          txt.appendInt(-123);
          txt.appendInt(Integer.MAX_VALUE);
          txt.appendInt(Integer.MIN_VALUE);
          txt.appendInt(10);
          txt.appendInt(-5);
          txt.appendInt(0);
        });
  }

  @Test
  void testAppendLong() {
    assertEquals(
        str -> {
          str.append(0L);
          str.append(9876543210L);
          str.append(-1234567890123L);
          str.append(Long.MAX_VALUE);
          str.append(Long.MIN_VALUE);
          str.append(100L);
          str.append(-200L);
          str.append(300L);
        },
        txt -> {
          txt.appendLong(0L);
          txt.appendLong(9876543210L);
          txt.appendLong(-1234567890123L);
          txt.appendLong(Long.MAX_VALUE);
          txt.appendLong(Long.MIN_VALUE);
          txt.appendLong(100L);
          txt.appendLong(-200L);
          txt.appendLong(300L);
        });
  }

  @Test
  void testAppendDouble() {
    assertEquals(
        str -> {
          str.append(3.14159);
          str.append(-2.5);
          str.append(Double.NaN);
          str.append(Double.POSITIVE_INFINITY);
          str.append(Double.NEGATIVE_INFINITY);
          str.append(1.1);
          str.append(-2.22);
          str.append(3.333);
          str.append(4.4444);
        },
        txt -> {
          txt.appendDouble(3.14159);
          txt.appendDouble(-2.5);
          txt.appendDouble(Double.NaN);
          txt.appendDouble(Double.POSITIVE_INFINITY);
          txt.appendDouble(Double.NEGATIVE_INFINITY);
          txt.appendDouble(1.1);
          txt.appendDouble(-2.22);
          txt.appendDouble(3.333);
          txt.appendDouble(4.4444);
        });
  }

  @Test
  void testAppendDouble_EdgeCases() {
    assertEquals(
        str -> {
          str.append(-0d);
          str.append(1d);
          str.append(0.0d);
          str.append(1.23456789e8);
          str.append(0.2);
          str.append(0.3);
          str.append(0.1 + 0.2);
        },
        txt -> {
          txt.appendDouble(-0d);
          txt.appendDouble(1d);
          txt.appendDouble(0.0d);
          txt.appendDouble(1.23456789e8);
          txt.appendDouble(0.2);
          txt.appendDouble(0.3);
          txt.appendDouble(0.1 + 0.2);
        });
  }

  @Test
  void testAppendCodePoint() {
    // 0x10FFFF is max range
    assertEquals(
        str -> {
          str.appendCodePoint('A');
          str.appendCodePoint(0x20AC);
          str.appendCodePoint(0x1F600);
          str.appendCodePoint(0x10FFFF);
        },
        txt -> {
          txt.appendCodePoint('A');
          txt.appendCodePoint(0x20AC);
          txt.appendCodePoint(0x1F600);
          txt.appendCodePoint(0x10FFFF);
        });
  }

  @Test
  void testAppendCharSequence_Errors() {
    assertThrows(
        IndexOutOfBoundsException.class,
        str -> str.append("Hello", -1, 2),
        txt -> txt.appendAscii("Hello", -1, 2));
    assertThrows(
        IndexOutOfBoundsException.class,
        str -> str.append("Hello", 1, 10),
        txt -> txt.appendAscii("Hello", 1, 10));
    assertThrows(
        IndexOutOfBoundsException.class,
        str -> str.append("Hello", 3, 2),
        txt -> txt.appendAscii("Hello", 3, 2));
  }

  @Test
  void testAppendJsonEscaped_Null() {
    assertJsonEscaped("null", null);
  }

  @Test
  void testAppendJsonEscaped_Empty() {
    assertJsonEscaped("\"\"", "");
  }

  @Test
  void testAppendJsonEscaped_Plain() {
    assertJsonEscaped("\"hello world\"", "hello world");
  }

  @Test
  void testAppendJsonEscaped_Escapes() {
    assertJsonEscaped("\"\\\"\"", "\"");     // " → \"
    assertJsonEscaped("\"\\\\\"", "\\");     // \ → \\
    assertJsonEscaped("\"\\n\"",  "\n");     // LF → \n
    assertJsonEscaped("\"\\r\"",  "\r");     // CR → \r
    assertJsonEscaped("\"\\t\"",  "\t");     // TAB → \t
    assertJsonEscaped("\"\\b\"",  "\b");     // BS → \b
    assertJsonEscaped("\"\\f\"",  "\f");     // FF → \f
  }

  @Test
  void testAppendJsonEscaped_Controls() {
    // exercise /uXXXX path, including upper-case hex A-F
    assertJsonEscaped("\"\\u0000\"", "\u0000");
    assertJsonEscaped("\"\\u0001\"", "\u0001");
    assertJsonEscaped("\"\\u000B\"", "\u000B");   // VT
    assertJsonEscaped("\"\\u000E\"", "\u000E");   // SO
    assertJsonEscaped("\"\\u001A\"", "\u001A");   // SUB
    assertJsonEscaped("\"\\u001F\"", "\u001F");   // US
  }

  @Test
  void testAppendJsonEscaped_JavaScriptLineSeparators() {
    assertJsonEscaped("\"\\u2028\"", "\u2028");
    assertJsonEscaped("\"\\u2029\"", "\u2029");
  }

  @Test
  void testAppendJsonEscaped_NonAsciiPassthrough() {
    assertJsonEscaped("\"\u00A9\"", "\u00A9");                 // ©
    assertJsonEscaped("\"\uD83D\uDE00\"", "\uD83D\uDE00");     // 😀
  }

  @Test
  void testAppendJsonEscaped_Mixed() {
    assertJsonEscaped(
        "\"a\\\"b\u00A9\uD83D\uDE00d\\n\"",
        "a\"b\u00A9\uD83D\uDE00d\n");
  }

  @Test
  void testAppendJsonEscaped_TextFastPath() {
    // fast path (no escapes) with a Text longer than 16 so appendSlice hits copyTo
    Text input = Text.of("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");  // 31 a's
    TextBuilder txt = new TextBuilder();
    txt.appendJsonEscaped(input);
    Assertions.assertEquals(
        "\"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"",
        txt.toString());
  }

  @Test
  void testAppendJsonEscaped_TextWithLongCleanRuns() {
    // regression: escape path with start > 0 and > 16-char clean run after the escape
    // (this is the shape that the appendSlice start/len bug hit)
    Text input = Text.of("aaaaaaaaaaaaaaaa\"bbbbbbbbbbbbbbbb");  // 16 a's, ", 16 b's
    TextBuilder txt = new TextBuilder();
    txt.appendJsonEscaped(input);
    Assertions.assertEquals(
        "\"aaaaaaaaaaaaaaaa\\\"bbbbbbbbbbbbbbbb\"",
        txt.toString());
  }

  @Test
  void testAppendCodePoint_Surrogates() {
    assertEquals(
        str -> {
          str.appendCodePoint(Character.MIN_SURROGATE);  // 0xD800
          str.appendCodePoint(0xDBFF);
          str.appendCodePoint(0xDC00);
          str.appendCodePoint(Character.MAX_SURROGATE);  // 0xDFFF
        },
        txt -> {
          txt.appendCodePoint(Character.MIN_SURROGATE);
          txt.appendCodePoint(0xDBFF);
          txt.appendCodePoint(0xDC00);
          txt.appendCodePoint(Character.MAX_SURROGATE);
        });
  }

  @Test
  void testAppendCodePoint_SupplementaryBoundary() {
    assertEquals(
        str -> {
          str.appendCodePoint(Character.MIN_SUPPLEMENTARY_CODE_POINT);  // 0x10000
          str.appendCodePoint(0x1F600);
          str.appendCodePoint(Character.MAX_CODE_POINT);                // 0x10FFFF
        },
        txt -> {
          txt.appendCodePoint(Character.MIN_SUPPLEMENTARY_CODE_POINT);
          txt.appendCodePoint(0x1F600);
          txt.appendCodePoint(Character.MAX_CODE_POINT);
        });
  }

  @Test
  void testAppendCodePoint_InvalidValues() {
    assertThrows(IllegalArgumentException.class,
        str -> str.appendCodePoint(-1),
        txt -> txt.appendCodePoint(-1));
    assertThrows(IllegalArgumentException.class,
        str -> str.appendCodePoint(0x110000),
        txt -> txt.appendCodePoint(0x110000));
    assertThrows(IllegalArgumentException.class,
        str -> str.appendCodePoint(Integer.MIN_VALUE),
        txt -> txt.appendCodePoint(Integer.MIN_VALUE));
    assertThrows(IllegalArgumentException.class,
        str -> str.appendCodePoint(Integer.MAX_VALUE),
        txt -> txt.appendCodePoint(Integer.MAX_VALUE));
  }

  @Test
  void testAppendDouble_ScientificNotation() {
    assertEquals(
        str -> {
          str.append(1e7);
          str.append(1e8);
          str.append(1e-4);
          str.append(1.5e10);
          str.append(1.5e-10);
          str.append(-1e7);
          str.append(-1e-4);
        },
        txt -> {
          txt.appendDouble(1e7);
          txt.appendDouble(1e8);
          txt.appendDouble(1e-4);
          txt.appendDouble(1.5e10);
          txt.appendDouble(1.5e-10);
          txt.appendDouble(-1e7);
          txt.appendDouble(-1e-4);
        });
  }

  @Test
  void testAppendDouble_IntegralBoundary() {
    // boundary at |value| < 1e7: below prints via appendLong + ".0", at/above via String.valueOf
    assertEquals(
        str -> {
          str.append(9999999.0);
          str.append(10000000.0);
          str.append(-9999999.0);
          str.append(-10000000.0);
        },
        txt -> {
          txt.appendDouble(9999999.0);
          txt.appendDouble(10000000.0);
          txt.appendDouble(-9999999.0);
          txt.appendDouble(-10000000.0);
        });
  }

  private static void assertEquals(Consumer<StringBuilder> expected, Consumer<Appender> actual) {
    StringBuilder e = new StringBuilder();
    expected.accept(e);

    TextBuilder a = new TextBuilder();
    actual.accept(a);
    Assertions.assertEquals(e.toString(), a.toString(), "StringBuilder != TextBuilder");
    Assertions.assertEquals(e.length(), a.length());
  }

  private static void assertJsonEscaped(String expected, CharSequence input) {
    TextBuilder txt = new TextBuilder();
    txt.appendJsonEscaped(input);
    Assertions.assertEquals(expected, txt.toString());
  }

  private static void assertThrows(
      Class<? extends Throwable> expectedType,
      Consumer<StringBuilder> expected,
      Consumer<Appender> actual) {
    StringBuilder e = new StringBuilder();
    Assertions.assertThrows(expectedType, () -> expected.accept(e));

    TextBuilder a = new TextBuilder();
    Assertions.assertThrows(expectedType, () -> actual.accept(a));
  }
}