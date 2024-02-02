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

import java.io.IOException;
import java.io.Reader;
import java.io.Serializable;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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

import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.stream.IntStream.range;

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
     * Allows to observe all path lookups in the tree.
     *
     * @since 0.10
     */
    @FunctionalInterface
    interface GetListener {

        /**
         * @param path absolute path in the tree that is resolved
         */
        void accept( String path );
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
    static JsonNode of( String json ) {
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
        return JsonTree.ofNonStandard( json ).get( "$" );
    }

    /**
     * Create a new lazily parsed {@link JsonNode} tree.
     *
     * @param json  standard compliant JSON input
     * @param onGet to observe all path lookup in the returned tree, may be null
     * @return the given JSON input as {@link JsonNode} tree
     * @since 0.10
     */
    static JsonNode of( String json, GetListener onGet ) {
        return JsonTree.of( json, onGet ).get( "$" );
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
        try {
            return of( Files.readString( file, encoding ), onGet );
        } catch ( IOException ex ) {
            throw new UncheckedIOException( ex );
        }
    }

    /**
     * @param json JSON input
     * @param onGet to observe all path lookup in the returned tree, may be null
     * @return the given JSON input as {@link JsonNode} tree
     * @since 1.0
     * @implNote not optimized, added to allow transparent change of implementation later
     */
    static JsonNode of( Reader json, GetListener onGet ) {
        char[] buffer = new char[4096]; // a usual FS block size
        StringBuilder jsonChars = new StringBuilder(0);
        int numChars;
        try {
            while ( (numChars = json.read( buffer )) >= 0 )
                jsonChars.append( buffer, 0, numChars );
        } catch ( IOException ex ) {
            throw new UncheckedIOException( ex );
        }
        return of(jsonChars.toString(), onGet);
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
    default JsonValue lift( JsonTypedAccessStore store ) {
        JsonVirtualTree root = new JsonVirtualTree( getRoot(), store );
        return isRoot() ? root : root.get( getPath() );
    }

    /**
     * @return The parent of this node or the root itself if this node is the root
     * @since 0.6
     */
    @Surly
    default JsonNode getParent() {
        return isRoot() ? this : getRoot().get( parentPath( getPath() ) );
    }

    /**
     * Access the node at the given path in the subtree of this node.
     *
     * @param path a simple or nested path relative to this node. A path starting with {@code $} is relative to the root
     *             node of this node, in other words it is an absolute path
     * @return the node at the given path
     * @throws JsonPathException when no such node exists in the subtree of this node
     */
    @Surly
    default JsonNode get( String path )
        throws JsonPathException {
        if ( path.isEmpty() ) return this;
        if ( "$".equals( path ) ) return getRoot();
        if ( path.startsWith( "$" ) ) return getRoot().get( path.substring( 1 ) );
        throw new JsonPathException( path,
            format( "This is a leaf node of type %s that does not have any children at path: %s", getType(), path ) );
    }

    /**
     * Size of an array of number of object members.
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
     * Check for member existence.
     *
     * @param name of the member in this object node
     * @return true, if the member exists, false otherwise
     * @throws JsonTreeException if this is not an object node
     * @since 0.6
     */
    default boolean isMember( String name ) {
        try {
            return member( name ) != null;
        } catch ( JsonPathException ex ) {
            return false;
        }
    }

    /**
     * Check for element existence.
     *
     * @param index array index greater than 0 and less than {@link #size()}
     * @return true, if this array node as an element at the provided index, false otherwise
     * @throws JsonTreeException if this is not an array node
     */
    default boolean isElement( int index ) {
        try {
            return element( index ) != null;
        } catch ( JsonPathException ex ) {
            return false;
        }
    }

    /**
     * The value depends on the {@link #getType()}:
     * <ul>
     * <li>{@link JsonNodeType#NULL} returns {@code null}</li>
     * <li>{@link JsonNodeType#BOOLEAN} returns {@link Boolean}</li>
     * <li>{@link JsonNodeType#STRING} returns {@link String}</li>
     * <li>{@link JsonNodeType#NUMBER} returns either {@link Integer},
     * {@link Long} or {@link Double}</li>
     * <li>{@link JsonNodeType#ARRAY} returns an {@link Iterable} of
     * {@link JsonNode}, same as {@link #elements()}</li>
     * <li>{@link JsonNodeType#OBJECT} returns a {@link Iterable} of {@link Entry}
     * with {@link String} keys and {@link JsonNode} values, same as {@link #members()}</li>
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
     */
    default JsonNode member( String name )
        throws JsonPathException {
        throw new JsonTreeException( getType() + " node has no member property: " + name );
    }

    /**
     * OBS! Only defined when this node is of type {@link JsonNodeType#OBJECT}).
     * <p>
     * The members are iterated in order of declaration in the underlying document.
     *
     * @return this {@link #value()} as a sequence of {@link Entry}
     * @throws JsonTreeException if this node is not an object node that could have members
     */
    default Iterable<Entry<String, JsonNode>> members() {
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
     * @return this {@link #value()} as a sequence of {@link String} keys
     * @throws JsonTreeException if this node is not an object node that could have members
     * @since 0.11
     */
    default Iterable<String> keys() {
        throw new JsonTreeException( getType() + " node has no keys property." );
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
    default Iterator<Entry<String, JsonNode>> members( boolean cacheNodes ) {
        throw new JsonTreeException( getType() + " node has no members property." );
    }

    /**
     * OBS! Only defined when this node is of type {@link JsonNodeType#ARRAY}).
     *
     * @param index index of the element to access
     * @return the node at the given array index
     * @throws JsonPathException when no such element exists
     * @throws JsonTreeException if this node is not an array node that could have elements
     */
    default JsonNode element( int index )
        throws JsonPathException {
        throw new JsonTreeException( getType() + " node has no element property for index: " + index );
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
     */
    String getPath();

    /**
     * @return the plain JSON of this node as defined in the overall content
     */
    String getDeclaration();

    /**
     * @return offset or index in the overall content where this node starts (inclusive, points to first index that
     * belongs to the node)
     */
    int startIndex();

    /**
     * @return offset or index in the overall content where this node ends (exclusive, points to first index after the
     * node)
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
    JsonNode replaceWith( String json );

    /**
     * @see #replaceWith(String)
     * @since 0.6
     */
    default JsonNode replaceWith( JsonNode node ) {
        return isRoot() ? node : replaceWith( node.getDeclaration() );
    }

    /**
     * @see #replaceWith(String)
     * @since 0.6
     */
    default JsonNode replaceWith( String path, JsonNode node ) {
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
    default JsonNode addMember( String name, String json ) {
        return addMembers( JsonNode.of( "{\"" + name + "\":" + json + "}" ) );
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
    default JsonNode addMembers( String path, Consumer<JsonBuilder.JsonObjectBuilder> obj ) {
        return get( path ).addMembers( obj );
    }

    /**
     * @see #addMembers(JsonNode)
     * @since 0.6
     */
    default JsonNode addMembers( String path, JsonNode obj ) {
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
        return replaceWith( JsonBuilder.createObject( merged -> {
            merged.addMembers(
                StreamSupport.stream( members().spliterator(), false ).filter( e -> !obj.isMember( e.getKey() ) ),
                JsonBuilder.JsonObjectBuilder::addMember );
            merged.addMembers( obj.members(), JsonBuilder.JsonObjectBuilder::addMember );
        } ) );
    }

    /**
     * @see #removeMembers(Set)
     * @since 0.6
     */
    default JsonNode removeMembers( String path, Set<String> names ) {
        return get( path ).removeMembers( names );
    }

    /**
     * Removes the given set of members from this node should they exist.
     *
     * @param names names of the members to remove
     * @return A new tree where this node is stripped of any members whose name is in the provided set of names
     * @throws JsonTreeException if this node is not an object node
     */
    default JsonNode removeMembers( Set<String> names ) {
        checkType( JsonNodeType.OBJECT, getType(), "removeMembers" );
        if ( isEmpty() || names.isEmpty() ) return getRoot();
        return replaceWith( JsonBuilder.createObject(
            obj -> obj.addMembers(
                StreamSupport.stream( members().spliterator(), false ).filter( e -> !names.contains( e.getKey() ) ),
                JsonBuilder.JsonObjectBuilder::addMember ) ) );
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
    default JsonNode addElements( String path, Consumer<JsonBuilder.JsonArrayBuilder> array ) {
        return get( path ).addElements( array );
    }

    /**
     * @see #addElements(JsonNode)
     * @since 0.6
     */
    default JsonNode addElements( String path, JsonNode array ) {
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

    static String parentPath( String path ) {
        if ( path.endsWith( "]" ) ) {
            return path.substring( 0, path.lastIndexOf( '[' ) );
        }
        int end = path.lastIndexOf( '.' );
        return end < 0 ? "" : path.substring( 0, end );
    }
}
