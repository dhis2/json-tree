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

import java.util.List;
import java.util.function.Function;

import static java.util.function.Predicate.not;
import static org.hisp.dhis.jsontree.Validation.NodeType.ARRAY;

/**
 * A {@link JsonList} is nothing else then a {@link JsonArray} with "typed" uniform elements.
 *
 * @param <E> type of the list elements
 * @author Jan Bernitt
 */
@Validation( type = ARRAY )
@Validation.Ignore
public interface JsonList<E extends JsonValue> extends JsonAbstractArray<E> {

    /**
     * Convert to Java list.
     *
     * @param toValue maps from {@link JsonValue} to a plain JAVA value
     * @param <T>     type of result list elements
     * @return this list mapped to a {@link List} of elements mapped by the provided mapper function from the
     * {@link JsonValue}s of this {@link JsonList}. Undefined or JSON null is mapped to an empty list.
     * @see #toList(Function, Object)
     */
    default <T> List<T> toList( Function<E, T> toValue ) {
        return stream().map( toValue ).toList();
    }

    /**
     * Convert to Java list with null or undefined elements replaced with a provided default value.
     * <p>
     * Undefined can occur because of views.
     *
     * @param toValue  maps from {@link JsonValue} to a plain JAVA value
     * @param whenUndefined value used when {@link JsonValue} is {@link JsonValue#isUndefined()}
     * @param <T>      type of result list elements
     * @return this list mapped with any elements defined JSON null replaced with the provided default
     */
    default <T> List<T> toList( Function<E, T> toValue, T whenUndefined ) {
        return toList( e -> e.isUndefined() ? whenUndefined : toValue.apply( e ) );
    }

    /**
     * Convert to Java list with null or undefined elements filtered out.
     * <p>
     * Undefined can occur because of views.
     *
     * @param toValue maps from {@link JsonValue} to a plain JAVA value
     * @param <T>     type of result list elements
     * @return existing elements of this list mapped by the provided toValue function. Undefined or JSON null is mapped
     * to an empty list.
     */
    default <T> List<T> toListOfNonNullElements( Function<E, T> toValue ) {
        if ( isUndefined() ) return List.of();
        return indexes().mapToObj( this::get ).filter( not(JsonValue::isUndefined) ).map( toValue ).toList();
    }

    /**
     * Maps this list to a lazy transformed list view where each element of the original list is transformed by the
     * given function when accessed.
     * <p>
     * This means the returned list always has same size as the original list.
     *
     * @param projection transformer function
     * @param <V>        type of the transformer output, elements of the list view
     * @return a lazily transformed list view of this list
     */
    default <V extends JsonValue> JsonList<V> project( Function<E, V> projection ) {
        final class JsonListProjection extends CollectionView<JsonList<E>> implements JsonList<V> {

            private JsonListProjection( JsonList<E> self ) {
                super( self );
            }

            @Override
            public V get( int index ) {
                return projection.apply( viewed.get( index ) );
            }

            @Override
            public Class<? extends JsonValue> asType() {
                return JsonList.class;
            }
        }
        return new JsonListProjection( this );
    }
}
