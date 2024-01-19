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
 * Tests Validation of the @{@link Validation#maximum()} property.
 *
 * @author Jan Bernitt
 */
class JsonValidationMaximumTest {

    public interface JsonMaximumExampleA extends JsonObject {

        @Validation( maximum = 100 )
        default int age() {
            return getNumber( "age" ).intValue();
        }
    }

    public interface JsonMaximumExampleB extends JsonObject {

        @Validation( maximum = 250.5 )
        default Number height() {
            return getNumber( "height" ).number();
        }
    }

    @Test
    void testMaximum_OK() {
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"age":-200}""" ).validate( JsonMaximumExampleA.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"age":100}""" ).validate( JsonMaximumExampleA.class ) );

        assertDoesNotThrow( () -> JsonMixed.of( "{}" ).validate( JsonMaximumExampleB.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"height":250.4}""" ).validate( JsonMaximumExampleB.class ) );
    }

    @Test
    void testMaximum_Required() {
        assertValidationError( "{}", JsonMaximumExampleA.class, Rule.REQUIRED, "age" );
    }

    @Test
    void testMaximum_TooLarge() {
        assertValidationError( """
            {"age":101}""", JsonMaximumExampleA.class, Rule.MAXIMUM, 100d, 101d );
        assertValidationError( """
            {"height":250.7}""", JsonMaximumExampleB.class, Rule.MAXIMUM, 250.5d, 250.7d );
    }

    @Test
    void testMaximum_WrongType() {
        assertValidationError( """
            {"age":true}""", JsonMaximumExampleA.class, Rule.TYPE, Set.of( NodeType.INTEGER ), NodeType.BOOLEAN );
        assertValidationError( """
            {"height":true}""", JsonMaximumExampleB.class, Rule.TYPE, Set.of( NodeType.NUMBER ), NodeType.BOOLEAN );
    }
}
