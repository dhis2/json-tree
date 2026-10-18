package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.Language;
import org.junit.jupiter.api.Test;

import static org.hisp.dhis.jsontree.JsonPathExpression.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JsonPathExpression#kind()}.
 *
 * <p>Paths are supplied as raw JSON arrays so key names must be quoted
 * strings (e.g. {@code ["foo"]}, not {@code [foo]}).
 */
class JsonPathExpressionKindTest {

    private static JsonPathExpression path(@Language("json") String jsonArray) {
        return JsonPathExpression.of(jsonArray);
    }

    private static JsonPathExpression.Kind kind(@Language("json") String jsonArray) {
        return path(jsonArray).kind();
    }

    // ===== ROOT =====

    @Test
    void kind_rootConstant() {
        assertEquals(ROOT, JsonPathExpression.ROOT.kind());
    }

    @Test
    void kind_emptyPath() {
        assertEquals(ROOT, kind("[]"));
    }

    @Test
    void kind_emptyPathViaCharSequence() {
        assertEquals(ROOT, JsonPathExpression.of("[]").kind());
    }

    // ===== KEY =====

    @Test
    void kind_simpleKey() {
        assertEquals(KEY, kind("""
            ["foo"]"""));
    }

    @Test
    void kind_keyContainingDot() {
        assertEquals(KEY, kind("""
            ["foo.bar"]"""));
    }

    @Test
    void kind_keyContainingBrackets() {
        assertEquals(KEY, kind("""
            ["foo[5]"]"""));
    }

    @Test
    void kind_numericStringKey() {
        // Quoted "5" is a key, not an index
        assertEquals(KEY, kind("""
            ["5"]"""));
    }

    @Test
    void kind_emptyStringKey() {
        assertEquals(KEY, kind("""
            [""]"""));
    }

    @Test
    void kind_unicodeKey() {
        assertEquals(KEY, kind("""
            ["ключ"]"""));
    }

    @Test
    void kind_nestedKeys() {
        JsonPathExpression p = path("""
            ["foo", "bar", "baz"]""");
        assertEquals(KEY, p.kind());
        assertEquals(KEY, p.parent().kind());
        assertEquals(KEY, p.parent().parent().kind());
    }

    // ===== INDEX =====

    @Test
    void kind_zeroIndex() {
        assertEquals(INDEX, kind("[0]"));
    }

    @Test
    void kind_positiveIndex() {
        assertEquals(INDEX, kind("[5]"));
    }

    @Test
    void kind_negativeIndex() {
        assertEquals(INDEX, kind("[-1]"));
    }

    @Test
    void kind_negativeZeroIndex() {
        // -0 is still an INDEX; the sign is interpreted at resolve time.
        assertEquals(INDEX, kind("[-0]"));
    }

    // ===== ENUMERATION =====

    @Test
    void kind_enumerationOfOne() {
        assertEquals(ENUMERATION, kind("[[5]]"));
    }

    @Test
    void kind_enumerationOfMany() {
        assertEquals(ENUMERATION, kind("[[1, 3, 5]]"));
    }

    @Test
    void kind_enumerationWithNegatives() {
        assertEquals(ENUMERATION, kind("[[-1, -3]]"));
    }

    @Test
    void kind_enumerationOfOneViaCharSequence() {
        // of("[5]") parses "[5]" as a JSON fragment -> array with one element
        assertEquals(ENUMERATION, kind("[[5]]"));
    }

    @Test
    void kind_emptyEnumeration() {
        // Empty array is an enumeration with no entries; only reachable as
        // a non-tail segment because "[]" alone makes ROOT.
        assertEquals(ENUMERATION, kind("""
            ["foo", []]"""));
    }

    // ===== RANGE =====

    @Test
    void kind_simpleRange() {
        assertEquals(RANGE, kind("[[[1, 2]]]"));
    }

    @Test
    void kind_rangeWithNegatives() {
        assertEquals(RANGE, kind("[[[-3, -1]]]"));
    }

    @Test
    void kind_rangeWithZeros() {
        assertEquals(RANGE, kind("[[[0, 0]]]"));
    }

    @Test
    void kind_rangeArityIsCheckedByKind() {
        assertThrows(JsonPatchException.class, () -> kind("[[[]]]"));
        assertThrows(JsonPatchException.class, () -> kind("[[[1, 2, 3]]]"));
        assertThrows(JsonPatchException.class, () -> kind("""
            [[["a", "b"]]]"""));
        // but
        assertEquals(ARR_CONDITION, kind("[[[1]]]"));
    }

    // ===== CONDITION =====

    @Test
    void kind_valueCondition() {
        assertEquals(OBJ_CONDITION, kind("""
            [[{"=": 7}]]"""));
    }

    @Test
    void kind_valueConditionOnString() {
        assertEquals(OBJ_CONDITION, kind("""
            [[{"=": "foo"}]]"""));
    }

    @Test
    void kind_propertyCondition() {
        assertEquals(OBJ_CONDITION, kind("""
            [[{"id": "foo"}]]"""));
    }

    @Test
    void kind_multiPropertyCondition() {
        assertEquals(OBJ_CONDITION, kind("""
            [[{"id": "foo", "status": "draft"}]]"""));
    }

    @Test
    void kind_emptyConditionObject() {
        // kind() accepts; resolve() matches everything (pinned in resolve tests)
        assertEquals(OBJ_CONDITION, kind("[[{}]]"));
    }

    // ===== CONDITION_SHORTHAND =====

    @Test
    void kind_shorthandTrue() {
        assertEquals(ARR_CONDITION, kind("[[true]]"));
    }

    @Test
    void kind_shorthandFalse() {
        assertEquals(ARR_CONDITION, kind("[[false]]"));
    }

    @Test
    void kind_shorthandNull() {
        assertEquals(ARR_CONDITION, kind("[[null]]"));
    }

    @Test
    void kind_shorthandString() {
        assertEquals(ARR_CONDITION, kind("""
            [["foo"]]"""));
    }

    // ===== ERROR: invalid segment shapes =====

    @Test
    void kind_shorthandNonIntegerNumber() {
        // 1.5 is not isInteger()
        assertThrows(JsonPatchException.class, () -> kind("[1.5]"));
    }

    @Test
    void kind_booleanSegment_throws() {
        assertThrows(JsonPatchException.class, () -> kind("[true]"));
    }

    @Test
    void kind_objectSegment_throws() {
        assertThrows(JsonPatchException.class,
            () -> kind("[{}]"));
    }

    @Test
    void kind_mixedMultiElementEnumeration_throws() {
        assertThrows(JsonPatchException.class, () -> kind("[1, true]"));
        assertThrows(JsonPatchException.class, () -> kind("[true, false]"));
        assertThrows(JsonPatchException.class, () -> kind("""
            [{"a":1}, {"b":2}]"""));
    }

    // ===== companions: isRoot / toString =====

    @Test
    void isRoot_rootConstant() {
        assertTrue(JsonPathExpression.ROOT.isRoot());
    }

    @Test
    void isRoot_emptyPath() {
        assertTrue(path("[]").isRoot());
    }

    @Test
    void isRoot_nonEmptyPath() {
        assertFalse(path("""
            ["foo"]""").isRoot());
    }

    @Test
    void toString_root() {
        assertEquals("[]", JsonPathExpression.ROOT.toString());
    }

    @Test
    void toString_singleKey() {
        assertEquals("""
            ["foo"]""", path("""
            ["foo"]""").toString());
    }

    @Test
    void toString_mixedSegments() {
    assertEquals(
        """
            ["foo",5,[1, 2]]""",
        path(
"""
            ["foo", 5, [1, 2]]""")
            .toString());
    }

    // ===== CharSequence factory: parses each argument as JSON =====

    @Test
    void of_charSequence_parsesEachAsJsonFragment() {
        // "5" is an index, "[5]" is enumeration-of-one, "\"5\"" is a key.
        assertEquals(INDEX, kind("[5]"));
        assertEquals(ENUMERATION, kind("[[5]]"));
        assertEquals(KEY, kind("[\"5\"]"));
    }

    @Test
    void of_charSequence_buildsNestedPath() {
        JsonPathExpression p = JsonPathExpression.of("[\"foo\", 5, [1, 2]]");
        assertEquals(ENUMERATION, p.kind());
        assertEquals(INDEX, p.parent().kind());
        assertEquals(KEY, p.parent().parent().kind());
    }

    // ===== more gaps =====

    @Test void kind_enumerationWithDuplicateIndex() {
        // Currently accepted; resolve emits duplicates. Pin behaviour.
        assertEquals(ENUMERATION, kind("[[1, 1]]"));
    }
    @Test void kind_enumerationWithNegativeZero() {
        assertEquals(ENUMERATION, kind("[[0, -0]]"));
    }
    @Test void kind_mixedSignEnumeration() {
        // Positive and negative in one enumeration — allowed by kind()
        assertEquals(ENUMERATION, kind("[[1, -1]]"));
    }
    @Test void kind_singleElementEqualsString() {
        // ["="] is a value condition on the string "=", not a malformed value-condition
        assertEquals(ARR_CONDITION, kind("""
          [["="]]"""));
    }
}