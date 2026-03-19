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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Tests the basic correctness of {@link JsonVirtualTree} which is the implementation of all core
 * interfaces of the {@link JsonValue} utility.
 *
 * @author Jan Bernitt
 */
class JsonVirtualTreeTest {

  @Test
  void testCustomObjectTypeMultiMap() {
    JsonMultiMap<JsonNumber> multiMap =
        Json5.of("{'foo':[1,23], 'bar': [34,56]}").asMultiMap(JsonNumber.class);
    assertFalse(multiMap.isEmpty());
    assertTrue(multiMap.isObject());
    assertEquals(23, multiMap.get("foo").get(1).intValue());
    assertEquals(34, multiMap.get("bar").get(0).intValue());
  }

  @Test
  void testObjectHas() {
    JsonObject obj = Json5.of("{'users': {'foo':{'id':'foo'}, 'bar':[]}}");
    assertTrue(obj.has("users"));
    assertTrue(obj.getObject("users").has("foo", "bar"));
    assertFalse(obj.has("no-a-member"));
    Exception ex =
        assertThrowsExactly(
            JsonTreeException.class, () -> JsonMixed.of("[]").getObject("undefined").has("foo"));
    assertEquals(
        "ARRAY node at path (root) is not a OBJECT and does not support #getOrNull(Text:\".undefined\"): []",
        ex.getMessage());
    JsonObject bar = obj.getObject("users").getObject("bar");
    ex = assertThrowsExactly(JsonTreeException.class, () -> bar.has("is-array"));
    assertEquals("ARRAY node at path .users.bar is not a OBJECT and does not support #isMember(Text): []", ex.getMessage());
  }

  @Test
  void testNumber() {
    JsonObject obj = Json5.of("{'number': 13, 'fraction': 4.2}");

    assertEquals(13, obj.getNumber("number").number());
    assertEquals(4.2f, obj.getNumber("fraction").number().floatValue(), 0.001f);
    assertTrue(obj.getNumber("number").exists());
    assertNull(obj.getNumber("missing").number());
  }

  @Test
  void testIntValue() {
    JsonObject obj = Json5.of("{'number':13}");
    assertEquals(13, obj.getNumber("number").intValue());
    JsonNumber missing = obj.getNumber("missing");
    assertThrowsExactly(JsonPathException.class, missing::intValue);
  }

  @Test
  void testString() {
    JsonObject obj = Json5.of("{'text': 'plain'}");

    assertEquals("plain", obj.getString("text").string());
    assertTrue(obj.getString("text").exists());
    assertNull(obj.getString("missing").string());
  }

  @Test
  void testBool() {
    JsonObject obj = Json5.of("{'flag': true}");

    assertTrue(obj.getBoolean("flag").bool());
    assertTrue(obj.getBoolean("flag").exists());
    assertNull(obj.getBoolean("missing").bool());
  }

  @Test
  void testNull() {
    JsonObject obj = Json5.of("{'value': null}");

    assertNull(obj.getBoolean("value").bool());
    assertNull(obj.getString("value").string());
    assertNull(obj.getNumber("value").number());
    assertTrue(obj.getObject("value").exists());
    assertTrue(obj.getArray("value").exists());
    assertTrue(obj.get("value").isNull());
    assertFalse(obj.get("value").isObject());
    assertFalse(obj.get("value").isArray());
  }

  @Test
  void testBooleanValue() {
    JsonObject obj = Json5.of("{'flag': true}");

    assertTrue(obj.getBoolean("flag").booleanValue());
    JsonBoolean missing = obj.getBoolean("missing");
    assertThrowsExactly(JsonPathException.class, missing::booleanValue);
  }

  @Test
  void testNotExists() {
    JsonObject obj = Json5.of("{'flag': true}");

    assertFalse(obj.getString("no").exists());
  }

  @Test
  void testSizeArray() {
    JsonObject obj = Json5.of("{'numbers': [1,2,3,4]}");

    assertEquals(4, obj.getArray("numbers").size());
    assertFalse(obj.getArray("numbers").isNull());
  }

  @Test
  void testStringValues() {
    JsonObject obj = Json5.of("{'letters': ['a','b','c']}");

    assertEquals(asList("a", "b", "c"), obj.getArray("letters").stringValues());
  }

  @Test
  void testNumberValues() {
    JsonObject obj = Json5.of("{'digits': [1,2,3]}");

    assertEquals(asList(1, 2, 3), obj.getArray("digits").numberValues());
  }

  @Test
  void testBoolValues() {
    JsonObject obj = Json5.of("{'flags': [true, false, true]}");

    assertEquals(asList(true, false, true), obj.getArray("flags").booleanValues());
  }

  @Test
  void testArrayValuesMappedView() {
    JsonArray arr = Json5.of("[{'a':'x'},{'a':'y'},{'a':'z','b':1}]");
    JsonList<JsonString> view = arr.projectAsList(e -> e.asObject().getString("a"));
    assertEquals(asList("x", "y", "z"), view.toList(JsonString::string));
  }

  @Test
  void testListValuesMappedView() {
    JsonList<JsonObject> list =
        Json5.of("[{'a':'x','b':1},{'a':'y'},{'a':'z','b':3}]")
            .asList(JsonObject.class);
    assertEquals(
        asList("x", "y", "z"), list.project(e -> e.getString("a")).toList(JsonString::string));
    assertEquals(
        asList(1, null, 3), list.project(e -> e.getNumber("b")).toList(JsonNumber::intValue, null));
    assertEquals(
        asList(1, 3),
        list.project(e -> e.getNumber("b")).toListOfNonNullElements(JsonNumber::intValue));
  }

  @Test
  void testIsNull() {
    JsonObject obj = Json5.of("{'optional': null }");

    assertTrue(obj.getArray("optional").isNull());
  }

  @Test
  void testIsArray() {
    JsonObject obj = Json5.of("{'array': [], 'notAnArray': 42 }");

    assertTrue(JsonMixed.of("[]").isArray());
    assertTrue(obj.getArray("array").isArray());
    assertFalse(obj.getArray("notAnArray").isArray());
    assertFalse(obj.getArray("missing").isArray());
  }

  @Test
  void testIsObject() {
    JsonObject obj = Json5.of("{'object': {}, 'notAnObject': 42 }");

    assertTrue(obj.isObject());
    assertTrue(obj.getArray("object").isObject());
    assertFalse(obj.getNumber("notAnObject").isObject());
    assertFalse(obj.getObject("missing").isObject());
  }

  @Test
  void testBooleanNode() {
    JsonObject obj = Json5.of("{'a': true }");
    assertEquals("true", obj.getBoolean("a").toJson());
  }

  @Test
  void testNumberNode() {
    JsonObject obj = Json5.of("{'a': 42 }");
    assertEquals("42", obj.getNumber("a").toJson());
  }

  @Test
  void testStringNode() {
    JsonObject obj = Json5.of("{'a': 'hello, again' }");
    assertEquals("\"hello, again\"", obj.getString("a").toJson());
  }

  @Test
  void testArrayNode() {
    JsonObject obj = Json5.of("{'a': ['hello, again', 12] }");
    assertEquals("[\"hello, again\", 12]", obj.getArray("a").toJson());
  }

  @Test
  void testObjectNode() {
    JsonObject obj = Json5.of("{'a': ['hello, again', 12] }");
    assertEquals("{\"a\": [\"hello, again\", 12] }", obj.toJson());
  }

  @Test
  void testObjectGet() {
    JsonObject obj = Json5.of("{'x':{'a':[1], 'b':2, 'c':3}}");
    assertEquals(1, obj.getNumber("x{a}[0]").intValue());
    assertEquals(1, obj.getObject("x").getArray("{a}").getNumber(0).intValue());
    assertEquals(1, obj.getObject("x").node().get("{a}").get("[0]").value());
  }

  @Test
  void testObjectGet_DotInMemberName() {
    // language=JSON
    String json =
        """
            {
                "some.thing": 42,
                "foo.bar": { "answer": 42 },
                "a": { "b.c": "d"},
                "x": { "y.z": { "answer": 42 }},
                "obj": {"x": { "y.z": { "answer": 42 }}}
            }""";
    JsonObject doc = JsonMixed.of(json);
    assertEquals("d", doc.getString("a{b.c}").string());
    assertEquals(42, doc.getNumber("{some.thing}").integer());
    assertEquals(42, doc.getNumber("{foo.bar}.answer").integer());
    assertEquals(42, doc.getNumber("x{y.z}.answer").integer());
    assertEquals(42, doc.getNumber("obj.x{y.z}.answer").integer());
  }

  @Test
  void testListContainsAll() {
    JsonList<JsonString> list =
        Json5.of("[{'a':'x'}, {'a':'y'}]")
            .as(JsonArray.class)
            .projectAsList(e -> e.asObject().getString("a"));
    assertTrue(list.containsAll(JsonString::string, Set.of("y", "x")));
  }

  @Test
  void testListContains() {
    JsonList<JsonString> list =
        Json5.of("[{'a':'x'}, {'a':'y'}]")
            .as(JsonArray.class)
            .projectAsList(e -> e.asObject().getString("a"));
    assertTrue(list.contains(JsonString::string, "y"::equals));
    assertFalse(list.contains(JsonString::string, "z"::equals));
  }

  @Test
  void testListContainsUnique() {
    JsonList<JsonString> list =
        Json5.of("[{'a':'x'}, {'a':'y'}, {'a':'y'}]")
            .as(JsonArray.class)
            .projectAsList(e -> e.asObject().getString("a"));
    assertTrue(list.containsUnique(JsonString::string, "x"::equals));
    assertFalse(list.containsUnique(JsonString::string, "y"::equals));
  }

  @Test
  void testListFirst() {
    JsonList<JsonObject> list =
        Json5.of("[{'a':'x'}, {'a':'y','b':1}, {'a':'y','b':2}]")
            .asList(JsonObject.class);
    assertEquals(
        1,
        list.first(e -> Objects.equals("y", e.getString("a").string()))
            .asObject()
            .getNumber("b")
            .intValue());
    assertFalse(list.first(e -> e.has("c")).exists());
  }

  @Test
  void testToString() {
    JsonList<JsonNumber> list = JsonMixed.of("[12,42]").asList(JsonNumber.class);
    assertEquals("[12,42]", list.toString());
    JsonMap<JsonNumber> map = Json5.of("{'a':12,'b':42}").asMap(JsonNumber.class);
    assertEquals("{\"a\":12,\"b\":42}", map.toString());
  }

  @Test
  void testToString_NonExistingPath() {
    JsonList<JsonNumber> list =
        JsonMixed.of("[12,42]").getObject("non-existing").asList(JsonNumber.class);
    assertEquals(
        "ARRAY node at path (root) is not a OBJECT and does not support #get(Text:\".non-existing\"): [12,42]",
        list.toString());
  }

  @Test
  void testToString_MalformedJson() {
    JsonMap<JsonNumber> map = JsonMixed.of("{\"a:12}").asMap(JsonNumber.class);
    assertEquals(
        """
        Unexpected EOI at position 7,
        {"a:12}
               ^ expected \"""",
        map.get("a").toString());
  }

  @Test
  void testNode() {
    JsonMixed arr = JsonMixed.of("[]");
    JsonValue doesNotExist = arr.get(0);
    assertNull(doesNotExist.nodeIfExists()); // force remembering
    // still throws
    assertThrowsExactly(JsonPathException.class, doesNotExist::node);
  }
}
