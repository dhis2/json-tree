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

import static org.hisp.dhis.jsontree.Validation.NodeType.NUMBER;

import org.hisp.dhis.jsontree.internal.NotNull;
import org.hisp.dhis.jsontree.internal.TerminalOp;

/**
 * Represents a numeric JSON node.
 *
 * @author Jan Bernitt
 */
@Validation(type = NUMBER)
@Validation.Ignore
public interface JsonNumber extends JsonPrimitive {

  /**
   * @return numeric value of the property or {@code null} when this property is undefined or
   *     defined as JSON {@code null}.
   * @throws JsonTreeException in case this node is not a number node (or null)
   */
  @TerminalOp(canBeUndefined = true)
  default Number number() {
    JsonNode node = nodeIfExists();
    return node == null || node.getType() == JsonNodeType.NULL ? null : node.numberValue();
  }

  /**
   * @return numeric value as integer (potentially dropping any fractions) or {@code null} if this
   *     property is undefined or defined as JSON {@code null}.
   * @throws JsonTreeException in case this node is not a number node (or null)
   * @since 0.10
   */
  @TerminalOp(canBeUndefined = true)
  default Integer integer() {
    return isUndefined() ? null : intValue();
  }

  /**
   * @param orDefault value to use if this node is undefined or defined null
   * @return the value of this number node as the type of the provided default
   * @throws JsonTreeException in case this node is not a number node (or null)
   * @throws ClassCastException in case the value is not of the default type
   */
  @SuppressWarnings("unchecked")
  @TerminalOp(canBeUndefined = true)
  default <T extends Number> T number(@NotNull T orDefault) {
    if (isUndefined()) return orDefault;
    return (T) orDefault.getClass().cast(number());
  }

  /**
   * @return this number node's value as an int
   * @throws JsonPathException in case this node does not exist
   * @throws JsonTreeException in case this node is not a number node (including JSON null)
   * @see #intValue(int)
   */
  @TerminalOp
  default int intValue() {
    return node().intValue();
  }

  /**
   * @param orDefault when the node in undefined (including defined as null)
   * @return this number node's value as an integer or the given default if it is undefined
   * @throws JsonTreeException in case this node is not a number node
   */
  @TerminalOp(canBeUndefined = true)
  default int intValue(int orDefault) {
    return isUndefined() ? orDefault : intValue();
  }

  /**
   * @return this number node's value as a long
   * @throws JsonPathException in case this node does not exist
   * @throws JsonTreeException in case this node is not a number node (including JSON null)
   */
  @TerminalOp
  default long longValue() {
    return node().longValue();
  }

  /**
   * @return this number node's value as a float
   * @throws JsonPathException in case this node does not exist
   * @throws JsonTreeException in case this node is not a number node (including JSON null)
   */
  @TerminalOp
  default float floatValue() {
    return (float) node().doubleValue();
  }

  /**
   * @return this number node's value as a double
   * @throws JsonPathException in case this node does not exist
   * @throws JsonTreeException in case this node is not a number node (including JSON null)
   */
  @TerminalOp
  default double doubleValue() {
    return node().doubleValue();
  }
}
