package org.hisp.dhis.jsontree;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.hisp.dhis.jsontree.JsonBuilder.checkValid;

/**
 * High level API to convert Java values to JSON {@link JsonValue} nodes.
 * <p>
 * It builds on top of the individual APIs like {@link JsonValue}, {@link JsonMixed} and {@link JsonBuilder}.
 * <p>
 * In contrast to the lower level APIs this APIs expects Java value as input. For example, a {@link String} is
 * understood not as a JSON string value with quotes but a Java string value that is escaped to create the JSON string
 * equivalent.
 *
 * @author Jan Bernitt
 * @since 0.11
 */
public interface Json {

    static JsonMixed ofNull() {
        return JsonMixed.of( JsonNode.NULL );
    }

    static JsonString of( String value ) {
        return JsonMixed.of( JsonBuilder.createString( value ) );
    }

    static JsonString of(char value) {
        return of(String.valueOf( value ));
    }

    static JsonString of(Character value) {
        return value == null ? ofNull() : of( value.charValue() );
    }

    static JsonNumber of( boolean value ) {
        return JsonMixed.of( String.valueOf( value ) );
    }

    static JsonNumber of( Boolean value ) {
        return value == null ? ofNull() : JsonMixed.of( value.toString() );
    }

    static JsonNumber of( int value ) {
        return JsonMixed.of( String.valueOf( value ) );
    }

    static JsonNumber of( long value ) {
        return JsonMixed.of( String.valueOf( value ) );
    }

    static JsonNumber of( float value ) {
        checkValid( value );
        return JsonMixed.of( String.valueOf( value ) );
    }

    static JsonNumber of( double value ) {
        checkValid( value );
        return JsonMixed.of( String.valueOf( value ) );
    }

    static JsonNumber of( Number value ) {
        if ( value instanceof Double d ) checkValid( d );
        if ( value instanceof Float f ) checkValid( f );
        return value == null ? ofNull() : JsonMixed.of( value.toString() );
    }

    @SafeVarargs
    static <E> JsonArray array( Function<E, ? extends JsonValue> map, E... items ) {
        return items == null ? ofNull() : array( map, Arrays.asList( items ) );
    }

    static <E> JsonArray array( Function<E, ? extends JsonValue> map, Stream<E> items ) {
        return array( arr -> items.forEach( e -> arr.addElement( map.apply( e ).node() ) ) );
    }

    static <E> JsonArray array( Function<E, ? extends JsonValue> map, Iterable<E> items ) {
        return items == null ? ofNull() : array( arr -> items.forEach( e -> arr.addElement( map.apply( e ).node() ) ) );
    }

    static JsonArray array( Consumer<JsonBuilder.JsonArrayBuilder> arr ) {
        return JsonMixed.of( JsonBuilder.createArray( arr ) );
    }

    static JsonArray array( IntStream values ) {
        return array( arr -> arr.addNumbers( values ) );
    }

    static JsonArray array( int... values ) {
        return array( IntStream.of( values ) );
    }

    static JsonArray array( LongStream values ) {
        return array( arr -> arr.addNumbers( values ) );
    }

    static JsonArray array( long... values ) {
        return array( LongStream.of( values ) );
    }

    static JsonArray array( DoubleStream values ) {
        return array( arr -> arr.addNumbers( values ) );
    }

    static JsonArray array( double... values ) {
        return array( DoubleStream.of( values ) );
    }

    /**
     * @return an empty JSON array
     */
    static JsonArray array() {
        return JsonMixed.of( JsonNode.EMPTY_ARRAY );
    }

    static JsonObject object( Consumer<JsonBuilder.JsonObjectBuilder> obj ) {
        return JsonMixed.of( JsonBuilder.createObject( obj ) );
    }

    static <T> JsonObject object( Function<T, ? extends JsonValue> map, Map<String, T> items ) {
        return items == null ? ofNull() : object( map, items.entrySet() );
    }

    static <T> JsonObject object( Function<T, ? extends JsonValue> map, Iterable<Map.Entry<String, T>> items ) {
        return items == null
            ? ofNull()
            : object( obj -> items.forEach( e -> obj.addMember( e.getKey(), map.apply( e.getValue() ).node() ) ) );
    }

    static <T> JsonObject object( Function<T, ? extends JsonValue> map, Stream<Map.Entry<String, T>> items ) {
        return object( obj -> items.forEach( e -> obj.addMember( e.getKey(), map.apply( e.getValue() ).node() ) ) );
    }

    /**
     * @return an empty JSON object
     */
    static JsonObject object() {
        return JsonMixed.of( JsonNode.EMPTY_OBJECT );
    }
}
