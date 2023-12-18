package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

/**
 * Tests the {@link Json} API.
 *
 * @author Jan Bernitt
 */
class JsonTest {

    @Test
    void testOfNull() {
        assertJson( "null", Json.ofNull() );
    }

    @Test
    void testOf_Boolean() {
        assertJson( "true", Json.of(true) );
        assertJson( "false", Json.of(false) );
        assertJson( "null", Json.of((Boolean) null) );
        assertJson( "true", Json.of(Boolean.TRUE) );
        assertJson( "false", Json.of(Boolean.FALSE) );
    }

    @Test
    void testOf_String() {
        assertJson( "null", Json.of( (String) null ) );
        assertJson( "\"hello\"", Json.of( "hello" ) );
        assertJson( "\"hello\\\n"
            + "world\"", Json.of( "hello\nworld" ) );
    }

    @Test
    void testOf_Int() {
        assertJson( "1", Json.of( 1 ) );
        assertJson( "-1", Json.of( -1 ) );
    }

    @Test
    void testOf_Long() {
        assertJson( "1", Json.of( 1L ) );
        assertJson( "-1", Json.of( -1L ) );
    }

    @Test
    void testOf_Double() {
        assertJson( "1.0", Json.of( 1d ) );
        assertJson( "-1.0", Json.of( -1d ) );
        assertJson( "1.7976931348623157E308", Json.of( Double.MAX_VALUE ) );
        assertJson( "4.9E-324", Json.of( Double.MIN_VALUE ) );
        assertJson( "1.05E-12", Json.of( Double.valueOf( "10.5e-13" ) ) );
        assertThrowsExactly( JsonFormatException.class, () -> Json.of( Double.NaN ) );
        assertThrowsExactly( JsonFormatException.class, () -> Json.of( Double.POSITIVE_INFINITY ) );
        assertThrowsExactly( JsonFormatException.class, () -> Json.of( Double.NEGATIVE_INFINITY ) );
    }

    @Test
    void testOf_Float() {
        assertJson( "1.0", Json.of( 1f ) );
        assertJson( "-1.0", Json.of( -1f ) );
        assertJson( "3.4028235E38", Json.of( Float.MAX_VALUE ) );
        assertJson( "1.4E-45", Json.of( Float.MIN_VALUE ) );
        assertJson( "1.05E-12", Json.of( Float.valueOf( "10.5e-13" ) ) );
        assertThrowsExactly( JsonFormatException.class, () -> Json.of( Float.NaN ) );
        assertThrowsExactly( JsonFormatException.class, () -> Json.of( Float.POSITIVE_INFINITY ) );
        assertThrowsExactly( JsonFormatException.class, () -> Json.of( Float.NEGATIVE_INFINITY ) );
    }

    @Test
    void testOf_Number() {
        assertJson( "1", Json.of( Integer.valueOf( 1 ) ) );
        assertJson( "2", Json.of( Long.valueOf( 2L ) ) );
        assertJson( "2.4", Json.of( Float.valueOf( 2.4f ) ) );
        assertJson( "42.42", Json.of( Double.valueOf( 42.42d ) ) );
        assertJson( "null", Json.of( (Number) null ) );
        assertThrowsExactly( JsonFormatException.class, () -> Json.of( Double.valueOf( Double.NaN ) ));
        assertThrowsExactly( JsonFormatException.class, () -> Json.of( Double.valueOf(Double.POSITIVE_INFINITY ) ));
        assertThrowsExactly( JsonFormatException.class, () -> Json.of( Double.valueOf(Double.NEGATIVE_INFINITY ) ));
        assertThrowsExactly( JsonFormatException.class, () -> Json.of( Float.valueOf( Float.NaN ) ) );
        assertThrowsExactly( JsonFormatException.class, () -> Json.of( Float.valueOf( Float.POSITIVE_INFINITY ) ) );
        assertThrowsExactly( JsonFormatException.class, () -> Json.of( Float.valueOf( Float.NEGATIVE_INFINITY ) ) );
    }

    @Test
    void testArray_Stream() {
        assertJson( "[]", Json.array( Json::of, Stream.<Double>empty() ) );
        assertJson( "[1,2,3]", Json.array( Json::of, Stream.of( 1, 2, 3 ) ) );
        assertJson( "[\"1\",\"2\",\"3\"]", Json.array( Json::of, Stream.of( "1", "2", "3" ) ) );
    }

    @Test
    void testArray_Iterable() {
        assertJson( "null", Json.array( Json::of, (List<Long>) null ) );
        assertJson( "[]", Json.array( Json::of, List.<Integer>of()) );
        assertJson( "[true,false]", Json.array( Json::of, List.of(true, false )) );
        assertJson( "[\"1\",\"2\",\"3\"]", Json.array( Json::of, List.of("1", "2", "3" )) );
    }

    @Test
    void testArray_Array() {
        assertJson( "null", Json.array( Json::of, (Float[]) null ) );
        assertJson( "[]", Json.array( Json::of, new Number[0] ) );
        assertJson( "[1,null]", Json.array( Json::of, 1, null ) );
        assertJson( "[true,false]", Json.array( Json::of, true, false ) );
        assertJson( "[\"1\",\"2\",\"3\"]", Json.array( Json::of, "1", "2", "3" ) );
    }

    @Test
    void testArray_Builder() {
        assertJson( "[]", Json.array( arr -> {} ));
        assertJson( "[42]", Json.array( arr -> arr.addNumber( 42 ) ));
        assertJson( "[\"forty\",2,null]",
            Json.array( arr -> arr.addString( "forty" ).addNumber( 2 ).addString( null ) ));
    }

    @Test
    void testArray_Nest() {
        assertJson( "[[1,2,3],[4]]", Json.array( e -> Json.array(Json::of, e),
            List.of(List.of(1,2,3), List.of(4)) ) );
    }

    @Test
    void testArray_Empty() {
        assertJson( "[]", Json.array() );
    }

    @Test
    void testArray_Ints() {
        assertJson( "[1,2,3]", Json.array(1,2,3) );
    }

    @Test
    void testArray_Longs() {
        assertJson( "[1,2,3]", Json.array(1L,2L,3L) );
    }

    @Test
    void testArray_Doubles() {
        assertJson( "[1.0,2.0,3.0]", Json.array(1d,2d,3d) );
        assertThrowsExactly( JsonFormatException.class, () -> Json.array(1d, Double.NaN, 3d) );
    }

    @Test
    void testObject_Stream() {
        assertJson( "{}", Json.object( Json::of, Stream.<Map.Entry<String, Number>>empty() ));
        assertJson( "{\"a\":1}", Json.object( Json::of, Stream.of(Map.entry( "a", 1 ))));
        assertJson( "{\"a\":\"b\",\"c\":\"d\"}",
            Json.object( Json::of, Stream.of(Map.entry( "a", "b" ), Map.entry( "c", "d" ))));
    }

    @Test
    void testObject_Iterable() {
        assertJson( "null", Json.object( Json::of, (List<Map.Entry<String, Number>>) null ) );
        assertJson( "{}", Json.object( Json::of, List.<Map.Entry<String, Number>>of() ));
        assertJson( "{\"a\":true}", Json.object( Json::of, List.of(Map.entry( "a", true ))));
        assertJson( "{\"a\":\"b\",\"c\":\"d\"}",
            Json.object( Json::of, List.of(Map.entry( "a", "b" ), Map.entry( "c", "d" ))));
    }

    @Test
    void testObject_Map() {
        assertJson( "{}", Json.object( Json::of, Map.<String, Number>of() ));
        assertJson( "{\"a\":true}", Json.object( Json::of, Map.of( "a", true )));
        assertJson( "{\"a\":\"b\",\"c\":\"d\"}",
            Json.object( Json::of, new TreeMap<>(Map.of( "a", "b", "c", "d" ))));
    }

    @Test
    void testObject_Builder() {
        assertJson( "{}", Json.object( obj -> {} ));
        assertJson( "{\"a\":true,\"b\":42}",
            Json.object( obj -> obj.addBoolean( "a", true ).addNumber( "b", 42 ) ) );
    }

    @Test
    void testObject_Nest() {
        assertJson( "{\"a\":{\"b\":1}}",
            Json.object( e -> Json.object(Json::of, e), Map.of("a", Map.of("b", 1))));
    }

    @Test
    void testObject_Empty() {
        assertJson( "{}", Json.object() );
    }

    private static void assertJson( String expected, JsonValue value ) {
        assertEquals( expected, value.toJson() );
    }
}
