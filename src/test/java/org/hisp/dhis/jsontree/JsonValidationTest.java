package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

/**
 * Test the JSON schema validation supported by {@link Validation}.
 */
class JsonValidationTest {

    interface JsonBean extends JsonObject {

        @Validation
        default String getFoo() {
            return getString( "foo" ).string();
        }

        String bar();
    }

    @Test
    void testPathCanBeRecorded() {
        Deque<String> rec = new LinkedList<>();
        JsonObject obj = JsonMixed.of( JsonNode.of( "{}", rec::add ) );

        obj.as( JsonBean.class ).getFoo();
        assertEquals( ".foo", rec.getLast() );
        obj.as( JsonBean.class ).bar();
        assertEquals( ".bar", rec.getLast() );
    }

    interface JsonMinimum extends JsonObject {

        @Validation(minimum = 0)
        default int age() {
            return getNumber( "age" ).intValue();
        }
    }

    @Test
    void testMinimum_TooSmall() {
        //language=json
        String json = """
            {"age":-1}""";
        assertValidationError( JsonMixed.of( json ), JsonMinimum.class, JsonSchemaValidation.Type.MINIMUM );
    }

    @Test
    void testMinimum_WrongType() {
        //language=json
        String json = """
            {"age":true}""";
        assertValidationError( JsonMixed.of( json ), JsonMinimum.class, JsonSchemaValidation.Type.TYPE );
    }

    private static void assertValidationError( JsonValue actual, Class<? extends JsonValue> schema,
        JsonSchemaValidation.Type expected ) {
        JsonSchemaException ex = assertThrowsExactly( JsonSchemaException.class, () -> actual.validate( schema ) );
        List<JsonSchemaValidation.Error> errors = ex.getInfo().errors();
        assertEquals( 1, errors.size() );
        assertEquals( expected, errors.get( 0 ).type() );
    }
}
