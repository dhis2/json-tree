package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the specific API of {@link JsonMultiMap}.
 *
 * @author Jan Bernitt
 */
class JsonMultiMapTest {

    @Test
    void testToMap_Undefined() {
        JsonMultiMap<JsonNumber> obj = JsonMixed.of( "{}" ).getMultiMap( "a", JsonNumber.class );
        JsonMultiMap<JsonNumber> obj2 = JsonMixed.of( "null" ).getObject( "a" ).asMultiMap( JsonNumber.class );
        assertEquals( Map.of(), obj.toMap( JsonNumber::number ) );
        assertEquals( Map.of(), obj2.toMap( JsonNumber::number ) );
    }

    @Test
    void testToMap_NoMapObject() {
        JsonMultiMap<JsonNumber> obj = JsonMixed.of( "[1]" ).getMultiMap( 0, JsonNumber.class );
        JsonMultiMap<JsonNumber> obj2 = JsonMixed.of( "1" ).asMultiMap( JsonNumber.class );
        assertThrowsExactly( JsonTreeException.class, () -> obj.toMap( JsonNumber::number ) );
        assertThrowsExactly( JsonTreeException.class, () -> obj2.toMap( JsonNumber::number ) );
    }

    @Test
    void testToMap_Empty() {
        JsonMultiMap<JsonNumber> obj = JsonMixed.of( "{}" ).asMultiMap( JsonNumber.class );
        JsonMultiMap<JsonNumber> obj2 = JsonMixed.of( "{\"a\":{}}" ).getMultiMap( "a", JsonNumber.class );
        JsonMultiMap<JsonNumber> obj3 = JsonMixed.of( "null" ).asMultiMap( JsonNumber.class );
        assertEquals( Map.of(), obj.toMap( JsonNumber::number ) );
        assertEquals( Map.of(), obj2.toMap( JsonNumber::number ) );
        assertEquals( Map.of(), obj3.toMap( JsonNumber::number ) );
    }

    @Test
    void testToMap_NonEmpty() {
        JsonMultiMap<JsonNumber> obj = JsonMixed.of( "{\"b\":[1],\"c\":[2]}" ).asMultiMap( JsonNumber.class );
        JsonMultiMap<JsonNumber> obj2 = JsonMixed.of( "{\"a\":{\"b\":[1],\"c\":[2]}}" )
            .getMultiMap( "a", JsonNumber.class );
        assertEquals( Map.of( "b", List.of( 1 ), "c", List.of( 2 ) ), obj.toMap( JsonNumber::integer ) );
        assertEquals( Map.of( "b", List.of( 1 ), "c", List.of( 2 ) ), obj2.toMap( JsonNumber::integer ) );
    }

    @Test
    void testViewAsMultiMap_Undefined() {
        JsonMultiMap<JsonArray> obj = JsonMixed.of( "{}" ).getMultiMap( "a", JsonArray.class );
        JsonMultiMap<JsonArray> obj2 = JsonMixed.of( "null" ).getObject( "a" ).asMultiMap( JsonArray.class );
        assertDoesNotThrow( () -> obj.project( arr -> arr.get( 0 ) ) );
        assertDoesNotThrow( () -> obj2.project( arr -> arr.get( 0 ) ) );
    }

    @Test
    void testViewAsMultiMap_NoMapObject() {
        JsonMultiMap<JsonArray> obj = JsonMixed.of( "[1]" ).getMultiMap( 0, JsonArray.class );
        JsonMultiMap<JsonArray> obj2 = JsonMixed.of( "1" ).asMultiMap( JsonArray.class );
        assertDoesNotThrow( () -> obj.project( arr -> arr.get( 0 ) ) );
        assertDoesNotThrow( () -> obj2.project( arr -> arr.get( 0 ) ) );
    }

    @Test
    void testViewAsMultiMap_Empty() {
        JsonMultiMap<JsonArray> obj = JsonMixed.of( "{}" ).asMultiMap( JsonArray.class );
        JsonMultiMap<JsonArray> obj2 = JsonMixed.of( "{\"a\":{}}" ).getMultiMap( "a", JsonArray.class );
        JsonMultiMap<JsonArray> obj3 = JsonMixed.of( "null" ).asMultiMap( JsonArray.class );
        assertTrue( obj.project( arr -> arr.get( 0 ) ).isEmpty() );
        assertTrue( obj2.project( arr -> arr.get( 0 ) ).isEmpty() );
        JsonMultiMap<JsonValue> view = obj3.project( arr -> arr.get( 0 ) );
        assertThrowsExactly( JsonTreeException.class, view::isEmpty );
    }

    @Test
    void testViewAsMultiMap_NonEmpty() {
        JsonMultiMap<JsonArray> obj = JsonMixed.of( "{\"b\":[[1]],\"c\":[[2]]}" ).asMultiMap( JsonArray.class );
        JsonMultiMap<JsonArray> obj2 = JsonMixed.of( "{\"a\":{\"b\":[[1]],\"c\":[[2]]}}" )
            .getMultiMap( "a", JsonArray.class );
        assertEquals( Map.of( "b", List.of( 1 ), "c", List.of( 2 ) ),
            obj.project( arr -> arr.getNumber( 0 ) ).toMap( JsonNumber::integer ) );
        assertEquals( Map.of( "b", List.of( 1 ), "c", List.of( 2 ) ),
            obj2.project( arr -> arr.getNumber( 0 ) ).toMap( JsonNumber::integer ) );
    }

    @Test
    void testToMapWithOrder() {
        JsonMultiMap<JsonNumber> obj = JsonMixed.of( "{\"b\":[1,2],\"c\":[2,1,5,4]}" ).asMultiMap( JsonNumber.class );
        Map<String, List<Integer>> actual = obj.toMap( JsonNumber::intValue, Integer::compareTo );
        assertEquals( Map.of( "b", List.of( 1, 2 ), "c", List.of( 1, 2, 4, 5 ) ), actual );
    }
}
