package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchDeclarationException;
import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchTargetException;
import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchTestException;
import org.hisp.dhis.jsontree.JsonPathPattern.JsonPathPatternType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * A {@link JsonPatch} allows bulk-editing a {@link JsonValue} using a set of
 * {@link JsonPatchOp}erations.
 *
 * <p><b>Not iterative.</b> In contrast to RFC 6902, this implementation does not
 * apply operations sequentially. All operations resolve against the <em>original
 * target</em>, and collisions are detected by comparing the claimed write paths
 * before any edit is attempted. Any clash aborts the patch. This means:
 * <ul>
 *   <li>Operations are order-independent — any permutation produces the same result.</li>
 *   <li>Every path, index, and condition is resolved against the pre-patch target.</li>
 *   <li>The patch can be validated without producing an intermediate result, and the
 *       final result is produced in one pass over the source text.</li>
 * </ul>
 *
 * <p>For example, removing the first three elements of an array:
 * <pre>
 *   { "op": "remove", "path": ["array", [[0,2]]] }
 *   // or
 *   [
 *     { "op": "remove", "path": ["array", 0] },
 *     { "op": "remove", "path": ["array", 1] },
 *     { "op": "remove", "path": ["array", 2] }
 *   ]
 * </pre>
 * In RFC 6902 the same removals would have to be issued in order {@code 2, 1, 0}.
 *
 * <p><b>Path forms.</b> The {@code path} (and {@code from}/{@code to} for
 * move/copy/swap) may be either a JSON array of segment expressions or an
 * RFC 6901 JSON Pointer string ({@code "/foo/0/-"}), making the surface
 * compatible with standard JSON Patch for single-target operations.
 *
 * <p><b>Operation names.</b> Both canonical names ({@code insert}, {@code remove},
 * …) and RFC 6902 aliases ({@code add}, {@code delete}, …) are accepted. See
 * {@link JsonPatchOpType}.
 *
 * <p><b>Test-first semantics.</b> A {@code test} operation — either standalone or
 * provided as the optional {@code test} field on a mutation op — runs before any
 * mutation is queued. If any test fails, the entire patch aborts without
 * attempting edits.
 *
 * <p><b>Known limitation: duplicate keys.</b> Path-based addressing cannot
 * disambiguate duplicate object keys; {@code remove} guards against silently
 * erasing the wrong span when a duplicate predecessor is detected and throws.
 *
 * @see JsonPathPattern  the path expression grammar
 * @see JsonNodeOp  the resolved operations
 * @since 1.10
 * @author Jan Bernitt
 */
public interface JsonPatch extends JsonAbstractArray<JsonMixed> {

  enum JsonPatchOpType {
    /**
     * Asserts a precondition on the target. Without a {@code value} field, passes
     * when the target path resolves to a <em>defined</em> value (neither missing
     * nor JSON null). With {@code value}, passes only if the value at the path
     * equals the provided JSON.
     *
     * <p>Also accepted as {@code "?"}.
     */
    test,
    /** Adds a value; {@code spread} for multiple array elements. Alias: {@code "add"}, {@code "+"}. */
    insert,
    /** Removes a value at the path; a no-op when the path does not exist. Aliases: {@code "delete"}, {@code "-"}. */
    remove,
    /** Replaces the value at a path. Aliases: none beyond {@code "replace"}. */
    replace,
    /** Inserts multiple elements or object members at one path. Alias: {@code "~"}. */
    spread,
    /** Removes from {@code from} and inserts at {@code to} (or {@code path}). */
    move,
    /** Copies the value at {@code from} to {@code to} (or {@code path}). Alias: {@code ">"}. */
    copy,
    /** Exchanges the values at {@code from} and {@code to}. */
    swap;

    /**
     * @return true when this op type accepts the optional {@code test} field for
     *     CAS-style conditional execution. {@code test} itself and ops that do not
     *     write a single identifiable value ({@code spread}, {@code swap}) are
     *     excluded.
     */
    public boolean allowsTest() {
      return this != test && this != spread && this != swap;
    }

    /**
     * @return true when this op uses the {@code to} field for its destination
     *     (with {@code path} as fallback for RFC 6902 compatibility).
     */
    public boolean allowsTo() {
      return this == move || this == copy || this == swap;
    }
  }

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

    default JsonMixed test() {
      return get("test");
    }

    default boolean hasTo() {
      return get("to").exists();
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
    // do all tests first
    //TODO sort test by target path?
    for (JsonMixed x : patch) {
      JsonPatchOp op = x.as(JsonPatchOp.class);
      JsonPatchOpType opType = readOp(op);
      if (op.test().exists()) {
        if (!opType.allowsTest())
          throw new JsonPatchDeclarationException(
              "Operation `%s` cannot use a `test` field".formatted(opType));
        String pathName = op.hasTo() ? "to" : "path";
        test(target, opType, op.to(), "%s `%s`".formatted(opType, pathName), op.test());
      } else {
        if (opType == JsonPatchOpType.test) {
          test(target, op);
        }
      }
    }
    // then move to actual changes
    for (JsonMixed x : patch) {
      JsonPatchOp op = x.as(JsonPatchOp.class);
      JsonPatchOpType opType = readOp(op);
      if (!opType.allowsTo() && op.hasTo())
        throw new JsonPatchDeclarationException(
            "Operation %s does not allow use of `to`".formatted(opType));
      switch (opType) {
        case test -> {} // already handled
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
    resolvePaths(target, insert.$path(), "insert `path`", false, true)
        .findFirst()
        .map(p -> new JsonNodeOp.Insert(p, value.node()))
        .ifPresent(ops::add);
  }

  private static void spread(JsonMixed target, JsonPatchOp spread, List<JsonNodeOp> ops) {
    JsonMixed value = readValue(spread, "spread `value`", true);
    resolvePaths(target, spread.$path(), "spread `path`", false, true)
        .findFirst()
        .map(p -> new JsonNodeOp.Spread(p, value.node()))
        .ifPresent(ops::add);
  }

  private static void remove(JsonMixed target, JsonPatchOp remove, List<JsonNodeOp> ops) {
    resolvePaths(target, remove.$path(), "remove `path`", true, false)
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
    List<JsonPath> replaced = resolvePaths(target, pattern, "replace `path`", true).toList();
    int n = replaced.size();

    // Ranges clamp; enumerations don't. Any "write target" that doesn't exist
    // means the client's view of the array is stale — same premise as the
    // empty-range error below.
    int m = value.isArray() ? value.size() : n;
    for (int i = 0; i < Math.min(n, m); i++) {
      if (!target.exists(replaced.get(i)))
        throw new JsonPatchTargetException(
            "Replace target does not exist (target may have changed): " + replaced.get(i));
    }
    if (n == 0 && pattern.type() == JsonPathPatternType.RANGE) {
      throw new JsonPatchTargetException(
          "Replace range resolved to no existing targets: " + path.toJson());
    }
    if (pattern.type() == JsonPathPatternType.ENUMERATION && n > 0 && m > n) {
      throw new JsonPatchDeclarationException(
          "Operation replace has more values (%d) than targets (%d) for enumeration path: %s"
              .formatted(m, n, path.toJson()));
    }

    int r = Math.min(n, m);
    for (int i = 0; i < r; i++)
      ops.add(new JsonNodeOp.Replace(replaced.get(i), value.isArray() ? value.get(i).node() : value.node()));
    if (m < n) {
      for (int i = r; i < n; i++)
        ops.add(new JsonNodeOp.Remove(replaced.get(i)));
    } else if (m > n && n > 0) {
      // range only — enumeration already threw above
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
    JsonPath p = resolvePaths(target, replace.$path(), "replace `path`", false, true)
        .findFirst()
        .orElseThrow(() -> new JsonPatchTargetException(
            "Replace target could not be resolved: " + replace.$path().toJson()));

    if (target.exists(p)) {
      // In-place replace
      ops.add(new JsonNodeOp.Replace(p, value.node()));
      return;
    }
    // Target missing. Distinguish object vs array parent.
    JsonPath parentPath = p.parentPath();
    if (!parentPath.isEmpty() && !target.exists(parentPath)) {
      throw new JsonPatchTargetException(
          "Replace parent does not exist: " + parentPath);
    }
    JsonMixed parent = parentPath.isEmpty() ? target : target.get(parentPath);
    if (parent.isObject()) {
      // Object "set key" semantics — add missing member.
      // Equivalent to Remove(no-op) + Insert(add-member).
      ops.add(new JsonNodeOp.Insert(p, value.node()));
      return;
    }
    // Array (or other): replacing a non-existent position is an error.
    throw new JsonPatchTargetException(
        "Replace target does not exist: " + p);
  }

  private static void move(JsonMixed target, JsonPatchOp move, List<JsonNodeOp> ops) {
    JsonPath from = resolvePath(target, move.from(), "move `from`", false);
    if (from == null || !target.exists(from)) return;      // missing source => no-op
    JsonPath to = resolvePath(target, move.to(), "move `to` (or `path`)", true);
    if (to == null) return;
    if (from.equals(to)) return; // move to self => no-op

    // snapshot from original
    ops.add(new JsonNodeOp.Remove(from));
    ops.add(new JsonNodeOp.Insert(to, target.get(from).node()));
  }

  private static void copy(JsonMixed target, JsonPatchOp copy, List<JsonNodeOp> ops) {
    JsonPath from = resolvePath(target, copy.from(), "copy `from`", false);
    if (from == null || !target.exists(from)) return;
    JsonPath to = resolvePath(target, copy.to(), "copy `to` (or `path`)", true);
    if (to == null) return;

    ops.add(new JsonNodeOp.Insert(to, target.get(from).node()));
  }

  private static void swap(JsonMixed target, JsonPatchOp swap, List<JsonNodeOp> ops) {
    JsonPath from = resolvePath(target, swap.from(), "swap `from`", false);
    JsonPath to   = resolvePath(target, swap.to(), "swap `to` (or `path`)", false);
    if (from == null || to == null) return;
    if (!target.exists(from) || !target.exists(to)) return;
    if (from.equals(to)) return;

    JsonNode valueFrom = target.get(from).node();
    JsonNode valueTo   = target.get(to).node();
    ops.add(new JsonNodeOp.Replace(from, valueTo));
    ops.add(new JsonNodeOp.Replace(to, valueFrom));
  }

  private static void test(JsonMixed target, JsonPatchOp test) {
    JsonMixed expected = readValue(test, "test `value`", false);
    test(target, JsonPatchOpType.test, test.$path(), "test `path`", expected);
  }

  private static void test(JsonMixed target, JsonPatchOpType type, JsonMixed path, String pathName, JsonMixed expected) {
    boolean hasExpected = expected.exists();
    if (hasExpected && !path.isString() && path.isArray()) {
      JsonPathPattern pattern = readPattern(path, pathName);
      if (pattern.type().isMultiTarget())
        throw new JsonPatchDeclarationException(
            "Operation %s against a value only supports single target `path`, but was: %s"
                .formatted(type, path.toJson()));
    }

    List<JsonPath> paths = resolvePaths(target, path, pathName, true, false).toList();
    if (paths.isEmpty())
      throw new JsonPatchTestException("Test failed: no match for `path` " + path.toJson());

    for (JsonPath p : paths) {
      if (hasExpected) {
        if (!target.exists(p))
          throw new JsonPatchTestException("Test failed: no value at path " + p);
        if (!target.get(p).equivalentTo(expected))
          throw new JsonPatchTestException(
              "Test failed: value at path " + p + " is " + target.get(p)
                  + ", expected: " + expected);
      } else {
        if (target.get(p).isUndefined())
          throw new JsonPatchTestException(
              "Test failed: value at path " + p + " is undefined (null or missing)");
      }
    }
  }

  private static Stream<JsonPath> resolvePaths(
      JsonMixed target, JsonMixed path, String name, boolean allowMultiple, boolean writable) {
    if (path.isUndefined())
      throw new JsonPatchDeclarationException(
          "Operation %s is required but was undefined".formatted(name));
    if (path.isString()) {
      JsonPath decoded;
      try {
        decoded = new JsonPointer(path.string()).decode();
      } catch (JsonPathException e) {
        throw new JsonPatchDeclarationException(
            "Operation %s has invalid JSON Pointer `%s`: %s"
                .formatted(name, path.string(), e.getMessage()));
      }
      JsonPath resolved = resolveDashes(target, decoded);
      validateResolvedPath(target, resolved, name, writable);
      return Stream.of(resolved);
    }
    if (!path.isArray())
      throw new JsonPatchDeclarationException(
          "Operation %s must be either a string or an array, but was: %s"
              .formatted(name, path));

    JsonPathPattern pattern = readPattern(path, name);
    if (!allowMultiple && pattern.type().isMultiTarget())
      throw new JsonPatchDeclarationException(
          "Operation %s cannot be used with multi-target %s, but was: %s"
              .formatted(name, pattern.type().name().toLowerCase(Locale.ROOT), path));

    return resolvePaths(target, pattern, name, writable);
  }

  private static Stream<JsonPath> resolvePaths(
      JsonMixed target, JsonPathPattern pattern, String name, boolean writable) {
    Stream<JsonPath> resolved;
    try {
      resolved = pattern.resolve(target);
    } catch (JsonPathPatternException e) {
      throw new JsonPatchTargetException(
          "Operation %s cannot resolve path against target: %s".formatted(name, e.getMessage()));
    }
    return resolved.peek(p -> validateResolvedPath(target, p, name, writable));
  }

  private static JsonPath resolvePath(JsonMixed target, JsonMixed path, String name, boolean writable) {
    return resolvePaths(target, path, name, false, writable).findFirst().orElse(null);
  }

  private static void validateResolvedPath(JsonMixed target, JsonPath p, String name, boolean writable) {
    if (p.isEmpty()) return;
    JsonPath parentPath = p.parentPath();

    JsonMixed parent = parentPath.isEmpty() ? target : target.get(parentPath);
    if (!parent.exists()) {
      if (!writable) return;
      // Parent missing: walk up to find the deepest existing ancestor.
      // If that ancestor exists and cannot host a write (scalar/null/etc.),
      // fail at declaration
      JsonPath ancestorPath = parentPath;
      while (!ancestorPath.isEmpty() && !target.exists(ancestorPath))
        ancestorPath = ancestorPath.parentPath();
      JsonMixed ancestor = ancestorPath.isEmpty() ? target : target.get(ancestorPath);
      if (ancestor.exists() && !ancestor.isObject() && !ancestor.isArray())
        throw new JsonPatchTargetException(
            "Operation %s: cannot write under %s parent at `%s`"
                .formatted(name, ancestor.type(), ancestorPath));
      return;
    }

    if (parent.isArray()) {
      if (!p.segment().isTextualInteger())
        throw new JsonPatchTargetException(
            "Operation %s: cannot address array `%s` with non-integer segment `%s`"
                .formatted(name, parentPath, p.segment()));
    } else if (!parent.isObject() && writable) {
      throw new JsonPatchTargetException(
          "Operation %s: cannot write under %s parent at `%s`"
              .formatted(name, parent.type(), parentPath));
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

  private static JsonPatchOpType readOp(JsonPatchOp op) {
    JsonString opName = op.op();
    if (opName.isUndefined())
      throw new JsonPatchDeclarationException("Operation `op` is required but was undefined: "+op);
    String name = opName.string().toLowerCase(Locale.ROOT);
    return switch (name) {
      case "?", "test" -> JsonPatchOpType.test;
      case "+", "add", "insert" -> JsonPatchOpType.insert;
      case "-", "delete", "remove" -> JsonPatchOpType.remove;
      case "~", "spread" -> JsonPatchOpType.spread;
      case "move" -> JsonPatchOpType.move;
      case ">", "copy" -> JsonPatchOpType.copy;
      case "swap" -> JsonPatchOpType.swap;
      case "replace" -> JsonPatchOpType.replace;
      default ->
          throw new JsonPatchDeclarationException(
              "Operation `op` %s is unknown in %s: ".formatted(name, op));
    };
  }
}
