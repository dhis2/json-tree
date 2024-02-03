package org.hisp.dhis.jsontree;

import static org.hisp.dhis.jsontree.Validation.YesNo.YES;

/**
 * A JSON patch operation as defined by <a href="https://datatracker.ietf.org/doc/html/rfc6902/">RFC-6902</a>
 * (see <a href="https://jsonpatch.com/">jsonpatch.com</a>).
 *
 * @author Jan Bernitt
 * @since 1.1
 */
public interface JsonPatch extends JsonObject {

    enum Op {ADD, REMOVE, REPLACE, COPY, MOVE, TEST}

    @Required
    @Validation( dependentRequired = { "=add", "=replace", "=copy", "=move", "=test" }, caseInsensitive = YES )
    default Op getOperation() {
        return getString( "op" ).parsed( str -> Op.valueOf( str.toUpperCase() ) );
    }

    @Required
    default JsonPointer getPath() {
        return getString( "path" ).parsed( JsonPointer::new );
    }

    @Validation( dependentRequired = { "add", "replace", "test" } )
    default JsonMixed getValue() {
        return get( "value", JsonMixed.class );
    }

    @Validation( dependentRequired = { "copy", "move" } )
    default JsonPointer getFrom() {
        return getString( "from" ).parsed( JsonPointer::new );
    }
}
