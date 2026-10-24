package org.hisp.dhis.jsontree;

import static org.hisp.dhis.jsontree.JsonSelector.$;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

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

  // ---------------------------------------------------------------------------
  // descendants shorthand (added with the parser fix)
  // ---------------------------------------------------------------------------

  @Test
  void testOf_DescendantsShorthand() {
    // RFC 9535 shorthand: $..name is equivalent to $..['name']
    // Both spellings normalise to descendants + key, so toString is identical
    assertEquals("$...name", JsonSelector.of("$..name").toString());
    assertEquals("$...name", JsonSelector.of("$..['name']").toString());
    assertEquals(
        JsonSelector.of("$..name").toString(),
        JsonSelector.of("$..['name']").toString());
  }

  @Test
  void testOf_DescendantsChained() {
    assertEquals("$...a.b", JsonSelector.of("$..a.b").toString());
    assertEquals("$...a...b", JsonSelector.of("$..a..b").toString());
    assertEquals("$...a...b.c", JsonSelector.of("$..a..b.c").toString());
  }

  @Test
  void testOf_DescendantsFollowedByBracket() {
    // The fix must not steal these existing forms
    assertEquals("$..[*]", JsonSelector.of("$..[*]").toString());
    assertEquals("$..[1]", JsonSelector.of("$..[1]").toString());
    assertEquals("$..[1,2]", JsonSelector.of("$..[1,2]").toString());
    assertEquals("$..[1:3]", JsonSelector.of("$..[1:3]").toString());
    assertEquals("$...name", JsonSelector.of("$..['name']").toString());
    // and bare $.. still parses (nothing after the dots)
    assertEquals("$..", JsonSelector.of("$..").toString());
  }

  @Test
  void testOf_DescendantsMixed() {
    assertEquals("$...foo[*]", JsonSelector.of("$..foo[*]").toString());
    assertEquals("$...foo[1]", JsonSelector.of("$..foo[1]").toString());
    assertEquals("$...foo[1].bar", JsonSelector.of("$..foo[1].bar").toString());
    assertEquals("$.foo...bar", JsonSelector.of("$.foo..bar").toString());
  }

  // ---------------------------------------------------------------------------
  // roots, bracketed keys, unions, slices
  // ---------------------------------------------------------------------------

  @Test
  void testOf_Roots() {
    assertEquals("$", JsonSelector.of("$").toString());
    assertEquals("@", JsonSelector.of("@").toString());
    assertEquals("$.foo", JsonSelector.of("$.foo").toString());
    assertEquals("@.foo", JsonSelector.of("@.foo").toString());
    assertEquals("$.foo.bar", JsonSelector.of("$.foo.bar").toString());
  }

  @Test
  void testOf_BracketedKeys() {
    // bracketed and dotted forms normalise to the same internal chain
    assertEquals("$.foo", JsonSelector.of("$['foo']").toString());
    assertEquals("$.foo", JsonSelector.of("$.foo").toString());
    assertEquals("$.foo.bar", JsonSelector.of("$.foo['bar']").toString());
    assertEquals("$.foo.bar", JsonSelector.of("$['foo']['bar']").toString());
  }

  @Test
  void testOf_Unions() {
    assertEquals("$[1,2]", JsonSelector.of("$[1,2]").toString());
    assertEquals("$[3,1,2]", JsonSelector.of("$[3,1,2]").toString());
    assertEquals("$.foo[1,2]", JsonSelector.of("$.foo[1,2]").toString());
  }

  @Test
  void testOf_Slices() {
    assertEquals("$[:5]", JsonSelector.of("$[:5]").toString());
    assertEquals("$[1:]", JsonSelector.of("$[1:]").toString());
    assertEquals("$[::2]", JsonSelector.of("$[::2]").toString());
    assertEquals("$[1:5:2]", JsonSelector.of("$[1:5:2]").toString());
    assertEquals("$[::-1]", JsonSelector.of("$[::-1]").toString());
  }

  // ---------------------------------------------------------------------------
  // empty input, fluent/parse equivalence, errors
  // ---------------------------------------------------------------------------

  @Test
  void testOf_EmptyExpression() {
    assertEquals("$", JsonSelector.of("").toString());
  }

  @Test
  void testOf_EquivalenceWithFluentApi() {
    // every parse should equal the fluent construction it expands to
    assertEquals(
        $.descendants().key("name").toString(),
        JsonSelector.of("$..name").toString());
    assertEquals(
        $.descendants().key("name").toString(),
        JsonSelector.of("$..['name']").toString());
    assertEquals(
        $.key("foo").any().key("bar").toString(),
        JsonSelector.of("$.foo[*].bar").toString());
    assertEquals(
        $.key("foo").descendants().key("bar").toString(),
        JsonSelector.of("$.foo..bar").toString());
    assertEquals(
        $.descendants().key("foo").any().toString(),
        JsonSelector.of("$..foo[*]").toString());
  }

  @Test
  void testOf_Errors() {
    // anything that is neither a segment start nor part of a key is illegal
    assertThrows(IllegalArgumentException.class, () -> JsonSelector.of("%"));
    assertThrows(IllegalArgumentException.class, () -> JsonSelector.of("$%foo"));
    assertThrows(IllegalArgumentException.class, () -> JsonSelector.of("$.foo%"));
    assertThrows(IllegalArgumentException.class, () -> JsonSelector.of("$..%"));
  }

  // ---------------------------------------------------------------------------
  // shorthand actually matches at query time, not just in toString
  // ---------------------------------------------------------------------------

  @Test
  void testOf_DescendantsShorthand_Matching() {
    JsonMixed json =
        JsonMixed.of(
            """
      {
        "people": [
          { "name": "Ada" },
          { "name": "Grace" }
        ]
      }
      """);

    assertEquals(2, json.queryCount(JsonSelector.of("$..name")));
    assertEquals(2, json.queryCount(JsonSelector.of("$..['name']")));
    assertEquals(
        json.query(JsonSelector.of("$..name")).toList(),
        json.query(JsonSelector.of("$..['name']")).toList());
  }

  // ---------------------------------------------------------------------------
  // Fix 1: $[:] and $[::] must not throw; step preservation
  // ---------------------------------------------------------------------------

  @Test
  void testOf_EmptySlice() {
    assertEquals("$[:]", JsonSelector.of("$[:]").toString());
    assertEquals("$[:]", JsonSelector.of("$[::]").toString()); // step 1 is default
    assertEquals("$[::2]", JsonSelector.of("$[::2]").toString());
    assertEquals("$[::-1]", JsonSelector.of("$[::-1]").toString());
    assertEquals("$[1::2]", JsonSelector.of("$[1::2]").toString());
  }

  // ---------------------------------------------------------------------------
  // Fix 2: ? type filter — bracket form (RFC 9535) and top-level form
  // ---------------------------------------------------------------------------

  @Test
  void testOf_TypeFilter() {
    // bracketed form: RFC child selector
    assertEquals("$[?(:string)]", JsonSelector.of("$[?(:string)]").toString());
    assertEquals("$[?(:number)]", JsonSelector.of("$[?(:number)]").toString());
    assertEquals("$[?(:object)]", JsonSelector.of("$[?(:object)]").toString());
    assertEquals(
        JsonSelector.of("$[?(:string)]").toString(),
        JsonSelector.of("$[?(:STRING)]").toString());
    // top-level form: narrowing filter on the current node
    assertEquals("$?(:string)", JsonSelector.of("$?(:string)").toString());
    // unknown type still errors
    assertThrows(IllegalArgumentException.class, () -> JsonSelector.of("$[?(:nope)]"));
  }

  @Test
  void testOf_TypeFilter_Semantics() {
    JsonMixed json = JsonMixed.of("[1, \"a\", true, null, 2, [3], {\"k\":4}]");
    // bracketed form selects children of the array root
    assertEquals("[1, 2]",
        json.query(JsonSelector.of("$[?(:number)]")).toList().toString());
    // top-level form narrows the root itself — root is an array, not a number
    assertEquals(0, json.queryCount(JsonSelector.of("$?(:number)")));
  }

  @Test
  void testOf_TypeFilter_RoundTrip() {
    // toString output must re-parse to an equal selector
    for (JsonNodeType t : JsonNodeType.values()) {
      JsonSelector s = JsonSelector.of("$").type(t);
      assertEquals(s, JsonSelector.of(s.toString()),
          "round-trip failed for type " + t);
    }
  }

  // ---------------------------------------------------------------------------
  // Fix 3: negative indexes are indexes, not slices
  // ---------------------------------------------------------------------------

  @Test
  void testOf_NegativeIndex() {
    assertEquals("$[-1]", JsonSelector.of("$[-1]").toString());
    assertEquals("$[-1]", JsonSelector.of("$[-1]").toString());
    // slice syntax unchanged
    assertEquals("$[-1:]", JsonSelector.of("$[-1:]").toString());
    assertEquals("$[-1:]", JsonSelector.of("$[-1::]").toString());
    assertEquals("$[:-1]", JsonSelector.of("$[:-1]").toString());
    // union of negatives works
    assertEquals("$[-1,-2]", JsonSelector.of("$[-1,-2]").toString());
  }

  @Test
  void testNegativeIndex_Matching() {
    JsonMixed json = JsonMixed.of( "[\"a\",\"b\",\"c\"]" );
    assertEquals(
        "[\"c\"]",
        json.query( JsonSelector.of( "$[-1]" ) ).toList().toString() );
    assertEquals(
        "[\"a\"]",
        json.query( JsonSelector.of( "$[-3]" ) ).toList().toString() );
    // out of range is empty, not an error
    assertEquals(
        0,
        json.queryCount( JsonSelector.of( "$[-4]" ) ) );
    assertEquals(
        0,
        json.queryCount( JsonSelector.of( "$[3]" ) ) );
  }

  // ---------------------------------------------------------------------------

  @Test
  void testOf_Union_NoWhitespace() {
    // whitespace around commas is not part of the supported subset — both
    // old and new parsers reject it
    assertEquals("$[1,2,3]", JsonSelector.of("$[1,2,3]").toString());
    assertThrows(IllegalArgumentException.class, () -> JsonSelector.of("$[1, 2]"));
    assertThrows(IllegalArgumentException.class, () -> JsonSelector.of("$[1 ,2]"));
  }

  @Test
  void testOf_StringVsText() {
    // String is a full expression
    assertEquals("$...foo", JsonSelector.of("$..foo").toString());
    // Text is a literal key segment — syntax characters are not interpreted
    assertEquals(".x.y", JsonSelector.of(Text.of("x.y")).toString());
    assertEquals(".$..foo", JsonSelector.of(Text.of("$..foo")).toString());
  }

  @Test
  void testFlatten_ToString() {
    assertEquals( "$[]", JsonSelector.$.flatten().toString() );
    assertEquals( "$.foo[]", JsonSelector.$.key( "foo" ).flatten().toString() );
  }

  @Test
  void testOfFlatten_RoundTrip() {
    assertEquals( JsonSelector.$.flatten().toString(),
        JsonSelector.of( "$[]" ).toString() );
  }
}
