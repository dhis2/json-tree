package org.hisp.dhis.jsontree;

import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * @author Jan Bernitt
 * @param <T> the self (result) type for chaining
 * @since 1.15
 */
@FunctionalInterface
public interface JsonCheck<T extends JsonProbe> {

  /**
   * @apiNote The reason {@code getValue} was picked for name is because {@link JsonNode} and {@link JsonValue}
   * already had to implement it so that they can be {@link java.util.Map.Entry}s.
   *
   * @return self
   */
  T getValue();

  /*
  The utilities...
   */

  default <E extends Exception> T check(Predicate<T> is, Supplier<E> orElse) throws E {
    T self = getValue();
    if (is.test(self)) return self;
    throw orElse.get();
  }

  default <E extends Exception> T check(Predicate<T> is, Function<T, E> orElse) throws E {
    T self = getValue();
    if (is.test(self)) return self;
    throw orElse.apply(self);
  }

  default T check(JsonNodeType expected) {
    return check(e -> e.type() == expected, "expected "+expected);
  }

  default T check(Predicate<T> is) throws IllegalArgumentException {
    return check(is, "");
  }

  default T check(Predicate<T> is, String message) throws IllegalArgumentException {
    T self = getValue();
    if (is.test(self)) return self;
    throw new IllegalArgumentException(formatCheckError(self, message));
  }

  private static <T extends JsonProbe> String formatCheckError(T self, String message) {
    boolean exists = self instanceof JsonValue v && !v.exists();
    StringBuilder msg = new StringBuilder();
    if (exists) {
      msg.append("Unexpected ").append(self.type());
    } else {
      msg.append("Missing");
    }
    msg.append(" value at ").append(self.path());
    if (!message.isEmpty()) {
      msg.append(": ").append(msg);
    }
    return msg.toString();
  }
}
