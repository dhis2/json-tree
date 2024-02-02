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
 * Tests Validation of the @{@link Validation#exclusiveMinimum()} property.
 *
 * @author Jan Bernitt
 */
class JsonValidationExclusiveMinimumTest {

    public interface JsonMinimumExampleA extends JsonObject {

        @Validation( exclusiveMinimum = 0 )
        default int age() {
            return getNumber( "age" ).intValue();
        }
    }

    public interface JsonMinimumExampleB extends JsonObject {

        @Validation( exclusiveMinimum = 20 )
        default Integer height() {
            return getNumber( "height" ).integer();
        }
    }

    @Test
    void testExclusiveMinimum_OK() {
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"age":1}""" ).validate( JsonMinimumExampleA.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"age":50}""" ).validate( JsonMinimumExampleA.class ) );

        assertDoesNotThrow( () -> JsonMixed.of( "{}" ).validate( JsonMinimumExampleB.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"height":21}""" ).validate( JsonMinimumExampleB.class ) );
    }

    @Test
    void testExclusiveMinimum_Required() {
        assertValidationError( "{}", JsonMinimumExampleA.class, Rule.REQUIRED, "age" );
    }

    @Test
    void testExclusiveMinimum_TooSmall() {
        assertValidationError( """
            {"age":0}""", JsonMinimumExampleA.class, Rule.EXCLUSIVE_MINIMUM, 0d, 0d );
        assertValidationError( """
            {"height":20}""", JsonMinimumExampleB.class, Rule.EXCLUSIVE_MINIMUM, 20d, 20d );
    }

    @Test
    void testExclusiveMinimum_WrongType() {
        assertValidationError( """
            {"age":true}""", JsonMinimumExampleA.class, Rule.TYPE, Set.of( NodeType.INTEGER ), NodeType.BOOLEAN );
        assertValidationError( """
            {"height":true}""", JsonMinimumExampleB.class, Rule.TYPE, Set.of( NodeType.INTEGER ), NodeType.BOOLEAN );
    }
}
