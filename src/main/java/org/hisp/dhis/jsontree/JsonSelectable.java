package org.hisp.dhis.jsontree;

import static java.util.Comparator.comparingInt;

import java.util.Optional;
import java.util.PriorityQueue;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Stream;
import org.hisp.dhis.jsontree.JsonSelector.Matches;

/**
 * The API in {@link JsonNode} that is about selecting {@link JsonNode}s based on {@link JsonSelector}s.
 * <p>
 * Just extracted from main {@link JsonNode} interface to group and organize the code a bit.
 *
 * @since 1.9
 */
public interface JsonSelectable<T> {

  /**
   * The essential method of the query API.
   *
   * @implNote while {@link Stream}s might be more convenient to deal with for a caller the {@link
   *     java.util.function.Consumer} based API as offered by this essential method has the best
   *     performance as no collection of matches is required. Instead, the aggregation is handled by
   *     the caller in the most optimal way for the callers use case.
   * @param selector the path selector (query)
   * @param matches a callback to collect matches
   */
  void query(JsonSelector selector, Matches<T> matches);

  default Stream<T> query(JsonSelector selector) {
    return query(selector, Integer.MAX_VALUE);
  }

  default Stream<T> query(JsonSelector selector, int limit) {
    final class Streamer implements Matches<T> {

      final Stream.Builder<T> stream = Stream.builder();
      int count;

      @Override
      public boolean satisfied() {
        return count >= limit;
      }

      @Override
      public void accept(T match) {
        count++;
        stream.accept(match);
      }
    }
    Streamer res = new Streamer();
    query(selector, res);
    return res.stream.build();
  }

  default int queryCount(JsonSelector selector) {
    return queryCount(selector, Integer.MAX_VALUE);
  }

  default int queryCount(JsonSelector selector, int limit) {
    final class Counter implements Matches<T> { int count;

      @Override
      public boolean satisfied() {
        return count >= limit;
      }

      @Override
      public void accept(T match) {
        count++;
      }
    }
    Counter c = new Counter();
    query(selector, c);
    return c.count;
  }

  default boolean queryExists(JsonSelector selector) {
    return queryCount(selector, 1) > 0;
  }

  default Optional<T> queryFirst(JsonSelector selector) {
    return query(selector, 1).findFirst();
  }

  default <V> V queryReduce(
      JsonSelector selector, Function<T, V> extract, V init, BinaryOperator<V> reduce) {
    return queryReduce(selector, Integer.MAX_VALUE, extract, init, reduce);
  }

  default <V> V queryReduce(
      JsonSelector selector, int limit, Function<T, V> extract, V init, BinaryOperator<V> reduce) {
    final class Reducer implements Matches<T> {
      V value = init;
      int count;

      @Override
      public boolean satisfied() {
        return count >= limit;
      }

      @Override
      public void accept(T match) {
        count++;
        value = reduce.apply(value, extract.apply(match));
      }
    }
    Reducer res = new Reducer();
    query(selector, res);
    return res.value;
  }

  default Stream<T> queryTop(int n, JsonSelector selector, ToIntFunction<T> score) {
    record Score<R>(R value, int score) {}
    final PriorityQueue<Score<T>> heap = new PriorityQueue<>(n+1, comparingInt(Score::score));
    query(
        selector,
        match -> {
          heap.offer(new Score<>(match, score.applyAsInt(match)));
          if (heap.size() > n) heap.poll();
        });
    return heap.stream().sorted((a, b) -> Integer.compare(b.score, a.score)).map(Score::value);
  }
}
