package org.hisp.dhis.jsontree.internal;

import org.hisp.dhis.jsontree.JsonNode;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker for methods in {@link org.hisp.dhis.jsontree.JsonValue} API that cause a lookup of the
 * {@link JsonNode} and as such can fail with an exception because the virtual expectation
 * does not match the actual JSON.
 */
@Retention(RetentionPolicy.SOURCE)
@Target(ElementType.METHOD)
public @interface TerminalOp {

  /**
   * @return an additional flag for terminal operations that allow JSON null as a valid value which
   *     maps to Java null
   */
  boolean canBeNull() default false;

  /**
   * @return an additional flag for terminal operations that return a default in case the JSON does
   *     not define the value (this excludes being defined as JSON null). This always implies {@link
   *     #canBeNull()}.
   */
  boolean canBeUndefined() default false;

  /**
   * @return when true, the node must be an array. When used with {@link #mustBeObject()} objects
   *     are also permitted. Even without that flag set there is an array-object-duality that at
   *     least treats empty objects as empty arrays.
   *     When used with {@link #canBeUndefined()} JSON null and undefined is also permitted.
   *     When used with {@link #canBeNull()} JSON null is permitted.
   */
  boolean mustBeArray() default false;

  boolean mustBeObject() default false;
}
