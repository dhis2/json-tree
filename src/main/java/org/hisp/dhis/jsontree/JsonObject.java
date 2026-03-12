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

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Arrays.stream;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SIZED;

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

    @Override
    default Stream<JsonValue> values() {
        return values(true);
    }

    /**
     * @implNote This utilizes {@link JsonNode#members(boolean)} avoiding map lookups for each element. On
     *     {@link JsonAbstractArray} level this cannot be done as the node cannot be {@link
     *     JsonNode#lift(JsonAccessors)} ed to the unknown generic target type.
     * @param remember true, to internally "remember" the elements iterated over so far, false to only iterate without
     *                   keeping references to them further on so GC can pick em up
     * @since 1.9
     */
    default Stream<JsonValue> values(boolean remember) {
        if (isUndefined() || isEmpty()) return Stream.empty();
        JsonAccessors accessors = getAccessors();
        return StreamSupport.stream( node().members(remember), false)
            .map( e -> e.getValue().lift( accessors ) );
    }

    @Override
    default Stream<Map.Entry<Text, JsonValue>> entries() {
        return entries(true);
    }

    /**
     * @implNote This utilizes {@link JsonNode#members(boolean)} avoiding map lookups for each element. On
     *     {@link JsonAbstractArray} level this cannot be done as the node cannot be {@link
     *     JsonNode#lift(JsonAccessors)} ed to the unknown generic target type.
     * @param remember true, to internally "remember" the elements iterated over so far, false to only iterate without
     *                   keeping references to them further on so GC can pick em up
     * @since 1.9
     */
    default Stream<Map.Entry<Text, JsonValue>> entries(boolean remember) {
        if (isUndefined() || isEmpty()) return Stream.empty();
        JsonAccessors accessors = getAccessors();
        return StreamSupport.stream( node().members(remember), false)
            .map( e -> Map.entry( e.getKey(), e.getValue().lift( accessors ) ) );
    }

    /**
     * An object property based on a default method declared in a type extending {@link JsonObject}.
     *
     * @param in       the {@link JsonObject} or {@link Record} type that declared the property
     * @param jsonName of the property
     * @param jsonType the type the property is resolved to internally when calling {@link #get(CharSequence, Class)}
     * @param javaName the name of the java property accessed that caused the JSON property to be resolved
     * @param javaType the return type of the underlying method that declares the property
     * @param source   the underlying method that declared the property
     * @since 1.4
     */
    record Property(Class<?> in, Text jsonName, Class<? extends JsonValue> jsonType,
                    String javaName, AnnotatedType javaType, AnnotatedElement source) {}

    /**
     * Note that there can be more than one property with the same {@link Property#javaName()} in case the method it
     * reflects accesses more than one member from the JSON object. In such a case each access is a property of the
     * accessed {@link Property#jsonName()}  with the same {@link Property#javaName()}.
     *
     * @return a model of this object in form its properties in no particular order
     * @since 1.4
     */
    static List<Property> properties(Class<?> of) {
        return JsonVirtualTree.properties( of );
    }


    /**
     * Access to object fields by name.
     * <p>
     * Note that this neither checks if a field exist nor if it has the assumed type.
     *
     * @param name field name
     * @param as   assumed type of the field
     * @param <T>  returned field type
     * @return field value for the given name
     * @since 1.9
     */
    <T extends JsonValue> T get( Text name, Class<T> as );
    default JsonValue get( Text name ) {
        return get( name, JsonValue.class );
    }

    <T extends JsonValue> T get( JsonPath subPath, Class<T> as );

    /**
     * @see #get(Text, Class)
     */
    default <T extends JsonValue> T get( CharSequence name, Class<T> as ) {
        if (name instanceof String s && JsonPath.isSyntaxPresent( s ))
            return get( JsonPath.of( s ), as );
        return get( Text.of( name ), as );
    }

    default JsonValue get( CharSequence name ) {
        return get( name, JsonValue.class );
    }

    default JsonObject getObject( CharSequence name ) {
        return get( name, JsonObject.class );
    }

    default JsonNumber getNumber( CharSequence name ) {
        return get( name, JsonNumber.class );
    }

    default JsonArray getArray( CharSequence name ) {
        return get( name, JsonArray.class );
    }

    default JsonString getString( CharSequence name ) {
        return get( name, JsonString.class );
    }

    default JsonBoolean getBoolean( CharSequence name ) {
        return get( name, JsonBoolean.class );
    }

    default <E extends JsonValue> JsonList<E> getList( CharSequence name, Class<E> as ) {
        return JsonAbstractCollection.asList( getArray( name ), as );
    }

    default <E extends JsonValue> JsonMap<E> getMap( CharSequence name, Class<E> as ) {
        return JsonAbstractCollection.asMap( getObject( name ), as );
    }

    default <E extends JsonValue> JsonMultiMap<E> getMultiMap( CharSequence name, Class<E> as ) {
        return JsonAbstractCollection.asMultiMap( getObject( name ), as );
    }

    /**
     * Uses the JSON schema validation to check whether this object conforms to the provided type
     *
     * @param schema a subtype of {@link JsonObject} or {@link Record} to check agsinst
     * @param rules optional set of {@link Rule}s to check, empty includes all
     * @return true if this is an object is valid against the provided schema
     * @since 0.11 (in this form with rules parameter)
     */
    default boolean isA( Class<?> schema, Rule... rules ) {
        try {
            JsonValidator.validate( this, schema, rules );
            return true;
        } catch ( JsonPathException | JsonTreeException | JsonSchemaException ex ) {
            return false;
        }
    }

    /**
     * "Cast" and check against provided object shape.
     *
     * @param schema a subtype of {@link JsonObject} or {@link Record} to check agsinst
     * @param rules optional set of {@link Rule}s to check, empty includes all
     * @param <T>  type check and of the result
     * @return this node as the provided object type
     * @throws JsonPathException   when this node does not exist
     * @throws JsonTreeException   when this node is not an object
     * @throws JsonSchemaException when this node does not have all of the {@link Required} properties present
     * @since 0.11 (in this form with rules parameter)
     */
    default <T extends JsonObject> T asA( Class<T> schema, Rule... rules )
        throws JsonPathException, JsonTreeException, JsonSchemaException {
        T obj = as( schema );
        JsonValidator.validate( obj, schema, rules );
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
            public <T extends JsonValue> T get( Text name, Class<T> as ) {
                return projection.apply( viewed.get( name ) ).as( as );
            }

            @Override
            public <T extends JsonValue> T get( JsonPath subPath, Class<T> as ) {
                return projection.apply( viewed.get( subPath, JsonValue.class ) ).as( as );
            }

            @Override
            public boolean has( Collection<? extends CharSequence> names ) {
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
