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

import org.hisp.dhis.jsontree.JsonTypedAccessStore.JsonGenericTypedAccessor;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Stream;

import static java.lang.Character.isUpperCase;
import static java.lang.Character.toLowerCase;
import static java.util.Collections.emptyList;

/**
 * Implements the {@link JsonValue} read-only access abstraction for JSON responses.
 * <p>
 * The way this works is that internally when navigating the JSON object the {@link #path} is extended. The returned
 * value is always either a {@link JsonResponse} or a {@link Proxy} calling all default methods on the declaring
 * interface and all implemented by {@link JsonResponse} on a "wrapped" {@link JsonResponse} instance.
 * <p>
 * When values are accessed the {@link #path} path is extracted from the {@link #content} and eventually converted and
 * checked against expectations. Exceptions are eventually converted to provide a consistent behaviour.
 * <p>
 * It is crucial to understand that the complete JSON object model is purely a typed convenience layer expressing what
 * could or is expected to exist. Whether something actually exist is first evaluated when leaf values are accessed or
 * existence is explicitly checked using {@link #exists()}.
 * <p>
 * This also means specific {@link JsonObject}s are modelled by extending the interface and implementing {@code default}
 * methods. No other implementation class is ever written except {@link JsonResponse}.
 *
 * @author Jan Bernitt
 */
public final class JsonResponse implements JsonObject, JsonArray, JsonString, JsonNumber, JsonBoolean, Serializable {
    public static final JsonResponse NULL = new JsonResponse( JsonNode.of( "null" ), "$", JsonTypedAccess.GLOBAL,
        null );

    private final JsonNode content;

    private final String path;

    private final transient JsonTypedAccessStore store;

    private final transient ConcurrentMap<String, Object> accessCache;

    public JsonResponse( String content, JsonTypedAccessStore store ) {
        this( JsonNode.of( content.isEmpty() ? "{}" : content ), "$", store, null );
    }

    private JsonResponse( JsonNode content, String path, JsonTypedAccessStore store,
        ConcurrentMap<String, Object> accessCache ) {
        this.content = content;
        this.path = path;
        this.store = store;
        this.accessCache = accessCache;
    }

    @Override
    public boolean isAccessCached() {
        return accessCache != null;
    }

    @Override
    public JsonResponse withAccessCached() {
        return isAccessCached() ? this : new JsonResponse( content, path, store, new ConcurrentHashMap<>() );
    }

    private <T> T value( JsonNodeType expected, Function<JsonNode, T> get, Function<JsonPathException, T> orElse ) {
        try {
            JsonNode node = content.get( path );
            JsonNodeType actualType = node.getType();
            if ( actualType == JsonNodeType.NULL ) {
                return null;
            }
            if ( actualType != expected ) {
                throw new UnsupportedOperationException(
                    String.format( "Path `%s` does not contain a %s but a(n) %s: %s",
                        path, expected, actualType, node ) );
            }
            return get.apply( node );
        }
        catch ( JsonPathException ex ) {
            return orElse.apply( ex );
        }
    }

    private <T> T value( Function<JsonNode, T> get ) {
        try {
            return get.apply( content.get( path ) );
        }
        catch ( JsonPathException | JsonFormatException ex ) {
            throw noSuchElement( ex );
        }
    }

    private JsonNode value() {
        return value( Function.identity() );
    }

    @Override
    public <T extends JsonValue> T get( int index, Class<T> as ) {
        return asType( as, new JsonResponse( content, path + "[" + index + "]", store, accessCache ) );
    }

    @Override
    public <T extends JsonValue> T get( String name, Class<T> as ) {
        String p = name.startsWith( "{" ) ? path + name : path + "." + name;
        return asType( as, new JsonResponse( content, p, store, accessCache ) );
    }

    @Override
    public <T extends JsonValue> T as( Class<T> as ) {
        return asType( as, this );
    }

    @SuppressWarnings( "unchecked" )
    private <T extends JsonValue> T asType( Class<T> as, JsonResponse res ) {
        return isExtended( as ) ? createProxy( as, res ) : (T) res;
    }

    @Override
    public <A, B> B mapNonNull( A from, Function<A, B> to ) {
        if ( from == null ) {
            try {
                content.get( path );
            }
            catch ( JsonPathException ex ) {
                throw noSuchElement( ex );
            }
        }
        return to.apply( from );
    }

    @Override
    public JsonNode node() {
        return value();
    }

    @Override
    public boolean isEmpty() {
        return value().isEmpty();
    }

    @Override
    public boolean has( String... names ) {
        return Boolean.TRUE.equals( value( JsonNodeType.OBJECT,
            node -> node.members().keySet().containsAll( Arrays.asList( names ) ),
            ex -> false ) );
    }

    @Override
    public List<String> stringValues() {
        return arrayList( String.class );
    }

    @Override
    public List<Number> numberValues() {
        return arrayList( Number.class );
    }

    @Override
    public List<Boolean> boolValues() {
        return arrayList( Boolean.class );
    }

    @SuppressWarnings( "unchecked" )
    private <T> List<T> arrayList( Class<T> elementType ) {
        return value( JsonNodeType.ARRAY, node -> {
            List<T> res = new ArrayList<>();
            for ( JsonNode e : node.elements() ) {
                Object value = e.value();
                if ( !elementType.isInstance( value ) ) {
                    throw new IllegalArgumentException( "element is not a " + elementType );
                }
                res.add( (T) value );
            }
            return res;
        }, ex -> emptyList() );
    }

    @Override
    public int size() {
        return value().size();
    }

    @Override
    public boolean isArray() {
        return value().getType() == JsonNodeType.ARRAY;
    }

    @Override
    public boolean isObject() {
        return value().getType() == JsonNodeType.OBJECT;
    }

    @Override
    public Boolean bool() {
        return (Boolean) value( JsonNodeType.BOOLEAN, JsonNode::value, ex -> null );
    }

    @Override
    public Number number() {
        return (Number) value( JsonNodeType.NUMBER, JsonNode::value, ex -> null );
    }

    @Override
    public String string() {
        return (String) value( JsonNodeType.STRING, JsonNode::value, ex -> null );
    }

    @Override
    public boolean exists() {
        try {
            return content.get( path ) != null;
        }
        catch ( JsonPathException ex ) {
            return false;
        }
    }

    @Override
    public boolean isNull() {
        return value().getType() == JsonNodeType.NULL;
    }

    @Override
    public boolean equals( Object obj ) {
        return obj instanceof JsonResponse response
            && path.equals( response.path )
            && content.equals( response.content );
    }

    @Override
    public int hashCode() {
        return Objects.hash( content, path );
    }

    @Override
    public String toString() {
        try {
            return content.get( path ).getDeclaration();
        }
        catch ( JsonPathException | JsonFormatException ex ) {
            return ex.getMessage();
        }
    }

    private NoSuchElementException noSuchElement( RuntimeException cause ) {
        return new NoSuchElementException(cause);
    }

    @SuppressWarnings( "unchecked" )
    private <E extends JsonValue> E createProxy( Class<E> as, JsonResponse inner ) {
        return (E) Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(), new Class[] { as },
            ( proxy, method, args ) -> {
                // are we dealing with a default method in the extending class?
                Class<?> declaringClass = method.getDeclaringClass();
                if ( isExtended( declaringClass ) ) {
                    if ( method.isDefault() ) {
                        // call the default method of the proxied type itself
                        return callDefaultMethod( proxy, method, declaringClass );
                    }
                    // abstract extending interface method?
                    return callAbstractMethod( inner, method, args );
                }
                // call the same method on the wrapped object (assuming it has
                // it)
                return callCoreApiMethod( inner, method, args );
            } );
    }

    /**
     * Any default methods implemented by an extension of the {@link JsonValue} class tree is run by calling the default
     * as defined in the interface. This is sadly not as straight forward as it might sound.
     */
    private static Object callDefaultMethod( Object proxy, Method method, Class<?> declaringClass )
        throws Throwable {
        if ( !isJava8() ) {
            return MethodHandles.lookup()
                .findSpecial( declaringClass, method.getName(),
                    MethodType.methodType( method.getReturnType(), method.getParameterTypes() ),
                    declaringClass )
                .bindTo( proxy ).invokeWithArguments();
        }
        Constructor<Lookup> constructor = Lookup.class
            .getDeclaredConstructor( Class.class );
        constructor.trySetAccessible();
        return constructor.newInstance( declaringClass )
            .in( declaringClass )
            .unreflectSpecial( method, declaringClass )
            .bindTo( proxy )
            .invokeWithArguments();
    }

    /**
     * Abstract interface methods are "implemented" by deriving an {@link JsonGenericTypedAccessor} from the method's
     * return type and have the accessor extract the value by using solely the underlying {@link JsonValue} API.
     */
    private Object callAbstractMethod( JsonResponse inner, Method method, Object[] args ) {
        JsonObject obj = inner.asObject();
        Class<?> resType = method.getReturnType();
        String name = stripGetterPrefix( method );
        boolean hasDefault = method.getParameterCount() == 1 && method.getParameterTypes()[0] == resType;
        if ( obj.get( name ).isUndefined() && hasDefault ) {
            return args[0];
        }
        if ( accessCache != null && isCacheable( resType ) ) {
            String keyId = inner.path + "." + name + ":" + toSignature( method.getGenericReturnType() );
            return accessCache.computeIfAbsent( keyId, key -> access( method, obj, name ) );
        }
        return access( method, obj, name );
    }

    private Object access( Method method, JsonObject obj, String name ) {
        Type genericType = method.getGenericReturnType();
        JsonTypedAccessStore accessStore = store == null ? JsonTypedAccess.GLOBAL : store;
        JsonGenericTypedAccessor<?> accessor = accessStore.accessor( method.getReturnType() );
        if ( accessor != null ) {
            return accessor.access( obj, name, genericType, accessStore );
        }
        throw new UnsupportedOperationException( "No accessor registered for type: " + genericType );
    }

    /**
     * All methods by the core API of the general JSON tree represented as {@link JsonValue}s (and the general
     * subclasses) are implemented by the {@link JsonResponse} wrapper so they can be called directly.
     */
    private static Object callCoreApiMethod( JsonValue inner, Method method, Object[] args )
        throws Throwable {
        try {
            return method.invoke( inner, args );
        }
        catch ( InvocationTargetException ex ) {
            throw ex.getTargetException();
        }
    }

    /**
     * This is twofold: Concepts like {@link Stream} and {@link Iterator} should not be cached to work correctly.
     * <p>
     * For all other types this is about reaching a balance between memory usage and CPU usage. Simple objects are
     * recomputed whereas complex objects are not.
     */
    private boolean isCacheable( Class<?> resType ) {
        return resType.isInterface()
            && !Stream.class.isAssignableFrom( resType )
            && !Iterator.class.isAssignableFrom( resType )
            && !JsonPrimitive.class.isAssignableFrom( resType );
    }

    private static boolean isExtended( Class<?> declaringClass ) {
        return !declaringClass.isAssignableFrom( JsonResponse.class );
    }

    private static boolean isJava8() {
        String javaVersion = System.getProperty( "java.version" );
        boolean javaVersionIsBlank = javaVersion.trim().isEmpty();
        return !javaVersionIsBlank && javaVersion.startsWith( "1.8" );
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
        }
        else {
            str.append( '?' );
        }
    }
}
