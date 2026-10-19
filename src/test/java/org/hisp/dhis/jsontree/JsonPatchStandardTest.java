package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchEditException;
import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchTestException;
import org.hisp.dhis.jsontree.internal.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that exercise the patch API using only RFC 6902 conventions: standard
 * op names ({@code add}, {@code remove}, {@code replace}, {@code move},
 * {@code copy}, {@code test}) and JSON Pointer string paths.
 *
 * <p>Results are compared via {@link JsonValue#equivalentTo(JsonValue)} so
 * that the padding whitespace the edit pipeline leaves where a comma was
 * omitted (e.g. {@code { "a":1}}) is not asserted against.
 *
 * <h3>Remove</h3>
 * <p>Known divergence from RFC 6902: {@code remove} on a missing path is a
 * no-op here (RFC 6902 requires the target to exist). This is intentional and
 * consistent with the "forgiving" semantics elsewhere in the API.
 *
 * <h3>Move</h3>
 * <p>In this implementation, {@code move} is {@code Remove(from)} followed by
 * {@code Insert(to, value)}, with both paths resolved against the original
 * target. This differs from RFC 6902 when moving an element rightward within
 * an array: RFC interprets {@code to} as a position in the shortened array
 * (after {@code from} has been removed), while we interpret it as a position
 * in the original array.
 *
 * <p>Example — on {@code [1,2,3]}, {@code move /1 /2} is a no-op here
 * (insert before the original element at index 2, which is {@code 3}) but
 * produces {@code [1,3,2]} in RFC 6902. To achieve the RFC result, use
 * {@code move /1 /3} (past the end) or {@code move /1 /-0} (append).
 *
 * <p>This divergence is deliberate: it preserves the design's core
 * invariant that every index in every operation refers to the original
 * target, regardless of operation order.
 * <pre>
 *   relationship           our result                         RFC result
 *   -------------------------------------------------------------------------------------------
 *   from > to              matches RFC                        matches RFC
 *   from == to             no-op                              no-op
 *   from < to, to == size  matches RFC (append)               matches RFC (append)
 *   from < to, to < size   insert before original element to  insert after original element to
 * </pre>
 */
class JsonPatchStandardTest {

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

    // ===== add (→ internal insert) =====

    @Test
    void add_objectMember_replaces() {
        // RFC 6902: add on an existing member replaces its value
        assertPatched("""
            {"a":2}""", """
            {"a":1}""", """
            [{"op": "add", "path": "/a", "value": 2}]""");
    }

    @Test
    void add_objectMember_new() {
        assertPatched("""
            {"a":1,"b":2}""", """
            {"a":1}""", """
            [{"op": "add", "path": "/b", "value": 2}]""");
    }

    @Test
    void add_nested_new() {
        assertPatched("""
            {"a":{"b":1}}""", """
            {"a":{}}""", """
            [{"op": "add", "path": "/a/b", "value": 1}]""");
    }

    @Test
    void add_arrayElement_prepend() {
        assertPatched("[0,1,2]", "[1,2]", """
            [{"op": "add", "path": "/0", "value": 0}]""");
    }

    @Test
    void add_arrayElement_middle() {
        assertPatched("[1,9,2,3]", "[1,2,3]", """
            [{"op": "add", "path": "/1", "value": 9}]""");
    }

    @Test
    void add_arrayElement_appendByInteger() {
        assertPatched("[1,2,3]", "[1,2]", """
            [{"op": "add", "path": "/2", "value": 3}]""");
    }

    // ===== remove =====

    @Test
    void remove_objectMember() {
        assertPatched("""
            {"b":2}""", """
            {"a":1,"b":2}""", """
            [{"op": "remove", "path": "/a"}]""");
    }

    @Test
    void remove_arrayElement() {
        assertPatched("[1,3]", "[1,2,3]", """
            [{"op": "remove", "path": "/1"}]""");
    }

    @Test
    void remove_missing_isNoOp() {
        // Diverges from RFC 6902 (which requires existence); our design is
        // forgiving and treats remove on a missing path as a no-op.
        assertPatched("""
            {"a":1}""", """
            {"a":1}""", """
            [{"op": "remove", "path": "/x"}]""");
    }

    // ===== replace =====

    @Test
    void replace_objectMember() {
        assertPatched("""
            {"a":2}""", """
            {"a":1}""", """
            [{"op": "replace", "path": "/a", "value": 2}]""");
    }

    @Test
    void replace_arrayElement() {
        assertPatched("[1,9,3]", "[1,2,3]", """
            [{"op": "replace", "path": "/1", "value": 9}]""");
    }

    @Test
    void replace_missing_throws() {
        assertThrowsExactly(JsonPatchEditException.class, () -> patch("""
            {"a":1}""", """
            [{"op": "replace", "path": "/x", "value": 2}]"""));
    }

    // ===== move =====

    @Test
    void move_rfcStyle_usesPathAsDestination() {
        // RFC 6902 uses `path` as the destination for move
        assertPatched("""
            {"a":1,"c":2}""", """
            {"a":1,"b":2}""", """
            [{"op": "move", "from": "/b", "path": "/c"}]""");
    }

    @Test
    void move_libStyle_toAlias() {
        // Our extension: `to` as an alias for the destination
        assertPatched("""
            {"a":1,"c":2}""", """
            {"a":1,"b":2}""", """
            [{"op": "move", "from": "/b", "to": "/c"}]""");
    }

    @Test
    void move_arrayElement_right_beforeOriginalElement() {
        // to=2 means "insert before original element 2 (value 3)".
        // Since the source (index 1, value 2) is removed first, this puts
        // 2 back where it started: no visible change.
        // RFC would give [1,3,2] because to=2 refers to the shortened array.
        assertPatched("[1,2,3]", "[1,2,3]", """
          [{"op": "move", "from": "/1", "path": "/2"}]""");
    }

    @Test
    void move_arrayElement_right_rfcEquivalenceViaToPlusOne() {
        // For RFC move /1 → /2, use to=3 (past the array, append slot):
        // remove element 1, insert before original element 3 (which doesn't
        // exist, so append).
        assertPatched("[1,3,2]", "[1,2,3]", """
          [{"op": "move", "from": "/1", "path": "/3"}]""");
    }

    @Test
    void move_arrayElement_right_toEnd() {
        // to = size → append slot, no adjustment needed. Same as RFC.
        assertPatched("[1,3,2]", "[1,2,3]", """
          [{"op": "move", "from": "/1", "path": "/-"}]""");
        assertPatched("[2,3,1]", "[1,2,3]", """
          [{"op": "move", "from": "/0", "path": "/-"}]""");
    }

    @Test
    void move_arrayElement_left() {
        // from > to: matches RFC exactly
        assertPatched("[2,1,3]", "[1,2,3]", """
          [{"op": "move", "from": "/1", "path": "/0"}]""");
        assertPatched("[3,1,2]", "[1,2,3]", """
          [{"op": "move", "from": "/2", "path": "/0"}]""");
    }

    @Test
    void move_arrayElement_sameIndex() {
        assertPatched("[1,2,3]", "[1,2,3]", """
          [{"op": "move", "from": "/1", "path": "/1"}]""");
    }

    @Test
    void move_toSelf_isNoOp() {
        assertPatched("""
            {"a":1}""", """
            {"a":1}""", """
            [{"op": "move", "from": "/a", "path": "/a"}]""");
    }

    @Test
    void move_missingSource_isNoOp() {
        assertPatched("""
            {"a":1}""", """
            {"a":1}""", """
            [{"op": "move", "from": "/x", "path": "/y"}]""");
    }

    // ===== copy =====

    @Test
    void copy_rfcStyle() {
        assertPatched("""
            {"a":1,"b":1}""", """
            {"a":1}""", """
            [{"op": "copy", "from": "/a", "path": "/b"}]""");
    }

    @Test
    void copy_libStyle_toAlias() {
        assertPatched("""
            {"a":1,"b":1}""", """
            {"a":1}""", """
            [{"op": "copy", "from": "/a", "to": "/b"}]""");
    }

    @Test
    void copy_missingSource_isNoOp() {
        assertPatched("""
            {"a":1}""", """
            {"a":1}""", """
            [{"op": "copy", "from": "/x", "path": "/y"}]""");
    }

    @Test
    void copy_readsOriginalNotIntermediate() {
        // Copy reads against the original: /b gets 1 (the original /a),
        // even though /a is being replaced by the same patch
        assertPatched("""
            {"a":2,"b":1}""", """
            {"a":1}""", """
            [
              {"op": "copy",    "from": "/a", "path": "/b"},
              {"op": "replace", "path": "/a", "value": 2}
            ]""");
    }

    // ===== test =====

    @Test
    void test_existenceOnly_passes() {
        assertPatched("""
            {"a":1}""", """
            {"a":1}""", """
            [{"op": "test", "path": "/a"}]""");
    }

    @Test
    void test_missing_throws() {
        assertThrowsExactly(JsonPatchTestException.class, () -> patch("""
            {"a":1}""", """
            [{"op": "test", "path": "/x"}]"""));
    }

    @Test
    void test_valueMatch_passes() {
        assertPatched("""
            {"a":1}""", """
            {"a":1}""", """
            [{"op": "test", "path": "/a", "value": 1}]""");
    }

    @Test
    void test_valueMismatch_throws() {
        assertThrowsExactly(JsonPatchTestException.class, () -> patch("""
            {"a":1}""", """
            [{"op": "test", "path": "/a", "value": 2}]"""));
    }

    @Test
    void test_valueOnObject_passes() {
        assertPatched("""
            {"a":{"x":1,"y":2}}""", """
            {"a":{"x":1,"y":2}}""", """
            [{"op": "test", "path": "/a", "value": {"x":1,"y":2}}]""");
    }

    @Test
    void test_valueOnObject_mismatchThrows() {
        assertThrowsExactly(JsonPatchTestException.class, () -> patch("""
            {"a":{"x":1,"y":2}}""", """
            [{"op": "test", "path": "/a", "value": {"x":1,"y":3}}]"""));
    }

    // ===== JSON Pointer escapes =====

    @Test
    void pointer_escapedSlash() {
        // Key "a/b" is encoded as "/a~1b"
        assertPatched("""
            {"a/b":2}""", """
            {"a/b":1}""", """
            [{"op": "replace", "path": "/a~1b", "value": 2}]""");
    }

    @Test
    void pointer_escapedTilde() {
        // Key "a~b" is encoded as "/a~0b"
        assertPatched("""
            {"a~b":2}""", """
            {"a~b":1}""", """
            [{"op": "replace", "path": "/a~0b", "value": 2}]""");
    }

    @Test
    void pointer_multipleEscapes() {
        // Key "a/~b" is encoded as "/a~1~0b"
        assertPatched("""
            {"a/~b":2}""", """
            {"a/~b":1}""", """
            [{"op": "replace", "path": "/a~1~0b", "value": 2}]""");
    }

    @Test
    void pointer_numericKeyOnObject() {
        // "/5" is a key "5" when the parent is an object, not an array index
        assertPatched("""
            {"5":9}""", """
            {"5":1}""", """
            [{"op": "replace", "path": "/5", "value": 9}]""");
    }

    // ===== Root pointer =====

    @Test
    void pointer_root_replace() {
        assertPatched("""
            {"b":2}""", """
            {"a":1}""", """
            [{"op": "replace", "path": "", "value": {"b":2}}]""");
    }

    @Test
    void pointer_root_test_passes() {
        assertPatched("""
            {"a":1}""", """
            {"a":1}""", """
            [{"op": "test", "path": "", "value": {"a":1}}]""");
    }

    @Test
    void pointer_root_test_mismatch_throws() {
        assertThrowsExactly(JsonPatchTestException.class, () -> patch("""
            {"a":1}""", """
            [{"op": "test", "path": "", "value": {"a":2}}]"""));
    }

    // ===== combined standard ops =====

    @Test
    void combined_replaceAndRemove() {
        assertPatched("""
            {"a":10,"c":30}""", """
            {"a":1,"b":2,"c":3}""", """
            [
              {"op": "replace", "path": "/a", "value": 10},
              {"op": "remove",  "path": "/b"},
              {"op": "replace", "path": "/c", "value": 30}
            ]""");
    }

    @Test
    void combined_testThenAdd() {
        // Test fires before mutations are applied; both must succeed for
        // the patch to produce a result
        assertPatched("""
            {"a":1,"b":2}""", """
            {"a":1}""", """
            [
              {"op": "test", "path": "/a", "value": 1},
              {"op": "add",  "path": "/b", "value": 2}
            ]""");
    }

    @Test
    void combined_testFails_nothingApplied() {
        assertThrowsExactly(JsonPatchTestException.class, () -> patch("""
            {"a":1}""", """
            [
              {"op": "test", "path": "/a", "value": 99},
              {"op": "add",  "path": "/b", "value": 2}
            ]"""));
    }

    // ===== known gap: pointer "-" for append =====

    @Test
    void pointer_dash_appendToArray() {
        // RFC 6901/6902: "-" as the last segment means "append".
        // This exercises JsonPointer.decode() → the "-" must resolve to
        // a numeric tail position when the parent is an array.
        assertPatched("[1,2,3]", "[1,2]", """
            [{"op": "add", "path": "/-", "value": 3}]""");
    }

    @Test
    void pointer_dash_appendNestedArray() {
        assertPatched("""
            {"arr":[1,2,3]}""", """
            {"arr":[1,2]}""", """
            [{"op": "add", "path": "/arr/-", "value": 3}]""");
    }
}