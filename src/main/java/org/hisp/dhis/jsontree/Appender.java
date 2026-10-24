package org.hisp.dhis.jsontree;

import static java.lang.Character.highSurrogate;
import static java.lang.Character.isBmpCodePoint;
import static java.lang.Character.lowSurrogate;
import static java.util.Objects.requireNonNull;

import org.hisp.dhis.jsontree.internal.NotNull;

import java.io.OutputStream;

/**
 * A text sink API that in its core has the same idea as {@link Appendable} but
 * the {@link Appender} API is dedicated to support optimal writing of JSON.
 * To that end the caller can distinguish when ASCII character(s) are written
 * or character(s) or unknown nature that might require JSON escaping and/or
 * text encoding in the target encoding set in the sink represented by the API.
 *
 * @author Jan Bernitt
 * @since 1.9 (revised in 1.12)
 */
public interface Appender extends AutoCloseable {

  static Appender ofUTF8(OutputStream out) {
    return new Utf8OutputStreamAppender(out);
  }

  static TextBuilder of(int capacity) {
    return new TextBuilder(capacity);
  }

  void appendAscii(char c);
  default void appendAscii(CharSequence text) {
    appendAscii(text, 0, text == null ? 4 : text.length());
  }

  void appendAscii(CharSequence text, int start, int end);

  void appendEncoded(char c);
  default void appendEncoded(CharSequence text) {
      appendEncoded(text, 0, text == null ? 4 : text.length());
  }
  void appendEncoded(CharSequence text, int start, int end);

  @Override
  default void close() {}     // no checked exception, char sinks are no-ops

  void appendJsonEscaped(CharSequence jsonString);

  void appendCodePoint(int cp);

  default void appendInt(int value) {
    if (value < 0) {
      appendAscii('-');
      if (value == Integer.MIN_VALUE) {
        appendAscii("2147483648");       // |MIN_VALUE| overflows int
        return;
      }
      value = -value;
    }
    if (value < 10) {
      appendAscii((char) ('0' + value));
      return;
    }
    int div = 1_000_000_000;                 // 10^9 covers every non-negative int
    while (value < div) div /= 10;
    while (div > 0) {
      int digit = value / div;
      appendAscii((char) ('0' + digit));
      value -= digit * div;
      div /= 10;
    }
  }

  default void appendLong(long value) {
    if (value < 0) {
      appendAscii('-');
      if (value == Long.MIN_VALUE) {
        appendAscii("9223372036854775808");  // |MIN_VALUE| overflows long
        return;
      }
      value = -value;
    }
    if (value < 10) {
      appendAscii((char) ('0' + value));
      return;
    }
    long div = 1_000_000_000_000_000_000L;   // 10^18 covers every non-negative long
    while (value < div) div /= 10;
    while (div > 0) {
      int digit = (int) (value / div);
      appendAscii((char) ('0' + digit));
      value -= (long) digit * div;
      div /= 10;
    }
  }

  default void appendDouble(double value) {
    if (Double.isNaN(value)) {
      appendAscii("NaN");
    } else if (value == Double.POSITIVE_INFINITY) {
      appendAscii("Infinity");
    } else if (value == Double.NEGATIVE_INFINITY) {
      appendAscii("-Infinity");
    } else if (value == 0d) {
      appendAscii(Double.compare(value, -0d) == 0 ? "-0.0" : "0.0");
    } else if (value == Math.floor(value) && Math.abs(value) < 1e7) {
      // integral value that JDK would also print in decimal form
      appendLong((long) value);
      appendAscii(".0");
    } else {
      // let the JDK produce the shortest round-trip representation
      appendAscii(String.valueOf(value));
    }
  }

  static boolean needsJsonEscaping(char c) {
    if (c < 0x20) return true;                     // controls — all escape
    if (c < 0x7F) return c == '"' || c == '\\';    // printable ASCII
    return c == 0x2028 || c == 0x2029;             // escape for JS compatibility
  }

  static void checkRange(CharSequence text, int start, int end) {
    if (start < 0 || end < start || text.length() < end)
      throw new IndexOutOfBoundsException(
          "0 <= start %d <= end %d <= length %d".formatted(start, end, text.length()));
  }

  static void checkCodePoint(int cp) {
    if (!Character.isValidCodePoint(cp)) {
      throw new IllegalArgumentException(
          "Not a valid Unicode code point: 0x" + Integer.toHexString(cp));
    }
  }
}
