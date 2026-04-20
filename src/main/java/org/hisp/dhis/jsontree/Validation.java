package org.hisp.dhis.jsontree;

import static java.lang.Double.NaN;
import static org.hisp.dhis.jsontree.Validation.YesNo.AUTO;
import static org.hisp.dhis.jsontree.Validation.YesNo.NO;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import org.hisp.dhis.jsontree.internal.CheckNull;
import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * Structural Validations as defined by the JSON schema specification <a
 * href="https://json-schema.org/draft/2020-12">2020-12 dialect</a>.
 *
 * <p>Used on methods or {@link java.lang.reflect.RecordComponent}s to add validation to the method
 * return type.
 *
 * <p>Used on type declaration used in return types to add validation to any of its usage.
 *
 * <p>
 *
 * <h3>Meta-Annotations</h3>
 *
 * Used on annotation type to define meta annotations for validations, for example a
 * {@code @NonNegativeInteger} annotation which would be annotated {@code @Validation(type=INTEGER,
 * minimum=0)}. Such meta-annotations should use {@link Target} of {@link ElementType#TYPE} and
 * {@link ElementType#TYPE_USE}.
 *
 * <p>
 *
 * <h3>Priority</h3>
 *
 * Order of source priority lowest to highest:
 *
 * <ol>
 *   <li>value type class (using the Java type information; only if no annotation is present on
 *       type)
 *   <li>Meta-annotation(s) on value type class
 *   <li>{@link Validation} annotation on value type class
 *   <li>Meta-annotation(s) on property method or record component
 *   <li>{@link Validation} annotation on property method or record component
 *   <li>Meta-annotation(s) on property method return type (type use)
 *   <li>{@link Validation} annotation on property method return type (type use)
 * </ol>
 *
 * Sources with higher priority override values of sources with lower priority unless the higher
 * priority value is "undefined".
 *
 * @see Required
 * @author Jan Bernitt
 * @see org.hisp.dhis.jsontree.Validator
 * @since 0.11
 */
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Validation {

  /**
   * @since 1.9
   */
  enum Mode {
    /**
     * Return the {@link Result} (fail fast) but don't throw
     */
    PROBE,

    PROBE_ALL,
    /**
     * Throw on first error found
     */
    FAIL,
    /**
     * Find all errors and then throw
     */
    FAIL_ALL;

    public boolean isFailFast() {
      return this == PROBE || this == FAIL;
    }
  }

  enum YesNo {
    NO,
    YES,
    AUTO;

    public boolean isYes() {
      return this == YES;
    }

    public boolean isAuto() {
      return this == AUTO;
    }
  }

  enum Rule {
    // any values, non-standard
    CUSTOM,

    // any values
    TYPE,
    ENUM,

    // string values
    MIN_LENGTH,
    MAX_LENGTH,
    PATTERN,

    // number values
    MINIMUM,
    MAXIMUM,
    EXCLUSIVE_MINIMUM,
    EXCLUSIVE_MAXIMUM,
    MULTIPLE_OF,

    // array values
    MIN_ITEMS,
    MAX_ITEMS,
    UNIQUE_ITEMS,

    // object values
    MIN_PROPERTIES,
    MAX_PROPERTIES,
    REQUIRED,
    DEPENDENT_REQUIRED
  }

  /** In line with the JSON schema validation specification. */
  enum NodeType {
    NULL,
    BOOLEAN,
    STRING,
    NUMBER,
    INTEGER,
    ARRAY,
    OBJECT;

    @NotNull
    public static NodeType of(@CheckNull JsonNodeType type) {
      if (type == null) return NULL;
      return switch (type) {
        case OBJECT -> OBJECT;
        case ARRAY -> ARRAY;
        case STRING -> STRING;
        case BOOLEAN -> BOOLEAN;
        case NULL -> NULL;
        case NUMBER -> NUMBER;
      };
    }

    @NotNull
    @SuppressWarnings("unchecked")
    public static Set<NodeType> of(@NotNull Class<?> type) {
      return ofJsonType(
          JsonValue.class.isAssignableFrom(type)
              ? (Class<? extends JsonValue>) type
              : toJsonType(type));
    }

    static Class<? extends JsonValue> toJsonType(Class<?> type) {
      if (type == String.class || type.isEnum()) return JsonString.class;
      if (type == Character.class || type == char.class) return JsonString.class;
      if (type == Date.class) return JsonDate.class;
      if (type == LocalDate.class || type == LocalTime.class || type == LocalDateTime.class) return JsonMixed.class;
      if (type == Boolean.class || type == boolean.class) return JsonBoolean.class;
      if (type == Integer.class || type == Long.class || type == BigInteger.class || type == Instant.class)
        return JsonInteger.class;
      if (Number.class.isAssignableFrom(type)) return JsonNumber.class;
      if (type.isPrimitive())
        return type == float.class || type == double.class ? JsonNumber.class : JsonInteger.class;
      if (Collection.class.isAssignableFrom(type)) return JsonArray.class;
      if (Object[].class.isAssignableFrom(type)) return JsonArray.class;
      if (Map.class.isAssignableFrom(type)) return JsonObject.class;
      if (Record.class.isAssignableFrom(type)) return JsonObject.class;
      return JsonValue.class;
    }

    @SuppressWarnings("unchecked")
    static Set<NodeType> ofJsonType(Class<? extends JsonValue> type) {
      Validation validation = type.getAnnotation(Validation.class);
      if (validation != null) {
        NodeType[] types = validation.type();
        if (types.length == 0) return Set.of();
        return EnumSet.of(types[0], types);
      }
      EnumSet<NodeType> res = EnumSet.noneOf(NodeType.class);
      for (Class<?> si : type.getInterfaces()) {
        if (JsonValue.class.isAssignableFrom(si)) {
          res.addAll(ofJsonType((Class<? extends JsonValue>) si));
        }
      }
      return res;
    }
  }

  /**
   * The core function to check a value which is used for both built-in validations for the {@link
   * Rule}s defined by the standard as well as custom validations provuded in user space using
   * {@link org.hisp.dhis.jsontree.Validator}.
   */
  @FunctionalInterface
  interface Validator {

    /**
     * Adds an error to the provided callback in case the provided value is not valid according to
     * this check.
     *
     * @param value the value to check
     * @param addError callback to add errors
     */
    void validate(JsonMixed value, Consumer<Error> addError);
  }

  /**
   * @param value the value that was validated
   * @param schema the schema validated against
   * @param rules the rules included in the validation
   * @param errors list of errors (if any were found)
   * @since 1.9
   */
  record Result(JsonValue value, Class<?> schema, Set<Rule> rules, List<Error> errors) {}

  record Error(Rule rule, JsonPath path, JsonValue value, String template, List<Object> args) {

    public static Error of(Rule rule, JsonValue value, String template, Object... args) {
      return new Error(rule, value.path(), value, template, List.of(args));
    }

    @Override
    public String toString() {
      return "%s %s (%s)".formatted(path, template.formatted(args.toArray()), rule);
    }
  }

  /**
   * Used to mark properties that should not be validated. By default, all properties are validated.
   */
  @Target({ElementType.METHOD, ElementType.TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @interface Ignore {}

  /**
   * Validations that apply to array elements or object member values.
   *
   * <p>To build multi-level validations use validation annotated item types or create specific
   * validation annotations for the items which in term can have an {@linkplain Items} annotation.
   */
  @Target({ElementType.METHOD, ElementType.TYPE, ElementType.ANNOTATION_TYPE})
  @Retention(RetentionPolicy.RUNTIME)
  @interface Items {

    Validation value();
  }

  /**
   * If multiple type are given the value must be one of them.
   *
   * <p>An {@link NodeType#INTEGER} is any number with a fraction of zero. This means it can have a
   * fraction part as long as that part is zero.
   *
   * @return value must have one of the given JSON node types
   */
  NodeType[] type() default {};

  /**
   * @return The strictness used when checking {@link #type()}s. By default, conversion is non-strict for best interoperability
   */
  Strict strictness() default @Strict();

  @Retention(RetentionPolicy.RUNTIME)
  @interface Strict {

    /**
     * @return true, to set all AUTO to YES
     */
    boolean value() default false;

    /**
     * Non-strict: The JSON strings "true" and "false" are accepted as JSON booleans
     *
     * @return When YES, do not accept boolean given as JSON string
     */
    YesNo booleans() default YesNo.AUTO;

    /**
     * Non-strict: A JSON number or JSON boolean are accepted as JSON string (as their text value)
     * if and only if no string-specific or custom validations rules are present. For restricted strings
     * it is always assumed that only a JSON string can match the restrictions.
     *
     * @return When YES, do not accept JSON number or boolean
     */
    YesNo strings() default YesNo.AUTO;

    /**
     * Non-strict: A JSON string that is numerical is accepted as number. Where numbers map to Java
     * doubles the number literals of NaN, Infinite and -Infinite are accepted as well.
     * In such a case number specific restrictions do apply to the string as well. This handling only
     * occurs if JSON string wasn't already an accepted type.
     *
     * @return When YES, do not accept JSON string as number
     */
    YesNo numbers() default YesNo.AUTO;

    record Instance(boolean value, YesNo booleans, YesNo strings, YesNo numbers) implements Strict {
      public static final Strict AUTO = new Instance(false, YesNo.AUTO, YesNo.AUTO, YesNo.AUTO);
      @Override
      public Class<Strict> annotationType() {
        return Strict.class;
      }
    }
  }

  /**
   * A property marked as varargs will allow {@link NodeType#ARRAY} to occur, each element then is
   * validated against the present simple value validations.
   *
   * <p>In addition, all given array requirements must be met.
   *
   * <p>{@link YesNo#AUTO} is used when inferring validation from the return type for types that are
   * {@link java.util.Collection}s of simple types.
   *
   * <p>Cannot be used in combination with {@link #oneOfValues()} as it would be unclear if those
   * values apply to the array, the elements or both.
   *
   * @return when {@link YesNo#YES}, the given {@link #type()} can occur once (as simple type value)
   *     or many times as an array of such values.
   */
  YesNo varargs() default YesNo.AUTO;

  /**
   * Corresponds to JSON schema validation specified as {@code enum}. Because of the name clash with
   * the Java keyword {@code enum} the name {@code oneOfValues} was chosen.
   *
   * <p>If all values are strings and all start with a letter and none is {@code true}, {@code
   * false} or {@code null} then the strings do not have to be quoted.
   *
   * @return value must be equal to one of the given JSON values
   */
  String[] oneOfValues() default {};

  /**
   * Corresponds to JSON schema validation specified as {@code enum}.
   *
   * <p>This is just a shorter more convenient form to declare {@link #oneOfValues()} set using an
   * {@link Enum} class.
   *
   * @return value must be equal to one of the value of the given enum
   */
  Class<? extends Enum> enumeration() default Enum.class;

  /**
   * {@link YesNo#AUTO} is not case-insensitive.
   *
   * @return to allow {@link #enumeration()} names to be of different case
   * @since 1.0
   */
  YesNo caseInsensitive() default YesNo.AUTO;

  /*
  Validations for Strings
  */

  /**
   * If multiple annotations are present the largest of any given minimum is used.
   *
   * @return string value must not be shorter than the given minimum length
   */
  int minLength() default -1;

  /**
   * If multiple annotations are present the smallest of any given maximum is used.
   *
   * @return string value must not be longer than the given maximum length
   */
  int maxLength() default -1;

  /**
   * If multiple annotations are present all given patterns must match.
   *
   * @see RegEx
   *
   * @return string value must match the given {@link InputExpression} pattern
   */
  String[] pattern() default {};

  /**
   * ATM formats are purely informal to be used when expressing validations as JSON schema as it
   * would be used e.g. in OpenAPI. They are meant to be used together with {@link #pattern()} to give
   * patterns a human readable description or name.
   *
   * @return a name of the format
   */
  String format() default "";

  /*
  Validations for Numbers
   */

  /**
   * If multiple annotations are present the largest of any given minimum is used.
   *
   * @return number value must be equal to or larger than the given value
   */
  double minimum() default Double.NaN;

  /**
   * If multiple annotations are present the smallest of any given maximum is used.
   *
   * @return number value mst be equal to or less than the given value
   */
  double maximum() default Double.NaN;

  /**
   * If multiple annotations are present the largest of any given minimum is used.
   *
   * @return number value must be larger than the given value
   */
  double exclusiveMinimum() default Double.NaN;

  /**
   * If multiple annotations are present the smallest of any given maximum is used.
   *
   * @return number value must be smaller than the given value
   */
  double exclusiveMaximum() default Double.NaN;

  /**
   * If multiple annotations are present the smallest of any given factor is used.
   *
   * @return number value must be divisible by the given value without rest
   */
  double multipleOf() default Double.NaN;

  /*
  Validations for Arrays
   */

  /**
   * When used on a method the validation applies to the return type array,
   *
   * <p>when used on a type the validation applies to the annotated type array.
   *
   * <p>If multiple annotations are present the largest of any given minimum is used.
   *
   * @return array value must have at least the given number of elements
   */
  int minItems() default -1;

  /**
   * When used on a method the validation applies to the return type array,
   *
   * <p>when used on a type the validation applies to the annotated type array.
   *
   * <p>If multiple annotations are present the smallest of any given maximum is used.
   *
   * @return array value must have at most the given number of elements
   */
  int maxItems() default -1;

  /**
   * When used on a method the validation applies to the return type array,
   *
   * <p>when used on a type the validation applies to the annotated type array.
   *
   * <p>If multiple annotations are present the property dependentRequires unique items if any of
   * them specifies it.
   *
   * @return all elements in the array value must be unique
   */
  YesNo uniqueItems() default YesNo.AUTO;

  /*
  Validations for Objects
   */

  /**
   * When used on a method the validation applies to the return type object,
   *
   * <p>when used on a type the validation applies to the annotated type object.
   *
   * <p>If multiple annotations are present the largest of any given minimum is used.
   *
   * @return object must have at least the given number of properties
   */
  int minProperties() default -1;

  /**
   * When used on a method the validation applies to the return type object,
   *
   * <p>when used on a type the validation applies to the annotated type object.
   *
   * <p>If multiple annotations are present the smallest of any given maximum is used.
   *
   * @return object must have at most the given number of properties
   */
  int maxProperties() default -1;

  /**
   * When set to AUTO any property using a Java primitive type is required.
   *
   * <p>If multiple annotations are present with differing value YES takes precedence over NO, both
   * take precedence over AUTO.
   *
   * @return parent object must have the annotated property
   */
  YesNo required() default YesNo.AUTO;

  /**
   * To describe which properties are in a dependency relation with each other properties are
   * assigned to group names. One or more members of a group have the role of a trigger while the
   * others are the ones that are required depending on the trigger. This property defines the
   * groups for the annotated property and its role using suffixes as described below.
   *
   * <h3>Triggers</h3>
   * Use a CSS selector-like syntax for trigger conditions:
   * <ul>
   *   <li>{@code .group[=*]} triggers group when present</li>
   *   <li>{@code .group[=?]} triggers group when absent</li>
   *   <li>{@code .group[={value}]} triggers group when it has the given text value</li>
   * </ul>
   *
   * <p>A group with multiple properties with trigger conditions always combines with AND logic,
   * meaning all conditions must be met to trigger the group.
   *
   * <p>If none of the properties in a group is marked any of the properties makes all others in the
   * group required (all group properties are co-dependent).
   *
   * <h3>Exclusive Dependent Required</h3>
   * <p>In addition, a property that is dependent required (not a trigger) can be marked exclusive using {@code *}
   * suffix, to indicate that it is mutual exclusive to all other dependent required properties that are marked equally.
   *
   * @return the names of the groups the annotated property belongs to or triggers
   */
  String[] dependentRequired() default {};

  /**
   * This is a non-standard adjustment to {@link #required()} and {@link #dependentRequired()}.
   *
   * @return when {@link YesNo#YES} a JSON {@code null} value satisfies being {@link #required()} or
   *     {@link #dependentRequired()}
   * @since 1.1
   */
  YesNo acceptNull() default YesNo.AUTO;

  /**
   * When a {@link Validation} annotation declares a {@link Meta} {@link Class} all other attributes are ignored.
   *
   * @return the factory class that can extract a {@link Validation.Instance} from another
   *     annotation. This is used to create custom annotation which do have attributes that transfer
   *     to attributes of {@link Validation}.
   */
  Class<? extends Meta> meta() default Meta.class;

  /**
   * A {@link Validator} that uses RegEx patterns.
   * Use via @{@link org.hisp.dhis.jsontree.Validator}.
   */
  record RegEx(java.util.regex.Pattern regex) implements Validator {

    public RegEx(Validation params) {
      // avoid exception during bootstrapping fail => simply is ineffective
      this(params.pattern().length == 0 ? null : Pattern.compile(params.pattern()[0]));
    }

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      if (regex == null) return; // params had no pattern
      if (!value.isSimple() || value.isUndefined()) return;
      CharSequence actual = value.text();
      if (!regex.matcher(actual).matches())
        addError.accept(
            Error.of(Rule.PATTERN, value, "must match %s but was: %s", regex.pattern(), actual));
    }
  }

  /**
   * The value must be one of the provided JSON strings
   *
   * @param constants expected JSON values
   */
  record EnumJson(Set<JsonMixed> constants) implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      for (JsonMixed c : constants) if (c.equivalentTo(value)) return;
      addError.accept(
          Error.of(Rule.ENUM, value, "must be one of %s but was: %s", constants, value));
    }
  }

  /*
  Meta Annotations from other annotatons with values
   */

  interface Meta<T extends Annotation> {

    Validation extract(T meta);
  }

  record Instance(
      NodeType[] type,
      Strict strictness,
      YesNo varargs,
      String[] oneOfValues,
      Class<? extends Enum> enumeration,
      YesNo caseInsensitive,
      int minLength,
      int maxLength,
      String[] pattern,
      String format,
      double minimum,
      double maximum,
      double exclusiveMinimum,
      double exclusiveMaximum,
      double multipleOf,
      int minItems,
      int maxItems,
      YesNo uniqueItems,
      int minProperties,
      int maxProperties,
      YesNo required,
      String[] dependentRequired,
      YesNo acceptNull)
      implements Validation {

    public Instance(
        int minLength,
        int maxLength,
        double minimum,
        double maximum,
        double exclusiveMinimum,
        double exclusiveMaximum,
        int minItems,
        int maxItems,
        int minProperties,
        int maxProperties,
        NodeType... types) {
      this(
          types,
          Strict.Instance.AUTO,
          NO,
          new String[0],
          Enum.class,
          AUTO,
          minLength,
          maxLength,
          new String[0],
          "",
          minimum,
          maximum,
          exclusiveMinimum,
          exclusiveMaximum,
          NaN,
          minItems,
          maxItems,
          AUTO,
          minProperties,
          maxProperties,
          AUTO,
          new String[0],
          AUTO);
    }

    @Override
    public Class<Validation> annotationType() {
      return Validation.class;
    }

    @Override
    public Class<? extends Meta> meta() {
      return Meta.class;
    }
  }
}
