package org.hisp.dhis.jsontree;

import static java.lang.System.Logger.Level.WARNING;
import static org.hisp.dhis.jsontree.JsonFormatException.expected;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Utility class for {@code char[]} based helper functions.
 *
 * @author Jan Bernitt
 * @since 1.9
 */
final class CharDecoder {

  private static final System.Logger log = System.getLogger(CharDecoder.class.getName());

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
    if (StandardCharsets.UTF_8.equals(encoding)) return decodeUTF8(bytes);
    if (StandardCharsets.ISO_8859_1.equals(encoding) || StandardCharsets.US_ASCII.equals(encoding)) {
      return decodeIso88591(bytes);
    }
    // fall-back for encodings not directly handled
    return new String(bytes, encoding).toCharArray();
  }

  private static char[] decodeIso88591(byte[] src) {
    char[] dest = new char[src.length];
    for (int i = 0; i < src.length; i++) dest[i] = (char) (src[i] & 0xFF); // ISO‑8859‑1 / Latin‑1
    return dest;
  }

  private static char[] decodeUTF8(byte[] src) {
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
    int charCount = countCharsUTF8(src, i, length);
    // if all chars only need 1 byte we are dealing with ASCII only
    // and we can short-circuit to the simple loop
    if (charCount == length) return decodeIso88591(src);
    char[] dest = new char[charCount];
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
    if (errors > 0) log.log(WARNING, "UTF-8 contained %d illegal byte sequences".formatted(errors));
    return dest;
  }

  /**
   * Counts the exact number of {@code char}s the UTF-8 decoder will produce.
   *
   * <p>Uses the leading byte of each sequence to determine its byte-length and jumps
   * directly to the next code point, without inspecting continuation bytes. This
   * mirrors what {@link #decodeUTF8(byte[])} actually does: it also does
   * not validate continuation bytes, only checks that enough bytes remain. For
   * truncated sequences the decoder writes a single replacement char and stops; this
   * counter may count one extra char for a 4-byte sequence truncated at EOF, but
   * since the decoder's caller uses the returned write offset rather than the array
   * length, an over-estimate is harmless (it produces unused trailing slots that are
   * never read). An under-estimate would cause an ArrayIndexOutOfBoundsException;
   * this counter never under-counts.
   */
  private static int countCharsUTF8(byte[] src, int start, int length) {
    int count = 0;
    int i = start;
    while (i < length) {
      int b = src[i] & 0xFF;
      if (b < 0xC0) {          // 0xxxxxxx (ASCII) or 10xxxxxx (stray continuation)
        i += 1;
        count += 1;
      } else if (b < 0xE0) {   // 110xxxxx → 2-byte sequence
        i += 2;
        count += 1;
      } else if (b < 0xF0) {   // 1110xxxx → 3-byte sequence
        i += 3;
        count += 1;
      } else if (b < 0xF8) {   // 11110xxx → 4-byte sequence → surrogate pair
        i += 4;
        count += 2;
      } else {                 // 0xF8-0xFF: invalid lead byte (RFC 3629)
        i += 1;
        count += 1;
      }
    }
    return count;
  }
}
