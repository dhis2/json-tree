package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hisp.dhis.jsontree.JsonNodeType.NUMBER;
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
            .query($.key("books").any().filter(book -> book.get("price").intValue() < 10).key("title"))
            .toList();
    assertEquals(1, matches.size());
    assertEquals("A", matches.get(0).textValue().toString());

    // in another way: using JsonValue API and a filter with sub-selector
    List<JsonMixed> matches2 =
        json.query(
                $.key("books")
                    .any()
                    .filter(AT.key("price").filter(price -> price.intValue() < 10))
                    .key("title"))
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

    List<JsonMixed> matches = json.query($.descendants().filter(JsonProbe::isSimple)).toList();
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
    assertEquals(11, json.queryCount($.descendants()), "$.descendant() should visit all nodes");
    assertEquals(
        6,
        json.queryCount($.descendants(), 6),
        "$.descendant() should visit only nodes up to the limit");
    assertEquals(
        16, json.queryReduce($.descendants().type(NUMBER), JsonMixed::intValue, 0, Integer::max));
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
                    .descendants()
                    .key("street"))
            .toList()
            .toString());
  }

  record IsEmptyMatcher() implements JsonSelector.Matcher {

    @Override
    public void match(JsonNode node, JsonSelector next, JsonSelector.Matches<JsonNode> matches) {
        if (!node.isSimple() && node.isEmpty()) next.match(node, matches);
    }

    @Override
    public String toString() {
      return "[]";
    }
  }

  @Test
  void testCustomMatcher() {
    JsonObject json =
        JsonMixed.of(
            """
      {
        "books": [
          { "title": "A", "price": 8 },
          { "title": "C", "price": 10, "releases": [] },
          { "title": "B", "price": 12 }
        ]
      }
      """);

    List<JsonPath> matches =
        json.node().query($.descendants().select(new IsEmptyMatcher())).map(JsonNode::path).toList();
    assertEquals("[.books.1.releases]", matches.toString());
  }

  @Test
  void testQueryTop() {
    JsonObject json =
        JsonMixed.of(
            """
      {
        "books": [
          { "title": "A", "price": 8 },
          { "title": "B", "price": 12 },
          { "title": "C", "price": 6 },
          { "title": "E", "price": 11 },
          { "title": "F", "price": 5 },
          { "title": "G", "price": 9 },
          { "title": "D", "price": 16 },
          { "title": "H", "price": 4 }
        ]
      }
      """);
    assertEquals(
        "[16, 12, 11]",
        json.queryTop(3, $.descendants().key("price"), JsonMixed::intValue)
            .toList().toString());
  }
}
