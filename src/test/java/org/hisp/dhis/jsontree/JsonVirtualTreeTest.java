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

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;
import java.util.Objects;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the basic correctness of {@link JsonVirtualTree} which is the implementation of all core interfaces of the
 * {@link JsonValue} utility.
 *
 * @author Jan Bernitt
 */
class JsonVirtualTreeTest {

    @Test
    void testCustomObjectTypeMultiMap() {
        JsonMultiMap<JsonNumber> multiMap = createJSON( "{'foo':[1,23], 'bar': [34,56]}" )
            .asMultiMap( JsonNumber.class );
        assertFalse( multiMap.isEmpty() );
        assertTrue( multiMap.isObject() );
        assertEquals( 23, multiMap.get( "foo" ).get( 1 ).intValue() );
        assertEquals( 34, multiMap.get( "bar" ).get( 0 ).intValue() );
    }

    @Test
    void testObjectHas() {
        JsonObject response = createJSON( "{'users': {'foo':{'id':'foo'}, 'bar':[]}}" );
        assertTrue( response.has( "users" ) );
        assertTrue( response.getObject( "users" ).has( "foo", "bar" ) );
        assertFalse( response.has( "no-a-member" ) );
        assertFalse( createJSON( "[]" ).getObject( "undefined" ).has( "foo" ) );
        JsonObject bar = response.getObject( "users" ).getObject( "bar" );
        Exception ex = assertThrows( UnsupportedOperationException.class, () -> bar.has( "is-array" ) );
        assertEquals( "Path `$.users.bar` does not contain a OBJECT but a(n) ARRAY: []", ex.getMessage() );
    }

    @Test
    void testNumber() {
        JsonObject response = createJSON( "{'number': 13, 'fraction': 4.2}" );

        assertEquals( 13, response.getNumber( "number" ).number() );
        assertEquals( 4.2f, response.getNumber( "fraction" ).number().floatValue(), 0.001f );
        assertTrue( response.getNumber( "number" ).exists() );
        assertNull( response.getNumber( "missing" ).number() );
    }

    @Test
    void testIntValue() {
        JsonObject response = createJSON( "{'number':13}" );
        assertEquals( 13, response.getNumber( "number" ).intValue() );
        JsonNumber missing = response.getNumber( "missing" );
        assertThrows( NoSuchElementException.class, missing::intValue );
    }

    @Test
    void testString() {
        JsonObject response = createJSON( "{'text': 'plain'}" );

        assertEquals( "plain", response.getString( "text" ).string() );
        assertTrue( response.getString( "text" ).exists() );
        assertNull( response.getString( "missing" ).string() );
    }

    @Test
    void testBool() {
        JsonObject response = createJSON( "{'flag': true}" );

        assertTrue( response.getBoolean( "flag" ).bool() );
        assertTrue( response.getBoolean( "flag" ).exists() );
        assertNull( response.getBoolean( "missing" ).bool() );
    }

    @Test
    void testNull() {
        JsonObject response = createJSON( "{'value': null}" );

        assertNull( response.getBoolean( "value" ).bool() );
        assertNull( response.getString( "value" ).string() );
        assertNull( response.getNumber( "value" ).number() );
        assertTrue( response.getObject( "value" ).exists() );
        assertTrue( response.getArray( "value" ).exists() );
        assertTrue( response.get( "value" ).isNull() );
        assertFalse( response.get( "value" ).isObject() );
        assertFalse( response.get( "value" ).isArray() );
    }

    @Test
    void testBooleanValue() {
        JsonObject response = createJSON( "{'flag': true}" );

        assertTrue( response.getBoolean( "flag" ).booleanValue() );
        JsonBoolean missing = response.getBoolean( "missing" );
        assertThrows( NoSuchElementException.class, missing::booleanValue );
    }

    @Test
    void testNotExists() {
        JsonObject response = createJSON( "{'flag': true}" );

        assertFalse( response.getString( "no" ).exists() );
    }

    @Test
    void testSizeArray() {
        JsonObject response = createJSON( "{'numbers': [1,2,3,4]}" );

        assertEquals( 4, response.getArray( "numbers" ).size() );
        assertFalse( response.getArray( "numbers" ).isNull() );
    }

    @Test
    void testStringValues() {
        JsonObject response = createJSON( "{'letters': ['a','b','c']}" );

        assertEquals( asList( "a", "b", "c" ), response.getArray( "letters" ).stringValues() );
    }

    @Test
    void testNumberValues() {
        JsonObject response = createJSON( "{'digits': [1,2,3]}" );

        assertEquals( asList( 1, 2, 3 ), response.getArray( "digits" ).numberValues() );
    }

    @Test
    void testBoolValues() {
        JsonObject response = createJSON( "{'flags': [true, false, true]}" );

        assertEquals( asList( true, false, true ), response.getArray( "flags" ).boolValues() );
    }

    @Test
    void testArrayValuesMappedView() {
        JsonArray arr = createJSON( "[{'a':'x'},{'a':'y'},{'a':'z','b':1}]" );
        JsonList<JsonString> view = arr.viewAsList( e -> e.asObject().getString( "a" ) );
        assertEquals( asList( "x", "y", "z" ), view.toList( JsonString::string ) );
    }

    @Test
    void testListValuesMappedView() {
        JsonList<JsonObject> list = createJSON( "[{'a':'x','b':1},{'a':'y'},{'a':'z','b':3}]" )
            .asList( JsonObject.class );
        assertEquals( asList( "x", "y", "z" ),
            list.viewAsList( e -> e.getString( "a" ) ).toList( JsonString::string ) );
        assertEquals( asList( 1, null, 3 ),
            list.viewAsList( e -> e.getNumber( "b" ) ).toList( JsonNumber::intValue, null ) );
        assertEquals( asList( 1, 3 ),
            list.viewAsList( e -> e.getNumber( "b" ) ).toListOfElementsThatExists( JsonNumber::intValue ) );
    }

    @Test
    void testIsNull() {
        JsonObject response = createJSON( "{'optional': null }" );

        assertTrue( response.getArray( "optional" ).isNull() );
    }

    @Test
    void testIsArray() {
        JsonObject response = createJSON( "{'array': [], 'notAnArray': 42 }" );

        assertTrue( createJSON( "[]" ).isArray() );
        assertTrue( response.getArray( "array" ).isArray() );
        assertFalse( response.getArray( "notAnArray" ).isArray() );
        JsonArray missing = response.getArray( "missing" );
        assertThrows( NoSuchElementException.class, missing::isArray );
    }

    @Test
    void testIsObject() {
        JsonObject response = createJSON( "{'object': {}, 'notAnObject': 42 }" );

        assertTrue( response.isObject() );
        assertTrue( response.getArray( "object" ).isObject() );
        assertFalse( response.getArray( "notAnObject" ).isObject() );
        JsonArray missing = response.getArray( "missing" );
        assertThrows( NoSuchElementException.class, missing::isObject );
    }

    @Test
    void testBooleanNode() {
        JsonObject response = createJSON( "{'a': true }" );
        assertEquals( "true", response.getBoolean( "a" ).node().getDeclaration() );
    }

    @Test
    void testNumberNode() {
        JsonObject response = createJSON( "{'a': 42 }" );
        assertEquals( "42", response.getNumber( "a" ).node().getDeclaration() );
    }

    @Test
    void testStringNode() {
        JsonObject response = createJSON( "{'a': 'hello, again' }" );
        assertEquals( "\"hello, again\"", response.getString( "a" ).node().getDeclaration() );
    }

    @Test
    void testArrayNode() {
        JsonObject response = createJSON( "{'a': ['hello, again', 12] }" );
        assertEquals( "[\"hello, again\", 12]", response.getArray( "a" ).node().getDeclaration() );
    }

    @Test
    void testObjectNode() {
        JsonObject response = createJSON( "{'a': ['hello, again', 12] }" );
        assertEquals( "{\"a\": [\"hello, again\", 12] }", response.node().getDeclaration() );
    }

    @Test
    void testObjectGet() {
        JsonObject obj = createJSON( "{'x':{'a':[1], 'b':2, 'c':3}}" );
        assertEquals( 1, obj.getNumber( "x{a}[0]" ).intValue() );
        assertEquals( 1, obj.getObject( "x" ).getArray( "{a}" ).getNumber( 0 ).intValue() );
        assertEquals( 1, obj.getObject( "x" ).node().get( "{a}" ).get( "[0]" ).value() );
    }

    @Test
    void testListContainsAll() {
        JsonList<JsonString> list = createJSON( "[{'a':'x'}, {'a':'y'}]" ).as( JsonArray.class )
            .viewAsList( e -> e.asObject().getString( "a" ) );
        assertTrue( list.containsAll( JsonString::string, "y", "x" ) );
    }

    @Test
    void testListContains() {
        JsonList<JsonString> list = createJSON( "[{'a':'x'}, {'a':'y'}]" ).as( JsonArray.class )
            .viewAsList( e -> e.asObject().getString( "a" ) );
        assertTrue( list.contains( JsonString::string, "y"::equals ) );
        assertFalse( list.contains( JsonString::string, "z"::equals ) );
    }

    @Test
    void testListContainsUnique() {
        JsonList<JsonString> list = createJSON( "[{'a':'x'}, {'a':'y'}, {'a':'y'}]" ).as( JsonArray.class )
            .viewAsList( e -> e.asObject().getString( "a" ) );
        assertTrue( list.containsUnique( JsonString::string, "x"::equals ) );
        assertFalse( list.containsUnique( JsonString::string, "y"::equals ) );
    }

    @Test
    void testListFirst() {
        JsonList<JsonObject> list = createJSON( "[{'a':'x'}, {'a':'y','b':1}, {'a':'y','b':2}]" )
            .asList( JsonObject.class );
        assertEquals( 1, list.first( e -> Objects.equals( "y", e.getString( "a" ).string() ) )
            .asObject().getNumber( "b" ).intValue() );
        assertFalse( list.first( e -> e.has( "c" ) ).exists() );
    }

    @Test
    void testToString() {
        JsonList<JsonNumber> list = createJSON( "[12,42]" ).asList( JsonNumber.class );
        assertEquals( "[12,42]", list.toString() );
        JsonMap<JsonNumber> map = createJSON( "{'a':12,'b':42}" ).asMap( JsonNumber.class );
        assertEquals( "{\"a\":12,\"b\":42}", map.toString() );
    }

    @Test
    void testToString_NonExistingPath() {
        JsonList<JsonNumber> list = createJSON( "[12,42]" ).getObject( "non-existing" ).asList( JsonNumber.class );
        assertEquals( "Path `.non-existing` does not exist, parent `` is not an OBJECT but a ARRAY node.",
            list.toString() );
    }

    @Test
    void testToString_MalformedJson() {
        JsonMap<JsonNumber> map = createJSON( "{'a:12}" ).asMap( JsonNumber.class );
        assertEquals( "Expected \" but reach EOI: {\"a:12}", map.toString() );
    }

    private static JsonMixed createJSON( String content ) {
        return JsonMixed.of( content.replace( '\'', '"' ), JsonTypedAccess.GLOBAL );
    }
}
