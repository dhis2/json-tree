package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonAbstractObject;
import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonNodeType;
import org.hisp.dhis.jsontree.JsonValue;
import org.hisp.dhis.jsontree.Validation.Error;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.hisp.dhis.jsontree.Validation.Validator;
import org.hisp.dhis.jsontree.internal.Maybe;
import org.hisp.dhis.jsontree.internal.Surly;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
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
     * "JVM Cache" for schemas that are already transformed to a {@link ObjectValidator} entry
     */
    private static final Map<Class<? extends JsonValue>, ObjectValidator> BY_SCHEMA_TYPE = new ConcurrentHashMap<>();

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
                    Class<?> propType = objectValidation.types().get( property );
                    Validator propTypeValidator =JsonAbstractObject.class.isAssignableFrom( propType )
                        ? getInstance( (Class<? extends JsonValue>) propType )
                        : null;
                    Validator validator = Guard.of( propValidator, propTypeValidator );
                    if ( validator != null ) res.put( property, validator );
                } );
                Validator dependentRequired = createDependentRequired( properties );
                if ( dependentRequired != null ) res.put( "", dependentRequired );
                return new ObjectValidator( type, Map.copyOf( res ) );
            } );
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
        Validator items = node.items() == null ? null : new Items( create( node.items(), property ) );
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
            if ( members.values().stream().allMatch( role -> !role.endsWith( "!" ) && !role.endsWith( "?" ) ) ) {
                all.add( new DependentRequiredCodependent( Set.copyOf( members.keySet() ) ) );
            } else {
                List<String> present = members.entrySet().stream().filter( e ->
                    e.getValue().endsWith( "!" ) ).map( Map.Entry::getKey ).toList();
                List<String> absent = members.entrySet().stream().filter( e ->
                    e.getValue().endsWith( "?" ) ).map( Map.Entry::getKey ).toList();
                List<String> dependent = members.entrySet().stream().filter( e ->
                    !e.getValue().endsWith( "!" ) && !e.getValue().endsWith( "?" ) ).map( Map.Entry::getKey ).toList();
                all.add(
                    new DependentRequired( Set.copyOf( present ), Set.copyOf( absent ), Set.copyOf( dependent ) ) );
            }
        } );
        return All.of( all.toArray( Validator[]::new ) );
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
                                     Set<String> dependents) implements Validator {

        @Override
        public void validate( JsonMixed value, Consumer<Error> addError ) {
            if ( !value.isObject() ) return;
            if ( !present.isEmpty() && present.stream().anyMatch( value::isUndefined ) ) return;
            if ( !absent.isEmpty() && absent.stream().anyMatch( not( value::isUndefined ) ) ) return;
            if ( dependents.stream().anyMatch( value::isUndefined ) ) {
                Set<String> missing = Set.copyOf( dependents.stream().filter( value::isUndefined ).toList() );
                if ( present.isEmpty() ) {
                    addError.accept( Error.of( Rule.DEPENDENT_REQUIRED, value,
                        "object without any of %s all of %s are required, missing: %s", absent, dependents, missing ) );
                } else if ( absent.isEmpty() ) {
                    addError.accept( Error.of( Rule.DEPENDENT_REQUIRED, value,
                        "object with any of %s all of %s are required, missing: %s", present, dependents, missing ) );
                } else {
                    addError.accept( Error.of( Rule.DEPENDENT_REQUIRED, value,
                        "object with any of %s or without any of %s all of %s are required, missing: %s", present,
                        absent, dependents, missing ) );
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
