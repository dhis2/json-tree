package org.hisp.dhis.jsontree;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import static org.hisp.dhis.jsontree.Appender.checkCodePoint;
import static org.hisp.dhis.jsontree.Appender.checkRange;
import static org.hisp.dhis.jsontree.Appender.needsJsonEscaping;

/**
 * A UTF-8 encoding {@link Appender} that writes directly to an {@link OutputStream}.
 *
 * <p>This is the streaming counterpart to {@link TextBuilder}. Instances are normally obtained
 * via {@link Appender#ofUTF8(OutputStream)}. Encoded bytes are accumulated in a fixed 8 KiB
 * buffer and written to the underlying stream in chunks; {@link #close()} flushes the remainder
 * and closes the stream.
 *
 * <p>The class exists to avoid the allocations and per-call overhead of any {@link
 * java.io.Writer}-based sink when streaming JSON. Specifically:
 *
 * <ul>
 *   <li>UTF-8 encoding is done in-line using integer arithmetic into a reusable {@code byte[]}.
 *       No {@link java.nio.charset.CharsetEncoder}, {@link java.nio.CharBuffer}, or intermediate
 *       {@code char[]} is involved.</li>
 *   <li>{@link #appendAscii} copies the ASCII content of a span chunk-by-chunk, so the bounds
 *       check on the buffer happens once per chunk rather than once per character.</li>
 *   <li>{@link #appendJsonEscaped} writes JSON escape sequences as ASCII bytes directly into the
 *       buffer, so the escape step allocates nothing.</li>
 * </ul>
 *
 * <p>Unicode scalars that cannot form a valid UTF-8 sequence (unpaired surrogates passed to
 * {@link #appendEncoded(char)} or {@link #appendEncoded(CharSequence, int, int)}, as well as
 * surrogate code points passed to {@link #appendCodePoint(int)}) are encoded as the replacement
 * character {@code U+FFFD}. This matches the {@code CodingErrorAction.REPLACE} behaviour of the
 * JDK's own encoder — a caller that splits a surrogate pair across two single-char calls gets
 * {@code U+FFFD U+FFFD}, the same result an {@link java.io.OutputStreamWriter} would produce.
 *
 * <p>{@link #close()} flushes any pending bytes and closes the underlying {@link OutputStream}.
 * This matches the previous {@link java.io.BufferedWriter} ownership semantics of {@link
 * JsonBuilder#streamObject(OutputStream, java.util.function.Consumer)} and {@link
 * JsonBuilder#streamArray(OutputStream, java.util.function.Consumer)}: the sink owns the target.
 *
 * @author Jan Bernitt (with AI support)
 * @see Appender#ofUTF8(OutputStream)
 * @see TextBuilder
 * @since 1.12
 */
final class Utf8OutputStreamAppender implements Appender {

  private static final int BUFFER_SIZE = 8192;

  private final OutputStream out;
  private final byte[] utf8 = new byte[BUFFER_SIZE];
  private int pos;

  Utf8OutputStreamAppender(OutputStream out) {
    this.out = out;
  }

  @Override
  public void appendAscii(char c) {
    if (pos == BUFFER_SIZE) flushBuffer();
    utf8[pos++] = (byte) c; // ASCII contract
  }

  @Override
  public void appendAscii(CharSequence text, int start, int end) {
    if (text == null) text = "null";
    checkRange(text, start, end);
    int i = start;
    while (i < end) {
      if (pos == BUFFER_SIZE) flushBuffer();
      int n = Math.min(end - i, BUFFER_SIZE - pos);
      int iEnd = i + n;
      int j = pos;
      while (i < iEnd) utf8[j++] = (byte) text.charAt(i++);
      pos = j;
    }
  }

  @Override
  public void appendEncoded(char c) {
    if (c < 0x80) {
      if (pos == BUFFER_SIZE) flushBuffer();
      utf8[pos++] = (byte) c;
    } else if (c < 0x800) {
      if (pos > BUFFER_SIZE - 2) flushBuffer();
      utf8[pos++] = (byte) (0xC0 | (c >> 6));
      utf8[pos++] = (byte) (0x80 | (c & 0x3F));
    } else if (Character.isSurrogate(c)) {
      if (pos > BUFFER_SIZE - 3) flushBuffer();
      writeReplacementChar();
    } else {
      if (pos > BUFFER_SIZE - 3) flushBuffer();
      utf8[pos++] = (byte) (0xE0 | (c >> 12));
      utf8[pos++] = (byte) (0x80 | ((c >> 6) & 0x3F));
      utf8[pos++] = (byte) (0x80 | (c & 0x3F));
    }
  }

  private static final int MIN_CHUNK_BYTES = 8; // 2 chars × 4 bytes worst case

  /**
   * Writes the given span using {@link #appendEncoded(char)} semantics, so the UTF-8 encoding of
   * each character is produced directly into the buffer.
   *
   * <p>Surrogate pairs that fall entirely within {@code [start, end)} are encoded as a single
   * 4-byte UTF-8 sequence. A high surrogate at the last position of the span, one not followed by
   * a low surrogate, or a lone low surrogate is written as {@code U+FFFD}. Note that this means a
   * surrogate pair split across the boundary of two spans (or between two separate calls) will
   * decode as two replacement characters, not as the original codepoint — the same
   * {@code OutputStreamWriter.write} behaviour.
   */
  @Override
  public void appendEncoded(CharSequence text, int start, int end) {
    if (text == null) {
      appendAscii("null", start, end);
      return;
    }
    checkRange(text, start, end);
    int i = start;
    while (i < end) {
      // always start a chunk with enough room for at least a surrogate pair
      if (pos >= BUFFER_SIZE - MIN_CHUNK_BYTES) flushBuffer();
      // conservative: assume every char may need up to 4 bytes
      int chunk = Math.min(end - i, (BUFFER_SIZE - pos) >> 2);
      int chunkEnd = i + chunk;
      // never split a surrogate pair across chunks
      if (chunkEnd < end && Character.isHighSurrogate(text.charAt(chunkEnd - 1))) {
        chunkEnd--;
      }
      while (i < chunkEnd) {
        char c = text.charAt(i++);
        if (c < 0x80) {
          utf8[pos++] = (byte) c;
        } else if (c < 0x800) {
          utf8[pos++] = (byte) (0xC0 | (c >> 6));
          utf8[pos++] = (byte) (0x80 | (c & 0x3F));
        } else if (Character.isHighSurrogate(c)) {
          if (i < chunkEnd) {
            char low = text.charAt(i);
            if (Character.isLowSurrogate(low)) {
              i++;
              int cp = Character.toCodePoint(c, low);
              utf8[pos++] = (byte) (0xF0 | (cp >> 18));
              utf8[pos++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
              utf8[pos++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
              utf8[pos++] = (byte) (0x80 | (cp & 0x3F));
              continue;
            }
          }
          writeReplacementChar();
        } else if (Character.isLowSurrogate(c)) {
          writeReplacementChar();
        } else {
          utf8[pos++] = (byte) (0xE0 | (c >> 12));
          utf8[pos++] = (byte) (0x80 | ((c >> 6) & 0x3F));
          utf8[pos++] = (byte) (0x80 | (c & 0x3F));
        }
      }
    }
  }

  private void writeReplacementChar() {
    // assumes space has been ensured already
    utf8[pos++] = (byte) 0xEF;
    utf8[pos++] = (byte) 0xBF;
    utf8[pos++] = (byte) 0xBD; // U+FFFD
  }

  private void flushBuffer() {
    if (pos > 0) {
      try {
        out.write(utf8, 0, pos);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      pos = 0;
    }
  }

  @Override
  public void close() {
    flushBuffer();
    try {
      out.close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  @Override
  public void appendCodePoint(int cp) {
    checkCodePoint(cp);
    if (cp < 0x80) {
      if (pos == BUFFER_SIZE) flushBuffer();
      utf8[pos++] = (byte) cp;
    } else if (cp < 0x800) {
      if (pos > BUFFER_SIZE - 2) flushBuffer();
      utf8[pos++] = (byte) (0xC0 | (cp >> 6));
      utf8[pos++] = (byte) (0x80 | (cp & 0x3F));
    } else if (cp >= 0xD800 && cp <= 0xDFFF) {
      if (pos > BUFFER_SIZE - 3) flushBuffer();
      writeReplacementChar();
    } else if (cp < 0x10000) {
      if (pos > BUFFER_SIZE - 3) flushBuffer();
      utf8[pos++] = (byte) (0xE0 | (cp >> 12));
      utf8[pos++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
      utf8[pos++] = (byte) (0x80 | (cp & 0x3F));
    } else {
      if (pos > BUFFER_SIZE - 4) flushBuffer();
      utf8[pos++] = (byte) (0xF0 | (cp >> 18));
      utf8[pos++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
      utf8[pos++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
      utf8[pos++] = (byte) (0x80 | (cp & 0x3F));
    }
  }

  /**
   * Writes the given value as a JSON string including the surrounding double quotes, or as the
   * literal {@code null} when {@code value} is {@code null}.
   *
   * <p>The implementation first scans for the first character that {@link
   * Appender#needsJsonEscaping(char) needs escaping}. If no such character exists, the whole
   * value is written in one {@link #appendEncoded(CharSequence, int, int)} call. Otherwise each
   * maximal run of clean characters is written via {@code appendEncoded} and each escapable run is
   * written as ASCII escape sequences via {@link #writeEscapeAsAscii(char)}. In both cases the
   * surrounding quotes are written directly into the buffer.
   *
   * <p>The method never allocates an intermediate {@code char[]}, {@code StringBuilder}, or
   * {@code String}.
   */
  @Override
  public void appendJsonEscaped(CharSequence value) {
    if (value == null) {
      appendAscii("null");
      return;
    }
    int len = value.length();
    int i = 0;
    while (i < len && !needsJsonEscaping(value.charAt(i))) i++;
    if (pos > BUFFER_SIZE - 2) flushBuffer();
    utf8[pos++] = '"';
    if (i == len) {
      appendEncoded(value, 0, len); // bulk UTF-8
      if (pos > BUFFER_SIZE - 1) flushBuffer();
      utf8[pos++] = '"';
      return;
    }
    int start = 0;
    while (i < len) {
      appendEncoded(value, start, i);
      while (i < len && needsJsonEscaping(value.charAt(i))) {
        writeEscapeAsAscii(value.charAt(i++)); // writes 2..6 ASCII bytes
      }
      start = i;
      while (i < len && !needsJsonEscaping(value.charAt(i))) i++;
    }
    appendEncoded(value, start, len);
    if (pos > BUFFER_SIZE - 1) flushBuffer();
    utf8[pos++] = '"';
  }

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  /**
   * Writes the JSON escape sequence for the given character as ASCII bytes directly into the
   * buffer.
   *
   * <p>The escape sequences are {@code \"}, {@code \\}, {@code \n}, {@code \r}, {@code \t},
   * {@code \b}, {@code \f}, and {@code \}{@code uXXXX} for all other escapable characters. All
   * sequences are pure ASCII and are therefore safe to write without any charset encoding step.
   *
   * <p>The method reserves {@code BUFFER_SIZE - 6} bytes before writing so that the longest
   * sequence ({@code \}{@code uXXXX}, 6 bytes) always fits after a single {@link #flushBuffer()}.
   * It is the caller's responsibility to ensure that {@link Appender#needsJsonEscaping(char)}
   * returned {@code true} for {@code c}.
   */
  private void writeEscapeAsAscii(char c) {
    if (pos > BUFFER_SIZE - 6) flushBuffer(); // uXXXX worst case
    utf8[pos++] = '\\';
    switch (c) {
      case '"' -> utf8[pos++] = '"';
      case '\\' -> utf8[pos++] = '\\';
      case '\n' -> utf8[pos++] = 'n';
      case '\r' -> utf8[pos++] = 'r';
      case '\t' -> utf8[pos++] = 't';
      case '\b' -> utf8[pos++] = 'b';
      case '\f' -> utf8[pos++] = 'f';
      default -> {
        utf8[pos++] = 'u';
        utf8[pos++] = (byte) HEX[(c >> 12) & 0xF];
        utf8[pos++] = (byte) HEX[(c >> 8) & 0xF];
        utf8[pos++] = (byte) HEX[(c >> 4) & 0xF];
        utf8[pos++] = (byte) HEX[c & 0xF];
      }
    }
  }
}
