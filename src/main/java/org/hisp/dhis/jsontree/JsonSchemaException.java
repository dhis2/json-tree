package org.hisp.dhis.jsontree;

/**
 * Thrown when an JSON input does not match its JSON schema description.
 */
public final class JsonSchemaException extends IllegalArgumentException {

    public JsonSchemaException( String s ) {
        super( s );
    }
}
