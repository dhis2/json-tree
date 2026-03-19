package org.hisp.dhis.jsontree;

/**
 * Thrown when an operation on a {@link JsonNode} in a JSON tree is not supported or permitted by
 * the actual node.
 *
 * <p>Mostly this is related to the {@link JsonNodeType}. An operation might only be supported by
 * specific type of nodes.
 *
 * @author Jan Bernitt
 */
public final class JsonTreeException extends UnsupportedOperationException {

  public JsonTreeException(String message) {
    super(message);
  }

  public static JsonTreeException notA(JsonNodeType required, JsonNode node, String operation) {
    return new JsonTreeException(
        "%s node at path %s is not a %s and does not support #%s: %s"
            .formatted(
                node.getType(),
                node.getPath().toDisplayString(),
                required,
                operation,
                excerpt(node)));
  }

  public static JsonTreeException notAContainer(JsonNode node, String operation) {
    return new JsonTreeException(
        "%s node at path %s is not a container and does not support #%s: %s"
            .formatted(node.getType(), node.getPath().toDisplayString(), operation, excerpt(node)));
  }

  public static JsonTreeException notAnObject(JsonPath subPath, JsonNode node, String operation) {
    return new JsonTreeException(
        "%s node at path %s is not a OBJECT and does not support #%s:\"%s\"): %s"
            .formatted(
                node.getType(),
                node.getPath().toDisplayString(),
                operation.substring(0, operation.length()-1),
                subPath.toDisplayString(),
                excerpt(node)));
  }

  private static CharSequence excerpt(JsonNode node) {
    return switch (node.getType()) {
      case NULL, BOOLEAN, STRING, NUMBER -> node.getDeclaration();
      case OBJECT, ARRAY -> {
        Text json = node.getDeclaration();
        if (json.length() < 20) yield json;
        yield json.subSequence(0, 20) + "...";
      }
    };
  }
}
