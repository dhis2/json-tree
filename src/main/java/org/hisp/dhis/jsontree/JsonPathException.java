/*
 * Copyright (c) 2004-2022, University of Oslo
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

import java.util.NoSuchElementException;

import static java.lang.String.format;

/**
 * Exception thrown when a given path does not exist in the {@link JsonTree}.
 *
 * @author Jan Bernitt
 */
public final class JsonPathException extends NoSuchElementException {

  /**
   * Note that this cannot be of type {@link JsonPath} as only instances with a valid path can be
   * constructed but this exception might precisely be about an invalid path.
   */
  private final transient JsonPath path;

  public JsonPathException(JsonPath path, String message) {
    super(message);
    this.path = path;
  }

  public JsonPath getPath() {
    return path;
  }

  /**
   * When the given path is resolved but the closest parent that does exist
   * was the given simple value.
   */
  static JsonPathException noSuchNode(JsonPath path, JsonNode closest) {
    return new JsonPathException(
        path,
        "Path `%s` does not exist, parent `%s` is already a simple node of type %s."
            .formatted(path, closest.path(), closest.type()));
  }

  /**
   * When an array element is accessed by index but the array index is out of bounds
   */
  static JsonPathException noSuchElement(JsonPath array, int index, int size) {
    JsonPath elementPath = array.chain(index);
    throw new JsonPathException(
        elementPath,
        "Path `%s` does not exist, array `%s` has only `%d` elements."
            .formatted(elementPath, array, size));
  }

  /**
   * When an object member is accessed by key but the object does not have a member with the given
   * name
   */
  static JsonPathException noSuchMember(JsonPath object, Text key) {
    JsonPath memberPath = object.chain(key);
    return new JsonPathException(
        memberPath,
        "Path `%s` does not exist, object `%s` does not have a property `%s`"
            .formatted(memberPath, object, key));
  }
}
