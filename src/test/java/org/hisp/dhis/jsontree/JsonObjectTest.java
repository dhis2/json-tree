package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Tests for methods declared as default methods in {@link JsonObject}.
 *
 * @author Jan Bernitt
 */
class JsonObjectTest {

  //TODO test all object methods on nodes of other types => should throw

  @Test
  void testSize() {
    assertEquals(0, JsonMixed.of("{}").size());
    assertEquals(1, JsonMixed.of("{\"a\": 1}").size());
    assertEquals(2, JsonMixed.of("{\"a\": 1, \"b\": 2}").size());
    assertEquals(3, JsonMixed.of("{\"a\": 1, \"b\": 2, \"c\": null}").size());
  }

  @Test
  void testHas_NoObject() {
    JsonMixed value = JsonMixed.of("1");
    assertThrowsExactly(JsonTreeException.class, () -> value.has("x"));
  }

  @Test
  void testNames_Undefined() {
    JsonObject value = JsonMixed.of("{}").getObject("x");
    assertEquals(List.of(), value.names());
  }

  @Test
  void testNames_NoObject() {
    JsonMixed value = JsonMixed.of("1");
    assertThrowsExactly(JsonTreeException.class, value::names);
  }

  @Test
  void testNames_Empty() {
    JsonMixed value = JsonMixed.of("{}");
    assertEquals(List.of(), value.names());
  }

  @Test
  void testNames_NonEmpty() {
    // language=json
    String json =
        """
            {"a":1,"b":2}""";
    JsonMixed value = JsonMixed.of(json);
    assertEquals(List.of("a", "b"), value.names());
  }

  @Test
  void testNames_Special() {
    // language=json
    String json =
        """
            {".":1,"{uid}":2,"[0]": 3, "": 4}""";
    JsonMixed value = JsonMixed.of(json);
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
  void testProject() {
    // language=json
    String json =
        """
            { "a": [1], "b": [2] }""";
    JsonMixed value = JsonMixed.of(json);
    JsonObject obj = value.project(e -> e.as(JsonArray.class).get(0));
    assertSame(JsonObject.class, obj.asType());
    assertEquals(List.of("a", "b"), obj.names());
    assertEquals(List.of(1, 2), List.of(obj.get("a").node().value(), obj.get("b").node().value()));
    assertEquals(1, obj.getNumber("a").intValue());
    assertTrue(obj.has("a"));
    assertTrue(obj.has("a", "b"));
    assertFalse(obj.has("a", "b", "c"));
  }
}
