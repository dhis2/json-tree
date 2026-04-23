package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.hisp.dhis.jsontree.InputExpression.Pattern;
import org.junit.jupiter.api.Test;

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
    assertEquals(new Pattern(0, Text.of("He~o"), 3, -1), Pattern.of(0,"He~o"));
  }

  @Test
  void testScanIf() {
    assertMatches("He~~lo", "Hello","Helo","Helllo","Heo");
    assertDoesNotMatch("He~~lo", "Hemo", "Hero");
    assertEquals(new Pattern(0, Text.of("He~~lo"), 3, -1), Pattern.of(0,"He~~lo"));
  }

  @Test
  void testScanIf_ExampleURL() {
    String pattern = "/api~~(/+@)(/gist)";
    assertMatches(pattern, "/api/gist", "/api/foo/gist", "/api/foo/bar/gist");
    assertDoesNotMatch(pattern, "/api/foo","/api/gist/foo");
    assertEquals(new Pattern(0, Text.of(pattern), 9, -1), Pattern.of(0, pattern));
  }

  @Test
  void testGroup_NestedTail() {
    // Note that while generally groups can not be nested
    // it does work when the nesting is the last unit
    // because the first closing ) is functionally closing all levels
    assertMatches("~(foo+(bar))", "foobar", "foofoobar");
    assertMatches("~(foo+(bar~(baz)))", "foobarbarianbaz", "foofoobarberbaz");
  }

  @Test
  void testSequenceNumeric() {
    assertMatches("|2050|", "2022");
    assertDoesNotMatch("|2050|", "2061");
    assertMatches("|2059|", "0001","2059");
    assertDoesNotMatch("|2059|", "2060");
  }

  @Test
  void testPattern_LongNumbers() {
    assertMatches("|9223372036854775807|", "9223372036854775807");
    assertDoesNotMatch("|9223372036854775807|", "9223372036854775808");
    assertMatches("|-9223372036854775807|", "-9223372036854775807");
  }


  @Test
  void testPattern_ExampleInteger() {
    // different variants of how to match for an integer
    List<String> patterns =
        List.of("?|s|+#", "?[+-]+#", "?[s]+#", "?[+-]#9?#", "?[+-]{2147483647}(#9?#)");
    for (String pattern : patterns)
      assertMatches(pattern, "1", "0", "-2", "00001", "+999", "-2147483647");
  }

  @Test
  void testPattern_ExampleDecimal() {
    InputExpression expr =
        InputExpression.of("?[+-]+#?(.*#)?([E]?[+-]+#)", "?[+-]*#.+#?([E]?[+-]+#)");
    assertMatches(expr, ".5", "4.", "1.1", "+345.234", "-23.56e-12", "+.5e1", "-0.004E-345");
    assertDoesNotMatch(expr, ".", "-.", "0a", "a0", "1ee", "1.E+");
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
  void testPattern_ExampleTime() {
    String hhmm = "|23:59|";
    assertMatches(hhmm, "14:53", "00:00", "23:59", "08:01");
    assertDoesNotMatch(hhmm, "23.59", "2359", "24:00", "11:60");
    String hhmmss = "|23:59:59|";
    assertMatches(hhmmss, "14:12:44", "00:00:00", "23:59:59");
    assertDoesNotMatch(hhmmss, "23.59:00", "235900", "24:00:00", "11:60:00", "00:00:60");
  }

  @Test
  void testPattern_ExampleUUID() {
    // examples given by AI from different generation methods and corner cases
    String[] inputs = {
        "a8098c1a-f86e-11da-bd1a-00112444be1e",
        "6fa459ea-ee8a-3ca4-894e-db77e160355e",
        "886313e1-3b8a-5372-9b90-0c9aee199e5d",
        "019d2555-7874-7e9d-a284-9b45a0b2f165",
        "00000000-0000-0000-0000-000000000000",
        "ffffffff-ffff-ffff-ffff-ffffffffffff",
        "FFFFFFFF-FFFF-FFFF-FFFF-FFFFFFFFFFFF"};
    assertMatches("8[x]-4[x]-4[x]-4[x]-8[x]4[x]", inputs);
    assertMatches("|xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx|", inputs);
    String[] illegalInputs = {
        "gG8098c1a-f86e-11da-bd1a-00112444be1e",
        "6fa459eaee8a3ca4894edb77e160355e",
    };
    assertDoesNotMatch("8[x]-4[x]-4[x]-4[x]-8[x]4[x]", illegalInputs);
    assertDoesNotMatch("|xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx|", illegalInputs);
  }

  @Test
  void testPattern_ExampleIPv4() {
    assertMatches("|255.255.255.255|", "255.255.255.255", "192.168.001.001");
    assertMatches("#2?#.#2?#.#2?#.#2?#","255.255.255.255", "0.0.0.0", "192.168.1.1");
    assertMatches("{255}(#2?#).{255}(#2?#).{255}(#2?#).{255}(#2?#)","255.255.255.255", "0.0.0.0", "192.168.1.1");
    assertDoesNotMatch("{255}(#2?#).{255}(#2?#).{255}(#2?#).{255}(#2?#)", "256.0.0.0");
  }

  @Test
  void testPattern_ExampleMacAddress() {
    // examples given by AI
    String[] inputs = {
      "00:1A:2B:3C:4D:5E",
      "1A:2B:3C:4D:5E:6F",
      "00:80:41:ae:fd:7e",
      "FF:FF:FF:FF:FF:FF",
      "D8:D3:85:EB:12:E3"
    };
    assertMatches("2[x]:2[x]:2[x]:2[x]:2[x]:2[x]", inputs);
    assertMatches("|xx-xx-xx-xx-xx-xx|", "00-14-22-04-25-37");
    String pattern = "2[x][:-]2[x][:-]2[x][:-]2[x][:-]2[x][:-]2[x]";
    assertMatches(pattern, inputs);
    assertMatches(pattern, "00-14-22-04-25-37");
    assertDoesNotMatch(pattern, "G0:1A:2B:3C:4D:5E", "1A2B3C4D5E6F");
  }

  @Test
  void testPattern_ExampleCurrency() {
    String pattern = "$?-+#*(,3#)?(.2#)";
    assertMatches(pattern, "$2", "$0.99", "$1,234.56", "$-4.45", "$-12.34", "$1,234,000.00", "$1,234,000");
    assertDoesNotMatch(pattern, "0.99", "$0.1", "$I5");
  }

  @Test
  void testPattern_ExampleUsZipCode() {
    String pattern = "5#?(-4#)";
    assertMatches(pattern, "12345", "50784", "12345-1234");
    assertDoesNotMatch(pattern,"1234", "123", "12", "1", "1-2345");
  }

  @Test
  void testPattern_ExampleEnum() {
    InputExpression expr =
        InputExpression.of("|OBJECT|", "|ARRAY|", "|STRING|", "|NUMBER|", "|BOOLEAN|", "|NULL|");
    for (JsonNodeType t : JsonNodeType.values()) {
      assertMatches(expr, t.name());
      assertMatches(expr, t.name().toLowerCase());
    }
  }

  @Test
  void testPattern_ExampleDHIS2Periods() {
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
    assertMatches(expr,"2025W51", "1989W3", "2044Q1", "2033BiW22", "2033Nov", "1999SatW44");
    // pattern issue
    assertDoesNotMatch(expr, "20221", "2022-1", "1980113", "1980-1-13", "1980100");
    // numerically out of bounds
    assertDoesNotMatch(expr, true, "2051", "1899", "198013", "1980-14", "1977-00-28");
  }

  private static void assertMatches(String pattern, String...inputs) {
    InputExpression expr = InputExpression.of(pattern);
    assertMatches(expr, inputs);
    // also test via matches directly
    for (String input : inputs) {
      assertTrue(InputExpression.matches(pattern, input), () -> "%s should match %s".formatted(pattern, input));
      assertNotNull(expr.match(input));
    }
  }

  private static void assertDoesNotMatch(String pattern, String...inputs) {
    InputExpression expr = InputExpression.of(pattern);
    assertDoesNotMatch(expr, null, inputs);
    // also test via matches directly
    for (String input : inputs) {
      assertFalse(InputExpression.matches(pattern, input), () -> "%s should NOT match %s".formatted(pattern, input));
      assertNull(expr.match(input));
    }
  }

  private static void assertMatches(InputExpression expr, String...inputs) {
    java.util.regex.Pattern regex = java.util.regex.Pattern.compile(expr.toRegEx());
    for (String input : inputs) {
      Pattern matching = expr.match(input);
      assertNotNull(matching, () -> "Pattern should match %s".formatted(input));
      assertTrue(regex.matcher(input).matches(), () -> "RegEx %s should match input: %s".formatted(regex, input));
    }
  }

  private static void assertDoesNotMatch(InputExpression expr, String...inputs) {
    assertDoesNotMatch(expr, false, inputs);
  }

  private static void assertDoesNotMatch(InputExpression expr, Boolean regexMatches, String...inputs) {
    java.util.regex.Pattern regex = java.util.regex.Pattern.compile(expr.toRegEx());
    for (String input : inputs) {
      Pattern matching = expr.match(input);
      assertNull(matching, () -> "Pattern should NOT match %s".formatted(input));
      if (regexMatches != null)
        assertEquals(
          regexMatches,
          regex.matcher(input).matches(),
          () ->
              "RegEx %s should %s match input: %s"
                  .formatted(regex, regexMatches ? "" : "NOT ", input));
    }
  }
}
