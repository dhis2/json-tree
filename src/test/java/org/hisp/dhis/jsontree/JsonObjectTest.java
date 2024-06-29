package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for methods declared as default methods in {@link JsonObject}.
 *
 * @author Jan Bernitt
 */
class JsonObjectTest {

    @Test
    void testHas_NoObject() {
        JsonMixed value = JsonMixed.of( "1" );
        assertThrowsExactly( JsonTreeException.class, () -> value.has( "x" ) );
    }

    @Test
    void testNames_Undefined() {
        JsonObject value = JsonMixed.of( "{}" ).getObject( "x" );
        assertEquals( List.of(), value.names() );
    }

    @Test
    void testNames_NoObject() {
        JsonMixed value = JsonMixed.of( "1" );
        assertThrowsExactly( JsonTreeException.class, value::names );
    }

    @Test
    void testNames_Empty() {
        JsonMixed value = JsonMixed.of( "{}" );
        assertEquals( List.of(), value.names() );
    }

    @Test
    void testNames_NonEmpty() {
        //language=json
        String json = """
            {"a":1,"b":2}""";
        JsonMixed value = JsonMixed.of( json );
        assertEquals( List.of( "a", "b" ), value.names() );
    }

    @Test
    void testNames_Special() {
        //language=json
        String json = """
            {".":1,"{uid}":2,"[0]": 3}""";
        JsonMixed value = JsonMixed.of( json );
        assertEquals( List.of( ".", "{uid}", "[0]" ), value.names() );
    }

    @Test
    void testPaths_Special() {
        //language=json
        String json = """
            {"root": {".":1,"{uid}":2,"[0]": 3,"normal":4}}""";
        JsonObject value = JsonMixed.of( json ).getObject( "root" );
        assertEquals( List.of( JsonPath.of( ".root{.}" ), JsonPath.of( ".root.{uid}" ), JsonPath.of( ".root.[0]" ),
                JsonPath.of( ".root.normal" ) ),
            value.paths().toList() );
    }

    @Test
    void testProject() {
        //language=json
        String json = """
            { "a": [1], "b": [2] }""";
        JsonMixed value = JsonMixed.of( json );
        JsonObject obj = value.project( e -> e.as( JsonArray.class ).get( 0 ) );
        assertSame( JsonObject.class, obj.asType() );
        assertEquals( List.of( "a", "b" ), obj.names() );
        assertEquals( List.of( 1, 2 ), List.of( obj.get( "a" ).node().value(), obj.get( "b" ).node().value() ) );
        assertTrue( obj.has( "a", "b" ) );
        assertFalse( obj.has( "a", "b", "c" ) );
    }
}
