package org.hisp.dhis.jsontree;

import java.util.List;
import org.hisp.dhis.jsontree.internal.CheckNull;
import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * When a patch operation fails.
 *
 * @author Jan Bernitt
 * @since 1.1
 */
public final class JsonPatchException extends IllegalArgumentException {

  @NotNull
  public static JsonPatchException clash(
      @NotNull List<JsonNodeOperation> ops,
      @NotNull JsonNodeOperation a,
      @CheckNull JsonNodeOperation b) {
    JsonPath ap = a.path();
    if (b == null)
      return clash(
          ops,
          a,
          ops.stream()
              .filter(op -> op.path().startsWith(ap))
              .findFirst()
              .orElseThrow(() -> new JsonPatchException("")));
    int aIndex = ops.indexOf(a);
    int bIndex = ops.lastIndexOf(b); // use last in case 2 identical operations
    JsonPath bp = b.path();
    if (ap.equals(bp))
      return new JsonPatchException(
          "operation %d has same target as operation %d: %s %s".formatted(aIndex, bIndex, a, b));
    if (bp.startsWith(ap) && bp.length() > ap.length()) return clash(ops, b, a);
    if (ap.startsWith(bp) && ap.length() > bp.length())
      return new JsonPatchException(
          "operation %d targets child of operation %d: %s %s".formatted(aIndex, bIndex, a, b));
    // this should only happen for object merge clashes
    return new JsonPatchException(
        "operation %d contradicts operation %d: %s %s".formatted(aIndex, bIndex, a, b));
  }

  public JsonPatchException(String msg) {
    super(msg);
  }
}
