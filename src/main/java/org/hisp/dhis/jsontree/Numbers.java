package org.hisp.dhis.jsontree;

/**
 * Parses numbers from a {@code char[]} efficiently.
 *
 * <p>Doubles that cannot be computed exactly from long division have to fall back to the slow
 * {@link Double#parseDouble(String)} but whenever that is not the case the methods provided here
 * are reasonably fast and allocation free.
 *
 * <p>Luckily most doubles encountered in practice are neither very small nor very large so long
 * division is exact and the parsing stays allocation free.
 *
 * @author Jan Bernitt
 * @since 1.9
 */
final class Numbers {

  private static final long MAX_SAFE_SIGNIFICAND = ((1L << 53) - 1);

  /** 22 because 5^22 < 2^53 (and 10^k = 2^k * 5^k with any 2^k being exact) */
  static final int MAX_EXP = 22;

  static final double[] POW10 = new double[MAX_EXP + 1];

  static {
    POW10[0] = 1.0;
    for (int i = 1; i <= MAX_EXP; i++) {
      POW10[i] = POW10[i - 1] * 10.0; // exact for i ≤ 22
    }
  }

  /**
   * @see Double#parseDouble(String)
   */
  static double parseDouble(char[] num) {
    return parseDouble(num, 0, num.length);
  }

  static double parseDouble(char[] num, int offset, int length) {
    int end = offset + length;
    // strip tailing whitespace
    while (length > 0 && num[offset + length - 1] <= ' ') length--;
    // strip leading whitespace
    while (offset < end && num[offset] <= ' ') {
      offset++;
      length--;
    }
    return parseDoubleNoBoundsCheck(num, offset, length);
  }

  static double parseDoubleNoBoundsCheck(char[] num, int offset, int length) {
    int i0 = offset;
    // test all easy cases fist
    if (length <= 0) throw nanNoDigits(num, i0, 0);
    // a single character must be an integer or invalid
    if (length == 1) return parseInt(num, i0, length);
    // a single decimal digit => parse as int / 10
    if (length == 2 && num[i0] == '.') return parseInt(num, i0 + 1, 1) / 10d;
    // NaN
    if (num[i0] == 'N') return parseDoubleNaN(num, i0, length);
    // Infinity
    if (num[i0] == 'I') return parseDoubleInfinity(num, i0, length);
    // -Infinity
    if (num[i0] == '-' && num[i0 + 1] == 'I') return -parseDoubleInfinity(num, i0 + 1, length - 1);

    // special case: all decimals are zeros or even all digits are zeros?
    if (num[i0 + length - 1] == '0') {
      int i = i0 + length - 1;
      while (i >= i0 && num[i] == '0') i--;
      if (i < i0) return 0d;
      if (num[i] == '.') {
        if (i0 == i) return 0d;
        if (i0 + 1 == i && num[i0] == '-') return -0d;
        if (i0 + 1 == i && num[i0] == '+') return 0d;
        int digits = i - i0;
        if (digits < 19) { // < 19 cannot overflow a long
          long n = parseLong(num, i0, digits);
          // OBS: double has -0 and 0, long does not, so we have to restore -
          return n == 0L && num[i0] == '-' ? -0d : n;
        }
      }
      if (i == i0 && num[i] == '-') return -0d;
      if (i == i0 && num[i] == '+') return 0d;
    }

    // some number then, lets find the exponent index
    int eOffset = skipToExponent(num, i0, length);
    // compute the significand
    long significand = 0;
    int digits = 0;
    int leadingZeros = 0;
    int decimals = 0;
    int i = i0;
    int end = eOffset < 0 ? i0 + length : offset + eOffset;
    boolean neg = num[i] == '-';
    if (neg || num[i] == '+') i++;
    boolean seenDot = false;
    for (; i < end; i++) {
      char c = num[i];
      if (c == '.') {
        if (seenDot) throw nanError(num, i0, length, "Number contains multiple decimal points: ");
        seenDot = true;
      } else if (isDigit(c)) {
        if (c == '0' && digits == leadingZeros) leadingZeros++;
        digits++;
        significand = significand * 10 + (c - '0');
        if (seenDot) decimals++;
      } else throw nanIllegalChar(num, i0, length, c);
    }
    if (digits == 0) throw nanError(num, i0, length, "Number has no digits before the exponent: ");
    // fast path:
    // simple conservative pre-condition: 1-15 significand digits, 1-4 exp characters
    int expLen = eOffset < 0 ? 0 : length - eOffset - 1;
    if (digits - leadingZeros <= 15 && expLen <= 4) {
      int exp = eOffset < 0 ? 0 : parseInt(num, offset + eOffset + 1, expLen);
      // compute effective exponent by subtracting decimal places in significand
      exp -= decimals;
      // final check for fast path: are significand and exponent in long division range?
      if (significand <= MAX_SAFE_SIGNIFICAND && Math.abs(exp) <= MAX_EXP) {
        double result =
            exp >= 0 ? (double) significand * POW10[exp] : (double) significand / POW10[-exp];
        return neg ? -result : result;
      }
    }
    // fallback
    return Double.parseDouble(new String(num, i0, length));
  }

  private static int skipToExponent(char[] num, int offset, int length) {
    int i = 0;
    while (i < length && (num[offset + i] | 0b10_0000) != 'e') i++;
    return i < length ? i : -1;
  }

  private static double parseDoubleNaN(char[] num, int offset, int length) {
    if (length != 3 || num[offset + 1] != 'a' || num[offset + 2] != 'N')
      throw nanError(num, offset, length, "Number contains illegal literal: ");
    return Double.NaN;
  }

  private static double parseDoubleInfinity(char[] num, int offset, int length) {
    if (length != 8
        || num[offset + 1] != 'n'
        || num[offset + 2] != 'f'
        || num[offset + 3] != 'i'
        || num[offset + 4] != 'n'
        || num[offset + 5] != 'i'
        || num[offset + 6] != 't'
        || num[offset + 7] != 'y')
      throw nanError(num, offset, length, "Number contains illegal literal: ");
    return Double.POSITIVE_INFINITY;
  }

  /**
   * @see Integer#parseInt(String)
   */
  static int parseInt(char[] num) {
    return parseInt(num, 0, num.length);
  }

  static int parseInt(char[] num, int offset, int length) {
    if (length <= 0) throw nanNoDigits(num, offset, 0);
    boolean neg = num[offset] == '-';
    int i = offset;
    if (neg || num[offset] == '+') i++;
    int end = offset + length;
    if (i >= end) throw nanNoDigits(num, offset, length);
    while (i < end && num[i] == '0') i++; // skip leading zeros
    int digits = length - (i - offset);
    if (digits == 0) return 0;
    if (digits > 10 || digits == 10 && num[i] > '2') throw nanOverflow(num, offset, length, "int");
    int n = 0;
    for (; i < end; i++) {
      n *= 10;
      char d = num[i];
      if (!isDigit(d)) throw nanIllegalChar(num, offset, length, d);
      n += d - '0';
    }
    if (n < 0 && !(neg && -n == Integer.MIN_VALUE)) throw nanOverflow(num, offset, length, "int");
    return neg ? -n : n;
  }

  /**
   * @see Long#parseLong(String)
   */
  static long parseLong(char[] num) {
    return parseLong(num, 0, num.length);
  }

  static long parseLong(char[] num, int offset, int length) {
    if (length <= 0) throw nanNoDigits(num, offset, 0);
    boolean neg = num[offset] == '-';
    int i = offset;
    if (neg || num[offset] == '+') i++;
    int end = offset + length;
    if (i >= end) throw nanNoDigits(num, offset, length);
    while (i < end && num[i] == '0') i++; // skip leading zeros
    int digits = length - (i - offset);
    if (digits == 0) return 0L;
    if (digits > 19 || digits == 19 && num[i] == '9' && num[i + 1] > '2')
      throw nanOverflow(num, offset, length, "long");
    long n = 0;
    for (; i < end; i++) {
      n *= 10;
      char d = num[i];
      if (!isDigit(d)) throw nanIllegalChar(num, offset, length, d);
      n += d - '0';
    }
    if (n < 0 && !(neg && -n == Long.MIN_VALUE)) throw nanOverflow(num, offset, length, "long");
    return neg ? -n : n;
  }

  static Number parseNumber(char[] num) {
    return parseNumber(num, 0, num.length);
  }

  static Number parseNumber(char[] num, int offset, int length) {
    double number = parseDouble(num, offset, length);
    if (number % 1 != 0d) return number;
    long n = (long) number;
    if (n < Integer.MAX_VALUE && n > Integer.MIN_VALUE) return (int) n;
    return n;
  }

  static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  static boolean isSignedInteger(char[] json, int offset, int length) {
    if (length <= 0) return false;
    char sign = json[offset];
    int i = 0;
    if (sign == '-' || sign == '+') i++;
    if (i > 0 && length == 1) return false;
    for (; i < length; i++) if (!isDigit(json[offset + i])) return false;
    return true;
  }

  private static NumberFormatException nanError(char[] num, int offset, int length, String msg) {
    return new NumberFormatException(msg + new String(num, offset, length));
  }

  private static NumberFormatException nanNoDigits(char[] num, int offset, int length) {
    return nanError(num, offset, length, "Number has no digits: ");
  }

  private static NumberFormatException nanIllegalChar(char[] num, int offset, int length, char ch) {
    return nanError(num, offset, length, "Number contains illegal character `%s`: ".formatted(ch));
  }

  private static NumberFormatException nanOverflow(
      char[] num, int offset, int length, String type) {
    return nanError(num, offset, length, "Number overflows %s range: ".formatted(type));
  }
}
