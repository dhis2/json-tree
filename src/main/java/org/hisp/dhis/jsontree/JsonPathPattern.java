package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.CheckNull;
import org.hisp.dhis.jsontree.internal.Language;
import org.hisp.dhis.jsontree.internal.NotNull;

import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * An <b>unresolved</b> path expression. Resolution against a concrete target
 * produces zero or more {@link JsonPath}s — the concrete (post-resolution)
 * form used by {@link JsonNodeOp}s. The distinction matters: {@code JsonPath}
 * segments are always literal keys or non-negative integer indexes, whereas
 * {@code JsonPathPattern} segments encode targeting intent that depends on
 * the target's runtime shape.
 *
 * <p>Segments are encoded by type:
 * <pre>
 *   "foo"          → KEY            literal object member
 *   3              → INDEX          array position (resolved against parent)
 *   -1             → INDEX          negative from-end index
 *   [[1,3]]        → RANGE          inclusive [from, to] array span
 *   [0, 2, 4]      → ENUMERATION    scattered array positions
 *   [{"id": "x"}]  → OBJ_CONDITION  first element whose members match
 *   ["=", 3]       → ARR_CONDITION  first element equal to given value
 *   [true], ["a"]  → ARR_CONDITION  shorthand for ["=", true] / ["=", "a"]
 * </pre>
 *
 * <p>RANGE and ENUMERATION are <b>tail-only</b> — they must be the last segment
 * because they resolve to multiple paths. Conditions may appear anywhere along
 * the path; each resolves to a single index before the next segment is
 * evaluated.
 *
 * <p>Resolution rules:
 * <ul>
 *   <li>Indexes are resolved against the parent array's size; underflow (a
 *       negative index that resolves below 0) throws.</li>
 *   <li>Ranges clamp positive overflow but reject underflow; inverted ranges
 *       throw.</li>
 *   <li>Conditions match the first element satisfying the predicate; no match
 *       yields no path (a no-op for the operation).</li>
 * </ul>
 *
 * <p>Segments can also be addressed by JSON Pointer strings when the pattern is
 * provided via {@link JsonPatch}'s string form; in that case the string is
 * decoded via {@link JsonPointer} first and then reinterpreted here.
 *
 * @see JsonPath  the resolved form
 * @see JsonPointer  the RFC 6901 string form
 * @since 1.10
 * @author Jan Bernitt
 */
public record JsonPathPattern(@NotNull JsonPathPatternType type, @CheckNull JsonPathPattern parent, @CheckNull JsonMixed segment) {

  public static final JsonPathPattern ROOT = new JsonPathPattern(JsonPathPatternType.ROOT,null, null);

  public static JsonPathPattern of(@Language("json") String path) {
    return of((CharSequence) path); // purely for language injection
  }

  public static JsonPathPattern of(CharSequence path) {
    return of(JsonMixed.of(path));
  }

  public static JsonPathPattern of(JsonArray path) {
    if (path.isEmpty()) return ROOT;
    JsonPathPattern res = null;
    for (JsonMixed seg : path)
        res = new JsonPathPattern(segmentType(res, seg), res, seg);
    return res == null ? ROOT : res;
  }

  public JsonPathPattern {
    if (parent != null && parent.type.isMultiTarget())
      throw JsonPathPatternException.chainFailedMultiTarget(parent, segment);
    if (parent != null && parent.type == JsonPathPatternType.ROOT)
      throw JsonPathPatternException.chainFailedRoot(parent, segment);
  }

  public enum JsonPathPatternType {
    ROOT,
    KEY,
    INDEX,
    ENUMERATION,
    RANGE,
    OBJ_CONDITION,
    ARR_CONDITION;

    public boolean isMultiTarget() {
      return this == RANGE || this == ENUMERATION;
    }
  }

  private static JsonPathPatternType segmentType(@CheckNull JsonPathPattern parent, @CheckNull JsonMixed segment) {
    if (segment == null) return JsonPathPatternType.ROOT;
    if (segment.isString()) return JsonPathPatternType.KEY;
    if (segment.isInteger()) return JsonPathPatternType.INDEX;
    if (segment.isArray()) {
      int size = segment.size();
      if (size == 1) {
        JsonMixed e1 = segment.get(0);
        if (e1.isArray()) {
          int e1Size = e1.size();
          if (e1Size == 1) return JsonPathPatternType.ARR_CONDITION; // [[...]] condition with an array value
          if (e1Size != 2 || !e1.every(JsonMixed::isInteger))
            throw JsonPathPatternException.chainFailedArray(parent, segment);
          return JsonPathPatternType.RANGE;
        }
        if (e1.isObject()) return JsonPathPatternType.OBJ_CONDITION;
        if (e1.isInteger()) return JsonPathPatternType.ENUMERATION;   // enumeration-of-one
        return JsonPathPatternType.ARR_CONDITION; // [true], [null], ["str"]
      }
      if (size == 2) {
        JsonMixed e1 = segment.get(0);
        // ["=", ?]
        if (e1.isString() && e1.text().contentEquals("="))
          return JsonPathPatternType.ARR_CONDITION;
      }
      // multi-element: must be all numbers for a "gap-range"
      if (segment.every(JsonMixed::isInteger)) return JsonPathPatternType.ENUMERATION;
      throw JsonPathPatternException.chainFailedArray(parent, segment);
    }
    throw JsonPathPatternException.chainFailed(parent, segment);
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
    return switch (type()) {
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
    return switch (type()) {
      case KEY -> new JsonPath(p, segment.text());
      case INDEX -> {
        checkIsArrayTarget(arr);
        yield new JsonPath(p, Text.of(resolveIndexBound(segment, arr.size())));
      }
      case ARR_CONDITION -> {
        checkIsArrayTarget(arr);
        JsonMixed expected = segment.get(segment.size()-1); // either [x] or ["=", x] => x
        JsonMixed match = arr.first(expected::equivalentTo);
        yield !match.exists() ? null : new JsonPath(p, match.getKey());
      }
      case OBJ_CONDITION -> {
        checkIsArrayTarget(arr);
        JsonMixed cond = segment.get(0);
        JsonMixed match = arr.first(e -> {
          for (Text key : cond.keys()) {
            if (!cond.get(key).equivalentTo(e.get(key))) return false;
          }
          return true;
        });
        yield !match.exists() ? null : new JsonPath(p, match.getKey());
      }
      // below cases should be impossible to get here as this is checked in constructor,
      // but to be safe
      case ROOT ->
          throw JsonPathPatternException.chainFailedRoot(parent, segment);
      case RANGE, ENUMERATION ->
          throw JsonPathPatternException.chainFailedMultiTarget(parent, segment);
    };
  }


  private Stream<JsonPath> resolveRange(JsonMixed target) {
    JsonPath p = parent == null ? null : parent.resolveSingle(target);
    if (p == null && parent != null) return Stream.empty();
    if (segment == null) return Stream.empty(); // just to satisfy nullness analysis
    JsonArray arr = p == null ? target : target.get(p, JsonArray.class);
    checkIsArrayTarget(arr);
    int size = arr.size();
    if (size == 0) return Stream.empty();
    JsonArray range = segment.get(0);
    int from = resolveIndexBound(range.get(0), size);
    int to   = resolveIndexBound(range.get(1), size);
    if (from > to) throw JsonPathPatternException.resolveFailedRangeInverted(this, size);
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
    checkIsArrayTarget(arr);
    int size = arr.size();
    return segment.values().map(e -> new JsonPath(p, Text.of(resolveIndexBound(e, size)))).stream();
  }

  private int resolveIndexBound(JsonMixed bound, int size) {
    int index = bound.intValue();
    int resolved = bound.text().charAt(0) == '-' ? size + index : index;
    if (resolved < 0) throw JsonPathPatternException.resolveFailedIndexUnderflow(this, size, resolved);
    return resolved;
  }

  private void checkIsArrayTarget(JsonArray target) {
    if (!target.isArray())
      throw JsonPathPatternException.resolveFailedNoArray(this, target);
  }
}
