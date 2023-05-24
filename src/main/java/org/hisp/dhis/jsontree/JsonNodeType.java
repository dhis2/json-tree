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
     * @since 0.9
     * @return true, if the node is neither an object nor an array
     */
    public boolean isSimple() {
        return this != ARRAY && this != OBJECT;
    }
}
