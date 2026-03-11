package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.Surly;

import java.util.List;
import java.util.Objects;

/**
 * A JSON path is a sequence of object member names or array indexes in the order from root to the
 * target node.
 *
 * <p>An array index path is indistinguishable from a numeric object member name.
 *
 * <p>When constructed from a user input {@link String} in {@link #of(CharSequence)} the provided string
 * might require splitting into segments. Therefore, there is a syntax to indicate
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
 *
 * @param parent null in case there is no parent (1st segment does not explicitly point to empty path)
 * @param segment null in case of this being the root (empty path)
 */
public record JsonPath(JsonPath parent, Text segment) {

    /**
     * The empty path pointing to the root or self
     */
    public static final JsonPath ROOT = new JsonPath( null, null );

    /**
     * "Parse" a path {@link String} into its {@link JsonPath} form
     *
     * @param path a JSON path string
     * @return the provided path as {@link JsonPath} object
     * @throws JsonPathException when the path cannot be split into segments as it is not a valid path
     */
    public static JsonPath of( CharSequence path ) {
        if (path.isEmpty()) return ROOT;
        if (path instanceof Text t) return new JsonPath( null, t );
        //TODO write a test that checks if an object with a member using the empty string as member name works
        return splitPath( null, Text.of( path ) );
    }

    /**
     * Create a path for an array index
     *
     * @param index array index
     * @return an array index selecting path
     */
    public static JsonPath of(int index) {
        return new JsonPath(null, Text.of( index ));
    }

    public JsonPath {
        if (segment == null && parent != null)
            throw new JsonPathException(parent, "Only root can have null segment but got: "+parent );
    }

    /**
     * Extends this path on the right (end)
     *
     * @param subPath the path to add to the end of this one
     * @return a new path instance that starts with all segments of this path followed by all segments of the provided sub-path
     */
    public JsonPath extendedWith( JsonPath subPath ) {
        if (isEmpty()) return subPath;
        if (subPath.isEmpty()) return this;
        if (subPath.isHead()) return extendedWith( subPath.segment );
        // below case is complicated/slow, but it is never the case
        // unless the user manually calls it with a longer path
        JsonPath res = this;
        for (Text segment : subPath.segments())
            res = new JsonPath( res, segment );
        return res;
    }

    /**
     * Extends this path on the right (end)
     *
     * @param subSegment a plain object member name
     * @return a new path instance that adds the provided object member name segment to this path to create a new
     * absolute path for the same root
     */
    public JsonPath extendedWith( Text subSegment ) {
        if (subSegment == null) throw new NullPointerException();
        if (isEmpty()) return new JsonPath( null, subSegment );
        return new JsonPath( this, subSegment );
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

    public JsonPath extendedWith(String subPath) {
        if (subPath.isEmpty()) return this;
        Text plain = Text.of( subPath );
        // not having syntax also means it is a non-nested path (single segment)
        if (!isSyntaxPresent( plain )) return extendedWith( plain );
        return splitPath( isEmpty() ? null : this, plain );
    }

  /**
   * Drops the right most path segment.
   *
   * @return a path ending before the segment of this path (this node's parent's path)
   * @throws JsonPathException when called on the root (empty path)
   */
  @Surly
  public JsonPath parentPath() {
        if ( isEmpty() )
            throw new JsonPathException( this, "Root/self path does not have a parent." );
        return parent == null ? ROOT : parent;
    }

    /**
     * @return true, when this path is the root (points to itself)
     */
    public boolean isEmpty() {
        return segment == null;
    }

    /**
     * @return true, when this is a path with only 1 segment
     * @since 1.9
     */
    public boolean isHead() {
        return parent == null && segment != null;
    }

    /**
     * @return the number of segments in this path, zero for the root (self)
     */
    public int size() {
        if (isEmpty()) return 0;
        return parent == null ? 1 : parent.size() + 1;
    }

    @Override
    public boolean equals( Object obj ) {
        if (!(obj instanceof JsonPath other)) return false;
        if (parent == null && other.parent != null) return false;
        if (parent != null && other.parent == null) return false;
        if (segment == null && other.segment != null) return false;
        if (segment != null && other.segment == null) return false;
        return Objects.equals( parent, other.parent ) && Objects.equals( segment, other.segment );
    }

    @Override
    public int hashCode() {
        if (isEmpty()) return 0;
        int hash = parent == null ? 1 : parent.hashCode();
        return hash * 31 + segment.hashCode();
    }

    @Override
    public String toString() {
        if (isEmpty()) return "";
        if (parent == null) return escape(segment, this);
        return parent + escape(segment, this);
    }

    public List<Text> segments() {
        if (isEmpty()) return List.of();
        if (parent == null) return List.of(segment);
        int n = size();
        JsonPath p = this;
        Text[] res = new Text[n];
        for (int i = n-1; i >= 0; i--) {
            res[i] = p.segment;
            p = p.parent;
        }
        return List.of(res);
    }

    /**
     * Escapes a plain segment if required.
     *
     * @param literally the plain segment name to escape in syntax
     * @return the normalized, potentially escaped segment name
     * @throws JsonPathException in case the segment is impossible to represent without being
     *     misinterpreted when converting it back using {@link JsonPath#of(CharSequence)}
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
            if (context == null) context = new JsonPath(null, seg);
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
    private static JsonPath splitPath( JsonPath parent, Text path )
        throws JsonPathException {
        // fast path when there is no syntax present (plain name)
        if (!isSyntaxPresent(path)) return new JsonPath(parent, path);
        // slow path
        int len = path.length();
        int i = 0;
        int start = 0;
        int end = 0;
        JsonPath cur = parent;
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
            } else throw new JsonPathException( cur,
                "Malformed path %s, invalid start of segment at position %d.".formatted( path, i ) );
            cur = new JsonPath( cur, path.subSequence( start, end ) );
        }
        // make immutable
        return cur;
    }

    public static boolean isSyntaxPresent( Text path ) {
        return path.contains( '.' ) || path.contains( '{' ) || path.contains( '[' );
    }

    public static boolean isSyntaxPresent( String path ) {
        return path.indexOf( '.' ) >= 0 || path.indexOf( '{' ) >= 0 || path.indexOf( '[' ) >= 0;
    }

    public static boolean isSyntaxIndicator(char ch) {
        return ch == '.' || ch == '{' || ch == '[';
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
