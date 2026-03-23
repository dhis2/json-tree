package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import org.junit.jupiter.api.Test;

/**
 * Tests the specific API of the {@link JsonNumber} methods.
 *
 * @author Jan Bernitt
 */
class JsonNumberTest {

  @Test
  void testInteger_Undefined() {
    assertNull(JsonMixed.of("{}").getNumber("x").integer());
  }

  @Test
  void testInteger_Null() {
    assertNull(JsonMixed.of("null").integer());
  }

  @Test
  void testInteger_NoFractionNumber() {
    assertEquals(42, JsonMixed.of("42").integer());
  }

  @Test
  void testInteger_FractionNumber() {
    assertEquals(42, JsonMixed.of("42.5").integer());
  }

  @Test
  void testIntValue_Undefined() {
    JsonNumber x = JsonMixed.of("{}").getNumber("x");
    assertThrowsExactly(JsonPathException.class, x::intValue);
  }

  @Test
  void testIntValue_NoNumber() {
    JsonNumber x = JsonMixed.of("true");
    assertThrowsExactly(JsonTreeException.class, x::intValue);
  }

  @Test
  void testIntValue_Null() {
    JsonNumber val = JsonMixed.of("null");
    assertThrowsExactly(JsonTreeException.class, val::intValue);

    val = JsonMixed.of("[null]").getNumber(0);
    assertThrowsExactly(JsonTreeException.class, val::intValue);
  }

  @Test
  void testIntValue_NoFractionNumber() {
    assertEquals(42, JsonMixed.of("42").intValue());
  }

  @Test
  void testIntValue_FractionNumber() {
    assertEquals(42, JsonMixed.of("42.5").intValue());
  }

  @Test
  void testIntValue_WithDefaultUndefined() {
    JsonNumber x = JsonMixed.of("{}").getNumber("x");
    assertEquals(42, x.intValue(42));
  }

  @Test
  void testIntValue_WithDefaultNoNumber() {
    JsonNumber x = JsonMixed.of("true");
    assertThrowsExactly(JsonTreeException.class, () -> x.intValue(42));
  }

  @Test
  void testIntValue_WithDefaultNull() {
    JsonMixed x = JsonMixed.of("null");
    assertEquals(42, x.intValue(42));
  }

  @Test
  void testIntValue_WithDefaultNoFractionNumber() {
    assertEquals(42, JsonMixed.of("42").intValue(55));
  }

  @Test
  void testIntValue_WithDefaultFractionNumber() {
    assertEquals(42, JsonMixed.of("42.5").intValue(55));
  }

  @Test
  void testNumber_WithDefaultUndefined() {
    JsonNumber x = JsonMixed.of("{}").getNumber("x");
    assertEquals(42, x.number(42));
  }

  @Test
  void testNumber_WithDefaultNoNumber() {
    JsonNumber x = JsonMixed.of("true");
    assertThrowsExactly(JsonTreeException.class, () -> x.number(42));
  }

  @Test
  void testNumber_WithDefaultNull() {
    JsonMixed x = JsonMixed.of("null");
    assertEquals(42, x.number(42));
  }

  @Test
  void testNumber_WithDefaultNoFractionNumber() {
    assertEquals(42, JsonMixed.of("42").number(55).intValue());
  }

  @Test
  void testNumber_WithDefaultFractionNumber() {
    assertEquals(42.5d, JsonMixed.of("42.5").number(55d).doubleValue());
  }

  @Test
  void testIntValue() {
    assertEquals(3, JsonMixed.of("3").intValue());
    assertEquals(3, JsonMixed.of("3.0").intValue());
    assertEquals(3, JsonMixed.of("3.14").intValue());
    assertEquals(Integer.MAX_VALUE, Json.of(Integer.MAX_VALUE).intValue());
    assertEquals(Integer.MIN_VALUE, Json.of(Integer.MIN_VALUE).intValue());
    assertThrowsExactly(JsonTreeException.class, () -> JsonMixed.of("null").intValue());
    assertThrowsExactly(JsonPathException.class, () -> JsonMixed.of("[]").getNumber(0).intValue());
  }

  @Test
  void testLongValue() {
    assertEquals(3L, JsonMixed.of("3").longValue());
    assertEquals(3L, JsonMixed.of("3.0").longValue());
    assertEquals(3L, JsonMixed.of("3.14").longValue());
    assertEquals(Long.MAX_VALUE, Json.of(Long.MAX_VALUE).longValue());
    assertEquals(Long.MIN_VALUE, Json.of(Long.MIN_VALUE).longValue());
    assertThrowsExactly(JsonTreeException.class, () -> JsonMixed.of("null").longValue());
    assertThrowsExactly(JsonPathException.class, () -> JsonMixed.of("[]").getNumber(0).longValue());
  }

  @Test
  void testDoubleValue() {
    assertEquals(3d, JsonMixed.of("3").doubleValue());
    assertEquals(3.0d, JsonMixed.of("3.0").doubleValue());
    assertEquals(3.14d, JsonMixed.of("3.14").doubleValue());
    assertEquals(Double.MAX_VALUE, Json.of(Double.MAX_VALUE).doubleValue());
    assertEquals(Double.MIN_VALUE, Json.of(Double.MIN_VALUE).doubleValue());
    assertThrowsExactly(JsonTreeException.class, () -> JsonMixed.of("null").doubleValue());
    assertThrowsExactly(
        JsonPathException.class, () -> JsonMixed.of("[]").getNumber(0).doubleValue());
  }

  @Test
  void testFloatValue() {
    assertEquals(3f, JsonMixed.of("3").floatValue());
    assertEquals(3.0f, JsonMixed.of("3.0").floatValue());
    assertEquals(3.14f, JsonMixed.of("3.14").floatValue());
    assertEquals(Float.MAX_VALUE, Json.of(Float.MAX_VALUE).floatValue());
    assertEquals(Float.MIN_VALUE, Json.of(Float.MIN_VALUE).floatValue());
    assertThrowsExactly(JsonTreeException.class, () -> JsonMixed.of("null").floatValue());
    assertThrowsExactly(
        JsonPathException.class, () -> JsonMixed.of("[]").getNumber(0).floatValue());
  }

  @Test
  void testIrregularNumberPersistence() {
    JsonMixed big =
        JsonMixed.of(
            "99999999999999999999999999999999999999999999999999999999999999999999999999999999999999");
    assertEquals(
        "99999999999999999999999999999999999999999999999999999999999999999999999999999999999999",
        big.number().toString(), "number() should maintain original value");
    JsonNode root = JsonBuilder.createObject(JsonBuilder.PRETTY, obj ->
        obj.addMember("big-one", big.node())
            .addNumber("big-two", big.number())
    );
    assertEquals(
        """
        {
          "big-one": 99999999999999999999999999999999999999999999999999999999999999999999999999999999999999,
          "big-two": 99999999999999999999999999999999999999999999999999999999999999999999999999999999999999
        }""",
        root.getDeclaration().toString());
  }
}
