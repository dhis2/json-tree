package org.hisp.dhis.jsontree;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE_USE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static java.util.Comparator.comparing;
import static java.util.function.Predicate.not;

/**
 * Computes the differences between two JSON values.
 *
 * @author Jan Bernitt
 * @since 1.7
 */
public record JsonDiff(JsonValue expected, JsonValue actual, List<Difference> differences) {

  /** Is there more JSON, missing JSON, the JSON is out of order or are the simple values wrong? */
  public enum Type {
    /** There is something missing in the actual JSON that was expected */
    LESS,
    /** There is something additional in the actual JSON that isn't expected */
    MORE,
    /** Array elements or object members are in different order in the actual and expected */
    SORT,
    /** The simple value of the leaf node in the actual JSON is not equal to the value expected */
    NEQ
  }

  public record Difference(Type type, JsonValue inExpected, JsonValue inActual) {

    @Override
    public String toString() {
      return JsonDiff.format(this);
    }
  }

  /**
   * When annotating a property method of a JSON array value the elements in the array do not have
   * to be in the same order as in the "expected" array.
   *
   * <p>When annotating a property method of a JSON object value the members in the object do not
   * have to be in the same order as in the "expected" object.
   */
  @Target({METHOD, TYPE_USE})
  @Retention(RUNTIME)
  public @interface AnyOrder {
    boolean value() default true;
  }

  /**
   * When annotating a property method of a JSON array value that may be extra elements in the
   * actual JSON array. If order is checked these would all need to be at the end, if any order is
   * allowed they can be in-between the expected values.
   *
   * <p>When annotating a property method of a JSON object value there may be extra members in the
   * actual JSON object. If order is checked these would all need to be at the end, if any order is
   * allowed they can be in-between the expected members.
   */
  @Target({METHOD, TYPE_USE})
  @Retention(RUNTIME)
  public @interface AnyAdditional {
    boolean value() default true;
  }

  /**
   * @param anyOrder allow any order of array elements or object members when comparing
   * @param anyAdditional allow any additional array elements or object members in the actual value
   *     when comparing
   */
  public record Strictness(boolean anyOrder, boolean anyAdditional) {}

  public enum Equivalence {
    NUMERIC,
    TEXTUAL
  }

  /**
   * How to compare expected and actual JSON values when making a diff.
   *
   * @param arrays default {@link Strictness} for arrays
   * @param objects default {@link Strictness} for objects
   */
  public record Mode(Strictness arrays, Strictness objects, Equivalence numbers) {
    public static final Mode DEFAULT =
        new Mode(new Strictness(false, false), new Strictness(true, false), Equivalence.NUMERIC);
    public static final Mode STRICT =
        new Mode(new Strictness(false, false), new Strictness(false, false), Equivalence.TEXTUAL);
    public static final Mode LENIENT =
        new Mode(new Strictness(true, true), new Strictness(true, true), Equivalence.NUMERIC);

    public Mode anyOrder() {
      return anyOrder(true);
    }

    public Mode anyOrder(boolean anyOrder) {
      return new Mode(
          new Strictness(anyOrder, arrays.anyAdditional),
          new Strictness(anyOrder, objects.anyAdditional),
          numbers);
    }

    public Mode anyAdditional() {
      return anyAdditional(true);
    }

    public Mode anyAdditional(boolean anyAdditional) {
      return new Mode(
          new Strictness(arrays.anyOrder, anyAdditional),
          new Strictness(objects.anyOrder, anyAdditional),
          numbers);
    }

    public Mode arrays(Strictness arrays) {
      return new Mode(arrays, objects, numbers);
    }

    public Mode objects(Strictness objects) {
      return new Mode(arrays, objects, numbers);
    }

    public Mode numbers(Equivalence numbers) {
      return new Mode(arrays, objects, numbers);
    }
  }

  static JsonDiff of(JsonValue e, JsonValue a, Mode mode) {
    List<Difference> differences = new ArrayList<>();
    if (!e.node().isRoot()) e = e.node().extract().lift(e.getAccessStore());
    if (!a.node().isRoot()) a = a.node().extract().lift(a.getAccessStore()).as(a.asType());
    diff(e, a, mode, differences::add, getRootInfo(a.asType()));
    return new JsonDiff(e, a, List.copyOf(differences));
  }

  public static String format(Difference d) {
    JsonValue a = d.inExpected;
    JsonValue b = d.inActual;
    JsonPath aPath = a.exists() ? a.node().getPath() : b.node().getPath();
    JsonPath bPath = b.exists() ? b.node().getPath() : a.node().getPath();
    String aJson = a.exists() ? a.toJson() : "?";
    String bJson = b.exists() ? b.toJson() : "?";
    String type =
        switch (d.type()) {
          case NEQ -> "!=";
          case MORE -> "++";
          case LESS -> "--";
          case SORT -> ">>";
        };
    if (aPath.equals(bPath)) return "%s $%s: %s <> %s".formatted(type, aPath, aJson, bJson);
    return "%s $%s/$%s: %s <> %s".formatted(type, aPath, bPath, aJson, bJson);
  }

  private static void diff(
      JsonValue e, JsonValue a, Mode mode, Consumer<Difference> add, PropertyInfo p) {
    if (!a.exists()) {
      add.accept(new Difference(Type.LESS, e, a));
      return;
    }
    if (e.type() != a.type()) {
      add.accept(new Difference(Type.NEQ, e, a));
      return;
    }
    switch (e.type()) {
      case BOOLEAN, STRING, NULL -> diffValue(e, a, add);
      case NUMBER -> diffNumber(e.as(JsonNumber.class), a.as(JsonNumber.class), mode, add);
      case ARRAY -> diffArray(e.as(JsonArray.class), a.as(JsonArray.class), mode, add, p);
      case OBJECT -> diffObject(e.asObject(), a.asObject(), mode, add, p);
    }
  }

  private static void diffValue(JsonValue e, JsonValue a, Consumer<Difference> add) {
    if (!e.toJson().equals(a.toJson())) add.accept(new Difference(Type.NEQ, e, a));
  }

  private static void diffNumber(JsonNumber e, JsonNumber a, Mode mode, Consumer<Difference> add) {
    if (mode.numbers == Equivalence.TEXTUAL) {
      diffValue(e, a, add);
    } else if (e.doubleValue() != a.doubleValue()) add.accept(new Difference(Type.NEQ, e, a));
  }

  private static void diffObject(
      JsonObject e, JsonObject a, Mode mode, Consumer<Difference> add, PropertyInfo p) {
    if (!p.anyAdditional(mode.objects.anyAdditional) && e.size() != a.size()) {
      // list all extra members
      a.keys()
          .filter(not(e::has))
          .forEach(key -> add.accept(new Difference(Type.MORE, e.get(key), a.get(key))));
    }
    // handle all members in a
    if (p.anyOrder(mode.objects.anyOrder)) {
      // any order
      e.keys().forEach(key -> diff(e.get(key), a.get(key), mode, add, p.property(key)));
    } else {
      // exact order
      Iterator<String> eKeys = e.keys().iterator();
      Iterator<String> aKeys = a.keys().filter(e::has).iterator();
      while (eKeys.hasNext() && aKeys.hasNext()) {
        String eKey = eKeys.next();
        String aKey = aKeys.next();
        if (eKey.equals(aKey)) {
          diff(e.get(eKey), a.get(aKey), mode, add, p.property(eKey));
        } else {
          add.accept(new Difference(Type.SORT, e.get(eKey), a.get(aKey)));
        }
      }
    }
  }

  private static void diffArray(
      JsonArray e, JsonArray a, Mode mode, Consumer<Difference> add, PropertyInfo p) {
    int eN = e.size();
    int aN = a.size();
    if (eN < aN && !p.anyAdditional(mode.arrays.anyAdditional)) {
      // list all extra elements
      IntStream.range(eN, aN)
          .forEach(i -> add.accept(new Difference(Type.MORE, e.get(i), a.get(i))));
    }
    PropertyInfo elements = p.elements();
    if (p.anyOrder(mode.arrays.anyOrder)) {
      // any order
      BitSet different = new BitSet(eN);
      for (int i = 0; i < eN; i++) {
        int index = i;
        diff(e.get(i), a.get(i), mode, d -> different.set(index), elements);
      }
      if (different.isEmpty()) return;
      // try to find an equal value else-where
      for (int i0 : different.stream().toArray()) { // needs a copy to loop & modify!
        JsonValue elem = e.get(i0);
        int sameIndex =
            different.stream()
                .filter(i -> i != i0 && noDiff(elem, a.get(i), mode, elements))
                .findFirst()
                .orElse(-1);
        if (sameIndex >= 0) {
          different.clear(sameIndex);
        } else {
          JsonValue inActual = a.get(i0);
          Type type = inActual.exists() ? Type.NEQ : Type.LESS;
          add.accept(new Difference(type, elem, inActual));
        }
      }
    } else {
      // exact order
      for (int i = 0; i < eN; i++) diff(e.get(i), a.get(i), mode, add, elements);
    }
  }

  private static boolean noDiff(JsonValue e, JsonValue a, Mode mode, PropertyInfo p) {
    AtomicBoolean diff = new AtomicBoolean(true);
    diff(e, a, mode, d -> diff.set(false), p);
    return diff.get();
  }

  /*
  Type and annotation knowledge being extracted
   */

  /** For each type this captures the information given by annotations. */
  private record PropertyInfo(
      Boolean anyOrder,
      Boolean anyAdditional,
      // the properties of an object
      Map<String, PropertyInfo> properties,
      // the elements of an array or the values of a map object
      PropertyInfo values) {

    static final PropertyInfo NONE = new PropertyInfo((Boolean) null, null, Map.of(), null);

    static PropertyInfo of(
        AnnotatedElement source,
        Boolean anyOrder,
        Boolean anyAdditional,
        Map<String, PropertyInfo> properties,
        PropertyInfo values) {
      AnyOrder order = source.getAnnotation(AnyOrder.class);
      AnyAdditional additional = source.getAnnotation(AnyAdditional.class);
      if (order == null
          && additional == null
          && anyOrder == null
          && anyAdditional == null
          && properties.isEmpty()
          && values == NONE) return NONE;
      if (order != null) anyOrder = order.value();
      if (additional != null) anyAdditional = additional.value();
      return new PropertyInfo(anyOrder, anyAdditional, properties, values);
    }

    PropertyInfo elements() {
      return values == null ? NONE : values; // an array
    }

    PropertyInfo property(String key) {
      if (values != null && values != NONE) return values; // a map
      return properties.getOrDefault(key, NONE); // a object
    }

    boolean anyOrder(boolean defaultValue) {
      return anyOrder != null ? anyOrder : defaultValue;
    }

    boolean anyAdditional(boolean defaultValue) {
      return anyAdditional != null ? anyAdditional : defaultValue;
    }
  }

  private static final Map<Class<? extends JsonObject>, Map<String, PropertyInfo>> INFO =
      new ConcurrentSkipListMap<>(comparing(Class::getName));

  private static PropertyInfo getRootInfo(Class<? extends JsonValue> type) {
    if (JsonObject.class.isAssignableFrom(type)) {
      @SuppressWarnings("unchecked")
      Class<? extends JsonObject> objType = (Class<? extends JsonObject>) type;
      return new PropertyInfo((Boolean) null, null, getProperties(objType), PropertyInfo.NONE);
    }
    return PropertyInfo.NONE;
  }

  private static Map<String, PropertyInfo> getProperties(Class<? extends JsonObject> type) {
    return INFO.computeIfAbsent(type, JsonDiff::findProperties);
  }

  private static Map<String, PropertyInfo> findProperties(Class<? extends JsonObject> type) {
    List<JsonObject.Property> properties = JsonObject.properties(type);
    if (properties.isEmpty()) return Map.of();
    Map<String, PropertyInfo> res = new HashMap<>();
    for (JsonObject.Property p : properties) {
      PropertyInfo info = propertyOf(p.javaType());
      if (info != PropertyInfo.NONE) res.put(p.jsonName(), info);
    }
    return Map.copyOf(res);
  }

  private static PropertyInfo propertyOf(AnnotatedType type) {
    java.lang.reflect.Type t = type.getType();
    if (t instanceof Class<?> raw) {
      if (JsonObject.class.isAssignableFrom(raw)) {
        @SuppressWarnings("unchecked")
        Map<String, PropertyInfo> properties = getProperties((Class<? extends JsonObject>) raw);
        return PropertyInfo.of(type, null, null, properties, PropertyInfo.NONE);
      }
      if (JsonArray.class.isAssignableFrom(raw))
        return PropertyInfo.of(type, null, null, Map.of(), PropertyInfo.NONE);
    } else if (type instanceof AnnotatedParameterizedType pt) {
      Class<?> raw = (Class<?>) ((ParameterizedType) pt.getType()).getRawType();
      AnnotatedType eType = pt.getAnnotatedActualTypeArguments()[0];
      if (JsonList.class.isAssignableFrom(raw))
        return PropertyInfo.of(type, null, null, Map.of(), propertyOf(eType));
      if (List.class.isAssignableFrom(raw))
        return PropertyInfo.of(type, false, null, Map.of(), propertyOf(eType));
      if (Set.class.isAssignableFrom(raw))
        return PropertyInfo.of(type, true, null, Map.of(), propertyOf(eType));
      if (JsonMap.class.isAssignableFrom(raw))
        return PropertyInfo.of(type, true, true, Map.of(), propertyOf(eType));
      if (JsonMultiMap.class.isAssignableFrom(raw))
        return PropertyInfo.of(type, true, true, Map.of(), propertyOf(eType));
      if (Map.class.isAssignableFrom(raw))
        return PropertyInfo.of(
            type, true, true, Map.of(), propertyOf(pt.getAnnotatedActualTypeArguments()[1]));
    }
    return PropertyInfo.NONE;
  }
}
