package org.hisp.dhis.jsontree.internal;

import java.lang.annotation.*;

/** substitute for jetbrains @Language */
@Target({ElementType.PARAMETER, ElementType.FIELD, ElementType.LOCAL_VARIABLE, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface Language {
  String value();
}
