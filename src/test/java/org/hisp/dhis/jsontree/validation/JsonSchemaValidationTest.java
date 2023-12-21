package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonList;
import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.JsonString;
import org.hisp.dhis.jsontree.JsonValue;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

class JsonSchemaValidationTest {

    @Test
    void test() {
        ObjectValidation validation = ObjectValidation.getInstance( JsonSchema.JsonValidation.class );
        ObjectValidator validator = ObjectValidator.getInstance( validation );
        validation.properties().forEach( (k,v) -> System.out.printf( "%s=%s%n", k,v ) );
        System.out.println(validator);
    }

    @Test
    void test2() {
        ObjectValidation validation = ObjectValidation.getInstance( TypeUseExample.class );
        ObjectValidator validator = ObjectValidator.getInstance( validation );
        validation.properties().forEach( (k,v) -> System.out.printf( "%s=%s%n", k,v ) );
        System.out.println(validator);

        JsonValue json = JsonValue.of( """
            {
            "data": [["yes", "hello"], []],
            "data2": []
            }
            """ );
        json.validate(TypeUseExample.class);
    }

    public record Foo() implements Validation.Validator {

        @Override public void validate( JsonMixed value, Consumer<Validation.Error> addError ) {

        }
    }

    public interface TypeUseExample extends JsonObject {

        @Validator( Foo.class )
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
