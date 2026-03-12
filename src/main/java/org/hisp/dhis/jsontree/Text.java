package org.hisp.dhis.jsontree;

import java.util.Arrays;

/**
 * A {@link String}-like API without requiring to manifest a {@link String} instance.
 *
 * @implNote The {@link Text} abstraction exists to enable views without defensive copying to allow
 *     for a very small memory overhead of sub-sequences found within a JSON document. Most
 *     prominent the JSON string node values and the names of object properties.
 *     This takes advantage of the fact that most such strings do not need JSON level decoding
 *     and can be direct views (slices) of the JSON document itself.
 * @author Jan Bernitt
 * @since 1.9
 */
public interface Text extends CharSequence, Comparable<Text> {

    /**
     * @see String#indexOf(int)
     */
    default int indexOf(char ch) {
        return indexOf( ch, 0 );
    }

    /**
     * @see String#indexOf(int, int)
     */
    default int indexOf(char ch, int startIndex) {
        return indexOf( ch, startIndex, length() );
    }

    default int indexOf(char ch, int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++)
            if (charAt( i ) == ch) return i;
        return -1;
    }

    /**
     * @see String#lastIndexOf(int)
     */
    default int lastIndexOf(char ch) {
        return lastIndexOf(ch, length() - 1);
    }

    /**
     * @see String#lastIndexOf(int, int)
     */
    default int lastIndexOf(char ch, int startIndex) {
        return lastIndexOf( ch, startIndex, 0 );
    }

    default int lastIndexOf(char ch, int startIndex, int endIndex) {
        for (int i = startIndex; i >= endIndex; i--)
            if (charAt( i ) == ch) return i;
        return -1;
    }

    default boolean contains(char ch) {
        return indexOf( ch ) >= 0;
    }

    /**
     * @see String#contains(CharSequence)
     */
    default boolean contains(CharSequence infix) {
        return indexOf( infix ) >= 0;
    }

    /**
     * @see String#indexOf(String)
     */
    default int indexOf(CharSequence infix) {
        return indexOf( infix, 0);
    }

    /**
     * @see String#indexOf(String, int)
     */
    default int indexOf(CharSequence infix, int startIndex) {
        return indexOf( infix, startIndex, length() );
    }

    /**
     * @see String#indexOf(String, int, int) 
     */
    default int indexOf(CharSequence infix, int startIndex, int endIndex) {
        if (startIndex < 0) return -1;
        if (infix.isEmpty()) return 0;
        // find + match
        int fLen = infix.length();
        int mLen = endIndex - startIndex;
        if (fLen > mLen) return -1;
        char f0 = infix.charAt( 0 );
        int m0 = indexOf( f0, startIndex, endIndex );
        while (m0 >=0 && m0+fLen <= endIndex)  {
            if (regionMatches( m0, infix )) return m0;
            m0 = indexOf( f0, m0+1, endIndex );
        }
        return -1;
    }

    /**
     * @see String#lastIndexOf(String)
     */
    default int lastIndexOf(CharSequence infix) {
        return lastIndexOf( infix, length()-1, 0 );
    }

    /**
     * @see String#lastIndexOf(String, int)
     */
    default int lastIndexOf(CharSequence infix, int startIndex) {
        return lastIndexOf( infix, startIndex, 0 );
    }

    default int lastIndexOf(CharSequence infix, int startIndex, int endIndex) {
        if (startIndex < 0) return -1;
        if (infix.isEmpty()) return 0;
        // find + match
        int fLen = infix.length();
        int mLen = startIndex - endIndex + 1;
        if (fLen > mLen) return -1;
        char f0 = infix.charAt( 0 );
        int m0 = lastIndexOf( f0, startIndex, endIndex );
        while (m0 >= endIndex)  {
            if (regionMatches( m0, infix )) return m0;
            m0 = lastIndexOf( f0, m0-1, endIndex );
        }
        return -1;
    }

    /**
     * @see String#startsWith(String)
     */
    default boolean startsWith(CharSequence prefix) {
        return startsWith( prefix, 0 );
    }

    /**
     * @see String#startsWith(String, int)
     */
    default boolean startsWith(CharSequence prefix, int startIndex) {
        return regionMatches(startIndex, prefix);
    }

    /**
     * @see String#endsWith(String)
     */
    default boolean endsWith(CharSequence suffix) {
        return regionMatches(length() - suffix.length(), suffix);
    }

    /**
     * @see String#contentEquals(CharSequence)
     */
    default boolean contentEquals(CharSequence text) {
        if (text.isEmpty()) return isEmpty();
        int len = text.length();
        if ( len != length()) return false;
        for (int i = 0; i < len; i++)
            if (charAt( i ) != text.charAt( i )) return false;
        return true;
    }

    default boolean regionMatches(int startIndex, CharSequence sample) {
        return regionMatches( startIndex, sample, 0, sample.length() );
    }

    /**
     * @see String#regionMatches(int, String, int, int)
     */
    default boolean regionMatches(int startIndex, CharSequence sample, int offset, int len) {
        if (startIndex < 0
            || offset < 0
            || startIndex > length() - len
            || offset > sample.length() - len) return false;
        if (len <= 0) return true;
        for (int i = 0; i < len; i++)
            if (charAt( startIndex + i ) != sample.charAt( offset + i ))
                return false;
        return true;
    }

    @Override
    default Text subSequence( int start, int end ) {
        checkSubSequence( start, end, length() );
        int len = end - start;
        if (start == 0 && end == len) return this;
        char[] buffer = new char[len];
        for (int i = 0; i < len; i++) buffer[i] = charAt( start+i );
        return of(buffer, 0, len);
    }

    /**
     * @see String#toCharArray()
     */
    default char[] toCharArray() {
        char[] arr = new char[length()];
        for (int i = 0; i < arr.length; i++)
            arr[i] = charAt( i );
        return arr;
    }

    /**
     * @return true, if the text is a signed or unsigned integer value
     */
    default boolean isInt() {
        if (isEmpty()) return false;
        int i = 0;
        if (charAt( 0 ) == '-' || charAt( 0 ) == '+') i++;
        for (; i < length(); i++)
            if (charAt( i ) < '0' || charAt( i ) > '9') return false;
        return true;
    }

    /**
     * @return this text parsed to an integer
     * @throws NumberFormatException in case the text is not a valid integer
     */
    default int parseInt() {
        if (!isInt()) throw new NumberFormatException("Not a number: "+this);
        boolean neg = charAt( 0 ) == '-';
        int i = 0;
        if (neg || charAt( 0 ) == '+') i++;
        int n = 0;
        int end = length();
        for (; i < end; i++) {
            n *= 10;
            n += charAt( i ) - '0';
        }
        return neg ? -n : n;
    }

    @Override
    default int compareTo( Text other ) {
        int n = Math.min( length(), other.length() );
        for (int k = 0; k < n; k++) {
            int res = charAt(k) - other.charAt(k);
            if (res != 0) return res;
        }
        return length() - other.length();
    }

    /**
     * @implNote All classes implementing {@link Text} must implement {@code equals} using the
     *     equivalent of {@link #contentEquals(CharSequence)} for instances of {@link Text}, arguments
     *     not being {@link Text}s are not equal.
     */
    boolean equals(Object obj);

    /**
     * @implNote All classes implementing {@link Text} must implement {@code hashCode} using {@link
     *     Text#hashCode(Text)}.
     */
    int hashCode();

    static Text copyOf(Text text) {
        return of(text.toCharArray(), 0, text.length());
    }

    /**
     * @param text the source to convert to a {@link Text}
     * @return the given text as {@link Text} (no copy if it can be avoided)
     * @see #copyOf(Text) to force a copy
     * @since 1.9
     */
    static Text of(CharSequence text) {
        if (text instanceof Text t) return t;
        if (text instanceof String s) return of(s);
        int len = text.length();
        char[] buffer = new char[len];
        for (int i = 0; i < len; i++) buffer[i] = text.charAt( i );
        return of(buffer, 0, len );
    }

    /**
     * @return The string's characters as {@link Text} mainly for user space API uses, tests and such.
     *     Internally it should be avoided to use this whenever a {@link Text} can be received from
     *     the {@link JsonNode} API instead.
     */
    static Text of(String text) {
        char[] buffer = text.toCharArray();
        return of( buffer, 0, buffer.length);
    }

    /**
     * @apiNote This method will not make a defensive copy of the given buffer as the main use case is
     *     to use {@link Text} as a shallow "pointer" to slices on the same underlying #buffer.
     *     Only arrays that are not mutated any longer should be passed.
     *
     * @since 1.9
     * @param buffer the characters viewed
     * @param offset first character in the buffer included in the view
     * @param length number of characters included in the view from the offset
     * @return A read-only view of the slice starting at offset (no defensive copy)
     */
    static Text of(char[] buffer, int offset, int length) {
        record Slice(char[] buffer, int offset, int length) implements Text {

            @Override public char charAt( int index ) {
                return buffer[offset + index];
            }

            @Override
            public Slice subSequence( int start, int end ) {
                checkSubSequence( start, end, length );
                if (start == 0 && end == length) return this;
                return new Slice(buffer, offset+start, end - start);
            }

            @Override
            public boolean isInt() {
                if (buffer == Cache._100_TO_999) return true;
                return Text.super.isInt();
            }

            @Override
            public int parseInt() {
                if (!isInt()) throw new NumberFormatException("Not a number: "+this);
                return Chars.parseInt( buffer, offset, length );
            }

            @Override
            public boolean equals( Object obj ) {
                if (this == obj) return true;
                if (!(obj instanceof Text text)) return false;
                return contentEquals( text );
            }

            @Override
            public int hashCode() {
                return Text.hashCode(this);
            }

            @Override public boolean contentEquals( CharSequence text ) {
                if (text instanceof Slice other) {
                    // optimization: avoid virtual method calls by comparing arrays directly
                    if (length != other.length) return false;
                    if (buffer == other.buffer && offset == other.offset) return true;
                    for (int i = 0; i < length; i++)
                        if (buffer[offset+i] != other.buffer[i+other.offset]) return false;
                    return true;
                }
                return Text.super.contentEquals(text);
            }

            @Override public char[] toCharArray() {
                return
                    Arrays.copyOfRange( buffer, offset, offset+length);
            }

            @Override
            public String toString() {
                return length == 1
                    ? String.valueOf( buffer[offset] )
                    : new String(buffer, offset, length);
            }
        }
        return new Slice( buffer, offset, length );
    }

    /**
     * @apiNote should be considered private
     * @implNote A private cache for digits of 0-999.
     * It saves on allocation of a character buffer and increases the chance
     * of the characters already being in CPU cache as we reuse the same
     * memory region for small-ish indexes.
     */
    record Cache() {
        private static final char[] _100_TO_999 = new char[900*3];
        static {
            int j = 0;
            for (int i = 100; i < 1000; i++) {
                _100_TO_999[j++] = (char)('0'+i/100);
                _100_TO_999[j++] = (char)('0'+(i%100/10));
                _100_TO_999[j++] = (char)('0'+i%10);
            }
        }
    }

    /**
     * @return An array index as {@link Text} (like used in a {@link JsonPath} segment)
     */
    static Text of(int index) {
        if (index >= 0) {
            if (index < 10) return of(Cache._100_TO_999, index * 3 + 2, 1);
            if (index < 100) return of(Cache._100_TO_999, index * 3 + 1, 2);
            if (index < 1000) return of(Cache._100_TO_999, (index - 100) * 3, 3);
        }
        boolean neg = index < 0;
        int rest = Math.abs(index);
        int n0 = neg ? 1 : 0;
        int n = n0;
        while (rest > 0) {
            n++;
            rest /= 10;
        }
        char[] digits = new char[n];
        if (neg) digits[0] = '-';
        rest = Math.abs(index);
        for (int i = n - 1; i >= n0; i--) {
            digits[i] = (char) ('0' + (rest % 10));
            rest /= 10;
        }
        return of(digits, 0, digits.length);
    }

    private static void checkSubSequence( int start, int end, int length ) {
        if ( start < 0 || end < 0 || end > length || start > end )
            throw new IndexOutOfBoundsException("expected: 0 <= start(" + start + ") <= end (" + end + ") <= length(" + length + ')');
    }

    /**
     * @apiNote The exact hash code algorithm does not need to be specified as all classes
     *     implementing {@link Text} must use this function to compute the hash code. As long as the
     *     algorithm is deterministic (which it is) this will produce codes satisfying the
     *     requirements of {@link Object#hashCode()}.
     * @implNote Because a {@link Text} can be a view of a large slice of characters the hash-code
     *     algorithm just samples characters from the content. This increases the risk of
     *     hash-code collisions but makes computing the hash code cheap and fast O(1). This is
     *     particularly important in the main use case of {@link Text} as the main component of
     *     a {@link JsonPath} which is indexes in the {@link JsonNode} cache within a {@link
     *     JsonTree}.
     * @param text the slice to compute the hash code for
     * @return the computed hash code
     */
    static int hashCode(Text text) {
        if (text.isEmpty()) return 0;
        int hash = 1;
        int sampleSize = 9;
        int length = text.length();
        if (length <= sampleSize) {
            for (int i = 0; i < length; i++)
                hash = 31 * hash + text.charAt(i);
        } else {
            // take characters as evenly spaced as possible
            // while including both ends though linear interpolation
            int step = length - 1;
            for (int i = 0; i < sampleSize; i++)
                // index is equivalent to: i * step / (sampleSize - 1)
                hash = 31 * hash + text.charAt(i * step >> 3);
        }
        return hash;
    }

}
