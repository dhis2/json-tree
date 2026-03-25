package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import static org.hisp.dhis.jsontree.JsonSelector.$;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Some test for the {@link JsonSelector} POC.
 */
class JsonSelectorTest {

  @Test
  void testToString() {
    assertEquals(
        "$.test[*][1,2][3:][?(<condition>)]",
        $
            .key("test")
            .any()
            .indexes(1, 2)
            .slice(3)
            .filter(JsonNode::isNull)
            .toString());
  }

  @Test
  void testOf_Expression() {
    assertEquals("$[*].foo.bar.baz", JsonSelector.of("$[*].foo.bar['baz']").toString());
  }

  @Test
  void testDescendant() {
    assertEquals("$..", $.descendant().descendant().toString());
    assertEquals("$...x..", $.descendant().key("x").descendant().toString());
  }
}
