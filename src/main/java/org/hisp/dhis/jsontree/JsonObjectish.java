package org.hisp.dhis.jsontree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;

/**
 * An "abstract" type that is expected to be backed by a JSON object.
 *
 * @since 0.11
 */
@Validation.Ignore
public interface JsonObjectish<E extends JsonValue> extends JsonCollection {

    /**
     * A typed variant of {@link JsonObject#get(String)}, equivalent to {@link JsonObject#get(String, Class)} where 2nd
     * parameter is the type parameter E.
     *
     * @param key property to access
     * @return value at the provided property
     */
    E get( String key );

    /**
     * Test an object for property names.
     *
     * @param names a set of property names that should exist
     * @return true if this object has (at least) all the given names
     * @throws JsonTreeException in case this value is not an JSON object
     */
    default boolean has( String... names ) {
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
    default boolean has( Collection<String> names ) {
        return exists() && names.stream().allMatch( node()::isMember );
    }

    /**
     * @param key the key or name to look up
     * @return true, if the provided key is defined, false otherwise
     * @since 0.11
     */
    default boolean containsKey( String key ) {
        return !isUndefined(key);
    }

    /**
     * OBS! This does not require this node to be an object node.
     *
     * @param name name of the object member
     * @return true if this object does not have a member of the provided name
     * @since 0.11
     */
    default boolean isUndefined( String name ) {
        return get( name ).isUndefined();
    }

    /**
     * @return The keys of this map.
     * @throws JsonTreeException in case this node does exist but is not an object node
     * @since 0.11 (as Stream)
     */
    default Stream<String> keys() {
        return isUndefined() ? Stream.empty() : stream( node().keys().spliterator(), false );
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
     * Lists JSON object property names in order of declaration.
     *
     * @return The list of property names in the order they were defined.
     * @throws JsonTreeException in case this value is not an JSON object
     */
    default List<String> names() {
        List<String> names = new ArrayList<>();
        keys().forEach( names::add );
        return names;
    }

    /**
     * @param action call with each entry in the map/object in order of their declaration
     * @throws JsonTreeException in case this node does exist but is not an object node
     * @since 0.10
     */
    default void forEach( BiConsumer<String, ? super E> action ) {
        // need to use keys() + get(key) because of wrapper type E is not accessible otherwise
        // and to preserve the path of the values
        keys().forEach( key -> action.accept( key, get( key ) ) );
    }

    /**
     * @param action called for each value in the map/object in order of their declaration
     * @throws JsonTreeException in case this node does exist but is not an object node
     * @since 0.11
     */
    default void forEachEntry( Consumer<E> action ) {
        keys().forEach( name -> action.accept( get( name )) );
    }
}
