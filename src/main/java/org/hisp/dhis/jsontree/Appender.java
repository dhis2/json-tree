package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.NotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.IntStream;

import static java.lang.Character.highSurrogate;
import static java.lang.Character.isBmpCodePoint;
import static java.lang.Character.lowSurrogate;
import static java.util.Objects.requireNonNull;

/**
 * A more user-friendly version of an {@link Appendable} append-only sink.
 * <p>
 * {@link StringBuilder} can be wrapped using {@link #of(StringBuilder)}.
 * All other {@link Appendable}s can be wrapped using {@link #of(Appendable)}.
 *
 * @author Jan Bernitt
 * @since 1.9
 */
public interface Appender extends Appendable {

  @Override
  Appender append(char c);

  @Override
  default Appender append(CharSequence text) {
    return append(text, 0, text == null ? 4 : text.length());
  }

  @Override
  default Appender append(CharSequence text, int start, int end) {
    if (text == null) text = "null";
    if (text.isEmpty()) return this;
    int len = end - start;
    for (int i = 0; i < len; i++) append(text.charAt(start + i));
    return this;
  }

  default Appender append(char[] chars) {
    return append(chars, 0, chars.length);
  }

  default Appender append(char[] chars, int offset, int len) {
    requireNonNull(chars);
    for (int i = 0; i < len; i++) append(chars[offset + i]);
    return this;
  }

  default Appender append(int value) {
    return append(String.valueOf(value));
  }

  default Appender append(long value) {
    return append(String.valueOf(value));
  }

  default Appender append(double value) {
    return append(String.valueOf(value));
  }

  default Appender appendCodePoint(int cp) {
    if (isBmpCodePoint(cp)) return append((char) cp);
    append(highSurrogate(cp));
    return append(lowSurrogate(cp));
  }

  /**
   * Elevate an {@link Appendable} to an {@link Appender}
   */
  static Appender of(@NotNull Appendable to) {
    record Adapter(Appendable to) implements Appender {

      @Override
      public Appender append(char c) {
        try {
          to.append(c);
          return this;
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      @Override
      public Appender append(CharSequence text, int start, int end) {
        try {
          to.append(text, start, end);
          return this;
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      @Override
      public String toString() {
        return to.toString();
      }
    }
    return new Adapter(to);
  }

  /**
   * Special adapter for {@link StringBuilder} just for lower overhead
   */
  static Appender of(@NotNull StringBuilder to) {
    record Adapter(StringBuilder to) implements Appender {

      @Override
      public Appender append(char c) {
          to.append(c);
          return this;
      }

      @Override
      public Appender append(CharSequence text) {
        to.append(text);
        return this;
      }

      @Override
      public Appender append(CharSequence text, int start, int end) {
          to.append(text, start, end);
          return this;
      }

      @Override
      public Appender append(char[] chars) {
        to.append(chars);
        return this;
      }

      @Override
      public Appender append(char[] chars, int offset, int len) {
        to.append(chars, offset, len);
        return this;
      }

      @Override
      public Appender appendCodePoint(int cp) {
        to.appendCodePoint(cp);
        return this;
      }

      public Appender appendCodePoints(IntStream codePoints) {
        codePoints.forEach(this::appendCodePoint);
        return this;
      }

      @Override
      public Appender append(int value) {
        to.append(value);
        return this;
      }

      @Override
      public Appender append(long value) {
        to.append(value);
        return this;
      }

      @Override
      public Appender append(double value) {
        to.append(value);
        return this;
      }

      @Override
      public String toString() {
        return to.toString();
      }
    }
    return new Adapter(to);
  }

}
