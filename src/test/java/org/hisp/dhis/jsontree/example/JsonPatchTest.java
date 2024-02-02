package org.hisp.dhis.jsontree.example;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.Required;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.Map;
import java.util.Set;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.hisp.dhis.jsontree.Assertions.assertValidationError;
import static org.hisp.dhis.jsontree.Validation.YesNo.YES;

/**
 * A test that shows how the JSON patch standard could be modeled.
 */
class JsonPatchTest {

    public enum Op { ADD, REMOVE, REPLACE, COPY, MOVE, TEST }

    @Retention( RUNTIME )
    @Target( METHOD )
    @Validation(pattern = "(\\/(([^\\/~])|(~[01]))*)")
    @interface JsonPointer {}

    public interface JsonPatch extends JsonObject {

        @Required
        @Validation(dependentRequired = {"=add", "=replace", "=copy", "=move", "=test"}, caseInsensitive = YES)
        default Op getOperation() {
            return getString( "op" ).parsed( Op::valueOf );
        }

        @JsonPointer
        @Required
        default String getPath() {
            return getString( "path" ).string();
        }

        @Validation(dependentRequired = {"add", "replace", "test"})
        default JsonMixed getValue() {
            return get( "value", JsonMixed.class );
        }

        @JsonPointer
        @Validation(dependentRequired = {"copy", "move"})
        default String getFrom() {
            return getString( "from" ).string();
        }
    }

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
