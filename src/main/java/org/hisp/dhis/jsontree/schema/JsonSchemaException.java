package org.hisp.dhis.jsontree.schema;

/**
 * Thrown when an JSON input does not match its JSON schema description.
 */
public class JsonSchemaException extends IllegalArgumentException {

    public JsonSchemaException( String s ) {
        super( s );
    }
}
