package org.hisp.dhis.jsontree.validation;

import static java.lang.Double.isNaN;
import static java.util.Comparator.comparing;
import static org.hisp.dhis.jsontree.Validation.NodeType.ARRAY;
import static org.hisp.dhis.jsontree.Validation.NodeType.BOOLEAN;
import static org.hisp.dhis.jsontree.Validation.NodeType.INTEGER;
import static org.hisp.dhis.jsontree.Validation.NodeType.NULL;
import static org.hisp.dhis.jsontree.Validation.NodeType.NUMBER;
import static org.hisp.dhis.jsontree.Validation.NodeType.OBJECT;
import static org.hisp.dhis.jsontree.Validation.NodeType.STRING;
import static org.hisp.dhis.jsontree.Validation.YesNo.AUTO;
import static org.hisp.dhis.jsontree.Validation.YesNo.YES;

import java.lang.System.Logger.Level;
import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedParameterizedType;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Stream;

import org.hisp.dhis.jsontree.InputExpression;
import org.hisp.dhis.jsontree.JsonObject;
import org.hisp.dhis.jsontree.JsonValue;
import org.hisp.dhis.jsontree.Text;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.YesNo;
import org.hisp.dhis.jsontree.Validator;
import org.hisp.dhis.jsontree.internal.CheckNull;
import org.hisp.dhis.jsontree.internal.NotNull;
import org.hisp.dhis.jsontree.validation.PropertyValidations.ArrayValidation;
import org.hisp.dhis.jsontree.validation.PropertyValidations.NumberValidation;
import org.hisp.dhis.jsontree.validation.PropertyValidations.StringValidation;
import org.hisp.dhis.jsontree.validation.PropertyValidations.ValueValidation;

/**
 * Analysis types and annotations to extract a {@link PropertyValidations} model description.
 *
 * @author Jan Bernitt
 * @since 0.11
 * @param schema the type the validation is based upon
 * @param types Java target type by JSON name
 * @param properties validation for each property by JSON name
 */
record ObjectValidation(
    @NotNull Class<?> schema,
    @NotNull Map<Text, Type> types,
    @NotNull Map<Text, PropertyValidations> properties) {

  /** Cache for all validation applied to a particular object schema. */
  private static final Map<Class<?>, ObjectValidation> INSTANCES = new ConcurrentHashMap<>();

  /**
   * A cache for the validations set for any use (mapping target) of a particular Java type from an
   * annotation put on the type declaration itself.
   */
  private static final Map<Class<?>, PropertyValidations> TYPE_DECLARATION_VALIDATIONS =
      new ConcurrentSkipListMap<>(comparing(Class::getName));

  /**
   * Meta annotations are those that are themselves annotated {@link Validation} or one or more
   * meta-annotations which they aggregate. The map caches the validation set for a particular
   * meta-annotation type.
   */
  private static final Map<Class<? extends Annotation>, PropertyValidations>
      META_TYPE_BY_VALIDATIONS = new ConcurrentHashMap<>();

  /**
   * Resolves the node validations to apply to a value of the provided schema type.
   *
   * @param schema a type representing a JSON structure node
   * @return a map of validations to apply to each property for the provided schema and the type
   *     itself (root = empty string property)
   */
  @NotNull
  public static ObjectValidation of(Class<?> schema) {
    if (!JsonObject.class.isAssignableFrom(schema) && !Record.class.isAssignableFrom(schema))
      throw new UnsupportedOperationException(
          "Must be a subtype of JsonObject or Record but was: " + schema);
    return INSTANCES.computeIfAbsent(schema, t -> createInstance(t, JsonObject.properties(schema)));
  }

  private static ObjectValidation createInstance(
      Class<?> schema, List<JsonObject.Property> properties) {
    Map<Text, PropertyValidations> validations = new HashMap<>();
    Map<Text, Type> types = new HashMap<>();
    properties.stream()
        .filter(ObjectValidation::isNotIgnored)
        .forEach(
            p -> {
              validations.put(p.jsonName(), fromProperty(p));
              types.put(p.jsonName(), p.javaType().getType());
            });
    return new ObjectValidation(schema, Map.copyOf(types), Map.copyOf(validations));
  }

  private static boolean isNotIgnored(JsonObject.Property p) {
    return !p.source().isAnnotationPresent(Validation.Ignore.class)
        && !p.in().isAnnotationPresent(Validation.Ignore.class);
  }

  @CheckNull
  private static PropertyValidations fromProperty(JsonObject.Property p) {
    PropertyValidations onMethod = fromAnnotations(p.source());
    PropertyValidations onReturnType = fromValueTypeUse(p.javaType());
    if (onMethod == null) return onReturnType;
    if (onReturnType == null) return onMethod;
    return onMethod.overlay(onReturnType);
  }

  /**
   * @param src as it occurs for the property method, may be null
   * @return validation based on the Java value type (this includes annotations on the class type)
   */
  @CheckNull
  private static PropertyValidations fromValueTypeUse(AnnotatedType src) {
    Type type = src.getType();
    if (type instanceof Class<?> simpleType)
      return fromValueTypeDeclaration(simpleType).overlay(fromAnnotations(src));
    // TODO(future) AnnotatedArrayType...
    if (!(src instanceof AnnotatedParameterizedType pt)) return null;
    Type rt = ((ParameterizedType) pt.getType()).getRawType();
    Class<?> rawType = (Class<?>) rt;
    PropertyValidations base = fromValueTypeDeclaration(rawType).overlay(fromAnnotations(src));
    AnnotatedType[] typeArguments = pt.getAnnotatedActualTypeArguments();
    if (typeArguments.length == 1) return base.withItems(fromValueTypeUse(typeArguments[0]));
    if (Map.class.isAssignableFrom(rawType)) {
      // TODO make use of "propertyNames" (schema field) for key restrictions
      return base.withItems(fromValueTypeUse(typeArguments[1]));
    }
    return base;
  }

  @NotNull
  private static PropertyValidations fromValueTypeDeclaration(@NotNull Class<?> type) {
    return TYPE_DECLARATION_VALIDATIONS.computeIfAbsent(
        type,
        t -> {
          PropertyValidations declared = fromAnnotations(t);
          PropertyValidations inferred = declared != null ? declared : toPropertyValidation(t);
          if (Object[].class.isAssignableFrom(t))
            return inferred.withItems(fromValueTypeDeclaration(t.getComponentType()));
          return inferred;
        });
  }

  @CheckNull
  private static PropertyValidations fromAnnotations(AnnotatedElement src) {
    PropertyValidations meta = fromMetaAnnotations(src);
    Validation validation = getValidationAnnotation(src);
    PropertyValidations main = validation == null ? null : toPropertyValidation(validation);
    List<Validation.Validator> validators = toValidators(src);
    PropertyValidations items = fromItems(src);
    PropertyValidations base = meta == null ? main : meta.overlay(main);
    if (base == null && items == null && validators.isEmpty()) return null;
    if (base == null)
      base =
          new PropertyValidations(
              Set.of(),
              PropertyValidations.Strict.DEFAULT,
              null,
              null,
              null,
              null,
              null,
              null);
    return base.withCustoms(validators).withItems(items);
  }

  @CheckNull
  private static Validation getValidationAnnotation(AnnotatedElement src) {
    Validation a = src.getAnnotation(Validation.class);
    if (a != null) return a;
    if (!(src instanceof Class<?> c)) return null;
    for (Class<?> si : c.getInterfaces()) {
      a = getValidationAnnotation(si);
      if (a != null) return a;
    }
    return null;
  }

  @CheckNull
  private static PropertyValidations fromMetaAnnotations(AnnotatedElement src) {
    Annotation[] candidates = src.getAnnotations();
    if (candidates.length == 0) return null;
    return Stream.of(candidates)
        .sorted(comparing(a -> a.annotationType().getSimpleName()))
        .map(ObjectValidation::fromMetaAnnotation)
        .filter(Objects::nonNull)
        .reduce(PropertyValidations::overlay)
        .orElse(null);
  }

  @CheckNull
  private static PropertyValidations fromMetaAnnotation(Annotation a) {
    Class<? extends Annotation> type = a.annotationType();
    if (!type.isAnnotationPresent(Validation.class)) return null;
    return META_TYPE_BY_VALIDATIONS.computeIfAbsent(
        type,
        t ->
            toPropertyValidation(t.getAnnotation(Validation.class))
                .withCustoms(toValidators(t))
                .withItems(fromItems(t)));
  }

  @CheckNull
  private static PropertyValidations fromItems(AnnotatedElement src) {
    if (!src.isAnnotationPresent(Validation.Items.class)) return null;
    return toPropertyValidation(src.getAnnotation(Validation.Items.class).value());
  }

  @NotNull
  private static PropertyValidations toPropertyValidation(Class<?> type) {
    ValueValidation values =
        !type.isPrimitive() ? null : new ValueValidation(YES, Set.of(), YesNo.AUTO, Set.of(), List.of());
    StringValidation strings =
        !type.isEnum() ? null : new StringValidation(anyOfStrings(type), YesNo.AUTO, -1, -1, null);
    return new PropertyValidations(
        anyOfTypes(type), PropertyValidations.Strict.DEFAULT, values, strings, null, null, null, null);
  }

  @NotNull
  private static PropertyValidations toPropertyValidation(@NotNull Validation src) {
    PropertyValidations res =
        new PropertyValidations(
            anyOfTypes(src),
            toStrict(src.strictness()),
            toValueValidation(src),
            toStringValidation(src),
            toNumberValidation(src),
            toArrayValidation(src),
            toObjectValidation(src),
            null);
    return src.varargs().isYes() ? res.varargs() : res;
  }

  private static PropertyValidations.Strict toStrict(Validation.Strict src) {
    YesNo booleans = src.booleans();
    YesNo strings = src.strings();
    YesNo numbers = src.numbers();
    if (src.value()) {
      if (booleans == AUTO) booleans = YES;
      if (strings == AUTO) strings = YES;
      if (numbers == AUTO) numbers = YES;
    }
    return new PropertyValidations.Strict(booleans, strings, numbers);
  }

  @CheckNull
  private static ValueValidation toValueValidation(@NotNull Validation src) {
    boolean oneOfValuesEmpty =
        src.oneOfValues().length == 0 || isAutoUnquotedJsonStrings(src.oneOfValues());
    boolean dependentRequiresEmpty = src.dependentRequired().length == 0;
    if (src.required().isAuto()
        && oneOfValuesEmpty
        && dependentRequiresEmpty
        && src.acceptNull().isAuto()) return null;
    Set<String> oneOfValues =
        oneOfValuesEmpty
            ? Set.of()
            : Set.copyOf(
                Stream.of(src.oneOfValues()).map(e -> JsonValue.of(e).toMinimizedJson()).toList());
    return new ValueValidation(
        src.required(), Set.of(src.dependentRequired()), src.acceptNull(), oneOfValues, List.of());
  }

  private static boolean isAutoUnquotedJsonStrings(String[] values) {
    return values.length > 0
        && Stream.of(values)
            .allMatch(
                v ->
                    !v.isEmpty()
                        && Character.isLetter(v.charAt(0))
                        && !"true".equals(v)
                        && !"false".equals(v)
                        && !"null".equals(v));
  }

  @CheckNull
  private static StringValidation toStringValidation(@NotNull Validation src) {
    if (src.enumeration() == Enum.class
        && src.minLength() < 0
        && src.maxLength() < 0
        && src.pattern().length == 0
        && src.caseInsensitive().isAuto()
        && !isAutoUnquotedJsonStrings(src.oneOfValues())) return null;
    Set<String> anyOfStrings = anyOfStrings(src.enumeration());
    if (anyOfStrings.isEmpty() && isAutoUnquotedJsonStrings(src.oneOfValues()))
      anyOfStrings = Set.of(src.oneOfValues());
    InputExpression pattern = src.pattern().length == 0 ? null : InputExpression.of(src.pattern());
    return new StringValidation(
        anyOfStrings, src.caseInsensitive(), src.minLength(), src.maxLength(), pattern);
  }

  private static Set<String> anyOfStrings(@NotNull Class<?> type) {
    return type == Enum.class || !type.isEnum()
        ? Set.of()
        : Set.copyOf(Stream.of(type.getEnumConstants()).map(e -> ((Enum<?>) e).name()).toList());
  }

  @CheckNull
  private static NumberValidation toNumberValidation(@NotNull Validation src) {
    if (isNaN(src.minimum())
        && isNaN(src.maximum())
        && isNaN(src.exclusiveMinimum())
        && isNaN(src.exclusiveMaximum())
        && isNaN(src.multipleOf())) return null;
    return new NumberValidation(
        src.minimum(),
        src.maximum(),
        src.exclusiveMinimum(),
        src.exclusiveMaximum(),
        src.multipleOf());
  }

  @CheckNull
  private static ArrayValidation toArrayValidation(@NotNull Validation src) {
    if (src.minItems() < 0 && src.maxItems() < 0 && src.uniqueItems().isAuto()) return null;
    return new ArrayValidation(src.minItems(), src.maxItems(), src.uniqueItems());
  }

  @CheckNull
  private static PropertyValidations.ObjectValidation toObjectValidation(@NotNull Validation src) {
    if (src.minProperties() < 0 && src.maxProperties() < 0) return null;
    return new PropertyValidations.ObjectValidation(src.minProperties(), src.maxProperties());
  }

  @NotNull
  private static List<Validation.Validator> toValidators(@NotNull AnnotatedElement src) {
    Validator[] validators = src.getAnnotationsByType(Validator.class);
    if (validators.length == 0) return List.of();
    return Stream.of(validators)
        .map(ObjectValidation::toValidator)
        .filter(Objects::nonNull)
        .toList();
  }

  private static final System.Logger log = System.getLogger(ObjectValidation.class.getName());

  @CheckNull
  private static Validation.Validator toValidator(@NotNull Validator src) {
    Class<? extends Validation.Validator> type = src.value();
    if (!type.isRecord()) {
      log.log(Level.ERROR, "Validator ignored, it must be record type but was: " + type);
      return null;
    }
    Validation[] params = src.params();
    if (params.length == 0) return newValidator(type, new Class[0], new Object[0]);
    if (params.length == 1)
      return newValidator(type, new Class<?>[] {Validation.class}, new Object[] {params[0]});
    return newValidator(type, new Class<?>[] {Validation[].class}, new Object[] {params});
  }

  private static Validation.Validator newValidator(Class<?> type, Class<?>[] types, Object[] args) {
    try {
      return (Validation.Validator)
          MethodHandles.lookup()
              .findConstructor(type, MethodType.methodType(void.class, types))
              .asFixedArity()
              .invokeWithArguments(args);
    } catch (Throwable ex) {
      log.log(Level.ERROR, "Validator ignored, failed to construct instance", ex);
      return null;
    }
  }

  @NotNull
  private static Set<NodeType> anyOfTypes(@NotNull Validation src) {
    return anyOfTypes(src.type());
  }

  @NotNull
  private static Set<NodeType> anyOfTypes(NodeType... type) {
    if (type.length == 0) return Set.of();
    Set<NodeType> anyOf = EnumSet.of(type[0], type);
    if (anyOf.contains(NodeType.INTEGER) && anyOf.contains(NodeType.NUMBER))
      anyOf.remove(NodeType.INTEGER);
    return Set.copyOf(anyOf);
  }

  @NotNull
  private static Set<NodeType> anyOfTypes(Class<?> type) {
    NodeType main = nodeTypeOf(type);
    if (type == Date.class && main != null) return Set.of(main, INTEGER);
    if (main != null) return Set.of(main);
    return Set.of();
  }

  @CheckNull
  private static NodeType nodeTypeOf(@NotNull Class<?> type) {
    if (type == String.class || type.isEnum() || type == Date.class) return STRING;
    if (type == Character.class || type == char.class) return STRING;
    if (type == Boolean.class || type == boolean.class) return BOOLEAN;
    if (type == Integer.class || type == Long.class || type == BigInteger.class)
      return NodeType.INTEGER;
    if (Number.class.isAssignableFrom(type)) return NUMBER;
    if (type == Void.class || type == void.class) return NULL;
    if (type.isPrimitive())
      return type == float.class || type == double.class ? NUMBER : NodeType.INTEGER;
    if (Collection.class.isAssignableFrom(type)) return ARRAY;
    if (Object[].class.isAssignableFrom(type)) return ARRAY;
    if (Map.class.isAssignableFrom(type)) return OBJECT;
    return null;
  }
}
