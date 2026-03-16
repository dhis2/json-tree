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

import static org.hisp.dhis.jsontree.JsonNode.Index.AUTO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests {@link JsonNode} specific aspects of the {@link JsonTree} implementation of the interface.
 *
 * @author Jan Bernitt
 */
class JsonNodeTest {

  @Test
  void testEquals() {
    assertEquals(JsonNode.of("1"), JsonNode.of("1"));
  }

  @Test
  void testGet_String() {
    assertGetThrowsJsonTreeException(
        "\"hello\"",
        "STRING node at path (root) is not a OBJECT and does not support #get(JsonPath) + .foo: \"hello\"");
  }

  @Test
  void testGetOrNull() {
    assertNull(JsonNode.of("{}").getIfExists("foo"));
    assertNull(JsonNode.of("[]").getIfExists("[1]"));
    assertThrowsExactly(JsonTreeException.class, () -> JsonNode.of("true").getIfExists("foo"));
    assertThrowsExactly(JsonTreeException.class, () -> JsonNode.of("1").getIfExists("foo"));
    assertThrowsExactly(JsonTreeException.class, () -> JsonNode.of("\"x\"").getIfExists("foo"));
  }

  @Test
  void testGet_Number() {
    assertGetThrowsJsonTreeException("42", "NUMBER node at path (root) is not a OBJECT and does not support #get(JsonPath) + .foo: 42");
  }

  @Test
  void testGet_Boolean() {
    assertGetThrowsJsonTreeException(
        "true", "BOOLEAN node at path (root) is not a OBJECT and does not support #get(JsonPath) + .foo: true");
  }

  @Test
  void testGet_Null() {
    assertGetThrowsJsonTreeException("null", "NULL node at path (root) is not a OBJECT and does not support #get(JsonPath) + .foo: null");
  }

  @Test
  void testGet_Object() {
    JsonNode root = JsonNode.of("{\"a\":{\"b\":{\"c\":42}}}");
    assertEquals(42, root.get("a.b.c").value());
    JsonNode b = root.get("a.b");
    assertEquals(42, b.get("c").value());
  }

  @Test
  void testGet_EmptyProperty() {
    JsonNode root =
        JsonNode.of(
            """
            {"": "hello"}""");
    assertSame(root, root.get(""));
    assertEquals(Text.of("hello"), root.get("{}").value());
  }

  @Test
  void testGet_Object_NoValueAtPath() {
    assertGetThrowsJsonPathException(
        "{\"a\":{\"b\":{\"c\":42}}}",
        "b",
        "Path `.b` does not exist, object `` does not have a property `b`");
    assertGetThrowsJsonPathException(
        "{\"a\":{\"b\":{\"c\":42}}}",
        "a.c",
        "Path `.a.c` does not exist, object `.a` does not have a property `c`");
  }

  @Test
  void testGet_Array() {
    JsonNode root = JsonNode.of("[[1,2],[3,4],{\"a\":5}]");
    assertEquals(1, root.get("[0][0]").value());
    JsonNode arr1 = root.get("[1]");
    assertEquals(4, arr1.get("[1]").value());
    assertEquals(5, root.get("[2].a").value());
    assertEquals(5, root.get("[2]").get("a").value());
  }

  @Test
  void testGet_Array_NoValueAtPath() {
    assertGetThrowsJsonTreeException(
        "[1,2]", "a", "ARRAY node at path (root) is not a OBJECT and does not support #get(Text) + .a: [1,2]");
    assertGetThrowsJsonTreeException(
        "[1,2]", ".a", "ARRAY node at path (root) is not a OBJECT and does not support #get(Text) + .a: [1,2]");
    assertGetThrowsJsonPathException(
        "[[1,2],[]]", "[1][0]", "Path `.1.0` does not exist, array `.1` has only `0` elements.");
    assertGetThrowsJsonTreeException(
        "[[1,2],[]]", "[0].a", "ARRAY node at path .0 is not a OBJECT and does not support #get(Text) + .a: [1,2]");
  }

  @Test
  void testMember_NoObject() {
    JsonNode val = JsonNode.of("1");
    JsonTreeException ex =
        assertThrowsExactly(JsonTreeException.class, () -> val.member(Text.of("a")));
    assertEquals("NUMBER node at path (root) is not a OBJECT and does not support #member(Text): 1", ex.getMessage());
  }

  @Test
  void testMembers_NoObject() {
    JsonNode val = JsonNode.of("1");
    JsonTreeException ex = assertThrowsExactly(JsonTreeException.class, () -> val.members(AUTO));
    assertEquals("NUMBER node at path (root) is not a OBJECT and does not support #members(Index): 1", ex.getMessage());
  }

  @Test
  void testElements_NoArray() {
    JsonNode val = JsonNode.of("1");
    JsonTreeException ex = assertThrowsExactly(JsonTreeException.class, () -> val.elements(AUTO));
    assertEquals("NUMBER node at path (root) is not a ARRAY and does not support #elements(Index): 1", ex.getMessage());
  }

  @Test
  void testReplaceWith_Path() {
    // language=json
    String json =
        """
            {
            "a": 1,
            "b": [2]
            }""";
    JsonNode obj = JsonNode.of(json);
    JsonNode actual = obj.replaceWith("b[0]", JsonNode.of("3"));
    assertEquals(
        """
            {
            "a": 1,
            "b": [3]
            }""",
        actual.getDeclaration().toString());
  }

  @Test
  void testAddMembers_Path() {
    // language=json
    String json =
        """
            {
            "a": 1,
            "b": {}
            }""";
    JsonNode actual = JsonNode.of(json).addMembers("b", obj -> obj.addNumber("x", 42));
    assertEquals(
        """
            {
            "a": 1,
            "b": {"x":42}
            }""",
        actual.getDeclaration().toString());
  }

  @Test
  void testRemoveMembers_Path() {
    // language=json
    String json =
        """
            {
            "a": 1,
            "b": {"x": 42, "y": 1, "z": 2}
            }""";
    JsonNode actual = JsonNode.of(json).removeMembers("b", Set.of("x", "z"));
    assertEquals(
        """
            {
            "a": 1,
            "b": {"y":1}
            }""",
        actual.getDeclaration().toString());
  }

  public interface JsonBean extends JsonObject {

    default String getFoo() {
      return getString("foo").string();
    }

    String bar();
  }

  @Test
  void testGetListener_DefaultMethod_DoesNotExist() {
    Deque<JsonPath> rec = new LinkedList<>();
    JsonObject obj = JsonMixed.of(JsonNode.of("{}", rec::add));
    assertNull(obj.as(JsonBean.class).getFoo());
    assertEquals(".foo", rec.getLast().toString());
  }

  @Test
  void testGetListener_DefaultMethod_DoesExist() {
    Deque<JsonPath> rec = new LinkedList<>();
    JsonObject obj = JsonMixed.of(JsonNode.of("{\"foo\": \"str\"}", rec::add));
    assertEquals("str", obj.as(JsonBean.class).getFoo());
    assertEquals(".foo", rec.getLast().toString());
  }

  @Test
  void testGetListener_AbstractMethod_DoesNotExist() {
    Deque<JsonPath> rec = new LinkedList<>();
    JsonObject obj = JsonMixed.of(JsonNode.of("{}", rec::add));
    assertNull(obj.as(JsonBean.class).bar());
    assertEquals(".bar", rec.getLast().toString());
  }

  @Test
  void testGetListener_AbstractMethod_DoesExist() {
    Deque<JsonPath> rec = new LinkedList<>();
    JsonObject obj = JsonMixed.of(JsonNode.of("{\"bar\": \"str\"}", rec::add));
    assertEquals("str", obj.as(JsonBean.class).bar());
    assertEquals(".bar", rec.getLast().toString());
  }

  private static void assertGetThrowsJsonPathException(String json, String path, String expected) {
    JsonNode root = JsonNode.of(json);
    JsonPathException ex = assertThrowsExactly(JsonPathException.class, () -> root.get(path));
    assertEquals(expected, ex.getMessage());
  }

  private static void assertGetThrowsJsonTreeException(String json, String expected) {
    assertGetThrowsJsonTreeException(json, ".foo", expected);
  }

  private static void assertGetThrowsJsonTreeException(String json, String path, String expected) {
    JsonNode root = JsonNode.of(json);
    JsonTreeException ex = assertThrowsExactly(JsonTreeException.class, () -> root.get(path));
    assertEquals(expected, ex.getMessage());
  }
}
