package org.hisp.dhis.jsontree.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Something that should never be null.
 *
 * @see CheckNull
 */
@Target({ElementType.TYPE_USE})
public @interface NotNull {}
