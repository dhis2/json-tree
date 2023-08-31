package org.hisp.dhis.jsontree;

import java.util.List;

/**
 * Thrown when an JSON input does not match its JSON schema description.
 */
public final class JsonSchemaException extends IllegalArgumentException {

    private final transient Info info;

    public record Info(JsonValue value, Class<? extends JsonValue> schema, List<Validation.Error> errors) {}

    public JsonSchemaException( String message, Info info ) {
        super( message );
        this.info = info;
    }

    public Info getInfo() {
        return info;
    }
}
