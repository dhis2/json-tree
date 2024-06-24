package org.hisp.dhis.jsontree;

import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 *
 * @param as
 * @param select
 * @param followSelected
 * @param items
 * @param <T>
 */
public record PathSelector<T extends JsonValue>(Class<T> as, Predicate<T> select, boolean followSelected,
                                                Map<String, PathSelector<?>> items) {

    static <T extends JsonValue> Stream<String> visit(T root, PathSelector<? super T> selector ) {
        Stream.Builder<String> add = Stream.builder();
        if (selector.select.test( root )) {
            selector.items.forEach( (path, pathSelector ) -> {
                // is path an array?
                //
            } );
        }
        return add.build();
    }
}
