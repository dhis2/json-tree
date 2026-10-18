package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.Language;
import org.junit.jupiter.api.Test;

import static org.hisp.dhis.jsontree.JsonPathPattern.JsonPathPatternType.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JsonPathPattern#type()}.
 *
 * <p>Paths are supplied as raw JSON arrays so key names must be quoted strings (e.g. {@code
 * ["foo"]}, not {@code [foo]}).
 */
class JsonPathPatternJsonPathPatternTypeTest {

  private static JsonPathPattern pathOf(@Language("json") String jsonArray) {
    return JsonPathPattern.of(jsonArray);
  }

  private static JsonPathPattern.JsonPathPatternType typeOf(@Language("json") String jsonArray) {
    return pathOf(jsonArray).type();
  }

  // ===== ROOT =====

  @Test
  void type_rootConstant() {
    assertEquals(ROOT, JsonPathPattern.ROOT.type());
  }

  @Test
  void type_emptyPath() {
    assertEquals(ROOT, typeOf("[]"));
  }

  @Test
  void type_emptyPathViaCharSequence() {
    assertEquals(ROOT, JsonPathPattern.of("[]").type());
  }

  // ===== KEY =====

  @Test
  void type_simpleKey() {
    assertEquals(
        KEY,
        typeOf(
            """
            ["foo"]"""));
  }

  @Test
  void type_keyContainingDot() {
    assertEquals(
        KEY,
        typeOf(
            """
            ["foo.bar"]"""));
  }

  @Test
  void type_keyContainingBrackets() {
    assertEquals(
        KEY,
        typeOf(
            """
            ["foo[5]"]"""));
  }

  @Test
  void type_numericStringKey() {
    // Quoted "5" is a key, not an index
    assertEquals(
        KEY,
        typeOf(
            """
            ["5"]"""));
  }

  @Test
  void type_emptyStringKey() {
    assertEquals(
        KEY,
        typeOf(
            """
            [""]"""));
  }

  @Test
  void type_unicodeKey() {
    assertEquals(
        KEY,
        typeOf(
            """
            ["ключ"]"""));
  }

  @Test
  void type_nestedKeys() {
    JsonPathPattern p =
        pathOf(
            """
            ["foo", "bar", "baz"]""");
    assertEquals(KEY, p.type());
    assertEquals(KEY, p.parent().type());
    assertEquals(KEY, p.parent().parent().type());
  }

  // ===== INDEX =====

  @Test
  void type_zeroIndex() {
    assertEquals(INDEX, typeOf("[0]"));
  }

  @Test
  void type_positiveIndex() {
    assertEquals(INDEX, typeOf("[5]"));
  }

  @Test
  void type_negativeIndex() {
    assertEquals(INDEX, typeOf("[-1]"));
  }

  @Test
  void type_negativeZeroIndex() {
    // -0 is still an INDEX; the sign is interpreted at resolve time.
    assertEquals(INDEX, typeOf("[-0]"));
  }

  // ===== ENUMERATION =====

  @Test
  void type_enumerationOfOne() {
    assertEquals(ENUMERATION, typeOf("[[5]]"));
  }

  @Test
  void type_enumerationOfMany() {
    assertEquals(ENUMERATION, typeOf("[[1, 3, 5]]"));
  }

  @Test
  void type_enumerationWithNegatives() {
    assertEquals(ENUMERATION, typeOf("[[-1, -3]]"));
  }

  @Test
  void type_enumerationOfOneViaCharSequence() {
    // of("[5]") parses "[5]" as a JSON fragment -> array with one element
    assertEquals(ENUMERATION, typeOf("[[5]]"));
  }

  @Test
  void type_emptyEnumeration() {
    // Empty array is an enumeration with no entries; only reachable as
    // a non-tail segment because "[]" alone makes ROOT.
    assertEquals(
        ENUMERATION,
        typeOf(
            """
            ["foo", []]"""));
  }

  // ===== RANGE =====

  @Test
  void type_simpleRange() {
    assertEquals(RANGE, typeOf("[[[1, 2]]]"));
  }

  @Test
  void type_rangeWithNegatives() {
    assertEquals(RANGE, typeOf("[[[-3, -1]]]"));
  }

  @Test
  void type_rangeWithZeros() {
    assertEquals(RANGE, typeOf("[[[0, 0]]]"));
  }

  @Test
  void type_rangeArityIsCheckedByKind() {
    assertThrows(JsonPathPatternException.class, () -> typeOf("[[[]]]"));
    assertThrows(JsonPathPatternException.class, () -> typeOf("[[[1, 2, 3]]]"));
    assertThrows(
        JsonPathPatternException.class,
        () ->
            typeOf(
                """
            [[["a", "b"]]]"""));
    // but
    assertEquals(ARR_CONDITION, typeOf("[[[1]]]"));
  }

  // ===== CONDITION =====

  @Test
  void type_valueCondition() {
    assertEquals(
        OBJ_CONDITION,
        typeOf(
            """
            [[{"=": 7}]]"""));
  }

  @Test
  void type_valueConditionOnString() {
    assertEquals(
        OBJ_CONDITION,
        typeOf(
            """
            [[{"=": "foo"}]]"""));
  }

  @Test
  void type_propertyCondition() {
    assertEquals(
        OBJ_CONDITION,
        typeOf(
            """
            [[{"id": "foo"}]]"""));
  }

  @Test
  void type_multiPropertyCondition() {
    assertEquals(
        OBJ_CONDITION,
        typeOf(
            """
            [[{"id": "foo", "status": "draft"}]]"""));
  }

  @Test
  void type_emptyConditionObject() {
    // kind() accepts; resolve() matches everything (pinned in resolve tests)
    assertEquals(OBJ_CONDITION, typeOf("[[{}]]"));
  }

  // ===== CONDITION_SHORTHAND =====

  @Test
  void type_shorthandTrue() {
    assertEquals(ARR_CONDITION, typeOf("[[true]]"));
  }

  @Test
  void type_shorthandFalse() {
    assertEquals(ARR_CONDITION, typeOf("[[false]]"));
  }

  @Test
  void type_shorthandNull() {
    assertEquals(ARR_CONDITION, typeOf("[[null]]"));
  }

  @Test
  void type_shorthandString() {
    assertEquals(
        ARR_CONDITION,
        typeOf(
            """
            [["foo"]]"""));
  }

  // ===== ERROR: invalid segment shapes =====

  @Test
  void type_shorthandNonIntegerNumber() {
    // 1.5 is not isInteger()
    assertThrows(JsonPathPatternException.class, () -> typeOf("[1.5]"));
  }

  @Test
  void type_booleanSegment_throws() {
    assertThrows(JsonPathPatternException.class, () -> typeOf("[true]"));
  }

  @Test
  void type_objectSegment_throws() {
    assertThrows(JsonPathPatternException.class, () -> typeOf("[{}]"));
  }

  @Test
  void type_mixedMultiElementEnumeration_throws() {
    assertThrows(JsonPathPatternException.class, () -> typeOf("[1, true]"));
    assertThrows(JsonPathPatternException.class, () -> typeOf("[true, false]"));
    assertThrows(
        JsonPathPatternException.class,
        () ->
            typeOf(
                """
            [{"a":1}, {"b":2}]"""));
  }

  // ===== companions: isRoot / toString =====

  @Test
  void isRoot_rootConstant() {
    assertTrue(JsonPathPattern.ROOT.isRoot());
  }

  @Test
  void isRoot_emptyPath() {
    assertTrue(pathOf("[]").isRoot());
  }

  @Test
  void isRoot_nonEmptyPath() {
    assertFalse(
        pathOf(
                """
            ["foo"]""")
            .isRoot());
  }

  @Test
  void toString_root() {
    assertEquals("[]", JsonPathPattern.ROOT.toString());
  }

  @Test
  void toString_singleKey() {
    assertEquals(
        """
            ["foo"]""",
        pathOf(
                """
            ["foo"]""")
            .toString());
  }

  @Test
  void toString_mixedSegments() {
    assertEquals(
        """
            ["foo",5,[1, 2]]""",
        pathOf(
                """
            ["foo", 5, [1, 2]]""")
            .toString());
  }

  // ===== CharSequence factory: parses each argument as JSON =====

  @Test
  void of_charSequence_parsesEachAsJsonFragment() {
    // "5" is an index, "[5]" is enumeration-of-one, "\"5\"" is a key.
    assertEquals(INDEX, typeOf("[5]"));
    assertEquals(ENUMERATION, typeOf("[[5]]"));
    assertEquals(KEY, typeOf("[\"5\"]"));
  }

  @Test
  void of_charSequence_buildsNestedPath() {
    JsonPathPattern p = JsonPathPattern.of("[\"foo\", 5, [1, 2]]");
    assertEquals(ENUMERATION, p.type());
    assertEquals(INDEX, p.parent().type());
    assertEquals(KEY, p.parent().parent().type());
  }

  // ===== more gaps =====

  @Test
  void type_enumerationWithDuplicateIndex() {
    // Currently accepted; resolve emits duplicates. Pin behaviour.
    assertEquals(ENUMERATION, typeOf("[[1, 1]]"));
  }

  @Test
  void type_enumerationWithNegativeZero() {
    assertEquals(ENUMERATION, typeOf("[[0, -0]]"));
  }

  @Test
  void type_mixedSignEnumeration() {
    // Positive and negative in one enumeration — allowed by kind()
    assertEquals(ENUMERATION, typeOf("[[1, -1]]"));
  }

  @Test
  void type_singleElementEqualsString() {
    // ["="] is a value condition on the string "=", not a malformed value-condition
    assertEquals(
        ARR_CONDITION,
        typeOf(
            """
          [["="]]"""));
  }
}
