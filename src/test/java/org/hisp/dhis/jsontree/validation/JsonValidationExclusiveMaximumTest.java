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
 * Tests Validation of the @{@link Validation#exclusiveMaximum()} property.
 *
 * @author Jan Bernitt
 */
class JsonValidationExclusiveMaximumTest {

    public interface JsonMaximumRequiredExample extends JsonObject {

        @Validation(exclusiveMaximum = 100)
        default int age() {
            return getNumber( "age" ).intValue();
        }
    }

    public interface JsonMaximumExample extends JsonObject {
        @Validation(exclusiveMaximum = 250.5)
        default Number height() {
            return getNumber( "height" ).number();
        }
    }

    @Test
    void testExclusiveMaximum_OK() {
        assertDoesNotThrow( () -> JsonValue.of( """
            {"age":-200}""" ).validate( JsonMaximumRequiredExample.class ) );
        assertDoesNotThrow( () -> JsonValue.of( """
            {"age":99}""" ).validate( JsonMaximumRequiredExample.class ) );

        assertDoesNotThrow( () -> JsonValue.of( "{}" ).validate( JsonMaximumExample.class ) );
        assertDoesNotThrow( () -> JsonValue.of( """
            {"height":250.4}""" ).validate( JsonMaximumExample.class ) );
    }

    @Test
    void testExclusiveMaximum_Required() {
        assertValidationError( "{}", JsonMaximumRequiredExample.class, Rule.REQUIRED, "age" );
    }

    @Test
    void testExclusiveMaximum_TooLarge() {
        assertValidationError( """
            {"age":100}""", JsonMaximumRequiredExample.class, Rule.EXCLUSIVE_MAXIMUM, 100d, 100d );
        assertValidationError( """
            {"height":250.5}""", JsonMaximumExample.class, Rule.EXCLUSIVE_MAXIMUM, 250.5d, 250.5d );
    }

    @Test
    void testExclusiveMaximum_WrongType() {
        assertValidationError(  """
            {"age":true}""", JsonMaximumRequiredExample.class, Rule.TYPE, Set.of( NodeType.INTEGER ) );
        assertValidationError(  """
            {"height":true}""", JsonMaximumExample.class, Rule.TYPE, Set.of( NodeType.NUMBER )  );
    }
}
