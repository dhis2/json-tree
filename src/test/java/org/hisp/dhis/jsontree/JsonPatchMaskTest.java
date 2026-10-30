package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JsonValue#mask(JsonPath...)}.
 *
 * <p>{@code mask} removes the given paths from this value, producing a new
 * value with the original unchanged. It's a convenience over
 * {@code patch([Remove(p) for p in paths])}, so it inherits patch's
 * order-independence, no-op-on-missing semantics, and clash detection.
 *
 * <p>Paths are <b>relative</b> to the node the method is called on, not
 * absolute from the tree root.
 */
class JsonPatchMaskTest {

    private static JsonMixed sample() {
        return JsonMixed.of("""
            {
              "name": "Alice",
              "password": "secret",
              "age": 30,
              "contact": {
                "email": "alice@example.com",
                "phone": "+49-30-1234"
              },
              "users": [
                {"id": "a", "password": "s1"},
                {"id": "b", "password": "s2"}
              ],
              "nothing": null
            }""");
    }

    // ===== basic masking =====

    @Test
    void mask_singleTopLevelPath() {
        JsonMixed masked = sample().mask(JsonPath.of("password"));
        assertFalse(masked.exists(JsonPath.of("password")));
        assertEquals("Alice", masked.get("name").string());
        assertEquals(30, masked.get("age").intValue());
    }

    @Test
    void mask_nestedPath() {
        JsonMixed masked = sample().mask(JsonPath.of("contact", "email"));
        assertFalse(masked.exists(JsonPath.of("contact", "email")));
        assertEquals("+49-30-1234",
            masked.get(JsonPath.of("contact", "phone")).string());
    }

    @Test
    void mask_arrayElement_shrinksArray() {
        JsonMixed masked = sample().mask(JsonPath.of("users", "0"));
        assertEquals(1, masked.get("users").size());
        assertEquals("b", masked.get(JsonPath.of("users", "0", "id")).string());
    }

    @Test
    void mask_memberOfArrayElement() {
        JsonMixed masked = sample().mask(JsonPath.of("users", "1", "password"));
        // first user's password untouched
        assertEquals("s1",
            masked.get(JsonPath.of("users", "0", "password")).string());
        // second user's password removed
        assertFalse(masked.exists(JsonPath.of("users", "1", "password")));
    }

    @Test
    void mask_preservesSiblingStructure() {
        JsonMixed masked = sample().mask(JsonPath.of("password"));
        // Still an object with the remaining members intact
        assertTrue(masked.isObject());
        assertTrue(masked.exists(JsonPath.of("name")));
        assertTrue(masked.exists(JsonPath.of("contact")));
        assertTrue(masked.exists(JsonPath.of("contact", "email")));
        assertFalse(masked.exists(JsonPath.of("password")));
    }

    // ===== multiple paths =====

    @Test
    void mask_multiplePaths() {
        JsonMixed masked = sample().mask(
            JsonPath.of("password"),
            JsonPath.of("contact", "email"));
        assertFalse(masked.exists(JsonPath.of("password")));
        assertFalse(masked.exists(JsonPath.of("contact", "email")));
        assertEquals("Alice", masked.get("name").string());
    }

    @Test
    void mask_emptyVarargs_noOp() {
        JsonMixed masked = sample().mask();
        assertTrue(sample().equivalentTo(masked));
    }

    // ===== no-op cases =====

    @Test
    void mask_missingTopLevelPath_noOp() {
        JsonMixed masked = sample().mask(JsonPath.of("missing"));
        assertTrue(sample().equivalentTo(masked));
    }

    @Test
    void mask_missingNestedPath_noOp() {
        JsonMixed masked = sample().mask(JsonPath.of("missing", "deeper"));
        assertTrue(sample().equivalentTo(masked));
    }

    @Test
    void mask_pathThroughScalar_noOp() {
        // "name" is a string; masking something under it resolves to
        // nothing → no-op
        JsonMixed masked = sample().mask(JsonPath.of("name", "deeper"));
        assertTrue(sample().equivalentTo(masked));
    }

    @Test
    void mask_outOfBoundsIndex_noOp() {
        JsonMixed masked = sample().mask(JsonPath.of("users", "10"));
        assertTrue(sample().equivalentTo(masked));
    }

    // ===== null =====

    @Test
    void mask_nullValue_removes() {
        // A null node is a real value at that path — masking removes it
        JsonMixed masked = sample().mask(JsonPath.of("nothing"));
        assertFalse(masked.exists(JsonPath.of("nothing")));
    }

    // ===== clash detection =====

    @Test
    void mask_siblingPaths_ok() {
        // Siblings don't clash
        JsonMixed masked = sample().mask(
            JsonPath.of("contact", "email"),
            JsonPath.of("contact", "phone"));
        assertFalse(masked.exists(JsonPath.of("contact", "email")));
        assertFalse(masked.exists(JsonPath.of("contact", "phone")));
    }

    // ===== relative to this node =====

    @Test
    void mask_relativeToSubtree() {
        JsonMixed contact = sample().get("contact");
        JsonMixed masked = contact.mask(JsonPath.of("email"));
        assertFalse(masked.exists(JsonPath.of("email")));
        assertEquals("+49-30-1234",
            masked.get(JsonPath.of("phone")).string());
    }

    @Test
    void mask_relativePathDoesNotIncludeAncestors() {
        // On the contact subtree, masking "contact.email" doesn't work —
        // "contact" isn't a member of contact. No-op.
        JsonMixed contact = sample().get("contact");
        JsonMixed masked = contact.mask(JsonPath.of("contact", "email"));
        assertTrue(contact.equivalentTo(masked));
    }

    // ===== undefined self =====

    @Test
    void mask_onUndefined_returnsUndefined() {
        JsonMixed missing = sample().get("missing");
        assertFalse(missing.exists());
        JsonMixed masked = missing.mask(JsonPath.of("anything"));
        assertFalse(masked.exists());
    }

    // ===== immutability =====

    @Test
    void mask_originalUnchanged() {
        JsonMixed original = sample();
        JsonMixed masked = original.mask(JsonPath.of("password"));
        assertTrue(original.exists(JsonPath.of("password")),
            "original must still have password");
        assertFalse(masked.exists(JsonPath.of("password")));
    }

    @Test
    void mask_originalRemainsFullyIntact() {
        // Full structural comparison, not just one path
        JsonMixed original = sample();
        original.mask(JsonPath.of("password"), JsonPath.of("contact", "email"));
        assertTrue(sample().equivalentTo(original),
            "original must be byte-for-byte equivalent after mask");
    }

    // ===== behavioral consistency =====

    @Test
    void mask_equivalentToExplicitPatch() {
        // mask(paths) should behave identically to explicitly patching with
        // Remove ops at those paths
        JsonPath[] paths = {JsonPath.of("password"), JsonPath.of("contact", "email")};
        JsonMixed masked = sample().mask(paths);
        JsonMixed patched = JsonMixed.of(sample().node()
            .patch(java.util.Arrays.stream(paths)
                .map(JsonNodeOp.Remove::new)
                .toList())
            .getDeclaration().toString());
        assertTrue(masked.equivalentTo(patched));
    }

    @Test
    void mask_orderOfPathsDoesNotMatter() {
        JsonMixed a = sample().mask(
            JsonPath.of("password"),
            JsonPath.of("contact", "email"));
        JsonMixed b = sample().mask(
            JsonPath.of("contact", "email"),
            JsonPath.of("password"));
        assertTrue(a.equivalentTo(b));
    }

    // ===== root-level mask =====

    @Test
    void mask_onRootNode() {
        JsonMixed masked = sample().mask(JsonPath.of("password"));
        // Still the full document minus password
        assertTrue(masked.exists(JsonPath.of("contact")));
        assertTrue(masked.exists(JsonPath.of("users")));
        assertFalse(masked.exists(JsonPath.of("password")));
    }

    @Test
    void mask_duplicatePaths_deduped() {
        JsonMixed masked = sample().mask(
            JsonPath.of("password"), JsonPath.of("password"));
        assertFalse(masked.exists(JsonPath.of("password")));
    }

    @Test
    void mask_ancestorAndDescendant_minimized() {
        // Previously threw; now behaves as if only "contact" was listed
        JsonMixed masked = sample().mask(
            JsonPath.of("contact"),
            JsonPath.of("contact", "email"));
        assertFalse(masked.exists(JsonPath.of("contact")));
    }

    @Test
    void mask_onlySiblings_stillClashFree() {
        // Non-overlapping paths still work normally — minimization doesn't
        // accidentally collapse them
        JsonMixed masked = sample().mask(
            JsonPath.of("contact", "email"),
            JsonPath.of("contact", "phone"));
        assertFalse(masked.exists(JsonPath.of("contact", "email")));
        assertFalse(masked.exists(JsonPath.of("contact", "phone")));
    }

    @Test
    void mask_descendantBeforeAncestor_minimized() {
        // Order independence: same result whether ancestor or descendant first
        JsonMixed ancestorFirst = sample().mask(
            JsonPath.of("contact"),
            JsonPath.of("contact", "email"));
        JsonMixed descendantFirst = sample().mask(
            JsonPath.of("contact", "email"),
            JsonPath.of("contact"));
        assertTrue(ancestorFirst.equivalentTo(descendantFirst));
        assertFalse(ancestorFirst.exists(JsonPath.of("contact")));
    }

    @Test
    void mask_overlappingPaths_equivalentToMinimal() {
        // mask(contact, contact.email, contact.phone) ≡ mask(contact)
        JsonMixed redundant = sample().mask(
            JsonPath.of("contact"),
            JsonPath.of("contact", "email"),
            JsonPath.of("contact", "phone"));
        JsonMixed minimal = sample().mask(JsonPath.of("contact"));
        assertTrue(redundant.equivalentTo(minimal));
    }

    @Test
    void mask_multipleDuplicates_dedupedToSingle() {
        JsonMixed masked = sample().mask(
            JsonPath.of("password"),
            JsonPath.of("password"),
            JsonPath.of("password"));
        assertFalse(masked.exists(JsonPath.of("password")));
    }

    @Test
    void mask_noOverlap_stillWorks() {
        // Minimization must not collapse siblings — this is the regression guard
        JsonMixed masked = sample().mask(
            JsonPath.of("contact", "email"),
            JsonPath.of("contact", "phone"));
        assertFalse(masked.exists(JsonPath.of("contact", "email")));
        assertFalse(masked.exists(JsonPath.of("contact", "phone")));
    }

    @Test
    void mask_selfYieldsNull() {
        // mask(SELF) means "remove everything" → becomes null
        JsonMixed masked = sample().mask(JsonPath.SELF);
        assertTrue(masked.isNull());
    }

    @Test
    void mask_ancestorAndSibling() {
        // [contact, password] — non-overlapping, both removed
        JsonMixed masked = sample().mask(
            JsonPath.of("contact"),
            JsonPath.of("password"));
        assertFalse(masked.exists(JsonPath.of("contact")));
        assertFalse(masked.exists(JsonPath.of("password")));
        assertEquals("Alice", masked.get("name").string());
    }

    @Test
    void mask_ancestorWithDistantDescendant() {
        // contact.email is under contact — the minimization must catch this
        // even though the descendant is multiple levels down
        JsonMixed masked = sample().mask(
            JsonPath.of("contact"),
            JsonPath.of("contact", "email"));
        assertFalse(masked.exists(JsonPath.of("contact")));
        // "contact" itself being gone implies email is gone too
    }

    @Test
    void mask_self_yieldsNull() {
        JsonMixed masked = sample().mask(JsonPath.SELF);
        assertTrue(masked.exists());
        assertTrue(masked.isNull());
    }

    @Test
    void mask_selfWithOtherPaths_yieldsNull() {
        // SELF subsumes everything else
        JsonMixed masked = sample().mask(
            JsonPath.SELF,
            JsonPath.of("password"));
        assertTrue(masked.isNull());
    }

    @Test
    void mask_otherPathsThenSelf_yieldsNull() {
        // Order independence
        JsonMixed masked = sample().mask(
            JsonPath.of("password"),
            JsonPath.SELF);
        assertTrue(masked.isNull());
    }

    @Test
    void mask_bySelector_onSubtree_returnsMaskedSubtree() {
        JsonMixed contact = sample().get("contact");
        JsonMixed masked = contact.mask(JsonSelector.of("@.email"));
        // Result is the masked contact subtree, not the whole document
        assertFalse(masked.exists(JsonPath.of("email")));
        assertEquals("+49-30-1234", masked.get("phone").string());
        // Not the whole root — "name" is a top-level property, not on contact
        assertFalse(masked.exists(JsonPath.of("name")));
    }

    @Test
    void mask_bySelector_consistentShapeWithJsonPathOverload() {
        // Both overloads return the masked subtree, not the masked root
        JsonMixed contact = sample().get("contact");
        JsonMixed bySelector = contact.mask(JsonSelector.of("@.email"));
        JsonMixed byPath = contact.mask(JsonPath.of("email"));
        assertTrue(bySelector.equivalentTo(byPath));
    }

    @Test
    void mask_bySelector_ancestorRemoved_subtreeUndefined() {
        // On .contact.email's parent, mask .contact (the ancestor of this node)
        JsonMixed email = sample().get(JsonPath.of("contact", "email"));
        JsonMixed masked = email.mask(JsonSelector.of("$.contact"));  // adjust selector to address ancestor from root
        // "contact" was removed, so the node we called mask on is gone
        assertFalse(masked.exists());
    }

    @Test
    void mask_bySelector_onRoot_works() {
        JsonMixed masked = sample().mask(JsonSelector.of("$.password"));
        assertFalse(masked.exists(JsonPath.of("password")));
        assertEquals("Alice", masked.get("name").string());
    }

    @Test
    void mask_bySelector_consistentWithJsonPathOverload() {
        // mask(selector-for-x) and mask(JsonPath.of(x)) produce the same result
        JsonMixed bySelector = sample().mask(JsonSelector.of("$.password"));
        JsonMixed byPath = sample().mask(JsonPath.of("password"));
        assertTrue(bySelector.equivalentTo(byPath));
    }

    @Test
    void mask_bySelector_ancestorRemoved_yieldsUndefined() {
        JsonMixed email = sample().get(JsonPath.of("contact", "email"));
        JsonMixed masked = email.mask(JsonSelector.of("$.contact"));
        assertFalse(masked.exists());
    }

    @Test
    void mask_bySelector_selfMatched_yieldsNull() {
        JsonMixed email = sample().get(JsonPath.of("contact", "email"));
        JsonMixed masked = email.mask(JsonSelector.of("$.contact.email"));
        assertTrue(masked.exists());
        assertTrue(masked.isNull());
    }

    @Test
    void mask_bySelector_descendantMatched_yieldsMaskedSelf() {
        JsonMixed contact = sample().get("contact");
        JsonMixed masked = contact.mask(JsonSelector.of("@.email"));
        assertFalse(masked.exists(JsonPath.of("email")));
        assertEquals("+49-30-1234", masked.get("phone").text().toString());
    }

    @Test
    void mask_bySelector_siblingMatched_noOpOnSelf() {
        JsonMixed contact = sample().get("contact");
        JsonMixed masked = contact.mask(JsonSelector.of("$.name"));
        assertTrue(contact.equivalentTo(masked));
    }

    @Test
    void mask_bySelector_rootMatched_yieldsUndefined() {
        // $ matches the whole tree — the root is a strict ancestor of everything
        JsonMixed email = sample().get(JsonPath.of("contact", "email"));
        JsonMixed masked = email.mask(JsonSelector.of("$"));
        assertFalse(masked.exists());
    }

    @Test
    void mask_pathThroughNull_noOp() {
        JsonMixed masked = sample().mask(JsonPath.of("nothing", "x"));
        assertTrue(sample().equivalentTo(masked));
    }

    @Test
    void mask_onPrimitiveRoot_noOp() {
        // Removing a missing path from a scalar root is a no-op
        JsonMixed result = JsonMixed.of("5").mask(JsonPath.of("a"));
        assertTrue(JsonMixed.of("5").equivalentTo(result));
    }

    @Test
    void mask_onPrimitiveRoot_selfYieldsNull() {
        // Remove SELF on a scalar root → NULL (not empty document)
        JsonMixed result = JsonMixed.of("5").mask(JsonPath.SELF);
        assertTrue(result.isNull());
    }

    @Test
    void mask_bySelector_onPrimitiveRoot_noOpForDescendant() {
        JsonMixed result = JsonMixed.of("5").mask(JsonSelector.of("$.a"));
        assertTrue(JsonMixed.of("5").equivalentTo(result));
    }

    @Test
    void mask_bySelector_onPrimitiveRoot_rootSelectorYieldsNull() {
        JsonMixed result = JsonMixed.of("5").mask(JsonSelector.of("$"));
        assertTrue(result.isNull());
    }

    @Test
    void mask_onPrimitiveRoot_nestedPath_noOp() {
        JsonMixed result = JsonMixed.of("5").mask(JsonPath.of("a", "b", "c"));
        assertTrue(JsonMixed.of("5").equivalentTo(result));
    }

    @Test
    void mask_onStringPrimitive_noOp() {
        JsonMixed result = JsonMixed.of("\"hello\"").mask(JsonPath.of("a"));
        assertEquals("hello", result.string());
    }

    @Test
    void mask_onBooleanPrimitive_noOp() {
        JsonMixed result = JsonMixed.of("true").mask(JsonPath.of("a"));
        assertTrue(result.booleanValue());
    }

    @Test
    void mask_onNullRoot_noOp() {
        // JSON null — getIfExists on it also throws
        JsonMixed result = JsonMixed.of("null").mask(JsonPath.of("a"));
        assertTrue(result.isNull());
    }

    @Test
    void mask_onNullRoot_selfYieldsNull() {
        // mask(SELF) on null → NULL (idempotent)
        JsonMixed result = JsonMixed.of("null").mask(JsonPath.SELF);
        assertTrue(result.isNull());
    }
}