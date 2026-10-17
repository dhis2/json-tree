package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JsonPathExpression#resolve(JsonMixed)}.
 *
 * <p>The helper {@link #segments(JsonPath)} flattens a resolved path to its
 * segment strings so assertions read as the path they name:
 * {@code ["foo", "1"]} corresponds to {@code .foo[1]} with all special
 * syntax resolved to concrete keys and non-negative integers.
 */
class JsonPathExpressionResolveTest {

    // ===== Root =====

    @Test
    void resolve_root_yieldsSelf() {
        List<JsonPath> paths = JsonPathExpression.ROOT.resolve(JsonMixed.of("{}")).toList();
        assertEquals(1, paths.size());
        assertEquals(JsonPath.SELF, paths.get(0));
    }

    @Test
    void resolve_emptyExpression_yieldsSelf() {
        assertEquals(JsonPath.SELF,
            JsonPathExpression.of().resolve(JsonMixed.of("{}")).findFirst().orElseThrow());
    }

    // ===== Key-only paths =====

    @Test
    void resolve_singleKey() {
        assertEquals(List.of(List.of("foo")),
            resolve("{\"foo\":1}", "foo"));
    }

    @Test
    void resolve_nestedKeys() {
        assertEquals(List.of(List.of("foo", "bar")),
            resolve("{\"foo\":{\"bar\":1}}", "foo", "bar"));
    }

    @Test
    void resolve_key_doesNotRequireExistence() {
        // Key paths are kept as-is regardless of target content
        assertEquals(List.of(List.of("foo", "bar")),
            resolve("{}", "foo", "bar"));
    }

    // ===== Single index =====

    @Test
    void resolve_index_positive() {
        assertEquals(List.of(List.of("foo", "1")),
            resolve("{\"foo\":[\"a\",\"b\",\"c\"]}", "foo", "[1]"));
    }

    @Test
    void resolve_index_zero() {
        assertEquals(List.of(List.of("foo", "0")),
            resolve("{\"foo\":[\"a\",\"b\",\"c\"]}", "foo", "[0]"));
    }

    @Test
    void resolve_index_negativeOne_isLast() {
        assertEquals(List.of(List.of("foo", "2")),
            resolve("{\"foo\":[\"a\",\"b\",\"c\"]}", "foo", "[-1]"));
    }

    @Test
    void resolve_index_negativeTwo() {
        assertEquals(List.of(List.of("foo", "1")),
            resolve("{\"foo\":[\"a\",\"b\",\"c\"]}", "foo", "[-2]"));
    }

    @Test
    void resolve_index_negativeZero_isAppend() {
        assertEquals(List.of(List.of("foo", "3")),
            resolve("{\"foo\":[\"a\",\"b\",\"c\"]}", "foo", "[-0]"));
    }

    @Test
    void resolve_index_positiveOutOfBounds_isEmitted() {
        // Current behaviour: out-of-bounds indexes are emitted so downstream
        // operations can no-op or error as appropriate per operation type.
        assertEquals(List.of(List.of("foo", "5")),
            resolve("{\"foo\":[\"a\",\"b\",\"c\"]}", "foo", "[5]"));
    }

    @Test
    void resolve_index_negativeUnderflow_isEmitted() {
        // -10 on size 3 → 3 + (-10) = -7. Emitted as-is; downstream decides.
        assertEquals(List.of(List.of("foo", "-7")),
            resolve("{\"foo\":[\"a\",\"b\",\"c\"]}", "foo", "[-10]"));
    }

    // ===== Ranges =====

    @Test
    void resolve_range_simple() {
        assertEquals(List.of(
                List.of("foo", "0"),
                List.of("foo", "1"),
                List.of("foo", "2")),
            resolve("{\"foo\":[\"a\",\"b\",\"c\",\"d\"]}", "foo", "[0:2]"));
    }

    @Test
    void resolve_range_singleElement() {
        assertEquals(List.of(List.of("foo", "1")),
            resolve("{\"foo\":[\"a\",\"b\",\"c\"]}", "foo", "[1:1]"));
    }

    @Test
    void resolve_range_negativeBounds() {
        assertEquals(List.of(
                List.of("foo", "2"),
                List.of("foo", "3"),
                List.of("foo", "4")),
            resolve("{\"foo\":[\"a\",\"b\",\"c\",\"d\",\"e\"]}", "foo", "[-3:-1]"));
    }

    @Test
    void resolve_range_openUpper() {
        assertEquals(List.of(
                List.of("foo", "1"),
                List.of("foo", "2")),
            resolve("{\"foo\":[\"a\",\"b\",\"c\"]}", "foo", "[1:]"));
    }

    @Test
    void resolve_range_openUpper_wholeArray() {
        assertEquals(List.of(
                List.of("foo", "0"),
                List.of("foo", "1"),
                List.of("foo", "2")),
            resolve("{\"foo\":[\"a\",\"b\",\"c\"]}", "foo", "[0:]"));
    }

    @Test
    void resolve_range_positiveOverflow_isClamped() {
        // [1:5] on size 3 emits indexes 1..2
        assertEquals(List.of(
                List.of("foo", "1"),
                List.of("foo", "2")),
            resolve("{\"foo\":[\"a\",\"b\",\"c\"]}", "foo", "[1:5]"));
    }

    @Test
    void resolve_range_inverted_throws() {
        assertThrows(JsonPatchException.class,
            () -> resolve("{\"foo\":[\"a\",\"b\",\"c\"]}", "foo", "[3:1]"));
    }

    @Test
    void resolve_range_negativeUnderflow_throws() {
        // -10 on size 3 → -7 < 0 → underflow, unlike positive overflow
        assertThrows(JsonPatchException.class,
            () -> resolve("{\"foo\":[\"a\",\"b\",\"c\"]}", "foo", "[-10:-1]"));
    }

    @Test
    void resolve_range_negativeFromUnderflow_throws() {
        assertThrows(JsonPatchException.class,
            () -> resolve("{\"foo\":[\"a\",\"b\",\"c\"]}", "foo", "[-10:2]"));
    }

    @Test
    void resolve_range_openUpperOnEmptyArray_emitsNothing() {
        assertEquals(List.of(),
            resolve("{\"foo\":[]}", "foo", "[0:]"));
    }

    @Test
    void resolve_range_bothBeyondEnd_emitsNothing() {
        assertEquals(List.of(),
            resolve("{\"foo\":[\"a\"]}", "foo", "[5:10]"));
    }

    // ===== Conditions =====

    @Test
    void resolve_condition_valueMatch() {
        assertEquals(List.of(List.of("foo", "2")),
            resolve("{\"foo\":[1,2,3,4]}", "foo", "[?=3]"));
    }

    @Test
    void resolve_condition_valueNoMatch() {
        assertEquals(List.of(),
            resolve("{\"foo\":[1,2,3,4]}", "foo", "[?=99]"));
    }

    @Test
    void resolve_condition_propertyMatch_string() {
        assertEquals(List.of(List.of("foo", "1")),
            resolve("{\"foo\":[{\"id\":\"foo\"},{\"id\":\"bar\"}]}", "foo", "[?id=bar]"));
    }

    @Test
    void resolve_condition_propertyMatch_number() {
        assertEquals(List.of(List.of("foo", "1")),
            resolve("{\"foo\":[{\"id\":1},{\"id\":2}]}", "foo", "[?id=2]"));
    }

    @Test
    void resolve_condition_propertyNoMatch() {
        assertEquals(List.of(),
            resolve("{\"foo\":[{\"id\":\"foo\"},{\"id\":\"bar\"}]}", "foo", "[?id=xxx]"));
    }

    @Test
    void resolve_condition_propertyMissingOnAllElements() {
        assertEquals(List.of(),
            resolve("{\"foo\":[{\"name\":\"foo\"}]}", "foo", "[?id=foo]"));
    }

    @Test
    void resolve_condition_firstMatchOnly() {
        assertEquals(List.of(List.of("foo", "0")),
            resolve("{\"foo\":[{\"id\":\"x\"},{\"id\":\"x\"},{\"id\":\"x\"}]}", "foo", "[?id=x]"));
    }

    @Test
    void resolve_condition_emptyArray_yieldsEmpty() {
        assertEquals(List.of(),
            resolve("{\"foo\":[]}", "foo", "[?id=foo]"));
    }

    // ===== Conditions above tail =====

    @Test
    void resolve_conditionThenKey() {
        assertEquals(List.of(List.of("foo", "1", "name")),
            resolve("{\"foo\":[{\"id\":\"foo\",\"name\":\"A\"},{\"id\":\"bar\",\"name\":\"B\"}]}",
                "foo", "[?id=bar]", "name"));
    }

    @Test
    void resolve_conditionThenIndex() {
        assertEquals(List.of(List.of("foo", "1", "items", "0")),
            resolve("{\"foo\":["
                    + "{\"id\":\"foo\",\"items\":[\"a\"]},"
                    + "{\"id\":\"bar\",\"items\":[\"x\"]}]}",
                "foo", "[?id=bar]", "items", "[0]"));
    }

    @Test
    void resolve_conditionThenRange() {
        assertEquals(List.of(
                List.of("foo", "1", "items", "0"),
                List.of("foo", "1", "items", "1")),
            resolve("{\"foo\":["
                    + "{\"id\":\"foo\",\"items\":[\"a\"]},"
                    + "{\"id\":\"bar\",\"items\":[\"x\",\"y\",\"z\"]}]}",
                "foo", "[?id=bar]", "items", "[0:1]"));
    }

    @Test
    void resolve_condition_noMatch_shortCircuitsChildren() {
        assertEquals(List.of(),
            resolve("{\"foo\":[{\"id\":\"foo\"}]}",
                "foo", "[?id=xxx]", "name"));
    }

    // ===== Errors: non-array parents =====

    @Test
    void resolve_indexOnNonArray_throws() {
        assertThrows(JsonPatchException.class,
            () -> resolve("{\"foo\":\"notarray\"}", "foo", "[0]"));
    }

    @Test
    void resolve_indexOnObject_throws() {
        assertThrows(JsonPatchException.class,
            () -> resolve("{\"foo\":{}}", "foo", "[0]"));
    }

    @Test
    void resolve_rangeOnNonArray_throws() {
        assertThrows(JsonPatchException.class,
            () -> resolve("{\"foo\":{}}", "foo", "[0:2]"));
    }

    @Test
    void resolve_conditionOnNonArray_throws() {
        assertThrows(JsonPatchException.class,
            () -> resolve("{\"foo\":1}", "foo", "[?id=foo]"));
    }

    // ===== Root-level array (p == null through array segments) =====

    @Test
    void resolve_indexOnRootArray() {
        assertEquals(List.of(List.of("1")),
            resolve("[\"a\",\"b\",\"c\"]", "[1]"));
    }

    @Test
    void resolve_negativeIndexOnRootArray() {
        assertEquals(List.of(List.of("2")),
            resolve("[\"a\",\"b\",\"c\"]", "[-1]"));
    }

    @Test
    void resolve_rangeOnRootArray() {
        assertEquals(List.of(List.of("0"), List.of("1")),
            resolve("[\"a\",\"b\",\"c\"]", "[0:1]"));
    }

    @Test
    void resolve_conditionOnRootArray() {
        assertEquals(List.of(List.of("2")),
            resolve("[1,2,3,4]", "[?=3]"));
    }

    @Test
    void resolve_conditionOnRootObjectArray() {
        assertEquals(List.of(List.of("1")),
            resolve("[{\"id\":\"foo\"},{\"id\":\"bar\"}]", "[?id=bar]"));
    }

    // ===== Edge: empty array =====

    @Test
    void resolve_indexOnEmptyArray_emitsPath() {
        // Emits even though the slot doesn't exist; downstream handles
        assertEquals(List.of(List.of("0")),
            resolve("[]", "[0]"));
    }

    @Test
    void resolve_negativeZeroOnEmptyArray_resolvesToZero() {
        // size 0 + 0 = 0 → same as [0]; appending at 0 in an empty array
        assertEquals(List.of(List.of("0")),
            resolve("[]", "[-0]"));
    }

    private static List<String> segments(JsonPath path) {
        return path.segments().stream().map(Text::toString).toList();
    }

    private static List<List<String>> resolve(JsonMixed target, CharSequence... path) {
        return JsonPathExpression.of(path).resolve(target)
            .map(JsonPathExpressionResolveTest::segments)
            .toList();
    }

    private static List<List<String>> resolve(String target, CharSequence... path) {
        return resolve(JsonMixed.of(target), path);
    }
}