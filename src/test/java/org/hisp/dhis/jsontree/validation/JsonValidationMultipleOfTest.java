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
 * Tests Validation of the @{@link Validation#multipleOf()} property.
 *
 * @author Jan Bernitt
 */
class JsonValidationMultipleOfTest {

    public interface JsonMultipleOfExampleA extends JsonObject {

        @Validation( multipleOf = 10 )
        default int age() {
            return getNumber( "age" ).intValue();
        }
    }

    public interface JsonMultipleOfExampleB extends JsonObject {

        @Validation( multipleOf = 0.5 )
        default Number height() {
            return getNumber( "height" ).number();
        }
    }

    @Test
    void testMultipleOf_OK() {
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"age":0}""" ).validate( JsonMultipleOfExampleA.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"age":20}""" ).validate( JsonMultipleOfExampleA.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"height":1.5}""" ).validate( JsonMultipleOfExampleB.class ) );
    }

    @Test
    void testMultipleOf_Required() {
        assertValidationError( "{}", JsonMultipleOfExampleA.class, Rule.REQUIRED, "age" );
    }

    @Test
    void testMultipleOf_Remainder() {
        assertValidationError( """
            {"age":5}""", JsonMultipleOfExampleA.class, Rule.MULTIPLE_OF, 10d, 5d );
        assertValidationError( """
            {"height":1.2}""", JsonMultipleOfExampleB.class, Rule.MULTIPLE_OF, 0.5d, 1.2d );
    }

    @Test
    void testMultipleOf_WrongType() {
        assertValidationError( """
            {"age":true}""", JsonMultipleOfExampleA.class, Rule.TYPE, Set.of( NodeType.INTEGER ), NodeType.BOOLEAN );
        assertValidationError( """
            {"height":true}""", JsonMultipleOfExampleB.class, Rule.TYPE, Set.of( NodeType.NUMBER ), NodeType.BOOLEAN );
    }
}
