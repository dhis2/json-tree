package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.CheckNull;
import org.hisp.dhis.jsontree.internal.Language;

import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * A path addressing a location in a JSON document, expressed as an ordered
 * list of segments. Each segment is a string in one of four forms:
 *
 * <pre>
 *   "foo"       object member access (key)
 *   "[5]"       array element access at the given index
 *   "[1:3]"     array range, both ends inclusive
 *   "[?id=foo]" array condition, first element whose property matches
 * </pre>
 *
 * <p>Index and range bounds may be negative, addressing relative to the end
 * of the array in the original document (e.g. {@code [-1]}). Brackets are
 * reserved for array access: a key that would start with {@code [} is not
 * expressible.
 */
public record JsonPathExpression(@CheckNull JsonPathExpression parent, @CheckNull JsonMixed segment) {

  public static final JsonPathExpression ROOT = new JsonPathExpression(null, null);

  public static JsonPathExpression of(@Language("json") String path) {
    return of((CharSequence) path); // purely for language injection
  }

  public static JsonPathExpression of(CharSequence path) {
    return of(JsonMixed.of(path));
  }

  public static JsonPathExpression of(JsonArray path) {
    if (path.isEmpty()) return ROOT;
    JsonPathExpression res = null;
    for (JsonMixed seg : path)
        res = new JsonPathExpression(res, seg);
    return res == null ? ROOT : res;
  }

  public enum Kind { ROOT, KEY, INDEX, ENUMERATION, RANGE, OBJ_CONDITION, ARR_CONDITION;
    public boolean isMultiTarget() {
      return this == RANGE || this == ENUMERATION;
    }
  }

  public Kind kind() {
    if (segment == null) return Kind.ROOT;
    if (segment.isString()) return Kind.KEY;
    if (segment.isInteger()) return Kind.INDEX;
    if (segment.isArray()) {
      int size = segment.size();
      if (size == 1) {
        JsonMixed e1 = segment.get(0);
        if (e1.isArray()) {
          int e1Size = e1.size();
          if (e1Size == 1) return Kind.ARR_CONDITION; // [[...]] condition with an array value
          if (e1Size != 2 || !e1.every(JsonMixed::isInteger))
            throw new JsonPatchException(
                "Nested array is neither a 2-integer range nor a single-element condition; "
                    + "to match array value %s use [\"=\", %s]".formatted(e1, e1));
          return Kind.RANGE;
        }
        if (e1.isObject()) return Kind.OBJ_CONDITION;
        if (e1.isInteger()) return Kind.ENUMERATION;   // enumeration-of-one
        return Kind.ARR_CONDITION; // [true], [null], ["str"]
      }
      if (size == 2) {
        JsonMixed e1 = segment.get(0);
        // ["=", ?]
        if (e1.isString() && e1.text().contentEquals("="))
          return Kind.ARR_CONDITION;
      }
      // multi-element: must be all numbers for a "gap-range"
      if (segment.every(JsonMixed::isInteger)) return Kind.ENUMERATION;
      throw new JsonPatchException("Invalid path segment: mixed types in " + segment);
    }
    throw new JsonPatchException("Invalid path segment: " + segment);
  }

  public boolean isRoot() {
    return parent == null && segment == null;
  }

  @Override
  public String toString() {
    if (isRoot()) return "[]";
    return JsonBuilder.createArray(this::toString).getDeclaration().toString();
  }

  private void toString(JsonBuilder.JsonArrayBuilder arr) {
    if (parent != null) parent.toString(arr);
    if (segment != null) arr.addElement(segment.node());
  }

  public Stream<JsonPath> resolve(JsonMixed target) {
    return switch (kind()) {
      case ROOT -> Stream.of(JsonPath.SELF);
      case RANGE -> resolveRange(target);
      case ENUMERATION -> resolveEnumeration(target);
      default -> {
        JsonPath p = resolveSingle(target);
        yield p == null ? Stream.empty() : Stream.of(p);
      }
    };
  }

  private JsonPath resolveSingle(JsonMixed target) {
    JsonPath p = parent == null ? null : parent.resolveSingle(target);
    if (p == null && parent != null) return null;
    if (segment == null) return null; // just to satisfy nullness analysis
    JsonArray arr = p == null ? target : target.get(p, JsonArray.class);
    Kind kind = kind();
    return switch (kind) {
      case ROOT -> JsonPath.SELF;
      case KEY -> new JsonPath(p, segment.text());
      case INDEX -> {
        if (!arr.isArray())
          throw new JsonPatchException("Array expression `%s` cannot be resolved for target %s".formatted(segment, arr.type()));
        yield new JsonPath(p, Text.of(resolveBound(segment, arr.size())));
      }
      case ARR_CONDITION -> {
        if (!arr.isArray())
          throw new JsonPatchException("Array expression `%s` cannot be resolved for target %s".formatted(segment, arr.type()));
        JsonMixed expected = segment.get(segment.size()-1); // either [x] or ["=", x] => x
        JsonMixed match = arr.first(expected::equivalentTo);
        yield !match.exists() ? null : new JsonPath(p, match.getKey());
      }
      case OBJ_CONDITION -> {
        if (!arr.isArray())
          throw new JsonPatchException("Array expression `%s` cannot be resolved for target %s".formatted(segment, arr.type()));
        JsonMixed cond = segment.get(0);
        JsonMixed match = arr.first(e -> {
          for (Text key : cond.keys()) {
            if (!cond.get(key).equivalentTo(e.get(key))) return false;
          }
          return true;
        });
        yield !match.exists() ? null : new JsonPath(p, match.getKey());
      }
      case RANGE, ENUMERATION ->
          throw new JsonPatchException("%s can only be used at the end of a path, but was: %s".formatted(kind, this));
    };
  }

  private Stream<JsonPath> resolveRange(JsonMixed target) {
    JsonPath p = parent == null ? null : parent.resolveSingle(target);
    if (p == null && parent != null) return Stream.empty();
    if (segment == null) return Stream.empty(); // just to satisfy nullness analysis
    JsonArray arr = p == null ? target : target.get(p, JsonArray.class);
    if (!arr.isArray())
      throw new JsonPatchException("Range `%s` cannot be resolved for target %s".formatted(segment, arr.type()));
    int size = arr.size();
    if (size == 0) return Stream.empty();
    JsonArray range = segment.get(0);
    int from = resolveBound(range.get(0), size);
    int to   = resolveBound(range.get(1), size);
    if (from > to) throw new JsonPatchException("Inverted range `%s`".formatted(segment));
    int start = Math.max(0, from);
    int end   = Math.min(size - 1, to);
    if (start > end) return Stream.empty();
    return IntStream.rangeClosed(start, end).mapToObj(i -> new JsonPath(p, Text.of(i)));
  }

  private Stream<JsonPath> resolveEnumeration(JsonMixed target) {
    JsonPath p = parent == null ? null : parent.resolveSingle(target);
    if (p == null && parent != null) return Stream.empty();
    if (segment == null) return Stream.empty(); // just to satisfy nullness analysis
    JsonArray arr = p == null ? target : target.get(p, JsonArray.class);
    if (!arr.isArray())
      throw new JsonPatchException("Enumeration `%s` cannot be resolved for target %s".formatted(segment, arr.type()));
    int size = arr.size();
    return segment.values().map(e -> new JsonPath(p, Text.of(resolveBound(e, size)))).stream();
  }

  private static int resolveBound(JsonMixed bound, int size) {
    int index = bound.intValue();
    int resolved = bound.text().charAt(0) == '-' ? size + index : index;
    if (resolved < 0) throw new JsonPatchException("Underflow: " + resolved + " (size " + size + ")");
    return resolved;
  }
}
