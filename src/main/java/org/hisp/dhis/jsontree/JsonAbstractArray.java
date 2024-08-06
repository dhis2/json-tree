package org.hisp.dhis.jsontree;

import org.hisp.dhis.jsontree.Validation.Rule;
import org.hisp.dhis.jsontree.internal.Surly;
import org.hisp.dhis.jsontree.validation.JsonValidator;

import java.util.Collection;
import java.util.Iterator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.hisp.dhis.jsontree.Validation.NodeType.ARRAY;

/**
 * An "abstract" type that is expected to be backed by a JSON array.
 *
 * @since 0.11
 */
@Validation.Ignore
@Validation( type = ARRAY )
public interface JsonAbstractArray<E extends JsonValue> extends JsonAbstractCollection, Iterable<E> {

    /**
     * A typed variant of {@link JsonArray#get(int)}, equivalent to {@link JsonArray#get(int, Class)} where 2nd
     * parameter is the type parameter E.
     *
     * @param index index to access
     * @return element at the provided index
     */
    E get( int index );

    /**
     * @return indexes of the elements contained in this array
     * @since 0.11
     */
    default IntStream indexes() {
        return isUndefined() || isEmpty() ? IntStream.empty() : IntStream.range( 0, size() );
    }

    @Surly
    @Override
    default Iterator<E> iterator() {
        return indexes().mapToObj( this::get ).iterator();
    }

    /**
     * @return this list as a {@link Stream}, if this node does not exist or is JSON {@code null} the stream is empty
     */
    default Stream<E> stream() {
        return isUndefined() || isEmpty() ? Stream.empty() : StreamSupport.stream( spliterator(), false );
    }

    /**
     * True, if this list contains all the values provided given each value of the list is transformed by the toValue
     * function to access its comparable value.
     *
     * @param toValue convert list element to comparable value
     * @param values  set of expected value in no particular order
     * @param <T>     type of the values compared
     * @return true if all values are found, otherwise false
     */
    default <T> boolean containsAll( Function<E, T> toValue, Collection<T> values ) {
        return count( toValue, values::contains ) == values.size();
    }

    /**
     * Is there any value matching the test?
     *
     * @param toValue convert list element to comparable value
     * @param test    returns true, if equal (contained)
     * @param <T>     type of the values compared
     * @return true if any value is found returning true for the test performed
     */
    default <T> boolean contains( Function<E, T> toValue, Predicate<T> test ) {
        return count( toValue, test ) > 0;
    }

    /**
     * Is there just one and only one value matching the test?
     *
     * @param toValue convert list element to comparable value
     * @param test    returns true, if equal (contained)
     * @param <T>     type of the values compared
     * @return true if only one value is found returning true for the test performed
     */
    default <T> boolean containsUnique( Function<E, T> toValue, Predicate<T> test ) {
        return count( toValue, test ) == 1;
    }

    /**
     * Counts the number of values in this list that match the test (return true).
     *
     * @param toValue convert list element to comparable value
     * @param test    returns true, if equal (contained)
     * @param <T>     type of the values compared
     * @return number of elements matching the test
     */
    default <T> int count( Function<E, T> toValue, Predicate<T> test ) {
        return (int) stream().filter( e -> test.test( toValue.apply( e ) ) ).count();
    }

    /**
     * Finds the first element of this list that matches the test criteria (returns true).
     * <p>
     * If no such element is returned a non-existing element at index of size of this list is returned.
     *
     * @param test matcher to find the element
     * @return the first matching element, or an element that does not exist
     */
    default E first( Predicate<E> test ) {
        return stream().filter( test ).findFirst().orElseGet( () -> get( size() ) );
    }

    /**
     * @param schema the schema to validate all elements of this array against
     * @param rules optional set of {@link Rule}s to check, empty includes all
     * @throws JsonSchemaException      in case this value does not match the given schema
     * @throws IllegalArgumentException in case the given schema is not an interface
     * @since 1.0
     */
    default void validateEach(Class<? extends JsonObject> schema, Rule... rules) {
        forEach( e -> JsonValidator.validate( e, schema, rules ) );
    }
}
