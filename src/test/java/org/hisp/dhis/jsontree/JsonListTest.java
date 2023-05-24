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

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the additional utility methods of the {@link JsonList} interface.
 *
 * @author Jan Bernitt
 */
class JsonListTest {

    @Test
    void testList_stream_Undefined() {
        JsonList<JsonNumber> list = createJSON( "{}" ).getList( "missing", JsonNumber.class );
        assertEquals( List.of(), list.stream().map( JsonNumber::intValue ).collect( toList() ) );
    }

    @Test
    void testList_stream_Null() {
        JsonList<JsonNumber> list = createJSON( "null" ).asList( JsonNumber.class );
        assertEquals( List.of(), list.stream().map( JsonNumber::intValue ).collect( toList() ) );
    }

    @Test
    void testList_stream_Empty() {
        JsonList<JsonNumber> list = createJSON( "[]" ).asList( JsonNumber.class );
        assertEquals( List.of(), list.stream().map( JsonNumber::intValue ).collect( toList() ) );
    }

    @Test
    void testList_toList_Undefined() {
        JsonList<JsonNumber> list = createJSON( "{}" ).getList( "missing", JsonNumber.class );
        assertEquals( List.of(), list.toList( JsonNumber::intValue ) );
    }

    @Test
    void testList_toList_Null() {
        JsonList<JsonNumber> list = createJSON( "null" ).asList( JsonNumber.class );
        assertEquals( List.of(), list.toList( JsonNumber::intValue ) );
    }

    @Test
    void testList_toList_Empty() {
        JsonList<JsonNumber> list = createJSON( "[]" ).asList( JsonNumber.class );
        assertEquals( List.of(), list.toList( JsonNumber::intValue ) );
    }

    @Test
    void testList_toListOfElementsThatExists_Undefined() {
        JsonList<JsonNumber> list = createJSON( "{}" ).getList( "missing", JsonNumber.class );
        assertEquals( List.of(), list.toListOfElementsThatExists( JsonNumber::intValue ) );
    }

    @Test
    void testList_toListOfElementsThatExists_Null() {
        JsonList<JsonNumber> list = createJSON( "null" ).asList( JsonNumber.class );
        assertEquals( List.of(), list.toListOfElementsThatExists( JsonNumber::intValue ) );
    }

    @Test
    void testList_toListOfElementsThatExists_Empty() {
        JsonList<JsonNumber> list = createJSON( "[]" ).asList( JsonNumber.class );
        assertEquals( List.of(), list.toListOfElementsThatExists( JsonNumber::intValue ) );
    }

    @Test
    void testList_toListOfElementsThatExists_OnlyNulls() {
        JsonList<JsonNumber> list = createJSON( "[null,null]" ).asList( JsonNumber.class );
        assertEquals( List.of(), list.toListOfElementsThatExists( JsonNumber::intValue ) );
    }

    @Test
    void testList_toListOfElementsThatExists_Mixed() {
        JsonList<JsonNumber> list = createJSON( "[null,1,2,null,3]" ).asList( JsonNumber.class );
        assertEquals( List.of( 1, 2, 3 ), list.toListOfElementsThatExists( JsonNumber::intValue ) );
    }

    private static JsonMixed createJSON( String content ) {
        return JsonMixed.of( content.replace( '\'', '"' ), JsonTypedAccess.GLOBAL );
    }
}
