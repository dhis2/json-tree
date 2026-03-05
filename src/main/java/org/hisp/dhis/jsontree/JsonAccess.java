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

import java.lang.reflect.Array;
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
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static java.util.Collections.emptyIterator;
import static java.util.Spliterators.spliterator;

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
        if ( res != null ) return res;
        // automatically provide enum, record and array mappings
        if (type.isEnum()) return (JsonAccessor<T>) byResultType.get( Enum.class );
        if (type.isRecord()) return (JsonAccessor<T>) byResultType.get( Record.class );
        if (type.isArray())  return (JsonAccessor<T>) byResultType.get( Object[].class );
        // automatically provide JsonValue subtype mapping (as forward)
        if ( JsonValue.class.isAssignableFrom( type ) )
            return (JsonAccessor<T>) byResultType.get( JsonValue.class );
        throw new JsonAccessException("No accessor registered for type: " + type);
    }

    public <T> JsonAccess add( Class<T> as, JsonAccessor<T> accessor ) {
        byResultType.put( as, accessor );
        return this;
    }

    public <T> JsonAccess add( Class<T> as, SimpleJsonAccessor<T> accessor ) {
        return add( as, (JsonAccessor<T>) accessor );
    }

    public <T> JsonAccess addKey(Class<T> as, Function<String, T> parse) {
        return add(as, value -> accessAsString( value, parse ));
    }

    public JsonAccess init() {
        return add( String.class, JsonAccess::accessAsString )
            .add( boolean.class, JsonAccess::accessAsPrimitiveBoolean )
            .add( char.class, JsonAccess::accessAsPrimitiveCharacter )
            .add( int.class, value -> accessAsPrimitiveNumber(value, Number::intValue) )
            .add( long.class, value -> accessAsPrimitiveNumber(value, Number::longValue) )
            .add( float.class, value -> accessAsPrimitiveNumber(value, Number::floatValue) )
            .add( double.class, value -> accessAsPrimitiveNumber(value, Number::doubleValue) )
            .add( Boolean.class, JsonAccess::accessAsBoolean )
            .add( Character.class, JsonAccess::accessAsCharacter )
            .add( Integer.class, value -> accessAsNumber(value, Number::intValue ) )
            .add( Long.class, value -> accessAsNumber(value, Number::longValue ) )
            .add( Float.class, value -> accessAsNumber(value, Number::floatValue ) )
            .add( Double.class, value -> accessAsNumber(value, Number::doubleValue ) )
            .add( Number.class, value -> accessAsNumber(value, Function.identity() ) )
            .add( URL.class, value -> value.as(JsonURL.class ).url() )
            .add( UUID.class, value -> value.parsed( UUID::fromString ) )
            .add( LocalDateTime.class, value -> value.as(JsonDate.class ).date() )
            .add( LocalDate.class, value -> value.as(JsonDate.class ).dateOnly() )
            .add( LocalTime.class, value -> value.as(JsonDate.class ).timeOnly() )
            .add( Date.class, JsonAccess::accessAsDate )

            // JDK generic type
            .add( List.class, JsonAccess::accessAsList )
            .add( Iterable.class, JsonAccess::accessAsList )
            .add( Set.class, JsonAccess::accessAsSet )
            .add( Map.class, JsonAccess::accessAsMap )
            .add( Stream.class, JsonAccess::accessAsStream )
            .add( Iterator.class, JsonAccess::accessAsIterator )
            .add( Optional.class, JsonAccess::accessAsOptional )

            // type-families
            .add(Enum.class, JsonAccess::accessAsEnum )
            .add(Object[].class, JsonAccess::accessAsArray )
            .add(Record.class, JsonAccess::accessAsRecord )

            // JSON forwards
            .add( JsonList.class,
                ( value, as, accessors ) -> value.asList( extractJsonValueTypeParameter( as, 0 ) ) )
            .add( JsonMap.class,
                ( value, as, accessors ) -> value.asMap( extractJsonValueTypeParameter( as, 0 ) ) )
            .add( JsonMultiMap.class,
                ( value, as, accessors ) -> value.asMultiMap( extractJsonValueTypeParameter( as, 0 ) ) )
            .add( JsonValue.class,
                ( value, as, accessors ) -> value.as( getRawType( as, JsonValue.class ) ) );
    }

    public static String accessAsString(JsonMixed str) {
        if (str.isUndefined()) return null;
        if (str.isString()) return str.string();
        if (str.isNumber() || str.isBoolean()) return str.toJson();
        throw new JsonAccessException( "JSON does not map to a Java String: "+str );
    }

    public static <T> T accessAsString(JsonMixed str, Function<String, T> as) {
        String val = accessAsString( str );
        if (val == null) return null;
        return as.apply( val );
    }

    public static Enum<?> accessAsEnum(JsonMixed str, Type as, JsonAccessors accessors) {
        if (str.isUndefined()) return null;
        String name = str.string();
        @SuppressWarnings( "rawtypes" )
        Class<? extends Enum> enumType = getRawType( as, Enum.class );
        try {
            return toEnumConstant( enumType, name );
        } catch ( IllegalArgumentException ex ) {
            // try most adjusted to Java naming conventions:
            // upper case and dash to underscore, trimmed
            try {
                return toEnumConstant( enumType, name.toUpperCase().replace( '-', '_' ).trim() );
            } catch ( IllegalArgumentException ex2 ) {
                throw new JsonAccessException(
                    "JSON does not map to Java enum %s: %s%n\tValid values are: %s"
                        .formatted(
                            enumType.getSimpleName(),
                            name,
                            Stream.of(enumType.getEnumConstants())
                                .map(Enum::name)
                                .collect(Collectors.joining(","))));
            }
        }
    }

    public static Boolean accessAsPrimitiveBoolean(JsonMixed bool) {
        Boolean val = accessAsBoolean( bool );
        if (val == null) throw new JsonAccessException( "JSON is undefined, Java is a primitive boolean" );
        return val;
    }

    public static Boolean accessAsBoolean(JsonMixed bool) {
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

    public static <T extends Number> T accessAsPrimitiveNumber(JsonMixed number, Function<Number, T> as) {
        T val = accessAsNumber( number, as );
        if (val == null) throw new JsonAccessException( "JSON is undefined, Java is a primitive number" );
        return val;
    }

    public static <T extends Number> T accessAsNumber(JsonMixed number, Function<Number, T> as) {
        if (number.isUndefined()) return null;
        if (number.isNumber()) return as.apply( number.number() );
        if (number.isString()) return as.apply( Double.parseDouble( number.as( JsonString.class).string() ));
        if (number.isBoolean()) return as.apply( number.as( JsonBoolean.class ).booleanValue() ? 1 : 0 );
        throw new JsonAccessException( "JSON does not map to a Java Number: "+number );
    }

    public static Character accessAsPrimitiveCharacter( JsonMixed str ) {
        Character val = accessAsCharacter( str );
        if (val == null) throw new JsonAccessException( "JSON is undefined, Java is a primitive char" );
        return val;
    }

    public static Character accessAsCharacter( JsonMixed str ) {
        if (str.isUndefined()) return null;
        if (str.isString()) {
            String val = str.string();
            return val.isEmpty() ? null : val.charAt( 0 );
        }
        if (str.isNumber()) return str.toJson().charAt(0);
        throw new JsonAccessException( "JSON does not map to a Java Character: "+str );
    }

    public static Date accessAsDate(JsonMixed date) {
        if (date.isUndefined()) return null;
        if (date.isNumber()) return new Date(date.as(JsonNumber.class).longValue());
        if (date.isString())
            return Date.from(LocalDateTime.parse(date.as(JsonString.class).string())
                .toInstant(ZoneOffset.UTC));
        throw new JsonAccessException( "JSON does not map to a Java Date: "+date );
    }

    public static Optional<?> accessAsOptional(JsonMixed value, Type as, JsonAccessors accessors) {
        if ( value.isUndefined() )  return Optional.empty();
        Type valueType = extractTypeParameter( as, 0 );
        JsonAccessor<?> valueAccess = accessors.accessor( getRawType( valueType ) );
        return Optional.ofNullable( valueAccess.access( value, valueType, accessors ) );
    }

    @SuppressWarnings( { "java:S1168", "java:S1452" } )
    public static List<?> accessAsList( JsonMixed list, Type as, JsonAccessors accessors ) {
        if (list.isUndefined()) return null;
        if (list.isArray() && list.isEmpty()) return List.of();
        int size = list.isArray() ? list.size() : 1;
        List<Object> res = new ArrayList<>( size );
        accessAsIterator( list, as, accessors ).forEachRemaining( res::add );
        return res;
    }

    @SuppressWarnings( "java:S1452" )
    public static Set<?> accessAsSet( JsonMixed set, Type as, JsonAccessors accessors ) {
        if (set.isUndefined()) return null;
        if (set.isArray() && set.isEmpty()) return Set.of();
        Class<?> eRawType = getRawType( extractTypeParameter( as, 0 ) );
        @SuppressWarnings({"unchecked", "rawtypes"})
        Set<Object> res =
            eRawType.isEnum() ? (Set) EnumSet.noneOf((Class<Enum>) eRawType) : new LinkedHashSet<>();
        accessAsIterator( set, as, accessors ).forEachRemaining( res::add );
        return res;
    }

    public static Object[] accessAsArray(JsonMixed array, Type as, JsonAccessors accessors ) {
        if (array.isUndefined()) return null;
        Class<?> type = getRawType( as );
        if (array.isArray() && array.isEmpty())
            return (Object[]) Array.newInstance( type.getComponentType(), 0 );
        int size = array.isArray() ? array.size() : 1;
        Object[] res = (Object[]) Array.newInstance( type.getComponentType(), size );
        int i = 0;
        for ( Iterator<?> it = accessAsIterator( array, as, accessors ); it.hasNext(); )
            res[i++] = it.next();
        return res;
    }

    @SuppressWarnings( { "java:S1168", "java:S1452" } )
    public static Map<?, ?> accessAsMap( JsonMixed map, Type as, JsonAccessors accessors ) {
        if (map.isUndefined()) return null;
        if (map.isEmpty()) return Map.of();
        Type valueType = extractTypeParameter( as, 1 );
        JsonAccessor<?> valueAccess = accessors.accessor( getRawType( valueType ) );
        Class<?> rawKeyType = getRawType( extractTypeParameter( as, 0 ) );
        try {
            JsonAccessor<?> keyAccess = accessors.accessor( rawKeyType );
            //TODO use
        } catch ( JsonAccessException ex ) {
            // default to build-in support types
        }
        Function<String, ?> toKey = getKeyMapper( rawKeyType );
        @SuppressWarnings({"rawtypes", "unchecked"})
        Map<Object, Object> res = rawKeyType.isEnum() ? new EnumMap(rawKeyType) : new LinkedHashMap<>();
        map.entries().forEach( e ->
            res.put(
                toKey.apply( e.getKey() ),
                valueAccess.access( e.getValue().as( JsonMixed.class ), valueType, accessors ) ));
        return res;
    }

    public static Stream<?> accessAsStream( JsonMixed stream, Type as, JsonAccessors accessors ) {
        if ( stream.isUndefined() || stream.isArray() && stream.isEmpty() ) return Stream.empty();
        if (stream.isObject()) throw new JsonAccessException( "JSON does not map to Java Stream: "+stream );
        int size = stream.isArray() ? stream.size() : 1;
        Iterator<?> iter = accessAsIterator( stream, as, accessors );
        return StreamSupport.stream( spliterator( iter, size, Spliterator.ORDERED ), false );
    }

    public static Iterator<?> accessAsIterator( JsonMixed seq, Type as, JsonAccessors accessors ) {
        if ( seq.isUndefined() || seq.isArray() && seq.isEmpty() ) return emptyIterator();
        if (seq.isObject()) throw new JsonAccessException( "JSON does not map to Java Iterator: "+seq );
        Class<?> seqType = getRawType( as );
        Type elementType = seqType.isArray() ? seqType.getComponentType() : extractTypeParameter( as, 0 );
        JsonAccessor<?> elements = accessors.accessor( getRawType( elementType ) );
        // auto-box simple values in a 1 element sequence
        if (!seq.isArray()) return List.of(elements.access( seq, as, accessors )).iterator();
        return seq.stream().map( e -> elements.access( e.as( JsonMixed.class ), elementType, accessors ) ).iterator();
    }

    public static Record accessAsRecord( JsonMixed value, Type as, JsonAccessors accessors ) {
        if (value.isUndefined()) return null;
        if (value.isString()) {
            // check if a dedicated accessor was registered => use it

            // or look for a constructor accepting only 1 String
        }
        Class<? extends Record> type = getRawType( as, Record.class );
        //TODO support mapping from array by index
        if (!value.isObject())
            throw new JsonAccessException(
                "JSON does not map to Java record %s, object expected but got: %s"
                    .formatted(type.getSimpleName(), value));

        return null;
    }

    /**
     * Map keys are really object member names and as such not values so accessors cannot be used to map them.
     * Therefore, a handful of type are supported explicitly.
     */
    private static Function<String, ?> getKeyMapper( Class<?> rawKeyType ) {
        if (rawKeyType.isEnum()) return key -> toEnumConstant(rawKeyType, key);
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
    private static Enum<?> toEnumConstant( Class type, String str ) {
        return Enum.valueOf( type, str );
    }
}
