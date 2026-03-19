package org.hisp.dhis.jsontree;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toMap;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Test the {@link Juon} parser accessible via {@link Jurl#of(String)}.
 *
 * @author Jan Bernitt
 */
class JurlTest {

  @Test
  void testTrash() {
    JsonFormatException ex = assertThrowsExactly(JsonFormatException.class, () -> Jurl.of("{}"));
    assertEquals(
        """
      Unexpected character at position 0,
      {}
      ^ expected <value>""",
        ex.getMessage());
  }

  @Test
  void testBoolean() {
    assertJurlEquals(JsonMixed.of("true"), "true");
    assertJurlEquals(JsonMixed.of("false"), "false");
    assertJurlEquals(JsonMixed.of("true"), "t");
    assertJurlEquals(JsonMixed.of("false"), "f");
  }

  @Test
  void testNull() {
    assertJurlEquals(JsonMixed.of("null"), "null");
    assertJurlEquals(JsonMixed.of("null"), "n");
    assertJurlEquals(JsonMixed.of("null"), "");
    assertJurlEquals(JsonMixed.of("null"), " ");
  }

  @Test
  void testNumber() {
    assertJurlEquals(JsonMixed.of("1234"), "1234");
    assertJurlEquals(JsonMixed.of("42.12"), "42.12");
    assertJurlEquals(JsonMixed.of("-0.12"), "-0.12");
    assertJurlEquals(JsonMixed.of("-0.12e-3"), "-0.12e-3");
    assertJurlEquals(JsonMixed.of("0.12e12"), "0.12E12");
  }

  @Test
  void testNumber_OmitLeadingZero() {
    assertJurlEquals(JsonMixed.of("0.12"), ".12");
  }

  @Test
  void testNumber_OmitTailingZero() {
    assertJurlEquals(JsonMixed.of("0.0"), "0.");
  }

  @Test
  void testQuotedString() {
    assertJurlEquals(JsonMixed.of("\"\""), "''");
    assertJurlEquals(JsonMixed.of("\"a\""), "'a'");
    assertJurlEquals(JsonMixed.of("\"hello world\""), "'hello world'");

    JsonFormatException ex = assertThrowsExactly(JsonFormatException.class, () -> Jurl.of("'%'"));
    assertEquals(
        """
      Unexpected character at position 1,
      '%'
       ^ expected <url-safe-character>""",
        ex.getMessage());
  }

  @Test
  void testQuotedString_EscapingMnemonic() {
    String mnemonics =
        "~a=& ~P=& ~e== ~g== ~p=+ ~U=+ ~h=# ~M=# ~c=% ~O=% ~q=' ~Q=' ~d=\" ~L=\" ~~=~ ~b=\\";
    Map<String, String> singles =
        Stream.of(mnemonics.split(" ")).collect(toMap(e -> e.substring(0, 2), e -> e.substring(3)));
    for (Map.Entry<String, String> e : singles.entrySet())
      assertJurlEquals(Json.of(e.getValue()), "'%s'".formatted(e.getKey()));
    assertJurlEquals(Json.of(" "), "'~s'");
    assertJurlEquals(Json.of(" "), "'~J'");
  }

  @Test
  void testQuotedString_EscapingHexadecimal() {
    assertJurlEquals(Json.of(" "), "'~20'");
    assertJurlEquals(Json.of("("), "'~28'");
    assertJurlEquals(Json.of(")"), "'~29'");

    JsonFormatException ex = assertThrowsExactly(JsonFormatException.class, () -> Jurl.of("'~GA'"));
    assertEquals(
        """
      Unexpected character at position 2,
      '~GA'
        ^ expected <hex-digits>""",
        ex.getMessage());

    assertThrowsExactly(JsonFormatException.class, () -> Jurl.of("'~AG'"));
  }

  @Test
  void testUnquotedString() {
    // string that might be misinterpreted as other literals
    assertJurlEquals(JsonMixed.of("\"no\""), "no");
    assertJurlEquals(JsonMixed.of("\"truely\""), "truely");
    assertJurlEquals(JsonMixed.of("\"255.255.255.0\""), "255.255.255.0");
  }

  @Test
  void testUnquotedString_Space() {
    JsonFormatException ex =
        assertThrowsExactly(JsonFormatException.class, () -> Jurl.of("hello world"));
    assertEquals(
        """
      Unexpected character at position 5,
      hello world
           ^ expected <end-of-input>""",
        ex.getMessage());
  }

  @Test
  void testArray() {
    assertJurlEquals(JsonMixed.of("[]"), "()");
    assertJurlEquals(JsonMixed.of("[1]"), "(1)");
    assertJurlEquals(JsonMixed.of("[true]"), "(true)");
    assertJurlEquals(JsonMixed.of("[false]"), "(false)");
    assertJurlEquals(JsonMixed.of("[null]"), "(null)");
    assertJurlEquals(JsonMixed.of("[1,2,3]"), "(1,2,3)");
    assertJurlEquals(JsonMixed.of("[true,false]"), "(true,false)");
    assertJurlEquals(JsonMixed.of("[\"a\",\"b\",\"c\",\"d\"]"), "('a','b','c','d')");
    // dangling comma
    assertJurlEquals(JsonMixed.of("[1]"), "(1,)");
    assertJurlEquals(JsonMixed.of("[1,2]"), "(1,2,)");
    // omitted null
    assertJurlEquals(JsonMixed.of("[]"), "(,)");
    assertJurlEquals(JsonMixed.of("[null,null]"), "(,,)");
    assertJurlEquals(JsonMixed.of("[1]"), "(1,)");
    assertJurlEquals(JsonMixed.of("[null,1]"), "(,1)");
    assertJurlEquals(JsonMixed.of("[null,null,3]"), "(,,3)");
    assertJurlEquals(JsonMixed.of("[1,null,3]"), "(1,,3)");
    assertJurlEquals(JsonMixed.of("[1,null]"), "(1,,)");
    assertJurlEquals(JsonMixed.of("[1,null,0.3,null,5]"), "(1,,.3,,5)");
  }

  @Test
  void testArray_Nested() {
    assertJurlEquals(JsonMixed.of("[[]]"), "(())");
    assertJurlEquals(JsonMixed.of("[[],[]]"), "((),())");
    assertJurlEquals(JsonMixed.of("[[[]]]"), "((()))");
    assertJurlEquals(JsonMixed.of("[[[1,2],[3,4]],[5,6]]"), "(((1,2),(3,4)),(5,6))");
  }

  @Test
  void testObject() {
    assertJurlEquals(JsonMixed.of("{\"a\":1}"), "(a:1)");
    assertJurlEquals(JsonMixed.of("{\"hi\":\"ho\"}"), "(hi:'ho')");
    assertJurlEquals(JsonMixed.of("{\"hi\":\"ho\"}"), "(hi:ho)");
    assertJurlEquals(
        JsonMixed.of("{\"no\":1,\"surprises\":{\"please\":true}}"),
        "(no:1,surprises:(please:true))");

    // dangling comma
    assertJurlEquals(JsonMixed.of("{\"a\":1,\"b\":2}"), "(a:1,b:2,)");

    // omitted nulls
    assertJurlEquals(JsonMixed.of("{\"a\":null}"), "(a:)");
    assertJurlEquals(JsonMixed.of("{\"a\":null,\"b\":null}"), "(a:,b:)");
    assertJurlEquals(JsonMixed.of("{\"a\":null,\"b\":null,\"c\":3}"), "(a:,b:,c:3)");
    assertJurlEquals(JsonMixed.of("{\"a\":1,\"b\":null,\"c\":3}"), "(a:1,b:,c:3)");
    assertJurlEquals(JsonMixed.of("{\"a\":1,\"b\":null,\"c\":null}"), "(a:1,b:,c:)");
    assertJurlEquals(
        JsonMixed.of("{\"a\":1,\"b\":null,\"c\":0.3,\"d\":null,\"e\":5}"), "(a:1,b:,c:.3,d:,e:5)");

    JsonFormatException ex =
        assertThrowsExactly(JsonFormatException.class, () -> Jurl.of("(a:1,,)"));
    assertEquals(
        """
        Unexpected character at position 5,
        (a:1,,)
             ^ expected <member-name>""",
        ex.getMessage());
  }

  @Test
  void testObject_Nested() {
    assertJurlEquals(JsonMixed.of("{\"a\":{\"b\":null}}"), "(a:(b:))");
    assertJurlEquals(JsonMixed.of("{\"a\":{\"b\":null},\"c\":{\"d\":null}}"), "(a:(b:),c:(d:))");
    assertJurlEquals(JsonMixed.of("{\"a\":{\"b\":{\"c\":null}}}"), "(a:(b:(c:)))");
    String json =
        """
        {"a":{"b":{"c":1,"d":2},"e":{"f":3,"g":4}},"h":{"i":5,"k":6}}""";
    assertJurlEquals(JsonMixed.of(json), "(a:(b:(c:1,d:2),e:(f:3,g:4)),h:(i:5,k:6))");
  }

  @Test
  void testExample_ShorthandsOmittedNulls() {
    String expected =
        """
            {"name":"John","age":42,"license":false,"keywords":["hello","world"],"void":null}""";
    assertJurlEquals(
        JsonMixed.of(expected),
        "(name:'John',age:42,license:false,keywords:('hello','world'),void:null)");
  }

  @Test
  void testExample_Full() {
    String expected =
        """
            {"name":"Freddy","age":30,"car":null,"addresses":[{"street":"Elm Street","zip":1428,"city":"Springwood","invoice":true}]}""";
    assertJurlEquals(
        JsonMixed.of(expected),
        "(name:'Freddy',age:30,car:,addresses:((street:'Elm Street',zip:1428,city:'Springwood',invoice:t)))");
  }

  @Test
  void testExample_Dhis2FilterParameter() {
    assertJurlEquals(
        JsonMixed.of(
            """
        {"p":"dataElement","op":"eq","v":"de123456789"}"""),
        "(p:dataElement,op:eq,v:de123456789)");
  }

  @Test
  void testExample_Dhis2FieldsParameter() {
    assertJurlEquals(
        JsonMixed.of(
            """
        ["name","id",{"dataElements":["id","name"]},{"orgUnits":"size"}]"""),
        "(name,id,(dataElements:(id,name)),(orgUnits:size))");
  }

  private static void assertJurlEquals(JsonValue expected, String input) {
    // on the client
    String encoded = URLEncoder.encode(input, UTF_8);
    // on the server
    String decoded = URLDecoder.decode(encoded, UTF_8);
    assertEquals(expected, Jurl.of(decoded));
  }
}
