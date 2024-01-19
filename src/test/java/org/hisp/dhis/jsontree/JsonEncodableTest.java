package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Jan Bernitt
 */
class JsonEncodableTest {

    record Point(int x, int y) implements JsonBuilder.JsonObjectEncodable {

        public void addToObject( JsonBuilder.JsonObjectBuilder p ) {
            p.addNumber( "x", x ).addNumber( "y", y );
        }
    }

    @Test
    void testPoint() {
        assertEquals( "{\"origin\":{\"x\":0,\"y\":0}}",
            Json.object( obj -> obj.addMember( "origin", new Point( 0, 0 ) ) ).toJson() );
    }
}
