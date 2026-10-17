package org.hisp.dhis.jsontree;

import java.util.ArrayList;
import java.util.List;

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
    if (path.isRange()) throw new JsonPatchException("Range path not allowed for " + insert.type());
    path.resolve(target)
        .findFirst()
        .map(p -> new JsonNodeOp.Insert(p, insert.value().node()))
        .ifPresent(ops::add);
  }

  private static void spread(JsonMixed target, JsonPatchOp spread, List<JsonNodeOp> ops) {
    JsonPathExpression path = JsonPathExpression.of(spread.$path());
    if (path.isRange()) throw new JsonPatchException("Range path not allowed for " + spread.type());
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
    if (!path.isRange()) {
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
    if (fromExpr.isRange()) throw new JsonPatchException("Range path not allowed for move source");
    if (toExpr.isRange())   throw new JsonPatchException("Range path not allowed for move target");

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
    if (fromExpr.isRange()) throw new JsonPatchException("Range path not allowed for copy source");
    if (toExpr.isRange())   throw new JsonPatchException("Range path not allowed for copy target");

    JsonPath from = fromExpr.resolve(target).findFirst().orElse(null);
    if (from == null || !target.exists(from)) return;
    JsonPath to = toExpr.resolve(target).findFirst().orElse(null);
    if (to == null) return;

    ops.add(new JsonNodeOp.Insert(to, target.get(from).node()));
  }

  private static void swap(JsonMixed target, JsonPatchOp swap, List<JsonNodeOp> ops) {
    JsonPathExpression fromExpr = JsonPathExpression.of(swap.from());
    JsonPathExpression toExpr   = JsonPathExpression.of(swap.to());
    if (fromExpr.isRange()) throw new JsonPatchException("Range path not allowed for swap source");
    if (toExpr.isRange())   throw new JsonPatchException("Range path not allowed for swap target");

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
    if (resolved.isEmpty()) {
      throw new JsonPatchException("Test failed: no match for path " + path);
    }
    for (JsonPath p : resolved) {
      if (!target.exists(p)) {
        throw new JsonPatchException("Test failed: no value at " + p);
      }
    }
  }
}
