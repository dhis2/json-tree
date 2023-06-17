package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void testHas_NoObject() {
        JsonMixed value = JsonMixed.of( "1" );
        assertThrows( JsonTreeException.class, () -> value.has( "x" ) );
    }

    @Test
    void testNames_NoObject() {
        JsonMixed value = JsonMixed.of( "1" );
        assertThrows( JsonTreeException.class, value::names );
    }

    @Test
    void testViewAsObject() {
        //language=json
        String json = """
        {
            "a": [1],
            "b": [2]
        }""";
        JsonMixed value = JsonMixed.of( json );
        JsonObject obj = value.viewAsObject( e -> e.as( JsonArray.class ).get( 0 ) );
        assertSame( JsonObject.class, obj.asType() );
        assertEquals( List.of("a", "b"), obj.names() );
        assertEquals( List.of(1,2), List.of(obj.get( "a" ).node().value(), obj.get( "b" ).node().value()));
    }
}
