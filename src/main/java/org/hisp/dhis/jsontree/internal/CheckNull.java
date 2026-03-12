package org.hisp.dhis.jsontree.internal;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Something that could be null.
 *
 * @see NotNull
 */
@Target({ElementType.TYPE_USE})
public @interface CheckNull {}
