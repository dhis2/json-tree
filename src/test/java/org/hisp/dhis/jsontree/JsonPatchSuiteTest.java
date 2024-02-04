package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Runs the JSON patch test suite provided by
 * <a href="https://github.com/json-patch/json-patch-tests">json-patch-tests</a>.
 *
 * @author Jan Bernitt
 */
class JsonPatchSuiteTest {

    public interface JsonPatchScenario extends JsonObject {

        default String comment() { return getString( "comment" ).string(); }
        default JsonMixed doc() { return get( "doc", JsonMixed.class); }
        default JsonList<JsonPatch> patch() { return getList( "patch", JsonPatch.class ); }
        default JsonMixed expected() { return get( "expected", JsonMixed.class ); }
        default String error() { return getString( "error" ).string(); }
        default boolean disabled() { return getBoolean( "disabled" ).booleanValue(false); }
    }

    @TestFactory
    List<DynamicTest> testScenarios() {
        return JsonMixed.of( Path.of("src/test/resources/json-patch-tests/tests.json") )
            .asList( JsonPatchScenario.class )
            .stream().map( scenario -> dynamicTest( scenario.comment(), () -> assertScenario( scenario ) ) )
            .toList();
    }

    private void assertScenario(JsonPatchScenario scenario) {
        String error = scenario.error();
        if ( error != null) {
            assertThrows( JsonPatchException.class, () -> scenario.doc().patch( scenario.patch() ) );
        } else {
            JsonValue patched = scenario.doc().patch( scenario.patch() );
            assertTrue( scenario.expected().equivalentTo( patched ),
                () -> "expected %s but was: %s".formatted( scenario.expected(), patched ) );
        }
    }
}
