package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.Maybe;
import org.hisp.dhis.jsontree.internal.Surly;

import java.io.Serializable;
import java.lang.annotation.Annotation;
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
 * @see Required
 *
 * @author Jan Bernitt
 * @see org.hisp.dhis.jsontree.Validator
 * @since 0.11
 */
@Target( { ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.TYPE_USE } )
@Retention( RetentionPolicy.RUNTIME )
public @interface Validation {

    enum YesNo {
        NO, YES, AUTO;

        public boolean isYes() {
            return this == YES;
        }

        public boolean isAuto() {
            return this == AUTO;
        }
    }

    enum Rule {
        // any values
        TYPE, ENUM, CUSTOM,

        // string values
        MIN_LENGTH, MAX_LENGTH, PATTERN,

        // number values
        MINIMUM, MAXIMUM, EXCLUSIVE_MINIMUM, EXCLUSIVE_MAXIMUM, MULTIPLE_OF,

        // array values
        MIN_ITEMS, MAX_ITEMS, UNIQUE_ITEMS,

        // object values
        MIN_PROPERTIES, MAX_PROPERTIES, REQUIRED, DEPENDENT_REQUIRED
    }

    /**
     * In line with the JSON schema validation specification.
     */
    enum NodeType {
        NULL, BOOLEAN, STRING, NUMBER, INTEGER, ARRAY, OBJECT;

        @Surly
        public static NodeType of( @Maybe JsonNodeType type ) {
            if ( type == null ) return NULL;
            return switch ( type ) {
                case OBJECT -> OBJECT;
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
    @FunctionalInterface
    interface Validator {

        /**
         * Adds an error to the provided callback in case the provided value is not valid according to this check.
         *
         * @param value    the value to check
         * @param addError callback to add errors
         */
        void validate( JsonMixed value, Consumer<Error> addError );
    }

    record Error(Rule rule, String path, JsonValue value, String template, List<Object> args) implements Serializable {

        public static Error of( Rule rule, JsonValue value, String template, Object... args ) {
            return new Error( rule, value.path(), value, template, List.of( args ) );
        }

        @Override
        public String toString() {
            return "%s %s (%s)".formatted( path, template.formatted( args.toArray() ), rule );
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
     * Cannot be used in combination with {@link #oneOfValues()} as it would be unclear if those values apply to the
     * array, the elements or both.
     *
     * @return when {@link YesNo#YES}, the given {@link #type()} can occur once (as simple type value) or many times as
     * an array of such values.
     */
    YesNo varargs() default YesNo.AUTO;

    /**
     * Corresponds to JSON schema validation specified as {@code enum}.
     *
     * @return value must be equal to one of the given JSON values
     */
    String[] oneOfValues() default {};

    /**
     * Corresponds to JSON schema validation specified as {@code enum}.
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
     * To describe which properties are in a dependency relation with each other properties can be assigned group names.
     * One or more members of a group have the role of a trigger while the others are the ones that are required
     * depending on the trigger. This property defines the groups for the annotated property and its role using suffixes
     * as described below.
     * <p>
     * A trigger uses the suffix {@code !} to trigger when present, {@code ?} to trigger when absent.
     * <p>
     * Multiple triggers in a group always combine with AND logic (all need to present/absent). For a group with
     * multiple {@code !} triggers all must be present to trigger. For a group with multiple {@code ?} triggers all must
     * be absent to trigger. For a group with both {@code !} and {@code ?} triggers both conditions must be met to
     * trigger.
     * <p>
     * If none of the properties in a group is marked any of the properties makes all others in the group required.
     * <p>
     * In addition, a property that is dependent required (not a trigger) can use the {@code ^} suffix if it is
     * mutual exclusive to all other required properties that are marked equally.
     *
     * @return the names of the groups the annotated property belongs to
     */
    String[] dependentRequired() default {};
}
