package org.hisp.dhis.jsontree.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Something that should never be null.
 *
 * @see CheckNull
 */
@Target({ ElementType.RECORD_COMPONENT, ElementType.PARAMETER, ElementType.FIELD, ElementType.METHOD, ElementType.TYPE_USE})
public @interface NotNull {}
