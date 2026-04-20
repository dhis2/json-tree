package org.hisp.dhis.jsontree.validation;

import static java.lang.Double.isNaN;
import static java.util.stream.Collectors.joining;
import static org.hisp.dhis.jsontree.Validation.NodeType.ARRAY;
import static org.hisp.dhis.jsontree.Validation.NodeType.BOOLEAN;
import static org.hisp.dhis.jsontree.Validation.NodeType.INTEGER;
import static org.hisp.dhis.jsontree.Validation.NodeType.NULL;
import static org.hisp.dhis.jsontree.Validation.NodeType.NUMBER;
import static org.hisp.dhis.jsontree.Validation.NodeType.OBJECT;
import static org.hisp.dhis.jsontree.Validation.NodeType.STRING;

import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.hisp.dhis.jsontree.InputExpression;
import org.hisp.dhis.jsontree.JsonList;
import org.hisp.dhis.jsontree.JsonMap;
import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonNodeType;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.JsonPath;
import org.hisp.dhis.jsontree.Text;
import org.hisp.dhis.jsontree.Validation.Error;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.Rule;
import org.hisp.dhis.jsontree.Validation.Validator;
import org.hisp.dhis.jsontree.internal.CheckNull;
import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * A validator that Contains one {@link Validator} for each property of the schema that needs
 * validation.
 *
 * @param schema that source used to extract {@link JsonObject.Property} list from
 * @param properties one validator for each property
 * @author Jan Bernitt
 * @since 0.11
 */
record ObjectValidator(@NotNull Class<?> schema, @NotNull Map<JsonPath, Validator> properties)
    implements Validator {

  @Override
  public void validate(JsonMixed value, Consumer<Error> addError) {
    if (!value.isObject()) return;
    properties()
        .forEach(
            (property, validator) ->
                validator.validate(value.get(property, JsonMixed.class), addError));
  }

  /*
     Creation internals...
  */

  /**
   * "JVM Cache" for schemas that are already transformed to a {@link ObjectValidator} entry. OBS!
   * Cannot use {@code ConcurrentHashMap} because while computing an entry another entry might be
   * added within (as a consequence of) the computation.
   */
  private static final Map<Class<?>, ObjectValidator> BY_SCHEMA_TYPE =
      new ConcurrentSkipListMap<>(Comparator.comparing(Class::getName));

  public static ObjectValidator of(Class<?> schema) {
    return of(schema, new HashSet<>());
  }

  private static ObjectValidator of(Class<?> schema, Set<Class<?>> currentlyResolved) {
    return ofObject(schema, () -> ObjectValidation.of(schema), currentlyResolved);
  }

  private static ObjectValidator ofObject(
      Class<?> schema, Supplier<ObjectValidation> analyse, Set<Class<?>> currentlyResolved) {
    return BY_SCHEMA_TYPE.computeIfAbsent(
        schema,
        type -> {
          currentlyResolved.add(type);
          Map<JsonPath, Validator> res = new TreeMap<>();
          ObjectValidation objectValidation = analyse.get();
          Map<Text, PropertyValidations> properties = objectValidation.properties();
          properties.forEach(
              (property, validations) -> {
                Validator propValidator = of(property, validations);
                Validator propTypeValidator =
                    ofJavaType(objectValidation.types().get(property), currentlyResolved);
                Validator validator = Chain.of(propValidator, propTypeValidator);
                if (validator != null) res.put(JsonPath.of(property), validator);
              });
          Validator dependentRequired = ofDependentRequired(properties);
          if (dependentRequired != null) res.put(JsonPath.SELF, dependentRequired);
          return new ObjectValidator(type, Map.copyOf(res));
        });
  }

  /**
   * TODO(future) support for types like: {@code JsonIntList extends JsonList<JsonInteger>} So a Class type
   * where the actual type for the list element needs to be extracted from the superinterfaces.
   * {@code JsonSet<T> extends JsonAbstractArray<T>} or {@code JsonMultiMap<T> extends
   * JsonAbstractObject<JsonList<T>>} So types with parameters where the actual type of the object
   * or array needs to be found from a combination of the actual type arguments and the
   * superinterfaces.
   */
  @CheckNull
  private static Validator ofJavaType(
      java.lang.reflect.Type type, Set<Class<?>> currentlyResolved) {
    if (type instanceof Class<?> schema) {
      if (JsonObject.class.isAssignableFrom(schema) || Record.class.isAssignableFrom(schema)) {
        if (currentlyResolved.contains(schema)) return new Lazy(schema, new AtomicReference<>());
        return of(schema, currentlyResolved);
      }
    } else if (type instanceof ParameterizedType pt) {
      Class<?> rawType = (Class<?>) pt.getRawType();
      if (JsonMap.class.isAssignableFrom(rawType))
        return Items.of(ofJavaType(pt.getActualTypeArguments()[0], currentlyResolved));
      if (JsonList.class.isAssignableFrom(rawType))
        return Items.of(ofJavaType(pt.getActualTypeArguments()[0], currentlyResolved));
    }
    return null;
  }

  @CheckNull
  private static Validator of(Text property, PropertyValidations validations) {
    Map<JsonNodeType, Validator> byType = new EnumMap<>(JsonNodeType.class);
    BiConsumer<JsonNodeType, Validator> add =
        (type, validator) -> {
          if (validator != null) byType.put(type, validator);
        };

    Set<NodeType> accepted = validations.accepted();
    if (accepted.isEmpty()) accepted = EnumSet.allOf(NodeType.class);
    boolean acceptStrings = accepted.contains(STRING);
    boolean acceptNumbers = accepted.contains(NUMBER) || accepted.contains(INTEGER);
    boolean acceptIntegersOnly = accepted.contains(INTEGER) && !accepted.contains(NUMBER);

    Validator strings = ofString(validations.strings());
    Validator numbers = ofNumber(validations.numbers(), acceptIntegersOnly);
    Validator customs = All.of(validations.customs().stream());
    if (acceptStrings) add.accept(JsonNodeType.STRING, strings);
    if (acceptNumbers) add.accept(JsonNodeType.NUMBER, numbers);
    if (accepted.contains(ARRAY)) add.accept(JsonNodeType.ARRAY, ofArray(validations.arrays()));
    if (accepted.contains(OBJECT)) add.accept(JsonNodeType.OBJECT, ofObject(validations.objects()));

    // strictness AUTO acceptance
    PropertyValidations.Strict strictness = validations.strictness();
    boolean strictStrings = strings != null || customs != null  || strictness.strings().isYes();
    boolean strictBooleans = strictness.booleans().isYes();
    boolean strictNumbers = strictness.numbers().isYes();
    if (!strictNumbers && acceptNumbers && numbers != null && !acceptStrings) {
      // accept number given as string => number constraints apply to string
      add.accept(JsonNodeType.STRING, numbers);
    }
    if (!strictNumbers && acceptNumbers && numbers == null && acceptStrings && strings != null) {
      // string constraints apply to numbers
      add.accept(JsonNodeType.NUMBER, strings);
    }

    Validator type = null;
    if (!validations.accepted().isEmpty()) {
      type =
          new Type(
              EnumSet.copyOf(validations.accepted()), strictBooleans, strictStrings, strictNumbers);
    }
    Validator typeSpecific = byType.isEmpty() ? null : new TypeDependent(byType);
    Validator items = validations.items() == null ? null : Items.of(of(property, validations.items()));
    Validator required = ofRequired(property, validations);
    return Chain.of(required, type, customs, typeSpecific, items);
  }

  private static Validator ofRequired(Text property, PropertyValidations validations) {
    PropertyValidations.RequiredValidation requiredness = validations.requiredness();
    boolean isRequiredYes = requiredness != null && requiredness.required().isYes();
    boolean isRequiredAuto =
        (requiredness == null || requiredness.required().isAuto()) && isRequiredImplicitly(validations);
    boolean isAllowNull = requiredness != null && requiredness.allowNull().isYes();
    return !isRequiredYes && !isRequiredAuto ? null : new Required(property, isAllowNull);
  }

  /**
   * minItems, minLength, minProperties implicitly means this is required if there is only one type
   * possible and if required is AUTO
   */
  private static boolean isRequiredImplicitly(PropertyValidations validations) {
    return validations.accepted().stream().allMatch(type -> isRequiredImplicitly(validations, type));
  }

  private static boolean isRequiredImplicitly(PropertyValidations validations, NodeType type) {
    return switch (type) {
      case STRING -> validations.strings() != null && validations.strings().minLength() > 0;
      case ARRAY -> validations.arrays() != null && validations.arrays().minItems() > 0;
      case OBJECT -> validations.objects() != null && validations.objects().minProperties() > 0;
      default -> false;
    };
  }

  @CheckNull
  private static Validator ofString(@CheckNull PropertyValidations.StringValidation strings) {
    if (strings == null) return null;
    return All.of(
        ofStringEnum(strings),
        strings.minLength() <= 0 ? null : new MinLength(strings.minLength()),
        strings.maxLength() <= 1 ? null : new MaxLength(strings.maxLength()),
        strings.pattern() == null
            ? null
            : new Pattern(strings.pattern()));
  }

  private static Validator ofStringEnum(@NotNull PropertyValidations.StringValidation strings) {
    Set<String> constants = strings.anyOfStrings();
    if (constants.isEmpty()) return null;
    boolean caseInsensitive = strings.caseInsensitive().isYes();
    if (!caseInsensitive) return EnumCaseSensitive.of(constants);
    return new EnumCaseInsensitive(constants);
  }

  @CheckNull
  private static Validator ofNumber(
      @CheckNull PropertyValidations.NumberValidation numbers, boolean acceptIntegersOnly) {
    if (numbers == null) return null;
    double min = numbers.minimum();
    double max = numbers.maximum();
    double minEx = numbers.exclusiveMinimum();
    double maxEx = numbers.exclusiveMaximum();
    if (acceptIntegersOnly) {
      return All.of(
          isNaN(min) ? null : new MinimumLong((long) min),
          isNaN(max) ? null : new MaximumLong((long) max),
          isNaN(minEx) ? null : new MinimumLong((long) minEx + 1),
          isNaN(maxEx) ? null : new MaximumLong((long) maxEx - 1),
          isNaN(numbers.multipleOf()) ? null : new MultipleOf(numbers.multipleOf()));
    }
    return All.of(
        isNaN(min) ? null : new Minimum(min),
        isNaN(max) ? null : new Maximum(max),
        isNaN(minEx) ? null : new ExclusiveMinimum(minEx),
        isNaN(maxEx) ? null : new ExclusiveMaximum(maxEx),
        isNaN(numbers.multipleOf()) ? null : new MultipleOf(numbers.multipleOf()));
  }

  @CheckNull
  private static Validator ofArray(@CheckNull PropertyValidations.ArrayValidation arrays) {
    return arrays == null
        ? null
        : All.of(
        arrays.minItems() <= 0 ? null : new MinItems(arrays.minItems()),
        arrays.maxItems() <= 1 ? null : new MaxItems(arrays.maxItems()),
        !arrays.uniqueItems().isYes() ? null : new UniqueItems());
  }

  @CheckNull
  private static Validator ofObject(@CheckNull PropertyValidations.ObjectValidation objects) {
    return objects == null
        ? null
        : All.of(
        objects.minProperties() <= 0 ? null : new MinProperties((objects.minProperties())),
        objects.maxProperties() <= 1 ? null : new MaxProperties((objects.maxProperties())));
  }

  @CheckNull
  private static Validator ofDependentRequired(
      @NotNull Map<Text, PropertyValidations> properties) {
    if (properties.isEmpty()) return null;
    if (properties.values().stream()
        .allMatch(p -> p.requiredness() == null || p.requiredness().dependentRequired().isEmpty())) return null;
    Set<String> groups = new HashSet<>();
    Map<String, List<DependentRequired.Trigger>> triggersByGroup = new HashMap<>();
    Map<String, List<DependentRequired.Dependent>> nonExclusiveByGroup = new HashMap<>();
    Map<String, List<DependentRequired.Dependent>> exclusiveByGroup = new HashMap<>();
    properties.forEach(
        (name, validation) -> {
          PropertyValidations.RequiredValidation requiredness = validation.requiredness();
          if (requiredness != null && !requiredness.dependentRequired().isEmpty()) {
            requiredness
                .dependentRequired()
                .forEach(
                    indicator -> {
                      boolean trigger = indicator.indexOf('[') > 0;
                      String group = indicator;
                      if (trigger) group = group.substring(0, indicator.indexOf('['));
                      boolean exclusive = !trigger && group.endsWith("*");
                      if (exclusive) group = group.substring(0, group.length() - 1);
                      groups.add(group);
                      List<DependentRequired.Trigger> triggers =
                          triggersByGroup.computeIfAbsent(group, key -> new ArrayList<>());
                      if (trigger && indicator.endsWith("*]")) {
                        triggers.add(
                            new DependentRequired.Trigger(
                                name, requiredness.present(), "with " + name));
                      } else if (trigger && indicator.endsWith("?]")) {
                        triggers.add(
                            new DependentRequired.Trigger(
                                name, requiredness.absent(), "without " + name));
                      } else if (trigger) {
                        int iEquals = indicator.indexOf('=');
                        String value =
                            indicator.substring(iEquals + 1, indicator.indexOf(']', iEquals));
                        triggers.add(
                            new DependentRequired.Trigger(
                                name,
                                e -> e.exists() && e.node().textValue().contentEquals(value),
                                "with %s=%s".formatted(name, value)));
                      } else {
                        DependentRequired.Dependent dependent = new DependentRequired.Dependent(name, requiredness.present());
                        if (exclusive) {
                          exclusiveByGroup.computeIfAbsent(group, key -> new ArrayList<>()).add(dependent);
                        } else {
                          nonExclusiveByGroup.computeIfAbsent(group, key -> new ArrayList<>()).add(dependent);
                        }
                      }
                    });
          }
        });
    List<Validator> all = new ArrayList<>();
    for (String group : groups) {
      all.add(
          new DependentRequired(
              triggersByGroup.getOrDefault(group, List.of()),
              nonExclusiveByGroup.getOrDefault(group, List.of()),
              exclusiveByGroup.getOrDefault(group, List.of())
          ));
    }
    return All.of(all.toArray(Validator[]::new));
  }

  /*
  Node type independent or generic validators
  */

  private record All(List<Validator> validators) implements Validator {

    @CheckNull
    static Validator of(Validator... validators) {
      return of(Stream.of(validators));
    }
    static Validator of(Stream<Validator> validators) {
      List<Validator> actual =
          validators
              .filter(Objects::nonNull)
              .mapMulti(
                  (Validator v, Consumer<Validator> pipe) -> {
                    if (v instanceof All all) {
                      all.validators.forEach(pipe);
                    } else {
                      pipe.accept(v);
                    }
                  })
              .toList();
      return actual.isEmpty() ? null : actual.size() == 1 ? actual.get(0) : new All(actual);
    }

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      validators.forEach(v -> v.validate(value, addError));
    }
  }

  /** Runs the {@link #dependent()} only if the {@link #independent()} is successful */
  private record Chain(Validator independent, Validator dependent) implements Validator {

    @CheckNull
    static Validator of(@CheckNull Validator independent, @CheckNull Validator dependent) {
      if (dependent == null) return independent;
      if (independent == null) return dependent;
      return new Chain(independent, dependent);
    }

    static Validator of(Validator... validators) {
      if (validators.length == 0) return null;
      Validator chain = validators[0];
      for (int i = 1; i < validators.length; i++)
        chain = of(chain, validators[i]);
      return chain;
    }

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      boolean[] guard = {true};
      independent.validate(
          value,
          error -> {
            guard[0] = false;
            addError.accept(error);
          });
      if (guard[0]) dependent.validate(value, addError);
    }
  }

  private record Type(
      EnumSet<NodeType> accepted,
      boolean strictBooleans,
      boolean strictStrings,
      boolean strictNumbers)
      implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      NodeType actual = NodeType.of(value.type());
      if (actual == NULL || accepted.contains(actual)) return;
      boolean acceptIntegers = accepted.contains(INTEGER);
      if (actual == NUMBER && acceptIntegers && value.isInteger()) return;
      // non-strict cases:
      // easy: accept string given as number or boolean
      if ((actual == BOOLEAN || actual == NUMBER) && !strictStrings && accepted.contains(STRING))
        return;
      // harder: numbers or booleans given as string
      if (actual == STRING) {
        boolean acceptBooleansAsString = !strictBooleans && accepted.contains(BOOLEAN);
        boolean acceptNumbers = accepted.contains(NUMBER);
        boolean acceptNumbersAsString = !strictNumbers && (acceptNumbers || acceptIntegers);
        // avoid accessing text() if both booleans and numbers are strict
        if (acceptBooleansAsString || acceptNumbersAsString) {
          Text text = value.text();
          // accept boolean given as string
          if (acceptBooleansAsString && (text.contentEquals("true") || text.contentEquals("false")))
            return;
          // accept number given as string
          if (!strictNumbers
              && acceptNumbers
              && (text.isTextualDecimal() || text.isSpecialDecimal())) return;
          if (!strictNumbers && acceptIntegers && text.isTextualInteger()) return;
        }
      }

      addError.accept(
          Error.of(Rule.TYPE, value, "must have any of %s type but was: %s", accepted, actual));
    }
  }

  private record TypeDependent(Map<JsonNodeType, Validator> byType) implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      Validator forType = byType.get(value.type());
      if (forType != null) forType.validate(value, addError);
    }
  }

  private record Items(Validator each) implements Validator {

    static Validator of(Validator each) {
      return each == null ? null : new Items(each);
    }

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      if (value.isObject()) value.forEachValue(e -> each.validate(e.as(JsonMixed.class), addError));
      if (value.isArray()) value.forEach(e -> each.validate(e.as(JsonMixed.class), addError));
    }
  }

  private record Required(Text property, boolean allowNull) implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      if (allowNull && !value.exists() || !allowNull && value.isUndefined())
        addError.accept(
            Error.of(
                Rule.REQUIRED,
                value,
                "%s is required but was " + (value.isNull() ? "null" : "undefined"),
                property));
    }
  }

  /*
  string values
   */

  private record EnumCaseSensitive(Set<Text> constants) implements Validator {

    static EnumCaseSensitive of(Set<String> constants) {
      return new EnumCaseSensitive(Set.copyOf(constants.stream().map(Text::of).toList()));
    }

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      Text actual = value.text();
      if (!constants.contains(actual))
        addError.accept(
            Error.of(
                Rule.ENUM,
                value,
                "must be one of %s but was: %s",
                constants,
                actual));
    }
  }

  private record EnumCaseInsensitive(InputExpression expr, Set<String> constants) implements Validator {

    EnumCaseInsensitive(Set<String> constants) {
      this(
          InputExpression.of(
              constants.stream()
                  .map(String::toUpperCase)
                  .map("|%s|"::formatted)
                  .toArray(String[]::new)),
          constants);
    }

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      Text actual = value.text();
      if (expr.match(actual) == null)
        addError.accept(
            Error.of(
                Rule.ENUM,
                value,
                "must be one of %s (case insensitive) but was: %s",
                constants,
                actual));
    }
  }

  private record MinLength(int limit) implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      int actual = value.length();
      if (actual < limit)
        addError.accept(
            Error.of(Rule.MIN_LENGTH, value, "length must be >= %d but was: %d", limit, actual));
    }
  }

  private record MaxLength(int limit) implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      int actual = value.length();
      if (actual > limit)
        addError.accept(
            Error.of(Rule.MAX_LENGTH, value, "length must be <= %d but was: %d", limit, actual));
    }
  }

  private record Pattern(InputExpression expr, String regExEquivalent) implements Validator {
    Pattern(InputExpression expr) {
      this(expr, expr.toRegEx());
    }

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      Text actual = value.text();
      if (expr.match(actual) == null)
        addError.accept(
            Error.of(Rule.PATTERN, value, "must match %s but was: %s", regExEquivalent, actual));
    }
  }

  /*
  number values
   */

  private record MinimumLong(long limit) implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      long actual = value.longValue();
      if (actual < limit)
        addError.accept(Error.of(Rule.MINIMUM, value, "must be >= %d but was: %d", limit, actual));
    }
  }

  private record Minimum(double limit) implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      double actual = value.doubleValue();
      if (actual < limit)
        addError.accept(Error.of(Rule.MINIMUM, value, "must be >= %f but was: %f", limit, actual));
    }
  }

  private record MaximumLong(long limit) implements Validator {
    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      long actual = value.longValue();
      if (actual > limit)
        addError.accept(Error.of(Rule.MAXIMUM, value, "must be <= %d but was: %d", limit, actual));
    }
  }

  private record Maximum(double limit) implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      double actual = value.doubleValue();
      if (actual > limit)
        addError.accept(Error.of(Rule.MAXIMUM, value, "must be <= %f but was: %f", limit, actual));
    }
  }

  private record ExclusiveMinimum(double limit) implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      double actual = value.doubleValue();
      if (actual <= limit)
        addError.accept(
            Error.of(Rule.EXCLUSIVE_MINIMUM, value, "must be > %f but was: %f", limit, actual));
    }
  }

  private record ExclusiveMaximum(double limit) implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      double actual = value.doubleValue();
      if (actual >= limit)
        addError.accept(
            Error.of(Rule.EXCLUSIVE_MAXIMUM, value, "must be < %f but was: %f", limit, actual));
    }
  }

  private record MultipleOf(double n) implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      double actual = value.doubleValue();
      if (actual % n > 0d)
        addError.accept(
            Error.of(Rule.MULTIPLE_OF, value, "must be a multiple of %f but was: %f", n, actual));
    }
  }

  /*
  array values
   */

  private record MinItems(int count) implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      if (!value.isArray()) return;
      int actual = value.size();
      if (actual < count)
        addError.accept(
            Error.of(Rule.MIN_ITEMS, value, "must have >= %d items but had: %d", count, actual));
    }
  }

  private record MaxItems(int count) implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      if (!value.isArray()) return;
      int actual = value.size();
      if (actual > count)
        addError.accept(
            Error.of(Rule.MAX_ITEMS, value, "must have <= %d items but had: %d", count, actual));
    }
  }

  private record UniqueItems() implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      if (!value.isArray() || value.isEmpty()) return;
      int size = value.size();
      for (int i = 0; i < size; i++)
        for (int j = 1; j < size; j++)
          if (i != j && value.get(i).equivalentTo(value.get(j))) {
            addError.accept(
                Error.of(
                    Rule.UNIQUE_ITEMS,
                    value,
                    "items must be unique but %s was found at index %d and %d",
                    value.get(i).node().getDeclaration(),
                    i,
                    j));
              return;
            }
    }
  }

  /*
  object values
   */

  private record MinProperties(int count) implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      if (!value.isObject()) return;
      int actual = value.size();
      if (actual < count)
        addError.accept(
            Error.of(
                Rule.MIN_PROPERTIES,
                value,
                "must have >= %d properties but has: %d",
                count,
                actual));
    }
  }

  private record MaxProperties(int count) implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      if (!value.isObject()) return;
      int actual = value.size();
      if (actual > count)
        addError.accept(
            Error.of(
                Rule.MAX_PROPERTIES,
                value,
                "must have <= %d properties but has: %d",
                count,
                actual));
    }
  }

  private record DependentRequired(
      List<Trigger> triggers, List<Dependent> nonExclusive, List<Dependent> exclusive)
      implements Validator {

    record Trigger(Text name, Predicate<JsonMixed> condition, String description) {}
    record Dependent(Text name, Predicate<JsonMixed> present) {}

    @Override
    public void validate(JsonMixed obj, Consumer<Error> addError) {
      if (!obj.isObject()) return;
      if (!triggers.isEmpty()) {
        // any trigger not met => done
        for (Trigger trigger : triggers)
          if (!trigger.condition.test(obj.get(trigger.name))) return;
      } else {
        // codependent: all absent? => done
        if (!nonExclusive.isEmpty() && nonExclusive.stream().noneMatch(d -> d.present.test(obj.get(d.name)))) return;
      }
      // check dependents...
      if (!nonExclusive.isEmpty() && nonExclusive.stream().anyMatch(d -> !d.present.test(obj.get(d.name)))) {
        List<Text> missing =
            nonExclusive.stream()
                .filter(d -> !d.present.test(obj.get(d.name)))
                .map(Dependent::name)
                .toList();
        addError.accept(
            Error.of(
                Rule.DEPENDENT_REQUIRED,
                obj,
                "object %s requires all of %s, missing: %s",
                triggers.stream().map(Trigger::description).collect(joining(", ")),
                Set.copyOf(nonExclusive.stream().map(Dependent::name).toList()),
                Set.copyOf(missing)));
      }
      // check exclusivity
      else if (!exclusive.isEmpty()) {
        List<Text> found =
            exclusive.stream()
                .filter(d -> d.present.test(obj.get(d.name)))
                .map(Dependent::name)
                .toList();
        if (found.size() != 1)
          addError.accept(
              Error.of(
                  Rule.DEPENDENT_REQUIRED,
                  obj,
                  "object %s requires one but only one of %s, but has: %s",
                  triggers.stream().map(Trigger::description).collect(joining(", ")),
                  Set.copyOf(exclusive.stream().map(Dependent::name).toList()),
                  Set.copyOf(found)));
      }
    }
  }

  private record Lazy(Class<?> of, AtomicReference<Validator> instance) implements Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Error> addError) {
      Validator validator = instance.get();
      if (validator == null) {
        validator = ObjectValidator.of(of);
        instance.set(validator);
      }
      validator.validate(value, addError);
    }
  }
}
