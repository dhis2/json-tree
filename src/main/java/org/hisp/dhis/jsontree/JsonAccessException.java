package org.hisp.dhis.jsontree;

import java.lang.reflect.Type;

/**
 * Thrown then {@link org.hisp.dhis.jsontree.JsonAccessors.JsonAccessor#access(JsonMixed, Type,
 * JsonAccessors)} cannot convert the value found in the JSON to the target Java type.
 *
 * <p>This would for example happen internally when calling {@link JsonValue#to(Class)} with a
 * target type that no {@link org.hisp.dhis.jsontree.JsonAccessors.JsonAccessor} is known for or
 * where the accessor failed to map the value.
 *
 * <p>Because navigation and mapping is lazy in this library and only accessed properties are mapped
 * this is generally phrased as accessing, not mapping JSON to Java since that is usually associated
 * with mapping the entire input to an object model.
 *
 * @since 1.9
 */
public final class JsonAccessException extends IllegalStateException {

  public JsonAccessException(String message) {
    super(message);
  }

  public JsonAccessException(String message, Throwable cause) {
    super(message, cause);
  }
}
