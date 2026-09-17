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

import java.io.PrintStream;
import java.util.function.Consumer;
import org.hisp.dhis.jsontree.JsonBuilder.JsonArrayBuilder;
import org.hisp.dhis.jsontree.JsonBuilder.JsonObjectBuilder;

/**
 * An "append only" {@link JsonBuilder} implementation that can be used with a {@link PrintStream}
 * or a {@link StringBuilder}.
 *
 * @author Jan Bernitt
 */
final class JsonAppender implements JsonBuilder, JsonObjectBuilder, JsonArrayBuilder {

  private final PrettyPrint config;
  private final Appender json;

  private final boolean indent;
  private final String indent1;
  private final String colon;
  private final boolean[] hasChildrenAtLevel = new boolean[128];

  private int level = 0;
  private String indentLevel = "";

  private char[] escapeBuffer;
  private int bufPos = 0;

  JsonAppender(PrettyPrint config, Appender json) {
    this.config = config;
    this.json = json;
    this.indent = config.indentSpaces() > 0 || config.indentTabs() > 0;
    this.indent1 = "\t".repeat(config.indentTabs()) + " ".repeat(config.indentSpaces());
    this.colon = config.spaceAfterColon() ? ": " : ":";
  }

  private void appendDouble(double value) {
    if (JsonBuilder.requiresString(value)) {
      json.append('"');
      json.append(String.valueOf(value));
      json.append('"');
    } else {
      json.append(value);
    }
  }

  private void appendCommaWhenNeeded() {
    if (!hasChildrenAtLevel[level]) {
      hasChildrenAtLevel[level] = true;
    } else {
      json.append(',');
    }
    if (indent) json.append(indentLevel);
  }

  private static boolean needsEscaping(char c) {
    if (c < 0x20) return true;                     // controls — all escape
    if (c < 0x7F) return c == '"' || c == '\\';    // printable ASCII
    return c == 0x2028 || c == 0x2029;             // escape for JS compatibility
  }

  private static boolean needsEscaping(CharSequence str) {
    int len = str.length();
    for (int i = 0; i < len; i++)
      if (needsEscaping(str.charAt(i))) return true;
    return false;
  }

  void appendEscaped(CharSequence str) {
    if (str == null) {
      json.append("null");
      return;
    }
    json.append('"');
    if (!needsEscaping(str)) {
      json.append(str);
    } else {
      // lazy init of the buffer as we might not need it most of the time
      if (escapeBuffer == null)
        escapeBuffer = new char[4096];
      bufPos = 0;
      int len = str.length();
      if (len <= 4096 / 6) {
        // even the worst case fits in the buffer
        // ,so we just append and transfer
        for (int i = 0; i < len; i++)
          bufferEscaped(str.charAt(i));
        json.append(escapeBuffer, 0, bufPos);
      } else {
        // we might go outside the buffer
        // ,so we are checking to not overflow
        for (int i = 0; i < len; i++) {
          bufferEscaped(str.charAt(i));
          if (bufPos >= 4090) {
            json.append(escapeBuffer, 0, bufPos);
            bufPos = 0;
          }
        }
        json.append(escapeBuffer, 0, bufPos);
      }
    }
    json.append('"');
  }

  private void bufferEscaped(char c) {
    switch (c) {
      case '"'    -> { escapeBuffer[bufPos++] = '\\'; escapeBuffer[bufPos++] = '"';  }
      case '\\'   -> { escapeBuffer[bufPos++] = '\\'; escapeBuffer[bufPos++] = '\\'; }
      case '\n'   -> { escapeBuffer[bufPos++] = '\\'; escapeBuffer[bufPos++] = 'n';  }
      case '\r'   -> { escapeBuffer[bufPos++] = '\\'; escapeBuffer[bufPos++] = 'r';  }
      case '\t'   -> { escapeBuffer[bufPos++] = '\\'; escapeBuffer[bufPos++] = 't';  }
      case '\b'   -> { escapeBuffer[bufPos++] = '\\'; escapeBuffer[bufPos++] = 'b';  }
      case '\f'   -> { escapeBuffer[bufPos++] = '\\'; escapeBuffer[bufPos++] = 'f';  }
      case 0x2028, 0x2029 -> bufferUnicodeEscaped(c);
      default -> {
        if (c < 0x20) bufferUnicodeEscaped(c);
        else escapeBuffer[bufPos++] = c;
      }
    }
  }
  private static final char[] HEX = "0123456789ABCDEF".toCharArray();
  private void bufferUnicodeEscaped(char c) {
    escapeBuffer[bufPos++] = '\\';
    escapeBuffer[bufPos++] = 'u';
    escapeBuffer[bufPos++] = HEX[(c >> 12) & 0xF];
    escapeBuffer[bufPos++] = HEX[(c >>  8) & 0xF];
    escapeBuffer[bufPos++] = HEX[(c >>  4) & 0xF];
    escapeBuffer[bufPos++] = HEX[ c        & 0xF];
  }

  private void beginLevel(char c) {
    json.append(c);
    hasChildrenAtLevel[++level] = false;
    indentLevel = "\n" + indent1.repeat(level);
  }

  private void endLevel(char c) {
    level--;
    indentLevel = "\n" + indent1.repeat(level);
    if (indent && hasChildrenAtLevel[level + 1]) json.append(indentLevel);
    json.append(c);
  }

  @Override
  public JsonNode toObject(Consumer<JsonObjectBuilder> obj) {
    visitObject(obj);
    return toNode();
  }

  void visitObject(Consumer<JsonObjectBuilder> obj) {
    beginLevel('{');
    obj.accept(this);
    endLevel('}');
  }

  @Override
  public JsonNode toArray(Consumer<JsonArrayBuilder> arr) {
    visitArray(arr);
    return toNode();
  }

  void visitArray(Consumer<JsonArrayBuilder> arr) {
    beginLevel('[');
    arr.accept(this);
    endLevel(']');
  }

  private JsonNode toNode() {
    CharSequence json = this.json.toString();
    return json == null ? null : JsonNode.of(json);
  }

  /*
   * JsonObjectBuilder
   */

  private void appendMemberName(CharSequence name) {
    appendCommaWhenNeeded();
    json.append('"');
    json.append(name);
    json.append('"');
    json.append(colon);
  }

  private JsonObjectBuilder addRawMember(CharSequence name, CharSequence rawValue) {
    appendMemberName(name);
    json.append(rawValue);
    return this;
  }

  @Override
  public JsonObjectBuilder addMember(CharSequence name, JsonNode value) {
    JsonNodeType type = value.type();
    if (config.excludeNullMembers() && type == JsonNodeType.NULL) return this;
    if (config.retainOriginalDeclaration() || type.isSimple())
      return addRawMember(name, value.getDeclaration());
    return switch (type) {
      case OBJECT -> addObject(name, obj -> value.members().forEach(obj::addMember));
      case ARRAY -> addArray(name, arr -> value.elements().forEach(arr::addElement));
      case NUMBER -> addNumber(name, (Number) value.value());
      case STRING -> addString(name, (Text) value.value());
      case BOOLEAN -> addBoolean(name, (Boolean) value.value());
      case NULL -> addBoolean(name, null);
    };
  }

  @Override
  public JsonObjectBuilder addBoolean(CharSequence name, boolean value) {
    return addRawMember(name, value ? "true" : "false");
  }

  @Override
  public JsonObjectBuilder addBoolean(CharSequence name, Boolean value) {
    if (value == null && config.excludeNullMembers()) return this;
    return addRawMember(name, value == null ? "null" : value ? "true" : "false");
  }

  @Override
  public JsonObjectBuilder addNumber(CharSequence name, int value) {
    appendMemberName(name);
    json.append(value);
    return this;
  }

  @Override
  public JsonObjectBuilder addNumber(CharSequence name, long value) {
    appendMemberName(name);
    json.append(value);
    return this;
  }

  @Override
  public JsonObjectBuilder addNumber(CharSequence name, double value) {
    appendMemberName(name);
    appendDouble(value);
    return this;
  }

  @Override
  public JsonObjectBuilder addNumber(CharSequence name, Number value) {
    if (value == null) return config.excludeNullMembers() ? this : addRawMember(name, "null");
    if (value instanceof Integer) return addNumber(name, value.intValue());
    if (value instanceof Long) return addNumber(name, value.longValue());
    if (value instanceof Textual t) return addRawMember(name, t.textValue());
    if (value instanceof Double || value instanceof Float) return addNumber(name, value.doubleValue());
    return addRawMember(name, value.toString());
  }

  @Override
  public JsonObjectBuilder addString(CharSequence name, CharSequence value) {
    if (value == null && config.excludeNullMembers()) return this;
    if (value == null) return addRawMember(name, "null");
    appendMemberName(name);
    appendEscaped(value);
    return this;
  }

  @Override
  public JsonObjectBuilder addArray(CharSequence name, Consumer<JsonArrayBuilder> value) {
    appendMemberName(name);
    visitArray(value);
    return this;
  }

  @Override
  public JsonObjectBuilder addObject(CharSequence name, Consumer<JsonObjectBuilder> value) {
    appendMemberName(name);
    visitObject(value);
    return this;
  }

  /*
   * JsonArrayBuilder
   */

  private JsonArrayBuilder addRawElement(CharSequence rawValue) {
    appendCommaWhenNeeded();
    json.append(rawValue);
    return this;
  }

  @Override
  public JsonArrayBuilder addElement(JsonNode value) {
    JsonNodeType type = value.type();
    if (config.retainOriginalDeclaration() || type.isSimple())
      return addRawElement(value.getDeclaration());
    return switch (type) {
      case OBJECT -> addObject(obj -> value.members().forEach(obj::addMember));
      case ARRAY -> addArray(arr -> value.elements().forEach(arr::addElement));
      case NUMBER -> addNumber((Number) value.value());
      case STRING -> addString((Text) value.value());
      case BOOLEAN -> addBoolean((Boolean) value.value());
      case NULL -> addRawElement("null");
    };
  }

  @Override
  public JsonArrayBuilder addBoolean(boolean value) {
    return addRawElement(value ? "true" : "false");
  }

  @Override
  public JsonArrayBuilder addBoolean(Boolean value) {
    return addRawElement(value == null ? "null" : value ? "true" : "false");
  }

  @Override
  public JsonArrayBuilder addNumber(int value) {
    appendCommaWhenNeeded();
    json.append(value);
    return this;
  }

  @Override
  public JsonArrayBuilder addNumber(long value) {
    appendCommaWhenNeeded();
    json.append(value);
    return this;
  }

  @Override
  public JsonArrayBuilder addNumber(double value) {
    appendCommaWhenNeeded();
    appendDouble(value);
    return this;
  }

  @Override
  public JsonArrayBuilder addNumber(Number value) {
    if (value == null) return addRawElement("null");
    if (value instanceof Integer) return addNumber(value.intValue());
    if (value instanceof Long) return addNumber(value.longValue());
    if (value instanceof Textual t) return addRawElement(t.textValue());
    if (value instanceof Double || value instanceof Float) return addNumber(value.doubleValue());
    return addRawElement(value.toString());
  }

  @Override
  public JsonArrayBuilder addString(CharSequence value) {
    appendCommaWhenNeeded();
    appendEscaped(value);
    return this;
  }

  @Override
  public JsonArrayBuilder addArray(Consumer<JsonArrayBuilder> value) {
    appendCommaWhenNeeded();
    visitArray(value);
    return this;
  }

  @Override
  public JsonArrayBuilder addObject(Consumer<JsonObjectBuilder> value) {
    appendCommaWhenNeeded();
    visitObject(value);
    return this;
  }
}
