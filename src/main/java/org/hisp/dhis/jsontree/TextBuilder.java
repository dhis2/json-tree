package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.NotNull;

import java.util.Arrays;

import static java.lang.Character.highSurrogate;
import static java.lang.Character.isBmpCodePoint;
import static java.lang.Character.lowSurrogate;

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
public final class TextBuilder implements Appendable, CharSequence {

  private char[] buffer;
  private int length = 0;

  public TextBuilder() {
    this(16);
  }

  public TextBuilder(int capacity) {
    this.buffer = new char[Math.max(16, ceilingPowerOfTwo(capacity))];
  }

  private static int ceilingPowerOfTwo(int x) {
    if (x <= 0) throw new IllegalArgumentException("capacity must be > 0");
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

  public Text text() {
    return Text.of(buffer,0, length);
  }

  @Override
  public @NotNull String toString() {
    return new String(buffer, 0, length);
  }

  @Override
  public boolean equals(Object obj) {
    return  obj instanceof TextBuilder other && length == other.length && text().equals(other.text());
  }

  @Override
  public int hashCode() {
    return text().hashCode();
  }

  /*
  Builder API
   */

  private void ensureCapacity(int more) {
    if (length + more >= buffer.length) {
      int capacity = length * 2;
      while (capacity <= length + more + 16) capacity *= 2;
      buffer = Arrays.copyOf(buffer, capacity);
    }
  }

  @Override
  public TextBuilder append(CharSequence text) {
    return append(text, 0, text.length());
  }

  @Override
  public TextBuilder append(CharSequence text, int start, int end) {
    if (text.isEmpty()) return this;
    int len = end - start;
    ensureCapacity(len);
    for (int i = 0; i < len; i++) buffer[length++] = text.charAt(start + i);
    return this;
  }

  @Override
  public TextBuilder append(char c) {
    ensureCapacity(1);
    buffer[length++] = c;
    return this;
  }

  public TextBuilder append(int value) {
    int len = characterCount(value);
    ensureCapacity(len);
    char[] buf = buffer;
    appendInt(value, buf, length, len);
    length += len;
    return this;
  }

  public TextBuilder append(long value) {
    int len = characterCount(value);
    ensureCapacity(len);
    char[] buf = buffer;
    appendLong(value, buf, length, len);
    length += len;
    return this;
  }

  public TextBuilder append(double value) {
    if (Double.isNaN(value)) return append("NaN");
    if (value == Double.POSITIVE_INFINITY) return append("Infinity");
    if (value == Double.NEGATIVE_INFINITY) return append("-Infinity");
    if (Double.compare(value, -0d) == 0) return append("-0.0");
    if (value % 1 == 0) return append((long) value);
    return append(String.valueOf(value));
  }

  public TextBuilder appendCodePoint(int cp) {
    ensureCapacity(2);
    if (isBmpCodePoint(cp)) {
      buffer[length++] = (char) cp;
    } else {
      buffer[length++] = highSurrogate(cp);
      buffer[length++] = lowSurrogate(cp);
    }
    return this;
  }

  static void appendInt(int value, char[] buffer, int offset, int length) {
    boolean neg = value < 0;
    boolean overflow = value == Integer.MIN_VALUE;
    value = overflow ? value - 1 : Math.abs(value);
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
    value = overflow ? value - 1L : Math.abs(value);
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
    boolean overflow = value == Integer.MIN_VALUE;
    int rest = overflow ? value - 1 : Math.abs(value);
    int n = 0;
    while (rest > 0) {
      n++;
      rest /= 10;
    }
    return value < 0 ? n+1 : n;
  }

  static int characterCount(long value) {
    boolean overflow = value == Long.MIN_VALUE;
    long rest = overflow ? value - 1L : Math.abs(value);
    int n = 0;
    while (rest > 0) {
      n++;
      rest /= 10;
    }
    return value < 0 ? n+1 : n;
  }
}
