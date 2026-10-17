package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.CheckNull;
import org.hisp.dhis.jsontree.internal.NotNull;

import java.util.NoSuchElementException;
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
public record JsonPathExpression(@CheckNull JsonPathExpression parent, @NotNull Text segment) {

  public static final JsonPathExpression ROOT = new JsonPathExpression(null, Text.of(""));

  //IDEA: to escape keys with [] we could say that
  // if the segment starts with [] but then has more characters
  // the characters following the initial [] are literally

  public static JsonPathExpression of(JsonArray segments) {
    if (segments.isEmpty()) return ROOT;
    JsonPathExpression res = null;
    for (JsonMixed seg : segments)
      if (!seg.isUndefined() && !(seg.isString() && seg.length() == 0))
        res = new JsonPathExpression(res, seg.text());
    return res == null ? ROOT : res;
  }

  public static JsonPathExpression of(CharSequence... segments) {
    if (segments.length == 0) return ROOT;
    JsonPathExpression res = null;
    for (CharSequence seg : segments)
      if (!seg.isEmpty())
        res = new JsonPathExpression(res, Text.of(seg));
    return res == null ? ROOT : res;
  }

  public boolean isRoot() {
    return parent == null && segment.isEmpty();
  }

  public boolean isArray() {
    int i = segment.lastIndexOf(']');
    return i > 1 && i == segment.length() - 1 && segment.indexOf('[') == 0;
  }

  public boolean isIndex() {
    return isArray() && segment.slice(1, -1).isTextualInteger();
  }

  public boolean isNegativeIndex() {
    return isIndex() && segment.charAt(1) == '-';
  }

  public boolean isRange() {
    if (!isArray()) return false;
    int colon = segment.indexOf(':', 1);
    Text from = segment.slice(1, colon);
    Text to = segment.slice(colon + 1, -1);
    return (from.isEmpty() || from.isTextualInteger()) && (to.isEmpty() || to.isTextualInteger());
  }

  public boolean isNegativeRangeFrom() {
    return isRange() && segment.charAt(1) == '-';
  }

  public boolean isNegativeRangeTo() {
    char c = segment.charAt(segment.indexOf(':', 1) + 1);
    return isRange() && (c == '-' || c == ']');
  }

  public boolean isCondition() {
    if (!isArray()) return false;
    if (segment.length() < 3) return false;
    if (segment.charAt(1) != '?') return false;
    return segment.indexOf('=', 2) >= 0;
  }

  public int index() {
    if (!isArray()) throw new NoSuchElementException("Segment is not an array: "+segment);
    return segment.slice(1, -1).parseInt();
  }

  public int rangeFromIndex() {
    if (!isRange()) throw new NoSuchElementException("Segment is not a range: "+segment);
    Text from = segment.slice(1, segment.indexOf(':', 1));
    return from.isEmpty() ? 0 : from.parseInt();
  }

  public int rangeToIndex() {
    if (!isRange()) throw new NoSuchElementException("Segment is not a range: "+segment);
    Text to = segment.slice(segment.indexOf(':', 1) + 1, -1);
    return to.isEmpty() ? -1 : to.parseInt();
  }

  public Text conditionOn() {
    if (!isCondition()) throw new NoSuchElementException("Segment is not a condition: "+segment);
    return segment.slice(2, segment.indexOf('=', 2));
  }

  public Text conditionValue() {
    if (!isCondition()) throw new NoSuchElementException("Segment is not a condition: "+segment);
    return segment.slice(segment.indexOf('=', 2)+1, -1);
  }

  @Override
  public String toString() {
    if (isRoot()) return "[]";
    return JsonBuilder.createArray(this::toString).getDeclaration().toString();
  }

  private void toString(JsonBuilder.JsonArrayBuilder arr) {
    if (parent != null) parent.toString(arr);
    arr.addString(segment);
  }

  public Stream<JsonPath> resolve(JsonMixed target) {
    if (isRoot()) return Stream.of(JsonPath.SELF);
    if (isRange()) return resolveRange(target);
    JsonPath p = resolveSingle(target);
    return p == null ? Stream.empty() : Stream.of(p);
  }

  private JsonPath resolveSingle(JsonMixed target) {
    JsonPath p = parent == null ? null : parent.resolveSingle(target);
    if (p == null && parent != null) return null;
    if (!isArray()) return new JsonPath(p, segment); // keep key "as is"
    JsonArray arr = p == null ? target : target.get(p, JsonArray.class);
    if (!arr.isArray())
      throw new JsonPatchException("Array expression `%s` cannot be resolved for target %s".formatted(segment, arr.type()));
    if (isIndex()) {
      int i = index();
      if (isNegativeIndex())
        return new JsonPath(p, Text.of(arr.size() + i));
      return new JsonPath(p, Text.of(i));
    }
    if (isCondition()) {
      Text on = conditionOn();
      Text value = conditionValue();
      if (on.isEmpty()) {
        // find simple value in array
        JsonMixed match = arr.first(e -> value.contentEquals(e.text()));
        if (!match.exists()) return null;
        return new JsonPath(p, match.getKey());
      } else {
        // assume array of objects and find object with property value
        JsonMixed match = arr.first(e -> {
          JsonMixed property = e.get(on);
          return property.exists() && value.contentEquals(property.text());
        });
        if (!match.exists()) return null;
        return new JsonPath(p, match.getKey());
      }
    }
    // must be a range
    throw new JsonPatchException("Range can only be used at the end of a path, but was: "+ this);
  }

  private Stream<JsonPath> resolveRange(JsonMixed target) {
    JsonPath p = parent == null ? null : parent.resolveSingle(target);
    if (p == null && parent != null) return Stream.empty();
    JsonArray arr = p == null ? target : target.get(p, JsonArray.class);
    if (!arr.isArray())
      throw new JsonPatchException("Range `%s` cannot be resolved for target %s".formatted(segment, arr.type()));
    int size = arr.size();
    if (size == 0) return Stream.empty();
    int from = resolveBound(rangeFromIndex(), isNegativeRangeFrom(), size);
    int to   = resolveBound(rangeToIndex(),   isNegativeRangeTo(),   size);
    if (from > to) throw new JsonPatchException("Inverted range `%s`".formatted(segment));

    int start = Math.max(0, from);
    int end   = Math.min(size - 1, to);
    if (start > end) return Stream.empty();
    return IntStream.rangeClosed(start, end).mapToObj(i -> new JsonPath(p, Text.of(i)));
  }

  private static int resolveBound(int raw, boolean negative, int size) {
    int resolved = negative ? size + raw : raw;
    if (resolved < 0) throw new JsonPatchException("Underflow: " + resolved + " (size " + size + ")");
    return resolved;
  }
}
