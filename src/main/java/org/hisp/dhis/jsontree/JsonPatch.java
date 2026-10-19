package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchDeclarationException;
import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchTargetException;
import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchTestException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

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
 *   { "op": "remove", path: ["array", [[0,2]]] }
 *   // or
 *   [
 *     { "op": "remove", path: ["array", 0] },
 *     { "op": "remove", path: ["array", 1] },
 *     { "op": "remove", path: ["array", 2] },
 *   ]
 *   // or any other order of these 3 (or combination of single and range removals)
 * </pre>
 * Whereas in RFC 6902 the remove must have been issued 2,1,0 (not 0,1,2) to not accidentally delete the wrong elements.
 * <p>
 * Note also that {@code path} uses an array of segment expressions so that no escaping is needed.
 * Path segments are always literal unless a special array indexing syntax is used. See {@link JsonPathPattern}
 * for more details on this.
 *
 * @see JsonPathPattern
 *
 * @since 1.10
 */
public interface JsonPatch extends JsonAbstractArray<JsonMixed> {

  enum OpType { test, insert, remove, replace, spread, move, copy, swap }

  interface JsonPatchOp extends JsonObject {

    default JsonString op() {
      return getString("op");
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
      JsonMixed to = get("to");
      return to.exists() ? to : get("path");
    }
  }

  static JsonMixed patch(JsonMixed target, JsonPatch patch) {
    if (!target.exists()) throw new JsonPatchTargetException("Patch target does not exist");
    if (patch.isEmpty()) return target;
    List<JsonNodeOp> ops = patchOps(target, patch);
    if (ops.isEmpty()) return target;
    return target.node().patch(ops).lift(target.getAccessors());
  }

  static List<JsonNodeOp> patchOps(JsonMixed target, JsonPatch patch) {
    List<JsonNodeOp> ops = new ArrayList<>(patch.size());
    for (JsonMixed x : patch) {
      JsonPatchOp op = x.as(JsonPatchOp.class);
      switch (readOp(op)) {
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
    JsonMixed value = readValue(insert, "insert `value`", true);
    resolvePaths(target, insert.$path(), "insert `path`", false)
        .findFirst()
        .map(p -> new JsonNodeOp.Insert(p, value.node()))
        .ifPresent(ops::add);
  }

  private static void spread(JsonMixed target, JsonPatchOp spread, List<JsonNodeOp> ops) {
    JsonMixed value = readValue(spread, "spread `value`", true);
    resolvePaths(target, spread.$path(), "spread `path`", false)
        .findFirst()
        .map(p -> new JsonNodeOp.Spread(p, value.node()))
        .ifPresent(ops::add);
  }

  private static void remove(JsonMixed target, JsonPatchOp remove, List<JsonNodeOp> ops) {
    resolvePaths(target, remove.$path(), "remove `path`", true)
        .filter(target::exists)
        .map(JsonNodeOp.Remove::new)
        .forEach(ops::add);
  }

  private static void replace(JsonMixed target, JsonPatchOp replace, List<JsonNodeOp> ops) {
    JsonMixed path = replace.$path();
    if (path.isString()) {
      replace1(target, replace, ops);
      return;
    }
    JsonPathPattern pattern = readPattern(path, "replace `path`");
    if (!pattern.type().isMultiTarget()) {
      replace1(target, replace, ops);
      return;
    }
    JsonMixed value = readValue(replace, "replace `value`", true);
    List<JsonPath> replaced = resolvePaths(target, pattern, "replace `path`").toList();
    int n = replaced.size();
    int m = value.isArray() ? value.size() : n;   // scalar broadcasts to all targets
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

  private static void replace1(JsonMixed target, JsonPatchOp replace, List<JsonNodeOp> ops) {
    JsonMixed value = readValue(replace, "replace `value`", true);
    resolvePaths(target, replace.$path(), "replace `path`", false)
        .findFirst()
        .map(p -> new JsonNodeOp.Replace(p, value.node()))
        .ifPresent(ops::add);
  }

  private static void move(JsonMixed target, JsonPatchOp move, List<JsonNodeOp> ops) {
    JsonPath from = resolvePath(target, move.from(), "move `from`");
    if (from == null || !target.exists(from)) return;      // missing source => no-op
    JsonPath to = resolvePath(target, move.to(), "move `to` (or `path`)");
    if (to == null) return;
    if (from.equals(to)) return; // move to self => no-op

    // snapshot from original
    ops.add(new JsonNodeOp.Remove(from));
    ops.add(new JsonNodeOp.Insert(to, target.get(from).node()));
  }

  private static void copy(JsonMixed target, JsonPatchOp copy, List<JsonNodeOp> ops) {
    JsonPath from = resolvePath(target, copy.from(), "copy `from`");
    if (from == null || !target.exists(from)) return;
    JsonPath to = resolvePath(target, copy.to(), "copy `to` (or `path`)");
    if (to == null) return;

    ops.add(new JsonNodeOp.Insert(to, target.get(from).node()));
  }

  private static void swap(JsonMixed target, JsonPatchOp swap, List<JsonNodeOp> ops) {
    JsonPath from = resolvePath(target, swap.from(), "swap `from`");
    JsonPath to   = resolvePath(target, swap.to(), "swap `to` (or `path`)");
    if (from == null || to == null) return;
    if (!target.exists(from) || !target.exists(to)) return;
    if (from.equals(to)) return;

    JsonNode valueFrom = target.get(from).node();
    JsonNode valueTo   = target.get(to).node();
    ops.add(new JsonNodeOp.Replace(from, valueTo));
    ops.add(new JsonNodeOp.Replace(to, valueFrom));
  }

  private static void test(JsonMixed target, JsonPatchOp test) {
    JsonMixed path = test.$path();
    JsonMixed expected = readValue(test, "test `value`", false);
    boolean hasExpected = expected.exists();
    if (hasExpected && !path.isString() && path.isArray()) {
      JsonPathPattern pattern = readPattern(path, "test `path`");
      if (pattern.type().isMultiTarget())
        throw new JsonPatchDeclarationException(
            "Operation test with `value` must use single target `path`, but was: "
                + path.toJson());
    }
    List<JsonPath> paths = resolvePaths(target, test.$path(), "test `path`", true).toList();
    if (paths.isEmpty())
      throw new JsonPatchTestException("Test failed: no match for `path` " + test.$path().toJson());
    for (JsonPath p : paths) {
      if (!target.exists(p)) throw new JsonPatchTestException("Test failed: no value at path " + p);
      if (hasExpected && !target.get(p).equivalentTo(expected))
        throw new JsonPatchTestException(
            "Test failed: value at path " + p + " is " + target.get(p) + ", expected: " + expected);
    }
  }

  private static JsonPath resolvePath(JsonMixed target, JsonMixed path, String name) {
    return resolvePaths(target, path, name, false).findFirst().orElse(null);
  }

  private static Stream<JsonPath> resolvePaths(JsonMixed target, JsonMixed path, String name, boolean allowMultiple) {
    if (path.isUndefined())
      throw new JsonPatchDeclarationException(
          "Operation %s is required but was undefined".formatted(name));
    if (path.isString()) {
      try {
        JsonPath p = new JsonPointer(path.string()).decode();
        return Stream.of(resolveDashes(target, p));
      } catch (JsonPathException e) {
        throw new JsonPatchDeclarationException(
            "Operation %s has invalid JSON Pointer `%s`: %s"
                .formatted(name, path.string(), e.getMessage()));
      }
    }
    if (!path.isArray())
      throw new JsonPatchDeclarationException(
          "Operation %s must be either a string or an array, but was: %s"
              .formatted(name, path));

    JsonPathPattern pattern = readPattern(path, name);
    if (!allowMultiple && pattern.type().isMultiTarget()) {
      throw new JsonPatchDeclarationException(
          "Operation %s cannot be used with multi-target %s, but was: %s"
              .formatted(name, pattern.type().name().toLowerCase(Locale.ROOT), path));
    }
    return resolvePaths(target, pattern, name);
  }

  private static Stream<JsonPath> resolvePaths(JsonMixed target, JsonPathPattern pattern, String name) {
    try {
      return pattern.resolve(target);
    } catch (JsonPathPatternException e) {
      throw new JsonPatchTargetException(
          "Operation %s cannot resolve path against target: %s".formatted(name, e.getMessage()));
    }
  }

  /**
   * Resolves {@code -} segments in a decoded JSON Pointer against the target.
   * Per RFC 6901, {@code -} means "the slot after the last array element" when
   * the parent is an array; when the parent is an object, it is an ordinary key.
   */
  private static JsonPath resolveDashes(JsonMixed target, JsonPath path) {
    if (path == null || path == JsonPath.SELF) return path;
    JsonPath parentPath = resolveDashes(target, path.parent());
    Text segment = path.segment();
    if (segment != null && segment.contentEquals("-")) {
      JsonMixed parent = parentPath == null ? target : target.get(parentPath);
      if (parent.isArray())
        return new JsonPath(parentPath, Text.of(parent.size()));
      if (!parent.exists() || !parent.isObject())
        throw new JsonPatchTargetException(
            "Cannot resolve `-` in JSON Pointer: parent does not exist");
      // Not an array: `-` is a literal object key. Leave as-is.
    }
    return new JsonPath(parentPath, segment);
  }

  private static JsonPathPattern readPattern(JsonMixed path, String name) {
    JsonPathPattern pattern;
    try {
      pattern = JsonPathPattern.of(path);
    } catch (JsonPathPatternException e) {
      throw new JsonPatchDeclarationException(
          "Operation %s has invalid path pattern: %s".formatted(name, e.getMessage()));
    }
    return pattern;
  }

  private static JsonMixed readValue(JsonPatchOp op, String name, boolean required) {
    JsonMixed value = op.value();
    if (!required || value.exists()) return value;
    throw new JsonPatchDeclarationException(
        "Operation %s is required but was undefined".formatted(name));
  }

  private static OpType readOp(JsonPatchOp op) {
    JsonString opName = op.op();
    if (opName.isUndefined())
      throw new JsonPatchDeclarationException("Operation `op` is required but was undefined: "+op);
    String name = opName.string().toLowerCase(Locale.ROOT);
    return switch (name) {
      case "?", "test" -> OpType.test;
      case "+", "add", "insert" -> OpType.insert;
      case "-", "delete", "remove" -> OpType.remove;
      case "~", "spread" -> OpType.spread;
      case "move" -> OpType.move;
      case ">", "copy" -> OpType.copy;
      case "swap" -> OpType.swap;
      case "replace" -> OpType.replace;
      default ->
          throw new JsonPatchDeclarationException(
              "Operation `op` %s is unknown in %s: ".formatted(name, op));
    };
  }
}
