/*
 * Copyright (c) 2004-2021, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonAccessors.JsonAccessor;
import org.hisp.dhis.jsontree.internal.Maybe;
import org.hisp.dhis.jsontree.internal.Surly;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serial;
import java.io.Serializable;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;

/**
 * Implements the {@link JsonValue} read-only access abstraction for JSON responses.
 * <p>
 * The way this works is that internally when navigating the JSON object the {@link #path} is extended. The returned
 * value is always either a {@link JsonVirtualTree} or a {@link Proxy} calling all default methods on the declaring
 * interface and all implemented by {@link JsonVirtualTree} on a "wrapped" {@link JsonVirtualTree} instance.
 * <p>
 * When values are accessed the {@link #path} is extracted from the {@link #root} and eventually converted and checked
 * against expectations. Exceptions are eventually converted to provide a consistent behaviour.
 * <p>
 * It is crucial to understand that the complete JSON object model is purely a typed convenience layer expressing what
 * could or is expected to exist. Whether something actually exist is first evaluated when leaf values are accessed or
 * existence is explicitly checked using {@link #exists()}.
 * <p>
 * This also means specific {@link JsonObject}s are modelled by extending the interface and implementing {@code default}
 * methods. No other implementation {@code class} is ever needed apart from the {@link JsonVirtualTree}.
 *
 * @author Jan Bernitt
 */
final class JsonVirtualTree implements JsonMixed, Serializable {

    public static final JsonMixed NULL = new JsonVirtualTree( JsonNode.NULL, JsonPath.SELF, JsonAccess.GLOBAL );

    private static final Map<Class<?>, List<Property>> PROPERTIES = new ConcurrentHashMap<>();

    static List<Property> properties(Class<?> of) {
        if (JsonObject.class.isAssignableFrom(of)) {
            @SuppressWarnings( "unchecked" )
            Class<? extends JsonObject> type = (Class<? extends JsonObject>) of;
            return PROPERTIES.computeIfAbsent(type, t -> captureProperties( type ));
        }
        if (Record.class.isAssignableFrom( of )) {
            @SuppressWarnings( "unchecked" )
            Class<? extends Record> type = (Class<? extends Record>) of;
            return PROPERTIES.computeIfAbsent(type, t -> componentProperties( type ));
        }
        throw new UnsupportedOperationException("Must be a subtype of JsonObject or Record but was: "+of);
    }

    /**
     * {@link MethodHandle} cache for zero-args default methods as usually used by the properties declared in
     * {@link JsonObject} sub-types. Each subtype has its on map with the method name as key as that is already unique.
     */
    private static final ClassValue<Map<String, MethodHandle>> PROPERTY_MH_CACHE = new ClassValue<>() {
        @Override
        protected Map<String, MethodHandle> computeValue( Class declaringClass ) {
            return new ConcurrentHashMap<>();
        }
    };

    /**
     * {@link MethodHandle} cache used for any method that does not match conditions for {@link #PROPERTY_MH_CACHE}.
     * <p>
     * {@link MethodHandle}s are cached as a performance optimisation, in particular because during the MH resolve
     * exceptions might be thrown and caught internally which has shown to be costly compared to a cache lookup.
     */
    private static final Map<Method, MethodHandle> OTHER_MH_CACHE = new ConcurrentHashMap<>();

    private final @Surly JsonNode root;
    private final @Surly JsonPath path;
    private JsonNode node; // remember once it was resolved from root
    private transient @Surly JsonAccessors accessors;

    public JsonVirtualTree( @Maybe CharSequence json, @Surly JsonAccessors accessors ) {
        this( json == null || json.isEmpty() ? JsonNode.EMPTY_OBJECT : JsonNode.of( json ), JsonPath.SELF, accessors);
    }

    public JsonVirtualTree( @Surly JsonNode root, @Surly JsonAccessors accessors ) {
        this( root, JsonPath.SELF, accessors );
    }

    private JsonVirtualTree( @Surly JsonNode root, @Surly JsonPath path, @Surly JsonAccessors accessors ) {
        this.root = root;
        this.path = path;
        if (path.isEmpty()) node = root;
        this.accessors = accessors;
    }

    @Serial
    private void readObject( ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();        // restore non‑transient fields
        accessors = JsonAccess.GLOBAL; // set transient field
    }

    @Surly @Override
    public JsonPath path() {
        return path;
    }

    @Surly @Override
    public JsonAccessors getAccessors() {
        return accessors;
    }

    @Override
    public <T> T to( Class<T> type ) {
        if (type.isInterface()) return createProxy( type, this );
        try {
            return accessors.accessor( type ).access( this, type, accessors );
        } catch ( JsonAccessException ex ) {
            throw ex; // keep it
        } catch ( RuntimeException ex ) {
            // wrap it
            throw new JsonAccessException(
                "JSON does not map to a Java %s: %s".formatted(type.getSimpleName(), toJson()), ex);
        }
    }

    @Override
    public Class<? extends JsonValue> asType() {
        return JsonMixed.class;
    }

    private JsonNode node( JsonNodeType expected ) {
        JsonNode res = node;
        if (node == null) {
            if (!root.exists(path)) return null;
            node = root.get(path);
            res = node;
        }
        JsonNodeType actualType = res.getType();
        if ( actualType == JsonNodeType.NULL ) {
            return null;
        }
        if ( actualType != expected ) {
            throw new JsonTreeException(
                String.format( "Path `%s` does not contain an %s but a(n) %s: %s",
                    path, expected, actualType, res ) );
        }
        return res;
    }

    @Override
    public <T extends JsonValue> T get( int index, Class<T> as ) {
        return asType( as, new JsonVirtualTree( root, path.chain( index ), accessors ) );
    }

    @Override
    public <T extends JsonValue> T get( Text name, Class<T> as ) {
        return asType( as, new JsonVirtualTree( root, path.chain( name ), accessors) );
    }

    @Override
    public <T extends JsonValue> T get( JsonPath subPath, Class<T> as ) {
        if (subPath.isEmpty()) return as( as );
        return asType( as, new JsonVirtualTree( root, path.concat( subPath ), accessors) );
    }

    @Override
    public <T extends JsonValue> T as( Class<T> as ) {
        return asType( as, this );
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public <T extends JsonValue> T as( Class<T> as, BiConsumer<Method, Object[]> onCall ) {
        return (T) Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(), new Class[] { as },
            ( proxy, method, args ) -> {
                onCall.accept( method, args );
                return onInvoke( proxy, as, this, method, args, true );
            } );
    }

    @SuppressWarnings( "unchecked" )
    private <T extends JsonValue> T asType( Class<T> as, JsonVirtualTree target ) {
        return isJsonMixedSubType( as ) ? createProxy( as, target ) : (T) target;
    }

    @Override
    public JsonNode node() {
        if (node != null) return node;
        node = root.get( path );
        return node;
    }

    @Override
    public boolean isEmpty() {
        return node().isEmpty();
    }

    @Override
    public List<String> stringValues() {
        return arrayList( Text.class, Text::toString );
    }

    @Override
    public List<Number> numberValues() {
        return arrayList( Number.class, Function.identity() );
    }

    @Override
    public List<Boolean> boolValues() {
        return arrayList( Boolean.class, Function.identity() );
    }

    private <T, E> List<T> arrayList( Class<E> elementType, Function<E,T> map) {
        JsonNode array = node( JsonNodeType.ARRAY );
        if (array == null || array.isEmpty()) return List.of();
        List<T> res = new ArrayList<>(array.size());
        for ( JsonNode e : array.elements() ) {
            @SuppressWarnings( "unchecked" )
            E value = (E) e.value();
            if ( !elementType.isInstance( value ) ) {
                throw new JsonTreeException(
                    "Array element is not a " + elementType.getName() + ": " + e.getDeclaration() );
            }
            res.add( map.apply( value ) );
        }
        return res;
    }

    @Override
    public int size() {
        return node().size();
    }

    @Override
    public Boolean bool() {
        JsonNode node = node( JsonNodeType.BOOLEAN );
        return node == null ? null : (Boolean) node.value();
    }

    @Override
    public Number number() {
        JsonNode node = node( JsonNodeType.NUMBER );
        return node == null ? null : (Number) node.value();
    }

    @Override
    public Text text() {
        JsonNode node = node( JsonNodeType.STRING );
        return node == null ? null : (Text) node.value();
    }

    @Override
    public boolean exists() {
        return path.isEmpty() || root.exists( path );
    }

    @Override
    public boolean equals( Object obj ) {
        return obj instanceof JsonVirtualTree other
            && path.equals( other.path )
            && root.equals( other.root );
    }

    @Override
    public int hashCode() {
        return root.hashCode() ^ path.hashCode();
    }

    @Override
    public String toString() {
        try {
            return node().getDeclaration().toString();
        } catch ( JsonPathException | JsonTreeException | JsonFormatException ex ) {
            return ex.getMessage();
        }
    }

    @SuppressWarnings( "unchecked" )
    private <E> E createProxy( Class<E> as, JsonVirtualTree target ) {
        return (E) Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(), new Class[] { as },
            ( proxy, method, args ) -> onInvoke( proxy, as, target, method, args, false ) );
    }

    /**
     * @param proxy instance of the proxy the method was invoked upon
     * @param as the type the proxy represents
     * @param target the underlying tree that in the end holds the node against which a call potentially is resolved
     * @param method the method of the proxy that was called
     * @param args the arguments for the method
     * @return the result of the method call
     * @param <E> type of the proxy
     */
    private <E> Object onInvoke( Object proxy, Class<E> as, JsonVirtualTree target, Method method,
        Object[] args, boolean alwaysCallDefault )
        throws Throwable {
        // are we dealing with a default method in the extending class?
        Class<?> declaringClass = method.getDeclaringClass();
        if ( declaringClass == JsonValue.class && "asType".equals( method.getName() )
            && method.getParameterCount() == 0 ) {
            return as;
        }
        boolean isDefault = method.isDefault();
        if ( isJsonMixedSubType( declaringClass ) || isDefault && alwaysCallDefault ) {
            if ( isDefault ) {
                // call the default method of the proxied type itself
                return callDefaultMethod( proxy, method, args );
                //TODO what if we bind to the target for JsonMixed?
            }
            // abstract extending interface method?
            return callAbstractMethod( target, method, args );
        }
        // call the same method on the wrapped object (assuming it has it)
        return callCoreApiMethod( target, method, args );
    }

    /**
     * Any default methods implemented by an extension of the {@link JsonValue} class tree is run by calling the default
     * as defined in the interface. This is sadly not as straight forward as it might sound.
     */
    private static Object callDefaultMethod( Object proxy, Method method, Object[] args )
        throws Throwable {
        if (method.getParameterCount() == 0)
            return PROPERTY_MH_CACHE.get( method.getDeclaringClass() )
                .computeIfAbsent( method.getName(), name -> getDefaultMethodHandle( method ) )
                .bindTo( proxy ).invoke();
        return OTHER_MH_CACHE.computeIfAbsent( method, JsonVirtualTree::getDefaultMethodHandle )
            .bindTo( proxy ).invokeWithArguments( args );
    }

    /**
     * All methods by the core API of the general JSON tree represented as {@link JsonValue}s (and the general
     * subclasses) are implemented by the {@link JsonVirtualTree} wrapper, so they can be called directly.
     */
    private static Object callCoreApiMethod( JsonVirtualTree target, Method method, Object[] args )
        throws Throwable {
        if (args == null || args.length == 0)
            return OTHER_MH_CACHE.computeIfAbsent( method, JsonVirtualTree::getCoreApiMethodHandle )
                .bindTo( target ).invoke();
        if (method.isDefault())
            return OTHER_MH_CACHE.computeIfAbsent( method, JsonVirtualTree::getDefaultMethodHandle )
                .bindTo( target ).invokeWithArguments( args );
        return OTHER_MH_CACHE.computeIfAbsent(method, JsonVirtualTree::getCoreApiMethodHandle )
            .bindTo( target ).invokeWithArguments( args );
    }

    private static MethodHandle getCoreApiMethodHandle( Method m ) {
        try {
            return MethodHandles.lookup().unreflect( m );
        } catch ( IllegalAccessException e ) {
            throw new RuntimeException( e );
        }
    }

    private static MethodHandle getDefaultMethodHandle( Method method ) {
        try {
            Class<?> declaringClass = method.getDeclaringClass();
            return MethodHandles.lookup()
                .findSpecial( declaringClass, method.getName(),
                    MethodType.methodType( method.getReturnType(), method.getParameterTypes() ),
                    declaringClass );
        } catch ( Exception ex ) {
            throw new RuntimeException(ex);
        }
    }

  /**
   * Abstract interface methods are "implemented" by mapping the JSON value to the method's return
   * type using an {@link JsonAccessor}. The path/name is extracted from the abstract method's
   * property name.
   */
  private Object callAbstractMethod(JsonVirtualTree target, Method method, Object[] args) {
        JsonObject obj = target.asObject();
        Class<?> resType = method.getReturnType();
        String name = stripGetterPrefix( method );
        boolean hasDefault = method.getParameterCount() == 1 && method.getParameterTypes()[0] == resType;
        if ( hasDefault && obj.get( name ).isUndefined()) {
            return args[0];
        }
        return access( method, obj, name );
    }

    private Object access( Method method, JsonObject obj, String name ) {
        Type genericType = method.getGenericReturnType();
        JsonAccessor<?> accessor = accessors.accessor( method.getReturnType() );
        try {
            return accessor.access( obj.get( name, JsonMixed.class ), genericType, accessors );
        } catch ( JsonAccessException ex ) {
            throw ex;
        } catch ( RuntimeException ex ) {
            throw new JsonAccessException(
                "JSON does not map to a Java %s: %s".formatted(genericType.getTypeName(), toJson()), ex);
        }
    }

    /**
     * Logically we check for JsonMixed but to be safe any method implemented by {@linkplain JsonVirtualTree} should be
     * considered as "core" and be handled directly
     */
    private static boolean isJsonMixedSubType( Class<?> declaringClass ) {
        return !declaringClass.isAssignableFrom( JsonVirtualTree.class );
    }

    private static String stripGetterPrefix( Method method ) {
        String name = method.getName();
        if ( name.startsWith( "is" ) && name.length() > 2 && isUpperCase( name.charAt( 2 ) ) ) {
            return toLowerCase( name.charAt( 2 ) ) + name.substring( 3 );
        }
        if ( name.startsWith( "get" ) && name.length() > 3 && isUpperCase( name.charAt( 3 ) ) ) {
            return toLowerCase( name.charAt( 3 ) ) + name.substring( 4 );
        }
        return name;
    }

    private String toSignature( Type type ) {
        if ( type instanceof Class<?> ) {
            return ((Class<?>) type).getCanonicalName();
        }
        if ( type instanceof ParameterizedType ) {
            StringBuilder str = new StringBuilder();
            toSignature( type, str );
            return str.toString();
        }
        return "?";
    }

    private void toSignature( Type type, StringBuilder str ) {
        if ( type instanceof Class<?> ) {
            str.append( ((Class<?>) type).getCanonicalName() );
            return;
        }
        if ( type instanceof ParameterizedType pt ) {
            toSignature( pt.getRawType(), str );
            str.append( '<' );
            for ( Type ata : pt.getActualTypeArguments() ) {
                str.append( toSignature( ata ) );
            }
            str.append( '>' );
        } else {
            str.append( '?' );
        }
    }

    private static List<Property> componentProperties(Class<? extends Record> of) {
        return Stream.of( of.getRecordComponents() )
            .map( c ->  new JsonObject.Property(
                of, Text.of(c.getName()), JsonMixed.class, c.getName(), c.getAnnotatedType(), c ))
            .toList();
    }

    private static List<Property> captureProperties(Class<? extends JsonObject> of) {
        List<Property> res = new ArrayList<>();
        propertyMethods(of).forEach( m -> {
            @SuppressWarnings( "unchecked" )
            Class<? extends JsonObject> in = (Class<? extends JsonObject>) m.getDeclaringClass();
            JsonObject obj = JsonMixed.of( "{}" ).as( of, (method, args) -> {
                if ( isJsonObjectGetAs( method ) ) {
                    Text name = (Text) args[0];
                    @SuppressWarnings( "unchecked" )
                    Class<? extends JsonValue> type = (Class<? extends JsonValue>) args[1];
                    res.add( new Property( in, name, type, m.getName(), m.getAnnotatedReturnType(), m ) );
                }
            });
            invokePropertyMethod( obj, m ); // may add zero, one or more properties via the callback
        } );
        return List.copyOf( res );
    }

    private static boolean isJsonObjectGetAs( Method method ) {
        if (!"get".equals( method.getName() )
            || method.getParameterCount() != 2
            || method.getDeclaringClass() != JsonObject.class) return false;
        Class<?>[] types = method.getParameterTypes();
        return types[0] == Text.class && types[1] == Class.class;
    }

    private static void invokePropertyMethod(JsonObject obj, Method property) {
        try {
            MethodHandles.lookup().unreflect( property ).invokeWithArguments( obj );
        } catch ( Throwable e ) {
            // ignore
        }
    }

  private static Stream<Method> propertyMethods(Class<?> of) {
    return Stream.of(of.getMethods()).filter(JsonVirtualTree::isJsonObjectSubTypeProperty);
    }

    /**
     * @return Only true for methods declared in interfaces extending {@link JsonObject}. All core API methods are
     * excluded.
     */
    private static boolean isJsonObjectSubTypeProperty(Method m) {
        return m.isDefault() || Modifier.isAbstract( m.getModifiers() )
            && m.getParameterCount() == 0
            && isJsonMixedSubType( m.getDeclaringClass() )
            && m.getReturnType() != void.class;
    }
}
