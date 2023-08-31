package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests the specific API of {@link JsonMap}.
 *
 * @author Jan Bernitt
 */
class JsonMapTest {

    @Test
    void testKeys_Undefined() {
        JsonMap<JsonString> obj = JsonMixed.of( "{}" ).getMap( "a", JsonString.class );
        JsonMap<JsonString> obj2 = JsonMixed.of( "null" ).getObject( "a" ).asMap( JsonString.class );
        assertThrowsExactly( JsonPathException.class, obj::keys );
        assertThrowsExactly( JsonPathException.class, obj2::keys );

        // with default
        assertEquals( Set.of(), obj.keys( Set.of() ) );
        assertEquals( Set.of(), obj2.keys( Set.of() ) );
    }

    @Test
    void testKeys_NoMapObject() {
        JsonMap<JsonString> obj = JsonMixed.of( "[1]" ).getMap( 0, JsonString.class );
        JsonMap<JsonString> obj2 = JsonMixed.of( "null" ).asMap( JsonString.class );
        assertThrowsExactly( JsonTreeException.class, obj::keys );
        assertThrowsExactly( JsonTreeException.class, obj2::keys );

        // with default
        assertThrowsExactly( JsonTreeException.class, () -> obj.keys( Set.of() ) );
        assertThrowsExactly( JsonTreeException.class, () -> obj2.keys( Set.of() ) );
    }

    @Test
    void testKeys_Empty() {
        JsonMap<JsonString> obj = JsonMixed.of( "{}" ).asMap( JsonString.class );
        JsonMap<JsonString> obj2 = JsonMixed.of( "{\"a\":{}}" ).getMap( "a", JsonString.class );
        assertEquals( Set.of(), obj.keys() );
        assertEquals( Set.of(), obj2.keys() );
    }

    @Test
    void testKeys_NonEmpty() {
        JsonMap<JsonString> obj = JsonMixed.of( "{\"b\":1,\"c\":2}" ).asMap( JsonString.class );
        JsonMap<JsonString> obj2 = JsonMixed.of( "{\"a\":{\"b\":1,\"c\":2}}" ).getMap( "a", JsonString.class );
        assertEquals( Set.of( "b", "c" ), obj.keys() );
        assertEquals( Set.of( "b", "c" ), obj2.keys() );
    }

    @Test
    void testForEach_Undefined() {
        JsonMap<JsonString> obj = JsonMixed.of( "{}" ).getMap( "a", JsonString.class );
        JsonMap<JsonString> obj2 = JsonMixed.of( "null" ).getObject( "a" ).asMap( JsonString.class );
        assertThrowsExactly( JsonPathException.class, () -> obj.forEach( ( k, v ) -> fail() ) );
        assertThrowsExactly( JsonPathException.class, () -> obj2.forEach( ( k, v ) -> fail() ) );
    }

    @Test
    void testForEach_NoMapObject() {
        JsonMap<JsonString> obj = JsonMixed.of( "[1]" ).getMap( 0, JsonString.class );
        JsonMap<JsonString> obj2 = JsonMixed.of( "null" ).asMap( JsonString.class );
        assertThrowsExactly( JsonTreeException.class, () -> obj.forEach( ( k, v ) -> fail() ) );
        assertThrowsExactly( JsonTreeException.class, () -> obj2.forEach( ( k, v ) -> fail() ) );
    }

    @Test
    void testForEach_Empty() {
        JsonMap<JsonString> obj = JsonMixed.of( "{}" ).asMap( JsonString.class );
        JsonMap<JsonString> obj2 = JsonMixed.of( "{\"a\":{}}" ).getMap( "a", JsonString.class );
        obj.forEach( ( k, v ) -> fail() );
        obj2.forEach( ( k, v ) -> fail() );
    }

    @Test
    void testForEach_NonEmpty() {
        JsonMap<JsonString> obj = JsonMixed.of( "{\"b\":1,\"c\":2}" ).asMap( JsonString.class );
        JsonMap<JsonString> obj2 = JsonMixed.of( "{\"a\":{\"b\":1,\"c\":2}}" ).getMap( "a", JsonString.class );

        Map<String, Object> actual = new HashMap<>();
        Map<String, Object> actual2 = new HashMap<>();
        obj.forEach( ( k, v ) -> actual.put( k, v.node().value() ) );
        obj2.forEach( ( k, v ) -> actual2.put( k, v.node().value() ) );
        assertEquals( Map.of( "b", 1, "c", 2 ), actual );
        assertEquals( Map.of( "b", 1, "c", 2 ), actual2 );
    }

    @Test
    void testViewAsMap_Undefined() {
        JsonMap<JsonArray> obj = JsonMixed.of( "{}" ).getMap( "a", JsonArray.class );
        JsonMap<JsonArray> obj2 = JsonMixed.of( "null" ).getObject( "a" ).asMap( JsonArray.class );
        assertDoesNotThrow( () -> obj.viewAsMap( arr -> arr.get( 0 ) ) );
        assertDoesNotThrow( () -> obj2.viewAsMap( arr -> arr.get( 0 ) ) );
    }

    @Test
    void testViewAsMap_NoMapObject() {
        JsonMap<JsonArray> obj = JsonMixed.of( "[1]" ).getMap( 0, JsonArray.class );
        JsonMap<JsonArray> obj2 = JsonMixed.of( "null" ).asMap( JsonArray.class );
        assertDoesNotThrow( () -> obj.viewAsMap( arr -> arr.get( 0 ) ) );
        assertDoesNotThrow( () -> obj2.viewAsMap( arr -> arr.get( 0 ) ) );
    }

    @Test
    void testViewAsMap_Empty() {
        JsonMap<JsonArray> obj = JsonMixed.of( "{}" ).asMap( JsonArray.class );
        JsonMap<JsonArray> obj2 = JsonMixed.of( "{\"a\":{}}" ).getMap( "a", JsonArray.class );
        assertTrue( obj.viewAsMap( arr -> arr.get( 0 ) ).isEmpty() );
        assertTrue( obj2.viewAsMap( arr -> arr.get( 0 ) ).isEmpty() );
    }

    @Test
    void testViewAsMap_NonEmpty() {
        JsonMap<JsonArray> obj = JsonMixed.of( "{\"b\":[1],\"c\":[2]}" ).asMap( JsonArray.class );
        JsonMap<JsonArray> obj2 = JsonMixed.of( "{\"a\":{\"b\":[1],\"c\":[2]}}" ).getMap( "a", JsonArray.class );
        assertEquals( Set.of( "b", "c" ), obj.viewAsMap( arr -> arr.get( 0 ) ).keys() );
        assertEquals( Set.of( "b", "c" ), obj2.viewAsMap( arr -> arr.get( 0 ) ).keys() );
        assertEquals( 1, obj.viewAsMap( arr -> arr.getNumber( 0 ) ).get( "b" ).intValue() );
        assertEquals( 1, obj2.viewAsMap( arr -> arr.getNumber( 0 ) ).get( "b" ).intValue() );
    }

}
