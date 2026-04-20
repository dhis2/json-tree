package org.hisp.dhis.jsontree.validation;

import static java.lang.Double.NaN;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.hisp.dhis.jsontree.Assertions.assertValidationError;
import static org.hisp.dhis.jsontree.Validation.YesNo.AUTO;
import static org.hisp.dhis.jsontree.Validation.YesNo.NO;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.annotation.Retention;
import java.util.List;
import java.util.Map;
import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.hisp.dhis.jsontree.Validation.YesNo;
import org.junit.jupiter.api.Test;

/**
 * Creates meta annotations that mimic common Java server validation
 * using {@link Validation#meta()} and {@link Validation.Meta}.
 */
class JsonValidationMetaTest {

  /**
   * This test is not specifically testing that components can be made optional,
   * it merely uses this so the JSON used in the test can only include the property
   * that is under test.
   */
  @Retention(RUNTIME)
  @Validation(required = YesNo.NO)
  @interface Optional {}

  @Retention(RUNTIME)
  @Validation(minLength = 1, minItems = 1, minProperties = 1)
  @interface NotEmpty {}

  @Retention(RUNTIME)
  @Validation(exclusiveMinimum = 0)
  @interface Positive {}

  @Retention(RUNTIME)
  @Validation(minimum = 0)
  @interface PositiveOrZero {}

  @Retention(RUNTIME)
  @Validation(exclusiveMaximum = 0)
  @interface Negative {}

  @Retention(RUNTIME)
  @Validation(maximum = 0)
  @interface NegativeOrZero {}

  @Retention(RUNTIME)
  @Validation(meta = Range.Meta.class)
  public @interface Range {
    int min(); int max();

    record Meta() implements Validation.Meta<Range> {

      @Override
      public Validation extract(Range range) {
        return new Validation.Instance(
            -1, -1, range.min(), range.max(), NaN, NaN, -1, -1, -1, -1, Validation.NodeType.INTEGER);
      }
    }
  }

  @Retention(RUNTIME)
  @Validation(meta = Digits.Meta.class)
  public @interface Digits {
    int integer();
    int fraction() default 0;

    record Meta() implements Validation.Meta<Digits> {

      @Override
      public Validation extract(Digits digits) {
        String pattern = digits.integer()+"#";
        if (digits.fraction() > 0) pattern += "."+digits.fraction()+"#";
        return new Validation.Instance(new Validation.NodeType[]{Validation.NodeType.STRING, Validation.NodeType.NUMBER}, Validation.Strict.Instance.AUTO,
            NO,
            new String[0],
            Enum.class,
            AUTO,
            -1,
            -1,
            new String[] { pattern },
            "",
            NaN,
            NaN,
            NaN,
            NaN,
            NaN,
            -1,
            -1,
            AUTO,
            -1,
            -1,
            AUTO,
            new String[0],
            AUTO);
      }
    }
  }

  public record ExampleBean(
      @Optional @NotEmpty String notEmptyString,
      @Optional @NotEmpty List<String> notEmptyArray,
      @Optional @NotEmpty Map<String, String> notEmptyMap,
      @Optional @Positive int positive,
      @Optional @PositiveOrZero int positiveOrZero,
      @Optional @Negative int negative,
      @Optional @NegativeOrZero int negativeOrZero,
      @Optional @Range(min=0, max=120) int range,
      @Optional @Digits(integer = 3, fraction = 1) double digits
  ) {
    static final ExampleBean DEFAULT =
        new ExampleBean(null, List.of(), Map.of(), 1, 0, -1, 0, 0, 123.4);
  }

  @Test
  void testDefaults() {
    ExampleBean actual = JsonMixed.of("{}").to(ExampleBean.class);
    assertEquals(1, actual.positive());
    assertEquals(-1, actual.negative());
    assertNull(actual.notEmptyString());
    assertEquals(List.of(), actual.notEmptyArray());
    assertEquals(Map.of(), actual.notEmptyMap());
  }

  @Test
  void testNotEmpty_String() {
    JsonObject obj = JsonMixed.of("""
      {
        "notEmptyString": ""
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.MIN_LENGTH, 1, 0);

    JsonObject valid = JsonMixed.of("""
      {
        "notEmptyString": "_"
      }""");
    assertDoesNotThrow(() -> valid.validate(ExampleBean.class));
  }

  @Test
  void testNotEmpty_Array() {
    JsonObject obj = JsonMixed.of("""
      {
        "notEmptyArray": []
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.MIN_ITEMS, 1, 0);

    JsonObject valid = JsonMixed.of("""
      {
        "notEmptyArray": [""]
      }""");
    assertDoesNotThrow(() -> valid.validate(ExampleBean.class));
  }

  @Test
  void testNotEmpty_Object() {
    JsonObject obj = JsonMixed.of("""
      {
        "notEmptyMap": {}
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.MIN_PROPERTIES, 1, 0);

    JsonObject valid = JsonMixed.of("""
      {
        "notEmptyMap": {"a": null}
      }""");
    assertDoesNotThrow(() -> valid.validate(ExampleBean.class));
  }

  @Test
  void testRange() {
    JsonObject obj = JsonMixed.of("""
      {
        "range": 130
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.MAXIMUM, 120L, 130L);

    obj = JsonMixed.of("""
      {
        "range": -1
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.MINIMUM, 0L, -1L);

    JsonObject valid = JsonMixed.of("""
      {
        "range": 10
      }""");
    assertDoesNotThrow(() -> valid.validate(ExampleBean.class));
  }

  @Test
  void testPositive() {
    JsonObject obj = JsonMixed.of("""
      {
        "positive": -1
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.MINIMUM, 1L, -1L);

    JsonObject valid = JsonMixed.of("""
      {
        "positive": 10
      }""");
    assertDoesNotThrow(() -> valid.validate(ExampleBean.class));
  }

  @Test
  void testPositiveOrZero() {
    JsonObject obj = JsonMixed.of("""
      {
        "positiveOrZero": -1
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.MINIMUM, 0L, -1L);

    JsonObject valid = JsonMixed.of("""
      {
        "positiveOrZero": 0
      }""");
    assertDoesNotThrow(() -> valid.validate(ExampleBean.class));
  }

  @Test
  void testNegative() {
    JsonObject obj = JsonMixed.of("""
      {
        "negative": 0
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.MAXIMUM, -1L, 0L);

    JsonObject valid = JsonMixed.of("""
      {
        "negative": -1
      }""");
    assertDoesNotThrow(() -> valid.validate(ExampleBean.class));
  }

  @Test
  void testNegativeOrZero() {
    JsonObject obj = JsonMixed.of("""
      {
        "negativeOrZero": 1
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.MAXIMUM, 0L, 1L);

    JsonObject valid = JsonMixed.of("""
      {
        "negativeOrZero": 0
      }""");
    assertDoesNotThrow(() -> valid.validate(ExampleBean.class));
  }

  @Test
  void testDigits() {
    JsonObject obj = JsonMixed.of("""
      {
        "digits": 12.3
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.PATTERN, "(?:[0-9]){3}[.](?:[0-9]){1}", "12.3");

    JsonObject valid = JsonMixed.of("""
      {
        "digits": 444.4
      }""");
    assertDoesNotThrow(() -> valid.validate(ExampleBean.class));

    JsonObject valid2 = JsonMixed.of("""
      {
        "digits": "444.4"
      }""");
    assertDoesNotThrow(() -> valid2.validate(ExampleBean.class));
    assertEquals(444.4d, valid2.to(ExampleBean.class).digits);
  }
}
