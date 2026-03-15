package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import org.junit.jupiter.api.Test;

/**
 * Tests the {@link JsonString} specific API.
 *
 * @author Jan Bernitt
 */
class JsonStringTest {

  @Test
  void testStringWithDefault_Undefined() {
    assertEquals("hello", JsonMixed.of("{}").getString("x").string("hello"));
  }

  @Test
  void testStringWithDefault_Null() {
    assertEquals("hello", JsonMixed.of("null").string("hello"));
  }

  @Test
  void testStringWithDefault_NoString() {
    JsonMixed str = JsonMixed.of("1");
    JsonTreeException ex = assertThrowsExactly(JsonTreeException.class, () -> str.string("hello"));
    assertEquals("NUMBER node at path (root) is not a STRING and does not support #textValue(): 1", ex.getMessage());
  }

  @Test
  void testStringWithDefault_Empty() {
    assertEquals("", JsonMixed.of("\"\"").string("hello"));
  }

  @Test
  void testStringWithDefault_NonEmpty() {
    assertEquals("world", JsonMixed.of("\"world\"").string("hello"));
  }

  @Test
  void testParsed_Undefined() {
    assertNull(JsonMixed.of("{}").getString("x").parsed(String::length));
  }

  @Test
  void testParsed_Null() {
    assertNull(JsonMixed.of("null").parsed(String::length));
  }

  @Test
  void testParsed_NoString() {
    JsonMixed str = JsonMixed.of("1");
    JsonTreeException ex =
        assertThrowsExactly(JsonTreeException.class, () -> str.parsed(String::length));
    assertEquals("NUMBER node at path (root) is not a STRING and does not support #textValue(): 1", ex.getMessage());
  }

  @Test
  void testParsed_NonEmpty() {
    assertEquals(5, JsonMixed.of("\"world\"").parsed(String::length));
  }
}
