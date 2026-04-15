package org.hisp.dhis.jsontree;

import static java.lang.Math.abs;

import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * A number represented by its text form.
 *
 * <p>This has 2 main advantages: maintains the exact information reading numbers from input and
 * re-writing those numbers later. Defers the parsing until it is known if the caller needs int,
 * long or double. Int and long being much easier problems and the parsing can be more efficiently.
 *
 * <p>For doubles this implementation checks if we have an easy case of a special double value or a
 * double value that can be computed exact from long division. Doubles that cannot be computed
 * exactly from long division have to fall back to the slow {@link Double#parseDouble(String)} but
 * whenever that is not the case the methods provided here are reasonably fast and allocation free.
 *
 * <p>Luckily most doubles encountered in practice are neither very small nor very large so long
 * division is exact and the parsing stays allocation free.
 *
 * @author Jan Bernitt
 * @since 1.9
 */
public final class TextualNumber extends Number implements Textual {

  public static Number of(char @NotNull [] buffer) {
    return of(buffer, 0, buffer.length);
  }

  public static Number of(char @NotNull [] buffer, int offset, int length) {
    if (length < 0) throw nanError(buffer, offset, length, "length must be >= 0");
    if (offset + length > buffer.length)
      throw new NumberFormatException("offset + length must be <= buffer.length");
    if (isTextualInteger(buffer, offset, length)) {
      // up to 9 digits is always in int range
      if (length <= 9) return parseIntExact(buffer, offset, length);
      // will overflow for sure
      if (length > 20) return new TextualNumber(buffer, offset, length);
      try {
        long value = parseLongExact(buffer, offset, length);
        if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) return (int) value;
        return value;
      } catch (ArithmeticException ex) {
        // we did overflow, fall back to let the user take it
        return new TextualNumber(buffer, offset, length);
      }
    }
    if (!isTextualDecimal(buffer, offset, length))
      throw nanError(buffer, offset, length, "Is neither a integer or decimal number");
    return new TextualNumber(buffer, offset, length);
  }

  private final char[] buffer;
  private final int offset;
  private final int length;

  private TextualNumber(char @NotNull [] buffer, int offset, int length) throws NumberFormatException {
    this.buffer = buffer;
    this.offset = offset;
    this.length = length;
  }

  @Override
  public @NotNull Text textValue() {
    return Text.of(buffer, offset, length);
  }

  @Override
  public String toString() {
    return new String(buffer, offset, length);
  }

  @Override
  public int hashCode() {
    int hash = 1;
    for (int i = 0; i < length; i++) hash = 31 * hash + buffer[offset+i];
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof TextualNumber n)) return false;
    if (length != n.length) return false;
    for (int i = 0; i < length; i++)
      if (buffer[offset+i] != n.buffer[n.offset+i]) return false;
    return true;
  }

  @Override
  public int intValue() {
    return parseIntCast(buffer, offset, length);
  }

  @Override
  public long longValue() {
    return parseLongCast(buffer, offset, length);
  }

  @Override
  public float floatValue() {
    return (float) doubleValue();
  }

  @Override
  public double doubleValue() {
    return parseDouble(buffer, offset, length);
  }

  static boolean isTextualDecimal(char[] buffer) {
    return isTextualDecimal(buffer, 0, buffer.length);
  }

  static boolean isTextualDecimal(char[] buffer, int offset, int length) {
    if (length == 0) return false;
    int eOffset = offsetExponent(buffer, offset, length);
    if (eOffset < 0) return isTextualDecimalBase(buffer, offset, length);
    int baseLength = eOffset - offset;
    return isTextualDecimalBase(buffer, offset, baseLength)
        && isTextualInteger(buffer, eOffset + 1, length - 1 - baseLength);
  }

  static boolean isTextualDecimalBase(char[] buffer, int offset, int length) {
    int i = offset;
    if (buffer[i] == '-' || buffer[i] == '+') i++; // sign
    int end = offset+length;
    int i0 = i;
    while (i < end && isDigit(buffer[i])) i++;
    boolean mDigits = i > i0;
    if (i == end) return mDigits;
    if (buffer[i] == '.') {
      i++; // skip .
      i0 = i;
      while (i < end && isDigit(buffer[i])) i++;
      boolean dDigits = i > i0;
      if (!mDigits && !dDigits) return false;
    }
    return i == end;
  }

  static boolean isTextualInteger(char[] buffer, int offset, int length) {
    if (length <= 0) return false;
    char sign = buffer[offset];
    int i = 0;
    if (sign == '-' || sign == '+') i++;
    if (i > 0 && length == 1) return false;
    for (; i < length; i++) if (!isDigit(buffer[offset + i])) return false;
    return true;
  }

  static boolean isNumericInteger(char[] buffer) {
    return isNumericInteger(buffer, 0, buffer.length);
  }

  static boolean isNumericInteger(char[] buffer, int offset, int length) {
    if (length <= 0) return false;
    char sign = buffer[offset];
    int i = offset;
    if (sign == '-' || sign == '+') i++;
    if (i > offset && length == 1) return false;
    int end = offset + length;
    int d0 = i;
    while (i < end && isDigit(buffer[i])) i++;
    if (i == d0) return false;
    if (i == end) return true;
    if (buffer[i++] != '.') return false;
    while (i < end && buffer[i] == '0') i++;
    return i == end;
  }

  private static boolean allDigits(char[] buffer, int offset, int length) {
    int end = offset+length;
    for (int i = offset; i < end; i++)
      if (!isDigit(buffer[i])) return false;
    return true;
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }



  /**
   * Make it an int, no matter what (same as cast semantics)
   *
   * @see #parseIntExact(char[], int, int)
   * @return the int value of the given number, possibly truncating decimal points, possibly
   *     overflowing
   */
  static int parseIntCast(char[] buffer, int offset, int length) {
    int len = lengthInsignificantZeros(buffer, offset, length);
    offset += len;
    length -= len;
    if (isTextualInteger(buffer, offset, length))
        return parseInt(buffer, offset, length, true);
    if (offsetExponent(buffer, offset, length) < 0) {
      int dpOffset = offsetDecimalPoint(buffer, offset, length);
      if (dpOffset > 0)
        return parseIntCast(buffer, offset, length - (dpOffset - offset));
    }
    return (int) parseDouble(buffer, offset, length);
  }

  /**
   * Make it a long, no matter what (same as cast semantics)
   *
   * @see #parseLongExact(char[], int, int)
   * @return the long value of the given number, possibly truncating decimal points, possibly overflowing
   */
  static long parseLongCast(char[] buffer, int offset, int length) {
    int len = lengthInsignificantZeros(buffer, offset, length);
    offset += len;
    length -= len;
    if (isTextualInteger(buffer, offset, length))
      return parseLong(buffer, offset, length, true);
    if (offsetExponent(buffer, offset, length) < 0) {
      int dpOffset = offsetDecimalPoint(buffer, offset, length);
      if (dpOffset > 0)
        return parseLongCast(buffer, offset, length - (dpOffset - offset));
    }
    return (long) parseDouble(buffer, offset, length);
  }

  /**
   * Make it a double, but be a bit clever about it for integer numbers.
   *
   * @return the double value of the given number (possibly cast from an int or long if no decimal
   *     or exponential part was present and if the base was in range)
   */
  static double parseDoubleCast(char[] buffer, int offset, int length) {
    int len = lengthInsignificantZeros(buffer, offset, length);
    offset += len;
    length -= len;
    if (isTextualInteger(buffer, offset, length)) {
      if (length <= 9) {
        int val = parseIntExact(buffer, offset, length);
        if (val != 0) return val;
        return buffer[offset] == '-' ? -0d : 0d;
      }
      if (length <= 18) {
        long val = parseLongExact(buffer, offset, length);
        if (val != 0L) return val;
        return buffer[offset] == '-' ? -0d : 0d;
      }
    }
    return parseDouble(buffer, offset, length);
  }

  static double parseDoubleCast(char[] buffer) {
    return parseDoubleCast(buffer, 0, buffer.length);
  }

  /**
   * @param significandLength number of significant digit characters (not counting leading zeros)
   * @param expLength number of characters in the exponent (including sign)
   * @return true, if the number defined by the given number of characters is always exact when
   *     computing a double from long division.
   */
  private static boolean isExactDouble(int significandLength, int expLength) {
    return significandLength <= 15 && expLength <= 4;
  }

  private static boolean isExactDouble(long significand, int exp) {
    return abs(significand) <= MAX_SAFE_SIGNIFICAND && abs(exp) <= MAX_EXP;
  }

  /*
  Number parsing details
   */

  private static final long MAX_SAFE_SIGNIFICAND = ((1L << 53) - 1);

  /** 22 because 5^22 < 2^53 (and 10^k = 2^k * 5^k with any 2^k being exact) */
  static final int MAX_EXP = 22;

  static final double[] POW10 = new double[MAX_EXP + 1];

  static {
    POW10[0] = 1.0;
    for (int i = 1; i <= MAX_EXP; i++)
      POW10[i] = POW10[i - 1] * 10.0; // exact for i ≤ 22
  }

  /**
   * @see Double#parseDouble(String)
   */
  private static double parseDouble(char[] buffer, int offset, int length) {
    // strip tailing whitespace
    while (length > 0 && buffer[offset + length - 1] <= ' ') length--;
    // strip leading whitespace
    int len = lengthInsignificantZeros(buffer, offset, length);
    offset += len;
    length -= len;
    return parseDoubleNoBoundsCheck(buffer, offset, length);
  }

  private static double parseDoubleNoBoundsCheck(char[] buffer, int offset, int length) {
    int i0 = offset;
    // test all easy cases fist
    if (length <= 0) throw nanNoDigits(buffer, i0, 0);
    // a single character must be an integer or invalid
    if (length == 1) return parseIntExact(buffer, i0, length);
    // a single decimal digit => parse as int / 10
    if (length == 2 && buffer[i0] == '.') return parseIntExact(buffer, i0 + 1, 1) / 10d;
    // NaN
    if (buffer[i0] == 'N') return parseDoubleNaN(buffer, i0, length);
    // Infinity
    if (buffer[i0] == 'I') return parseDoubleInfinity(buffer, i0, length);
    // -Infinity
    if (buffer[i0] == '-' && buffer[i0 + 1] == 'I') return -parseDoubleInfinity(buffer, i0 + 1, length - 1);

    // special case: all decimals are zeros or even all digits are zeros?
    if (buffer[i0 + length - 1] == '0') {
      int i = i0 + length - 1;
      while (i >= i0 && buffer[i] == '0') i--;
      if (i < i0) return 0d;
      if (buffer[i] == '.') {
        if (i0 == i) return 0d;
        if (i0 + 1 == i && buffer[i0] == '-') return -0d;
        if (i0 + 1 == i && buffer[i0] == '+') return 0d;
        int digits = i - i0;
        if (digits < 19) { // < 19 cannot overflow a long
          long n = parseLongExact(buffer, i0, digits);
          // OBS: double has -0 and 0, long does not, so we have to restore -
          return n == 0L && buffer[i0] == '-' ? -0d : n;
        }
      }
      if (i == i0 && buffer[i] == '-') return -0d;
      if (i == i0 && buffer[i] == '+') return 0d;
    }

    // some number then, lets find the exponent index
    int eOffset = offsetExponent(buffer, i0, length);
    // compute the significand
    long significand = 0;
    int digits = 0;
    int leadingZeros = 0;
    int decimals = 0;
    int i = i0;
    int end = eOffset < 0 ? i0 + length : eOffset;
    boolean neg = buffer[i] == '-';
    if (neg || buffer[i] == '+') i++;
    boolean seenDot = false;
    for (; i < end; i++) {
      char c = buffer[i];
      if (c == '.') {
        if (seenDot) throw nanError(buffer, i0, length, "Number contains multiple decimal points: ");
        seenDot = true;
      } else if (isDigit(c)) {
        if (c == '0' && digits == leadingZeros) leadingZeros++;
        digits++;
        significand = significand * 10 + (c - '0');
        if (seenDot) decimals++;
      } else throw nanIllegalChar(buffer, i0, length, c);
    }
    if (digits == 0) throw nanError(buffer, i0, length, "Number has no digits before the exponent: ");
    // fast path:
    // simple conservative pre-condition: 1-15 significand digits, 1-4 exp characters
    int expLen = eOffset < 0 ? 0 : length - 1 - (eOffset - offset);
    if (isExactDouble(digits - leadingZeros, expLen)) {
      int exp = eOffset < 0 ? 0 : parseIntExact(buffer, eOffset + 1, expLen);
      // compute effective exponent by subtracting decimal places in significand
      exp -= decimals;
      // final check for fast path: are significand and exponent in long division range?
      if (isExactDouble(significand, exp)) {
        double result =
              exp >= 0 ? (double) significand * POW10[exp] : (double) significand / POW10[-exp];
        return neg ? -result : result;
      }
    }
    // fallback
    return Double.parseDouble(new String(buffer, i0, length));
  }

  private static double parseDoubleNaN(char[] buffer, int offset, int length) {
    if (length != 3 || buffer[offset + 1] != 'a' || buffer[offset + 2] != 'N')
      throw nanError(buffer, offset, length, "Number contains illegal literal: ");
    return Double.NaN;
  }

  private static double parseDoubleInfinity(char[] buffer, int offset, int length) {
    if (length != 8
        || buffer[offset + 1] != 'n'
        || buffer[offset + 2] != 'f'
        || buffer[offset + 3] != 'i'
        || buffer[offset + 4] != 'n'
        || buffer[offset + 5] != 'i'
        || buffer[offset + 6] != 't'
        || buffer[offset + 7] != 'y')
      throw nanError(buffer, offset, length, "Number contains illegal literal: ");
    return Double.POSITIVE_INFINITY;
  }

  /**
   * @see Integer#parseInt(String)
   */
  static int parseIntExact(char[] buffer) {
    return parseIntExact(buffer, 0, buffer.length);
  }

  static int parseIntExact(char[] buffer, int offset, int length) {
      return parseInt(buffer, offset, length, false);
    }

  private static int parseInt(char[] buffer, int offset, int length, boolean overflow) {
    if (length <= 0) throw nanNoDigits(buffer, offset, 0);
    boolean neg = buffer[offset] == '-';
    int i = offset;
    if (neg || buffer[offset] == '+') i++;
    int end = offset + length;
    if (i >= end) throw nanNoDigits(buffer, offset, length);
    while (i < end && buffer[i] == '0') i++; // skip leading zeros
    int digits = length - (i - offset);
    if (digits == 0) return 0;
    if (!overflow && (digits > 10 || digits == 10 && buffer[i] > '2')) {
      if (!allDigits(buffer, offset+(length - digits), digits))
        throw nanError(buffer, offset, length, "Number contains illegal character(s)");
      throw overflow(buffer, offset, length, "int");
    }
    int n = 0;
    for (; i < end; i++) {
      n *= 10;
      char d = buffer[i];
      if (!isDigit(d)) throw nanIllegalChar(buffer, offset, length, d);
      n += d - '0';
    }
    if (!overflow && n < 0 && !(neg && -n == Integer.MIN_VALUE))
      throw overflow(buffer, offset, length, "int");
    return neg ? -n : n;
  }

  /**
   * @see Long#parseLong(String)
   */
  static long parseLongExact(char[] buffer) {
    return parseLongExact(buffer, 0, buffer.length);
  }

  static long parseLongExact(char[] buffer, int offset, int length) {
    return parseLong(buffer, offset, length, false);
  }

  private static long parseLong(char[] buffer, int offset, int length, boolean overflow) {
    if (length <= 0) throw nanNoDigits(buffer, offset, 0);
    boolean neg = buffer[offset] == '-';
    int i = offset;
    if (neg || buffer[offset] == '+') i++;
    int end = offset + length;
    if (i >= end) throw nanNoDigits(buffer, offset, length);
    while (i < end && buffer[i] == '0') i++; // skip leading zeros
    int digits = length - (i - offset);
    if (digits == 0) return 0L;
    if (!overflow && (digits > 19 || digits == 19 && buffer[i] == '9' && buffer[i + 1] > '2')) {
      if (!allDigits(buffer, offset+(length - digits), digits))
        throw nanError(buffer, offset, length, "Number contains illegal character(s)");
      throw overflow(buffer, offset, length, "long");
    }
    long n = 0;
    for (; i < end; i++) {
      n *= 10;
      char d = buffer[i];
      if (!isDigit(d)) throw nanIllegalChar(buffer, offset, length, d);
      n += d - '0';
    }
    if (!overflow && n < 0 && !(neg && -n == Long.MIN_VALUE))
      throw overflow(buffer, offset, length, "long");
    return neg ? -n : n;
  }

  private static int offsetExponent(char[] buffer, int offset, int length) {
    int i = offset;
    int end = offset+length;
    while (i < end && (buffer[i] | 0b10_0000) != 'e') i++;
    return i < end ? i : -1;
  }

  private static int offsetDecimalPoint(char[] buffer, int offset, int length) {
    int i = offset;
    int end = offset+length;
    while (i < end && buffer[i] != '.') i++;
    return i < end ? i : -1;
  }

  /** moves past leading whitespace and zeros that would not change the numeric value */
  private static int lengthInsignificantZeros(char[] buffer, int offset, int length) {
    int i = offset;
    int end = Math.min(buffer.length, offset + length);
    while (i < end && buffer[i] == ' ') i++;
    while (i < end - 1 && buffer[i] == '0' && isDigit(buffer[i + 1])) i++;
    return i - offset;
  }

  private static NumberFormatException nanError(char[] buffer, int offset, int length, String msg) {
    return new NumberFormatException(msg + new String(buffer, offset, length));
  }

  private static NumberFormatException nanNoDigits(char[] buffer, int offset, int length) {
    return nanError(buffer, offset, length, "Number has no digits: ");
  }

  private static NumberFormatException nanIllegalChar(char[] buffer, int offset, int length, char ch) {
    return nanError(buffer, offset, length, "Number contains illegal character `%s`: ".formatted(ch));
  }

  private static ArithmeticException overflow(
      char[] buffer, int offset, int length, String type) {
    return new ArithmeticException(
        "Number overflows %s range: ".formatted(type) + new String(buffer, offset, length));
  }
}
