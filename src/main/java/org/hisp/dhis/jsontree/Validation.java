package org.hisp.dhis.jsontree;

import java.io.Serializable;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.List;
import java.util.function.Consumer;

/**
 * Structural Validations as defined by the JSON schema specification <a
 * href="https://json-schema.org/draft/2020-12">2020-12 dialect</a>.
 * <p>
 * Used on methods to add validation to the method return type.
 * <p>
 * Used on a {@link JsonValue} subtype to add validation to any of its usage.
 * <p>
 * <h3>Meta-Annotations</h3>
 * Used on annotation type to define meta annotations for validations, for example a {@code @NonNegativeInteger}
 * annotation which would be annotated {@code @Validation(type=INTEGER, minimum=0)}.
 * <p>
 * <h3>Priority</h3>
 * Order of source priority lowest to highest:
 * <ol>
 *     <li>value type class (using the Java type information; only if no annotation is present on type)</li>
 *     <li>Meta-annotation(s) on value type class</li>
 *     <li>{@link Validation} annotation on value type class</li>
 *     <li>Meta-annotation(s) on property method</li>
 *     <li>{@link Validation} annotation on property method</li>
 *     <li>Meta-annotation(s) on property method return type (type use)</li>
 *     <li>{@link Validation} annotation on property method return type (type use)</li>
 * </ol>
 * Sources with higher priority override values of sources with lower priority unless the higher priority value is "undefined".
 *
 *
 *
 * @see org.hisp.dhis.jsontree.Validator
 *
 * @author Jan Bernitt
 * @since 0.11
 */
@Inherited
@Target( { ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.TYPE_USE } )
@Retention( RetentionPolicy.RUNTIME )
public @interface Validation {

    enum YesNo {NO, YES, AUTO;
        public boolean isYes() { return this == YES; }

        public boolean isAuto() {
            return this == AUTO;
        }
    }

    enum Rule {
        // any values
        TYPE, ENUM,

        // string values
        MIN_LENGTH, MAX_LENGTH, PATTERN,

        // number values
        MINIMUM, MAXIMUM, EXCLUSIVE_MINIMUM, EXCLUSIVE_MAXIMUM, MULTIPLE_OF,

        // array values
        MIN_ITEMS, MAX_ITEMS, UNIQUE_ITEMS, MIN_CONTAINS, MAX_CONTAINS,

        // object values
        MIN_PROPERTIES, MAX_PROPERTIES, REQUIRED, DEPENDENT_REQUIRED
    }

    /**
     * In line with the JSON schema validation specification.
     */
    enum NodeType {
        NULL, BOOLEAN, STRING, NUMBER, INTEGER, ARRAY, OBJECT;

        public static NodeType of( JsonNodeType type ) {
            return switch ( type ) {
                case OBJECT -> OBJECT ;
                case ARRAY -> ARRAY;
                case STRING -> STRING;
                case BOOLEAN -> BOOLEAN;
                case NULL -> NULL;
                case NUMBER -> NUMBER;
            };
        }
    }

    /**
     * Value validation
     */
    interface Validator {

        /**
         * Adds an error to the provided callback in case the provided value is not valid according to this check.
         *
         * @param value    the value to check
         * @param addError callback to add errors
         */
        void validate( JsonMixed value, Consumer<Error> addError );
    }

    record Error(Enum<?> rule, String path, JsonMixed value, List<Object> args) implements Serializable {

        public static Error of( Enum<?> rule, JsonMixed value, Object... args ) {
            return new Error( rule, value.node().getPath(), value, List.of( args ) );
        }
    }

    /**
     * Used to mark properties that should not be validated. By default, all properties are validated.
     */
    @Target( { ElementType.METHOD, ElementType.TYPE } )
    @Retention( RetentionPolicy.RUNTIME ) @interface Ignore {}

    /**
     * Validations that apply to array elements or object member values.
     * <p>
     * To build multi-level validations use validation annotated item types or create specific validation annotations
     * for the items which in term can have an {@linkplain Items} annotation.
     */
    @Target( { ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE } )
    @Retention( RetentionPolicy.RUNTIME ) @interface Items {

        Validation value();
    }

    /**
     * When annotated on a method this is assumed to be the property specific schema part.
     * <p>
     * When annotated on a type this is assumed to be an entire JSON schema definition.
     *
     * @return A JSON schema definition to extract all validations
     */
    String schema() default "";

    /**
     * If multiple type are given the value must be one of them.
     * <p>
     * An {@link NodeType#INTEGER} is any number with a fraction of zero. This means it can have a fraction part as long
     * as that part is zero.
     *
     * @return value must have one of the given JSON node types
     */
    NodeType[] type() default {};

    /**
     * A property marked as varargs will allow {@link NodeType#ARRAY} to occur, each element then is validated against
     * the present simple value validations.
     * <p>
     * In addition, all given array requirements must be met.
     * <p>
     * {@link YesNo#AUTO} is used when inferring validation from the return type for types that are
     * {@link java.util.Collection}s of simple types.
     * <p>
     * Cannot be used in combination with {@link #values()} as it would be unclear if those values
     * apply to the array, the elements or both.
     *
     * @return when {@link YesNo#YES}, the given {@link #type()} can occur once (as simple type value) or many times as
     * an array of such values.
     */
    YesNo varargs() default YesNo.AUTO;

    /**
     * The JSON values do not need quoting for strings if the string starts with a letter.
     * <p>
     * When multiple annotations are present defining a set of values the union of those sets is used.
     *
     * @return value must be equal to one of the given JSON values
     */
    String[] values() default {};

    /**
     * If multiple annotations are present defining an enumeration type they have to be the same or an
     * {@link IllegalStateException} is thrown to indicate the programming error.
     *
     * @return value must be equal to one of the value of the given enum
     */
    Class<? extends Enum> enumeration() default Enum.class;

    /*
     Validations for Strings
     */

    /**
     * If multiple annotations are present the largest of any given minimum is used.
     *
     * @return string value must not be shorter than the given minimum length
     */
    int minLength() default -1;

    /**
     * If multiple annotations are present the smallest of any given maximum is used.
     *
     * @return string value must not be longer than the given maximum length
     */
    int maxLength() default -1;

    /**
     * If multiple annotations are present all given patterns must match.
     *
     * @return string value must match the given regex pattern
     */
    String pattern() default "";

    /*
    Validations for Numbers
     */

    /**
     * If multiple annotations are present the largest of any given minimum is used.
     *
     * @return number value must be equal to or larger than the given value
     */
    double minimum() default Double.NaN;

    /**
     * If multiple annotations are present the smallest of any given maximum is used.
     *
     * @return number value mst be equal to or less than the given value
     */
    double maximum() default Double.NaN;

    /**
     * If multiple annotations are present the largest of any given minimum is used.
     *
     * @return number value must be larger than the given value
     */
    double exclusiveMinimum() default Double.NaN;

    /**
     * If multiple annotations are present the smallest of any given maximum is used.
     *
     * @return number value must be smaller than the given value
     */
    double exclusiveMaximum() default Double.NaN;

    /**
     * If multiple annotations are present the smallest of any given factor is used.
     *
     * @return number value must be divisible by the given value without rest
     */
    double multipleOf() default Double.NaN;

    /*
    Validations for Arrays
     */

    /**
     * When used on a method the validation applies to the return type array,
     * <p>
     * when used on a type the validation applies to the annotated type array.
     * <p>
     * If multiple annotations are present the largest of any given minimum is used.
     *
     * @return array value must have at least the given number of elements
     */
    int minItems() default -1;

    /**
     * When used on a method the validation applies to the return type array,
     * <p>
     * when used on a type the validation applies to the annotated type array.
     * <p>
     * If multiple annotations are present the smallest of any given maximum is used.
     *
     * @return array value must have at most the given number of elements
     */
    int maxItems() default -1;

    /**
     * When used on a method the validation applies to the return type array,
     * <p>
     * when used on a type the validation applies to the annotated type array.
     * <p>
     * If multiple annotations are present the property dependentRequires unique items if any of them specifies it.
     *
     * @return all elements in the array value must be unique
     */
    YesNo uniqueItems() default YesNo.AUTO;

    /*
    Validations for Objects
     */

    /**
     * When used on a method the validation applies to the return type object,
     * <p>
     * when used on a type the validation applies to the annotated type object.
     * <p>
     * If multiple annotations are present the largest of any given minimum is used.
     *
     * @return object must have at least the given number of properties
     */
    int minProperties() default -1;

    /**
     * When used on a method the validation applies to the return type object,
     * <p>
     * when used on a type the validation applies to the annotated type object.
     * <p>
     * If multiple annotations are present the smallest of any given maximum is used.
     *
     * @return object must have at most the given number of properties
     */
    int maxProperties() default -1;

    /**
     * When set to AUTO any property using a Java primitive type is required.
     * <p>
     * If multiple annotations are present with differing value YES takes precedence over NO, both take precedence over
     * AUTO.
     *
     * @return parent object must have the annotated property
     */
    YesNo required() default YesNo.AUTO;

    /**
     * This works closely together with {@link #groups()}.
     *
     * @return then name of the group(s) of properties that are required together. OBS! These are not the names of the
     * properties but made up names for groups.
     */
    String[] dependentRequired() default {};

    /**
     * A group describes a set of properties that are related in a way. Group names are local to the Java type defining
     * the groups member properties.
     * <p>
     * A property can be member in multiple groups.
     * <p>
     * A group name may be marked to define the role the property has in the group:
     * <ul>
     *     <li>{@code group!}: in the named group when the annotated property is present the non-marked members must be present</li>
     *     <li>{@code group?}: in the named group when the annotated property is missing the non-marked members must be present</li>
     * </ul>
     * If multiple members mark the same group with {@code !} all of the properties with a mark must be present to trigger the group.
     * <p>
     * If multiple members mark the same group with {@code ?} all of the properties with a mark must be absent to trigger the group.
     * <p>
     * If members of the same group are marked with both {@code !} and {@code ?} both conditions must be met to activate the group validation.
     * <p>
     * If none of the properties in a group is marked any of the properties makes all others in the group required.
     * <p>
     * If multiple groups sources are present the property becomes member of the union of all groups.
     *
     * @return the names of the groups the annotated property belongs to
     */
    String[] groups() default {};
}
