package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * While does only test {@link Numbers#parseDouble(char[], int, int)} directly the function uses
 * {@link Numbers#parseInt(char[], int, int)} and {@link Numbers#parseLong(char[], int, int)} internally
 * to parse exponents and doubles without decimals. That way these do have coverage.
 *
 * @author Jan Bernitt (with AI suggestions for edge cases)
 */
class NumbersTest {

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

  @DisplayName("[+/-]#+")
  @Test
  void testParseDouble_RandomIntegers() {
    for (int k = 0; k < NUM_RANDOM_TESTS; k++) {
      StringBuilder num = new StringBuilder();
      appendInteger(num);

      assertDoubleEquals(num.toString());
    }
  }

  @DisplayName("[+/-]#*.#+")
  @Test
  void testParseDouble_RandomDecimals() {
    for (int k = 0; k < NUM_RANDOM_TESTS; k++) {
      StringBuilder num = new StringBuilder();
      appendDecimal(num);

      assertDoubleEquals(num.toString());
    }
  }

  @DisplayName("#*.#e[+/-]#+")
  @Test
  void testParseDouble_RandomScientific() {
    for (int k = 0; k < NUM_RANDOM_TESTS; k++) {
      StringBuilder num = new StringBuilder();
      appendScientific(num);

      assertDoubleEquals(num.toString());
    }
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
    assertIntThrows("02147483647.0"); // max with leading zero + 0 fraction
    assertIntThrows("-02147483648.0"); // min with leading zero + 0 fraction
    assertIntThrows("483647.0"); // 0 fraction
    assertIntThrows("-7483648.0"); // 0 fraction
    assertIntThrows("2147483648"); // max + 1
    assertIntThrows("-2147483649"); // min - 1
    assertIntThrows("4294967296"); // overflows to positive number
  }

  @Test
  void testParseLong_EdgeCases() {
    assertLongEquals(String.valueOf(Long.MAX_VALUE));
    assertLongEquals(String.valueOf(Long.MIN_VALUE));
    assertLongEquals("+9223372036854775807"); // max with + sign
    assertLongEquals("09223372036854775807"); // max with leading zero
    assertLongEquals("-09223372036854775807"); // min with leading zero
    assertLongEquals("0000000000000000000000000000000000000000000000000000000000");
    assertLongThrows("09223372036854775807.0"); // max with leading zero + 0 fraction
    assertLongThrows("-09223372036854775807.0"); // min with leading zero + 0 fraction
    assertLongThrows("3372036854775807.0"); // 0 fraction
    assertLongThrows("-36854775807.0"); // 0 fraction
    assertLongThrows("9223372036854775808"); // max + 1
    assertLongThrows("-9223372036854775809"); // min - 1
    assertLongThrows("9300072036854775808"); // a too large number pre-identified
    assertLongThrows("9223372036854775808"); // a too large number not pre-identified
  }

  private static void assertDoubleThrows(String number) {
    assertThrowsExactly(
        NumberFormatException.class,
        () -> Double.parseDouble(number),
        () -> "Number is valid for Double.parseDouble: `" + number + "`");
    assertThrowsExactly(
        NumberFormatException.class,
        () -> Numbers.parseDouble(number.toCharArray()),
        "Should throw for: " + number);
  }

  private static void assertDoubleEquals(String number) {
    assertDoubleEqualsExact(number);
    assertDoubleEqualsExact(number + " ");
    assertDoubleEqualsExact(" " + number);
    assertDoubleEqualsExact(" " + number + " ");
  }

  private static void assertDoubleEqualsExact(String number) {
    double expected = Double.parseDouble(number);
    try {
      double actual = Numbers.parseDouble(number.toCharArray(), 0, number.length());
      assertEquals(expected, actual, "Failed for: " + number);
    } catch (NumberFormatException ex) {
      fail("Number valid for Double.parseDouble was rejected: " + number.replace(' ', '_'), ex);
    }
  }

  private static void assertLongEquals(String number) {
    long expected = Long.parseLong(number);
    try {
      long actual = Numbers.parseLong(number.toCharArray(), 0, number.length());
      assertEquals(expected, actual, "Failed for: " + number);
    } catch (NumberFormatException ex) {
      fail("Number valid for Long.parseLong was rejected: " + number.replace(' ', '_'), ex);
    }
  }

  private static void assertLongThrows(String number) {
    assertThrowsExactly(
        NumberFormatException.class,
        () -> Long.parseLong(number),
        () -> "Number is valid for Long.parseLong: `" + number + "`");
    assertThrowsExactly(
        NumberFormatException.class,
        () -> Numbers.parseLong(number.toCharArray()),
        "Should throw for: " + number);
  }

  private static void assertIntEquals(String number) {
    int expected = Integer.parseInt(number);
    try {
      int actual = Numbers.parseInt(number.toCharArray(), 0, number.length());
      assertEquals(expected, actual, "Failed for: " + number);
    } catch (NumberFormatException ex) {
      fail("Number valid for Integer.parseInt was rejected: " + number.replace(' ', '_'), ex);
    }
  }

  private static void assertIntThrows(String number) {
    assertThrowsExactly(
        NumberFormatException.class,
        () -> Integer.parseInt(number),
        () -> "Number is valid for Integer.parseInt: `" + number + "`");
    assertThrowsExactly(
        NumberFormatException.class,
        () -> Numbers.parseInt(number.toCharArray()),
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
