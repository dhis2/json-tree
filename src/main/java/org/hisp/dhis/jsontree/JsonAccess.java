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
import java.util.Collections;
import java.util.Date;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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

import static java.util.Collections.emptyIterator;
import static java.util.Spliterators.spliterator;
import static java.util.Spliterators.spliteratorUnknownSize;

/**
 * Standard implementation of the {@link JsonAccessors}.
 * <p>
 * On top of the {@link JsonAccessor}s that were added it automatically creates and adds an accessor for any
 * {@code enum} and any subtype of {@link JsonValue} when it is resolved via {@link #accessor(Class)}.
 *
 * @author Jan Bernitt
 * @since 1.9 (in the refactored form, earlier version had a similar concept since 0.4)
 */
public final class JsonAccess implements JsonAccessors {

    /**
     * Default {@link JsonAccessors} repository. This will be used for all {@link JsonValue} instances
     * that have been de-serialized from Java's serialisation. Which is why even this instances still
     * allows to add {@link JsonAccessor} functions. While this instance is initialized with default
     * functions they can be overridden by registering another function for the same type.
     */
    public static final JsonAccess GLOBAL = new JsonAccess().init();

    private final Map<Class<?>, JsonAccessor<?>> byResultType = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings( "unchecked" )
    public <T> JsonAccessor<T> accessor( Class<T> type ) {
        JsonAccessor<T> res = (JsonAccessor<T>) byResultType.get( type );
        if ( res != null ) {
            return res;
        }
        if ( type.isEnum() ) {
            // automatically provide enum mapping
            return (JsonAccessor<T>) byResultType.get( Enum.class );
        }
        if ( JsonValue.class.isAssignableFrom( type ) ) {
            // automatically provide JsonValue subtype mapping
            return (JsonAccessor<T>) byResultType.get( JsonValue.class );
        }
        throw new UnsupportedOperationException( "No accessor registered for type: " + type );
    }

    public <T> JsonAccess add( Class<T> returnType, SimpleJsonAccessor<T> accessor ) {
        byResultType.put( returnType, accessor );
        return this;
    }

    public <T> JsonAccess add( Class<T> returnType, JsonAccessor<T> accessor ) {
        byResultType.put( returnType, accessor );
        return this;
    }

    public JsonAccess init() {
        return add( String.class, JsonAccess::accessAsString )
            .add( boolean.class, JsonAccess::accessAsPrimitiveBoolean )
            .add( char.class, JsonAccess::accessAsPrimitiveCharacter )
            .add( int.class, ( obj, name ) -> accessAsPrimitiveNumber(obj, name, Number::intValue) )
            .add( long.class, ( obj, name ) -> accessAsPrimitiveNumber(obj, name, Number::longValue) )
            .add( float.class, ( obj, name ) -> accessAsPrimitiveNumber(obj, name, Number::floatValue) )
            .add( double.class, ( obj, name ) -> accessAsPrimitiveNumber(obj, name, Number::doubleValue) )
            .add( Boolean.class, JsonAccess::accessAsBoolean )
            .add( Character.class, JsonAccess::accessAsCharacter )
            .add( Integer.class, ( obj, name ) -> accessAsNumber( obj, name, Number::intValue ) )
            .add( Long.class, ( obj, name ) -> accessAsNumber( obj, name, Number::longValue ) )
            .add( Float.class, ( obj, name ) -> accessAsNumber( obj, name, Number::floatValue ) )
            .add( Double.class, ( obj, name ) -> accessAsNumber( obj, name, Number::doubleValue ) )
            .add( Number.class, ( obj, name ) -> accessAsNumber( obj, name, Function.identity() ) )
            .add( URL.class, ( obj, name ) -> obj.get( name, JsonURL.class ).url() )
            .add( UUID.class, ( obj, name ) -> obj.getString( name ).parsed( UUID::fromString ) )
            .add( LocalDateTime.class, ( obj, name ) -> obj.get( name, JsonDate.class ).date() )
            .add( LocalDate.class, ( obj, name ) -> obj.get( name, JsonDate.class ).dateOnly() )
            .add( LocalTime.class, ( obj, name ) -> obj.get( name, JsonDate.class ).timeOnly() )
            .add( Date.class, JsonAccess::accessAsDate )

            // JSON generic type
            .add( JsonList.class,
                ( obj, name, to, store ) -> obj.getList( name, extractJsonValueTypeParameter( to, 0 ) ) )
            .add( JsonMap.class,
                ( obj, name, to, store ) -> obj.getMap( name, extractJsonValueTypeParameter( to, 0 ) ) )
            .add( JsonMultiMap.class,
                ( obj, name, to, store ) -> obj.getMultiMap( name, extractJsonValueTypeParameter( to, 0 ) ) )

            // JDK generic type
            .add( List.class, JsonAccess::accessAsList )
            .add( Iterable.class, JsonAccess::accessAsList )
            .add( Set.class, JsonAccess::accessAsSet )
            .add( Map.class, JsonAccess::accessAsMap )
            .add( Stream.class, JsonAccess::accessAsStream )
            .add( Iterator.class, JsonAccess::accessAsIterator )
            .add( Optional.class, JsonAccess::accessAsOptional )

            // type-families
            .add( Enum.class, ( obj, name, to, store ) -> obj.getString( name ).parsed(
                str -> asEnumConstant( getRawType( to, Enum.class ), str ) ) )
            .add( JsonValue.class,
                ( obj, name, to, store ) -> obj.get( name ).as( getRawType( to, JsonValue.class ) ) );
    }

    public static String accessAsString(JsonObject obj, String path) {
        JsonString str = obj.getString( path );
        if (str.isUndefined()) return null;
        if (str.isString()) return str.string();
        if (str.isNumber() || str.isBoolean()) return str.toJson();
        throw new JsonAccessException( "JSON does not map to a Java String: "+str );
    }

    public static Boolean accessAsPrimitiveBoolean(JsonObject obj, String path) {
        Boolean val = accessAsBoolean( obj, path );
        if (val == null) throw new JsonAccessException( "JSON is undefined, Java is a primitive boolean" );
        return val;
    }

    public static Boolean accessAsBoolean(JsonObject obj, String path) {
        JsonBoolean bool = obj.getBoolean( path );
        if (bool.isUndefined()) return null;
        if (bool.isBoolean()) return bool.booleanValue();
        if (bool.isNumber()) {
            double val = bool.as( JsonNumber.class ).doubleValue();
            if (val == 0.0d) return false;
            if (val == 1.0d) return true;
        } else if (bool.isString()) {
            String val = bool.as( JsonString.class ).string().toLowerCase();
            if ("on".equals( val ) || "yes".equals( val ) || "true".equals( val ) || "t".equals( val )) return true;
            if ("off".equals( val ) || "no".equals( val ) || "false".equals( val ) || "f".equals( val )) return false;
        }
        throw new JsonAccessException( "JSON does not map to a Java Boolean: "+bool );
    }

    public static <T extends Number> T accessAsPrimitiveNumber(JsonObject obj, String path, Function<Number, T> as) {
        T val = accessAsNumber( obj, path, as );
        if (val == null) throw new JsonAccessException( "JSON is undefined, Java is a primitive number" );
        return val;
    }

    public static <T extends Number> T accessAsNumber(JsonObject obj, String path, Function<Number, T> as) {
        JsonNumber number = obj.getNumber( path );
        if (number.isUndefined()) return null;
        if (number.isNumber()) return as.apply( number.number() );
        if (number.isString()) return as.apply( Double.parseDouble( number.as( JsonString.class).string() ));
        if (number.isBoolean()) return as.apply( number.as( JsonBoolean.class ).booleanValue() ? 1 : 0 );
        throw new JsonAccessException( "JSON does not map to a Java Number: "+number );
    }

    public static Character accessAsPrimitiveCharacter( JsonObject obj, String path ) {
        Character val = accessAsCharacter( obj, path );
        if (val == null) throw new JsonAccessException( "JSON is undefined, Java is a primitive char" );
        return val;
    }

    public static Character accessAsCharacter( JsonObject obj, String path ) {
        JsonString str = obj.getString( path );
        if (str.isUndefined()) return null;
        if (str.isString()) {
            String val = str.string();
            return val.isEmpty() ? null : val.charAt( 0 );
        }
        if (str.isNumber()) return str.toJson().charAt(0);
        throw new JsonAccessException( "JSON does not map to a Java Character: "+str );
    }

    public static Date accessAsDate(JsonObject obj, String path) {
        JsonValue date = obj.get(path);
        if (date.isUndefined()) return null;
        if (date.isNumber()) return new Date(date.as(JsonNumber.class).longValue());
        if (date.isString())
            return Date.from(LocalDateTime.parse(date.as(JsonString.class).string())
                .toInstant(ZoneOffset.UTC));
        throw new JsonAccessException( "JSON does not map to a Java Date: "+date );
    }

    public static Optional<?> accessAsOptional(
        JsonObject obj, String path, Type to, JsonAccessors store) {
        JsonValue v = obj.get( path );
        if ( v.isUndefined() )  return Optional.empty();
        Type valueType = extractTypeParameter( to, 0 );
        JsonAccessor<?> value = store.accessor( getRawType( valueType ) );
        return Optional.ofNullable( value.access( obj, path, valueType, store ) );
    }

    @SuppressWarnings( { "java:S1168", "java:S1452" } )
    public static List<?> accessAsList( JsonObject obj, String path, Type to, JsonAccessors store ) {
        JsonList<?> list = obj.getList( path, JsonValue.class );
        if (list.isUndefined()) return null;
        if (list.isArray() && list.isEmpty()) return List.of();
        int size = list.isArray() ? list.size() : 1;
        List<Object> res = new ArrayList<>( size );
        accessAsIterator( obj, path, to, store ).forEachRemaining( res::add );
        return res;
    }

    @SuppressWarnings( "java:S1452" )
    public static Set<?> accessAsSet( JsonObject obj, String path, Type to, JsonAccessors store ) {
        JsonList<?> list = obj.getList( path, JsonValue.class );
        if (list.isUndefined()) return null;
        if (list.isArray() && list.isEmpty()) return Set.of();
        Class<?> eRawType = getRawType( extractTypeParameter( to, 0 ) );
        @SuppressWarnings({"unchecked", "rawtypes"})
        Set<Object> res =
            eRawType.isEnum() ? (Set) EnumSet.noneOf((Class<Enum>) eRawType) : new LinkedHashSet<>();
        accessAsIterator( obj, path, to, store ).forEachRemaining( res::add );
        return res;
    }

    @SuppressWarnings( { "java:S1168", "java:S1452" } )
    public static Map<?, ?> accessAsMap( JsonObject obj, String path, Type to, JsonAccessors store ) {
        JsonObject map = obj.getObject( path );
        if (map.isUndefined()) return null;
        if (map.isEmpty()) return Map.of();
        Type valueType = extractTypeParameter( to, 1 );
        JsonAccessor<?> valueAccess = store.accessor( getRawType( valueType ) );
        Class<?> rawKeyType = getRawType( extractTypeParameter( to, 0 ) );
        Function<String, ?> toKey = getKeyMapper( rawKeyType );
        @SuppressWarnings({"rawtypes", "unchecked"})
        Map<Object, Object> res = rawKeyType.isEnum() ? new EnumMap(rawKeyType) : new LinkedHashMap<>();
        map.keys().forEach( key ->
            res.put( toKey.apply( key ), valueAccess.access( map, key, valueType, store ) ));
        return res;
    }

    public static Stream<?> accessAsStream( JsonObject obj, String path, Type to, JsonAccessors store ) {
        JsonList<?> seq = obj.getList( path, JsonValue.class );
        if ( seq.isUndefined() || seq.isArray() && seq.isEmpty() ) return Stream.empty();
        if (seq.isObject()) throw new JsonAccessException( "JSON does not map to Java Stream: "+seq );
        int size = seq.isArray() ? seq.size() : 1;
        Iterator<?> iter = accessAsIterator( obj, path, to, store );
        return StreamSupport.stream( spliterator( iter, size, Spliterator.ORDERED ), false );
    }

    public static Iterator<?> accessAsIterator( JsonObject obj, String path, Type to, JsonAccessors store ) {
        JsonList<?> seq = obj.getList( path, JsonValue.class );
        if ( seq.isUndefined() || seq.isArray() && seq.isEmpty() ) return emptyIterator();
        if (seq.isObject()) throw new JsonAccessException( "JSON does not map to Java Iterator: "+seq );
        Type elementType = extractTypeParameter( to, 0 );
        JsonAccessor<?> elements = store.accessor( getRawType( elementType ) );
        if (!seq.isArray()) return List.of(elements.access( obj, path, to, store )).iterator();
        int size = seq.size();
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
                return elements.access( obj, path + "[" + i++ + "]", elementType, store );
            }
        };
    }

    /**
     * Map keys are really object member names and as such not values so accessors cannot be used to map them.
     * Therefore, a handful of type are supported explicitly.
     */
    private static Function<String, ?> getKeyMapper( Class<?> rawKeyType ) {
        if (rawKeyType.isEnum()) return key -> asEnumConstant(rawKeyType, key);
        if (String.class == rawKeyType) return key -> key;
        if (Integer.class == rawKeyType) return Integer::parseInt;
        if (Long.class == rawKeyType) return Long::parseLong;
        if (Double.class == rawKeyType) return Double::parseDouble;
        if (Character.class == rawKeyType) return str -> str.charAt(0);
        if (Boolean.class == rawKeyType) return Boolean::getBoolean;
        throw new UnsupportedOperationException("Unsupported map key type: " + rawKeyType);
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
