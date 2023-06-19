package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.Deque;
import java.util.LinkedList;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonValidationTest {

    interface JsonBean extends JsonObject {

        @Validation
        default String getFoo() {
            return getString( "foo" ).string();
        }

        String bar();
    }

    @Test
    void testPathCanBeRecorded() {
        Deque<String> rec = new LinkedList<>();
        JsonObject obj = JsonMixed.of( JsonNode.of( "{}", rec::add ) );

        obj.as( JsonBean.class ).getFoo();
        assertEquals( ".foo", rec.getLast() );
        obj.as( JsonBean.class ).bar();
        assertEquals( ".bar", rec.getLast() );
    }
}
