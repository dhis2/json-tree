package org.hisp.dhis.jsontree;

import java.util.Optional;
import java.util.function.BinaryOperator;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The API in {@link JsonNode} that is about selecting {@link JsonNode}s based on {@link JsonSelector}s.
 * <p>
 * Just extracted from main {@link JsonNode} interface to group and organize the code a bit.
 *
 * @since 1.9
 */
public interface JsonSelectable<T> {

  void query(JsonSelector selector, Consumer<T> matches);

  default Stream<T> query(JsonSelector selector) {
    Stream.Builder<T> b = Stream.builder();
    query(selector, b);
    return b.build();
  }

  default int queryCount(JsonSelector selector) {
    final class Counter implements Consumer<T> { int count;

      @Override
      public void accept(T t) {
        count++;
      }
    }
    Counter c = new Counter();
    query(selector, c);
    return c.count;
  }

  default boolean queryExists(JsonSelector selector) {
    return queryCount(selector) > 0;
  }

  default Optional<T> queryFirst(JsonSelector selector) {
    return queryAggregate(selector, (l,r) -> l);
  }

  default Optional<T> queryAggregate(JsonSelector selector, BinaryOperator<T> op) {
    final class Aggregator implements Consumer<T> { T value;

      @Override
      public void accept(T match) {
        if (value == null) {
          value = match;
        } else {
          value = op.apply(value, match);
        }
      }
    }
    Aggregator res = new Aggregator();
    query(selector, res);
    return Optional.ofNullable(res.value);
  }

}
