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
 * The API in {@link JsonNode} that is about selecting {@link JsonNode}s based on {@link
 * JsonSelector}s.
 *
 * <p>Just extracted from main {@link JsonNode} interface to group and organize the code a bit.
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

  /**
   * @implNote JDK {@link Stream}s are convenient but always prefer one of the other methods for
   *     better performance
   * @return Queries the entire (sub)tree and return a stream of matches
   */
  default Stream<T> query(JsonSelector selector) {
    return query(selector, Integer.MAX_VALUE);
  }

  /**
   * @implNote JDK {@link Stream}s are convenient but always prefer one of the other methods for
   *     better performance
   * @param limit maximum number of matches before ending the search
   * @return Queries the entire (sub)tree and return a stream of matches up to the given limit of
   *     matches
   */
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

  /**
   * @return Queries the entire (sub)tree and return the number of matches found
   */
  default int queryCount(JsonSelector selector) {
    return queryCount(selector, Integer.MAX_VALUE);
  }

  /**
   * @param limit maximum number of matches before ending the search
   * @return Queries the entire (sub)tree and return the number of matches up to the given limit of
   *     matches
   */
  default int queryCount(JsonSelector selector, int limit) {
    final class Counter implements Matches<T> {
      int count;

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

  /**
   * @return Queries the entire (sub)tree and ends the search returning true, as soon as at least
   *     one match is found
   */
  default boolean queryExists(JsonSelector selector) {
    return queryCount(selector, 1) > 0;
  }

  /**
   * @return Queries the entire (sub)tree and ends the search returning the first match, as soon as
   *     it is found
   */
  default Optional<T> queryFirst(JsonSelector selector) {
    return query(selector, 1).findFirst();
  }

  /**
   * Same as {@link #queryReduce(JsonSelector, int, Function, Object, BinaryOperator)} but without
   * limit so all matches will be aggregated
   */
  default <V> V queryReduce(
      JsonSelector selector, Function<T, V> extract, V init, BinaryOperator<V> reduce) {
    return queryReduce(selector, Integer.MAX_VALUE, extract, init, reduce);
  }

  /**
   * @param limit maximum number of matches before ending the search
   * @param extract a function to extract the value from a node that is aggregated (reduced)
   * @param init the initial value of the aggregation
   * @param reduce the reduction (aggregation) function used to combine values extracted from
   *     matches (right hand side operator) with the aggregation so far (left hand side operator)
   * @return Queries the entire (sub)tree and aggregates the values extracted from matches to a
   *     single aggregate value (up to the given limit of matches) matches
   */
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

  /**
   * @param n number of highest scoring matches to return
   * @param score scoring function (higher is better/kept)
   * @return the best scoring matches (up to given limit of n) in order from the highest score to
   *     the lowest score
   */
  default Stream<T> queryTop(int n, JsonSelector selector, ToIntFunction<T> score) {
    record Score<R>(R value, int score) {}
    final PriorityQueue<Score<T>> heap = new PriorityQueue<>(n + 1, comparingInt(Score::score));
    query(
        selector,
        match -> {
          heap.offer(new Score<>(match, score.applyAsInt(match)));
          if (heap.size() > n) heap.poll();
        });
    return heap.stream().sorted((a, b) -> Integer.compare(b.score, a.score)).map(Score::value);
  }
}
