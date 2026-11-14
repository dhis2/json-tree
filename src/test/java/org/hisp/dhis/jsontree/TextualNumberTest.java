package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigInteger;
import java.util.Random;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * While does only test {@link TextualNumber#parseDouble(char[], int, int)} directly the function uses
 * {@link TextualNumber#parseIntExact(char[], int, int)} and {@link TextualNumber#parseLongExact(char[], int, int)} internally
 * to parse exponents and doubles without decimals. That way these do have coverage.
 *
 * @author Jan Bernitt (with AI suggestions for edge cases)
 */
class TextualNumberTest {

  private static final int NUM_RANDOM_TESTS = 10_000;
  private final Random rng = new Random(123456789L);

  @DisplayName("[+/-]0*[.[0+]")
  @Test
  void testParseDouble_Zeros() {
    String zeros =
        """
            0 .0 .00 0.0 0.00 00.00
            -0 -.0 -.00 -0.0 -0.00 -00.00
            +0 +.0 +.00 +0.0 +0.00 +00.00
            """;
    for (String n : zeros.split("\\s+")) assertDoubleEquals(n);
  }

  @Test
  void testParseDouble_RandomIntegers() {
    StringBuilder num = new StringBuilder(32);
    for (int k = 0; k < NUM_RANDOM_TESTS; k++) {
      num.setLength(0);
      appendInteger(num);

      String number = num.toString();
      assertDoubleEquals(number);
      assertLongEquals(number);
      assertLongCastEquals(number);
      if (number.length() < 10) {
        assertIntEquals(number);
        assertIntCastEquals(number);
      }
    }
  }

  @Test
  void testParseDouble_RandomDecimals() {
    StringBuilder num = new StringBuilder(32);
    for (int k = 0; k < NUM_RANDOM_TESTS; k++) {
      num.setLength(0);
      appendDecimal(num);

      assertDoubleEquals(num.toString());
    }
  }

  @Test
  void testParseDouble_RandomScientific() {
    StringBuilder num = new StringBuilder(32);
    for (int k = 0; k < NUM_RANDOM_TESTS; k++) {
      num.setLength(0);
      appendScientific(num);

      assertDoubleEquals(num.toString());
    }
  }

  @Test
  void testTextualNumber_SignedZero_Fallback() {
    // fallback path: exponent too large for fast path, significand all zeros
    assertEquals(-0.0, TextualNumber.of("-0E-60".toCharArray()).doubleValue());
    assertEquals( 0.0, TextualNumber.of( "0E-60".toCharArray()).doubleValue());
    assertEquals(-0.0, TextualNumber.of("-0.00E+0".toCharArray()).doubleValue());
    // make sure the sign is *not* wrongly applied to non-zero values reaching the fallback
    assertEquals(-1e-60, TextualNumber.of("-1E-60".toCharArray()).doubleValue());
    assertEquals( 1e-60, TextualNumber.of( "1E-60".toCharArray()).doubleValue());
  }

  @Test
  void testParseDouble_OverflowCases() {
    // if naively handled as long it would overflow and returns wrong value
    assertDoubleEquals("9999999999999999999.0");
  }

  @Test
  void testParseDouble_EdgeCases() {
    String cases =
        """
                // Named
                NaN Infinity -Infinity

                // Zeros and signed zero
                0 -0 +0 0.0 -0.0 +0.0
                0e0 -0e0 0e-10 0e+10

                // Leading zeros
                00123 -000456 +000.789
                0.00123 -0.000456 +0.000789
                000123.456 -000.789e2

                // Missing integer/fractional parts
                .5 -.5 +.5
                123. -123. +123.
                .5e2 123.e-2

                // Plus sign
                +123 +123.456 +1.23e+4

                // Trailing zeros after decimal
                123.0 -123.0 123.00 0.0 0.00

                // Boundary of fast‑path (15 significant digits)
                -123456789012345          // 15 digits
                123456789012345          // 15 digits
                123456789012345.0        // still 15 significant
                123456789012345e0        // 15 significant
                123456789012345.678      // >15 significant -> fallback

                // Exponent boundary (|effExp| = 22)
                1e22 1e-22 1.23e22 1.23e-22
                1e23 1e-23               // |effExp| > 22 -> fallback

                // Very long digit sequences (force fallback)
                12345678901234567890
                0.12345678901234567890
                12345678901234567890e5
                1e999 -1e-999

                // Large exponent but small significand (should still fallback if exponent too large)
                1e300 1e-300

                // Subnormal and overflow boundaries
                4.9e-324                   // smallest positive subnormal
                1.8e308                    // near max finite
                1e310                       // overflow to infinity
                -1e310                      // overflow to -infinity

                // Exact powers of ten
                1e0 1e1 1e10 1e-10
                10.0 100.0

                // Strings with mixed case exponent
                1E2 1E-2 1E+2

                // Strings that Double.toString might produce (but we also want them here)
                3.141592653589793
                2.2250738585072014e-308    // Double.MIN_NORMAL
            """;

    String[] numbers =
        cases
            .lines()
            .map(line -> line.indexOf('/') < 0 ? line : line.substring(0, line.indexOf('/')))
            .collect(Collectors.joining())
            .split("\\s+");
    for (String num : numbers) if (!num.isBlank()) assertDoubleEquals(num);
  }

  @Test
  void testParseDouble_InvalidInputsThrow() {
    String invalid =
        """
                 + - . e E +. -e .e,
                123e 123e- 123e+,
                123.. 123e.5 123e1.2,
                NaNx Infiniti -Infiniti
                Foo bar
                """;
    for (String num : invalid.split("\\s+")) assertDoubleThrows(num);
    assertDoubleThrows("");
    assertDoubleThrows(" ");
  }

  @Test
  void testParseInt_EdgeCases() {
    assertIntEquals(String.valueOf(Integer.MAX_VALUE));
    assertIntEquals(String.valueOf(Integer.MIN_VALUE));
    assertIntEquals("+2147483647"); // max with plus sign
    assertIntEquals("02147483647"); // max with leading zero
    assertIntEquals("-02147483648"); // min with leading zero
    assertIntEquals("0000000000000000000000000000000000000000000000000000000000");
  }

  @Test
  void testParseInt_Cast() {
    assertIntCastEquals("100.0");
    assertIntCastEquals("123456.7890");
    assertIntCastEquals("-0.7890");
  }

  @Test
  void testParseIntCast_FallbackToDouble() {
    assertIntCastEquals("1e5");
    assertIntCastEquals("-1e5");
    assertIntCastEquals("+1e5");
    assertIntCastEquals("1E5");
    assertIntCastEquals("1.5e3");
    assertIntCastEquals(".5");
    assertIntCastEquals(".99");
    assertIntCastEquals("1e20");   // (int) 1e20  == Integer.MAX_VALUE
    assertIntCastEquals("-1e20");  // (int) -1e20 == Integer.MIN_VALUE
    assertIntCastEquals("1e-20");  // -> 0
    assertIntCastEquals("1e999");  // -> (int) Infinity == Integer.MAX_VALUE
  }

  @Test
  void testParseInt_EdgeCasesThrows() {
    assertIntThrows("02147483647.0"); // max with leading zero + 0 fraction
    assertIntThrows("-02147483648.0"); // min with leading zero + 0 fraction
    assertIntThrows("483647.0"); // 0 fraction
    assertIntThrows("-7483648.0"); // 0 fraction
    // in contrast to Number API (parseInt) an overflow always throws ArithmeticException
    assertIntThrows("2147483648", ArithmeticException.class); // max + 1
    assertIntThrows("-2147483649", ArithmeticException.class); // min - 1
    assertIntThrows("4294967296", ArithmeticException.class); // overflows to positive number
  }

  @Test
  void testParseLong_EdgeCases() {
    assertLongEquals(String.valueOf(Long.MAX_VALUE));
    assertLongEquals(String.valueOf(Long.MIN_VALUE));
    assertLongEquals("+9223372036854775807"); // max with + sign
    assertLongEquals("09223372036854775807"); // max with leading zero
    assertLongEquals("-09223372036854775807"); // min with leading zero
    assertLongEquals("0000000000000000000000000000000000000000000000000000000000");
  }


  @Test
  void testParseLong_Cast() {
    assertLongCastEquals("100.0");
    assertLongCastEquals("123456.7890");
    assertLongCastEquals("-0.7890");
  }

  @Test
  void testParseLongCast_FallbackToDouble() {
    // exponent form: isIntegerLiteral == false and offsetExponent >= 0 -> fallback
    assertLongCastEquals("1e5");
    assertLongCastEquals("-1e5");
    assertLongCastEquals("+1e5");
    assertLongCastEquals("1E5");
    assertLongCastEquals("1.5e3");
    // leading decimal point: isIntegerLiteral == false, offsetExponent < 0, dpOffset == 0
    // -> no recursion possible -> fallback
    assertLongCastEquals(".5");
    assertLongCastEquals(".99");
    // cast saturates when parseDoubleDecimal produces a value beyond long range
    assertLongCastEquals("1e20");   // (long) 1e20  == Long.MAX_VALUE
    assertLongCastEquals("-1e20");  // (long) -1e20 == Long.MIN_VALUE
    // cast truncates toward zero
    assertLongCastEquals("1e-20");  // -> 0L
    // exponent too large even for the fast path: exercises the innermost
    // Double.parseDouble fallback in parseDoubleDecimal too
    assertLongCastEquals("1e999");  // -> (long) Infinity == Long.MAX_VALUE
  }

  @Test
  void testParseLong_EdgeCasesThrows() {
    assertLongThrows("09223372036854775807.0"); // max with leading zero + 0 fraction
    assertLongThrows("-09223372036854775807.0"); // min with leading zero + 0 fraction
    assertLongThrows("3372036854775807.0"); // 0 fraction
    assertLongThrows("-36854775807.0"); // 0 fraction
    // in contrast to Number API (parseLong) an overflow always throws ArithmeticException
    assertLongThrows("9223372036854775808", ArithmeticException.class); // max + 1
    assertLongThrows("-9223372036854775809", ArithmeticException.class); // min - 1
    assertLongThrows("9300072036854775808", ArithmeticException.class); // a too large number pre-identified
    assertLongThrows("9223372036854775808", ArithmeticException.class); // a too large number not pre-identified
  }

  @Test
  void testIsNumericZero() {
    assertNumericZero("0");
    assertNumericZero("+0");
    assertNumericZero("-0");
    assertNumericZero("0.");
    assertNumericZero("0.0");
    assertNumericZero("-0.0");
    assertNumericZero("00.00");
    assertNumericZero("-00.00");

    assertNotNumericZero(".");
    assertNotNumericZero("+.");
    assertNotNumericZero("-.");
    assertNotNumericZero("1");
    assertNotNumericZero("0.1");
    assertNotNumericZero("0.0001");
    assertNotNumericZero("0.000100");
    assertNotNumericZero("-1");
    assertNotNumericZero("-0.1");
    assertNotNumericZero("-0.00010");
    assertNotNumericZero("hello");
  }

  @Test
  @SuppressWarnings("SimplifiableAssertion")
  void testOf_SignedZero_ExponentForm() {
    // Note that we must use assertTrue(Double.valueOf(-0.0).equals(...)
    // as assertEquals(-0.0, ...) would hide the sign significance as 0.0 = -0.0
    assertTrue(Double.valueOf(-0.0).equals(TextualNumber.of("-0E-60".toCharArray()).doubleValue()));
    assertTrue(Double.valueOf( 0.0).equals(TextualNumber.of( "0E-60".toCharArray()).doubleValue()));
    assertTrue(Double.valueOf(-0.0).equals(TextualNumber.of("-0.00E+0".toCharArray()).doubleValue()));
    assertTrue(Double.valueOf(-0.0).equals(TextualNumber.of("-00.0E-60".toCharArray()).doubleValue()));
  }

  @Test
  void testOf_MalformedExponent_StillThrows() {
    // regression guard for the reorder: a naive isNumericZero fix that only strips
    // the exponent would return 0d for these and skip validation entirely
    assertThrows(NumberFormatException.class, () -> TextualNumber.of("0E".toCharArray()));
    assertThrows(NumberFormatException.class, () -> TextualNumber.of("-0E+".toCharArray()));
    assertThrows(NumberFormatException.class, () -> TextualNumber.of("0.0E".toCharArray()));
  }

  /*
   * Regression guards for the Double.parseDouble-faithful fallback in TextualNumber.
   *
   * The fallback uses the allocation-free
   *   new BigDecimal(buffer, offset, length).doubleValue()
   * to convert a numeric literal to double. BigDecimal's scale is a 32-bit int,
   * so its constructor rejects literals whose exponent exceeds Integer.MAX_VALUE
   * with a NumberFormatException, whereas Double.parseDouble saturates to
   * +/-Infinity (or to +/-0.0 on underflow). TextualNumber detects an exponent
   * that cannot fit an int and routes those literals through Double.parseDouble
   * instead. Without that check these tests fail with a NumberFormatException.
   */

  @Test
  void testOf_DoubleValue_HugeExponent_Overflow() {
    // 12-digit exponent, far beyond Integer.MAX_VALUE. Double.parseDouble
    // accepts it and saturates; a naked BigDecimal constructor would throw.
    assertEquals(
        Double.POSITIVE_INFINITY,
        TextualNumber.of("1e999999999999".toCharArray()).doubleValue());
    assertEquals(
        Double.NEGATIVE_INFINITY,
        TextualNumber.of("-1e999999999999".toCharArray()).doubleValue());
  }

  @Test
  @SuppressWarnings("SimplifiableAssertion")
  void testOf_DoubleValue_HugeExponent_Underflow() {
    assertEquals(
        0.0,
        TextualNumber.of("1e-999999999999".toCharArray()).doubleValue());
    // underflow must preserve sign like Double.parseDouble does
    assertTrue(
        Double.valueOf(-0.0).equals(
            TextualNumber.of("-1e-999999999999".toCharArray()).doubleValue()));
  }

  @Test
  void testOf_DoubleValue_ExponentBoundary() {
    // Integer.MAX_VALUE = 2147483647 (10 digits) fits BigDecimal's int scale
    // and would succeed through the BigDecimal path too.
    assertEquals(
        Double.POSITIVE_INFINITY,
        TextualNumber.of("1e2147483647".toCharArray()).doubleValue());
    // Integer.MAX_VALUE + 1 = 2147483648 (also 10 digits) does NOT fit; the
    // raw BigDecimal constructor throws NumberFormatException here.
    assertEquals(
        Double.POSITIVE_INFINITY,
        TextualNumber.of("1e2147483648".toCharArray()).doubleValue());
    assertEquals(
        Double.NEGATIVE_INFINITY,
        TextualNumber.of("-1e2147483649".toCharArray()).doubleValue());
  }

  @Test
  void testOf_DoubleValue_NormalExponent_StillWorks() {
    // sanity: the detection must not disturb the common path
    assertEquals(1000.0, TextualNumber.of("1e3".toCharArray()).doubleValue());
    assertEquals(0.001, TextualNumber.of("1e-3".toCharArray()).doubleValue());
    assertEquals(-3.14, TextualNumber.of("-3.14".toCharArray()).doubleValue());
    assertEquals(3.14, TextualNumber.of("3.14".toCharArray()).doubleValue());
  }

  @Test
  @SuppressWarnings("UnnecessaryBoxing")
  void testOf_IntegerPromotion() {
    // fits in int
    assertEquals(Integer.valueOf(0), TextualNumber.of("0".toCharArray()));
    assertEquals(Integer.valueOf(42), TextualNumber.of("42".toCharArray()));
    assertEquals(Integer.valueOf(Integer.MIN_VALUE), TextualNumber.of("-2147483648".toCharArray()));
    // fits in long but not int
    assertEquals(Long.valueOf(2147483648L), TextualNumber.of("2147483648".toCharArray()));
    assertEquals(Long.valueOf(Long.MAX_VALUE), TextualNumber.of("9223372036854775807".toCharArray()));
    assertEquals(Long.valueOf(Long.MIN_VALUE), TextualNumber.of("-9223372036854775808".toCharArray()));
    // just past long
    assertEquals(new BigInteger("9223372036854775808"), TextualNumber.of("9223372036854775808".toCharArray()));
    assertEquals(new BigInteger("-9223372036854775809"), TextualNumber.of("-9223372036854775809".toCharArray()));
    // very large
    assertEquals(new BigInteger("123456789012345678901234567890"),
        TextualNumber.of("123456789012345678901234567890".toCharArray()));
  }

  @Test
  @SuppressWarnings("UnnecessaryBoxing")
  void testOf_IntegerPromotion_LeadingZeros() {
    // regression: the old length > 20 shortcut would have promoted these to BigInteger
    // even though the values fit in int
    assertEquals(Integer.valueOf(1), TextualNumber.of("0000000000000000000001".toCharArray()));
    assertEquals(Integer.valueOf(0), TextualNumber.of("0000000000000000000000".toCharArray()));
    assertEquals(Integer.valueOf(1),
        TextualNumber.of("0000000000000000000000000000001".toCharArray()));
    // but a genuine 21-digit value still promotes
    assertEquals(new BigInteger("123456789012345678901"),
        TextualNumber.of("123456789012345678901".toCharArray()));
  }

  @Test
  @SuppressWarnings("UnnecessaryBoxing")
  void testOf_NegativeZero() {
    // -0 stays Double (preserves sign); 0 goes through the integer path
    assertEquals(Double.valueOf(-0.0), TextualNumber.of("-0".toCharArray()));
    assertEquals(Integer.valueOf(0), TextualNumber.of("0".toCharArray()));
  }

  @Test
  void testFitsInLong() {
    // straightforward fits
    assertTrue(TextualNumber.fitsInLong("0".toCharArray(), 0, 1));
    assertTrue(TextualNumber.fitsInLong("9223372036854775807".toCharArray(), 0, 19));   // MAX
    assertTrue(TextualNumber.fitsInLong("-9223372036854775808".toCharArray(), 0, 20));  // MIN
    // just past the boundary
    assertFalse(TextualNumber.fitsInLong("9223372036854775808".toCharArray(), 0, 19));  // MAX+1
    assertFalse(TextualNumber.fitsInLong("-9223372036854775809".toCharArray(), 0, 20)); // MIN-1
    // same digit count, prefix comparison decides (this is the case the old early check missed)
    assertFalse(TextualNumber.fitsInLong("9240000000000000000".toCharArray(), 0, 19));
    assertTrue (TextualNumber.fitsInLong("9200000000000000000".toCharArray(), 0, 19));
    // leading zeros don't count
    assertTrue (TextualNumber.fitsInLong("0000000000000000000001".toCharArray(), 0, 22));
    // sign-only and empty: caller guarantees isIntegerLiteral, but predicate still sane
    assertTrue (TextualNumber.fitsInLong("-0".toCharArray(), 0, 2));
  }

  @Test
  void testOf_PromotionWithoutExceptions() {
    // the fitsInLong path must not throw for any valid integer literal
    TextualNumber.of("9223372036854775807".toCharArray());   // fits
    TextualNumber.of("9223372036854775808".toCharArray());   // overflow → BigInteger
    TextualNumber.of("123456789012345678901234567890".toCharArray());  // far overflow → BigInteger
  }

  @Test
  void testParseDouble_19DigitBoundary() {
    // fits in long: goes via parseLongExact, no String allocation
    assertEquals(9223372036854775807L, (long) TextualNumber.parseDouble("9223372036854775807".toCharArray()));
    // 1 past long: goes via Double.parseDouble(String), still round-to-nearest-even
    assertEquals(9.223372036854776E18, TextualNumber.parseDouble("9223372036854775808".toCharArray()));
    // 19 nines: also doesn't fit, same fallback
    assertEquals(1.0E19, TextualNumber.parseDouble("9999999999999999999".toCharArray()));
  }

  /*
   * Tests for the 310-significant-digit short-circuit in parseDouble.
   *
   * A double can hold at most ~1.8e308. Any integer with 310 or more
   * significant decimal digits is therefore >= 10^309 > Double.MAX_VALUE,
   * so Double.parseDouble would return +/-Infinity. We short-circuit that
   * case to avoid the expensive Double.parseDouble.
   *
   * The critical tests are the ones that would fail for a naive
   * `length >= 310` check (which ignores sign, leading spaces and, most
   * importantly, leading zeros after a sign) and the exact boundary tests
   * that would fail for a threshold that is one too aggressive (309).
   */

  @Test
  void testParseDouble_310SignificantDigits_Unsigned() {
    String s = "9".repeat(310);
    assertEquals(Double.POSITIVE_INFINITY, TextualNumber.parseDouble(s.toCharArray()));
    assertDoubleEquals(s);
  }

  @Test
  void testParseDouble_310SignificantDigits_Negative() {
    String s = "-" + "9".repeat(310);
    assertEquals(Double.NEGATIVE_INFINITY, TextualNumber.parseDouble(s.toCharArray()));
    assertDoubleEquals(s);
  }

  @Test
  void testParseDouble_310SignificantDigits_PlusSign() {
    // guards against mixing up '+' with '-' (must NOT return -Infinity)
    String s = "+" + "9".repeat(310);
    assertEquals(Double.POSITIVE_INFINITY, TextualNumber.parseDouble(s.toCharArray()));
    assertDoubleEquals(s);
  }

  @Test
  void testParseDouble_310SignificantDigits_AfterLeadingZeros() {
    // no sign, 100 leading zeros must not count toward the 310 threshold
    String s = "0".repeat(100) + "9".repeat(310);
    assertEquals(Double.POSITIVE_INFINITY, TextualNumber.parseDouble(s.toCharArray()));
    assertDoubleEquals(s);
  }

  @Test
  void testParseDouble_310SignificantDigits_AfterSignAndLeadingZeros() {
    // the pathological case for a naive counter: sign + zeros + significant digits
    String s = "-" + "0".repeat(100) + "9".repeat(310);
    assertEquals(Double.NEGATIVE_INFINITY, TextualNumber.parseDouble(s.toCharArray()));
    assertDoubleEquals(s);
  }

  @Test
  void testParseDouble_ExactBoundary_310vs309() {
    // 310 significant digits => 10^309 > Double.MAX_VALUE => short-circuit to Infinity
    String overflow = "1" + "0".repeat(309);
    assertEquals(Double.POSITIVE_INFINITY, TextualNumber.parseDouble(overflow.toCharArray()));
    assertDoubleEquals(overflow);

    // 309 significant digits => 10^308 < Double.MAX_VALUE => must NOT short-circuit
    String finite = "1" + "0".repeat(308);
    double d = TextualNumber.parseDouble(finite.toCharArray());
    assertTrue(Double.isFinite(d), "10^308 must remain finite");
    assertEquals(1e308, d);
    assertDoubleEquals(finite);
  }

  @Test
  void testParseDouble_309SignificantDigits_Negative() {
    String s = "-1" + "0".repeat(308);
    double d = TextualNumber.parseDouble(s.toCharArray());
    assertTrue(Double.isFinite(d));
    assertTrue(d < 0);
    assertDoubleEquals(s);
  }

  @Test
  void testParseDouble_Various310DigitPatterns() {
    for (String s :
        new String[] {
            "1" + "0".repeat(309), // 10^309
            "1".repeat(310),       // ~1.11e309
            "9".repeat(310),       // ~1e310
            "1" + "9".repeat(309)  // ~2e309
        }) {
      assertEquals(
          Double.POSITIVE_INFINITY,
          TextualNumber.parseDouble(s.toCharArray()),
          "Expected Infinity for " + s.length() + "-digit integer starting with " + s.charAt(0));
      assertDoubleEquals(s);
    }
  }

  @Test
  void testParseDouble_LongButSmallValue_AfterSign_NoFalsePositive() {
    // 300 zeros after the sign + 20 significant digits => overflows long
    // (so it reaches the new code path) but is still a finite double.
    // A naive length-based check would wrongly return -Infinity here.
    String s = "-" + "0".repeat(300) + "1".repeat(20);
    double d = TextualNumber.parseDouble(s.toCharArray());
    assertTrue(Double.isFinite(d), "300 zeros after sign are not significant digits");
    assertTrue(d < 0);
    assertDoubleEquals(s);
  }

  @Test
  void testParseDouble_EvenLongerButSmallValue_NoFalsePositive() {
    // ~520 chars total but only 20 significant digits
    String s = "-" + "0".repeat(500) + "1".repeat(20);
    double d = TextualNumber.parseDouble(s.toCharArray());
    assertTrue(Double.isFinite(d));
    assertDoubleEquals(s);
  }

  @Test
  void testParseDouble_SignAndExactly310Zeros_Zero() {
    // all-zero significand: handled by the fitsInLong fast path, must remain -0.0
    String s = "-" + "0".repeat(310);
    double d = TextualNumber.parseDouble(s.toCharArray());
    assertTrue(Double.valueOf(-0.0).equals(d), "Expected -0.0, got " + d);
  }

  @Test
  void testParseDouble_LeadingSpacesThen310Digits() {
    // the initial lengthInsignificantZeros skips spaces; the sign (if any) is
    // then still at buffer[offset] and must be picked up correctly
    String s = "   +" + "9".repeat(310);
    assertEquals(Double.POSITIVE_INFINITY, TextualNumber.parseDouble(s.toCharArray()));
  }

  @Test
  void testParseDouble_LeadingSpacesSignAndZerosAnd310Digits() {
    String s = "   " + "-" + "0".repeat(100) + "9".repeat(310);
    assertEquals(Double.NEGATIVE_INFINITY, TextualNumber.parseDouble(s.toCharArray()));
  }

  /*
  Asserts
   */


  private static void assertNumericZero(String value) {
    assertTrue(TextualNumber.isZeroValue(value.toCharArray(), 0, value.length()));
  }

  private static void assertNotNumericZero(String value) {
    assertFalse(TextualNumber.isZeroValue(value.toCharArray(), 0, value.length()));
  }

  private static void assertDoubleThrows(String number) {
    assertThrowsExactly(
        NumberFormatException.class,
        () -> Double.parseDouble(number),
        () -> "Number is valid for Double.parseDouble: `" + number + "`");
    assertThrowsExactly(
        NumberFormatException.class,
        () -> TextualNumber.parseDouble(number.toCharArray()),
        "TextualNumber.parseDouble should throw for: " + number);
    assertThrowsExactly(NumberFormatException.class,
        () -> TextualNumber.of(number.toCharArray()),
        "TextualNumber.of() should throw for: "+number);
  }

  private static void assertDoubleEquals(String number) {
    char[] raw = number.toCharArray();
    double expected = Double.parseDouble(number);   // once, not 4×

    // of() has no bounds API — equivalent to the old `number.trim()` call, once.
    try {
      Number n = TextualNumber.of(raw);
      assertEquals(expected, n.doubleValue(),
          "Failed TextualNumber.of().doubleValue() for: " + number);
    } catch (NumberFormatException ex) {
      fail("Number valid for Double.parseDouble was rejected: "
          + number.replace(' ', '_'), ex);
    }

    // parseDoubleCast: 4 views of one padded buffer, zero extra char[]/String.
    assertDoubleCast(expected, raw, 0, raw.length, number);
    char[] padded = new char[raw.length + 2];
    System.arraycopy(raw, 0, padded, 1, raw.length);
    padded[0] = ' ';
    padded[padded.length - 1] = ' ';
    assertDoubleCast(expected, padded, 0, raw.length + 1, number);   // trailing
    assertDoubleCast(expected, padded, 1, raw.length + 1, number);   // leading
    assertDoubleCast(expected, padded, 0, raw.length + 2, number);   // both
  }

  private static void assertDoubleCast(double expected, char[] buf, int off, int len, String display) {
    try {
      double actual = TextualNumber.parseDouble(buf, off, len);
      assertEquals(expected, actual, "Failed for: " + display);
    } catch (NumberFormatException ex) {
      fail("Number valid for Double.parseDouble was rejected: "
          + display.replace(' ', '_'), ex);
    }
  }

  private static void assertLongEquals(String number) {
    long expected = Long.parseLong(number);
    try {
      long actual = TextualNumber.parseLongExact(number.toCharArray(), 0, number.length());
      assertEquals(expected, actual, "Failed parseLongExact() for: " + number);
      long actualCast = TextualNumber.parseLongCast(number.toCharArray(), 0, number.length());
      assertEquals(expected, actualCast, "Failed parseLongCast() for: " + number);
      Number actualNumber = TextualNumber.of(number.trim().toCharArray());
      assertEquals(expected, actualNumber.longValue(), "Failed TextualNumber.of().longValue() for: "+number);
    } catch (NumberFormatException ex) {
      fail("Number valid for Long.parseLong was rejected: " + number.replace(' ', '_'), ex);
    }
  }

  private static void assertLongCastEquals(String number) {
    long expected = (long) Double.parseDouble(number);
    try {
      long actual = TextualNumber.parseLongCast(number.toCharArray(), 0, number.length());
      assertEquals(expected, actual, "Failed parseLongCast() for: "+number);
      Number actualNumber = TextualNumber.of(number.trim().toCharArray());
      assertEquals(expected, actualNumber.longValue(), "Failed TextualNumber.of().longValue() for: "+number);
    } catch (NumberFormatException ex) {
      fail("Number valid for Double.parseDouble was rejected: " + number.replace(' ', '_'), ex);
    }
  }

  private static void assertLongThrows(String number) {
    assertLongThrows(number, NumberFormatException.class);
  }

  private static void assertLongThrows(String number, Class<? extends RuntimeException> expected) {
    assertThrowsExactly(
        NumberFormatException.class,
        () -> Long.parseLong(number),
        () -> "Number is valid for Long.parseLong: `" + number + "`");
    assertThrowsExactly(
        expected,
        () -> TextualNumber.parseLongExact(number.toCharArray()),
        "Should throw for: " + number);
  }

  private static void assertIntEquals(String number) {
    int expected = Integer.parseInt(number);
    try {
      int actual = TextualNumber.parseIntExact(number.toCharArray(), 0, number.length());
      assertEquals(expected, actual, "Failed parseIntExact() for: " + number);
      int actualCast = TextualNumber.parseIntCast(number.toCharArray(), 0, number.length());
      assertEquals(expected, actualCast, "Failed parseIntCast() for: " + number);
      Number actualNumber = TextualNumber.of(number.toCharArray());
      assertEquals(expected, actualNumber.intValue(), "Failed TextualNumber.of().intValue() for: "+number);
    } catch (NumberFormatException ex) {
      fail("Number valid for Integer.parseInt was rejected: " + number.replace(' ', '_'), ex);
    }
  }

  private static void assertIntCastEquals(String number) {
    int expected = (int) Double.parseDouble(number);
    try {
      int actual = TextualNumber.parseIntCast(number.toCharArray(), 0, number.length());
      assertEquals(expected, actual, "Failed parseIntCast() for: "+number);
    } catch (NumberFormatException ex) {
      fail("Number valid for Double.parseDouble was rejected: " + number.replace(' ', '_'), ex);
    }
  }

  private static void assertIntThrows(String number) {
    assertIntThrows(number, NumberFormatException.class);
  }

  private static void assertIntThrows(String number, Class<? extends RuntimeException> expected) {
    assertThrowsExactly(
        NumberFormatException.class,
        () -> Integer.parseInt(number),
        () -> "Number is valid for Integer.parseInt: `" + number + "`");
    assertThrowsExactly(
        expected,
        () -> TextualNumber.parseIntExact(number.toCharArray()),
        "Should throw for: " + number);
  }

  private void appendInteger(StringBuilder sb) {
    if (rng.nextBoolean()) sb.append(rng.nextBoolean() ? '-' : '+'); // optional sign
    // at least one digit
    int len = rng.nextInt(10) + 1; // 1..10 digits
    for (int i = 0; i < len; i++) sb.append((char) ('0' + rng.nextInt(10)));
  }

  private void appendDecimal(StringBuilder sb) {
    if (rng.nextBoolean()) sb.append(rng.nextBoolean() ? '-' : '+');
    if (rng.nextBoolean()) {
      int intLen = rng.nextInt(5) + 1; // 1..5 digits
      for (int i = 0; i < intLen; i++) sb.append((char) ('0' + rng.nextInt(10)));
    }
    sb.append('.');
    // fractional part (must have at least one digit)
    int fracLen = rng.nextInt(5) + 1;
    for (int i = 0; i < fracLen; i++) sb.append((char) ('0' + rng.nextInt(10)));
  }

  private void appendScientific(StringBuilder sb) {
    if (rng.nextBoolean()) {
      appendInteger(sb);
    } else {
      appendDecimal(sb);
    }
    sb.append(rng.nextBoolean() ? 'e' : 'E');
    if (rng.nextBoolean()) sb.append(rng.nextBoolean() ? '-' : '+'); // exponent sign
    int expLen = rng.nextInt(3) + 1; // 1..3 digits
    for (int i = 0; i < expLen; i++) sb.append((char) ('0' + rng.nextInt(10)));
  }
}
