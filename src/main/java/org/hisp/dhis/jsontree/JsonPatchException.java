package org.hisp.dhis.jsontree;

/**
 * When a {@link JsonPatch} operation fails.
 *
 * @author Jan Bernitt
 * @since 1.1
 */
public final class JsonPatchException extends IllegalArgumentException {

    public JsonPatchException(String msg ) {
        super( msg );
    }
}
