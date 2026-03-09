package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.Surly;

import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

/**
 * Represents a JSON path and the logic of how to split it into segments.
 *
 * <p>Segments are always evaluated (split) left to right. Each segment is expected to start with
 * the symbol identifying the type of segment. There are three notations:
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
 * @author Jan Bernitt
 * @since 1.1
 * @param segments Each segment represents a level in a JSON object hierarchy. This is why they are
 *     also called keys as they make the names of the object members or keys when looking at an
 *     object as a key-value map.
 */
public record JsonPath(List<Text> segments) {

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
        char c0 = path.charAt( 0 );
        if ( c0 !=  '{' && c0 != '[' && c0 != '.' ) path = "."+path;
        return new JsonPath( splitIntoSegments( Text.of( path ) ) );
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
        if (subPath.isEmpty()) return this;
        if (isEmpty()) return subPath;
        if (subPath.size() == 1) return extendedWith( subPath.segments.get( 0 ) );
        List<Text> subSegments = subPath.segments;
        int n = size();
        Text[] concat = new Text[n +subSegments.size()];
        for (int i = 0; i < n; i++) concat[i] = segments.get( i );
        for (int i = 0; i < subSegments.size(); i++) concat[n+i] = subSegments.get( i );
        return new JsonPath( List.of(concat) );
    }

    /**
     * Extends this path on the right (end)
     *
     * @param subSegment a plain object member name
     * @return a new path instance that adds the provided object member name segment to this path to create a new
     * absolute path for the same root
     */
    public JsonPath extendedWith( Text subSegment ) {
        if (isEmpty()) return new JsonPath( List.of(subSegment) );
        int n = size();
        Text[] concat = new Text[n+1];
        for (int i = 0; i < n; i++) concat[i] = segments.get( i );
        concat[n] = subSegment;
        return new JsonPath( List.of(concat));
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

    /**
     * Drops the right most path segment.
     *
     * @return a path ending before the segment of this path (this node's parent's path)
     * @throws JsonPathException when called on the root (empty path)
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

    @Override
    public String toString() {
        return segments.stream().map( this::toString ).collect( joining());
    }

    private String toString( Text seg ) {
        boolean hasCurly = seg.indexOf( '{' ) >= 0;
        boolean hasSquare = seg.indexOf( '[' ) >= 0;
        boolean hasDot = seg.indexOf( '.' ) >= 0;
        // default case: no special characters in name
        if (!hasCurly && !hasSquare && !hasDot) return "." + seg;
        // common special case: has a dot (and possibly square) => needs curly escape
        if ( !hasCurly && hasDot ) {
            checkCurlyEnd( seg, indexOfInnerCurlySegmentEnd( seg ) );
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
        checkCurlyEnd( seg, end );
        return "{"+seg+"}";
    }

    /**
     * a } at the very end is ok since escaping that again {...} makes it an invalid end
     * so then effectively there is no valid on in the escaped name
     */
    private void checkCurlyEnd(Text seg, int end) {
        if ( end > 0 && end < seg.length()-1)
            throw new JsonPathException( this,
                "Path segment %s in path %s cannot be escaped without causing a misunderstanding when split via JsonPath.of(String)".formatted(
                    seg, this ));

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
        int len = path.length();
        int i = 0;
        int s = 0;
        List<Text> res = new ArrayList<>();
        while ( i < len ) {
            if ( isDotSegmentOpen( path, i ) ) {
                i++; // advance past the .
                if ( i < len && path.charAt( i ) != '.' ) {
                    i++; // if it is not a dot the first char after the . is never a start of next segment
                    while ( i < len && !isDotSegmentClose( path, i ) ) i++;
                }
            } else if ( isSquareSegmentOpen( path, i ) ) {
                while ( !isSquareSegmentClose( path, i ) ) i++;
                i++; // include the ]
            } else if ( isCurlySegmentOpen( path, i ) ) {
                while ( !isCurlySegmentClose( path, i ) ) i++;
                i++; // include the }
            } else throw new JsonPathException( path.toString(),
                "Malformed path %s, invalid start of segment at position %d.".formatted( path, i ) );
            res.add( path.subSequence( s, i ) );
            s = i;
        }
        // make immutable
        return List.copyOf( res );
    }

    private static boolean isDotSegmentOpen( Text path, int index ) {
        return path.charAt( index ) == '.';
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
