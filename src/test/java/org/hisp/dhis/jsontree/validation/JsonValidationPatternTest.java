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
 * Tests Validation of the @{@link Validation#pattern()} property.
 *
 * @author Jan Bernitt
 */
class JsonValidationPatternTest {

    public interface JsonPatternExampleA extends JsonObject {

        @Validation( pattern = "[0-9]{1,4}[A-Z]?" )
        default String no() {
            return getString( "no" ).string();
        }
    }

    @Test
    void testMaxLength_OK() {
        assertDoesNotThrow( () -> JsonMixed.of( """
            {}""" ).validate( JsonPatternExampleA.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"no":null}""" ).validate( JsonPatternExampleA.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"no":"12"}""" ).validate( JsonPatternExampleA.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"no":"12B"}""" ).validate( JsonPatternExampleA.class ) );
    }

    @Test
    void testMaxLength_NoMatch() {
        assertValidationError( """
            {"no":"B12"}""", JsonPatternExampleA.class, Rule.PATTERN, "[0-9]{1,4}[A-Z]?", "B12" );
        assertValidationError( """
            {"no":"12345"}""", JsonPatternExampleA.class, Rule.PATTERN, "[0-9]{1,4}[A-Z]?", "12345" );
    }

    @Test
    void testMaxLength_WrongType() {
        assertValidationError( """
            {"no":true}""", JsonPatternExampleA.class, Rule.TYPE, Set.of( NodeType.STRING ), NodeType.BOOLEAN );
    }
}
