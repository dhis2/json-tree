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
 * Tests Validation of the @{@link Validation#multipleOf()} property.
 *
 * @author Jan Bernitt
 */
class JsonValidationMultipleOfTest {

    public interface JsonMultipleOfRequiredExample extends JsonObject {

        @Validation(multipleOf = 10)
        default int age() {
            return getNumber( "age" ).intValue();
        }
    }

    public interface JsonMultipleOfExample extends JsonObject {
        @Validation(multipleOf = 0.5)
        default Number height() {
            return getNumber( "height" ).number();
        }
    }

    @Test
    void testMultipleOf_OK() {
        assertDoesNotThrow( () -> JsonValue.of( """
            {"age":0}""" ).validate( JsonMultipleOfRequiredExample.class ) );
        assertDoesNotThrow( () -> JsonValue.of( """
            {"age":20}""" ).validate( JsonMultipleOfRequiredExample.class ) );
        assertDoesNotThrow( () -> JsonValue.of( """
            {"height":1.5}""" ).validate( JsonMultipleOfExample.class ) );
    }

    @Test
    void testMultipleOf_Required() {
        assertValidationError( "{}", JsonMultipleOfRequiredExample.class, Rule.REQUIRED, "age" );
    }

    @Test
    void testMultipleOf_Remainder() {
        assertValidationError( """
            {"age":5}""", JsonMultipleOfRequiredExample.class, Rule.MULTIPLE_OF, 10d, 5d );
        assertValidationError( """
            {"height":1.2}""", JsonMultipleOfExample.class, Rule.MULTIPLE_OF, 0.5d, 1.2d );
    }

    @Test
    void testMultipleOf_WrongType() {
        assertValidationError(  """
            {"age":true}""", JsonMultipleOfRequiredExample.class, Rule.TYPE, Set.of( NodeType.INTEGER ) );
        assertValidationError(  """
            {"height":true}""", JsonMultipleOfExample.class, Rule.TYPE, Set.of( NodeType.NUMBER )  );
    }
}
