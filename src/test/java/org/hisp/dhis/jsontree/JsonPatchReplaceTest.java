package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchDeclarationException;
import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchTargetException;
import org.hisp.dhis.jsontree.internal.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests pinning the value-shape semantics for multi-target {@code replace}:
 *
 * <ul>
 *   <li><b>Scalar value</b> — applied to every resolved target (broadcast).</li>
 *   <li><b>Array value, same length</b> — aligned position-by-position.</li>
 *   <li><b>Array value, shorter</b> — aligned; excess targets removed (collapse).</li>
 *   <li><b>Array value, longer (range only)</b> — aligned; excess values spread
 *       past the last target.</li>
 * </ul>
 *
 * <p>The "array-of-one" form is the uniform explicit encoding for "collapse
 * several targets to one" — available for both ranges and enumerations, and
 * independent of the scalar broadcast semantics.
 *
 * <p>Single-index replace is unaffected by these rules: the value is inserted
 * verbatim, so a scalar replaces the element and an array value becomes the
 * new element.
 */
class JsonPatchReplaceTest {

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

    // ===== Range + scalar: broadcast =====

    @Test
    void range_scalar_broadcasts() {
        assertPatched("""
            {"arr":[1,99,99,99,5]}""", """
            {"arr":[1,2,3,4,5]}""", """
            [{"op": "replace", "path": ["arr", [[1,3]]], "value": 99}]""");
    }

    @Test
    void range_scalar_broadcastsWholeArray() {
        assertPatched("""
            {"arr":[true,true,true]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", [[0,2]]], "value": true}]""");
    }

    @Test
    void range_scalar_singleElementRange() {
        // Single-element range: broadcast == single replace, no visible difference
        assertPatched("""
            {"arr":[1,99,3]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", [[1,1]]], "value": 99}]""");
    }

    // ===== Enumeration + scalar: broadcast =====

    @Test
    void enumeration_scalar_broadcasts() {
        assertPatched("""
            {"arr":[99,2,99,4]}""", """
            {"arr":[1,2,3,4]}""", """
            [{"op": "replace", "path": ["arr", [0,2]], "value": 99}]""");
    }

    @Test
    void enumeration_scalar_broadcastsNull() {
        assertPatched("""
            {"arr":[null,2,null,4,null]}""", """
            {"arr":[1,2,3,4,5]}""", """
            [{"op": "replace", "path": ["arr", [0,2,4]], "value": null}]""");
    }

    @Test
    void enumeration_scalar_singleElement() {
        // Single-element enumeration: broadcast to one target
        assertPatched("""
            {"arr":[1,2,99]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", [2]], "value": 99}]""");
    }

    // ===== Array-of-one: explicit collapse =====

    @Test
    void range_arrayOfOne_collapses() {
        // "Shrink [1..3] to a single value" — the uniform collapse form
        assertPatched("""
            {"arr":[1,99,5]}""", """
            {"arr":[1,2,3,4,5]}""", """
            [{"op": "replace", "path": ["arr", [[1,3]]], "value": [99]}]""");
    }

    @Test
    void enumeration_arrayOfOne_collapsesToFirstTarget() {
        // Enumeration collapse: index 0 gets 99, remaining targets removed
        assertPatched("""
            {"arr":[99,2,4]}""", """
            {"arr":[1,2,3,4]}""", """
            [{"op": "replace", "path": ["arr", [0,2]], "value": [99]}]""");
    }

    // ===== Array + array: aligned =====

    @Test
    void range_arrayAligned_sameLength() {
        assertPatched("""
            {"arr":[1,10,20,30,5]}""", """
            {"arr":[1,2,3,4,5]}""", """
            [{"op": "replace", "path": ["arr", [[1,3]]], "value": [10,20,30]}]""");
    }

    @Test
    void range_arrayShorter_dropsExcess() {
        assertPatched("""
            {"arr":[1,10,20,5]}""", """
            {"arr":[1,2,3,4,5]}""", """
            [{"op": "replace", "path": ["arr", [[1,3]]], "value": [10,20]}]""");
    }

    @Test
    void range_arrayLonger_spreadsExcess() {
        assertPatched("""
            {"arr":[1,10,20,30,40,5]}""", """
            {"arr":[1,2,3,4,5]}""", """
            [{"op": "replace", "path": ["arr", [[1,3]]], "value": [10,20,30,40]}]""");
    }

    @Test
    void enumeration_arrayAligned() {
        assertPatched("""
            {"arr":[10,2,20,4]}""", """
            {"arr":[1,2,3,4]}""", """
            [{"op": "replace", "path": ["arr", [0,2]], "value": [10,20]}]""");
    }

    // ===== Single-index: value inserted as-is =====

    @Test
    void singleIndex_scalar() {
        assertPatched("""
            {"arr":[1,99,3]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", 1], "value": 99}]""");
    }

    @Test
    void singleIndex_arrayBecomesElement() {
        // A single-index target is not a multi-target path: the array is
        // inserted as-is, so the element becomes an array.
        assertPatched("""
            {"arr":[1,[9,8],3]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", 1], "value": [9,8]}]""");
    }

    // ===== No-op edges =====

    @Test
    void range_emptyTargets_throws() {
        // Range out of bounds: "replace elements 5 through 10" on a 3-element
        // array expresses intent to modify something that isn't there. The
        // client's view of the target is stale.
        assertThrows(JsonPatchTargetException.class, () ->
            patch("""
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", [[5,10]]], "value": 99}]"""));
    }

    @Test
    void range_emptyArray_throws() {
        // Same premise: "replace elements 0 through 5 of an empty array" —
        // nothing to replace. If the intent is to add elements, use spread.
        assertThrows(JsonPatchTargetException.class, () ->
            patch("""
            {"arr":[]}""", """
            [{"op": "replace", "path": ["arr", [[0,5]]], "value": 99}]"""));
    }

    @Test
    void range_partialOverlap_works() {
        // Boundary: as long as at least one target exists, the patch proceeds.
        // [[1,10]] on size 3 clamps to [1,2]; both exist; broadcast applies.
        assertPatched("""
          {"arr":[1,99,99]}""", """
          {"arr":[1,2,3]}""", """
          [{"op": "replace", "path": ["arr", [[1,10]]], "value": 99}]""");
    }

    @Test
    void enumeration_empty_isNoOp() {
        // Distinguished from range: an empty enumeration is an explicit
        // declaration of "zero targets", not a stale-state symptom.
        assertPatched("""
          {"arr":[1,2,3]}""", """
          {"arr":[1,2,3]}""", """
          [{"op": "replace", "path": ["arr", []], "value": 99}]""");
    }

    // ==== enumeration path mismatch with replacement values

    @Test
    void enumeration_arrayLonger_throws() {
        // More values than targets: no coherent "spread position" exists for a
        // scattered enumeration. Range handles this via spread-after-last;
        // enumeration must be a declaration error.
        assertThrowsExactly(JsonPatchDeclarationException.class, () ->
            patch("""
            {"arr":[1,2,3,4]}""", """
            [{"op": "replace", "path": ["arr", [0,2]], "value": [10,20,30]}]"""));
    }

    @Test
    void enumeration_arrayLonger_byOne_throws() {
        // Smallest failing case
        assertThrowsExactly(JsonPatchDeclarationException.class, () ->
            patch("""
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", [0,2]], "value": [10,20,30]}]"""));
    }

    @Test
    void enumeration_arrayEqualLength_stillWorks() {
        // Boundary: m == n is fine
        assertPatched("""
            {"arr":[10,2,20,4]}""", """
            {"arr":[1,2,3,4]}""", """
            [{"op": "replace", "path": ["arr", [0,2]], "value": [10,20]}]""");
    }

    @Test
    void enumeration_empty_isNoOp_evenWithNonEmptyValue() {
        // n == 0: no targets → no-op, even though m > n
        assertPatched("""
            {"arr":[1,2,3]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", []], "value": [10,20]}]""");
    }

    // but...

    @Test
    void range_arrayLonger_stillSpreads() {
        // Range: excess values go past the end of the contiguous span.
        // This is coherent because "past the end" is well-defined for a range.
        assertPatched("""
            {"arr":[1,10,20,30,40,5]}""", """
            {"arr":[1,2,3,4,5]}""", """
            [{"op": "replace", "path": ["arr", [[1,3]]], "value": [10,20,30,40]}]""");
    }

    // ==== replace enumeration clamping =====

    @Test
    void enumeration_missingTarget_throws() {
        // Client said "replace 0, 2, and 5" but 5 doesn't exist. Stale state.
        assertThrows(JsonPatchTargetException.class, () ->
            patch("""
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", [0,2,5]], "value": [10,20,30]}]"""));
    }

    @Test
    void enumeration_missingTargetScalar_throws() {
        // Same with scalar broadcast
        assertThrows(JsonPatchTargetException.class, () ->
            patch("""
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", [0,2,5]], "value": 99}]"""));
    }

    @Test
    void enumeration_missingTargetBeyondWritePortion_doesNotThrow() {
        // 5 is past the write portion (m=2), so it's a remove target — and
        // remove-on-missing is a no-op. The patch proceeds.
        assertPatched("""
            {"arr":[10,2,20]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", [0,2,5]], "value": [10,20]}]""");
    }

    @Test
    void enumeration_allTargetsExist_works() {
        // Baseline: no missing targets, all good
        assertPatched("""
            {"arr":[10,2,20,30]}""", """
            {"arr":[1,2,3,4]}""", """
            [{"op": "replace", "path": ["arr", [0,2,3]], "value": [10,20,30]}]""");
    }

    @Test
    void remove_outOfBoundsEnumeration_stillNoOps() {
        // remove stays forgiving: [0,2,5] on size 3 removes 0 and 2, no-ops on 5
        assertPatched("""
            {"arr":[2]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "remove", "path": ["arr", [0,2,5]]}]""");
    }

    // ==== target does not exist issues =====

    @Test
    void replace_missingObjectKey_inserts() {
        assertPatched("""
            {"a":1, "b": 2}""", """
            {"a":1}""", """
            [{"op": "replace", "path": ["b"], "value": 2}]""");
    }

    @Test
    void replace_outOfBoundsArrayIndex_throwsTargetException() {
        assertThrows(JsonPatchTargetException.class, () ->
            patch("""
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", 5], "value": 9}]"""));
    }

    @Test
    void replace_conditionNoMatch_throwsTargetException() {
        // Previously no-op; now stale-state symptom
        assertThrows(JsonPatchTargetException.class, () ->
            patch("""
            {"arr":[{"id":"y"}]}""", """
            [{"op": "replace", "path": ["arr", [{"id":"x"}]], "value": 9}]"""));
    }

    @Test
    void replace_root_stillWorks() {
        // exists(SELF) is true; no regression on root replace
        assertPatched("""
        {"b":2}""", """
        {"a":1}""", """
        [{"op": "replace", "path": [], "value": {"b":2}}]""");
    }

    @Test
    void replace_pointerRoot_stillWorks() {
        assertPatched("""
        {"b":2}""", """
        {"a":1}""", """
        [{"op": "replace", "path": "", "value": {"b":2}}]""");
    }

    @Test
    void replace_missingArrayIndex_throws() {
        assertThrows(JsonPatchTargetException.class, () ->
            patch("""
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", 5], "value": 9}]"""));
    }

    @Test
    void replace_atArraySize_throws() {
        // index == size would be append under remove+insert, but replace is
        // "modify in place" — use insert for append.
        assertThrows(JsonPatchTargetException.class, () ->
            patch("""
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", 3], "value": 9}]"""));
    }

    @Test
    void replace_missingParent_throws() {
        assertThrows(JsonPatchTargetException.class, () ->
            patch("{}", """
            [{"op": "replace", "path": ["a", "b"], "value": 1}]"""));
    }

    @Test
    void replace_conditionNoMatch_throws() {
        // resolve returns empty stream → can't determine a parent to add to
        assertThrows(JsonPatchTargetException.class, () ->
            patch("""
            {"arr":[{"id":"y"}]}""", """
            [{"op": "replace", "path": ["arr", [{"id":"x"}], "v"], "value": 9}]"""));
    }

    @Test
    void replace_conditionMatches_missingField_addsField() {
        // Condition matched → resolves to a specific element; field missing →
        // add member, consistent with object-set semantics
        assertPatched("""
            {"arr":[{"id":"x","v":9}]}""", """
            {"arr":[{"id":"x"}]}""", """
            [{"op": "replace", "path": ["arr", [{"id":"x"}], "v"], "value": 9}]""");
    }

}