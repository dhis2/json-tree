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
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the {@link JuonAppender} implementation of {@link Juon}.
 *
 * @author Jan Bernitt
 */
class JuonAppenderTest {

    @Test
    void testObject_Boolean() {
        assertEquals( "(a:t,b:f)", Juon.createObject( obj -> obj
            .addBoolean( "a", true )
            .addBoolean( "b", false )
            .addBoolean( "c", null ) ) );
    }

    @Test
    void testArray_Boolean() {
        Consumer<JsonArrayBuilder> builder = arr -> arr
            .addBoolean( true )
            .addBoolean( false )
            .addBoolean( null );
        assertEquals( "(t,f,)", Juon.createArray( builder ) );
        assertEquals( "(true,false,null)", Juon.createArray(Juon.PLAIN, builder ) );
    }

    @Test
    void testObject_Int() {
        assertEquals( "(int:42)", Juon.createObject( obj -> obj
            .addNumber( "int", 42 ) ) );
    }

    @Test
    void testArray_Int() {
        assertEquals( "(42)", Juon.createArray( arr -> arr
            .addNumber( 42 ) ) );
    }

    @Test
    void testObject_Double() {
        assertEquals( "(double:42.42)", Juon.createObject( obj -> obj
            .addNumber( "double", 42.42 ) ) );
    }

    @Test
    void testArray_Double() {
        assertEquals( "(42.42)", Juon.createArray( arr -> arr
            .addNumber( 42.42 ) ) );
    }

    @Test
    void testObject_Long() {
        assertEquals( "(long:" + Long.MAX_VALUE + ")", Juon.createObject( obj -> obj
            .addNumber( "long", Long.MAX_VALUE ) ) );
    }

    @Test
    void testArray_Long() {
        assertEquals( "(" + Long.MAX_VALUE + ")", Juon.createArray( arr -> arr
            .addNumber( Long.MAX_VALUE ) ) );
    }

    @Test
    void testObject_BigInteger() {
        assertEquals( "(bint:42)", Juon.createObject( obj -> obj
            .addNumber( "bint", BigInteger.valueOf( 42L ) ) ) );
    }

    @Test
    void testArray_BigInteger() {
        assertEquals( "(42)", Juon.createArray( arr -> arr
            .addNumber( BigInteger.valueOf( 42L ) ) ) );
    }

    @Test
    void testObject_String() {
        assertEquals( "(s:'hello')", Juon.createObject( obj -> obj
            .addString( "s", "hello" ) ) );
    }

    @Test
    void testObject_StringNull() {
        assertEquals( "null", Juon.createObject( obj -> obj
            .addString( "s", null ) ) );
    }

    @Test
    void testObject_StringEscapes() {
        assertEquals( "(s:'\"oh yes\"')", Juon.createObject( obj -> obj
            .addString( "s", "\"oh yes\"" ) ) );
    }

    @Test
    void testArray_String() {
        assertEquals( "('hello')", Juon.createArray( arr -> arr
            .addString( "hello" ) ) );
    }

    @Test
    void testArray_StringEscapes() {
        assertEquals( "('hello\\\\ world')", Juon.createArray( arr -> arr
            .addString( "hello\\ world" ) ) );
    }

    @Test
    void testObject_IntArray() {
        assertEquals( "(array:(1,2))", Juon.createObject( obj -> obj
            .addArray( "array", arr -> arr.addNumbers( 1, 2 ) ) ) );
    }

    @Test
    void testArray_IntArray() {
        assertEquals( "((1,2))", Juon.createArray( arr -> arr
            .addArray( a -> a.addNumbers( 1, 2 ) ) ) );
    }

    @Test
    void testObject_DoubleArray() {
        assertEquals( "(array:(1.5,2.5))", Juon.createObject( obj -> obj
            .addArray( "array", arr -> arr.addNumbers( 1.5d, 2.5d ) ) ) );
    }

    @Test
    void testArray_DoubleArray() {
        assertEquals( "((1.5,2.5))", Juon.createArray( arr -> arr
            .addArray( a -> a.addNumbers( 1.5d, 2.5d ) ) ) );
    }

    @Test
    void testObject_LongArray() {
        assertEquals( "(array:(" + Long.MIN_VALUE + "," + Long.MAX_VALUE + "))", Juon.createObject( obj -> obj
            .addArray( "array", arr -> arr.addNumbers( Long.MIN_VALUE, Long.MAX_VALUE ) ) ) );
    }

    @Test
    void testArray_LongArray() {
        assertEquals( "((" + Long.MIN_VALUE + "," + Long.MAX_VALUE + "))", Juon.createArray( arr -> arr
            .addArray( a -> a.addNumbers( Long.MIN_VALUE, Long.MAX_VALUE ) ) ) );
    }

    @Test
    void testObject_StringArray() {
        assertEquals( "(array:('a','b'))", Juon.createObject( obj -> obj
            .addArray( "array", arr -> arr.addStrings( "a", "b" ) ) ) );
    }

    @Test
    void testArray_StringArray() {
        assertEquals( "(('a','b'))", Juon.createArray( arr -> arr
            .addArray( a -> a.addStrings( "a", "b" ) ) ) );
    }

    @Test
    void testObject_OtherArray() {
        assertEquals( "(array:('SOURCE','CLASS','RUNTIME'))", Juon.createObject( obj -> obj
            .addArray( "array", arr -> arr.addElements( RetentionPolicy.values(), JsonArrayBuilder::addString,
                RetentionPolicy::name ) ) ) );
    }

    @Test
    void testArray_OtherArray() {
        assertEquals( "(('SOURCE','CLASS','RUNTIME'))", Juon.createArray( arr -> arr
            .addArray( a -> a.addElements( RetentionPolicy.values(), JsonArrayBuilder::addString,
                RetentionPolicy::name ) ) ) );
    }

    @Test
    void testArray_OtherCollection() {
        assertEquals( "(('SOURCE','CLASS','RUNTIME'))", Juon.createArray( arr -> arr
            .addArray( a -> a.addElements( List.of( RetentionPolicy.values() ), JsonArrayBuilder::addString,
                RetentionPolicy::name ) ) ) );
    }

    @Test
    void testObject_ObjectBuilder() {
        assertEquals( "(obj:(inner:42))", Juon.createObject( outer -> outer
            .addObject( "obj", obj -> obj.addNumber( "inner", 42 ) ) ) );
    }

    @Test
    void testArray_ObjectBuilder() {
        assertEquals( "((42,14))", Juon.createArray( arr -> arr
            .addArray( arr2 -> arr2.addNumber( 42 ).addNumber( 14 ) ) ) );
    }

    @Test
    void testArray_ArrayBuilder() {
        assertEquals( "((inner:42))", Juon.createArray( arr -> arr
            .addObject( obj -> obj.addNumber( "inner", 42 ) ) ) );
    }

    @Test
    void testObject_ObjectMap() {
        assertEquals( "(obj:(field:42))", Juon.createObject( outer -> outer
            .addObject( "obj", inner -> inner.addMembers(
                Map.of( "field", 42 ).entrySet(), JsonObjectBuilder::addNumber ) ) ) );
    }

    @Test
    void testArray_ObjectMap() {
        assertEquals( "((field:42))", Juon.createArray( arr -> arr
            .addObject( obj -> obj.addMembers( Map.of( "field", 42 ), JsonObjectBuilder::addNumber ) ) ) );
    }

    @Test
    void testObject_MembersMap() {
        assertEquals( "(field:42)", Juon.createObject( outer -> outer
            .addMembers( Map.of( "field", 42 ).entrySet(), JsonObjectBuilder::addNumber ) ) );
    }

    @Test
    void testArray_ElementsCollection() {
        assertEquals( "((42))", Juon.createArray( arr -> arr
            .addArray( a -> a.addElements( List.of( 42 ), JsonArrayBuilder::addNumber ) ) ) );
    }

    @Test
    void testObject_JsonNode() {
        assertEquals( "(node:('a','b'))", Juon.createObject( obj -> obj
            .addMember( "node", JsonNode.of( "[\"a\",\"b\"]" ) ) ) );
    }

    @Test
    void testObject_JsonNodeNull() {
        Consumer<JsonObjectBuilder> builder = obj -> obj
            .addMember( "node", JsonNode.NULL );
        assertEquals( "null", Juon.createObject( builder ),
            "when the 'node' member is omitted the entire object is empty and approximated to null" );
        assertEquals( "(node:null)", Juon.createObject(Juon.PLAIN, builder ),
            "but when null members are included plain the object no longer is empty");
    }

    @Test
    void testArray_JsonNode() {
        assertEquals( "(('a','b'))", Juon.createArray( arr -> arr
            .addElement( JsonNode.of( "[\"a\",\"b\"]" ) ) ) );
    }
}
