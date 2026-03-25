package org.hisp.dhis.jsontree;

import java.util.function.Consumer;
import org.hisp.dhis.jsontree.JsonBuilder.JsonArrayBuilder;
import org.hisp.dhis.jsontree.JsonBuilder.JsonObjectBuilder;
import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * Builder for JURL formatted strings.
 *
 * @author Jan Bernitt
 * @since 1.9
 */
final class JurlBuilder implements JsonObjectBuilder, JsonArrayBuilder {

  private final Jurl.Format format;
  private final TextBuilder out = new TextBuilder();
  private final boolean[] hasChildrenAtLevel = new boolean[128];
  private final Probe probe;

  private int level = 0;

  JurlBuilder(Jurl.Format format) {
    this.format = format;
    this.probe = new Probe(format.nullsInObjects() != Jurl.Format.Nulls.OMIT);
  }

  @Override
  public String toString() {
    return out.toString();
  }

  static String toJurl(Jurl.Format format, JsonNode value) {
    JurlBuilder bld = new JurlBuilder(format);
    bld.addElement(value);
    return bld.out.toString();
  }

  @Override
  public JsonObjectBuilder addMember(CharSequence name, JsonNode value) {
    JsonNodeType type = value.type();
    return switch (type) {
      case OBJECT -> addObject(name, obj -> value.members().forEach(obj::addMember));
      case ARRAY -> addArray(name, arr -> value.elements().forEach(arr::addElement));
      case STRING -> addString(name, value.value().toString());
      case NUMBER -> addRawMember(name, value.getDeclaration());
      case NULL -> addRawMember(name, format.nullsInObjects().value);
      case BOOLEAN -> format.booleanShorthands()
          ? addBoolean(name, value.booleanValue())
          : addRawMember(name, value.getDeclaration());
    };
  }

  @Override
  public JsonObjectBuilder addBoolean(CharSequence name, boolean value) {
    return format.booleanShorthands()
      ? addRawMember(name, value ? "t":"f")
      : addRawMember(name, value ? "true" : "false");
  }

  @Override
  public JsonObjectBuilder addBoolean(CharSequence name, Boolean value) {
    if (value == null) return addRawMember(name, format.nullsInObjects().value);
    return addBoolean(name, value.booleanValue());
  }

  @Override
  public JsonObjectBuilder addNumber(CharSequence name, int value) {
    addMember(name);
    out.append(value);
    return this;
  }

  @Override
  public JsonObjectBuilder addNumber(CharSequence name, long value) {
    addMember(name);
    out.append(value);
    return this;
  }

  @Override
  public JsonObjectBuilder addNumber(CharSequence name, double value) {
    addMember(name);
    appendDouble(value);
    return this;
  }

  @Override
  public JsonObjectBuilder addNumber(CharSequence name, Number value) {
    if (value == null) return addRawMember(name, format.nullsInObjects().value);
    if (value instanceof Integer) return addNumber(name, value.intValue());
    if (value instanceof Long) return addNumber(name, value.longValue());
    if (value instanceof Double || value instanceof Float) addNumber(name, value.doubleValue());
    return addRawMember(name, value.toString());
  }

  @Override
  public JsonObjectBuilder addString(CharSequence name, CharSequence value) {
    if (value == null) return addRawMember(name, format.nullsInObjects().value);
    addMember(name);
    appendEscaped(value);
    return this;
  }

  @Override
  public JsonObjectBuilder addArray(CharSequence name, Consumer<JsonArrayBuilder> arr) {
    addMember(name);
    addArray(arr);
    return this;
  }

  @Override
  public JsonObjectBuilder addObject(CharSequence name, Consumer<JsonObjectBuilder> obj) {
    addMember(name);
    addObject(obj);
    return this;
  }

  private void addMember(CharSequence name) {
    appendCommaWhenNeeded();
    append(name);
    append(':');
  }

  private JsonObjectBuilder addRawMember(CharSequence name, CharSequence rawValue) {
    if (rawValue == null) return this;
    addMember(name);
    append(rawValue);
    return this;
  }

  private JsonArrayBuilder addRawElement(CharSequence rawValue) {
    appendCommaWhenNeeded();
    if (rawValue != null && !rawValue.isEmpty()) append(rawValue);
    return this;
  }

  @Override
  public JsonArrayBuilder addElement(JsonNode value) {
    JsonNodeType type = value.type();
    return switch (type) {
      case OBJECT -> addObject(obj -> value.members().forEach(obj::addMember));
      case ARRAY -> addArray(arr -> value.elements().forEach(arr::addElement));
      case STRING -> addString(value.value().toString());
      case NUMBER -> addRawElement(value.getDeclaration());
      case NULL -> addRawElement(format.nullsInArrays().value);
      case BOOLEAN ->
          format.booleanShorthands()
              ? addBoolean(value.booleanValue())
              : addRawElement(value.getDeclaration());
    };
  }

  @Override
  public JsonArrayBuilder addBoolean(boolean value) {
    return format.booleanShorthands()
        ? addRawElement(value ? "t" : "f")
        : addRawElement(value ? "true" : "false");
  }

  @Override
  public JsonArrayBuilder addBoolean(Boolean value) {
    if (value == null) return addRawElement(format.nullsInArrays().value);
    return addBoolean(value.booleanValue());
  }

  @Override
  public JsonArrayBuilder addNumber(int value) {
    appendCommaWhenNeeded();
    out.append(value);
    return this;
  }

  @Override
  public JsonArrayBuilder addNumber(long value) {
    appendCommaWhenNeeded();
    out.append(value);
    return this;
  }

  @Override
  public JsonArrayBuilder addNumber(double value) {
    appendCommaWhenNeeded();
    appendDouble(value);
    return this;
  }

  private void appendDouble(double value) {
    if (JsonBuilder.requiresString(value)) {
      if (Double.isNaN(value)) out.append("\"NaN\"");
      if (value == Double.POSITIVE_INFINITY) out.append("\"Infinity\"");
      if (value == Double.NEGATIVE_INFINITY) out.append("\"-Infinity\"");
    } else {
      out.append(value);
    }
  }

  @Override
  public JsonArrayBuilder addNumber(Number value) {
    if (value == null) return addRawElement(format.nullsInArrays().value);
    if (value instanceof Integer) return addNumber(value.intValue());
    if (value instanceof Long) return addNumber(value.longValue());
    if (value instanceof Double || value instanceof Float) addNumber(value.doubleValue());
    return addRawElement(value.toString());
  }

  @Override
  public JsonArrayBuilder addString(CharSequence value) {
    if (value == null) return addRawElement(format.nullsInArrays().value);
    appendCommaWhenNeeded();
    appendEscaped(value);
    return this;
  }

  @Override
  public JsonArrayBuilder addArray(Consumer<JsonArrayBuilder> arr) {
    beginLevel();
    arr.accept(this);
    endLevel();
    return this;
  }

  @Override
  public JsonArrayBuilder addObject(Consumer<JsonObjectBuilder> obj) {
    probe.members = 0;
    obj.accept(probe);
    if (probe.members == 0) {
      appendCommaWhenNeeded();
      // approximate the empty object with null
      String value = format.nullsInArrays().value;
      if (value == null) {
        append("null");
      } else
        append(value);
      return this;
    }
    beginLevel();
    obj.accept(this);
    endLevel();
    return this;
  }

  private void beginLevel() {
    append('(');
    hasChildrenAtLevel[++level] = false;
  }

  private void endLevel() {
    level--;
    append(')');
  }

  private void appendCommaWhenNeeded() {
    if (!hasChildrenAtLevel[level]) {
      hasChildrenAtLevel[level] = true;
    } else {
      append(',');
    }
  }

  private void append(CharSequence str) {
    out.append(str);
  }

  private void append(char c) {
    out.append(c);
  }

  void appendEscaped(@NotNull CharSequence str) {
    append('\'');
    for (int i = 0; i < str.length(); i++) {
      appendEscaped(str.charAt(i));
    }
    append('\'');
  }

  private void appendEscaped(char c) {
    if (Jurl.isQuotedString(c)) {
      out.append(c);
    } else {
      switch (c) {
        case '&' -> out.append("~a");
        case '=' -> out.append("~e");
        case '+' -> out.append("~p");
        case '#' -> out.append("~h");
        case '%' -> out.append("~c");
        case '\'' -> out.append("~q");
        case '"' -> out.append("~d");
        case '~' -> out.append("~~");
        case '\\' -> out.append("~b");
        default -> {
          out.append('~');
          out.append(toHexDigit(c / 16));
          out.append(toHexDigit(c % 16));
        }
      }
    }
  }

  private static char toHexDigit(int value) {
    if (value < 10) return (char) ('0'+value);
    return (char) ('A'+(value-10));
  }

  private final class Probe implements JsonObjectBuilder {
    private final boolean countNulls;
    int members;

    private Probe(boolean countNulls) {
      this.countNulls = countNulls;
    }


    @Override
    public JsonObjectBuilder addMember(CharSequence name, JsonNode value) {
      return count(value.isNull() ? null : value);
    }

    @Override
    public JsonObjectBuilder addBoolean(CharSequence name, boolean value) {
      return count(value);
    }

    @Override
    public JsonObjectBuilder addBoolean(CharSequence name, Boolean value) {
      return count(value);
    }

    @Override
    public JsonObjectBuilder addNumber(CharSequence name, int value) {
      return count(value);
    }

    @Override
    public JsonObjectBuilder addNumber(CharSequence name, long value) {
      return count(value);
    }

    @Override
    public JsonObjectBuilder addNumber(CharSequence name, double value) {
      return count(value);
    }

    @Override
    public JsonObjectBuilder addNumber(CharSequence name, Number value) {
      return count(value);
    }

    @Override
    public JsonObjectBuilder addString(CharSequence name, CharSequence value) {
      return count(value);
    }

    @Override
    public JsonObjectBuilder addArray(CharSequence name, Consumer<JsonArrayBuilder> value) {
      members++;
      return this;
    }

    @Override
    public JsonObjectBuilder addObject(CharSequence name, Consumer<JsonObjectBuilder> value) {
      members++;
      return this;
    }

    private JsonObjectBuilder count(Object value) {
      if (value != null || countNulls) members++;
      return this;
    }
  }
}
