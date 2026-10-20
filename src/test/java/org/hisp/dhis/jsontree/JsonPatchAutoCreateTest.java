package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchEditException;
import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchTargetException;
import org.hisp.dhis.jsontree.internal.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for auto-creating object parents during {@code insert} and object-valued
 * {@code spread}.
 *
 * <p>When the parent path of an insert does not exist, the deepest existing
 * ancestor (which must be an object) gets a chain of new nested objects created
 * up to the target position, then the value is inserted, then the newly opened
 * objects are closed.
 *
 * <p>Only object parents can be auto-created. Array parents must be created
 * explicitly — the resolver rejects array-index segments whose parent doesn't
 * exist before the edit phase is reached.
 *
 * <p><b>Known limitation:</b> object member names that happen to be
 * integer-shaped strings (e.g. {@code "123"}) cannot be auto-created, because
 * {@link JsonPath} segments lose the distinction between "key that looks like a
 * number" and "array index". Clients needing such names must first create the
 * parent object explicitly with {@code insert}.
 */
class JsonPatchAutoCreateTest {

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

    // ===== basic auto-create (insert) =====

    @Test
    void insert_createsOneLevel() {
        assertPatched("""
            {"a":{"b":1}}""", "{}", """
            [{"op": "insert", "path": ["a","b"], "value": 1}]""");
    }

    @Test
    void insert_createsTwoLevels() {
        assertPatched("""
            {"a":{"b":{"c":1}}}""", "{}", """
            [{"op": "insert", "path": ["a","b","c"], "value": 1}]""");
    }

    @Test
    void insert_createsThreeLevels() {
        assertPatched("""
            {"a":{"b":{"c":{"d":1}}}}""", "{}", """
            [{"op": "insert", "path": ["a","b","c","d"], "value": 1}]""");
    }

    @Test
    void insert_createsWithExistingSibling() {
        assertPatched("""
            {"x":1,"a":{"b":2}}""", """
            {"x":1}""", """
            [{"op": "insert", "path": ["a","b"], "value": 2}]""");
    }

    @Test
    void insert_createsBelowExistingObject() {
        // Existing partial chain: "a" exists, "b" and "c" don't
        assertPatched("""
            {"a":{"x":1,"b":{"c":2}}}""", """
            {"a":{"x":1}}""", """
            [{"op": "insert", "path": ["a","b","c"], "value": 2}]""");
    }

    @Test
    void insert_createsBelowDeepExistingObject() {
        assertPatched("""
            {"a":{"b":{"x":1,"c":{"d":2}}}}""","""
            {"a":{"b":{"x":1}}}""", """
            [{"op": "insert", "path": ["a","b","c","d"], "value": 2}]""");
    }

    @Test
    void insert_createsIntoEmptyParent_normalPath() {
        // Parent exists (empty object) — no auto-create, but result same shape
        assertPatched("""
            {"a":{"b":1}}""", """
            {"a":{}}""", """
            [{"op": "insert", "path": ["a","b"], "value": 1}]""");
    }

    // ===== value types =====

    @Test
    void insert_createsWithArrayValue() {
        assertPatched("""
            {"a":{"b":[1,2,3]}}""", "{}", """
            [{"op": "insert", "path": ["a","b"], "value": [1,2,3]}]""");
    }

    @Test
    void insert_createsWithObjectValue() {
        assertPatched("""
            {"a":{"b":{"x":1}}}""", "{}", """
            [{"op": "insert", "path": ["a","b"], "value": {"x":1}}]""");
    }

    @Test
    void insert_createsWithNullValue() {
        assertPatched("""
            {"a":{"b":null}}""", "{}", """
            [{"op": "insert", "path": ["a","b"], "value": null}]""");
    }

    @Test
    void insert_createsWithStringValue() {
        assertPatched("""
            {"a":{"b":"hello"}}""", "{}", """
            [{"op": "insert", "path": ["a","b"], "value": "hello"}]""");
    }

    // ===== spread into missing parent =====

    @Test
    void spread_createsSingleLevel() {
        assertPatched("""
            {"a":{"x":1,"y":2}}""", "{}", """
            [{"op": "spread", "path": ["a"], "value": {"x":1,"y":2}}]""");
    }

    @Test
    void spread_createsTwoLevels() {
        assertPatched("""
            {"a":{"b":{"x":1,"y":2}}}""", "{}", """
            [{"op": "spread", "path": ["a","b"], "value": {"x":1,"y":2}}]""");
    }

    @Test
    void spread_emptyObjectCreatesEmptyParent() {
        // Empty spread into missing path still creates the empty object
        assertPatched("""
            {"a":{}}""", "{}", """
            [{"op": "spread", "path": ["a"], "value": {}}]""");
    }

    // ===== multiple auto-creates =====

    @Test
    void multipleCreates_disjointChains() {
        // Two independent auto-creates from the same empty root
        assertPatched("""
            {"a":{"b":1},"c":{"d":2}}""", "{}", """
            [
              {"op": "insert", "path": ["a","b"], "value": 1},
              {"op": "insert", "path": ["c","d"], "value": 2}
            ]""");
    }

    @Test
    void multipleCreates_orderIndependent() {
        // Same as above, reversed — equivalentTo ignores member order
        assertPatched("""
            {"a":{"b":1},"c":{"d":2}}""", "{}", """
            [
              {"op": "insert", "path": ["c","d"], "value": 2},
              {"op": "insert", "path": ["a","b"], "value": 1}
            ]""");
    }

    // ===== combined with other ops =====

    @Test
    void create_withReplaceElsewhere() {
        assertPatched("""
            {"x":9,"a":{"b":1}}""", """
            {"x":0}""", """
            [
              {"op": "replace", "path": ["x"],       "value": 9},
              {"op": "insert",  "path": ["a","b"],   "value": 1}
            ]""");
    }

    @Test
    void create_withRemoveElsewhere() {
        assertPatched("""
            {"a":{"b":1}}""", """
            {"c":2}""", """
            [
              {"op": "remove", "path": ["c"]},
              {"op": "insert", "path": ["a","b"], "value": 1}
            ]""");
    }

    // ===== error cases =====

    @Test
    void create_booleanAncestor_throws() {
    assertThrowsExactly(
        JsonPatchTargetException.class,
        () ->
            patch("""
            {"a":true}""","""
            [{"op": "insert", "path": ["a","b"], "value": 1}]"""));
    }

    @Test
    void create_scalarAncestor_throws() {
        // Cannot insert into a number — auto-create doesn't override this
        assertThrowsExactly(JsonPatchTargetException.class, () -> patch("""
            {"a":5}""", """
            [{"op": "insert", "path": ["a","b"], "value": 1}]"""));
    }

    @Test
    void create_nullAncestor_throws() {
        assertThrowsExactly(JsonPatchTargetException.class, () -> patch("""
            {"a":null}""", """
            [{"op": "insert", "path": ["a","b"], "value": 1}]"""));
    }

    @Test
    void create_arrayAncestor_nonIntegerName_throws() {
        // High level: fails at declaration time (patchOps) with a target exception
        assertThrowsExactly(JsonPatchTargetException.class, () -> patch("""
            {"a":[1,2]}""", """
            [{"op": "insert", "path": ["a","b"], "value": 1}]"""));
    }

    @Test
    void lowLevel_insert_nonIntegerSegmentOnArray_throwsEditException() {
        // Low level: bypass the high-level validation, hit the edit-phase check
        JsonNode target = JsonNode.of("""
                    {"a":[1,2]}""");
        JsonPath path = new JsonPath(new JsonPath(null, Text.of("a")), Text.of("b"));
        assertThrowsExactly(JsonPatchEditException.class, () ->
            JsonNodeOp.patch(target, List.of(new JsonNodeOp.Insert(path, JsonNode.of("1")))));
    }

    @Test
    void create_arraySegmentInChain_throwsAtResolve() {
        // Numeric segment in the auto-create chain: resolver rejects it
        // (array parent would be required, and auto-create doesn't support arrays)
        assertThrowsExactly(JsonPatchTargetException.class, () -> patch("{}", """
            [{"op": "insert", "path": ["a", 0, "b"], "value": 1}]"""));
    }

    // ===== round-trip: created values are readable =====

    @Test
    void createdValueIsReadable() {
        JsonMixed patched = patch("{}", """
            [{"op": "insert", "path": ["a","b","c"], "value": 42}]""");
        JsonMixed c = patched.get("a").get("b").get("c");
        assertTrue(c.isNumber() && c.intValue() == 42);
    }

    @Test
    void createdSpreadObjectIsReadable() {
        JsonMixed patched = patch("{}", """
            [{"op": "spread", "path": ["a","b"], "value": {"x":1,"y":2}}]""");
        JsonMixed inner = patched.get("a").get("b");
        assertTrue(inner.isObject());
        assertEquals(1, inner.get("x").intValue());
        assertEquals(2, inner.get("y").intValue());
    }

    // ==== writable vs non-writable targets =====

    @Test
    void spread_scalarParent_throws() {
        assertThrowsExactly(JsonPatchTargetException.class, () -> patch("""
            {"a":5}""", """
            [{"op": "spread", "path": ["a","b"], "value": {"x":1}}]"""));
    }

    @Test
    void replace_scalarParent_throws() {
        assertThrowsExactly(JsonPatchTargetException.class, () -> patch("""
            {"a":5}""", """
            [{"op": "replace", "path": ["a","b"], "value": 1}]"""));
    }

    @Test
    void move_toScalarParent_throws() {
        // Source exists, so the whole move is not a no-op; destination under
        // scalar parent fails at declaration
        assertThrowsExactly(JsonPatchTargetException.class, () -> patch("""
            {"x":1,"a":5}""", """
            [{"op": "move", "from": ["x"], "to": ["a","b"]}]"""));
    }

    @Test
    void copy_toScalarParent_throws() {
        assertThrowsExactly(JsonPatchTargetException.class, () -> patch("""
            {"x":1,"a":5}""", """
            [{"op": "copy", "from": ["x"], "to": ["a","b"]}]"""));
    }

    @Test
    void remove_scalarParent_isNoOp() {
        // remove is forgiving: path can't exist → no-op
        assertPatched("""
            {"a":5}""", """
            {"a":5}""", """
        [{"op": "remove", "path": ["a","b"]}]""");
    }

    @Test
    void test_scalarParent_fails() {
        // test on a path that can't exist → test failure, not declaration error
        assertThrowsExactly(JsonPatchException.JsonPatchTestException.class, () ->
            patch("""
                {"a":5}""", """
                [{"op": "test", "path": ["a","b"]}]"""));
    }

    @Test
    void pointer_pathInto_scalarParent_throws() {
    // JSON Pointer form exercises the other resolve branch
    assertThrowsExactly(
        JsonPatchTargetException.class,
        () ->
            patch("""
            {"a":5}""", """
            [{"op": "add", "path": "/a/b", "value": 1}]"""));
    }

}