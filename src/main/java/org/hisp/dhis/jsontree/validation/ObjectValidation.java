package org.hisp.dhis.jsontree.validation;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonNode;
import org.hisp.dhis.jsontree.JsonPathException;
import org.hisp.dhis.jsontree.JsonValue;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validator;
import org.hisp.dhis.jsontree.internal.Maybe;
import org.hisp.dhis.jsontree.internal.Surly;
import org.hisp.dhis.jsontree.validation.PropertyValidation.ArrayValidation;
import org.hisp.dhis.jsontree.validation.PropertyValidation.NumberValidation;
import org.hisp.dhis.jsontree.validation.PropertyValidation.StringValidation;
import org.hisp.dhis.jsontree.validation.PropertyValidation.ValueValidation;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

import static java.lang.Double.isNaN;
import static java.lang.reflect.Modifier.isAbstract;
import static java.util.Comparator.comparing;
import static org.hisp.dhis.jsontree.Validation.NodeType.ARRAY;
import static org.hisp.dhis.jsontree.Validation.NodeType.BOOLEAN;
import static org.hisp.dhis.jsontree.Validation.NodeType.INTEGER;
import static org.hisp.dhis.jsontree.Validation.NodeType.NULL;
import static org.hisp.dhis.jsontree.Validation.NodeType.NUMBER;
import static org.hisp.dhis.jsontree.Validation.NodeType.OBJECT;
import static org.hisp.dhis.jsontree.Validation.NodeType.STRING;
import static org.hisp.dhis.jsontree.Validation.YesNo.AUTO;
import static org.hisp.dhis.jsontree.Validation.YesNo.YES;

/**
 * Analysis types and annotations to extract a {@link PropertyValidation} model description.
 *
 * @author Jan Bernitt
 * @since 0.11
 */
record ObjectValidation(
    @Surly Class<? extends JsonValue> schema,
    @Surly Map<String, Type> types,
    @Surly Map<String, PropertyValidation> properties) {

    private static final Map<Class<? extends JsonValue>, ObjectValidation> INSTANCES = new ConcurrentHashMap<>();

    private static final Map<Class<?>, PropertyValidation> RECORD_BY_JAVA_TYPE = new ConcurrentSkipListMap<>(
        comparing( Class::getName ) );

    /**
     * Meta annotations are those that are themselves annotated {@link Validation} or one or more meta-annotations which
     * they aggregate.
     */
    private static final Map<Class<? extends Annotation>, PropertyValidation> RECORD_BY_META_TYPE = new ConcurrentHashMap<>();

    /**
     * Resolves the node validations to apply to a value of the provided schema type.
     *
     * @param schema a type representing a JSON structure node
     * @return a map of validations to apply to each property for the provided schema and the type itself (root = empty
     * string property)
     */
    @Surly
    public static ObjectValidation getInstance( Class<? extends JsonValue> schema ) {
        if ( !schema.isInterface() ) throw new IllegalArgumentException( "Schema must be an interface" );
        return INSTANCES.computeIfAbsent( schema, ObjectValidation::createInstance );
    }

    private static ObjectValidation createInstance( Class<? extends JsonValue> schema ) {
        Map<String, PropertyValidation> properties = new HashMap<>();
        Map<String, Type> types = new HashMap<>();
        propertyMethods( schema )
            .forEach( m -> {
                String property = captureProperty( m, schema );
                if ( property != null ) {
                    properties.put( property, fromMethod( m ) );
                    types.put( property, m.getGenericReturnType() );
                }
            } );
        return new ObjectValidation( schema, Map.copyOf( types ), Map.copyOf( properties ) );
    }

    private static <T extends JsonValue> String captureProperty( Method m, Class<T> schema ) {
        String[] box = new String[1];
        T value = JsonMixed.of( JsonNode.of( "{}", path -> box[0] = path.substring( 1 ) ) ).as( schema );
        try {
            Object res = MethodHandles.lookup().unreflect( m ).invokeWithArguments( value );
            // for virtual nodes force lookup by resolving the underlying node in the actual tree
            if ( res instanceof JsonValue node ) node.node();
            return box[0];
        } catch ( JsonPathException e ) {
            return box[0];
        } catch ( Throwable ex ) {
            return null;
        }
    }

    private static Stream<Method> propertyMethods( Class<?> schema ) {
        return Stream.of( schema.getMethods() )
            .filter( m -> m.getDeclaringClass().isInterface() && !m.getDeclaringClass()
                .isAnnotationPresent( Validation.Ignore.class ) )
            .filter( m -> m.getReturnType() != void.class && !m.isAnnotationPresent( Validation.Ignore.class ) )
            .filter( m -> m.getParameterCount() == 0 && (m.isDefault()) || isAbstract( m.getModifiers() ) );
    }

    @Maybe
    private static PropertyValidation fromMethod( Method src ) {
        PropertyValidation onMethod = fromAnnotations( src );
        PropertyValidation onReturnType = fromValueTypeUse( src.getAnnotatedReturnType() );
        if ( onMethod == null ) return onReturnType;
        if ( onReturnType == null ) return onMethod;
        return onMethod.overlay( onReturnType );
    }

    /**
     * @param src as it occurs for the property method, may be null
     * @return validation based on the Java value type (this includes annotations on the class type)
     */
    @Maybe
    private static PropertyValidation fromValueTypeUse( AnnotatedType src ) {
        Type type = src.getType();
        if ( type instanceof Class<?> simpleType )
            return fromValueTypeDeclaration( simpleType ).overlay( fromAnnotations( src ) );
        //TODO AnnotatedArrayType...
        if ( !(src instanceof AnnotatedParameterizedType pt) ) return null;
        Type rt = ((ParameterizedType) pt.getType()).getRawType();
        Class<?> rawType = (Class<?>) rt;
        PropertyValidation base = fromValueTypeDeclaration( rawType ).overlay( fromAnnotations( src ) );
        AnnotatedType[] typeArguments = pt.getAnnotatedActualTypeArguments();
        if ( typeArguments.length == 1 )
            return base.withItems( fromValueTypeUse( typeArguments[0] ) );
        if ( Map.class.isAssignableFrom( rawType ) )
            return base.withItems( fromValueTypeUse( typeArguments[1] ) );
        return base;
    }

    @Surly
    private static PropertyValidation fromValueTypeDeclaration( @Surly Class<?> type ) {
        return RECORD_BY_JAVA_TYPE.computeIfAbsent( type, t -> {
            PropertyValidation declared = fromAnnotations( t );
            PropertyValidation inferred = declared != null ? declared : toPropertyValidation( t );
            if ( Object[].class.isAssignableFrom( t ) )
                return inferred.withItems( fromValueTypeDeclaration( t.getComponentType() ) );
            return inferred;
        } );
    }

    @Maybe
    private static PropertyValidation fromAnnotations( AnnotatedElement src ) {
        PropertyValidation meta = fromMetaAnnotations( src );
        Validation validation = getValidationAnnotation( src );
        PropertyValidation main = validation == null ? null : toPropertyValidation( validation );
        List<Validation.Validator> validators = toValidators( src );
        PropertyValidation items = fromItems( src );
        PropertyValidation base = meta == null ? main : meta.overlay( main );
        if (base == null && items == null && validators.isEmpty()) return null;
        if (base == null)
            base = new PropertyValidation( Set.of(), null, null, null, null, null, null );
        return base
            .withCustoms( validators )
            .withItems( items );
    }

    @Maybe
    private static Validation getValidationAnnotation( AnnotatedElement src ) {
        Validation a = src.getAnnotation( Validation.class );
        if (a != null) return a;
        if (!(src instanceof Class<?> c )) return null;
        for (Class<?> si : c.getInterfaces()) {
            a = getValidationAnnotation( si );
            if (a != null) return a;
        }
        return null;
    }

    @Maybe
    private static PropertyValidation fromMetaAnnotations( AnnotatedElement src ) {
        Annotation[] candidates = src.getAnnotations();
        if ( candidates.length == 0 ) return null;
        return Stream.of( candidates )
            .sorted( comparing( a -> a.annotationType().getSimpleName() ) )
            .map( ObjectValidation::fromMetaAnnotation )
            .filter( Objects::nonNull )
            .reduce( PropertyValidation::overlay )
            .orElse( null );
    }

    @Maybe
    private static PropertyValidation fromMetaAnnotation( Annotation a ) {
        Class<? extends Annotation> type = a.annotationType();
        if ( !type.isAnnotationPresent( Validation.class ) ) return null;
        return RECORD_BY_META_TYPE.computeIfAbsent( type,
            t -> toPropertyValidation( t.getAnnotation( Validation.class ) )
                .withCustoms( toValidators( t ) )
                .withItems( fromItems( t ) )
        );
    }

    @Maybe
    private static PropertyValidation fromItems( AnnotatedElement src ) {
        if ( !src.isAnnotationPresent( Validation.Items.class ) ) return null;
        return toPropertyValidation( src.getAnnotation( Validation.Items.class ).value() );
    }

    @Surly
    private static PropertyValidation toPropertyValidation( Class<?> type ) {
        ValueValidation values = !type.isPrimitive() ? null : new ValueValidation( YES, Set.of(), AUTO, Set.of(), List.of() );
        StringValidation strings = !type.isEnum() ? null : new StringValidation( anyOfStrings( type ), AUTO,-1, -1, "" );
        return new PropertyValidation( anyOfTypes( type ), values, strings, null, null, null, null );
    }

    @Surly
    private static PropertyValidation toPropertyValidation( @Surly Validation src ) {
        PropertyValidation res = new PropertyValidation(
            anyOfTypes( src ),
            toValueValidation( src ),
            toStringValidation( src ),
            toNumberValidation( src ),
            toArrayValidation( src ),
            toObjectValidation( src ),
            null );
        return src.varargs().isYes() ? res.varargs() : res;
    }

    @Maybe
    private static ValueValidation toValueValidation( @Surly Validation src ) {
        boolean oneOfValuesEmpty = src.oneOfValues().length == 0 || isAutoUnquotedJsonStrings( src.oneOfValues() );
        boolean dependentRequiresEmpty = src.dependentRequired().length == 0;
        if ( src.required().isAuto() && oneOfValuesEmpty && dependentRequiresEmpty && src.acceptNull().isAuto() ) return null;
        Set<String> oneOfValues = oneOfValuesEmpty
            ? Set.of()
            : Set.copyOf( Stream.of( src.oneOfValues() ).map( e -> JsonValue.of( e ).toMinimizedJson() ).toList() );
        return new ValueValidation( src.required(), Set.of( src.dependentRequired() ), src.acceptNull(), oneOfValues,
            List.of() );
    }

    private static boolean isAutoUnquotedJsonStrings(String[] values) {
        return values.length > 0 && Stream.of( values ).allMatch( v -> !v.isEmpty()
                && Character.isLetter( v.charAt( 0 ) )
                && !"true".equals( v )
                && !"false".equals( v )
                && !"null".equals( v ));
    }

    @Maybe
    private static StringValidation toStringValidation( @Surly Validation src ) {
        if ( src.enumeration() == Enum.class && src.minLength() < 0 && src.maxLength() < 0 && src.pattern().isEmpty()
            && src.caseInsensitive().isAuto() && !isAutoUnquotedJsonStrings( src.oneOfValues() ))
            return null;
        Set<String> anyOfStrings = anyOfStrings( src.enumeration() );
        if (anyOfStrings.isEmpty() && isAutoUnquotedJsonStrings( src.oneOfValues() ))
            anyOfStrings = Set.of(src.oneOfValues());
        return new StringValidation(anyOfStrings, src.caseInsensitive(), src.minLength(), src.maxLength(), src.pattern() );
    }

    private static Set<String> anyOfStrings( @Surly Class<?> type ) {
        return type == Enum.class || !type.isEnum()
            ? Set.of()
            : Set.copyOf( Stream.of( type.getEnumConstants() ).map( e -> ((Enum<?>)e).name() ).toList() );
    }

    @Maybe
    private static NumberValidation toNumberValidation( @Surly Validation src ) {
        if ( isNaN( src.minimum() ) && isNaN( src.maximum() ) && isNaN( src.exclusiveMinimum() ) && isNaN(
            src.exclusiveMaximum() ) && isNaN( src.multipleOf() ) ) return null;
        return new NumberValidation( src.minimum(), src.maximum(), src.exclusiveMinimum(), src.exclusiveMaximum(),
            src.multipleOf() );
    }

    @Maybe
    private static ArrayValidation toArrayValidation( @Surly Validation src ) {
        if ( src.minItems() < 0 && src.maxItems() < 0 && src.uniqueItems().isAuto() ) return null;
        return new ArrayValidation( src.minItems(), src.maxItems(), src.uniqueItems() );
    }

    @Maybe
    private static PropertyValidation.ObjectValidation toObjectValidation( @Surly Validation src ) {
        if ( src.minProperties() < 0 && src.maxProperties() < 0 ) return null;
        return new PropertyValidation.ObjectValidation( src.minProperties(), src.maxProperties() );
    }

    @Surly
    private static List<Validation.Validator> toValidators( @Surly AnnotatedElement src ) {
        Validator[] validators = src.getAnnotationsByType( Validator.class );
        if ( validators.length == 0 ) return List.of();
        return Stream.of( validators )
            .map( ObjectValidation::toValidator )
            .filter( Objects::nonNull )
            .toList();
    }

    @Maybe
    private static Validation.Validator toValidator( @Surly Validator src ) {
        Class<? extends Validation.Validator> type = src.value();
        if ( !type.isRecord() ) return null;
        Validation[] params = src.params();
        try {
            if ( params.length == 0 )
                return type.getConstructor().newInstance();
            if ( params.length == 1 )
                return type.getConstructor( Validation.class ).newInstance( params[0] );
            return type.getConstructor( Validation[].class ).newInstance( (Object) params );
        } catch ( Exception ex ) {
            return null;
        }
    }

    @Surly
    private static Set<NodeType> anyOfTypes( @Surly Validation src ) {
        return anyOfTypes( src.type() );
    }

    @Surly
    private static Set<NodeType> anyOfTypes( NodeType... type ) {
        if ( type.length == 0 ) return Set.of();
        Set<NodeType> anyOf = EnumSet.of( type[0], type );
        if ( anyOf.contains( NodeType.INTEGER ) && anyOf.contains( NodeType.NUMBER ) ) anyOf.remove( NodeType.INTEGER );
        return Set.copyOf( anyOf );
    }

    @Surly
    private static Set<NodeType> anyOfTypes( Class<?> type ) {
        NodeType main = nodeTypeOf( type );
        if ( type == Date.class && main != null ) return Set.of( main, INTEGER );
        if ( main != null ) return Set.of( main );
        return Set.of();
    }

    @Maybe
    private static NodeType nodeTypeOf( @Surly Class<?> type ) {
        if ( type == String.class || type.isEnum() || type == Date.class ) return STRING;
        if ( type == Character.class || type == char.class ) return STRING;
        if ( type == Boolean.class || type == boolean.class ) return BOOLEAN;
        if ( type == Integer.class || type == Long.class || type == BigInteger.class )
            return NodeType.INTEGER;
        if ( Number.class.isAssignableFrom( type ) ) return NUMBER;
        if ( type == Void.class || type == void.class ) return NULL;
        if ( type.isPrimitive() )
            return type == float.class || type == double.class
                ? NUMBER
                : NodeType.INTEGER;
        if ( Collection.class.isAssignableFrom( type ) ) return ARRAY;
        if ( Object[].class.isAssignableFrom( type ) ) return ARRAY;
        if ( Map.class.isAssignableFrom( type ) ) return OBJECT;
        return null;
    }
}
