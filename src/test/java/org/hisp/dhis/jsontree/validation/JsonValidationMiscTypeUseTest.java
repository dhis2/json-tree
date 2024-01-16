package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonList;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.JsonString;
import org.hisp.dhis.jsontree.JsonValue;
import org.hisp.dhis.jsontree.Validation;
import org.junit.jupiter.api.Test;

import java.util.List;

class JsonValidationMiscTypeUseTest {

    @Test
    void testMethodLevel() {
        ObjectValidation validation = ObjectValidation.getInstance( JsonSchema.JsonValidation.class );
        ObjectValidator validator = ObjectValidator.getInstance( validation );
        validation.properties().forEach( (k,v) -> System.out.printf( "%s=%s%n", k,v ) );
        System.out.println(validator);
    }

    @Test
    void testTyeUse_MultiLevel() {
        ObjectValidation validation = ObjectValidation.getInstance( JsonTypeUseExample.class );
        ObjectValidator validator = ObjectValidator.getInstance( validation );
        validation.properties().forEach( (k,v) -> System.out.printf( "%s=%s%n", k,v ) );
        System.out.println(validator);

        JsonValue json = JsonValue.of( """
            {
            "data": [["yes", "hello"], []],
            "data2": []
            }
            """ );
        json.validate( JsonTypeUseExample.class);
    }

    public interface JsonTypeUseExample extends JsonObject {

        @Validation(minItems = 1)
        default List<@Validation(maxItems = 10) List<@Validation(pattern = "123") String>> getData2() {
            return getArray( "data2" ).values( e -> List.of() );
        }

        @SuppressWarnings( {"unchecked" } )
        @Validation(minItems = 2)
        default JsonList<@Validation(maxItems = 10) JsonList<@Validation(maxLength = 4) JsonString>> getData() {
            return get( "data", JsonList.class );
        }
    }
}
