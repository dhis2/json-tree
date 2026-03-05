package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.Validation;
import org.junit.jupiter.api.Test;

import static org.hisp.dhis.jsontree.Assertions.assertValidationError;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests validation of the {@link org.hisp.dhis.jsontree.Validation} when used on a {@link Record} type.
 *
 * @author Jan Bernitt
 */
class JsonValidationRecordTest {

    record SomeBean(@Validation(minimum = 0) int age) {}

    @Test
    void testMinimum_OK() {
        assertDoesNotThrow( () -> JsonMixed.of("""
            {"age": 22}""").validate( SomeBean.class ));
    }

    @Test
    void testMinimum_Required() {
        assertValidationError( "{}", SomeBean.class, Validation.Rule.REQUIRED, "age" );
    }

}
