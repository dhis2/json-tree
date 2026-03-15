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

import static java.lang.String.format;
import static java.util.Collections.emptyIterator;
import static java.util.Objects.requireNonNull;
import static java.util.Spliterator.NONNULL;
import static java.util.Spliterator.ORDERED;
import static java.util.Spliterator.SIZED;
import static org.hisp.dhis.jsontree.Chars.expectChar;
import static org.hisp.dhis.jsontree.Chars.expectDigit;
import static org.hisp.dhis.jsontree.Chars.expectFalse;
import static org.hisp.dhis.jsontree.Chars.expectMoreChar;
import static org.hisp.dhis.jsontree.Chars.expectNull;
import static org.hisp.dhis.jsontree.Chars.expectTrue;

import java.io.Serial;
import java.io.Serializable;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Spliterator;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.hisp.dhis.jsontree.internal.CheckNull;
import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * {@link JsonTree} is a JSON parser specifically designed as a verifying tool for JSON trees which
 * skips though JSON trees to extract only a specific {@link JsonNode} at a certain path while have
 * subsequent operations benefit from partial parsing work already done in previous calls.
 *
 * <p>Supported paths:
 *
 * <pre>
 * $ = root
 * $.property = field property in root object
 * $.a.b = field b in a in root object
 * $[0] = index 0 of root array
 * $.a[1] index 1 of array in a field in root
 * </pre>
 *
 * <p>This parser follows the JSON standard as defined by <a
 * href="https://www.json.org/">json.org</a>.
 *
 * @author Jan Bernitt
 * @implNote This uses records because JVMs starting with JDK 21/22 will consider record fields as
 *     {@code @Stable} which might help optimize access to the {@link #json} char array.
 */
record JsonTree(
    char @NotNull[] json,
    @NotNull JsonNode.Index auto,
    @NotNull HashMap<JsonPath, JsonNode> nodesByPath,
    @CheckNull JsonNode.GetListener onGet) {

  static JsonNode of(@NotNull CharSequence json, @CheckNull JsonNode.GetListener onGet) {
    if (json instanceof String s) return of(s.toCharArray(), onGet);
    return of(Text.of(json).toCharArray(), onGet);
  }

  static JsonNode of(Path json, Charset encoding, JsonNode.GetListener onGet) {
    return of(Chars.from(json, encoding), onGet);
  }

  /**
   * @param json valid JSON, except it also allows single quoted strings and dangling commas
   * @return a lazy tree of the provided JSON
   * @since 0.11
   */
  static JsonNode ofNonStandard(@NotNull String json) {
    //TODO make a Json5 class with a of() method, remove this
    //TODO make JSON5 inspired where this can be rewritten in place
    // disallow (throw) any JSON5 that does not work
    char[] jsonChars = json.toCharArray();
    adjustToStandard(jsonChars);
    return of(jsonChars, null);
  }

  private static JsonNode of(char[] json, JsonNode.GetListener onGet) {
    JsonTree res = new JsonTree(json, JsonNode.Index.AUTO, new HashMap<>(), onGet);
    JsonNode root = res.autoDetect(JsonPath.SELF, skipWhitespace(res.json, 0));
    res.nodesByPath.put(JsonPath.SELF, root);
    return root;
  }

  /**
   * The main idea of lazy nodes is that at creation only the start index and the path the node
   * represents is known.
   *
   * <p>The "expensive" operations to access the nodes {@link #value()} or find its {@link
   * #endIndex()} are only computed on demand.
   */
  private abstract static class LazyJsonNode implements JsonNode {

    final JsonTree tree;
    final JsonPath path;
    final int start;
    private int end = -1;

    LazyJsonNode(JsonTree tree, JsonPath path, int start) {
      this.tree = tree;
      this.path = path;
      this.start = start;
    }

    @Override
    public final @NotNull JsonNode getRoot() {
      return tree.get(JsonPath.SELF, false);
    }

    @Override
    public final JsonPath getPath() {
      return path;
    }

    @Override
    public final Text getDeclaration() {
      return path.isEmpty() // avoid parse caused by endIndex() if root
          ? Text.of(tree.json, 0, tree.json.length)
          : Text.of(tree.json, start, endIndex() - start);
    }

    @Override
    public boolean exists(@NotNull JsonPath subPath) {
      return tree.exists(path.concat(subPath));
    }

    @Override
    public final int startIndex() {
      return start;
    }

    @Override
    public final int endIndex() {
      if (end < 0) end = skipNodeAutodetect(tree.json, start);
      return end;
    }

    @Override
    public final JsonNode replaceWith(CharSequence json) {
      if (isRoot()) return JsonNode.of(json);
      int endIndex = endIndex();
      StringBuilder newJson = new StringBuilder();
      char[] oldJson = tree.json;
      if (startIndex() > 0) {
        newJson.append(oldJson, 0, startIndex());
      }
      newJson.append(json);
      if (endIndex < oldJson.length) {
        newJson.append(oldJson, endIndex, oldJson.length - endIndex);
      }
      return JsonNode.of(newJson);
    }

    @Override
    public final void visit(JsonNodeType type, Consumer<JsonNode> visitor) {
      if (type == null || type == getType()) {
        visitor.accept(this);
      }
      visitChildren(type, visitor);
    }

    void visitChildren(JsonNodeType type, Consumer<JsonNode> visitor) {
      // by default node has no children
    }

    @Override
    public final Optional<JsonNode> find(@CheckNull JsonNodeType type, Predicate<JsonNode> test) {
      if ((type == null || type == getType()) && test.test(this)) {
        return Optional.of(this);
      }
      return findChildren(type, test);
    }

    Optional<JsonNode> findChildren(JsonNodeType type, Predicate<JsonNode> test) {
      // by default node has no children, no match
      return Optional.empty();
    }

    @Override
    public final String toString() {
      return getDeclaration().toString();
    }

    @Override
    public final int hashCode() {
      return Text.of(tree.json, start, endIndex()).hashCode();
    }

    @Override
    public final boolean equals(Object obj) {
      if (this == obj) return true;
      if (!(obj instanceof LazyJsonNode other)) return false;
      if (tree.json == other.tree.json && start == other.start) return true;
      return getPath().equals(other.getPath())
          && getDeclaration().contentEquals(other.getDeclaration());
    }

    /**
     * This methods necessity is an unfortunate detail of valid JSON which does allow whitespace
     * after the root but no further other characters. Since the {@link #endIndex()} (skipping over
     * the JSON) should be lazy and at least deferred until actual usage an issue with dangling
     * trash can first be checked for when user accesses the {@link #value()}.
     */
    final void checkNoDanglingTrash() {
      Chars.expectEndOfBuffer(tree.json, skipWhitespace(tree.json, endIndex()));
    }

    /*
    Serialisation
    */

    @Serial
    public Object writeReplace() {
      return new SerializedJsonNode(
          tree.json, path.segments().stream().map(Text::toString).toArray(String[]::new));
    }

    record SerializedJsonNode(char[] json, String[] path) implements Serializable {
      @Serial private static final long serialVersionUID = 1L;

      @Serial
      private Object readResolve() {
        return JsonTree.of(json, null).get(JsonPath.of(path));
      }
    }
  }

  private static final class LazyJsonObject extends LazyJsonNode
      implements Streamable<Entry<Text, JsonNode>> {

    private int size = -1;

    LazyJsonObject(JsonTree tree, JsonPath path, int start) {
      super(tree, path, start);
    }

    @Override
    public Iterable<Entry<Text, JsonNode>> value() {
      if (path.isEmpty()) checkNoDanglingTrash();
      return this;
    }

    @NotNull
    @Override
    public Iterator<Entry<Text, JsonNode>> iterator() {
      return membersIterator(Index.AUTO);
    }

    @Override
    public @NotNull Stream<Entry<Text, JsonNode>> stream() {
      return JsonTree.stream(iterator(), size());
    }

    @Override
    public @NotNull JsonNodeType getType() {
      return JsonNodeType.OBJECT;
    }

    @Override
    public JsonNode get(Text name) {
      return member(name);
    }

    @Override
    public JsonNode getOrNull(Text name) throws JsonTreeException {
      return memberOrNull(name);
    }

    @NotNull
    @Override
    public JsonNode get(@NotNull JsonPath subPath) {
      if (subPath.isHead()) return get(subPath.segment());
      return tree.get(path.concat(subPath), false);
    }

    @CheckNull
    @Override
    public JsonNode getOrNull(@NotNull JsonPath subPath) {
      if (subPath.isHead()) return getOrNull(subPath.segment());
      return tree.get(path.concat(subPath), true);
    }

    @Override
    public Streamable<Entry<Text, JsonNode>> members() {
      return this;
    }

    @Override
    public boolean isEmpty() {
      if (size >= 0) return size == 0;
      // avoid computing value with a "cheap" check
      return tree.json[skipWhitespace(tree.json, start + 1)] == '}';
    }

    @Override
    public int size() {
      if (size >= 0) return size;
      size = isEmpty() ? 0 : skipCountObject(tree.json, start);
      return size;
    }

    @Override
    void visitChildren(JsonNodeType type, Consumer<JsonNode> visitor) {
      members().forEach(e -> e.getValue().visit(type, visitor));
    }

    @Override
    Optional<JsonNode> findChildren(JsonNodeType type, Predicate<JsonNode> test) {
      for (Entry<Text, JsonNode> e : members()) {
        Optional<JsonNode> res = e.getValue().find(type, test);
        if (res.isPresent()) {
          return res;
        }
      }
      return Optional.empty();
    }

    @NotNull
    @Override
    public JsonNode member(Text name) throws JsonPathException {
      return requireNonNull(member(name, false));
    }

    @Override
    public JsonNode memberOrNull(Text name) throws JsonPathException {
      return member(name, true);
    }

    private JsonPathException noSuchElement(Text name) {
      JsonPath memberPath = path.chain(name);
      return new JsonPathException(
          memberPath,
          format(
              "Path `%s` does not exist, object `%s` does not have a property `%s`",
              memberPath, path, name));
    }

    private JsonNode member(Text name, boolean orNull) throws JsonPathException {
      JsonPath memberPath = path.chain(name);
      if (tree.onGet != null) tree.onGet.accept(memberPath);
      JsonNode member = tree.nodesByPath.get(memberPath);
      if (member != null) return member;
      char[] json = tree.json;
      int index = skipWhitespace(json, expectChar(json, start, '{'));
      while (index < json.length && json[index] != '}') {
        Text property = Chars.decodeString(json, index);
        index = expectColon(json, skipString(json, index));
        if (name.equals(property)) {
          int mStart = index;
          return tree.nodesByPath.computeIfAbsent(memberPath, key -> tree.autoDetect(key, mStart));
        } else {
          index = skipNodeAutodetect(json, index);
          index = expectCommaOrEnd(json, index, '}');
        }
      }
      if (orNull) return null;
      throw noSuchElement(name);
    }

    @Override
    public Streamable<Entry<Text, JsonNode>> members(Index index) {
      return new StreamableAdapter<>(() -> membersIterator(index), this::size, this::isEmpty);
    }

    private Iterator<Entry<Text, JsonNode>> membersIterator(Index op) {
      if (isEmpty()) return emptyIterator();
      return new Iterator<>() {
        private final char[] json = tree.json;
        private final Index index = op == Index.AUTO ? tree.auto : op;

        private int offset = skipWhitespace(json, expectChar(json, start, '{'));
        private int n = 0;

        @Override
        public boolean hasNext() {
          return offset < json.length && json[offset] != '}';
        }

        @Override
        public Entry<Text, JsonNode> next() {
          if (!hasNext())
            throw new NoSuchElementException("next() called without checking hasNext()");
          Text name = Chars.decodeString(json, offset);
          offset = expectColon(json, skipString(json, offset));
          JsonNode member = tree.autoDetect(path.chain(name), offset, index);
          int end = member.endIndex();
          if (end < offset) {
            // duplicate keys case: skip the duplicate
            offset = expectCommaOrEnd(json, skipNodeAutodetect(json, offset), '}');
            return Map.entry(name, member);
          }
          n++;
          offset = expectCommaOrEnd(json, end, '}');
          if (size < 0 && !hasNext()) size = n;
          return Map.entry(name, member);
        }
      };
    }

    @Override
    public boolean isMember(@NotNull Text name) {
      if (isEmpty()) return false;
      if (tree.nodesByPath.containsKey(path.chain(name))) return true;
      char[] json = tree.json;
      int offset = skipWhitespace(json, expectChar(json, start, '{'));
      while (offset < json.length && json[offset] != '}') {
        Text m = Chars.decodeString(json, offset);
        if (m.equals(name)) return true;
        offset = expectColon(json, skipString(json, offset)); // move after :
        offset = expectCommaOrEnd(json, skipNodeAutodetect(json, offset), '}');
      }
      return false;
    }

    @Override
    public Streamable<JsonPath> paths() {
      return keys(path::chain);
    }

    @Override
    public Streamable<String> names() {
      return keys(Text::toString);
    }

    @Override
    public Streamable<Text> keys() {
      return keys(name -> name);
    }

    private <E> Streamable<E> keys(Function<Text, E> toKey) {
      return new StreamableAdapter<>(() ->
          new Iterator<>() {
            private final char[] json = tree.json;
            private int offset = skipWhitespace(json, expectChar(json, start, '{'));

            @Override
            public boolean hasNext() {
              return offset < json.length && json[offset] != '}';
            }

            @Override
            public E next() {
              if (!hasNext())
                throw new NoSuchElementException("next() called without checking hasNext()");
              Text name = Chars.decodeString(json, offset);
              offset = expectColon(json, skipString(json, offset)); // move after :
              JsonNode member = tree.autoDetect(path.chain(name), offset, Index.ADD);
              // move after value
              offset =
                  member == null || member.endIndex() < offset // (duplicates)
                      ? expectCommaOrEnd(json, skipNodeAutodetect(json, offset), '}')
                      : expectCommaOrEnd(json, member.endIndex(), '}');
              return toKey.apply(name);
            }
          }, this::size, this::isEmpty);
    }
  }

  private static final class LazyJsonArray extends LazyJsonNode implements Streamable<JsonNode> {

    private int size = -1;

    LazyJsonArray(JsonTree tree, JsonPath path, int start) {
      super(tree, path, start);
    }

    @Override
    public Iterable<JsonNode> value() {
      if (path.isEmpty()) checkNoDanglingTrash();
      return this;
    }

    @NotNull
    @Override
    public Iterator<JsonNode> iterator() {
      return elementsIterator(Index.AUTO);
    }

    @Override
    public @NotNull Stream<JsonNode> stream() {
      return JsonTree.stream(iterator(), size());
    }

    @Override
    public @NotNull JsonNodeType getType() {
      return JsonNodeType.ARRAY;
    }

    @Override
    public Streamable<JsonNode> elements() {
      return this;
    }

    @Override
    public JsonNode get(Text name) throws JsonPathException, JsonTreeException {
      if (name.isSignedInteger()) return element(name.parseInt(), name, false);
      return super.get(name);
    }

    @Override
    public JsonNode getOrNull(Text name) throws JsonTreeException {
      if (name.isSignedInteger()) return element(name.parseInt(), name, true);
      return super.getOrNull(name);
    }

    @NotNull
    @Override
    public JsonNode get(@NotNull JsonPath subPath) {
      if (subPath.isHead()) return get(subPath.segment());
      return tree.get(path.concat(subPath), false);
    }

    @Override
    public JsonNode getOrNull(@NotNull JsonPath subPath) throws JsonTreeException {
      if (subPath.isHead()) return getOrNull(subPath.segment());
      return tree.get(path.concat(subPath), true);
    }

    @Override
    public boolean isEmpty() {
      if (size >= 0) return size == 0;
      // avoid computing value with a "cheap" check
      return tree.json[skipWhitespace(tree.json, start + 1)] == ']';
    }

    @Override
    public int size() {
      if (size >= 0) return size;
      size = isEmpty() ? 0 : skipCountArray(tree.json, start);
      return size;
    }

    @Override
    void visitChildren(JsonNodeType type, Consumer<JsonNode> visitor) {
      elements().forEach(node -> node.visit(type, visitor));
    }

    @Override
    Optional<JsonNode> findChildren(JsonNodeType type, Predicate<JsonNode> test) {
      for (JsonNode e : elements()) {
        Optional<JsonNode> res = e.find(type, test);
        if (res.isPresent()) return res;
      }
      return Optional.empty();
    }

    @NotNull
    @Override
    public JsonNode element(int index) {
      return element(index, Text.of(index), false);
    }

    @NotNull
    @Override
    public JsonNode element(Text index) {
      if (!index.isSignedInteger()) return super.get(index);
      return element(index.parseInt(), index, false);
    }

    @Override
    public JsonNode elementOrNull(int index) {
      return element(index, Text.of(index), true);
    }

    @Override
    public JsonNode elementOrNull(Text index) {
      if (!index.isSignedInteger()) return super.get(index);
      return element(index.parseInt(), index, true);
    }

    private JsonPathException outOfBounds(int index, int size) {
      JsonPath elementPath = path.chain(index);
      throw new JsonPathException(
          elementPath,
          "Path `%s` does not exist, array `%s` has only `%d` elements."
              .formatted(elementPath, path, size));
    }

    private JsonNode element(int index, Text segment, boolean orNull) throws JsonPathException {
      if (index < 0) throw outOfBounds(index, size());
      if (size >= 0 && index >= size) {
        if (orNull) return null;
        throw outOfBounds(index, size);
      }
      JsonPath elementPath = path.chain(segment);
      JsonNode e = tree.nodesByPath.get(elementPath);
      if (e != null) return e;
      char[] json = tree.json;
      if (index == 0) {
        int i = skipWhitespace(json, expectChar(json, start, '['));
        if (json[i] == ']') {
          if (orNull) return null;
          throw outOfBounds(0, 0);
        }
        return tree.nodesByPath.computeIfAbsent(elementPath, p -> tree.autoDetect(p, i));
      }
      // maybe the element before it exists? (iteration in a counter loop)
      JsonNode predecessor = tree.nodesByPath.get(path.chain(index - 1));
      if (predecessor != null) {
        int i = skipWhitespace(json, expectCommaOrEnd(json, predecessor.endIndex(), ']'));
        if (json[i] == ']') {
          if (orNull) return null;
          throw outOfBounds(index, index);
        }
        return tree.nodesByPath.computeIfAbsent(elementPath, p -> tree.autoDetect(p, i));
      }
      // go there from the start of the array
      int i = skipWhitespace(json, expectChar(json, start, '['));
      int elementsToSkip = index;
      while (elementsToSkip > 0 && i < json.length && json[i] != ']') {
        i = skipWhitespace(json, i);
        i = skipNodeAutodetect(json, i);
        i = expectCommaOrEnd(json, i, ']');
        elementsToSkip--;
      }
      if (json[i] == ']') {
        if (orNull) return null;
        throw outOfBounds(index, index - elementsToSkip);
      }
      int eStart = i;
      return tree.nodesByPath.computeIfAbsent(elementPath, p -> tree.autoDetect(p, eStart));
    }

    @Override
    public Streamable<JsonNode> elements(Index index) {
      return new StreamableAdapter<>(() -> elementsIterator(index), this::size, this::isEmpty);
    }

    @Override
    public Streamable<Text> values() {
      return new StreamableAdapter<>(this::valuesIterator, this::size, this::isEmpty);
    }

    private Iterator<JsonNode> elementsIterator(Index op) {
      return new Iterator<>() {
        private final char[] json = tree.json;
        private final Index index = op == Index.AUTO ? tree.auto : op;

        private int offset = skipWhitespace(json, expectChar(json, start, '['));
        private int n = 0;

        @Override
        public boolean hasNext() {
          return offset < json.length && json[offset] != ']';
        }

        @Override
        public JsonNode next() {
          if (!hasNext())
            throw new NoSuchElementException("next() called without checking hasNext()");
          JsonNode e = tree.autoDetect(path.chain(n), offset, index);
          n++;
          offset = expectCommaOrEnd(json, e.endIndex(), ']');
          if (size < 0 && !hasNext()) size = n; // remember size now that we know
          return e;
        }
      };
    }

    private Iterator<Text> valuesIterator() {
      return new Iterator<>() {
        private final char[] json = tree.json;

        private int offset = skipWhitespace(json, expectChar(json, start, '['));
        private int n = 0;

        @Override
        public boolean hasNext() {
          return offset < json.length && json[offset] != ']';
        }

        @Override
        public Text next() {
          if (!hasNext())
            throw new NoSuchElementException("next() called without checking hasNext()");
          int end = skipNodeAutodetect(json, offset);
          n++;
          Text res =
              json[end - 1] == '"'
                  ? Text.of(json, offset + 1, end - offset - 2)
                  : Text.of(json, offset, end - offset);
          offset = expectCommaOrEnd(json, end, ']');
          if (size < 0 && !hasNext()) size = n; // remember size now that we know
          return res;
        }
      };
    }
  }

  /**
   * @implNote using {@link Chars}-helpers to parse the value would throw {@link
   *     NumberFormatException} for illegal input but before that would happen the call to {@link
   *     #endIndex()} already makes sure it is a valid JSON number or a {@link JsonFormatException}
   *     is thrown (as required by this API contract).
   * @implNote The primitive overrides could be based on {@link #numberValue()} but that would cause
   *     boxing which we want to avoid.
   */
  private static final class LazyJsonNumber extends LazyJsonNode {

    LazyJsonNumber(JsonTree tree, JsonPath path, int start) {
      super(tree, path, start);
    }

    @Override
    public @NotNull JsonNodeType getType() {
      return JsonNodeType.NUMBER;
    }

    @Override
    public Number numberValue() {
      if (path.isEmpty()) checkNoDanglingTrash();
      double number = Chars.parseDouble(tree.json, start, endIndex() - start);
      if (number % 1 != 0d) return number;
      long n = (long) number;
      if (n < Integer.MAX_VALUE && n > Integer.MIN_VALUE) return (int) n;
      return n;
    }

    @Override
    public int intValue() {
      if (path.isEmpty()) checkNoDanglingTrash();
      int length = endIndex() - start;
      return Chars.isSignedInteger(tree.json, start, length)
          ? Chars.parseInt(tree.json, start, length)
          : (int) Chars.parseDouble(tree.json, start, length);
    }

    @Override
    public long longValue() {
      if (path.isEmpty()) checkNoDanglingTrash();
      int length = endIndex() - start;
      return Chars.isSignedInteger(tree.json, start, length)
          ? Chars.parseLong(tree.json, start, length)
          : (long) Chars.parseDouble(tree.json, start, length);
    }

    @Override
    public double doubleValue() {
      if (path.isEmpty()) checkNoDanglingTrash();
      return Chars.parseDouble(tree.json, start, endIndex() - start);
    }
  }

  private static final class LazyJsonString extends LazyJsonNode {

    LazyJsonString(JsonTree tree, JsonPath path, int start) {
      super(tree, path, start);
    }

    @Override
    public @NotNull JsonNodeType getType() {
      return JsonNodeType.STRING;
    }

    @Override
    public Text textValue() {
      if (path.isEmpty()) checkNoDanglingTrash();
      return Chars.decodeString(tree.json, start);
    }

    @Override
    public boolean booleanValue() {
      return textValue().parseBoolean();
    }

    @Override
    public int intValue() {
      return textValue().parseInt();
    }

    @Override
    public long longValue() {
      return textValue().parseLong();
    }

    @Override
    public double doubleValue() {
      return textValue().parseDouble();
    }
  }

  private static final class LazyJsonBoolean extends LazyJsonNode {

    LazyJsonBoolean(JsonTree tree, JsonPath path, int start) {
      super(tree, path, start);
    }

    @Override
    public @NotNull JsonNodeType getType() {
      return JsonNodeType.BOOLEAN;
    }

    @Override
    public boolean booleanValue() {
      if (path.isEmpty()) checkNoDanglingTrash();
      return endIndex() == start + 4;
    }

    @Override
    public Text textValue() {
      if (path.isEmpty()) checkNoDanglingTrash();
      return getDeclaration();
    }
  }

  private static final class LazyJsonNull extends LazyJsonNode {

    LazyJsonNull(JsonTree tree, JsonPath path, int start) {
      super(tree, path, start);
    }

    @Override
    public @NotNull JsonNodeType getType() {
      return JsonNodeType.NULL;
    }

    @Override
    public Object value() {
      if (path.isEmpty()) checkNoDanglingTrash();
      return null;
    }
  }

  @Override
  public String toString() {
    return new String(json);
  }

  private JsonNode get(JsonPath path, boolean orNull) {
    JsonNode node = nodesByPath.get(path);
    if (node != null) return node;
    // find by finding the closest already indexed parent and navigate down from there...
    JsonNode parent = getClosestIndexedParent(path, nodesByPath);
    int segMax = path.length();
    int segCur = parent.getPath().length();
    while (parent != null && segCur < segMax) { // meaning: are we at the target node? (self)
      checkNodeIsParent(parent, path);
      Text segment = path.segments().get(segCur);
      JsonNodeType type = parent.getType();
      if (type == JsonNodeType.ARRAY) {
        parent = orNull ? parent.elementOrNull(segment) : parent.element(segment);
      } else if (type == JsonNodeType.OBJECT) {
        parent = orNull ? parent.memberOrNull(segment) : parent.member(segment);
      } else {
        throw new JsonPathException(path, format("Malformed path %s at %s.", path, segment));
      }
      segCur++;
    }
    return parent;
  }

  private boolean exists(JsonPath path) {
    if (nodesByPath.containsKey(path)) return true;
    JsonNode node = getClosestIndexedParent(path, nodesByPath);
    int segMax = path.length();
    int segCur = node.getPath().length();
    while (node != null && segCur < segMax) {
      JsonNodeType type = node.getType();
      if (type.isSimple()) return false;
      Text segment = path.segments().get(segCur);
      if (type == JsonNodeType.ARRAY) {
        node = node.elementOrNull(segment);
      } else if (type == JsonNodeType.OBJECT) {
        node = node.memberOrNull(segment);
      }
      segCur++;
    }
    return node != null;
  }

  private JsonNode autoDetect(JsonPath path, int offset, JsonNode.Index index) {
    return switch (index) {
      case SKIP -> autoDetectNoIndexLookup(path, offset);
      case CHECK -> autoDetect(path, offset);
      case AUTO ->
          isPrimitiveNode(json, offset)
              ? autoDetect(path, offset)
              : nodesByPath.computeIfAbsent(path, key -> autoDetectNoIndexLookup(key, offset));
      case ADD -> nodesByPath.computeIfAbsent(path, key -> autoDetectNoIndexLookup(key, offset));
    };
  }

  private JsonNode autoDetect(JsonPath path, int offset) {
    JsonNode node = nodesByPath.get(path);
    return node != null ? node : autoDetectNoIndexLookup(path, offset);
  }

  private JsonNode autoDetectNoIndexLookup(JsonPath path, int offset) {
    if (offset >= json.length) {
      throw new JsonFormatException(json, offset, "a JSON value but found EOI");
    }
    char c = json[offset];
    return switch (c) {
      case '{' -> new LazyJsonObject(this, path, offset);
      case '[' -> new LazyJsonArray(this, path, offset);
      case '"' -> new LazyJsonString(this, path, offset);
      case 't', 'f' -> new LazyJsonBoolean(this, path, offset);
      case 'n' -> new LazyJsonNull(this, path, offset);
      case '-', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' ->
          new LazyJsonNumber(this, path, offset);
      default ->
          throw new JsonFormatException(
              json, offset, "start of a JSON value but found: `" + c + "`");
    };
  }

  private static void checkNodeIsParent(JsonNode parent, JsonPath path) {
    JsonNodeType type = parent.getType();
    if (type.isSimple()) {
      throw new JsonPathException(
          path,
          format(
              "Path `%s` does not exist, parent `%s` is already a simple node of type %s.",
              path, parent.getPath(), type));
    }
  }

  private static JsonNode getClosestIndexedParent(
      JsonPath path, Map<JsonPath, JsonNode> nodesByPath) {
    JsonPath parentPath = path.parentPath();
    JsonNode parent = nodesByPath.get(parentPath);
    if (parent != null) return parent;
    return getClosestIndexedParent(parentPath, nodesByPath);
  }

  /*--------------------------------------------------------------------------
  Parsing support
  -------------------------------------------------------------------------*/

  private static boolean isPrimitiveNode(char[] json, int offset) {
    return offset < json.length && json[offset] != '{' && json[offset] != '[';
  }

  private static int skipNodeAutodetect(char[] json, int offset) {
    expectMoreChar(json, offset);
    return switch (json[offset]) {
      case '{' -> // object node
          skipObject(json, offset);
      case '[' -> // array node
          skipArray(json, offset);
      case '"' -> // string node
          skipString(json, offset); // true => boolean node
      case 't', 'f' -> // false => boolean node
          skipBoolean(json, offset);
      case 'n' -> // null node
          skipNull(json, offset);
      default -> // must be number node then...
          skipNumber(json, offset);
    };
  }

  private static int skipObject(char[] json, int offset) {
    int index = offset;
    index = expectChar(json, index, '{');
    index = skipWhitespace(json, index);
    while (index < json.length && json[index] != '}') {
      index = skipString(json, index);
      index = expectColon(json, index);
      index = skipNodeAutodetect(json, index);
      index = expectCommaOrEnd(json, index, '}');
    }
    return expectChar(json, index, '}');
  }

  private static int skipCountObject(char[] json, int offset) {
    int index = offset;
    index = expectChar(json, index, '{');
    index = skipWhitespace(json, index);
    int c = 0;
    while (index < json.length && json[index] != '}') {
      index = skipString(json, index);
      index = expectColon(json, index);
      index = skipNodeAutodetect(json, index);
      c++;
      index = expectCommaOrEnd(json, index, '}');
    }
    expectChar(json, index, '}');
    return c;
  }

  private static int skipArray(char[] json, int offset) {
    int index = offset;
    index = expectChar(json, index, '[');
    index = skipWhitespace(json, index);
    while (index < json.length && json[index] != ']') {
      index = skipNodeAutodetect(json, index);
      index = expectCommaOrEnd(json, index, ']');
    }
    return expectChar(json, index, ']');
  }

  private static int skipCountArray(char[] json, int offset) {
    int index = offset;
    index = expectChar(json, index, '[');
    index = skipWhitespace(json, index);
    int c = 0;
    while (index < json.length && json[index] != ']') {
      index = skipNodeAutodetect(json, index);
      c++;
      index = expectCommaOrEnd(json, index, ']');
    }
    expectChar(json, index, ']');
    return c;
  }

  private static int expectColon(char[] json, int offset) {
    return skipWhitespace(json, expectChar(json, skipWhitespace(json, offset), ':'));
  }

  private static int expectCommaOrEnd(char[] json, int offset, char end) {
    offset = skipWhitespace(json, offset);
    if (offset < json.length && json[offset] == ',') return skipWhitespace(json, offset + 1);
    if (offset >= json.length || json[offset] != end) return expectChar(json, offset, end); // causes fail
    return offset; // found end, return index pointing to it
  }

  private static int skipBoolean(char[] json, int offset) {
    return json[offset] == 't' ? expectTrue(json, offset) : expectFalse(json, offset);
  }

  private static int skipNull(char[] json, int offset) {
    return expectNull(json, offset);
  }

  private static int skipString(char[] json, int offset) {
    int index = offset;
    index = expectChar(json, index, '"');
    while (index < json.length) {
      char c = json[index++];
      if (c == '"') {
        // found the end (if escaped we would have hopped over)
        return index;
      } else if (c == '\\') {
        Chars.expectEscapableCharacter(json, index);
        // hop over escaped char or unicode
        index += json[index] == 'u' ? 5 : 1;
      } else if (c < ' ') {
        throw new JsonFormatException(
            json,
            index - 1,
            "Control code character is not allowed in JSON string but found: " + (int) c);
      }
    }
    return expectChar(json, index, '"');
  }

  private static int skipNumber(char[] json, int offset) {
    int index = offset;
    index = skipChar(json, index, '-');
    index = expectDigit(json, index);
    index = skipDigits(json, index);
    int before = index;
    index = skipChar(json, index, '.');
    if (index > before) {
      index = expectDigit(json, index);
      index = skipDigits(json, index);
    }
    before = index;
    index = skipChar(json, index, 'e', 'E');
    if (index == before) return index;
    index = skipChar(json, index, '+', '-');
    index = expectDigit(json, index);
    return skipDigits(json, index);
  }

  private static int skipWhitespace(char[] json, int offset) {
    int index = offset;
    while (index < json.length && isWhitespace(json[index])) index++;
    return index;
  }

  private static int skipDigits(char[] json, int offset) {
    int index = offset;
    while (index < json.length && isDigit(json[index])) index++;
    return index;
  }

  /** JSON only considers ASCII digits as digits */
  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  /** JSON only considers ASCII whitespace as whitespace */
  private static boolean isWhitespace(char c) {
    return c == ' ' || c == '\n' || c == '\t' || c == '\r';
  }

  private static int skipChar(char[] json, int offset, char c) {
    return offset < json.length && json[offset] == c ? offset + 1 : offset;
  }

  private static int skipChar(char[] json, int offset, char... anyOf) {
    if (offset >= json.length) {
      return offset;
    }
    for (char c : anyOf) {
      if (json[offset] == c) {
        return offset + 1;
      }
    }
    return offset;
  }

  private static <T> Stream<T> stream(Iterator<T> iterator, int knownExactSize) {
    return StreamSupport.stream(new ItemSpliterator<>(iterator, knownExactSize), false);
  }

  private record StreamableAdapter<T>(
      Supplier<Iterator<T>> newIterator, IntSupplier size, BooleanSupplier isEmpty) implements Streamable<T> {
    @Override
    public @NotNull Iterator<T> iterator() {
      return isEmpty.getAsBoolean() ? Collections.emptyIterator() : newIterator.get();
    }

    @Override
    public @NotNull Stream<T> stream() {
      return isEmpty.getAsBoolean() ? Stream.empty() : JsonTree.stream(iterator(), size.getAsInt());
    }
  }

  private record ItemSpliterator<T>(Iterator<T> iterator, int knownExactSize)
      implements Spliterator<T> {

    @Override
    public void forEachRemaining(Consumer<? super T> action) {
      iterator.forEachRemaining(action);
    }

    @Override
    public boolean tryAdvance(Consumer<? super T> action) {
      if (iterator.hasNext()) {
        action.accept(iterator.next());
        return true;
      }
      return false;
    }

    @Override
    public Spliterator<T> trySplit() {
      return null; // not splitting please and thank you...
    }

    @Override
    public long getExactSizeIfKnown() {
      return knownExactSize;
    }

    @Override
    public long estimateSize() {
      // This is not accounting for being called after some items have been consumed
      // but the JDK Iterator wrappers don't either, so it got to be fine anyhow
      return knownExactSize;
    }

    @Override
    public int characteristics() {
      return ORDERED | SIZED | NONNULL | IMMUTABLE;
    }
  }

  /*
  Adjusting non-standard conform JSON to standard conform JSON.
  The actual parser only accepts standard conform JSON input.
  The lenient parsing is implemented by rewriting the input to be standard conform.
   */

  /**
   * Skips through the input and - switches single quotes of string values and member names to
   * double quotes. - removes dangling commas for arrays and objects
   */
  private static void adjustToStandard(char[] json) {
    adjustNodeAutodetect(json, 0);
  }

  static int adjustNodeAutodetect(char[] json, int atIndex) {
    return switch (json[atIndex]) {
      case '{' -> // object node
          adjustObject(json, atIndex);
      case '[' -> // array node
          adjustArray(json, atIndex);
      case '\'' -> adjustString(json, atIndex);
      case '"' -> // string node
          skipString(json, atIndex); // true => boolean node
      case 't', 'f' -> // false => boolean node
          skipBoolean(json, atIndex);
      case 'n' -> // null node
          skipNull(json, atIndex);
      default -> // must be number node then...
          skipNumber(json, atIndex);
    };
  }

  static int adjustObject(char[] json, int fromIndex) {
    int index = fromIndex;
    index = expectChar(json, index, '{');
    index = skipWhitespace(json, index);
    while (index < json.length && json[index] != '}') {
      index = adjustString(json, index);
      index = expectColon(json, index);
      index = adjustNodeAutodetect(json, index);
      // blank dangling ,
      if (json[index] == ',' && json[index + 1] == '}') json[index++] = ' ';
      index = expectCommaOrEnd(json, index, '}');
    }
    return expectChar(json, index, '}');
  }

  static int adjustArray(char[] json, int fromIndex) {
    int index = fromIndex;
    index = expectChar(json, index, '[');
    index = skipWhitespace(json, index);
    while (index < json.length && json[index] != ']') {
      index = adjustNodeAutodetect(json, index);
      // blank dangling ,
      if (json[index] == ',' && json[index + 1] == ']') json[index++] = ' ';
      index = expectCommaOrEnd(json, index, ']');
    }
    return expectChar(json, index, ']');
  }

  static int adjustString(char[] json, int fromIndex) {
    int index = fromIndex;
    if (json[index] == '"') return skipString(json, fromIndex);
    index = expectChar(json, index, '\'');
    json[index - 1] = '"';
    while (index < json.length && json[index] != '\'') index++;
    index = expectChar(json, index, '\'');
    json[index - 1] = '"';
    return index;
  }
}
