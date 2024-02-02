package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.hisp.dhis.jsontree.Assertions.assertValidationError;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests Validation of the @{@link Validation#minimum()} property.
 *
 * @author Jan Bernitt
 */
class JsonValidationMinimumTest {

    public interface JsonMinimumExampleA extends JsonObject {

        @Validation( minimum = 0 )
        default int age() {
            return getNumber( "age" ).intValue();
        }
    }

    public interface JsonMinimumExampleB extends JsonObject {

        @Validation( minimum = 20 )
        default Integer height() {
            return getNumber( "height" ).integer();
        }
    }

    @Test
    void testMinimum_OK() {
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"age":0}""" ).validate( JsonMinimumExampleA.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"age":50}""" ).validate( JsonMinimumExampleA.class ) );

        assertDoesNotThrow( () -> JsonMixed.of( "{}" ).validate( JsonMinimumExampleB.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"height":20}""" ).validate( JsonMinimumExampleB.class ) );
    }

    @Test
    void testMinimum_Required() {
        assertValidationError( "{}", JsonMinimumExampleA.class, Rule.REQUIRED, "age" );
    }

    @Test
    void testMinimum_TooSmall() {
        assertValidationError( """
            {"age":-1}""", JsonMinimumExampleA.class, Rule.MINIMUM, 0d, -1d );
        assertValidationError( """
            {"height":19}""", JsonMinimumExampleB.class, Rule.MINIMUM, 20d, 19d );
    }

    @Test
    void testMinimum_WrongType() {
        assertValidationError( """
            {"age":true}""", JsonMinimumExampleA.class, Rule.TYPE, Set.of( NodeType.INTEGER ), NodeType.BOOLEAN );
        assertValidationError( """
            {"height":true}""", JsonMinimumExampleB.class, Rule.TYPE, Set.of( NodeType.INTEGER ), NodeType.BOOLEAN );
    }
}
