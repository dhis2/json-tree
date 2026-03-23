package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * This test is not testing any performance metrics, but it verifies that the mechanisms that get us
 * better performance work as expected.
 *
 * <p>This is done by checking that certain access patterns do or do not result in a lookup under
 * the hood. This makes use of the fact that object member lookup by name can be observed using a
 * {@link JsonNode.GetListener}.
 */
class JsonApiPerfTest {

  /**
   * This test verifies that once a terminal operation is done on a virtual node ({@link JsonValue} API)
   * it internally remembers the actual node ({@link JsonNode} API) so that subsequent operations do
   * not cause another lookup.
   */
  @Test
  void testJsonValue_JsonNodeIsRemembered() {
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
  void testJsonValue_JsonNodeIsRemembered_NonExisting() {
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
  void testJsonValue_intValues() {
    List<JsonPath> accessed = new ArrayList<>();
    JsonObject root = JsonMixed.of(JsonNode.of("{\"values\":[1,2,3,4,5]", accessed::add));

    assertEquals(List.of(), accessed);
    assertEquals(15, root.getArray("values").intValues().sum());
    assertEquals(List.of(JsonPath.of("values")), accessed, "there should be one access of values");
  }

  @Test
  void testJsonValue_longValues() {
    List<JsonPath> accessed = new ArrayList<>();
    JsonObject root = JsonMixed.of(JsonNode.of("{\"values\":[1,2,3,4,5]", accessed::add));

    assertEquals(List.of(), accessed);
    assertEquals(15L, root.getArray("values").longValues().sum());
    assertEquals(List.of(JsonPath.of("values")), accessed, "there should be one access of values");
  }

  @Test
  void testJsonValue_doubleValues() {
    List<JsonPath> accessed = new ArrayList<>();
    JsonObject root = JsonMixed.of(JsonNode.of("{\"values\":[1,2,3,4,5]", accessed::add));

    assertEquals(List.of(), accessed);
    assertEquals(15d, root.getArray("values").doubleValues().sum());
    assertEquals(List.of(JsonPath.of("values")), accessed, "there should be one access of values");
  }

  @Test
  void testJsonValue_streamValues() {
    List<JsonPath> accessed = new ArrayList<>();
    JsonObject root = JsonMixed.of(JsonNode.of("{\"values\":[1,2,3,4,5]", accessed::add));

    assertEquals(List.of(), accessed);
    assertEquals(5L, root.getArray("values").streamValues(double.class).count());
    assertEquals(List.of(JsonPath.of("values")), accessed, "there should be one access of values");
  }

  @Test
  void testJsonNode_elements() {
    JsonNode root = JsonNode.of("[ 1,2 , true , false, \"hello\",{},[]]");

    Iterator<JsonNode> elements = root.elements(JsonNode.Index.SKIP).iterator();
    JsonNode e0 = elements.next();
    JsonNode e1 = elements.next();
    JsonNode e2 = elements.next();
    JsonNode e3 = elements.next();
    JsonNode e4 = elements.next();
    JsonNode e5 = elements.next();
    JsonNode e6 = elements.next();
    assertFalse(elements.hasNext());
    assertThrows(NoSuchElementException.class, elements::next);
    assertEquals(1, e0.intValue());
    assertEquals(2, e1.intValue());
    assertEquals(true, e2.value());
    assertEquals(false, e3.value());
    assertEquals("hello", e4.value().toString());
    assertEquals("{}", e5.getDeclaration().toString());
    assertEquals("[]", e6.getDeclaration().toString());

    JsonNode e0kept = root.elements(JsonNode.Index.ADD).iterator().next();
    assertNotSame(e0, e0kept);
    assertSame(e0kept, root.elements(JsonNode.Index.CHECK).iterator().next());
    assertNotSame(e0kept, root.elements(JsonNode.Index.SKIP).iterator().next());
  }

  @Test
  void testJsonNode_members() {
    JsonNode doc = JsonNode.of("{\"a\": 1,\"b\":2 ,\"c\": true ,\"d\":false}");

    JsonNode root = doc.get("$");

    Iterator<JsonNode> members = root.members(JsonNode.Index.SKIP).iterator();
    JsonNode m1 = members.next();
    JsonNode m2 = members.next();
    JsonNode m3 = members.next();
    JsonNode m4 = members.next();
    assertFalse(members.hasNext());
    assertThrows(NoSuchElementException.class, members::next);
    assertEquals("a", m1.getKey().toString());
    assertEquals(1, m1.intValue());
    assertEquals("b", m2.getKey().toString());
    assertEquals(2, m2.intValue());
    assertEquals("c", m3.getKey().toString());
    assertEquals(true, m3.value());
    assertEquals("d", m4.getKey().toString());
    assertEquals(false, m4.value());

    JsonNode m1kept = root.members(JsonNode.Index.ADD).iterator().next();
    assertNotSame(m1, m1kept);
    assertSame(m1kept, root.members(JsonNode.Index.CHECK).iterator().next());
    assertNotSame(m1kept, root.members(JsonNode.Index.SKIP).iterator().next());
  }
}
