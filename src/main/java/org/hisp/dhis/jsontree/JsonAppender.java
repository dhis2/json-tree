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
import java.util.function.Supplier;
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
  private final Appender out;

  private final boolean indent;
  private final String indent1;
  private final String colon;
  private final boolean[] hasChildrenAtLevel = new boolean[128];

  private int level = 0;
  private String indentLevel = "";

  JsonAppender(PrettyPrint config, Appender out) {
    this.config = config;
    this.out = out;
    this.indent = config.indentSpaces() > 0 || config.indentTabs() > 0;
    this.indent1 = "\t".repeat(config.indentTabs()) + " ".repeat(config.indentSpaces());
    this.colon = config.spaceAfterColon() ? ": " : ":";
  }

  private void append(char c) {
    out.append(c);
  }

  private void append(CharSequence str) {
    out.append(str);
  }

  private void appendCommaWhenNeeded() {
    if (!hasChildrenAtLevel[level]) {
      hasChildrenAtLevel[level] = true;
    } else {
      append(',');
    }
    if (indent) append(indentLevel);
  }

  void appendEscaped(CharSequence str) {
    if (str == null) {
      append("null");
      return;
    }
    append('"');
    str.chars().forEachOrdered(this::appendEscaped);
    append('"');
  }

  private void appendEscaped(int c) {
    switch (c) {
      case '\b' -> append("\\b");
      case '\f' -> append("\\f");
      case '\n' -> append("\\n");
      case '\r' -> append("\\r");
      case '\t' -> append("\\t");
      case '"' -> append("\\\"");
      case '\\' -> append("\\\\");
      case -31 -> append("\\u%04X".formatted(c));
      case 0x2028 -> append("\\u2028");
      case 0x2029 -> append("\\u2029");
      default -> append((char) c);
    }
  }

  private void beginLevel(char c) {
    append(c);
    hasChildrenAtLevel[++level] = false;
    indentLevel = "\n" + indent1.repeat(level);
  }

  private void endLevel(char c) {
    level--;
    indentLevel = "\n" + indent1.repeat(level);
    if (indent && hasChildrenAtLevel[level + 1]) append(indentLevel);
    append(c);
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
    CharSequence json = out.toString();
    return json == null ? null : JsonNode.of(json);
  }

  /*
   * JsonObjectBuilder
   */

  private JsonObjectBuilder addRawMember(CharSequence name, CharSequence rawValue) {
    appendCommaWhenNeeded();
    append('"');
    append(name);
    append('"');
    append(colon);
    append(rawValue);
    return this;
  }

  private JsonObjectBuilder addRawMember(CharSequence name, CharSequence rawValue, boolean quoted) {
    appendCommaWhenNeeded();
    append('"');
    append(name);
    append('"');
    append(colon);
    if (quoted) append('"');
    append(rawValue);
    if (quoted) append('"');
    return this;
  }

  @Override
  public JsonObjectBuilder addMember(CharSequence name, JsonNode value) {
    JsonNodeType type = value.getType();
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
    return addRawMember(name, Text.of(value));
  }

  @Override
  public JsonObjectBuilder addNumber(CharSequence name, long value) {
    return addRawMember(name, String.valueOf(value));
  }

  @Override
  public JsonObjectBuilder addNumber(CharSequence name, double value) {
    // TODO avoid String if possible
    return addRawMember(name, String.valueOf(value), JsonBuilder.requiresString(value));
  }

  @Override
  public JsonObjectBuilder addNumber(CharSequence name, Number value) {
    if (value == null && config.excludeNullMembers()) return this;
    return value == null
        ? addRawMember(name, "null")
        : addRawMember(name, value.toString(), JsonBuilder.requiresString(value));
  }

  @Override
  public JsonObjectBuilder addString(CharSequence name, CharSequence value) {
    if (value == null && config.excludeNullMembers()) return this;
    if (value == null) return addRawMember(name, "null");
    appendCommaWhenNeeded();
    append('"');
    append(name);
    append('"');
    append(colon);
    appendEscaped(value);
    return this;
  }

  @Override
  public JsonObjectBuilder addArray(CharSequence name, Consumer<JsonArrayBuilder> value) {
    appendCommaWhenNeeded();
    append('"');
    append(name);
    append('"');
    append(colon);
    visitArray(value);
    return this;
  }

  @Override
  public JsonObjectBuilder addObject(CharSequence name, Consumer<JsonObjectBuilder> value) {
    appendCommaWhenNeeded();
    append('"');
    append(name);
    append('"');
    append(colon);
    visitObject(value);
    return this;
  }

  /*
   * JsonArrayBuilder
   */

  private JsonArrayBuilder addRawElement(CharSequence rawValue) {
    appendCommaWhenNeeded();
    append(rawValue);
    return this;
  }

  private JsonArrayBuilder addRawElement(CharSequence rawValue, boolean quoted) {
    appendCommaWhenNeeded();
    if (quoted) append('"');
    append(rawValue);
    if (quoted) append('"');
    return this;
  }

  @Override
  public JsonArrayBuilder addElement(JsonNode value) {
    JsonNodeType type = value.getType();
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
    return addRawElement(Text.of(value));
  }

  @Override
  public JsonArrayBuilder addNumber(long value) {
    return addRawElement(String.valueOf(value));
  }

  @Override
  public JsonArrayBuilder addNumber(double value) {
    return addRawElement(String.valueOf(value), JsonBuilder.requiresString(value));
  }

  @Override
  public JsonArrayBuilder addNumber(Number value) {
    return value == null
        ? addRawElement("null")
        : addRawElement(value.toString(), JsonBuilder.requiresString(value));
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
