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

import org.hisp.dhis.jsontree.validation.JsonValidator;

import java.util.List;
import java.util.function.Function;

/**
 * The {@link JsonValue} is a virtual read-only view for {@link JsonNode} representing an actual {@link JsonTree}.
 * <p>
 * As usual there are specific node type for the JSON building blocks:
 * <ul>
 * <li>{@link JsonObject}</li>
 * <li>{@link JsonArray}</li>
 * <li>{@link JsonString}</li>
 * <li>{@link JsonNumber}</li>
 * <li>{@link JsonBoolean}</li>
 * </ul>
 * In addition, there is {@link JsonCollection} as a common base type of
 * {@link JsonObject} and {@link JsonArray}, as well as {@link JsonPrimitive} as common
 * base type of {@link JsonString}, {@link JsonNumber} and {@link JsonBoolean}.
 * <p>
 * In addition {@link JsonList} is a typed JSON array of uniform elements (which
 * can be understood as a typed wrapper around a {@link JsonArray}).
 * <p>
 * Similarly {@link JsonMap} is a typed JSON object map of uniform values (which
 * can be understood as a typed wrapper around a {@link JsonObject}).
 * <p>
 * The API is designed to:
 * <ul>
 * <li>be extended by further type extending {@link JsonValue}, such as
 * {@link JsonDate} but also further specific object type</li>
 * <li>fail at point of assertion. This means traversing the virtual tree does
 * not cause errors unless explicitly provoked.</li>
 * <li>be implemented by a single class which only builds a lookup path and
 * checks or provides the leaf values on demand. Interfaces not directly
 * implemented by this class are dynamically created using a
 * {@link java.lang.reflect.Proxy}.</li>
 * </ul>
 *
 * @author Jan Bernitt
 * @see JsonMixed
 */
@Validation.Ignore
public interface JsonValue {

    /**
     * Lift an actual {@link JsonNode} tree to a virtual {@link JsonValue}.
     *
     * @param node non null
     * @return the provided {@link JsonNode} as virtual {@link JsonValue}
     */
    static JsonValue of( JsonNode node ) {
        return node == null ? JsonVirtualTree.NULL : JsonMixed.of( node );
    }

    /**
     * View the provided JSON string as virtual lazy evaluated tree.
     *
     * @param json a valid JSON string
     * @return virtual JSON tree root {@link JsonValue}
     */
    static JsonValue of( String json ) {
        return of( json, JsonTypedAccess.GLOBAL );
    }

    /**
     * View the provided JSON string as virtual lazy evaluated tree using the provided {@link JsonTypedAccessStore} for
     * mapping to Java method return type.
     *
     * @param json  a valid JSON string
     * @param store mapping used to map JSON values to the Java method return type of abstract methods, when
     *              {@code null} default mapping is used
     * @return virtual JSON tree root {@link JsonValue}
     */
    static JsonValue of( String json, JsonTypedAccessStore store ) {
        return json == null || "null".equals( json ) ? JsonVirtualTree.NULL : JsonMixed.of( json, store );
    }

    /**
     * If the {@link JsonValue} is not yet proxied, that means it is the unchanged underlying virtual tree, the returned
     * type is {@link JsonMixed}.
     *
     * @return The type the current proxy represents. OBS! This is not necessarily the type of the actual JSON value in
     * the document, just the type it was requested to be programmatically (assumed type).
     * @since 0.10
     */
    Class<? extends JsonValue> asType();

    /**
     * @throws JsonSchemaException in case this value does not match the schema of {@link #asType()}
     * @since 0.11
     */
    default void validate() {
        validate( asType() );
    }

    /**
     * @param schema the schema to validate against
     * @throws JsonSchemaException      in case this value does not match the given schema
     * @throws IllegalArgumentException in case the given schema is not an interface
     * @since 0.11
     */
    default void validate( Class<? extends JsonValue> schema ) {
        JsonValidator.validate( this, schema );
    }

    /**
     * A property exists when it is part of the JSON response. This means it can be declared JSON {@code null}. Only a
     * path that does not exist returns false.
     *
     * @return true, if the value exists, else false
     */
    boolean exists();

    /**
     * @return true if the value exists and is defined JSON {@code null}
     * @throws JsonPathException in case this value does not exist in the JSON document
     */
    default boolean isNull() {
        return node().getType() == JsonNodeType.NULL;
    }

    /**
     * @return true if this JSON node either does not exist at all or is defined as JSON {@code null}, otherwise false
     */
    default boolean isUndefined() {
        return !exists() || isNull();
    }

    /**
     * @return true if the value exists and is a JSON array node (empty or not) but not JSON {@code null}
     * @throws JsonPathException in case this value does not exist in the JSON document
     */
    default boolean isArray() {
        return node().getType() == JsonNodeType.ARRAY;
    }

    /**
     * @return true if the value exists and is an JSON object node (empty or not) but not JSON {@code null}
     * @throws JsonPathException in case this value does not exist in the JSON document
     */
    default boolean isObject() {
        return node().getType() == JsonNodeType.OBJECT;
    }

    /**
     * @return true if the value exists and is an JSON number node (not JSON {@code null})
     * @throws JsonPathException in case this value does not exist in the JSON document
     * @since 0.10
     */
    default boolean isNumber() {
        return node().getType() == JsonNodeType.NUMBER;
    }

    /**
     * @return true if the value exists and is an JSON number node and has no fraction part or a fraction of zero
     * @throws JsonPathException in case this value does not exist in the JSON document
     * @since 0.10
     */
    default boolean isInteger() {
        return isNumber() && ((Number) node().value()).doubleValue() % 1d == 0d;
    }

    /**
     * @return true if the value exists and is an JSON string node (not JSON {@code null})
     * @throws JsonPathException in case this value does not exist in the JSON document
     * @since 0.10
     */

    default boolean isString() {
        return node().getType() == JsonNodeType.STRING;
    }

    /**
     * @return true if the value exists and is an JSON boolean node (not JSON {@code null})
     * @throws JsonPathException in case this value does not exist in the JSON document
     * @since 0.10
     */
    default boolean isBoolean() {
        return node().getType() == JsonNodeType.BOOLEAN;
    }

    /**
     * "Cast" this JSON value to a more specific type. Note that any type can be switched to any other type. Types here
     * are just what we believe to be true. They are only here to guide us, not assert existence.
     * <p>
     * Whether assumptions are actually true is determined when leaf values are accessed.
     *
     * @param as  assumed value type
     * @param <T> value type returned
     * @return this object as the provided type, this might mean this object is wrapped as the provided type or
     * literally cast.
     */
    <T extends JsonValue> T as( Class<T> as );

    /**
     * @return This value as {@link JsonObject} (same as {@code as(JsonObject.class)})
     */
    default JsonObject asObject() {
        return as( JsonObject.class );
    }

    /**
     * This value as a list of uniform elements (view on JSON array).
     *
     * @param elementType assumed value element type
     * @param <E>         type of list elements
     * @return list view of this value (assumes array)
     */
    default <E extends JsonValue> JsonList<E> asList( Class<E> elementType ) {
        return JsonCollection.asList( as( JsonArray.class ), elementType );
    }

    /**
     * This value as map of uniform values (view on JSON object).
     *
     * @param valueType assumed map value type
     * @param <V>       type of map values
     * @return map view of this value (assumes object)
     */
    default <V extends JsonValue> JsonMap<V> asMap( Class<V> valueType ) {
        return JsonCollection.asMap( as( JsonObject.class ), valueType );
    }

    /**
     * This value as map of list value with of uniform elements (view on JSON object).
     *
     * @param valueType assumed map value type
     * @param <V>       type of map values
     * @return map view of this value (assumes object)
     */
    default <V extends JsonValue> JsonMultiMap<V> asMultiMap( Class<V> valueType ) {
        return JsonCollection.asMultiMap( as( JsonObject.class ), valueType );
    }

    /**
     * Used to get a list when the actual value may be either a single value or an array of the same single value type.
     *
     * @param elementType single value or array element type
     * @param toElement   to convert to target element type
     * @param <T>         type target type for element conversion
     * @param <V>         type of array elements (or the single value)
     * @return an empty list for undefined, a list with one element for a single simple source value, or a list with the
     * elements of an array source value.
     * @throws JsonTreeException in case the single value or an element in the array is not compatible with the element
     *                           type
     * @since 0.10
     */
    default <T, V extends JsonValue> List<T> toListFromVarargs( Class<V> elementType, Function<V, T> toElement ) {
        return isUndefined() ? List.of() : isArray()
            ? asList( elementType ).toList( toElement )
            : List.of( toElement.apply( as( elementType ) ) );
    }

    /**
     * Access the node in the JSON document. This can be the low level API that is concerned with extraction by path.
     * <p>
     * This might be useful in test to access the {@link JsonNode#getDeclaration()} to modify and reuse it.
     *
     * @return the underlying {@link JsonNode} in the overall JSON document if it exists
     * @throws JsonPathException in case this value does not exist in the JSON document
     */

    JsonNode node();

    /**
     * @return JSON declaration for this value
     * @throws JsonPathException if this node does not exist
     * @since 0.11
     */
    default String toJson() {
        return node().getDeclaration();
    }

    /**
     * @return true, if results of JSON to Java method return type mapping via {@link JsonTypedAccessStore} are cached
     * so that complex return values are only computed once. Any interface return type is considered a complex type.
     */
    default boolean isAccessCached() {
        return false;
    }

    /**
     * @return This value but with typed access cached if supported. Check using {@link #isAccessCached()} on result.
     */
    default JsonValue withAccessCached() {
        return this;
    }
}
