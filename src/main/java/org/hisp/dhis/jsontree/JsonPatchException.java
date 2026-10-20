package org.hisp.dhis.jsontree;

import java.util.List;
import org.hisp.dhis.jsontree.internal.CheckNull;
import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * Thrown when applying a patch fails.
 *
 * @author Jan Bernitt
 * @since 1.1
 */
public abstract class JsonPatchException extends IllegalArgumentException {

  protected JsonPatchException(String msg) {
    super(msg);
  }

  /**
   * Thrown when the test of a test-operation is not successful.
   */
  public static final class JsonPatchTestException extends JsonPatchException {

    public JsonPatchTestException(String msg) {
      super(msg);
    }
  }

  /**
   * Thrown when a patch fails because the operation cannot be applied to the target.
   * It is an issue that arises from resolving an operation to a specific target.
   * When thrown as this exact type, the issue is found before the edit phase.
   */
  public static class JsonPatchTargetException extends JsonPatchException {

    public JsonPatchTargetException(String msg) {
      super(msg);
    }
  }

  /**
   * Thrown when the patch fails during the edit phase when actual changes are applied.
   * It is a target specific issue just that it is found late during the edit phase.
   */
  public static final class JsonPatchEditException extends JsonPatchTargetException {

    public JsonPatchEditException(String msg) {
      super(msg);
    }
  }

  /**
   * Thrown when a patch operation has an invalid or inconsistent declaration.
   * It is a target in-dependent issue with an operation.
   */
  public static final class JsonPatchDeclarationException extends JsonPatchException {

    public JsonPatchDeclarationException(String msg) {
      super(msg);
    }
  }



  @NotNull
  public static JsonPatchException clash(
      @NotNull List<? extends JsonNodeOp> ops,
      @NotNull JsonNodeOp a,
      @CheckNull JsonNodeOp b) {
    JsonPath ap = a.path();
    if (b == null)
      return clash(
          ops,
          a,
          ops.stream()
              .filter(op -> op.path().startsWith(ap))
              .findFirst()
              .orElseThrow(() -> new JsonPatchTargetException("")));
    int aIndex = ops.indexOf(a);
    int bIndex = ops.lastIndexOf(b); // use last in case 2 identical operations
    JsonPath bp = b.path();
    if (ap.equals(bp))
      return new JsonPatchTargetException(
          "operation %d has same target as operation %d: %s %s".formatted(aIndex, bIndex, a, b));
    if (bp.startsWith(ap) && bp.length() > ap.length()) return clash(ops, b, a);
    if (ap.startsWith(bp) && ap.length() > bp.length())
      return new JsonPatchTargetException(
          "operation %d targets child of operation %d: %s %s".formatted(aIndex, bIndex, a, b));
    // this should only happen for object merge clashes
    return new JsonPatchTargetException(
        "operation %d contradicts operation %d: %s %s".formatted(aIndex, bIndex, a, b));
  }

}
