package org.hisp.dhis.jsontree;

/**
 * Possible types of JSON nodes in {@link JsonNode} tree.
 */
public enum JsonNodeType {
    OBJECT,
    ARRAY,
    STRING,
    NUMBER,
    BOOLEAN,
    NULL;

    /**
     * @return true, if the node is neither an object nor an array
     * @since 0.9
     */
    public boolean isSimple() {
        return this != ARRAY && this != OBJECT;
    }
}
