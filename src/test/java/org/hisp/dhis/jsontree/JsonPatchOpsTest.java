package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchDeclarationException;
import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchTargetException;
import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchTestException;
import org.hisp.dhis.jsontree.internal.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JsonPatch#patchOps}.
 *
 * <p>Each test provides the target JSON and the authored patch (as JSON strings) and asserts the
 * desugared low-level operation list. Segments in expected {@link JsonPath}s are the concrete
 * resolved forms (keys as-is, indexes as non-negative integers), matching what {@link
 * JsonPathPattern#resolve} produces.
 */
class JsonPatchOpsTest {

  private static List<JsonNodeOp> ops(
      @Language("json") String target, @Language("json") String patch) {
    return JsonPatch.patchOps(JsonMixed.of(target), JsonMixed.of(patch).as(JsonPatch.class));
  }

  private static JsonPath path(String... segments) {
    return JsonPath.of(segments);
  }

  private static void assertOps(
      @Language("json") String target, @Language("json") String patch, JsonNodeOp... expected) {
    List<JsonNodeOp> actual = ops(target, patch);
    List<JsonNodeOp> exp = List.of(expected);
    if (exp.size() != actual.size()) assertEquals(exp, actual); // fails
    for (int i = 0; i < actual.size(); i++) {
      JsonNodeOp e = exp.get(i);
      JsonNodeOp a = actual.get(i);
      if (!e.path().equals(a.path())) assertEquals(exp, actual); // fails
      if (!e.value().getDeclaration().contentEquals(a.value().getDeclaration()))
        assertEquals(exp, actual); // fails
    }
  }

  // ===== remove =====

  @Test
  void remove_singleKey() {
    assertOps(
        "{\"foo\": 1}",
        """
            [{"op": "remove", "path": ["foo"]}]""",
        new JsonNodeOp.Remove(path("foo")));
  }

  @Test
  void remove_missingKey_isNoOp() {
    assertOps(
        "{\"foo\": 1}",
        """
            [{"op": "remove", "path": ["bar"]}]""");
  }

  @Test
  void remove_arrayIndex() {
    assertOps(
        "{\"foo\": [1,2,3]}",
        """
            [{"op": "remove", "path": ["foo", 1]}]""",
        new JsonNodeOp.Remove(path("foo", "1")));
  }

  @Test
  void remove_outOfBoundsIndex_isNoOp() {
    assertOps(
        "{\"foo\": [1,2,3]}",
        """
            [{"op": "remove", "path": ["foo", 5]}]""");
  }

  @Test
  void remove_arrayRange_expandsToSingleIndexes() {
    assertOps(
        "{\"foo\": [1,2,3,4,5]}",
        """
            [{"op": "remove", "path": ["foo", [[1,3]]]}]""",
        new JsonNodeOp.Remove(path("foo", "1")),
        new JsonNodeOp.Remove(path("foo", "2")),
        new JsonNodeOp.Remove(path("foo", "3")));
  }

  @Test
  void remove_negativeIndex() {
    assertOps(
        "{\"foo\": [1,2,3]}",
        """
            [{"op": "remove", "path": ["foo", -1]}]""",
        new JsonNodeOp.Remove(path("foo", "2")));
  }

  @Test
  void remove_enumerationWithDuplicateIndex_clashesDownstream() {
    // patchOps emits both; the clash detector is what rejects the pair.
    assertOps(
        "{\"foo\": [1,2,3]}",
        """
        [{"op": "remove", "path": ["foo", [0, 0]]}]""",
        new JsonNodeOp.Remove(path("foo", "0")),
        new JsonNodeOp.Remove(path("foo", "0")));
  }

  // ===== insert =====

  @Test
  void insert_singleKey() {
    assertOps(
        "{}",
        """
            [{"op": "insert", "path": ["foo"], "value": 1}]""",
        new JsonNodeOp.Insert(path("foo"), JsonNode.of("1")));
  }

  @Test
  void insert_arrayIndex() {
    assertOps(
        "{\"foo\": [1,2,3]}",
        """
            [{"op": "insert", "path": ["foo", 1], "value": 99}]""",
        new JsonNodeOp.Insert(path("foo", "1"), JsonNode.of("99")));
  }

  @Test
  void insert_appendWithNegativeZero() {
    assertOps(
        "{\"foo\": [1,2,3]}",
        """
            [{"op": "insert", "path": ["foo", -0], "value": 99}]""",
        new JsonNodeOp.Insert(path("foo", "3"), JsonNode.of("99")));
  }

  @Test
  void insert_rangePath_throws() {
    assertThrowsExactly(
        JsonPatchDeclarationException.class,
        () ->
            ops(
                "{\"foo\": [1,2,3]}",
                """
            [{"op": "insert", "path": ["foo", [[1,2]]], "value": 99}]"""));
  }

  @Test
  void insert_enumerationPath_throws() {
    assertThrowsExactly(
        JsonPatchDeclarationException.class,
        () ->
            ops(
                "{\"foo\": [1,2,3]}",
                """
        [{"op": "insert", "path": ["foo", [0, 2]], "value": 99}]"""));
  }

  @Test
  void spread_enumerationPath_throws() {
    assertThrowsExactly(
        JsonPatchDeclarationException.class,
        () ->
            ops(
                "{\"foo\": [1,2,3]}",
                """
        [{"op": "spread", "path": ["foo", [0, 2]], "value": [10,20]}]"""));
  }

  // ===== replace (single index) =====

  @Test
  void replace_singleKey() {
    assertOps(
        "{\"foo\": 1}",
        """
            [{"op": "replace", "path": ["foo"], "value": 2}]""",
        new JsonNodeOp.Replace(path("foo"), JsonNode.of("2")));
  }

  @Test
  void replace_arrayIndex() {
    assertOps(
        "{\"foo\": [1,2,3]}",
        """
            [{"op": "replace", "path": ["foo", 1], "value": 99}]""",
        new JsonNodeOp.Replace(path("foo", "1"), JsonNode.of("99")));
  }

  // ===== replace (range) =====

  @Test
  void replace_range_equalLength() {
    assertOps(
        "{\"foo\": [1,2,3,4,5]}",
        """
            [{"op": "replace", "path": ["foo", [[1,3]]], "value": [10,20,30]}]""",
        new JsonNodeOp.Replace(path("foo", "1"), JsonNode.of("10")),
        new JsonNodeOp.Replace(path("foo", "2"), JsonNode.of("20")),
        new JsonNodeOp.Replace(path("foo", "3"), JsonNode.of("30")));
  }

  @Test
  void replace_range_shorterValue() {
    assertOps(
        "{\"foo\": [1,2,3,4,5]}",
        """
            [{"op": "replace", "path": ["foo", [[1,3]]], "value": [10]}]""",
        new JsonNodeOp.Replace(path("foo", "1"), JsonNode.of("10")),
        new JsonNodeOp.Remove(path("foo", "2")),
        new JsonNodeOp.Remove(path("foo", "3")));
  }

  @Test
  void replace_range_emptyValue_deletesRange() {
    assertOps(
        "{\"foo\": [1,2,3,4,5]}",
        """
            [{"op": "replace", "path": ["foo", [[1,3]]], "value": []}]""",
        new JsonNodeOp.Remove(path("foo", "1")),
        new JsonNodeOp.Remove(path("foo", "2")),
        new JsonNodeOp.Remove(path("foo", "3")));
  }

  @Test
  void replace_range_longerValue() {
    // FAILS until the m > n branch is fixed — see review above.
    assertOps(
        "{\"foo\": [1,2,3,4,5]}",
        """
            [{"op": "replace", "path": ["foo", [[1,3]]], "value": [10,20,30,40,50]}]""",
        new JsonNodeOp.Replace(path("foo", "1"), JsonNode.of("10")),
        new JsonNodeOp.Replace(path("foo", "2"), JsonNode.of("20")),
        new JsonNodeOp.Replace(path("foo", "3"), JsonNode.of("30")),
        new JsonNodeOp.Spread(path("foo", "4"), JsonNode.of("[40,50]")));
  }

  @Test
  void replace_range_longerValue_byOne() {
    // Smallest case that exercises the m > n branch.
    assertOps(
        "{\"foo\": [1,2,3]}",
        """
            [{"op": "replace", "path": ["foo", [[0,0]]], "value": [10,20]}]""",
        new JsonNodeOp.Replace(path("foo", "0"), JsonNode.of("10")),
        new JsonNodeOp.Spread(path("foo", "1"), JsonNode.of("[20]")));
  }

  @Test
  void replace_range_noBroadcast() {
    // Scalar value; m = 1 via the isArray() guard.
    assertOps(
        "{\"foo\": [1,2,3,4,5]}",
        """
            [{"op": "replace", "path": ["foo", [[1,3]]], "value": [99]}]""",
        new JsonNodeOp.Replace(path("foo", "1"), JsonNode.of("99")),
        new JsonNodeOp.Remove(path("foo", "2")),
        new JsonNodeOp.Remove(path("foo", "3")));
  }

  @Test
  void replace_range_nonArrayValue() {
    // Scalar value; broadcasts to all affected range indexes
    assertOps(
        "{\"foo\": [1,2,3,4,5]}",
        """
            [{"op": "replace", "path": ["foo", [[1,3]]], "value": 99}]""",
        new JsonNodeOp.Replace(path("foo", "1"), JsonNode.of("99")),
        new JsonNodeOp.Replace(path("foo", "2"), JsonNode.of("99")),
        new JsonNodeOp.Replace(path("foo", "3"), JsonNode.of("99")));
  }

  @Test
  void replace_range_resolvesToEmpty_noOp() {
    assertOps(
        "{\"foo\": [1,2,3]}",
        """
            [{"op": "replace", "path": ["foo", [[5,10]]], "value": [10]}]""");
  }

  @Test
  void replace_range_resolvesToEmpty_scalarValue_noOp() {
    assertOps(
        "{\"foo\": [1,2,3]}",
        """
        [{"op": "replace", "path": ["foo", [[5,10]]], "value": 99}]""");
  }

  // ===== spread =====

  @Test
  void spread_arrayElements() {
    assertOps(
        "{\"foo\": [1,2,3]}",
        """
            [{"op": "spread", "path": ["foo", 1], "value": [10, 20]}]""",
        new JsonNodeOp.Spread(path("foo", "1"), JsonNode.of("[10, 20]")));
  }

  @Test
  void spread_objectMembers() {
    assertOps(
        "{\"foo\": {}}",
        """
            [{"op": "spread", "path": ["foo"], "value": {"a": 1, "b": 2}}]""",
        new JsonNodeOp.Spread(path("foo"), JsonNode.of("{\"a\": 1, \"b\": 2}")));
  }

  @Test
  void spread_rangePath_throws() {
    assertThrowsExactly(
        JsonPatchDeclarationException.class,
        () ->
            ops(
                "{\"foo\": [1,2,3]}",
                """
            [{"op": "spread", "path": ["foo", [[1,2]]], "value": [10,20]}]"""));
  }

  // ===== multiple operations =====

  @Test
  void multipleOps_preserveOrder() {
    assertOps(
        "{\"a\": 1, \"b\": [1,2,3]}",
        """
            [
              {"op": "remove", "path": ["a"]},
              {"op": "insert", "path": ["b", 0], "value": 99}
            ]""",
        new JsonNodeOp.Remove(path("a")),
        new JsonNodeOp.Insert(path("b", "0"), JsonNode.of("99")));
  }

  @Test
  void emptyPatch_producesNoOps() {
    assertOps("{\"foo\": 1}", "[]");
  }

  // ===== move =====

  @Test
  void move_simpleKey() {
    assertOps(
        "{\"a\": 1, \"b\": 2}",
        """
        [{"op": "move", "from": ["a"], "to": ["c"]}]""",
        new JsonNodeOp.Remove(path("a")),
        new JsonNodeOp.Insert(path("c"), JsonNode.of("1")));
  }

  @Test
  void move_missingSource_isNoOp() {
    assertOps(
        "{\"a\": 1}",
        """
        [{"op": "move", "from": ["x"], "to": ["c"]}]""");
  }

  @Test
  void move_arrayElement_toEnd() {
    assertOps(
        "{\"foo\": [10, 20, 30]}",
        """
        [{"op": "move", "from": ["foo", 0], "to": ["foo", -0]}]""",
        new JsonNodeOp.Remove(path("foo", "0")),
        new JsonNodeOp.Insert(path("foo", "3"), JsonNode.of("10")));
  }

  @Test
  void move_acrossContainers() {
    assertOps(
        "{\"a\": {\"x\": 1}, \"b\": {}}",
        """
        [{"op": "move", "from": ["a", "x"], "to": ["b", "y"]}]""",
        new JsonNodeOp.Remove(path("a", "x")),
        new JsonNodeOp.Insert(path("b", "y"), JsonNode.of("1")));
  }

  @Test
  void move_rangeSource_throws() {
    assertThrowsExactly(
        JsonPatchDeclarationException.class,
        () ->
            ops(
                "{\"foo\": [1,2,3]}",
                """
        [{"op": "move", "from": ["foo", [[0,1]]], "to": ["x"]}]"""));
  }

  @Test
  void move_rangeTarget_throws() {
    assertThrowsExactly(
        JsonPatchDeclarationException.class,
        () ->
            ops(
                "{\"foo\": [1,2,3]}",
                """
        [{"op": "move", "from": ["foo", 0], "to": ["bar", [[0,1]]]}]"""));
  }

  // ===== copy =====

  @Test
  void copy_simpleKey() {
    assertOps(
        "{\"a\": 1, \"b\": 2}",
        """
        [{"op": "copy", "from": ["a"], "to": ["c"]}]""",
        new JsonNodeOp.Insert(path("c"), JsonNode.of("1")));
  }

  @Test
  void copy_missingSource_isNoOp() {
    assertOps(
        "{\"a\": 1}",
        """
        [{"op": "copy", "from": ["x"], "to": ["c"]}]""");
  }

  @Test
  void copy_arrayElement() {
    assertOps(
        "{\"foo\": [10, 20]}",
        """
        [{"op": "copy", "from": ["foo", 0], "to": ["foo", -0]}]""",
        new JsonNodeOp.Insert(path("foo", "2"), JsonNode.of("10")));
  }

  @Test
  void copy_intoDifferentContainer() {
    assertOps(
        "{\"a\": {\"x\": 1}, \"b\": {}}",
        """
        [{"op": "copy", "from": ["a", "x"], "to": ["b", "y"]}]""",
        new JsonNodeOp.Insert(path("b", "y"), JsonNode.of("1")));
  }

  @Test
  void copy_rangeSource_throws() {
    assertThrowsExactly(
        JsonPatchDeclarationException.class,
        () ->
            ops(
                "{\"foo\": [1,2,3]}",
                """
        [{"op": "copy", "from": ["foo", [[0,1]]], "to": ["x"]}]"""));
  }

  // ===== swap =====

  @Test
  void swap_simpleKeys() {
    assertOps(
        "{\"a\": 1, \"b\": 2}",
        """
        [{"op": "swap", "from": ["a"], "to": ["b"]}]""",
        new JsonNodeOp.Replace(path("a"), JsonNode.of("2")),
        new JsonNodeOp.Replace(path("b"), JsonNode.of("1")));
  }

  @Test
  void swap_arrayElements() {
    assertOps(
        "{\"foo\": [10, 20, 30]}",
        """
        [{"op": "swap", "from": ["foo", 0], "to": ["foo", 2]}]""",
        new JsonNodeOp.Replace(path("foo", "0"), JsonNode.of("30")),
        new JsonNodeOp.Replace(path("foo", "2"), JsonNode.of("10")));
  }

  @Test
  void swap_acrossContainersAndDepths() {
    // Different depth: .a (object) at depth 1, .b.x at depth 2.
    // Two disjoint Replace ops; the values themselves differ in shape.
    assertOps(
        "{\"a\": {\"v\": 1}, \"b\": {\"x\": 42}}",
        """
        [{"op": "swap", "from": ["a"], "to": ["b", "x"]}]""",
        new JsonNodeOp.Replace(path("a"), JsonNode.of("42")),
        new JsonNodeOp.Replace(path("b", "x"), JsonNode.of("{\"v\": 1}")));
  }

  @Test
  void swap_objectsOfDifferentShapes() {
    assertOps(
        "{\"a\": {\"x\": 1}, \"b\": [1,2,3]}",
        """
        [{"op": "swap", "from": ["a"], "to": ["b"]}]""",
        new JsonNodeOp.Replace(path("a"), JsonNode.of("[1,2,3]")),
        new JsonNodeOp.Replace(path("b"), JsonNode.of("{\"x\": 1}")));
  }

  @Test
  void swap_missingFrom_isNoOp() {
    assertOps(
        "{\"a\": 1}",
        """
        [{"op": "swap", "from": ["x"], "to": ["a"]}]""");
  }

  @Test
  void swap_missingTo_isNoOp() {
    assertOps(
        "{\"a\": 1}",
        """
        [{"op": "swap", "from": ["a"], "to": ["x"]}]""");
  }

  @Test
  void swap_samePath_isNoOp() {
    // Without the from.equals(to) short-circuit this would emit two Replace
    // ops at the same path, which clash. The short-circuit avoids that.
    assertOps(
        "{\"a\": 1}",
        """
        [{"op": "swap", "from": ["a"], "to": ["a"]}]""");
  }

  @Test
  void swap_rangeSource_throws() {
    assertThrowsExactly(
        JsonPatchDeclarationException.class,
        () ->
            ops(
                "{\"foo\": [1,2,3]}",
                """
        [{"op": "swap", "from": ["foo", [[0,1]]], "to": ["a"]}]"""));
  }

  @Test
  void swap_rangeTarget_throws() {
    assertThrowsExactly(
        JsonPatchDeclarationException.class,
        () ->
            ops(
                "{\"foo\": [1,2,3]}",
                """
        [{"op": "swap", "from": ["a"], "to": ["foo", [[0,1]]]}]"""));
  }

  @Test
  void swap_ancestorIsProducedButWillClashDownstream() {
    // patchOps itself does not run the claim check, so this emits two
    // Replace ops that the downstream clash detector will reject.
    // Pinned so the behaviour is deliberate, not incidental.
    assertOps(
        "{\"a\": {\"b\": 1}}",
        """
        [{"op": "swap", "from": ["a"], "to": ["a", "b"]}]""",
        new JsonNodeOp.Replace(path("a"), JsonNode.of("1")),
        new JsonNodeOp.Replace(path("a", "b"), JsonNode.of("{\"b\": 1}")));
  }

  // ===== test (guard) =====

  @Test
  void test_existingKey_passes_noOps() {
    assertOps(
        "{\"a\": 1}",
        """
        [{"op": "test", "path": ["a"]}]""");
  }

  @Test
  void test_missingKey_throws() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{}",
                """
        [{"op": "test", "path": ["a"]}]"""));
  }

  @Test
  void test_nestedKey_passes() {
    assertOps(
        "{\"a\": {\"b\": 1}}",
        """
        [{"op": "test", "path": ["a", "b"]}]""");
  }

  @Test
  void test_nestedKeyMissingAncestor_throws() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{}",
                """
        [{"op": "test", "path": ["a", "b"]}]"""));
  }

  @Test
  void test_conditionMatches_passes() {
    assertOps(
        "{\"foo\": [{\"id\":\"x\"},{\"id\":\"y\"}]}",
        """
        [{"op": "test", "path": ["foo", [{"id": "x"}]]}]""");
  }

  @Test
  void test_conditionNoMatch_throws() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"foo\": [{\"id\":\"y\"}]}",
                """
        [{"op": "test", "path": ["foo", [{"id":"x"}]]}]"""));
  }

  @Test
  void test_valueConditionMatches_passes() {
    assertOps(
        "{\"foo\": [1,2,3]}",
        """
        [{"op": "test", "path": ["foo", ["=", 2]]}]""");
  }

  @Test
  void test_valueConditionNoMatch_throws() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"foo\": [1,2,3]}",
                """
        [{"op": "test", "path": ["foo", ["=", 99]]}]"""));
  }

  @Test
  void test_arrayHasMinimumElements() {
    // Range test: "foo has at least 5 elements"
    assertOps(
        "{\"foo\": [1,2,3,4,5]}",
        """
        [{"op": "test", "path": ["foo", [[4,4]]]}]""");
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"foo\": [1,2,3]}",
                """
        [{"op": "test", "path": ["foo", [[4,4]]]}]"""));
  }

  @Test
  void test_rangeWithNoElements_throws() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"foo\": []}",
                """
        [{"op": "test", "path": ["foo", [[0,5]]]}]"""));
  }

  // ===== test as a guard around mutations =====

  @Test
  void test_passes_thenMutationsApplied() {
    assertOps(
        "{\"a\": 1, \"b\": 2}",
        """
        [
          {"op": "test", "path": ["a"]},
          {"op": "remove", "path": ["b"]}
        ]""",
        new JsonNodeOp.Remove(path("b")));
  }

  @Test
  void test_fails_nothingApplied() {
    // Whole patch rejected — the mutation ops already accumulated are lost.
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"a\": 1, \"b\": 2}",
                """
        [
          {"op": "remove", "path": ["b"]},
          {"op": "test", "path": ["x"]}
        ]"""));
  }

  @Test
  void test_positionDoesNotMatter() {
    // Same patch, test at top vs. bottom — both reject.
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"a\": 1}",
                """
        [
          {"op": "test", "path": ["x"]},
          {"op": "remove", "path": ["a"]}
        ]"""));
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"a\": 1}",
                """
        [
          {"op": "remove", "path": ["a"]},
          {"op": "test", "path": ["x"]}
        ]"""));
  }

  @Test
  void test_idempotencyGuard_pattern() {
    // The motivating case: only apply the "publish" mutation if still draft.
    // After first application, [?status=draft] no longer matches, so re-applying
    // the same patch fails the test and does nothing.
    String target =
        """
        {"articles": [{"id": "a1", "status": "draft"}]}""";
    String patch =
        """
        [
          {"op": "test",   "path": ["articles", [{"status":"draft"}]]},
          {"op": "replace","path": ["articles", [{"status":"draft"}], "status"], "value": "published"}
        ]""";
    assertOps(
        target,
        patch,
        new JsonNodeOp.Replace(path("articles", "0", "status"), JsonNode.of("\"published\"")));
  }

  // ===== test with value: primitives =====

  @Test
  void test_value_number_passes() {
    assertOps(
        "{\"a\": 1}",
        """
        [{"op": "test", "path": ["a"], "value": 1}]""");
  }

  @Test
  void test_value_number_fails() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"a\": 1}",
                """
        [{"op": "test", "path": ["a"], "value": 2}]"""));
  }

  @Test
  void test_value_string_passes() {
    assertOps(
        "{\"a\": \"hello\"}",
        """
        [{"op": "test", "path": ["a"], "value": "hello"}]""");
  }

  @Test
  void test_value_string_fails() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"a\": \"hello\"}",
                """
        [{"op": "test", "path": ["a"], "value": "world"}]"""));
  }

  @Test
  void test_value_boolean_passes() {
    assertOps(
        "{\"a\": true}",
        """
        [{"op": "test", "path": ["a"], "value": true}]""");
  }

  @Test
  void test_value_boolean_fails() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"a\": true}",
                """
        [{"op": "test", "path": ["a"], "value": false}]"""));
  }

  @Test
  void test_value_typeMismatch_fails() {
    // 1 (number) vs "1" (string) — structural compare distinguishes them
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"a\": 1}",
                """
        [{"op": "test", "path": ["a"], "value": "1"}]"""));
  }

  // ===== test with value: composite =====

  @Test
  void test_value_object_passes() {
    assertOps(
        "{\"a\": {\"x\": 1, \"y\": 2}}",
        """
        [{"op": "test", "path": ["a"], "value": {"x": 1, "y": 2}}]""");
  }

  @Test
  void test_value_object_propertyOrderIgnored() {
    // JSON object member order is not significant; structural compare
    // should treat {"x":1,"y":2} and {"y":2,"x":1} as equal.
    assertOps(
        "{\"a\": {\"x\": 1, \"y\": 2}}",
        """
        [{"op": "test", "path": ["a"], "value": {"y": 2, "x": 1}}]""");
  }

  @Test
  void test_value_object_extraProperty_fails() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"a\": {\"x\": 1}}",
                """
        [{"op": "test", "path": ["a"], "value": {"x": 1, "y": 2}}]"""));
  }

  @Test
  void test_value_object_missingProperty_fails() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"a\": {\"x\": 1, \"y\": 2}}",
                """
        [{"op": "test", "path": ["a"], "value": {"x": 1}}]"""));
  }

  @Test
  void test_value_array_passes() {
    assertOps(
        "{\"a\": [1,2,3]}",
        """
        [{"op": "test", "path": ["a"], "value": [1,2,3]}]""");
  }

  @Test
  void test_value_array_orderMatters_fails() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"a\": [1,2,3]}",
                """
        [{"op": "test", "path": ["a"], "value": [3,2,1]}]"""));
  }

  @Test
  void test_value_array_lengthMatters_fails() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"a\": [1,2,3]}",
                """
        [{"op": "test", "path": ["a"], "value": [1,2,3,4]}]"""));
  }

  // ===== test with value combined with conditions =====

  @Test
  void test_value_withCondition_passes() {
    assertOps(
        "{\"foo\": [{\"id\":\"x\",\"v\":1}]}",
        """
        [{"op": "test", "path": ["foo", [{"id":"x"}]], "value": {"id":"x","v":1}}]""");
  }

  @Test
  void test_value_withCondition_valueMismatch_fails() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"foo\": [{\"id\":\"x\",\"v\":1}]}",
                """
        [{"op": "test", "path": ["foo", [{"id":"x"}]], "value": {"id":"x","v":2}}]"""));
  }

  @Test
  void test_value_withCondition_noMatch_fails() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"foo\": [{\"id\":\"x\"}]}",
                """
        [{"op": "test", "path": ["foo", [{"id":"y"}]], "value": {"id":"y"}}]"""));
  }

  // ===== test with value and missing node =====

  @Test
  void test_value_nodeMissing_fails() {
    // The existence check fires before the value check
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{}",
                """
        [{"op": "test", "path": ["a"], "value": 1}]"""));
  }

  @Test
  void test_value_nodeMissingNested_fails() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"a\": {}}",
                """
        [{"op": "test", "path": ["a", "b"], "value": 1}]"""));
  }

  // ===== test with value and range =====

  @Test
  void test_value_withRange_throws() {
    // Combining value with a multi-path range is rejected as ambiguous:
    // "all elements equal value" is not the client's likely intent.
    assertThrowsExactly(
        JsonPatchDeclarationException.class,
        () ->
            ops(
                "{\"foo\": [1,2,3]}",
                """
        [{"op": "test", "path": ["foo", [[0,2]]], "value": 1}]"""));
  }

  // ===== idempotency / modCount guard pattern =====

  @Test
  void test_value_modCountGuard_passes() {
    assertOps(
        "{\"modCount\": 5, \"data\": [1,2,3]}",
        """
        [
          {"op": "test",   "path": ["modCount"], "value": 5},
          {"op": "insert", "path": ["data", -0], "value": 4}
        ]""",
        new JsonNodeOp.Insert(path("data", "3"), JsonNode.of("4")));
  }

  @Test
  void test_value_modCountGuard_stale_fails() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"modCount\": 6, \"data\": [1,2,3]}",
                """
        [
          {"op": "test",   "path": ["modCount"], "value": 5},
          {"op": "insert", "path": ["data", -0], "value": 4}
        ]"""));
  }

  @Test
  void test_value_objectSnapshotGuard_passes() {
    // Guard against concurrent modification: the whole article matches.
    String target =
        """
        {"articles": [{"id": "a1", "status": "draft", "rev": 3}]}""";
    String patch =
        """
        [
          {"op": "test",    "path": ["articles", [{"id":"a1"}]], "value": {"id":"a1","status":"draft","rev":3}},
          {"op": "replace", "path": ["articles", [{"id":"a1"}], "status"], "value": "published"}
        ]""";
    assertOps(
        target,
        patch,
        new JsonNodeOp.Replace(path("articles", "0", "status"), JsonNode.of("\"published\"")));
  }

  @Test
  void test_value_objectSnapshotGuard_stale_fails() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                """
        {"articles": [{"id": "a1", "status": "draft", "rev": 4}]}""",
                """
        [
          {"op": "test",    "path": ["articles", [{"id":"a1"}]], "value": {"id":"a1","status":"draft","rev":3}},
          {"op": "replace", "path": ["articles", [{"id":"a1"}], "status"], "value": "published"}
        ]"""));
  }

  @Test
  void test_value_singleElementRange_passes() {
    // [[3,3]] resolves to one index,
    // but we do not accept ranges for test with value
    assertThrowsExactly(
        JsonPatchDeclarationException.class,
        () ->
            ops(
                "{\"foo\": [1,2,3,4]}",
                """
            [{"op": "test", "path": ["foo", [[3,3]]], "value": 4}]"""));
  }

  @Test
  void test_value_singleElementEnumeration_passes() {
    // [[3]] resolves to one index,
    // but we do not accept enumerations for test with value
    assertThrowsExactly(
        JsonPatchDeclarationException.class,
        () ->
            ops(
                "{\"foo\": [1,2,3,4]}",
                """
        [{"op": "test", "path": ["foo", [2]], "value": 3}]"""));
  }

  @Test
  void test_value_explicitNull() {
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            ops(
                "{\"a\": 1}",
                """
        [{"op": "test", "path": ["a"], "value": null}]"""));
    assertOps(
        "{\"a\": null}",
        """
        [{"op": "test", "path": ["a"], "value": null}]""");
  }

  // ==== more gaps ====

  // patchOps
  @Test
  void insert_enumeration_throws() {
    assertThrowsExactly(
        JsonPatchDeclarationException.class,
        () ->
            ops(
                "{\"foo\": [1,2,3]}",
                """
        [{"op": "insert", "path": ["foo", [0, 2]], "value": 99}]"""));
  }

  @Test
  void remove_enumeration_expands() {
    assertOps(
        "{\"foo\": [1,2,3,4,5]}",
        """
        [{"op": "remove", "path": ["foo", [0, 2, 4]]}]""",
        new JsonNodeOp.Remove(path("foo", "0")),
        new JsonNodeOp.Remove(path("foo", "2")),
        new JsonNodeOp.Remove(path("foo", "4")));
  }

  // ==== pointer use =====
  @Test
  void pointer_appendToArray() {
    assertOps(
        "{\"arr\": [1,2,3]}",
        """
        [{"op": "add", "path": "/arr/-", "value": 4}]""",
        new JsonNodeOp.Insert(path("arr", "3"), JsonNode.of("4")));
  }

  @Test
  void pointer_dashAsObjectKey() {
    assertOps(
        "{\"-\": 42}",
        """
        [{"op": "test", "path": "/-"}]"""); // passes: key "-" exists
  }

  @Test
  void pointer_dashAppendToRoot() {
    assertOps(
        "[1,2,3]",
        """
        [{"op": "add", "path": "/-", "value": 4}]""",
        new JsonNodeOp.Insert(path("3"), JsonNode.of("4")));
  }

  @Test
  void pointer_dashParentNotArray_throws() {
    assertThrows(
        JsonPatchTargetException.class,
        () ->
            ops(
                "{\"arr\": \"str\"}",
                """
        [{"op": "add", "path": "/arr/-", "value": 4}]"""));
  }

  @Test
  void pointer_dashParentMissing_throws() {
    assertThrows(
        JsonPatchTargetException.class,
        () ->
            ops(
                "{}",
                """
        [{"op": "add", "path": "/arr/-", "value": 4}]"""));
  }

  @Test
  void pointer_dashMidPathFailsDownstream() {
    // /a/-/b decodes to a.b index=a.size, which is not addressable
    assertThrows(
        RuntimeException.class,
        () ->
            ops(
                "{\"a\": [{\"b\": 1}]}",
                """
        [{"op": "test", "path": "/a/-/b"}]"""));
  }
}
