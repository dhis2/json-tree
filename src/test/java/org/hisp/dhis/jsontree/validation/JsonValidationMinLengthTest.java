package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.hisp.dhis.jsontree.Validation.YesNo.NO;
import static org.hisp.dhis.jsontree.Assertions.assertValidationError;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests Validation of the @{@link Validation#minLength()} property.
 *
 * @author Jan Bernitt
 */
class JsonValidationMinLengthTest {

    public interface JsonMinLengthExampleA extends JsonObject {

        @Validation( minLength = 2 )
        default String name() {
            return getString( "name" ).string();
        }
    }

    public interface JsonMinLengthExampleB extends JsonObject {

        @Validation( minLength = 3, required = NO )
        default String abbr() {
            return getString( "abbr" ).string();
        }
    }

    @Test
    void testMinLength_OK() {
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"name":"yo"}""" ).validate( JsonMinLengthExampleA.class ) );

        assertDoesNotThrow( () -> JsonMixed.of( """
            {"abbr":"WAT"}""" ).validate( JsonMinLengthExampleB.class ) );
    }

    @Test
    void testMinLength_Undefined() {
        assertValidationError( "{}", JsonMinLengthExampleA.class, Rule.REQUIRED, "name" );

        assertDoesNotThrow( () -> JsonMixed.of( """
            {"abbr":null}""" ).validate( JsonMinLengthExampleB.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {}""" ).validate( JsonMinLengthExampleB.class ) );
    }

    @Test
    void testMinLength_TooShort() {
        assertValidationError( """
            {"name":"X"}""", JsonMinLengthExampleA.class, Rule.MIN_LENGTH, 2, 1 );
        assertValidationError( """
            {"abbr":"AB"}""", JsonMinLengthExampleB.class, Rule.MIN_LENGTH, 3, 2 );
    }

    @Test
    void testMinLength_WrongType() {
        assertValidationError( """
            {"name":true}""", JsonMinLengthExampleA.class, Rule.TYPE, Set.of( NodeType.STRING ), NodeType.BOOLEAN );
        assertValidationError( """
            {"abbr":true}""", JsonMinLengthExampleB.class, Rule.TYPE, Set.of( NodeType.STRING ), NodeType.BOOLEAN );
    }
}
