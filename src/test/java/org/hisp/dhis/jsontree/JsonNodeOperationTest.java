package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonNodeOperation.Insert;
import org.hisp.dhis.jsontree.JsonNodeOperation.Remove;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hisp.dhis.jsontree.JsonNode.NULL;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the conflict detection when running a {@link JsonNode#patch(List)}.
 */
class JsonNodeOperationTest {

    @Test
    void testPatch_RemoveSamePathBefore() {
        assertRejected("operation 0 has same target as operation 1:",
            new Remove( "foo.bar" ),
            new Insert( "foo.bar", NULL ) );
    }

    @Test
    void testPatch_RemoveSamePathAfter() {
        assertRejected("operation 0 has same target as operation 1:",
            new Insert( "foo.bar", NULL ),
            new Remove( "foo.bar" ));
    }

    @Test
    void testPatch_RemoveSamePathRemove() {
        assertRejected("operation 0 has same target as operation 1:",
            new Remove( "foo.bar" ),
            new Remove( "foo.bar" ) );
    }

    @Test
    void testPatch_InsertSamePathInsert() {
        assertRejected("operation 0 has same target as operation 1:",
            new Insert( "foo.bar", NULL ),
            new Insert( "foo.bar", NULL ) );
    }

    @Test
    void testPatch_InsertSiblingsPathInsert() {
        assertAccepted(
            new Insert( "foo.x", NULL ),
            new Insert( "foo.y", NULL ) );
    }

    @Test
    void testPatch_RemoveChildPathAfter() {
        assertRejected("operation 1 targets child of operation 0:",
            new Insert( "foo", NULL ),
            new Remove( "foo.bar" ) );
        assertRejected("operation 1 targets child of operation 0:",
            new Insert( "foo.bar", NULL ),
            new Remove( "foo.bar.baz" ) );
    }
    @Test
    void testPatch_RemoveChildPathBefore() {
        assertRejected("operation 0 targets child of operation 1:",
            new Remove( "foo.bar" ),
            new Insert( "foo", NULL ));
        assertRejected("operation 0 targets child of operation 1:",
            new Remove( "foo.bar.baz" ),
            new Insert( "foo.bar", NULL ));
    }

    @Test
    void testPatch_RemoveParentPathBefore() {
        assertRejected("operation 1 targets child of operation 0:",
            new Remove( "foo" ),
            new Insert( "foo.bar", NULL ));
        assertRejected("operation 1 targets child of operation 0:",
            new Remove( "foo.bar" ),
            new Insert( "foo.bar.baz", NULL ));
    }

    @Test
    void testPatch_InsertParentPathBefore() {
        assertRejected("operation 1 targets child of operation 0:",
            new Insert( "foo.bar", NULL ),
            new Insert( "foo.bar.baz", NULL ));
    }

    @Test
    void testPatch_RemoveParentPathAfter() {
        assertRejected("operation 0 targets child of operation 1:",
            new Insert( "foo.bar", NULL ),
            new Remove( "foo" ) );
        assertRejected("operation 0 targets child of operation 1:",
            new Insert( "foo.bar.baz", NULL ),
            new Remove( "foo.bar" ) );
    }

    @Test
    void testPatch_InsertParentPathAfter() {
        assertRejected("operation 0 targets child of operation 1:",
            new Insert( "foo.bar.baz", NULL ),
            new Insert( "foo.bar", NULL ));
    }

    @Test
    void testPatch_RemoveParentPathRemoveAfter() {
        assertRejected("operation 0 targets child of operation 1:",
            new Remove( "foo.bar.baz" ),
            new Remove( "foo.bar" ) );
    }

    @Test
    void testPatch_RemoveParentPathRemoveBefore() {
        assertRejected("operation 1 targets child of operation 0:",
            new Remove( "foo.bar" ),
            new Remove( "foo.bar.baz" ));
    }

    @Test
    void testPatch_InsertArrayInsert() {
        assertAccepted(
            new Insert( "foo[0]", NULL ),
            new Insert( "foo[1]", NULL ));
    }

    @Test
    void testPatch_Misc() {
        assertAccepted(
            new Insert( "foo.x", NULL ),
            new Remove( "bar.x" ),
            new Insert( "foo.y", NULL ),
            new Remove( "fo" ),
            new Insert( "y", NULL ),
            new Insert( "que", NULL )
            );
    }

    @Test
    void testPatch_ObjectMerge() {
        assertAccepted(
            new Insert( "foo", JsonNode.of( """
                {"x": 1, "y": 2}""" ), true),
            new Insert( "foo.z", JsonNode.of( "3" ) ));

        assertAccepted(
            new Insert( "foo", JsonNode.of( """
                {"x": 1, "y": 2}""" ), true),
            new Insert( "foo", JsonNode.of( """
                {"z": 3, "zero": 0}""" ), true));

        assertRejected("operation 1 targets child of operation 0:",
            new Insert( "foo", JsonNode.of( """
                {"z": 1, "y": 2}""" ), true),
            new Insert( "foo.z", JsonNode.of( "3" ) ));

        assertRejected("operation 0 has same target as operation 1:",
            new Insert( "foo", JsonNode.of( """
                {"x": 1, "y": 2}""" ), true),
            new Insert( "foo", JsonNode.of( """
                {"x": 3, "zero": 0}""" ), true));
    }

    @Test
    void testMergeArrayInserts_Uniform() {
        assertEquals( List.of(
                new Insert("foo[0]", JsonNode.of( "[1,2,3,4]" ), true)),

            JsonNodeOperation.mergeArrayInserts( List.of(
                new Insert( "foo[0]", JsonNode.of( "1" ) ),
                new Insert( "foo[0]", JsonNode.of( "[2,3]" ), true ),
                new Insert( "foo[0]", JsonNode.of( "4" ) )
            ) ));
    }

    @Test
    void testMergeArrayInserts_Mixed() {
        assertEquals( List.of(
                new Remove( "x" ),
                new Insert( "foo[0]", JsonNode.of( "[1,2,3,4]" ), true ),
                new Insert( "bar", NULL ) ),

            JsonNodeOperation.mergeArrayInserts( List.of(
                new Remove( "x" ),
                new Insert( "foo[0]", JsonNode.of( "1" ) ),
                new Insert( "bar", NULL ),
                new Insert( "foo[0]", JsonNode.of( "[2,3]" ), true ),
                new Insert( "foo[0]", JsonNode.of( "4" ) )
            ) ) );
    }

    private static void assertRejected(String error, JsonNodeOperation... ops) {
        JsonPatchException ex = assertThrows( JsonPatchException.class,
            () -> JsonNodeOperation.checkPatch( List.of( ops ) ) );
        String msg = ex.getMessage();
        assertEquals( error, msg.substring( 0, Math.min( msg.length(), error.length() ) ), msg );
    }

    private static void assertAccepted( JsonNodeOperation... ops) {
        assertDoesNotThrow( () -> JsonNodeOperation.checkPatch( List.of(ops) ) );
    }
}
