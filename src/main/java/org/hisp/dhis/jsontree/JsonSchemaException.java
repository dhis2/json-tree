package org.hisp.dhis.jsontree;

import java.util.List;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

/**
 * Thrown when an JSON input does not match its JSON schema description.
 */
public final class JsonSchemaException extends IllegalArgumentException {

    private final transient Info info;

    public record Info(JsonValue value, Class<? extends JsonValue> schema, List<Validation.Error> errors) {}

    public JsonSchemaException( String message, Info info ) {
        super( message+toString( info.errors() ) );
        this.info = info;
    }

    public Info getInfo() {
        return info;
    }

    private static String toString(List<Validation.Error> errors) {
        return errors.stream().map( e -> "\n\t"+ e.toString()).collect( joining());
    }
}
