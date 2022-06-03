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

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;

import org.hisp.dhis.jsontree.JsonDocument.JsonFormatException;
import org.hisp.dhis.jsontree.JsonDocument.JsonNodeType;
import org.hisp.dhis.jsontree.JsonDocument.JsonPathException;
import org.junit.Test;

/**
 * Tests the fundamental properties of the {@link JsonDocument} JSON path
 * extractor.
 *
 * @author Jan Bernitt
 */
public class JsonDocumentTest
{
    @Test
    public void testStringNode()
    {
        JsonNode node = JsonNode.of( "\"hello\"" );
        assertEquals( JsonNodeType.STRING, node.getType() );
        assertEquals( "hello", node.value() );
        assertEquals( 0, node.startIndex() );
    }

    @Test
    public void testStringNode_Unicode()
    {
        // use an array to see that unicode skipping works as well
        JsonNode node0 = new JsonDocument( "[\"Star \\uD83D\\uDE80 ship\", 12]" ).get( "$[0]" );
        assertEquals( JsonNodeType.STRING, node0.getType() );
        assertEquals( "Star \uD83D\uDE80 ship", node0.value() );
        JsonNode node1 = new JsonDocument( "[\"Star \\uD83D\\uDE80 ship\", 12]" ).get( "$[1]" );
        assertEquals( JsonNodeType.NUMBER, node1.getType() );
        assertEquals( 12, node1.value() );
    }

    @Test
    public void testStringNode_EscapedChars()
    {
        JsonNode node = JsonNode.of( "\"\\\\\\/\\t\\r\\n\\f\\b\\\"\"" );
        assertEquals( "\\/\t\r\n\f\b\"", node.value() );
    }

    @Test
    public void testStringNode_Unsupported()
    {
        JsonNode node = JsonNode.of( "\"hello\"" );
        Exception ex = assertThrows( UnsupportedOperationException.class, node::isEmpty );
        assertEquals( "STRING node has no empty property.", ex.getMessage() );
        ex = assertThrows( UnsupportedOperationException.class, node::size );
        assertEquals( "STRING node has no size property.", ex.getMessage() );
        ex = assertThrows( UnsupportedOperationException.class, node::elements );
        assertEquals( "STRING node has no elements property.", ex.getMessage() );
        ex = assertThrows( UnsupportedOperationException.class, node::members );
        assertEquals( "STRING node has no members property.", ex.getMessage() );
    }

    @Test
    public void testStringNode_EOI()
    {
        JsonNode node = JsonNode.of( "\"hello" );
        JsonFormatException ex = assertThrows( JsonFormatException.class, node::value );
        assertEquals( "Expected \" but reach EOI: \"hello", ex.getMessage() );
    }

    @Test
    public void testNumberNode_Integer()
    {
        JsonNode node = JsonNode.of( "123" );
        assertEquals( JsonNodeType.NUMBER, node.getType() );
        assertEquals( 123, node.value() );
    }

    @Test
    public void testNumberNode_Unsupported()
    {
        JsonNode node = JsonNode.of( "1e-2" );
        Exception ex = assertThrows( UnsupportedOperationException.class, node::isEmpty );
        assertEquals( "NUMBER node has no empty property.", ex.getMessage() );
        ex = assertThrows( UnsupportedOperationException.class, node::size );
        assertEquals( "NUMBER node has no size property.", ex.getMessage() );
        ex = assertThrows( UnsupportedOperationException.class, node::elements );
        assertEquals( "NUMBER node has no elements property.", ex.getMessage() );
        ex = assertThrows( UnsupportedOperationException.class, node::members );
        assertEquals( "NUMBER node has no members property.", ex.getMessage() );
    }

    @Test
    public void testNumberNode_EOI()
    {
        JsonNode node = JsonNode.of( "-" );
        JsonFormatException ex = assertThrows( JsonFormatException.class, node::value );
        assertEquals( "Expected character but reached EOI: -", ex.getMessage() );
    }

    @Test
    public void testNumberNode_Long()
    {
        JsonNode node = JsonNode.of( "2147483648" );
        assertEquals( JsonNodeType.NUMBER, node.getType() );
        assertEquals( 2147483648L, node.value() );
    }

    @Test
    public void testBooleanNode_True()
    {
        JsonNode node = JsonNode.of( "true" );
        assertEquals( JsonNodeType.BOOLEAN, node.getType() );
        assertEquals( true, node.value() );
    }

    @Test
    public void testBooleanNode_Unsupported()
    {
        JsonNode node = JsonNode.of( "false" );
        Exception ex = assertThrows( UnsupportedOperationException.class, node::isEmpty );
        assertEquals( "BOOLEAN node has no empty property.", ex.getMessage() );
        ex = assertThrows( UnsupportedOperationException.class, node::size );
        assertEquals( "BOOLEAN node has no size property.", ex.getMessage() );
        ex = assertThrows( UnsupportedOperationException.class, node::elements );
        assertEquals( "BOOLEAN node has no elements property.", ex.getMessage() );
        ex = assertThrows( UnsupportedOperationException.class, node::members );
        assertEquals( "BOOLEAN node has no members property.", ex.getMessage() );
    }

    @Test
    public void testBooleanNode_False()
    {
        JsonNode node = JsonNode.of( "false" );
        assertEquals( JsonNodeType.BOOLEAN, node.getType() );
        assertEquals( false, node.value() );
    }

    @Test
    public void testNullNode()
    {
        JsonNode node = JsonNode.of( "null" );
        assertEquals( JsonNodeType.NULL, node.getType() );
        assertNull( node.value() );
    }

    @Test
    public void testNullNode_Unsupported()
    {
        JsonNode node = JsonNode.of( "null" );
        Exception ex = assertThrows( UnsupportedOperationException.class, node::isEmpty );
        assertEquals( "NULL node has no empty property.", ex.getMessage() );
        ex = assertThrows( UnsupportedOperationException.class, node::size );
        assertEquals( "NULL node has no size property.", ex.getMessage() );
        ex = assertThrows( UnsupportedOperationException.class, node::elements );
        assertEquals( "NULL node has no elements property.", ex.getMessage() );
        ex = assertThrows( UnsupportedOperationException.class, node::members );
        assertEquals( "NULL node has no members property.", ex.getMessage() );
    }

    @Test
    public void testArray_IndexOutOfBounds()
    {
        JsonDocument doc = new JsonDocument( "[]" );
        assertThrows( JsonPathException.class, () -> doc.get( "$[0]" ) );
    }

    @Test
    public void testArray_Numbers()
    {
        JsonNode node = JsonNode.of( "[1, 2 ,3]" );
        assertEquals( JsonNodeType.ARRAY, node.getType() );
        assertFalse( node.isEmpty() );
        assertEquals( 3, node.size() );
    }

    @Test
    public void testArray_Unsupported()
    {
        JsonNode node = JsonNode.of( "[]" );
        Exception ex = assertThrows( UnsupportedOperationException.class, node::members );
        assertEquals( "ARRAY node has no members property.", ex.getMessage() );
    }

    @Test
    public void testArray_IterateElements()
    {
        JsonDocument doc = new JsonDocument( "[ 1,2 , true , false, \"hello\",{},[]]" );

        JsonNode root = doc.get( "$" );

        Iterator<JsonNode> elements = root.elements( false );
        JsonNode e0 = elements.next();
        JsonNode e1 = elements.next();
        JsonNode e2 = elements.next();
        JsonNode e3 = elements.next();
        JsonNode e4 = elements.next();
        JsonNode e5 = elements.next();
        JsonNode e6 = elements.next();
        assertFalse( elements.hasNext() );
        assertThrows( NoSuchElementException.class, elements::next );
        assertEquals( 1, e0.value() );
        assertEquals( 2, e1.value() );
        assertEquals( true, e2.value() );
        assertEquals( false, e3.value() );
        assertEquals( "hello", e4.value() );
        assertEquals( emptyMap(), e5.value() );
        assertEquals( emptyList(), e6.value() );

        JsonNode e0kept = root.elements( true ).next();
        assertNotSame( e0, e0kept );
        assertSame( e0kept, root.elements( false ).next() );
    }

    @Test
    public void testObject_Flat()
    {
        JsonNode root = JsonNode.of( "{\"a\":1, \"bb\":true , \"ccc\":null }" );
        assertEquals( JsonNodeType.OBJECT, root.getType() );
        assertFalse( root.isEmpty() );
        assertEquals( 3, root.size() );
        Map<String, JsonNode> members = root.members();
        assertEquals( new HashSet<>( asList( "a", "bb", "ccc" ) ), members.keySet() );
    }

    @Test
    public void testObject_Deep()
    {
        JsonDocument doc = new JsonDocument( "{\"a\": { \"b\" : [12, false] } }" );

        JsonNode root = doc.get( "$" );
        assertEquals( JsonNodeType.OBJECT, root.getType() );
        assertFalse( root.isEmpty() );
        assertEquals( 1, root.size() );

        JsonNode a = doc.get( "$.a" );
        assertEquals( JsonNodeType.OBJECT, a.getType() );
        assertFalse( a.isEmpty() );
        assertEquals( 1, a.size() );

        JsonNode ab = doc.get( "$.a.b" );
        assertEquals( JsonNodeType.ARRAY, ab.getType() );
        assertFalse( ab.isEmpty() );
        assertEquals( 2, ab.size() );
        assertEquals( "[12, false]", ab.getDeclaration() );

        JsonNode ab0 = doc.get( "$.a.b[0]" );
        assertEquals( JsonNodeType.NUMBER, ab0.getType() );
        assertEquals( 12, ab0.value() );

        JsonNode ab1 = doc.get( "$.a.b[1]" );
        assertEquals( JsonNodeType.BOOLEAN, ab1.getType() );
        assertEquals( false, ab1.value() );
    }

    /**
     * This test might look very much the same as the above but this test avoid
     * accessing the object fields or array elements before using
     * {@link JsonDocument#get(String)} to resolve inner object so that these
     * would not already be in the internal map but would need to be resolved by
     * going the path backwards.
     */
    @Test
    public void testObject_DeepAccess()
    {
        JsonDocument doc = new JsonDocument( "{\"a\": { \"b\" : [12, false] } }" );

        JsonNode root = doc.get( "$" );
        assertEquals( JsonNodeType.OBJECT, root.getType() );

        JsonNode a = doc.get( "$.a" );
        assertEquals( JsonNodeType.OBJECT, a.getType() );

        JsonNode ab = doc.get( "$.a.b" );
        assertEquals( JsonNodeType.ARRAY, ab.getType() );

        JsonNode ab0 = doc.get( "$.a.b[0]" );
        assertEquals( JsonNodeType.NUMBER, ab0.getType() );
        assertEquals( 12, ab0.value() );

        JsonNode ab1 = doc.get( "$.a.b[1]" );
        assertEquals( JsonNodeType.BOOLEAN, ab1.getType() );
        assertEquals( false, ab1.value() );
    }

    @Test
    public void testObject_IterateMembers()
    {
        JsonDocument doc = new JsonDocument( "{\"a\": 1,\"b\":2 ,\"c\": true ,\"d\":false}" );

        JsonNode root = doc.get( "$" );

        Iterator<Entry<String, JsonNode>> members = root.members( false );
        Entry<String, JsonNode> m1 = members.next();
        Entry<String, JsonNode> m2 = members.next();
        Entry<String, JsonNode> m3 = members.next();
        Entry<String, JsonNode> m4 = members.next();
        assertFalse( members.hasNext() );
        assertThrows( NoSuchElementException.class, members::next );
        assertEquals( "a", m1.getKey() );
        assertEquals( 1, m1.getValue().value() );
        assertEquals( "b", m2.getKey() );
        assertEquals( 2, m2.getValue().value() );
        assertEquals( "c", m3.getKey() );
        assertEquals( true, m3.getValue().value() );
        assertEquals( "d", m4.getKey() );
        assertEquals( false, m4.getValue().value() );

        JsonNode m1kept = root.members( true ).next().getValue();
        assertNotSame( m1.getValue(), m1kept );
        assertSame( m1kept, root.members( true ).next().getValue() );
    }

    @Test
    public void testObject_Unsupported()
    {
        JsonNode node = JsonNode.of( "{}" );
        Exception ex = assertThrows( UnsupportedOperationException.class, node::elements );
        assertEquals( "OBJECT node has no elements property.", ex.getMessage() );
    }

    @Test
    public void testObject_NoSuchProperty()
    {
        JsonDocument doc = new JsonDocument( "{\"a\": { \"b\" : [12, false] } }" );

        JsonPathException ex = assertThrows( JsonPathException.class, () -> doc.get( ".a.notFound" ) );
        assertEquals( "Path `.a.notFound` does not exist, object `.a` does not have a property `notFound`",
            ex.getMessage() );
    }

    @Test
    public void testObject_NoSuchIndex()
    {
        JsonDocument doc = new JsonDocument( "{\"a\": { \"b\" : [12, false] } }" );

        JsonPathException ex = assertThrows( JsonPathException.class, () -> doc.get( ".a.b[3]" ) );
        assertEquals( "Path `.a.b[3]` does not exist, array `.a.b` has only `2` elements.",
            ex.getMessage() );
    }

    @Test
    public void testObject_WrongNodeTypeArray()
    {
        JsonDocument doc = new JsonDocument( "{\"a\": { \"b\" : 42 } }" );

        JsonPathException ex = assertThrows( JsonPathException.class, () -> doc.get( ".a.b[1]" ) );
        assertEquals( "Path `.a.b[1]` does not exist, parent `.a.b` is not an ARRAY but a NUMBER node.",
            ex.getMessage() );
    }

    @Test
    public void testObject_WrongNodeTypeObject()
    {
        JsonDocument doc = new JsonDocument( "{\"a\": 42 }" );

        JsonPathException ex = assertThrows( JsonPathException.class, () -> doc.get( ".a.b.[1]" ) );
        assertEquals( "Path `.a.b.[1]` does not exist, parent `.a` is not an OBJECT but a NUMBER node.",
            ex.getMessage() );
    }

    @Test
    public void testString_MissingQuotes()
    {
        JsonDocument doc = new JsonDocument( "{\"a\": hello }" );

        JsonFormatException ex = assertThrows( JsonFormatException.class, () -> doc.get( ".a" ) );
        assertEquals(
            "Unexpected character at position 6," + System.getProperty( "line.separator" ) + "{\"a\": hello }"
                + System.getProperty( "line.separator" ) + "      ^ expected start of value",
            ex.getMessage() );
    }

    @Test
    public void testNull()
    {
        JsonNode node = JsonNode.of( "null" );
        assertEquals( JsonNodeType.NULL, node.getType() );
        assertNull( node.value() );
    }

    @Test
    public void testExtractAndReplace()
    {
        JsonDocument doc = new JsonDocument( "{\"a\": { \"b\" : [12, false] } }" );
        JsonNode onlyA = doc.get( "$.a" ).extract();
        assertEquals( "{ \"b\" : [12, false] }", onlyA.toString() );
        assertEquals( "{ \"b\" : [42, false] }",
            onlyA.members().get( "b" ).elements().get( 0 ).replaceWith( "42" ).toString() );
    }

    @Test
    public void testAdd()
    {
        JsonDocument doc = new JsonDocument( "{\"a\": { \"b\" : [12, false] } }" );
        assertEquals( "{\"a\": { \"b\" : [12, false] , \"c\":{}} }",
            doc.get( ".a" ).addMember( "c", "{}" ).toString() );
    }

    @Test
    public void testVisit()
    {
        JsonDocument doc = new JsonDocument( "{\"a\": { \"b\" : [12, false, \"hello\"] } }" );
        JsonNode root = doc.get( "$" );
        assertEquals( 2, root.count( JsonNodeType.OBJECT ) );
        assertEquals( 1, root.count( JsonNodeType.NUMBER ) );
        assertEquals( 1, root.count( JsonNodeType.BOOLEAN ) );
        assertEquals( 1, root.count( JsonNodeType.STRING ) );
        assertEquals( 1, root.count( JsonNodeType.ARRAY ) );
        assertEquals( 0, root.count( JsonNodeType.NULL ) );
    }
}
