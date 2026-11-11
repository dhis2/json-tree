package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.hisp.dhis.jsontree.internal.Language;
import org.junit.jupiter.api.Test;

/**
 * Tests for the {@link JsonNumber} arbitrary-precision accessors added in 1.14.
 *
 * <p>Contract under test:
 *
 * <ul>
 *   <li>{@link JsonNumber#bigInteger()} is <b>strict</b>: exact for integer values (including
 *       decimal literals whose fractional part is zero and exponent forms), throws on a non-zero
 *       fractional part, and is not bounded by {@code long} range.
 *   <li>{@link JsonNumber#bigDecimal()} is <b>exact</b>: never loses information for finite
 *       inputs, preserves the input scale (so {@code "3.140"} round-trips to a {@code
 *       BigDecimal} that {@code toString}s as {@code "3.140"}, not {@code "3.14"}), and is not
 *       bounded by {@code double} range.
 *   <li>Both return {@code null} for undefined or JSON {@code null} (matching {@link
 *       JsonNumber#integer()}), and throw {@link JsonTreeException} for non-number nodes.
 *   <li>{@code NaN} and {@code ±Infinity} (JSON5) throw {@link NumberFormatException} — they have
 *       no {@code BigInteger} or {@code BigDecimal} representation.
 * </ul>
 *
 * @author AI
 */
class JsonBigNumberTest {

  // ---------------------------------------------------------------------------
  // BigInteger
  // ---------------------------------------------------------------------------

  @Test
  void testBigInteger_Int() {
    assertEquals(BigInteger.valueOf(42), number("42").bigInteger());
    assertEquals(BigInteger.valueOf(0), number("0").bigInteger());
    assertEquals(BigInteger.valueOf(Integer.MIN_VALUE), number("-2147483648").bigInteger());
  }

  @Test
  void testBigInteger_Long() {
    assertEquals(BigInteger.valueOf(2147483648L), number("2147483648").bigInteger());
    assertEquals(BigInteger.valueOf(Long.MAX_VALUE), number("9223372036854775807").bigInteger());
    assertEquals(BigInteger.valueOf(Long.MIN_VALUE), number("-9223372036854775808").bigInteger());
  }

  @Test
  void testBigInteger_BeyondLong() {
    assertEquals(
        new BigInteger("9223372036854775808"), number("9223372036854775808").bigInteger());
    assertEquals(
        new BigInteger("123456789012345678901234567890"),
        number("123456789012345678901234567890").bigInteger());
  }

  @Test
  void testBigInteger_DecimalWithZeroFraction() {
    // 3.0, 3.00, 5.0, 1.5e1, 1e3 all denote integer values
    assertEquals(BigInteger.valueOf(3), number("3.0").bigInteger());
    assertEquals(BigInteger.valueOf(3), number("3.00").bigInteger());
    assertEquals(BigInteger.valueOf(5), number("5.0").bigInteger());
    assertEquals(BigInteger.valueOf(15), number("1.5e1").bigInteger());
    assertEquals(BigInteger.valueOf(1000), number("1e3").bigInteger());
  }

  @Test
  void testBigInteger_Fractional_Throws() {
    // unlike Number.intValue()/longValue(), bigInteger() must not truncate
    assertThrows(ArithmeticException.class, () -> number("3.14").bigInteger());
    assertThrows(ArithmeticException.class, () -> number("-0.5").bigInteger());
    assertThrows(ArithmeticException.class, () -> number("1.0001").bigInteger());
  }

  @Test
  void testBigInteger_NegativeZero() {
    // BigInteger has no signed zero; both forms map to zero
    assertEquals(BigInteger.ZERO, number("-0").bigInteger());
    assertEquals(BigInteger.ZERO, number("-0.0").bigInteger());
    assertEquals(BigInteger.ZERO, number("0E-60").bigInteger());
  }

  @Test
  void testBigInteger_Undefined_ReturnsNull() {
    // JSON null behaves like undefined for value accessors on JsonNumber
    assertNull(JsonMixed.of("null").as(JsonNumber.class).bigInteger());
  }

  @Test
  void testBigInteger_NonNumber_Throws() {
    JsonNumber n = JsonMixed.of("\"abc\"").as(JsonNumber.class);
    assertThrowsExactly(NumberFormatException.class, n::bigInteger);
  }

  @Test
  void testBigInteger_NonStringNumber_Throws() {
    JsonNumber n = JsonMixed.of("true").as(JsonNumber.class);
    assertThrowsExactly(JsonTreeException.class, n::bigInteger);
  }

  @Test
  void testBigInteger_NaN_Throws() {
    JsonNumber n = Json5.of(" NaN ").as(JsonNumber.class);
    assertThrows(NumberFormatException.class, n::bigInteger);
  }

  @Test
  void testBigInteger_Infinity_Throws() {
    assertThrows(
        NumberFormatException.class,
        Json5.of(" Infinity ").as(JsonNumber.class)::bigInteger);
    assertThrows(
        NumberFormatException.class,
        Json5.of(" -Infinity ").as(JsonNumber.class)::bigInteger);
  }

  // ---------------------------------------------------------------------------
  // BigDecimal
  // ---------------------------------------------------------------------------

  @Test
  void testBigDecimal_SimpleDecimal() {
    assertEquals(new BigDecimal("3.14"), number("3.14").bigDecimal());
    assertEquals(new BigDecimal("-2.5"), number("-2.5").bigDecimal());
    assertEquals(new BigDecimal("0.001"), number("0.001").bigDecimal());
  }

  @Test
  void testBigDecimal_PreservesScale() {
    // Exactness here means preserving the input scale, not just the value.
    // BigDecimal.equals() compares scale, so this asserts both value and scale.
    assertEquals(new BigDecimal("3.140"), number("3.140").bigDecimal());
    assertEquals(new BigDecimal("3.14"), number("3.14").bigDecimal());
    // 42 vs 42.0 differ in scale; each round-trips exactly
    assertEquals(new BigDecimal("42"), number("42").bigDecimal());
    assertEquals(new BigDecimal("42.0"), number("42.0").bigDecimal());
  }

  @Test
  void testBigDecimal_HugeInteger() {
    assertEquals(
        new BigDecimal("123456789012345678901234567890"),
        number("123456789012345678901234567890").bigDecimal());
  }

  @Test
  void testBigDecimal_ScientificNotation() {
    // 1.5e10 -> unscaled 15, scale -9; toString is "1.5E+10"
    assertEquals(new BigDecimal("1.5e10"), number("1.5e10").bigDecimal());
    // value check for good measure
    assertEquals(
        0,
        new BigDecimal("15000000000")
            .compareTo(number("1.5e10").bigDecimal().stripTrailingZeros()));
  }

  @Test
  void testBigDecimal_VerySmall() {
    // precision beyond what a double can distinguish — should be exact
    assertEquals(new BigDecimal("1e-100"), number("1e-100").bigDecimal());
    assertEquals(
        new BigDecimal("1.234567890123456789012345678901234567890"),
        number("1.234567890123456789012345678901234567890").bigDecimal());
  }

  @Test
  void testBigDecimal_VeryLarge() {
    // well beyond double range — only BigDecimal can hold this exactly
    assertEquals(new BigDecimal("1e400"), number("1e400").bigDecimal());
  }

  @Test
  void testBigDecimal_Undefined_ReturnsNull() {
    assertNull(JsonMixed.of("null").as(JsonNumber.class).bigDecimal());
  }

  @Test
  void testBigDecimal_NonNumber_Throws() {
    JsonNumber n = JsonMixed.of("true").as(JsonNumber.class);
    assertThrowsExactly(JsonTreeException.class, n::bigDecimal);
  }

  @Test
  void testBigDecimal_NonStringNumber_Throws() {
    JsonNumber n = JsonMixed.of("\"abc\"").as(JsonNumber.class);
    assertThrowsExactly(NumberFormatException.class, n::bigDecimal);
  }

  @Test
  void testBigDecimal_NaN_Throws() {
    JsonNumber n = Json5.of(" NaN ").as(JsonNumber.class);
    assertThrows(NumberFormatException.class, n::bigDecimal);
  }

  @Test
  void testBigDecimal_Infinity_Throws() {
    assertThrows(
        NumberFormatException.class,
        Json5.of(" Infinity ").as(JsonNumber.class)::bigDecimal);
    assertThrows(
        NumberFormatException.class,
        Json5.of(" -Infinity ").as(JsonNumber.class)::bigDecimal);
  }

  // ---------------------------------------------------------------------------
  // helpers
  // ---------------------------------------------------------------------------

  private static JsonNumber number(@Language("json") String json) {
    return JsonMixed.of(json).as(JsonNumber.class);
  }
}