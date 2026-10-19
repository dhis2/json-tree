package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.Language;
import org.junit.jupiter.api.Test;

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
    void range_emptyTargets_isNoOp() {
        assertPatched("""
            {"arr":[1,2,3]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", [[5,10]]], "value": 99}]""");
    }

    @Test
    void enumeration_empty_isNoOp() {
        // Empty array segment → empty enumeration → no targets → no-op
        assertPatched("""
            {"arr":[1,2,3]}""", """
            {"arr":[1,2,3]}""", """
            [{"op": "replace", "path": ["arr", []], "value": 99}]""");
    }
}