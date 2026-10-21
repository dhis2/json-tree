package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Guards the proxy routing for typed extension sub-types of the collection interfaces
 * ({@link JsonList}, {@link JsonMap}, {@link JsonMultiMap}, and their common bases
 * {@link JsonAbstractArray} / {@link JsonAbstractObject}).
 *
 * <h2>The issue these tests probe</h2>
 *
 * A node returned by {@link JsonMixed#as(Class)} is a JDK dynamic proxy over a
 * {@code JsonVirtualTree}. The proxy implements only the requested extension interface;
 * every accessor call goes through an {@link java.lang.reflect.InvocationHandler} that
 * decides whether to invoke the method on the proxy (so the default body runs with
 * {@code this == proxy}) or on the underlying {@code target} (so the concrete
 * implementation on {@code JsonVirtualTree} runs).
 *
 * <p>For the collection base types that decision is load-bearing, because their rich API
 * ({@code stream()}, {@code iterator()}, {@code values()}, {@code entrySet()}, …) is
 * implemented as default methods that ultimately call an abstract, element-typed
 * accessor:
 *
 * <ul>
 *   <li>{@link JsonAbstractArray#get(int)}</li>
 *   <li>{@link JsonAbstractArray#stream()}</li>
 *   <li>{@link JsonAbstractObject#get(Text)}</li>
 *   <li>{@link JsonAbstractObject#entries(JsonNode.Index)}</li>
 * </ul>
 *
 * After erasure the abstract slot of e.g. {@code JsonList<JsonFoo>.get(int)} is
 * {@code get(int)} on core API, and {@code JsonArray.get(int)} returning {@code JsonMixed}
 * is a valid covariant override of it. {@code JsonVirtualTree} therefore
 * <em>accidentally satisfies</em> the abstract method with an untyped implementation.
 * If the handler routes such a call to the target, the default API runs with
 * {@code this == target}, {@code this.get(...)} resolves to the untyped
 * {@code JsonMixed}-returning version, and the compiler-inserted cast to {@code E}
 * fails later.
 *
 * <h2>What a passing test means</h2>
 *
 * <ol>
 *   <li><b>Sub-types that resolve their element type must work end-to-end.</b>
 *       An extension interface that overrides the element-typed accessor with a default
 *       (e.g. {@code default JsonFoo get(Text k) { return get(k, JsonFoo.class); }})
 *       must return correctly-typed values from {@code get(...)}, and from every
 *       inherited default that builds on it ({@code stream()}, {@code values()},
 *       {@code entrySet()}). This requires the handler to invoke the <em>default</em>
 *       methods declared by {@code JsonAbstractArray} / {@code JsonAbstractObject}
 *       on the proxy, not on the target, so the {@code this}-calls inside them route
 *       back through the handler and reach the extension's override.</li>
 *
 *   <li><b>Sub-types that do <em>not</em> resolve their element type must fail fast.</b>
 *       An extension interface that inherits the element-typed accessor abstract
 *       (e.g. {@code interface FooMap extends JsonMap<String, JsonFoo> {}} with no
 *       {@code get(Text)} override) must throw
 *       {@link UnsupportedOperationException} at the offending call — not silently
 *       return a {@code JsonMixed} that later explodes as a
 *       {@link ClassCastException}. This requires the handler to detect "abstract
 *       method declared by {@code JsonAbstractArray} / {@code JsonAbstractObject},
 *       invoked on an extension proxy" and refuse to route it to the target.</li>
 * </ol>
 *
 * <p>The two outcomes together mean the intended contract is: <em>extending a typed
 * collection interface is supported, but only if you supply the element-typed accessor;
 * otherwise you are told so at the call site</em>. Callers that do not want to subclass
 * at all should keep using the view-based path ({@code asList(JsonFoo.class)},
 * {@code asMap(K.class, V.class)}, …), which captures the element type in a concrete
 * object and sidesteps the proxy entirely.
 *
 * <h2>What these tests deliberately do not cover</h2>
 *
 * <ul>
 *   <li>They do not subclass {@code JsonList} / {@code JsonMap} / {@code JsonMultiMap}
 *       to change behaviour; the subclassing is only used to <em>pin an element type</em>
 *       on the collection, which is the exact surface the proxy-routing decision
 *       affects.</li>
 *   <li>They do not assert on the concrete exception type of the failure mode that
 *       the fix removes ({@code ClassCastException}); they assert on the fail-fast
 *       that replaces it. A regression that reintroduces the CCE will therefore
 *       surface as "expected UnsupportedOperationException, got ClassCastException",
 *       which points directly at the routing decision.</li>
 * </ul>
 *
 * @see JsonAbstractArray
 * @see JsonAbstractObject
 * @see JsonList
 * @see JsonMap
 * @see JsonMultiMap
 */
class JsonMapSubtypeTest {

  interface JsonFoo extends JsonObject {
    default String name() { return getString("name").string(); }
  }

  private static final String JSON = """
      {
        "map": {
          "first":  { "name": "a" },
          "second": { "name": "b" }
        },
        "multimap": {
          "first":  [ { "name": "a" }, { "name": "b" } ],
          "second": [ { "name": "c" } ]
        }
      }
      """;

  private static final JsonMixed root = JsonMixed.of(JSON);

  private static Text key(String k) { return Text.of(k); }

  // -----------------------------------------------------------------------
  // JsonMap subtypes
  // -----------------------------------------------------------------------

  interface FooMap extends JsonMap<JsonFoo> {}
  // no get(Text) override -> abstract method inherited from JsonAbstractObject

  interface CompleteFooMap extends JsonMap<JsonFoo> {
    @Override
    default JsonFoo get(Text k) { return asObject().get(k).as(JsonFoo.class); }

    @Override
    default Streamable<JsonFoo> entries(JsonNode.Index index) {
      return asObject().entries(index).map(e -> e.as(JsonFoo.class));
    }
  }

  @Test
  void incompleteMap_get_failsFast() {
    FooMap map = root.get("map").as(FooMap.class);
    UnsupportedOperationException ex =
        assertThrows(UnsupportedOperationException.class, () -> map.get(key("first")));
    assertEquals("JsonAbstractObject sub-types must implement public abstract E org.hisp.dhis.jsontree.JsonAbstractObject.get(org.hisp.dhis.jsontree.Text)", ex.getMessage());
  }

  @Test
  void incompleteMap_entries_failsFast() {
    FooMap map = root.get("map").as(FooMap.class);
    UnsupportedOperationException ex =
        assertThrows(UnsupportedOperationException.class,
            () -> map.entries().map(JsonFoo::name).toList());
    assertEquals("JsonAbstractObject sub-types must implement public abstract org.hisp.dhis.jsontree.Streamable<E> org.hisp.dhis.jsontree.JsonAbstractObject.entries(org.hisp.dhis.jsontree.JsonNode$Index)", ex.getMessage());
  }

  @Test
  void incompleteMap_forEach_failsFast() {
    List<String> names = new ArrayList<>();
    FooMap map = root.get("map").as(FooMap.class);
    UnsupportedOperationException ex =
        assertThrows(UnsupportedOperationException.class,
            () -> map.forEach((k,v) -> names.add(v.name())));
    assertEquals("JsonAbstractObject sub-types must implement public abstract org.hisp.dhis.jsontree.Streamable<E> org.hisp.dhis.jsontree.JsonAbstractObject.entries(org.hisp.dhis.jsontree.JsonNode$Index)", ex.getMessage());
    assertEquals(0, names.size());
  }

  @Test
  void completeMap_get_returns_typedValue() {
    CompleteFooMap map = root.get("map").as(CompleteFooMap.class);
    assertEquals("a", map.get(key("first")).name());
    assertEquals("b", map.get(key("second")).name());
  }

  @Test
  void completeMap_entries_returns_typedValues() {
    CompleteFooMap map = root.get("map").as(CompleteFooMap.class);
    List<String> names = map.entries().map(JsonFoo::name).toList();
    assertEquals(List.of("a", "b"), names);
  }

  @Test
  void completeMap_forEach_returns_typedValues() {
    List<String> names = new ArrayList<>();
    CompleteFooMap map = root.get("map").as(CompleteFooMap.class);
    map.forEach((k,v) -> names.add(v.name()));
    assertEquals(List.of("a", "b"), names);
  }

  // -----------------------------------------------------------------------
  // JsonMultiMap subtypes
  // -----------------------------------------------------------------------

  interface FooMultiMap extends JsonMultiMap<JsonFoo> {}
  // no get(Text) override -> abstract method inherited from JsonAbstractObject

  interface CompleteFooMultiMap extends JsonMultiMap<JsonFoo> {
    @Override
    default JsonList<JsonFoo> get(Text k) {
      return asObject().getList(k, JsonFoo.class);
    }

    @Override
    default Streamable<JsonList<JsonFoo>> entries(JsonNode.Index index) {
      return asObject().entries(index).map(e -> e.asList(JsonFoo.class));
    }
  }

  @Test
  void incompleteMultiMap_get_failsFast() {
    FooMultiMap mm = root.get("multimap").as(FooMultiMap.class);
    UnsupportedOperationException ex =
        assertThrows(UnsupportedOperationException.class, () -> mm.get(key("first")));
    assertEquals("JsonAbstractObject sub-types must implement public abstract E org.hisp.dhis.jsontree.JsonAbstractObject.get(org.hisp.dhis.jsontree.Text)", ex.getMessage());
  }

  @Test
  void incompleteMultiMap_entries_failsFast() {
    FooMultiMap mm = root.get("multimap").as(FooMultiMap.class);
    UnsupportedOperationException ex =
        assertThrows(UnsupportedOperationException.class, () -> mm.entries().toList());
    assertEquals("JsonAbstractObject sub-types must implement public abstract org.hisp.dhis.jsontree.Streamable<E> org.hisp.dhis.jsontree.JsonAbstractObject.entries(org.hisp.dhis.jsontree.JsonNode$Index)", ex.getMessage());
  }

  @Test
  void incompleteMultiMap_forEach_failsFast() {
    List<String> names = new ArrayList<>();
    FooMultiMap mm = root.get("multimap").as(FooMultiMap.class);
    UnsupportedOperationException ex =
        assertThrows(UnsupportedOperationException.class, () -> mm.forEach((k,v) -> v.stream().map(JsonFoo::name).forEach(names::add)));
    assertEquals("JsonAbstractObject sub-types must implement public abstract org.hisp.dhis.jsontree.Streamable<E> org.hisp.dhis.jsontree.JsonAbstractObject.entries(org.hisp.dhis.jsontree.JsonNode$Index)", ex.getMessage());
    assertEquals(0, names.size());
  }

  @Test
  void completeMultiMap_get_returns_typedEntryList() {
    CompleteFooMultiMap mm = root.get("multimap").as(CompleteFooMultiMap.class);
    JsonList<JsonFoo> first = mm.get(key("first"));
    assertEquals("a", first.get(0).name());
    assertEquals("b", first.get(1).name());
  }

  @Test
  void completeMultiMap_get_stream_returns_typedEntryLists() {
    CompleteFooMultiMap mm = root.get("multimap").as(CompleteFooMultiMap.class);
    List<String> names = mm.get(key("first")).stream().map(JsonFoo::name).toList();
    assertEquals(List.of("a", "b"), names);
  }

  @Test
  void completeMultiMap_entries_returns_typedEntryLists() {
    CompleteFooMultiMap mm = root.get("multimap").as(CompleteFooMultiMap.class);
    List<String> names = mm.entries().stream().flatMap(list -> list.stream().map(JsonFoo::name)).toList();
    assertEquals(List.of("a", "b", "c"), names);
  }

  @Test
  void completeMultiMap_forEach_returns_typedEntryLists() {
    List<String> names = new ArrayList<>();
    CompleteFooMultiMap mm = root.get("multimap").as(CompleteFooMultiMap.class);
    mm.forEach((k,v) -> v.stream().map(JsonFoo::name).forEach(names::add));
    assertEquals(List.of("a", "b", "c"), names);
  }
}