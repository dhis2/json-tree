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

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static org.hisp.dhis.jsontree.Chars.expectChar;
import static org.hisp.dhis.jsontree.Chars.expectChars;
import static org.hisp.dhis.jsontree.Chars.expectDigit;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;
import org.hisp.dhis.jsontree.internal.Maybe;
import org.hisp.dhis.jsontree.internal.Surly;

/**
 * {@link JsonTree} is a JSON parser specifically designed as a verifying tool for JSON trees which skips though JSON
 * trees to extract only a specific {@link JsonNode} at a certain path while have subsequent operations benefit from
 * partial parsing work already done in previous calls.
 * <p>
 * Supported paths:
 *
 * <pre>
 * $ = root
 * $.property = field property in root object
 * $.a.b = field b in a in root object
 * $[0] = index 0 of root array
 * $.a[1] index 1 of array in a field in root
 * </pre>
 * <p>
 * This parser follows the JSON standard as defined by
 * <a href="https://www.json.org/">json.org</a>.
 *
 * @author Jan Bernitt
 * @implNote This uses records because JVMs starting with JDK 21/22 will consider record fields as {@code @Stable} which
 * might help optimize access to the {@link #json} char array.
 */
record JsonTree(@Surly char[] json, @Surly HashMap<JsonPath, JsonNode> nodesByPath, @Maybe JsonNode.GetListener onGet)
    implements Serializable {

    static JsonNode of( @Surly String json, @Maybe JsonNode.GetListener onGet ) {
        return new JsonTree( json.toCharArray(), new HashMap<>(), onGet ).get( JsonPath.ROOT );
    }

    /**
     * @param json valid JSON, except it also allows single quoted strings and dangling commas
     * @return a lazy tree of the provided JSON
     * @since 0.11
     */
    static JsonNode ofNonStandard( @Surly String json ) {
        char[] jsonChars = json.toCharArray();
        adjustToStandard( jsonChars );
        return new JsonTree( jsonChars, new HashMap<>(), null ).get( JsonPath.ROOT );
    }

    /**
     * The main idea of lazy nodes is that at creation only the start index and the path the node represents is known.
     * <p>
     * The "expensive" operations to access the nodes {@link #value()} or find its {@link #endIndex()} are only computed
     * on demand.
     */
    private abstract static class LazyJsonNode<T> implements JsonNode {

        final JsonTree tree;
        final JsonPath path;
        final int start;

        protected Integer end;
        private transient T value;

        LazyJsonNode( JsonTree tree, JsonPath path, int start ) {
            this.tree = tree;
            this.path = path;
            this.start = start;
        }

        @Override
        public final JsonNode getRoot() {
            return tree.get( JsonPath.ROOT );
        }

        @Override
        public final JsonPath getPath() {
            return path;
        }

        //TODO change return type to Text?
        @Override
        public final String getDeclaration() {
            return path.isEmpty() // avoid parse caused by endIndex() if root
                ? new String( tree.json )
                : new String( tree.json, start, endIndex() - start );
        }

        @Override
        public final T value() {
            if ( getType() == JsonNodeType.NULL ) {
                checkNoTrailingTrash();
                return null;
            }
            if ( !isParsed() ) {
                value = parseValue();
                checkNoTrailingTrash();
            }
            return value;
        }

        private void checkNoTrailingTrash() {
            if ( !path.isEmpty() ) return;
            Chars.expectEndOfBuffer( tree.json, skipWhitespace( tree.json, endIndex() ) );
        }

        protected final boolean isParsed() {
            return value != null;
        }

        @Override
        public final int startIndex() {
            return start;
        }

        @Override
        public final int endIndex() {
            if ( end == null ) end = skipNodeAutodetect( tree.json, start );
            return end;
        }

        @Override
        public final JsonNode replaceWith( String json ) {
            if ( isRoot() ) return JsonNode.of( json );
            int endIndex = endIndex();
            StringBuilder newJson = new StringBuilder();
            char[] oldJson = tree.json;
            if ( startIndex() > 0 ) {
                newJson.append( oldJson, 0, startIndex() );
            }
            newJson.append( json );
            if ( endIndex < oldJson.length ) {
                newJson.append( oldJson, endIndex, oldJson.length - endIndex );
            }
            return JsonNode.of( newJson.toString() );
        }

        @Override
        public final void visit( JsonNodeType type, Consumer<JsonNode> visitor ) {
            if ( type == null || type == getType() ) {
                visitor.accept( this );
            }
            visitChildren( type, visitor );
        }

        void visitChildren( JsonNodeType type, Consumer<JsonNode> visitor ) {
            // by default node has no children
        }

        @Override
        public final Optional<JsonNode> find( @Maybe JsonNodeType type, Predicate<JsonNode> test ) {
            if ( (type == null || type == getType()) && test.test( this ) ) {
                return Optional.of( this );
            }
            return findChildren( type, test );
        }

        Optional<JsonNode> findChildren( JsonNodeType type, Predicate<JsonNode> test ) {
            // by default node has no children, no match
            return Optional.empty();
        }

        @Override
        public final String toString() {
            return getDeclaration();
        }


        @Override
        public int hashCode() {
            return Arrays.hashCode( tree.json );
        }

        @Override
        public boolean equals( Object obj ) {
            return this == obj || obj instanceof LazyJsonNode<?> other && Arrays.equals(tree.json, other.tree.json);
        }

        /**
         * @return parses the JSON to a value as described by {@link #value()}
         */
        abstract T parseValue();
    }

    private static final class LazyJsonObject extends LazyJsonNode<Iterable<Entry<Text, JsonNode>>>
        implements Iterable<Entry<Text, JsonNode>> {

        private Integer size;

        LazyJsonObject( JsonTree tree, JsonPath path, int start ) {
            super( tree, path, start );
        }

        @Surly @Override
        public Iterator<Entry<Text, JsonNode>> iterator() {
            return members( true );
        }

        @Override
        public JsonNodeType getType() {
            return JsonNodeType.OBJECT;
        }

        @Override
        public JsonNode get( Text name ) {
            return tree.get(path.extendedWith(name));
        }

        @Override
        public JsonNode getOrNull( Text name ) throws JsonTreeException {
            return tree.getOrNull(path.extendedWith( name ));
        }

        @Surly @Override
        public JsonNode get(@Surly JsonPath subPath ) {
            return tree.get( this.path.extendedWith( subPath ) );
        }

        @Maybe @Override
        public JsonNode getOrNull( @Surly JsonPath subPath ) {
            return tree.getOrNull( this.path.extendedWith( subPath ) );
        }

        @Override
        public Iterable<Entry<Text, JsonNode>> members() {
            return requireNonNull( value() );
        }

        @Override
        public boolean isEmpty() {
            // avoid computing value with a "cheap" check
            return tree.json[skipWhitespace( tree.json, start + 1 )] == '}';
        }

        @Override
        public int size() {
            if (size != null) return size;
            if (isEmpty()) return 0;
            size = (int) StreamSupport.stream( spliterator(), false ).count();
            return size;
        }

        @Override
        void visitChildren( JsonNodeType type, Consumer<JsonNode> visitor ) {
            members().forEach( e -> e.getValue().visit( type, visitor ) );
        }

        @Override
        Optional<JsonNode> findChildren( JsonNodeType type, Predicate<JsonNode> test ) {
            for ( Entry<Text, JsonNode> e : members() ) {
                Optional<JsonNode> res = e.getValue().find( type, test );
                if ( res.isPresent() ) {
                    return res;
                }
            }
            return Optional.empty();
        }

        @Override
        Iterable<Entry<Text, JsonNode>> parseValue() {
            return this;
        }

        @Surly @Override
        public JsonNode member( Text name )  throws JsonPathException {
            return requireNonNull( member( name, false) );
        }

        @Override
        public JsonNode memberOrNull( Text name )  throws JsonPathException {
            return member( name, true );
        }

        private JsonPathException noSuchElement(Text name) {
            JsonPath memberPath = path.extendedWith( name );
            return new JsonPathException( memberPath,
                format( "Path `%s` does not exist, object `%s` does not have a property `%s`", memberPath, path,
                    name ) );
        }

        private JsonNode member( Text name, boolean orNull )  throws JsonPathException {
            JsonPath memberPath =  path.extendedWith( name );
            JsonNode member = tree.nodesByPath.get( memberPath );
            if ( member != null ) return member;
            if (size != null) {
                // if size is known all members are in the tree map
                // so a miss means there is no such member
                if (!orNull) throw noSuchElement( name );
                return null;
            }
            char[] json = tree.json;
            int index = skipWhitespace( json, expectChar( json, start, '{' ) );
            while ( index < json.length && json[index] != '}' ) {
                Text property = Chars.parseString( json, index );
                index = expectColon( json, skipString( json, index ) );
                if ( name.equals( property ) ) {
                    int mStart = index;
                    return tree.nodesByPath.computeIfAbsent( memberPath, key -> tree.autoDetect( key, mStart ) );
                } else {
                    index = skipNodeAutodetect( json, index );
                    index = expectCommaOrEnd( json, index, '}' );
                }
            }
            if (!orNull) throw noSuchElement( name );
            return null;
        }

        @Override
        public Iterator<Entry<Text, JsonNode>> members( boolean cacheNodes ) {
            return new Iterator<>() {
                private final char[] json = tree.json;
                private final Map<JsonPath, JsonNode> nodesByPath = tree.nodesByPath;

                private int startIndex = skipWhitespace( json, expectChar( json, start, '{' ) );
                private int n = 0;

                @Override
                public boolean hasNext() {
                    boolean hasNext = startIndex < json.length && json[startIndex] != '}';
                    if (!hasNext && cacheNodes) size = n;
                    return hasNext;
                }

                @Override
                public Entry<Text, JsonNode> next() {
                    if ( !hasNext() )
                        throw new NoSuchElementException( "next() called without checking hasNext()" );
                    Text name = Chars.parseString( json, startIndex );
                    JsonPath propertyPath = path.extendedWith( name );
                    int startIndexVal = expectColon( json, skipString( json, startIndex ) );
                    JsonNode member = cacheNodes
                        ? nodesByPath.computeIfAbsent( propertyPath, key -> tree.autoDetect( key, startIndexVal ) )
                        : nodesByPath.get( propertyPath );
                    if ( member == null ) {
                        member = tree.autoDetect( propertyPath, startIndexVal );
                    } else if ( member.endIndex() < startIndexVal ) {
                        // duplicate keys case: just skip the duplicate
                        startIndex = expectCommaOrEnd( json, skipNodeAutodetect( json, startIndexVal ), '}' );
                        return Map.entry( name, member );
                    }
                    n++;
                    startIndex = expectCommaOrEnd( json, member.endIndex(), '}' );
                    return Map.entry( name, member );
                }
            };
        }

        @Override
        public Iterable<JsonPath> paths() {
            return keys(path::extendedWith);
        }

        @Override
        public Iterable<String> names() {
            return keys(Text::toString);
        }

        @Override
        public Iterable<Text> keys() {
            return keys(name -> name);
        }

        private <E> Iterable<E> keys( Function<Text, E> toKey) {
            return () -> new Iterator<>() {
                private final char[] json = tree.json;
                private final Map<JsonPath, JsonNode> nodesByPath = tree.nodesByPath;
                private int startIndex = skipWhitespace( json, expectChar( json, start, '{' ) );

                @Override
                public boolean hasNext() {
                    return startIndex < json.length && json[startIndex] != '}';
                }

                @Override
                public E next() {
                    if ( !hasNext() )
                        throw new NoSuchElementException( "next() called without checking hasNext()" );
                    Text name = Chars.parseString( json, startIndex );
                    // advance to next member or end...
                    JsonNode member = nodesByPath.get( path.extendedWith( name ) );
                    startIndex = expectColon( json, skipString( json, startIndex )); // move after :
                    // move after value
                    startIndex = member == null || member.endIndex() < startIndex // (duplicates)
                        ? expectCommaOrEnd( json, skipNodeAutodetect( json, startIndex ), '}' )
                        : expectCommaOrEnd( json, member.endIndex(), '}' );
                    return toKey.apply( name );
                }
            };
        }
    }

    private static final class LazyJsonArray extends LazyJsonNode<Iterable<JsonNode>> implements Iterable<JsonNode> {

        private Integer size;

        LazyJsonArray( JsonTree tree, JsonPath path, int start ) {
            super( tree, path, start );
        }

        @Surly @Override
        public Iterator<JsonNode> iterator() {
            return elements( true );
        }

        @Surly @Override
        public JsonNode get( @Surly JsonPath subPath ) {
            return tree.get( this.path.extendedWith( subPath ) );
        }

        @Maybe @Override
        public JsonNode getOrNull( @Surly JsonPath subPath ) {
            return tree.getOrNull( this.path.extendedWith( subPath ) );
        }

        @Override
        public JsonNodeType getType() {
            return JsonNodeType.ARRAY;
        }

        @Override
        public Iterable<JsonNode> elements() {
            return requireNonNull( value() );
        }

        @Override
        public boolean isEmpty() {
            // avoid computing value with a "cheap" check
            return tree.json[skipWhitespace( tree.json, start + 1 )] == ']';
        }

        @Override
        public int size() {
            if (size != null) return size;
            if (isEmpty()) return 0;
            size = (int) StreamSupport.stream( spliterator(), false ).count();
            return size;
        }

        @Override
        void visitChildren( JsonNodeType type, Consumer<JsonNode> visitor ) {
            elements().forEach( node -> node.visit( type, visitor ) );
        }

        @Override
        Optional<JsonNode> findChildren( JsonNodeType type, Predicate<JsonNode> test ) {
            for ( JsonNode e : elements() ) {
                Optional<JsonNode> res = e.find( type, test );
                if ( res.isPresent() ) return res;
            }
            return Optional.empty();
        }

        @Override
        Iterable<JsonNode> parseValue() {
            return this;
        }

        @Surly @Override
        public JsonNode element( int index ) {
            return element( index, Text.of( index ), false );
        }

        @Surly @Override
        public JsonNode element( Text index ) {
            return element( index.parseInt(), index, false );
        }

        @Override
        public JsonNode elementOrNull( int index ) {
            return element( index, Text.of(index), true );
        }

        @Override
        public JsonNode elementOrNull( Text index ) {
            return element( index.parseInt(), index, true );
        }

        private JsonPathException outOfBounds(int index) {
            JsonPath elementPath = path.extendedWith( index );
            String msg = "Path `%s` does not exist".formatted( elementPath );
            if (size != null) msg += ", array `%s` has only `%d` elements".formatted( getPath(), size );
            throw new JsonPathException( elementPath, msg);
        }

        private JsonNode element( int index, Text segment, boolean orNull )
            throws JsonPathException {
            if ( index < 0 )
                throw outOfBounds( index );
            if (size != null && index >= size) {
                // early exit for a miss
                if (!orNull) throw outOfBounds( index );
                return null;
            }
            JsonPath elementPath = path.extendedWith( segment );
            JsonNode e = tree.nodesByPath.get( elementPath );
            if (e != null) return e;
            char[] json = tree.json;
            if (index == 0) {
                int i = skipWhitespace( json, expectChar( json, start, '[' ) );
                if (json[i] == ']') {
                    if (!orNull) throw outOfBounds( 0 );
                    return null;
                }
                return tree.nodesByPath.computeIfAbsent( elementPath, p -> tree.autoDetect( p, i ) );
            }
            // maybe the element before it exists? (iteration in a counter loop)
            JsonNode predecessor = tree.nodesByPath.get( path.extendedWith( index - 1) );
            if (predecessor != null) {
                int i = skipWhitespace( json, expectCommaOrEnd( json, predecessor.endIndex(), ']' ));
                if (json[i] == ']') {
                    if (!orNull) throw outOfBounds( index - 1 );
                    return null;
                }
                return tree.nodesByPath.computeIfAbsent( elementPath, p -> tree.autoDetect( p, i ) );
            }
            // go there from the start of the array
            int i = skipWhitespace( json, expectChar( json, start, '[' ) );
            int elementsToSkip = index;
            while ( elementsToSkip > 0 && i < json.length && json[i] != ']' ) {
                i = skipWhitespace( json, i );
                i = skipNodeAutodetect( json, i );
                i = expectCommaOrEnd( json, i, ']' );
                elementsToSkip--;
            }
            if ( json[i] == ']' ) {
                if (!orNull) throw  outOfBounds( index - elementsToSkip );
                return null;
            }
            int eStart = i;
            return tree.nodesByPath.computeIfAbsent( elementPath, p -> tree.autoDetect( p, eStart ));
        }

        @Override
        public Iterator<JsonNode> elements( boolean cacheNodes ) {
            return new Iterator<>() {
                private final char[] json = tree.json;
                private final Map<JsonPath, JsonNode> nodesByPath = tree.nodesByPath;

                private int startIndex = skipWhitespace( json, expectChar( json, start, '[' ) );
                private int n = 0;

                @Override
                public boolean hasNext() {
                    boolean hasNext = startIndex < json.length && json[startIndex] != ']';
                    if (!hasNext && cacheNodes) size = n;
                    return hasNext;
                }

                @Override
                public JsonNode next() {
                    if ( !hasNext() )
                        throw new NoSuchElementException( "next() called without checking hasNext()" );
                    JsonPath elementPath = path.extendedWith( n );
                    JsonNode e = cacheNodes
                        ? nodesByPath.computeIfAbsent( elementPath, key -> tree.autoDetect( key, startIndex ) )
                        : nodesByPath.get( elementPath );
                    if ( e == null ) {
                        e = tree.autoDetect( elementPath, startIndex );
                    }
                    n++;
                    startIndex = expectCommaOrEnd( json, e.endIndex(), ']' );
                    return e;
                }
            };
        }
    }

    private static final class LazyJsonNumber extends LazyJsonNode<Number> {

        LazyJsonNumber( JsonTree tree, JsonPath path, int start ) {
            super( tree, path, start );
        }

        @Override
        public JsonNodeType getType() {
            return JsonNodeType.NUMBER;
        }

        @Override
        Number parseValue() {
            end = skipNumber( tree.json, start );
            return Chars.parseNumber( tree.json, start, end - start );
        }
    }



    private static final class LazyJsonString extends LazyJsonNode<Text> {

        LazyJsonString( JsonTree tree, JsonPath path, int start ) {
            super( tree, path, start );
        }

        @Override
        public JsonNodeType getType() {
            return JsonNodeType.STRING;
        }

        @Override
        Text parseValue() {
            return Chars.parseString( tree.json, start );
        }
    }

    private static final class LazyJsonBoolean extends LazyJsonNode<Boolean> {

        LazyJsonBoolean( JsonTree tree, JsonPath path, int start ) {
            super( tree, path, start );
        }

        @Override
        public JsonNodeType getType() {
            return JsonNodeType.BOOLEAN;
        }

        @Override
        Boolean parseValue() {
            end = skipBoolean( tree.json, start );
            return end == start + 4; // then it was true
        }
    }

    private static final class LazyJsonNull extends LazyJsonNode<Serializable> {

        LazyJsonNull( JsonTree tree, JsonPath path, int start ) {
            super( tree, path, start );
        }

        @Override
        public JsonNodeType getType() {
            return JsonNodeType.NULL;
        }

        @Override
        Serializable parseValue() {
            end = skipNull( tree.json, start );
            return null;
        }
    }

    @Override
    public String toString() {
        return new String( json );
    }

    /**
     * Returns the {@link JsonNode} for the given path.
     *
     * @param path a path as described in {@link JsonTree} javadoc
     * @return the {@link JsonNode} for the path, never {@code null}
     * @throws JsonPathException   when this document does not contain a node corresponding to the given path or the
     *                             given path is not a valid path expression
     * @throws JsonFormatException when this document contains malformed JSON that confuses the parser
     */
    private JsonNode get( JsonPath path ) {
        return get( path, false );
    }

    private JsonNode getOrNull( JsonPath path ) {
        return get( path, true );
    }

    private JsonNode get( JsonNode parent, Text name, boolean orNull ) {
        return null; //TODO
    }

    private JsonNode get( JsonPath path, boolean orNull ) {
        if ( nodesByPath.isEmpty() )
            nodesByPath.put( JsonPath.ROOT, autoDetect( JsonPath.ROOT, skipWhitespace( json, 0 ) ) );
        if ( onGet != null && !path.isEmpty() ) onGet.accept( path.toString() );
        JsonNode node = nodesByPath.get( path );
        if ( node != null )
            return node;
        // find by finding the closest already indexed parent and navigate down from there...
        JsonNode parent = getClosestIndexedParent( path, nodesByPath );
        int segMax = path.size();
        int segCur = parent.getPath().size();
        while (parent != null && segCur < segMax ) { // meaning: are we at the target node? (self)
            checkNodeIs( parent, path );
            Text segment = path.segments().get( segCur );
            if (parent.getType() == JsonNodeType.ARRAY) {
                parent = orNull ? parent.elementOrNull( segment ) : parent.element( segment );
            } else if ( parent.getType() == JsonNodeType.OBJECT ) {
                parent = orNull ? parent.memberOrNull( segment ) : parent.member( segment );
            } else {
                throw new JsonPathException( path, format( "Malformed path %s at %s.", path, segment ) );
            }
            segCur++;
        }
        return parent;
    }

    private JsonNode autoDetect( JsonPath path, int atIndex ) {
        JsonNode node = nodesByPath.get( path );
        if ( node != null ) {
            return node;
        }
        if ( atIndex >= json.length ) {
            throw new JsonFormatException( json, atIndex, "a JSON value but found EOI" );
        }
        char c = json[atIndex];
        return switch ( c ) {
            case '{' -> new LazyJsonObject( this, path, atIndex );
            case '[' -> new LazyJsonArray( this, path, atIndex );
            case '"' -> new LazyJsonString( this, path, atIndex );
            case 't', 'f' -> new LazyJsonBoolean( this, path, atIndex );
            case 'n' -> new LazyJsonNull( this, path, atIndex );
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' ->
                 new LazyJsonNumber( this, path, atIndex );
            default -> throw new JsonFormatException( json, atIndex, "start of a JSON value but found: `" + c + "`" );
        };
    }

    private static void checkNodeIs( JsonNode parent, JsonPath path ) {
        JsonNodeType type = parent.getType();
        if ( type.isSimple() ) {
            throw new JsonPathException( path,
                format( "Path `%s` does not exist, parent `%s` is already a simple node of type %s.", path,
                    parent.getPath(), type ) );
        }
    }

    private static JsonNode getClosestIndexedParent( JsonPath path, Map<JsonPath, JsonNode> nodesByPath ) {
        JsonPath parentPath = path.dropLastSegment();
        JsonNode parent = nodesByPath.get( parentPath );
        if ( parent != null ) {
            return parent;
        }
        return getClosestIndexedParent( parentPath, nodesByPath );
    }

    /*--------------------------------------------------------------------------
     Parsing support
     -------------------------------------------------------------------------*/

    static int skipNodeAutodetect( char[] json, int atIndex ) {
        return switch ( json[atIndex] ) {
            case '{' -> // object node
                skipObject( json, atIndex );
            case '[' -> // array node
                skipArray( json, atIndex );
            case '"' -> // string node
                skipString( json, atIndex ); // true => boolean node
            case 't', 'f' -> // false => boolean node
                skipBoolean( json, atIndex );
            case 'n' -> // null node
                skipNull( json, atIndex );
            default -> // must be number node then...
                skipNumber( json, atIndex );
        };
    }

    static int skipObject( char[] json, int fromIndex ) {
        int index = fromIndex;
        index = expectChar( json, index, '{' );
        index = skipWhitespace( json, index );
        while ( index < json.length && json[index] != '}' ) {
            index = skipString( json, index );
            index = expectColon( json, index );
            index = skipNodeAutodetect( json, index );
            index = expectCommaOrEnd( json, index, '}' );
        }
        return expectChar( json, index, '}' );
    }

    static int skipArray( char[] json, int fromIndex ) {
        int index = fromIndex;
        index = expectChar( json, index, '[' );
        index = skipWhitespace( json, index );
        while ( index < json.length && json[index] != ']' ) {
            index = skipNodeAutodetect( json, index );
            index = expectCommaOrEnd( json, index, ']' );
        }
        return expectChar( json, index, ']' );
    }

    private static int expectColon( char[] json, int index ) {
        return skipWhitespace( json, expectChar( json, skipWhitespace( json, index ), ':' ) );
    }

    private static int expectCommaOrEnd( char[] json, int index, char end ) {
        index = skipWhitespace( json, index );
        if ( json[index] == ',' )
            return skipWhitespace( json, index + 1 );
        if ( json[index] != end )
            return expectChar( json, index, end ); // causes fail
        return index; // found end, return index pointing to it
    }

    private static int skipBoolean( char[] json, int fromIndex ) {
        return expectChars( json, fromIndex, json[fromIndex] == 't' ? "true" : "false" );
    }

    private static int skipNull( char[] json, int fromIndex ) {
        return expectChars( json, fromIndex, "null" );
    }

    private static int skipString( char[] json, int fromIndex ) {
        int index = fromIndex;
        index = expectChar( json, index, '"' );
        while ( index < json.length ) {
            char c = json[index++];
            if ( c == '"' ) {
                // found the end (if escaped we would have hopped over)
                return index;
            } else if ( c == '\\' ) {
                Chars.expectEscapedCharacter( json, index );
                // hop over escaped char or unicode
                index += json[index] == 'u' ? 5 : 1;
            } else if ( c < ' ' ) {
                throw new JsonFormatException( json, index - 1,
                    "Control code character is not allowed in JSON string but found: " + (int) c );
            }
        }
        return expectChar( json, index, '"' );
    }

    private static int skipNumber( char[] json, int fromIndex ) {
        int index = fromIndex;
        index = skipChar( json, index, '-' );
        index = expectDigit( json, index );
        index = skipDigits( json, index );
        int before = index;
        index = skipChar( json, index, '.' );
        if ( index > before ) {
            index = expectDigit( json, index );
            index = skipDigits( json, index );
        }
        before = index;
        index = skipChar( json, index, 'e', 'E' );
        if ( index == before )
            return index;
        index = skipChar( json, index, '+', '-' );
        index = expectDigit( json, index );
        return skipDigits( json, index );
    }

    private static int skipWhitespace( char[] json, int fromIndex ) {
        int index = fromIndex;
        while ( index < json.length && isWhitespace( json[index] ) )
            index++;
        return index;
    }

    private static int skipDigits( char[] json, int fromIndex ) {
        int index = fromIndex;
        while ( index < json.length && isDigit( json[index] ) )
            index++;
        return index;
    }

    /**
     * JSON only considers ASCII digits as digits
     */
    private static boolean isDigit( char c ) {
        return c >= '0' && c <= '9';
    }

    /**
     * JSON only considers ASCII whitespace as whitespace
     */
    private static boolean isWhitespace( char c ) {
        return c == ' ' || c == '\n' || c == '\t' || c == '\r';
    }

    private static int skipChar( char[] json, int index, char c ) {
        return index < json.length && json[index] == c ? index + 1 : index;
    }

    private static int skipChar( char[] json, int index, char... anyOf ) {
        if ( index >= json.length ) {
            return index;
        }
        for ( char c : anyOf ) {
            if ( json[index] == c ) {
                return index + 1;
            }
        }
        return index;
    }

    /*
    Adjusting non-standard conform JSON to standard conform JSON.
    The actual parser only accepts standard conform JSON input.
    The lenient parsing is implemented by rewriting the input to be standard conform.
     */

    /**
     * Skips through the input and - switches single quotes of string values and member names to double quotes. -
     * removes dangling commas for arrays and objects
     */
    private static void adjustToStandard( char[] json ) {
        adjustNodeAutodetect( json, 0 );
    }

    static int adjustNodeAutodetect( char[] json, int atIndex ) {
        return switch ( json[atIndex] ) {
            case '{' -> // object node
                adjustObject( json, atIndex );
            case '[' -> // array node
                adjustArray( json, atIndex );
            case '\'' -> adjustString( json, atIndex );
            case '"' -> // string node
                skipString( json, atIndex ); // true => boolean node
            case 't', 'f' -> // false => boolean node
                skipBoolean( json, atIndex );
            case 'n' -> // null node
                skipNull( json, atIndex );
            default -> // must be number node then...
                skipNumber( json, atIndex );
        };
    }

    static int adjustObject( char[] json, int fromIndex ) {
        int index = fromIndex;
        index = expectChar( json, index, '{' );
        index = skipWhitespace( json, index );
        while ( index < json.length && json[index] != '}' ) {
            index = adjustString( json, index );
            index = expectColon( json, index );
            index = adjustNodeAutodetect( json, index );
            // blank dangling ,
            if ( json[index] == ',' && json[index + 1] == '}' ) json[index++] = ' ';
            index = expectCommaOrEnd( json, index, '}' );
        }
        return expectChar( json, index, '}' );
    }

    static int adjustArray( char[] json, int fromIndex ) {
        int index = fromIndex;
        index = expectChar( json, index, '[' );
        index = skipWhitespace( json, index );
        while ( index < json.length && json[index] != ']' ) {
            index = adjustNodeAutodetect( json, index );
            // blank dangling ,
            if ( json[index] == ',' && json[index + 1] == ']' )
                json[index++] = ' ';
            index = expectCommaOrEnd( json, index, ']' );
        }
        return expectChar( json, index, ']' );
    }

    static int adjustString( char[] json, int fromIndex ) {
        int index = fromIndex;
        if ( json[index] == '"' ) return skipString( json, fromIndex );
        index = expectChar( json, index, '\'' );
        json[index - 1] = '"';
        while ( index < json.length && json[index] != '\'')
            index++;
        index = expectChar( json, index, '\'' );
        json[index - 1] = '"';
        return index;
    }
}
