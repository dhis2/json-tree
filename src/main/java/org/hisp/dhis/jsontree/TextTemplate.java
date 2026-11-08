package org.hisp.dhis.jsontree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * A general-purpose text processor that uses {@code @directive} lines to handles conditional blocks,
 * replacement blocks, reorder blocks and insertions.
 *
 * <h3>Built-in directives</h3>
 *
 * <pre>
 *   -- @if {expr} ... [-- @else ...] -- @end
 *   -- @replace {expr} [replacement] ... -- @end
 *   -- @reorder :name ... -- @end     (one or more consecutive siblings)
 *   -- @insert :name
 * </pre>
 *
 * An <code>{expr}</code> are one or more <code>:name</code> references combined
 * with <code>&&</code> and <code>||</code> (and binding stronger).
 *
 * <p>Directive lines are recognised by their {@code prefix} (usually the line-comment marker of the
 * host language, e.g. {@code "--"} for SQL). A directive may be chained onto the same line after a
 * closing or branching directive by repeating the prefix, e.g. {@code --@end --@if :foo}. Anything
 * other than whitespace or another prefixed directive after an {@code @else} or {@code @end} is an
 * error.
 *
 * <p>Consecutive {@code @reorder :name ... @end} blocks form a reorder sequence: they are emitted
 * in the order given by {@link #reorders} (name → ordinal), not in template order. The blocks must
 * be adjacent; any non-{@code @reorder} line ends the sequence.
 *
 * @author Jan Bernitt
 * @since 1.13
 */
public record TextTemplate(
    CharSequence prefix,
    Predicate<String> tests,
    Function<String, Text> inserts,
    ToIntFunction<String> reorders) {

  public Text render(Text template) {
    TextBuilder out = new TextBuilder(template.length());
    DirectiveLine resume = renderBlock(template, 0, out, true);
    if (resume != null) {
      String msg =
          resume.type() == Directive.END
              ? "@end without opening directive"
              : "Closing @end is missing for: " + resume.type();
      throw new IllegalStateException(msg);
    }
    return out.textValue();
  }

  private enum Directive {
    IF,
    ELSE,
    END,
    INSERT,
    REPLACE,
    REORDER;

    static Directive of(Text name) {
      if (name == null) return null;
      if (name.contentEquals("if")) return IF;
      if (name.contentEquals("else")) return ELSE;
      if (name.contentEquals("end")) return END;
      if (name.contentEquals("insert")) return INSERT;
      if (name.contentEquals("replace")) return REPLACE;
      if (name.contentEquals("reorder")) return REORDER;
      throw new IllegalStateException("Unknown directive @" + name);
    }

    @Override
    public String toString() {
      return "@" + name().toLowerCase(Locale.ROOT);
    }
  }

  /**
   * A directive line whose shape has already been validated against its {@link Directive} type,
   * together with the absolute position from which its parent should continue. {@code name} is
   * populated for {@code @insert}, {@code @reorder} and {@code @replace :name}; {@code expr} for
   * {@code @if} and {@code @replace <expr> = …}; {@code replacement} only for the latter.
   * {@code resumePos} is only consumed when this record is returned from {@link #renderBlock}
   * (i.e. for {@link Directive#ELSE} and {@link Directive#END}).
   */
  private record DirectiveLine(Directive type, String name, Text expr, Text replacement, int resumePos) {}

  private record ReorderBlock(String name, int startPos, int endPos) {}

  /**
   * @return the directive line that closed this block ({@link Directive#ELSE} or {@link
   *     Directive#END}) or {@code null} if the block ended at EOF
   */
  private DirectiveLine renderBlock(Text input, int startPos, TextBuilder out, boolean emit) {
    int len = input.length();
    int pos = startPos;
    outer:
    while (pos < len) {
      Text line = lineOf(input, pos);
      int nextLineStart = nextLineStart(input, pos);

      DirectiveLine dl = parseDirectiveLine(line, pos, nextLineStart);

      if (dl == null) {
        if (emit) {
          out.appendEncoded(line);
          out.appendAscii('\n');
        }
        pos = nextLineStart;
        continue;
      }

      switch (dl.type()) {
        case IF -> {
          pos = renderIf(input, out, emit, dl, line, nextLineStart).resumePos();
          continue outer;
        }
        case ELSE, END -> {
          return dl;
        }
        case INSERT -> renderInsert(out, emit, dl);
        case REPLACE -> {
          pos = renderReplace(input, out, emit, dl, line, nextLineStart).resumePos();
          continue outer;
        }
        case REORDER -> {
          pos = renderReorder(input, out, emit, dl, line, nextLineStart).resumePos();
          continue outer;
        }
      }
      pos = nextLineStart;
    }
    return null;
  }

  private void renderInsert(TextBuilder out, boolean emit, DirectiveLine dl) {
    if (emit) appendNonEmpty(out, inserts.apply(dl.name()));
  }

  private DirectiveLine renderIf(
      Text input, TextBuilder out, boolean emit, DirectiveLine dl, Text line, int nextLineStart) {
    boolean satisfied = evalOr(dl.expr());
    DirectiveLine inner = renderBlock(input, nextLineStart, out, emit && satisfied);
    if (inner != null && inner.type() == Directive.END) return inner;
    if (inner != null && inner.type() == Directive.ELSE) {
      DirectiveLine after = renderBlock(input, inner.resumePos(), out, emit && !satisfied);
      if (after != null && after.type() == Directive.END) return after;
      throw new IllegalStateException(
          "@else block did not close with @end but with "
              + (after == null ? null : after.type())
              + ": "
              + line);
    }
    throw new IllegalStateException(
        "@if block did not close with @end but with "
            + (inner == null ? null : inner.type())
            + ": "
            + line);
  }

  private DirectiveLine renderReplace(
      Text input, TextBuilder out, boolean emit, DirectiveLine dl, Text line, int nextLineStart) {
    boolean replace = dl.replacement() != null ? evalOr(dl.expr()) : tests.test(dl.name());

    if (replace && emit) {
      if (dl.replacement() != null) {
        appendNonEmpty(out, dl.replacement());
      } else {
        appendNonEmpty(out, inserts.apply(dl.name()));
      }
    }

    DirectiveLine inner = renderBlock(input, nextLineStart, out, !replace && emit);
    if (inner == null || inner.type() != Directive.END) {
      throw new IllegalStateException(
          "@replace block did not close with @end but with "
              + (inner == null ? null : inner.type())
              + ": "
              + line);
    }
    return inner;
  }

  private DirectiveLine renderReorder(
      Text input, TextBuilder out, boolean emit, DirectiveLine dl, Text line, int nextLineStart) {
    int len = input.length();
    List<ReorderBlock> blocks = new ArrayList<>();

    blocks.add(collectReorder(input, out, dl.name(), nextLineStart, line));

    while (blocks.get(blocks.size() - 1).endPos() < len) {
      int pos = blocks.get(blocks.size() - 1).endPos();
      Text next = lineOf(input, pos);
      int nextStart = nextLineStart(input, pos);
      DirectiveLine nextDl = parseDirectiveLine(next, pos, nextStart);
      if (nextDl == null || nextDl.type() != Directive.REORDER) break;
      blocks.add(collectReorder(input, out, nextDl.name(), nextStart, next));
    }

    int end = blocks.get(blocks.size() - 1).endPos();

    if (emit) {
      blocks.sort(Comparator.comparingInt(b -> reorders.applyAsInt(b.name())));
      for (ReorderBlock block : blocks) {
        renderBlock(input, block.startPos(), out, true);
      }
    }
    return new DirectiveLine(Directive.END, null, null, null, end);
  }

  private ReorderBlock collectReorder(
      Text input, TextBuilder out, String name, int startPos, Text opener) {
    DirectiveLine resume = renderBlock(input, startPos, out, false);
    if (resume == null || resume.type() != Directive.END) {
      throw new IllegalStateException(
          "@reorder block '"
              + name
              + "' did not close with @end but with "
              + (resume == null ? null : resume.type())
              + ": "
              + opener);
    }
    return new ReorderBlock(name, startPos, resume.resumePos());
  }

  private static void appendNonEmpty(TextBuilder out, Text inserted) {
    if (inserted != null && !inserted.isEmpty()) {
      out.appendEncoded(inserted);
      out.appendAscii('\n');
    }
  }

  /*
  Expressions
   */

  private boolean evalOr(Text expr) {
    int i = 0, len = expr.length(), start = 0;
    while (i < len - 1) {
      if (expr.charAt(i) == '|' && expr.charAt(i + 1) == '|') {
        if (evalAnd(expr.subSequence(start, i).trim())) return true;
        i += 2;
        start = i;
      } else {
        i++;
      }
    }
    return evalAnd(expr.subSequence(start, len).trim());
  }

  private boolean evalAnd(Text expr) {
    int i = 0, len = expr.length(), start = 0;
    while (i < len - 1) {
      if (expr.charAt(i) == '&' && expr.charAt(i + 1) == '&') {
        if (!evalTest(expr.subSequence(start, i).trim())) return false;
        i += 2;
        start = i;
      } else {
        i++;
      }
    }
    return evalTest(expr.subSequence(start, len).trim());
  }

  /**
   * {@code ref} has already been validated by {@link #parseExpr(Directive, Text)}: it is
   * {@code ':'} followed by one or more alphanumeric characters.
   */
  private boolean evalTest(Text ref) {
    return tests.test(ref.subSequence(1).toString());
  }

  /*
  Parsing (+ validation)
   */

  /**
   * Parses {@code line} into a {@link DirectiveLine}, validating the arguments required by its
   * directive type at construction and computing the position from which the parent should resume.
   * Returns {@code null} if {@code line} is not a directive line. {@code pos} is the absolute
   * position of the start of {@code line} in the input, {@code nextLineStart} the position just
   * past its {@code \n} (or EOF).
   */
  private DirectiveLine parseDirectiveLine(Text line, int pos, int nextLineStart) {
    if (!isDirectiveLine(line)) return null;
    Directive type = Directive.of(parseDirectiveName(line));
    if (type == null) return null;
    return switch (type) {
      case IF -> parseIf(line, nextLineStart);
      case ELSE, END -> parseElseEnd(type, line, pos, nextLineStart);
      case INSERT -> parseInsert(line, nextLineStart);
      case REPLACE -> parseReplace(line, nextLineStart);
      case REORDER -> parseReorder(line, nextLineStart);
    };
  }

  public static Text parseDirectiveName(Text line) {
    int start = line.indexOf('@');
    if (start < 0) return null;
    int end = start + 1;
    while (end < line.length() && isAlphanumeric(line.charAt(end))) end++;
    if (end == start + 1) return null;
    return line.subSequence(start + 1, end);
  }

  private boolean isDirectiveLine(Text line) {
    int len = line.length();
    int i = skipIndent(line, 0);
    if (!line.startsWith(prefix, i)) return false;
    i += prefix.length();
    i = skipIndent(line, i);
    if (i >= len || line.charAt(i) != '@') return false;
    i++; // skip @
    int s = i;
    i = skipAlphanumeric(line, s);
    return i > s && (i >= len || isIndent(line.charAt(i)));
  }

  private static DirectiveLine parseIf(Text line, int nextLineStart) {
    Text expr = line.subSequence(skipDirectiveName(line), line.length()).trim();
    parseExpr(Directive.IF, expr);
    return new DirectiveLine(Directive.IF, null, expr, null, nextLineStart);
  }

  /**
   * {@code @else} and {@code @end} are the only directives that may be chained onto the same line
   * by repeating the {@code prefix}. Anything after them must therefore be either whitespace only,
   * or another directive line (e.g. {@code --@end --@if :foo}); anything else is an error.
   */
  private DirectiveLine parseElseEnd(Directive type, Text line, int pos, int nextLineStart) {
    int nameEnd = skipDirectiveName(line);
    if (nameEnd >= line.length()) {
      return new DirectiveLine(type, null, null, null, nextLineStart);
    }
    Text tail = line.subSequence(nameEnd, line.length());
    if (skipIndent(tail, 0) >= tail.length()) {
      // only trailing whitespace: nothing meaningful to emit, resume on the next line
      return new DirectiveLine(type, null, null, null, nextLineStart);
    }
    if (!isDirectiveLine(tail)) {
      throw new IllegalStateException(type + " unexpected content after directive: " + line);
    }
    // chained directive: resume at the start of the tail so renderBlock sees and parses it
    return new DirectiveLine(type, null, null, null, pos + nameEnd);
  }

  /** {@code -- @insert :name} — a {@code :name} and nothing else. */
  private static DirectiveLine parseInsert(Text line, int nextLineStart) {
    int start = directiveArgumentStart(line);
    String name = parseNameRef(Directive.INSERT, line, start);
    requireEndOfLine(Directive.INSERT, line, start + 1 + name.length());
    return new DirectiveLine(Directive.INSERT, name, null, null, nextLineStart);
  }

  /** {@code -- @reorder :name} — a {@code :name} and nothing else. */
  private static DirectiveLine parseReorder(Text line, int nextLineStart) {
    int start = directiveArgumentStart(line);
    String name = parseNameRef(Directive.REORDER, line, start);
    requireEndOfLine(Directive.REORDER, line, start + 1 + name.length());
    return new DirectiveLine(Directive.REORDER, name, null, null, nextLineStart);
  }

  /**
   * {@code -- @replace :name} (a {@code :name} and nothing else) or
   * {@code -- @replace <expr> = <replacement>} (an expression, then {@code =}, then the replacement
   * text, which may be empty).
   */
  private static DirectiveLine parseReplace(Text line, int nextLineStart) {
    int afterName = skipDirectiveName(line);
    int assign = line.indexOf('=', afterName);
    if (assign < 0) {
      int start = directiveArgumentStart(line);
      String name = parseNameRef(Directive.REPLACE, line, start);
      requireEndOfLine(Directive.REPLACE, line, start + 1 + name.length());
      return new DirectiveLine(Directive.REPLACE, name, null, null, nextLineStart);
    }
    Text expr = line.subSequence(afterName, assign).trim();
    Text replacement = line.subSequence(assign + 1, line.length()).trim();
    parseExpr(Directive.REPLACE, expr);
    return new DirectiveLine(Directive.REPLACE, null, expr, replacement, nextLineStart);
  }

  /**
   * Validates that {@code expr} is a boolean expression of the form
   * {@code :name (('&&'|'||') :name)*}, where {@code name} is one or more alphanumeric characters.
   */
  private static void parseExpr(Directive directive, Text expr) {
    int i = 0, len = expr.length();
    boolean expectTerm = true;
    boolean inTerm = false;
    while (i < len) {
      char c = expr.charAt(i++);
      if (c == '&' || c == '|') {
        if (i >= len || expr.charAt(i) != c)
          throw new IllegalStateException(
              "Single '" + c + "' in " + directive + " expression (use '" + c + c + "'): " + expr);
        if (expectTerm)
          throw new IllegalStateException(
              "Unexpected '" + c + c + "' at start of " + directive + " expression: " + expr);
        expectTerm = true;
        inTerm = false;
        i++; // skip second symbol
      } else if (c == ' ' || c == '\t') {
        if (inTerm) inTerm = false;
      } else {
        if (!expectTerm && !inTerm)
          throw new IllegalStateException(
              "Missing operator between terms in " + directive + " (use && or ||): " + expr);
        if (expectTerm && c != ':')
          throw new IllegalStateException(directive + " requires ':name' argument: " + expr);
        if (!expectTerm && !isAlphanumeric(c))
          throw new IllegalStateException(
              "Name must be alphanumeric in " + directive + " expression: " + expr);
        expectTerm = false;
        inTerm = true;
      }
    }
    if (expectTerm)
      throw new IllegalStateException(directive + " expression ends unexpectedly: " + expr);
  }

  /** @return the position of the first non-whitespace character after the directive name */
  private static int directiveArgumentStart(Text line) {
    return skipIndent(line, skipDirectiveName(line));
  }

  /** Fails unless everything from {@code pos} to the end of {@code line} is whitespace. */
  private static void requireEndOfLine(Directive directive, Text line, int pos) {
    if (skipIndent(line, pos) < line.length())
      throw new IllegalStateException(directive + " unexpected content after ':name': " + line);
  }

  /**
   * Parses a {@code :name} starting at {@code start} in {@code line}, validating that
   * {@code line} has a {@code ':'} at {@code start} and a non-empty alphanumeric run after it. The
   * character after the run must be whitespace or EOF, so {@code :foo-bar} is rejected as an
   * invalid name (rather than silently parsing as {@code foo}).
   *
   * @return the name (without the leading {@code ':'})
   */
  private static String parseNameRef(Directive directive, Text line, int start) {
    if (start >= line.length() || line.charAt(start) != ':')
      throw new IllegalStateException(directive + " requires ':name' argument: " + line);
    int end = skipAlphanumeric(line, start + 1);
    if (end == start + 1)
      throw new IllegalStateException(directive + " argument must have a letter: " + line);
    if (end < line.length() && !isIndent(line.charAt(end)))
      throw new IllegalStateException(directive + " name must be alphanumeric: " + line);
    return line.subSequence(start + 1, end).toString();
  }

  // ---- shared helpers ----

  /** Line content with any trailing {@code \r} removed. */
  private static Text lineOf(Text input, int pos) {
    int nl = input.indexOf('\n', pos);
    int end = nl < 0 ? input.length() : nl;
    if (end > pos && input.charAt(end - 1) == '\r') end--;
    return input.subSequence(pos, end);
  }

  /** Position just past the {@code \n} (or EOF). */
  private static int nextLineStart(Text input, int pos) {
    int nl = input.indexOf('\n', pos);
    return nl < 0 ? input.length() : nl + 1;
  }

  /** @return the position just past the {@code @name} part of a directive line */
  private static int skipDirectiveName(Text line) {
    return skipAlphanumeric(line, line.indexOf('@') + 1);
  }

  private static int skipAlphanumeric(Text line, int i) {
    int len = line.length();
    while (i < len && isAlphanumeric(line.charAt(i))) i++;
    return i;
  }

  private static int skipIndent(Text line, int i) {
    int len = line.length();
    while (i < len && isIndent(line.charAt(i))) i++;
    return i;
  }

  private static boolean isAlphanumeric(char c) {
    return 'a' <= c && c <= 'z' || 'A' <= c && c <= 'Z' || '0' <= c && c <= '9';
  }

  private static boolean isIndent(char c) {
    return c == ' ' || c == '\t';
  }

}