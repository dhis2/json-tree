package org.hisp.dhis.jsontree;

import static java.lang.Character.highSurrogate;
import static java.lang.Character.isBmpCodePoint;
import static java.lang.Character.lowSurrogate;
import static java.lang.Math.abs;
import static java.util.Objects.requireNonNull;
import static org.hisp.dhis.jsontree.Appender.checkCodePoint;
import static org.hisp.dhis.jsontree.Appender.checkRange;
import static org.hisp.dhis.jsontree.Appender.needsJsonEscaping;

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
  public void appendAscii(char c) {
    appendEncoded(c);
  }

  @Override
  public void appendAscii(CharSequence text, int start, int end) {
    appendEncoded(text, start, end);
  }

  @Override
  public void appendEncoded(CharSequence text, int start, int end) {
    if (text == null) text = "null";
    checkRange(text, start, end);
    if (text.isEmpty()) return;
    int len = end - start;
    if (len == 0) return;
    ensureCapacity(len);
    appendSlice(text, start, len);
  }

  @Override
  public void appendEncoded(char c) {
    ensureCapacity(1);
    buffer[length++] = c;
  }

  @Override
  public void appendCodePoint(int cp) {
    checkCodePoint(cp);
    if (isBmpCodePoint(cp)) {
      appendEncoded((char) cp);
    } else {
      appendEncoded(highSurrogate(cp));
      appendEncoded(lowSurrogate(cp));
    }
  }

  @Override
  public void appendJsonEscaped(CharSequence value) {
    if (value == null) { appendAscii("null"); return; }
    int len = value.length();
    int i = 0;
    while (i < len && !needsJsonEscaping(value.charAt(i))) i++;
    if (i == len) {                              // fast path: no escapes
      ensureCapacity(len + 2);
      buffer[length++] = '"';
      appendSlice(value, 0, len);
      buffer[length++] = '"';
      return;
    }
    ensureCapacity(1);
    buffer[length++] = '"';
    int start = 0;
    while (i < len) {
      appendAscii(value, start, i);
      while (i < len && needsJsonEscaping(value.charAt(i))) {
        writeEscapeToBuffer(value.charAt(i++));
      }
      start = i;
      while (i < len && !needsJsonEscaping(value.charAt(i))) i++;
    }
    appendAscii(value, start, len);
    ensureCapacity(1);
    buffer[length++] = '"';
  }

  private void appendSlice(CharSequence value, int start, int len) {
    if (len > 16 && value instanceof Text t) {
      t.copyTo(buffer, length, start, len);
      length += len;
    } else {
      for (int j = 0; j < len; j++)
        buffer[length++] = value.charAt(start + j);
    }
  }

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();
  private void writeEscapeToBuffer(char c) {
    ensureCapacity(6);                           // worst case /uXXXX
    switch (c) {
      case '"'    -> { buffer[length++] = '\\'; buffer[length++] = '"';  }
      case '\\'   -> { buffer[length++] = '\\'; buffer[length++] = '\\'; }
      case '\n'   -> { buffer[length++] = '\\'; buffer[length++] = 'n';  }
      case '\r'   -> { buffer[length++] = '\\'; buffer[length++] = 'r';  }
      case '\t'   -> { buffer[length++] = '\\'; buffer[length++] = 't';  }
      case '\b'   -> { buffer[length++] = '\\'; buffer[length++] = 'b';  }
      case '\f'   -> { buffer[length++] = '\\'; buffer[length++] = 'f';  }
      default -> {
        buffer[length++] = '\\';
        buffer[length++] = 'u';
        buffer[length++] = HEX[(c >> 12) & 0xF];
        buffer[length++] = HEX[(c >>  8) & 0xF];
        buffer[length++] = HEX[(c >>  4) & 0xF];
        buffer[length++] = HEX[ c        & 0xF];
      }
    }
  }
}
