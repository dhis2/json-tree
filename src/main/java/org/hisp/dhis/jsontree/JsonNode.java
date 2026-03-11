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

import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.hisp.dhis.jsontree.internal.Maybe;
import org.hisp.dhis.jsontree.internal.Surly;

/**
 * API of a JSON tree as it actually exist in an HTTP response with a JSON payload.
 * <p>
 * Operations are lazily evaluated to make working with the JSON tree efficient.
 * <p>
 * Trying to use operations that are not supported for the {@link JsonNodeType}, like accessing the {@link #members()}
 * or {@link #elements()} of a {@link JsonNode} that is not an object or array respectively, will throw an
 * {@link JsonTreeException} immediately.
 * <p>
 * Likewise, trying to resolve nodes in the tree that do not exist results in a {@link JsonPathException} immediately.
 *
 * @author Jan Bernitt
 */
public interface JsonNode extends Serializable {

    /**
     * JSON {@code null} as root of a tree
     *
     * @since 0.6
     */
    JsonNode NULL = JsonNode.of( "null" );

    /**
     * JSON {@code {}} as a root of a tree
     *
     * @since 0.6
     */
    JsonNode EMPTY_OBJECT = JsonNode.of( "{}" );

    /**
     * JSON {@code []} as a root of a tree
     *
     * @since 0.6
     */
    JsonNode EMPTY_ARRAY = JsonNode.of( "[]" );

    /**
     * Allows to observe all object member path lookups in the tree.
     *
     * @since 0.10
     */
    @FunctionalInterface
    interface GetListener {

        /**
         * @param path absolute object member path in the tree that is resolved
         */
        void accept( JsonPath path );
    }

    /**
     * Create a new lazily parsed {@link JsonNode} tree.
     * <p>
     * JSON format issues are first encountered when the part of the document is accessed or skipped as part of working
     * with the tree.
     *
     * @param json standard compliant JSON input
     * @return the given JSON input as {@link JsonNode} tree
     * @since 0.4
     */
    static JsonNode of( CharSequence json ) {
        return of( json, null );
    }

    /**
     * Create a new lazily parsed {@link JsonNode} tree.
     * <p>
     *
     * @param json JSON input
     * @return the given JSON input as {@link JsonNode} tree
     * @since 0.10
     */
    static JsonNode ofNonStandard( String json ) {
        return JsonTree.ofNonStandard( json );
    }

    /**
     * Creates a new lazily parsed {@link JsonNode} tree from special URL object notation.
     * <p>
     * Note that the {@link JsonNode}'s {@link JsonNode#getDeclaration()} will be the equivalent JSON, not the original
     * URL notation.
     *
     * @param juon a value in URL notation
     * @return the given URL notation input as {@link JsonNode}
     * @since 1.3
     */
    static JsonNode ofUrlObjectNotation(String juon) {
        return of( Juon.toJson( juon ));
    }

    /**
     * Create a new lazily parsed {@link JsonNode} tree.
     *
     * @param json  standard compliant JSON input
     * @param onGet to observe all path lookup in the returned tree, may be null
     * @return the given JSON input as {@link JsonNode} tree
     * @since 0.10
     */
    static JsonNode of( CharSequence json, GetListener onGet ) {
        return JsonTree.of( json, onGet );
    }

    /**
     * @param file a JSON file in UTF-8 encoding
     * @return the given JSON input as {@link JsonNode} tree
     * @since 1.0
     */
    static JsonNode of( Path file ) {
        return of(file, null);
    }

    /**
     * @param file a JSON file in UTF-8 encoding
     * @param onGet to observe all path lookup in the returned tree, may be null
     * @return the given JSON input as {@link JsonNode} tree
     * @since 1.0
     */
    static JsonNode of( Path file, GetListener onGet ) {
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
    static JsonNode of( Path file, Charset encoding, GetListener onGet ) {
        return JsonTree.of( file, encoding, onGet );
    }

    /**
     * @return the type of the node as derived from the node beginning
     */
    JsonNodeType getType();

    /**
     * @return The root of this JSON document independent of which node in the tree it is called on
     * @since 0.6
     */
    JsonNode getRoot();

    /**
     * In contrast to {@link JsonValue#of(JsonNode)} this method does not result in a new independent virtual tree.
     * <p>
     * In other words this is the reverse method for {@link JsonValue#node()}
     *
     * @return this node as {@link JsonValue} as it is contained in its current tree
     * @since 0.11
     */
    default JsonValue lift( JsonAccessors accessors ) {
        JsonVirtualTree root = new JsonVirtualTree( getRoot(), accessors );
        return isRoot() ? root : root.get( getPath().toString() );
    }

    /**
     * @return The parent of this node or the root itself if this node is the root
     * @since 0.6
     */
    @Surly
    default JsonNode getParent() {
        return isRoot() ? this : getRoot().get( getPath().parentPath().toString() );
    }

    /**
     *
     * @param name exact name of the object member accessed
     * @return the node at the given path
     * @throws JsonPathException when no such member exists in the in this object node
     * @throws JsonTreeException when the operation is called on a non-object node
     * @since 1.9
     */
    default JsonNode get(Text name) throws JsonPathException, JsonTreeException {
        JsonPath path = getPath().chain( name );
        throw new JsonTreeException(getType() + " node at path `"+getPath()+"` is not an object, no member at path: " + path);
    }

    /**
     *
     * @param name exact name of the object member accessed
     * @return the node at the given path or null if no such node exists
     * @throws JsonTreeException when the operation is called on a non-object node
     */
    default JsonNode getOrNull(Text name) throws JsonTreeException {
        JsonPath path = getPath().chain( name );
        throw new JsonTreeException(getType() + " node at path `"+getPath()+"` is not an object, no member at path: " + path);
    }

    /**
     * Access the node at the given path in the subtree of this node.
     *
     * @param path a simple or nested path relative to this node. A path starting with {@code $} is relative to the root
     *             node of this node, in other words it is an absolute path
     * @return the node at the given path
     * @throws JsonPathException when no such node exists in the subtree of this node
     * @throws JsonTreeException when the operation is called on a non-object node
     */
    @Surly
    default JsonNode get(@Surly CharSequence path) throws JsonPathException, JsonTreeException {
        if ( path.isEmpty() ) return this;
        if (path instanceof String p) {
            if ("$".equals(p)) return getRoot();
            if (p.charAt(0) == '$' && p.length() > 1 && JsonPath.isSyntaxIndicator(p.charAt(1)))
                return getRoot().get(p.substring(1));
        }
        return get( JsonPath.of( path ) );
    }

    /**
     * Access the node at the given path in the subtree of this node.
     *
     * @param path a simple or nested path relative to this node. A path starting with {@code $} is relative to the root
     *             node of this node, in other words it is an absolute path
     * @return the node at the given path or {@code null} if no such node exists
     * @throws JsonPathException when the provided path is malformed
     * @throws JsonTreeException when the operation is called on a non-object node
     * @since 1.5
     */
    @Maybe
    default JsonNode getOrNull(@Surly CharSequence path ) throws JsonPathException {
        if ( path.isEmpty() ) return this;
        if (path instanceof String p) {
            if ("$".equals(p)) return getRoot();
            if (p.charAt(0) == '$' && p.length() > 1 && JsonPath.isSyntaxIndicator(p.charAt(1)))
                return getRoot().getOrNull(p.substring(1));
        }
        return getOrNull( JsonPath.of( path ) );
    }

    /**
     * @param subPath a path understood relative to this node's {@link #getPath()}
     * @return the node at the given path
     * @throws JsonPathException when no such node exists in the subtree of this node
     * @throws JsonTreeException when the operation is called on a non-object node
     * @since 1.1
     */
    @Surly
    default JsonNode get(@Surly JsonPath subPath) throws JsonTreeException, JsonPathException {
        if (subPath.isEmpty()) return this;
        JsonPath path = getPath().concat( subPath );
        throw new JsonTreeException(getType() + " node is not an object, no member at path: " + path);
    }

    /**
     * @param subPath a path understood relative to this node's {@link #getPath()}
     * @return the node at the given path or {@code null} if no such node exists
     * @throws JsonTreeException when the operation is called on a non-object node
     * @since 1.5
     */
    @Maybe
    default JsonNode getOrNull(@Surly JsonPath subPath) throws JsonTreeException {
        if (subPath.isEmpty()) return this;
        JsonPath path = getPath().concat( subPath );
        throw new JsonTreeException(getType() + " node is not an object, no member at path: " + path);
    }

    /**
     * Size of an array or number of object members.
     * <p>
     * This is preferable to calling {@link #value()} or {@link #members()} or {@link #elements()} when size is only
     * property of interest as it might have better performance.
     *
     * @return number of elements in an array or number of fields in an object, otherwise undefined
     * @throws JsonTreeException when this node in neither an array nor an object
     */
    default int size() {
        throw new JsonTreeException( getType() + " node has no size property." );
    }

    /**
     * @param subPath relative path to check
     * @return true, only if a node exists that the given path relative to this node (this includes a
     *     defined JSON {@code null})
     * @since 1.9
     */
    boolean exists(JsonPath subPath);

    /**
     * Whether an array or object has no elements or members.
     * <p>
     * This is preferable to calling {@link #value()} or {@link #members()} or {@link #elements()} when emptiness is
     * only property of interest as it might have better performance.
     *
     * @return true if an array or object has no elements/fields, otherwise undefined
     * @throws JsonTreeException when this node in neither an array nor an object
     */
    default boolean isEmpty() {
        throw new JsonTreeException( getType() + " node has no empty property." );
    }

    /**
     * @return true if this node is the root of the tree, false otherwise
     * @since 0.6
     */
    default boolean isRoot() {
        return getPath().isEmpty();
    }

    /**
     * OBS! Only defined when this node is of type {@link JsonNodeType#OBJECT}).
     * <p>
     * Check for member existence.
     *
     * @param name of the member in this object node
     * @return true, if the member exists, false otherwise
     * @throws JsonTreeException if this is not an object node
     * @since 1.9
     */
    default boolean isMember( Text name ) {
        return StreamSupport.stream( keys().spliterator(), false)
            .anyMatch( name::contentEquals );
    }

    /**
     * @see #isMember(Text)
     * @since 0.6
     */
    default boolean isMember( CharSequence name ) {
        return isMember( Text.of( name ) );
    }

    /**
     * OBS! Only defined when this node is of type {@link JsonNodeType#ARRAY} or {@link JsonNodeType#OBJECT}).
     *
     * @param index array index greater than 0 and less than {@link #size()}
     * @return true, if this array node as an element at the provided index, false otherwise
     * @throws JsonTreeException if this is not an array node
     */
    default boolean isElement( int index ) {
        return index >= 0 && index < size();
    }

    /**
     * The value depends on the {@link #getType()}:
     * <ul>
     * <li>{@link JsonNodeType#NULL} returns {@code null}</li>
     * <li>{@link JsonNodeType#BOOLEAN} returns {@link Boolean}</li>
     * <li>{@link JsonNodeType#STRING} returns {@link Text}</li>
     * <li>{@link JsonNodeType#NUMBER} returns either {@link Integer},
     * {@link Long} or {@link Double} (smallest/simplest possible)</li>
     * <li>{@link JsonNodeType#ARRAY} same as {@link #elements()}</li>
     * <li>{@link JsonNodeType#OBJECT} returns same as {@link #members()}</li>
     * </ul>
     *
     * @return the nodes value as described in the above table
     */
    Object value();

    /**
     * OBS! Only defined when this node is of type {@link JsonNodeType#OBJECT}).
     *
     * @param name name of the member to access
     * @return the member with the given name
     * @throws JsonPathException when no such member exists
     * @throws JsonTreeException if this node is not an object node that could have members
     * @since 1.9
     */
    @Surly
    default JsonNode member( Text name ) throws JsonPathException {
        throw new JsonTreeException( getType() + " node has no member property: " + name );
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
    @Maybe
    default JsonNode memberOrNull( Text name ) throws JsonPathException {
        throw new JsonTreeException( getType() + " node has no member property: " + name );
    }

    /**
     * OBS! Only defined when this node is of type {@link JsonNodeType#OBJECT}).
     * <p>
     * The members are iterated in order of declaration in the underlying document.
     *
     * @return this {@link #value()} as a sequence of {@link Entry}s
     * @throws JsonTreeException if this node is not an object node that could have members
     */
    default Iterable<Entry<Text, JsonNode>> members() {
        throw new JsonTreeException( getType() + " node has no members property." );
    }

    /**
     * OBS! Only defined when this node is of type {@link JsonNodeType#OBJECT}).
     * <p>
     * The keys are iterated in order of declaration in the underlying document.
     * <p>
     * The main reason to use this method over {@link #members()} is that values are not yet parsed into tree nodes if
     * they haven't been parsed already. In that regard this can be more lightweight than using {@link #members()}.
     *
     * @return this {@link #value()} as a sequence of {@link Text} keys
     * @throws JsonTreeException if this node is not an object node that could have members
     * @since 0.11
     */
    default Iterable<Text> keys() {
        throw new JsonTreeException( getType() + " node has no keys property." );
    }

    /**
     * OBS! Only defined when this node is of type {@link JsonNodeType#OBJECT}).
     *
     * @return the absolute paths of the members of this object in order of declaration
     * @throws JsonTreeException if this node is not an object node that could have members
     * @since 1.2
     */
    default Iterable<JsonPath> paths() {
        throw new JsonTreeException( getType() + " node has no paths property." );
    }

    /**
     * OBS! Only defined when this node is of type {@link JsonNodeType#OBJECT}).
     * <p>
     * The names are iterated in order of declaration in the underlying document.
     *
     * @return the raw property names of this object node as they occur in the JSON document
     * @throws JsonTreeException if this node is not an object node that could have members
     * @since 1.1
     */
    default Iterable<String> names() {
        throw new JsonTreeException( getType() + " node has no names property." );
    }

    /**
     * OBS! Only defined when this node is of type {@link JsonNodeType#OBJECT}).
     * <p>
     * The members are iterated in order of declaration in the underlying document.
     *
     * @param cacheNodes true, to internally "remember" the members iterated over so far, false to only iterate without
     *                   keeping references to them further on so GC can pick em up
     * @return an iterator to lazily process the members one at a time - mostly to avoid materialising all members in
     * memory for large maps. Member {@link JsonNode}s that already exist internally will be reused and returned by the
     * iterator.
     * @throws JsonTreeException if this node is not an object node that could have members
     */
    default Iterator<Entry<Text, JsonNode>> members( boolean cacheNodes ) {
        throw new JsonTreeException( getType() + " node has no members property." );
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
    @Surly
    default JsonNode element( Text index ) throws JsonPathException, JsonTreeException {
        throw new JsonTreeException( getType() + " node has no elements to access at index: " + index );
    }

    /**
     * @see #element(Text)
     */
    default JsonNode element( int index ) throws JsonPathException, JsonTreeException {
        return element( Text.of( index ) );
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
    @Maybe
    default JsonNode elementOrNull( Text index ) throws JsonPathException, JsonTreeException {
        throw new JsonTreeException( getType() + " node has no elements to access as index: " + index );
    }

    /**
     * @see #elementOrNull(Text)
     * @since 1.5
     */
    default JsonNode elementOrNull( int index ) throws JsonPathException, JsonTreeException {
        return elementOrNull( Text.of( index ) );
    }

    /**
     * OBS! Only defined when this node is of type {@link JsonNodeType#ARRAY}).
     * <p>
     * The elements are iterated in the order declared in the underlying JSON document.
     *
     * @return this {@link #value()} as as {@link Stream}
     * @throws JsonTreeException if this node is not an array node that could have elements
     */
    default Iterable<JsonNode> elements() {
        throw new JsonTreeException( getType() + " node has no elements property." );
    }

    /**
     * OBS! Only defined when this node is of type {@link JsonNodeType#ARRAY}).
     * <p>
     * The elements are iterated in the order declared in the underlying JSON document.
     *
     * @param cacheNodes true, to internally "remember" the members iterated over so far, false to only iterate without
     *                   keeping references to them further on so GC can pick em up
     * @return an iterator to lazily process the elements one at a time - mostly to avoid materialising all elements in
     * memory for large arrays. Member {@link JsonNode}s that already exist internally will be reused and returned by
     * the iterator.
     * @throws JsonTreeException if this node is not an array node that could have elements
     */
    default Iterator<JsonNode> elements( boolean cacheNodes ) {
        throw new JsonTreeException( getType() + " node has no elements property." );
    }

    /**
     * Visit subtree of this node including this node.
     *
     * @param type    type of nodes to visitor accepts
     * @param visitor consumes all nodes in the subtree of this node (including this node) that are of the provided type
     *                in root first order.
     */
    void visit( JsonNodeType type, Consumer<JsonNode> visitor );

    /**
     * Visit subtree of this node including this node.
     *
     * @param visitor consumes all nodes in the subtree of this node (including this node)
     */
    default void visit( Consumer<JsonNode> visitor ) {
        visit( null, visitor );
    }

    /**
     * Searches for a node in this subtree that satisfies the provided test.
     *
     * @param test a test that returns true when node is found
     * @return the first node found or empty
     * @since 0.11
     */
    default Optional<JsonNode> find( Predicate<JsonNode> test ) {
        return find( null, test );
    }

    /**
     * Searches for a node in this subtree that matches type and satisfies the provided test.
     *
     * @param type node type tested, maybe null to search for any type of node
     * @param test a test that returns true when node is found
     * @return the first node found or empty
     */
    default Optional<JsonNode> find( JsonNodeType type, Predicate<JsonNode> test ) {
        throw new JsonTreeException( getType() + " node does not support find." );
    }

    /**
     * Count matching nodes in a subtree of this node including this node.
     *
     * @param type    type of node to passed to the visitor
     * @param visitor a {@link Predicate} returning true, to count the provided node, false to not count it.
     * @return total number of nodes in the subtree of this node for which the visitor returned true
     */
    default int count( JsonNodeType type, Predicate<JsonNode> visitor ) {
        AtomicInteger count = new AtomicInteger();
        visit( type, node -> {
            if ( visitor.test( node ) ) {
                count.incrementAndGet();
            }
        } );
        return count.get();
    }

    /**
     * Returns the number of notes of the given type in the entire subtree including this node.
     *
     * @param type type of node to count
     * @return number of nodes in subtree
     */
    default int count( JsonNodeType type ) {
        return count( type, node -> true );
    }

    /*
     * API about this node in the context of the underlying JSON document
     */

    /**
     * @return path within the overall content this node represents
     * @since 1.1 (with {@link JsonPath} type)
     */
    JsonPath getPath();

    /**
     * @return the plain JSON of this node as defined in the overall content
     */
    Text getDeclaration();

    /**
     * @return offset or index in the overall content where this node starts (inclusive, points to first index that
     * belongs to the node). For example, for an object node this is the position of the opening curly bracket.
     */
    int startIndex();

    /**
     * @return offset or index in the overall content where this node ends (exclusive, points to first index after the
     * node). For example, for an object node this is the position directly after the closing curly bracket.
     */
    int endIndex();

    /*
     * API about using this node to create new trees.
     *
     * All such operations always leave the input JsonNodes unchanged and produce new independent trees.
     */

    /**
     * @return This node as a new independent JSON document where this node is the new root of that document.
     */
    default JsonNode extract() {
        return isRoot() ? this : of( getDeclaration() );
    }

    /**
     * Replace this node and returns the root of a new tree where this node got replaced.
     *
     * @param json The JSON used instead of the on this node represents. <br/><b>Note</b> that the provided JSON is not
     *             check to be valid JSON immediately.
     * @return A new document root where this node got replaced with the provided JSON
     */
    JsonNode replaceWith( CharSequence json );

    /**
     * @see #replaceWith(CharSequence)
     * @since 0.6
     */
    default JsonNode replaceWith( JsonNode node ) {
        return isRoot() ? node : replaceWith( node.getDeclaration() );
    }

    /**
     * @see #replaceWith(CharSequence)
     * @since 0.6
     */
    default JsonNode replaceWith( CharSequence path, JsonNode node ) {
        return get( path ).replaceWith( node );
    }

    /**
     * Adds a property to this node assuming this node represents a {@link JsonNodeType#OBJECT}.
     *
     * @param name a JSON object property name
     * @param json a JSON value. The value provided is assumed to be valid JSON. To avoid adding malformed JSON prefer
     *             use of {@link #addMembers(Consumer)}.
     * @return a new tree where this node member of the provided name is either added or replaced with the provided
     * value
     * @throws JsonTreeException when the operation is applied to a non object node
     * @deprecated Avoid use as the provided json is not guaranteed to be valid JSON
     */
    @Deprecated( since = "0.6.0" )
    default JsonNode addMember( CharSequence name, CharSequence json ) {
        return addMembers( obj -> obj.addMember( name, JsonNode.of( json ) ));
    }

    /**
     * @see #addMembers(JsonNode)
     * @since 0.6
     */
    default JsonNode addMembers( Consumer<JsonBuilder.JsonObjectBuilder> obj ) {
        return addMembers( JsonBuilder.createObject( obj ) );
    }

    /**
     * @see #addMembers(JsonNode)
     * @since 0.6
     */
    default JsonNode addMembers( CharSequence path, Consumer<JsonBuilder.JsonObjectBuilder> obj ) {
        return get( path ).addMembers( obj );
    }

    /**
     * @see #addMembers(JsonNode)
     * @since 0.6
     */
    default JsonNode addMembers( CharSequence path, JsonNode obj ) {
        return get( path ).addMembers( obj );
    }

    /**
     * Merges this object node with the provided object node.
     * <p>
     * Members keep their order, first all members of this node, then all members of the provided node.
     * <p>
     * If a member is present in both nodes the member of the provided argument overrides the member in this node. There
     * is no guarantee which of the two possible positions in the order the resulting override node has.
     *
     * @param obj another object node
     * @return A new tree where this node is replaced with an object node which has all members of this object node and
     * the provided object node
     * @throws JsonTreeException if either this or the provided node aren't object nodes
     * @since 0.6
     */
    default JsonNode addMembers( JsonNode obj ) {
        checkType( JsonNodeType.OBJECT, getType(), "addMembers" );
        checkType( JsonNodeType.OBJECT, obj.getType(), "addMembers" );
        if ( obj.isEmpty() ) return getRoot();
        if ( isEmpty() && isRoot() ) return obj;
        return replaceWith(
            JsonBuilder.createObject(
                merged -> {
                    for (Entry<Text, JsonNode> member : members())
                        if (!obj.isMember( member.getKey() ))
                            merged.addMember( member.getKey(), member.getValue() );
                    for (Entry<Text, JsonNode> member : obj.members())
                        merged.addMember( member.getKey(), member.getValue() );
                }));
    }

    /**
     * @see #removeMembers(Set)
     * @since 0.6
     */
    default JsonNode removeMembers( CharSequence path, Set<String> names ) {
        return get( path ).removeMembers( names );
    }

    /**
     * Removes the given set of members from this node should they exist.
     *
     * @param names names of the members to remove
     * @return A new tree where this node is stripped of any members whose name is in the provided set of names
     * @throws JsonTreeException if this node is not an object node
     */
    default JsonNode removeMembers( Set<? extends CharSequence> names ) {
        checkType( JsonNodeType.OBJECT, getType(), "removeMembers" );
        if ( isEmpty() || names.isEmpty() ) return getRoot();
        return replaceWith( JsonBuilder.createObject( obj -> {
            for (Entry<Text, JsonNode> member : members())
                if ( names.stream().noneMatch( name -> member.getKey().contentEquals( name ) ) )
                    obj.addMember( member.getKey(), member.getValue() );
        }));
    }

    /**
     * @see #addElements(JsonNode)
     * @since 0.6
     */
    default JsonNode addElements( Consumer<JsonBuilder.JsonArrayBuilder> array ) {
        return addElements( JsonBuilder.createArray( array ) );
    }

    /**
     * @see #addElements(JsonNode)
     * @since 0.6
     */
    default JsonNode addElements( CharSequence path, Consumer<JsonBuilder.JsonArrayBuilder> array ) {
        return get( path ).addElements( array );
    }

    /**
     * @see #addElements(JsonNode)
     * @since 0.6
     */
    default JsonNode addElements( CharSequence path, JsonNode array ) {
        return get( path ).addElements( array );
    }

    /**
     * Appends the provided array node's elements to this array node.
     *
     * @param array another array node whose elements to append to this array node
     * @return A new tree where this node is replaced with an array node which has all elements of this array node and
     * the provided array node
     * @throws JsonTreeException if either this or the provided node aren't array nodes
     * @since 0.6
     */
    default JsonNode addElements( JsonNode array ) {
        checkType( JsonNodeType.ARRAY, getType(), "addElements" );
        checkType( JsonNodeType.ARRAY, array.getType(), "addElements" );
        if ( array.isEmpty() ) return getRoot();
        if ( isEmpty() && isRoot() ) return array;
        return replaceWith( JsonBuilder.createArray( merged -> {
            merged.addElements( elements(), JsonBuilder.JsonArrayBuilder::addElement );
            merged.addElements( array.elements(), JsonBuilder.JsonArrayBuilder::addElement );
        } ) );
    }

    /**
     * @see #putElements(int, JsonNode)
     * @since 0.6
     */
    default JsonNode putElements( int index, Consumer<JsonBuilder.JsonArrayBuilder> array ) {
        return putElements( index, JsonBuilder.createArray( array ) );
    }

    /**
     * Inserts the provided array node's elements into this array node at the provided index.
     *
     * @param index at which to insert the elements
     * @param array the array with the elements to insert
     * @return A new tree where this node is replaced with an array node which has all elements of the provided array
     * inserted into its own elements at the provided index
     * @throws JsonTreeException if either this or the provided node aren't array nodes
     */
    default JsonNode putElements( int index, JsonNode array ) {
        checkType( JsonNodeType.ARRAY, getType(), "putElements" );
        checkType( JsonNodeType.ARRAY, array.getType(), "putElements" );
        if ( array.isEmpty() ) return getRoot();
        return replaceWith( JsonBuilder.createArray( merged -> {
            int size = size();
            if ( index > 0 && size > 0 )
                range( 0, min( index, size ) ).forEach( i -> merged.addElement( element( i ) ) );
            if ( index > size )
                range( size, index ).forEach( i -> merged.addElement( JsonNode.NULL ) );
            array.elements().forEach( merged::addElement );
            if ( size > index )
                range( index, size ).forEach( i -> merged.addElement( element( i ) ) );
        } ) );
    }

    default JsonNode removeElements( int from ) {
        return removeElements( from, Integer.MAX_VALUE );
    }

    default JsonNode removeElements( int from, int to ) {
        checkType( JsonNodeType.ARRAY, getType(), "removeElements" );
        if ( isEmpty() ) return getRoot();
        int size = size();
        if ( from >= size ) return getRoot();
        return replaceWith( JsonBuilder.createArray( array -> {
            if ( from > 0 )
                range( 0, from ).forEach( i -> array.addElement( element( i ) ) );
            if ( to < size )
                range( to, size ).forEach( i -> array.addElement( element( i ) ) );
        } ) );
    }

    private void checkType( JsonNodeType expected, JsonNodeType actual, String operation ) {
        if ( actual != expected )
            throw new JsonTreeException(
                format( "`%s` only allowed for %s but was: %s", operation, expected, actual ) );
    }

}
