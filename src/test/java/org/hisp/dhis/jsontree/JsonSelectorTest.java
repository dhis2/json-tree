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
        "$.test[*][1,2][3:]?(<condition>)",
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
    assertEquals("@[*].foo.bar.baz", JsonSelector.of("@[*].foo.bar['baz']").toString());
    assertEquals("$..", JsonSelector.of("$..").toString());
    assertEquals("$[1]", JsonSelector.of("$[1]").toString());
    assertEquals("$[1,2,3]", JsonSelector.of("$[1,2,3]").toString());
    assertEquals("$[1:2]", JsonSelector.of("$[1:2]").toString());
    assertEquals("$[1:2:3]", JsonSelector.of("$[1:2:3]").toString());
    assertEquals("$[-1:]", JsonSelector.of("$[-1::]").toString());
    assertEquals("$[1:]", JsonSelector.of("$[1:]").toString());
  }

  @Test
  void testDescendant() {
    assertEquals("$..", $.descendants().descendants().toString());
    assertEquals("$...x..", $.descendants().key("x").descendants().toString());
  }
}
