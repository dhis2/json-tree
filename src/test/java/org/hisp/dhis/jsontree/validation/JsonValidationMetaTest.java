package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.util.List;
import java.util.Map;

import static java.lang.Double.NaN;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.hisp.dhis.jsontree.Assertions.assertValidationError;

/**
 * Creates meta annotations that mimic common Java server validation
 * using {@link Validation#meta()} and {@link Validation.Meta}.
 */
class JsonValidationMetaTest {

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

  record ExampleBean(
      @NotEmpty String notEmptyString,
      @NotEmpty List<String> notEmptyArray,
      @NotEmpty Map<String, String> notEmptyMap,
      @Positive int positive,
      @PositiveOrZero int positiveOrZero,
      @Negative int negative,
      @NegativeOrZero int negativeOrZero,
      @Range(min=0, max=120) int range
  ) {}

  @Test
  void testRange() {
    JsonObject obj = JsonMixed.of("""
      {
        "range": 130
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.MAXIMUM, 120, 130);
    obj = JsonMixed.of("""
      {
        "range": -1
      }""");
    assertValidationError(obj, ExampleBean.class, Rule.MINIMUM, 0, -1);
  }
}
