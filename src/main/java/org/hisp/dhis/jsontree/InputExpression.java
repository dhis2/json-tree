package org.hisp.dhis.jsontree;

import static java.lang.Character.toLowerCase;
import static java.lang.Integer.MAX_VALUE;

import java.util.List;
import java.util.stream.Stream;

/**
 * Input patterns are specifically designed to match short sequences where the relevant features to
 * match are in the ASCII range. Typical examples are numbers, like dates or telephone numbers as
 * well as alphanumeric values, like UUIDs, codes and other identifiers.
 *
 * <h3>Performance and Safety</h3>
 *
 * A key aspect of the design is that matching is allocation-free and guaranteed to be linear time.
 * Mostly that is linear to the pattern length. In case of open-ended repeats and scans this can be
 * linear to the input length as a worse case scenario. This makes it fairly safe to run against
 * user input without risking that matching causes excessive resource usage.
 *
 * <h3>Syntax</h3>
 *
 * See {@code INPUT_EXPRESSIONS.md} in the repository root for details
 *
 * @author Jan Bernitt
 * @since 1.9
 */
public record InputExpression(List<Pattern> patterns) {

  public static InputExpression of(String... patterns) {
    return new InputExpression(Stream.of(patterns).map(Pattern::of).toList());
  }

  /**
   * @param input the sequence to check
   * @return the {@link Pattern} that matches the input exactly, or null if none matches
   */
  public Pattern match(CharSequence input) {
    for (Pattern pattern : patterns) if (pattern.matches(input)) return pattern;
    return null;
  }

  /**
   * @param pattern pattern to test
   * @param input character sequence that is tested for the pattern
   * @return true, if the pattern matches the entire input exactly
   */
  public static boolean matches(String pattern, CharSequence input) {
    return matches(pattern.toCharArray(), input);
  }

  /**
   * @param pattern pattern to test
   * @param input character sequence that is tested for the pattern
   * @return true, if the pattern matches the entire input exactly
   */
  public static boolean matches(char[] pattern, CharSequence input) {
    return match(pattern, 0, pattern.length, input, 0) == input.length();
  }

  /**
   * Finds the index in the input (starting at pos) where the given slice of the pattern between
   * offset and end has matched.
   *
   * @param pattern pattern to match
   * @param offset in pattern to match
   * @param end end index in pattern where the match is considered complete
   * @param input input sequence to match against
   * @param pos offset in input to start matching
   * @return the next index in input after the pattern is matched or -1 if the pattern does not
   *     match starting at pos
   */
  public static int match(char[] pattern, int offset, int end, CharSequence input, int pos) {
    int i = offset;
    int len = input.length();
    // note that pos < len cannot be tested in the while condition
    // as there might be pattern left that can match nothing with nothing left
    while (i < end && pos >= 0) {
      int iCode = i;
      char opcode = pattern[i++];
      switch (opcode) {
        case '#': if (pos >= len || !isDigit(input.charAt(pos++))) return -1; break;
        case '@': if (pos >= len || !isIdentifier(input.charAt(pos++))) return -1; break;
        case '[': {
          if (pos >= len || !matchSet(pattern, i, input.charAt(pos++))) return -1;
          // optimisation: jumped here as unit, return directly
          if (iCode == offset && offset > 0) return pos;
          i = skipTo(']', pattern, i) + 1;
        }
        break;
        case '|': {
          pos = matchSequence(pattern, i, input, pos);
          if (pos < 0) return -1;
          // optimisation: jumped here as unit, return directly
          if (iCode == offset && offset > 0) return pos;
          i = skipTo('|', pattern, i) + 1;
        }
        break;
        case '?', '*', '+', '1','2','3','4','5','6','7','8','9': {
          pos = matchRepeat(pattern, iCode, input, pos);
          if (pos < 0) return -1;
          if (pattern[i] == '?') i++;
          i = skipUnit(pattern, i);
        }
        break;
        case '~': {
          pos = matchScan(pattern, i, input, pos);
          if (pos < 0) return -1;
          i =
              pattern[i] == '~'
                  ? skipUnit(pattern, skipUnit(pattern, i + 1))
                  : skipUnit(pattern, i);
        }
        break;
        case '{': {
          pos = matchNumericBounds(pattern, i, input, pos);
          if (pos < 0) return -1;
          i = skipUnit(pattern, i-1);
        }
        break;
        case '(', ')': break; // these are NOOPs here, just mark block start/end for repeat/scan
        default:
          if (opcode != input.charAt(pos++)) return -1;
      }
    }
    // ) at the end is special as it would be a NOOP
    if (pos >= len && i + 1 == end && pattern[i] == ')') return pos;
    return i == end ? pos : -1;
  }

  public static int matchScan(char[] pattern, int offset, CharSequence input, int pos) {
    int len = input.length();
    if (pattern[offset] == '~') {
      //~~{unit}{unit}
      int end1 = skipUnit(pattern, offset+1);
      int end2 = skipUnit(pattern, end1);
      while (pos < len) {
        int pos2 = match(pattern, end1, end2, input, pos);
        if (pos2 >= 0) return pos2;
        pos2 = match(pattern, offset+1, end1, input, pos);
        if (pos2 < 0) return -1;
        pos = pos2;
      }
      return -1;
    }
    // ~{unit}
    int end = skipUnit(pattern, offset);
    while (pos < len) {
      int pos2 = match(pattern, offset, end, input, pos);
      if (pos2 >= 0) return pos2;
      pos++;
    }
    return -1;
  }

  public static int matchRepeat(char[] pattern, int offset, CharSequence input, int pos) {
    int i = offset;
    char opcode = pattern[i++];
    int repMin = switch (opcode) {
      case '?', '*' -> 0;
      case '+' -> 1;
      default -> opcode - '0';
    };
    int repMax= switch (opcode) {
      case '?' -> 1;
      case '*', '+' -> MAX_VALUE;
      default -> opcode - '0';
    };
    if (pattern[i] == '?') {
      repMin = 0;
      i++;
    }
    int end = skipUnit(pattern, i);
    int len = input.length();
    // mandatory occurrences
    for (int r = 0; r < repMin; r++) {
      pos = match(pattern, i, end, input, pos);
      if (pos < 0) return -1; //mismatch
      if (pos >= len && r < repMin-1) return -1; // too little input
    }
    if (pos >= len) return pos;
    // optional occurrences
    for (int r = repMin; r < repMax; r++) {
      int pos2 = match(pattern, i, end, input, pos);
      if (pos2 < 0 || pos2 == pos) return pos; // no progress or mismatch => done
      pos = pos2; // made some progress
      if (pos >= len) return pos; // input exhausted; stop trying further repeats
    }
    return pos; // max-rep done successful
  }

  private static int matchNumericBounds(char[] pattern, int offset, CharSequence input, int pos) {
    int end = skipTo('}', pattern, offset);
    int offset2 = end+1;
    int end2 = skipUnit(pattern, offset2);
    int pos2 = match(pattern, offset2, end2, input, pos);
    if (pos2 < 0) return -1;
    long min = 0;
    long max = 0;
    int i = offset;
    while (i < pattern.length && isDigit(pattern[i])) {
      max *= 10;
      max += pattern[i++] - '0';
    }
    if (i < pattern.length && pattern[i] != '}') {
      min = max;
      max = 0;
      i++;
      while (i < pattern.length && isDigit(pattern[i])) {
        max *= 10;
        max += pattern[i++] - '0';
      }
    }
    long val = 0;
    i = pos;
    while (i < pos2) {
      val *= 10;
      val += input.charAt(i++) - '0';
    }
    if (val >= min && (max== 0 || val <= max)) return pos2;
    return -1;
  }



  /**
   * <pre>
   *   |...|
   * </pre>
   */
  private static int matchSequence(char[] pattern, int offset, CharSequence input, int pos) {
    int i = offset;
    int len = input.length();
    while (i < pattern.length && pos < len && (i == offset || pattern[i] != '|')) {
      char in = input.charAt(pos++);
      char opcode = pattern[i++];
      switch (opcode) {
        case 'b': if (!isBinary(in)) return -1; break;
        case 'd', '#': if (!isDigit(in)) return -1; break;
        case 'i': if (!isIdentifier(in)) return -1; break;
        case 'u': if (!isUpperLetter(in)) return -1; break;
        case 'l': if (!isLowerLetter(in)) return -1; break;
        case 'c': if (!isLetter(in)) return -1; break;
        case 'a': if (!isAlphanumeric(in)) return -1; break;
        case 'x': if (!isHexadecimal(in)) return -1; break;
        case 's': if (!isSign(in)) return -1; break;
        case '?': break; // any character, always fine
        default:
          if (isUpperLetter(opcode)) {
            if (opcode != (in & ~0b10_0000)) return -1; break;
          } else if (isDigit(opcode)) {
            if (!isDigit(in)) return -1; // easy case
            int pos0 = pos-1;
            pos = matchNumericSequence(pattern, i-1, input, pos0);
            if (pos < 0) return -1;
            i += pos - pos0 - 1;
          } else if (isLowerLetter(opcode)) {
            throw reserved(opcode);
          } else {
            // everything else is taken literally
            if (opcode != in) return -1;
          }
      }
    }
    return pos;
  }

  private static int matchNumericSequence(char[] pattern, int offset, CharSequence input, int pos) {
    int i = offset;
    long max = pattern[i++] - '0';
    long val = input.charAt(pos++) - '0';
    int len = input.length();
    while (i < pattern.length && pattern[i] != '|' && isDigit(pattern[i]) && pos < len) {
      char d = input.charAt(pos++);
      if (!isDigit(d)) return -1;
      max *= 10;
      max += pattern[i++] - '0';
      val *= 10;
      val += d - '0';
    }
    if (i > pattern.length) return -1;
    if (pattern[i] != '|' && isDigit(pattern[i])) return -1;
    return val <= max && val >= 0 ? pos : -1;
  }


  /**
   * <pre>
   *   [...]
   * </pre>
   */
  private static boolean matchSet(char[] pattern, int offset, char in) {
    int i = offset;
    while (i < pattern.length && (i == offset || pattern[i] != ']')) {
      char opcode = pattern[i++];
      switch (opcode) {
        case 'b': if (isBinary(in)) return true; break;
        case 'd': if (isDigit(in)) return true; break;
        case 'i': if (isIdentifier(in)) return true; break;
        case 'u': if (isUpperLetter(in)) return true; break;
        case 'l': if (isLowerLetter(in)) return true; break;
        case 'c': if (isLetter(in)) return true; break;
        case 'a': if (isAlphanumeric(in)) return true; break;
        case 'x': if (isHexadecimal(in)) return true; break;
        case 's': if (isSign(in)) return true; break;
        default:
          if (isUpperLetter(opcode)) {
            if (opcode == (in & ~0b10_0000)) return true;
          } else if (isDigit(opcode)) {
            if (isDigit(in, opcode)) return true;
          } else if (isLowerLetter(opcode)) {
            throw reserved(opcode);
          }
          else {
            // everything else is taken literally
            if (opcode == in) return true;
          }
      }
    }
    return false;
  }

  private static UnsupportedOperationException reserved(char opcode) {
    return new UnsupportedOperationException("Lower case letter %s is reserved for future use as named set.".formatted(opcode));
  }

  private static boolean isSign(char c) {
    return c == '+' || c == '-';
  }

  private static boolean isBinary(char c) {
    return c == '0' || c == '1';
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }

  private static boolean isDigit(char c, char upperDigit) {
    return c >= '0' && c <= upperDigit;
  }

  private static boolean isLetter(char c) {
    return isLowerLetter(c) || isUpperLetter(c);
  }

  private static boolean isLowerLetter(char c) {
    return c >= 'a' && c <= 'z';
  }

  private static boolean isUpperLetter(char c) {
    return c >= 'A' && c <= 'Z';
  }

  private static boolean isAlphanumeric(char c) {
    return isLetter(c) || isDigit(c);
  }

  private static boolean isHexadecimal(char c) {
    return isDigit(c) || c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F';
  }

  private static boolean isIdentifier(char c) {
    return isAlphanumeric(c) || c == '_' || c == '-';
  }

  private static int skipTo(char c, char[] pattern, int offset) {
    int len = pattern.length;
    while (offset < len && pattern[offset] != c) offset++;
    return offset;
  }

  private static int skipUnit(char[] pattern, int offset) {
    return switch (pattern[offset]) {
      // +2 (+1 for opening, +1 for first character inside)
      case '|' -> skipTo('|', pattern, offset + 2) + 1;
      case '[' -> skipTo(']', pattern, offset + 2) + 1;
      case '(' -> skipTo(')', pattern, offset + 2) + 1;
      case '{' -> skipUnit(pattern, skipTo('}', pattern, offset + 2) + 1);
      default -> offset + 1;
    };
  }

  private static int skipUnit(Text pattern, int offset) {
    return switch (pattern.charAt(offset)) {
      // +2 (+1 for opening, +1 for first character inside)
      case '|' -> pattern.indexOf('|', offset + 2) + 1;
      case '[' -> pattern.indexOf(']', offset + 2) + 1;
      case '(' -> pattern.indexOf(')', offset + 2) + 1;
      case '{' -> skipUnit(pattern, pattern.indexOf('}', offset + 2) + 1);
      default -> offset + 1;
    };
  }

  /*
  Pattern (find length bounds)
   */

  /**
   * A pattern is one alternative within a multi-pattern-{@link InputExpression}.
   *
   * @param pattern the pattern to match
   * @param minLength the minimum input length required to match the pattern, -1 if not fixed
   * @param maxLength the maximum input length possible to match the pattern, -1 if not fixed
   * @implNote Using length bounds is purely a performance optimisation that makes use of the
   * fact the Input Expressions often have a clear length bounds.
   */
  public record Pattern(Text pattern, int minLength, int maxLength) {

    public static Pattern of(CharSequence pattern) {
      Text p = Text.of(pattern);
      return new Pattern(p, length(p, 0, p.length(), false), length(p, 0, p.length(), true));
    }

    public boolean matches(CharSequence input) {
      int length = input.length();
      if (minLength >= 0 && length < minLength) return false;
      if (maxLength >= 0 && length > maxLength) return false;
      return pattern.matches(input);
    }

    private static int length(Text pattern, int offset, int end, boolean max) {
      int len = 0;
      int i = offset;
      while (i < end) {
        char opcode = pattern.charAt(i++);
        switch (opcode) {
          case '#', '@': len++; break;
          case '[': len++; i = pattern.indexOf(']', i + 1) + 1; break;
          case '|': {
            int i2 = pattern.indexOf('|', i + 1) + 1;
            len += (i2 - i - 1);
            i = i2;
          }
          break;
          case '?', '*', '+': {
            if (max && (opcode == '*' || opcode == '+')) return -1; // open end
            int end2 = skipUnit(pattern, i);
            if (max || opcode == '+') { // ? max or + min => 1 times
              int len2 = length(pattern, i, end2, max);
              if (len2 < 0) return -1;
              len += len2;
            }
            i = end2;
          }
          break;
          case '~': {
            if (max) return -1; // open end
            if (i < end && pattern.charAt(i) == '~') i = skipUnit(pattern, i+1);
            int end2 = skipUnit(pattern, i);
            int len2 = length(pattern, i, end2, false);
            if (len2 < 0) return -1;
            len += len2;
            i = end2;
          }
          break;
          case '1','2','3','4','5','6','7','8','9': {
            boolean zeroOrMore = i < end && pattern.charAt(i) == '?';
            if (zeroOrMore) i++; // skip ?
            int end2 = skipUnit(pattern, i);
            if (max || !zeroOrMore) {
              int len2 = length(pattern, i, end2, max);
              if (len2 < 0) return -1;
              len += (len2 * (opcode - '0'));
            }
            i = end2;
            // for min + zeroOrMore we do nothing here
            // then ? will be handled by ? case above
          }
          break;
          case '{':
            i = pattern.indexOf('}', i) + 1; break;
          case '(', ')': break; // no impact here
          default: len++;
        }
      }
      return len;
    }

  }

  /*
  To RegEx equivalent
   */

  /**
   * @return An approximation of this expression as Regular Expression. The RegEx may be more
   *     permissive.
   */
  public String toRegEx() {
    TextBuilder regex = new TextBuilder();
    boolean single = patterns.size() == 1;
    for (Pattern pattern : patterns) {
      if (!regex.isEmpty()) regex.append('|');
      // to be safe the | applies correctly wrap each in non-capture group
      if (!single) regex.append("(?:");
      toRegEx(pattern.pattern, 0, pattern.pattern.length(), regex);
      if (!single) regex.append(')');
    }
    return regex.toString();
  }

  private void toRegEx(Text pattern, int offset, int end, TextBuilder regex) {
    int i = offset;
    while (i < end) {
      char opcode = pattern.charAt(i++);
      switch (opcode) {
        case '#' -> regex.append("[0-9]");
        case '@' -> regex.append("[-_0-9A-Za-z]");
        case '[' -> i = toRegExSet(pattern, i, skipUnit(pattern, i-1), regex);
        case '|' -> i = toRegExSequence(pattern, i, skipUnit(pattern, i-1), regex);
        case '?', '*', '+', '1','2','3','4','5','6','7','8','9' -> i = toRegExRepeat(pattern, i-1, regex);
        case '~' -> i = toRegExScan(pattern, i, regex);
        // ignore bounds as there is no regex equivalent
        case '{' -> i = pattern.indexOf('}', i) + 1;
        case '(' -> regex.append('(');
        case ')' -> regex.append(')');
        default -> {
          // everything else is taken literally
          if (isAlphanumeric(opcode)) {
            regex.append(opcode);
          } else {
            regex.append('[');
            if (isRegExSetEscaped(opcode)) regex.append('\\');
            regex.append(opcode).append(']');
          }
        }
      }
    }
  }

  private int toRegExScan(Text pattern, int offset, TextBuilder regex) {
    if (pattern.charAt(offset) == '~') {
      //~~AB => A*?B
      regex.append("(?:");
      int end1 = skipUnit(pattern, offset+1);
      toRegEx(pattern, offset+1, end1, regex);
      regex.append(")*?(?:");
      int end2 = skipUnit(pattern, end1);
      toRegEx(pattern, end1, end2, regex);
      regex.append(')');
      return end2;
    }
    // ~A => .*?A
    int end = skipUnit(pattern, offset);
    regex.append(".*?(:?");
    toRegEx(pattern, offset, end, regex);
    regex.append(')');
    return end;
  }

  private int toRegExRepeat(Text pattern, int offset, TextBuilder regex) {
    int i = offset;
    char opcode = pattern.charAt(i++);
    int repMin = switch (opcode) {
      case '?', '*' -> 0;
      case '+' -> 1;
      default -> opcode - '0';
    };
    int repMax= switch (opcode) {
      case '?' -> 1;
      case '*', '+' -> MAX_VALUE;
      default -> opcode - '0';
    };
    if (pattern.charAt(i) == '?') {
      repMin = 0;
      i++;
    }
    int end = skipUnit(pattern, i);
    regex.append("(?:");
    toRegEx(pattern, i, end, regex);
    regex.append(')');
    if (repMin == 0 && repMax == 1) {
      regex.append('?');
    } else if (repMin == 0 && repMax == MAX_VALUE) {
      regex.append('*');
    } else if (repMin == 1 && repMax == MAX_VALUE) {
      regex.append('+');
    } else {
      regex.append('{');
      if (repMin < repMax) regex.append(repMin).append(',');
      regex.append(repMax).append('}');
    }
    return end;
  }

  private int toRegExSet(Text pattern, int offset, int end, TextBuilder regex) {
    regex.append('[');
    int i = offset;
    while (i < end) {
      char opcode = pattern.charAt(i++);
      switch (opcode) {
        case 'b' -> regex.append("01");
        case 'd' -> regex.append("0-9");
        case 'i' -> regex.append("\\-_0-9A-Za-z");
        case 'u' -> regex.append("A-Z");
        case 'l' -> regex.append("a-z");
        case 'c' -> regex.append("A-Za-z");
        case 'a' -> regex.append("0-9A-Za-z");
        case 'x' -> regex.append("0-9A-Fa-f");
        case 's' -> regex.append("\\-+");
        case ']' -> {
          if (i-1 == offset) regex.append('\\');
          regex.append(']');
        }
        default -> {
          if (isUpperLetter(opcode)) {
            regex.append(opcode).append(toLowerCase(opcode));
          } else if (isDigit(opcode)) {
            regex.append("0-").append(opcode);
          } else if (isLowerLetter(opcode)) {
            throw reserved(opcode);
          } else {
            // everything else is taken literally
            if (isRegExSetEscaped(opcode)) regex.append('\\');
            regex.append(opcode);
          }
        }
      }
    }
    return end;
  }

  private int toRegExSequence(Text pattern, int offset, int end, TextBuilder regex) {
    int i = offset;
    while (i < end) {
      char opcode = pattern.charAt(i++);
      switch (opcode) {
        case 'b' -> regex.append("[01]");
        case 'd', '#', '0',  '1','2','3','4','5','6','7','8','9' -> regex.append("[0-9]");
        case 'i' -> regex.append("[-_0-9A-Za-z]");
        case 'u' -> regex.append("[A-Z]");
        case 'l' -> regex.append("[a-z]");
        case 'c' -> regex.append("[A-Za-z]");
        case 'a' -> regex.append("[0-9A-Za-z]");
        case 'x' -> regex.append("[0-9A-Fa-f]");
        case 's' -> regex.append("[-+]");
        case '?' -> regex.append(".");
        case '|' -> {
          if (i-1 == offset) regex.append("\\|");
        }
        default -> {
          if (isUpperLetter(opcode)) {
            regex.append('[').append(opcode).append(toLowerCase(opcode)).append(']');
          } else if (isLowerLetter(opcode)) {
            throw reserved(opcode);
          } else {
            if (isAlphanumeric(opcode)) {
              regex.append(opcode);
            } else {
              // everything else is taken literally
              regex.append('[');
              if (isRegExSetEscaped(opcode)) regex.append('\\');
              regex.append(opcode).append(']');
            }
          }
        }
      }
    }
    return end;
  }

  private static boolean isRegExSetEscaped(char c) {
    return c == ']' || c == '^' || c == '-' || c == '\\' || c == '/';
  }
}
