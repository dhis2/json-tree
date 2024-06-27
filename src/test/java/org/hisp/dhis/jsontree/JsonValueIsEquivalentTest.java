package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the {@link JsonValue#equivalentTo(JsonValue)} method.
 */
class JsonValueIsEquivalentTest {

    @Test
    void testEquivalentTo_Undefined_Undefined() {
        JsonMixed root = JsonMixed.of( "{}" );
        assertEquivalent( root.get( "foo" ), root.get( "bar" ) );
    }

    @Test
    void testEquivalentTo_Undefined_NonUndefined() {
        JsonValue undefined = JsonMixed.of( "{}" ).get( "foo" );
        assertNotEquivalent( undefined, JsonValue.of( "null" ) );
        assertNotEquivalent( undefined, JsonValue.of( "true" ) );
        assertNotEquivalent( undefined, JsonValue.of( "false" ) );
        assertNotEquivalent( undefined, JsonValue.of( "1" ) );
        assertNotEquivalent( undefined, JsonValue.of( "\"1\"" ) );
        assertNotEquivalent( undefined, JsonValue.of( "[]" ) );
        assertNotEquivalent( undefined, JsonValue.of( "{}" ) );
    }

    @Test
    void testEquivalentTo_Null_Null() {
        assertEquivalent(JsonValue.of( "null" ), JsonValue.of( "null" ));
    }

    @Test
    void testEquivalentTo_Null_NonNull() {
        JsonValue nil = Json.ofNull();
        assertNotEquivalent( nil, JsonMixed.of( "{}" ).get( 0 ) );
        assertNotEquivalent( nil, JsonValue.of( "true" ) );
        assertNotEquivalent( nil, JsonValue.of( "false" ) );
        assertNotEquivalent( nil, JsonValue.of( "1" ) );
        assertNotEquivalent( nil, JsonValue.of( "\"1\"" ) );
        assertNotEquivalent( nil, JsonValue.of( "[]" ) );
        assertNotEquivalent( nil, JsonValue.of( "{}" ) );
    }

    @Test
    void testEquivalentTo_String_String() {
        assertEquivalent(JsonValue.of( "\"hello\"" ), JsonValue.of( "\"hello\"" ));
        assertNotEquivalent(JsonValue.of( "\"hello you\"" ), JsonValue.of( "\"hello\"" ));
    }

    @Test
    void testEquivalentTo_String_NonString() {
        assertNotEquivalent(JsonValue.of( "\"null\"" ), JsonValue.of( "null" ));
        assertNotEquivalent(JsonValue.of( "\"true\"" ), JsonValue.of( "true" ));
        assertNotEquivalent(JsonValue.of( "\"false\"" ), JsonValue.of( "false" ));
    }

    @Test
    void testEquivalentTo_Boolean_Boolean() {
        assertEquivalent(JsonValue.of( "true" ), JsonValue.of( "true" ));
        assertEquivalent(JsonValue.of( "false" ), JsonValue.of( "false" ));
        assertNotEquivalent(JsonValue.of( "true" ), JsonValue.of( "false" ));
    }

    @Test
    void testEquivalentTo_Number_Number() {
        assertEquivalent(JsonValue.of( "1" ), JsonValue.of( "1" ));
        assertEquivalent(JsonValue.of( "1.0" ), JsonValue.of( "1.0" ));
        assertEquivalent(JsonValue.of( "1" ), JsonValue.of( "1.0" ));
        assertNotEquivalent(JsonValue.of( "2" ), JsonValue.of( "2.5" ));
    }

    @Test
    void testEquivalentTo_Array_Array() {
        assertEquivalent(JsonValue.of( "[]" ), JsonValue.of( "[ ]" ));
        assertEquivalent(JsonValue.of( "[1]" ), JsonValue.of( "[ 1 ]" ));
        assertEquivalent(JsonValue.of( "[1,2]" ), JsonValue.of( "[ 1,2 ]" ));
        assertEquivalent(JsonValue.of( "[1,[2]]" ), JsonValue.of( "[ 1,[ 2] ]" ));
        assertNotEquivalent(JsonValue.of( "[2,1]" ), JsonValue.of( "[1,2 ]" ));
    }

    @Test
    void testEquivalentTo_Object_Object() {
        assertEquivalent( JsonValue.of("""
            {}"""), JsonValue.of("""
            {}""" ));
        assertEquivalent( JsonValue.of("""
            {"a": "b", "c": 4}"""), JsonValue.of("""
            {"c": 4, "a":"b"}""" ));
        assertNotEquivalent( JsonValue.of("""
            {"a": "b", "c": 4}"""), JsonValue.of("""
            {"c": 3, "a":"b"}""" ));
        assertNotEquivalent( JsonValue.of("""
            {"a": "b", "c": 4}"""), JsonValue.of("""
            {"a":"b", "c": 4, "d": null}""" ));
    }

    @Test
    void testEquivalentTo_Mixed() {
        assertEquivalent( JsonValue.of("""
            {"x": 10, "c": [4, {"foo": "bar", "y": 20}]}"""), JsonValue.of("""
            {"c": [4, {"y":20, "foo": "bar"}], "x":10}""" ));
    }

    @Test
    void testIdenticalTo_Number() {
        assertIdentical( JsonValue.of( "1"), JsonValue.of( "1") );
        assertIdentical( JsonValue.of( "1.0"), JsonValue.of( "1.0") );
        assertEquivalentButNotIdentical( JsonValue.of( "1"), JsonValue.of( "1.0") );
    }

    @Test
    void testIdenticalTo_Object() {
        assertIdentical( JsonValue.of("""
            {"a": "b", "c": 4}"""), JsonValue.of("""
            {"a":"b","c":4}""" ));
        assertEquivalentButNotIdentical( JsonValue.of("""
            {"a": "b", "c": 4}"""), JsonValue.of("""
            {"c":4, "a":"b"}""" ));
        assertEquivalentButNotIdentical( JsonValue.of("""
            {"a": "b", "c": [{},{"x": 1, "y": 1}]}"""), JsonValue.of("""
            {"a": "b", "c": [{},{"y": 1, "x": 1}]}""" ));
    }

    private static void assertEquivalent(JsonValue a, JsonValue b) {
        assertTrue( a.equivalentTo( b ) );
        assertTrue( b.equivalentTo( a ) );
    }

    private static void assertNotEquivalent(JsonValue a, JsonValue b) {
        assertFalse( a.equivalentTo( b ) );
        assertFalse( b.equivalentTo( a ) );
    }

    private static void assertIdentical(JsonValue a, JsonValue b) {
        assertTrue( a.identicalTo( b ) );
        assertTrue( b.identicalTo( a ) );
    }

    private static void assertEquivalentButNotIdentical(JsonValue a, JsonValue b) {
        assertEquivalent( a, b );
        assertFalse( a.identicalTo( b ) );
        assertFalse( b.identicalTo( a ) );
    }
}
