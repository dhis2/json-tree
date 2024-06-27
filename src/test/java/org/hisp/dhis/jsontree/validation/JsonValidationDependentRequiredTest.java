package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.Required;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.hisp.dhis.jsontree.Assertions.assertValidationError;
import static org.hisp.dhis.jsontree.Validation.YesNo.YES;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests Validation of the @{@link Validation#dependentRequired()} property.
 *
 * @author Jan Bernitt
 */
class JsonValidationDependentRequiredTest {

    public interface JsonDependentRequiredExampleA extends JsonObject {

        @Validation( dependentRequired = "name" )
        default String firstName() {
            return getString( "firstName" ).string();
        }

        @Validation( dependentRequired = "name" )
        default String lastName() {
            return getString( "lastName" ).string();
        }
    }

    public interface JsonDependentRequiredExampleB extends JsonObject {

        @Validation( dependentRequired = "city+zip!" )
        default String zip() {
            return getString( "zip" ).string();
        }

        @Validation( dependentRequired = "city+zip" )
        default String city() {
            return getString( "city" ).string();
        }
    }

    public interface JsonDependentRequiredExampleC extends JsonObject {

        @Validation( dependentRequired = { "street+no?", "box" } )
        default String box() {
            return getString( "box" ).string();
        }

        @Validation( dependentRequired = { "street+no", "box?" } )
        default String street() {
            return getString( "street" ).string();
        }

        @Validation( dependentRequired = { "street+no" } )
        default String no() {
            return getString( "no" ).string();
        }
    }

    public interface JsonDependentRequiredExampleD extends JsonObject {

        @Required
        @Validation(dependentRequired = "=update")
        default String getOp() { return getString( "op" ).string(); }

        @Validation(dependentRequired = "update")
        default String getPath() { return getString( "path" ).string(); }

        @Validation(dependentRequired = "update", acceptNull = YES)
        default JsonMixed getValue() { return get( "value", JsonMixed.class ); }
    }

    @Test
    void testDependentRequired_Codependent() {
        assertValidationError( """
                {"firstName":"peter"}""", JsonDependentRequiredExampleA.class, Rule.DEPENDENT_REQUIRED,
            Set.of( "firstName", "lastName" ), Set.of( "lastName" ) );
        assertValidationError( """
                {"lastName":"peter"}""", JsonDependentRequiredExampleA.class, Rule.DEPENDENT_REQUIRED,
            Set.of( "firstName", "lastName" ), Set.of( "firstName" ) );
        assertValidationError( """
                {"lastName":"peter", "firstName": null}""", JsonDependentRequiredExampleA.class, Rule.DEPENDENT_REQUIRED,
            Set.of( "firstName", "lastName" ), Set.of( "firstName" ) );

        assertDoesNotThrow( () -> JsonMixed.of( """
            {}""" ).validate( JsonDependentRequiredExampleA.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"firstName":"Joe", "lastName":"Dow"}""" ).validate( JsonDependentRequiredExampleA.class ) );
    }

    @Test
    void testDependentRequired_PresentDependent() {
        assertValidationError( """
                {"zip":"12345"}""", JsonDependentRequiredExampleB.class, Rule.DEPENDENT_REQUIRED,
            Set.of( "zip" ), Set.of( "city" ), Set.of( "city" ) );

        assertDoesNotThrow( () -> JsonMixed.of( """
            {}""" ).validate( JsonDependentRequiredExampleB.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"city":"Denver"}""" ).validate( JsonDependentRequiredExampleB.class ) );
    }

    @Test
    void testDependentRequired_AbsentDependent() {
        assertValidationError( """
                {"street":"main"}""", JsonDependentRequiredExampleC.class, Rule.DEPENDENT_REQUIRED,
            Set.of( "box" ), Set.of( "street", "no" ), Set.of( "no" ) );

        assertDoesNotThrow( () -> JsonMixed.of( """
            {"box": "234X"}""" ).validate( JsonDependentRequiredExampleC.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"street":"main", "no":"11"}""" ).validate( JsonDependentRequiredExampleC.class ) );
    }

    @Test
    void testDependentRequired_AllowNull() {
        assertValidationError( """
                {"op":"update"}""", JsonDependentRequiredExampleD.class, Rule.DEPENDENT_REQUIRED,
            Map.of("op", "update"), Set.of("value", "path"), Set.of("value", "path") );
        assertValidationError( """
                {"op":"update", "value": null}""", JsonDependentRequiredExampleD.class, Rule.DEPENDENT_REQUIRED,
            Map.of("op", "update"), Set.of("value", "path"), Set.of("path") );

        assertDoesNotThrow( () -> JsonMixed.of( """
            {"op":"other"}""" ).validate( JsonDependentRequiredExampleD.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"op":"update", "path": "x", "value": null}""" ).validate( JsonDependentRequiredExampleD.class ) );
        assertDoesNotThrow( () -> JsonMixed.of( """
            {"op":"update", "path": "x", "value": 1}""" ).validate( JsonDependentRequiredExampleD.class ) );
    }

}
