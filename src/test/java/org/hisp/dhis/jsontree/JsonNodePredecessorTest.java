package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.internal.Language;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link JsonNode#predecessor()}.
 *
 * <p>Contract: returns the path of the node that precedes this one in its
 * parent container (array or object), or {@code null} if there is no
 * predecessor — either because this node is the root or because it is the
 * first item in its parent.
 *
 * <p>Path comparisons are done by flattening to segment lists so assertions
 * read as the path they name.
 */
class JsonNodePredecessorTest {

    private static JsonNode node(@Language("json") String json) {
        return JsonNode.of(json);
    }

    private static List<String> segments(JsonPath path) {
        assertNotNull(path);
        return path.segments().stream().map(Text::toString).toList();
    }

    // ===== root =====

    @Test
    void predecessor_rootScalar_isNull() {
        assertNull(node("5").predecessor());
    }

    @Test
    void predecessor_rootArray_isNull() {
        assertNull(node("[1,2,3]").predecessor());
    }

    @Test
    void predecessor_rootObject_isNull() {
        assertNull(node("""
            {"a":1}""").predecessor());
    }

    // ===== array elements =====

    @Test
    void predecessor_firstArrayElement_isNull() {
        assertNull(node("[10,20,30]").element(0).predecessor());
    }

    @Test
    void predecessor_secondArrayElement_isFirst() {
        JsonPath pred = node("[10,20,30]").element(1).predecessor();
        assertEquals(List.of("0"), segments(pred));
    }

    @Test
    void predecessor_middleArrayElement() {
        JsonPath pred = node("[10,20,30,40,50]").element(2).predecessor();
        assertEquals(List.of("1"), segments(pred));
    }

    @Test
    void predecessor_lastArrayElement() {
        JsonPath pred = node("[10,20,30,40,50]").element(4).predecessor();
        assertEquals(List.of("3"), segments(pred));
    }

    @Test
    void predecessor_singleArrayElement_isNull() {
        assertNull(node("[42]").element(0).predecessor());
    }

    @Test
    void predecessor_arrayElement_outOfOrderAccess() {
        // Accessing out of order must still produce the correct predecessor
        // for each node. This validates that the path is computed rather
        // than derived from a previously materialized neighbor.
        JsonNode arr = node("[10,20,30,40,50]");

        JsonPath pred3 = arr.element(3).predecessor();
        assertEquals(List.of("2"), segments(pred3));

        JsonPath pred1 = arr.element(1).predecessor();
        assertEquals(List.of("0"), segments(pred1));

        assertNull(arr.element(0).predecessor());
    }

    @Test
    void predecessor_arrayElement_viaIteration() {
        JsonNode arr = node("[10,20,30]");
        List<JsonNode> elements = arr.elements().toList();

        assertNull(elements.get(0).predecessor());
        assertEquals(List.of("0"), segments(elements.get(1).predecessor()));
        assertEquals(List.of("1"), segments(elements.get(2).predecessor()));
    }

    @Test
    void predecessor_arrayElement_viaGetByPath() {
        JsonNode root = node("""
            {"arr":[10,20,30]}""");
        JsonNode n = root.get(JsonPath.of("arr", "1"));
        assertEquals(List.of("arr", "0"), segments(n.predecessor()));
    }

    @Test
    void predecessor_arrayElementIfExists() {
        JsonNode arr = node("[10,20,30]");
        assertEquals(List.of("0"), segments(arr.elementIfExists(1).predecessor()));
    }

    // ===== object members =====

    @Test
    void predecessor_firstObjectMember_isNull() {
        assertNull(node("""
            {"a":1,"b":2}""").member(Text.of("a")).predecessor());
    }

    @Test
    void predecessor_secondObjectMember() {
        JsonPath pred = node("""
            {"a":1,"b":2,"c":3}""").member(Text.of("b")).predecessor();
        assertEquals(List.of("a"), segments(pred));
    }

    @Test
    void predecessor_thirdObjectMember() {
        JsonPath pred = node("""
            {"a":1,"b":2,"c":3}""").member(Text.of("c")).predecessor();
        assertEquals(List.of("b"), segments(pred));
    }

    @Test
    void predecessor_singleObjectMember_isNull() {
        assertNull(node("""
            {"only":1}""").member(Text.of("only")).predecessor());
    }

    @Test
    void predecessor_objectMember_outOfOrderAccess() {
        JsonNode obj = node("""
            {"a":1,"b":2,"c":3,"d":4}""");

        JsonPath predC = obj.member(Text.of("c")).predecessor();
        assertEquals(List.of("b"), segments(predC));

        assertNull(obj.member(Text.of("a")).predecessor());

        JsonPath predB = obj.member(Text.of("b")).predecessor();
        assertEquals(List.of("a"), segments(predB));

        JsonPath predD = obj.member(Text.of("d")).predecessor();
        assertEquals(List.of("c"), segments(predD));
    }

    @Test
    void predecessor_objectMember_viaIteration() {
        JsonNode obj = node("""
            {"a":1,"b":2,"c":3}""");
        List<JsonNode> members = obj.members().toList();

        assertNull(members.get(0).predecessor());
        assertEquals(List.of("a"), segments(members.get(1).predecessor()));
        assertEquals(List.of("b"), segments(members.get(2).predecessor()));
    }

    @Test
    void predecessor_objectMember_viaGetByPath() {
        JsonNode root = node("""
            {"o":{"a":1,"b":2,"c":3}}""");
        JsonNode n = root.get(JsonPath.of("o", "c"));
        assertEquals(List.of("o", "b"), segments(n.predecessor()));
    }

    @Test
    void predecessor_memberIfExists() {
        JsonNode obj = node("""
            {"a":1,"b":2}""");
        assertEquals(List.of("a"), segments(obj.memberIfExists(Text.of("b")).predecessor()));
    }

    // ===== nested structures =====

    @Test
    void predecessor_elementInNestedArray() {
        // [[10,20],[30,40]] — 20's predecessor is 10 at path "0.0"
        JsonNode inner = node("[[10,20],[30,40]]").element(0);
        assertEquals(List.of("0", "0"), segments(inner.element(1).predecessor()));
    }

    @Test
    void predecessor_innerArrayAsOuterElement() {
        // [[10,20],[30,40]] — second inner array's predecessor is first
        JsonPath pred = node("[[10,20],[30,40]]").element(1).predecessor();
        assertEquals(List.of("0"), segments(pred));
    }

    @Test
    void predecessor_memberOfObjectInArray() {
        // [{"a":1,"b":2}] — member "b" has predecessor "0.a"
        JsonNode arr = node("""
            [{"a":1,"b":2}]""");
        JsonPath pred = arr.element(0).member(Text.of("b")).predecessor();
        assertEquals(List.of("0", "a"), segments(pred));
    }

    @Test
    void predecessor_elementOfArrayInObject() {
        // {"arr":[1,2,3]} — element 2 has predecessor "arr.1"
        JsonNode root = node("""
            {"arr":[1,2,3]}""");
        JsonPath pred = root.member(Text.of("arr")).element(2).predecessor();
        assertEquals(List.of("arr", "1"), segments(pred));
    }

    @Test
    void predecessor_deepNesting() {
        // {"a":{"b":[{"c":1},{"d":2}]}} — second element's predecessor is
        // "a.b.0"
        JsonNode inner = node("""
            {"a":{"b":[{"c":1},{"d":2}]}}""")
            .member(Text.of("a"))
            .member(Text.of("b"))
            .element(1);
        assertEquals(List.of("a", "b", "0"), segments(inner.predecessor()));
    }

    // ===== predecessor path is resolvable =====

    @Test
    void predecessor_pathResolvesForArray() {
        JsonNode arr = node("[10,20,30,40]");
        JsonNode third = arr.element(2);
        JsonPath pred = third.predecessor();
        assertTrue(arr.exists(pred));
        assertEquals(20, arr.get(pred).intValue());
    }

    @Test
    void predecessor_pathResolvesForObject() {
        JsonNode obj = node("""
            {"x":"hello","y":"world"}""");
        JsonNode y = obj.member(Text.of("y"));
        JsonPath pred = y.predecessor();
        assertTrue(obj.exists(pred));
        assertEquals("hello", obj.get(pred).textValue().toString());
    }

    // ===== duplicate keys (documented edge case) =====

    @Test
    void predecessor_duplicateKeys_pathResolvesToFirstOccurrence() {
        // {"a":1,"a":2,"b":3} — non-standard JSON, but pin the behavior.
        // "b"'s predecessor path is "a" and resolves to the FIRST "a" (value 1),
        // not the source-adjacent second "a" (value 2). Predecessor is a
        // path-based contract, not a source-node contract; this is expected.
        JsonNode obj = node("""
            {"a":1,"a":2,"b":3}""");
        JsonPath pred = obj.member(Text.of("b")).predecessor();
        assertEquals(List.of("a"), segments(pred));
        assertEquals(1, obj.get(pred).intValue());
    }

    // ===== cross-check: consistency with parent/path =====

    @Test
    void predecessor_isSiblingOfThisNode() {
        // The predecessor's parent path must match this node's parent path
        JsonNode obj = node("""
            {"a":1,"b":2,"c":3}""");
        JsonNode b = obj.member(Text.of("b"));
        JsonPath pred = b.predecessor();
        assertEquals(segments(b.path().parentPath()), segments(pred.parentPath()));
    }

    @Test
    void predecessor_isNotSelfOrAncestor() {
        // Sanity: predecessor is never the node itself nor an ancestor
        JsonNode root = node("""
            {"a":{"x":1,"y":2},"b":[10,20]}""");
        JsonNode y = root.member(Text.of("a")).member(Text.of("y"));
        JsonPath pred = y.predecessor();
        assertNotEquals(y.path(), pred);
        assertFalse(y.path().startsWith(pred));
    }
}