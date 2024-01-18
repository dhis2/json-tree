package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
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

    public interface JsonMaximumExampleA extends JsonObject {

        @Validation( exclusiveMaximum = 100 )
        default int age() {
            return getNumber( "age" ).intValue();
        }
    }

    public interface JsonMaximumExampleB extends JsonObject {

        @Validation( exclusiveMaximum = 250.5 )
        default Number height() {
            return getNumber( "height" ).number();
        }
    }

    @Test
    void testExclusiveMaximum_OK() {
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"age":-200}""" ).validate( JsonMaximumExampleA.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"age":99}""" ).validate( JsonMaximumExampleA.class ) );

        assertDoesNotThrow( () -> JsonMixed.of( "{}" ).validate( JsonMaximumExampleB.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"height":250.4}""" ).validate( JsonMaximumExampleB.class ) );
    }

    @Test
    void testExclusiveMaximum_Required() {
        assertValidationError( "{}", JsonMaximumExampleA.class, Rule.REQUIRED, "age" );
    }

    @Test
    void testExclusiveMaximum_TooLarge() {
        assertValidationError( """
            {"age":100}""", JsonMaximumExampleA.class, Rule.EXCLUSIVE_MAXIMUM, 100d, 100d );
        assertValidationError( """
            {"height":250.5}""", JsonMaximumExampleB.class, Rule.EXCLUSIVE_MAXIMUM, 250.5d, 250.5d );
    }

    @Test
    void testExclusiveMaximum_WrongType() {
        assertValidationError( """
            {"age":true}""", JsonMaximumExampleA.class, Rule.TYPE, Set.of( NodeType.INTEGER ), NodeType.BOOLEAN );
        assertValidationError( """
            {"height":true}""", JsonMaximumExampleB.class, Rule.TYPE, Set.of( NodeType.NUMBER ), NodeType.BOOLEAN );
    }
}
