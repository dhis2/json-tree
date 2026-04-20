package org.hisp.dhis.jsontree.validation;

import static org.hisp.dhis.jsontree.Assertions.assertValidationError;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.Set;
import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.junit.jupiter.api.Test;

/**
 * Tests Validation of the @{@link Validation#exclusiveMinimum()} property.
 *
 * @author Jan Bernitt
 */
class JsonValidationExclusiveMinimumTest {

  public interface JsonMinimumExampleA extends JsonObject {

    @Validation(exclusiveMinimum = 0)
    default double weight() {
      return getNumber("weight").intValue();
    }
  }

  public interface JsonMinimumExampleB extends JsonObject {

    @Validation(exclusiveMinimum = 20)
    default Integer height() {
      return getNumber("height").integer();
    }
  }

  @Test
  void testExclusiveMinimum_OK() {
    assertDoesNotThrow(
        () ->
            JsonMixed.of(
                    """
            {"weight":1}""")
                .validate(JsonMinimumExampleA.class));
    assertDoesNotThrow(
        () ->
            JsonMixed.of(
                    """
            {"weight":50}""")
                .validate(JsonMinimumExampleA.class));

    assertDoesNotThrow(() -> JsonMixed.of("{}").validate(JsonMinimumExampleB.class));
    assertDoesNotThrow(
        () ->
            JsonMixed.of(
                    """
            {"height":21}""")
                .validate(JsonMinimumExampleB.class));
  }

  @Test
  void testExclusiveMinimum_Required() {
    assertValidationError("{}", JsonMinimumExampleA.class, Rule.REQUIRED, "weight");
  }

  @Test
  void testExclusiveMinimum_TooSmall() {
    assertValidationError(
        """
            {"weight":0}""",
        JsonMinimumExampleA.class,
        Rule.EXCLUSIVE_MINIMUM,
        0d,
        0d);
    assertValidationError(
        """
            {"height":20}""",
        JsonMinimumExampleB.class,
        Rule.MINIMUM,
        21L,
        20L);
  }

  @Test
  void testExclusiveMinimum_WrongType() {
    assertValidationError(
        """
            {"weight":true}""",
        JsonMinimumExampleA.class,
        Rule.TYPE,
        Set.of(NodeType.NUMBER),
        NodeType.BOOLEAN);
    assertValidationError(
        """
            {"height":true}""",
        JsonMinimumExampleB.class,
        Rule.TYPE,
        Set.of(NodeType.INTEGER),
        NodeType.BOOLEAN);
  }
}
