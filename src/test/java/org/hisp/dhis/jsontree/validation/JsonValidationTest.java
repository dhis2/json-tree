package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonNode;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.JsonSchemaException;
import org.hisp.dhis.jsontree.JsonValue;
import org.hisp.dhis.jsontree.Validation;
import org.junit.jupiter.api.Test;

import java.util.Deque;
import java.util.LinkedList;
import java.util.List;

import static org.hisp.dhis.jsontree.validation.Assertions.assertValidationError;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

/**
 * Test the JSON schema validation supported by {@link Validation}.
 */
class JsonValidationTest {

    public interface JsonBean extends JsonObject {

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
