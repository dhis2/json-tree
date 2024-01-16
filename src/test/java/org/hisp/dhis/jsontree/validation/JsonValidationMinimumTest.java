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
 * Tests Validation of the @{@link Validation#minimum()} property.
 *
 * @author Jan Bernitt
 */
class JsonValidationMinimumTest {

    public interface JsonMinimumRequiredExample extends JsonObject {

        @Validation(minimum = 0)
        default int age() {
            return getNumber( "age" ).intValue();
        }
    }

    public interface JsonMinimumExample extends JsonObject {
        @Validation(minimum = 20)
        default Integer height() {
            return getNumber( "height" ).integer();
        }
    }

    @Test
    void testMinimum_OK() {
        assertDoesNotThrow( () -> JsonValue.of( """
            {"age":0}""" ).validate( JsonMinimumRequiredExample.class ) );
        assertDoesNotThrow( () -> JsonValue.of( """
            {"age":50}""" ).validate( JsonMinimumRequiredExample.class ) );

        assertDoesNotThrow( () -> JsonValue.of( "{}" ).validate( JsonMinimumExample.class ) );
        assertDoesNotThrow( () -> JsonValue.of( """
            {"height":20}""" ).validate( JsonMinimumExample.class ) );
    }

    @Test
    void testMinimum_Required() {
        assertValidationError( "{}", JsonMinimumRequiredExample.class, Rule.REQUIRED, "age" );
    }

    @Test
    void testMinimum_TooSmall() {
        assertValidationError( """
            {"age":-1}""", JsonMinimumRequiredExample.class, Rule.MINIMUM, 0d, -1d );
        assertValidationError( """
            {"height":19}""", JsonMinimumExample.class, Rule.MINIMUM, 20d, 19d );
    }

    @Test
    void testMinimum_WrongType() {
        assertValidationError(  """
            {"age":true}""", JsonMinimumRequiredExample.class, Rule.TYPE, Set.of( NodeType.INTEGER ) );
        assertValidationError(  """
            {"height":true}""", JsonMinimumExample.class, Rule.TYPE, Set.of( NodeType.INTEGER )  );
    }
}
