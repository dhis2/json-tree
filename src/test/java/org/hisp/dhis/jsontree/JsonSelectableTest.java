package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hisp.dhis.jsontree.JsonSelector.$;
import static org.hisp.dhis.jsontree.JsonSelector.AT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonSelectableTest {

  @Test
  void testFilter() {
    JsonObject json =
        JsonMixed.of(
            """
      {
        "books": [
          { "title": "A", "price": 8 },
          { "title": "B", "price": 12 }
        ]
      }
      """);
    List<JsonNode> matches =
        json.node()
            .query($.key("books").filter(book -> book.get("price").intValue() < 10).key("title"))
            .toList();
    assertEquals(1, matches.size());
    assertEquals("A", matches.get(0).textValue().toString());

    // in another way: using JsonValue API and a filter with sub-selector
    List<JsonMixed> matches2 =
        json.query(
                $.key("books").filter(AT.key("price"), price -> price.intValue() < 10).key("title"))
            .toList();
    assertEquals(1, matches.size());
    assertEquals("A", matches2.get(0).string());
  }

  @Test
  void testDescendant() {
    JsonObject json =
        JsonMixed.of(
            """
      {
        "books": [
          { "title": "A", "price": 8 },
          { "title": "C", "price": 10, "x": true },
          { "title": "B", "price": 12 }
        ]
      }
      """);

    List<JsonMixed> matches = json.query($.descendant().filter(node -> node.getType().isSimple())).toList();
    assertEquals("[\"A\", 8, \"C\", 10, true, \"B\", 12]", matches.toString());
  }

  @Test
  void testDescendant_Visit() {
    JsonObject json =
        JsonMixed.of(
            """
      {
        "books": [
          { "title": "A", "price": 8 },
          { "title": "B", "price": 12 },
          { "title": "C", "price": 16 }
        ]
      }
      """);
    assertEquals(11, json.queryCount($.descendant()), "$.descendant() should visit all nodes");
  }

  @Test
  void testDescendant_MultiLevel() {
    JsonObject json =
        JsonMixed.of(
            """
      {
        "books": [
          { "title": "A", "price": 8 },
          { "title": "B", "price": 12, "author": { "address": {"street": "Main"}} },
          { "title": "C", "price": 16 }
        ]
      }
      """);
    assertEquals(
        "[\"Main\"]",
        json.query(
                $.find(node -> node.isObject() && node.has("title"))
                    .key("author")
                    .descendant()
                    .key("street"))
            .toList()
            .toString());
  }
}
