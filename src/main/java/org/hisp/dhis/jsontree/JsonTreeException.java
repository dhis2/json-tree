package org.hisp.dhis.jsontree;

/**
 * Thrown when an operation on a {@link JsonNode} in a JSON tree is not supported or permitted by the actual node.
 * <p>
 * Mostly this is related to the {@link JsonNodeType}. An operation might only be supported by specific type of nodes.
 *
 * @author Jan Bernitt
 */
public final class JsonTreeException extends UnsupportedOperationException {

    public JsonTreeException( String message ) {
        super( message );
    }
}
