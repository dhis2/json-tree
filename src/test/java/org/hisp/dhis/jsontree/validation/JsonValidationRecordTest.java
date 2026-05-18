package org.hisp.dhis.jsontree.validation;

import static org.hisp.dhis.jsontree.Assertions.assertValidationError;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.function.Consumer;

import org.hisp.dhis.jsontree.Collapsed;
import org.hisp.dhis.jsontree.JsonMixed;
import org.hisp.dhis.jsontree.Validation;
import org.hisp.dhis.jsontree.Validator;
import org.junit.jupiter.api.Test;

/**
 * Tests validation of the {@link org.hisp.dhis.jsontree.Validation} when used on a {@link Record}
 * type.
 *
 * @author Jan Bernitt
 */
class JsonValidationRecordTest {

  record SomeBean(
      @Validation(minimum = 0) int age,
      @Validator(CustomValidator.class) String pattern,
      @Collapsed InnerBean inner) {}

  record InnerBean(@Validation(minLength = 10, required = Validation.YesNo.NO) String name) {}

  record CustomValidator() implements Validation.Validator {

    @Override
    public void validate(JsonMixed value, Consumer<Validation.Error> addError) {
      if (value.isString() && !value.string().equals("foo"))
        addError.accept(
            Validation.Error.of(
                Validation.Rule.CUSTOM, value, "must be foo bus was: %s", value.string()));
    }
  }

  @Test
  void testRecord_Minimum_OK() {
    assertDoesNotThrow(
        () ->
            JsonMixed.of(
                    """
            {"age": 22}""")
                .validate(SomeBean.class));
  }

  @Test
  void testRecord_Minimum_Required() {
    assertValidationError("{}", SomeBean.class, Validation.Rule.REQUIRED, "age");
  }

  @Test
  void testRecord_Custom_Validator() {
    assertValidationError(
        """
            {"age": 10, "pattern": "bar"}""",
        SomeBean.class,
        Validation.Rule.CUSTOM,
        "bar");
  }

  @Test
  void testRecord_CollapsedValidation() {
    assertValidationError(
        """
            {"age": 10, "pattern": "foo", "name": "tooShort"}""",
        SomeBean.class,
        Validation.Rule.MIN_LENGTH,
        10, 8);
  }
}
