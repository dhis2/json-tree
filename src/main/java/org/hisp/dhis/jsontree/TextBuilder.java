package org.hisp.dhis.jsontree;

import static java.lang.Math.abs;
import static java.util.Objects.requireNonNull;

import java.util.Arrays;
import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * Similar to a {@link StringBuilder} except that it is an append-only API to build {@link Text}s.
 *
 * @apiNote Because the cursor position can only move forward it is always safe to expose sections
 *     that are already written as {@link Text}-slices sharing a section of the written {@link
 *     #buffer}.
 * @implNote The {@link TextBuilder} deliberately does not implement {@link Text} as it is a mutable
 *     value, whereas {@link Text} requires immutability. Instead, it implements {@link
 *     CharSequence} and {@link #slice(int, int)} to get immutable {@link Text} views on the current
 *     buffer that will not change even if the buffer is mutated later.
 * @author Jan Bernitt
 * @since 1.9
 */
public final class TextBuilder implements Appender, Textual, CharSequence {

  private char[] buffer;
  private int length = 0;

  public TextBuilder() {
    this(16);
  }

  public TextBuilder(int capacity) {
    this.buffer = new char[Math.max(16, ceilingPowerOfTwo(capacity))];
  }

  private static int ceilingPowerOfTwo(int x) {
    if (x <= 0) return 16;
    int highestBit = Integer.highestOneBit(x);
    return (x == highestBit) ? highestBit : highestBit << 1;
  }

  /*
  Terminal Operations API
   */

  public int capacity() {
    return buffer.length;
  }

  @Override
  public int length() {
    return length;
  }

  @Override
  public char charAt(int index) {
    return buffer[index];
  }

  @Override
  public @NotNull Text subSequence(int start, int end) {
    return slice(start, end - start);
  }

  public Text slice(int offset, int length) {
    return Text.of(buffer, offset, length);
  }

  public Text slice(int offset) {
    return slice(offset, length - offset);
  }

  @Override
  public @NotNull Text textValue() {
    return Text.of(buffer,0, length);
  }

  @Override
  public @NotNull String toString() {
    return new String(buffer, 0, length);
  }

  @Override
  public boolean equals(Object obj) {
    return  obj instanceof TextBuilder other && length == other.length && textValue().equals(other.textValue());
  }

  @Override
  public int hashCode() {
    return textValue().hashCode();
  }

  /*
  Builder API
   */

  private void ensureCapacity(int more) {
    if (length + more >= buffer.length) {
      int capacity = buffer.length;
      // 1.5 grow factor
      while (capacity <= length + more + 16) capacity += capacity / 2;
      buffer = Arrays.copyOf(buffer, capacity);
    }
  }

  @Override
  public TextBuilder append(CharSequence text) {
    return append(text, 0, text == null ? 4 : text.length());
  }

  @Override
  public TextBuilder append(CharSequence text, int start, int end) {
    if (text == null) text = "null";
    if (start < 0 || end < start || text.length() < end)
      throw new IndexOutOfBoundsException(
          "0 <= start %d <= end %d <= length %d".formatted(start, end, text.length()));
    if (text.isEmpty()) return this;
    int len = end - start;
    if (len == 0) return this;
    ensureCapacity(len);
    for (int i = 0; i < len; i++) buffer[length++] = text.charAt(start + i);
    return this;
  }

  @Override
  public TextBuilder append(char[] chars) {
    return append(chars, 0, chars.length);
  }

  @Override
  public TextBuilder append(char[] chars, int offset, int len) {
    requireNonNull(chars);
    if (offset < 0 || offset+len > chars.length)
      throw new IndexOutOfBoundsException(
          "0 <= offset %d <= offset+len %d <= length %d".formatted(offset, offset+len, chars.length));
    if (len == 0) return this;
    ensureCapacity(len);
    System.arraycopy(chars, offset, buffer, length, len);
    length+=len;
    return this;
  }

  @Override
  public TextBuilder append(char c) {
    ensureCapacity(1);
    buffer[length++] = c;
    return this;
  }

  @Override
  public TextBuilder append(int value) {
    int len = characterCount(value);
    ensureCapacity(len);
    char[] buf = buffer;
    appendInt(value, buf, length, len);
    length += len;
    return this;
  }

  @Override
  public TextBuilder append(long value) {
    int len = characterCount(value);
    ensureCapacity(len);
    char[] buf = buffer;
    appendLong(value, buf, length, len);
    length += len;
    return this;
  }

  @Override
  public TextBuilder append(double value) {
    if (Double.isNaN(value)) return append("NaN");
    if (value == Double.POSITIVE_INFINITY) return append("Infinity");
    if (value == Double.NEGATIVE_INFINITY) return append("-Infinity");
    if (value == 0d) return Double.compare(value, -0d) == 0 ? append("-0.0") : append("0.0");
    if (isDecimalNotation(value)) {
      if (value % 1d == 0) return append((long) value).append(".0");
      double scaled = value * 10d;
      if (scaled % 1d == 0) {
        if (scaled / 10d == value) {
          long val10x = (long) scaled;
          return append(val10x/10).appendDecimal(abs(val10x)%10);
        }
      } else {
        scaled = value * 100d;
        if (scaled % 1d == 0) {
          if (scaled / 100d == value) {
            long val100x = (long) scaled;
            return append(val100x/100).appendDecimal(abs(val100x)%100);
          }
        } else {
          scaled = value * 1000d;
          if (scaled % 1d == 0 && scaled / 1000d == value) {
            long val1000x = (long) scaled;
            return append(val1000x/1000).appendDecimal(abs(val1000x)%1000);
          }
        }
      }
    }
    return append(String.valueOf(value));
  }

  private TextBuilder appendDecimal(long value) {
    while (value > 10 && value % 10 == 0) value /= 10;
    return append('.').append(value);
  }

  /**
   * Would the number print in decimal notation?
   */
  private static boolean isDecimalNotation(double value) {
    double abs = abs(value);
    return abs >= 1e-3 && abs < 1e7; // JLS Rule: decimal if 10^-3 ≤ abs < 10^7
  }

  @Override
  public TextBuilder appendCodePoint(int cp) {
    ensureCapacity(2);
    Appender.super.appendCodePoint(cp);
    return this;
  }

  static void appendInt(int value, char[] buffer, int offset, int length) {
    boolean neg = value < 0;
    boolean overflow = value == Integer.MIN_VALUE;
    value = overflow ? value - 1 : abs(value);
    int digit0 = neg ? 1 : 0;
    if (neg) buffer[offset] = '-';
    int rest = value;
    for (int i = length - 1; i >= digit0; i--) {
      buffer[offset + i] = (char) ('0' + (rest % 10));
      rest /= 10;
    }
    if (overflow) buffer[offset + length - 1] += 1;
  }

  static void appendLong(long value, char[] buffer, int offset, int length) {
    boolean neg = value < 0;
    boolean overflow = value == Long.MIN_VALUE;
    value = overflow ? value - 1L : abs(value);
    int digit0 = neg ? 1 : 0;
    if (neg) buffer[offset] = '-';
    long rest = value;
    for (int i = length - 1; i >= digit0; i--) {
      buffer[offset + i] = (char) ('0' + (rest % 10));
      rest /= 10;
    }
    if (overflow) buffer[offset + length - 1] += 1;
  }

  static int characterCount(int value) {
    if (value == 0) return 1;
    boolean overflow = value == Integer.MIN_VALUE;
    int rest = overflow ? value - 1 : abs(value);
    int n = 0;
    while (rest > 0) {
      n++;
      rest /= 10;
    }
    return value < 0 ? n+1 : n;
  }

  static int characterCount(long value) {
    if (value == 0L) return 1;
    boolean overflow = value == Long.MIN_VALUE;
    long rest = overflow ? value - 1L : abs(value);
    int n = 0;
    while (rest > 0) {
      n++;
      rest /= 10;
    }
    return value < 0 ? n+1 : n;
  }
}
