package org.hisp.dhis.jsontree.validation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.JsonPath;
import org.hisp.dhis.jsontree.JsonPathException;
import org.hisp.dhis.jsontree.JsonSchemaException;
import org.hisp.dhis.jsontree.Validation.Result;
import org.hisp.dhis.jsontree.JsonTreeException;
import org.hisp.dhis.jsontree.JsonValue;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validation.Error;

/**
 * @author Jan Bernitt
 * @since 0.11
 */
public final class JsonValidator {

  public static Result validate(JsonValue value, Class<?> schema, Validation.Mode mode, Validation.Rule[] rules) {
    Set<Validation.Rule> set =
        rules.length == 0 ? EnumSet.allOf(Validation.Rule.class) : EnumSet.of(rules[0], rules);
    return validate(value, schema, mode, set);
  }

  private static Result validate(JsonValue value, Class<?> schema, Validation.Mode mode, Set<Validation.Rule> rules) {
    if (!value.exists())
      throw new JsonPathException(
          value.path(),
          String.format(
              "Value at path `%s` is not a %s object as it does not exist",
              value.path(), schema.getSimpleName()));
    if (!value.isObject()) {
      throw new JsonTreeException(
          String.format(
              "Value at path `%s` is not a %s object but a %s",
              value.path(), schema.getSimpleName(), value.type()));
    }
    ObjectValidator validator = ObjectValidator.of(schema);
    if (validator.properties().isEmpty()) return new Result(value, schema, rules, List.of());

    boolean failFast = mode.isFailFast();
    List<Error> errors = failFast ? List.of() : new ArrayList<>();
    Consumer<Error> addError =
        error -> {
          if (rules.contains(error.rule())) {
            if (!failFast) {
              errors.add(error);
            } else
              throw new JsonSchemaException(
                  "fail fast", new Result(value, schema, rules, List.of(error)));
          }
        };

    // TODO strict types vs convertable types mode
    if (mode == Validation.Mode.PROBE) {
      try {
        validate(value, validator, addError);
        return new Result(value, schema, rules, List.of());
      } catch (JsonSchemaException ex) {
        return ex.getResult();
      }
    } else {
      validate(value, validator, addError);
      Result result = new Result(value, schema, rules, errors);
      if (mode != Validation.Mode.PROBE_ALL)
        if (!errors.isEmpty())
          throw new JsonSchemaException("%d errors".formatted(errors.size()), result);
      return result;
    }
  }

  private static void validate(JsonValue value, ObjectValidator validator, Consumer<Error> addError) {
    for (Map.Entry<JsonPath, Validation.Validator> e : validator.properties().entrySet()) {
      JsonMixed property = value.asObject().get(e.getKey(), JsonMixed.class);
      e.getValue().validate(property, addError);
    }
  }

  // TODO a publicly accessible way to get the JSON Schema validation description (JSON) for a
  // schema class
  // and also for classes used as values like UID to get what the validation is on these in
  // isolation for OpenAPI

  // TODO a mode or setting that allows to skip validation on parts that are about stream processing
  // like Stream<X> or Iterator<X> types where the validation would rack the benefits
  // of stream processing the items
}
