package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.InputExpression.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the {@link InputExpression} pattern matching implementation.
 *
 * @author Jan Bernitt
 */
class InputExpressionTest {

  @Test
  void testLiterally() {
    assertMatches("Hello", "Hello");
    assertDoesNotMatch("Hello", "Hello, World");
  }

  @Test
  void testRepeat_DigitsHash() {
    assertMatches("+#", "123");
    assertDoesNotMatch("+#", "1A");
  }

  @Test
  void testRepeat_Sequence() {
    assertMatches("3|ddul|", "12Ab34Bc56Cd");
    assertMatches("3?|ddul|", "12Ab", "12Ab12Ab", "12Ab12Ab12Ab");
    assertDoesNotMatch("3|ddul|", "12ab34Bc56Cd", "12Ab34Bc", "12Ab");
    assertDoesNotMatch("3?|ddul|", "12Ab12Ab12Ab12Ab");
  }

  @Test
  void testRepeat_SequenceBlock() {
    assertMatches("3(|ddul|)", "12Ab34Bc56Cd");
  }

  @Test
  void testRepeat_ZeroTimesMatchesEndOfInput() {
    assertMatches("#?#", "1", "12");
  }

  @Test
  void testScan() {
    assertMatches("He~o", "Hello", "Hero", "Heo");
    assertEquals(new Pattern(Text.of("He~o"), 3, -1), Pattern.of("He~o"));
  }

  @Test
  void testScanIf() {
    assertMatches("He~~lo", "Hello","Helo","Helllo","Heo");
    assertDoesNotMatch("He~~lo", "Hemo", "Hero");
    assertEquals(new Pattern(Text.of("He~~lo"), 3, -1), Pattern.of("He~~lo"));
  }

  @Test
  void testScanIf_URL() {
    String pattern = "/api~~(/+@)(/gist)";
    assertMatches(pattern, "/api/gist", "/api/foo/gist", "/api/foo/bar/gist");
    assertDoesNotMatch(pattern, "/api/foo","/api/gist/foo");
    assertEquals(new Pattern(Text.of(pattern), 9, -1), Pattern.of(pattern));
  }

  @Test
  void testSequenceNumeric() {
    assertMatches("|2050|", "2022");
    assertDoesNotMatch("|2050|", "2061");
    assertMatches("|2059|", "0001","2059");
    assertDoesNotMatch("|2059|", "2060");
  }

  @Test
  void testPattern_Dates() {
    assertMatches("|2050-12-31|", "2020-01-01", "2020-00-00");
    assertMatches("|2050/12/31|", "2020/01/01");
    assertMatches("|2050.12.31|", "2020.01.01");
    assertMatches("|2050|[-./]|12|[-./]|31|", "2020.01.01","2020-01-01", "2020/01/01");

    assertDoesNotMatch("|2050-12-31|", "2020-01-32", "2020-13-01", "2051-01-01");
  }

  @Test
  void testPattern_DatesNumericBound() {
    // YYYY/MM/DD
    String pattern = "{1900-}|2050|/{1-}|12|/{1-}|31|";
    assertMatches(pattern, "2020/01/01");
    assertDoesNotMatch(pattern, "2020/00/00");
    // Weekly with 1-2 week digits
    pattern = "{1900-}|2050|W{1-53}(#?#)";
    assertMatches(pattern, "2020W1", "2020W53");
    assertDoesNotMatch(pattern, "2020W0", "2020W54");
  }

  @Test
  void testPattern_LongNumbers() {
    assertMatches("|9223372036854775807|", "9223372036854775807");
    assertDoesNotMatch("|9223372036854775807|", "9223372036854775808");
    assertMatches("|-9223372036854775807|", "-9223372036854775807");
  }

  @Test
  void testPattern_DHIS2Periods() {
    InputExpression expr = InputExpression.of(
        "{1900-}|2050|",
        "{1900-}|2050|{1-}|12|",
        "{1900-}|2050|-{1-}|12|",
        "{1900-}|2050|{1-}|12|{1-}|31|",
        "{1900-}|2050|-{1-}|12|-{1-}|31|",
        "{1900-}|2050|W{1-53}(#?#)",
        "{1900-}|2050|WedW{1-53}(#?#)",
        "{1900-}|2050|SatW{1-53}(#?#)",
        "{1900-}|2050|SunW{1-53}(#?#)",
        "{1900-}|2050|BiW{1-27}(#?#)",
        "{1900-}|2050|{1-}|06|B",
        "{1900-}|2050|Q{1-}|4|",
        "{1900-}|2050|NovQ{1-}|4|",
        "{1900-}|2050|S{1-}|2|",
        "{1900-}|2050|NovS{1-}|2|",
        "{1900-}|2050|AprilS{1-}|2|",
        "{1900-}|2050|April",
        "{1900-}|2050|July",
        "{1900-}|2050|Sep",
        "{1900-}|2050|Oct",
        "{1900-}|2050|Nov"
    );
    assertMatches(expr, "2022", "198011", "1980-11", "19770528", "1977-05-28");
    assertMatches(expr,"2025W51", "1989W3", "2044Q1", "2033BiW22");
    // pattern issue
    assertDoesNotMatch(expr, "20221", "2022-1", "1980113", "1980-1-13");
    // numeric out of bounds
    assertDoesNotMatch(expr, "2051", "1899", "1980100", "198013", "1980-14", "1977-00-28");
  }

  private static void assertMatches(String pattern, String...inputs) {
    InputExpression expr = InputExpression.of(pattern);
    for (String input : inputs) {
      assertTrue(InputExpression.matches(pattern, input), () -> "%s should match %s".formatted(pattern, input));
      assertNotNull(expr.match(input));
    }
  }

  private static void assertDoesNotMatch(String pattern, String...inputs) {
    InputExpression expr = InputExpression.of(pattern);
    for (String input : inputs) {
      assertFalse(InputExpression.matches(pattern, input), () -> "%s should NOT match %s".formatted(pattern, input));
      assertNull(expr.match(input));
    }
  }

  private static void assertMatches(InputExpression expr, String...inputs) {
    for (String input : inputs) {
      Pattern matching = expr.match(input);
      assertNotNull(matching, () -> "Pattern should match %s".formatted(input));
    }
  }

  private static void assertDoesNotMatch(InputExpression expr, String...inputs) {
    for (String input : inputs) {
      Pattern matching = expr.match(input);
      assertNull(matching, () -> "Pattern should NOT match %s".formatted(input));
    }
  }
}
