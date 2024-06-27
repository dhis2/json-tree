package org.hisp.dhis.jsontree;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.stream.Collectors.joining;
import static org.hisp.dhis.jsontree.Validation.NodeType.STRING;

/**
 * As defined by <a href="https://datatracker.ietf.org/doc/html/rfc6901/">RFC-6901</a>.
 *
 * @author Jan Bernitt
 * @since 1.1
 *
 * @param value a pointer expression
 */
@Validation( type = STRING, pattern = "(/((~[01])|([^/~]))*)*" )
public record JsonPointer(String value) {

    /**
     * Returns individual segments as otherwise escaped / cannot be distinguished from an unescaped / that separates
     * segments.
     *
     * @return the decoded segments of this pointer
     */
    public List<String> decode() {
        if (value.isEmpty()) return List.of();
        return Stream.of(value.substring( 1 ).split( "/" )).map( JsonPointer::decode ).toList();
    }

    private static String decode(String segment) {
        return segment.replace( "~1", "/" ).replace( "~0", "~" );
    }

    /**
     * @return this pointer as path as it is used in the {@link JsonValue} and {@link JsonNode} APIs
     */
    public String path() {
        if (value.isEmpty()) return "";
        return decode().stream().map( JsonPointer::toPath ).collect( joining());
    }

    private static String toPath(String segment) {
        if (segment.isEmpty()) return segment;
        if (segment.chars().allMatch( JsonPointer::isArrayIndex )) return "["+segment+"]";
        return "."+segment;
    }

    private static boolean isArrayIndex(int c) {
        return c == '-' || c >= '0' && c <= '9';
    }

    @Override
    public String toString() {
        return value+" = "+path();
    }

    // TODO additions: when a path ends with an index and + the value should be an array,
    // all its elements should be inserted in the target at the given index
}
