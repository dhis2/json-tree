package org.hisp.dhis.jsontree;

import java.io.IOException;
import java.io.UncheckedIOException;

import static java.lang.Character.highSurrogate;
import static java.lang.Character.isBmpCodePoint;
import static java.lang.Character.lowSurrogate;

/**
 * A more user-friendly version of {@link Appendable}.
 *
 * @author Jan Bernitt
 * @since 1.9
 */
public interface Appender extends Appendable {

  static Appender of(Appendable to) {
    return new Adapter(to);
  }

  @Override
  Appendable append(char c);

  @Override
  Appendable append(CharSequence text);

  @Override
  default Appendable append(CharSequence text, int start, int end) {
    if (text.isEmpty()) return this;
    int len = end - start;
    for (int i = 0; i < len; i++) append(text.charAt(start + i));
    return this;
  }

  default Appendable append(int value) {
    return append(String.valueOf(value));
  }

  default Appendable append(long value) {
    return append(String.valueOf(value));
  }

  default Appendable append(double value) {
    return append(String.valueOf(value));
  }

  default Appendable appendCodePoint(int cp) {
    if (isBmpCodePoint(cp)) return append((char) cp);
    append(highSurrogate(cp));
    return append(lowSurrogate(cp));
  }

  record Adapter(Appendable to) implements Appender {

    @Override
    public Appendable append(char c) {
      try {
        return to.append(c);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public Appendable append(CharSequence text) {
      try {
        return to.append(text);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    @Override
    public String toString() {
      return to.toString();
    }
  }
}
