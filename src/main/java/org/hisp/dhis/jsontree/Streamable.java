package org.hisp.dhis.jsontree;

import java.util.stream.Stream;
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

  @NotNull
  Stream<T> stream();
}
