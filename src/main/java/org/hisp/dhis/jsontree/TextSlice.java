package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.NotNull;

import java.util.Arrays;

import static java.lang.Math.abs;
import static org.hisp.dhis.jsontree.CharFragment.checkSubSequence;

/**
 * A no-defensive-copy view of a slice from a {@code char[]} text sequence.
 *
 * @param buffer the sequence viewed
 * @param offset the start of the view in the buffer
 * @param length the length of the viewed sequence in the buffer from the given start
 */
record TextSlice(char[] buffer, int offset, int length) implements Text {

  /**
   * Text of a number, 0-999 cached, others allocate one new exact {@code char[]}.
   *
   * @param value numeric value to represent as Text
   * @return Text form of the given value
   */
  static Text of(int value) {
    if (value >= 0) {
      if (value < 10) return new TextSlice(ZERO_TO_999, value * 3 + 2, 1);
      if (value < 100) return new TextSlice(ZERO_TO_999, value * 3 + 1, 2);
      if (value < 1000) return new TextSlice(ZERO_TO_999, (value - 100) * 3, 3);
    }
    int len = characterCount(value);
    char[] digits = new char[len];
    appendInt(value, digits, len);
    return new TextSlice(digits, 0, digits.length);
  }

  static Text of(CharSequence text) {
    if (text instanceof Text t) return t;
    if (text instanceof String s) return of(s);
    int len = text.length();
    if (len == 0) return EMPTY;
    if (text instanceof CharSeq cs) {
      char[] buffer = new char[cs.length()];
      cs.copyTo(buffer, 0);
      return new TextSlice(buffer, 0, len);
    }
    Text cached = cacheGet(text.charAt(0), len);
    if (cached != null && cached.contentEquals(text)) return cached;
    char[] buffer = new char[len];
    for (int i = 0; i < len; i++) buffer[i] = text.charAt(i);
    Text res = new TextSlice(buffer, 0, len);
    cacheUpdate(res);
    return res;
  }

  static Text of(String text) {
    if (text.isEmpty()) return EMPTY;
    Text cached = cacheGet(text.charAt(0), text.length());
    if (cached != null && cached.contentEquals(text)) return cached;
    char[] buffer = text.toCharArray();
    Text res = new TextSlice(buffer, 0, buffer.length);
    cacheUpdate(res);
    return res;
  }

  TextSlice {
    if (length < 0) throw new IllegalArgumentException("length must be >= 0");
    if (offset + length > buffer.length)
      throw new IllegalArgumentException("offset + length must be <= buffer.length");
  }

  @Override
  public Text self() {
    return this;
  }

  @Override
  public Text wrap(char[] buffer, int offset, int length) {
    checkSubSequence(offset, offset+length, buffer.length);
    return new TextSlice(buffer, offset, length);
  }

  @Override
  public char charAt(int index) {
    return buffer[offset + index];
  }

  @Override
  public @NotNull Text subSequence(int start, int end) {
    checkSubSequence(start, end, length);
    if (start == 0 && end == length) return this;
    return new TextSlice(buffer, offset + start, end - start);
  }

  private boolean isInteger() {
    // if this buffer is the number cache
    // we do know the contents to be an integer number
    return buffer == ZERO_TO_999 && length > 0;
  }

  @Override
  public boolean isTextualInteger() {
    return isInteger() || TextualNumber.isIntegerLiteral(buffer, offset, length);
  }

  @Override
  public boolean isNumericInteger() {
    return isInteger() || TextualNumber.isIntegerValue(buffer, offset, length);
  }

  @Override
  public boolean isTextualDecimal() {
    return isInteger() || TextualNumber.isNumberLiteral(buffer, offset, length);
  }

  @Override
  public boolean parseBoolean() {
    return Chars.parseBoolean(buffer, offset, length);
  }

  @Override
  public int parseInt() {
    if (buffer == ZERO_TO_999) return parseIntCached();
    return TextualNumber.parseIntExact(buffer, offset, length);
  }

  @Override
  public long parseLong() {
    if (buffer == ZERO_TO_999) return parseIntCached();
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
  public boolean matches(CharSequence input, int offset, int len) {
    return InputExpression.match(buffer, offset, length, input, offset) == offset+len;
  }

  /**
   * Computes the integer value of a slice of the {@link #ZERO_TO_999} cache arithmetically from
   * the slice's {@code offset} instead of reading and parsing its characters.
   * <p>
   * Every slice of the cache that can be produced by {@link #of(int)} or
   * {@link #subSequence(int, int)} covers the digit block
   * {@code [offset % 3, offset % 3 + length)} of the 3-digit number {@code 100 + offset / 3},
   * so the value is that block extracted by division and modulo. Slices outside that shape (in
   * particular the empty slice) fall back to the generic parser.
   */
  private int parseIntCached() {
    int first = offset % 3;
    if (length < 1 || length > 3 || first + length > 3)
      return TextualNumber.parseIntExact(buffer, offset, length);
    int n = 100 + offset / 3;
    return n / POW10[3 - first - length] % POW10[length];
  }

  private static final int[] POW10 = {1, 10, 100, 1000};

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Text text)) return false;
    return contentEquals(text);
  }

  @Override
  public int hashCode() {
    return CharSeq.hashCode(this);
  }

  @Override
  public boolean contentEquals(CharSequence text) {
    if (text instanceof TextSlice other) {
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
  public int offsetIn(Text other) {
    if (other instanceof TextSlice ts) {
      if (buffer != ts.buffer) return -1;
      int relOffset = offset-ts.offset;
      if (relOffset < 0) return -1;
      if (ts.offset + ts.length < offset+length) return -1;
      return relOffset;
    }
    // unreachable since TextSlice is the only permitted Text
    return -1;
  }

  @Override
  public boolean contentMemoryEquals(@NotNull Text other) {
    return other instanceof TextSlice s && s.buffer == buffer;
  }

  @Override
  public Text concat(CharSequence suffix) {
    if (suffix.isEmpty()) return this;
    if (isEmpty()) return Text.of(suffix);
    char[] res = new char[length + suffix.length()];
    System.arraycopy(buffer, offset, res, 0, length);
    if (suffix instanceof CharSeq cs) {
      cs.copyTo(res, length);
    } else {
      for (int i = 0; i < suffix.length(); i++) res[length + i] = suffix.charAt(i);
    }
    return Text.of(res, 0, res.length);
  }

  @Override
  public char[] toCharArray() {
    return Arrays.copyOfRange(buffer, offset, offset + length);
  }

  @Override
  public void copyTo(char[] dest, int offset) {
    System.arraycopy(buffer, this.offset, dest, offset, length);
  }

  @Override
  public void copyTo(char[] dest, int offset, int from, int length) {
    System.arraycopy(buffer, this.offset+from, dest, offset, length);
  }

  @Override
  public @NotNull String toString() {
    return length == 1 ? String.valueOf(buffer[offset]) : new String(buffer, offset, length);
  }

  /**
   * @implNote A private cache for digits of 0-999. It saves on allocation of a character buffer
   *     and increases the chance of the characters already being in CPU cache as we reuse the
   *     same memory region for small-ish indexes.
   */
  private static final char[] ZERO_TO_999 = new char[900 * 3];

  static {
    int j = 0;
    for (int i = 100; i < 1000; i++) {
      ZERO_TO_999[j++] = (char) ('0' + i / 100);
      ZERO_TO_999[j++] = (char) ('0' + (i % 100 / 10));
      ZERO_TO_999[j++] = (char) ('0' + i % 10);
    }
  }

  /**
   * For no specific reason other than "attributeoptioncombo" being 20 letters
   */
  private static final int WORD_CACHE_MAX_LENGTH = 20;

  /**
   * A cache for short texts starting with a lower case letter like often used in path segment
   * names. The lookup is very cheap with 2 simple index computations.
   * <p>
   * This avoids allocation in a loop where certain object members are accessed using
   * non-{@link Text} {@link CharSequence}s like {@link String}s.
   * This is a typical use case of a JSON object API.
   */
  private static final Text[][] WORD_CACHE = new Text[26][WORD_CACHE_MAX_LENGTH];

  private static Text cacheGet(char c, int length) {
    if (length < 1 || length > WORD_CACHE_MAX_LENGTH || c < 'a' || c > 'z') return null;
    return WORD_CACHE[c - 'a'][length - 1];
  }

  private static void cacheUpdate(Text text) {
    char c = text.charAt(0);
    if (c < 'a' || c > 'z') return;
    int length = text.length();
    if (length < 1 || length > WORD_CACHE_MAX_LENGTH) return;
    WORD_CACHE[c - 'a'][length - 1] = text;
  }

      /*
  Text(int) Helpers
   */

  private static void appendInt(int value, char[] buffer, int length) {
    boolean neg = value < 0;
    boolean overflow = value == Integer.MIN_VALUE;
    value = overflow ? value - 1 : abs(value);
    int digit0 = neg ? 1 : 0;
    if (neg) buffer[0] = '-';
    int rest = value;
    for (int i = length - 1; i >= digit0; i--) {
      buffer[i] = (char) ('0' + (rest % 10));
      rest /= 10;
    }
    if (overflow) buffer[length - 1] += 1;
  }

  private static int characterCount(int value) {
    if (value == 0) return 1;
    int n = digitCount(value);
    return value < 0 ? n + 1 : n;
  }

  private static int digitCount(int n) {
    if (n < 0) {
      if (n == Integer.MIN_VALUE) return 10;
      n = -n;
    }
    if (n < 10) return 1;
    if (n < 100) return 2;
    if (n < 1000) return 3;
    if (n < 10000) return 4;
    if (n < 100000) return 5;
    if (n < 1000000) return 6;
    if (n < 10000000) return 7;
    if (n < 100000000) return 8;
    if (n < 1000000000) return 9;
    return 10;
  }
}
