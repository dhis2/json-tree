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
 * <p>Collapsing can e.g. be used to access a "flat" JSON object with many properties as a Java
 * record which uses inner records to group those properties without the need to mirror that
 * grouping in JSON.
 *
 * <p>Since records do not allow for inheritance collapsing inner records can be used to compose
 * structures that certain groups of properties without the need to duplicate the components. This
 * is important as it still allows to write code that operates on one of such groups of properties
 * in a way that inheritance would without the need to duplicate the code that can handle data
 * having those properties. In contrast to inheritance collapsed composition supports the equivalent
 * of multiple-inheritance simply by having multiple inner collapsed record properties.
 *
 * <p>Please note that ATM records with generics are not supported simply to keep the implementation
 * simple.
 *
 * @author Jan Bernitt
 * @since 1.9
 */
@Target(ElementType.RECORD_COMPONENT)
@Retention(RetentionPolicy.RUNTIME)
public @interface Collapsed {}
