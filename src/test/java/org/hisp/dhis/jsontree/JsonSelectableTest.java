package org.hisp.dhis.jsontree;

import static java.util.stream.Collectors.toList;
import static org.hisp.dhis.jsontree.JsonNodeType.NUMBER;
import static org.hisp.dhis.jsontree.JsonSelector.$;
import static org.hisp.dhis.jsontree.JsonSelector.AT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link JsonSelectable} API.
 */
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

  // ---------------------------------------------------------------------------
  // key matching
  // ---------------------------------------------------------------------------

  @Test
  void testKey_Single() {
    JsonObject json = JsonMixed.of("""
        {"a": 1, "b": 2}""");
    assertEquals("[1]", json.query($.key("a")).toList().toString());
    assertEquals("[2]", json.query($.key("b")).toList().toString());
  }

  @Test
  void testKey_Chained() {
    JsonObject json = JsonMixed.of("""
        {"a": {"b": {"c": 42}}}""");
    assertEquals("[42]", json.query($.key("a").key("b").key("c")).toList().toString());
  }

  @Test
  void testKey_NoMatch() {
    JsonObject json = JsonMixed.of("""
        {"a": 1}""");
    assertEquals(0, json.queryCount($.key("missing")));
    assertEquals(0, json.queryCount($.key("a").key("b")));
  }

  @Test
  void testKey_OnNonObject() {
    JsonMixed json = JsonMixed.of("[1,2,3]");
    assertEquals(0, json.queryCount($.key("a")));
  }

  // ---------------------------------------------------------------------------
  // index matching
  // ---------------------------------------------------------------------------

  @Test
  void testIndex_Positive() {
    JsonMixed json = JsonMixed.of("[10, 20, 30]");
    assertEquals("[10]", json.query($.index(0)).toList().toString());
    assertEquals("[20]", json.query($.index(1)).toList().toString());
    assertEquals("[30]", json.query($.index(2)).toList().toString());
  }

  @Test
  void testIndex_Negative() {
    JsonMixed json = JsonMixed.of("[10, 20, 30]");
    assertEquals("[30]", json.query($.index(-1)).toList().toString());
    assertEquals("[20]", json.query($.index(-2)).toList().toString());
    assertEquals("[10]", json.query($.index(-3)).toList().toString());
  }

  @Test
  void testIndex_OutOfRange() {
    JsonMixed json = JsonMixed.of("[10, 20, 30]");
    assertEquals(0, json.queryCount($.index(3)));
    assertEquals(0, json.queryCount($.index(-4)));
    assertEquals(0, json.queryCount($.index(100)));
  }

  @Test
  void testIndex_OnNonArray() {
    JsonObject json = JsonMixed.of("""
        {"a": 1}""");
    assertEquals(0, json.queryCount($.index(0)));
  }

  // ---------------------------------------------------------------------------
  // union matching
  // ---------------------------------------------------------------------------

  @Test
  void testUnion_Positive() {
    JsonMixed json = JsonMixed.of("[10, 20, 30, 40]");
    // union visits in the order given, not in document order
    assertEquals("[40, 10]", json.query($.indexes(3, 0)).toList().toString());
    assertEquals("[10, 20, 30]", json.query($.indexes(0, 1, 2)).toList().toString());
  }

  // ---------------------------------------------------------------------------
  // slice matching
  // ---------------------------------------------------------------------------

  @Test
  void testSlice_StartEnd() {
    JsonMixed json = JsonMixed.of("[0, 1, 2, 3, 4, 5]");
    assertEquals("[1, 2, 3]", json.query($.slice(1, 4)).toList().toString());
  }

  @Test
  void testSlice_StartOnly() {
    JsonMixed json = JsonMixed.of("[0, 1, 2, 3, 4]");
    assertEquals("[3, 4]", json.query($.slice(3)).toList().toString());
  }

  @Test
  void testSlice_Step() {
    JsonMixed json = JsonMixed.of("[0, 1, 2, 3, 4, 5]");
    assertEquals("[0, 2, 4]", json.query($.slice(0, 6, 2)).toList().toString());
  }

  @Test
  void testSlice_NegativeStep() {
    JsonMixed json = JsonMixed.of("[0, 1, 2, 3, 4]");
    // -6 normalises to -1 → "past the start", i.e. full reverse
    assertEquals("[4, 3, 2, 1, 0]", json.query($.slice(4, -6, -1)).toList().toString());
    // -1 normalises to 4 → start 4, end 4 exclusive → empty
    assertEquals("[]", json.query($.slice(4, -1, -1)).toList().toString());
    // partial reverse: from 3 down to (not including) index 1
    assertEquals("[3, 2]", json.query($.slice(3, 1, -1)).toList().toString());
  }

  // ---------------------------------------------------------------------------
  // any matching
  // ---------------------------------------------------------------------------

  @Test
  void testAny_Array() {
    JsonMixed json = JsonMixed.of("[10, 20, 30]");
    assertEquals("[10, 20, 30]", json.query($.any()).toList().toString());
  }

  @Test
  void testAny_Object() {
    JsonObject json = JsonMixed.of("""
        {"a": 1, "b": 2, "c": 3}""");
    assertEquals("[1, 2, 3]", json.query($.any()).toList().toString());
  }

  @Test
  void testAny_OnScalar() {
    JsonMixed json = JsonMixed.of("42");
    assertEquals(0, json.queryCount($.any()));
  }

  // ---------------------------------------------------------------------------
  // type matching
  // ---------------------------------------------------------------------------

  @Test
  void testType_OnMixedArray() {
    JsonMixed json = JsonMixed.of("[1, \"a\", true, null, 2, [3], {\"k\":4}]");
    assertEquals("[1, 2]", json.query($.any().type(JsonNodeType.NUMBER)).toList().toString());
    assertEquals("[\"a\"]", json.query($.any().type(JsonNodeType.STRING)).toList().toString());
    assertEquals("[true]", json.query($.any().type(JsonNodeType.BOOLEAN)).toList().toString());
    assertEquals("[null]", json.query($.any().type(JsonNodeType.NULL)).toList().toString());
  }

  // ---------------------------------------------------------------------------
  // descendant ordering (result paths, not values — asserts visit order)
  // ---------------------------------------------------------------------------

  @Test
  void testDescendants_VisitOrder() {
    JsonObject json = JsonMixed.of("""
        {"a": 1, "b": [2, 3], "c": {"d": 4}}""");
    // depth-first, document order, includes the node itself as ""
    List<JsonPath> paths =
        json.node().query($.descendants()).map(JsonNode::path).toList();
    assertEquals("[, .a, .b, .b.0, .b.1, .c, .c.d]", paths.toString());
  }

  // ---------------------------------------------------------------------------
  // root / relative anchors
  // ---------------------------------------------------------------------------

  @Test
  void testRelative_AT_OnSelf() {
    JsonObject json = JsonMixed.of("""
        {"a": 1}""");
    // AT matches the node query() is called on
    assertEquals(1, json.queryCount(AT));
  }

  @Test
  void testRoot_JumpsToDocumentRoot() {
    JsonObject json = JsonMixed.of("""
        {"a": {"b": 1}}""");
    JsonMixed aNode = json.get("a");
    // even queried from a sub-node, $ resolves against the document root
    assertEquals("[1]", aNode.query($.key("a").key("b")).toList().toString());
  }

  // ---------------------------------------------------------------------------
  // entry-point edge cases
  // ---------------------------------------------------------------------------

  @Test
  void testQueryExists() {
    JsonObject json = JsonMixed.of("""
        {"a": 1, "b": [10, 20]}""");
    assertTrue(json.queryExists($.key("a")));
    assertTrue(json.queryExists($.key("b").any()));
    assertFalse(json.queryExists($.key("c")));
  }

  @Test
  void testQueryFirst() {
    JsonObject json = JsonMixed.of("""
        {"a": 1, "b": 2}""");
    assertEquals(
        java.util.Optional.of(1),
        json.queryFirst($.key("a")).map(JsonMixed::intValue));
    assertEquals(
        java.util.Optional.empty(),
        json.queryFirst($.key("c")));
  }

  @Test
  void testQueryReduce_Empty() {
    JsonObject json = JsonMixed.of("""
        {"a": 1}""");
    // no matches → init is returned unchanged
    assertEquals(
        99,
        json.queryReduce($.key("missing"), JsonMixed::intValue, 99, Integer::sum));
  }

  @Test
  void testQueryTop_FewerMatchesThanLimit() {
    JsonObject json = JsonMixed.of("""
        {"a": 1, "b": 2}""");
    assertEquals(
        "[2, 1]",
        json.queryTop(10, $.any(), JsonMixed::intValue).toList().toString());
  }

  // ============================================================================
  // flatten: chain and static factory
  // ============================================================================

  @Test
  void testFlatten_ArrayOfArrays() {
    JsonMixed json = JsonMixed.of( "[[1,2],[3,4]]" );
    List<String> flat = json.query( JsonSelector.$.flatten() )
        .map( n -> n.toString() ).toList();
    assertEquals( List.of( "1", "2", "3", "4" ), flat );
  }

  @Test
  void testFlatten_AlreadyFlat() {
    JsonMixed json = JsonMixed.of( "[1,2,3]" );
    List<String> flat = json.query( JsonSelector.$.flatten() )
        .map( n -> n.toString() ).toList();
    assertEquals( List.of( "1", "2", "3" ), flat );
  }

  @Test
  void testFlatten_MixedArray() {
    // scalars pass through, nested arrays expand one level
    JsonMixed json = JsonMixed.of( "[1,[2,3],4]" );
    List<String> flat = json.query( JsonSelector.$.flatten() )
        .map( n -> n.toString() ).toList();
    assertEquals( List.of( "1", "2", "3", "4" ), flat );
  }

  @Test
  void testFlatten_NonArray_PassesThrough() {
    // flatten on a non-array is identity
    JsonMixed json = JsonMixed.of( "5" );
    List<String> flat = json.query( JsonSelector.$.flatten() )
        .map( n -> n.toString() ).toList();
    assertEquals( List.of( "5" ), flat );
  }

  @Test
  void testOfFlatten_EqualsChain() {
    JsonMixed json = JsonMixed.of( "[[1,2],[3,4]]" );
    assertEquals(
        json.query( JsonSelector.$.flatten() ).map( n -> n.toString() ).toList(),
        json.query( JsonSelector.ofFlatten() ).map( n -> n.toString() ).toList() );
  }

  @Test
  void testFlatten_ComposedWithKey() {
    // equivalent of JMESPath reservations[].instances[].state
    JsonMixed json = JsonMixed.of( """
        {
          "reservations": [
            {"instances": [{"state":"running"},{"state":"stopped"}]},
            {"instances": [{"state":"terminated"}]}
          ]
        }""" );

    List<String> states = json.query(
            JsonSelector.$.key( "reservations" )
                .flatten()
                .key( "instances" )
                .flatten()
                .key( "state" ) )
        .map( n -> n.as( JsonString.class ).string() )
        .toList();

    assertEquals( List.of( "running", "stopped", "terminated" ), states );
  }

  // ============================================================================
  // queryMap: single level, nested, constructors, materialised form
  // ============================================================================

  @Test
  void testQueryMap_SingleLevel() {
    JsonMixed json = JsonMixed.of( """
        {
          "people": [
            {"first": "James", "last": "Smith"},
            {"first": "Jacob", "last": "Johnson"},
            {"first": "Jayden", "last": "Williams"}
          ]
        }""" );

    List<String> firsts = json.queryMap(
        JsonSelector.of( "$.people[*]" ),
        p -> p.getString( "first" ).string() );

    assertEquals( List.of( "James", "Jacob", "Jayden" ), firsts );
  }

  @Test
  void testQueryMap_Nested() {
    // equivalent of JMESPath reservations[*].instances[*].state
    // nesting in JMESPath is expressed here as nested queryMap calls
    JsonMixed json = JsonMixed.of( """
        {
          "reservations": [
            {"instances": [{"state":"running"},{"state":"stopped"}]},
            {"instances": [{"state":"terminated"},{"state":"running"}]}
          ]
        }""" );

    List<List<String>> states = json.queryMap(
        JsonSelector.of( "$.reservations[*]" ),
        res -> res.queryMap(
            JsonSelector.of( "@.instances[*]" ),
            inst -> inst.getString( "state" ).string() ) );

    assertEquals(
        List.of(
            List.of( "running", "stopped" ),
            List.of( "terminated", "running" ) ),
        states );
  }

  @Test
  void testQueryMap_Constructor_Object() {
    // equivalent of JMESPath multiselect hash: people[*].{name: first, age: age}
    JsonMixed json = JsonMixed.of( """
        {
          "people": [
            {"first": "James", "age": 30},
            {"first": "Jacob", "age": 25}
          ]
        }""" );

    List<JsonObject> slimmed = json.queryMap(
        JsonSelector.of( "$.people[*]" ),
        p -> Json.object( o -> o
            .addString( "name", p.getString( "first" ).string() )
            .addNumber( "age", p.getNumber( "age" ).integer() ) ) );

    assertEquals( 2, slimmed.size() );
    assertEquals( "{\"name\":\"James\",\"age\":30}", slimmed.get( 0 ).toJson() );
    assertEquals( "{\"name\":\"Jacob\",\"age\":25}", slimmed.get( 1 ).toJson() );
  }

  @Test
  void testQueryMap_Constructor_Array() {
    // equivalent of JMESPath multiselect list: people[*].[first, age]
    JsonMixed json = JsonMixed.of( """
        {
          "people": [
            {"first": "James", "age": 30},
            {"first": "Jacob", "age": 25}
          ]
        }""" );

    List<JsonArray> pairs = json.queryMap(
        JsonSelector.of( "$.people[*]" ),
        p -> Json.array( a -> a
            .addString( p.getString( "first" ).string() )
            .addNumber( p.getNumber( "age" ).integer() ) ) );

    assertEquals( "[\"James\",30]", pairs.get( 0 ).toJson() );
    assertEquals( "[\"Jacob\",25]", pairs.get( 1 ).toJson() );
  }

  @Test
  void testQueryMap_MaterialisedForm_JsonArray() {
    // Demonstrates the "list projection" materialised as a JSON document
    // rather than a Java list — the queryMap results are re-serialised.
    JsonMixed json = JsonMixed.of( """
        {"people": [{"name": "James"}, {"name": "Jacob"}]}""" );

    List<String> names = json.queryMap(
        JsonSelector.of( "$.people[*]" ),
        p -> p.getString( "name" ).string() );

    JsonArray materialised = Json.array( arr -> names.forEach( arr::addString ) );

    assertEquals( "[\"James\",\"Jacob\"]", materialised.toJson() );
  }

  @Test
  void testQueryMap_MaterialisedForm_JsonObject() {
    // Same pattern but assembling a JSON document keyed by one of the
    // projected values — closer to a "build a lookup" use case.
    JsonMixed json = JsonMixed.of( """
        {"people": [
            {"id": "u1", "name": "James"},
            {"id": "u2", "name": "Jacob"}
        ]}""" );

    List<Map.Entry<String, String>> pairs = json.queryMap(
        JsonSelector.of( "$.people[*]" ),
        p -> Map.entry( p.getString( "id" ).string(),
            p.getString( "name" ).string() ) );

    JsonObject materialised = Json.object( o ->
        pairs.forEach( e -> o.addString( e.getKey(), e.getValue() ) ) );

    assertEquals( "{\"u1\":\"James\",\"u2\":\"Jacob\"}", materialised.toJson() );
  }

  @Test
  void testQueryMap_EmptyResult() {
    JsonMixed json = JsonMixed.of( "{\"people\": []}" );
    List<String> names = json.queryMap(
        JsonSelector.of( "$.people[*]" ),
        p -> p.getString( "name" ).string() );
    assertTrue( names.isEmpty() );
  }

  @Test
  void testQueryMap_NullsSkipped() {
    JsonMixed json = JsonMixed.of( """
        {"people": [{"name": "James"}, {"nickname": "JJ"}, {"name": "Jacob"}]}""" );
    List<String> names = json.queryMap(
        JsonSelector.of( "$.people[*]" ),
        p -> p.getString( "name" ).isUndefined() ? null : p.getString( "name" ).string() );
    assertEquals( List.of( "James", "Jacob" ), names );
  }

  @Test
  void testQueryMap_NullsPreservedViaStream() {
    // what to do instead of queryMap to keep nulls
    JsonMixed json = JsonMixed.of( """
        {"people": [{"name": "James"}, {"nickname": "JJ"}, {"name": "Jacob"}]}""" );
    List<String> names = json.query( JsonSelector.of( "$.people[*]" ) )
        .map( p -> p.getString( "name" ).isUndefined() ? null : p.getString( "name" ).string() )
        .collect( toList() );
    assertEquals( java.util.Arrays.asList( "James", null, "Jacob" ), names );
  }
}
