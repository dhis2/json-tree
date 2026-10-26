package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.hisp.dhis.jsontree.internal.Language;
import org.junit.jupiter.api.Test;

/**
 * Tests for the three {@code addObjectMembers} overloads on {@link JsonBuilder.JsonObjectBuilder}.
 *
 * <p>These methods exist for the same reason as {@link JsonBuilder.JsonArrayBuilder#addObjects}:
 * they avoid a per-entry allocation when building large map-shaped JSON. The {@link
 * JsonBuilder.Box} is reused internally, so the observable consequences are value correctness and
 * iteration order — a broken {@code Box.set} would surface as wrong values or repeated entries.
 *
 * <h2>Null handling</h2>
 *
 * <p>Unlike the array-element case, the values being passed around are always wrapped in a
 * {@link Map.Entry}. This means two distinct kinds of null are possible, and they behave
 * differently:
 *
 * <ul>
 *   <li><b>A {@code null} value inside a non-null entry</b> is passed through to the encoder
 *       unchanged. All three overloads behave identically here. The encoder decides what to do
 *       (skip, write a default, throw, ...).</li>
 *   <li><b>A {@code null} entry</b> throws {@link NullPointerException} on first use in all
 *       three overloads — {@code Iterable} iteration and {@code Stream.forEachOrdered} both
 *       invoke the loop body with the null entry, and the body then calls {@code e.getValue()}.
 *       Note that {@code Stream} itself does <em>not</em> reject null elements; the NPE comes
 *       from the {@code getValue()} call, same as in the {@code Iterable} case.</li>
 * </ul>
 *
 * <p>Both cases are exercised below so the semantics are pinned down.
 */
class JsonObjectBuilderAddObjectMembersTest {

  record Item(int id, String name) {}

  // ——— Map overload ———

  @Test
  void testMap_Empty() {
    assertEquals("{}", toJson(obj -> obj.addObjectMembers(Map.of(), (nested, item) -> {})));
  }

  @Test
  void testMap_Single() {
    assertEquals(
        "{\"a\":{\"id\":1,\"name\":\"x\"}}",
        toJson(obj -> obj.addObjectMembers(
            Map.of("a", new Item(1, "x")),
            (nested, item) -> {
              nested.addNumber("id", item.id());
              nested.addString("name", item.name());
            })));
  }

  @Test
  void testMap_PreservesOrder() {
    Map<String, Item> items = new LinkedHashMap<>();
    items.put("a", new Item(1, "x"));
    items.put("b", new Item(2, "y"));
    items.put("c", new Item(3, "z"));
    assertEquals(
        "{\"a\":{\"id\":1,\"name\":\"x\"},\"b\":{\"id\":2,\"name\":\"y\"},\"c\":{\"id\":3,\"name\":\"z\"}}",
        toJson(obj -> obj.addObjectMembers(items, (nested, item) -> {
          nested.addNumber("id", item.id());
          nested.addString("name", item.name());
        })));
  }

  @Test
  void testMap_EncoderInvokedOncePerEntry() {
    AtomicInteger count = new AtomicInteger();
    List<Item> received = new ArrayList<>();
    Map<String, Item> items = new LinkedHashMap<>();
    items.put("a", new Item(1, "x"));
    items.put("b", new Item(2, "y"));
    items.put("c", new Item(3, "z"));
    toJson(obj -> obj.addObjectMembers(items, (nested, item) -> {
      count.incrementAndGet();
      received.add(item);
      nested.addNumber("id", item.id());
    }));
    assertEquals(3, count.get());
    assertEquals(List.of(new Item(1, "x"), new Item(2, "y"), new Item(3, "z")), received);
  }

  // ——— Iterable overload ———

  @Test
  void testIterable_Empty() {
    assertEquals("{}", toJson(obj -> obj.addObjectMembers(List.<Map.Entry<String, Item>>of(),
        (nested, item) -> {})));
  }

  @Test
  void testIterable_PreservesOrder() {
    List<Map.Entry<String, Item>> entries = List.of(
        Map.entry("c", new Item(3, "z")),
        Map.entry("a", new Item(1, "x")),
        Map.entry("b", new Item(2, "y")));
    assertEquals(
        "{\"c\":{\"id\":3,\"name\":\"z\"},\"a\":{\"id\":1,\"name\":\"x\"},\"b\":{\"id\":2,\"name\":\"y\"}}",
        toJson(obj -> obj.addObjectMembers(entries, (nested, item) -> {
          nested.addNumber("id", item.id());
          nested.addString("name", item.name());
        })));
  }

  // ——— Stream overload ———

  @Test
  void testStream_Empty() {
    assertEquals("{}", toJson(obj -> obj.addObjectMembers(Stream.<Map.Entry<String, Item>>empty(),
        (nested, item) -> {})));
  }

  @Test
  void testStream_PreservesOrder() {
    Stream<Map.Entry<String, Item>> entries = Stream.of(
        Map.entry("a", new Item(1, "x")),
        Map.entry("b", new Item(2, "y")),
        Map.entry("c", new Item(3, "z")));
    assertEquals(
        "{\"a\":{\"id\":1,\"name\":\"x\"},\"b\":{\"id\":2,\"name\":\"y\"},\"c\":{\"id\":3,\"name\":\"z\"}}",
        toJson(obj -> obj.addObjectMembers(entries, (nested, item) -> {
          nested.addNumber("id", item.id());
          nested.addString("name", item.name());
        })));
  }

  // ——— Content shape ———

  @Test
  void testAllMemberTypes() {
    record Full(int num, String str, boolean bool) {}
    assertEquals(
        "{\"k\":{\"num\":42,\"str\":\"hello\",\"bool\":true}}",
        toJson(obj -> obj.addObjectMembers(
            Map.of("k", new Full(42, "hello", true)),
            (nested, f) -> {
              nested.addNumber("num", f.num());
              nested.addString("str", f.str());
              nested.addBoolean("bool", f.bool());
            })));
  }

  @Test
  void testWithNestedObject() {
    record Outer(String name, Item inner) {}
    assertEquals(
        "{\"k\":{\"name\":\"a\",\"inner\":{\"id\":1}}}",
        toJson(obj -> obj.addObjectMembers(
            Map.of("k", new Outer("a", new Item(1, "x"))),
            (nested, o) -> {
              nested.addString("name", o.name());
              nested.addObject("inner", inner -> inner.addNumber("id", o.inner().id()));
            })));
  }

  @Test
  void testWithNestedArray() {
    record Outer(String name, List<Integer> tags) {}
    assertEquals(
        "{\"k\":{\"name\":\"a\",\"tags\":[1,2,3]}}",
        toJson(obj -> obj.addObjectMembers(
            Map.of("k", new Outer("a", List.of(1, 2, 3))),
            (nested, o) -> {
              nested.addString("name", o.name());
              nested.addArray("tags", tags -> o.tags().forEach(tags::addNumber));
            })));
  }

  // ——— Keys ———

  @Test
  void testKeysAreEscaped() {
    Map<String, Item> items = new LinkedHashMap<>();
    items.put("key with space", new Item(1, "x"));
    items.put("key\"with\"quotes", new Item(2, "y"));
    assertEquals(
        "{\"key with space\":{\"name\":\"x\"},\"key\\\"with\\\"quotes\":{\"name\":\"y\"}}",
        toJson(obj -> obj.addObjectMembers(items, (nested, item) ->
            nested.addString("name", item.name()))));
  }

  // ——— Chaining and return value ———

  @Test
  void testReturnsThis() {
    toJson(obj -> assertSame(obj, obj.addObjectMembers(
        Map.of("k", new Item(1, "x")), (nested, item) -> {})));
  }

  @Test
  void testMixedWithOtherMembers() {
    assertEquals(
        "{\"a\":1,\"x\":{\"id\":1},\"z\":true}",
        toJson(obj -> {
          obj.addNumber("a", 1);
          obj.addObjectMembers(
              Map.of("x", new Item(1, "y")),
              (nested, item) -> nested.addNumber("id", item.id()));
          obj.addBoolean("z", true);
        }));
  }

  @Test
  void testMultipleCalls() {
    assertEquals(
        "{\"a\":{\"id\":1},\"b\":{\"id\":2},\"c\":{\"id\":3}}",
        toJson(obj -> {
          obj.addObjectMembers(
              Map.of("a", new Item(1, "x")),
              (nested, item) -> nested.addNumber("id", item.id()));
          obj.addObjectMembers(
              Map.of("b", new Item(2, "y"), "c", new Item(3, "z")),
              (nested, item) -> nested.addNumber("id", item.id()));
        }));
  }

  // ——— Null values inside non-null entries pass through ———

  @Test
  void testMap_NullValuePassedToEncoder() {
    Map<String, Item> items = new LinkedHashMap<>();
    items.put("a", new Item(1, "x"));
    items.put("b", null);
    items.put("c", new Item(3, "z"));
    assertEquals(
        "{\"a\":{\"name\":\"x\"},\"b\":{\"name\":\"was-null\"},\"c\":{\"name\":\"z\"}}",
        toJson(obj -> obj.addObjectMembers(items, (nested, item) ->
            nested.addString("name", item == null ? "was-null" : item.name()))));
  }

  @Test
  void testIterable_NullValuePassedToEncoder() {
    List<Map.Entry<String, Item>> entries = List.of(
        Map.entry("a", new Item(1, "x")),
        new SimpleEntry<>("b", null),                   // SimpleEntry allows null values
        Map.entry("c", new Item(3, "z")));
    assertEquals(
        "{\"a\":{\"name\":\"x\"},\"b\":{\"name\":\"was-null\"},\"c\":{\"name\":\"z\"}}",
        toJson(obj -> obj.addObjectMembers(entries, (nested, item) ->
            nested.addString("name", item == null ? "was-null" : item.name()))));
  }

  @Test
  void testStream_NullValuePassedToEncoder() {
    Stream<Map.Entry<String, Item>> entries = Stream.<Map.Entry<String, Item>>of(
        Map.entry("a", new Item(1, "x")),
        new SimpleEntry<>("b", null),
        Map.entry("c", new Item(3, "z")));
    assertEquals(
        "{\"a\":{\"name\":\"x\"},\"b\":{\"name\":\"was-null\"},\"c\":{\"name\":\"z\"}}",
        toJson(obj -> obj.addObjectMembers(entries, (nested, item) ->
            nested.addString("name", item == null ? "was-null" : item.name()))));
  }

  // ——— Null entries throw NPE in both Iterable and Stream ———
  // (the NPE comes from e.getValue() in the loop body, not from the source abstraction;
  //  Stream does not reject null elements on its own — forEachOrdered passes them through)

  @Test
  void testIterable_NullEntryThrows() {
    List<Map.Entry<String, Item>> entries = Arrays.asList(
        Map.entry("a", new Item(1, "x")),
        null);
    assertThrows(NullPointerException.class, () ->
        toJson(obj -> obj.addObjectMembers(entries, (nested, item) ->
            nested.addString("name", item.name()))));
  }

  @Test
  void testStream_NullEntryThrows() {
    Stream<Map.Entry<String, Item>> entries = Stream.<Map.Entry<String, Item>>of(
        Map.entry("a", new Item(1, "x")),
        null);
    assertThrows(NullPointerException.class, () ->
        toJson(obj -> obj.addObjectMembers(entries, (nested, item) ->
            nested.addString("name", item.name()))));
  }

  // ——— Large count ———

  @Test
  void testLargeCount() {
    int n = 10_000;
    Map<String, Integer> values = new LinkedHashMap<>();
    for (int i = 0; i < n; i++) values.put("k" + i, i);
    String json = toJson(obj -> obj.addObjectMembers(
        values, (nested, i) -> nested.addNumber("i", i)));
    // every entry contributes exactly one '{' (the value object); no nesting beyond that
    long objects = json.chars().filter(c -> c == '{').count();
    assertEquals(n+1, objects); // + 1 for the root object
    assertTrue(json.startsWith("{\"k0\":{\"i\":0},\"k1\":{\"i\":1},"), json.substring(0, 40));
    assertTrue(json.endsWith("{\"i\":9999}}"));
  }

  // ——— Helpers ———

  private static String toJson(Consumer<JsonBuilder.JsonObjectBuilder> body) {
    return JsonBuilder.createObject(body).getDeclaration().toString();
  }
}