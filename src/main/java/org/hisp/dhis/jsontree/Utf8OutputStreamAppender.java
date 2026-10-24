package org.hisp.dhis.jsontree;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;

import static org.hisp.dhis.jsontree.Appender.checkCodePoint;
import static org.hisp.dhis.jsontree.Appender.checkRange;
import static org.hisp.dhis.jsontree.Appender.needsJsonEscaping;

final class Utf8OutputStreamAppender implements Appender {

  private static final int BUFFER_SIZE = 8192;

  private final OutputStream out;
  private final byte[] bytes = new byte[BUFFER_SIZE];
  private int pos;

  Utf8OutputStreamAppender(OutputStream out) {
    this.out = out;
  }

  @Override
  public void appendAscii(char c) {
    if (pos == BUFFER_SIZE) flushBuffer();
    bytes[pos++] = (byte) c; // ASCII contract
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
      while (i < iEnd) bytes[j++] = (byte) text.charAt(i++);
      pos = j;
    }
  }

  @Override
  public void appendEncoded(char c) {
    if (c < 0x80) {
      if (pos == BUFFER_SIZE) flushBuffer();
      bytes[pos++] = (byte) c;
    } else if (c < 0x800) {
      if (pos > BUFFER_SIZE - 2) flushBuffer();
      bytes[pos++] = (byte) (0xC0 | (c >> 6));
      bytes[pos++] = (byte) (0x80 | (c & 0x3F));
    } else if (Character.isSurrogate(c)) {
      if (pos > BUFFER_SIZE - 3) flushBuffer();
      writeReplacementChar();
    } else {
      if (pos > BUFFER_SIZE - 3) flushBuffer();
      bytes[pos++] = (byte) (0xE0 | (c >> 12));
      bytes[pos++] = (byte) (0x80 | ((c >> 6) & 0x3F));
      bytes[pos++] = (byte) (0x80 | (c & 0x3F));
    }
  }

  @Override
  public void appendEncoded(CharSequence text, int start, int end) {
    if (text == null) {
      appendAscii("null", start, end);
      return;
    }
    checkRange(text, start, end);
    int i = start;
    while (i < end) {
      if (pos >= BUFFER_SIZE - 4) flushBuffer(); // reserve 4 bytes
      char c = text.charAt(i++);
      if (c < 0x80) { // ASCII fast path
        bytes[pos++] = (byte) c;
      } else if (c < 0x800) { // 2-byte
        bytes[pos++] = (byte) (0xC0 | (c >> 6));
        bytes[pos++] = (byte) (0x80 | (c & 0x3F));
      } else if (Character.isHighSurrogate(c)) {
        if (i < end) {
          char low = text.charAt(i);
          if (Character.isLowSurrogate(low)) {
            i++;
            int cp = Character.toCodePoint(c, low);
            bytes[pos++] = (byte) (0xF0 | (cp >> 18));
            bytes[pos++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
            bytes[pos++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
            bytes[pos++] = (byte) (0x80 | (cp & 0x3F));
            continue;
          }
        }
        writeReplacementChar(); // unpaired high surrogate
      } else if (Character.isLowSurrogate(c)) {
        writeReplacementChar(); // unpaired low surrogate
      } else { // 3-byte BMP
        bytes[pos++] = (byte) (0xE0 | (c >> 12));
        bytes[pos++] = (byte) (0x80 | ((c >> 6) & 0x3F));
        bytes[pos++] = (byte) (0x80 | (c & 0x3F));
      }
    }
  }

  private void writeReplacementChar() {
    // assumes space has been ensured already
    bytes[pos++] = (byte) 0xEF;
    bytes[pos++] = (byte) 0xBF;
    bytes[pos++] = (byte) 0xBD; // U+FFFD
  }

  private void flushBuffer() {
    if (pos > 0) {
      try {
        out.write(bytes, 0, pos);
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
      bytes[pos++] = (byte) cp;
    } else if (cp < 0x800) {
      if (pos > BUFFER_SIZE - 2) flushBuffer();
      bytes[pos++] = (byte) (0xC0 | (cp >> 6));
      bytes[pos++] = (byte) (0x80 | (cp & 0x3F));
    } else if (cp >= 0xD800 && cp <= 0xDFFF) {
      if (pos > BUFFER_SIZE - 3) flushBuffer();
      writeReplacementChar();
    } else if (cp < 0x10000) {
      if (pos > BUFFER_SIZE - 3) flushBuffer();
      bytes[pos++] = (byte) (0xE0 | (cp >> 12));
      bytes[pos++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
      bytes[pos++] = (byte) (0x80 | (cp & 0x3F));
    } else {
      if (pos > BUFFER_SIZE - 4) flushBuffer();
      bytes[pos++] = (byte) (0xF0 | (cp >> 18));
      bytes[pos++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
      bytes[pos++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
      bytes[pos++] = (byte) (0x80 | (cp & 0x3F));
    }
  }

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
    bytes[pos++] = '"';
    if (i == len) {
      appendEncoded(value, 0, len); // bulk UTF-8
      if (pos > BUFFER_SIZE - 1) flushBuffer();
      bytes[pos++] = '"';
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
    bytes[pos++] = '"';
  }

  private static final char[] HEX = "0123456789ABCDEF".toCharArray();

  private void writeEscapeAsAscii(char c) {
    if (pos > BUFFER_SIZE - 6) flushBuffer(); // uXXXX worst case
    bytes[pos++] = '\\';
    switch (c) {
      case '"' -> bytes[pos++] = '"';
      case '\\' -> bytes[pos++] = '\\';
      case '\n' -> bytes[pos++] = 'n';
      case '\r' -> bytes[pos++] = 'r';
      case '\t' -> bytes[pos++] = 't';
      case '\b' -> bytes[pos++] = 'b';
      case '\f' -> bytes[pos++] = 'f';
      default -> {
        bytes[pos++] = 'u';
        bytes[pos++] = (byte) HEX[(c >> 12) & 0xF];
        bytes[pos++] = (byte) HEX[(c >> 8) & 0xF];
        bytes[pos++] = (byte) HEX[(c >> 4) & 0xF];
        bytes[pos++] = (byte) HEX[c & 0xF];
      }
    }
  }
}
