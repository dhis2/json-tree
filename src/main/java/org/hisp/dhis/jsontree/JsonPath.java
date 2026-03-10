package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.Surly;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

/**
 * A JSON path is a sequence of object member names or array indexes in the order from root to the
 * target node.
 *
 * <p>An array index path is indistinguishable from a numeric object member name.
 *
 * <p>When constructed from a user input {@link String} in {@link #of(String)} the provided string
 * might require splitting into {@link #segments()}. Therefore, there is a syntax to indicate
 * segments. The most common form is the {@code .}-syntax where dot marks the start of a segment.
 * The initial segment can omit the leading dot.
 *
 * <p>There are three notations:
 *
 * <ul>
 *   <li>dot object member access: {@code .property}
 *   <li>curly bracket object member access: {@code {property}}
 *   <li>square bracket array index access: {@code [index]}
 * </ul>
 *
 * <p>A segment is only identified as such if its open and closing symbol is found. That means an
 * opening bracket without a closing one is not a segment start symbol and understood literally.
 * Similarly, an opening bracket were the closing bracket is not found before another opening
 * bracket is also not a start symbol and also understood literally. Literally means it becomes part
 * of the property name that started further left. In the same manner an array index access is only
 * understood as such if the string in square brackets is indeed an integer number. Otherwise, the
 * symbols are understood literally.
 *
 * <p>These rules are chosen to have maximum literal interpretation while providing a way to encode
 * paths that contain notation symbols. A general escaping mechanism is avoided as this would force
 * users to always encode and decode paths just to make a few corner cases work which are not
 * possible with the chosen compromise.
 *
 * <p>Inputs that might require escaping of a segment can do so using {@link #escape(Text)}.
 *
 * @author Jan Bernitt
 * @since 1.1
 * @param segments The segments exactly as they occur in the object hierarchy of the JSON document
 *     they should match or where they have been extracted from
 * @param size number of segments used from the given list (to drop tails without rebuilding lists)
 */
public record JsonPath(List<Text> segments, int size) {

    /**
     * A path pointing to the root or self
     */
    public static final JsonPath ROOT = new JsonPath( List.of(), 0 );

    /**
     * "Parse" a path {@link String} into its {@link JsonPath} form
     *
     * @param path a JSON path string
     * @return the provided path as {@link JsonPath} object
     * @throws JsonPathException when the path cannot be split into segments as it is not a valid path
     */
    public static JsonPath of( String path ) {
        if (path.isEmpty()) return ROOT;
        List<Text> segments = splitIntoSegments( Text.of( path ) );
        return new JsonPath( segments, segments.size() );
    }

    /**
     * Create a path for an array index
     *
     * @param index array index
     * @return an array index selecting path
     */
    public static JsonPath of(int index) {
    return new JsonPath(List.of(Text.of( index )), 1);
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
        if (isEmpty()) return subPath;
        return extendedWith( subPath.segments );
    }

    public JsonPath extendedWith( List<Text> subSegments ) {
        if (isEmpty()) return new JsonPath( subSegments, subSegments.size() );
        if ( subSegments.isEmpty()) return this;
        if ( subSegments.size() == 1) return extendedWith( subSegments.get( 0 ) );
        int n = size;
        Text[] concat = new Text[n + subSegments.size()];
        for (int i = 0; i < n; i++) concat[i] = segments.get( i );
        for ( int i = 0; i < subSegments.size(); i++) concat[n+i] = subSegments.get( i );
        return new JsonPath( List.of( concat ), concat.length );
    }

    /**
     * Extends this path on the right (end)
     *
     * @param subSegment a plain object member name
     * @return a new path instance that adds the provided object member name segment to this path to create a new
     * absolute path for the same root
     */
    public JsonPath extendedWith( Text subSegment ) {
        if (isEmpty()) return new JsonPath( List.of(subSegment), 1 );
        int n = size;
        Text[] concat = new Text[n+1];
        for (int i = 0; i < n; i++) concat[i] = segments.get( i );
        concat[n] = subSegment;
        return new JsonPath( List.of(concat), size+1);
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
        return extendedWith(Text.of(index));
    }

    //TODO unclear if this is really needed
    public JsonPath extendedWith(String path) {
        Text plain = Text.of( path );
        // not having syntax also means it is a non-nested path (single segment)
        if (!isSyntaxPresent( plain )) return extendedWith( plain );
        return extendedWith( splitIntoSegments( plain ) );
    }

  /**
   * Drops the right most path segment.
   *
   * @implNote getting the parent path is an essential step in many operations which is why it was
   *     optimized to reuse the same list of segments but to use the {@link #size} limits to
   *     restrict the sublist that is considered.
   * @return a path ending before the segment of this path (this node's parent's path)
   * @throws JsonPathException when called on the root (empty path)
   */
  @Surly
  public JsonPath parentPath() {
        if ( isEmpty() )
            throw new JsonPathException( this, "Root/self path does not have a parent." );
        if (size == 1) return ROOT;
        return new JsonPath( segments, size-1 );
    }

    /**
     * @return true, when this path is the root (points to itself)
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * @return the number of segments in this path, zero for the root (self)
     */
    public int size() {
        return size;
    }

    @Override
    public boolean equals( Object obj ) {
        if (!(obj instanceof JsonPath other)) return false;
        if (size != other.size) return false;
        for (int i = 0; i < size; i++)
            if (!segments.get( i ).equals( other.segments.get( i ) )) return false;
        return true;
    }

    @Override
    public int hashCode() {
        if (isEmpty()) return 0;
        if (size == 1) return segments.get( 0 ).hashCode();
        int hash = 1;
        for (int i = 0; i < size; i++)
            hash = hash * 31 + segments.get( i ).hashCode();
        return hash;
    }

    @Override
    public String toString() {
        if (isEmpty()) return "";
        if (size == 1) return escape(segments.get(0), this);
        return segments.stream().limit( size ).map( seg -> escape( seg, this )).collect( joining());
    }

    /**
     * Escapes a plain segment if required.
     *
     * @param literally the plain segment name to escape in syntax
     * @return the normalized, potentially escaped segment name
     * @throws JsonPathException in case the segment is impossible to represent without being
     *     misinterpreted when converting it back using {@link JsonPath#of(String)}
     * @since 1.9
     */
    public static String escape(Text literally) throws JsonPathException {
        return escape( literally, null );
    }

    private static String escape( Text seg, JsonPath context ) throws JsonPathException {
        boolean hasCurly = seg.indexOf( '{' ) >= 0;
        boolean hasSquare = seg.indexOf( '[' ) >= 0;
        boolean hasDot = seg.indexOf( '.' ) >= 0;
        // default case: no special characters in name
        if (!hasCurly && !hasSquare && !hasDot) return "." + seg;
        // common special case: has a dot (and possibly square) => needs curly escape
        if ( !hasCurly && hasDot ) {
            checkCurlyEnd( seg, indexOfInnerCurlySegmentEnd( seg ), context );
            return "{"+seg+"}";
        }
        // common special case: has a square but no curly or dot => only needs escaping when open + close square
        if ( !hasCurly ) return hasInnerSquareSegment( seg ) ? "{"+seg+"}" : "." + seg;
        // edge special case: [...] but only opens at the start => dot works
        if ( !hasDot && seg.charAt( 0 ) == '[' && seg.indexOf( '[', 1 ) < 0 ) return "."+seg;
        // edge special case: {...} but only opens at the start => dot works
        if ( !hasDot && seg.charAt( 0 ) == '{' && seg.indexOf( '{', 1 ) < 0 ) return "."+seg;
        // special case: has curly open but no valid curly close => plain or dot works
        int end = indexOfInnerCurlySegmentEnd( seg );
        if (!hasDot && end < 1) return seg.charAt( 0 ) == '{' ? "."+seg : seg.toString();
        checkCurlyEnd( seg, end, context );
        return "{"+seg+"}";
    }

    /**
     * a } at the very end is ok since escaping that again {...} makes it an invalid end so then
     * effectively there is no valid on in the escaped name
     */
    private static void checkCurlyEnd(Text seg, int end, JsonPath context) {
        if (end > 0 && end < seg.length() - 1) {
            if (context == null) context = new JsonPath(List.of(seg), 1);
            throw new JsonPathException(context, "Path segment %s cannot be escaped".formatted(seg));
        }
    }

    private static boolean hasInnerSquareSegment(Text seg) {
        int i = seg.indexOf( '[', 1 );
        while ( i >= 0 ) {
            if (isSquareSegmentOpen( seg, i )) return true;
            i = seg.indexOf( '[', i+1 );
        }
        return false;
    }

    /**
     * Searches for the end since possibly a curly escape is used and a valid inner curly end would be misunderstood.
     */
    private static int indexOfInnerCurlySegmentEnd(Text seg) {
        int i = seg.indexOf( '}', 1 );
        while ( i >= 0 ) {
            if (isCurlySegmentClose( seg, i )) return i;
            i = seg.indexOf( '}', i+1 );
        }
        return -1;
    }

    /**
     * @param path the path to slit into segments
     * @return splits the path into segments each starting with a character that {@link #isSegmentOpen(char)}
     * @throws JsonPathException when the path cannot be split into segments as it is not a valid path
     */
    private static List<Text> splitIntoSegments( Text path )
        throws JsonPathException {
        // fast path when there is no syntax present (plain name)
        if (!isSyntaxPresent( path )) return List.of(path);
        // slow path
        int len = path.length();
        int i = 0;
        int start = 0;
        int end = 0;
        List<Text> res = new ArrayList<>();
        while ( i < len ) {
            char c = path.charAt( i );
            if ( c == '[' && isSquareSegmentOpen( path, i ) ) {
                i++;
                start = i;
                while (!isSquareSegmentClose( path, i ) ) i++;
                end = i;
                i++; // most past ]
            } else if (c == '{' && isCurlySegmentOpen( path, i ) ) {
                i++;
                start = i;
                while ( !isCurlySegmentClose( path, i ) ) i++;
                end = i;
                i++; // most past }
            } else if ( c == '.' || i == 0 ) {
                if (c == '.') i++;
                start = i;
                if ( i < len && path.charAt( i ) != '.' ) {
                    i++; // if it is not a dot the first char after the . is never a start of next segment
                    while ( i < len && !isDotSegmentClose( path, i ) ) i++;
                }
                end = i;
                if (start == end) start -= 1;
            } else throw new JsonPathException( new JsonPath(res, res.size()),
                "Malformed path %s, invalid start of segment at position %d.".formatted( path, i ) );
            res.add( path.subSequence( start, end ) );
        }
        // make immutable
        return List.copyOf( res );
    }

    public static boolean isSyntaxPresent( Text path ) {
        return path.contains( '.' ) || path.contains( '{' ) || path.contains( '[' );
    }

    public static boolean isSyntaxPresent( String path ) {
        return path.indexOf( '.' ) >= 0 || path.indexOf( '{' ) >= 0 || path.indexOf( '[' ) >= 0;
    }

    /**
     * Dot segment: {@code .property}
     *
     * @param index into path
     * @return when it is a dot, a valid start of a curly segment or a valid start of a square segment
     */
    private static boolean isDotSegmentClose( Text path, int index ) {
        return path.charAt( index ) == '.' || isCurlySegmentOpen( path, index ) || isSquareSegmentOpen( path, index );
    }

    private static boolean isCurlySegmentOpen( Text path, int index ) {
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
    private static boolean isCurlySegmentClose( Text path, int index ) {
        return path.charAt( index ) == '}' && (index + 1 >= path.length() || isSegmentOpen(
            path.charAt( index + 1 ) ));
    }

    private static boolean isSquareSegmentOpen( Text path, int index ) {
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
    private static boolean isSquareSegmentClose( Text path, int index ) {
        return path.charAt( index ) == ']' && (index + 1 >= path.length() || isSegmentOpen(
            path.charAt( index + 1 ) ));
    }

    private static boolean isSegmentOpen( char c ) {
        return c == '.' || c == '{' || c == '[';
    }
}
