package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonSchemaException.Info;
import org.hisp.dhis.jsontree.Validation.Error;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.hisp.dhis.jsontree.Validation.YesNo;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static java.lang.Double.NaN;
import static java.lang.Double.isNaN;
import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toUnmodifiableSet;

/**
 * @author Jan Bernitt
 * @since 0.10
 */
public final class JsonSchemaValidation {

    /**
     * Used when no further checks are done but a non-null {@link Validation.Validator} instance is needed.
     */
    private record Valid() implements Validation.Validator {

        static final Valid VALID = new Valid();

        @Override public void validate( JsonMixed value, Consumer<Error> addError ) {
            // nothing to do
        }
    }

    private record All(List<Validation.Validator> validators) implements Validation.Validator {

        static Validation.Validator of( Validation.Validator... validators ) {
            List<Validation.Validator> actual = Stream.of( validators ).filter( v -> v != Valid.VALID ).toList();
            return actual.isEmpty()
                ? Valid.VALID
                : actual.size() == 1 ? actual.get( 0 ) : new All( actual );
        }

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            validators.forEach( v -> v.validate( value, addError ) );
        }
    }

    private record TypeDependent(Map<NodeType, Validation.Validator> anyOf) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            Validation.Validator forType = switch ( value.node().getType() ) {
                case OBJECT -> anyOf.get( Validation.NodeType.OBJECT );
                case ARRAY -> anyOf.get( Validation.NodeType.ARRAY );
                case STRING -> anyOf.get( Validation.NodeType.STRING );
                case BOOLEAN -> anyOf.get( Validation.NodeType.BOOLEAN );
                case NULL -> anyOf.get( Validation.NodeType.NULL );
                case NUMBER -> anyOf.get( Validation.NodeType.NUMBER );
            };
            if ( forType == null && value.isInteger() )
                forType = anyOf.get( Validation.NodeType.INTEGER );
            if ( forType == null ) {
                addError.accept( Error.of( Rule.TYPE, value, anyOf.keySet() ) );
            } else {
                forType.validate( value, addError );
            }
        }
    }

    private record TypeEach(Validation.Validator each) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isObject() ) value.asMap( JsonMixed.class ).forEach( ( k, v ) -> each.validate( v, addError ) );
            if ( value.isArray() ) value.asList( JsonMixed.class ).forEach( e -> each.validate( e, addError ) );
        }
    }

    private record EnumAnyJson(Set<String> constants) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !constants.contains( value.toJson() ) )
                addError.accept( Error.of( Rule.ENUM, value, constants ) );
        }
    }

    /*
    string values
     */

    private record EnumAnyString(List<String> constants) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isString() && !constants.contains( value.string() ) )
                addError.accept( Error.of( Rule.ENUM, value, constants ) );
        }
    }

    private record MinLength(int limit) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isString() && value.string().length() < limit )
                addError.accept( Error.of( Rule.MIN_LENGTH, value, limit ) );
        }
    }

    private record MaxLength(int limit) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isString() && value.string().length() > limit )
                addError.accept( Error.of( Rule.MAX_LENGTH, value, limit ) );
        }
    }

    private record Pattern(String regex) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isString() && !value.string().matches( regex ) )
                addError.accept( Error.of( Rule.PATTERN, value, regex ) );
        }
    }


    /*
    number values
     */

    private record Minimum(double limit) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isNumber() && value.number().doubleValue() < limit )
                addError.accept( Error.of( Rule.MINIMUM, value, limit ) );
        }
    }

    private record Maximum(double limit) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isNumber() && value.number().doubleValue() > limit )
                addError.accept( Error.of( Rule.MAXIMUM, value, limit ) );
        }
    }

    private record ExclusiveMinimum(double limit) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isNumber() && value.number().doubleValue() <= limit )
                addError.accept( Error.of( Rule.EXCLUSIVE_MINIMUM, value, limit ) );
        }
    }

    private record ExclusiveMaximum(double limit) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isNumber() && value.number().doubleValue() >= limit )
                addError.accept( Error.of( Rule.EXCLUSIVE_MAXIMUM, value, limit ) );
        }
    }

    private record MultipleOf(double n) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isNumber() && value.number().doubleValue() % n > 0d )
                addError.accept( Error.of( Rule.MULTIPLE_OF, value, n ) );
        }
    }

    /*
    array values
     */

    private record MinItems(int count) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isArray() && value.size() < count )
                addError.accept( Error.of( Rule.MIN_ITEMS, value, count ) );
        }
    }

    private record MaxItems(int count) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isArray() && value.size() > count )
                addError.accept( Error.of( Rule.MAX_ITEMS, value, count ) );
        }
    }

    private record UniqueItems() implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isArray() ) {
                List<String> elementsAsJson = value.asList( JsonValue.class ).toList( v -> v.toJson() );
                for ( int i = 0; i < elementsAsJson.size(); i++ ) {
                    int j = elementsAsJson.lastIndexOf( elementsAsJson.get( i ) );
                    if ( j != i )
                        addError.accept( Error.of( Rule.UNIQUE_ITEMS, value, elementsAsJson.get( i ), i, j ) );
                }
            }
        }
    }

    /*
    object values
     */

    private record MinProperties(int limit) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isObject() && value.size() < limit )
                addError.accept( Error.of( Rule.MIN_PROPERTIES, value, limit ) );
        }
    }

    private record MaxProperties(int limit) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isObject() && value.size() > limit )
                addError.accept( Error.of( Rule.MAX_PROPERTIES, value, limit ) );
        }
    }

    private record Required(String property) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            addError.accept( Error.of( Rule.REQUIRED, value, property ) );
        }
    }

    private record DependentRequired(String property, List<String> present, List<String> absent,
                                     List<String> dependents) implements Validation.Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isObject() || value.get( property ).isUndefined() ) return;
            if ( !present.isEmpty() && present.stream().anyMatch( value::isUndefined ) ) return;
            if ( !absent.isEmpty() && absent.stream().anyMatch( Predicate.not( value::isUndefined ) ) ) return;
            if ( dependents.stream().anyMatch( value::isUndefined ) )
                addError.accept( Error.of( Rule.DEPENDENT_REQUIRED, value, property, dependents ) );
        }
    }

    private record Validators(Class<? extends JsonValue> schema, Map<String, Validation.Validator> properties) {}

    private static final Map<Class<? extends JsonValue>, Validators> VALIDATORS_BY_TYPE = new ConcurrentSkipListMap<>(
        comparing( Class::getName ) );
    private static final Map<Class<?>, Validations> CONSTRAINTS_BY_TYPE = new ConcurrentSkipListMap<>(
        comparing( Class::getName ) );

    static void validate( JsonValue value, JsonSchema schema ) {
        throw new UnsupportedOperationException( "not yet implemented" );
    }

    static void validate( JsonValue value, Class<? extends JsonValue> schema ) {
        Validators validators = of( schema );
        List<Error> errors = new ArrayList<>();
        for ( Map.Entry<String, Validation.Validator> e : validators.properties().entrySet() ) {
            JsonValue property = value.asObject().get( e.getKey() );
            e.getValue().validate( property.as( JsonMixed.class ), errors::add );
        }
        if ( !errors.isEmpty() ) throw new JsonSchemaException( "%d errors".formatted( errors.size() ),
            new Info( value, schema, errors ) );
    }

    static Validators of( Class<? extends JsonValue> schema ) {
        if ( !schema.isInterface() ) throw new IllegalArgumentException( "Schema must be an interface" );
        return VALIDATORS_BY_TYPE.computeIfAbsent( schema, JsonSchemaValidation::analyse );
    }

    private static <T extends JsonValue> Validators analyse( Class<T> schema ) {
        if ( schema.isAssignableFrom( JsonMixed.class ) )
            return new Validators( schema, Map.of() );
        if ( JsonObject.class.isAssignableFrom( schema ) )
            return analyseObject( schema );
        //TODO array type
        //TODO primitive subtypes
        return new Validators( schema, Map.of() );
    }

    private static <T extends JsonValue> Validators analyseObject( Class<T> schema ) {
        Map<String, Validation.Validator> validatorsByProperty = new HashMap<>();
        propertyMethods( schema ).forEach( m -> {
            String property = m.isAnnotationPresent( Validation.Exclude.class )
                ? null
                : captureProperty( m, schema );
            if ( property != null ) {
                createConstraints( m );
                Validation.Validator validator = createValidator( property, m.getReturnType(), m.getGenericReturnType(),
                    null, 0 );
                if ( validator != null ) {
                    validatorsByProperty.put( property, validator );
                }
                //TODO add to "" path validator for the dependent requires
            }
        } );
        return new Validators( schema, validatorsByProperty );
    }

    private static <T extends JsonValue> String captureProperty( Method m, Class<T> schema ) {
        String[] box = new String[1];
        T value = JsonMixed.of( JsonNode.of( "{}", path -> box[0] = path.substring( 1 ) ) ).as( schema );
        try {
            MethodHandles.lookup().unreflect( m ).invokeWithArguments( value );
            return box[0];
        } catch ( JsonPathException e ) {
            return box[0];
        } catch ( Throwable ex ) {
            return null;
        }
    }

    private static Stream<Method> propertyMethods( Class<?> schema ) {
        return Stream.of( schema.getDeclaredMethods() )
            .filter( m -> !m.getDeclaringClass().isAssignableFrom( JsonMixed.class ) )
            .filter( m -> m.getParameterCount() == 0 && (m.isDefault()) || Modifier.isAbstract( m.getModifiers() ) );
    }

    private static Validations createConstraints( AnnotatedElement e ) {
        Validation primary = e.getAnnotation( Validation.class );
        Validations secondaries = null;
        if ( primary != null && primary.base() != JsonValue.class )
            secondaries = CONSTRAINTS_BY_TYPE.computeIfAbsent( primary.base(),
                JsonSchemaValidation::createTypeConstraints );
        for ( Annotation a : e.getAnnotations() ) {
            if ( a.annotationType().isAnnotationPresent( Validation.class ) ) {
                Validation secondary = a.annotationType().getAnnotation( Validation.class );
                secondaries = Validations.merge( secondaries, new Validations( secondary ), false );
            }
        }
        if ( primary == null && secondaries == null ) return null;
        if ( primary == null ) return secondaries;
        if ( secondaries == null ) return new Validations( primary );
        return Validations.merge( secondaries, new Validations( primary ), true );
    }

    private static Validations createTypeConstraints( Class<?> type ) {
        if ( type == JsonValue.class ) return new Validations(
            Set.of( Validation.NodeType.OBJECT, Validation.NodeType.ARRAY, Validation.NodeType.BOOLEAN,
                Validation.NodeType.STRING, Validation.NodeType.NUMBER, Validation.NodeType.NULL ) );
        if ( !JsonValue.class.isAssignableFrom( type ) ) return new Validations( Set.of( nodeTypeOf( type ) ) );
        // there will be annotations in subtypes of JsonValue, merge them
        return createConstraints( type );
    }

    private static Validation.Validator createValidator( String property, Class<?> type,
        java.lang.reflect.Type genericType,
        Validations model, int typeLevel ) {
        Set<NodeType> types = model.type();
        if ( types.isEmpty() ) {
            types = JsonValue.class.isAssignableFrom( type )
                ? Set.of( Validation.NodeType.OBJECT, Validation.NodeType.ARRAY, Validation.NodeType.BOOLEAN,
                Validation.NodeType.STRING, Validation.NodeType.NUMBER,
                Validation.NodeType.NULL )
                : Set.of( nodeTypeOf( type ) );
        }
        Map<NodeType, Validation.Validator> validators = new EnumMap<>( NodeType.class );
        if ( types.contains( Validation.NodeType.STRING ) ) {
            validators.put( Validation.NodeType.STRING, All.of(
                model.enumeration() == Enum.class ? Valid.VALID : new EnumAnyString(
                    Stream.of( model.enumeration().getEnumConstants() ).map( Enum::name ).toList() ),
                model.minLength() < 0 ? Valid.VALID : new MinLength( model.minLength() ),
                model.maxLength() <= 0 ? Valid.VALID : new MaxLength( model.maxLength() ),
                model.pattern().isEmpty() ? Valid.VALID : All.of( model.pattern().stream().map( Pattern::new ).toArray(
                    Validation.Validator[]::new ) )
            ) );
        }
        if ( types.contains( Validation.NodeType.NUMBER ) || types.contains( Validation.NodeType.INTEGER ) ) {
            validators.put(
                types.contains( Validation.NodeType.NUMBER ) ? Validation.NodeType.NUMBER : Validation.NodeType.INTEGER,
                All.of(
                    isNaN( model.minimum() ) ? Valid.VALID : new Minimum( model.minimum() ),
                    isNaN( model.maximum() ) ? Valid.VALID : new Maximum( model.minimum() ),
                    isNaN( model.exclusiveMinimum() ) ? Valid.VALID : new ExclusiveMinimum( model.exclusiveMinimum() ),
                    isNaN( model.exclusiveMaximum() ) ? Valid.VALID : new ExclusiveMaximum( model.exclusiveMaximum() ),
                    isNaN( model.multipleOf() ) ? Valid.VALID : new MultipleOf( model.multipleOf() )
                ) );
        }

        if ( types.contains( Validation.NodeType.ARRAY ) ) {
            validators.put( Validation.NodeType.ARRAY, All.of(
                model.minItems() < 0 ? Valid.VALID : new MinItems( model.minItems() ),
                model.maxItems() <= 0 ? Valid.VALID : new MaxItems( model.maxItems() ),
                !model.uniqueItems() ? Valid.VALID : new UniqueItems()
                //TODO child validator
            ) );
        }
        if ( types.contains( Validation.NodeType.OBJECT ) ) {
            validators.put( Validation.NodeType.OBJECT, All.of(
                model.minProperties() < 0 ? Valid.VALID : new MinProperties( (model.minProperties()) ),
                model.maxProperties() <= 0 ? Valid.VALID : new MaxProperties( (model.maxProperties()) )
                //TODO child validator
            ) );
        }
        boolean isRequired = model.required() == YesNo.YES
            || model.required() == YesNo.AUTO && type.isPrimitive();
        validators.put( Validation.NodeType.NULL, !isRequired ? Valid.VALID : new Required( property ) );

        return model.values().isEmpty()
            ? new TypeDependent( validators )
            : All.of( new TypeDependent( validators ), new EnumAnyJson( model.values() ) );
    }

    private static NodeType nodeTypeOf( Class<?> type ) {
        if ( type == String.class || type.isEnum() || type == Date.class ) return Validation.NodeType.STRING;
        if ( type == Character.class || type == char.class ) return Validation.NodeType.STRING;
        if ( type == Boolean.class || type == boolean.class ) return Validation.NodeType.BOOLEAN;
        if ( type == Integer.class || type == Long.class || type == BigInteger.class )
            return Validation.NodeType.INTEGER;
        if ( Number.class.isAssignableFrom( type ) ) return Validation.NodeType.NUMBER;
        if ( type == Void.class || type == void.class ) return Validation.NodeType.NULL;
        if ( type.isPrimitive() )
            return type == float.class || type == double.class
                ? Validation.NodeType.NUMBER
                : Validation.NodeType.INTEGER;
        if ( Collection.class.isAssignableFrom( type ) ) return Validation.NodeType.ARRAY;
        if ( Object[].class.isAssignableFrom( type ) ) return Validation.NodeType.ARRAY;
        if ( Map.class.isAssignableFrom( type ) ) return Validation.NodeType.OBJECT;
        return Validation.NodeType.STRING;
    }

    /**
     * Used to merge annotations to a single value.
     */
    public record Validations(
        Set<String> groups,
        Set<NodeType> type,
        Set<String> values,
        @SuppressWarnings( "rawtypes" )
        Class<? extends Enum> enumeration,
        int minLength,
        int maxLength,
        Set<String> pattern,
        double minimum,
        double maximum,
        double exclusiveMinimum,
        double exclusiveMaximum,
        double multipleOf,
        int minItems,
        int maxItems,
        boolean uniqueItems,
        int minProperties,
        int maxProperties,
        YesNo required,
        Set<String> dependentRequires
    ) {

        static final Validations NONE = new Validations( Set.of() );

        Validations( Set<NodeType> type ) {
            this( Set.of(), type, Set.of(), Enum.class, //
                -1, -1, Set.of(), //
                NaN, NaN, NaN, NaN, NaN, //
                -1, -1, false, //
                -1, -1, YesNo.AUTO, Set.of() );
        }

        Validations( Validation v ) {
            this( Set.of( v.groups() ), Set.of( v.type() ), Set.of( v.values() ), v.enumeration(), //
                v.minLength(), v.maxLength(), v.pattern().isEmpty() ? Set.of() : Set.of( v.pattern() ), //
                v.minimum(), v.maximum(), v.exclusiveMinimum(), v.exclusiveMaximum(), v.multipleOf(), //
                v.minItems(), v.maxItems(), v.uniqueItems(), //
                v.minProperties(), v.maxProperties(), v.required(), Set.of( v.dependentRequired() ) );
        }

        static Validations merge( Validations a, Validations b, boolean favourB ) {
            return a == null ? b : new Validations(
                union( a.groups, b.groups, favourB ),
                union( a.type, b.type, favourB ),
                union( a.values, b.values, favourB ),
                merge( a.enumeration, b.enumeration, favourB ),
                max( a.minLength, b.minLength, favourB ),
                min( a.maxLength, b.maxLength, favourB ),
                union( a.pattern, b.pattern, favourB ),
                max( a.minimum, b.minimum, favourB ),
                min( a.maximum, b.maximum, favourB ),
                max( a.exclusiveMinimum, b.exclusiveMinimum, favourB ),
                min( a.exclusiveMaximum, b.exclusiveMaximum, favourB ),
                min( a.multipleOf, b.multipleOf, favourB ),
                max( a.minItems, b.minItems, favourB ),
                min( a.maxItems, b.maxItems, favourB ),
                a.uniqueItems || b.uniqueItems,
                max( a.minProperties, b.minProperties, favourB ),
                min( a.maxProperties, b.maxProperties, favourB ),
                yesOverNoOverAuto( a.required, b.required, favourB ),
                union( a.dependentRequires, b.dependentRequires, favourB )
            );
        }

        static YesNo yesOverNoOverAuto( YesNo a, YesNo b, boolean favourB ) {
            if ( favourB && b != YesNo.AUTO ) return b;
            if ( a == YesNo.YES || b == YesNo.YES ) return YesNo.YES;
            if ( a == YesNo.NO || b == YesNo.NO ) return YesNo.NO;
            return YesNo.AUTO;
        }

        static int min( int a, int b, boolean favourB ) {
            if ( favourB && b > 1 ) return b;
            if ( a <= 1 ) return b;
            if ( b <= 1 ) return a;
            return Math.min( a, b );
        }

        static int max( int a, int b, boolean favourB ) {
            if ( favourB && b > 0 ) return b;
            if ( a <= 0 ) return b;
            if ( b <= 0 ) return a;
            return Math.max( a, b );
        }

        static double min( double a, double b, boolean favourB ) {
            if ( favourB && !isNaN( b ) ) return b;
            if ( isNaN( a ) ) return b;
            if ( isNaN( b ) ) return a;
            return Math.min( a, b );
        }

        static double max( double a, double b, boolean favourB ) {
            if ( favourB && !isNaN( b ) ) return b;
            if ( isNaN( a ) ) return b;
            if ( isNaN( b ) ) return a;
            return Math.max( a, b );
        }

        static <T> Set<T> union( Set<T> a, Set<T> b, boolean favourB ) {
            if ( favourB && !b.isEmpty() ) return b;
            if ( a.isEmpty() ) return b;
            if ( b.isEmpty() ) return a;
            return Stream.concat( a.stream(), b.stream() ).collect( toUnmodifiableSet() );
        }

        static <T> List<Set<T>> union( List<Set<T>> a, List<Set<T>> b, boolean favourB ) {
            if ( favourB && !b.isEmpty() ) return b;
            if ( a.isEmpty() ) return b;
            if ( b.isEmpty() ) return a;
            int length = Math.max( a.size(), b.size() );
            List<Set<T>> res = new ArrayList<>( length );
            for ( int i = 0; i < length; i++ ) {
                res.add( union( i >= a.size() ? Set.of() : a.get( i ), i >= b.size() ? Set.of() : b.get( i ), false ) );
            }
            return List.copyOf( res );
        }

        @SuppressWarnings( "rawtypes" )
        static Class<? extends Enum> merge( Class<? extends Enum> a, Class<? extends Enum> b, boolean favourB ) {
            if ( favourB && b != Enum.class ) return b;
            if ( a == Enum.class ) return b;
            if ( b == Enum.class ) return a;
            if ( a != b ) throw new IllegalStateException(
                "Inconsistent schema definition, two different enums: %s %s".formatted(
                    a.getName(), b.getName() ) );
            return a;
        }
    }

    static Validations constraintsOf( JsonSchema.JsonValidation v ) {
        Set<String> values = Set.of(); //TODO use enum/const
        /*
        return new Constraints( List.of(Set.copyOf( v.type())), values, Enum.class, //
            v.minLength(), v.maxLength()
            );

         */
        return null;
    }

}
