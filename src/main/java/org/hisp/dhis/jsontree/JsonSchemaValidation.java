package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonSchemaException.Info;
import org.hisp.dhis.jsontree.JsonSchema.NodeType;
import org.hisp.dhis.jsontree.Validation.Restriction;
import org.hisp.dhis.jsontree.Validation.YesNo;
import org.hisp.dhis.jsontree.Validation.Error;

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
     * Value validation
     */
    private interface Validator {

        /**
         * Adds an error to the provided callback in case the provided value is not valid according to this check.
         *
         * @param value    the value to check
         * @param addError callback to add errors
         */
        void validate( JsonMixed value, Consumer<Error> addError );
    }

    /**
     * Used when no further checks are done but a non-null {@link Validator} instance is needed.
     */
    private record Valid() implements Validator {

        static final Valid VALID = new Valid();

        @Override public void validate( JsonMixed value, Consumer<Error> addError ) {
            // nothing to do
        }
    }

    private record All(List<Validator> validators) implements Validator {

        static Validator of( Validator... validators ) {
            List<Validator> actual = Stream.of( validators ).filter( v -> v != Valid.VALID ).toList();
            return actual.isEmpty()
                ? Valid.VALID
                : actual.size() == 1 ? actual.get( 0 ) : new All( actual );
        }

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            validators.forEach( v -> v.validate( value, addError ) );
        }
    }

    private record TypeSwitch(Map<NodeType, Validator> anyOf) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            Validator forType = switch ( value.node().getType() ) {
                case OBJECT -> anyOf.get( NodeType.OBJECT );
                case ARRAY -> anyOf.get( NodeType.ARRAY );
                case STRING -> anyOf.get( NodeType.STRING );
                case BOOLEAN -> anyOf.get( NodeType.BOOLEAN );
                case NULL -> anyOf.get( NodeType.NULL );
                case NUMBER -> anyOf.get( NodeType.NUMBER );
            };
            if ( forType == null && value.isInteger() )
                forType = anyOf.get( NodeType.INTEGER );
            if ( forType == null ) {
                addError.accept( Error.of( Restriction.TYPE, value, anyOf.keySet() ) );
            } else {
                forType.validate( value, addError );
            }
        }
    }

    private record TypeEach(Validator each) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isObject() ) value.asMap( JsonMixed.class ).forEach( ( k, v ) -> each.validate( v, addError ) );
            if ( value.isArray() ) value.asList( JsonMixed.class ).forEach( e -> each.validate( e, addError ) );
        }
    }

    private record EnumJson(Set<String> constants) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !constants.contains( value.node().getDeclaration() ) )
                addError.accept( Error.of( Restriction.ENUM, value, constants ) );
        }
    }

    private record EnumString(List<String> constants) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isString() && !constants.contains( value.string() ) )
                addError.accept( Error.of( Restriction.ENUM, value, constants ) );
        }
    }

    /*
    string values
     */

    private record MinLength(int limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isString() && value.string().length() < limit )
                addError.accept( Error.of( Restriction.MIN_LENGTH, value, limit ) );
        }
    }

    private record MaxLength(int limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isString() && value.string().length() > limit )
                addError.accept( Error.of( Restriction.MAX_LENGTH, value, limit ) );
        }
    }

    private record Pattern(String regex) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isString() && !value.string().matches( regex ) )
                addError.accept( Error.of( Restriction.PATTERN, value, regex ) );
        }
    }


    /*
    number values
     */

    private record Minimum(double limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isNumber() && value.number().doubleValue() < limit )
                addError.accept( Error.of( Restriction.MINIMUM, value, limit ) );
        }
    }

    private record Maximum(double limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isNumber() && value.number().doubleValue() > limit )
                addError.accept( Error.of( Restriction.MAXIMUM, value, limit ) );
        }
    }

    private record ExclusiveMinimum(double limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isNumber() && value.number().doubleValue() <= limit )
                addError.accept( Error.of( Restriction.EXCLUSIVE_MINIMUM, value, limit ) );
        }
    }

    private record ExclusiveMaximum(double limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isNumber() && value.number().doubleValue() >= limit )
                addError.accept( Error.of( Restriction.EXCLUSIVE_MAXIMUM, value, limit ) );
        }
    }

    private record MultipleOf(double n) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isNumber() && value.number().doubleValue() % n > 0d )
                addError.accept( Error.of( Restriction.MULTIPLE_OF, value, n ) );
        }
    }

    /*
    array values
     */

    private record MinItems(int count) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isArray() && value.size() < count )
                addError.accept( Error.of( Restriction.MIN_ITEMS, value, count ) );
        }
    }

    private record MaxItems(int count) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isArray() && value.size() > count )
                addError.accept( Error.of( Restriction.MAX_ITEMS, value, count ) );
        }
    }

    private record UniqueItems() implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isArray() ) {
                List<String> elementsAsJson = value.asList( JsonValue.class ).toList( v -> v.node().getDeclaration() );
                for ( int i = 0; i < elementsAsJson.size(); i++ ) {
                    int j = elementsAsJson.lastIndexOf( elementsAsJson.get( i ) );
                    if ( j != i )
                        addError.accept( Error.of( Restriction.UNIQUE_ITEMS, value, elementsAsJson.get( i ), i, j ) );
                }
            }
        }
    }

    /*
    object values
     */

    private record MinProperties(int limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isObject() && value.size() < limit )
                addError.accept( Error.of( Restriction.MIN_PROPERTIES, value, limit ) );
        }
    }

    private record MaxProperties(int limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isObject() && value.size() > limit )
                addError.accept( Error.of( Restriction.MAX_PROPERTIES, value, limit ) );
        }
    }

    private record Required(String property) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            addError.accept( Error.of( Restriction.REQUIRED, value, property ) );
        }
    }

    private record Requires(String property, List<String> properties) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isObject() && value.get( property ).exists() && !value.has( properties ) )
                addError.accept( Error.of( Restriction.DEPENDENT_REQUIRED, value, property, properties ) );
        }
    }

    private record Replaces(String property, List<String> properties) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isObject() && value.get( property ).isUndefined() && !value.has( properties ) )
                addError.accept( Error.of( Restriction.DEPENDENT_REQUIRED, value, property, properties ) );
        }
    }

    private record Validators(Class<? extends JsonValue> schema, Map<String, Validator> properties) {}

    private static final Map<Class<? extends JsonValue>, Validators> VALIDATORS_BY_TYPE = new ConcurrentSkipListMap<>(
        comparing( Class::getName ));
    private static final Map<Class<?>, Constraints> CONSTRAINTS_BY_TYPE = new ConcurrentSkipListMap<>(
        comparing( Class::getName ));

    static void validate( JsonValue value, JsonSchema schema ) {
        throw new UnsupportedOperationException( "not yet implemented" );
    }

    static void validate( JsonValue value, Class<? extends JsonValue> schema ) {
        Validators validators = of( schema );
        List<Error> errors = new ArrayList<>();
        for ( Map.Entry<String, Validator> e : validators.properties().entrySet() ) {
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
        Map<String, Validator> validatorsByProperty = new HashMap<>();
        propertyMethods( schema ).forEach( m -> {
            String property = m.isAnnotationPresent( Validation.Exclude.class )
                ? null
                : captureProperty( m, schema );
            if ( property != null ) {
                createConstraints( m );
                Validator validator = createValidator( property, m.getReturnType(), m.getGenericReturnType(),
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

    private static Constraints createConstraints( AnnotatedElement e ) {
        Validation primary = e.getAnnotation( Validation.class );
        Constraints secondaries = null;
        if (primary != null && primary.base() != JsonValue.class)
            secondaries = CONSTRAINTS_BY_TYPE.computeIfAbsent( primary.base(), JsonSchemaValidation::createTypeConstraints );
        for (Annotation a : e.getAnnotations() ) {
            if (a.annotationType().isAnnotationPresent( Validation.class )) {
                Validation secondary = a.annotationType().getAnnotation( Validation.class );
                secondaries = Constraints.merge( secondaries, new Constraints( secondary ), false );
            }
        }
        if (primary == null && secondaries == null) return null;
        if (primary == null) return secondaries;
        if (secondaries == null) return new Constraints( primary );
        return Constraints.merge( secondaries, new Constraints( primary ), true );
     }

     private static Constraints createTypeConstraints(Class<?> type) {
        if (type == JsonValue.class) return new Constraints(
            Set.of( NodeType.OBJECT, NodeType.ARRAY, NodeType.BOOLEAN, NodeType.STRING, NodeType.NUMBER, NodeType.NULL ) );
        if (!JsonValue.class.isAssignableFrom( type )) return new Constraints( Set.of(nodeTypeOf( type )) );
        // there will be annotations in subtypes of JsonValue, merge them
        return createConstraints( type );
     }

    private static Validator createValidator( String property, Class<?> type, java.lang.reflect.Type genericType,
        Constraints direct, int typeLevel ) {
        Set<NodeType> types = direct.type( typeLevel );
        if ( types.isEmpty() ) {
            types = JsonValue.class.isAssignableFrom( type )
                ? Set.of( NodeType.OBJECT, NodeType.ARRAY, NodeType.BOOLEAN, NodeType.STRING, NodeType.NUMBER,
                NodeType.NULL )
                : Set.of( nodeTypeOf( type ) );
        }
        Map<NodeType, Validator> validators = new EnumMap<>( NodeType.class );
        if ( types.contains( NodeType.STRING ) ) {
            validators.put( NodeType.STRING, All.of(
                direct.enumeration() == Enum.class ? Valid.VALID : new EnumString(
                    Stream.of( direct.enumeration().getEnumConstants() ).map( Enum::name ).toList() ),
                direct.minLength() < 0 ? Valid.VALID : new MinLength( direct.minLength() ),
                direct.maxLength() <= 0 ? Valid.VALID : new MaxLength( direct.maxLength() ),
                direct.pattern().isEmpty() ? Valid.VALID : All.of(direct.pattern().stream().map( Pattern::new ).toArray(Validator[]::new))
            ) );
        }
        if ( types.contains( NodeType.NUMBER ) || types.contains( NodeType.INTEGER ) ) {
            validators.put( types.contains( NodeType.NUMBER ) ? NodeType.NUMBER : NodeType.INTEGER, All.of(
                isNaN( direct.minimum() ) ? Valid.VALID : new Minimum( direct.minimum() ),
                isNaN( direct.maximum() ) ? Valid.VALID : new Maximum( direct.minimum() ),
                isNaN( direct.exclusiveMinimum() ) ? Valid.VALID : new ExclusiveMinimum( direct.exclusiveMinimum() ),
                isNaN( direct.exclusiveMaximum() ) ? Valid.VALID : new ExclusiveMaximum( direct.exclusiveMaximum() ),
                isNaN( direct.multipleOf() ) ? Valid.VALID : new MultipleOf( direct.multipleOf() )
            ) );
        }

        if ( types.contains( NodeType.ARRAY ) ) {
            validators.put( NodeType.ARRAY, All.of(
                direct.minItems() < 0 ? Valid.VALID : new MinItems( direct.minItems() ),
                direct.maxItems() <= 0 ? Valid.VALID : new MaxItems( direct.maxItems() ),
                !direct.uniqueItems() ? Valid.VALID : new UniqueItems()
                //TODO child validator
            ) );
        }
        if ( types.contains( NodeType.OBJECT ) ) {
            validators.put( NodeType.OBJECT, All.of(
                direct.minProperties() < 0 ? Valid.VALID : new MinProperties( (direct.minProperties()) ),
                direct.maxProperties() <= 0 ? Valid.VALID : new MaxProperties( (direct.maxProperties()) )
                //TODO child validator
            ) );
        }
        boolean isRequired = direct.required() == YesNo.YES
            || direct.required() == YesNo.AUTO && type.isPrimitive();
        validators.put( NodeType.NULL, !isRequired ? Valid.VALID : new Required( property ) );

        return direct.values().isEmpty()
            ? new TypeSwitch( validators )
            : All.of( new TypeSwitch( validators ), new EnumJson( direct.values() ) );
    }

    private static NodeType nodeTypeOf( Class<?> type ) {
        if ( type == String.class || type.isEnum() || type == Date.class ) return NodeType.STRING;
        if ( type == Character.class || type == char.class ) return NodeType.STRING;
        if ( type == Boolean.class || type == boolean.class ) return NodeType.BOOLEAN;
        if ( type == Integer.class || type == Long.class || type == BigInteger.class ) return NodeType.INTEGER;
        if ( Number.class.isAssignableFrom( type ) ) return NodeType.NUMBER;
        if ( type.isPrimitive() && type != void.class )
            return type == float.class || type == double.class ? NodeType.NUMBER : NodeType.INTEGER;
        if ( Collection.class.isAssignableFrom( type ) ) return NodeType.ARRAY;
        if ( Object[].class.isAssignableFrom( type ) ) return NodeType.ARRAY;
        if ( Map.class.isAssignableFrom( type ) ) return NodeType.OBJECT;
        return NodeType.STRING;
    }

    /**
     * Used to merge annotations to a single value.
     */
    public record Constraints(
        List<Set<NodeType>> type,
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

        static final Constraints NONE = new Constraints(Set.of());

        Constraints( Set<NodeType> type ) {
            this( type.isEmpty() ? List.of() : List.of( type ), Set.of(), Enum.class, //
                -1, -1, Set.of(), //
                NaN, NaN, NaN, NaN, NaN, //
                -1, -1, false, //
                -1, -1, YesNo.AUTO, Set.of() );
        }

        Constraints(Validation v) {
            this(type( v.type(), v.types() ), Set.of(v.values()), v.enumeration(), //
                v.minLength(), v.maxLength(), v.pattern().isEmpty() ? Set.of() : Set.of(v.pattern()), //
                v.minimum(), v.maximum(), v.exclusiveMinimum(), v.exclusiveMaximum(), v.multipleOf(), //
                v.minItems(), v.maxItems(), v.uniqueItems(), //
                v.minProperties(), v.maxProperties(), v.required(), Set.of(v.dependentRequires()));
        }

        public Set<NodeType> type(int index) {
            return index >= type.size() ? Set.of() : type.get( index );
        }

        static Constraints merge(Constraints a, Constraints b, boolean favourB) {
            return a == null ? b : new Constraints(
                union(a.type, b.type, favourB),
                union(a.values, b.values, favourB),
                merge( a.enumeration, b.enumeration, favourB ),
                max( a.minLength, b.minLength, favourB ),
                min(a.maxLength, b.maxLength, favourB ),
                union( a.pattern, b.pattern, favourB ),
                max(a.minimum, b.minimum, favourB),
                min(a.maximum, b.maximum, favourB),
                max(a.exclusiveMinimum, b.exclusiveMinimum, favourB),
                min(a.exclusiveMaximum, b.exclusiveMaximum, favourB),
                min(a.multipleOf, b.multipleOf, favourB),
                max(a.minItems, b.minItems, favourB),
                min(a.maxItems, b.maxItems, favourB),
                a.uniqueItems || b.uniqueItems,
                max(a.minProperties, b.minProperties, favourB),
                min(a.maxProperties, b.maxProperties, favourB),
                yesOverNoOverAuto( a.required, b.required, favourB ),
                union( a.dependentRequires, b.dependentRequires, favourB )
                );
        }

        static List<Set<NodeType>> type(NodeType[] type, Validation.Type[] types) {
            return types.length == 0
                ? List.of(Set.of(type))
                : Stream.of( types ).map( t -> Set.of(t.value()) ).toList();
        }

        static YesNo yesOverNoOverAuto(YesNo a, YesNo b, boolean favourB) {
            if (favourB && b != YesNo.AUTO) return b;
            if (a == YesNo.YES || b == YesNo.YES) return YesNo.YES;
            if (a == YesNo.NO || b == YesNo.NO) return YesNo.NO;
            return YesNo.AUTO;
        }

        static int min(int a, int b, boolean favourB ) {
            if (favourB && b > 1) return b;
            if (a <= 1 ) return b;
            if (b <= 1 ) return a;
            return Math.min( a,b );
        }

        static int max(int a, int b, boolean favourB ) {
            if (favourB && b > 0) return b;
            if (a <= 0 ) return b;
            if (b <= 0 ) return a;
            return Math.max( a,b );
        }

        static double min( double a, double b, boolean favourB ) {
            if (favourB && !isNaN( b )) return b;
            if ( isNaN( a ) ) return b;
            if ( isNaN( b ) ) return a;
            return Math.min( a, b );
        }

        static double max( double a, double b, boolean favourB ) {
            if (favourB && !isNaN( b )) return b;
            if ( isNaN( a ) ) return b;
            if ( isNaN( b ) ) return a;
            return Math.max( a, b );
        }

        static <T> Set<T> union(Set<T> a, Set<T> b, boolean favourB) {
            if (favourB && !b.isEmpty()) return b;
            if (a.isEmpty()) return b;
            if (b.isEmpty()) return a;
            return Stream.concat( a.stream(), b.stream() ).collect( toUnmodifiableSet());
        }

        static <T> List<Set<T>> union(List<Set<T>> a, List<Set<T>> b, boolean favourB) {
            if (favourB && !b.isEmpty()) return b;
            if (a.isEmpty()) return b;
            if (b.isEmpty()) return a;
            int length = Math.max( a.size(), b.size() );
            List<Set<T>> res = new ArrayList<>( length );
            for (int i = 0; i < length; i++) {
                res.add( union( i >= a.size() ? Set.of() : a.get( i ), i >= b.size() ? Set.of() : b.get( i ), false ) );
            }
            return List.copyOf( res );
        }

        @SuppressWarnings( "rawtypes" )
        static Class<? extends Enum> merge(Class<? extends Enum> a, Class<? extends Enum> b, boolean favourB) {
            if (favourB && b != Enum.class) return b;
            if (a == Enum.class) return b;
            if (b == Enum.class) return a;
            if ( a != b ) throw new IllegalStateException(
                "Inconsistent schema definition, two different enums: %s %s".formatted(
                    a.getName(), b.getName() ) );
            return a;
        }
    }

    static Constraints constraintsOf(JsonSchema.JsonValidation v) {
        Set<String> values = Set.of(); //TODO use enum/const
        /*
        return new Constraints( List.of(Set.copyOf( v.type())), values, Enum.class, //
            v.minLength(), v.maxLength()
            );

         */
        return null;
    }

}
