package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.Error;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.hisp.dhis.jsontree.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.function.Consumer;

import static org.hisp.dhis.jsontree.validation.Assertions.assertValidationError;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Basic test for a custom {@link org.hisp.dhis.jsontree.Validation.Validator} class.
 *
 * @author Jan Bernitt
 */
class JsonValidationValidatorTest {

    public record UserValidator() implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isString() ) return;
            if ( !Set.of( "user1", "user2" ).contains( value.string() ) )
                addError.accept(
                    Error.of( Rule.CUSTOM, value, "Not a valid user %s", value.string() ) );
        }
    }

    public interface JsonUserUpdate extends JsonObject {

        @Validator( UserValidator.class )
        default String user() {
            return getString( "user" ).string();
        }
    }

    @Test
    void testValidator_OK() {
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"user": "user1"}""" ).validate( JsonUserUpdate.class ) );
    }

    @Test
    void testValidator_UnknownUser() {
        assertValidationError( """
            {"user": "user47"}""", JsonUserUpdate.class, Rule.CUSTOM, "user47" );
    }
}
