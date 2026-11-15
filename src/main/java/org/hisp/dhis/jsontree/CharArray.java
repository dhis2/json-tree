package org.hisp.dhis.jsontree;

import java.util.function.Consumer;
import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * Is a {@link CharSeq} that can be mutated to a consistent subtype. A {@linkplain CharArray}
 * represents the group {@link CharSeq} subtypes that provide an API wrapping a {@code char[]} that
 * is considered immutable.
 *
 * <p>Similar to {@link CharSeq} it mirrors methods found in {@link String} with the caveat that
 * their logic applies to the ASCII range (e.g. {@link #toUpperCase()} only affects ASCII letters).
 *
 * @implNote It is good practice to derive a sealed sub-interface of a {@link CharArray} so that any
 *     call to its API will be monomorphic with a single implementation for said sub-interface. This
 *     way the utility inherited from the abstract {@link CharSeq} and {@link CharArray} comes
 *     without the overhead of polymorphic calls.
 *     <p>A subtype only needs to implement two simple methods, {@link #self()} ({@code return
 *     this;}) and {@link #wrap(char[], int, int)}.
 * @param <T> the subtype returned by mutations (new immutable instances)
 * @author Jan Bernitt
 * @since 1.13 (methods without since originate from Text in 1.9)
 * @see Text
 * @apiNote renamed from {@code CharFragment} to {@code CharArray} in 1.14
 */
public interface CharArray<T extends CharArray<T>> extends CharSeq, Comparable<T> {

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
    return shrink(CharArray::isTrim);
  }

  /**
   * @implNote This is not identical to {@link String#strip()} but trims ASCII whitespace characters only
   * @return this text without leading or trailing whitespace (inline and newline/line-breaks)
   * @since 1.13
   */
  default T strip() {
    return shrink(CharArray::isStrip);
  }

  /**
   * @see String#stripLeading()
   * @since 1.14
   **/
  default T stripLeading() {
    return shrink(CharArray::isStrip, true, false);
  }

  /**
   * @see String#stripTrailing()
   * @since 1.14
   **/
  default T stripTrailing() {
    return shrink(CharArray::isStrip, false, true);
  }

  /**
   * @see #shrink(CharPredicate, boolean, boolean)
   * @since 1.13
   */
  default T shrink(CharPredicate test) {
    return shrink(test, true, true);
  }

  /**
   * @param test characters to strip from start/end of this text
   * @param left true to shrink form the start or left side
   * @param right true to shrink form the end or right side
   * @return this text without any leading or trailing characters that satisfied the given test
   * @since 1.14
   */
  default T shrink(CharPredicate test, boolean left, boolean right) {
    if (isEmpty() || (!left && !right)) return self();
    int len = length();
    int l = 0;
    if (left)
      while (l < len && test.test(charAt(l))) l++;
    if (l == len) return subSequence(l, l);
    int r = len - 1;
    if (right)
      while (r >= l && test.test(charAt(r))) r--;
    if (r < l) return subSequence(l, l);
    if (l == 0 && r == len - 1) return self();
    return subSequence(l, r + 1);
  }

  /**
   * @see String#concat(String)
   */
  default T concat(CharSequence suffix) {
    return concat(self(), suffix);
  }

  /**
   * Common code to concatenate two texts to a specific result type
   * based on the text a suffix is appended to.
   *
   * @param self the text a suffix is appended to
   * @param suffix the text to append
   * @return a new instance with the two text fragments concatenated together
   * @param <T> type of the result fragment
   * @since 1.14
   */
  static <T extends CharArray<T>> T concat(T self, CharSequence suffix) {
    if (suffix.isEmpty()) return self;
    int len = self.length();
    char[] res = new char[len+suffix.length()];
    self.copyTo(res, 0);
    CharSeq.copyTo(suffix, res, len);
    return self.wrap(res);
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
   * Returns this sequence with every occurrence of {@code oldChar} replaced by
   * {@code newChar}.
   *
   * <p>Like {@link String#replace(char, char)}, returns {@link #self()} (no allocation)
   * when {@code oldChar == newChar} or when {@code oldChar} does not occur in this
   * sequence.
   *
   * @see String#replace(char, char)
   * @since 1.14
   */
  default T replace(char oldChar, char newChar) {
    if (oldChar == newChar || !contains(oldChar)) return self();
    char[] replaced = toCharArray();
    for (int i = 0; i < replaced.length; i++)
      if (replaced[i] == oldChar) replaced[i] = newChar;
    return wrap(replaced, 0, replaced.length);
  }

  /**
   * Returns this sequence with every non-overlapping occurrence of {@code target} replaced
   * by {@code replacement}.
   *
   * <p>Returns {@link #self()} without allocating when {@code target} is empty or does not
   * occur in this sequence. Note that the empty-target case diverges from
   * {@link String#replace(CharSequence, CharSequence)}, which inserts {@code replacement}
   * at every position; here an empty target has zero occurrences (matching
   * {@link #count(CharSequence)}) and is a no-op.
   *
   * @see String#replace(CharSequence, CharSequence)
   * @since 1.14
   */
  default T replace(CharSequence target, CharSequence replacement) {
    if (isEmpty() || target.isEmpty() || !contains(target)) return self();
    int oldLen = target.length();
    int newLen = replacement.length();
    int n = count(target);
    int delta = n * (newLen - oldLen);
    int len = length();
    if (len + delta == 0) return wrap(); // empty
    char[] dest = new char[len + delta];
    int offset = 0;
    int from = 0;
    int to = indexOf(target, from);
    while (to >= 0) {
      int gapLen = to - from;
      copyTo(dest, offset, from, gapLen);
      from = to + oldLen;
      offset += gapLen;
      CharSeq.copyTo(replacement, dest, offset);
      offset += newLen;
      to = indexOf(target, from);
    }
    copyTo(dest, offset, from, len - from);
    return wrap(dest);
  }

  /**
   * @see String#repeat(int)
   * @since 1.14
   */
  default T repeat(int count) {
    if (count < 0) throw new IllegalArgumentException("repeat count is negative: " + count);
    if (count == 1) return self();
    if (count == 0 || isEmpty()) return wrap();
    int len = length();
    long resultLen = (long) len * count;
    if (resultLen > Integer.MAX_VALUE)
      throw new IllegalArgumentException("repeat result too large: " + resultLen);
    char[] dest = new char[(int) resultLen];
    for (int offset = 0; offset < dest.length; offset += len)
      copyTo(dest, offset);
    return wrap(dest);
  }

  /**
   * Returns this sequence with ASCII letters {@code 'A'}..{@code 'Z'} converted to
   * {@code 'a'}..{@code 'z'}.
   *
   * <p>Deliberately ASCII-only, matching {@link #trim()}, {@link #strip()}, and
   * {@link #isBlank()} in this API. Non-ASCII letters (including accented Latin,
   * Cyrillic, Greek) and locale-specific case rules (e.g. Turkish dotless {@code ı})
   * are not touched. This is narrower than {@link String#toLowerCase()}.
   *
   * <p>Returns {@link #self()} without allocating when this sequence contains no
   * uppercase ASCII letters.
   *
   * @see String#toLowerCase()
   * @since 1.14
   */
  default T toLowerCase() {
    return toCase(CharArray::isUpperCase, 32);
  }

  /**
   * Returns this sequence with ASCII letters {@code 'a'}..{@code 'z'} converted to
   * {@code 'A'}..{@code 'Z'}.
   *
   * <p>Deliberately ASCII-only, matching {@link #trim()}, {@link #strip()}, and
   * {@link #isBlank()} in this API. Non-ASCII letters (including accented Latin,
   * Cyrillic, Greek) and locale-specific case rules (e.g. Turkish dotless {@code ı})
   * are not touched. This is narrower than {@link String#toUpperCase()}.
   *
   * <p>Returns {@link #self()} without allocating when this sequence contains no
   * lowercase ASCII letters.
   *
   * @see String#toUpperCase()
   * @since 1.14
   */
  default T toUpperCase() {
    return toCase(CharArray::isLowerCase, -32);
  }

  private T toCase(CharPredicate test, int delta) {
    if (!contains(test)) return self();
    char[] buf = toCharArray();
    for (int i = 0; i < buf.length; i++)
      if (test.test(buf[i])) buf[i] += delta;
    return wrap(buf);
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
   * <p>Only affects a text that {@link #isIntegerValue()} — a plain
   * integer, optionally signed, or a decimal with an all-zero fraction. The output is canonical: no
   * sign on zero, no leading zeros, no trailing {@code .0+} fraction. A leading {@code '+'} or
   * {@code '-'} is consumed; a {@code '-'} is preserved on non-zero results, and dropped entirely
   * on zero.
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
   * @return this text reduced to its canonical integer value if (and only if) it {@link #isIntegerValue()}
   * @since 1.13
   */
  default T trimInteger() {
    if (!isIntegerValue()) return self();
    int len = length();
    if (len == 0) return self();
    boolean negative = charAt(0) == '-';
    int start = (negative || charAt(0) == '+') ? 1 : 0;
    int dot = indexOf('.', start);
    int intEnd = dot < 0 ? len : dot;
    while (start < intEnd - 1 && charAt(start) == '0') start++;
    // An all-zero integer part collapses to "0" regardless of sign.
    // Return the current '0' as a view rather than allocating via wrap('0').
    if (intEnd - start == 1 && charAt(start) == '0') return subSequence(start, start + 1);
    if (!negative || start == 1) {
      // non-negative (with or without a consumed '+'): result is the digit slice
      // negative with no leading zeros stripped: result is [0, intEnd), starting at the '-'
      int from = negative ? 0 : start;
      if (from == 0 && intEnd == len) return self();
      return subSequence(from, intEnd);
    }
    // negative with leading zeros stripped from the digits: rebuild as "-"+digits
    // in a single buffer (previous wrap('-').concat(digits) allocated twice)
    int digitsLen = intEnd - start;
    char[] buffer = new char[1 + digitsLen];
    buffer[0] = '-';
    copyTo(buffer, 1, start, digitsLen);
    return wrap(buffer);
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
   * @since 1.13
   */
  static boolean isTrim(char ch) {
    return ch == ' ' || ch == '\t';
  }

  /**
   * The {@link CharPredicate} for {@link #strip()}.
   *
   * @param ch character to test
   * @return true for ASCII whitespace
   * @since 1.13
   */
  static boolean isStrip(char ch) {
    return CharSeq.isBlank(ch);
  }

  /**
   * @param ch character to test
   * @return true for ASCII {@code 'A'}..{@code 'Z'}
   * @since 1.14
   */
  static boolean isUpperCase(char ch) {
    return 'A' <= ch && ch <= 'Z';
  }

  /**
   * @param ch character to test
   * @return true for ASCII {@code 'a'}..{@code 'z'}
   * @since 1.14
   */
  static boolean isLowerCase(char ch) {
    return 'a' <= ch && ch <= 'z';
  }
}
