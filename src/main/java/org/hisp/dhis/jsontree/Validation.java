package org.hisp.dhis.jsontree;

import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Structural Validations as defined by the JSON schema specification <a
 * href="https://json-schema.org/draft/2020-12">2020-12 dialect</a>.
 * <p>
 * Used on methods to add validation to the method return type.
 * <p>
 * Used on annotation types to define meta annotations for validations, for example a {@code @NonNegativeInteger}
 * annotation which would be annotated {@code @Validation(type=INTEGER, minimum=0)}.
 *
 * @author Jan Bernitt
 * @since 0.10
 */
@Inherited
@Target( { ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE } )
@Retention( RetentionPolicy.RUNTIME )
public @interface Validation {

    enum NodeType {NULL, BOOLEAN, OBJECT, ARRAY, STRING, NUMBER, INTEGER}

    /**
     * When annotated on a method this is assumed to be the property specific schema part.
     * <p>
     * When annotated on a type this is assumed to be an entire JSON schema definition.
     *
     * @return A JSON schema definition to extract all validations
     */
    String schema() default "";

    /**
     * If multiple types are given the value must be one of them.
     * <p>
     * An integer is any number with a fraction of zero. This means it can have a fraction part as long as that part is
     * zero.
     *
     * @return value must have one of the given JSON node types
     */
    NodeType[] type() default {};

    /**
     * The JSON values do not need quoting for strings if the string starts with a letter.
     *
     * @return value must be equal to one of the given JSON values
     */
    String[] values() default {};

    /**
     * @return value must be equal to one of the value of the given enum
     */
    Class<? extends Enum> enumeration() default Enum.class;

    /*
     Validations for Strings
     */

    /**
     * @return string value must not be shorter than the given minimum length
     */
    int minLength() default -1;

    /**
     * @return string value must not be longer than the given maximum length
     */
    int maxLength() default -1;

    /**
     * @return string value must match the given regex pattern
     */
    String pattern() default "";

    /*
    Validations for Numbers
     */

    /**
     * @return number value must be equal to or larger than the given value
     */
    double minimum() default Double.NaN;

    /**
     * @return number value mst be equal to or less than the given value
     */
    double maximum() default Double.NaN;

    /**
     * @return number value must be larger than the given value
     */
    double exclusiveMinimum() default Double.NaN;

    /**
     * @return number value must be smaller than the given value
     */
    double exclusiveMaximum() default Double.NaN;

    /**
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
     *
     * @return array value must have at least the given number of elements
     */
    int minItems() default -1;

    /**
     * When used on a method the validation applies to the return type array,
     * <p>
     * when used on a type the validation applies to the annotated type array.
     *
     * @return array value must have at most the given number of elements
     */
    int maxItems() default -1;

    /**
     * When used on a method the validation applies to the return type array,
     * <p>
     * when used on a type the validation applies to the annotated type array.
     *
     * @return all elements in the array value must be unique
     */
    boolean uniqueItems() default false;

    int minContains() default -1;

    int maxContains() default -1;

    /*
    Validations for Objects
     */

    /**
     * When used on a method the validation applies to the return type object,
     * <p>
     * when used on a type the validation applies to the annotated type object.
     *
     * @return object must have at least the given number of properties
     */
    int minProperties() default -1;

    /**
     * When used on a method the validation applies to the return type object,
     * <p>
     * when used on a type the validation applies to the annotated type object.
     *
     * @return object must have at most the given number of properties
     */
    int maxProperties() default -1;

    /**
     * @return parent object must have the annotated property
     */
    boolean required() default false;

    /**
     * @return parent object must have all given required properties in case the annotated property exist
     */
    String[] dependentRequired() default {};
}
