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

import org.hisp.dhis.jsontree.Validation.Rule;
import org.hisp.dhis.jsontree.validation.JsonValidator;

import java.util.Collection;
import java.util.function.Function;

import static java.util.Arrays.stream;

/**
 * Represents a JSON object node.
 * <p>
 * As all nodes are mere views or virtual field path access will never throw a {@link JsonPathException}.
 * <p>
 * Whether a field with a given name exists is determined first when {@link JsonValue#exists()} or other value accessing
 * operations are performed on a node.
 *
 * @author Jan Bernitt
 */
@Validation.Ignore
public interface JsonObject extends JsonAbstractObject<JsonValue> {

    /**
     * Access to object fields by name.
     * <p>
     * Note that this neither checks if a field exist nor if it has the assumed type.
     *
     * @param name field name
     * @param as   assumed type of the field
     * @param <T>  returned field type
     * @return field value for the given name
     */
    <T extends JsonValue> T get( String name, Class<T> as );

    default JsonValue get( String name ) {
        return get( name, JsonValue.class );
    }

    default JsonObject getObject( String name ) {
        return get( name, JsonObject.class );
    }

    default JsonNumber getNumber( String name ) {
        return get( name, JsonNumber.class );
    }

    default JsonArray getArray( String name ) {
        return get( name, JsonArray.class );
    }

    default JsonString getString( String name ) {
        return get( name, JsonString.class );
    }

    default JsonBoolean getBoolean( String name ) {
        return get( name, JsonBoolean.class );
    }

    default <E extends JsonValue> JsonList<E> getList( String name, Class<E> as ) {
        return JsonAbstractCollection.asList( getArray( name ), as );
    }

    default <E extends JsonValue> JsonMap<E> getMap( String name, Class<E> as ) {
        return JsonAbstractCollection.asMap( getObject( name ), as );
    }

    default <E extends JsonValue> JsonMultiMap<E> getMultiMap( String name, Class<E> as ) {
        return JsonAbstractCollection.asMultiMap( getObject( name ), as );
    }

    /**
     * Uses the {@link Required} annotations present to check whether this object conforms to the provided type
     *
     * @param type object type to check
     * @param rules optional set of {@link Rule}s to check, empty includes all
     * @return true if this is an object and has all {@link Required} members of the provided type
     * @since 0.11 (in this form with rules parameter)
     */
    default boolean isA( Class<? extends JsonObject> type, Rule... rules ) {
        try {
            asA( type, rules );
            return true;
        } catch ( JsonPathException | JsonTreeException | JsonSchemaException ex ) {
            return false;
        }
    }

    /**
     * "Cast" and check against provided object shape.
     *
     * @param type expected object type
     * @param rules optional set of {@link Rule}s to check, empty includes all
     * @param <T>  type check and of the result
     * @return this node as the provided object type
     * @throws JsonPathException   when this node does not exist
     * @throws JsonTreeException   when this node is not an object
     * @throws JsonSchemaException when this node does not have all of the {@link Required} properties present
     * @since 0.11 (in this form with rules parameter)
     */
    default <T extends JsonObject> T asA( Class<T> type, Rule... rules )
        throws JsonPathException, JsonTreeException, JsonSchemaException {
        T obj = as( type );
        JsonValidator.validate( obj, type, rules );
        return obj;
    }

    /**
     * Maps this object's members to a lazy transformed object view where each property value of the original object is
     * transformed by the given function when accessed.
     * <p>
     * This means the returned object always has the same number of members as the original object.
     *
     * @param projection transformer function
     * @param <V>        type of the transformer output, members of the object view
     * @return a lazily transformed object view of this object
     */
    default <V extends JsonValue> JsonObject project( Function<JsonValue, V> projection ) {
        final class JsonObjectProjection extends CollectionView<JsonObject> implements JsonObject {

            private JsonObjectProjection( JsonObject viewed ) {
                super( viewed );
            }

            @Override
            public <T extends JsonValue> T get( String name, Class<T> as ) {
                return projection.apply( viewed.get( name ) ).as( as );
            }

            @Override
            public boolean has( Collection<String> names ) {
                return viewed.has( names );
            }

            @Override
            public Class<? extends JsonValue> asType() {
                return JsonObject.class;
            }
        }
        return new JsonObjectProjection( this );
    }
}
