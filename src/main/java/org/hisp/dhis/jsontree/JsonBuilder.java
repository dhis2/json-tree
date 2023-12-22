/*
 * Copyright (c) 2004-2021, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.jsontree;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;

/**
 * A {@link JsonNode} builder API is designed to efficiently create declare or stream JSON programmatically.
 * <p>
 * It can be used to write JSON directly to some type of output stream or for ad-hoc creation of {@link JsonNode}s.
 * <p>
 * The {@link JsonNode} API also makes use of the builders to transform a {@link JsonNode} tree. Since each manipulation
 * creates a copy of the tree in its JSON {@link String} form the sweet spot for this is in low volume changes as they
 * occur in tests. It would be a poor fit for highly flexible high volume JSON tree generation processes. In such cases
 * the main structures within such trees need to be created using the {@link JsonBuilder#createArray(Consumer)} or
 * {@link JsonBuilder#createObject(Consumer)} first.
 *
 * @author Jan Bernitt
 * @since 0.11 (alpha overhaul)
 * @apiNote beta (subject to change)
 */
public interface JsonBuilder {

    /**
     * Can be implemented by Java types that are capable to translate themselves to JSON
     * using a builder.
     *
     * @since 0.11
     * @apiNote beta (subject to change)
     */
    interface JsonEncodable {
        /**
         * Adds the implementing java object to a currently built array.
         *
         * @param arr to use to append the object to as an element
         */
        void addTo( JsonArrayBuilder arr );

        /**
         * Adds the implementing java object to a currently built object.
         *
         * @param name    name member name to use
         * @param obj to use to append the object as a member
         */
        void addTo( String name, JsonObjectBuilder obj );
    }

    @FunctionalInterface
    interface JsonObjectEncodable extends JsonEncodable {
        @Override
        default void addTo( JsonBuilder.JsonArrayBuilder arr ) {
            arr.addObject(this::addToObject );
        }

        @Override
        default void addTo( String name, JsonBuilder.JsonObjectBuilder obj ) {
            obj.addObject( name, this::addToObject );
        }

        void addToObject(JsonBuilder.JsonObjectBuilder obj);
    }

    PrettyPrint MINIMIZED = new PrettyPrint( 0, 0, false, true, false );

    /**
     * Pretty-printing configuration for the {@link JsonBuilder}.
     * <p>
     * If spaces and tabs are used the indent will first have tabs, then spaces.
     *
     * @param indentSpaces              number of spaces to use when indenting nested object properties or array
     *                                  elements
     * @param indentTabs                number of tabs to use when indenting nested object properties or array elements
     * @param spaceAfterColon           when true, the colon between property name and value has a space between the
     *                                  colon and the property value
     * @param retainOriginalDeclaration when true, elements or properties provided as {@link JsonNode}s are kept "as
     *                                  is", that means their JSON is included as returned by
     *                                  {@link JsonNode#getDeclaration()}. When false their JSON is reformatted to
     *                                  adhere to the pretty-printing configuration.
     * @param excludeNullMembers        when true, null object properties are omitted
     */
    record PrettyPrint(int indentSpaces, int indentTabs, boolean spaceAfterColon, boolean retainOriginalDeclaration,
                       boolean excludeNullMembers) {}

    /**
     * @param value a Java string value, may be null
     * @return JSON string node of the provided Java string value
     * @since 0.11
     */
    static JsonNode createString( String value ) {
        if ( value == null ) return JsonNode.NULL;
        StringBuilder json = new StringBuilder();
        new JsonAppender( MINIMIZED, json ).appendEscaped( value );
        return JsonNode.of( json.toString() );
    }

    /**
     * Check if a double can be represented in JSON
     *
     * @param value a double value
     * @throws JsonFormatException if the double is NaN or Infinity
     * @since 0.11
     */
    static void checkValid( double value ) {
        if ( Double.isNaN( value ) ) throw new JsonFormatException( "NaN is not a valid JSON value" );
        if ( Double.isInfinite( value ) ) throw new JsonFormatException( "Infinite is not a valid JSON value" );
    }

    /**
     * Check if a number can be represented in JSON
     *
     * @param value any number value, may be null
     * @throws JsonFormatException if the double is NaN or Infinity
     * @since 0.11
     */
    static void checkValid(Number value) {
        if ( value instanceof Double d ) checkValid( d.doubleValue() );
        if ( value instanceof Float f ) checkValid( f.doubleValue() );
    }


    /**
     * Convenience method for ad-hoc creation of JSON object {@link JsonNode}. Use {@link JsonNode#getDeclaration()} to
     * get the JSON {@link String}.
     *
     * @param obj builder to add properties to the created JSON object
     * @return created JSON object {@link JsonNode}
     * @since 0.2
     */
    static JsonNode createObject( Consumer<JsonObjectBuilder> obj ) {
        return createObject( MINIMIZED, obj );
    }

    /**
     * @since 0.7
     */
    static JsonNode createObject( PrettyPrint config, Consumer<JsonObjectBuilder> obj ) {
        return new JsonAppender( config, new StringBuilder() ).toObject( obj );
    }

    /**
     * Convenience method for ad-hoc creation of JSON arrays {@link JsonNode}. Use {@link JsonNode#getDeclaration()} to
     * get the JSON {@link String}.
     *
     * @param arr builder to add elements of the created JSON array
     * @return created JSON array {@link JsonNode}
     * @since 0.2
     */
    static JsonNode createArray( Consumer<JsonArrayBuilder> arr ) {
        return createArray( MINIMIZED, arr );
    }

    /**
     * @since 0.7
     */
    static JsonNode createArray( PrettyPrint config, Consumer<JsonArrayBuilder> arr ) {
        return new JsonAppender( config, new StringBuilder() ).toArray( arr );
    }

    /**
     * Convenience method to directly write an object to the provided {@link OutputStream}.
     *
     * @param out target, not null
     * @param obj builder to create the written object
     * @since 0.2
     */
    static void streamObject( OutputStream out, Consumer<JsonObjectBuilder> obj ) {
        streamObject( MINIMIZED, out, obj );
    }

    /**
     * @since 0.7
     */
    static void streamObject( PrettyPrint config, OutputStream out, Consumer<JsonObjectBuilder> obj ) {
        try ( PrintStream jsonStream = new PrintStream( out ) ) {
            new JsonAppender( config, jsonStream ).toObject( obj );
        }
    }

    /**
     * Convenience method to directly write an array to the provided {@link OutputStream}.
     *
     * @param out target, not null
     * @param arr builder to create the written array
     * @since 0.2
     */
    static void streamArray( OutputStream out, Consumer<JsonArrayBuilder> arr ) {
        streamArray( MINIMIZED, out, arr );
    }

    /**
     * @since 0.7
     */
    static void streamArray( PrettyPrint config, OutputStream out, Consumer<JsonArrayBuilder> arr ) {
        try ( PrintStream jsonStream = new PrintStream( out ) ) {
            new JsonAppender( config, jsonStream ).toArray( arr );
        }
    }

    /**
     * Define a {@link JsonNode} that is a {@link JsonObject}.
     *
     * @param obj fluent API to define the properties of the JSON object created
     * @return the {@link JsonNode} representing the created object, might return {@code null} in case the content is
     * only written to an output stream and cannot be accessed as {@link JsonNode} value
     */
    JsonNode toObject( Consumer<JsonObjectBuilder> obj );

    /**
     * Define a {@link JsonNode} that is a {@link JsonArray}.
     *
     * @param arr fluent API to define the elements of the JSON array created
     * @return the {@link JsonNode} representing the created array, might return {@code null} in case the content is
     * only written to an output stream and cannot be accessed as {@link JsonNode} value
     */
    JsonNode toArray( Consumer<JsonArrayBuilder> arr );

    default JsonNode toObject( Object pojo, JsonMapper mapper ) {
        return toObject( obj -> obj.addMembers( pojo, mapper ) );
    }

    /**
     * Maps POJOs to JSON by using the provided builder to append the builder representation of the POJO to the
     * currently built JSON tree.
     * <p>
     * The mapping is intentionally not built into the {@link JsonBuilder} as different builder implementation might be
     * useful in combination with different mappers in different scenarios. By having the mapping being an explicit
     * support abstraction this allows to combine any combination of the two while also keeping each simple and focussed
     * on solving a single problem.
     *
     * @author Jan Bernitt
     */
    interface JsonMapper {

        /**
         * Adds a POJO java object to a currently built array.
         *
         * @param builder to use to append the object to as an element
         * @param value   the POJO to append as an element
         */
        void addTo( JsonArrayBuilder builder, Object value );

        /**
         * Adds a POJO java object as the currently built object or if a name is provided as a property within that
         * built object.
         *
         * @param builder to use to append the POJO fields as properties
         * @param name    name of the field in JSON or {@code null} if the POJO fields should become the properties
         * @param value   the POJO to append as an object or property
         */
        void addTo( JsonObjectBuilder builder, String name, Object value );
    }

    /**
     * Builder API to add properties to JSON object node.
     *
     * @author Jan Bernitt
     * @since 0.11 (alpha overhaul)
     * @apiNote beta (subject to change)
     */
    interface JsonObjectBuilder {

        JsonObjectBuilder addMember( String name, JsonNode value );

        JsonObjectBuilder addBoolean( String name, boolean value );

        JsonObjectBuilder addBoolean( String name, Boolean value );

        JsonObjectBuilder addNumber( String name, int value );

        JsonObjectBuilder addNumber( String name, long value );

        JsonObjectBuilder addNumber( String name, double value );

        JsonObjectBuilder addNumber( String name, Number value );

        JsonObjectBuilder addString( String name, String value );

        JsonObjectBuilder addArray( String name, Consumer<JsonArrayBuilder> value );

        JsonObjectBuilder addObject( String name, Consumer<JsonObjectBuilder> value );

        /*
        convenience API (based on the above)
         */

        default JsonObjectBuilder addMember(String name, JsonEncodable value) {
            value.addTo( name, this );
            return this;
        }

        default JsonObjectBuilder addMember( String name, Object pojo, JsonMapper mapper ) {
            mapper.addTo( this, name, pojo );
            return this;
        }

        default JsonObjectBuilder addMembers( Object pojo, JsonMapper mapper ) {
            return addMember( null, pojo, mapper );
        }

        interface AddMember<V> {

            void add( JsonObjectBuilder obj, String key, V value );
        }

        default <V> JsonObjectBuilder addMembers( Map<String, V> value, AddMember<? super V> addMember ) {
            return addMembers( value.entrySet(), addMember );
        }

        default <V> JsonObjectBuilder addMembers( Iterable<Map.Entry<String, V>> value,
            AddMember<? super V> addMember ) {
            value.forEach( e -> addMember.add( this, e.getKey(), e.getValue() ) );
            return this;
        }

        default <V> JsonObjectBuilder addMembers( Stream<Map.Entry<String, V>> value, AddMember<? super V> addMember ) {
            value.forEach( e -> addMember.add( this, e.getKey(), e.getValue() ) );
            return this;
        }
    }

    /**
     * Builder API to add elements to JSON array node.
     *
     * @author Jan Bernitt
     * @since 0.11 (alpha overhaul)
     * @apiNote beta (subject to change)
     */
    interface JsonArrayBuilder {

        JsonArrayBuilder addElement( JsonNode value );

        JsonArrayBuilder addBoolean( boolean value );

        JsonArrayBuilder addBoolean( Boolean value );

        JsonArrayBuilder addNumber( int value );

        JsonArrayBuilder addNumber( long value );

        JsonArrayBuilder addNumber( double value );

        JsonArrayBuilder addNumber( Number value );

        JsonArrayBuilder addString( String value );

        JsonArrayBuilder addArray( Consumer<JsonArrayBuilder> value );

        JsonArrayBuilder addObject( Consumer<JsonObjectBuilder> value );

        /*
        convenience API (based on the above)
         */

        default JsonArrayBuilder addElement(JsonEncodable value) {
            value.addTo( this );
            return this;
        }

        default JsonArrayBuilder addElement( Object value, JsonMapper mapper ) {
            mapper.addTo( this, value );
            return this;
        }

        default JsonArrayBuilder addNumbers( IntStream values ) {
            values.forEach( this::addNumber );
            return this;
        }

        default JsonArrayBuilder addNumbers( LongStream values ) {
            values.forEach( this::addNumber );
            return this;
        }

        default JsonArrayBuilder addNumbers( DoubleStream values ) {
            values.forEach( this::addNumber );
            return this;
        }

        default JsonArrayBuilder addNumbers( int... values ) {
            return addNumbers( IntStream.of( values ) );
        }

        default JsonArrayBuilder addNumbers( long... values ) {
            return addNumbers( LongStream.of( values ) );
        }

        default JsonArrayBuilder addNumbers( double... values ) {
            return addNumbers( DoubleStream.of( values ) );
        }

        default JsonArrayBuilder addNumbers( Number[] values ) {
            return addElements( Stream.of( values ), JsonArrayBuilder::addNumber );
        }

        default JsonArrayBuilder addStrings( String... values ) {
            return addElements( Stream.of( values ), JsonArrayBuilder::addString );
        }

        default <E> JsonArrayBuilder addElements( Stream<E> values,
            BiConsumer<JsonArrayBuilder, ? super E> addElement ) {
            values.forEachOrdered( v -> addElement.accept( this, v ) );
            return this;
        }

        default <E> JsonArrayBuilder addElements( Iterable<E> values,
            BiConsumer<JsonArrayBuilder, ? super E> addElement ) {
            return addElements( stream( values.spliterator(), false ), addElement );
        }

        default <E, T> JsonArrayBuilder addElements( Iterable<T> values,
            BiConsumer<JsonArrayBuilder, ? super E> addElement, Function<T, E> map ) {
            return addElements( values, ( arr, e ) -> addElement.accept( arr, map.apply( e ) ) );
        }

        default <E, T> JsonArrayBuilder addElements( T[] values,
            BiConsumer<JsonArrayBuilder, ? super E> addElement, Function<T, E> map ) {
            return addElements( Arrays.asList( values ), addElement, map );
        }
    }
}
