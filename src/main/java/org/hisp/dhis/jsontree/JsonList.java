/*
 * Copyright (c) 2004-2021, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.jsontree;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A {@link JsonList} is nothing else then a {@link JsonArray} with "typed"
 * uniform elements.
 *
 * @author Jan Bernitt
 *
 * @param <E> type of the list elements
 */
public interface JsonList<E extends JsonValue> extends JsonCollection, Iterable<E>
{
    /**
     * A typed variant of {@link JsonArray#get(int)}, equivalent to
     * {@link JsonArray#get(int, Class)} where 2nd parameter is the type
     * parameter E.
     *
     * @param index index to access
     * @return element at the provided index
     */
    E get( int index );

    /**
     * True, if this list contains all the values provided given each value of
     * the list is transformed by the toValue function to access its comparable
     * value.
     *
     * @param toValue convert list element to comparable value
     * @param values set of expected value in no particular order
     * @param <T> type of the values compared
     * @return true if all values are found, otherwise false
     */
    default <T> boolean containsAll( Function<E, T> toValue, T... values )
    {
        return containsAll( toValue, Set.of( values ) );
    }

    /**
     * True, if this list contains all the values provided given each value of
     * the list is transformed by the toValue function to access its comparable
     * value.
     *
     * @param toValue convert list element to comparable value
     * @param values set of expected value in no particular order
     * @param <T> type of the values compared
     * @return true if all values are found, otherwise false
     */
    default <T> boolean containsAll( Function<E, T> toValue, Collection<T> values )
    {
        return count( toValue, values::contains ) == values.size();
    }

    /**
     * Is there any value matching the test?
     *
     * @param toValue convert list element to comparable value
     * @param test returns true, if equal (contained)
     * @param <T> type of the values compared
     * @return true if any value is found returning true for the test performed
     */
    default <T> boolean contains( Function<E, T> toValue, Predicate<T> test )
    {
        return count( toValue, test ) > 0;
    }

    /**
     * Is there just one and only one value matching the test?
     *
     * @param toValue convert list element to comparable value
     * @param test returns true, if equal (contained)
     * @param <T> type of the values compared
     * @return true if only one value is found returning true for the test
     *         performed
     */
    default <T> boolean containsUnique( Function<E, T> toValue, Predicate<T> test )
    {
        return count( toValue, test ) == 1;
    }

    /**
     * Counts the number of values in this list that match the test (return
     * true).
     *
     * @param toValue convert list element to comparable value
     * @param test returns true, if equal (contained)
     * @param <T> type of the values compared
     * @return number of elements matching the test
     */
    default <T> int count( Function<E, T> toValue, Predicate<T> test )
    {
        int c = 0;
        for ( E e : this )
        {
            if ( test.test( toValue.apply( e ) ) )
            {
                c++;
            }
        }
        return c;
    }

    /**
     * Finds the first element of this list that matches the test criteria
     * (returns true).
     *
     * If no such element is returned a non-existing element at index of size of
     * this list is returned.
     *
     * @param test matcher to find the element
     * @return the first matching element, or an element that does not exist
     */
    default E first( Predicate<E> test )
    {
        for ( E e : this )
        {
            if ( e.exists() && test.test( e ) )
            {
                return e;
            }
        }
        return get( size() ); // we know this does not exist
    }

    @Override
    default Iterator<E> iterator()
    {
        int size = size();
        return new Iterator<>()
        {
            int index = 0;

            @Override
            public boolean hasNext()
            {
                return index < size;
            }

            @Override
            public E next()
            {
                if ( !hasNext() )
                {
                    throw new NoSuchElementException();
                }
                return get( index++ );
            }
        };
    }

    default Iterable<E> filtered( Predicate<? super E> filter )
    {
        return stream().filter( filter ).collect( Collectors.toList() );
    }

    /**
     * @return this list as a {@link Stream}
     */
    default Stream<E> stream()
    {
        return StreamSupport.stream( spliterator(), false );
    }

    /**
     * Map this {@link JsonList} to a plain {@link List}. To map the element
     * from a {@link JsonValue} to a plain value a mapper {@link Function} is
     * provided.
     *
     * @param toValue maps from {@link JsonValue} to plain value
     * @param <T> type of result list elements
     * @return this list mapped to a {@link List} of elements mapped by the
     *         provided mapper function from the {@link JsonValue}s of this
     *         {@link JsonList}.
     * @throws java.util.NoSuchElementException in case a source element
     *         {@link JsonValue} does not exist
     *
     * @see #toList(Function, Object)
     */
    default <T> List<T> toList( Function<E, T> toValue )
    {
        return stream().map( toValue ).collect( Collectors.toList() );
    }

    /**
     * Map list of {@link JsonValue}s to plain/simple values using a provided
     * default value when the {@link JsonValue} does not exist.
     *
     * @param toValue maps from {@link JsonValue} to plain value
     * @param whenNotExists value used when {@link JsonValue} does not
     *        {@link JsonValue#exists()}
     * @param <T> type of result list elements
     * @return mapped list using the default in case a {@link JsonValue} in this
     *         list does not exist
     */
    default <T> List<T> toList( Function<E, T> toValue, T whenNotExists )
    {
        return toList( e -> e.exists() ? toValue.apply( e ) : whenNotExists );
    }

    /**
     * Unlike {@link #toList(Function)} which throws an exception should a
     * source {@link JsonValue} not exist this method skips all elements that do
     * not {@link JsonValue#exists()}.
     *
     * @param toValue maps from {@link JsonValue} to plain value
     * @param <T> type of result list elements
     * @return existing elements of this list mapped by the provided toValue
     *         function
     */
    default <T> List<T> toListOfElementsThatExists( Function<E, T> toValue )
    {
        ArrayList<T> list = new ArrayList<>( size() );
        for ( E e : this )
        {
            if ( e.exists() )
            {
                list.add( toValue.apply( e ) );
            }
        }
        return list;
    }

    /**
     * Maps this list to a lazy transformed list view where each element of the
     * original list is transformed by the given function when accessed.
     * <p>
     * This means the returned list always has same size as the original list.
     *
     * @param elementToX transformer function
     * @param <V> type of the transformer output, elements of the list view
     * @return a lazily transformed list view of this list
     */
    default <V extends JsonValue> JsonList<V> viewAsList( Function<E, V> elementToX )
    {
        final class JsonListView extends CollectionView<JsonList<E>> implements JsonList<V>
        {
            private JsonListView( JsonList<E> self )
            {
                super( self );
            }

            @Override
            public V get( int index )
            {
                return elementToX.apply( viewed.get( index ) );
            }
        }
        return new JsonListView( this );
    }
}
