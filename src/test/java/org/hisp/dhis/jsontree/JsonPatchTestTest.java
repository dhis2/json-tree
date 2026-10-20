package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchDeclarationException;
import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchTestException;
import org.hisp.dhis.jsontree.internal.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonPatchTestTest {

  @Test
  void test_pathOnly_passesForNonNullValue() {
    assertPatched("""
      {"a":1}""", """
      {"a":1}""", """
      [{"op": "test", "path": ["a"]}]""");
  }

  @Test
  void test_pathOnly_failsForNullValue() {
    // null counts as undefined
    assertThrowsExactly(JsonPatchTestException.class, () ->
        patch("""
          {"a":null}""", """
          [{"op": "test", "path": ["a"]}]"""));
  }

  @Test
  void test_pathOnly_failsForMissingKey() {
    assertThrowsExactly(JsonPatchTestException.class, () ->
        patch("{}", """
            [{"op": "test", "path": ["a"]}]"""));
  }

  @Test
  void test_valueNull_passesForNullValue() {
    // Preserves the "exact null" check that pathOnly no longer provides
    assertPatched("""
      {"a":null}""", """
      {"a":null}""", """
      [{"op": "test", "path": ["a"], "value": null}]""");
  }

  @Test
  void test_valueNull_failsForMissingKey() {
    // Distinguishes null from missing — the one thing the value-form
    // preserves that path-only no longer does
    assertThrowsExactly(JsonPatchTestException.class, () ->
        patch("{}", """
            [{"op": "test", "path": ["a"], "value": null}]"""));
  }

  @Test
  void test_valueNull_failsForNonNullValue() {
    assertThrowsExactly(JsonPatchTestException.class, () ->
        patch("""
          {"a":1}""", """
          [{"op": "test", "path": ["a"], "value": null}]"""));
  }

  @Test
  void test_pathOnly_passesForFalseAndZero() {
    // JSON false and 0 are defined values, not "falsy" — regression guard
    assertPatched("""
        {"a":false}""", """
        {"a":false}""", """
        [{"op": "test", "path": ["a"]}]""");
    assertPatched("""
        {"a":0}""", """
        {"a":0}""", """
        [{"op": "test", "path": ["a"]}]""");
  }

  @Test
  void test_pathOnly_passesForEmptyContainers() {
    // Empty object and empty array are defined values
    assertPatched("""
        {"a":{}}""", """
        {"a":{}}""", """
        [{"op": "test", "path": ["a"]}]""");
    assertPatched("""
        {"a":[]}""", """
        {"a":[]}""", """
        [{"op": "test", "path": ["a"]}]""");
  }

  @Test
  void test_pathOnly_multitarget_allDefined_passes() {
    // Range test on a fully populated array
    assertPatched("""
        {"arr":[1,2,3]}""", """
        {"arr":[1,2,3]}""", """
        [{"op": "test", "path": ["arr", [[0,2]]]}]""");
  }

  @Test
  void test_pathOnly_multitarget_nullElement_fails() {
    // Even one null in the range fails the "all defined" check
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            patch("""
          {"arr":[1,null,3]}""","""
          [{"op": "test", "path": ["arr", [[0,2]]]}]"""));
  }

  @Test
  void cas_replace_passesWhenMatches() {
    assertPatched("""
      {"a":2}""", """
      {"a":1}""", """
      [{"op": "replace", "path": "/a", "value": 2, "test": 1}]""");
  }

  @Test
  void cas_replace_failsWhenMismatches() {
    assertThrowsExactly(JsonPatchTestException.class, () ->
        patch("""
        {"a":1}""", """
        [{"op": "replace", "path": "/a", "value": 2, "test": 99}]"""));
  }

  @Test
  void cas_replace_failsWhenMissing() {
    assertThrowsExactly(JsonPatchTestException.class, () ->
        patch("{}", """
            [{"op": "replace", "path": "/a", "value": 2, "test": 1}]"""));
  }

  @Test
  void cas_insert_replacesWhenMatches() {
    // insert on existing path with matching test = CAS replace
    assertPatched("""
      {"a":2}""", """
      {"a":1}""", """
      [{"op": "insert", "path": "/a", "value": 2, "test": 1}]""");
  }

  @Test
  void cas_insert_failsWhenMissing() {
    // Precondition requires the path to exist; auto-create is unreachable with CAS
    assertThrowsExactly(JsonPatchTestException.class, () ->
        patch("{}", """
            [{"op": "insert", "path": "/a", "value": 2, "test": 1}]"""));
  }

  @Test
  void cas_remove_passesWhenMatches() {
    assertPatched("{}", """
        {"a":1}""", """
        [{"op": "remove", "path": "/a", "test": 1}]""");
  }

  @Test
  void cas_remove_failsWhenMismatches() {
    assertThrowsExactly(JsonPatchTestException.class, () ->
        patch("""
            {"a":1}""", """
            [{"op": "remove", "path": "/a", "test": 2}]"""));
  }

  @Test
  void cas_testValueNull_passesForNull() {
    assertPatched("""
        {"a":2}""", """
        {"a":null}""", """
        [{"op": "replace", "path": "/a", "value": 2, "test": null}]""");
  }

  @Test
  void cas_testValueNull_failsForMissing() {
    assertThrowsExactly(JsonPatchTestException.class, () ->
        patch("{}", """
            [{"op": "replace", "path": "/a", "value": 2, "test": null}]"""));
  }

  @Test
  void cas_multiTargetRange_throws() {
    assertThrowsExactly(JsonPatchDeclarationException.class, () ->
        patch("""
        {"arr":[1,2,3]}""", """
        [{"op": "remove", "path": ["arr", [[0,2]]], "test": 1}]"""));
  }

  @Test
  void cas_testOpRejectsTestField() {
    assertThrowsExactly(JsonPatchDeclarationException.class, () ->
        patch("""
        {"a":1}""", """
        [{"op": "test", "path": "/a", "value": 1, "test": 2}]"""));
  }

  @Test
  void cas_spreadRejectsTestField() {
    assertThrowsExactly(JsonPatchDeclarationException.class, () ->
        patch("""
        {"a":{}}""", """
        [{"op": "spread", "path": "/a", "value": {"x":1}, "test": 1}]"""));
  }

  @Test
  void cas_idempotencyPattern() {
    // Apply once: test passes, value changes
    assertPatched("""
        {"status":"published"}""", """
        {"status":"draft"}""", """
        [{"op": "replace", "path": "/status", "value": "published", "test": "draft"}]""");
    // Apply again: test fails, throws (client catches and treats as "already applied")
    assertThrowsExactly(
        JsonPatchTestException.class,
        () ->
            patch("""
            {"status":"published"}""", """
            [{"op": "replace", "path": "/status", "value": "published", "test": "draft"}]"""));
  }

  @Test
  void cas_move_rfcForm_testsDestination() {
    assertPatched("""
      {"a":1,"b":2}""", """
      {"a":1,"b":5,"c":2}""", """
      [{"op": "move", "from": "/c", "path": "/b", "test": 5}]""");
  }

  @Test
  void cas_move_toForm_testsDestination() {
    assertPatched("""
      {"a":1,"b":2}""", """
      {"a":1,"b":5,"c":2}""", """
      [{"op": "move", "from": "/c", "to": "/b", "test": 5}]""");
  }

  @Test
  void cas_move_rfcForm_failsWhenDestinationMismatches() {
    assertThrowsExactly(JsonPatchTestException.class, () ->
        patch("""
          {"a":1,"b":5,"c":2}""", """
          [{"op": "move", "from": "/c", "path": "/b", "test": 99}]"""));
  }

  @Test
  void cas_move_toForm_failsWhenDestinationMismatches() {
    assertThrowsExactly(JsonPatchTestException.class, () ->
        patch("""
          {"a":1,"b":5,"c":2}""", """
          [{"op": "move", "from": "/c", "to": "/b", "test": 99}]"""));
  }

  @Test
  void cas_copy_toForm_testsDestination() {
    assertPatched("""
      {"a":1,"b":2,"c":2}""", """
      {"a":1,"b":5,"c":2}""", """
      [{"op": "copy", "from": "/c", "to": "/b", "test": 5}]""");
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
