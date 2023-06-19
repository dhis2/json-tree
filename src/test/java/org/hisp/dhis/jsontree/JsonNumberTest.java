package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

/**
 * Tests the specific API of the {@link JsonNumber} methods.
 *
 * @author Jan Bernitt
 */
class JsonNumberTest {

    @Test
    void testInteger_Undefined() {
        assertNull( JsonMixed.of( "{}" ).getNumber( "x" ).integer() );
    }

    @Test
    void testInteger_Null() {
        assertNull( JsonMixed.of( "null" ).integer() );
    }

    @Test
    void testInteger_NoFractionNumber() {
        assertEquals( 42, JsonMixed.of( "42" ).integer() );
    }

    @Test
    void testInteger_FractionNumber() {
        assertEquals( 42, JsonMixed.of( "42.5" ).integer() );
    }

    @Test
    void testIntValue_Undefined() {
        JsonNumber x = JsonMixed.of( "{}" ).getNumber( "x" );
        assertThrowsExactly( JsonPathException.class, x::intValue );
    }

    @Test
    void testIntValue_NoNumber() {
        JsonNumber x = JsonMixed.of( "true" );
        assertThrowsExactly( JsonTreeException.class, x::intValue );
    }

    @Test
    void testIntValue_Null() {
        JsonMixed val = JsonMixed.of( "null" );
        assertThrowsExactly( NullPointerException.class, val::intValue );
    }

    @Test
    void testIntValue_NoFractionNumber() {
        assertEquals( 42, JsonMixed.of( "42" ).intValue() );
    }

    @Test
    void testIntValue_FractionNumber() {
        assertEquals( 42, JsonMixed.of( "42.5" ).intValue() );
    }

    @Test
    void testIntValue_WithDefaultUndefined() {
        JsonNumber x = JsonMixed.of( "{}" ).getNumber( "x" );
        assertEquals( 42, x.intValue( 42 ) );
    }

    @Test
    void testIntValue_WithDefaultNoNumber() {
        JsonNumber x = JsonMixed.of( "true" );
        assertThrowsExactly( JsonTreeException.class, () -> x.intValue( 42 ) );
    }

    @Test
    void testIntValue_WithDefaultNull() {
        JsonMixed x = JsonMixed.of( "null" );
        assertEquals( 42, x.intValue( 42 ) );
    }

    @Test
    void testIntValue_WithDefaultNoFractionNumber() {
        assertEquals( 42, JsonMixed.of( "42" ).intValue( 55 ) );
    }

    @Test
    void testIntValue_WithDefaultFractionNumber() {
        assertEquals( 42, JsonMixed.of( "42.5" ).intValue( 55 ) );
    }

    @Test
    void testNumber_WithDefaultUndefined() {
        JsonNumber x = JsonMixed.of( "{}" ).getNumber( "x" );
        assertEquals( 42, x.number( 42 ) );
    }

    @Test
    void testNumber_WithDefaultNoNumber() {
        JsonNumber x = JsonMixed.of( "true" );
        assertThrowsExactly( JsonTreeException.class, () -> x.number( 42 ) );
    }

    @Test
    void testNumber_WithDefaultNull() {
        JsonMixed x = JsonMixed.of( "null" );
        assertEquals( 42, x.number( 42 ) );
    }

    @Test
    void testNumber_WithDefaultNoFractionNumber() {
        assertEquals( 42, JsonMixed.of( "42" ).number( 55 ) );
    }

    @Test
    void testNumber_WithDefaultFractionNumber() {
        assertEquals( 42.5d, JsonMixed.of( "42.5" ).number( 55d ) );
    }

}
