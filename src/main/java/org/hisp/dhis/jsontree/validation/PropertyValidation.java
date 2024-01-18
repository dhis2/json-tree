package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.Validator;
import org.hisp.dhis.jsontree.Validation.YesNo;
import org.hisp.dhis.jsontree.internal.Maybe;
import org.hisp.dhis.jsontree.internal.Surly;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static java.lang.Double.isNaN;
import static java.util.stream.Collectors.toSet;

/**
 * A declarative model or description of what validation rules to check.
 *
 * @param anyOfTypes the node types allowed/expected
 * @param values     general validations that apply to any node type
 * @param strings    validations that apply to string nodes
 * @param numbers    validations that apply to number nodes
 * @param arrays     validations that apply to array nodes
 * @param objects    validations that apply to object nodes
 * @param items      validations that apply to array elements or object member values (map use)
 */
record PropertyValidation(
    @Surly Set<NodeType> anyOfTypes,
    @Maybe ValueValidation values,
    @Maybe StringValidation strings,
    @Maybe NumberValidation numbers,
    @Maybe ArrayValidation arrays,
    @Maybe ObjectValidation objects,
    @Maybe PropertyValidation items
    //TODO maybe add a Map<Rule, Set<Class<?>>> origin,
    // which remembers where (annotation or validators) a validation originates from
    // but this is difficult to keep accurate with the overlay
) {

    /**
     * Layers the provided validations on top of this. This means they take precedence unless they are defined off.
     *
     * @param with the validations that take precedence of this
     * @return A new node validations with all validations that took precedence from the provided parameter and this
     * node validations acting as fallback when a validation is defined off
     */
    @Surly
    PropertyValidation overlay( @Maybe PropertyValidation with ) {
        if ( with == null ) return this;
        return new PropertyValidation(
            overlayC( anyOfTypes, with.anyOfTypes ),
            values == null ? with.values : values.overlay( with.values ),
            strings == null ? with.strings : strings.overlay( with.strings ),
            numbers == null ? with.numbers : numbers.overlay( with.numbers ),
            arrays == null ? with.arrays : arrays.overlay( with.arrays ),
            objects == null ? with.objects : objects.overlay( with.objects ),
            items == null ? with.items : items.overlay( with.items )
        );
    }

    @Surly
    PropertyValidation withItems( @Maybe PropertyValidation items ) {
        if ( items == null && this.items == null ) return this;
        return new PropertyValidation( anyOfTypes, values, strings, numbers, arrays, objects, items );
    }

    @Surly
    PropertyValidation withCustoms( @Surly List<Validator> validators ) {
        if ( validators.isEmpty() && (values == null || values.customs.isEmpty()) ) return this;
        ValueValidation newValues = values == null
            ? new ValueValidation( YesNo.AUTO, Set.of(), Set.of(), validators )
            : new ValueValidation( values.required, values.dependentRequired, values.anyOfJsons, validators );
        return new PropertyValidation( anyOfTypes, newValues, strings, numbers, arrays, objects, items );
    }

    @Surly
    public PropertyValidation varargs() {
        Set<NodeType> anyOfTypes = new HashSet<>( anyOfTypes() );
        anyOfTypes.add( NodeType.ARRAY );
        ArrayValidation arrays = this.arrays;
        if ( values != null && values.required.isYes() ) {
            arrays = this.arrays == null
                ? new ArrayValidation( 1, -1, YesNo.AUTO )
                : this.arrays.required();
        }
        return new PropertyValidation( Set.copyOf( anyOfTypes ), values, strings, numbers, arrays, objects,
            new PropertyValidation( anyOfTypes(), values, strings, numbers, null, objects, items ) );
    }

    /**
     * Validations that apply to any node type.
     *
     * @param required          is the value required to exist or is undefined/null OK, non {@link YesNo#YES} is off
     * @param dependentRequired the groups this property is a member of for dependent requires
     * @param anyOfJsons        the JSON value must be one of the provided JSON values, empty set is off
     * @param customs           a validator defined by class is used (custom or user defined validators), empty list is
     *                          off
     */
    record ValueValidation(@Surly YesNo required, @Surly Set<String> dependentRequired, @Surly Set<String> anyOfJsons,
                           @Surly List<Validator> customs) {

        ValueValidation overlay( @Maybe ValueValidation with ) {
            return with == null ? this : new ValueValidation(
                overlayY( required, with.required ),
                overlayC( dependentRequired, with.dependentRequired ),
                overlayC( anyOfJsons, with.anyOfJsons ),
                overlayAdditive( customs, with.customs ) );
        }
    }

    /**
     * Validations that apply to string nodes.
     *
     * @param anyOfNames JSON string value must be one of the enum names, {@link Enum} is off
     * @param minLength  minimum length for the JSON string, negative is off
     * @param maxLength  maximum length for the JSON string, negative is off
     * @param pattern    JSON string must match the provided pattern, empty string is off
     */
    record StringValidation(@SuppressWarnings( "rawtypes" )
                            @Surly Class<? extends Enum> anyOfNames, int minLength, int maxLength,
                            @Surly String pattern) {

        StringValidation overlay( @Maybe StringValidation with ) {
            return with == null ? this : new StringValidation(
                overlayE( anyOfNames, with.anyOfNames ),
                overlayI( minLength, with.minLength ),
                overlayI( maxLength, with.maxLength ),
                overlayS( pattern, with.pattern ) );
        }
    }

    /**
     * Validations that apply to number nodes.
     *
     * @param minimum          JSON number must be greater than or equal to this lower limit, NaN is off
     * @param maximum          JSON number must be less than or equal to this upper limit, NaN is off
     * @param exclusiveMinimum JSON number value must be larger than the given value, NaN is off
     * @param exclusiveMaximum JSON number value must be smaller than the given value, NaN is off
     * @param multipleOf       JSON number value must be divisible by the given value without rest, NaN is off
     */
    record NumberValidation(double minimum, double maximum, double exclusiveMinimum, double exclusiveMaximum,
                            double multipleOf) {

        NumberValidation overlay( @Maybe NumberValidation with ) {
            return with == null ? this : new NumberValidation(
                overlayD( minimum, with.minimum ),
                overlayD( maximum, with.maximum ),
                overlayD( exclusiveMinimum, with.exclusiveMinimum ),
                overlayD( exclusiveMaximum, with.exclusiveMaximum ),
                overlayD( multipleOf, with.multipleOf ) );
        }
    }

    /**
     * Validations that apply to array nodes.
     *
     * @param minItems    JSON array must have at least this many elements, negative is off
     * @param maxItems    JSON array must have at most this many elements, negative is off
     * @param uniqueItems all elements in the JSON array value must be unique, false is off
     */
    record ArrayValidation(int minItems, int maxItems, @Surly YesNo uniqueItems) {

        ArrayValidation overlay( @Maybe ArrayValidation with ) {
            return with == null ? this : new ArrayValidation(
                overlayI( minItems, with.minItems ),
                overlayI( maxItems, with.maxItems ),
                overlayY( uniqueItems, with.uniqueItems ) );
        }

        /**
         * @return Same array validation but the array will be required to have at least 1 element
         */
        public ArrayValidation required() {
            return new ArrayValidation( Math.max( 1, minItems ), maxItems, uniqueItems );
        }
    }

    /**
     * Validations that apply to object nodes.
     *
     * @param minProperties JSON object must have at least this many properties, negative is off
     * @param maxProperties JSON object must have at most this many properties, negative is off
     */
    record ObjectValidation(int minProperties, int maxProperties) {

        ObjectValidation overlay( @Maybe ObjectValidation with ) {
            return with == null ? this : new ObjectValidation(
                overlayI( minProperties, with.minProperties ),
                overlayI( maxProperties, with.maxProperties ) );
        }
    }

    private static YesNo overlayY( YesNo a, YesNo b ) {
        if ( a == b || a == YesNo.AUTO ) return b;
        if ( b == YesNo.AUTO ) return a;
        return b;
    }

    private static String overlayS( String a, String b ) {
        if ( !b.isEmpty() ) return b;
        if ( !a.isEmpty() ) return a;
        return b;
    }

    private static int overlayI( int a, int b ) {
        if ( b >= 0 ) return b;
        if ( a >= 0 ) return a;
        return b;
    }

    private static double overlayD( double a, double b ) {
        if ( !isNaN( b ) ) return b;
        if ( !isNaN( a ) ) return a;
        return b;
    }

    private static <T, C extends Collection<T>> C overlayC( C a, C b ) {
        if ( !b.isEmpty() ) return b;
        if ( !a.isEmpty() ) return a;
        return b;
    }

    private static <E> List<E> overlayAdditive( List<E> a, List<E> b ) {
        if ( b.isEmpty() ) return a;
        if ( a.isEmpty() ) return b;
        Set<Class<?>> bs = b.stream().map( Object::getClass ).collect( toSet() );
        return Stream.concat( b.stream(), a.stream().filter( e -> !bs.contains( e.getClass() ) ) ).toList();
    }

    @SuppressWarnings( "rawtypes" )
    private static Class<? extends Enum> overlayE( Class<? extends Enum> a, Class<? extends Enum> b ) {
        if ( b != Enum.class ) return b;
        if ( a != Enum.class ) return a;
        return b;
    }
}
