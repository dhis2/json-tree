package org.hisp.dhis.jsontree;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * A {@link String}-like API without requiring to manifest a {@link String} instance.
 *
 * @apiNote A {@link Text} must always be observed as if it is an immutable value. It is highly
 *     recommended to not extend the interface but instead use {@link Text#of(char[], int, int)} or
 *     on of the other {@code of}-methods to create instances.
 * @implNote The {@link Text} abstraction exists to enable views without defensive copying to allow
 *     for a very small memory overhead of sub-sequences found within a JSON document. Most
 *     prominent the JSON string node values and the names of object properties. This takes
 *     advantage of the fact that most such strings do not need JSON level decoding and can be
 *     direct views (slices) of the JSON document itself.
 * @author Jan Bernitt
 * @since 1.9
 */
public interface Text extends CharSequence, Comparable<Text> {

  Text EMPTY = Text.of(new char[0], 0, 0);

  /**
   * @see String#indexOf(int)
   */
  default int indexOf(char ch) {
    return indexOf(ch, 0);
  }

  /**
   * @see String#indexOf(int, int)
   */
  default int indexOf(char ch, int startIndex) {
    return indexOf(ch, startIndex, length());
  }

  default int indexOf(char ch, int startIndex, int endIndex) {
    for (int i = startIndex; i < endIndex; i++) if (charAt(i) == ch) return i;
    return -1;
  }

  /**
   * @see String#lastIndexOf(int)
   */
  default int lastIndexOf(char ch) {
    return lastIndexOf(ch, length() - 1);
  }

  /**
   * @see String#lastIndexOf(int, int)
   */
  default int lastIndexOf(char ch, int startIndex) {
    return lastIndexOf(ch, startIndex, 0);
  }

  default int lastIndexOf(char ch, int startIndex, int endIndex) {
    for (int i = startIndex; i >= endIndex; i--) if (charAt(i) == ch) return i;
    return -1;
  }

  default boolean contains(char ch) {
    return indexOf(ch) >= 0;
  }

  /**
   * @see String#contains(CharSequence)
   */
  default boolean contains(CharSequence infix) {
    return indexOf(infix) >= 0;
  }

  /**
   * @see String#indexOf(String)
   */
  default int indexOf(CharSequence infix) {
    return indexOf(infix, 0);
  }

  /**
   * @see String#indexOf(String, int)
   */
  default int indexOf(CharSequence infix, int startIndex) {
    return indexOf(infix, startIndex, length());
  }

  /**
   * @see String#indexOf(String, int, int)
   */
  default int indexOf(CharSequence infix, int startIndex, int endIndex) {
    if (startIndex < 0) return -1;
    if (infix.isEmpty()) return 0;
    // find + match
    int fLen = infix.length();
    int mLen = endIndex - startIndex;
    if (fLen > mLen) return -1;
    char f0 = infix.charAt(0);
    int m0 = indexOf(f0, startIndex, endIndex);
    while (m0 >= 0 && m0 + fLen <= endIndex) {
      if (regionMatches(m0, infix)) return m0;
      m0 = indexOf(f0, m0 + 1, endIndex);
    }
    return -1;
  }

  /**
   * @see String#lastIndexOf(String)
   */
  default int lastIndexOf(CharSequence infix) {
    return lastIndexOf(infix, length() - 1, 0);
  }

  /**
   * @see String#lastIndexOf(String, int)
   */
  default int lastIndexOf(CharSequence infix, int startIndex) {
    return lastIndexOf(infix, startIndex, 0);
  }

  default int lastIndexOf(CharSequence infix, int startIndex, int endIndex) {
    if (startIndex < 0) return -1;
    if (infix.isEmpty()) return 0;
    // find + match
    int fLen = infix.length();
    int mLen = startIndex - endIndex + 1;
    if (fLen > mLen) return -1;
    char f0 = infix.charAt(0);
    int m0 = lastIndexOf(f0, startIndex, endIndex);
    while (m0 >= endIndex) {
      if (regionMatches(m0, infix)) return m0;
      m0 = lastIndexOf(f0, m0 - 1, endIndex);
    }
    return -1;
  }

  /**
   * @see String#startsWith(String)
   */
  default boolean startsWith(CharSequence prefix) {
    return startsWith(prefix, 0);
  }

  /**
   * @see String#startsWith(String, int)
   */
  default boolean startsWith(CharSequence prefix, int startIndex) {
    return regionMatches(startIndex, prefix);
  }

  /**
   * @see String#endsWith(String)
   */
  default boolean endsWith(CharSequence suffix) {
    return regionMatches(length() - suffix.length(), suffix);
  }

  /**
   * @see String#contentEquals(CharSequence)
   */
  default boolean contentEquals(CharSequence text) {
    if (text.isEmpty()) return isEmpty();
    int len = text.length();
    if (len != length()) return false;
    for (int i = 0; i < len; i++) if (charAt(i) != text.charAt(i)) return false;
    return true;
  }

  default boolean regionMatches(int startIndex, CharSequence sample) {
    return regionMatches(startIndex, sample, 0, sample.length());
  }

  /**
   * @see String#regionMatches(int, String, int, int)
   */
  default boolean regionMatches(int startIndex, CharSequence sample, int offset, int len) {
    if (startIndex < 0
        || offset < 0
        || startIndex > length() - len
        || offset > sample.length() - len) return false;
    if (len <= 0) return true;
    for (int i = 0; i < len; i++)
      if (charAt(startIndex + i) != sample.charAt(offset + i)) return false;
    return true;
  }

  @Override
  default @NotNull Text subSequence(int start, int end) {
    checkSubSequence(start, end, length());
    if (start == 0 && end == length()) return this;
    int len = end - start;
    if (start == 0 && end == len) return this;
    char[] buffer = new char[len];
    for (int i = 0; i < len; i++) buffer[i] = charAt(start + i);
    return of(buffer, 0, len);
  }

  /**
   * @see String#toCharArray()
   */
  default char[] toCharArray() {
    char[] arr = new char[length()];
    for (int i = 0; i < arr.length; i++) arr[i] = charAt(i);
    return arr;
  }

  /**
   * @return true, if this text is a valid integer value (of any range), or in other words if the
   *     sequence only consists of digits except for the first character which may be a plus or
   *     minus sign.
   */
  default boolean isTextualInteger() {
    if (isEmpty()) return false;
    int i = 0;
    if (charAt(0) == '-' || charAt(0) == '+') i++;
    int len = length();
    for (; i < len; i++) if (charAt(i) < '0' || charAt(i) > '9') return false;
    return true;
  }

  /**
   * @return true, if this is either a {@link #isTextualInteger()} or a floating point number in
   *     decimal notation with all decimals being zero.
   */
  default boolean isNumericInteger() {
    return TextualNumber.isNumericInteger(toCharArray());
  }

  /**
   * @return true, if this text is a valid decimal value (of any range), in other words if it is
   *     either a {@link #isTextualInteger()} or a floating point number in decimal or exponential
   *     notation.
   */
  default boolean isTextualDecimal() {
    return TextualNumber.isTextualDecimal(toCharArray());
  }

  /**
   * In contrast to the JDK method this only accepts valid inputs.
   *
   * @throws IllegalArgumentException when text is neither "true" nor "false" (case-insensitive)
   * @see Boolean#parseBoolean(String)
   */
  default boolean parseBoolean() {
    return Chars.parseBoolean(toCharArray());
  }

  /**
   * @see Integer#parseInt(String)
   */
  default int parseInt() {
    return TextualNumber.parseIntExact(toCharArray(), 0, length());
  }

  /**
   * @see Long#parseLong(String)
   */
  default long parseLong() {
    return TextualNumber.parseLongExact(toCharArray(), 0, length());
  }

  /**
   * @see Double#parseDouble(String)
   */
  default double parseDouble() {
    return TextualNumber.parseDoubleCast(toCharArray());
  }

  default Number parseNumber() {
    return TextualNumber.of(toCharArray());
  }

  /**
   * @see String#compareTo(String)
   */
  @Override
  default int compareTo(Text other) {
    int n = Math.min(length(), other.length());
    for (int k = 0; k < n; k++) {
      int res = charAt(k) - other.charAt(k);
      if (res != 0) return res;
    }
    return length() - other.length();
  }

  /**
   * @implNote All classes implementing {@link Text} must implement {@code equals} using the
   *     equivalent of {@link #contentEquals(CharSequence)} for instances of {@link Text}, arguments
   *     not being {@link Text}s are not equal.
   */
  boolean equals(Object obj);

  /**
   * @implNote All classes implementing {@link Text} must implement {@code hashCode} using {@link
   *     Text#hashCode(Text)}.
   */
  int hashCode();

  /**
   * @param other another instance to compare to
   * @return true, if the characters of this and the given text are both backed by what the JVM
   *     considers the same "object" instance. This does not imply that the characters exposed by
   *     the two {@link Text} instances are observed as being equal. They only both prevent the GC
   *     of the same underlying storage instance. Or in other words, they share memory without being
   *     necessarily observed as equal.
   */
  default boolean contentMemoryEquals(@NotNull Text other) {
    return false;
  }

  static Text copyOf(@NotNull Text text) {
    return of(text.toCharArray(), 0, text.length());
  }

  /**
   * @param text the source to convert to a {@link Text}
   * @return the given text as {@link Text} (no copy if it can be avoided)
   * @see #copyOf(Text) to force a copy
   * @since 1.9
   */
  static Text of(@NotNull CharSequence text) {
    if (text instanceof Text t) return t;
    if (text instanceof String s) return of(s);
    int len = text.length();
    if (len == 0) return EMPTY;
    Text cached = Cache.get(text.charAt(0), len);
    if (cached != null && cached.contentEquals(text)) return cached;
    char[] buffer = new char[len];
    for (int i = 0; i < len; i++) buffer[i] = text.charAt(i);
    Text res = of(buffer, 0, len);
    Cache.set(res);
    return res;
  }

  /**
   * @return The string's characters as {@link Text} mainly for user space API uses, tests and such.
   *     Internally it should be avoided to use this whenever a {@link Text} can be received from
   *     the {@link JsonNode} API instead.
   */
  static Text of(String text) {
    if (text.isEmpty()) return EMPTY;
    Text cached = Cache.get(text.charAt(0), text.length());
    if (cached != null && cached.contentEquals(text)) return cached;
    char[] buffer = text.toCharArray();
    Text res = of(buffer, 0, buffer.length);
    Cache.set(res);
    return res;
  }

  /**
   * @apiNote This method will not make a defensive copy of the given buffer as the main use case is
   *     to use {@link Text} as a shallow "pointer" to slices on the same underlying #buffer. Only
   *     arrays that are not mutated any longer should be passed.
   * @since 1.9
   * @param buffer the characters viewed
   * @param offset first character in the buffer included in the view
   * @param length number of characters included in the view from the offset
   * @return A read-only view of the slice starting at offset (no defensive copy)
   */
  static Text of(char[] buffer, int offset, int length) {
    record Slice(char[] buffer, int offset, int length) implements Text {

      Slice {
        if (length < 0) throw new IllegalArgumentException("length must be >= 0");
        if (offset + length > buffer.length)
          throw new IllegalArgumentException("offset + length must be <= buffer.length");
      }

      @Override
      public char charAt(int index) {
        return buffer[offset + index];
      }

      @Override
      public @NotNull Slice subSequence(int start, int end) {
        checkSubSequence(start, end, length);
        if (start == 0 && end == length) return this;
        return new Slice(buffer, offset + start, end - start);
      }

      private boolean isInteger() {
        // if this buffer is the number cache
        // we do know the contents to be an integer number
        return buffer == Cache._100_TO_999;
      }

      @Override
      public boolean isTextualInteger() {
        return isInteger() || TextualNumber.isTextualInteger(buffer, offset, length);
      }

      @Override
      public boolean isNumericInteger() {
        return isInteger() || TextualNumber.isNumericInteger(buffer, offset, length);
      }

      @Override
      public boolean isTextualDecimal() {
        return isInteger() || TextualNumber.isTextualDecimal(buffer, offset, length);
      }

      @Override
      public boolean parseBoolean() {
        return Chars.parseBoolean(buffer, offset, length);
      }

      @Override
      public int parseInt() {
        return TextualNumber.parseIntExact(buffer, offset, length);
      }

      @Override
      public long parseLong() {
        return TextualNumber.parseLongExact(buffer, offset, length);
      }

      @Override
      public double parseDouble() {
        return TextualNumber.parseDoubleCast(buffer, offset, length);
      }

      @Override
      public Number parseNumber() {
        return TextualNumber.of(buffer, offset, length);
      }

      @Override
      public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Text text)) return false;
        return contentEquals(text);
      }

      @Override
      public int hashCode() {
        return Text.hashCode(this);
      }

      @Override
      public boolean contentEquals(CharSequence text) {
        if (text instanceof Slice other) {
          // optimization: avoid virtual method calls by comparing arrays directly
          if (length != other.length) return false;
          if (buffer == other.buffer && offset == other.offset) return true;
          for (int i = 0; i < length; i++)
            if (buffer[offset + i] != other.buffer[i + other.offset]) return false;
          return true;
        }
        return Text.super.contentEquals(text);
      }

      @Override
      public boolean contentMemoryEquals(@NotNull Text other) {
        return other instanceof Slice s && s.buffer == buffer;
      }

      @Override
      public char[] toCharArray() {
        return Arrays.copyOfRange(buffer, offset, offset + length);
      }

      @Override
      public @NotNull String toString() {
        return length == 1 ? String.valueOf(buffer[offset]) : new String(buffer, offset, length);
      }
    }
    return new Slice(buffer, offset, length);
  }

  /**
   * @apiNote should be considered private
   */
  record Cache() {
    /**
     * @implNote A private cache for digits of 0-999. It saves on allocation of a character buffer
     *     and increases the chance of the characters already being in CPU cache as we reuse the
     *     same memory region for small-ish indexes.
     */
    private static final char[] _100_TO_999 = new char[900 * 3];

    static {
      int j = 0;
      for (int i = 100; i < 1000; i++) {
        _100_TO_999[j++] = (char) ('0' + i / 100);
        _100_TO_999[j++] = (char) ('0' + (i % 100 / 10));
        _100_TO_999[j++] = (char) ('0' + i % 10);
      }
    }

    /**
     * A cache for short texts starting with a lower case letter like often used in path segment
     * names. This avoids allocation in a loop where certain object members are accessed using
     * {@link String}s. This is a typical use case of a JSON object API.
     */
    private static final Text[][] STARTING_A_Z_LENGTH_1_TO_16 = new Text[26][16];

    static Text get(char c, int length) {
      if (length < 1 || length > 16 || c < 'a' || c > 'z') return null;
      return STARTING_A_Z_LENGTH_1_TO_16[c - 'a'][length - 1];
    }

    static void set(Text text) {
      char c = text.charAt(0);
      int length = text.length();
      if (length < 1 || length > 16 || c < 'a' || c > 'z') return;
      STARTING_A_Z_LENGTH_1_TO_16[c - 'a'][length - 1] = text;
    }
  }

  /**
   * @return {@link Text} for of any int number
   */
  static Text of(int value) {
    if (value >= 0) {
      if (value < 10) return of(Cache._100_TO_999, value * 3 + 2, 1);
      if (value < 100) return of(Cache._100_TO_999, value * 3 + 1, 2);
      if (value < 1000) return of(Cache._100_TO_999, (value - 100) * 3, 3);
    }
    int len = TextBuilder.characterCount(value);
    char[] digits = new char[len];
    TextBuilder.appendInt(value, digits, 0, len);
    return of(digits, 0, digits.length);
  }

  static Text of(Path file, Charset encoding) {
    try {
      byte[] bytes = Files.readAllBytes(file);
      return Chars.decode(bytes, encoding, (arr, length) -> of(arr, 0, length));
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private static void checkSubSequence(int start, int end, int length) {
    if (start < 0 || end < 0 || end > length || start > end)
      throw new IndexOutOfBoundsException(
          "expected: 0 <= start(" + start + ") <= end (" + end + ") <= length(" + length + ')');
  }

  /**
   * @apiNote The exact hash code algorithm does not need to be specified as all classes
   *     implementing {@link Text} must use this function to compute the hash code. As long as the
   *     algorithm is deterministic (which it is) this will produce codes satisfying the
   *     requirements of {@link Object#hashCode()}.
   * @implNote Because a {@link Text} can be a view of a large slice of characters the hash-code
   *     algorithm just samples characters from the content. This increases the risk of hash-code
   *     collisions but makes computing the hash code cheap and fast O(1). This is particularly
   *     important in the main use case of {@link Text} as the main component of a {@link JsonPath}
   *     which is indexes in the {@link JsonNode} cache within a {@link JsonTree}.
   * @param text the slice to compute the hash code for
   * @return the computed hash code
   */
  static int hashCode(Text text) {
    if (text.isEmpty()) return 0;
    int hash = 1;
    int sampleSize = 9;
    int length = text.length();
    if (length <= sampleSize) {
      for (int i = 0; i < length; i++) hash = 31 * hash + text.charAt(i);
    } else {
      // take characters as evenly spaced as possible
      // while including both ends though linear interpolation
      int step = length - 1;
      for (int i = 0; i < sampleSize; i++)
        // index is equivalent to: i * step / (sampleSize - 1)
        hash = 31 * hash + text.charAt(i * step >> 3);
    }
    return hash;
  }
}
