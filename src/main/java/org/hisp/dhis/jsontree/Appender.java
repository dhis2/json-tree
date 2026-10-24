package org.hisp.dhis.jsontree;

import java.io.OutputStream;

/**
 * An append-only text sink used to build or stream JSON.
 *
 * <p>Unlike {@link Appendable}, which offers a single character-oriented surface, {@link Appender}
 * distinguishes three kinds of content because JSON generation has three distinct write patterns
 * with different optimal implementations in each sink:
 *
 * <ul>
 *   <li><b>ASCII content</b> — JSON structure ({@code { } [ ] , : "}), literals ({@code true}
 *       {@code false} {@code null}), numbers, and JSON escape sequences. All of this is pure
 *       ASCII, so a sink can copy it byte-for-byte on an ASCII-compatible target (such as UTF-8
 *       or any ISO-8859-* charset) without involving a charset encoder. See {@link
 *       #appendAscii(char)} and {@link #appendAscii(CharSequence, int, int)}.</li>
 *   <li><b>General text content</b> — user-supplied string values, member names, or any other
 *       text whose characters may need charset encoding. See {@link #appendEncoded(char)} and
 *       {@link #appendEncoded(CharSequence, int, int)}.</li>
 *   <li><b>JSON string literals</b> — content that must be JSON-escaped, charset-encoded, and
 *       wrapped in double quotes. See {@link #appendJsonEscaped(CharSequence)}.</li>
 * </ul>
 *
 * <p>Splitting these three cases lets each sink pick the cheapest correct implementation for
 * each. A character-oriented sink such as {@link TextBuilder} writes all three directly into its
 * {@code char[]}; a byte-oriented sink such as {@link Utf8OutputStreamAppender} writes ASCII
 * bytes directly, encodes non-ASCII as UTF-8 into the same buffer, and writes escape sequences
 * as ASCII bytes. Neither path allocates per character and neither delegates charset encoding to
 * a {@link java.io.Writer}.
 *
 * <h2>Null handling</h2>
 *
 * <p>Where a {@link CharSequence} argument is {@code null}, the interface behaves like {@link
 * StringBuilder#append(CharSequence)}: {@link #appendAscii(CharSequence)} and {@link
 * #appendEncoded(CharSequence)} write the literal string {@code "null"}. The three-argument
 * variants convert {@code null} to {@code "null"} <em>before</em> the range check is applied, so
 * {@code appendEncoded(null, 0, 0)} writes nothing and {@code appendEncoded(null, 0, 5)} throws
 * {@link IndexOutOfBoundsException}.
 *
 * <p>{@link #appendJsonEscaped(CharSequence)} is different: it treats {@code null} as the JSON
 * value {@code null} — the four ASCII characters {@code n u l l}, <em>without</em> quotes —
 * matching the semantics of {@link JsonBuilder#createString(CharSequence)} returning {@link
 * JsonNode#NULL} for a {@code null} argument.
 *
 * <h2>Surrogate handling</h2>
 *
 * <p>Unpaired surrogates behave differently across sinks because the correct treatment depends
 * on the target representation:
 *
 * <ul>
 *   <li>{@link TextBuilder} is a {@code char}-oriented sink and preserves surrogates as-is. A
 *       surrogate pair passed inside a single span is kept unchanged, and a lone surrogate
 *       remains a lone {@code char} in the output. This matches {@link
 *       StringBuilder#appendCodePoint(int)} and {@link StringBuilder#append(char)}.</li>
 *   <li>{@link Utf8OutputStreamAppender} is a byte-oriented sink and cannot encode a lone
 *       surrogate as UTF-8. It replaces them with the Unicode replacement character {@code
 *       U+FFFD}, matching the {@link java.nio.charset.CodingErrorAction#REPLACE} policy of
 *       {@link java.io.OutputStreamWriter}.</li>
 * </ul>
 *
 * <p>Callers that want a specific behaviour for supplementary codepoints should use {@link
 * #appendCodePoint(int)}, which encodes the codepoint directly and does not rely on how a sink
 * treats split surrogate pairs.
 *
 * <h2>Thread safety</h2>
 *
 * <p>{@code Appender} instances are not thread-safe. Each instance should be used from a single
 * thread. Since the interface extends {@link AutoCloseable}, instances can be used in a
 * try-with-resources block; {@link #close()} does not throw a checked exception.
 *
 * @author Jan Bernitt
 * @since 1.9 (revised in 1.12)
 * @see Appender#ofUTF8(OutputStream)
 * @see Appender#of(int)
 * @see TextBuilder
 * @see Utf8OutputStreamAppender
 */
public interface Appender extends AutoCloseable {

  /**
   * Creates a UTF-8 encoding {@link Appender} that writes to the given {@link OutputStream}.
   *
   * <p>The returned appender owns the stream: {@link #close()} flushes pending bytes and closes the
   * stream.
   *
   * @param out the target stream
   * @return a new UTF-8 appender that writes to {@code out}
   */
  static Appender ofUTF8(OutputStream out) {
    return new Utf8OutputStreamAppender(out);
  }

  /**
   * Creates a new {@link TextBuilder} with the given initial capacity.
   *
   * <p>The returned builder is a character-oriented {@link Appender} that stores all writes in
   * an internal {@code char[]} and exposes the result as a {@link Text} via {@link
   * TextBuilder#textValue()} or as a {@link String} via {@link TextBuilder#toString()}.
   *
   * @param capacity initial buffer capacity in characters
   * @return a new {@code TextBuilder}
   */
  static TextBuilder of(int capacity) {
    return new TextBuilder(capacity);
  }

  /**
   * Writes a single ASCII character.
   *
   * <p>The caller guarantees that {@code c} is in the ASCII range {@code 0x00..0x7F}. Sinks may
   * write it byte-for-byte to an ASCII-compatible target without involving a charset encoder.
   *
   * @param c the ASCII character to write
   */
  void appendAscii(char c);

  /**
   * Writes a span of an {@link CharSequence} that consists entirely of ASCII characters.
   *
   * <p>The caller guarantees that every character in {@code [start, end)} is in the ASCII range.
   * Sinks may copy the span byte-for-byte to an ASCII-compatible target without involving a
   * charset encoder.
   *
   * <p>When {@code text} is {@code null} it is treated as the literal string {@code "null"}
   * before the range check is applied — see the class-level null handling notes.
   *
   * @param text the source, may be {@code null}
   * @param start first index (inclusive) to write
   * @param end end index (exclusive) to write
   * @throws IndexOutOfBoundsException if the range is not valid for {@code text} (after null
   *     substitution)
   */
  void appendAscii(CharSequence text, int start, int end);

  /**
   * Convenience method for {@link #appendAscii(CharSequence, int, int)} covering the entire
   * sequence.
   *
   * @param text the source, may be {@code null}
   */
  default void appendAscii(CharSequence text) {
    appendAscii(text, 0, text == null ? 4 : text.length());
  }

  /**
   * Writes a single character using the sink's target encoding.
   *
   * <p>Contrast with {@link #appendAscii(char)}: this method does not assume the character is
   * ASCII, so a byte sink will encode it to bytes and a character sink will write it
   * unchanged. For characters in the ASCII range the two methods produce identical output.
   *
   * @param c the character to write
   */
  void appendEncoded(char c);

  /**
   * Writes a span of an {@link CharSequence} using the sink's target encoding.
   *
   * <p>Surrogate pairs that fall entirely within {@code [start, end)} are treated as a single
   * codepoint by sinks that encode to bytes. A high surrogate at the last index of the span, or
   * a high surrogate not followed by a low surrogate, is not combined with anything outside the
   * span — see the class-level surrogate handling notes.
   *
   * <p>When {@code text} is {@code null} it is treated as the literal string {@code "null"}
   * before the range check is applied.
   *
   * @param text the source, may be {@code null}
   * @param start first index (inclusive) to write
   * @param end end index (exclusive) to write
   * @throws IndexOutOfBoundsException if the range is not valid for {@code text} (after null
   *     substitution)
   */
  void appendEncoded(CharSequence text, int start, int end);

  /**
   * Convenience method for {@link #appendEncoded(CharSequence, int, int)} covering the entire
   * sequence.
   *
   * @param text the source, may be {@code null}
   */
  default void appendEncoded(CharSequence text) {
    appendEncoded(text, 0, text == null ? 4 : text.length());
  }

  /**
   * Releases any resources held by this sink.
   *
   * <p>The default implementation is a no-op, appropriate for sinks that hold only in-memory
   * state such as {@link TextBuilder}. Sinks that own an external resource — for example a
   * {@link java.io.OutputStream} — must override this method to flush pending bytes and release
   * the resource.
   *
   * <p>Unlike {@link AutoCloseable#close()} this method does not declare a checked exception.
   * Sinks that wrap I/O are expected to translate failures into {@link
   * java.io.UncheckedIOException}.
   */
  @Override
  default void close() {}

  /**
   * Writes the given value as a JSON string literal.
   *
   * <p>This method writes the surrounding double quotes itself; the caller must not write them
   * separately. The content between the quotes is JSON-escaped according to the policy returned
   * by {@link #needsJsonEscaping(char)} and then charset-encoded by the sink:
   *
   * <pre>{@code
   * appender.appendJsonEscaped("hello");  // writes: "hello"
   * appender.appendJsonEscaped("a\"b");   // writes: "a\"b"
   * appender.appendJsonEscaped("a\nb");   // writes: "a\nb"    (backslash-n, two chars)
   * appender.appendJsonEscaped("©");      // writes: "©"       (encoded per sink charset)
   * }</pre>
   *
   * <p>When {@code value} is {@code null} this method writes the JSON literal {@code null} —
   * unquoted — which is different from writing the string {@code "null"}. This matches the
   * semantics of {@link JsonBuilder#createString(CharSequence)}: a {@code null} Java string
   * becomes a JSON {@code null} node, not a JSON string node.
   *
   * <p>Implementations are expected to scan for clean spans and fall back to per-character
   * escape handling only for the escapable characters, so that values without any escapable
   * content are written in a single bulk encoding step. No intermediate {@code char[]}, {@code
   * StringBuilder}, or {@code String} should be allocated.
   *
   * @param value the value to write as a JSON string, may be {@code null}
   */
  void appendJsonEscaped(CharSequence value);

  /**
   * Writes a single Unicode codepoint using the sink's target encoding.
   *
   * <p>The input must be a valid Unicode codepoint value in the range {@code 0x0..0x10FFFF}.
   * Surrogate codepoints ({@code 0xD800..0xDFFF}) are valid codepoint <em>values</em> but are
   * not valid Unicode scalar values; different sinks handle them differently — see the
   * class-level surrogate handling notes.
   *
   * <p>This method is the recommended way to write supplementary-plane characters (codepoints
   * above {@code 0xFFFF}). Writing the two surrogate halves separately through {@link
   * #appendEncoded(char)} may produce {@code U+FFFD U+FFFD} on byte sinks.
   *
   * @param cp the codepoint to write
   * @throws IllegalArgumentException if {@code cp} is not a valid Unicode codepoint value
   */
  void appendCodePoint(int cp);

  /**
   * Writes the decimal representation of the given {@code int}.
   *
   * <p>The output is unquoted, containing only ASCII digits and — for negative values — a
   * leading {@code -}. It matches {@link Integer#toString(int)} for all inputs, including
   * {@link Integer#MIN_VALUE}.
   *
   * @param value the value to write
   */
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

  /**
   * Writes the decimal representation of the given {@code long}.
   *
   * <p>The output is unquoted, containing only ASCII digits and — for negative values — a
   * leading {@code -}. It matches {@link Long#toString(long)} for all inputs, including {@link
   * Long#MIN_VALUE}.
   *
   * @param value the value to write
   */
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

  /**
   * Writes the textual representation of the given {@code double}.
   *
   * <p>The output is unquoted. Finite values whose magnitude is below {@code 1e7} and which have
   * no fractional part are written as their integral form followed by {@code .0}; all other
   * finite values use {@link Double#toString(double)} and are written verbatim. {@link
   * Double#NaN}, {@link Double#POSITIVE_INFINITY}, and {@link Double#NEGATIVE_INFINITY} are
   * written as the ASCII sequences {@code NaN}, {@code Infinity}, and {@code -Infinity}.
   *
   * <p>None of these forms is a valid JSON number in the strict sense except the decimal and
   * integral variants. The caller is responsible for wrapping the output in JSON quotes when
   * needed — see {@link JsonBuilder#requiresString(double)}.
   *
   * @param value the value to write
   */
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

  /**
   * The JSON escaping policy used by {@link #appendJsonEscaped(CharSequence)}.
   *
   * <p>A character needs to be escaped inside a JSON string if it is:
   *
   * <ul>
   *   <li>a control character ({@code < 0x20}),</li>
   *   <li>a double quote ({@code "}) or a backslash ({@code \}), or</li>
   *   <li>one of the JavaScript line separators {@code U+2028} and {@code U+2029}. These are
   *       not required by strict JSON but are escaped for compatibility with JSON embedded in
   *       {@code <script>} tags and with JavaScript parsers that treat them as line terminators
   *       inside string literals.</li>
   * </ul>
   *
   * <p>The policy is defined here — not inside individual sinks — so that the escape rules
   * cannot drift between implementations. Every sink must use this method (directly or via
   * {@link #appendJsonEscaped(CharSequence)}).
   *
   * @param c the character to test
   * @return {@code true} if {@code c} must be escaped inside a JSON string literal
   */
  static boolean needsJsonEscaping(char c) {
    if (c < 0x20) return true;                     // controls — all escape
    if (c < 0x7F) return c == '"' || c == '\\';    // printable ASCII
    return c == 0x2028 || c == 0x2029;             // escape for JS compatibility
  }

  /**
   * Validates a range for a {@link CharSequence} argument.
   *
   * <p>Called by implementations of {@link #appendAscii(CharSequence, int, int)} and {@link
   * #appendEncoded(CharSequence, int, int)} after any null substitution, so that the range check
   * is consistent across sinks and produces a consistent error message.
   *
   * @param text the sequence to validate the range against (already null-substituted)
   * @param start first index (inclusive)
   * @param end end index (exclusive)
   * @throws IndexOutOfBoundsException if {@code start < 0}, {@code end < start}, or {@code
   *     text.length() < end}
   */
  static void checkRange(CharSequence text, int start, int end) {
    if (start < 0 || end < start || text.length() < end)
      throw new IndexOutOfBoundsException(
          "0 <= start %d <= end %d <= length %d".formatted(start, end, text.length()));
  }

  /**
   * Validates that the given value is a valid Unicode codepoint.
   *
   * <p>Called by implementations of {@link #appendCodePoint(int)}. Note that surrogate codepoints
   * ({@code 0xD800..0xDFFF}) pass this check — they are valid codepoint values even though they
   * are not valid Unicode scalar values.
   *
   * @param cp the value to validate
   * @throws IllegalArgumentException if {@code cp} is not in the range {@code 0x0..0x10FFFF}
   */
  static void checkCodePoint(int cp) {
    if (!Character.isValidCodePoint(cp)) {
      throw new IllegalArgumentException(
          "Not a valid Unicode code point: 0x" + Integer.toHexString(cp));
    }
  }
}