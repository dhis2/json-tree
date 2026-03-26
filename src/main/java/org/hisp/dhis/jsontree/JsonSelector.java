package org.hisp.dhis.jsontree;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static org.hisp.dhis.jsontree.JsonNode.Index.SKIP;

/**
 * A {@link JsonSelector} describes a search pattern to find matching nodes in a {@link JsonNode}
 * tree.
 *
 * <p>{@link JsonSelector} offers a programmatic fluent API to compose a selector. Segments of an
 * entire selector can also be parsed from a {@link String} using {@link #of(String)}. However,
 * textual expressions are limited and do not support the full extent of the fluent API.
 *
 * <h3>RFC-9535 Comparison</h3>
 *
 * The building blocks (matchers) and their string-form syntax are strongly inspired by JsonPath
 * standard (RFC 9535). Most matchers work very similar to the standard but filters and recursive
 * decent differ slightly. Furthermore, the {@link JsonSelector} API only supports programmatic
 * composition of filters using Java as expression language in the form of {@link Predicate}
 * expressions. This keeps the implementation simple while offering the full expressiveness of the
 * Java language and the {@link JsonNode} API to formulate filters.
 *
 * <h3>Matching Extensions</h3>
 *
 * {@link JsonSelector} is the data model of a selector path as wel as the matching implementation.
 * The matching is implemented entirely on top of the {@link JsonNode} API and thus can easily be
 * extended with further matching logic by implementing a custom {@link Matcher} which can be
 * inserted into a selector path using {@link #select(Matcher)}.
 *
 * @author Jan Bernitt
 * @since 1.9
 */
public record JsonSelector(Matcher matcher, JsonSelector next) {

  /**
   * A sink for the matches found
   */
  @FunctionalInterface
  public interface Matches<T> extends Consumer<T> {
    /**
     * @return ture, when sufficient matches have been found and the matching should not progress
     *     further.
     */
    default boolean satisfied() {
      return false;
    }
  }

  /**
   * Checks a node against the condition of a selector segment and forwards matches to the next
   * selector segment.
   */
  @FunctionalInterface
  public interface Matcher {

    /**
     * @param node the node to test
     * @param next the next segment to test next level with if the given node matches this matcher
     * @param matches the consumer for matches found that needs to be forwarded
     */
    void match(JsonNode node, JsonSelector next, Matches<JsonNode> matches);
  }

  /**
   * The root selector
   */
  public static final JsonSelector $ = new JsonSelector(new RootMatcher(), null);
  /**
   * The {@code @} selector used as a starting point in relative selectors like they are used in filters.
   */
  public static final JsonSelector AT = new JsonSelector(new SelfMatcher(), null);

  public static JsonSelector of(String expression) {
    return $.parseExpression(expression);
  }

  public static JsonSelector of(JsonNodeType type) {
    return new JsonSelector(new TypeMatcher(type), null);
  }

  public static JsonSelector of(Text name) {
    return new JsonSelector(new KeyMatcher(name), null);
  }

  public static JsonSelector ofAny() {
    return new JsonSelector(new AnyMatcher(), null);
  }

  public static JsonSelector ofDescendants() {
    return new JsonSelector(new DescendantsMatcher(), null);
  }

  public static JsonSelector of(int index) {
    return new JsonSelector(new IndexMatcher(Text.of(index)), null);
  }

  public static JsonSelector of(int[] indexes) {
    return new JsonSelector(
        new UnionMatcher(IntStream.of(indexes).mapToObj(Text::of).toList()), null);
  }

  public static JsonSelector of(int start, int end) {
    return of(start, end, 1);
  }

  public static JsonSelector of(int start, int end, int step) {
    return new JsonSelector(new SliceMatcher(start, end, step), null);
  }

  public static JsonSelector of(Predicate<JsonNode> filter) {
    return new JsonSelector(new FilterMatcher(filter), null);
  }

  @Override
  public String toString() {
    if (next == null && matcher == null) return "+";
    if (next == null) return matcher.toString();
    return matcher.toString() + next;
  }

  /**
   * @return true, when this selector not is the root {@link #$} of self {@link #AT} selector, in
   *     other words it represents a segment or tail of a selector
   */
  public boolean isSubSelector() {
    return !(matcher instanceof RootMatcher || matcher instanceof SelfMatcher);
  }

  /**
   * @return true, when this selector starts with the {@code ..} (recursive decent or descendants)
   *     matcher
   */
  public boolean isDescendants() {
    return matcher instanceof DescendantsMatcher;
  }

  /*
  Selection (navigate to a selective set of children)
   */

  public JsonSelector key(CharSequence name) {
    return select(of(Text.of(name)));
  }

  public JsonSelector any() {
    return select(ofAny());
  }

  public JsonSelector descendants() {
    return select(ofDescendants());
  }

  public JsonSelector index(int index) {
    return select(of(index));
  }

  public JsonSelector indexes(int... indexes) {
    return select(of(indexes));
  }

  public JsonSelector slice(int start) {
    return select(new JsonSelector(new SliceMatcher(start, null, 1), null));
  }

  public JsonSelector slice(int start, int end) {
    return slice(start, end, 1);
  }

  public JsonSelector slice(int start, int end, int step) {
    return select(of(start, end, step));
  }

  /*
  Filtering (remove from paths matching so far, no navigation)
   */

  public JsonSelector type(JsonNodeType type) {
    return select(of(type));
  }

  public JsonSelector filter(Predicate<JsonNode> filter) {
    return select(of(filter));
  }

  public JsonSelector filter(JsonSelector selector) {
    // note that just considering the first match of the sub-select
    // is in line with the RFC-9535 logic of path selectors in filters
    return filter(node -> node.queryFirst(selector).isPresent());
  }

  /*
  Combined or generic
   */

  public JsonSelector find(Predicate<JsonMixed> filter) {
    return descendants().filter(node -> filter.test(node.lift(JsonAccess.GLOBAL)));
  }

  public JsonSelector find(JsonNodeType type, Predicate<JsonMixed> filter) {
    return descendants().type(type).filter(node -> filter.test(node.lift(JsonAccess.GLOBAL)));
  }

  public JsonSelector select(Matcher next) {
    return select(new JsonSelector(next, null));
  }

  public JsonSelector select(JsonSelector next) {
    // avoid 2 descendants in a row
    if (this.next == null && next.isDescendants() && isDescendants()) return this;
    // appending requires reconstructing the chain from the start
    // we do this because we need a cheap dropping head as that
    // will happen all the time during evaluation
    // and this is what is super cheap with this data structure
    // just return the next()
    return new JsonSelector(matcher, this.next == null ? next : this.next.select(next));
  }

  public JsonSelector select(CharSequence expression) {
    if (expression.isEmpty()) return this;
    JsonSelector res = parseExpression(expression);
    return !res.isSubSelector() ? res : select(res);
  }

  /**
   * This is a special instance that we pass as next if only the last matcher has to match. If it's
   * {@link #match(JsonNode, Matches)} is called we have a match.
   */
  private static final JsonSelector MATCH = new JsonSelector(null, null);

  public void match(JsonNode node, Matches<JsonNode> matches) {
    if (matches.satisfied()) return;
    if (this == MATCH) {
      matches.accept(node);
    } else {
      matcher.match(node, next == null ? MATCH : next, matches);
      if (isDescendants()) matchChildren(node, matches);
    }
  }

  private void matchChildren(JsonNode node, Matches<JsonNode> matches) {
    switch (node.type()) {
      case ARRAY -> {
        for (JsonNode e : node.elements(SKIP)) match(e, matches);
      }
      case OBJECT -> {
        for (JsonNode e : node.members(SKIP)) match(e, matches);
      }
    }
  }

  private record RootMatcher() implements Matcher {

    @Override
    public void match(JsonNode node, JsonSelector next, Matches<JsonNode> matches) {
      next.match(node.getRoot(), matches);
    }

    @Override
    public String toString() {
      return "$";
    }
  }

  private record SelfMatcher() implements Matcher {

    @Override
    public void match(JsonNode node, JsonSelector next, Matches<JsonNode> matches) {
      next.match(node, matches);
    }

    @Override
    public String toString() {
      return "@";
    }
  }

  private record DescendantsMatcher() implements Matcher {

    @Override
    public void match(JsonNode node, JsonSelector next, Matches<JsonNode> matches) {
      // this is one part of it
      // where we try to apply the sub-selector to self
      next.match(node, matches);
    }

    @Override
    public String toString() {
      return "..";
    }
  }

  private record TypeMatcher(JsonNodeType type) implements Matcher {

    @Override
    public void match(JsonNode node, JsonSelector next, Matches<JsonNode> matches) {
      if (node.type() == type) next.match(node, matches);
    }

    @Override
    public String toString() {
      // note RFC-9535 does not have type matchers
      return "?(:" + type.name().toLowerCase() + ")";
    }
  }

  private record KeyMatcher(Text name) implements Matcher {

    @Override
    public void match(JsonNode node, JsonSelector next, Matches<JsonNode> matches) {
      if (node.isObject()) {
        JsonNode e = node.getIfExists(name);
        if (e != null) next.match(e, matches);
      }
    }

    @Override
    public String toString() {
      return "." + name;
    }
  }

  private record AnyMatcher() implements Matcher {

    @Override
    public void match(JsonNode node, JsonSelector next, Matches<JsonNode> matches) {
      JsonNodeType type = node.type();
      if (type == JsonNodeType.OBJECT) {
        for (JsonNode e : node.members(SKIP)) next.match(e, matches);
      } else if (type == JsonNodeType.ARRAY) {
        for (JsonNode e : node.elements(SKIP)) next.match(e, matches);
      }
    }

    @Override
    public String toString() {
      return "[*]";
    }
  }

  private record IndexMatcher(Text index) implements Matcher {

    @Override
    public void match(JsonNode node, JsonSelector next, Matches<JsonNode> matches) {
      if (node.isArray() && !node.isEmpty()) {
        JsonNode e = node.elementIfExists(index);
        if (e != null) next.match(e, matches);
      }
    }

    @Override
    public String toString() {
      return "[" + index + "]";
    }
  }

  private record UnionMatcher(List<Text> indexes) implements Matcher {

    @Override
    public void match(JsonNode node, JsonSelector next, Matches<JsonNode> matches) {
      if (node.isArray() && !node.isEmpty()) {
        for (Text i : indexes) {
          JsonNode e = node.elementIfExists(i);
          if (e != null) next.match(e, matches);
        }
      }
    }

    @Override
    public String toString() {
      return "[%s]".formatted(String.join(",", indexes));
    }
  }

  private record SliceMatcher(Integer start, Integer end, int step) implements Matcher {

    @Override
    public void match(JsonNode node, JsonSelector next, Matches<JsonNode> matches) {
      if (node.isArray() && !node.isEmpty()) {
        int size = node.size();
        int sRel = start != null ? start : step > 0 ? 0 : size;
        int eRel = end != null ? end : step > 0 ? size : 0;
        int sAbs = sRel < 0 ? size - sRel : Math.min(sRel, size);
        int eAbs = eRel < 0 ? size - eRel : Math.min(eRel, size);
        if (step < 0) {
          // forward iteration
          for (int i = sAbs; i >= eAbs; i += step) next.match(node.element(i), matches);
        } else {
          // backward iteration
          for (int i = sAbs; i < eAbs; i += step) next.match(node.element(i), matches);
        }
      }
    }

    @Override
    public String toString() {
      Object s = start == null ? "" : start;
      Object e = end == null ? "" : end;
      return step == 1 ? "[%s:%s]".formatted(s, e) : "[%s:%s:%d]".formatted(s, e, step);
    }
  }

  private record FilterMatcher(Predicate<JsonNode> filter) implements Matcher {

    @Override
    public void match(JsonNode node, JsonSelector next, Matches<JsonNode> matches) {
      if (filter.test(node)) next.match(node, matches);
    }

    @Override
    public String toString() {
      return "?(<condition>)";
    }
  }

  /*
  Parsing
   */

  /**
   * This is a very naive parsing that works for correct input and
   * fails for most incorrect with some exception, but there are a
   * few special cases where illegal input in certain places is just
   * ignored. This is to strike a balance between amount of code
   * and how important this feature is overall.
   */
  private JsonSelector parseExpression(CharSequence expression) {
    JsonSelector res = this;
    int offset = 0;
    while (offset < expression.length()) {
      char c = expression.charAt(offset++);
      switch (c) {
        case '$' -> res = $; // drop prior chain
        case '@' -> res = AT; // drop prior chain
        case '.' -> {
          if (expression.charAt(offset) == '.') {
            res = res.descendants();
            offset++;
          } else {
            int end = skipAlphanumeric(expression, offset);
            res = res.key(expression.subSequence(offset, end));
            offset = end;
          }
        }
        case '?' -> {
          offset++; // (
          int end = skipUntil(')', expression, offset);
          res = parseCondition(res, expression, offset, end - offset);
          offset = end + 1; // )
        }
        case '[' -> {
          int end = skipUntil(']', expression, offset);
          res = parseSelection(res, expression, offset, end - offset);
          offset = end + 1; // ]
        }
        default -> throw illegalCharacter(expression, offset - 1);
      }
    }
    return res;
  }

  private static JsonSelector parseCondition(
      JsonSelector parent, CharSequence expression, int offset, int length) {
    if (expression.charAt(offset) == ':') {
      return parent.type(
          JsonNodeType.valueOf(expression.subSequence(offset + 1, offset + 1 + length).toString()));
    }
    throw illegalCharacter(expression, offset - 1);
  }

  private static JsonSelector parseSelection(
      JsonSelector parent, CharSequence expression, int offset, int length) {
    char c = expression.charAt(offset);
    if (c == '*') return parent.any();
    if (c == '\'')
      return parent.key(expression.subSequence(offset + 1, skipUntil('\'', expression, offset+1)));
    if (c == '-') return parseSlice(parent, expression, offset, length);
    int end = skipDigits(expression, offset);
    c = expression.charAt(end);
    if (c == ']') return parent.index(parseInt(expression.subSequence(offset, offset+length).toString()));
    if (c == ',') return parseUnion(parent, expression, offset, length);
    if (c == ':') return parseSlice(parent, expression, offset, length);
    throw illegalCharacter(expression, offset);
  }

  private static JsonSelector parseUnion(JsonSelector parent, CharSequence expression, int offset, int length) {
    return parent.indexes(
        Stream.of(expression.subSequence(offset, offset + length).toString().split(","))
            .mapToInt(Integer::parseInt)
            .toArray());
  }

  private static JsonSelector parseSlice(JsonSelector parent, CharSequence expression, int offset, int length) {
    String[] parts = expression.subSequence(offset, offset + length).toString().split(":");
    if (parts.length == 0) return parent.select(new SliceMatcher(null, null, 1));
    if (parts.length == 1) return parent.select(new SliceMatcher(parseInt(parts[0]), null, 1));
    if (parts.length == 2) return parent.select(new SliceMatcher(sliceIndex(parts[0]), sliceIndex(parts[1]), 1));
    return parent.select(new SliceMatcher(sliceIndex(parts[0]), sliceIndex(parts[1]), parseInt(parts[2])));
  }

  private static Integer sliceIndex(String value) {
    if (value.isEmpty()) return null;
    return parseInt(value);
  }

  private static IllegalArgumentException illegalCharacter(CharSequence expression, int offset) {
    return new IllegalArgumentException(
        "Illegal character %s at offset %d".formatted(expression.charAt(offset), offset));
  }

  private static int skipAlphanumeric(CharSequence expression, int offset) {
    while (offset < expression.length() && isAlphanumeric(expression.charAt(offset))) offset++;
    return offset;
  }

  private static int skipDigits(CharSequence expression, int offset) {
    while (offset < expression.length() && isDigit(expression.charAt(offset))) offset++;
    return offset;
  }

  private static int skipUntil(char ch, CharSequence expression, int offset) {
    while (offset < expression.length() && expression.charAt(offset) != ch) offset++;
    return offset;
  }

  private static boolean isAlphanumeric(char c) {
    return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || isDigit(c) || c == '_';
  }

  private static boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  }
}
