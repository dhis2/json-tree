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

  private char[] escBuffer;
  private int escBufPos = 0;

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

  private static final int ESC_BUFFER_SIZE = 4096;
  private static final int ESC_MAX_LEN = 6; // 1:6 after escape (uXXXX)

  void appendEscaped(CharSequence str) {
    if (str == null) {
      json.append("null");
      return;
    }
    json.append('"');

    int len = str.length();
    int i = 0;
    while (i < len && !needsEscaping(str.charAt(i)))
      i++;

    // Fast path: if no character needs escaping, append the input as-is.
    if (i == len) {
      json.append(str);
      json.append('"');
      return;
    }

    // Escape path: buffer is allocated lazily.
    if (escBuffer == null)
      escBuffer = new char[ESC_BUFFER_SIZE];
    escBufPos = 0;

    // append span of clean prefix
    json.append(str, 0, i);

    while (i < len) {
      // 1. append span of escaped chars via buffer
      while (i < len && needsEscaping(str.charAt(i))) {
        if (escBufPos > ESC_BUFFER_SIZE - ESC_MAX_LEN) {
          json.append(escBuffer, 0, escBufPos);
          escBufPos = 0;
        }
        escBufAppendEscaped(str.charAt(i++));
      }
      if (escBufPos > 0) {
        json.append(escBuffer, 0, escBufPos);
        escBufPos = 0;
      }

      // 2. append span of non-escaped chars directly from source
      int start = i;
      while (i < len && !needsEscaping(str.charAt(i)))
        i++;
      json.append(str, start, i );
    }
    json.append('"');
  }

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();
  private void escBufAppendEscaped(char c) {
    switch (c) {
      case '"'    -> { escBuffer[escBufPos++] = '\\'; escBuffer[escBufPos++] = '"';  }
      case '\\'   -> { escBuffer[escBufPos++] = '\\'; escBuffer[escBufPos++] = '\\'; }
      case '\n'   -> { escBuffer[escBufPos++] = '\\'; escBuffer[escBufPos++] = 'n';  }
      case '\r'   -> { escBuffer[escBufPos++] = '\\'; escBuffer[escBufPos++] = 'r';  }
      case '\t'   -> { escBuffer[escBufPos++] = '\\'; escBuffer[escBufPos++] = 't';  }
      case '\b'   -> { escBuffer[escBufPos++] = '\\'; escBuffer[escBufPos++] = 'b';  }
      case '\f'   -> { escBuffer[escBufPos++] = '\\'; escBuffer[escBufPos++] = 'f';  }
      default -> {
        escBuffer[escBufPos++] = '\\';
        escBuffer[escBufPos++] = 'u';
        escBuffer[escBufPos++] = HEX[(c >> 12) & 0xF];
        escBuffer[escBufPos++] = HEX[(c >>  8) & 0xF];
        escBuffer[escBufPos++] = HEX[(c >>  4) & 0xF];
        escBuffer[escBufPos++] = HEX[ c        & 0xF];
      }
    }
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
