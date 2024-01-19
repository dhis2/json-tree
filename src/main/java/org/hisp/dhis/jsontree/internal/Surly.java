package org.hisp.dhis.jsontree.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Something that should never be null.
 *
 * @see Maybe
 */
@Target( { ElementType.PARAMETER, ElementType.METHOD, ElementType.FIELD } )
public @interface Surly {
}
