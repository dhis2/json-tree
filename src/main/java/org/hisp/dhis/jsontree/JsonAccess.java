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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.hisp.dhis.jsontree.JsonNode.Index;

/**
 * Standard implementation of the {@link JsonAccessors}.
 *
 * <p>On top of the {@link JsonAccessor}s that were added it automatically creates and adds an
 * accessor for any {@code enum} and any subtype of {@link JsonValue} when it is resolved via {@link
 * #accessor(Class)}.
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
  private record RecordFactory(
      MethodHandle constructor,
      RecordComponent[] components,
      MethodHandle factory1,
      Type component1) {}

  private static final Map<Class<? extends Record>, RecordFactory> RECORD_FACTORY_BY_TYPE =
      new ConcurrentHashMap<>();

  private final Map<Class<?>, JsonAccessor<?>> byResultType = new ConcurrentHashMap<>();

  @Override
  @SuppressWarnings("unchecked")
  public <T> JsonAccessor<T> accessor(Class<T> type) {
    JsonAccessor<T> res = (JsonAccessor<T>) byResultType.get(type);
    if (res != null) return res;
    // automatically provide enum, record and array mappings
    if (type.isEnum()) return (JsonAccessor<T>) byResultType.get(Enum.class);
    if (type.isRecord()) return (JsonAccessor<T>) byResultType.get(Record.class);
    if (type.isArray()) return (JsonAccessor<T>) byResultType.get(Object[].class);
    // automatically provide JsonValue subtype mapping (as forward)
    if (JsonValue.class.isAssignableFrom(type))
      return (JsonAccessor<T>) byResultType.get(JsonValue.class);
    throw new JsonAccessException("No accessor registered for type: " + type);
  }

  public <T> JsonAccess add(Class<T> as, JsonAccessor<T> accessor) {
    byResultType.put(as, accessor);
    return this;
  }

  public <T> JsonAccess add(Class<T> as, SimpleJsonAccessor<T> accessor) {
    return add(as, (JsonAccessor<T>) accessor);
  }

  public <T, E> JsonAccess add(Class<T> as, SimpleJsonAccessor<E> accessor, Function<E, T> f) {
    return add(as, value -> {
      E val = accessor.access(value);
      return val == null ? null : f.apply(val);});
  }

  public <T> JsonAccess addStringAs(Class<T> as, Function<String, T> parse) {
    return add(as, value -> accessAsString(value, parse));
  }

  public JsonAccess init() {
    return add(String.class, JsonString::string)
        .add(Text.class, JsonString::text)
        .add(boolean.class, JsonBoolean::booleanValue)
        .add(char.class, JsonString::charValue)
        .add(int.class, JsonNumber::intValue)
        .add(long.class, JsonNumber::longValue)
        .add(float.class, JsonNumber::floatValue)
        .add(double.class, JsonNumber::doubleValue)
        .add(Boolean.class, JsonBoolean::bool)
        .add(Character.class, JsonString::character)
        .add(Number.class, JsonNumber::number)
        .add(Integer.class, JsonNumber::integer)
        .add(Long.class, JsonNumber::number, Number::longValue)
        .add(Float.class, JsonNumber::number, Number::floatValue)
        .add(Double.class, JsonNumber::number, Number::doubleValue)
        .add(URL.class, value -> value.as(JsonURL.class).url())
        .add(UUID.class, value -> value.parsed(UUID::fromString))
        .add(LocalDateTime.class, value -> value.as(JsonDate.class).date())
        .add(LocalDate.class, value -> value.as(JsonDate.class).dateOnly())
        .add(LocalTime.class, value -> value.as(JsonDate.class).timeOnly())
        .add(Date.class, JsonAccess::accessAsDate)

        // JDK generic type
        .add(List.class, JsonAccess::accessAsList)
        .add(Iterable.class, JsonAccess::accessAsList)
        .add(Set.class, JsonAccess::accessAsSet)
        .add(Map.class, JsonAccess::accessAsMap)
        .add(Stream.class, JsonAccess::accessAsStream)
        .add(Iterator.class, JsonAccess::accessAsIterator)
        .add(Optional.class, JsonAccess::accessAsOptional)
        .add(IntStream.class, JsonArray::intValues)
        .add(LongStream.class, JsonArray::longValues)
        .add(DoubleStream.class, JsonArray::doubleValues)

        // type-families
        .add(Enum.class, JsonAccess::accessAsEnum)
        .add(Object[].class, JsonAccess::accessAsArray)
        .add(Record.class, JsonAccess::accessAsRecord)

        // JSON forwards
        .add(
            JsonList.class,
            (value, as, accessors) -> value.asList(extractJsonValueTypeParameter(as, 0)))
        .add(
            JsonMap.class,
            (value, as, accessors) -> value.asMap(extractJsonValueTypeParameter(as, 0)))
        .add(
            JsonMultiMap.class,
            (value, as, accessors) -> value.asMultiMap(extractJsonValueTypeParameter(as, 0)))
        .add(JsonValue.class, (value, as, accessors) -> value.as(getRawType(as, JsonValue.class)));
  }

  public static String accessAsString(JsonMixed str) {
    if (str.isUndefined()) return null;
    if (str.isString()) return str.string();
    if (str.isNumber() || str.isBoolean()) return str.toJson();
    throw new JsonAccessException("JSON does not map to a Java String: " + str);
  }

  public static <T> T accessAsString(JsonMixed str, Function<String, T> as) {
    String val = accessAsString(str);
    if (val == null) return null;
    return as.apply(val);
  }

  public static Enum<?> accessAsEnum(JsonMixed str, Type as, JsonAccessors accessors) {
    if (str.isUndefined()) return null;
    String name = str.string();
    @SuppressWarnings("rawtypes")
    Class<? extends Enum> enumType = getRawType(as, Enum.class);
    try {
      return toEnumConstant(enumType, name);
    } catch (IllegalArgumentException ex) {
      // try most adjusted to Java naming conventions:
      // upper case and dash to underscore, trimmed
      try {
        return toEnumConstant(enumType, name.toUpperCase().replace('-', '_').trim());
      } catch (IllegalArgumentException ex2) {
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

  public static Date accessAsDate(JsonMixed date) {
    if (date.isUndefined()) return null;
    if (date.isNumber()) return new Date(date.as(JsonNumber.class).longValue());
    if (date.isString())
      return Date.from(
          LocalDateTime.parse(date.as(JsonString.class).string()).toInstant(ZoneOffset.UTC));
    throw new JsonAccessException("JSON does not map to a Java Date: " + date);
  }

  public static Optional<?> accessAsOptional(JsonMixed value, Type as, JsonAccessors accessors) {
    if (value.isUndefined()) return Optional.empty();
    Type valueType = extractTypeParameter(as, 0);
    JsonAccessor<?> valueAccess = accessors.accessor(getRawType(valueType));
    return Optional.ofNullable(valueAccess.access(value, valueType, accessors));
  }

  @SuppressWarnings({"java:S1168", "java:S1452"})
  public static List<?> accessAsList(JsonMixed list, Type as, JsonAccessors accessors) {
    if (list.isUndefined()) return null;
    if (list.isArray() && list.isEmpty()) return List.of();
    return accessAsStream(list, as, accessors).toList();
  }

  @SuppressWarnings("java:S1452")
  public static Set<?> accessAsSet(JsonMixed set, Type as, JsonAccessors accessors) {
    if (set.isUndefined()) return null;
    if (set.isArray() && set.isEmpty()) return Set.of();
    Class<?> eRawType = getRawType(extractTypeParameter(as, 0));
    @SuppressWarnings({"unchecked", "rawtypes"})
    Set<Object> res =
        eRawType.isEnum() ? (Set) EnumSet.noneOf((Class<Enum>) eRawType) : new LinkedHashSet<>();
    accessAsStream(set, as, accessors).forEach(res::add);
    return res;
  }

  public static Object[] accessAsArray(JsonMixed array, Type as, JsonAccessors accessors) {
    if (array.isUndefined()) return null;
    Class<?> type = getRawType(as);
    return accessAsStream(array, as, accessors)
        .toArray(len -> (Object[]) Array.newInstance(type.getComponentType(), len));
  }

  public static Iterator<?> accessAsIterator(JsonMixed seq, Type as, JsonAccessors accessors) {
    return accessAsStream(seq, as, accessors).iterator();
  }

  public static Stream<?> accessAsStream(JsonMixed stream, Type as, JsonAccessors accessors) {
    if (stream.isUndefined() || stream.isArray() && stream.isEmpty()) return Stream.empty();
    if (stream.isObject())
      throw new JsonAccessException("JSON does not map to Java Stream: " + stream);
    Class<?> seqType = getRawType(as);
    Type elementType = seqType.isArray() ? seqType.getComponentType() : extractTypeParameter(as, 0);
    JsonAccessor<?> elements = accessors.accessor(getRawType(elementType));
    // auto-box simple values in a 1 element sequence
    if (!stream.isArray()) return Stream.of(elements.access(stream, as, accessors));
    return stream.stream(Index.SKIP).map(e -> elements.access(e.as(JsonMixed.class), elementType, accessors));
  }

  @SuppressWarnings({"java:S1168", "java:S1452"})
  public static Map<?, ?> accessAsMap(JsonMixed map, Type as, JsonAccessors accessors) {
    if (map.isUndefined()) return null;
    if (map.isEmpty()) return Map.of();
    Type valueType = extractTypeParameter(as, 1);
    JsonAccessor<?> valueAccess = accessors.accessor(getRawType(valueType));
    Class<?> rawKeyType = getRawType(extractTypeParameter(as, 0));
    JsonAccessor<?> keyAccess = accessors.accessor(rawKeyType);
    Function<Text, ?> toKey =
        name ->
            keyAccess.access(Json.of(name).as(JsonMixed.class), rawKeyType, accessors);
    @SuppressWarnings({"rawtypes", "unchecked"})
    Map<Object, Object> res = rawKeyType.isEnum() ? new EnumMap(rawKeyType) : new LinkedHashMap<>();
    map.entries()
        .forEach(
            e ->
                res.put(
                    toKey.apply(e.getKey()),
                    valueAccess.access(e.getValue().as(JsonMixed.class), valueType, accessors)));
    return res;
  }

  public static Record accessAsRecord(JsonMixed obj, Type as, JsonAccessors accessors) {
    if (obj.isUndefined()) return null;
    Class<? extends Record> type = getRawType(as, Record.class);

    RecordFactory factory =
        RECORD_FACTORY_BY_TYPE.computeIfAbsent(
            type, t -> createRecordFactory(MethodHandles.lookup(), t));
    if (!obj.isObject() && !obj.isArray()) {
      boolean wrapper = factory.components.length == 1;
      MethodHandle c1 = wrapper ? factory.constructor : factory.factory1;
      if (c1 == null)
        throw new JsonAccessException(
            "JSON does not map to Java record %s, object or array expected"
                .formatted(type.getSimpleName()));
      Type type1 = wrapper ? factory.components[0].getGenericType() : factory.component1;
      Object arg = accessors.accessor(getRawType(type1)).access(obj, type1, accessors);
      try {
        return type.cast(c1.invokeWithArguments(arg));
      } catch (Throwable ex) {
        throw new JsonAccessException(
            "JSON does not map to Java record %s, construction from single value failed", ex);
      }
    }

    Object[] args = new Object[factory.components.length];

    if (obj.isObject()) {
      int i = 0;
      for (RecordComponent c : factory.components) {
        args[i++] =
            accessors
                .accessor(c.getType())
                .access(obj.get(c.getName(), JsonMixed.class), c.getGenericType(), accessors);
      }
    } else if (obj.isArray()) {
      int i = 0;
      for (RecordComponent c : factory.components)
        args[i] =
            accessors
                .accessor(c.getType())
                .access(obj.get(i++, JsonMixed.class), c.getGenericType(), accessors);
    }
    try {
      return type.cast(factory.constructor.invokeWithArguments(args));
    } catch (Throwable ex) {
      throw new JsonAccessException("JSON does not map to Java record %s, construction failed", ex);
    }
  }

  public static Class<?> getRawType(Type type) {
    return getRawType(type, Object.class);
  }

  @SuppressWarnings({"unchecked", "unused"})
  public static <T> Class<? extends T> getRawType(Type type, Class<T> base) {
    return (Class<T>) (type instanceof ParameterizedType pt ? pt.getRawType() : type);
  }

  public static Type extractTypeParameter(Type from, int n) {
    return ((ParameterizedType) from).getActualTypeArguments()[n];
  }

  public static Class<? extends JsonValue> extractJsonValueTypeParameter(Type from, int n) {
    return getRawType(extractTypeParameter(from, n), JsonValue.class);
  }

  @SuppressWarnings({"rawtypes", "unchecked"})
  private static Enum<?> toEnumConstant(Class type, String str) {
    return Enum.valueOf(type, str);
  }

  private static RecordFactory createRecordFactory(
      MethodHandles.Lookup lookup, Class<? extends Record> type) {
    RecordComponent[] components = type.getRecordComponents();
    Class<?>[] types = Stream.of(components).map(RecordComponent::getType).toArray(Class<?>[]::new);

    MethodHandle canonical = constructor(lookup, type, types);
    if (canonical == null)
      throw new JsonAccessException(
          "JSON cannot be mapped to Java record %s, canonical constructor is not accessible"
              .formatted(type.getSimpleName()));
    MethodHandle c1 = null;
    Type c1Type = null;
    if (components.length > 1) {
      for (Method m : type.getDeclaredMethods()) {
        if (m.getParameterCount() == 1
            && m.getReturnType() == type
            && Modifier.isStatic(m.getModifiers())) {
          c1 = ofStatic(lookup, type, m.getName(), m.getParameterTypes()[0]);
          if (c1 != null) c1Type = m.getGenericParameterTypes()[0];
        }
      }
    }
    return new RecordFactory(canonical, components, c1, c1Type);
  }

  private static MethodHandle constructor(
      MethodHandles.Lookup lookup, Class<? extends Record> type, Class<?>[] types) {
    try {
      return lookup.findConstructor(type, MethodType.methodType(void.class, types)).asFixedArity();
    } catch (NoSuchMethodException | IllegalAccessException e) {
      return null;
    }
  }

  private static MethodHandle ofStatic(
      MethodHandles.Lookup lookup, Class<? extends Record> type, String name, Class<?> param) {
    try {
      return lookup.findStatic(type, name, MethodType.methodType(type, param));
    } catch (NoSuchMethodException | IllegalAccessException e) {
      return null;
    }
  }
}
