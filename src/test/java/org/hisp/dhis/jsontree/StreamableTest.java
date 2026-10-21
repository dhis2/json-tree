package org.hisp.dhis.jsontree;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Spliterator;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Streamable} API.
 */
class StreamableTest {

    // ----------------------------------------------------------------------
    // Helper
    // ----------------------------------------------------------------------

    /** Creates a Streamable that yields the given elements, each call to iterator() returns a fresh Sized. */
    private static <T> Streamable<T> streamableOf(List<T> elements) {
        return () -> new Streamable.Sized<>() {
            private int index = 0;

            @Override
            public boolean hasNext() {
                return index < elements.size();
            }

            @Override
            public T next() {
                if (!hasNext()) throw new NoSuchElementException();
                return elements.get(index++);
            }

            @Override
            public long getExactSizeIfKnown() {
                return elements.size();
            }
        };
    }

    // ----------------------------------------------------------------------
    // Streamable.empty()
    // ----------------------------------------------------------------------

    @Test
    void empty_IteratorHasNoNext() {
        Iterator<Object> it = Streamable.empty().iterator();
        assertFalse(it.hasNext());
        assertThrows(NoSuchElementException.class, it::next);
    }

    @Test
    void empty_CountIsZero() {
        assertEquals(0, Streamable.empty().count());
    }

    @Test
    void empty_ToListIsEmpty() {
        assertTrue(Streamable.empty().toList().isEmpty());
    }

    @Test
    void empty_StreamIsEmpty() {
        assertEquals(0, Streamable.empty().stream().count());
    }

    // ----------------------------------------------------------------------
    // Streamable.of(T)
    // ----------------------------------------------------------------------

    @Test
    void single_IteratorYieldsOneElement() {
        Iterator<String> it = Streamable.of("a").iterator();
        assertTrue(it.hasNext());
        assertEquals("a", it.next());
        assertFalse(it.hasNext());
    }

    @Test
    void single_CountIsOne() {
        assertEquals(1, Streamable.of("a").count());
    }

    @Test
    void single_ToListContainsElement() {
        assertEquals(List.of("a"), Streamable.of("a").toList());
    }

    @Test
    void single_StreamYieldsOneElement() {
        assertEquals(List.of("a"), Streamable.of("a").stream().collect(Collectors.toList()));
    }

    // ----------------------------------------------------------------------
    // Streamable iteration / mapping / collection
    // ----------------------------------------------------------------------

    @Test
    void streamable_IteratorIsIndependentPerCall() {
        Streamable<Integer> streamable = streamableOf(List.of(1, 2, 3));

        Iterator<Integer> first = streamable.iterator();
        assertEquals(1, first.next());        // consume one element

        Iterator<Integer> second = streamable.iterator(); // must be a fresh iterator
        assertEquals(1, second.next());
        assertEquals(2, second.next());
        assertEquals(3, second.next());
        assertFalse(second.hasNext());
    }

    @Test
    void streamable_StreamIsIndependentPerCall() {
        Streamable<Integer> streamable = streamableOf(List.of(1, 2, 3));

        List<Integer> first = streamable.stream().collect(Collectors.toList());
        List<Integer> second = streamable.stream().collect(Collectors.toList());

        assertEquals(List.of(1, 2, 3), first);
        assertEquals(List.of(1, 2, 3), second);
    }

    @Test
    void streamable_MapTransformsElements() {
        Streamable<Integer> streamable = streamableOf(List.of(1, 2, 3));
        Streamable<String> mapped = streamable.map(i -> "v" + i);

        assertEquals(List.of("v1", "v2", "v3"), mapped.toList());
    }

    @Test
    void streamable_ToListReturnsAllElements() {
        Streamable<Integer> streamable = streamableOf(List.of(10, 20, 30));
        assertEquals(List.of(10, 20, 30), streamable.toList());
    }

    @Test
    void streamable_CountReturnsSize() {
        Streamable<Integer> streamable = streamableOf(List.of(10, 20, 30));
        assertEquals(3, streamable.count());
    }

    // ----------------------------------------------------------------------
    // Sized utility methods (single‑use iterator)
    // ----------------------------------------------------------------------

    @Test
    void sized_IteratorIsSingleUse() {
        Streamable.Sized<String> sized = Streamable.of("x").iterator();
        assertEquals("x", sized.next());
        assertFalse(sized.hasNext());
        assertThrows(NoSuchElementException.class, sized::next);
    }

    @Test
    void sized_MapPreservesSizeAndAppliesFunction() {
        Streamable.Sized<Integer> original = streamableOf(List.of(1, 2, 3)).iterator();
        Streamable.Sized<String> mapped = original.map(i -> "n" + i);

        assertEquals(3, mapped.getExactSizeIfKnown());
        assertEquals(List.of("n1", "n2", "n3"), mapped.toList());
    }

    @Test
    void sized_ToListReturnsAllElements() {
        Streamable.Sized<Integer> sized = streamableOf(List.of(5, 6, 7)).iterator();
        assertEquals(List.of(5, 6, 7), sized.toList());
    }

    @Test
    void sized_CountReturnsExactSize() {
        Streamable.Sized<Integer> sized = streamableOf(List.of(8, 9, 10)).iterator();
        assertEquals(3, sized.count());
    }

    @Test
    void sized_ForEachRemainingConsumesAll() {
        Streamable.Sized<Integer> sized = streamableOf(List.of(1, 2, 3)).iterator();
        List<Integer> collected = new ArrayList<>();
        sized.forEachRemaining(collected::add);
        assertEquals(List.of(1, 2, 3), collected);
        assertFalse(sized.hasNext());
    }

    @Test
    void sized_TryAdvanceConsumesOne() {
        Streamable.Sized<String> sized = Streamable.of("a").iterator();
        List<String> collected = new ArrayList<>();
        assertTrue(sized.tryAdvance(collected::add));
        assertEquals(List.of("a"), collected);
        assertFalse(sized.tryAdvance(collected::add));
    }

    @Test
    void sized_TrySplitReturnsNull() {
        Streamable.Sized<String> sized = Streamable.of("a").iterator();
        assertNull(sized.trySplit());
    }

    @Test
    void sized_EstimateSizeEqualsExactSize() {
        Streamable.Sized<Integer> sized = streamableOf(List.of(1, 2, 3)).iterator();
        assertEquals(3, sized.estimateSize());
        assertEquals(3, sized.getExactSizeIfKnown());
    }

    @Test
    void sized_CharacteristicsAreCorrect() {
        Streamable.Sized<String> sized = Streamable.of("a").iterator();
        int expected = Spliterator.ORDERED | Spliterator.SIZED | Spliterator.NONNULL | Spliterator.IMMUTABLE;
        assertEquals(expected, sized.characteristics());
    }

    // ----------------------------------------------------------------------
    // Illegal reuse – Sized must not be a Streamable
    // ----------------------------------------------------------------------

    @Test
    void sizedIsNotAStreamable() {
        Streamable.Sized<String> sized = Streamable.of("a").iterator();
        // Sized deliberately does not extend Streamable, so this cast must fail
        assertFalse(sized instanceof Streamable);
    }

    @Test
    void streamableDoesNotReturnSameIteratorInstance() {
        Streamable<Integer> streamable = streamableOf(List.of(1, 2));
        Iterator<Integer> first = streamable.iterator();
        Iterator<Integer> second = streamable.iterator();
        assertNotSame(first, second);
    }

    @Test
    void streamableStreamCreatesNewStreamEachTime() {
        Streamable<Integer> streamable = streamableOf(List.of(1, 2));
        Stream<Integer> first = streamable.stream();
        Stream<Integer> second = streamable.stream();
        assertNotSame(first, second);
        // Both streams must be usable independently
        assertEquals(List.of(1, 2), first.collect(Collectors.toList()));
        assertEquals(List.of(1, 2), second.collect(Collectors.toList()));
    }

    // ----------------------------------------------------------------------
    // Empty — identity and fast path (this == EMPTY)
    // ----------------------------------------------------------------------

    @Test
    void empty_isSingleton() {
        assertSame(Streamable.empty(), Streamable.empty());
        assertSame(Streamable.EMPTY, Streamable.empty());
    }

    @Test
    void empty_streamIsEmpty() {
        assertEquals(0L, Streamable.empty().stream().count());
    }

    @Test
    void empty_streamReturnsFreshStreamPerCall() {
        Streamable<Object> empty = Streamable.empty();
        Stream<Object> a = empty.stream();
        Stream<Object> b = empty.stream();
        assertNotSame(a, b);
        // each stream is independent and empty
        assertEquals(0L, a.count());
        assertEquals(0L, b.count());
    }

    @Test
    void empty_toListReturnsEmptyList() {
        assertTrue(Streamable.empty().toList().isEmpty());
    }

    @Test
    void empty_toListIsImmutable() {
        List<Object> list = Streamable.empty().toList();
        assertThrows(UnsupportedOperationException.class, () -> list.add("x"));
    }

    @Test
    void empty_toListIsStableAcrossCalls() {
        Streamable<Object> empty = Streamable.empty();
        assertTrue(empty.toList().isEmpty());
        assertTrue(empty.toList().isEmpty());
        assertTrue(empty.toList().isEmpty());
    }

    @Test
    void empty_countIsZero() {
        assertEquals(0L, Streamable.empty().count());
        assertEquals(0L, Streamable.empty().count()); // idempotent
    }

    // ----------------------------------------------------------------------
    // Empty — the underlying iterator (used when EMPTY is not the receiver)
    // ----------------------------------------------------------------------

    @Test
    void empty_iteratorIsFreshPerCall() {
        Streamable<Object> empty = Streamable.empty();
        Iterator<Object> first = empty.iterator();
        Iterator<Object> second = empty.iterator();
        assertNotSame(first, second);
        assertFalse(first.hasNext());
        assertFalse(second.hasNext());
    }

    @Test
    void empty_iteratorNextThrowsWithDedicatedMessage() {
        Iterator<Object> it = Streamable.empty().iterator();
        NoSuchElementException ex = assertThrows(NoSuchElementException.class, it::next);
        assertEquals("Empty does not have a next() element", ex.getMessage());
    }

    @Test
    void empty_sizedIteratorIsEmptyAndSizedZero() {
        Streamable.Sized<Object> sized = Streamable.empty().iterator();
        assertEquals(0L, sized.getExactSizeIfKnown());
        assertEquals(0L, sized.estimateSize());
        assertFalse(sized.hasNext());
        assertNull(sized.trySplit());
        assertFalse(sized.tryAdvance(x -> fail("must not advance")));
        sized.forEachRemaining(x -> fail("must not yield"));
    }

    // ----------------------------------------------------------------------
    // Empty — via the general (non-EMPTY) code path
    // ----------------------------------------------------------------------

    @Test
    void empty_customEmptyStreamableWorksLikeEmpty() {
        // A custom Streamable that yields nothing; it is NOT the EMPTY singleton,
        // so stream()/toList()/count() take the general code path.
        Streamable<Object> custom = () -> new Streamable.Sized<>() {
            @Override public boolean hasNext() { return false; }
            @Override public Object next() { throw new NoSuchElementException(); }
            @Override public long getExactSizeIfKnown() { return 0L; }
        };
        assertTrue(custom.toList().isEmpty());
        assertEquals(0L, custom.count());
        assertEquals(0L, custom.stream().count());
    }

    @Test
    void empty_mapDoesNotInvokeFunction() {
        AtomicInteger calls = new AtomicInteger();
        Streamable<String> mapped = Streamable.<Integer>empty().map(i -> {
            calls.incrementAndGet();
            return "v" + i;
        });

        assertTrue(mapped.toList().isEmpty());
        assertEquals(0L, mapped.count());
        assertEquals(0L, mapped.stream().count());

        assertEquals(0, calls.get(), "map function must never be called for an empty source");
    }

    @Test
    void empty_mapPreservesEmptiness() {
        Streamable<Object> empty = Streamable.empty();
        assertEquals(List.of(), empty.map(x -> x).toList());
        assertEquals(0L, empty.map(x -> x).count());
        assertEquals(0L, empty.map(x -> x).stream().count());
    }

    @Test
    void empty_chainedMapsStayEmpty() {
        Streamable<String> chained = Streamable.<Integer>empty()
            .map(i -> i + 1)
            .map(i -> "v" + i);
        assertTrue(chained.toList().isEmpty());
        assertEquals(0L, chained.count());
        assertEquals(0L, chained.stream().count());
    }

    @Test
    void empty_sizedIteratorToListIsEmptyWithoutAllocation() {
        // Covers Sized.toList()'s size==0 short-circuit
        Streamable.Sized<Object> sized = Streamable.empty().iterator();
        assertTrue(sized.toList().isEmpty());
        assertEquals(0L, sized.count());
    }
}