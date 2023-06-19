package org.hisp.dhis.jsontree;

/**
 * A {@link JsonValue} of unknown type that can be treated as any of the general JSON types for convenience.
 *
 * @author Jan Bernitt
 * @see JsonValue
 * @since 0.8
 */
public interface JsonMixed extends JsonObject, JsonArray, JsonString, JsonNumber, JsonBoolean {

    /**
     * Lift an actual {@link JsonNode} tree to a virtual {@link JsonValue}.
     *
     * @param node non null
     * @return the provided {@link JsonNode} as virtual {@link JsonMixed}
     */
    static JsonMixed of( JsonNode node ) {
        return new JsonVirtualTree( node.extract(), JsonTypedAccess.GLOBAL );
    }

    /**
     * View the provided JSON string as virtual lazy evaluated tree.
     *
     * @param json a standard conform JSON string
     * @return root of the virtual tree representing the given JSON input
     */
    static JsonMixed of( String json ) {
        return of( json, JsonTypedAccess.GLOBAL );
    }

    /**
     * View the provided JSON string as virtual lazy evaluated tree using the provided {@link JsonTypedAccessStore} for
     * mapping to Java method return types.
     *
     * @param json  a standard conform JSON string
     * @param store mapping used to map JSON values to the Java method return types of abstract methods, when
     *              {@code null} default mapping is used
     * @return root of the virtual tree representing the given JSON input
     */
    static JsonMixed of( String json, JsonTypedAccessStore store ) {
        return new JsonVirtualTree( json, store );
    }

    /**
     * Uses a more lenient parser to read the provided JSON input.
     * <p>
     * Beyond the standard this allows:
     * <ul>
     *     <li>single quoted strings (no escape)</li>
     * </ul>
     *
     * @param json a JSON input that is not standard conform
     * @return root of the virtual tree representing the given JSON input
     * @since 0.10
     */
    static JsonMixed ofNonStandard( String json ) {
        return of( JsonNode.ofNonStandard( json ) );
    }
}
