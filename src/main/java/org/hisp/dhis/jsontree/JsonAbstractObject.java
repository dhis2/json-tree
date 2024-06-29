package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.Validation.Rule;
import org.hisp.dhis.jsontree.validation.JsonValidator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;
import static org.hisp.dhis.jsontree.Validation.NodeType.OBJECT;

/**
 * An "abstract" type that is expected to be backed by a JSON object.
 *
 * @since 0.11
 */
@Validation.Ignore
@Validation( type = OBJECT )
public interface JsonAbstractObject<E extends JsonValue> extends JsonAbstractCollection {

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
        return !isUndefined( key );
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
     * Test if the object property is defined which includes being defined JSON {@code null}.
     *
     * @param name name of the object member
     * @return true if this object has a member of the provided name
     * @since 1.1
     */
    default boolean exists(String name) {
       return get(name).exists();
    }

    /**
     * Note that keys may differ from the member names as defined in the JSON document in case that their literal
     * interpretation would have clashed with key syntax. In that case the object member name is "escaped" so that using
     * the returned key with {@link #get(String)} will return the value. Use {@link #names()} to receive the literal
     * object member names as defined in the document.
     *
     * @return The keys of this map.
     * @throws JsonTreeException in case this node does exist but is not an object node
     * @see #names()
     * @since 0.11 (as Stream)
     */
    default Stream<String> keys() {
        return isUndefined() || isEmpty() ? Stream.empty() : stream( node().keys().spliterator(), false );
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
     * @return a stream of map/object entries in order of their declaration
     * @throws JsonTreeException in case this node does exist but is not an object node
     * @since 0.11
     */
    default Stream<Map.Entry<String, E>> entries() {
        //TODO use members internally to have names as entry key?
        return keys().map( key -> Map.entry( key, get( key ) ) );
    }

    /**
     * Lists raw JSON object member names in order of declaration.
     *
     * @return The list of object member names in the order they were defined.
     * @throws JsonTreeException in case this node does exist but is not an object node
     * @see #keys()
     */
    default List<String> names() {
        return isUndefined() || isEmpty() ? List.of() : stream( node().names().spliterator(), false ).toList();
    }

    /**
     * @return a stream of the absolute paths of the map/object members in oder of their declaration
     * @throws JsonTreeException in case this node does exist but is not an object node
     * @since 1.2
     */
    default Stream<JsonPath> paths() {
        return isUndefined() || isEmpty() ? Stream.empty() : stream( node().paths().spliterator(), false );
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
    default void validate( Class<? extends JsonAbstractObject<?>> schema, Rule... rules ) {
        JsonValidator.validate( this, schema, rules );
    }
}
