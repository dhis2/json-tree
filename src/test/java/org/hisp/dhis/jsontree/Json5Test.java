package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.stream.DoubleStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

/** Tests the {@link Json5} re-writing. */
class Json5Test {

  @Test
  void testOf_SingleQuotes() {
    assertEquals("\"hello\"", Json5.of("'hello'").toJson());
    assertEquals("{\"hello\":42}", Json5.of("{'hello':42}").toJson());
    assertEquals("{\"hello\":\"you\"}", Json5.of("{'hello':'you'}").toJson());
  }

  @Test
  void testOf_SingleQuotesNoEndQuote() {
    JsonFormatException ex =
        assertThrowsExactly(JsonFormatException.class, () -> Json5.of("{'a:12}"));
    assertEquals(
        """
        Unexpected EOI at position 7,
        {"a:12}
               ^ expected '""",
        ex.getMessage());
  }

  @Test
  void testOf_SingleQuotesContainsDoubleQuote() {
    assertEquals("{\"a\":\"hello'you'\"}", Json5.of("{'a':'hello\"you\"'}").toJson());
    assertEquals("\"hello\\\"you\\\"\"", Json5.of("'hello\\\"you\\\"'").toJson());
    assertEquals("hello\"you\"", Json5.of("'hello\\\"you\\\"'").string());
  }

  @Test
  void testOf_DanglingCommas() {
    assertEquals("[1,2 ]", Json5.of("[1,2,]").toJson());
    assertEquals("{\"a\":1 }", Json5.of("{'a':1,}").toJson());
  }

  @Test
  void testOf_NumberPlusSign() {
    assertEquals("2", Json5.of("+2").toJson());
    assertEquals("2.0", Json5.of("+2.0").toJson());
    assertEquals("2.4e2", Json5.of("+2.4e2").toJson());
  }

  @Test
  void testOf_NumberLeadingDecimalPoint() {
    assertEquals("[0.0]", Json5.of("[ .0]").toJson());
    assertEquals("[0.0]", Json5.of("[+.0]").toJson());
  }

  @Test
  void testOf_NumberLiterals() {
    JsonArray array = Json5.of("[ NaN , Infinity, 1, -Infinity,]");
    assertEquals("[\"NaN\",\"Infinity\",1,\"-Infinity\"]", array.toJson());

    // this is actually useful since strings can be treated as numbers too
    assertArrayEquals(
        DoubleStream.of(Double.NaN, Double.POSITIVE_INFINITY, 1d, Double.NEGATIVE_INFINITY)
            .toArray(),
        array.doubleValues().toArray());
  }
}
