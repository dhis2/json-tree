/*
 * Copyright (c) 2004-2022, University of Oslo
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

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Spliterators.spliteratorUnknownSize;

/**
 * Standard implementation of the {@link JsonTypedAccessStore}.
 * <p>
 * On top of the {@link JsonGenericTypedAccessor}s that were added it automatically creates and adds an accessor for any
 * {@code enum} and any subtype of {@link JsonValue} when it is resolved via {@link #accessor(Class)}.
 *
 * @author Jan Bernitt
 * @since 0.4
 */
public final class JsonTypedAccess implements JsonTypedAccessStore {

    public static final JsonTypedAccess GLOBAL = new JsonTypedAccess().init();

    private final Map<Class<?>, JsonGenericTypedAccessor<?>> byResultType = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings( "unchecked" )
    public <T> JsonGenericTypedAccessor<T> accessor( Class<T> type ) {
        JsonGenericTypedAccessor<T> res = (JsonGenericTypedAccessor<T>) byResultType.get( type );
        if ( res != null ) {
            return res;
        }
        if ( type.isEnum() ) {
            // automatically provide enum mapping
            return (JsonGenericTypedAccessor<T>) byResultType.get( Enum.class );
        }
        if ( JsonValue.class.isAssignableFrom( type ) ) {
            // automatically provide JsonValue subtype mapping
            return (JsonGenericTypedAccessor<T>) byResultType.get( JsonValue.class );
        }
        return null;
    }

    public <T> JsonTypedAccess add( Class<T> returnType, JsonTypedAccessor<T> accessor ) {
        byResultType.put( returnType, accessor );
        return this;
    }

    public <T> JsonTypedAccess add( Class<T> returnType, JsonGenericTypedAccessor<T> accessor ) {
        byResultType.put( returnType, accessor );
        return this;
    }

    public JsonTypedAccess init() {
        return add( String.class, ( obj, name ) -> obj.getString( name ).string() )
            .add( boolean.class, ( obj, name ) -> obj.getBoolean( name ).booleanValue() )
            .add( char.class, JsonTypedAccess::accessChar )
            .add( int.class, ( obj, name ) -> obj.getNumber( name ).intValue() )
            .add( long.class, ( obj, name ) -> obj.getNumber( name ).longValue() )
            .add( float.class, ( obj, name ) -> obj.getNumber( name ).floatValue() )
            .add( double.class, ( obj, name ) -> obj.getNumber( name ).doubleValue() )
            .add( Boolean.class, ( obj, name ) -> obj.getBoolean( name ).bool() )
            .add( Character.class, ( obj, name ) -> obj.getString( name ).parsed( str -> str.charAt( 0 ) ) )
            .add( Integer.class, ( obj, name ) -> orNull( obj.getNumber( name ).number(), Number::intValue ) )
            .add( Long.class, ( obj, name ) -> orNull( obj.getNumber( name ).number(), Number::longValue ) )
            .add( Float.class, ( obj, name ) -> orNull( obj.getNumber( name ).number(), Number::floatValue ) )
            .add( Double.class, ( obj, name ) -> orNull( obj.getNumber( name ).number(), Number::doubleValue ) )
            .add( Number.class, ( obj, name ) -> obj.getNumber( name ).number() )
            .add( URL.class, ( obj, name ) -> obj.get( name, JsonURL.class ).url() )
            .add( UUID.class, ( obj, name ) -> obj.getString( name ).parsed( UUID::fromString ) )
            .add( LocalDateTime.class, ( obj, name ) -> obj.get( name, JsonDate.class ).date() )
            .add( LocalDate.class, ( obj, name ) -> obj.get( name, JsonDate.class ).dateOnly() )
            .add( LocalTime.class, ( obj, name ) -> obj.get( name, JsonDate.class ).timeOnly() )
            .add( Date.class, JsonTypedAccess::accessDate )

            // JSON generic type
            .add( JsonList.class,
                ( obj, name, to, store ) -> obj.getList( name, extractJsonValueTypeParameter( to, 0 ) ) )
            .add( JsonMap.class,
                ( obj, name, to, store ) -> obj.getMap( name, extractJsonValueTypeParameter( to, 0 ) ) )
            .add( JsonMultiMap.class,
                ( obj, name, to, store ) -> obj.getMultiMap( name, extractJsonValueTypeParameter( to, 0 ) ) )

            // JDK generic type
            .add( List.class, JsonTypedAccess::accessList )
            .add( Iterable.class, JsonTypedAccess::accessList )
            .add( Set.class, JsonTypedAccess::accessSet )
            .add( Map.class, JsonTypedAccess::accessMap )
            .add( Stream.class, JsonTypedAccess::accessStream )
            .add( Iterator.class, JsonTypedAccess::accessIterator )
            .add( Optional.class, JsonTypedAccess::accessOptional )

            // type-sets
            .add( Enum.class, ( obj, name, to, store ) -> obj.getString( name ).parsed(
                str -> asEnumConstant( getRawType( to, Enum.class ), str ) ) )
            .add( JsonValue.class,
                ( obj, name, to, store ) -> obj.get( name ).as( getRawType( to, JsonValue.class ) ) );
    }

    private static <A, B> B orNull( A a, Function<A, B> f ) {
        return a == null ? null : f.apply( a );
    }

    private static char accessChar( JsonObject obj, String path ) {
        String str = obj.getString( path ).string();
        if ( str == null || str.isEmpty() ) {
            throw new JsonPathException( path, "No character for property " + path );
        }
        return str.charAt( 0 );
    }

    public static Date accessDate( JsonObject obj, String path ) {
        JsonValue date = obj.get( path );
        if ( date.isUndefined() ) {
            return null;
        }
        if ( date.node().getType() == JsonNodeType.NUMBER ) {
            return new Date( date.as( JsonNumber.class ).longValue() );
        }
        return Date.from( LocalDateTime.parse( date.as( JsonString.class ).string() ).toInstant( ZoneOffset.UTC ) );
    }

    public static Optional<?> accessOptional( JsonObject from, String path, Type to, JsonTypedAccessStore store ) {
        JsonValue v = from.get( path );
        if ( v.isUndefined() ) {
            return Optional.empty();
        }
        Type valueType = extractTypeParameter( to, 0 );
        JsonGenericTypedAccessor<?> accessor = store.accessor( getRawType( valueType ) );
        return Optional.ofNullable( accessor.access( from, path, valueType, store ) );
    }

    /**
     * Accessors always assume to work relative to a parent object. Therefore, when accessing list elements the
     * resolution is not using the list as root but the object that contains the list.
     */
    @SuppressWarnings( { "java:S1168", "java:S1452" } )
    public static List<?> accessList( JsonObject from, String path, Type to, JsonTypedAccessStore store ) {
        JsonList<?> list = from.getList( path, JsonValue.class );
        if ( list.isUndefined() ) {
            return null;
        }
        if ( list.isEmpty() ) {
            return List.of();
        }
        Type elementType = extractTypeParameter( to, 0 );
        JsonGenericTypedAccessor<?> elementAccess = store.accessor( getRawType( elementType ) );
        int size = list.size();
        List<Object> res = new ArrayList<>( size );
        for ( int i = 0; i < size; i++ ) {
            res.add( elementAccess.access( from, path + "[" + i + "]", elementType, store ) );
        }
        return res;
    }

    @SuppressWarnings( "java:S1452" )
    public static Set<?> accessSet( JsonObject from, String path, Type to, JsonTypedAccessStore store ) {
        List<?> list = accessList( from, path, to, store );
        return list == null ? null : new HashSet<>( list );
    }

    @SuppressWarnings( { "java:S1168", "java:S1452" } )
    public static Map<?, ?> accessMap( JsonObject from, String path, Type to, JsonTypedAccessStore store ) {
        JsonObject map = from.getObject( path );
        if ( map.isUndefined() ) {
            return null;
        }
        if ( map.isEmpty() ) {
            return Map.of();
        }
        Type valueType = extractTypeParameter( to, 1 );
        JsonGenericTypedAccessor<?> valueAccess = store.accessor( getRawType( valueType ) );
        Class<?> rawKeyType = getRawType( extractTypeParameter( to, 0 ) );
        Function<String, Object> toKey = getKeyMapper( rawKeyType );
        @SuppressWarnings( { "rawtypes", "unchecked" } )
        Map<Object, Object> res = rawKeyType.isEnum() ? new EnumMap( rawKeyType ) : new HashMap<>();
        map.keys().forEach( key ->
            res.put( toKey.apply( key ), valueAccess.access( map, key, valueType, store ) ));
        return res;
    }

    public static Stream<?> accessStream( JsonObject from, String path, Type to, JsonTypedAccessStore store ) {
        JsonList<?> seq = from.getList( path, JsonValue.class );
        if ( seq.isUndefined() ) {
            return Stream.empty();
        }
        Iterator<?> iter = accessIterator( from, path, to, store );
        return StreamSupport.stream( spliteratorUnknownSize( iter, Spliterator.ORDERED ), false );
    }

    public static Iterator<?> accessIterator( JsonObject from, String path, Type to, JsonTypedAccessStore store ) {
        JsonList<?> seq = from.getList( path, JsonValue.class );
        if ( seq.isUndefined() ) {
            return List.of().listIterator();
        }
        int size = seq.size();
        Type elementType = extractTypeParameter( to, 0 );
        JsonGenericTypedAccessor<?> elementAccess = store.accessor( getRawType( elementType ) );
        return new Iterator<>() {
            int i = 0;

            @Override
            public boolean hasNext() {
                return i < size;
            }

            @Override
            public Object next() {
                if ( !hasNext() )
                    throw new NoSuchElementException( "next() called without checking hasNext()" );
                Object value = elementAccess.access( from, path + "[" + i + "]", elementType, store );
                i++;
                return value;
            }
        };
    }

    /**
     * Map keys are really object member names and as such not values so accessors cannot be used to map them.
     * Therefore, a handful of type are supported explicitly.
     */
    private static Function<String, Object> getKeyMapper( Class<?> rawKeyType ) {
        if ( rawKeyType.isEnum() ) {
            return key -> asEnumConstant( rawKeyType, key );
        }
        if ( String.class == rawKeyType ) {
            return key -> key;
        }
        if ( Integer.class == rawKeyType ) {
            return Integer::parseInt;
        }
        if ( Long.class == rawKeyType ) {
            return Long::parseLong;
        }
        // any other number as long as Double works
        if ( rawKeyType.isAssignableFrom( Double.class ) ) {
            return Double::parseDouble;
        }
        if ( Character.class == rawKeyType ) {
            return str -> str.charAt( 0 );
        }
        if ( Boolean.class == rawKeyType ) {
            return Boolean::getBoolean;
        }
        throw new UnsupportedOperationException( "Unsupported map key type: " + rawKeyType );
    }

    public static Class<?> getRawType( Type type ) {
        return getRawType( type, Object.class );
    }

    @SuppressWarnings( { "unchecked", "unused" } )
    public static <T> Class<? extends T> getRawType( Type type, Class<T> base ) {
        return (Class<T>) (type instanceof ParameterizedType pt
            ? pt.getRawType()
            : type);
    }

    public static Type extractTypeParameter( Type from, int n ) {
        return ((ParameterizedType) from).getActualTypeArguments()[n];
    }

    public static Class<? extends JsonValue> extractJsonValueTypeParameter( Type from, int n ) {
        return getRawType( extractTypeParameter( from, n ), JsonValue.class );
    }

    @SuppressWarnings( { "rawtypes", "unchecked" } )
    private static Enum<?> asEnumConstant( Class type, String str ) {
        return Enum.valueOf( type, str );
    }
}
