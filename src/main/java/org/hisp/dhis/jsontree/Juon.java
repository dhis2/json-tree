package org.hisp.dhis.jsontree;

import java.util.function.Consumer;

import static org.hisp.dhis.jsontree.JsonFormatException.expected;

/**
 * JUON is short for JS URL Object Notation.
 * //TODO rename to JURL
 *
 * <p>JS here is used in the same vain as it is used in JSON. Meaning the fundamental types of JUON
 * are the same as those used by JSON.
 *
 * @author Jan Bernitt
 * @since 1.3
 */
record Juon(char[] juon, StringBuilder json) {

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

  public static final Format MINIMAL = new Format(true, Format.Nulls.EMPTY, Format.Nulls.OMIT);
  public static final Format PLAIN = new Format(false, Format.Nulls.PLAIN, Format.Nulls.PLAIN);

  public static String of(JsonValue value) {
    return of(value.node());
  }

  public static String of(JsonNode value) {
    return of(value, MINIMAL);
  }

  public static String of(JsonValue value, Format format) {
    return of(value.node(), format);
  }

  public static String of(JsonNode value, Format format) {
    return JuonAppender.toJuon(format, value);
  }

  static String createObject(Consumer<JsonBuilder.JsonObjectBuilder> obj) {
    return createObject(MINIMAL, obj);
  }

  static String createObject(Format format, Consumer<JsonBuilder.JsonObjectBuilder> obj) {
    JuonAppender res = new JuonAppender(format);
    res.addObject(obj);
    return res.toString();
  }

  static String createArray(Consumer<JsonBuilder.JsonArrayBuilder> arr) {
    return createArray(MINIMAL, arr);
  }

  static String createArray(Format format, Consumer<JsonBuilder.JsonArrayBuilder> arr) {
    JuonAppender res = new JuonAppender(format);
    res.addArray(arr);
    return res.toString();
  }

  /**
   * Converts JUON to JSON.
   *
   * @param juon the JUON input
   * @return the equivalent JSON
   * @since 1.3
   */
  public static String toJson(String juon) {
    if (juon == null || juon.isEmpty() || juon.isBlank()) return "null";
    StringBuilder json = new StringBuilder(juon.length() * 2);
    new Juon(juon.toCharArray(), json).toJsonAutoDetect(0);
    return json.toString();
  }

  private int toJsonAutoDetect(int index) {
    if (index >= juon.length) throw expected("<juon-value>", juon, index);
    return switch (juon[index]) {
      case '(' -> toJsonObjectOrArray(index);
      case '\'' -> toJsonString(index);
      case 't', 'f' -> toJsonBoolean(index);
      case 'n', ',' -> toJsonNull(index);
      case '-', '.' -> toJsonNumber(index);
      default -> {
        if (isDigit(juon[index])) yield toJsonNumber(index);
        throw expected("<juan-node>", juon, index);
      }
    };
  }

  private int toJsonObjectOrArray(int index) {
    int i = index;
    i++; // skip (
    while (isWhitespace(peek(i))) i++;
    char c = peek(i);
    // empty array or omitted null at start of an array or another level of array
    if (c == ')' || c == ',' || c == '(') return toJsonArray(index);
    // a number value => array element
    if (c == '-' || c == '.' || isDigit(c)) return toJsonArray(index);
    // a string value => array element
    if (c == '\'') return toJsonArray(index);
    // not a boolean or null value => assume object member name
    if (c != 't' && c != 'f' && c != 'n') return toJsonObject(index);
    // might be true/false/null 1st element in an array
    while (isLowerLetter(peek(i))) i++;
    c = peek(i); // what followed the lower letter word?
    if (c == ',' || c == ')') return toJsonArray(index);
    if (c == ':') return toJsonObject(index);
    throw expected("<object-or-array>", juon, index);
  }

  private int toJsonObject(int index) {
    index = toJsonSymbol(index, '(', '{');
    char c = ',';
    while (hasChar(index) && c == ',') {
      index = toJsonMemberName(index);
      index = toJsonSymbol(index, ':', ':');
      if (peek(index) == ')') { // trailing omitted null...
        append("null");
      } else {
        index = toJsonAutoDetect(index);
        index = toJsonWhitespace(index);
      }
      c = peek(index);
      if (c == ',') {
        index = toJsonSymbol(index, ',', ',');
      }
    }
    return toJsonSymbol(index, ')', '}');
  }

  private int toJsonArray(int index) {
    index = toJsonSymbol(index, '(', '[');
    char c = peek(index) == ')' ? ')' : ',';
    while (hasChar(index) && c == ',') {
      index = toJsonAutoDetect(index);
      index = toJsonWhitespace(index);
      c = peek(index);
      if (c == ',') {
        index = toJsonSymbol(index, ',', ',');
        if (peek(index) == ')') { // trailing omitted null...
          c = ')';
          append("null");
        }
      }
    }
    return toJsonSymbol(index, ')', ']');
  }

  private int toJsonMemberName(int index) {
    char c = peek(index);
    if (!isNameFirstChar(c))
      throw expected("<member-name>", juon, index);
    append('"');
    index = toJsonChar(index); // add start letter
    while (isNameChar(peek(index))) index = toJsonChar(index);
    append('"');
    return index;
  }

  private int toJsonString(int index) {
    index = toJsonSymbol(index, '\'', '"');
    while (hasChar(index)) {
      char c = peek(index++);
      if (c == '\'') {
        append('"');
        return index; // found end of string literal
      }
      // 1:1 transfer (default case)
      if (c == '"') {
        append("\\\""); // needs escaping in JSON
      } else {
        append(c);
      }
      if (c == '\\') {
        char escape = peek(index);
        index++; // move beyond the first escaped char
        if (escape == '\'') {
          // does not need escaping in JSON
          json.setLength(json.length() - 1); // undo \
          append('\'');
        } else {
          append(escape);
          if (escape == 'u') {
            append(peek(index++));
            append(peek(index++));
            append(peek(index++));
            append(peek(index++));
          }
        }
      }
    }
    // this is only to fail at end of input from exiting the while loop
    return toJsonSymbol(index, '\'', '"');
  }

  private int toJsonNumber(int index) {
    char c = peek(index);
    // sign
    if (c == '-') {
      append('-');
      return toJsonNumber(index + 1);
    }
    // integer part
    if (c == '.') {
      append('0');
    } else {
      while (hasChar(index) && isDigit(peek(index))) index = toJsonChar(index);
    }
    // fraction part
    if (hasChar(index) && peek(index) == '.') {
      index = toJsonChar(index); // transfer .
      if (!hasChar(index) || !isDigit(peek(index))) {
        append("0"); // auto-add zero after decimal point when no digits follow
        return index;
      }
      while (hasChar(index) && isDigit(peek(index))) index = toJsonChar(index);
    }
    // exponent part
    c = hasChar(index) ? peek(index) : 'x';
    if (c == 'e' || c == 'E') {
      index = toJsonChar(index); // e/E
      if (peek(index) == '-') index = toJsonChar(index); // -
      while (hasChar(index) && isDigit(peek(index))) index = toJsonChar(index);
    }
    return index;
  }

  private int toJsonBoolean(int index) {
    char c = peek(index);
    if (c == 't') return toJsonLiteral(index, "true");
    if (c == 'f') return toJsonLiteral(index, "false");
    throw expected("[tf]", juon, index);
  }

  private int toJsonNull(int index) {
    // omitted null (we see the comma after)
    if (peek(index) == ',') {
      append("null");
      return index; // the comma needs to be processed elsewhere
    }
    return toJsonLiteral(index, "null");
  }

  private int toJsonWhitespace(int index) {
    while (hasChar(index) && isWhitespace(peek(index))) append(peek(index++));
    return index;
  }

  private int toJsonSymbol(int index, char inJuon, char inJson) {
    if (peek(index) != inJuon) throw expected(inJuon + " => " + inJson, juon, index);
    append(inJson);
    return toJsonWhitespace(index + 1);
  }

  private int toJsonChar(int index) {
    append(peek(index));
    return index + 1;
  }

  private int toJsonLiteral(int index, String literal) {
    boolean isShort = !hasChar(index + 1) || !isLowerLetter(juon[index + 1]);
    if (isShort) {
      append(literal);
      return toJsonWhitespace(index + 1);
    }
    int l = literal.length();
    for (int i = 0; i < l; i++) {
      char c = peek(index + i);
      if (c != literal.charAt(i))
        throw expected(literal.charAt(i), juon, index + i);
      append(c);
    }
    return toJsonWhitespace(index + l);
  }

  private boolean hasChar(int index) {
    return index < juon.length;
  }

  private char peek(int index) {
    if (!hasChar(index)) expected("<?>", juon, index);
    return juon[index];
  }

  private void append(char c) {
    json.append(c);
  }

  private void append(String str) {
    json.append(str);
  }

  private static boolean isWhitespace(char c) {
    return c == ' ' || c == '\n' || c == '\t' || c == '\r';
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
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

  private static boolean isNameFirstChar(char c) {
    return isLetter(c) || c == '@';
  }

  private static boolean isNameChar(char c) {
    return isLetter(c) || isDigit(c) || c == '@' || c == '-' || c == '.' || c == '_';
  }
}
