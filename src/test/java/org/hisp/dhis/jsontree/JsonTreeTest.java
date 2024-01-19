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

import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests the fundamental properties of the {@link JsonTree} JSON path extractor.
 *
 * @author Jan Bernitt
 */
class JsonTreeTest {

    @Test
    void testStringNode() {
        JsonNode node = JsonNode.of( "\"hello\"" );
        assertEquals( JsonNodeType.STRING, node.getType() );
        assertEquals( "hello", node.value() );
        assertEquals( 0, node.startIndex() );
        assertSame( node, node.getParent() );
        assertSame( node, node.getRoot() );
    }

    @Test
    void testStringNode_Unicode() {
        // use an array to see that unicode skipping works as well
        JsonNode node0 = JsonNode.of( "[\"Star \\uD83D\\uDE80 ship\", 12]" ).get( "[0]" );
        assertEquals( JsonNodeType.STRING, node0.getType() );
        assertEquals( "Star \uD83D\uDE80 ship", node0.value() );
        JsonNode node1 = JsonNode.of( "[\"Star \\uD83D\\uDE80 ship\", 12]" ).get( "[1]" );
        assertEquals( JsonNodeType.NUMBER, node1.getType() );
        assertEquals( 12, node1.value() );
    }

    @Test
    void testStringNode_EscapedChars() {
        JsonNode node = JsonNode.of( "\"\\\\\\/\\t\\r\\n\\f\\b\\\"\"" );
        assertEquals( "\\/\t\r\n\f\b\"", node.value() );
    }

    @Test
    void testStringNode_Unsupported() {
        JsonNode node = JsonNode.of( "\"hello\"" );
        Exception ex = assertThrowsExactly( JsonTreeException.class, node::isEmpty );
        assertEquals( "STRING node has no empty property.", ex.getMessage() );
        ex = assertThrowsExactly( JsonTreeException.class, node::size );
        assertEquals( "STRING node has no size property.", ex.getMessage() );
        ex = assertThrowsExactly( JsonTreeException.class, node::elements );
        assertEquals( "STRING node has no elements property.", ex.getMessage() );
        ex = assertThrowsExactly( JsonTreeException.class, node::members );
        assertEquals( "STRING node has no members property.", ex.getMessage() );
    }

    @Test
    void testStringNode_EOI() {
        JsonNode node = JsonNode.of( "\"hello" );
        JsonFormatException ex = assertThrowsExactly( JsonFormatException.class, node::value );
        assertEquals( "Expected \" but reach EOI: \"hello", ex.getMessage() );
    }

    @Test
    void testNumberNode_Integer() {
        JsonNode node = JsonNode.of( "123" );
        assertEquals( JsonNodeType.NUMBER, node.getType() );
        assertEquals( 123, node.value() );
        assertSame( node, node.getParent() );
        assertSame( node, node.getRoot() );
    }

    @Test
    void testNumberNode_Unsupported() {
        JsonNode node = JsonNode.of( "1e-2" );
        Exception ex = assertThrowsExactly( JsonTreeException.class, node::isEmpty );
        assertEquals( "NUMBER node has no empty property.", ex.getMessage() );
        ex = assertThrowsExactly( JsonTreeException.class, node::size );
        assertEquals( "NUMBER node has no size property.", ex.getMessage() );
        ex = assertThrowsExactly( JsonTreeException.class, node::elements );
        assertEquals( "NUMBER node has no elements property.", ex.getMessage() );
        ex = assertThrowsExactly( JsonTreeException.class, node::members );
        assertEquals( "NUMBER node has no members property.", ex.getMessage() );
    }

    @Test
    void testNumberNode_EOI() {
        JsonNode node = JsonNode.of( "-" );
        JsonFormatException ex = assertThrowsExactly( JsonFormatException.class, node::value );
        assertEquals( "Expected character but reached EOI: -", ex.getMessage() );
    }

    @Test
    void testNumberNode_Long() {
        JsonNode node = JsonNode.of( "2147483648" );
        assertEquals( JsonNodeType.NUMBER, node.getType() );
        assertEquals( 2147483648L, node.value() );
    }

    @Test
    void testBooleanNode_True() {
        JsonNode node = JsonNode.of( "true" );
        assertEquals( JsonNodeType.BOOLEAN, node.getType() );
        assertEquals( true, node.value() );
        assertSame( node, node.getParent() );
        assertSame( node, node.getRoot() );
    }

    @Test
    void testBooleanNode_Unsupported() {
        JsonNode node = JsonNode.of( "false" );
        Exception ex = assertThrowsExactly( JsonTreeException.class, node::isEmpty );
        assertEquals( "BOOLEAN node has no empty property.", ex.getMessage() );
        ex = assertThrowsExactly( JsonTreeException.class, node::size );
        assertEquals( "BOOLEAN node has no size property.", ex.getMessage() );
        ex = assertThrowsExactly( JsonTreeException.class, node::elements );
        assertEquals( "BOOLEAN node has no elements property.", ex.getMessage() );
        ex = assertThrowsExactly( JsonTreeException.class, node::members );
        assertEquals( "BOOLEAN node has no members property.", ex.getMessage() );
    }

    @Test
    void testBooleanNode_False() {
        JsonNode node = JsonNode.of( "false" );
        assertEquals( JsonNodeType.BOOLEAN, node.getType() );
        assertEquals( false, node.value() );
    }

    @Test
    void testNullNode() {
        JsonNode node = JsonNode.of( "null" );
        assertEquals( JsonNodeType.NULL, node.getType() );
        assertNull( node.value() );
        assertSame( node, node.getParent() );
        assertSame( node, node.getRoot() );
    }

    @Test
    void testNullNode_Unsupported() {
        JsonNode node = JsonNode.of( "null" );
        Exception ex = assertThrowsExactly( JsonTreeException.class, node::isEmpty );
        assertEquals( "NULL node has no empty property.", ex.getMessage() );
        ex = assertThrowsExactly( JsonTreeException.class, node::size );
        assertEquals( "NULL node has no size property.", ex.getMessage() );
        ex = assertThrowsExactly( JsonTreeException.class, node::elements );
        assertEquals( "NULL node has no elements property.", ex.getMessage() );
        ex = assertThrowsExactly( JsonTreeException.class, node::members );
        assertEquals( "NULL node has no members property.", ex.getMessage() );
    }

    @Test
    void testArray_IndexOutOfBounds() {
        JsonNode doc = JsonNode.of( "[]" );
        assertThrowsExactly( JsonPathException.class, () -> doc.get( "[0]" ) );
    }

    @Test
    void testArray_Numbers() {
        JsonNode node = JsonNode.of( "[1, 2 ,3]" );
        assertEquals( JsonNodeType.ARRAY, node.getType() );
        assertFalse( node.isEmpty() );
        assertEquals( 3, node.size() );
        assertSame( node, node.getParent() );
        assertSame( node, node.getRoot() );
        assertSame( node, node.get( "[0]" ).getParent() );
        assertSame( node, node.get( "[0]" ).getRoot() );
    }

    @Test
    void testArray_Unsupported() {
        JsonNode node = JsonNode.of( "[]" );
        Exception ex = assertThrowsExactly( JsonTreeException.class, node::members );
        assertEquals( "ARRAY node has no members property.", ex.getMessage() );
    }

    @Test
    void testArray_IterateElements() {
        JsonNode root = JsonNode.of( "[ 1,2 , true , false, \"hello\",{},[]]" );

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
        assertEquals( "{}", e5.getDeclaration() );
        assertEquals( "[]", e6.getDeclaration() );

        JsonNode e0kept = root.elements( true ).next();
        assertNotSame( e0, e0kept );
        assertSame( e0kept, root.elements( false ).next() );
    }

    @Test
    void testArray_IndexAccessElements() {
        JsonNode root = JsonNode.of( "[ 1,2 , true , false, \"hello\",{},[]]" );

        assertEquals( 1, root.element( 0 ).value() );
        assertEquals( 2, root.element( 1 ).value() );
        assertEquals( true, root.element( 2 ).value() );
        assertEquals( false, root.element( 3 ).value() );
        assertEquals( "hello", root.element( 4 ).value() );
        assertEquals( "{}", root.element( 5 ).getDeclaration() );
        assertEquals( "[]", root.element( 6 ).getDeclaration() );
    }

    @Test
    void testArray_IndexAccessElementsReverseOrder() {
        JsonNode root = JsonNode.of( "[ 1,2 , true , false, \"hello\",{},[]]" );

        assertEquals( "[]", root.element( 6 ).getDeclaration() );
        assertEquals( "{}", root.element( 5 ).getDeclaration() );
        assertEquals( "hello", root.element( 4 ).value() );
        assertEquals( false, root.element( 3 ).value() );
        assertEquals( true, root.element( 2 ).value() );
        assertEquals( 2, root.element( 1 ).value() );
        assertEquals( 1, root.element( 0 ).value() );
    }

    @Test
    void testArray_IndexAccessElementsRandomOrder() {
        JsonNode root = JsonNode.of( "[ 1,2 , true , false, \"hello\",{},[]]" );

        assertEquals( "hello", root.element( 4 ).value() );
        assertEquals( "[]", root.element( 6 ).getDeclaration() );
        assertEquals( "{}", root.element( 5 ).getDeclaration() );
        assertEquals( 2, root.element( 1 ).value() );
        assertEquals( false, root.element( 3 ).value() );
        assertEquals( true, root.element( 2 ).value() );
        assertEquals( 1, root.element( 0 ).value() );
    }

    @Test
    void testObject_Flat() {
        JsonNode root = JsonNode.of( "{\"a\":1, \"bb\":true , \"ccc\":null }" );
        assertEquals( JsonNodeType.OBJECT, root.getType() );
        assertFalse( root.isEmpty() );
        assertEquals( 3, root.size() );
        assertEquals( List.of( "a", "bb", "ccc" ), JsonValue.of( root ).asObject().names() );
        assertSame( root, root.getParent() );
        assertSame( root, root.getRoot() );
        assertSame( root, root.get( "a" ).getParent() );
        assertSame( root, root.get( "a" ).getRoot() );
    }

    @Test
    void testObject_Deep() {
        JsonNode doc = JsonNode.of( "{\"a\": { \"b\" : [12, false] } }" );

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
        assertSame( a, ab.getParent() );
        assertSame( root, ab.getRoot() );

        JsonNode ab0 = doc.get( "$.a.b[0]" );
        assertEquals( JsonNodeType.NUMBER, ab0.getType() );
        assertEquals( 12, ab0.value() );
        assertSame( ab, ab0.getParent() );
        assertSame( root, ab0.getRoot() );

        JsonNode ab1 = doc.get( "$.a.b[1]" );
        assertEquals( JsonNodeType.BOOLEAN, ab1.getType() );
        assertEquals( false, ab1.value() );
    }

    /**
     * This test might look very much the same as the above but this test avoids accessing the object fields or array
     * elements before using {@link JsonNode#get(String)} to resolve inner object so that these would not already be in
     * the internal map but would need to be resolved by going the path backwards.
     */
    @Test
    void testObject_DeepAccess() {
        JsonNode doc = JsonNode.of( "{\"a\": { \"b\" : [12, false] } }" );

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
    void testObject_DotInMemberName() {
        //language=JSON
        String json = """
            {
                "some.thing": 42,
                "foo.bar": { "answer": 42 },
                "a": { "b.c": "d"},
                "x": { "y.z": { "answer": 42 }},
                "obj": {"x": { "y.z": { "answer": 42 }}}
            }""";
        JsonNode doc = JsonNode.of( json );
        assertEquals( "\"d\"", doc.get( "a{b.c}" ).getDeclaration() );
        assertEquals( "42", doc.get( "{some.thing}" ).getDeclaration() );
        assertEquals( "42", doc.get( "{foo.bar}.answer" ).getDeclaration() );
        assertEquals( "42", doc.get( "x{y.z}.answer" ).getDeclaration() );
        assertEquals( "42", doc.get( "obj.x{y.z}.answer" ).getDeclaration() );
    }

    @Test
    void testObject_IterateMembers() {
        JsonNode doc = JsonNode.of( "{\"a\": 1,\"b\":2 ,\"c\": true ,\"d\":false}" );

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
    void testObject_Member() {
        JsonNode doc = JsonNode.of( "{\"a\": 1,\"b\":2 ,\"c\": true ,\"d\":false}" );

        assertEquals( 1, doc.member( "a" ).value() );
        assertEquals( 2, doc.member( "b" ).value() );
        assertEquals( false, doc.member( "d" ).value() );
        assertEquals( true, doc.member( "c" ).value() );
    }

    @Test
    void testObject_MemberDoesNotExist() {
        JsonNode doc = JsonNode.of( "{\"a\": 1,\"b\":2 ,\"c\": true ,\"d\":false}" );

        JsonPathException ex = assertThrowsExactly( JsonPathException.class, () -> doc.member( "missing" ) );
        assertEquals( "Path `.missing` does not exist, object `` does not have a property `missing`", ex.getMessage() );
    }

    @Test
    void testObject_MemberDoesNotExist2() {
        JsonNode doc = JsonNode.of( "{\"a\": 1,\"b\":2 ,\"c\": true ,\"d\":false}" );

        doc.members(); // make sure value is set
        assertNotNull( doc.member( "a" ) );

        JsonPathException ex = assertThrowsExactly( JsonPathException.class, () -> doc.member( "no" ) );
        assertEquals( "Path `.no` does not exist, object `` does not have a property `no`", ex.getMessage() );
    }

    @Test
    void testObject_Unsupported() {
        JsonNode node = JsonNode.of( "{}" );
        Exception ex = assertThrowsExactly( JsonTreeException.class, node::elements );
        assertEquals( "OBJECT node has no elements property.", ex.getMessage() );
    }

    @Test
    void testObject_NoSuchProperty() {
        JsonNode doc = JsonNode.of( "{\"a\": { \"b\" : [12, false] } }" );

        JsonPathException ex = assertThrowsExactly( JsonPathException.class, () -> doc.get( ".a.notFound" ) );
        assertEquals( "Path `.a.notFound` does not exist, object `.a` does not have a property `notFound`",
            ex.getMessage() );
    }

    @Test
    void testArray_NoSuchIndex() {
        JsonNode doc = JsonNode.of( "{\"a\": { \"b\" : [12, false] } }" );

        JsonPathException ex = assertThrowsExactly( JsonPathException.class, () -> doc.get( ".a.b[3]" ) );
        assertEquals( "Path `.a.b[3]` does not exist, array `.a.b` has only `2` elements.",
            ex.getMessage() );
    }

    @Test
    void testArray_NegativeIndex() {
        JsonNode doc = JsonNode.of( "{\"a\": { \"b\" : [12, false] } }" );
        JsonPathException ex = assertThrowsExactly( JsonPathException.class, () -> doc.get( ".a.b[-1]" ) );
        assertEquals( "Path `.a.b` does not exist, array index is negative: -1",
            ex.getMessage() );
    }

    @Test
    void testObject_WrongNodeTypeArray() {
        JsonNode doc = JsonNode.of( "{\"a\": { \"b\" : 42 } }" );

        JsonPathException ex = assertThrowsExactly( JsonPathException.class, () -> doc.get( ".a.b[1]" ) );
        assertEquals( "Path `.a.b[1]` does not exist, parent `.a.b` is not an ARRAY but a NUMBER node.",
            ex.getMessage() );
    }

    @Test
    void testObject_WrongNodeTypeObject() {
        JsonNode doc = JsonNode.of( "{\"a\": 42 }" );

        JsonPathException ex = assertThrowsExactly( JsonPathException.class, () -> doc.get( ".a.b.[1]" ) );
        assertEquals( "Path `.a.b.[1]` does not exist, parent `.a` is not an OBJECT but a NUMBER node.",
            ex.getMessage() );
    }

    @Test
    void testString_MissingQuotes() {
        JsonNode doc = JsonNode.of( "{\"a\": hello }" );

        JsonFormatException ex = assertThrowsExactly( JsonFormatException.class, () -> doc.get( ".a" ) );
        String nl = System.getProperty( "line.separator" );
        assertEquals(
            "Unexpected character at position 6," + nl + "{\"a\": hello }"
                + nl + "      ^ expected start of a JSON value but found: `h`",
            ex.getMessage() );
    }

    @Test
    void testNull() {
        JsonNode node = JsonNode.of( "null" );
        assertEquals( JsonNodeType.NULL, node.getType() );
        assertNull( node.value() );
    }

    @Test
    void testExtractAndReplace() {
        JsonNode doc = JsonNode.of( "{\"a\": { \"b\" : [12, false] } }" );
        JsonNode onlyA = doc.get( "$.a" ).extract();
        assertEquals( "{ \"b\" : [12, false] }", onlyA.toString() );
        assertEquals( "{ \"b\" : [42, false] }",
            onlyA.member( "b" ).element( 0 ).replaceWith( "42" ).toString() );
    }

    @Test
    void testAddMembers_Empty() {
        JsonNode num = JsonNode.of( "{}" ).addMember( "num", "1" );
        assertEquals( "{\"num\":1}", num.getDeclaration() );
        assertSame( num, num.addMembers( JsonNode.of( "{}" ) ) );
        JsonNode root = JsonBuilder.createObject( obj -> obj
            .addNumber( "num", 42 )
            .addMember( "sub", num ) );
        assertSame( root, root.addMembers( "sub", JsonNode.EMPTY_OBJECT ) );
    }

    @Test
    void testAddMembers_Multiple() {
        assertEquals( "{\"num\":1,\"str\":\"hello\"}", JsonNode.of( "{}" ).addMembers( obj -> obj
            .addNumber( "num", 1 )
            .addString( "str", "hello" ) ).getDeclaration() );
    }

    @Test
    void testAddMembers_Replace() {
        assertEquals( "{\"num\":2}", JsonNode.of( "{\"num\":1}" ).addMembers(
            obj -> obj.addNumber( "num", 2 ) ).getDeclaration() );
    }

    @Test
    void testAddMembers_ReplaceMixed() {
        JsonNode before = JsonNode.EMPTY_OBJECT.addMembers( obj ->
            obj.addNumber( "num", 1 ).addString( "str", "hello" ).addBoolean( "bo", true ) );
        assertEquals( "{\"num\":1,\"bo\":true,\"str\":\"world\",\"pi\":3.1415}", before.addMembers(
            obj -> obj.addString( "str", "world" ).addNumber( "pi", 3.1415d ) ).getDeclaration() );
    }

    @Test
    void testAddMember_Nested() {
        //language=JSON
        String json = """
            {"a":{"b":[12,false]}}""";
        JsonNode doc = JsonNode.of( json );
        //language=JSON
        String expected = """
            {"a":{"b":[12,false],"c":{}}}""";
        assertEquals( expected,
            doc.get( ".a" ).addMember( "c", "{}" ).toString() );
    }

    @Test
    void testAddMember_WrongNodeTypes() {
        assertThrowsExactly( JsonTreeException.class, () -> JsonNode.EMPTY_OBJECT.addMembers( JsonNode.NULL ) );
        assertThrowsExactly( JsonTreeException.class, () -> JsonNode.EMPTY_ARRAY.addMembers( JsonNode.EMPTY_OBJECT ) );
    }

    @Test
    void testRemoveMembers() {
        JsonNode root = JsonBuilder.createObject( obj -> obj
            .addNumber( "num", 1 )
            .addString( "str", "zZZZ" )
            .addBoolean( "right", true )
            .addArray( "nums", arr -> arr.addNumbers( 1, 2, 3 ) ) );

        assertEquals( "{\"num\":1,\"right\":true}",
            root.removeMembers( Set.of( "str", "foo", "nums" ) ).getDeclaration() );
    }

    @Test
    void testRemoveMembers_WrongNodeTypes() {
        assertThrowsExactly( JsonTreeException.class, () -> JsonNode.NULL.removeMembers( Set.of() ) );
    }

    @Test
    void testAddElements_Empty() {
        assertEquals( "[2,\"tree\"]", JsonNode.EMPTY_ARRAY.addElements( arr -> arr
            .addNumber( 2 ).addString( "tree" ) ).getDeclaration() );
        JsonNode array = JsonBuilder.createArray( arr -> arr.addNumber( 42 ) );
        assertSame( array, array.addElements( JsonNode.EMPTY_ARRAY ) );
        assertSame( array, JsonNode.EMPTY_ARRAY.addElements( array ) );
    }

    @Test
    void testAddElements_Nested() {
        JsonNode root = JsonBuilder.createObject( obj -> obj
            .addArray( "a", arr -> arr.addNumber( 42 ) ) );
        assertEquals( "{\"a\":[42,2]}", root.addElements( "a", arr -> arr.addNumber( 2 ) ).getDeclaration() );
        assertSame( root, root.addElements( "a", JsonNode.EMPTY_ARRAY ) );
    }

    @Test
    void testAddElements_WrongNodeTypes() {
        assertThrowsExactly( JsonTreeException.class, () -> JsonNode.EMPTY_OBJECT.addElements( JsonNode.EMPTY_ARRAY ) );
        assertThrowsExactly( JsonTreeException.class, () -> JsonNode.EMPTY_ARRAY.addElements( JsonNode.EMPTY_OBJECT ) );
    }

    @Test
    void testPutElements_Empty() {
        JsonNode array = JsonBuilder.createArray( arr -> arr.addNumber( 42 ) );
        assertSame( array, array.putElements( 0, JsonNode.EMPTY_ARRAY ) );
    }

    @Test
    void testPutElements_Flat() {
        JsonNode array = JsonBuilder.createArray( arr -> arr.addNumber( 42 ) );
        assertEquals( "[42,null,555]", array.putElements( 2, arr -> arr.addNumber( 555 ) ).getDeclaration() );
    }

    @Test
    void testPutElements_Nested() {
        JsonNode root = JsonBuilder.createObject( obj -> obj.addArray( "arr", arr -> arr.addNumber( 42 ) ) );
        assertEquals( "{\"arr\":[\"hello\",42]}",
            root.get( "arr" ).putElements( 0, arr -> arr.addString( "hello" ) ).getDeclaration() );
        assertSame( root, root.get( "arr" ).putElements( 0, JsonNode.EMPTY_ARRAY ) );
    }

    @Test
    void testPutElements_WrongNodeTypes() {
        assertThrowsExactly( JsonTreeException.class,
            () -> JsonNode.EMPTY_OBJECT.putElements( 0, JsonNode.EMPTY_ARRAY ) );
        assertThrowsExactly( JsonTreeException.class,
            () -> JsonNode.EMPTY_ARRAY.putElements( 0, JsonNode.EMPTY_OBJECT ) );
    }

    @Test
    void testRemoveElements_Empty() {
        assertSame( JsonNode.EMPTY_ARRAY, JsonNode.EMPTY_ARRAY.removeElements( 0 ) );
        JsonNode array = JsonBuilder.createArray( arr -> arr.addNumber( 1 ).addNumber( 2 ).addNumber( 3 ) );
        assertSame( array, array.removeElements( 3 ) );
        assertEquals( "[1,3]", array.removeElements( 1, 2 ).getDeclaration() );
        assertEquals( "[3]", array.removeElements( 0, 2 ).getDeclaration() );
        assertEquals( "[2,3]", array.removeElements( 0, 1 ).getDeclaration() );
        assertEquals( "[1,2]", array.removeElements( 2, 5 ).getDeclaration() );
    }

    @Test
    void testRemoveElements_WrongNodeType() {
        assertThrowsExactly( JsonTreeException.class, () -> JsonNode.NULL.removeElements( 5 ) );
    }

    @Test
    void testIsElement_Empty() {
        assertFalse( JsonNode.EMPTY_ARRAY.isElement( 0 ) );
    }

    @Test
    void testIsElement_Simple() {
        JsonNode array = JsonBuilder.createArray( arr -> arr.addNumber( 42 ) );
        assertTrue( array.isElement( 0 ) );
        assertFalse( array.isElement( 1 ) );
        assertFalse( array.isElement( -1 ) );
    }

    @Test
    void testIsElement_WrongNodeType() {
        assertThrowsExactly( JsonTreeException.class, () -> JsonNode.NULL.isElement( 0 ) );
    }

    @Test
    void testVisit() {
        JsonNode doc = JsonNode.of( "{\"a\": { \"b\" : [12, false, \"hello\"] } }" );
        JsonNode root = doc.get( "$" );
        assertEquals( 2, root.count( JsonNodeType.OBJECT ) );
        assertEquals( 1, root.count( JsonNodeType.NUMBER ) );
        assertEquals( 1, root.count( JsonNodeType.BOOLEAN ) );
        assertEquals( 1, root.count( JsonNodeType.STRING ) );
        assertEquals( 1, root.count( JsonNodeType.ARRAY ) );
        assertEquals( 0, root.count( JsonNodeType.NULL ) );
    }

    @Test
    void testOfNonStandard_SingleQuotes() {
        assertEquals( "\"hello\"", JsonNode.ofNonStandard( "'hello'" ).getDeclaration() );
        assertEquals( "{\"hello\":42}", JsonNode.ofNonStandard( "{'hello':42}" ).getDeclaration() );
        assertEquals( "{\"hello\":\"you\"}", JsonNode.ofNonStandard( "{'hello':'you'}" ).getDeclaration() );
    }

    @Test
    void testOfNonStandard_DanglingCommas() {
        assertEquals( "[1,2 ]", JsonNode.ofNonStandard( "[1,2,]" ).getDeclaration() );
        assertEquals( "{\"a\":1 }", JsonNode.ofNonStandard( "{'a':1,}" ).getDeclaration() );
    }
}
