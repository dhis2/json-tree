package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
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
        assertEquals(List.of(), obj.keys().toList() );
        assertEquals(List.of(), obj2.keys().toList() );
    }

    @Test
    void testKeys_NoMapObject() {
        JsonMap<JsonString> obj = JsonMixed.of( "[1]" ).getMap( 0, JsonString.class );
        JsonMap<JsonString> obj2 = JsonMixed.of( "1" ).asMap( JsonString.class );
        assertThrowsExactly( JsonTreeException.class, obj::keys );
        assertThrowsExactly( JsonTreeException.class, obj2::keys );
    }

    @Test
    void testKeys_Empty() {
        JsonMap<JsonString> obj = JsonMixed.of( "{}" ).asMap( JsonString.class );
        JsonMap<JsonString> obj2 = JsonMixed.of( "{\"a\":{}}" ).getMap( "a", JsonString.class );
        assertEquals( List.of(), obj.keys().toList() );
        assertEquals( List.of(), obj2.keys().toList() );
    }

    @Test
    void testKeys_NonEmpty() {
        JsonMap<JsonNumber> obj = JsonMixed.of( "{\"b\":1,\"c\":2}" ).asMap( JsonNumber.class );
        JsonMap<JsonNumber> obj2 = JsonMixed.of( "{\"a\":{\"b\":1,\"c\":2}}" ).getMap( "a", JsonNumber.class );
        assertEquals( List.of( "b", "c" ), obj.keys().toList() );
        assertEquals( List.of( "b", "c" ), obj2.keys().toList() );
    }

    @Test
    void testForEach_Undefined() {
        JsonMap<JsonString> obj = JsonMixed.of( "{}" ).getMap( "a", JsonString.class );
        JsonMap<JsonString> obj2 = JsonMixed.of( "null" ).getObject( "a" ).asMap( JsonString.class );
        obj.forEach( ( k, v ) -> fail("Should not be called") );
        obj2.forEach( ( k, v ) -> fail("Should not be called") );
    }

    @Test
    void testForEach_NoMapObject() {
        JsonMap<JsonString> obj = JsonMixed.of( "[1]" ).getMap( 0, JsonString.class );
        JsonMap<JsonString> obj2 = JsonMixed.of( "1" ).asMap( JsonString.class );
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
        JsonMap<JsonNumber> obj = JsonMixed.of( "{\"b\":1,\"c\":2}" ).asMap( JsonNumber.class );
        JsonMap<JsonNumber> obj2 = JsonMixed.of( "{\"a\":{\"b\":1,\"c\":2}}" ).getMap( "a", JsonNumber.class );

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
        assertEquals( List.of( "b", "c" ), obj.viewAsMap( arr -> arr.get( 0 ) ).keys().toList() );
        assertEquals( List.of( "b", "c" ), obj2.viewAsMap( arr -> arr.get( 0 ) ).keys().toList() );
        assertEquals( 1, obj.viewAsMap( arr -> arr.getNumber( 0 ) ).get( "b" ).intValue() );
        assertEquals( 1, obj2.viewAsMap( arr -> arr.getNumber( 0 ) ).get( "b" ).intValue() );
    }

    @Test
    void testViewAsMap_Keys() {
        JsonMap<JsonArray> obj = JsonMixed.of( "{\"b\":[1],\"c\":[2]}" ).asMap( JsonArray.class );
        JsonMap<JsonNumber> view = obj.viewAsMap( arr -> arr.getNumber( 0 ) );
        assertEquals( List.of("b", "c"), view.keys().toList() );
    }

    @Test
    void testViewAsMap_Values() {
        JsonMap<JsonArray> obj = JsonMixed.of( "{\"b\":[1],\"c\":[2]}" ).asMap( JsonArray.class );
        JsonMap<JsonNumber> view = obj.viewAsMap( arr -> arr.getNumber( 0 ) );
        assertEquals( List.of(1, 2), view.values().map( JsonNumber::intValue ).toList() );
    }
}
