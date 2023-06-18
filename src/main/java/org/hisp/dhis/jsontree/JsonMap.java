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

import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static java.util.stream.Collectors.toUnmodifiableSet;
import static java.util.stream.StreamSupport.stream;

/**
 * {@link JsonMap}s are a special form of a {@link JsonObject} where all properties have a common uniform value type.
 *
 * @param <E> type of the uniform map values
 * @author Jan Bernitt
 */
public interface JsonMap<E extends JsonValue> extends JsonCollection {

    /**
     * A typed variant of {@link JsonObject#get(String)}, equivalent to {@link JsonObject#get(String, Class)} where 2nd
     * parameter is the type parameter E.
     *
     * @param key property to access
     * @return value at the provided property
     */
    E get( String key );

    /**
     * @return The keys of this map.
     * @throws JsonPathException in case this value does not exist in the JSON document
     * @throws JsonTreeException in case this node does exist but is not an object node
     */
    default Set<String> keys() {
        return stream( node().members().spliterator(), false )
            .map( Map.Entry::getKey ).collect( toUnmodifiableSet() );
    }

    /**
     * @param orDefault returned if this value does not exist
     * @return the keys of this map object or the given default in case this node does not exist
     * @throws JsonTreeException in case this node does exist but is not an object node
     * @since 0.10
     */
    default Set<String> keys( Set<String> orDefault ) {
        try {
            return keys();
        } catch ( JsonPathException ex ) {
            return orDefault;
        }
    }

    /**
     * @param action call with each entry in the map in order of their declaration
     * @throws JsonPathException in case this value does not exist in the JSON document
     * @throws JsonTreeException in case this node does exist but is not an object node
     * @since 0.10
     */
    default void forEach( BiConsumer<String, ? super E> action ) {
        keys().forEach( key -> action.accept( key, get( key ) ) );
    }

    /**
     * Maps this map's entry values to a lazy transformed map view where each entry value of the original map is
     * transformed by the given function when accessed.
     * <p>
     * This means the returned map always has same size as the original map.
     *
     * @param memberToX transformer function
     * @param <V>       type of the transformer output, entries of the map view
     * @return a lazily transformed map view of this map
     */
    default <V extends JsonValue> JsonMap<V> viewAsMap( Function<E, V> memberToX ) {
        final class JsonMapView extends CollectionView<JsonMap<E>> implements JsonMap<V> {

            private JsonMapView( JsonMap<E> viewed ) {
                super( viewed );
            }

            @Override
            public V get( String key ) {
                return memberToX.apply( viewed.get( key ) );
            }

            @Override
            public Class<? extends JsonValue> asType() {
                return JsonMap.class;
            }
        }
        return new JsonMapView( this );
    }
}
