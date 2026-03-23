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

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import org.hisp.dhis.jsontree.JsonDiff.Mode;
import org.hisp.dhis.jsontree.internal.CheckNull;
import org.hisp.dhis.jsontree.internal.NotNull;
import org.hisp.dhis.jsontree.internal.TerminalOp;

/**
 * The {@link JsonValue} is a virtual read-only view for {@link JsonNode}, which is representing an
 * actual {@link JsonTree}.
 *
 * <p>As usual there are specific node type for the JSON building blocks:
 *
 * <ul>
 *   <li>{@link JsonObject}
 *   <li>{@link JsonArray}
 *   <li>{@link JsonString}
 *   <li>{@link JsonNumber}
 *   <li>{@link JsonBoolean}
 * </ul>
 *
 * In addition, there is {@link JsonAbstractCollection} as a common base type of {@link JsonObject}
 * and {@link JsonArray}, as well as {@link JsonPrimitive} as common base type of {@link
 * JsonString}, {@link JsonNumber} and {@link JsonBoolean}.
 *
 * <p>In addition {@link JsonList} is a typed JSON array of uniform elements (which can be
 * understood as a typed wrapper around a {@link JsonArray}).
 *
 * <p>Similarly {@link JsonMap} is a typed JSON object map of uniform values (which can be
 * understood as a typed wrapper around a {@link JsonObject}).
 *
 * <p>The API is designed to:
 *
 * <ul>
 *   <li>be extended by further type extending {@link JsonValue}, such as {@link JsonDate}, but also
 *       further specific object types
 *   <li>fail at the point of assertion/use not traversal. This means traversing the virtual tree
 *       does not cause errors unless explicitly provoked by a "terminal operation" or malformed
 *       input
 *   <li>be implemented by a single class which only builds a lookup path and checks or provides the
 *       leaf values on demand. Interfaces not directly implemented by this class are dynamically
 *       created using a {@link java.lang.reflect.Proxy}.
 * </ul>
 *
 * @implNote When serializing a {@link JsonValue} the deserialized instance only supports the {@link
 *     JsonMixed} API as the {@link java.lang.reflect.Proxy} or subtypes cannot be restored. It has
 *     to be recreated by calling {@link #as(Class)}.
 * @author Jan Bernitt
 * @see JsonMixed
 */
@Validation.Ignore
public interface JsonValue extends Map.Entry<Text, JsonValue> {

  /**
   * Lift an actual {@link JsonNode} tree to a virtual {@link JsonValue}.
   *
   * @param node non null
   * @return the provided {@link JsonNode} as virtual {@link JsonValue}
   */
  static JsonValue of(JsonNode node) {
    return node == null ? JsonVirtualTree.NULL : JsonMixed.of(node);
  }

  /**
   * View the provided JSON string as virtual lazy evaluated tree.
   *
   * @param json a valid JSON string
   * @return virtual JSON tree root {@link JsonValue}
   */
  static JsonValue of(CharSequence json) {
    return of(json, JsonAccess.GLOBAL);
  }

  /**
   * View the provided JSON string as virtual lazy evaluated tree using the provided {@link
   * JsonAccessors} for mapping to Java method return type.
   *
   * @param json a valid JSON string
   * @param accessors mapping used to map JSON values to the Java method return type of abstract
   *     methods, when {@code null} default mapping is used
   * @return virtual JSON tree root {@link JsonValue}
   */
  static JsonValue of(CharSequence json, @NotNull JsonAccessors accessors) {
    return json == null || "null".contentEquals(json)
        ? JsonVirtualTree.NULL
        : JsonMixed.of(json, accessors);
  }

  /**
   * @param file a JSON file in UTF-8 encoding
   * @return root of the virtual tree representing the given JSON input
   * @since 1.0
   */
  static JsonValue of(Path file) {
    return of(JsonNode.of(file));
  }

  /**
   * If the {@link JsonValue} is not yet proxied, that means it is the unchanged underlying virtual
   * tree, the returned type is {@link JsonMixed}.
   *
   * @return The type the current proxy represents. OBS! This is not necessarily the type of the
   *     actual JSON value in the document, just the type it was requested to be programmatically
   *     (assumed type).
   * @since 0.10
   */
  Class<? extends JsonValue> asType();

  /**
   * @return this node path
   * @since 0.11
   */
  @NotNull
  JsonPath path();

  @Override
  default Text getKey() {
    return path().segment();
  }

  @Override
  default JsonValue getValue() {
    return this;
  }

  @Override
  default JsonValue setValue(JsonValue value) {
    throw new UnsupportedOperationException("A JSON value is an immutable view");
  }

  /**
   * The "mapping" uses the node's {@link #getAccessors()} to map the JSON to the given Java target
   * type.
   *
   * <p>When called with a subtype of {@link JsonValue} this is equivalent to calling {@link
   * #as(Class)}.
   *
   * <p>When calling with an interface that does not extend {@link JsonValue} this is still
   * equivalent to calling {@link #as(Class)} except that the API no longer gives access to the
   * methods that would be inherited from the {@link JsonValue} base.
   *
   * @param type target Java type
   * @return the JSON of this node accessed as the given Java type
   * @param <T> target Java type the JSON value of this node is mapped to (accessed as)
   * @throws JsonAccessException in case no accessor function is know to convert to the given type
   *     or the conversion is not possible from the actual JSON value
   * @since 1.9
   */
  @TerminalOp
  <T> T to(Class<T> type) throws JsonAccessException;

  /**
   * @return this node's type or null if this node does not exist in the actual tree
   * @since 0.11
   */
  @CheckNull
  @TerminalOp(canBeUndefined = true)
  default JsonNodeType type() {
    return !exists() ? null : node().getType();
  }

  /**
   * A property exists when it is part of the JSON response. This means it can be declared JSON
   * {@code null}. Only a path that does not exist returns false.
   *
   * @return true, if the value exists, else false
   */
  @TerminalOp(canBeUndefined = true)
  boolean exists();

  /**
   * @return true if the value exists and is defined JSON {@code null}
   * @throws JsonPathException in case this value does not exist in the JSON document
   */
  @TerminalOp(canBeNull = true)
  default boolean isNull() {
    return type() == JsonNodeType.NULL;
  }

  /**
   * @return true if this JSON node either does not exist at all or is defined as JSON {@code null},
   *     otherwise false
   */
  @TerminalOp(canBeUndefined = true)
  default boolean isUndefined() {
    return !exists() || isNull();
  }

  /**
   * @return true if the value exists and is a JSON array node (empty or not) but not JSON {@code
   *     null}
   */
  @TerminalOp(canBeUndefined = true)
  default boolean isArray() {
    return type() == JsonNodeType.ARRAY;
  }

  /**
   * @return true if the value exists and is an JSON object node (empty or not) but not JSON {@code
   *     null}
   */
  @TerminalOp(canBeUndefined = true)
  default boolean isObject() {
    return type() == JsonNodeType.OBJECT;
  }

  /**
   * @return true if the value exists and is an JSON number node (not JSON {@code null})
   * @since 0.10
   */
  @TerminalOp(canBeUndefined = true)
  default boolean isNumber() {
    return type() == JsonNodeType.NUMBER;
  }

  /**
   * @return true if the value exists and is an JSON number node and has no fraction part or a
   *     fraction of zero
   * @since 0.10
   */
  @TerminalOp(canBeUndefined = true)
  default boolean isInteger() {
    return isNumber() && ((Number) node().value()).doubleValue() % 1d == 0d;
  }

  /**
   * @return true, if this node is a string of exactly "NaN" as specified for the special value for
   *     Java doubles
   * @since 1.9
   */
  @TerminalOp(canBeUndefined = true)
  default boolean isNaN() {
    return isString() && node().textValue().contentEquals("NaN");
  }

  /**
   * @return true, if this node is a string of exactly "Infinity" or "-Infinity" as specified for the
   *     special value for Java doubles
   * @since 1.9
   */
  @TerminalOp(canBeUndefined = true)
  default boolean isInfinity() {
    return isString()
        && (node().textValue().contentEquals("Infinity")
            || node().textValue().contentEquals("-Infinity"));
  }

  /**
   * @return true if the value exists and is an JSON string node (not JSON {@code null})
   * @since 0.10
   */
  @TerminalOp(canBeUndefined = true)
  default boolean isString() {
    return type() == JsonNodeType.STRING;
  }

  /**
   * @return true if the value exists and is an JSON boolean node (not JSON {@code null})
   * @since 0.10
   */
  @TerminalOp(canBeUndefined = true)
  default boolean isBoolean() {
    return type() == JsonNodeType.BOOLEAN;
  }

  /**
   * "Cast" this JSON value to a more specific type. Note that any type can be switched to any other
   * type. Types here are just what we believe to be true. They are only here to guide us, not
   * assert existence.
   *
   * <p>Whether assumptions are actually true is determined when leaf values are accessed.
   *
   * @param as assumed value type
   * @param <T> value type returned
   * @return this object as the provided type, this might mean this object is wrapped as the
   *     provided type or literally cast.
   */
  <T extends JsonValue> T as(Class<T> as);

  /**
   * Same as {@link #as(Class)} but with an additional parameter to pass a callback function. This
   * allows to observe the API calls for meta-programming. This should not be used in "normal" API
   * usage.
   *
   * <p>Not all methods can be observed as some are handled internally without ever going via the
   * proxy. However, in contrast to {@link #as(Class)} when using this method any call of a default
   * method is handled via proxy.
   *
   * @param as assumed value type for this value
   * @param onCall a function that is called before the proxy handles an API call that allows to
   *     observe calls
   * @param <T> value type returned
   * @return this object as the provided type, this might mean this object is wrapped as the
   *     provided type or
   * @since 1.4
   */
  <T extends JsonValue> T as(Class<T> as, BiConsumer<Method, Object[]> onCall);

  /**
   * @return This value as {@link JsonObject} (same as {@code as(JsonObject.class)})
   */
  default JsonObject asObject() {
    return as(JsonObject.class);
  }

  /**
   * This value as a list of uniform elements (view on JSON array).
   *
   * @param elementType assumed value element type
   * @param <E> type of list elements
   * @return list view of this value (assumes array)
   */
  default <E extends JsonValue> JsonList<E> asList(Class<E> elementType) {
    return JsonAbstractCollection.asList(as(JsonArray.class), elementType);
  }

  /**
   * This value as map of uniform values (view on JSON object).
   *
   * @param valueType assumed map value type
   * @param <V> type of map values
   * @return map view of this value (assumes object)
   */
  default <V extends JsonValue> JsonMap<V> asMap(Class<V> valueType) {
    return JsonAbstractCollection.asMap(as(JsonObject.class), valueType);
  }

  /**
   * This value as map of list value with of uniform elements (view on JSON object).
   *
   * @param valueType assumed map value type
   * @param <V> type of map values
   * @return map view of this value (assumes object)
   */
  default <V extends JsonValue> JsonMultiMap<V> asMultiMap(Class<V> valueType) {
    return JsonAbstractCollection.asMultiMap(as(JsonObject.class), valueType);
  }

  /**
   * Used to get a list when the actual value may be either a single value or an array of the same
   * single value type.
   *
   * @param elementType single value or array element type
   * @param toElement to convert to target element type
   * @param <T> type target type for element conversion
   * @param <V> type of array elements (or the single value)
   * @return an empty list for undefined, a list with one element for a single simple source value,
   *     or a list with the elements of an array source value.
   * @throws JsonTreeException in case the single value or an element in the array is not compatible
   *     with the element type
   * @since 0.10
   */
  @TerminalOp(canBeUndefined = true)
  default <T, V extends JsonValue> List<T> toListFromVarargs(
      Class<V> elementType, Function<V, T> toElement) {
    return isUndefined()
        ? List.of()
        : isArray()
            ? asList(elementType).toList(toElement)
            : List.of(toElement.apply(as(elementType)));
  }

  /**
   * The same information does not imply the value is identically defined. There can be differences
   * in formatting, the order of object members or how the same numerical value is encoded for a
   * number.
   *
   * <p>Equivalence is always symmetric; if A is equivalent to B then B must also be equivalent to
   * A.
   *
   * @param other the value to compare with
   * @return true, if this value represents the same information, else false
   * @since 1.1
   */
  @TerminalOp(canBeUndefined = true)
  default boolean equivalentTo(JsonValue other) {
    return equivalentTo(this, other, JsonValue::equivalentTo);
  }

  /**
   * The two values only differ in formatting (whitespace outside of values).
   *
   * <p>All values that are identical are also {@link #equivalentTo(JsonValue)}.
   *
   * <p>Identical is always symmetric; if A is identical to B then B must also be identical to A.
   *
   * @param other the value to compare with
   * @return true, if this value only differs in formatting from the other value, otherwise false
   * @since 1.1
   */
  @TerminalOp(canBeUndefined = true)
  default boolean identicalTo(JsonValue other) {
    if (!equivalentTo(this, other, JsonValue::identicalTo)) return false;
    if (isNumber()) return node().getDeclaration().equals(other.node().getDeclaration());
    if (!isObject()) return true;
    // names must be in same order
    Iterator<Text> it1 = asObject().keys().iterator();
    Iterator<Text> it2 = other.asObject().keys().iterator();
    while (it1.hasNext() && it2.hasNext()) if (!it1.next().equals(it2.next())) return false;
    return !it1.hasNext() && !it2.hasNext();
  }

  private static boolean equivalentTo(
      JsonValue a, JsonValue b, BiPredicate<JsonValue, JsonValue> eqItems) {
    if (a.type() != b.type()) return false;
    if (a.isUndefined()) return true; // types are same, must be either both null or both undefined
    if (a.isString())
      return a.as(JsonString.class).text().contentEquals(b.as(JsonString.class).text());
    if (a.isBoolean())
      return a.as(JsonBoolean.class).booleanValue() == b.as(JsonBoolean.class).booleanValue();
    if (a.isNumber())
      return a.as(JsonNumber.class).doubleValue() == b.as(JsonNumber.class).doubleValue();
    if (a.isArray()) {
      JsonArray ar = a.as(JsonArray.class);
      JsonArray br = b.as(JsonArray.class);
      return ar.size() == br.size()
          && ar.indexes().allMatch(i -> eqItems.test(ar.get(i), br.get(i)));
    }
    JsonObject ao = a.asObject();
    JsonObject bo = b.asObject();
    return ao.size() == bo.size()
        && ao.keys().allMatch(key -> eqItems.test(ao.get(key), bo.get(key)));
  }

  /**
   * Compare this value (expected) with the given value (actual) using {@link Mode#DEFAULT}.
   *
   * @since 1.7
   * @param with the JSON to compare this JSON value with. To benefit from annotation specific
   *     handling the value must be "cast" to the root object type using {@link #as(Class)} prior to
   *     calling this method
   * @return the differences
   * @throws JsonPathException in case either of the two values compared is undefined
   */
  @TerminalOp(canBeNull = true)
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
   * @throws JsonPathException in case either of the two values compared is undefined
   */
  @TerminalOp(canBeNull = true)
  default JsonDiff diff(JsonValue with, Mode mode) {
    return JsonDiff.of(this, with, mode);
  }

  /**
   * Access the node in the JSON document. This can be the low level API that is concerned with
   * extraction by path.
   *
   * <p>This might be useful in test to access the {@link JsonNode#getDeclaration()} to modify and
   * reuse it.
   *
   * @return the underlying {@link JsonNode} if it exists
   * @throws JsonPathException in case this value does not exist in the JSON document
   */
  @NotNull
  @TerminalOp(canBeNull = true)
  JsonNode node();

  /**
   * @since 1.9
   */
  @CheckNull
  @TerminalOp(canBeNull = true)
  JsonNode nodeIfExists();

  /**
   * @return JSON declaration for this value (as originally given)
   * @throws JsonPathException if this node does not exist
   * @since 0.11
   */
  @TerminalOp(canBeNull = true)
  default String toJson() {
    return node().getDeclaration().toString();
  }

  /**
   * @return This node in standard JURL notation
   * @since 1.9
   */
  @TerminalOp(canBeNull = true)
  default String toJurl() {
      return toJurl(Jurl.STANDARD);
  }

  /**
   * @return This node in JURL notation with given format rules
   * @since 1.9
   */
  default String toJurl(Jurl.Format format) {
    return JurlBuilder.toJurl(format, node());
  }

  /**
   * @return JSON declaration for this value in a minimized formatting
   * @throws JsonPathException if this node does not exist
   * @since 0.11
   */
  @TerminalOp(canBeNull = true)
  default String toMinimizedJson() {
    if (!isObject() && !isArray()) return toJson();
    if (isObject())
      return JsonBuilder.createObject(
              JsonBuilder.MINIMIZED_FULL,
              obj ->
                  asObject().entries().forEach(e -> obj.addMember(e.getKey(), e.getValue().node())))
          .getDeclaration()
          .toString();
    return JsonBuilder.createArray(
            JsonBuilder.MINIMIZED_FULL,
            arr -> as(JsonArray.class).forEach(e -> arr.addElement(e.node())))
        .getDeclaration()
        .toString();
  }

  /**
   * @return the accessor mappings/factory used
   * @since 1.9
   */
  @NotNull
  JsonAccessors getAccessors();

  /**
   * Finds the first value that satisfies the given test.
   *
   * <p>OBS! When no match is found a value that does not exist is returned.
   *
   * @param type API to test the node with
   * @param test test to perform on all objects that satisfy the type filter
   * @param <T> type of the object to find
   * @return the first found match or JSON {@code null} object
   */
  @TerminalOp(canBeUndefined = true)
  default <T extends JsonValue> T find(Class<T> type, Predicate<T> test) {
    if (isUndefined()) return JsonMixed.of("{}").get("notFound", type);
    JsonAccessors accessors = getAccessors();
    Optional<JsonNode> match =
        node()
            .find(
                node -> {
                  try {
                    return test.test(node.lift(accessors).as(type));
                  } catch (JsonTreeException | JsonPathException ex) {
                    // the test called a method that was not supported by the tested node
                    return false;
                  }
                });
    return match.isEmpty()
        ? JsonMixed.of("{}").get("notFound", type)
        : match.get().lift(accessors).as(type);
  }
}
