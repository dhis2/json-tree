package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JsonContainerSubtypeTest {

  // Custom sub-type used as the *element*. This is the only "extension" the
  // tests introduce - the collections themselves are stock JsonList/JsonMap/
  // JsonMultiMap. The point is to catch the erasure/covariant-return hole
  // without depending on the extension-of-JsonList support question.
  interface JsonFoo extends JsonObject {
    default String name()  { return getString("name").string(); }
    default int    value() { return getNumber("value").intValue(); }
  }

  // Root as an extension interface -> accessor calls go through the proxy
  // handler on the extension branch. If default-method routing regresses,
  // these tests fail at the first root.items() call, not deep inside a stream.
  interface Root extends JsonObject {
    default JsonList<JsonFoo>            items()    { return getList("items", JsonFoo.class); }
    default JsonMap<JsonFoo>     map()      { return getMap("map", JsonFoo.class); }
    default JsonMultiMap<JsonFoo> multimap() { return getMultiMap("multimap", JsonFoo.class); }
  }

  private static final String JSON = """
      {
        "items": [
          { "name": "a", "value": 1 },
          { "name": "b", "value": 2 }
        ],
        "map": {
          "first":  { "name": "a", "value": 1 },
          "second": { "name": "b", "value": 2 }
        },
        "multimap": {
          "first": [
            { "name": "a", "value": 1 },
            { "name": "b", "value": 2 }
          ]
        }
      }
      """;

  private static Root root() {
    return JsonMixed.of(JSON).as(Root.class);
  }

  @Nested
  class ListTests {

    @Test
    void get_returns_typed_element() {
      JsonFoo first = root().items().get(0);
      assertEquals("a", first.name());   // CCE on a wrong-typed element
      assertEquals(1,   first.value());
    }

    @Test
    void get_returns_typed_element_from_abstract() {
      JsonAbstractArray<?> arr = root().items().as(JsonAbstractArray.class);
      JsonFoo first = arr.get(0).as(JsonFoo.class);
      assertEquals("a", first.name());   // CCE on a wrong-typed element
      assertEquals(1,   first.value());
    }

    @Test
    void iterator_returns_typed_elements() {
      List<String> names = new ArrayList<>();
      for (JsonFoo foo : root().items()) names.add(foo.name());
      assertEquals(List.of("a", "b"), names);
    }

    @Test
    void forEach_returns_typed_elements() {
      List<String> names = new ArrayList<>();
      root().items().forEach(foo -> names.add(foo.name()));
      assertEquals(List.of("a", "b"), names);
    }

    @Test
    void stream_map_does_not_cast_fail() {
      // the method reference forces an (JsonFoo) cast on every element
      assertEquals(List.of("a", "b"),
          root().items().stream().map(JsonFoo::name).toList());
    }

    @Test
    void stream_toList_does_not_cast_fail() {
      List<JsonFoo> foos = root().items().stream().toList();
      foos.forEach(f -> assertInstanceOf(JsonFoo.class, f));
    }

    @Test
    void stream_findFirst_does_not_cast_fail() {
      JsonFoo first = root().items().stream().findFirst().orElseThrow();
      assertEquals("a", first.name());
    }

    interface JsonFooList extends JsonList<JsonFoo> {} // no get override

    @Test
    void extensionList_without_typed_get_fails_fast_at_get() {
      JsonFooList list = root().items().as(JsonFooList.class);
      UnsupportedOperationException ex =
          assertThrows(UnsupportedOperationException.class, () -> list.get(0).name());
      assertEquals("JsonAbstractArray sub-types must implement public abstract E org.hisp.dhis.jsontree.JsonAbstractArray.get(int)", ex.getMessage());
    }

    @Test
    void extensionList_without_typed_get_fails_fast_at_stream() {
      JsonFooList list = root().items().as(JsonFooList.class);
      UnsupportedOperationException ex = assertThrows(UnsupportedOperationException.class, () -> list.stream().map(JsonFoo::name).toList());
      assertEquals("JsonAbstractArray sub-types must implement public abstract java.util.stream.Stream<E> org.hisp.dhis.jsontree.JsonAbstractArray.stream()", ex.getMessage());
    }

    @Test
    void extensionList_with_typed_get_is_supported() {
      interface TypedFooList extends JsonList<JsonFoo> {
        @Override default JsonFoo get(int i) { return asArray().get(i, JsonFoo.class); }

        @Override
        default Stream<JsonFoo> stream() {
          return asArray().stream().map(e -> e.as(JsonFoo.class));
        }
      }
      TypedFooList list = root().items().as(TypedFooList.class);
      assertEquals("a", list.get(0).name());
      assertEquals(List.of("a", "b"), list.stream().map(JsonFoo::name).toList());
      // also test a stream based inherited method
      assertEquals(2, list.count(JsonFoo::value, n -> n > 0));
    }
  }

  @Nested class MapTests {

    @Test
    void get_returns_typed_value() {
      JsonFoo foo = root().map().get("first");
      assertEquals("a", foo.name());
    }

    @Test
    void values_stream_returns_typed_values() {
      assertEquals(List.of("a", "b"),
          root().map().entries().stream().map(JsonFoo::name).sorted().toList());
    }

    @Test
    void entrySet_stream_returns_typed_values() {
      assertEquals(List.of("a", "b"),
          root().map().entries().stream()
              .map(JsonFoo::name)
              .sorted().toList());
    }
  }

  @Nested class MultiMapTests {

    @Test
    void get_returns_typed_list() {
      JsonList<JsonFoo> list = root().multimap().get("first");
      JsonFoo first = list.get(0);
      assertEquals("a", first.name());
    }

    @Test
    void get_iterator_returns_typed_elements() {
      List<String> names = new ArrayList<>();
      for (JsonFoo foo : root().multimap().get("first")) names.add(foo.name());
      assertEquals(List.of("a", "b"), names);
    }

    @Test
    void get_stream_returns_typed_elements() {
      assertEquals(List.of("a", "b"),
          root().multimap().get("first").stream().map(JsonFoo::name).toList());
    }
  }

}