package org.hisp.dhis.jsontree;

import static org.hisp.dhis.jsontree.Chars.expectChar;
import static org.hisp.dhis.jsontree.JsonFormatException.expected;

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
    toJsonAutoDetect(chars, 0, json);
    return JsonMixed.of(json);
  }

  //TODO test for filters as JSON with nesting

  private static int toJsonAutoDetect(char[] jurl, int offset, TextBuilder json) {
    if (offset >= jurl.length) throw expected("<value>", jurl, offset);
    return switch (jurl[offset]) {
      case '(' -> toJsonArrayOrObject(jurl, offset, json);
      case '\'' -> toJsonString(jurl, offset, json);
      case 't', 'f' -> toJsonBoolean(jurl, offset, json);
      case 'n' -> toJsonNull(jurl, offset, json);
      case '-', '.' -> toJsonNumber(jurl, offset, json);
      case ',' -> {
        json.append("null");
        yield offset+1;
      }
      default -> {
        if (isDigit(jurl[offset])) yield toJsonNumber(jurl, offset, json);
        if (isUrlUnreserved(jurl[offset])) yield toJsonStringUnquoted(jurl,offset, json);
        throw expected("<value>", jurl, offset);
      }
    };
  }

  private static int toJsonArrayOrObject(char[] jurl, int offset, TextBuilder json) {
    if (offset + 1 >= jurl.length) throw expected("<container>", jurl, offset);
    char c = jurl[offset + 1];
    // () => empty array
    if (c == ')') {
      json.append("[]");
      return offset+2;
    }
    // (,) => empty array
    if (c == ',' && offset+2 < jurl.length && jurl[offset+2] == ')') {
      json.append("[]");
      return offset+3;
    }
    // omitted null at start of an array or another level of array or object
    if (c == ',' || c == '(') return toJsonArray(jurl, offset, json);
    // a string value => array element
    if (c == '\'') return toJsonArray(jurl, offset, json);
    // we need to skip the unreserved to see if we find :
    int i = offset+1;
    while (i < jurl.length && isUrlUnreserved(jurl[i])) i++;
    if (i >= jurl.length) throw expected("<end-of-container>", jurl, i);
    c = jurl[i]; // skipping letter found , or ) => must be an array
    if (c == ',' || c == ')') return toJsonArray(jurl, offset, json);
    return toJsonObject(jurl, offset, json);
  }

  private static int toJsonObject(char[] jurl, int offset, TextBuilder json) {
    int i = expectChar(jurl, offset, '(');
    json.append('{');
    while (true) {
      if (i >= jurl.length) throw expected("<member-name>", jurl, i);
      char c = jurl[i];
      if (!isUrlUnreserved(c)) throw expected("<member-name>", jurl, i);
      json.append('"');
      while (i < jurl.length && isUrlUnreserved(jurl[i])) json.append(jurl[i++]);
      json.append('"');
      i = expectChar(jurl, i, ':');
      json.append(':');
      if (i >= jurl.length) throw expected("<member-value>", jurl, i);
      c = jurl[i];
      if (c == ',' || c ==')') {
        json.append("null");
      } else i = toJsonAutoDetect(jurl, i, json);
      if (i >= jurl.length) throw expected("<end-of-object>", jurl, i);
      c = jurl[i];
      if (c == ')') {
        json.append('}');
        return i + 1;
      }
      if (c != ',' || i + 1 >= jurl.length) throw expected("<end-of-object>", jurl, i);
      if (jurl[i + 1] == ')') { // dangling ,
        json.append('}');
        return i + 2;
      }
      json.append(',');
      i++; // skip ,
    }
  }

  private static int toJsonArray(char[] jurl, int offset, TextBuilder json) {
    int i = expectChar(jurl, offset, '(');
    json.append('[');
    while (true) {
      if (i >= jurl.length) throw expected("<value>", jurl, i);
      char c = jurl[i];
      if (c == ',' || c == ')') {
        json.append("null");
      } else i = toJsonAutoDetect(jurl, i, json);
      if (i >= jurl.length) throw expected("<end-of-array>", jurl, i);
      c = jurl[i];
      if (c == ')') {
        json.append(']');
        return i + 1;
      }
      if (c != ',' || i + 1 >= jurl.length) throw expected("<end-of-array>", jurl, i);
      if (jurl[i + 1] == ')') { // dangling ,
        json.append(']');
        return i + 2;
      }
      json.append(',');
      i++; // skip ,
    }
  }

  private static int toJsonString(char[] jurl, int offset, TextBuilder json) {
    int i = expectChar(jurl, offset, '\'');
    json.append('"');
    while (i < jurl.length) {
      char c = jurl[i++];
      if (c == '\'') {
        json.append('"');
        return i; // found end of string literal
      } else if (c == '~') {
        // escaped stuff
        if (i >= jurl.length) throw expected("<escaped-character>", jurl, i);
        switch (jurl[i++]) {
          case '~' -> json.append('~');
          case 'b' -> json.append('\\');
          case 'a', 'P' -> json.append('&');
          case 'e', 'g' -> json.append('=');
          case 'p', 'U' -> json.append('+');
          case 'h', 'M' -> json.append('#');
          case 'c', 'O' -> json.append('%');
          case 'q', 'Q' -> json.append("\\\\"); // backslash escaped for JSON
          case 'd', 'L' -> json.append("\\\""); // " escaped for JSON
          case 's', 'J' -> json.append(' ');
          default -> {
            // ~XX
            if (!isHexDigit(c) || i >= jurl.length || !isHexDigit(jurl[i]))
              throw expected("<escaped-character>", jurl, i);
            json.append((char)(hexValue(c)*16 + hexValue(jurl[i++])));
          }
        }
      } else if (isUrlSafeCharacter(c)) {
        json.append(c); // these are also all allowed plain in JSON
      } else throw expected("<url-safe-character>", jurl, i-1);
    }
    throw expected('\'', jurl, i);
  }

  private static int toJsonStringUnquoted(char[] jurl, int offset, TextBuilder json) {
    //TODO must check that true, false, null and valid numbers are not strings
    int i = offset; // we got here from autodetect, so it is safe and a isUrlUnreserved
    json.append('"');
    while (i < jurl.length && isUrlUnreserved(jurl[i])) json.append(jurl[i++]);
    json.append('"');
    return i;
  }

  private static int toJsonNumber(char[] jurl, int offset, TextBuilder json) {
    char c = jurl[offset]; // we got here from autodetect so it is safe
    // sign
    if (c == '-') {
      json.append('-');
      //TODO prevent --
      return toJsonNumber(jurl,offset + 1, json);
    }
    // integer part
    int i = offset;
    if (c == '.') {
      json.append('0'); // insert leading zero
    } else {
      while (i < jurl.length && isDigit(jurl[i])) json.append(jurl[i++]);
    }
    if (i >= jurl.length) return i; // integer number
    c = jurl[i];
    // fraction part ?
    if (c == '.') {
      i++; // skip .
      json.append('.');
      if (i >= jurl.length || !isDigit(jurl[i])) {
        json.append("0"); // auto-add zero after decimal point when no digits follow
        return i;
      }
      while (i < jurl.length && isDigit(jurl[i])) json.append(jurl[i++]);
    }
    if (i >= jurl.length) return i; // decimal number
    // exponent part ?
    c = jurl[i];
    if (c != 'e' && c != 'E') return i; // no exponent
    i++; // skip e/E
    json.append('e');
    if (i >= jurl.length) throw expected("<exponent>", jurl, i);
    c = jurl[i];
    if (c == '-') {
      i++; // skip -
      json.append('-');
    }
    // this would transfer zero digits here -> fine, JSON parsing will find it
    while (i < jurl.length && isDigit(jurl[i])) json.append(jurl[i++]);
    return i;
  }

  private static int toJsonBoolean(char[] jurl, int offset, TextBuilder json) {
    int i = offset;
    char c = jurl[i]; // we got here from autodetect so it is safe
    if (c == 't') {
      json.append("true");
      if (i+4 >= jurl.length) return offset+1;
      if (jurl[i+1] == 'r' && jurl[i+2] == 'u' && jurl[i+3] == 'e') return offset+4;
      return offset+1;
    } else {
      json.append("false");
      if (i+5 >= jurl.length) return offset+1;
      if (jurl[i+1] == 'a' && jurl[i+2] == 'l' && jurl[i+3] == 's' && jurl[i+4] == 'e') return offset+5;
      return offset+1;
    }
  }

  private static int toJsonNull(char[] jurl, int offset, TextBuilder json) {
    json.append("null");
    if (offset + 4 >= jurl.length) return offset+1; // can only be short form
    int i = offset;
    if (jurl[i+1] == 'u' && jurl[i+2] == 'l' && jurl[i+3] == 'l') return offset+4;
    return offset+1;
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isHexDigit(char c) {
    return isDigit(c) || c >= 'A' && c <= 'F';
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
   * Strictly a space is not URL safe, but it is changed to + and back on the URL encoder/decoder
   * level so from JURLs perspective it can be used plain.
   */
  private static boolean isUrlSafeCharacter(char c) {
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
   * In URL the "Unreserved" set contains {@code ~} and does not contain {@code @} but the idea here
   * is the same.
   */
  private static boolean isUrlUnreserved(char c) {
    return isLetter(c) || isDigit(c) || c == '-' || c == '.' || c == '_' || c == '@';
  }

  private static int hexValue(char c) {
    if (c >= '0' && c <= '9') return c-'0';
    return 10 + (c-'A');
  }
}
