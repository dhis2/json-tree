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
 * <p>Consecutive {@code @reorder :name ... @end} blocks form a reorder sequence: they are emitted
 * in the order given by {@link #reorders} (name → ordinal), not in template order. The blocks must
 * be adjacent; any non-{@code @reorder} line ends the sequence.
 *
 * @author Jan Bernitt
 * @since 1.13
 */
public record TextTemplate(
    Predicate<Text> directives,
    Predicate<String> tests,
    Function<String, Text> inserts,
    ToIntFunction<String> reorders) {

  public Text render(Text template) {
    TextBuilder out = new TextBuilder((int) (template.length() * 1.5));
    End end = processBlock(template, 0, out, true);
    if (end.type() != null) {
      String msg =
          end.type == Directive.END
              ? "@end without opening directive"
              : "Closing @end is missing for: " + end.type;
      throw new IllegalStateException(msg);
    }
    return out.textValue();
  }

  public static Predicate<Text> isDirectiveLine(CharSequence prefix) {
    return line -> {
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
    };
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

  private record End(Directive type, int pos) {}

  private record ReorderBlock(String name, int startPos, int endPos) {}

  private End processBlock(Text input, int startPos, TextBuilder out, boolean emit) {
    int len = input.length();
    int pos = startPos;
    outer:
    while (pos < len) {
      Text line = lineOf(input, pos);
      int nextLineStart = nextLineStart(input, pos);

      Directive directive = null;
      if (directives.test(line)) directive = Directive.of(lineDirectiveName(line));

      if (directive == null) {
        if (emit) {
          out.appendEncoded(line);
          out.appendAscii('\n');
        }
        pos = nextLineStart;
        continue;
      }

      switch (directive) {
        case IF -> {
          pos = processIf(input, out, emit, line, nextLineStart).pos();
          continue outer;
        }
        case ELSE, END -> {
          int nameEnd = line.indexOf('@') + 1;
          while (nameEnd < line.length() && isAlphanumeric(line.charAt(nameEnd))) nameEnd++;
          int resume = nameEnd < line.length() && isIndent(line.charAt(nameEnd))
              ? pos + nameEnd
              : nextLineStart(input, pos);
          return new End(directive, resume);
        }
        case INSERT -> processInsert(out, emit, line);
        case REPLACE -> {
          pos = processReplace(input, out, emit, line, nextLineStart).pos();
          continue outer;
        }
        case REORDER -> {
          pos = processReorder(input, out, emit, line, nextLineStart).pos();
          continue outer;
        }
        default -> throw new IllegalStateException("Unknown directive @" + directive + ": " + line);
      }
      pos = nextLineStart;
    }
    return new End(null, len);
  }

  private void processInsert(TextBuilder out, boolean emit, Text line) {
    String name = requireDirectiveNameRef(Directive.INSERT, line);
    if (emit) appendNonEmpty(out, inserts.apply(name));
  }

  private static void appendNonEmpty(TextBuilder out, Text inserted) {
    if (inserted != null && !inserted.isEmpty()) {
      out.appendEncoded(inserted);
      out.appendAscii('\n');
    }
  }

  /**
   * Processes a {@code @if :cond ... @end} block, including an optional {@code @else} branch. The
   * returned {@link End} always has type {@link Directive#END}; if the inner block ends with
   * anything else (an unmatched {@code @else}, the top of the input, or a stray {@code @end}), this
   * method throws. Since {@code @end} closes the innermost open block regardless of whether it was
   * opened by {@code @if}, {@code @replace} or {@code @reorder}, an {@code @if} nesting once
   * started can only be closed by its own matching {@code @end}.
   */
  private End processIf(Text input, TextBuilder out, boolean emit, Text line, int nextLineStart) {
    boolean satisfied = evalExpr(Directive.IF, line.subSequence(line.indexOf("@if")+3).trim());
    End inner = processBlock(input, nextLineStart, out, emit && satisfied);
    if (inner.type() == Directive.END) return inner;
    if (inner.type() == Directive.ELSE) {
      End after = processBlock(input, inner.pos(), out, emit && !satisfied);
      if (after.type() == Directive.END) return after;
      throw new IllegalStateException(
          "@else block did not close with @end but with " + after.type() + ": " + line);
    }
    throw new IllegalStateException(
        "@if block did not close with @end but with " + inner.type() + ": " + line);
  }

  private End processReplace(
      Text input, TextBuilder out, boolean emit, Text line, int nextLineStart) {
    int assign = line.indexOf('=');
    boolean noReplacement = assign < 0;
    Text expr = line.subSequence(line.indexOf("@replace")+8, noReplacement ? line.length() : assign).trim();
    if (noReplacement && (expr.contains('&') || expr.contains('|')))
      throw new IllegalStateException("@replace without replacement must use a :name only but got: "+expr);
    boolean replace = evalExpr(Directive.REPLACE, expr);

    if (replace && emit) {
      if (noReplacement) {
        appendNonEmpty(out, inserts.apply(requireDirectiveNameRef(Directive.REPLACE, expr)));
      } else {
        appendNonEmpty(out, line.subSequence(assign+1).trim());
      }
    }

    End inner = processBlock(input, nextLineStart, out, !replace && emit);
    if (inner.type() != Directive.END) {
      throw new IllegalStateException(
          "@replace block did not close with @end but with " + inner.type() + ": " + line);
    }
    return inner;
  }

  private End processReorder(
      Text input, TextBuilder out, boolean emit, Text line, int nextLineStart) {
    int len = input.length();
    List<ReorderBlock> blocks = new ArrayList<>();

    String firstName = requireDirectiveNameRef(Directive.REORDER, line);
    blocks.add(collectReorder(input, out, firstName, nextLineStart, line));

    while (blocks.get(blocks.size() - 1).endPos() < len) {
      int pos = blocks.get(blocks.size() - 1).endPos();
      Text next = lineOf(input, pos);
      if (!directives.test(next) || Directive.of(lineDirectiveName(next)) != Directive.REORDER) {
        break;
      }
      String name = requireDirectiveNameRef(Directive.REORDER, next);
      int startPos = nextLineStart(input, pos);
      blocks.add(collectReorder(input, out, name, startPos, next));
    }

    // get end of last before reordering
    int end = blocks.get(blocks.size() - 1).endPos();

    if (emit) {
      blocks.sort(Comparator.comparingInt(b -> reorders.applyAsInt(b.name())));
      for (ReorderBlock block : blocks) {
        processBlock(input, block.startPos(), out, true);
      }
    }
    return new End(Directive.END, end);
  }

  /** Consumes a single {@code @reorder} body (from {@code startPos} through its {@code @end}). */
  private ReorderBlock collectReorder(
      Text input, TextBuilder out, String name, int startPos, Text opener) {
    End end = processBlock(input, startPos, out, false);
    if (end.type() != Directive.END) {
      throw new IllegalStateException(
          "@reorder block '"
              + name
              + "' did not close with @end but with "
              + end.type()
              + ": "
              + opener);
    }
    return new ReorderBlock(name, startPos, end.pos());
  }

  /*
  Expressions
   */

  private boolean evalExpr(Directive directive, Text expr) {
    validateExpr(directive, expr);
    return evalOr(expr);
  }

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

  private boolean evalTest(Text ref) {
    // ref is guaranteed by validateExpr to start with ':'
    for (int i = 1; i < ref.length(); i++) {
      if (!isAlphanumeric(ref.charAt(i)))
        throw new IllegalStateException("Name must be alphanumeric: " + ref);
    }
    return tests.test(ref.subSequence(1).toString());
  }

  private void validateExpr(Directive directive, Text expr) {
    int i = 0, len = expr.length();
    boolean expectTerm = true;
    boolean inTerm = false;
    while (i < len) {
      char c = expr.charAt(i++);
      if (c == '&' || c == '|') {
        if (i >= len || expr.charAt(i) != c)
          throw new IllegalStateException(
              "Single '" + c + "' in "+directive+" expression (use '" + c + c + "'): " + expr);
        if (expectTerm)
          throw new IllegalStateException(
              "Unexpected '" + c + c + "' at start of "+directive+" expression: " + expr);
        expectTerm = true;
        inTerm = false;
        i ++; // skip second symbol
      } else if (c == ' ' || c == '\t') {
        if (inTerm) inTerm = false;
      } else {
        if (!expectTerm && !inTerm)
          throw new IllegalStateException(
              "Missing operator between terms in "+directive+" (use && or ||): " + expr);
        if (expectTerm && c != ':')
          throw new IllegalStateException(directive+" requires ':name' argument: " + expr);
        expectTerm = false;
        inTerm = true;
      }
    }
    if (expectTerm)
      throw new IllegalStateException(directive+ " expression ends unexpectedly: " + expr);
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

  /**
   * @param line a line of text
   * @return the directive name or null if no directive is contained
   */
  public static Text lineDirectiveName(Text line) {
    int start = line.indexOf('@');
    if (start < 0) return null;
    int end = start + 1;
    while (end < line.length() && isAlphanumeric(line.charAt(end))) end++;
    if (end == start + 1) return null;
    return line.subSequence(start + 1, end);
  }

  private static String requireDirectiveNameRef(Directive name, Text line) {
    int s = line.indexOf(':');
    if (s < 0) throw new IllegalStateException(name + " requires ':name' argument: " + line);
    int e = skipAlphanumeric(line, s + 1);
    if (e == s + 1)
      throw new IllegalStateException(name+" argument must have a letter: " + line);
    if (e < line.length() && !isIndent(line.charAt(e)))
      throw new IllegalStateException(name + " name must be alphanumeric: " + line);
    return line.subSequence(s + 1, e).toString();
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
