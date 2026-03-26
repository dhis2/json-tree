package org.hisp.dhis.jsontree;

import static java.lang.Double.NaN;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link JsonArray} specific API methods.
 *
 * @author Jan Bernitt
 */
class JsonArrayTest {

  @Test
  void testSize() {
    assertEquals(0, JsonMixed.of("[]").size());
    assertEquals(1, JsonMixed.of("[1]").size());
    assertEquals(2, JsonMixed.of("[1,2]").size());
    assertEquals(3, JsonMixed.of("[[],[],[]]").size());
  }

  @Test
  void testStringValues_NoArray() {
    JsonMixed value = JsonMixed.of("1");
    assertThrowsExactly(JsonTreeException.class, value::stringValues);
  }

  @Test
  void testStringValues_NotOnlyStrings() {
    assertEquals(List.of("a", "1", "true"), JsonMixed.of("[\"a\", 1, true]").stringValues());
  }

  @Test
  void testBooleanValues_NoArray() {
    JsonMixed value = JsonMixed.of("1");
    assertThrowsExactly(JsonTreeException.class, value::booleanValues);
  }

  @Test
  void testBooleanValues_NotOnlyBooleans() {
    JsonMixed value = JsonMixed.of("[true, 1, \"a\"]");
    JsonTreeException ex = assertThrowsExactly(JsonTreeException.class, value::booleanValues);
    assertEquals(
        "NUMBER node at path .1 is not a BOOLEAN and does not support #booleanValue(): 1",
        ex.getMessage());
  }

  @Test
  void testForEach_Empty() {
    JsonMixed array = JsonMixed.of("[]");
    array.forEach(e -> fail("should never be called but was with: " + e));
  }

  @Test
  void testForEach_NonEmpty() {
    JsonMixed array = JsonMixed.of("[1,2]");
    List<Object> actual = new ArrayList<>();
    array.forEach(e -> actual.add(e.node().intValue()));
    assertEquals(List.of(1, 2), actual);
  }

  @Test
  void testForEach_NoArray() {
    JsonMixed value = JsonMixed.of("1");
    assertThrowsExactly(JsonTreeException.class, () -> value.forEach(e -> fail()));
  }

  @Test
  void testValues_Mapped() {
    // language=json
    String json =
        """
            ["a","b","c"]""";
    JsonMixed arr = JsonMixed.of(json);
    assertEquals(List.of('a', 'b', 'c'), arr.values(str -> str.charAt(0)));
  }

  @Test
  void testGetList_IndexAs() {
    JsonMixed arr = JsonMixed.of("[[1,2], [3,4]]");
    assertEquals(List.of(1, 2), arr.getList(0, JsonNumber.class).toList(JsonNumber::integer));
  }

  @Test
  void testIntValues() {
    assertArrayEquals(
        IntStream.range(1, 5).toArray(), JsonMixed.of("[1,2,3,4]").intValues().toArray());
  }

  @Test
  void testLongValues() {
    assertArrayEquals(
        LongStream.range(1L, 5L).toArray(), JsonMixed.of("[1,2,3,4]").longValues().toArray());
  }

  @Test
  void testDoubleValues() {
    assertArrayEquals(
        DoubleStream.of(1d, NaN, 3.1d).toArray(),
        Json5.of("[1, NaN, 3.1]").doubleValues().toArray());
  }

  @Test
  void testListValues() {
    assertEquals(List.of(1d, NaN, 3.1d), Json5.of("[1, NaN, 3.1]").listValues(double.class));
  }

  @Test
  void streamValues() {
    assertEquals(
        Stream.of(1, 2, 3).toList(), Json5.of("[1, '2', 3.0]").streamValues(int.class).toList());
  }
}
