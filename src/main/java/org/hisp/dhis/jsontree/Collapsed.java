package org.hisp.dhis.jsontree;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker annotation for {@link java.lang.reflect.RecordComponent}s that are themselves {@link
 * Record}s whose components should be treated as if they were directly defined in the parent record
 * type when mapping JSON to Java using {@link JsonValue#to(Class)}.
 *
 * <p>Collapsing can be used to compose structures in Java without requiring that the JSON input has
 * the same structure. Since records do not allow for inheritance this is an alternative way to get
 * to a similar goal. In contrast to inheritance collapsed composition supports the equivalent of
 * multi-inheritance.
 *
 * @author Jan Bernitt
 * @since 1.9
 */
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface Collapsed {}
