package org.hisp.dhis.jsontree;

import java.util.function.Consumer;

import static org.hisp.dhis.jsontree.Chars.expectChar;
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
  //TODO Make API similar to Json5 and return JsonMixed
  public static String toJson(String juon) {
    return "";
  }


}
