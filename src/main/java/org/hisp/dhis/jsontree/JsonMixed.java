package org.hisp.dhis.jsontree;

/**
 * A {@link JsonValue} of unknown type that can be treated as any of the general JSON types for convenience.
 *
 * @see JsonValue
 * 
 * @author Jan Bernitt
 * @since 0.8
 */
public interface JsonMixed extends JsonObject, JsonArray, JsonString, JsonNumber, JsonBoolean {

    /**
     * Lift an actual {@link JsonNode} tree to a virtual {@link JsonValue}.
     *
     * @param node non null
     * @return the provided {@link JsonNode} as virtual {@link JsonValue}
     */
    static JsonMixed of( JsonNode node ) {
        return new JsonVirtualTree( node.extract(), JsonTypedAccess.GLOBAL );
    }

    /**
     * View the provided JSON string as virtual lazy evaluated tree.
     *
     * @param json JSON string
     * @return virtual JSON tree root {@link JsonValue}
     */
    static JsonMixed of( String json ) {
        return of( json, JsonTypedAccess.GLOBAL );
    }

    /**
     * View the provided JSON string as virtual lazy evaluated tree using the provided {@link JsonTypedAccessStore} for
     * mapping to Java method return types.
     *
     * @param json  a JSON string
     * @param store mapping used to map JSON values to the Java method return types of abstract methods, when
     *              {@code null} default mapping is used
     * @return virtual JSON tree root {@link JsonValue}
     */
    static JsonMixed of( String json, JsonTypedAccessStore store ) {
        return new JsonVirtualTree( json, store );
    }
}
