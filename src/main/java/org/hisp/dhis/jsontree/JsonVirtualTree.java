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
import org.hisp.dhis.jsontree.internal.Maybe;
import org.hisp.dhis.jsontree.internal.Surly;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
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

    public static final JsonMixed NULL = new JsonVirtualTree( JsonNode.NULL, "$", JsonTypedAccess.GLOBAL, null );

    private final @Surly JsonNode root;
    private final @Surly String path;
    private final transient @Surly JsonTypedAccessStore store;
    private final transient @Maybe ConcurrentMap<String, Object> accessCache;

    public JsonVirtualTree(@Maybe String json, @Surly JsonTypedAccessStore store ) {
        this( json == null || json.isEmpty() ? JsonNode.EMPTY_OBJECT : JsonNode.of( json ), "$", store, null );
    }

    public JsonVirtualTree(@Surly JsonNode root, @Surly JsonTypedAccessStore store ) {
        this( root, "$", store, null );
    }

    private JsonVirtualTree(@Surly JsonNode root, @Surly String path, @Surly JsonTypedAccessStore store,
        @Maybe ConcurrentMap<String, Object> accessCache ) {
        this.root = root;
        this.path = path;
        this.store = store;
        this.accessCache = accessCache;
    }

    @Surly @Override
    public JsonTypedAccessStore getAccessStore() {
        return store;
    }

    @Override
    public boolean isAccessCached() {
        return accessCache != null;
    }

    @Override
    public JsonVirtualTree withAccessCached() {
        return isAccessCached() ? this : new JsonVirtualTree( root, path, store, new ConcurrentHashMap<>() );
    }

    @Override
    public Class<? extends JsonValue> asType() {
        return JsonMixed.class;
    }

    private <T> T value( JsonNodeType expected, Function<JsonNode, T> get, Function<JsonPathException, T> orElse ) {
        try {
            JsonNode node = root.get( path );
            JsonNodeType actualType = node.getType();
            if ( actualType == JsonNodeType.NULL ) {
                return null;
            }
            if ( actualType != expected ) {
                throw new JsonTreeException(
                    String.format( "Path `%s` does not contain an %s but a(n) %s: %s",
                        path, expected, actualType, node ) );
            }
            return get.apply( node );
        } catch ( JsonPathException ex ) {
            return orElse.apply( ex );
        }
    }

    private JsonNode value() {
        return root.get( path );
    }

    @Override
    public <T extends JsonValue> T get( int index, Class<T> as ) {
        return asType( as, new JsonVirtualTree( root, path + "[" + index + "]", store, accessCache ) );
    }

    @Override
    public <T extends JsonValue> T get( String name, Class<T> as ) {
        boolean isQualified = name.startsWith( "{" ) || name.startsWith( "." ) || name.startsWith( "[" );
        String p = isQualified ? path + name : path + "." + name;
        return asType( as, new JsonVirtualTree( root, p, store, accessCache ) );
    }

    @Override
    public <T extends JsonValue> T as( Class<T> as ) {
        return asType( as, this );
    }

    @SuppressWarnings( "unchecked" )
    private <T extends JsonValue> T asType( Class<T> as, JsonVirtualTree res ) {
        return isExtended( as ) ? createProxy( as, res ) : (T) res;
    }

    @Override
    public <A, B> B mapNonNull( A from, Function<A, B> to ) {
        if ( from == null ) {
            root.get( path ); // cause throw in case node does not exist
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
                    throw new JsonTreeException(
                        "Array element is not a " + elementType.getName() + ": " + e.getDeclaration() );
                }
                res.add( (T) value );
            }
            return res;
        }, ex -> List.of() );
    }

    @Override
    public int size() {
        return value().size();
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
            return root.get( path ) != null;
        } catch ( JsonPathException ex ) {
            return false;
        }
    }

    @Override
    public boolean equals( Object obj ) {
        return obj instanceof JsonVirtualTree response
            && path.equals( response.path )
            && root.equals( response.root );
    }

    @Override
    public int hashCode() {
        return Objects.hash( root, path );
    }

    @Override
    public String toString() {
        try {
            return root.get( path ).getDeclaration();
        } catch ( JsonPathException | JsonFormatException ex ) {
            return ex.getMessage();
        }
    }

    @SuppressWarnings( "unchecked" )
    private <E extends JsonValue> E createProxy( Class<E> as, JsonVirtualTree inner ) {
        return (E) Proxy.newProxyInstance(
            Thread.currentThread().getContextClassLoader(), new Class[] { as },
            ( proxy, method, args ) -> {
                // are we dealing with a default method in the extending class?
                Class<?> declaringClass = method.getDeclaringClass();
                if ( declaringClass == JsonValue.class && "asType".equals( method.getName() )
                    && method.getParameterCount() == 0 ) {
                    return as;
                }
                if ( isExtended( declaringClass ) ) {
                    if ( method.isDefault() ) {
                        // call the default method of the proxied type itself
                        return callDefaultMethod( proxy, method, declaringClass, args );
                    }
                    // abstract extending interface method?
                    return callAbstractMethod( inner, method, args );
                }
                // call the same method on the wrapped object (assuming it has it)
                return callCoreApiMethod( inner, method, args );
            } );
    }

    /**
     * Any default methods implemented by an extension of the {@link JsonValue} class tree is run by calling the default
     * as defined in the interface. This is sadly not as straight forward as it might sound.
     */
    private static Object callDefaultMethod( Object proxy, Method method, Class<?> declaringClass, Object[] args )
        throws Throwable {
        return MethodHandles.lookup()
            .findSpecial( declaringClass, method.getName(),
                MethodType.methodType( method.getReturnType(), method.getParameterTypes() ),
                declaringClass )
            .bindTo( proxy ).invokeWithArguments(args);
    }

    /**
     * Abstract interface methods are "implemented" by deriving an {@link JsonGenericTypedAccessor} from the method's
     * return type and have the accessor extract the value by using the underlying {@link JsonValue} API.
     */
    private Object callAbstractMethod( JsonVirtualTree inner, Method method, Object[] args ) {
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
     * subclasses) are implemented by the {@link JsonVirtualTree} wrapper, so they can be called directly.
     */
    private static Object callCoreApiMethod( JsonVirtualTree inner, Method method, Object[] args )
        throws Throwable {
        return args == null || args.length == 0
            ? MethodHandles.lookup().unreflect( method ).invokeWithArguments( inner )
            : MethodHandles.lookup().unreflect( method ).bindTo( inner ).invokeWithArguments( args );
    }

    /**
     * This is twofold: Concepts like {@link Stream} and {@link Iterator} should not be cached to work correctly.
     * <p>
     * For all other type this is about reaching a balance between memory usage and CPU usage. Simple objects are
     * recomputed whereas complex objects are not.
     */
    private boolean isCacheable( Class<?> resType ) {
        return resType.isInterface()
            && !Stream.class.isAssignableFrom( resType )
            && !Iterator.class.isAssignableFrom( resType )
            && !JsonPrimitive.class.isAssignableFrom( resType );
    }

    private static boolean isExtended( Class<?> declaringClass ) {
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
}
