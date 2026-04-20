package org.hisp.dhis.jsontree.validation;

import static org.hisp.dhis.jsontree.Assertions.assertValidationError;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link Validation#pattern()} based validations.
 */
class JsonValidationPatternTest {

  /**
   * An example that show how the number/string duality can be exploited to
   * add a pattern validation to a number to restrict the integer and fraction digits.
   */
  public record PatternOnNumber(
      @Validation(
              pattern = "2#.3#",
              type = {NodeType.STRING, NodeType.NUMBER})
          double value) {}

  @Test
  void testPattern() {
    JsonObject obj = JsonMixed.of("""
      {
        "value": 34.123
      }""");

    assertDoesNotThrow(() -> obj.validate(PatternOnNumber.class));
    assertEquals(new PatternOnNumber(34.123d), obj.to(PatternOnNumber.class));

    JsonObject obj2 = JsonMixed.of("""
      {
        "value": 34.12
      }""");

    assertValidationError(
        obj2,
        PatternOnNumber.class,
        Validation.Rule.PATTERN,
        "(?:[0-9]){2}[.](?:[0-9]){3}",
        "34.12");
  }
}
