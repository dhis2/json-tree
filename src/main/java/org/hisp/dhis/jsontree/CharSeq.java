package org.hisp.dhis.jsontree;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * All the utility that really should have been in {@link CharSequence} providing all read-only
 * utility that is common for {@link String} and beyond solely based on methods already defined in
 * {@link CharSequence}. In other words: a type implementing {@link CharSequence} can switch to
 * {@link CharSeq} to bring in all the utility provided without any further changes.
 *
 * <p>In addition, the API provides methods to transfer the content of the sequence to {@code
 * char[]} as well as parsing it to numeric primitives (without going via {@link String}).
 *
 * @author Jan Bernitt
 * @since 1.13 (methods without since originate from Text in 1.9)
 * @see CharFragment
 * @see Text
 */
public interface CharSeq extends CharSequence {

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
   * @param test returns true for a match
   * @return true, when this text contains a character that satisfies the given test
   * @since 1.13
   */
  default boolean contains(CharPredicate test) {
    if (isEmpty()) return false;
    int len = length();
    for (int i = 0; i < len; i++) if (test.test(charAt(i))) return true;
    return false;
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
   * @see #skip(CharPredicate, int, int)
   * @since 1.13
   */
  default int skip(CharPredicate test) {
    return skip(test, 0, length());
  }

  /**
   * @see #skip(CharPredicate, int, int)
   * @since 1.13
   */
  default int skip(CharPredicate test, int startIndex) {
    return skip(test, startIndex, length());
  }

  /**
   * Find the first character index in this sequence that does not satisfy the given test.
   *
   * @param test the test to perform on each character
   * @param startIndex index in this sequence to start testing from
   * @param endIndex the end of the checked range (exclusive)
   * @return the first index that did not satisfy the given test starting from given start index;
   *     returns {@code endIndex} if all characters from the start did not satisfy the test;
   *     {@code -1} when start index is outside valid index range for this sequence. This is equal
   *     to the number of characters skipped if starting from zero or {@code startIndex + characters
   *     skipped}.
   * @since 1.13
   */
  default int skip(CharPredicate test, int startIndex, int endIndex) {
    if (startIndex < 0) return -1;
    if (startIndex >= endIndex) return -1;
    int len = length();
    if (startIndex >= len) return -1;
    int e = Math.min(len, endIndex);
    for (int i = startIndex; i < e; i++) if (!test.test(charAt(i))) return i;
    return e;
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

  /**
   * @implNote This is not RegEx matching like {@link String#matches(String)} but rather matching
   *     using this sequence as {@link InputExpression} pattern.
   * @param input an input string
   * @return if this text as pattern matches the entirety of the given input
   */
  default boolean matches(CharSequence input) {
    return matches(input, 0, input.length());
  }

  /**
   * Match an input sub-sequence against this text as pattern.
   *
   * @implNote This is not RegEx matching like {@link String#matches(String)} but rather matching
   *     using this sequence as {@link InputExpression} pattern.
   * @see InputExpression
   * @param input an input string
   * @param offset the first character to match
   * @param len length from the first character to match
   * @return if this text as pattern matches the subsequence of the given input
   */
  default boolean matches(CharSequence input, int offset, int len) {
    return InputExpression.match(toCharArray(), 0, length(), input, offset) == offset + len;
  }

  /*
  Transfer to char[]
   */

  /**
   * @see String#toCharArray()
   * @see #copyTo(char[], int) to avoid alocation of the result
   */
  default char[] toCharArray() {
    char[] arr = new char[length()];
    for (int i = 0; i < arr.length; i++) arr[i] = charAt(i);
    return arr;
  }

  /**
   * Copies this entire text to the dest array.
   *
   * @param dest array to write to
   * @param offset first index to write to in dest
   * @since 1.10
   */
  default void copyTo(char[] dest, int offset) {
    int len = length();
    for (int i = 0; i < len; i++) dest[i + offset] = charAt(i);
  }

  /**
   * Copies a slice of this text to the dest array.
   *
   * @param dest array to write to
   * @param offset first index to write to in dest
   * @param from first index to write in this text
   * @param length number of characters to write
   * @since 1.10
   */
  default void copyTo(char[] dest, int offset, int from, int length) {
    int to = from + length;
    for (int i = from; i < to; i++) dest[offset++] = charAt(i);
  }

  /*
  Numeric conversion support
   */

  /**
   * Tests if this sequence is the <em>form</em> of an integer literal: a sequence of one or more
   * decimal digits, optionally preceded by a {@code +} or {@code -} sign, and containing no
   * decimal point, exponent, or whitespace.
   *
   * <p>This is a purely <em>syntactic</em> check. Numeric range is not considered, so values that
   * overflow {@code int} or {@code long} (for example {@code "2147483648"} or {@code
   * "123456789012345678901234567890"}) are still integer literals. Range is only enforced by the
   * corresponding parse methods: {@link #parseInt()}, {@link #parseLong()}, or {@link
   * #parseNumber()} for arbitrary magnitude.
   *
   * <p>Returns {@code false} for the empty sequence and for a lone sign ({@code "+"} or {@code
   * "-"}).
   *
   * @return true if this sequence is the form of an integer literal
   * @see #isIntegerValue() for the corresponding value predicate
   * @apiNote renamed from isTextualInteger to isIntegerLiteral in 1.14
   */
  default boolean isIntegerLiteral() {
    if (isEmpty()) return false;
    int i = 0;
    if (charAt(0) == '-' || charAt(0) == '+') i++;
    int len = length();
    for (; i < len; i++) if (charAt(i) < '0' || charAt(i) > '9') return false;
    return true;
  }

  /**
   * Tests if this sequence <em>represents</em> an integer value: either an {@link
   * #isIntegerLiteral() integer literal}, or a decimal number whose fractional part, if present, is
   * all zeros (for example {@code "1.0"}, {@code "123.000"}, or {@code "5."}).
   *
   * <p>At least one digit is required before the decimal point, so {@code "5."} is an integer value
   * but {@code ".5"} is not, and neither is {@code ".0"}. This is a deliberate restriction, not an
   * oversight: the primary use case is detecting the formatter artifact of appending {@code .0} to
   * a value that is really an integer (e.g. {@code "42.0"}), and no common serialiser produces a
   * leading-dot form like {@code ".0"} for zero. The one asymmetry this creates — {@link
   * #isZeroValue()} does accept {@code ".0"} — is the price of that restriction, and is confined to
   * an input shape that doesn't occur in practice.
   *
   * <p>Unlike {@link #isZeroValue()} this does <em>not</em> accept exponential notation. A value
   * like {@code "1e5"} reports {@code false} here even though it denotes the integer {@code
   * 100000}. Use {@link #isNumberLiteral()} to check the general number form.
   *
   * <p>Returns {@code false} for the empty sequence and for any input that is not a valid number.
   *
   * @return true if this sequence represents an integer value
   * @see #isIntegerLiteral() for the corresponding form predicate
   * @apiNote renamed from isNumericInteger to isIntegerValue in 1.14
   */
  default boolean isIntegerValue() {
    return TextualNumber.isIntegerValue(toCharArray());
  }

  /**
   * Tests if this sequence <em>represents</em> the numeric value zero: an optional sign, followed
   * by digits that are all zero and optionally a decimal point with more zero digits, and
   * optionally any exponent. Examples that return {@code true}: {@code "0"}, {@code "-0"}, {@code
   * "0.0"}, {@code "-0.00"}, {@code "0E5"}, {@code "-0.0E-60"}.
   *
   * <p>Unlike {@link #isIntegerValue()} this <em>does</em> strip the exponent before checking,
   * because the value zero is unaffected by the exponent. This is why {@code "0E5"} is a zero
   * value but {@code "1e5"} is not an integer value.
   *
   * <p>Returns {@code false} for the empty sequence and for any input that is not a valid number.
   *
   * @return true if this sequence represents the numeric value zero
   * @since 1.14
   */
  default boolean isZeroValue() {
    return TextualNumber.isZeroValue(toCharArray());
  }

  /**
   * Tests if this sequence is the <em>form</em> of a number literal: an optional {@code +} or
   * {@code -} sign, followed by digits and optionally a single decimal point with at least one
   * digit on at least one side of the point, optionally followed by an exponent introduced by
   * {@code e} or {@code E} with an optional sign and one or more digits. No whitespace is allowed
   * anywhere.
   *
   * <p>This is a purely <em>syntactic</em> check. Numeric range is not considered, so arbitrarily
   * large numbers (e.g. {@code "9999999999999999999999999999999999"}) are accepted; range
   * semantics are applied by the parse methods ({@link #parseDouble()}, {@link #parseNumber()}).
   *
   * <p>The Java double special values {@code NaN}, {@code Infinity}, and {@code -Infinity} are not
   * number literals in this sense; test those with {@link #isSpecialDoubleValue()}.
   *
   * <p>Returns {@code false} for the empty sequence and for input that does not match the number
   * grammar (for example {@code "1e"}, {@code "1.2.3"}, or any input containing whitespace).
   *
   * @return true if this sequence is the form of a number literal
   * @see #isIntegerLiteral() for the stricter integer-only form predicate
   * @apiNote renamed from isTextualDecimal to isNumberLiteral in 1.14
   */
  default boolean isNumberLiteral() {
    return TextualNumber.isNumberLiteral(toCharArray());
  }

  /**
   * Tests if this sequence is exactly one of the Java {@code double} special value literals: {@code
   * "NaN"}, {@code "Infinity"}, or {@code "-Infinity"}.
   *
   * <p>The comparison is case-sensitive and does not accept surrounding whitespace, matching what
   * {@link Double#parseDouble(String)} accepts for these values. A special double value is
   * <em>not</em> a finite number literal, so {@link #isNumberLiteral()} returns {@code false} for
   * these.
   *
   * @return true if this sequence is one of the Java double special value literals
   * @apiNote renamed from isSpecialDecimal to isSpecialDoubleValue in 1.14
   */
  default boolean isSpecialDoubleValue() {
    return contentEquals("NaN") || contentEquals("Infinity") || contentEquals("-Infinity");
  }

  /**
   * In contrast to the JDK method this only accepts valid inputs.
   *
   * @throws IllegalArgumentException when text is neither "true" nor "false" (case-insensitive)
   * @see Boolean#parseBoolean(String)
   */
  default boolean parseBoolean() {
    return CharDecoder.parseBoolean(toCharArray());
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
    return TextualNumber.parseDouble(toCharArray());
  }

  /**
   * Parses this sequence as a number, choosing the smallest type that can represent the value
   * without loss.
   *
   * <p>Integer literals become {@link Integer}, {@link Long} or {@link BigInteger} (in that
   * order, whichever fits). The numeric value zero and the special double values ({@code NaN},
   * {@code ±Infinity}) become {@link Double} — which preserves the {@code 0.0} vs {@code -0.0}
   * distinction that {@link java.math.BigDecimal} cannot. Any other decimal literal becomes a
   * lazily-parsed {@link Number} that preserves the exact input text; the {@link Textual} and
   * {@link CharSeq} interfaces may be used to recover that text from the returned instance:
   *
   * <pre>{@code
   * Number n = someText.parseNumber();
   * if (n instanceof Textual t) {
   *     Text original = t.textValue();  // exact input form, e.g. "1.50" not "1.5"
   * }
   * }</pre>
   *
   * <p>This method does not allow whitespace at the start or end of the sequence.
   *
   * @return this sequence parsed as a number
   * @throws NumberFormatException if this sequence is not a valid number literal or special
   *     double value
   */
  default Number parseNumber() {
    return TextualNumber.of(toCharArray());
  }

  /**
   * @see BigDecimal#BigDecimal(String)
   * @since 1.14
   */
  default BigDecimal parseBigDecimal() {
    return new BigDecimal(toCharArray());
  }

  /**
   * @see BigInteger#BigInteger(String)
   * @since 1.14
   */
  default BigInteger parseBigInteger() {
    return parseBigDecimal().toBigIntegerExact();
  }

  /**
   * A fast hash-code algorithm that takes a fixed number of samples from the sequence
   * and thus can be considered O(1) independent of the length of the sequence.
   * This naturally comes at a higher risk of hash code collisions.
   *
   * @apiNote The exact hash code algorithm is not defined other than that it is deterministic and all hashes produced satisfying the
   *     requirements of {@link Object#hashCode()} contract.
   * @implNote Because a {@link CharSequence} can be a view of a large slice of characters the hash-code
   *     algorithm just samples characters from the content. This increases the risk of hash-code
   *     collisions but makes computing the hash code cheap and fast O(1).
   *
   *     <p>In context of {@link Text tThis is particularly important in the main use case of {@link Text}
   *     as the main component of a {@link JsonPath} which is indexes in the {@link JsonNode} cache within a {@link JsonTree}.
   * @param text the sequence to compute the hash code for
   * @return the computed hash code
   */
  static int hashCode(CharSequence text) {
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
