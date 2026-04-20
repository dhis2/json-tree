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

import static org.hisp.dhis.jsontree.JsonTreeException.notA;
import static org.hisp.dhis.jsontree.Validation.NodeType.STRING;

import java.util.function.Function;
import org.hisp.dhis.jsontree.internal.CheckNull;
import org.hisp.dhis.jsontree.internal.NotNull;
import org.hisp.dhis.jsontree.internal.TerminalOp;

/**
 * Represents a string JSON node.
 *
 * @author Jan Bernitt
 */
@Validation(type = STRING)
@Validation.Ignore
public interface JsonString extends JsonValue {

  @Override
  default JsonString getValue() {
    return this; // return type override
  }

  /**
   * @return the text of the string node, {@code null} when this property is undefined or defined as
   *     JSON {@code null}.
   * @throws JsonTreeException in case this node exist but does not support {@link JsonNode#textValue()}
   * @since 1.9
   */
  @TerminalOp(canBeUndefined = true)
  default Text text() {
    JsonNode node = nodeIfExists();
    return node == null || node.isNull() ? null : node.textValue();
  }

  /**
   *
   * @return length of the text of this node if it exists, or -1 if it does not exists
   * @throws JsonTreeException in case this node exist but does not support {@link JsonNode#textValue()}
   * @since 1.9
   */
  @TerminalOp(canBeUndefined = true)
  default int length() {
    JsonNode node = nodeIfExists();
    return node == null ? -1 : node.textValue().length();
  }

  /**
   * @return the first character of the text or null if it is empty or undefined
   * @throws JsonTreeException in case this node exist but does not support {@link JsonNode#textValue()}
   * @since 1.9
   */
  @TerminalOp(canBeUndefined = true)
  default Character character() {
    Text str = text();
    return str == null || str.isEmpty() ? null : str.charAt(0);
  }

  @TerminalOp
  default char charValue() {
    JsonNode node = node();
    Text str = node.textValue();
    if (str.isEmpty()) throw notA(JsonNodeType.STRING, node, "charValue()");
    return str.charAt(0);
  }

  /**
   * @return string value of the property or {@code null} when this property is undefined or defined
   *     as JSON {@code null}.
   * @throws JsonTreeException in case this node exist but does not support {@link JsonNode#textValue()}
   */
  @TerminalOp(canBeUndefined = true)
  default String string() {
    return string(null);
  }

  /**
   * @param orDefault used when this node is either undefined or defined as JSON null
   * @return this string node string value or the default if undefined or defined null
   * @throws JsonTreeException in case this node exists but is not a string node (or null)
   */
  @TerminalOp(canBeUndefined = true)
  default String string(String orDefault) {
    return isUndefined() ? orDefault : text().toString();
  }

  /**
   * @param parser function that parses a given {@link String} to the returned type.
   * @param <T> return type
   * @return {@code null} when {@link #string()} returns {@code null} otherwise the result of
   *     calling provided parser with result of {@link #string()}.
   */
  @CheckNull
  @TerminalOp(canBeUndefined = true)
  default <T> T parsed(@NotNull Function<String, T> parser) {
    String value = string();
    return value == null ? null : parser.apply(value);
  }

  interface CheckedFunction<A, B> {

    B apply(A a) throws Exception;
  }

  @TerminalOp(canBeNull = true)
  default <T> T parsedChecked(CheckedFunction<String, T> converter) {
    String value = string();
    if (value == null) return null;
    try {
      return converter.apply(value);
    } catch (Exception ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  @TerminalOp(canBeNull = true)
  default Class<?> parsedClass() {
    return parsedChecked(Class::forName);
  }
}
