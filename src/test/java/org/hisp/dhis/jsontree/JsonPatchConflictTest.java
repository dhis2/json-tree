package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonNode.Insert;
import org.hisp.dhis.jsontree.JsonNode.Remove;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hisp.dhis.jsontree.JsonNode.NULL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the conflict detection when running a {@link JsonNode#patch(List)}.
 */
class JsonPatchConflictTest {

    @Test
    void testPatch_RemoveSamePathBefore() {
        assertNoConflict(
            new Remove( "foo.bar" ),
            new Insert( "foo.bar", NULL ) );
    }

    @Test
    void testPatch_RemoveSamePathAfter() {
        assertSameConflict(
            new Insert( "foo.bar", NULL ),
            new Remove( "foo.bar" ));
    }

    @Test
    void testPatch_RemoveSamePathRemove() {
        assertNoConflict(
            new Remove( "foo.bar" ),
            new Remove( "foo.bar" ) );
    }

    @Test
    void testPatch_InsertSamePathInsert() {
        assertSameConflict(
            new Insert( "foo.bar", NULL ),
            new Insert( "foo.bar", NULL ) );
    }

    @Test
    void testPatch_InsertSiblingsPathInsert() {
        assertNoConflict(
            new Insert( "foo.x", NULL ),
            new Insert( "foo.y", NULL ) );
    }

    @Test
    void testPatch_RemoveChildPathAfter() {
        assertChildConflict(
            new Insert( "foo", NULL ),
            new Remove( "foo.bar" ) );
        assertChildConflict(
            new Insert( "foo.bar", NULL ),
            new Remove( "foo.bar.baz" ) );
    }
    @Test
    void testPatch_RemoveChildPathBefore() {
        assertParentConflict(
            new Remove( "foo.bar" ),
            new Insert( "foo", NULL ));
        assertParentConflict(
            new Remove( "foo.bar.baz" ),
            new Insert( "foo.bar", NULL ));
    }

    @Test
    void testPatch_RemoveParentPathBefore() {
        assertChildConflict(
            new Remove( "foo" ),
            new Insert( "foo.bar", NULL ));
        assertChildConflict(
            new Remove( "foo.bar" ),
            new Insert( "foo.bar.baz", NULL ));
    }

    @Test
    void testPatch_InsertParentPathBefore() {
        assertChildConflict(
            new Insert( "foo.bar", NULL ),
            new Insert( "foo.bar.baz", NULL ));
    }

    @Test
    void testPatch_RemoveParentPathAfter() {
        assertParentConflict(
            new Insert( "foo.bar", NULL ),
            new Remove( "foo" ) );
        assertParentConflict(
            new Insert( "foo.bar.baz", NULL ),
            new Remove( "foo.bar" ) );
    }

    @Test
    void testPatch_InsertParentPathAfter() {
        assertParentConflict(
            new Insert( "foo.bar.baz", NULL ),
            new Insert( "foo.bar", NULL ));
    }

    @Test
    void testPatch_RemoveParentPathRemoveAfter() {
        assertParentConflict(
            new Remove( "foo.bar.baz" ),
            new Remove( "foo.bar" ) );
    }

    @Test
    void testPatch_RemoveParentPathRemoveBefore() {
        assertChildConflict(
            new Remove( "foo.bar" ),
            new Remove( "foo.bar.baz" ));
    }

    @Test
    void testPatch_Misc() {
        assertNoConflict(
            new Insert( "foo.x", NULL ),
            new Remove( "bar.x" ),
            new Insert( "foo.y", NULL ),
            new Remove( "que" ),
            new Insert( "y", NULL ),
            new Insert( "que", NULL )
            );
    }

    private static void assertParentConflict(JsonNode.Operation... ops) {
        JsonPatchException ex = assertThrows( JsonPatchException.class,
            () -> JsonTree.checkPatch( List.of( ops ) ) );
        assertTrue( ex.getMessage().contains( " parent " ) );
    }

    private static void assertChildConflict(JsonNode.Operation... ops) {
        JsonPatchException ex = assertThrows( JsonPatchException.class,
            () -> JsonTree.checkPatch( List.of( ops ) ) );
        assertTrue( ex.getMessage().contains( " child " ) );
    }

    private static void assertSameConflict(JsonNode.Operation... ops) {
        JsonPatchException ex = assertThrows( JsonPatchException.class,
            () -> JsonTree.checkPatch( List.of( ops ) ) );
        assertTrue( ex.getMessage().contains( " same " ) );
    }

    private static void assertNoConflict(JsonNode.Operation... ops) {
        assertDoesNotThrow( () -> JsonTree.checkPatch( List.of(ops) ) );
    }
}
