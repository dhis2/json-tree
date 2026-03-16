package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test is not testing any performance metrics, but it verifies that the mechanisms that get us
 * better performance work as expected.
 *
 * <p>This is done by checking that certain access patterns do or do not result in a lookup under
 * the hood. This makes use of the fact that object member lookup by name can be observed using a
 * {@link JsonNode.GetListener}.
 */
class JsonValuePerfTest {

  /**
   * This test verifies that once a terminal operation is done on a virtual node ({@link JsonValue} API)
   * it internally remembers the actual node ({@link JsonNode} API) so that subsequent operations do
   * not cause another lookup.
   */
  @Test
  void testNodeIsRemembered() {
    List<JsonPath> accessed = new ArrayList<>();
    JsonObject root = JsonMixed.of(JsonNode.of("{\"a\":1, \"b\":2}", accessed::add));
    JsonNumber a = root.getNumber("a");

    assertEquals(List.of(), accessed, "there should not be access yet as no terminal op was called");
    assertEquals(1, a.intValue()); // terminal op
    assertEquals(List.of(JsonPath.of("a")), accessed, "there should be one access of a");
    assertTrue(a.isNumber()); // another terminal op
    assertEquals(List.of(JsonPath.of("a")), accessed, "reusing the node should not require lookup");
    // in contrast, when walking to the same virtual node again from root
    // this is now another instance and so terminal op is another lookup
    assertTrue(root.get("a").isNumber());
    assertEquals(
        List.of(JsonPath.of("a"), JsonPath.of("a")),
        accessed,
        "but requesting path again requires lookup");
  }

  @Test
  void testNodeIsRemembered_NonExisting() {
    List<JsonPath> accessed = new ArrayList<>();
    JsonObject root = JsonMixed.of(JsonNode.of("{\"a\":1, \"b\":2}", accessed::add));
    // x does not exist... still we want to remember that
    JsonNumber x = root.getNumber("x");

    assertEquals(List.of(), accessed, "there should not be access yet as no terminal op was called");
    assertFalse(x.isInteger()); // terminal op
    assertEquals(List.of(JsonPath.of("x")), accessed, "there should be one access of x");
    assertFalse(x.isNumber()); // another terminal op
    assertEquals(List.of(JsonPath.of("x")), accessed, "reusing the node should not require lookup");
    // in contrast, when walking to the same virtual node again from root
    // this is now another instance and so terminal op is another lookup
    assertFalse(root.get("x").isNumber());
    assertEquals(
        List.of(JsonPath.of("x"), JsonPath.of("x")),
        accessed,
        "but requesting path again requires lookup");
  }

  @Test
  void testNodeMembers_OnlyRootLookup() {
    List<JsonPath> accessed = new ArrayList<>();
    JsonObject root = JsonMixed.of(JsonNode.of("{\"values\":[1,2,3,4,5]", accessed::add));

    assertEquals(List.of(), accessed);
    assertEquals(15, root.getArray("values").intValues().sum());
    assertEquals(List.of(JsonPath.of("values")), accessed, "there should be one access of values");
  }

  // TODO test common ops that they only access node() once

  //TODO also test the Index modes this way

}
