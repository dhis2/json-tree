package org.hisp.dhis.jsontree;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static java.lang.Character.highSurrogate;
import static java.lang.Character.isBmpCodePoint;
import static java.lang.Character.lowSurrogate;
import static java.lang.Math.max;
import static java.lang.Math.min;

/**
 * Utility class for {@code char[]} based helper functions.
 *
 * @author Jan Bernitt
 * @since 1.9
 */
final class Chars {

    /*
    Parsing JSON Numbers
     */

    static Number parseNumber(char[] buffer, int offset, int length) {
        int endInteger = skipInteger(buffer, offset, length);
        if (endInteger > 0) {
            int digits = endInteger - offset;
            if (digits < 9 || digits < 10 && (buffer[offset] == '-' || buffer[offset] == '+'))
                return parseInt(buffer, offset, digits);
            long n = parseLong(buffer, offset, digits);
            if ( n < Integer.MAX_VALUE && n > Integer.MIN_VALUE ) return (int) n;
            return n;
        }
        double number = parseDouble( buffer, offset, length );
        if ( number % 1 != 0d ) return number;
        long n = (long) number;
        if ( n < Integer.MAX_VALUE && n > Integer.MIN_VALUE ) return (int) n;
        return n;
    }

    static double parseDouble(char[] buffer, int offset, int length) {
        if (!isExactDouble( buffer, offset, length ))
            return Double.parseDouble( new String( buffer, offset, length ) );
        long n = 0;
        long div = 1;
        int i = offset;
        int end = offset+length;
        boolean neg = buffer[i] == '-';
        if (neg || buffer[i] == '+') i++;
        boolean fraction = false;
        for (; i < end; i++) {
            char c = buffer[i];
            if (c == '.') {
                fraction=true;
            } else { // must be digit
                n = n * 10 + (c - '0');
                if (fraction) div *= 10;
            }
        }
        double res = (double) n / (double) div;
        return neg ? -res : res;
    }

    /**
     * @return true if the number in buffer[offset..offset+length-1] can be parsed
     * exactly using the long-division method (i.e., total significant digits ≤ 15).
     */
    static boolean isExactDouble(char[] buffer, int offset, int length) {
        int i = offset;
        int end = offset+length;
        int n = 0;
        if (buffer[i] == '-' || buffer[i] == '+') i++;
        while ( i < end && buffer[i] == '0' ) i++; // ignore leading 0
        for (; i < end; i++) {
            char c = buffer[i];
            if (isDigit( c )) {
                n++;
            } else if(c != '.') return false;
        }
        return n <= 15;
    }

    static int skipInteger(char[] buffer, int offset, int length) {
        int i = offset;
        int end = offset+length;
        if (buffer[i] == '-' || buffer[i] == '+') i++;
        for (; i < end; i++)
            if (!isDigit( buffer[i] ))  {
                // when number is "ddd.0" we return index of . as the integer end
                // as the numeric value is an integer
                if (buffer[i] == '.' && i+2 == end && buffer[i+1] == '0') return i;
                return -1;
            }
        return i;
    }

    static int parseInt(char[] buffer, int offset, int length) {
        boolean neg = buffer[offset] == '-';
        int i = offset;
        if (neg || buffer[offset] == '+') i++;
        int n = 0;
        int end = offset + length;
        for (; i < end; i++) {
            n *= 10;
            n += buffer[i] - '0';
        }
        return neg ? -n : n;
    }

    static long parseLong(char[] buffer, int offset, int length) {
        boolean neg = buffer[offset] == '-';
        int i = offset;
        if (neg || buffer[offset] == '+') i++;
        long n = 0;
        int end = offset + length;
        for (; i < end; i++) {
            n *= 10;
            n += buffer[i] - '0';
        }
        return neg ? -n : n;
    }

    private static boolean isDigit( char c ) {
        return c >= '0' && c <= '9';
    }

    /*
    Parsing JSON encoded Strings
     */

    static Text parseString( char[] json, int offset ) {
        int length = 0;
        int index = offset;
        index = expectChar( json, index, '"' );
        while ( index < json.length ) {
            char c = json[index++];
            if ( c == '"' ) {
                // found the end (if escaped we would have hopped over)
                if (length == index-1-offset) // no escaping used
                    return Text.of( json, offset+1, length );
                // did use escaping...
                return parseStringWithEscaping( json, offset+1, length );
            } else if ( c == '\\' ) {
                expectEscapedCharacter( json, index );
                // hop over escaped char or unicode
                if (json[index] == 'u') {
                    int cp = parseCodePoint( json, index+1 );
                    if (!isBmpCodePoint( cp )) length++; // needs 2
                    index += 4; // XXXX
                }
                index += 1; // u or escaped char
            } else if ( c < ' ' ) {
                throw new JsonFormatException( json, index - 1,
                    "Control code character is not allowed in JSON string but found: " + (int) c );
            }
            length++;
        }
        // throws...
        expectChar( json, index, '"' );
        throw new JsonFormatException( "Invalid string" );
    }

    /**
     * @implNote When this runs we already know we find the end of the string, so some checks
     * have been removed. The only checks required are code point decoding issues.
     */
    private static Text parseStringWithEscaping(char[] json, int offset, int length) {
        char[] text = new char[length];
        int i = 0;
        int index = offset;
        while (index < json.length) {
            char c = json[index++];
            if (c == '"') {
                // found the end (if escaped we would have hopped over)
                return Text.of(text, 0, length);
            }
            if (c == '\\') {
                switch (json[index++]) {
                    case 'u' -> { // unicode uXXXX
                        int cp = parseCodePoint(json, index);
                        if ( isBmpCodePoint(cp)) {
                            text[i++] = (char) cp;
                        } else {
                            text[i++] = highSurrogate(cp);
                            text[i++] = lowSurrogate(cp);
                        }
                        index += 4; // u we already skipped
                    }
                    case '\\' -> text[i++] = '\\';
                    case '/' -> text[i++] = '/';
                    case 'b' -> text[i++] = '\b';
                    case 'f' -> text[i++] = '\f';
                    case 'n' -> text[i++] = '\n';
                    case 'r' -> text[i++] = '\r';
                    case 't' -> text[i++] = '\t';
                    case '"' -> text[i++] = '"';
                    default -> throw new JsonFormatException(json, index, '?');
                }
            } else {
                text[i++] = c;
            }
        }
        // throws...
        expectChar( json, index, '"' );
        throw new JsonFormatException( "Invalid string" );
    }

    private static int parseCodePoint(char[] json, int offset) {
        if (offset + 3 >= json.length)
            throw new JsonFormatException("Insufficient characters for code point at index " + offset);
        int cp = 0;
        for (int i = 0; i < 4; i++) {
            char c = json[offset + i];
            int digit = switch ( c ) {
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> c - '0';
                case 'a', 'b', 'c', 'd', 'e', 'f' -> c - 'a' + 10;
                case 'A', 'B', 'C', 'D', 'E', 'F' -> c - 'A' + 10;
                default ->
                    throw new JsonFormatException("Invalid hexadecimal digit: '" + c + "' at index " + (offset + i));
            };
            cp = (cp << 4) | digit; // equivalent to cp = cp * 16 + digit
        }
        return cp;
    }

    /*
    Error handling
     */

    static int expectChars( char[] json, int offset, CharSequence expected ) {
        int length = expected.length();
        for ( int i = 0; i < length; i++ ) {
            expectChar( json, offset + i, expected.charAt( i ) );
        }
        return offset + length;
    }

    static int expectDigit( char[] json, int offset) {
        if ( offset >= json.length )
            throw new JsonFormatException( "Expected character but reached EOI: " + getEndSection( json, offset ) );
        if ( !isDigit( json[offset] ))
            throw new JsonFormatException( json, offset, '#' );
        return offset + 1;
    }

    static int expectChar( char[] json, int offset, char expected ) {
        if ( offset >= json.length )
            throw new JsonFormatException( "Expected " + expected + " but reach EOI: " + getEndSection( json, offset ) );
        if ( json[offset] != expected )
            throw new JsonFormatException( json, offset, expected );
        return offset + 1;
    }

    static void expectEscapedCharacter( char[] json, int offset ) {
        if ( offset >= json.length )
            throw new JsonFormatException(
                "Expected escaped character but reached EOI: " + getEndSection( json, offset ) );
        if ( !isEscapableCharacter( json[offset] ) )
            throw new JsonFormatException( json, offset, "Illegal escaped string character: " + json[offset] );
    }

    private static boolean isEscapableCharacter( char c ) {
        return c == '"' || c == '\\' || c == '/' || c == 'b' || c == 'f' || c == 'n' || c == 'r' || c == 't'
            || c == 'u';
    }

    static void expectEndOfBuffer(char[] json, int offset ) {
        if ( json.length > offset ) {
            throw new JsonFormatException(
                "Unexpected input after end of root value: " + getEndSection( json, offset ) );
        }
    }

    private static String getEndSection( char[] json, int offset ) {
        return new String( json, max( 0, min( json.length, offset ) - 20 ), min( 20, json.length ) );
    }

    /*
    Reading inputs to char[]
     */

    /**
     * @implNote With lazy parsing one precondition is that we need to have the entire input in memory.
     * Therefore, the general approach to IO is not to stream or buffer but to get the JSON
     * into memory as efficient as possible. Mainly this is about avoiding extra intermediate
     * representations and short-lived objects during the charset decoding.
     *
     * @param file a JSON file
     * @param encoding the encoding assumed
     * @return the character in the file
     */
    static char[] from(Path file, Charset encoding ) {
        try {
            byte[] src = Files.readAllBytes(file);
            if (StandardCharsets.UTF_8.equals( encoding )) return fromUTF8( src );
            if (StandardCharsets.ISO_8859_1.equals( encoding )) return fromIso88591( src );
            return new String(src, encoding).toCharArray();
        } catch ( IOException e ) {
            throw new UncheckedIOException( e );
        }
    }

    private static char[] fromIso88591(byte[] src) {
        char[] dest = new char[src.length];
        for (int i = 0; i < src.length; i++)
            dest[i] = (char) (src[i] & 0xFF); // ISO‑8859‑1 / Latin‑1
        return dest;
    }

    private static char[] fromUTF8( byte[] src) {
        char[] dest = new char[src.length];
        int offset = 0;

        for (int i = 0; i < src.length; ) {
            int b = src[i++] & 0xFF;            // treat as unsigned
            if (b < 0x80) {                     // 0xxxxxxx (ASCII)
                dest[offset++] = (char) b;
            } else if ((b & 0xE0) == 0xC0) {    // 110xxxxx → 2 bytes
                int codePoint = ((b & 0x1F) << 6) | (src[i++] & 0x3F);
                dest[offset++] = (char) codePoint;
            } else if ((b & 0xF0) == 0xE0) {    // 1110xxxx → 3 bytes
                int codePoint = ((b & 0x0F) << 12) | ((src[i++] & 0x3F) << 6) | (src[i++] & 0x3F);
                dest[offset++] = (char) codePoint;
            } else if ((b & 0xF8) == 0xF0) {    // 11110xxx → 4 bytes (supplementary)
                int codePoint = ((b & 0x07) << 18) | ((src[i++] & 0x3F) << 12) |
                    ((src[i++] & 0x3F) << 6) | (src[i++] & 0x3F);
                // Convert to surrogate pair
                codePoint -= 0x10000;
                dest[offset++] = (char) (0xD800 | (codePoint >> 10));
                dest[offset++] = (char) (0xDC00 | (codePoint & 0x3FF));
            } else {
                // Invalid UTF‑8 – you may decide to insert a replacement character
                dest[offset++] = '�'; // replacement character
            }
        }
        // over-allocated slots become space
        if (offset < dest.length)
            Arrays.fill(dest, offset, dest.length, ' ');
        return dest;
    }
}
