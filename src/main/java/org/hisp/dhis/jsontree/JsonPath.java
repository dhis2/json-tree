package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.Surly;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.lang.Integer.parseInt;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Stream.concat;

/**
 * Represents a JSON path and the logic of how to split it into segments.
 * <p>
 * Segments are always evaluated (split) left to right. Each segment is expected to start with the symbol identifying
 * the type of segment. There are three notations:
 *
 * <ul>
 *     <li>dot object member access: {@code .property}</li>
 *     <li>curly bracket object member access: {@code {property}}</li>
 *     <li>square bracket array index access: {@code [index]}</li>
 * </ul>
 * <p>
 * A segment is only identified as such if its open and closing symbol is found.
 * That means an opening bracket without a closing one is not a segment start symbol and understood literally.
 * Similarly, an opening bracket were the closing bracket is not found before another opening bracket is also not a start symbol and also understood literally.
 * Literally means it becomes part of the property name that started further left.
 * In the same manner an array index access is only understood as such if the string in square brackets is indeed an integer number.
 * Otherwise, the symbols are understood literally.
 * <p>
 * These rules are chosen to have maximum literal interpretation while providing a way to encode paths that contain notation symbols.
 * A general escaping mechanism is avoided as this would force users to always encode and decode paths just to make a few corner cases work which are not possible with the chosen compromise.
 *
 * @author Jan Bernitt
 * @since 1.1
 */
public record JsonPath(List<String> segments) {

    /**
     * A path pointing to the root or self
     */
    public static final JsonPath ROOT = new JsonPath( List.of() );

    /**
     * "Parse" a path {@link String} into its {@link JsonPath} form
     *
     * @param path a JSON path string
     * @return the provided path as {@link JsonPath} object
     * @throws JsonPathException when the path cannot be split into segments as it is not a valid path
     */
    public static JsonPath of( String path ) {
        return new JsonPath( splitIntoSegments( path ) );
    }

    /**
     * Create a path for an array index
     *
     * @param index array index
     * @return an array index selecting path
     */
    public static JsonPath of(int index) {
        return ROOT.extendedWith( index );
    }

    /**
     * Elevate an object member name to a key.
     *
     * @param name a plain object member name
     * @return the provided plain name unless it needs "escaping" to be understood as the path key (segment) referring
     * to the provided name member
     */
    public static String keyOf( String name ) {
        return keyOf( name, false );
    }

    /**
     * @param name         a plain object member name
     * @param forceSegment when true, the returned key must also be a valid segment, otherwise it can be a plain name
     *                     that is extended to a segment later by the caller
     * @return the plain name when possible and no segment is forced, otherwise the corresponding segment key
     */
    private static String keyOf( String name, boolean forceSegment ) {
        if ( name.startsWith( "{" ) || name.startsWith( "[" ) ) return "." + name;
        if ( name.indexOf( '.' ) >= 0 ) return "{" + name + "}";
        return forceSegment ? "." + name : name;
    }

    public JsonPath {
        requireNonNull( segments );
    }

    /**
     * Extends this path on the right (end)
     *
     * @param subPath the path to add to the end of this one
     * @return a new path instance that starts with all segments of this path followed by all segments of the provided sub-path
     */
    public JsonPath extendedWith( JsonPath subPath ) {
        return extendedWith( subPath.segments );
    }

    /**
     * Extends this path on the right (end)
     *
     * @param name a plain object member name
     * @return a new path instance that adds the provided object member name segment to this path to create a new
     * absolute path for the same root
     */
    public JsonPath extendedWith( String name ) {
        return extendedWith( List.of(keyOf( name, true )) );
    }

    /**
     * Extends this path on the right (end)
     *
     * @param index a valid array index
     * @return a new path instance that adds the provided array index segment to this path to create a new absolute path
     * for the same root
     */
    public JsonPath extendedWith( int index ) {
        if ( index < 0 ) throw new JsonPathException( this,
            "Path array index must be zero or positive but was: %d".formatted( index ) );
        return extendedWith( List.of("[" + index + "]"));
    }

    private JsonPath extendedWith( List<String> subSegments ) {
        return subSegments.isEmpty()
            ? this
            : new JsonPath( concat( segments.stream(), subSegments.stream() ).toList() );
    }

    /**
     * Shortens this path on the left (start)
     *
     * @param parent a direct or indirect parent of this path
     * @return a relative path to the node this path points to when starting from the provided parent
     * @throws JsonPathException if the parent path provided wasn't a parent of this path
     */
    public JsonPath shortenedBy( JsonPath parent ) {
        if ( parent.isEmpty() ) return this;
        if ( !toString().startsWith( parent.toString() ) ) throw new JsonPathException( parent,
            "Path %s is not a parent of %s".formatted( parent.toString(), toString() ) );
        return new JsonPath( segments.subList( parent.size(), size() ) );
    }

    /**
     * Drops the left most path segment.
     *
     * @return a path starting with to the next segment of this path (it's "child" path)
     * @throws JsonPathException when called on the root (empty path)
     * @see #dropLastSegment()
     */
    @Surly
    public JsonPath dropFirstSegment() {
        if ( isEmpty() ) throw new JsonPathException( this, "Root/self path does not have a child." );
        int size = segments.size();
        return size == 1 ? ROOT : new JsonPath( segments.subList( 1, size ) );
    }

    /**
     * Drops the right most path segment.
     *
     * @return a path ending before the segment of this path (this node's parent's path)
     * @throws JsonPathException when called on the root (empty path)
     * @see #dropFirstSegment()
     */
    @Surly
    public JsonPath dropLastSegment() {
        if ( isEmpty() )
            throw new JsonPathException( this, "Root/self path does not have a parent." );
        int size = segments.size();
        return size == 1 ? ROOT : new JsonPath( segments.subList( 0, size - 1 ) );
    }

    /**
     * @return true, when this path is the root (points to itself)
     */
    public boolean isEmpty() {
        return segments.isEmpty();
    }

    /**
     * @return the number of segments in this path, zero for the root (self)
     */
    public int size() {
        return segments.size();
    }

    public boolean startsWithArray() {
        if ( isEmpty() ) return false;
        return segments.get( 0 ).charAt( 0 ) == '[';
    }

    public boolean startsWithObject() {
        if ( isEmpty() ) return false;
        char c0 = segments.get( 0 ).charAt( 0 );
        return c0 == '.' || c0 == '{';
    }

    public int arrayIndexAtStart() {
        if ( isEmpty() ) throw new JsonPathException( this, "Root/self path does not designate an array index." );
        if ( !startsWithArray() )
            throw new JsonPathException( this, "Path %s does not start with an array.".formatted( toString() ) );
        String seg0 = segments.get( 0 );
        return parseInt( seg0.substring( 1, seg0.length() - 1 ) );
    }

    @Surly
    public String objectMemberAtStart() {
        if ( isEmpty() ) throw new JsonPathException( this, "Root/self path does not designate a object member name." );
        if ( !startsWithObject() )
            throw new JsonPathException( this, "Path %s does not start with an object.".formatted( toString() ) );
        String seg0 = segments.get( 0 );
        return seg0.charAt( 0 ) == '.' ? seg0.substring( 1 ) : seg0.substring( 1, seg0.length() - 1 );
    }

    @Override
    public String toString() {
        return String.join( "", segments );
    }

    @Override
    public boolean equals( Object obj ) {
        if (!(obj instanceof JsonPath other)) return false;
        return segments.equals( other.segments );
    }

    /**
     * @param path the path to slit into segments
     * @return splits the path into segments each starting with a character that {@link #isSegmentOpen(char)}
     * @throws JsonPathException when the path cannot be split into segments as it is not a valid path
     */
    private static List<String> splitIntoSegments( String path )
        throws JsonPathException {
        int len = path.length();
        int i = 0;
        int s = 0;
        List<String> res = new ArrayList<>();
        while ( i < len ) {
            if ( isDotSegmentOpen( path, i ) ) {
                i++; // advance past the .
                if ( i < len && path.charAt( i ) != '.' ) {
                    i++; // if it is not a dot the first char after the . is never the end
                    while ( i < len && !isDotSegmentClose( path, i ) ) i++;
                }
            } else if ( isSquareSegmentOpen( path, i ) ) {
                while ( !isSquareSegmentClose( path, i ) ) i++;
                i++; // include the ]
            } else if ( isCurlySegmentOpen( path, i ) ) {
                while ( !isCurlySegmentClose( path, i ) ) i++;
                i++; // include the }
            } else throw new JsonPathException( path,
                "Malformed path %s, invalid start of segment at position %d.".formatted( path, i ) );
            res.add( path.substring( s, i ) );
            s = i;
        }
        // make immutable
        return List.copyOf( res );
    }

    private static boolean isDotSegmentOpen( String path, int index ) {
        return path.charAt( index ) == '.';
    }

    /**
     * Dot segment: {@code .property}
     *
     * @param index into path
     * @return when it is a dot, a valid start of a curly segment or a valid start of a square segment
     */
    private static boolean isDotSegmentClose( String path, int index ) {
        return path.charAt( index ) == '.' || isCurlySegmentOpen( path, index ) || isSquareSegmentOpen( path, index );
    }

    private static boolean isCurlySegmentOpen( String path, int index ) {
        if ( path.charAt( index ) != '{' ) return false;
        // there must be a curly end before next .
        int i = index + 1;
        do {
            i = path.indexOf( '}', i );
            if ( i < 0 ) return false;
            if ( isCurlySegmentClose( path, i ) ) return true;
            i++;
        }
        while ( i < path.length() );
        return false;
    }

    /**
     * Curly segment: {@code {property}}
     *
     * @param index into path
     * @return next closing } that is directly followed by a segment start (or end of path)
     */
    private static boolean isCurlySegmentClose( String path, int index ) {
        return path.charAt( index ) == '}' && (index + 1 >= path.length() || isSegmentOpen(
            path.charAt( index + 1 ) ));
    }

    private static boolean isSquareSegmentOpen( String path, int index ) {
        if ( path.charAt( index ) != '[' ) return false;
        // there must be a curly end before next .
        int i = index + 1;
        while ( i < path.length() && path.charAt( i ) >= '0' && path.charAt( i ) <= '9' ) i++;
        return i > index + 1 && i < path.length() && isSquareSegmentClose( path, i );
    }

    /**
     * Square segment: {@code [index]}
     *
     * @param index into path
     * @return next closing ] that is directly followed by a segment start (or end of path)
     */
    private static boolean isSquareSegmentClose( String path, int index ) {
        return path.charAt( index ) == ']' && (index + 1 >= path.length() || isSegmentOpen(
            path.charAt( index + 1 ) ));
    }

    private static boolean isSegmentOpen( char c ) {
        return c == '.' || c == '{' || c == '[';
    }
}
