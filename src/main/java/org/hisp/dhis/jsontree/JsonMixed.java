package org.hisp.dhis.jsontree;

import static org.hisp.dhis.jsontree.Validation.NodeType.ARRAY;
import static org.hisp.dhis.jsontree.Validation.NodeType.BOOLEAN;
import static org.hisp.dhis.jsontree.Validation.NodeType.INTEGER;
import static org.hisp.dhis.jsontree.Validation.NodeType.NUMBER;
import static org.hisp.dhis.jsontree.Validation.NodeType.OBJECT;
import static org.hisp.dhis.jsontree.Validation.NodeType.STRING;

import java.nio.file.Path;
import org.hisp.dhis.jsontree.internal.Language;
import org.hisp.dhis.jsontree.internal.NotNull;

/**
 * Main API to wrap JSON raw strings as {@link JsonValue} nodes.
 *
 * <p>{@link JsonValue} is the bottom type or base type of possible JSON values. It represents a
 * value of "unknown" node type.
 *
 * <p>{@linkplain JsonMixed} is the union type of all possible core JSON types. It is a convenience
 * starting point that can be treated as any node type. It can also be used to represent a value
 * that knowingly can be of different JSON node types.
 *
 * @author Jan Bernitt
 * @see JsonValue
 * @since 0.8
 */
@Validation(type = {OBJECT, ARRAY, STRING, NUMBER, INTEGER, BOOLEAN})
@Validation.Ignore
public interface JsonMixed
    extends JsonObject, JsonArray, JsonString, JsonNumber, JsonBoolean, JsonInteger {

  /**
   * Lift an actual {@link JsonNode} tree to a virtual {@link JsonValue}.
   *
   * @param node non null
   * @return the provided {@link JsonNode} as virtual {@link JsonMixed}
   */
  static JsonMixed of(JsonNode node) {
    return new JsonVirtualTree(node.extract(), JsonAccess.GLOBAL);
  }

  /**
   * View the provided JSON string as virtual lazy evaluated tree.
   *
   * @param json a standard conform JSON string
   * @return root of the virtual tree representing the given JSON input
   */
  static JsonMixed of(CharSequence json) {
    return of(json, JsonAccess.GLOBAL);
  }

  static JsonMixed of(@Language("json") String json) {
    return of(json, JsonAccess.GLOBAL);
  }

  /**
   * View the provided JSON string as virtual lazy evaluated tree using the provided {@link
   * JsonAccessors} for mapping to Java method return type.
   *
   * @param json a standard conform JSON string
   * @param accessors mapping used to map JSON values to the Java method return type of abstract
   *     methods, when {@code null} default mapping is used
   * @return root of the virtual tree representing the given JSON input
   */
  static JsonMixed of(CharSequence json, @NotNull JsonAccessors accessors) {
    return json == null || "null".contentEquals(json)
        ? JsonVirtualTree.NULL
        : new JsonVirtualTree(json, accessors);
  }

  /**
   * @param file a JSON file in UTF-8 encoding
   * @return root of the virtual tree representing the given JSON input
   * @since 1.0
   */
  static JsonMixed of(Path file) {
    return of(JsonNode.of(file));
  }

  @Override
  default JsonMixed getValue() {
    return this;
  }
}
