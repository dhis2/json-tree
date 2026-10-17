package org.hisp.dhis.jsontree;

import static java.util.stream.Collectors.toMap;
import static org.hisp.dhis.jsontree.JsonBuilder.createArray;

import java.util.List;

/**
 * {@linkplain JsonNodeOp}s are used to make bulk modifications using {@link
 * JsonNode#patch(List)}.
 *
 * <p>{@linkplain JsonNodeOp} is a path based operation that is not yet "bound" to target.
 *
 * <p>The order of operations made into a set does not matter. Any order has the same outcome when
 * applied to the same target.
 *
 * @author Jan Bernitt
 * @since 1.1 (renamed and revised since 1.10)
 */
public sealed interface JsonNodeOp {

  /**
   * @return the target of the operation
   */
  JsonPath path();

  JsonNode value();

  record Insert(JsonPath path, JsonNode value) implements JsonNodeOp {}
  record Replace(JsonPath path, JsonNode value) implements JsonNodeOp {}
  record Spread(JsonPath path, JsonNode value) implements JsonNodeOp {}
  record Remove(JsonPath path) implements JsonNodeOp {
    @Override
    public JsonNode value() {
      return JsonNode.NULL;
    }
  }

  /**
   * The paths an operation claims as an exclusive write region.
   * Two operations clash if a claim of one is an ancestor-or-self of a claim of the other.
   */
  static Iterable<JsonPath> claims(JsonNodeOp op) {
    if (op instanceof Spread spread) {
      if (spread.value.isEmpty()) return List.of();
      if (spread.value.isObject())
        return spread.value.keys().map(key -> new JsonPath(spread.path, key));
    }
    return List.of(op.path());
  }

  /**
   * @param ops set of patch operations
   * @implNote array merge inserts don't need special handling as it is irrelevant how many elements
   *     are inserted at the target index as each operation is independent and uniquely targets an
   *     insert position in the target array in its state before any change
   */
  static void checkPatch(List<JsonNodeOp> ops) {
    for (int i = 0; i < ops.size(); i++) {
      for (int j = i + 1; j < ops.size(); j++) {
        JsonNodeOp a = ops.get(i);
        JsonNodeOp b = ops.get(j);

        for (JsonPath pa : claims(a)) {
          for (JsonPath pb : claims(b)) {
            if (isAncestorOrSelf(pa, pb) || isAncestorOrSelf(pb, pa)) {
              throw JsonPatchException.clash(ops, a, b);
            }
          }
        }
      }
    }
  }

  /** true if {@code a} equals {@code b} or is a strict ancestor of it */
  private static boolean isAncestorOrSelf(JsonPath a, JsonPath b) {
    return b.startsWith(a);   // your JsonPath's existing prefix test
  }

}
