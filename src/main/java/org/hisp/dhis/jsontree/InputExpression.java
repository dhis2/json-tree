package org.hisp.dhis.jsontree;

import java.util.List;
import java.util.stream.Stream;

import static java.lang.Integer.MAX_VALUE;

/**
 * Input patterns are specifically designed to match short sequences where the relevant features
 * to match are in the ASCII range. Typical examples are numbers, like dates or telephone numbers
 * as well as alphanumeric values, like UUIDs, codes and other identifiers.
 *
 * <h3>Performance and Safety</h3>
 * A key aspect of the design is that matching is allocation-free and guaranteed to be linear time.
 * Mostly that is linear to the pattern length. In case of open-ended repeats and scans this is
 * linear to the input length. This makes it fairly safe to run against user input without risking
 * that matching causes excessive resource usage.
 *
 * <h3>Syntax</h3>
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
   * @return the format that matches the input, or null if none matches
   */
  public Pattern match(CharSequence input) {
    for (Pattern pattern : patterns) if (pattern.matches(input)) return pattern;
    return null;
  }

  public static boolean matches(String pattern, CharSequence input) {
    return matches(pattern.toCharArray(), input);
  }

  public static boolean matches(char[] pattern, CharSequence input) {
    return match(pattern, 0, pattern.length, input, 0) == input.length();
  }

  public static int match(char[] pattern, int offset, int end, CharSequence input, int pos) {
    int i = offset;
    int len = input.length();
    while (i < end && pos >= 0) {
      int iCode = i;
      char opcode = pattern[i++];
      switch (opcode) {
        case '#': if (!isDigit(input.charAt(pos++))) return -1; break;
        case '@': if (!isIdentifier(input.charAt(pos++))) return -1; break;
        case '[': {
          if (!matchSet(pattern, i, input.charAt(pos++))) return -1;
          i = skipTo(']', pattern, i) + 1;
        }
        break;
        case '|': {
          if (!matchSequence(pattern, i, input, pos)) return -1;
          i = skipTo('|', pattern, i) + 1;
          pos += i - iCode - 2; // 1:1 length relation: set char => 1 input char
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
  private static boolean matchSequence(char[] pattern, int offset, CharSequence input, int pos) {
    int i = offset;
    int len = input.length();
    while (i < pattern.length && pos < len && (i == offset || pattern[i] != '|')) {
      char in = input.charAt(pos++);
      char opcode = pattern[i++];
      switch (opcode) {
        case 'b': if (!isBinary(in)) return false; break;
        case 'd', '#': if (!isDigit(in)) return false; break;
        case 'i': if (!isIdentifier(in)) return false; break;
        case 'u': if (!isUpperLetter(in)) return false; break;
        case 'l': if (!isLowerLetter(in)) return false; break;
        case 'c': if (!isLetter(in)) return false; break;
        case 'a': if (!isAlphanumeric(in)) return false; break;
        case 'x': if (!isHexadecimal(in)) return false; break;
        case 's': if (!isSign(in)) return false; break;
        case '?': break; // any character, always fine
        default:
          if (isUpperLetter(opcode)) {
            if (opcode != (in & ~0b10_0000)) return false; break;
          } else if (isDigit(opcode)) {
            if (!isDigit(in)) return false; // easy case
            int pos0 = pos-1;
            pos = matchNumericSequence(pattern, i-1, input, pos0);
            if (pos < 0) return false;
            i += pos - pos0 - 1;
          } else if (isLowerLetter(opcode)) {
            throw reserved(opcode);
          } else {
            // everything else is taken literally
            if (opcode != in) return false; break;
          }
      }
    }
    return true;
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

  /*
  Format (find length bounds)
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
            if (pattern.charAt(i) == '~') i = skipUnit(pattern, i+1);
            int end2 = skipUnit(pattern, i);
            int len2 = length(pattern, i, end2, false);
            if (len2 < 0) return -1;
            len += len2;
            i = end2;
          }
          break;
          case '1','2','3','4','5','6','7','8','9': {
            boolean zeroOrMore = pattern.charAt(i) == '?';
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
  }

}
