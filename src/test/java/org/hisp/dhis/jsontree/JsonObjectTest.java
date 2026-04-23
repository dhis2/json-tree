package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.hisp.dhis.jsontree.JsonNode.Index;
import org.junit.jupiter.api.Test;

/**
 * Tests for methods declared as default methods in {@link JsonObject}.
 *
 * @author Jan Bernitt
 */
class JsonObjectTest {

  @Test
  void testSize() {
    assertEquals(0, Json5.of("{}").size());
    assertEquals(1, Json5.of("{'a': 1}").size());
    assertEquals(2, Json5.of("{'a': 1, 'b': 2}").size());
    assertEquals(3, Json5.of("{'a': 1, 'b': 2, 'c': null}").size());
    // duplicate keys count for size
    assertEquals(3, Json5.of("{'a': 1, 'b': 2, 'a': 2}").size());
  }

  @Test
  void testMembers_Duplicates() {
    JsonMixed duplicates = Json5.of("{'a': 1, 'b': 2, 'a': 3}");
    // with CHECK nodes do not get indexes and so duplicates are observed
    assertEquals(
        List.of(1, 2, 3),
        duplicates.entries(Index.CHECK).map(JsonMixed::intValue).toList());
    assertEquals(
        List.of(1, 2, 1),
        duplicates.entries(Index.ADD).map(JsonMixed::intValue).toList());
    // now that they are indexes even CHECK de-duplicates
    assertEquals(
        List.of(1, 2, 1),
        duplicates.entries(Index.CHECK).map(JsonMixed::intValue).toList());
    // but if we skip duplicates are observed again
    assertEquals(
        List.of(1, 2, 3),
        duplicates.entries(Index.SKIP).map(JsonMixed::intValue).toList());
  }

  @Test
  void testHas_NoObject() {
    assertThrowsExactly(JsonTreeException.class, () -> Json5.of("1").has("x"));
    assertThrowsExactly(JsonTreeException.class, () -> Json5.of("true").has("x"));
    assertThrowsExactly(JsonTreeException.class, () -> Json5.of("'a'").has("x"));
    assertThrowsExactly(JsonTreeException.class, () -> Json5.of("'[1]'").has("0"));
  }

  @Test
  void testNames_Undefined() {
    assertEquals(List.of(), JsonMixed.of("{}").getObject("x").names());
  }

  @Test
  void testNames_NoObject() {
    assertThrowsExactly(JsonTreeException.class, () -> Json5.of("1").names());
    assertThrowsExactly(JsonTreeException.class, () -> Json5.of("true").names());
    assertThrowsExactly(JsonTreeException.class, () -> Json5.of("'a'").names());
    assertThrowsExactly(JsonTreeException.class, () -> Json5.of("[1]").names());
  }

  @Test
  void testNames_Empty() {
    assertEquals(List.of(), JsonMixed.of("{}").names());
    assertEquals(List.of("a", "b"), Json5.of("{'a':1,'b':2}").names());
  }


  @Test
  void testNames_Duplicates() {
    assertEquals(List.of(), JsonMixed.of("{}").names());
    assertEquals(List.of("a", "b", "a"), Json5.of("{'a':1,'b':2, 'a': 3}").names());
  }

  @Test
  void testNames_Special() {
    JsonMixed value = Json5.of("{'.':1,'{uid}':2,'[0]': 3, '': 4}");
    assertEquals(List.of(".", "{uid}", "[0]", ""), value.names());
    assertEquals(4, value.getNumber(Text.of("")).intValue());
  }

  @Test
  void testPaths_Special() {
    // language=json
    String json =
        """
            {"root": {".":1,"{uid}":2,"[0]": 3,"normal":4}}""";
    JsonObject value = JsonMixed.of(json).getObject("root");
    assertEquals(
        List.of(
            JsonPath.of(".root{.}"),
            JsonPath.of(".root.{uid}"),
            JsonPath.of(".root.[0]"),
            JsonPath.of(".root.normal")),
        value.paths().toList());
  }

  @Test
  void testPaths_OpenAPI() {
    // language=json
    String json =
        """
            {"paths": {"/api/dataElements/{uid:[a-zA-Z0-9]{11}}": {"get": {"id": "opx"}, "delete": {"id":"opy"}}}}""";
    JsonObject paths = JsonMixed.of(json).getObject("paths");
    assertEquals(List.of("/api/dataElements/{uid:[a-zA-Z0-9]{11}}"), paths.names());
    JsonObject ops = paths.getObject(Text.of("/api/dataElements/{uid:[a-zA-Z0-9]{11}}"));
    assertEquals(List.of("get", "delete"), ops.keys().map(Text::toString).toList());
    assertEquals("opy", ops.getObject("delete").getString("id").string());
  }

  @Test
  void testPaths_NoObject() {
    assertThrowsExactly(JsonTreeException.class, () -> Json5.of("1").paths());
    assertThrowsExactly(JsonTreeException.class, () -> Json5.of("true").paths());
    assertThrowsExactly(JsonTreeException.class, () -> Json5.of("'a'").paths());
    assertThrowsExactly(JsonTreeException.class, () -> Json5.of("[1]").paths());
  }

  @Test
  void testEntries_NoObject() {
    assertThrowsExactly(JsonTreeException.class, () -> Json5.of("1").entries());
    assertThrowsExactly(JsonTreeException.class, () -> Json5.of("true").entries());
    assertThrowsExactly(JsonTreeException.class, () -> Json5.of("'a'").entries());
    assertThrowsExactly(JsonTreeException.class, () -> Json5.of("[1]").entries());
  }

  @Test
  void testProject() {
    // language=json
    String json =
        """
            { "a": [1], "b": [2] }""";
    JsonMixed value = JsonMixed.of(json);
    JsonObject obj = value.project(e -> e.as(JsonArray.class).get(0));
    assertSame(JsonObject.class, obj.asType());
    assertEquals(List.of("a", "b"), obj.names());
    assertEquals(List.of(1, 2), List.of(obj.get("a").node().intValue(), obj.get("b").node().intValue()));
    assertEquals(1, obj.getNumber("a").intValue());
    assertTrue(obj.has("a"));
    assertTrue(obj.has("a", "b"));
    assertFalse(obj.has("a", "b", "c"));
  }
}
