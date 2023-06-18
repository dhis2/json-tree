package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tests for the {@link JsonArray} specific API methods.
 *
 * @author Jan Bernitt
 */
class JsonArrayTest {

    @Test
    void testStringValues_NoArray() {
        JsonMixed value = JsonMixed.of( "1" );
        assertThrowsExactly( JsonTreeException.class, value::stringValues );
    }

    @Test
    void testStringValues_NotOnlyStrings() {
        JsonMixed value = JsonMixed.of( "[\"a\", 1, true]" );
        JsonTreeException ex = assertThrowsExactly( JsonTreeException.class, value::stringValues );
        assertEquals( "Array element is not a java.lang.String: 1", ex.getMessage() );
    }

    @Test
    void testNumberValues_NoArray() {
        JsonMixed value = JsonMixed.of( "1" );
        assertThrowsExactly( JsonTreeException.class, value::numberValues );
    }

    @Test
    void testNumberValues_NotOnlyNumbers() {
        JsonMixed value = JsonMixed.of( "[1, true, \"a\"]" );
        JsonTreeException ex = assertThrowsExactly( JsonTreeException.class, value::numberValues );
        assertEquals( "Array element is not a java.lang.Number: true", ex.getMessage() );
    }

    @Test
    void testBoolValues_NoArray() {
        JsonMixed value = JsonMixed.of( "1" );
        assertThrowsExactly( JsonTreeException.class, value::boolValues );
    }

    @Test
    void testBoolValues_NotOnlyBooleans() {
        JsonMixed value = JsonMixed.of( "[true, 1, \"a\"]" );
        JsonTreeException ex = assertThrowsExactly( JsonTreeException.class, value::boolValues );
        assertEquals( "Array element is not a java.lang.Boolean: 1", ex.getMessage() );
    }

    @Test
    void testForEach_Empty() {
        JsonMixed array = JsonMixed.of( "[]" );
        array.forEach( e -> fail( "should never be called but was with: " + e ) );
    }

    @Test
    void testForEach_NonEmpty() {
        JsonMixed array = JsonMixed.of( "[1,2]" );
        List<Object> actual = new ArrayList<>();
        array.forEach( e -> actual.add( e.node().value() ) );
        assertEquals( List.of( 1, 2 ), actual );
    }

    @Test
    void testForEach_NoArray() {
        JsonMixed value = JsonMixed.of( "1" );
        assertThrowsExactly( JsonTreeException.class, () -> value.forEach( e -> fail() ) );
    }
}
