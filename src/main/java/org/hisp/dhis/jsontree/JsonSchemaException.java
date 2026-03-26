package org.hisp.dhis.jsontree;

import static java.util.stream.Collectors.joining;

import java.util.List;

/** Thrown when an JSON input does not match its JSON schema description. */
public final class JsonSchemaException extends IllegalArgumentException {

  private final transient Validation.Result result;

  public JsonSchemaException(String message, Validation.Result result) {
    super(message + toString(result.errors()));
    this.result = result;
  }

  public Validation.Result getResult() {
    return result;
  }

  private static String toString(List<Validation.Error> errors) {
    return errors.stream().map(e -> "\n\t" + e.toString()).collect(joining());
  }
}
