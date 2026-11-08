package org.hisp.dhis.jsontree;

import java.util.function.Consumer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the {@link TextBuilder}.
 *
 * @author Jan Bernitt
 */
class TextBuilderTest {

  @Test
  void testAppendChar() {
    assertAppendEquals(
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
    assertAppendEquals(
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
    assertEquals("null|null|null|null", txt.toString());
  }

  @Test
  void testAppendNullCharSequence_ZeroLength() {
    TextBuilder txt = new TextBuilder();
    txt.appendAscii(null, 0, 0);
    txt.appendEncoded(null, 0, 0);
    assertEquals("", txt.toString());
  }

  @Test
  void testAppendNullCharSequence_OutOfBounds() {
    assertAppendThrows(
        IndexOutOfBoundsException.class,
        str -> str.append((CharSequence) null, 0, 5),
        txt -> txt.appendAscii(null, 0, 5));
    assertAppendThrows(
        IndexOutOfBoundsException.class,
        str -> str.append((CharSequence) null, 0, 5),
        txt -> txt.appendEncoded(null, 0, 5));
    assertAppendThrows(
        IndexOutOfBoundsException.class,
        str -> str.append((CharSequence) null, -1, 3),
        txt -> txt.appendEncoded(null, -1, 3));
    assertAppendThrows(
        IndexOutOfBoundsException.class,
        str -> str.append((CharSequence) null, 3, 2),
        txt -> txt.appendEncoded(null, 3, 2));
  }

  @Test
  void testAppendCharSequenceRange() {
    assertAppendEquals(
        str -> str.append("Hello, world!", 0, 13),
        txt -> txt.appendAscii("Hello, world!", 0, 13));
    assertAppendEquals(
        str -> str.append("Hello, world!", 7, 13),
        txt -> txt.appendAscii("Hello, world!", 7, 13));
    assertAppendEquals(
        str -> str.append("Hello, world!", 1, 5),
        txt -> txt.appendAscii("Hello, world!", 1, 5));
    assertAppendEquals(
        str -> str.append("Hello, world!", 3, 3),
        txt -> txt.appendAscii("Hello, world!", 3, 3));
  }


  @Test
  void testAppendInt() {
    assertAppendEquals(
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
    assertAppendEquals(
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
  void testAppendFloat() {
    assertAppendEquals(
        str -> {
          str.append(3.14159f);
          str.append(-2.5f);
          str.append(Float.NaN);
          str.append(Float.POSITIVE_INFINITY);
          str.append(Float.NEGATIVE_INFINITY);
          str.append(1.1f);
          str.append(-2.22f);
          str.append(3.333f);
          str.append(4.4444f);
        },
        txt -> {
          txt.appendFloat(3.14159f);
          txt.appendFloat(-2.5f);
          txt.appendFloat(Float.NaN);
          txt.appendFloat(Float.POSITIVE_INFINITY);
          txt.appendFloat(Float.NEGATIVE_INFINITY);
          txt.appendFloat(1.1f);
          txt.appendFloat(-2.22f);
          txt.appendFloat(3.333f);
          txt.appendFloat(4.4444f);
        });
  }

  @Test
  void testAppendFloat_EdgeCases() {
    assertAppendEquals(
        str -> {
          str.append(-0f);
          str.append(1f);
          str.append(0.0f);
          str.append(1.23456789e8f);
          str.append(0.2f);
          str.append(0.3f);
          str.append(0.1f + 0.2f);
        },
        txt -> {
          txt.appendFloat(-0f);
          txt.appendFloat(1f);
          txt.appendFloat(0.0f);
          txt.appendFloat(1.23456789e8f);
          txt.appendFloat(0.2f);
          txt.appendFloat(0.3f);
          txt.appendFloat(0.1f + 0.2f);
        });
  }

  @Test
  void testAppendFloat_ScientificNotation() {
    assertAppendEquals(
        str -> {
          str.append(1e7f);
          str.append(1e8f);
          str.append(1e-4f);
          str.append(1.5e10f);
          str.append(1.5e-10f);
          str.append(-1e7f);
          str.append(-1e-4f);
        },
        txt -> {
          txt.appendFloat(1e7f);
          txt.appendFloat(1e8f);
          txt.appendFloat(1e-4f);
          txt.appendFloat(1.5e10f);
          txt.appendFloat(1.5e-10f);
          txt.appendFloat(-1e7f);
          txt.appendFloat(-1e-4f);
        });
  }

  @Test
  void testAppendFloat_IntegralBoundary() {
    // boundary at |value| < 1e7: below prints via appendLong + ".0", at/above via String.valueOf
    assertAppendEquals(
        str -> {
          str.append(9999999.0f);
          str.append(10000000.0f);
          str.append(-9999999.0f);
          str.append(-10000000.0f);
        },
        txt -> {
          txt.appendFloat(9999999.0f);
          txt.appendFloat(10000000.0f);
          txt.appendFloat(-9999999.0f);
          txt.appendFloat(-10000000.0f);
        });
  }

  @Test
  void testAppendDouble() {
    assertAppendEquals(
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
    assertAppendEquals(
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
    assertAppendEquals(
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
    assertAppendThrows(
        IndexOutOfBoundsException.class,
        str -> str.append("Hello", -1, 2),
        txt -> txt.appendAscii("Hello", -1, 2));
    assertAppendThrows(
        IndexOutOfBoundsException.class,
        str -> str.append("Hello", 1, 10),
        txt -> txt.appendAscii("Hello", 1, 10));
    assertAppendThrows(
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
    assertEquals(
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
    assertEquals(
        "\"aaaaaaaaaaaaaaaa\\\"bbbbbbbbbbbbbbbb\"",
        txt.toString());
  }

  @Test
  void testAppendCodePoint_Surrogates() {
    assertAppendEquals(
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
    assertAppendEquals(
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
    assertAppendThrows(IllegalArgumentException.class,
        str -> str.appendCodePoint(-1),
        txt -> txt.appendCodePoint(-1));
    assertAppendThrows(IllegalArgumentException.class,
        str -> str.appendCodePoint(0x110000),
        txt -> txt.appendCodePoint(0x110000));
    assertAppendThrows(IllegalArgumentException.class,
        str -> str.appendCodePoint(Integer.MIN_VALUE),
        txt -> txt.appendCodePoint(Integer.MIN_VALUE));
    assertAppendThrows(IllegalArgumentException.class,
        str -> str.appendCodePoint(Integer.MAX_VALUE),
        txt -> txt.appendCodePoint(Integer.MAX_VALUE));
  }

  @Test
  void testAppendDouble_ScientificNotation() {
    assertAppendEquals(
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
    assertAppendEquals(
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

  // ---------------------------------------------------------------------------
  // CharSeqFragment<Text> overrides: self / empty / textValue / toString
  // ---------------------------------------------------------------------------

  @Test
  void testSelf() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    Text t = b.self();
    assertEquals("hello", t.toString());
    // appending after snapshot must not affect the earlier view
    b.appendAscii(" world");
    assertEquals("hello", t.toString());
    assertEquals("hello world", b.toString());
  }

  @Test
  void testSelf_SurvivesBufferGrowth() {
    TextBuilder b = new TextBuilder(4);
    b.appendAscii("hi");
    Text t = b.self();
    // force reallocation (buffer replaced, old buffer untouched)
    for (int i = 0; i < 20; i++) b.appendAscii('x');
    assertEquals("hi", t.toString());
  }

  @Test
  void testTextValue() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    Text t = b.textValue();
    assertEquals("hello", t.toString());
    b.appendAscii(" world");
    assertEquals("hello", t.toString());
  }

  @Test
  void testToString() {
    TextBuilder b = new TextBuilder();
    assertEquals("", b.toString());
    b.appendAscii("hello");
    assertEquals("hello", b.toString());
  }

  // ---------------------------------------------------------------------------
  // wrap(char[], int, int) — the "share internal buffer" factory
  // ---------------------------------------------------------------------------

  @Test
  void testWrap_ValidRange() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    char[] arr = "ABCDE".toCharArray();
    Text t = b.wrap(arr, 1, 3);
    assertEquals("BCD", t.toString());
  }

  @Test
  void testWrap_WholeRange() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    char[] arr = "WORLD".toCharArray();
    assertEquals("WORLD", b.wrap(arr, 0, 5).toString());
  }

  @Test
  void testWrap_ZeroLengthSlice() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    char[] arr = new char[10];
    assertEquals("", b.wrap(arr, 2, 0).toString());
  }

  @Test
  void testWrap_OffsetAtContentLength_Throws() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    assertThrows(IndexOutOfBoundsException.class, () -> b.slice(5, 6));
    assertThrows(IndexOutOfBoundsException.class, () -> b.slice(5, 8));
    assertThrows(IndexOutOfBoundsException.class, () -> b.slice(6, 6));
  }

  @Test
  void testWrap_EndBeyondContentLength_Throws() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    assertThrows(IndexOutOfBoundsException.class, () -> b.slice( 0, 6));
    assertThrows(IndexOutOfBoundsException.class, () -> b.slice(3, 6));
    assertThrows(IndexOutOfBoundsException.class, () -> b.slice(4, 6));
  }

  @Test
  void testWrap_EmptyBuilder_AlwaysThrows() {
    TextBuilder b = new TextBuilder();
    assertThrows(IndexOutOfBoundsException.class, () -> b.slice(0, 1));
  }

  @Test
  void testWrap_ReturnsViewOfPassedBuffer() {
    // The returned Text is a no-copy view of the passed array (the whole point
    // of wrap: avoid defensive copies when sharing an existing buffer).
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    char[] arr = "AAAAA".toCharArray();
    Text t = b.wrap(arr, 0, 5);
    assertEquals("AAAAA", t.toString());
    // mutation of the buffer is visible through the view (it is not a copy)
    arr[0] = 'X';
    assertEquals("XAAAA", t.toString());
  }

  // ---------------------------------------------------------------------------
  // wrap(T proto) — the type-preserving factory (always copies)
  // ---------------------------------------------------------------------------

  @Test
  void testWrapProto_CopiesContent() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    Text t = b.wrap(Text.EMPTY);
    assertEquals("hello", t.toString());
    // result is independent of subsequent builder mutations
    b.appendAscii(" world");
    assertEquals("hello", t.toString());
    assertEquals("hello world", b.toString());
  }

  @Test
  void testWrapProto_EmptyBuilder() {
    TextBuilder b = new TextBuilder();
    Text t = b.wrap(Text.of("x")); // proto's content is irrelevant, only its wrap impl matters
    assertEquals(Text.EMPTY, t);
    assertEquals("", t.toString());
  }

  @Test
  void testWrapProto_IsIndependentCopy() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hi");
    Text t = b.wrap(Text.of("x"));
    b.appendAscii("there"); // mutate builder after taking the view
    assertEquals("hi", t.toString());
  }

  // ---------------------------------------------------------------------------
  // toCharArray()
  // ---------------------------------------------------------------------------

  @Test
  void testToCharArray() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    Assertions.assertArrayEquals(
        new char[] {'h', 'e', 'l', 'l', 'o'}, b.toCharArray());
  }

  @Test
  void testToCharArray_Empty() {
    TextBuilder b = new TextBuilder();
    Assertions.assertArrayEquals(new char[0], b.toCharArray());
  }

  @Test
  void testToCharArray_OnlyWrittenPortion() {
    // capacity may be larger than length; only the written part is returned
    TextBuilder b = new TextBuilder(100);
    b.appendAscii("hi");
    assertEquals(2, b.toCharArray().length);
  }

  @Test
  void testToCharArray_IsCopy() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    char[] arr = b.toCharArray();
    arr[0] = 'X';
    assertEquals("hello", b.toString()); // builder unchanged
    assertEquals('X', arr[0]);
  }

  // ---------------------------------------------------------------------------
  // copyTo(char[], int)
  // ---------------------------------------------------------------------------

  @Test
  void testCopyTo() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    char[] dest = new char[10];
    b.copyTo(dest, 2);
    Assertions.assertArrayEquals(
        new char[] {0, 0, 'h', 'e', 'l', 'l', 'o', 0, 0, 0}, dest);
  }

  @Test
  void testCopyTo_Empty() {
    TextBuilder b = new TextBuilder();
    char[] dest = new char[5];
    b.copyTo(dest, 0);
    Assertions.assertArrayEquals(new char[5], dest);
  }

  @Test
  void testCopyTo_ZeroDestOffset() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hi");
    char[] dest = new char[4];
    b.copyTo(dest, 0);
    Assertions.assertArrayEquals(new char[] {'h', 'i', 0, 0}, dest);
  }

  // ---------------------------------------------------------------------------
  // copyTo(char[], int, int, int)
  // ---------------------------------------------------------------------------

  @Test
  void testCopyToSubrange() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello world");
    char[] dest = new char[10];
    b.copyTo(dest, 2, 1, 4); // copy "ello"
    Assertions.assertArrayEquals(
        new char[] {0, 0, 'e', 'l', 'l', 'o', 0, 0, 0, 0}, dest);
  }

  @Test
  void testCopyToSubrange_Prefix() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    char[] dest = new char[3];
    b.copyTo(dest, 0, 0, 3);
    Assertions.assertArrayEquals(new char[] {'h', 'e', 'l'}, dest);
  }

  @Test
  void testCopyToSubrange_Suffix() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    char[] dest = new char[2];
    b.copyTo(dest, 0, 3, 2);
    Assertions.assertArrayEquals(new char[] {'l', 'o'}, dest);
  }

  @Test
  void testCopyToSubrange_Empty() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    char[] dest = new char[5];
    b.copyTo(dest, 0, 2, 0);
    Assertions.assertArrayEquals(new char[5], dest);
  }

  // ---------------------------------------------------------------------------
  // equals / hashCode
  // ---------------------------------------------------------------------------

  @Test
  void testEquals() {
    TextBuilder a = new TextBuilder();
    a.appendAscii("hello");
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    TextBuilder c = new TextBuilder();
    c.appendAscii("world");

    assertEquals(a, a);
    assertEquals(a, b);
    assertNotEquals(a, c);
    assertEquals(new TextBuilder(), new TextBuilder());

    // deliberately not equal to a String or Text of the same content
    assertNotEquals("hello", a);
    assertNotEquals(Text.of("hello"), a);
  }

  @Test
  void testHashCode() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    assertEquals(CharSeq.hashCode(b), b.hashCode());
  }

  // ---------------------------------------------------------------------------
  // inherited defaults that route through the overrides
  // ---------------------------------------------------------------------------

  @Test
  void testSubSequence_OnBuilder() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    assertEquals("ell", b.subSequence(1, 4).toString());
    assertEquals("llo", b.subSequence(2).toString());
    assertEquals("hello", b.subSequence(0, 5).toString());
  }

  @Test
  void testTrim_OnBuilder() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("  hello  ");
    assertEquals("hello", b.trim().toString());
  }

  @Test
  void testStrip_OnBuilder() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("\nhello\n");
    assertEquals("hello", b.strip().toString());
  }

  @Test
  void testShrink_OnBuilder() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("xxhelloxx");
    assertEquals("hello", b.shrink(c -> c == 'x').toString());
  }

  @Test
  void testContentEquals_OnBuilder() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    assertTrue(b.contentEquals("hello"));
    assertTrue(b.contentEquals(Text.of("hello")));
    assertFalse(b.contentEquals("world"));
  }

  @Test
  void testCompareTo_OnBuilder() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    assertTrue(b.compareTo(Text.of("world")) < 0);
    assertEquals(0, b.compareTo(Text.of("hello")));
    assertTrue(b.compareTo(Text.of("abc")) > 0);
  }

  @Test
   void testConcat_OnBuilder() {
     TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    assertEquals("hello world", b.concat(" world").toString());
  }

  @Test
  void testSubSequence_FromZero_OnBuilder() {
     TextBuilder b = new TextBuilder();
     b.appendAscii("hello");
    assertEquals("hel", b.subSequence(0, 3).toString());
   }

  // ---------------------------------------------------------------------------
  // wrap(char[], int, int) — the "share internal buffer" factory
  // ---------------------------------------------------------------------------

  @Test
  void testWrap_ValidRange2() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    char[] arr = "ABCDE".toCharArray();
    Text t = b.wrap(arr, 1, 3);
    assertEquals("BCD", t.toString());
  }

  @Test
  void testWrap_ExternalArray_LargerThanContent() {
    // external array: contract is a pure factory, only bounded by the array
    TextBuilder b = new TextBuilder();
    b.appendAscii("hi");
    char[] arr = "MUCHLONGERCONTENT".toCharArray();
    assertEquals("MUCHLONGERCONTENT", b.wrap(arr, 0, arr.length).toString());
  }

  @Test
  void testWrap_EmptyBuilder_ExternalArray() {
    TextBuilder b = new TextBuilder();
    char[] arr = "hello".toCharArray();
    assertEquals("hello", b.wrap(arr, 0, 5).toString());
    assertEquals("", b.wrap(arr, 5, 0).toString());
  }

  @Test
  void testWrap_ZeroLengthSlice2() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    char[] arr = new char[10];
    assertEquals("", b.wrap(arr, 0, 0).toString());
    assertEquals("", b.wrap(arr, 2, 0).toString());
    assertEquals("", b.wrap(arr, 10, 0).toString());
  }

  @Test
  void testWrap_NegativeOffset_Throws() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    char[] arr = new char[10];
    assertThrows(IndexOutOfBoundsException.class, () -> b.wrap(arr, -1, 3));
  }

  @Test
  void testWrap_NegativeLength_Throws() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    char[] arr = new char[10];
    assertThrows(IndexOutOfBoundsException.class, () -> b.wrap(arr, 0, -1));
  }

  @Test
  void testWrap_EndBeyondArray_Throws() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    char[] arr = new char[5];
    assertThrows(IndexOutOfBoundsException.class, () -> b.wrap(arr, 0, 6));
    assertThrows(IndexOutOfBoundsException.class, () -> b.wrap(arr, 3, 3));
    assertThrows(IndexOutOfBoundsException.class, () -> b.wrap(arr, 6, 0));
  }

  @Test
  void testWrap_ReturnsViewOfPassedBuffer2() {
    // the whole point of wrap: no defensive copy, the returned Text is a view
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    char[] arr = "AAAAA".toCharArray();
    Text t = b.wrap(arr, 0, 5);
    assertEquals("AAAAA", t.toString());
    arr[0] = 'X';
    assertEquals("XAAAA", t.toString());
  }

  @Test
  void testSubSequence_FromZero_OnBuilder2() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    assertEquals("hel", b.subSequence(0, 3).toString());
    assertEquals("", b.subSequence(0, 0).toString());
    assertEquals("hello", b.subSequence(0, 5).toString());
  }

  @Test
  void testSlice_FromZero_OnBuilder() {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello world");
    assertEquals("hell", b.slice(0, 4).toString());
    assertEquals("hello worl", b.slice(0, -1).toString());
  }

  @Test
  void testConcat_OnBuilder2() {
    // regression: wrap used to reject the freshly-copied oversized array
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");
    assertEquals("hello world", b.concat(" world").toString());
    assertEquals("hello!", b.concat("!").toString());
  }

  @Test
  void testConcat_OnEmptyBuilder() {
    TextBuilder b = new TextBuilder();
    assertEquals("hello", b.concat("hello").toString());
  }

  @Test
  void testWrap_InternalBufferPastContent_Throws() throws Exception {
    TextBuilder b = new TextBuilder();
    b.appendAscii("hello");           // capacity >= 32
    var f = TextBuilder.class.getDeclaredField("buffer");
    f.setAccessible(true);
    char[] internal = (char[]) f.get(b);

    // offset at content length -> throws (would expose unwritten tail)
    assertThrows(IndexOutOfBoundsException.class,
        () -> b.wrap(internal, b.length(), 0));
    // offset + length past content -> throws
    assertThrows(IndexOutOfBoundsException.class,
        () -> b.wrap(internal, 3, 3));

    // within content -> OK
    assertEquals("ell", b.wrap(internal, 1, 3).toString());
  }

  /*
  asserts
   */

  private static void assertAppendEquals(Consumer<StringBuilder> expected, Consumer<Appender> actual) {
    StringBuilder e = new StringBuilder();
    expected.accept(e);

    TextBuilder a = new TextBuilder();
    actual.accept(a);
    assertEquals(e.toString(), a.toString(), "StringBuilder != TextBuilder");
    assertEquals(e.length(), a.length());
  }

  private static void assertJsonEscaped(String expected, CharSequence input) {
    TextBuilder txt = new TextBuilder();
    txt.appendJsonEscaped(input);
    assertEquals(expected, txt.toString());
  }

  private static void assertAppendThrows(
      Class<? extends Throwable> expectedType,
      Consumer<StringBuilder> expected,
      Consumer<Appender> actual) {
    StringBuilder e = new StringBuilder();
    assertThrows(expectedType, () -> expected.accept(e));

    TextBuilder a = new TextBuilder();
    assertThrows(expectedType, () -> actual.accept(a));
  }
}