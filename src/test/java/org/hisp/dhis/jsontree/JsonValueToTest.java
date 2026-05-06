package org.hisp.dhis.jsontree;

import static java.util.Map.entry;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.junit.jupiter.api.Test;

/**
 * Tests an advanced scenario of the {@link JsonValue#to(Class)} method where the {@link
 * JsonObject#properties(Class)} are used to create a JSON object from web REST API URL parameters
 * in form of a {@link Map} of {@link String}s which then is mapped to the target record using the
 * to-method.
 *
 * @author Jan Bernitt
 */
class JsonValueToTest {

  public record OuterParams(
      int number,
      Integer optionalNumber,
      String string,
      Set<JsonNodeType> types,
      List<ExampleItem> items,
      ExampleItem fallback,
      @Collapsed InnerParams inner
  ) {}

  public record InnerParams(
      String foo,
      @Collapsed InnerInnerParams inner
  ) {}

  public record InnerInnerParams(String bar) {}

  @Validation(type = NodeType.STRING)
  public record ExampleItem(String key, double value) {
    public static ExampleItem of(String value) {
      return new ExampleItem(
          value.substring(0, value.indexOf(':')),
          Double.parseDouble(value.substring(value.indexOf(':') + 1)));
    }
  }

  @Test
  void testMinimal() {
    InnerParams inner = new InnerParams(null, new InnerInnerParams(null));
    assertParamsEquals(
        new OuterParams(10, null, null, null, null, null, inner),
        Map.of("number", List.of("10")));
  }

  @Test
  void testEmpty() {
    InnerParams inner = new InnerParams(null, new InnerInnerParams(null));
    assertParamsEquals(
        new OuterParams(10, null, null, Set.of(), List.of(), null, inner),
        Map.ofEntries(
            entry("number", List.of("10")),
            entry("optionalNumber", List.of()),
            entry("string", List.of()),
            entry("types", List.of()),
            entry("items", List.of()),
            entry("fallback", List.of())));
  }

  @Test
  void testMaximal() {
    assertParamsEquals(
        new OuterParams(
            10,
            20,
            "hello",
            Set.of(JsonNodeType.STRING, JsonNodeType.NULL),
            List.of(new ExampleItem("a", 1.5), new ExampleItem("b", 2.0)),
            new ExampleItem("c", 5),
            new InnerParams("foo", new InnerInnerParams("bar"))),
        Map.ofEntries(
            entry("number", List.of("10")),
            entry("optionalNumber", List.of("20")),
            entry("string", List.of("hello")),
            entry("types", List.of("STRING", "NULL")),
            entry("items", List.of("a:1.5", "b:2.0")),
            entry("fallback", List.of("c:5")),
            entry("foo", List.of("foo")),
            entry("bar", List.of("bar"))));
  }

  @Test
  void testSingle() {
    assertParamsEquals(
        new OuterParams(
            10,
            20,
            "hello",
            Set.of(JsonNodeType.STRING),
            List.of(new ExampleItem("a", 1.5)),
            new ExampleItem("c", 5),
            new InnerParams("foo2", new InnerInnerParams(null))),
        Map.ofEntries(
            entry("number", List.of("10")),
            entry("optionalNumber", List.of("20")),
            entry("string", List.of("hello")),
            entry("types", List.of("STRING")),
            entry("items", List.of("a:1.5")),
            entry("fallback", List.of("c:5")),
            entry("foo", List.of("foo2"))));
  }

  private static void assertParamsEquals(OuterParams expected, Map<String, List<String>> actual) {
    List<JsonObject.Property> properties = JsonObject.collapsedProperties(OuterParams.class);
    JsonNode object =
        JsonBuilder.createObject(
            obj -> {
              for (JsonObject.Property p : properties) {
                Text name = p.jsonName();
                String key = name.toString();
                if (!actual.containsKey(key)) continue;
                List<String> values = actual.get(key);
                Set<NodeType> types = p.types();
                if (values == null || values.isEmpty()) {
                  if (types.contains(NodeType.BOOLEAN)) {
                    obj.addBoolean(name, true);
                  } else if (types.contains(NodeType.ARRAY)) {
                    obj.addArray(name, arr -> {});
                  }
                } else {
                  NodeType type = types.iterator().next();
                  if (types.contains(NodeType.ARRAY)) type = NodeType.ARRAY;
                  if (types.size() == 1) {
                    switch (type) {
                      case INTEGER, NUMBER, BOOLEAN, NULL ->
                          obj.addMember(name, JsonNode.of(values.get(0)));
                      case STRING, OBJECT ->
                          obj.addString(
                              name, values.size() == 1 ? values.get(0) : String.join(",", values));
                      case ARRAY ->
                          obj.addArray(
                              name,
                              arr ->
                                  arr.addElements(values, JsonBuilder.JsonArrayBuilder::addString));
                    }
                  }
                }
              }
            });
    JsonMixed params = JsonMixed.of(object);
    params.validate(OuterParams.class);
    assertEquals(expected, params.to(OuterParams.class));
  }
}
