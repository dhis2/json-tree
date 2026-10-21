package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JsonContainer#get(JsonPath)} and
 * {@link JsonContainer#exists(JsonPath)}.
 *
 * <p>Both methods address a sub-path relative to the node they're called on.
 * The distinction from the {@code CharSequence}/{@code Text} overloads is that
 * those accept a single member name (one path segment), while these accept a
 * full relative path that can walk through nested objects and arrays.
 *
 * <p>When the target node does not exist, {@code get} returns an undefined
 * value (safe to chain further {@code get} calls on) rather than throwing, and
 * {@code exists} returns {@code false}.
 */
class JsonContainerTest {

    private static final String SAMPLE = """
        {
          "name": "Alice",
          "age": 30,
          "tags": ["red", "green", "blue"],
          "address": {
            "city": "Berlin",
            "zip": "10115"
          },
          "users": [
            {"id": "a", "name": "Alice"},
            {"id": "b", "name": "Bob"}
          ],
          "empty": {},
          "nothing": null
        }""";

    private static JsonMixed sample() {
        return JsonMixed.of(SAMPLE);
    }

    // ===== get(JsonPath) — successful lookups =====

    @Test
    void get_singleKey() {
        assertEquals("Alice", sample().get(JsonPath.of("name")).string());
    }

    @Test
    void get_nestedKey() {
        assertEquals("Berlin",
            sample().get(JsonPath.of("address", "city")).string());
    }

    @Test
    void get_arrayIndex() {
        assertEquals("green",
            sample().get(JsonPath.of("tags", "1")).string());
    }

    @Test
    void get_deepMixedPath() {
        // object → array → object → string
        assertEquals("Bob",
            sample().get(JsonPath.of("users", "1", "name")).string());
    }

    @Test
    void get_emptyObject_exists() {
        JsonMixed result = sample().get(JsonPath.of("empty"));
        assertTrue(result.exists());
        assertTrue(result.isObject());
    }

    @Test
    void get_numericKeyOnObject() {
    // "1" as a path segment on an object is a key, not an index
    JsonMixed obj =JsonMixed.of(
            """
            {"1": "one"}""");
        assertEquals("one", obj.get(JsonPath.of("1")).string());
    }

    // ===== get(JsonPath) — undefined results =====

    @Test
    void get_missingKey_undefined() {
        assertFalse(sample().get(JsonPath.of("missing")).exists());
    }

    @Test
    void get_missingNestedKey_undefined() {
        assertFalse(sample().get(JsonPath.of("address", "missing")).exists());
    }

    @Test
    void get_missingIntermediate_undefined() {
        assertFalse(sample().get(JsonPath.of("missing", "alsoMissing")).exists());
    }

    @Test
    void get_arrayIndexOutOfBounds_undefined() {
        assertFalse(sample().get(JsonPath.of("tags", "5")).exists());
    }

    @Test
    void get_pathThroughScalar_undefined() {
        // "name" is a string; can't traverse further into it
        assertFalse(sample().get(JsonPath.of("name", "nested")).exists());
    }

    @Test
    void get_pathThroughNull_undefined() {
        // "nothing" is null; can't traverse into null
        assertFalse(sample().get(JsonPath.of("nothing", "x")).exists());
    }

    @Test
    void get_undefinedChainsSafely() {
        // Chaining .get(JsonPath) on an undefined result stays undefined,
        // never throws
        JsonPath missingPath = JsonPath.of("missing");
        JsonMixed missing = sample().get(missingPath);
        assertFalse(missing.exists());
        assertEquals(missingPath, missing.path());
        assertFalse(missing.get(JsonPath.of("deeper")).exists());
        assertFalse(missing.get(JsonPath.of("a", "b", "c")).exists());
    }

    // ===== get(JsonPath) — empty path =====

    @Test
    void get_emptyPath_returnsSelf() {
        JsonMixed root = sample();
        assertTrue(root.get(JsonPath.SELF).equivalentTo(root));
    }

    // ===== get(JsonPath) — relative to this node =====

    @Test
    void get_relativeToSubtree() {
        JsonMixed address = sample().get("address");
        assertEquals("Berlin",
            address.get(JsonPath.of("city")).string());
    }

    @Test
    void get_relativeToArrayElement() {
        JsonMixed bob = sample().get(JsonPath.of("users", "1"));
        assertEquals("Bob",
            bob.get(JsonPath.of("name")).string());
    }

    @Test
    void get_relativePathDoesNotIncludeAncestors() {
        // On the address subtree, "address.city" is not addressable —
        // paths are relative, so "address" would be a member of the
        // address object itself
        JsonMixed address = sample().get("address");
        assertFalse(address.get(JsonPath.of("address", "city")).exists());
    }

    @Test
    void get_onUndefinedSelf_undefined() {
        JsonMixed missing = sample().get(JsonPath.of("missing"));
        assertFalse(missing.exists());
        assertFalse(missing.get(JsonPath.of("a")).exists());
        assertFalse(missing.get(JsonPath.of("a", "b")).exists());
    }

    // ===== get(JsonPath) — consistency with other accessors =====

    @Test
    void get_consistentWithCharSequence() {
        assertTrue(sample().get("address")
            .equivalentTo(sample().get(JsonPath.of("address"))));
        assertTrue(sample().get("name")
            .equivalentTo(sample().get(JsonPath.of("name"))));
    }

    @Test
    void get_consistentAcrossEquivalentPaths() {
        // Both should resolve to the same value
        assertTrue(sample().get(JsonPath.of("users", "0", "name"))
            .equivalentTo(sample().get(JsonPath.of("users", "0")).get(JsonPath.of("name"))));
    }

    // ===== get(JsonPath) — null node =====

    @Test
    void get_nullValue_returnsNodeNotUndefined() {
        // A null node is a real value — get returns it, not an undefined
        // placeholder. Its existence can be distinguished from a missing key.
        JsonMixed result = sample().get(JsonPath.of("nothing"));
        assertTrue(result.exists());
        assertTrue(JsonMixed.of("null").equivalentTo(result));
    }

    // ===== exists(JsonPath) — positive cases =====

    @Test
    void exists_simpleKey() {
        assertTrue(sample().exists(JsonPath.of("name")));
    }

    @Test
    void exists_nestedKey() {
        assertTrue(sample().exists(JsonPath.of("address", "city")));
    }

    @Test
    void exists_arrayIndex() {
        assertTrue(sample().exists(JsonPath.of("tags", "1")));
    }

    @Test
    void exists_deepMixedPath() {
        assertTrue(sample().exists(JsonPath.of("users", "1", "name")));
    }

    @Test
    void exists_emptyPath() {
        assertTrue(sample().exists(JsonPath.SELF));
    }

    @Test
    void exists_emptyObject() {
        assertTrue(sample().exists(JsonPath.of("empty")));
    }

    @Test
    void exists_nullValue() {
        // null is a node — it exists (isUndefined is a separate concept)
        assertTrue(sample().exists(JsonPath.of("nothing")));
    }

    // ===== exists(JsonPath) — negative cases =====

    @Test
    void exists_missingKey() {
        assertFalse(sample().exists(JsonPath.of("missing")));
    }

    @Test
    void exists_missingIntermediate() {
        assertFalse(sample().exists(JsonPath.of("missing", "deeper")));
    }

    @Test
    void exists_arrayIndexOutOfBounds() {
        assertFalse(sample().exists(JsonPath.of("tags", "5")));
    }

    @Test
    void exists_pathThroughScalar() {
        // Can't traverse into a scalar
        assertFalse(sample().exists(JsonPath.of("name", "nested")));
    }

    @Test
    void exists_pathThroughNull() {
        // Can't traverse into null
        assertFalse(sample().exists(JsonPath.of("nothing", "x")));
    }

    // ===== exists(JsonPath) — relative to this node =====

    @Test
    void exists_relativeToSubtree() {
        assertTrue(sample().get("address").exists(JsonPath.of("city")));
    }

    @Test
    void exists_relativePathDoesNotIncludeAncestors() {
        JsonMixed address = sample().get("address");
        assertFalse(address.exists(JsonPath.of("address", "city")));
    }

    @Test
    void exists_onUndefinedSelf() {
        JsonMixed missing = sample().get(JsonPath.of("missing"));
        assertFalse(missing.exists(JsonPath.of("anything")));
    }

    // ===== exists(JsonPath) — consistency =====

    @Test
    void exists_consistentWithGetResultExists() {
        // exists(path) and get(path).exists() must agree. Neither may throw.
        JsonPath[] paths = {
            JsonPath.of("name"),
            JsonPath.of("age"),
            JsonPath.of("missing"),
            JsonPath.of("address"),
            JsonPath.of("address", "city"),
            JsonPath.of("address", "missing"),
            JsonPath.of("tags", "0"),
            JsonPath.of("tags", "5"),
            JsonPath.of("users", "1", "name"),
            JsonPath.of("nothing"),
            JsonPath.of("empty")
        };
        JsonMixed root = sample();
        for (JsonPath path : paths) {
            assertEquals(root.exists(path), root.get(path).exists(),
                "exists and get().exists() disagree for path: " + path);
        }
    }

    @Test
    void exists_consistentWithExistsCharSequence() {
        assertEquals(
            sample().exists("name"),
            sample().exists(JsonPath.of("name")));
        assertEquals(
            sample().exists("missing"),
            sample().exists(JsonPath.of("missing")));
    }

    // ===== combined checks =====

    @Test
    void get_thenExists_roundTrip() {
        // If get returns a non-undefined value at path, exists must be true
        // and vice versa.
        JsonMixed root = sample();
        JsonPath[] paths = {
            JsonPath.of("address", "zip"),
            JsonPath.of("tags", "2"),
            JsonPath.of("users", "0", "id")
        };
        for (JsonPath path : paths) {
            assertTrue(root.exists(path), "exists false for: " + path);
            assertTrue(root.get(path).exists(), "get().exists() false for: " + path);
        }
    }
}