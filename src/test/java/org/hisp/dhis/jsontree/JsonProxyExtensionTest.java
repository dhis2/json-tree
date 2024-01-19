package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonProxyExtensionTest {

    interface JsonPage extends JsonObject {

        default <T extends JsonValue> JsonList<T> entries( Class<T> of ) {
            return getList( "entries", of );
        }
    }

    @Test
    void testExtensionMethodsCanHaveArguments() {
        String json = """
            { "entries": [1,2,3] }
            """;
        JsonPage page = JsonMixed.of( json ).as( JsonPage.class );
        assertEquals( List.of( 1, 2, 3 ),
            page.entries( JsonNumber.class ).toList( JsonNumber::intValue ) );
    }
}
