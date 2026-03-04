package org.hisp.dhis.jsontree;

import java.lang.reflect.Type;

/**
 * Thrown then {@link org.hisp.dhis.jsontree.JsonAccessors.JsonAccessor#access(JsonObject, String,
 * Type, JsonAccessors)} cannot convert the value found in the JSON to the target Java type.
 *
 * @since 1.9
 */
public class JsonAccessException extends IllegalStateException {

    public JsonAccessException( String message ) {
        super( message );
    }
}
