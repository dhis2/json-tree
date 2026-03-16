package org.hisp.dhis.jsontree;

import static java.lang.Character.highSurrogate;
import static java.lang.Character.isBmpCodePoint;
import static java.lang.Character.lowSurrogate;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.System.Logger.Level.WARNING;
import static org.hisp.dhis.jsontree.JsonFormatException.insufficientCodePointCharacters;
import static org.hisp.dhis.jsontree.JsonFormatException.notAHexDigit;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.function.BiFunction;

/**
 * Utility class for {@code char[]} based helper functions.
 *
 * @author Jan Bernitt
 * @since 1.9
 */
final class Chars {

  private static final System.Logger log = System.getLogger(Chars.class.getName());

  static boolean parseBoolean(char[] val) {
    return parseBoolean(val, 0, val.length);
  }

  static boolean parseBoolean(char[] val, int offset, int length) {
    if (length < 4 || length > 5)
      throw new IllegalArgumentException("Not a boolean: " + new String(val, offset, length));
    int i = offset;
    int c0 = val[i] | 0b10_0000;
    if (c0 == 't') {
      if ((val[i + 1] | 0b10_0000) == 'r'
          && (val[i + 2] | 0b10_0000) == 'u'
          && (val[i + 3] | 0b10_0000) == 'e') return true;
    } else if (c0 == 'f') {
      if ((val[i + 1] | 0b10_0000) == 'a'
          && (val[i + 2] | 0b10_0000) == 'l'
          && (val[i + 3] | 0b10_0000) == 's'
          && (val[i + 4] | 0b10_0000) == 'e') return false;
    }
    throw new IllegalArgumentException("Not a boolean: " + new String(val, offset, length));
  }

  private static final long MAX_SAFE_SIGNIFICAND = ((1L << 53) - 1);

  /** 22 because 5^22 < 2^53 (and 10^k = 2^k * 5^k with any 2^k being exact) */
  private static final int MAX_EXP = 22;

  private static final double[] POW10 = new double[MAX_EXP + 1];

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
    if (length <= 0) throw nanNoDigits(num, i0, 0);
    if (length == 1) return parseInt(num, i0, length); // can only be a digit or invalid
    // .# ?
    if (length == 2 && num[i0] == '.') return parseInt(num, i0 + 1, 1) / 10d;
    // ###.0+ or just zeros?
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
    // NaN
    if (num[i0] == 'N') return parseDoubleNaN(num, i0, length);
    // Infinity
    if (num[i0] == 'I') return parseDoubleInfinity(num, i0, length);
    // -Infinity
    if (num[i0] == '-' && num[i0 + 1] == 'I') return -parseDoubleInfinity(num, i0 + 1, length - 1);

    // a longer number, find the exponent index
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
    // fast path requires max of 15 significand digits and only allows for exp up to 4 characters
    // including sign
    int expLen = eOffset < 0 ? 0 : length - eOffset - 1;
    if (digits - leadingZeros <= 15 && expLen <= 4) {
      int exp = eOffset < 0 ? 0 : parseInt(num, offset + eOffset + 1, expLen);
      // compute effective exponent
      exp -= decimals;
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

  private static boolean isDigit(char c) {
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

  /*
  Parsing JSON encoded Strings
   */

  static Text decodeString(char[] json, int offset) {
    int length = 0;
    int index = offset;
    index = expectChar(json, index, '"');
    while (index < json.length) {
      char c = json[index++];
      if (c == '"') {
        // found the end (if escaped we would have hopped over)
        // if str length is same as JSON raw characters length no escaping was used,
        // and we can use a direct view of the raw characters
        if (length == index - 2 - offset) return Text.of(json, offset + 1, length);
        // did use escaping...
        return decodeStringWithEscaping(json, offset + 1, length);
      } else if (c == '\\') {
        expectEscapableCharacter(json, index);
        // hop over escaped char or unicode
        if (json[index] == 'u') {
          int cp = parseCodePoint(json, index + 1);
          if (!isBmpCodePoint(cp)) length++; // needs 2
          index += 4; // XXXX
        }
        index += 1; // u or escaped char
      } else if (c < ' ') {
        throw new JsonFormatException(
            json,
            index - 1,
            "Control code character is not allowed in JSON string but found: " + (int) c);
      }
      length++;
    }
    // throws...
    expectChar(json, index, '"');
    throw new JsonFormatException("Invalid string");
  }

  /**
   * @implNote When this runs we already know we find the end of the string, so some checks have
   *     been removed. The only checks required are code point decoding issues.
   */
  private static Text decodeStringWithEscaping(char[] json, int offset, int length) {
    char[] text = new char[length];
    int i = 0;
    int index = offset;
    while (index < json.length) {
      char c = json[index++];
      if (c == '"') {
        // found the end (if escaped we would have hopped over)
        return Text.of(text, 0, length);
      }
      if (c == '\\') {
        switch (json[index++]) {
          case 'u' -> { // unicode uXXXX
            int cp = parseCodePoint(json, index);
            if (isBmpCodePoint(cp)) {
              text[i++] = (char) cp;
            } else {
              text[i++] = highSurrogate(cp);
              text[i++] = lowSurrogate(cp);
            }
            index += 4; // u we already skipped
          }
          case '\\' -> text[i++] = '\\';
          case '/' -> text[i++] = '/';
          case 'b' -> text[i++] = '\b';
          case 'f' -> text[i++] = '\f';
          case 'n' -> text[i++] = '\n';
          case 'r' -> text[i++] = '\r';
          case 't' -> text[i++] = '\t';
          case '"' -> text[i++] = '"';
          default -> throw new JsonFormatException(json, index, '?');
        }
      } else {
        text[i++] = c;
      }
    }
    // throws...
    expectChar(json, index, '"');
    throw new JsonFormatException("Invalid string");
  }

  private static int parseCodePoint(char[] json, int offset) {
    if (offset + 3 >= json.length)
      throw insufficientCodePointCharacters(json, offset);
    int cp = 0;
    for (int i = 0; i < 4; i++) {
      char c = json[offset + i];
      int digit =
          switch (c) {
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> c - '0';
            case 'a', 'b', 'c', 'd', 'e', 'f' -> c - 'a' + 10;
            case 'A', 'B', 'C', 'D', 'E', 'F' -> c - 'A' + 10;
            default -> throw notAHexDigit(json, offset + i);
          };
      cp = (cp << 4) | digit; // equivalent to cp = cp * 16 + digit
    }
    return cp;
  }

  /*
  Error handling
   */

  static int expectNull(char[] json, int offset) {
    expectChar(json, offset++, 'n');
    expectChar(json, offset++, 'u');
    expectChar(json, offset++, 'l');
    expectChar(json, offset++, 'l');
    return offset;
  }

  static int expectTrue(char[] json, int offset) {
    expectChar(json, offset++, 't');
    expectChar(json, offset++, 'r');
    expectChar(json, offset++, 'u');
    expectChar(json, offset++, 'e');
    return offset;
  }

  static int expectFalse(char[] json, int offset) {
    expectChar(json, offset++, 'f');
    expectChar(json, offset++, 'a');
    expectChar(json, offset++, 'l');
    expectChar(json, offset++, 's');
    expectChar(json, offset++, 'e');
    return offset;
  }

  static int expectDigit(char[] json, int offset) {
    expectMoreChar(json, offset);
    if (!isDigit(json[offset])) throw new JsonFormatException(json, offset, '#');
    return offset + 1;
  }

  static void expectMoreChar(char[] json, int offset) {
    if (offset >= json.length)
      throw new JsonFormatException(
          "Expected character but reached EOI: " + getEndSection(json, offset));
  }

  static int expectChar(char[] json, int offset, char expected) {
    if (offset >= json.length)
      throw new JsonFormatException(
          "Expected " + expected + " but reach EOI: " + getEndSection(json, offset));
    if (json[offset] != expected) throw new JsonFormatException(json, offset, expected);
    return offset + 1;
  }

  static void expectEscapableCharacter(char[] json, int offset) {
    expectMoreChar(json, offset);
    if (!isEscapableCharacter(json[offset]))
      throw new JsonFormatException(
          json, offset, "Illegal escaped string character: " + json[offset]);
  }

  private static boolean isEscapableCharacter(char c) {
    return c == '"' || c == '\\' || c == '/' || c == 'b' || c == 'f' || c == 'n' || c == 'r'
        || c == 't' || c == 'u';
  }

  static void expectEndOfBuffer(char[] json, int offset) {
    if (json.length > offset) {
      throw new JsonFormatException(
          "Unexpected input after end of root value: " + getEndSection(json, offset));
    }
  }

  private static String getEndSection(char[] json, int offset) {
    return new String(json, max(0, min(json.length, offset) - 20), min(20, json.length));
  }

  /*
  Reading binary inputs to char[]
   */

  /**
   * @implNote With lazy parsing one precondition is that we need to have the entire input in
   *     memory. Therefore, the general approach to IO is not to stream or buffer but to get the
   *     JSON into memory as efficient as possible. Mainly this is about avoiding extra intermediate
   *     representations and short-lived objects during the charset decoding.
   * @param bytes binary of a JSON file
   * @param encoding the encoding assumed
   * @return the character in the file
   */
  static char[] decode(byte[] bytes, Charset encoding) {
    return decode(bytes, encoding, (arr, length) -> arr);
  }

  static <T> T decode(byte[] bytes, Charset encoding, BiFunction<char[], Integer, T> wrap) {
      if (StandardCharsets.UTF_8.equals(encoding)) return decodeUTF8(bytes, wrap);
      if (StandardCharsets.ISO_8859_1.equals(encoding)) {
        char[] res = decodeIso88591(bytes);
        return wrap.apply(res, res.length);
      }
      char[] res = new String(bytes, encoding).toCharArray();
      return wrap.apply(res, res.length);
  }

  private static char[] decodeIso88591(byte[] src) {
    char[] dest = new char[src.length];
    for (int i = 0; i < src.length; i++) dest[i] = (char) (src[i] & 0xFF); // ISO‑8859‑1 / Latin‑1
    return dest;
  }

  private static <T> T decodeUTF8(byte[] src, BiFunction<char[], Integer, T> wrap) {
    int i = 0;
    int length = src.length;
    if (length >= 3
        && src[i] == (byte) 0xEF
        && src[i + 1] == (byte) 0xBB
        && src[i + 2] == (byte) 0xBF) {
      i = 3; // skip the BOM bytes
    }
    int errors = 0;
    int offset = 0;
    char[] dest = new char[length - i];
    while (i < length) {
      int b = src[i++] & 0xFF; // treat as unsigned
      if (b < 0x80) { // 0xxxxxxx (ASCII)
        dest[offset++] = (char) b;
      } else if ((b & 0xE0) == 0xC0) { // 110xxxxx → 2 bytes
        if (i < length) {
          int cp = ((b & 0x1F) << 6) | (src[i++] & 0x3F);
          dest[offset++] = (char) cp;
        } else {
          dest[offset++] = '�'; // Invalid UTF‑8, replacement character;
          errors++;
        }
      } else if ((b & 0xF0) == 0xE0) { // 1110xxxx → 3 bytes
        if (i + 1 < length) {
          int cp = ((b & 0x0F) << 12) | ((src[i++] & 0x3F) << 6) | (src[i++] & 0x3F);
          dest[offset++] = (char) cp;
        } else {
          i = length;
          dest[offset++] = '�'; // Invalid UTF‑8, replacement character;
          errors++;
        }
      } else if ((b & 0xF8) == 0xF0) { // 11110xxx → 4 bytes (supplementary)
        if (i + 2 < length) {
          int cp =
              ((b & 0x07) << 18)
                  | ((src[i++] & 0x3F) << 12)
                  | ((src[i++] & 0x3F) << 6)
                  | (src[i++] & 0x3F);
          // Convert to surrogate pair
          cp -= 0x10000;
          dest[offset++] = (char) (0xD800 | (cp >> 10));
          dest[offset++] = (char) (0xDC00 | (cp & 0x3FF));
        } else {
          i = length;
          dest[offset++] = '�'; // Invalid UTF‑8, replacement character;
          errors++;
        }
      } else {
        dest[offset++] = '�'; // Invalid UTF‑8, replacement character
        errors++;
      }
    }
    // over-allocated slots become space
    if (offset < dest.length) Arrays.fill(dest, offset, dest.length, ' ');
    if (errors > 0) log.log(WARNING, "UTF-8 contained %d illegal byte sequences".formatted(errors));
    return wrap.apply(dest, offset);
  }
}
