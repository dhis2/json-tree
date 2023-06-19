package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link JsonBoolean} specific API methods.
 */
class JsonBooleanTest {

    @Test
    void testBooleanValue_WithDefaultUndefined() {
        JsonBoolean x = JsonMixed.of( "{}" ).getBoolean( "x" );
        assertTrue( x.booleanValue( true ) );
    }

    @Test
    void testBooleanValue_WithDefaultNoBoolean() {
        JsonTreeException ex = assertThrowsExactly( JsonTreeException.class,
            () -> JsonMixed.of( "1" ).booleanValue( true ) );
        assertEquals( "Path `$` does not contain an BOOLEAN but a(n) NUMBER: 1", ex.getMessage() );
    }

    @Test
    void testBooleanValue_WithDefaultNull() {
        assertTrue( JsonMixed.of( "null" ).booleanValue( true ) );
    }

    @Test
    void testBooleanValue_WithDefaultDefined() {
        assertFalse( JsonMixed.of( "false" ).booleanValue( true ) );
    }
}
