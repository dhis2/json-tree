package org.hisp.dhis.jsontree;

import java.util.List;

import static org.hisp.dhis.jsontree.JsonSchema.NodeType.STRING;

/**
 * Structure of a JSON schema document as described in <a href="https://json-schema.org/draft/2020-12">2020-12
 * dialect</a>.
 *
 * @author Jan Bernitt
 */
@SuppressWarnings( "java:S100" )
public interface JsonSchema {

    enum NodeType {
        NULL, BOOLEAN, STRING, NUMBER, INTEGER, ARRAY, OBJECT;

        boolean isSimple() {
            return this != ARRAY && this != OBJECT;
        }
    }

    /**
     * Structural Validation.
     * <p>
     * Defines properties that impose requirements for successful validation of an instance.
     */
    interface JsonValidation extends JsonObject {

        /*
        Validations for Any Instance Type
         */

        /**
         * The value MUST be either a string or an array. If it is an array, elements of the array MUST be strings and
         * MUST be unique.
         * <p>
         * String values MUST be one of the six primitive type ("null", "boolean", "object", "array", "number", or
         * "string"), or "integer" which matches any number with a zero fractional part.
         *
         * @return Validation succeeds if the type of the instance matches at least one of the given type.
         */
        @Validation( type = STRING, uniqueItems = true, minItems = 0, maxItems = 6, enumeration = NodeType.class )
        default List<NodeType> type() {
            return get( "type" ).toListFromVarargs( JsonString.class, str -> str.parsed( NodeType::valueOf ) );
        }

        /**
         * The value of this property MUST be an array. This array SHOULD have at least one element. Elements in the
         * array SHOULD be unique.
         *
         * @return Validation succeeds if the instance is equal to one of the elements in the given array.
         */
        default JsonArray $enum() {
            return getArray( "enum" );
        }

        /**
         * The value of this property MAY be of any type, including null.
         *
         * @return Validation succeeds if the instance is equal to this value.
         */
        default JsonValue $const() {
            return get( "const" );
        }

        /*
         Validations for Strings
         */

        /**
         * The value of this property MUST be a non-negative integer.
         *
         * @return A string instance is valid if its length is greater than, or equal to, this value.
         */
        @Validation( minimum = 0 )
        default int minLength() {
            return getNumber( "minLength" ).intValue( 0 );
        }

        /**
         * The value of this property MUST be a non-negative integer.
         *
         * @return A string instance is valid if its length is less than, or equal to, this value.
         */
        @Validation( minimum = 0 )
        default Integer maxLength() {
            return getNumber( "maxLength" ).integer();
        }

        /**
         * The value of this property MUST be a string. This string SHOULD be a valid regular expression, according to
         * the ECMA-262 regular expression dialect.
         *
         * @return A string instance is considered valid if the regular expression matches the instance successfully.
         */
        default String pattern() {
            return getString( "pattern" ).string();
        }

        /*
        Validations for Numbers
         */

        /**
         * @return Validation succeeds if the numeric instance is greater than or equal to the given number.
         */
        default Number minimum() {
            return getNumber( "minimum" ).number();
        }

        /**
         * @return Validation succeeds if the numeric instance is less than or equal to the given number.
         */
        default Number maximum() {
            return getNumber( "maximum" ).number();
        }

        /**
         * @return Validation succeeds if the numeric instance is greater than the given number.
         */
        default Number exclusiveMinimum() {
            return getNumber( "exclusiveMinimum" ).number();
        }

        /**
         * @return Validation succeeds if the numeric instance is less than the given number.
         */
        default Number exclusiveMaximum() {
            return getNumber( "exclusiveMaximum" ).number();
        }

        /**
         * The value of "multipleOf" MUST be a number, strictly greater than 0.
         *
         * @return A numeric instance is valid only if division by this value results in an integer.
         */
        default Number multipleOf() {
            return getNumber( "multipleOf" ).number();
        }

        /*
        Validations for Arrays
         */

        /**
         * The value of this property MUST be a non-negative integer.
         *
         * @return An array instance is valid if its size is greater than, or equal to, the value of this keyword.
         */
        @Validation( minimum = 0 )
        default int minItems() {
            return getNumber( "minItems" ).intValue( 0 );
        }

        /**
         * The value of this property MUST be a non-negative integer.
         *
         * @return An array instance is valid if its size is less than, or equal to, the value of this keyword.
         */
        @Validation( minimum = 0 )
        default Integer maxItems() {
            return getNumber( "maxItems" ).integer();
        }

        /**
         * @return If true, an array instance is valid if all of its elements are unique.
         */
        default boolean uniqueItems() {
            return getBoolean( "uniqueItems" ).booleanValue( false );
        }

        /**
         * The value of this property MUST be a non-negative integer.
         * <p>
         * If "contains" is not present within the same schema object, then this keyword has no effect.
         * <p>
         * An instance array is valid against "minContains" in two ways, depending on the form of the annotation result
         * of an adjacent "contains" property. The first way is if the annotation result is an array and the length of
         * that array is greater than or equal to the "minContains" value. The second way is if the annotation result is
         * a boolean "true" and the instance array length is greater than or equal to the "minContains" value.
         * <p>
         * A value of 0 is allowed, but is only useful for setting a range of occurrences from 0 to the value of
         * "maxContains". A value of 0 causes "minContains" and "contains" to always pass validation (but validation can
         * still fail against a "maxContains" keyword).
         *
         * @return The number of times that the "contains" property matches must be greater than or equal to the given
         * integer.
         */
        @Validation( minimum = 0 )
        default int minContains() {
            return getNumber( "minContains" ).intValue( 1 );
        }

        /**
         * The value of this property MUST be a non-negative integer.
         * <p>
         * If "contains" is not present within the same schema object, then this keyword has no effect.
         * <p>
         * An instance array is valid against "maxContains" in two ways, depending on the form of the annotation result
         * of an adjacent "contains" property. The first way is if the annotation result is an array and the length of
         * that array is less than or equal to the "maxContains" value. The second way is if the annotation result is a
         * boolean "true" and the instance array length is less than or equal to the "maxContains" value.
         *
         * @return The number of times that the "contains" property matches must be less than or equal to the given
         * integer.
         */
        @Validation( minimum = 0 )
        default Integer maxContains() {
            return getNumber( "maxContains" ).integer();
        }

        /*
        Validations for Objects
         */

        /**
         * The value of this property MUST be a non-negative integer.
         *
         * @return An object instance is valid if its number of properties is greater than, or equal to the given
         * integer.
         */
        @Validation( minimum = 0 )
        default int minProperties() {
            return getNumber( "minProperties" ).intValue( 0 );
        }

        /**
         * The value of this property MUST be a non-negative integer.
         *
         * @return An object instance is valid if its number of properties is less than, or equal to the given integer.
         */
        @Validation( minimum = 0 )
        default Integer maxProperties() {
            return getNumber( "maxProperties" ).integer();
        }

        /**
         * The value of this property MUST be an array. Elements of this array, if any, MUST be strings, and MUST be
         * unique.
         *
         * @return An object instance is valid if every item in the array is the name of a property in the instance.
         */
        @Validation( uniqueItems = true )
        default List<String> required() {
            return getArray( "required" ).stringValues();
        }

        /**
         * The value of this property MUST be an object. Properties in this object, if any, MUST be arrays. Elements in
         * each array, if any, MUST be strings, and MUST be unique.
         * <p>
         * This specifies properties that are required if a specific other property is present. Their requirement is
         * dependent on the presence of the other property.
         * <p>
         *
         * @return Validation succeeds if, for each name that appears in both the instance and as a name within the map,
         * every item in the corresponding list is also the name of a property in the instance.
         */
        default JsonMultiMap<JsonString> dependentRequired() {
            return getMultiMap( "dependentRequired", JsonString.class );
        }
    }

    /**
     * Defines properties for general-purpose annotations that provide commonly used information for documentation and
     * user interface display purposes.
     */
    interface JsonMetaData extends JsonObject {

        /**
         * @return A preferably short description about the purpose of the instance described by the schema.
         */
        default String title() {
            return getString( "title" ).string();
        }

        /**
         * @return An explanation about the purpose of the instance described by the schema.
         */
        default String description() {
            return getString( "description" ).string();
        }

        /**
         * @return A default JSON value associated with a particular schema.
         */
        default JsonValue $default() {
            return get( "default" );
        }

        /**
         * @return A sample JSON values associated with a particular schema, for the purpose of illustrating usage.
         */
        default JsonArray examples() {
            return getArray( "examples" );
        }

        /**
         * @return The value of the instance is managed exclusively by the owning authority, and attempts by an
         * application to modify the value of this property are expected to be ignored or rejected by that owning
         * authority.
         */
        default boolean readOnly() {
            return getBoolean( "readOnly" ).booleanValue( false );
        }

        /**
         * @return The value is never present when the instance is retrieved from the owning authority.
         */
        default boolean writeOnly() {
            return getBoolean( "writeOnly" ).booleanValue( false );
        }

        /**
         * @return Applications should refrain from using the declared property.
         */
        default boolean deprecated() {
            return getBoolean( "deprecated" ).booleanValue( false );
        }
    }

}
