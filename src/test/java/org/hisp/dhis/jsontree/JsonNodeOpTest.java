package org.hisp.dhis.jsontree;

import static org.hisp.dhis.jsontree.JsonNode.NULL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.util.List;
import org.hisp.dhis.jsontree.JsonNodeOp.Insert;
import org.hisp.dhis.jsontree.JsonNodeOp.Remove;
import org.hisp.dhis.jsontree.JsonNodeOp.Spread;
import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchEditException;
import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchTargetException;
import org.junit.jupiter.api.Test;

/** Checks the conflict detection when running a {@link JsonNode#patch(List)}. */
class JsonNodeOpTest {

  @Test
  void testPatch_RemoveSamePathBefore() {
    assertRejected(
        "operation 0 has same target as operation 1:",
        remove(".foo.bar"),
        insert(".foo.bar", NULL));
  }

  @Test
  void testPatch_RemoveSamePathAfter() {
    assertRejected(
        "operation 0 has same target as operation 1:",
        insert(".foo.bar", NULL),
        remove(".foo.bar"));
  }

  @Test
  void testPatch_RemoveSamePathRemove() {
    assertRejected(
        "operation 0 has same target as operation 1:",
        remove(".foo.bar"),
        remove(".foo.bar"));
  }

  @Test
  void testPatch_InsertSamePathInsert() {
    assertRejected(
        "operation 0 has same target as operation 1:",
        insert(".foo.bar", NULL),
        insert(".foo.bar", NULL));
  }

  @Test
  void testPatch_InsertSiblingsPathInsert() {
    assertAccepted(insert(".foo.x", NULL), insert(".foo.y", NULL));
  }

  @Test
  void testPatch_RemoveChildPathAfter() {
    assertRejected(
        "operation 1 targets child of operation 0:",
        insert(".foo", NULL),
        remove(".foo.bar"));
    assertRejected(
        "operation 1 targets child of operation 0:",
        insert(".foo.bar", NULL),
        remove(".foo.bar.baz"));
  }

  @Test
  void testPatch_RemoveChildPathBefore() {
    assertRejected(
        "operation 0 targets child of operation 1:",
        remove(".foo.bar"),
        insert(".foo", NULL));
    assertRejected(
        "operation 0 targets child of operation 1:",
        remove(".foo.bar.baz"),
        insert(".foo.bar", NULL));
  }

  @Test
  void testPatch_RemoveParentPathBefore() {
    assertRejected(
        "operation 1 targets child of operation 0:",
        remove(".foo"),
        insert(".foo.bar", NULL));
    assertRejected(
        "operation 1 targets child of operation 0:",
        remove(".foo.bar"),
        insert(".foo.bar.baz", NULL));
  }

  @Test
  void testPatch_InsertParentPathBefore() {
    assertRejected(
        "operation 1 targets child of operation 0:",
        insert(".foo.bar", NULL),
        insert(".foo.bar.baz", NULL));
  }

  @Test
  void testPatch_RemoveParentPathAfter() {
    assertRejected(
        "operation 0 targets child of operation 1:",
        insert(".foo.bar", NULL),
        remove(".foo"));
    assertRejected(
        "operation 0 targets child of operation 1:",
        insert(".foo.bar.baz", NULL),
        remove(".foo.bar"));
  }

  @Test
  void testPatch_InsertParentPathAfter() {
    assertRejected(
        "operation 0 targets child of operation 1:",
        insert(".foo.bar.baz", NULL),
        insert(".foo.bar", NULL));
  }

  @Test
  void testPatch_RemoveParentPathRemoveAfter() {
    assertRejected(
        "operation 0 targets child of operation 1:",
        remove(".foo.bar.baz"),
        remove(".foo.bar"));
  }

  @Test
  void testPatch_RemoveParentPathRemoveBefore() {
    assertRejected(
        "operation 1 targets child of operation 0:",
        remove(".foo.bar"),
        remove(".foo.bar.baz"));
  }

  @Test
  void testPatch_InsertArrayInsert() {
    assertAccepted(insert(".foo[0]", NULL), insert(".foo[1]", NULL));
  }

  @Test
  void testPatch_Misc() {
    assertAccepted(
        insert(".foo.x", NULL),
        remove(".bar.x"),
        insert(".foo.y", NULL),
        remove(".fo"),
        insert(".y", NULL),
        insert(".que", NULL));
  }

  @Test
  void testPatch_ObjectSpread() {
    assertAccepted(
        spread(
            ".foo",
            JsonNode.of(
                """
                {"x": 1, "y": 2}""")),
        insert(".foo.z", JsonNode.of("3")));

    assertAccepted(
        spread(
            ".foo",
            JsonNode.of(
                """
                {"x": 1, "y": 2}""")),
        spread(
            ".foo",
            JsonNode.of(
                """
                {"z": 3, "zero": 0}""")));

    assertRejected(
        "operation 1 targets child of operation 0:",
        spread(
            ".foo",
            JsonNode.of(
                """
                {"z": 1, "y": 2}""")),
        insert(".foo.z", JsonNode.of("3")));

    assertRejected(
        "operation 0 has same target as operation 1:",
        spread(
            ".foo",
            JsonNode.of(
                """
                {"x": 1, "y": 2}""")),
        spread(
            ".foo",
            JsonNode.of(
                """
                {"x": 3, "zero": 0}""")));
  }

  @Test
  void testPatch_ArraySpread() {
    // A spread of two elements at index 0 occupies .foo[0] and .foo[1].
    // Inserting at .foo[2] (the index just past the occupied range) is fine.
    assertAccepted(
        spread(
            ".foo[0]",
            JsonNode.of(
                """
                ["x", "y"]""")),
        insert(".foo[2]", JsonNode.of("42")));

    // A spread at index 1 does not disturb the element at index 0.
    // (This is the descending-apply-order invariant in miniature.)
    assertAccepted(
        spread(
            ".foo[1]",
            JsonNode.of(
                """
                ["y", "z"]""")),
        insert(".foo[0]", JsonNode.of("42")));

    // Two spreads over disjoint index ranges do not clash.
    assertAccepted(
        spread(
            ".foo[0]",
            JsonNode.of(
                """
                ["x"]""")),
        spread(
            ".foo[1]",
            JsonNode.of(
                """
                ["y", "z"]""")));

    // Spread + Insert at disjoint index does not clash
    assertAccepted(
        spread(
            ".foo[0]",
            JsonNode.of(
                """
                ["x", "y"]""")),
        insert(".foo[1]", JsonNode.of("42")));

    // Two spreads that both claim .foo[0] clash.
    assertRejected(
        "operation 0 has same target as operation 1:",
        spread(
            ".foo[0]",
            JsonNode.of(
                """
                ["x"]""")),
        spread(
            ".foo[0]",
            JsonNode.of(
                """
                ["y"]""")));

    // An empty spread claims no index and therefore clashes with nothing.
    assertAccepted(
        spread(
            ".foo[0]",
            JsonNode.of(
                """
                []""")),
        insert(".foo[0]", JsonNode.of("42")));
  }

  @Test
  void lowLevel_insert_scalarParent_throwsEditException() {
    JsonNode target = JsonNode.of("""
      {"a":5}""");
    JsonPath path = new JsonPath(new JsonPath(null, Text.of("a")), Text.of("b"));
    assertThrowsExactly(JsonPatchEditException.class, () ->
        JsonNodeOp.patch(target, List.of(new JsonNodeOp.Insert(path, JsonNode.of("1")))));
  }

  @Test
  void lowLevel_replace_scalarParent_throwsEditException() {
    JsonNode target = JsonNode.of("""
      {"a":5}""");
    assertThrowsExactly(JsonPatchEditException.class, () ->
        JsonNodeOp.patch(target, List.of(new JsonNodeOp.Replace(JsonPath.of("a", "b"), JsonNode.of("1")))));
  }

  private static void assertRejected(String error, JsonNodeOp... ops) {
    JsonPatchTargetException ex =
        assertThrowsExactly(JsonPatchTargetException.class, () -> JsonNodeOp.checkPatch(List.of(ops)));
    String msg = ex.getMessage();
    assertEquals(error, msg.substring(0, Math.min(msg.length(), error.length())), msg);
  }

  private static void assertAccepted(JsonNodeOp... ops) {
    assertDoesNotThrow(() -> JsonNodeOp.checkPatch(List.of(ops)));
  }

  private static Remove remove(String path) {
    return new Remove(JsonPath.of(path));
  }

  private static Insert insert(String path, JsonNode value) {
    return new Insert(JsonPath.of(path), value);
  }

  private static Spread spread(String path, JsonNode value) {
    return new Spread(JsonPath.of(path), value);
  }
}
