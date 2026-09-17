/*
 * Copyright (c) 2004-2021, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.jsontree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.annotation.RetentionPolicy;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.hisp.dhis.jsontree.JsonBuilder.JsonArrayBuilder;
import org.hisp.dhis.jsontree.JsonBuilder.JsonObjectBuilder;
import org.junit.jupiter.api.Test;

/**
 * Tests the {@link JsonAppender} implementation of a {@link JsonBuilder}.
 *
 * @author Jan Bernitt
 */
class JsonAppenderTest {

  @Test
  void testObject_Boolean() {
    assertJson(
        "{'a':true,'b':false,'c':null}",
        JsonBuilder.createObject(
            obj -> obj.addBoolean("a", true).addBoolean("b", false).addBoolean("c", null)));
  }

  @Test
  void testArray_Boolean() {
    assertJson(
        "[true,false,null]",
        JsonBuilder.createArray(arr -> arr.addBoolean(true).addBoolean(false).addBoolean(null)));
  }

  @Test
  void testObject_Int() {
    assertJson("{'int':42}", JsonBuilder.createObject(obj -> obj.addNumber("int", 42)));
  }

  @Test
  void testArray_Int() {
    assertJson("[42]", JsonBuilder.createArray(arr -> arr.addNumber(42)));
  }

  @Test
  void testObject_Double() {
    assertJson("{'double':42.42}", JsonBuilder.createObject(obj -> obj.addNumber("double", 42.42)));
  }

  @Test
  void testArray_Double() {
    assertJson("[42.42]", JsonBuilder.createArray(arr -> arr.addNumber(42.42)));
  }

  @Test
  void testObject_Long() {
    assertJson(
        "{'long':" + Long.MAX_VALUE + "}",
        JsonBuilder.createObject(obj -> obj.addNumber("long", Long.MAX_VALUE)));
  }

  @Test
  void testArray_Long() {
    assertJson(
        "[" + Long.MAX_VALUE + "]", JsonBuilder.createArray(arr -> arr.addNumber(Long.MAX_VALUE)));
  }

  @Test
  void testObject_BigInteger() {
    assertJson(
        "{'bint':42}",
        JsonBuilder.createObject(obj -> obj.addNumber("bint", BigInteger.valueOf(42L))));
  }

  @Test
  void testArray_BigInteger() {
    assertJson("[42]", JsonBuilder.createArray(arr -> arr.addNumber(BigInteger.valueOf(42L))));
  }

  @Test
  void testObject_String() {
    assertJson("{'s':'hello'}", JsonBuilder.createObject(obj -> obj.addString("s", "hello")));
  }

  @Test
  void testObject_StringNull() {
    assertJson("{'s':null}", JsonBuilder.createObject(obj -> obj.addString("s", null)));
  }

  @Test
  void testObject_StringEscapes() {
    assertJson(
        "{'s':'\\\"oh yes\\\"'}",
        JsonBuilder.createObject(obj -> obj.addString("s", "\"oh yes\"")));
  }

  @Test
  void testArray_String() {
    assertJson("['hello']", JsonBuilder.createArray(arr -> arr.addString("hello")));
  }

  @Test
  void testArray_StringEscapes() {
    assertJson(
        "['hello\\\\ world']", JsonBuilder.createArray(arr -> arr.addString("hello\\ world")));
  }

  @Test
  void testObject_IntArray() {
    assertJson(
        "{'array':[1,2]}",
        JsonBuilder.createObject(obj -> obj.addArray("array", arr -> arr.addNumbers(1, 2))));
  }

  @Test
  void testArray_IntArray() {
    assertJson("[[1,2]]", JsonBuilder.createArray(arr -> arr.addArray(a -> a.addNumbers(1, 2))));
  }

  @Test
  void testObject_DoubleArray() {
    assertJson(
        "{'array':[1.5,2.5]}",
        JsonBuilder.createObject(obj -> obj.addArray("array", arr -> arr.addNumbers(1.5d, 2.5d))));
  }

  @Test
  void testArray_DoubleArray() {
    assertJson(
        "[[1.5,2.5]]", JsonBuilder.createArray(arr -> arr.addArray(a -> a.addNumbers(1.5d, 2.5d))));
  }

  @Test
  void testObject_LongArray() {
    assertJson(
        "{'array':[" + Long.MIN_VALUE + "," + Long.MAX_VALUE + "]}",
        JsonBuilder.createObject(
            obj -> obj.addArray("array", arr -> arr.addNumbers(Long.MIN_VALUE, Long.MAX_VALUE))));
  }

  @Test
  void testArray_LongArray() {
    assertJson(
        "[[" + Long.MIN_VALUE + "," + Long.MAX_VALUE + "]]",
        JsonBuilder.createArray(
            arr -> arr.addArray(a -> a.addNumbers(Long.MIN_VALUE, Long.MAX_VALUE))));
  }

  @Test
  void testObject_StringArray() {
    assertJson(
        "{'array':['a','b']}",
        JsonBuilder.createObject(obj -> obj.addArray("array", arr -> arr.addStrings("a", "b"))));
  }

  @Test
  void testArray_StringArray() {
    assertJson(
        "[['a','b']]", JsonBuilder.createArray(arr -> arr.addArray(a -> a.addStrings("a", "b"))));
  }

  @Test
  void testObject_OtherArray() {
    assertJson(
        "{'array':['SOURCE','CLASS','RUNTIME']}",
        JsonBuilder.createObject(
            obj ->
                obj.addArray(
                    "array",
                    arr ->
                        arr.addElements(
                            RetentionPolicy.values(),
                            JsonArrayBuilder::addString,
                            RetentionPolicy::name))));
  }

  @Test
  void testArray_OtherArray() {
    assertJson(
        "[['SOURCE','CLASS','RUNTIME']]",
        JsonBuilder.createArray(
            arr ->
                arr.addArray(
                    a ->
                        a.addElements(
                            RetentionPolicy.values(),
                            JsonArrayBuilder::addString,
                            RetentionPolicy::name))));
  }

  @Test
  void testArray_OtherCollection() {
    assertJson(
        "[['SOURCE','CLASS','RUNTIME']]",
        JsonBuilder.createArray(
            arr ->
                arr.addArray(
                    a ->
                        a.addElements(
                            List.of(RetentionPolicy.values()),
                            JsonArrayBuilder::addString,
                            RetentionPolicy::name))));
  }

  @Test
  void testObject_ObjectBuilder() {
    assertJson(
        "{'obj':{'inner':42}}",
        JsonBuilder.createObject(
            outer -> outer.addObject("obj", obj -> obj.addNumber("inner", 42))));
  }

  @Test
  void testArray_ObjectBuilder() {
    assertJson(
        "[[42,14]]",
        JsonBuilder.createArray(arr -> arr.addArray(arr2 -> arr2.addNumber(42).addNumber(14))));
  }

  @Test
  void testArray_ArrayBuilder() {
    assertJson(
        "[{'inner':42}]",
        JsonBuilder.createArray(arr -> arr.addObject(obj -> obj.addNumber("inner", 42))));
  }

  @Test
  void testObject_ObjectMap() {
    assertJson(
        "{'obj':{'field':42}}",
        JsonBuilder.createObject(
            outer ->
                outer.addObject(
                    "obj",
                    inner ->
                        inner.addMembers(
                            Map.of("field", 42).entrySet(), JsonObjectBuilder::addNumber))));
  }

  @Test
  void testArray_ObjectMap() {
    assertJson(
        "[{'field':42}]",
        JsonBuilder.createArray(
            arr ->
                arr.addObject(
                    obj -> obj.addMembers(Map.of("field", 42), JsonObjectBuilder::addNumber))));
  }

  @Test
  void testObject_MembersMap() {
    assertJson(
        "{'field':42}",
        JsonBuilder.createObject(
            outer ->
                outer.addMembers(Map.of("field", 42).entrySet(), JsonObjectBuilder::addNumber)));
  }

  @Test
  void testArray_ElementsCollection() {
    assertJson(
        "[[42]]",
        JsonBuilder.createArray(
            arr -> arr.addArray(a -> a.addElements(List.of(42), JsonArrayBuilder::addNumber))));
  }

  @Test
  void testObject_JsonNode() {
    assertJson(
        "{'node':['a','b']}",
        JsonBuilder.createObject(obj -> obj.addMember("node", JsonNode.of("[\"a\",\"b\"]"))));
  }

  @Test
  void testObject_JsonNodeNull() {
    assertJson(
        "{'node':null}", JsonBuilder.createObject(obj -> obj.addMember("node", JsonNode.NULL)));
  }

  @Test
  void testArray_JsonNode() {
    assertJson(
        "[['a','b']]",
        JsonBuilder.createArray(arr -> arr.addElement(JsonNode.of("[\"a\",\"b\"]"))));
  }

  @Test
  void testNewlines() {
    // language=JSON
    String json =
        """
            {"id":"woOg1dUFoX0","time":1738685143915,"message":"com.fasterxml.jackson.core.JsonParseException: Unexpected character ('<' (code 60)): expected a valid value (JSON String, Number, Array, Object or token 'null', 'true' or 'false')\\n at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 1]","level":"ERROR"}""";
    JsonObject msg = JsonMixed.of(json);
    String message = msg.getString("message").string();
    assertEquals(
        """
                com.fasterxml.jackson.core.JsonParseException: Unexpected character ('<' (code 60)): expected a valid value (JSON String, Number, Array, Object or token 'null', 'true' or 'false')
                 at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); line: 1, column: 1]""",
        message);
    JsonObject actual =
        Json.object(
            obj ->
                obj.addString("id", "woOg1dUFoX0")
                    .addNumber("time", 1738685143915L)
                    .addString("message", message)
                    .addString("level", "ERROR"));
    assertEquals(json, actual.toJson());
  }

  @Test
  void testObject_StringEscapes_Long() {
    // 3000 double‑quotes → each becomes \" (2 chars) → 6000 escaped chars
    String longString = "\"".repeat(3000);
    assertJson(
        "{'s':'" + "\\\"".repeat(3000) + "'}",
        JsonBuilder.createObject(obj -> obj.addString("s", longString))
    );
  }

  @Test
  void testObject_StringEscapes_LongMixed() {
    // 1000 'a' + 1000 '"' + 1000 'b' → 1000 plain + 2000 escaped + 1000 plain = 4000 escaped chars
    // Still under 4096, but len = 3000 > 682, so the long path is used and the buffer is filled.
    String longString = "a".repeat(1000) + "\"".repeat(1000) + "b".repeat(1000);
    String expectedEscaped = "a".repeat(1000) + "\\\"".repeat(1000) + "b".repeat(1000);
    assertJson(
        "{'s':'" + expectedEscaped + "'}",
        JsonBuilder.createObject(obj -> obj.addString("s", longString))
    );
  }

  @Test
  void testObject_StringEscapes_LongBoundary() {
    // 683 double‑quotes → 1366 escaped chars → still under 4096, but len > 682 so long path is used.
    // This ensures the long path is taken even when the buffer doesn't actually overflow.
    String longString = "\"".repeat(683);
    assertJson(
        "{'s':'" + "\\\"".repeat(683) + "'}",
        JsonBuilder.createObject(obj -> obj.addString("s", longString))
    );
  }

  @Test
  void testArray_StringEscapes_VeryLong() {
    // 5000 double‑quotes → 10000 escaped chars → forces at least two flushes.
    String longString = "\"".repeat(5000);
    assertJson(
        "['" + "\\\"".repeat(5000) + "']",
        JsonBuilder.createArray(arr -> arr.addString(longString))
    );
  }

  @Test
  void testObject_StringEscapes_LongWithEscapes() {
    // 2000 repetitions of: 'a' + '"' + '\n' + 'b' + '\t'
    // Each unit is 5 input chars → 1 + 2 + 2 + 1 + 2 = 8 escaped chars.
    // Total input len = 10000 (> 682) → long path.
    // Total escaped = 16000 (> 4096) → multiple flushes.
    String unit = "a\"\nb\t";
    String longString = unit.repeat(2000);

    String escapedUnit = "a\\\"\\nb\\t";
    String expected = escapedUnit.repeat(2000);

    assertJson(
        "{'s':'" + expected + "'}",
        JsonBuilder.createObject(obj -> obj.addString("s", longString))
    );
  }

  @Test
  void testObject_StringEscapes_LongEscapeAtBoundary() {
    // 4088 plain 'a' chars, then a control char that expands to 6 chars (\u0001),
    // then a few more chars to force the flush to happen after the escape.
    //
    // Plain chars: 4088 → bufPos = 4088
    // \u0001:       6   → bufPos = 4094  (crosses the 4090 threshold)
    // Next iteration triggers flush.
    String longString = "a".repeat(4088) + "\u0001" + "b".repeat(100);
    String expected    = "a".repeat(4088) + "\\u0001" + "b".repeat(100);

    assertJson(
        "{'s':'" + expected + "'}",
        JsonBuilder.createObject(obj -> obj.addString("s", longString))
    );
  }

  @Test
  void testObject_StringEscapes_LongEscapeExactlyAtThreshold() {
    // 4090 plain chars, then \u0001 → bufPos = 4096 exactly, then flush.
    // If the guard were "> 4090" instead of ">= 4090", this would overflow.
    String longString = "a".repeat(4090) + "\u0001" + "c".repeat(50);
    String expected    = "a".repeat(4090) + "\\u0001" + "c".repeat(50);

    assertJson(
        "{'s':'" + expected + "'}",
        JsonBuilder.createObject(obj -> obj.addString("s", longString))
    );
  }

  @Test
  void testObject_StringEscapes_LongConsecutiveEscapes() {
    // Fill to near the boundary, then emit two uXXXX escapes back to back.
    // After the first escape, bufPos = 4094; the guard doesn't fire yet.
    // After the second escape, bufPos would be 4100 — but the guard fires after
    // the first, so the second escape starts at 0.
    String longString = "a".repeat(4089) + "\u0001\u0002" + "d".repeat(100);
    String expected    = "a".repeat(4089) + "\\u0001\\u0002" + "d".repeat(100);

    assertJson(
        "{'s':'" + expected + "'}",
        JsonBuilder.createObject(obj -> obj.addString("s", longString))
    );
  }

  @Test
  void testArray_StringEscapes_LongThenShort() {
    String longString  = ("x\"\n").repeat(1500);   // len=4500, escaped ≈ 6000
    String shortString = "a\"b";

    assertJson(
        "['" + "x\\\"\\n".repeat(1500) + "','a\\\"b']",
        JsonBuilder.createArray(arr -> {
          arr.addString(longString);
          arr.addString(shortString);
        })
    );
  }

  @Test
  void testObject_StringEscapes_Backslash() {
    assertJson(
        "{'s':'\\\\'}",
        JsonBuilder.createObject(obj -> obj.addString("s", "\\"))
    );
  }

  @Test
  void testObject_StringEscapes_CarriageReturn() {
    assertJson(
        "{'s':'\\r'}",
        JsonBuilder.createObject(obj -> obj.addString("s", "\r"))
    );
  }

  @Test
  void testObject_StringEscapes_Tab() {
    assertJson(
        "{'s':'\\t'}",
        JsonBuilder.createObject(obj -> obj.addString("s", "\t"))
    );
  }

  @Test
  void testObject_StringEscapes_Backspace() {
    assertJson(
        "{'s':'\\b'}",
        JsonBuilder.createObject(obj -> obj.addString("s", "\b"))
    );
  }

  @Test
  void testObject_StringEscapes_FormFeed() {
    assertJson(
        "{'s':'\\f'}",
        JsonBuilder.createObject(obj -> obj.addString("s", "\f"))
    );
  }

  @Test
  void testObject_StringEscapes_Nul() {
    assertJson(
        "{'s':'\\u0000'}",
        JsonBuilder.createObject(obj -> obj.addString("s", "\u0000"))
    );
  }

  @Test
  void testObject_StringEscapes_Nul_Raw() {
    String json = JsonBuilder.createObject(obj -> obj.addString("s", "\u0000")).toString();
    assertEquals("{\"s\":\"\\u0000\"}", json);
  }

  @Test
  void testObject_StringEscapes_LineSeparator() {
    // Input is a single U+2028 character
    assertJson(
        "{'s':'\\u2028'}",
        JsonBuilder.createObject(obj -> obj.addString("s", "\u2028"))
    );
  }

  @Test
  void testObject_StringEscapes_ParagraphSeparator() {
    assertJson(
        "{'s':'\\u2029'}",
        JsonBuilder.createObject(obj -> obj.addString("s", "\u2029"))
    );
  }

  @Test
  void testObject_StringEscapes_Soh() {          // 0x01 — low nibble 1
    assertJson(
        "{'s':'\\u0001'}",
        JsonBuilder.createObject(obj -> obj.addString("s", "\u0001"))
    );
  }

  @Test
  void testObject_StringEscapes_Dle() {          // 0x10 — high nibble 1, low nibble 0
    assertJson(
        "{'s':'\\u0010'}",
        JsonBuilder.createObject(obj -> obj.addString("s", "\u0010"))
    );
  }

  @Test
  void testObject_StringEscapes_UnitSeparator() { // 0x1F — high nibble 1, low nibble F
    assertJson(
        "{'s':'\\u001F'}",
        JsonBuilder.createObject(obj -> obj.addString("s", "\u001F"))
    );
  }

  @Test
  void testObject_StringEscapes_SmallRunFillsBuffer() {
    // Large clean prefix ends at 4088; a quote (2-char escape) brings bufPos to 4090;
    // a small clean run of 10 chars pushes bufPos to 4096 and triggers the flush
    // inside the small-run loop.
    String input    = "a".repeat(4088) + "\"" + "b".repeat(10);
    String expected = "a".repeat(4088) + "\\\"" + "b".repeat(10);
    assertJson(
        "{'s':'" + expected + "'}",
        JsonBuilder.createObject(obj -> obj.addString("s", input))
    );
  }

  @Test
  void testObject_StringEscapes_SmallRunAfterFullBuffer() {
    // Regression: 6-char escape leaves bufPos exactly at BUFFER_SIZE;
    // the following small run must not write at index BUFFER_SIZE.
    String input    = "a".repeat(4090) + "\u0001" + "b";
    String expected = "a".repeat(4090) + "\\u0001" + "b";
    assertJson(
        "{'s':'" + expected + "'}",
        JsonBuilder.createObject(obj -> obj.addString("s", input))
    );
  }

  private static void assertJson(String expected, JsonNode actual) {
    assertEquals(expected.replace('\'', '"'), actual.getDeclaration().toString());
  }
}
