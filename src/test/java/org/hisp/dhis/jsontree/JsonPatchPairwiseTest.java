package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pairwise tests: for a 3-member target {@code {a:1,b:2,c:3}}, apply two
 * single-key operations to two distinct keys and verify the outcome by key
 * order and per-key values.
 *
 * <p>The key-order check catches comma-placement and edit-span bugs in the
 * zipper. The per-key value check catches wrong-span content bugs. Each case
 * is run in both op orders to exercise the "order doesn't matter" invariant.
 */
class JsonPatchPairwiseTest {

    private static final JsonNode TARGET = JsonNode.of("""
      {"a":1,"b":2,"c":3}""");
    private static final int REPLACEMENT = 99;
    private static final List<Text> KEYS = TARGET.keys().toList();

    sealed interface KeyOp {
        JsonNodeOp toOp(Text key);
        /** value after apply, or null if the key is removed */
        Integer apply();
    }

    record RemoveOp() implements KeyOp {
        public JsonNodeOp toOp(Text key) { return new JsonNodeOp.Remove(new JsonPath(null, key)); }
        public Integer apply() { return null; }

        @Override
        public String toString() {
            return "remove";
        }
    }

    record ReplaceOp() implements KeyOp {
        public JsonNodeOp toOp(Text key) {
            return new JsonNodeOp.Replace(new JsonPath(null, key), JsonNode.of(String.valueOf(REPLACEMENT)));
        }
        public Integer apply() { return REPLACEMENT; }

        @Override
        public String toString() {
            return "replace";
        }
    }

  record InsertOp() implements KeyOp {
    // Insert on an existing member = replace (RFC 6902 "add" semantics)
    public JsonNodeOp toOp(Text key) {
      return new JsonNodeOp.Insert(
          new JsonPath(null, key), JsonNode.of(String.valueOf(REPLACEMENT)));
    }

    public Integer apply() {
      return REPLACEMENT;
    }

    @Override
    public String toString() {
      return "insert";
    }
  }

    record SpreadOp() implements KeyOp {
        public JsonNodeOp toOp(Text key) {
            JsonNode obj = JsonNode.of("{\"" + key + "\":" + REPLACEMENT + "}");
            return new JsonNodeOp.Spread(JsonPath.SELF, obj);
        }
        public Integer apply() { return REPLACEMENT; }

        @Override
        public String toString() {
            return "spread";
        }
    }

  private static final List<KeyOp> OPS =
      List.of(new RemoveOp(), new ReplaceOp(), new InsertOp(), new SpreadOp());

    static Stream<Arguments> pairwise() {
        List<Arguments> cases = new ArrayList<>();
        for (int i = 0; i < KEYS.size(); i++) {
            for (int j = i + 1; j < KEYS.size(); j++) {
                Text k1 = KEYS.get(i), k2 = KEYS.get(j);
                for (KeyOp o1 : OPS) {
                    for (KeyOp o2 : OPS) {
                        cases.add(Arguments.of(k1, o1, k2, o2));
                    }
                }
            }
        }
        return cases.stream();
    }

    @ParameterizedTest(name = "{1} {0}, {3} {2}")
    @MethodSource("pairwise")
    void pairwise(Text k1, KeyOp o1, Text k2, KeyOp o2) {
        Map<Text, Integer> expected = expectedOutcome(k1, o1, k2, o2);

        JsonMixed forward  = apply(List.of(o1.toOp(k1), o2.toOp(k2)));
        JsonMixed backward = apply(List.of(o2.toOp(k2), o1.toOp(k1)));

        assertOutcome("forward",  forward,  expected);
        assertOutcome("backward", backward, expected);
    }

    @Test
    void spread_atRoot_works() {
        JsonNodeOp.Spread spread =
            new JsonNodeOp.Spread(
                JsonPath.SELF,
                JsonNode.of(
                    """
                      {"a":99}"""));
        JsonMixed patched = TARGET.patch(List.of(spread)).lift(JsonAccess.GLOBAL);
        assertEquals(99, patched.get("a").intValue());
    }

    @Test
    void spread_multipleKeysInOneOp() {
        JsonNodeOp.Spread spread = new JsonNodeOp.Spread(
            JsonPath.SELF,
            JsonNode.of(
                """
                    {"a":10,"b":20}"""));
        JsonMixed patched = TARGET.patch(List.of(spread)).lift(JsonAccess.GLOBAL);
        assertEquals(10, patched.get("a").intValue());
        assertEquals(20, patched.get("b").intValue());
        assertEquals(3, patched.get("c").intValue());
    }

    private static Map<Text, Integer> expectedOutcome(Text k1, KeyOp o1, Text k2, KeyOp o2) {
        Map<Text, Integer> expected = new LinkedHashMap<>();
        for (Text k : KEYS) {
          // a→1, b→2, c→3
          Integer after = 1 + "abc".indexOf(k.charAt(0));
            if (k.equals(k1)) after = o1.apply();
            if (k.equals(k2)) after = o2.apply();
            if (after != null) expected.put(k, after);
        }
        return expected;
    }

    private static JsonMixed apply(List<JsonNodeOp> ops) {
        return TARGET.patch(ops).lift(JsonAccess.GLOBAL);
    }

    private static void assertOutcome(String label, JsonMixed patched, Map<Text, Integer> expected) {
        List<Text> actualKeys = patched.keys().toList();
        assertEquals(List.copyOf(expected.keySet()), actualKeys,
            label + ": key order mismatch");
        for (Map.Entry<Text, Integer> e : expected.entrySet()) {
            JsonMixed v = patched.get(e.getKey());
            assertTrue(v.isNumber() && v.intValue() == e.getValue(),
                () -> label + ": key '" + e.getKey() + "' expected " + e.getValue() + " got " + v);
        }
    }
}