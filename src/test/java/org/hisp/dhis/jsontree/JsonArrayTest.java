package org.hisp.dhis.jsontree;

import static java.lang.Double.NaN;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
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

  @Test
  void testDoubleValues_HugeExponent() {
    // JSON numbers have no exponent range limit; the array helper must not throw
    // for exponents beyond BigDecimal's int scale
    assertArrayEquals(
        new double[] {Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 0.0},
        JsonMixed.of("[1e999999999999, -1e999999999999, 1e-999999999999]")
            .doubleValues()
            .toArray());
  }

  // ---------------------------------------------------------------------------
  // textValues()
  // ---------------------------------------------------------------------------

  @Test
  void testTextValues_Empty() {
    assertEquals(List.of(), JsonMixed.of("[]").textValues().toList());
  }

  @Test
  void testTextValues_Null() {
    assertEquals(List.of(), JsonMixed.of("null").textValues().toList());
  }

  @Test
  void testTextValues_NotArray() {
    JsonMixed value = JsonMixed.of("1");
    assertThrowsExactly(JsonTreeException.class, value::textValues);
  }

  @Test
  void testTextValues_Mixed() {
    // raw for numbers/booleans, decoded for strings (this is the shape stringValues() relied on)
    List<String> values =
        JsonMixed.of("[\"a\", 1, true, 1.50]").textValues().map(Text::toString).toList();
    assertEquals(List.of("a", "1", "true", "1.50"), values);
  }

  @Test
  void testTextValues_PreservesNumberText() {
    // raw number text is preserved: parseNumber() sees the original digits, not a re-formatted value
    List<Text> values = JsonMixed.of("[1.50, 1e3, -0.0]").textValues().toList();
    assertEquals("1.50", values.get(0).toString());
    assertEquals("1e3",  values.get(1).toString());
    assertEquals("-0.0", values.get(2).toString());
  }

  // ---------------------------------------------------------------------------
  // bigDecimalValues()
  // ---------------------------------------------------------------------------

  @Test
  void testBigDecimalValues_Empty() {
    assertEquals(List.of(), JsonMixed.of("[]").bigDecimalValues().toList());
  }

  @Test
  void testBigDecimalValues_Null() {
    assertEquals(List.of(), JsonMixed.of("null").bigDecimalValues().toList());
  }

  @Test
  void testBigDecimalValues_NotArray() {
    JsonMixed value = JsonMixed.of("1");
    assertThrowsExactly(JsonTreeException.class, value::bigDecimalValues);
  }

  @Test
  void testBigDecimalValues_SimpleDecimals() {
    assertEquals(
        List.of(new BigDecimal("1.5"), new BigDecimal("-2.5"), new BigDecimal("3.14")),
        JsonMixed.of("[1.5, -2.5, 3.14]").bigDecimalValues().toList());
  }

  @Test
  void testBigDecimalValues_PreservesScale() {
    // BigDecimal.equals compares value AND scale; this pins down that parseBigDecimal does not
    // normalise ("1.50" must not become "1.5"). If the impl ever routes through a String round-
    // trip or a normalising parse, this fails.
    assertEquals(
        List.of(new BigDecimal("1.50"), new BigDecimal("1.5")),
        JsonMixed.of("[1.50, 1.5]").bigDecimalValues().toList());
  }

  @Test
  void testBigDecimalValues_BeyondDouble() {
    // only BigDecimal can hold these — the whole point of the accessor
    assertEquals(
        List.of(new BigDecimal("1e400"), new BigDecimal("1e-400")),
        JsonMixed.of("[1e400, 1e-400]").bigDecimalValues().toList());
  }

  @Test
  void testBigDecimalValues_NotANumber() {
    Streamable<BigDecimal> values = JsonMixed.of("[1, \"a\"]").bigDecimalValues();
    assertThrows(NumberFormatException.class, values::toList);
  }

  // ---------------------------------------------------------------------------
  // bigIntegerValues()
  // ---------------------------------------------------------------------------

  @Test
  void testBigIntegerValues_Empty() {
    assertEquals(List.of(), JsonMixed.of("[]").bigIntegerValues().toList());
  }

  @Test
  void testBigIntegerValues_Null() {
    assertEquals(List.of(), JsonMixed.of("null").bigIntegerValues().toList());
  }

  @Test
  void testBigIntegerValues_NotArray() {
    JsonMixed value = JsonMixed.of("1");
    assertThrowsExactly(JsonTreeException.class, value::bigIntegerValues);
  }

  @Test
  void testBigIntegerValues_BeyondLong() {
    // the whole point: values a long cannot hold
    assertEquals(
        List.of(
            new BigInteger("9223372036854775808"),
            new BigInteger("123456789012345678901234567890")),
        JsonMixed.of("[9223372036854775808, 123456789012345678901234567890]")
            .bigIntegerValues()
            .toList());
  }

  @Test
  void testBigIntegerValues_IntegerValuedDecimals() {
    // 1.0, 1e1, 1e3 all denote integers — toBigIntegerExact() handles them
    assertEquals(
        List.of(BigInteger.ONE, BigInteger.TEN, BigInteger.valueOf(1000)),
        JsonMixed.of("[1.0, 1e1, 1e3]").bigIntegerValues().toList());
  }

  @Test
  void testBigIntegerValues_Fractional_Throws() {
    // STRICT: not a truncation. If this ever starts returning [1, 1] the accessor has
    // drifted to Number.intValue()-style semantics, which was the whole point of avoiding.
    Streamable<BigInteger> values = JsonMixed.of("[1, 1.5]").bigIntegerValues();
    assertThrows(ArithmeticException.class, values::toList);
  }

  @Test
  void testBigIntegerValues_NotANumber() {
    Streamable<BigInteger> values = JsonMixed.of("[1, \"a\"]").bigIntegerValues();
    assertThrows(NumberFormatException.class, values::toList);
  }

  // ---------------------------------------------------------------------------
  // values(Class<T>) with BigInteger / BigDecimal
  //
  // These assume to(BigInteger.class) and to(BigDecimal.class) are wired through
  // to the big-number accessors the same way int.class / double.class are wired
  // through intValue() / doubleValue(). If any of these fail, the fix is in
  // JsonValue.to(Class<T>) (or a special case in values(Class<T>) itself).
  //
  // Design decisions these tests encode:
  //  - BigInteger: STRICT parse (ArithmeticException on non-zero fraction), not
  //    a Number.intValue()-style truncating cast. Consistent with bigInteger()
  //    and bigIntegerValues().
  //  - BigDecimal: exact, scale-preserving ("1.50" stays scale-2, not "1.5"),
  //    and not bounded by double range (1e400 is representable).
  //  - Neither goes through double as an intermediate step.
  // ---------------------------------------------------------------------------

  @Test
  void testValues_ToBigInteger() {
    assertEquals(
        List.of(BigInteger.ONE, BigInteger.valueOf(2), BigInteger.valueOf(3)),
        JsonMixed.of("[1, 2, 3]").values(BigInteger.class).toList());
  }

  @Test
  void testValues_ToBigInteger_Negative() {
    assertEquals(
        List.of(BigInteger.valueOf(-1), BigInteger.valueOf(-2)),
        JsonMixed.of("[-1, -2]").values(BigInteger.class).toList());
  }

  @Test
  void testValues_ToBigInteger_BeyondLong() {
    assertEquals(
        List.of(
            new BigInteger("9223372036854775808"),
            new BigInteger("123456789012345678901234567890")),
        JsonMixed.of("[9223372036854775808, 123456789012345678901234567890]")
            .values(BigInteger.class)
            .toList());
  }

  @Test
  void testValues_ToBigInteger_IntegerValuedDecimals() {
    // 1.0, 1e1, 1e3 all denote integer values; toBigIntegerExact handles them
    assertEquals(
        List.of(BigInteger.ONE, BigInteger.TEN, BigInteger.valueOf(1000)),
        JsonMixed.of("[1.0, 1e1, 1e3]").values(BigInteger.class).toList());
  }

  @Test
  void testValues_ToBigInteger_FractionalThrows() {
    Streamable<BigInteger> values = JsonMixed.of("[1, 1.5]").values(BigInteger.class);
    JsonAccessException ex = assertThrows(JsonAccessException.class, values::toList);
    assertEquals(ArithmeticException.class, ex.getCause().getClass());
  }

  @Test
  void testValues_ToBigInteger_NotANumberThrows() {
    Streamable<BigInteger> values = JsonMixed.of("[1, \"abc\"]").values(BigInteger.class);
    JsonAccessException ex = assertThrows(JsonAccessException.class, values::toList);
    assertEquals(NumberFormatException.class, ex.getCause().getClass());
  }

  @Test
  void testValues_ToBigInteger_Empty() {
    assertEquals(List.of(), JsonMixed.of("[]").values(BigInteger.class).toList());
  }

  @Test
  void testValues_ToBigInteger_NullArray() {
    // values() returns empty for JSON null array, so values(Class) follows
    assertEquals(List.of(), JsonMixed.of("null").values(BigInteger.class).toList());
  }

  @Test
  void testValues_ToBigDecimal() {
    assertEquals(
        List.of(new BigDecimal("1.5"), new BigDecimal("-2.5"), new BigDecimal("3.14")),
        JsonMixed.of("[1.5, -2.5, 3.14]").values(BigDecimal.class).toList());
  }

  @Test
  void testValues_ToBigDecimal_PreservesScale() {
    // BigDecimal.equals compares value AND scale, so this pins down that the
    // path is not going through a normalising parse. "1.50" must stay scale-2.
    assertEquals(
        List.of(new BigDecimal("1.50"), new BigDecimal("1.5"), new BigDecimal("2")),
        JsonMixed.of("[1.50, 1.5, 2]").values(BigDecimal.class).toList());
  }

  @Test
  void testValues_ToBigDecimal_BeyondDouble() {
    // only BigDecimal can hold these — if this fails with Infinity, the impl is
    // routing through double somewhere
    assertEquals(
        List.of(new BigDecimal("1e400"), new BigDecimal("1e-400")),
        JsonMixed.of("[1e400, 1e-400]").values(BigDecimal.class).toList());
  }

  @Test
  void testValues_ToBigDecimal_NotANumberThrows() {
    Streamable<BigDecimal> values = JsonMixed.of("[1, \"abc\"]").values(BigDecimal.class);
    JsonAccessException ex = assertThrows(JsonAccessException.class, values::toList);
    assertEquals(NumberFormatException.class, ex.getCause().getClass());
  }

  @Test
  void testValues_ToBigDecimal_Empty() {
    assertEquals(List.of(), JsonMixed.of("[]").values(BigDecimal.class).toList());
  }

  @Test
  void testValues_ToBigDecimal_NullArray() {
    assertEquals(List.of(), JsonMixed.of("null").values(BigDecimal.class).toList());
  }

  // ---------------------------------------------------------------------------
  // Null elements: nullable types (boxed and big-number) preserve Java null;
  // primitive streams cannot and must throw.
  // ---------------------------------------------------------------------------

  @Test
  void testValues_ToBigInteger_WithNull() {
    assertEquals(
        Arrays.asList(BigInteger.ONE, null, BigInteger.valueOf(2)),
        JsonMixed.of("[1, null, 2]").values(BigInteger.class).toList());
  }

  @Test
  void testValues_ToBigInteger_OnlyNull() {
    assertEquals(
        Arrays.asList((BigInteger) null),
        JsonMixed.of("[null]").values(BigInteger.class).toList());
  }

  @Test
  void testValues_ToBigInteger_AllNull() {
    assertEquals(
        Arrays.asList((BigInteger) null, null, null),
        JsonMixed.of("[null, null, null]").values(BigInteger.class).toList());
  }

  @Test
  void testValues_ToBigDecimal_WithNull() {
    assertEquals(
        Arrays.asList(new BigDecimal("1.5"), null, new BigDecimal("2.5")),
        JsonMixed.of("[1.5, null, 2.5]").values(BigDecimal.class).toList());
  }

  @Test
  void testValues_ToBigDecimal_OnlyNull() {
    assertEquals(
        Arrays.asList((BigDecimal) null),
        JsonMixed.of("[null]").values(BigDecimal.class).toList());
  }

  // ---------------------------------------------------------------------------
  // bigIntegerValues() / bigDecimalValues() are STRICT text parsers: a JSON
  // null element comes through textValues() as the literal text "null", which
  // does not parse as a number. This is the same contract as intValues() /
  // longValues() / doubleValues() — the whole *Values() family is strict.
  //
  // The null-aware variant is values(BigInteger.class) / values(BigDecimal.class),
  // tested separately above, which goes through the node type system and
  // preserves Java null via @TerminalOp(canBeUndefined = true).
  // ---------------------------------------------------------------------------

  @Test
  void testBigIntegerValues_WithNullThrows() {
    Streamable<BigInteger> values = JsonMixed.of("[1, null, 2]").bigIntegerValues();
    assertThrows(NumberFormatException.class, values::toList);
  }

  @Test
  void testBigDecimalValues_WithNullThrows() {
    Streamable<BigDecimal> values = JsonMixed.of("[1.5, null, 2.5]").bigDecimalValues();
    assertThrows(NumberFormatException.class, values::toList);
  }

  @Test
  void testNullElement_StrictParseVsNodeConversion() {
    // Same input, two entry points, two deliberate behaviours:
    //
    //   *Values() family: parses raw text => "null" doesn't parse => throws
    //   values(Class):     converts nodes    => JSON null becomes Java null
    //
    // The split is by abstraction level, not by type. It matches the
    // intValue()-vs-intValues() split documented on intValues().
    String json = "[1, null, 2]";

    assertThrows(
        NumberFormatException.class,
        () -> JsonMixed.of(json).bigIntegerValues().toList());

    assertEquals(
        Arrays.asList(BigInteger.ONE, null, BigInteger.valueOf(2)),
        JsonMixed.of(json).values(BigInteger.class).toList());
  }

  // ---------------------------------------------------------------------------
  // Primitive streams cannot represent null — the contrast is intentional.
  // ---------------------------------------------------------------------------

  @Test
  void testIntValues_WithNullThrows() {
    IntStream values = JsonMixed.of("[1, null, 2]").intValues();
    assertThrows(NumberFormatException.class, values::toArray);
  }

  @Test
  void testLongValues_WithNullThrows() {
    LongStream values = JsonMixed.of("[1, null, 2]").longValues();
    assertThrows(NumberFormatException.class, values::toArray);
  }

  @Test
  void testDoubleValues_WithNullThrows() {
    DoubleStream values = JsonMixed.of("[1, null, 2]").doubleValues();
    assertThrows(NumberFormatException.class, values::toArray);
  }

  // ---------------------------------------------------------------------------
  // Boxed primitives follow the same nullable convention as big-number types —
  // this is the precedent that makes the big-type behaviour consistent, not an
  // isolated exception.
  // ---------------------------------------------------------------------------

  @Test
  void testValues_ToInteger_WithNull() {
    assertEquals(
        Arrays.asList(1, null, 2),
        JsonMixed.of("[1, null, 2]").values(Integer.class).toList());
  }

  // ---------------------------------------------------------------------------
  // values(int.class) vs values(Integer.class): same static type, different
  // runtime Class token. The impl must distinguish them.
  //
  //   values(Integer.class) is null-safe  (via integer()      @TerminalOp(canBeUndefined))
  //   values(int.class)     is null-hostile (via intValue()   @TerminalOp without canBeUndefined)
  //
  // The exception type below must be tightened once confirmed; JsonAccessException
  // matches the pattern of testValues_ToBigInteger_FractionalThrows (wrapping the
  // cause), JsonTreeException would mean intValue()'s own terminal-op check fires.
  // ---------------------------------------------------------------------------

  @Test
  void testValues_ToInt_WithNullThrows() {
    // primitive int.class cannot represent null — contrast with Integer.class above
    Streamable<Integer> values = JsonMixed.of("[1, null, 2]").values(int.class);
    JsonAccessException ex = assertThrows(JsonAccessException.class, values::toList);
    assertEquals(JsonTreeException.class, ex.getCause().getClass());
  }

  @Test
  void testValues_ToIntVsInteger_NullContrast() {
    // Same input, two class literals, two outcomes — the pin for the
    // "primitives can't be null" half of the contract.
    String json = "[1, null, 2]";

    JsonAccessException ex = assertThrows(
        JsonAccessException.class,
        () -> JsonMixed.of(json).values(int.class).toList());
    assertEquals(JsonTreeException.class, ex.getCause().getClass());

    assertEquals(
        Arrays.asList(1, null, 2),
        JsonMixed.of(json).values(Integer.class).toList());
  }

  // ---------------------------------------------------------------------------
  // values(int.class) has CAST semantics (Number.intValue()), the opposite of
  // intValues()' PARSE semantics (Integer.parseInt()). Pin the contrast so a
  // future "unification" that routes values(int.class) through parseInt gets
  // caught immediately.
  // ---------------------------------------------------------------------------

  @Test
  void testValues_ToInt_Truncates() {
    // intValue() semantics, NOT parseInt(): "3.14" truncates to 3.
    // intValues() would throw NumberFormatException here — that's the split.
    assertEquals(
        List.of(1, 3, 5),
        JsonMixed.of("[1, 3.14, 5]").values(int.class).toList());
  }

  @Test
  void testValues_ToInt_OverflowWraps() {
    // intValue() semantics, NOT parseInt(): 2147483648 wraps to MIN_VALUE.
    // intValues() would throw ArithmeticException here — that's the split.
    assertEquals(
        List.of(Integer.MIN_VALUE),
        JsonMixed.of("[2147483648]").values(int.class).toList());
  }

  @Test
  void testValues_ToIntVsIntValues() {
    // the whole split, in one test, both directions — this is the tripwire
    // if anyone ever "unifies" values(int.class) with intValues()
    String json = "[1, 3.14, 2147483648]";

    // intValues(): strict parse, throws on the first non-conforming element
    assertThrows(
        NumberFormatException.class,
        () -> JsonMixed.of(json).intValues().toArray());

    // values(int.class): Number.intValue() cast — truncates and wraps
    assertEquals(
        List.of(1, 3, Integer.MIN_VALUE),
        JsonMixed.of(json).values(int.class).toList());
  }
}
