package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for methods declared as default methods in {@link JsonObject}.
 *
 * @author Jan Bernitt
 */
class JsonObjectTest {

    @Test
    void testFind() {
        //language=JSON
        String json = """
            {
                "x":{ "foo": 1 }
            }""";
        JsonMixed root = JsonMixed.of( json );
        assertTrue( root.find( JsonObject.class, obj -> obj.has( "foo" ) ).isObject() );
        assertTrue( root.find( JsonObject.class, obj -> obj.has( "bar" ) ).isNull() );
    }
}
