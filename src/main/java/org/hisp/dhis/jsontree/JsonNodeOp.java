package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonPatchException.JsonPatchEditException;
import org.hisp.dhis.jsontree.internal.NotNull;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.toMap;
import static org.hisp.dhis.jsontree.JsonBuilder.createArray;
import static org.hisp.dhis.jsontree.JsonNode.Index.SKIP;

import java.util.ArrayList;
import java.util.List;

/**
 * {@linkplain JsonNodeOp}s describe atomic, target-bound modifications
 * applied via {@link JsonNode#patch(List)}.
 *
 * <p>A {@code JsonNodeOp} has already been resolved against a concrete target —
 * its {@link #path()} contains only literal keys and non-negative integer
 * indexes. All target-dependent decisions (negative index resolution, pattern
 * matching, condition evaluation, array bounds clamping) have already happened
 * before an op reaches this layer. The consumer ({@link #patch}) is a pure
 * text-edit pass over the original JSON with no further target lookups needed
 * for interpretation.
 *
 * <p><b>Order independence.</b> A set of ops applied to the same target
 * produces the same result regardless of order. {@link #checkPatch(List)}
 * enforces this by rejecting any pair whose claims overlap (one path is an
 * ancestor-or-self of the other). Clashing ops throw rather than resolving
 * non-deterministically.
 *
 * <p>Concrete operations: {@link Insert}, {@link Replace}, {@link Spread},
 * {@link Remove}. {@link Remove#value()} returns {@link JsonNode#NULL} so all
 * ops uniformly expose a value for the edit pass.
 *
 * @since 1.1 (renamed and revised since 1.10)
 * @author Jan Bernitt
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
   * Applies a set of operations to the given target, producing a new tree.
   *
   * <p>The order of operations does not matter — any permutation produces the
   * same result. Every operation references the <em>original</em> target's
   * state; no intermediate result is ever materialized. The target itself is
   * not modified.
   *
   * <p>Before any edit is generated, {@link #checkPatch(List)} is invoked to
   * reject clashing operations. If an operation cannot be applied — the target
   * node does not exist, an index is out of bounds, an auto-created parent
   * would land under a scalar, a duplicate-key predecessor is ambiguous, and
   * so on — the whole patch aborts with no changes made.
   *
   * @param target the tree to modify
   * @param patch the operations to apply, usually the result of
   *     {@link JsonPatch#patchOps} but may be constructed directly
   * @return a new tree with the patch applied, or {@code target} itself when
   *     the patch is empty or resolves to no changes
   * @throws JsonPatchException if the operations clash or an edit cannot be
   *     applied
   * @see #checkPatch(List)
   */
  static JsonNode patch(JsonNode target, List<? extends JsonNodeOp> patch) {
    return JsonNodeOpPatch.patch(target, patch);
  }

  /**
   * Verifies that no two of the given operations clash.
   *
   * <p>Two operations clash when a claimed write path of one is an
   * ancestor-or-self of a claimed write path of the other. See
   * {@link JsonNodeOpPatch#claims(JsonNodeOp)} for what each op claims — for most ops this
   * is simply {@link #path()}, but a {@link Spread} of an object claims one
   * path per member, so overlapping keys are what a spread clash actually
   * looks like.
   *
   * <p>Clashes are rejected rather than resolved because the operations are
   * only order-independent when their claimed regions are disjoint: the same
   * pair of ops applied in different orders would produce different results.
   *
   * <p>{@link #patch(JsonNode, List)} calls this internally. Clients may call
   * it directly to validate a patch without producing a result — useful when
   * a patch is built up incrementally and the caller wants to fail early.
   *
   * @param ops the operations to check, may be empty
   * @throws JsonPatchException if any two operations claim overlapping paths
   * @see JsonNodeOpPatch#claims(JsonNodeOp)
   */
  static void checkPatch(List<? extends JsonNodeOp> ops) {
    JsonNodeOpPatch.checkPatch(ops);
  }
}
