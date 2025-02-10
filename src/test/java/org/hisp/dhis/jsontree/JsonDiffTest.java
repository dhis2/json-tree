package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hisp.dhis.jsontree.JsonDiff.Mode.DEFAULT;
import static org.hisp.dhis.jsontree.JsonDiff.Mode.LENIENT;
import static org.hisp.dhis.jsontree.JsonDiff.Mode.STRICT;
import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonDiffTest {

  @Test
  void testNull() {
    assertNoDiff("null", "null");
    assertDiff("null", "[]", "!= $: null <> []");
    assertDiff("null", "1", "!= $: null <> 1");
  }

  @Test
  void testNumber() {
    assertNoDiff("12", "12");
    assertNoDiff("4", "4");
    assertDiff("7", "4", "!= $: 7 <> 4");
  }

  @Test
  void testNumber_Mode() {
    assertNoDiff("4", "4", STRICT);
    assertDiff("4", "4.0", STRICT, "!= $: 4 <> 4.0");
    assertNoDiff("13.5", "13.500", LENIENT);
  }

  @Test
  void testBoolean() {
    assertNoDiff("true", "true");
    assertNoDiff("false", "false");
    assertDiff("false", "true", "!= $: false <> true");
    assertDiff("true", "false", "!= $: true <> false");
  }

  @Test
  void testString() {
    assertNoDiff(Json.of("hello"), Json.of("hello"));
    assertNoDiff(Json.of("hey, ho"), Json.of("hey, ho"));
    assertDiff(Json.of("hello"), Json.of("world"), "!= $: \"hello\" <> \"world\"");
  }

  @Test
  void testArray() {
    assertNoDiff("[]", "[]");
    assertNoDiff("[ [ ] ]", "[[]]");
    assertNoDiff("[true]", "[true]");
    assertNoDiff("[ true, 42 ]", "[true, 42]");
    assertNoDiff("[ 42, true ]", "[true, 42]", STRICT.anyOrder());
    assertNoDiff("[ \"a\", 42, true ]", "[true, \"a\", 42]", STRICT.anyOrder());
    assertDiff("[1,2,3]", "[1,5,3]", "!= $[1]: 2 <> 5");
    assertNoDiff("[1,2,3]", "[1,3,2]", LENIENT);
    assertNoDiff("[1,2,3]", "[1,3,2,5]", LENIENT);
    assertDiff("[1,2,3]", "[1,3,2,5]", STRICT.anyOrder(), "++ $[3]: ? <> 5");
    assertDiff("[ true, 42, 678 ]", "[true, 42]", "-- $[2]: 678 <> ?");
    assertDiff("[ true, 42 ]", "[true, 42, 678]", "++ $[2]: ? <> 678");
  }

  @Test
  void testObject() {
    assertNoDiff("{}", "{}");
    assertNoDiff("{}", "{  }");
    assertNoDiff("{\"x\": {} }", "{ \"x\":{ }}");
    assertNoDiff("{}", "{ \"x\":{ }}", LENIENT);
    assertNoDiff("{\"a\": 1, \"b\":2}", "{ \"b\": 2, \"c\": 42, \"a\": 1}", LENIENT);
    assertDiff(
        "{ \"b\": 2, \"c\": 42, \"a\": 1}", "{\"a\": 1, \"b\":2}", LENIENT, "-- $.c: 42 <> ?");
    assertDiff("{\"a\":[1,2,3]}", "{\"a\":[1,5,3]}", "!= $.a[1]: 2 <> 5");
  }

  private interface JsonAnyAnnotationObject extends JsonObject {

    @JsonDiff.AnyOrder
    default JsonList<JsonNumber> versions() {
      return getList("versions", JsonNumber.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    default JsonList<@JsonDiff.AnyOrder JsonList<JsonNumber>> numbers() {
      return (JsonList) getList("numbers", JsonList.class);
    }

    @JsonDiff.AnyOrder
    @JsonDiff.AnyAdditional
    default JsonObject sub() {
      return getObject("sub");
    }

    @JsonDiff.AnyAdditional
    default JsonArray indexes() {
      return getArray("indexes");
    }
  }

  @Test
  void testAnyAdditional_Array() {
    assertNoDiff(
        JsonValue.of("{\"sub\":{}}"),
        JsonValue.of("{\"sub\":{\"some\": 1}}").as(JsonAnyAnnotationObject.class));
    assertNoDiff(
        JsonValue.of("{\"sub\":{\"a\": 2, \"c\": 7}}"),
        JsonValue.of("{\"sub\":{\"x\": 1, \"c\": 7, \"a\":2}}").as(JsonAnyAnnotationObject.class));
  }

  @Test
  void testAnyOrder_Object() {
    assertNoDiff(
        JsonValue.of("{\"versions\":[1,2,3]}"),
        JsonValue.of("{\"versions\":[2,3,1]}").as(JsonAnyAnnotationObject.class));
  }

  @Test
  void testAnyOrder_JsonList() {
    assertNoDiff(
        JsonValue.of("{\"versions\":[1,2,3]}"),
        JsonValue.of("{\"versions\":[2,3,1]}").as(JsonAnyAnnotationObject.class));
  }

  @Test
  void testAnyOrder_JsonListList() {
    assertNoDiff(
        JsonValue.of("{\"numbers\":[[1],[1,2],[1,2,3]]}"),
        JsonValue.of("{\"numbers\":[[1], [2,1],[3,1,2]]}").as(JsonAnyAnnotationObject.class));
  }

  @Test
  void testAnyOrder_JsonListList2() {
    assertDiff(
        JsonValue.of("{\"numbers\":[[1],[1,2],[1,2,3]]}"),
        JsonValue.of("{\"numbers\":[[2,1], [1],[3,1,2]]}").as(JsonAnyAnnotationObject.class),
        "++ $.numbers[0][1]: ? <> 1",
        "!= $.numbers[0][0]: 1 <> 2",
        "-- $.numbers[1][1]: 2 <> ?");
  }

  private interface JsonTypeDefaultsObject extends JsonObject {

    default Set<Number> numbers() {
      return Set.copyOf(getArray("numbers").numberValues());
    }

    default Map<String, Number> ages() {
      return getMap("ages", JsonNumber.class).toMap(JsonNumber::number);
    }
  }

  @Test
  void testAnyOrder_Set() {
    assertNoDiff(
        JsonValue.of("{\"numbers\":[1,2,3]}"),
        JsonValue.of("{\"numbers\":[2,3,1]}").as(JsonTypeDefaultsObject.class));
  }

  @Test
  void testAnyOrder_Map() {
    assertNoDiff(
        JsonValue.of("{\"ages\":{\"0-15\":1, \"16-30\": 5}}"),
        JsonValue.of("{\"ages\":{\"16-30\":5, \"0-15\": 1}}").as(JsonTypeDefaultsObject.class));
  }

  @Test
  void testAnyAdditional_Map() {
    assertNoDiff(
        JsonValue.of("{\"ages\":{\"0-15\":1, \"16-30\": 5}}"),
        JsonValue.of("{\"ages\":{\"16-30\":5, \"0-15\": 1, \"31+\": 4}}")
            .as(JsonTypeDefaultsObject.class));
  }

  private static void assertDiff(String expected, String actual, String... differences) {
    assertDiff(expected, actual, DEFAULT, differences);
  }

  private static void assertDiff(
      String expected, String actual, JsonDiff.Mode mode, String... differences) {
    assertDiff(JsonValue.of(expected), JsonValue.of(actual), mode, differences);
  }

  private static void assertDiff(JsonValue expected, JsonValue actual, String... differences) {
    assertDiff(expected, actual, DEFAULT, differences);
  }

  private static void assertDiff(
      JsonValue expected, JsonValue actual, JsonDiff.Mode mode, String... differences) {
    assertEquals(
        List.of(differences),
        expected.diff(actual, mode).differences().stream()
            .map(JsonDiff.Difference::toString)
            .toList());
  }

  private static void assertNoDiff(String expected, String actual) {
    assertNoDiff(JsonValue.of(expected), JsonValue.of(actual));
  }

  private static void assertNoDiff(String expected, String actual, JsonDiff.Mode mode) {
    assertNoDiff(JsonValue.of(expected), JsonValue.of(actual), mode);
  }

  private static void assertNoDiff(JsonValue expected, JsonValue actual) {
    assertNoDiff(expected, actual, STRICT);
  }

  private static void assertNoDiff(JsonValue expected, JsonValue actual, JsonDiff.Mode mode) {
    assertEquals(List.of(), expected.diff(actual, mode).differences());
  }
}
