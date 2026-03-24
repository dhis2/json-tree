package org.hisp.dhis.jsontree;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.IntStream;

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
 * @since 1.9 (in the source but not public yet)
 */
record JsonSelector(Type type, Matcher match, JsonSelector next) {

  public static final JsonSelector ROOT = new JsonSelector(Type.ROOT, new RootMatcher(), null);

  public static JsonSelector of(String expression) {
    return ROOT.select(expression);
  }

  public static JsonSelector of(Text name) {
    return new JsonSelector(Type.NAME, new NameMatcher(name), null);
  }

  public static JsonSelector ofAny() {
    return new JsonSelector(Type.ANY, new AnyMatcher(), null);
  }

  public static JsonSelector of(int index) {
    return new JsonSelector(Type.INDEX, new IndexMatcher(Text.of(index)), null);
  }

  public static JsonSelector of(int[] indexes) {
    return new JsonSelector(
        Type.UNION, new UnionMatcher(IntStream.of(indexes).mapToObj(Text::of).toList()), null);
  }

  public static JsonSelector of(int start, int end) {
    return of(start, end, 1);
  }

  public static JsonSelector of(int start, int end, int step) {
    return new JsonSelector(Type.SLICE, new SliceMatcher(start, end, step), null);
  }

  public static JsonSelector of(Predicate<JsonNode> filter) {
    return new JsonSelector(Type.FILTER, new FilterMatcher(filter), null);
  }

  @Override
  public String toString() {
    if (next == null) return match.toString();
    return match.toString()+next;
  }

  public JsonSelector property(CharSequence name) {
    return select(of(Text.of(name)));
  }

  public JsonSelector any() {
    return select(ofAny());
  }

  public JsonSelector index(int index) {
    return select(of(index));
  }

  public JsonSelector indexes(int... indexes) {
    return select(of(indexes));
  }

  public JsonSelector slice(int start) {
    return select(new JsonSelector(Type.SLICE, new SliceMatcher(start, null, 1), null));
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

  public JsonSelector select(JsonSelector next) {
    // appending requires reconstructing the chain from the start
    // we do this because we need a cheap dropping head as that
    // will happen all the time during evaluation
    // and this is what is super cheap with this data structure
    // just return the next()
    return new JsonSelector(type, match, this.next == null ? next : this.next.select(next));
  }

  public JsonSelector select(CharSequence expression) {
    if (expression.isEmpty()) return this;
    JsonSelector res = this;
    int offset = 0;
    while (offset < expression.length()) {
      char c = expression.charAt(offset++);
      switch (c) {
        case '$' -> res = ROOT; // drop prior chain
        case '.' -> {
          int end = skipName(expression, offset);
          res = res.property(expression.subSequence(offset, end));
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
              res = res.property(expression.subSequence(offset, end));
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
    return res.type == Type.ROOT ? res : select(res);
  }

  private static int skipName(CharSequence expression, int offset) {
    while (offset < expression.length() && isLetter(expression.charAt(offset))) offset++;
    return offset;
  }

  private static boolean isLetter(char c) {
    return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c == '_';
  }

  enum Type {
    /**
     * {@code $} => Navigate to root
     */
    ROOT,
    /**
     * {@code .key} or {@code ['key']} => direct child by exact name
     */
    NAME,
    /**
     * {@code [*]} => all direct children
     */
    ANY,
    /**
     * {@code [0]} => direct child by index number
     */
    INDEX,
    /**
     * {@code [1,3,5]} => direct children by 2 or more index numbers
     */
    UNION,
    /**
     * {@code [2:3:4]} => direct children by index range start:end:step (step can be omitted)
     */
    SLICE,
    /**
     * {@code ..} => recursive decent to match the next selector anywhere in the current subtree
     */
    DECENT,
    /**
     * {@code [?(@.price < 10)]} => match on a condition, one or both sides can be relative paths.
     * In theory nesting is allowed, the left and/or right side of the logical expression can be
     * a constant literal or an arbitrary path expression (even including $ as starting point).
     */
    FILTER,
  }

  sealed interface Matcher {

    void match(JsonNode parent, Consumer<JsonNode> match);
  }

  record RootMatcher() implements Matcher {

    @Override
    public void match(JsonNode parent, Consumer<JsonNode> match) {
      match.accept(parent.getRoot());
    }

    @Override
    public String toString() {
      return "$";
    }
  }

  record SelfMatcher() implements Matcher {

    @Override
    public void match(JsonNode parent, Consumer<JsonNode> match) {
      match.accept(parent);
    }

    @Override
    public String toString() {
      return "@";
    }
  }

  record NameMatcher(Text name) implements Matcher {

    @Override
    public void match(JsonNode parent, Consumer<JsonNode> match) {
      if (!parent.getType().isSimple()) {
        JsonNode e = parent.getIfExists(name);
        if (e != null) match.accept(e);
      }
    }

    @Override
    public String toString() {
      return "."+name;
    }
  }

  record AnyMatcher() implements Matcher {

    @Override
    public void match(JsonNode parent, Consumer<JsonNode> match) {
      JsonNodeType type = parent.getType();
      if (type == JsonNodeType.OBJECT) {
        parent.members().forEach(match);
      } else if (type == JsonNodeType.ARRAY) {
        parent.elements().forEach(match);
      }
    }

    @Override
    public String toString() {
      return "[*]";
    }
  }

  record IndexMatcher(Text index) implements Matcher {

    @Override
    public void match(JsonNode parent, Consumer<JsonNode> match) {
      if (parent.getType() == JsonNodeType.ARRAY && !parent.isEmpty()) {
        JsonNode e = parent.elementIfExists(index);
        if (e != null) match.accept(e);
      }
    }

    @Override
    public String toString() {
      return "["+index+"]";
    }
  }

  record UnionMatcher(List<Text> indexes) implements Matcher {

    @Override
    public void match(JsonNode parent, Consumer<JsonNode> match) {
      if (parent.getType() == JsonNodeType.ARRAY && !parent.isEmpty()) {
        for (Text i : indexes) {
          JsonNode e = parent.elementIfExists(i);
          if (e != null) match.accept(e);
        }
      }
    }

    @Override
    public String toString() {
      return "[%s]".formatted(String.join(",", indexes));
    }
  }

  record SliceMatcher(Integer start, Integer end, int step) implements Matcher {

    @Override
    public void match(JsonNode parent, Consumer<JsonNode> match) {
      if (parent.getType() == JsonNodeType.ARRAY && !parent.isEmpty()) {
        if (start != null && end != null && step > 0) {
          // basic case: forward iteration with known bounds
          for (int i = start; i < end; i += step) match.accept(parent.element(i));
        } else {
          int size = parent.size();
          int sRel = start != null ? start : step > 0 ? 0 : size;
          int eRel = end != null ? end : step > 0 ? size : 0;
          int sAbs = sRel < 0 ? size - sRel : Math.min(sRel, size);
          int eAbs = eRel < 0 ? size - eRel : Math.min(eRel, size);
          if (step < 0) {
            for (int i = sAbs; i >= eAbs; i += step) match.accept(parent.element(i));
          } else {
            for (int i = sAbs; i < eAbs; i += step) match.accept(parent.element(i));
          }
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

  record FilterMatcher(Predicate<JsonNode> filter) implements Matcher {

    @Override
    public void match(JsonNode parent, Consumer<JsonNode> match) {
      if (filter.test(parent)) match.accept(parent);
    }

    @Override
    public String toString() {
      return "[?(~)]";
    }
  }
}
