package org.hisp.dhis.jsontree;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * An annotation to use custom {@link Validation.Validator}s.
 * <p>
 * Any type linked this way must have either a no-args constructor or a constructor accepting a {@link String} array.
 * The value of that array is the {@link #params()} property of the annotation to instantiate the validator for a
 * specific setting, like a limit.
 *
 * @author Jan Bernitt
 * @since 0.11
 */
@Repeatable( Validator.ValidatorRepeat.class )
@Retention( RetentionPolicy.RUNTIME )
@Target( { ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.TYPE_USE } )
public @interface Validator {

    /**
     * @return must also be a {@link Record} class
     */
    Class<? extends Validation.Validator> value();

    // maybe make this a @Validation? as arguments?
    String[] params() default {};

    @Retention( RetentionPolicy.RUNTIME )
    @Target( { ElementType.METHOD, ElementType.ANNOTATION_TYPE, ElementType.TYPE, ElementType.TYPE_USE } )
    @interface ValidatorRepeat {

        Validator[] value();
    }

    //IDEA: use Class<? extends Validator>[] validator() default {} in @Validation
    // and each of them gets the @Validation instance passed to the constructor
    // which can be used for parameters
}
