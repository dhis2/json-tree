package org.hisp.dhis.jsontree;

import static java.util.Spliterator.DISTINCT;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SIZED;
import static java.util.Spliterators.spliterator;
import static java.util.stream.StreamSupport.stream;
import static org.hisp.dhis.jsontree.Validation.NodeType.OBJECT;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.hisp.dhis.jsontree.validation.JsonValidator;

/**
 * An "abstract" type that is expected to be backed by a JSON object.
 *
 * @since 0.11
 */
@Validation.Ignore
@Validation( type = OBJECT )
public interface JsonAbstractObject<E extends JsonValue> extends JsonAbstractCollection {

    /**
     * @param key property to access
     * @return value at the provided property
     * @since 1.9
     */
    E get(Text key);

    /**
     * A typed variant of {@link JsonObject#get(CharSequence)}, equivalent to {@link JsonObject#get(CharSequence, Class)} where 2nd
     * parameter is the type parameter E.
     *
     * @see #get(Text)
     */
    default E get( CharSequence key ) {
        return get( Text.of( key ) );
    }

    /**
     * Test an object for property names.
     *
     * @param names a set of property names that should exist
     * @return true if this object has (at least) all the given names
     * @throws JsonTreeException in case this value is not an JSON object
     */
    default boolean has( CharSequence... names ) {
        return has( List.of( names ) );
    }

    /**
     * Test an object for property names.
     *
     * @param names a set of property names that should exist
     * @return true if this object has (at least) all the given names
     * @throws JsonTreeException in case this value is not an JSON object
     * @since 0.10
     */
    default boolean has( Collection<? extends CharSequence> names ) {
        if (!exists()) return false;
        // Note that size check would miss that arrays do not have named members
        JsonNode node = node();
        return names.stream().allMatch( node::isMember );
    }

    /**
     * @param key the key or name to look up
     * @return true, if the provided key is defined, false otherwise
     * @since 0.11
     */
    default boolean containsKey( CharSequence key ) {
        return !isUndefined( key );
    }

    /**
     * OBS! This does not require this node to be an object node.
     *
     * @param name name of the object member
     * @return true if this object does not have a member of the provided name
     * @since 0.11
     */
    default boolean isUndefined( CharSequence name ) {
        return get( name ).isUndefined();
    }

    /**
     * Test if the object property is defined which includes being defined JSON {@code null}.
     *
     * @param name name of the object member
     * @return true if this object has a member of the provided name
     * @since 1.1
     */
    default boolean exists(CharSequence name) {
       return get(name).exists();
    }

    /**
     * Note that keys may differ from the member names as defined in the JSON document in case that their literal
     * interpretation would have clashed with key syntax. In that case the object member name is "escaped" so that using
     * the returned key with {@link #get(CharSequence)} will return the value. Use {@link #names()} to receive the literal
     * object member names as defined in the document.
     *
     * @return The keys of this map.
     * @throws JsonTreeException in case this node does exist but is not an object node
     * @see #names()
     * @since 0.11 (as Stream)
     */
    default Stream<Text> keys() {
        if (isUndefined() || isEmpty()) return Stream.empty();
        Iterator<Text> iter = node().keys().iterator();
        return stream( spliterator( iter, size(), ORDERED | SIZED | DISTINCT | NONNULL ), false);
    }

    /**
     * @return a stream of the map/object values in order of their declaration
     * @throws JsonTreeException in case this node does exist but is not an object node
     * @since 0.11
     */
    default Stream<E> values() {
        return keys().map( this::get );
    }

    /**
     * @return a stream of map/object entries in order of their declaration.
     * @throws JsonTreeException in case this node does exist but is not an object node
     * @since 0.11
     */
    default Stream<Map.Entry<Text, E>> entries() {
        if ( isUndefined() || isEmpty() ) return Stream.empty();
        return stream( node().keys().spliterator(), false )
            .map(name -> Map.entry( name , get(name)));
    }

    /**
     * Lists raw JSON object member names in order of declaration.
     *
     * @return The list of object member names in the order they were defined.
     * @throws JsonTreeException in case this node does exist but is not an object node
     * @see #keys()
     */
    default List<String> names() {
        return keys().map( Text::toString ).toList();
    }

    /**
     * @return a stream of the absolute paths of the map/object members in oder of their declaration
     * @throws JsonTreeException in case this node does exist but is not an object node
     * @since 1.2
     */
    default Stream<JsonPath> paths() {
        if (isUndefined() || isEmpty()) return Stream.empty();
        Iterator<JsonPath> iter = node().paths().iterator();
        return stream( spliterator( iter, size(), ORDERED | SIZED | DISTINCT | NONNULL), false);
    }

    /**
     * @param action call with each entry in the map/object in order of their declaration
     * @throws JsonTreeException in case this node does exist but is not an object node
     * @since 0.10
     */
    default void forEach( BiConsumer<Text, ? super E> action ) {
        // need to use keys() + get(key) because of wrapper type E is not accessible otherwise
        // and to preserve the path of the values
        keys().forEach( key -> action.accept( key, get( key ) ) );
    }

    /**
     * @param action called for each value in the map/object in order of their declaration
     * @throws JsonTreeException in case this node does exist but is not an object node
     * @since 0.11
     */
    default void forEachValue( Consumer<E> action ) {
        keys().forEach( name -> action.accept( get( name ) ) );
    }

    /**
     * @param schema the schema to validate against
     * @param rules optional set of {@link Rule}s to check, empty includes all
     * @throws JsonSchemaException      in case this value does not match the given schema
     * @throws IllegalArgumentException in case the given schema is not an interface
     * @since 0.11
     */
    default void validate( Class<?> schema, Rule... rules ) {
        JsonValidator.validate( this, schema, rules );
    }
}
