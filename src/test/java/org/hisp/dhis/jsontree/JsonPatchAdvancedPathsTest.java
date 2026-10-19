package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JsonValue#patch(JsonPatch)} using path expressions that are
 * not expressible as JSON Pointers: negative indexes, ranges, enumerations, and
 * conditions (object, value, shorthand).
 *
 * <p>Only {@code remove}, {@code replace}, and {@code test} accept these path
 * forms. Single-target ops ({@code insert}, {@code spread}, {@code move},
 * {@code copy}, {@code swap}) reject them at declaration time — the last two
 * tests in the "dispatch" section pin that.
 */
class JsonPatchAdvancedPathsTest {

    private static JsonMixed patch(@Language("json") String target, @Language("json") String patch) {
        return JsonMixed.of(target).patch(JsonMixed.of(patch).as(JsonPatch.class));
    }

    private static void assertPatched(@Language("json") String expected,
                                      @Language("json") String target,
                                      @Language("json") String patch) {
        JsonMixed actual = patch(target, patch);
        assertTrue(JsonMixed.of(expected).equivalentTo(actual),
            () -> "expected: " + expected + "\n  actual: " + actual);
    }

    // ===== Negative indexes =====

    @Test
    void negativeIndex_replaceLast() {
        assertPatched("""
            {"arr":[1,2,9]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", -1], "value": 9}]""");
    }

    @Test
    void negativeIndex_removeLast() {
        assertPatched("""
            {"arr":[1,2]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "remove", "path": ["arr", -1]}]""");
    }

    // ===== Ranges =====

    @Test
    void range_removeMiddle() {
        assertPatched("""
            {"arr":[1,4,5]}""", """
            {"arr":[1,2,3,4,5]}""", """
            [{"op": "remove", "path": ["arr", [[1,2]]]}]""");
    }

    @Test
    void range_removeAll() {
        assertPatched("""
            {"arr":[]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "remove", "path": ["arr", [[0,2]]]}]""");
    }

    @Test
    void range_replace_sameLength() {
        assertPatched("""
            {"arr":[1,20,30,4,5]}""", """
            {"arr":[1,2,3,4,5]}""", """
            [{"op": "replace", "path": ["arr", [[1,2]]], "value": [20,30]}]""");
    }

    @Test
    void range_replace_shorter() {
        assertPatched("""
            {"arr":[1,20,4,5]}""", """
            {"arr":[1,2,3,4,5]}""", """
            [{"op": "replace", "path": ["arr", [[1,2]]], "value": [20]}]""");
    }

    @Test
    void range_replace_longer() {
        assertPatched("""
            {"arr":[1,20,30,40,4,5]}""", """
            {"arr":[1,2,3,4,5]}""", """
            [{"op": "replace", "path": ["arr", [[1,2]]], "value": [20,30,40]}]""");
    }

    @Test
    void range_replace_negativeBounds() {
        assertPatched("""
            {"arr":[1,2,20,30,40]}""", """
            {"arr":[1,2,3,4,5]}""", """
            [{"op": "replace", "path": ["arr", [[-3,-1]]], "value": [20,30,40]}]""");
    }

    @Test
    void range_noOverlap_isNoOp() {
        assertPatched("""
            {"arr":[1,2,3]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "remove", "path": ["arr", [[5,10]]]}]""");
    }

    // ===== Enumerations =====

    @Test
    void enumeration_remove() {
        assertPatched("""
            {"arr":[2,4,5]}""", """
            {"arr":[1,2,3,4,5]}""", """
            [{"op": "remove", "path": ["arr", [0,2]]}]""");
    }

    @Test
    void enumeration_replace() {
        // Array value: aligned by position with the resolved indexes
        assertPatched("""
            {"arr":[9,2,8,4]}""", """
            {"arr":[1,2,3,4]}""", """
            [{"op": "replace", "path": ["arr", [0,2]], "value": [9,8]}]""");
    }

    @Test
    void enumeration_single_remove() {
        assertPatched("""
            {"arr":[1,2]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "remove", "path": ["arr", [2]]}]""");
    }

    @Test
    void enumeration_single_replace() {
        assertPatched("""
            {"arr":[1,2,9]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", [2]], "value": 9}]""");
    }

    // ===== Object conditions =====

    @Test
    void condition_object_replace() {
        assertPatched("""
            {"arr":[{"id":"a"},{"id":"b","v":9}]}""",
            """
            {"arr":[{"id":"a"},{"id":"b","v":2}]}""", """
            [{"op": "replace", "path": ["arr", [{"id":"b"}], "v"], "value": 9}]""");
    }

    @Test
    void condition_object_remove() {
        assertPatched("""
            {"arr":[{"id":"a"},{"id":"c"}]}""",
            """
            {"arr":[{"id":"a"},{"id":"b"},{"id":"c"}]}""", """
            [{"op": "remove", "path": ["arr", [{"id":"b"}]]}]""");
    }

    @Test
    void condition_object_multiProperty() {
        // Both `id` and `s` must match — `{id:b, s:y}` is skipped, `{id:b, s:x}`
        // is matched even though it has additional members (v).
        assertPatched("""
            {"arr":[{"id":"a"},{"id":"b","s":"y"},{"id":"b","s":"x","v":9}]}""",
            """
            {"arr":[{"id":"a"},{"id":"b","s":"y"},{"id":"b","s":"x","v":1}]}""", """
            [{"op": "replace", "path": ["arr", [{"id":"b","s":"x"}], "v"], "value": 9}]""");
    }

    @Test
    void condition_object_noMatch_isNoOp() {
        assertPatched("""
            {"arr":[{"id":"a"}]}""", """
            {"arr":[{"id":"a"}]}""", """
            [{"op": "remove", "path": ["arr", [{"id":"x"}]]}]""");
    }

    // ===== Value conditions =====

    @Test
    void condition_value_replace() {
        assertPatched("""
            {"arr":[1,2,9]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", ["=", 3]], "value": 9}]""");
    }

    @Test
    void condition_value_noMatch_isNoOp() {
        assertPatched("""
            {"arr":[1,2,3]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "remove", "path": ["arr", ["=", 99]]}]""");
    }

    // ===== Shorthand value conditions =====

    @Test
    void condition_shorthand_true() {
        assertPatched("""
            {"arr":[9,false]}""", """
            {"arr":[true,false]}""", """
            [{"op": "replace", "path": ["arr", [true]], "value": 9}]""");
    }

    @Test
    void condition_shorthand_string() {
        assertPatched("""
            {"arr":[9,"b"]}""", """
            {"arr":["a","b"]}""", """
            [{"op": "replace", "path": ["arr", ["a"]], "value": 9}]""");
    }

    // ===== Conditions above tail =====

    @Test
    void condition_thenKey_replace() {
        assertPatched("""
            {"arr":[{"id":"a","v":1},{"id":"b","v":9}]}""",
            """
            {"arr":[{"id":"a","v":1},{"id":"b","v":2}]}""", """
            [{"op": "replace", "path": ["arr", [{"id":"b"}], "v"], "value": 9}]""");
    }

    @Test
    void condition_thenIndex_replace() {
        assertPatched(
            """
            {"arr":[{"id":"a","items":[1]},{"id":"b","items":[9]}]}""",
            """
            {"arr":[{"id":"a","items":[1]},{"id":"b","items":[2]}]}""", """
            [{"op": "replace", "path": ["arr", [{"id":"b"}], "items", 0], "value": 9}]""");
    }

    // ===== test with advanced paths =====

    @Test
    void test_objectCondition_passes() {
        assertPatched("""
            {"arr":[{"id":"a"}]}""", """
            {"arr":[{"id":"a"}]}""", """
            [{"op": "test", "path": ["arr", [{"id":"a"}]]}]""");
    }

    @Test
    void test_objectCondition_fails() {
        assertThrows(JsonPatchException.class, () -> patch("""
            {"arr":[{"id":"a"}]}""", """
            [{"op": "test", "path": ["arr", [{"id":"x"}]]}]"""));
    }

    @Test
    void test_rangeHasEnoughElements_passes() {
        // Single-element range: "arr has at least 5 elements"
        assertPatched("""
            {"arr":[1,2,3,4,5]}""", """
            {"arr":[1,2,3,4,5]}""", """
            [{"op": "test", "path": ["arr", [[4,4]]]}]""");
    }

    @Test
    void test_rangeTooShort_fails() {
        assertThrows(JsonPatchException.class, () -> patch("""
            {"arr":[1,2,3]}""", """
            [{"op": "test", "path": ["arr", [[4,4]]]}]"""));
    }

    @Test
    void test_valueWithCondition_passes() {
        assertPatched("""
            {"arr":[{"id":"a","v":1}]}""", """
            {"arr":[{"id":"a","v":1}]}""", """
            [{"op": "test", "path": ["arr", [{"id":"a"}]], "value": {"id":"a","v":1}}]""");
    }

    @Test
    void test_valueWithMultiTargetRange_throws() {
        // test-with-value on a range is a declaration error
        assertThrows(JsonPatchException.class, () -> patch("""
            {"arr":[1,2,3]}""", """
            [{"op": "test", "path": ["arr", [[0,2]]], "value": 1}]"""));
    }

    // ===== single-target ops reject multi-target paths =====

    @Test
    void insert_multiTargetPath_throws() {
        assertThrows(JsonPatchException.class, () -> patch("""
            {"arr":[1,2,3]}""", """
            [{"op": "insert", "path": ["arr", [[1,2]]], "value": 9}]"""));
    }

    @Test
    void spread_multiTargetPath_throws() {
        assertThrows(JsonPatchException.class, () -> patch("""
            {"arr":[1,2,3]}""", """
            [{"op": "spread", "path": ["arr", [0,2]], "value": [9]}]"""));
    }

    // ===== combined: disjoint range and condition =====

    @Test
    void combined_rangeRemoveAndConditionReplace() {
        assertPatched(
            """
            {"items":[{"id":"a","v":1},{"id":"b","v":9},{"id":"d","v":4}]}""",
            """
            {"items":[{"id":"a","v":1},{"id":"b","v":2},{"id":"c","v":3},{"id":"d","v":4}]}""", """
            [
              {"op": "remove",  "path": ["items", [[2,2]]]},
              {"op": "replace", "path": ["items", [{"id":"b"}], "v"], "value": 9}
            ]""");
    }
}