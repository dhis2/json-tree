package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.CheckNull;
import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * Exception thrown when constructing a malformed {@link JsonPathPattern}
 * or when a {@link JsonPathPattern#resolve(JsonMixed)} is performed with a target
 * that is incompatible with the pattern.
 *
 * @author Jan Bernitt
 * @since 1.10
 */
public final class JsonPathPatternException extends IllegalArgumentException {

  private final JsonPathPattern pattern;

  public JsonPathPatternException(JsonPathPattern pattern, String message) {
    super(message);
    this.pattern = pattern;
  }

  public JsonPathPattern getPattern() {
    return pattern;
  }

  static JsonPathPatternException chainFailed(@CheckNull JsonPathPattern parent, @NotNull JsonMixed segment) {
    return new JsonPathPatternException(
        parent,
        "Pattern chain error. Cannot extend %s with segment: %s".formatted(parent, segment));
  }

  static JsonPathPatternException chainFailedMultiTarget(@NotNull JsonPathPattern parent, @CheckNull JsonMixed segment) {
    return new JsonPathPatternException(
        parent,
        "Pattern chain error. Cannot extend %s with another multi-target segment: %s"
            .formatted(parent, segment));
  }

  static JsonPathPatternException chainFailedRoot(@NotNull JsonPathPattern parent, @CheckNull JsonMixed segment) {
    return new JsonPathPatternException(parent, "Pattern chain error. Cannot extend root with segment: " + segment);
  }

  static JsonPathPatternException chainFailedArray(@CheckNull JsonPathPattern parent, @NotNull JsonMixed segment) {
    return new JsonPathPatternException(parent, "Pattern chain error. Invalid array segment: " + segment);
  }

  static JsonPathPatternException resolveFailedNoArray(@NotNull JsonPathPattern pattern, JsonArray target) {
    return new JsonPathPatternException(
        pattern,
        "Pattern resolve error. Array pattern `%s` resolves to a node of type %s"
            .formatted(pattern.segment(), target.type()));
  }

  static JsonPathPatternException resolveFailedIndexUnderflow(@NotNull JsonPathPattern pattern, int size, int resolved) {
    return new JsonPathPatternException(pattern, "Pattern resolve error. Array index underflow: " + resolved + " (size " + size + ")");
  }

  static JsonPathPatternException resolveFailedRangeInverted(@NotNull JsonPathPattern pattern, int size) {
    return new JsonPathPatternException(
        pattern,
        "Pattern resolve error. Inverted range `%s` for size %d"
            .formatted(pattern.segment(), size));
  }
}
