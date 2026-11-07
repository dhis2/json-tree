package org.hisp.dhis.jsontree;

import java.util.Arrays;
import java.util.function.Consumer;
import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * Is a {@link CharSeq} with manipulation methods all returning a consistent subtype.
 *
 * @implNote It is good practice to derive a sealed sub-interface of a {@link CharFragment}
 * so that any call to its API will be monomorphic with a single implementation for said sub-interface.
 * This way the utility inherited from the abstract {@link CharSeq} and {@link CharFragment}
 * comes without the overhead of polymorphic calls.
 *
 * @param <T> the subtype returned by manipulation (new immutable instances)
 *
 * @author Jan Bernitt
 * @since 1.13 (methods without since originate from Text in 1.9)
 * @see Text
 */
public interface CharFragment<T extends CharFragment<T>> extends CharSeq, Comparable<T> {

  /*
  The essential methods
   */

  /**
   * @return an immutable view of this sequence in its current state, can be {@code this} in case no
   *     changes can be observed later on.
   * @since 1.13
   */
  T self();

  /**
   * @return a value representing the empty sequence with the right type
   * @since 1.13
   */
  T empty();

  /**
   * Note that wrap is a factory for new instances
   * to create new instances of the same implementation
   * class used by this text but otherwise is not dependent
   * on the particular character sequence in this text.
   *
   * @param buffer backing buffer (assume ownership - immutability)
   * @param offset start of the sequence in the buffer represented by the returned sequence
   * @param length length of the returned sequence starting at offset in the given buffer
   * @return a new instance representing the sequence given by the slice from the buffer
   * @since 1.13
   */
  T wrap(char[] buffer, int offset, int length);

  /*
  The utilities this offers
   */

  /**
   * @see #wrap(char[], int, int)
   * @since 1.13
   */
  default T wrap(char... chars) {
    return wrap(chars, 0, chars.length);
  }

  @Override
  default @NotNull T subSequence(int start, int end) {
    int length = length();
    checkSubSequence(start, end, length);
    if (start == 0 && end == length) return self();
    int len = end - start;
    char[] buffer = new char[len];
    for (int i = 0; i < len; i++) buffer[i] = charAt(start + i);
    return wrap(buffer, 0, len);
  }

  /**
   * @see #subSequence(int, int)
   * @since 1.13
   */
  default T subSequence(int start) {
    return subSequence(start, length());
  }

  /**
   * Same as {@link #subSequence(int, int)} except that negative end is slicing away that number of
   * characters from the end and that any slice exceeding the limit of what is available results in
   * an empty text not an error. The operation still fails if start or end are out of bounds (less
   * than zero or larger than length).
   *
   * @param start offset or first character to include in the slice
   * @param end first character to exclude from the slice or when negative number of characters to
   *     drop from the end
   * @return the slice
   */
  default T slice(int start, int end) {
    return subSequence(start, Math.max(start, end < 0 ? length()+end : end));
  }

  /**
   * @see #slice(int, int)
   * @since 1.13
   */
  default T slice(int start) {
    return slice(start, length());
  }

  /**
   * @implNote This is not identical to {@link String#trim()} but trims ASCII indentation whitespace characters only
   * @return this text without left and right inline whitespace (tabs and spaces removed)
   */
  default T trim() {
    return shrink(CharFragment::isTrim);
  }

  /**
   * @implNote This is not identical to {@link String#strip()} but trims ASCII whitespace characters only
   * @return this text without leading or trailing whitespace (inline and newline/line-breaks)
   * @since 1.13
   */
  default T strip() {
    return shrink(CharFragment::isStrip);
  }

  /**
   * @param test characters to strip from start/end of this text
   * @return this text without any leading or trailing characters that satisfied the given test
   * @since 1.13
   */
  default T shrink(CharPredicate test) {
    if (isEmpty()) return self();
    int len = length();
    int left = 0;
    while (left < len && test.test(charAt(left))) left++;
    if (left == len) return empty();
    int right = len-1;
    while (right > left && test.test(charAt(right))) right--;
    if (left == 0 && right == len-1) return self();
    return subSequence(left, right + 1);
  }

  /**
   * @see String#concat(String)
   */
  default T concat(CharSequence suffix) {
    if (suffix.isEmpty()) return self();
    char[] a = toCharArray();
    int len = suffix.length();
    char[] res = Arrays.copyOf(a, a.length+ len);
    for (int i = 0; i < len; i++)
      res[a.length+i] = suffix.charAt(i);
    return wrap(res, 0, res.length);
  }

  /**
   * Slices this text into segments based on a pattern.
   * Note that in contrast to a split by regular expression this matches the pattern at the start of this text,
   * slices of the section as the first segment and repeats the process on the remaining tail text.
   * If the pattern does not match the beginning of the text the remaining tail is the last segment.
   *
   * @param pattern the prefix to extract into segments
   * @param segments accepts the slices of segments in order left to right
   */
  default void slice(CharSeq pattern, Consumer<T> segments) {
    if (isEmpty()) {
      segments.accept(self());
      return;
    }
    char[] p = pattern.toCharArray();
    int pos = 0;
    int len = length();
    int end = InputExpression.match(p, 0, p.length, this, pos);
    if (end < 0 || end >= len) {
      segments.accept(self());
    } else {
      do {
        segments.accept(subSequence(pos, end));
        pos = end;
        end = InputExpression.match(p, 0, p.length, this, pos);
      } while (end >= 0 && end < len);
      segments.accept(subSequence(pos, len));
    }
  }

  /**
   * @see String#compareTo(String)
   */
  @Override
  default int compareTo(T other) {
    int n = Math.min(length(), other.length());
    for (int k = 0; k < n; k++) {
      int res = charAt(k) - other.charAt(k);
      if (res != 0) return res;
    }
    return length() - other.length();
  }

  /*
  Special purpose
   */

  /**
   * Reduces a number's text to its pure value digits so that equivalent integer representations
   * compare equal as text.
   *
   * <p>The input is expected to be an integer value as per {@link #isNumericInteger()} — a plain
   * integer, optionally signed, or a decimal with an all-zero fraction. The output is canonical: no
   * sign on zero, no leading zeros, no trailing {@code .0+} fraction. A leading {@code '+'} or
   * {@code '-'} is consumed; a {@code '-'} is preserved on non-zero results, and dropped entirely
   * on zero.
   *
   * <p>Behaviour on non-integer input is unspecified: a non-zero fraction is silently dropped.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>{@code "42"} → {@code "42"}
   *   <li>{@code "+42"} → {@code "42"}
   *   <li>{@code "-42"} → {@code "-42"}
   *   <li>{@code "42.00"} → {@code "42"}
   *   <li>{@code "0"} → {@code "0"}
   *   <li>{@code "-0"} → {@code "0"}
   *   <li>{@code "+0"} → {@code "0"}
   *   <li>{@code "0.00"} → {@code "0"}
   * </ul>
   *
   * @since 1.13
   */
  default T trimInteger() {
    int len = length();
    if (len == 0) return self();
    boolean negative = charAt(0) == '-';
    int start = (negative || charAt(0) == '+') ? 1 : 0;
    // The integer part ends at the dot if there is one, otherwise at the end.
    int dot = indexOf('.', start);
    int intEnd = dot < 0 ? len : dot;
    while (start < intEnd - 1 && charAt(start) == '0') start++;
    // An all-zero integer part collapses to "0" regardless of sign.
    if (intEnd - start == 1 && charAt(start) == '0') return wrap('0');
    T digits = subSequence(start, intEnd);
    return negative ? wrap('-').concat(digits) : digits;
  }

  /*
  statics
   */

  static void checkSubSequence(int start, int end, int length) {
    if (start < 0 || end < 0 || end > length || start > end)
      throw new IndexOutOfBoundsException(
          "expected: 0 <= start(" + start + ") <= end (" + end + ") <= length(" + length + ')');
  }

  /**
   * The {@link CharPredicate} for {@link #trim()}.
   *
   * @param ch character to test
   * @return true for ASCII indent whitespace
   */
  static boolean isTrim(char ch) {
    return ch == ' ' || ch == '\t';
  }

  /**
   * The {@link CharPredicate} for {@link #strip()}.
   *
   * @param ch character to test
   * @return true for ASCII whitespace
   */
  static boolean isStrip(char ch) {
    return isTrim(ch) || ch == '\n' || ch == '\r';
  }

}
