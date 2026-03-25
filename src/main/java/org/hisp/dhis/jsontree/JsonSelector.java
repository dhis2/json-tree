package org.hisp.dhis.jsontree;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;

import static org.hisp.dhis.jsontree.JsonNode.Index.SKIP;

/**
 * A model for JsonPath (RFC 9535) (called {@link JsonSelector} due to name clash with existing
 * {@link JsonPath}).
 *
 * <p>ATM this is just a POC for the API and how to deal with complexities. The key insight here is
 * that filter expression are difficult to implement when parsing them from a string, so instead we
 * just use {@link Predicate} and offer a fluent API to compose {@link JsonSelector}s which is much
 * easier and more powerful als the full extent of Java and the {@link JsonNode} API can be used in
 * expressions.
 *
 * @author Jan Bernitt
 * @since 1.9
 */
public record JsonSelector(Matcher matcher, JsonSelector next) {

  public static final JsonSelector $ = new JsonSelector(new RootMatcher(), null);
  public static final JsonSelector AT = new JsonSelector(new SelfMatcher(), null);

  public static JsonSelector of(String expression) {
    return $.select(expression);
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

  public static JsonSelector ofDescendant() {
    return new JsonSelector(new DescendantMatcher(), null);
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
    return matcher.toString()+next;
  }

  /**
   * @return true, when this selector starts with root {@link #$}, false otherwise
   */
  public boolean isAbsolute() {
    return matcher instanceof RootMatcher;
  }

  /**
   * @return true, when this selector starts with {@code ..} (recursive decent or descendant) matcher
   */
  public boolean isDescendant() {
    return matcher instanceof DescendantMatcher;
  }

  public JsonSelector type(JsonNodeType type) {
    return select(of(type));
  }

  public JsonSelector key(CharSequence name) {
    return select(of(Text.of(name)));
  }

  public JsonSelector any() {
    return select(ofAny());
  }

  public JsonSelector descendant() {
    return select(ofDescendant());
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

  public JsonSelector filter(Predicate<JsonNode> filter) {
    return select(of(filter));
  }

  public JsonSelector filter(JsonSelector selector, Predicate<JsonNode> filter) {
    // note that just considering the first match of the sub-select
    // is in line with the RFC-9535 logic of path selectors in filters
    return filter(node -> node.query(selector).findFirst().filter(filter).isPresent());
  }

  public JsonSelector find(Predicate<JsonMixed> filter) {
    return descendant().filter(node -> filter.test(node.lift(JsonAccess.GLOBAL)));
  }

  public <T extends JsonValue> JsonSelector find(Class<T> as, Predicate<T> filter) {
    return descendant().filter(node -> filter.test(node.lift(JsonAccess.GLOBAL).as(as)));
  }

  public JsonSelector select(JsonSelector next) {
    // avoid 2 descendants in a row
    if (this.next == null && next.isDescendant() && isDescendant()) return this;
    // appending requires reconstructing the chain from the start
    // we do this because we need a cheap dropping head as that
    // will happen all the time during evaluation
    // and this is what is super cheap with this data structure
    // just return the next()
    return new JsonSelector(matcher, this.next == null ? next : this.next.select(next));
  }

  public JsonSelector select(CharSequence expression) {
    if (expression.isEmpty()) return this;
    JsonSelector res = this;
    int offset = 0;
    while (offset < expression.length()) {
      char c = expression.charAt(offset++);
      switch (c) {
        case '$' -> res = $; // drop prior chain
        case ':' -> {
          int end = skipName(expression, offset);
          res = res.type(JsonNodeType.valueOf(expression.subSequence(offset, end).toString()));
          offset=end;
        }
        case '.' -> {
          int end = skipName(expression, offset);
          res = res.key(expression.subSequence(offset, end));
          offset=end;
        }
        case '[' -> {
          switch (expression.charAt(offset++)) {
            case '*' -> {
              res = res.any();
              offset += 1;
            }
            case '\'' -> {
              int end = skipName(expression, offset);
              res = res.key(expression.subSequence(offset, end));
              offset = end + 2;
            }
            default -> {
              //TODO index, union or slice
            }
          }
        }
        default ->
            throw new IllegalArgumentException(
                "Illegal character %s at offset %d"
                    .formatted(expression.charAt(offset - 1), offset - 1));
      }
    }
    return res.isAbsolute() ? res : select(res);
  }

  private static int skipName(CharSequence expression, int offset) {
    while (offset < expression.length() && isLetter(expression.charAt(offset))) offset++;
    return offset;
  }

  private static boolean isLetter(char c) {
    return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_';
  }

  /**
   * This is a special instance that we pass as next if only the last matcher has to match.
   * If it's {@link #match(JsonNode, Consumer)} is called we have a match.
   */
  private static final JsonSelector MATCH = new JsonSelector(null, null);

  public void match(JsonNode node, Consumer<JsonNode> matches) {
    if (this == MATCH) {
        matches.accept(node);
    } else {
      matcher.match(node, next == null ? MATCH : next, matches);
      if (isDescendant()) matchChildren(node, matches);
    }
  }

  private void matchChildren(JsonNode node, Consumer<JsonNode> matches) {
    switch (node.getType()) {
      case ARRAY -> {
        for (JsonNode e : node.elements(SKIP))
          if (next == null || !e.getType().isSimple()) match(e, matches);
      }
      case OBJECT -> {
        for (JsonNode e : node.members(SKIP))
          if (next == null || !e.getType().isSimple()) match(e, matches);
      }
    }
  }

  /**
   * Checks a node against the condition of a selector segment and forwards matches
   * to the next selector segment.
   */
  public interface Matcher {

    /**
     * @param self the node to test
     * @param next the next segment to test next level with if the given node matches this matcher
     * @param matches the consumer for matches found that needs to be forwarded
     */
    void match(JsonNode self, JsonSelector next, Consumer<JsonNode> matches);
  }

  private record RootMatcher() implements Matcher {

    @Override
    public void match(JsonNode self, JsonSelector next, Consumer<JsonNode> matches) {
      next.match(self.getRoot(), matches);
    }

    @Override
    public String toString() {
      return "$";
    }
  }

  private record SelfMatcher() implements Matcher {

    @Override
    public void match(JsonNode self, JsonSelector next, Consumer<JsonNode> matches) {
      next.match(self, matches);
    }

    @Override
    public String toString() {
      return "@";
    }
  }

  private record DescendantMatcher() implements Matcher {

    @Override
    public void match(JsonNode self, JsonSelector next, Consumer<JsonNode> matches) {
      // this is one part of it
      // where we try to apply the sub-selector to self
      next.match(self, matches);
    }

    @Override
    public String toString() {
      return "..";
    }
  }

  private record TypeMatcher(JsonNodeType type) implements Matcher {

    @Override
    public void match(JsonNode self, JsonSelector next, Consumer<JsonNode> matches) {
      if (self.getType() == type) next.match(self, matches);
    }

    @Override
    public String toString() {
      return ":"+type.name().toLowerCase(); // note RFC-9535 does not have type matchers
    }
  }

  private record KeyMatcher(Text name) implements Matcher {

    @Override
    public void match(JsonNode self, JsonSelector next, Consumer<JsonNode> matches) {
      if (self.getType() == JsonNodeType.OBJECT) {
        JsonNode e = self.getIfExists(name);
        if (e != null) next.match(e, matches);
      }
    }

    @Override
    public String toString() {
      return "."+name;
    }
  }

  private record AnyMatcher() implements Matcher {

    @Override
    public void match(JsonNode self, JsonSelector next, Consumer<JsonNode> matches) {
      JsonNodeType type = self.getType();
      if (type == JsonNodeType.OBJECT) {
        for (JsonNode e : self.members(SKIP)) next.match(e, matches);
      } else if (type == JsonNodeType.ARRAY) {
        for (JsonNode e : self.elements(SKIP)) next.match(e, matches);
      }
    }

    @Override
    public String toString() {
      return "[*]";
    }
  }

  private record IndexMatcher(Text index) implements Matcher {

    @Override
    public void match(JsonNode self, JsonSelector next, Consumer<JsonNode> matches) {
      if (self.getType() == JsonNodeType.ARRAY && !self.isEmpty()) {
        JsonNode e = self.elementIfExists(index);
        if (e != null) next.match(e, matches);
      }
    }

    @Override
    public String toString() {
      return "["+index+"]";
    }
  }

  private record UnionMatcher(List<Text> indexes) implements Matcher {

    @Override
    public void match(JsonNode self, JsonSelector next, Consumer<JsonNode> matches) {
      if (self.getType() == JsonNodeType.ARRAY && !self.isEmpty()) {
        for (Text i : indexes) {
          JsonNode e = self.elementIfExists(i);
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
    public void match(JsonNode self, JsonSelector next, Consumer<JsonNode> matches) {
      if (self.getType() == JsonNodeType.ARRAY && !self.isEmpty()) {
        int size = self.size();
        int sRel = start != null ? start : step > 0 ? 0 : size;
        int eRel = end != null ? end : step > 0 ? size : 0;
        int sAbs = sRel < 0 ? size - sRel : Math.min(sRel, size);
        int eAbs = eRel < 0 ? size - eRel : Math.min(eRel, size);
        if (step < 0) {
          // forward iteration
          for (int i = sAbs; i >= eAbs; i += step) next.match(self.element(i), matches);
        } else {
          // backward iteration
          for (int i = sAbs; i < eAbs; i += step) next.match(self.element(i), matches);
        }
      }
    }

    @Override
    public String toString() {
      Object s = start == null ? "" : start;
      Object e = end == null ? "" : end;
      return step == 1
          ? "[%s:%s]".formatted(s, e)
          : "[%s:%s:%d]".formatted(s, e, step);
    }
  }

  private record FilterMatcher(Predicate<JsonNode> filter) implements Matcher {

    @Override
    public void match(JsonNode self, JsonSelector next, Consumer<JsonNode> matches) {
      switch (self.getType()) {
        case ARRAY -> self.elements(SKIP).stream().filter(filter).forEach(e -> next.match(e, matches));
        case OBJECT -> self.members(SKIP).stream().filter(filter).forEach(e -> next.match(e, matches));
        default -> {
          if (filter.test(self)) next.match(self, matches);
        }
      }
    }

    @Override
    public String toString() {
      return "[?(~)]";
    }
  }
}
