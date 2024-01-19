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

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the static methods of the {@link JsonBuilder}.
 * <p>
 * This is mostly to ensure the wiring is correct so the tests are not trying to cover different cases when it comes to
 * JSON creation - they just want to cover different path of wiring.
 *
 * @author Jan Bernitt
 */
class JsonBuilderTest {

    @Test
    void testCreateObject() {
        assertEquals( "{\"foo\":[\"bar\"]}",
            JsonBuilder.createObject( obj -> obj.addArray( "foo", arr -> arr.addString( "bar" ) ) )
                .getDeclaration() );
    }

    @Test
    void testCreateArray() {
        assertEquals( "[42,\"42\",true]",
            JsonBuilder.createArray( arr -> arr.addNumber( 42 ).addString( "42" ).addBoolean( true ) )
                .getDeclaration() );
    }

    @Test
    void testStreamObject() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonBuilder.streamObject( out, obj -> obj.addArray( "foo", arr -> arr.addString( "bar" ) ) );
        assertEquals( "{\"foo\":[\"bar\"]}", out.toString() );
    }

    @Test
    void testStreamArray() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        JsonBuilder.streamArray( out, arr -> arr.addNumber( 42 ).addString( "42" ).addBoolean( true ) );
        assertEquals( "[42,\"42\",true]", out.toString() );
    }
}
