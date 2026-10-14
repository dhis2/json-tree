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
   * All the operations that can occur on low level where patches are applied
   */
  sealed interface EssentialOp extends JsonNodeOp permits Remove, Insert, Replace, Spread {
    /**
     * @return the target of the operation
     */
    JsonPath path();
  }

  /*
  Essential operations
   */

  record Remove(JsonPath path) implements EssentialOp {}
  record Insert(JsonPath path, JsonNode value) implements EssentialOp {}
  record Replace(JsonPath path, JsonNode value) implements EssentialOp {}
  record Spread(JsonPath path, JsonNode value) implements EssentialOp {}

  /*
  De-sugared operations
   */

  record Move(JsonPath from, JsonPath to) implements JsonNodeOp {}
  record Copy(JsonPath from, JsonPath to) implements JsonNodeOp {}
  record Swap(JsonPath one, JsonPath other) implements JsonNodeOp {}

  /**
   * The paths an operation claims as an exclusive write region.
   * Two operations clash if a claim of one is an ancestor-or-self of a claim of the other.
   */
  static Iterable<JsonPath> claims(EssentialOp op) {
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
  static void checkPatch(List<EssentialOp> ops) {
    for (int i = 0; i < ops.size(); i++) {
      for (int j = i + 1; j < ops.size(); j++) {
        EssentialOp a = ops.get(i);
        EssentialOp b = ops.get(j);

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
    if (a.isEmpty()) return true;
    int aDepth = a.length();
    int bDepth = b.length();
    if (aDepth > bDepth) return false; // A is deeper, cannot be ancestor

    JsonPath aCur = a;
    JsonPath bCur = b.drop(bDepth - aDepth); // make same length

    // check tail-to-head in lockstep
    while (aCur != null && bCur != null) {
      if (!segmentIntersects(aCur.segment(), bCur.segment())) return false;
      aCur = aCur.parent();
      bCur = bCur.parent();
    }
    return true;
  }

  private static boolean segmentIntersects(Text a, Text b) {
    if (a == null || b == null) return false;
    int colonA = a.indexOf(':');
    colonA = colonA == 0 ? a.indexOf(':', 1) : -1;
    int colonB = b.indexOf(':');
    colonB = colonB == 0 ? b.indexOf(':', 1) : -1;

    int aFrom = -1;
    int aTo = -1;
    int bFrom = -1;
    int bTo = -1;

    if (colonA > 0) {
      Text from = a.slice(1, colonA);
      Text to = a.slice(colonA+1, a.length());
      if (from.isTextualInteger() && to.isTextualInteger()) {
        aFrom = from.parseInt();
        aTo = to.parseInt();
        if (aFrom == aTo) aTo++;
      }
    } else if (a.isTextualInteger()) {
      aFrom = a.parseInt();
      aTo = aFrom + 1;
    }
    if (colonB > 0) {
      Text from = b.slice(1, colonB);
      Text to = b.slice(colonB+1, b.length());
      if (from.isTextualInteger() && to.isTextualInteger()) {
        bFrom = from.parseInt();
        bTo = to.parseInt();
        if (bFrom == bTo) bTo++;
      }
    } else if (b.isTextualInteger()) {
      bFrom = b.parseInt();
      bTo = bFrom + 1;
    }

    // one of them is not numeric: equals
    if (aFrom == -1 || bFrom == -1) return a.equals(b);
    // otherwise: range overlap check
    return aFrom < bTo && bFrom < aTo;
  }
}
