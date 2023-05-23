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
import org.junit.Test;

import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.util.List;

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertEquals;

/**
 * Tests the {@link JsonAppender} implementation of a {@link JsonBuilder}.
 *
 * @author Jan Bernitt
 */
public class JsonAppenderTest {

    private final JsonBuilder builder = new JsonAppender( new StringBuilder() );

    @Test
    public void testObject_Boolean() {
        assertJson( "{'a':true,'b':false,'c':null}", builder.toObject( obj -> obj
            .addBoolean( "a", true )
            .addBoolean( "b", false )
            .addBoolean( "c", null ) ) );
    }

    @Test
    public void testArray_Boolean() {
        assertJson( "[true,false,null]", builder.toArray( arr -> arr
            .addBoolean( true )
            .addBoolean( false )
            .addBoolean( null ) ) );
    }

    @Test
    public void testObject_Int() {
        assertJson( "{'int':42}", builder.toObject( obj -> obj
            .addNumber( "int", 42 ) ) );
    }

    @Test
    public void testArray_Int() {
        assertJson( "[42]", builder.toArray( arr -> arr
            .addNumber( 42 ) ) );
    }

    @Test
    public void testObject_Double() {
        assertJson( "{'double':42.42}", builder.toObject( obj -> obj
            .addNumber( "double", 42.42 ) ) );
    }

    @Test
    public void testArray_Double() {
        assertJson( "[42.42]", builder.toArray( arr -> arr
            .addNumber( 42.42 ) ) );
    }

    @Test
    public void testObject_Long() {
        assertJson( "{'long':" + Long.MAX_VALUE + "}", builder.toObject( obj -> obj
            .addNumber( "long", Long.MAX_VALUE ) ) );
    }

    @Test
    public void testArray_Long() {
        assertJson( "[" + Long.MAX_VALUE + "]", builder.toArray( arr -> arr
            .addNumber( Long.MAX_VALUE ) ) );
    }

    @Test
    public void testObject_BigInteger() {
        assertJson( "{'bint':42}", builder.toObject( obj -> obj
            .addNumber( "bint", BigInteger.valueOf( 42L ) ) ) );
    }

    @Test
    public void testArray_BigInteger() {
        assertJson( "[42]", builder.toArray( arr -> arr
            .addNumber( BigInteger.valueOf( 42L ) ) ) );
    }

    @Test
    public void testObject_String() {
        assertJson( "{'s':'hello'}", builder.toObject( obj -> obj
            .addString( "s", "hello" ) ) );
    }

    @Test
    public void testArray_String() {
        assertJson( "['hello']", builder.toArray( arr -> arr
            .addString( "hello" ) ) );
    }

    @Test
    public void testObject_IntArray() {
        assertJson( "{'array':[1,2]}", builder.toObject( obj -> obj
            .addArray( "array", 1, 2 ) ) );
    }

    @Test
    public void testArray_IntArray() {
        assertJson( "[[1,2]]", builder.toArray( arr -> arr
            .addArray( 1, 2 ) ) );
    }

    @Test
    public void testObject_DoubleArray() {
        assertJson( "{'array':[1.5,2.5]}", builder.toObject( obj -> obj
            .addArray( "array", 1.5d, 2.5d ) ) );
    }

    @Test
    public void testArray_DoubleArray() {
        assertJson( "[[1.5,2.5]]", builder.toArray( arr -> arr
            .addArray( 1.5d, 2.5d ) ) );
    }

    @Test
    public void testObject_LongArray() {
        assertJson( "{'array':[" + Long.MIN_VALUE + "," + Long.MAX_VALUE + "]}", builder.toObject( obj -> obj
            .addArray( "array", Long.MIN_VALUE, Long.MAX_VALUE ) ) );
    }

    @Test
    public void testArray_LongArray() {
        assertJson( "[[" + Long.MIN_VALUE + "," + Long.MAX_VALUE + "]]", builder.toArray( arr -> arr
            .addArray( Long.MIN_VALUE, Long.MAX_VALUE ) ) );
    }

    @Test
    public void testObject_StringArray() {
        assertJson( "{'array':['a','b']}", builder.toObject( obj -> obj
            .addArray( "array", "a", "b" ) ) );
    }

    @Test
    public void testArray_StringArray() {
        assertJson( "[['a','b']]", builder.toArray( arr -> arr
            .addArray( "a", "b" ) ) );
    }

    @Test
    public void testObject_OtherArray() {
        assertJson( "{'array':['SOURCE','CLASS','RUNTIME']}", builder.toObject( obj -> obj
            .addArray( "array", RetentionPolicy.values(), JsonArrayBuilder::addString, RetentionPolicy::name ) ) );
    }

    @Test
    public void testArray_OtherArray() {
        assertJson( "[['SOURCE','CLASS','RUNTIME']]", builder.toArray( arr -> arr
            .addArray( RetentionPolicy.values(), JsonArrayBuilder::addString, RetentionPolicy::name ) ) );
    }

    @Test
    public void testArray_OtherCollection() {
        assertJson( "[['SOURCE','CLASS','RUNTIME']]", builder.toArray( arr -> arr
            .addArray( List.of( RetentionPolicy.values() ), JsonArrayBuilder::addString, RetentionPolicy::name ) ) );
    }

    @Test
    public void testObject_StreamArray() {
        assertJson( "{'a2':[['a'],['b','c']]}", builder.toObject( obj -> obj
            .addArray( "a2", List.of( "a", "bc" ),
                ( arr, e ) -> arr.addArray( e.codePoints().boxed(),
                    ( innerArr, c ) -> innerArr.addString( Character.toString( c ) ) ) ) ) );
    }

    @Test
    public void testArray_StreamArray() {
        assertJson( "[[['a'],['b','c']]]", builder.toArray( arr -> arr
            .addArray( List.of( "a", "bc" ),
                ( arr2, e ) -> arr2.addArray( e.codePoints().boxed(),
                    ( innerArr, c ) -> innerArr.addString( Character.toString( c ) ) ) ) ) );
    }

    @Test
    public void testObject_ObjectBuilder() {
        assertJson( "{'obj':{'inner':42}}", builder.toObject( outer -> outer
            .addObject( "obj", obj -> obj.addNumber( "inner", 42 ) ) ) );
    }

    @Test
    public void testArray_ObjectBuilder() {
        assertJson( "[[42,14]]", builder.toArray( arr -> arr
            .addArray( arr2 -> arr2.addNumber( 42 ).addNumber( 14 ) ) ) );
    }

    @Test
    public void testArray_ArrayBuilder() {
        assertJson( "[{'inner':42}]", builder.toArray( arr -> arr
            .addObject( obj -> obj.addNumber( "inner", 42 ) ) ) );
    }

    @Test
    public void testObject_ObjectMap() {
        assertJson( "{'obj':{'field':42}}", builder.toObject( outer -> outer
            .addObject( "obj", singletonMap( "field", 42 ), JsonObjectBuilder::addNumber ) ) );
    }

    @Test
    public void testArray_ObjectMap() {
        assertJson( "[{'field':42}]", builder.toArray( arr -> arr
            .addObject( singletonMap( "field", 42 ), JsonObjectBuilder::addNumber ) ) );
    }

    @Test
    public void testObject_MembersMap() {
        assertJson( "{'field':42}", builder.toObject( outer -> outer
            .addMembers( singletonMap( "field", 42 ), JsonObjectBuilder::addNumber ) ) );
    }

    @Test
    public void testArray_ElementsCollection() {
        assertJson( "[[42]]", builder.toArray( arr -> arr
            .addArray( singletonList( 42 ), JsonArrayBuilder::addNumber ) ) );
    }

    @Test
    public void testObject_JsonNode() {
        assertJson( "{'node':['a','b']}", builder.toObject( obj -> obj
            .addMember( "node", JsonNode.of( "[\"a\",\"b\"]" ) ) ) );
    }

    @Test
    public void testArray_JsonNode() {
        assertJson( "[['a','b']]", builder.toArray( arr -> arr
            .addElement( JsonNode.of( "[\"a\",\"b\"]" ) ) ) );
    }

    private static void assertJson( String expected, JsonNode actual ) {
        assertEquals( expected.replace( '\'', '"' ), actual.getDeclaration() );
    }
}
