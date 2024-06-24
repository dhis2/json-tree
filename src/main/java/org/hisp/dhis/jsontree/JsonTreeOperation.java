package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.Maybe;
import org.hisp.dhis.jsontree.internal.Surly;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static java.lang.Integer.parseInt;
import static java.util.Comparator.comparing;
import static org.hisp.dhis.jsontree.JsonNode.parentPath;

/**
 * {@link JsonTreeOperation}s are used internally to make modifications to a {@link JsonTree}.
 * <p>
 * In contrast to {@link JsonNodeOperation}s they are based on a {@link #target()} {@link JsonNode} and their order
 * matters as they have to be applied in order of their {@link #sortIndex()} to cut and paste a new {@link JsonTree}
 * together without forcing intermediate representations (which is most of all a performance optimisation.).
 * <p>
 * This means the {@link JsonNodeOperation#path()} has been resolved to a {@link JsonNode} in an actual target
 * {@link JsonTree}. The paths are interpreted relative to the target {@link JsonNode} provided in the
 * {@link #of(JsonNodeOperation, JsonNode)} method.
 *
 * @author Jan Bernitt
 * @since 1.1
 */
sealed interface JsonTreeOperation {

    /**
     * @return the node to modify or if no such mode exists the parent that gets modified
     */
    JsonNode target();

    /**
     * The goal of this index is to bring the operations into an order that allows to copy together a new tree from the
     * original tree and nodes brought in by the operations.
     *
     * @return the index in the underlying JSON input that is (or is near) the start position where a modification takes
     * place. Usually this is the start of the modified node but for example if new members or elements are added to an
     * object/array these are appended, therefore this points to the end of the parent they are appended to
     */
    default int sortIndex() {
        return target().startIndex();
    }

    /*
     * Object operations
     */

    record RemoveMember(JsonNode target, String name) implements JsonTreeOperation { }

    record ReplaceMember(JsonNode target, String name, JsonNode value) implements JsonTreeOperation { }

    /**
     *
     * @param target the parent members are added to
     * @param values a map of all members to add
     */
    record AddMembers(JsonNode target, Map<String, JsonNode> values) implements JsonTreeOperation {

        @Override public int sortIndex() {
            return target.endIndex();
        }
    }

    /*
     * Array operations
     */

    record RemoveElements(JsonNode target, int index) implements JsonTreeOperation { }

    record InsertElements(JsonNode target, int index, List<JsonNode> value) implements JsonTreeOperation {}

    record AppendElements(JsonNode target, List<JsonNode> values) implements JsonTreeOperation {

        @Override public int sortIndex() {
            return target.endIndex();
        }
    }

    @Surly
    static List<JsonTreeOperation> of(List<JsonNodeOperation> ops, JsonNode target) {
        return ops.stream()
            .flatMap( e -> of(e, target).stream() )
            .sorted(comparing( JsonTreeOperation::sortIndex ))
            .toList();
    }

    @Surly
    static List<JsonTreeOperation> of(JsonNodeOperation op, JsonNode target) {
        String path = op.path();
        String name = path.substring( path.lastIndexOf( '/' ) +1 );
        if (op instanceof JsonNodeOperation.Remove ) {
            JsonNode t = target.getOrDefault( path, null );
            if (t == null) return List.of();
            if (t.getParent().getType() == JsonNodeType.OBJECT)
                return List.of(new RemoveMember( t, name ));
            return List.of(new RemoveElements( t, parseInt( name ) ));
        }
        if (op instanceof JsonNodeOperation.Insert insert) {
            JsonNode t = target.getOrDefault( path, null );
            JsonNode value = insert.value();
            if (t == null) {
                JsonNode parent = target.get( parentPath( path ) );
                boolean isObjectParent = parent.getType() == JsonNodeType.OBJECT;
                if (isObjectParent) return List.of(new AddMembers( parent, Map.of(name, value ) ));
                return List.of(new InsertElements( parent, parseInt( name ), List.of(value) ));
            }
            if (t.getType() == JsonNodeType.OBJECT) return List.of(new ReplaceMember( t, name, value ));
            return List.of(new AppendElements( t, List.of(value) ));
        }
        throw new UnsupportedOperationException(op.toString());
    }

}
