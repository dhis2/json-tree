package org.hisp.dhis.jsontree.validation;

import static java.lang.Double.isNaN;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.hisp.dhis.jsontree.InputExpression;
import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonValue;
import org.hisp.dhis.jsontree.Validation.NodeType;
import org.hisp.dhis.jsontree.Validation.Validator;
import org.hisp.dhis.jsontree.Validation.YesNo;
import org.hisp.dhis.jsontree.internal.CheckNull;
import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * A declarative model or description of what validation rules to check for a single property within
 * an object.
 *
 * @param accepted the node types accepted/expected
 * @param customs a validator defined by class is used (custom or user defined validators), empty
 *     list is off
 * @param requiredness validations about the property being required or not (presence)
 * @param strings validations that apply to string nodes
 * @param numbers validations that apply to number nodes
 * @param arrays validations that apply to array nodes
 * @param objects validations that apply to object nodes
 * @param items validations that apply to array elements or object member values (map use)
 */
record PropertyValidations(
    @NotNull Set<NodeType> accepted,
    @NotNull Strict strictness,
    @NotNull List<Validator> customs,
    @CheckNull RequiredValidation requiredness,
    @CheckNull StringValidation strings,
    @CheckNull NumberValidation numbers,
    @CheckNull ArrayValidation arrays,
    @CheckNull ObjectValidation objects,
    @CheckNull PropertyValidations items) {

  record Strict(YesNo booleans, YesNo strings, YesNo numbers) {
    public static final Strict DEFAULT = new Strict(YesNo.AUTO, YesNo.AUTO, YesNo.AUTO);

    Strict overlay(Strict with) {
      return new Strict(
          overlayY(booleans, with.booleans),
          overlayY(strings, with.strings),
          overlayY(numbers, with.numbers));
    }
  }

  /**
   * Layers the provided validations on top of this. This means they take precedence unless they are
   * defined off.
   *
   * @param with the validations that take precedence of this
   * @return A new node validations with all validations that took precedence from the provided
   *     parameter and this node validations acting as fallback when a validation is defined off
   */
  @NotNull
  PropertyValidations overlay(@CheckNull PropertyValidations with) {
    if (with == null) return this;
    return new PropertyValidations(
        overlayC(accepted, with.accepted),
        strictness.overlay(with.strictness),
        overlayEachClassAtMostOnce(customs, with.customs),
        requiredness == null ? with.requiredness : requiredness.overlay(with.requiredness),
        strings == null ? with.strings : strings.overlay(with.strings),
        numbers == null ? with.numbers : numbers.overlay(with.numbers),
        arrays == null ? with.arrays : arrays.overlay(with.arrays),
        objects == null ? with.objects : objects.overlay(with.objects),
        items == null ? with.items : items.overlay(with.items));
  }

  @NotNull
  PropertyValidations withItems(@CheckNull PropertyValidations items) {
    if (items == null && this.items == null) return this;
    return new PropertyValidations(
        accepted, strictness, customs, requiredness, strings, numbers, arrays, objects, items);
  }

  @NotNull
  PropertyValidations withCustoms(@NotNull List<Validator> validators) {
    List<Validator> merged = overlayEachClassAtMostOnce(customs, validators);
    return new PropertyValidations(
        accepted, strictness, merged, requiredness, strings, numbers, arrays, objects, items);
  }

  @NotNull
  public PropertyValidations varargs() {
    Set<NodeType> newTypes = new HashSet<>(accepted());
    newTypes.add(NodeType.ARRAY);
    ArrayValidation arrays = this.arrays;
    if (requiredness != null && requiredness.required.isYes()) {
      arrays =
          this.arrays == null ? new ArrayValidation(1, -1, YesNo.AUTO) : this.arrays.required();
    }
    return new PropertyValidations(
        Set.copyOf(newTypes),
        strictness,
        customs,
        requiredness,
        strings,
        numbers,
        arrays,
        objects,
        new PropertyValidations(
            accepted(), strictness, customs, requiredness, strings, numbers, null, objects, items));
  }

  /**
   * Validations that apply to any node type.
   *
   * @param required is the value required to exist or is undefined/null OK, non {@link YesNo#YES}
   *     is off
   * @param dependentRequired the groups this property is a member of for dependent requires
   * @param allowNull when {@link YesNo#YES} a JSON {@code null} value satisfies being {@link
   *     #required()} or {@link #dependentRequired()}
   */
  record RequiredValidation(
      @NotNull YesNo required, @NotNull Set<String> dependentRequired, @NotNull YesNo allowNull) {

    RequiredValidation overlay(@CheckNull PropertyValidations.RequiredValidation with) {
      return with == null
          ? this
          : new RequiredValidation(
              overlayY(required, with.required),
              overlayC(dependentRequired, with.dependentRequired),
              overlayY(allowNull, with.allowNull));
    }

    Predicate<JsonMixed> present() {
      return allowNull.isYes() ? JsonValue::exists : not(JsonValue::isUndefined);
    }

    Predicate<JsonMixed> absent() {
      return allowNull.isYes() ? not(JsonValue::exists) : JsonValue::isUndefined;
    }
  }

  /**
   * Validations that apply to string nodes.
   *
   * @param anyOfStrings JSON string value must be one of the enum names
   * @param caseInsensitive test {@link #anyOfStrings} with {@link String#equalsIgnoreCase(String)}
   * @param minLength minimum length for the JSON string, negative is off
   * @param maxLength maximum length for the JSON string, negative is off
   * @param pattern JSON string must match the provided pattern, null is off
   */
  record StringValidation(
      @NotNull Set<String> anyOfStrings,
      YesNo caseInsensitive,
      int minLength,
      int maxLength,
      @CheckNull InputExpression pattern) {
    StringValidation overlay(@CheckNull StringValidation with) {
      return with == null
          ? this
          : new StringValidation(
              overlayC(anyOfStrings, with.anyOfStrings),
              overlayY(caseInsensitive, with.caseInsensitive),
              overlayI(minLength, with.minLength),
              overlayI(maxLength, with.maxLength),
              overlayO(pattern, with.pattern));
    }
  }

  /**
   * Validations that apply to number nodes.
   *
   * @param minimum JSON number must be greater than or equal to this lower limit, NaN is off
   * @param maximum JSON number must be less than or equal to this upper limit, NaN is off
   * @param exclusiveMinimum JSON number value must be larger than the given value, NaN is off
   * @param exclusiveMaximum JSON number value must be smaller than the given value, NaN is off
   * @param multipleOf JSON number value must be divisible by the given value without rest, NaN is
   *     off
   */
  record NumberValidation(
      double minimum,
      double maximum,
      double exclusiveMinimum,
      double exclusiveMaximum,
      double multipleOf) {

    NumberValidation overlay(@CheckNull NumberValidation with) {
      return with == null
          ? this
          : new NumberValidation(
              overlayD(minimum, with.minimum),
              overlayD(maximum, with.maximum),
              overlayD(exclusiveMinimum, with.exclusiveMinimum),
              overlayD(exclusiveMaximum, with.exclusiveMaximum),
              overlayD(multipleOf, with.multipleOf));
    }
  }

  /**
   * Validations that apply to array nodes.
   *
   * @param minItems JSON array must have at least this many elements, negative is off
   * @param maxItems JSON array must have at most this many elements, negative is off
   * @param uniqueItems all elements in the JSON array value must be unique, false is off
   */
  record ArrayValidation(int minItems, int maxItems, @NotNull YesNo uniqueItems) {

    ArrayValidation overlay(@CheckNull ArrayValidation with) {
      return with == null
          ? this
          : new ArrayValidation(
              overlayI(minItems, with.minItems),
              overlayI(maxItems, with.maxItems),
              overlayY(uniqueItems, with.uniqueItems));
    }

    /**
     * @return Same array validation but the array will be required to have at least 1 element
     */
    public ArrayValidation required() {
      return new ArrayValidation(Math.max(1, minItems), maxItems, uniqueItems);
    }
  }

  /**
   * Validations that apply to object nodes.
   *
   * @param minProperties JSON object must have at least this many properties, negative is off
   * @param maxProperties JSON object must have at most this many properties, negative is off
   */
  record ObjectValidation(int minProperties, int maxProperties) {

    ObjectValidation overlay(@CheckNull ObjectValidation with) {
      return with == null
          ? this
          : new ObjectValidation(
              overlayI(minProperties, with.minProperties),
              overlayI(maxProperties, with.maxProperties));
    }
  }

  private static YesNo overlayY(YesNo a, YesNo b) {
    if (a == b || a == YesNo.AUTO) return b;
    if (b == YesNo.AUTO) return a;
    return b;
  }

  private static <T> T overlayO(T a, T b) {
    if (b != null) return b;
    if (a != null) return a;
    return b;
  }

  private static int overlayI(int a, int b) {
    if (b >= 0) return b;
    if (a >= 0) return a;
    return b;
  }

  private static double overlayD(double a, double b) {
    if (!isNaN(b)) return b;
    if (!isNaN(a)) return a;
    return b;
  }

  private static <T, C extends Collection<T>> C overlayC(C a, C b) {
    if (!b.isEmpty()) return b;
    if (!a.isEmpty()) return a;
    return b;
  }

  private static <E> List<E> overlayEachClassAtMostOnce(List<E> a, List<E> b) {
    if (b.isEmpty()) return a;
    if (a.isEmpty()) return b;
    Set<Class<?>> bs = b.stream().map(Object::getClass).collect(toSet());
    return Stream.concat(b.stream(), a.stream().filter(e -> !bs.contains(e.getClass()))).toList();
  }
}
