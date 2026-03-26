package org.hisp.dhis.jsontree;

import static java.lang.Character.highSurrogate;
import static java.lang.Character.isBmpCodePoint;
import static java.lang.Character.lowSurrogate;
import static java.lang.System.Logger.Level.WARNING;
import static org.hisp.dhis.jsontree.JsonFormatException.expected;

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

  /*
  Parsing JSON encoded Strings
   */

  static Text unescapeJsonString(char[] json, int offset) {
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
        return unescapeJsonStringWithEscaping(json, offset + 1, length);
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
        throw expected("<non-control-code>", json, index - 1);
      }
      length++;
    }
    // throws...
    expectChar(json, index, '"');
    throw expected('"', json, index);
  }

  /**
   * @implNote When this runs we already know we find the end of the string, so some checks have
   *     been removed. The only checks required are code point decoding issues.
   */
  private static Text unescapeJsonStringWithEscaping(char[] json, int offset, int length) {
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
          default -> throw expected("<escaped>", json, index);
        }
      } else {
        text[i++] = c;
      }
    }
    // throws...
    expectChar(json, index, '"');
    throw expected('"', json, index);
  }

  private static int parseCodePoint(char[] json, int offset) {
    if (offset + 3 >= json.length) throw expected("[0-9a-f-A-F]", json, offset);
    int cp = 0;
    for (int i = 0; i < 4; i++) {
      char c = json[offset + i];
      int digit =
          switch (c) {
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> c - '0';
            case 'a', 'b', 'c', 'd', 'e', 'f' -> c - 'a' + 10;
            case 'A', 'B', 'C', 'D', 'E', 'F' -> c - 'A' + 10;
            default -> throw expected("[0-9a-f-A-F]", json, offset + i);
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
    if (offset >= json.length || !isDigit(json[offset])) throw expected("[0-9]", json, offset);
    return offset + 1;
  }

  static int expectChar(char[] json, int offset, char expected) {
    if (offset >= json.length || json[offset] != expected) throw expected(expected, json, offset);
    return offset + 1;
  }

  static void expectEscapableCharacter(char[] json, int offset) {
    if (offset >= json.length || !isEscapableCharacter(json[offset])) throw expected("<escaped>", json, offset);
  }

  private static boolean isEscapableCharacter(char c) {
    return c == '"' || c == '\\' || c == '/' || c == 'b' || c == 'f' || c == 'n' || c == 'r'
        || c == 't' || c == 'u';
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  static void expectEndOfBuffer(char[] json, int offset) {
    if (json.length > offset) throw expected("<end>", json, offset);
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
