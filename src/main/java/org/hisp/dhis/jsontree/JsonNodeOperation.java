package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonBuilder.JsonArrayBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;
import static org.hisp.dhis.jsontree.JsonBuilder.createArray;
import static org.hisp.dhis.jsontree.JsonNodeType.OBJECT;
import static org.hisp.dhis.jsontree.JsonPatchException.clash;

/**
 * {@linkplain JsonNodeOperation}s are used to make bulk modifications using {@link JsonNode#patch(List)}.
 * <p>
 * {@linkplain JsonNodeOperation} is a path based operation that is not yet "bound" to target.
 * <p>
 * The order of operations made into a set does not matter. Any order has the same outcome when applied to the same
 * target.
 *
 * @author Jan Bernitt
 * @since 1.1
 */
sealed public interface JsonNodeOperation {

    /**
     * @return the target of the operation
     */
    String path();

    /**
     * @return true when this operation targets an array index
     */
    default boolean isArrayOp() {
        return path().endsWith( "]" );
    }

    /**
     * @return true when this is an {@link Insert} operation
     */
    default boolean isRemove() {
        return this instanceof Remove;
    }

    /**
     * @param path relative path to remove
     */
    record Remove(String path) implements JsonNodeOperation {}

    /**
     * <h4>Insert into Arrays</h4>
     * In an array the value is inserted before the existing value at the path index. That means the current value at
     * the path index will be after the inserted value in the updated tree.
     * <p>
     * <h4>Merge</h4>
     * <ul>
     *     <li>object + object = add all properties of inserted object to target object</li>
     *     <li>array + array = insert all elements of inserted array at target index into the target array</li>
     *     <li>array + primitive = append inserted element to target array</li>
     *     <li>primitive + primitive = create array with current value and inserted value</li>
     *     <li>* + object = trying to merge an object value into a non object target is an error</li>
     * </ul>
     *
     * @param path  relative path to the target property, this either is the root, an object member or an array index or
     *              range
     * @param value the new value
     * @param merge when true, insert the value's items not the value itself
     */
    record Insert(String path, JsonNode value, boolean merge) implements JsonNodeOperation {
        public Insert(String path, JsonNode value) { this(path, value, false); }
    }

    /**
     * As each target path may only occur once a set of operations may need folding inserts for arrays. This means each
     * operation that wants to insert at the same index in the same target array is merged into a single operation
     * inserting all the values in the order they occur in the #ops parameter.
     *
     * @param ops a set of ops that may contain multiple inserts targeting the same array index
     * @return a list of operations where the clashing array inserts have been merged by concatenating the inserted
     * elements
     * @throws JsonPathException if the ops is found to contain other operations clashing on same path (that are not
     *                           array inserts)
     */
    static List<JsonNodeOperation> mergeArrayInserts(List<JsonNodeOperation> ops) {
        if (ops.stream().filter( JsonNodeOperation::isArrayOp ).count() < 2) return ops;
        return List.copyOf( ops.stream()
            .collect( toMap(JsonNodeOperation::path, Function.identity(), (op1, op2) -> {
                if (!op1.isArrayOp() || op1.isRemove() || op2.isRemove() )
                    throw JsonPatchException.clash( ops, op1, op2 );
                JsonNode merged = createArray( arr -> {
                    Consumer<JsonNodeOperation> add = op -> {
                        Insert insert = (Insert) op;
                        if ( insert.merge() ) {
                            arr.addElements( insert.value().elements(), JsonArrayBuilder::addElement );
                        } else {
                            arr.addElement( insert.value() );
                        }
                    };
                    add.accept( op1 );
                    add.accept( op2 );
                } );
                return new Insert( op1.path(), merged, true );
            }, LinkedHashMap::new ) ).values());
    }

    /**
     * @param ops set of patch operations
     * @implNote array merge inserts don't need special handling as it is irrelevant how many elements are inserted at
     * the target index as each operation is independent and uniquely targets an insert position in the target array in
     * its state before any change
     */
    static void checkPatch( List<JsonNodeOperation> ops ) {
        if (ops.size() < 2) return;
        Map<String, JsonNodeOperation> opsByPath = new HashMap<>();
        Set<String> parents = new HashSet<>();
        for ( JsonNodeOperation op : ops ) {
            String path = op.path();
            if (op instanceof Insert insert && insert.merge && insert.value.getType() == OBJECT) {
                insert.value.keys().forEach( p -> checkPatchPath( ops, op, path+"."+p, opsByPath, parents )  );
                checkPatchParents( ops, op, path, opsByPath, parents );
            } else {
                checkPatchPath( ops, op, path, opsByPath, parents );
                checkPatchParents( ops, op, JsonNode.parentPath( path ), opsByPath, parents );
            }
        }
    }

    private static void checkPatchPath( List<JsonNodeOperation> ops, JsonNodeOperation op, String path,
        Map<String, JsonNodeOperation> opsByPath, Set<String> parents ) {
        if ( opsByPath.containsKey( path ) ) throw clash( ops, opsByPath.get( path ), op );
        if ( parents.contains( path ) ) throw clash( ops, op, null );
        opsByPath.put( path, op );
    }

    private static void checkPatchParents( List<JsonNodeOperation> ops, JsonNodeOperation op, String path,
        Map<String, JsonNodeOperation> opsByPath, Set<String> parents ) {
        while ( !path.isEmpty() ) {
            if ( opsByPath.containsKey( path ) ) throw clash( ops, opsByPath.get( path ), op );
            parents.add( path );
            path = JsonNode.parentPath( path );
        }
    }
}
