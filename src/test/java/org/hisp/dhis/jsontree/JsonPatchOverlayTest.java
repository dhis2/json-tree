package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.JsonValue.OverlayNull;
import org.hisp.dhis.jsontree.internal.Language;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JsonValue#overlay(JsonValue)} and {@link JsonValue#overlay(JsonValue,
 * OverlayNull)}.
 *
 * <p>Overlay implements RFC 7386 merge-patch semantics: the source JSON is "painted on top of" this
 * value. Object sources recurse when the target is also an object; non-object sources (scalar,
 * array) replace wholesale; a {@code null} member removes that member (in the default mode).
 * Members of this value not mentioned in the source are kept.
 *
 * <p>Results are compared via {@link JsonValue#equivalentTo(JsonValue)} since the patch pipeline
 * may leave padding whitespace where a comma was omitted.
 */
class JsonPatchOverlayTest {

  private static JsonMixed sample() {
    return JsonMixed.of(
        """
            {
              "name": "Alice",
              "age": 30,
              "contact": {
                "email": "alice@example.com",
                "phone": "+49-30-1234"
              },
              "tags": ["a", "b", "c"]
            }""");
  }

  // ===== Basic RFC behavior (default REMOVE for null members) =====

  @Test
  void overlay_primitive_replaces() {
    assertOverlay(
        """
            {"name":"Alice","age":31,
             "contact":{"email":"alice@example.com","phone":"+49-30-1234"},
             "tags":["a","b","c"]}""",
        """
                {"age": 31}""");
  }

  @Test
  void overlay_addsKey() {
    assertOverlay(
        """
            {"name":"Alice","age":30,
             "contact":{"email":"alice@example.com","phone":"+49-30-1234"},
             "tags":["a","b","c"],
             "city":"Berlin"}""",
        """
            {"city": "Berlin"}""");
  }

  @Test
  void overlay_nullMember_removes() {
    assertOverlay(
        """
            {"name":"Alice",
             "contact":{"email":"alice@example.com","phone":"+49-30-1234"},
             "tags":["a","b","c"]}""",
        """
            {"age": null}""");
  }

  @Test
  void overlay_deepMerge() {
    assertOverlay(
        """
            {"name":"Alice","age":30,
             "contact":{"email":"alice@example.com","phone":"+49-30-5678"},
             "tags":["a","b","c"]}""",
        """
            {"contact": {"phone": "+49-30-5678"}}""");
  }

  @Test
  void overlay_arrayAtomic() {
    assertOverlay(
        """
            {"name":"Alice","age":30,
             "contact":{"email":"alice@example.com","phone":"+49-30-1234"},
             "tags":["x"]}""",
        """
            {"tags": ["x"]}""");
  }

  @Test
  void overlay_objectOverScalar() {
    assertOverlay(
        """
            {"name":{"first":"Alice"},"age":30,
             "contact":{"email":"alice@example.com","phone":"+49-30-1234"},
             "tags":["a","b","c"]}""",
        """
            {"name": {"first": "Alice"}}""");
  }

  @Test
  void overlay_scalarOverObject() {
    assertOverlay(
        """
            {"name":"Alice","age":30,
             "contact":"none",
             "tags":["a","b","c"]}""",
        """
            {"contact": "none"}""");
  }

  @Test
  void overlay_selfExtrasKept() {
    assertOverlay(
        """
            {"name":"Alicia",
             "contact":{"email":"alice@example.com","phone":"+49-30-1234"},
             "tags":["a","b","c"]}""",
        """
            {"name": "Alicia", "age": null}""");
  }

    @Test
    void overlay_emptyObject_noop() {
        assertOverlay(
            """
                {"name":"Alice","age":30,
                 "contact":{"email":"alice@example.com","phone":"+49-30-1234"},
                 "tags":["a","b","c"]}""",
            """
                {"contact": {}}""");
    }

  @Test
  void overlay_noChange() {
    assertOverlay(
        """
            {"name":"Alice","age":30,
             "contact":{"email":"alice@example.com","phone":"+49-30-1234"},
             "tags":["a","b","c"]}""",
        """
            {"name": "Alice", "age": 30}""");
  }

  // ===== Top-level non-object sources (RFC 7386 §2) =====

  @Test
  void overlay_srcNull_wholeDocumentBecomesNull() {
    assertOverlay("null", "null");
  }

  @Test
  void overlay_srcScalar_wholeDocumentReplaced() {
    assertOverlay("5", "5");
  }

  @Test
  void overlay_srcString_wholeDocumentReplaced() {
    assertOverlay("\"hello\"", "\"hello\"");
  }

  @Test
  void overlay_srcArray_wholeDocumentReplaced() {
    assertOverlay("[1,2,3]", "[1,2,3]");
  }

  // ===== REPLACE variant =====

  @Test
  void overlay_replaceNullMember_replacesWithNull() {
    assertOverlay(
        """
            {"name":"Alice","age":null,
             "contact":{"email":"alice@example.com","phone":"+49-30-1234"},
             "tags":["a","b","c"]}""",
        """
            {"age": null}""",
        OverlayNull.REPLACE);
  }

  @Test
  void overlay_replaceNull_addsNullMember() {
    assertOverlay(
        """
            {"name":"Alice","age":30,
             "contact":{"email":"alice@example.com","phone":"+49-30-1234"},
             "tags":["a","b","c"],
             "nickname":null}""",
        """
            {"nickname": null}""",
        OverlayNull.REPLACE);
  }

  @Test
  void overlay_replaceNull_nestedReplaces() {
    assertOverlay(
        """
            {"name":"Alice","age":30,
             "contact":{"email":null,"phone":"+49-30-1234"},
             "tags":["a","b","c"]}""",
        """
            {"contact": {"email": null}}""",
        OverlayNull.REPLACE);
  }

  @Test
  void overlay_removeNull_nestedRemoves() {
    assertOverlay(
        """
            {"name":"Alice","age":30,
             "contact":{"phone":"+49-30-1234"},
             "tags":["a","b","c"]}""",
        """
            {"contact": {"email": null}}""",
        OverlayNull.REMOVE);
  }

  // ===== Undefined sources =====

  @Test
  void overlay_srcUndefined_noop() {
    JsonMixed undefined = sample().get(JsonPath.of("missing"));
    assertFalse(undefined.exists());
    JsonMixed result = sample().overlay(undefined);
    assertTrue(sample().equivalentTo(result));
  }

  @Test
  void overlay_onUndefinedSelf_undefined() {
    JsonMixed missing = sample().get(JsonPath.of("missing"));
    assertFalse(missing.exists());
    JsonMixed result =
        missing.overlay(JsonMixed.of(
                """
            {"a": 1}"""));
    assertFalse(result.exists());
  }

  // ===== Java null argument =====

  @Test
  void overlay_javaNullSrc_throwsNPE() {
    assertThrows(NullPointerException.class, () -> sample().overlay((JsonValue) null));
  }

  @Test
  void overlay_javaNullOverlayNull_throwsNPE() {
    assertThrows(NullPointerException.class, () -> sample().overlay(JsonMixed.of("{}"), null));
  }

  // ===== Immutability =====

  @Test
  void overlay_originalUnchanged() {
    JsonMixed original = sample();
    original.overlay(
        JsonMixed.of(
            """
        {"age": 99, "name": null}"""));
    assertTrue(sample().equivalentTo(original), "original must be unchanged after overlay");
  }

  // ===== Cross-check with mask semantics =====

  @Test
  void overlay_deepNull_consistentWithMask() {
    JsonMixed byOverlay =
        sample()
            .overlay(
                JsonMixed.of(
                    """
            {"contact": {"email": null}}"""));
    JsonMixed byMask = sample().mask(JsonPath.of("contact", "email"));
    assertTrue(byMask.equivalentTo(byOverlay));
  }

  // ===== Nested structural cases =====

  @Test
  void overlay_deeplyNestedMerge() {
    JsonMixed target =
        JsonMixed.of(
            """
            {"a": {"b": {"c": 1, "d": 2}, "e": 3}}""");
    JsonMixed src =
        JsonMixed.of(
            """
            {"a": {"b": {"c": 9}}}""");
    JsonMixed result = target.overlay(src);
    assertTrue(
        JsonMixed.of(
                """
            {"a": {"b": {"c": 9, "d": 2}, "e": 3}}""")
            .equivalentTo(result));
  }

  @Test
  void overlay_missingIntermediate_createsChain() {
    JsonMixed target =
        JsonMixed.of(
            """
        {"a": 1}""");
    JsonMixed src =
        JsonMixed.of(
            """
        {"x": {"y": {"z": 9}}}""");
    JsonMixed result = target.overlay(src);
    assertTrue(
        JsonMixed.of(
                """
            {"a": 1, "x": {"y": {"z": 9}}}""")
            .equivalentTo(result));
  }

  @Test
  void overlay_emptySource_noop() {
    JsonMixed result = sample().overlay(JsonMixed.of("{}"));
    assertTrue(sample().equivalentTo(result));
  }

    @Test
    void overlay_addsNestedMember() {
        JsonMixed target = JsonMixed.of("""
            {"a": {}}""");
        JsonMixed src = JsonMixed.of("""
            {"a": {"b": 1}}""");
        JsonMixed result = target.overlay(src);
    assertTrue(
        JsonMixed.of(
                """
            {"a": {"b": 1}}""")
            .equivalentTo(result));
    }


  private static void assertOverlay(
      @Language("json") String expected, @Language("json") String src) {
    assertOverlay(expected, src, null);
  }

  private static void assertOverlay(
      @Language("json") String expected, @Language("json") String src, OverlayNull nullHandling) {
    JsonMixed srcValue = JsonMixed.of(src);
    JsonMixed result =
        nullHandling == null ? sample().overlay(srcValue) : sample().overlay(srcValue, nullHandling);
    assertTrue(
        JsonMixed.of(expected).equivalentTo(result),
        () -> "expected: " + expected + "\n  actual: " + result);
  }
}
