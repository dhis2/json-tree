package org.hisp.dhis.jsontree;

import static java.util.Objects.requireNonNull;
import static org.hisp.dhis.jsontree.Validation.NodeType.STRING;

import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * As defined by <a href="https://datatracker.ietf.org/doc/html/rfc6901/">RFC-6901</a>.
 *
 * @author Jan Bernitt
 * @since 1.1
 * @param value a pointer expression
 */
@Validation(type = STRING)
@Validator(value = Validation.RegEx.class, params = @Validation( pattern = "(/((~[01])|([^/~]))*)*"))
public record JsonPointer(@NotNull String value) {

  public JsonPointer {
    requireNonNull(value);
  }

  @NotNull
  public JsonPath decode() {
    if (value.isEmpty()) return JsonPath.SELF;
    Text path = Text.of(value);
    int start = 1; // Skip the leading '/'
    int end = path.indexOf('/', start);
    if (end < 0) return JsonPath.of(decode(path.subSequence(start, path.length())));
    JsonPath res = JsonPath.SELF;
    while (end >= 0) {
      res = res.chain(decode(path.subSequence(start, end)));
      start = end + 1;
      end = path.indexOf('/', start);
    }
    return res.chain(decode(path.subSequence(start, path.length())));
  }

  private static Text decode(Text segment) {
    if (!segment.contains('~')) return segment;
    boolean has1 = segment.contains("~1");
    boolean has0 = segment.contains("~0");
    if (!has1 && !has0) return segment;
    return Text.of(segment.toString().replace("~1", "/").replace("~0", "~"));
  }

  @Override
  public String toString() {
    return value;
  }
}
