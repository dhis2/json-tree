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

import org.hisp.dhis.jsontree.JsonDiff.Mode;
import org.hisp.dhis.jsontree.internal.Maybe;
import org.hisp.dhis.jsontree.internal.Surly;

import java.io.Reader;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * The {@link JsonValue} is a virtual read-only view for {@link JsonNode}, which
 * is representing an actual {@link JsonTree}.
 * <p>
 * As usual there are specific node type for the JSON building blocks:
 * <ul>
 * <li>{@link JsonObject}</li>
 * <li>{@link JsonArray}</li>
 * <li>{@link JsonString}</li>
 * <li>{@link JsonNumber}</li>
 * <li>{@link JsonBoolean}</li>
 * </ul>
 * In addition, there is {@link JsonAbstractCollection} as a common base type of
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
 * {@link JsonDate}, but also further specific object types</li>
 * <li>fail at the point of assertion/use not traversal.
 * This means traversing the virtual tree does not cause errors unless explicitly
 * provoked by a "terminal operation" or malformed input</li>
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
    static JsonValue of( String json, @Surly JsonTypedAccessStore store ) {
        return json == null || "null".equals( json ) ? JsonVirtualTree.NULL : JsonMixed.of( json, store );
    }

    /**
     * @param file a JSON file in UTF-8 encoding
     * @return root of the virtual tree representing the given JSON input
     * @since 1.0
     */
    static JsonValue of( Path file ) {
        return of(JsonNode.of( file ));
    }

    /**
     * @param json JSON input
     * @return root of the virtual tree representing the given JSON input
     * @since 1.0
     */
    static JsonValue of( Reader json ) {
        return of(JsonNode.of( json, null ));
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
     * @return this node path
     * @since 0.11
     */
    @Surly
    String path();

    /**
     * @return this node's type or null if this node does not exist in the actual tree
     * @since 0.11
     */
    @Maybe
    default JsonNodeType type() {
        return !exists() ? null : node().getType();
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
        return type() == JsonNodeType.NULL;
    }

    /**
     * @return true if this JSON node either does not exist at all or is defined as JSON {@code null}, otherwise false
     */
    default boolean isUndefined() {
        return !exists() || isNull();
    }

    /**
     * @return true if the value exists and is a JSON array node (empty or not) but not JSON {@code null}
     */
    default boolean isArray() {
        return type() == JsonNodeType.ARRAY;
    }

    /**
     * @return true if the value exists and is an JSON object node (empty or not) but not JSON {@code null}
     */
    default boolean isObject() {
        return type() == JsonNodeType.OBJECT;
    }

    /**
     * @return true if the value exists and is an JSON number node (not JSON {@code null})
     * @since 0.10
     */
    default boolean isNumber() {
        return type() == JsonNodeType.NUMBER;
    }

    /**
     * @return true if the value exists and is an JSON number node and has no fraction part or a fraction of zero
     * @since 0.10
     */
    default boolean isInteger() {
        return isNumber() && ((Number) node().value()).doubleValue() % 1d == 0d;
    }

    /**
     * @return true if the value exists and is an JSON string node (not JSON {@code null})
     * @since 0.10
     */

    default boolean isString() {
        return type() == JsonNodeType.STRING;
    }

    /**
     * @return true if the value exists and is an JSON boolean node (not JSON {@code null})
     * @since 0.10
     */
    default boolean isBoolean() {
        return type() == JsonNodeType.BOOLEAN;
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
     * Same as {@link #as(Class)} but with an additional parameter to pass a callback function. This allows to observe
     * the API calls for meta-programming. This should not be used in "normal" API usage.
     * <p>
     * Not all methods can be observed as some are handled internally without ever going via the proxy. However, in
     * contrast to {@link #as(Class)} when using this method any call of a default method is handled via proxy.
     *
     * @param as     assumed value type for this value
     * @param onCall a function that is called before the proxy handles an API call that allows to observe calls
     * @param <T>    value type returned
     * @return this object as the provided type, this might mean this object is wrapped as the provided type or
     * @since 1.4
     */
    <T extends JsonValue> T as( Class<T> as, BiConsumer<Method, Object[]> onCall );

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
        return JsonAbstractCollection.asList( as( JsonArray.class ), elementType );
    }

    /**
     * This value as map of uniform values (view on JSON object).
     *
     * @param valueType assumed map value type
     * @param <V>       type of map values
     * @return map view of this value (assumes object)
     */
    default <V extends JsonValue> JsonMap<V> asMap( Class<V> valueType ) {
        return JsonAbstractCollection.asMap( as( JsonObject.class ), valueType );
    }

    /**
     * This value as map of list value with of uniform elements (view on JSON object).
     *
     * @param valueType assumed map value type
     * @param <V>       type of map values
     * @return map view of this value (assumes object)
     */
    default <V extends JsonValue> JsonMultiMap<V> asMultiMap( Class<V> valueType ) {
        return JsonAbstractCollection.asMultiMap( as( JsonObject.class ), valueType );
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
     * The same information does not imply the value is identically defined. There can be differences in formatting, the
     * order of object members or how the same numerical value is encoded for a number.
     * <p>
     * Equivalence is always symmetric; if A is equivalent to B then B must also be equivalent to A.
     *
     * @param other the value to compare with
     * @return true, if this value represents the same information, else false
     * @since 1.1
     */
    default boolean equivalentTo(JsonValue other) {
        return equivalentTo( this, other, JsonValue::equivalentTo );
    }

    /**
     * The two values only differ in formatting (whitespace outside of values).
     * <p>
     * All values that are identical are also {@link #equivalentTo(JsonValue)}.
     * <p>
     * Identical is always symmetric; if A is identical to B then B must also be identical to A.
     *
     * @param other the value to compare with
     * @return true, if this value only differs in formatting from the other value, otherwise false
     * @since 1.1
     */
    default boolean identicalTo(JsonValue other) {
        if (!equivalentTo( this, other, JsonValue::identicalTo )) return false;
        if (isNumber()) return toJson().equals( other.toJson() );
        if (!isObject()) return true;
        // names must be in same order
        return asObject().names().equals( other.asObject().names() );
    }

    private static boolean equivalentTo(JsonValue a, JsonValue b, BiPredicate<JsonValue, JsonValue> compare ) {
        if (a.type() != b.type()) return false;
        if (a.isUndefined()) return true; // includes null
        if (a.isString()) return a.as( JsonString.class ).string().equals( b.as( JsonString.class ).string() );
        if (a.isBoolean()) return a.as(JsonBoolean.class).booleanValue() == b.as( JsonBoolean.class ).booleanValue();
        if (a.isNumber()) return a.as( JsonNumber.class ).doubleValue() == b.as( JsonNumber.class ).doubleValue();
        if (a.isArray()) {
            JsonArray ar = a.as( JsonArray.class );
            JsonArray br = b.as( JsonArray.class );
            return ar.size() == br.size() && ar.indexes().allMatch( i  -> compare.test( ar.get( i ), br.get( i ) ));
        }
        JsonObject ao = a.asObject();
        JsonObject bo = b.asObject();
        return ao.size() == bo.size() && ao.keys().allMatch( key -> compare.test( ao.get( key ), bo.get( key ) ) );
    }

  /**
   * Compare this value (expected) with the given value (actual) using {@link Mode#DEFAULT}.
   *
   * @since 1.7
   * @param with the JSON to compare this JSON value with. To benefit from annotation specific
   *     handling the value must be "cast" to the root object type using {@link #as(Class)} prior to
   *     calling this method
   * @return the differences
   */
  default JsonDiff diff(JsonValue with) {
    return diff(with, Mode.DEFAULT);
  }

  /**
   * Compare this value (expected) with the given value (actual) using the provided mode.
   *
   * @since 1.7
   * @param with the JSON to compare this JSON value with. To benefit from annotation specific
   *     handling the value must be "cast" to the root object type using {@link #as(Class)} prior to
   *     calling this method
   * @param mode of how strict to make the comparison
   * @return the differences
   */
  default JsonDiff diff(JsonValue with, Mode mode) {
    return JsonDiff.of(this, with, mode);
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
     * @return JSON declaration for this value (as originally given)
     * @throws JsonPathException if this node does not exist
     * @since 0.11
     */
    default String toJson() {
        return node().getDeclaration();
    }

    /**
     * @return JSON declaration for this value in a minimized formatting
     * @throws JsonPathException if this node does not exist
     * @since 0.11
     */
    default String toMinimizedJson() {
        if ( !isObject() && !isArray() ) return toJson();
        if ( isObject() ) return JsonBuilder.createObject( JsonBuilder.MINIMIZED_FULL,
                obj -> asObject().entries().forEach( e -> obj.addMember( e.getKey(), e.getValue().node() ) ) )
            .getDeclaration();
        return JsonBuilder.createArray( JsonBuilder.MINIMIZED_FULL,
            arr -> as( JsonArray.class ).forEach( e -> arr.addElement( e.node() ) ) ).getDeclaration();
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

    /**
     * @return the store used by this instance
     * @since 0.11
     */
    @Surly
    JsonTypedAccessStore getAccessStore();

    /**
     * Finds the first value that satisfies the given test.
     * <p>
     * OBS! When no match is found a value that does not exist is returned.
     *
     * @param type API to test the node with
     * @param test test to perform on all objects that satisfy the type filter
     * @param <T>  type of the object to find
     * @return the first found match or JSON {@code null} object
     */
    default <T extends JsonValue> T find( Class<T> type, Predicate<T> test ) {
        if ( isUndefined() ) return JsonMixed.of( "{}" ).get( "notFound", type );
        JsonTypedAccessStore store = getAccessStore();
        Optional<JsonNode> match = node().find(
            node -> {
                try {
                    return test.test( node.lift( store ).as( type ) );
                } catch ( JsonTreeException | JsonPathException ex ) {
                    // the test called a method that was not supported by the tested node
                    return false;
                }
            } );
        return match.isEmpty()
            ? JsonMixed.of( "{}" ).get( "notFound", type )
            : match.get().lift( store ).as( type );
    }

}
