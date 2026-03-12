package org.hisp.dhis.jsontree;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to use custom {@link Validation.Validator}s.
 *
 * <p>Any type linked in {@link #value()} must be a {@link Record} and have either no components, 1
 * component of type {@link Validation} or 1 component of type {@link Validation[]}. The value of
 * that component will be the {@link #params()} property from the actual annotation. This allows to
 * parameterize validators with some options. Which options are recognized and their semantics
 * depends on the implementation. In this role the {@link Validation} does not represent the
 * semantics of the standard but merely acts as a utility to allow parametrisation.
 *
 * @author Jan Bernitt
 * @since 0.11
 */
@Repeatable(Validator.ValidatorRepeat.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.TYPE_USE})
public @interface Validator {

  /**
   * @return must also be a {@link Record} class
   */
  Class<? extends Validation.Validator> value();

  Validation[] params() default {};

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.TYPE_USE})
  @interface ValidatorRepeat {

    Validator[] value();
  }
}
