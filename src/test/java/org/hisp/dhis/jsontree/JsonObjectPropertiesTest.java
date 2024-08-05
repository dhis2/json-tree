package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonObject.Property;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the extraction of properties provided by {@link JsonObject#properties(Class)}
 */
class JsonObjectPropertiesTest {

    public interface User extends JsonObject {

        @Required
        default String name() {
            return getString( "name" ).string();
        }
    }

    @Test
    void test() {
        List<Property> properties = JsonObject.properties( User.class );
        assertEquals( 1, properties.size());
        Property name = properties.get( 0 );
        assertProperty( "name", JsonString.class, name );
        assertTrue( name.source().isAnnotationPresent( Required.class ) );
    }

    private static void assertProperty(String name, Class<? extends JsonValue> type, Property actual) {
        assertEquals( name, actual.name() );
        assertEquals( type, actual.type() );
    }
}
