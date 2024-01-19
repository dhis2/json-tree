package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.Validation;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.hisp.dhis.jsontree.Validation.Rule.MAX_LENGTH;
import static org.hisp.dhis.jsontree.Validation.Rule.MIN_LENGTH;
import static org.hisp.dhis.jsontree.Validation.Rule.REQUIRED;
import static org.hisp.dhis.jsontree.Validation.YesNo.YES;
import static org.hisp.dhis.jsontree.validation.Assertions.assertValidationError;

class JsonValidationMiscMetaAnnotationTest {

    @Retention( RUNTIME )
    @Validation( minLength = 11, maxLength = 11, required = YES )
    public @interface UID {}

    public interface JsonMetaExampleA extends JsonObject {

        @UID
        default String getUID() {
            return getString( "id" ).string();
        }
    }

    @Test
    void testMeta_MinLength() {
        assertValidationError( """
            {"id":  "hello"}""", JsonMetaExampleA.class, MIN_LENGTH, 11, 5 );
    }

    @Test
    void testMeta_MaxLength() {
        assertValidationError( """
            {"id":  "helloworld01"}""", JsonMetaExampleA.class, MAX_LENGTH, 11, 12 );
    }

    @Test
    void testMeta_Required() {
        assertValidationError( "{}", JsonMetaExampleA.class, REQUIRED, "id" );
    }
}
