package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.util.Set;

import static org.hisp.dhis.jsontree.Assertions.assertValidationError;
import static org.hisp.dhis.jsontree.Validation.YesNo.YES;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Tests Validation of the @{@link Validation#enumeration()} and @{@link Validation#oneOfValues()} properties.
 *
 * @author Jan Bernitt
 */
class JsonValidationEnumTest {

    public interface JsonEnumExampleA extends JsonObject {

        @Validation(oneOfValues = {"hello", "world"})
        default String getType() { return getString( "type" ).string(); }
    }

    public interface JsonEnumExampleB extends JsonObject {

        @Validation(oneOfValues = {"\"yes\"", "true", "1", "\"no\"", "false", "0"})
        default JsonMixed isOn() { return get( "on", JsonMixed.class ); }
    }

    public interface JsonEnumExampleC extends JsonObject {

        @Validation(enumeration = ElementType.class )
        default String getType() { return getString( "type" ).string(); }
    }

    public interface JsonEnumExampleD extends JsonObject {

        @Validation(enumeration = ElementType.class, caseInsensitive = YES)
        default String getType() { return getString( "type" ).string(); }
    }

    @Test
    void testEnum_OneOfValuesPlain_OK() {
        assertDoesNotThrow( () -> JsonMixed.of("""
            {"type": "hello"}""").validate( JsonEnumExampleA.class )  );
        assertDoesNotThrow( () -> JsonMixed.of("""
            {"type": "world"}""").validate( JsonEnumExampleA.class )  );
    }

    @Test
    void testEnum_OneOfValuesPlain_Invalid() {
        assertValidationError( """
            {"type": "joe"}""", JsonEnumExampleA.class, Rule.ENUM, Set.of("hello", "world"), "joe" );
        assertValidationError( """
            {"type": "HELLO"}""", JsonEnumExampleA.class, Rule.ENUM, Set.of("hello", "world"), "HELLO" );
    }

    @Test
    void testEnum_OneOfValuesMixed_OK() {
        assertDoesNotThrow( () -> JsonMixed.of("""
            {"on": 1}""").validate( JsonEnumExampleB.class )  );
        assertDoesNotThrow( () -> JsonMixed.of("""
            {"on": true}""").validate( JsonEnumExampleB.class )  );
        assertDoesNotThrow( () -> JsonMixed.of("""
            {"on": "yes"}""").validate( JsonEnumExampleB.class )  );
    }

    @Test
    void testEnum_OneOfValuesMixed_Invalid() {
        assertValidationError( """
            {"on": "1"}""", JsonEnumExampleB.class, Rule.ENUM,
            Set.of("false", "1", "0", "true", "\"yes\"", "\"no\""), "\"1\"" );
    }

    @Test
    void testEnum_Enumeration_OK() {
        assertDoesNotThrow( () -> JsonMixed.of("""
            {"type": "TYPE"}""").validate( JsonEnumExampleC.class )  );
        assertDoesNotThrow( () -> JsonMixed.of("""
            {"type": "METHOD"}""").validate( JsonEnumExampleC.class )  );
    }

    @Test
    void testEnum_Enumeration_Invalid() {
        Set<String> expectedValidValues = Set.of( "ANNOTATION_TYPE", "RECORD_COMPONENT", "TYPE_PARAMETER", "MODULE",
            "TYPE", "TYPE_USE", "PARAMETER", "CONSTRUCTOR", "FIELD", "METHOD", "LOCAL_VARIABLE", "PACKAGE" );
        assertValidationError( """
            {"type": "type"}""", JsonEnumExampleC.class, Rule.ENUM, expectedValidValues, "type" );
        assertValidationError( """
            {"type": "oh no"}""", JsonEnumExampleC.class, Rule.ENUM, expectedValidValues, "oh no" );
    }

    @Test
    void testEnum_EnumerationCaseInsensitive_OK() {
        assertDoesNotThrow( () -> JsonMixed.of("""
            {"type": "tyPe"}""").validate( JsonEnumExampleD.class )  );
        assertDoesNotThrow( () -> JsonMixed.of("""
            {"type": "Field"}""").validate( JsonEnumExampleD.class )  );
    }

    @Test
    void testEnum_EnumerationCaseInsensitive_Invalid() {
        Set<String> expectedValidValues = Set.of( "ANNOTATION_TYPE", "RECORD_COMPONENT", "TYPE_PARAMETER", "MODULE",
            "TYPE", "TYPE_USE", "PARAMETER", "CONSTRUCTOR", "FIELD", "METHOD", "LOCAL_VARIABLE", "PACKAGE" );
        assertValidationError( """
            {"type": "RECORD"}""", JsonEnumExampleD.class, Rule.ENUM, expectedValidValues, "RECORD" );
        assertValidationError( """
            {"type": "oh no"}""", JsonEnumExampleD.class, Rule.ENUM, expectedValidValues, "oh no" );
    }
}
