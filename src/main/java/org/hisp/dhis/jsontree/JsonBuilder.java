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
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static java.util.stream.StreamSupport.stream;

/**
 * A {@link JsonNode} builder API that mostly is designed to efficiently create JSON in a streaming scenario or when
 * programmatically declaring a JSON structure.
 * <p>
 * This can be used to write JSON directly to some type of output stream or for ad-hoc creation of {@link JsonNode}s.
 * <p>
 * The {@link JsonNode} API also makes use of the builders to transform a {@link JsonNode} tree. Since each manipulation
 * creates a copy of the tree in its JSON {@link String} form the sweet spot for this is in low volume changes as they
 * occur in tests. It would be a poor fit for highly flexible high volume JSON tree generation processes. In such cases
 * the main structures within such trees need to be created using the {@link JsonBuilder#createArray(Consumer)} or
 * {@link JsonBuilder#createObject(Consumer)} first.
 *
 * @author Jan Bernitt
 */
public interface JsonBuilder {

    PrettyPrint MINIMIZED = new PrettyPrint( 0, 0, false, true, false );

    /**
     * Pretty-printing configuration for the {@link JsonBuilder}.
     * <p>
     * If spaces and tabs are used the indent will first have tabs, then spaces.
     *
     * @param indentSpaces              number of spaces to use when indenting nested object members or array elements
     * @param indentTabs                number of tabs to use when indenting nested object members or array elements
     * @param spaceAfterColon           when true, the colon between member name and value has a space between the colon
     *                                  and the member value
     * @param retainOriginalDeclaration when true, elements or members provided as {@link JsonNode}s are kept "as is",
     *                                  that means their JSON is included as returned by
     *                                  {@link JsonNode#getDeclaration()}. When false their JSON is reformatted to
     *                                  adhere to the pretty-printing configuration.
     * @param excludeNullMembers        when true, null members are ommitted
     */
    record PrettyPrint(int indentSpaces, int indentTabs, boolean spaceAfterColon, boolean retainOriginalDeclaration,
                       boolean excludeNullMembers) {}

    /**
     * Convenience method for ad-hoc creation of JSON object {@link JsonNode}. Use {@link JsonNode#getDeclaration()} to
     * get the JSON {@link String}.
     *
     * @param obj builder to add members of the created JSON object
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
     * Like a {@link BiConsumer} but with 3 inputs.
     */
    interface TriConsumer<A, B, C> {

        void accept( A a, B b, C c );
    }

    /**
     * Define a {@link JsonNode} that is a {@link JsonObject}.
     *
     * @param obj fluent API to define the members of the JSON object created
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
         * Adds a POJO java object as the currently built object or if a name is provided as a member within that built
         * object.
         *
         * @param builder to use to append the POJO fields as member field(s)
         * @param name    name of the field in JSON or {@code null} if the POJO fields should become the member fields
         * @param value   the POJO to append as an object or member
         */
        void addTo( JsonObjectBuilder builder, String name, Object value );
    }

    /**
     * Builder API to add members to JSON object node.
     *
     * @author Jan Bernitt
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

        <K, V> JsonObjectBuilder addObject( String name, Iterable<Map.Entry<K, V>> value,
            TriConsumer<JsonObjectBuilder, ? super K, ? super V> toMember );

        default <K, V> JsonObjectBuilder addObject( String name, Iterable<Map.Entry<K, V>> value,
            Predicate<? super K> filter,
            TriConsumer<JsonObjectBuilder, ? super K, ? super V> toMember ) {
            return addObject( name, value, ( builder, k, v ) -> {
                if ( filter.test( k ) ) toMember.accept( builder, k, v );
            } );
        }

        JsonObjectBuilder addMember( String name, Object pojo, JsonMapper mapper );

        default JsonObjectBuilder addMembers( Object pojo, JsonMapper mapper ) {
            return addMember( null, pojo, mapper );
        }

        default <K, V> JsonObjectBuilder addMembers( Iterable<Map.Entry<K, V>> value,
            TriConsumer<JsonObjectBuilder, ? super K, ? super V> toMember ) {
            return addObject( null, value, toMember );
        }

        default <K, V> JsonObjectBuilder addMembers( Iterable<Map.Entry<K, V>> value,
            Predicate<? super K> filter,
            TriConsumer<JsonObjectBuilder, ? super K, ? super V> toMember ) {
            return addObject( null, value, filter, toMember );
        }

        default <V> JsonObjectBuilder addArray( String name, Stream<V> value,
            BiConsumer<JsonArrayBuilder, ? super V> toElement ) {
            return addArray( name, arr -> value.forEachOrdered( v -> toElement.accept( arr, v ) ) );
        }

        default <V> JsonObjectBuilder addArray( String name, Collection<V> value,
            BiConsumer<JsonArrayBuilder, V> toElement ) {
            return addArray( name, value.stream(), toElement );
        }

        default JsonObjectBuilder addArray( String name, String... value ) {
            return addArray( name, List.of( value ), JsonArrayBuilder::addString );
        }

        default JsonObjectBuilder addArray( String name, int... value ) {
            return addArray( name, IntStream.of( value ).boxed(), JsonArrayBuilder::addNumber );
        }

        default JsonObjectBuilder addArray( String name, long... value ) {
            return addArray( name, LongStream.of( value ).boxed(), JsonArrayBuilder::addNumber );
        }

        default JsonObjectBuilder addArray( String name, double... value ) {
            return addArray( name, DoubleStream.of( value ).boxed(), JsonArrayBuilder::addNumber );
        }

        default <V> JsonObjectBuilder addArray( String name, V[] value,
            BiConsumer<JsonArrayBuilder, ? super V> toElement ) {
            return addArray( name, Arrays.stream( value ), toElement );
        }

        default <V, X> JsonObjectBuilder addArray( String name, V[] value,
            BiConsumer<JsonArrayBuilder, X> add, Function<? super V, X> toElement ) {
            return addArray( name, value, ( arr, v ) -> add.accept( arr, toElement.apply( v ) ) );
        }

    }

    /**
     * Builder API to add elements to JSON array node.
     *
     * @author Jan Bernitt
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

        <E> JsonArrayBuilder addArray( Stream<E> values, BiConsumer<JsonArrayBuilder, ? super E> toElement );

        <E> JsonArrayBuilder addElements( Stream<E> values, BiConsumer<JsonArrayBuilder, ? super E> toElement );

        JsonArrayBuilder addObject( Consumer<JsonObjectBuilder> value );

        <K, V> JsonObjectBuilder addObject( Map<K, V> value,
            TriConsumer<JsonObjectBuilder, ? super K, ? super V> toMember );

        JsonArrayBuilder addElement( Object value, JsonMapper mapper );

        default JsonArrayBuilder addArray( String... values ) {
            return addArray( List.of( values ), JsonArrayBuilder::addString );
        }

        default JsonArrayBuilder addArray( int... values ) {
            return addArray( IntStream.of( values ).boxed(), JsonArrayBuilder::addNumber );
        }

        default JsonArrayBuilder addArray( long... values ) {
            return addArray( LongStream.of( values ).boxed(), JsonArrayBuilder::addNumber );
        }

        default JsonArrayBuilder addArray( double... values ) {
            return addArray( DoubleStream.of( values ).boxed(), JsonArrayBuilder::addNumber );
        }

        default JsonArrayBuilder addArray( Number[] values ) {
            return addArray( values, JsonArrayBuilder::addNumber );
        }

        default <E> JsonArrayBuilder addArray( E[] values, BiConsumer<JsonArrayBuilder, ? super E> toElement ) {
            return addArray( Arrays.stream( values ), toElement );
        }

        default <E, X> JsonArrayBuilder addArray( E[] values,
            BiConsumer<JsonArrayBuilder, X> add, Function<? super E, X> toElement ) {
            return addArray( values, ( arr, v ) -> add.accept( arr, toElement.apply( v ) ) );
        }

        default <E> JsonArrayBuilder addArray( Collection<E> values,
            BiConsumer<JsonArrayBuilder, ? super E> toElement ) {
            return addArray( values.stream(), toElement );
        }

        default <E, X> JsonArrayBuilder addArray( Collection<E> values, BiConsumer<JsonArrayBuilder, X> add,
            Function<? super E, X> toElement ) {
            return addArray( values, ( arr, v ) -> add.accept( arr, toElement.apply( v ) ) );
        }

        default <E> JsonArrayBuilder addElements( Iterable<E> values,
            BiConsumer<JsonArrayBuilder, ? super E> toElement ) {
            return addElements( stream( values.spliterator(), false ), toElement );
        }

        default <E> JsonArrayBuilder addElements( Collection<E> values,
            BiConsumer<JsonArrayBuilder, ? super E> toElement ) {
            return addElements( values.stream(), toElement );
        }

        default <E, X> JsonArrayBuilder addElements( Collection<E> values, BiConsumer<JsonArrayBuilder, X> add,
            Function<? super E, X> toElement ) {
            return addElements( values, ( arr, v ) -> add.accept( arr, toElement.apply( v ) ) );
        }
    }
}
