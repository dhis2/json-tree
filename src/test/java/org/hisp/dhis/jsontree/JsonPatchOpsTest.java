package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JsonPatch#patchOps}.
 *
 * <p>Each test provides the target JSON and the authored patch (as JSON strings)
 * and asserts the desugared low-level operation list. Segments in expected
 * {@link JsonPath}s are the concrete resolved forms (keys as-is, indexes as
 * non-negative integers), matching what {@link JsonPathExpression#resolve}
 * produces.
 */
class JsonPatchOpsTest {

    private static List<JsonNodeOp> ops(String target, String patch) {
        return JsonPatch.patchOps(JsonMixed.of(target), JsonMixed.of(patch).as(JsonPatch.class));
    }

    private static JsonPath path(String... segments) {
       return JsonPath.of(segments);
    }

    private static void assertOps(@Language("json") String target, @Language("json") String patch, JsonNodeOp... expected) {
        List<JsonNodeOp> actual = ops(target, patch);
        List<JsonNodeOp> exp = List.of(expected);
        if (exp.size() != actual.size())
            assertEquals(exp, actual); // fails
        for (int i = 0; i < actual.size(); i++) {
            JsonNodeOp e = exp.get(i);
            JsonNodeOp a = actual.get(i);
            if (!e.path().equals(a.path()))
                assertEquals(exp, actual); // fails
            if (!e.value().getDeclaration().contentEquals(a.value().getDeclaration()))
                assertEquals(exp, actual); // fails
        }
    }

    // ===== remove =====

    @Test
    void remove_singleKey() {
        assertOps("{\"foo\": 1}", """
            [{"op": "remove", "path": ["foo"]}]""",
            new JsonNodeOp.Remove(path("foo")));
    }

    @Test
    void remove_missingKey_isNoOp() {
        assertOps("{\"foo\": 1}", """
            [{"op": "remove", "path": ["bar"]}]""");
    }

    @Test
    void remove_arrayIndex() {
        assertOps("{\"foo\": [1,2,3]}", """
            [{"op": "remove", "path": ["foo", "[1]"]}]""",
            new JsonNodeOp.Remove(path("foo", "1")));
    }

    @Test
    void remove_outOfBoundsIndex_isNoOp() {
        assertOps("{\"foo\": [1,2,3]}", """
            [{"op": "remove", "path": ["foo", "[5]"]}]""");
    }

    @Test
    void remove_arrayRange_expandsToSingleIndexes() {
        assertOps("{\"foo\": [1,2,3,4,5]}", """
            [{"op": "remove", "path": ["foo", "[1:3]"]}]""",
            new JsonNodeOp.Remove(path("foo", "1")),
            new JsonNodeOp.Remove(path("foo", "2")),
            new JsonNodeOp.Remove(path("foo", "3")));
    }

    @Test
    void remove_negativeIndex() {
        assertOps("{\"foo\": [1,2,3]}", """
            [{"op": "remove", "path": ["foo", "[-1]"]}]""",
            new JsonNodeOp.Remove(path("foo", "2")));
    }

    // ===== insert =====

    @Test
    void insert_singleKey() {
        assertOps("{}", """
            [{"op": "insert", "path": ["foo"], "value": 1}]""",
            new JsonNodeOp.Insert(path("foo"), JsonNode.of("1")));
    }

    @Test
    void insert_arrayIndex() {
        assertOps("{\"foo\": [1,2,3]}", """
            [{"op": "insert", "path": ["foo", "[1]"], "value": 99}]""",
            new JsonNodeOp.Insert(path("foo", "1"), JsonNode.of("99")));
    }

    @Test
    void insert_appendWithNegativeZero() {
        assertOps("{\"foo\": [1,2,3]}", """
            [{"op": "insert", "path": ["foo", "[-0]"], "value": 99}]""",
            new JsonNodeOp.Insert(path("foo", "3"), JsonNode.of("99")));
    }

    @Test
    void insert_rangePath_throws() {
        assertThrows(JsonPatchException.class, () -> ops("{\"foo\": [1,2,3]}", """
            [{"op": "insert", "path": ["foo", "[1:2]"], "value": 99}]"""));
    }

    // ===== replace (single index) =====

    @Test
    void replace_singleKey() {
        assertOps("{\"foo\": 1}", """
            [{"op": "replace", "path": ["foo"], "value": 2}]""",
            new JsonNodeOp.Replace(path("foo"), JsonNode.of("2")));
    }

    @Test
    void replace_arrayIndex() {
        assertOps("{\"foo\": [1,2,3]}", """
            [{"op": "replace", "path": ["foo", "[1]"], "value": 99}]""",
            new JsonNodeOp.Replace(path("foo", "1"), JsonNode.of("99")));
    }

    // ===== replace (range) =====

    @Test
    void replace_range_equalLength() {
        assertOps("{\"foo\": [1,2,3,4,5]}", """
            [{"op": "replace", "path": ["foo", "[1:3]"], "value": [10,20,30]}]""",
            new JsonNodeOp.Replace(path("foo", "1"), JsonNode.of("10")),
            new JsonNodeOp.Replace(path("foo", "2"), JsonNode.of("20")),
            new JsonNodeOp.Replace(path("foo", "3"), JsonNode.of("30")));
    }

    @Test
    void replace_range_shorterValue() {
        assertOps("{\"foo\": [1,2,3,4,5]}", """
            [{"op": "replace", "path": ["foo", "[1:3]"], "value": [10]}]""",
            new JsonNodeOp.Replace(path("foo", "1"), JsonNode.of("10")),
            new JsonNodeOp.Remove(path("foo", "2")),
            new JsonNodeOp.Remove(path("foo", "3")));
    }

    @Test
    void replace_range_emptyValue_deletesRange() {
        assertOps("{\"foo\": [1,2,3,4,5]}", """
            [{"op": "replace", "path": ["foo", "[1:3]"], "value": []}]""",
            new JsonNodeOp.Remove(path("foo", "1")),
            new JsonNodeOp.Remove(path("foo", "2")),
            new JsonNodeOp.Remove(path("foo", "3")));
    }

    @Test
    void replace_range_longerValue() {
        // FAILS until the m > n branch is fixed — see review above.
        assertOps("{\"foo\": [1,2,3,4,5]}", """
            [{"op": "replace", "path": ["foo", "[1:3]"], "value": [10,20,30,40,50]}]""",
            new JsonNodeOp.Replace(path("foo", "1"), JsonNode.of("10")),
            new JsonNodeOp.Replace(path("foo", "2"), JsonNode.of("20")),
            new JsonNodeOp.Replace(path("foo", "3"), JsonNode.of("30")),
            new JsonNodeOp.Spread(path("foo", "4"), JsonNode.of("[40,50]")));
    }

    @Test
    void replace_range_longerValue_byOne() {
        // Smallest case that exercises the m > n branch.
        assertOps("{\"foo\": [1,2,3]}", """
            [{"op": "replace", "path": ["foo", "[0:0]"], "value": [10,20]}]""",
            new JsonNodeOp.Replace(path("foo", "0"), JsonNode.of("10")),
            new JsonNodeOp.Spread(path("foo", "1"), JsonNode.of("[20]")));
    }

    @Test
    void replace_range_nonArrayValue() {
        // Scalar value; m = 1 via the isArray() guard.
        assertOps("{\"foo\": [1,2,3,4,5]}", """
            [{"op": "replace", "path": ["foo", "[1:3]"], "value": 99}]""",
            new JsonNodeOp.Replace(path("foo", "1"), JsonNode.of("99")),
            new JsonNodeOp.Remove(path("foo", "2")),
            new JsonNodeOp.Remove(path("foo", "3")));
    }

    @Test
    void replace_range_resolvesToEmpty_noOp() {
        assertOps("{\"foo\": [1,2,3]}", """
            [{"op": "replace", "path": ["foo", "[5:10]"], "value": [10]}]""");
    }

    @Test
    void replace_range_resolvesToEmpty_scalarValue_noOp() {
        assertOps("{\"foo\": [1,2,3]}", """
        [{"op": "replace", "path": ["foo", "[5:10]"], "value": 99}]""");
    }

    // ===== spread =====

    @Test
    void spread_arrayElements() {
        assertOps("{\"foo\": [1,2,3]}", """
            [{"op": "spread", "path": ["foo", "[1]"], "value": [10, 20]}]""",
            new JsonNodeOp.Spread(path("foo", "1"), JsonNode.of("[10, 20]")));
    }

    @Test
    void spread_objectMembers() {
        assertOps("{\"foo\": {}}", """
            [{"op": "spread", "path": ["foo"], "value": {"a": 1, "b": 2}}]""",
            new JsonNodeOp.Spread(path("foo"), JsonNode.of("{\"a\": 1, \"b\": 2}")));
    }

    @Test
    void spread_rangePath_throws() {
        assertThrows(JsonPatchException.class, () -> ops("{\"foo\": [1,2,3]}", """
            [{"op": "spread", "path": ["foo", "[1:2]"], "value": [10,20]}]"""));
    }

    // ===== multiple operations =====

    @Test
    void multipleOps_preserveOrder() {
        assertOps("{\"a\": 1, \"b\": [1,2,3]}", """
            [
              {"op": "remove", "path": ["a"]},
              {"op": "insert", "path": ["b", "[0]"], "value": 99}
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
        assertOps("{\"a\": 1, \"b\": 2}", """
        [{"op": "move", "from": ["a"], "to": ["c"]}]""",
            new JsonNodeOp.Remove(path("a")),
            new JsonNodeOp.Insert(path("c"), JsonNode.of("1")));
    }

    @Test
    void move_missingSource_isNoOp() {
        assertOps("{\"a\": 1}", """
        [{"op": "move", "from": ["x"], "to": ["c"]}]""");
    }

    @Test
    void move_arrayElement_toEnd() {
        assertOps("{\"foo\": [10, 20, 30]}", """
        [{"op": "move", "from": ["foo", "[0]"], "to": ["foo", "[-0]"]}]""",
            new JsonNodeOp.Remove(path("foo", "0")),
            new JsonNodeOp.Insert(path("foo", "3"), JsonNode.of("10")));
    }

    @Test
    void move_acrossContainers() {
        assertOps("{\"a\": {\"x\": 1}, \"b\": {}}", """
        [{"op": "move", "from": ["a", "x"], "to": ["b", "y"]}]""",
            new JsonNodeOp.Remove(path("a", "x")),
            new JsonNodeOp.Insert(path("b", "y"), JsonNode.of("1")));
    }

    @Test
    void move_rangeSource_throws() {
        assertThrows(JsonPatchException.class, () -> ops("{\"foo\": [1,2,3]}", """
        [{"op": "move", "from": ["foo", "[0:1]"], "to": ["x"]}]"""));
    }

    @Test
    void move_rangeTarget_throws() {
        assertThrows(JsonPatchException.class, () -> ops("{\"foo\": [1,2,3]}", """
        [{"op": "move", "from": ["foo", "[0]"], "to": ["bar", "[0:1]"]}]"""));
    }

// ===== copy =====

    @Test
    void copy_simpleKey() {
        assertOps("{\"a\": 1, \"b\": 2}", """
        [{"op": "copy", "from": ["a"], "to": ["c"]}]""",
            new JsonNodeOp.Insert(path("c"), JsonNode.of("1")));
    }

    @Test
    void copy_missingSource_isNoOp() {
        assertOps("{\"a\": 1}", """
        [{"op": "copy", "from": ["x"], "to": ["c"]}]""");
    }

    @Test
    void copy_arrayElement() {
        assertOps("{\"foo\": [10, 20]}", """
        [{"op": "copy", "from": ["foo", "[0]"], "to": ["foo", "[-0]"]}]""",
            new JsonNodeOp.Insert(path("foo", "2"), JsonNode.of("10")));
    }

    @Test
    void copy_intoDifferentContainer() {
        assertOps("{\"a\": {\"x\": 1}, \"b\": {}}", """
        [{"op": "copy", "from": ["a", "x"], "to": ["b", "y"]}]""",
            new JsonNodeOp.Insert(path("b", "y"), JsonNode.of("1")));
    }

    @Test
    void copy_rangeSource_throws() {
        assertThrows(JsonPatchException.class, () -> ops("{\"foo\": [1,2,3]}", """
        [{"op": "copy", "from": ["foo", "[0:1]"], "to": ["x"]}]"""));
    }

// ===== swap =====

    @Test
    void swap_simpleKeys() {
        assertOps("{\"a\": 1, \"b\": 2}", """
        [{"op": "swap", "from": ["a"], "to": ["b"]}]""",
            new JsonNodeOp.Replace(path("a"), JsonNode.of("2")),
            new JsonNodeOp.Replace(path("b"), JsonNode.of("1")));
    }

    @Test
    void swap_arrayElements() {
        assertOps("{\"foo\": [10, 20, 30]}", """
        [{"op": "swap", "from": ["foo", "[0]"], "to": ["foo", "[2]"]}]""",
            new JsonNodeOp.Replace(path("foo", "0"), JsonNode.of("30")),
            new JsonNodeOp.Replace(path("foo", "2"), JsonNode.of("10")));
    }

    @Test
    void swap_acrossContainersAndDepths() {
        // Different depth: .a (object) at depth 1, .b.x at depth 2.
        // Two disjoint Replace ops; the values themselves differ in shape.
        assertOps("{\"a\": {\"v\": 1}, \"b\": {\"x\": 42}}", """
        [{"op": "swap", "from": ["a"], "to": ["b", "x"]}]""",
            new JsonNodeOp.Replace(path("a"), JsonNode.of("42")),
            new JsonNodeOp.Replace(path("b", "x"), JsonNode.of("{\"v\": 1}")));
    }

    @Test
    void swap_objectsOfDifferentShapes() {
        assertOps("{\"a\": {\"x\": 1}, \"b\": [1,2,3]}", """
        [{"op": "swap", "from": ["a"], "to": ["b"]}]""",
            new JsonNodeOp.Replace(path("a"), JsonNode.of("[1,2,3]")),
            new JsonNodeOp.Replace(path("b"), JsonNode.of("{\"x\": 1}")));
    }

    @Test
    void swap_missingFrom_isNoOp() {
        assertOps("{\"a\": 1}", """
        [{"op": "swap", "from": ["x"], "to": ["a"]}]""");
    }

    @Test
    void swap_missingTo_isNoOp() {
        assertOps("{\"a\": 1}", """
        [{"op": "swap", "from": ["a"], "to": ["x"]}]""");
    }

    @Test
    void swap_samePath_isNoOp() {
        // Without the from.equals(to) short-circuit this would emit two Replace
        // ops at the same path, which clash. The short-circuit avoids that.
        assertOps("{\"a\": 1}", """
        [{"op": "swap", "from": ["a"], "to": ["a"]}]""");
    }

    @Test
    void swap_rangeSource_throws() {
        assertThrows(JsonPatchException.class, () -> ops("{\"foo\": [1,2,3]}", """
        [{"op": "swap", "from": ["foo", "[0:1]"], "to": ["a"]}]"""));
    }

    @Test
    void swap_rangeTarget_throws() {
        assertThrows(JsonPatchException.class, () -> ops("{\"foo\": [1,2,3]}", """
        [{"op": "swap", "from": ["a"], "to": ["foo", "[0:1]"]}]"""));
    }

    @Test
    void swap_ancestorIsProducedButWillClashDownstream() {
        // patchOps itself does not run the claim check, so this emits two
        // Replace ops that the downstream clash detector will reject.
        // Pinned so the behaviour is deliberate, not incidental.
        assertOps("{\"a\": {\"b\": 1}}", """
        [{"op": "swap", "from": ["a"], "to": ["a", "b"]}]""",
            new JsonNodeOp.Replace(path("a"), JsonNode.of("1")),
            new JsonNodeOp.Replace(path("a", "b"), JsonNode.of("{\"b\": 1}")));
    }

    // ===== test (guard) =====

    @Test
    void test_existingKey_passes_noOps() {
        assertOps("{\"a\": 1}", """
        [{"op": "test", "path": ["a"]}]""");
    }

    @Test
    void test_missingKey_throws() {
        assertThrows(JsonPatchException.class, () -> ops("{}", """
        [{"op": "test", "path": ["a"]}]"""));
    }

    @Test
    void test_nestedKey_passes() {
        assertOps("{\"a\": {\"b\": 1}}", """
        [{"op": "test", "path": ["a", "b"]}]""");
    }

    @Test
    void test_nestedKeyMissingAncestor_throws() {
        assertThrows(JsonPatchException.class, () -> ops("{}", """
        [{"op": "test", "path": ["a", "b"]}]"""));
    }

    @Test
    void test_conditionMatches_passes() {
        assertOps("{\"foo\": [{\"id\":\"x\"},{\"id\":\"y\"}]}", """
        [{"op": "test", "path": ["foo", "[?id=x]"]}]""");
    }

    @Test
    void test_conditionNoMatch_throws() {
        assertThrows(JsonPatchException.class, () -> ops("{\"foo\": [{\"id\":\"y\"}]}", """
        [{"op": "test", "path": ["foo", "[?id=x]"]}]"""));
    }

    @Test
    void test_valueConditionMatches_passes() {
        assertOps("{\"foo\": [1,2,3]}", """
        [{"op": "test", "path": ["foo", "[?=2]"]}]""");
    }

    @Test
    void test_valueConditionNoMatch_throws() {
        assertThrows(JsonPatchException.class, () -> ops("{\"foo\": [1,2,3]}", """
        [{"op": "test", "path": ["foo", "[?=99]"]}]"""));
    }

    @Test
    void test_arrayHasMinimumElements() {
        // Range test: "foo has at least 5 elements"
        assertOps("{\"foo\": [1,2,3,4,5]}", """
        [{"op": "test", "path": ["foo", "[4:4]"]}]""");
        assertThrows(JsonPatchException.class, () -> ops("{\"foo\": [1,2,3]}", """
        [{"op": "test", "path": ["foo", "[4:4]"]}]"""));
    }

    @Test
    void test_rangeWithNoElements_throws() {
        assertThrows(JsonPatchException.class, () -> ops("{\"foo\": []}", """
        [{"op": "test", "path": ["foo", "[0:5]"]}]"""));
    }

// ===== test as a guard around mutations =====

    @Test
    void test_passes_thenMutationsApplied() {
        assertOps("{\"a\": 1, \"b\": 2}", """
        [
          {"op": "test", "path": ["a"]},
          {"op": "remove", "path": ["b"]}
        ]""",
            new JsonNodeOp.Remove(path("b")));
    }

    @Test
    void test_fails_nothingApplied() {
        // Whole patch rejected — the mutation ops already accumulated are lost.
        assertThrows(JsonPatchException.class, () -> ops("{\"a\": 1, \"b\": 2}", """
        [
          {"op": "remove", "path": ["b"]},
          {"op": "test", "path": ["x"]}
        ]"""));
    }

    @Test
    void test_positionDoesNotMatter() {
        // Same patch, test at top vs. bottom — both reject.
        assertThrows(JsonPatchException.class, () -> ops("{\"a\": 1}", """
        [
          {"op": "test", "path": ["x"]},
          {"op": "remove", "path": ["a"]}
        ]"""));
        assertThrows(JsonPatchException.class, () -> ops("{\"a\": 1}", """
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
        String target = """
        {"articles": [{"id": "a1", "status": "draft"}]}""";
        String patch = """
        [
          {"op": "test",   "path": ["articles", "[?status=draft]"]},
          {"op": "replace","path": ["articles", "[?status=draft]", "status"], "value": "published"}
        ]""";
        assertOps(target, patch,
            new JsonNodeOp.Replace(path("articles", "0", "status"), JsonNode.of("\"published\"")));
    }
}