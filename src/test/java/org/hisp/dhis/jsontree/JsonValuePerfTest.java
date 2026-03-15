package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonValuePerfTest {

  @Test
  void testNodeIsRemembered() {
    List<JsonPath> accessed = new ArrayList<>();
    JsonObject root = JsonMixed.of(JsonNode.of("{\"a\":1, \"b\":2}", accessed::add));
    JsonNumber a = root.getNumber("a");

    assertEquals(List.of(), accessed, "no terminal operation was called yet");
    assertEquals(1, a.intValue());
    assertEquals(List.of(JsonPath.of("a")), accessed);
    assertTrue(a.isNumber());
    assertEquals(List.of(JsonPath.of("a")), accessed, "reusing the node should not require lookup");
    assertTrue(root.get("a").isNumber());
    assertEquals(
        List.of(JsonPath.of("a"), JsonPath.of("a")),
        accessed,
        "but requesting path again requires lookup");
  }
  //TODO test the remembering of the JsonNode once it was
  // accessed by using GetListener

  // TODO test common ops that they only access node() once

  //TODO also test the Index modes this way

}
