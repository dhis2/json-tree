/*
 * Copyright (c) 2004-2022, University of Oslo
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

import static java.lang.Math.max;
import static java.lang.Math.min;

import java.util.Arrays;

/**
 * Thrown when the JSON content turns out to be invalid JSON.
 *
 * @author Jan Bernitt
 */
public class JsonFormatException extends IllegalArgumentException
{
    public JsonFormatException( String message )
    {
        super( message );
    }

    public JsonFormatException( char[] json, int index, char expected )
    {
        this( createParseErrorMessage( json, index, expected ) );
    }

    public JsonFormatException( char[] json, int index, String expected )
    {
        this( createParseErrorMessage( json, index, expected ) );
    }

    private static String createParseErrorMessage( char[] json, int index, char expected )
    {
        return createParseErrorMessage( json, index, expected == '~' ? "start of value" : "`" + expected + "`" );
    }

    private static String createParseErrorMessage( char[] json, int index, String expected )
    {
        int start = max( 0, index - 20 );
        int length = min( json.length - start, 40 );
        String section = new String( json, start, length );
        char[] pointer = new char[index - start + 1];
        Arrays.fill( pointer, ' ' );
        pointer[pointer.length - 1] = '^';
        return String.format( "Unexpected character at position %d,%n%s%n%s expected %s",
            index, section, new String( pointer ), expected );
    }
}
