package org.hisp.dhis.jsontree;

/**
 * An {@link Appender} that requires to release resources, so it should be used with
 * try-with-resources block.
 *
 * @since 1.13
 */
public interface IoAppender extends Appender, AutoCloseable {

  /**
   * Releases any resources held by this sink.
   *
   * <p>Sinks that own an external resource — for example a {@link java.io.OutputStream} — must
   * override this method to flush pending bytes and release the resource.
   *
   * <p>Unlike {@link AutoCloseable#close()} this method does not declare a checked exception. Sinks
   * that wrap I/O are expected to translate failures into {@link java.io.UncheckedIOException}.
   */
  @Override
  default void close() {}
}
