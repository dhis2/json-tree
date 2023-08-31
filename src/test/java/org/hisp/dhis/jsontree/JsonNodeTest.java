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

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

/**
 * Tests {@link JsonNode} specific aspects of the {@link JsonTree} implementation of the interface.
 *
 * @author Jan Bernitt
 */
class JsonNodeTest {

    @Test
    void testGet_String() {
        assertGetThrowsJsonPathException( "\"hello\"",
            "This is a leaf node of type STRING that does not have any children at path: foo" );
    }

    @Test
    void testGet_Number() {
        assertGetThrowsJsonPathException( "42",
            "This is a leaf node of type NUMBER that does not have any children at path: foo" );
    }

    @Test
    void testGet_Boolean() {
        assertGetThrowsJsonPathException( "true",
            "This is a leaf node of type BOOLEAN that does not have any children at path: foo" );
    }

    @Test
    void testGet_Null() {
        assertGetThrowsJsonPathException( "null",
            "This is a leaf node of type NULL that does not have any children at path: foo" );
    }

    @Test
    void testGet_Object() {
        JsonNode root = JsonNode.of( "{\"a\":{\"b\":{\"c\":42}}}" );
        assertEquals( 42, root.get( "a.b.c" ).value() );
        JsonNode b = root.get( "a.b" );
        assertEquals( 42, b.get( "c" ).value() );
    }

    @Test
    void testGet_Object_NoValueAtPath() {
        assertGetThrowsJsonPathException( "{\"a\":{\"b\":{\"c\":42}}}", "b",
            "Path `.b` does not exist, object `` does not have a property `b`" );
        assertGetThrowsJsonPathException( "{\"a\":{\"b\":{\"c\":42}}}", "a.c",
            "Path `.a.c` does not exist, object `.a` does not have a property `c`" );
    }

    @Test
    void testGet_Array() {
        JsonNode root = JsonNode.of( "[[1,2],[3,4],{\"a\":5}]" );
        assertEquals( 1, root.get( "[0][0]" ).value() );
        JsonNode arr1 = root.get( "[1]" );
        assertEquals( 4, arr1.get( "[1]" ).value() );
        assertEquals( 5, root.get( "[2].a" ).value() );
        assertEquals( 5, root.get( "[2]" ).get( "a" ).value() );
    }

    @Test
    void testGet_Array_NoValueAtPath() {
        assertGetThrowsJsonPathException( "[1,2]", "a", "Malformed path a at a." );
        assertGetThrowsJsonPathException( "[[1,2],[]]", "[1][0]",
            "Path `[1][0]` does not exist, array `[1]` has only `0` elements." );
        assertGetThrowsJsonPathException( "[[1,2],[]]", "[0].a",
            "Path `[0].a` does not exist, parent `[0]` is not an OBJECT but a ARRAY node." );
    }

    @Test
    void testMember_NoObject() {
        JsonNode val = JsonNode.of( "1" );
        JsonTreeException ex = assertThrowsExactly( JsonTreeException.class, () -> val.member( "a" ) );
        assertEquals( "NUMBER node has no member property.", ex.getMessage() );
    }

    @Test
    void testMembers_NoObject() {
        JsonNode val = JsonNode.of( "1" );
        JsonTreeException ex = assertThrowsExactly( JsonTreeException.class, () -> val.members( true ) );
        assertEquals( "NUMBER node has no members property.", ex.getMessage() );
    }

    @Test
    void testElements_NoArray() {
        JsonNode val = JsonNode.of( "1" );
        JsonTreeException ex = assertThrowsExactly( JsonTreeException.class, () -> val.elements( true ) );
        assertEquals( "NUMBER node has no elements property.", ex.getMessage() );
    }

    @Test
    void testReplaceWith_Path() {
        //language=json
        String json = """
            {
            "a": 1,
            "b": [2]
            }""";
        JsonNode obj = JsonNode.of( json );
        JsonNode actual = obj.replaceWith( "b[0]", JsonNode.of( "3" ) );
        assertEquals( """
            {
            "a": 1,
            "b": [3]
            }""", actual.getDeclaration() );
    }

    @Test
    void testAddMembers_Path() {
        //language=json
        String json = """
            {
            "a": 1,
            "b": {}
            }""";
        JsonNode actual = JsonNode.of( json ).addMembers( "b",
            obj -> obj.addNumber( "x", 42 ) );
        assertEquals( """
            {
            "a": 1,
            "b": {"x":42}
            }""", actual.getDeclaration() );
    }

    @Test
    void testRemoveMembers_Path() {
        //language=json
        String json = """
            {
            "a": 1,
            "b": {"x": 42, "y": 1, "z": 2}
            }""";
        JsonNode actual = JsonNode.of( json ).removeMembers( "b", Set.of( "x", "z" ) );
        assertEquals( """
            {
            "a": 1,
            "b": {"y":1}
            }""", actual.getDeclaration() );
    }

    private static void assertGetThrowsJsonPathException( String json, String expected ) {
        assertGetThrowsJsonPathException( json, "foo", expected );
    }

    private static void assertGetThrowsJsonPathException( String json, String path, String expected ) {
        JsonNode root = JsonNode.of( json );
        JsonPathException ex = assertThrowsExactly( JsonPathException.class, () -> root.get( path ) );
        assertEquals( expected, ex.getMessage() );
    }
}
