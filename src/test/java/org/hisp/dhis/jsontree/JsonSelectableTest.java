package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hisp.dhis.jsontree.JsonSelector.$;
import static org.hisp.dhis.jsontree.JsonSelector.AT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonSelectableTest {

  @Test
  void testFilter() {
    JsonObject json = JsonMixed.of("""
      {
        "books": [
          { "title": "A", "price": 8 },
          { "title": "B", "price": 12 }
        ]
      }
      """);
    List<JsonNode> matches =
        json.node()
            .query(
                $.key("books")
                    .filter(book -> book.get("price").intValue() < 10)
                    .key("title"))
            .toList();
    assertEquals(1, matches.size());
    assertEquals("A", matches.get(0).textValue().toString());

    // in another way: using JsonValue API and a filter with sub-selector
    List<JsonMixed> matches2 =
        json
            .query(
                $.key("books")
                    .filter(AT.key("price"), price -> price.intValue() < 10)
                    .key("title"))
            .toList();
    assertEquals(1, matches.size());
    assertEquals("A", matches2.get(0).string());
  }
}
