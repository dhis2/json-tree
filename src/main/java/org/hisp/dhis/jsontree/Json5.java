package org.hisp.dhis.jsontree;

import static org.hisp.dhis.jsontree.Chars.expectChar;
import static org.hisp.dhis.jsontree.JsonFormatException.expected;
import static org.hisp.dhis.jsontree.JsonTree.expectColon;
import static org.hisp.dhis.jsontree.JsonTree.expectCommaOrEnd;
import static org.hisp.dhis.jsontree.JsonTree.isWhitespace;
import static org.hisp.dhis.jsontree.JsonTree.skipWhitespace;

/**
 * Actually, not quite <b>JSON5</b>.
 *
 * <p>Only supports syntax differences that can be re-witten to valid JSON without inserting
 * characters.
 *
 * <p>This means:
 *
 * <ul>
 *   <li>No unquoted object keys
 *   <li>No numbers with hexadecimal notation
 *   <li>No numbers with leading decimal point (unless there is space to the left)
 *   <li>No numbers with trailing decimal point
 *   <li>No number literals NaN, Infinity and -Infinity (unless they are enclosed in space)
 *   <li>No whitespace other than what is permitted by JSON
 *   <li>Double quotes in strings become single quotes
 * </ul>
 *
 * @author Jan Bernitt
 * @since 1.9
 */
public interface Json5 {
  /**
   * @param json5 JSON or JSON5 input
   * @return the input converted to JSON exposed as {@link JsonMixed}
   * @throws JsonFormatException in case the input is invalid JSON5 (though some validation might
   *     still be lazy and first occur on access)
   */
  static JsonMixed of(String json5) {
    return of(json5, JsonNode.Index.AUTO);
  }

  static JsonMixed of(String json5, JsonNode.Index auto) {
    char[] chars = json5.toCharArray();
    toJsonAutodetect(chars, skipWhitespace(chars, 0));
    return JsonMixed.of(JsonTree.of(chars, null, auto));
  }

  private static int toJsonAutodetect(char[] json5, int offset) {
    // validity of true/false/null will be validated by JSON parser later
    return switch (json5[offset]) {
      case '{' -> toJsonObject(json5, offset);
      case '[' -> toJsonArray(json5, offset);
      case '\'' -> toJsonString(json5, offset);
      case '"' -> JsonTree.skipString(json5, offset);
      case 't', 'n' -> offset + 4;
      case 'f' -> offset + 5;
      case '/' -> skipComment(json5, offset);
      default -> toJsonNumber(json5, offset);
    };
  }

  private static int toJsonObject(char[] json5, int offset) {
    int i = offset;
    i = expectChar(json5, i, '{');
    i = skipWhitespace(json5, i);
    while (i < json5.length && json5[i] != '}') {
      i = toJsonString(json5, i);
      i = expectColon(json5, i);
      i = toJsonAutodetect(json5, i);
      // blank dangling ,
      if (json5[i] == ',' && json5[i + 1] == '}') json5[i++] = ' ';
      i = expectCommaOrEnd(json5, i, '}');
    }
    return expectChar(json5, i, '}');
  }

  private static int toJsonArray(char[] json5, int offset) {
    int i = offset;
    i = expectChar(json5, i, '[');
    i = skipWhitespace(json5, i);
    while (i < json5.length && json5[i] != ']') {
      i = toJsonAutodetect(json5, i);
      // blank dangling ,
      if (json5[i] == ',' && json5[i + 1] == ']') json5[i++] = ' ';
      i = expectCommaOrEnd(json5, i, ']');
    }
    return expectChar(json5, i, ']');
  }

  private static int toJsonString(char[] json5, int offset) {
    int i = offset;
    if (json5[i] == '"') return JsonTree.skipString(json5, offset);
    i = expectChar(json5, i, '\'');
    json5[i - 1] = '"';
    while (i < json5.length && json5[i] != '\'') {
      if (json5[i] == '"' && json5[i - 1] != '\\') json5[i] = '\'';
      i++;
    }
    i = expectChar(json5, i, '\'');
    json5[i - 1] = '"';
    return i;
  }

  private static int toJsonNumber(char[] json5, int offset) {
    int i = offset;
    char c = json5[i]; // we know it exist at this point
    if (c == 'N') return toJsonQuotedNumberLiteral(json5, i, "NaN");
    if (c == 'I') return toJsonQuotedNumberLiteral(json5, i, "Infinity");
    if (c == '-' && i + 1 < json5.length && json5[i + 1] == 'I')
      return toJsonQuotedNumberLiteral(json5, i, "-Infinity");
    if (json5[i] == '+') {
      json5[i] = ' '; // blank leading +
      i++;
    }
    if (json5[i] == '.') {
      if (i == 0 || !(isWhitespace(json5[i - 1]) || json5[i - 1] == '+'))
        throw expected("<json5-leading-dot-left-padded>", json5, i);
      json5[i - 1] = '0'; // insert zero before decimal point
      i--;
    }
    return JsonTree.skipNumber(json5, skipWhitespace(json5, i));
  }

  private static int toJsonQuotedNumberLiteral(char[] json5, int offset, String literal) {
    int i = offset;
    if (i == 0 || !isWhitespace(json5[i - 1])) throw numberLiteralNoSpace(literal, json5, i);
    i = expectChars(literal, json5, i);
    if (!canInsertQuote(json5, i)) throw numberLiteralNoSpace(literal, json5, i);
    json5[offset - 1] = '"';
    insertEndQuote(json5, i);
    return i + 1;
  }

  private static int skipComment(char[] json5, int offset) {
    int i = offset;
    i = expectChar(json5, i, '/');
    if (i >= json5.length || (json5[i] != '/' && json5[i] != '*'))
      throw expected("[/*]", json5, i);
    json5[offset] = ' '; // blank first /
    if (json5[i] == '/') {
      while (i < json5.length && json5[i] != '\n') json5[i++] = ' ';
      return i;
    }
    while (i < json5.length - 1 && json5[i] != '*' && json5[i + 1] == '/') json5[i++] = ' ';
    if (i < json5.length - 1) {
      json5[i] = ' '; // blank /*
      json5[i + 1] = ' ';
    }
    return i;
  }

  private static int expectChars(String sequence, char[] json5, int offset) {
    int length = sequence.length();
    for (int i = 0; i < length; i++)
      if (offset+i > json5.length || json5[offset + i] != sequence.charAt(i))
        throw expected(sequence, json5, offset);
    return offset + length;
  }

  private static boolean canInsertQuote(char[] json5, int offset) {
    int i = offset;
    char c = json5[i];
    if (isWhitespace(c)) return true;
    return c == ','
        && i + 1 < json5.length
        && (json5[i + 1] == ']' || json5[i + 1] == '}' || isWhitespace(json5[i + 1]));
  }

  private static void insertEndQuote(char[] json5, int offset) {
    if (isWhitespace(json5[offset])) {
      json5[offset] = '"';
    } else {
      if (json5[offset] == ',' && isWhitespace(json5[offset + 1]))
        json5[offset + 1] = ','; // move comma
      json5[offset] = '"';
    }
  }

  private static JsonFormatException numberLiteralNoSpace(String literal, char[] json5, int offset) {
    return expected("<json5-%s-spaced>".formatted(literal), json5, offset);
  }
}
