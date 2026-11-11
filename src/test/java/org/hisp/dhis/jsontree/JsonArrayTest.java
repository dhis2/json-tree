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
  void testValues_ToClass() {
    assertEquals(List.of(1d, NaN, 3.1d), Json5.of("[1, NaN, 3.1]").values(double.class).toList());
    assertEquals(
        List.of(1, 2, 3), Json5.of("[1, '2', 3.0]").values(int.class).toList());
  }

  /*
   * The intValues(), longValues() and doubleValues() helpers are bulk parsing operations
   * analogous to Integer.parseInt(String), Long.parseLong(String) and Double.parseDouble(String).
   * They are NOT casts analogous to Number.intValue() / Number.longValue() / Number.doubleValue().
   *
   * The tests below pin down the difference:
   *  - non-numeric elements throw NumberFormatException (no implicit conversion)
   *  - non-integer elements throw NumberFormatException for the integer helpers (no truncation)
   *  - integers that overflow the primitive range throw ArithmeticException (no silent wrapping)
   *  - doubles follow Double.parseDouble semantics, i.e. overflow goes to Infinity rather than throwing
   */

  @Test
  void testIntValues_NoArray() {
    JsonMixed value = JsonMixed.of("1");
    assertThrowsExactly(JsonTreeException.class, value::intValues);
  }

  @Test
  void testIntValues_Empty() {
    assertArrayEquals(new int[0], JsonMixed.of("[]").intValues().toArray());
  }

  @Test
  void testIntValues_Null() {
    assertArrayEquals(new int[0], JsonMixed.of("null").intValues().toArray());
  }

  @Test
  void testIntValues_Negative() {
    assertArrayEquals(
        new int[] {-1, -2, -3}, JsonMixed.of("[-1, -2, -3]").intValues().toArray());
  }

  @Test
  void testIntValues_NotANumber() {
    IntStream values = JsonMixed.of("[1, \"a\", 3]").intValues();
    assertThrowsExactly(NumberFormatException.class, values::toArray);
  }

  @Test
  void testIntValues_NotAnInteger() {
    // unlike Number.intValue() this does not truncate 3.14 to 3
    IntStream values = JsonMixed.of("[1, 3.14, 5]").intValues();
    assertThrowsExactly(NumberFormatException.class, values::toArray);
  }

  @Test
  void testIntValues_Overflow() {
    // unlike Number.intValue() this does not wrap 2147483648 to -2147483648
    IntStream values = JsonMixed.of("[2147483648]").intValues();
    ArithmeticException ex = assertThrowsExactly(ArithmeticException.class, values::toArray);
    assertEquals("Number overflows int range: 2147483648", ex.getMessage());
  }

  @Test
  void testIntValues_OverflowNegative() {
    IntStream values = JsonMixed.of("[-2147483649]").intValues();
    assertThrowsExactly(ArithmeticException.class, values::toArray);
  }

  @Test
  void testIntValues_Boundaries() {
    assertArrayEquals(
        new int[] {Integer.MAX_VALUE, Integer.MIN_VALUE},
        JsonMixed.of("[2147483647, -2147483648]").intValues().toArray());
  }

  @Test
  void testLongValues_NoArray() {
    JsonMixed value = JsonMixed.of("1");
    assertThrowsExactly(JsonTreeException.class, value::longValues);
  }

  @Test
  void testLongValues_Empty() {
    assertArrayEquals(new long[0], JsonMixed.of("[]").longValues().toArray());
  }

  @Test
  void testLongValues_Null() {
    assertArrayEquals(new long[0], JsonMixed.of("null").longValues().toArray());
  }

  @Test
  void testLongValues_BeyondIntRange() {
    // just because intValues() would throw doesn't mean longValues() does
    assertArrayEquals(
        new long[] {2147483648L, -2147483649L},
        JsonMixed.of("[2147483648, -2147483649]").longValues().toArray());
  }

  @Test
  void testLongValues_NotANumber() {
    LongStream values = JsonMixed.of("[1, \"a\", 3]").longValues();
    assertThrowsExactly(NumberFormatException.class, values::toArray);
  }

  @Test
  void testLongValues_NotAnInteger() {
    // unlike Number.longValue() this does not truncate 3.14 to 3
    LongStream values = JsonMixed.of("[1, 3.14, 5]").longValues();
    assertThrowsExactly(NumberFormatException.class, values::toArray);
  }

  @Test
  void testLongValues_Overflow() {
    // unlike Number.longValue() this does not wrap to a negative value
    LongStream values = JsonMixed.of("[9223372036854775808]").longValues();
    ArithmeticException ex = assertThrowsExactly(ArithmeticException.class, values::toArray);
    assertEquals("Number overflows long range: 9223372036854775808", ex.getMessage());
  }

  @Test
  void testLongValues_OverflowNegative() {
    LongStream values = JsonMixed.of("[-9223372036854775809]").longValues();
    assertThrowsExactly(ArithmeticException.class, values::toArray);
  }

  @Test
  void testLongValues_Boundaries() {
    assertArrayEquals(
        new long[] {Long.MAX_VALUE, Long.MIN_VALUE},
        JsonMixed.of("[9223372036854775807, -9223372036854775808]").longValues().toArray());
  }

  @Test
  void testDoubleValues_NoArray() {
    JsonMixed value = JsonMixed.of("1");
    assertThrowsExactly(JsonTreeException.class, value::doubleValues);
  }

  @Test
  void testDoubleValues_Empty() {
    assertArrayEquals(new double[0], JsonMixed.of("[]").doubleValues().toArray());
  }

  @Test
  void testDoubleValues_Null() {
    assertArrayEquals(new double[0], JsonMixed.of("null").doubleValues().toArray());
  }

  @Test
  void testDoubleValues_Decimals() {
    assertArrayEquals(
        new double[] {1.5, -2.5, 3.0},
        JsonMixed.of("[1.5, -2.5, 3]").doubleValues().toArray());
  }

  @Test
  void testDoubleValues_NotANumber() {
    DoubleStream values = JsonMixed.of("[1, \"a\", 3]").doubleValues();
    assertThrowsExactly(NumberFormatException.class, values::toArray);
  }

  @Test
  void testDoubleValues_Special() {
    // Double.parseDouble does not throw for NaN / Infinity, so neither do we
    assertArrayEquals(
        new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY},
        // note that we have to give these space left and right to be valid
        Json5.of("[ NaN , Infinity , -Infinity ]").doubleValues().toArray());
  }

  @Test
  void testDoubleValues_OverflowToInfinity() {
    // unlike int and long helpers, doubles do not throw on overflow, they go to Infinity
    assertArrayEquals(
        new double[] {Double.POSITIVE_INFINITY},
        JsonMixed.of("[1e400]").doubleValues().toArray());
  }
}
