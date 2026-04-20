package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.Assertions;
import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.hisp.dhis.jsontree.Assertions.assertValidationError;
import static org.hisp.dhis.jsontree.Validation.YesNo.NO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the consequences of {@link Validation#strictness()}.
 */
class JsonValidationStrictnessTest {

  public record ExampleBean(
      @Validation(required = NO, strictness = @Validation.Strict(true))
      String strictString,
      @Validation(required = NO)
      String nonStrictString,
      @Validation(required = NO, strictness = @Validation.Strict(true))
      double strictDouble,
      @Validation(required = NO)
      double nonStrictDouble,
      @Validation(required = NO, strictness = @Validation.Strict(true))
      int strictInteger,
      @Validation(required = NO)
      int nonStrictInteger,
      @Validation(required = NO, strictness = @Validation.Strict(true))
      boolean strictBoolean,
      @Validation(required = NO)
      boolean nonStrictBoolean
  ) {
    static final ExampleBean DEFAULT = new ExampleBean("", "", 0d, 0d, 0, 0, false, false);
  }

  @Test
  void testStrictString() {
    JsonObject obj = JsonMixed.of("""
      {
        "strictString": 123
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.TYPE, Set.of(NodeType.STRING), NodeType.NUMBER);
    obj = JsonMixed.of("""
      {
        "strictString": true
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.TYPE, Set.of(NodeType.STRING), NodeType.BOOLEAN);
  }

  @Test
  void testNonStrictString() {
    JsonObject obj = JsonMixed.of("""
      {
        "nonStrictString": 123
      }""");
    assertEquals("123", obj.to(ExampleBean.class).nonStrictString);
     obj = JsonMixed.of("""
      {
        "nonStrictString": true
      }""");
    assertEquals("true", obj.to(ExampleBean.class).nonStrictString);
  }

  @Test
  void testNonStrictDouble() {
    JsonObject obj = JsonMixed.of("""
      {
        "nonStrictDouble": "NaN"
      }""");
    assertEquals(Double.NaN, obj.to(ExampleBean.class).nonStrictDouble);
    obj = JsonMixed.of("""
      {
        "nonStrictDouble": "Infinity"
      }""");
    assertEquals(Double.POSITIVE_INFINITY, obj.to(ExampleBean.class).nonStrictDouble);
    obj = JsonMixed.of("""
      {
        "nonStrictDouble": "-Infinity"
      }""");
    assertEquals(Double.NEGATIVE_INFINITY, obj.to(ExampleBean.class).nonStrictDouble);
  }

  @Test
  void testStrictDouble() {
    JsonObject obj = JsonMixed.of("""
      {
        "strictDouble": "NaN"
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.TYPE, Set.of(NodeType.NUMBER), NodeType.STRING);
    obj = JsonMixed.of("""
      {
        "strictDouble": "Infinity"
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.TYPE, Set.of(NodeType.NUMBER), NodeType.STRING);
    obj = JsonMixed.of("""
      {
        "strictDouble": "-Infinity"
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.TYPE, Set.of(NodeType.NUMBER), NodeType.STRING);
  }

  @Test
  void testNonStrictInteger() {
    JsonObject obj = JsonMixed.of("""
      {
        "nonStrictInteger": "NaN"
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.TYPE, Set.of(NodeType.INTEGER), NodeType.STRING);
    obj = JsonMixed.of("""
      {
        "nonStrictInteger": 1.0
      }""");
    assertEquals(1, obj.to(ExampleBean.class).nonStrictInteger);
    obj = JsonMixed.of("""
      {
        "nonStrictInteger": 1.6
      }""");
    assertEquals(1, obj.to(ExampleBean.class).nonStrictInteger);
  }

  @Test
  void testStrictInteger() {
    JsonObject obj = JsonMixed.of("""
      {
        "strictInteger": "NaN"
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.TYPE, Set.of(NodeType.INTEGER), NodeType.STRING);
    obj = JsonMixed.of("""
      {
        "strictInteger": "Infinity"
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.TYPE, Set.of(NodeType.INTEGER), NodeType.STRING);
    obj = JsonMixed.of("""
      {
        "strictInteger": "-Infinity"
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.TYPE, Set.of(NodeType.INTEGER), NodeType.STRING);
    obj = JsonMixed.of("""
      {
        "strictInteger": 0.1
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.TYPE, Set.of(NodeType.INTEGER), NodeType.NUMBER);
  }

  @Test
  void testStrictBoolean() {
    JsonObject obj = JsonMixed.of("""
      {
        "strictBoolean": "true"
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.TYPE, Set.of(NodeType.BOOLEAN), NodeType.STRING);
  }

  @Test
  void testNonStrictBoolean() {
    JsonObject obj = JsonMixed.of("""
      {
        "nonStrictBoolean": "true"
      }""");
    assertTrue(obj.to(ExampleBean.class).nonStrictBoolean);
  }

}
