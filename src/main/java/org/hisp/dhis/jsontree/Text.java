package org.hisp.dhis.jsontree;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * A {@link String}-like API without requiring to manifest a {@link String} instance.
 *
 * @implNote The {@link Text} interface exists to hide the internals of {@link TextSlice} to enable
 *     views without defensive copying.
 *     <p>This allows for a very small memory overhead of sub-sequences found within a JSON
 *     document. Most prominent the JSON string node values and the names of object properties. This
 *     takes advantage of the fact that most such strings do not need JSON level decoding and can be
 *     direct views (slices) of the JSON document itself.
 * @author Jan Bernitt
 * @since 1.9
 * @see CharSeq
 * @see CharFragment
 */
public sealed interface Text extends CharFragment<Text> permits TextSlice {

  /**
   * The empty {@link Text} (equivalent to {@code ""})
   */
  Text EMPTY = Text.of(new char[0], 0, 0);

  /*
  Factories
   */

  static Text copyOf(@NotNull Text text) {
    char[] chars = text.toCharArray();
    return of(chars, 0, chars.length);
  }

  /**
   * @param text the source to convert to a {@link Text}
   * @return the given text as {@link Text} (no copy if it can be avoided)
   * @see #copyOf(Text) to force a copy
   * @since 1.9
   */
  static Text of(@NotNull CharSequence text) {
    return TextSlice.of(text);
  }

  /**
   * Convert a {@link String} to {@link Text}. Caches short inputs under certain unspecified
   * conditions.
   *
   * @return The string's characters as {@link Text} mainly for user space API uses, tests and such.
   *     Internally it should be avoided to use this whenever a {@link Text} can be received from
   *     the {@link JsonNode} API instead.
   */
  static Text of(String text) {
    return TextSlice.of(text);
  }

  /**
   * @apiNote This method will not make a defensive copy of the given buffer as the main use case is
   *     to use {@link Text} as a shallow "pointer" to slices on the same underlying #buffer. Only
   *     arrays that are not mutated any longer should be passed.
   * @since 1.9
   * @param buffer the characters viewed
   * @param offset first character in the buffer included in the view
   * @param length number of characters included in the view from the offset
   * @return A read-only view of the slice starting at offset (no defensive copy)
   */
  static Text of(char[] buffer, int offset, int length) {
    return new TextSlice(buffer, offset, length);
  }

  /**
   * Convert a number to a {@link Text}. Caches 0-999.
   *
   * @return {@link Text} for of any int number
   */
  static Text of(int value) {
    return TextSlice.of(value);
  }

  static Text of(Path file, Charset encoding) {
    try {
      byte[] bytes = Files.readAllBytes(file);
      char[] chars = Chars.decode(bytes, encoding);
      return of(chars, 0, chars.length);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /*
  API extensions
   */

  /**
   * @param other another instance
   * @return the relative offset to the given instance if and only if both share the same memory
   *     ({@link #contentMemoryEquals(Text)}) and if the given text is enclosing this text,
   *     otherwise -1
   * @since 1.13
   */
  int offsetIn(Text other);

  /**
   * @param other another instance to compare to
   * @return true, if the characters of this and the given text are both backed by what the JVM
   *     considers the same "object" instance. This does not imply that the characters exposed by
   *     the two {@link Text} instances are observed as being equal. They only both prevent the GC
   *     of the same underlying storage instance. Or in other words, they share memory without being
   *     necessarily observed as equal.
   */
  default boolean contentMemoryEquals(@NotNull Text other) {
    return this == other;
  }

}
