package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.Validation.Rule;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.hisp.dhis.jsontree.Assertions.assertValidationError;

/**
 * Basic validation test for {@link JsonPatch} objects.
 */
class JsonPatchValidationTest {

    @Test
    void testAny_InvalidPath() {
        assertValidationError( """
            { "op": "remove", "path": "hello" }""", JsonPatch.class, Rule.PATTERN,
            "(\\/(([^\\/~])|(~[01]))*)", "hello");
    }

    @Test
    void testAny_MissingOp() {
        assertValidationError( """
            { "path": "/hello" }""", JsonPatch.class, Rule.REQUIRED, "op");
    }

    @Test
    void testAny_InvalidOp() {
        assertValidationError( """
            { "op": "update", "path": "/hello" }""", JsonPatch.class, Rule.ENUM,
            Set.of("ADD", "MOVE", "COPY", "REMOVE", "REPLACE", "TEST"), "update");
    }

    @Test
    void testAny_MissingPath() {
        assertValidationError( """
            { "op": "remove"}""", JsonPatch.class, Rule.REQUIRED, "path");
    }

    @Test
    void testAdd_MissingValue() {
        assertValidationError( """
            { "op": "add", "path": "/foo"}""", JsonPatch.class, Rule.DEPENDENT_REQUIRED,
            Map.of("op", "add"), Set.of("value"), Set.of("value"));
    }

    @Test
    void testReplace_MissingValue() {
        assertValidationError( """
            { "op": "replace", "path": "/foo"}""", JsonPatch.class, Rule.DEPENDENT_REQUIRED,
            Map.of("op", "replace"), Set.of("value"), Set.of("value"));
    }

    @Test
    void testTest_MissingValue() {
        assertValidationError( """
            { "op": "test", "path": "/foo"}""", JsonPatch.class, Rule.DEPENDENT_REQUIRED,
            Map.of("op", "test"), Set.of("value"), Set.of("value"));
    }

    @Test
    void testCopy_MissingFrom() {
        assertValidationError( """
            { "op": "copy", "path": "/foo"}""", JsonPatch.class, Rule.DEPENDENT_REQUIRED,
            Map.of("op", "copy"), Set.of("from"), Set.of("from"));
    }

    @Test
    void testMove_MissingFrom() {
        assertValidationError( """
            { "op": "move", "path": "/foo"}""", JsonPatch.class, Rule.DEPENDENT_REQUIRED,
            Map.of("op", "move"), Set.of("from"), Set.of("from"));
    }

    @Test
    void testMove_InvalidFrom() {
        assertValidationError( """
            { "op": "move", "from": "hello", "path":"/" }""", JsonPatch.class, Rule.PATTERN,
            "(\\/(([^\\/~])|(~[01]))*)", "hello");
    }
}
