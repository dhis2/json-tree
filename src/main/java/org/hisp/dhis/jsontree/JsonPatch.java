package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonNodeOperation.Insert;
import org.hisp.dhis.jsontree.JsonNodeOperation.Remove;

import java.util.ArrayList;
import java.util.List;

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

    @Validation( dependentRequired = { "add", "replace", "test" }, acceptNull = YES)
    default JsonMixed getValue() {
        return get( "value", JsonMixed.class );
    }

    @Validation( dependentRequired = { "copy", "move" } )
    default JsonPointer getFrom() {
        return getString( "from" ).parsed( JsonPointer::new );
    }

    static JsonValue apply(JsonValue value, JsonList<JsonPatch> ops) throws JsonPatchException {
        return JsonValue.of( value.node().patch(JsonPatch.operations( value.as( JsonMixed.class ), ops )));
    }

    /**
     * Converts a json-patch to {@link JsonNodeOperation}s.
     *
     * @param value the target value
     * @param with the patch to apply
     * @return list of {@link JsonNodeOperation}s to apply to get the patch effect
     */
    private static List<JsonNodeOperation> operations(JsonMixed value, JsonList<JsonPatch> with) {
        List<JsonNodeOperation> ops = new ArrayList<>();
        int i = 0;
        for (JsonPatch op : with) {
            op.validate( JsonPatch.class );
            String path = op.getPath().path();
            //FIXME if path is - (append) then this must be substituted with the actual first index after the last
            switch ( op.getOperation() ) {
                case ADD -> ops.add( new Insert(path, op.getValue().node() ) );
                case REMOVE -> ops.add( new Remove( path ) );
                case REPLACE -> {
                    ops.add( new Remove( path));
                    ops.add( new Insert( JsonNode.nextIndexPath( path ), op.getValue().node() ));
                }
                case MOVE -> {
                    String from = op.getFrom().path();
                    ops.add( new Remove( from ) );
                    ops.add( new Insert( path, value.get( from ).node()));
                }
                case COPY -> {
                    String from = op.getFrom().path();
                    ops.add( new Insert( path, value.get( from ).node()) );
                }
                case TEST -> {
                    if ( !value.get( path ).equivalentTo( op.getValue() ) )
                        throw new JsonPatchException("operation %d failed its test: %s".formatted( i, op.toJson() ) );
                }
            }
            i++;
        }
        return ops;
    }
    // V A L I D A T I O N S
    // pointer:
    // leading zeros in pointer seg
    // tailing /
    // pointer does not start with /
    // null as pointer path
    // pointer + tree:
    // pointer implies parent of different node type
    // index must be in range 0-length (length means append new)

    // operation:
    // target does not exist (remove/replace)
}
