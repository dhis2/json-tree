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
 * Tests Validation of the @{@link Validation#maximum()} property.
 *
 * @author Jan Bernitt
 */
class JsonValidationMaximumTest {

    public interface JsonMaximumRequiredExample extends JsonObject {

        @Validation(maximum = 100)
        default int age() {
            return getNumber( "age" ).intValue();
        }
    }

    public interface JsonMaximumExample extends JsonObject {
        @Validation(maximum = 250.5)
        default Number height() {
            return getNumber( "height" ).number();
        }
    }

    @Test
    void testMaximum_OK() {
        assertDoesNotThrow( () -> JsonValue.of( """
            {"age":-200}""" ).validate( JsonMaximumRequiredExample.class ) );
        assertDoesNotThrow( () -> JsonValue.of( """
            {"age":100}""" ).validate( JsonMaximumRequiredExample.class ) );

        assertDoesNotThrow( () -> JsonValue.of( "{}" ).validate( JsonMaximumExample.class ) );
        assertDoesNotThrow( () -> JsonValue.of( """
            {"height":250.4}""" ).validate( JsonMaximumExample.class ) );
    }

    @Test
    void testMaximum_Required() {
        assertValidationError( "{}", JsonMaximumRequiredExample.class, Rule.REQUIRED, "age" );
    }

    @Test
    void testMaximum_TooLarge() {
        assertValidationError( """
            {"age":101}""", JsonMaximumRequiredExample.class, Rule.MAXIMUM, 100d, 101d );
        assertValidationError( """
            {"height":250.7}""", JsonMaximumExample.class, Rule.MAXIMUM, 250.5d, 250.7d );
    }

    @Test
    void testMaximum_WrongType() {
        assertValidationError(  """
            {"age":true}""", JsonMaximumRequiredExample.class, Rule.TYPE, Set.of( NodeType.INTEGER ) );
        assertValidationError(  """
            {"height":true}""", JsonMaximumExample.class, Rule.TYPE, Set.of( NodeType.NUMBER )  );
    }
}
