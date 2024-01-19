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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.hisp.dhis.jsontree.Validation.NodeType.OBJECT;

/**
 * A {@link JsonMap} with {@link JsonList} of elements.
 * <p>
 * This needs a dedicated type as we cannot pass a {@link JsonMap} {@link Class} with the generics of a {@link JsonList}
 * of the element type otherwise.
 *
 * @param <E> type of the map list elements
 * @author Jan Bernitt
 */
@Validation( type = OBJECT )
@Validation.Ignore
public interface JsonMultiMap<E extends JsonValue> extends JsonAbstractObject<JsonList<E>> {

    /**
     * Convert this {@link JsonMultiMap} to a {@link Map} of {@link List} values where the list elements are mapped from
     * {@link JsonValue} by the provided mapping {@link Function}.
     * <p>
     * The order of the elements in the list are kept.
     *
     * @param toValue maps map list elements
     * @param <T>     type of map value list elements
     * @return this {@link JsonMultiMap} as {@link Map}
     * @throws JsonTreeException in case this node does exist but is not an object node
     */
    default <T> Map<String, List<T>> toMap( Function<E, T> toValue ) {
        return toMap( toValue, null );
    }

    /**
     * Same as {@link #toMap(Function)} but the order of the elements in a {@link List} is sorted by the provided
     * order.
     *
     * @param toValue maps map list elements
     * @param order   comparison used to sort the lists representing the map values
     * @param <T>     type of map value list elements
     * @return this {@link JsonMultiMap} as {@link Map}
     * @throws JsonTreeException in case this node does exist but is not an object node
     */
    default <T> Map<String, List<T>> toMap( Function<E, T> toValue, Comparator<T> order ) {
        if ( isUndefined() ) {
            return Map.of();
        }
        Map<String, List<T>> res = new LinkedHashMap<>();
        forEach( ( key, value ) -> {
            List<T> list = value.toList( toValue );
            if ( order != null ) {
                list = new ArrayList<>( list );
                list.sort( order );
            }
            res.put( key, list );

        } );
        return res;
    }

    /**
     * Maps this multimap list elements to a lazy transformed view where each entry value of the original map is
     * transformed by the given function when accessed.
     * <p>
     * This means the returned multimap always has same size as the original map.
     *
     * @param projection transformer function
     * @param <V>        type of the transformer output for list elements of each of the map entry lists
     * @return a lazily transformed multimap view of this multimap
     */
    default <V extends JsonValue> JsonMultiMap<V> project( Function<E, V> projection ) {
        final class JsonMultiMapProjection extends CollectionView<JsonMultiMap<E>> implements JsonMultiMap<V> {

            private JsonMultiMapProjection( JsonMultiMap<E> viewed ) {
                super( viewed );
            }

            @Override
            public JsonList<V> get( String key ) {
                return viewed.get( key ).project( projection );
            }

            @Override
            public Class<? extends JsonValue> asType() {
                return JsonMultiMap.class;
            }
        }
        return new JsonMultiMapProjection( this );
    }
}
