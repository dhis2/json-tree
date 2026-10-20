package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchTargetException;
import org.hisp.dhis.jsontree.internal.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for applying a patch via {@link JsonValue#patch(JsonPatch)}.
 *
 * <p>Compares results by JSON equivalence rather than raw text, since the
 * edit pipeline may leave padding whitespace where a comma was omitted
 * (e.g. inserting the first member into an empty object).
 */
class JsonPatchApplyTest {


    // ===== remove =====

    @Test
    void remove_objectMember_first() {
        assertPatched("""
{"b":2}""", """
{"a":1,"b":2}""", """
            [{"op": "remove", "path": ["a"]}]""");
    }

    @Test
    void remove_objectMember_middle() {
        assertPatched("""
{"a":1,"c":3}""", """
{"a":1,"b":2,"c":3}""", """
            [{"op": "remove", "path": ["b"]}]""");
    }

    @Test
    void remove_objectMember_last() {
        assertPatched("""
{"a":1}""", """
{"a":1,"b":2}""", """
            [{"op": "remove", "path": ["b"]}]""");
    }

    @Test
    void remove_objectMember_only() {
        assertPatched("{}", """
{"a":1}""", """
            [{"op": "remove", "path": ["a"]}]""");
    }

    @Test
    void remove_objectMember_missing_isNoOp() {
        assertPatched("""
{"a":1}""", """
{"a":1}""", """
            [{"op": "remove", "path": ["x"]}]""");
    }

    @Test
    void remove_twoFirstObjectMembers() {
        // Exercises the REMOVE_WITH_COMMA pending-blank flag surviving an
        // intervening zero-copy REMOVE edit.
        assertPatched("""
{"c":3}""", """
{"a":1,"b":2,"c":3}""", """
            [
              {"op": "remove", "path": ["a"]},
              {"op": "remove", "path": ["b"]}
            ]""");
    }

    // ===== insert =====

    @Test
    void insert_objectMember_emptyObject() {
        assertPatched("""
{"a":1}""", "{}", """
            [{"op": "insert", "path": ["a"], "value": 1}]""");
    }

    @Test
    void insert_objectMember_existingObject() {
        assertPatched("""
{"a":1,"b":2}""", """
{"a":1}""", """
            [{"op": "insert", "path": ["b"], "value": 2}]""");
    }

    @Test
    void insert_objectMember_overwritesExisting() {
        assertPatched("""
{"a":2}""", """
{"a":1}""", """
            [{"op": "insert", "path": ["a"], "value": 2}]""");
    }

    @Test
    void insert_twoObjectMembers_bothNew() {
        assertPatched("""
{"a":1,"b":2,"c":3}""", """
{"a":1}""", """
            [
              {"op": "insert", "path": ["b"], "value": 2},
              {"op": "insert", "path": ["c"], "value": 3}
            ]""");
    }

    @Test
    void insert_nestedObjectMember() {
        assertPatched("""
{"a":{"b":1}}""", """
{"a":{}}""", """
            [{"op": "insert", "path": ["a","b"], "value": 1}]""");
    }

    @Test
    void insert_arrayElement_beforeExisting() {
        assertPatched("[1,9,2,3]", "[1,2,3]", """
            [{"op": "insert", "path": [1], "value": 9}]""");
    }

    @Test
    void insert_arrayElement_append() {
        assertPatched("[1,2,3,4]", "[1,2,3]", """
            [{"op": "insert", "path": [3], "value": 4}]""");
    }

    @Test
    void insert_arrayElement_emptyArray() {
        assertPatched("[1]", "[]", """
            [{"op": "insert", "path": [0], "value": 1}]""");
    }

    // ===== replace =====

    @Test
    void replace_objectMember() {
        assertPatched("""
{"a":2}""", """
{"a":1}""", """
            [{"op": "replace", "path": ["a"], "value": 2}]""");
    }

    @Test
    void replace_nestedObjectMember() {
        assertPatched("""
{"a":{"b":2}}""", """
{"a":{"b":1}}""", """
            [{"op": "replace", "path": ["a","b"], "value": 2}]""");
    }

    @Test
    void replace_arrayElement() {
        assertPatched("[1,9,3]", "[1,2,3]", """
            [{"op": "replace", "path": [1], "value": 9}]""");
    }

    @Test
    void replace_objectWithArray() {
        assertPatched("""
{"a":[1,2,3]}""", """
{"a":{"x":1}}""", """
            [{"op": "replace", "path": ["a"], "value": [1,2,3]}]""");
    }

    @Test
    void replace_arrayElementWithObject() {
        assertPatched("""
[1,{"x":1},3]""", "[1,2,3]", """
            [{"op": "replace", "path": [1], "value": {"x":1}}]""");
    }

    // ===== spread =====

    @Test
    void spread_object_intoExistingObject() {
        assertPatched("""
{"a":1,"b":2,"c":3}""", """
{"a":1}""", """
            [{"op": "spread", "path": [], "value": {"b":2,"c":3}}]""");
    }

    @Test
    void spread_object_overwritesExistingKey() {
        assertPatched("""
{"a":9,"b":2}""", """
{"a":1}""", """
            [{"op": "spread", "path": [], "value": {"a":9,"b":2}}]""");
    }

    @Test
    void spread_object_intoEmptyObject() {
        assertPatched("""
{"a":1,"b":2}""", "{}", """
            [{"op": "spread", "path": [], "value": {"a":1,"b":2}}]""");
    }

    @Test
    void spread_array_insertElementsBefore() {
        assertPatched("[1,9,8,2,3]", "[1,2,3]", """
            [{"op": "spread", "path": [1], "value": [9,8]}]""");
    }

    @Test
    void spread_array_appendElements() {
        assertPatched("[1,2,3,9,8]", "[1,2,3]", """
            [{"op": "spread", "path": [3], "value": [9,8]}]""");
    }

    @Test
    void spread_array_emptyValue_isNoOp() {
        assertPatched("[1,2,3]", "[1,2,3]", """
            [{"op": "spread", "path": [1], "value": []}]""");
    }

    // ===== multiple independent ops =====

    @Test
    void multipleOps_replaceAndRemove() {
        assertPatched("""
{"a":10,"c":30}""", """
{"a":1,"b":2,"c":3}""", """
            [
              {"op": "replace", "path": ["a"], "value": 10},
              {"op": "remove",  "path": ["b"]},
              {"op": "replace", "path": ["c"], "value": 30}
            ]""");
    }

    @Test
    void multipleOps_removeFirstThenInsertAnother() {
        // Exercises REMOVE_WITH_COMMA followed by an INSERT_WITH_COMMA on
        // the same parent: blanking the erased head's comma must not
        // interfere with the newly inserted member's comma.
        assertPatched("""
{"b":2,"c":3}""", """
{"a":1,"b":2}""", """
            [
              {"op": "remove", "path": ["a"]},
              {"op": "insert", "path": ["c"], "value": 3}
            ]""");
    }

    @Test
    void multipleOps_differentContainers() {
        assertPatched(
            """
{"a":[1,2,9],"b":{"x":10}}""",
            """
{"a":[1,2,3],"b":{"x":1}}""", """
      [
        {"op": "remove",  "path": ["a", 2]},
        {"op": "insert",  "path": ["a", 3], "value": 9},
        {"op": "replace", "path": ["b", "x"], "value": 10}
      ]""");
    }

    // ===== trivial cases =====

    @Test
    void emptyPatch_returnsTargetUnchanged() {
        assertPatched("""
{"a":1}""", """
{"a":1}""", "[]");
    }

    @Test
    void patchDoesNotMutateOriginal() {
        JsonMixed target = JsonMixed.of("""
{"a":1}""");
        JsonMixed patched = target.patch(JsonMixed.of("""
            [{"op": "replace", "path": ["a"], "value": 2}]""").as(JsonPatch.class));
        assertTrue(JsonMixed.of("""
{"a":1}""").equivalentTo(target),
            "original must be unchanged");
        assertTrue(JsonMixed.of("""
{"a":2}""").equivalentTo(patched));
    }

    private static JsonMixed patch(@Language("json") String targetJson, @Language("json") String patchJson) {
        return JsonMixed.of(targetJson).patch(JsonMixed.of(patchJson).as(JsonPatch.class));
    }

    private static void assertPatched(@Language("json") String expected, @Language("json") String targetJson, @Language("json") String patchJson) {
        JsonMixed actual = patch(targetJson, patchJson);
        assertTrue(JsonMixed.of(expected).equivalentTo(actual),
            () -> "expected: " + expected + "\n  actual: " + actual);
    }
}