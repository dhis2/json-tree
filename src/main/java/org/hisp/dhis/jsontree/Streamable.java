package org.hisp.dhis.jsontree;

import static java.util.Collections.emptyIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * A source for both {@link #iterator()}s and {@link #stream()}s.
 *
 * <p>The advantage is that the source can use its internal knowledge to create the {@link Stream}
 * with the least overhead and defer the size computation until the user actually requests a {@link
 * Stream}. This would not be possible when going {@link java.util.Iterator} to {@link
 * java.util.Spliterator} to {@link Stream}.
 *
 * <p>At the same time exposing items like this is much more user-friendly. The {@link Streamable}
 * can be used in advanced for loops but also in {@link java.util.Iterator} based loops as well as
 * {@link Stream}ing all without this ending up creating adapters upon adapters internally.
 *
 * @author Jan Bernitt
 * @since 1.9
 * @param <T> type of elements
 */
public interface Streamable<T> extends Iterable<T> {

  @SuppressWarnings("unchecked")
  static <T> Sized<T> empty() {
    return (Sized<T>) EMPTY;
  }

  @NotNull
  Stream<T> stream();

  /**
   * The adapter API for sized immutable sources.
   *
   * <p>It allows to implement {@link Iterator}-based iteration and {@link Stream}-based consumption
   * of data sources by implementing {@link Iterator#hasNext()}, {@link Iterator#next()} and {@link
   * Spliterator#getExactSizeIfKnown()} without causing any unnecessary indirections or allocation
   * of scaffolding objects. Thereby a highly convenient API is provided without extra costs while
   * keeping the implementation of subclasses straight forward.
   *
   * @author Jan Bernitt
   */
  interface Sized<T> extends Iterator<T>, Spliterator<T>, Streamable<T> {

    // override to make it abstract again
    @Override
    long getExactSizeIfKnown();

    @Override
    default @NotNull Stream<T> stream() {
      return StreamSupport.stream(this, false);
    }

    @Override
    default @NotNull Iterator<T> iterator() {
      return this;
    }

    @Override
    default void forEachRemaining(Consumer<? super T> action) {
      while (hasNext()) action.accept(next());
    }

    @Override
    default boolean tryAdvance(Consumer<? super T> action) {
      if (hasNext()) {
        action.accept(next());
        return true;
      }
      return false;
    }

    default Spliterator<T> trySplit() {
      return null; // not splitting please and thank you...
    }

    default long estimateSize() {
      // This is not accounting for being called after some items have been consumed
      // but the JDK Iterator wrappers don't either, so it got to be fine anyhow
      return getExactSizeIfKnown();
    }

    @Override
    default int characteristics() {
      return ORDERED | SIZED | NONNULL | IMMUTABLE;
    }

    default <E> Sized<E> map(Function<T, E> f) {
      Sized<T> self = this;
      return new Sized<E>() {
        @Override
        public long getExactSizeIfKnown() {
          return self.getExactSizeIfKnown();
        }

        @Override
        public boolean hasNext() {
          return self.hasNext();
        }

        @Override
        public E next() {
          return f.apply(self.next());
        }
      };
    }

    default List<T> toList() {
      int size = (int) getExactSizeIfKnown();
      if (size == 0) return List.of();
      List<T> res = new ArrayList<>(size);
      while (hasNext()) res.add(next());
      return res;
    }
  }

  Sized<?> EMPTY = new Sized<>() {
    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public Object next() {
      throw new NoSuchElementException("Empty does not have a next() element");
    }

    @Override
    public @NotNull Stream<Object> stream() {
      return Stream.empty();
    }

    @Override
    public @NotNull Iterator<Object> iterator() {
      return emptyIterator();
    }

    @Override
    public long getExactSizeIfKnown() {
      return 0L;
    }
  };
}
