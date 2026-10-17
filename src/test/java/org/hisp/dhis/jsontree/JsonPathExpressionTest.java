package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JsonPathExpression}.
 */
class JsonPathExpressionTest {

    @Test
    void of_noSegments_returnsRoot() {
        assertSame(JsonPathExpression.ROOT, JsonPathExpression.of());
        assertSame(JsonPathExpression.ROOT, JsonPathExpression.of(""));
        assertSame(JsonPathExpression.ROOT, JsonPathExpression.of("", ""));
    }

    @Test
    void of_singleSegment_hasNullParent() {
        JsonPathExpression path = JsonPathExpression.of("foo");
        assertNull(path.parent());
        assertEquals("foo", path.segment().toString());

        assertEquals(JsonPathExpression.of("foo"), JsonPathExpression.of("", "foo"));
        assertEquals(JsonPathExpression.of("foo"), JsonPathExpression.of("foo", ""));
    }

    @Test
    void of_multipleSegments_nestsLeftToRight() {
        JsonPathExpression path = JsonPathExpression.of("foo", "[1]", "bar");
        assertEquals("bar", path.segment().toString());
        assertEquals("[1]", path.parent().segment().toString());
        assertEquals("foo", path.parent().parent().segment().toString());
        assertNull(path.parent().parent().parent());
    }

    @Test
    void isRoot_onlyForTheRootInstance() {
        assertTrue(JsonPathExpression.ROOT.isRoot());
        assertTrue(JsonPathExpression.of().isRoot());
        assertFalse(JsonPathExpression.of("foo").isRoot());
        assertFalse(JsonPathExpression.of("[0]").isRoot());
    }

    @Test
    void isArray_trueForBracketedSegments() {
        assertTrue(JsonPathExpression.of("[5]").isArray());
        assertTrue(JsonPathExpression.of("[-1]").isArray());
        assertTrue(JsonPathExpression.of("[1:3]").isArray());
        assertTrue(JsonPathExpression.of("[?id=foo]").isArray());
    }

    @Test
    void isArray_falseForNonBracketedOrPartialSegments() {
        assertFalse(JsonPathExpression.of("foo").isArray());
        assertFalse(JsonPathExpression.of("[5").isArray());
        assertFalse(JsonPathExpression.of("5]").isArray());
        assertFalse(JsonPathExpression.of("foo[5]").isArray());
        assertFalse(JsonPathExpression.of("[]").isArray());
    }

    @Test
    void isRange_trueForRangeSegments() {
        assertTrue(JsonPathExpression.of("[1:3]").isRange());
        assertTrue(JsonPathExpression.of("[0:0]").isRange());
        assertTrue(JsonPathExpression.of("[-3:-1]").isRange());
        assertTrue(JsonPathExpression.of("[12:34]").isRange());
    }

    @Test
    void isRange_falseForNonRangeSegments() {
        assertFalse(JsonPathExpression.of("[5]").isRange());
        assertFalse(JsonPathExpression.of("[?id=foo]").isRange());
        assertFalse(JsonPathExpression.of("foo").isRange());
        assertFalse(JsonPathExpression.of("a:b").isRange());
    }

    @Test
    void isRange_OmittedBound() {
        assertTrue(JsonPathExpression.of("[1:]").isRange());
        assertEquals(-1, JsonPathExpression.of("[1:]").rangeToIndex());

        assertTrue(JsonPathExpression.of("[:3]").isRange());
        assertEquals(0, JsonPathExpression.of("[:3]").rangeFromIndex());

        assertTrue(JsonPathExpression.of("[:]").isRange());
        assertEquals(0, JsonPathExpression.of("[:]").rangeFromIndex());
        assertEquals(-1, JsonPathExpression.of("[:]").rangeToIndex());
    }

    @Test
    void isCondition_noRangeCollision() {
        JsonPathExpression path = JsonPathExpression.of("[?id=foo:bar]");
        assertFalse(path.isRange());
        assertTrue(path.isCondition());
    }

    @Test
    void isCondition_allowsEmptyOnAndValue() {
        JsonPathExpression path = JsonPathExpression.of("[?=]");
        assertTrue(path.isCondition());
        assertEquals(Text.of(""), path.conditionOn());
        assertEquals(Text.of(""), path.conditionValue());
    }

    @Test
    void isCondition_trueForConditionSegments() {
        assertTrue(JsonPathExpression.of("[?id=foo]").isCondition());
        assertTrue(JsonPathExpression.of("[?=3]").isCondition());
    }

    @Test
    void isCondition_falseForOtherSegments() {
        assertFalse(JsonPathExpression.of("[5]").isCondition());
        assertFalse(JsonPathExpression.of("[1:3]").isCondition());
        assertFalse(JsonPathExpression.of("[?]").isCondition());
        assertFalse(JsonPathExpression.of("[?id]").isCondition());
        assertFalse(JsonPathExpression.of("foo").isCondition());
    }

    @Test
    void index_returnsArrayIndex() {
        assertEquals(0, JsonPathExpression.of("[0]").index());
        assertEquals(5, JsonPathExpression.of("[5]").index());
        assertEquals(42, JsonPathExpression.of("[42]").index());
    }

    @Test
    void index_returnsNegativeIndex() {
        assertEquals(-1, JsonPathExpression.of("[-1]").index());
        assertEquals(-3, JsonPathExpression.of("[-3]").index());
    }

    @Test
    void index_throwsWhenNotAnArraySegment() {
        assertThrows(NoSuchElementException.class, () -> JsonPathExpression.of("foo").index());
        assertThrows(NoSuchElementException.class, () -> JsonPathExpression.of("[]").index());
        assertThrows(NoSuchElementException.class, () -> JsonPathExpression.ROOT.index());
    }

    @Test
    void index_throwsForRangeAndConditionSegments() {
        assertThrows(NumberFormatException.class, () -> JsonPathExpression.of("[1:3]").index());
        assertThrows(NumberFormatException.class, () -> JsonPathExpression.of("[1:]").index());
        assertThrows(NumberFormatException.class, () -> JsonPathExpression.of("[?id=foo]").index());
    }

    @Test
    void index_collapsesNegativeZero() {
        assertEquals(0, JsonPathExpression.of("[0]").index());
        assertEquals(0, JsonPathExpression.of("[-0]").index());
    }

    @Test
    void index_throwsForMalformedNumbers() {
        // Per design, raw parse failure is fine — resolve layer handles it.
        assertThrows(NumberFormatException.class, () -> JsonPathExpression.of("[abc]").index());
        assertThrows(NumberFormatException.class, () -> JsonPathExpression.of("[5x]").index());
    }

    @Test
    void index_doesNotTrimWhitespace() {
        // Matches Java's Integer.parseInt: whitespace not allowed.
        assertThrows(NumberFormatException.class, () -> JsonPathExpression.of("[ 5 ]").index());
    }

    @Test
    void rangeFrom_returnsLeftBound() {
        assertEquals(1, JsonPathExpression.of("[1:3]").rangeFromIndex());
        assertEquals(0, JsonPathExpression.of("[0:0]").rangeFromIndex());
        assertEquals(-3, JsonPathExpression.of("[-3:-1]").rangeFromIndex());
    }

    @Test
    void rangeTo_returnsRightBound() {
        assertEquals(3, JsonPathExpression.of("[1:3]").rangeToIndex());
        assertEquals(0, JsonPathExpression.of("[0:0]").rangeToIndex());
        assertEquals(-1, JsonPathExpression.of("[-3:-1]").rangeToIndex());
    }

    @Test
    void rangeFrom_throwsWhenNotARange() {
        assertThrows(NoSuchElementException.class,
                () -> JsonPathExpression.of("[5]").rangeFromIndex());
        assertThrows(NoSuchElementException.class,
                () -> JsonPathExpression.of("[?id=foo]").rangeFromIndex());
        assertThrows(NoSuchElementException.class,
                () -> JsonPathExpression.of("foo").rangeFromIndex());
    }

    @Test
    void rangeTo_throwsWhenNotARange() {
        assertThrows(NoSuchElementException.class,
                () -> JsonPathExpression.of("[5]").rangeToIndex());
        assertThrows(NoSuchElementException.class,
                () -> JsonPathExpression.of("foo").rangeToIndex());
    }

    @Test
    void conditionOn_returnsPropertyName() {
        assertEquals("id", JsonPathExpression.of("[?id=foo]").conditionOn().toString());
        assertEquals("", JsonPathExpression.of("[?=3]").conditionOn().toString());
    }

    @Test
    void conditionValue_currentlyIncludesEqualsSign() {
        assertEquals("foo", JsonPathExpression.of("[?id=foo]").conditionValue().toString());
    }

    @Test
    void conditionOn_throwsWhenNotACondition() {
        assertThrows(NoSuchElementException.class,
                () -> JsonPathExpression.of("[5]").conditionOn());
        assertThrows(NoSuchElementException.class,
                () -> JsonPathExpression.of("[1:3]").conditionOn());
    }

    @Test
    void conditionValue_throwsWhenNotACondition() {
        assertThrows(NoSuchElementException.class,
                () -> JsonPathExpression.of("[5]").conditionValue());
    }

    @Test void isIndex_negative()        { assertTrue(JsonPathExpression.of("[-1]").isIndex()); }
    @Test void isNegativeIndex_true()    { assertTrue(JsonPathExpression.of("[-1]").isNegativeIndex()); }
    @Test void isNegativeIndex_false()   { assertFalse(JsonPathExpression.of("[1]").isNegativeIndex()); }
    @Test void isRange_negative()        { assertTrue(JsonPathExpression.of("[-3:-1]").isRange()); }
    @Test void isNegativeRangeFrom()     { assertTrue(JsonPathExpression.of("[-3:1]").isNegativeRangeFrom()); }
    @Test void isNegativeRangeTo_true()  { assertTrue(JsonPathExpression.of("[1:-1]").isNegativeRangeTo()); }
    @Test void isNegativeRangeTo_empty() { assertTrue(JsonPathExpression.of("[1:]").isNegativeRangeTo()); }
    @Test void isRange_emptyFrom()       { assertTrue(JsonPathExpression.of("[:3]").isRange()); }

    @Test
    void toString_singleSegment() {
        assertEquals("[\"foo\"]", JsonPathExpression.of("foo").toString());
    }

    @Test
    void toString_multipleSegments() {
        assertEquals("[\"foo\",\"bar\"]",
                JsonPathExpression.of("foo", "bar").toString());
    }

    @Test
    void toString_root() {
        assertEquals("[]", JsonPathExpression.ROOT.toString());
    }

    @Test void resolveCondition_stringMatch() {
        JsonMixed target = JsonMixed.of("""
          [{"id":"foo"}]""");
        assertEquals(1, JsonPathExpression.of("[?id=foo]").resolve(target).count());
    }
}