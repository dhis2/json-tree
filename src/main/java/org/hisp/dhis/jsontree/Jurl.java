package org.hisp.dhis.jsontree;

import static org.hisp.dhis.jsontree.Chars.expectChar;
import static org.hisp.dhis.jsontree.JsonFormatException.expected;

import java.util.function.Consumer;

/**
 * <b>JURL</b> is JSON for the URL.
 * <p>
 * See details at {@code JURL.md}.
 *
 * @author Jan Bernitt
 * @since 1.9
 */
public interface Jurl {

  /**
   * @param jurl JURL notation data
   * @return the input converted to a JSON exposed as {@link JsonMixed}
   * @throws JsonFormatException in case the input is invalid JURL
   */
  static JsonMixed of(String jurl) {
    if (jurl == null || jurl.isEmpty() || jurl.isBlank()) return Json.ofNull();
    char[] chars = jurl.toCharArray();
    TextBuilder json = new TextBuilder(chars.length*2);
    int end = toJsonAutoDetect(chars, 0, json);
    if (end < chars.length) throw expected("<end-of-input>", chars, end);
    return JsonMixed.of(json);
  }

  /*
  Builder
   */

  static String createObject(Consumer<JsonBuilder.JsonObjectBuilder> obj) {
    return createObject(MINIMAL, obj);
  }

  static String createObject(Format format, Consumer<JsonBuilder.JsonObjectBuilder> obj) {
    JurlBuilder res = new JurlBuilder(format);
    res.addObject(obj);
    return res.toString();
  }

  static String createArray(Consumer<JsonBuilder.JsonArrayBuilder> arr) {
    return createArray(MINIMAL, arr);
  }

  static String createArray(Format format, Consumer<JsonBuilder.JsonArrayBuilder> arr) {
    JurlBuilder res = new JurlBuilder(format);
    res.addArray(arr);
    return res.toString();
  }

  record Format(boolean booleanShorthands, Nulls nullsInArrays, Nulls nullsInObjects) {
    enum Nulls {
      PLAIN("null"),
      SHORTHAND("n"),
      EMPTY(""),
      OMIT(null);
      final String value;

      Nulls(String value) {
        this.value = value;
      }
    }
  }

  /**
   * Uses shorthands, object members with null values
   */
  Format MINIMAL = new Format(true, Format.Nulls.SHORTHAND, Format.Nulls.OMIT);
  /**
   * No shorthands, nulls plain (as in JSON)
   */
  Format STANDARD = new Format(false, Format.Nulls.PLAIN, Format.Nulls.PLAIN);


  /*
  Transcoder to JSON
   */

  private static int toJsonAutoDetect(char[] jurl, int offset, TextBuilder json) {
    if (offset >= jurl.length) throw expected("<value>", jurl, offset);
    return switch (jurl[offset]) {
      case '(' -> toJsonContainer(jurl, offset, json);
      case '\'' -> toJsonString(jurl, offset, json);
      case 't', 'f', '-', '.', 'n' -> toJsonPrimitive(jurl, offset, json);
      default -> {
        if (isUnquotedString(jurl[offset])) yield toJsonPrimitive(jurl,offset, json);
        throw expected("<value>", jurl, offset);
      }
    };
  }

  /**
   * Either an array or an object
   */
  private static int toJsonContainer(char[] jurl, int offset, TextBuilder json) {
    if (offset + 1 >= jurl.length) throw expected("<container>", jurl, offset);
    char c = jurl[offset + 1];
    // () => empty array
    if (c == ')') {
      json.appendAscii("[]");
      return offset+2;
    }
    // (,) => empty array
    if (c == ',' && offset+2 < jurl.length && jurl[offset+2] == ')') {
      json.appendAscii("[]");
      return offset+3;
    }
    // omitted null at start of an array or another level of array or object
    if (c == ',' || c == '(') return toJsonArray(jurl, offset, json);
    // a string value => array element
    if (c == '\'') return toJsonArray(jurl, offset, json);
    // we need to skip the unreserved to see if we find :
    int i = offset+1;
    while (i < jurl.length && isUnquotedString(jurl[i])) i++;
    if (i >= jurl.length) throw expected("<end-of-container>", jurl, i);
    c = jurl[i]; // skipping letter found , or ) => must be an array
    if (c == ',' || c == ')') return toJsonArray(jurl, offset, json);
    return toJsonObject(jurl, offset, json);
  }

  private static int toJsonObject(char[] jurl, int offset, TextBuilder json) {
    int i = expectChar(jurl, offset, '(');
    json.appendAscii('{');
    while (true) {
      if (i >= jurl.length) throw expected("<member-name>", jurl, i);
      char c = jurl[i];
      if (!isUnquotedString(c)) throw expected("<member-name>", jurl, i);
      json.appendAscii('"');
      while (i < jurl.length && isUnquotedString(jurl[i])) json.appendAscii(jurl[i++]);
      json.appendAscii('"');
      i = expectChar(jurl, i, ':');
      json.appendAscii(':');
      if (i >= jurl.length) throw expected("<member-value>", jurl, i);
      c = jurl[i];
      if (c == ',' || c ==')') {
        json.appendAscii("null");
      } else i = toJsonAutoDetect(jurl, i, json);
      if (i >= jurl.length) throw expected("<end-of-object>", jurl, i);
      c = jurl[i];
      if (c == ')') {
        json.appendAscii('}');
        return i + 1;
      }
      if (c != ',' || i + 1 >= jurl.length) throw expected("<end-of-object>", jurl, i);
      if (jurl[i + 1] == ')') { // dangling ,
        json.appendAscii('}');
        return i + 2;
      }
      json.appendAscii(',');
      i++; // skip ,
    }
  }

  private static int toJsonArray(char[] jurl, int offset, TextBuilder json) {
    int i = expectChar(jurl, offset, '(');
    json.appendAscii('[');
    while (true) {
      if (i >= jurl.length) throw expected("<value>", jurl, i);
      char c = jurl[i];
      if (c == ',' || c == ')') {
        json.appendAscii("null");
      } else i = toJsonAutoDetect(jurl, i, json);
      if (i >= jurl.length) throw expected("<end-of-array>", jurl, i);
      c = jurl[i];
      if (c == ')') {
        json.appendAscii(']');
        return i + 1;
      }
      if (c != ',' || i + 1 >= jurl.length) throw expected("<end-of-array>", jurl, i);
      if (jurl[i + 1] == ')') { // dangling ,
        json.appendAscii(']');
        return i + 2;
      }
      json.appendAscii(',');
      i++; // skip ,
    }
  }

  private static int toJsonString(char[] jurl, int offset, TextBuilder json) {
    int i = expectChar(jurl, offset, '\'');
    json.appendAscii('"');
    while (i < jurl.length) {
      char c = jurl[i++];
      if (c == '\'') {
        json.appendAscii('"');
        return i; // found end of string literal
      } else if (c == '~') {
        if (i >= jurl.length) throw expected("<escaped-character>", jurl, i);
        switch (jurl[i++]) {
          case '~' -> json.appendAscii('~');
          case 'b' -> json.appendAscii("\\\\");
          case 'a', 'P' -> json.appendAscii('&');
          case 'e', 'g' -> json.appendAscii('=');
          case 'p', 'U' -> json.appendAscii('+');
          case 'h', 'M' -> json.appendAscii('#');
          case 'c', 'O' -> json.appendAscii('%');
          case 'q', 'Q' -> json.appendAscii('\'');
          case 'd', 'L' -> json.appendAscii("\\\"");
          case 's', 'J' -> json.appendAscii(' ');
          // ~XX — first UTF-8 byte
          default -> i = toJsonStringHexEscaped(jurl, i, json);
        }
      } else if (isQuotedString(c)) {
        json.appendAscii(c); // these are also all allowed plain in JSON
      } else throw expected("<url-safe-character>", jurl, i-1);
    }
    throw expected('\'', jurl, i);
  }

  private static int toJsonStringHexEscaped(char[] jurl, int offset, TextBuilder json) {
    int i = offset;
    if (!isHexDigit(jurl[i -1]) || i >= jurl.length || !isHexDigit(jurl[i]))
      throw expected("<hex-digits>", jurl, i -1);
    int b0 = hexValue(jurl[i -1]) * 16 + hexValue(jurl[i++]);
    if (b0 < 0x80) {
      appendJsonChar(json, (char) b0);
    } else if ((b0 & 0xE0) == 0xC0) {
      int b1 = readContinuation(jurl, i); i += 3;
      int cp = ((b0 & 0x1F) << 6) | (b1 & 0x3F);
      json.appendCodePoint(cp);
    } else if ((b0 & 0xF0) == 0xE0) {
      int b1 = readContinuation(jurl, i); i += 3;
      int b2 = readContinuation(jurl, i); i += 3;
      int cp = ((b0 & 0x0F) << 12) | ((b1 & 0x3F) << 6) | (b2 & 0x3F);
      if (cp >= 0xD800 && cp <= 0xDFFF) cp = 0xFFFD;   // invalid UTF-8 → replacement
      json.appendCodePoint(cp);
    } else if ((b0 & 0xF8) == 0xF0) {
      int b1 = readContinuation(jurl, i); i += 3;
      int b2 = readContinuation(jurl, i); i += 3;
      int b3 = readContinuation(jurl, i); i += 3;
      int cp = ((b0 & 0x07) << 18) | ((b1 & 0x3F) << 12) | ((b2 & 0x3F) << 6) | (b3 & 0x3F);
      json.appendCodePoint(cp);
    } else {
      throw expected("<utf8-sequence>", jurl, i -1);
    }
    return i;
  }

  /**
   * Writes a single decoded character into the JSON being built, applying JSON string escaping
   * where needed. Only the single-byte UTF-8 branch can produce characters that require escaping
   * here (multi-byte branches always yield codepoints {@code >= 0x80}).
   */
  private static void appendJsonChar(TextBuilder json, char c) {
    switch (c) {
      case '"'  -> json.appendAscii("\\\"");
      case '\\' -> json.appendAscii("\\\\");
      case '\n' -> json.appendAscii("\\n");
      case '\r' -> json.appendAscii("\\r");
      case '\t' -> json.appendAscii("\\t");
      case '\b' -> json.appendAscii("\\b");
      case '\f' -> json.appendAscii("\\f");
      default -> {
        if (c < 0x20) {
          json.appendAscii("\\u00");
          json.appendAscii(toHexDigit((c >> 4) & 0xF));
          json.appendAscii(toHexDigit(c & 0xF));
        } else {
          json.appendAscii(c);
        }
      }
    }
  }

  private static char toHexDigit(int value) {
    return value < 10 ? (char) ('0' + value) : (char) ('A' + (value - 10));
  }

  private static int readContinuation(char[] jurl, int pos) {
    if (pos + 2 >= jurl.length
        || jurl[pos] != '~'
        || !isHexDigit(jurl[pos + 1])
        || !isHexDigit(jurl[pos + 2]))
      throw expected("<utf8-continuation>", jurl, pos);
    int b = hexValue(jurl[pos + 1]) * 16 + hexValue(jurl[pos + 2]);
    if ((b & 0xC0) != 0x80) throw expected("<utf8-continuation-byte>", jurl, pos);
    return b;
  }

  private static int toJsonPrimitive(char[] jurl, int offset, TextBuilder json) {
    int i = offset; // we got here from autodetect, so it is safe and a isUrlUnreserved
    while (i < jurl.length && isUnquotedString(jurl[i])) i++;
    int len = i - offset;
    i = offset;
    char c0 = jurl[i];
    // literal ?
    if (len == 1) {
      if (c0 == 't') {
        json.appendAscii("true");
        return offset + 1;
      } else if (c0 == 'f') {
        json.appendAscii("false");
        return offset + 1;
      }
      if (c0 == 'n') {
        json.appendAscii("null");
        return offset + 1;
      }
    } else if (len == 4) {
      if (c0 == 't' && jurl[i+1] == 'r' && jurl[i+2] == 'u' && jurl[i+3] == 'e') {
        json.appendAscii("true");
        return offset+4;
      } else if (c0 == 'n' && jurl[i+1] == 'u' && jurl[i+2] == 'l' && jurl[i+3] == 'l') {
        json.appendAscii("null");
        return offset+4;
      }
    } else if (len == 5 && c0 == 'f' && jurl[i+1] == 'a' && jurl[i+2] == 'l' && jurl[i+3] == 's' && jurl[i+4] == 'e') {
      json.appendAscii("false");
      return offset+5;
    }
    // else => number
    if (isNumber(jurl, offset)) return toJsonNumber(jurl, offset, json);
    // else => string
    json.appendAscii('"');
    for (int j = 0; j < len; j++) json.appendAscii(jurl[offset + j]);
    json.appendAscii('"');
    return offset+len;
  }

  private static int toJsonNumber(char[] jurl, int offset, TextBuilder json) {
    char c = jurl[offset]; // we got here from autodetect so it is safe
    // sign
    if (c == '-') {
      json.appendAscii('-');
      return toJsonNumber(jurl,offset + 1, json);
    }
    // integer part
    int i = offset;
    if (c == '.') {
      json.appendAscii('0'); // insert leading zero
    } else {
      while (i < jurl.length && isDigit(jurl[i])) json.appendAscii(jurl[i++]);
    }
    if (i >= jurl.length) return i; // integer number
    c = jurl[i];
    // fraction part ?
    if (c == '.') {
      i++; // skip .
      json.appendAscii('.');
      if (i >= jurl.length || !isDigit(jurl[i])) {
        json.appendAscii("0"); // auto-add zero after decimal point when no digits follow
        return i;
      }
      while (i < jurl.length && isDigit(jurl[i])) json.appendAscii(jurl[i++]);
    }
    if (i >= jurl.length) return i; // decimal number
    // exponent part ?
    c = jurl[i];
    if (c != 'e' && c != 'E') return i; // no exponent
    i++; // skip e/E
    json.appendAscii('e');
    if (i >= jurl.length) throw expected("<exponent>", jurl, i);
    c = jurl[i];
    if (c == '-') {
      i++; // skip -
      json.appendAscii('-');
    }
    // this would transfer zero digits here -> fine, JSON parsing will find it
    while (i < jurl.length && isDigit(jurl[i])) json.appendAscii(jurl[i++]);
    return i;
  }

  /**
   * {@code . digit+ exponent?} or {@code digit+ [. digit* exponent?]}
   */
  private static boolean isNumber(char[] jurl, int offset) {
    int i = offset;
    if (jurl[i] == '-') i++; // skip past - sign
    if (i >= jurl.length) return false; // just a -
    char c = jurl[i];
    // . digit+ [exponent]
    if (c == '.') return isDecimal(jurl, i);
    // digit+ [. [ digit* [exponent]]]
    int s = i;
    while (i < jurl.length && isDigit(jurl[i])) i++;
    if (i == s) return false; // no digits
    // end of unquoted?
    if (i >= jurl.length || !isUnquotedString(jurl[i])) return true;
    // just a trailing . ?
    if (jurl[i] == '.' && (i+1 >= jurl.length || !isUnquotedString(jurl[i+1]))) return true;
    // full decimal?
    return isDecimal(jurl, i);
  }

  /**
   * {@code [. digit+ exponent?]}
   */
  private static boolean isDecimal(char[] jurl, int offset) {
    int i = offset;
    if (i >= jurl.length || jurl[i] != '.') return false;
    i++; // skip .
    int s = i;
    while (i < jurl.length && isDigit(jurl[i])) i++;
    return i > s && (i >= jurl.length || !isUnquotedString(jurl[i]) || isExponent(jurl, i));
  }

  /**
   * {@code e/E digit+}
   */
  private static boolean isExponent(char[] jurl, int offset) {
    int i = offset;
    if (i >= jurl.length || (jurl[i] != 'e' && jurl[i] != 'E')) return false;
    i++; // skip e/E
    if (i >= jurl.length) return false; // just an e/E
    char c = jurl[i];
    if (c == '-') i++; // skip -
    int s = i;
    while (i < jurl.length && isDigit(jurl[i])) i++;
    return i > s && (i >= jurl.length || !isUnquotedString(jurl[i]));
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isHexDigit(char c) {
    return isDigit(c) || (c >= 'A' && c <= 'F');
  }

  private static boolean isLetter(char c) {
    return isLowerLetter(c) || isUpperLetter(c);
  }

  private static boolean isUpperLetter(char c) {
    return c >= 'A' && c <= 'Z';
  }

  private static boolean isLowerLetter(char c) {
    return c >= 'a' && c <= 'z';
  }

  /**
   * The characters allowed in a quoted string
   */
  static boolean isQuotedString(char c) {
    // test most common first
    if (isLetter(c) || isDigit(c) || c == ' ') return true;
    // Ranges of consecutive punctuation
    if (c >= '\'' && c <= '*') return true; // ' ( ) *
    if (c >= ',' && c <= '/') return true; // , - . /
    // Isolated punctuation
    return c == '?' || c == '@' || c == ':' || c == ';' || c == '!' || c == '$' || c == '_'
        || c == '~';
  }

  /**
   * The characters allowed in an unquoted string or object member name
   */
  static boolean isUnquotedString(char c) {
    return isLetter(c) || isDigit(c) || c == '-' || c == '.' || c == '_' || c == '@';
  }

  private static int hexValue(char c) {
    if (c >= '0' && c <= '9') return c-'0';
    return 10 + (c-'A');
  }
}
