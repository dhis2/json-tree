package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link JsonBuilder.JsonArrayBuilder#addObjects} and its three overloads.
 *
 * <p>The point of these methods is to avoid a per-element {@link Consumer} allocation when
 * building large arrays of objects — the {@link JsonBuilder.Box} is reused internally. These
 * tests therefore focus on the observable consequence of that reuse: every element must reach
 * the encoder with its own value, in iteration order, with exactly one invocation per element.
 * Any failure of the {@code Box.set}/{@code accept} protocol would show up as a wrong value for
 * one or more elements here.
 *
 * <p>Null handling diverges between the overloads by design: {@code Iterable} and array
 * overloads pass {@code null} elements through to the encoder (which decides what to do);
 * {@code Stream.forEachOrdered} throws {@link NullPointerException} per the Stream contract.
 * Both behaviours are covered.
 */
class JsonArrayBuilderAddObjectsTest {

  record Item(int id, String name) {}

  // ——— Iterable overload ———

  @Test
  void testIterable_Empty() {
    assertEquals("[]", toJson(arr -> arr.addObjects(List.of(), (obj, item) -> {})));
  }

  @Test
  void testIterable_Single() {
    assertEquals(
        "[{\"id\":1,\"name\":\"a\"}]",
        toJson(arr -> arr.addObjects(
            List.of(new Item(1, "a")),
            (obj, item) -> {
              obj.addNumber("id", item.id());
              obj.addString("name", item.name());
            })));
  }

  @Test
  void testIterable_PreservesOrder() {
    List<Item> items = List.of(
        new Item(3, "c"),
        new Item(1, "a"),
        new Item(2, "b"));
    assertEquals(
        "[{\"id\":3,\"name\":\"c\"},{\"id\":1,\"name\":\"a\"},{\"id\":2,\"name\":\"b\"}]",
        toJson(arr -> arr.addObjects(items, (obj, item) -> {
          obj.addNumber("id", item.id());
          obj.addString("name", item.name());
        })));
  }

  @Test
  void testIterable_EncoderInvokedOncePerElementInOrder() {
    AtomicInteger count = new AtomicInteger();
    List<Integer> received = new ArrayList<>();
    toJson(arr -> arr.addObjects(List.of(5, 3, 1, 4, 2), (obj, n) -> {
      count.incrementAndGet();
      received.add(n);
      obj.addNumber("n", n);
    }));
    assertEquals(5, count.get());
    assertEquals(List.of(5, 3, 1, 4, 2), received);
  }

  // ——— Stream overload ———

  @Test
  void testStream_Empty() {
    assertEquals("[]", toJson(arr -> arr.addObjects(Stream.<Item>empty(), (obj, item) -> {})));
  }

  @Test
  void testStream_PreservesOrder() {
    assertEquals(
        "[{\"id\":1,\"name\":\"a\"},{\"id\":2,\"name\":\"b\"},{\"id\":3,\"name\":\"c\"}]",
        toJson(arr -> arr.addObjects(
            Stream.of(new Item(1, "a"), new Item(2, "b"), new Item(3, "c")),
            (obj, item) -> {
              obj.addNumber("id", item.id());
              obj.addString("name", item.name());
            })));
  }

  // ——— Array overload ———

  @Test
  void testArray_Empty() {
    assertEquals("[]", toJson(arr -> arr.addObjects(new Item[0], (obj, item) -> {})));
  }

  @Test
  void testArray_PreservesOrder() {
    Item[] items = {
        new Item(1, "a"),
        new Item(2, "b"),
        new Item(3, "c"),
    };
    assertEquals(
        "[{\"id\":1,\"name\":\"a\"},{\"id\":2,\"name\":\"b\"},{\"id\":3,\"name\":\"c\"}]",
        toJson(arr -> arr.addObjects(items, (obj, item) -> {
          obj.addNumber("id", item.id());
          obj.addString("name", item.name());
        })));
  }

  // ——— Content shape ———

  @Test
  void testAllMemberTypes() {
    record Full(int num, String str, boolean bool) {}
    assertEquals(
        "[{\"num\":42,\"str\":\"hello\",\"bool\":true}]",
        toJson(arr -> arr.addObjects(
            List.of(new Full(42, "hello", true)),
            (obj, f) -> {
              obj.addNumber("num", f.num());
              obj.addString("str", f.str());
              obj.addBoolean("bool", f.bool());
            })));
  }

  @Test
  void testWithNestedObject() {
    record Inner(int value) {}
    record Outer(String name, Inner inner) {}
    assertEquals(
        "[{\"name\":\"a\",\"inner\":{\"value\":1}}]",
        toJson(arr -> arr.addObjects(
            List.of(new Outer("a", new Inner(1))),
            (obj, o) -> {
              obj.addString("name", o.name());
              obj.addObject("inner", inner -> inner.addNumber("value", o.inner().value()));
            })));
  }

  @Test
  void testWithNestedArray() {
    record Outer(String name, List<Integer> tags) {}
    assertEquals(
        "[{\"name\":\"a\",\"tags\":[1,2,3]}]",
        toJson(arr -> arr.addObjects(
            List.of(new Outer("a", List.of(1, 2, 3))),
            (obj, o) -> {
              obj.addString("name", o.name());
              obj.addArray("tags", tags -> o.tags().forEach(tags::addNumber));
            })));
  }

  // ——— Chaining and return value ———

  @Test
  void testReturnsThis() {
    // assertSame failing here throws out of createArray, which fails the test
    toJson(arr -> assertSame(arr, arr.addObjects(List.of(1), (obj, n) -> obj.addNumber("n", n))));
  }

  @Test
  void testMixedWithOtherElements() {
    assertEquals(
        "[0,{\"n\":1},{\"n\":2},99]",
        toJson(arr -> {
          arr.addNumber(0);
          arr.addObjects(List.of(1, 2), (obj, n) -> obj.addNumber("n", n));
          arr.addNumber(99);
        }));
  }

  @Test
  void testMultipleCalls() {
    assertEquals(
        "[{\"n\":1},{\"n\":2},{\"n\":3},{\"n\":4},{\"n\":5}]",
        toJson(arr -> {
          arr.addObjects(List.of(1, 2, 3), (obj, n) -> obj.addNumber("n", n));
          arr.addObjects(List.of(4, 5), (obj, n) -> obj.addNumber("n", n));
        }));
  }

  // ——— Null elements — the overloads diverge by design ———

  @Test
  void testIterable_NullElementPassedToEncoder() {
    List<Item> items = new ArrayList<>();
    items.add(new Item(1, "a"));
    items.add(null);
    items.add(new Item(3, "c"));
    assertEquals(
        "[{\"name\":\"a\"},{\"name\":\"was-null\"},{\"name\":\"c\"}]",
        toJson(arr -> arr.addObjects(items, (obj, item) ->
            obj.addString("name", item == null ? "was-null" : item.name()))));
  }

  @Test
  void testArray_NullElementPassedToEncoder() {
    Item[] items = { new Item(1, "a"), null };
    assertEquals(
        "[{\"name\":\"a\"},{\"name\":\"was-null\"}]",
        toJson(arr -> arr.addObjects(items, (obj, item) ->
            obj.addString("name", item == null ? "was-null" : item.name()))));
  }

  @Test
  void testStream_NullElementThrowsNpe() {
    // Stream.forEachOrdered rejects null elements per the Stream contract
    assertThrows(NullPointerException.class, () ->
        toJson(arr -> arr.addObjects(
            Stream.of(new Item(1, "a"), null),
            (obj, item) -> obj.addString("name", item.name()))));
  }

  // ——— Large count — proves no crash and no per-element allocation failure at scale ———

  @Test
  void testLargeCount() {
    int n = 10_000;
    String json = toJson(arr -> arr.addObjects(
        IntStream.range(0, n).boxed().toList(),
        (obj, i) -> obj.addNumber("i", i)));
    // Every object in the output has exactly one '{' (no nesting), so the brace count is the
    // element count — a cheap proxy for "all n were emitted, in one continuous run"
    long objects = json.chars().filter(c -> c == '{').count();
    assertEquals(n, objects);
    assertTrue(json.startsWith("[{\"i\":0},{\"i\":1},"), json.substring(0, 30));
    assertTrue(json.endsWith("{\"i\":9999}]"));
  }

  // ——— Helpers ———

  private static String toJson(Consumer<JsonBuilder.JsonArrayBuilder> body) {
    return JsonBuilder.createArray(body).getDeclaration().toString();
  }
}