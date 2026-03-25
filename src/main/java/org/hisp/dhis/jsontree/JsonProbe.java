package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.TerminalOp;

/**
 * A JSON unit that can be tested for fundamental properties.
 *
 * @author Jan Bernitt
 * @since 1.9
 */
public interface JsonProbe {

  JsonPath path();

  /**
   * @return true if this node is the root of the tree, false otherwise
   * @since 0.6
   */
  default boolean isRoot() {
    return path().isEmpty();
  }

  JsonNodeType type();

  /**
   * @return true if the value exists and is defined JSON {@code null}
   * @throws JsonPathException in case this value does not exist in the JSON document
   */
  @TerminalOp(canBeNull = true)
  default boolean isNull() {
    return type() == JsonNodeType.NULL;
  }

  /**
   * @return true if the value exists and is a JSON array node (empty or not) but not JSON {@code
   *     null}
   */
  @TerminalOp(canBeUndefined = true)
  default boolean isArray() {
    return type() == JsonNodeType.ARRAY;
  }

  /**
   * @return true if the value exists and is an JSON object node (empty or not) but not JSON {@code
   *     null}
   */
  @TerminalOp(canBeUndefined = true)
  default boolean isObject() {
    return type() == JsonNodeType.OBJECT;
  }

  /**
   * @return true if the value exists and is an JSON number node (not JSON {@code null})
   * @since 0.10
   */
  @TerminalOp(canBeUndefined = true)
  default boolean isNumber() {
    return type() == JsonNodeType.NUMBER;
  }

  /**
   * @return true if the value exists and is an JSON string node (not JSON {@code null})
   * @since 0.10
   */
  @TerminalOp(canBeUndefined = true)
  default boolean isString() {
    return type() == JsonNodeType.STRING;
  }

  /**
   * @return true if the value exists and is an JSON boolean node (not JSON {@code null})
   * @since 0.10
   */
  @TerminalOp(canBeUndefined = true)
  default boolean isBoolean() {
    return type() == JsonNodeType.BOOLEAN;
  }

  /**
   * @return true if this value exists and is not a JSON array or object
   * @since 1.9
   */
  @TerminalOp(canBeUndefined = true)
  default boolean isSimple() {
    JsonNodeType type = type();
    return type != null && type != JsonNodeType.OBJECT && type != JsonNodeType.ARRAY;
  }
}
