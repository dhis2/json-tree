package org.hisp.dhis.jsontree;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Test the {@link Juon} parser accessible via {@link Jurl#of(String)}.
 *
 * @author Jan Bernitt
 */
class JurlTest {

  @Test
  void testBoolean() {
    assertEquals(JsonMixed.of("true"), Jurl.of("true"));
    assertEquals(JsonMixed.of("false"), Jurl.of("false"));
  }

  @Test
  void testBoolean_Shorthand() {
    assertEquals(JsonMixed.of("true"), Jurl.of("t"));
    assertEquals(JsonMixed.of("false"), Jurl.of("f"));
  }

  @Test
  void testNull() {
    assertEquals(JsonMixed.of("null"), Jurl.of("null"));
  }

  @Test
  void testNull_Shorthand() {
    assertEquals(JsonMixed.of("null"), Jurl.of("n"));
  }

  @Test
  void testNull_Omit() {
    assertEquals(JsonMixed.of("null"), Jurl.of(null));
    assertEquals(JsonMixed.of("null"), Jurl.of(""));
    assertEquals(JsonMixed.of("null"), Jurl.of(" "));
  }

  @Test
  void testNumber() {
    assertEquals(JsonMixed.of("1234"), Jurl.of("1234"));
    assertEquals(JsonMixed.of("42.12"), Jurl.of("42.12"));
    assertEquals(JsonMixed.of("-0.12"), Jurl.of("-0.12"));
    assertEquals(JsonMixed.of("-0.12e-3"), Jurl.of("-0.12e-3"));
    assertEquals(JsonMixed.of("0.12e12"), Jurl.of("0.12E12"));
  }

  @Test
  void testNumber_OmitLeadingZero() {
    assertEquals(JsonMixed.of("0.12"), Jurl.of(".12"));
  }

  @Test
  void testNumber_OmitTailingZero() {
    assertEquals(JsonMixed.of("0.0"), Jurl.of("0."));
  }

  @Test
  void testString() {
    assertEquals(JsonMixed.of("\"\""), Jurl.of("''"));
    assertEquals(JsonMixed.of("\"a\""), Jurl.of("'a'"));
    assertEquals(JsonMixed.of("\"hello world\""), Jurl.of("'hello world'"));
  }

  @Test
  void testArray() {
    assertEquals(JsonMixed.of("[]"), Jurl.of("()"));
    assertEquals(JsonMixed.of("[1]"), Jurl.of("(1)"));
    assertEquals(JsonMixed.of("[true]"), Jurl.of("(true)"));
    assertEquals(JsonMixed.of("[false]"), Jurl.of("(false)"));
    assertEquals(JsonMixed.of("[null]"), Jurl.of("(null)"));
    assertEquals(JsonMixed.of("[1]"), Jurl.of("(1,)"));
    assertEquals(JsonMixed.of("[1,2,3]"), Jurl.of("(1,2,3)"));
    assertEquals(JsonMixed.of("[true,false]"), Jurl.of("(true,false)"));
    assertEquals(JsonMixed.of("[\"a\",\"b\",\"c\",\"d\"]"), Jurl.of("('a','b','c','d')"));
  }

  @Test
  void testArray_Array() {
    assertEquals(JsonMixed.of("[[]]"), Jurl.of("(())"));
    assertEquals(JsonMixed.of("[[],[]]"), Jurl.of("((),())"));
    assertEquals(JsonMixed.of("[[[]]]"), Jurl.of("((()))"));
    assertEquals(JsonMixed.of("[[[1,2],[3,4]],[5,6]]"), Jurl.of("(((1,2),(3,4)),(5,6))"));
  }

  @Test
  void testArray_OmitNulls() {
    assertEquals(JsonMixed.of("[]"), Jurl.of("(,)"));
    assertEquals(JsonMixed.of("[null,null,3]"), Jurl.of("(,,3)"));
    assertEquals(JsonMixed.of("[1,null,3]"), Jurl.of("(1,,3)"));
    assertEquals(JsonMixed.of("[1,null]"), Jurl.of("(1,,)"));
    assertEquals(JsonMixed.of("[1,null,0.3,null,5]"), Jurl.of("(1,,.3,,5)"));
  }

  @Test
  void testObject() {
    assertEquals(JsonMixed.of("{\"hi\":\"ho\"}"), Jurl.of("(hi:'ho')"));
    assertEquals(JsonMixed.of("{\"hi\":\"ho\"}"), Jurl.of("(hi:ho)"));
    assertEquals(
        JsonMixed.of("{\"no\":1,\"surprises\":{\"please\":true}}"),
        Jurl.of("(no:1,surprises:(please:true))"));
  }

  @Test
  @DisplayName("In contrast to JSON, in JURL nulls in objects can be omitted (left empty)")
  void testObject_OmitNulls() {
    assertEquals(JsonMixed.of("{\"a\":null}"), Jurl.of("(a:)"));
    assertEquals(JsonMixed.of("{\"a\":null,\"b\":null}"), Jurl.of("(a:,b:)"));
    assertEquals(JsonMixed.of("{\"a\":null,\"b\":null,\"c\":3}"), Jurl.of("(a:,b:,c:3)"));
    assertEquals(JsonMixed.of("{\"a\":1,\"b\":null,\"c\":3}"), Jurl.of("(a:1,b:,c:3)"));
    assertEquals(JsonMixed.of("{\"a\":1,\"b\":null,\"c\":null}"), Jurl.of("(a:1,b:,c:)"));
    assertEquals(
        JsonMixed.of("{\"a\":1,\"b\":null,\"c\":0.3,\"d\":null,\"e\":5}"),
        Jurl.of("(a:1,b:,c:.3,d:,e:5)"));
  }

  @Test
  void testObject_Object() {
    assertJurlEquals(JsonMixed.of("{\"a\":{\"b\":null}}"), "(a:(b:))");
    assertJurlEquals(JsonMixed.of("{\"a\":{\"b\":null},\"c\":{\"d\":null}}"), "(a:(b:),c:(d:))");
    assertJurlEquals(JsonMixed.of("{\"a\":{\"b\":{\"c\":null}}}"), "(a:(b:(c:)))");
    String json =
        """
        {"a":{"b":{"c":1,"d":2},"e":{"f":3,"g":4}},"h":{"i":5,"k":6}}""";
    assertJurlEquals(JsonMixed.of(json), "(a:(b:(c:1,d:2),e:(f:3,g:4)),h:(i:5,k:6))");
  }

  @Test
  void testMixed_Minimal() {
    String expected =
        """
            {"name":"John","age":42,"license":false,"keywords":["hello","world"],"void":null}""";
    assertJurlEquals(
        JsonMixed.of(expected),
        "(name:'John',age:42,license:false,keywords:('hello','world'),void:null)");
  }

  @Test
  void testMixed_Example() {
    String expected =
        """
            {"name":"Freddy","age":30,"car":null,"addresses":[{"street":"Elm Street","zip":1428,"city":"Springwood","invoice":true}]}""";
    assertJurlEquals(
        JsonMixed.of(expected),
        "(name:'Freddy',age:30,car:,addresses:((street:'Elm Street',zip:1428,city:'Springwood',invoice:t)))");
  }

  private static void assertJurlEquals(JsonMixed expected, String actual) {
    // on the client
    String encoded = URLEncoder.encode(actual, UTF_8);
    // on the server
    String decoded = URLDecoder.decode(encoded, UTF_8);
    assertEquals(expected, Jurl.of(decoded));
  }
}
