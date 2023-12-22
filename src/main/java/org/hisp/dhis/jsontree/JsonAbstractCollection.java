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

import org.hisp.dhis.jsontree.internal.Surly;

import java.util.Optional;
import java.util.function.Predicate;

import static org.hisp.dhis.jsontree.Validation.NodeType.ARRAY;
import static org.hisp.dhis.jsontree.Validation.NodeType.OBJECT;

/**
 * Common base class for JSON nodes that have children.
 * <p>
 * These are their
 * <ul>
 * <li>{@link JsonArray}s, or</li>
 * <li>{@link JsonObject}s</li>
 * </ul>
 * <p>
 * Each has their typed wrapper type:
 * <ul>
 * <li>{@link JsonList}</li>
 * <li>{@link JsonMap}</li>
 * </ul>
 *
 * @author Jan Bernitt
 */
@Validation( type = { ARRAY, OBJECT } )
@Validation.Ignore
public interface JsonAbstractCollection extends JsonValue {

    /**
     * @return true, in case the collection exists but has no elements and is not {@code null}
     * @throws JsonPathException in case this value does not exist
     * @throws JsonTreeException in case the value does exist but is not a collection
     */
    boolean isEmpty();

    /**
     * @return the number of elements in the collection
     * @throws JsonPathException in case this value does not exist
     * @throws JsonTreeException in case the value does exist but is not a collection
     */
    int size();

    static <E extends JsonValue> JsonList<E> asList( JsonArray array, Class<E> as ) {
        class ListView extends CollectionView<JsonArray> implements JsonList<E> {

            ListView( JsonArray viewed ) {
                super( viewed );
            }

            @Override
            public E get( int index ) {
                return array.get( index, as );
            }

            @Override
            public Class<? extends JsonValue> asType() {
                return JsonList.class;
            }
        }
        return new ListView( array );
    }

    static <E extends JsonValue> JsonMap<E> asMap( JsonObject object, Class<E> as ) {
        class MapView extends CollectionView<JsonObject> implements JsonMap<E> {

            MapView( JsonObject viewed ) {
                super( viewed );
            }

            @Override
            public E get( String key ) {
                return viewed.get( key, as );
            }

            @Override
            public Class<? extends JsonValue> asType() {
                return JsonMap.class;
            }
        }
        return new MapView( object );
    }

    static <E extends JsonValue> JsonMultiMap<E> asMultiMap( JsonObject object, Class<E> as ) {
        class MultiMapView extends CollectionView<JsonObject> implements JsonMultiMap<E> {

            MultiMapView( JsonObject viewed ) {
                super( viewed );
            }

            @Override
            public JsonList<E> get( String key ) {
                return viewed.getList( key, as );
            }

            @Override
            public Class<? extends JsonValue> asType() {
                return JsonMultiMap.class;
            }
        }
        return new MultiMapView( object );
    }

    abstract class CollectionView<T extends JsonAbstractCollection> implements
        JsonAbstractCollection {

        protected final T viewed;

        protected CollectionView( T viewed ) {
            this.viewed = viewed;
        }

        @Override
        public final JsonNode node() {
            return viewed.node();
        }

        @Override
        public final boolean isEmpty() {
            return viewed.isEmpty();
        }

        @Override
        public final int size() {
            return viewed.size();
        }

        @Override
        public final boolean exists() {
            return viewed.exists();
        }

        @Override
        public final boolean isAccessCached() {
            return viewed.isAccessCached();
        }

        @Surly @Override
        public JsonTypedAccessStore getAccessStore() {
            return viewed.getAccessStore();
        }

        @Override
        public final <V extends JsonValue> V as( Class<V> as ) {
            return viewed.as( as );
        }

        @Override
        public final String toString() {
            return viewed.toString();
        }
    }
}
