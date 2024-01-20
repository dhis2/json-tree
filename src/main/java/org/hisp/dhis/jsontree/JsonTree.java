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

import org.hisp.dhis.jsontree.internal.Maybe;
import org.hisp.dhis.jsontree.internal.Surly;

import java.io.Serializable;
import java.util.AbstractMap.SimpleEntry;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.stream.StreamSupport;

import static java.lang.Character.toChars;
import static java.lang.Integer.parseInt;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

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
record JsonTree(@Surly char[] json, @Surly HashMap<String, JsonNode> nodesByPath, @Maybe JsonNode.GetListener onGet)
    implements Serializable {

    static JsonTree of( @Surly String json, @Maybe JsonNode.GetListener onGet ) {
        return new JsonTree( json.toCharArray(), new HashMap<>(), onGet );
    }

    /**
     * @param json valid JSON, except it also allows single quoted strings and dangling commas
     * @return a lazy tree of the provided JSON
     * @since 0.11
     */
    static JsonTree ofNonStandard( @Surly String json ) {
        char[] jsonChars = json.toCharArray();
        adjustToStandard( jsonChars );
        return new JsonTree( jsonChars, new HashMap<>(), null );
    }

    /**
     * The main idea of lazy nodes is that at creation only the start index and the path the node represents is known.
     * <p>
     * The "expensive" operations to access the nodes {@link #value()} or find its {@link #endIndex()} are only computed
     * on demand.
     */
    private abstract static class LazyJsonNode<T> implements JsonNode {

        final JsonTree tree;
        final String path;
        final int start;

        protected Integer end;
        private transient T value;

        //TODO remember the index in parent array/object to improve size() performance?

        LazyJsonNode( JsonTree tree, String path, int start ) {
            this.tree = tree;
            this.path = path;
            this.start = start;
        }

        @Override
        public final JsonNode getRoot() {
            return tree.get( "$" );
        }

        @Override
        public final String getPath() {
            return path;
        }

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
            if ( !path.isEmpty() )
                return;
            int afterWs = skipWhitespace( tree.json, endIndex() );
            if ( tree.json.length > afterWs ) {
                throw new JsonFormatException(
                    "Unexpected input after end of root value: " + getEndSection( tree.json, afterWs ) );
            }
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
            if ( end == null ) {
                end = skipNodeAutodetect( tree.json, start );
            }
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

        /**
         * @return parses the JSON to a value as described by {@link #value()}
         */
        abstract T parseValue();
    }

    private static final class LazyJsonObject extends LazyJsonNode<Iterable<Entry<String, JsonNode>>>
        implements Iterable<Entry<String, JsonNode>> {

        private Integer size;

        LazyJsonObject( JsonTree tree, String path, int start ) {
            super( tree, path, start );
        }

        @Surly @Override
        public Iterator<Entry<String, JsonNode>> iterator() {
            return members( true );
        }

        @Override
        public JsonNodeType getType() {
            return JsonNodeType.OBJECT;
        }

        @Surly @Override
        public JsonNode get( String path ) {
            if ( path.isEmpty() ) return this;
            if ( "$".equals( path ) ) return getRoot();
            if ( path.startsWith( "$" ) ) return getRoot().get( path.substring( 1 ) );
            if ( path.startsWith( "{" ) ) return tree.get( this.path + path );
            // trim any leading . of the relative path
            return tree.get( this.path + "." + (path.startsWith( "." ) ? path.substring( 1 ) : path) );
        }

        @Override
        public Iterable<Entry<String, JsonNode>> members() {
            return requireNonNull( value() );
        }

        @Override
        public boolean isEmpty() {
            // avoid computing value with a "cheap" check
            return tree.json[skipWhitespace( tree.json, start + 1 )] == '}';
        }

        @Override
        public int size() {
            if (isEmpty()) return 0;
            if (size != null) return size;
            size = (int) StreamSupport.stream( spliterator(), false ).count();
            return size;
        }

        @Override
        void visitChildren( JsonNodeType type, Consumer<JsonNode> visitor ) {
            members().forEach( e -> e.getValue().visit( type, visitor ) );
        }

        @Override
        Optional<JsonNode> findChildren( JsonNodeType type, Predicate<JsonNode> test ) {
            for ( Entry<String, JsonNode> e : members() ) {
                Optional<JsonNode> res = e.getValue().find( type, test );
                if ( res.isPresent() ) {
                    return res;
                }
            }
            return Optional.empty();
        }

        @Override
        Iterable<Entry<String, JsonNode>> parseValue() {
            return this;
        }

        @Override
        public JsonNode member( String name )
            throws JsonPathException {
            String mPath = path + "." + name;
            JsonNode member = tree.nodesByPath.get( mPath );
            if ( member != null ) {
                return member;
            }
            char[] json = tree.json;
            int index = skipWhitespace( json, expectChar( json, start, '{' ) );
            while ( index < json.length && json[index] != '}' ) {
                LazyJsonString.Span property = LazyJsonString.parseString( json, index );
                index = expectColonSeparator( json, property.endIndex() );
                if ( name.equals( property.value() ) ) {
                    int mStart = index;
                    return tree.nodesByPath.computeIfAbsent( mPath,
                        key -> tree.autoDetect( key, mStart ) );
                } else {
                    index = skipNodeAutodetect( json, index );
                    index = expectCommaSeparatorOrEnd( json, index, '}' );
                }
            }
            throw new JsonPathException( mPath,
                format( "Path `%s` does not exist, object `%s` does not have a property `%s`", mPath, path, name ) );
        }

        @Override
        public Iterator<Entry<String, JsonNode>> members( boolean cacheNodes ) {
            return new Iterator<>() {
                private final char[] json = tree.json;
                private final Map<String, JsonNode> nodesByPath = tree.nodesByPath;
                private int startIndex = skipWhitespace( json, expectChar( json, start, '{' ) );

                @Override
                public boolean hasNext() {
                    return startIndex < json.length && json[startIndex] != '}';
                }

                @Override
                public Entry<String, JsonNode> next() {
                    if ( !hasNext() )
                        throw new NoSuchElementException( "next() called without checking hasNext()" );
                    LazyJsonString.Span property = LazyJsonString.parseString( json, startIndex );
                    String name = property.value();
                    String mPath = path + "." + name;
                    int startIndexVal = expectColonSeparator( json, property.endIndex() );
                    JsonNode member = cacheNodes
                        ? nodesByPath.computeIfAbsent( mPath, key -> tree.autoDetect( key, startIndexVal ) )
                        : nodesByPath.get( mPath );
                    if ( member == null ) {
                        member = tree.autoDetect( mPath, startIndexVal );
                    } else if ( member.endIndex() < startIndexVal ) {
                        // duplicate keys case: just skip the duplicate
                        startIndex = expectCommaSeparatorOrEnd( json, skipNodeAutodetect( json, startIndexVal ), '}' );
                        return new SimpleEntry<>( name, member );
                    }
                    startIndex = expectCommaSeparatorOrEnd( json, member.endIndex(), '}' );
                    return new SimpleEntry<>( name, member );
                }
            };
        }

        @Override
        public Iterable<String> keys() {
            return () -> new Iterator<>() {
                private final char[] json = tree.json;
                private final Map<String, JsonNode> nodesByPath = tree.nodesByPath;
                private int startIndex = skipWhitespace( json, expectChar( json, start, '{' ) );

                @Override
                public boolean hasNext() {
                    return startIndex < json.length && json[startIndex] != '}';
                }

                @Override
                public String next() {
                    if ( !hasNext() )
                        throw new NoSuchElementException( "next() called without checking hasNext()" );
                    LazyJsonString.Span property = LazyJsonString.parseString( json, startIndex );
                    String name = property.value();
                    // advance to next member or end...
                    String mPath = path + "." + name;
                    JsonNode member = nodesByPath.get( mPath );
                    startIndex = expectColonSeparator( json, property.endIndex() ); // move after :
                    // move after value
                    startIndex = member == null || member.endIndex() < startIndex // (duplicates)
                        ? expectCommaSeparatorOrEnd( json, skipNodeAutodetect( json, startIndex ), '}' )
                        : expectCommaSeparatorOrEnd( json, member.endIndex(), '}' );
                    return name;
                }
            };
        }
    }

    private static final class LazyJsonArray extends LazyJsonNode<Iterable<JsonNode>> implements Iterable<JsonNode> {

        private Integer size;

        LazyJsonArray( JsonTree tree, String path, int start ) {
            super( tree, path, start );
        }

        @Surly @Override
        public Iterator<JsonNode> iterator() {
            return elements( true );
        }

        @Surly @Override
        public JsonNode get( String path ) {
            if ( path.isEmpty() ) return this;
            if ( "$".equals( path ) ) return getRoot();
            if ( path.startsWith( "$" ) ) return getRoot().get( path.substring( 1 ) );
            return tree.get( this.path + path );
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
            if (isEmpty()) return 0;
            if (size != null) return size;
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

        @Override
        public JsonNode element( int index )
            throws JsonPathException {
            if ( index < 0 ) {
                throw new JsonPathException( path,
                    format( "Path `%s` does not exist, array index is negative: %d", path, index ) );
            }
            char[] json = tree.json;
            JsonNode predecessor = index == 0 ? null : tree.nodesByPath.get( path + '[' + (index - 1) + ']' );
            int s = predecessor != null
                ? skipWhitespace( json, expectCommaSeparatorOrEnd( json, predecessor.endIndex(), ']' ) )
                : skipWhitespace( json, expectChar( json, start, '[' ) );
            int skipN = predecessor != null ? 0 : index;
            int startIndex = predecessor == null ? 0 : index - 1;
            return tree.nodesByPath.computeIfAbsent( path + '[' + index + ']',
                key -> tree.autoDetect( key, skipWhitespace( json, skipToElement( skipN, json, s,
                    skipped -> checkIndexExists( this, skipped + startIndex, key ) ) ) ) );
        }

        private static void checkIndexExists( JsonNode parent, int length, String path ) {
            throw new JsonPathException( path,
                format( "Path `%s` does not exist, array `%s` has only `%d` elements.",
                    path, parent.getPath(), length ) );
        }

        @Override
        public Iterator<JsonNode> elements( boolean cacheNodes ) {
            return new Iterator<>() {
                private final char[] json = tree.json;
                private final Map<String, JsonNode> nodesByPath = tree.nodesByPath;

                private int startIndex = skipWhitespace( json, expectChar( json, start, '[' ) );
                private int n = 0;

                @Override
                public boolean hasNext() {
                    return startIndex < json.length && json[startIndex] != ']';
                }

                @Override
                public JsonNode next() {
                    if ( !hasNext() )
                        throw new NoSuchElementException( "next() called without checking hasNext()" );
                    String ePath = path + '[' + n + "]";
                    JsonNode e = cacheNodes
                        ? nodesByPath.computeIfAbsent( ePath,
                        key -> tree.autoDetect( key, startIndex ) )
                        : nodesByPath.get( ePath );
                    if ( e == null ) {
                        e = tree.autoDetect( ePath, startIndex );
                    }
                    n++;
                    startIndex = expectCommaSeparatorOrEnd( json, e.endIndex(), ']' );
                    return e;
                }
            };
        }

        private static int skipToElement( int n, char[] json, int index, IntConsumer onEndOfArray ) {
            int elementsToSkip = n;
            while ( elementsToSkip > 0 && index < json.length && json[index] != ']' ) {
                index = skipWhitespace( json, index );
                index = skipNodeAutodetect( json, index );
                index = expectCommaSeparatorOrEnd( json, index, ']' );
                elementsToSkip--;
            }
            if ( json[index] == ']' ) {
                onEndOfArray.accept( n - elementsToSkip );
            }
            return index;
        }
    }

    private static final class LazyJsonNumber extends LazyJsonNode<Number> {

        LazyJsonNumber( JsonTree tree, String path, int start ) {
            super( tree, path, start );
        }

        @Override
        public JsonNodeType getType() {
            return JsonNodeType.NUMBER;
        }

        @Override
        Number parseValue() {
            end = skipNumber( tree.json, start );
            double number = Double.parseDouble( new String( tree.json, start, end - start ) );
            if ( number % 1 == 0d ) {
                long asLong = (long) number;
                if ( asLong < Integer.MAX_VALUE && asLong > Integer.MIN_VALUE ) {
                    return (int) asLong;
                }
                return asLong;
            }
            return number;
        }
    }

    private static final class LazyJsonString extends LazyJsonNode<String> {

        LazyJsonString( JsonTree tree, String path, int start ) {
            super( tree, path, start );
        }

        @Override
        public JsonNodeType getType() {
            return JsonNodeType.STRING;
        }

        @Override
        String parseValue() {
            Span span = parseString( tree.json, start );
            end = span.endIndex();
            return span.value();
        }

        record Span(String value, int endIndex) {}

        static Span parseString( char[] json, int start ) {
            //TODO make this a shared builder in JsonTree instance for performance?
            StringBuilder str = new StringBuilder();
            int index = start;
            index = expectChar( json, index, '"' );
            while ( index < json.length ) {
                char c = json[index++];
                if ( c == '"' ) {
                    // found the end (if escaped we would have hopped over)
                    // ++ already advanced after closing quotes, index is end
                    return new Span( str.toString(), index );
                }
                if ( c == '\\' ) {
                    checkEscapedCharExists( json, index );
                    checkValidEscapedChar( json, index );
                    switch ( json[index++] ) {
                        case 'u' -> { // unicode uXXXX
                            str.append( toChars( parseInt( new String( json, index, 4 ), 16 ) ) );
                            index += 4; // u we already skipped
                        }
                        case '\\' -> str.append( '\\' );
                        case '/' -> str.append( '/' );
                        case 'b' -> str.append( '\b' );
                        case 'f' -> str.append( '\f' );
                        case 'n' -> str.append( '\n' );
                        case 'r' -> str.append( '\r' );
                        case 't' -> str.append( '\t' );
                        case '"' -> str.append( '"' );
                        default -> throw new JsonFormatException( json, index, '?' );
                    }
                } else if ( c < ' ' ) {
                    throw new JsonFormatException( json, index - 1,
                        "Control code character is not allowed in JSON string but found: " + (int) c );
                } else {
                    str.append( c );
                }
            }
            // throws...
            expectChar( json, index, '"' );
            throw new JsonFormatException( "Invalid string" );
        }
    }

    private static final class LazyJsonBoolean extends LazyJsonNode<Boolean> {

        LazyJsonBoolean( JsonTree tree, String path, int start ) {
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

        LazyJsonNull( JsonTree tree, String path, int start ) {
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
    JsonNode get( String path ) {
        if ( nodesByPath.isEmpty() )
            nodesByPath.put( "", autoDetect( "", skipWhitespace( json, 0 ) ) );
        if ( path.startsWith( "$" ) ) {
            path = path.substring( 1 );
        }
        if ( onGet != null && !path.isEmpty() ) onGet.accept( path );
        JsonNode node = nodesByPath.get( path );
        if ( node != null ) {
            return node;
        }
        JsonNode parent = getClosestIndexedParent( path, nodesByPath );
        String pathToGo = path.substring( parent.getPath().length() );
        while ( !pathToGo.isEmpty() ) {
            if ( pathToGo.startsWith( "[" ) ) {
                checkNodeIs( parent, JsonNodeType.ARRAY, path );
                int index = parseInt( pathToGo.substring( 1, pathToGo.indexOf( ']' ) ) );
                parent = parent.element( index );
                pathToGo = pathToGo.substring( pathToGo.indexOf( ']' ) + 1 );
            } else if ( pathToGo.startsWith( "." ) ) {
                checkNodeIs( parent, JsonNodeType.OBJECT, path );
                String property = getHeadProperty( pathToGo );
                parent = parent.member( property );
                pathToGo = pathToGo.substring( 1 + property.length() );
            } else if ( pathToGo.startsWith( "{" ) ) {
                // map access syntax: {property}
                checkNodeIs( parent, JsonNodeType.OBJECT, path );
                String property = pathToGo.substring( 1, pathToGo.indexOf( '}' ) );
                parent = parent.member( property );
                pathToGo = pathToGo.substring( 2 + property.length() );
            } else {
                throw new JsonPathException( path, format( "Malformed path %s at %s.", path, pathToGo ) );
            }
        }
        return parent;
    }

    private static String getHeadProperty( String path ) {
        int index = 1;
        while ( index < path.length()
            && path.charAt( index ) != '.' && path.charAt( index ) != '[' && path.charAt( index ) != '{' ) {
            index++;
        }
        return path.substring( 1, index );
    }

    private JsonNode autoDetect( String path, int atIndex ) {
        JsonNode node = nodesByPath.get( path );
        if ( node != null ) {
            return node;
        }
        if ( atIndex >= json.length ) {
            throw new JsonFormatException( json, atIndex, "a JSON value but found EOI" );
        }
        char c = json[atIndex];
        switch ( c ) {
            case '{' -> {
                return new LazyJsonObject( this, path, atIndex );
            }
            case '[' -> {
                return new LazyJsonArray( this, path, atIndex );
            }
            case '"' -> {
                return new LazyJsonString( this, path, atIndex );
            }
            case 't', 'f' -> {
                return new LazyJsonBoolean( this, path, atIndex );
            }
            case 'n' -> {
                return new LazyJsonNull( this, path, atIndex );
            }
            case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                return new LazyJsonNumber( this, path, atIndex );
            }
            default -> throw new JsonFormatException( json, atIndex, "start of a JSON value but found: `" + c + "`" );
        }
    }

    private static void checkNodeIs( JsonNode parent, JsonNodeType expected, String path ) {
        if ( parent.getType() != expected ) {
            throw new JsonPathException( path,
                format( "Path `%s` does not exist, parent `%s` is not an %s but a %s node.", path,
                    parent.getPath(), expected, parent.getType() ) );
        }
    }

    private static void checkEscapedCharExists( char[] json, int index ) {
        if ( index >= json.length ) {
            throw new JsonFormatException(
                "Expected escaped character but reached EOI: " + getEndSection( json, index ) );
        }
    }

    private static void checkValidEscapedChar( char[] json, int index ) {
        if ( !isEscapableLetter( json[index] ) ) {
            throw new JsonFormatException( json, index, "Illegal escaped string character: " + json[index] );
        }
    }

    private static JsonNode getClosestIndexedParent( String path, Map<String, JsonNode> nodesByPath ) {
        String parentPath = JsonNode.parentPath( path );
        JsonNode parent = nodesByPath.get( parentPath );
        if ( parent != null ) {
            return parent;
        }
        return getClosestIndexedParent( parentPath, nodesByPath );
    }

    /*--------------------------------------------------------------------------
     Parsing support
     -------------------------------------------------------------------------*/

    @FunctionalInterface
    interface CharPredicate {

        boolean test( char c );
    }

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
            index = expectColonSeparator( json, index );
            index = skipNodeAutodetect( json, index );
            index = expectCommaSeparatorOrEnd( json, index, '}' );
        }
        return expectChar( json, index, '}' );
    }

    static int skipArray( char[] json, int fromIndex ) {
        int index = fromIndex;
        index = expectChar( json, index, '[' );
        index = skipWhitespace( json, index );
        while ( index < json.length && json[index] != ']' ) {
            index = skipNodeAutodetect( json, index );
            index = expectCommaSeparatorOrEnd( json, index, ']' );
        }
        return expectChar( json, index, ']' );
    }

    private static int expectColonSeparator( char[] json, int index ) {
        return skipWhitespace( json, expectChar( json, skipWhitespace( json, index ), ':' ) );
    }

    private static int expectCommaSeparatorOrEnd( char[] json, int index, char end ) {
        index = skipWhitespace( json, index );
        if ( json[index] == ',' ) {
            return skipWhitespace( json, index + 1 );
        }
        if ( json[index] != end ) {
            return expectChar( json, index, end ); // cause fail
        }
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
                checkEscapedCharExists( json, index );
                checkValidEscapedChar( json, index );
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
        index = expectChar( json, index, JsonTree::isDigit );
        index = skipDigits( json, index );
        int before = index;
        index = skipChar( json, index, '.' );
        if ( index > before ) {
            index = expectChar( json, index, JsonTree::isDigit );
            index = skipDigits( json, index );
        }
        before = index;
        index = skipChar( json, index, 'e', 'E' );
        if ( index == before )
            return index;
        index = skipChar( json, index, '+', '-' );
        index = expectChar( json, index, JsonTree::isDigit );
        return skipDigits( json, index );
    }

    private static int skipWhitespace( char[] json, int fromIndex ) {
        return skipWhile( json, fromIndex, JsonTree::isWhitespace );
    }

    private static int skipDigits( char[] json, int fromIndex ) {
        return skipWhile( json, fromIndex, JsonTree::isDigit );
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

    private static boolean isEscapableLetter( char c ) {
        return c == '"' || c == '\\' || c == '/' || c == 'b' || c == 'f' || c == 'n' || c == 'r' || c == 't'
            || c == 'u';
    }

    private static int skipWhile( char[] json, int fromIndex, CharPredicate whileTrue ) {
        int index = fromIndex;
        while ( index < json.length && whileTrue.test( json[index] ) ) {
            index++;
        }
        return index;
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

    private static int expectChars( char[] json, int index, CharSequence expected ) {
        int length = expected.length();
        for ( int i = 0; i < length; i++ ) {
            expectChar( json, index + i, expected.charAt( i ) );
        }
        return index + length;
    }

    private static int expectChar( char[] json, int index, CharPredicate expected ) {
        if ( index >= json.length ) {
            throw new JsonFormatException( "Expected character but reached EOI: " + getEndSection( json, index ) );
        }
        if ( !expected.test( json[index] ) ) {
            throw new JsonFormatException( json, index, '~' );
        }
        return index + 1;
    }

    private static int expectChar( char[] json, int index, char expected ) {
        if ( index >= json.length ) {
            throw new JsonFormatException( "Expected " + expected + " but reach EOI: " + getEndSection( json, index ) );
        }
        if ( json[index] != expected ) {
            throw new JsonFormatException( json, index, expected );
        }
        return index + 1;
    }

    private static String getEndSection( char[] json, int index ) {
        return new String( json, max( 0, min( json.length, index ) - 20 ), min( 20, json.length ) );
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
            index = expectColonSeparator( json, index );
            index = adjustNodeAutodetect( json, index );
            // blank dangling ,
            if ( json[index] == ',' && json[index + 1] == '}' ) json[index++] = ' ';
            index = expectCommaSeparatorOrEnd( json, index, '}' );
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
            index = expectCommaSeparatorOrEnd( json, index, ']' );
        }
        return expectChar( json, index, ']' );
    }

    static int adjustString( char[] json, int fromIndex ) {
        int index = fromIndex;
        if ( json[index] == '"' ) return skipString( json, fromIndex );
        index = expectChar( json, index, '\'' );
        json[index - 1] = '"';
        index = skipWhile( json, index, c -> c != '\'' );
        index = expectChar( json, index, '\'' );
        json[index - 1] = '"';
        return index;
    }
}
