package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonAbstractArray;
import org.hisp.dhis.jsontree.JsonAbstractObject;
import org.hisp.dhis.jsontree.JsonList;
import org.hisp.dhis.jsontree.JsonMap;
import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonNodeType;
import org.hisp.dhis.jsontree.JsonValue;
import org.hisp.dhis.jsontree.Validation.Error;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.hisp.dhis.jsontree.Validation.Validator;
import org.hisp.dhis.jsontree.internal.Maybe;
import org.hisp.dhis.jsontree.internal.Surly;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static java.lang.Double.isNaN;
import static java.util.function.Predicate.not;
import static org.hisp.dhis.jsontree.Validation.NodeType.ARRAY;
import static org.hisp.dhis.jsontree.Validation.NodeType.INTEGER;
import static org.hisp.dhis.jsontree.Validation.NodeType.NULL;
import static org.hisp.dhis.jsontree.Validation.NodeType.NUMBER;
import static org.hisp.dhis.jsontree.Validation.NodeType.OBJECT;
import static org.hisp.dhis.jsontree.Validation.NodeType.STRING;

/**
 * A validator that Contains one {@link Validator} for each property of the schema that needs validation.
 *
 * @param schema     that JSON type that was used as the basis to create the property validators from
 * @param properties one validator for each property
 * @author Jan Bernitt
 * @since 0.11
 */
record ObjectValidator(
    @Surly Class<? extends JsonValue> schema,
    @Surly Map<String, Validator> properties
) implements Validator {

    @Override
    public void validate( JsonMixed value, Consumer<Error> addError ) {
        if ( !value.isObject() ) return;
        properties().forEach(
            ( property, validator ) -> validator.validate( value.get( property, JsonMixed.class ), addError ) );
    }

    /*
        Creation internals...
     */

    /**
     * "JVM Cache" for schemas that are already transformed to a {@link ObjectValidator} entry.
     * OBS! Cannot use {@code ConcurrentHashMap} because while computing an entry another entry might be added within
     * (as a consequence of) the computation.
     */
    private static final Map<Class<? extends JsonValue>, ObjectValidator> BY_SCHEMA_TYPE = new ConcurrentSkipListMap<>(
        Comparator.comparing( Class::getName ));

    @Surly
    public static ObjectValidator getInstance( Class<? extends JsonValue> schema ) {
        return getInstance( schema, () -> ObjectValidation.getInstance( schema ) );
    }

    @Surly
    public static ObjectValidator getInstance( ObjectValidation obj ) {
        return getInstance( obj.schema(), () -> obj );
    }

    private static ObjectValidator getInstance( Class<? extends JsonValue> schema,
        Supplier<ObjectValidation> analyse ) {
        return BY_SCHEMA_TYPE.computeIfAbsent( schema,
            type -> {
                Map<String, Validator> res = new TreeMap<>();
                ObjectValidation objectValidation = analyse.get();
                Map<String, PropertyValidation> properties = objectValidation.properties();
                properties.forEach( ( property, validation ) -> {
                    Validator propValidator = create( validation, property );
                    Validator propTypeValidator = getInstance( objectValidation.types().get( property ) );
                    Validator validator = Guard.of( propValidator, propTypeValidator );
                    if ( validator != null ) res.put( property, validator );
                } );
                Validator dependentRequired = createDependentRequired( properties );
                if ( dependentRequired != null ) res.put( "", dependentRequired );
                return new ObjectValidator( type, Map.copyOf( res ) );
            } );
    }

    /**
     * TODO support for types like:
     * {@code JsonIntList extends JsonList<JsonInteger>}
     * So a Class type where the actual type for the list element needs to be extracted from the superinterfaces.
     * {@code JsonSet<T> extends JsonAbstractArray<T>} or {@code JsonMultiMap<T> extends JsonAbstractObject<JsonList<T>>}
     * So types with parameters where the actual type of the object or array needs to be found from a combination of the
     * actual type arguments and the superinterfaces.
     */
    @Maybe
    private static Validator getInstance( java.lang.reflect.Type type ) {
        if (type instanceof Class<?> cType) {
            if (JsonAbstractObject.class.isAssignableFrom( cType )) {
                @SuppressWarnings( "unchecked" )
                Class<? extends JsonValue> schema = (Class<? extends JsonValue>) cType;
                return getInstance( schema );
            }
        }
        if (type instanceof ParameterizedType pt ) {
            Class<?> rawType = (Class<?>) pt.getRawType();
            if ( JsonMap.class.isAssignableFrom( rawType ) )
                return Items.of(getInstance( pt.getActualTypeArguments()[0] ));
            if ( JsonList.class.isAssignableFrom( rawType ) )
                return Items.of( getInstance( pt.getActualTypeArguments()[0] ));
        }
        return null;
    }

    @Maybe
    private static Validator create( PropertyValidation node, String property ) {
        Set<NodeType> anyOf = node.anyOfTypes();

        Map<JsonNodeType, Validator> byType = new EnumMap<>( JsonNodeType.class );
        BiConsumer<JsonNodeType, Validator> add = ( type, validator ) -> {
            if ( validator != null ) byType.put( type, validator );
        };
        Predicate<NodeType> has = type -> anyOf.isEmpty() || anyOf.contains( type );
        if ( has.test( STRING ) ) add.accept( JsonNodeType.STRING, create( node.strings() ) );
        if ( has.test( NUMBER ) ) add.accept( JsonNodeType.NUMBER, create( node.numbers() ) );
        if ( has.test( INTEGER ) ) add.accept( JsonNodeType.NUMBER, create( node.numbers() ) );
        if ( has.test( ARRAY ) ) add.accept( JsonNodeType.ARRAY, create( node.arrays() ) );
        if ( has.test( OBJECT ) ) add.accept( JsonNodeType.OBJECT, create( node.objects() ) );

        Validator type = anyOf.isEmpty() ? null : new Type( anyOf );
        Validator anyType = create( node.values() );
        Validator typeDependent = byType.isEmpty() ? null : new TypeDependent( byType );
        Validator items = node.items() == null ? null : Items.of( create( node.items(), property ) );
        Validator whenDefined = Guard.of( type, Guard.of( anyType, Guard.of( typeDependent, items ) ) );

        PropertyValidation.ValueValidation values = node.values();
        boolean isRequiredYes = values != null && values.required().isYes();
        boolean isRequiredAuto = (values == null || values.required().isAuto()) && isRequiredImplicitly( node, anyOf );
        Validator required = !isRequiredYes && !isRequiredAuto ? null : new Required( property );
        return Guard.of( required, whenDefined );
    }

    /**
     * minItems, minLength, minProperties implicitly means this is required if there is only one type possible and if
     * required is AUTO
     */
    private static boolean isRequiredImplicitly( PropertyValidation node, Set<NodeType> anyOf ) {
        return anyOf.size() == 1 && (
            anyOf.contains( STRING ) && node.strings() != null && node.strings().minLength() > 0
                || anyOf.contains( ARRAY ) && node.arrays() != null && node.arrays().minItems() > 0
                || anyOf.contains( OBJECT ) && node.objects() != null && node.objects().minProperties() > 0);
    }

    @Maybe
    private static Validator create( @Maybe PropertyValidation.ValueValidation values ) {
        return values == null ? null : All.of(
            values.anyOfJsons().isEmpty() ? null : new EnumAnyJson( values.anyOfJsons() ),
            values.customs().isEmpty() ? null : new All( values.customs() )
        );
    }

    @Maybe
    private static Validator create( @Maybe PropertyValidation.StringValidation strings ) {
        return strings == null ? null : All.of(
            strings.anyOfNames() == Enum.class ? null : new EnumAnyString(
                Stream.of( strings.anyOfNames().getEnumConstants() ).map( Enum::name ).toList() ),
            strings.minLength() <= 0 ? null : new MinLength( strings.minLength() ),
            strings.maxLength() <= 1 ? null : new MaxLength( strings.maxLength() ),
            strings.pattern().isEmpty() ? null : new Pattern( strings.pattern() ) );
    }

    @Maybe
    private static Validator create( @Maybe PropertyValidation.NumberValidation numbers ) {
        return numbers == null ? null : All.of(
            isNaN( numbers.minimum() ) ? null : new Minimum( numbers.minimum() ),
            isNaN( numbers.maximum() ) ? null : new Maximum( numbers.maximum() ),
            isNaN( numbers.exclusiveMinimum() ) ? null : new ExclusiveMinimum( numbers.exclusiveMinimum() ),
            isNaN( numbers.exclusiveMaximum() ) ? null : new ExclusiveMaximum( numbers.exclusiveMaximum() ),
            isNaN( numbers.multipleOf() ) ? null : new MultipleOf( numbers.multipleOf() ) );
    }

    @Maybe
    private static Validator create( @Maybe PropertyValidation.ArrayValidation arrays ) {
        return arrays == null ? null : All.of(
            arrays.minItems() <= 0 ? null : new MinItems( arrays.minItems() ),
            arrays.maxItems() <= 1 ? null : new MaxItems( arrays.maxItems() ),
            !arrays.uniqueItems().isYes() ? null : new UniqueItems() );
    }

    @Maybe
    private static Validator create( @Maybe PropertyValidation.ObjectValidation objects ) {
        return objects == null ? null : All.of(
            objects.minProperties() <= 0 ? null : new MinProperties( (objects.minProperties()) ),
            objects.maxProperties() <= 1 ? null : new MaxProperties( (objects.maxProperties()) ) );
    }

    @Maybe
    private static Validator createDependentRequired( @Surly Map<String, PropertyValidation> properties ) {
        if ( properties.isEmpty() ) return null;
        if ( properties.values().stream()
            .allMatch( p -> p.values() == null || p.values().dependentRequired().isEmpty() ) ) return null;
        Map<String, Map<String, String>> groupPropertyRole = new HashMap<>();
        properties.forEach( ( name, property ) -> {
            PropertyValidation.ValueValidation values = property.values();
            if ( values != null && !values.dependentRequired().isEmpty() ) {
                values.dependentRequired().forEach( role -> {
                    String group = role.replace( "!", "" ).replace( "?", "" );
                    groupPropertyRole.computeIfAbsent( group, key -> new HashMap<>() ).put( name, role );
                } );
            }
        } );
        List<Validator> all = new ArrayList<>();
        groupPropertyRole.forEach( ( group, members ) -> {
            if ( members.values().stream().noneMatch( ObjectValidator::isDependentRequiredRole )) {
                all.add( new DependentRequiredCodependent( Set.copyOf( members.keySet() ) ) );
            } else {
                List<String> present = members.entrySet().stream().filter( e ->
                    e.getValue().endsWith( "!" ) ).map( Map.Entry::getKey ).toList();
                List<String> absent = members.entrySet().stream().filter( e ->
                    e.getValue().endsWith( "?" ) ).map( Map.Entry::getKey ).toList();
                List<String> dependent = members.entrySet().stream().filter( e ->
                    !isDependentRequiredRole( e.getValue() ) ).map( Map.Entry::getKey ).toList();
                List<String> exclusiveDependent = members.entrySet().stream().filter( e ->
                    e.getValue().endsWith( "^" ) ).map( Map.Entry::getKey ).toList();
                all.add(
                    new DependentRequired( Set.copyOf( present ), Set.copyOf( absent ),
                        Set.copyOf( dependent ), Set.copyOf( exclusiveDependent ) ) );
            }
        } );
        return All.of( all.toArray( Validator[]::new ) );
    }

    private static boolean isDependentRequiredRole(String group) {
        return group.endsWith( "?" ) || group.endsWith( "!" ) || group.endsWith( "^" );
    }

    /*
     Node type independent or generic validators
     */

    private record All(List<Validator> validators) implements Validator {

        @Maybe
        static Validator of( Validator... validators ) {
            List<Validator> actual = Stream.of( validators )
                .filter( Objects::nonNull )
                .mapMulti( ( Validator v, Consumer<Validator> pipe ) -> {
                    if ( v instanceof All all ) {all.validators.forEach( pipe );} else {pipe.accept( v );}
                } ).toList();
            return actual.isEmpty()
                ? null
                : actual.size() == 1 ? actual.get( 0 ) : new All( actual );
        }

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            validators.forEach( v -> v.validate( value, addError ) );
        }
    }

    private record Guard(Validator when, Validator then) implements Validator {

        @Maybe
        static Validator of( @Maybe Validator when, @Maybe Validator then ) {
            if ( then == null ) return when;
            if ( when == null ) return then;
            return new Guard( when, then );
        }

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            boolean[] guard = { true };
            when.validate( value, error -> {
                guard[0] = false;
                addError.accept( error );
            } );
            if ( guard[0] ) then.validate( value, addError );
        }
    }

    private record Type(Set<NodeType> anyOf) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            NodeType actual = NodeType.of( value.type() );
            if ( actual == NULL || anyOf.contains( actual ) ) return;
            if ( actual == NUMBER && anyOf.contains( INTEGER ) && value.isInteger() ) return;
            addError.accept( Error.of( Rule.TYPE, value,
                "must have any of %s type but was: %s", anyOf, actual ) );
        }
    }

    private record TypeDependent(Map<JsonNodeType, Validator> anyOf) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            Validator forType = anyOf.get( value.type() );
            if ( forType != null ) forType.validate( value, addError );
        }
    }

    private record Items(Validator each) implements Validator {

        static Validator of(Validator each) {
            return each == null ? null : new Items( each );
        }

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isObject() ) value.forEachValue( e -> each.validate( e.as( JsonMixed.class ), addError ) );
            if ( value.isArray() ) value.forEach( e -> each.validate( e.as( JsonMixed.class ), addError ) );
        }
    }

    private record Required(String property) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isUndefined() )
                addError.accept( Error.of( Rule.REQUIRED, value,
                    "%s is required but was " + (value.isNull() ? "null" : "undefined"), property ) );
        }
    }

    /**
     * The value must be one of the provided JSON strings
     *
     * @param constants each a valid JSON string
     */
    private record EnumAnyJson(Set<String> constants) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isUndefined() ) return;
            String json = value.toMinimizedJson();
            if ( !constants.contains( json ) )
                addError.accept( Error.of( Rule.ENUM, value,
                    "must be one of %s but was: %s", constants, json ) );
        }
    }

    /*
    string values
     */

    private record EnumAnyString(List<String> constants) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isString() ) return;
            String actual = value.string();
            if ( !constants.contains( actual ) )
                addError.accept( Error.of( Rule.ENUM, value,
                    "must be one of %s but was: %s", constants, actual ) );
        }
    }

    private record MinLength(int limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isString() ) return;
            int actual = value.string().length();
            if ( actual < limit )
                addError.accept( Error.of( Rule.MIN_LENGTH, value,
                    "length must be >= %d but was: %d", limit, actual ) );
        }
    }

    private record MaxLength(int limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isString() ) return;
            int actual = value.string().length();
            if ( actual > limit )
                addError.accept( Error.of( Rule.MAX_LENGTH, value,
                    "length must be <= %d but was: %d", limit, actual ) );
        }
    }

    private record Pattern(String regex) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isString() ) return;
            String actual = value.string();
            if ( !actual.matches( regex ) )
                addError.accept( Error.of( Rule.PATTERN, value,
                    "must match %s but was: %s", regex, actual ) );
        }
    }


    /*
    number values
     */

    private record Minimum(double limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isNumber() ) return;
            double actual = value.number().doubleValue();
            if ( actual < limit )
                addError.accept( Error.of( Rule.MINIMUM, value,
                    "must be >= %f but was: %f", limit, actual ) );
        }
    }

    private record Maximum(double limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isNumber() ) return;
            double actual = value.number().doubleValue();
            if ( actual > limit )
                addError.accept( Error.of( Rule.MAXIMUM, value,
                    "must be <= %f but was: %f", limit, actual ) );
        }
    }

    private record ExclusiveMinimum(double limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isNumber() ) return;
            double actual = value.number().doubleValue();
            if ( actual <= limit )
                addError.accept( Error.of( Rule.EXCLUSIVE_MINIMUM, value,
                    "must be > %f but was: %f", limit, actual ) );
        }
    }

    private record ExclusiveMaximum(double limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isNumber() ) return;
            double actual = value.number().doubleValue();
            if ( actual >= limit )
                addError.accept( Error.of( Rule.EXCLUSIVE_MAXIMUM, value,
                    "must be < %f but was: %f", limit, actual ) );
        }
    }

    private record MultipleOf(double n) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isNumber() ) return;
            double actual = value.number().doubleValue();
            if ( actual % n > 0d )
                addError.accept( Error.of( Rule.MULTIPLE_OF, value,
                    "must be a multiple of %f but was: %f", n, actual ) );
        }
    }

    /*
    array values
     */

    private record MinItems(int count) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isArray() ) return;
            int actual = value.size();
            if ( actual < count )
                addError.accept( Error.of( Rule.MIN_ITEMS, value,
                    "must have >= %d items but had: %d", count, actual ) );
        }
    }

    private record MaxItems(int count) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isArray() ) return;
            int actual = value.size();
            if ( actual > count )
                addError.accept( Error.of( Rule.MAX_ITEMS, value,
                    "must have <= %d items but had: %d", count, actual ) );
        }
    }

    private record UniqueItems() implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( value.isArray() ) {
                List<String> elementsAsJson = value.asList( JsonValue.class ).toList( JsonValue::toMinimizedJson );
                for ( int i = 0; i < elementsAsJson.size(); i++ ) {
                    int j = elementsAsJson.lastIndexOf( elementsAsJson.get( i ) );
                    if ( j != i )
                        addError.accept( Error.of( Rule.UNIQUE_ITEMS, value,
                            "items must be unique but %s was found at index %d and %d", elementsAsJson.get( i ), i,
                            j ) );
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
            if ( !value.isObject() ) return;
            int actual = value.size();
            if ( actual < limit )
                addError.accept( Error.of( Rule.MIN_PROPERTIES, value,
                    "must have >= %d properties but has: %d", limit, actual ) );
        }
    }

    private record MaxProperties(int limit) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isObject() ) return;
            int actual = value.size();
            if ( actual > limit )
                addError.accept( Error.of( Rule.MAX_PROPERTIES, value,
                    "must have <= %d properties but has: %d", limit, actual ) );
        }
    }

    private record DependentRequired(Set<String> present, Set<String> absent,
                                     Set<String> dependents, Set<String> exclusiveDependent) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isObject() ) return;
            if ( !present.isEmpty() && present.stream().anyMatch( value::isUndefined ) ) return;
            if ( !absent.isEmpty() && absent.stream().anyMatch( not( value::isUndefined ) ) ) return;
            if ( !dependents.isEmpty() && dependents.stream().anyMatch( value::isUndefined ) ) {
                Set<String> missing = Set.copyOf( dependents.stream().filter( value::isUndefined ).toList() );
                if ( present.isEmpty() ) {
                    addError.accept( Error.of( Rule.DEPENDENT_REQUIRED, value,
                        "object without any of %s requires all of %s, missing: %s", absent, dependents, missing ) );
                } else if ( absent.isEmpty() ) {
                    addError.accept( Error.of( Rule.DEPENDENT_REQUIRED, value,
                        "object with any of %s requires all of %s, missing: %s", present, dependents, missing ) );
                } else {
                    addError.accept( Error.of( Rule.DEPENDENT_REQUIRED, value,
                        "object with any of %s or without any of %s requires all of %s, missing: %s", present,
                        absent, dependents, missing ) );
                }
            }
            if (!exclusiveDependent.isEmpty()) {
                Set<String> defined = Set.copyOf( exclusiveDependent.stream().filter( p -> !value.isUndefined( p ) ).toList());
                if (defined.size() == 1) return; // it is exclusively defined => OK
                if (present.isEmpty() && absent.isEmpty()) {
                    addError.accept( Error.of( Rule.DEPENDENT_REQUIRED, value,
                        "object requires one but only one of %s, but has: %s", exclusiveDependent, defined ) );
                } else if ( present.isEmpty() ) {
                    addError.accept( Error.of( Rule.DEPENDENT_REQUIRED, value,
                        "object without any of %s requires one but only one of %s, but has: %s", absent, exclusiveDependent, defined ) );
                } else if ( absent.isEmpty() ) {
                    addError.accept( Error.of( Rule.DEPENDENT_REQUIRED, value,
                        "object with any of %s requires one but only one of %s, but has: %s", present, exclusiveDependent, defined ) );
                } else {
                    addError.accept( Error.of( Rule.DEPENDENT_REQUIRED, value,
                        "object with any of %s or without any of %s requires one but only one of %s, but has: %s", present,
                        absent, exclusiveDependent, defined ) );
                }
            }
        }
    }

    private record DependentRequiredCodependent(Set<String> codependent) implements Validator {

        @Override public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isObject() ) return;
            if ( codependent.stream().anyMatch( value::isUndefined ) && codependent.stream()
                .anyMatch( not( value::isUndefined ) ) )
                addError.accept( Error.of( Rule.DEPENDENT_REQUIRED, value,
                    "object with any of %1$s all of %1$s are required, missing: %s", codependent,
                    Set.copyOf( codependent.stream().filter( value::isUndefined ).toList() ) ) );
        }
    }
}
