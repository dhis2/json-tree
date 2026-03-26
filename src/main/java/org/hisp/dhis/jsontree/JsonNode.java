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

import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.stream.IntStream.range;
import static org.hisp.dhis.jsontree.JsonNodeType.ARRAY;
import static org.hisp.dhis.jsontree.JsonNodeType.BOOLEAN;
import static org.hisp.dhis.jsontree.JsonNodeType.NUMBER;
import static org.hisp.dhis.jsontree.JsonNodeType.OBJECT;
import static org.hisp.dhis.jsontree.JsonNodeType.STRING;
import static org.hisp.dhis.jsontree.JsonTreeException.notA;
import static org.hisp.dhis.jsontree.JsonTreeException.notAContainer;
import static org.hisp.dhis.jsontree.JsonTreeException.notAnObject;

import java.io.IOException;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.hisp.dhis.jsontree.JsonSelector.Matches;
import org.hisp.dhis.jsontree.internal.CheckNull;
import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * API of a JSON tree as it actually exist, parsed lazily on access.
 *
 * <h3>Laziness and Eager Throw</h3>
 *
 * <p>Operations are lazily evaluated to make working with the JSON tree efficient.
 *
 * <p>Trying to use operations that are not supported for the {@link JsonNodeType}, like accessing
 * the {@link #members()} or {@link #elements()} of a {@link JsonNode} that is not an object or
 * array respectively, will throw an {@link JsonTreeException} immediately.
 *
 * <h3>Laziness and Validation</h3>
 *
 * A {@link JsonFormatException} is first encountered when the node containing the error is parsed
 * due to user access. A node is only fully validated when an operation needed to find the {@link
 * #endIndex()}.
 *
 * <p>Likewise, trying to resolve nodes in the tree that do not exist results in a {@link
 * JsonPathException} immediately.
 *
 * @author Jan Bernitt
 */
public interface JsonNode
    extends JsonProbe, JsonSelectable<JsonNode>, Serializable, Textual, Map.Entry<Text, JsonNode> {

  /**
   * Behaviour requested by the caller of how to utilise the internal node cache
   * of a {@link JsonTree}.
   *
   * @since 1.9
   */
  enum Index {
    /**
     * Does neither read the index nor insert created nodes (smallest memory footprint).
     *
     * <p>This is likely the most reasonable choice when using the result in a {@link Stream}, as
     * usually that means the nodes would never be revisited again.
     */
    SKIP,
    /**
     * Checks the index to short circuit finding the node in the raw JSON (starting from parent
     * start) but does not create and insert the node in the index if it does not exist (balance
     * between CPU and memory load).
     */
    CHECK,
    /**
     * Like {@link #CHECK}, except that a node that is not yet indexed, it created and added (use
     * memory to lighten CPU load).
     *
     * <p>This is the most "caching" and useful for excessive "random" access into a big document.
     */
    ADD,

    /**
     * Like {@link #ADD}, but does only index array and object nodes (parents).
     *
     * @implNote This is the standard default as most leaf nodes only see single access and calling
     *     multiple methods on a {@link JsonValue} representing the {@link JsonNode} will have the
     *     node remembered internally after the first lookup.
     */
    AUTO
  }

  /**
   * JSON {@code null} as root of a tree
   *
   * @since 0.6
   */
  JsonNode NULL = JsonNode.of("null");

  /**
   * JSON {@code {}} as a root of a tree
   *
   * @since 0.6
   */
  JsonNode EMPTY_OBJECT = JsonNode.of("{}");

  /**
   * JSON {@code []} as a root of a tree
   *
   * @since 0.6
   */
  JsonNode EMPTY_ARRAY = JsonNode.of("[]");

  /**
   * Allows to observe all individual object member path lookups in the tree. This does not include
   * member iteration of any sort but only lookup by name using {@link JsonNode#member(Text)}.
   *
   * @since 0.10
   */
  @FunctionalInterface
  interface GetListener {

    /**
     * @param path absolute object member path in the tree that is resolved
     */
    void accept(JsonPath path);
  }

  /**
   * Create a new lazily parsed {@link JsonNode} tree.
   *
   * <p>JSON format issues are first encountered when the part of the document is accessed or
   * skipped as part of working with the tree.
   *
   * @param json standard compliant JSON input
   * @return the given JSON input as {@link JsonNode} tree
   * @since 0.4
   */
  static JsonNode of(CharSequence json) {
    return of(json, null);
  }

  /**
   * Create a new lazily parsed {@link JsonNode} tree.
   *
   * @param json standard compliant JSON input
   * @param onGet to observe all path lookup in the returned tree, may be null
   * @return the given JSON input as {@link JsonNode} tree
   * @since 0.10
   */
  static JsonNode of(CharSequence json, GetListener onGet) {
    return of(json, onGet, Index.AUTO);
  }

  static JsonNode of(CharSequence json, GetListener onGet, Index auto) {
    if (json instanceof String s) return JsonTree.of(s.toCharArray(), onGet, auto);
    return JsonTree.of(Text.of(json).toCharArray(), onGet, auto);
  }

  /**
   * @param file a JSON file in UTF-8 encoding
   * @return the given JSON input as {@link JsonNode} tree
   * @since 1.0
   */
  static JsonNode of(Path file) {
    return of(file, null);
  }

  /**
   * @param file a JSON file in UTF-8 encoding
   * @param onGet to observe all path lookup in the returned tree, may be null
   * @return the given JSON input as {@link JsonNode} tree
   * @since 1.0
   */
  static JsonNode of(Path file, GetListener onGet) {
    return of(file, StandardCharsets.UTF_8, onGet);
  }

  /**
   * @param file a JSON file in the given encoding
   * @param encoding of the given file
   * @param onGet to observe all path lookup in the returned tree, may be null
   * @return the given JSON input as {@link JsonNode} tree
   * @implNote not optimized, added to allow transparent change of implementation later
   * @since 1.0
   */
  static JsonNode of(Path file, Charset encoding, GetListener onGet) {
    return of(file, encoding, onGet, Index.AUTO);
  }

  static JsonNode of(Path file, Charset encoding, GetListener onGet, Index auto) {
    try {
      return of(Files.readAllBytes(file), encoding, onGet, auto);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  static JsonNode of(
      byte[] bytes, Charset encoding, @CheckNull GetListener onGet, Index auto) {
    return JsonTree.of(Chars.decode(bytes, encoding), onGet, auto);
  }

  /**
   * @return the type of the node as derived from the node beginning
   */
  @NotNull
  JsonNodeType type();

  /**
   * @return The root of this JSON document independent of which node in the tree it is called on
   * @since 0.6
   */
  @NotNull
  JsonNode getRoot();

  /**
   * In contrast to {@link JsonValue#of(JsonNode)} this method does not result in a new independent
   * virtual tree.
   *
   * <p>In other words this is the reverse method for {@link JsonValue#node()}
   *
   * @return this node as {@link JsonValue} as it is contained in its current tree
   * @since 0.11
   */
  default JsonMixed lift(JsonAccessors accessors) {
    return JsonVirtualTree.lift(this, accessors);
  }

  /**
   * @return The parent of this node or the root itself if this node is the root
   * @since 0.6
   */
  @NotNull
  default JsonNode getParent() {
    return isRoot() ? this : getRoot().get(path().parentPath());
  }

  /**
   * @param name exact name of the object member accessed
   * @return the node at the given path
   * @throws JsonPathException when no such member exists in the in this object node
   * @throws JsonTreeException when the operation is called on a non-object node
   * @since 1.9
   */
  default JsonNode get(Text name) throws JsonPathException, JsonTreeException {
    throw notAnObject(JsonPath.of(name), this, "get(Text)");
  }

  /**
   * @param name exact name of the object member accessed
   * @return the node at the given path or null if no such node exists
   * @throws JsonTreeException when the operation is called on a non-object node
   */
  default JsonNode getIfExists(Text name) throws JsonTreeException {
    throw notAnObject(JsonPath.of(name), this, "getOrNull(Text)");
  }

  /**
   * Access the node at the given path in the subtree of this node.
   *
   * @param path a simple or nested path relative to this node. A path starting with {@code $} is
   *     relative to the root node of this node, in other words it is an absolute path
   * @return the node at the given path
   * @throws JsonPathException when no such node exists in the subtree of this node
   * @throws JsonTreeException when the operation is called on a non-object node
   */
  @NotNull
  default JsonNode get(@NotNull CharSequence path) throws JsonPathException, JsonTreeException {
    if (path.isEmpty()) return this;
    if (path instanceof String p) {
      if ("$".equals(p)) return getRoot();
      if (p.charAt(0) == '$' && p.length() > 1 && JsonPath.isSyntaxIndicator(p.charAt(1)))
        return getRoot().get(p.substring(1));
    }
    return get(JsonPath.of(path));
  }

  /**
   * Access the node at the given path in the subtree of this node.
   *
   * @param path a simple or nested path relative to this node. A path starting with {@code $} is
   *     relative to the root node of this node, in other words it is an absolute path
   * @return the node at the given path or {@code null} if no such node exists
   * @throws JsonPathException when the provided path is malformed
   * @throws JsonTreeException when the operation is called on a non-object node
   * @since 1.5
   */
  @CheckNull
  default JsonNode getIfExists(@NotNull CharSequence path) throws JsonPathException {
    if (path.isEmpty()) return this;
    if (path instanceof String p) {
      if ("$".equals(p)) return getRoot();
      if (p.charAt(0) == '$' && p.length() > 1 && JsonPath.isSyntaxIndicator(p.charAt(1)))
        return getRoot().getIfExists(p.substring(1));
    }
    return getIfExists(JsonPath.of(path));
  }

  /**
   * @param subPath a path understood relative to this node's {@link #path()}
   * @return the node at the given path
   * @throws JsonPathException when no such node exists in the subtree of this node
   * @throws JsonTreeException when the operation is called on a non-object node
   * @since 1.1
   */
  @NotNull
  default JsonNode get(@NotNull JsonPath subPath) throws JsonTreeException, JsonPathException {
    if (subPath.isEmpty()) return this;
    throw notAnObject(subPath, this, "get(JsonPath)");
  }

  /**
   * @param subPath a path understood relative to this node's {@link #path()}
   * @return the node at the given path or {@code null} if no such node exists
   * @throws JsonTreeException when the operation is called on a non-object node
   * @since 1.5
   */
  @CheckNull
  default JsonNode getIfExists(@NotNull JsonPath subPath) throws JsonTreeException {
    if (subPath.isEmpty()) return this;
    throw notAnObject(subPath, this, "getIfExists(JsonPath)");
  }

  /**
   * Size of an array or number of object members.
   *
   * <p>This is preferable to calling {@link #value()} or {@link #members()} or {@link #elements()}
   * when size is only property of interest as it might have better performance.
   *
   * @return number of elements in an array or number of fields in an object, otherwise undefined
   * @throws JsonTreeException when this node in neither an array nor an object
   */
  default int size() {
    throw notAContainer(this, "size()");
  }

  /**
   * @param subPath relative path to check
   * @return true, only if a node exists that the given path relative to this node (this includes a
   *     defined JSON {@code null})
   * @since 1.9
   */
  boolean exists(@NotNull JsonPath subPath);

  /**
   * Whether an array or object has no elements or members.
   *
   * <p>This is preferable to calling {@link #value()} or {@link #members()} or {@link #elements()}
   * when emptiness is only property of interest as it might have better performance.
   *
   * @return true if an array or object has no elements/fields, otherwise undefined
   * @throws JsonTreeException when this node in neither an array nor an object
   */
  default boolean isEmpty() {
    throw notAContainer(this, "isEmpty()");
  }

  /**
   * OBS! Only defined when this node is of type {@link JsonNodeType#OBJECT}).
   *
   * <p>Check for member existence.
   *
   * @param name of the member in this object node
   * @return true, if the member exists, false otherwise
   * @throws JsonTreeException if this is not an object node
   * @since 1.9
   */
  default boolean isMember(@NotNull Text name) {
    throw notA(OBJECT, this, "isMember(Text)");
  }

  /**
   * @see #isMember(Text)
   * @since 0.6
   */
  default boolean isMember(@NotNull CharSequence name) {
    return isMember(Text.of(name));
  }

  /**
   * OBS! Only defined when this node is of type {@link JsonNodeType#ARRAY} or {@link
   * JsonNodeType#OBJECT}).
   *
   * @param index array index greater than 0 and less than {@link #size()}
   * @return true, if this array node as an element at the provided index, false otherwise
   * @throws JsonTreeException if this is not an array node
   */
  default boolean isElement(int index) {
    return index >= 0 && index < size();
  }

  /**
   * The value depends on the {@link #type()}:
   *
   * <ul>
   *   <li>{@link JsonNodeType#NULL} returns {@code null}
   *   <li>{@link JsonNodeType#BOOLEAN} returns {@link #booleanValue()}
   *   <li>{@link JsonNodeType#STRING} returns {@link #textValue()}
   *   <li>{@link JsonNodeType#NUMBER} returns {@link #numberValue()}
   *   <li>{@link JsonNodeType#ARRAY} same as {@link #elements()}
   *   <li>{@link JsonNodeType#OBJECT} returns same as {@link #members()}
   * </ul>
   *
   * @return the nodes value as described in the above table
   */
  default Object value() {
    return switch (type()) {
      case NULL -> null;
      case ARRAY -> elements();
      case OBJECT -> members();
      case STRING -> textValue();
      case NUMBER -> numberValue();
      case BOOLEAN -> booleanValue();
    };
  }

  /**
   * @since 1.9
   * @return the textual representation of the node, available for primitive nodes
   */
  @Override
  default @NotNull Text textValue() {
    throw notA(STRING, this, "textValue()");
  }

  /**
   * @return this boolean (or string) node's value as a boolean
   * @throws JsonTreeException if this node is not a boolean (or string) node
   * @throws IllegalArgumentException if this node is a string node but the boolean could not be
   *     parsed successfully
   * @since 1.9
   */
  default boolean booleanValue() {
    throw notA(BOOLEAN, this, "booleanValue()");
  }

  /**
   * Comparable to {@link Number#intValue()}
   *
   * @return this number (or string) node's value as an int (cast from double if needed)
   * @throws JsonTreeException if this node is not a number (or string) node
   * @throws ArithmeticException if this node is a numeric string node its value is out of int range
   * @throws NumberFormatException if this node is a string node but the number could not be parsed
   *     successfully
   * @since 1.9
   */
  default int intValue() {
    throw notA(NUMBER, this, "intValue()");
  }

  /**
   * Comparable to {@link Number#longValue()}
   *
   * @return this number (or string) node's value as a long (cast from double if needed)
   * @throws JsonTreeException if this node is not a number (or string) node
   * @throws ArithmeticException if this node is a numeric string node its value is out of long range
   * @throws NumberFormatException if this node is a string node but the number could not be parsed
   *     successfully
   * @since 1.9
   */
  default long longValue() {
    throw notA(NUMBER, this, "longValue()");
  }

  /**
   * Comparable to {@link Number#doubleValue()}
   *
   * @return this number (or string) node's value as a double
   * @throws JsonTreeException if this node is not a number (or string) node
   * @throws NumberFormatException if this node is a string node but the number could not be parsed
   *     successfully
   * @since 1.9
   */
  default double doubleValue() {
    throw notA(NUMBER, this, "doubleValue()");
  }

  /**
   * @apiNote If the actual numeric exact value is within int range, an {@link Integer} is returned.
   * If it is within long range, a {@link Long} is returned. Otherwise, the type of {@link Number}
   * is unspecified. In any case it is recommended to use the {@link Number} API methods, not
   * unbox (cast) the result to any specific subtype.
   *
   * @throws NumberFormatException if this node is a string node but the value is not a valid number
   * @return This node value as a number (JSON number or string node)
   * @since 1.9
   */
  default Number numberValue() {
    throw notA(NUMBER, this, "numberValue()");
  }

  /**
   * OBS! Only defined when this node is of type {@link JsonNodeType#OBJECT}).
   *
   * @param name name of the member to access
   * @return the member with the given name
   * @throws JsonPathException when no such member exists
   * @throws JsonTreeException if this node is not an object node that could have members
   * @since 1.9
   */
  @NotNull
  default JsonNode member(Text name) throws JsonPathException {
    throw notA(OBJECT, this, "member(Text)");
  }

  /**
   * OBS! Only defined when this node is of type {@link JsonNodeType#OBJECT}).
   *
   * @param name name of the member to access
   * @return the member with the given name or {@code null} if no such member exists
   * @throws JsonPathException when the path is malformed
   * @throws JsonTreeException if this node is not an object node that could have members
   * @since 1.9
   */
  @CheckNull
  default JsonNode memberIfExists(Text name) throws JsonPathException {
    throw notA(OBJECT, this, "memberIfExists(Text)");
  }

  /**
   * OBS! Only defined when this node is of type {@link JsonNodeType#OBJECT}).
   *
   * <p>The members are iterated in order of declaration in the underlying document.
   *
   * @return this {@link #value()} as a sequence of {@link Entry}s
   * @throws JsonTreeException if this node is not an object node that could have members
   */
  default Streamable<JsonNode> members() {
    throw notA(OBJECT, this, "members()");
  }

  /**
   * OBS! Only defined when this node is of type {@link JsonNodeType#OBJECT}).
   *
   * <p>The keys are iterated in order of declaration in the underlying document.
   *
   * <p>The main reason to use this method over {@link #members()} is that values are not yet parsed
   * into tree nodes if they haven't been parsed already. In that regard this can be more
   * lightweight than using {@link #members()}.
   *
   * @return this {@link #value()} as a sequence of {@link Text} keys
   * @throws JsonTreeException if this node is not an object node that could have members
   * @since 0.11
   */
  default Streamable<Text> keys() {
    throw notA(OBJECT, this, "keys()");
  }

  /**
   * OBS! Only defined when this node is of type {@link JsonNodeType#OBJECT}).
   *
   * @return the absolute paths of the members of this object in order of declaration
   * @throws JsonTreeException if this node is not an object node that could have members
   * @since 1.2
   */
  default Streamable<JsonPath> paths() {
    throw notA(OBJECT, this, "paths()");
  }

  /**
   * OBS! Only defined when this node is of type {@link JsonNodeType#OBJECT}).
   *
   * <p>The members are iterated in order of declaration in the underlying document.
   *
   * @param index the strategy to apply when it comes to node lookup and indexing
   * @return an iterator to lazily process the members one at a time - mostly to avoid materialising
   *     all members in memory for large maps. Member {@link JsonNode}s that already exist
   *     internally will be reused and returned by the iterator.
   * @throws JsonTreeException if this node is not an object node that could have members
   */
  default Streamable<JsonNode> members(Index index) {
    throw notA(OBJECT, this, "members(Index)");
  }

  /**
   * OBS! Only defined when this node is of type {@link JsonNodeType#ARRAY}).
   *
   * @param index index of the element to access
   * @return the node at the given array index
   * @throws JsonPathException when no such element exists
   * @throws JsonTreeException if this node is not an array node that could have elements
   * @since 1.9
   */
  @NotNull
  default JsonNode element(Text index) throws JsonPathException, JsonTreeException {
    throw notA(ARRAY, this, "element(Text)");
  }

  /**
   * @see #element(Text)
   */
  default JsonNode element(int index) throws JsonPathException, JsonTreeException {
    return element(Text.of(index));
  }

  /**
   * OBS! Only defined when this node is of type {@link JsonNodeType#ARRAY}).
   *
   * @param index index of the element to access
   * @return the node at the given array index or {@code null} if no such element exists
   * @throws JsonPathException when the index is negative (invalid)
   * @throws JsonTreeException if this node is not an array node that could have elements
   * @since 1.9
   */
  @CheckNull
  default JsonNode elementIfExists(Text index) throws JsonPathException, JsonTreeException {
    throw notA(ARRAY, this, "elementIfExists(Text)");
  }

  /**
   * @see #elementIfExists(Text)
   * @since 1.5
   */
  default JsonNode elementIfExists(int index) throws JsonPathException, JsonTreeException {
    return elementIfExists(Text.of(index));
  }

  /**
   * OBS! Only defined when this node is of type {@link JsonNodeType#ARRAY}).
   *
   * <p>The elements are iterated in the order declared in the underlying JSON document.
   *
   * @return this {@link #value()} as as {@link Stream}
   * @throws JsonTreeException if this node is not an array node that could have elements
   */
  default Streamable<JsonNode> elements() {
    throw notA(ARRAY, this, "elements()");
  }

  /**
   * OBS! Only defined when this node is of type {@link JsonNodeType#ARRAY}).
   *
   * <p>The elements are iterated in the order declared in the underlying JSON document.
   *
   * @param index the strategy to apply when it comes to node lookup and indexing
   * @return an iterator to lazily process the elements one at a time - mostly to avoid
   *     materialising all elements in memory for large arrays. Member {@link JsonNode}s that
   *     already exist internally will be reused and returned by the iterator.
   * @throws JsonTreeException if this node is not an array node that could have elements
   */
  default Streamable<JsonNode> elements(Index index) {
    throw notA(ARRAY, this, "elements(Index)");
  }

  /**
   * Iterates the raw JSON ignoring existing nodes and the node index. This is the lowest overhead
   * way to iterate values.
   *
   * @return the raw JSON values of the elements of this array (same as their {@link
   *     #getDeclaration()} except for a string it removes the quotes).
   * @throws JsonTreeException if this node is not an array node that could have elements
   * @since 1.9
   */
  default Streamable<Text> values() {
    throw notA(ARRAY, this, "values()");
  }

  /*
  Search API
   */

  @Override
  default void query(JsonSelector selector, Matches<JsonNode> matches) {
    selector.match(this, matches);
  }

  /*
   * API about this node in the context of the underlying JSON document
   */

  /**
   * @return path within the overall content this node represents
   * @since 1.1 (with {@link JsonPath} type)
   * @since 1.9 (name changed to {@code path()})
   */
  JsonPath path();

  /**
   * @return the plain JSON of this node as defined in the overall content
   */
  Text getDeclaration();

  /**
   * @return offset or index in the overall content where this node starts (inclusive, points to
   *     first index that belongs to the node). For example, for an object node this is the position
   *     of the opening curly bracket.
   */
  int startIndex();

  /**
   * @return offset or index in the overall content where this node ends (exclusive, points to first
   *     index after the node). For example, for an object node this is the position directly after
   *     the closing curly bracket.
   */
  int endIndex();

  /*
   * API about using this node to create new trees.
   *
   * All such operations always leave the input JsonNodes unchanged and produce new independent trees.
   */

  /**
   * @return This node as a new independent JSON document where this node is the new root of that
   *     document.
   */
  default JsonNode extract() {
    return isRoot() ? this : of(getDeclaration());
  }

  /**
   * Replace this node and returns the root of a new tree where this node got replaced.
   *
   * @param json The JSON used instead of the on this node represents. <br>
   *     <b>Note</b> that the provided JSON is not check to be valid JSON immediately.
   * @return A new document root where this node got replaced with the provided JSON
   */
  JsonNode replaceWith(CharSequence json);

  /**
   * @see #replaceWith(CharSequence)
   * @since 0.6
   */
  default JsonNode replaceWith(JsonNode node) {
    return isRoot() ? node : replaceWith(node.getDeclaration());
  }

  /**
   * @see #replaceWith(CharSequence)
   * @since 0.6
   */
  default JsonNode replaceWith(CharSequence path, JsonNode node) {
    return get(path).replaceWith(node);
  }

  /**
   * Adds a property to this node assuming this node represents a {@link JsonNodeType#OBJECT}.
   *
   * @param name a JSON object property name
   * @param json a JSON value. The value provided is assumed to be valid JSON. To avoid adding
   *     malformed JSON prefer use of {@link #addMembers(Consumer)}.
   * @return a new tree where this node member of the provided name is either added or replaced with
   *     the provided value
   * @throws JsonTreeException when the operation is applied to a non object node
   * @deprecated Avoid use as the provided json is not guaranteed to be valid JSON
   */
  @Deprecated(since = "0.6.0")
  default JsonNode addMember(CharSequence name, CharSequence json) {
    return addMembers(obj -> obj.addMember(name, JsonNode.of(json)));
  }

  /**
   * @see #addMembers(JsonNode)
   * @since 0.6
   */
  default JsonNode addMembers(Consumer<JsonBuilder.JsonObjectBuilder> obj) {
    return addMembers(JsonBuilder.createObject(obj));
  }

  /**
   * @see #addMembers(JsonNode)
   * @since 0.6
   */
  default JsonNode addMembers(CharSequence path, Consumer<JsonBuilder.JsonObjectBuilder> obj) {
    return get(path).addMembers(obj);
  }

  /**
   * @see #addMembers(JsonNode)
   * @since 0.6
   */
  default JsonNode addMembers(CharSequence path, JsonNode obj) {
    return get(path).addMembers(obj);
  }

  /**
   * Merges this object node with the provided object node.
   *
   * <p>Members keep their order, first all members of this node, then all members of the provided
   * node.
   *
   * <p>If a member is present in both nodes the member of the provided argument overrides the
   * member in this node. There is no guarantee which of the two possible positions in the order the
   * resulting override node has.
   *
   * @param obj another object node
   * @return A new tree where this node is replaced with an object node which has all members of
   *     this object node and the provided object node
   * @throws JsonTreeException if either this or the provided node aren't object nodes
   * @since 0.6
   */
  default JsonNode addMembers(JsonNode obj) {
    checkType(JsonNodeType.OBJECT, type(), "addMembers");
    checkType(JsonNodeType.OBJECT, obj.type(), "addMembers");
    if (obj.isEmpty()) return getRoot();
    if (isEmpty() && isRoot()) return obj;
    return replaceWith(
        JsonBuilder.createObject(
            merged -> {
              for (Entry<Text, JsonNode> member : members())
                if (!obj.isMember(member.getKey()))
                  merged.addMember(member.getKey(), member.getValue());
              for (Entry<Text, JsonNode> member : obj.members())
                merged.addMember(member.getKey(), member.getValue());
            }));
  }

  /**
   * @see #removeMembers(Set)
   * @since 0.6
   */
  default JsonNode removeMembers(CharSequence path, Set<String> names) {
    return get(path).removeMembers(names);
  }

  /**
   * Removes the given set of members from this node should they exist.
   *
   * @param names names of the members to remove
   * @return A new tree where this node is stripped of any members whose name is in the provided set
   *     of names
   * @throws JsonTreeException if this node is not an object node
   */
  default JsonNode removeMembers(Set<? extends CharSequence> names) {
    checkType(JsonNodeType.OBJECT, type(), "removeMembers");
    if (isEmpty() || names.isEmpty()) return getRoot();
    return replaceWith(
        JsonBuilder.createObject(
            obj -> {
              for (Entry<Text, JsonNode> member : members())
                if (names.stream().noneMatch(name -> member.getKey().contentEquals(name)))
                  obj.addMember(member.getKey(), member.getValue());
            }));
  }

  /**
   * @see #addElements(JsonNode)
   * @since 0.6
   */
  default JsonNode addElements(Consumer<JsonBuilder.JsonArrayBuilder> array) {
    return addElements(JsonBuilder.createArray(array));
  }

  /**
   * @see #addElements(JsonNode)
   * @since 0.6
   */
  default JsonNode addElements(CharSequence path, Consumer<JsonBuilder.JsonArrayBuilder> array) {
    return get(path).addElements(array);
  }

  /**
   * @see #addElements(JsonNode)
   * @since 0.6
   */
  default JsonNode addElements(CharSequence path, JsonNode array) {
    return get(path).addElements(array);
  }

  /**
   * Appends the provided array node's elements to this array node.
   *
   * @param array another array node whose elements to append to this array node
   * @return A new tree where this node is replaced with an array node which has all elements of
   *     this array node and the provided array node
   * @throws JsonTreeException if either this or the provided node aren't array nodes
   * @since 0.6
   */
  default JsonNode addElements(JsonNode array) {
    checkType(JsonNodeType.ARRAY, type(), "addElements");
    checkType(JsonNodeType.ARRAY, array.type(), "addElements");
    if (array.isEmpty()) return getRoot();
    if (isEmpty() && isRoot()) return array;
    return replaceWith(
        JsonBuilder.createArray(
            merged -> {
              merged.addElements(elements(), JsonBuilder.JsonArrayBuilder::addElement);
              merged.addElements(array.elements(), JsonBuilder.JsonArrayBuilder::addElement);
            }));
  }

  /**
   * @see #putElements(int, JsonNode)
   * @since 0.6
   */
  default JsonNode putElements(int index, Consumer<JsonBuilder.JsonArrayBuilder> array) {
    return putElements(index, JsonBuilder.createArray(array));
  }

  /**
   * Inserts the provided array node's elements into this array node at the provided index.
   *
   * @param index at which to insert the elements
   * @param array the array with the elements to insert
   * @return A new tree where this node is replaced with an array node which has all elements of the
   *     provided array inserted into its own elements at the provided index
   * @throws JsonTreeException if either this or the provided node aren't array nodes
   */
  default JsonNode putElements(int index, JsonNode array) {
    checkType(JsonNodeType.ARRAY, type(), "putElements");
    checkType(JsonNodeType.ARRAY, array.type(), "putElements");
    if (array.isEmpty()) return getRoot();
    return replaceWith(
        JsonBuilder.createArray(
            merged -> {
              int size = size();
              if (index > 0 && size > 0)
                range(0, min(index, size)).forEach(i -> merged.addElement(element(i)));
              if (index > size) range(size, index).forEach(i -> merged.addElement(JsonNode.NULL));
              array.elements().forEach(merged::addElement);
              if (size > index) range(index, size).forEach(i -> merged.addElement(element(i)));
            }));
  }

  default JsonNode removeElements(int from) {
    return removeElements(from, Integer.MAX_VALUE);
  }

  default JsonNode removeElements(int from, int to) {
    checkType(JsonNodeType.ARRAY, type(), "removeElements");
    if (isEmpty()) return getRoot();
    int size = size();
    if (from >= size) return getRoot();
    return replaceWith(
        JsonBuilder.createArray(
            array -> {
              if (from > 0) range(0, from).forEach(i -> array.addElement(element(i)));
              if (to < size) range(to, size).forEach(i -> array.addElement(element(i)));
            }));
  }

  private void checkType(JsonNodeType expected, JsonNodeType actual, String operation) {
    if (actual != expected)
      throw new JsonTreeException(
          format("`%s` only allowed for %s but was: %s", operation, expected, actual));
  }
}
