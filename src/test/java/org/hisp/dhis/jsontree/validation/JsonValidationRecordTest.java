package org.hisp.dhis.jsontree.validation;

import static org.hisp.dhis.jsontree.Assertions.assertValidationError;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.function.Consumer;
import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validator;
import org.junit.jupiter.api.Test;

/**
 * Tests validation of the {@link org.hisp.dhis.jsontree.Validation} when used on a {@link Record} type.
 *
 * @author Jan Bernitt
 */
class JsonValidationRecordTest {

    record SomeBean(
        @Validation(minimum = 0) int age,
        @Validator( CustomValidator.class ) String pattern
    ) {}

    record CustomValidator() implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Validation.Error> addError ) {
            if (value.isString() && !value.string().equals( "foo" ))
                addError.accept( Validation.Error.of( Validation.Rule.CUSTOM, value, "must be foo bus was: %s", value.string()));
        }
    }

    @Test
    void testMinimum_OK() {
        assertDoesNotThrow( () -> JsonMixed.of("""
            {"age": 22}""").validate( SomeBean.class ));
    }

    @Test
    void testMinimum_Required() {
        assertValidationError( "{}", SomeBean.class, Validation.Rule.REQUIRED, "age" );
    }

    @Test
    void testCustom_Validator() {
        assertValidationError( """
            {"age": 10, "pattern": "bar"}""", SomeBean.class, Validation.Rule.CUSTOM, "bar" );
    }

}
