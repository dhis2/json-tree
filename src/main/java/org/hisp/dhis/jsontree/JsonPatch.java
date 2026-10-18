package org.hisp.dhis.jsontree;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link JsonPatch} allows to bulk-edit a {@link JsonValue}s using a set of {@link
 * JsonPatchOp}erations.
 *
 * <p>In contrast to JSON Patch standard described in RFC 6902 this implementation is not iterative.
 * The order of operations does not matter. Instead, all operations are applied in reference to the
 * unmodified target and collisions between the operations are detected based on the modified paths.
 * Any collision aborts the patch. This means all paths always refer to the target value before any
 * change. This also means a patch can be validated without producing any intermediate result. A
 * final result can be created in a single pass with no intermediate representations. This approach
 * has much better performance and is much easier to implement and reason about.
 * <p>
 * For example, if a client wants to delete the first 3 elements in an array they can use
 * <pre>
 *   { "op": "remove", path: ["array", "[0:2]"] }
 *   // or
 *   [
 *     { "op": "remove", path: ["array", "[0]" },
 *     { "op": "remove", path: ["array", "[1]" },
 *     { "op": "remove", path: ["array", "[2]" },
 *   ]
 *   // or any other order of these 3 (or combination of single and range removals)
 * </pre>
 * Whereas in RFC 6902 the remove must have been issued 2,1,0 (not 0,1,2) to not accidentally delete the wrong elements.
 * <p>
 * Note also that {@code path} uses an array of segment expressions so that no escaping is needed.
 * Path segments are always literal unless a special array indexing syntax is used. See {@link JsonPathExpression}
 * for more details on this.
 *
 * @see JsonPathExpression
 *
 * @since 1.10
 */
public interface JsonPatch extends JsonAbstractArray<JsonMixed> {

  enum OpType { test, insert, remove, replace, spread, move, copy, swap }

  interface JsonPatchOp extends JsonObject {

    default OpType op() {
      return getString("op").parsed(OpType::valueOf);
    }

    default JsonMixed $path() {
      return get("path");
    }

    default JsonMixed value() {
      return get("value");
    }

    default JsonMixed from() {
      return get("from");
    }

    default JsonMixed to() {
      return get("to");
    }
  }

  static JsonValue patch(JsonMixed target, JsonPatch patch) {
    if (patch.isEmpty()) return target;
    List<JsonNodeOp> ops = patchOps(target, patch);
    if (ops.isEmpty()) return target;
    return null; // TODO apply
  }

  static List<JsonNodeOp> patchOps(JsonMixed target, JsonPatch patch) {
    List<JsonNodeOp> ops = new ArrayList<>(patch.size());
    for (JsonMixed x : patch) {
      JsonPatchOp op = x.as(JsonPatchOp.class);
      switch (op.op()) {
        case test -> test(target, op);
        case remove -> remove(target, op, ops);
        case insert -> insert(target, op, ops);
        case replace -> replace(target, op, ops);
        case spread -> spread(target, op, ops);
        case move -> move(target, op, ops);
        case copy -> copy(target, op, ops);
        case swap -> swap(target, op, ops);
      }
    }
    return ops;
  }

  private static void insert(JsonMixed target, JsonPatchOp insert, List<JsonNodeOp> ops) {
    JsonPathExpression path = JsonPathExpression.of(insert.$path());
    if (path.kind().isMultiTarget()) throw new JsonPatchException("Range path not allowed for " + insert.type());
    path.resolve(target)
        .findFirst()
        .map(p -> new JsonNodeOp.Insert(p, insert.value().node()))
        .ifPresent(ops::add);
  }

  private static void spread(JsonMixed target, JsonPatchOp spread, List<JsonNodeOp> ops) {
    JsonPathExpression path = JsonPathExpression.of(spread.$path());
    if (path.kind().isMultiTarget()) throw new JsonPatchException("Range path not allowed for " + spread.type());
    path.resolve(target)
        .findFirst()
        .map(p -> new JsonNodeOp.Spread(p, spread.value().node()))
        .ifPresent(ops::add);
  }

  private static void remove(JsonMixed target, JsonPatchOp remove, List<JsonNodeOp> ops) {
    JsonPathExpression.of(remove.$path())
        .resolve(target)
        .filter(target::exists)
        .map(JsonNodeOp.Remove::new)
        .forEach(ops::add);
  }

  private static void replace(JsonMixed target, JsonPatchOp replace, List<JsonNodeOp> ops) {
    JsonPathExpression path = JsonPathExpression.of(replace.$path());
    JsonMixed value = replace.value();
    if (!path.kind().isMultiTarget()) {
      path.resolve(target)
          .findFirst()
          .map(p -> new JsonNodeOp.Replace(p, value.node()))
          .ifPresent(ops::add);
    } else {
      List<JsonPath> replaced = path.resolve(target).toList();
      int n = replaced.size();
      int m = value.isArray() ? value.size() : 1;
      int r = Math.min(n, m);
      for (int i = 0; i < r; i++)
        ops.add(new JsonNodeOp.Replace(replaced.get(i), value.isArray() ? value.get(i).node() : value.node()));
      if (m < n) {
        for (int i = r; i < n; i++)
          ops.add(new JsonNodeOp.Remove(replaced.get(i)));
      } else if (m > n && n > 0) {
        JsonNode spread = JsonBuilder.createArray(arr -> {
          for (int i = r; i < m; i++)
            arr.addElement(value.get(i).node());
        });
        JsonPath last = replaced.get(n - 1);
        JsonPath after = new JsonPath(last.parent(), Text.of(last.segment().parseInt()+1));
        ops.add(new JsonNodeOp.Spread(after, spread));
      }
    }
  }

  private static void move(JsonMixed target, JsonPatchOp move, List<JsonNodeOp> ops) {
    JsonPathExpression fromExpr = JsonPathExpression.of(move.from());
    JsonPathExpression toExpr   = JsonPathExpression.of(move.to());
    if (fromExpr.kind().isMultiTarget()) throw new JsonPatchException("Range path not allowed for move source");
    if (toExpr.kind().isMultiTarget())   throw new JsonPatchException("Range path not allowed for move target");

    JsonPath from = fromExpr.resolve(target).findFirst().orElse(null);
    if (from == null || !target.exists(from)) return;      // missing source → no-op
    JsonPath to = toExpr.resolve(target).findFirst().orElse(null);
    if (to == null) return;

    // snapshot from original
    ops.add(new JsonNodeOp.Remove(from));
    ops.add(new JsonNodeOp.Insert(to, target.get(from).node()));
  }

  private static void copy(JsonMixed target, JsonPatchOp copy, List<JsonNodeOp> ops) {
    JsonPathExpression fromExpr = JsonPathExpression.of(copy.from());
    JsonPathExpression toExpr   = JsonPathExpression.of(copy.to());
    if (fromExpr.kind().isMultiTarget()) throw new JsonPatchException("Range path not allowed for copy source");
    if (toExpr.kind().isMultiTarget())   throw new JsonPatchException("Range path not allowed for copy target");

    JsonPath from = fromExpr.resolve(target).findFirst().orElse(null);
    if (from == null || !target.exists(from)) return;
    JsonPath to = toExpr.resolve(target).findFirst().orElse(null);
    if (to == null) return;

    ops.add(new JsonNodeOp.Insert(to, target.get(from).node()));
  }

  private static void swap(JsonMixed target, JsonPatchOp swap, List<JsonNodeOp> ops) {
    JsonPathExpression fromExpr = JsonPathExpression.of(swap.from());
    JsonPathExpression toExpr   = JsonPathExpression.of(swap.to());
    if (fromExpr.kind().isMultiTarget()) throw new JsonPatchException("Range path not allowed for swap source");
    if (toExpr.kind().isMultiTarget())   throw new JsonPatchException("Range path not allowed for swap target");

    JsonPath from = fromExpr.resolve(target).findFirst().orElse(null);
    JsonPath to   = toExpr.resolve(target).findFirst().orElse(null);
    if (from == null || to == null) return;
    if (!target.exists(from) || !target.exists(to)) return;
    if (from.equals(to)) return;

    JsonNode valueFrom = target.get(from).node();
    JsonNode valueTo   = target.get(to).node();
    ops.add(new JsonNodeOp.Replace(from, valueTo));
    ops.add(new JsonNodeOp.Replace(to, valueFrom));
  }

  private static void test(JsonMixed target, JsonPatchOp test) {
    JsonPathExpression path = JsonPathExpression.of(test.$path());
    List<JsonPath> resolved = path.resolve(target).toList();
    if (resolved.isEmpty())
      throw new JsonPatchException("Test failed: no match for path " + path);
    JsonMixed expected = test.value();
    boolean hasExpected = expected.exists();
    if (hasExpected && resolved.size() > 1)
      throw new JsonPatchException("Test with value does not support range paths: " + path);
    for (JsonPath p : resolved) {
      if (!target.exists(p)) throw new JsonPatchException("Test failed: no value at " + p);
      if (hasExpected && !target.get(p).equivalentTo(expected))
        throw new JsonPatchException(
            "Test failed: value at " + p + " is " + target.get(p) + ", expected " + expected);
    }
  }
}
