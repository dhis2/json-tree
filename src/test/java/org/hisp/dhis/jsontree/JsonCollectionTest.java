package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

/**
 * Tests the {@link JsonAbstractCollection} specific API methods.
 *
 * @author Jan Bernitt
 */
class JsonCollectionTest {

    @Test
    void testIsEmpty_Undefined() {
        JsonObject obj = JsonMixed.of( "{}" ).getObject( "a" );
        assertThrowsExactly( JsonPathException.class, obj::isEmpty );
        JsonArray arr = JsonMixed.of( "{}" ).getArray( "a" );
        assertThrowsExactly( JsonPathException.class, arr::isEmpty );
    }

    @Test
    void testIsEmpty_NoCollection() {
        JsonMixed obj = JsonMixed.of( "1" );
        assertThrowsExactly( JsonTreeException.class, obj::size );
    }
}
