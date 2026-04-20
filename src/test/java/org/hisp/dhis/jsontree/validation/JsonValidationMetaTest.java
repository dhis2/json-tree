package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.hisp.dhis.jsontree.Validation.YesNo;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.util.List;
import java.util.Map;

import static java.lang.Double.NaN;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.hisp.dhis.jsontree.Assertions.assertValidationError;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Creates meta annotations that mimic common Java server validation
 * using {@link Validation#meta()} and {@link Validation.Meta}.
 */
class JsonValidationMetaTest {

  @Retention(RUNTIME)
  @Validation(required = YesNo.NO)
  @interface Optional {}

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

  public record ExampleBean(
      @Optional @NotEmpty String notEmptyString,
      @Optional @NotEmpty List<String> notEmptyArray,
      @Optional @NotEmpty Map<String, String> notEmptyMap,
      @Optional @Positive int positive,
      @Optional @PositiveOrZero int positiveOrZero,
      @Optional @Negative int negative,
      @Optional @NegativeOrZero int negativeOrZero,
      @Optional @Range(min=0, max=120) int range
  ) {
    static final ExampleBean DEFAULT =
        new ExampleBean(null, List.of(), Map.of(), 1, 0, -1, 0, 0);
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
}
