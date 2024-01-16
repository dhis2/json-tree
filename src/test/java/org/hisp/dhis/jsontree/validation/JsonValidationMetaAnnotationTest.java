package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.JsonValue;
import org.hisp.dhis.jsontree.Validation;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.hisp.dhis.jsontree.Validation.YesNo.YES;

class JsonValidationMetaAnnotationTest {

    @Retention( RUNTIME )
    @Validation(minLength = 11, maxLength = 11, required = YES)
    public @interface UID {}

    public interface Bean extends JsonObject {
        @UID
        default String getUID() {
            return getString( "id" ).string();
        };
    }

    @Test
    void test() {
        String json = """
        {"id":  "hello"}
        """;
        JsonValidator.validate( JsonValue.of( json ),  Bean.class );
    }

    @Test
    void test2() {
        String json = """
        {}
        """;
        JsonValidator.validate( JsonValue.of( json ),  Bean.class );
    }
}
