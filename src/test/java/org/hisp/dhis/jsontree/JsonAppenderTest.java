/*
 * Copyright (c) 2004-2021, University of Oslo
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

import org.hisp.dhis.jsontree.JsonBuilder.JsonArrayBuilder;
import org.hisp.dhis.jsontree.JsonBuilder.JsonObjectBuilder;
import org.junit.jupiter.api.Test;

import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the {@link JsonAppender} implementation of a {@link JsonBuilder}.
 *
 * @author Jan Bernitt
 */
class JsonAppenderTest {

    @Test
    void testObject_Boolean() {
        assertJson( "{'a':true,'b':false,'c':null}", JsonBuilder.createObject( obj -> obj
            .addBoolean( "a", true )
            .addBoolean( "b", false )
            .addBoolean( "c", null ) ) );
    }

    @Test
    void testArray_Boolean() {
        assertJson( "[true,false,null]", JsonBuilder.createArray( arr -> arr
            .addBoolean( true )
            .addBoolean( false )
            .addBoolean( null ) ) );
    }

    @Test
    void testObject_Int() {
        assertJson( "{'int':42}", JsonBuilder.createObject( obj -> obj
            .addNumber( "int", 42 ) ) );
    }

    @Test
    void testArray_Int() {
        assertJson( "[42]", JsonBuilder.createArray( arr -> arr
            .addNumber( 42 ) ) );
    }

    @Test
    void testObject_Double() {
        assertJson( "{'double':42.42}", JsonBuilder.createObject( obj -> obj
            .addNumber( "double", 42.42 ) ) );
    }

    @Test
    void testArray_Double() {
        assertJson( "[42.42]", JsonBuilder.createArray( arr -> arr
            .addNumber( 42.42 ) ) );
    }

    @Test
    void testObject_Long() {
        assertJson( "{'long':" + Long.MAX_VALUE + "}", JsonBuilder.createObject( obj -> obj
            .addNumber( "long", Long.MAX_VALUE ) ) );
    }

    @Test
    void testArray_Long() {
        assertJson( "[" + Long.MAX_VALUE + "]", JsonBuilder.createArray( arr -> arr
            .addNumber( Long.MAX_VALUE ) ) );
    }

    @Test
    void testObject_BigInteger() {
        assertJson( "{'bint':42}", JsonBuilder.createObject( obj -> obj
            .addNumber( "bint", BigInteger.valueOf( 42L ) ) ) );
    }

    @Test
    void testArray_BigInteger() {
        assertJson( "[42]", JsonBuilder.createArray( arr -> arr
            .addNumber( BigInteger.valueOf( 42L ) ) ) );
    }

    @Test
    void testObject_String() {
        assertJson( "{'s':'hello'}", JsonBuilder.createObject( obj -> obj
            .addString( "s", "hello" ) ) );
    }

    @Test
    void testObject_StringNull() {
        assertJson( "{'s':null}", JsonBuilder.createObject( obj -> obj
            .addString( "s", null ) ) );
    }

    @Test
    void testObject_StringEscapes() {
        assertJson( "{'s':'\\\"oh yes\\\"'}", JsonBuilder.createObject( obj -> obj
            .addString( "s", "\"oh yes\"" ) ) );
    }

    @Test
    void testArray_String() {
        assertJson( "['hello']", JsonBuilder.createArray( arr -> arr
            .addString( "hello" ) ) );
    }

    @Test
    void testArray_StringEscapes() {
        assertJson( "['hello\\\\ world']", JsonBuilder.createArray( arr -> arr
            .addString( "hello\\ world" ) ) );
    }

    @Test
    void testObject_IntArray() {
        assertJson( "{'array':[1,2]}", JsonBuilder.createObject( obj -> obj
            .addArray( "array", arr -> arr.addNumbers( 1, 2 ) ) ) );
    }

    @Test
    void testArray_IntArray() {
        assertJson( "[[1,2]]", JsonBuilder.createArray( arr -> arr
            .addArray( a -> a.addNumbers( 1, 2 ) ) ) );
    }

    @Test
    void testObject_DoubleArray() {
        assertJson( "{'array':[1.5,2.5]}", JsonBuilder.createObject( obj -> obj
            .addArray( "array", arr -> arr.addNumbers( 1.5d, 2.5d ) ) ) );
    }

    @Test
    void testArray_DoubleArray() {
        assertJson( "[[1.5,2.5]]", JsonBuilder.createArray( arr -> arr
            .addArray( a -> a.addNumbers( 1.5d, 2.5d ) ) ) );
    }

    @Test
    void testObject_LongArray() {
        assertJson( "{'array':[" + Long.MIN_VALUE + "," + Long.MAX_VALUE + "]}", JsonBuilder.createObject( obj -> obj
            .addArray( "array", arr -> arr.addNumbers( Long.MIN_VALUE, Long.MAX_VALUE ) ) ) );
    }

    @Test
    void testArray_LongArray() {
        assertJson( "[[" + Long.MIN_VALUE + "," + Long.MAX_VALUE + "]]", JsonBuilder.createArray( arr -> arr
            .addArray( a -> a.addNumbers( Long.MIN_VALUE, Long.MAX_VALUE ) ) ) );
    }

    @Test
    void testObject_StringArray() {
        assertJson( "{'array':['a','b']}", JsonBuilder.createObject( obj -> obj
            .addArray( "array", arr -> arr.addStrings( "a", "b" ) ) ) );
    }

    @Test
    void testArray_StringArray() {
        assertJson( "[['a','b']]", JsonBuilder.createArray( arr -> arr
            .addArray( a -> a.addStrings( "a", "b" ) ) ) );
    }

    @Test
    void testObject_OtherArray() {
        assertJson( "{'array':['SOURCE','CLASS','RUNTIME']}", JsonBuilder.createObject( obj -> obj
            .addArray( "array", arr -> arr.addElements( RetentionPolicy.values(), JsonArrayBuilder::addString,
                RetentionPolicy::name ) ) ) );
    }

    @Test
    void testArray_OtherArray() {
        assertJson( "[['SOURCE','CLASS','RUNTIME']]", JsonBuilder.createArray( arr -> arr
            .addArray( a -> a.addElements( RetentionPolicy.values(), JsonArrayBuilder::addString,
                RetentionPolicy::name ) ) ) );
    }

    @Test
    void testArray_OtherCollection() {
        assertJson( "[['SOURCE','CLASS','RUNTIME']]", JsonBuilder.createArray( arr -> arr
            .addArray( a -> a.addElements( List.of( RetentionPolicy.values() ), JsonArrayBuilder::addString,
                RetentionPolicy::name ) ) ) );
    }

    @Test
    void testObject_ObjectBuilder() {
        assertJson( "{'obj':{'inner':42}}", JsonBuilder.createObject( outer -> outer
            .addObject( "obj", obj -> obj.addNumber( "inner", 42 ) ) ) );
    }

    @Test
    void testArray_ObjectBuilder() {
        assertJson( "[[42,14]]", JsonBuilder.createArray( arr -> arr
            .addArray( arr2 -> arr2.addNumber( 42 ).addNumber( 14 ) ) ) );
    }

    @Test
    void testArray_ArrayBuilder() {
        assertJson( "[{'inner':42}]", JsonBuilder.createArray( arr -> arr
            .addObject( obj -> obj.addNumber( "inner", 42 ) ) ) );
    }

    @Test
    void testObject_ObjectMap() {
        assertJson( "{'obj':{'field':42}}", JsonBuilder.createObject( outer -> outer
            .addObject( "obj", inner -> inner.addMembers(
                Map.of( "field", 42 ).entrySet(), JsonObjectBuilder::addNumber ) ) ) );
    }

    @Test
    void testArray_ObjectMap() {
        assertJson( "[{'field':42}]", JsonBuilder.createArray( arr -> arr
            .addObject( obj -> obj.addMembers( Map.of( "field", 42 ), JsonObjectBuilder::addNumber ) ) ) );
    }

    @Test
    void testObject_MembersMap() {
        assertJson( "{'field':42}", JsonBuilder.createObject( outer -> outer
            .addMembers( Map.of( "field", 42 ).entrySet(), JsonObjectBuilder::addNumber ) ) );
    }

    @Test
    void testArray_ElementsCollection() {
        assertJson( "[[42]]", JsonBuilder.createArray( arr -> arr
            .addArray( a -> a.addElements( List.of( 42 ), JsonArrayBuilder::addNumber ) ) ) );
    }

    @Test
    void testObject_JsonNode() {
        assertJson( "{'node':['a','b']}", JsonBuilder.createObject( obj -> obj
            .addMember( "node", JsonNode.of( "[\"a\",\"b\"]" ) ) ) );
    }

    @Test
    void testObject_JsonNodeNull() {
        assertJson( "{'node':null}", JsonBuilder.createObject( obj -> obj
            .addMember( "node", JsonNode.NULL ) ) );
    }

    @Test
    void testArray_JsonNode() {
        assertJson( "[['a','b']]", JsonBuilder.createArray( arr -> arr
            .addElement( JsonNode.of( "[\"a\",\"b\"]" ) ) ) );
    }

    @Test
    void testNewlines() {
        //language=JSON
        String json = """
            {"id":"woOg1dUFoX0","time":1738685143915,"message":"com.fasterxml.jackson.core.JsonParseException: Unexpected character ('<' (code 60)): expected a valid value (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\\n at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 1]","level":"ERROR"}""";
        JsonObject msg = JsonMixed.of( json );
        String message = msg.getString( "message" ).string();
        assertEquals( """
                com.fasterxml.jackson.core.JsonParseException: Unexpected character ('<' (code 60)): expected a valid value (JSON String, Number, Array, Object or token 'null', 'true' or 'false')
                 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 1]""",
            message );
        JsonObject actual = Json.object( obj ->
            obj.addString( "id", "woOg1dUFoX0" )
                .addNumber( "time", 1738685143915L )
                .addString( "message", message )
                .addString( "level", "ERROR" ));
        assertEquals( json, actual.toJson() );
    }

    private static void assertJson( String expected, JsonNode actual ) {
        assertEquals( expected.replace( '\'', '"' ), actual.getDeclaration() );
    }
}
