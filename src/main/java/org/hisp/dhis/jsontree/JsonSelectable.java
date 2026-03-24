package org.hisp.dhis.jsontree;

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
    final class Counter { int count; }
    Counter c = new Counter();
    query(selector, match -> c.count++);
    return c.count;
  }
}
