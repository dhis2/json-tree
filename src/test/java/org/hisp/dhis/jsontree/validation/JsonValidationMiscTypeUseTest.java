package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hisp.dhis.jsontree.validation.Assertions.assertValidationError;

class JsonValidationMiscTypeUseTest {

    public interface JsonTypeUseExampleA extends JsonObject {

        @Validation( minItems = 1 )
        default List<@Validation( maxItems = 2 ) List<@Validation( pattern = ".es.*" ) String>> getData() {
            return getArray( "data" ).values( e -> List.of() );
        }
    }

    @Test
    void testTyeUse_MultiLevel() {
        assertValidationError( """
            { "data": [["yes", "hello"], []] }""", JsonTypeUseExampleA.class, Rule.PATTERN, ".es.*", "hello" );
        assertValidationError( """
            { "data": [["yes", "best", "rest"], []] }""", JsonTypeUseExampleA.class, Rule.MAX_ITEMS, 2, 3 );
        assertValidationError( """
            { "data": [] }""", JsonTypeUseExampleA.class, Rule.MIN_ITEMS, 1, 0 );
        assertValidationError( """
            { "data": null }""", JsonTypeUseExampleA.class, Rule.REQUIRED, "data" );
        assertValidationError( """
            {}""", JsonTypeUseExampleA.class, Rule.REQUIRED, "data" );
    }

}
