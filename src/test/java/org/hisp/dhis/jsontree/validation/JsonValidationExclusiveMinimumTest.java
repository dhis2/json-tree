package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.JsonValue;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.hisp.dhis.jsontree.validation.Assertions.assertValidationError;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests Validation of the @{@link Validation#exclusiveMinimum()} property.
 *
 * @author Jan Bernitt
 */
class JsonValidationExclusiveMinimumTest {

    public interface JsonMinimumRequiredExample extends JsonObject {

        @Validation(exclusiveMinimum = 0)
        default int age() {
            return getNumber( "age" ).intValue();
        }
    }

    public interface JsonMinimumExample extends JsonObject {
        @Validation(exclusiveMinimum = 20)
        default Integer height() {
            return getNumber( "height" ).integer();
        }
    }

    @Test
    void testExclusiveMinimum_OK() {
        assertDoesNotThrow( () -> JsonValue.of( """
            {"age":1}""" ).validate( JsonMinimumRequiredExample.class ) );
        assertDoesNotThrow( () -> JsonValue.of( """
            {"age":50}""" ).validate( JsonMinimumRequiredExample.class ) );

        assertDoesNotThrow( () -> JsonValue.of( "{}" ).validate( JsonMinimumExample.class ) );
        assertDoesNotThrow( () -> JsonValue.of( """
            {"height":21}""" ).validate( JsonMinimumExample.class ) );
    }

    @Test
    void testExclusiveMinimum_Required() {
        assertValidationError( "{}", JsonMinimumRequiredExample.class, Rule.REQUIRED, "age" );
    }

    @Test
    void testExclusiveMinimum_TooSmall() {
        assertValidationError( """
            {"age":0}""", JsonMinimumRequiredExample.class, Rule.EXCLUSIVE_MINIMUM, 0d, 0d );
        assertValidationError( """
            {"height":20}""", JsonMinimumExample.class, Rule.EXCLUSIVE_MINIMUM, 20d, 20d );
    }

    @Test
    void testExclusiveMinimum_WrongType() {
        assertValidationError(  """
            {"age":true}""", JsonMinimumRequiredExample.class, Rule.TYPE, Set.of( NodeType.INTEGER ) );
        assertValidationError(  """
            {"height":true}""", JsonMinimumExample.class, Rule.TYPE, Set.of( NodeType.INTEGER )  );
    }
}
